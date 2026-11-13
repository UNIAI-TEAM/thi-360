import type { ComponentProps } from "react";
import { cn } from "../lib/cn";

export const statusTones = ["success", "warning", "danger", "info", "neutral"] as const;
export type StatusTone = (typeof statusTones)[number];

const toneClasses: Record<StatusTone, string> = {
  success: "bg-success-bg text-success",
  warning: "bg-warning-bg text-warning",
  danger: "bg-danger-bg text-danger",
  info: "bg-info-bg text-info",
  neutral: "bg-neutral-bg text-neutral",
};

export type StatusChipProps = Omit<ComponentProps<"span">, "children"> & {
  tone: StatusTone;
  /** Chữ trạng thái. Bắt buộc: không truyền trạng thái chỉ bằng màu (DESIGN.md mục 8). */
  children: string;
};

export function StatusChip({ tone, className, children, ...props }: StatusChipProps) {
  return (
    <span
      data-tone={tone}
      className={cn(
        "inline-flex h-6 items-center gap-1.5 rounded-full px-2.5 text-caption font-medium whitespace-nowrap",
        toneClasses[tone],
        className,
      )}
      {...props}
    >
      <span aria-hidden className="size-1.5 shrink-0 rounded-full bg-current" />
      {children}
    </span>
  );
}
