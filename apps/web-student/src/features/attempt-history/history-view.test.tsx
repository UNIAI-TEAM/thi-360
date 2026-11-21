import { act, fireEvent, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
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
}));

vi.mock("next/navigation", () => ({
  usePathname: () => "/history",
  useRouter: () => ({ replace: navMocks.replace, push: vi.fn(), refresh: vi.fn() }),
  useSearchParams: () => new URLSearchParams(navMocks.search),
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
    navMocks.search = "";
    navMocks.replace.mockReset();
  });

  afterEach(() => {
    vi.useRealTimers();
    vi.restoreAllMocks();
  });

  it("chặn admin và xử lý trạng thái lỗi + Thử lại + dữ liệu cũ stale (AC-15)", async () => {
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
    await userEvent.click(screen.getByRole("button", { name: "Thử lại" }));
    expect(await screen.findByText("Đề HSA tổng hợp số 1")).toBeInTheDocument();
    errView.unmount();
  });

  it("áp dụng quy tắc điểm AC-4, V-ACT, REGRADING (AC-19), và F20 compensated/fault (AC-18)", async () => {
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
        displayScore: undefined,
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
    ];

    renderWithProviders(
      <AttemptHistoryView
        loadCurrentSession={async () => STUDENT_SESSION}
        loadHistory={async () => ({ items, page: 0, size: 20, totalItems: 5, totalPages: 1 })}
        loadExamKinds={async () => EXAM_KINDS}
        loadSubjects={async () => SUBJECTS}
      />,
    );

    const table = await screen.findByRole("table", { name: "Danh sách lịch sử luyện thi" });
    const rows = within(table).getAllByRole("row");

    // Genuine 0 stays 0/100
    expect(rows[1]).toHaveTextContent("0/100");
    // PENDING_REVIEW hides raw score
    expect(rows[2]).toHaveTextContent("Chưa có điểm");
    // IN_PROGRESS shows Continue link
    expect(within(rows[3]!).getByRole("link", { name: "Tiếp tục" })).toHaveAttribute("href", "/attempts/att-open");
    // REGRADING keeps score + note + compensated banner
    expect(rows[4]).toHaveTextContent("112/150");
    expect(rows[4]).toHaveTextContent("Điểm trước khi chấm lại");
    expect(rows[4]).toHaveTextContent("Lượt bị ảnh hưởng bởi lỗi hệ thống, đã cấp bù 1 lượt");
    // V-ACT shows scaleMax + practice note + fault flag
    expect(rows[5]).toHaveTextContent("980/1.200");
    expect(rows[5]).toHaveTextContent("Điểm luyện tập");
    expect(rows[5]).toHaveTextContent("Lượt kết thúc do lỗi hệ thống");
  });

  it("đọc URL query lọc, bỏ qua examId không phải UUID, xử lý chip UUID, debounce tìm kiếm, empty + xóa bộ lọc và phân trang (AC-13, AC-14, AC-16)", async () => {
    // 1. Non-UUID examId (product code from /home link) MUST NOT be forwarded to loadHistory
    navMocks.search = "?examId=HSA-FULL-01";
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
    expect(await screen.findByRole("heading", { level: 2, name: "Chưa có lượt thi phù hợp" })).toBeInTheDocument();
    expect(captureParams).toHaveBeenCalledWith({ page: 0, size: 20 });
    expect(screen.queryByRole("button", { name: "Bỏ lọc theo bài" })).not.toBeInTheDocument();
    nonUuidView.unmount();

    // 2. Valid UUID examId + active filter clear & pagination
    const uuid = "22222222-2222-2222-2222-222222222222";
    navMocks.search = `?examId=${uuid}&scope=SUBJECT&from=2026-11-01&to=2026-11-20&page=1`;
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
    expect(await screen.findByRole("button", { name: "Bỏ lọc theo bài" })).toBeInTheDocument();
    expect(uuidLoader).toHaveBeenCalledWith({
      examId: uuid,
      scope: "SUBJECT",
      from: "2026-11-01",
      to: "2026-11-20",
      page: 0,
      size: 20,
    });

    // Clear exam chip -> removes examId param
    await userEvent.click(screen.getByRole("button", { name: "Bỏ lọc theo bài" }));
    expect(navMocks.replace).toHaveBeenCalledWith(
      "/history?scope=SUBJECT&from=2026-11-01&to=2026-11-20",
      { scroll: false },
    );

    // Pagination page 2
    navMocks.replace.mockClear();
    await userEvent.click(screen.getByRole("button", { name: "Trang 2" }));
    expect(navMocks.replace).toHaveBeenCalledWith(
      `/history?scope=SUBJECT&examId=${uuid}&from=2026-11-01&to=2026-11-20&page=2`,
      { scroll: false },
    );
    uuidView.unmount();

    // 3. Debounced search input
    vi.useFakeTimers();
    navMocks.search = "";
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
    const searchInput = screen.getByLabelText("Tìm theo tên bài");
    fireEvent.change(searchInput, { target: { value: "HSA" } });
    expect(navMocks.replace).not.toHaveBeenCalled();

    await act(async () => {
      await vi.advanceTimersByTimeAsync(350);
    });
    expect(navMocks.replace).toHaveBeenCalledWith("/history?q=HSA", { scroll: false });
  });

  it("chuyển sang CardList ở màn hình hẹp và áp dụng pickName song ngữ trên en", async () => {
    installContainerWidth(PHONE_CONTENT_WIDTH);
    renderWithProviders(
      <AttemptHistoryView
        loadCurrentSession={async () => STUDENT_SESSION}
        loadHistory={async () => ({
          items: [
            makeItem({
              attemptId: "att-mob",
              scope: "SUBJECT",
              subjectCode: "TOAN",
              choices: { p1: { optionCode: "SCIENCE", subjectCodes: ["VATLI", "HOAHOC"] } },
            }),
          ],
          page: 0,
          size: 20,
          totalItems: 1,
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
  });
});
