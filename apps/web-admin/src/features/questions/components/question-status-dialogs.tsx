"use client";

import { ConfirmDialog } from "@thi360/ui";
import { useTranslations } from "next-intl";
import type { QuestionSummary } from "../types";

/**
 * Hộp thoại chuyển trạng thái câu hỏi, dùng ở danh sách (UNI-2592) và trang chi tiết (UNI-2593).
 * Bản nền chỉ giữ chữ ký; UNI-2592 làm đủ theo canvas `KiemTraSanSang` (liệt kê điều kiện còn thiếu) và
 * `NgungCauHoi` (bài thi đang dùng câu này). Bên gọi chỉ truyền câu hỏi và nhận kết quả.
 */
export interface QuestionStatusDialogProps {
  question: QuestionSummary | null;
  open: boolean;
  onOpenChange: (open: boolean) => void;
  /** Gọi sau khi đổi trạng thái thành công, với câu hỏi đã cập nhật. */
  onDone?: (question: QuestionSummary) => void;
}

export function MarkReadyDialog({ question, open, onOpenChange }: QuestionStatusDialogProps) {
  const t = useTranslations("questions.statusDialogs");
  return (
    <ConfirmDialog
      open={open && question !== null}
      onOpenChange={onOpenChange}
      tone="primary"
      title={t("markReadyTitle")}
      description={question?.code ?? ""}
      onConfirm={() => onOpenChange(false)}
    />
  );
}

export function RetireQuestionDialog({ question, open, onOpenChange }: QuestionStatusDialogProps) {
  const t = useTranslations("questions.statusDialogs");
  return (
    <ConfirmDialog
      open={open && question !== null}
      onOpenChange={onOpenChange}
      title={t("retireTitle")}
      description={question?.code ?? ""}
      onConfirm={() => onOpenChange(false)}
    />
  );
}
