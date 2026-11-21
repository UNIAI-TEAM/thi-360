import { QueryClient } from "@tanstack/react-query";
import { screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, describe, expect, it, vi } from "vitest";
import { MY_ORDER_DETAIL_QUERY_KEY, type OrderDetail } from "@/features/orders/api";
import { formatDateTime } from "@/features/orders/format";
import { ApiStatusError } from "@/lib/api/client";
import enMessages from "@/messages/en.json";
import { installContainerWidth, PHONE_CONTENT_WIDTH } from "@/test/container-width";
import { renderWithProviders } from "@/test/render";
import { STUDENT_SESSION } from "@/test/storefront-fixtures";
import { OrderDetailView } from "./order-detail-view";

function makeDetail(overrides: Partial<OrderDetail> = {}): OrderDetail {
  return {
    id: "ord-1",
    code: "DH261117K7M2Q9",
    userId: "u-stu",
    buyerUsername: "student01",
    buyerFullName: "Học viên 01",
    productType: "EXAM",
    examId: "exam-1",
    examVersionNo: 2,
    optionCodes: [],
    productCode: "HSA-FULL-03",
    productName: "Đề thi thử ĐGNL HSA số 3",
    listPriceVnd: 450000,
    promotionDiscountVnd: 90000,
    voucherCode: "CHAOBANMOI",
    voucherDiscountVnd: 50000,
    discountApplied: "COMBINED",
    amountDueVnd: 310000,
    attemptsGranted: 3,
    benefits: { aiExplanation: true, strengthWeakness: false, practice: false } as unknown as OrderDetail["benefits"],
    resultView: "FULL_SOLUTION",
    paymentMethod: "VNPAY",
    state: "PENDING_PAYMENT",
    expiresAt: "2026-11-17T03:27:00Z",
    createdAt: "2026-11-17T03:12:00Z",
    updatedAt: "2026-11-17T03:12:00Z",
    items: [
      {
        id: "item-1",
        examId: "exam-1",
        examVersionNo: 2,
        examCode: "HSA-FULL-03",
        examName: "Đề thi thử ĐGNL HSA số 3",
        optionCodes: [],
        attemptsGranted: 3,
        benefits: {},
        resultView: "FULL_SOLUTION",
        position: 1,
      },
    ],
    payments: [
      {
        id: "pay-1",
        attemptSeq: 1,
        txnRef: "DH261117K7M2Q901",
        amountVnd: 310000,
        state: "FAILED",
        duplicate: false,
        bankCode: "NCB",
        cardType: "ATM",
        responseCode: "51",
        createdAt: "2026-11-17T03:13:00Z",
      },
    ],
    history: [
      {
        id: "hist-1",
        toState: "PENDING_PAYMENT",
        actor: "LEARNER",
        reason: "ORDER_CREATED",
        at: "2026-11-17T03:12:00Z",
      },
    ],
    ...overrides,
  };
}

describe("OrderDetailView · AC-14 & AC-21", () => {
  it("AC-14: 404 ORDER_NOT_FOUND hiện đúng thông báo không tìm thấy đơn trong tài khoản đang đăng nhập", async () => {
    renderWithProviders(
      <OrderDetailView
        code="DH-OTHER-USER"
        loadCurrentSession={async () => STUDENT_SESSION}
        loadOrderDetail={async () => {
          throw new ApiStatusError(404, "Not found", { code: "ORDER_NOT_FOUND" });
        }}
      />,
    );

    const alert = await screen.findByRole("alert");
    expect(alert).toHaveTextContent(
      "Không tìm thấy đơn trong tài khoản đang đăng nhập. Hãy kiểm tra bạn đã đăng nhập đúng tài khoản dùng để mua chưa.",
    );
    expect(screen.getByRole("link", { name: "Đăng nhập bằng tài khoản khác" })).toHaveAttribute(
      "href",
      "/login?callbackUrl=%2Faccount%2Forders%2FDH-OTHER-USER",
    );
    expect(screen.getByRole("link", { name: "Quay lại đơn của tôi" })).toHaveAttribute("href", "/account/orders");
  });

  it("AC-21: đơn chờ có Tiếp tục thanh toán (/pay) và Hủy đơn (/cancel)", async () => {
    const detail = makeDetail();
    const cancelled = makeDetail({ state: "CANCELLED" });
    const loadDetail = vi.fn().mockResolvedValueOnce(detail).mockResolvedValueOnce(cancelled);
    const payOrder = vi.fn(async () => ({
      next: "REDIRECT",
      paymentUrl: "https://mock.local/pay?txn=DH261117K7M2Q902",
      txnRef: "DH261117K7M2Q902",
      order: detail,
    }));
    const cancelOrder = vi.fn(async () => cancelled);
    const navigate = vi.fn();

    renderWithProviders(
      <OrderDetailView
        code="DH261117K7M2Q9"
        loadCurrentSession={async () => STUDENT_SESSION}
        loadOrderDetail={loadDetail}
        payOrder={payOrder}
        cancelOrder={cancelOrder}
        navigate={navigate}
      />,
    );

    expect(await screen.findByRole("heading", { level: 1, name: "Đơn DH261117K7M2Q9" })).toBeInTheDocument();
    expect(screen.getByText("Chờ thanh toán")).toBeInTheDocument();
    expect(screen.getByText("DH261117K7M2Q901")).toBeInTheDocument();
    expect(screen.queryByRole("region", { name: "Danh sách giao dịch thanh toán" })).toBeNull();
    const paymentsTable = screen.getByRole("table", { name: "Danh sách giao dịch thanh toán" });
    expect(within(paymentsTable).getByRole("columnheader", { name: "Chi tiết" })).toHaveClass("min-w-48");
    const firstRowCells = within(within(paymentsTable).getAllByRole("row")[1]!).getAllByRole("cell");
    expect(firstRowCells[5]).toHaveClass("min-w-48");
    expect(within(paymentsTable).getByText("Tài khoản ngân hàng không đủ số dư để thanh toán.")).toBeInTheDocument();

    await userEvent.click(screen.getByRole("button", { name: "Tiếp tục thanh toán" }));
    expect(payOrder).toHaveBeenCalledWith("DH261117K7M2Q9", expect.any(String));
    expect(navigate).toHaveBeenCalledWith("https://mock.local/pay?txn=DH261117K7M2Q902");

    await userEvent.click(screen.getByRole("button", { name: "Hủy đơn" }));
    const dialog = screen.getByRole("dialog");
    await userEvent.click(within(dialog).getByRole("button", { name: "Hủy đơn" }));
    expect(cancelOrder).toHaveBeenCalledWith("DH261117K7M2Q9", expect.any(String));
    expect(await screen.findByText("Đã hủy")).toBeInTheDocument();
  });

  it("AC-21: máy chủ từ chối /pay (ORDER_EXPIRED) hiện lý do, yêu cầu tạo đơn mới tới /checkout/<code> và tải lại đơn", async () => {
    const pending = makeDetail();
    const expired = makeDetail({ state: "EXPIRED" });
    const loadDetail = vi.fn().mockResolvedValueOnce(pending).mockResolvedValueOnce(expired);
    const payOrder = vi.fn().mockRejectedValue(new ApiStatusError(409, "Order expired", { code: "ORDER_EXPIRED" }));

    renderWithProviders(
      <OrderDetailView
        code="DH261117K7M2Q9"
        loadCurrentSession={async () => STUDENT_SESSION}
        loadOrderDetail={loadDetail}
        payOrder={payOrder}
      />,
    );

    await screen.findByRole("button", { name: "Tiếp tục thanh toán" });
    await userEvent.click(screen.getByRole("button", { name: "Tiếp tục thanh toán" }));

    const alert = await screen.findByRole("alert");
    expect(alert).toHaveTextContent(/Đơn đã hết hạn thanh toán/);
    expect(within(alert).getByRole("link", { name: "Tạo đơn mới" })).toHaveAttribute("href", "/checkout/HSA-FULL-03");
    expect(await screen.findByText("Hết hạn")).toBeInTheDocument();
  });

  it("AC-21: máy chủ từ chối /pay vì PRODUCT_DISCONTINUED hiện liên kết Về danh mục bài thi", async () => {
    const pending = makeDetail();
    const loadDetail = vi.fn().mockResolvedValue(pending);
    const payOrder = vi.fn().mockRejectedValue(new ApiStatusError(410, "Gone", { code: "PRODUCT_DISCONTINUED" }));

    renderWithProviders(
      <OrderDetailView
        code="DH261117K7M2Q9"
        loadCurrentSession={async () => STUDENT_SESSION}
        loadOrderDetail={loadDetail}
        payOrder={payOrder}
      />,
    );

    await userEvent.click(await screen.findByRole("button", { name: "Tiếp tục thanh toán" }));

    const alert = await screen.findByRole("alert");
    expect(alert).toHaveTextContent(/Bài thi đã ngừng bán/);
    expect(within(alert).getByRole("link", { name: "Về danh mục bài thi" })).toHaveAttribute("href", "/#exams");
  });

  it("AC-21: máy chủ từ chối /cancel (ORDER_NOT_PENDING) hiện lỗi ngay trong hộp thoại và ở chi tiết đơn", async () => {
    const pending = makeDetail();
    const paid = makeDetail({ state: "PAID" });
    const loadDetail = vi.fn().mockResolvedValueOnce(pending).mockResolvedValueOnce(paid);
    const cancelOrder = vi.fn().mockRejectedValue(new ApiStatusError(409, "Not pending", { code: "ORDER_NOT_PENDING" }));

    renderWithProviders(
      <OrderDetailView
        code="DH261117K7M2Q9"
        loadCurrentSession={async () => STUDENT_SESSION}
        loadOrderDetail={loadDetail}
        cancelOrder={cancelOrder}
      />,
    );

    await userEvent.click(await screen.findByRole("button", { name: "Hủy đơn" }));
    const dialog = screen.getByRole("dialog");
    await userEvent.click(within(dialog).getByRole("button", { name: "Hủy đơn" }));

    expect(await within(dialog).findByRole("alert")).toHaveTextContent("Đơn không còn ở trạng thái chờ thanh toán.");
    expect(within(dialog).queryByRole("link", { name: "Tạo đơn mới" })).toBeNull();
    await userEvent.click(within(dialog).getByRole("button", { name: "Giữ đơn" }));
    expect(await screen.findByText("Đang kiểm tra")).toBeInTheDocument();
    expect(screen.queryByRole("link", { name: "Tạo đơn mới" })).toBeNull();
  });

  describe("R1-45, R1-57: useCardMode giao dịch và dữ liệu cũ", () => {
    afterEach(() => {
      vi.unstubAllGlobals();
      vi.restoreAllMocks();
    });

    it("hẹp dùng CardList, rộng dùng bảng duy nhất, chuẩn hóa '—' cho bank trống và giữ đơn khi refetch lỗi (R1-12, R1-42, R1-45, R1-57, R1-61)", async () => {
      const setWidth = installContainerWidth(900);
      const initial = makeDetail({
        payments: [
          {
            id: "pay-nobank",
            attemptSeq: 1,
            txnRef: "TX_NOBANK",
            amountVnd: 310000,
            bankCode: undefined,
            cardType: undefined,
            responseCode: undefined,
            state: "CREATED",
            duplicate: true,
            createdAt: "2026-11-17T03:12:00Z",
          },
        ],
      });
      const loadDetail = vi.fn().mockResolvedValueOnce(initial).mockRejectedValueOnce(new Error("network blip"));
      const payOrder = vi.fn().mockRejectedValue(new ApiStatusError(500, "Upstream error"));

      renderWithProviders(
        <OrderDetailView
          code="DH261117K7M2Q9"
          loadCurrentSession={async () => STUDENT_SESSION}
          loadOrderDetail={loadDetail}
          payOrder={payOrder}
        />,
      );

      const table = await screen.findByRole("table");
      expect(screen.queryByRole("list", { name: "Danh sách giao dịch thanh toán" })).toBeNull();
      const bodyRow = within(table).getAllByRole("row")[1]!;
      expect(within(bodyRow).getAllByRole("cell")[2]).toHaveTextContent("—");

      setWidth(PHONE_CONTENT_WIDTH);
      const list = await screen.findByRole("list", { name: "Danh sách giao dịch thanh toán" });
      expect(screen.queryByRole("table")).toBeNull();
      expect(within(list).getByText(/TX_NOBANK/)).toBeInTheDocument();
      expect(within(list).getByText("Thu trùng")).toBeInTheDocument();
      expect(within(list).getByText("—")).toBeInTheDocument();

      setWidth(900);
      expect(await screen.findByRole("table")).toBeInTheDocument();

      // Pay 500 -> refetch đơn bị lỗi -> vẫn giữ đơn và hiện banner stale, chỉ có 1 h1 (R1-42, R1-61)
      await userEvent.click(screen.getByRole("button", { name: "Tiếp tục thanh toán" }));
      await waitFor(() => expect(payOrder).toHaveBeenCalledTimes(1));
      expect(screen.getAllByRole("heading", { level: 1 })).toHaveLength(1);
      expect(screen.queryByRole("heading", { name: "Không tải được chi tiết đơn hàng" })).toBeNull();
      const alerts = screen.getAllByRole("alert");
      expect(alerts.some((el) => el.textContent?.includes("Dữ liệu chưa được cập nhật."))).toBe(true);
    });
  });

  it("initial 500 dùng detail.errorTitle (h1), session lỗi dùng detail.errorTitle (h1), hỗ trợ payments rỗng VNPAY/FREE và đơn 0đ không voucher (R1-8, R1-12, R1-15)", async () => {
    const first = renderWithProviders(
      <OrderDetailView
        code="DH-ERR"
        loadCurrentSession={async () => STUDENT_SESSION}
        loadOrderDetail={() => Promise.reject(new ApiStatusError(500, "Server down"))}
      />,
    );
    expect(await screen.findByRole("heading", { level: 1, name: "Không tải được chi tiết đơn hàng" })).toBeInTheDocument();
    first.unmount();

    const sessErr = renderWithProviders(
      <OrderDetailView
        code="DH-SESS"
        loadCurrentSession={() => Promise.reject(new Error("session err"))}
        loadOrderDetail={vi.fn()}
      />,
    );
    expect(await screen.findByRole("heading", { level: 1, name: "Không tải được chi tiết đơn hàng" })).toBeInTheDocument();
    sessErr.unmount();

    // VNPAY với payments rỗng
    const vnpEmpty = renderWithProviders(
      <OrderDetailView
        code="DH-VNPEMPTY"
        loadCurrentSession={async () => STUDENT_SESSION}
        loadOrderDetail={async () => makeDetail({ code: "DH-VNPEMPTY", paymentMethod: "VNPAY", payments: [] })}
      />,
    );
    expect(await screen.findByText("Chưa có giao dịch thanh toán.")).toBeInTheDocument();
    vnpEmpty.unmount();

    // Đơn 0đ không voucher + FREE payments rỗng
    renderWithProviders(
      <OrderDetailView
        code="DH-FREE0"
        loadCurrentSession={async () => STUDENT_SESSION}
        loadOrderDetail={async () =>
          makeDetail({
            code: "DH-FREE0",
            paymentMethod: "FREE",
            amountDueVnd: 0,
            voucherCode: undefined,
            voucherDiscountVnd: 0,
            discountApplied: "PROMOTION_ONLY",
            state: "FULFILLED",
            payments: [],
          })
        }
      />,
    );
    expect(await screen.findByRole("heading", { level: 1, name: "Đơn DH-FREE0" })).toBeInTheDocument();
    expect(screen.getByText("Đơn miễn phí, không phát sinh giao dịch VNPAY.")).toBeInTheDocument();
    expect(screen.getByText("Miễn phí")).toBeInTheDocument();
    expect(screen.queryByText(/Miễn phí nhờ voucher/)).toBeNull();
  });

  it("khóa chéo Pay/Cancel, giữ Idempotency-Key qua 500/429, đổi key sau 409, invalidate list khi hủy, focus h1 và xóa actionError khi đổi code (R1-2, R1-7, R1-13, R1-14, R1-40, R1-41, R1-43, R1-67)", async () => {
    expect(MY_ORDER_DETAIL_QUERY_KEY("DH-1", "stu-a")).not.toEqual(MY_ORDER_DETAIL_QUERY_KEY("DH-1", "stu-b"));

    let resolvePay!: (v: { next: string; order: OrderDetail; paymentUrl: string; txnRef: string }) => void;
    const payOrder = vi
      .fn()
      .mockImplementationOnce(
        () =>
          new Promise<{ next: string; order: OrderDetail; paymentUrl: string; txnRef: string }>((resolve) => {
            resolvePay = resolve;
          }),
      )
      .mockRejectedValueOnce(new ApiStatusError(500, "Temp 500"))
      .mockRejectedValueOnce(new ApiStatusError(429, "Rate limited", { code: "RATE_LIMITED" }))
      .mockRejectedValueOnce(new ApiStatusError(409, "Expired", { code: "ORDER_EXPIRED" }))
      .mockResolvedValueOnce({
        next: "REDIRECT",
        paymentUrl: "https://sandbox.vnpayment.vn/next",
        txnRef: "TX-NEXT",
        order: makeDetail({ code: "DH-ACT" }),
      });

    const cancelledOrder = makeDetail({ code: "DH-ACT", state: "CANCELLED", updatedAt: "2026-11-17T03:15:00Z" });
    const cancelOrder = vi.fn().mockResolvedValue(cancelledOrder);
    const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
    const invalidateSpy = vi.spyOn(client, "invalidateQueries");
    const navigate = vi.fn();

    const { rerender } = renderWithProviders(
      <OrderDetailView
        code="DH-ACT"
        loadCurrentSession={async () => STUDENT_SESSION}
        loadOrderDetail={async (c) => makeDetail({ code: c, state: "PENDING_PAYMENT" })}
        payOrder={payOrder}
        cancelOrder={cancelOrder}
        navigate={navigate}
      />,
      { client },
    );

    const payBtn = await screen.findByRole("button", { name: "Tiếp tục thanh toán" });
    const cancelBtn = screen.getByRole("button", { name: "Hủy đơn" });

    // Đang gửi Pay -> khóa cả Pay lần 2 và nút Cancel
    await userEvent.click(payBtn);
    expect(cancelBtn).toBeDisabled();
    await userEvent.click(payBtn);
    expect(payOrder).toHaveBeenCalledTimes(1);
    resolvePay({
      next: "REDIRECT",
      paymentUrl: "https://sandbox.vnpayment.vn/p1",
      txnRef: "TX-P1",
      order: makeDetail({ code: "DH-ACT" }),
    });
    await waitFor(() => expect(navigate).toHaveBeenCalledWith("https://sandbox.vnpayment.vn/p1"));

    // 500 -> giữ key, 429 RATE_LIMITED -> giữ cùng key và hiện copy 429
    await userEvent.click(payBtn);
    await waitFor(() => expect(payOrder).toHaveBeenCalledTimes(2));
    const retryKey = payOrder.mock.calls[1]?.[1];

    await userEvent.click(payBtn);
    await waitFor(() => expect(payOrder).toHaveBeenCalledTimes(3));
    expect(payOrder.mock.calls[2]?.[1]).toBe(retryKey);
    expect(await screen.findByRole("alert")).toHaveTextContent(
      "Bạn thao tác quá nhanh. Vui lòng chờ một lát rồi thử lại.",
    );

    // 409 -> dùng retryKey, rồi reset key sang UUID mới ở lần gọi sau
    await userEvent.click(payBtn);
    await waitFor(() => expect(payOrder).toHaveBeenCalledTimes(4));
    expect(payOrder.mock.calls[3]?.[1]).toBe(retryKey);

    await userEvent.click(payBtn);
    await waitFor(() => expect(payOrder).toHaveBeenCalledTimes(5));
    expect(payOrder.mock.calls[4]?.[1]).not.toBe(retryKey);

    // Gây lại lỗi 429 rồi đổi prop code -> actionError biến mất (R1-41)
    payOrder.mockRejectedValueOnce(new ApiStatusError(429, "Fast", { code: "RATE_LIMITED" }));
    await userEvent.click(payBtn);
    expect(await screen.findByRole("alert")).toHaveTextContent(/quá nhanh/);

    rerender(
      <OrderDetailView
        code="DH-ACT-2"
        loadCurrentSession={async () => STUDENT_SESSION}
        loadOrderDetail={async (c) => makeDetail({ code: c, state: "PENDING_PAYMENT" })}
        payOrder={payOrder}
        cancelOrder={cancelOrder}
        navigate={navigate}
      />,
    );
    await waitFor(() => expect(screen.getByRole("heading", { level: 1, name: "Đơn DH-ACT-2" })).toBeInTheDocument());
    expect(screen.queryByRole("alert")).toBeNull();

    // Hủy đơn -> invalidate ["me", "orders"] và đưa focus về <h1> (R1-43, R1-67)
    await userEvent.click(screen.getByRole("button", { name: "Hủy đơn" }));
    const dialog = screen.getByRole("dialog");
    await userEvent.click(within(dialog).getByRole("button", { name: "Hủy đơn" }));
    await waitFor(() => expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: ["me", "orders"] }));
    const heading = screen.getByRole("heading", { level: 1, name: "Đơn DH-ACT-2" });
    await waitFor(() => expect(document.activeElement).toBe(heading));
  });

  it("bản địa hóa toàn bộ actor/reason lịch sử đơn hàng ở cả vi và en và formatDateTime lúc 00:xx (R1-15, R1-16, R1-58)", async () => {
    expect(formatDateTime("2026-11-20T17:05:00Z")).toBe("21/11/2026 00:05");

    const fullHistory: OrderDetail["history"] = [
      { id: "h1", fromState: undefined, toState: "PENDING_PAYMENT", actor: "LEARNER", reason: "ORDER_CREATED", at: "2026-11-17T03:00:00Z" },
      { id: "h2", fromState: undefined, toState: "PAID", actor: "SYSTEM", reason: "FREE_ORDER_PAID", at: "2026-11-17T03:01:00Z" },
      { id: "h3", fromState: "PENDING_PAYMENT", toState: "PENDING_PAYMENT", actor: "LEARNER", reason: "PAYMENT_ATTEMPT_CREATED:TX1", at: "2026-11-17T03:02:00Z" },
      { id: "h4", fromState: "PENDING_PAYMENT", toState: "PENDING_PAYMENT", actor: "LEARNER", reason: "REUSE_PENDING_PAYMENT:TX2", at: "2026-11-17T03:03:00Z" },
      { id: "h5", fromState: "PENDING_PAYMENT", toState: "REPLACED", actor: "LEARNER", reason: "REPLACED_BY:DH2", at: "2026-11-17T03:04:00Z" },
      { id: "h6", fromState: "PENDING_PAYMENT", toState: "CANCELLED", actor: "LEARNER", reason: "CANCELLED_BY_USER", at: "2026-11-17T03:05:00Z" },
      { id: "h7", fromState: "PENDING_PAYMENT", toState: "PAID", actor: "VNPAY_IPN", reason: "PAYMENT_SUCCEEDED:TX3", at: "2026-11-17T03:06:00Z" },
      { id: "h8", fromState: "PAID", toState: "FULFILLED", actor: "SYSTEM", reason: "FULFILLED", at: "2026-11-17T03:07:00Z" },
      { id: "h9", fromState: "FULFILLED", toState: "FULFILLED", actor: "VNPAY_IPN", reason: "EXCESS_AFTER_COUNTED:TX4", at: "2026-11-17T03:08:00Z" },
      { id: "h10", fromState: "FULFILLED", toState: "FULFILLED", actor: "SYSTEM", reason: "AUTO_EXCESS_AFTER_COUNTED:TX5", at: "2026-11-17T03:09:00Z" },
      { id: "h11", fromState: "EXPIRED", toState: "EXPIRED", actor: "VNPAY_IPN", reason: "LATE_SUCCESS_PENDING_REVIEW:TX6", at: "2026-11-17T03:10:00Z" },
      { id: "h12", fromState: "FULFILLED", toState: "FULFILLED", actor: "ADMIN", reason: "DOUBLE_CHARGE_REVIEW:TX7", at: "2026-11-17T03:11:00Z" },
      { id: "h13", fromState: "REPLACED", toState: "REPLACED", actor: "SYSTEM", reason: "DOUBLE_CHARGE_REPLACED_ORDER:TX8", at: "2026-11-17T03:12:00Z" },
      { id: "h14", fromState: "EXPIRED", toState: "FULFILLED", actor: "ADMIN", reason: "LATE_PAYMENT_RECLAIMED:TX9", at: "2026-11-17T03:13:00Z" },
      { id: "h15", fromState: "REPLACED", toState: "FULFILLED", actor: "SYSTEM", reason: "LATE_PAYMENT_REPLACED_RECLAIMED:TX10", at: "2026-11-17T03:14:00Z" },
      { id: "h16", fromState: "REPLACED", toState: "REPLACED", actor: "VNPAY_IPN", reason: "LATE_PAYMENT_REPLACED_DOUBLE_CHARGE:TX11", at: "2026-11-17T03:15:00Z" },
      { id: "h17", fromState: "PENDING_PAYMENT", toState: "EXPIRED", actor: "SYSTEM", reason: "EXPIRED", at: "2026-11-17T03:16:00Z" },
      { id: "h18", fromState: "FULFILLED", toState: "REFUNDED", actor: "ADMIN", reason: "REFUNDED", at: "2026-11-17T03:17:00Z" },
      { id: "h19", fromState: "PAID", toState: "FULFILLED", actor: "CRON", reason: "INTERNAL_RAW_CODE", at: "2026-11-17T03:18:00Z" },
    ];

    const forbiddenRawTokens = [
      "ORDER_CREATED",
      "FREE_ORDER_PAID",
      "PAYMENT_ATTEMPT_CREATED",
      "REUSE_PENDING_PAYMENT",
      "REPLACED_BY",
      "CANCELLED_BY_USER",
      "PAYMENT_SUCCEEDED",
      "EXCESS_AFTER_COUNTED",
      "AUTO_EXCESS_AFTER_COUNTED",
      "LATE_SUCCESS_PENDING_REVIEW",
      "DOUBLE_CHARGE_REVIEW",
      "DOUBLE_CHARGE_REPLACED_ORDER",
      "LATE_PAYMENT_RECLAIMED",
      "LATE_PAYMENT_REPLACED_RECLAIMED",
      "LATE_PAYMENT_REPLACED_DOUBLE_CHARGE",
      "VNPAY_IPN",
      "INTERNAL_RAW_CODE",
      "FULFILLED",
      "PENDING_PAYMENT",
    ];

    const viView = renderWithProviders(
      <OrderDetailView
        code="DH-HIST"
        loadCurrentSession={async () => STUDENT_SESSION}
        loadOrderDetail={async () => makeDetail({ code: "DH-HIST", history: fullHistory })}
      />,
    );
    expect(await screen.findByText("Khởi tạo đơn hàng chờ thanh toán")).toBeInTheDocument();
    expect(screen.getByText(/Đã thay thế bằng đơn DH2/)).toBeInTheDocument();
    expect(screen.getAllByText(/Cổng thanh toán VNPAY/).length).toBeGreaterThan(0);
    expect(screen.getByText("Cập nhật trạng thái đơn hàng")).toBeInTheDocument();
    for (const token of forbiddenRawTokens) {
      expect(document.body.textContent).not.toContain(token);
    }
    viView.unmount();

    renderWithProviders(
      <OrderDetailView
        code="DH-HIST"
        loadCurrentSession={async () => STUDENT_SESSION}
        loadOrderDetail={async () => makeDetail({ code: "DH-HIST", history: fullHistory })}
      />,
      { locale: "en", messages: enMessages },
    );
    expect(await screen.findByText("Created order awaiting payment")).toBeInTheDocument();
    expect(screen.getAllByText(/VNPAY gateway/).length).toBeGreaterThan(0);
    for (const token of forbiddenRawTokens) {
      expect(document.body.textContent).not.toContain(token);
    }
  });

  it("refetch trả 404 ORDER_NOT_FOUND ẩn đơn cũ và banner stale (R2-8)", async () => {
    const pending = makeDetail({ code: "DH-REFETCH-404" });
    const loadDetail = vi
      .fn()
      .mockResolvedValueOnce(pending)
      .mockRejectedValueOnce(new ApiStatusError(404, "Not found", { code: "ORDER_NOT_FOUND" }));
    const payOrder = vi.fn().mockRejectedValueOnce(new ApiStatusError(500, "Temp err"));

    renderWithProviders(
      <OrderDetailView
        code="DH-REFETCH-404"
        loadCurrentSession={async () => STUDENT_SESSION}
        loadOrderDetail={loadDetail}
        payOrder={payOrder}
      />,
    );

    expect(await screen.findByRole("heading", { level: 1, name: "Đơn DH-REFETCH-404" })).toBeInTheDocument();
    await userEvent.click(screen.getByRole("button", { name: "Tiếp tục thanh toán" }));

    expect(await screen.findByRole("heading", { level: 1, name: "Không tìm thấy đơn hàng" })).toBeInTheDocument();
    expect(screen.queryByText("Đề thi thử ĐGNL HSA số 3")).toBeNull();
    expect(screen.queryByText(/Dữ liệu chưa được cập nhật/)).toBeNull();
  });

  it("cancelOrder giữ Idempotency-Key qua 500 kể cả khi đóng mở lại dialog, và tạo key mới sau 409 (R2-19)", async () => {
    const order = makeDetail({ code: "DH-CAN-IDEM", state: "PENDING_PAYMENT" });
    const cancelOrder = vi
      .fn()
      .mockRejectedValueOnce(new ApiStatusError(500, "Server 500"))
      .mockRejectedValueOnce(new ApiStatusError(500, "Server 500 again"))
      .mockRejectedValueOnce(new ApiStatusError(409, "Not pending", { code: "ORDER_NOT_PENDING" }))
      .mockResolvedValueOnce(makeDetail({ code: "DH-CAN-IDEM", state: "CANCELLED" }));

    renderWithProviders(
      <OrderDetailView
        code="DH-CAN-IDEM"
        loadCurrentSession={async () => STUDENT_SESSION}
        loadOrderDetail={async () => order}
        cancelOrder={cancelOrder}
      />,
    );

    await userEvent.click(await screen.findByRole("button", { name: "Hủy đơn" }));
    await userEvent.click(within(screen.getByRole("dialog")).getByRole("button", { name: "Hủy đơn" }));
    await waitFor(() => expect(cancelOrder).toHaveBeenCalledTimes(1));
    const key1 = cancelOrder.mock.calls[0]?.[1];

    // Thử lại ngay trong hộp thoại -> dùng lại key1
    await userEvent.click(within(screen.getByRole("dialog")).getByRole("button", { name: "Hủy đơn" }));
    await waitFor(() => expect(cancelOrder).toHaveBeenCalledTimes(2));
    expect(cancelOrder.mock.calls[1]?.[1]).toBe(key1);

    // Đóng hộp thoại khi đang có lỗi rồi mở lại -> vẫn dùng lại key1, lần 3 trả 409 -> xóa key
    await userEvent.click(within(screen.getByRole("dialog")).getByRole("button", { name: "Giữ đơn" }));
    await userEvent.click(screen.getByRole("button", { name: "Hủy đơn" }));
    await userEvent.click(within(screen.getByRole("dialog")).getByRole("button", { name: "Hủy đơn" }));
    await waitFor(() => expect(cancelOrder).toHaveBeenCalledTimes(3));
    expect(cancelOrder.mock.calls[2]?.[1]).toBe(key1);

    // Sau 409 -> lần gọi tiếp theo dùng key mới
    await userEvent.click(within(screen.getByRole("dialog")).getByRole("button", { name: "Hủy đơn" }));
    await waitFor(() => expect(cancelOrder).toHaveBeenCalledTimes(4));
    expect(cancelOrder.mock.calls[3]?.[1]).not.toBe(key1);
  });
});
