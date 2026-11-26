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
import { examTabFromPathname } from "../editor-tabs";

export interface ExamDraftLeaveDialogProps {
  open: boolean;
  saving: boolean;
  error: string | null;
  examId: string;
  pendingHref: string | null;
  onSaveAndGo: () => void;
  onDiscardAndGo: () => void;
  onStay: () => void;
}

/**
 * Hộp thoại rời bước khi còn thay đổi chưa lưu, dùng chung cho các bước soạn bài thi (UNI-1720):
 * cho phép Lưu và chuyển, Bỏ thay đổi và chuyển, hoặc Ở lại.
 */
export function ExamDraftLeaveDialog({
  open,
  saving,
  error,
  examId,
  pendingHref,
  onSaveAndGo,
  onDiscardAndGo,
  onStay,
}: ExamDraftLeaveDialogProps) {
  const t = useTranslations("exams.draftLeave");
  const tTabs = useTranslations("exams.editor.tabs");

  const destination = (() => {
    const base = `/exams/${encodeURIComponent(examId)}`;
    const path = pendingHref?.split(/[?#]/)[0] ?? "";
    if (!path || (path !== base && !path.startsWith(`${base}/`))) {
      return t("destinationPage");
    }
    const route = examTabFromPathname(path);
    return t("destinationTab", { tab: tTabs(route) });
  })();

  return (
    <Dialog open={open} onOpenChange={(next) => (!next && !saving ? onStay() : undefined)}>
      <DialogContent size="md" closeLabel={t("close")} data-testid="exam-draft-leave-dialog">
        <DialogHeader>
          <DialogTitle>{t("title")}</DialogTitle>
          <DialogDescription>
            {destination} {t("description")}
          </DialogDescription>
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
          <Button type="button" variant="danger" disabled={saving} onClick={onDiscardAndGo}>
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
