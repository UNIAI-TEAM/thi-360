import { screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";
import reference from "@/test/fixtures/content-reference.json";
import { renderWithProviders } from "@/test/render";
import type { ContentReference } from "@/features/content/types";
import { ExamStructureSubjectPanel } from "../components/exam-structure-subject-panel";
import { overviewRowsFromTemplate } from "../create-exam-overview";
import { subjectAllocation, type SubjectAllocation } from "../subject-matrix";
import type { AnswerQuotas, ExamPart, ExamSection, ExamTemplate, ExamTemplateBlock, QuestionBlock } from "../types";

/**
 * UNI-2754 (AD-25, phương án A): chỉ tiêu dạng trả lời của môn có dòng "Bộ câu hỏi" (QUESTION_SET). Khi môn khai dòng này, câu
 * con của nhóm chùm chỉ tính vào dòng này (không tính vào dạng của câu con); môn không khai thì giữ cách tính cũ.
 */

vi.mock(import("@/features/content/api"), async (importOriginal) => {
  const actual = await importOriginal();
  const { getContentReferenceFixture: getContentReference } = await import("@/test/fixtures/content-reference");
  return { ...actual, getContentReference, defaultContentApiDeps: { getContentReference } };
});
vi.mock(import("../queries"), async (importOriginal) => {
  const actual = await importOriginal();
  return { ...actual, useSubjectAvailableQuestions: () => ({ isError: false, data: { total: 120, byAnswerType: [] } }) as never };
});

const AXES = { grades: reference.grades.map((grade) => grade.code), difficulties: reference.difficulties.map((item) => item.code) };

const block = (over: Partial<QuestionBlock>): QuestionBlock => ({
  id: "b",
  name: "Nhóm",
  questionType: null,
  questionCount: null,
  setCount: null,
  setSize: null,
  requiredPointsPerQuestion: 1,
  knowledgeScope: null,
  tagCodes: [],
  matrix: null,
  childAnswerType: null,
  templateGroupCode: null,
  answerTypeLocked: false,
  ...over,
});

/** Nhóm câu đơn mở lấy phần còn lại + một nhóm chùm `sets` × `size` câu con dạng `child`. */
const section = (quotas: AnswerQuotas, sets: number, size: number, child: QuestionBlock["childAnswerType"] = "SINGLE_CHOICE"): ExamSection => ({
  id: "s1",
  subjectCode: "TOAN",
  questionCount: 50,
  knowledgeScope: null,
  answerQuotas: quotas,
  matrix: null,
  choiceGroup: null,
  blocks: [
    block({ id: "open", name: "Câu đơn", questionType: null, questionCount: null }),
    block({ id: "set", name: "Chùm", questionType: "QUESTION_SET", questionCount: sets * size, setCount: sets, setSize: size, childAnswerType: child }),
  ],
});

const WITH_SET_ROW: AnswerQuotas = {
  unit: "COUNT",
  items: [
    { answerType: "SINGLE_CHOICE", value: 32 },
    { answerType: "SHORT_ANSWER", value: 15 },
    { answerType: "QUESTION_SET", value: 3 },
  ],
};
const WITHOUT_SET_ROW: AnswerQuotas = {
  unit: "COUNT",
  items: [
    { answerType: "SINGLE_CHOICE", value: 35 },
    { answerType: "SHORT_ANSWER", value: 15 },
  ],
};

const rowOf = (result: SubjectAllocation, key: string) => result.answerTypes!.find((row) => row.key === key)!;

describe("tính số đã phân bổ khi môn có dòng Bộ câu hỏi", () => {
  it("32 / 15 / 3: chùm 1 × 3 câu con chỉ tính vào Bộ câu hỏi (3/3), không trừ vào dạng của câu con; nhóm mở phủ vừa đủ 32 + 15", () => {
    const result = subjectAllocation(section(WITH_SET_ROW, 1, 3), AXES, 50);
    expect(rowOf(result, "QUESTION_SET")).toMatchObject({ quota: 3, allocated: 3, missing: 0 });
    expect(rowOf(result, "SINGLE_CHOICE")).toMatchObject({ quota: 32, allocated: 0, missing: 32 });
    expect(result).toMatchObject({ allocated: 50, missing: 0, openAllocated: 47, openMissing: 0 });
    expect(result.openSplit).toEqual([
      { key: "SINGLE_CHOICE", count: 32 },
      { key: "SHORT_ANSWER", count: 15 },
    ]);
  });

  it("nhóm câu đơn mở không bao giờ phủ dòng Bộ câu hỏi: không có nhóm chùm thì dòng còn thiếu 3 và nhóm mở vượt phần của nó", () => {
    const noSets: ExamSection = { ...section(WITH_SET_ROW, 1, 3), blocks: [block({ id: "open", name: "Câu đơn" })] };
    const result = subjectAllocation(noSets, AXES, 50);
    expect(rowOf(result, "QUESTION_SET")).toMatchObject({ allocated: 0, missing: 3 });
    expect(result).toMatchObject({ openAllocated: 50, openMissing: -3 });
  });

  it("môn không khai dòng Bộ câu hỏi: giữ cách tính cũ, câu con trừ vào dạng của chính nó (35 → 25)", () => {
    const result = subjectAllocation(section(WITHOUT_SET_ROW, 2, 5), AXES, 50);
    expect(rowOf(result, "SINGLE_CHOICE")).toMatchObject({ quota: 35, allocated: 10, missing: 25 });
    expect(result.answerTypes!.some((row) => row.key === "QUESTION_SET")).toBe(false);
    expect(result).toMatchObject({ openAllocated: 40, openMissing: 0 });
  });
});

describe("màn môn có dòng Bộ câu hỏi", () => {
  function renderPanel(current: ExamSection) {
    const part = { id: "p1", sections: [current], options: [] } as unknown as ExamPart;
    return renderWithProviders(
      <ExamStructureSubjectPanel
        context={{ part, option: null, section: current, location: { partId: "p1", optionId: null, sectionId: "s1" } }}
        subjectName="Toán học"
        axes={AXES}
        labels={{ grade: (code) => code, difficulty: (code) => code }}
        reference={reference as unknown as ContentReference}
        onChange={() => {}}
        onSelectBlock={() => {}}
        onAddBlock={() => {}}
      />,
    );
  }

  it("32 / 15 / 3: dòng 'Bộ câu hỏi' đã phân bổ 3, đủ, tổng 50 / 50 không báo lệch", () => {
    renderPanel(section(WITH_SET_ROW, 1, 3));
    const table = screen.getByRole("region", { name: "Dạng trả lời" });
    const row = within(table).getByRole("rowheader", { name: "Bộ câu hỏi" }).closest("tr") as HTMLElement;
    expect(within(row).getByRole("textbox", { name: "Chỉ tiêu Bộ câu hỏi" })).toHaveValue("3");
    expect(Array.from(row.querySelectorAll("td"), (cell) => cell.textContent)).toEqual(expect.arrayContaining(["3", "3", "0"]));
    expect(within(row).queryByText(/Vượt|Thiếu/)).not.toBeInTheDocument();
    expect(screen.queryByText(/Tổng chỉ tiêu/)).not.toBeInTheDocument();
    expect(screen.queryByText("Chưa khai chỉ tiêu")).not.toBeInTheDocument();
  });

  it("thêm dạng trả lời: 'Bộ câu hỏi' có trong danh sách dạng chưa khai", async () => {
    const user = userEvent.setup();
    renderPanel(section(WITHOUT_SET_ROW, 2, 5));
    await user.click(screen.getByRole("combobox", { name: "Thêm dạng trả lời" }));
    expect(await screen.findByRole("option", { name: "Bộ câu hỏi" })).toBeInTheDocument();
  });
});

describe("tổng quan bước 1: lệnh hỏi của nhóm chùm (create-exam-overview)", () => {
  const set = (quotas: AnswerQuotas | null): ExamTemplate => {
    const blocks: ExamTemplateBlock[] = [
      {
        name: "Chùm Đúng/Sai",
        questionType: "QUESTION_SET",
        questionCount: 6,
        requiredPointsPerQuestion: 6,
        setCount: 2,
        setSize: 3,
        childAnswerType: "TRUE_FALSE",
        itemShape: { statements: 4 },
      },
    ];
    return {
      code: "VSAT_X",
      kind: "VSAT",
      scope: "SUBJECT",
      name: "Mẫu",
      timing: "SHARED",
      durationMinutes: 90,
      maxScore: 150,
      fixedBlockCounts: true,
      parts: [{ name: "Phần 1", questionCount: 6, durationMinutes: null, mode: "FIXED", sections: [{ subjectCode: "MATH", questionCount: null, blocks, answerQuotas: quotas }] }],
    } as unknown as ExamTemplate;
  };

  it("môn không có dòng Bộ câu hỏi: chùm 6 câu × 4 ý = 24 lệnh", () => {
    expect(overviewRowsFromTemplate(set(null), "SUBJECT")[1]!.block!.commands).toBe(24);
  });

  it("môn có dòng Bộ câu hỏi: số lệnh hỏi trên đề không đổi (24); chỉ tiêu dạng trả lời không đổi cách đếm lệnh", () => {
    const quotas: AnswerQuotas = { unit: "COUNT", items: [{ answerType: "QUESTION_SET", value: 6 }] };
    expect(overviewRowsFromTemplate(set(quotas), "SUBJECT")[1]!.block!.commands).toBe(24);
  });
});
