"use client";

import * as DialogPrimitive from "@radix-ui/react-dialog";
import { X } from "lucide-react";
import type { ComponentProps } from "react";
import { cn } from "../lib/cn";
import { focusRing } from "../lib/styles";
import { useUiStrings } from "./ui-strings";

export const Dialog = DialogPrimitive.Root;
export const DialogTrigger = DialogPrimitive.Trigger;
export const DialogClose = DialogPrimitive.Close;

export function DialogOverlay({
  className,
  ...props
}: ComponentProps<typeof DialogPrimitive.Overlay>) {
  return (
    <DialogPrimitive.Overlay
      className={cn(
        "fixed inset-0 z-50 bg-overlay data-[state=open]:animate-fade-in data-[state=closed]:animate-fade-out motion-reduce:animate-none",
        className,
      )}
      {...props}
    />
  );
}

export type DialogContentProps = ComponentProps<typeof DialogPrimitive.Content> & {
  /** `md` rộng 480px (biểu mẫu ngắn), `lg` rộng 720px. */
  size?: "md" | "lg";
  /** Tên nút đóng (×) cho trình đọc màn hình. Bắt buộc vì không có chuỗi mặc định. */
  closeLabel?: string;
};

export function DialogContent({
  className,
  children,
  size = "md",
  closeLabel,
  ...props
}: DialogContentProps) {
  const { common } = useUiStrings();
  return (
    <DialogPrimitive.Portal>
      <DialogOverlay />
      <DialogPrimitive.Content
        className={cn(
          "fixed top-1/2 left-1/2 z-50 flex max-h-[calc(100dvh-2rem)] w-[calc(100vw-2rem)] -translate-x-1/2 -translate-y-1/2 flex-col gap-4 rounded-2xl bg-surface p-6 shadow-xl",
          "data-[state=open]:animate-dialog-in data-[state=closed]:animate-dialog-out motion-reduce:animate-none",
          size === "lg" ? "max-w-[720px]" : "max-w-[480px]",
          className,
        )}
        {...props}
      >
        {/*
          Vùng cuộn: cao tối đa bằng phần còn lại của hộp thoại (max-h theo viewport) nên
          nội dung dài cuộn bên trong, tiêu đề (DialogHeader) và chân (DialogFooter) dính
          mép trên/dưới. Margin âm + padding chừa chỗ cho vòng focus (overflow cắt), không đổi
          kích thước nội dung.
        */}
        <div className="-mx-2 -my-1 flex min-h-0 flex-1 flex-col gap-4 overflow-y-auto px-2 py-1">
          {children}
        </div>
        <DialogPrimitive.Close
          aria-label={closeLabel ?? common.close}
          className={cn(
            "absolute top-4 right-4 inline-flex size-9 items-center justify-center rounded-lg text-text-muted hover:bg-subtle",
            focusRing,
          )}
        >
          <X aria-hidden className="size-[18px]" />
        </DialogPrimitive.Close>
      </DialogPrimitive.Content>
    </DialogPrimitive.Portal>
  );
}

export function DialogHeader({ className, ...props }: ComponentProps<"div">) {
  return (
    <div
      className={cn("sticky top-0 z-10 flex flex-col gap-1.5 bg-surface pr-8", className)}
      {...props}
    />
  );
}

export function DialogTitle({
  className,
  ...props
}: ComponentProps<typeof DialogPrimitive.Title>) {
  return (
    <DialogPrimitive.Title
      className={cn("text-h3 font-bold text-text", className)}
      {...props}
    />
  );
}

export function DialogDescription({
  className,
  ...props
}: ComponentProps<typeof DialogPrimitive.Description>) {
  return (
    <DialogPrimitive.Description
      className={cn("text-body text-text-muted", className)}
      {...props}
    />
  );
}

/** Nút căn phải, nút chính ở ngoài cùng bên phải (đặt nút chính cuối cùng). */
export function DialogFooter({ className, ...props }: ComponentProps<"div">) {
  return (
    <div
      className={cn("sticky bottom-0 z-10 flex justify-end gap-3 bg-surface", className)}
      {...props}
    />
  );
}
