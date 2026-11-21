"use client";

import {
  Button,
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
  notify,
} from "@thi360/ui";
import { CircleAlert, LoaderCircle } from "lucide-react";
import { useTranslations } from "next-intl";
import { type ReactNode, useEffect, useRef, useState } from "react";

import { isBundleRevisionConflict, resolveBundleErrorMessage } from "../errors";
import { useDiscontinueBundleMutation, usePublishBundleMutation } from "../queries";
import type { BundleDetailResponse, BundleStatus } from "../types";

export interface BundleActionTarget {
  id: string;
  code: string;
  name: string;
  revision: number;
  status: BundleStatus;
}

interface BundleActionMutation {
  isPending: boolean;
  mutate: (
    variables: { id: string; expectedRevision: number },
    options: {
      onSuccess: (updated: BundleDetailResponse) => void;
      onError: (err: unknown) => void;
    },
  ) => void;
}

interface BundleConfirmActionDialogProps {
  bundle: BundleActionTarget | null;
  open: boolean;
  onOpenChange: (open: boolean) => void;
  onCompleted?: (updated: BundleDetailResponse) => void;
  onConflict?: () => void | Promise<void>;
  tone: "primary" | "danger";
  title: string;
  description: ReactNode;
  confirmLabel: string;
  successToast: (updated: BundleDetailResponse) => string;
  mutation: BundleActionMutation;
}

function BundleConfirmActionDialog({
  bundle,
  open,
  onOpenChange,
  onCompleted,
  onConflict,
  tone,
  title,
  description,
  confirmLabel,
  successToast,
  mutation,
}: BundleConfirmActionDialogProps) {
  const t = useTranslations("bundles");
  const tErrors = useTranslations("bundles.errors");

  const [submitError, setSubmitError] = useState<unknown>(null);
  const [isRefreshingConflict, setIsRefreshingConflict] = useState(false);
  const [conflictRefreshError, setConflictRefreshError] = useState<unknown>(null);

  const effectiveOpen = open && bundle !== null;
  const sessionKey = effectiveOpen && bundle ? bundle.id : null;
  const [prevSessionKey, setPrevSessionKey] = useState<string | null>(sessionKey);
  if (sessionKey !== prevSessionKey) {
    setPrevSessionKey(sessionKey);
    setSubmitError(null);
    setIsRefreshingConflict(false);
    setConflictRefreshError(null);
  }

  const mountedRef = useRef(true);
  const isOpenRef = useRef(effectiveOpen);
  const sessionSeqRef = useRef(0);

  useEffect(() => {
    mountedRef.current = true;
    return () => {
      mountedRef.current = false;
    };
  }, []);

  useEffect(() => {
    isOpenRef.current = effectiveOpen;
    sessionSeqRef.current += 1;
    return () => {
      isOpenRef.current = false;
    };
  }, [effectiveOpen, sessionKey]);

  const isBusy = mutation.isPending || isRefreshingConflict;
  const isConfirmDisabled = isBusy || Boolean(conflictRefreshError);

  const resetDialogErrors = () => {
    setSubmitError(null);
    setIsRefreshingConflict(false);
    setConflictRefreshError(null);
  };

  const handleOpenChange = (next: boolean) => {
    if (isBusy && !next) return;
    if (!next) {
      resetDialogErrors();
    }
    onOpenChange(next);
  };

  const runConflictRefresh = async () => {
    if (!onConflict) return;
    const mySession = sessionSeqRef.current;
    setIsRefreshingConflict(true);
    setConflictRefreshError(null);
    try {
      await onConflict();
      if (!mountedRef.current || !isOpenRef.current || sessionSeqRef.current !== mySession) {
        return;
      }
      setConflictRefreshError(null);
    } catch (refreshErr) {
      if (!mountedRef.current || !isOpenRef.current || sessionSeqRef.current !== mySession) {
        return;
      }
      setConflictRefreshError(refreshErr);
    } finally {
      if (mountedRef.current && isOpenRef.current && sessionSeqRef.current === mySession) {
        setIsRefreshingConflict(false);
      }
    }
  };

  const handleConfirm = () => {
    if (!bundle || isConfirmDisabled) return;
    setSubmitError(null);
    setConflictRefreshError(null);
    mutation.mutate(
      { id: bundle.id, expectedRevision: bundle.revision },
      {
        onSuccess: (updated) => {
          if (!mountedRef.current) return;
          resetDialogErrors();
          onOpenChange(false);
          notify.success(successToast(updated));
          onCompleted?.(updated);
        },
        onError: (err) => {
          if (!mountedRef.current || !isOpenRef.current) return;
          setSubmitError(err);
          if (isBundleRevisionConflict(err) && onConflict) {
            void runConflictRefresh();
          }
        },
      },
    );
  };

  if (!bundle) return null;

  return (
    <Dialog open={effectiveOpen} onOpenChange={handleOpenChange}>
      <DialogContent closeLabel={t("actions.close")}>
        <DialogHeader>
          <DialogTitle>{title}</DialogTitle>
          <DialogDescription>{description}</DialogDescription>
        </DialogHeader>

        {conflictRefreshError ? (
          <div
            role="alert"
            className="flex flex-wrap items-center justify-between gap-2 rounded-lg border border-danger bg-danger-bg px-3 py-2 text-caption text-danger"
          >
            <span className="flex items-start gap-2">
              <CircleAlert aria-hidden className="mt-0.5 size-4 shrink-0" />
              <span>
                {t("dialogs.conflictRefreshFailed", {
                  reason: resolveBundleErrorMessage(conflictRefreshError, tErrors),
                })}
              </span>
            </span>
            <Button
              type="button"
              variant="secondary"
              size="sm"
              loading={isRefreshingConflict}
              onClick={() => void runConflictRefresh()}
            >
              {t("actions.retry")}
            </Button>
          </div>
        ) : isRefreshingConflict ? (
          <div
            role="status"
            className="flex items-center gap-2 rounded-lg border border-border bg-subtle px-3 py-2 text-caption text-text-secondary"
          >
            <LoaderCircle
              aria-hidden
              className="size-4 shrink-0 animate-spin motion-reduce:animate-[spin_6s_linear_infinite]"
            />
            <span>{t("dialogs.refreshingConflict")}</span>
          </div>
        ) : submitError ? (
          <div
            role="alert"
            className="rounded-lg border border-danger bg-danger-bg px-3 py-2 text-caption text-danger"
          >
            {resolveBundleErrorMessage(submitError, tErrors)}
          </div>
        ) : null}

        <DialogFooter>
          <Button
            type="button"
            variant="secondary"
            disabled={isBusy}
            onClick={() => handleOpenChange(false)}
          >
            {t("actions.cancel")}
          </Button>
          <Button
            type="button"
            variant={tone}
            loading={isBusy}
            disabled={isConfirmDisabled}
            onClick={handleConfirm}
          >
            {confirmLabel}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}

export function BundlePublishDialog({
  bundle,
  open,
  onOpenChange,
  onCompleted,
  onConflict,
}: {
  bundle: BundleActionTarget | null;
  open: boolean;
  onOpenChange: (open: boolean) => void;
  onCompleted?: (updated: BundleDetailResponse) => void;
  onConflict?: () => void | Promise<void>;
}) {
  const t = useTranslations("bundles");
  const publishMutation = usePublishBundleMutation();
  const isRepublish = bundle?.status === "PUBLISHED";

  return (
    <BundleConfirmActionDialog
      bundle={bundle}
      open={open}
      onOpenChange={onOpenChange}
      onCompleted={onCompleted}
      onConflict={onConflict}
      tone="primary"
      title={isRepublish ? t("dialogs.publish.republishTitle") : t("dialogs.publish.title")}
      description={
        bundle ? (
          <span>
            {t("dialogs.publish.descriptionPrefix")}{" "}
            <strong data-exam-content>{bundle.name}</strong>{" "}
            <span data-exam-content className="font-mono">
              ({bundle.code})
            </span>
            . {t("dialogs.publish.descriptionSuffix")}
          </span>
        ) : null
      }
      confirmLabel={isRepublish ? t("actions.republish") : t("actions.publish")}
      successToast={(updated) => t("dialogs.publish.successToast", { code: updated.code })}
      mutation={publishMutation}
    />
  );
}

export function BundleDiscontinueDialog({
  bundle,
  open,
  onOpenChange,
  onCompleted,
  onConflict,
}: {
  bundle: BundleActionTarget | null;
  open: boolean;
  onOpenChange: (open: boolean) => void;
  onCompleted?: (updated: BundleDetailResponse) => void;
  onConflict?: () => void | Promise<void>;
}) {
  const t = useTranslations("bundles");
  const discontinueMutation = useDiscontinueBundleMutation();

  return (
    <BundleConfirmActionDialog
      bundle={bundle}
      open={open}
      onOpenChange={onOpenChange}
      onCompleted={onCompleted}
      onConflict={onConflict}
      tone="danger"
      title={t("dialogs.discontinue.title")}
      description={
        bundle ? (
          <span>
            {t("dialogs.discontinue.descriptionPrefix")}{" "}
            <strong data-exam-content>{bundle.name}</strong>{" "}
            <span data-exam-content className="font-mono">
              ({bundle.code})
            </span>
            ? {t("dialogs.discontinue.descriptionSuffix")}
          </span>
        ) : null
      }
      confirmLabel={t("actions.discontinue")}
      successToast={(updated) => t("dialogs.discontinue.successToast", { code: updated.code })}
      mutation={discontinueMutation}
    />
  );
}
