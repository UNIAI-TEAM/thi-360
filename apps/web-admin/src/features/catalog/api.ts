import type { components, operations } from "@thi360/api-client";
import { createBrowserApiClient } from "@/lib/api/client";
import { ApiStatusError } from "@/lib/api/query-client";

export type CatalogGroupResponse = components["schemas"]["CatalogGroupResponse"];
export type CatalogItemResponse = components["schemas"]["CatalogItemResponse"];
export type CreateCatalogGroupRequest = components["schemas"]["CreateCatalogGroupRequest"];
export type UpdateCatalogGroupRequest = components["schemas"]["UpdateCatalogGroupRequest"];
export type CreateCatalogItemRequest = components["schemas"]["CreateCatalogItemRequest"];
export type UpdateCatalogItemRequest = components["schemas"]["UpdateCatalogItemRequest"];
export type ReorderCatalogItemsRequest = components["schemas"]["ReorderCatalogItemsRequest"];
export type CatalogOptionResponse = components["schemas"]["CatalogOptionResponse"];
export type CatalogUsageResponse = components["schemas"]["CatalogUsageResponse"];
export type CatalogUsagesResponse = components["schemas"]["CatalogUsagesResponse"];
export type CombinationSubjectResponse = components["schemas"]["CombinationSubjectResponse"];
export type CombinationVersionResponse = components["schemas"]["CombinationVersionResponse"];
export type AuditEventResponse = components["schemas"]["AuditEventResponse"];
export type PageResponseAuditEventResponse = components["schemas"]["PageResponseAuditEventResponse"];
export type PageResponseCatalogGroupResponse =
  components["schemas"]["PageResponseCatalogGroupResponse"];
export type PageResponseCatalogItemResponse =
  components["schemas"]["PageResponseCatalogItemResponse"];
export type FieldError = components["schemas"]["FieldError"];
export type CatalogGroupKind = CatalogGroupResponse["kind"];
export type CatalogGroupType = CatalogGroupResponse["type"];
export type CatalogStatus = CatalogGroupResponse["status"];

export type ListCatalogGroupsQuery = operations["listGroups"]["parameters"]["query"];
export type ListCatalogItemsQuery = operations["listItems"]["parameters"]["query"];
export type ListAuditEventsQuery = operations["listAuditEvents"]["parameters"]["query"];
export class CatalogApiError extends ApiStatusError {
  readonly code?: string;
  readonly fieldErrors: FieldError[];

  constructor(status: number, message = "", code?: string, fieldErrors: FieldError[] = []) {
    super(status, message);
    this.name = "CatalogApiError";
    this.code = code;
    this.fieldErrors = fieldErrors;
  }
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

function toCatalogApiError(status: number, error: unknown): CatalogApiError {
  if (typeof error === "object" && error !== null) {
    const message = "message" in error && typeof error.message === "string" ? error.message : "";
    const code = "code" in error && typeof error.code === "string" ? error.code : undefined;
    const fieldErrors =
      "fieldErrors" in error && Array.isArray(error.fieldErrors)
        ? error.fieldErrors.filter(isFieldError)
        : [];
    return new CatalogApiError(status, message, code, fieldErrors);
  }
  return new CatalogApiError(status);
}

export const catalogQueryKeys = {
  all: ["catalog"] as const,
  groups: (query?: ListCatalogGroupsQuery) => ["catalog", "groups", query ?? {}] as const,
  items: (groupId: string, query?: ListCatalogItemsQuery) =>
    ["catalog", "items", groupId, query ?? {}] as const,
  options: (groupCode: string) => ["catalog", "options", groupCode] as const,
  itemUsages: (itemId: string) => ["catalog", "item-usages", itemId] as const,
  itemVersions: (itemId: string) => ["catalog", "item-versions", itemId] as const,
  itemAudit: (itemId: string, page?: number) => ["catalog", "item-audit", itemId, page ?? 0] as const,
};
export interface CatalogApi {
  listGroups: (query?: ListCatalogGroupsQuery) => Promise<PageResponseCatalogGroupResponse>;
  getGroup: (id: string) => Promise<CatalogGroupResponse>;
  createGroup: (payload: CreateCatalogGroupRequest) => Promise<CatalogGroupResponse>;
  updateGroup: (id: string, payload: UpdateCatalogGroupRequest) => Promise<CatalogGroupResponse>;
  deleteGroup: (id: string, reason: string) => Promise<void>;
  listItems: (
    groupId: string,
    query?: ListCatalogItemsQuery,
  ) => Promise<PageResponseCatalogItemResponse>;
  createItem: (
    groupId: string,
    payload: CreateCatalogItemRequest,
  ) => Promise<CatalogItemResponse>;
  updateItem: (
    itemId: string,
    payload: UpdateCatalogItemRequest,
  ) => Promise<CatalogItemResponse>;
  deleteItem: (itemId: string, reason: string) => Promise<void>;
  reorderItems: (
    groupId: string,
    payload: ReorderCatalogItemsRequest,
  ) => Promise<CatalogItemResponse[]>;
  getOptions: (groupCode: string) => Promise<CatalogOptionResponse[]>;
  getItemUsages: (itemId: string) => Promise<CatalogUsagesResponse>;
  listItemVersions: (itemId: string) => Promise<CombinationVersionResponse[]>;
  getCombinationVersion: (versionId: string) => Promise<CombinationVersionResponse>;
  listItemAuditEvents: (
    itemId: string,
    query?: Omit<ListAuditEventsQuery, "targetType" | "targetId">,
  ) => Promise<PageResponseAuditEventResponse>;
}
export async function listCatalogGroups(
  query?: ListCatalogGroupsQuery,
): Promise<PageResponseCatalogGroupResponse> {
  const client = createBrowserApiClient();
  const { data, error, response } = await client.GET("/api/v1/admin/catalog/groups", {
    params: { query: { size: 100, ...query } },
  });
  if (!response.ok || !data) throw toCatalogApiError(response.status, error);
  return data;
}

export async function getCatalogGroup(id: string): Promise<CatalogGroupResponse> {
  const client = createBrowserApiClient();
  const { data, error, response } = await client.GET("/api/v1/admin/catalog/groups/{id}", {
    params: { path: { id } },
  });
  if (!response.ok || !data) throw toCatalogApiError(response.status, error);
  return data;
}

export async function createCatalogGroup(
  payload: CreateCatalogGroupRequest,
): Promise<CatalogGroupResponse> {
  const client = createBrowserApiClient();
  const { data, error, response } = await client.POST("/api/v1/admin/catalog/groups", {
    body: payload,
  });
  if (!response.ok || !data) throw toCatalogApiError(response.status, error);
  return data;
}

export async function updateCatalogGroup(
  id: string,
  payload: UpdateCatalogGroupRequest,
): Promise<CatalogGroupResponse> {
  const client = createBrowserApiClient();
  const { data, error, response } = await client.PATCH("/api/v1/admin/catalog/groups/{id}", {
    params: { path: { id } },
    body: payload,
  });
  if (!response.ok || !data) throw toCatalogApiError(response.status, error);
  return data;
}

export async function deleteCatalogGroup(id: string, reason: string): Promise<void> {
  const client = createBrowserApiClient();
  const { error, response } = await client.DELETE("/api/v1/admin/catalog/groups/{id}", {
    params: { path: { id }, query: { reason } },
  });
  if (!response.ok) throw toCatalogApiError(response.status, error);
}

export async function listCatalogItems(
  groupId: string,
  query?: ListCatalogItemsQuery,
): Promise<PageResponseCatalogItemResponse> {
  const client = createBrowserApiClient();
  const { data, error, response } = await client.GET("/api/v1/admin/catalog/groups/{id}/items", {
    params: { path: { id: groupId }, query },
  });
  if (!response.ok || !data) throw toCatalogApiError(response.status, error);
  return data;
}

export async function createCatalogItem(
  groupId: string,
  payload: CreateCatalogItemRequest,
): Promise<CatalogItemResponse> {
  const client = createBrowserApiClient();
  const { data, error, response } = await client.POST("/api/v1/admin/catalog/groups/{id}/items", {
    params: { path: { id: groupId } },
    body: payload,
  });
  if (!response.ok || !data) throw toCatalogApiError(response.status, error);
  return data;
}

export async function updateCatalogItem(
  itemId: string,
  payload: UpdateCatalogItemRequest,
): Promise<CatalogItemResponse> {
  const client = createBrowserApiClient();
  const { data, error, response } = await client.PATCH("/api/v1/admin/catalog/items/{id}", {
    params: { path: { id: itemId } },
    body: payload,
  });
  if (!response.ok || !data) throw toCatalogApiError(response.status, error);
  return data;
}

export async function deleteCatalogItem(itemId: string, reason: string): Promise<void> {
  const client = createBrowserApiClient();
  const { error, response } = await client.DELETE("/api/v1/admin/catalog/items/{id}", {
    params: { path: { id: itemId }, query: { reason } },
  });
  if (!response.ok) throw toCatalogApiError(response.status, error);
}

export async function reorderCatalogItems(
  groupId: string,
  payload: ReorderCatalogItemsRequest,
): Promise<CatalogItemResponse[]> {
  const client = createBrowserApiClient();
  const { data, error, response } = await client.PUT(
    "/api/v1/admin/catalog/groups/{id}/items/order",
    {
      params: { path: { id: groupId } },
      body: payload,
    },
  );
  if (!response.ok || !data) throw toCatalogApiError(response.status, error);
  return data;
}

export async function getCatalogOptions(groupCode: string): Promise<CatalogOptionResponse[]> {
  const client = createBrowserApiClient();
  const { data, error, response } = await client.GET("/api/v1/catalog/options", {
    params: { query: { group: groupCode } },
  });
  if (!response.ok || !data) throw toCatalogApiError(response.status, error);
  return data;
}

export async function getCatalogItemUsages(itemId: string): Promise<CatalogUsagesResponse> {
  const client = createBrowserApiClient();
  const { data, error, response } = await client.GET("/api/v1/admin/catalog/items/{id}/usages", {
    params: { path: { id: itemId } },
  });
  if (!response.ok || !data) throw toCatalogApiError(response.status, error);
  return data;
}

export async function listCatalogItemVersions(
  itemId: string,
): Promise<CombinationVersionResponse[]> {
  const client = createBrowserApiClient();
  const { data, error, response } = await client.GET("/api/v1/admin/catalog/items/{id}/versions", {
    params: { path: { id: itemId } },
  });
  if (!response.ok || !data) throw toCatalogApiError(response.status, error);
  return data;
}

export async function getCombinationVersion(
  versionId: string,
): Promise<CombinationVersionResponse> {
  const client = createBrowserApiClient();
  const { data, error, response } = await client.GET(
    "/api/v1/admin/catalog/combination-versions/{versionId}",
    {
      params: { path: { versionId } },
    },
  );
  if (!response.ok || !data) throw toCatalogApiError(response.status, error);
  return data;
}

export async function listCatalogItemAuditEvents(
  itemId: string,
  query?: Omit<ListAuditEventsQuery, "targetType" | "targetId">,
): Promise<PageResponseAuditEventResponse> {
  const client = createBrowserApiClient();
  const { data, error, response } = await client.GET("/api/v1/admin/audit-events", {
    params: {
      query: {
        targetType: "CATALOG_ITEM",
        targetId: itemId,
        sort: "occurredAt,desc",
        ...query,
      },
    },
  });
  if (!response.ok || !data) throw toCatalogApiError(response.status, error);
  return data;
}

export const defaultCatalogApi: CatalogApi = {
  listGroups: listCatalogGroups,
  getGroup: getCatalogGroup,
  createGroup: createCatalogGroup,
  updateGroup: updateCatalogGroup,
  deleteGroup: deleteCatalogGroup,
  listItems: listCatalogItems,
  createItem: createCatalogItem,
  updateItem: updateCatalogItem,
  deleteItem: deleteCatalogItem,
  reorderItems: reorderCatalogItems,
  getOptions: getCatalogOptions,
  getItemUsages: getCatalogItemUsages,
  listItemVersions: listCatalogItemVersions,
  getCombinationVersion: getCombinationVersion,
  listItemAuditEvents: listCatalogItemAuditEvents,
};
