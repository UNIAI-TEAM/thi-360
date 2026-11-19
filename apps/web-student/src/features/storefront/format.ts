import { APP_TIME_ZONE } from "@thi360/web-kit/preferences";
import type { DecimalRange, IntRange } from "./types";

/** Nhãn "Bài ôn" khi bài xây từ chế độ ôn tập (`buildMode = REVIEW`, BS 2). */
export const REVIEW_BUILD_MODE = "REVIEW";

// `en-GB` luôn cho "dd/MM"; mẫu của `vi` là "dd-MM", không đúng quy ước ngày của DESIGN.md mục 8.
const DAY_MONTH = new Intl.DateTimeFormat("en-GB", { day: "2-digit", month: "2-digit", timeZone: APP_TIME_ZONE });

/** "dd/MM" theo giờ Việt Nam của một thời điểm ISO (ngày kết thúc khuyến mãi, TM 11.3). */
export function formatDayMonth(iso: string): string {
  return DAY_MONTH.format(new Date(iso));
}

/** "50" khi mọi đường như nhau, "48–50" khi các đường khác nhau (TM 10.1); `null` khi API không có giá trị. */
export function rangeText(range: IntRange | DecimalRange | null | undefined): string | null {
  if (!range) return null;
  return range.min === range.max ? String(range.min) : `${range.min}–${range.max}`;
}

/** Mức giảm so với giá gốc, làm tròn phần trăm, tối thiểu 1; `null` khi không có khuyến mãi thật sự. */
export function discountPercent(listPrice: number, price: number): number | null {
  if (!(listPrice > price) || listPrice <= 0) return null;
  return Math.max(1, Math.round(((listPrice - price) / listPrice) * 100));
}
