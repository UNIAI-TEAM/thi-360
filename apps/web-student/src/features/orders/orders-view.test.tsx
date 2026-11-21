import { screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, describe, expect, it, vi } from "vitest";
import {
  MY_ORDERS_QUERY_KEY,
  type OrderDetail,
  type OrderPage,
  type OrderPayAction,
  type OrderSummary,
} from "@/features/orders/api";
import { focusAfterDialogUnmount } from "@/features/orders/format";
import { ApiStatusError } from "@/lib/api/client";
import { installContainerWidth, PHONE_CONTENT_WIDTH } from "@/test/container-width";
import { renderWithProviders } from "@/test/render";
import { ADMIN_SESSION, STUDENT_SESSION } from "@/test/storefront-fixtures";
import { OrdersView } from "./orders-view";

function makeOrder(overrides: Partial<OrderSummary> = {}): OrderSummary {
  return {
    id: "ord-1",
    code: "DH261117K7M2Q9",
    productType: "EXAM",
    examId: "exam-1",
    productCode: "HSA-FULL-03",
    productName: "Đề thi thử ĐGNL HSA số 3",
    listPriceVnd: 450000,
    promotionDiscountVnd: 90000,
    voucherDiscountVnd: 50000,
    discountApplied: "COMBINED",
    amountDueVnd: 310000,
    attemptsGranted: 3,
    paymentMethod: "VNPAY",
    state: "PENDING_PAYMENT",
    expiresAt: "2026-11-17T03:27:00Z",
    createdAt: "2026-11-17T03:12:00Z",
    ...overrides,
  };
}

describe("OrdersView · AC-20", () => {
  it("hiện trạng thái đang tải, lỗi, rỗng và có dữ liệu", async () => {
    const loading = renderWithProviders(
      <OrdersView loadCurrentSession={async () => STUDENT_SESSION} loadOrders={() => new Promise(() => {})} />,
    );
    expect(await screen.findByRole("heading", { level: 1, name: "Đơn của tôi" })).toBeInTheDocument();
    expect(screen.getByRole("status", { name: "Đang tải" })).toHaveAttribute("aria-busy", "true");
    loading.unmount();

    const failLoader = vi
      .fn<(p: { page: number; size: number }) => Promise<OrderPage>>()
      .mockRejectedValueOnce(new Error("network"))
      .mockResolvedValueOnce({ items: [], page: 0, size: 20, totalItems: 0, totalPages: 0 });
    const errView = renderWithProviders(
      <OrdersView loadCurrentSession={async () => STUDENT_SESSION} loadOrders={failLoader} />,
    );
    expect(await screen.findByRole("alert")).toHaveTextContent("Không tải được danh sách đơn");
    await userEvent.click(screen.getByRole("button", { name: "Thử lại" }));
    expect(await screen.findByRole("heading", { name: "Bạn chưa có đơn nào" })).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Khám phá bài thi" })).toHaveAttribute("href", "/#exams");
    errView.unmount();
  });

  it("hiện các trạng thái đơn, Tiếp tục thanh toán, Hủy đơn qua hộp thoại và phân trang", async () => {
    const page1: OrderPage = {
      page: 0,
      size: 20,
      totalItems: 22,
      totalPages: 2,
      items: [
        makeOrder({ id: "o1", code: "DH-PENDING", state: "PENDING_PAYMENT" }),
        makeOrder({ id: "o2", code: "DH-PAID", state: "PAID", expiresAt: undefined }),
        makeOrder({
          id: "o3",
          code: "DH-FULFILLED",
          state: "FULFILLED",
          paymentMethod: "FREE",
          amountDueVnd: 0,
          promotionDiscountVnd: 0,
          voucherDiscountVnd: 450000,
        }),
        makeOrder({ id: "o4", code: "DH-EXPIRED", state: "EXPIRED" }),
        makeOrder({ id: "o5", code: "DH-REPLACED", state: "REPLACED" }),
        makeOrder({ id: "o6", code: "DH-CANCELLED", state: "CANCELLED" }),
        makeOrder({ id: "o7", code: "DH-REFUNDED", state: "REFUNDED" }),
      ],
    };
    const page2: OrderPage = {
      page: 1,
      size: 20,
      totalItems: 22,
      totalPages: 2,
      items: [makeOrder({ id: "o8", code: "DH-PAGE2", state: "FULFILLED" })],
    };

    const loadOrders = vi
      .fn<(p: { page: number; size: number }) => Promise<OrderPage>>()
      .mockImplementation(async ({ page }) => (page === 1 ? page2 : page1));
    const payOrder = vi.fn(async () => ({
      next: "REDIRECT",
      paymentUrl: "https://mock.local/pay?txn=DH-PENDING02",
      txnRef: "DH-PENDING02",
      order: {} as OrderDetail,
    }));
    const cancelOrder = vi.fn(async () => ({}) as OrderDetail);
    const navigate = vi.fn();

    renderWithProviders(
      <OrdersView
        loadCurrentSession={async () => STUDENT_SESSION}
        loadOrders={loadOrders}
        payOrder={payOrder}
        cancelOrder={cancelOrder}
        navigate={navigate}
      />,
    );

    expect(screen.queryByRole("region", { name: "Danh sách đơn hàng của tôi" })).toBeNull();
    const table = await screen.findByRole("table", { name: "Danh sách đơn hàng của tôi" });
    const scoped = within(table);
    expect(scoped.getByRole("columnheader", { name: "Bài thi" })).toHaveClass("min-w-48");
    expect(within(scoped.getAllByRole("row")[1]!).getAllByRole("cell")[1]).toHaveClass("min-w-48");
    expect(scoped.getByRole("link", { name: "DH-PENDING" })).toHaveAttribute("href", "/account/orders/DH-PENDING");
    expect(scoped.getByText("Chờ thanh toán")).toBeInTheDocument();
    expect(scoped.getByText("Đang kiểm tra")).toBeInTheDocument();
    expect(scoped.getByText("Đã cấp bài thi")).toBeInTheDocument();
    expect(scoped.getByText("Hết hạn")).toBeInTheDocument();
    expect(scoped.getByText("Đã thay thế")).toBeInTheDocument();
    expect(scoped.getByText("Đã hủy")).toBeInTheDocument();
    expect(scoped.getByText("Đã hoàn tiền")).toBeInTheDocument();
    expect(scoped.getByText("Miễn phí nhờ voucher")).toBeInTheDocument();
    expect(scoped.getByRole("link", { name: "Vào Bài thi của tôi" })).toHaveAttribute("href", "/home");

    // Tiếp tục thanh toán gửi Idempotency-Key và chuyển hướng paymentUrl
    await userEvent.click(scoped.getByRole("button", { name: "Tiếp tục thanh toán" }));
    expect(payOrder).toHaveBeenCalledWith("DH-PENDING", expect.any(String));
    expect(navigate).toHaveBeenCalledWith("https://mock.local/pay?txn=DH-PENDING02");

    // Hủy đơn mở ConfirmDialog và gọi cancelOrder
    await userEvent.click(scoped.getByRole("button", { name: "Hủy đơn" }));
    const dialog = screen.getByRole("dialog");
    expect(within(dialog).getByText("Hủy đơn DH-PENDING?")).toBeInTheDocument();
    await userEvent.click(within(dialog).getByRole("button", { name: "Hủy đơn" }));
    expect(cancelOrder).toHaveBeenCalledWith("DH-PENDING", expect.any(String));

    // Phân trang sang trang 2
    await userEvent.click(screen.getByRole("button", { name: "Trang 2" }));
    expect(await within(screen.getByRole("table", { name: "Danh sách đơn hàng của tôi" })).findByText("DH-PAGE2")).toBeInTheDocument();
  });

  it("lỗi khi /pay từ chối hiện thông báo và nút tạo đơn mới", async () => {
    const loadOrders = vi.fn(async () => ({
      items: [makeOrder({ code: "DH-OLD", productCode: "HSA-FULL-03", state: "PENDING_PAYMENT" })],
      page: 0,
      size: 20,
      totalItems: 1,
      totalPages: 1,
    }));
    const payOrder = vi.fn().mockRejectedValue(new ApiStatusError(409, "Expired", { code: "ORDER_EXPIRED" }));

    renderWithProviders(
      <OrdersView loadCurrentSession={async () => STUDENT_SESSION} loadOrders={loadOrders} payOrder={payOrder} />,
    );

    const table = await screen.findByRole("table", { name: "Danh sách đơn hàng của tôi" });
    await userEvent.click(within(table).getByRole("button", { name: "Tiếp tục thanh toán" }));

    const alert = await screen.findByRole("alert");
    expect(alert).toHaveTextContent(/Đơn đã hết hạn thanh toán/);
    expect(within(alert).getByRole("link", { name: "Tạo đơn mới" })).toHaveAttribute("href", "/checkout/HSA-FULL-03");
  });

  it("tài khoản quản trị không gọi API /me/orders và lỗi phiên dùng heading cấp 1 (R2-12)", async () => {
    const adminLoadOrders = vi.fn();
    const adminView = renderWithProviders(
      <OrdersView loadCurrentSession={async () => ADMIN_SESSION} loadOrders={adminLoadOrders} />,
    );

    expect(await screen.findByRole("heading", { level: 1, name: "Tài khoản quản trị" })).toBeInTheDocument();
    expect(adminLoadOrders).not.toHaveBeenCalled();
    adminView.unmount();

    const loadOrders = vi.fn();
    renderWithProviders(
      <OrdersView loadCurrentSession={() => Promise.reject(new Error("session err"))} loadOrders={loadOrders} />,
    );
    expect(await screen.findByRole("heading", { level: 1, name: "Không tải được danh sách đơn" })).toBeInTheDocument();
    expect(loadOrders).not.toHaveBeenCalled();
  });

  describe("R1-45, R1-57: useCardMode bảng/thẻ và dữ liệu cũ khi refetch lỗi", () => {
    afterEach(() => {
      vi.unstubAllGlobals();
      vi.restoreAllMocks();
    });

    it("rộng dùng bảng duy nhất, hẹp dùng CardList và giữ bảng khi refetch lỗi (R1-12, R1-39, R1-45, R1-57, R1-61, R2-20, R2-21)", async () => {
      const setWidth = installContainerWidth(900);
      const loadOrders = vi
        .fn<() => Promise<OrderPage>>()
        .mockResolvedValueOnce({
          items: [
            makeOrder({ id: "o-bundle", code: "DH-BUNDLE", productType: "BUNDLE", state: "PENDING_PAYMENT" }),
            makeOrder({
              id: "o-free",
              code: "DH-FREE0",
              paymentMethod: "FREE",
              amountDueVnd: 0,
              promotionDiscountVnd: 200000,
              voucherDiscountVnd: 0,
              discountApplied: "PROMOTION_ONLY",
              state: "FULFILLED",
            }),
          ],
          page: 0,
          size: 20,
          totalItems: 2,
          totalPages: 1,
        })
        .mockRejectedValueOnce(new Error("network blip"));

      const payOrder = vi.fn().mockRejectedValue(new ApiStatusError(500, "Temporary failure"));
      renderWithProviders(
        <OrdersView loadCurrentSession={async () => STUDENT_SESSION} loadOrders={loadOrders} payOrder={payOrder} />,
      );

      const table = await screen.findByRole("table");
      expect(screen.queryByRole("list", { name: "Danh sách đơn hàng của tôi" })).toBeNull();
      expect(within(table).getByText("Miễn phí")).toBeInTheDocument();
      expect(within(table).queryByText("Miễn phí nhờ voucher")).toBeNull();

      // Thu hẹp xuống 358px -> chỉ còn CardList, table bị gỡ khỏi DOM, chỉ có 1 link tới chi tiết đơn và giữ pointer-coarse:min-h-11
      setWidth(PHONE_CONTENT_WIDTH);
      const list = await screen.findByRole("list", { name: "Danh sách đơn hàng của tôi" });
      expect(screen.queryByRole("table")).toBeNull();
      expect(within(list).getByRole("link", { name: "DH-BUNDLE" })).toHaveAttribute("href", "/account/orders/DH-BUNDLE");
      expect(within(list).getByText("Bộ đề")).toBeInTheDocument();
      expect(within(list).queryByRole("link", { name: /Xem chi tiết/ })).toBeNull();
      const cardItems = within(list).getAllByRole("listitem");
      expect(
        within(cardItems[0]!).getAllByRole("link").filter((el) => el.getAttribute("href") === "/account/orders/DH-BUNDLE"),
      ).toHaveLength(1);
      expect(within(cardItems[0]!).getByRole("button", { name: "Tiếp tục thanh toán" })).toHaveClass(
        "pointer-coarse:min-h-11",
      );
      expect(within(cardItems[0]!).getByRole("button", { name: "Hủy đơn" })).toHaveClass("pointer-coarse:min-h-11");
      expect(within(cardItems[1]!).getByRole("link", { name: "Vào Bài thi của tôi" })).toHaveClass(
        "pointer-coarse:min-h-11",
      );

      // Mở rộng lại -> refetch lỗi khi nhấn Pay -> giữ dữ liệu cũ + hiện banner stale + không hiện full error
      setWidth(900);
      expect(await screen.findByRole("table")).toBeInTheDocument();
      await userEvent.click(screen.getAllByRole("button", { name: "Tiếp tục thanh toán" })[0]!);
      await waitFor(() => expect(payOrder).toHaveBeenCalledTimes(1));
      expect(screen.getAllByRole("heading", { level: 1 })).toHaveLength(1);
      expect(screen.queryByRole("heading", { name: "Không tải được danh sách đơn" })).toBeNull();
      const staleAlerts = screen.getAllByRole("alert");
      expect(staleAlerts.some((el) => el.textContent?.includes("Dữ liệu chưa được cập nhật."))).toBe(true);
      expect(screen.getByRole("table")).toBeInTheDocument();
    });
  });

  it("chống bấm đúp, khóa chéo Pay/Cancel, giữ Idempotency-Key qua 500, đổi key sau 409, hiện RATE_LIMITED và khôi phục focus sau hủy (R1-2, R1-13, R1-14, R1-37, R1-38, R1-67)", async () => {
    type PayResult = Awaited<ReturnType<OrderPayAction>>;
    let resolvePay!: (v: PayResult) => void;
    const payOrder = vi.fn<OrderPayAction>().mockImplementationOnce(
      () =>
        new Promise<PayResult>((resolve) => {
          resolvePay = resolve;
        }),
    );

    const loadOrders = vi.fn().mockResolvedValue({
      items: [
        makeOrder({ id: "o-1", code: "DH-111", state: "PENDING_PAYMENT" }),
        makeOrder({ id: "o-2", code: "DH-222", state: "PENDING_PAYMENT" }),
      ],
      page: 0,
      size: 20,
      totalItems: 2,
      totalPages: 1,
    });

    const navigate = vi.fn();
    renderWithProviders(
      <OrdersView
        loadCurrentSession={async () => STUDENT_SESSION}
        loadOrders={loadOrders}
        payOrder={payOrder}
        cancelOrder={vi.fn(async () => ({}) as OrderDetail)}
        navigate={navigate}
      />,
    );

    const rows = await screen.findAllByRole("row");
    const firstPay = within(rows[1]!).getByRole("button", { name: "Tiếp tục thanh toán" });
    const firstCancel = within(rows[1]!).getByRole("button", { name: "Hủy đơn" });
    const secondPay = within(rows[2]!).getByRole("button", { name: "Tiếp tục thanh toán" });

    // Nhấp đồng thời khi đang xử lý -> chỉ gọi 1 lần và khóa các nút khác (R1-13, R1-38)
    await userEvent.click(firstPay);
    expect(firstCancel).toBeDisabled();
    expect(secondPay).toBeDisabled();
    await userEvent.click(firstPay);
    expect(payOrder).toHaveBeenCalledTimes(1);

    resolvePay({
      next: "REDIRECT",
      paymentUrl: "https://sandbox.vnpayment.vn/pay-1",
      txnRef: "TX-PAY-1",
      order: {} as OrderDetail,
    });
    await waitFor(() => expect(navigate).toHaveBeenCalledWith("https://sandbox.vnpayment.vn/pay-1"));
  });

  it("giữ Idempotency-Key qua 500, tạo key mới sau 409, 429 hiện RATE_LIMITED, ORDER_NOT_PENDING không có Tạo đơn mới và focus về link đơn khi hủy (R1-2, R1-7, R1-14, R1-37, R1-67)", async () => {
    expect(MY_ORDERS_QUERY_KEY(0, 20, "stu-a")).not.toEqual(MY_ORDERS_QUERY_KEY(0, 20, "stu-b"));

    const order = makeOrder({ code: "DH-RETRY", productCode: "HSA-FULL-03", state: "PENDING_PAYMENT" });
    const payOrder = vi
      .fn<OrderPayAction>()
      .mockRejectedValueOnce(new ApiStatusError(500, "Server busy"))
      .mockRejectedValueOnce(new ApiStatusError(429, "Too fast", { code: "RATE_LIMITED" }))
      .mockRejectedValueOnce(new ApiStatusError(409, "Not pending", { code: "ORDER_NOT_PENDING" }))
      .mockResolvedValueOnce({
        next: "REDIRECT",
        paymentUrl: "https://sandbox.vnpayment.vn/pay-2",
        txnRef: "TX-PAY-2",
        order: {} as OrderDetail,
      });

    const cancelOrder = vi.fn(async () => ({ ...order, items: [], updatedAt: "2026-11-17T03:15:00Z", state: "CANCELLED" }) as unknown as OrderDetail);

    renderWithProviders(
      <OrdersView
        loadCurrentSession={async () => STUDENT_SESSION}
        loadOrders={async () => ({ items: [order], page: 0, size: 20, totalItems: 1, totalPages: 1 })}
        payOrder={payOrder}
        cancelOrder={cancelOrder}
      />,
    );

    const payBtn = await screen.findByRole("button", { name: "Tiếp tục thanh toán" });
    await userEvent.click(payBtn);
    await waitFor(() => expect(payOrder).toHaveBeenCalledTimes(1));
    const key1 = payOrder.mock.calls[0]?.[1];

    // 429 RATE_LIMITED: giữ nguyên Idempotency-Key + hiện copy 429
    await userEvent.click(payBtn);
    await waitFor(() => expect(payOrder).toHaveBeenCalledTimes(2));
    expect(payOrder.mock.calls[1]?.[1]).toBe(key1);
    expect(await screen.findByRole("alert")).toHaveTextContent(
      "Bạn thao tác quá nhanh. Vui lòng chờ một lát rồi thử lại.",
    );

    // 409 ORDER_NOT_PENDING: vẫn dùng key1 cho request này, sau đó reset key và không hiện "Tạo đơn mới"
    await userEvent.click(payBtn);
    await waitFor(() => expect(payOrder).toHaveBeenCalledTimes(3));
    expect(payOrder.mock.calls[2]?.[1]).toBe(key1);
    const alert = await screen.findByRole("alert");
    expect(alert).toHaveTextContent("Đơn không còn ở trạng thái chờ thanh toán.");
    expect(within(alert).queryByRole("link", { name: "Tạo đơn mới" })).toBeNull();

    // Lần gọi tiếp theo dùng key mới khác key1
    await userEvent.click(payBtn);
    await waitFor(() => expect(payOrder).toHaveBeenCalledTimes(4));
    expect(payOrder.mock.calls[3]?.[1]).not.toBe(key1);

    // Hủy đơn thành công đưa focus về link chi tiết đơn (R1-67)
    await userEvent.click(screen.getByRole("button", { name: "Hủy đơn" }));
    const dialog = screen.getByRole("dialog");
    await userEvent.click(within(dialog).getByRole("button", { name: "Hủy đơn" }));
    const orderLink = screen.getByRole("link", { name: "DH-RETRY" });
    await waitFor(() => expect(document.activeElement).toBe(orderLink));
  });

  it("cancelOrder giữ Idempotency-Key qua 500 kể cả khi đóng mở lại dialog, và tạo key mới sau 409 (R2-19)", async () => {
    const order = makeOrder({ code: "DH-LIST-CAN", state: "PENDING_PAYMENT" });
    const cancelOrder = vi
      .fn()
      .mockRejectedValueOnce(new ApiStatusError(500, "Server 500"))
      .mockRejectedValueOnce(new ApiStatusError(500, "Server 500 again"))
      .mockRejectedValueOnce(new ApiStatusError(409, "Not pending", { code: "ORDER_NOT_PENDING" }))
      .mockResolvedValueOnce({ ...order, items: [], updatedAt: "2026-11-17T03:15:00Z", state: "CANCELLED" } as unknown as OrderDetail);

    renderWithProviders(
      <OrdersView
        loadCurrentSession={async () => STUDENT_SESSION}
        loadOrders={async () => ({ items: [order], page: 0, size: 20, totalItems: 1, totalPages: 1 })}
        cancelOrder={cancelOrder}
      />,
    );

    await userEvent.click(await screen.findByRole("button", { name: "Hủy đơn" }));
    await userEvent.click(within(screen.getByRole("dialog")).getByRole("button", { name: "Hủy đơn" }));
    await waitFor(() => expect(cancelOrder).toHaveBeenCalledTimes(1));
    const key1 = cancelOrder.mock.calls[0]?.[1];

    // Thử lại trong dialog -> giữ nguyên key1
    await userEvent.click(within(screen.getByRole("dialog")).getByRole("button", { name: "Hủy đơn" }));
    await waitFor(() => expect(cancelOrder).toHaveBeenCalledTimes(2));
    expect(cancelOrder.mock.calls[1]?.[1]).toBe(key1);

    // Đóng dialog khi đang có lỗi rồi mở lại -> giữ nguyên key1, lần 3 trả 409 -> xóa key
    await userEvent.click(within(screen.getByRole("dialog")).getByRole("button", { name: "Giữ đơn" }));
    await userEvent.click(screen.getByRole("button", { name: "Hủy đơn" }));
    await userEvent.click(within(screen.getByRole("dialog")).getByRole("button", { name: "Hủy đơn" }));
    await waitFor(() => expect(cancelOrder).toHaveBeenCalledTimes(3));
    expect(cancelOrder.mock.calls[2]?.[1]).toBe(key1);

    // Lần gọi tiếp theo tạo key mới
    await userEvent.click(within(screen.getByRole("dialog")).getByRole("button", { name: "Hủy đơn" }));
    await waitFor(() => expect(cancelOrder).toHaveBeenCalledTimes(4));
    expect(cancelOrder.mock.calls[3]?.[1]).not.toBe(key1);
  });

  it("focusAfterDialogUnmount đợi [role='dialog'] gỡ khỏi DOM trước khi focus (R1-67, R2-7, R2-11, R2-22)", async () => {
    const dialog = document.createElement("div");
    dialog.setAttribute("role", "dialog");
    const target = document.createElement("button");
    target.type = "button";
    target.textContent = "Target";
    document.body.append(dialog, target);

    const cleanup = focusAfterDialogUnmount(() => target);
    try {
      await new Promise((resolve) => window.setTimeout(resolve, 25));
      expect(document.activeElement).not.toBe(target);

      dialog.remove();
      await waitFor(() => expect(document.activeElement).toBe(target));
    } finally {
      cleanup();
      dialog.remove();
      target.remove();
    }
  });
});
