import { normalizeUiLocale } from "@thi360/web-kit/preferences";

interface BilingualName {
  nameVi?: string | null;
  nameEn?: string | null;
}

/**
 * Tên danh mục dùng chung (kỳ thi, môn) theo ngôn ngữ giao diện, rơi về `nameVi` (DESIGN.md mục 8): cùng quy tắc
 * với `pickName` của `@thi360/web-kit`. Bản này là tạm: `@thi360/web-kit` chỉ xuất `pickName` qua cổng gốc, mà cổng
 * gốc kéo `proxy.ts` (`node:net`) vào gói trình duyệt nên build lỗi; khi web-kit có đường xuất riêng cho `i18n`
 * thì đổi import và xóa tệp này.
 */
export function pickName(item: BilingualName, locale = "vi"): string {
  const vi = item.nameVi?.trim() ?? "";
  const en = item.nameEn?.trim() ?? "";
  if (normalizeUiLocale(locale) === "en" && en.length > 0) return en;
  return vi.length > 0 ? vi : en;
}
