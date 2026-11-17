/** Khóa truy vấn của dữ liệu tham chiếu: dùng chung bởi facade `api.ts` (bản mẫu đã gỡ ở QA, UNI-2621). */
export const CONTENT_QUERY_KEYS = {
  all: ["content"] as const,
  /** Tên danh mục theo ngôn ngữ giao diện, nên mỗi ngôn ngữ một khóa: đổi ngôn ngữ thì tải lại. */
  reference: (locale: string) => ["content", "reference", locale] as const,
};
