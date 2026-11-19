"use client";

import * as SelectPrimitive from "@radix-ui/react-select";
import { Check, ChevronDown } from "lucide-react";
import { cn } from "../lib/cn";
import { fieldBase } from "../lib/styles";
import { RichTextView } from "../rich-text";
import { htmlToText } from "./helpers";

export interface MatchSelectOption {
  value: string;
  /** Chữ cái cột phải (a, b, …). */
  letter: string;
  /** Nội dung mục phải (HTML): hiện bằng `RichTextView` nên công thức và hóa học dựng đúng. */
  html: string;
  disabled?: boolean;
}

/**
 * Nội dung một mục phải: chữ cái + nội dung đề. Chỉ nhánh này gắn `data-exam-content`; tên truy cập và
 * chữ chờ chọn của ô là chữ giao diện nên nằm ngoài vùng đó (DESIGN.md mục 8).
 */
export function MatchOptionContent({
  letter,
  html,
  wrap = false,
  className,
}: {
  letter: string;
  html: string;
  /** `true`: xuống dòng (ô review); mặc định cắt bằng dấu ba chấm (trong ô chọn một dòng). */
  wrap?: boolean;
  className?: string;
}) {
  return (
    <span data-exam-content className={cn("flex min-w-0 items-baseline gap-1.5", className)}>
      <strong className="shrink-0 font-semibold">{letter}.</strong>
      <RichTextView
        className={cn("min-w-0 flex-1 [&_p]:m-0!", wrap ? "wrap-break-word" : "truncate [&_p]:inline")}
        html={html}
      />
    </span>
  );
}

/**
 * Ô chọn của Ghép đôi ở chế độ "select". Dùng riêng thay vì `Select` chung vì nhãn của `Select` chỉ là chữ
 * thuần, còn mục phải có thể chứa công thức (`\(x^2\)`, `\ce{…}`) cần dựng bằng `RichTextView`.
 * Chữ thuần (`htmlToText`) chỉ dùng cho `textValue`, tức tìm theo ký tự gõ.
 */
export function MatchSelect({
  options,
  value,
  placeholder,
  onValueChange,
  className,
  "aria-label": ariaLabel,
}: {
  options: MatchSelectOption[];
  value: string;
  placeholder: string;
  onValueChange: (value: string) => void;
  className?: string;
  "aria-label": string;
}) {
  const selected = options.find((option) => option.value === value);
  return (
    <SelectPrimitive.Root value={value} onValueChange={onValueChange}>
      <SelectPrimitive.Trigger
        aria-label={ariaLabel}
        className={cn(
          fieldBase,
          "flex h-10 items-center justify-between gap-2 px-3 text-left data-[placeholder]:text-text-placeholder",
          className,
        )}
      >
        <SelectPrimitive.Value placeholder={placeholder}>
          {selected && <MatchOptionContent letter={selected.letter} html={selected.html} />}
        </SelectPrimitive.Value>
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
                textValue={`${option.letter}. ${htmlToText(option.html)}`}
                className="relative flex min-h-9 cursor-default items-center rounded-sm py-1.5 pr-8 pl-3 text-label font-medium text-text outline-hidden select-none data-[disabled]:opacity-50 data-[highlighted]:bg-subtle data-[state=checked]:text-primary-text"
              >
                <SelectPrimitive.ItemText>
                  <MatchOptionContent letter={option.letter} html={option.html} />
                </SelectPrimitive.ItemText>
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
