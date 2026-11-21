import { onlineManager } from "@tanstack/react-query";
import { act, fireEvent, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { useSyncExternalStore } from "react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import type { CatalogOptionResponse } from "@/features/attempt-result/api";
import enMessages from "@/messages/en.json";
import { installContainerWidth, PHONE_CONTENT_WIDTH } from "@/test/container-width";
import { renderWithProviders } from "@/test/render";
import { ADMIN_SESSION, STUDENT_SESSION } from "@/test/storefront-fixtures";
import type { AttemptHistoryItem, AttemptHistoryQueryParams, PageResponseAttemptHistoryItem } from "./api";
import { AttemptHistoryView } from "./history-view";

const navMocks = vi.hoisted(() => ({
  search: "",
  replace: vi.fn(),
  listeners: new Set<() => void>(),
  setSearch(next: string) {
    this.search = next;
    for (const listener of this.listeners) {
      listener();
    }
  },
}));

vi.mock("next/navigation", () => ({
  usePathname: () => "/history",
  useRouter: () => ({ replace: navMocks.replace, push: vi.fn(), refresh: vi.fn() }),
  useSearchParams: () => {
    const search = useSyncExternalStore(
      (cb) => {
        navMocks.listeners.add(cb);
        return () => navMocks.listeners.delete(cb);
      },
      () => navMocks.search,
      () => navMocks.search,
    );
    return new URLSearchParams(search);
  },
}));

const EXAM_KINDS: CatalogOptionResponse[] = [
  { id: "ek-1", code: "HSA", nameVi: "ĐGNL HSA", nameEn: "HSA Assessment", displayOrder: 1, value: 1 },
  { id: "ek-2", code: "VACT", nameVi: "ĐGNL V-ACT", nameEn: "", displayOrder: 2, value: 2 },
];

const SUBJECTS: CatalogOptionResponse[] = [
  { id: "s-1", code: "TOAN", nameVi: "Toán học", nameEn: "Mathematics", displayOrder: 1, value: 1 },
  { id: "s-2", code: "VATLI", nameVi: "Vật lí", nameEn: "Physics", displayOrder: 2, value: 2 },
  { id: "s-3", code: "HOAHOC", nameVi: "Hóa học", nameEn: "", displayOrder: 3, value: 3 },
];

function makeItem(overrides: Partial<AttemptHistoryItem> = {}): AttemptHistoryItem {
  return {
    attemptId: "att-1",
    examId: "11111111-1111-1111-1111-111111111111",
    examName: "Đề HSA tổng hợp số 1",
    examKindCode: "HSA",
    scope: "FULL",
    attemptNo: 1,
    startedAt: "2026-11-20T01:00:00Z",
    state: "SCORED",
    action: "VIEW_RESULT",
    displayScore: 108.5,
    maxScore: 150,
    ...overrides,
  };
}

describe("AttemptHistoryView · AC-4, AC-13..AC-16, AC-18, AC-19", () => {
  beforeEach(() => {
    navMocks.setSearch("");
    navMocks.replace.mockReset();
  });

  afterEach(() => {
    onlineManager.setOnline(true);
    vi.useRealTimers();
    vi.restoreAllMocks();
  });

  it("chặn admin và xử lý trạng thái tải (aria-busy), lỗi (không hiện empty), Thử lại, offline, và dữ liệu cũ stale (AC-15)", async () => {
    const blockedLoader = vi.fn<() => Promise<PageResponseAttemptHistoryItem>>();
    const adminView = renderWithProviders(
      <AttemptHistoryView
        loadCurrentSession={async () => ADMIN_SESSION}
        loadHistory={blockedLoader}
        loadExamKinds={async () => EXAM_KINDS}
        loadSubjects={async () => SUBJECTS}
      />,
    );
    expect(await screen.findByRole("heading", { level: 2, name: "Tài khoản quản trị" })).toBeInTheDocument();
    expect(blockedLoader).not.toHaveBeenCalled();
    adminView.unmount();

    // Loading skeleton with aria-busy="true" (R1-30)
    let resolveDeferred!: (value: PageResponseAttemptHistoryItem) => void;
    const deferredLoad = vi.fn(
      () =>
        new Promise<PageResponseAttemptHistoryItem>((resolve) => {
          resolveDeferred = resolve;
        }),
    );
    const loadingView = renderWithProviders(
      <AttemptHistoryView
        loadCurrentSession={async () => STUDENT_SESSION}
        loadHistory={deferredLoad}
        loadExamKinds={async () => EXAM_KINDS}
        loadSubjects={async () => SUBJECTS}
      />,
    );
    const loadingStatus = await screen.findByRole("status", { name: "Đang tải lịch sử luyện thi" });
    expect(loadingStatus).toHaveAttribute("aria-busy", "true");
    await act(async () => {
      resolveDeferred({ items: [makeItem()], page: 0, size: 20, totalItems: 1, totalPages: 1 });
    });
    expect(await screen.findByText("Đề HSA tổng hợp số 1")).toBeInTheDocument();
    loadingView.unmount();

    const failThenOk = vi
      .fn<(p: AttemptHistoryQueryParams) => Promise<PageResponseAttemptHistoryItem>>()
      .mockRejectedValueOnce(new Error("net"))
      .mockResolvedValueOnce({ items: [makeItem()], page: 0, size: 20, totalItems: 1, totalPages: 1 });
    const errView = renderWithProviders(
      <AttemptHistoryView
        loadCurrentSession={async () => STUDENT_SESSION}
        loadHistory={failThenOk}
        loadExamKinds={async () => EXAM_KINDS}
        loadSubjects={async () => SUBJECTS}
      />,
    );
    expect(await screen.findByRole("alert")).toHaveTextContent("Không tải được lịch sử luyện thi");
    expect(screen.queryByText("Chưa có lượt thi phù hợp")).not.toBeInTheDocument();
    expect(screen.queryByText("Bạn chưa có lượt luyện thi nào")).not.toBeInTheDocument();
    await userEvent.click(screen.getByRole("button", { name: "Thử lại" }));
    expect(await screen.findByText("Đề HSA tổng hợp số 1")).toBeInTheDocument();
    errView.unmount();

    // Stale-data banner when subsequent filter load rejects while keeping previous data (R1-30, R1-46)
    const okThenFail = vi
      .fn<(p: AttemptHistoryQueryParams) => Promise<PageResponseAttemptHistoryItem>>()
      .mockResolvedValueOnce({ items: [makeItem({ examName: "Đề cũ giữ lại" })], page: 0, size: 20, totalItems: 1, totalPages: 1 })
      .mockRejectedValueOnce(new Error("refetch fail"));
    const staleView = renderWithProviders(
      <AttemptHistoryView
        loadCurrentSession={async () => STUDENT_SESSION}
        loadHistory={okThenFail}
        loadExamKinds={async () => EXAM_KINDS}
        loadSubjects={async () => SUBJECTS}
      />,
    );
    expect(await screen.findByText("Đề cũ giữ lại")).toBeInTheDocument();
    await act(async () => {
      await staleView.client.refetchQueries();
    });
    expect(await screen.findByRole("alert")).toHaveTextContent("Dữ liệu chưa được cập nhật.");
    expect(screen.getByText("Đề cũ giữ lại")).toBeInTheDocument();
    staleView.unmount();

    // Offline cold load & offline filter change (R1-36)
    onlineManager.setOnline(false);
    const offlineColdView = renderWithProviders(
      <AttemptHistoryView
        loadCurrentSession={async () => STUDENT_SESSION}
        loadHistory={async () => ({ items: [makeItem()], page: 0, size: 20, totalItems: 1, totalPages: 1 })}
        loadExamKinds={async () => EXAM_KINDS}
        loadSubjects={async () => SUBJECTS}
      />,
    );
    expect(await screen.findByRole("heading", { level: 2, name: "Không có kết nối" })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Thử lại" })).toHaveClass("h-11");
    offlineColdView.unmount();
    onlineManager.setOnline(true);
  });

  it("áp dụng quy tắc điểm AC-4, V-ACT, REGRADING (AC-19), choiceOptions.ANH, variant=exam, và F20 compensated/fault (AC-18)", async () => {
    const items: AttemptHistoryItem[] = [
      makeItem({
        attemptId: "att-zero",
        examName: "Đề điểm 0 thật",
        state: "SCORED",
        displayScore: 0,
        maxScore: 100,
      }),
      makeItem({
        attemptId: "att-pending",
        examName: "Đề tự luận chờ chấm",
        state: "PENDING_REVIEW",
        displayScore: undefined,
        rawScore: 40,
        maxScore: 100,
      }),
      makeItem({
        attemptId: "att-open",
        examName: "Đề đang làm dở",
        state: "IN_PROGRESS",
        action: "CONTINUE",
        displayScore: 0,
        rawScore: 0,
      }),
      makeItem({
        attemptId: "att-regrade",
        examName: "Đề đang chấm lại",
        state: "PENDING_REVIEW",
        regrading: true,
        displayScore: 112,
        maxScore: 150,
        compensated: true,
      }),
      makeItem({
        attemptId: "att-vact",
        examName: "Đề V-ACT tổng hợp",
        examKindCode: "VACT",
        state: "SCORED",
        displayScore: 980,
        scaleMax: 1200,
        maxScore: 120,
        faultFlag: "SYSTEM_FAULT",
        compensated: false,
      }),
      makeItem({
        attemptId: "att-grading",
        examName: "Đề đang chấm điểm",
        state: "GRADING",
        displayScore: 0,
        rawScore: 0,
        choices: { p3: { optionCode: "ANH" } },
      }),
      makeItem({
        attemptId: "att-gerr",
        examName: "Đề lỗi xử lý kết quả",
        state: "GRADING_ERROR",
        displayScore: 0,
        rawScore: 0,
      }),
    ];

    renderWithProviders(
      <AttemptHistoryView
        loadCurrentSession={async () => STUDENT_SESSION}
        loadHistory={async () => ({ items, page: 0, size: 20, totalItems: 7, totalPages: 1 })}
        loadExamKinds={async () => EXAM_KINDS}
        loadSubjects={async () => SUBJECTS}
      />,
    );

    const table = await screen.findByRole("table", { name: "Danh sách lịch sử luyện thi" });
    const rows = within(table).getAllByRole("row");

    // role="status" count badge (R1-46)
    expect(screen.getByRole("status")).toHaveTextContent("7 lượt");

    // Genuine 0 stays 0/100
    expect(rows[1]).toHaveTextContent("0/100");
    // PENDING_REVIEW hides raw score
    expect(rows[2]).toHaveTextContent("Chưa có điểm");
    // IN_PROGRESS shows Continue link with variant="exam" and hides rawScore: 0 (R1-33, R1-48)
    const continueLink = within(rows[3]!).getByRole("link", { name: "Tiếp tục" });
    expect(continueLink).toHaveAttribute("href", "/attempts/att-open");
    expect(continueLink.className).toContain("bg-accent-500");
    expect(rows[3]).toHaveTextContent("Chưa có điểm");
    // REGRADING keeps score + chip "Đang chấm lại" + note + compensated banner (R1-33)
    expect(rows[4]).toHaveTextContent("Đang chấm lại");
    expect(rows[4]).toHaveTextContent("112/150");
    expect(rows[4]).toHaveTextContent("Điểm trước khi chấm lại");
    expect(rows[4]).toHaveTextContent("Lượt bị ảnh hưởng bởi lỗi hệ thống, đã cấp bù 1 lượt");
    // V-ACT shows scaleMax + practice note + fault flag
    expect(rows[5]).toHaveTextContent("980/1.200");
    expect(rows[5]).toHaveTextContent("Điểm luyện tập");
    expect(rows[5]).toHaveTextContent("Lượt kết thúc do lỗi hệ thống");
    // GRADING & GRADING_ERROR hide rawScore: 0 + choiceOptions.ANH shows "Tiếng Anh" (R1-14, R1-33)
    expect(rows[6]).toHaveTextContent("Chưa có điểm");
    expect(rows[6]).toHaveTextContent("Tiếng Anh");
    expect(rows[7]).toHaveTextContent("Chưa có điểm");
  });

  it("đọc URL query lọc, bỏ qua examId không phải UUID, 3 trạng thái rỗng, lọc kind/from/to, xử lý ngày/trang sai, debounce giữ ký tự đang gõ, chip UUID và phân trang (R1-8..11, R1-18..19, R1-29, R1-31, R1-56..57)", async () => {
    // 1. Non-UUID examId (product code from /home link) MUST NOT be forwarded -> !activeFilters && totalItems === 0 -> brand-new student empty state + /home CTA (R1-11)
    navMocks.setSearch("?examId=HSA-FULL-01");
    const captureParams = vi.fn(async (params: AttemptHistoryQueryParams) => ({
      items: [],
      page: params.page,
      size: params.size,
      totalItems: 0,
      totalPages: 0,
    }));

    const nonUuidView = renderWithProviders(
      <AttemptHistoryView
        loadCurrentSession={async () => STUDENT_SESSION}
        loadHistory={captureParams}
        loadExamKinds={async () => EXAM_KINDS}
        loadSubjects={async () => SUBJECTS}
      />,
    );
    expect(await screen.findByRole("heading", { level: 2, name: "Bạn chưa có lượt luyện thi nào" })).toBeInTheDocument();
    expect(within(screen.getByRole("main")).getByRole("link", { name: "Bài thi của tôi" })).toHaveAttribute("href", "/home");
    expect(captureParams).toHaveBeenCalledWith({ page: 0, size: 20 });
    expect(screen.queryByRole("button", { name: "Bỏ lọc theo bài" })).not.toBeInTheDocument();
    nonUuidView.unmount();

    // 1b. Invalid calendar date & invalid/oversized page sanitization (R1-18, R1-19)
    navMocks.setSearch("?from=2026-02-30&to=2026-04-31&page=2abc");
    const sanitizedParams = vi.fn(async (params: AttemptHistoryQueryParams) => ({
      items: [],
      page: params.page,
      size: params.size,
      totalItems: 0,
      totalPages: 0,
    }));
    const sanitizeView = renderWithProviders(
      <AttemptHistoryView
        loadCurrentSession={async () => STUDENT_SESSION}
        loadHistory={sanitizedParams}
        loadExamKinds={async () => EXAM_KINDS}
        loadSubjects={async () => SUBJECTS}
      />,
    );
    expect(await screen.findByRole("heading", { level: 2, name: "Bạn chưa có lượt luyện thi nào" })).toBeInTheDocument();
    expect(sanitizedParams).toHaveBeenCalledWith({ page: 0, size: 20 });
    sanitizeView.unmount();

    // 1c. Out-of-range page empty state (?page=99 with totalItems: 5) -> Back to page 1 CTA (R1-9, R1-10)
    navMocks.setSearch("?page=99");
    const outOfRangeView = renderWithProviders(
      <AttemptHistoryView
        loadCurrentSession={async () => STUDENT_SESSION}
        loadHistory={async () => ({ items: [], page: 98, size: 20, totalItems: 5, totalPages: 1 })}
        loadExamKinds={async () => EXAM_KINDS}
        loadSubjects={async () => SUBJECTS}
      />,
    );
    expect(await screen.findByRole("heading", { level: 2, name: "Trang này chưa có dữ liệu" })).toBeInTheDocument();
    await userEvent.click(screen.getByRole("button", { name: "Về trang 1" }));
    expect(navMocks.replace).toHaveBeenCalledWith("/history", { scroll: true });
    outOfRangeView.unmount();

    // 1d. Active filter empty state + Clear filters CTA (R1-29)
    navMocks.replace.mockClear();
    navMocks.setSearch("?q=KHONGCO&scope=PART");
    const activeEmptyView = renderWithProviders(
      <AttemptHistoryView
        loadCurrentSession={async () => STUDENT_SESSION}
        loadHistory={async () => ({ items: [], page: 0, size: 20, totalItems: 0, totalPages: 0 })}
        loadExamKinds={async () => EXAM_KINDS}
        loadSubjects={async () => SUBJECTS}
      />,
    );
    expect(await screen.findByRole("heading", { level: 2, name: "Chưa có lượt thi phù hợp" })).toBeInTheDocument();
    const clearButtons = screen.getAllByRole("button", { name: "Xóa bộ lọc" });
    await userEvent.click(clearButtons[clearButtons.length - 1]!);
    expect(navMocks.replace).toHaveBeenCalledWith("/history", { scroll: false });
    activeEmptyView.unmount();

    // 2. Valid UUID examId + active filter clear, kind/from/to filters, unknown kind option, & pagination scroll: true
    const uuid = "22222222-2222-2222-2222-222222222222";
    navMocks.setSearch(`?examId=${uuid}&kind=LEGACY&scope=SUBJECT&from=2026-11-01&to=2026-11-20&page=1`);
    const uuidLoader = vi.fn(async () => ({
      items: [makeItem({ attemptId: "a2", examId: uuid, examName: "Đề Toán riêng", scope: "SUBJECT", subjectCode: "TOAN" })],
      page: 0,
      size: 20,
      totalItems: 25,
      totalPages: 2,
    }));

    const uuidView = renderWithProviders(
      <AttemptHistoryView
        loadCurrentSession={async () => STUDENT_SESSION}
        loadHistory={uuidLoader}
        loadExamKinds={async () => EXAM_KINDS}
        loadSubjects={async () => SUBJECTS}
      />,
    );
    const clearExamBtn = await screen.findByRole("button", { name: "Bỏ lọc theo bài" });
    // t.rich examFilterChip preserves examName in data-exam-content (R1-39, R1-55)
    const examChip = clearExamBtn.parentElement!;
    expect(
      await within(examChip).findByText("Đề Toán riêng"),
    ).toHaveAttribute("data-exam-content");
    // Unknown kind=LEGACY stays visible in Select trigger (R1-57)
    expect(screen.getByRole("combobox", { name: "Kỳ thi" })).toHaveTextContent("LEGACY");
    expect(uuidLoader).toHaveBeenCalledWith({
      examId: uuid,
      kind: "LEGACY",
      scope: "SUBJECT",
      from: "2026-11-01",
      to: "2026-11-20",
      page: 0,
      size: 20,
    });

    // Select kind option (R1-31)
    navMocks.replace.mockClear();
    await userEvent.click(screen.getByRole("combobox", { name: "Kỳ thi" }));
    await userEvent.click(screen.getByRole("option", { name: "ĐGNL HSA" }));
    expect(navMocks.replace).toHaveBeenCalledWith(
      `/history?kind=HSA&scope=SUBJECT&examId=${uuid}&from=2026-11-01&to=2026-11-20`,
      { scroll: false },
    );

    // Change from & to date inputs (R1-31)
    navMocks.replace.mockClear();
    fireEvent.change(screen.getByLabelText("Từ ngày"), { target: { value: "2026-11-05" } });
    expect(navMocks.replace).toHaveBeenCalledWith(
      `/history?kind=HSA&scope=SUBJECT&examId=${uuid}&from=2026-11-05&to=2026-11-20`,
      { scroll: false },
    );
    fireEvent.change(screen.getByLabelText("Đến ngày"), { target: { value: "2026-11-18" } });
    expect(navMocks.replace).toHaveBeenCalledWith(
      `/history?kind=HSA&scope=SUBJECT&examId=${uuid}&from=2026-11-05&to=2026-11-18`,
      { scroll: false },
    );

    // Pagination page 2 uses scroll: true (R1-56)
    navMocks.replace.mockClear();
    await userEvent.click(screen.getByRole("button", { name: "Trang 2" }));
    expect(navMocks.replace).toHaveBeenCalledWith(
      `/history?kind=HSA&scope=SUBJECT&examId=${uuid}&from=2026-11-05&to=2026-11-18&page=2`,
      { scroll: true },
    );

    // Clear exam chip -> removes examId param and resets page to 1
    navMocks.replace.mockClear();
    await userEvent.click(screen.getByRole("button", { name: "Bỏ lọc theo bài" }));
    expect(navMocks.replace).toHaveBeenCalledWith(
      "/history?kind=HSA&scope=SUBJECT&from=2026-11-05&to=2026-11-18",
      { scroll: false },
    );
    uuidView.unmount();

    // 3. Debounced search input preserves trailing keystrokes typed while prior URL update is in-flight (R1-8)
    vi.useFakeTimers();
    navMocks.setSearch("");
    navMocks.replace.mockClear();
    renderWithProviders(
      <AttemptHistoryView
        loadCurrentSession={async () => STUDENT_SESSION}
        loadHistory={async () => ({ items: [makeItem()], page: 0, size: 20, totalItems: 1, totalPages: 1 })}
        loadExamKinds={async () => EXAM_KINDS}
        loadSubjects={async () => SUBJECTS}
      />,
    );
    await act(async () => {
      await vi.advanceTimersByTimeAsync(10);
    });
    const searchInput = screen.getByLabelText("Tìm theo tên bài") as HTMLInputElement;
    fireEvent.change(searchInput, { target: { value: "HSA" } });
    expect(navMocks.replace).not.toHaveBeenCalled();

    await act(async () => {
      await vi.advanceTimersByTimeAsync(350);
    });
    expect(navMocks.replace).toHaveBeenCalledWith("/history?q=HSA", { scroll: false });

    // User types " 1" before router.replace updates searchParams
    fireEvent.change(searchInput, { target: { value: "HSA 1" } });
    act(() => {
      navMocks.setSearch("?q=HSA");
    });
    expect(searchInput.value).toBe("HSA 1");
    await act(async () => {
      await vi.advanceTimersByTimeAsync(350);
    });
    expect(navMocks.replace).toHaveBeenLastCalledWith("/history?q=HSA+1", { scroll: false });
  });

  it("giữ nguyên các bộ lọc cập nhật liên tiếp trước khi router.replace đồng bộ useSearchParams (W5)", async () => {
    vi.useFakeTimers();
    navMocks.setSearch("?kind=HSA&from=2026-11-21&to=2026-11-21");
    renderWithProviders(
      <AttemptHistoryView
        loadCurrentSession={async () => STUDENT_SESSION}
        loadHistory={async () => ({ items: [makeItem()], page: 0, size: 20, totalItems: 1, totalPages: 1 })}
        loadExamKinds={async () => EXAM_KINDS}
        loadSubjects={async () => SUBJECTS}
      />,
    );
    await act(async () => {
      await vi.advanceTimersByTimeAsync(10);
    });
    expect(screen.getByRole("table", { name: "Danh sách lịch sử luyện thi" })).toBeInTheDocument();

    navMocks.replace.mockClear();
    const fromInput = document.querySelector("#history-from") as HTMLInputElement;
    const toInput = document.querySelector("#history-to") as HTMLInputElement;
    expect(fromInput).not.toBeNull();
    expect(toInput).not.toBeNull();

    // Rapid sequential changes to #history-from and #history-to without navMocks.search updating in between
    fireEvent.change(fromInput, { target: { value: "2000-01-01" } });
    fireEvent.change(toInput, { target: { value: "2000-01-01" } });

    expect(navMocks.replace).toHaveBeenCalledTimes(2);
    expect(navMocks.replace).toHaveBeenNthCalledWith(
      1,
      "/history?kind=HSA&from=2000-01-01&to=2026-11-21",
      { scroll: false },
    );
    expect(navMocks.replace).toHaveBeenNthCalledWith(
      2,
      "/history?kind=HSA&from=2000-01-01&to=2000-01-01",
      { scroll: false },
    );

    // Unrelated re-render (typing in search) while URL update is in-flight must not clobber latestFilterStateRef,
    // and debounce timer must sync latestFilterStateRef before calling router.replace
    navMocks.replace.mockClear();
    const searchInput = screen.getByLabelText("Tìm theo tên bài") as HTMLInputElement;
    fireEvent.change(searchInput, { target: { value: "Đề mới" } });
    fireEvent.change(fromInput, { target: { value: "2026-10-01" } });
    expect(navMocks.replace).toHaveBeenLastCalledWith(
      "/history?kind=HSA&from=2026-10-01&to=2000-01-01",
      { scroll: false },
    );

    await act(async () => {
      await vi.advanceTimersByTimeAsync(350);
    });
    expect(navMocks.replace).toHaveBeenLastCalledWith(
      "/history?q=%C4%90%E1%BB%81+m%E1%BB%9Bi&kind=HSA&from=2026-10-01&to=2000-01-01",
      { scroll: false },
    );

    fireEvent.change(toInput, { target: { value: "2026-10-31" } });
    expect(navMocks.replace).toHaveBeenLastCalledWith(
      "/history?q=%C4%90%E1%BB%81+m%E1%BB%9Bi&kind=HSA&from=2026-10-01&to=2026-10-31",
      { scroll: false },
    );

    // clearAllFilters resets latestFilterStateRef synchronously before router.replace
    fireEvent.click(screen.getByRole("button", { name: "Xóa bộ lọc" }));
    expect(navMocks.replace).toHaveBeenLastCalledWith("/history", { scroll: false });
    fireEvent.change(fromInput, { target: { value: "2026-11-15" } });
    expect(navMocks.replace).toHaveBeenLastCalledWith("/history?from=2026-11-15", { scroll: false });
  });

  it("chuyển sang CardList ở màn hình hẹp (1 tab stop mỗi thẻ) và áp dụng pickName song ngữ + ANH trên en (R1-42)", async () => {
    installContainerWidth(PHONE_CONTENT_WIDTH);
    renderWithProviders(
      <AttemptHistoryView
        loadCurrentSession={async () => STUDENT_SESSION}
        loadHistory={async () => ({
          items: [
            makeItem({
              attemptId: "att-mob",
              examName: "Mobile Result Card",
              scope: "SUBJECT",
              subjectCode: "TOAN",
              choices: { p1: { optionCode: "SCIENCE", subjectCodes: ["VATLI", "HOAHOC"] } },
            }),
            makeItem({
              attemptId: "att-mob-cont",
              examName: "Mobile Continue Card",
              state: "IN_PROGRESS",
              action: "CONTINUE",
              displayScore: undefined,
              choices: { p3: { optionCode: "ANH" } },
            }),
          ],
          page: 0,
          size: 20,
          totalItems: 2,
          totalPages: 1,
        })}
        loadExamKinds={async () => EXAM_KINDS}
        loadSubjects={async () => SUBJECTS}
      />,
      { locale: "en", messages: enMessages },
    );

    const list = await screen.findByRole("list", { name: "Attempts" });
    expect(screen.queryByRole("table")).not.toBeInTheDocument();
    expect(within(list).getByText(/Subject Mathematics/)).toBeInTheDocument();
    expect(within(list).getByText("Science: Physics, Hóa học")).toBeInTheDocument();
    expect(within(list).getByText("English")).toBeInTheDocument();

    // Every CardList item has exactly 1 <Link> tab stop (R1-42) and CONTINUE uses variant="exam" (R1-48)
    const cards = within(list).getAllByRole("listitem");
    expect(cards).toHaveLength(2);
    expect(within(cards[0]!).getAllByRole("link")).toHaveLength(1);
    const contLinks = within(cards[1]!).getAllByRole("link");
    expect(contLinks).toHaveLength(1);
    expect(contLinks[0]).toHaveTextContent("Continue");
    expect(contLinks[0]!.className).toContain("bg-accent-500");
  });
});
