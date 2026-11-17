"use client";

import { Button, Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from "@thi360/ui";
import { useTranslations } from "next-intl";
import type { FormEvent, ReactNode } from "react";

export interface InsertDialogShellProps {
  /** `open`/`onOpenChange` của `Dialog` — mở hộp chèn. */
  open: boolean;
  onOpenChange: (open: boolean) => void;
  title: string;
  description?: string;
  /** Gọi khi bấm nút chính (mặc định nhãn "Chèn"); form submit trong hộp cũng gọi nó. */
  onSubmit?: (event?: FormEvent) => void;
  /** Nhãn nút chính — mặc định `richText.insertDialog.insert` ("Chèn"). */
  submitLabel?: string;
  /** `true` khóa nút chính (ví dụ thiếu trường bắt buộc, LaTeX lỗi). */
  submitDisabled?: boolean;
  /** Hộp nào không có thao tác chèn ngay (hộp tạm) thì ẩn hàng nút. */
  hideFooter?: boolean;
  children: ReactNode;
}

/**
 * Khung chung của hộp thoại chèn (spec 7): `Dialog` của kit, rộng 1000px (trong 980–1020) trên màn
 * rộng, toàn màn dưới 640px; tiêu đề, mô tả và hàng nút Hủy/Chèn sẵn có. Dialog của kit tự trả focus
 * về nút đã mở.
 */
export function InsertDialogShell({
  open,
  onOpenChange,
  title,
  description,
  onSubmit,
  submitLabel,
  submitDisabled = false,
  hideFooter = false,
  children,
}: InsertDialogShellProps) {
  const t = useTranslations("richText");
  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent
        closeLabel={t("insertDialog.close")}
        size="xl"
        className="sm:max-w-[1000px] max-sm:top-0 max-sm:left-0 max-sm:translate-x-0 max-sm:translate-y-0 max-sm:h-dvh max-sm:max-h-dvh max-sm:w-screen max-sm:rounded-none"
      >
        <DialogHeader>
          <DialogTitle>{title}</DialogTitle>
          {description && <DialogDescription>{description}</DialogDescription>}
        </DialogHeader>
        <form
          onSubmit={(event) => {
            event.preventDefault();
            // Dialog dựng trong portal: submit React vẫn nổi bọt lên biểu mẫu của trang chứa trình soạn.
            event.stopPropagation();
            onSubmit?.(event);
          }}
        >
          {children}
          {!hideFooter && (
            <DialogFooter>
              <Button type="button" variant="secondary" onClick={() => onOpenChange(false)}>
                {t("insertDialog.cancel")}
              </Button>
              <Button type="submit" disabled={submitDisabled || !onSubmit}>
                {submitLabel ?? t("insertDialog.insert")}
              </Button>
            </DialogFooter>
          )}
        </form>
      </DialogContent>
    </Dialog>
  );
}
