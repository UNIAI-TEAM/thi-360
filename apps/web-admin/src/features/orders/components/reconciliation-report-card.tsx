"use client";

import { AlertCircle, CheckCircle2, RefreshCw } from "lucide-react";
import { useLocale, useTranslations } from "next-intl";

import { Button, Card, Skeleton, cn } from "@thi360/ui";

import { formatNumber, formatOrderDate, formatOrderDateTime } from "../format";
import { useLatestReconciliationReportQuery } from "../queries";

export function ReconciliationReportCard() {
  const t = useTranslations("orders.reconciliation");
  const locale = useLocale();
  const query = useLatestReconciliationReportQuery();

  if (query.fetchStatus === "paused" && query.isPending) {
    return (
      <Card className="p-4">
        <div
          role="alert"
          className="flex flex-wrap items-center justify-between gap-3 rounded-lg border border-danger/30 bg-danger-bg p-3 text-sm text-danger"
        >
          <div className="flex items-center gap-2">
            <AlertCircle className="h-4 w-4 shrink-0" aria-hidden="true" />
            <span>{t("offlineError")}</span>
          </div>
          <Button
            type="button"
            variant="outline"
            size="sm"
            onClick={() => void query.refetch()}
          >
            <RefreshCw className="mr-1.5 h-3.5 w-3.5" aria-hidden="true" />
            {t("retry")}
          </Button>
        </div>
      </Card>
    );
  }

  if (query.isPending) {
    return (
      <Card className="p-4" aria-label={t("title")}>
        <div className="flex flex-col gap-3">
          <div className="flex flex-wrap items-center justify-between gap-2">
            <Skeleton className="h-5 w-56" />
            <Skeleton className="h-4 w-44" />
          </div>
          <div className="grid grid-cols-2 gap-3 sm:grid-cols-4">
            <Skeleton className="h-16 w-full" />
            <Skeleton className="h-16 w-full" />
            <Skeleton className="h-16 w-full" />
            <Skeleton className="h-16 w-full" />
          </div>
        </div>
      </Card>
    );
  }

  if (query.isError) {
    return (
      <Card className="p-4">
        <div
          role="alert"
          className="flex flex-wrap items-center justify-between gap-3 rounded-lg border border-danger/30 bg-danger-bg p-3 text-sm text-danger"
        >
          <div className="flex items-center gap-2">
            <AlertCircle className="h-4 w-4 shrink-0" aria-hidden="true" />
            <span>{t("unavailableError")}</span>
          </div>
          <Button
            type="button"
            variant="outline"
            size="sm"
            onClick={() => void query.refetch()}
          >
            <RefreshCw className="mr-1.5 h-3.5 w-3.5" aria-hidden="true" />
            {t("retry")}
          </Button>
        </div>
      </Card>
    );
  }

  const report = query.data?.available ? query.data.report : undefined;

  if (!report) {
    return (
      <Card className="p-4">
        <div className="flex flex-col gap-1">
          <div className="flex items-center gap-2">
            <h2 className="text-sm font-semibold text-text">{t("title")}</h2>
          </div>
          <p className="text-sm text-text-secondary">{t("notRunYet")}</p>
          <p className="text-xs text-text-muted">{t("triggerHint")}</p>
        </div>
      </Card>
    );
  }

  const counters = [
    {
      key: "paidUnfulfilled",
      label: t("paidUnfulfilled"),
      value: report.paidUnfulfilledCount,
    },
    {
      key: "unknownPayment",
      label: t("unknownPayment"),
      value: report.unknownPaymentCount,
    },
    {
      key: "unrefundedExcess",
      label: t("unrefundedExcess"),
      value: report.unrefundedExcessCount,
    },
    {
      key: "pendingLateSuccess",
      label: t("pendingLateSuccess"),
      value: report.pendingLateSuccessCount,
    },
  ];

  const allZero = counters.every((item) => item.value === 0);

  return (
    <Card className="p-4" aria-label={t("title")}>
      <div className="flex flex-col gap-3">
        <div className="flex flex-wrap items-baseline justify-between gap-2">
          <div className="flex items-center gap-2">
            {allZero ? (
              <CheckCircle2 className="h-4 w-4 shrink-0 text-success" aria-hidden="true" />
            ) : (
              <AlertCircle className="h-4 w-4 shrink-0 text-warning" aria-hidden="true" />
            )}
            <h2 className="text-sm font-semibold text-text">{t("title")}</h2>
          </div>
          <div className="flex flex-wrap items-center gap-x-3 gap-y-1 text-xs text-text-secondary">
            <span>{t("latestDate", { date: formatOrderDate(report.reportDate, locale) })}</span>
            <span>{t("generatedAt", { time: formatOrderDateTime(report.generatedAt, locale) })}</span>
          </div>
        </div>

        <dl className="grid grid-cols-2 gap-3 sm:grid-cols-4">
          {counters.map((counter) => (
            <div
              key={counter.key}
              className={cn(
                "rounded-lg border px-3 py-2.5",
                counter.value > 0
                  ? "border-warning/30 bg-warning-bg"
                  : "border-border bg-subtle",
              )}
            >
              <dt className="text-xs text-text-secondary">{counter.label}</dt>
              <dd
                className={
                  counter.value > 0
                    ? "mt-1 font-mono text-lg font-semibold text-warning"
                    : "mt-1 font-mono text-lg font-semibold text-text"
                }
              >
                {formatNumber(counter.value, locale)}
              </dd>
            </div>
          ))}
        </dl>
      </div>
    </Card>
  );
}
