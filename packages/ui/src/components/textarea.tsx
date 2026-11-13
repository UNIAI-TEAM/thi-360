import type { ComponentProps } from "react";
import { cn } from "../lib/cn";
import { fieldBase } from "../lib/styles";

export type TextareaProps = ComponentProps<"textarea">;

export function Textarea({ className, rows = 3, ...props }: TextareaProps) {
  return (
    <textarea
      rows={rows}
      className={cn(fieldBase, "min-h-20 resize-y px-3 py-2.5", className)}
      {...props}
    />
  );
}
