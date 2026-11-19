import { screen } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import reference from "@/test/fixtures/content-reference.json";
import { renderWithProviders } from "@/test/render";
import type { ContentReference } from "@/features/content/types";
import { ExamStructureSubjectPanel } from "../components/exam-structure-subject-panel";
import type { BlockMatrix, ExamPart, ExamSection, QuestionBlock } from "../types";

/** UNI-2752 (mục C, AD-15): hai bảng "Theo khối lớp" và "Theo độ khó" của môn chỉ hiện khi một nhóm khai chỉ tiêu riêng. */

vi.mock(import("@/features/content/api"), async (importOriginal) => {
  const actual = await importOriginal();
  const { getContentReferenceFixture: getContentReference } = await import("@/test/fixtures/content-reference");
  return { ...actual, getContentReference, defaultContentApiDeps: { getContentReference } };
});
vi.mock(import("../queries"), async (importOriginal) => {
  const actual = await importOriginal();
  return { ...actual, useSubjectAvailableQuestions: () => ({ isError: false, data: { total: 120, byAnswerType: [] } }) as never };
});

const GRADES = reference.grades.map((grade) => grade.code);
const DIFFICULTIES = reference.difficulties.map((item) => item.code);

const separate = (grades: number[], difficulties: number[]): BlockMatrix => ({
  mode: "SEPARATE",
  unit: "COUNT",
  cells: [],
  byGrade: GRADES.map((code, index) => ({ code, value: grades[index] ?? 0 })),
  byDifficulty: DIFFICULTIES.map((code, index) => ({ code, value: difficulties[index] ?? 0 })),
});

const block = (matrix: BlockMatrix | null): QuestionBlock => ({
  id: "b1",
  name: "Nhóm 1",
  questionType: "SINGLE_CHOICE",
  questionCount: 10,
  setCount: null,
  setSize: null,
  requiredPointsPerQuestion: 1,
  knowledgeScope: null,
  tagCodes: [],
  matrix,
  childAnswerType: null,
  templateGroupCode: null,
  answerTypeLocked: false,
});

function renderPanel(groupMatrix: BlockMatrix | null) {
  const section: ExamSection = {
    id: "s1",
    subjectCode: "TOAN",
    questionCount: 10,
    knowledgeScope: null,
    answerQuotas: null,
    matrix: separate([4, 3, 3], [4, 3, 3]),
    choiceGroup: null,
    blocks: [block(groupMatrix)],
  };
  const part = { id: "p1", sections: [section], options: [] } as unknown as ExamPart;
  return renderWithProviders(
    <ExamStructureSubjectPanel
      examId="exam-1"
      context={{ part, option: null, section, location: { partId: "p1", optionId: null, sectionId: "s1" } }}
      subjectName="Toán học"
      axes={{ grades: GRADES, difficulties: DIFFICULTIES }}
      labels={{ grade: (code) => code, difficulty: (code) => code }}
      reference={reference as unknown as ContentReference}
      onChange={() => {}}
      onSelectBlock={() => {}}
      onAddBlock={() => {}}
    />,
  );
}

describe("bảng chỉ tiêu khối lớp và độ khó của môn", () => {
  it("không nhóm nào khai chỉ tiêu khối lớp hay độ khó: chỉ còn dòng nguồn, không có hai bảng chi tiết", () => {
    renderPanel(null);
    expect(screen.getByText(/Nguồn phù hợp với bộ lọc của khối:/)).toBeInTheDocument();
    expect(screen.queryAllByRole("columnheader", { name: "Đã phân bổ ở nhóm" })).toHaveLength(0);
    expect(screen.queryAllByRole("columnheader", { name: "Còn thiếu" })).toHaveLength(0);
  });

  it("một nhóm khai chỉ tiêu riêng: dòng nguồn và cả hai bảng", () => {
    renderPanel(separate([5, 5, 0], [5, 5, 0]));
    expect(screen.getByText(/Nguồn phù hợp với bộ lọc của khối:/)).toBeInTheDocument();
    // Hai bảng chi tiết: một theo khối lớp, một theo độ khó (cột "Đã phân bổ ở nhóm" chỉ có ở hai bảng này).
    expect(screen.getAllByRole("columnheader", { name: "Đã phân bổ ở nhóm" })).toHaveLength(2);
    expect(screen.getAllByRole("columnheader", { name: "Còn thiếu" })).toHaveLength(2);
  });
});
