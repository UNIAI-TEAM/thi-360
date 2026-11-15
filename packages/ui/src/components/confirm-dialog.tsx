"use client";

import { useRef, useState, type ReactNode } from "react";
import { Button } from "./button";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "./dialog";
import { FormField } from "./form-field";
import { Textarea } from "./textarea";
import { useUiStrings } from "./ui-strings";

type ReasonProps =
  | {
      /** Có nhãn thì hiện ô lý do (DESIGN.md mục 5.10). */
      reasonLabel: string;
      /**
       * Quy tắc yêu cầu lý do. Nút xác nhận KHÔNG bị khóa: bấm khi ô lý do trống thì ô được đánh dấu lỗi, nhận
       * focus, hiện dòng lỗi gắn với nó, và `onConfirm` không được gọi (U-15).
       */
      requireReason?: boolean;
    }
  | { reasonLabel?: undefined; requireReason?: never };

export type ConfirmDialogProps = ReasonProps & {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  title: string;
  /** Nêu rõ đối tượng và số bản ghi bị ảnh hưởng. */
  description: ReactNode;
  confirmLabel?: string;
  cancelLabel?: string;
  closeLabel?: string;
  /** Nhận lý do đã cắt khoảng trắng (chuỗi rỗng nếu không có ô lý do hoặc để trống). */
  onConfirm: (reason: string) => void;
  /** `danger` (mặc định) cho khóa, xóa, reset, ngừng sử dụng. */
  tone?: "danger" | "primary";
  /**
   * Lỗi của lần xác nhận vừa rồi (ví dụ máy chủ từ chối). Hiện NGAY TRONG hộp thoại dưới dạng `role="alert"`:
   * toast nằm ngoài hộp thoại đang mở thì người dùng bàn phím và trình đọc màn hình không tới được. Nơi gọi
   * xóa lỗi khi người dùng thử lại hoặc đóng hộp thoại.
   */
  error?: ReactNode;
  /** Đang thực hiện: khóa cả hai nút và ô lý do, Escape, nút × và bấm ra ngoài không đóng được. Hộp thoại không tự đóng, bên gọi đóng khi xong. */
  loading?: boolean;
};

export function ConfirmDialog({
  open,
  onOpenChange,
  title,
  description,
  confirmLabel,
  cancelLabel,
  closeLabel,
  onConfirm,
  tone = "danger",
  loading = false,
  error,
  reasonLabel,
  requireReason = false,
}: ConfirmDialogProps) {
  const { common } = useUiStrings();
  return (
    <Dialog open={open} onOpenChange={(next) => (loading && !next ? undefined : onOpenChange(next))}>
      <DialogContent closeLabel={closeLabel ?? common.close}>
        <DialogHeader>
          <DialogTitle>{title}</DialogTitle>
          <DialogDescription>{description}</DialogDescription>
        </DialogHeader>
        <ConfirmBody
          reasonLabel={reasonLabel}
          requireReason={requireReason}
          confirmLabel={confirmLabel ?? common.confirm}
          cancelLabel={cancelLabel ?? common.cancel}
          tone={tone}
          loading={loading}
          error={error}
          onCancel={() => onOpenChange(false)}
          onConfirm={onConfirm}
        />
      </DialogContent>
    </Dialog>
  );
}

type ConfirmBodyProps = {
  reasonLabel?: string;
  requireReason: boolean;
  confirmLabel: string;
  cancelLabel: string;
  tone: "danger" | "primary";
  loading: boolean;
  error?: ReactNode;
  onCancel: () => void;
  onConfirm: (reason: string) => void;
};

/** Giữ lý do trong phần nội dung hộp thoại, nên mỗi lần mở lại là một ô lý do trống. */
function ConfirmBody({
  reasonLabel,
  requireReason,
  confirmLabel,
  cancelLabel,
  tone,
  loading,
  error,
  onCancel,
  onConfirm,
}: ConfirmBodyProps) {
  const { confirmDialog } = useUiStrings();
  const [reason, setReason] = useState("");
  const [confirmedEmpty, setConfirmedEmpty] = useState(false);
  const reasonRef = useRef<HTMLTextAreaElement>(null);
  const trimmed = reason.trim();
  const reasonMissing = requireReason && trimmed === "";

  /*
   * Không khóa nút để chặn việc kiểm tra: nút bị khóa không nói được vì sao và không nhận focus bàn phím.
   * Cho bấm, rồi chỉ vào ô còn thiếu (U-15). Lỗi tự hết khi ô có chữ.
   */
  const handleConfirm = () => {
    if (reasonMissing) {
      setConfirmedEmpty(true);
      reasonRef.current?.focus();
      return;
    }
    onConfirm(trimmed);
  };

  return (
    <>
      {reasonLabel && (
        <FormField
          label={reasonLabel}
          required={requireReason}
          error={confirmedEmpty && reasonMissing ? confirmDialog.reasonRequired : undefined}
        >
          {(field) => (
            <Textarea
              {...field}
              ref={reasonRef}
              value={reason}
              disabled={loading}
              onChange={(event) => setReason(event.target.value)}
            />
          )}
        </FormField>
      )}
      {error ? (
        <div
          role="alert"
          className="rounded-lg border border-danger bg-danger-bg px-3 py-2 text-caption text-danger"
        >
          {error}
        </div>
      ) : null}
      <DialogFooter>
        <Button variant="secondary" disabled={loading} onClick={onCancel}>
          {cancelLabel}
        </Button>
        <Button variant={tone} loading={loading} onClick={handleConfirm}>
          {confirmLabel}
        </Button>
      </DialogFooter>
    </>
  );
}
