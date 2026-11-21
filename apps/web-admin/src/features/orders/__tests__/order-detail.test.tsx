import { screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { fail, fakeApi, ok, withRealApi } from "@/test/fake-api";

import { OrderDetailView } from "../components/order-detail-view";
import {
  OrderPaymentStatusChip,
  OrderRefundStatusChip,
  OrderStateChip,
  PaymentAttemptStateChip,
  RefundRowStateChip,
} from "../components/order-status-chips";
import {
  makeOrderDetail,
  makePayment,
  makeRefund,
  renderOrdersUi,
  resetMockNavigation,
} from "./order-fixtures";

vi.mock("@/lib/api/client", async () => {
  const fake = await import("@/test/fake-api");
  return { createBrowserApiClient: fake.fakeBrowserApiClient };
});
vi.mock("next/navigation", async () => {
  const fixtures = await import("./order-fixtures");
  return fixtures.createNextNavigationMock();
});
withRealApi();

describe("OrderDetailView (AC-4, AC-9, AC-10, AC-15, AC-17)", () => {
  beforeEach(() => {
    fakeApi.reset();
    resetMockNavigation("/orders/DH-261121-0001", "");
  });

  it("hiện đầy đủ 4 khối chi tiết đơn hàng, quyền thi, thanh toán, hoàn tiền và lịch sử (AC-4)", async () => {
    fakeApi.get("/api/v1/admin/orders/{code}", () =>
      ok(
        makeOrderDetail({
          payments: [
            makePayment({
              refunds: [makeRefund({ state: "SUCCEEDED" })],
              refundedAmountVnd: 299000,
              refundableAmountVnd: 0,
            }),
          ],
          auditEvents: [
            {
              id: "019a0000-0000-7000-8000-000000000901",
              action: "REFUND_SUCCEEDED",
              actorType: "USER",
              actorUsername: "finance.admin",
              actorRoles: ["ADMIN"],
              module: "COMMERCE",
              outcome: "SUCCESS",
              occurredAt: "2026-11-21T03:00:02Z",
              reason: "Hoàn tiền theo yêu cầu đối soát",
            },
          ],
        }),
      ),
    );

    renderOrdersUi(<OrderDetailView code="DH-261121-0001" />);

    expect(await screen.findByText("Đơn hàng DH-261121-0001")).toBeInTheDocument();
    expect(screen.getByText("Thông tin đơn hàng tại thời điểm tạo")).toBeInTheDocument();
    expect(screen.getByText("Quyền thi được cấp")).toBeInTheDocument();
    expect(screen.getByText("Giao dịch thanh toán")).toBeInTheDocument();
    expect(screen.getByText("Lịch sử hoàn tiền")).toBeInTheDocument();
    expect(screen.getByText("Lịch sử xử lý và nhật ký kiểm toán")).toBeInTheDocument();
    expect(screen.getByText("Hoàn tiền thành công")).toBeInTheDocument();
  });

  it("ẩn mọi nút hoàn tiền và tra cứu hoàn tiền khi thiếu quyền ORDER.REFUND (AC-9 thiếu ORDER.REFUND)", async () => {
    fakeApi.get("/api/v1/admin/orders/{code}", () =>
      ok(
        makeOrderDetail({
          state: "PAID",
          payments: [
            makePayment({
              refundableAmountVnd: 299000,
              refunds: [makeRefund({ state: "UNKNOWN" })],
            }),
          ],
        }),
      ),
    );

    renderOrdersUi(<OrderDetailView code="DH-261121-0001" />, {
      permissions: ["ORDER.VIEW", "ORDER.RECOVER"],
    });

    const fulfillButtons = await screen.findAllByRole("button", { name: "Tiếp tục cấp quyền" });
    expect(fulfillButtons.length).toBeGreaterThan(0);
    expect(screen.queryByRole("button", { name: "Hoàn tiền" })).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Tra cứu lại" })).not.toBeInTheDocument();
  });

  it("ẩn nút Tiếp tục cấp quyền và Xử lý tiền về muộn khi thiếu quyền ORDER.RECOVER (AC-10 thiếu ORDER.RECOVER)", async () => {
    fakeApi.get("/api/v1/admin/orders/{code}", () =>
      ok(
        makeOrderDetail({
          state: "PAID",
          payments: [
            makePayment({
              lateSuccessAt: "2026-11-21T02:20:00Z",
              lateSuccessReview: "PENDING",
            }),
          ],
        }),
      ),
    );

    renderOrdersUi(<OrderDetailView code="DH-261121-0001" />, {
      permissions: ["ORDER.VIEW", "ORDER.REFUND"],
    });

    expect(await screen.findByText("Đơn hàng DH-261121-0001")).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Tiếp tục cấp quyền" })).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Xử lý tiền về muộn" })).not.toBeInTheDocument();
    expect(screen.getAllByRole("button", { name: "Hoàn tiền" }).length).toBeGreaterThan(0);
  });

  it("vô hiệu hóa nút Hoàn tiền và hiển thị tooltip khi có lần hoàn đang chạy (AC-15 có lần hoàn đang chạy)", async () => {
    fakeApi.get("/api/v1/admin/orders/{code}", () =>
      ok(
        makeOrderDetail({
          payments: [
            makePayment({
              refunds: [makeRefund({ state: "PROCESSING" })],
              inProgressRefundAmountVnd: 299000,
              refundableAmountVnd: 0,
            }),
          ],
        }),
      ),
    );

    renderOrdersUi(<OrderDetailView code="DH-261121-0001" />);

    const buttons = await screen.findAllByRole("button", { name: "Hoàn tiền" });
    for (const btn of buttons) {
      expect(btn).toBeDisabled();
    }
  });

  it("mở dialog Tiếp tục cấp quyền đúng copy, bắt buộc nhập lý do và gửi POST fulfill kèm Idempotency-Key (AC-17 tiếp tục cấp quyền)", async () => {
    const user = userEvent.setup();
    let fulfillPayload: Record<string, unknown> | undefined;
    let fulfillIdempotency: string | undefined;

    fakeApi.get("/api/v1/admin/orders/{code}", () =>
      ok(
        makeOrderDetail({
          state: "PAID",
          productName: "De IELTS Academic 09",
          attemptsGranted: 5,
        }),
      ),
    );

    fakeApi.post("/api/v1/admin/orders/{code}/fulfill", (call) => {
      fulfillPayload = call.options?.body as Record<string, unknown>;
      const params = call.options?.params as { header?: Record<string, string> } | undefined;
      fulfillIdempotency = params?.header?.["Idempotency-Key"];
      return ok(
        makeOrderDetail({
          state: "FULFILLED",
          productName: "De IELTS Academic 09",
          attemptsGranted: 5,
        }),
      );
    });

    renderOrdersUi(<OrderDetailView code="DH-261121-0001" />);

    await user.click((await screen.findAllByRole("button", { name: "Tiếp tục cấp quyền" }))[0]!);

    const dialog = await screen.findByRole("dialog");
    expect(within(dialog).getByText("Tiếp tục cấp quyền cho đơn hàng này?")).toBeInTheDocument();
    expect(
      within(dialog).getByText(/Đơn DH-261121-0001 sẽ cộng 5 lượt thi cho/),
    ).toBeInTheDocument();
    expect(within(dialog).getByText("De IELTS Academic 09")).toBeInTheDocument();

    // Blank submit must validate
    await user.click(screen.getByRole("button", { name: "Xác nhận tiếp tục cấp quyền" }));
    expect(screen.getByText("Vui lòng nhập lý do tiếp tục cấp quyền.")).toBeInTheDocument();

    await user.type(
      screen.getByLabelText(/Lý do tiếp tục cấp quyền/),
      "Khach da chuyen khoan du",
    );
    await user.click(screen.getByRole("button", { name: "Xác nhận tiếp tục cấp quyền" }));

    await waitFor(() => {
      expect(fulfillPayload).toEqual({ reason: "Khach da chuyen khoan du" });
      expect(fulfillIdempotency).toBeTruthy();
    });
  });

  it("hiện lỗi nội tuyến nếu server trả 409 ORDER_RECOVERY_NOT_ALLOWED", async () => {
    const user = userEvent.setup();
    fakeApi.get("/api/v1/admin/orders/{code}", () =>
      ok(makeOrderDetail({ state: "PAID" })),
    );
    fakeApi.post("/api/v1/admin/orders/{code}/fulfill", () =>
      fail(409, { code: "ORDER_RECOVERY_NOT_ALLOWED", message: "not allowed" }),
    );

    renderOrdersUi(<OrderDetailView code="DH-261121-0001" />);
    await user.click((await screen.findAllByRole("button", { name: "Tiếp tục cấp quyền" }))[0]!);
    await user.type(screen.getByLabelText(/Lý do tiếp tục cấp quyền/), "Thu cap quyen");
    await user.click(screen.getByRole("button", { name: "Xác nhận tiếp tục cấp quyền" }));

    expect(
      await screen.findByText("Đơn hàng không ở trạng thái cho phép tiếp tục cấp quyền."),
    ).toBeInTheDocument();
  });

  it("D-4: chip trạng thái không lộ raw i18n key khi nhận UNKNOWN hoặc enum lạ", () => {
    const { container } = renderOrdersUi(
      <div>
        <OrderStateChip state="FUTURE_ORDER_STATE" />
        <OrderPaymentStatusChip status="FUTURE_PAY_STATUS" />
        <PaymentAttemptStateChip state="FUTURE_ATTEMPT_STATE" />
        <OrderRefundStatusChip status="UNKNOWN" />
        <OrderRefundStatusChip status="FUTURE_REFUND_STATUS" />
        <RefundRowStateChip state="FUTURE_ROW_STATE" />
      </div>,
      { locale: "vi" },
    );

    expect(container.textContent).not.toContain("orders.");
    expect(screen.getAllByText("Chưa rõ").length).toBeGreaterThanOrEqual(6);

    const enRender = renderOrdersUi(<OrderRefundStatusChip status="UNKNOWN" />, {
      locale: "en",
    });
    expect(enRender.container.textContent).toContain("Unknown");
    expect(enRender.container.textContent).not.toContain("orders.");
  });

  it("D-6: timeline lịch sử dịch mã sự kiện/tác nhân và tô màu gatewayMessage theo trạng thái", async () => {
    fakeApi.get("/api/v1/admin/orders/{code}", () =>
      ok(
        makeOrderDetail({
          payments: [
            makePayment({
              id: "pay-1",
              txnRef: "TXN01",
              state: "SUCCEEDED",
              refunds: [
                makeRefund({
                  id: "ref-fail",
                  paymentTxnRef: "TXN01",
                  state: "FAILED",
                  gatewayMessage: "Bank rejected card",
                }),
                makeRefund({
                  id: "ref-ok",
                  paymentTxnRef: "TXN01",
                  state: "SUCCEEDED",
                  gatewayMessage: "Refund completed cleanly",
                }),
                makeRefund({
                  id: "ref-unk",
                  paymentTxnRef: "TXN01",
                  state: "UNKNOWN",
                  gatewayMessage: "Gateway timeout pending check",
                }),
              ],
            }),
          ],
          orderHistory: [
            {
              id: "h-1",
              fromState: undefined,
              toState: "CREATED",
              reason: "ORDER_CREATED",
              actor: "LEARNER",
              at: "2026-11-20T08:00:00.000Z",
            },
            {
              id: "h-2",
              fromState: "CREATED",
              toState: "PENDING_PAYMENT",
              reason: "PAYMENT_ATTEMPT_CREATED:TXN01",
              actor: "PLATFORM",
              at: "2026-11-20T08:01:00.000Z",
            },
            {
              id: "h-3",
              fromState: "PENDING_PAYMENT",
              toState: "PAID",
              reason: "PAYMENT_SUCCEEDED:TXN01",
              actor: "VNPAY_IPN",
              at: "2026-11-20T08:02:00.000Z",
            },
            {
              id: "h-4",
              fromState: "PAID",
              toState: "FULFILLED",
              reason: "FULFILLED",
              actor: "WORKER",
              at: "2026-11-20T08:03:00.000Z",
            },
            {
              id: "h-5",
              fromState: "EXPIRED",
              toState: "PAID",
              reason: "LATE_SUCCESS_PENDING_REVIEW:TXN01",
              actor: "VNPAY_QUERYDR",
              at: "2026-11-20T08:04:00.000Z",
            },
          ],
          auditEvents: [
            {
              id: "a-1",
              action: "ORDER_RETRY_FULFILL",
              module: "ORDERS",
              outcome: "SUCCESS",
              actorType: "SYSTEM",
              actorRoles: [],
              actorUsername: undefined,
              reason: "Custom free-text note from support",
              occurredAt: "2026-11-20T08:05:00.000Z",
            },
          ],
        }),
      ),
    );

    const { container } = renderOrdersUi(<OrderDetailView code="DH-261121-0001" />, {
      locale: "en",
    });

    expect(await screen.findByText("Order created")).toBeInTheDocument();
    expect(screen.getByText("Created payment attempt (TXN01)")).toBeInTheDocument();
    expect(screen.getByText("Payment succeeded (TXN01)")).toBeInTheDocument();
    expect(screen.getByText("Entitlements granted")).toBeInTheDocument();
    expect(
      screen.getByText("Late-success payment on failed attempt, pending review (TXN01)"),
    ).toBeInTheDocument();

    expect(screen.getByText("Learner")).toBeInTheDocument();
    expect(screen.getByText("Platform admin")).toBeInTheDocument();
    expect(screen.getByText("VNPAY IPN")).toBeInTheDocument();
    expect(screen.getByText("VNPAY QueryDR")).toBeInTheDocument();
    expect(screen.getAllByText("System").length).toBeGreaterThanOrEqual(2);

    const freeTextNode = screen.getByText("Custom free-text note from support");
    expect(freeTextNode.closest("[data-exam-content]")).not.toBeNull();

    const failMsg = screen.getByText("Bank rejected card");
    expect(failMsg).toHaveClass("text-danger");
    expect(failMsg.closest("[data-exam-content]")).not.toBeNull();

    const okMsg = screen.getByText("Refund completed cleanly");
    expect(okMsg).toHaveClass("text-text-secondary");
    expect(okMsg).not.toHaveClass("text-danger");

    const unkMsg = screen.getByText("Gateway timeout pending check");
    expect(unkMsg).toHaveClass("text-warning");

    expect(container.querySelectorAll("ol")).toHaveLength(1);
  });

  it("D-2: chuyển bảng cấp quyền, giao dịch và hoàn tiền sang ListCard ở viewport hẹp mà vẫn giữ nút thao tác", async () => {
    const origGetBoundingClientRect = HTMLElement.prototype.getBoundingClientRect;
    HTMLElement.prototype.getBoundingClientRect = () =>
      ({
        width: 350,
        height: 120,
        top: 0,
        left: 0,
        bottom: 120,
        right: 350,
        x: 0,
        y: 0,
        toJSON: () => ({}),
      }) as DOMRect;

    try {
      fakeApi.get("/api/v1/admin/orders/{code}", () =>
        ok(
          makeOrderDetail({
            state: "PAID",
            paymentStatus: "SUCCEEDED",
            refundStatus: "NONE",
            payments: [
              makePayment({
                id: "pay-pend",
                attemptSeq: 1,
                txnRef: "TXNPEND",
                state: "CREATED",
                lateSuccessAt: undefined,
                refunds: [],
              }),
              makePayment({
                id: "pay-late",
                attemptSeq: 2,
                txnRef: "TXNLATE",
                state: "SUCCEEDED",
                lateSuccessAt: "2026-11-20T09:00:00.000Z",
                lateSuccessReview: "PENDING",
                refundableAmountVnd: 100000,
                refunds: [
                  makeRefund({
                    id: "ref-1",
                    paymentTxnRef: "TXNLATE",
                    state: "UNKNOWN",
                    gatewayMessage: "Waiting gateway",
                  }),
                ],
              }),
            ],
          }),
        ),
      );

      renderOrdersUi(<OrderDetailView code="DH-261121-0001" />);

      expect((await screen.findAllByRole("button", { name: "Hoàn tiền" })).length).toBeGreaterThanOrEqual(2);
      expect(screen.getAllByRole("button", { name: "Tra cứu lại" }).length).toBeGreaterThanOrEqual(1);
      expect(
        screen.getAllByRole("button", { name: "Xử lý tiền về muộn" }).length,
      ).toBeGreaterThanOrEqual(2);
      expect(document.querySelectorAll("table")).toHaveLength(0);
    } finally {
      HTMLElement.prototype.getBoundingClientRect = origGetBoundingClientRect;
    }
  });
});
