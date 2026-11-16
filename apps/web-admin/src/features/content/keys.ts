/** Khóa truy vấn của dữ liệu tham chiếu: dùng chung bởi facade `api.ts` và bản mẫu `api.mock.ts`. */
export const CONTENT_QUERY_KEYS = {
  all: ["content"] as const,
  reference: () => ["content", "reference"] as const,
};
