import type { AvailableQuestionsQuery, ListExamsQuery, SuggestExamCodeQuery } from "./types";

/** Khóa truy vấn của Bài thi: dùng chung bởi facade `api.ts` và bản mẫu `api.mock.ts`. */
export const EXAMS_QUERY_KEYS = {
  all: ["exams"] as const,
  lists: () => ["exams", "list"] as const,
  list: (query: ListExamsQuery) => ["exams", "list", query] as const,
  detail: (id: string) => ["exams", "detail", id] as const,
  structure: (id: string) => ["exams", "structure", id] as const,
  templates: () => ["exams", "templates"] as const,
  suggestedCode: (query: SuggestExamCodeQuery) => ["exams", "suggested-code", query] as const,
  available: (query: AvailableQuestionsQuery) => ["exams", "available-questions", query] as const,
};
