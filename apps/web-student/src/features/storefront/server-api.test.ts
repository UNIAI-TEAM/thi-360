// @vitest-environment node
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { EMPTY_CATALOG_QUERY } from "./catalog-query";
import { loadFiltersOnServer, loadProductOnServer, loadProductsOnServer } from "./server-api";

const fetchMock = vi.fn<typeof fetch>();

function json(status: number, body: unknown) {
  return new Response(JSON.stringify(body), { status, headers: { "Content-Type": "application/json" } });
}

function requestUrl(call: number): URL {
  const [input] = fetchMock.mock.calls[call]!;
  return new URL((input as Request).url);
}

beforeEach(() => {
  vi.stubEnv("THI360_API_URL", "http://api.test");
  vi.stubGlobal("fetch", fetchMock);
});

afterEach(() => {
  fetchMock.mockReset();
  vi.unstubAllGlobals();
});

describe("đọc danh mục công khai ở máy chủ web", () => {
  it("danh sách: gọi API công khai với trang tính từ 0, cỡ trang 12, cache 60 giây", async () => {
    fetchMock.mockImplementation(async () =>
      json(200, { items: [], page: 1, size: 12, totalItems: 0, totalPages: 0 }),
    );

    const result = await loadProductsOnServer({ ...EMPTY_CATALOG_QUERY, kind: "HSA", q: "full", page: 2 });

    expect(result.status).toBe("ok");
    const url = requestUrl(0);
    expect(url.origin + url.pathname).toBe("http://api.test/api/v1/public/storefront/products");
    expect(Object.fromEntries(url.searchParams)).toEqual({ kind: "HSA", q: "full", sort: "NEWEST", page: "1", size: "12" });
    expect(fetchMock.mock.calls[0]![1]).toMatchObject({ next: { revalidate: 60 } });
  });

  it("lỗi API hoặc lỗi mạng của danh sách và bộ lọc thành trạng thái lỗi, không ném ra", async () => {
    fetchMock.mockImplementationOnce(async () => json(500, { code: "INTERNAL_ERROR" }));
    await expect(loadProductsOnServer(EMPTY_CATALOG_QUERY)).resolves.toEqual({ status: "error" });

    fetchMock.mockRejectedValueOnce(new Error("connect ECONNREFUSED"));
    await expect(loadFiltersOnServer()).resolves.toEqual({ status: "error" });
  });

  it("chưa cấu hình địa chỉ API (môi trường thật): trạng thái lỗi", async () => {
    vi.stubEnv("THI360_API_URL", "");
    vi.stubEnv("NODE_ENV", "production");

    await expect(loadProductsOnServer(EMPTY_CATALOG_QUERY)).resolves.toEqual({ status: "error" });
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it("chi tiết: 200 trả sản phẩm, 404 là không tìm thấy", async () => {
    fetchMock.mockImplementationOnce(async () => json(200, { code: "HSA-FULL-01", name: "A" }));
    await expect(loadProductOnServer("HSA-FULL-01")).resolves.toMatchObject({ status: "ok", product: { name: "A" } });
    expect(requestUrl(0).pathname).toBe("/api/v1/public/storefront/products/HSA-FULL-01");

    fetchMock.mockImplementationOnce(async () => json(404, { code: "PRODUCT_NOT_FOUND" }));
    await expect(loadProductOnServer("NOPE")).resolves.toEqual({ status: "notFound" });
  });

  it("chi tiết: 410 PRODUCT_DISCONTINUED mang mã và tên bài đã ngừng bán", async () => {
    fetchMock.mockImplementationOnce(async () =>
      json(410, { code: "PRODUCT_DISCONTINUED", product: { code: "QDA-FULL-01", name: "QDA FULL 01" } }),
    );

    await expect(loadProductOnServer("QDA-FULL-01")).resolves.toEqual({
      status: "gone",
      product: { code: "QDA-FULL-01", name: "QDA FULL 01" },
    });
  });

  it("chi tiết: 409 PRODUCT_UNAVAILABLE và 200 saleState UNAVAILABLE trả về status unavailable", async () => {
    fetchMock.mockImplementationOnce(async () =>
      json(409, { code: "PRODUCT_UNAVAILABLE", product: { code: "BO-1", name: "Bo 1" } }),
    );
    await expect(loadProductOnServer("BO-1")).resolves.toMatchObject({
      status: "unavailable",
      product: { code: "BO-1", name: "Bo 1" },
    });

    fetchMock.mockImplementationOnce(async () =>
      json(200, { code: "BO-2", name: "Bo 2", saleState: "UNAVAILABLE" }),
    );
    await expect(loadProductOnServer("BO-2")).resolves.toMatchObject({
      status: "unavailable",
      product: { code: "BO-2", name: "Bo 2" },
    });
  });

  it("chi tiết: lỗi máy chủ ném ra để trang lỗi chung xử lý, không giả làm 404", async () => {
    fetchMock.mockImplementationOnce(async () => json(500, { code: "INTERNAL_ERROR" }));

    await expect(loadProductOnServer("HSA-FULL-01")).rejects.toThrow(/500/);
  });

  it("mã có ký tự đặc biệt được mã hóa trong đường dẫn", async () => {
    fetchMock.mockImplementationOnce(async () => json(404, {}));

    await loadProductOnServer("A/B C");

    expect(requestUrl(0).pathname).toBe("/api/v1/public/storefront/products/A%2FB%20C");
  });
});
