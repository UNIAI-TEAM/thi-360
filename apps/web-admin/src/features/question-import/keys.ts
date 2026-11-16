/** Khóa truy vấn của nhập câu hỏi: dùng chung bởi facade `api.ts` và bản mẫu `api.mock.ts`. */
export const QUESTION_IMPORT_QUERY_KEYS = {
  all: ["questionImport"] as const,
  history: () => ["questionImport", "history"] as const,
};
