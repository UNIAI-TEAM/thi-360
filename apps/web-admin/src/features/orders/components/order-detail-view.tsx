"use client";

import { AlertCircle, AlertTriangle, ArrowLeft, ExternalLink, RefreshCw } from "lucide-react";
import Link from "next/link";
import { useLocale, useTranslations } from "next-intl";
import { useMemo, useState } from "react";

import {
  Button,
  Card,
  EmptyState,
  PageHeader,
  Skeleton,
  Tooltip,
  focusRing,
} from "@thi360/ui";

import { usePermissions } from "@/features/session/session-provider";
import { useSetBreadcrumbLeaf } from "@/features/shell/breadcrumb-leaf";

import {
  formatStructuredOrderReason,
  resolveOrderAuditActionLabel,
} from "../audit-labels";
import { OrderApiError, resolveOrderErrorMessage } from "../errors";
import {
  formatAttemptSeq,
  formatNumber,
  formatOrderDateTime,
  formatVnd,
  formatVnpayPayDate,
} from "../format";
import { useAdminOrderDetailQuery } from "../queries";
import type {
  AdminOrderDetailResponse,
  PaymentDetailView,
} from "../types";
import { ContinueFulfillDialog } from "./continue-fulfill-dialog";
import { LateSuccessDialog } from "./late-success-dialog";
import {
  GrantStateChip,
  OrderPaymentStatusChip,
  OrderRefundStatusChip,
  OrderStateChip,
  PaymentAttemptStateChip,
  PaymentClassificationChip,
  RefundRowStateChip,
} from "./order-status-chips";
import { RefundDialog } from "./refund-dialog";

const KNOWN_REVIEW_REASONS = new Set([
  "FULFILLMENT_EXHAUSTED",
  "PAYMENT_UNKNOWN_OVER_24H",
  "REFUND_UNKNOWN",
  "LATE_SUCCESS_PENDING",
  "DUPLICATE_UNREFUNDED",
]);

interface TimelineEntry {
  id: string;
  at: string;
  source: "ORDER" | "AUDIT";
  actor: string;
  actionLabel: string;
  localizedReason: string | null;
  rawReason: string | null;
}

export function OrderDetailView({ code }: { code: string }) {
  const t = useTranslations("orders");
  const tErrors = useTranslations("orders.errors");
  const tAuditActions = useTranslations("orders.auditActions");
  const tHistoryReasons = useTranslations("orders.historyReasons");
  const tReviewReasons = useTranslations("orders.reviewReasons");
  const locale = useLocale();
  const permissions = usePermissions();

  useSetBreadcrumbLeaf(code);

  const detailQuery = useAdminOrderDetailQuery(code);

  const [fulfillOpen, setFulfillOpen] = useState(false);
  const [lateSuccessPaymentId, setLateSuccessPaymentId] = useState<string | null>(null);
  const [refundPaymentId, setRefundPaymentId] = useState<string | null>(null);

  const canRecover = !permissions.isLoading && permissions.can("ORDER.RECOVER");
  const canRefund = !permissions.isLoading && permissions.can("ORDER.REFUND");
  const canViewExam = !permissions.isLoading && permissions.can("EXAM.VIEW");

  const order = detailQuery.data;

  const activeLateSuccessPayment: PaymentDetailView | undefined = useMemo(() => {
    if (!order || !lateSuccessPaymentId) return undefined;
    return order.payments.find((p) => p.id === lateSuccessPaymentId);
  }, [order, lateSuccessPaymentId]);

  const activeRefundPayment: PaymentDetailView | undefined = useMemo(() => {
    if (!order || !refundPaymentId) return undefined;
    return order.payments.find((p) => p.id === refundPaymentId);
  }, [order, refundPaymentId]);

  const timelineEntries = useMemo<TimelineEntry[]>(() => {
    if (!order) return [];
    const items: TimelineEntry[] = [];

    for (const h of order.orderHistory) {
      const formatted = formatStructuredOrderReason(h.reason, tHistoryReasons);
      const fromLabel = h.fromState ? t(`states.${h.fromState}`) : "—";
      const toLabel = t(`states.${h.toState}`);
      items.push({
        id: `history-${h.id}`,
        at: h.at,
        source: "ORDER",
        actor: h.actor === "SYSTEM" ? t("detail.history.actorSystem") : h.actor,
        actionLabel: t("detail.history.stateTransition", { from: fromLabel, to: toLabel }),
        localizedReason: formatted.localized,
        rawReason: formatted.rawText,
      });
    }

    for (const a of order.auditEvents) {
      const formatted = formatStructuredOrderReason(a.reason, tHistoryReasons);
      const actor =
        a.actorUsername ||
        (a.actorType === "SYSTEM"
          ? t("detail.history.actorSystem")
          : t("detail.history.actorUnknown"));
      items.push({
        id: `audit-${a.id}`,
        at: a.occurredAt,
        source: "AUDIT",
        actor,
        actionLabel: resolveOrderAuditActionLabel(a.action, tAuditActions),
        localizedReason: formatted.localized,
        rawReason: formatted.rawText,
      });
    }

    items.sort((a, b) => Date.parse(b.at) - Date.parse(a.at));
    return items;
  }, [order, t, tAuditActions, tHistoryReasons]);

  const isOffline = detailQuery.fetchStatus === "paused" && detailQuery.isPending;

  if (isOffline) {
    return (
      <div className="flex flex-col gap-5">
        <PageHeader title={t("detail.title", { code })} />
        <EmptyState
          headingLevel={2}
          icon={<AlertCircle className="h-6 w-6" />}
          title={t("detail.offlineTitle")}
          description={t("detail.offlineDescription")}
          action={
            <Button type="button" variant="secondary" onClick={() => void detailQuery.refetch()}>
              {t("detail.retry")}
            </Button>
          }
        />
      </div>
    );
  }

  if (detailQuery.isPending) {
    return (
      <div className="flex flex-col gap-5" aria-busy="true" aria-label={t("detail.loading")}>
        <Skeleton className="h-10 w-72" />
        <Skeleton className="h-44 w-full" />
        <Skeleton className="h-44 w-full" />
        <Skeleton className="h-44 w-full" />
      </div>
    );
  }

  if (detailQuery.isError || !order) {
    const isNotFound =
      detailQuery.error instanceof OrderApiError && detailQuery.error.status === 404;
    return (
      <div className="flex flex-col gap-5">
        <PageHeader title={t("detail.title", { code })} />
        <EmptyState
          headingLevel={2}
          icon={<AlertCircle className="h-6 w-6" />}
          title={isNotFound ? t("detail.notFoundTitle") : t("detail.errorTitle")}
          description={
            isNotFound
              ? t("detail.notFoundDescription")
              : resolveOrderErrorMessage(detailQuery.error, tErrors)
          }
          action={
            <div className="flex flex-wrap gap-2">
              <Button asChild variant="secondary">
                <Link href="/orders">
                  <ArrowLeft aria-hidden="true" />
                  <span>{t("nav.backToOrders")}</span>
                </Link>
              </Button>
              {!isNotFound ? (
                <Button type="button" variant="outline" onClick={() => void detailQuery.refetch()}>
                  {t("detail.retry")}
                </Button>
              ) : null}
            </div>
          }
        />
      </div>
    );
  }

  const pendingLatePayments = order.payments.filter((p) => p.lateSuccessReview === "PENDING");
  const primaryRefundCandidate = order.payments.find(
    (p) =>
      p.state === "SUCCEEDED" &&
      (p.refunds.some((r) => r.state === "UNKNOWN" || r.state === "PROCESSING") ||
        p.refundableAmountVnd > 0),
  );
  const allRefunds = order.payments.flatMap((payment) =>
    payment.refunds.map((refund) => ({ refund, payment })),
  );

  const reviewReasonFormatted =
    order.reviewReason && KNOWN_REVIEW_REASONS.has(order.reviewReason)
      ? tReviewReasons(order.reviewReason)
      : order.reviewReason;

  return (
    <div className="flex flex-col gap-5">
      <div className="flex flex-col gap-2">
        <PageHeader
          title={t("detail.title", { code: order.code })}
          actions={
            <div className="flex flex-wrap items-center gap-2">
              <Button asChild variant="secondary" size="sm">
                <Link href="/orders">
                  <ArrowLeft aria-hidden="true" />
                  <span>{t("nav.backToOrders")}</span>
                </Link>
              </Button>

              <Button asChild variant="secondary" size="sm">
                <Link href={`/orders?q=${encodeURIComponent(order.buyer.username)}`}>
                  <span>{t("detail.actions.viewBuyerOrders")}</span>
                </Link>
              </Button>

              {canViewExam && order.examId ? (
                <Button asChild variant="outline" size="sm">
                  <Link href={`/exams/${encodeURIComponent(order.examId)}`}>
                    <span>{t("nav.openExam")}</span>
                    <ExternalLink aria-hidden="true" />
                  </Link>
                </Button>
              ) : null}

              {canRecover && order.state === "PAID" ? (
                <Button type="button" variant="primary" size="sm" onClick={() => setFulfillOpen(true)}>
                  {t("detail.actions.continueFulfill")}
                </Button>
              ) : null}

              {canRecover && pendingLatePayments.length > 0 ? (
                <Button
                  type="button"
                  variant="outline"
                  size="sm"
                  onClick={() => setLateSuccessPaymentId(pendingLatePayments[0]!.id)}
                >
                  {t("detail.actions.reviewLateSuccess")}
                </Button>
              ) : null}

              {canRefund && primaryRefundCandidate ? (
                <PaymentRefundActionButton
                  payment={primaryRefundCandidate}
                  onOpen={() => setRefundPaymentId(primaryRefundCandidate.id)}
                />
              ) : null}
            </div>
          }
        />
        <div className="flex flex-wrap items-center gap-2">
          <OrderStateChip state={order.state} />
          <OrderPaymentStatusChip status={order.paymentStatus} />
          <OrderRefundStatusChip status={order.refundStatus} />
        </div>
      </div>

      {order.replacedByOrderCode ? (
        <div
          role="status"
          className="flex flex-wrap items-center justify-between gap-2 rounded-lg border border-border bg-subtle px-4 py-3 text-sm text-text"
        >
          <span>{t("detail.banners.replacedByOrder", { code: order.replacedByOrderCode })}</span>
          <Link
            href={`/orders/${encodeURIComponent(order.replacedByOrderCode)}`}
            className={`font-mono text-sm font-semibold text-primary-text hover:underline ${focusRing}`}
          >
            {order.replacedByOrderCode}
          </Link>
        </div>
      ) : null}

      {order.state === "PAID" ? (
        <div
          role="alert"
          className="flex flex-wrap items-center justify-between gap-3 rounded-lg border border-warning/40 bg-warning-bg px-4 py-3 text-sm text-text"
        >
          <div className="flex flex-col gap-0.5">
            <span className="font-semibold">{t("detail.banners.paidUnfulfilledTitle")}</span>
            <span className="text-xs text-text-secondary">{t("detail.banners.paidUnfulfilledBody")}</span>
          </div>
          {canRecover ? (
            <Button type="button" variant="primary" size="sm" onClick={() => setFulfillOpen(true)}>
              {t("detail.actions.continueFulfill")}
            </Button>
          ) : null}
        </div>
      ) : null}

      {pendingLatePayments.length > 0 ? (
        <div
          role="alert"
          className="flex flex-wrap items-center justify-between gap-3 rounded-lg border border-warning/40 bg-warning-bg px-4 py-3 text-sm text-text"
        >
          <div className="flex flex-col gap-0.5">
            <span className="font-semibold">{t("detail.banners.lateSuccessPendingTitle")}</span>
            <span className="text-xs text-text-secondary">
              {t("detail.banners.lateSuccessPendingBody", { count: pendingLatePayments.length })}
            </span>
          </div>
          {canRecover ? (
            <Button
              type="button"
              variant="outline"
              size="sm"
              onClick={() => setLateSuccessPaymentId(pendingLatePayments[0]!.id)}
            >
              {t("detail.actions.reviewLateSuccess")}
            </Button>
          ) : null}
        </div>
      ) : null}

      {order.needsReview ? (
        <div
          role="alert"
          className="flex items-start gap-2.5 rounded-lg border border-warning/40 bg-warning-bg px-4 py-3 text-sm text-text"
        >
          <AlertTriangle className="mt-0.5 h-4 w-4 shrink-0 text-warning" aria-hidden="true" />
          <div className="flex flex-col gap-0.5">
            <span className="font-semibold">{t("detail.banners.needsReviewTitle")}</span>
            <span className="text-xs text-text-secondary" data-exam-content={!KNOWN_REVIEW_REASONS.has(order.reviewReason ?? "") || undefined}>
              {reviewReasonFormatted
                ? t("detail.banners.needsReviewBody", { reason: reviewReasonFormatted })
                : t("detail.banners.needsReviewTitle")}
            </span>
          </div>
        </div>
      ) : null}

      {/* Block 1: Snapshot */}
      <Card className="p-5">
        <div className="flex flex-col gap-4">
          <div className="flex items-center justify-between gap-2">
            <h2 className="text-base font-semibold text-text">{t("detail.snapshot.title")}</h2>
            <Button
              type="button"
              variant="secondary"
              size="sm"
              onClick={() => void detailQuery.refetch()}
            >
              <RefreshCw className="h-3.5 w-3.5" aria-hidden="true" />
              <span>{t("filters.refresh")}</span>
            </Button>
          </div>

          <dl className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
            <div>
              <dt className="text-xs text-text-secondary">{t("detail.snapshot.buyer")}</dt>
              <dd className="mt-1 text-sm font-medium text-text" data-exam-content>
                {order.buyer.fullName}
              </dd>
              <dd className="font-mono text-xs text-text-secondary">{order.buyer.username}</dd>
            </div>

            <div>
              <dt className="text-xs text-text-secondary">{t("detail.snapshot.product")}</dt>
              <dd className="mt-1 text-sm font-medium text-text" data-exam-content>
                {order.productName}
              </dd>
              <dd className="font-mono text-xs text-text-secondary">{order.productCode}</dd>
            </div>

            <div>
              <dt className="text-xs text-text-secondary">{t("detail.snapshot.attemptsGranted")}</dt>
              <dd className="mt-1 font-mono text-sm font-semibold text-text">
                {formatNumber(order.attemptsGranted, locale)}
              </dd>
              <dd className="text-xs text-text-secondary">
                {t("detail.snapshot.paymentMethod")}: {t(`paymentMethods.${order.paymentMethod}`)}
              </dd>
            </div>

            <div>
              <dt className="text-xs text-text-secondary">{t("detail.snapshot.pricing")}</dt>
              <dd className="mt-1 flex flex-col gap-0.5 text-xs text-text-secondary">
                <span>
                  {t("detail.snapshot.listPrice")}:{" "}
                  <span className="font-mono text-text">{formatVnd(order.listPriceVnd, locale)}</span>
                </span>
                {order.promotionDiscountVnd > 0 ? (
                  <span>
                    {t("detail.snapshot.promotionDiscount")}:{" "}
                    <span className="font-mono text-text">
                      -{formatVnd(order.promotionDiscountVnd, locale)}
                    </span>
                  </span>
                ) : null}
                {order.voucherDiscountVnd > 0 ? (
                  <span>
                    {t("detail.snapshot.voucherDiscount")}
                    {order.voucherCode ? ` (${order.voucherCode})` : ""}:{" "}
                    <span className="font-mono text-text">
                      -{formatVnd(order.voucherDiscountVnd, locale)}
                    </span>
                  </span>
                ) : null}
                <span className="font-semibold text-text">
                  {t("detail.snapshot.totalDue")}:{" "}
                  <span className="font-mono">{formatVnd(order.amountDueVnd, locale)}</span>
                </span>
              </dd>
            </div>

            <div>
              <dt className="text-xs text-text-secondary">{t("detail.snapshot.createdAt")}</dt>
              <dd className="mt-1 font-mono text-xs text-text">
                {formatOrderDateTime(order.createdAt, locale)}
              </dd>
              {order.expiresAt ? (
                <dd className="mt-0.5 text-xs text-text-secondary">
                  {t("detail.snapshot.expiresAt")}:{" "}
                  <span className="font-mono">{formatOrderDateTime(order.expiresAt, locale)}</span>
                </dd>
              ) : null}
            </div>

            <div>
              <dt className="text-xs text-text-secondary">{t("detail.snapshot.paidAt")}</dt>
              <dd className="mt-1 font-mono text-xs text-text">
                {order.paidAt ? formatOrderDateTime(order.paidAt, locale) : "—"}
              </dd>
              {order.fulfilledAt ? (
                <dd className="mt-0.5 text-xs text-text-secondary">
                  {t("detail.snapshot.fulfilledAt")}:{" "}
                  <span className="font-mono">{formatOrderDateTime(order.fulfilledAt, locale)}</span>
                </dd>
              ) : null}
            </div>
          </dl>

          {order.fulfillmentError ? (
            <div className="rounded-lg border border-danger/30 bg-danger-bg px-3 py-2 text-xs text-danger">
              <span className="font-semibold">{t("detail.snapshot.fulfillmentError")}: </span>
              <span data-exam-content>{order.fulfillmentError}</span>
              {" · "}
              <span>
                {t("detail.snapshot.fulfillmentTries")}: {order.fulfillmentTries}
              </span>
            </div>
          ) : null}
        </div>
      </Card>

      {/* Block 2: Grants */}
      <Card className="p-5">
        <h2 className="text-base font-semibold text-text">{t("detail.grants.title")}</h2>
        {order.grants.length === 0 ? (
          <p className="mt-3 text-sm text-text-secondary">{t("detail.grants.empty")}</p>
        ) : (
          <div className="mt-3 overflow-x-auto">
            <table className="w-full border-collapse text-left text-sm">
              <thead>
                <tr className="border-b border-border text-xs text-text-secondary">
                  <th className="py-2 pr-4 font-semibold">{t("detail.grants.code")}</th>
                  <th className="py-2 pr-4 font-semibold">{t("detail.grants.remaining")}</th>
                  <th className="py-2 pr-4 font-semibold">{t("detail.grants.status")}</th>
                  <th className="py-2 font-semibold">{t("detail.grants.createdAt")}</th>
                </tr>
              </thead>
              <tbody>
                {order.grants.map((grant) => (
                  <tr key={grant.grantId} className="border-b border-border last:border-0">
                    <td className="py-2.5 pr-4 font-mono text-xs text-text">
                      {grant.entitlementId}
                    </td>
                    <td className="py-2.5 pr-4 font-mono text-xs text-text">
                      {grant.attemptsLeft} / {grant.attempts}
                    </td>
                    <td className="py-2.5 pr-4">
                      <GrantStateChip grant={grant} />
                    </td>
                    <td className="py-2.5 font-mono text-xs text-text-secondary">
                      {formatOrderDateTime(grant.grantedAt, locale)}
                      {grant.revokedAt
                        ? ` (${t("detail.grants.revokedAt")}: ${formatOrderDateTime(grant.revokedAt, locale)})`
                        : ""}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </Card>

      {/* Block 3A: Payments */}
      <Card className="p-5">
        <h2 className="text-base font-semibold text-text">{t("detail.payments.title")}</h2>
        {order.payments.length === 0 ? (
          <p className="mt-3 text-sm text-text-secondary">{t("detail.payments.empty")}</p>
        ) : (
          <div className="mt-3 overflow-x-auto">
            <table className="w-full border-collapse text-left text-sm">
              <thead>
                <tr className="border-b border-border text-xs text-text-secondary">
                  <th className="py-2 pr-3 font-semibold">{t("detail.payments.txnRef")}</th>
                  <th className="py-2 pr-3 font-semibold">{t("detail.payments.amount")}</th>
                  <th className="py-2 pr-3 font-semibold">{t("detail.payments.status")}</th>
                  <th className="py-2 pr-3 font-semibold">{t("detail.payments.classification")}</th>
                  <th className="py-2 pr-3 font-semibold">{t("detail.payments.payDate")}</th>
                  <th className="py-2 pr-3 font-semibold">{t("detail.payments.lateSuccessResolution")}</th>
                  <th className="py-2 font-semibold text-right">{t("detail.payments.action")}</th>
                </tr>
              </thead>
              <tbody>
                {order.payments.map((payment) => (
                  <tr key={payment.id} className="border-b border-border last:border-0 align-top">
                    <td className="py-2.5 pr-3">
                      <div className="font-mono text-xs font-semibold text-text">
                        #{formatAttemptSeq(payment.attemptSeq)} · {payment.txnRef}
                      </div>
                      {payment.vnpTransactionNo ? (
                        <div className="font-mono text-[11px] text-text-secondary">
                          {t("detail.payments.vnpayTxnNo")}: {payment.vnpTransactionNo}
                        </div>
                      ) : null}
                      {payment.bankCode ? (
                        <div className="text-[11px] text-text-secondary">{payment.bankCode}</div>
                      ) : null}
                    </td>
                    <td className="py-2.5 pr-3 font-mono text-xs font-semibold text-text whitespace-nowrap">
                      {formatVnd(payment.amountVnd, locale)}
                    </td>
                    <td className="py-2.5 pr-3 whitespace-nowrap">
                      <PaymentAttemptStateChip
                        state={payment.state}
                        lateSuccessAt={payment.lateSuccessAt}
                      />
                    </td>
                    <td className="py-2.5 pr-3">
                      <PaymentClassificationChip payment={payment} />
                    </td>
                    <td className="py-2.5 pr-3 font-mono text-xs text-text-secondary whitespace-nowrap">
                      {payment.payDate ? formatVnpayPayDate(payment.payDate, locale) : "—"}
                    </td>
                    <td className="py-2.5 pr-3 text-xs text-text-secondary">
                      {payment.lateSuccessReview
                        ? t(`lateSuccessResolutions.${payment.lateSuccessReview}`)
                        : "—"}
                    </td>
                    <td className="py-2.5 text-right whitespace-nowrap">
                      <div className="flex justify-end gap-2">
                        {canRecover && payment.lateSuccessReview === "PENDING" ? (
                          <Button
                            type="button"
                            variant="outline"
                            size="sm"
                            onClick={() => setLateSuccessPaymentId(payment.id)}
                          >
                            {t("detail.actions.reviewLateSuccess")}
                          </Button>
                        ) : null}
                        {canRefund && payment.state === "SUCCEEDED" ? (
                          <PaymentRefundActionButton
                            payment={payment}
                            onOpen={() => setRefundPaymentId(payment.id)}
                          />
                        ) : null}
                      </div>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </Card>

      {/* Block 3B: Refund History */}
      <Card className="p-5">
        <h2 className="text-base font-semibold text-text">{t("detail.refunds.title")}</h2>
        {allRefunds.length === 0 ? (
          <p className="mt-3 text-sm text-text-secondary">{t("detail.refunds.empty")}</p>
        ) : (
          <div className="mt-3 overflow-x-auto">
            <table className="w-full border-collapse text-left text-sm">
              <thead>
                <tr className="border-b border-border text-xs text-text-secondary">
                  <th className="py-2 pr-3 font-semibold">{t("detail.refunds.paymentRef")}</th>
                  <th className="py-2 pr-3 font-semibold">{t("detail.refunds.kind")}</th>
                  <th className="py-2 pr-3 font-semibold">{t("detail.refunds.amount")}</th>
                  <th className="py-2 pr-3 font-semibold">{t("detail.refunds.status")}</th>
                  <th className="py-2 pr-3 font-semibold">{t("detail.refunds.reason")}</th>
                  <th className="py-2 pr-3 font-semibold">{t("detail.refunds.createdAt")}</th>
                  <th className="py-2 font-semibold text-right">{t("detail.refunds.action")}</th>
                </tr>
              </thead>
              <tbody>
                {allRefunds.map(({ refund, payment }) => (
                  <tr key={refund.id} className="border-b border-border last:border-0 align-top">
                    <td className="py-2.5 pr-3 font-mono text-xs text-text">
                      <div>{refund.paymentTxnRef}</div>
                      {refund.externalRef ? (
                        <div className="text-[11px] text-text-secondary">{refund.externalRef}</div>
                      ) : null}
                    </td>
                    <td className="py-2.5 pr-3 text-xs text-text-secondary whitespace-nowrap">
                      {t(`refundKinds.${refund.kind}`)}
                    </td>
                    <td className="py-2.5 pr-3 font-mono text-xs font-semibold text-text whitespace-nowrap">
                      {formatVnd(refund.amountVnd, locale)}
                    </td>
                    <td className="py-2.5 pr-3 whitespace-nowrap">
                      <RefundRowStateChip state={refund.state} />
                    </td>
                    <td className="py-2.5 pr-3 text-xs text-text">
                      <p data-exam-content>{refund.reason}</p>
                      {refund.gatewayMessage ? (
                        <p className="mt-0.5 text-danger" data-exam-content>
                          {refund.gatewayMessage}
                        </p>
                      ) : null}
                      {refund.markFailedReason ? (
                        <p className="mt-0.5 text-text-secondary" data-exam-content>
                          {refund.markFailedReason}
                        </p>
                      ) : null}
                    </td>
                    <td className="py-2.5 pr-3 font-mono text-xs text-text-secondary whitespace-nowrap">
                      {formatOrderDateTime(refund.requestedAt, locale)}
                    </td>
                    <td className="py-2.5 text-right whitespace-nowrap">
                      {canRefund && refund.state === "UNKNOWN" ? (
                        <Button
                          type="button"
                          variant="outline"
                          size="sm"
                          onClick={() => setRefundPaymentId(payment.id)}
                        >
                          {t("detail.actions.queryRefund")}
                        </Button>
                      ) : null}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </Card>

      {/* Block 4: Order History & Audit Log */}
      <Card className="p-5">
        <h2 className="text-base font-semibold text-text">{t("detail.history.title")}</h2>
        {timelineEntries.length === 0 ? (
          <p className="mt-3 text-sm text-text-secondary">{t("detail.history.empty")}</p>
        ) : (
          <div className="mt-3 overflow-x-auto">
            <table className="w-full border-collapse text-left text-sm">
              <thead>
                <tr className="border-b border-border text-xs text-text-secondary">
                  <th className="py-2 pr-3 font-semibold">{t("detail.history.colTime")}</th>
                  <th className="py-2 pr-3 font-semibold">{t("detail.history.colType")}</th>
                  <th className="py-2 pr-3 font-semibold">{t("detail.history.colActor")}</th>
                  <th className="py-2 font-semibold">{t("detail.history.colDetail")}</th>
                </tr>
              </thead>
              <tbody>
                {timelineEntries.map((entry) => (
                  <tr key={entry.id} className="border-b border-border last:border-0 align-top">
                    <td className="py-2.5 pr-3 font-mono text-xs text-text-secondary whitespace-nowrap">
                      {formatOrderDateTime(entry.at, locale)}
                    </td>
                    <td className="py-2.5 pr-3 text-xs text-text-secondary whitespace-nowrap">
                      {entry.source === "ORDER"
                        ? t("detail.history.sourceOrder")
                        : t("detail.history.sourceAudit")}
                    </td>
                    <td className="py-2.5 pr-3 font-mono text-xs text-text">{entry.actor}</td>
                    <td className="py-2.5 text-xs text-text">
                      <p className="font-medium">{entry.actionLabel}</p>
                      {entry.localizedReason ? (
                        <p className="mt-0.5 text-text-secondary">{entry.localizedReason}</p>
                      ) : null}
                      {entry.rawReason ? (
                        <p className="mt-0.5 text-text-secondary" data-exam-content>
                          {entry.rawReason}
                        </p>
                      ) : null}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </Card>

      {canRecover ? (
        <ContinueFulfillDialog
          open={fulfillOpen}
          onOpenChange={setFulfillOpen}
          order={order}
        />
      ) : null}

      {canRecover && activeLateSuccessPayment ? (
        <LateSuccessDialog
          open={Boolean(activeLateSuccessPayment)}
          onOpenChange={(next) => {
            if (!next) setLateSuccessPaymentId(null);
          }}
          order={order}
          payment={activeLateSuccessPayment}
        />
      ) : null}

      {canRefund && activeRefundPayment ? (
        <RefundDialog
          open={Boolean(activeRefundPayment)}
          onOpenChange={(next) => {
            if (!next) setRefundPaymentId(null);
          }}
          order={order}
          payment={activeRefundPayment}
        />
      ) : null}
    </div>
  );
}

function PaymentRefundActionButton({
  payment,
  onOpen,
}: {
  payment: PaymentDetailView;
  onOpen: () => void;
}) {
  const t = useTranslations("orders.detail.actions");
  const hasUnknown = payment.refunds.some((r) => r.state === "UNKNOWN");
  const hasProcessing = payment.refunds.some((r) => r.state === "PROCESSING");

  if (hasUnknown) {
    return (
      <Button type="button" variant="outline" size="sm" onClick={onOpen}>
        {t("refund")}
      </Button>
    );
  }

  if (hasProcessing) {
    return (
      <Tooltip content={t("refundPendingTooltip")}>
        <span>
          <Button type="button" variant="outline" size="sm" disabled>
            {t("refund")}
          </Button>
        </span>
      </Tooltip>
    );
  }

  if (payment.refundableAmountVnd <= 0) {
    return null;
  }

  return (
    <Button type="button" variant="outline" size="sm" onClick={onOpen}>
      {t("refund")}
    </Button>
  );
}

// Keep unused type export silenced if any
export type { AdminOrderDetailResponse };
