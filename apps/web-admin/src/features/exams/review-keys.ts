/** Khóa truy vấn của Kiểm tra và Công bố bài thi: dùng chung bởi facade `review-api.ts` (bản mẫu đã gỡ ở QA, UNI-2621). */
export const EXAM_REVIEW_QUERY_KEYS = {
  all: ["exams", "review"] as const,
  check: (examId: string) => ["exams", "review", examId, "check"] as const,
  checkRun: (examId: string, runId: string) => ["exams", "review", examId, "check-run", runId] as const,
  versions: (examId: string) => ["exams", "review", examId, "versions"] as const,
  version: (examId: string, version: number) => ["exams", "review", examId, "versions", version] as const,
};
