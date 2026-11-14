/**
 * Vòng focus vẽ vào trong cho vùng tự cuộn nằm sát mép thẻ `overflow-hidden`. Cùng chuỗi với `focusRingInset`
 * của UI kit (`packages/ui/src/lib/styles.ts`); kit chưa xuất hằng này qua `@thi360/ui` nên tạm chép ở đây.
 */
export const focusRingInset =
  "focus-visible:outline-3 focus-visible:-outline-offset-3 focus-visible:outline-primary-text";

/**
 * Màu trung tính cho chip chỉ để đọc. Dùng kèm dạng tĩnh của kit, `<Chip variant="tag">` (thẻ `span`, không vào
 * thứ tự Tab): dạng `tag` mặc định mang màu chính, một bảng nhiều dòng chip màu chính sẽ lấn át cột Trạng thái.
 */
export const STATIC_CHIP = "border-border bg-surface text-text-secondary";
