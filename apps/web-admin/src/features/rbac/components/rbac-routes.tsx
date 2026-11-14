"use client";

import { useEffect } from "react";
import { BulkAssignTab, type BulkAssignTabProps } from "./bulk-assign-tab";
import { PermissionUsersTab, type PermissionUsersTabProps } from "./permission-users-tab";
import { PermissionsTab, type PermissionsTabProps } from "./permissions-tab";
import { useRbacLayout } from "./rbac-layout-view";
import { RolesTab, type RolesTabProps } from "./roles-tab";

/*
 * Nội dung của từng trang con `/rbac/<tab>`: nối tab với trạng thái dùng chung của layout
 * (quyền đang chọn trên URL, hộp thoại tạo vai trò, chuyển tab). Props chỉ để test tiêm API giả.
 */

export type RbacRolesRouteProps = Omit<RolesTabProps, "createOpen" | "onCreateOpenChange">;

/** `/rbac/roles` */
export function RbacRolesRoute(props: RbacRolesRouteProps) {
  const { createRoleOpen, setCreateRoleOpen } = useRbacLayout();

  // Rời trang Vai trò thì đóng yêu cầu mở hộp thoại, để quay lại không tự mở lại.
  useEffect(() => () => setCreateRoleOpen(false), [setCreateRoleOpen]);

  return (
    <RolesTab {...props} createOpen={createRoleOpen} onCreateOpenChange={setCreateRoleOpen} />
  );
}

export type RbacPermissionsRouteProps = Omit<PermissionsTabProps, "onSelectPermissionUsers">;

/** `/rbac/permissions` */
export function RbacPermissionsRoute(props: RbacPermissionsRouteProps) {
  const { navigateToTab } = useRbacLayout();
  return (
    <PermissionsTab {...props} onSelectPermissionUsers={(code) => navigateToTab("users", code)} />
  );
}

export type RbacUsersRouteProps = Omit<
  PermissionUsersTabProps,
  "selectedPermissionCode" | "onSelectPermission" | "onAssignMany" | "onViewRoles"
>;

/** `/rbac/users` (+ `?permission=CODE`) */
export function RbacUsersRoute(props: RbacUsersRouteProps) {
  const { navigateToTab, selectedPermissionCode } = useRbacLayout();
  return (
    <PermissionUsersTab
      {...props}
      selectedPermissionCode={selectedPermissionCode}
      onSelectPermission={(code) => navigateToTab("users", code)}
      onAssignMany={(code) => navigateToTab("bulk-assign", code)}
      onViewRoles={() => navigateToTab("roles")}
    />
  );
}

export type RbacBulkAssignRouteProps = Omit<BulkAssignTabProps, "initialPermissionCode">;

/** `/rbac/bulk-assign` (+ `?permission=CODE`) */
export function RbacBulkAssignRoute(props: RbacBulkAssignRouteProps) {
  const { selectedPermissionCode } = useRbacLayout();
  return <BulkAssignTab {...props} initialPermissionCode={selectedPermissionCode} />;
}
