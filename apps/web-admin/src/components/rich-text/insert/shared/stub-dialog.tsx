"use client";

import { useTranslations } from "next-intl";
import { InsertDialogShell } from "./insert-dialog-shell";

export interface StubDialogProps {
  /** Trạng thái mở của hộp (prop `open`/`onOpenChange` mà trình soạn truyền cho dialog). */
  open: boolean;
  onOpenChange: (open: boolean) => void;
  /** Tiêu đề và mô tả — nhãn/mô tả đã dịch của mục (`richText.insert.<id>.label|.description`). */
  title: string;
  description?: string;
}

/**
 * Hộp tạm "Đang làm" cho mục chèn chưa có hộp thật (lane sóng 2 thay từng hộp). Không có thao tác
 * chèn: chỉ Đóng/Hủy.
 */
export function StubDialog({ open, onOpenChange, title, description }: StubDialogProps) {
  const t = useTranslations("richText.insert.stub");
  return (
    <InsertDialogShell open={open} onOpenChange={onOpenChange} title={title} description={description} hideFooter>
      <p className="text-body text-text-muted">{t("body")}</p>
    </InsertDialogShell>
  );
}
