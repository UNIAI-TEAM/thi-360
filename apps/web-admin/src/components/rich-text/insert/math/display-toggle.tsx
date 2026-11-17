"use client";

import { cn, focusRing } from "@thi360/ui";

export interface DisplayToggleProps {
  /** `true` là Dòng riêng (`span.math-block`), `false` là Trong dòng (`span.math-inline`). */
  display: boolean;
  onChange: (display: boolean) => void;
  /** Tên của nhóm hai nút (`aria-label`). */
  label: string;
  inlineLabel: string;
  blockLabel: string;
  className?: string;
}

/**
 * Hai nút Trong dòng / Dòng riêng theo bản vẽ (thanh nền xám, nút chọn nổi lên). Dùng ở hộp công thức và khung
 * "Công thức đang chọn". Là nút bật/tắt (`aria-pressed`): nút đang chọn là `true`.
 */
export function DisplayToggle({ display, onChange, label, inlineLabel, blockLabel, className }: DisplayToggleProps) {
  const options = [
    { value: false, label: inlineLabel },
    { value: true, label: blockLabel },
  ];
  return (
    <div role="group" aria-label={label} className={cn("inline-flex gap-0.5 self-start rounded-xl bg-subtle p-1", className)}>
      {options.map((option) => {
        const selected = display === option.value;
        return (
          <button
            key={String(option.value)}
            type="button"
            aria-pressed={selected}
            onClick={() => onChange(option.value)}
            className={cn(
              "h-8 rounded-lg px-3 text-label whitespace-nowrap transition-colors duration-150 motion-reduce:transition-none",
              selected ? "bg-surface font-semibold text-primary-text shadow-sm" : "font-medium text-text-muted hover:text-text",
              focusRing,
            )}
          >
            {option.label}
          </button>
        );
      })}
    </div>
  );
}
