import type { ExamDetail, ExamPart, QuestionBlock } from "../types";
import { codedExam } from "./coded-fixtures";
import { EXAMS_FIXTURE } from "./exam-fixture";

const block = (overrides: Partial<QuestionBlock>): QuestionBlock => ({
  id: "blk",
  name: "Nhóm",
  questionType: "SINGLE_CHOICE",
  questionCount: 1,
  setCount: null,
  setSize: null,
  requiredPointsPerQuestion: 1,
  knowledgeScope: { nodeIds: [] },
  tagCodes: [],
  childAnswerType: null,
  templateGroupCode: null,
  answerTypeLocked: false,
  matrix: null,
  ...overrides,
});

/** Hình thật của bài SP2E Ngữ văn vừa tạo từ mẫu mặc định: Phần I 30 câu 6 điểm, Phần II · Viết chỉ có nhóm Viết bài bỏ qua. */
export function sp2eVanExam(): ExamDetail {
  const skeleton = structuredClone(EXAMS_FIXTURE.exams.find((item) => item.code === "HSA-F-001")!.parts[0]!);
  const part = (id: string, name: string, order: number, count: number, blocks: QuestionBlock[]): ExamPart => ({
    ...skeleton,
    id,
    name,
    order,
    mode: "FIXED",
    options: [],
    durationMinutes: null,
    questionCount: count,
    questionCountOnPaper: count,
    questionCountRequired: count,
    sections: [{ ...skeleton.sections[0]!, id: `${id}-s`, subjectCode: "LITERATURE", questionCount: count, blocks }],
  });
  return {
    ...codedExam("QDA"),
    id: "ex-sp2e-van",
    code: "SP2E-VAN-001",
    kind: "SP2E",
    timing: "SHARED",
    durationMinutes: 45,
    maxScore: 6,
    totalQuestions: 30,
    parts: [
      part("p1", "Phần I · Đọc hiểu", 1, 30, [block({ id: "b1", name: "Văn bản 1", questionCount: 30, requiredPointsPerQuestion: 0.2 })]),
      part("p2", "Phần II · Viết", 2, 0, [block({ id: "b5", name: "Viết bài", questionType: "ESSAY", questionCount: 1, omitted: true })]),
    ],
  };
}
