import type { ComponentProps } from "react";
import { cn } from "../lib/cn";

/** Khối giữ chỗ khi đang tải. Đặt kích thước bằng class (`h-4 w-32`). */
export function Skeleton({ className, ...props }: ComponentProps<"div">) {
  return (
    <div
      aria-hidden
      className={cn("animate-pulse rounded-md bg-subtle motion-reduce:animate-none", className)}
      {...props}
    />
  );
}
