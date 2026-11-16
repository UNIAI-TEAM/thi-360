import type { AppLocale } from "@thi360/web-kit/preferences";
import { mockRequest } from "@/lib/mock/content-mock";
import referenceFixture from "./mock/reference.json";
import type { ContentReference } from "./types";

/**
 * Dữ liệu tham chiếu mẫu của Ngân hàng câu hỏi và Bài thi: môn, khối, độ khó, nhãn, mã tổ hợp, cây kiến thức.
 * Mục danh mục có `name` (tiếng Việt) và `nameEn`; API thật trả sẵn `name` theo ngôn ngữ của yêu cầu, nên bản mẫu
 * cũng chọn tên theo ngôn ngữ rồi bỏ `nameEn`.
 */

/** Chọn `name` theo ngôn ngữ ở mọi mục có `nameEn`; mục không có `nameEn` (cây kiến thức) giữ nguyên. */
function localizeNames<T>(value: T, locale: AppLocale): T {
  if (Array.isArray(value)) return value.map((item) => localizeNames(item, locale)) as T;
  if (typeof value !== "object" || value === null) return value;
  const { nameEn, ...rest } = value as Record<string, unknown>;
  const entries = Object.entries(rest).map(([key, child]) => [key, localizeNames(child, locale)] as const);
  const localized = Object.fromEntries(entries);
  if (locale === "en" && typeof nameEn === "string") localized.name = nameEn;
  return localized as T;
}

export async function getContentReference(locale: AppLocale = "vi"): Promise<ContentReference> {
  return mockRequest(() => localizeNames(referenceFixture, locale) as unknown as ContentReference);
}
