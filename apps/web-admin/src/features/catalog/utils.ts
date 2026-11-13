import type { FieldError } from "@thi360/api-client";
import { getErrorStatus } from "@/lib/api/query-client";

export const CATALOG_CODE_REGEX = /^[A-Za-z0-9][A-Za-z0-9_.-]{0,49}$/;

export const BEHAVIOR_WARNING_GROUP_CODES: Record<string, true> = {
  QUESTION_FORMAT: true,
  TASK_TYPE: true,
};

export const CATALOG_ERROR_CODES = [
  "CATALOG_CHANGE_BLOCKED",
  "CATALOG_CODE_LOCKED",
  "CATALOG_CODE_TAKEN",
  "CATALOG_GROUP_INACTIVE",
  "CATALOG_GROUP_NOT_EMPTY",
  "CATALOG_GROUP_SYSTEM",
  "CATALOG_ITEM_IN_USE",
  "CATALOG_ITEM_SYSTEM",
  "CATALOG_LEVELS_LOCKED",
  "CATALOG_NODE_HAS_CHILDREN",
  "CATALOG_PARENT_INACTIVE",
  "CATALOG_PARENT_INVALID",
  "CATALOG_REF_GROUP_DEFAULT",
  "CATALOG_REF_GROUP_IN_USE",
  "CATALOG_REF_INVALID",
  "CATALOG_TREE_DEPTH_EXCEEDED",
  "CATALOG_TREE_TOO_LARGE",
  "IMPORT_EMPTY_FILE",
  "IMPORT_FILE_TOO_LARGE",
  "IMPORT_FORMULA_NOT_ALLOWED",
  "IMPORT_HAS_ERRORS",
  "IMPORT_INVALID_FILE",
  "IMPORT_INVALID_HEADER",
  "IMPORT_MACRO_NOT_ALLOWED",
  "IMPORT_MERGED_REGION_TOO_LARGE",
  "IMPORT_NOT_SUPPORTED",
  "IMPORT_PREVIEW_EXPIRED",
  "IMPORT_ROW_LIMIT_EXCEEDED",
  "INTERNAL_ERROR",
  "NOT_FOUND",
  "RATE_LIMITED",
  "VALIDATION_FAILED",
  "VERSION_CONFLICT",
] as const;

export type CatalogErrorCode = (typeof CATALOG_ERROR_CODES)[number];
const KNOWN_ERROR_CODE_MAP: Record<string, true> = CATALOG_ERROR_CODES.reduce<Record<string, true>>(
  (acc, code) => {
    acc[code] = true;
    return acc;
  },
  {},
);

export interface BilingualName {
  nameVi?: string | null;
  nameEn?: string | null;
}

/**
 * Chọn tên hiển thị theo ngôn ngữ hiện tại với dự phòng là `nameVi` (spec §9.6, T15 run rules).
 */
export function pickName(item: BilingualName, locale = "vi"): string {
  const vi = item.nameVi?.trim() ?? "";
  const en = item.nameEn?.trim() ?? "";
  if (locale.toLowerCase().startsWith("en") && en.length > 0) {
    return en;
  }
  return vi.length > 0 ? vi : en;
}

export function isKnownCatalogErrorCode(code: string | undefined): code is CatalogErrorCode {
  return Boolean(code && code in KNOWN_ERROR_CODE_MAP);
}

export function getCatalogErrorCode(error: unknown): string | undefined {
  if (typeof error === "object" && error !== null && "code" in error) {
    return typeof error.code === "string" ? error.code : undefined;
  }
  return undefined;
}

function isFieldError(value: unknown): value is FieldError {
  return (
    typeof value === "object" &&
    value !== null &&
    "field" in value &&
    typeof value.field === "string" &&
    "code" in value &&
    typeof value.code === "string" &&
    "message" in value &&
    typeof value.message === "string"
  );
}

export function getCatalogFieldErrors(error: unknown): FieldError[] {
  if (typeof error === "object" && error !== null && "fieldErrors" in error) {
    const raw = error.fieldErrors;
    return Array.isArray(raw) ? raw.filter(isFieldError) : [];
  }
  return [];
}

/**
 * Ánh xạ mã lỗi API sang khóa dịch (vi + en), không lấy nguyên văn câu lỗi từ backend.
 */
export function resolveCatalogErrorMessage(
  error: unknown,
  translateCode: (code: CatalogErrorCode | "UNKNOWN_ERROR") => string,
): string {
  const code = getCatalogErrorCode(error);
  if (isKnownCatalogErrorCode(code)) {
    return translateCode(code);
  }
  const status = getErrorStatus(error);
  if (status === 400) return translateCode("VALIDATION_FAILED");
  if (status === 404) return translateCode("NOT_FOUND");
  if (status === 412) return translateCode("VERSION_CONFLICT");
  if (status === 429) return translateCode("RATE_LIMITED");
  if (status !== null && status >= 500) return translateCode("INTERNAL_ERROR");
  return translateCode("UNKNOWN_ERROR");
}
