import { screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { Toaster } from "@thi360/ui";
import { afterEach, beforeEach, describe, expect, it, vi, type Mock } from "vitest";
import { SessionProvider } from "@/features/session/session-provider";
import viMessages from "@/messages/vi.json";
import { TEST_ADMIN_SESSION } from "@/test/fixtures";
import { renderWithProviders } from "@/test/render";
import { RbacApiError, type ListRolesParams } from "../api";
import { RbacPageView, getAllowedRbacTabs } from "../components/rbac-page-view";
import type {
  CreateRoleRequest,
  PageResponseRoleSummaryResponse,
  PermissionResponse,
  RoleDetailResponse,
  RoleSummaryResponse,
} from "../types";

const navMocks = vi.hoisted(() => ({
  replace: vi.fn(),
  pathname: "/phan-quyen",
  search: "",
}));

vi.mock("next/navigation", () => ({
  useRouter: () => ({ replace: navMocks.replace }),
  usePathname: () => navMocks.pathname,
  useSearchParams: () => new URLSearchParams(navMocks.search),
}));

const PERMISSIONS_FIXTURE: PermissionResponse[] = [
  {
    id: "p-class-view",
    code: "CLASS.VIEW",
    bitIndex: 4,
    groupCode: "CLASS",
    groupName: "Lớp học",
    name: "Xem lớp học",
    scope: "MANAGE",
    roleCount: 2,
    userCount: 1,
  },
  {
    id: "p-class-modify",
    code: "CLASS.MODIFY",
    bitIndex: 5,
    groupCode: "CLASS",
    groupName: "Lớp học",
    name: "Xem và sửa lớp học",
    scope: "MANAGE",
    roleCount: 1,
    userCount: 1,
  },
  {
    id: "p-audit-view",
    code: "AUDIT.VIEW",
    bitIndex: 11,
    groupCode: "AUDIT",
    groupName: "Nhật ký",
    name: "Xem nhật ký",
    scope: "MANAGE",
    roleCount: 1,
    userCount: 1,
  },
];

const ROLES_FIXTURE: RoleSummaryResponse[] = [
  {
    id: "r-sys",
    code: "SYSTEM_ADMIN",
    name: "Quản trị hệ thống",
    access: "MANAGE",
    system: true,
    status: "ACTIVE",
    permissionCount: 13,
    userCount: 1,
    version: 0,
    createdAt: "2026-11-13T00:00:00Z",
    updatedAt: "2026-11-13T00:00:00Z",
  },
  {
    id: "r-custom-in-use",
    code: "CLASS_WATCHER",
    name: "Theo dõi lớp",
    access: "MANAGE",
    system: false,
    status: "ACTIVE",
    permissionCount: 1,
    userCount: 2,
    version: 1,
    createdAt: "2026-11-13T00:00:00Z",
    updatedAt: "2026-11-13T00:00:00Z",
  },
  {
    id: "r-custom-unused",
    code: "TEMP_ROLE",
    name: "Vai trò tạm",
    access: "EXAM",
    system: false,
    status: "INACTIVE",
    permissionCount: 0,
    userCount: 0,
    version: 0,
    createdAt: "2026-11-13T00:00:00Z",
    updatedAt: "2026-11-13T00:00:00Z",
  },
];

beforeEach(() => {
  navMocks.replace.mockReset();
  navMocks.pathname = "/phan-quyen";
  navMocks.search = "";
});

afterEach(() => {
  vi.unstubAllGlobals();
});

function renderRbacPage({
  permissions = TEST_ADMIN_SESSION.permissions,
  roles = ROLES_FIXTURE,
  createRole = vi.fn(async (payload: CreateRoleRequest): Promise<RoleDetailResponse> => ({
    id: "r-new",
    code: payload.code,
    name: payload.name,
    description: payload.description,
    access: payload.access,
    system: false,
    status: "ACTIVE",
    permissionCodes: payload.permissionCodes ?? [],
    permissionCount: payload.permissionCodes?.length ?? 0,
    userCount: 0,
    version: 0,
    createdAt: "2026-11-14T00:00:00Z",
    updatedAt: "2026-11-14T00:00:00Z",
  })),
  deleteRole = vi.fn(async () => undefined),
  getRole = vi.fn(async (id: string): Promise<RoleDetailResponse> => {
    const found = roles.find((r) => r.id === id) ?? roles[0]!;
    return { ...found, permissionCodes: ["CLASS.VIEW"] };
  }),
  listRoles = vi.fn(async (): Promise<PageResponseRoleSummaryResponse> => ({
    items: roles,
    page: 0,
    size: 20,
    totalItems: roles.length,
    totalPages: 1,
  })),
}: {
  permissions?: readonly string[];
  roles?: RoleSummaryResponse[];
  createRole?: Mock<(payload: CreateRoleRequest) => Promise<RoleDetailResponse>>;
  deleteRole?: Mock<(id: string) => Promise<void>>;
  getRole?: Mock<(id: string) => Promise<RoleDetailResponse>>;
  listRoles?: Mock<(params?: ListRolesParams) => Promise<PageResponseRoleSummaryResponse>>;
} = {}) {
  return {
    createRole,
    deleteRole,
    listRoles,
    ...renderWithProviders(
      <>
        <SessionProvider
          loadSession={async () => ({
            ...TEST_ADMIN_SESSION,
            permissions: [...permissions],
          })}
        >
          <RbacPageView
            rolesTabProps={{
              listRoles,
              listPermissions: async () => PERMISSIONS_FIXTURE,
              deleteRole,
              roleDialogApi: {
                getRole,
                createRole,
              },
            }}
          />
        </SessionProvider>
        <Toaster
          containerAriaLabel={viMessages.common.notificationsLabel}
          closeButtonAriaLabel={viMessages.common.closeToast}
        />
      </>,
    ),
  };
}

describe("getAllowedRbacTabs", () => {
  it("tính đúng danh sách tab theo quyền hiệu lực", () => {
    expect(getAllowedRbacTabs(new Set(["ROLE.VIEW"]))).toEqual(["vai-tro"]);
    expect(getAllowedRbacTabs(new Set(["PERMISSION.VIEW"]))).toEqual(["quyen"]);
    expect(getAllowedRbacTabs(new Set(["PERMISSION.VIEW", "USER.VIEW"]))).toEqual([
      "quyen",
      "nguoi-dung",
    ]);
    expect(getAllowedRbacTabs(new Set(["USER.ASSIGN"]))).toEqual(["gan-hang-loat"]);
    expect(getAllowedRbacTabs(new Set(TEST_ADMIN_SESSION.permissions))).toEqual([
      "vai-tro",
      "quyen",
      "nguoi-dung",
      "gan-hang-loat",
    ]);
  });
});

describe("RbacPageView & RolesTab", () => {
  it("người chỉ có ROLE.VIEW chỉ thấy tab Vai trò và không có nút ghi", async () => {
    navMocks.search = "tab=quyen";
    renderRbacPage({ permissions: ["ROLE.VIEW"] });

    await screen.findByText("Theo dõi lớp");

    const tabs = screen.getAllByRole("tab");
    expect(tabs.map((el) => el.textContent)).toEqual(["Vai trò"]);
    expect(navMocks.replace).toHaveBeenCalledWith("/phan-quyen?tab=vai-tro");

    expect(screen.queryByRole("button", { name: "+ Tạo vai trò" })).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: /Sửa vai trò/ })).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: /Xóa vai trò/ })).not.toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Xem vai trò Theo dõi lớp" })).toBeInTheDocument();
  });

  it("người không có quyền nào trong phân quyền thấy trang 403", async () => {
    renderRbacPage({ permissions: ["CATALOG.VIEW"] });

    expect(
      await screen.findByRole("heading", { name: "Bạn không có quyền vào trang này" }),
    ).toBeInTheDocument();
  });

  it("hiện lỗi và cho thử lại khi tải danh sách vai trò thất bại", async () => {
    const listRoles = vi
      .fn()
      .mockRejectedValueOnce(new RbacApiError(500, "Server error", "INTERNAL_ERROR"))
      .mockResolvedValueOnce({
        items: ROLES_FIXTURE,
        page: 0,
        size: 20,
        totalItems: ROLES_FIXTURE.length,
        totalPages: 1,
      });

    renderRbacPage({ listRoles });

    const retry = await screen.findByRole("button", { name: "Thử lại" });
    await userEvent.click(retry);

    expect(await screen.findByText("Theo dõi lớp")).toBeInTheDocument();
  });

  it("tạo vai trò mới, chuyển sang Thi rồi quay lại Quản trị vẫn giữ quyền đã chọn", async () => {
    const user = userEvent.setup();
    const { createRole } = renderRbacPage();

    await screen.findByText("Theo dõi lớp");
    await user.click(screen.getByRole("button", { name: "+ Tạo vai trò" }));

    const dialog = await screen.findByRole("dialog");
    await user.type(within(dialog).getByLabelText(/Mã vai trò/), "CLASS_OBSERVER");
    await user.type(within(dialog).getByLabelText(/Tên vai trò/), "Giám sát lớp");
    await user.click(within(dialog).getByRole("checkbox", { name: "Cấp đối tượng Lớp học" }));

    await user.click(within(dialog).getByRole("button", { name: "Thi" }));
    expect(within(dialog).getByText(/Đang cấp 0 đối tượng/)).toBeInTheDocument();

    await user.click(within(dialog).getByRole("button", { name: "Quản trị" }));
    expect(within(dialog).getByText(/Đang cấp 1 đối tượng/)).toBeInTheDocument();

    await user.click(within(dialog).getByRole("button", { name: "Tạo vai trò" }));

    await waitFor(() => {
      expect(createRole).toHaveBeenCalledWith(
        expect.objectContaining({
          code: "CLASS_OBSERVER",
          name: "Giám sát lớp",
          access: "MANAGE",
          permissionCodes: ["CLASS.VIEW"],
        }),
      );
    });
  });

  it("hiển thị thông báo lỗi PRIVILEGE_ESCALATION bằng tiếng Việt trong dialog", async () => {
    const user = userEvent.setup();
    const createRole = vi.fn(async () => {
      throw new RbacApiError(403, "Forbidden", "PRIVILEGE_ESCALATION");
    });

    renderRbacPage({ createRole });

    await screen.findByText("Theo dõi lớp");
    await user.click(screen.getByRole("button", { name: "+ Tạo vai trò" }));

    const dialog = await screen.findByRole("dialog");
    await user.type(within(dialog).getByLabelText(/Mã vai trò/), "SUPER");
    await user.type(within(dialog).getByLabelText(/Tên vai trò/), "Siêu vai trò");
    await user.click(within(dialog).getByRole("button", { name: "Tạo vai trò" }));

    expect(
      await within(dialog).findByText(viMessages.rbac.errorCodes.PRIVILEGE_ESCALATION),
    ).toBeInTheDocument();
  });

  it("khóa xóa vai trò hệ thống hoặc đang có tài khoản giữ; cho phép xóa vai trò tự tạo không có user", async () => {
    const user = userEvent.setup();
    const { deleteRole } = renderRbacPage();

    await screen.findByText("Vai trò tạm");

    expect(screen.getByRole("button", { name: "Xóa vai trò Quản trị hệ thống" })).toHaveAttribute(
      "aria-disabled",
      "true",
    );
    expect(screen.getByRole("button", { name: "Xóa vai trò Theo dõi lớp" })).toHaveAttribute(
      "aria-disabled",
      "true",
    );

    await user.click(screen.getByRole("button", { name: "Xóa vai trò Vai trò tạm" }));
    const confirmDialog = await screen.findByRole("dialog");
    await user.click(within(confirmDialog).getByRole("button", { name: "Xóa vai trò" }));

    await waitFor(() => {
      expect(deleteRole).toHaveBeenCalledWith("r-custom-unused");
    });
  });
});
