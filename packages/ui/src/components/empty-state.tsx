import type { ReactNode } from "react";
import { cn } from "../lib/cn";

export type EmptyStateProps = {
  /** Một icon lucide; hiện trong ô tròn 48px. */
  icon?: ReactNode;
  title: string;
  description?: string;
  /** Nút hành động, chỉ truyền khi người dùng có quyền. */
  action?: ReactNode;
  className?: string;
};

/** Trạng thái rỗng: icon, một câu giải thích, nút hành động nếu có (DESIGN.md mục 5.9). */
export function EmptyState({ icon, title, description, action, className }: EmptyStateProps) {
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
      <h3 className="text-title font-semibold text-text">{title}</h3>
      {description && <p className="max-w-sm text-body text-text-muted">{description}</p>}
      {action}
    </div>
  );
}
