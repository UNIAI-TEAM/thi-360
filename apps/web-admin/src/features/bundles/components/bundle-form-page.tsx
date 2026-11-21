"use client";

import { Button, EmptyState, PageHeader, Skeleton } from "@thi360/ui";
import { AlertTriangle, ArrowLeft, Ban, CircleAlert, Upload, WifiOff } from "lucide-react";
import { useTranslations } from "next-intl";
import Link from "next/link";
import { useId, useState } from "react";

import { usePermissions } from "@/features/session/session-provider";
import { useSetBreadcrumbLeaf } from "@/features/shell/breadcrumb-leaf";
import { ApiStatusError } from "@/lib/api/query-client";

import { bundleErrorCode, resolveBundleErrorMessage } from "../errors";
import { useBundleDetailQuery } from "../queries";
import {
  BundleDiscontinueDialog,
  BundlePublishDialog,
} from "./bundle-action-dialogs";
import { BundleForm } from "./bundle-form";
import { BundleStatusBadges } from "./bundle-status-chips";

export function BundleCreatePage() {
  const t = useTranslations("bundles");
  const { can } = usePermissions();
  const formId = useId();
  useSetBreadcrumbLeaf(t("create.title"));

  return (
    <div className="flex flex-col gap-5">
      <PageHeader
        title={t("create.title")}
        description={t("create.description")}
        actions={
          <Button variant="secondary" asChild>
            <Link href="/bundles">
              <ArrowLeft />
              <span>{t("actions.backToList")}</span>
            </Link>
          </Button>
        }
      />

      <BundleForm
        mode="create"
        formId={formId}
        canModify={can("EXAM.MODIFY")}
      />
    </div>
  );
}

export function BundleEditPage({ bundleId }: { bundleId: string }) {
  const t = useTranslations("bundles");
  const tErrors = useTranslations("bundles.errors");
  const { can } = usePermissions();
  const canModify = can("EXAM.MODIFY");
  const canPublish = can("EXAM.PUBLISH");

  const query = useBundleDetailQuery(bundleId);
  const [hasConflict, setHasConflict] = useState(false);
  const [reloadSeq, setReloadSeq] = useState(0);
  const [isFormDirty, setIsFormDirty] = useState(false);
  const [publishOpen, setPublishOpen] = useState(false);
  const [discontinueOpen, setDiscontinueOpen] = useState(false);
  const formId = useId();
  const publishBlockedReasonId = useId();

  useSetBreadcrumbLeaf(query.data ? query.data.code : t("edit.title"));

  const handleConflict = async () => {
    setHasConflict(true);
    const refetchResult = await query.refetch();
    if (!refetchResult.isError) {
      setIsFormDirty(false);
      setReloadSeq((seq) => seq + 1);
    }
  };

  if (query.isLoading) {
    return (
      <div className="flex flex-col gap-5" aria-busy="true">
        <Skeleton className="h-9 w-64" />
        <Skeleton className="h-96 w-full" />
      </div>
    );
  }

  if (!query.data) {
    const isOffline = query.fetchStatus === "paused" && !query.isError;
    const code = bundleErrorCode(query.error);
    const notFound =
      code === "BUNDLE_NOT_FOUND" ||
      code === "NOT_FOUND" ||
      (query.error instanceof ApiStatusError && query.error.status === 404);

    return (
      <EmptyState
        headingLevel={1}
        icon={isOffline ? <WifiOff /> : undefined}
        title={
          isOffline
            ? t("list.offlineTitle")
            : notFound
              ? t("edit.notFoundTitle")
              : t("edit.errorTitle")
        }
        description={
          isOffline
            ? t("list.offlineDescription")
            : notFound
              ? t("edit.notFoundDescription")
              : query.error
                ? resolveBundleErrorMessage(query.error, tErrors)
                : t("edit.errorDescription")
        }
        action={
          notFound ? (
            <Button variant="secondary" asChild>
              <Link href="/bundles">{t("actions.backToList")}</Link>
            </Button>
          ) : (
            <Button variant="secondary" onClick={() => void query.refetch()}>
              {t("actions.retry")}
            </Button>
          )
        }
      />
    );
  }

  const detail = query.data;
  const actionTarget = {
    id: detail.id,
    code: detail.code,
    name: detail.name,
    revision: detail.revision,
    status: detail.status,
  };

  return (
    <div className="flex flex-col gap-5">
      <div className="flex flex-col gap-2">
        <header className="flex flex-col gap-3 sm:flex-row sm:flex-wrap sm:items-center sm:justify-between sm:gap-4">
          <div className="min-w-0 max-w-full">
            <h1 className="text-page-title font-bold wrap-break-word text-text">
              <span data-exam-content>{detail.name}</span>
            </h1>
            <p className="mt-1 text-body wrap-break-word text-text-muted">
              <span data-exam-content className="font-mono">
                {detail.code}
              </span>
            </p>
          </div>
          <div
            data-slot="page-actions"
            className="flex max-w-full flex-col items-start gap-1.5 sm:items-end"
          >
            <div className="flex flex-wrap items-center gap-2">
              <Button variant="secondary" asChild>
                <Link href="/bundles">
                  <ArrowLeft />
                  <span>{t("actions.backToList")}</span>
                </Link>
              </Button>
              {canPublish && detail.status === "PUBLISHED" && (
                <Button
                  type="button"
                  variant="secondary"
                  onClick={() => setDiscontinueOpen(true)}
                >
                  <Ban />
                  <span>{t("actions.discontinue")}</span>
                </Button>
              )}
              {canPublish && detail.status !== "DISCONTINUED" && (
                <Button
                  type="button"
                  disabled={isFormDirty}
                  aria-describedby={isFormDirty ? publishBlockedReasonId : undefined}
                  onClick={() => setPublishOpen(true)}
                >
                  <Upload />
                  <span>
                    {detail.status === "PUBLISHED"
                      ? t("actions.republish")
                      : t("actions.publish")}
                  </span>
                </Button>
              )}
            </div>
            {isFormDirty && canPublish && detail.status !== "DISCONTINUED" && (
              <p id={publishBlockedReasonId} className="text-caption font-medium text-warning">
                {t("actions.saveBeforePublish")}
              </p>
            )}
          </div>
        </header>
        <BundleStatusBadges
          status={detail.status}
          saleState={detail.saleState}
          hasUnpublishedChanges={detail.hasUnpublishedChanges}
        />
      </div>

      {hasConflict && (
        <div
          role="alert"
          className="flex flex-col gap-3 rounded-lg border border-warning bg-warning-bg p-4 text-warning sm:flex-row sm:items-center sm:justify-between"
        >
          <div className="flex items-start gap-3">
            <AlertTriangle aria-hidden className="mt-0.5 size-5 shrink-0" />
            <div className="flex flex-col gap-0.5">
              <p className="text-body font-semibold">
                {t("form.banners.conflictTitle")}
              </p>
              <p className="text-caption text-text">
                {t("form.banners.conflictDescription")}
              </p>
            </div>
          </div>
          <Button
            type="button"
            variant="secondary"
            size="sm"
            onClick={() => setHasConflict(false)}
          >
            {t("actions.dismiss")}
          </Button>
        </div>
      )}

      {query.isError && (
        <div
          role="alert"
          className="flex flex-col gap-3 rounded-lg border border-danger bg-danger-bg p-4 text-danger sm:flex-row sm:items-center sm:justify-between"
        >
          <div className="flex items-start gap-3">
            <CircleAlert aria-hidden className="mt-0.5 size-5 shrink-0" />
            <div className="flex flex-col gap-0.5">
              <p className="text-body font-semibold">{t("edit.errorTitle")}</p>
              <p className="text-caption text-text">
                {query.error
                  ? resolveBundleErrorMessage(query.error, tErrors)
                  : t("edit.errorDescription")}
              </p>
            </div>
          </div>
          <Button
            type="button"
            variant="secondary"
            size="sm"
            onClick={() => void query.refetch()}
          >
            {t("actions.retry")}
          </Button>
        </div>
      )}

      <BundleForm
        key={`${detail.revision}:${reloadSeq}`}
        mode="edit"
        formId={formId}
        initialDetail={detail}
        canModify={canModify}
        onDirtyChange={setIsFormDirty}
        onConflict={handleConflict}
        onSaved={() => {
          setHasConflict(false);
          setIsFormDirty(false);
          setReloadSeq((seq) => seq + 1);
        }}
      />

      <BundlePublishDialog
        bundle={actionTarget}
        open={publishOpen}
        onOpenChange={setPublishOpen}
        onConflict={handleConflict}
        onCompleted={() => setHasConflict(false)}
      />

      <BundleDiscontinueDialog
        bundle={actionTarget}
        open={discontinueOpen}
        onOpenChange={setDiscontinueOpen}
        onConflict={handleConflict}
        onCompleted={() => setHasConflict(false)}
      />
    </div>
  );
}
