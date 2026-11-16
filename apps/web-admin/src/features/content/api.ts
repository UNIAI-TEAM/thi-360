import type { AppLocale } from "@thi360/web-kit/preferences";
import { isContentMockEnabled, isGradingMockEnabled } from "@/lib/mock/flags";
import { selectApi } from "./api-switch";
import * as mock from "./api.mock";
import * as real from "./api.real";
import type { ContentReference } from "./types";

/**
 * Dữ liệu tham chiếu của Ngân hàng câu hỏi và Bài thi. Cờ `NEXT_PUBLIC_THI360_CONTENT_MOCK` bật thì dùng dữ liệu mẫu
 * (`api.mock.ts`). LƯU Ý (F-02): hàng chờ chấm đọc chung dữ liệu này nên cờ `NEXT_PUBLIC_THI360_GRADING_MOCK` CŨNG
 * ép bản mẫu khi `CONTENT_MOCK` đang tắt — chỉ bật `GRADING_MOCK` trong dev sẽ vô tình đổi danh mục/khóa kiến thức của
 * ngân hàng câu hỏi sang mã mẫu và ghi `POST /questions` sẽ trả 400 `VALIDATION_FAILED`. Không gỡ được kết nối này
 * vì chấm cần cùng một tập giá trị danh mục; lỗi đã ghi để lead cập nhật tài liệu env. Cờ tắt cả hai:
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
