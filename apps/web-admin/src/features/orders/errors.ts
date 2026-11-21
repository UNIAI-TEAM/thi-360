import type { ApiError, FieldError } from "@thi360/api-client";

import { ApiStatusError } from "@/lib/api/query-client";

export class OrderApiError extends ApiStatusError {
  readonly code?: string;
  readonly fieldErrors: readonly FieldError[];

  constructor(
    status: number,
    message = "",
    code?: string,
    fieldErrors: readonly FieldError[] = [],
  ) {
    super(status, message);
    this.name = "OrderApiError";
    this.code = code;
    this.fieldErrors = fieldErrors;
  }
}

export function toOrderApiError(status: number, error: unknown): OrderApiError {
  if (typeof error === "object" && error !== null) {
    const body = error as Partial<ApiError>;
    return new OrderApiError(
      status,
      typeof body.message === "string" ? body.message : "",
      typeof body.code === "string" && body.code !== "" ? body.code : undefined,
      Array.isArray(body.fieldErrors) ? body.fieldErrors : [],
    );
  }
  return new OrderApiError(status);
}

export function orderErrorCode(error: unknown): string | undefined {
  if (typeof error !== "object" || error === null || !("code" in error)) return undefined;
  return typeof error.code === "string" && error.code !== "" ? error.code : undefined;
}

export function orderFieldErrors(error: unknown): readonly FieldError[] {
  if (typeof error !== "object" || error === null || !("fieldErrors" in error)) return [];
  return Array.isArray(error.fieldErrors) ? (error.fieldErrors as FieldError[]) : [];
}

export type OrderErrorTranslator = (key: string, values?: Record<string, string | number>) => string;

const CODE_TO_KEY: Record<string, string> = {
  IDEMPOTENCY_KEY_REQUIRED: "IDEMPOTENCY_KEY_REQUIRED",
  IDEMPOTENCY_KEY_REUSED: "IDEMPOTENCY_KEY_REUSED",
  INTERNAL_ERROR: "INTERNAL_ERROR",
  INVALID_TIME_RANGE: "INVALID_TIME_RANGE",
  LATE_SUCCESS_ALREADY_RESOLVED: "LATE_SUCCESS_ALREADY_RESOLVED",
  ORDER_NOT_FOUND: "ORDER_NOT_FOUND",
  ORDER_PAYMENT_NOT_FOUND: "ORDER_PAYMENT_NOT_FOUND",
  ORDER_RECOVERY_NOT_ALLOWED: "ORDER_RECOVERY_NOT_ALLOWED",
  RATE_LIMITED: "RATE_LIMITED",
  REFUND_ACTIVE_EXISTS: "REFUND_ACTIVE_EXISTS",
  REFUND_AMOUNT_EXCEEDED: "REFUND_AMOUNT_EXCEEDED",
  REFUND_EXTERNAL_REF_REQUIRED: "REFUND_EXTERNAL_REF_REQUIRED",
  REFUND_MANUAL_REQUIRES_FAILED_API: "REFUND_MANUAL_REQUIRES_FAILED_API",
  REFUND_NOT_FOUND: "REFUND_NOT_FOUND",
  REFUND_NOT_UNKNOWN: "REFUND_NOT_UNKNOWN",
  REFUND_PAYMENT_NOT_SUCCEEDED: "REFUND_PAYMENT_NOT_SUCCEEDED",
  REFUND_QUERY_NOT_ALLOWED: "REFUND_QUERY_NOT_ALLOWED",
  REFUND_SYSTEM_ERROR_NOT_VERIFIED: "REFUND_SYSTEM_ERROR_NOT_VERIFIED",
  VALIDATION_FAILED: "VALIDATION_FAILED",
};

/**
 * Resolve a localized UI message for any order/refund API or network failure.
 * Never displays raw backend `error.message`.
 */
export function resolveOrderErrorMessage(error: unknown, tErrors: OrderErrorTranslator): string {
  const code = orderErrorCode(error);
  if (code && code in CODE_TO_KEY) {
    return tErrors(CODE_TO_KEY[code]!);
  }
  if (error instanceof ApiStatusError) {
    if (error.status === 403) return tErrors("FORBIDDEN");
    if (error.status === 404) return tErrors("NOT_FOUND");
    if (error.status === 409) return tErrors("CONFLICT");
    if (error.status === 422 || error.status === 400) return tErrors("VALIDATION_FAILED");
    if (error.status === 429) return tErrors("RATE_LIMITED");
    if (error.status >= 500) return tErrors("INTERNAL_ERROR");
    return tErrors("GENERIC");
  }
  if (error instanceof TypeError) {
    return tErrors("NETWORK");
  }
  return tErrors("GENERIC");
}
