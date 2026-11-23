"use client";

import { cn, focusRingInset } from "@thi360/ui";
import { ArrowLeftRight } from "lucide-react";
import { useTranslations } from "next-intl";
import type { ReactNode } from "react";

interface ScrollableTableProps {
  /** Tên của vùng cuộn khi không có tiêu đề để trỏ tới. */
  label?: string;
  /** Id của tiêu đề đặt tên cho vùng cuộn. */
  labelledBy?: string;
  /** Viền và bo góc của khung; bỏ trống khi khung ngoài đã có viền. */
  className?: string;
  children: ReactNode;
}

export function ScrollableTable({ label, labelledBy, className, children }: ScrollableTableProps) {
  const t = useTranslations("exams.create.overview");
  return (
    <div className={cn("overflow-hidden", className)}>
      <p className="flex items-center gap-1.5 border-b border-border bg-subtle px-3 py-1.5 text-caption text-text-muted md:hidden">
        <ArrowLeftRight aria-hidden className="size-3.5 shrink-0" />
        {t("scrollHint")}
      </p>
      <div role="region" tabIndex={0} aria-label={label} aria-labelledby={label ? undefined : labelledBy} className={cn("relative max-w-full overflow-x-auto", focusRingInset)}>
        {children}
      </div>
    </div>
  );
}
