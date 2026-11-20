import { cleanup, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { useState } from "react";
import { describe, expect, it, vi } from "vitest";
import reference from "@/test/fixtures/content-reference.json";
import { renderWithProviders } from "@/test/render";
import type { ContentReference } from "@/features/content/types";
import { mapBlock, toApiParts } from "../api-mapping";
import { BlockEditor } from "../components/block-editor";
import { effectiveScope } from "../subject-matrix";
import type { ExamPart, ExamSection, QuestionBlock } from "../types";

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
  subjectCode: "TOAN",
  questionCount: 30,
  knowledgeScope: null,
  answerQuotas: null,
  matrix: null,
  choiceGroup: null,
  blocks: [],
};

function makeBlock(patch: Partial<QuestionBlock> = {}): QuestionBlock {
  return {
    id: "b1",
    name: "Nhóm 2",
    questionType: "QUESTION_SET",
    questionCount: 18,
    setCount: 6,
    setSize: 3,
    requiredPointsPerQuestion: 10,
    knowledgeScope: null,
    tagCodes: [],
    matrix: null,
    childAnswerType: null,
    templateGroupCode: null,
    answerTypeLocked: false,
    ...patch,
  };
}

function renderEditor(initial: QuestionBlock, options: { templateCode?: string; section?: ExamSection; subjectCode?: string } = {}) {
  const latest = { block: initial };
  function Harness() {
    const [block, setBlock] = useState(initial);
    return (
      <BlockEditor
        block={block}
        section={options.section ?? SECTION}
        subjectCode={options.subjectCode ?? "TOAN"}
        locationText="Phần 2 · Toán học · nhóm 2 / 2"
        position={1}
        siblingCount={1}
        axes={{ grades: reference.grades.map((grade) => grade.code), difficulties: reference.difficulties.map((item) => item.code) }}
        labels={{ grade: (code) => code, difficulty: (code) => code }}
        reference={reference as unknown as ContentReference}
        examKind="VACT"
        templateCode={options.templateCode}
        scoringDefaults={null}
        competencies={[]}
        onChange={(next) => {
          latest.block = next;
          setBlock(next);
        }}
        onMove={() => {}}
        onRemove={() => {}}
        onOpenSection={() => {}}
      />
    );
  }
  renderWithProviders(<Harness />);
  return latest;
}

async function typeInto(user: ReturnType<typeof userEvent.setup>, name: string | RegExp, text: string) {
  const field = screen.getByRole("textbox", { name });
  await user.clear(field);
  await user.type(field, text);
}

describe("chùm cỡ linh hoạt", () => {
  it("chỉ nhập tổng 18 gửi questionCount, bỏ setCount và setSize, ẩn hai ô số chùm; chuyển lại 6 × 3 gửi lại hai số", async () => {
    const user = userEvent.setup();
    const latest = renderEditor(makeBlock({ setCount: 5, setSize: 5, questionCount: 25 }));

    await user.click(screen.getByRole("radio", { name: "Chỉ nhập tổng câu con" }));
    expect(screen.queryByRole("textbox", { name: /^Số chùm/ })).not.toBeInTheDocument();
    expect(screen.queryByRole("textbox", { name: /^Số câu con mỗi chùm/ })).not.toBeInTheDocument();
    await typeInto(user, /^Tổng số câu con/, "18");
    expect(latest.block).toMatchObject({ questionCount: 18, setCount: null, setSize: null });
    expect(screen.getByText("18 câu con · tối đa 180 điểm")).toBeInTheDocument();

    await user.click(screen.getByRole("radio", { name: "Khai số chùm và số câu con" }));
    expect(screen.queryByRole("textbox", { name: /^Tổng số câu con/ })).not.toBeInTheDocument();
    await typeInto(user, /^Số chùm/, "6");
    await typeInto(user, /^Số câu con mỗi chùm/, "3");
    expect(latest.block).toMatchObject({ setCount: 6, setSize: 3, questionCount: 18 });
    expect(screen.getByText("6 chùm × 3 câu con = 18 câu con · tối đa 180 điểm")).toBeInTheDocument();
  });

  it("nhóm lưu ở chế độ tổng: gửi lên không có setCount, setSize; đọc lại mở đúng chế độ tổng", async () => {
    const flexible = makeBlock({ setCount: null, setSize: null });
    const part = { id: "p1", sections: [{ ...SECTION, blocks: [flexible] }] } as unknown as ExamPart;
    const sent = toApiParts([part]) as unknown as { sections: { blocks: Record<string, unknown>[] }[] }[];
    const apiBlock = sent[0]!.sections[0]!.blocks[0]!;
    expect(apiBlock).not.toHaveProperty("setCount");
    expect(apiBlock).not.toHaveProperty("setSize");
    expect(apiBlock).toMatchObject({ questionCount: 18 });

    const reloaded = mapBlock(apiBlock as Parameters<typeof mapBlock>[0]);
    expect(reloaded).toMatchObject({ questionCount: 18, setCount: null, setSize: null });
    renderEditor(reloaded);
    expect(screen.getByRole("radio", { name: "Chỉ nhập tổng câu con" })).toBeChecked();
    expect(screen.getByRole("textbox", { name: /^Tổng số câu con/ })).toHaveValue("18");
  });

  it("chuyển sang 'Chỉ nhập tổng câu con' khi chùm có điều kiện riêng: bỏ slots và báo chùm bị bỏ", async () => {
    const user = userEvent.setup();
    const latest = renderEditor(
      makeBlock({ setCount: 3, setSize: 2, questionCount: 6, slots: [{ index: 1, tagCodes: ["REVIEW"] }, { index: 2 }, { index: 3, tagCodes: ["GRAPH"] }] }),
    );
    expect(screen.queryByText(/đã bị bỏ khỏi bản nháp/)).not.toBeInTheDocument();
    await user.click(screen.getByRole("radio", { name: "Chỉ nhập tổng câu con" }));
    expect(latest.block).not.toHaveProperty("slots");
    expect(screen.getByText(/Điều kiện riêng của chùm 1, 3 đã bị bỏ khỏi bản nháp/)).toHaveAttribute("role", "status");
    await user.click(screen.getByRole("radio", { name: "Khai số chùm và số câu con" }));
    expect(screen.queryByText(/đã bị bỏ khỏi bản nháp/)).not.toBeInTheDocument();
  });

  it("chuyển sang 'Chỉ nhập tổng câu con' khi chùm chưa có điều kiện riêng: không có thông báo", async () => {
    const user = userEvent.setup();
    renderEditor(makeBlock());
    await user.click(screen.getByRole("radio", { name: "Chỉ nhập tổng câu con" }));
    expect(screen.queryByText(/đã bị bỏ khỏi bản nháp/)).not.toBeInTheDocument();
  });

  it("chuyển sang 'Chỉ nhập tổng câu con' bỏ điều kiện theo vị trí câu con, ẩn bảng điều kiện đó và ghi lý do; chuyển lại thì bảng hiện trở lại", async () => {
    const user = userEvent.setup();
    const latest = renderEditor(makeBlock({ childConditions: [{ from: 1, to: 2, nodeIds: ["N1"] }] }));
    expect(screen.getByRole("region", { name: "Điều kiện theo vị trí câu con" })).toBeInTheDocument();
    await user.click(screen.getByRole("radio", { name: "Chỉ nhập tổng câu con" }));
    expect(latest.block).not.toHaveProperty("childConditions");
    expect(screen.queryByRole("region", { name: "Điều kiện theo vị trí câu con" })).not.toBeInTheDocument();
    expect(screen.getByText(/Điều kiện theo vị trí câu con không dùng được khi chỉ nhập tổng câu con/)).toBeInTheDocument();
    await user.click(screen.getByRole("radio", { name: "Khai số chùm và số câu con" }));
    expect(screen.getByRole("region", { name: "Điều kiện theo vị trí câu con" })).toBeInTheDocument();
    expect(screen.queryByText(/không dùng được khi chỉ nhập tổng câu con/)).not.toBeInTheDocument();
  });

  it("nhóm tải từ máy chủ ở chế độ tổng đã có điều kiện theo vị trí: ẩn bảng nhưng không tự xóa khi lưu", async () => {
    const user = userEvent.setup();
    const stored = [{ from: 1, to: 2, nodeIds: ["N1"] }];
    const latest = renderEditor(makeBlock({ setCount: null, setSize: null, childConditions: stored }));
    expect(screen.queryByRole("region", { name: "Điều kiện theo vị trí câu con" })).not.toBeInTheDocument();
    await typeInto(user, /^Tổng số câu con/, "12");
    expect(latest.block).toMatchObject({ questionCount: 12, childConditions: stored });
  });
});

describe("ô Mọi câu con thuộc chủ đề", () => {
  const scoped = { nodeIds: ["N1"] };

  it("chỉ hiện ở Theo chủ đề có MAPID của nhóm chùm; bật thì gửi everyChildInScope = true", async () => {
    const user = userEvent.setup();
    const latest = renderEditor(makeBlock({ knowledgeScope: scoped }));
    const box = screen.getByRole("checkbox", { name: "Mọi câu con thuộc chủ đề" });
    expect(box).not.toBeChecked();
    expect(screen.getByText(/chùm 2 câu Xác suất và 1 câu Hình học không được chọn/)).toBeInTheDocument();
    await user.click(box);
    expect(latest.block.knowledgeScope).toEqual({ nodeIds: ["N1"], everyChildInScope: true });
  });

  it("Toàn môn và nhóm câu đơn không có ô", () => {
    renderEditor(makeBlock({ knowledgeScope: null }));
    expect(screen.queryByRole("checkbox", { name: "Mọi câu con thuộc chủ đề" })).not.toBeInTheDocument();
  });

  it("nhóm câu đơn có MAPID không có ô", () => {
    renderEditor(makeBlock({ questionType: "SINGLE_CHOICE", setCount: null, setSize: null, knowledgeScope: scoped }));
    expect(screen.queryByRole("checkbox", { name: "Mọi câu con thuộc chủ đề" })).not.toBeInTheDocument();
  });

  it("bài từ mẫu VACT_REVIEW bật sẵn; tắt thì gửi everyChildInScope = false", async () => {
    const user = userEvent.setup();
    const latest = renderEditor(makeBlock({ knowledgeScope: scoped }), { templateCode: "VACT_REVIEW" });
    const box = screen.getByRole("checkbox", { name: "Mọi câu con thuộc chủ đề" });
    expect(box).toBeChecked();
    await user.click(box);
    expect(latest.block.knowledgeScope).toEqual({ nodeIds: ["N1"], everyChildInScope: false });
  });

  it("nhóm chùm của bài VACT_REVIEW có MAPID mà chưa có cờ: mở nhóm là lưu true, không cần bấm ô", () => {
    const latest = renderEditor(makeBlock({ knowledgeScope: scoped }), { templateCode: "VACT_REVIEW" });
    expect(screen.getByRole("checkbox", { name: "Mọi câu con thuộc chủ đề" })).toBeChecked();
    expect(latest.block.knowledgeScope).toEqual({ nodeIds: ["N1"], everyChildInScope: true });
    const part = { id: "p1", sections: [{ ...SECTION, blocks: [latest.block] }] } as unknown as ExamPart;
    const sent = toApiParts([part]) as unknown as { sections: { blocks: { knowledgeScope: unknown }[] }[] }[];
    expect(sent[0]!.sections[0]!.blocks[0]!.knowledgeScope).toEqual({ nodeIds: ["N1"], everyChildInScope: true });
  });

  it("bài VACT_REVIEW: nhóm câu đơn có MAPID đổi sang Câu chùm thì lưu everyChildInScope = true", async () => {
    const user = userEvent.setup();
    const single = makeBlock({ questionType: "SINGLE_CHOICE", setCount: null, setSize: null, questionCount: 4, knowledgeScope: scoped });
    const review = renderEditor(single, { templateCode: "VACT_REVIEW" });
    expect(review.block.knowledgeScope).toEqual(scoped);
    await user.click(screen.getByRole("radio", { name: "Câu chùm" }));
    expect(review.block.knowledgeScope).toEqual({ nodeIds: ["N1"], everyChildInScope: true });
    expect(screen.getByRole("checkbox", { name: "Mọi câu con thuộc chủ đề" })).toBeChecked();
  });

  it("bài không phải VACT_REVIEW: nhóm chùm có MAPID chưa có cờ hiện ô tắt và không gửi cờ", () => {
    const latest = renderEditor(makeBlock({ knowledgeScope: scoped }));
    expect(screen.getByRole("checkbox", { name: "Mọi câu con thuộc chủ đề" })).not.toBeChecked();
    expect(latest.block.knowledgeScope).toEqual(scoped);
  });
});

describe("phạm vi riêng của chùm: hết MAPID thì kế thừa môn, không gửi cờ", () => {
  const nodeId = reference.knowledgeTree[0]!.children![0]!.id;
  const SECTION_SCOPE = { nodeIds: ["kn-0D2"] };
  const REVIEW_SECTION: ExamSection = { ...SECTION, knowledgeScope: SECTION_SCOPE };
  const review = { templateCode: "VACT_REVIEW", section: REVIEW_SECTION };

  function sentScope(block: QuestionBlock) {
    const part = { id: "p1", sections: [{ ...SECTION, blocks: [block] }] } as unknown as ExamPart;
    const sent = toApiParts([part]) as unknown as { sections: { blocks: { knowledgeScope?: unknown }[] }[] }[];
    return sent[0]!.sections[0]!.blocks[0]!.knowledgeScope;
  }
  const scopeBox = () => screen.getByText("Phạm vi kiến thức").closest("fieldset")!;
  const removeLastChip = (user: ReturnType<typeof userEvent.setup>) => user.click(screen.getByRole("button", { name: /^Bỏ kiến thức 0D1 / }));
  const toWholeSubject = (user: ReturnType<typeof userEvent.setup>) => user.click(within(scopeBox()).getByRole("radio", { name: "Toàn môn" }));
  async function applyEmptyPicker(user: ReturnType<typeof userEvent.setup>) {
    await user.click(within(scopeBox()).getByRole("button", { name: "Chọn kiến thức" }));
    const dialog = await screen.findByRole("dialog");
    await user.click(within(dialog).getByRole("button", { name: /^Bỏ 0D1 / }));
    await user.click(within(dialog).getByRole("button", { name: "Áp dụng (0)" }));
  }
  const paths = [
    ["bỏ chip MAPID cuối cùng", removeLastChip],
    ["chọn Toàn môn", toWholeSubject],
    ["áp dụng hộp chọn với 0 mục", applyEmptyPicker],
  ] as const;

  it.each(paths)("chùm VACT_REVIEW có MAPID và cờ true, %s: lưu knowledgeScope null và dùng phạm vi của môn", async (_name, act) => {
    const user = userEvent.setup();
    const latest = renderEditor(makeBlock({ knowledgeScope: { nodeIds: [nodeId], everyChildInScope: true } }), review);
    expect(screen.getByRole("checkbox", { name: "Mọi câu con thuộc chủ đề" })).toBeChecked();
    await act(user);
    expect(latest.block.knowledgeScope).toBeNull();
    expect(sentScope(latest.block)).toBeUndefined();
    expect(effectiveScope(latest.block, REVIEW_SECTION)).toEqual(SECTION_SCOPE);
    expect(screen.queryByRole("checkbox", { name: "Mọi câu con thuộc chủ đề" })).not.toBeInTheDocument();
  });

  it.each(paths)("chùm VACT_REVIEW có MAPID và cờ false, %s: cũng lưu null", async (_name, act) => {
    const user = userEvent.setup();
    const latest = renderEditor(makeBlock({ knowledgeScope: { nodeIds: [nodeId], everyChildInScope: false } }), review);
    await act(user);
    expect(latest.block.knowledgeScope).toBeNull();
    expect(sentScope(latest.block)).toBeUndefined();
  });

  it.each(paths)("chùm không có cờ, %s: lưu null, không còn {nodeIds: []}", async (_name, act) => {
    const user = userEvent.setup();
    const latest = renderEditor(makeBlock({ knowledgeScope: { nodeIds: [nodeId] } }));
    await act(user);
    expect(latest.block.knowledgeScope).toBeNull();
    expect(sentScope(latest.block)).toBeUndefined();
  });

  it.each(paths)("chùm có competencyCodes, %s: giữ năng lực, bỏ cờ", async (_name, act) => {
    const user = userEvent.setup();
    const latest = renderEditor(makeBlock({ knowledgeScope: { nodeIds: [nodeId], competencyCodes: ["NL1"], everyChildInScope: true } }), review);
    await act(user);
    expect(latest.block.knowledgeScope).toEqual({ nodeIds: [], competencyCodes: ["NL1"] });
    expect(sentScope(latest.block)).toEqual({ nodeIds: [], competencyCodes: ["NL1"] });
  });

  it("về null rồi chọn lại MAPID trên bài VACT_REVIEW: ô hiện bật và lưu true", async () => {
    const user = userEvent.setup();
    const latest = renderEditor(makeBlock({ knowledgeScope: { nodeIds: [nodeId], everyChildInScope: true } }), { ...review, subjectCode: "MATH" });
    await removeLastChip(user);
    expect(latest.block.knowledgeScope).toBeNull();
    // Bỏ chip cuối: dữ liệu về phạm vi của môn nên ô chọn cũng về "Toàn môn" (FU-FE3AA-V1 V1-04), chọn lại "Theo chủ đề" để thêm MAPID.
    expect(within(scopeBox()).getByRole("radio", { name: "Toàn môn" })).toBeChecked();
    await user.click(within(scopeBox()).getByRole("radio", { name: "Theo chủ đề" }));
    await user.click(within(scopeBox()).getByRole("button", { name: "Chọn kiến thức" }));
    const dialog = await screen.findByRole("dialog");
    await user.click(await within(dialog).findByRole("checkbox", { name: /Mệnh đề và tập hợp/ }));
    await user.click(within(dialog).getByRole("button", { name: "Áp dụng (1)" }));
    expect(screen.getByRole("checkbox", { name: "Mọi câu con thuộc chủ đề" })).toBeChecked();
    expect(latest.block.knowledgeScope).toEqual({ nodeIds: [nodeId], everyChildInScope: true });
    expect(sentScope(latest.block)).toEqual({ nodeIds: [nodeId], everyChildInScope: true });
  });

  it("chùm thành câu đơn: bỏ cờ; hết MAPID thì phạm vi về null", async () => {
    const user = userEvent.setup();
    const withNodes = renderEditor(makeBlock({ knowledgeScope: { nodeIds: [nodeId], everyChildInScope: true } }));
    await user.click(screen.getByRole("radio", { name: "Câu đơn" }));
    expect(withNodes.block.knowledgeScope).toEqual({ nodeIds: [nodeId] });
    cleanup();
    const withoutNodes = renderEditor(makeBlock({ knowledgeScope: { nodeIds: [], everyChildInScope: true } }));
    await user.click(screen.getByRole("radio", { name: "Câu đơn" }));
    expect(withoutNodes.block.knowledgeScope).toBeNull();
  });
});

describe("dòng tổng của nhóm Đúng/Sai", () => {
  const trueFalse = (count: number) =>
    makeBlock({
      questionType: "TRUE_FALSE",
      questionCount: count,
      setCount: null,
      setSize: null,
      requiredPointsPerQuestion: 1,
      itemShape: { statements: 4 },
    });

  it("4 câu, 4 mệnh đề, 1 điểm mỗi câu ghi 4 câu · 16 ý · tối đa 4 điểm; đổi còn 3 câu thì 3 câu · 12 ý · tối đa 3 điểm", async () => {
    const user = userEvent.setup();
    renderEditor(trueFalse(4));
    expect(screen.getByText("4 câu · 16 ý · tối đa 4 điểm")).toBeInTheDocument();
    await typeInto(user, /^Số câu cần lấy/, "3");
    expect(screen.getByText("3 câu · 12 ý · tối đa 3 điểm")).toBeInTheDocument();
  });
});
