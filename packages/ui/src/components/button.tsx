import { Slot } from "@radix-ui/react-slot";
import { cva, type VariantProps } from "class-variance-authority";
import { LoaderCircle } from "lucide-react";
import type { ComponentProps, MouseEvent, ReactNode } from "react";
import { cn } from "../lib/cn";
import { focusRing } from "../lib/styles";

export const buttonVariants = cva(
  [
    "relative inline-flex shrink-0 items-center justify-center rounded-lg text-label font-semibold whitespace-nowrap",
    "transition-colors duration-150 motion-reduce:transition-none",
    "disabled:pointer-events-none disabled:opacity-50 aria-disabled:pointer-events-none aria-disabled:opacity-50",
    focusRing,
  ],
  {
    variants: {
      variant: {
        primary: "bg-primary-600 text-on-primary hover:bg-primary-700",
        secondary:
          "border border-border bg-surface text-text-secondary hover:border-border-strong hover:bg-subtle",
        outline:
          "border border-primary-200 bg-surface text-primary-800 hover:bg-primary-50",
        soft: "bg-primary-100 text-primary-600 hover:bg-primary-200",
        exam: "bg-accent-500 text-on-accent hover:brightness-95",
        danger: "bg-danger-solid text-on-primary hover:bg-danger-solid-hover",
        link: "bg-transparent text-primary-600 hover:underline",
      },
      size: {
        sm: "h-9 px-3",
        md: "h-10 px-4",
        lg: "h-12 px-7",
        xl: "h-13 px-7",
      },
    },
    compoundVariants: [
      { variant: "primary", size: ["lg", "xl"], class: "shadow-primary" },
      { variant: "link", class: "h-auto px-0" },
    ],
    defaultVariants: { variant: "primary", size: "md" },
  },
);

type ButtonBaseProps = Omit<ComponentProps<"button">, "children"> &
  VariantProps<typeof buttonVariants> & { children?: ReactNode };

export type ButtonProps = ButtonBaseProps &
  (
    | {
        /** Hiện spinner, khóa nút, giữ nguyên chiều rộng. */
        loading?: boolean;
        asChild?: false;
      }
    | {
        /** Phần tử con nhận kiểu nút. `disabled` thành `aria-disabled`, bỏ khỏi thứ tự Tab và chặn bấm. */
        asChild: true;
        loading?: never;
      }
  );

export function Button({
  className,
  variant,
  size,
  loading = false,
  asChild = false,
  disabled,
  children,
  type = "button",
  ...props
}: ButtonProps) {
  const classes = cn(buttonVariants({ variant, size }), className);

  if (asChild) {
    return (
      <Slot
        className={classes}
        {...props}
        {...(disabled && {
          "aria-disabled": true,
          tabIndex: -1,
          onClickCapture: (event: MouseEvent<HTMLElement>) => {
            event.preventDefault();
            event.stopPropagation();
          },
        })}
      >
        {children}
      </Slot>
    );
  }

  return (
    <button
      type={type}
      className={classes}
      disabled={disabled || loading}
      aria-busy={loading || undefined}
      {...props}
    >
      {loading && (
        <LoaderCircle
          aria-hidden
          className="absolute size-4 animate-spin motion-reduce:animate-none"
        />
      )}
      <span
        className={cn(
          "inline-flex items-center gap-2 [&_svg]:size-4 [&_svg]:shrink-0",
          loading && "invisible",
        )}
      >
        {children}
      </span>
    </button>
  );
}
