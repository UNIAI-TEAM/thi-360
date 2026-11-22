import type { paths } from "@thi360/api-client";
import { createBrowserApiClient } from "@/lib/api/client";
import { toGradingApiError } from "./errors";
import type {
  EssayGradeView,
  ExamKindCatalogOption,
  FinalizeEssayRequest,
  FinalizeEssayResponse,
  GradingCountsQuery,
  GradingItemDetailResponse,
  GradingQueueCountsResponse,
  ListGradingItemsQuery,
  PageResponseGradingQueueItemView,
  ReopenEssayRequest,
  SaveEssayDraftRequest,
} from "./types";

export { GRADING_QUERY_KEYS } from "./keys";
export {
  GRADING_ERROR_CODES,
  GradingApiError,
  gradingErrorCode,
  resolveGradingErrorMessage,
  toGradingApiError,
  type GradingErrorCode,
  type GradingErrorTranslator,
} from "./errors";

export async function listGradingItems(
  params: ListGradingItemsQuery,
  signal?: AbortSignal,
): Promise<PageResponseGradingQueueItemView> {
  const client = createBrowserApiClient();
  const query: NonNullable<paths["/api/v1/admin/grading/items"]["get"]["parameters"]["query"]> = {
    page: params.page ?? 0,
    size: params.size ?? 20,
  };
  if (params.state) query.state = params.state;
  const exam = params.exam?.trim();
  if (exam) query.exam = exam;
  if (params.kind) query.kind = params.kind;
  if (params.from) query.from = params.from;
  if (params.to) query.to = params.to;

  const { data, error, response } = await client.GET("/api/v1/admin/grading/items", {
    params: { query },
    signal,
  });
  if (error || !data) {
    throw toGradingApiError(response.status, error);
  }
  return data;
}

export async function getGradingItemCounts(
  params: GradingCountsQuery,
  signal?: AbortSignal,
): Promise<GradingQueueCountsResponse> {
  const client = createBrowserApiClient();
  const query: NonNullable<paths["/api/v1/admin/grading/items/counts"]["get"]["parameters"]["query"]> = {};
  const exam = params.exam?.trim();
  if (exam) query.exam = exam;
  if (params.kind) query.kind = params.kind;
  if (params.from) query.from = params.from;
  if (params.to) query.to = params.to;

  const { data, error, response } = await client.GET("/api/v1/admin/grading/items/counts", {
    params: { query },
    signal,
  });
  if (error || !data) {
    throw toGradingApiError(response.status, error);
  }
  return data;
}

export async function getGradingItemDetail(
  itemId: string,
  signal?: AbortSignal,
): Promise<GradingItemDetailResponse> {
  const client = createBrowserApiClient();
  const { data, error, response } = await client.GET("/api/v1/admin/grading/items/{itemId}", {
    params: { path: { itemId } },
    signal,
  });
  if (error || !data) {
    throw toGradingApiError(response.status, error);
  }
  return data;
}

export async function claimGradingItem(itemId: string): Promise<EssayGradeView> {
  const client = createBrowserApiClient();
  const { data, error, response } = await client.POST("/api/v1/admin/grading/items/{itemId}/claim", {
    params: { path: { itemId } },
  });
  if (error || !data) {
    throw toGradingApiError(response.status, error);
  }
  return data;
}

export async function saveEssayDraft(
  itemId: string,
  body: SaveEssayDraftRequest,
): Promise<EssayGradeView> {
  const client = createBrowserApiClient();
  const { data, error, response } = await client.PUT("/api/v1/admin/grading/items/{itemId}", {
    params: { path: { itemId } },
    body,
  });
  if (error || !data) {
    throw toGradingApiError(response.status, error);
  }
  return data;
}

export async function finalizeEssayGrade(
  itemId: string,
  body: FinalizeEssayRequest,
): Promise<FinalizeEssayResponse> {
  const client = createBrowserApiClient();
  const { data, error, response } = await client.POST("/api/v1/admin/grading/items/{itemId}/finalize", {
    params: { path: { itemId } },
    body,
  });
  if (error || !data) {
    throw toGradingApiError(response.status, error);
  }
  return data;
}

export async function reopenEssayGrade(
  itemId: string,
  body: ReopenEssayRequest,
): Promise<EssayGradeView> {
  const client = createBrowserApiClient();
  const { data, error, response } = await client.POST("/api/v1/admin/grading/items/{itemId}/reopen", {
    params: { path: { itemId } },
    body,
  });
  if (error || !data) {
    throw toGradingApiError(response.status, error);
  }
  return data;
}

export async function fetchExamKindOptions(signal?: AbortSignal): Promise<ExamKindCatalogOption[]> {
  const client = createBrowserApiClient();
  const { data, error, response } = await client.GET("/api/v1/catalog/options", {
    params: { query: { group: "EXAM" } },
    signal,
  });
  if (error || !data) {
    throw toGradingApiError(response.status, error);
  }
  return data.map((item) => ({
    id: item.id,
    code: item.code,
    nameVi: item.nameVi,
    ...(item.nameEn ? { nameEn: item.nameEn } : {}),
  }));
}

export interface GradingApiDeps {
  listGradingItems: typeof listGradingItems;
  getGradingItemCounts: typeof getGradingItemCounts;
  getGradingItemDetail: typeof getGradingItemDetail;
  claimGradingItem: typeof claimGradingItem;
  saveEssayDraft: typeof saveEssayDraft;
  finalizeEssayGrade: typeof finalizeEssayGrade;
  reopenEssayGrade: typeof reopenEssayGrade;
  fetchExamKindOptions: typeof fetchExamKindOptions;
}

export const defaultGradingApiDeps: GradingApiDeps = {
  listGradingItems,
  getGradingItemCounts,
  getGradingItemDetail,
  claimGradingItem,
  saveEssayDraft,
  finalizeEssayGrade,
  reopenEssayGrade,
  fetchExamKindOptions,
};
