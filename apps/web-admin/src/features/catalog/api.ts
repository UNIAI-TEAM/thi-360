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
export type CatalogTreeNodeResponse = components["schemas"]["CatalogTreeNodeResponse"];
export type CatalogDescendantNodeResponse = components["schemas"]["CatalogDescendantNodeResponse"];
export type CatalogGroupRefResponse = components["schemas"]["CatalogGroupRefResponse"];
export type CatalogItemRefResponse = components["schemas"]["CatalogItemRefResponse"];
export type CatalogInheritedRefResponse = components["schemas"]["CatalogInheritedRefResponse"];
export type CatalogOptionResponse = components["schemas"]["CatalogOptionResponse"];
export type CatalogUsageResponse = components["schemas"]["CatalogUsageResponse"];
export type CatalogUsagesResponse = components["schemas"]["CatalogUsagesResponse"];
export type CombinationSubjectResponse = components["schemas"]["CombinationSubjectResponse"];
export type CombinationVersionResponse = components["schemas"]["CombinationVersionResponse"];
export type AuditEventResponse = components["schemas"]["AuditEventResponse"];
export type PageResponseAuditEventResponse = components["schemas"]["PageResponseAuditEventResponse"];
export type CatalogImportPreviewResponse = components["schemas"]["CatalogImportPreviewResponse"];
export type CatalogImportResultResponse = components["schemas"]["CatalogImportResultResponse"];
export type CatalogImportRowError = components["schemas"]["CatalogImportRowError"];
export type ExportCatalogItemsResult = {
  blob: Blob;
  filename: string;
};
export type PageResponseCatalogGroupResponse =
  components["schemas"]["PageResponseCatalogGroupResponse"];
export type PageResponseCatalogItemResponse =
  components["schemas"]["PageResponseCatalogItemResponse"];
export type MoveCatalogItemRequest = components["schemas"]["MoveCatalogItemRequest"];
export type FieldError = components["schemas"]["FieldError"];
export type CatalogGroupKind = CatalogGroupResponse["kind"];
export type CatalogGroupType = CatalogGroupResponse["type"];
export type CatalogStatus = CatalogGroupResponse["status"];

export type ListCatalogGroupsQuery = operations["listGroups"]["parameters"]["query"];
export type ListCatalogItemsQuery = operations["listItems"]["parameters"]["query"];
export type ListCatalogTreeQuery = operations["listTree"]["parameters"]["query"];
export type GetCatalogDescendantsQuery = operations["getDescendants"]["parameters"]["query"];
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
  item: (itemId: string) => ["catalog", "item", itemId] as const,
  tree: (groupId: string, query?: ListCatalogTreeQuery) =>
    ["catalog", "tree", groupId, query ?? {}] as const,
  refGroups: (groupId: string) => ["catalog", "ref-groups", groupId] as const,
  descendants: (nodeId: string, leafOnly?: boolean) =>
    ["catalog", "descendants", nodeId, leafOnly ?? true] as const,
  itemUsages: (itemId: string) => ["catalog", "item-usages", itemId] as const,
  groupUsages: (groupId: string) => ["catalog", "group-usages", groupId] as const,
  itemVersions: (itemId: string) => ["catalog", "item-versions", itemId] as const,
  itemAudit: (itemId: string, page?: number) => ["catalog", "item-audit", itemId, page ?? 0] as const,
};
export interface CatalogApi {
  listGroups: (query?: ListCatalogGroupsQuery) => Promise<PageResponseCatalogGroupResponse>;
  createGroup: (payload: CreateCatalogGroupRequest) => Promise<CatalogGroupResponse>;
  updateGroup: (id: string, payload: UpdateCatalogGroupRequest) => Promise<CatalogGroupResponse>;
  deleteGroup: (id: string, reason: string) => Promise<void>;
  getGroupUsages: (groupId: string) => Promise<CatalogUsagesResponse>;
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
  getItem: (itemId: string) => Promise<CatalogItemResponse>;
  listTree: (
    groupId: string,
    query?: ListCatalogTreeQuery,
  ) => Promise<CatalogTreeNodeResponse[]>;
  moveItem: (
    itemId: string,
    payload: MoveCatalogItemRequest,
  ) => Promise<CatalogItemResponse>;
  listRefGroups: (groupId: string) => Promise<CatalogGroupRefResponse[]>;
  addRefGroup: (groupId: string, refGroupId: string) => Promise<CatalogGroupRefResponse[]>;
  removeRefGroup: (groupId: string, refGroupId: string) => Promise<void>;
  addItemRef: (itemId: string, refItemId: string) => Promise<CatalogItemResponse>;
  removeItemRef: (itemId: string, refItemId: string) => Promise<void>;
  getDescendants: (
    query?: GetCatalogDescendantsQuery,
  ) => Promise<CatalogDescendantNodeResponse[]>;
  getItemUsages: (itemId: string) => Promise<CatalogUsagesResponse>;
  listItemVersions: (itemId: string) => Promise<CombinationVersionResponse[]>;
  listItemAuditEvents: (
    itemId: string,
    query?: Omit<ListAuditEventsQuery, "targetType" | "targetId">,
  ) => Promise<PageResponseAuditEventResponse>;
  exportItems: (groupId: string) => Promise<ExportCatalogItemsResult>;
  previewImport: (groupId: string, file: File) => Promise<CatalogImportPreviewResponse>;
  confirmImport: (
    groupId: string,
    previewId: string,
    idempotencyKey?: string,
  ) => Promise<CatalogImportResultResponse>;
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

export async function getCatalogGroupUsages(groupId: string): Promise<CatalogUsagesResponse> {
  const client = createBrowserApiClient();
  const { data, error, response } = await client.GET("/api/v1/admin/catalog/groups/{id}/usages", {
    params: { path: { id: groupId } },
  });
  if (!response.ok || !data) throw toCatalogApiError(response.status, error);
  return data;
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

export async function getCatalogItem(itemId: string): Promise<CatalogItemResponse> {
  const client = createBrowserApiClient();
  const { data, error, response } = await client.GET("/api/v1/admin/catalog/items/{id}", {
    params: { path: { id: itemId } },
  });
  if (!response.ok || !data) throw toCatalogApiError(response.status, error);
  return data;
}

export async function listCatalogTree(
  groupId: string,
  query?: ListCatalogTreeQuery,
): Promise<CatalogTreeNodeResponse[]> {
  const client = createBrowserApiClient();
  const { data, error, response } = await client.GET("/api/v1/admin/catalog/groups/{id}/tree", {
    params: { path: { id: groupId }, query },
  });
  if (!response.ok || !data) throw toCatalogApiError(response.status, error);
  return data;
}

export async function moveCatalogItem(
  itemId: string,
  payload: MoveCatalogItemRequest,
): Promise<CatalogItemResponse> {
  const client = createBrowserApiClient();
  const { data, error, response } = await client.PUT("/api/v1/admin/catalog/items/{id}/parent", {
    params: { path: { id: itemId } },
    body: payload,
  });
  if (!response.ok || !data) throw toCatalogApiError(response.status, error);
  return data;
}

export async function listCatalogRefGroups(
  groupId: string,
): Promise<CatalogGroupRefResponse[]> {
  const client = createBrowserApiClient();
  const { data, error, response } = await client.GET(
    "/api/v1/admin/catalog/groups/{id}/ref-groups",
    {
      params: { path: { id: groupId } },
    },
  );
  if (!response.ok || !data) throw toCatalogApiError(response.status, error);
  return data;
}

export async function addCatalogRefGroup(
  groupId: string,
  refGroupId: string,
): Promise<CatalogGroupRefResponse[]> {
  const client = createBrowserApiClient();
  const { data, error, response } = await client.PUT(
    "/api/v1/admin/catalog/groups/{id}/ref-groups/{refGroupId}",
    {
      params: { path: { id: groupId, refGroupId } },
    },
  );
  if (!response.ok || !data) throw toCatalogApiError(response.status, error);
  return data;
}

export async function removeCatalogRefGroup(
  groupId: string,
  refGroupId: string,
): Promise<void> {
  const client = createBrowserApiClient();
  const { error, response } = await client.DELETE(
    "/api/v1/admin/catalog/groups/{id}/ref-groups/{refGroupId}",
    {
      params: { path: { id: groupId, refGroupId } },
    },
  );
  if (!response.ok) throw toCatalogApiError(response.status, error);
}

export async function addCatalogItemRef(
  itemId: string,
  refItemId: string,
): Promise<CatalogItemResponse> {
  const client = createBrowserApiClient();
  const { data, error, response } = await client.PUT(
    "/api/v1/admin/catalog/items/{id}/refs/{refItemId}",
    {
      params: { path: { id: itemId, refItemId } },
    },
  );
  if (!response.ok || !data) throw toCatalogApiError(response.status, error);
  return data;
}

export async function removeCatalogItemRef(itemId: string, refItemId: string): Promise<void> {
  const client = createBrowserApiClient();
  const { error, response } = await client.DELETE(
    "/api/v1/admin/catalog/items/{id}/refs/{refItemId}",
    {
      params: { path: { id: itemId, refItemId } },
    },
  );
  if (!response.ok) throw toCatalogApiError(response.status, error);
}

export async function getCatalogDescendants(
  query?: GetCatalogDescendantsQuery,
): Promise<CatalogDescendantNodeResponse[]> {
  const client = createBrowserApiClient();
  const { data, error, response } = await client.GET("/api/v1/catalog/tree/descendants", {
    params: { query },
  });
  if (!response.ok || !data) throw toCatalogApiError(response.status, error);
  return data;
}

function parseDispositionFilename(header: string | null): string | null {
  if (!header) return null;
  const utf8Match = /filename\*=UTF-8''([^;\n]+)/i.exec(header);
  if (utf8Match?.[1]) {
    try {
      return decodeURIComponent(utf8Match[1].trim().replace(/^"|"$/g, ""));
    } catch {
      return utf8Match[1].trim().replace(/^"|"$/g, "");
    }
  }
  const plainMatch = /filename="?([^";\n]+)"?/i.exec(header);
  return plainMatch?.[1]?.trim() ?? null;
}

export async function exportCatalogItems(groupId: string): Promise<ExportCatalogItemsResult> {
  const client = createBrowserApiClient();
  const { data, error, response } = await client.GET(
    "/api/v1/admin/catalog/groups/{id}/items/export",
    {
      params: { path: { id: groupId } },
      parseAs: "blob",
    },
  );
  if (!response.ok || !(data instanceof Blob)) {
    throw toCatalogApiError(response.status, error);
  }
  const filename =
    parseDispositionFilename(response.headers.get("content-disposition")) ??
    `catalog-${groupId}.xlsx`;
  return { blob: data, filename };
}

export async function previewCatalogImport(
  groupId: string,
  file: File,
): Promise<CatalogImportPreviewResponse> {
  const client = createBrowserApiClient();
  const formData = new FormData();
  formData.append("file", file);
  const { data, error, response } = await client.POST(
    "/api/v1/admin/catalog/groups/{id}/items/import",
    {
      params: { path: { id: groupId } },
      body: formData as unknown as { file: string },
      bodySerializer: (body) => body as unknown as BodyInit,
    },
  );
  if (!response.ok || !data) throw toCatalogApiError(response.status, error);
  return data;
}

export async function confirmCatalogImport(
  groupId: string,
  previewId: string,
  idempotencyKey?: string,
): Promise<CatalogImportResultResponse> {
  const client = createBrowserApiClient();
  const { data, error, response } = await client.POST(
    "/api/v1/admin/catalog/groups/{id}/items/import/{previewId}/confirm",
    {
      params: {
        path: { id: groupId, previewId },
        ...(idempotencyKey ? { header: { "Idempotency-Key": idempotencyKey } } : {}),
      },
    },
  );
  if (!response.ok || !data) throw toCatalogApiError(response.status, error);
  return data;
}

export const defaultCatalogApi: CatalogApi = {
  listGroups: listCatalogGroups,
  createGroup: createCatalogGroup,
  updateGroup: updateCatalogGroup,
  deleteGroup: deleteCatalogGroup,
  getGroupUsages: getCatalogGroupUsages,
  listItems: listCatalogItems,
  createItem: createCatalogItem,
  updateItem: updateCatalogItem,
  deleteItem: deleteCatalogItem,
  reorderItems: reorderCatalogItems,
  getOptions: getCatalogOptions,
  getItem: getCatalogItem,
  listTree: listCatalogTree,
  moveItem: moveCatalogItem,
  listRefGroups: listCatalogRefGroups,
  addRefGroup: addCatalogRefGroup,
  removeRefGroup: removeCatalogRefGroup,
  addItemRef: addCatalogItemRef,
  removeItemRef: removeCatalogItemRef,
  getDescendants: getCatalogDescendants,
  getItemUsages: getCatalogItemUsages,
  listItemVersions: listCatalogItemVersions,
  listItemAuditEvents: listCatalogItemAuditEvents,
  exportItems: exportCatalogItems,
  previewImport: previewCatalogImport,
  confirmImport: confirmCatalogImport,
};
