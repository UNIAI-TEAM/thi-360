import { screen, within } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import en from "@/messages/en.json";
import reference from "@/test/fixtures/content-reference.json";
import { renderWithProviders } from "@/test/render";
import type { ContentReference } from "@/features/content/types";
import { BlockSetConditions } from "../components/block-set-conditions";
import { ExamStructureSubjectPanel } from "../components/exam-structure-subject-panel";
import { ScoringExamples, StrandReference } from "../components/scoring-examples";
import { strandTable } from "../reference-distribution";
import type { ExamPart, ExamQuota, ExamSection, QuestionBlock, TopicQuota } from "../types";

/** FE-7 vòng 4, F3: mọi bảng cuộn ngang của bước 2 có cùng dòng nhắc và vùng cuộn có tên như bảng tổng quan bước 1. */

vi.mock(import("@/features/content/api"), async (importOriginal) => {
  const actual = await importOriginal();
  const { getContentReferenceFixture: getContentReference } = await import("@/test/fixtures/content-reference");
  return { ...actual, getContentReference, defaultContentApiDeps: { getContentReference } };
});

vi.mock(import("../queries"), async (importOriginal) => {
  const actual = await importOriginal();
  return {
    ...actual,
    useSubjectAvailableQuestions: () => ({ isError: false, data: undefined }) as never,
    useAvailableQuestions: () => ({ isError: false, data: undefined }) as never,
  };
});

const block = (patch: Partial<QuestionBlock> = {}): QuestionBlock => ({
  id: "b",
  name: "Dòng 1",
  questionType: "TRUE_FALSE",
  questionCount: 5,
  setCount: null,
  setSize: null,
  requiredPointsPerQuestion: 6,
  knowledgeScope: null,
  tagCodes: [],
  matrix: null,
  childAnswerType: null,
  templateGroupCode: null,
  answerTypeLocked: false,
  ...patch,
});

const section = (blocks: QuestionBlock[], patch: Partial<ExamSection> = {}): ExamSection => ({
  id: "s",
  subjectCode: "TOAN",
  questionCount: 20,
  knowledgeScope: null,
  answerQuotas: null,
  matrix: null,
  choiceGroup: null,
  blocks,
  ...patch,
});

const part = (blocks: QuestionBlock[]): ExamPart => ({
  id: "p",
  order: 1,
  name: "Phần",
  durationMinutes: null,
  questionCount: 20,
  questionCountOnPaper: 20,
  questionCountRequired: 20,
  participation: { mode: "ALL", choose: null },
  mode: "FIXED",
  sections: [section(blocks)],
  options: [],
});

const CUE_VI = "Bảng cuộn ngang: vuốt để xem các cột còn lại.";
const CUE_EN = "The table scrolls sideways: swipe to see the other columns.";

/** Vùng cuộn của bảng `name` có tên truy cập được, nhận bàn phím và đứng sau dòng nhắc cuộn. */
function expectScrollRegion(name: string | RegExp, cue = CUE_VI) {
  // Phần tử `section` đặt tên theo cùng tiêu đề cũng là vùng; chỉ vùng cuộn nhận bàn phím.
  const region = screen.getAllByRole("region", { name }).find((item) => item.hasAttribute("tabindex"))!;
  expect(region).toHaveAttribute("tabindex", "0");
  expect(within(region).getByRole("table")).toBeInTheDocument();
  expect(region.previousElementSibling).toHaveTextContent(cue);
}

describe("bảng cuộn ngang của bước 2 (F3)", () => {
  it("bảng điểm V-SAT: mỗi bảng ví dụ có dòng nhắc cuộn", () => {
    renderWithProviders(<ScoringExamples kind="VSAT" part={part([block(), block({ id: "m", questionType: "MATCHING" })])} />);
    expectScrollRegion("Đúng/Sai · tối đa 6 điểm");
    expectScrollRegion("Ghép đôi · tối đa 6 điểm");
  });

  it("bảng H-SCA có dòng nhắc cuộn", () => {
    renderWithProviders(<ScoringExamples kind="HSCA" part={part([block({ questionType: "SINGLE_CHOICE", requiredPointsPerQuestion: 0.25 })])} />);
    expectScrollRegion("H-SCA · cách chấm theo dạng câu");
  });

  it("bảng mạch đối chiếu theo lệnh có dòng nhắc cuộn", () => {
    const strand: TopicQuota = { id: "t1", name: "Đọc hiểu", nodeIds: [], count: 42, enforcement: "REFERENCE", basis: "PROMPT" };
    const table = strandTable(section([], { subjectCode: "ENGLISH", topicQuotas: [strand] }), [] as ExamQuota[], "SUBJECT");
    renderWithProviders(<StrandReference table={table} subjectName="Tiếng Anh" difficultyName={(code) => code} />);
    expectScrollRegion("Mạch đối chiếu theo lệnh");
  });

  it("bảng nhóm lấy câu của môn có dòng nhắc cuộn, cả tiếng Anh", () => {
    const current = section([block()]);
    const props = {
      subjectName: "Toán",
      axes: { grades: reference.grades.map((grade) => grade.code), difficulties: reference.difficulties.map((item) => item.code) },
      labels: { grade: (code: string) => code, difficulty: (code: string) => code },
      reference: reference as unknown as ContentReference,
      onChange: () => {},
      onSelectBlock: () => {},
      onAddBlock: () => {},
      context: { part: part([block()]), option: null, section: current, location: { partId: "p", optionId: null, sectionId: "s" } },
    };
    const view = renderWithProviders(<ExamStructureSubjectPanel {...props} />);
    expectScrollRegion("Các nhóm lấy câu của Toán");
    view.unmount();
    renderWithProviders(<ExamStructureSubjectPanel {...props} />, { locale: "en", messages: en });
    expectScrollRegion(/^Question groups of/, CUE_EN);
  });

  it("bảng điều kiện theo vị trí có dòng nhắc cuộn", () => {
    const set = block({
      questionType: "QUESTION_SET",
      setCount: 1,
      setSize: 2,
      questionCount: 2,
      childConditions: [{ from: 1, to: 2, nodeIds: [], answerType: "SINGLE_CHOICE" }],
    });
    renderWithProviders(
      <BlockSetConditions block={set} reference={reference as unknown as ContentReference} subjectCode="TOAN" onChange={() => {}} commandCount={null} locked={false} />,
    );
    expectScrollRegion("Loại câu hỏi theo vị trí trong chùm");
  });

  it("N1 · bảng Điều kiện của N chùm có dòng nhắc cuộn", () => {
    const set = block({ questionType: "QUESTION_SET", setCount: 2, setSize: 2, questionCount: 4 });
    renderWithProviders(
      <BlockSetConditions block={set} reference={reference as unknown as ContentReference} subjectCode="TOAN" onChange={() => {}} commandCount={null} locked={false} />,
    );
    expectScrollRegion("Điều kiện của 2 chùm");
  });
});
