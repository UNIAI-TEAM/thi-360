import { cva, type VariantProps } from "class-variance-authority";
import type { ComponentProps } from "react";
import { cn } from "../lib/cn";

const cardVariants = cva("rounded-lg border border-border bg-surface shadow-xs", {
  variants: {
    padding: { none: "", md: "p-4", lg: "p-6" },
  },
  defaultVariants: { padding: "lg" },
});

export type CardProps = ComponentProps<"div"> & VariantProps<typeof cardVariants>;

/** Thẻ gom nhóm ở web admin: không có hiệu ứng hover (DESIGN.md mục 5.4). */
export function Card({ className, padding, ...props }: CardProps) {
  return <div className={cn(cardVariants({ padding }), className)} {...props} />;
}
