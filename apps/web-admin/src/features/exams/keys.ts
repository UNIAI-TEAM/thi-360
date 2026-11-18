import type { AvailableQuestionsQuery, ListExamsQuery, SubjectAvailableQuestionsQuery, SuggestExamCodeQuery } from "./types";

/** Khóa truy vấn của Bài thi: dùng chung bởi facade `api.ts` (bản mẫu đã gỡ ở QA, UNI-2621). */
export const EXAMS_QUERY_KEYS = {
  all: ["exams"] as const,
  lists: () => ["exams", "list"] as const,
  list: (query: ListExamsQuery) => ["exams", "list", query] as const,
  detail: (id: string) => ["exams", "detail", id] as const,
  structure: (id: string) => ["exams", "structure", id] as const,
  structureSummary: (id: string) => ["exams", "structure", id, "summary"] as const,
  templates: () => ["exams", "templates"] as const,
  suggestedCode: (query: SuggestExamCodeQuery) => ["exams", "suggested-code", query] as const,
  available: (query: AvailableQuestionsQuery) => ["exams", "available-questions", query] as const,
  subjectAvailable: (query: SubjectAvailableQuestionsQuery) => ["exams", "available-questions", "subject", query] as const,
};
