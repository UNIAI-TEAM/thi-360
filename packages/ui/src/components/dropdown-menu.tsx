"use client";

import * as DropdownMenuPrimitive from "@radix-ui/react-dropdown-menu";
import type { ComponentProps } from "react";
import { cn } from "../lib/cn";

export const DropdownMenu = DropdownMenuPrimitive.Root;
export const DropdownMenuTrigger = DropdownMenuPrimitive.Trigger;
export const DropdownMenuGroup = DropdownMenuPrimitive.Group;
export const DropdownMenuRadioGroup = DropdownMenuPrimitive.RadioGroup;
export const DropdownMenuItemIndicator = DropdownMenuPrimitive.ItemIndicator;

export function DropdownMenuContent({
  className,
  sideOffset = 6,
  align = "end",
  ...props
}: ComponentProps<typeof DropdownMenuPrimitive.Content>) {
  return (
    <DropdownMenuPrimitive.Portal>
      <DropdownMenuPrimitive.Content
        sideOffset={sideOffset}
        align={align}
        className={cn(
          "z-50 min-w-48 overflow-x-hidden overflow-y-auto rounded-lg border border-border bg-surface p-1 shadow-md",
          // Danh sách dài (vd. Thêm nhãn của câu hỏi) gọn trong 20rem (~8 mục) và cuộn bên trong; màn thấp thì nhỏ hơn nữa
          // theo chỗ trống Radix đo được, trừ lề 8px (UNI-2878).
          "max-h-[min(20rem,calc(var(--radix-dropdown-menu-content-available-height)-0.5rem))]",
          // Chỉ chạy hiệu ứng trong `motion-safe:`: lớp `motion-reduce:animate-none` không thắng nổi bộ chọn `[data-state]` (U-13).
          "motion-safe:data-[state=open]:animate-fade-in",
          className,
        )}
        {...props}
      />
    </DropdownMenuPrimitive.Portal>
  );
}

/*
 * Mục đang được tô sáng chính là mục đang giữ focus. Dấu hiệu bình thường là nền (`data-[highlighted]`), mà chế
 * độ tương phản cao bỏ nền, nên dùng `focus:outline-hidden` thay cho `outline-none`: ở chế độ đó mục đang tô
 * sáng có viền ngoài 2px (C-13, S-04; DESIGN.md mục 5.13).
 *
 * Chiều cao: 40px với chuột (mật độ của máy tính), 44px khi thiết bị trỏ chính là ngón tay
 * (`pointer-coarse:` = `@media (pointer: coarse)`), theo WCAG 2.5.5 (S-12).
 */
export function DropdownMenuItem({
  className,
  destructive = false,
  ...props
}: ComponentProps<typeof DropdownMenuPrimitive.Item> & {
  /** Thao tác nguy hiểm: chữ màu danger. */
  destructive?: boolean;
}) {
  return (
    <DropdownMenuPrimitive.Item
      className={cn(
        "flex h-10 cursor-default items-center gap-2.5 pointer-coarse:h-11 rounded-sm px-3 text-label font-medium select-none focus:outline-hidden [&_svg]:size-[18px] [&_svg]:shrink-0",
        "data-[disabled]:pointer-events-none data-[disabled]:opacity-50 data-[highlighted]:bg-subtle",
        destructive ? "text-danger data-[highlighted]:bg-danger-bg" : "text-text-secondary",
        className,
      )}
      {...props}
    />
  );
}

export function DropdownMenuRadioItem({
  className,
  ...props
}: ComponentProps<typeof DropdownMenuPrimitive.RadioItem>) {
  return (
    <DropdownMenuPrimitive.RadioItem
      className={cn(
        "flex h-10 cursor-default items-center gap-2.5 pointer-coarse:h-11 rounded-sm px-3 text-label font-medium text-text-secondary select-none focus:outline-hidden [&_svg]:size-[18px] [&_svg]:shrink-0",
        "data-[disabled]:pointer-events-none data-[disabled]:opacity-50 data-[highlighted]:bg-subtle",
        "data-[state=checked]:bg-primary-50 data-[state=checked]:font-semibold data-[state=checked]:text-primary-text",
        className,
      )}
      {...props}
    />
  );
}

export function DropdownMenuLabel({
  className,
  ...props
}: ComponentProps<typeof DropdownMenuPrimitive.Label>) {
  return (
    <DropdownMenuPrimitive.Label
      className={cn("px-3 py-2 text-caption text-text-muted", className)}
      {...props}
    />
  );
}

export function DropdownMenuSeparator({
  className,
  ...props
}: ComponentProps<typeof DropdownMenuPrimitive.Separator>) {
  return (
    <DropdownMenuPrimitive.Separator
      className={cn("-mx-1 my-1 h-px bg-border", className)}
      {...props}
    />
  );
}
