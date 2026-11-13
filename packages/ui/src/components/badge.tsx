import type { ComponentProps } from "react";
import { cn } from "../lib/cn";

/** Huy hiệu đếm: viên thuốc, viền `border`, chữ `text-muted` (ví dụ "3 đề"). */
export function Badge({ className, ...props }: ComponentProps<"span">) {
  return (
    <span
      className={cn(
        "inline-flex items-center rounded-full border border-border px-2.5 py-0.5 text-caption font-medium whitespace-nowrap text-text-muted tabular-nums",
        className,
      )}
      {...props}
    />
  );
}
