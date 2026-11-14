import { fireEvent, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { Toaster } from "@thi360/ui";
import { useSyncExternalStore } from "react";
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

const navMocks = vi.hoisted(() => {
  const listeners = new Set<() => void>();
  return {
    replace: vi.fn(),
    pathname: "/phan-quyen",
    search: "",
    listeners,
    setSearch(nextSearch: string) {
      this.search = nextSearch;
      for (const listener of this.listeners) listener();
    },
    subscribe(listener: () => void) {
      listeners.add(listener);
      return () => {
        listeners.delete(listener);
      };
    },
  };
});

vi.mock("next/navigation", () => ({
  useRouter: () => ({ replace: navMocks.replace }),
  usePathname: () => navMocks.pathname,
  useSearchParams: () => {
    const search = useSyncExternalStore(
      navMocks.subscribe,
      () => navMocks.search,
      () => navMocks.search,
    );
    return new URLSearchParams(search);
  },
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
  navMocks.setSearch("");
  window.history.replaceState(null, "", "/phan-quyen");
  const originalReplaceState = window.history.replaceState.bind(window.history);
  vi.spyOn(window.history, "replaceState").mockImplementation((data, unused, url) => {
    originalReplaceState(data, unused, url);
    if (typeof url === "string") {
      const parsed = new URL(url, window.location.origin);
      navMocks.pathname = parsed.pathname;
      navMocks.setSearch(parsed.search.replace(/^\?/, ""));
    }
  });
});

afterEach(() => {
  vi.restoreAllMocks();
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
  listPermissions = vi.fn(async () => PERMISSIONS_FIXTURE),
}: {
  permissions?: readonly string[];
  roles?: RoleSummaryResponse[];
  createRole?: Mock<(payload: CreateRoleRequest) => Promise<RoleDetailResponse>>;
  deleteRole?: Mock<(id: string) => Promise<void>>;
  getRole?: Mock<(id: string) => Promise<RoleDetailResponse>>;
  listRoles?: Mock<(params?: ListRolesParams) => Promise<PageResponseRoleSummaryResponse>>;
  listPermissions?: Mock<() => Promise<PermissionResponse[]>>;
} = {}) {
  return {
    createRole,
    deleteRole,
    listRoles,
    listPermissions,
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
              listPermissions,
              deleteRole,
              roleDialogApi: {
                getRole,
                createRole,
              },
            }}
            permissionsTabProps={{
              listPermissions,
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
  it("chuẩn hóa URL thiếu ?tab sang tab đầu tiên được phép", async () => {
    navMocks.setSearch("");
    renderRbacPage();

    await screen.findByText("Theo dõi lớp");
    await waitFor(() => {
      expect(window.location.pathname + window.location.search).toBe("/phan-quyen?tab=vai-tro");
    });
  });

  it("người chỉ có ROLE.VIEW mở ?tab=quyen chuyển về ?tab=vai-tro, không render tab cấm và không có nút ghi", async () => {
    navMocks.setSearch("tab=quyen");
    window.history.replaceState(null, "", "/phan-quyen?tab=quyen");
    const listPermissions = vi.fn(async () => PERMISSIONS_FIXTURE);
    renderRbacPage({ permissions: ["ROLE.VIEW"], listPermissions });
    await screen.findByText("Theo dõi lớp");

    const tabs = screen.getAllByRole("tab");
    expect(tabs.map((el) => el.textContent)).toEqual(["Vai trò"]);
    await waitFor(() => {
      expect(window.location.pathname + window.location.search).toBe("/phan-quyen?tab=vai-tro");
    });
    expect(listPermissions).not.toHaveBeenCalled();
    expect(
      screen.queryByText(viMessages.rbac.permissionsTab.bannerNote),
    ).not.toBeInTheDocument();

    expect(screen.queryByRole("button", { name: "+ Tạo vai trò" })).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: /Sửa vai trò/ })).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: /Xóa vai trò/ })).not.toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Xem vai trò Theo dõi lớp" })).toBeInTheDocument();
  });

  it("hiển thị trạng thái lỗi và nút thử lại khi tải phiên thất bại", async () => {
    const loadSession = vi
      .fn()
      .mockRejectedValueOnce(new Error("network"))
      .mockResolvedValueOnce({ ...TEST_ADMIN_SESSION });

    renderWithProviders(
      <SessionProvider loadSession={loadSession}>
        <RbacPageView
          rolesTabProps={{
            listRoles: async () => ({
              items: ROLES_FIXTURE,
              page: 0,
              size: 20,
              totalItems: ROLES_FIXTURE.length,
              totalPages: 1,
            }),
            listPermissions: async () => PERMISSIONS_FIXTURE,
          }}
        />
      </SessionProvider>,
    );

    const retry = await screen.findByRole("button", { name: "Thử lại" });
    await userEvent.click(retry);

    expect(await screen.findByText("Theo dõi lớp")).toBeInTheDocument();
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
  }, 15_000);

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

  it("đóng dialog Tạo vai trò rồi chuyển sang tab Quyền và quay lại Vai trò không tự mở lại dialog", async () => {
    const user = userEvent.setup();
    renderRbacPage();

    await screen.findByText("Theo dõi lớp");
    await user.click(screen.getByRole("button", { name: "+ Tạo vai trò" }));
    const dialog = await screen.findByRole("dialog");
    await user.click(within(dialog).getByRole("button", { name: "Hủy" }));
    await waitFor(() => {
      expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
    });

    await user.click(screen.getByRole("tab", { name: "Quyền" }));
    expect(await screen.findByText(viMessages.rbac.permissionsTab.bannerNote)).toBeInTheDocument();

    await user.click(screen.getByRole("tab", { name: "Vai trò" }));
    await screen.findByText("Theo dõi lớp");
    expect(screen.queryByRole("dialog")).not.toBeInTheDocument();

    // Bấm "+ Tạo vai trò" lại vẫn mở được dialog mới bình thường
    await user.click(screen.getByRole("button", { name: "+ Tạo vai trò" }));
    expect(await screen.findByRole("dialog")).toBeInTheDocument();
  });

  it("bấm Tạo vai trò, đóng dialog rồi bấm Tạo vai trò lại không đổi tab vẫn mở được dialog (R2-1)", async () => {
    const user = userEvent.setup();
    renderRbacPage();

    await screen.findByText("Theo dõi lớp");
    await user.click(screen.getByRole("button", { name: "+ Tạo vai trò" }));
    const firstDialog = await screen.findByRole("dialog");
    await user.click(within(firstDialog).getByRole("button", { name: "Hủy" }));
    await waitFor(() => {
      expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
    });

    await user.click(screen.getByRole("button", { name: "+ Tạo vai trò" }));
    expect(await screen.findByRole("dialog")).toBeInTheDocument();
  });

  it("hiển thị lỗi kiểm tra độ dài mã, tên và mô tả bằng tiếng Việt đã bản địa hóa", async () => {
    const user = userEvent.setup();
    const { createRole } = renderRbacPage();

    await screen.findByText("Theo dõi lớp");
    await user.click(screen.getByRole("button", { name: "+ Tạo vai trò" }));

    const dialog = await screen.findByRole("dialog");
    fireEvent.change(within(dialog).getByLabelText(/Mã vai trò/), {
      target: { value: "A".repeat(65) },
    });
    fireEvent.change(within(dialog).getByLabelText(/Tên vai trò/), {
      target: { value: "B".repeat(121) },
    });
    fireEvent.change(within(dialog).getByLabelText(/Mô tả/), {
      target: { value: "C".repeat(501) },
    });
    await user.click(within(dialog).getByRole("button", { name: "Tạo vai trò" }));

    expect(await within(dialog).findByText(viMessages.rbac.roleDialog.codeMaxLength)).toBeInTheDocument();
    expect(within(dialog).getByText(viMessages.rbac.roleDialog.nameMaxLength)).toBeInTheDocument();
    expect(within(dialog).getByText(viMessages.rbac.roleDialog.descriptionMaxLength)).toBeInTheDocument();
    expect(within(dialog).queryByText(/Too big:/)).not.toBeInTheDocument();
    expect(createRole).not.toHaveBeenCalled();
  });
});
