/** Vòng focus nhìn thấy được cho mọi điều khiển (DESIGN.md mục 8). */
export const focusRing =
  "focus-visible:outline-none focus-visible:ring-3 focus-visible:ring-primary-600/30";

/** Nền chung của ô nhập, vùng nhập và nút chọn (DESIGN.md mục 5.2). Lỗi hiển thị theo `aria-invalid`. */
export const fieldBase =
  "w-full rounded-md border border-border bg-surface text-body text-text shadow-xs transition-colors duration-150 motion-reduce:transition-none placeholder:text-text-placeholder hover:border-border-strong focus:outline-none focus:border-primary-600 focus:ring-3 focus:ring-primary-600/15 aria-[invalid=true]:border-danger aria-[invalid=true]:focus:ring-danger/15 disabled:cursor-not-allowed disabled:bg-subtle disabled:opacity-60";
