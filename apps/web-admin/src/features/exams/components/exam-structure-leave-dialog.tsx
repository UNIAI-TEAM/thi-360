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

export interface ExamStructureLeaveDialogProps {
  open: boolean;
  /** Đang lưu ("Lưu và chuyển"): khóa các nút và không đóng được hộp thoại. */
  saving: boolean;
  /** Lỗi của lần lưu vừa rồi; hộp thoại ở lại và hiện lỗi (R-05). */
  error: string | null;
  onSaveAndGo: () => void;
  onDiscard: () => void;
  onStay: () => void;
}

/**
 * Hộp thoại ba nút khi còn thay đổi chưa lưu (R-05, bản vẽ `HopThoaiChuaLuu2B`): **Lưu và chuyển**, **Bỏ thay đổi**,
 * **Ở lại**. Hiện khi chuyển môn hoặc phần, quay lại bước 1, đổi tab và rời trang trong ứng dụng; đóng tab hoặc tải lại
 * trang dùng hộp thoại của trình duyệt. "Lưu và chuyển" lỗi thì ở lại và hiện lỗi.
 */
export function ExamStructureLeaveDialog({ open, saving, error, onSaveAndGo, onDiscard, onStay }: ExamStructureLeaveDialogProps) {
  const t = useTranslations("exams.structure.leave");
  return (
    <Dialog open={open} onOpenChange={(next) => (!next && !saving ? onStay() : undefined)}>
      <DialogContent size="md" closeLabel={t("close")} data-testid="structure-leave-dialog">
        <DialogHeader>
          <DialogTitle>{t("title")}</DialogTitle>
          <DialogDescription>{t("description")}</DialogDescription>
        </DialogHeader>
        {error && (
          <p role="alert" className="rounded-md border border-danger/30 bg-danger-bg px-3 py-2 text-body text-danger">
            {error}
          </p>
        )}
        <DialogFooter>
          <Button type="button" variant="secondary" disabled={saving} onClick={onStay}>
            {t("stay")}
          </Button>
          <Button type="button" variant="secondary" disabled={saving} onClick={onDiscard}>
            {t("discard")}
          </Button>
          <Button type="button" loading={saving} onClick={onSaveAndGo}>
            {t("saveAndGo")}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
