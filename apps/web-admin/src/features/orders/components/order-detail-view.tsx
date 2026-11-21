"use client";

import { AlertCircle, AlertTriangle, ArrowLeft, ExternalLink, RefreshCw } from "lucide-react";
import Link from "next/link";
import { useLocale, useTranslations } from "next-intl";
import { type ComponentProps, useMemo, useState } from "react";

import {
  Badge,
  Button,
  Card,
  EmptyState,
  ListCard,
  ListCardTag,
  PageHeader,
  Skeleton,
  Table,
  cn,
  focusRing,
} from "@thi360/ui";

import { usePermissions } from "@/features/session/session-provider";
import { useSetBreadcrumbLeaf } from "@/features/shell/breadcrumb-leaf";

import {
  formatStructuredOrderReason,
  resolveOrderActorLabel,
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
  GrantView,
  OrderItemView,
  PaymentDetailView,
  RefundResponse,
} from "../types";
import { ContinueFulfillDialog } from "./continue-fulfill-dialog";
import { LateSuccessDialog } from "./late-success-dialog";
import {
  GrantStateChip,
  KNOWN_ORDER_STATES,
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

const KNOWN_PAYMENT_METHODS = new Set(["VNPAY", "FREE"]);
const KNOWN_REFUND_KINDS = new Set(["API", "MANUAL"]);
const KNOWN_LATE_SUCCESS_RESOLUTIONS = new Set([
  "ACCEPTED",
  "AUTO_EXCESS",
  "PENDING",
  "REJECTED",
]);
const KNOWN_ORDER_RESULT_VIEWS = new Set([
  "SCORE",
  "SCORE_AND_ANSWERS",
  "FULL_SOLUTION",
  "FULL",
]);

type TimelineTone = "success" | "warning" | "danger" | "info" | "neutral";

const TIMELINE_DOT_CLASS: Record<TimelineTone, string> = {
  success: "bg-success ring-success",
  warning: "bg-warning ring-warning",
  danger: "bg-danger ring-danger",
  info: "bg-info ring-info",
  neutral: "bg-neutral ring-neutral",
};

interface TimelineEntry {
  id: string;
  at: string;
  orderIndex: number;
  source: "ORDER" | "AUDIT";
  actorLabel: string;
  actorIsRawUsername: boolean;
  actionLabel: string;
  localizedReason: string | null;
  rawReason: string | null;
  dotClass: string;
}

interface RefundRowItem {
  refund: RefundResponse;
  payment: PaymentDetailView;
}

export function canContinueFulfill(order: AdminOrderDetailResponse): boolean {
  if (order.state !== "PAID") return false;
  if (order.paymentMethod === "FREE") return true;
  const counted = order.payments.find((p) => p.state === "SUCCEEDED" && !p.duplicate);
  if (!counted) return false;
  const hasActiveRefund = counted.refunds.some(
    (r) => r.state === "PROCESSING" || r.state === "UNKNOWN",
  );
  return !hasActiveRefund && counted.refundedAmountVnd < counted.amountVnd;
}

function selectPrimaryRefundCandidate(
  payments: readonly PaymentDetailView[],
): PaymentDetailView | undefined {
  const succeeded = payments.filter((p) => p.state === "SUCCEEDED");
  const isExcess = (p: PaymentDetailView) => p.duplicate || Boolean(p.excessReason);
  const hasActive = (p: PaymentDetailView) =>
    p.refunds.some((r) => r.state === "UNKNOWN" || r.state === "PROCESSING");

  const refundableExcess = succeeded.filter(
    (p) => isExcess(p) && p.refundableAmountVnd > 0 && !hasActive(p),
  );
  if (refundableExcess.length > 0) return refundableExcess[0];

  const unknownExcess = succeeded.find(
    (p) => isExcess(p) && p.refunds.some((r) => r.state === "UNKNOWN"),
  );
  if (unknownExcess) return unknownExcess;

  const refundableCounted = succeeded.filter(
    (p) => !isExcess(p) && p.refundableAmountVnd > 0 && !hasActive(p),
  );
  if (refundableCounted.length > 0) return refundableCounted[0];

  return succeeded.find((p) => hasActive(p));
}

function refundGatewayMessageClass(state: string): string {
  if (state === "FAILED") return "text-danger";
  if (state === "UNKNOWN") return "text-warning";
  return "text-text-secondary";
}

function historyDotTone(toState: string, reason: string | null | undefined): TimelineTone {
  const code = reason?.split(":")[0] ?? "";
  if (toState === "FULFILLED" || code === "PAYMENT_SUCCEEDED") return "success";
  if (code.includes("REJECTED") || code.includes("FAILED") || code.includes("MISMATCH")) {
    return "danger";
  }
  if (toState === "REFUNDED" || code.startsWith("REFUND_")) return "info";
  if (
    toState === "PAID" ||
    code.includes("LATE_") ||
    code.includes("EXCESS") ||
    code.includes("DOUBLE_CHARGE")
  ) {
    return "warning";
  }
  return "neutral";
}

function auditDotTone(action: string, outcome: string): TimelineTone {
  if (outcome === "FAILURE" || action === "REFUND_FAILED") return "danger";
  if (
    action === "ENTITLEMENT_GRANTED" ||
    action === "ORDER_FULFILLED_MANUALLY" ||
    action === "PAYMENT_SUCCEEDED" ||
    action === "REFUND_SUCCEEDED"
  ) {
    return "success";
  }
  if (
    action.includes("EXCESS") ||
    action.includes("DOUBLE_CHARGE") ||
    action.includes("LATE_SUCCESS") ||
    action === "PAYMENT_NEEDS_REVIEW"
  ) {
    return "warning";
  }
  if (action.includes("REFUND")) return "info";
  return "neutral";
}

export function OrderDetailView({ code }: { code: string }) {
  const t = useTranslations("orders");
  const tStates = useTranslations("orders.states");
  const tFallback = useTranslations("orders.statusFallback");
  const tErrors = useTranslations("orders.errors");
  const tHistory = useTranslations("orders.detail.history");
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

  const sortedBundleItems = useMemo<OrderItemView[]>(
    () => (order ? [...order.items].sort((a, b) => a.position - b.position) : []),
    [order],
  );
  const bundleItemByExamId = useMemo(
    () => new Map(sortedBundleItems.map((item) => [item.examId, item])),
    [sortedBundleItems],
  );

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
      const actorResolved = resolveOrderActorLabel(h.actor, undefined, tHistory);
      const fromLabel = !h.fromState
        ? "—"
        : KNOWN_ORDER_STATES.has(h.fromState)
          ? tStates(h.fromState)
          : tFallback("unknown");
      const toLabel = KNOWN_ORDER_STATES.has(h.toState)
        ? tStates(h.toState)
        : tFallback("unknown");
      items.push({
        id: `history-${h.id}`,
        at: h.at,
        orderIndex: items.length,
        source: "ORDER",
        actorLabel: actorResolved.label,
        actorIsRawUsername: actorResolved.isRawUsername,
        actionLabel: t("detail.history.stateTransition", { from: fromLabel, to: toLabel }),
        localizedReason: formatted.localized,
        rawReason: formatted.rawText,
        dotClass: TIMELINE_DOT_CLASS[historyDotTone(h.toState, h.reason)],
      });
    }

    for (const a of order.auditEvents) {
      const formatted = formatStructuredOrderReason(a.reason, tHistoryReasons);
      const actorResolved = resolveOrderActorLabel(a.actorUsername, a.actorType, tHistory);
      items.push({
        id: `audit-${a.id}`,
        at: a.occurredAt,
        orderIndex: items.length,
        source: "AUDIT",
        actorLabel: actorResolved.label,
        actorIsRawUsername: actorResolved.isRawUsername,
        actionLabel: resolveOrderAuditActionLabel(a.action, tAuditActions),
        localizedReason: formatted.localized,
        rawReason: formatted.rawText,
        dotClass: TIMELINE_DOT_CLASS[auditDotTone(a.action, a.outcome)],
      });
    }

    items.sort((a, b) => {
      const ta = Date.parse(a.at);
      const tb = Date.parse(b.at);
      const aValid = !Number.isNaN(ta);
      const bValid = !Number.isNaN(tb);
      if (aValid !== bValid) return bValid ? 1 : -1;
      if (aValid && bValid && tb !== ta) return tb - ta;
      return b.orderIndex - a.orderIndex;
    });
    return items;
  }, [order, t, tStates, tFallback, tHistory, tAuditActions, tHistoryReasons]);

  const bundleItemColumns: ComponentProps<typeof Table<OrderItemView>>["columns"] = useMemo(
    () => [
      {
        id: "position",
        header: t("detail.bundleItems.position"),
        cell: ({ row }) => (
          <span className="font-mono text-xs text-text-secondary tabular-nums">
            {formatNumber(row.original.position, locale)}
          </span>
        ),
      },
      {
        id: "exam",
        header: t("detail.bundleItems.exam"),
        cell: ({ row }) => (
          <div className="flex min-w-44 flex-col gap-0.5">
            <span className="text-sm font-medium text-text" data-exam-content>
              {row.original.examName}
            </span>
            <span className="font-mono text-xs text-text-secondary" data-exam-content>
              {row.original.examCode}
            </span>
          </div>
        ),
      },
      {
        id: "version",
        header: t("detail.bundleItems.version"),
        cell: ({ row }) => (
          <span className="font-mono text-xs whitespace-nowrap text-text-secondary">
            {t("detail.bundleItems.versionValue", { version: row.original.examVersionNo })}
          </span>
        ),
      },
      {
        id: "attemptsGranted",
        header: () => <span className="block text-right">{t("detail.bundleItems.attempts")}</span>,
        cell: ({ row }) => (
          <span className="block text-right font-mono text-xs text-text tabular-nums">
            {formatNumber(row.original.attemptsGranted, locale)}
          </span>
        ),
      },
      {
        id: "resultView",
        header: t("detail.bundleItems.resultView"),
        cell: ({ row }) => (
          <span className="text-xs text-text-secondary">
            {KNOWN_ORDER_RESULT_VIEWS.has(row.original.resultView)
              ? t(`detail.bundleItems.resultViews.${row.original.resultView}`)
              : row.original.resultView}
          </span>
        ),
      },
    ],
    [locale, t],
  );

  const grantColumns: ComponentProps<typeof Table<GrantView>>["columns"] = useMemo(
    () => [
      {
        id: "code",
        header: t("detail.grants.code"),
        cell: ({ row }) => {
          const item = bundleItemByExamId.get(row.original.examId);
          return (
            <div className="flex flex-col gap-0.5">
              {item ? (
                <span className="text-xs font-medium text-text">
                  <span data-exam-content>{item.examName}</span>{" "}
                  <span className="font-mono text-text-secondary" data-exam-content>
                    ({item.examCode})
                  </span>
                </span>
              ) : null}
              <span className="block max-w-[260px] font-mono text-xs wrap-anywhere text-text">
                {row.original.entitlementId}
              </span>
            </div>
          );
        },
      },
      {
        id: "remaining",
        header: t("detail.grants.remaining"),
        cell: ({ row }) => (
          <span className="font-mono text-xs whitespace-nowrap text-text">
            {formatNumber(row.original.attemptsLeft, locale)} /{" "}
            {formatNumber(row.original.attempts, locale)}
          </span>
        ),
      },
      {
        id: "status",
        header: t("detail.grants.status"),
        cell: ({ row }) => <GrantStateChip grant={row.original} />,
      },
      {
        id: "grantedAt",
        header: t("detail.grants.createdAt"),
        cell: ({ row }) => {
          const grant = row.original;
          return (
            <div className="flex flex-col gap-0.5 font-mono text-xs text-text-secondary">
              <span className="whitespace-nowrap">{formatOrderDateTime(grant.grantedAt, locale)}</span>
              {grant.revokedAt ? (
                <span className="text-caption">
                  {t("detail.grants.revokedAt")}: {formatOrderDateTime(grant.revokedAt, locale)}
                </span>
              ) : null}
            </div>
          );
        },
      },
    ],
    [bundleItemByExamId, locale, t],
  );

  const paymentColumns: ComponentProps<typeof Table<PaymentDetailView>>["columns"] = useMemo(
    () => [
      {
        id: "txnRef",
        header: t("detail.payments.txnRef"),
        cell: ({ row }) => {
          const payment = row.original;
          const bankMeta = [payment.bankCode, payment.cardType].filter(Boolean).join(" · ");
          return (
            <div className="flex max-w-[190px] min-w-0 flex-col gap-0.5">
              <span className="font-mono text-xs font-semibold wrap-anywhere text-text">
                #{formatAttemptSeq(payment.attemptSeq)} · {payment.txnRef}
              </span>
              {payment.vnpTransactionNo ? (
                <span className="font-mono text-caption wrap-anywhere text-text-secondary">
                  {t("detail.payments.vnpayTxnNo")}: {payment.vnpTransactionNo}
                </span>
              ) : null}
              {bankMeta ? <span className="text-caption text-text-secondary">{bankMeta}</span> : null}
            </div>
          );
        },
      },
      {
        id: "amount",
        header: () => <span className="block text-right">{t("detail.payments.amount")}</span>,
        cell: ({ row }) => (
          <div className="text-right font-mono text-xs font-semibold whitespace-nowrap text-text">
            {formatVnd(row.original.amountVnd, locale)}
          </div>
        ),
      },
      {
        id: "status",
        header: t("detail.payments.status"),
        cell: ({ row }) => {
          const payment = row.original;
          const resolutionLabel = payment.lateSuccessReview
            ? KNOWN_LATE_SUCCESS_RESOLUTIONS.has(payment.lateSuccessReview)
              ? t(`lateSuccessResolutions.${payment.lateSuccessReview}`)
              : tFallback("unknown")
            : null;
          return (
            <div className="flex flex-col gap-1">
              <PaymentAttemptStateChip
                state={payment.state}
                lateSuccessAt={payment.lateSuccessAt}
              />
              {resolutionLabel ? (
                <span className="text-caption text-text-secondary">{resolutionLabel}</span>
              ) : null}
            </div>
          );
        },
      },
      {
        id: "classification",
        header: t("detail.payments.classification"),
        cell: ({ row }) => <PaymentClassificationChip payment={row.original} />,
      },
      {
        id: "payDate",
        header: t("detail.payments.payDate"),
        cell: ({ row }) => (
          <span className="font-mono text-xs whitespace-nowrap text-text-secondary">
            {row.original.payDate ? formatVnpayPayDate(row.original.payDate, locale) : "—"}
          </span>
        ),
      },
      {
        id: "action",
        header: () => <span className="block text-right">{t("detail.payments.action")}</span>,
        cell: ({ row }) => {
          const payment = row.original;
          const showLateBtn = canRecover && payment.lateSuccessReview === "PENDING";
          const showRefundBtn = canRefund && payment.state === "SUCCEEDED";
          if (!showLateBtn && !showRefundBtn) {
            return <span className="block text-right text-xs text-text-secondary">—</span>;
          }
          return (
            <div className="flex flex-col items-end gap-1.5">
              {showLateBtn ? (
                <Button
                  type="button"
                  variant="outline"
                  size="sm"
                  aria-label={t("detail.actions.reviewLateSuccessAria", {
                    seq: formatAttemptSeq(payment.attemptSeq),
                    txnRef: payment.txnRef,
                  })}
                  onClick={() => setLateSuccessPaymentId(payment.id)}
                >
                  {t("detail.actions.reviewLateSuccess")}
                </Button>
              ) : null}
              {showRefundBtn ? (
                <PaymentRefundActionButton
                  payment={payment}
                  context="row"
                  onOpen={() => setRefundPaymentId(payment.id)}
                />
              ) : null}
            </div>
          );
        },
      },
    ],
    [canRecover, canRefund, locale, t, tFallback],
  );

  const refundColumns: ComponentProps<typeof Table<RefundRowItem>>["columns"] = useMemo(
    () => [
      {
        id: "paymentRef",
        header: t("detail.refunds.paymentRef"),
        cell: ({ row }) => {
          const { refund } = row.original;
          return (
            <div className="flex max-w-[165px] min-w-0 flex-col gap-0.5 font-mono text-xs text-text">
              <span className="wrap-anywhere">{refund.paymentTxnRef}</span>
              {refund.externalRef ? (
                <span className="text-caption wrap-anywhere text-text-secondary">
                  {refund.externalRef}
                </span>
              ) : null}
            </div>
          );
        },
      },
      {
        id: "kind",
        header: t("detail.refunds.kind"),
        cell: ({ row }) => {
          const kind = row.original.refund.kind;
          return (
            <span className="text-xs text-text-secondary">
              {KNOWN_REFUND_KINDS.has(kind) ? t(`refundKinds.${kind}`) : tFallback("unknown")}
            </span>
          );
        },
      },
      {
        id: "amount",
        header: () => <span className="block text-right">{t("detail.refunds.amount")}</span>,
        cell: ({ row }) => (
          <div className="text-right font-mono text-xs font-semibold whitespace-nowrap text-text">
            {formatVnd(row.original.refund.amountVnd, locale)}
          </div>
        ),
      },
      {
        id: "status",
        header: t("detail.refunds.status"),
        cell: ({ row }) => <RefundRowStateChip state={row.original.refund.state} />,
      },
      {
        id: "reason",
        header: t("detail.refunds.reason"),
        cell: ({ row }) => {
          const { refund } = row.original;
          return (
            <div className="flex min-w-36 max-w-[240px] flex-col gap-0.5 text-xs text-text">
              <p className="wrap-anywhere" data-exam-content>
                {refund.reason}
              </p>
              {refund.gatewayMessage ? (
                <p
                  className={cn("wrap-anywhere", refundGatewayMessageClass(refund.state))}
                  data-exam-content
                >
                  {refund.gatewayMessage}
                </p>
              ) : null}
              {refund.markFailedReason ? (
                <p className="text-text-secondary wrap-anywhere" data-exam-content>
                  {refund.markFailedReason}
                </p>
              ) : null}
            </div>
          );
        },
      },
      {
        id: "createdAt",
        header: t("detail.refunds.createdAt"),
        cell: ({ row }) => (
          <span className="font-mono text-xs whitespace-nowrap text-text-secondary">
            {formatOrderDateTime(row.original.refund.requestedAt, locale)}
          </span>
        ),
      },
      {
        id: "action",
        header: () => <span className="block text-right">{t("detail.refunds.action")}</span>,
        cell: ({ row }) => {
          const { refund, payment } = row.original;
          const canQueryRefund =
            canRefund &&
            refund.kind === "API" &&
            (refund.state === "UNKNOWN" || refund.state === "PROCESSING");
          if (!canQueryRefund) {
            return <span className="block text-right text-xs text-text-secondary">—</span>;
          }
          return (
            <div className="flex justify-end">
              <Button
                type="button"
                variant="outline"
                size="sm"
                aria-label={t("detail.actions.queryRefundAria", { id: refund.id })}
                onClick={() => setRefundPaymentId(payment.id)}
              >
                {t("detail.actions.queryRefund")}
              </Button>
            </div>
          );
        },
      },
    ],
    [canRefund, locale, t, tFallback],
  );

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

  const showContinueFulfill = canContinueFulfill(order);
  const pendingLatePayments = order.payments.filter((p) => p.lateSuccessReview === "PENDING");
  const primaryRefundCandidate = selectPrimaryRefundCandidate(order.payments);
  const allRefunds: RefundRowItem[] = order.payments.flatMap((payment) =>
    payment.refunds.map((refund) => ({ refund, payment })),
  );

  let reviewReasonFormatted: string | null | undefined = order.reviewReason;
  let reviewReasonIsRaw = false;
  if (order.reviewReason) {
    if (KNOWN_REVIEW_REASONS.has(order.reviewReason)) {
      reviewReasonFormatted = tReviewReasons(order.reviewReason);
    } else {
      const structured = formatStructuredOrderReason(order.reviewReason, tHistoryReasons);
      if (structured.localized) {
        reviewReasonFormatted = structured.localized;
      } else {
        reviewReasonFormatted = structured.rawText;
        reviewReasonIsRaw = true;
      }
    }
  }

  const paymentMethodLabel = KNOWN_PAYMENT_METHODS.has(order.paymentMethod)
    ? t(`paymentMethods.${order.paymentMethod}`)
    : tFallback("unknown");

  return (
    <div className="@container flex flex-col gap-5">
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

              {canViewExam && order.productType === "BUNDLE" && order.bundleId ? (
                <Button asChild variant="outline" size="sm">
                  <Link href={`/bundles/${encodeURIComponent(order.bundleId)}`}>
                    <span>{t("nav.openBundle")}</span>
                    <ExternalLink aria-hidden="true" />
                  </Link>
                </Button>
              ) : null}

              {canRecover && showContinueFulfill ? (
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
                  context="header"
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
            className={cn("font-mono text-sm font-semibold text-primary-text hover:underline", focusRing)}
          >
            {order.replacedByOrderCode}
          </Link>
        </div>
      ) : null}

      {showContinueFulfill ? (
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
            <span
              className="text-xs text-text-secondary"
              data-exam-content={reviewReasonIsRaw || undefined}
            >
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
              <dd className="mt-1 flex flex-wrap items-center gap-1.5 text-sm font-medium text-text">
                <span data-exam-content>{order.productName}</span>
                {order.productType === "BUNDLE" ? <Badge>{t("bundle.badge")}</Badge> : null}
              </dd>
              <dd className="font-mono text-xs text-text-secondary">
                {order.productCode}
                {order.productType === "BUNDLE" && order.bundleVersionNo != null
                  ? ` · v${order.bundleVersionNo}`
                  : ""}
              </dd>
            </div>

            <div>
              <dt className="text-xs text-text-secondary">{t("detail.snapshot.attemptsGranted")}</dt>
              <dd className="mt-1 font-mono text-sm font-semibold text-text">
                {formatNumber(order.attemptsGranted, locale)}
              </dd>
              <dd className="text-xs text-text-secondary">
                {t("detail.snapshot.paymentMethod")}: {paymentMethodLabel}
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

      {/* Block 1B: Bundle Items */}
      {order.productType === "BUNDLE" || sortedBundleItems.length > 0 ? (
        <Card padding="none" className="overflow-hidden">
          <div className="border-b border-border px-5 py-4">
            <h2 className="text-base font-semibold text-text">
              {t("detail.bundleItems.title")}
            </h2>
          </div>
          {sortedBundleItems.length === 0 ? (
            <p className="px-5 py-4 text-sm text-text-secondary">
              {t("detail.bundleItems.empty")}
            </p>
          ) : (
            <Table
              aria-label={t("detail.bundleItems.title")}
              columns={bundleItemColumns}
              data={sortedBundleItems}
              getRowId={(item) => item.id}
              className="[&_:is(th,td)]:px-3 [&_:is(th,td):first-child]:ps-5 [&_:is(th,td):last-child]:pe-5"
              empty={
                <p className="px-5 py-4 text-sm text-text-secondary">
                  {t("detail.bundleItems.empty")}
                </p>
              }
              cards={{
                render: (item) => (
                  <ListCard
                    title={<span data-exam-content>{item.examName}</span>}
                    caption={
                      <span data-exam-content className="font-mono">
                        {item.examCode}
                      </span>
                    }
                    tags={
                      <ListCardTag>
                        {t("detail.bundleItems.versionValue", { version: item.examVersionNo })}
                      </ListCardTag>
                    }
                    meta={[
                      {
                        label: t("detail.bundleItems.position"),
                        value: formatNumber(item.position, locale),
                      },
                      {
                        label: t("detail.bundleItems.attempts"),
                        value: formatNumber(item.attemptsGranted, locale),
                      },
                      {
                        label: t("detail.bundleItems.resultView"),
                        value: KNOWN_ORDER_RESULT_VIEWS.has(item.resultView)
                          ? t(`detail.bundleItems.resultViews.${item.resultView}`)
                          : item.resultView,
                      },
                    ]}
                  />
                ),
              }}
            />
          )}
        </Card>
      ) : null}

      {/* Block 2: Grants */}
      <Card padding="none" className="overflow-hidden">
        <div className="border-b border-border px-5 py-4">
          <h2 className="text-base font-semibold text-text">{t("detail.grants.title")}</h2>
        </div>
        {order.grants.length === 0 ? (
          <p className="px-5 py-4 text-sm text-text-secondary">{t("detail.grants.empty")}</p>
        ) : (
          <Table
            aria-label={t("detail.grants.title")}
            columns={grantColumns}
            data={order.grants}
            getRowId={(grant) => grant.grantId}
            empty={<p className="px-5 py-4 text-sm text-text-secondary">{t("detail.grants.empty")}</p>}
            cards={{
              render: (grant) => {
                const item = bundleItemByExamId.get(grant.examId);
                return (
                  <ListCard
                    title={<span className="font-mono text-xs wrap-anywhere">{grant.entitlementId}</span>}
                    caption={
                      item ? (
                        <span>
                          <span data-exam-content>{item.examName}</span>{" "}
                          <span className="font-mono" data-exam-content>
                            ({item.examCode})
                          </span>
                        </span>
                      ) : undefined
                    }
                    status={<GrantStateChip grant={grant} />}
                    meta={[
                      {
                        label: t("detail.grants.remaining"),
                        value: `${formatNumber(grant.attemptsLeft, locale)} / ${formatNumber(grant.attempts, locale)}`,
                      },
                      {
                        label: t("detail.grants.createdAt"),
                        value: formatOrderDateTime(grant.grantedAt, locale),
                      },
                      Boolean(grant.revokedAt) && {
                        label: t("detail.grants.revokedAt"),
                        value: formatOrderDateTime(grant.revokedAt, locale),
                      },
                    ]}
                  />
                );
              },
            }}
          />
        )}
      </Card>

      {/* Block 3A: Payments */}
      <Card padding="none" className="overflow-hidden">
        <div className="border-b border-border px-5 py-4">
          <h2 className="text-base font-semibold text-text">{t("detail.payments.title")}</h2>
        </div>
        {order.payments.length === 0 ? (
          <p className="px-5 py-4 text-sm text-text-secondary">{t("detail.payments.empty")}</p>
        ) : (
          <Table
            aria-label={t("detail.payments.title")}
            columns={paymentColumns}
            data={order.payments}
            getRowId={(payment) => payment.id}
            className="[&_:is(th,td)]:px-3 [&_:is(th,td):first-child]:ps-5 [&_:is(th,td):last-child]:pe-5"
            empty={
              <p className="px-5 py-4 text-sm text-text-secondary">{t("detail.payments.empty")}</p>
            }
            cards={{
              render: (payment) => {
                const showLateBtn = canRecover && payment.lateSuccessReview === "PENDING";
                const showRefundBtn = canRefund && payment.state === "SUCCEEDED";
                const bankMeta = [payment.bankCode, payment.cardType].filter(Boolean).join(" · ");
                const resolutionLabel = payment.lateSuccessReview
                  ? KNOWN_LATE_SUCCESS_RESOLUTIONS.has(payment.lateSuccessReview)
                    ? t(`lateSuccessResolutions.${payment.lateSuccessReview}`)
                    : tFallback("unknown")
                  : null;
                return (
                  <ListCard
                    title={
                      <span className="font-mono text-xs font-semibold wrap-anywhere">
                        #{formatAttemptSeq(payment.attemptSeq)} · {payment.txnRef}
                      </span>
                    }
                    caption={
                      [
                        payment.vnpTransactionNo
                          ? `${t("detail.payments.vnpayTxnNo")}: ${payment.vnpTransactionNo}`
                          : null,
                        bankMeta || null,
                      ]
                        .filter(Boolean)
                        .join(" · ") || undefined
                    }
                    status={
                      <PaymentAttemptStateChip
                        state={payment.state}
                        lateSuccessAt={payment.lateSuccessAt}
                      />
                    }
                    tags={<PaymentClassificationChip payment={payment} />}
                    meta={[
                      {
                        label: t("detail.payments.amount"),
                        value: (
                          <span className="font-mono font-semibold text-text">
                            {formatVnd(payment.amountVnd, locale)}
                          </span>
                        ),
                      },
                      {
                        label: t("detail.payments.payDate"),
                        value: payment.payDate
                          ? formatVnpayPayDate(payment.payDate, locale)
                          : "—",
                      },
                      Boolean(resolutionLabel) && {
                        label: t("detail.payments.lateSuccessResolution"),
                        value: resolutionLabel,
                      },
                    ]}
                  >
                    {showLateBtn || showRefundBtn ? (
                      <div className="relative z-1 flex flex-wrap gap-2 pt-1">
                        {showLateBtn ? (
                          <Button
                            type="button"
                            variant="outline"
                            size="sm"
                            aria-label={t("detail.actions.reviewLateSuccessAria", {
                              seq: formatAttemptSeq(payment.attemptSeq),
                              txnRef: payment.txnRef,
                            })}
                            onClick={() => setLateSuccessPaymentId(payment.id)}
                          >
                            {t("detail.actions.reviewLateSuccess")}
                          </Button>
                        ) : null}
                        {showRefundBtn ? (
                          <PaymentRefundActionButton
                            payment={payment}
                            context="row"
                            onOpen={() => setRefundPaymentId(payment.id)}
                          />
                        ) : null}
                      </div>
                    ) : null}
                  </ListCard>
                );
              },
            }}
          />
        )}
      </Card>

      {/* Block 3B: Refund History */}
      <Card padding="none" className="overflow-hidden">
        <div className="border-b border-border px-5 py-4">
          <h2 className="text-base font-semibold text-text">{t("detail.refunds.title")}</h2>
        </div>
        {allRefunds.length === 0 ? (
          <p className="px-5 py-4 text-sm text-text-secondary">{t("detail.refunds.empty")}</p>
        ) : (
          <Table
            aria-label={t("detail.refunds.title")}
            columns={refundColumns}
            data={allRefunds}
            getRowId={(item) => item.refund.id}
            className="[&_:is(th,td)]:px-3 [&_:is(th,td):first-child]:ps-5 [&_:is(th,td):last-child]:pe-5"
            empty={
              <p className="px-5 py-4 text-sm text-text-secondary">{t("detail.refunds.empty")}</p>
            }
            cards={{
              render: ({ refund, payment }) => {
                const kindLabel = KNOWN_REFUND_KINDS.has(refund.kind)
                  ? t(`refundKinds.${refund.kind}`)
                  : tFallback("unknown");
                const canQueryRefund =
                  canRefund &&
                  refund.kind === "API" &&
                  (refund.state === "UNKNOWN" || refund.state === "PROCESSING");
                return (
                  <ListCard
                    title={
                      <span className="font-mono text-xs font-semibold wrap-anywhere">
                        {refund.paymentTxnRef}
                      </span>
                    }
                    caption={
                      refund.externalRef
                        ? `${kindLabel} · ${refund.externalRef}`
                        : kindLabel
                    }
                    status={<RefundRowStateChip state={refund.state} />}
                    meta={[
                      {
                        label: t("detail.refunds.amount"),
                        value: (
                          <span className="font-mono font-semibold text-text">
                            {formatVnd(refund.amountVnd, locale)}
                          </span>
                        ),
                      },
                      {
                        label: t("detail.refunds.createdAt"),
                        value: formatOrderDateTime(refund.requestedAt, locale),
                      },
                    ]}
                  >
                    <div className="flex flex-col gap-1 text-xs">
                      <p className="wrap-anywhere text-text" data-exam-content>
                        {refund.reason}
                      </p>
                      {refund.gatewayMessage ? (
                        <p
                          className={cn("wrap-anywhere", refundGatewayMessageClass(refund.state))}
                          data-exam-content
                        >
                          {refund.gatewayMessage}
                        </p>
                      ) : null}
                      {refund.markFailedReason ? (
                        <p className="text-text-secondary wrap-anywhere" data-exam-content>
                          {refund.markFailedReason}
                        </p>
                      ) : null}
                    </div>
                    {canQueryRefund ? (
                      <div className="relative z-1 flex flex-wrap gap-2 pt-1">
                        <Button
                          type="button"
                          variant="outline"
                          size="sm"
                          aria-label={t("detail.actions.queryRefundAria", { id: refund.id })}
                          onClick={() => setRefundPaymentId(payment.id)}
                        >
                          {t("detail.actions.queryRefund")}
                        </Button>
                      </div>
                    ) : null}
                  </ListCard>
                );
              },
            }}
          />
        )}
      </Card>

      {/* Block 4: Order History & Audit Log Timeline */}
      <Card className="p-5">
        <h2 className="text-base font-semibold text-text">{t("detail.history.title")}</h2>
        {timelineEntries.length === 0 ? (
          <p className="mt-3 text-sm text-text-secondary">{t("detail.history.empty")}</p>
        ) : (
          <ol className="mt-4 flex flex-col">
            {timelineEntries.map((entry) => (
              <li
                key={entry.id}
                className="grid grid-cols-[1.25rem_minmax(0,1fr)] gap-x-3 sm:grid-cols-[9rem_1.25rem_minmax(0,1fr)]"
              >
                <time
                  dateTime={entry.at}
                  className="hidden py-2 font-mono text-xs whitespace-nowrap text-text-secondary tabular-nums sm:block"
                >
                  {formatOrderDateTime(entry.at, locale)}
                </time>
                <span aria-hidden="true" className="relative flex justify-center">
                  <span className="absolute inset-y-0 w-px bg-border" />
                  <span
                    className={cn(
                      "relative mt-3 size-2.5 shrink-0 rounded-full border-2 border-surface ring-1",
                      entry.dotClass,
                    )}
                  />
                </span>
                <div className="flex min-w-0 flex-col gap-1 py-2 pb-4">
                  <div className="flex flex-wrap items-center gap-x-2 gap-y-1">
                    <span className="text-sm font-semibold text-text">{entry.actionLabel}</span>
                    <ListCardTag>
                      {entry.source === "ORDER"
                        ? t("detail.history.sourceOrder")
                        : t("detail.history.sourceAudit")}
                    </ListCardTag>
                  </div>
                  <div className="flex flex-wrap items-center gap-x-2 gap-y-0.5 text-xs text-text-secondary">
                    <time dateTime={entry.at} className="font-mono tabular-nums sm:hidden">
                      {formatOrderDateTime(entry.at, locale)}
                    </time>
                    <span
                      className={entry.actorIsRawUsername ? "font-mono" : undefined}
                      data-exam-content={entry.actorIsRawUsername || undefined}
                    >
                      {entry.actorLabel}
                    </span>
                  </div>
                  {entry.localizedReason ? (
                    <p className="text-xs text-text-secondary">{entry.localizedReason}</p>
                  ) : null}
                  {entry.rawReason ? (
                    <p className="text-xs wrap-anywhere text-text-secondary" data-exam-content>
                      {entry.rawReason}
                    </p>
                  ) : null}
                </div>
              </li>
            ))}
          </ol>
        )}
      </Card>

      {canRecover && showContinueFulfill ? (
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
  context,
}: {
  payment: PaymentDetailView;
  onOpen: () => void;
  context: "header" | "row";
}) {
  const t = useTranslations("orders.detail.actions");
  const hasActiveRefund = payment.refunds.some(
    (r) => r.state === "UNKNOWN" || r.state === "PROCESSING",
  );
  const ariaLabel = t("refundAria", {
    seq: formatAttemptSeq(payment.attemptSeq),
    txnRef: payment.txnRef,
  });

  if (hasActiveRefund) {
    const reasonId = `refund-lock-${context}-${payment.id}`;
    return (
      <div className="inline-flex flex-wrap items-center justify-end gap-2">
        <Button
          type="button"
          variant="outline"
          size="sm"
          disabled
          aria-label={ariaLabel}
          aria-describedby={reasonId}
        >
          {t("refund")}
        </Button>
        <span id={reasonId} className="text-caption text-text-secondary">
          {t("refundPendingTooltip")}
        </span>
      </div>
    );
  }

  if (payment.refundableAmountVnd <= 0) {
    return null;
  }

  return (
    <Button type="button" variant="outline" size="sm" aria-label={ariaLabel} onClick={onOpen}>
      {t("refund")}
    </Button>
  );
}

// Keep unused type export silenced if any
export type { AdminOrderDetailResponse };
