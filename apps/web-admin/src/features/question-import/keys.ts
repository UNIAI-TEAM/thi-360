/** Khóa truy vấn của nhập câu hỏi: dùng chung bởi facade `api.ts` (bản mẫu đã gỡ ở QA, UNI-2621). */
export const QUESTION_IMPORT_QUERY_KEYS = {
  all: ["questionImport"] as const,
  history: () => ["questionImport", "history"] as const,
};
