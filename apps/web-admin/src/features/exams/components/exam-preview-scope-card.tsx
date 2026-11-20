"use client";

import { cn, focusRing } from "@thi360/ui";
import type { ReactNode } from "react";

/**
 * Một thẻ chọn của "Phạm vi sinh thử" (bản vẽ `SinhThu2B`): nút chọn kèm tên phạm vi, bên dưới là ô chọn của phạm vi đó.
 * Thẻ đang chọn viền đậm nền nhạt; thẻ bị khóa (Cả bài khi bài có phần chọn mã) chữ nhạt kèm lời nhắc.
 */
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
        "flex min-w-0 flex-col gap-2 rounded-xl p-3",
        checked ? "border-2 border-primary-text bg-primary-50" : "border border-border bg-surface",
        disabled && "text-text-muted",
      )}
    >
      <label className={cn("flex items-center gap-2 font-semibold", disabled ? "cursor-not-allowed" : "cursor-pointer")}>
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
