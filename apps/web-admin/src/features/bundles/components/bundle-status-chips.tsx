"use client";

import { StatusChip, type StatusTone } from "@thi360/ui";
import { useTranslations } from "next-intl";

import type { BundleSaleState, BundleStatus } from "../types";

const STATUS_TONE: Record<BundleStatus, StatusTone> = {
  DRAFT: "neutral",
  PUBLISHED: "success",
  DISCONTINUED: "neutral",
};

export function BundleStatusBadges({
  status,
  saleState,
  hasUnpublishedChanges,
}: {
  status: BundleStatus;
  saleState?: BundleSaleState | null;
  hasUnpublishedChanges: boolean;
}) {
  const t = useTranslations("bundles.states");

  return (
    <div className="flex flex-wrap items-center gap-1.5">
      <StatusChip tone={STATUS_TONE[status]}>{t(status)}</StatusChip>
      {status === "PUBLISHED" && saleState === "UNAVAILABLE" && (
        <StatusChip tone="danger">{t("UNAVAILABLE")}</StatusChip>
      )}
      {status === "PUBLISHED" && hasUnpublishedChanges && (
        <StatusChip tone="warning">{t("UNPUBLISHED_CHANGES")}</StatusChip>
      )}
    </div>
  );
}

export function BundleItemSaleChip({ saleState }: { saleState: string }) {
  const t = useTranslations("bundles.itemSaleStates");
  if (saleState === "ON_SALE") return null;
  const key = saleState === "DISCONTINUED" || saleState === "UNAVAILABLE" ? saleState : "UNAVAILABLE";
  return <StatusChip tone="danger">{t(key)}</StatusChip>;
}
