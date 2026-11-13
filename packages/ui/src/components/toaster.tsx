import type { ComponentProps, CSSProperties } from "react";
import { Toaster as Sonner, toast } from "sonner";

// Màu của sonner lấy từ token; style inline thắng theme mặc định của sonner.
const tokenStyle = {
  "--normal-bg": "var(--color-surface)",
  "--normal-text": "var(--color-text)",
  "--normal-border": "var(--color-border)",
  "--success-bg": "var(--color-success-bg)",
  "--success-text": "var(--color-success)",
  "--success-border": "var(--color-success)",
  "--error-bg": "var(--color-danger-bg)",
  "--error-text": "var(--color-danger)",
  "--error-border": "var(--color-danger)",
  "--border-radius": "var(--radius-lg)",
} as CSSProperties;

/** Đặt một lần ở layout gốc. Góc trên phải, tự ẩn sau 4 giây (DESIGN.md mục 5.10). */
export function Toaster(props: ComponentProps<typeof Sonner>) {
  return (
    <Sonner
      position="top-right"
      duration={4000}
      closeButton
      style={tokenStyle}
      {...props}
    />
  );
}

/** Lỗi không tự ẩn: người dùng phải đóng (DESIGN.md mục 5.10). */
export const notify = {
  success: (message: string) => toast.success(message),
  error: (message: string) => toast.error(message, { duration: Infinity }),
  info: (message: string) => toast.info(message),
};
