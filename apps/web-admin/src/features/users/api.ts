import type { ApiError, components, FieldError, operations } from "@thi360/api-client";
import { createBrowserApiClient } from "@/lib/api/client";
import { ApiStatusError } from "@/lib/api/query-client";

export type UserSummary = components["schemas"]["UserSummaryResponse"];
export type UserDetail = components["schemas"]["UserDetailResponse"];
export type UserRoleSummary = components["schemas"]["UserRoleSummary"];
export type PageResponseUserSummary = components["schemas"]["PageResponseUserSummaryResponse"];
export type CreateUserRequest = components["schemas"]["CreateUserRequest"];
export type UpdateUserRequest = components["schemas"]["UpdateUserRequest"];
export type DeactivateUserRequest = components["schemas"]["DeactivateUserRequest"];
export type ResetPasswordRequest = components["schemas"]["ResetPasswordRequest"];
export type DeletionCheckResponse = components["schemas"]["DeletionCheckResponse"];
export type DeactivationCheckResponse = components["schemas"]["DeactivationCheckResponse"];
export type BulkUserActionRequest = components["schemas"]["BulkUserActionRequest"];
export type BulkUserActionPreviewResponse = components["schemas"]["BulkUserActionPreviewResponse"];
export type BulkUserActionResponse = components["schemas"]["BulkUserActionResponse"];
export type UsernameAvailabilityResponse = components["schemas"]["UsernameAvailabilityResponse"];
export type RoleSummary = components["schemas"]["RoleSummaryResponse"];
export type AuditEvent = components["schemas"]["AuditEventResponse"];
export type PageResponseAuditEvent = components["schemas"]["PageResponseAuditEventResponse"];

export type ListUsersQuery = NonNullable<operations["listUsers"]["parameters"]["query"]>;
export type ListAuditEventsQuery = NonNullable<operations["listAuditEvents"]["parameters"]["query"]>;

export const USERS_QUERY_KEYS = {
  all: ["users"] as const,
  lists: () => ["users", "list"] as const,
  list: (query: ListUsersQuery) => ["users", "list", query] as const,
  detail: (id: string) => ["users", "detail", id] as const,
  roles: () => ["users", "roles"] as const,
  audit: (userId: string, query: Omit<ListAuditEventsQuery, "targetType" | "targetId">) =>
    ["users", "audit", userId, query] as const,
};

export class UsersApiError extends ApiStatusError {
  readonly code?: string;
  readonly fieldErrors: readonly FieldError[];
  readonly httpStatus: number;

  constructor(status: number, message = "", code?: string, fieldErrors: readonly FieldError[] = []) {
    super(code === "PRIVILEGE_ESCALATION" ? 409 : status, message);
    this.name = "UsersApiError";
    this.httpStatus = status;
    this.code = code;
    this.fieldErrors = fieldErrors;
  }
}

export function toUsersApiError(status: number, error: unknown): UsersApiError {
  if (typeof error === "object" && error !== null) {
    const body = error as Partial<ApiError>;
    const code = typeof body.code === "string" ? body.code : undefined;
    const message = typeof body.message === "string" ? body.message : "";
    const fieldErrors = Array.isArray(body.fieldErrors) ? body.fieldErrors : [];
    return new UsersApiError(status, message, code, fieldErrors);
  }
  return new UsersApiError(status);
}

export async function listUsers(query: ListUsersQuery): Promise<PageResponseUserSummary> {
  const client = createBrowserApiClient();
  const { data, error, response } = await client.GET("/api/v1/admin/users", {
    params: { query },
  });
  if (!response.ok || !data) throw toUsersApiError(response.status, error);
  return data;
}

export async function getUser(id: string): Promise<UserDetail> {
  const client = createBrowserApiClient();
  const { data, error, response } = await client.GET("/api/v1/admin/users/{id}", {
    params: { path: { id } },
  });
  if (!response.ok || !data) throw toUsersApiError(response.status, error);
  return data;
}

export async function createUser(body: CreateUserRequest): Promise<UserDetail> {
  const client = createBrowserApiClient();
  const { data, error, response } = await client.POST("/api/v1/admin/users", {
    body,
  });
  if (!response.ok || !data) throw toUsersApiError(response.status, error);
  return data;
}

export async function updateUser(id: string, body: UpdateUserRequest): Promise<UserDetail> {
  const client = createBrowserApiClient();
  const { data, error, response } = await client.PATCH("/api/v1/admin/users/{id}", {
    params: { path: { id } },
    body,
  });
  if (!response.ok || !data) throw toUsersApiError(response.status, error);
  return data;
}

export async function deactivateUser(id: string, body: DeactivateUserRequest): Promise<UserDetail> {
  const client = createBrowserApiClient();
  const { data, error, response } = await client.POST("/api/v1/admin/users/{id}/deactivate", {
    params: { path: { id } },
    body,
  });
  if (!response.ok || !data) throw toUsersApiError(response.status, error);
  return data;
}

export async function activateUser(id: string): Promise<UserDetail> {
  const client = createBrowserApiClient();
  const { data, error, response } = await client.POST("/api/v1/admin/users/{id}/activate", {
    params: { path: { id } },
  });
  if (!response.ok || !data) throw toUsersApiError(response.status, error);
  return data;
}

export async function resetUserPassword(id: string, body: ResetPasswordRequest): Promise<UserDetail> {
  const client = createBrowserApiClient();
  const { data, error, response } = await client.POST("/api/v1/admin/users/{id}/reset-password", {
    params: { path: { id } },
    body,
  });
  if (!response.ok || !data) throw toUsersApiError(response.status, error);
  return data;
}

export async function deleteUser(id: string, reason: string): Promise<void> {
  const client = createBrowserApiClient();
  const { error, response } = await client.DELETE("/api/v1/admin/users/{id}", {
    params: { path: { id }, query: { reason } },
  });
  if (!response.ok) throw toUsersApiError(response.status, error);
}

export async function checkUserDeletion(id: string): Promise<DeletionCheckResponse> {
  const client = createBrowserApiClient();
  const { data, error, response } = await client.GET("/api/v1/admin/users/{id}/deletion-check", {
    params: { path: { id } },
  });
  if (!response.ok || !data) throw toUsersApiError(response.status, error);
  return data;
}

export async function checkUserDeactivation(id: string): Promise<DeactivationCheckResponse> {
  const client = createBrowserApiClient();
  const { data, error, response } = await client.GET("/api/v1/admin/users/{id}/deactivation-check", {
    params: { path: { id } },
  });
  if (!response.ok || !data) throw toUsersApiError(response.status, error);
  return data;
}

export async function previewBulkUserActions(
  body: BulkUserActionRequest,
): Promise<BulkUserActionPreviewResponse> {
  const client = createBrowserApiClient();
  const { data, error, response } = await client.POST("/api/v1/admin/users/bulk-actions/preview", {
    body,
  });
  if (!response.ok || !data) throw toUsersApiError(response.status, error);
  return data;
}

export async function applyBulkUserActions(
  body: BulkUserActionRequest,
  idempotencyKey: string,
): Promise<BulkUserActionResponse> {
  const client = createBrowserApiClient();
  const { data, error, response } = await client.POST("/api/v1/admin/users/bulk-actions", {
    params: { header: { "Idempotency-Key": idempotencyKey } },
    body,
  });
  if (!response.ok || !data) throw toUsersApiError(response.status, error);
  return data;
}

export async function checkUsernameAvailability(
  username: string,
): Promise<UsernameAvailabilityResponse> {
  const client = createBrowserApiClient();
  const { data, error, response } = await client.GET("/api/v1/admin/users/username-availability", {
    params: { query: { username } },
  });
  if (!response.ok || !data) throw toUsersApiError(response.status, error);
  return data;
}

export async function listActiveRoles(): Promise<RoleSummary[]> {
  const client = createBrowserApiClient();
  const { data, error, response } = await client.GET("/api/v1/admin/roles", {
    params: { query: { status: "ACTIVE", size: 100, page: 0 } },
  });
  if (!response.ok || !data) throw toUsersApiError(response.status, error);
  return data.items;
}

export async function listUserAuditEvents(
  userId: string,
  query: Omit<ListAuditEventsQuery, "targetType" | "targetId">,
): Promise<PageResponseAuditEvent> {
  const client = createBrowserApiClient();
  const { data, error, response } = await client.GET("/api/v1/admin/audit-events", {
    params: {
      query: {
        ...query,
        targetType: "USER",
        targetId: userId,
        sort: query.sort ?? "occurredAt,desc",
      },
    },
  });
  if (!response.ok || !data) throw toUsersApiError(response.status, error);
  return data;
}

export interface UsersApiDeps {
  listUsers: typeof listUsers;
  getUser: typeof getUser;
  createUser: typeof createUser;
  updateUser: typeof updateUser;
  deactivateUser: typeof deactivateUser;
  activateUser: typeof activateUser;
  resetUserPassword: typeof resetUserPassword;
  deleteUser: typeof deleteUser;
  checkUserDeletion: typeof checkUserDeletion;
  checkUserDeactivation: typeof checkUserDeactivation;
  previewBulkUserActions: typeof previewBulkUserActions;
  applyBulkUserActions: typeof applyBulkUserActions;
  checkUsernameAvailability: typeof checkUsernameAvailability;
  listActiveRoles: typeof listActiveRoles;
  listUserAuditEvents: typeof listUserAuditEvents;
}

export const defaultUsersApiDeps: UsersApiDeps = {
  listUsers,
  getUser,
  createUser,
  updateUser,
  deactivateUser,
  activateUser,
  resetUserPassword,
  deleteUser,
  checkUserDeletion,
  checkUserDeactivation,
  previewBulkUserActions,
  applyBulkUserActions,
  checkUsernameAvailability,
  listActiveRoles,
  listUserAuditEvents,
};
