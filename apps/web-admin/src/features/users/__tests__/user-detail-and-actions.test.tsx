import { screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { SessionProvider } from "@/features/session/session-provider";
import type { Session } from "@/features/session/types";
import { TEST_ADMIN_SESSION } from "@/test/fixtures";
import en from "@/messages/en.json";
import viMessages from "@/messages/vi.json";
import { renderWithProviders } from "@/test/render";
import { defaultUsersApiDeps, type UserDetail, type UsersApiDeps, UsersApiError } from "../api";
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

function makeDeps(overrides: Partial<UsersApiDeps> = {}): Partial<UsersApiDeps> {
  return {
    ...defaultUsersApiDeps,
    getUser: vi.fn<UsersApiDeps["getUser"]>(async () => BASE_USER),
    updateUser: vi.fn<UsersApiDeps["updateUser"]>(async (_id, body) => ({
      ...BASE_USER,
      fullName: body.fullName ?? BASE_USER.fullName,
      email: body.email ?? BASE_USER.email,
      phone: body.phone ?? BASE_USER.phone,
      version: BASE_USER.version + 1,
    })),
    checkUserDeactivation: vi.fn<UsersApiDeps["checkUserDeactivation"]>(async () => ({
      activeAttempt: false,
    })),
    deactivateUser: vi.fn<UsersApiDeps["deactivateUser"]>(async (_id, body) => ({
      ...BASE_USER,
      status: "INACTIVE",
      inactiveReason: body.reason,
      inactivatedAt: "2026-11-14T09:00:00Z",
    })),
    activateUser: vi.fn<UsersApiDeps["activateUser"]>(async () => ({
      ...BASE_USER,
      status: "ACTIVE",
    })),
    resetUserPassword: vi.fn<UsersApiDeps["resetUserPassword"]>(async () => BASE_USER),
    checkUserDeletion: vi.fn<UsersApiDeps["checkUserDeletion"]>(async () => ({
      deletable: true,
      blockers: [],
    })),
    deleteUser: vi.fn<UsersApiDeps["deleteUser"]>(async () => undefined),
    listUserAuditEvents: vi.fn<UsersApiDeps["listUserAuditEvents"]>(async () => ({
      items: [
        {
          id: "evt-1",
          occurredAt: "2026-11-12T08:35:00Z",
          actorUsername: "ops.admin",
          actorRoles: ["OPERATIONS"],
          actorType: "USER",
          module: "IDENTITY",
          action: "USER_CREATED",
          targetType: "USER",
          targetId: BASE_USER.id,
          outcome: "SUCCESS",
          reason: "Tạo mới",
        },
      ],
      page: 0,
      size: 20,
      totalItems: 1,
      totalPages: 1,
    })),
    ...overrides,
  };
}

function stubRbacFetch({
  roles = [
    {
      roleId: "r-student",
      roleCode: "STUDENT",
      roleName: "Học viên",
      access: "EXAM",
      status: "ACTIVE",
      system: true,
      assignedAt: "2026-11-10T08:00:00Z",
    },
  ],
  hasManageRole = false,
}: {
  roles?: Array<{
    roleId: string;
    roleCode: string;
    roleName: string;
    access: "MANAGE" | "EXAM";
    status: "ACTIVE" | "INACTIVE";
    system: boolean;
    assignedAt: string;
  }>;
  hasManageRole?: boolean;
} = {}) {
  vi.stubGlobal(
    "fetch",
    vi.fn(async (input: RequestInfo | URL) => {
      const url = typeof input === "string" ? input : input instanceof URL ? input.toString() : input.url;
      if (url.endsWith("/effective-permissions")) {
        return new Response(
          JSON.stringify({
            userId: BASE_USER.id,
            effectiveAccess: hasManageRole ? "MANAGE" : "EXAM",
            hasManageRole,
            inactiveDirectPermissions: [],
            permissions: [],
          }),
          { status: 200, headers: { "Content-Type": "application/json" } },
        );
      }
      if (url.endsWith("/permissions")) {
        return new Response(
          JSON.stringify({
            userId: BASE_USER.id,
            hasManageRole,
            permissions: [],
          }),
          { status: 200, headers: { "Content-Type": "application/json" } },
        );
      }
      if (url.endsWith("/roles")) {
        return new Response(
          JSON.stringify({
            userId: BASE_USER.id,
            access: hasManageRole ? "MANAGE" : "EXAM",
            roles,
          }),
          { status: 200, headers: { "Content-Type": "application/json" } },
        );
      }
      return new Response(JSON.stringify({}), {
        status: 404,
        headers: { "Content-Type": "application/json" },
      });
    }),
  );
}

function renderDetail(
  deps: Partial<UsersApiDeps>,
  session: Session = TEST_ADMIN_SESSION,
) {
  return renderWithProviders(
    <SessionProvider loadSession={async () => session}>
      <UserDetailView userId={BASE_USER.id} deps={deps} />
    </SessionProvider>,
  );
}

beforeEach(() => {
  routerMock.push.mockReset();
  stubRbacFetch();
});

afterEach(() => {
  vi.unstubAllGlobals();
  vi.restoreAllMocks();
});

describe("UserDetailView & action dialogs (Slice 2)", () => {
  it("hiển thị mã tài khoản, lần đăng nhập cuối, tab chỗ chờ T10, tab Vai trò & quyền thật và lịch sử thay đổi", async () => {
    const deps = makeDeps();
    renderDetail(deps);

    expect(await screen.findByRole("heading", { name: "Học viên 01" }, { timeout: 3000 })).toBeInTheDocument();
    expect(screen.getByText("U000101")).toBeInTheDocument();
    expect(screen.getByText(/student01 · U000101/)).toBeInTheDocument();

    await userEvent.click(screen.getByRole("tab", { name: "Lớp học" }));
    expect(await screen.findByText("Chưa kết nối dữ liệu lớp học")).toBeInTheDocument();

    await userEvent.click(screen.getByRole("tab", { name: "Vai trò & quyền" }));
    expect(
      await screen.findByRole("heading", { name: viMessages.rbac.userRbac.directSectionTitle }),
    ).toBeInTheDocument();
    expect(screen.getByRole("button", { name: viMessages.rbac.userRbac.assignRolesButton })).toBeEnabled();
    expect(screen.getByText("Học viên")).toBeInTheDocument();
    expect(screen.queryByText("Chưa kết nối dữ liệu vai trò & quyền")).not.toBeInTheDocument();
    expect(screen.queryByText(/\(T08\)|\(T10\)/)).not.toBeInTheDocument();

    await userEvent.click(screen.getByRole("tab", { name: "Lịch sử thay đổi" }));
    expect(await screen.findByText("Tạo tài khoản")).toBeInTheDocument();
    expect(deps.listUserAuditEvents).toHaveBeenCalledWith(
      BASE_USER.id,
      expect.objectContaining({ page: 0, size: 20, sort: "occurredAt,desc" }),
    );
  });

  it("người chỉ có USER.VIEW không thấy nút hành động, không sửa được và không gọi audit-events", async () => {
    const deps = makeDeps();
    renderDetail(deps, { ...TEST_ADMIN_SESSION, permissions: ["USER.VIEW"] });

    expect(await screen.findByRole("heading", { name: "Học viên 01" })).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Ngừng hoạt động" })).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Reset mật khẩu" })).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Xóa" })).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Lưu thay đổi" })).not.toBeInTheDocument();
    expect(screen.queryByRole("tab", { name: "Lịch sử thay đổi" })).not.toBeInTheDocument();
    expect(deps.listUserAuditEvents).not.toHaveBeenCalled();
  });

  it("deactivation-check có lượt thi đang làm hiện cảnh báo trước khi xác nhận ngừng hoạt động", async () => {
    const deactivateMock = vi.fn<UsersApiDeps["deactivateUser"]>(async (_id, body) => ({
      ...BASE_USER,
      status: "INACTIVE",
      inactiveReason: body.reason,
    }));
    const deps = makeDeps({
      checkUserDeactivation: vi.fn<UsersApiDeps["checkUserDeactivation"]>(async () => ({
        activeAttempt: true,
      })),
      deactivateUser: deactivateMock,
    });
    const consoleErrorSpy = vi.spyOn(console, "error").mockImplementation(() => {});
    renderDetail(deps);

    await userEvent.click(await screen.findByRole("button", { name: "Ngừng hoạt động" }));

    expect(
      await screen.findByText(/Cảnh báo: Tài khoản đang có lượt thi chưa hoàn thành/),
    ).toBeInTheDocument();
    const nestingWarnings = consoleErrorSpy.mock.calls.filter((args) =>
      args.some((arg) => typeof arg === "string" && /cannot be a descendant of|validateDOMNesting|hydration/i.test(arg)),
    );
    expect(nestingWarnings).toEqual([]);
    consoleErrorSpy.mockRestore();

    const reasonInput = screen.getByLabelText(/Lý do ngừng hoạt động/);
    const confirmBtn = screen.getByRole("dialog").querySelector("button:last-child") as HTMLButtonElement;
    expect(confirmBtn).toBeDisabled();

    await userEvent.type(reasonInput, "Vi phạm quy chế");
    await userEvent.click(confirmBtn);

    await waitFor(() => {
      expect(deactivateMock).toHaveBeenCalledWith(BASE_USER.id, { reason: "Vi phạm quy chế" });
    });
  });

  it("deletion-check bị chặn mở dialog Không thể xóa và nút Ngừng hoạt động thay thế chuyển sang luồng ngừng hoạt động", async () => {
    const checkDeactivateMock = vi.fn<UsersApiDeps["checkUserDeactivation"]>(async () => ({
      activeAttempt: false,
    }));
    const deps = makeDeps({
      checkUserDeletion: vi.fn<UsersApiDeps["checkUserDeletion"]>(async () => ({
        deletable: false,
        blockers: [
          {
            source: "COMMERCE",
            code: "USER_HAS_TRANSACTIONS",
            message: "Tài khoản đã có đơn hàng thanh toán",
          },
        ],
      })),
      checkUserDeactivation: checkDeactivateMock,
    });
    renderDetail(deps);

    await userEvent.click(await screen.findByRole("button", { name: "Xóa" }));

    expect(await screen.findByText("Không thể xóa tài khoản")).toBeInTheDocument();
    expect(screen.getByText(/Đơn hàng & giao dịch:/)).toBeInTheDocument();
    expect(screen.getByText(viMessages.users.errorCodes.USER_HAS_TRANSACTIONS)).toBeInTheDocument();
    expect(screen.queryByText(/Tài khoản đã có đơn hàng thanh toán/)).not.toBeInTheDocument();
    expect(screen.queryByText(/COMMERCE:/)).not.toBeInTheDocument();
    await userEvent.click(screen.getByRole("button", { name: "Ngừng hoạt động thay thế" }));

    expect(checkDeactivateMock).toHaveBeenCalledWith(BASE_USER.id);
    expect(await screen.findByText("Ngừng hoạt động tài khoản")).toBeInTheDocument();
  });

  it("chỉnh sửa thông tin tại chỗ xử lý VERSION_CONFLICT và hiển thị nút Tải lại", async () => {
    const deps = makeDeps({
      updateUser: vi.fn<UsersApiDeps["updateUser"]>(async () => {
        throw new UsersApiError(409, "conflict", "VERSION_CONFLICT");
      }),
    });
    renderDetail(deps);

    const nameInput = await screen.findByLabelText(/Họ và tên/);
    await userEvent.clear(nameInput);
    await userEvent.type(nameInput, "Học viên 01 mới");
    await userEvent.click(screen.getByRole("button", { name: "Lưu thay đổi" }));

    expect(
      await screen.findByText(/Tài khoản vừa được cập nhật ở nơi khác/),
    ).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Tải lại" })).toBeInTheDocument();
  });

  it("tab Vai trò & quyền và hộp thoại chặn xóa hiển thị khóa dịch tiếng Anh khi locale là en", async () => {
    stubRbacFetch({
      hasManageRole: true,
      roles: [
        {
          roleId: "r-sys",
          roleCode: "SYSTEM_ADMIN",
          roleName: "Quản trị hệ thống",
          access: "MANAGE",
          status: "ACTIVE",
          system: true,
          assignedAt: "2026-11-10T08:00:00Z",
        },
        {
          roleId: "r-custom",
          roleCode: "GRADER",
          roleName: "Essay Grader",
          access: "MANAGE",
          status: "ACTIVE",
          system: false,
          assignedAt: "2026-11-10T08:00:00Z",
        },
      ],
    });
    const deps = makeDeps({
      getUser: vi.fn<UsersApiDeps["getUser"]>(async () => ({
        ...BASE_USER,
        roles: [
          { id: "r-sys", code: "SYSTEM_ADMIN", name: "Quản trị hệ thống", access: "MANAGE" },
          { id: "r-custom", code: "GRADER", name: "Essay Grader", access: "MANAGE" },
        ],
      })),
      checkUserDeletion: vi.fn<UsersApiDeps["checkUserDeletion"]>(async () => ({
        deletable: false,
        blockers: [
          {
            source: "COMMERCE",
            code: "USER_HAS_ORDERS",
            message: "Chuỗi tiếng Việt thô từ backend",
          },
        ],
      })),
    });

    renderWithProviders(
      <SessionProvider loadSession={async () => TEST_ADMIN_SESSION}>
        <UserDetailView userId={BASE_USER.id} deps={deps} />
      </SessionProvider>,
      { locale: "en", messages: en },
    );

    await userEvent.click(await screen.findByRole("tab", { name: "Roles & permissions" }));
    expect(await screen.findByText("System administrator")).toBeInTheDocument();
    expect(screen.getByText("Essay Grader")).toBeInTheDocument();
    expect(screen.queryByText("Quản trị hệ thống")).not.toBeInTheDocument();

    await userEvent.click(screen.getByRole("button", { name: "Delete" }));
    expect(await screen.findByText("Cannot delete account")).toBeInTheDocument();
    expect(screen.getByText(/Orders & transactions:/)).toBeInTheDocument();
    expect(screen.getByText(en.users.errorCodes.USER_HAS_ORDERS)).toBeInTheDocument();
    expect(screen.queryByText(/Chuỗi tiếng Việt thô/)).not.toBeInTheDocument();
  });

  it("ResetPasswordDialog kiểm tra trùng username, giới hạn lý do 500 ký tự, sinh mật khẩu ngẫu nhiên, ánh xạ PASSWORD_WEAK và gửi đúng payload", async () => {
    const targetUser: UserDetail = { ...BASE_USER, username: "Student@01" };
    const resetMock = vi
      .fn<UsersApiDeps["resetUserPassword"]>()
      .mockRejectedValueOnce(
        new UsersApiError(400, "raw", "PASSWORD_WEAK", [
          { field: "password", code: "PASSWORD_DIGIT", message: "raw" },
        ]),
      )
      .mockResolvedValueOnce(targetUser);

    renderDetail(makeDeps({ getUser: async () => targetUser, resetUserPassword: resetMock }));

    await userEvent.click(await screen.findByRole("button", { name: "Reset mật khẩu" }));
    const dialog = screen.getByRole("dialog");
    const passwordInput = within(dialog).getByLabelText(/Mật khẩu mới/);
    const reasonInput = within(dialog).getByLabelText(/Lý do đặt lại mật khẩu/);
    const submitButton = within(dialog).getByRole("button", { name: "Đặt lại mật khẩu" });

    await userEvent.type(passwordInput, targetUser.username);
    await userEvent.click(submitButton);
    expect(await within(dialog).findByText(viMessages.users.validation.passwordSameAsUsername)).toBeInTheDocument();
    expect(resetMock).not.toHaveBeenCalled();
    await userEvent.click(within(dialog).getByRole("button", { name: "Tạo ngẫu nhiên" }));
    expect(passwordInput).toHaveAttribute("type", "text");
    const generated = (passwordInput as HTMLInputElement).value;
    expect(generated.length).toBe(12);

    await userEvent.click(reasonInput);
    await userEvent.paste("a".repeat(501));
    await userEvent.click(submitButton);
    expect(await within(dialog).findByText(viMessages.users.validation.reasonMax)).toBeInTheDocument();
    expect(resetMock).not.toHaveBeenCalled();

    await userEvent.clear(reasonInput);
    await userEvent.type(reasonInput, "  Cấp lại mật khẩu  ");
    await userEvent.click(within(dialog).getByLabelText(/Bắt đổi mật khẩu ở lần đăng nhập kế tiếp/));
    await userEvent.click(submitButton);

    expect(await within(dialog).findByText(viMessages.users.errorCodes.PASSWORD_DIGIT)).toBeInTheDocument();
    expect(resetMock).toHaveBeenCalledTimes(1);

    await userEvent.click(submitButton);
    await waitFor(() => {
      expect(resetMock).toHaveBeenNthCalledWith(2, BASE_USER.id, {
        password: generated,
        mustChangePassword: true,
        reason: "Cấp lại mật khẩu",
      });
    });
  });

  it("tab Vai trò & quyền chuyển sang chế độ chỉ xem khi thiếu USER.ASSIGN hoặc tài khoản đã xóa", async () => {
    const { unmount } = renderDetail(makeDeps(), {
      ...TEST_ADMIN_SESSION,
      permissions: ["USER.VIEW"],
    });

    await userEvent.click(await screen.findByRole("tab", { name: "Vai trò & quyền" }));
    expect(
      await screen.findByRole("heading", { name: viMessages.rbac.userRbac.directSectionTitle }),
    ).toBeInTheDocument();
    expect(
      screen.queryByRole("button", { name: viMessages.rbac.userRbac.assignRolesButton }),
    ).not.toBeInTheDocument();
    expect(
      screen.queryByRole("button", { name: viMessages.rbac.userRbac.addDirectButton }),
    ).not.toBeInTheDocument();
    unmount();

    renderDetail(
      makeDeps({
        getUser: vi.fn<UsersApiDeps["getUser"]>(async () => ({
          ...BASE_USER,
          status: "INACTIVE",
          deletedAt: "2026-11-13T10:00:00Z",
          deleteReason: "Đã nghỉ học",
        })),
      }),
    );

    await userEvent.click(await screen.findByRole("tab", { name: "Vai trò & quyền" }));
    expect(
      await screen.findByRole("heading", { name: viMessages.rbac.userRbac.directSectionTitle }),
    ).toBeInTheDocument();
    expect(
      screen.queryByRole("button", { name: viMessages.rbac.userRbac.assignRolesButton }),
    ).not.toBeInTheDocument();
    expect(
      screen.queryByRole("button", { name: viMessages.rbac.userRbac.addDirectButton }),
    ).not.toBeInTheDocument();
  });
});
