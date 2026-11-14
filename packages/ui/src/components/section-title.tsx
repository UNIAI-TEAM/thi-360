import type { ReactNode } from "react";
import { cn } from "../lib/cn";
import { Badge } from "./badge";

export type SectionTitleProps = {
  title: string;
  /** Huy hiệu đếm bên cạnh tiêu đề. */
  count?: string;
  /** Liên kết "Xem tất cả →" căn phải. */
  action?: ReactNode;
  /** Class nền của thanh dọc 4px (mặc định `bg-primary-600`). */
  barClassName?: string;
  className?: string;
};

/** Thanh dọc 4px bên trái, tiêu đề h3, huy hiệu đếm, liên kết căn phải (DESIGN.md mục 5.6). */
export function SectionTitle({ title, count, action, barClassName, className }: SectionTitleProps) {
  return (
    <div className={cn("flex items-center gap-3", className)}>
      <span aria-hidden className={cn("h-6 w-1 shrink-0 rounded-full bg-primary-600", barClassName)} />
      <h2 className="text-h3 font-bold text-text">{title}</h2>
      {count && <Badge>{count}</Badge>}
      {action && <div className="ml-auto">{action}</div>}
    </div>
  );
}
