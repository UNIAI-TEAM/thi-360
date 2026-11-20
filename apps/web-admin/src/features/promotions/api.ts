import { createBrowserApiClient } from "@/lib/api/client";

import { toPromotionApiError } from "./errors";
import type {
  ClassPickerOption,
  CreatePromotionRequest,
  ExamKindOption,
  ExamPickerOption,
  PageResponsePromotionSummary,
  PriceQuote,
  PromotionDetail,
  PromotionDetailQueryParams,
  PromotionsListQueryParams,
  UpdatePromotionRequest,
  UserPickerOption,
} from "./types";

export async function listPromotions(
  params: PromotionsListQueryParams,
  signal?: AbortSignal,
): Promise<PageResponsePromotionSummary> {
  const client = createBrowserApiClient();
  const query: {
    kind?: "AUTO" | "VOUCHER";
    status?: "ACTIVE" | "STOPPED";
    state?: "UPCOMING" | "RUNNING" | "ENDED";
    q?: string;
    page?: number;
    size?: number;
  } = {
    kind: params.kind,
    page: params.page,
    size: params.size,
  };

  if (params.stateFilter === "UPCOMING" || params.stateFilter === "RUNNING") {
    query.state = params.stateFilter;
    query.status = "ACTIVE";
  } else if (params.stateFilter === "ENDED") {
    query.state = "ENDED";
  } else if (params.stateFilter === "STOPPED") {
    query.status = "STOPPED";
  }

  const trimmedQ = params.q.trim();
  if (trimmedQ) query.q = trimmedQ;

  const { data, error, response } = await client.GET("/api/v1/admin/promotions", {
    params: { query },
    signal,
  });
  if (error || !data) {
    throw toPromotionApiError(response.status, error);
  }
  return data;
}

export async function getPromotion(
  id: string,
  params?: Partial<PromotionDetailQueryParams>,
  signal?: AbortSignal,
): Promise<PromotionDetail> {
  const client = createBrowserApiClient();
  const query: {
    page?: number;
    size?: number;
    redemptionState?: "RESERVED" | "CONFIRMED" | "RELEASED";
    redemptionQ?: string;
  } = {};

  if (typeof params?.page === "number") query.page = params.page;
  if (typeof params?.size === "number") query.size = params.size;
  if (params?.redemptionState && params.redemptionState !== "ALL") {
    query.redemptionState = params.redemptionState;
  }
  const trimmedQ = params?.redemptionQ?.trim();
  if (trimmedQ) query.redemptionQ = trimmedQ;

  const { data, error, response } = await client.GET("/api/v1/admin/promotions/{id}", {
    params: {
      path: { id },
      ...(Object.keys(query).length > 0 ? { query } : {}),
    },
    signal,
  });
  if (error || !data) {
    throw toPromotionApiError(response.status, error);
  }
  return data;
}

export async function createPromotion(
  body: CreatePromotionRequest,
  idempotencyKey: string,
): Promise<PromotionDetail> {
  const client = createBrowserApiClient();
  const { data, error, response } = await client.POST("/api/v1/admin/promotions", {
    params: {
      header: { "Idempotency-Key": idempotencyKey },
    },
    body,
  });
  if (error || !data) {
    throw toPromotionApiError(response.status, error);
  }
  return data;
}

export async function updatePromotion(id: string, body: UpdatePromotionRequest): Promise<PromotionDetail> {
  const client = createBrowserApiClient();
  const { data, error, response } = await client.PATCH("/api/v1/admin/promotions/{id}", {
    params: { path: { id } },
    body,
  });
  if (error || !data) {
    throw toPromotionApiError(response.status, error);
  }
  return data;
}

export async function stopPromotion(id: string, expectedRevision: number): Promise<PromotionDetail> {
  const client = createBrowserApiClient();
  const { data, error, response } = await client.POST("/api/v1/admin/promotions/{id}/stop", {
    params: { path: { id } },
    body: { expectedRevision },
  });
  if (error || !data) {
    throw toPromotionApiError(response.status, error);
  }
  return data;
}

export async function resumePromotion(id: string, expectedRevision: number): Promise<PromotionDetail> {
  const client = createBrowserApiClient();
  const { data, error, response } = await client.POST("/api/v1/admin/promotions/{id}/resume", {
    params: { path: { id } },
    body: { expectedRevision },
  });
  if (error || !data) {
    throw toPromotionApiError(response.status, error);
  }
  return data;
}

export async function deletePromotion(id: string): Promise<void> {
  const client = createBrowserApiClient();
  const { error, response } = await client.DELETE("/api/v1/admin/promotions/{id}", {
    params: { path: { id } },
  });
  if (error || !response.ok) {
    throw toPromotionApiError(response.status, error);
  }
}

export async function getSuggestedPromotionCode(signal?: AbortSignal): Promise<string> {
  const client = createBrowserApiClient();
  const { data, error, response } = await client.GET("/api/v1/admin/promotions/suggested-code", {
    signal,
  });
  if (error || !data) {
    throw toPromotionApiError(response.status, error);
  }
  return data.code;
}

export async function previewPromotionPrice(
  promotionId: string,
  examId: string,
  signal?: AbortSignal,
): Promise<PriceQuote> {
  const client = createBrowserApiClient();
  const { data, error, response } = await client.POST("/api/v1/admin/promotions/price-preview", {
    body: { promotionId, examId },
    signal,
  });
  if (error || !data) {
    throw toPromotionApiError(response.status, error);
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
    throw toPromotionApiError(response.status, error);
  }
  return data.map((item) => ({
    id: item.id,
    code: item.code,
    nameVi: item.nameVi,
    ...(item.nameEn ? { nameEn: item.nameEn } : {}),
  }));
}

export async function searchPublishedExams(q: string, signal?: AbortSignal): Promise<ExamPickerOption[]> {
  const client = createBrowserApiClient();
  const trimmed = q.trim();
  const { data, error, response } = await client.GET("/api/v1/admin/exams", {
    params: {
      query: {
        status: "PUBLISHED",
        page: 0,
        size: 20,
        ...(trimmed ? { q: trimmed } : {}),
      },
    },
    signal,
  });
  if (error || !data) {
    throw toPromotionApiError(response.status, error);
  }
  return data.items.map((item) => ({
    id: item.id,
    code: item.code,
    name: item.name,
  }));
}

export async function searchClasses(q: string, signal?: AbortSignal): Promise<ClassPickerOption[]> {
  const client = createBrowserApiClient();
  const trimmed = q.trim();
  const { data, error, response } = await client.GET("/api/v1/admin/classes", {
    params: {
      query: {
        page: 0,
        size: 20,
        ...(trimmed ? { q: trimmed } : {}),
      },
    },
    signal,
  });
  if (error || !data) {
    throw toPromotionApiError(response.status, error);
  }
  return data.items.map((item) => ({
    id: item.id,
    code: item.code,
    name: item.name,
  }));
}

export async function searchUsers(q: string, signal?: AbortSignal): Promise<UserPickerOption[]> {
  const client = createBrowserApiClient();
  const trimmed = q.trim();
  const { data, error, response } = await client.GET("/api/v1/admin/users", {
    params: {
      query: {
        status: "ACTIVE",
        page: 0,
        size: 20,
        ...(trimmed ? { q: trimmed } : {}),
      },
    },
    signal,
  });
  if (error || !data) {
    throw toPromotionApiError(response.status, error);
  }
  return data.items.map((item) => ({
    id: item.id,
    username: item.username,
    fullName: item.fullName,
    ...(item.email ? { email: item.email } : {}),
  }));
}
