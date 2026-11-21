import type { components } from "@thi360/api-client";
import type { ProductDetail } from "@/features/storefront/types";
import { createBrowserApiClient, toApiStatusError } from "@/lib/api/client";

export type PriceQuote = components["schemas"]["PriceQuote"];
export type PriceQuoteRequest = components["schemas"]["PriceQuoteRequest"];
export type PriceQuotePromotion = components["schemas"]["PriceQuotePromotion"];
export type PriceQuoteVoucher = components["schemas"]["PriceQuoteVoucher"];
export type CreateOrderRequest = components["schemas"]["CreateOrderRequest"];
export type CreateOrderResponse = components["schemas"]["CreateOrderResponse"];
export type OrderPriceChangedResponse = components["schemas"]["OrderPriceChangedResponse"];
export type ProductGoneResponse = components["schemas"]["ProductGoneResponse"];
export type VnpayReturnResponse = components["schemas"]["VnpayReturnResponse"];

export type ProductCheckoutLookup =
  | { status: "ok"; product: ProductDetail }
  | { status: "gone"; product: { code: string; name: string } }
  | { status: "notFound" };

export type CheckoutProductLoader = (code: string) => Promise<ProductCheckoutLookup>;
export type PriceQuoteAction = (request: PriceQuoteRequest) => Promise<PriceQuote>;
export type OrderCreateAction = (request: CreateOrderRequest, idempotencyKey: string) => Promise<CreateOrderResponse>;
export type VnpayReturnVerifier = (params: Record<string, string>) => Promise<VnpayReturnResponse>;

export class ProductGoneError extends Error {
  readonly status = 410;
  readonly product: { code: string; name: string };

  constructor(product: { code: string; name: string }, message?: string) {
    super(message ?? "Product is discontinued");
    this.name = "ProductGoneError";
    this.product = product;
  }
}

export class OrderConflictError extends Error {
  readonly status = 409;
  readonly code: string;
  readonly quote?: PriceQuote;

  constructor(code: string, message: string, quote?: PriceQuote) {
    super(message);
    this.name = "OrderConflictError";
    this.code = code;
    this.quote = quote;
  }
}

export const CHECKOUT_QUOTE_QUERY_KEY = (code: string, seenVersionNo?: number, seenPrice?: number) =>
  ["checkout", "quote", code, seenVersionNo ?? null, seenPrice ?? null] as const;

export const CHECKOUT_PRODUCT_QUERY_KEY = (code: string) => ["checkout", "product", code] as const;

export const VNPAY_RETURN_QUERY_KEY = (serialized: string) => ["checkout", "vnpay-return", serialized] as const;

export async function loadProductForCheckout(code: string): Promise<ProductCheckoutLookup> {
  const client = createBrowserApiClient();
  const { data, error, response } = await client.GET("/api/v1/public/storefront/products/{code}", {
    params: { path: { code } },
  });
  if (response.status === 410) {
    const gone = error as ProductGoneResponse | undefined;
    return {
      status: "gone",
      product: gone?.product ?? { code, name: code },
    };
  }
  if (response.status === 404) {
    return { status: "notFound" };
  }
  if (!response.ok || !data) {
    throw toApiStatusError(response.status, error as never);
  }
  return { status: "ok", product: data };
}

export async function fetchPriceQuote(request: PriceQuoteRequest): Promise<PriceQuote> {
  const client = createBrowserApiClient();
  const { data, error, response } = await client.POST("/api/v1/me/price-quote", {
    body: request,
  });
  if (response.status === 410) {
    const gone = error as ProductGoneResponse | undefined;
    throw new ProductGoneError(gone?.product ?? { code: request.code, name: request.code }, gone?.message);
  }
  if (!response.ok || !data) {
    throw toApiStatusError(response.status, error as never);
  }
  return data;
}

export async function createOrder(request: CreateOrderRequest, idempotencyKey: string): Promise<CreateOrderResponse> {
  const client = createBrowserApiClient();
  const { data, error, response } = await client.POST("/api/v1/me/orders", {
    body: request,
    params: {
      header: { "Idempotency-Key": idempotencyKey },
    },
  });
  if (response.status === 410) {
    const gone = error as ProductGoneResponse | undefined;
    throw new ProductGoneError(gone?.product ?? { code: request.code, name: request.code }, gone?.message);
  }
  if (response.status === 409) {
    const conflict = error as OrderPriceChangedResponse | undefined;
    throw new OrderConflictError(
      conflict?.code ?? "CONFLICT",
      conflict?.message ?? "Order conflict",
      conflict?.quote,
    );
  }
  if (!response.ok || !data) {
    throw toApiStatusError(response.status, error as never);
  }
  return data;
}

export async function verifyVnpayReturn(params: Record<string, string>): Promise<VnpayReturnResponse> {
  const client = createBrowserApiClient();
  const { data, error, response } = await client.GET("/api/v1/public/payments/vnpay/return", {
    params: { query: { params } },
    querySerializer: () => {
      const search = new URLSearchParams();
      for (const [key, value] of Object.entries(params)) {
        if (value !== undefined) {
          search.append(key, value);
        }
      }
      return search.toString();
    },
  });
  if (!response.ok || !data) {
    throw toApiStatusError(response.status, error as never);
  }
  return data;
}
