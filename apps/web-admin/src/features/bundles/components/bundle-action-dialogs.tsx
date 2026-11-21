"use client";

import { ConfirmDialog, notify } from "@thi360/ui";
import { useTranslations } from "next-intl";
import { useState } from "react";

import { resolveBundleErrorMessage } from "../errors";
import { useDiscontinueBundleMutation, usePublishBundleMutation } from "../queries";
import type { BundleDetailResponse, BundleStatus } from "../types";

export interface BundleActionTarget {
  id: string;
  code: string;
  name: string;
  revision: number;
  status: BundleStatus;
}

export function BundlePublishDialog({
  bundle,
  open,
  onOpenChange,
  onCompleted,
}: {
  bundle: BundleActionTarget | null;
  open: boolean;
  onOpenChange: (open: boolean) => void;
  onCompleted?: (updated: BundleDetailResponse) => void;
}) {
  const t = useTranslations("bundles");
  const tErrors = useTranslations("bundles.errors");
  const publishMutation = usePublishBundleMutation();
  const [error, setError] = useState<unknown>(null);

  if (!bundle) return null;

  const isRepublish = bundle.status === "PUBLISHED";
  const handleOpenChange = (next: boolean) => {
    if (publishMutation.isPending && !next) return;
    if (!next) setError(null);
    onOpenChange(next);
  };

  const handleConfirm = () => {
    setError(null);
    publishMutation.mutate(
      { id: bundle.id, expectedRevision: bundle.revision },
      {
        onSuccess: (updated) => {
          setError(null);
          onOpenChange(false);
          notify.success(t("dialogs.publish.successToast", { code: updated.code }));
          onCompleted?.(updated);
        },
        onError: (err) => {
          setError(err);
        },
      },
    );
  };

  return (
    <ConfirmDialog
      open={open}
      onOpenChange={handleOpenChange}
      tone="primary"
      title={isRepublish ? t("dialogs.publish.republishTitle") : t("dialogs.publish.title")}
      description={
        <span>
          {t("dialogs.publish.descriptionPrefix")}{" "}
          <strong data-exam-content>{bundle.name}</strong>{" "}
          <span data-exam-content className="font-mono">
            ({bundle.code})
          </span>
          . {t("dialogs.publish.descriptionSuffix")}
        </span>
      }
      confirmLabel={isRepublish ? t("actions.republish") : t("actions.publish")}
      cancelLabel={t("actions.cancel")}
      closeLabel={t("actions.close")}
      loading={publishMutation.isPending}
      error={error ? resolveBundleErrorMessage(error, tErrors) : undefined}
      onConfirm={handleConfirm}
    />
  );
}

export function BundleDiscontinueDialog({
  bundle,
  open,
  onOpenChange,
  onCompleted,
}: {
  bundle: BundleActionTarget | null;
  open: boolean;
  onOpenChange: (open: boolean) => void;
  onCompleted?: (updated: BundleDetailResponse) => void;
}) {
  const t = useTranslations("bundles");
  const tErrors = useTranslations("bundles.errors");
  const discontinueMutation = useDiscontinueBundleMutation();
  const [error, setError] = useState<unknown>(null);

  if (!bundle) return null;

  const handleOpenChange = (next: boolean) => {
    if (discontinueMutation.isPending && !next) return;
    if (!next) setError(null);
    onOpenChange(next);
  };

  const handleConfirm = () => {
    setError(null);
    discontinueMutation.mutate(
      { id: bundle.id, expectedRevision: bundle.revision },
      {
        onSuccess: (updated) => {
          setError(null);
          onOpenChange(false);
          notify.success(t("dialogs.discontinue.successToast", { code: updated.code }));
          onCompleted?.(updated);
        },
        onError: (err) => {
          setError(err);
        },
      },
    );
  };

  return (
    <ConfirmDialog
      open={open}
      onOpenChange={handleOpenChange}
      tone="danger"
      title={t("dialogs.discontinue.title")}
      description={
        <span>
          {t("dialogs.discontinue.descriptionPrefix")}{" "}
          <strong data-exam-content>{bundle.name}</strong>{" "}
          <span data-exam-content className="font-mono">
            ({bundle.code})
          </span>
          ? {t("dialogs.discontinue.descriptionSuffix")}
        </span>
      }
      confirmLabel={t("actions.discontinue")}
      cancelLabel={t("actions.cancel")}
      closeLabel={t("actions.close")}
      loading={discontinueMutation.isPending}
      error={error ? resolveBundleErrorMessage(error, tErrors) : undefined}
      onConfirm={handleConfirm}
    />
  );
}
