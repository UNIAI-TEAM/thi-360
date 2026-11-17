import type { AppLocale } from "@thi360/web-kit/preferences";
import type { ContentReference } from "@/features/content/types";
import referenceFixture from "./content-reference.json";

/**
 * Bộ dựng `getContentReference` cho test (N2-2): test nào cần danh mục mẫu ở nhánh API thật thay
 * `getContentReference` trong `defaultContentApiDeps` bằng hàm này. Chọn `name` theo ngôn ngữ ở mọi mục có
 * `nameEn` (giống bản mẫu cũ); mục không có `nameEn` (cây kiến thức) giữ nguyên.
 */
function localizeNames<T>(value: T, locale: AppLocale): T {
  if (Array.isArray(value)) return value.map((item) => localizeNames(item, locale)) as T;
  if (typeof value !== "object" || value === null) return value;
  const { nameEn, ...rest } = value as Record<string, unknown>;
  const entries = Object.entries(rest).map(([key, child]) => [key, localizeNames(child, locale)] as const);
  const localized = Object.fromEntries(entries);
  if (locale === "en" && typeof nameEn === "string") localized.name = nameEn;
  return localized as T;
}

export function getContentReferenceFixture(locale: AppLocale = "vi"): Promise<ContentReference> {
  return Promise.resolve(structuredClone(localizeNames(referenceFixture, locale)) as unknown as ContentReference);
}
