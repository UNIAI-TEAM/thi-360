import type { ListQuestionsQuery } from "./types";

/** Khóa truy vấn của Ngân hàng câu hỏi: dùng chung bởi facade `api.ts` (bản mẫu đã gỡ ở QA, UNI-2621). */
export const QUESTIONS_QUERY_KEYS = {
  all: ["questions"] as const,
  lists: () => ["questions", "list"] as const,
  list: (query: ListQuestionsQuery) => ["questions", "list", query] as const,
  statusCounts: (query: ListQuestionsQuery) => ["questions", "status-counts", query] as const,
  authors: () => ["questions", "authors"] as const,
  detail: (id: string) => ["questions", "detail", id] as const,
  readiness: (id: string) => ["questions", "readiness", id] as const,
  retireImpact: (id: string) => ["questions", "retire-impact", id] as const,
};
