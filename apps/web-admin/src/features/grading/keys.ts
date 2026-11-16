import type { ListGradingAttemptsQuery } from "./types";

/** Khóa truy vấn, mã lỗi và trường sắp xếp của hàng chờ chấm: của hàng chờ chấm. */
export const GRADING_QUERY_KEYS = {
  all: ["grading"] as const,
  list: (query: ListGradingAttemptsQuery) => ["grading", "list", query] as const,
  statusCounts: (query: ListGradingAttemptsQuery) => ["grading", "status-counts", query] as const,
  filterOptions: () => ["grading", "filter-options"] as const,
  detail: (id: string) => ["grading", "detail", id] as const,
};

/** Mã lỗi nghiệp vụ của lưu nháp và hoàn tất chấm (`MockApiError.code`); chữ ở `grading.detail.errors`. */
export const GRADING_ERROR_CODES = [
  "ATTEMPT_ALREADY_SCORED",
  "GRADING_INCOMPLETE",
  "SCORE_OUT_OF_RANGE",
  "UNKNOWN_QUESTION",
  "NOT_FOUND",
] as const;
export type GradingErrorCode = (typeof GRADING_ERROR_CODES)[number];

export const SORTABLE_GRADING_FIELDS = ["submittedAt", "learner"] as const;
export const DEFAULT_GRADING_SORT = "submittedAt,asc";
