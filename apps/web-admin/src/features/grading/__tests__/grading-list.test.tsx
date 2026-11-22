import { onlineManager, useQueryClient, type QueryClient } from "@tanstack/react-query";
import { screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { useEffect } from "react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { installContainerWidth, PHONE_CONTENT_WIDTH } from "@/test/container-width";
import { fail, fakeApi, ok, withRealApi } from "@/test/fake-api";
import { GradingListView } from "../components/grading-list-view";
import {
  ATTEMPT_1_ID,
  EXAM_KIND_CATALOG_FIXTURES,
  ITEM_1_ID,
  makeCounts,
  makeQueueItem,
  renderGradingUi,
} from "./grading-fixtures";

const routerMock = vi.hoisted(() => ({ push: vi.fn(), replace: vi.fn() }));
vi.mock("next/navigation", () => ({
  useRouter: () => routerMock,
  usePathname: () => "/grading",
  useSearchParams: () => new URLSearchParams(window.location.search),
}));
vi.mock("@/lib/api/client", async () => {
  const fake = await import("@/test/fake-api");
  return { createBrowserApiClient: fake.fakeBrowserApiClient };
});

withRealApi();

function setUrl(search: string) {
  window.history.replaceState(null, "", `/grading${search}`);
}

beforeEach(() => {
  setUrl("");
  routerMock.push.mockReset();
  fakeApi.get("/api/v1/catalog/options", ok(EXAM_KIND_CATALOG_FIXTURES));
});

afterEach(() => {
  setUrl("");
});

describe("GradingListView", () => {
  it("hiển thị hàng chờ ẩn danh (không lộ tên/username học viên) và lọc theo state, exam, kind, from, to", async () => {
    const item1 = {
      ...makeQueueItem({
        itemId: ITEM_1_ID,
        attemptId: ATTEMPT_1_ID,
        examName: "Đề thi BCA Ngữ văn 2026",
        examKindCode: "BCA",
        partName: "Phần Viết",
        displayNo: 25,
        state: "WAITING",
        regrading: true,
      }),
      learnerName: "Nguyễn Văn Bí Mật",
      username: "secret.student01",
      email: "secret@thi360.test",
    };
    const item2 = makeQueueItem({
      itemId: "item-2",
      attemptId: "12345678-0000-4000-8000-000000000000",
      examName: "Đề SPT Toán 2026",
      examKindCode: "SPT",
      partName: "Tự luận",
      displayNo: 12,
      state: "DONE",
      earnedPoints: 8.5,
      pointsMax: 10,
      graderName: "Giám khảo A",
    });

    const listQueries: Array<Record<string, unknown>> = [];
    fakeApi.get("/api/v1/admin/grading/items", (call) => {
      const q = ((call.options?.params as { query?: Record<string, unknown> })?.query ?? {});
      listQueries.push(q);
      return ok({ items: [item1, item2], page: 0, size: 20, totalItems: 2, totalPages: 1 });
    });
    fakeApi.get("/api/v1/admin/grading/items/counts", ok(makeCounts({ waiting: 1, inProgress: 0, done: 1, total: 2 })));

    renderGradingUi(<GradingListView />);

    const table = await screen.findByRole("table", { name: "Danh sách câu tự luận cần chấm" });
    expect(await within(table).findByText("Đề thi BCA Ngữ văn 2026")).toBeInTheDocument();
    expect(within(table).getByRole("link", { name: "#ABCDEF12 · Câu 25" })).toHaveAttribute(
      "href",
      `/grading/${ITEM_1_ID}`,
    );
    expect(within(table).getByText("Đang chấm lại")).toBeInTheDocument();

    // Không hiển thị bất kỳ tên học viên/tên đăng nhập/email nào và không có cột Học viên (chấm ẩn danh, AC-2).
    expect(within(table).queryByRole("columnheader", { name: /Học viên/i })).not.toBeInTheDocument();
    expect(document.body).not.toHaveTextContent("Nguyễn Văn Bí Mật");
    expect(document.body).not.toHaveTextContent("secret.student01");
    expect(document.body).not.toHaveTextContent("secret@thi360.test");

    // Bấm nút thao tác điều hướng tới /grading/[itemId].
    await userEvent.click(screen.getByRole("button", { name: "Chấm bài #ABCDEF12 · Câu 25" }));
    expect(routerMock.push).toHaveBeenCalledWith(`/grading/${ITEM_1_ID}`);

    // Lọc ngày from/to + tìm kiếm bài thi + URL sync.
    const fromInput = screen.getByLabelText("Từ ngày");
    const toInput = screen.getByLabelText("Đến ngày");
    await userEvent.type(fromInput, "2026-11-01");
    await userEvent.type(toInput, "2026-11-20");
    await userEvent.type(screen.getByLabelText("Bài thi"), "BCA Ngữ văn");

    await waitFor(() => {
      expect(window.location.search).toContain("from=2026-11-01");
      expect(window.location.search).toContain("to=2026-11-20");
      expect(listQueries.some((q) => q.exam === "BCA Ngữ văn" && q.from === "2026-11-01" && q.to === "2026-11-20")).toBe(
        true,
      );
    });
  });

  it("số đếm theo bộ lọc", async () => {
    const countsQueries: Array<Record<string, unknown>> = [];
    fakeApi.get("/api/v1/admin/grading/items", () =>
      ok({ items: [makeQueueItem()], page: 0, size: 20, totalItems: 1, totalPages: 1 }),
    );
    fakeApi.get("/api/v1/admin/grading/items/counts", (call) => {
      const q = ((call.options?.params as { query?: Record<string, unknown> })?.query ?? {});
      countsQueries.push(q);
      if (q.kind === "SPT") {
        return ok(makeCounts({ waiting: 1, inProgress: 1, done: 2, total: 4 }));
      }
      return ok(makeCounts({ waiting: 5, inProgress: 2, done: 3, total: 10 }));
    });

    renderGradingUi(<GradingListView />);

    const statusGroup = await screen.findByRole("group", { name: "Trạng thái" });
    await waitFor(() => {
      expect(within(statusGroup).getByRole("radio", { name: /Tất cả\s*10/ })).toBeChecked();
      expect(within(statusGroup).getByRole("radio", { name: /Chờ chấm\s*5/ })).toBeInTheDocument();
      expect(within(statusGroup).getByRole("radio", { name: /Đang chấm\s*2/ })).toBeInTheDocument();
      expect(within(statusGroup).getByRole("radio", { name: /Đã chấm\s*3/ })).toBeInTheDocument();
    });

    // Đổi state không truyền state vào API counts và giữ nguyên số đếm.
    await userEvent.click(within(statusGroup).getByRole("radio", { name: /Chờ chấm/ }));
    await waitFor(() => expect(window.location.search).toContain("state=WAITING"));
    expect(countsQueries.every((q) => !("state" in q))).toBe(true);

    // Đổi bộ lọc kỳ thi (kind) -> counts cập nhật theo bộ lọc mới (vẫn bỏ qua state).
    await userEvent.click(screen.getByRole("combobox", { name: "Kỳ thi" }));
    await userEvent.click(screen.getByRole("option", { name: "Danh gia nang luc DH Su pham (SPT)" }));

    await waitFor(() => {
      expect(within(statusGroup).getByRole("radio", { name: /Tất cả\s*4/ })).toBeInTheDocument();
      expect(within(statusGroup).getByRole("radio", { name: /Chờ chấm\s*1/ })).toBeInTheDocument();
      expect(within(statusGroup).getByRole("radio", { name: /Đang chấm\s*1/ })).toBeInTheDocument();
      expect(within(statusGroup).getByRole("radio", { name: /Đã chấm\s*2/ })).toBeInTheDocument();
    });
    expect(countsQueries.at(-1)).toEqual({ kind: "SPT" });
  });

  it("rỗng, lỗi", async () => {
    // 1. Loading skeleton
    fakeApi.pending("/api/v1/admin/grading/items");
    fakeApi.get("/api/v1/admin/grading/items/counts", ok(makeCounts({ waiting: 0, inProgress: 0, done: 0, total: 0 })));
    const { unmount } = renderGradingUi(<GradingListView />);
    const loadingTable = await screen.findByRole("table", { name: "Danh sách câu tự luận cần chấm" });
    expect(loadingTable).toHaveAttribute("aria-busy", "true");
    unmount();

    // 2. Empty unfiltered
    fakeApi.get("/api/v1/admin/grading/items", ok({ items: [], page: 0, size: 20, totalItems: 0, totalPages: 0 }));
    const { unmount: unmountEmpty } = renderGradingUi(<GradingListView />);
    expect(await screen.findByText("Chưa có câu tự luận cần chấm")).toBeInTheDocument();
    unmountEmpty();

    // 3. Empty filtered with clear button
    setUrl("?kind=BCA");
    const { unmount: unmountFiltered } = renderGradingUi(<GradingListView />);
    expect(await screen.findByText("Không có câu tự luận khớp bộ lọc")).toBeInTheDocument();
    await userEvent.click(within(screen.getByRole("table")).getByRole("button", { name: "Xóa tất cả bộ lọc" }));
    await waitFor(() => expect(window.location.search).toBe(""));
    unmountFiltered();

    // 4. Error + Retry recovers
    let calls = 0;
    fakeApi.get("/api/v1/admin/grading/items", () => {
      calls += 1;
      if (calls === 1) return fail(500, { code: "INTERNAL_ERROR" });
      return ok({ items: [makeQueueItem()], page: 0, size: 20, totalItems: 1, totalPages: 1 });
    });
    renderGradingUi(<GradingListView />);
    expect(await screen.findByText("Không tải được hàng chờ chấm")).toBeInTheDocument();
    await userEvent.click(screen.getByRole("button", { name: "Thử lại" }));
    expect(await screen.findByText("BCA Literature Mock 01")).toBeInTheDocument();
    expect(calls).toBe(2);
  });

  it("hiển thị song ngữ pickName ở locale en và bọc data-exam-content đúng phạm vi (A7.7)", async () => {
    const bcaItem = makeQueueItem({
      itemId: "item-bca",
      examName: "Đề BCA giữ nguyên tiếng Việt",
      partName: "Phần Tự luận giữ nguyên",
      examKindCode: "BCA",
    });
    const sptItem = makeQueueItem({
      itemId: "item-spt",
      attemptId: "11112222-3333-4444-8555-666677778888",
      examName: "Đề SPT giữ nguyên tiếng Việt",
      partName: "Phần Tự luận SPT",
      examKindCode: "SPT",
    });
    fakeApi.get(
      "/api/v1/admin/grading/items",
      ok({ items: [bcaItem, sptItem], page: 0, size: 20, totalItems: 2, totalPages: 1 }),
    );
    fakeApi.get("/api/v1/admin/grading/items/counts", ok(makeCounts({ waiting: 2, inProgress: 0, done: 0, total: 2 })));

    renderGradingUi(<GradingListView />, { locale: "en" });

    // Item có nameEn -> hiện nameEn; item có nameEn rỗng -> fallback nameVi; không bọc data-exam-content.
    const table = await screen.findByRole("table", { name: "Essay grading queue" });
    const bcaKind = await within(table).findByText("Public Security Admission (BCA)");
    expect(bcaKind.closest("[data-exam-content]")).toBeNull();
    const sptKind = within(table).getByText("Danh gia nang luc DH Su pham (SPT)");
    expect(sptKind.closest("[data-exam-content]")).toBeNull();

    // Nội dung bài thi / phần thi nằm trong data-exam-content; nhãn UI nằm ngoài.
    const examNameEl = screen.getByText("Đề BCA giữ nguyên tiếng Việt");
    expect(examNameEl.closest("[data-exam-content]")).not.toBeNull();
    const partNameEl = screen.getByText("Phần Tự luận giữ nguyên");
    expect(partNameEl.closest("[data-exam-content]")).not.toBeNull();
    expect(screen.getByRole("heading", { level: 1, name: "Essay grading" }).closest("[data-exam-content]")).toBeNull();
  });

  it("khung hẹp hiển thị ListCard ẩn danh", async () => {
    installContainerWidth(PHONE_CONTENT_WIDTH);
    fakeApi.get(
      "/api/v1/admin/grading/items",
      ok({ items: [makeQueueItem()], page: 0, size: 20, totalItems: 1, totalPages: 1 }),
    );
    fakeApi.get("/api/v1/admin/grading/items/counts", ok(makeCounts()));

    renderGradingUi(<GradingListView />);

    const link = await screen.findByRole("link", { name: "#ABCDEF12 · Câu 25" });
    expect(link).toHaveAttribute("href", `/grading/${ITEM_1_ID}`);
    expect(screen.queryByRole("table")).not.toBeInTheDocument();
  });

  it("ẩn danh sách cũ và phân trang khi ngoại tuyến, và Thử lại gọi lại cả list và counts (R1-26, R1-44)", async () => {
    let qcRef: QueryClient | null = null;
    function CaptureClient() {
      const qc = useQueryClient();
      useEffect(() => {
        qcRef = qc;
      }, [qc]);
      return null;
    }

    let listCalls = 0;
    let countsCalls = 0;
    let failRequests = false;
    fakeApi.get("/api/v1/admin/grading/items", () => {
      listCalls += 1;
      if (failRequests) return fail(500, { code: "INTERNAL_ERROR" });
      return ok({ items: [makeQueueItem()], page: 0, size: 20, totalItems: 30, totalPages: 2 });
    });
    fakeApi.get("/api/v1/admin/grading/items/counts", () => {
      countsCalls += 1;
      return ok(makeCounts());
    });

    renderGradingUi(
      <>
        <CaptureClient />
        <GradingListView />
      </>,
    );

    await screen.findByRole("link", { name: "#ABCDEF12 · Câu 25" });
    expect(screen.getByRole("navigation", { name: "Phân trang hàng chờ chấm" })).toBeInTheDocument();

    try {
      onlineManager.setOnline(false);
      await userEvent.click(screen.getByRole("radio", { name: /Đã chấm/ }));

      expect(
        await screen.findByText(
          "Máy đang mất kết nối nên chưa tải được danh sách. Danh sách tự tải lại khi có mạng, hoặc bấm Thử lại.",
        ),
      ).toBeInTheDocument();
      expect(screen.getAllByText("Mất kết nối mạng").length).toBeGreaterThanOrEqual(2);
      expect(screen.queryByRole("link", { name: "#ABCDEF12 · Câu 25" })).not.toBeInTheDocument();
      expect(
        screen.queryByRole("navigation", { name: "Phân trang hàng chờ chấm" }),
      ).not.toBeInTheDocument();
      expect(qcRef).not.toBeNull();
      failRequests = true;
    } finally {
      onlineManager.setOnline(true);
    }

    // Sau khi có mạng trở lại và gặp lỗi 500, bấm Thử lại phải gọi lại cả items lẫn counts (R1-44).
    expect(await screen.findByText("Không tải được hàng chờ chấm")).toBeInTheDocument();
    const prevList = listCalls;
    const prevCounts = countsCalls;
    failRequests = false;
    await userEvent.click(screen.getByRole("button", { name: "Thử lại" }));
    await waitFor(() => {
      expect(listCalls).toBeGreaterThan(prevList);
      expect(countsCalls).toBeGreaterThan(prevCounts);
    });
    expect(await screen.findByRole("link", { name: "#ABCDEF12 · Câu 25" })).toBeInTheDocument();
  });

  it("hiển thị tối đa 4 chữ số thập phân cho câu DONE ở danh sách (R2-27)", async () => {
    fakeApi.get(
      "/api/v1/admin/grading/items",
      ok({
        items: [
          makeQueueItem({
            state: "DONE",
            earnedPoints: 3.1234,
            pointsMax: 10,
            graderName: "Giám khảo B",
          }),
        ],
        page: 0,
        size: 20,
        totalItems: 1,
        totalPages: 1,
      }),
    );
    fakeApi.get("/api/v1/admin/grading/items/counts", ok(makeCounts({ done: 1, total: 1 })));

    renderGradingUi(<GradingListView />);
    expect(await screen.findByText("3,1234 / 10")).toBeInTheDocument();
  });

  it("phục hồi cả danh sách, counts lẫn nhãn + dropdown loại kỳ thi từ /api/v1/catalog/options khi bấm Thử lại sau lỗi kép (R3-18)", async () => {
    let listCalls = 0;
    let countsCalls = 0;
    let catalogCalls = 0;
    let outage = true;

    fakeApi.get("/api/v1/catalog/options", () => {
      catalogCalls += 1;
      if (outage) return fail(500, { code: "INTERNAL_ERROR" });
      return ok(EXAM_KIND_CATALOG_FIXTURES);
    });
    fakeApi.get("/api/v1/admin/grading/items", () => {
      listCalls += 1;
      if (outage) return fail(500, { code: "INTERNAL_ERROR" });
      return ok({
        items: [makeQueueItem({ examName: "Đề BCA phục hồi", examKindCode: "BCA" })],
        page: 0,
        size: 20,
        totalItems: 1,
        totalPages: 1,
      });
    });
    fakeApi.get("/api/v1/admin/grading/items/counts", () => {
      countsCalls += 1;
      return outage ? fail(500, { code: "INTERNAL_ERROR" }) : ok(makeCounts({ waiting: 1, total: 1 }));
    });

    renderGradingUi(<GradingListView />, { locale: "en" });
    expect(await screen.findByText("Could not load grading queue")).toBeInTheDocument();
    expect(listCalls).toBe(1);
    expect(countsCalls).toBe(1);
    expect(catalogCalls).toBe(1);

    outage = false;
    await userEvent.click(screen.getByRole("button", { name: "Try again" }));
    const table = await screen.findByRole("table", { name: "Essay grading queue" });
    expect(await within(table).findByText("Đề BCA phục hồi")).toBeInTheDocument();
    expect(await within(table).findByText("Public Security Admission (BCA)")).toBeInTheDocument();
    expect(listCalls).toBe(2);
    expect(countsCalls).toBe(2);
    expect(catalogCalls).toBe(2);

    // Dropdown Exam type cũng được nạp lại sau Retry.
    await userEvent.click(screen.getByRole("combobox", { name: "Exam type" }));
    expect(await screen.findByRole("option", { name: "Public Security Admission (BCA)" })).toBeInTheDocument();
  });
});
