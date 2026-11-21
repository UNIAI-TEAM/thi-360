import { act, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

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

  afterEach(() => {
    onlineManager.setOnline(true);
  });

  it("hiện đúng 9 cột theo thứ tự thiết kế khi có dữ liệu (AC-1 cột gộp thời gian và nút icon Xem)", async () => {
    const orderNewest = makeOrderSummary({
      orderId: "order-2",
      code: "DH-261121-0099",
      buyerFullName: "Tran Minh",
      buyerUsername: "minh.tran",
      productName: "De thi VSTEP B2",
      productCode: "VSTEP-B2",
      amountDueVnd: 299000,
    });
    const orderOlder = makeOrderSummary({
      orderId: "order-1",
      code: "DH-261120-0010",
      buyerFullName: "Le Hoang",
      buyerUsername: "hoang.le",
    });

    let firstQuery: Record<string, unknown> | undefined;
    fakeApi.get("/api/v1/admin/exams", () => ok({ items: [] }));
    fakeApi.get("/api/v1/admin/orders", (call) => {
      const params = call.options?.params as { query?: Record<string, unknown> } | undefined;
      firstQuery = params?.query;
      return ok(makeOrderListResponse([orderNewest, orderOlder]));
    });

    renderOrdersUi(<OrderListView />);

    expect(await screen.findByText("DH-261121-0099")).toBeInTheDocument();
    expect(firstQuery).toMatchObject({ sort: "createdAt,desc", page: 0, size: 20 });

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
      "Thời gian",
      "Thao tác",
    ]);

    const bodyRows = Array.from(table.querySelectorAll("tbody tr"));
    expect(bodyRows[0]).toHaveTextContent("DH-261121-0099");
    expect(bodyRows[1]).toHaveTextContent("DH-261120-0010");
    expect(screen.getAllByText("Tổng 2 đơn hàng").length).toBeGreaterThanOrEqual(1);

    expect(within(table).getByText("Tran Minh")).toBeInTheDocument();
    expect(within(table).getByText("De thi VSTEP B2")).toBeInTheDocument();

    const timestampsCell = table.querySelector("tbody [data-col='timestamps']");
    expect(timestampsCell).not.toBeNull();
    expect(timestampsCell).toHaveTextContent("Tạo lúc:");
    expect(timestampsCell).toHaveTextContent("Thanh toán:");

    const viewIconLink = within(table).getByRole("link", {
      name: "Xem giao dịch đơn DH-261121-0099",
    });
    expect(viewIconLink).toHaveAttribute("href", "/orders/DH-261121-0099");
    expect(table.querySelector("[data-col-summary='timestamps']")).not.toBeNull();
  });

  it("gắn data-exam-content chỉ lên các option bài thi động trong bộ lọc, không gắn lên Tất cả bài thi (D-1)", async () => {
    fakeApi.get("/api/v1/admin/exams", () =>
      ok({
        items: [
          { id: "exam-1", code: "DS-HSA-01", name: "Đề thi thử HSA số 1" },
          { id: "exam-2", code: "DS-QDA-01", name: "Đề thi thử QDA số 1" },
        ],
      }),
    );
    fakeApi.get("/api/v1/admin/orders", () => ok(makeOrderListResponse([])));

    const { container } = renderOrdersUi(<OrderListView />);

    await waitFor(() => {
      const dynamicOption = container.querySelector('select option[value="exam-1"]');
      expect(dynamicOption).not.toBeNull();
      expect(dynamicOption).toHaveAttribute("data-exam-content");
    });

    const allOption = container.querySelector('select option[value="__ALL__"]');
    expect(allOption).not.toBeNull();
    expect(allOption).not.toHaveAttribute("data-exam-content");
    expect(container.querySelector('select option[value="exam-2"]')).toHaveAttribute(
      "data-exam-content",
    );
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
    expect(searchInput).toHaveAttribute("maxlength", "200");
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

    const toInput = screen.getByLabelText("Đến ngày");
    await user.type(toInput, "2026-11-21");

    await waitFor(() => {
      expect(seenQueries.some((q) => q.to === "2026-11-21T16:59:59.999Z")).toBe(true);
    });

    // Exam, paymentStatus, refundStatus, paymentMethod, attention & Reset (R1-52)
    await user.click(screen.getByRole("combobox", { name: "Bài thi" }));
    await user.click(await screen.findByRole("option", { name: "IELTS Test 01 (EXAM-01)" }));
    await waitFor(() => {
      expect(
        seenQueries.some((q) => q.examId === "019a0000-0000-7000-8000-000000000201"),
      ).toBe(true);
    });

    await user.click(screen.getByRole("combobox", { name: "Thanh toán" }));
    await user.click(await screen.findByRole("option", { name: "Thành công" }));
    await waitFor(() => {
      expect(seenQueries.some((q) => q.paymentStatus === "SUCCEEDED")).toBe(true);
    });

    await user.click(screen.getByRole("combobox", { name: "Hoàn tiền" }));
    await user.click(await screen.findByRole("option", { name: "Đã hoàn một phần" }));
    await waitFor(() => {
      expect(seenQueries.some((q) => q.refundStatus === "PARTIAL")).toBe(true);
    });

    await user.click(screen.getByRole("combobox", { name: "Hình thức thu" }));
    await user.click(await screen.findByRole("option", { name: "VNPAY" }));
    await waitFor(() => {
      expect(seenQueries.some((q) => q.paymentMethod === "VNPAY")).toBe(true);
    });

    // Reset clears all active filters
    await user.click(screen.getByRole("button", { name: "Đặt lại" }));
    await waitFor(() => {
      const latest = seenQueries[seenQueries.length - 1]!;
      expect(latest.q).toBeUndefined();
      expect(latest.examId).toBeUndefined();
      expect(latest.paymentStatus).toBeUndefined();
      expect(latest.refundStatus).toBeUndefined();
      expect(latest.paymentMethod).toBeUndefined();
    });
  });

  it("giữ đồng thời từ khóa tìm kiếm và bộ lọc bấm trong vòng 300ms, và không ghi đè URL ?q= sau khi xóa tìm kiếm (R1-10, R1-11, R1-12)", async () => {
    const user = userEvent.setup();
    const seenQueries: Array<Record<string, unknown>> = [];

    fakeApi.get("/api/v1/admin/exams", () => ok({ items: [] }));
    fakeApi.get("/api/v1/admin/orders", (call) => {
      const params = call.options?.params as { query?: Record<string, unknown> } | undefined;
      seenQueries.push(params?.query ?? {});
      return ok(makeOrderListResponse([makeOrderSummary()]));
    });

    renderOrdersUi(<OrderListView />);
    await screen.findByText("DH-261121-0001");

    const searchInput = screen.getByRole("searchbox", { name: "Tìm đơn hàng" });
    await user.type(searchInput, "DH26");
    // Immediately click paymentStatus filter within 300ms debounce window
    await user.click(screen.getByRole("combobox", { name: "Thanh toán" }));
    await user.click(await screen.findByRole("option", { name: "Thành công" }));

    await waitFor(
      () => {
        expect(
          seenQueries.some((q) => q.q === "DH26" && q.paymentStatus === "SUCCEEDED"),
        ).toBe(true);
      },
      { timeout: 1500 },
    );

    // Clear search, then simulate external URL navigation to ?q=buyer1
    await user.clear(searchInput);
    await waitFor(() => {
      expect(seenQueries[seenQueries.length - 1]?.q).toBeUndefined();
    });

    act(() => {
      resetMockNavigation("/orders", "?q=buyer1");
    });

    await waitFor(() => {
      expect(searchInput).toHaveValue("buyer1");
      expect(seenQueries.some((q) => q.q === "buyer1")).toBe(true);
    });
  });

  it("gắn query.attention khi bấm chip Cần xử lý và sống sót khi /reconciliation-reports/latest lỗi 500 (AC-3, AC-22, R1-52, R1-55)", async () => {
    const user = userEvent.setup();
    const seenQueries: Array<Record<string, unknown>> = [];

    fakeApi.get("/api/v1/admin/reconciliation-reports/latest", () =>
      fail(500, { code: "INTERNAL_ERROR", message: "reconcile down" }),
    );
    fakeApi.get("/api/v1/admin/exams", () => ok({ items: [] }));
    fakeApi.get("/api/v1/admin/orders", (call) => {
      const params = call.options?.params as { query?: Record<string, unknown> } | undefined;
      seenQueries.push(params?.query ?? {});
      return ok(
        makeOrderListResponse([makeOrderSummary()], {
          attention: makeAttentionSummary({
            paidUnfulfilled: 2,
            totalAttentionOrders: 2,
          }),
        }),
      );
    });

    renderOrdersUi(<OrderListView />);

    expect(
      await screen.findByText("Không tải được báo cáo đối soát gần nhất."),
    ).toBeInTheDocument();
    expect(await screen.findByText("DH-261121-0001")).toBeInTheDocument();

    await user.click(screen.getByRole("button", { name: /Đã thu tiền chưa cấp quyền/ }));
    await waitFor(() => {
      expect(seenQueries.some((q) => q.attention === "PAID_UNFULFILLED")).toBe(true);
    });
  });

  it("xử lý tham số URL bất thường: fallback examId thiếu, auto-clamp ?page=5, chuẩn hóa sort/date sai (R1-26, R1-27, R1-28, R1-30, R1-31)", async () => {
    const seenQueries: Array<Record<string, unknown>> = [];

    fakeApi.get("/api/v1/admin/exams", () => ok({ items: [] }));
    fakeApi.get("/api/v1/admin/orders", (call) => {
      const params = call.options?.params as { query?: Record<string, unknown> } | undefined;
      seenQueries.push(params?.query ?? {});
      const page = Number(params?.query?.page ?? 0);
      return ok(
        makeOrderListResponse(page === 0 ? [makeOrderSummary()] : [], {
          page,
          totalItems: 1,
          totalPages: 1,
        }),
      );
    });

    resetMockNavigation(
      "/orders",
      "?examId=missing-id&page=5&sort=invalid,desc&from=notadate&to=2026-02-31",
    );
    const { container } = renderOrdersUi(<OrderListView />);

    await waitFor(() => {
      const fallbackOption = container.querySelector('select option[value="missing-id"]');
      expect(fallbackOption).not.toBeNull();
      expect(fallbackOption).toHaveAttribute("data-exam-content");
    });

    expect(await screen.findByText("DH-261121-0001")).toBeInTheDocument();
    expect(seenQueries.some((q) => q.page === 0 && q.sort === "createdAt,desc")).toBe(true);
    for (const q of seenQueries) {
      expect(q.from).toBeUndefined();
      expect(q.to).toBeUndefined();
      expect(q.sort).toBe("createdAt,desc");
    }
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

  it("hiện lỗi tải danh sách và trạng thái ngoại tuyến khi tải mới hoặc đổi bộ lọc offline (AC-19, R1-39, R1-42)", async () => {
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

    const { unmount } = renderOrdersUi(<OrderListView />);

    expect(await screen.findByText("Không tải được đơn hàng")).toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: "Thử lại" }));

    expect(await screen.findByText("DH-261121-0001")).toBeInTheDocument();

    // Switching filter while offline must show offline state instead of dimmed stale rows (R1-42)
    try {
      act(() => {
        onlineManager.setOnline(false);
      });
      await user.click(screen.getByRole("combobox", { name: "Thanh toán" }));
      await user.click(await screen.findByRole("option", { name: "Thành công" }));
      expect(await screen.findByText("Đang ngoại tuyến")).toBeInTheDocument();
      unmount();

      resetMockNavigation("/orders", "?q=OFFLINE_CHECK");
      renderOrdersUi(<OrderListView />);
      expect(await screen.findByText("Đang ngoại tuyến")).toBeInTheDocument();
    } finally {
      act(() => {
        onlineManager.setOnline(true);
      });
    }
  });
});
