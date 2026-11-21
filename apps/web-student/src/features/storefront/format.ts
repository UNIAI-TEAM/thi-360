import { APP_TIME_ZONE } from "@thi360/web-kit/preferences";
import type { DecimalRange, IntRange } from "./types";

/** Nhãn "Bài ôn" khi bài xây từ chế độ ôn tập (`buildMode = REVIEW`, BS 2). */
export const REVIEW_BUILD_MODE = "REVIEW";

/** Nhận diện sản phẩm bộ đề theo `productType` hoặc `format`. */
export function isBundleProduct(product: { productType?: string | null; format?: string | null }): boolean {
  return product.productType === "BUNDLE" || product.format === "BUNDLE";
}

// `en-GB` luôn cho "dd/MM"; mẫu của `vi` là "dd-MM", không đúng quy ước ngày của DESIGN.md mục 8.
const DAY_MONTH = new Intl.DateTimeFormat("en-GB", { day: "2-digit", month: "2-digit", timeZone: APP_TIME_ZONE });
const VI_NUMBER = new Intl.NumberFormat("vi", { maximumFractionDigits: 2 });
const EN_NUMBER = new Intl.NumberFormat("en-GB", { maximumFractionDigits: 2 });

/** "dd/MM" theo giờ Việt Nam của một thời điểm ISO (ngày kết thúc khuyến mãi, TM 11.3). */
export function formatDayMonth(iso: string): string {
  return DAY_MONTH.format(new Date(iso));
}

/** Định dạng số theo ngôn ngữ hiển thị (`vi` dùng dấu phẩy thập phân, `en` dùng dấu chấm). */
export function formatLocaleNumber(value: number, locale: string): string {
  return (locale.toLowerCase().startsWith("en") ? EN_NUMBER : VI_NUMBER).format(value);
}

/** "50" khi mọi đường như nhau, "48–50" khi các đường khác nhau (TM 10.1); `null` khi API không có giá trị. */
export function rangeText(range: IntRange | DecimalRange | null | undefined, locale?: string): string | null {
  if (!range) return null;
  const format = (n: number) => (locale ? formatLocaleNumber(n, locale) : String(n));
  return range.min === range.max ? format(range.min) : `${format(range.min)}–${format(range.max)}`;
}

/** Mức giảm so với giá gốc, làm tròn phần trăm, tối thiểu 1; `null` khi không có khuyến mãi thật sự. */
export function discountPercent(listPrice: number, price: number): number | null {
  if (!(listPrice > price) || listPrice <= 0) return null;
  return Math.max(1, Math.round(((listPrice - price) / listPrice) * 100));
}
