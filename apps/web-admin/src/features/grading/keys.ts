import type { GradingCountsQuery, ListGradingItemsQuery } from "./types";

export const GRADING_QUERY_KEYS = {
  all: ["grading"] as const,
  list: (query: ListGradingItemsQuery) => ["grading", "list", query] as const,
  counts: (query: GradingCountsQuery) => ["grading", "counts", query] as const,
  examKinds: () => ["grading", "exam-kinds"] as const,
  detail: (itemId: string) => ["grading", "detail", itemId] as const,
};
