import type { ExamDetail, ExamPart, ExamSummary } from "./types";

const SUMMARY_KEYS = [
  "id", "code", "name", "kind", "scope", "status", "revision", "servingVersion", "hasUnpublishedChanges", "totalQuestions",
  "durationMinutes", "englishOption", "combinationCount", "partCode", "subjectCode", "timing", "maxScore", "referenceMax", "practiceScoring",
  "hasEssay", "check", "attemptCount", "updatedAt", "updatedBy",
] as const satisfies readonly (keyof ExamSummary)[];

/** Số nhánh nội dung (THPTQG Tin học) của các phần tự chọn. */
function branchCountOf(parts: readonly ExamPart[]): number {
  return parts.flatMap((part) => (part.mode === "CHOICE" ? part.options : [])).filter((option) => option.kind === "BRANCH").length;
}

/** Dòng danh sách của một bài thi; số nhánh tính từ cấu trúc. */
export function toExamSummary(exam: ExamDetail): ExamSummary {
  const picked = Object.fromEntries(SUMMARY_KEYS.map((key) => [key, exam[key]])) as unknown as ExamSummary;
  return { ...picked, branchCount: branchCountOf(exam.parts) };
}
