import type { UiLocale } from "../messages/ui-strings";

/** Chữ cái phương án theo vị trí trong `option_order` đã nhận (F22): 0 -> A, 1 -> B… */
export function optionLetter(index: number): string {
  return String.fromCharCode(65 + (index % 26));
}

/** Chữ thường cho cột phải Ghép đôi: 0 -> a, 1 -> b… */
export function matchLetter(index: number): string {
  return String.fromCharCode(97 + (index % 26));
}

/** Chữ thường cho ý Đúng/Sai: 0 -> a, 1 -> b… (cùng bộ với `matchLetter`, giữ tên theo nghĩa). */
export function statementLetter(index: number): string {
  return matchLetter(index);
}

/**
 * Xếp `items` theo `order` (danh sách id đã nhận trong lượt): mục có id trong `order` đứng theo
 * thứ tự đó trước, mục thiếu trong `order` (dữ liệu cũ, xem trước) đứng sau theo thứ tự mảng.
 * `order` thiếu hoặc rỗng: giữ nguyên.
 */
export function orderById<T extends { id: string }>(items: readonly T[], order: readonly string[] | undefined): T[] {
  if (!order || order.length === 0) return [...items];
  const position = new Map(order.map((id, index) => [id, index]));
  return [...items].sort((a, b) => {
    const pa = position.get(a.id);
    const pb = position.get(b.id);
    if (pa === undefined && pb === undefined) return items.indexOf(a) - items.indexOf(b);
    if (pa === undefined) return 1;
    if (pb === undefined) return -1;
    return pa - pb;
  });
}

/** Chữ thuần của đoạn HTML, dùng cho nhãn `option` và `aria-label` (không để HTML vào thuộc tính). */
export function htmlToText(html: string): string {
  if (!html) return "";
  if (typeof DOMParser !== "undefined") {
    const doc = new DOMParser().parseFromString(`<div>${html}</div>`, "text/html");
    for (const el of [...doc.querySelectorAll("script, style, template")]) el.remove();
    return (doc.body.textContent ?? "").replace(/\s+/g, " ").trim();
  }
  return html.replace(/<[^>]*>/g, "").replace(/\s+/g, " ").trim();
}

const SCORE_FORMATTERS: Record<UiLocale, Intl.NumberFormat> = {
  vi: new Intl.NumberFormat("vi-VN", { maximumFractionDigits: 2 }),
  en: new Intl.NumberFormat("en-US", { maximumFractionDigits: 2 }),
};

/** Điểm hiển thị làm tròn nửa lên tới 2 chữ số, theo dấu thập phân của ngôn ngữ đang dùng. */
export function formatScore(value: number, locale: UiLocale): string {
  return SCORE_FORMATTERS[locale].format(Number(value.toFixed(2)));
}
