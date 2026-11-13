"use client";

import * as CheckboxPrimitive from "@radix-ui/react-checkbox";
import { Check, Minus } from "lucide-react";
import type { ComponentProps } from "react";
import { cn } from "../lib/cn";
import { focusRing } from "../lib/styles";

export type CheckboxProps = ComponentProps<typeof CheckboxPrimitive.Root>;

/** `checked="indeterminate"` hiện dấu trừ (chọn một phần). */
export function Checkbox({ className, checked, ...props }: CheckboxProps) {
  return (
    <CheckboxPrimitive.Root
      checked={checked}
      className={cn(
        "peer grid size-5 shrink-0 place-items-center rounded-sm border border-border-strong bg-surface text-on-primary transition-colors duration-150 motion-reduce:transition-none",
        "hover:border-primary-600 disabled:cursor-not-allowed disabled:opacity-50",
        "data-[state=checked]:border-primary-600 data-[state=checked]:bg-primary-600",
        "data-[state=indeterminate]:border-primary-600 data-[state=indeterminate]:bg-primary-600",
        focusRing,
        className,
      )}
      {...props}
    >
      <CheckboxPrimitive.Indicator>
        {checked === "indeterminate" ? (
          <Minus aria-hidden className="size-3.5" strokeWidth={3} />
        ) : (
          <Check aria-hidden className="size-3.5" strokeWidth={3} />
        )}
      </CheckboxPrimitive.Indicator>
    </CheckboxPrimitive.Root>
  );
}
