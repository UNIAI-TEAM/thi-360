"use client";

import {
  Button,
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
  cn,
  focusRing,
} from "@thi360/ui";
import { AlertTriangle, MonitorSmartphone } from "lucide-react";
import { useTranslations } from "next-intl";
import Link from "next/link";

export interface TakeoverDialogProps {
  open: boolean;
  examTitle?: string;
  lastSeenAt?: string;
  isSubmitting?: boolean;
  onCancel: () => void;
  onConfirmTakeover: () => Promise<void> | void;
}

export function TakeoverDialog({
  open,
  examTitle,
  lastSeenAt,
  isSubmitting = false,
  onCancel,
  onConfirmTakeover,
}: TakeoverDialogProps) {
  const t = useTranslations("attempt.takeover");
  const dialogT = useTranslations("attempt.dialogs");

  return (
    <Dialog open={open} onOpenChange={(next) => { if (!next && !isSubmitting) onCancel(); }}>
      <DialogContent size="md" closeLabel={dialogT("closeLabel")}>
        <DialogHeader>
          <DialogTitle>{t("title")}</DialogTitle>
          <DialogDescription>{t("description")}</DialogDescription>
        </DialogHeader>

        <div className="space-y-2 rounded-xl border border-border bg-subtle p-3.5 text-caption text-text-secondary">
          {examTitle ? (
            <p className="font-semibold text-text">
              {t("attemptLabel")}: <span data-exam-content="">{examTitle}</span>
            </p>
          ) : null}
          {lastSeenAt ? (
            <p>
              {t("lastSeenLabel")}: <time dateTime={lastSeenAt}>{lastSeenAt}</time>
            </p>
          ) : null}
          <p>{t("noteClock")}</p>
          <p>{t("noteUnsaved")}</p>
        </div>

        <DialogFooter>
          <Button type="button" variant="secondary" disabled={isSubmitting} onClick={onCancel}>
            {t("cancel")}
          </Button>
          <Button
            type="button"
            variant="primary"
            disabled={isSubmitting}
            onClick={() => void onConfirmTakeover()}
          >
            {t("confirm")}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}

export interface SessionEvictedViewProps {
  onTakeoverHere?: () => Promise<void> | void;
  isTakingOver?: boolean;
}

export function SessionEvictedView({
  onTakeoverHere,
  isTakingOver = false,
}: SessionEvictedViewProps) {
  const t = useTranslations("attempt.evicted");

  return (
    <section
      role="alert"
      className="mx-auto my-8 flex max-w-lg flex-col items-center gap-4 rounded-2xl border border-warning bg-surface p-8 text-center shadow-sm"
    >
      <div className="flex size-12 items-center justify-center rounded-full bg-warning-bg text-warning">
        <MonitorSmartphone className="size-6" aria-hidden="true" />
      </div>
      <h2 className="text-h3 font-bold text-text">{t("heading")}</h2>
      <p className="text-body text-text-muted">{t("description")}</p>
      <div className="flex flex-wrap items-center justify-center gap-3 pt-2">
        <Link
          href="/my-exams"
          className={cn(
            "inline-flex h-10 items-center justify-center rounded-lg border border-border bg-surface px-4 text-label font-medium text-text-secondary hover:bg-subtle",
            focusRing,
          )}
        >
          {t("backToMyExams")}
        </Link>
        {onTakeoverHere ? (
          <Button
            type="button"
            variant="primary"
            disabled={isTakingOver}
            onClick={() => void onTakeoverHere()}
          >
            {t("takeoverHere")}
          </Button>
        ) : null}
      </div>
    </section>
  );
}

export interface ReadonlyTabBannerProps {
  onSwitchHere: () => void;
}

export function ReadonlyTabBanner({ onSwitchHere }: ReadonlyTabBannerProps) {
  const t = useTranslations("attempt.tabLock");

  return (
    <div
      role="status"
      className="flex flex-wrap items-center justify-between gap-3 border-b border-warning bg-warning-bg px-4 py-2.5 text-caption text-warning"
    >
      <div className="flex items-center gap-2">
        <AlertTriangle className="size-4 shrink-0" aria-hidden="true" />
        <span className="font-semibold">{t("badge")}</span>
        <span>{t("description")}</span>
      </div>
      <Button type="button" variant="secondary" size="sm" onClick={onSwitchHere}>
        {t("switchHere")}
      </Button>
    </div>
  );
}
