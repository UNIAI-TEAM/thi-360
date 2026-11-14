import { getErrorStatus } from "@/lib/api/query-client";
import { ClassesApiError } from "./api";

export const KNOWN_CLASS_ERROR_CODES = [
  "CLASS_ALREADY_MEMBER",
  "CLASS_CODE_INVALID",
  "CLASS_CODE_TAKEN",
  "CLASS_INACTIVE",
  "CLASS_TRANSFER_SAME_CLASS",
  "FORBIDDEN",
  "INTERNAL_ERROR",
  "NOTBLANK",
  "NOTEMPTY",
  "NOT_BLANK",
  "NOT_FOUND",
  "RATE_LIMITED",
  "REASON_REQUIRED",
  "SIZE",
  "USER_NOT_AVAILABLE",
  "VALIDATION_FAILED",
  "VERSION_CONFLICT",
] as const;

export type KnownClassErrorCode = (typeof KNOWN_CLASS_ERROR_CODES)[number];

const KNOWN_CODE_MAP: Record<KnownClassErrorCode, true> = {
  CLASS_ALREADY_MEMBER: true,
  CLASS_CODE_INVALID: true,
  CLASS_CODE_TAKEN: true,
  CLASS_INACTIVE: true,
  CLASS_TRANSFER_SAME_CLASS: true,
  FORBIDDEN: true,
  INTERNAL_ERROR: true,
  NOTBLANK: true,
  NOTEMPTY: true,
  NOT_BLANK: true,
  NOT_FOUND: true,
  RATE_LIMITED: true,
  REASON_REQUIRED: true,
  SIZE: true,
  USER_NOT_AVAILABLE: true,
  VALIDATION_FAILED: true,
  VERSION_CONFLICT: true,
};

export function mapClassErrorCodeToKey(code?: string): KnownClassErrorCode | null {
  if (!code) return null;
  return Object.hasOwn(KNOWN_CODE_MAP, code) ? (code as KnownClassErrorCode) : null;
}

export function resolveClassesErrorMessage(
  error: unknown,
  translateCode: (code: KnownClassErrorCode) => string,
  fallbackMessage: string,
): string {
  if (error instanceof ClassesApiError) {
    if (error.code === "VALIDATION_FAILED") {
      const firstFieldCode = mapClassErrorCodeToKey(error.fieldErrors[0]?.code);
      if (firstFieldCode) return translateCode(firstFieldCode);
    }
    const mapped = mapClassErrorCodeToKey(error.code);
    if (mapped) return translateCode(mapped);
    const firstFieldCode = mapClassErrorCodeToKey(error.fieldErrors[0]?.code);
    if (firstFieldCode) return translateCode(firstFieldCode);
  }
  const status = getErrorStatus(error);
  if (status === 400) return translateCode("VALIDATION_FAILED");
  if (status === 403) return translateCode("FORBIDDEN");
  if (status === 404) return translateCode("NOT_FOUND");
  if (status === 429) return translateCode("RATE_LIMITED");
  if (status !== null && status >= 500) return translateCode("INTERNAL_ERROR");
  return fallbackMessage;
}

function pad2(n: number): string {
  return String(n).padStart(2, "0");
}

/** Định dạng ngày chuẩn dd/MM/yyyy (DESIGN.md mục 8). */
export function formatClassDate(iso?: string): string {
  if (!iso) return "—";
  const date = new Date(iso);
  if (Number.isNaN(date.getTime())) return "—";
  return `${pad2(date.getDate())}/${pad2(date.getMonth() + 1)}/${date.getFullYear()}`;
}

/** Định dạng ngày giờ dd/MM/yyyy HH:mm. */
export function formatClassDateTime(iso?: string): string {
  if (!iso) return "—";
  const date = new Date(iso);
  if (Number.isNaN(date.getTime())) return "—";
  return `${pad2(date.getDate())}/${pad2(date.getMonth() + 1)}/${date.getFullYear()} ${pad2(date.getHours())}:${pad2(date.getMinutes())}`;
}
