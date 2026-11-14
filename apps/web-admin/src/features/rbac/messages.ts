import { getErrorStatus } from "@/lib/api/query-client";
import { RbacApiError } from "./api";

export const KNOWN_OBJECT_CODES = [
  "USER",
  "CLASS",
  "ROLE",
  "PERMISSION",
  "CATALOG",
  "AUDIT",
  "APPEARANCE",
] as const;

export const KNOWN_PERMISSION_CODES = [
  "USER.VIEW",
  "USER.MODIFY",
  "USER.ASSIGN",
  "USER.LEARNING_VIEW",
  "CLASS.VIEW",
  "CLASS.MODIFY",
  "ROLE.VIEW",
  "ROLE.MODIFY",
  "PERMISSION.VIEW",
  "CATALOG.VIEW",
  "CATALOG.MODIFY",
  "AUDIT.VIEW",
  "APPEARANCE.MODIFY",
] as const;

export const KNOWN_SYSTEM_ROLE_CODES = [
  "SYSTEM_ADMIN",
  "CONTENT_ADMIN",
  "OPERATIONS",
  "STUDENT",
] as const;

export const KNOWN_ERROR_CODES = [
  "DIRECT_PERMISSION_REQUIRES_MANAGE",
  "INTERNAL_ERROR",
  "PERMISSION_REQUIRES_VIEW",
  "PERMISSION_SCOPE_MISMATCH",
  "PRIVILEGE_ESCALATION",
  "RATE_LIMITED",
  "REASON_REQUIRED",
  "ROLE_ACCESS_LOCKED",
  "ROLE_CODE_TAKEN",
  "ROLE_INACTIVE",
  "ROLE_IN_USE",
  "ROLE_IS_SYSTEM",
  "SELF_LOCKOUT",
  "VALIDATION_FAILED",
  "VERSION_CONFLICT",
] as const;

export const KNOWN_BULK_REASON_CODES = [
  "DIRECT_PERMISSION_REQUIRES_MANAGE",
  "PERMISSION_REQUIRES_VIEW",
  "PERMISSION_SCOPE_MISMATCH",
  "PRIVILEGE_ESCALATION",
  "ROLE_INACTIVE",
  "SELF_LOCKOUT",
  "USER_NOT_AVAILABLE",
] as const;

type KnownObjectCode = (typeof KNOWN_OBJECT_CODES)[number];
type KnownPermissionCode = (typeof KNOWN_PERMISSION_CODES)[number];
type KnownSystemRoleCode = (typeof KNOWN_SYSTEM_ROLE_CODES)[number];
type KnownErrorCode = (typeof KNOWN_ERROR_CODES)[number];
type KnownBulkReasonCode = (typeof KNOWN_BULK_REASON_CODES)[number];

export type RbacTranslator = (key: string, values?: Record<string, string | number>) => string;

function isInList<T extends string>(list: readonly T[], value: string): value is T {
  return (list as readonly string[]).includes(value);
}

/** Tên đối tượng theo mã; fallback tên từ backend nếu có mã đối tượng mới ở đợt sau. */
export function resolveObjectName(
  t: RbacTranslator,
  objectCode: string,
  fallbackName?: string,
): string {
  if (isInList(KNOWN_OBJECT_CODES, objectCode)) {
    return t(`objects.${objectCode as KnownObjectCode}`);
  }
  return fallbackName ?? objectCode;
}

/** Tên quyền (mức hoặc quyền riêng) theo mã; fallback tên từ backend. */
function toPermissionMessageKey(permissionCode: KnownPermissionCode): string {
  return permissionCode.replaceAll(".", "_");
}

/** Tên quyền (mức hoặc quyền riêng) theo mã; fallback tên từ backend. */
export function resolvePermissionName(
  t: RbacTranslator,
  permissionCode: string,
  fallbackName?: string,
): string {
  if (isInList(KNOWN_PERMISSION_CODES, permissionCode)) {
    return t(`permissions.${toPermissionMessageKey(permissionCode)}.name`);
  }
  return fallbackName ?? permissionCode;
}

/** Tên ngắn đầy đủ ("Xem tài khoản", "Xem và sửa lớp học"…) theo mã; fallback tên từ backend. */
export function resolvePermissionShortName(
  t: RbacTranslator,
  permissionCode: string,
  fallbackName?: string,
): string {
  if (isInList(KNOWN_PERMISSION_CODES, permissionCode)) {
    return t(`permissions.${toPermissionMessageKey(permissionCode)}.shortName`);
  }
  return fallbackName ?? permissionCode;
}

/** Tên vai trò theo mã nếu là vai trò hệ thống; ngược lại giữ nguyên tên người dùng đặt. */
export function resolveRoleName(
  t: RbacTranslator,
  roleCode: string,
  roleName: string,
  isSystem?: boolean,
): string {
  if (isSystem !== false && isInList(KNOWN_SYSTEM_ROLE_CODES, roleCode)) {
    return t(`systemRoles.${roleCode as KnownSystemRoleCode}`);
  }
  return roleName;
}

export function extractRbacErrorCode(error: unknown): string | undefined {
  if (error instanceof RbacApiError) {
    if (error.code && error.code !== "VALIDATION_FAILED") return error.code;
    const firstFieldCode = error.fieldErrors[0]?.code;
    if (firstFieldCode && isInList(KNOWN_ERROR_CODES, firstFieldCode)) {
      return firstFieldCode;
    }
    if (error.code) return error.code;
  }
  return undefined;
}

/** Ánh xạ mã lỗi backend (T04/T07) sang thông báo bản địa hóa theo key. */
export function resolveRbacErrorMessage(
  t: RbacTranslator,
  error: unknown,
  fallbackKey: string,
): string {
  const code = extractRbacErrorCode(error);
  if (code && isInList(KNOWN_ERROR_CODES, code)) {
    return t(`errorCodes.${code as KnownErrorCode}`);
  }
  const status = error instanceof RbacApiError ? error.httpStatus : getErrorStatus(error);
  if (status === 412) return t("errorCodes.VERSION_CONFLICT");
  if (status === 400) return t("errorCodes.VALIDATION_FAILED");
  if (status === 429) return t("errorCodes.RATE_LIMITED");
  if (status !== null && status >= 500) return t("errorCodes.INTERNAL_ERROR");
  return t(fallbackKey);
}

/** Ánh xạ reasonCode của bước xem trước/áp dụng hàng loạt sang câu hiển thị theo key. */
export function resolveBulkReasonMessage(
  t: RbacTranslator,
  reasonCode?: string,
): string {
  if (!reasonCode) return "";
  if (isInList(KNOWN_BULK_REASON_CODES, reasonCode)) {
    return t(`bulk.reasonCodes.${reasonCode as KnownBulkReasonCode}`);
  }
  return reasonCode;
}
