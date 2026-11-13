"use client";

import { Slot } from "@radix-ui/react-slot";
import { cva, type VariantProps } from "class-variance-authority";
import type { ComponentProps, ReactNode } from "react";
import { cn } from "../lib/cn";
import { focusRing } from "../lib/styles";
import { Tooltip } from "./tooltip";

const iconButtonVariants = cva(
  [
    "inline-flex shrink-0 items-center justify-center rounded-lg transition-colors duration-150 motion-reduce:transition-none",
    "[&_svg]:size-[18px] aria-disabled:cursor-not-allowed aria-disabled:opacity-50",
    focusRing,
  ],
  {
    variants: {
      tone: {
        default: "text-text-secondary hover:bg-subtle aria-disabled:hover:bg-transparent",
        danger: "text-danger hover:bg-danger-bg aria-disabled:hover:bg-transparent",
      },
      size: { md: "size-10", lg: "size-11" },
    },
    defaultVariants: { tone: "default", size: "md" },
  },
);

export type IconButtonProps = Omit<ComponentProps<"button">, "aria-label" | "children"> &
  VariantProps<typeof iconButtonVariants> & {
    /** Bắt buộc: tên thao tác kèm đối tượng, ví dụ "Sửa vai trò Vận hành" (DESIGN.md mục 5.9). */
    "aria-label": string;
    /** Một icon lucide 18px. */
    icon: ReactNode;
    /** Nội dung tooltip. Mặc định là `aria-label`; thao tác bị khóa nên nêu lý do ở đây. */
    tooltip?: ReactNode;
  };

/**
 * Nút chỉ có icon cho cột Thao tác. Bị khóa (`disabled` hoặc `aria-disabled`) dùng `aria-disabled` thay vì `disabled`
 * để vẫn focus và rê chuột được, tooltip mới nêu được lý do khóa.
 */
export function IconButton({
  icon,
  tooltip,
  tone,
  size,
  className,
  disabled,
  onClick,
  type = "button",
  "aria-label": ariaLabel,
  "aria-disabled": ariaDisabled,
  ...props
}: IconButtonProps) {
  const locked = Boolean(disabled) || ariaDisabled === true || ariaDisabled === "true";
  return (
    <Tooltip content={tooltip ?? ariaLabel}>
      <button
        type={type}
        aria-label={ariaLabel}
        className={cn(iconButtonVariants({ tone, size }), className)}
        {...props}
        aria-disabled={locked || undefined}
        onClick={(event) => {
          if (locked) {
            event.preventDefault();
            return;
          }
          onClick?.(event);
        }}
      >
        <Slot aria-hidden>{icon}</Slot>
      </button>
    </Tooltip>
  );
}
