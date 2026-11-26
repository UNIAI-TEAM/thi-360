import { screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";

import OrdersLayout from "@/app/(shell)/orders/layout";
import { fail, fakeApi, ok, withRealApi } from "@/test/fake-api";

import { OrderDetailView } from "../components/order-detail-view";
import {
  OrderPaymentStatusChip,
  OrderRefundStatusChip,
  OrderStateChip,
  PaymentAttemptStateChip,
  PaymentClassificationChip,
  RefundRowStateChip,
} from "../components/order-status-chips";
import {
  makeGrant,
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

  it("hiện đầy đủ 4 khối chi tiết đơn hàng, quyền thi, thanh toán, hoàn tiền và lịch sử (AC-4, R1-60, R1-61, R1-68)", async () => {
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
          orderHistory: [
            {
              id: "h-seq-1",
              fromState: undefined,
              toState: "PENDING_PAYMENT",
              reason: "ORDER_CREATED",
              actor: "LEARNER",
              at: "2026-11-21T02:00:00Z",
            },
            {
              id: "h-seq-2",
              fromState: "PENDING_PAYMENT",
              toState: "FULFILLED",
              reason: "FULFILLED:TXN-261121-0001-01",
              actor: "SYSTEM",
              at: "2026-11-21T02:00:00Z",
            },
            {
              id: "h-rej",
              fromState: "FULFILLED",
              toState: "PAID",
              reason: "REFUND_REJECTED_09:09",
              actor: "SYSTEM",
              at: "2026-11-21T03:00:00Z",
            },
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

    const { container } = renderOrdersUi(<OrderDetailView code="DH-261121-0001" />);

    expect(await screen.findByText("Đơn hàng DH-261121-0001")).toBeInTheDocument();
    expect(screen.getByText("Thông tin đơn hàng tại thời điểm tạo")).toBeInTheDocument();
    expect(screen.getByText("Quyền thi được cấp")).toBeInTheDocument();
    expect(screen.getByText("Giao dịch thanh toán")).toBeInTheDocument();
    expect(screen.getByText("Lịch sử hoàn tiền")).toBeInTheDocument();
    expect(screen.getByText("Lịch sử xử lý và nhật ký kiểm toán")).toBeInTheDocument();
    expect(screen.getByText("Hoàn tiền thành công")).toBeInTheDocument();

    // Block 1 snapshot concrete values (R1-68)
    expect(screen.getByText("Nguyen Van An")).toBeInTheDocument();
    expect(screen.getByText("an.nguyen")).toBeInTheDocument();
    expect(screen.getByText("IELTS Academic Practice Test 01")).toBeInTheDocument();
    expect(screen.getByText("IELTS-AC-01")).toBeInTheDocument();
    expect(screen.getByText("350.000 đ")).toBeInTheDocument();
    expect(screen.getByText("-51.000 đ")).toBeInTheDocument();
    expect(screen.getAllByText("299.000 đ").length).toBeGreaterThanOrEqual(2);
    expect(screen.getByText("3")).toBeInTheDocument();
    expect(screen.getByText(/Phương thức thanh toán:\s*VNPAY/)).toBeInTheDocument();

    // Block 2 grants concrete values (R1-68)
    expect(
      screen.getByText("019a0000-0000-7000-8000-000000000502"),
    ).toBeInTheDocument();
    expect(screen.getByText("2 / 3")).toBeInTheDocument();
    expect(screen.getByText("Còn hiệu lực")).toBeInTheDocument();

    // Block 3 payments concrete values
    expect(screen.getByText("#01 · TXN-261121-0001-01")).toBeInTheDocument();
    expect(screen.getByText("Giao dịch ghi nhận đơn")).toBeInTheDocument();

    // Block 4 REFUND_REJECTED_09 danger dot (R1-60) & deterministic equal-timestamp ordering (R1-61)
    const timelineItems = Array.from(container.querySelectorAll("ol > li"));
    const rejectedLi = timelineItems.find((li) =>
      li.textContent?.includes("VNPAY từ chối yêu cầu hoàn tiền"),
    );
    expect(rejectedLi?.querySelector(".bg-danger")).not.toBeNull();
    const sameTsItems = timelineItems.filter(
      (li) =>
        li.textContent?.includes("Đã hoàn tất cấp lượt thi") ||
        li.textContent?.includes("Tạo đơn hàng mới"),
    );
    expect(sameTsItems[0]).toHaveTextContent("Đã hoàn tất cấp lượt thi");
    expect(sameTsItems[1]).toHaveTextContent("Tạo đơn hàng mới");
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

    expect(await screen.findByText("Đơn hàng DH-261121-0001")).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: /^Hoàn tiền/ })).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: /^Tra cứu lại/ })).not.toBeInTheDocument();
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
    expect(screen.queryByRole("button", { name: /^Xử lý tiền về muộn/ })).not.toBeInTheDocument();
    expect(screen.getAllByRole("button", { name: /^Hoàn tiền/ }).length).toBeGreaterThan(0);
  });

  it("vô hiệu hóa nút Hoàn tiền và hiển thị 'Có lần hoàn đang chạy' cho cả PROCESSING và UNKNOWN; hiện Tra cứu lại cho API PROCESSING (AC-15, R1-4..R1-7, R1-41)", async () => {
    fakeApi.get("/api/v1/admin/orders/{code}", () =>
      ok(
        makeOrderDetail({
          payments: [
            makePayment({
              id: "pay-proc",
              attemptSeq: 1,
              txnRef: "TXN-PROC",
              refunds: [makeRefund({ id: "ref-proc", kind: "API", state: "PROCESSING" })],
              inProgressRefundAmountVnd: 299000,
              refundableAmountVnd: 0,
            }),
            makePayment({
              id: "pay-unk",
              attemptSeq: 2,
              txnRef: "TXN-UNK",
              duplicate: true,
              excessReason: "AFTER_COUNTED",
              refunds: [makeRefund({ id: "ref-unk", kind: "API", state: "UNKNOWN" })],
              inProgressRefundAmountVnd: 299000,
              refundableAmountVnd: 0,
            }),
          ],
        }),
      ),
    );

    renderOrdersUi(<OrderDetailView code="DH-261121-0001" />);

    const procBtn = (
      await screen.findAllByRole("button", { name: "Hoàn tiền giao dịch lần 01 (TXN-PROC)" })
    )[0]!;
    const unkBtn = screen.getAllByRole("button", {
      name: "Hoàn tiền giao dịch lần 02 (TXN-UNK)",
    })[0]!;

    expect(procBtn).toBeDisabled();
    expect(unkBtn).toBeDisabled();
    expect(procBtn).toHaveAccessibleDescription("Có lần hoàn đang chạy");
    expect(unkBtn).toHaveAccessibleDescription("Có lần hoàn đang chạy");
    expect(screen.getAllByText("Có lần hoàn đang chạy").length).toBeGreaterThanOrEqual(2);

    // Query VNPAY action must be available for BOTH API PROCESSING and API UNKNOWN refunds (R1-7)
    expect(
      screen.getByRole("button", { name: "Tra cứu lại lần hoàn (ref-proc)" }),
    ).toBeInTheDocument();
    expect(
      screen.getByRole("button", { name: "Tra cứu lại lần hoàn (ref-unk)" }),
    ).toBeInTheDocument();
  });

  it("mở dialog Tiếp tục cấp quyền đúng copy TM 9.5, bắt buộc nhập lý do, cập nhật Đã cấp quyền sau submit (AC-17, R1-1..R1-3, R1-54)", async () => {
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
          grants: [makeGrant({ attempts: 5, attemptsLeft: 5 })],
        }),
      );
    });

    renderOrdersUi(<OrderDetailView code="DH-261121-0001" />);

    await user.click((await screen.findAllByRole("button", { name: "Tiếp tục cấp quyền" }))[0]!);

    const dialog = await screen.findByRole("dialog");
    expect(within(dialog).getByText("Tiếp tục cấp quyền cho đơn hàng này?")).toBeInTheDocument();
    expect(
      within(dialog).getByText(
        /Đơn DH-261121-0001 đã thanh toán thành công\. Tiếp tục cấp quyền bài thi/,
      ),
    ).toBeInTheDocument();
    expect(within(dialog).getByText("De IELTS Academic 09")).toBeInTheDocument();
    expect(
      within(dialog).getByText(
        /cho tài khoản an\.nguyen\? Người dùng sẽ không bị thu thêm tiền\./,
      ),
    ).toBeInTheDocument();
    expect(within(dialog).getByText("Số lượt thi sẽ cộng: 5 lượt")).toBeInTheDocument();

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
      expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
    });
    expect(screen.getByText("Đã cấp quyền")).toBeInTheDocument();
  });

  it("chế độ en: dialog Tiếp tục cấp quyền đúng câu chữ TM 9.5 và không lộ message raw từ fieldErrors (R1-3, R1-25, R1-45)", async () => {
    const user = userEvent.setup();
    fakeApi.get("/api/v1/admin/orders/{code}", () =>
      ok(
        makeOrderDetail({
          state: "PAID",
          paymentMethod: "FREE",
          payments: [],
          productName: "De IELTS Academic 09",
          attemptsGranted: 2,
        }),
      ),
    );
    fakeApi.post("/api/v1/admin/orders/{code}/fulfill", () =>
      fail(400, {
        code: "VALIDATION_FAILED",
        message: "raw top level",
        fieldErrors: [{ field: "reason", code: "NotBlank", message: "raw backend msg" }],
      }),
    );

    renderOrdersUi(<OrderDetailView code="DH-261121-0001" />, { locale: "en" });
    await user.click((await screen.findAllByRole("button", { name: "Continue fulfillment" }))[0]!);

    const dialog = await screen.findByRole("dialog");
    expect(
      within(dialog).getByText(/Order DH-261121-0001 has been paid successfully\. Continue granting exam/),
    ).toBeInTheDocument();
    expect(within(dialog).getByText("De IELTS Academic 09")).toBeInTheDocument();
    expect(
      within(dialog).getByText(/to account an\.nguyen\? The user will not be charged again\./),
    ).toBeInTheDocument();

    await user.type(screen.getByLabelText(/Reason for manual fulfillment/), "Verified free order");
    await user.click(screen.getByRole("button", { name: "Confirm fulfillment" }));

    expect(
      await within(dialog).findByText("Please enter a reason to continue fulfillment."),
    ).toBeInTheDocument();
    expect(dialog.textContent).not.toContain("raw backend msg");
    expect(dialog.textContent).not.toContain("raw top level");
  });

  it("ẩn nút Tiếp tục cấp quyền ở các trạng thái không đủ điều kiện TM 9.5 và hiện cho PAID + FREE (R1-1, R1-2, R1-54)", async () => {
    const ineligibleCases: Array<{ label: string; detail: ReturnType<typeof makeOrderDetail> }> = [
      { label: "FULFILLED", detail: makeOrderDetail({ state: "FULFILLED" }) },
      { label: "PENDING_PAYMENT", detail: makeOrderDetail({ state: "PENDING_PAYMENT" }) },
      { label: "REFUNDED", detail: makeOrderDetail({ state: "REFUNDED" }) },
      {
        label: "PAID_WITH_UNKNOWN_REFUND",
        detail: makeOrderDetail({
          state: "PAID",
          payments: [makePayment({ refunds: [makeRefund({ state: "UNKNOWN" })] })],
        }),
      },
      {
        label: "PAID_WITH_PROCESSING_REFUND",
        detail: makeOrderDetail({
          state: "PAID",
          payments: [makePayment({ refunds: [makeRefund({ state: "PROCESSING" })] })],
        }),
      },
      {
        label: "PAID_FULLY_REFUNDED",
        detail: makeOrderDetail({
          state: "PAID",
          payments: [makePayment({ amountVnd: 299000, refundedAmountVnd: 299000, refundableAmountVnd: 0 })],
        }),
      },
      {
        label: "PAID_NO_COUNTED_PAYMENT",
        detail: makeOrderDetail({
          state: "PAID",
          payments: [makePayment({ duplicate: true, state: "SUCCEEDED" })],
        }),
      },
    ];

    for (const c of ineligibleCases) {
      fakeApi.reset();
      fakeApi.get("/api/v1/admin/orders/{code}", () => ok(c.detail));
      const { unmount } = renderOrdersUi(<OrderDetailView code="DH-261121-0001" />);
      expect(await screen.findByText("Đơn hàng DH-261121-0001")).toBeInTheDocument();
      expect(
        screen.queryByRole("button", { name: "Tiếp tục cấp quyền" }),
        `Expected no Continue Fulfill button for ${c.label}`,
      ).not.toBeInTheDocument();
      unmount();
    }
  });

  it("ưu tiên khoản thu thừa trên header Hoàn tiền và đặt aria-label phân biệt từng dòng thanh toán (R1-35, R1-36, R1-47)", async () => {
    const user = userEvent.setup();
    const countedPayment = makePayment({
      id: "pay-1",
      attemptSeq: 1,
      txnRef: "TXN-COUNTED-01",
      duplicate: false,
      refundableAmountVnd: 299000,
    });
    const excessRefundable = makePayment({
      id: "pay-2",
      attemptSeq: 2,
      txnRef: "TXN-EXCESS-02",
      duplicate: true,
      excessReason: "AFTER_COUNTED",
      refundableAmountVnd: 299000,
    });
    const excessRefunded = makePayment({
      id: "pay-3",
      attemptSeq: 3,
      txnRef: "TXN-EXCESS-03",
      duplicate: true,
      excessReason: "AFTER_COUNTED",
      refundableAmountVnd: 0,
      refundedAmountVnd: 299000,
    });

    fakeApi.get("/api/v1/admin/orders/{code}", () =>
      ok(makeOrderDetail({ payments: [countedPayment, excessRefundable, excessRefunded] })),
    );

    renderOrdersUi(<OrderDetailView code="DH-261121-0001" />);

    expect(await screen.findByText("Thu thừa (đã hoàn)")).toBeInTheDocument();
    const headerOrExcessButtons = screen.getAllByRole("button", {
      name: "Hoàn tiền giao dịch lần 02 (TXN-EXCESS-02)",
    });
    expect(headerOrExcessButtons.length).toBe(2);
    expect(
      screen.getByRole("button", { name: "Hoàn tiền giao dịch lần 01 (TXN-COUNTED-01)" }),
    ).toBeInTheDocument();

    // Clicking the header Refund button (index 0) must open excess payment, NOT counted payment
    await user.click(headerOrExcessButtons[0]!);
    expect(await screen.findByText("Khoản thu thừa cần hoàn trả")).toBeInTheDocument();
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

  it("OrdersLayout chặn người thiếu ORDER.VIEW bằng ForbiddenView tại chỗ và cho phép người có ORDER.VIEW (R1-13)", async () => {
    const { unmount } = renderOrdersUi(
      <OrdersLayout>
        <div>Nội dung đơn hàng</div>
      </OrdersLayout>,
      { permissions: ["EXAM.VIEW"] },
    );

    expect(
      await screen.findByRole("heading", { name: "Bạn không có quyền vào trang này" }),
    ).toBeInTheDocument();
    expect(screen.queryByText("Nội dung đơn hàng")).not.toBeInTheDocument();
    unmount();

    renderOrdersUi(
      <OrdersLayout>
        <div>Nội dung đơn hàng</div>
      </OrdersLayout>,
      { permissions: ["ORDER.VIEW"] },
    );

    expect(await screen.findByText("Nội dung đơn hàng")).toBeInTheDocument();
  });

  it("D-4: chip trạng thái không lộ raw i18n key khi nhận UNKNOWN hoặc enum lạ và hiện Thu thừa (đã hoàn)", () => {
    const { container } = renderOrdersUi(
      <div>
        <OrderStateChip state="FUTURE_ORDER_STATE" />
        <OrderPaymentStatusChip status="FUTURE_PAY_STATUS" />
        <PaymentAttemptStateChip state="FUTURE_ATTEMPT_STATE" />
        <OrderRefundStatusChip status="UNKNOWN" />
        <OrderRefundStatusChip status="FUTURE_REFUND_STATUS" />
        <RefundRowStateChip state="FUTURE_ROW_STATE" />
        <PaymentClassificationChip
          payment={makePayment({
            duplicate: true,
            excessReason: "AFTER_COUNTED",
            refundableAmountVnd: 0,
            refundedAmountVnd: 299000,
          })}
        />
      </div>,
      { locale: "vi" },
    );

    expect(container.textContent).not.toContain("orders.");
    expect(screen.getAllByText("Chưa rõ").length).toBeGreaterThanOrEqual(6);
    expect(screen.getByText("Thu thừa (đã hoàn)")).toBeInTheDocument();

    const enRender = renderOrdersUi(
      <div>
        <OrderRefundStatusChip status="UNKNOWN" />
        <PaymentClassificationChip
          payment={makePayment({
            duplicate: true,
            excessReason: "AFTER_COUNTED",
            refundableAmountVnd: 0,
            refundedAmountVnd: 299000,
          })}
        />
      </div>,
      { locale: "en" },
    );
    expect(enRender.container.textContent).toContain("Unknown");
    expect(enRender.container.textContent).toContain("Excess (refunded)");
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

      expect((await screen.findAllByRole("button", { name: /^Hoàn tiền/ })).length).toBeGreaterThanOrEqual(2);
      expect(screen.getAllByRole("button", { name: /^Tra cứu lại/ }).length).toBeGreaterThanOrEqual(1);
      expect(
        screen.getAllByRole("button", { name: /^Xử lý tiền về muộn/ }).length,
      ).toBeGreaterThanOrEqual(2);
      expect(document.querySelectorAll("table")).toHaveLength(0);
    } finally {
      HTMLElement.prototype.getBoundingClientRect = origGetBoundingClientRect;
    }
  });
});

describe("Đầu trang chi tiết đơn hàng (UNI-2825)", () => {
  beforeEach(() => {
    fakeApi.reset();
    resetMockNavigation("/orders/DH-261121-0001", "");
  });

  function expectBackLinkAboveTitle(heading: HTMLElement) {
    const header = heading.closest('[data-slot="detail-header"]');
    expect(header).not.toBeNull();
    const back = within(header as HTMLElement).getByRole("link", { name: "Danh sách đơn hàng" });
    expect(back).toHaveAttribute("href", "/orders");
    expect(back.compareDocumentPosition(heading) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy();
    return header as HTMLElement;
  }

  it("lối Danh sách đơn hàng nằm trên tiêu đề, chip trạng thái cạnh tiêu đề, nút Quay lại không còn trong nhóm nút", async () => {
    fakeApi.get("/api/v1/admin/orders/{code}", () => ok(makeOrderDetail()));
    renderOrdersUi(<OrderDetailView code="DH-261121-0001" />);

    const heading = await screen.findByRole("heading", { level: 1, name: "Đơn hàng DH-261121-0001" });
    const header = expectBackLinkAboveTitle(heading);
    expect(heading.parentElement).toHaveTextContent("Đã cấp quyền");
    expect(header).toHaveTextContent("Thành tiền cần trả: 299.000 đ");
    const actions = header.querySelector('[data-slot="page-actions"]') as HTMLElement;
    expect(within(actions).getByRole("link", { name: "Xem đơn của người mua" })).toBeInTheDocument();
    expect(screen.queryByRole("link", { name: "Quay lại danh sách đơn hàng" })).not.toBeInTheDocument();
  });

  it("màn lỗi 404 cũng có đầu trang với lối Danh sách đơn hàng", async () => {
    fakeApi.get("/api/v1/admin/orders/{code}", () => fail(404, { code: "ORDER_NOT_FOUND" }));
    renderOrdersUi(<OrderDetailView code="DH-261121-0001" />);

    const heading = await screen.findByRole("heading", { level: 1, name: "Đơn hàng DH-261121-0001" });
    expectBackLinkAboveTitle(heading);
  });
});
