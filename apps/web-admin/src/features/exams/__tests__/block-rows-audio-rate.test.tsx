import { cleanup, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { useState } from "react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import reference from "@/test/fixtures/content-reference.json";
import en from "@/messages/en.json";
import { renderWithProviders } from "@/test/render";
import type { ContentReference } from "@/features/content/types";
import { BlockEditor } from "../components/block-editor";
import type { AudioRequirement, ExamSection, QuestionBlock } from "../types";

/**
 * FE-7, bước 2: dòng lấy câu có nhãn audio, nguồn khả dụng theo audio, tổng câu và tỷ lệ trong phần, nhập theo tỷ lệ (chùm
 * phải ra số nguyên chùm), mẫu khóa dòng (R-130, R-135, R-142).
 */

vi.mock(import("@/features/content/api"), async (importOriginal) => {
  const actual = await importOriginal();
  const { getContentReferenceFixture: getContentReference } = await import("@/test/fixtures/content-reference");
  return { ...actual, getContentReference, defaultContentApiDeps: { getContentReference } };
});

const availableMock = vi.hoisted(() => ({ queries: [] as unknown[], total: 42 }));
vi.mock(import("../queries"), async (importOriginal) => {
  const actual = await importOriginal();
  return {
    ...actual,
    useAvailableQuestions: (query: unknown) => {
      availableMock.queries.push(query);
      return { isError: false, data: { total: availableMock.total, cells: [], byAnswerType: [] } } as never;
    },
  };
});

beforeEach(() => {
  availableMock.queries.length = 0;
});

const section = (audioRequirement?: AudioRequirement): ExamSection => ({
  id: "s1",
  subjectCode: "ANH",
  questionCount: 35,
  knowledgeScope: null,
  answerQuotas: null,
  matrix: null,
  choiceGroup: null,
  blocks: [],
  ...(audioRequirement ? { audioRequirement } : {}),
});

const block = (patch: Partial<QuestionBlock> = {}): QuestionBlock => ({
  id: "b1",
  name: "Dòng 2 · Hội thoại 4 câu",
  questionType: "QUESTION_SET",
  questionCount: 8,
  setCount: 2,
  setSize: 4,
  requiredPointsPerQuestion: 1,
  knowledgeScope: null,
  tagCodes: [],
  matrix: null,
  childAnswerType: null,
  templateGroupCode: null,
  answerTypeLocked: false,
  ...patch,
});

function renderEditor(
  initial: QuestionBlock,
  options: { audio?: AudioRequirement; rowsLocked?: boolean; partTotal?: number | null; commandCount?: number | null; english?: boolean } = {},
) {
  const onRateInvalid = vi.fn();
  const latest = { block: initial };
  function Harness() {
    const [current, setCurrent] = useState(initial);
    return (
      <BlockEditor
        block={current}
        section={section(options.audio)}
        subjectCode="ANH"
        locationText="Nghe · Tiếng Anh · dòng 2 / 3"
        position={1}
        siblingCount={3}
        axes={{ grades: reference.grades.map((grade) => grade.code), difficulties: reference.difficulties.map((item) => item.code) }}
        labels={{ grade: (code) => code, difficulty: (code) => code }}
        reference={reference as unknown as ContentReference}
        examKind="HSCA"
        scoringDefaults={null}
        competencies={[]}
        partTotal={options.partTotal === undefined ? 35 : options.partTotal}
        rowsLocked={options.rowsLocked}
        onRateInvalid={onRateInvalid}
        commandCount={options.commandCount}
        onChange={(next) => {
          latest.block = next;
          setCurrent(next);
        }}
        onMove={() => {}}
        onRemove={() => {}}
        onOpenSection={() => {}}
      />
    );
  }
  renderWithProviders(<Harness />, options.english ? { locale: "en", messages: en } : undefined);
  return { latest, onRateInvalid };
}

describe("dòng lấy câu có audio (R-130, R-142)", () => {
  it("dòng 1, 2, 3 · phần Nghe: nhãn Có audio không đổi ở dòng, nguồn khả dụng theo API, tổng câu và tỷ lệ trong phần", () => {
    renderEditor(block(), { audio: "REQUIRED" });
    expect(screen.getByText("Có audio")).toBeInTheDocument();
    expect(screen.getByText("Điều kiện audio đặt theo phần, không đổi ở từng dòng.")).toBeInTheDocument();
    expect(screen.getByText("8 câu · 22,9% của phần")).toBeInTheDocument();
    expect(screen.getByText("Nguồn khả dụng: 42 câu có audio")).toBeInTheDocument();
    expect(availableMock.queries.at(-1)).toMatchObject({ subjectCode: "ANH", audioRequirement: "REQUIRED" });
    // Không có ô nào để đổi nhãn audio ở dòng.
    expect(screen.queryByRole("combobox", { name: /audio/i })).toBeNull();
  });

  it("dòng 1 · phần Đọc: nhãn Không có audio, đếm nguồn gửi FORBIDDEN; môn không có điều kiện audio thì không nhãn", () => {
    renderEditor(block(), { audio: "FORBIDDEN" });
    expect(screen.getByText("Không có audio")).toBeInTheDocument();
    expect(screen.getByText("Nguồn khả dụng: 42 câu không có audio")).toBeInTheDocument();
    expect(availableMock.queries.at(-1)).toMatchObject({ audioRequirement: "FORBIDDEN" });
  });

  it("môn không có điều kiện audio: không nhãn, không dòng nguồn theo audio", () => {
    renderEditor(block());
    expect(screen.queryByText("Có audio")).toBeNull();
    expect(screen.queryByText("Không có audio")).toBeNull();
    expect(screen.queryByText(/Nguồn khả dụng/)).toBeNull();
  });
});

describe("nhập theo tỷ lệ (R-142)", () => {
  it("dòng 4 · chùm cỡ 4 nhập 28,6% ra 10 câu: báo lỗi tại dòng, chặn lưu, số câu không đổi; 22,9% ra 8 câu thì hết lỗi", async () => {
    const user = userEvent.setup();
    const { latest, onRateInvalid } = renderEditor(block(), { audio: "REQUIRED" });
    const entry = screen.getByRole("radio", { name: "Số lượng" }).closest("fieldset")!;
    await user.click(within(entry).getByRole("radio", { name: "Tỷ lệ" }));
    const rate = screen.getByRole("textbox", { name: "Tỷ lệ trong phần (%)" });
    expect(rate).toHaveValue("22,9");

    await user.clear(rate);
    await user.type(rate, "28,6");
    const alert = await screen.findByRole("alert");
    expect(alert).toHaveTextContent("Không ghép được chùm nguyên.");
    expect(alert).toHaveTextContent("Tỷ lệ 28,6% ra 10 câu, không chia hết cho cỡ chùm 4 câu con. Chọn 8 hoặc 12 câu.");
    expect(rate).toBeInvalid();
    expect(rate).toHaveAccessibleDescription(/Chọn 8 hoặc 12 câu/);
    await waitFor(() => expect(onRateInvalid).toHaveBeenLastCalledWith(true));
    expect(latest.block.questionCount).toBe(8);

    await user.clear(rate);
    await user.type(rate, "34,3");
    await waitFor(() => expect(onRateInvalid).toHaveBeenLastCalledWith(false));
    expect(latest.block).toMatchObject({ questionCount: 12, setCount: 3, setSize: 4 });
    expect(screen.queryByRole("alert")).toBeNull();
  }, 20_000);
});

describe("mẫu khóa dòng (R-135)", () => {
  it("dòng 5 · số lượng và dạng chỉ đọc kèm lý do; nguồn câu vẫn sửa được; không xóa được dòng", () => {
    renderEditor(block(), { rowsLocked: true });
    expect(screen.getByText("Mẫu đã khóa dòng")).toBeInTheDocument();
    const reason = screen.getByText(/Bài cả môn giữ nguyên dạng và số lượng của dòng theo mẫu/);
    for (const name of ["Số chùm", /^Số câu con mỗi chùm/]) {
      const input = screen.getByRole("textbox", { name });
      expect(input).toHaveAttribute("readonly");
      expect(input).toHaveAttribute("aria-readonly", "true");
      expect(input.getAttribute("aria-describedby")).toContain(reason.id);
    }
    expect(screen.getByRole("textbox", { name: /^Loại câu hỏi/ })).toHaveAttribute("readonly");
    expect(screen.getByRole("button", { name: /^Xóa nhóm/ })).toHaveAttribute("aria-disabled", "true");
    expect(screen.queryByRole("radio", { name: "Số lượng" })).toBeNull();
    expect(screen.getByText(/Nguồn câu, phạm vi kiến thức và chỉ tiêu trong dòng vẫn sửa được/)).toBeInTheDocument();
  });

  it("F2 · ô số khóa có nền và chữ của ô bị khóa, ô sửa được thì không", () => {
    renderEditor(block(), { rowsLocked: true });
    const locked = screen.getByRole("textbox", { name: "Số chùm" });
    expect(locked).toHaveClass("bg-subtle", "text-text-muted");
    expect(locked).toHaveAttribute("readonly");
    cleanup();
    renderEditor(block());
    expect(screen.getByRole("textbox", { name: "Số chùm" })).not.toHaveClass("bg-subtle");
  });
});

describe("chùm trộn dạng (R-132)", () => {
  const mixed = block({
    name: "Bài đọc dùng chung – câu 8–15",
    setCount: 1,
    setSize: 8,
    questionCount: 8,
    requiredPointsPerQuestion: 6,
    positionRange: { from: 8, to: 15 },
    childConditions: [
      { from: 1, to: 2, nodeIds: [], answerType: "TRUE_FALSE", itemShape: { statements: 4 } },
      { from: 3, to: 8, nodeIds: [], answerType: "SINGLE_CHOICE", itemShape: { options: 4 } },
    ],
  });

  it("dòng 6 · hiện vị trí 1–2 Đúng/Sai, 3–8 chọn một; dòng tổng 8 câu, 14 lệnh do máy chủ tính, 48 điểm", () => {
    renderEditor(mixed, { commandCount: 14 });
    const table = screen.getByRole("table", { name: "Loại câu hỏi theo vị trí trong chùm" });
    const rows = within(table).getAllByRole("row").map((row) => Array.from(row.querySelectorAll("th,td"), (cell) => cell.textContent));
    expect(rows[1]).toEqual(["Vị trí 1–2", "Câu 8–9", "Đúng/Sai", "4 ý mỗi câu", "8", "12"]);
    expect(rows[2]).toEqual(["Vị trí 3–8", "Câu 10–15", "Một đáp án", "4 phương án", "6", "36"]);
    expect(screen.getByText("8 câu · 14 lệnh · 48 điểm")).toBeInTheDocument();
  });

  it("chưa có số lệnh từ máy chủ thì dòng tổng bỏ phần lệnh, không tự đoán", () => {
    renderEditor(mixed, { commandCount: null });
    expect(screen.getByText("8 câu · 48 điểm")).toBeInTheDocument();
  });

  it("R1-1 · khối 2 chùm × 4: bảng là của một chùm, dòng tổng tính cả khối 8 câu, 8 điểm với lệnh của máy chủ", () => {
    const twoSets = block({
      setCount: 2,
      setSize: 4,
      questionCount: 8,
      requiredPointsPerQuestion: 1,
      childConditions: [
        { from: 1, to: 2, nodeIds: [], answerType: "TRUE_FALSE", itemShape: { statements: 4 } },
        { from: 3, to: 4, nodeIds: [], answerType: "SINGLE_CHOICE", itemShape: { options: 4 } },
      ],
    });
    renderEditor(twoSets, { commandCount: 20 });
    expect(screen.getByText("Bảng trên là của một chùm; dòng tổng tính cho cả 2 chùm.")).toBeInTheDocument();
    expect(screen.getByText("8 câu · 20 lệnh · 8 điểm")).toBeInTheDocument();
  });

  it("R1-1 · vị trí chưa khai dạng: dòng tổng vẫn theo số câu của khối, không theo các vị trí đã khai", () => {
    const partial = block({
      setCount: 1,
      setSize: 4,
      questionCount: 4,
      requiredPointsPerQuestion: 2,
      childConditions: [{ from: 1, to: 2, nodeIds: [], answerType: "SINGLE_CHOICE" }],
    });
    renderEditor(partial, { commandCount: null });
    expect(screen.getByText("4 câu · 8 điểm")).toBeInTheDocument();
    expect(screen.queryByText(/chùm; dòng tổng/)).not.toBeInTheDocument();
  });

  it("R1-2 · mẫu khóa dòng: vị trí đã khai dạng không đổi dải, không bỏ được; vị trí chưa khai dạng (chỉ MAPID) vẫn sửa được", () => {
    const typed = block({
      setCount: 1,
      setSize: 8,
      questionCount: 8,
      childConditions: [
        { from: 1, to: 2, nodeIds: [], answerType: "TRUE_FALSE", itemShape: { statements: 4 } },
        { from: 3, to: 8, nodeIds: ["kn-1D4"] },
      ],
    });
    renderEditor(typed, { rowsLocked: true });
    const frozen = screen.getByRole("group", { name: "Điều kiện vị trí 1" });
    for (const name of ["Từ câu con số", "Đến câu con số"]) {
      expect(within(frozen).getByRole("textbox", { name })).toHaveAttribute("readonly");
    }
    expect(within(frozen).getByRole("button", { name: "Bỏ Điều kiện vị trí 1" })).toHaveAttribute("aria-disabled", "true");
    const open = screen.getByRole("group", { name: "Điều kiện vị trí 2" });
    for (const name of ["Từ câu con số", "Đến câu con số"]) {
      expect(within(open).getByRole("textbox", { name })).not.toHaveAttribute("readonly");
    }
    expect(within(open).getByRole("button", { name: "Bỏ Điều kiện vị trí 2" })).not.toHaveAttribute("aria-disabled");
  });

  it("R1-9 · vị trí Ghép đôi nhập được số vế trái, bảng vị trí tính lệnh hỏi theo số vế", async () => {
    const user = userEvent.setup();
    const matching = block({
      setCount: 1,
      setSize: 2,
      questionCount: 2,
      positionRange: { from: 8, to: 9 },
      childConditions: [{ from: 1, to: 2, nodeIds: [], answerType: "MATCHING" }],
    });
    renderEditor(matching);
    const table = screen.getByRole("table", { name: "Loại câu hỏi theo vị trí trong chùm" });
    const commands = () => Array.from(within(table).getAllByRole("row")[1]!.querySelectorAll("th,td"), (cell) => cell.textContent)[4];
    expect(commands()).toBe("2");
    await user.type(screen.getByRole("textbox", { name: "Số vế trái" }), "4");
    expect(commands()).toBe("8");
  });
});

describe("nhóm do mẫu khởi tạo (F4)", () => {
  it("tiếng Việt: tên nhóm nằm ở tiêu đề, dòng nguồn gốc không kèm mã nhóm của mẫu", () => {
    renderEditor(block({ templateGroupCode: "HSCA_TOAN_MULTI", name: "Phần I · Nhiều đáp án" }));
    expect(screen.getByText("Nhóm do mẫu khởi tạo.")).toBeInTheDocument();
    expect(screen.getByRole("heading", { level: 2 })).toHaveTextContent("Phần I · Nhiều đáp án");
    expect(document.body.textContent).not.toContain("HSCA_TOAN_MULTI");
  });

  it("tiếng Anh cũng không hiện mã nhóm", () => {
    renderEditor(block({ templateGroupCode: "HSCA_ANH_LISTEN_SET_4" }), { english: true });
    expect(screen.getByText("Group created from the template.")).toBeInTheDocument();
    expect(document.body.textContent).not.toContain("HSCA_ANH_LISTEN_SET_4");
  });

  it("nhóm thêm tay không có dòng này", () => {
    renderEditor(block({ templateGroupCode: null }));
    expect(screen.queryByText(/Nhóm do mẫu khởi tạo/)).toBeNull();
  });
});
