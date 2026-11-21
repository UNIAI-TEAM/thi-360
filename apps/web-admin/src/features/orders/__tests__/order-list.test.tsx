import { act, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { onlineManager } from "@tanstack/react-query";

import { fail, fakeApi, ok, withRealApi } from "@/test/fake-api";

import { OrderListView } from "../components/order-list-view";
import {
  makeAttentionSummary,
  makeOrderListResponse,
  makeOrderSummary,
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

describe("OrderListView (AC-1, AC-2, AC-18, AC-19)", () => {
  beforeEach(() => {
    fakeApi.reset();
    resetMockNavigation("/orders", "");
    onlineManager.setOnline(true);
    fakeApi.get("/api/v1/admin/reconciliation-reports/latest", () => ok({ available: false }));
    fakeApi.get("/api/v1/admin/orders/attention", () => ok(makeAttentionSummary()));
  });

  it("hiện đúng 10 cột theo thứ tự thiết kế khi có dữ liệu (AC-1 cột và thứ tự)", async () => {
    const order = makeOrderSummary({
      code: "DH-261121-0099",
      buyerFullName: "Tran Minh",
      buyerUsername: "minh.tran",
      productName: "De thi VSTEP B2",
      productCode: "VSTEP-B2",
      amountDueVnd: 299000,
    });

    fakeApi.get("/api/v1/admin/exams", () => ok({ items: [] }));
    fakeApi.get("/api/v1/admin/orders", () => ok(makeOrderListResponse([order])));

    renderOrdersUi(<OrderListView />);

    expect(await screen.findByText("DH-261121-0099")).toBeInTheDocument();
    const table = screen.getByRole("table", { name: "Bảng danh sách đơn hàng" });
    const headers = within(table)
      .getAllByRole("columnheader")
      .map((cell) => cell.textContent?.trim());

    expect(headers).toEqual([
      "Mã đơn",
      "Người mua",
      "Bài thi / Sản phẩm",
      "Số tiền",
      "Trạng thái đơn",
      "Thanh toán",
      "Hoàn tiền",
      "Tạo lúc",
      "Thanh toán lúc",
      "Thao tác",
    ]);

    expect(within(table).getByText("Tran Minh")).toBeInTheDocument();
    expect(within(table).getByText("De thi VSTEP B2")).toBeInTheDocument();
  });

  it("hỗ trợ tìm kiếm debounce, lọc ngày VN sang UTC, và fallback danh sách bài thi khi thiếu EXAM.VIEW (AC-2 tìm và lọc)", async () => {
    const user = userEvent.setup();
    const seenQueries: Array<Record<string, unknown>> = [];
    let calledAdminExams = false;

    fakeApi.get("/api/v1/admin/exams", () => {
      calledAdminExams = true;
      return fail(403, { code: "FORBIDDEN", message: "forbidden" });
    });

    fakeApi.get("/api/v1/admin/orders", (call) => {
      const params = call.options?.params as { query?: Record<string, unknown> } | undefined;
      seenQueries.push(params?.query ?? {});
      return ok(
        makeOrderListResponse([
          makeOrderSummary({
            code: "DH-261121-0001",
            examId: "019a0000-0000-7000-8000-000000000201",
            productCode: "EXAM-01",
            productName: "IELTS Test 01",
          }),
        ]),
      );
    });

    renderOrdersUi(<OrderListView />, {
      permissions: ["ORDER.VIEW"],
    });

    await screen.findByText("DH-261121-0001");
    expect(calledAdminExams).toBe(false);

    const searchInput = screen.getByRole("searchbox", { name: "Tìm đơn hàng" });
    await user.type(searchInput, "DH-2611");

    await waitFor(
      () => {
        expect(seenQueries.some((q) => q.q === "DH-2611")).toBe(true);
      },
      { timeout: 1500 },
    );

    const fromInput = screen.getByLabelText("Từ ngày");
    await user.type(fromInput, "2026-11-21");

    await waitFor(() => {
      expect(seenQueries.some((q) => q.from === "2026-11-20T17:00:00.000Z")).toBe(true);
    });
  });

  it("hiện trạng thái rỗng ban đầu và rỗng khi lọc không khớp (AC-18 rỗng)", async () => {
    const user = userEvent.setup();
    fakeApi.get("/api/v1/admin/exams", () => ok({ items: [] }));
    fakeApi.get("/api/v1/admin/orders", () => ok(makeOrderListResponse([])));

    renderOrdersUi(<OrderListView />);
    expect(await screen.findByText("Chưa có đơn hàng")).toBeInTheDocument();

    const searchInput = screen.getByRole("searchbox", { name: "Tìm đơn hàng" });
    await user.type(searchInput, "KHONGCO");

    expect(await screen.findByText("Không có đơn hàng phù hợp")).toBeInTheDocument();
  });

  it("hiện lỗi tải danh sách và trạng thái ngoại tuyến với nút thử lại (AC-19 lỗi tải)", async () => {
    const user = userEvent.setup();
    let failFirst = true;
    fakeApi.get("/api/v1/admin/exams", () => ok({ items: [] }));
    fakeApi.get("/api/v1/admin/orders", () => {
      if (failFirst) {
        failFirst = false;
        return fail(500, { code: "INTERNAL_ERROR", message: "db down" });
      }
      return ok(makeOrderListResponse([makeOrderSummary()]));
    });

    renderOrdersUi(<OrderListView />);

    expect(await screen.findByText("Không tải được đơn hàng")).toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: "Thử lại" }));

    expect(await screen.findByText("DH-261121-0001")).toBeInTheDocument();

    act(() => {
      onlineManager.setOnline(false);
    });
    resetMockNavigation("/orders", "?q=OFFLINE_CHECK");

    renderOrdersUi(<OrderListView />);
    expect(await screen.findByText("Đang ngoại tuyến")).toBeInTheDocument();
  });
});
