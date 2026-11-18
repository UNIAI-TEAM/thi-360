/**
 * Chỉ tiêu và số đạt do máy chủ tính là số thập phân (BigDecimal: 1,25 câu, 2,5 điểm), không còn là số nguyên. Hiển thị
 * tối đa 2 chữ số thập phân, không đệm số 0 cuối, theo dấu phân cách của ngôn ngữ (vi: 1,5; en: 1.5).
 */
export const DISPLAY_NUMBER_FORMAT = { maximumFractionDigits: 2 } as const;

/**
 * Làm tròn về 2 chữ số thập phân để truyền vào ICU `{x, number}`: định dạng số mặc định của ICU lấy tới 3 chữ số, nên
 * giá trị đã làm tròn mới không in quá 2 chữ số. Dấu phân cách vẫn do ICU theo ngôn ngữ.
 */
export function roundForDisplay(value: number): number {
  return Math.round((value + Number.EPSILON) * 100) / 100;
}
