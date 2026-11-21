import { screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { SessionProvider, useSession } from "@/features/session/session-provider";
import type { Session } from "@/features/session/types";
import en from "@/messages/en.json";
import viMessages from "@/messages/vi.json";
import { installContainerWidth, PHONE_CONTENT_WIDTH } from "@/test/container-width";
import { TEST_ADMIN_SESSION } from "@/test/fixtures";
import { renderWithProviders } from "@/test/render";
import {
  defaultUsersApiDeps,
  type StudentLearning,
  type UserDetail,
  type UsersApiDeps,
  UsersApiError,
} from "../api";
import { LearningProfileTab } from "../components/learning-profile-tab";
import { UserDetailView } from "../components/user-detail-view";

const routerMock = vi.hoisted(() => ({ push: vi.fn() }));
vi.mock("next/navigation", () => ({
  useRouter: () => routerMock,
  usePathname: () => window.location.pathname,
  useSearchParams: () => new URLSearchParams(window.location.search),
}));

const VIETNAMESE_DIACRITIC_RE =
  /[àáảãạăằắẳẵặâầấẩẫậèéẻẽẹêềếểễệìíỉĩịòóỏõọôồốổỗộơờớởỡợùúủũụưừứửữựỳýỷỹỵđÀÁẢÃẠĂẰẮẲẴẶÂẦẤẨẪẬÈÉẺẼẸÊỀẾỂỄỆÌÍỈĨỊÒÓỎÕỌÔỒỐỔỖỘƠỜỚỞỠỢÙÚỦŨỤƯỪỨỬỮỰỲÝỶỸỴĐ]/;

const RAW_ENUM_TOKENS = [
  "ACTIVE",
  "IN_USE",
  "EXHAUSTED",
  "REVOKED",
  "IN_PROGRESS",
  "GRADING",
  "PENDING_REVIEW",
  "SCORED",
  "GRADING_ERROR",
  "PURCHASE",
  "CLASS",
] as const;

beforeEach(() => {
  // Tab của trang chi tiết nằm trong URL: mỗi test bắt đầu ở đường dẫn sạch.
  window.history.replaceState(null, "", "/users/test");
});

const BASE_USER: UserDetail = {
  id: "00000000-0000-4000-8000-000000000101",
  code: "U000101",
  username: "student01",
  fullName: "Học viên 01",
  email: "student01@example.com",
  phone: "0901000001",
  source: "ADMIN_CREATED",
  status: "ACTIVE",
  mustChangePassword: false,
  lastLoginAt: "2026-11-12T08:30:00Z",
  roles: [{ id: "r-student", code: "STUDENT", name: "Học viên", access: "EXAM" }],
  version: 1,
  createdAt: "2026-11-10T08:00:00Z",
  updatedAt: "2026-11-12T08:30:00Z",
};

const EMPTY_LEARNING: StudentLearning = {
  userId: BASE_USER.id,
  dataAvailable: false,
  entitlements: [],
  attempts: [],
  assessments: [],
  practice: [],
  supportRequests: [],
};

const POPULATED_LEARNING: StudentLearning = {
  userId: BASE_USER.id,
  dataAvailable: true,
  entitlements: [
    {
      entitlementId: "11111111-1111-4111-8111-111111111111",
      examId: "aaaa1111-1111-4111-8111-111111111111",
      examCode: "HSA-TOAN-2026",
      examName: "Đề thi thử HSA Toán",
      source: "PURCHASE",
      attemptsTotal: 4,
      attemptsUsed: 2,
      attemptsReserved: 1,
      attemptsRevoked: 0,
      attemptsLeft: 1,
      status: "ACTIVE",
    },
    {
      entitlementId: "22222222-2222-4222-8222-222222222222",
      examId: "bbbb2222-2222-4222-8222-222222222222",
      examCode: "VACT-FULL-01",
      examName: null,
      source: "CLASS",
      attemptsTotal: 2,
      attemptsUsed: 1,
      attemptsReserved: 1,
      attemptsRevoked: 0,
      attemptsLeft: 0,
      status: "IN_USE",
    },
    {
      entitlementId: "33333333-3333-4333-8333-333333333333",
      examId: "cccc3333-3333-4333-8333-333333333333",
      examCode: null,
      examName: "Đề luyện THPTQG Vật lí",
      source: "PURCHASE",
      attemptsTotal: 1,
      attemptsUsed: 1,
      attemptsReserved: 0,
      attemptsRevoked: 0,
      attemptsLeft: 0,
      status: "EXHAUSTED",
    },
    {
      entitlementId: "44444444-4444-4444-8444-444444444444",
      examId: "dddd4444-4444-4444-8444-444444444444",
      examCode: "HSA-REV-02",
      examName: "Đề ôn tập HSA Văn",
      source: "PURCHASE",
      attemptsTotal: 3,
      attemptsUsed: 0,
      attemptsReserved: 0,
      attemptsRevoked: 3,
      attemptsLeft: 0,
      status: "REVOKED",
    },
  ],
  attempts: [
    {
      attemptId: "a1b2c3d4-0001-4000-8000-000000000001",
      examId: "aaaa1111-1111-4111-8111-111111111111",
      examName: "Đề thi thử HSA Toán",
      examKindCode: "HSA",
      scope: "PART",
      attemptNo: 1,
      status: "SCORED",
      startedAt: "2026-11-20T03:15:00Z",
      finishedAt: "2026-11-20T04:00:00Z",
      score: 85.5,
      maxScore: 100,
    },
    {
      attemptId: "b2c3d4e5-0002-4000-8000-000000000002",
      examId: "aaaa1111-1111-4111-8111-111111111111",
      examName: "Đề thi thử HSA Toán — Lần 0 điểm",
      examKindCode: "HSA",
      scope: "PART",
      attemptNo: 2,
      status: "SCORED",
      startedAt: "2026-11-20T05:00:00Z",
      finishedAt: "2026-11-20T05:30:00Z",
      score: 0,
      maxScore: 100,
    },
    {
      attemptId: "c3d4e5f6-0003-4000-8000-000000000003",
      examId: "bbbb2222-2222-4222-8222-222222222222",
      examName: "Đề thi V-ACT Tổng hợp",
      examKindCode: "VACT",
      scope: "FULL",
      attemptNo: 1,
      status: "IN_PROGRESS",
      startedAt: "2026-11-20T06:00:00Z",
      finishedAt: null,
      // Payload giả lập cố tình gán score: 0 để kiểm chứng guard không rò điểm khi chưa SCORED.
      score: 0,
      maxScore: 1200,
    },
    {
      attemptId: "d4e5f6a7-0004-4000-8000-000000000004",
      examId: "cccc3333-3333-4333-8333-333333333333",
      examName: "Đề luyện THPTQG Vật lí",
      examKindCode: "THPTQG",
      scope: "SUBJECT",
      attemptNo: 1,
      status: "GRADING",
      startedAt: "2026-11-20T07:00:00Z",
      finishedAt: "2026-11-20T07:45:00Z",
      score: 0,
      maxScore: 10,
    },
    {
      attemptId: "e5f6a7b8-0005-4000-8000-000000000005",
      examId: "dddd4444-4444-4444-8444-444444444444",
      examName: "Đề đánh giá BCA Tự luận",
      examKindCode: "BCA",
      scope: "FULL",
      attemptNo: null,
      status: "PENDING_REVIEW",
      startedAt: "2026-11-20T08:00:00Z",
      finishedAt: "2026-11-20T09:00:00Z",
      score: 0,
      maxScore: 100,
    },
    {
      attemptId: "f6a7b8c9-0006-4000-8000-000000000006",
      examId: "eeee5555-5555-4555-8555-555555555555",
      examName: "Đề thi SPT Tin học",
      examKindCode: "SPT",
      scope: "SUBJECT",
      attemptNo: 3,
      status: "GRADING_ERROR",
      startedAt: null,
      finishedAt: null,
      score: 9.99,
      maxScore: 10,
    },
  ],
  assessments: [],
  practice: [],
  supportRequests: [],
};

function makeDeps(overrides: Partial<UsersApiDeps> = {}): Partial<UsersApiDeps> {
  return {
    ...defaultUsersApiDeps,
    getUser: vi.fn<UsersApiDeps["getUser"]>(async () => BASE_USER),
    getUserLearning: vi.fn<UsersApiDeps["getUserLearning"]>(async () => EMPTY_LEARNING),
    ...overrides,
  };
}

function renderDetail(
  deps: Partial<UsersApiDeps>,
  session: Session = TEST_ADMIN_SESSION,
  options?: { locale?: "vi" | "en"; messages?: typeof viMessages },
) {
  return renderWithProviders(
    <SessionProvider loadSession={async () => session}>
      <UserDetailView userId={BASE_USER.id} deps={deps} />
    </SessionProvider>,
    options,
  );
}

function SessionStatusProbe() {
  const session = useSession();
  return session.status === "ready" ? <span data-testid="session-ready" /> : null;
}

function collectTextOutsideExamContent(root: HTMLElement): string {
  const parts: string[] = [];
  const walker = document.createTreeWalker(root, NodeFilter.SHOW_TEXT);
  let node = walker.nextNode();
  while (node) {
    const parent = node.parentElement;
    if (parent && !parent.closest("[data-exam-content]")) {
      const value = node.textContent ?? "";
      if (value.trim().length > 0) parts.push(value);
    }
    node = walker.nextNode();
  }
  return parts.join(" ");
}

function expectNoRawEnumTokens(container: HTMLElement): void {
  const visibleText = container.textContent ?? "";
  for (const token of RAW_ENUM_TOKENS) {
    expect(visibleText).not.toContain(token);
  }
}

afterEach(() => {
  vi.unstubAllGlobals();
  vi.restoreAllMocks();
});

describe("LearningProfileTab (LP / UNI-2743)", () => {
  it("hiển thị tab Hồ sơ học tập khi có USER.LEARNING_VIEW, hiện skeleton khi đang tải và đủ 5 khối rỗng", async () => {
    const deferred = Promise.withResolvers<StudentLearning>();
    const getUserLearning = vi.fn<UsersApiDeps["getUserLearning"]>(() => deferred.promise);

    renderDetail(makeDeps({ getUserLearning }));

    const learningTab = await screen.findByRole("tab", { name: "Hồ sơ học tập" });
    expect(getUserLearning).not.toHaveBeenCalled();

    await userEvent.click(learningTab);

    expect(getUserLearning).toHaveBeenCalledTimes(1);
    expect(getUserLearning).toHaveBeenCalledWith(BASE_USER.id);
    expect(await screen.findByRole("status", { name: viMessages.common.loading })).toBeInTheDocument();

    deferred.resolve(EMPTY_LEARNING);
    const blocks = viMessages.users.learning.blocks;
    for (const blockKey of [
      "entitlements",
      "attempts",
      "assessments",
      "practice",
      "supportRequests",
    ] as const) {
      const heading = await screen.findByRole("heading", { name: blocks[blockKey].title });
      const card = heading.closest("[data-slot='learning-profile-block']") as HTMLElement;
      expect(card).toBeInTheDocument();
      expect(card).toHaveAttribute("data-block", blockKey);
      expect(within(card).getByText(viMessages.users.learning.emptyTitle)).toBeInTheDocument();
      expect(within(card).getByText(blocks[blockKey].description)).toBeInTheDocument();
    }

    expect(screen.getAllByText(viMessages.users.learning.emptyTitle)).toHaveLength(5);

    // Hai cột theo bề rộng vùng chứa (trang chi tiết là `@container`), entitlements/attempts/supportRequests chiếm 2 cột.
    const grid = document.querySelector<HTMLElement>("[data-slot='learning-profile-tab']")!;
    expect(grid).toHaveClass("grid-cols-1", "@2xl:grid-cols-2");
    expect(grid.closest(".\\@container")).not.toBeNull();
    expect(grid.querySelector("[data-block='entitlements']")).toHaveClass("@2xl:col-span-2");
    expect(grid.querySelector("[data-block='attempts']")).toHaveClass("@2xl:col-span-2");
    expect(grid.querySelector("[data-block='supportRequests']")).toHaveClass("@2xl:col-span-2");
  });

  it("ẩn tab Hồ sơ học tập và không gọi API learning khi thiếu quyền USER.LEARNING_VIEW", async () => {
    const getUserLearning = vi.fn<UsersApiDeps["getUserLearning"]>(async () => EMPTY_LEARNING);
    const deps = makeDeps({ getUserLearning });

    renderDetail(deps, {
      ...TEST_ADMIN_SESSION,
      permissions: ["USER.VIEW", "USER.MODIFY", "AUDIT.VIEW"],
    });

    expect(await screen.findByRole("heading", { name: "Học viên 01" })).toBeInTheDocument();
    expect(screen.queryByRole("tab", { name: "Hồ sơ học tập" })).not.toBeInTheDocument();
    expect(getUserLearning).not.toHaveBeenCalled();
  });

  it("không gọi getUserLearning ngay cả khi LearningProfileTab được mount trực tiếp nếu thiếu USER.LEARNING_VIEW", async () => {
    const getUserLearning = vi.fn<UsersApiDeps["getUserLearning"]>(async () => EMPTY_LEARNING);

    renderWithProviders(
      <SessionProvider loadSession={async () => ({ ...TEST_ADMIN_SESSION, permissions: ["USER.VIEW"] })}>
        <SessionStatusProbe />
        <LearningProfileTab userId={BASE_USER.id} getUserLearning={getUserLearning} />
      </SessionProvider>,
    );

    await screen.findByTestId("session-ready");
    expect(getUserLearning).not.toHaveBeenCalled();
    expect(screen.queryByText(viMessages.users.learning.emptyTitle)).not.toBeInTheDocument();
  });

  it("hiển thị trạng thái lỗi kèm nút Thử lại, hiện skeleton khi đang tải lại và thành công sau khi thử lại", async () => {
    const retryDeferred = Promise.withResolvers<StudentLearning>();
    const getUserLearning = vi
      .fn<UsersApiDeps["getUserLearning"]>()
      .mockRejectedValueOnce(new UsersApiError(500, "Server error", "INTERNAL_ERROR"))
      .mockImplementationOnce(() => retryDeferred.promise);

    renderDetail(makeDeps({ getUserLearning }));

    await userEvent.click(await screen.findByRole("tab", { name: "Hồ sơ học tập" }));

    expect(await screen.findByText(viMessages.users.learning.errorTitle)).toBeInTheDocument();
    expect(screen.getByText(viMessages.users.learning.errorDescription)).toBeInTheDocument();

    await userEvent.click(screen.getByRole("button", { name: viMessages.common.retry }));

    expect(await screen.findByRole("status", { name: viMessages.common.loading })).toBeInTheDocument();

    retryDeferred.resolve(EMPTY_LEARNING);

    expect(
      await screen.findByRole("heading", { name: viMessages.users.learning.blocks.entitlements.title }),
    ).toBeInTheDocument();
    expect(getUserLearning).toHaveBeenCalledTimes(2);
  });

  it("hai khối có dữ liệu (AC-3): hiện đầy đủ quyền sử dụng và lượt thi, bảo vệ điểm SCORED và giữ 3 khối còn lại trống", async () => {
    const getUserLearning = vi.fn<UsersApiDeps["getUserLearning"]>(async () => POPULATED_LEARNING);
    const { container } = renderDetail(makeDeps({ getUserLearning }));

    await userEvent.click(await screen.findByRole("tab", { name: "Hồ sơ học tập" }));

    const tabGrid = await screen.findByRole("heading", { name: viMessages.users.learning.blocks.entitlements.title });
    const rootGrid = tabGrid.closest("[data-slot='learning-profile-tab']") as HTMLElement;
    expect(rootGrid).toHaveAttribute("data-data-available", "true");

    // 1. Khối Quyền sử dụng: bảng dữ liệu, đủ 4 trạng thái, không hiện emptyTitle.
    const entitlementsBlock = rootGrid.querySelector<HTMLElement>("[data-block='entitlements']")!;
    expect(within(entitlementsBlock).queryByText(viMessages.users.learning.emptyTitle)).not.toBeInTheDocument();
    const entHeader = within(entitlementsBlock).getByRole("heading", {
      name: viMessages.users.learning.blocks.entitlements.title,
    }).parentElement!;
    expect(within(entHeader).getByText("4")).toBeInTheDocument();
    const entTable = within(entitlementsBlock).getByRole("table", {
      name: viMessages.users.learning.entitlementsTable.ariaLabel,
    });
    const entRows = within(entTable).getAllByRole("row").slice(1);
    expect(entRows).toHaveLength(4);
    expect(within(entRows[0]!).getByText("Đề thi thử HSA Toán")).toHaveAttribute("data-exam-content");
    expect(within(entRows[0]!).getByText("HSA-TOAN-2026")).toHaveAttribute("data-exam-content");
    expect(within(entRows[0]!).getAllByRole("cell").slice(1, 4).map((cell) => cell.textContent)).toEqual(["4", "2", "1"]);
    // Khi examName null, rơi về examCode với data-exam-content.
    expect(within(entRows[1]!).getByText("VACT-FULL-01")).toHaveAttribute("data-exam-content");
    expect(within(entTable).getByText("Còn lượt").closest("[data-tone]")).toHaveAttribute("data-tone", "success");
    expect(within(entTable).getByText("Đang làm bài").closest("[data-tone]")).toHaveAttribute("data-tone", "info");
    expect(within(entTable).getByText("Hết lượt").closest("[data-tone]")).toHaveAttribute("data-tone", "neutral");
    expect(within(entTable).getByText("Đã thu hồi").closest("[data-tone]")).toHaveAttribute("data-tone", "danger");

    // 2. Khối Lịch sử lượt thi: bảng dữ liệu, kiểm chứng định dạng số vi (85,5 / 100), 0 / 100 khi SCORED, và "—" khi chưa SCORED.
    const attemptsBlock = rootGrid.querySelector<HTMLElement>("[data-block='attempts']")!;
    expect(within(attemptsBlock).queryByText(viMessages.users.learning.emptyTitle)).not.toBeInTheDocument();
    const attHeader = within(attemptsBlock).getByRole("heading", {
      name: viMessages.users.learning.blocks.attempts.title,
    }).parentElement!;
    expect(within(attHeader).getByText("6")).toBeInTheDocument();
    const attTable = within(attemptsBlock).getByRole("table", {
      name: viMessages.users.learning.attemptsTable.ariaLabel,
    });
    const attRows = within(attTable).getAllByRole("row").slice(1);
    expect(attRows).toHaveLength(6);

    // Hàng 0: SCORED với điểm thập phân 85,5 / 100 và mã lượt.
    expect(within(attRows[0]!).getByText("Lượt #1")).toBeInTheDocument();
    expect(within(attRows[0]!).getByText("A1B2C3D4")).toBeInTheDocument();
    expect(within(attRows[0]!).getByText("85,5 / 100")).toBeInTheDocument();
    expect(within(attRows[0]!).getByText("Đã chấm").closest("[data-tone]")).toHaveAttribute("data-tone", "success");

    // Hàng 1: SCORED với score = 0 vẫn hiện "0 / 100".
    expect(within(attRows[1]!).getByText("0 / 100")).toBeInTheDocument();

    // Hàng 2, 3, 4, 5: IN_PROGRESS, GRADING, PENDING_REVIEW, GRADING_ERROR -> tuyệt đối chỉ hiện "—", không hiện 0 hay 9,99.
    const pendingScores: Array<{ row: HTMLElement; statusLabel: string; tone: string }> = [
      { row: attRows[2]!, statusLabel: "Đang làm", tone: "info" },
      { row: attRows[3]!, statusLabel: "Đang chấm", tone: "warning" },
      { row: attRows[4]!, statusLabel: "Chờ chấm", tone: "warning" },
      { row: attRows[5]!, statusLabel: "Lỗi chấm", tone: "danger" },
    ];
    for (const { row, statusLabel, tone } of pendingScores) {
      expect(within(row).getByText(statusLabel).closest("[data-tone]")).toHaveAttribute("data-tone", tone);
      const cells = within(row).getAllByRole("cell");
      const scoreCell = cells[4]!;
      expect(scoreCell).toHaveTextContent("—");
      expect(scoreCell.textContent).not.toMatch(/0|9[.,]99/);
    }
    // Hàng 4 có attemptNo = null: chỉ hiện short UUID E5F6A7B8.
    expect(within(attRows[4]!).getByText("E5F6A7B8")).toBeInTheDocument();
    expect(within(attRows[4]!).queryByText(/Lượt #/)).not.toBeInTheDocument();
    // Hàng 5 có startedAt = null: ô thời điểm bắt đầu hiện "—".
    expect(within(attRows[5]!).getAllByRole("cell")[3]).toHaveTextContent("—");

    // 3. Ba khối còn lại vẫn luôn ở trạng thái trống (đúng 3 emptyTitle).
    for (const staticKey of ["assessments", "practice", "supportRequests"] as const) {
      const block = rootGrid.querySelector<HTMLElement>(`[data-block='${staticKey}']`)!;
      expect(within(block).getByText(viMessages.users.learning.emptyTitle)).toBeInTheDocument();
      expect(within(block).getByText(viMessages.users.learning.blocks[staticKey].description)).toBeInTheDocument();
    }
    expect(screen.getAllByText(viMessages.users.learning.emptyTitle)).toHaveLength(3);

    // 4. Kiểm tra data-exam-content chỉ bọc tên/mã bài thi, không bọc nhãn UI hay chip trạng thái.
    const statusChip = within(attTable).getByText("Chờ chấm");
    expect(statusChip.closest("[data-exam-content]")).toBeNull();
    const examHeader = within(attTable).getByRole("columnheader", {
      name: viMessages.users.learning.attemptsTable.columns.exam,
    });
    expect(examHeader.closest("[data-exam-content]")).toBeNull();
    expectNoRawEnumTokens(container);
  });

  it("dữ liệu một phần: chỉ entitlements có dữ liệu hoặc chỉ attempts có dữ liệu cho đúng 4 khối trống", async () => {
    const onlyEntitlements: StudentLearning = {
      ...EMPTY_LEARNING,
      dataAvailable: true,
      entitlements: [POPULATED_LEARNING.entitlements[0]!],
    };

    const { unmount } = renderDetail(
      makeDeps({ getUserLearning: vi.fn<UsersApiDeps["getUserLearning"]>(async () => onlyEntitlements) }),
    );

    await userEvent.click(await screen.findByRole("tab", { name: "Hồ sơ học tập" }));
    expect(await screen.findByRole("table", { name: viMessages.users.learning.entitlementsTable.ariaLabel })).toBeInTheDocument();
    expect(screen.getAllByText(viMessages.users.learning.emptyTitle)).toHaveLength(4);
    expect(
      screen.getByText(viMessages.users.learning.blocks.attempts.description),
    ).toBeInTheDocument();
    unmount();

    const onlyAttempts: StudentLearning = {
      ...EMPTY_LEARNING,
      dataAvailable: true,
      attempts: [POPULATED_LEARNING.attempts[0]!],
    };
    renderDetail(
      makeDeps({ getUserLearning: vi.fn<UsersApiDeps["getUserLearning"]>(async () => onlyAttempts) }),
    );
    await userEvent.click(await screen.findByRole("tab", { name: "Hồ sơ học tập" }));
    expect(await screen.findByRole("table", { name: viMessages.users.learning.attemptsTable.ariaLabel })).toBeInTheDocument();
    expect(screen.getAllByText(viMessages.users.learning.emptyTitle)).toHaveLength(4);
    expect(
      screen.getByText(viMessages.users.learning.blocks.entitlements.description),
    ).toBeInTheDocument();
  });

  it("hiển thị đầy đủ 5 khối bằng tiếng Anh khi locale là en (rỗng và có dữ liệu, không lọt dấu tiếng Việt ngoài [data-exam-content])", async () => {
    const emptyRender = renderDetail(makeDeps(), TEST_ADMIN_SESSION, { locale: "en", messages: en });

    await userEvent.click(await screen.findByRole("tab", { name: en.users.learning.tabLabel }));

    const blocks = en.users.learning.blocks;
    for (const blockKey of [
      "entitlements",
      "attempts",
      "assessments",
      "practice",
      "supportRequests",
    ] as const) {
      expect(await screen.findByRole("heading", { name: blocks[blockKey].title })).toBeInTheDocument();
      expect(screen.getByText(blocks[blockKey].description)).toBeInTheDocument();
    }
    expect(screen.getAllByText(en.users.learning.emptyTitle)).toHaveLength(5);
    expect(screen.queryByText("Chưa có dữ liệu")).not.toBeInTheDocument();
    emptyRender.unmount();

    // Locale en với dữ liệu thật (tên bài thi tiếng Việt giữ nguyên bên trong [data-exam-content]).
    const enDataRender = renderWithProviders(
      <SessionProvider loadSession={async () => TEST_ADMIN_SESSION}>
        <LearningProfileTab
          userId={BASE_USER.id}
          getUserLearning={vi.fn<UsersApiDeps["getUserLearning"]>(async () => POPULATED_LEARNING)}
        />
      </SessionProvider>,
      { locale: "en", messages: en },
    );

    const entTable = await screen.findByRole("table", {
      name: en.users.learning.entitlementsTable.ariaLabel,
    });
    const attTable = screen.getByRole("table", {
      name: en.users.learning.attemptsTable.ariaLabel,
    });

    // Điểm thập phân kiểu en (dấu chấm), ngày giờ theo Asia/Ho_Chi_Minh (03:15Z -> 10:15, 20/11/2026).
    expect(within(attTable).getByText("85.5 / 100")).toBeInTheDocument();
    expect(within(attTable).getByText("0 / 100")).toBeInTheDocument();
    expect(within(attTable).getByText("20/11/2026, 10:15")).toBeInTheDocument();
    expect(within(attTable).getByText("Awaiting grading")).toBeInTheDocument();
    expect(within(entTable).getByText("Active")).toBeInTheDocument();
    expect(within(entTable).getByText("In use")).toBeInTheDocument();
    expect(within(entTable).getByText("Exhausted")).toBeInTheDocument();
    expect(within(entTable).getByText("Revoked")).toBeInTheDocument();

    // Nội dung bài thi tiếng Việt nằm trong [data-exam-content].
    expect(within(attTable).getByText("Đề thi thử HSA Toán")).toHaveAttribute("data-exam-content");

    // Ngoài [data-exam-content], tuyệt đối không có ký tự tiếng Việt có dấu.
    const textOutsideExamContent = collectTextOutsideExamContent(enDataRender.container);
    expect(VIETNAMESE_DIACRITIC_RE.test(textOutsideExamContent)).toBe(false);
    expectNoRawEnumTokens(enDataRender.container);
  });

  it("khung hẹp 358px: chuyển sang ListCard cho cả hai bảng với data-exam-content và guard điểm", async () => {
    installContainerWidth(PHONE_CONTENT_WIDTH);
    const { container } = renderWithProviders(
      <SessionProvider loadSession={async () => TEST_ADMIN_SESSION}>
        <LearningProfileTab
          userId={BASE_USER.id}
          getUserLearning={vi.fn<UsersApiDeps["getUserLearning"]>(async () => POPULATED_LEARNING)}
        />
      </SessionProvider>,
    );

    const entList = await screen.findByRole("list", {
      name: viMessages.users.learning.entitlementsTable.ariaLabel,
    });
    const attList = screen.getByRole("list", {
      name: viMessages.users.learning.attemptsTable.ariaLabel,
    });
    expect(screen.queryByRole("table")).not.toBeInTheDocument();

    const entCards = within(entList).getAllByRole("listitem");
    expect(entCards).toHaveLength(4);
    expect(within(entCards[0]!).getByText("Đề thi thử HSA Toán")).toHaveAttribute("data-exam-content");
    expect(within(entCards[0]!).getByText("HSA-TOAN-2026")).toHaveAttribute("data-exam-content");
    expect(entCards[0]!).toHaveTextContent("Đã cấp: 4 · Đã dùng: 2 · Còn lại: 1 · Đang giữ: 1");
    expect(entCards[3]!).toHaveTextContent("Đã thu hồi: 3");

    const attCards = within(attList).getAllByRole("listitem");
    expect(attCards).toHaveLength(6);
    expect(within(attCards[0]!).getByText("Đề thi thử HSA Toán")).toHaveAttribute("data-exam-content");
    expect(attCards[0]!).toHaveTextContent("Lượt #1 · A1B2C3D4");
    expect(attCards[0]!).toHaveTextContent("Điểm: 85,5 / 100");
    expect(attCards[1]!).toHaveTextContent("Điểm: 0 / 100");
    expect(attCards[4]!).toHaveTextContent("E5F6A7B8");
    expect(attCards[4]!).toHaveTextContent("Điểm: —");
    expect(attCards[5]!).toHaveTextContent("Điểm: —");
    expect(attCards[5]!).not.toHaveTextContent("9,99");
    expectNoRawEnumTokens(container);
  });

  it("dự phòng khi thiếu examName/examCode, maxScore null hoặc SCORED thiếu score", async () => {
    const edgeData: StudentLearning = {
      ...EMPTY_LEARNING,
      dataAvailable: true,
      entitlements: [
        {
          ...POPULATED_LEARNING.entitlements[0]!,
          entitlementId: "ent-edge-1",
          examId: "eeee0000-0000-4000-8000-000000000099",
          examName: "",
          examCode: null,
        },
      ],
      attempts: [
        {
          ...POPULATED_LEARNING.attempts[0]!,
          attemptId: "att-edge-1-0000-4000-8000-000000000001",
          score: 85.5,
          maxScore: null,
        },
        {
          ...POPULATED_LEARNING.attempts[0]!,
          attemptId: "att-edge-2-0000-4000-8000-000000000002",
          score: null,
          maxScore: 100,
        },
      ],
    };

    renderWithProviders(
      <SessionProvider loadSession={async () => TEST_ADMIN_SESSION}>
        <LearningProfileTab
          userId={BASE_USER.id}
          getUserLearning={vi.fn<UsersApiDeps["getUserLearning"]>(async () => edgeData)}
        />
      </SessionProvider>,
    );

    const entTable = await screen.findByRole("table", {
      name: viMessages.users.learning.entitlementsTable.ariaLabel,
    });
    expect(within(entTable).getByText("eeee0000-0000-4000-8000-000000000099")).toHaveAttribute("data-exam-content");

    const attTable = screen.getByRole("table", {
      name: viMessages.users.learning.attemptsTable.ariaLabel,
    });
    const attRows = within(attTable).getAllByRole("row").slice(1);
    expect(within(attRows[0]!).getAllByRole("cell")[4]).toHaveTextContent("85,5");
    expect(within(attRows[0]!).getAllByRole("cell")[4]?.textContent).not.toContain("/");
    expect(within(attRows[1]!).getAllByRole("cell")[4]).toHaveTextContent("—");
  });
});
