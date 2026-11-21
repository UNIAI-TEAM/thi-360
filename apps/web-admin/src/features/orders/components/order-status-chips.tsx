"use client";

import { useTranslations } from "next-intl";

import { StatusChip } from "@thi360/ui";

import type {
  AdminPaymentStatus,
  AdminRefundStatus,
  GrantView,
  OrderState,
  PaymentDetailView,
  PaymentState,
  RefundState,
} from "../types";

type ChipTone = "success" | "warning" | "danger" | "info" | "neutral";

const ORDER_STATE_TONE: Record<OrderState, ChipTone> = {
  FULFILLED: "success",
  PAID: "warning",
  PENDING_PAYMENT: "info",
  REFUNDED: "info",
  REPLACED: "neutral",
  EXPIRED: "neutral",
  CANCELLED: "neutral",
};

const ORDER_PAYMENT_TONE: Record<AdminPaymentStatus, ChipTone> = {
  SUCCEEDED: "success",
  PENDING: "info",
  FAILED: "danger",
  FREE: "neutral",
};

const PAYMENT_ATTEMPT_TONE: Record<PaymentState, ChipTone> = {
  SUCCEEDED: "success",
  UNKNOWN: "danger",
  CREATED: "info",
  FAILED: "danger",
};

const ORDER_REFUND_TONE: Record<AdminRefundStatus | "UNKNOWN", ChipTone> = {
  FULL: "success",
  PARTIAL: "info",
  IN_PROGRESS: "info",
  NONE: "neutral",
  UNKNOWN: "warning",
};

const REFUND_ROW_TONE: Record<RefundState, ChipTone> = {
  SUCCEEDED: "success",
  PROCESSING: "info",
  UNKNOWN: "danger",
  FAILED: "danger",
};

export const KNOWN_ORDER_STATES = new Set<string>(Object.keys(ORDER_STATE_TONE));

export function OrderStateChip({ state }: { state: OrderState | string }) {
  const t = useTranslations("orders.states");
  const tFallback = useTranslations("orders.statusFallback");
  if (Object.hasOwn(ORDER_STATE_TONE, state)) {
    return <StatusChip tone={ORDER_STATE_TONE[state as OrderState]}>{t(state)}</StatusChip>;
  }
  return <StatusChip tone="neutral">{tFallback("unknown")}</StatusChip>;
}

export function OrderPaymentStatusChip({ status }: { status: AdminPaymentStatus | string }) {
  const t = useTranslations("orders.paymentStatuses");
  const tFallback = useTranslations("orders.statusFallback");
  if (Object.hasOwn(ORDER_PAYMENT_TONE, status)) {
    return (
      <StatusChip tone={ORDER_PAYMENT_TONE[status as AdminPaymentStatus]}>{t(status)}</StatusChip>
    );
  }
  return <StatusChip tone="neutral">{tFallback("unknown")}</StatusChip>;
}

export function PaymentAttemptStateChip({
  state,
  lateSuccessAt,
}: {
  state: PaymentState | string;
  lateSuccessAt?: string | null;
}) {
  const t = useTranslations("orders.paymentStatuses");
  const tFallback = useTranslations("orders.statusFallback");
  if (state === "SUCCEEDED" && lateSuccessAt) {
    return <StatusChip tone="warning">{t("LATE_SUCCESS")}</StatusChip>;
  }
  if (Object.hasOwn(PAYMENT_ATTEMPT_TONE, state)) {
    return (
      <StatusChip tone={PAYMENT_ATTEMPT_TONE[state as PaymentState]}>{t(state)}</StatusChip>
    );
  }
  return <StatusChip tone="neutral">{tFallback("unknown")}</StatusChip>;
}

export function OrderRefundStatusChip({ status }: { status: AdminRefundStatus | string }) {
  const t = useTranslations("orders.refundStatuses");
  const tFallback = useTranslations("orders.statusFallback");
  if (Object.hasOwn(ORDER_REFUND_TONE, status)) {
    return (
      <StatusChip tone={ORDER_REFUND_TONE[status as AdminRefundStatus | "UNKNOWN"]}>
        {t(status)}
      </StatusChip>
    );
  }
  return <StatusChip tone="neutral">{tFallback("unknown")}</StatusChip>;
}

export function RefundRowStateChip({ state }: { state: RefundState | string }) {
  const t = useTranslations("orders.refundRowStatuses");
  const tFallback = useTranslations("orders.statusFallback");
  if (Object.hasOwn(REFUND_ROW_TONE, state)) {
    return <StatusChip tone={REFUND_ROW_TONE[state as RefundState]}>{t(state)}</StatusChip>;
  }
  return <StatusChip tone="neutral">{tFallback("unknown")}</StatusChip>;
}

export function PaymentClassificationChip({ payment }: { payment: PaymentDetailView }) {
  const t = useTranslations("orders.paymentClassifications");

  if (payment.duplicate || payment.excessReason) {
    if (payment.refundableAmountVnd <= 0 && payment.refundedAmountVnd > 0) {
      return <StatusChip tone="neutral">{t("EXCESS_REFUNDED")}</StatusChip>;
    }
    return <StatusChip tone="warning">{t("EXCESS")}</StatusChip>;
  }
  if (payment.lateSuccessReview === "PENDING" || payment.state === "UNKNOWN") {
    return <StatusChip tone="warning">{t("PENDING_RECONCILE")}</StatusChip>;
  }
  if (payment.state === "SUCCEEDED") {
    return <StatusChip tone="success">{t("COUNTED")}</StatusChip>;
  }
  return <StatusChip tone="neutral">{t("UNPAID")}</StatusChip>;
}

export function GrantStateChip({ grant }: { grant: GrantView }) {
  const t = useTranslations("orders.grantStatuses");
  if (grant.revokedAt) {
    return <StatusChip tone="danger">{t("REVOKED")}</StatusChip>;
  }
  if (grant.attemptsLeft <= 0) {
    return <StatusChip tone="neutral">{t("EXHAUSTED")}</StatusChip>;
  }
  return <StatusChip tone="success">{t("ACTIVE")}</StatusChip>;
}
