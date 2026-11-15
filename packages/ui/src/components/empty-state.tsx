"use client";

import type { ReactNode } from "react";
import { cn } from "../lib/cn";
import { useUiStrings } from "./ui-strings";

export type EmptyStateProps = {
  /** Một icon lucide; hiện trong ô tròn 48px. */
  icon?: ReactNode;
  title?: string;
  /**
   * Cấp tiêu đề (`<h1>`–`<h6>`), chỉ đổi nghĩa trong dàn ý của trang, không đổi cỡ chữ. Mặc định 2: trạng thái rỗng
   * thường nằm ngay dưới tiêu đề trang (`h1`), nên không nhảy cấp. Nằm dưới một tiêu đề khối `h2` thì truyền 3;
   * là nội dung duy nhất của trang (trang lỗi không có tiêu đề riêng) thì truyền 1.
   */
  headingLevel?: 1 | 2 | 3 | 4 | 5 | 6;
  description?: string;
  /** Nút hành động, chỉ truyền khi người dùng có quyền. */
  action?: ReactNode;
  className?: string;
};

/** Trạng thái rỗng: icon, một câu giải thích, nút hành động nếu có (DESIGN.md mục 5.9). */
export function EmptyState({
  icon,
  title,
  headingLevel = 2,
  description,
  action,
  className,
}: EmptyStateProps) {
  const { emptyState } = useUiStrings();
  const Heading = `h${headingLevel}` as const;
  return (
    <div className={cn("flex flex-col items-center gap-3 px-6 py-12 text-center", className)}>
      {icon && (
        <div
          aria-hidden
          className="grid size-12 place-items-center rounded-full bg-primary-50 text-primary-text [&_svg]:size-6"
        >
          {icon}
        </div>
      )}
      <Heading className="text-title font-semibold text-text">{title ?? emptyState.defaultTitle}</Heading>
      {description && <p className="max-w-sm text-body text-text-muted">{description}</p>}
      {action}
    </div>
  );
}
