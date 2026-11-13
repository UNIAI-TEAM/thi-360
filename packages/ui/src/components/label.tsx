"use client";

import * as LabelPrimitive from "@radix-ui/react-label";
import type { ComponentProps } from "react";
import { cn } from "../lib/cn";

export type LabelProps = ComponentProps<typeof LabelPrimitive.Root> & {
  /** Hiện dấu * cạnh nhãn. Dấu này chỉ để nhìn; đặt `aria-required` ở ô nhập. */
  required?: boolean;
};

export function Label({ className, required, children, ...props }: LabelProps) {
  return (
    <LabelPrimitive.Root
      className={cn(
        "text-field-label font-medium text-text-secondary peer-disabled:opacity-60",
        className,
      )}
      {...props}
    >
      {children}
      {required && (
        <span aria-hidden className="ml-0.5 text-danger">
          *
        </span>
      )}
    </LabelPrimitive.Root>
  );
}
