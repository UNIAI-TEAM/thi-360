import type { components } from "@thi360/api-client";

export type AccessScope = "MANAGE" | "EXAM";
export type RoleStatus = "ACTIVE" | "INACTIVE";
export type UserStatus = "PROVISIONING" | "ACTIVE" | "INACTIVE";
export type PermissionSourceType = "ROLE" | "DIRECT";
export type BulkAuthzAction = "GRANT" | "REVOKE";
export type BulkAuthzTarget = "ROLE" | "PERMISSION";
export type BulkAuthzOutcome = "WILL_CHANGE" | "NO_CHANGE" | "SKIPPED";

export type ApiError = components["schemas"]["ApiError"];
export type FieldError = components["schemas"]["FieldError"];
export type PermissionResponse = components["schemas"]["PermissionResponse"];
export type PermissionSourceResponse = components["schemas"]["PermissionSourceResponse"];
export type PermissionUserResponse = components["schemas"]["PermissionUserResponse"];
export type PageResponsePermissionUserResponse =
  components["schemas"]["PageResponsePermissionUserResponse"];
export type RoleSummaryResponse = components["schemas"]["RoleSummaryResponse"];
export type RoleDetailResponse = components["schemas"]["RoleDetailResponse"];
export type PageResponseRoleSummaryResponse =
  components["schemas"]["PageResponseRoleSummaryResponse"];
export type CreateRoleRequest = components["schemas"]["CreateRoleRequest"];
export type UpdateRoleRequest = components["schemas"]["UpdateRoleRequest"];
export type UpdateRolePermissionsRequest = components["schemas"]["UpdateRolePermissionsRequest"];
export type UserSummaryResponse = components["schemas"]["UserSummaryResponse"];
export type PageResponseUserSummaryResponse =
  components["schemas"]["PageResponseUserSummaryResponse"];
export type UserRolesResponse = components["schemas"]["UserRolesResponse"];
export type UserRoleItemResponse = components["schemas"]["UserRoleItemResponse"];
export type UpdateUserRolesRequest = components["schemas"]["UpdateUserRolesRequest"];
export type UserDirectPermissionsResponse =
  components["schemas"]["UserDirectPermissionsResponse"];
export type UserDirectPermissionItemResponse =
  components["schemas"]["UserDirectPermissionItemResponse"];
export type UpdateUserPermissionsRequest =
  components["schemas"]["UpdateUserPermissionsRequest"];
export type EffectivePermissionsResponse =
  components["schemas"]["EffectivePermissionsResponse"];
export type EffectivePermissionItemResponse =
  components["schemas"]["EffectivePermissionItemResponse"];
export type BulkAuthzRequest = components["schemas"]["BulkAuthzRequest"];
export type BulkAuthzResponse = components["schemas"]["BulkAuthzResponse"];
export type BulkAuthzItemResponse = components["schemas"]["BulkAuthzItemResponse"];

/** Khóa tab, đồng thời là đoạn đường dẫn của trang con: `/rbac/<khóa>`. */
export type RbacTabKey = "roles" | "permissions" | "users" | "bulk-assign";

export interface PermissionObjectGroup {
  objectCode: string;
  objectName: string;
  viewPermission?: PermissionResponse;
  modifyPermission?: PermissionResponse;
  extraPermissions: PermissionResponse[];
  levelMode: "both" | "view" | "modify";
}
