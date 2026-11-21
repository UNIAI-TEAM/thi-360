import type { ApiError, FieldError } from "@thi360/api-client";
import { ApiStatusError } from "@/lib/api/query-client";

export const ATTEMPT_ERROR_CODES = [
  "ATTEMPT_NOT_GRADING_ERROR",
  "ATTEMPT_FAULT_NOT_ALLOWED",
  "ATTEMPT_ALREADY_FAULTED",
  "ATTEMPT_NOT_FAULTED",
  "ALREADY_COMPENSATED",
  "GRANT_REFUNDED",
  "IDEMPOTENCY_KEY_REQUIRED",
  "IDEMPOTENCY_KEY_REUSED",
  "INVALID_DATE_FILTER",
  "VALIDATION_FAILED",
] as const;

export type AttemptErrorCode = (typeof ATTEMPT_ERROR_CODES)[number];

const KNOWN_ERROR_CODE_SET = new Set<string>([
  ...ATTEMPT_ERROR_CODES,
  "INTERNAL_ERROR",
  "RATE_LIMITED",
  "FORBIDDEN",
  "NOT_FOUND",
  "CONFLICT",
  "NETWORK",
  "GENERIC",
]);

export class AttemptApiError extends ApiStatusError {
  readonly code?: string;
  readonly fieldErrors: readonly FieldError[];

  constructor(
    status: number,
    message = "",
    code?: string,
    fieldErrors: readonly FieldError[] = [],
  ) {
    super(status, message);
    this.name = "AttemptApiError";
    this.code = code;
    this.fieldErrors = fieldErrors;
  }
}

export function toAttemptApiError(status: number, error: unknown): AttemptApiError {
  if (typeof error === "object" && error !== null) {
    const body = error as Partial<ApiError>;
    return new AttemptApiError(
      status,
      typeof body.message === "string" ? body.message : "",
      typeof body.code === "string" && body.code !== "" ? body.code : undefined,
      Array.isArray(body.fieldErrors) ? body.fieldErrors : [],
    );
  }
  return new AttemptApiError(status);
}

export function attemptErrorCode(error: unknown): string | undefined {
  if (typeof error !== "object" || error === null || !("code" in error)) return undefined;
  return typeof error.code === "string" && error.code !== "" ? error.code : undefined;
}

export type AttemptErrorTranslator = (key: string, values?: Record<string, string | number>) => string;

/**
 * Resolve a localized UI error message for admin attempt API/network failures.
 * Never surfaces raw backend `error.message`.
 */
export function resolveAttemptErrorMessage(
  error: unknown,
  tErrors: AttemptErrorTranslator,
): string {
  const code = attemptErrorCode(error);
  if (code && KNOWN_ERROR_CODE_SET.has(code)) {
    return tErrors(code);
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
