import type { QuestionDetail, QuestionSummary } from "./types";

const SUMMARY_KEYS = [
  "id", "code", "type", "status", "contentPreview", "subjectCode", "gradeCode", "difficultyCode", "points",
  "knowledge", "competencyCode", "taskTypeCode", "tagCodes", "hasSolution", "version", "revision", "usedInExamCount",
  "sourceCode", "createdBy", "createdAt", "updatedAt",
] as const satisfies readonly (keyof QuestionSummary)[];

/** Dòng tóm tắt của một câu hỏi đầy đủ (danh sách, kết quả đổi trạng thái). */
export function toQuestionSummary(question: QuestionDetail): QuestionSummary {
  return Object.fromEntries(SUMMARY_KEYS.map((key) => [key, question[key]])) as unknown as QuestionSummary;
}
