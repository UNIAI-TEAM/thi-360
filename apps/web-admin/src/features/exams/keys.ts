import type { AvailableQuestionsQuery, ListExamsQuery, SubjectAvailableQuestionsQuery, SuggestExamCodeQuery } from "./types";

/** Khóa truy vấn của Bài thi: mọi khóa con đều bắt đầu bằng `["exams", ...]`. */
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

/** Khóa truy vấn của Kiểm tra và Công bố bài thi. */
export const EXAM_REVIEW_QUERY_KEYS = {
  all: ["exams", "review"] as const,
  check: (examId: string) => ["exams", "review", examId, "check"] as const,
  checkRun: (examId: string, runId: string) => ["exams", "review", examId, "check-run", runId] as const,
  versions: (examId: string) => ["exams", "review", examId, "versions"] as const,
  version: (examId: string, version: number) => ["exams", "review", examId, "versions", version] as const,
};

/** Khóa truy vấn của Thông tin bán bài thi. */
export const LISTING_QUERY_KEYS = {
  detail: (examId: string) => ["exams", "listing", examId] as const,
};

/** Khóa truy vấn của Làm thử đề xem trước. */
export const PREVIEW_TRIAL_QUERY_KEYS = {
  paper: (examId: string, previewId: string) => ["exams", examId, "preview", previewId, "trial-paper"] as const,
};
