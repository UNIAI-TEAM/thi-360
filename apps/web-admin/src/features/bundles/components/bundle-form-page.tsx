"use client";

import { Button, DetailHeader, EmptyState, Skeleton } from "@thi360/ui";
import { AlertTriangle, Ban, CircleAlert, Upload, WifiOff } from "lucide-react";
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
  const tCommon = useTranslations("common");
  const { can } = usePermissions();
  const formId = useId();
  useSetBreadcrumbLeaf(t("create.title"));

  return (
    <div className="flex flex-col gap-5">
      <DetailHeader
        back={{ href: "/bundles", label: tCommon("backTo.bundles") }}
        linkComponent={Link}
        title={t("create.title")}
        description={t("create.description")}
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
  const tCommon = useTranslations("common");
  const tErrors = useTranslations("bundles.errors");
  const { can } = usePermissions();
  const canModify = can("EXAM.MODIFY");
  const canPublish = can("EXAM.PUBLISH");

  const query = useBundleDetailQuery(bundleId);
  const [conflictState, setConflictState] = useState<"none" | "reloaded" | "refetch_failed">("none");
  const [conflictFailedRevision, setConflictFailedRevision] = useState<number | null>(null);
  const [reloadSeq, setReloadSeq] = useState(0);
  const [isFormDirty, setIsFormDirty] = useState(false);
  const [publishOpen, setPublishOpen] = useState(false);
  const [discontinueOpen, setDiscontinueOpen] = useState(false);
  const formId = useId();
  const publishBlockedReasonId = useId();

  useSetBreadcrumbLeaf(query.data ? query.data.code : t("edit.title"));

  // Recover cleanly if a background refetch (e.g. reconnect or cache invalidation) updates revision while failed banner is shown.
  if (
    conflictState === "refetch_failed" &&
    !query.isError &&
    query.data &&
    conflictFailedRevision !== null &&
    query.data.revision !== conflictFailedRevision
  ) {
    setConflictState("reloaded");
    setConflictFailedRevision(null);
    setIsFormDirty(false);
  }

  const handleConflict = async (options?: { throwOnError?: boolean }) => {
    const refetchResult = await query.refetch({
      cancelRefetch: !options?.throwOnError,
    });
    if (!refetchResult.isError) {
      setIsFormDirty(false);
      setReloadSeq((seq) => seq + 1);
      setConflictFailedRevision(null);
      setConflictState("reloaded");
      if (refetchResult.data?.status === "DISCONTINUED") {
        setPublishOpen(false);
        setDiscontinueOpen(false);
      }
      return;
    }
    setConflictFailedRevision(query.data?.revision ?? null);
    setConflictState("refetch_failed");
    if (options?.throwOnError) {
      throw refetchResult.error ?? new Error("refetch_failed");
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
            <Button
              variant="secondary"
              loading={query.isRefetching}
              onClick={() => void query.refetch()}
            >
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
      <DetailHeader
        back={{ href: "/bundles", label: tCommon("backTo.bundles") }}
        linkComponent={Link}
        title={<span data-exam-content>{detail.name}</span>}
        chips={
          <BundleStatusBadges
            status={detail.status}
            saleState={detail.saleState}
            hasUnpublishedChanges={detail.hasUnpublishedChanges}
          />
        }
        description={
          <span data-exam-content className="font-mono">
            {detail.code}
          </span>
        }
        actions={
          canPublish &&
          detail.status !== "DISCONTINUED" && (
            <>
              {detail.status === "PUBLISHED" && (
                <Button
                  type="button"
                  variant="secondary"
                  onClick={() => setDiscontinueOpen(true)}
                >
                  <Ban />
                  <span>{t("actions.discontinue")}</span>
                </Button>
              )}
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
            </>
          )
        }
      >
        {isFormDirty && canPublish && detail.status !== "DISCONTINUED" && (
          <p id={publishBlockedReasonId} className="text-caption font-medium text-warning">
            {t("actions.saveBeforePublish")}
          </p>
        )}
      </DetailHeader>

      {conflictState === "reloaded" && (
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
            onClick={() => {
              setConflictState("none");
              setConflictFailedRevision(null);
            }}
          >
            {t("actions.dismiss")}
          </Button>
        </div>
      )}

      {conflictState === "refetch_failed" && (
        <div
          role="alert"
          className="flex flex-col gap-3 rounded-lg border border-danger bg-danger-bg p-4 text-danger sm:flex-row sm:items-center sm:justify-between"
        >
          <div className="flex items-start gap-3">
            <CircleAlert aria-hidden className="mt-0.5 size-5 shrink-0" />
            <div className="flex flex-col gap-0.5">
              <p className="text-body font-semibold">
                {t("form.banners.conflictRefetchFailedTitle")}
              </p>
              <p className="text-caption text-text">
                {t("form.banners.conflictRefetchFailedDescription", {
                  reason: query.error
                    ? resolveBundleErrorMessage(query.error, tErrors)
                    : t("edit.errorDescription"),
                })}
              </p>
            </div>
          </div>
          <Button
            type="button"
            variant="secondary"
            size="sm"
            loading={query.isRefetching}
            onClick={() => void handleConflict()}
          >
            {t("actions.retry")}
          </Button>
        </div>
      )}

      {conflictState !== "refetch_failed" && query.isError && (
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
            loading={query.isRefetching}
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
        onConflict={() => handleConflict()}
        onSaved={() => {
          setConflictState("none");
          setConflictFailedRevision(null);
          setIsFormDirty(false);
          setReloadSeq((seq) => seq + 1);
        }}
      />

      <BundlePublishDialog
        bundle={actionTarget}
        open={publishOpen}
        onOpenChange={setPublishOpen}
        onConflict={() => handleConflict({ throwOnError: true })}
        onCompleted={() => {
          setConflictState("none");
          setConflictFailedRevision(null);
        }}
      />

      <BundleDiscontinueDialog
        bundle={actionTarget}
        open={discontinueOpen}
        onOpenChange={setDiscontinueOpen}
        onConflict={() => handleConflict({ throwOnError: true })}
        onCompleted={() => {
          setConflictState("none");
          setConflictFailedRevision(null);
        }}
      />
    </div>
  );
}
