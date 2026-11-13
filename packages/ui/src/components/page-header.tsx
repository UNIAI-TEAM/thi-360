import type { ReactNode } from "react";
import { cn } from "../lib/cn";

export type PageHeaderProps = {
  title: string;
  description?: string;
  /** Nút chính của màn (một nút), đặt bên phải. */
  actions?: ReactNode;
  className?: string;
};

/** Tiêu đề trang admin 22/30 đậm 700, nút chính căn phải (DESIGN.md mục 3, 5.8). */
export function PageHeader({ title, description, actions, className }: PageHeaderProps) {
  return (
    <header className={cn("flex flex-wrap items-center justify-between gap-4", className)}>
      <div className="min-w-0">
        <h1 className="text-page-title font-bold text-text">{title}</h1>
        {description && <p className="mt-1 text-body text-text-muted">{description}</p>}
      </div>
      {actions && <div className="flex items-center gap-3">{actions}</div>}
    </header>
  );
}
