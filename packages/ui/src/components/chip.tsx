import type { ComponentProps } from "react";
import { cn } from "../lib/cn";
import { focusRing } from "../lib/styles";

const base =
  "inline-flex items-center gap-2 rounded-full border text-caption font-medium whitespace-nowrap";

type ChipCommon = {
  /** Class nền của chấm màu 8px đứng trước chữ, ví dụ `bg-primary-600`. Không có thì không hiện chấm. */
  dotClassName?: string;
};

export type ChipProps =
  | (ChipCommon &
      ComponentProps<"span"> & {
        /** Chip nhỏ, chỉ để hiển thị (DESIGN.md mục 5.3). */
        variant: "tag";
      })
  | (ChipCommon &
      ComponentProps<"button"> & {
        /** Chip lọc: bấm để chọn hoặc bỏ chọn (mặc định). */
        variant?: "filter";
        selected?: boolean;
        /** `sm` cao 32px (admin), `md` cao 40px (web học viên). */
        size?: "sm" | "md";
      });

export function Chip(props: ChipProps) {
  if (props.variant === "tag") {
    const { variant, dotClassName, className, children, ...rest } = props;
    return (
      <span
        data-variant={variant}
        className={cn(
          base,
          "h-8 border-primary-600 bg-primary-50 px-3 text-primary-700",
          className,
        )}
        {...rest}
      >
        {dotClassName && <Dot className={dotClassName} />}
        {children}
      </span>
    );
  }

  const {
    variant = "filter",
    dotClassName,
    className,
    children,
    selected = false,
    size = "sm",
    type = "button",
    ...rest
  } = props;
  return (
    <button
      type={type}
      data-variant={variant}
      aria-pressed={selected}
      className={cn(
        base,
        "border-border bg-surface px-4 text-text-secondary transition-colors duration-150 motion-reduce:transition-none",
        "hover:border-border-strong disabled:pointer-events-none disabled:opacity-50",
        "aria-pressed:border-primary-600 aria-pressed:bg-primary-600 aria-pressed:text-on-primary",
        size === "md" ? "h-10" : "h-8",
        focusRing,
        className,
      )}
      {...rest}
    >
      {dotClassName && <Dot className={dotClassName} />}
      {children}
    </button>
  );
}

function Dot({ className }: { className: string }) {
  return <span aria-hidden className={cn("size-2 shrink-0 rounded-full", className)} />;
}
