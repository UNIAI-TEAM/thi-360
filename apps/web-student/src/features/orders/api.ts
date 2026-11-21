import type { components } from "@thi360/api-client";
import { createBrowserApiClient, toApiStatusError } from "@/lib/api/client";

export type OrderSummary = components["schemas"]["OrderSummary"];
export type OrderDetail = components["schemas"]["OrderDetail"];
export type OrderItemView = components["schemas"]["OrderItemView"];
export type OrderPaymentView = components["schemas"]["OrderPaymentView"];
export type OrderHistoryView = components["schemas"]["OrderHistoryView"];
export type OrderPage = components["schemas"]["PageResponseOrderSummary"];
export type PayOrderResponse = components["schemas"]["PayOrderResponse"];

export const ORDERS_PAGE_SIZE = 20;

export const MY_ORDERS_QUERY_KEY = (page: number, size: number) => ["me", "orders", page, size] as const;
export const MY_ORDER_DETAIL_QUERY_KEY = (code: string) => ["me", "orders", "detail", code] as const;

export type OrdersLoader = (params: { page: number; size: number }) => Promise<OrderPage>;
export type OrderDetailLoader = (code: string) => Promise<OrderDetail>;
export type OrderPayAction = (code: string, idempotencyKey: string) => Promise<PayOrderResponse>;
export type OrderCancelAction = (code: string, idempotencyKey: string) => Promise<OrderDetail>;

export async function loadMyOrders({
  page,
  size,
}: {
  page: number;
  size: number;
}): Promise<OrderPage> {
  const client = createBrowserApiClient();
  const { data, error, response } = await client.GET("/api/v1/me/orders", {
    params: { query: { page, size } },
  });
  if (!response.ok || !data) {
    throw toApiStatusError(response.status, error as never);
  }
  return data;
}

export async function loadMyOrderDetail(code: string): Promise<OrderDetail> {
  const client = createBrowserApiClient();
  const { data, error, response } = await client.GET("/api/v1/me/orders/{code}", {
    params: { path: { code } },
  });
  if (!response.ok || !data) {
    throw toApiStatusError(response.status, error as never);
  }
  return data;
}

export async function payMyOrder(code: string, idempotencyKey: string): Promise<PayOrderResponse> {
  const client = createBrowserApiClient();
  const { data, error, response } = await client.POST("/api/v1/me/orders/{code}/pay", {
    params: {
      path: { code },
      header: { "Idempotency-Key": idempotencyKey },
    },
  });
  if (!response.ok || !data) {
    throw toApiStatusError(response.status, error as never);
  }
  return data;
}

export async function cancelMyOrder(code: string, idempotencyKey: string): Promise<OrderDetail> {
  const client = createBrowserApiClient();
  const { data, error, response } = await client.POST("/api/v1/me/orders/{code}/cancel", {
    params: {
      path: { code },
      header: { "Idempotency-Key": idempotencyKey },
    },
  });
  if (!response.ok || !data) {
    throw toApiStatusError(response.status, error as never);
  }
  return data;
}
