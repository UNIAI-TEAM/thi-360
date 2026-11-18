"use client";

import {
  Button,
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@thi360/ui";
import { useTranslations } from "next-intl";

/** Một phần của cấu trúc: tên, số câu, thời lượng (`shared`: bài dùng thời lượng chung; `null`: mẫu chưa ghi). */
export interface ReloadPartSide {
  name: string;
  questionCount: number;
  durationMinutes: number | null;
  shared: boolean;
}

/**
 * Một phần sẽ được nạp lại. `before` có mặt (đổi phiên bản cấu trúc): hiện "trước → sau"; `before: null` là phần mới
 * chưa có trong cấu trúc hiện tại; bỏ trống thì chỉ hiện phần sẽ nạp.
 */
export interface ReloadPartRow extends ReloadPartSide {
  before?: ReloadPartSide | null;
}

export interface ReloadStructureDialogProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  title: string;
  description: string;
  /** Các phần sẽ thay cấu trúc hiện tại (PBI-04 mục 5.3, mục 6: chỉ thay sau khi quản trị viên xác nhận). */
  parts: readonly ReloadPartRow[];
  confirmLabel: string;
  /** Nhãn nút hủy; bỏ trống dùng "Giữ cấu trúc hiện tại". */
  cancelLabel?: string;
  /** Dòng nhỏ trên tiêu đề, ví dụ "Bước 1/2". */
  step?: string;
  /** Đoạn cuối dưới danh sách (điều xảy ra với thay đổi chưa lưu và trạng thái kiểm tra). */
  note?: string;
  loading?: boolean;
  /** Lỗi của lần nạp vừa rồi, hiện ngay trong hộp thoại. */
  error?: string | null;
  onConfirm: () => void;
}

/**
 * Hộp xác nhận nạp lại cấu trúc theo mẫu (đổi kỳ thi, phạm vi, phần, môn hoặc phiên bản năm): liệt kê các phần sẽ được
 * nạp lại để quản trị viên đối chiếu trước khi cấu trúc đang sửa bị thay.
 */
export function ReloadStructureDialog({
  open,
  onOpenChange,
  title,
  description,
  parts,
  confirmLabel,
  cancelLabel,
  step,
  note,
  loading = false,
  error,
  onConfirm,
}: ReloadStructureDialogProps) {
  const t = useTranslations("exams.reload");
  const sideMeta = (side: ReloadPartSide) =>
    side.shared
      ? t("partMetaShared", { questions: side.questionCount })
      : side.durationMinutes
        ? t("partMeta", { questions: side.questionCount, minutes: side.durationMinutes })
        : t("partMetaNoDuration", { questions: side.questionCount });
  return (
    <Dialog open={open} onOpenChange={(next) => (loading && !next ? undefined : onOpenChange(next))}>
      <DialogContent size="md" closeLabel={t("close")}>
        <DialogHeader>
          {step && <p className="text-caption font-semibold text-text-muted">{step}</p>}
          <DialogTitle>{title}</DialogTitle>
          <DialogDescription>{description}</DialogDescription>
        </DialogHeader>
        <section aria-label={t("listTitle", { count: parts.length })} className="flex flex-col gap-2">
          <h3 className="text-label font-semibold text-text">{t("listTitle", { count: parts.length })}</h3>
          {parts.length === 0 ? (
            <p className="text-body text-text-muted">{t("noParts")}</p>
          ) : (
            <ol className="flex max-h-64 flex-col overflow-y-auto rounded-md border border-border">
              {parts.map((part, index) => (
                <li
                  key={index}
                  className="flex flex-wrap items-baseline justify-between gap-x-3 gap-y-0.5 border-b border-border px-3 py-2 last:border-b-0"
                >
                  <span className="min-w-0 font-medium wrap-break-word text-text">
                    {t("partName", { order: index + 1, name: part.name })}
                  </span>
                  <span className="text-caption text-text-muted tabular-nums">
                    {part.before === undefined
                      ? sideMeta(part)
                      : t("partChange", {
                          before: part.before ? t("partWithMeta", { name: part.before.name, meta: sideMeta(part.before) }) : t("partNone"),
                          after: sideMeta(part),
                        })}
                  </span>
                </li>
              ))}
            </ol>
          )}
        </section>
        {note && <p className="text-body text-text-secondary">{note}</p>}
        {error && (
          <p role="alert" className="rounded-md border border-danger/30 bg-danger-bg px-3 py-2 text-body text-danger">
            {error}
          </p>
        )}
        <DialogFooter>
          <Button type="button" variant="secondary" disabled={loading} onClick={() => onOpenChange(false)}>
            {cancelLabel ?? t("cancel")}
          </Button>
          <Button type="button" loading={loading} onClick={onConfirm}>
            {confirmLabel}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
