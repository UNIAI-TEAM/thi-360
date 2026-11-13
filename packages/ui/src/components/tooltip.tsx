"use client";

import * as TooltipPrimitive from "@radix-ui/react-tooltip";
import type { ComponentProps, ReactNode } from "react";
import { cn } from "../lib/cn";

export type TooltipProps = {
  content: ReactNode;
  /** Phần tử kích hoạt; phải nhận được focus và rê chuột. */
  children: ReactNode;
  side?: ComponentProps<typeof TooltipPrimitive.Content>["side"];
  delayDuration?: number;
};

/** Tự mang Provider nên dùng được ở bất kỳ đâu. Hiện khi rê chuột hoặc focus bàn phím. */
export function Tooltip({ content, children, side = "top", delayDuration = 200 }: TooltipProps) {
  return (
    <TooltipPrimitive.Provider delayDuration={delayDuration}>
      <TooltipPrimitive.Root>
        <TooltipPrimitive.Trigger asChild>{children}</TooltipPrimitive.Trigger>
        <TooltipPrimitive.Portal>
          <TooltipPrimitive.Content
            side={side}
            sideOffset={6}
            className={cn(
              "z-50 max-w-72 rounded-sm bg-text px-2.5 py-1.5 text-caption font-medium text-page shadow-md",
              "data-[state=delayed-open]:animate-fade-in motion-reduce:animate-none",
            )}
          >
            {content}
          </TooltipPrimitive.Content>
        </TooltipPrimitive.Portal>
      </TooltipPrimitive.Root>
    </TooltipPrimitive.Provider>
  );
}
