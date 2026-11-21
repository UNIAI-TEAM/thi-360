import { screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";
import type { OrderDetail, OrderPage, OrderSummary } from "@/features/orders/api";
import { ApiStatusError } from "@/lib/api/client";
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

    const tableRegion = await screen.findByRole("region", { name: "Danh sách đơn hàng của tôi" });
    const scoped = within(tableRegion);
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
    expect(await within(screen.getByRole("region", { name: "Danh sách đơn hàng của tôi" })).findByText("DH-PAGE2")).toBeInTheDocument();
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

    const tableRegion = await screen.findByRole("region", { name: "Danh sách đơn hàng của tôi" });
    await userEvent.click(within(tableRegion).getByRole("button", { name: "Tiếp tục thanh toán" }));

    const alert = await screen.findByRole("alert");
    expect(alert).toHaveTextContent(/Đơn đã hết hạn thanh toán/);
    expect(within(alert).getByRole("link", { name: "Tạo đơn mới" })).toHaveAttribute("href", "/checkout/HSA-FULL-03");
  });

  it("tài khoản quản trị không gọi API /me/orders", async () => {
    const loadOrders = vi.fn();
    renderWithProviders(<OrdersView loadCurrentSession={async () => ADMIN_SESSION} loadOrders={loadOrders} />);

    expect(await screen.findByRole("heading", { level: 1, name: "Tài khoản quản trị" })).toBeInTheDocument();
    expect(loadOrders).not.toHaveBeenCalled();
  });
});
