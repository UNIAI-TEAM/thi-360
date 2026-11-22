import { afterEach, describe, expect, it, vi } from "vitest";
import { ApiStatusError } from "@/lib/api/client";
import {
  createOrder,
  fetchPriceQuote,
  loadProductForCheckout,
  OrderConflictError,
  ProductGoneError,
  ProductUnavailableError,
  verifyVnpayReturn,
} from "./api";

function jsonResponse(status: number, body: unknown): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { "content-type": "application/json" },
  });
}

describe("checkout/api", () => {
  afterEach(() => {
    vi.unstubAllGlobals();
    vi.restoreAllMocks();
  });

  it("loadProductForCheckout xử lý 200 ok, 404 notFound, 410 gone và lỗi 5xx", async () => {
    const fetchMock = vi
      .fn()
      .mockResolvedValueOnce(jsonResponse(200, { code: "HSA-FULL-01", name: "Đề HSA" }))
      .mockResolvedValueOnce(jsonResponse(404, { code: "PRODUCT_NOT_FOUND", message: "Missing", fieldErrors: [] }))
      .mockResolvedValueOnce(
        jsonResponse(410, {
          code: "PRODUCT_DISCONTINUED",
          message: "Gone",
          fieldErrors: [],
          product: { code: "HSA-OLD", name: "Đề cũ" },
        }),
      )
      .mockResolvedValueOnce(jsonResponse(500, { code: "INTERNAL_ERROR", message: "Boom", fieldErrors: [] }));
    vi.stubGlobal("fetch", fetchMock);

    await expect(loadProductForCheckout("HSA-FULL-01")).resolves.toEqual({
      status: "ok",
      product: { code: "HSA-FULL-01", name: "Đề HSA" },
    });
    await expect(loadProductForCheckout("MISSING")).resolves.toEqual({ status: "notFound" });
    await expect(loadProductForCheckout("HSA-OLD")).resolves.toEqual({
      status: "gone",
      product: { code: "HSA-OLD", name: "Đề cũ" },
    });
    await expect(loadProductForCheckout("ERR")).rejects.toMatchObject({
      status: 500,
      code: "INTERNAL_ERROR",
    });
  });

  it("fetchPriceQuote trả quote hợp lệ, ném ProductGoneError khi 410 và ApiStatusError khi 422", async () => {
    const fetchMock = vi
      .fn()
      .mockResolvedValueOnce(jsonResponse(200, { productCode: "HSA-FULL-01", finalPrice: 200000, versionNo: 2 }))
      .mockResolvedValueOnce(
        jsonResponse(410, {
          code: "PRODUCT_DISCONTINUED",
          message: "Discontinued",
          fieldErrors: [],
          product: { code: "HSA-FULL-01", name: "Đề HSA" },
        }),
      )
      .mockResolvedValueOnce(
        jsonResponse(422, { code: "VOUCHER_EXPIRED", message: "Expired voucher", fieldErrors: [] }),
      );
    vi.stubGlobal("fetch", fetchMock);

    await expect(fetchPriceQuote({ code: "HSA-FULL-01" })).resolves.toMatchObject({
      finalPrice: 200000,
      versionNo: 2,
    });
    await expect(fetchPriceQuote({ code: "HSA-FULL-01" })).rejects.toBeInstanceOf(ProductGoneError);
    await expect(fetchPriceQuote({ code: "HSA-FULL-01", voucherCode: "OLD" })).rejects.toMatchObject({
      status: 422,
      code: "VOUCHER_EXPIRED",
    });
  });

  it("createOrder gửi Idempotency-Key và dịch 409 OrderConflictError, 410 ProductGoneError, 422 ApiStatusError", async () => {
    const fetchMock = vi
      .fn()
      .mockResolvedValueOnce(jsonResponse(201, { next: "REDIRECT", order: { code: "DH01" }, paymentUrl: "https://pay" }))
      .mockResolvedValueOnce(
        jsonResponse(409, {
          code: "PRICE_CHANGED",
          message: "Price changed",
          fieldErrors: [],
          quote: { productCode: "HSA-FULL-01", finalPrice: 280000, versionNo: 3, changed: true },
        }),
      )
      .mockResolvedValueOnce(
        jsonResponse(410, {
          code: "PRODUCT_DISCONTINUED",
          message: "Gone",
          fieldErrors: [],
          product: { code: "HSA-FULL-01", name: "Đề HSA" },
        }),
      )
      .mockResolvedValueOnce(
        jsonResponse(422, { code: "VOUCHER_EXHAUSTED", message: "Exhausted", fieldErrors: [] }),
      );
    vi.stubGlobal("fetch", fetchMock);

    const payload = {
      code: "HSA-FULL-01",
      expectedAccountId: "u-1",
      expectedAmountDue: 250000,
      expectedVersionNo: 2,
    };
    const res = await createOrder(payload, "idem-key-123");
    expect(res.next).toBe("REDIRECT");
    const firstReq = fetchMock.mock.calls[0]![0] as Request;
    expect(firstReq.headers.get("Idempotency-Key")).toBe("idem-key-123");

    const conflict = await createOrder(payload, "idem-key-2").catch((err: unknown) => err);
    expect(conflict).toBeInstanceOf(OrderConflictError);
    expect((conflict as OrderConflictError).code).toBe("PRICE_CHANGED");
    expect((conflict as OrderConflictError).quote?.finalPrice).toBe(280000);

    await expect(createOrder(payload, "idem-key-3")).rejects.toBeInstanceOf(ProductGoneError);

    const voucherErr = await createOrder(payload, "idem-key-4").catch((err: unknown) => err);
    expect(voucherErr).toBeInstanceOf(ApiStatusError);
    expect((voucherErr as ApiStatusError).status).toBe(422);
    expect((voucherErr as ApiStatusError).code).toBe("VOUCHER_EXHAUSTED");
  });

  it("verifyVnpayReturn tuần tự hóa phẳng các tham số vnp_* và dịch lỗi 422 / 404", async () => {
    const fetchMock = vi
      .fn()
      .mockResolvedValueOnce(jsonResponse(200, { orderCode: "DH01", paymentState: "SUCCEEDED" }))
      .mockResolvedValueOnce(
        jsonResponse(422, { code: "VNPAY_SIGNATURE_INVALID", message: "Invalid signature", fieldErrors: [] }),
      );
    vi.stubGlobal("fetch", fetchMock);

    const verified = await verifyVnpayReturn({
      vnp_TxnRef: "DH0101",
      vnp_ResponseCode: "00",
      vnp_SecureHash: "abc123",
    });
    expect(verified).toEqual({ orderCode: "DH01", paymentState: "SUCCEEDED" });

    const req = fetchMock.mock.calls[0]![0] as Request;
    const url = new URL(req.url);
    expect(url.pathname).toBe("/api/v1/public/payments/vnpay/return");
    expect(url.searchParams.get("vnp_TxnRef")).toBe("DH0101");
    expect(url.searchParams.get("vnp_ResponseCode")).toBe("00");
    expect(url.searchParams.get("vnp_SecureHash")).toBe("abc123");
    expect(url.searchParams.has("params")).toBe(false);

    await expect(
      verifyVnpayReturn({ vnp_TxnRef: "DH0101", vnp_SecureHash: "bad" }),
    ).rejects.toMatchObject({
      status: 422,
      code: "VNPAY_SIGNATURE_INVALID",
    });
  });

  it("R1-4 / R1-6 / R1-37: loadProductForCheckout và fetchPriceQuote xử lý 409 PRODUCT_UNAVAILABLE (stub vs full), 409 khác/thiếu code và 200 saleState UNAVAILABLE", async () => {
    const fullBundle = {
      code: "BO-FULL",
      name: "Bộ đề SP2E",
      productType: "BUNDLE",
      format: "BUNDLE",
      examKind: { code: "SP2E", nameVi: "SP2E", nameEn: "SP2E" },
      benefits: { aiExplanation: true, strengthWeakness: false, practice: true },
      listPrice: 300000,
      price: 250000,
    };

    const fetchMock = vi
      .fn()
      .mockResolvedValueOnce(
        jsonResponse(409, {
          code: "PRODUCT_UNAVAILABLE",
          message: "Unavailable",
          product: { code: "BO-STUB", name: "Stub only" },
        }),
      )
      .mockResolvedValueOnce(
        jsonResponse(409, {
          code: "PRODUCT_UNAVAILABLE",
          message: "Unavailable",
          product: fullBundle,
        }),
      )
      .mockResolvedValueOnce(jsonResponse(409, { message: "Code-less 409", fieldErrors: [] }))
      .mockResolvedValueOnce(jsonResponse(409, { code: "OTHER_CONFLICT", message: "Other", fieldErrors: [] }))
      .mockResolvedValueOnce(jsonResponse(200, { ...fullBundle, code: "BO-200", saleState: "UNAVAILABLE" }))
      .mockResolvedValueOnce(jsonResponse(409, { code: "PRODUCT_UNAVAILABLE", message: "Bundle unavailable" }))
      .mockResolvedValueOnce(jsonResponse(409, { message: "No code conflict", fieldErrors: [] }))
      .mockResolvedValueOnce(jsonResponse(409, { code: "OTHER_CONFLICT", message: "Other 409", fieldErrors: [] }));
    vi.stubGlobal("fetch", fetchMock);

    await expect(loadProductForCheckout("BO-STUB")).resolves.toEqual({
      status: "unavailable",
      product: undefined,
    });
    await expect(loadProductForCheckout("BO-FULL")).resolves.toEqual({
      status: "unavailable",
      product: fullBundle,
    });
    await expect(loadProductForCheckout("BO-NOCODE")).rejects.toMatchObject({
      status: 409,
    });
    await expect(loadProductForCheckout("BO-OTHER")).rejects.toMatchObject({
      status: 409,
      code: "OTHER_CONFLICT",
    });
    await expect(loadProductForCheckout("BO-200")).resolves.toMatchObject({
      status: "unavailable",
      product: { code: "BO-200", saleState: "UNAVAILABLE" },
    });

    await expect(fetchPriceQuote({ code: "BO-FULL" })).rejects.toBeInstanceOf(ProductUnavailableError);
    const noCodeQuoteErr = await fetchPriceQuote({ code: "BO-FULL" }).catch((err: unknown) => err);
    expect(noCodeQuoteErr).toBeInstanceOf(ApiStatusError);
    expect(noCodeQuoteErr).not.toBeInstanceOf(ProductUnavailableError);
    expect((noCodeQuoteErr as ApiStatusError).status).toBe(409);

    const otherQuoteErr = await fetchPriceQuote({ code: "BO-FULL" }).catch((err: unknown) => err);
    expect(otherQuoteErr).toBeInstanceOf(ApiStatusError);
    expect(otherQuoteErr).not.toBeInstanceOf(ProductUnavailableError);
    expect((otherQuoteErr as ApiStatusError).code).toBe("OTHER_CONFLICT");
  });
});
