"use client";

import type { ComponentProps, CSSProperties } from "react";
import { Toaster as Sonner, toast } from "sonner";

export { toast };

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

export type ToasterProps = ComponentProps<typeof Sonner> & {
  /** Nhãn vùng thông báo cho trình đọc màn hình (chuyển xuống `containerAriaLabel` của sonner). */
  containerAriaLabel?: string;
  /** Nhãn nút đóng toast cho trình đọc màn hình (gắn vào `toastOptions.closeButtonAriaLabel`). */
  closeButtonAriaLabel?: string;
};

/** Đặt một lần ở layout gốc. Góc trên phải, tự ẩn sau 4 giây (DESIGN.md mục 5.10). */
export function Toaster({ closeButtonAriaLabel, toastOptions, ...props }: ToasterProps) {
  return (
    <Sonner
      position="top-right"
      duration={4000}
      closeButton
      style={tokenStyle}
      toastOptions={{
        ...toastOptions,
        ...(closeButtonAriaLabel !== undefined ? { closeButtonAriaLabel } : {}),
      }}
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
