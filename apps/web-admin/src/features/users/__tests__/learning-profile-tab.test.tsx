import { screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, describe, expect, it, vi } from "vitest";
import { SessionProvider } from "@/features/session/session-provider";
import type { Session } from "@/features/session/types";
import en from "@/messages/en.json";
import viMessages from "@/messages/vi.json";
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
}));

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

afterEach(() => {
  vi.restoreAllMocks();
});
describe("LearningProfileTab (T12 / UNI-2549)", () => {
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
        <LearningProfileTab userId={BASE_USER.id} getUserLearning={getUserLearning} />
      </SessionProvider>,
    );

    await waitFor(() => {
      expect(getUserLearning).not.toHaveBeenCalled();
    });
    expect(screen.queryByText(viMessages.users.learning.emptyTitle)).not.toBeInTheDocument();
  });

  it("hiển thị trạng thái lỗi kèm nút Thử lại và tải lại thành công khi bấm Thử lại", async () => {
    const getUserLearning = vi
      .fn<UsersApiDeps["getUserLearning"]>()
      .mockRejectedValueOnce(new UsersApiError(500, "Server error", "INTERNAL_ERROR"))
      .mockResolvedValueOnce(EMPTY_LEARNING);

    renderDetail(makeDeps({ getUserLearning }));

    await userEvent.click(await screen.findByRole("tab", { name: "Hồ sơ học tập" }));

    expect(await screen.findByText(viMessages.users.learning.errorTitle)).toBeInTheDocument();
    expect(screen.getByText(viMessages.users.learning.errorDescription)).toBeInTheDocument();

    await userEvent.click(screen.getByRole("button", { name: viMessages.common.retry }));

    expect(await screen.findByRole("heading", { name: viMessages.users.learning.blocks.entitlements.title })).toBeInTheDocument();
    expect(getUserLearning).toHaveBeenCalledTimes(2);
  });

  it("hiển thị đầy đủ 5 khối bằng tiếng Anh khi locale là en", async () => {
    const deps = makeDeps();
    renderDetail(deps, TEST_ADMIN_SESSION, { locale: "en", messages: en });

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
  });
});
