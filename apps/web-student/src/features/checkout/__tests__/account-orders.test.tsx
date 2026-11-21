import { screen, within } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import type { OrderDetail, OrderPage } from "@/features/orders/api";
import { OrderDetailView } from "@/features/orders/order-detail-view";
import { OrdersView } from "@/features/orders/orders-view";
import { renderWithProviders } from "@/test/render";
import { STUDENT_SESSION } from "@/test/storefront-fixtures";

describe("AC-25: đơn bộ đề có examId trống", () => {
  it("OrdersView hiển thị đúng đơn bộ đề khi examId là undefined", async () => {
    const bundlePage: OrderPage = {
      page: 0,
      size: 20,
      totalItems: 1,
      totalPages: 1,
      items: [
        {
          id: "ord-bundle-1",
          code: "DH261121BUNDLE",
          productType: "BUNDLE",
          examId: undefined,
          bundleId: "bundle-1",
          productCode: "BO-SP2E-TOAN-VAN",
          productName: "Bộ đề SP2E Toán + Văn",
          listPriceVnd: 500000,
          promotionDiscountVnd: 0,
          voucherDiscountVnd: 0,
          discountApplied: "NONE",
          amountDueVnd: 500000,
          attemptsGranted: 3,
          paymentMethod: "VNPAY",
          state: "FULFILLED",
          createdAt: "2026-11-21T02:00:00Z",
        },
      ],
    };

    renderWithProviders(
      <OrdersView loadCurrentSession={async () => STUDENT_SESSION} loadOrders={async () => bundlePage} />,
    );

    const table = await screen.findByRole("table", { name: "Danh sách đơn hàng của tôi" });
    const scoped = within(table);
    expect(scoped.getByRole("link", { name: "DH261121BUNDLE" })).toHaveAttribute(
      "href",
      "/account/orders/DH261121BUNDLE",
    );
    expect(scoped.getByText("Bộ đề")).toBeInTheDocument();
    expect(scoped.getByText("Bộ đề SP2E Toán + Văn")).toHaveAttribute("data-exam-content");
    expect(scoped.getByText("Đã cấp bài thi")).toBeInTheDocument();
  });

  it("OrderDetailView hiển thị đúng chi tiết đơn bộ đề khi examId trống", async () => {
    const bundleDetail: OrderDetail = {
      id: "ord-bundle-1",
      code: "DH261121BUNDLE",
      userId: "u-stu",
      buyerUsername: "student01",
      buyerFullName: "Học viên 01",
      productType: "BUNDLE",
      examId: undefined,
      examVersionNo: undefined,
      bundleId: "bundle-1",
      bundleVersionNo: 4,
      optionCodes: [],
      productCode: "BO-SP2E-TOAN-VAN",
      productName: "Bộ đề SP2E Toán + Văn",
      listPriceVnd: 500000,
      promotionDiscountVnd: 0,
      voucherDiscountVnd: 0,
      discountApplied: "NONE",
      amountDueVnd: 500000,
      attemptsGranted: 3,
      benefits: {},
      resultView: "FULL_SOLUTION",
      paymentMethod: "VNPAY",
      state: "FULFILLED",
      createdAt: "2026-11-21T02:00:00Z",
      updatedAt: "2026-11-21T02:05:00Z",
      items: [
        {
          id: "item-1",
          examId: "exam-a",
          examVersionNo: 2,
          examCode: "SP2E-TOAN-01",
          examName: "Đề SP2E Toán 01",
          optionCodes: [],
          attemptsGranted: 3,
          benefits: {},
          resultView: "FULL_SOLUTION",
          position: 1,
        },
        {
          id: "item-2",
          examId: "exam-b",
          examVersionNo: 1,
          examCode: "SP2E-VAN-01",
          examName: "Đề SP2E Văn 01",
          optionCodes: [],
          attemptsGranted: 3,
          benefits: {},
          resultView: "FULL_SOLUTION",
          position: 2,
        },
      ],
      payments: [],
      history: [],
    };

    renderWithProviders(
      <OrderDetailView
        code="DH261121BUNDLE"
        loadCurrentSession={async () => STUDENT_SESSION}
        loadOrderDetail={async () => bundleDetail}
      />,
    );

    expect(await screen.findByRole("heading", { level: 1, name: "Đơn DH261121BUNDLE" })).toBeInTheDocument();
    expect(screen.getByText("Bộ đề")).toBeInTheDocument();
    expect(screen.getByText("v4")).toBeInTheDocument();
    expect(screen.getByText("Bộ đề SP2E Toán + Văn")).toHaveAttribute("data-exam-content");
    expect(screen.getByText("Bài thi trong đơn (2)")).toBeInTheDocument();
    expect(screen.getByText("Đề SP2E Toán 01")).toHaveAttribute("data-exam-content");
    expect(screen.getByText("Đề SP2E Văn 01")).toHaveAttribute("data-exam-content");
  });
});
