import { useState, type ReactNode } from "react";
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

type ReasonProps =
  | {
      /** Có nhãn thì hiện ô lý do (DESIGN.md mục 5.10). */
      reasonLabel: string;
      /** Quy tắc yêu cầu lý do: nút xác nhận khóa tới khi ô lý do có chữ. */
      requireReason?: boolean;
    }
  | { reasonLabel?: undefined; requireReason?: never };

export type ConfirmDialogProps = ReasonProps & {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  title: string;
  /** Nêu rõ đối tượng và số bản ghi bị ảnh hưởng. */
  description: ReactNode;
  confirmLabel: string;
  cancelLabel: string;
  closeLabel: string;
  /** Nhận lý do đã cắt khoảng trắng (chuỗi rỗng nếu không có ô lý do hoặc để trống). */
  onConfirm: (reason: string) => void;
  /** `danger` (mặc định) cho khóa, xóa, reset, ngừng sử dụng. */
  tone?: "danger" | "primary";
  /** Đang thực hiện: khóa cả hai nút. Hộp thoại không tự đóng, bên gọi đóng khi xong. */
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
  reasonLabel,
  requireReason = false,
}: ConfirmDialogProps) {
  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent closeLabel={closeLabel}>
        <DialogHeader>
          <DialogTitle>{title}</DialogTitle>
          <DialogDescription>{description}</DialogDescription>
        </DialogHeader>
        <ConfirmBody
          reasonLabel={reasonLabel}
          requireReason={requireReason}
          confirmLabel={confirmLabel}
          cancelLabel={cancelLabel}
          tone={tone}
          loading={loading}
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
  onCancel,
  onConfirm,
}: ConfirmBodyProps) {
  const [reason, setReason] = useState("");
  const trimmed = reason.trim();

  return (
    <>
      {reasonLabel && (
        <FormField label={reasonLabel} required={requireReason}>
          {(field) => (
            <Textarea {...field} value={reason} onChange={(event) => setReason(event.target.value)} />
          )}
        </FormField>
      )}
      <DialogFooter>
        <Button variant="secondary" disabled={loading} onClick={onCancel}>
          {cancelLabel}
        </Button>
        <Button
          variant={tone}
          loading={loading}
          disabled={requireReason && trimmed === ""}
          onClick={() => onConfirm(trimmed)}
        >
          {confirmLabel}
        </Button>
      </DialogFooter>
    </>
  );
}
