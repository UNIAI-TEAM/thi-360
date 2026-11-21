import { screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { fail, fakeApi, ok, withRealApi } from "@/test/fake-api";

import { RefundDialog } from "../components/refund-dialog";
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

describe("RefundDialog (AC-11, AC-12, AC-13, AC-14, AC-16)", () => {
  beforeEach(() => {
    fakeApi.reset();
    resetMockNavigation("/orders/DH-261121-0001", "");
  });

  it("chặn vượt số còn hoàn ở client và xử lý 409 REFUND_AMOUNT_EXCEEDED từ server giữ nguyên dialog (AC-11 vượt số còn hoàn)", async () => {
    const user = userEvent.setup();
    const order = makeOrderDetail();
    const payment = makePayment({
      amountVnd: 300000,
      refundedAmountVnd: 100000,
      refundableAmountVnd: 200000,
    });

    const onOpenChange = vi.fn();
    renderOrdersUi(
      <RefundDialog open onOpenChange={onOpenChange} order={order} payment={payment} />,
    );

    const amountInput = await screen.findByLabelText(/Số tiền hoàn/);
    await user.clear(amountInput);
    await user.type(amountInput, "250000");
    await user.click(screen.getByRole("checkbox"));
    await user.type(screen.getByLabelText(/Lý do hoàn tiền/), "Hoan vuot muc");
    await user.click(screen.getByRole("button", { name: "Gửi yêu cầu hoàn tiền" }));

    expect(
      await screen.findByText("Số tiền hoàn vượt quá số tiền còn có thể hoàn trên giao dịch này."),
    ).toBeInTheDocument();
    expect(onOpenChange).not.toHaveBeenCalledWith(false);

    // Server 409 case
    fakeApi.post("/api/v1/admin/orders/{code}/payments/{paymentId}/refunds", () =>
      fail(409, { code: "REFUND_AMOUNT_EXCEEDED", message: "exceeded" }),
    );
    await user.clear(amountInput);
    await user.type(amountInput, "200000");
    await user.click(screen.getByRole("button", { name: "Gửi yêu cầu hoàn tiền" }));

    expect(
      await screen.findByText("Số tiền hoàn vượt quá hạn mức còn lại có thể hoàn."),
    ).toBeInTheDocument();
    expect(onOpenChange).not.toHaveBeenCalledWith(false);
  });

  it("hiện chế độ UNKNOWN cho phép Tra cứu lại trên VNPAY khi refund UNKNOWN (AC-12 UNKNOWN, R1-22, R1-23, R1-53)", async () => {
    const user = userEvent.setup();
    const queriedIds: string[] = [];
    const unknownRefund = makeRefund({ id: "ref-unknown-1", state: "UNKNOWN" });
    const failedPrior = makeRefund({ id: "ref-failed-prior", kind: "API", state: "FAILED" });
    const payment = makePayment({ refunds: [unknownRefund, failedPrior], refundableAmountVnd: 0 });
    const order = makeOrderDetail({ payments: [payment] });

    fakeApi.post("/api/v1/admin/refunds/{id}/query", (call) => {
      const params = call.options?.params as { path?: { id?: string } } | undefined;
      if (params?.path?.id) queriedIds.push(params.path.id);
      return ok(makeRefund({ id: "ref-unknown-1", state: "SUCCEEDED" }));
    });

    const onOpenChange = vi.fn();
    renderOrdersUi(
      <RefundDialog open onOpenChange={onOpenChange} order={order} payment={payment} />,
    );

    expect(
      await screen.findByText("Xử lý giao dịch hoàn chưa rõ kết quả"),
    ).toBeInTheDocument();
    // AC-12 negative: UNKNOWN must NOT show manual refund radio even when prior FAILED API exists (R1-53)
    expect(screen.queryByRole("radio", { name: /Ghi nhận hoàn tiền thủ công/ })).toBeNull();
    // DialogDescription must exist (R1-66)
    const dialog = screen.getByRole("dialog");
    expect(dialog.getAttribute("aria-describedby")).toBeTruthy();

    await user.click(screen.getByRole("button", { name: "Tra cứu lại trên VNPAY" }));

    await waitFor(() => {
      expect(queriedIds).toEqual(["ref-unknown-1"]);
      expect(onOpenChange).toHaveBeenCalledWith(false);
    });
  });

  it("chống bấm đúp Tra cứu lại VNPAY (chỉ gửi 1 POST) và invalidate chi tiết khi 409 (R1-22, R1-23)", async () => {
    const user = userEvent.setup();
    let queryCalls = 0;
    let releaseQuery: (() => void) | undefined;
    const unknownRefund = makeRefund({ id: "ref-unk-dbl", kind: "API", state: "UNKNOWN" });
    const payment = makePayment({ refunds: [unknownRefund], refundableAmountVnd: 0 });
    const order = makeOrderDetail({ payments: [payment] });

    fakeApi.post("/api/v1/admin/refunds/{id}/query", async () => {
      queryCalls += 1;
      await new Promise<void>((resolve) => {
        releaseQuery = resolve;
      });
      return fail(409, { code: "REFUND_QUERY_NOT_ALLOWED", message: "already resolved" });
    });

    renderOrdersUi(<RefundDialog open onOpenChange={vi.fn()} order={order} payment={payment} />);
    const queryBtn = await screen.findByRole("button", { name: "Tra cứu lại trên VNPAY" });
    await user.click(queryBtn);
    await user.click(queryBtn);
    expect(queryCalls).toBe(1);

    releaseQuery?.();
    expect(
      await screen.findByText("Không thể tra cứu lại yêu cầu hoàn tiền ở trạng thái hiện tại."),
    ).toBeInTheDocument();
  });

  it("mở khóa radio Ghi nhận hoàn thủ công và amountInput từ 0 sau khi đánh dấu thất bại giao dịch UNKNOWN và bắt xác nhận 2 bước (AC-13, AC-14, R1-20, R1-21, R1-53)", async () => {
    const user = userEvent.setup();
    const order = makeOrderDetail();
    const unknownRefund = makeRefund({ id: "ref-unk-2", kind: "API", state: "UNKNOWN" });
    const paymentWithUnknown = makePayment({
      refunds: [unknownRefund],
      refundableAmountVnd: 0,
    });

    let markFailedCalls = 0;
    let markedPayload: Record<string, unknown> | undefined;
    let createPayload: Record<string, unknown> | undefined;

    fakeApi.post("/api/v1/admin/refunds/{id}/mark-failed", (call) => {
      markFailedCalls += 1;
      markedPayload = call.options?.body as Record<string, unknown>;
      return ok(makeRefund({ id: "ref-unk-2", kind: "API", state: "FAILED" }));
    });
    fakeApi.post("/api/v1/admin/orders/{code}/payments/{paymentId}/refunds", (call) => {
      createPayload = call.options?.body as Record<string, unknown>;
      return ok(makeRefund({ id: "ref-man-1", kind: "MANUAL", state: "SUCCEEDED" }));
    });

    const { rerender } = renderOrdersUi(
      <RefundDialog open onOpenChange={vi.fn()} order={order} payment={paymentWithUnknown} />,
    );

    await user.click(await screen.findByRole("button", { name: "Đánh dấu thất bại thủ công" }));
    // Negative AC-13: blank evidenceRef/reason must block POST /mark-failed (R1-53)
    await user.click(screen.getByRole("button", { name: "Xác nhận đánh dấu thất bại" }));
    expect(screen.getByText("Vui lòng nhập mã đối soát hoặc chứng từ.")).toBeInTheDocument();
    expect(markFailedCalls).toBe(0);

    await user.type(screen.getByLabelText(/Mã đối soát \/ Chứng từ/), "BANK-ERR-01");
    await user.click(screen.getByRole("button", { name: "Xác nhận đánh dấu thất bại" }));
    expect(screen.getByText("Vui lòng nhập lý do hoàn tiền.")).toBeInTheDocument();
    expect(markFailedCalls).toBe(0);

    await user.type(screen.getByLabelText(/Lý do xác nhận thất bại/), "Merchant portal báo lỗi 99");
    await user.click(screen.getByRole("button", { name: "Xác nhận đánh dấu thất bại" }));

    await waitFor(() => {
      expect(markedPayload).toEqual({
        evidenceRef: "BANK-ERR-01",
        reason: "Merchant portal báo lỗi 99",
      });
    });

    // Simulate invalidated query returning payment with FAILED API refund and unlocked refundableAmountVnd (R1-20, R1-21)
    const paymentAfterMarkFailed = makePayment({
      refunds: [makeRefund({ id: "ref-unk-2", kind: "API", state: "FAILED" })],
      refundableAmountVnd: 299000,
    });
    rerender(
      <RefundDialog
        open
        onOpenChange={vi.fn()}
        order={{ ...order, payments: [paymentAfterMarkFailed] }}
        payment={paymentAfterMarkFailed}
      />,
    );

    const amountInput = await screen.findByLabelText(/Số tiền hoàn/);
    expect(amountInput).toHaveValue("299000");

    const manualRadio = await screen.findByRole("radio", { name: /Ghi nhận hoàn tiền thủ công/ });
    expect(manualRadio).toBeEnabled();
    await user.click(manualRadio);

    // Negative AC-14: blank externalRef blocks Step 2 (R1-53)
    await user.click(screen.getByRole("checkbox"));
    await user.type(screen.getByLabelText(/Lý do hoàn tiền/), "Chuyen khoan thu cong cho khach");
    await user.click(screen.getByRole("button", { name: "Tiếp tục xác nhận" }));
    expect(
      screen.getByText("Vui lòng nhập mã chứng từ hoặc mã tham chiếu chuyển khoản."),
    ).toBeInTheDocument();
    expect(screen.queryByText("Xác nhận lại thao tác ghi nhận hoàn thủ công")).toBeNull();

    await user.type(screen.getByLabelText(/Mã tham chiếu giao dịch hoàn ngoài hệ thống/), "VCB-889900");
    await user.click(screen.getByRole("button", { name: "Tiếp tục xác nhận" }));

    const step2Heading = await screen.findByText("Xác nhận lại thao tác ghi nhận hoàn thủ công");
    expect(step2Heading).toBeInTheDocument();
    expect(document.activeElement).toBe(step2Heading);

    await user.click(screen.getByRole("button", { name: "Xác nhận ghi nhận hoàn thủ công" }));

    await waitFor(() => {
      expect(createPayload).toMatchObject({
        amountVnd: 299000,
        kind: "MANUAL",
        verifiedSystemError: true,
        externalRef: "VCB-889900",
        reason: "Chuyen khoan thu cong cho khach",
      });
    });
  });

  it("chặn cả API và MANUAL bước 1->2 khi chưa tick verifiedSystemError với 0 API calls, và quay lại Step 1 khi Step 2 trả 422 (R1-8, R1-9, R1-37, R1-40, R1-53, R1-66)", async () => {
    const user = userEvent.setup();
    let postCalls = 0;
    const paymentWithFailedApi = makePayment({
      refunds: [makeRefund({ id: "ref-fail-1", kind: "API", state: "FAILED" })],
      refundableAmountVnd: 299000,
    });

    fakeApi.post("/api/v1/admin/orders/{code}/payments/{paymentId}/refunds", () => {
      postCalls += 1;
      return fail(422, { code: "REFUND_SYSTEM_ERROR_NOT_VERIFIED", message: "not verified" });
    });

    // First verify manual radio is disabled when no FAILED API refund exists (AC-14 negative)
    const { unmount } = renderOrdersUi(
      <RefundDialog
        open
        onOpenChange={vi.fn()}
        order={makeOrderDetail()}
        payment={makePayment({ refunds: [], refundableAmountVnd: 299000 })}
      />,
    );
    expect(
      await screen.findByRole("radio", { name: /Ghi nhận hoàn tiền thủ công/ }),
    ).toBeDisabled();
    unmount();

    renderOrdersUi(
      <RefundDialog
        open
        onOpenChange={vi.fn()}
        order={makeOrderDetail({ payments: [paymentWithFailedApi] })}
        payment={paymentWithFailedApi}
      />,
    );

    const dialog = await screen.findByRole("dialog");
    expect(dialog.getAttribute("aria-describedby")).toBeTruthy();

    // API submit blocked when checkbox unchecked
    await user.type(screen.getByLabelText(/Lý do hoàn tiền/), "Loi he thong");
    await user.click(screen.getByRole("button", { name: "Gửi yêu cầu hoàn tiền" }));
    expect(
      screen.getByText("Cần xác nhận lỗi hệ thống đã kiểm chứng trước khi tạo yêu cầu hoàn tiền."),
    ).toBeInTheDocument();
    expect(postCalls).toBe(0);
    expect(document.activeElement).toBe(screen.getByRole("checkbox"));

    // ArrowRight radio keyboard navigation moves focus to MANUAL radio (R1-37, R1-38, R1-40)
    const apiRadio = screen.getByRole("radio", { name: /Hoàn tiền tự động qua VNPAY/ });
    const manualRadio = screen.getByRole("radio", { name: /Ghi nhận hoàn tiền thủ công/ });
    expect(apiRadio.querySelector("svg")).not.toBeNull();
    apiRadio.focus();
    await user.keyboard("{ArrowRight}");
    expect(document.activeElement).toBe(manualRadio);
    expect(manualRadio).toHaveAttribute("aria-checked", "true");
    expect(manualRadio.querySelector("svg")).not.toBeNull();

    // MANUAL Step 1 -> Step 2 blocked when checkbox unchecked
    await user.type(screen.getByLabelText(/Mã tham chiếu giao dịch hoàn ngoài hệ thống/), "VCB-01");
    await user.click(screen.getByRole("button", { name: "Tiếp tục xác nhận" }));
    expect(screen.queryByText("Xác nhận lại thao tác ghi nhận hoàn thủ công")).toBeNull();
    expect(postCalls).toBe(0);

    // Check box -> Step 2 -> server 422 returns to Step 1 (R1-9)
    await user.click(screen.getByRole("checkbox"));
    await user.click(screen.getByRole("button", { name: "Tiếp tục xác nhận" }));
    expect(
      await screen.findByText("Xác nhận lại thao tác ghi nhận hoàn thủ công"),
    ).toBeInTheDocument();

    await user.click(screen.getByRole("button", { name: "Xác nhận ghi nhận hoàn thủ công" }));
    expect(
      await screen.findByText("Phải xác nhận lỗi hệ thống đã kiểm chứng trước khi hoàn tiền."),
    ).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Tiếp tục xác nhận" })).toBeInTheDocument();
    expect(postCalls).toBe(1);
  });

  it("hiện chế độ PROCESSING chỉ cho Tra cứu lại VNPAY (không Đánh dấu thất bại, không form hoàn) và chặn khi số còn hoàn = 0 (R1-7, R1-24)", async () => {
    const user = userEvent.setup();
    let queriedId = "";
    fakeApi.post("/api/v1/admin/refunds/{id}/query", (call) => {
      const params = call.options?.params as { path?: { id?: string } } | undefined;
      queriedId = params?.path?.id ?? "";
      return ok(makeRefund({ id: "ref-proc-1", state: "PROCESSING" }));
    });

    const procPayment = makePayment({
      refunds: [makeRefund({ id: "ref-proc-1", kind: "API", state: "PROCESSING" })],
      refundableAmountVnd: 0,
    });
    const { unmount } = renderOrdersUi(
      <RefundDialog
        open
        onOpenChange={vi.fn()}
        order={makeOrderDetail({ payments: [procPayment] })}
        payment={procPayment}
      />,
    );

    expect(await screen.findByText("Theo dõi yêu cầu hoàn tiền đang xử lý")).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Đánh dấu thất bại thủ công" })).toBeNull();
    expect(screen.queryByLabelText(/Số tiền hoàn/)).toBeNull();

    await user.click(screen.getByRole("button", { name: "Tra cứu lại trên VNPAY" }));
    await waitFor(() => {
      expect(queriedId).toBe("ref-proc-1");
    });
    unmount();

    // refundableAmountVnd === 0 with no active refund must show non-submittable notice
    const zeroPayment = makePayment({ refunds: [], refundableAmountVnd: 0, refundedAmountVnd: 299000 });
    renderOrdersUi(
      <RefundDialog
        open
        onOpenChange={vi.fn()}
        order={makeOrderDetail({ payments: [zeroPayment] })}
        payment={zeroPayment}
      />,
    );
    expect(await screen.findByText("Giao dịch không còn số dư để hoàn")).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Gửi yêu cầu hoàn tiền" })).toBeNull();
  });

  it("chỉ gửi 1 request khi bấm đúp nhanh và giữ cùng khóa Idempotency-Key khi thử lại cùng nội dung (AC-16 một khóa idempotency)", async () => {
    const user = userEvent.setup();
    const capturedKeys: string[] = [];
    let releaseFirst: (() => void) | undefined;

    fakeApi.post("/api/v1/admin/orders/{code}/payments/{paymentId}/refunds", async (call) => {
      const params = call.options?.params as { header?: Record<string, string> } | undefined;
      capturedKeys.push(params?.header?.["Idempotency-Key"] ?? "");
      if (capturedKeys.length === 1) {
        await new Promise<void>((resolve) => {
          releaseFirst = resolve;
        });
        return fail(500, { code: "INTERNAL_ERROR", message: "transient" });
      }
      return ok(makeRefund());
    });

    const onOpenChange = vi.fn();
    renderOrdersUi(
      <RefundDialog
        open
        onOpenChange={onOpenChange}
        order={makeOrderDetail()}
        payment={makePayment({ refundableAmountVnd: 299000 })}
      />,
    );

    await user.click(screen.getByRole("checkbox"));
    await user.type(screen.getByLabelText(/Lý do hoàn tiền/), "Hoan do loi he thong");
    const submitBtn = screen.getByRole("button", { name: "Gửi yêu cầu hoàn tiền" });

    // Rapid double click while first request is still in-flight must only send once
    await user.click(submitBtn);
    await user.click(submitBtn);
    expect(capturedKeys).toHaveLength(1);

    releaseFirst?.();
    await screen.findByText(/Hệ thống gặp sự cố tạm thời/);

    // Retry with identical payload must reuse exact same Idempotency-Key
    await user.click(submitBtn);

    await waitFor(() => {
      expect(capturedKeys).toHaveLength(2);
      expect(capturedKeys[0]).toBeTruthy();
      expect(capturedKeys[0]).toBe(capturedKeys[1]);
    });
  });
});
