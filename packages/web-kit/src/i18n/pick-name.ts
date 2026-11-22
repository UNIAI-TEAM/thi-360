import { normalizeUiLocale } from "../preferences";

export interface BilingualName {
  nameVi?: string | null;
  nameEn?: string | null;
}

/**
 * Chọn tên hiển thị theo ngôn ngữ hiện tại với dự phòng là `nameVi` (spec §9.6, DESIGN.md
 * mục 8): giao diện `en` hiện `nameEn` nếu có, không thì `nameVi`; `vi` luôn hiện `nameVi`.
 * Tên lấy từ nội dung đề (từ mẫu hoặc do admin gõ) không qua hàm này — chúng hiện nguyên
 * bản trong vùng `data-exam-content`.
 */
export function pickName(item: BilingualName, locale = "vi"): string {
  const vi = item.nameVi?.trim() ?? "";
  const en = item.nameEn?.trim() ?? "";
  if (normalizeUiLocale(locale) === "en" && en.length > 0) {
    return en;
  }
  return vi.length > 0 ? vi : en;
}
