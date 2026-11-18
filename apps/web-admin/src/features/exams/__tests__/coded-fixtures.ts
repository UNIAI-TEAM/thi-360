import type { ExamDetail, ExamPart, ExamSection, QuestionBlock, StructureCombination } from "../types";
import { examFixture } from "./fake-exams-backend";

/** Bài QDA và BCA có phần 3 theo mã (`codeSource`, `subjectPool`) như máy chủ trả, dùng cho test tổng điểm và lỗi cấu trúc. */

export const AXES = { grades: ["GRADE_12"], difficulties: ["APPLICATION"] };

const block = (id: string, count: number, pointsEach: number, over: Partial<QuestionBlock> = {}): QuestionBlock => ({
  id,
  name: `Nhóm ${id}`,
  questionType: "SINGLE_CHOICE",
  questionCount: count,
  setCount: null,
  setSize: null,
  requiredPointsPerQuestion: pointsEach,
  knowledgeScope: null,
  tagCodes: [],
  matrix: null,
  childAnswerType: null,
  templateGroupCode: null,
  answerTypeLocked: false,
  ...over,
});

const section = (id: string, subjectCode: string, count: number, pointsEach = 1, over: Partial<QuestionBlock> = {}): ExamSection => ({
  id,
  subjectCode,
  questionCount: count,
  knowledgeScope: null,
  answerQuotas: null,
  matrix: null,
  choiceGroup: null,
  blocks: [block(`b-${id}`, count, pointsEach, over)],
});

const part = (id: string, order: number, over: Partial<ExamPart> = {}): ExamPart => ({
  id,
  order,
  name: `Phần ${order}`,
  durationMinutes: 60,
  questionCount: 50,
  questionCountOnPaper: 50,
  questionCountRequired: 50,
  participation: { mode: "ALL", choose: null },
  maxPoints: null,
  mode: "FIXED",
  sections: [],
  options: [],
  ...over,
});

/** Phần theo mã như máy chủ trả: không có `sections` hay `options`, các môn nằm trong `subjectPool`. */
const codedPart = (id: string, order: number, questionCount: number, pool: ExamSection[], over: Partial<ExamPart> = {}): ExamPart =>
  part(id, order, {
    questionCount,
    questionCountOnPaper: questionCount,
    questionCountRequired: questionCount,
    participation: { mode: "CHOICE", choose: 1 },
    mode: "CHOICE",
    codeSource: { source: "GLOBAL_LIST", branch: null },
    subjectPool: pool,
    ...over,
  });

const combination = (code: string, partId: string, subjectCodes: string[]): StructureCombination => ({
  code,
  examCode: code,
  partId,
  subjectCodes,
  missingSubjects: [],
  nameVi: null,
  nameEn: null,
});

/** QDA: Toán 50 + Văn 50 + phần 3 theo mã (QD01–QD05, mỗi mã 50 câu), 150 điểm. */
export const QDA_PARTS = [
  part("qda-1", 1, { durationMinutes: 80, sections: [section("qda-toan", "MATH", 50)] }),
  part("qda-2", 2, { durationMinutes: 55, sections: [section("qda-van", "LITERATURE", 50)] }),
  codedPart(
    "qda-3",
    3,
    50,
    [
      section("qda-ly", "PHYSICS", 25),
      section("qda-hoa", "CHEMISTRY", 25),
      section("qda-sinh", "BIOLOGY", 25),
      section("qda-su", "HISTORY", 25),
      section("qda-dia", "GEOGRAPHY", 25),
      section("qda-anh", "ENGLISH", 50),
    ],
    { durationMinutes: 60, maxPoints: 50 },
  ),
];
export const QDA_COMBINATIONS = [
  combination("QD01", "qda-3", ["PHYSICS", "CHEMISTRY"]),
  combination("QD02", "qda-3", ["CHEMISTRY", "BIOLOGY"]),
  combination("QD03", "qda-3", ["PHYSICS", "BIOLOGY"]),
  combination("QD04", "qda-3", ["HISTORY", "GEOGRAPHY"]),
  combination("QD05", "qda-3", ["ENGLISH"]),
];

/** BCA: thời lượng chung 180 phút, phần 1–3 có điểm 25 / 60 / 15; phần 3 theo mã CA1–CA4 (mỗi mã một môn, 15 câu). */
export const BCA_PARTS = [
  part("bca-1", 1, {
    durationMinutes: null,
    questionCount: 1,
    questionCountOnPaper: 1,
    questionCountRequired: 1,
    maxPoints: 25,
    sections: [section("bca-van", "LITERATURE", 1, 25, { questionType: "ESSAY" })],
  }),
  part("bca-2", 2, { durationMinutes: null, questionCount: 60, questionCountOnPaper: 60, questionCountRequired: 60, maxPoints: 60, sections: [section("bca-toan", "MATH", 60)] }),
  codedPart(
    "bca-3",
    3,
    15,
    [section("bca-ly", "PHYSICS", 15), section("bca-hoa", "CHEMISTRY", 15), section("bca-sinh", "BIOLOGY", 15), section("bca-dia", "GEOGRAPHY", 15)],
    { durationMinutes: null, maxPoints: 15 },
  ),
];
export const BCA_COMBINATIONS = [
  combination("CA1", "bca-3", ["PHYSICS"]),
  combination("CA2", "bca-3", ["CHEMISTRY"]),
  combination("CA3", "bca-3", ["BIOLOGY"]),
  combination("CA4", "bca-3", ["GEOGRAPHY"]),
];

/** Bài đã kiểm tra Hợp lệ ở máy chủ. */
export function codedExam(kind: "QDA" | "BCA"): ExamDetail {
  const base = examFixture("ex-qda-f-001");
  const bca = kind === "BCA";
  return {
    ...base,
    id: `ex-${kind.toLowerCase()}-coded`,
    code: `${kind}-CODED-001`,
    kind,
    parts: bca ? BCA_PARTS : QDA_PARTS,
    timing: bca ? "SHARED" : "PER_PART",
    durationMinutes: bca ? 180 : 195,
    maxScore: bca ? 100 : 150,
    totalQuestions: bca ? 76 : 150,
    check: { status: "VALID", issueCount: 0, checkedAt: "2026-11-17T10:00:00+07:00" },
  };
}

/** Dòng phần của `structure/summary`: điểm theo phần, kể cả phần theo mã (máy chủ cộng điểm của một mã). */
export const summaryRow = (partId: string, name: string, count: number, maxPoints: number) => ({
  name,
  partId,
  target: count,
  allocated: count,
  difference: 0,
  maxPoints,
  children: [],
});
