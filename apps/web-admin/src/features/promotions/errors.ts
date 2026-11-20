import type { ApiError, FieldError } from "@thi360/api-client";

import { ApiStatusError } from "@/lib/api/query-client";

import type { ProductGoneResponse } from "./types";

export class PromotionApiError extends ApiStatusError {
  readonly code?: string;
  readonly fieldErrors: readonly FieldError[];
  readonly product?: ProductGoneResponse["product"];

  constructor(
    status: number,
    message = "",
    code?: string,
    fieldErrors: readonly FieldError[] = [],
    product?: ProductGoneResponse["product"],
  ) {
    super(status, message);
    this.name = "PromotionApiError";
    this.code = code;
    this.fieldErrors = fieldErrors;
    this.product = product;
  }
}

export function toPromotionApiError(status: number, error: unknown): PromotionApiError {
  if (typeof error === "object" && error !== null) {
    const body = error as Partial<ApiError & ProductGoneResponse>;
    return new PromotionApiError(
      status,
      typeof body.message === "string" ? body.message : "",
      typeof body.code === "string" && body.code !== "" ? body.code : undefined,
      Array.isArray(body.fieldErrors) ? body.fieldErrors : [],
      body.product,
    );
  }
  return new PromotionApiError(status);
}

export function promotionErrorCode(error: unknown): string | undefined {
  if (typeof error !== "object" || error === null || !("code" in error)) return undefined;
  return typeof error.code === "string" && error.code !== "" ? error.code : undefined;
}

export function promotionFieldErrors(error: unknown): readonly FieldError[] {
  if (typeof error !== "object" || error === null || !("fieldErrors" in error)) return [];
  return Array.isArray(error.fieldErrors) ? (error.fieldErrors as FieldError[]) : [];
}

export function isConflictStatus(error: unknown, expectedCode?: string): boolean {
  if (!(error instanceof ApiStatusError)) return false;
  if (expectedCode && promotionErrorCode(error) === expectedCode) return true;
  return error.status === 412 && !expectedCode;
}

export type PromotionErrorTranslator = (key: string, values?: Record<string, string | number>) => string;

const CODE_TO_KEY: Record<string, string> = {
  INTERNAL_ERROR: "INTERNAL_ERROR",
  PRODUCT_DISCONTINUED: "PRODUCT_GONE",
  PROMOTION_ENDED: "PROMOTION_ENDED",
  PROMOTION_ENDS_AT_PAST: "PROMOTION_ENDS_AT_PAST",
  PROMOTION_IN_USE: "PROMOTION_IN_USE",
  PROMOTION_SCOPE_INVALID: "PROMOTION_SCOPE_INVALID",
  RATE_LIMITED: "RATE_LIMITED",
  VALIDATION_FAILED: "VALIDATION_FAILED",
  VERSION_CONFLICT: "VERSION_CONFLICT",
  VOUCHER_CODE_TAKEN: "VOUCHER_CODE_TAKEN",
};

/**
 * Resolve a localized UI message for any API or network failure.
 * Never renders raw server `error.message` (R1-09).
 */
export function resolvePromotionErrorMessage(
  error: unknown,
  tErrors: PromotionErrorTranslator,
  options?: { usedCount?: number },
): string {
  const code = promotionErrorCode(error);
  if (code === "PROMOTION_TOTAL_LIMIT_BELOW_USED") {
    return typeof options?.usedCount === "number"
      ? tErrors("PROMOTION_TOTAL_LIMIT_BELOW_USED", { used: options.usedCount })
      : tErrors("PROMOTION_TOTAL_LIMIT_BELOW_USED_GENERIC");
  }
  if (code && code in CODE_TO_KEY) {
    return tErrors(CODE_TO_KEY[code]!);
  }
  if (error instanceof ApiStatusError) {
    if (error.status === 410) return tErrors("PRODUCT_GONE");
    if (error.status === 412) return tErrors("VERSION_CONFLICT");
    if (error.status === 409) return tErrors("CONFLICT");
    if (error.status === 403) return tErrors("FORBIDDEN");
    if (error.status === 404) return tErrors("NOT_FOUND");
    if (error.status === 422 || error.status === 400) return tErrors("VALIDATION_FAILED");
    if (error.status === 429) return tErrors("RATE_LIMITED");
    if (error.status >= 500) return tErrors("INTERNAL_ERROR");
    return tErrors("GENERIC");
  }
  if (error instanceof TypeError || error instanceof Error) {
    return tErrors("NETWORK");
  }
  return tErrors("GENERIC");
}
