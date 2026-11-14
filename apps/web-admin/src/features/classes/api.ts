import type { ApiError, components, FieldError, operations } from "@thi360/api-client";
import { createBrowserApiClient } from "@/lib/api/client";
import { ApiStatusError } from "@/lib/api/query-client";

export type ClassSummary = components["schemas"]["ClassSummaryResponse"];
export type ClassDetail = components["schemas"]["ClassDetailResponse"];
export type ClassMember = components["schemas"]["ClassMemberResponse"];
export type CreateClassRequest = components["schemas"]["CreateClassRequest"];
export type UpdateClassRequest = components["schemas"]["UpdateClassRequest"];
export type UpdateClassStatusRequest = components["schemas"]["UpdateClassStatusRequest"];
export type AddClassMembersRequest = components["schemas"]["AddClassMembersRequest"];
export type AddClassMembersResponse = components["schemas"]["AddClassMembersResponse"];
export type TransferClassMemberRequest = components["schemas"]["TransferClassMemberRequest"];
export type TransferClassMemberResponse = components["schemas"]["TransferClassMemberResponse"];
export type UserClassesResponse = components["schemas"]["UserClassesResponse"];
export type UserCurrentClass = components["schemas"]["UserCurrentClassResponse"];
export type UserClassHistory = components["schemas"]["UserClassHistoryResponse"];
export type PageResponseClassSummary = components["schemas"]["PageResponseClassSummaryResponse"];
export type PageResponseClassMember = components["schemas"]["PageResponseClassMemberResponse"];
export type ClassStatus = ClassSummary["status"];
export type ClassMemberHistoryAction = UserClassHistory["action"];

export type ListClassesQuery = NonNullable<operations["listClasses"]["parameters"]["query"]>;
export type ListClassMembersQuery = NonNullable<operations["listMembers"]["parameters"]["query"]>;

export const CLASSES_QUERY_KEYS = {
  all: ["classes"] as const,
  lists: () => ["classes", "list"] as const,
  list: (query: ListClassesQuery) => ["classes", "list", query] as const,
  options: () => ["classes", "options"] as const,
  detail: (id: string) => ["classes", "detail", id] as const,
  membersRoot: (id: string) => ["classes", "members", id] as const,
  members: (id: string, query: ListClassMembersQuery) => ["classes", "members", id, query] as const,
  userClasses: (userId: string) => ["classes", "user", userId] as const,
};

export class ClassesApiError extends ApiStatusError {
  readonly code?: string;
  readonly fieldErrors: readonly FieldError[];
  readonly httpStatus: number;

  constructor(status: number, message = "", code?: string, fieldErrors: readonly FieldError[] = []) {
    super(status, message);
    this.name = "ClassesApiError";
    this.httpStatus = status;
    this.code = code;
    this.fieldErrors = fieldErrors;
  }
}

export function toClassesApiError(status: number, error: unknown): ClassesApiError {
  if (typeof error === "object" && error !== null) {
    const body = error as Partial<ApiError>;
    const code = typeof body.code === "string" ? body.code : undefined;
    const message = typeof body.message === "string" ? body.message : "";
    const fieldErrors = Array.isArray(body.fieldErrors) ? body.fieldErrors : [];
    return new ClassesApiError(status, message, code, fieldErrors);
  }
  return new ClassesApiError(status);
}

export async function listClasses(query: ListClassesQuery): Promise<PageResponseClassSummary> {
  const client = createBrowserApiClient();
  const { data, error, response } = await client.GET("/api/v1/admin/classes", {
    params: { query },
  });
  if (!response.ok || !data) throw toClassesApiError(response.status, error);
  return data;
}

export async function listAllClasses(
  status?: ClassStatus,
  fetchPage: (query: ListClassesQuery) => Promise<PageResponseClassSummary> = listClasses,
): Promise<ClassSummary[]> {
  const first = await fetchPage({
    ...(status ? { status } : {}),
    page: 0,
    size: 100,
    sort: "code,asc",
  });
  if (first.totalPages <= 1) {
    return first.items;
  }
  const items = [...first.items];
  for (let page = 1; page < first.totalPages; page += 1) {
    const next = await fetchPage({
      ...(status ? { status } : {}),
      page,
      size: 100,
      sort: "code,asc",
    });
    items.push(...next.items);
  }
  return items;
}

export async function getClass(id: string): Promise<ClassDetail> {
  const client = createBrowserApiClient();
  const { data, error, response } = await client.GET("/api/v1/admin/classes/{id}", {
    params: { path: { id } },
  });
  if (!response.ok || !data) throw toClassesApiError(response.status, error);
  return data;
}

export async function createClass(body: CreateClassRequest): Promise<ClassDetail> {
  const client = createBrowserApiClient();
  const { data, error, response } = await client.POST("/api/v1/admin/classes", {
    body,
  });
  if (!response.ok || !data) throw toClassesApiError(response.status, error);
  return data;
}

export async function updateClass(id: string, body: UpdateClassRequest): Promise<ClassDetail> {
  const client = createBrowserApiClient();
  const { data, error, response } = await client.PATCH("/api/v1/admin/classes/{id}", {
    params: { path: { id } },
    body,
  });
  if (!response.ok || !data) throw toClassesApiError(response.status, error);
  return data;
}

export async function updateClassStatus(
  id: string,
  body: UpdateClassStatusRequest,
): Promise<ClassDetail> {
  const client = createBrowserApiClient();
  const { data, error, response } = await client.PATCH("/api/v1/admin/classes/{id}/status", {
    params: { path: { id } },
    body,
  });
  if (!response.ok || !data) throw toClassesApiError(response.status, error);
  return data;
}

export async function deleteClass(id: string, reason: string): Promise<void> {
  const client = createBrowserApiClient();
  const { error, response } = await client.DELETE("/api/v1/admin/classes/{id}", {
    params: { path: { id }, query: { reason } },
  });
  if (!response.ok) throw toClassesApiError(response.status, error);
}

export async function listClassMembers(
  id: string,
  query: ListClassMembersQuery,
): Promise<PageResponseClassMember> {
  const client = createBrowserApiClient();
  const { data, error, response } = await client.GET("/api/v1/admin/classes/{id}/members", {
    params: { path: { id }, query },
  });
  if (!response.ok || !data) throw toClassesApiError(response.status, error);
  return data;
}

export async function addClassMembers(
  id: string,
  body: AddClassMembersRequest,
): Promise<AddClassMembersResponse> {
  const client = createBrowserApiClient();
  const { data, error, response } = await client.POST("/api/v1/admin/classes/{id}/members", {
    params: { path: { id } },
    body,
  });
  if (!response.ok || !data) throw toClassesApiError(response.status, error);
  return data;
}

export async function removeClassMember(
  id: string,
  userId: string,
  reason?: string,
): Promise<void> {
  const client = createBrowserApiClient();
  const { error, response } = await client.DELETE(
    "/api/v1/admin/classes/{id}/members/{userId}",
    {
      params: {
        path: { id, userId },
        ...(reason ? { query: { reason } } : {}),
      },
    },
  );
  if (!response.ok) throw toClassesApiError(response.status, error);
}

export async function transferClassMember(
  id: string,
  userId: string,
  body: TransferClassMemberRequest,
): Promise<TransferClassMemberResponse> {
  const client = createBrowserApiClient();
  const { data, error, response } = await client.POST(
    "/api/v1/admin/classes/{id}/members/{userId}/transfer",
    {
      params: { path: { id, userId } },
      body,
    },
  );
  if (!response.ok || !data) throw toClassesApiError(response.status, error);
  return data;
}

export async function getUserClasses(userId: string): Promise<UserClassesResponse> {
  const client = createBrowserApiClient();
  const { data, error, response } = await client.GET("/api/v1/admin/users/{id}/classes", {
    params: { path: { id: userId } },
  });
  if (!response.ok || !data) throw toClassesApiError(response.status, error);
  return data;
}

export interface ClassesApiDeps {
  listClasses: typeof listClasses;
  listAllClasses: typeof listAllClasses;
  getClass: typeof getClass;
  createClass: typeof createClass;
  updateClass: typeof updateClass;
  updateClassStatus: typeof updateClassStatus;
  deleteClass: typeof deleteClass;
  listClassMembers: typeof listClassMembers;
  addClassMembers: typeof addClassMembers;
  removeClassMember: typeof removeClassMember;
  transferClassMember: typeof transferClassMember;
  getUserClasses: typeof getUserClasses;
}

export const defaultClassesApiDeps: ClassesApiDeps = {
  listClasses,
  listAllClasses,
  getClass,
  createClass,
  updateClass,
  updateClassStatus,
  deleteClass,
  listClassMembers,
  addClassMembers,
  removeClassMember,
  transferClassMember,
  getUserClasses,
};
