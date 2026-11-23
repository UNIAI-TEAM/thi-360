"use client";

import { cn, focusRing } from "@thi360/ui";
import { useId } from "react";

export interface ChoiceCardOption<T extends string> {
  value: T;
  label: string;
  description?: string;
  /** Câu giải thích phụ gắn vào lựa chọn qua `aria-describedby` (không làm bẩn tên trợ năng của radio). */
  hint?: string;
  disabled?: boolean;
}

export interface ChoiceCardGroupProps<T extends string> {
  legend?: string;
  name?: string;
  value: T;
  onChange: (value: T) => void;
  options: readonly ChoiceCardOption<T>[];
  required?: boolean;
  disabled?: boolean;
  hint?: string;
  variant?: "card" | "pill";
  size?: "sm" | "md";
  columns?: 1 | 2 | 3 | 4;
  layout?: "grid" | "inline" | "stack";
  ariaLabel?: string;
  ariaLabelledBy?: string;
  getOptionId?: (value: T) => string;
  /** Mỏ neo `data-field` gắn vào lựa chọn đầu tiên. */
  anchor?: string;
  className?: string;
}

const COLUMN_CLASSES: Record<1 | 2 | 3 | 4, string> = {
  1: "grid grid-cols-1 gap-2",
  2: "grid grid-cols-1 gap-2 sm:grid-cols-2",
  3: "grid grid-cols-1 gap-2 sm:grid-cols-3",
  4: "grid grid-cols-1 gap-2 sm:grid-cols-2 lg:grid-cols-4",
};

export function ChoiceCardGroup<T extends string>({
  legend,
  name,
  value,
  onChange,
  options,
  required,
  disabled,
  hint,
  variant = "card",
  size = "md",
  columns,
  layout,
  ariaLabel,
  ariaLabelledBy,
  getOptionId,
  anchor,
  className,
}: ChoiceCardGroupProps<T>) {
  const fallbackName = useId();
  const groupHintId = useId();
  const radioName = name ?? fallbackName;
  const resolvedLayout = layout ?? (variant === "pill" && !columns ? "inline" : "grid");
  const containerClass =
    resolvedLayout === "inline"
      ? "flex flex-wrap gap-2"
      : resolvedLayout === "stack"
        ? "flex flex-col gap-2"
        : columns
          ? COLUMN_CLASSES[columns]
          : "grid grid-cols-[repeat(auto-fit,minmax(min(9rem,100%),1fr))] gap-2";

  return (
    <fieldset
      className={cn("flex min-w-0 flex-col gap-1.5", className)}
      aria-label={!legend ? ariaLabel : undefined}
      aria-labelledby={!legend ? ariaLabelledBy : undefined}
      aria-describedby={hint ? groupHintId : undefined}
      disabled={disabled}
    >
      {legend && (
        <legend className="mb-1.5 text-field-label font-medium text-text-secondary">
          {legend}
          {required && (
            <span aria-hidden className="ml-0.5 text-danger">
              *
            </span>
          )}
        </legend>
      )}
      <div className={containerClass}>
        {options.map((option, index) => {
          const checked = option.value === value;
          const optionDisabled = Boolean(disabled || option.disabled);
          const optionId = getOptionId?.(option.value);
          const optionHintId = option.hint ? `${fallbackName}-${option.value}-hint` : undefined;
          const isPill = variant === "pill";
          return (
            <label
              key={option.value}
              htmlFor={optionId}
              className={cn(
                "flex min-w-0 cursor-pointer gap-2.5 rounded-md border transition-colors duration-150 motion-reduce:transition-none",
                isPill
                  ? cn("items-center", size === "sm" ? "min-h-8 px-2.5 text-body" : "min-h-10 px-3.5")
                  : cn("items-start", size === "sm" ? "p-2 text-body" : "p-3"),
                checked ? "border-primary-text bg-primary-50" : "border-border bg-surface hover:border-border-strong",
                optionDisabled && "cursor-not-allowed opacity-60",
              )}
            >
              <input
                id={optionId}
                type="radio"
                name={radioName}
                value={option.value}
                checked={checked}
                required={required}
                disabled={option.disabled}
                onChange={() => onChange(option.value)}
                data-field={index === 0 ? anchor : undefined}
                aria-describedby={optionHintId}
                className={cn("mt-0.5 size-4 shrink-0 accent-primary-fill", isPill && "mt-0", focusRing)}
              />
              <span className={cn("flex min-w-0 flex-col", isPill ? "py-1.5" : "gap-0.5")}>
                <span
                  className={cn(
                    "font-semibold",
                    isPill && !option.hint && !option.description && "whitespace-nowrap",
                    checked ? "text-primary-text" : "text-text",
                  )}
                >
                  {option.label}
                </span>
                {option.description && <span className="text-caption text-text-muted">{option.description}</span>}
                {option.hint && (
                  <span id={optionHintId} aria-hidden="true" className="text-caption font-normal text-text-muted">
                    {option.hint}
                  </span>
                )}
              </span>
            </label>
          );
        })}
      </div>
      {hint && (
        <p id={groupHintId} className="text-caption text-text-muted">
          {hint}
        </p>
      )}
    </fieldset>
  );
}
