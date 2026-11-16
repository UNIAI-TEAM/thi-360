import { createBrowserApiClient } from "@/lib/api/client";
import { toContentApiError } from "./api-error";

/**
 * Tra id giá trị danh mục theo mã (và ngược lại) qua `GET /catalog/options`. Bản chụp phiên bản và phân loại riêng của
 * câu con trong API câu hỏi dùng id giá trị danh mục, trong khi `ContentReference` chỉ có mã.
 */
export type CatalogGroupCode = "SUBJECT" | "GRADE" | "QUESTION_LEVEL" | "TASK_TYPE" | "COMPETENCY" | "QUESTION_TAG";

export interface CatalogItemRef {
  id: string;
  code: string;
}

const cache = new Map<CatalogGroupCode, Promise<CatalogItemRef[]>>();

async function fetchItems(group: CatalogGroupCode): Promise<CatalogItemRef[]> {
  const client = createBrowserApiClient();
  const { data, error, response } = await client.GET("/api/v1/catalog/options", { params: { query: { group } } });
  if (!response.ok || !data) throw toContentApiError(response.status, error);
  return data.map((option) => ({ id: option.id, code: option.code }));
}

/** Danh sách giá trị đang hoạt động của một nhóm; nhớ lại trong phiên, lỗi thì không nhớ để lần sau thử lại. */
export function loadCatalogItems(group: CatalogGroupCode): Promise<CatalogItemRef[]> {
  const cached = cache.get(group);
  if (cached) return cached;
  const request = fetchItems(group);
  cache.set(group, request);
  request.catch(() => cache.delete(group));
  return request;
}

/** Mã -> id của một nhóm; mã không có trong nhóm thì không có khóa. */
export async function loadCodeToId(group: CatalogGroupCode): Promise<Map<string, string>> {
  return new Map((await loadCatalogItems(group)).map((item) => [item.code, item.id]));
}

/** Id -> mã của một nhóm. */
export async function loadIdToCode(group: CatalogGroupCode): Promise<Map<string, string>> {
  return new Map((await loadCatalogItems(group)).map((item) => [item.id, item.code]));
}

/** Xóa bản nhớ (test). */
export function resetCatalogLookup(): void {
  cache.clear();
}
