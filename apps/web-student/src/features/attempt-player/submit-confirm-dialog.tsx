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

export interface SubmitPartBreakdownRow {
  ord: number;
  partName: string;
  answeredCount: number;
  totalCount: number;
  missingCount: number;
}

export interface EssayUnderLimitWarning {
  itemId: string;
  displayNo: number;
  wordCount: number;
  wordLimit: number;
}

export interface SubmitEssayStatusSummary {
  wordCount: number;
  unsavedWords?: boolean;
}

export interface SubmitConfirmDialogProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  answeredCount: number;
  partialCount: number;
  blankCount: number;
  flaggedCount: number;
  totalCount: number;
  partBreakdown: SubmitPartBreakdownRow[];
  essayWarnings: EssayUnderLimitWarning[];
  essayStatus?: SubmitEssayStatusSummary | null;
  status: "idle" | "submitting" | "unconfirmed" | "reconciling";
  onConfirmSubmit: () => Promise<void> | void;
  onReconcile: () => Promise<void> | void;
}

export function SubmitConfirmDialog({
  open,
  onOpenChange,
  answeredCount,
  partialCount,
  blankCount,
  flaggedCount,
  totalCount,
  partBreakdown,
  essayWarnings,
  essayStatus = null,
  status,
  onConfirmSubmit,
  onReconcile,
}: SubmitConfirmDialogProps) {
  const t = useTranslations("attempt.dialogs.submitConfirm");
  const dialogT = useTranslations("attempt.dialogs");
  const isBusy = status === "submitting" || status === "reconciling";
  const isUnconfirmed = status === "unconfirmed" || status === "reconciling";

  return (
    <Dialog open={open} onOpenChange={(next) => { if (!isBusy) onOpenChange(next); }}>
      <DialogContent size="lg" closeLabel={dialogT("closeLabel")}>
        <DialogHeader>
          <DialogTitle>{isUnconfirmed ? t("unconfirmedTitle") : t("title")}</DialogTitle>
          <DialogDescription>
            {isUnconfirmed ? t("unconfirmedDesc") : t("description")}
          </DialogDescription>
        </DialogHeader>

        {!isUnconfirmed ? (
          <div className="space-y-4">
            <div className="grid grid-cols-2 gap-2.5 sm:grid-cols-4">
              <div className="rounded-lg border border-border bg-subtle p-3">
                <p className="text-caption font-medium text-text">
                  {t("answeredCount", { answered: answeredCount, total: totalCount })}
                </p>
              </div>
              <div className="rounded-lg border border-border bg-subtle p-3">
                <p className="text-caption font-medium text-text">
                  {t("partialCount", { count: partialCount })}
                </p>
              </div>
              <div className="rounded-lg border border-border bg-subtle p-3">
                <p className="text-caption font-medium text-text">
                  {t("blankCount", { count: blankCount })}
                </p>
              </div>
              <div className="rounded-lg border border-border bg-subtle p-3">
                <p className="text-caption font-medium text-text">
                  {t("flaggedCount", { count: flaggedCount })}
                </p>
              </div>
            </div>

            {partBreakdown.length > 0 ? (
              <div className="space-y-2">
                <h3 className="text-caption font-semibold text-text-secondary">
                  {t("partBreakdownTitle")}
                </h3>
                <ul className="divide-y divide-border rounded-xl border border-border bg-surface">
                  {partBreakdown.map((row) => (
                    <li
                      key={row.ord}
                      className="flex flex-wrap items-center justify-between gap-2 px-3.5 py-2.5 text-caption"
                    >
                      <span className="font-medium text-text" data-exam-content="">
                        {row.partName}
                      </span>
                      <span className="text-text-muted tabular-nums">
                        {t("partMissingRow", {
                          missing: row.missingCount,
                          total: row.totalCount,
                        })}
                      </span>
                    </li>
                  ))}
                </ul>
              </div>
            ) : null}

            {essayStatus ? (
              <div className="space-y-1 rounded-xl border border-border bg-subtle px-3.5 py-2.5 text-caption text-text-secondary">
                <p className="font-semibold text-text">{t("essaySectionTitle")}</p>
                <p>
                  {essayStatus.unsavedWords
                    ? t("essayUnsaved", { words: essayStatus.wordCount })
                    : t("essaySaved", { words: essayStatus.wordCount })}
                </p>
              </div>
            ) : null}

            {essayWarnings.length > 0 ? (
              <div
                role="status"
                className="space-y-2 rounded-xl border border-warning bg-warning-bg p-3.5 text-caption text-warning"
              >
                <div className="flex items-center gap-2 font-semibold">
                  <TriangleAlert className="size-4 shrink-0" aria-hidden="true" />
                  <span>{t("essayUnderLimitTitle")}</span>
                </div>
                <ul className="list-disc space-y-1 pl-5">
                  {essayWarnings.map((item) => (
                    <li key={item.itemId}>
                      {t("essayUnderLimitItem", {
                        displayNo: item.displayNo,
                        wordCount: item.wordCount,
                        wordLimit: item.wordLimit,
                      })}
                    </li>
                  ))}
                </ul>
              </div>
            ) : null}
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
              loading={status === "reconciling"}
              onClick={() => void onReconcile()}
            >
              {t("reconcile")}
            </Button>
          ) : (
            <Button
              type="button"
              variant="primary"
              loading={status === "submitting"}
              onClick={() => void onConfirmSubmit()}
            >
              {t("confirm")}
            </Button>
          )}
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
