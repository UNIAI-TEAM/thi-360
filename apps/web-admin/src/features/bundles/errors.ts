import type { ApiError, FieldError } from "@thi360/api-client";

import { ApiStatusError } from "@/lib/api/query-client";

export class BundleApiError extends ApiStatusError {
  readonly code?: string;
  readonly fieldErrors: readonly FieldError[];

  constructor(
    status: number,
    message = "",
    code?: string,
    fieldErrors: readonly FieldError[] = [],
  ) {
    super(status, message);
    this.name = "BundleApiError";
    this.code = code;
    this.fieldErrors = fieldErrors;
  }
}

export function toBundleApiError(status: number, error: unknown): BundleApiError {
  if (typeof error === "object" && error !== null) {
    const body = error as Partial<ApiError>;
    return new BundleApiError(
      status,
      typeof body.message === "string" ? body.message : "",
      typeof body.code === "string" && body.code !== "" ? body.code : undefined,
      Array.isArray(body.fieldErrors) ? body.fieldErrors : [],
    );
  }
  return new BundleApiError(status);
}

export function bundleErrorCode(error: unknown): string | undefined {
  if (typeof error !== "object" || error === null || !("code" in error)) return undefined;
  return typeof error.code === "string" && error.code !== "" ? error.code : undefined;
}

export function bundleFieldErrors(error: unknown): readonly FieldError[] {
  if (typeof error !== "object" || error === null || !("fieldErrors" in error)) return [];
  return Array.isArray(error.fieldErrors) ? (error.fieldErrors as FieldError[]) : [];
}

export function isBundleRevisionConflict(error: unknown): boolean {
  if (error instanceof ApiStatusError && error.status === 412) return true;
  const code = bundleErrorCode(error);
  return code === "VERSION_CONFLICT" || code === "BUNDLE_REVISION_CONFLICT";
}

export type BundleErrorTranslator = (key: string, values?: Record<string, string | number>) => string;

const CODE_TO_KEY: Record<string, string> = {
  BUNDLE_ALREADY_DISCONTINUED: "BUNDLE_ALREADY_DISCONTINUED",
  BUNDLE_ATTEMPTS_INVALID: "BUNDLE_ATTEMPTS_INVALID",
  BUNDLE_CODE_TAKEN: "BUNDLE_CODE_TAKEN",
  BUNDLE_DESCRIPTION_REQUIRED: "BUNDLE_DESCRIPTION_REQUIRED",
  BUNDLE_EXAM_KIND_INVALID: "BUNDLE_EXAM_KIND_INVALID",
  BUNDLE_ITEMS_COUNT_INVALID: "BUNDLE_ITEMS_COUNT_INVALID",
  BUNDLE_ITEM_DUPLICATE: "BUNDLE_ITEM_DUPLICATE",
  BUNDLE_ITEM_FULL_SCOPE_NOT_ALLOWED: "BUNDLE_ITEM_FULL_SCOPE_NOT_ALLOWED",
  BUNDLE_ITEM_INVALID_EXAM: "BUNDLE_ITEM_INVALID_EXAM",
  BUNDLE_ITEM_KIND_MISMATCH: "BUNDLE_ITEM_KIND_MISMATCH",
  BUNDLE_ITEM_NOT_ON_SALE: "BUNDLE_ITEM_NOT_ON_SALE",
  BUNDLE_LIST_PRICE_NEGATIVE: "BUNDLE_LIST_PRICE_NEGATIVE",
  BUNDLE_NOT_FOUND: "BUNDLE_NOT_FOUND",
  BUNDLE_NOT_PUBLISHED: "BUNDLE_NOT_PUBLISHED",
  BUNDLE_RESULT_VIEW_INVALID: "BUNDLE_RESULT_VIEW_INVALID",
  BUNDLE_REVISION_CONFLICT: "VERSION_CONFLICT",
  INTERNAL_ERROR: "INTERNAL_ERROR",
  LISTING_PRICE_BELOW_MIN: "LISTING_PRICE_BELOW_MIN",
  LISTING_PRICE_MISSING: "LISTING_PRICE_MISSING",
  LISTING_PRICE_ZERO: "LISTING_PRICE_ZERO",
  RATE_LIMITED: "RATE_LIMITED",
  VALIDATION_FAILED: "VALIDATION_FAILED",
  VERSION_CONFLICT: "VERSION_CONFLICT",
};

/**
 * Resolve a localized UI error message from backend error code or HTTP status.
 * Never outputs raw server `error.message`.
 */
export function resolveBundleErrorMessage(
  error: unknown,
  tErrors: BundleErrorTranslator,
): string {
  const code = bundleErrorCode(error);
  if (code && Object.hasOwn(CODE_TO_KEY, code)) {
    return tErrors(CODE_TO_KEY[code]!);
  }
  if (error instanceof ApiStatusError) {
    if (error.status === 412) return tErrors("VERSION_CONFLICT");
    if (error.status === 409) return tErrors("CONFLICT");
    if (error.status === 403) return tErrors("FORBIDDEN");
    if (error.status === 404) return tErrors("BUNDLE_NOT_FOUND");
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
