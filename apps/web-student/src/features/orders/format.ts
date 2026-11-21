import type { StatusTone } from "@thi360/ui";
import { APP_TIME_ZONE } from "@thi360/web-kit/preferences";
import { OrderConflictError, ProductGoneError } from "@/features/checkout/api";
import { readSupportContact } from "@/features/storefront/support";
import { ApiStatusError } from "@/lib/api/client";
import type { OrderPaymentView, OrderSummary } from "./api";

const DATE_TIME_PARTS = new Intl.DateTimeFormat("en-GB", {
  day: "2-digit",
  month: "2-digit",
  year: "numeric",
  hour: "2-digit",
  minute: "2-digit",
  hourCycle: "h23",
  timeZone: APP_TIME_ZONE,
});

const TIME_ONLY = new Intl.DateTimeFormat("en-GB", {
  hour: "2-digit",
  minute: "2-digit",
  hourCycle: "h23",
  timeZone: APP_TIME_ZONE,
});

/** Format ISO timestamp to "dd/MM/yyyy HH:mm" in Asia/Ho_Chi_Minh. */
export function formatDateTime(iso: string): string {
  const date = new Date(iso);
  if (Number.isNaN(date.getTime())) return iso;
  const parts = Object.fromEntries(DATE_TIME_PARTS.formatToParts(date).map((part) => [part.type, part.value]));
  return `${parts.day}/${parts.month}/${parts.year} ${parts.hour}:${parts.minute}`;
}

/** Format ISO timestamp to "HH:mm" in Asia/Ho_Chi_Minh. */
export function formatTimeOnly(iso: string): string {
  const date = new Date(iso);
  if (Number.isNaN(date.getTime())) return iso;
  return TIME_ONLY.format(date);
}

/**
 * Only reset an Idempotency-Key on deterministic client rejections (409/410/422/etc.); keep the same key across
 * network, timeout (408), rate-limit (429), and 5xx failures so retries replay safely (TM §8.3, AC-11).
 */
export function shouldResetIdempotencyKey(error: unknown): boolean {
  return (
    error instanceof ProductGoneError ||
    error instanceof OrderConflictError ||
    (error instanceof ApiStatusError &&
      error.status >= 400 &&
      error.status < 500 &&
      error.status !== 408 &&
      error.status !== 429)
  );
}

export const ORDER_STATE_TONE: Record<OrderSummary["state"], StatusTone> = {
  PENDING_PAYMENT: "warning",
  PAID: "warning",
  FULFILLED: "success",
  EXPIRED: "neutral",
  REPLACED: "neutral",
  CANCELLED: "neutral",
  REFUNDED: "info",
};

export const PAYMENT_STATE_TONE: Record<OrderPaymentView["state"], StatusTone> = {
  CREATED: "neutral",
  SUCCEEDED: "success",
  FAILED: "danger",
  UNKNOWN: "warning",
};

export type OrderErrorKey =
  | "ORDER_EXPIRED"
  | "ORDER_NOT_FOUND"
  | "ORDER_NOT_PENDING"
  | "PAYMENT_ATTEMPTS_EXCEEDED"
  | "PRODUCT_DISCONTINUED"
  | "PRODUCT_NOT_FOUND"
  | "RATE_LIMITED"
  | "generic";

export function mapOrderActionError(error: unknown): OrderErrorKey {
  if (error instanceof ApiStatusError) {
    if (error.status === 429 || error.code === "RATE_LIMITED") return "RATE_LIMITED";
    if (error.status === 410 || error.code === "PRODUCT_DISCONTINUED") return "PRODUCT_DISCONTINUED";
    if (error.status === 404 && error.code === "PRODUCT_NOT_FOUND") return "PRODUCT_NOT_FOUND";
    if (error.status === 404 || error.code === "ORDER_NOT_FOUND") return "ORDER_NOT_FOUND";
    if (
      error.code === "ORDER_EXPIRED" ||
      error.code === "ORDER_NOT_PENDING" ||
      error.code === "PAYMENT_ATTEMPTS_EXCEEDED"
    ) {
      return error.code;
    }
  }
  return "generic";
}

export type HistoryActorKey = "LEARNER" | "SYSTEM" | "VNPAY_IPN" | "ADMIN" | "unknown";

const KNOWN_HISTORY_ACTORS = new Set<string>(["LEARNER", "SYSTEM", "VNPAY_IPN", "ADMIN"]);

export function mapHistoryActorKey(actor: string): HistoryActorKey {
  const normalized = actor.trim().toUpperCase();
  return KNOWN_HISTORY_ACTORS.has(normalized) ? (normalized as HistoryActorKey) : "unknown";
}

export type HistoryReasonKey =
  | "ORDER_CREATED"
  | "FREE_ORDER_PAID"
  | "PAYMENT_ATTEMPT_CREATED"
  | "REUSE_PENDING_PAYMENT"
  | "REPLACED_BY"
  | "CANCELLED_BY_USER"
  | "PAYMENT_SUCCEEDED"
  | "FULFILLED"
  | "EXCESS_AFTER_COUNTED"
  | "AUTO_EXCESS_AFTER_COUNTED"
  | "LATE_SUCCESS_PENDING_REVIEW"
  | "DOUBLE_CHARGE_REVIEW"
  | "DOUBLE_CHARGE_REPLACED_ORDER"
  | "LATE_PAYMENT_RECLAIMED"
  | "LATE_PAYMENT_REPLACED_RECLAIMED"
  | "LATE_PAYMENT_REPLACED_DOUBLE_CHARGE"
  | "EXPIRED"
  | "REFUNDED"
  | "unknown";

const KNOWN_HISTORY_REASONS = new Set<string>([
  "ORDER_CREATED",
  "FREE_ORDER_PAID",
  "PAYMENT_ATTEMPT_CREATED",
  "REUSE_PENDING_PAYMENT",
  "REPLACED_BY",
  "CANCELLED_BY_USER",
  "PAYMENT_SUCCEEDED",
  "FULFILLED",
  "EXCESS_AFTER_COUNTED",
  "AUTO_EXCESS_AFTER_COUNTED",
  "LATE_SUCCESS_PENDING_REVIEW",
  "DOUBLE_CHARGE_REVIEW",
  "DOUBLE_CHARGE_REPLACED_ORDER",
  "LATE_PAYMENT_RECLAIMED",
  "LATE_PAYMENT_REPLACED_RECLAIMED",
  "LATE_PAYMENT_REPLACED_DOUBLE_CHARGE",
  "EXPIRED",
  "REFUNDED",
]);

export function parseHistoryReason(rawReason: string): { key: HistoryReasonKey; ref: string } {
  const trimmed = rawReason.trim();
  const colonIdx = trimmed.indexOf(":");
  const prefix = (colonIdx >= 0 ? trimmed.slice(0, colonIdx) : trimmed).trim().toUpperCase();
  const ref = colonIdx >= 0 ? trimmed.slice(colonIdx + 1).trim() : "";
  if (KNOWN_HISTORY_REASONS.has(prefix)) {
    return { key: prefix as HistoryReasonKey, ref };
  }
  return { key: "unknown", ref };
}

export type VnpayReasonKey =
  | "07"
  | "09"
  | "10"
  | "11"
  | "12"
  | "13"
  | "24"
  | "51"
  | "65"
  | "75"
  | "79"
  | "99"
  | "generic";

const KNOWN_VNPAY_CODES = new Set<string>(["07", "09", "10", "11", "12", "13", "24", "51", "65", "75", "79", "99"]);

export function mapVnpayResponseCode(responseCode: string | null | undefined): VnpayReasonKey {
  const code = responseCode?.trim() ?? "";
  return KNOWN_VNPAY_CODES.has(code) ? (code as VnpayReasonKey) : "generic";
}

export function formatOrderSupportSummary(): string {
  const contact = readSupportContact();
  const channels = [contact.phone, contact.email].filter(Boolean).join(" · ");
  if (!channels) return contact.hours;
  return contact.hours ? `${channels} (${contact.hours})` : channels;
}

export interface BenefitsSnapshot {
  aiExplanation: boolean;
  strengthWeakness: boolean;
  practice: boolean;
  note?: string;
}

export function readBenefitsSnapshot(raw: unknown): BenefitsSnapshot {
  const record = typeof raw === "object" && raw !== null ? (raw as Record<string, unknown>) : {};
  return {
    aiExplanation: record.aiExplanation === true,
    strengthWeakness: record.strengthWeakness === true,
    practice: record.practice === true,
    note: typeof record.note === "string" && record.note.trim().length > 0 ? record.note.trim() : undefined,
  };
}
