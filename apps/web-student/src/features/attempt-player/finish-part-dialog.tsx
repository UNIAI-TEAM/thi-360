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
import { TriangleAlert } from "lucide-react";
import { useTranslations } from "next-intl";

export interface FinishPartDialogProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  partName?: string;
  missingCount: number;
  partialCount: number;
  totalCount: number;
  nextPartState?: string | null;
  status: "idle" | "submitting" | "unconfirmed" | "reconciling";
  onConfirm: () => Promise<void> | void;
  onReconcile: () => Promise<void> | void;
}

export function FinishPartDialog({
  open,
  onOpenChange,
  partName,
  missingCount,
  partialCount,
  totalCount,
  nextPartState = null,
  status,
  onConfirm,
  onReconcile,
}: FinishPartDialogProps) {
  const t = useTranslations("attempt.dialogs.finishPart");
  const dialogT = useTranslations("attempt.dialogs");
  const isBusy = status === "submitting" || status === "reconciling";
  const isUnconfirmed = status === "unconfirmed" || status === "reconciling";

  return (
    <Dialog open={open} onOpenChange={(next) => { if (!isBusy) onOpenChange(next); }}>
      <DialogContent size="md" closeLabel={dialogT("closeLabel")}>
        <DialogHeader>
          <DialogTitle>{isUnconfirmed ? t("unconfirmedTitle") : t("title")}</DialogTitle>
          <DialogDescription>
            {isUnconfirmed ? t("unconfirmedDesc") : t("warningIrreversible")}
          </DialogDescription>
        </DialogHeader>

        {partName ? (
          <p className="text-caption font-semibold text-text-secondary" data-exam-content="">
            {partName}
          </p>
        ) : null}

        {!isUnconfirmed ? (
          <div className="space-y-2 rounded-xl border border-warning bg-warning-bg p-3.5 text-caption text-warning">
            <div className="flex items-start gap-2">
              <TriangleAlert className="mt-0.5 size-4 shrink-0" aria-hidden="true" />
              <div className="space-y-1">
                <p className="font-medium">
                  {t("missingSummary", { missing: missingCount, total: totalCount })}
                </p>
                {partialCount > 0 ? <p>{t("partialSummary", { partial: partialCount })}</p> : null}
                {nextPartState === "AWAITING_CHOICE" ? (
                  <p>{t("nextPartChoiceNote")}</p>
                ) : (
                  <p>{t("nextPartOpensNote")}</p>
                )}
              </div>
            </div>
          </div>
        ) : null}

        <DialogFooter>
          <Button
            type="button"
            variant="secondary"
            disabled={isBusy}
            onClick={() => onOpenChange(false)}
          >
            {t("cancel")}
          </Button>
          {isUnconfirmed ? (
            <Button
              type="button"
              variant="primary"
              disabled={status === "reconciling"}
              onClick={() => void onReconcile()}
            >
              {t("checkingState")}
            </Button>
          ) : (
            <Button
              type="button"
              variant="primary"
              disabled={status === "submitting"}
              onClick={() => void onConfirm()}
            >
              {t("confirm")}
            </Button>
          )}
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
