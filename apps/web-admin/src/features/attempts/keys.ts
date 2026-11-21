import type { ListAdminAttemptsQuery } from "./types";

export const ATTEMPTS_QUERY_KEYS = {
  all: ["attempts"] as const,
  lists: () => ["attempts", "list"] as const,
  list: (query: ListAdminAttemptsQuery) => ["attempts", "list", query] as const,
  details: () => ["attempts", "detail"] as const,
  detail: (attemptId: string) => ["attempts", "detail", attemptId] as const,
  examKinds: () => ["attempts", "exam-kinds"] as const,
};
