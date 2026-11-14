import type { FieldError } from "@thi360/api-client";
import { getErrorStatus } from "@/lib/api/query-client";

export const CATALOG_CODE_REGEX = /^[A-Za-z0-9][A-Za-z0-9_.-]{0,49}$/;
export const MAX_IMPORT_FILE_BYTES = 5 * 1024 * 1024;


export const BEHAVIOR_WARNING_GROUP_CODES: Record<string, true> = {
  QUESTION_FORMAT: true,
  TASK_TYPE: true,
};

export const CATALOG_ERROR_CODES = [
  "CATALOG_CHANGE_BLOCKED",
  "CATALOG_CODE_DUPLICATE_IN_FILE",
  "CATALOG_CODE_LOCKED",
  "CATALOG_CODE_TAKEN",
  "CATALOG_GROUP_INACTIVE",
  "CATALOG_GROUP_NOT_EMPTY",
  "CATALOG_GROUP_SYSTEM",
  "CATALOG_ITEM_IN_USE",
  "CATALOG_ITEM_NOT_FOUND",
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
  "CODE_INVALID",
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
  "PARENT_REQUIRED",
  "RATE_LIMITED",
  "REQUIRED",
  "SIZE",
  "STATUS_INVALID",
  "VALIDATION_FAILED",
  "VERSION_CONFLICT",
] as const;

export type CatalogErrorCode = (typeof CATALOG_ERROR_CODES)[number];
export const CATALOG_AUDIT_ACTIONS = [
  "CATALOG_ITEM_CREATED",
  "CATALOG_ITEM_UPDATED",
  "CATALOG_ITEM_REORDERED",
  "CATALOG_ITEM_DEACTIVATED",
  "CATALOG_ITEM_ACTIVATED",
  "CATALOG_ITEM_DELETED",
  "CATALOG_ITEM_MOVED",
  "CATALOG_ITEM_REFS_UPDATED",
  "COMBINATION_VERSION_CREATED",
] as const;

export type CatalogAuditAction = (typeof CATALOG_AUDIT_ACTIONS)[number];

export const CATALOG_USAGE_TYPES = [
  "EXAM_BLUEPRINT",
  "EXAM_DEFINITION",
  "PUBLISHED_EXAM",
  "QUESTION",
  "SUBJECT_COMBINATION",
  "TREE_NODE",
] as const;

export type CatalogUsageType = (typeof CATALOG_USAGE_TYPES)[number];
const KNOWN_ERROR_CODE_MAP: Record<string, true> = CATALOG_ERROR_CODES.reduce<Record<string, true>>(
  (acc, code) => {
    acc[code] = true;
    return acc;
  },
  {},
);

const KNOWN_AUDIT_ACTION_MAP: Record<string, true> = CATALOG_AUDIT_ACTIONS.reduce<
  Record<string, true>
>((acc, action) => {
  acc[action] = true;
  return acc;
}, {});

const KNOWN_USAGE_TYPE_MAP: Record<string, true> = CATALOG_USAGE_TYPES.reduce<
  Record<string, true>
>((acc, usageType) => {
  acc[usageType] = true;
  return acc;
}, {});

export function resolveCatalogAuditActionKey(
  action: string | undefined,
): CatalogAuditAction | "UNKNOWN_ACTION" {
  return action && action in KNOWN_AUDIT_ACTION_MAP
    ? (action as CatalogAuditAction)
    : "UNKNOWN_ACTION";
}

export function resolveCatalogUsageTypeKey(
  objectType: string | undefined,
): CatalogUsageType | "UNKNOWN_USAGE" {
  return objectType && objectType in KNOWN_USAGE_TYPE_MAP
    ? (objectType as CatalogUsageType)
    : "UNKNOWN_USAGE";
}
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

const SYSTEM_TREE_LEVEL_KEYS = {
  chương: "chapter",
  bài: "lesson",
  "kiến thức": "knowledge",
} as const;

export type SystemTreeLevelKey =
  (typeof SYSTEM_TREE_LEVEL_KEYS)[keyof typeof SYSTEM_TREE_LEVEL_KEYS];

/**
 * Ánh xạ tên cấp của cây hệ thống (Chương / Bài / Kiến thức) sang khóa dịch vi/en;
 * giữ nguyên tên cấp người dùng tự nhập ở cây tùy chỉnh.
 */
export function resolveSystemTreeLevelKey(levelName: string): SystemTreeLevelKey | null {
  const normalized = levelName.trim().toLowerCase();
  if (normalized in SYSTEM_TREE_LEVEL_KEYS) {
    return SYSTEM_TREE_LEVEL_KEYS[normalized as keyof typeof SYSTEM_TREE_LEVEL_KEYS];
  }
  return null;
}

export function resolveTreeLevelLabel(
  levelName: string | undefined,
  options: {
    isSystemGroup?: boolean;
    translateLevel: (key: SystemTreeLevelKey) => string;
    fallback?: string;
  },
): string {
  if (!levelName) return options.fallback ?? "";
  if (options.isSystemGroup) {
    const key = resolveSystemTreeLevelKey(levelName);
    if (key) return options.translateLevel(key);
  }
  return levelName;
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

export function triggerBlobDownload(blob: Blob, filename: string): void {
  const url = URL.createObjectURL(blob);
  const anchor = document.createElement("a");
  anchor.href = url;
  anchor.download = filename;
  document.body.appendChild(anchor);
  anchor.click();
  anchor.remove();
  URL.revokeObjectURL(url);
}

function escapeCsvCell(value: string): string {
  const normalized = value.replace(/\r?\n/g, " ");
  if (/[",]/.test(normalized)) {
    return `"${normalized.replace(/"/g, '""')}"`;
  }
  return normalized;
}

export function buildImportErrorCsv(
  headers: { row: string; column: string; code: string; message: string },
  errors: ReadonlyArray<{ rowNo: number; column: string; code: string; message: string }>,
): Blob {
  const lines = [
    [headers.row, headers.column, headers.code, headers.message].map(escapeCsvCell).join(","),
    ...errors.map((err) =>
      [String(err.rowNo), err.column, err.code, err.message].map(escapeCsvCell).join(","),
    ),
  ];
  // UTF-8 BOM so Microsoft Excel preserves Vietnamese diacritics
  return new Blob(["\uFEFF" + lines.join("\r\n")], { type: "text/csv;charset=utf-8" });
}
