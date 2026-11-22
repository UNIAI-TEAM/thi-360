import { screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";
import { type PriceQuote, ProductUnavailableError } from "@/features/checkout/api";
import { ApiStatusError } from "@/lib/api/client";
import enMessages from "@/messages/en.json";
import { renderWithProviders } from "@/test/render";
import { VoucherBox, type VoucherErrorKey } from "./voucher-box";

vi.mock("next/navigation", () => ({
  usePathname: () => "/checkout/HSA-FULL-01",
}));

function makeQuote(overrides: Partial<PriceQuote> = {}): PriceQuote {
  return {
    productCode: "HSA-FULL-01",
    productName: "Đề thi thử ĐGNL HSA số 1",
    listPrice: 300000,
    finalPrice: 300000,
    attemptsGranted: 3,
    discountApplied: "NONE",
    versionNo: 1,
    changed: false,
    voucherNotBetter: false,
    ...overrides,
  };
}

describe("VoucherBox · AC-6", () => {
  it("kiểm tra nhập trống, áp dụng thành công, kết hợp, thay thế KM tự động, không tốt hơn và gỡ mã", async () => {
    const user = userEvent.setup();
    const onQuoteApplied = vi.fn();

    const requestQuote = vi
      .fn()
      .mockResolvedValueOnce(
        makeQuote({
          finalPrice: 240000,
          discountApplied: "VOUCHER_ONLY",
          voucher: {
            id: "v-1",
            code: "GIAM20",
            name: "Giảm 20%",
            discountType: "PERCENT",
            discountValue: 20,
            discountVnd: 60000,
            combinable: false,
          },
        }),
      )
      .mockResolvedValueOnce(
        makeQuote({
          finalPrice: 190000,
          discountApplied: "COMBINED",
          promotion: {
            id: "p-1",
            name: "Ưu đãi tuần thi",
            discountType: "AMOUNT",
            discountValue: 50000,
            discountVnd: 50000,
            endsAt: "2026-12-01T00:00:00Z",
          },
          voucher: {
            id: "v-2",
            code: "THEM60K",
            name: "Giảm thêm 60k",
            discountType: "AMOUNT",
            discountValue: 60000,
            discountVnd: 60000,
            combinable: true,
          },
        }),
      )
      .mockResolvedValueOnce(
        makeQuote({
          finalPrice: 250000,
          discountApplied: "PROMOTION_ONLY",
          voucherNotBetter: true,
          promotion: {
            id: "p-1",
            name: "Ưu đãi tuần thi",
            discountType: "AMOUNT",
            discountValue: 50000,
            discountVnd: 50000,
            endsAt: "2026-12-01T00:00:00Z",
          },
        }),
      )
      .mockResolvedValueOnce(makeQuote());

    const view = renderWithProviders(
      <VoucherBox
        productCode="HSA-FULL-01"
        seenPrice={300000}
        seenVersionNo={1}
        quote={makeQuote()}
        requestQuote={requestQuote}
        onQuoteApplied={onQuoteApplied}
      />,
    );

    const input = screen.getByLabelText("Nhập mã giảm giá");
    expect(input).toHaveClass("h-12", "max-sm:text-title", "pointer-coarse:text-title");

    await user.click(screen.getByRole("button", { name: "Áp dụng" }));
    const emptyAlert = screen.getByRole("alert");
    expect(emptyAlert).toHaveTextContent("Vui lòng nhập mã giảm giá.");
    expect(emptyAlert).toHaveAttribute("id", "voucher-error-msg");
    expect(input).toHaveAttribute("aria-invalid", "true");
    expect(input).toHaveAttribute("aria-describedby", "voucher-error-msg");
    expect(input).toHaveFocus();
    expect(requestQuote).not.toHaveBeenCalled();

    await user.type(input, "  GIAM20  ");
    await user.click(screen.getByRole("button", { name: "Áp dụng" }));

    expect(requestQuote).toHaveBeenNthCalledWith(1, {
      code: "HSA-FULL-01",
      voucherCode: "GIAM20",
      seenVersionNo: 1,
    });
    const appliedStatus = await screen.findByRole("status");
    expect(appliedStatus).toHaveTextContent("Đã áp dụng mã GIAM20 (-60.000 đ).");
    expect(appliedStatus).toHaveClass("border-border-primary");

    view.rerender(
      <VoucherBox
        productCode="HSA-FULL-01"
        quote={makeQuote({
          promotion: {
            id: "p-1",
            name: "Ưu đãi tuần thi",
            discountType: "AMOUNT",
            discountValue: 50000,
            discountVnd: 50000,
            endsAt: "2026-12-01T00:00:00Z",
          },
        })}
        requestQuote={requestQuote}
        onQuoteApplied={onQuoteApplied}
      />,
    );

    await user.clear(screen.getByLabelText("Nhập mã giảm giá"));
    await user.type(screen.getByLabelText("Nhập mã giảm giá"), "THEM60K");
    await user.click(screen.getByRole("button", { name: "Áp dụng" }));
    expect(await screen.findByRole("status")).toHaveTextContent(
      "Đã áp dụng mã THEM60K (-60.000 đ) cùng ưu đãi tự động.",
    );

    await user.clear(screen.getByLabelText("Nhập mã giảm giá"));
    await user.type(screen.getByLabelText("Nhập mã giảm giá"), "NHOHON");
    await user.click(screen.getByRole("button", { name: "Áp dụng" }));
    expect(await screen.findByRole("status")).toHaveTextContent(
      "Mã NHOHON không giảm nhiều hơn ưu đãi tự động hiện tại",
    );

    view.rerender(
      <VoucherBox
        productCode="HSA-FULL-01"
        quote={makeQuote({
          finalPrice: 240000,
          discountApplied: "VOUCHER_ONLY",
          voucher: {
            id: "v-1",
            code: "GIAM20",
            name: "Giảm 20%",
            discountType: "PERCENT",
            discountValue: 20,
            discountVnd: 60000,
            combinable: false,
          },
        })}
        requestQuote={requestQuote}
        onQuoteApplied={onQuoteApplied}
      />,
    );

    await user.click(screen.getByRole("button", { name: "Gỡ mã" }));
    expect(requestQuote).toHaveBeenLastCalledWith({ code: "HSA-FULL-01" });
    expect(onQuoteApplied).toHaveBeenLastCalledWith(expect.objectContaining({ discountApplied: "NONE" }), {
      action: "remove",
    });
    await waitFor(() => expect(screen.getByLabelText("Nhập mã giảm giá")).toHaveFocus());
  });

  it("hiện thông báo thay thế ưu đãi tự động khi voucher thay auto promo", async () => {
    const user = userEvent.setup();
    const requestQuote = vi.fn().mockResolvedValueOnce(
      makeQuote({
        finalPrice: 150000,
        discountApplied: "VOUCHER_ONLY",
        voucher: {
          id: "v-3",
          code: "GIAM50",
          name: "Giảm 50%",
          discountType: "PERCENT",
          discountValue: 50,
          discountVnd: 150000,
          combinable: false,
        },
      }),
    );

    renderWithProviders(
      <VoucherBox
        productCode="HSA-FULL-01"
        quote={makeQuote({
          finalPrice: 250000,
          discountApplied: "PROMOTION_ONLY",
          promotion: {
            id: "p-1",
            name: "Auto",
            discountType: "AMOUNT",
            discountValue: 50000,
            discountVnd: 50000,
            endsAt: "2026-12-01T00:00:00Z",
          },
        })}
        requestQuote={requestQuote}
        onQuoteApplied={vi.fn()}
      />,
    );

    await user.type(screen.getByLabelText("Nhập mã giảm giá"), "GIAM50");
    await user.click(screen.getByRole("button", { name: "Áp dụng" }));

    expect(await screen.findByRole("status")).toHaveTextContent(
      "Đã áp dụng mã GIAM50 (-150.000 đ). Mã này giảm nhiều hơn nên đã thay thế ưu đãi tự động.",
    );
  });

  it("giữ nguyên nhãn nút Áp dụng khi đang kiểm tra (R1-65) và gọi onUnauthorized khi 401 (R1-30)", async () => {
    const user = userEvent.setup();
    let resolvePending!: (value: PriceQuote) => void;
    const pendingQuote = vi.fn(
      () =>
        new Promise<PriceQuote>((resolve) => {
          resolvePending = resolve;
        }),
    );

    const loadingView = renderWithProviders(
      <VoucherBox
        productCode="HSA-FULL-01"
        quote={makeQuote()}
        requestQuote={pendingQuote}
        onQuoteApplied={vi.fn()}
      />,
    );

    await user.type(screen.getByLabelText("Nhập mã giảm giá"), "WAIT20");
    await user.click(screen.getByRole("button", { name: "Áp dụng" }));

    const busyBtn = screen.getByRole("button", { name: "Áp dụng" });
    expect(busyBtn).toHaveAttribute("aria-busy", "true");
    expect(screen.queryByRole("button", { name: "Đang kiểm tra..." })).toBeNull();

    resolvePending(makeQuote());
    await waitFor(() => expect(busyBtn).not.toHaveAttribute("aria-busy", "true"));
    loadingView.unmount();

    const onUnauthorized = vi.fn();
    const unauthRequest = vi
      .fn()
      .mockRejectedValue(new ApiStatusError(401, "Unauthorized", { code: "UNAUTHORIZED" }));

    renderWithProviders(
      <VoucherBox
        productCode="HSA-FULL-01"
        quote={makeQuote()}
        requestQuote={unauthRequest}
        onQuoteApplied={vi.fn()}
        onUnauthorized={onUnauthorized}
      />,
    );

    await user.type(screen.getByLabelText("Nhập mã giảm giá"), "GIAM20");
    await user.click(screen.getByRole("button", { name: "Áp dụng" }));

    await waitFor(() => expect(onUnauthorized).toHaveBeenCalledTimes(1));
    expect(screen.queryByRole("alert")).toBeNull();
  });

  it("chuyển seenPrice/seenVersionNo khi gỡ mã (R2-1), gọi onUnauthorized khi gỡ mã 401 (R2-16) và hiện lỗi chung khi phiên vẫn sống (R2-3)", async () => {
    const user = userEvent.setup();
    const onRemoveUnauthorized = vi.fn();
    const removeRequest = vi
      .fn()
      .mockResolvedValueOnce(makeQuote())
      .mockRejectedValueOnce(new ApiStatusError(401, "Unauthorized", { code: "UNAUTHORIZED" }));

    const quoteWithVoucher = makeQuote({
      finalPrice: 240000,
      discountApplied: "VOUCHER_ONLY",
      voucher: {
        id: "v-1",
        code: "GIAM20",
        name: "Giảm 20%",
        discountType: "PERCENT",
        discountValue: 20,
        discountVnd: 60000,
        combinable: false,
      },
    });

    const removeOkView = renderWithProviders(
      <VoucherBox
        productCode="HSA-FULL-01"
        seenPrice={300000}
        seenVersionNo={2}
        quote={quoteWithVoucher}
        requestQuote={removeRequest}
        onQuoteApplied={vi.fn()}
        onUnauthorized={onRemoveUnauthorized}
      />,
    );

    await user.click(screen.getByRole("button", { name: "Gỡ mã" }));
    expect(removeRequest).toHaveBeenNthCalledWith(1, {
      code: "HSA-FULL-01",
      seenPrice: 300000,
      seenVersionNo: 2,
    });

    await user.click(screen.getByRole("button", { name: "Gỡ mã" }));
    await waitFor(() => expect(onRemoveUnauthorized).toHaveBeenCalledTimes(1));
    expect(screen.queryByRole("alert")).toBeNull();
    removeOkView.unmount();

    // R2-3: session vẫn sống sau refetch -> báo lỗi chung và đưa focus về ô nhập
    const activeRaceView = renderWithProviders(
      <VoucherBox
        productCode="HSA-FULL-01"
        quote={makeQuote()}
        requestQuote={vi.fn().mockRejectedValueOnce(new ApiStatusError(401, "Unauthorized", { code: "UNAUTHORIZED" }))}
        onQuoteApplied={vi.fn()}
        onUnauthorized={async () => true}
      />,
    );

    const input = screen.getByLabelText("Nhập mã giảm giá");
    await user.type(input, "GIAM20");
    await user.click(screen.getByRole("button", { name: "Áp dụng" }));

    expect(await screen.findByRole("alert")).toHaveTextContent(
      "Không thể áp dụng mã giảm giá. Vui lòng thử lại.",
    );
    await waitFor(() => expect(input).toHaveFocus());
    activeRaceView.unmount();
  });

  const errorCases: Array<{
    code: VoucherErrorKey;
    status: number;
    expectedVi: string;
    expectedEn: string;
  }> = [
    {
      code: "VOUCHER_INVALID",
      status: 422,
      expectedVi: "Mã giảm giá không tồn tại hoặc không hợp lệ.",
      expectedEn: "This voucher code does not exist or is invalid.",
    },
    {
      code: "VOUCHER_EXPIRED",
      status: 422,
      expectedVi: "Mã giảm giá đã hết hạn sử dụng.",
      expectedEn: "This voucher code has expired.",
    },
    {
      code: "VOUCHER_EXHAUSTED",
      status: 422,
      expectedVi: "Mã giảm giá đã hết lượt sử dụng.",
      expectedEn: "This voucher code has reached its usage limit.",
    },
    {
      code: "VOUCHER_USER_LIMIT",
      status: 422,
      expectedVi: "Bạn đã dùng hết số lượt cho phép của mã giảm giá này.",
      expectedEn: "You have reached your personal usage limit for this voucher.",
    },
    {
      code: "VOUCHER_NOT_APPLICABLE",
      status: 422,
      expectedVi: "Mã giảm giá không áp dụng cho bài thi này.",
      expectedEn: "This voucher code is not applicable to this exam.",
    },
    {
      code: "VOUCHER_NOT_ELIGIBLE",
      status: 422,
      expectedVi: "Tài khoản của bạn chưa đủ điều kiện sử dụng mã này.",
      expectedEn: "Your account is not eligible for this voucher.",
    },
    {
      code: "RATE_LIMITED",
      status: 429,
      expectedVi: "Bạn kiểm tra mã quá nhanh. Vui lòng thử lại sau ít phút.",
      expectedEn: "Too many voucher checks. Please try again in a few minutes.",
    },
  ];

  it.each(errorCases)("hiển thị đúng thông báo lỗi $code cho vi và en", async ({ code, status, expectedVi, expectedEn }) => {
    const user = userEvent.setup();
    const requestQuote = vi.fn().mockRejectedValue(new ApiStatusError(status, code, { code }));

    const viView = renderWithProviders(
      <VoucherBox productCode="HSA-FULL-01" quote={makeQuote()} requestQuote={requestQuote} onQuoteApplied={vi.fn()} />,
      { locale: "vi" },
    );

    const viInput = screen.getByLabelText("Nhập mã giảm giá");
    await user.type(viInput, "BADCODE");
    await user.click(screen.getByRole("button", { name: "Áp dụng" }));
    const viAlert = await screen.findByRole("alert");
    expect(viAlert).toHaveTextContent(expectedVi);
    expect(viAlert).toHaveAttribute("id", "voucher-error-msg");
    expect(viInput).toHaveAttribute("aria-invalid", "true");
    expect(viInput).toHaveAttribute("aria-describedby", "voucher-error-msg");
    await waitFor(() => expect(viInput).toHaveFocus());
    viView.unmount();

    renderWithProviders(
      <VoucherBox productCode="HSA-FULL-01" quote={makeQuote()} requestQuote={requestQuote} onQuoteApplied={vi.fn()} />,
      { locale: "en", messages: enMessages },
    );

    const enInput = screen.getByRole("textbox", { name: "Enter voucher code" });
    await user.type(enInput, "BADCODE");
    await user.click(screen.getByRole("button", { name: "Apply" }));
    expect(await screen.findByRole("alert")).toHaveTextContent(expectedEn);
    expect(enInput).toHaveAttribute("aria-invalid", "true");
    await waitFor(() => expect(enInput).toHaveFocus());
  });

  it("R1-38: gọi onProductUnavailable khi requestQuote trả ProductUnavailableError hoặc 409 PRODUCT_UNAVAILABLE lúc áp dụng và gỡ mã", async () => {
    const user = userEvent.setup();
    const onProductUnavailableApply = vi.fn();
    const applyView = renderWithProviders(
      <VoucherBox
        productCode="BO-SP2E-001"
        isBundle
        quote={makeQuote({ productCode: "BO-SP2E-001" })}
        requestQuote={vi.fn().mockRejectedValueOnce(new ProductUnavailableError("Unavailable"))}
        onQuoteApplied={vi.fn()}
        onProductUnavailable={onProductUnavailableApply}
      />,
    );

    await user.type(screen.getByLabelText("Nhập mã giảm giá"), "GIAM20");
    await user.click(screen.getByRole("button", { name: "Áp dụng" }));

    await waitFor(() => expect(onProductUnavailableApply).toHaveBeenCalledTimes(1));
    expect(screen.queryByRole("alert")).toBeNull();
    applyView.unmount();

    const onProductUnavailableRemove = vi.fn();
    renderWithProviders(
      <VoucherBox
        productCode="BO-SP2E-001"
        isBundle
        quote={makeQuote({
          productCode: "BO-SP2E-001",
          voucher: {
            id: "v-1",
            code: "GIAM20",
            name: "20%",
            discountType: "PERCENT",
            discountValue: 20,
            discountVnd: 60000,
            combinable: false,
          },
        })}
        requestQuote={vi
          .fn()
          .mockRejectedValueOnce(new ApiStatusError(409, "Unavailable", { code: "PRODUCT_UNAVAILABLE" }))}
        onQuoteApplied={vi.fn()}
        onProductUnavailable={onProductUnavailableRemove}
      />,
    );

    await user.click(screen.getByRole("button", { name: "Gỡ mã" }));
    await waitFor(() => expect(onProductUnavailableRemove).toHaveBeenCalledTimes(1));
    expect(screen.queryByRole("alert")).toBeNull();
  });
});
