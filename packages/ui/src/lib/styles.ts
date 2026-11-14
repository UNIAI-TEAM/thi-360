/**
 * Vòng focus nhìn thấy được cho mọi điều khiển (DESIGN.md mục 8). Màu đặc (không alpha):
 * `primary-text` đạt >= 3:1 (WCAG 1.4.11) trên nền trang, bề mặt và nền phụ ở cả hai chế độ.
 */
export const focusRing =
  "focus-visible:outline-none focus-visible:ring-3 focus-visible:ring-primary-text";

/**
 * Vòng focus vẽ vào trong, cho vùng tự cuộn nằm sát mép một khung cắt tràn (bảng trong thẻ `overflow-hidden`):
 * vòng ngoài của `focusRing` bị khung đó cắt mất, còn `ring-inset` là bóng đổ nằm dưới nội dung nên bị nền của
 * ô tiêu đề và dòng đang chọn che. `outline` vẽ đè lên nội dung và không bị bỏ ở chế độ tương phản cao.
 * Cùng màu và cùng độ dày với `focusRing`.
 */
export const focusRingInset =
  "focus-visible:outline-3 focus-visible:-outline-offset-3 focus-visible:outline-primary-text";

/** Nền chung của ô nhập, vùng nhập và nút chọn (DESIGN.md mục 5.2). Lỗi hiển thị theo `aria-invalid`. */
export const fieldBase =
  "w-full rounded-md border border-border bg-surface text-body text-text shadow-xs transition-colors duration-150 motion-reduce:transition-none placeholder:text-text-placeholder hover:border-border-strong focus:outline-none focus:border-primary-600 focus:ring-3 focus:ring-primary-600/15 aria-[invalid=true]:border-danger aria-[invalid=true]:focus:ring-danger/15 disabled:cursor-not-allowed disabled:bg-subtle disabled:opacity-60";
