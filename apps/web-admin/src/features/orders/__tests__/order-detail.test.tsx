import { screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { fail, fakeApi, ok, withRealApi } from "@/test/fake-api";

import { OrderDetailView } from "../components/order-detail-view";
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
});
