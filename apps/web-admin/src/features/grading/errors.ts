import type { ApiError, FieldError } from "@thi360/api-client";
import { ApiStatusError } from "@/lib/api/query-client";
import type { GradingClaimedResponse } from "./types";

export const GRADING_ERROR_CODES = [
  "GRADING_CLAIMED",
  "GRADING_NOT_CLAIMED",
  "ESSAY_ALREADY_FINALIZED",
  "ESSAY_NOT_FINALIZED",
  "ESSAY_NOT_GRADABLE",
  "GRADING_ITEM_NOT_FOUND",
  "VERSION_CONFLICT",
  "ESSAY_POINTS_OUT_OF_RANGE",
  "ESSAY_CRITERIA_INVALID",
  "ESSAY_CRITERIA_SUM_MISMATCH",
  "INVALID_DATE_FILTER",
  "VALIDATION_FAILED",
] as const;

export type GradingErrorCode = (typeof GRADING_ERROR_CODES)[number];

const KNOWN_ERROR_CODE_SET = new Set<string>(GRADING_ERROR_CODE_KEYS());

function GRADING_ERROR_CODE_KEYS(): readonly string[] {
  return [
    ...GRADING_ERROR_CODES,
    "INTERNAL_ERROR",
    "RATE_LIMITED",
    "FORBIDDEN",
    "NOT_FOUND",
    "CONFLICT",
    "NETWORK",
    "GENERIC",
  ];
}

export class GradingApiError extends ApiStatusError {
  readonly code?: string;
  readonly fieldErrors: readonly FieldError[];
  readonly claimedById?: string;
  readonly claimedByName?: string;
  readonly claimedAt?: string;
  readonly lastActivityAt?: string;

  constructor(
    status: number,
    message = "",
    code?: string,
    fieldErrors: readonly FieldError[] = [],
    claimInfo?: {
      claimedById?: string;
      claimedByName?: string;
      claimedAt?: string;
      lastActivityAt?: string;
    },
  ) {
    super(status, message);
    this.name = "GradingApiError";
    this.code = code;
    this.fieldErrors = fieldErrors;
    this.claimedById = claimInfo?.claimedById;
    this.claimedByName = claimInfo?.claimedByName;
    this.claimedAt = claimInfo?.claimedAt;
    this.lastActivityAt = claimInfo?.lastActivityAt;
  }
}

export function toGradingApiError(status: number, error: unknown): GradingApiError {
  if (typeof error === "object" && error !== null) {
    const body = error as Partial<ApiError & GradingClaimedResponse>;
    return new GradingApiError(
      status,
      typeof body.message === "string" ? body.message : "",
      typeof body.code === "string" && body.code !== "" ? body.code : undefined,
      Array.isArray(body.fieldErrors) ? body.fieldErrors : [],
      {
        claimedById: typeof body.claimedById === "string" ? body.claimedById : undefined,
        claimedByName: typeof body.claimedByName === "string" && body.claimedByName.trim() !== ""
          ? body.claimedByName
          : undefined,
        claimedAt: typeof body.claimedAt === "string" ? body.claimedAt : undefined,
        lastActivityAt: typeof body.lastActivityAt === "string" ? body.lastActivityAt : undefined,
      },
    );
  }
  return new GradingApiError(status);
}

export function gradingErrorCode(error: unknown): string | undefined {
  if (typeof error !== "object" || error === null || !("code" in error)) return undefined;
  return typeof error.code === "string" && error.code !== "" ? error.code : undefined;
}

export type GradingErrorTranslator = (key: string, values?: Record<string, string | number>) => string;

/**
 * Resolve a localized UI error message for grading API/network failures.
 * Never surfaces raw backend `error.message` directly except formatted `claimedByName`.
 */
export function resolveGradingErrorMessage(error: unknown, tErrors: GradingErrorTranslator): string {
  const code = gradingErrorCode(error);
  if (code === "GRADING_CLAIMED" && error instanceof GradingApiError && error.claimedByName) {
    return tErrors("GRADING_CLAIMED_BY", { name: error.claimedByName });
  }
  if (code && KNOWN_ERROR_CODE_SET.has(code)) {
    return tErrors(code);
  }
  if (error instanceof ApiStatusError) {
    if (error.status === 403) return tErrors("FORBIDDEN");
    if (error.status === 404) return tErrors("NOT_FOUND");
    if (error.status === 409) return tErrors("CONFLICT");
    if (error.status === 412) return tErrors("VERSION_CONFLICT");
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
