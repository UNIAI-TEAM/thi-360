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
  "AUTO_EXCESS_AFTER_COUNTED",
  "DOUBLE_CHARGE_REPLACED_ORDER",
  "FULFILLED",
  "LATE_SUCCESS_ACCEPTED",
  "LATE_SUCCESS_REJECTED",
  "REFUNDED",
  "REFUND_LATE_SUCCESS_AFTER_FAILED",
  "REFUND_QUERY_SUCCEEDED_AFTER_FAILED",
  "REFUND_REJECTED_09",
  "REFUND_SUCCEEDED_QUERY_MISMATCH",
]);

export interface FormattedReasonText {
  localized: string | null;
  rawText: string | null;
}

/**
 * Backend writes structured reason prefixes like `DOUBLE_CHARGE_REPLACED_ORDER:<ref>` or
 * `LATE_SUCCESS_ACCEPTED:<txnRef>:<evidenceRef>` alongside free-text admin reasons.
 * Map known prefixes via i18n and mark free-text reasons as raw content for `data-exam-content`.
 */
export function formatStructuredOrderReason(
  reason: string | null | undefined,
  tReasons: (key: string, values?: Record<string, string>) => string,
): FormattedReasonText {
  const trimmed = reason?.trim() ?? "";
  if (!trimmed) return { localized: null, rawText: null };
  const sepIndex = trimmed.indexOf(":");
  const code = sepIndex === -1 ? trimmed : trimmed.slice(0, sepIndex);
  if (KNOWN_HISTORY_REASON_CODES.has(code)) {
    const detail = sepIndex === -1 ? trimmed : trimmed.slice(sepIndex + 1);
    return {
      localized: tReasons(code, { detail }),
      rawText: null,
    };
  }
  return {
    localized: null,
    rawText: trimmed,
  };
}
