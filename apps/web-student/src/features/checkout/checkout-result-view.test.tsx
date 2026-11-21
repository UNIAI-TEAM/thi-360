import { act, fireEvent, screen, within } from "@testing-library/react";
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

  it("hiện Thanh toán chưa thành công kèm lý do, Thanh toán lại giữ Idempotency-Key qua 500 và giữ tĩnh nhãn nút (AC-2, R1-32, R1-65)", async () => {
    const user = userEvent.setup();
    navState.search = "vnp_TxnRef=DHFAIL0101&vnp_ResponseCode=24&vnp_SecureHash=valid";
    let resolveSecondPay!: (val: { paymentUrl: string }) => void;
    const payOrder = vi
      .fn()
      .mockRejectedValueOnce(new ApiStatusError(500, "Server error"))
      .mockImplementationOnce(
        () =>
          new Promise((resolve) => {
            resolveSecondPay = resolve;
          }),
      );
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

    const retryBtn = screen.getByRole("button", { name: "Thanh toán lại" });
    await user.click(retryBtn);
    const firstKey = payOrder.mock.calls[0]![1];
    expect(await screen.findByText("Không thực hiện được thao tác. Vui lòng thử lại hoặc tạo đơn mới.")).toBeInTheDocument();

    await user.click(retryBtn);
    // R1-65: giữ nguyên nhãn nút khi loading
    expect(retryBtn).toHaveAttribute("aria-busy", "true");
    expect(screen.queryByRole("button", { name: "Đang chuyển tới VNPAY..." })).toBeNull();
    // R1-32: retry sau 500 dùng lại đúng Idempotency-Key
    expect(payOrder.mock.calls[1]![1]).toBe(firstKey);

    resolveSecondPay({ paymentUrl: "http://mock-vnpay/retry02" });
    await act(async () => {
      await Promise.resolve();
    });
    expect(navigate).toHaveBeenCalledWith("http://mock-vnpay/retry02");
  });

  it("xử lý verifyReturn FAILED ngay cả khi latestPayment còn CREATED do trễ IPN (R1-11)", async () => {
    navState.search = "vnp_TxnRef=DHLAG0101&vnp_ResponseCode=24&vnp_SecureHash=valid";

    renderWithProviders(
      <CheckoutResultView
        loadCurrentSession={async () => STUDENT_SESSION}
        verifyReturn={async () => ({ orderCode: "DHLAG01", paymentState: "FAILED" })}
        loadOrderDetail={async () =>
          makeOrder({
            code: "DHLAG01",
            state: "PENDING_PAYMENT",
            fulfilledAt: undefined,
            payments: [
              {
                id: "pay-1",
                attemptSeq: 1,
                txnRef: "DHLAG0101",
                amountVnd: 300000,
                state: "CREATED",
                duplicate: false,
                createdAt: "2026-11-21T03:01:00Z",
              },
            ],
          })
        }
      />,
    );

    expect(await screen.findByRole("heading", { level: 1, name: "Thanh toán chưa thành công" })).toBeInTheDocument();
    expect(screen.getByText("Bạn đã hủy giao dịch trên VNPAY.")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Thanh toán lại" })).toBeInTheDocument();
  });

  it("poll mỗi 2 giây, hỗ trợ order.state=PAID (0 đ CHECKING AC-5), giữ card khi poll lỗi tạm thời và reset khi đổi verifiedOrderCode (AC-3, AC-5, R1-9, R1-31, R1-35, R1-48)", async () => {
    vi.useFakeTimers({ toFake: ["setTimeout", "clearTimeout", "Date"] });
    try {
      // Phần A: PAID (0 đ CHECKING AC-5) poll 2s -> FULFILLED
      navState.search = "order=DHPAID0&state=checking";
      const loadPaid = vi
        .fn()
        .mockResolvedValueOnce(
          makeOrder({
            code: "DHPAID0",
            state: "PAID",
            paymentMethod: "FREE",
            amountDueVnd: 0,
            fulfilledAt: undefined,
            payments: [],
          }),
        )
        .mockResolvedValueOnce(
          makeOrder({
            code: "DHPAID0",
            state: "FULFILLED",
            paymentMethod: "FREE",
            amountDueVnd: 0,
            voucherDiscountVnd: 300000,
          }),
        );

      const paidView = renderWithProviders(
        <CheckoutResultView loadCurrentSession={async () => STUDENT_SESSION} loadOrderDetail={loadPaid} />,
      );

      for (let i = 0; i < 6; i += 1) {
        await act(async () => {
          await vi.advanceTimersByTimeAsync(1);
        });
      }
      expect(screen.getByRole("heading", { level: 1, name: "Đang chờ xác nhận thanh toán" })).toBeInTheDocument();
      expect(screen.getByText("Đang kiểm tra")).toBeInTheDocument();
      expect(screen.getAllByText(/DHPAID0/).length).toBeGreaterThan(0);

      await act(async () => {
        await vi.advanceTimersByTimeAsync(2000);
      });
      expect(screen.getByRole("heading", { level: 1, name: "Đã cấp bài thi" })).toBeInTheDocument();
      paidView.unmount();

      // Phần B: Lỗi poll tạm thời giữ checking card và tự phục hồi (R1-9)
      navState.search = "order=DHFLAKY&state=checking";
      const loadFlaky = vi
        .fn()
        .mockResolvedValueOnce(makeOrder({ code: "DHFLAKY", state: "PENDING_PAYMENT", fulfilledAt: undefined }))
        .mockRejectedValueOnce(new ApiStatusError(500, "Transient blip"))
        .mockResolvedValueOnce(makeOrder({ code: "DHFLAKY", state: "FULFILLED" }));

      const flakyView = renderWithProviders(
        <CheckoutResultView loadCurrentSession={async () => STUDENT_SESSION} loadOrderDetail={loadFlaky} />,
      );

      for (let i = 0; i < 6; i += 1) {
        await act(async () => {
          await vi.advanceTimersByTimeAsync(1);
        });
      }
      expect(screen.getByRole("heading", { level: 1, name: "Đang chờ xác nhận thanh toán" })).toBeInTheDocument();

      await act(async () => {
        await vi.advanceTimersByTimeAsync(2000);
      });
      // Vẫn giữ màn checking, hiện cảnh báo không phá hủy
      expect(screen.getByRole("heading", { level: 1, name: "Đang chờ xác nhận thanh toán" })).toBeInTheDocument();
      expect(screen.getByRole("alert")).toHaveTextContent("Tạm thời không kiểm tra được trạng thái mới nhất");

      await act(async () => {
        await vi.advanceTimersByTimeAsync(2000);
      });
      expect(screen.getByRole("heading", { level: 1, name: "Đã cấp bài thi" })).toBeInTheDocument();
      flakyView.unmount();

      // Phần C: Timeout 60s dừng poll và đổi verifiedOrderCode reset timedOut (AC-3, R1-31)
      navState.search = "order=DHPEND60&state=checking";
      const loadOrderDetail = vi.fn(async () =>
        makeOrder({
          code: navState.search.includes("DHNEW02") ? "DHNEW02" : "DHPEND60",
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

      const timeoutView = renderWithProviders(
        <CheckoutResultView loadCurrentSession={async () => STUDENT_SESSION} loadOrderDetail={loadOrderDetail} />,
      );

      for (let i = 0; i < 6; i += 1) {
        await act(async () => {
          await vi.advanceTimersByTimeAsync(1);
        });
      }

      expect(screen.getByRole("heading", { level: 1, name: "Đang chờ xác nhận thanh toán" })).toBeInTheDocument();
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

      expect(screen.getByRole("heading", { level: 1, name: "Đang kiểm tra thanh toán" })).toBeInTheDocument();
      expect(screen.getByText(/Đã quá 60 giây.*DHPEND60/)).toBeInTheDocument();
      expect(screen.getByText(/liên hệ hỗ trợ.*DHPEND60/)).toBeInTheDocument();
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

      // R1-31: đổi sang order khác phải reset timedOut
      navState.search = "order=DHNEW02&state=checking";
      timeoutView.rerender(
        <CheckoutResultView loadCurrentSession={async () => STUDENT_SESSION} loadOrderDetail={loadOrderDetail} />,
      );
      for (let i = 0; i < 6; i += 1) {
        await act(async () => {
          await vi.advanceTimersByTimeAsync(1);
        });
      }
      expect(screen.getByRole("heading", { level: 1, name: "Đang chờ xác nhận thanh toán" })).toBeInTheDocument();
    } finally {
      vi.useRealTimers();
    }
  });

  it("chạy ở chế độ khách khi chưa đăng nhập, hiện link Đăng nhập/Đăng ký ở header và lời nhắc trung lập cho ?order= (AC-13, R1-10, R1-66)", async () => {
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
    const banner = screen.getByRole("banner");
    expect(within(banner).getByRole("link", { name: "Đăng nhập" })).toBeInTheDocument();
    expect(within(banner).getByRole("link", { name: "Đăng ký" })).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Đăng nhập để xem bài thi" })).toHaveAttribute(
      "href",
      "/login?callbackUrl=%2Fcheckout%2Fresult%3Forder%3DDHGUEST01",
    );
    expect(loadOrderDetail).not.toHaveBeenCalled();
    okGuest.unmount();

    // R1-10: ?order= không có vnp_* -> lời nhắc trung lập, KHÔNG có chip "Đang kiểm tra"
    navState.search = "order=DHBOOKMARK1";
    const bookmarkGuest = renderWithProviders(
      <CheckoutResultView loadCurrentSession={async () => null} loadOrderDetail={loadOrderDetail} />,
    );
    expect(await screen.findByRole("heading", { level: 1, name: "Đăng nhập để xem trạng thái đơn hàng" })).toBeInTheDocument();
    expect(screen.getByText("Đăng nhập để xem kết quả đơn DHBOOKMARK1.")).toBeInTheDocument();
    expect(screen.queryByText("Đang kiểm tra")).toBeNull();
    expect(screen.getByRole("link", { name: "Đăng nhập để kiểm tra đơn" })).toHaveAttribute(
      "href",
      "/login?callbackUrl=%2Fcheckout%2Fresult%3Forder%3DDHBOOKMARK1",
    );
    bookmarkGuest.unmount();

    navState.search = "vnp_TxnRef=DHGUEST02&vnp_ResponseCode=24&vnp_SecureHash=valid";
    const failedGuest = renderWithProviders(
      <CheckoutResultView
        loadCurrentSession={async () => null}
        verifyReturn={async () => ({ orderCode: "DHGUEST02", paymentState: "FAILED" })}
        loadOrderDetail={loadOrderDetail}
      />,
    );
    expect(await screen.findByRole("heading", { level: 1, name: "Thanh toán chưa thành công" })).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Đăng nhập để kiểm tra đơn" })).toHaveAttribute(
      "href",
      "/login?callbackUrl=%2Fcheckout%2Fresult%3Forder%3DDHGUEST02",
    );
    expect(loadOrderDetail).not.toHaveBeenCalled();
    failedGuest.unmount();

    navState.search = "";
    renderWithProviders(<CheckoutResultView loadCurrentSession={async () => null} loadOrderDetail={loadOrderDetail} />);
    expect(await screen.findByRole("heading", { level: 1, name: "Thiếu thông tin giao dịch" })).toBeInTheDocument();
    expect(within(screen.getByRole("main")).getByRole("link", { name: "Đăng nhập" })).toHaveAttribute(
      "href",
      "/login?callbackUrl=%2Fcheckout%2Fresult",
    );
    expect(loadOrderDetail).not.toHaveBeenCalled();
  });

  it("xử lý lỗi sessionQuery 500 với nút Thử lại và verifyReturn 404 ORDER_NOT_FOUND không hiện Thử lại (R1-33, R1-34)", async () => {
    const user = userEvent.setup();
    navState.search = "order=DH123";
    const loadSession = vi
      .fn()
      .mockRejectedValueOnce(new ApiStatusError(500, "Session error"))
      .mockResolvedValueOnce(STUDENT_SESSION);

    const errView = renderWithProviders(
      <CheckoutResultView
        loadCurrentSession={loadSession}
        loadOrderDetail={async () => makeOrder({ code: "DH123", state: "FULFILLED" })}
      />,
    );

    expect(await screen.findByRole("heading", { level: 1, name: "Không thể kiểm tra kết quả thanh toán" })).toBeInTheDocument();
    expect(screen.queryByRole("link", { name: "Đăng nhập để kiểm tra đơn" })).toBeNull();
    await user.click(screen.getByRole("button", { name: "Thử lại" }));
    expect(await screen.findByRole("heading", { level: 1, name: "Đã cấp bài thi" })).toBeInTheDocument();
    errView.unmount();

    // R1-34: verifyReturn 404 ORDER_NOT_FOUND -> trạng thái terminal không có nút Thử lại
    navState.search = "vnp_TxnRef=DHMISSING01&vnp_ResponseCode=00&vnp_SecureHash=valid";
    renderWithProviders(
      <CheckoutResultView
        loadCurrentSession={async () => STUDENT_SESSION}
        verifyReturn={vi.fn().mockRejectedValueOnce(new ApiStatusError(404, "Not found", { code: "ORDER_NOT_FOUND" }))}
      />,
    );

    expect(await screen.findByRole("heading", { level: 1, name: "Không tìm thấy đơn hàng" })).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Xem đơn của tôi" })).toHaveAttribute("href", "/account/orders");
    expect(screen.queryByRole("button", { name: "Thử lại" })).toBeNull();
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
      await screen.findByRole("heading", { level: 1, name: "Không tìm thấy đơn trong tài khoản đang đăng nhập" }),
    ).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Đăng nhập bằng tài khoản khác" })).toHaveAttribute(
      "href",
      "/login?callbackUrl=%2Fcheckout%2Fresult%3Forder%3DDHOTHER99",
    );
    expect(screen.getByRole("link", { name: "Xem đơn của tôi" })).toHaveAttribute("href", "/account/orders");
  });

  it("không dừng poll vì vnp_TxnRef cũ đã FAILED khi đã tạo lần thanh toán mới (R2-5) và chờ session khi verifyReturn 404 (R2-25)", async () => {
    vi.useFakeTimers({ toFake: ["setTimeout", "clearTimeout", "Date"] });
    try {
      navState.search = "vnp_TxnRef=DH0101&vnp_ResponseCode=24&vnp_SecureHash=valid";
      const loadOrderDetail = vi.fn().mockResolvedValue(
        makeOrder({
          code: "DH01",
          state: "PENDING_PAYMENT",
          fulfilledAt: undefined,
          payments: [
            {
              id: "pay-1",
              attemptSeq: 1,
              txnRef: "DH0101",
              amountVnd: 300000,
              state: "FAILED",
              responseCode: "24",
              duplicate: false,
              createdAt: "2026-11-21T03:01:00Z",
            },
            {
              id: "pay-2",
              attemptSeq: 2,
              txnRef: "DH0102",
              amountVnd: 300000,
              state: "CREATED",
              duplicate: false,
              createdAt: "2026-11-21T03:02:00Z",
            },
          ],
        }),
      );

      const staleTxnView = renderWithProviders(
        <CheckoutResultView
          loadCurrentSession={async () => STUDENT_SESSION}
          verifyReturn={async () => ({ orderCode: "DH01", paymentState: "FAILED" })}
          loadOrderDetail={loadOrderDetail}
        />,
      );

      for (let i = 0; i < 6; i += 1) {
        await act(async () => {
          await vi.advanceTimersByTimeAsync(1);
        });
      }

      expect(screen.getByRole("heading", { level: 1, name: "Đang chờ xác nhận thanh toán" })).toBeInTheDocument();
      expect(screen.queryByRole("heading", { level: 1, name: "Thanh toán chưa thành công" })).toBeNull();
      expect(loadOrderDetail).toHaveBeenCalledTimes(1);

      await act(async () => {
        await vi.advanceTimersByTimeAsync(2000);
      });
      expect(loadOrderDetail).toHaveBeenCalledTimes(2);
      staleTxnView.unmount();
    } finally {
      vi.useRealTimers();
    }

    // R2-25: verifyReturn 404 khi sessionQuery đang tải -> giữ skeleton, không nháy CTA khách
    navState.search = "vnp_TxnRef=DH404WAIT&vnp_ResponseCode=00&vnp_SecureHash=valid";
    let resolveSession!: (session: typeof STUDENT_SESSION | null) => void;
    renderWithProviders(
      <CheckoutResultView
        loadCurrentSession={() =>
          new Promise((resolve) => {
            resolveSession = resolve;
          })
        }
        verifyReturn={vi.fn().mockRejectedValueOnce(new ApiStatusError(404, "Not found", { code: "ORDER_NOT_FOUND" }))}
      />,
    );

    const loadingStatus = await screen.findByRole("status", { name: "Đang tải" });
    expect(loadingStatus).toHaveAttribute("aria-busy", "true");
    expect(screen.queryByRole("link", { name: "Đăng nhập" })).toBeNull();

    await act(async () => {
      resolveSession(STUDENT_SESSION);
    });
    expect(await screen.findByRole("heading", { level: 1, name: "Không tìm thấy đơn hàng" })).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Xem đơn của tôi" })).toHaveAttribute("href", "/account/orders");
  });

  it("poll trả 404 ORDER_NOT_FOUND ẩn đơn cũ và dừng poll (R2-6), lỗi sau timeout 60s không hứa tự thử lại (R2-26)", async () => {
    vi.useFakeTimers({ toFake: ["setTimeout", "clearTimeout", "Date"] });
    try {
      // R2-6: PENDING_PAYMENT -> poll 404 ORDER_NOT_FOUND
      navState.search = "order=DHSWITCH404&state=checking";
      const loadSwitchingOrder = vi
        .fn()
        .mockResolvedValueOnce(makeOrder({ code: "DHSWITCH404", state: "PENDING_PAYMENT", fulfilledAt: undefined }))
        .mockRejectedValueOnce(new ApiStatusError(404, "Not found", { code: "ORDER_NOT_FOUND" }));

      const switchView = renderWithProviders(
        <CheckoutResultView loadCurrentSession={async () => STUDENT_SESSION} loadOrderDetail={loadSwitchingOrder} />,
      );

      for (let i = 0; i < 6; i += 1) {
        await act(async () => {
          await vi.advanceTimersByTimeAsync(1);
        });
      }
      expect(screen.getByRole("heading", { level: 1, name: "Đang chờ xác nhận thanh toán" })).toBeInTheDocument();

      await act(async () => {
        await vi.advanceTimersByTimeAsync(2000);
      });
      expect(
        screen.getByRole("heading", { level: 1, name: "Không tìm thấy đơn trong tài khoản đang đăng nhập" }),
      ).toBeInTheDocument();
      expect(screen.queryByText("Đề thi thử ĐGNL HSA số 1")).toBeNull();
      const callsAfter404 = loadSwitchingOrder.mock.calls.length;

      await act(async () => {
        await vi.advanceTimersByTimeAsync(6000);
      });
      expect(loadSwitchingOrder).toHaveBeenCalledTimes(callsAfter404);
      switchView.unmount();

      // R2-26: poll cuối trước timeout 60s lỗi -> khi timeout dừng poll, alert không hứa hệ thống vẫn tự thử lại
      navState.search = "order=DHTIMEOUTERR&state=checking";
      let pollCount = 0;
      const loadTimeoutThenErr = vi.fn(async () => {
        pollCount += 1;
        if (pollCount >= 30) {
          throw new ApiStatusError(500, "Still down");
        }
        return makeOrder({ code: "DHTIMEOUTERR", state: "PENDING_PAYMENT", fulfilledAt: undefined });
      });

      renderWithProviders(
        <CheckoutResultView loadCurrentSession={async () => STUDENT_SESSION} loadOrderDetail={loadTimeoutThenErr} />,
      );

      for (let i = 0; i < 6; i += 1) {
        await act(async () => {
          await vi.advanceTimersByTimeAsync(1);
        });
      }
      for (let i = 0; i < 30; i += 1) {
        await act(async () => {
          await vi.advanceTimersByTimeAsync(2000);
        });
      }
      expect(screen.getByRole("heading", { level: 1, name: "Đang kiểm tra thanh toán" })).toBeInTheDocument();

      const alert = screen.getByRole("alert");
      expect(alert).toHaveTextContent(
        "Không kiểm tra lại được trạng thái đơn lúc này. Hãy bấm Kiểm tra lại ngay hoặc liên hệ hỗ trợ.",
      );
      expect(alert).not.toHaveTextContent("Hệ thống vẫn đang tiếp tục thử lại.");

      const callsAtTimeout = loadTimeoutThenErr.mock.calls.length;
      await act(async () => {
        await vi.advanceTimersByTimeAsync(6000);
      });
      expect(loadTimeoutThenErr).toHaveBeenCalledTimes(callsAtTimeout);
    } finally {
      vi.useRealTimers();
    }
  });
});
