import type { paths } from "@thi360/api-client";

import { createBrowserApiClient } from "@/lib/api/client";

import { toOrderApiError } from "./errors";
import { vnDateEndToIso, vnDateStartToIso } from "./format";
import type {
  AdminOrderDetailResponse,
  AdminOrderListResponse,
  CreateRefundRequest,
  ExamBuyersQueryParams,
  ExamBuyersResponse,
  ExamSelectOption,
  LateSuccessReviewRequest,
  LatestReconciliationReportResponse,
  MarkRefundFailedRequest,
  OrderAttentionSummary,
  OrdersListQueryParams,
  RecoverOrderRequest,
  RefundResponse,
} from "./types";

export async function listAdminOrders(
  params: OrdersListQueryParams,
  signal?: AbortSignal,
): Promise<AdminOrderListResponse> {
  const client = createBrowserApiClient();
  const query: NonNullable<paths["/api/v1/admin/orders"]["get"]["parameters"]["query"]> = {
    page: params.page,
    size: params.size,
    sort: params.sort,
  };

  const trimmedQ = params.q.trim();
  if (trimmedQ) query.q = trimmedQ;
  if (params.examId) query.examId = params.examId;
  const fromIso = params.fromDate ? vnDateStartToIso(params.fromDate) : undefined;
  const toIso = params.toDate ? vnDateEndToIso(params.toDate) : undefined;
  if (fromIso) query.from = fromIso;
  if (toIso) query.to = toIso;
  if (params.paymentStatus !== "ALL") query.paymentStatus = params.paymentStatus;
  if (params.refundStatus !== "ALL") query.refundStatus = params.refundStatus;
  if (params.paymentMethod !== "ALL") query.paymentMethod = params.paymentMethod;
  if (params.attention) query.attention = params.attention;

  const { data, error, response } = await client.GET("/api/v1/admin/orders", {
    params: { query },
    signal,
  });
  if (error || !data) {
    throw toOrderApiError(response.status, error);
  }
  return data;
}

export async function getAdminOrderAttentionSummary(
  signal?: AbortSignal,
): Promise<OrderAttentionSummary> {
  const client = createBrowserApiClient();
  const { data, error, response } = await client.GET("/api/v1/admin/orders/attention", {
    signal,
  });
  if (error || !data) {
    throw toOrderApiError(response.status, error);
  }
  return data;
}

export async function getAdminOrderDetail(
  code: string,
  signal?: AbortSignal,
): Promise<AdminOrderDetailResponse> {
  const client = createBrowserApiClient();
  const { data, error, response } = await client.GET("/api/v1/admin/orders/{code}", {
    params: { path: { code } },
    signal,
  });
  if (error || !data) {
    throw toOrderApiError(response.status, error);
  }
  return data;
}

export async function recoverOrderFulfillment(
  code: string,
  body: RecoverOrderRequest,
  idempotencyKey: string,
): Promise<AdminOrderDetailResponse> {
  const client = createBrowserApiClient();
  const { data, error, response } = await client.POST("/api/v1/admin/orders/{code}/fulfill", {
    params: {
      path: { code },
      header: { "Idempotency-Key": idempotencyKey },
    },
    body,
  });
  if (error || !data) {
    throw toOrderApiError(response.status, error);
  }
  return data;
}

export async function reviewLateSuccessPayment(
  code: string,
  paymentId: string,
  body: LateSuccessReviewRequest,
  idempotencyKey: string,
): Promise<AdminOrderDetailResponse> {
  const client = createBrowserApiClient();
  const { data, error, response } = await client.POST(
    "/api/v1/admin/orders/{code}/payments/{paymentId}/late-success",
    {
      params: {
        path: { code, paymentId },
        header: { "Idempotency-Key": idempotencyKey },
      },
      body,
    },
  );
  if (error || !data) {
    throw toOrderApiError(response.status, error);
  }
  return data;
}

export async function createOrderPaymentRefund(
  code: string,
  paymentId: string,
  body: CreateRefundRequest,
  idempotencyKey: string,
): Promise<RefundResponse> {
  const client = createBrowserApiClient();
  const { data, error, response } = await client.POST(
    "/api/v1/admin/orders/{code}/payments/{paymentId}/refunds",
    {
      params: {
        path: { code, paymentId },
        header: { "Idempotency-Key": idempotencyKey },
      },
      body,
    },
  );
  if (error || !data) {
    throw toOrderApiError(response.status, error);
  }
  return data;
}

export async function getRefund(id: string, signal?: AbortSignal): Promise<RefundResponse> {
  const client = createBrowserApiClient();
  const { data, error, response } = await client.GET("/api/v1/admin/refunds/{id}", {
    params: { path: { id } },
    signal,
  });
  if (error || !data) {
    throw toOrderApiError(response.status, error);
  }
  return data;
}

export async function queryRefund(id: string): Promise<RefundResponse> {
  const client = createBrowserApiClient();
  const { data, error, response } = await client.POST("/api/v1/admin/refunds/{id}/query", {
    params: { path: { id } },
  });
  if (error || !data) {
    throw toOrderApiError(response.status, error);
  }
  return data;
}

export async function markRefundFailed(
  id: string,
  body: MarkRefundFailedRequest,
): Promise<RefundResponse> {
  const client = createBrowserApiClient();
  const { data, error, response } = await client.POST("/api/v1/admin/refunds/{id}/mark-failed", {
    params: { path: { id } },
    body,
  });
  if (error || !data) {
    throw toOrderApiError(response.status, error);
  }
  return data;
}

export async function getLatestReconciliationReport(
  signal?: AbortSignal,
): Promise<LatestReconciliationReportResponse> {
  const client = createBrowserApiClient();
  const { data, error, response } = await client.GET(
    "/api/v1/admin/reconciliation-reports/latest",
    { signal },
  );
  if (error || !data) {
    throw toOrderApiError(response.status, error);
  }
  return data;
}

export async function listExamBuyers(
  examId: string,
  params: ExamBuyersQueryParams,
  signal?: AbortSignal,
): Promise<ExamBuyersResponse> {
  const client = createBrowserApiClient();
  const { data, error, response } = await client.GET("/api/v1/admin/exams/{id}/buyers", {
    params: {
      path: { id: examId },
      query: {
        page: params.page,
        size: params.size,
        sort: params.sort,
      },
    },
    signal,
  });
  if (error || !data) {
    throw toOrderApiError(response.status, error);
  }
  return data;
}

/**
 * Only call `/api/v1/admin/exams` when caller holds `EXAM.VIEW` to avoid triggering global 403 redirect.
 */
export async function listAdminExamsForFilter(
  signal?: AbortSignal,
): Promise<ExamSelectOption[]> {
  const client = createBrowserApiClient();
  const { data, error, response } = await client.GET("/api/v1/admin/exams", {
    params: {
      query: {
        page: 0,
        size: 100,
      },
    },
    signal,
  });
  if (error || !data) {
    throw toOrderApiError(response.status, error);
  }
  return data.items.map((item) => ({
    id: item.id,
    code: item.code,
    name: item.name,
  }));
}

/**
 * Fallback exam options derived from `GET /api/v1/admin/orders` for roles lacking `EXAM.VIEW`
 * (e.g. `operations` or custom `ORDER.VIEW`-only roles). Never calls `/api/v1/admin/exams`.
 */
export async function listOrderDerivedExamsForFilter(
  signal?: AbortSignal,
): Promise<ExamSelectOption[]> {
  const client = createBrowserApiClient();
  const { data, error, response } = await client.GET("/api/v1/admin/orders", {
    params: {
      query: {
        page: 0,
        size: 100,
        sort: "createdAt,desc",
      },
    },
    signal,
  });
  if (error || !data) {
    throw toOrderApiError(response.status, error);
  }
  const byId = new Map<string, ExamSelectOption>();
  for (const item of data.items) {
    if (item.examId && !byId.has(item.examId)) {
      byId.set(item.examId, {
        id: item.examId,
        code: item.productCode,
        name: item.productName,
      });
    }
  }
  return Array.from(byId.values());
}
