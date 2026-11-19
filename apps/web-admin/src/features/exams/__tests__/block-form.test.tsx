import { screen, waitFor, within } from "@testing-library/react";
import userEvent, { type UserEvent } from "@testing-library/user-event";
import { useState } from "react";
import { describe, expect, it, vi } from "vitest";
import reference from "@/test/fixtures/content-reference.json";
import { renderWithProviders } from "@/test/render";
import type { ContentReference } from "@/features/content/types";
import { BlockEditor } from "../components/block-editor";
import { blockFieldAnchor, BLOCK_FIELDS, focusBlockField } from "../components/block-form-fields";
import type { ExamSection, QuestionBlock } from "../types";

// Danh mục là dữ liệu của lane Nội dung; dùng danh mục mẫu như `subject-matrix.test.tsx`.
vi.mock(import("@/features/content/api"), async (importOriginal) => {
  const actual = await importOriginal();
  const { getContentReferenceFixture: getContentReference } = await import("@/test/fixtures/content-reference");
  return { ...actual, getContentReference, defaultContentApiDeps: { getContentReference } };
});
vi.mock(import("../queries"), async (importOriginal) => {
  const actual = await importOriginal();
  return { ...actual, useAvailableQuestions: () => ({ isError: false, data: undefined }) as never };
});

const SECTION: ExamSection = {
  id: "s1",
  subjectCode: "LIT",
  questionCount: 25,
  knowledgeScope: null,
  answerQuotas: null,
  matrix: null,
  choiceGroup: null,
  blocks: [],
};

function makeBlock(patch: Partial<QuestionBlock> = {}): QuestionBlock {
  return {
    id: "b1",
    name: "Nhom thu",
    questionType: "SINGLE_CHOICE",
    questionCount: 4,
    setCount: null,
    setSize: null,
    requiredPointsPerQuestion: null,
    knowledgeScope: null,
    tagCodes: [],
    matrix: null,
    childAnswerType: null,
    templateGroupCode: null,
    answerTypeLocked: false,
    ...patch,
  };
}

function setBlock(patch: Partial<QuestionBlock> = {}): QuestionBlock {
  return makeBlock({ questionType: "QUESTION_SET", setCount: 5, setSize: 5, questionCount: 25, ...patch });
}

/** Giữ nhóm trong state như `ExamStructureView` và ghi lại nhóm mới nhất để kiểm tra giá trị lưu. */
function renderEditor(initial: QuestionBlock, options: { examKind?: string; omittedLocked?: boolean } = {}) {
  const latest = { block: initial };
  function Harness() {
    const [block, setBlock_] = useState(initial);
    return (
      <BlockEditor
        block={block}
        section={SECTION}
        subjectCode="LIT"
        locationText="Phần 2 · Văn · nhóm 5 / 5"
        position={1}
        siblingCount={1}
        axes={{ grades: reference.grades.map((grade) => grade.code), difficulties: reference.difficulties.map((item) => item.code) }}
        labels={{ grade: (code) => code, difficulty: (code) => code }}
        reference={reference as unknown as ContentReference}
        examKind={options.examKind}
        omittedLocked={options.omittedLocked}
        scoringDefaults={null}
        competencies={[]}
        onChange={(next) => {
          latest.block = next;
          setBlock_(next);
        }}
        onMove={() => {}}
        onRemove={() => {}}
        onOpenSection={() => {}}
      />
    );
  }
  return { latest, ...renderWithProviders(<Harness />) };
}

async function openConditionMenu(user: UserEvent) {
  await user.click(screen.getByRole("button", { name: "Thêm điều kiện" }));
  return screen.findByRole("menu");
}

describe("biểu mẫu nhóm: điều kiện", () => {
  it("thêm và bỏ điều kiện nhãn: chọn nhiều nhãn, màn ghi khớp một trong các nhãn", async () => {
    const user = userEvent.setup();
    const { latest } = renderEditor(makeBlock());
    expect(screen.getByText("Chưa thêm điều kiện nào.")).toBeInTheDocument();
    await openConditionMenu(user);
    await user.click(screen.getByRole("menuitem", { name: /Nhãn hoặc loại văn bản/ }));

    const section = screen.getByRole("region", { name: "Điều kiện của nhóm" });
    expect(within(section).getByText(/Khớp một trong các nhãn/)).toBeInTheDocument();
    await user.click(within(section).getByRole("checkbox", { name: "Câu bẫy" }));
    await user.click(within(section).getByRole("checkbox", { name: "Có đồ thị" }));
    expect(latest.block.tagCodes).toEqual(["TRAP", "GRAPH"]);

    await user.click(within(section).getByRole("button", { name: "Bỏ điều kiện Nhãn hoặc loại văn bản" }));
    expect(latest.block.tagCodes).toEqual([]);
    expect(screen.getByText("Chưa thêm điều kiện nào.")).toBeInTheDocument();
  });

  it("thêm và bỏ điều kiện điểm mỗi câu: nhận số thập phân, bỏ thì lưu null", async () => {
    const user = userEvent.setup();
    const { latest } = renderEditor(makeBlock());
    await openConditionMenu(user);
    await user.click(screen.getByRole("menuitem", { name: /Điểm mỗi câu/ }));
    await user.type(screen.getByRole("textbox", { name: "Điểm mỗi câu" }), "0,75");
    expect(latest.block.requiredPointsPerQuestion).toBe(0.75);
    expect(screen.getByText(/nhóm có 3 điểm/)).toBeInTheDocument();

    await openConditionMenu(user);
    expect(screen.getByRole("menuitem", { name: /Điểm mỗi câu/ })).toHaveAttribute("aria-disabled", "true");
    await user.keyboard("{Escape}");
    await user.click(screen.getByRole("button", { name: "Bỏ điều kiện Điểm mỗi câu" }));
    expect(latest.block.requiredPointsPerQuestion).toBeNull();
  });

  it("hình dạng câu và định dạng đáp án bị khóa khi loại câu không áp; Đúng/Sai nhập số ý, đổi sang loại khác thì bỏ", async () => {
    const user = userEvent.setup();
    const { latest } = renderEditor(makeBlock());
    await openConditionMenu(user);
    expect(screen.getByRole("menuitem", { name: /Hình dạng câu/ })).toHaveAttribute("aria-disabled", "true");
    expect(screen.getByRole("menuitem", { name: /Định dạng đáp án/ })).toHaveAttribute("aria-disabled", "true");
    await user.keyboard("{Escape}");

    await user.click(screen.getByRole("combobox", { name: /Loại câu hỏi/ }));
    await user.click(await screen.findByRole("option", { name: "Đúng/Sai" }));
    await openConditionMenu(user);
    await user.click(screen.getByRole("menuitem", { name: /Hình dạng câu/ }));
    await user.type(screen.getByRole("textbox", { name: "Số ý" }), "4");
    expect(latest.block.itemShape).toEqual({ statements: 4 });

    await user.click(screen.getByRole("combobox", { name: /Loại câu hỏi/ }));
    await user.click(await screen.findByRole("option", { name: "Một đáp án" }));
    expect(latest.block).not.toHaveProperty("itemShape");
    expect(screen.queryByRole("textbox", { name: "Số ý" })).not.toBeInTheDocument();
  });

  it("định dạng đáp án của nhóm Trả lời ngắn: chọn nhiều, bỏ hết thì bỏ trường", async () => {
    const user = userEvent.setup();
    const { latest } = renderEditor(makeBlock({ questionType: "SHORT_ANSWER" }));
    await openConditionMenu(user);
    await user.click(screen.getByRole("menuitem", { name: /Định dạng đáp án/ }));
    await user.click(screen.getByRole("checkbox", { name: "Phân số" }));
    await user.click(screen.getByRole("checkbox", { name: "Số nguyên" }));
    expect(latest.block.answerFormats).toEqual(["INTEGER", "FRACTION"]);
    await user.click(screen.getByRole("button", { name: "Bỏ điều kiện Định dạng đáp án" }));
    expect(latest.block).not.toHaveProperty("answerFormats");
  });

  it("điều kiện MAPID chuyển sang Phạm vi kiến thức 'Theo chủ đề' và mở hộp chọn", async () => {
    const user = userEvent.setup();
    renderEditor(makeBlock());
    await openConditionMenu(user);
    await user.click(screen.getByRole("menuitem", { name: /Kiến thức \(MAPID\)/ }));
    expect(await screen.findByRole("dialog")).toBeInTheDocument();
    expect(screen.getByRole("radio", { name: "Theo chủ đề", hidden: true })).toBeChecked();
  });
});

describe("biểu mẫu nhóm: câu đơn, câu chùm, loại câu hỏi, phạm vi kiến thức", () => {
  it("không còn nút ANY/ALL, không còn ô dạng bài; phạm vi chỉ có Toàn môn và Theo chủ đề", () => {
    renderEditor(makeBlock());
    expect(screen.queryByRole("button", { name: /^(ANY|ALL)$/ })).not.toBeInTheDocument();
    expect(screen.queryByRole("radio", { name: /^(ANY|ALL)$/ })).not.toBeInTheDocument();
    expect(screen.queryByText(/Dạng bài/i)).not.toBeInTheDocument();
    expect(screen.queryByText(/khớp tất cả/i)).not.toBeInTheDocument();
    const scope = screen.getByText("Phạm vi kiến thức").closest("fieldset")!;
    expect(within(scope).getAllByRole("radio").map((radio) => radio.closest("label")?.textContent)).toEqual(["Toàn môn", "Theo chủ đề"]);
    expect(within(scope).getByRole("radio", { name: "Toàn môn" })).toBeChecked();
  });

  it("SP2E và SPT gọi lựa chọn thứ hai là 'Theo mạch kiến thức'", () => {
    renderEditor(makeBlock(), { examKind: "SP2E" });
    expect(screen.getByRole("radio", { name: "Theo mạch kiến thức" })).toBeInTheDocument();
    expect(screen.queryByRole("radio", { name: "Theo chủ đề" })).not.toBeInTheDocument();
  });

  it("Câu đơn ↔ Câu chùm: sang câu chùm hiện số chùm và các điều kiện của chùm; về câu đơn thì bỏ slots", async () => {
    const user = userEvent.setup();
    const { latest } = renderEditor(makeBlock());
    expect(screen.getByRole("radio", { name: "Câu đơn" })).toBeChecked();
    expect(screen.queryByRole("region", { name: "Điều kiện riêng từng chùm" })).not.toBeInTheDocument();
    await user.click(screen.getByRole("radio", { name: "Câu chùm" }));
    expect(latest.block.questionType).toBe("QUESTION_SET");
    expect(screen.getByRole("textbox", { name: /Số chùm/ })).toBeInTheDocument();
    expect(screen.getByRole("region", { name: "Điều kiện riêng từng chùm" })).toBeInTheDocument();
    expect(screen.getByRole("region", { name: "Điều kiện theo vị trí câu con" })).toBeInTheDocument();
    await user.click(screen.getByRole("radio", { name: "Câu đơn" }));
    expect(latest.block.setCount).toBeNull();
    expect(screen.queryByRole("region", { name: "Điều kiện riêng từng chùm" })).not.toBeInTheDocument();
  });

  it("Theo chủ đề rồi Toàn môn: bỏ các MAPID đã chọn", async () => {
    const user = userEvent.setup();
    const nodeId = reference.knowledgeTree[0]!.id;
    const { latest } = renderEditor(makeBlock({ knowledgeScope: { nodeIds: [nodeId] } }));
    const scope = screen.getByText("Phạm vi kiến thức").closest("fieldset")!;
    expect(within(scope).getByRole("radio", { name: "Theo chủ đề" })).toBeChecked();
    await user.click(within(scope).getByRole("radio", { name: "Toàn môn" }));
    expect(latest.block.knowledgeScope).toBeNull();
  });
});

describe("biểu mẫu nhóm: chùm", () => {
  it("điều kiện riêng từng chùm: slots luôn đủ số chùm, đổi số chùm thì cắt và báo chùm bị bỏ", async () => {
    const user = userEvent.setup();
    const { latest } = renderEditor(setBlock({ slots: [{ index: 5, tagCodes: ["REVIEW"] }, { index: 1 }, { index: 2 }, { index: 3 }, { index: 4 }] }));
    const table = screen.getByRole("region", { name: "Điều kiện của 5 chùm" });
    expect(within(table).getAllByRole("row")).toHaveLength(6);
    expect(within(table).getByText("Cần rà soát")).toBeInTheDocument();

    await user.click(screen.getByRole("button", { name: "Sửa điều kiện chùm 2" }));
    const editor = screen.getByRole("group", { name: "Điều kiện chùm 2" });
    await user.click(within(editor).getByRole("checkbox", { name: "Có đồ thị" }));
    await user.click(within(editor).getByRole("checkbox", { name: "Câu bẫy" }));
    expect(latest.block.slots).toHaveLength(latest.block.setCount ?? -1);
    expect(latest.block.slots?.map((slot) => slot.index)).toEqual([1, 2, 3, 4, 5]);
    expect(latest.block.slots?.[1]?.tagCodes).toEqual(["GRAPH", "TRAP"]);
    expect(latest.block.slots?.[4]?.tagCodes).toEqual(["REVIEW"]);

    const sets = screen.getByRole("textbox", { name: /Số chùm/ });
    await user.clear(sets);
    await user.type(sets, "4");
    expect(latest.block.setCount).toBe(4);
    expect(latest.block.questionCount).toBe(20);
    expect(latest.block.slots).toHaveLength(4);
    expect(screen.getByText(/Điều kiện riêng của chùm 5 đã bị bỏ khỏi bản nháp/)).toBeInTheDocument();
  });

  it("chùm chưa đặt điều kiện thì nhóm không có slots; xóa điều kiện cuối thì bỏ slots", async () => {
    const user = userEvent.setup();
    const { latest } = renderEditor(setBlock());
    await user.click(screen.getByRole("button", { name: "Sửa điều kiện chùm 3" }));
    const editor = screen.getByRole("group", { name: "Điều kiện chùm 3" });
    await user.click(within(editor).getByRole("checkbox", { name: "Câu bẫy" }));
    expect(latest.block.slots).toHaveLength(5);
    await user.click(screen.getByRole("button", { name: "Xóa điều kiện chùm 3" }));
    expect(latest.block).not.toHaveProperty("slots");
  });

  it("điều kiện theo vị trí câu con: thêm dòng, báo thiếu MAPID và vượt số câu con, bỏ dòng", async () => {
    const user = userEvent.setup();
    const { latest } = renderEditor(setBlock());
    await user.click(screen.getByRole("button", { name: "Thêm điều kiện theo vị trí" }));
    expect(latest.block.childConditions).toEqual([{ from: 5, to: 5, nodeIds: [] }]);
    const row = screen.getByRole("group", { name: "Điều kiện vị trí 1" });
    expect(within(row).getByRole("alert")).toHaveTextContent("Chọn ít nhất một MAPID.");
    const to = within(row).getByRole("textbox", { name: "Đến câu con số" });
    await user.clear(to);
    await user.type(to, "7");
    expect(within(row).getByRole("alert")).toHaveTextContent("Vị trí vượt số câu con mỗi chùm (5).");
    await user.click(within(row).getByRole("button", { name: "Bỏ Điều kiện vị trí 1" }));
    expect(latest.block).not.toHaveProperty("childConditions");
  });
});

describe("biểu mẫu nhóm: công tắc và dải vị trí", () => {
  it("công tắc 'Đảo thứ tự các chùm' ẩn khi không shuffleEditable, chỉ còn dòng chú thích", () => {
    renderEditor(setBlock({ shuffleEditable: false }));
    expect(screen.queryByRole("switch", { name: "Đảo thứ tự các chùm" })).not.toBeInTheDocument();
    expect(screen.getByText("Câu và chùm trong nhóm luôn được đảo; câu con giữ thứ tự.")).toBeInTheDocument();
  });

  it("công tắc 'Đảo thứ tự các chùm' hiện khi shuffleEditable, mặc định bật, tắt thì lưu shuffleUnits = false", async () => {
    const user = userEvent.setup();
    const { latest } = renderEditor(setBlock({ shuffleEditable: true }));
    const toggle = screen.getByRole("switch", { name: "Đảo thứ tự các chùm" });
    expect(toggle).toBeChecked();
    await user.click(toggle);
    expect(latest.block.shuffleUnits).toBe(false);
    expect(toggle).not.toBeChecked();
  });

  it("công tắc 'Tạm bỏ qua' chỉ cho nhóm Tự luận; bật thì hiện nhãn 'Tự luận – tạm bỏ qua'", async () => {
    const user = userEvent.setup();
    const { latest, unmount } = renderEditor(makeBlock({ questionType: "ESSAY", questionCount: 3 }));
    const toggle = screen.getByRole("switch", { name: "Tạm bỏ qua" });
    expect(toggle).not.toBeChecked();
    await user.click(toggle);
    expect(latest.block.omitted).toBe(true);
    expect(screen.getByText("Tự luận – tạm bỏ qua")).toBeInTheDocument();
    unmount();
    renderEditor(makeBlock());
    expect(screen.queryByRole("switch", { name: "Tạm bỏ qua" })).not.toBeInTheDocument();
  });

  describe("mẫu khóa nhóm đã lược (H43)", () => {
    const TEMPLATE_GROUP = { questionType: "ESSAY", questionCount: 1, templateGroupCode: "HSCA_VAN_PARAGRAPH" } as const;

    it("nhóm của mẫu đang tạm bỏ qua: công tắc khóa, lý do nối bằng aria-describedby", async () => {
      const user = userEvent.setup();
      const { latest } = renderEditor(makeBlock({ ...TEMPLATE_GROUP, omitted: true }), { omittedLocked: true });
      const toggle = screen.getByRole("switch", { name: "Tạm bỏ qua" });
      expect(toggle).toBeDisabled();
      expect(toggle).toBeChecked();
      const reason = screen.getByText("Mẫu đã khóa nhóm này: nhóm đã lược của mẫu không bật lại được.");
      expect(toggle).toHaveAccessibleDescription(expect.stringContaining(reason.textContent!));
      await user.click(toggle);
      expect(latest.block.omitted).toBe(true);
    });

    it("nhóm đã bật từ bản nháp cũ vẫn tắt/bật được để sửa lại; tắt rồi thì khóa lại", async () => {
      const user = userEvent.setup();
      const { latest } = renderEditor(makeBlock({ ...TEMPLATE_GROUP, omitted: false }), { omittedLocked: true });
      const toggle = screen.getByRole("switch", { name: "Tạm bỏ qua" });
      expect(toggle).toBeEnabled();
      expect(screen.queryByText(/Mẫu đã khóa nhóm này/)).not.toBeInTheDocument();
      await user.click(toggle);
      expect(latest.block.omitted).toBe(true);
      expect(toggle).toBeDisabled();
    });

    it("mẫu không đặt cờ (ví dụ SP2E): công tắc vẫn bật tắt được", async () => {
      const user = userEvent.setup();
      const { latest } = renderEditor(makeBlock({ ...TEMPLATE_GROUP, omitted: true }), { omittedLocked: false });
      const toggle = screen.getByRole("switch", { name: "Tạm bỏ qua" });
      expect(toggle).toBeEnabled();
      await user.click(toggle);
      expect(latest.block.omitted).toBe(false);
    });

    it("nhóm thêm tay (không có mã nhóm mẫu) không bị khóa, như bộ kiểm tra", () => {
      renderEditor(makeBlock({ questionType: "ESSAY", questionCount: 1, omitted: true }), { omittedLocked: true });
      expect(screen.getByRole("switch", { name: "Tạm bỏ qua" })).toBeEnabled();
    });
  });

  it("hiện dải vị trí do hệ thống tính", () => {
    renderEditor(makeBlock({ positionRange: { from: 11, to: 14 } }));
    expect(screen.getByText("Câu 11–14")).toBeInTheDocument();
  });

  it("dải một câu ghi 'Câu 46'", () => {
    renderEditor(makeBlock({ positionRange: { from: 46, to: 46 } }));
    expect(screen.getByText("Câu 46")).toBeInTheDocument();
  });
});

describe("mỏ neo trường", () => {
  it("mỗi trường có điều khiển nhận focus mang data-field block-<id>-<trường>; trường không hiện thì không có mỏ neo", async () => {
    const user = userEvent.setup();
    const { container } = renderEditor(setBlock({ shuffleEditable: true, slots: [{ index: 1, tagCodes: ["TRAP"] }, { index: 2 }, { index: 3 }, { index: 4 }, { index: 5 }], itemShape: undefined }));
    await openConditionMenu(user);
    await user.click(screen.getByRole("menuitem", { name: /Nhãn hoặc loại văn bản/ }));
    await openConditionMenu(user);
    await user.click(screen.getByRole("menuitem", { name: /Điểm mỗi câu/ }));
    const present = ["name", "mode", "questionType", "childAnswerType", "setCount", "setSize", "scope", "conditions", "tags", "points", "slots", "childConditions", "shuffle"] as const;
    for (const field of present) {
      expect(container.querySelector(`[data-field="${blockFieldAnchor("b1", field)}"]`), field).not.toBeNull();
    }
    for (const field of ["questionCount", "itemShape", "answerFormats", "omitted"] as const) {
      expect(container.querySelector(`[data-field="${blockFieldAnchor("b1", field)}"]`), field).toBeNull();
    }
    expect(BLOCK_FIELDS).toEqual(expect.arrayContaining([...present]));
    expect(focusBlockField("b1", "name", container)).toBe(true);
    await waitFor(() => expect(screen.getByRole("textbox", { name: /Tên nhóm/ })).toHaveFocus());
    expect(focusBlockField("b1", "omitted", container)).toBe(false);
  });
});
