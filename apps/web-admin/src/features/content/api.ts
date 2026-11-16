import { guardApi, isContentMockEnabled, isGradingMockEnabled } from "@/lib/mock/flags";
import * as mock from "./api.mock";

/**
 * Dữ liệu tham chiếu của Ngân hàng câu hỏi và Bài thi. Cờ `NEXT_PUBLIC_THI360_CONTENT_MOCK` bật thì dùng dữ liệu mẫu
 * (`api.mock.ts`); hàng chờ chấm đọc cùng dữ liệu này nên cờ `NEXT_PUBLIC_THI360_GRADING_MOCK` cũng mở được. Cờ tắt:
 * nhánh API thật, chưa nối (FE-1, FE-2).
 */
export { CONTENT_QUERY_KEYS } from "./keys";

export const getContentReference = guardApi(
  () => isContentMockEnabled() || isGradingMockEnabled(),
  mock.getContentReference,
);

export interface ContentApiDeps {
  getContentReference: typeof getContentReference;
}

export const defaultContentApiDeps: ContentApiDeps = { getContentReference };
