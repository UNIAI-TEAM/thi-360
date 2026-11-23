"use client";

import { cn, focusRing } from "@thi360/ui";
import type { ReactNode } from "react";

export function PreviewScopeCard({
  name,
  value,
  label,
  checked,
  disabled = false,
  onSelect,
  children,
}: {
  name: string;
  value: string;
  label: string;
  checked: boolean;
  disabled?: boolean;
  onSelect: () => void;
  children?: ReactNode;
}) {
  return (
    <div
      className={cn(
        "flex min-w-0 flex-col gap-2 rounded-xl border p-3",
        checked ? "border-primary-text bg-primary-50 ring-1 ring-inset ring-primary-text" : "border-border bg-surface",
        disabled && "text-text-muted",
      )}
    >
      <label className={cn("flex items-center gap-2 font-semibold pointer-coarse:min-h-11", disabled ? "cursor-not-allowed" : "cursor-pointer")}>
        <input
          type="radio"
          name={name}
          value={value}
          checked={checked}
          disabled={disabled}
          onChange={onSelect}
          className={cn("size-4 shrink-0 accent-primary-fill", focusRing)}
        />
        {label}
      </label>
      {children}
    </div>
  );
}
