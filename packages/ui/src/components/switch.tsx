import * as SwitchPrimitive from "@radix-ui/react-switch";
import type { ComponentProps } from "react";
import { cn } from "../lib/cn";
import { focusRing } from "../lib/styles";

export type SwitchProps = ComponentProps<typeof SwitchPrimitive.Root>;

export function Switch({ className, ...props }: SwitchProps) {
  return (
    <SwitchPrimitive.Root
      className={cn(
        "peer inline-flex h-6 w-11 shrink-0 items-center rounded-full bg-border-strong p-0.5 transition-colors duration-150 motion-reduce:transition-none",
        "data-[state=checked]:bg-primary-600 disabled:cursor-not-allowed disabled:opacity-50",
        focusRing,
        className,
      )}
      {...props}
    >
      <SwitchPrimitive.Thumb className="block size-5 rounded-full bg-on-primary shadow-sm transition-transform duration-150 data-[state=checked]:translate-x-5 motion-reduce:transition-none" />
    </SwitchPrimitive.Root>
  );
}
