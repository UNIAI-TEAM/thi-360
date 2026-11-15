"use client";

import * as DialogPrimitive from "@radix-ui/react-dialog";
import { X } from "lucide-react";
import type { ComponentProps } from "react";
import { cn } from "../lib/cn";
import { useReturnFocusContent } from "../lib/return-focus";
import { focusRing } from "../lib/styles";
import { Dialog, DialogDescription, DialogOverlay, DialogTitle } from "./dialog";
import { useUiStrings } from "./ui-strings";

/** Cùng gốc với `Dialog`: ghi nhớ nơi mở để trả focus về khi đóng. */
export const Drawer = Dialog;
export const DrawerTrigger = DialogPrimitive.Trigger;
export const DrawerClose = DialogPrimitive.Close;

export type DrawerContentProps = ComponentProps<typeof DialogPrimitive.Content> & {
  /** Tên nút đóng (×) cho trình đọc màn hình. */
  closeLabel?: string;
  /**
   * Cạnh màn hình drawer bám vào và trượt ra từ đó. `right` (mặc định) cho chi tiết; `left` cho menu điều hướng
   * trên màn hẹp (DESIGN.md mục 5.8). Góc bo nằm ở cạnh trong, phía nội dung trang.
   */
  side?: "right" | "left";
};

/**
 * Trượt từ phải (hoặc từ trái với `side="left"`), rộng 520px, dùng cho chi tiết (DESIGN.md mục 5.10). Màn hẹp hơn
 * `sm` đệm 16px thay vì 24px.
 *
 * Cùng cấu trúc với `DialogContent` (C-12): khung drawer KHÔNG cuộn; nội dung nằm trong một vùng cuộn bên trong,
 * `DrawerHeader` dính mép trên, `DrawerFooter` (tùy chọn) dính mép dưới, nút × nằm ngoài vùng cuộn nên luôn
 * bấm được. Nơi gọi không phải đổi gì: con của `DrawerContent` vẫn xếp cột, cách nhau 16px.
 *
 * Muốn chỉ một phần nội dung cuộn (ví dụ giữ thanh tab đứng yên): cho khối đó `min-h-0 flex-1` và cho phần cuộn
 * bên trong nó `min-h-0 flex-1 overflow-y-auto`; vùng cuộn của drawer là một cột flex cao bằng phần còn lại.
 */
export function DrawerContent({
  className,
  children,
  closeLabel,
  side = "right",
  onCloseAutoFocus,
  ...props
}: DrawerContentProps) {
  const { common } = useUiStrings();
  const returnFocusProps = useReturnFocusContent(onCloseAutoFocus);
  return (
    <DialogPrimitive.Portal>
      <DialogOverlay />
      <DialogPrimitive.Content
        className={cn(
          "fixed inset-y-0 z-50 flex w-full max-w-[520px] flex-col gap-4 bg-surface p-4 shadow-xl sm:p-6",
          // Chỉ chạy hiệu ứng trong `motion-safe:` (xem ghi chú ở `DialogOverlay`).
          side === "left"
            ? "left-0 rounded-r-xl motion-safe:data-[state=open]:animate-drawer-in-left motion-safe:data-[state=closed]:animate-drawer-out-left"
            : "right-0 rounded-l-xl motion-safe:data-[state=open]:animate-drawer-in motion-safe:data-[state=closed]:animate-drawer-out",
          className,
        )}
        {...props}
        {...returnFocusProps}
      >
        {/*
          Vùng cuộn, như ở DialogContent: margin âm + padding chỉ theo chiều ngang (chừa chỗ cho vòng focus bị
          overflow cắt); theo chiều dọc không có padding/margin âm để nội dung cuộn không lộ ra phía trên
          tiêu đề dính.
        */}
        <div className="-mx-2 flex min-h-0 flex-1 flex-col gap-4 overflow-y-auto px-2">
          {children}
        </div>
        {/* z-20 > z-10 của DrawerHeader/DrawerFooter dính: nút × luôn là phần tử trên cùng tại tâm của nó. */}
        <DialogPrimitive.Close
          aria-label={closeLabel ?? common.close}
          className={cn(
            "absolute top-4 right-4 z-20 inline-flex size-9 items-center justify-center rounded-lg text-text-muted hover:bg-subtle",
            focusRing,
          )}
        >
          <X aria-hidden className="size-[18px]" />
        </DialogPrimitive.Close>
      </DialogPrimitive.Content>
    </DialogPrimitive.Portal>
  );
}

/** Dính mép trên của vùng cuộn; chừa 40px bên phải cho nút ×. */
export function DrawerHeader({ className, ...props }: ComponentProps<"div">) {
  return (
    <div
      className={cn("sticky top-0 z-10 flex flex-col gap-1.5 bg-surface pb-2 pr-10", className)}
      {...props}
    />
  );
}

/**
 * Hàng nút dính mép dưới của drawer (tùy chọn). Đặt làm con cuối của `DrawerContent`; `mt-auto` đẩy nó xuống
 * đáy khi nội dung ngắn. Nút căn phải, nút chính ở ngoài cùng bên phải; thiếu chỗ (màn hẹp) thì xuống dòng.
 */
export function DrawerFooter({ className, ...props }: ComponentProps<"div">) {
  return (
    <div
      className={cn(
        "sticky bottom-0 z-10 mt-auto flex flex-wrap justify-end gap-3 bg-surface pt-2 pb-1",
        className,
      )}
      {...props}
    />
  );
}

export const DrawerTitle = DialogTitle;
export const DrawerDescription = DialogDescription;
