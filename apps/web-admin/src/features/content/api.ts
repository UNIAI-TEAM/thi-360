import type { AppLocale } from "@thi360/web-kit/preferences";
import { isContentMockEnabled, isGradingMockEnabled } from "@/lib/mock/flags";
import { selectApi } from "./api-switch";
import * as mock from "./api.mock";
import * as real from "./api.real";
import type { ContentReference } from "./types";

/**
 * Dữ liệu tham chiếu của Ngân hàng câu hỏi và Bài thi. Cờ `NEXT_PUBLIC_THI360_CONTENT_MOCK` bật thì dùng dữ liệu mẫu
 * (`api.mock.ts`); hàng chờ chấm đọc cùng dữ liệu này nên cờ `NEXT_PUBLIC_THI360_GRADING_MOCK` cũng mở được. Cờ tắt:
 * `GET /content-reference` (`api.real.ts`).
 */
export { CONTENT_QUERY_KEYS } from "./keys";

/** `locale` chỉ để bản mẫu chọn tên; bản thật nhận ngôn ngữ từ cookie qua proxy (`Accept-Language`). */
export const getContentReference = selectApi<[locale?: AppLocale], ContentReference>(
  mock.getContentReference,
  () => real.getContentReference(),
  () => isContentMockEnabled() || isGradingMockEnabled(),
);

export interface ContentApiDeps {
  getContentReference: typeof getContentReference;
}

export const defaultContentApiDeps: ContentApiDeps = { getContentReference };
