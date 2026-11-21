import { act, fireEvent, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import type { OrderDetail } from "@/features/orders/api";
import { ApiStatusError } from "@/lib/api/client";
import { renderWithProviders } from "@/test/render";
import { STUDENT_SESSION } from "@/test/storefront-fixtures";
import { CheckoutResultView } from "./checkout-result-view";

const navState = {
  pathname: "/checkout/result",
  search: "",
};

vi.mock("next/navigation", () => ({
  usePathname: () => navState.pathname,
  useSearchParams: () => new URLSearchParams(navState.search),
}));

function makeOrder(overrides: Partial<OrderDetail> = {}): OrderDetail {
  return {
    id: "ord-1",
    code: "DH261121RES1",
    userId: STUDENT_SESSION.id,
    buyerUsername: STUDENT_SESSION.username,
    buyerFullName: STUDENT_SESSION.fullName,
    productType: "EXAM",
    examId: "exam-1",
    examVersionNo: 2,
    optionCodes: [],
    productCode: "HSA-FULL-01",
    productName: "Đề thi thử ĐGNL HSA số 1",
    listPriceVnd: 300000,
    promotionDiscountVnd: 0,
    voucherDiscountVnd: 0,
    discountApplied: "NONE",
    amountDueVnd: 300000,
    attemptsGranted: 2,
    benefits: { aiExplanation: true, strengthWeakness: true, practice: false } as unknown as OrderDetail["benefits"],
    resultView: "FULL_SOLUTION",
    paymentMethod: "VNPAY",
    state: "FULFILLED",
    fulfilledAt: "2026-11-21T03:05:00Z",
    createdAt: "2026-11-21T03:00:00Z",
    updatedAt: "2026-11-21T03:05:00Z",
    items: [],
    payments: [],
    history: [],
    ...overrides,
  };
}

describe("CheckoutResultView", () => {
  beforeEach(() => {
    navState.pathname = "/checkout/result";
    navState.search = "";
  });

  it("hiện Đã cấp bài thi sau VNPAY thành công và hỗ trợ đơn 0 đ voucher (AC-1, AC-4)", async () => {
    navState.search = "vnp_TxnRef=DH261121RES101&vnp_ResponseCode=00&vnp_SecureHash=valid";

    const vnpView = renderWithProviders(
      <CheckoutResultView
        loadCurrentSession={async () => STUDENT_SESSION}
        verifyReturn={async () => ({ orderCode: "DH261121RES1", paymentState: "SUCCEEDED" })}
        loadOrderDetail={async () => makeOrder()}
      />,
    );

    expect(await screen.findByRole("heading", { level: 1, name: "Đã cấp bài thi" })).toBeInTheDocument();
    expect(screen.getByText("Đề thi thử ĐGNL HSA số 1")).toHaveAttribute("data-exam-content");
    expect(screen.getByRole("link", { name: "Vào Bài thi của tôi" })).toHaveAttribute("href", "/home");
    expect(screen.getByRole("link", { name: "Xem chi tiết đơn" })).toHaveAttribute(
      "href",
      "/account/orders/DH261121RES1",
    );
    vnpView.unmount();

    navState.search = "order=DHFREE01";
    renderWithProviders(
      <CheckoutResultView
        loadCurrentSession={async () => STUDENT_SESSION}
        loadOrderDetail={async () =>
          makeOrder({
            code: "DHFREE01",
            amountDueVnd: 0,
            voucherDiscountVnd: 300000,
            discountApplied: "VOUCHER_ONLY",
          })
        }
      />,
    );

    expect(await screen.findByRole("heading", { level: 1, name: "Đã cấp bài thi" })).toBeInTheDocument();
    expect(screen.getByText("0 đ (đã áp dụng voucher)")).toBeInTheDocument();
  });

  it("hiện Thanh toán chưa thành công kèm lý do và Thanh toán lại gọi /pay (AC-2)", async () => {
    const user = userEvent.setup();
    navState.search = "vnp_TxnRef=DHFAIL0101&vnp_ResponseCode=24&vnp_SecureHash=valid";
    const payOrder = vi.fn().mockResolvedValue({ paymentUrl: "http://mock-vnpay/retry02" });
    const navigate = vi.fn();

    renderWithProviders(
      <CheckoutResultView
        loadCurrentSession={async () => STUDENT_SESSION}
        verifyReturn={async () => ({ orderCode: "DHFAIL01", paymentState: "FAILED" })}
        loadOrderDetail={async () =>
          makeOrder({
            code: "DHFAIL01",
            state: "PENDING_PAYMENT",
            fulfilledAt: undefined,
            payments: [
              {
                id: "pay-1",
                attemptSeq: 1,
                txnRef: "DHFAIL0101",
                amountVnd: 300000,
                state: "FAILED",
                responseCode: "24",
                duplicate: false,
                createdAt: "2026-11-21T03:01:00Z",
              },
            ],
          })
        }
        payOrder={payOrder}
        navigate={navigate}
      />,
    );

    expect(await screen.findByRole("heading", { level: 1, name: "Thanh toán chưa thành công" })).toBeInTheDocument();
    expect(screen.getByText("Bạn đã hủy giao dịch trên VNPAY.")).toBeInTheDocument();

    await user.click(screen.getByRole("button", { name: "Thanh toán lại" }));
    expect(payOrder).toHaveBeenCalledWith("DHFAIL01", expect.any(String));
    expect(navigate).toHaveBeenCalledWith("http://mock-vnpay/retry02");
  });

  it("poll mỗi 2 giây và dừng ở 60 giây với màn hình Đang chờ xác nhận thanh toán (AC-3, AC-5)", async () => {
    vi.useFakeTimers({ toFake: ["setTimeout", "clearTimeout", "Date"] });
    try {
      navState.search = "order=DHPEND60&state=checking";
      const loadOrderDetail = vi.fn(async () =>
        makeOrder({
          code: "DHPEND60",
          state: "PENDING_PAYMENT",
          fulfilledAt: undefined,
          payments: [
            {
              id: "pay-1",
              attemptSeq: 1,
              txnRef: "DHPEND6001",
              amountVnd: 300000,
              state: "CREATED",
              duplicate: false,
              createdAt: "2026-11-21T03:01:00Z",
            },
          ],
        }),
      );

      renderWithProviders(
        <CheckoutResultView loadCurrentSession={async () => STUDENT_SESSION} loadOrderDetail={loadOrderDetail} />,
      );

      for (let i = 0; i < 6; i += 1) {
        await act(async () => {
          await vi.advanceTimersByTimeAsync(1);
        });
      }

      expect(screen.getByRole("heading", { level: 1, name: "Đang kiểm tra thanh toán" })).toBeInTheDocument();
      expect(screen.getByText("Đang kiểm tra")).toBeInTheDocument();
      expect(loadOrderDetail).toHaveBeenCalledTimes(1);

      await act(async () => {
        await vi.advanceTimersByTimeAsync(2000);
      });
      expect(loadOrderDetail).toHaveBeenCalledTimes(2);

      for (let i = 0; i < 29; i += 1) {
        await act(async () => {
          await vi.advanceTimersByTimeAsync(2000);
        });
      }

      expect(screen.getByRole("heading", { level: 1, name: "Đang chờ xác nhận thanh toán" })).toBeInTheDocument();
      const countAtTimeout = loadOrderDetail.mock.calls.length;

      await act(async () => {
        await vi.advanceTimersByTimeAsync(10_000);
      });
      expect(loadOrderDetail).toHaveBeenCalledTimes(countAtTimeout);

      await act(async () => {
        fireEvent.click(screen.getByRole("button", { name: "Kiểm tra lại ngay" }));
        await vi.advanceTimersByTimeAsync(1);
      });
      expect(loadOrderDetail).toHaveBeenCalledTimes(countAtTimeout + 1);
    } finally {
      vi.useRealTimers();
    }
  });

  it("chạy ở chế độ khách khi chưa đăng nhập mà không gọi /me/orders (AC-13)", async () => {
    navState.search = "vnp_TxnRef=DHGUEST01&vnp_ResponseCode=00&vnp_SecureHash=valid";
    const loadOrderDetail = vi.fn();

    const okGuest = renderWithProviders(
      <CheckoutResultView
        loadCurrentSession={async () => null}
        verifyReturn={async () => ({ orderCode: "DHGUEST01", paymentState: "SUCCEEDED" })}
        loadOrderDetail={loadOrderDetail}
      />,
    );

    expect(await screen.findByRole("heading", { level: 1, name: "Thanh toán thành công" })).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Đăng nhập để xem bài thi" })).toHaveAttribute(
      "href",
      "/login?callbackUrl=%2Fcheckout%2Fresult%3Fvnp_TxnRef%3DDHGUEST01%26vnp_ResponseCode%3D00%26vnp_SecureHash%3Dvalid",
    );
    expect(loadOrderDetail).not.toHaveBeenCalled();
    okGuest.unmount();

    navState.search = "vnp_TxnRef=DHGUEST02&vnp_ResponseCode=24&vnp_SecureHash=valid";
    renderWithProviders(
      <CheckoutResultView
        loadCurrentSession={async () => null}
        verifyReturn={async () => ({ orderCode: "DHGUEST02", paymentState: "FAILED" })}
        loadOrderDetail={loadOrderDetail}
      />,
    );
    expect(await screen.findByRole("heading", { level: 1, name: "Thanh toán chưa thành công" })).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Đăng nhập để kiểm tra đơn" })).toBeInTheDocument();
    expect(loadOrderDetail).not.toHaveBeenCalled();
  });

  it("xử lý 404 đơn thuộc tài khoản khác trên /checkout/result (AC-14)", async () => {
    navState.search = "order=DHOTHER99";

    renderWithProviders(
      <CheckoutResultView
        loadCurrentSession={async () => STUDENT_SESSION}
        loadOrderDetail={async () => {
          throw new ApiStatusError(404, "Not found", { code: "ORDER_NOT_FOUND" });
        }}
      />,
    );

    expect(
      await screen.findByRole("heading", { name: "Không tìm thấy đơn trong tài khoản đang đăng nhập" }),
    ).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Đăng nhập bằng tài khoản khác" })).toHaveAttribute(
      "href",
      "/login?callbackUrl=%2Fcheckout%2Fresult%3Forder%3DDHOTHER99",
    );
    expect(screen.getByRole("link", { name: "Xem đơn của tôi" })).toHaveAttribute("href", "/account/orders");
  });
});
