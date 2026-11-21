import { screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { fail, fakeApi, ok, withRealApi } from "@/test/fake-api";

import { OrderDetailView } from "../components/order-detail-view";
import {
  makeOrderDetail,
  makePayment,
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

describe("Late success review and excess payment refund (AC-24, AC-25)", () => {
  beforeEach(() => {
    fakeApi.reset();
    resetMockNavigation("/orders/DH-261121-0001", "");
  });

  it("gửi quyết định ACCEPT hoặc REFUND cho tiền về muộn và xử lý 409 LATE_SUCCESS_ALREADY_RESOLVED (AC-24 đối soát)", async () => {
    const user = userEvent.setup();
    let reviewBody: Record<string, unknown> | undefined;
    let firstCall = true;

    const latePayment = makePayment({
      id: "pay-late-1",
      txnRef: "TXN-LATE-01",
      lateSuccessAt: "2026-11-21T04:00:00Z",
      lateSuccessReview: "PENDING",
    });

    fakeApi.get("/api/v1/admin/orders/{code}", () =>
      ok(
        makeOrderDetail({
          state: "EXPIRED",
          payments: [latePayment],
        }),
      ),
    );

    fakeApi.post("/api/v1/admin/orders/{code}/payments/{paymentId}/late-success", (call) => {
      reviewBody = call.options?.body as Record<string, unknown>;
      if (firstCall) {
        firstCall = false;
        return fail(409, { code: "LATE_SUCCESS_ALREADY_RESOLVED", message: "resolved" });
      }
      return ok(
        makeOrderDetail({
          state: "FULFILLED",
          payments: [makePayment({ ...latePayment, lateSuccessReview: "ACCEPTED" })],
        }),
      );
    });

    renderOrdersUi(<OrderDetailView code="DH-261121-0001" />);

    await user.click((await screen.findAllByRole("button", { name: "Xử lý tiền về muộn" }))[0]!);

    const dialog = await screen.findByRole("dialog");
    expect(within(dialog).getByText("Xử lý tiền về muộn")).toBeInTheDocument();
    await user.type(screen.getByLabelText(/Mã đối soát \/ Chứng từ/), "DS-20261121-09");
    await user.type(screen.getByLabelText(/Lý do xử lý/), "Đơn chưa có đơn thay thế");
    await user.click(screen.getByRole("button", { name: "Xác nhận quyết định" }));

    expect(
      await screen.findByText(
        "Giao dịch tiền về muộn này đã được xử lý trước đó. Dữ liệu đã được làm mới.",
      ),
    ).toBeInTheDocument();

    await user.click(screen.getByRole("radio", { name: /Từ chối cấp quyền và hoàn tiền/ }));
    await user.click(screen.getByRole("button", { name: "Xác nhận quyết định" }));

    await waitFor(() => {
      expect(reviewBody).toEqual({
        decision: "REFUND",
        evidenceRef: "DS-20261121-09",
        reason: "Đơn chưa có đơn thay thế",
      });
    });
  });

  it("phân biệt giao dịch ghi nhận đơn và khoản thu thừa, đồng thời hiện cảnh báo khoản thu thừa trong dialog hoàn tiền (AC-25 hoàn khoản thu thừa)", async () => {
    const user = userEvent.setup();
    const countedPayment = makePayment({
      id: "pay-counted",
      attemptSeq: 1,
      txnRef: "TXN-COUNTED-01",
      duplicate: false,
      refundableAmountVnd: 0,
      refundedAmountVnd: 0,
    });
    const excessPayment = makePayment({
      id: "pay-excess",
      attemptSeq: 2,
      txnRef: "TXN-EXCESS-02",
      duplicate: true,
      excessReason: "AFTER_COUNTED",
      refundableAmountVnd: 299000,
    });

    fakeApi.get("/api/v1/admin/orders/{code}", () =>
      ok(
        makeOrderDetail({
          payments: [countedPayment, excessPayment],
        }),
      ),
    );

    renderOrdersUi(<OrderDetailView code="DH-261121-0001" />);

    expect(await screen.findByText("Giao dịch ghi nhận đơn")).toBeInTheDocument();
    expect(screen.getByText("Thu thừa (cần hoàn)")).toBeInTheDocument();

    const refundButtons = screen.getAllByRole("button", { name: "Hoàn tiền" });
    await user.click(refundButtons[0]!);

    expect(await screen.findByText("Khoản thu thừa cần hoàn trả")).toBeInTheDocument();
    expect(
      screen.getByText("Thu tiền lần thứ hai sau khi đơn đã ghi nhận đủ"),
    ).toBeInTheDocument();
    expect(
      screen.getByText(/giữ quyền truy cập bài thi và không bị thu hồi các lượt thi hiện có/),
    ).toBeInTheDocument();
  });

  it("chế độ en: cảnh báo khoản thu thừa nêu rõ giữ quyền và không thu hồi lượt thi hiện có (AC-25)", async () => {
    const user = userEvent.setup();
    const excessPayment = makePayment({
      id: "pay-excess-en",
      attemptSeq: 2,
      txnRef: "TXN-EXCESS-EN",
      duplicate: true,
      excessReason: "AFTER_COUNTED",
      refundableAmountVnd: 299000,
    });

    fakeApi.get("/api/v1/admin/orders/{code}", () =>
      ok(makeOrderDetail({ payments: [excessPayment] })),
    );

    renderOrdersUi(<OrderDetailView code="DH-261121-0001" />, { locale: "en" });

    await user.click((await screen.findAllByRole("button", { name: "Refund" }))[0]!);
    expect(await screen.findByText("Excess payment requiring refund")).toBeInTheDocument();
    expect(
      screen.getByText(/keeps the buyer's exam access active and existing granted attempts are not revoked/),
    ).toBeInTheDocument();
  });
});
