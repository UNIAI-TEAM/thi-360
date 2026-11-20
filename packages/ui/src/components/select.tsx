"use client";

import * as SelectPrimitive from "@radix-ui/react-select";
import { Check, ChevronDown } from "lucide-react";
import type { ReactNode } from "react";
import { cn } from "../lib/cn";
import { fieldBase } from "../lib/styles";

/**
 * `label` nhận nút React để bọc phần chữ là nội dung đề (`data-exam-content`); chữ thuần vẫn dùng như trước.
 * `textValue` là chữ mà Radix dùng để gõ tìm trong danh sách (mặc định: `label` nếu là chuỗi, còn không là chữ
 * của `ItemText`); nhãn có nút không chữ nên truyền `textValue` để gõ tìm đúng.
 */
export type SelectOption = { value: string; label: ReactNode; disabled?: boolean; textValue?: string };

export type SelectProps = {
  options: SelectOption[];
  value?: string;
  defaultValue?: string;
  onValueChange?: (value: string) => void;
  placeholder?: string;
  disabled?: boolean;
  name?: string;
  id?: string;
  size?: "md" | "lg";
  className?: string;
  "aria-label"?: string;
  "aria-invalid"?: boolean;
  "aria-required"?: boolean;
  "aria-describedby"?: string;
};

/** Ô chọn một giá trị. Giá trị rỗng không hợp lệ: dùng `placeholder` cho trạng thái chưa chọn. */
export function Select({
  options,
  value,
  defaultValue,
  onValueChange,
  placeholder,
  disabled,
  name,
  size = "md",
  className,
  ...triggerProps
}: SelectProps) {
  return (
    <SelectPrimitive.Root
      value={value}
      defaultValue={defaultValue}
      onValueChange={onValueChange}
      disabled={disabled}
      name={name}
    >
      <SelectPrimitive.Trigger
        className={cn(
          fieldBase,
          "flex items-center justify-between gap-2 px-3 text-left data-[placeholder]:text-text-placeholder [&>span:first-child]:min-w-0",
          size === "lg" ? "min-h-12 py-2" : "min-h-10 py-1.5",
          className,
        )}
        {...triggerProps}
      >
        <SelectPrimitive.Value placeholder={placeholder} />
        <SelectPrimitive.Icon asChild>
          <ChevronDown aria-hidden className="size-4 shrink-0 text-text-muted" />
        </SelectPrimitive.Icon>
      </SelectPrimitive.Trigger>
      <SelectPrimitive.Portal>
        <SelectPrimitive.Content
          position="popper"
          sideOffset={4}
          className="z-50 max-h-72 min-w-(--radix-select-trigger-width) overflow-hidden rounded-lg border border-border bg-surface shadow-md"
        >
          <SelectPrimitive.Viewport className="p-1">
            {options.map((option) => (
              <SelectPrimitive.Item
                key={option.value}
                value={option.value}
                disabled={option.disabled}
                textValue={option.textValue ?? (typeof option.label === "string" ? option.label : undefined)}
                className="relative flex min-h-9 cursor-default items-center rounded-sm py-1.5 pr-8 pl-3 text-label font-medium text-text outline-hidden select-none data-[disabled]:opacity-50 data-[highlighted]:bg-subtle data-[state=checked]:text-primary-text"
              >
                <SelectPrimitive.ItemText>{option.label}</SelectPrimitive.ItemText>
                <SelectPrimitive.ItemIndicator className="absolute right-2.5">
                  <Check aria-hidden className="size-4" />
                </SelectPrimitive.ItemIndicator>
              </SelectPrimitive.Item>
            ))}
          </SelectPrimitive.Viewport>
        </SelectPrimitive.Content>
      </SelectPrimitive.Portal>
    </SelectPrimitive.Root>
  );
}
