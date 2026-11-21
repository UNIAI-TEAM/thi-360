import { createBrowserApiClient } from "@/lib/api/client";

import { toBundleApiError } from "./errors";
import type {
  BundleCandidateResponse,
  BundleDetailResponse,
  BundlesListQueryParams,
  CreateBundleRequest,
  ExamKindOption,
  PageResponseBundleSummaryResponse,
  UpdateBundleRequest,
} from "./types";

export async function listBundles(
  params: BundlesListQueryParams,
  signal?: AbortSignal,
): Promise<PageResponseBundleSummaryResponse> {
  const client = createBrowserApiClient();
  const query: {
    q?: string;
    examKind?: string;
    status?: "DRAFT" | "PUBLISHED" | "DISCONTINUED";
    page?: number;
    size?: number;
    sort?: string;
  } = {
    page: params.page,
    size: params.size,
  };

  if (params.status !== "ALL") query.status = params.status;
  if (params.examKind) query.examKind = params.examKind;
  const trimmedQ = params.q.trim();
  if (trimmedQ) query.q = trimmedQ;
  if (params.sort) query.sort = params.sort;

  const { data, error, response } = await client.GET("/api/v1/admin/bundles", {
    params: { query },
    signal,
  });
  if (error || !data) {
    throw toBundleApiError(response.status, error);
  }
  return data;
}

export async function getBundle(id: string, signal?: AbortSignal): Promise<BundleDetailResponse> {
  const client = createBrowserApiClient();
  const { data, error, response } = await client.GET("/api/v1/admin/bundles/{id}", {
    params: { path: { id } },
    signal,
  });
  if (error || !data) {
    throw toBundleApiError(response.status, error);
  }
  return data;
}

export async function listBundleCandidates(
  examKind: string,
  signal?: AbortSignal,
): Promise<BundleCandidateResponse[]> {
  const client = createBrowserApiClient();
  const { data, error, response } = await client.GET("/api/v1/admin/bundles/candidates", {
    params: { query: { examKind } },
    signal,
  });
  if (error || !data) {
    throw toBundleApiError(response.status, error);
  }
  return data;
}

export async function createBundle(body: CreateBundleRequest): Promise<BundleDetailResponse> {
  const client = createBrowserApiClient();
  const { data, error, response } = await client.POST("/api/v1/admin/bundles", {
    body,
  });
  if (error || !data) {
    throw toBundleApiError(response.status, error);
  }
  return data;
}

export async function updateBundle(
  id: string,
  body: UpdateBundleRequest,
): Promise<BundleDetailResponse> {
  const client = createBrowserApiClient();
  const { data, error, response } = await client.PUT("/api/v1/admin/bundles/{id}", {
    params: { path: { id } },
    body,
  });
  if (error || !data) {
    throw toBundleApiError(response.status, error);
  }
  return data;
}

export async function publishBundle(
  id: string,
  expectedRevision: number,
): Promise<BundleDetailResponse> {
  const client = createBrowserApiClient();
  const { data, error, response } = await client.POST("/api/v1/admin/bundles/{id}/publish", {
    params: { path: { id } },
    body: { expectedRevision },
  });
  if (error || !data) {
    throw toBundleApiError(response.status, error);
  }
  return data;
}

export async function discontinueBundle(
  id: string,
  expectedRevision: number,
): Promise<BundleDetailResponse> {
  const client = createBrowserApiClient();
  const { data, error, response } = await client.POST("/api/v1/admin/bundles/{id}/discontinue", {
    params: { path: { id } },
    body: { expectedRevision },
  });
  if (error || !data) {
    throw toBundleApiError(response.status, error);
  }
  return data;
}

export async function fetchExamKindOptions(signal?: AbortSignal): Promise<ExamKindOption[]> {
  const client = createBrowserApiClient();
  const { data, error, response } = await client.GET("/api/v1/catalog/options", {
    params: { query: { group: "EXAM" } },
    signal,
  });
  if (error || !data) {
    throw toBundleApiError(response.status, error);
  }
  return data.map((item) => ({
    id: item.id,
    code: item.code,
    nameVi: item.nameVi,
    ...(item.nameEn ? { nameEn: item.nameEn } : {}),
  }));
}
