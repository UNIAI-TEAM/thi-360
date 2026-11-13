import { screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, describe, expect, it, vi } from "vitest";
import { SessionProvider } from "@/features/session/session-provider";
import type { Session } from "@/features/session/types";
import { TEST_ADMIN_SESSION } from "@/test/fixtures";
import { renderWithProviders } from "@/test/render";
import { defaultUsersApiDeps, type UsersApiDeps, type UserSummary } from "../api";
import { UsersListView } from "../components/users-list-view";

const SAMPLE_USERS: UserSummary[] = [
  {
    id: "00000000-0000-4000-8000-000000000101",
    code: "U000001",
    username: "student01",
    fullName: "Học viên 01",
    email: "student01@example.com",
    phone: "0901000001",
    source: "ADMIN_CREATED",
    status: "ACTIVE",
    mustChangePassword: false,
    lastLoginAt: "2026-11-12T10:00:00Z",
    roles: [{ id: "r-student", code: "STUDENT", name: "Học viên", access: "EXAM" }],
    version: 0,
    createdAt: "2026-11-10T08:00:00Z",
    updatedAt: "2026-11-12T10:00:00Z",
  },
  {
    id: "00000000-0000-4000-8000-000000000102",
    code: "U000002",
    username: "student02",
    fullName: "Học viên 02",
    source: "BULK_IMPORT",
    status: "ACTIVE",
    mustChangePassword: false,
    roles: [{ id: "r-student", code: "STUDENT", name: "Học viên", access: "EXAM" }],
    version: 0,
    createdAt: "2026-11-10T08:00:00Z",
    updatedAt: "2026-11-10T08:00:00Z",
  },
  {
    id: "00000000-0000-4000-8000-000000000103",
    code: "U000003",
    username: "student03",
    fullName: "Học viên 03",
    email: "student03@example.com",
    source: "SELF_REGISTERED",
    status: "INACTIVE",
    mustChangePassword: false,
    lastLoginAt: "2026-11-05T10:00:00Z",
    roles: [{ id: "r-student", code: "STUDENT", name: "Học viên", access: "EXAM" }],
    version: 1,
    createdAt: "2026-11-01T08:00:00Z",
    updatedAt: "2026-11-06T08:00:00Z",
  },
  {
    id: "00000000-0000-4000-8000-000000000104",
    code: "U000004",
    username: "student04",
    fullName: "Học viên 04",
    source: "ADMIN_CREATED",
    status: "INACTIVE",
    mustChangePassword: false,
    deletedAt: "2026-11-11T09:00:00Z",
    roles: [{ id: "r-student", code: "STUDENT", name: "Học viên", access: "EXAM" }],
    version: 2,
    createdAt: "2026-11-01T08:00:00Z",
    updatedAt: "2026-11-11T09:00:00Z",
  },
];

function makeDeps(overrides: Partial<UsersApiDeps> = {}): Partial<UsersApiDeps> {
  return {
    ...defaultUsersApiDeps,
    listUsers: vi.fn<UsersApiDeps["listUsers"]>(async () => ({
      items: SAMPLE_USERS,
      page: 0,
      size: 20,
      totalItems: SAMPLE_USERS.length,
      totalPages: 1,
    })),
    listActiveRoles: vi.fn<UsersApiDeps["listActiveRoles"]>(async () => [
      {
        id: "r-student",
        code: "STUDENT",
        name: "Học viên",
        access: "EXAM",
        status: "ACTIVE",
        system: true,
        permissionCount: 0,
        userCount: 4,
        version: 0,
        createdAt: "2026-11-01T00:00:00Z",
        updatedAt: "2026-11-01T00:00:00Z",
      },
      {
        id: "r-ops",
        code: "OPERATIONS",
        name: "Vận hành / CSKH",
        access: "MANAGE",
        status: "ACTIVE",
        system: true,
        permissionCount: 5,
        userCount: 1,
        version: 0,
        createdAt: "2026-11-01T00:00:00Z",
        updatedAt: "2026-11-01T00:00:00Z",
      },
    ]),
    checkUsernameAvailability: vi.fn<UsersApiDeps["checkUsernameAvailability"]>(async () => ({
      available: true,
    })),
    createUser: vi.fn<UsersApiDeps["createUser"]>(async (body) => ({
      id: "00000000-0000-4000-8000-000000000999",
      code: "U000999",
      username: body.username,
      fullName: body.fullName,
      email: body.email,
      phone: body.phone,
      source: "ADMIN_CREATED",
      status: "ACTIVE",
      mustChangePassword: body.mustChangePassword ?? false,
      roles: [{ id: "r-student", code: "STUDENT", name: "Học viên", access: "EXAM" }],
      version: 0,
      createdAt: "2026-11-14T00:00:00Z",
      updatedAt: "2026-11-14T00:00:00Z",
    })),
    ...overrides,
  };
}

function renderUsersView(
  deps: Partial<UsersApiDeps>,
  session: Session = TEST_ADMIN_SESSION,
  showClassFilter = false,
) {
  return renderWithProviders(
    <SessionProvider loadSession={async () => session}>
      <UsersListView deps={deps} showClassFilter={showClassFilter} />
    </SessionProvider>,
  );
}

afterEach(() => {
  vi.restoreAllMocks();
});

describe("UsersListView (Slice 1)", () => {
  it("chặn trang khi thiếu quyền USER.VIEW", async () => {
    const deps = makeDeps();
    renderUsersView(deps, { ...TEST_ADMIN_SESSION, permissions: [] });

    expect(await screen.findByText("Bạn không có quyền vào trang này")).toBeInTheDocument();
    expect(deps.listUsers).not.toHaveBeenCalled();
  });

  it("hiện đủ 4 nhãn trạng thái, ẩn cột Mã và Đăng nhập cuối, ẩn nút tạo khi chỉ có USER.VIEW", async () => {
    const deps = makeDeps();
    renderUsersView(deps, { ...TEST_ADMIN_SESSION, permissions: ["USER.VIEW"] });

    expect(await screen.findByText("student01")).toBeInTheDocument();
    expect(screen.getByText("Hoạt động", { selector: "[data-tone='success']" })).toBeInTheDocument();
    expect(screen.getByText("Chưa đăng nhập", { selector: "[data-tone='warning']" })).toBeInTheDocument();
    expect(screen.getByText("Ngừng hoạt động", { selector: "[data-tone='danger']" })).toBeInTheDocument();
    expect(screen.getByText("Đã xóa", { selector: "[data-tone='neutral']" })).toBeInTheDocument();

    expect(screen.queryByRole("columnheader", { name: "Mã" })).not.toBeInTheDocument();
    expect(screen.queryByRole("columnheader", { name: "Đăng nhập cuối" })).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: /Tạo tài khoản/ })).not.toBeInTheDocument();
    expect(screen.queryByLabelText("Lớp")).not.toBeInTheDocument();
  });

  it("gửi đúng tham số bộ lọc tìm kiếm, chưa đăng nhập và hiện tài khoản đã xóa", async () => {
    const listUsersMock = vi.fn(async () => ({
      items: [],
      page: 0,
      size: 20,
      totalItems: 0,
      totalPages: 0,
    }));
    const deps = makeDeps({ listUsers: listUsersMock });
    renderUsersView(deps);

    await screen.findByText("Chưa có tài khoản");

    const searchBox = screen.getByLabelText("Tìm kiếm");
    await userEvent.type(searchBox, "student05");

    await waitFor(() => {
      expect(listUsersMock).toHaveBeenLastCalledWith(
        expect.objectContaining({ q: "student05", page: 0, size: 20 }),
      );
    });

    await userEvent.click(screen.getByLabelText("Chưa đăng nhập"));
    await userEvent.click(screen.getByLabelText("Hiện tài khoản đã xóa"));

    await waitFor(() => {
      expect(listUsersMock).toHaveBeenLastCalledWith(
        expect.objectContaining({
          q: "student05",
          neverLoggedIn: true,
          includeDeleted: true,
        }),
      );
    });

    expect(screen.getByText("Không tìm thấy tài khoản")).toBeInTheDocument();
  });

  it("trạng thái lỗi hiện nút Thử lại và nạp lại danh sách", async () => {
    const listUsersMock = vi
      .fn()
      .mockRejectedValueOnce(new Error("Network"))
      .mockResolvedValueOnce({
        items: SAMPLE_USERS.slice(0, 1),
        page: 0,
        size: 20,
        totalItems: 1,
        totalPages: 1,
      });

    renderUsersView(makeDeps({ listUsers: listUsersMock }));

    const retry = await screen.findByRole("button", { name: "Thử lại" });
    await userEvent.click(retry);

    expect(await screen.findByText("student01")).toBeInTheDocument();
  });

  it("CreateUserDialog kiểm tra tên đăng nhập khi blur, kiểm tra 5 điều kiện mật khẩu và sinh mật khẩu ngẫu nhiên", async () => {
    const checkMock = vi
      .fn()
      .mockResolvedValueOnce({ available: false, reasonCode: "USERNAME_TAKEN" })
      .mockResolvedValueOnce({ available: true });
    const createMock = vi.fn(async (body) => ({
      id: "00000000-0000-4000-8000-000000000777",
      code: "U000777",
      username: body.username,
      fullName: body.fullName,
      source: "ADMIN_CREATED" as const,
      status: "ACTIVE" as const,
      mustChangePassword: body.mustChangePassword ?? false,
      roles: [],
      version: 0,
      createdAt: "2026-11-14T00:00:00Z",
      updatedAt: "2026-11-14T00:00:00Z",
    }));

    const deps = makeDeps({
      checkUsernameAvailability: checkMock,
      createUser: createMock,
    });
    renderUsersView(deps);

    await userEvent.click(await screen.findByRole("button", { name: /Tạo tài khoản/ }));

    const usernameInput = screen.getByLabelText(/Tên đăng nhập/);
    await userEvent.type(usernameInput, "student01");
    await userEvent.tab();

    expect(await screen.findByText("Tên đăng nhập đã được sử dụng.")).toBeInTheDocument();
    expect(checkMock).toHaveBeenCalledWith("student01");

    await userEvent.clear(usernameInput);
    await userEvent.type(usernameInput, "student06");
    await userEvent.tab();

    expect(
      await screen.findByText(/Có thể dùng\. 4–30 ký tự/),
    ).toBeInTheDocument();

    await userEvent.click(screen.getByRole("button", { name: "Tạo ngẫu nhiên" }));
    const pwInput = screen.getByLabelText(/Mật khẩu/);
    expect(pwInput).toHaveAttribute("type", "text");
    const generated = (pwInput as HTMLInputElement).value;
    expect(generated.length).toBe(12);

    const rulesList = screen.getByRole("list", { name: "Điều kiện mật khẩu" });
    for (const item of rulesList.querySelectorAll("li")) {
      expect(item).toHaveAttribute("data-met", "true");
    }

    await userEvent.type(screen.getByLabelText(/Họ và tên/), "Học viên 06");
    await userEvent.click(screen.getByRole("button", { name: "Tạo tài khoản" }));

    await waitFor(() => {
      expect(createMock).toHaveBeenCalledWith(
        expect.objectContaining({
          username: "student06",
          password: generated,
          fullName: "Học viên 06",
          mustChangePassword: false,
        }),
      );
    });
  });
});
