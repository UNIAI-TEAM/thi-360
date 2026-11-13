import type { ComponentProps, ReactNode } from "react";
import { cn } from "../lib/cn";
import { fieldBase } from "../lib/styles";

export type InputProps = Omit<ComponentProps<"input">, "size"> & {
  /** `md` cao 40px (admin), `lg` cao 48px (web học viên, đăng nhập). */
  size?: "md" | "lg";
  /** Icon dẫn đầu 16px. */
  leadingIcon?: ReactNode;
};

export function Input({ className, size = "md", leadingIcon, ...props }: InputProps) {
  const input = (
    <input
      className={cn(
        fieldBase,
        size === "lg" ? "h-12" : "h-10",
        "px-3",
        leadingIcon && "pl-10",
        className,
      )}
      {...props}
    />
  );

  if (!leadingIcon) return input;

  return (
    <div className="relative w-full">
      <span
        aria-hidden
        className="pointer-events-none absolute inset-y-0 left-3 flex items-center text-text-placeholder [&_svg]:size-4"
      >
        {leadingIcon}
      </span>
      {input}
    </div>
  );
}
