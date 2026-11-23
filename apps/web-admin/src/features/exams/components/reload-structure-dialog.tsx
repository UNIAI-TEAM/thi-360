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
import type { ReactNode } from "react";
import type { TypeCount } from "../types";
import { ExamName, richNodes } from "./exam-review-shared";

/** Một phần của cấu trúc: tên, số câu, thời lượng (`shared`: bài dùng thời lượng chung; `null`: mẫu chưa ghi). */
interface ReloadPartSide {
  name: string;
  questionCount: number;
  durationMinutes: number | null;
  shared: boolean;
  /** Số câu theo dạng câu của phần (mẫu: từ API; cấu trúc đang có: tính từ nhóm); bỏ trống: không hiện dòng theo dạng câu. */
  typeCounts?: readonly TypeCount[];
}

/**
 * Một phần sẽ được nạp lại. `before` có mặt (đổi phiên bản cấu trúc): hiện "trước → sau"; `before: null` là phần mới
 * chưa có trong cấu trúc hiện tại; bỏ trống thì chỉ hiện phần sẽ nạp.
 */
export interface ReloadPartRow extends ReloadPartSide {
  before?: ReloadPartSide | null;
}

interface ReloadDialogShellProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  size?: "md" | "lg";
  closeLabel: string;
  testId?: string;
  step?: string;
  title: ReactNode;
  description: string;
  cancelLabel: string;
  confirmLabel: string;
  loading?: boolean;
  error?: string | null;
  onConfirm: () => void;
  children: ReactNode;
}

/** Khung hộp thoại xác nhận nạp lại cấu trúc / đổi phiên bản (dùng chung bước 1 và bước 2). */
export function ReloadDialogShell({
  open,
  onOpenChange,
  size = "md",
  closeLabel,
  testId,
  step,
  title,
  description,
  cancelLabel,
  confirmLabel,
  loading = false,
  error,
  onConfirm,
  children,
}: ReloadDialogShellProps) {
  return (
    <Dialog open={open} onOpenChange={(next) => (loading && !next ? undefined : onOpenChange(next))}>
      <DialogContent size={size} closeLabel={closeLabel} data-testid={testId}>
        <DialogHeader>
          {step && <p className="text-caption font-semibold text-text-muted">{step}</p>}
          <DialogTitle>{title}</DialogTitle>
          <DialogDescription>{description}</DialogDescription>
        </DialogHeader>
        {children}
        {error && (
          <p role="alert" className="rounded-md border border-danger/30 bg-danger-bg px-3 py-2 text-body text-danger">
            {error}
          </p>
        )}
        <DialogFooter>
          <Button type="button" variant="secondary" disabled={loading} onClick={() => onOpenChange(false)}>
            {cancelLabel}
          </Button>
          <Button type="button" loading={loading} onClick={onConfirm}>
            {confirmLabel}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}

interface ReloadStructureDialogProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  title: ReactNode;
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
  const tType = useTranslations("content.questionType");
  const typeLabel = (type: TypeCount["questionType"]) => (type === "ANY" ? t("typeAny") : tType.has(type) ? tType(type) : type);
  /**
   * Dòng thay đổi theo dạng câu của một phần (bản vẽ `DoiPhienBan2B`: "10 trắc nghiệm → 5 ghép đôi"): mỗi dạng có ở một
   * trong hai phía mà số câu khác nhau; phần mới chưa có phía trước thì liệt kê dạng của phần sẽ nạp.
   */
  const typeChanges = (part: ReloadPartRow): Array<{ type: TypeCount["questionType"]; from: number | null; to: number }> => {
    const after = part.typeCounts ?? [];
    const before = part.before ? (part.before.typeCounts ?? []) : null;
    const countOf = (list: readonly TypeCount[], type: TypeCount["questionType"]) => list.find((item) => item.questionType === type)?.questionCount ?? 0;
    const types = [...new Set([...(before ?? []).map((item) => item.questionType), ...after.map((item) => item.questionType)])];
    return types
      .map((type) => ({ type, from: before === null ? null : countOf(before, type), to: countOf(after, type) }))
      .filter((change) => (change.from === null ? change.to > 0 : change.from !== change.to));
  };
  const sideMeta = (side: ReloadPartSide) =>
    side.shared
      ? t("partMetaShared", { questions: side.questionCount })
      : side.durationMinutes
        ? t("partMeta", { questions: side.questionCount, minutes: side.durationMinutes })
        : t("partMetaNoDuration", { questions: side.questionCount });
  return (
    <ReloadDialogShell
      open={open}
      onOpenChange={onOpenChange}
      closeLabel={t("close")}
      step={step}
      title={title}
      description={description}
      cancelLabel={cancelLabel ?? t("cancel")}
      confirmLabel={confirmLabel}
      loading={loading}
      error={error}
      onConfirm={onConfirm}
    >
      <section aria-label={t("listTitle", { count: parts.length })} className="flex flex-col gap-2">
        <h3 className="text-label font-semibold text-text">{t("listTitle", { count: parts.length })}</h3>
        {parts.length === 0 ? (
          <p className="text-body text-text-muted">{t("noParts")}</p>
        ) : (
          <ol className="flex max-h-64 flex-col overflow-y-auto rounded-md border border-border">
            {parts.map((part, index) => {
              const changes = typeChanges(part);
              return (
                <li
                  key={index}
                  className="flex flex-wrap items-baseline justify-between gap-x-3 gap-y-0.5 border-b border-border px-3 py-2 last:border-b-0"
                >
                  <span className="min-w-0 font-medium wrap-break-word text-text">
                    {richNodes(t, "partName", { order: index + 1, name: <ExamName>{part.name}</ExamName> })}
                  </span>
                  <span className="text-caption text-text-muted tabular-nums">
                    {part.before === undefined
                      ? sideMeta(part)
                      : richNodes(t, "partChange", {
                          before: part.before ? richNodes(t, "partWithMeta", { name: <ExamName>{part.before.name}</ExamName>, meta: sideMeta(part.before) }) : t("partNone"),
                          after: sideMeta(part),
                        })}
                  </span>
                  {changes.length > 0 && (
                    <ul className="flex basis-full flex-col gap-0.5 ps-3 text-caption text-text-secondary tabular-nums">
                      {changes.map((change) => (
                        <li key={change.type}>
                          {change.from === null
                            ? t("typeNew", { type: typeLabel(change.type), count: change.to })
                            : t("typeChange", { type: typeLabel(change.type), from: change.from, to: change.to })}
                        </li>
                      ))}
                    </ul>
                  )}
                </li>
              );
            })}
          </ol>
        )}
      </section>
      {note && <p className="text-body text-text-secondary">{note}</p>}
    </ReloadDialogShell>
  );
}
