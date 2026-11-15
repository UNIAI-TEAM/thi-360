/**
 * Vòng focus của kit (DESIGN.md mục 8), cho nút tự viết không dùng được `Button`/`IconButton` (tay nắm kéo thả, nút
 * trong chip, nút chuyển đoạn, mẩu đường dẫn). Lấy thẳng từ `@thi360/ui` để không lệch với kit (C-21).
 */
export { focusRing, focusRingInset } from "@thi360/ui";

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
