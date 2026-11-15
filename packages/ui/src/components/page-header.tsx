import type { ReactNode } from "react";
import { cn } from "../lib/cn";

export type PageHeaderProps = {
  title: string;
  description?: string;
  /** Nút chính của màn (một nút), đặt bên phải. */
  actions?: ReactNode;
  className?: string;
};

/**
 * Tiêu đề trang admin 22/30 đậm 700, nút chính căn phải (DESIGN.md mục 3, 5.8).
 *
 * Màn hẹp hơn `sm` (điện thoại): nút xuống dưới tiêu đề và mô tả, căn trái, tự xuống dòng khi không đủ một hàng;
 * từ `sm` nút nằm cùng hàng với tiêu đề, căn phải, và vẫn xuống dòng khi tiêu đề dài. Tiêu đề và mô tả ngắt cả
 * từ quá dài, nên không phần tử nào đẩy trang tràn ngang.
 */
export function PageHeader({ title, description, actions, className }: PageHeaderProps) {
  return (
    <header
      className={cn(
        "flex flex-col gap-3 sm:flex-row sm:flex-wrap sm:items-center sm:justify-between sm:gap-4",
        className,
      )}
    >
      <div className="min-w-0 max-w-full">
        <h1 className="text-page-title font-bold wrap-break-word text-text">{title}</h1>
        {description && <p className="mt-1 text-body wrap-break-word text-text-muted">{description}</p>}
      </div>
      {actions && (
        <div data-slot="page-actions" className="flex max-w-full flex-wrap items-center gap-3">
          {actions}
        </div>
      )}
    </header>
  );
}
