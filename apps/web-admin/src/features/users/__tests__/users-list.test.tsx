import { screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, describe, expect, it, vi } from "vitest";
import { SessionProvider } from "@/features/session/session-provider";
import type { Session } from "@/features/session/types";
import { TEST_ADMIN_SESSION } from "@/test/fixtures";
import en from "@/messages/en.json";
import { renderWithProviders } from "@/test/render";
import { defaultUsersApiDeps, type UsersApiDeps, type UserSummary } from "../api";
import { UsersListView } from "../components/users-list-view";

const routerMock = vi.hoisted(() => ({ push: vi.fn() }));
vi.mock("next/navigation", () => ({
  useRouter: () => routerMock,
}));
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
    listAllClasses: vi.fn<UsersApiDeps["listAllClasses"]>(async () => [
      {
        id: "00000000-0000-4000-8000-000000000301",
        code: "LOP-A",
        name: "Lớp A",
        status: "ACTIVE",
        memberCount: 2,
        version: 0,
        createdAt: "2026-11-10T08:00:00Z",
        updatedAt: "2026-11-10T08:00:00Z",
      },
      {
        id: "00000000-0000-4000-8000-000000000302",
        code: "LOP-B",
        name: "Lớp B",
        status: "ACTIVE",
        memberCount: 2,
        version: 0,
        createdAt: "2026-11-10T08:00:00Z",
        updatedAt: "2026-11-10T08:00:00Z",
      },
    ]),
    getUserClasses: vi.fn<UsersApiDeps["getUserClasses"]>(async (userId) => ({
      userId,
      currentClasses: [],
      history: [],
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
  routerMock.push.mockReset();
  window.sessionStorage.clear();
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

    expect(await screen.findByText("student01", undefined, { timeout: 3000 })).toBeInTheDocument();
    expect(screen.getByText("Hoạt động", { selector: "[data-tone='success']" })).toBeInTheDocument();
    expect(screen.getByText("Chưa đăng nhập", { selector: "[data-tone='warning']" })).toBeInTheDocument();
    expect(screen.getByText("Ngừng hoạt động", { selector: "[data-tone='danger']" })).toBeInTheDocument();
    expect(screen.getByText("Đã xóa", { selector: "[data-tone='neutral']" })).toBeInTheDocument();

    expect(screen.queryByRole("columnheader", { name: "Mã" })).not.toBeInTheDocument();
    expect(screen.queryByRole("columnheader", { name: "Đăng nhập cuối" })).not.toBeInTheDocument();
    expect(screen.queryByRole("columnheader", { name: "Số điện thoại" })).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: /Tạo tài khoản/ })).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: /Thao tác với/ })).not.toBeInTheDocument();
    expect(screen.queryByRole("checkbox", { name: "Chọn tất cả tài khoản" })).not.toBeInTheDocument();
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

    await screen.findByText("Chưa có tài khoản", undefined, { timeout: 3000 });

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
    expect(screen.queryByText("0 tài khoản khớp bộ lọc")).not.toBeInTheDocument();
    expect(screen.getAllByText("Không tải được danh sách").length).toBeGreaterThan(0);
    await userEvent.click(retry);

    expect(await screen.findByText("student01")).toBeInTheDocument();
  });

  it("khi đang tải danh sách không hiển thị 0 tài khoản khớp bộ lọc", async () => {
    const pendingList = new Promise<never>(() => {});
    renderUsersView(makeDeps({ listUsers: vi.fn(() => pendingList) }));

    expect(await screen.findByText("Đang tải danh sách tài khoản...")).toBeInTheDocument();
    expect(screen.queryByText("0 tài khoản khớp bộ lọc")).not.toBeInTheDocument();
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

  it("thanh chọn nhiều dòng gọi preview/apply với Idempotency-Key và nút Gán vai trò, quyền ghi sessionStorage", async () => {
    const previewMock = vi.fn<UsersApiDeps["previewBulkUserActions"]>(async () => ({
      total: 2,
      affected: 1,
      skipped: [
        {
          id: SAMPLE_USERS[3]!.id,
          username: "student04",
          reasonCode: "USER_NOT_AVAILABLE",
          reason: "Tài khoản không tồn tại hoặc đã bị xóa",
        },
      ],
    }));
    const applyMock = vi.fn<UsersApiDeps["applyBulkUserActions"]>(async () => ({
      batchId: "batch-1",
      action: "DEACTIVATE",
      total: 2,
      succeeded: 1,
      skipped: 1,
      failed: 0,
      results: [
        {
          id: SAMPLE_USERS[0]!.id,
          username: "student01",
          outcome: "SUCCEEDED",
        },
        {
          id: SAMPLE_USERS[3]!.id,
          username: "student04",
          outcome: "SKIPPED",
          reasonCode: "USER_NOT_AVAILABLE",
          reason: "Tài khoản không tồn tại hoặc đã bị xóa",
        },
      ],
    }));

    const deps = makeDeps({
      previewBulkUserActions: previewMock,
      applyBulkUserActions: applyMock,
    });
    renderUsersView(deps);

    await screen.findByText("student01");
    await userEvent.click(screen.getByRole("checkbox", { name: "Chọn tài khoản student01" }));
    await userEvent.click(screen.getByRole("checkbox", { name: "Chọn tài khoản student04" }));

    const toolbar = await screen.findByRole("toolbar", {
      name: "Thao tác với các tài khoản đã chọn",
    });
    expect(toolbar).toHaveTextContent("Đã chọn 2 tài khoản");
    expect(screen.queryByRole("button", { name: "Cấp hàng loạt" })).not.toBeInTheDocument();
    expect(
      screen.getByText(/mở tab Gán hàng loạt của Phân quyền với 2 tài khoản này đã chọn sẵn/),
    ).toBeInTheDocument();

    await userEvent.click(screen.getByRole("button", { name: "Gán vai trò, quyền" }));
    expect(window.sessionStorage.getItem("rbac.bulk.userIds")).toBe(
      JSON.stringify([SAMPLE_USERS[0]!.id, SAMPLE_USERS[3]!.id]),
    );
    expect(routerMock.push).toHaveBeenCalledWith("/phan-quyen?tab=gan-hang-loat");

    await userEvent.click(screen.getByRole("button", { name: "Ngừng hoạt động" }));
    expect(previewMock).toHaveBeenCalledWith({
      action: "DEACTIVATE",
      userIds: [SAMPLE_USERS[0]!.id, SAMPLE_USERS[3]!.id],
    });

    expect(await screen.findByText("1 / 2 tài khoản đủ điều kiện thực hiện thao tác.")).toBeInTheDocument();
    expect(screen.getByText(/Tài khoản không tồn tại hoặc đã bị xóa/)).toBeInTheDocument();

    await userEvent.type(screen.getByLabelText(/Lý do thực hiện/), "Ngừng hàng loạt");
    await userEvent.click(screen.getByRole("button", { name: "Ngừng hoạt động 1 tài khoản" }));

    await waitFor(() => {
      expect(applyMock).toHaveBeenCalledWith(
        {
          action: "DEACTIVATE",
          userIds: [SAMPLE_USERS[0]!.id, SAMPLE_USERS[3]!.id],
          reason: "Ngừng hàng loạt",
        },
        expect.any(String),
      );
    });
    expect(await screen.findByText("Kết quả thao tác hàng loạt")).toBeInTheDocument();
  });

  it("hiển thị tên vai trò hệ thống theo khóa dịch khi locale là en và giữ nguyên tên vai trò tự tạo", async () => {
    const deps = makeDeps({
      listUsers: vi.fn<UsersApiDeps["listUsers"]>(async () => ({
        items: [
          {
            ...SAMPLE_USERS[0]!,
            id: "u-en-1",
            username: "ops.user",
            fullName: "Ops User",
            roles: [
              { id: "r-ops", code: "OPERATIONS", name: "Vận hành / CSKH", access: "MANAGE" },
              { id: "r-custom", code: "PROCTOR", name: "Exam Proctor", access: "MANAGE" },
            ],
          },
        ],
        page: 0,
        size: 20,
        totalItems: 1,
        totalPages: 1,
      })),
      listActiveRoles: vi.fn<UsersApiDeps["listActiveRoles"]>(async () => [
        {
          id: "r-sys",
          code: "SYSTEM_ADMIN",
          name: "Quản trị hệ thống",
          access: "MANAGE",
          status: "ACTIVE",
          system: true,
          permissionCount: 10,
          userCount: 1,
          version: 0,
          createdAt: "2026-11-01T00:00:00Z",
          updatedAt: "2026-11-01T00:00:00Z",
        },
        {
          id: "r-content",
          code: "CONTENT_ADMIN",
          name: "Quản trị nội dung",
          access: "MANAGE",
          status: "ACTIVE",
          system: true,
          permissionCount: 5,
          userCount: 1,
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
        {
          id: "r-student",
          code: "STUDENT",
          name: "Học viên",
          access: "EXAM",
          status: "ACTIVE",
          system: true,
          permissionCount: 0,
          userCount: 1,
          version: 0,
          createdAt: "2026-11-01T00:00:00Z",
          updatedAt: "2026-11-01T00:00:00Z",
        },
        {
          id: "r-custom",
          code: "PROCTOR",
          name: "Exam Proctor",
          access: "MANAGE",
          status: "ACTIVE",
          system: false,
          permissionCount: 2,
          userCount: 1,
          version: 0,
          createdAt: "2026-11-01T00:00:00Z",
          updatedAt: "2026-11-01T00:00:00Z",
        },
      ]),
    });

    renderWithProviders(
      <SessionProvider loadSession={async () => TEST_ADMIN_SESSION}>
        <UsersListView deps={deps} />
      </SessionProvider>,
      { locale: "en", messages: en },
    );

    expect(await screen.findByText("Operations / Support, Exam Proctor")).toBeInTheDocument();
    expect(screen.queryByText(/Vận hành \/ CSKH/)).not.toBeInTheDocument();
    const filterRoleTrigger = screen.getByRole("combobox", { name: "Role" });
    await userEvent.click(filterRoleTrigger);
    expect(await screen.findByRole("option", { name: "System administrator" })).toBeInTheDocument();
    expect(screen.getByRole("option", { name: "Content administrator" })).toBeInTheDocument();
    expect(screen.getByRole("option", { name: "Operations / Support" })).toBeInTheDocument();
    expect(screen.getByRole("option", { name: "Student" })).toBeInTheDocument();
    expect(screen.getByRole("option", { name: "Exam Proctor" })).toBeInTheDocument();
    await userEvent.keyboard("{Escape}");

    await userEvent.click(screen.getByRole("button", { name: /Create account/ }));
    const roleTrigger = within(screen.getByRole("dialog")).getByRole("combobox", { name: "Role" });
    await userEvent.click(roleTrigger);

    expect(await screen.findByRole("option", { name: "System administrator" })).toBeInTheDocument();
    expect(screen.getByRole("option", { name: "Content administrator" })).toBeInTheDocument();
    expect(screen.getByRole("option", { name: "Operations / Support" })).toBeInTheDocument();
    expect(screen.getByRole("option", { name: "Student" })).toBeInTheDocument();
    expect(screen.getByRole("option", { name: "Exam Proctor" })).toBeInTheDocument();
    expect(screen.queryByRole("option", { name: "Quản trị hệ thống" })).not.toBeInTheDocument();
  });

  it("bộ lọc Lớp học hoạt động với classId và CreateUserDialog ẩn/hiện classIds theo quyền CLASS.MODIFY", async () => {
    const listUsersMock = vi.fn<UsersApiDeps["listUsers"]>(async () => ({
      items: SAMPLE_USERS.slice(0, 1),
      page: 0,
      size: 20,
      totalItems: 1,
      totalPages: 1,
    }));
    const createMock = vi.fn<UsersApiDeps["createUser"]>(async (body) => ({
      id: "00000000-0000-4000-8000-000000000888",
      code: "U000888",
      username: body.username,
      fullName: body.fullName,
      source: "ADMIN_CREATED",
      status: "ACTIVE",
      mustChangePassword: body.mustChangePassword ?? false,
      roles: [{ id: "r-student", code: "STUDENT", name: "Học viên", access: "EXAM" }],
      version: 0,
      createdAt: "2026-11-14T00:00:00Z",
      updatedAt: "2026-11-14T00:00:00Z",
    }));

    // 1. Khi không có CLASS.MODIFY, CreateUserDialog không hiển thị chọn lớp học
    const { unmount } = renderUsersView(
      makeDeps({ listUsers: listUsersMock, createUser: createMock }),
      { ...TEST_ADMIN_SESSION, permissions: ["USER.VIEW", "USER.MODIFY", "CLASS.VIEW"] },
      true,
    );

    await screen.findByText("student01");
    const classTrigger = screen.getByRole("combobox", { name: "Lớp" });
    await userEvent.click(classTrigger);
    await userEvent.click(await screen.findByRole("option", { name: "LOP-A — Lớp A" }));

    await waitFor(() => {
      expect(listUsersMock).toHaveBeenLastCalledWith(
        expect.objectContaining({ classId: "00000000-0000-4000-8000-000000000301" }),
      );
    });

    await userEvent.click(screen.getByRole("button", { name: /Tạo tài khoản/ }));
    const dialogWithoutModify = screen.getByRole("dialog");
    expect(
      within(dialogWithoutModify).queryByRole("group", { name: "Lớp học" }),
    ).not.toBeInTheDocument();
    unmount();

    // 2. Khi có CLASS.MODIFY, chọn nhiều lớp và gửi classIds khi tạo tài khoản
    renderUsersView(
      makeDeps({ listUsers: listUsersMock, createUser: createMock }),
      TEST_ADMIN_SESSION,
      true,
    );

    await screen.findByText("student01");
    await userEvent.click(screen.getByRole("button", { name: /Tạo tài khoản/ }));
    const dialog = screen.getByRole("dialog");
    const classGroup = await within(dialog).findByRole("group", { name: "Lớp học" });
    await userEvent.click(within(classGroup).getByRole("checkbox", { name: /LOP-A/ }));
    await userEvent.click(within(classGroup).getByRole("checkbox", { name: /LOP-B/ }));

    await userEvent.type(within(dialog).getByLabelText(/Tên đăng nhập/), "student99");
    await userEvent.click(within(dialog).getByRole("button", { name: "Tạo ngẫu nhiên" }));
    await userEvent.type(within(dialog).getByLabelText(/Họ và tên/), "Học viên 99");
    await userEvent.click(within(dialog).getByRole("button", { name: "Tạo tài khoản" }));

    await waitFor(() => {
      expect(createMock).toHaveBeenCalledWith(
        expect.objectContaining({
          username: "student99",
          classIds: [
            "00000000-0000-4000-8000-000000000301",
            "00000000-0000-4000-8000-000000000302",
          ],
        }),
      );
    });
  });

  it("CreateUserDialog: lỗi tải danh sách lớp → báo lỗi + nút Thử lại, retry thành công hiển thị checkbox lớp", async () => {
    const user = userEvent.setup();
    const listAllClassesMock = vi
      .fn<UsersApiDeps["listAllClasses"]>()
      .mockRejectedValueOnce(new Error("network"))
      .mockResolvedValueOnce([
        {
          id: "00000000-0000-4000-8000-000000000301",
          code: "LOP-A",
          name: "Lớp A",
          status: "ACTIVE",
          memberCount: 2,
          version: 0,
          createdAt: "2026-11-10T08:00:00Z",
          updatedAt: "2026-11-10T08:00:00Z",
        },
      ]);

    renderUsersView(makeDeps({ listAllClasses: listAllClassesMock }), TEST_ADMIN_SESSION, false);

    await screen.findByText("student01");
    await user.click(screen.getByRole("button", { name: /Tạo tài khoản/ }));

    const dialog = screen.getByRole("dialog");
    // Lỗi tải lớp → hiện classesError + nút Thử lại
    await within(dialog).findByText("Không tải được danh sách lớp học. Hãy thử lại.");

    await user.click(within(dialog).getByRole("button", { name: "Thử lại" }));

    // Sau retry, nhóm lớp hiển thị checkbox LOP-A
    const classGroup = await within(dialog).findByRole("group", { name: "Lớp học" });
    expect(within(classGroup).getByRole("checkbox", { name: /LOP-A/ })).toBeInTheDocument();
    expect(listAllClassesMock).toHaveBeenCalledTimes(2);
  });

  it("định dạng số >= 1000 qua next-intl formatter theo locale vi và en", async () => {
    const deps = makeDeps({
      listUsers: vi.fn<UsersApiDeps["listUsers"]>(async () => ({
        items: SAMPLE_USERS.slice(0, 1),
        page: 0,
        size: 20,
        totalItems: 1250,
        totalPages: 63,
      })),
    });

    const { unmount } = renderWithProviders(
      <SessionProvider loadSession={async () => TEST_ADMIN_SESSION}>
        <UsersListView deps={deps} />
      </SessionProvider>,
      { locale: "vi" },
    );

    expect(await screen.findByText("1.250 tài khoản khớp bộ lọc")).toBeInTheDocument();
    expect(screen.getByText("Hiển thị 1–20 trên 1.250")).toBeInTheDocument();
    unmount();

    renderWithProviders(
      <SessionProvider loadSession={async () => TEST_ADMIN_SESSION}>
        <UsersListView deps={deps} />
      </SessionProvider>,
      { locale: "en", messages: en },
    );

    expect(await screen.findByText("1,250 matching accounts")).toBeInTheDocument();
    expect(screen.getByText("Showing 1–20 of 1,250")).toBeInTheDocument();
  });
});
