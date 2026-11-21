export const COMMERCE_AUDIT_ACTIONS_IN_USER_HISTORY = [
  "REFUND_REQUESTED",
  "REFUND_SUCCEEDED",
  "REFUND_FAILED",
  "REFUND_RECORDED_MANUALLY",
  "ENTITLEMENT_REVOKED",
  "ORDER_FULFILLED_MANUALLY",
  "PAYMENT_LATE_SUCCESS_ACCEPTED",
  "PAYMENT_LATE_SUCCESS_REJECTED",
] as const;

export const KNOWN_ORDER_AUDIT_ACTIONS: Record<string, true> = {
  DOUBLE_CHARGE_REPLACED_ORDER: true,
  ENTITLEMENT_GRANTED: true,
  ENTITLEMENT_REVOKED: true,
  ORDER_CANCELLED: true,
  ORDER_CREATED: true,
  ORDER_EXPIRED: true,
  ORDER_FULFILLED_MANUALLY: true,
  ORDER_PAYMENT_CREATED: true,
  ORDER_REFUNDED_BEFORE_FULFILLMENT: true,
  ORDER_REPLACED: true,
  PAYMENT_EXCESS_RECEIVED: true,
  PAYMENT_LATE_SUCCESS_ACCEPTED: true,
  PAYMENT_LATE_SUCCESS_PENDING: true,
  PAYMENT_LATE_SUCCESS_REJECTED: true,
  PAYMENT_NEEDS_REVIEW: true,
  PAYMENT_SUCCEEDED: true,
  REFUND_FAILED: true,
  REFUND_RECORDED_MANUALLY: true,
  REFUND_REQUESTED: true,
  REFUND_SUCCEEDED: true,
};

export function resolveOrderAuditActionLabel(
  action: string,
  tAuditActions: (key: string) => string,
): string {
  return Object.hasOwn(KNOWN_ORDER_AUDIT_ACTIONS, action) ? tAuditActions(action) : action;
}

const KNOWN_HISTORY_REASON_CODES = new Set([
  "AFTER_COUNTED",
  "AUTO_EXCESS",
  "AUTO_EXCESS_AFTER_COUNTED",
  "CANCELLED_BY_USER",
  "DOUBLE_CHARGE_REPLACED_ORDER",
  "EXCESS_AFTER_COUNTED",
  "FREE_ORDER_PAID",
  "FULFILLED",
  "LATE_PAYMENT_RECLAIMED",
  "LATE_PAYMENT_REPLACED_DOUBLE_CHARGE",
  "LATE_PAYMENT_REPLACED_RECLAIMED",
  "LATE_SUCCESS_ACCEPTED",
  "LATE_SUCCESS_ON_FAILED_PAYMENT",
  "LATE_SUCCESS_PENDING_REVIEW",
  "LATE_SUCCESS_REJECTED",
  "MANUAL_RECOVERY",
  "ORDER_CANCELLED",
  "ORDER_CREATED",
  "ORDER_EXPIRED",
  "ORDER_FULFILLED",
  "ORDER_REFUNDED",
  "ORDER_REPLACED",
  "PAYMENT_ATTEMPT_CREATED",
  "PAYMENT_FAILED",
  "PAYMENT_SUCCEEDED",
  "REFUNDED",
  "REFUNDED_BEFORE_FULFILLMENT",
  "REFUND_LATE_SUCCESS_AFTER_FAILED",
  "REFUND_QUERY_SUCCEEDED_AFTER_FAILED",
  "REFUND_REJECTED_09",
  "REFUND_SUCCEEDED_QUERY_MISMATCH",
  "REPLACED_BY",
  "REUSE_PENDING_PAYMENT",
  "VNPAY_QUERYDR_AMOUNT_MISMATCH",
  "VNPAY_QUERYDR_TXN_REF_MISMATCH",
]);

const QUERYDR_STATUS_PREFIX = "VNPAY_QUERYDR_STATUS_";

export interface FormattedReasonText {
  localized: string | null;
  rawText: string | null;
}

/**
 * Backend writes structured reason prefixes like `DOUBLE_CHARGE_REPLACED_ORDER:<ref>`,
 * `PAYMENT_ATTEMPT_CREATED:<txnRef>`, or bare codes like `ORDER_CREATED` / `FULFILLED`
 * alongside free-text admin reasons. Map known codes via i18n and mark free-text
 * reasons as raw content for `data-exam-content`.
 */
export function formatStructuredOrderReason(
  reason: string | null | undefined,
  tReasons: (key: string, values?: Record<string, string>) => string,
): FormattedReasonText {
  const trimmed = reason?.trim() ?? "";
  if (!trimmed) return { localized: null, rawText: null };

  const sepIndex = trimmed.indexOf(":");
  const code = sepIndex === -1 ? trimmed : trimmed.slice(0, sepIndex);
  const rawDetail = sepIndex === -1 ? "" : trimmed.slice(sepIndex + 1).trim();

  if (KNOWN_HISTORY_REASON_CODES.has(code)) {
    return {
      localized: tReasons(code, {
        hasDetail: rawDetail ? "yes" : "no",
        detail: rawDetail,
      }),
      rawText: null,
    };
  }

  if (code.startsWith(QUERYDR_STATUS_PREFIX)) {
    const statusPart = code.slice(QUERYDR_STATUS_PREFIX.length);
    const combined = [statusPart, rawDetail].filter(Boolean).join(" · ");
    return {
      localized: tReasons("VNPAY_QUERYDR_STATUS", {
        hasDetail: combined ? "yes" : "no",
        detail: combined,
      }),
      rawText: null,
    };
  }

  return {
    localized: null,
    rawText: trimmed,
  };
}

export interface OrderActorTranslator {
  (key: string): string;
}

const KNOWN_ACTOR_KEYS: Record<string, string> = {
  LEARNER: "actorLearner",
  PLATFORM: "actorPlatform",
  SYSTEM: "actorSystem",
  VNPAY_IPN: "actorVnpayIpn",
  VNPAY_QUERYDR: "actorVnpayQueryDr",
  WORKER: "actorSystem",
};

export interface ResolvedActorLabel {
  label: string;
  isRawUsername: boolean;
}

/**
 * Resolve backend actor tokens (`SYSTEM`, `WORKER`, `PLATFORM`, `LEARNER`, `VNPAY_IPN`,
 * `VNPAY_QUERYDR`) into localized labels while preserving genuine usernames.
 */
export function resolveOrderActorLabel(
  actor: string | null | undefined,
  actorType: string | null | undefined,
  tHistory: OrderActorTranslator,
): ResolvedActorLabel {
  const trimmed = actor?.trim() ?? "";
  if (trimmed && Object.hasOwn(KNOWN_ACTOR_KEYS, trimmed)) {
    return {
      label: tHistory(KNOWN_ACTOR_KEYS[trimmed]!),
      isRawUsername: false,
    };
  }
  if (!trimmed) {
    if (actorType === "SYSTEM") {
      return { label: tHistory("actorSystem"), isRawUsername: false };
    }
    if (actorType === "PLATFORM") {
      return { label: tHistory("actorPlatform"), isRawUsername: false };
    }
    return { label: tHistory("actorUnknown"), isRawUsername: false };
  }
  return {
    label: trimmed,
    isRawUsername: true,
  };
}
