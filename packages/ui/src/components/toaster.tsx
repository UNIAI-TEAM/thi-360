"use client";

import type { ComponentProps, CSSProperties } from "react";
import { Toaster as Sonner, toast } from "sonner";
import { cn } from "../lib/cn";
import { useUiStrings } from "./ui-strings";

export { toast };

/*
 * Màu của sonner lấy từ token; style inline thắng theme mặc định của sonner. Các biến `--success-*`, `--info-*`,
 * `--warning-*`, `--error-*` chỉ được sonner đọc khi bật `richColors` (S-24), nên `Toaster` bật nó và khai đủ
 * bốn loại ở đây để không loại nào rơi về màu viết sẵn của sonner. Token trạng thái được định nghĩa lại ở chế
 * độ tối và cặp chữ/nền của chúng do tokens.test.ts kiểm.
 */
const tokenStyle = {
  "--normal-bg": "var(--color-surface)",
  "--normal-text": "var(--color-text)",
  "--normal-border": "var(--color-border)",
  "--success-bg": "var(--color-success-bg)",
  "--success-text": "var(--color-success)",
  "--success-border": "var(--color-success)",
  "--info-bg": "var(--color-info-bg)",
  "--info-text": "var(--color-info)",
  "--info-border": "var(--color-info)",
  "--warning-bg": "var(--color-warning-bg)",
  "--warning-text": "var(--color-warning)",
  "--warning-border": "var(--color-warning)",
  "--error-bg": "var(--color-danger-bg)",
  "--error-text": "var(--color-danger)",
  "--error-border": "var(--color-danger)",
  "--border-radius": "var(--radius-lg)",
} as CSSProperties;

/*
 * Nút đóng của sonner rộng 20×20 và là cách duy nhất (ngoài vuốt) để đóng toast lỗi không tự ẩn (S-24, S-12).
 * CSS của sonner không nằm trong layer nào nên thắng mọi lớp tiện ích thường; phải dùng dạng `!` (important).
 *   - `size-6!`: 24×24 (WCAG 2.5.8) với mọi thiết bị.
 *   - `pointer-coarse:after:*`: trên màn cảm ứng, phần tử giả nới vùng chạm ra 44×44 (WCAG 2.5.5) mà không đổi
 *     hình nút; trên máy tính giữ nguyên.
 *   - hover: sonner tô nền xám sáng viết sẵn, ở chế độ tối làm mất dấu ×; đổi sang token.
 */
const closeButtonClass =
  "size-6! hover:border-border-strong! hover:bg-subtle! pointer-coarse:after:absolute pointer-coarse:after:-inset-2.5";

export type ToasterProps = ComponentProps<typeof Sonner> & {
  /** Nhãn vùng thông báo cho trình đọc màn hình (chuyển xuống `containerAriaLabel` của sonner). */
  containerAriaLabel?: string;
  /** Nhãn nút đóng toast cho trình đọc màn hình (gắn vào `toastOptions.closeButtonAriaLabel`). */
  closeButtonAriaLabel?: string;
};

/**
 * Đặt một lần ở layout gốc. Góc trên phải, tự ẩn sau 4 giây (DESIGN.md mục 5.10). Toast thành công, thông tin,
 * cảnh báo, lỗi mang cặp màu trạng thái của token (`richColors`); toast lỗi vì thế khác hẳn toast thường.
 */
export function Toaster({
  containerAriaLabel,
  closeButtonAriaLabel,
  toastOptions,
  ...props
}: ToasterProps) {
  const { toaster } = useUiStrings();
  return (
    <Sonner
      position="top-right"
      duration={4000}
      closeButton
      richColors
      containerAriaLabel={containerAriaLabel ?? toaster.containerAriaLabel}
      style={tokenStyle}
      toastOptions={{
        ...toastOptions,
        closeButtonAriaLabel: closeButtonAriaLabel ?? toaster.closeButtonAriaLabel,
        classNames: {
          ...toastOptions?.classNames,
          closeButton: cn(closeButtonClass, toastOptions?.classNames?.closeButton),
        },
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
