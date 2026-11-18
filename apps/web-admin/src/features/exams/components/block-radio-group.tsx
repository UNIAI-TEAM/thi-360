"use client";

import { cn, focusRing } from "@thi360/ui";
import { useId } from "react";

export interface BlockRadioOption<T extends string> {
  value: T;
  label: string;
}

export interface BlockRadioGroupProps<T extends string> {
  /** Tên nhóm, đọc bằng trình đọc màn hình; `legend` ẩn khi nhóm nằm trong `fieldset` đã có chú giải riêng. */
  legend: string;
  value: T;
  options: readonly BlockRadioOption<T>[];
  onChange: (value: T) => void;
  disabled?: boolean;
  /** Mỏ neo của lựa chọn đầu tiên (`data-field`), xem `block-form-fields.ts`. */
  anchor?: string;
}

/** Nhóm lựa chọn một trong ít mục dạng thẻ nhỏ ("Câu đơn" / "Câu chùm", "Toàn môn" / "Theo chủ đề"). */
export function BlockRadioGroup<T extends string>({ legend, value, options, onChange, disabled, anchor }: BlockRadioGroupProps<T>) {
  const name = useId();
  return (
    <fieldset className="flex min-w-0 flex-col gap-1.5" disabled={disabled}>
      <legend className="mb-1.5 text-field-label font-medium text-text-secondary">{legend}</legend>
      <div className="flex flex-wrap gap-2">
        {options.map((option, index) => {
          const checked = option.value === value;
          return (
            <label
              key={option.value}
              className={cn(
                "flex min-h-10 cursor-pointer items-center gap-2.5 rounded-md border px-3.5 transition-colors duration-150 motion-reduce:transition-none",
                checked ? "border-primary-text bg-primary-50" : "border-border bg-surface hover:border-border-strong",
                disabled && "cursor-not-allowed opacity-60",
              )}
            >
              <input
                type="radio"
                name={name}
                value={option.value}
                checked={checked}
                onChange={() => onChange(option.value)}
                data-field={index === 0 ? anchor : undefined}
                className={cn("size-4 shrink-0 accent-primary-fill", focusRing)}
              />
              <span className={cn("font-semibold whitespace-nowrap", checked ? "text-primary-text" : "text-text")}>{option.label}</span>
            </label>
          );
        })}
      </div>
    </fieldset>
  );
}
