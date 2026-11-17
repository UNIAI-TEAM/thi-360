import type { AppLocale } from "@thi360/web-kit/preferences";
import type { ContentReference } from "./types";
import { getContentReference as getContentReferenceReal } from "./api.real";

/**
 * Dữ liệu tham chiếu của Ngân hàng câu hỏi và Bài thi: `GET /content-reference` (`api.real.ts`).
 * Lớp dữ liệu mẫu và cờ `NEXT_PUBLIC_THI360_CONTENT_MOCK` đã gỡ ở QA (UNI-2621). Danh mục của màn chấm
 * `/grading` cũng đọc từ API thật — `GRADING_MOCK` chỉ còn điều khiển hàng chờ chấm, không đổi danh mục (F-02).
 */
export { CONTENT_QUERY_KEYS } from "./keys";

/** `locale` chỉ để bản dựng của test chọn tên; bản thật nhận ngôn ngữ từ cookie qua proxy (`Accept-Language`). */
export const getContentReference: (locale?: AppLocale) => Promise<ContentReference> = getContentReferenceReal;

export interface ContentApiDeps {
  getContentReference: typeof getContentReference;
}

export const defaultContentApiDeps: ContentApiDeps = { getContentReference };
