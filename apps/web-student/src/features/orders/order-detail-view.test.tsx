import { screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";
import type { OrderDetail } from "@/features/orders/api";
import { ApiStatusError } from "@/lib/api/client";
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
    const paymentsTable = screen.getByRole("region", { name: "Danh sách giao dịch thanh toán" });
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
    await userEvent.click(within(dialog).getByRole("button", { name: "Giữ đơn" }));
    expect(await screen.findByText("Đang kiểm tra")).toBeInTheDocument();
  });
});
