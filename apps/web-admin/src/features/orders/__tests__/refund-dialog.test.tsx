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

  it("hiện chế độ UNKNOWN cho phép Tra cứu lại trên VNPAY khi refund UNKNOWN (AC-12 UNKNOWN)", async () => {
    const user = userEvent.setup();
    const queriedIds: string[] = [];
    const unknownRefund = makeRefund({ id: "ref-unknown-1", state: "UNKNOWN" });
    const payment = makePayment({ refunds: [unknownRefund], refundableAmountVnd: 0 });
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
    await user.click(screen.getByRole("button", { name: "Tra cứu lại trên VNPAY" }));

    await waitFor(() => {
      expect(queriedIds).toEqual(["ref-unknown-1"]);
      expect(onOpenChange).toHaveBeenCalledWith(false);
    });
  });

  it("mở khóa radio Ghi nhận hoàn thủ công sau khi đánh dấu thất bại giao dịch UNKNOWN và bắt xác nhận 2 bước (AC-13, AC-14)", async () => {
    const user = userEvent.setup();
    const order = makeOrderDetail();
    const unknownRefund = makeRefund({ id: "ref-unk-2", kind: "API", state: "UNKNOWN" });
    const paymentWithUnknown = makePayment({
      refunds: [unknownRefund],
      refundableAmountVnd: 299000,
    });

    let markedPayload: Record<string, unknown> | undefined;
    let createPayload: Record<string, unknown> | undefined;

    fakeApi.post("/api/v1/admin/refunds/{id}/mark-failed", (call) => {
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
    await user.type(screen.getByLabelText(/Mã đối soát \/ Chứng từ/), "BANK-ERR-01");
    await user.type(screen.getByLabelText(/Lý do xác nhận thất bại/), "Merchant portal báo lỗi 99");
    await user.click(screen.getByRole("button", { name: "Xác nhận đánh dấu thất bại" }));

    await waitFor(() => {
      expect(markedPayload).toEqual({
        evidenceRef: "BANK-ERR-01",
        reason: "Merchant portal báo lỗi 99",
      });
    });

    // Simulate invalidated query returning payment with FAILED API refund
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

    const manualRadio = await screen.findByRole("radio", { name: /Ghi nhận hoàn tiền thủ công/ });
    expect(manualRadio).toBeEnabled();
    await user.click(manualRadio);

    await user.type(screen.getByLabelText(/Mã tham chiếu giao dịch hoàn ngoài hệ thống/), "VCB-889900");
    await user.type(screen.getByLabelText(/Lý do hoàn tiền/), "Chuyen khoan thu cong cho khach");
    await user.click(screen.getByRole("button", { name: "Tiếp tục xác nhận" }));

    expect(
      await screen.findByText("Xác nhận lại thao tác ghi nhận hoàn thủ công"),
    ).toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: "Xác nhận ghi nhận hoàn thủ công" }));

    await waitFor(() => {
      expect(createPayload).toMatchObject({
        amountVnd: 299000,
        kind: "MANUAL",
        externalRef: "VCB-889900",
        reason: "Chuyen khoan thu cong cho khach",
      });
    });
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
