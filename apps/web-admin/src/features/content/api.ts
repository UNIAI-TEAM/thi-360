import type { AppLocale } from "@thi360/web-kit/preferences";
import type { ContentReference } from "./types";
import { getContentReference as getContentReferenceReal } from "./api.real";

/**
 * Dữ liệu tham chiếu của Ngân hàng câu hỏi và Bài thi: `GET /content-reference` (`api.real.ts`).
 */
export { CONTENT_QUERY_KEYS } from "./keys";

/** `locale` chỉ để bản dựng của test chọn tên; bản thật nhận ngôn ngữ từ cookie qua proxy (`Accept-Language`). */
export const getContentReference: (locale?: AppLocale) => Promise<ContentReference> = getContentReferenceReal;

export interface ContentApiDeps {
  getContentReference: typeof getContentReference;
}

export const defaultContentApiDeps: ContentApiDeps = { getContentReference };
