/**
 * Vòng focus của kit (DESIGN.md mục 8), cho nút tự viết không dùng được `Button`/`IconButton` (tay nắm kéo thả, nút
 * trong chip, nút chuyển đoạn, mẩu đường dẫn). Cùng chuỗi với `focusRing` của UI kit (`packages/ui/src/lib/styles.ts`);
 * kit chưa xuất hằng này qua `@thi360/ui` nên tạm chép ở đây (C-21).
 */
export const focusRing =
  "focus-visible:outline-none focus-visible:ring-3 focus-visible:ring-primary-text";

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

/**
 * Tay nắm kéo thả (C-11): `touch-none` để ngón tay đặt lên tay nắm kéo được mục thay vì cuộn trang (PointerSensor
 * của dnd-kit bị trình duyệt hủy khi trang cuộn), `select-none` để kéo không bôi đen chữ. Chỉ đặt trên tay nắm,
 * phần còn lại của dòng vẫn cuộn trang bằng cảm ứng như thường.
 */
export const DRAG_HANDLE = "touch-none select-none";
