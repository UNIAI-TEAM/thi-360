import { beforeEach, describe, expect, it, vi } from "vitest";
import reference from "@/test/fixtures/content-reference.json";
import { renderWithProviders } from "@/test/render";
import type { ContentReference } from "@/features/content/types";
import { ExamStructureSubjectPanel } from "../components/exam-structure-subject-panel";
import { ReviewSubjectPanel } from "../components/review-subject-panel";
import type { AudioRequirement, ExamPart, ExamSection, QuestionBlock } from "../types";

/** FE-7 R-130, R-142: đếm nguồn cả môn (`count-subject`) mang điều kiện audio của môn, khớp số đếm từng dòng. */

vi.mock(import("@/features/content/api"), async (importOriginal) => {
  const actual = await importOriginal();
  const { getContentReferenceFixture: getContentReference } = await import("@/test/fixtures/content-reference");
  return { ...actual, getContentReference, defaultContentApiDeps: { getContentReference } };
});

const subjectCounts = vi.hoisted(() => ({ queries: [] as { subjectCode: string; groups: Record<string, unknown>[] }[] }));
vi.mock(import("../queries"), async (importOriginal) => {
  const actual = await importOriginal();
  return {
    ...actual,
    useSubjectAvailableQuestions: (query: unknown) => {
      subjectCounts.queries.push(query as never);
      return { isError: false, data: undefined } as never;
    },
  };
});

beforeEach(() => {
  subjectCounts.queries.length = 0;
});

const block: QuestionBlock = {
  id: "b1",
  name: "Dòng 1",
  questionType: "SINGLE_CHOICE",
  questionCount: 10,
  setCount: null,
  setSize: null,
  requiredPointsPerQuestion: 1,
  knowledgeScope: null,
  tagCodes: [],
  matrix: null,
  childAnswerType: null,
  templateGroupCode: null,
  answerTypeLocked: false,
};

const section = (audioRequirement?: AudioRequirement): ExamSection => ({
  id: "s1",
  subjectCode: "ANH",
  questionCount: 10,
  knowledgeScope: null,
  answerQuotas: null,
  matrix: null,
  choiceGroup: null,
  blocks: [block],
  ...(audioRequirement ? { audioRequirement } : {}),
});

const common = {
  examId: "exam-1",
  subjectName: "Tiếng Anh",
  axes: { grades: reference.grades.map((grade) => grade.code), difficulties: reference.difficulties.map((item) => item.code) },
  labels: { grade: (code: string) => code, difficulty: (code: string) => code },
  reference: reference as unknown as ContentReference,
  onChange: () => {},
};

function contextOf(current: ExamSection) {
  return { part: { id: "p1", sections: [current], options: [] } as unknown as ExamPart, option: null, section: current, location: { partId: "p1", optionId: null, sectionId: "s1" } };
}

function renderSubjectPanel(current: ExamSection) {
  renderWithProviders(<ExamStructureSubjectPanel {...common} context={contextOf(current)} onSelectBlock={() => {}} onAddBlock={() => {}} />);
  return subjectCounts.queries.at(-1)!;
}

function renderReviewPanel(current: ExamSection) {
  renderWithProviders(<ReviewSubjectPanel {...common} context={contextOf(current)} step1Count={10} />);
  return subjectCounts.queries.at(-1)!;
}

describe.each([
  ["panel môn", renderSubjectPanel],
  ["panel bài ôn", renderReviewPanel],
])("đếm nguồn cả môn có điều kiện audio · %s", (_name, render) => {
  it("môn của phần Nghe gửi REQUIRED ở từng nhóm", () => {
    expect(render(section("REQUIRED")).groups).toEqual([expect.objectContaining({ subjectCode: "ANH", audioRequirement: "REQUIRED" })]);
  });

  it("môn của phần Đọc gửi FORBIDDEN ở từng nhóm", () => {
    expect(render(section("FORBIDDEN")).groups).toEqual([expect.objectContaining({ audioRequirement: "FORBIDDEN" })]);
  });

  it("môn của kỳ thi cũ hoặc điều kiện ANY: thân đếm không có khóa audioRequirement", () => {
    for (const current of [section(), section("ANY")]) {
      subjectCounts.queries.length = 0;
      const group = render(current).groups[0]!;
      expect(group).not.toHaveProperty("audioRequirement");
      expect(Object.keys(group)).toEqual(expect.arrayContaining(["subjectCode", "questionType", "answerTypes"]));
    }
  });
});
