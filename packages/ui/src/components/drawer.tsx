"use client";

import * as DialogPrimitive from "@radix-ui/react-dialog";
import { X } from "lucide-react";
import type { ComponentProps } from "react";
import { cn } from "../lib/cn";
import { focusRing } from "../lib/styles";
import { DialogDescription, DialogOverlay, DialogTitle } from "./dialog";
import { useUiStrings } from "./ui-strings";

export const Drawer = DialogPrimitive.Root;
export const DrawerTrigger = DialogPrimitive.Trigger;
export const DrawerClose = DialogPrimitive.Close;

export type DrawerContentProps = ComponentProps<typeof DialogPrimitive.Content> & {
  /** Tên nút đóng (×) cho trình đọc màn hình. */
  closeLabel?: string;
};

/** Trượt từ phải, rộng 520px, dùng cho chi tiết (DESIGN.md mục 5.10). */
export function DrawerContent({ className, children, closeLabel, ...props }: DrawerContentProps) {
  const { common } = useUiStrings();
  return (
    <DialogPrimitive.Portal>
      <DialogOverlay />
      <DialogPrimitive.Content
        className={cn(
          "fixed inset-y-0 right-0 z-50 flex w-full max-w-[520px] flex-col gap-4 overflow-y-auto rounded-l-xl bg-surface p-6 shadow-xl",
          "data-[state=open]:animate-drawer-in data-[state=closed]:animate-drawer-out motion-reduce:animate-none",
          className,
        )}
        {...props}
      >
        {children}
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

export function DrawerHeader({ className, ...props }: ComponentProps<"div">) {
  return <div className={cn("flex flex-col gap-1.5 pr-8", className)} {...props} />;
}

export const DrawerTitle = DialogTitle;
export const DrawerDescription = DialogDescription;
