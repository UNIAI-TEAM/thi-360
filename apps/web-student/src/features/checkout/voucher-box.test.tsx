import { screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";
import type { PriceQuote } from "@/features/checkout/api";
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

    await user.click(screen.getByRole("button", { name: "Áp dụng" }));
    expect(screen.getByRole("alert")).toHaveTextContent("Vui lòng nhập mã giảm giá.");
    expect(requestQuote).not.toHaveBeenCalled();

    await user.type(screen.getByLabelText("Nhập mã giảm giá"), "  GIAM20  ");
    await user.click(screen.getByRole("button", { name: "Áp dụng" }));

    expect(requestQuote).toHaveBeenNthCalledWith(1, {
      code: "HSA-FULL-01",
      voucherCode: "GIAM20",
      seenPrice: 300000,
      seenVersionNo: 1,
    });
    expect(await screen.findByRole("status")).toHaveTextContent("Đã áp dụng mã GIAM20 (-60.000 đ).");

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

    await user.type(screen.getByLabelText("Nhập mã giảm giá"), "BADCODE");
    await user.click(screen.getByRole("button", { name: "Áp dụng" }));
    expect(await screen.findByRole("alert")).toHaveTextContent(expectedVi);
    viView.unmount();

    renderWithProviders(
      <VoucherBox productCode="HSA-FULL-01" quote={makeQuote()} requestQuote={requestQuote} onQuoteApplied={vi.fn()} />,
      { locale: "en", messages: enMessages },
    );

    await user.type(screen.getByRole("textbox", { name: "Enter voucher code" }), "BADCODE");
    await user.click(screen.getByRole("button", { name: "Apply" }));
    expect(await screen.findByRole("alert")).toHaveTextContent(expectedEn);
  });
});
