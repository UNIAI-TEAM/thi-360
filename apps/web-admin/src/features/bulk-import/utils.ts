import { getErrorStatus } from "@/lib/api/query-client";
import { BulkImportApiError, type ImportBatchStatus } from "./api";

export const MAX_IMPORT_FILE_BYTES = 5 * 1024 * 1024;

export const KNOWN_IMPORT_API_ERROR_CODES = [
  "FORBIDDEN",
  "IDEMPOTENCY_KEY_REUSED",
  "IMPORT_ALREADY_CONFIRMED",
  "IMPORT_CLASS_PERMISSION_REQUIRED",
  "IMPORT_EMPTY_FILE",
  "IMPORT_FILE_TOO_LARGE",
  "IMPORT_FORMULA_NOT_ALLOWED",
  "IMPORT_HAS_ERRORS",
  "IMPORT_INVALID_FILE",
  "IMPORT_INVALID_HEADER",
  "IMPORT_MACRO_NOT_ALLOWED",
  "IMPORT_RESULT_NOT_READY",
  "IMPORT_ROW_LIMIT_EXCEEDED",
  "IMPORT_SOURCE_GONE",
  "INTERNAL_ERROR",
  "NOT_FOUND",
  "RATE_LIMITED",
  "REQUIRED",
  "VALIDATION_FAILED",
] as const;

export type KnownImportApiErrorCode = (typeof KNOWN_IMPORT_API_ERROR_CODES)[number];

const API_ERROR_CODE_MAP: Record<KnownImportApiErrorCode, true> = {
  FORBIDDEN: true,
  IDEMPOTENCY_KEY_REUSED: true,
  IMPORT_ALREADY_CONFIRMED: true,
  IMPORT_CLASS_PERMISSION_REQUIRED: true,
  IMPORT_EMPTY_FILE: true,
  IMPORT_FILE_TOO_LARGE: true,
  IMPORT_FORMULA_NOT_ALLOWED: true,
  IMPORT_HAS_ERRORS: true,
  IMPORT_INVALID_FILE: true,
  IMPORT_INVALID_HEADER: true,
  IMPORT_MACRO_NOT_ALLOWED: true,
  IMPORT_RESULT_NOT_READY: true,
  IMPORT_ROW_LIMIT_EXCEEDED: true,
  IMPORT_SOURCE_GONE: true,
  INTERNAL_ERROR: true,
  NOT_FOUND: true,
  RATE_LIMITED: true,
  REQUIRED: true,
  VALIDATION_FAILED: true,
};

export const KNOWN_IMPORT_ROW_ERROR_CODES = [
  "CLASS_INACTIVE",
  "CLASS_NOT_FOUND",
  "EMAIL_DUPLICATE_IN_FILE",
  "EMAIL_INVALID",
  "EMAIL_TAKEN",
  "FULL_NAME_TOO_LONG",
  "PASSWORD_WEAK",
  "PHONE_TOO_LONG",
  "REQUIRED",
  "USERNAME_DUPLICATE_IN_FILE",
  "USERNAME_INVALID",
  "USERNAME_TAKEN",
] as const;

export type KnownImportRowErrorCode = (typeof KNOWN_IMPORT_ROW_ERROR_CODES)[number];

const ROW_ERROR_CODE_MAP: Record<KnownImportRowErrorCode, true> = {
  CLASS_INACTIVE: true,
  CLASS_NOT_FOUND: true,
  EMAIL_DUPLICATE_IN_FILE: true,
  EMAIL_INVALID: true,
  EMAIL_TAKEN: true,
  FULL_NAME_TOO_LONG: true,
  PASSWORD_WEAK: true,
  PHONE_TOO_LONG: true,
  REQUIRED: true,
  USERNAME_DUPLICATE_IN_FILE: true,
  USERNAME_INVALID: true,
  USERNAME_TAKEN: true,
};

export const KNOWN_IMPORT_FAILURE_CODES = [
  "CLASS_INACTIVE",
  "CLASS_NOT_FOUND",
  "CONFLICT",
  "EMAIL_TAKEN",
  "FORBIDDEN",
  "IDENTITY_PROVIDER_ERROR",
  "INTERNAL_ERROR",
  "PASSWORD_WEAK",
  "USERNAME_INVALID",
  "USERNAME_RESERVED",
  "USERNAME_TAKEN",
  "VALIDATION_FAILED",
] as const;

export type KnownImportFailureCode = (typeof KNOWN_IMPORT_FAILURE_CODES)[number];

const FAILURE_CODE_MAP: Record<KnownImportFailureCode, true> = {
  CLASS_INACTIVE: true,
  CLASS_NOT_FOUND: true,
  CONFLICT: true,
  EMAIL_TAKEN: true,
  FORBIDDEN: true,
  IDENTITY_PROVIDER_ERROR: true,
  INTERNAL_ERROR: true,
  PASSWORD_WEAK: true,
  USERNAME_INVALID: true,
  USERNAME_RESERVED: true,
  USERNAME_TAKEN: true,
  VALIDATION_FAILED: true,
};

export const IMPORT_COLUMN_KEYS = [
  "USERNAME",
  "PASSWORD",
  "FULL_NAME",
  "EMAIL",
  "PHONE",
  "CLASS_CODE",
] as const;

export type ImportColumnKey = (typeof IMPORT_COLUMN_KEYS)[number];

const COLUMN_SYNONYMS: Record<string, ImportColumnKey> = {
  username: "USERNAME",
  "tên đăng nhập": "USERNAME",
  "ten dang nhap": "USERNAME",
  password: "PASSWORD",
  "mật khẩu": "PASSWORD",
  "mat khau": "PASSWORD",
  fullname: "FULL_NAME",
  "full name": "FULL_NAME",
  full_name: "FULL_NAME",
  "họ và tên": "FULL_NAME",
  "ho va ten": "FULL_NAME",
  "họ tên": "FULL_NAME",
  "ho ten": "FULL_NAME",
  email: "EMAIL",
  phone: "PHONE",
  "số điện thoại": "PHONE",
  "so dien thoai": "PHONE",
  classcode: "CLASS_CODE",
  "class code": "CLASS_CODE",
  class_code: "CLASS_CODE",
  "mã lớp": "CLASS_CODE",
  "ma lop": "CLASS_CODE",
};

export function mapImportApiErrorCode(code?: string): KnownImportApiErrorCode | null {
  if (!code) return null;
  return Object.hasOwn(API_ERROR_CODE_MAP, code) ? (code as KnownImportApiErrorCode) : null;
}

export function resolveImportApiErrorMessage(
  error: unknown,
  translateCode: (code: KnownImportApiErrorCode) => string,
  fallbackMessage: string,
): string {
  if (error instanceof BulkImportApiError) {
    const firstFieldCode = mapImportApiErrorCode(error.fieldErrors[0]?.code);
    if (error.code === "VALIDATION_FAILED" && firstFieldCode) {
      return translateCode(firstFieldCode);
    }
    const mapped = mapImportApiErrorCode(error.code);
    if (mapped) return translateCode(mapped);
    if (firstFieldCode) return translateCode(firstFieldCode);
  }
  const status = getErrorStatus(error);
  if (status === 400) return translateCode("VALIDATION_FAILED");
  if (status === 403) return translateCode("FORBIDDEN");
  if (status === 404) return translateCode("NOT_FOUND");
  if (status === 413) return translateCode("IMPORT_FILE_TOO_LARGE");
  if (status === 429) return translateCode("RATE_LIMITED");
  if (status !== null && status >= 500) return translateCode("INTERNAL_ERROR");
  return fallbackMessage;
}

export function mapImportRowErrorCode(code?: string): KnownImportRowErrorCode | null {
  if (!code) return null;
  return Object.hasOwn(ROW_ERROR_CODE_MAP, code) ? (code as KnownImportRowErrorCode) : null;
}

export function mapImportFailureCode(code?: string): KnownImportFailureCode {
  if (code && Object.hasOwn(FAILURE_CODE_MAP, code)) {
    return code as KnownImportFailureCode;
  }
  return "INTERNAL_ERROR";
}

export function mapImportColumnKey(rawColumn?: string): ImportColumnKey | null {
  if (!rawColumn) return null;
  const normalized = rawColumn.replace(/\*/g, "").trim().toLowerCase();
  if (Object.hasOwn(COLUMN_SYNONYMS, normalized)) {
    return COLUMN_SYNONYMS[normalized]!;
  }
  return null;
}

export function isBatchInProgressStatus(status?: ImportBatchStatus): boolean {
  return status === "UPLOADED" || status === "CONFIRMED" || status === "PROCESSING";
}

export function isBatchTerminalStatus(status?: ImportBatchStatus): boolean {
  return (
    status === "COMPLETED" ||
    status === "COMPLETED_WITH_ERRORS" ||
    status === "FAILED"
  );
}

export function validateSelectedExcelFile(file: File): KnownImportApiErrorCode | null {
  const name = file.name.trim().toLowerCase();
  if (name.endsWith(".xlsm")) {
    return "IMPORT_MACRO_NOT_ALLOWED";
  }
  if (!name.endsWith(".xlsx")) {
    return "IMPORT_INVALID_FILE";
  }
  if (file.size === 0) {
    return "IMPORT_EMPTY_FILE";
  }
  if (file.size > MAX_IMPORT_FILE_BYTES) {
    return "IMPORT_FILE_TOO_LARGE";
  }
  return null;
}

export function generateIdempotencyKey(): string {
  if (typeof globalThis.crypto?.randomUUID === "function") {
    return globalThis.crypto.randomUUID();
  }
  return `import-${Date.now()}-${Math.random().toString(36).slice(2, 10)}`;
}
