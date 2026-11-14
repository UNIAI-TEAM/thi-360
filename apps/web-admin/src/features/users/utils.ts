import type { StatusTone } from "@thi360/ui";
import { getErrorStatus } from "@/lib/api/query-client";
import { UsersApiError } from "./api";

export type UserDisplayStatus =
  | "ACTIVE"
  | "INACTIVE"
  | "NEVER_LOGGED_IN"
  | "DELETED"
  | "PROVISIONING";

export interface UserDisplayStatusResult {
  status: UserDisplayStatus;
  tone: StatusTone;
}

export function getUserDisplayStatus(user: {
  status: "PROVISIONING" | "ACTIVE" | "INACTIVE";
  lastLoginAt?: string;
  deletedAt?: string;
}): UserDisplayStatusResult {
  if (user.deletedAt) {
    return { status: "DELETED", tone: "neutral" };
  }
  if (user.status === "INACTIVE") {
    return { status: "INACTIVE", tone: "danger" };
  }
  if (user.status === "ACTIVE" && !user.lastLoginAt) {
    return { status: "NEVER_LOGGED_IN", tone: "warning" };
  }
  if (user.status === "ACTIVE") {
    return { status: "ACTIVE", tone: "success" };
  }
  return { status: "PROVISIONING", tone: "neutral" };
}

export function formatUserDateTime(iso?: string): string {
  if (!iso) return "—";
  const date = new Date(iso);
  if (Number.isNaN(date.getTime())) return "—";
  const dd = String(date.getDate()).padStart(2, "0");
  const mm = String(date.getMonth() + 1).padStart(2, "0");
  const yyyy = date.getFullYear();
  const hh = String(date.getHours()).padStart(2, "0");
  const min = String(date.getMinutes()).padStart(2, "0");
  return `${dd}/${mm}/${yyyy} ${hh}:${min}`;
}

export const KNOWN_USER_ERROR_CODES = [
  "ALREADY_ACTIVE",
  "ALREADY_INACTIVE",
  "BULK_LIMIT_EXCEEDED",
  "CLASS_NOT_SUPPORTED",
  "EMAIL_INVALID",
  "EMAIL_TAKEN",
  "FORBIDDEN",
  "IDEMPOTENCY_KEY_REQUIRED",
  "IDEMPOTENCY_KEY_REUSED",
  "IDENTITY_PROVIDER_ERROR",
  "INTERNAL_ERROR",
  "NOTBLANK",
  "NOTEMPTY",
  "NOT_FOUND",
  "PASSWORD_DIGIT",
  "PASSWORD_LENGTH",
  "PASSWORD_LOWERCASE",
  "PASSWORD_SAME_AS_USERNAME",
  "PASSWORD_SPECIAL",
  "PASSWORD_UPPERCASE",
  "PASSWORD_WEAK",
  "PRIVILEGE_ESCALATION",
  "RATE_LIMITED",
  "REASON_REQUIRED",
  "ROLE_INACTIVE",
  "SELF_DEACTIVATE",
  "SELF_DELETE",
  "SIZE",
  "USERNAME_INVALID",
  "USERNAME_REQUIRED",
  "USERNAME_RESERVED",
  "USERNAME_TAKEN",
  "USER_HAS_TRANSACTIONS",
  "USER_NOT_AVAILABLE",
  "VALIDATION_FAILED",
  "VERSION_CONFLICT",
] as const;

export type KnownUserErrorCode = (typeof KNOWN_USER_ERROR_CODES)[number];

const KNOWN_CODE_MAP: Record<KnownUserErrorCode, true> = {
  ALREADY_ACTIVE: true,
  ALREADY_INACTIVE: true,
  BULK_LIMIT_EXCEEDED: true,
  CLASS_NOT_SUPPORTED: true,
  EMAIL_INVALID: true,
  EMAIL_TAKEN: true,
  FORBIDDEN: true,
  IDEMPOTENCY_KEY_REQUIRED: true,
  IDEMPOTENCY_KEY_REUSED: true,
  IDENTITY_PROVIDER_ERROR: true,
  INTERNAL_ERROR: true,
  NOTBLANK: true,
  NOTEMPTY: true,
  NOT_FOUND: true,
  PASSWORD_DIGIT: true,
  PASSWORD_LENGTH: true,
  PASSWORD_LOWERCASE: true,
  PASSWORD_SAME_AS_USERNAME: true,
  PASSWORD_SPECIAL: true,
  PASSWORD_UPPERCASE: true,
  PASSWORD_WEAK: true,
  PRIVILEGE_ESCALATION: true,
  RATE_LIMITED: true,
  REASON_REQUIRED: true,
  ROLE_INACTIVE: true,
  SELF_DEACTIVATE: true,
  SELF_DELETE: true,
  SIZE: true,
  USERNAME_INVALID: true,
  USERNAME_REQUIRED: true,
  USERNAME_RESERVED: true,
  USERNAME_TAKEN: true,
  USER_HAS_TRANSACTIONS: true,
  USER_NOT_AVAILABLE: true,
  VALIDATION_FAILED: true,
  VERSION_CONFLICT: true,
};

export function mapUserErrorCodeToKey(code?: string): KnownUserErrorCode | null {
  if (!code) return null;
  return Object.hasOwn(KNOWN_CODE_MAP, code) ? (code as KnownUserErrorCode) : null;
}

export function resolveUsersErrorMessage(
  error: unknown,
  translateCode: (code: KnownUserErrorCode) => string,
  fallbackMessage: string,
): string {
  if (error instanceof UsersApiError) {
    if (error.code === "VALIDATION_FAILED" || error.code === "PASSWORD_WEAK") {
      const firstFieldCode = mapUserErrorCodeToKey(error.fieldErrors[0]?.code);
      if (firstFieldCode) return translateCode(firstFieldCode);
    }
    const mapped = mapUserErrorCodeToKey(error.code);
    if (mapped) return translateCode(mapped);
    const firstFieldCode = mapUserErrorCodeToKey(error.fieldErrors[0]?.code);
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
