"use client";

import { Button, EmptyState, PageHeader, Skeleton } from "@thi360/ui";
import { AlertTriangle, ArrowLeft, WifiOff } from "lucide-react";
import { useTranslations } from "next-intl";
import Link from "next/link";
import { useSearchParams } from "next/navigation";
import { useState } from "react";

import { useSetBreadcrumbLeaf } from "@/features/shell/breadcrumb-leaf";
import { ApiStatusError } from "@/lib/api/query-client";

import { promotionErrorCode, resolvePromotionErrorMessage } from "../errors";
import { usePromotionDetailQuery } from "../queries";
import { PromotionForm } from "./promotion-form";

export function PromotionCreatePage() {
  const t = useTranslations("promotions");
  const searchParams = useSearchParams();
  const initialKind = searchParams.get("kind") === "VOUCHER" ? "VOUCHER" : "AUTO";

  useSetBreadcrumbLeaf(t("create.title"));

  return (
    <div className="flex flex-col gap-5">
      <PageHeader
        title={t("create.title")}
        description={t("create.description")}
        actions={
          <Button variant="secondary" asChild>
            <Link href="/promotions">
              <ArrowLeft />
              <span>{t("actions.backToList")}</span>
            </Link>
          </Button>
        }
      />

      <PromotionForm mode="create" initialKind={initialKind} />
    </div>
  );
}

export function PromotionEditPage({ promotionId }: { promotionId: string }) {
  const t = useTranslations("promotions");
  const tErrors = useTranslations("promotions.errors");
  const query = usePromotionDetailQuery(promotionId);
  const [conflictReason, setConflictReason] = useState<"version" | "in_use" | null>(null);
  const [reloadSeq, setReloadSeq] = useState(0);

  useSetBreadcrumbLeaf(query.data ? query.data.name : t("edit.title"));

  const handleReloadLatest = async () => {
    setConflictReason(null);
    await query.refetch();
    setReloadSeq((seq) => seq + 1);
  };

  const handleConflict = async (reason: "version" | "in_use") => {
    setConflictReason(reason);
    if (reason === "in_use") {
      await query.refetch();
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

  if (query.isError || !query.data) {
    const isOffline = query.fetchStatus === "paused" && !query.isError;
    const code = promotionErrorCode(query.error);
    const notFound =
      code === "PROMOTION_NOT_FOUND" ||
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
              ? t("detail.notFoundTitle")
              : t("edit.errorTitle")
        }
        description={
          isOffline
            ? t("list.offlineDescription")
            : notFound
              ? t("detail.notFoundDescription")
              : query.error
                ? resolvePromotionErrorMessage(query.error, tErrors)
                : t("edit.errorDescription")
        }
        action={
          notFound ? (
            <Button variant="secondary" asChild>
              <Link href="/promotions">{t("actions.backToList")}</Link>
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
  const conflictBanner = conflictReason ? (
    <div
      role="alert"
      className="flex flex-col gap-3 rounded-lg border border-danger/40 bg-danger-bg p-4 text-danger @xl:flex-row @xl:items-center @xl:justify-between"
    >
      <div className="flex items-start gap-3">
        <AlertTriangle aria-hidden className="mt-0.5 size-5 shrink-0" />
        <div className="flex flex-col gap-0.5">
          <p className="text-body font-semibold">
            {conflictReason === "in_use"
              ? t("form.banner.inUseConflictTitle")
              : t("form.banner.conflictTitle")}
          </p>
          <p className="text-caption">
            {conflictReason === "in_use"
              ? t("form.banner.inUseConflictDescription")
              : t("form.banner.conflictDescription")}
          </p>
        </div>
      </div>
      <Button
        type="button"
        variant="secondary"
        onClick={() => void handleReloadLatest()}
        className="shrink-0"
      >
        {t("actions.reloadLatest")}
      </Button>
    </div>
  ) : null;

  return (
    <div className="@container flex flex-col gap-5">
      <PageHeader
        title={t("edit.title")}
        description={t("edit.description")}
        actions={
          <Button variant="secondary" asChild>
            <Link href={`/promotions/${detail.id}`}>
              <ArrowLeft />
              <span>{t("actions.backToDetail")}</span>
            </Link>
          </Button>
        }
      />

      <PromotionForm
        key={`${detail.revision}:${detail.usedCount}:${reloadSeq}`}
        mode="edit"
        initialDetail={detail}
        onConflict={handleConflict}
        conflictBanner={conflictBanner}
      />
    </div>
  );
}
