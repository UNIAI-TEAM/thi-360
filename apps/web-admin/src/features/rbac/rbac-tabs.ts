import { hasPermission } from "@/features/session/permissions";
import type { RbacTabKey } from "./types";

/** Đường dẫn gốc của màn Phân quyền; mỗi tab là một trang con `/rbac/<khóa tab>`. */
export const RBAC_BASE_PATH = "/rbac";

export const RBAC_TAB_ORDER: readonly RbacTabKey[] = [
  "roles",
  "permissions",
  "role-users",
  "users",
  "bulk-assign",
];

export function getAllowedRbacTabs(permissions: ReadonlySet<string>): RbacTabKey[] {
  const allowed: RbacTabKey[] = [];
  if (hasPermission(permissions, "ROLE.VIEW")) allowed.push("roles");
  if (hasPermission(permissions, "PERMISSION.VIEW")) allowed.push("permissions");
  if (hasPermission(permissions, "ROLE.VIEW") && hasPermission(permissions, "USER.VIEW")) {
    allowed.push("role-users");
  }
  if (
    hasPermission(permissions, "PERMISSION.VIEW") &&
    hasPermission(permissions, "USER.VIEW")
  ) {
    allowed.push("users");
  }
  if (hasPermission(permissions, "USER.ASSIGN")) allowed.push("bulk-assign");
  return allowed;
}

export function resolveActiveRbacTab(
  requestedTab: string | null,
  allowedTabs: readonly RbacTabKey[],
): RbacTabKey | null {
  if (allowedTabs.length === 0) return null;
  if (
    requestedTab &&
    (allowedTabs as readonly string[]).includes(requestedTab)
  ) {
    return requestedTab as RbacTabKey;
  }
  return allowedTabs[0]!;
}

/** Đoạn đường dẫn ngay sau `/rbac` (khóa tab đang được yêu cầu); `null` khi đang ở chính `/rbac`. */
export function rbacTabFromPathname(pathname: string): string | null {
  if (!pathname.startsWith(`${RBAC_BASE_PATH}/`)) return null;
  const segment = pathname.slice(RBAC_BASE_PATH.length + 1).split("/")[0];
  return segment ? segment : null;
}

/** Chỉ hai tab này đọc `?permission=CODE`; ở tab khác tham số bị bỏ. */
function acceptsPermissionQuery(tab: RbacTabKey): boolean {
  return tab === "users" || tab === "bulk-assign";
}

/**
 * Đường dẫn của một tab. `code` là mã quyền (`?permission=`) với "Người dùng theo quyền" và "Gán hàng loạt",
 * là mã vai trò (`?role=`) với "Người dùng theo vai trò"; tab khác bỏ tham số.
 */
export function rbacTabHref(tab: RbacTabKey, code?: string | null): string {
  const path = `${RBAC_BASE_PATH}/${tab}`;
  if (!code) return path;
  if (tab === "role-users") return `${path}?${new URLSearchParams({ role: code }).toString()}`;
  if (!acceptsPermissionQuery(tab)) return path;
  return `${path}?${new URLSearchParams({ permission: code }).toString()}`;
}

/**
 * Đường dẫn khi người dùng chuyển từ tab đang mở sang `nextTab`.
 * Có `code` thì mang mã đó theo (mã vai trò khi sang "Người dùng theo vai trò", mã quyền với tab khác). Không có thì
 * giữ mã đang chọn khi ở lại tab hiện tại hoặc sang "Người dùng theo quyền"; sang "Gán hàng loạt" chỉ giữ khi đi từ
 * "Người dùng theo quyền". Vai trò đang chọn chỉ giữ khi ở lại "Người dùng theo vai trò".
 */
export function rbacNavigationHref(
  nextTab: RbacTabKey,
  from: { tab: RbacTabKey; permission: string | null; role?: string | null },
  code?: string,
): string {
  if (nextTab === "role-users") {
    return rbacTabHref(nextTab, code ?? (from.tab === "role-users" ? from.role : null));
  }
  if (code) return rbacTabHref(nextTab, code);
  const leavesForBulkAssign = nextTab === "bulk-assign" && from.tab !== "bulk-assign";
  if (leavesForBulkAssign && from.tab !== "users") return rbacTabHref(nextTab);
  return rbacTabHref(nextTab, from.permission);
}
