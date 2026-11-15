import { screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { SessionProvider } from "@/features/session/session-provider";
import type { Session } from "@/features/session/types";
import { TEST_ADMIN_SESSION } from "@/test/fixtures";
import en from "@/messages/en.json";
import viMessages from "@/messages/vi.json";
import { renderWithProviders } from "@/test/render";
import { notify } from "@thi360/ui";
import { defaultUsersApiDeps, type UserDetail, type UsersApiDeps, UsersApiError } from "../api";
import { UserDetailView } from "../components/user-detail-view";
import { createResetPasswordSchema } from "../schema";

const routerMock = vi.hoisted(() => ({ push: vi.fn() }));
vi.mock("next/navigation", () => ({
  useRouter: () => routerMock,
  usePathname: () => window.location.pathname,
  useSearchParams: () => new URLSearchParams(window.location.search),
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
    getUserClasses: vi.fn<UsersApiDeps["getUserClasses"]>(async (userId) => ({
      userId,
      currentClasses: [
        {
          classId: "cls-1",
          classCode: "LOP-A",
          className: "Lớp A",
          classStatus: "ACTIVE",
          joinedAt: "2026-11-10T08:00:00Z",
          addedByUsername: "ops.admin",
        },
      ],
      history: [
        {
          id: "hist-1",
          classId: "cls-1",
          classCode: "LOP-A",
          className: "Lớp A",
          action: "TRANSFER_IN",
          relatedClassId: "cls-0",
          relatedClassCode: "LOP-0",
          relatedClassName: "Lớp 0",
          actorUsername: "ops.admin",
          reason: "Đổi ca học",
          occurredAt: "2026-11-12T08:00:00Z",
        },
        {
          id: "hist-2",
          classId: "cls-0",
          classCode: "LOP-0",
          className: "Lớp 0",
          action: "JOIN",
          actorUsername: "ops.admin",
          occurredAt: "2026-11-10T08:00:00Z",
        },
      ],
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
  deleted = false,
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
  /** Backend thật trả 404 cho cả ba API đọc phân quyền của tài khoản đã xóa mềm. */
  deleted?: boolean;
} = {}) {
  const fetchMock = vi.fn(async (input: RequestInfo | URL) => {
    const url = typeof input === "string" ? input : input instanceof URL ? input.toString() : input.url;
    if (deleted && /\/users\/[^/]+\/(roles|permissions|effective-permissions)$/.test(url)) {
      return new Response(JSON.stringify({ code: "USER_NOT_FOUND", message: "User not found" }), {
        status: 404,
        headers: { "Content-Type": "application/json" },
      });
    }
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
        JSON.stringify({ userId: BASE_USER.id, hasManageRole, permissions: [] }),
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
  });
  vi.stubGlobal("fetch", fetchMock);
  return fetchMock;
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
  // Tab của trang chi tiết nằm trong URL: mỗi test bắt đầu ở đường dẫn sạch.
  window.history.replaceState(null, "", `/users/${BASE_USER.id}`);
});

afterEach(() => {
  vi.unstubAllGlobals();
  vi.restoreAllMocks();
});

describe("UserDetailView & action dialogs (Slice 2)", () => {
  it("hiển thị mã tài khoản, lần đăng nhập cuối, tab Lớp học thật, tab Vai trò & quyền thật và lịch sử thay đổi", async () => {
    const deps = makeDeps();
    renderDetail(deps);

    expect(await screen.findByRole("heading", { name: "Học viên 01" }, { timeout: 3000 })).toBeInTheDocument();
    expect(screen.getByText("U000101")).toBeInTheDocument();
    expect(screen.getByText(/student01 · U000101/)).toBeInTheDocument();

    await userEvent.click(screen.getByRole("tab", { name: "Lớp học" }));
    expect(await screen.findByRole("heading", { name: /Lớp học hiện tại/ })).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "LOP-A" })).toHaveAttribute("href", "/classes/cls-1");
    expect(screen.getByText("Chuyển đến")).toBeInTheDocument();
    expect(screen.getByText("Đổi ca học")).toBeInTheDocument();
    expect(deps.getUserClasses).toHaveBeenCalledWith(BASE_USER.id);

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
    expect(screen.queryByRole("button", { name: "Đặt lại mật khẩu" })).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Xóa" })).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Lưu thay đổi" })).not.toBeInTheDocument();
    expect(screen.queryByRole("tab", { name: "Lịch sử thay đổi" })).not.toBeInTheDocument();
    expect(deps.listUserAuditEvents).not.toHaveBeenCalled();
    await userEvent.click(screen.getByRole("tab", { name: "Lớp học" }));
    expect(await screen.findByText("Không có quyền xem thông tin lớp học")).toBeInTheDocument();
    expect(deps.getUserClasses).not.toHaveBeenCalled();
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
    // U-15: nút xác nhận luôn bấm được; bấm khi chưa có lý do thì báo lỗi ngay ở ô lý do thay vì khóa nút.
    expect(confirmBtn).toBeEnabled();
    await userEvent.click(confirmBtn);
    expect(reasonInput).toBeInvalid();
    expect(deactivateMock).not.toHaveBeenCalled();

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

    await userEvent.click(await screen.findByRole("button", { name: "Đặt lại mật khẩu" }));
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

    // Ô lý do có maxLength 500 (U-16): dán 501 ký tự thì ô chỉ nhận 500, không cần báo lỗi sau khi gửi.
    // Quy tắc 500 ký tự của schema vẫn giữ làm lưới an toàn (kiểm ở test schema bên dưới).
    await userEvent.click(reasonInput);
    await userEvent.paste("a".repeat(501));
    expect((reasonInput as HTMLTextAreaElement).value).toHaveLength(500);
    expect(within(dialog).queryByText(viMessages.users.validation.reasonMax)).not.toBeInTheDocument();
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

    const fetchMock = stubRbacFetch({ deleted: true });
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
    expect(await screen.findByText(viMessages.rbac.userRbac.deletedAccountNote)).toBeInTheDocument();
    expect(screen.getByText("Học viên")).toBeInTheDocument();
    expect(screen.getByText("STUDENT")).toBeInTheDocument();
    expect(
      screen.queryByRole("heading", { name: viMessages.rbac.userRbac.directSectionTitle }),
    ).not.toBeInTheDocument();
    expect(
      screen.queryByRole("button", { name: viMessages.rbac.userRbac.assignRolesButton }),
    ).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: viMessages.rbac.actions.retry })).not.toBeInTheDocument();
    expect(screen.queryByText(viMessages.rbac.userRbac.errorTitle)).not.toBeInTheDocument();

    const requestedUrls = fetchMock.mock.calls.map(([input]) =>
      typeof input === "string" ? input : input instanceof URL ? input.toString() : input.url,
    );
    expect(
      requestedUrls.filter((url) =>
        /\/users\/[^/]+\/(roles|permissions|effective-permissions)$/.test(url),
      ),
    ).toEqual([]);
  });

  it("tab Vai trò & quyền của tài khoản đã xóa hiện ghi chú tiếng Anh khi locale là en", async () => {
    stubRbacFetch({ deleted: true });
    renderWithProviders(
      <SessionProvider loadSession={async () => TEST_ADMIN_SESSION}>
        <UserDetailView
          userId={BASE_USER.id}
          deps={makeDeps({
            getUser: vi.fn<UsersApiDeps["getUser"]>(async () => ({
              ...BASE_USER,
              status: "INACTIVE",
              deletedAt: "2026-11-13T10:00:00Z",
              deleteReason: "Đã nghỉ học",
            })),
          })}
        />
      </SessionProvider>,
      { locale: "en", messages: en },
    );

    await userEvent.click(await screen.findByRole("tab", { name: "Roles & permissions" }));
    expect(await screen.findByText(en.rbac.userRbac.deletedAccountNote)).toBeInTheDocument();
  });
});

describe("Hộp thoại xác nhận báo lỗi ngay bên trong, không dùng toast (U-07)", () => {
  it("ngừng hoạt động thất bại: lỗi hiện trong hộp thoại, thử lại thì xóa lỗi, đóng rồi mở lại không còn lỗi cũ", async () => {
    const deactivateMock = vi
      .fn<UsersApiDeps["deactivateUser"]>()
      .mockRejectedValueOnce(new UsersApiError(409, "raw backend text", "ALREADY_INACTIVE"))
      .mockRejectedValueOnce(new UsersApiError(500, "raw backend text", "INTERNAL_ERROR"))
      .mockResolvedValueOnce({ ...BASE_USER, status: "INACTIVE" });
    const toastError = vi.spyOn(notify, "error");
    renderDetail(makeDeps({ deactivateUser: deactivateMock }));

    await userEvent.click(await screen.findByRole("button", { name: "Ngừng hoạt động" }));
    let dialog = await screen.findByRole("dialog");
    await userEvent.type(within(dialog).getByLabelText(/Lý do ngừng hoạt động/), "Vi phạm");
    await userEvent.click(within(dialog).getByRole("button", { name: "Ngừng hoạt động" }));

    // Lần 1 thất bại: hộp thoại còn mở, lỗi nằm bên trong dưới dạng role=alert, không có toast.
    const alert = await within(dialog).findByRole("alert");
    expect(alert).toHaveTextContent(viMessages.users.errorCodes.ALREADY_INACTIVE);
    expect(screen.queryByText(/raw backend text/)).not.toBeInTheDocument();
    expect(toastError).not.toHaveBeenCalled();

    // Thử lại: lỗi cũ được thay bằng lỗi của lần mới.
    await userEvent.click(within(dialog).getByRole("button", { name: "Ngừng hoạt động" }));
    await waitFor(() =>
      expect(within(dialog).getByRole("alert")).toHaveTextContent(viMessages.users.errorCodes.INTERNAL_ERROR),
    );
    expect(within(dialog).getAllByRole("alert")).toHaveLength(1);

    // Đóng rồi mở lại: không mang lỗi của lần trước sang.
    await userEvent.click(within(dialog).getByRole("button", { name: "Hủy" }));
    await waitFor(() => expect(screen.queryByRole("dialog")).not.toBeInTheDocument());
    await userEvent.click(screen.getByRole("button", { name: "Ngừng hoạt động" }));
    dialog = await screen.findByRole("dialog");
    expect(within(dialog).queryByRole("alert")).not.toBeInTheDocument();
    expect(toastError).not.toHaveBeenCalled();
  });

  it("xóa thất bại (không phải lỗi chặn xóa): lỗi hiện trong hộp thoại xóa", async () => {
    const deleteMock = vi
      .fn<UsersApiDeps["deleteUser"]>()
      .mockRejectedValueOnce(new UsersApiError(403, "raw", "SELF_DELETE"));
    const toastError = vi.spyOn(notify, "error");
    renderDetail(makeDeps({ deleteUser: deleteMock }));

    await userEvent.click(await screen.findByRole("button", { name: "Xóa" }));
    const dialog = await screen.findByRole("dialog");
    await userEvent.type(within(dialog).getByLabelText(/Lý do xóa tài khoản/), "Trùng");
    await userEvent.click(within(dialog).getByRole("button", { name: "Xóa tài khoản" }));

    expect(await within(dialog).findByRole("alert")).toHaveTextContent(
      viMessages.users.errorCodes.SELF_DELETE,
    );
    expect(toastError).not.toHaveBeenCalled();
  });

  it("kích hoạt lại thất bại: lỗi hiện trong hộp thoại kích hoạt", async () => {
    const activateMock = vi
      .fn<UsersApiDeps["activateUser"]>()
      .mockRejectedValueOnce(new UsersApiError(409, "raw", "ALREADY_ACTIVE"));
    const toastError = vi.spyOn(notify, "error");
    renderDetail(
      makeDeps({
        getUser: vi.fn<UsersApiDeps["getUser"]>(async () => ({ ...BASE_USER, status: "INACTIVE" })),
        activateUser: activateMock,
      }),
    );

    await userEvent.click(await screen.findByRole("button", { name: "Kích hoạt lại" }));
    const dialog = await screen.findByRole("dialog");
    await userEvent.click(within(dialog).getByRole("button", { name: "Kích hoạt lại" }));

    expect(await within(dialog).findByRole("alert")).toHaveTextContent(
      viMessages.users.errorCodes.ALREADY_ACTIVE,
    );
    expect(toastError).not.toHaveBeenCalled();
  });
});

describe("ResetPasswordDialog: gửi biểu mẫu sai thì focus về ô sai đầu tiên (U-08)", () => {
  it("mật khẩu yếu: focus chuyển vào ô mật khẩu mới", async () => {
    const resetMock = vi.fn<UsersApiDeps["resetUserPassword"]>();
    renderDetail(makeDeps({ resetUserPassword: resetMock }));

    await userEvent.click(await screen.findByRole("button", { name: "Đặt lại mật khẩu" }));
    const dialog = await screen.findByRole("dialog");
    const passwordInput = dialog.querySelector<HTMLInputElement>("input[autocomplete='new-password']")!;
    await userEvent.type(passwordInput, "abc");
    await userEvent.click(within(dialog).getByRole("button", { name: "Đặt lại mật khẩu" }));

    await waitFor(() => expect(passwordInput).toHaveAttribute("aria-invalid", "true"));
    expect(passwordInput).toHaveFocus();
    expect(resetMock).not.toHaveBeenCalled();
  });
});

describe("ResetPasswordDialog: không làm mất dữ liệu đã nhập khi đóng nhầm (U-11)", () => {
  it("đã nhập mật khẩu: Esc hỏi lại, Tiếp tục nhập giữ dữ liệu, Bỏ thay đổi mới đóng; chưa nhập thì đóng ngay", async () => {
    renderDetail(makeDeps());

    await userEvent.click(await screen.findByRole("button", { name: "Đặt lại mật khẩu" }));
    let dialog = await screen.findByRole("dialog", { name: "Đặt lại mật khẩu" });
    await userEvent.keyboard("{Escape}");
    await waitFor(() => expect(screen.queryByRole("dialog")).not.toBeInTheDocument());

    await userEvent.click(screen.getByRole("button", { name: "Đặt lại mật khẩu" }));
    dialog = await screen.findByRole("dialog", { name: "Đặt lại mật khẩu" });
    const passwordInput = dialog.querySelector<HTMLInputElement>("input[autocomplete='new-password']")!;
    await userEvent.type(passwordInput, "Admin@123");

    await userEvent.keyboard("{Escape}");
    let discard = await screen.findByRole("dialog", { name: "Bỏ thông tin đã nhập?" });
    await userEvent.click(within(discard).getByRole("button", { name: "Tiếp tục nhập" }));
    await waitFor(() =>
      expect(screen.queryByRole("dialog", { name: "Bỏ thông tin đã nhập?" })).not.toBeInTheDocument(),
    );
    expect(passwordInput).toHaveValue("Admin@123");

    await userEvent.click(within(dialog).getByRole("button", { name: "Hủy" }));
    discard = await screen.findByRole("dialog", { name: "Bỏ thông tin đã nhập?" });
    await userEvent.click(within(discard).getByRole("button", { name: "Bỏ thay đổi" }));
    await waitFor(() => expect(screen.queryByRole("dialog")).not.toBeInTheDocument());
  });
});

describe("Biểu mẫu sửa thông tin: kiểm tra khi rời ô và gợi ý cho ô nhập (U-16)", () => {
  it("email sai báo lỗi khi rời ô; ô có maxLength và tắt tự điền", async () => {
    renderDetail(makeDeps());
    const email = await screen.findByLabelText(/^Email/);
    expect(email).toHaveAttribute("maxlength", "255");
    expect(email).toHaveAttribute("autocomplete", "off");
    expect(screen.getByLabelText(/Họ và tên/)).toHaveAttribute("maxlength", "200");
    expect(screen.getByLabelText(/Số điện thoại/)).toHaveAttribute("maxlength", "30");

    await userEvent.clear(email);
    await userEvent.type(email, "sai-email");
    await userEvent.tab();
    await waitFor(() => expect(email).toHaveAttribute("aria-invalid", "true"));
  });

  it("ô lý do đặt lại mật khẩu giới hạn 500 ký tự", async () => {
    renderDetail(makeDeps());
    await userEvent.click(await screen.findByRole("button", { name: "Đặt lại mật khẩu" }));
    const dialog = await screen.findByRole("dialog");
    expect(within(dialog).getByLabelText(/Lý do đặt lại mật khẩu/)).toHaveAttribute("maxlength", "500");
  });
});

describe("Schema đặt lại mật khẩu", () => {
  it("vẫn từ chối lý do dài hơn 500 ký tự (lưới an toàn sau maxLength của ô nhập)", () => {
    const schema = createResetPasswordSchema(
      { passwordWeak: "weak", passwordSameAsUsername: "same", reasonMax: "too long" },
      "student01",
    );
    const result = schema.safeParse({ password: "Admin@123", reason: "a".repeat(501), mustChangePassword: false });
    expect(result.success).toBe(false);
    expect(result.error?.issues[0]?.message).toBe("too long");
  });
});

describe("Trang chi tiết: tiêu đề riêng, tab trong URL, dòng chưa đăng nhập (U-22)", () => {
  it("tiêu đề trang mang tên đăng nhập của tài khoản", async () => {
    renderDetail(makeDeps());
    await screen.findByRole("heading", { name: BASE_USER.fullName });
    await waitFor(() => expect(document.title).toBe("student01 · Tài khoản · Thi 360"));
  });

  it("đổi tab ghi ?tab= vào URL (giữ tham số khác); về tab mặc định thì bỏ tham số", async () => {
    window.history.replaceState(null, "", `/users/${BASE_USER.id}?from=list`);
    renderDetail(makeDeps());
    await userEvent.click(await screen.findByRole("tab", { name: "Lớp học" }));
    expect(window.location.search).toBe("?from=list&tab=classes");

    await userEvent.click(screen.getByRole("tab", { name: "Thông tin" }));
    expect(window.location.search).toBe("?from=list");
  });

  it("mở trang với ?tab=history thì tab Lịch sử thay đổi đang chọn", async () => {
    window.history.replaceState(null, "", `/users/${BASE_USER.id}?tab=history`);
    renderDetail(makeDeps());
    const historyTab = await screen.findByRole("tab", { name: "Lịch sử thay đổi" });
    expect(historyTab).toHaveAttribute("aria-selected", "true");
  });

  it("?tab= không hợp lệ hoặc tab không có quyền xem thì mở tab Thông tin", async () => {
    window.history.replaceState(null, "", `/users/${BASE_USER.id}?tab=history`);
    renderDetail(makeDeps(), { ...TEST_ADMIN_SESSION, permissions: ["USER.VIEW"] });
    expect(await screen.findByRole("tab", { name: "Thông tin" })).toHaveAttribute("aria-selected", "true");
  });

  it("tài khoản chưa đăng nhập lần nào: dòng phụ không ghép thành \"Đăng nhập cuối Chưa đăng nhập\"", async () => {
    renderDetail(
      makeDeps({ getUser: vi.fn<UsersApiDeps["getUser"]>(async () => ({ ...BASE_USER, lastLoginAt: undefined })) }),
    );
    expect(
      await screen.findByText("student01 · U000101 · Do quản trị tạo · Chưa đăng nhập lần nào"),
    ).toBeInTheDocument();
    expect(screen.queryByText(/Đăng nhập cuối Chưa đăng nhập/)).not.toBeInTheDocument();
  });
});

describe("Trang chi tiết tài khoản dùng được từ 360px (UNI-2586)", () => {
  it("bố cục chia theo bề rộng vùng chứa: hai thẻ từ @2xl, danh sách thông tin hai cột theo bề rộng thẻ", async () => {
    renderDetail(makeDeps());
    const heading = await screen.findByRole("heading", { name: BASE_USER.fullName });
    // Tên dài xuống dòng trong khối tên, không đẩy nút thao tác ra ngoài thẻ.
    expect(heading).toHaveClass("break-words");
    expect(heading.parentElement!.parentElement).toHaveClass("min-w-0");

    const codeTerm = screen.getByText("Mã tài khoản");
    const list = codeTerm.closest("dl")!;
    expect(list).toHaveClass("grid-cols-1", "@[17rem]:grid-cols-2", "[&_dd]:wrap-anywhere");
    expect(list.parentElement).toHaveClass("@container");
    const grid = list.parentElement!.parentElement!;
    expect(grid).toHaveClass("grid-cols-1", "@2xl:grid-cols-2");
    expect(grid.closest(".\\@container")).not.toBeNull();
    expect(grid.className).not.toMatch(/(^|\s)lg:grid-cols-2/);
  });
});
