import { createApiClient } from "@thi360/api-client";
import { resolveApiBase } from "@thi360/web-kit/proxy";
import { type CatalogQuery, toApiQuery } from "./catalog-query";
import { isFullProductDetail } from "./format";
import type { Loaded, ProductDetail, ProductLookup, ProductPage, StorefrontFilters } from "./types";

/** Cache dữ liệu danh mục ở máy chủ web 60 giây (TM 11.1); API công khai cũng trả `Cache-Control: max-age=60`. */
export const STOREFRONT_REVALIDATE_SECONDS = 60;
const REQUEST_TIMEOUT_MS = 5000;

function storefrontClient() {
  const base = resolveApiBase();
  if (!base) return null;
  return createApiClient(base, {
    fetch: (request) =>
      fetch(request, {
        next: { revalidate: STOREFRONT_REVALIDATE_SECONDS },
        signal: AbortSignal.timeout(REQUEST_TIMEOUT_MS),
      }),
  });
}

export async function loadFiltersOnServer(): Promise<Loaded<StorefrontFilters>> {
  const client = storefrontClient();
  if (!client) return { status: "error" };
  try {
    const { data, response } = await client.GET("/api/v1/public/storefront/filters");
    return response.ok && data ? { status: "ok", data } : { status: "error" };
  } catch {
    return { status: "error" };
  }
}

export async function loadProductsOnServer(query: CatalogQuery): Promise<Loaded<ProductPage>> {
  const client = storefrontClient();
  if (!client) return { status: "error" };
  try {
    const { data, response } = await client.GET("/api/v1/public/storefront/products", {
      params: { query: toApiQuery(query) },
    });
    return response.ok && data ? { status: "ok", data } : { status: "error" };
  } catch {
    return { status: "error" };
  }
}

/** 404 `PRODUCT_NOT_FOUND`, 409 `PRODUCT_UNAVAILABLE`, 410 `PRODUCT_DISCONTINUED` kèm `{code, name}`; lỗi khác ném ra để trang lỗi chung xử lý. */
export async function loadProductOnServer(code: string): Promise<ProductLookup> {
  const client = storefrontClient();
  if (!client) throw new Error("storefront: API address is not configured");
  const { data, error, response } = await client.GET("/api/v1/public/storefront/products/{code}", {
    params: { path: { code } },
  });
  if (response.ok && data) {
    const saleState = (data as ProductDetail & { saleState?: string }).saleState;
    if (saleState === "UNAVAILABLE") {
      return { status: "unavailable", product: data };
    }
    return { status: "ok", product: data };
  }
  if (response.status === 404) return { status: "notFound" };
  if (response.status === 409) {
    const unavailable = error as { code?: string; product?: unknown } | undefined;
    if (unavailable?.code === "PRODUCT_UNAVAILABLE") {
      return {
        status: "unavailable",
        product: isFullProductDetail(unavailable.product) ? unavailable.product : undefined,
      };
    }
  }
  if (response.status === 410) {
    const gone = (error as { product?: { code?: string; name?: string } } | undefined)?.product;
    return { status: "gone", product: { code: gone?.code ?? code, name: gone?.name ?? code } };
  }
  throw new Error(`storefront: product lookup failed with ${response.status}`);
}
