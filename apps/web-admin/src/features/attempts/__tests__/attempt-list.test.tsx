import { onlineManager } from "@tanstack/react-query";
import { screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { installContainerWidth, PHONE_CONTENT_WIDTH } from "@/test/container-width";
import { fail, fakeApi, ok, withRealApi } from "@/test/fake-api";
import { AttemptListSkeleton, AttemptListView } from "../components/attempt-list-view";
import {
  ATTEMPT_1_ID,
  ATTEMPT_EXAM_KIND_FIXTURES,
  makeAttemptSummary,
  renderAttemptsUi,
} from "./attempt-fixtures";

const routerMock = vi.hoisted(() => ({ push: vi.fn(), replace: vi.fn() }));
vi.mock("next/navigation", () => ({
  useRouter: () => routerMock,
  usePathname: () => "/attempts",
  useSearchParams: () => new URLSearchParams(window.location.search),
}));
vi.mock("@/lib/api/client", async () => {
  const fake = await import("@/test/fake-api");
  return { createBrowserApiClient: fake.fakeBrowserApiClient };
});

withRealApi();

function setUrl(search: string) {
  window.history.replaceState(null, "", `/attempts${search}`);
}

beforeEach(() => {
  setUrl("");
  routerMock.push.mockReset();
  fakeApi.get(
    "/api/v1/catalog/options",
    ok(
      ATTEMPT_EXAM_KIND_FIXTURES.map((k) =>
        k.code === "SPT" ? { ...k, nameVi: "Đánh giá năng lực ĐH Sư phạm (SPT)" } : k,
      ),
    ),
  );
});

afterEach(() => {
  setUrl("");
  vi.unstubAllGlobals();
  vi.restoreAllMocks();
});

describe("AttemptListView", () => {
  it("lọc theo user, exam, state, from, to, phân trang và bọc data-exam-content / pickName (AC-10, A7.7)", async () => {
    const scoredItem = makeAttemptSummary({
      attemptId: ATTEMPT_1_ID,
      username: "nguyenminhanh",
      userFullName: "Nguyễn Minh Anh",
      examName: "Đề thi thử ĐGNL HSA số 3",
      examKindCode: "HSA",
      scope: "FULL",
      state: "SCORED",
      displayScore: 96,
      maxScore: 150,
      finishedAt: "2026-11-17T04:00:00Z",
      endReason: "SUBMITTED",
      faultFlag: "SYSTEM_FAULT",
      compensated: true,
    });
    const pendingItem = makeAttemptSummary({
      attemptId: "6e2a41d7-0000-4000-8000-000000000000",
      username: "phamquynhanh",
      userFullName: "Phạm Quỳnh Anh",
      examName: "Đề thi thử SP2E số 1",
      examKindCode: "SPT",
      scope: "SUBJECT",
      state: "PENDING_REVIEW",
      regrading: true,
      displayScore: 6.75,
      maxScore: 10,
      finishedAt: "2026-11-16T10:30:00Z",
      endReason: "TIME_UP",
    });

    const listQueries: Array<Record<string, unknown>> = [];
    fakeApi.get("/api/v1/admin/attempts", (call) => {
      const q = (call.options?.params as { query?: Record<string, unknown> })?.query ?? {};
      listQueries.push(q);
      return ok({ items: [scoredItem, pendingItem], page: q.page ?? 0, size: 20, totalItems: 45, totalPages: 3 });
    });

    renderAttemptsUi(<AttemptListView />);

    const table = await screen.findByRole("table", { name: "Danh sách lượt thi" });
    expect(await within(table).findByText("Đề thi thử ĐGNL HSA số 3")).toBeInTheDocument();
    expect(within(table).getByRole("link", { name: "7C1E4B9A" })).toHaveAttribute(
      "href",
      `/attempts/${ATTEMPT_1_ID}`,
    );
    expect(within(table).getByText("96 / 150")).toBeInTheDocument();
    expect(within(table).getByText("6,75 / 10")).toBeInTheDocument();
    expect(within(table).getByText("Lượt hỏng · đã cấp bù")).toBeInTheDocument();
    expect(within(table).getByText("Đang chấm lại")).toBeInTheDocument();

    // Bấm nút xem chi tiết điều hướng tới /attempts/[id].
    await userEvent.click(screen.getByRole("button", { name: "Xem lượt 7C1E4B9A của nguyenminhanh" }));
    expect(routerMock.push).toHaveBeenCalledWith(`/attempts/${ATTEMPT_1_ID}`);

    // Chọn state PENDING_REVIEW (Chờ chấm)
    const statusGroup = screen.getByRole("group", { name: "Trạng thái" });
    await userEvent.click(within(statusGroup).getByRole("radio", { name: "Chờ chấm" }));

    // Nhập user, exam, from, to
    await userEvent.type(screen.getByLabelText("Học viên"), "nguyenminhanh");
    await userEvent.type(screen.getByLabelText("Bài thi"), "HSA số 3");
    await userEvent.type(screen.getByLabelText("Từ ngày"), "2026-11-14");
    await userEvent.type(screen.getByLabelText("Đến ngày"), "2026-11-17");

    await waitFor(() => {
      expect(window.location.search).toContain("state=PENDING_REVIEW");
      expect(window.location.search).toContain("user=nguyenminhanh");
      expect(window.location.search).toContain("exam=HSA+s%E1%BB%91+3");
      expect(window.location.search).toContain("from=2026-11-14");
      expect(window.location.search).toContain("to=2026-11-17");
      expect(
        listQueries.some(
          (q) =>
            q.state === "PENDING_REVIEW" &&
            q.user === "nguyenminhanh" &&
            q.exam === "HSA số 3" &&
            q.from === "2026-11-14" &&
            q.to === "2026-11-17",
        ),
      ).toBe(true);
    });

    // Phân trang: bấm Trang 2 -> page=1 (0-indexed) trong API
    await userEvent.click(screen.getByRole("button", { name: "Trang 2" }));
    await waitFor(() => {
      expect(window.location.search).toContain("page=2");
      expect(listQueries.at(-1)?.page).toBe(1);
    });
  });

  it("hiển thị song ngữ pickName ở locale en và bọc data-exam-content đúng phạm vi (A7.7)", async () => {
    const hsaItem = makeAttemptSummary({
      attemptId: ATTEMPT_1_ID,
      examName: "Đề HSA giữ nguyên tiếng Việt",
      examKindCode: "HSA",
      scope: "FULL",
      state: "SCORED",
      displayScore: 100,
      maxScore: 150,
    });
    const sptItem = makeAttemptSummary({
      attemptId: "11112222-3333-4444-8555-666677778888",
      examName: "Đề SPT giữ nguyên tiếng Việt",
      examKindCode: "SPT",
      scope: "SUBJECT",
      state: "PENDING_REVIEW",
    });
    fakeApi.get(
      "/api/v1/admin/attempts",
      ok({ items: [hsaItem, sptItem], page: 0, size: 20, totalItems: 2, totalPages: 1 }),
    );

    renderAttemptsUi(<AttemptListView />, { locale: "en" });

    const table = await screen.findByRole("table", { name: "Attempts list" });
    const hsaKindMeta = await within(table).findByText("VNU Hanoi Aptitude Test (HSA) · Full exam");
    expect(hsaKindMeta.closest("[data-exam-content]")).toBeNull();

    const sptKindMeta = within(table).getByText("Đánh giá năng lực ĐH Sư phạm (SPT) · By subject");
    expect(sptKindMeta.closest("[data-exam-content]")).toBeNull();

    const examNameEl = within(table).getByText("Đề HSA giữ nguyên tiếng Việt");
    expect(examNameEl.closest("[data-exam-content]")).not.toBeNull();
    expect(screen.getByRole("heading", { level: 1, name: "Attempts" }).closest("[data-exam-content]")).toBeNull();
    expect(within(table).getByText("Awaiting grading")).toBeInTheDocument();
  });

  it("rỗng, lỗi", async () => {
    // 1. Loading skeleton
    fakeApi.pending("/api/v1/admin/attempts");
    const { unmount } = renderAttemptsUi(<AttemptListView />);
    const loadingTable = await screen.findByRole("table", { name: "Danh sách lượt thi" });
    expect(loadingTable).toHaveAttribute("aria-busy", "true");
    unmount();

    // 2. Empty unfiltered
    fakeApi.get("/api/v1/admin/attempts", ok({ items: [], page: 0, size: 20, totalItems: 0, totalPages: 0 }));
    const { unmount: unmountEmpty } = renderAttemptsUi(<AttemptListView />);
    expect(await screen.findByText("Chưa có lượt thi nào")).toBeInTheDocument();
    unmountEmpty();

    // 3. Empty filtered + Clear all
    setUrl("?state=GRADING_ERROR");
    const { unmount: unmountFiltered } = renderAttemptsUi(<AttemptListView />);
    expect(await screen.findByText("Không có lượt thi khớp bộ lọc")).toBeInTheDocument();
    await userEvent.click(within(screen.getByRole("table")).getByRole("button", { name: "Xóa tất cả bộ lọc" }));
    await waitFor(() => expect(window.location.search).toBe(""));
    unmountFiltered();

    // 4. Error + Retry recovers
    let calls = 0;
    fakeApi.get("/api/v1/admin/attempts", () => {
      calls += 1;
      if (calls === 1) return fail(500, { code: "INTERNAL_ERROR" });
      return ok({
        items: [makeAttemptSummary({ examName: "Đề thi thử ĐGNL HSA số 3" })],
        page: 0,
        size: 20,
        totalItems: 1,
        totalPages: 1,
      });
    });
    renderAttemptsUi(<AttemptListView />);
    expect(await screen.findByText("Không tải được danh sách lượt thi")).toBeInTheDocument();
    await userEvent.click(screen.getByRole("button", { name: "Thử lại" }));
    expect(await screen.findByText("Đề thi thử ĐGNL HSA số 3")).toBeInTheDocument();
    expect(calls).toBe(2);
  });

  it("khung hẹp 390px hiển thị ListCard", async () => {
    installContainerWidth(PHONE_CONTENT_WIDTH);
    fakeApi.get(
      "/api/v1/admin/attempts",
      ok({ items: [makeAttemptSummary()], page: 0, size: 20, totalItems: 1, totalPages: 1 }),
    );

    renderAttemptsUi(<AttemptListView />);

    const link = await screen.findByRole("link", { name: "7C1E4B9A" });
    expect(link).toHaveAttribute("href", `/attempts/${ATTEMPT_1_ID}`);
    expect(screen.queryByRole("table")).not.toBeInTheDocument();
  });

  it("ẩn danh sách cũ và phân trang khi ngoại tuyến, và AttemptListSkeleton dùng Skeleton (R1-26, R1-31)", async () => {
    fakeApi.get(
      "/api/v1/admin/attempts",
      ok({
        items: [makeAttemptSummary()],
        page: 0,
        size: 20,
        totalItems: 30,
        totalPages: 2,
      }),
    );

    renderAttemptsUi(<AttemptListView />);
    await screen.findByRole("link", { name: "7C1E4B9A" });
    expect(screen.getByRole("navigation", { name: "Phân trang danh sách lượt thi" })).toBeInTheDocument();

    try {
      onlineManager.setOnline(false);
      await userEvent.click(screen.getByRole("radio", { name: "Đang làm" }));

      expect(await screen.findByText("Kiểm tra kết nối mạng rồi bấm Thử lại.")).toBeInTheDocument();
      expect(screen.queryByRole("link", { name: "7C1E4B9A" })).not.toBeInTheDocument();
      expect(
        screen.queryByRole("navigation", { name: "Phân trang danh sách lượt thi" }),
      ).not.toBeInTheDocument();
    } finally {
      onlineManager.setOnline(true);
    }

    const { container } = renderAttemptsUi(<AttemptListSkeleton />);
    expect(container.querySelector('[aria-busy="true"]')).not.toBeNull();
  });
});
