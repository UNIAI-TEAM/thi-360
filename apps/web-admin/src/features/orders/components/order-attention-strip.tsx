"use client";

import { AlertTriangle, CheckCircle2, X } from "lucide-react";
import { useLocale, useTranslations } from "next-intl";

import { Button, Card, cn, focusRing, Skeleton } from "@thi360/ui";

import { formatNumber } from "../format";
import type { OrderAttentionSummary, OrderAttentionType } from "../types";

export const ATTENTION_BUCKET_SPECS: ReadonlyArray<{
  kind: OrderAttentionType;
  field: keyof Omit<OrderAttentionSummary, "totalAttentionOrders">;
}> = [
  { kind: "LATE_SUCCESS_PENDING", field: "lateSuccessPending" },
  { kind: "PAID_UNFULFILLED", field: "paidUnfulfilled" },
  { kind: "PAYMENT_UNKNOWN_OVER_24H", field: "paymentUnknownOver24h" },
  { kind: "DUPLICATE_UNREFUNDED", field: "duplicateUnrefunded" },
  { kind: "NEEDS_REVIEW", field: "needsReview" },
  { kind: "REFUND_UNKNOWN", field: "refundUnknown" },
];

export interface OrderAttentionStripProps {
  summary?: OrderAttentionSummary;
  isLoading: boolean;
  isError: boolean;
  activeAttention: "" | OrderAttentionType;
  onSelectAttention: (next: "" | OrderAttentionType) => void;
}

export function OrderAttentionStrip({
  summary,
  isLoading,
  isError,
  activeAttention,
  onSelectAttention,
}: OrderAttentionStripProps) {
  const t = useTranslations("orders.attention");
  const locale = useLocale();

  if (isError && !summary) {
    return null;
  }

  if (isLoading && !summary) {
    return (
      <Card className="p-4" aria-label={t("title")}>
        <div className="flex flex-col gap-3">
          <Skeleton className="h-5 w-40" />
          <div className="flex flex-wrap gap-2">
            {ATTENTION_BUCKET_SPECS.map((spec) => (
              <Skeleton key={spec.kind} className="h-9 w-44 rounded-lg" />
            ))}
          </div>
        </div>
      </Card>
    );
  }

  if (!summary) {
    return null;
  }

  if (summary.totalAttentionOrders === 0 && !activeAttention) {
    return (
      <Card className="p-4">
        <div className="flex items-center gap-2.5 text-sm text-text-secondary">
          <CheckCircle2 className="h-4 w-4 shrink-0 text-success" aria-hidden="true" />
          <span>{t("emptyCalm")}</span>
        </div>
      </Card>
    );
  }

  return (
    <Card className="p-4">
      <div className="flex flex-col gap-3">
        <div className="flex flex-wrap items-center justify-between gap-2">
          <div className="flex items-center gap-2">
            <AlertTriangle className="h-4 w-4 shrink-0 text-warning" aria-hidden="true" />
            <h2 className="text-sm font-semibold text-text">{t("title")}</h2>
          </div>
          {activeAttention ? (
            <Button
              type="button"
              variant="secondary"
              size="sm"
              onClick={() => onSelectAttention("")}
            >
              <X className="mr-1 h-3.5 w-3.5" aria-hidden="true" />
              {t("clearFilter")}
            </Button>
          ) : null}
        </div>

        <div
          role="group"
          aria-label={t("title")}
          className="flex flex-wrap gap-2"
        >
          {ATTENTION_BUCKET_SPECS.map(({ kind, field }) => {
            const count = summary[field];
            const isSelected = activeAttention === kind;
            return (
              <button
                key={kind}
                type="button"
                aria-pressed={isSelected}
                onClick={() => onSelectAttention(isSelected ? "" : kind)}
                className={cn(
                  "inline-flex items-center gap-2 rounded-lg border px-3 py-1.5 text-sm font-medium transition-colors",
                  focusRing,
                  isSelected
                    ? "border-primary-600 bg-surface text-text"
                    : count > 0
                      ? "border-warning/40 bg-warning-bg text-text hover:border-warning"
                      : "border-border bg-subtle text-text-secondary hover:bg-surface",
                )}
              >
                <span>{t(`kinds.${kind}`)}</span>
                <span
                  className={cn(
                    "inline-flex min-w-6 items-center justify-center rounded-full px-1.5 py-0.5 font-mono text-xs font-semibold",
                    isSelected
                      ? "bg-primary-fill text-on-primary"
                      : count > 0
                        ? "bg-surface text-warning"
                        : "bg-surface text-text-secondary",
                  )}
                >
                  {formatNumber(count, locale)}
                </span>
              </button>
            );
          })}
        </div>
      </div>
    </Card>
  );
}
