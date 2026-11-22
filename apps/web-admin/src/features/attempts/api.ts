import type { paths } from "@thi360/api-client";
import { createBrowserApiClient } from "@/lib/api/client";
import { toAttemptApiError } from "./errors";
import type {
  AdminAttemptActionResponse,
  AdminAttemptDetailResponse,
  CompensateAttemptRequest,
  CompensateAttemptResponse,
  ExamKindCatalogOption,
  FaultAttemptRequest,
  ListAdminAttemptsQuery,
  PageResponseAdminAttemptSummary,
} from "./types";

export { ATTEMPTS_QUERY_KEYS } from "./keys";
export {
  ATTEMPT_ERROR_CODES,
  AttemptApiError,
  attemptErrorCode,
  resolveAttemptErrorMessage,
  toAttemptApiError,
  type AttemptErrorCode,
  type AttemptErrorTranslator,
} from "./errors";

export async function listAdminAttempts(
  params: ListAdminAttemptsQuery,
  signal?: AbortSignal,
): Promise<PageResponseAdminAttemptSummary> {
  const client = createBrowserApiClient();
  const query: NonNullable<paths["/api/v1/admin/attempts"]["get"]["parameters"]["query"]> = {
    page: params.page ?? 0,
    size: params.size ?? 20,
  };
  const user = params.user?.trim();
  if (user) query.user = user;
  const exam = params.exam?.trim();
  if (exam) query.exam = exam;
  if (params.state) query.state = params.state;
  if (params.from) query.from = params.from;
  if (params.to) query.to = params.to;

  const { data, error, response } = await client.GET("/api/v1/admin/attempts", {
    params: { query },
    signal,
  });
  if (error || !data) {
    throw toAttemptApiError(response.status, error);
  }
  return data;
}

export async function getAdminAttemptDetail(
  id: string,
  signal?: AbortSignal,
): Promise<AdminAttemptDetailResponse> {
  const client = createBrowserApiClient();
  const { data, error, response } = await client.GET("/api/v1/admin/attempts/{id}", {
    params: { path: { id } },
    signal,
  });
  if (error || !data) {
    throw toAttemptApiError(response.status, error);
  }
  return data;
}

export async function regradeAdminAttempt(id: string): Promise<AdminAttemptActionResponse> {
  const client = createBrowserApiClient();
  const { data, error, response } = await client.POST("/api/v1/admin/attempts/{id}/regrade", {
    params: { path: { id } },
  });
  if (error || !data) {
    throw toAttemptApiError(response.status, error);
  }
  return data;
}

export async function faultAdminAttempt(
  id: string,
  body: FaultAttemptRequest,
): Promise<AdminAttemptActionResponse> {
  const client = createBrowserApiClient();
  const { data, error, response } = await client.POST("/api/v1/admin/attempts/{id}/fault", {
    params: { path: { id } },
    body,
  });
  if (error || !data) {
    throw toAttemptApiError(response.status, error);
  }
  return data;
}

export async function compensateAdminAttempt(
  id: string,
  body: CompensateAttemptRequest,
  idempotencyKey: string,
): Promise<CompensateAttemptResponse> {
  const client = createBrowserApiClient();
  const { data, error, response } = await client.POST("/api/v1/admin/attempts/{id}/compensate", {
    params: {
      path: { id },
      header: { "Idempotency-Key": idempotencyKey },
    },
    body,
  });
  if (error || !data) {
    throw toAttemptApiError(response.status, error);
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
    throw toAttemptApiError(response.status, error);
  }
  return data.map((item) => ({
    id: item.id,
    code: item.code,
    nameVi: item.nameVi,
    ...(item.nameEn ? { nameEn: item.nameEn } : {}),
  }));
}

export interface AttemptApiDeps {
  listAdminAttempts: typeof listAdminAttempts;
  getAdminAttemptDetail: typeof getAdminAttemptDetail;
  regradeAdminAttempt: typeof regradeAdminAttempt;
  faultAdminAttempt: typeof faultAdminAttempt;
  compensateAdminAttempt: typeof compensateAdminAttempt;
  fetchExamKindOptions: typeof fetchExamKindOptions;
}

export const defaultAttemptApiDeps: AttemptApiDeps = {
  listAdminAttempts,
  getAdminAttemptDetail,
  regradeAdminAttempt,
  faultAdminAttempt,
  compensateAdminAttempt,
  fetchExamKindOptions,
};
