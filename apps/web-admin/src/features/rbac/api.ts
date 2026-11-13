import { createBrowserApiClient } from "@/lib/api/client";
import type {
  AccessScope,
  BulkAuthzRequest,
  BulkAuthzResponse,
  CreateRoleRequest,
  EffectivePermissionsResponse,
  FieldError,
  PageResponsePermissionUserResponse,
  PageResponseRoleSummaryResponse,
  PageResponseUserSummaryResponse,
  PermissionResponse,
  PermissionSourceType,
  RoleDetailResponse,
  RoleStatus,
  UpdateRolePermissionsRequest,
  UpdateRoleRequest,
  UpdateUserPermissionsRequest,
  UpdateUserRolesRequest,
  UserDirectPermissionsResponse,
  UserRolesResponse,
  UserStatus,
} from "./types";

export class RbacApiError extends Error {
  /**
   * HTTP status cho QueryProvider. Với lỗi nghiệp vụ `403 PRIVILEGE_ESCALATION`, đặt `undefined`
   * để QueryProvider không chuyển hướng toàn trang sang `/forbidden`, còn màn hình vẫn đọc được `httpStatus`.
   */
  readonly status: number | undefined;
  readonly httpStatus: number;
  readonly code?: string;
  readonly fieldErrors: FieldError[];

  constructor(
    httpStatus: number,
    message = "",
    code?: string,
    fieldErrors: FieldError[] = [],
  ) {
    super(message);
    this.name = "RbacApiError";
    this.httpStatus = httpStatus;
    this.code = code;
    this.fieldErrors = fieldErrors;
    this.status = httpStatus === 403 && code === "PRIVILEGE_ESCALATION" ? undefined : httpStatus;
  }
}

function isFieldErrorRecord(item: unknown): item is FieldError {
  if (typeof item !== "object" || item === null) return false;
  const hasField = "field" in item && typeof item.field === "string";
  const hasCode = "code" in item && typeof item.code === "string";
  const hasMessage = "message" in item && typeof item.message === "string";
  return hasField && hasCode && hasMessage;
}

function toRbacApiError(status: number, error: unknown): RbacApiError {
  if (typeof error === "object" && error !== null) {
    const code = "code" in error && typeof error.code === "string" ? error.code : undefined;
    const message = "message" in error && typeof error.message === "string" ? error.message : "";
    const rawFieldErrors =
      "fieldErrors" in error && Array.isArray(error.fieldErrors) ? error.fieldErrors : [];
    const fieldErrors = rawFieldErrors.filter(isFieldErrorRecord);
    return new RbacApiError(status, message, code, fieldErrors);
  }
  return new RbacApiError(status);
}

export const rbacKeys = {
  all: ["rbac"] as const,
  permissions: (params?: ListPermissionsParams) =>
    ["rbac", "permissions", params ?? {}] as const,
  roles: (params?: ListRolesParams) => ["rbac", "roles", params ?? {}] as const,
  roleDetail: (id: string) => ["rbac", "role", id] as const,
  permissionUsers: (id: string, params?: ListPermissionUsersParams) =>
    ["rbac", "permission-users", id, params ?? {}] as const,
  users: (params?: ListUsersParams) => ["rbac", "users", params ?? {}] as const,
  userRoles: (userId: string) => ["rbac", "user-roles", userId] as const,
  userDirectPermissions: (userId: string) =>
    ["rbac", "user-direct-permissions", userId] as const,
  userEffectivePermissions: (userId: string) =>
    ["rbac", "user-effective-permissions", userId] as const,
};

export interface ListPermissionsParams {
  q?: string;
  group?: string;
  scope?: AccessScope;
}

export interface ListRolesParams {
  q?: string;
  access?: AccessScope;
  status?: RoleStatus;
  page?: number;
  size?: number;
  sort?: string;
}

export interface ListPermissionUsersParams {
  source?: PermissionSourceType;
  status?: UserStatus;
  q?: string;
  page?: number;
  size?: number;
  sort?: string;
}

export interface ListUsersParams {
  q?: string;
  status?: UserStatus;
  roleId?: string;
  page?: number;
  size?: number;
  sort?: string;
}

export async function listPermissions(
  params?: ListPermissionsParams,
): Promise<PermissionResponse[]> {
  const client = createBrowserApiClient();
  const { data, error, response } = await client.GET("/api/v1/admin/permissions", {
    params: { query: params },
  });
  if (!response.ok || !data) throw toRbacApiError(response.status, error);
  return data;
}

export async function listRoles(
  params?: ListRolesParams,
): Promise<PageResponseRoleSummaryResponse> {
  const client = createBrowserApiClient();
  const { data, error, response } = await client.GET("/api/v1/admin/roles", {
    params: { query: params },
  });
  if (!response.ok || !data) throw toRbacApiError(response.status, error);
  return data;
}

export async function getRole(id: string): Promise<RoleDetailResponse> {
  const client = createBrowserApiClient();
  const { data, error, response } = await client.GET("/api/v1/admin/roles/{id}", {
    params: { path: { id } },
  });
  if (!response.ok || !data) throw toRbacApiError(response.status, error);
  return data;
}

export async function createRole(payload: CreateRoleRequest): Promise<RoleDetailResponse> {
  const client = createBrowserApiClient();
  const { data, error, response } = await client.POST("/api/v1/admin/roles", {
    body: payload,
  });
  if (!response.ok || !data) throw toRbacApiError(response.status, error);
  return data;
}

export async function updateRole(
  id: string,
  payload: UpdateRoleRequest,
): Promise<RoleDetailResponse> {
  const client = createBrowserApiClient();
  const { data, error, response } = await client.PATCH("/api/v1/admin/roles/{id}", {
    params: { path: { id } },
    body: payload,
  });
  if (!response.ok || !data) throw toRbacApiError(response.status, error);
  return data;
}

export async function updateRolePermissions(
  id: string,
  payload: UpdateRolePermissionsRequest,
): Promise<RoleDetailResponse> {
  const client = createBrowserApiClient();
  const { data, error, response } = await client.PUT("/api/v1/admin/roles/{id}/permissions", {
    params: { path: { id } },
    body: payload,
  });
  if (!response.ok || !data) throw toRbacApiError(response.status, error);
  return data;
}

export async function deleteRole(id: string): Promise<void> {
  const client = createBrowserApiClient();
  const { error, response } = await client.DELETE("/api/v1/admin/roles/{id}", {
    params: { path: { id } },
  });
  if (!response.ok) throw toRbacApiError(response.status, error);
}

export async function listUsersByPermission(
  id: string,
  params?: ListPermissionUsersParams,
): Promise<PageResponsePermissionUserResponse> {
  const client = createBrowserApiClient();
  const { data, error, response } = await client.GET("/api/v1/admin/permissions/{id}/users", {
    params: { path: { id }, query: params },
  });
  if (!response.ok || !data) throw toRbacApiError(response.status, error);
  return data;
}

export async function exportUsersByPermission(
  id: string,
  params?: Omit<ListPermissionUsersParams, "page" | "size">,
): Promise<{ blob: Blob; fileName: string }> {
  const client = createBrowserApiClient();
  const { data, error, response } = await client.GET(
    "/api/v1/admin/permissions/{id}/users/export",
    {
      params: { path: { id }, query: params },
      parseAs: "blob",
    },
  );
  if (!response.ok || !(data instanceof Blob)) {
    throw toRbacApiError(response.status, error);
  }
  const disposition = response.headers.get("content-disposition") ?? "";
  const match = /filename="?([^";]+)"?/i.exec(disposition);
  const fileName = match?.[1] ?? `permission-users-${id}.xlsx`;
  return { blob: data, fileName };
}

export async function listUsers(
  params?: ListUsersParams,
): Promise<PageResponseUserSummaryResponse> {
  const client = createBrowserApiClient();
  const { data, error, response } = await client.GET("/api/v1/admin/users", {
    params: { query: params },
  });
  if (!response.ok || !data) throw toRbacApiError(response.status, error);
  return data;
}

export async function getUserRoles(userId: string): Promise<UserRolesResponse> {
  const client = createBrowserApiClient();
  const { data, error, response } = await client.GET("/api/v1/admin/users/{id}/roles", {
    params: { path: { id: userId } },
  });
  if (!response.ok || !data) throw toRbacApiError(response.status, error);
  return data;
}

export async function updateUserRoles(
  userId: string,
  payload: UpdateUserRolesRequest,
): Promise<UserRolesResponse> {
  const client = createBrowserApiClient();
  const { data, error, response } = await client.PUT("/api/v1/admin/users/{id}/roles", {
    params: { path: { id: userId } },
    body: payload,
  });
  if (!response.ok || !data) throw toRbacApiError(response.status, error);
  return data;
}

export async function getUserPermissions(
  userId: string,
): Promise<UserDirectPermissionsResponse> {
  const client = createBrowserApiClient();
  const { data, error, response } = await client.GET("/api/v1/admin/users/{id}/permissions", {
    params: { path: { id: userId } },
  });
  if (!response.ok || !data) throw toRbacApiError(response.status, error);
  return data;
}

export async function updateUserPermissions(
  userId: string,
  payload: UpdateUserPermissionsRequest,
): Promise<UserDirectPermissionsResponse> {
  const client = createBrowserApiClient();
  const { data, error, response } = await client.PUT("/api/v1/admin/users/{id}/permissions", {
    params: { path: { id: userId } },
    body: payload,
  });
  if (!response.ok || !data) throw toRbacApiError(response.status, error);
  return data;
}

export async function getEffectivePermissions(
  userId: string,
): Promise<EffectivePermissionsResponse> {
  const client = createBrowserApiClient();
  const { data, error, response } = await client.GET(
    "/api/v1/admin/users/{id}/effective-permissions",
    {
      params: { path: { id: userId } },
    },
  );
  if (!response.ok || !data) throw toRbacApiError(response.status, error);
  return data;
}

export async function previewBulkAuthz(
  payload: BulkAuthzRequest,
  signal?: AbortSignal,
): Promise<BulkAuthzResponse> {
  const client = createBrowserApiClient();
  const { data, error, response } = await client.POST("/api/v1/admin/authz/bulk/preview", {
    body: payload,
    signal,
  });
  if (!response.ok || !data) throw toRbacApiError(response.status, error);
  return data;
}

export async function applyBulkAuthz(
  payload: BulkAuthzRequest,
  idempotencyKey: string,
): Promise<BulkAuthzResponse> {
  const client = createBrowserApiClient();
  const { data, error, response } = await client.POST("/api/v1/admin/authz/bulk", {
    params: {
      header: {
        "Idempotency-Key": idempotencyKey,
      },
    },
    body: payload,
  });
  if (!response.ok || !data) throw toRbacApiError(response.status, error);
  return data;
}
