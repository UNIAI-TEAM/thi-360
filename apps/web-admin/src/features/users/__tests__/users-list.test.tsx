import { screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, describe, expect, it, vi } from "vitest";
import { SessionProvider } from "@/features/session/session-provider";
import type { Session } from "@/features/session/types";
import { TEST_ADMIN_SESSION } from "@/test/fixtures";
import en from "@/messages/en.json";
import { renderWithProviders } from "@/test/render";
import { installContainerWidth, PHONE_CONTENT_WIDTH } from "@/test/container-width";
import { onlineManager, QueryClient } from "@tanstack/react-query";
import { SESSION_QUERY_KEY } from "@/features/session/api";
import { notify } from "@thi360/ui";
import { defaultUsersApiDeps, type UsersApiDeps, UsersApiError, type UserSummary } from "../api";
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
    classes: [
      { id: "00000000-0000-4000-8000-000000000301", code: "LOP-A", name: "Lớp A" },
      { id: "00000000-0000-4000-8000-000000000303", code: "LOP-C", name: "Lớp C" },
    ],
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
    classes: [],
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
    classes: [],
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
    classes: [],
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
      classes: [],
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
    expect(screen.queryByRole("link", { name: /Cấp hàng loạt/ })).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: /Tạo tài khoản/ })).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: /Thao tác với/ })).not.toBeInTheDocument();
    expect(screen.queryByRole("checkbox", { name: "Chọn tất cả tài khoản" })).not.toBeInTheDocument();
    expect(screen.queryByLabelText("Lớp")).not.toBeInTheDocument();
  });

  it("hiện nút Cấp hàng loạt dẫn tới /users/bulk-import khi có quyền USER.MODIFY", async () => {
    const deps = makeDeps();
    renderUsersView(deps, { ...TEST_ADMIN_SESSION, permissions: ["USER.VIEW", "USER.MODIFY"] });

    expect(await screen.findByText("student01", undefined, { timeout: 3000 })).toBeInTheDocument();
    const bulkLink = screen.getByRole("link", { name: "Cấp hàng loạt" });
    expect(bulkLink).toHaveAttribute("href", "/users/bulk-import");
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

  it("đổi bộ lọc khi đã có dữ liệu: các dòng cũ ở lại cho tới khi có dữ liệu mới, không hiện khung chờ (UNI-2577)", async () => {
    const first = SAMPLE_USERS[0]!;
    let releaseFiltered: () => void = () => {};
    const listUsersMock = vi.fn<UsersApiDeps["listUsers"]>(async (query) => {
      if (query.neverLoggedIn) {
        await new Promise<void>((resolve) => {
          releaseFiltered = resolve;
        });
        return { items: [first], page: 0, size: 20, totalItems: 1, totalPages: 1 };
      }
      return { items: SAMPLE_USERS, page: 0, size: 20, totalItems: SAMPLE_USERS.length, totalPages: 1 };
    });
    renderUsersView(makeDeps({ listUsers: listUsersMock }));

    const table = await screen.findByRole("table");
    await waitFor(() => expect(within(table).getAllByRole("row")).toHaveLength(1 + SAMPLE_USERS.length));

    await userEvent.click(screen.getByLabelText("Chưa đăng nhập"));
    await waitFor(() =>
      expect(listUsersMock).toHaveBeenLastCalledWith(expect.objectContaining({ neverLoggedIn: true })),
    );

    // Dữ liệu mới chưa về: bảng vẫn đủ các dòng cũ và báo đang bận, không thay bằng khung chờ.
    expect(within(table).getAllByRole("row")).toHaveLength(1 + SAMPLE_USERS.length);
    expect(table).toHaveAttribute("aria-busy", "true");
    expect(screen.queryByText("Đang tải danh sách tài khoản...")).not.toBeInTheDocument();

    releaseFiltered();
    await waitFor(() => expect(within(table).getAllByRole("row")).toHaveLength(1 + 1));
    expect(table).not.toHaveAttribute("aria-busy");
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

    const toolbar = await screen.findByRole("group", {
      name: "Thao tác với các tài khoản đã chọn",
    });
    expect(toolbar).toHaveTextContent("Đã chọn 2 tài khoản");
    expect(screen.queryByRole("button", { name: "Cấp hàng loạt" })).not.toBeInTheDocument();
    // Ghi chú dài nằm sau nút mở/đóng, không chiếm chỗ khi chưa mở.
    expect(screen.queryByText(/mở tab Gán hàng loạt của Phân quyền/)).not.toBeInTheDocument();
    const helpToggle = within(toolbar).getByRole("button", { name: "Cách hoạt động" });
    expect(helpToggle).toHaveAttribute("aria-expanded", "false");
    await userEvent.click(helpToggle);
    expect(helpToggle).toHaveAttribute("aria-expanded", "true");
    const note = screen.getByText(/mở tab Gán hàng loạt của Phân quyền với 2 tài khoản này đã chọn sẵn/);
    expect(note).toHaveAttribute("id", helpToggle.getAttribute("aria-controls"));
    await userEvent.keyboard("{Escape}");
    expect(screen.queryByText(/mở tab Gán hàng loạt của Phân quyền/)).not.toBeInTheDocument();

    await userEvent.click(screen.getByRole("button", { name: "Gán vai trò, quyền" }));
    expect(window.sessionStorage.getItem("rbac.bulk.userIds")).toBe(
      JSON.stringify([SAMPLE_USERS[0]!.id, SAMPLE_USERS[3]!.id]),
    );
    expect(routerMock.push).toHaveBeenCalledWith("/rbac/bulk-assign");

    await userEvent.click(screen.getByRole("button", { name: "Ngừng hoạt động" }));
    expect(previewMock).toHaveBeenCalledWith({
      action: "DEACTIVATE",
      userIds: [SAMPLE_USERS[0]!.id, SAMPLE_USERS[3]!.id],
    });

    expect(await screen.findByText("1 / 2 tài khoản đủ điều kiện thực hiện thao tác.")).toBeInTheDocument();
    expect(screen.getByText(/Tài khoản không tồn tại hoặc đã bị xóa/)).toBeInTheDocument();
    // Hộp thoại nêu đích danh tài khoản sẽ bị tác động (student04 bị bỏ qua nên không nằm trong câu này).
    expect(screen.getByText("Áp dụng cho: student01.")).toBeInTheDocument();

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

describe("UsersListView: bảng vừa khung và chữ dài (U-02, U-12)", () => {
  it("họ tên, email, vai trò xuống tối đa hai dòng, không cắt một dòng và không dựa vào title", async () => {
    const longName = "Nguyễn Hoàng Phương Thảo Nguyên Khánh Linh Bảo Trâm";
    const longEmail = "nguyen.hoang.phuong.thao.nguyen@truong-thpt-chuyen.example.edu.vn";
    const deps = makeDeps({
      listUsers: vi.fn<UsersApiDeps["listUsers"]>(async () => ({
        items: [{ ...SAMPLE_USERS[0]!, fullName: longName, email: longEmail }],
        page: 0,
        size: 20,
        totalItems: 1,
        totalPages: 1,
      })),
    });
    renderUsersView(deps);

    const nameCell = await screen.findByText(longName);
    const emailCell = screen.getByText(longEmail);
    const roleCell = screen.getByText("Học viên", { selector: "[data-col='roles']" });
    for (const cell of [nameCell, emailCell, roleCell]) {
      expect(cell).toHaveClass("line-clamp-2");
      expect(cell).not.toHaveClass("truncate");
      expect(cell).not.toHaveAttribute("title");
      expect(cell.className).not.toMatch(/\bmax-w-/);
    }
  });

  it("cột Lớp ghi mã các lớp hiện tại theo thứ tự trả về, tài khoản không có lớp hiện dấu gạch (UNI-2598)", async () => {
    renderUsersView(makeDeps());

    const table = await screen.findByRole("table", { name: "Danh sách tài khoản" });
    await within(table).findByText("student01");

    const header = within(table).getByRole("columnheader", { name: "Lớp" });
    expect(header.querySelector("[data-col='classes']")).not.toBeNull();
    const cells = [...table.querySelectorAll("tbody [data-col='classes']")].map((cell) => cell.textContent);
    expect(cells).toEqual(["LOP-A, LOP-C", "—", "—", "—"]);
    // Cột Lớp đứng giữa Vai trò và Nguồn như bản vẽ.
    const headers = within(table).getAllByRole("columnheader").map((th) => th.textContent?.trim());
    expect(headers.indexOf("Lớp")).toBe(headers.indexOf("Vai trò") + 1);
    expect(headers.indexOf("Nguồn")).toBe(headers.indexOf("Lớp") + 1);
    // Ẩn sau cột Nguồn, trước cột Vai trò khi khung hẹp lại.
    const container = table.closest(".\\@container");
    expect(container!.className).toContain("@max-[63rem]:[&_:is(th,td):has([data-col=classes])]:hidden");
  });

  it("cột Nguồn và Vai trò được đánh dấu để ẩn theo bề rộng khung chứa bảng; cột đầu và cột Thao tác được ghim", async () => {
    renderUsersView(makeDeps());

    const table = await screen.findByRole("table", { name: "Danh sách tài khoản" });
    await within(table).findByText("student01");

    const sourceHeader = within(table).getByRole("columnheader", { name: "Nguồn" });
    const rolesHeader = within(table).getByRole("columnheader", { name: "Vai trò" });
    expect(sourceHeader.querySelector("[data-col='source']")).not.toBeNull();
    expect(rolesHeader.querySelector("[data-col='roles']")).not.toBeNull();
    // Mỗi dòng dữ liệu có đúng một ô mang dấu của từng cột.
    expect(table.querySelectorAll("tbody [data-col='source']")).toHaveLength(SAMPLE_USERS.length);
    expect(table.querySelectorAll("tbody [data-col='roles']")).toHaveLength(SAMPLE_USERS.length);

    // Khung chứa là query container và mang quy tắc ẩn cột Nguồn trước (ngưỡng rộng hơn), rồi Vai trò.
    const container = table.closest(".\\@container");
    expect(container).not.toBeNull();
    expect(container!.className).toContain("@max-[68rem]:[&_:is(th,td):has([data-col=source])]:hidden");
    expect(container!.className).toContain("@max-[58rem]:[&_:is(th,td):has([data-col=roles])]:hidden");
  });
});

describe("UsersListView: dùng được từ 360px (UNI-2586)", () => {
  it("dải số kết quả và thanh thao tác hàng loạt nằm trong cột lưới co theo thẻ, câu gợi ý không bị cắt", async () => {
    renderUsersView(makeDeps());
    const hint = await screen.findByText(/^Chọn nhiều dòng để ngừng hoạt động/);
    const layer = hint.parentElement!;
    const slot = layer.parentElement!;
    // grid-cols-1 = repeat(1, minmax(0, 1fr)): hàng nút không xuống dòng của thanh thao tác không kéo cột rộng hơn thẻ.
    expect(slot).toHaveClass("grid", "grid-cols-1");
    expect(layer).toHaveClass("min-w-0");
  });

  it("khung vừa (40–48rem): Email được đánh dấu để ẩn, Họ và tên và Trạng thái vẫn là cột; không còn dòng tóm tắt", async () => {
    renderUsersView(makeDeps());
    const table = await screen.findByRole("table", { name: "Danh sách tài khoản" });
    await within(table).findByText("student01");

    const container = table.closest(".\\@container")!;
    expect(container.className).toContain("@max-3xl:[&_:is(th,td):has([data-col=email])]:hidden");
    expect(container.className).not.toContain("@max-[40rem]");
    expect(table.querySelectorAll("tbody [data-col='email']")).toHaveLength(SAMPLE_USERS.length);
    expect(within(table).getByRole("columnheader", { name: "Họ và tên" })).toBeInTheDocument();
    expect(within(table).getByRole("columnheader", { name: "Trạng thái" })).toBeInTheDocument();
    expect(table.querySelector("[data-col-summary]")).toBeNull();
    // Liên kết tên đăng nhập là liên kết thường của ô, có vòng focus của kit.
    const link = within(table).getByRole("link", { name: "student01" });
    expect(link).toHaveAttribute("href", `/users/${SAMPLE_USERS[0]!.id}`);
    expect(link).toHaveClass("focus-visible:ring-3");
    expect(link).not.toHaveClass("after:absolute");
  });

  it("thanh lọc chia theo bề rộng thẻ lọc: tìm kiếm cả hàng, ô chọn chia lưới, ô đánh dấu chung một hàng", async () => {
    renderUsersView(makeDeps());
    await screen.findByText("student01");

    const form = screen.getByRole("form", { name: "Bộ lọc tài khoản" });
    expect(form.parentElement).toHaveClass("@container");
    expect(form).toHaveClass("grid", "grid-cols-[repeat(auto-fill,minmax(8.5rem,1fr))]", "@3xl:flex");

    const searchField = screen.getByLabelText("Tìm kiếm").closest(".col-span-full");
    expect(searchField).not.toBeNull();
    const toggles = screen.getByLabelText("Chưa đăng nhập").closest(".col-span-full")!;
    expect(toggles).toHaveClass("flex", "flex-wrap", "@3xl:contents");
    expect(toggles).toContainElement(screen.getByLabelText("Hiện tài khoản đã xóa"));
  });

  it("thanh thao tác hàng loạt ở khung hẹp: nút thao tác một hàng cuộn ngang, nút trợ giúp chỉ còn icon", async () => {
    renderUsersView(makeDeps());
    await screen.findByText("student01");
    await userEvent.click(screen.getByRole("checkbox", { name: "Chọn tài khoản student01" }));

    const bar = screen.getByRole("group", { name: "Thao tác với các tài khoản đã chọn" });
    expect(bar.parentElement).toHaveClass("@container");
    const actions = within(bar).getByRole("button", { name: "Ngừng hoạt động" }).parentElement!;
    expect(actions).toHaveClass("@max-[40rem]:flex-nowrap", "@max-[40rem]:overflow-x-auto");
    const help = within(bar).getByRole("button", { name: "Cách hoạt động" });
    expect(within(help).getByText("Cách hoạt động")).toHaveClass("@max-md:sr-only");
  });
});

describe("UsersListView: kết quả không đồng bộ được đọc lên qua một vùng role=status (U-04)", () => {
  it("không đọc số kết quả ở lần tải đầu; đọc số kết quả mới sau khi đổi bộ lọc", async () => {
    const first = SAMPLE_USERS[0]!;
    const listUsersMock = vi.fn<UsersApiDeps["listUsers"]>(async (query) =>
      query.neverLoggedIn
        ? { items: [first], page: 0, size: 20, totalItems: 1, totalPages: 1 }
        : { items: SAMPLE_USERS, page: 0, size: 20, totalItems: SAMPLE_USERS.length, totalPages: 1 },
    );
    renderUsersView(makeDeps({ listUsers: listUsersMock }));

    await screen.findByText("student01");
    // Vùng thông báo có sẵn từ đầu và đang rỗng: lần tải đầu không đọc gì.
    const status = screen.getByRole("status");
    expect(status).toHaveAttribute("aria-live", "polite");
    expect(status).toBeEmptyDOMElement();

    await userEvent.click(screen.getByLabelText("Chưa đăng nhập"));
    await waitFor(() => expect(status).toHaveTextContent("1 tài khoản khớp bộ lọc"));
    // Vẫn chỉ một vùng thông báo cho cả danh sách.
    expect(screen.getAllByRole("status")).toHaveLength(1);
  });

  it("đọc cột và chiều sắp xếp sau mỗi lần đổi sắp xếp", async () => {
    renderUsersView(makeDeps());
    await screen.findByText("student01");
    const status = screen.getByRole("status");

    const sortButton = screen.getByRole("button", { name: "Họ và tên" });
    await userEvent.click(sortButton);
    expect(status).toHaveTextContent("Đã sắp xếp theo Họ và tên, tăng dần.");
    await userEvent.click(sortButton);
    expect(status).toHaveTextContent("Đã sắp xếp theo Họ và tên, giảm dần.");
    await userEvent.click(sortButton);
    expect(status).toHaveTextContent("Đã bỏ sắp xếp, danh sách về thứ tự mặc định.");
  });

  it("đọc số tài khoản đang chọn và đọc khi bỏ chọn hết", async () => {
    renderUsersView(makeDeps());
    await screen.findByText("student01");
    const status = screen.getByRole("status");

    await userEvent.click(screen.getByRole("checkbox", { name: "Chọn tài khoản student01" }));
    expect(status).toHaveTextContent("Đã chọn 1 tài khoản");
    await userEvent.click(screen.getByRole("checkbox", { name: "Chọn tài khoản student02" }));
    expect(status).toHaveTextContent("Đã chọn 2 tài khoản");

    await userEvent.click(screen.getByRole("button", { name: "Bỏ chọn" }));
    expect(status).toHaveTextContent("Đã bỏ chọn tất cả tài khoản.");
  });

  it("hộp thoại tạo tài khoản đọc kết quả tên đăng nhập dùng được và gắn dòng chữ vào ô nhập", async () => {
    renderUsersView(makeDeps());
    await userEvent.click(await screen.findByRole("button", { name: /Tạo tài khoản/ }));
    const dialog = screen.getByRole("dialog");

    // Dialog có hai vùng status: kết quả kiểm tra tên đăng nhập và thông báo của nút Tạo ngẫu nhiên trong ô mật khẩu.
    const regions = within(dialog).getAllByRole("status");
    for (const region of regions) expect(region).toBeEmptyDOMElement();

    const usernameInput = within(dialog).getByLabelText(/Tên đăng nhập/);
    await userEvent.type(usernameInput, "Student06");
    await userEvent.tab();

    const status = await waitFor(() => {
      const found = regions.find((region) => region.textContent === "Tên đăng nhập student06 có thể dùng.");
      expect(found).toBeDefined();
      return found as HTMLElement;
    });
    expect(usernameInput).toHaveAccessibleDescription(/Có thể dùng\. 4–30 ký tự/);

    // Gõ lại thì kết quả cũ không còn đúng: vùng thông báo về rỗng.
    await userEvent.type(usernameInput, "x");
    expect(status).toBeEmptyDOMElement();
  });
});

describe("UsersListView: thanh thao tác hàng loạt có chỗ giữ sẵn (U-05, U-14)", () => {
  it("tích dòng chỉ đổi lớp đang hiện trong cùng một ô lưới, không chèn thêm khối phía trên bảng", async () => {
    renderUsersView(makeDeps());
    await screen.findByText("student01");

    const countStrip = screen.getByText("4 tài khoản khớp bộ lọc").parentElement as HTMLElement;
    const slot = countStrip.parentElement as HTMLElement;
    const table = screen.getByRole("table", { name: "Danh sách tài khoản" });
    const childrenBefore = slot.parentElement!.children.length;

    // Chưa chọn: thanh thao tác đã nằm sẵn trong ô nhưng ẩn và không nhận thao tác.
    expect(screen.queryByRole("group", { name: "Thao tác với các tài khoản đã chọn" })).not.toBeInTheDocument();
    const bar = slot.querySelector<HTMLElement>("[role='group']")!;
    expect(bar).toHaveClass("invisible", "col-start-1", "row-start-1");
    expect(bar).toHaveAttribute("inert");
    expect(countStrip).toHaveClass("col-start-1", "row-start-1");
    expect(countStrip).not.toHaveClass("invisible");

    await userEvent.click(screen.getByRole("checkbox", { name: "Chọn tài khoản student01" }));

    // Đã chọn: hai lớp đổi vai, vẫn cùng ô lưới; không có phần tử nào được chèn vào giữa ô và bảng.
    expect(screen.getByRole("group", { name: "Thao tác với các tài khoản đã chọn" })).toBe(bar);
    expect(bar).not.toHaveClass("invisible");
    expect(bar).not.toHaveAttribute("inert");
    expect(countStrip).toHaveClass("invisible");
    expect(countStrip).toHaveAttribute("inert");
    expect(countStrip).toHaveAttribute("aria-hidden", "true");
    expect(slot.parentElement!.children.length).toBe(childrenBefore);
    expect(slot.compareDocumentPosition(table) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy();

    // Không còn role="toolbar" (không có mô hình phím mũi tên).
    expect(screen.queryByRole("toolbar")).not.toBeInTheDocument();
  });

  it("hộp thoại xác nhận nêu vài tài khoản đầu và gộp phần còn lại thành số đếm", async () => {
    const manyUsers: UserSummary[] = Array.from({ length: 7 }, (_, index) => ({
      ...SAMPLE_USERS[0]!,
      id: `00000000-0000-4000-8000-0000000002${String(index).padStart(2, "0")}`,
      username: `bulk${index + 1}`,
    }));
    const previewMock = vi.fn<UsersApiDeps["previewBulkUserActions"]>(async () => ({
      total: 7,
      affected: 7,
      skipped: [],
    }));
    renderUsersView(
      makeDeps({
        listUsers: vi.fn<UsersApiDeps["listUsers"]>(async () => ({
          items: manyUsers,
          page: 0,
          size: 20,
          totalItems: 7,
          totalPages: 1,
        })),
        previewBulkUserActions: previewMock,
      }),
    );

    await screen.findByText("bulk1");
    await userEvent.click(screen.getByRole("checkbox", { name: "Chọn tất cả tài khoản" }));
    await userEvent.click(screen.getByRole("button", { name: "Ngừng hoạt động" }));

    const dialog = await screen.findByRole("dialog");
    expect(
      within(dialog).getByText("Áp dụng cho: bulk1, bulk2, bulk3, bulk4, bulk5 và 2 tài khoản khác."),
    ).toBeInTheDocument();
  });
});

describe("UsersListView: áp dụng hàng loạt thất bại báo lỗi trong hộp thoại (U-07)", () => {
  it("lỗi hiện trong hộp thoại xác nhận, không dùng toast; đóng rồi mở lại không còn lỗi cũ", async () => {
    const previewMock = vi.fn<UsersApiDeps["previewBulkUserActions"]>(async () => ({
      total: 1,
      affected: 1,
      skipped: [],
    }));
    const applyMock = vi
      .fn<UsersApiDeps["applyBulkUserActions"]>()
      .mockRejectedValueOnce(new UsersApiError(429, "raw backend text", "RATE_LIMITED"));
    const toastError = vi.spyOn(notify, "error");
    renderUsersView(
      makeDeps({ previewBulkUserActions: previewMock, applyBulkUserActions: applyMock }),
    );

    await screen.findByText("student01");
    await userEvent.click(screen.getByRole("checkbox", { name: "Chọn tài khoản student01" }));
    await userEvent.click(screen.getByRole("button", { name: "Kích hoạt lại" }));

    let dialog = await screen.findByRole("dialog");
    await userEvent.click(within(dialog).getByRole("button", { name: "Kích hoạt 1 tài khoản" }));

    expect(await within(dialog).findByRole("alert")).toHaveTextContent(
      "Bạn thao tác quá nhanh. Vui lòng chờ một lát rồi thử lại.",
    );
    expect(screen.queryByText(/raw backend text/)).not.toBeInTheDocument();
    expect(toastError).not.toHaveBeenCalled();

    await userEvent.click(within(dialog).getByRole("button", { name: "Hủy" }));
    await waitFor(() => expect(screen.queryByRole("dialog")).not.toBeInTheDocument());
    await userEvent.click(screen.getByRole("button", { name: "Kích hoạt lại" }));
    dialog = await screen.findByRole("dialog");
    expect(within(dialog).queryByRole("alert")).not.toBeInTheDocument();
  });
});

describe("CreateUserDialog: gửi biểu mẫu sai thì focus về ô sai đầu tiên (U-08)", () => {
  it("mật khẩu là ô sai duy nhất: focus chuyển vào ô mật khẩu, không ở lại nút gửi", async () => {
    const createMock = vi.fn<UsersApiDeps["createUser"]>();
    renderUsersView(makeDeps({ createUser: createMock }));
    await userEvent.click(await screen.findByRole("button", { name: /Tạo tài khoản/ }));
    const dialog = screen.getByRole("dialog");

    await userEvent.type(within(dialog).getByLabelText(/Tên đăng nhập/), "student77");
    await userEvent.type(within(dialog).getByLabelText(/Họ và tên/), "Học viên 77");
    await userEvent.click(within(dialog).getByRole("button", { name: "Tạo tài khoản" }));

    const passwordInput = dialog.querySelector<HTMLInputElement>("input[autocomplete='new-password']")!;
    await waitFor(() => expect(passwordInput).toHaveFocus());
    expect(passwordInput).toHaveAttribute("aria-invalid", "true");
    expect(createMock).not.toHaveBeenCalled();
  });
});

describe("UsersListView: focus không rơi về <body> khi nút vừa bấm biến mất (U-10)", () => {
  it("Xóa bộ lọc bằng bàn phím: focus về ô tìm kiếm", async () => {
    renderUsersView(makeDeps());
    await screen.findByText("student01");

    await userEvent.click(screen.getByLabelText("Chưa đăng nhập"));
    const clearButton = await screen.findByRole("button", { name: "Xóa bộ lọc" });
    clearButton.focus();
    await userEvent.keyboard("{Enter}");

    await waitFor(() => expect(screen.queryByRole("button", { name: "Xóa bộ lọc" })).not.toBeInTheDocument());
    expect(screen.getByLabelText("Tìm kiếm")).toHaveFocus();
  });

  it("Bỏ chọn: focus về ô chọn tất cả của bảng", async () => {
    renderUsersView(makeDeps());
    await screen.findByText("student01");

    await userEvent.click(screen.getByRole("checkbox", { name: "Chọn tài khoản student01" }));
    const clearSelection = screen.getByRole("button", { name: "Bỏ chọn" });
    clearSelection.focus();
    await userEvent.keyboard("{Enter}");

    expect(screen.getByRole("checkbox", { name: "Chọn tất cả tài khoản" })).toHaveFocus();
    expect(document.body).not.toHaveFocus();
  });
});

describe("CreateUserDialog: không làm mất dữ liệu đã nhập khi đóng nhầm (U-11)", () => {
  async function openCreateDialog() {
    renderUsersView(makeDeps());
    await userEvent.click(await screen.findByRole("button", { name: /Tạo tài khoản/ }));
    return screen.getByRole("dialog", { name: "Tạo tài khoản" });
  }

  it("biểu mẫu chưa nhập gì: Esc đóng ngay, không hỏi", async () => {
    await openCreateDialog();
    await userEvent.keyboard("{Escape}");
    await waitFor(() => expect(screen.queryByRole("dialog")).not.toBeInTheDocument());
  });

  it("biểu mẫu chưa nhập gì: bấm ra ngoài vẫn đóng như trước", async () => {
    await openCreateDialog();
    // Radix gắn bộ nghe pointerdown ở document sau một nhịp; chờ nhịp đó rồi mới bấm.
    await new Promise((resolve) => setTimeout(resolve, 10));
    await userEvent.click(document.querySelector<HTMLElement>("[data-state='open'].fixed.inset-0")!);
    await waitFor(() => expect(screen.queryByRole("dialog")).not.toBeInTheDocument());
  });

  it("biểu mẫu đã nhập: bấm ra ngoài không đóng và không hỏi", async () => {
    const dialog = await openCreateDialog();
    await userEvent.type(within(dialog).getByLabelText(/Họ và tên/), "Học viên 88");

    const overlay = document.querySelector<HTMLElement>("[data-state='open'].fixed.inset-0")!;
    expect(overlay).not.toBeNull();
    await userEvent.click(overlay);

    expect(screen.getByRole("dialog", { name: "Tạo tài khoản" })).toBeInTheDocument();
    expect(screen.queryByRole("dialog", { name: "Bỏ thông tin đã nhập?" })).not.toBeInTheDocument();
    expect(within(dialog).getByLabelText(/Họ và tên/)).toHaveValue("Học viên 88");
  });

  it("biểu mẫu đã nhập: Esc hỏi lại; Tiếp tục nhập giữ nguyên dữ liệu; Hủy rồi Bỏ thay đổi mới đóng", async () => {
    const dialog = await openCreateDialog();
    await userEvent.type(within(dialog).getByLabelText(/Họ và tên/), "Học viên 88");

    await userEvent.keyboard("{Escape}");
    let discard = await screen.findByRole("dialog", { name: "Bỏ thông tin đã nhập?" });
    await userEvent.click(within(discard).getByRole("button", { name: "Tiếp tục nhập" }));
    await waitFor(() =>
      expect(screen.queryByRole("dialog", { name: "Bỏ thông tin đã nhập?" })).not.toBeInTheDocument(),
    );
    const stillOpen = screen.getByRole("dialog", { name: "Tạo tài khoản" });
    expect(within(stillOpen).getByLabelText(/Họ và tên/)).toHaveValue("Học viên 88");

    await userEvent.click(within(stillOpen).getByRole("button", { name: "Hủy" }));
    discard = await screen.findByRole("dialog", { name: "Bỏ thông tin đã nhập?" });
    await userEvent.click(within(discard).getByRole("button", { name: "Bỏ thay đổi" }));
    await waitFor(() => expect(screen.queryByRole("dialog")).not.toBeInTheDocument());

    // Mở lại là biểu mẫu trống, không còn hỏi.
    await userEvent.click(screen.getByRole("button", { name: /Tạo tài khoản/ }));
    const reopened = screen.getByRole("dialog", { name: "Tạo tài khoản" });
    expect(within(reopened).getByLabelText(/Họ và tên/)).toHaveValue("");
    await userEvent.keyboard("{Escape}");
    await waitFor(() => expect(screen.queryByRole("dialog")).not.toBeInTheDocument());
  });

  it("nút × cũng hỏi lại khi biểu mẫu đã nhập", async () => {
    const dialog = await openCreateDialog();
    await userEvent.type(within(dialog).getByLabelText(/Họ và tên/), "Học viên 88");
    await userEvent.click(within(dialog).getByRole("button", { name: "Đóng" }));
    expect(await screen.findByRole("dialog", { name: "Bỏ thông tin đã nhập?" })).toBeInTheDocument();
    expect(screen.getByText("Thông tin bạn vừa nhập trong biểu mẫu này sẽ không được lưu.")).toBeInTheDocument();
  });
});

describe("CreateUserDialog: kiểm tra khi rời ô và gợi ý cho ô nhập (U-16)", () => {
  it("email sai báo lỗi ngay khi rời ô, sửa đúng thì lỗi tự mất, không cần bấm gửi", async () => {
    renderUsersView(makeDeps());
    await userEvent.click(await screen.findByRole("button", { name: /Tạo tài khoản/ }));
    const dialog = screen.getByRole("dialog");

    const email = within(dialog).getByLabelText(/Email/);
    await userEvent.type(email, "khong-phai-email");
    await userEvent.tab();
    await waitFor(() => expect(email).toHaveAttribute("aria-invalid", "true"));
    expect(within(dialog).getByText("Email không đúng định dạng.")).toBeInTheDocument();

    await userEvent.clear(email);
    await userEvent.type(email, "hv@example.com");
    await waitFor(() => expect(email).not.toHaveAttribute("aria-invalid"));
  });

  it("tên đăng nhập sai định dạng báo lỗi khi rời ô và không hỏi máy chủ; ô để trống rời đi không báo lỗi", async () => {
    const checkMock = vi.fn<UsersApiDeps["checkUsernameAvailability"]>(async () => ({ available: true }));
    renderUsersView(makeDeps({ checkUsernameAvailability: checkMock }));
    await userEvent.click(await screen.findByRole("button", { name: /Tạo tài khoản/ }));
    const dialog = screen.getByRole("dialog");
    const username = within(dialog).getByLabelText(/Tên đăng nhập/);

    username.focus();
    await userEvent.tab();
    expect(username).not.toHaveAttribute("aria-invalid");

    await userEvent.type(username, "ab");
    await userEvent.tab();
    await waitFor(() => expect(username).toHaveAttribute("aria-invalid", "true"));
    expect(checkMock).not.toHaveBeenCalled();
  });

  it("ô nhập có maxLength theo schema và gợi ý cho trình duyệt", async () => {
    renderUsersView(makeDeps());
    await userEvent.click(await screen.findByRole("button", { name: /Tạo tài khoản/ }));
    const dialog = screen.getByRole("dialog");

    const username = within(dialog).getByLabelText(/Tên đăng nhập/);
    expect(username).toHaveAttribute("maxlength", "30");
    expect(username).toHaveAttribute("autocapitalize", "none");
    expect(username).toHaveAttribute("spellcheck", "false");

    const fullName = within(dialog).getByLabelText(/Họ và tên/);
    expect(fullName).toHaveAttribute("maxlength", "200");
    expect(fullName).toHaveAttribute("autocomplete", "off");

    const email = within(dialog).getByLabelText(/Email/);
    expect(email).toHaveAttribute("maxlength", "255");
    expect(email).toHaveAttribute("autocomplete", "off");
    expect(email).toHaveAttribute("spellcheck", "false");

    const phone = within(dialog).getByLabelText(/Số điện thoại/);
    expect(phone).toHaveAttribute("maxlength", "30");
    expect(phone).toHaveAttribute("autocomplete", "off");
  });
});

describe("UsersListView: mất mạng khi đổi bộ lọc (U-23)", () => {
  afterEach(() => {
    onlineManager.setOnline(true);
  });

  it("không giữ mãi các dòng của bộ lọc cũ: báo mất kết nối, bỏ số đếm cũ; có mạng lại thì tự tải", async () => {
    const first = SAMPLE_USERS[0]!;
    const listUsersMock = vi.fn<UsersApiDeps["listUsers"]>(async (query) =>
      query.neverLoggedIn
        ? { items: [first], page: 0, size: 20, totalItems: 1, totalPages: 1 }
        : { items: SAMPLE_USERS, page: 0, size: 20, totalItems: SAMPLE_USERS.length, totalPages: 1 },
    );
    renderUsersView(makeDeps({ listUsers: listUsersMock }));
    await screen.findByText("student01");
    expect(screen.getByText("4 tài khoản khớp bộ lọc")).toBeInTheDocument();

    onlineManager.setOnline(false);
    await userEvent.click(screen.getByLabelText("Chưa đăng nhập"));

    const table = screen.getByRole("table", { name: "Danh sách tài khoản" });
    expect(await within(table).findByText("Mất kết nối mạng")).toBeInTheDocument();
    expect(within(table).queryByText("student02")).not.toBeInTheDocument();
    expect(table).not.toHaveAttribute("aria-busy");
    expect(screen.queryByText("4 tài khoản khớp bộ lọc")).not.toBeInTheDocument();
    expect(within(table).getByRole("button", { name: "Thử lại" })).toBeInTheDocument();

    onlineManager.setOnline(true);
    // Số đếm hiện ở dải trên bảng (và được đọc lên qua vùng thông báo, nên lọc theo thẻ <p>).
    await waitFor(() =>
      expect(screen.getByText("1 tài khoản khớp bộ lọc", { selector: "p" })).toBeInTheDocument(),
    );
    expect(within(table).queryByText("Mất kết nối mạng")).not.toBeInTheDocument();
  });

  it("vào trang khi mất mạng (phiên đã có sẵn): báo mất kết nối, không hiện trạng thái Chưa có tài khoản", async () => {
    // Phiên cũng là một truy vấn và cũng bị tạm dừng khi mất mạng; trường hợp thật là điều hướng trong ứng dụng
    // tới /users khi phiên đã nằm sẵn trong bộ nhớ đệm.
    const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
    client.setQueryData(SESSION_QUERY_KEY, TEST_ADMIN_SESSION);
    onlineManager.setOnline(false);
    renderWithProviders(
      <SessionProvider loadSession={async () => TEST_ADMIN_SESSION}>
        <UsersListView deps={makeDeps()} showClassFilter={false} />
      </SessionProvider>,
      { client },
    );
    const table = await screen.findByRole("table", { name: "Danh sách tài khoản" });
    expect(await within(table).findByText("Mất kết nối mạng")).toBeInTheDocument();
    expect(screen.queryByText("Chưa có tài khoản")).not.toBeInTheDocument();
  });
});

describe("CreateUserDialog: nhóm Lớp học gắn với nhãn, gợi ý và lỗi (U-24)", () => {
  it("nhãn trỏ tới nhóm có thật; nhóm được mô tả bằng gợi ý và lỗi CLASS_INACTIVE", async () => {
    const createMock = vi.fn<UsersApiDeps["createUser"]>(async () => {
      throw new UsersApiError(400, "raw", "CLASS_INACTIVE");
    });
    renderUsersView(makeDeps({ createUser: createMock }), TEST_ADMIN_SESSION, true);
    await userEvent.click(await screen.findByRole("button", { name: /Tạo tài khoản/ }));
    const dialog = screen.getByRole("dialog");

    const group = await within(dialog).findByRole("group", { name: "Lớp học" });
    const label = within(dialog).getByText("Lớp học", { selector: "label" });
    expect(label).toHaveAttribute("for", group.id);
    expect(group).toHaveAccessibleDescription(
      "Tùy chọn gán tài khoản vào các lớp đang hoạt động ngay khi tạo.",
    );

    await userEvent.click(within(group).getByRole("checkbox", { name: /LOP-A/ }));
    await userEvent.type(within(dialog).getByLabelText(/Tên đăng nhập/), "student55");
    await userEvent.click(within(dialog).getByRole("button", { name: "Tạo ngẫu nhiên" }));
    await userEvent.type(within(dialog).getByLabelText(/Họ và tên/), "Học viên 55");
    await userEvent.click(within(dialog).getByRole("button", { name: "Tạo tài khoản" }));

    await waitFor(() =>
      expect(group).toHaveAccessibleDescription(/Lớp học được chọn đang ngừng sử dụng\./),
    );
  });
});

describe("UsersListView: khung hẹp thành danh sách thẻ (UNI-2587)", () => {
  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it("khung 358px: danh sách có tên thay cho bảng, mỗi tài khoản một thẻ đủ tiêu đề, họ tên, chip và email", async () => {
    installContainerWidth(PHONE_CONTENT_WIDTH);
    renderUsersView(makeDeps());

    const list = await screen.findByRole("list", { name: "Danh sách tài khoản" });
    await within(list).findByText("student01");
    expect(screen.queryByRole("table")).toBeNull();
    const cards = within(list).getAllByRole("listitem");
    expect(cards).toHaveLength(SAMPLE_USERS.length);

    const first = cards[0]!;
    // Tiêu đề là liên kết tới chi tiết, vùng bấm phủ cả thẻ; tên của liên kết chỉ là tên đăng nhập.
    const link = within(first).getByRole("link", { name: "student01" });
    expect(link).toHaveAttribute("href", `/users/${SAMPLE_USERS[0]!.id}`);
    expect(link).toHaveClass("after:absolute", "after:inset-0", "focus-visible:ring-3");
    expect(first).toHaveClass("relative");
    expect(within(first).getByText("Học viên 01")).toHaveClass("text-caption");
    // Chip trạng thái luôn hiện, kể cả Hoạt động; sau đó vai trò và nguồn.
    expect(within(first).getByText("Hoạt động").closest("[data-tone]")).toHaveAttribute("data-tone", "success");
    expect(within(first).getByText("Học viên", { selector: "[data-variant='tag']" })).toBeInTheDocument();
    expect(within(first).getByText("Do quản trị tạo")).toBeInTheDocument();
    expect(first).toHaveTextContent("Email: student01@example.com");

    // Tài khoản không có email thì không có mục Email; trạng thái suy ra (chưa đăng nhập, đã xóa) vẫn có chip.
    expect(cards[1]).not.toHaveTextContent("Email:");
    expect(within(cards[1]!).getByText("Chưa đăng nhập")).toBeInTheDocument();
    expect(within(cards[3]!).getByText("Đã xóa")).toBeInTheDocument();
  });

  it("thao tác của thẻ là menu ⋯ của dòng, nằm trên vùng bấm của liên kết", async () => {
    installContainerWidth(PHONE_CONTENT_WIDTH);
    renderUsersView(makeDeps());
    const list = await screen.findByRole("list", { name: "Danh sách tài khoản" });
    const menuButton = await within(list).findByRole("button", { name: "Thao tác với student01" });
    expect(menuButton.closest(".z-1")).toHaveClass("relative");

    await userEvent.click(menuButton);
    expect(await screen.findByRole("menuitem", { name: "Đặt lại mật khẩu" })).toBeInTheDocument();
    expect(screen.getByRole("menuitem", { name: "Xem chi tiết" })).toHaveAttribute(
      "href",
      `/users/${SAMPLE_USERS[0]!.id}`,
    );
  });

  it("không có quyền USER.MODIFY: thẻ không có nút thao tác và không có ô chọn", async () => {
    installContainerWidth(PHONE_CONTENT_WIDTH);
    renderUsersView(makeDeps(), { ...TEST_ADMIN_SESSION, permissions: ["USER.VIEW"] });
    const list = await screen.findByRole("list", { name: "Danh sách tài khoản" });
    await within(list).findByText("student01");
    expect(within(list).queryByRole("button", { name: /Thao tác với/ })).toBeNull();
    expect(screen.queryByRole("checkbox", { name: /Chọn tài khoản/ })).toBeNull();
    expect(screen.queryByRole("checkbox", { name: "Chọn tất cả tài khoản" })).toBeNull();
  });

  it("chọn thẻ hiện thanh thao tác hàng loạt; Bỏ chọn đưa focus về ô Chọn tất cả ở đầu danh sách", async () => {
    installContainerWidth(PHONE_CONTENT_WIDTH);
    renderUsersView(makeDeps());
    await screen.findByRole("list", { name: "Danh sách tài khoản" });
    await screen.findByText("student01");

    const barName = "Thao tác với các tài khoản đã chọn";
    // Thanh giữ sẵn chỗ nhưng ẩn với trình đọc màn hình khi chưa chọn.
    expect(screen.queryByRole("group", { name: barName })).toBeNull();

    await userEvent.click(screen.getByRole("checkbox", { name: "Chọn tài khoản student01" }));
    const bar = screen.getByRole("group", { name: barName });
    expect(bar).toHaveTextContent("Đã chọn 1 tài khoản");
    expect(screen.getAllByRole("listitem")[0]).toHaveAttribute("data-state", "selected");

    const selectAll = screen.getByRole("checkbox", { name: "Chọn tất cả tài khoản" });
    await userEvent.click(selectAll);
    expect(bar).toHaveTextContent(`Đã chọn ${SAMPLE_USERS.length} tài khoản`);

    const clearSelection = within(bar).getByRole("button", { name: "Bỏ chọn" });
    clearSelection.focus();
    await userEvent.keyboard("{Enter}");
    expect(selectAll).toHaveFocus();
    expect(screen.queryByRole("group", { name: barName })).toBeNull();
  });

  it("ô Sắp xếp theo thay cho tiêu đề cột: gửi sắp xếp lên máy chủ và đọc thông báo như khi bấm tiêu đề cột", async () => {
    const listUsersMock = vi.fn<UsersApiDeps["listUsers"]>(async () => ({
      items: SAMPLE_USERS,
      page: 0,
      size: 20,
      totalItems: SAMPLE_USERS.length,
      totalPages: 1,
    }));
    installContainerWidth(PHONE_CONTENT_WIDTH);
    renderUsersView(makeDeps({ listUsers: listUsersMock }));
    await screen.findByText("student01");

    const sort = screen.getByRole("combobox", { name: "Sắp xếp theo" });
    await userEvent.click(sort);
    const options = (await screen.findAllByRole("option")).map((option) => option.textContent);
    expect(options).toEqual([
      "Tên đăng nhập · tăng dần",
      "Tên đăng nhập · giảm dần",
      "Họ và tên · tăng dần",
      "Họ và tên · giảm dần",
    ]);
    await userEvent.click(screen.getByRole("option", { name: "Họ và tên · giảm dần" }));

    await waitFor(() =>
      expect(listUsersMock).toHaveBeenLastCalledWith(expect.objectContaining({ sort: "fullName,desc", page: 0 })),
    );
    expect(screen.getByRole("status")).toHaveTextContent("Đã sắp xếp theo Họ và tên, giảm dần.");
    expect(screen.getByRole("combobox", { name: "Sắp xếp theo" })).toHaveTextContent("Họ và tên · giảm dần");
  });

  it("khung rộng: vẫn là bảng như trước, nới khung từ hẹp sang rộng thì về bảng", async () => {
    const setWidth = installContainerWidth(PHONE_CONTENT_WIDTH);
    renderUsersView(makeDeps());
    await screen.findByRole("list", { name: "Danh sách tài khoản" });

    setWidth(1100);
    const table = screen.getByRole("table", { name: "Danh sách tài khoản" });
    expect(screen.queryByRole("list", { name: "Danh sách tài khoản" })).toBeNull();
    expect(within(table).getByRole("columnheader", { name: "Email" })).toBeInTheDocument();
    expect(within(table).getByRole("button", { name: "Họ và tên" })).toBeInTheDocument();
    expect(screen.queryByRole("combobox", { name: "Sắp xếp theo" })).toBeNull();
  });
});
