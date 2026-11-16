import { mockRequest } from "@/lib/mock/content-mock";
import referenceFixture from "./mock/reference.json";
import type { ContentReference } from "./types";

/**
 * Dữ liệu tham chiếu của Ngân hàng câu hỏi và Bài thi: môn, khối, độ khó, nhãn, mã tổ hợp, cây kiến thức.
 * Dữ liệu mẫu. Khi nối backend: gọi API lựa chọn của danh mục dùng chung (`/api/v1/admin/catalog/...`) và ghép lại
 * thành `ContentReference`; giữ nguyên chữ ký hàm.
 */
const reference = referenceFixture as ContentReference;

export async function getContentReference(): Promise<ContentReference> {
  return mockRequest(() => reference);
}
