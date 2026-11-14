import { hasPermission } from "@/features/session/permissions";
import type { RbacTabKey } from "./types";

/** Đường dẫn gốc của màn Phân quyền; mỗi tab là một trang con `/rbac/<khóa tab>`. */
export const RBAC_BASE_PATH = "/rbac";

export const RBAC_TAB_ORDER: readonly RbacTabKey[] = [
  "roles",
  "permissions",
  "users",
  "bulk-assign",
];

export function getAllowedRbacTabs(permissions: ReadonlySet<string>): RbacTabKey[] {
  const allowed: RbacTabKey[] = [];
  if (hasPermission(permissions, "ROLE.VIEW")) allowed.push("roles");
  if (hasPermission(permissions, "PERMISSION.VIEW")) allowed.push("permissions");
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

export function rbacTabHref(tab: RbacTabKey, permissionCode?: string | null): string {
  const path = `${RBAC_BASE_PATH}/${tab}`;
  if (!permissionCode || !acceptsPermissionQuery(tab)) return path;
  return `${path}?${new URLSearchParams({ permission: permissionCode }).toString()}`;
}

/**
 * Đường dẫn khi người dùng chuyển từ tab đang mở sang `nextTab`.
 * Có `permissionCode` thì mang mã đó theo. Không có thì giữ mã đang chọn khi ở lại tab hiện tại hoặc
 * sang "Người dùng theo quyền"; sang "Gán hàng loạt" chỉ giữ khi đi từ "Người dùng theo quyền".
 */
export function rbacNavigationHref(
  nextTab: RbacTabKey,
  from: { tab: RbacTabKey; permission: string | null },
  permissionCode?: string,
): string {
  if (permissionCode) return rbacTabHref(nextTab, permissionCode);
  const leavesForBulkAssign = nextTab === "bulk-assign" && from.tab !== "bulk-assign";
  if (leavesForBulkAssign && from.tab !== "users") return rbacTabHref(nextTab);
  return rbacTabHref(nextTab, from.permission);
}
