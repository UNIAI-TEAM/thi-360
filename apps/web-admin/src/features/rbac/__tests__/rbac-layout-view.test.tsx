import { act, fireEvent, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { Toaster } from "@thi360/ui";
import { usePathname } from "next/navigation";
import { StrictMode, useSyncExternalStore, type ComponentProps } from "react";
import { afterEach, beforeEach, describe, expect, it, vi, type Mock } from "vitest";
import { SessionProvider } from "@/features/session/session-provider";
import viMessages from "@/messages/vi.json";
import { TEST_ADMIN_SESSION } from "@/test/fixtures";
import { renderWithProviders } from "@/test/render";
import { RbacApiError, type ListPermissionUsersParams, type ListRolesParams } from "../api";
import { RbacLayoutView } from "../components/rbac-layout-view";
import {
  RbacBulkAssignRoute,
  RbacPermissionsRoute,
  RbacRolesRoute,
  RbacUsersRoute,
  type RbacBulkAssignRouteProps,
  type RbacPermissionsRouteProps,
  type RbacRolesRouteProps,
  type RbacUsersRouteProps,
} from "../components/rbac-routes";
import {
  RBAC_TAB_ORDER,
  getAllowedRbacTabs,
  rbacNavigationHref,
  rbacTabFromPathname,
  rbacTabHref,
  resolveActiveRbacTab,
} from "../rbac-tabs";
import type {
  CreateRoleRequest,
  PageResponsePermissionUserResponse,
  PageResponseRoleSummaryResponse,
  PermissionResponse,
  RoleDetailResponse,
  RoleSummaryResponse,
  UpdateRoleRequest,
} from "../types";

// Bộ định tuyến giả: giữ đường dẫn hiện tại, `push`/`replace` và `history.replaceState` đều đổi nó.
const navMocks = vi.hoisted(() => {
  const listeners = new Set<() => void>();
  const location = { pathname: "/rbac/roles", search: "" };
  function setLocation(url: string) {
    const parsed = new URL(url, "http://localhost");
    location.pathname = parsed.pathname;
    location.search = parsed.search.replace(/^\?/, "");
    for (const listener of listeners) listener();
  }
  return {
    location,
    setLocation,
    currentUrl: () => location.pathname + (location.search ? `?${location.search}` : ""),
    subscribe(listener: () => void) {
      listeners.add(listener);
      return () => {
        listeners.delete(listener);
      };
    },
    router: {
      push: vi.fn<(href: string) => void>(),
      replace: vi.fn<(href: string) => void>(),
    },
  };
});

vi.mock("next/navigation", () => ({
  useRouter: () => navMocks.router,
  usePathname: () =>
    useSyncExternalStore(
      navMocks.subscribe,
      () => navMocks.location.pathname,
      () => navMocks.location.pathname,
    ),
  useSearchParams: () => {
    const search = useSyncExternalStore(
      navMocks.subscribe,
      () => navMocks.location.search,
      () => navMocks.location.search,
    );
    return new URLSearchParams(search);
  },
}));

vi.mock("next/link", () => ({
  default: ({ href, children, ...props }: ComponentProps<"a"> & { href: string }) => (
    <a
      {...props}
      href={href}
      onClick={(event) => {
        event.preventDefault();
        navMocks.router.push(href);
      }}
    >
      {children}
    </a>
  ),
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

const EMPTY_PAGE = { items: [], page: 0, size: 20, totalItems: 0, totalPages: 0 };

const PERMISSION_USERS_FIXTURE: PageResponsePermissionUserResponse = {
  items: [
    {
      userId: "u-watch",
      code: "U000009",
      username: "class.watcher",
      fullName: "Người theo dõi lớp",
      status: "ACTIVE",
      sources: [
        { type: "ROLE", roleId: "r-custom-in-use", roleCode: "CLASS_WATCHER", roleName: "Theo dõi lớp" },
      ],
    },
  ],
  page: 0,
  size: 20,
  totalItems: 1,
  totalPages: 1,
};

beforeEach(() => {
  navMocks.router.push.mockReset();
  navMocks.router.push.mockImplementation(navMocks.setLocation);
  navMocks.router.replace.mockReset();
  navMocks.router.replace.mockImplementation(navMocks.setLocation);
  navMocks.setLocation("/rbac/roles");
  vi.spyOn(window.history, "replaceState").mockImplementation((_data, _unused, url) => {
    if (typeof url === "string") navMocks.setLocation(url);
  });
});

afterEach(() => {
  vi.restoreAllMocks();
  vi.unstubAllGlobals();
});

interface RbacScreenProps {
  rolesTabProps?: RbacRolesRouteProps;
  permissionsTabProps?: RbacPermissionsRouteProps;
  usersTabProps?: RbacUsersRouteProps;
  bulkAssignTabProps?: RbacBulkAssignRouteProps;
}

/** Thay cho App Router: chọn trang con theo đường dẫn hiện tại, như các `page.tsx` dưới `app/(shell)/rbac`. */
function RbacRoutes({
  rolesTabProps,
  permissionsTabProps,
  usersTabProps,
  bulkAssignTabProps,
}: RbacScreenProps) {
  const pathname = usePathname();
  if (pathname === "/rbac/roles") return <RbacRolesRoute {...rolesTabProps} />;
  if (pathname === "/rbac/permissions") return <RbacPermissionsRoute {...permissionsTabProps} />;
  if (pathname === "/rbac/users") return <RbacUsersRoute {...usersTabProps} />;
  if (pathname === "/rbac/bulk-assign") return <RbacBulkAssignRoute {...bulkAssignTabProps} />;
  return null;
}

function RbacScreen(props: RbacScreenProps) {
  return (
    <RbacLayoutView>
      <RbacRoutes {...props} />
    </RbacLayoutView>
  );
}

function tabNav() {
  return screen.getByRole("navigation", { name: viMessages.rbac.tabs.ariaLabel });
}

function tabLinkHrefs() {
  return within(tabNav())
    .getAllByRole("link")
    .map((link) => [link.textContent, link.getAttribute("href")]);
}

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
  listUsersByPermission = vi.fn(async () => PERMISSION_USERS_FIXTURE),
  updateRole,
  updateRolePermissions,
}: {
  permissions?: readonly string[];
  roles?: RoleSummaryResponse[];
  createRole?: Mock<(payload: CreateRoleRequest) => Promise<RoleDetailResponse>>;
  deleteRole?: Mock<(id: string) => Promise<void>>;
  getRole?: Mock<(id: string) => Promise<RoleDetailResponse>>;
  listRoles?: Mock<(params?: ListRolesParams) => Promise<PageResponseRoleSummaryResponse>>;
  listPermissions?: Mock<() => Promise<PermissionResponse[]>>;
  listUsersByPermission?: Mock<
    (id: string, params?: ListPermissionUsersParams) => Promise<PageResponsePermissionUserResponse>
  >;
  updateRole?: Mock<(id: string, payload: UpdateRoleRequest) => Promise<RoleDetailResponse>>;
  updateRolePermissions?: Mock<() => Promise<RoleDetailResponse>>;
} = {}) {
  return {
    createRole,
    deleteRole,
    listRoles,
    listPermissions,
    listUsersByPermission,
    ...renderWithProviders(
      <>
        <SessionProvider
          loadSession={async () => ({
            ...TEST_ADMIN_SESSION,
            permissions: [...permissions],
          })}
        >
          <RbacScreen
            rolesTabProps={{
              listRoles,
              listPermissions,
              deleteRole,
              roleDialogApi: {
                getRole,
                createRole,
                ...(updateRole ? { updateRole } : {}),
                ...(updateRolePermissions ? { updateRolePermissions } : {}),
              },
            }}
            permissionsTabProps={{
              listPermissions,
            }}
            usersTabProps={{
              listPermissions,
              listUsersByPermission,
            }}
            bulkAssignTabProps={{
              listUsers: async () => EMPTY_PAGE,
              listRoles,
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
    expect(getAllowedRbacTabs(new Set(["ROLE.VIEW"]))).toEqual(["roles"]);
    expect(getAllowedRbacTabs(new Set(["PERMISSION.VIEW"]))).toEqual(["permissions"]);
    expect(getAllowedRbacTabs(new Set(["PERMISSION.VIEW", "USER.VIEW"]))).toEqual([
      "permissions",
      "users",
    ]);
    expect(getAllowedRbacTabs(new Set(["USER.ASSIGN"]))).toEqual(["bulk-assign"]);
    expect(getAllowedRbacTabs(new Set(TEST_ADMIN_SESSION.permissions))).toEqual([
      "roles",
      "permissions",
      "users",
      "bulk-assign",
    ]);
    expect(getAllowedRbacTabs(new Set(TEST_ADMIN_SESSION.permissions))).toEqual(RBAC_TAB_ORDER);
  });
});

describe("đường dẫn các trang con Phân quyền", () => {
  it("đọc khóa tab từ đường dẫn; /rbac không có tab", () => {
    expect(rbacTabFromPathname("/rbac")).toBeNull();
    expect(rbacTabFromPathname("/rbac/")).toBeNull();
    expect(rbacTabFromPathname("/rbac/roles")).toBe("roles");
    expect(rbacTabFromPathname("/rbac/bulk-assign")).toBe("bulk-assign");
    expect(rbacTabFromPathname("/rbac-x/roles")).toBeNull();
  });

  it("tab không được phép hoặc không có thì lấy tab đầu tiên được phép; không được tab nào thì null", () => {
    expect(resolveActiveRbacTab("users", ["permissions", "users"])).toBe("users");
    expect(resolveActiveRbacTab("roles", ["permissions", "users"])).toBe("permissions");
    expect(resolveActiveRbacTab(null, ["roles", "permissions"])).toBe("roles");
    expect(resolveActiveRbacTab("roles", [])).toBeNull();
  });

  it("chỉ Người dùng theo quyền và Gán hàng loạt nhận ?permission", () => {
    expect(rbacTabHref("roles")).toBe("/rbac/roles");
    expect(rbacTabHref("roles", "CLASS.VIEW")).toBe("/rbac/roles");
    expect(rbacTabHref("permissions", "CLASS.VIEW")).toBe("/rbac/permissions");
    expect(rbacTabHref("users", "CLASS.VIEW")).toBe("/rbac/users?permission=CLASS.VIEW");
    expect(rbacTabHref("bulk-assign", "CLASS.VIEW")).toBe("/rbac/bulk-assign?permission=CLASS.VIEW");
    expect(rbacTabHref("users", null)).toBe("/rbac/users");
  });

  it("chuyển tab: mã quyền truyền vào luôn được mang theo; mã đang chọn chỉ theo sang Gán hàng loạt khi đi từ Người dùng theo quyền", () => {
    const fromUsers = { tab: "users", permission: "CLASS.VIEW" } as const;
    const fromPermissions = { tab: "permissions", permission: "CLASS.VIEW" } as const;
    const fromBulkAssign = { tab: "bulk-assign", permission: "CLASS.VIEW" } as const;

    expect(rbacNavigationHref("users", { tab: "permissions", permission: null }, "AUDIT.VIEW")).toBe(
      "/rbac/users?permission=AUDIT.VIEW",
    );
    expect(rbacNavigationHref("bulk-assign", fromUsers, "AUDIT.VIEW")).toBe(
      "/rbac/bulk-assign?permission=AUDIT.VIEW",
    );

    expect(rbacNavigationHref("bulk-assign", fromUsers)).toBe("/rbac/bulk-assign?permission=CLASS.VIEW");
    expect(rbacNavigationHref("users", fromUsers)).toBe("/rbac/users?permission=CLASS.VIEW");
    expect(rbacNavigationHref("roles", fromUsers)).toBe("/rbac/roles");
    expect(rbacNavigationHref("permissions", fromUsers)).toBe("/rbac/permissions");

    expect(rbacNavigationHref("bulk-assign", fromPermissions)).toBe("/rbac/bulk-assign");
    expect(rbacNavigationHref("users", fromPermissions)).toBe("/rbac/users?permission=CLASS.VIEW");

    expect(rbacNavigationHref("users", fromBulkAssign)).toBe("/rbac/users?permission=CLASS.VIEW");
    expect(rbacNavigationHref("bulk-assign", fromBulkAssign)).toBe(
      "/rbac/bulk-assign?permission=CLASS.VIEW",
    );
  });
});

describe("RbacLayoutView & RolesTab", () => {
  it("/rbac chuyển sang trang con đầu tiên được phép", async () => {
    navMocks.setLocation("/rbac");
    renderRbacPage();

    await screen.findByText("Theo dõi lớp");
    expect(navMocks.router.replace).toHaveBeenCalledWith("/rbac/roles");
    expect(navMocks.router.push).not.toHaveBeenCalled();
    expect(navMocks.currentUrl()).toBe("/rbac/roles");
  });

  it("/rbac của người không có ROLE.VIEW chuyển sang /rbac/permissions và bỏ ?permission", async () => {
    navMocks.setLocation("/rbac?permission=CLASS.VIEW");
    renderRbacPage({ permissions: ["PERMISSION.VIEW", "USER.VIEW"] });

    expect(await screen.findByText(viMessages.rbac.permissionsTab.bannerNote)).toBeInTheDocument();
    expect(navMocks.router.replace).toHaveBeenCalledWith("/rbac/permissions");
    expect(tabLinkHrefs().map(([label]) => label)).toEqual(["Quyền", "Người dùng theo quyền"]);
  });

  it("người chỉ có ROLE.VIEW mở /rbac/permissions chuyển về /rbac/roles, không render tab cấm và không có nút ghi", async () => {
    navMocks.setLocation("/rbac/permissions");
    const listPermissions = vi.fn(async () => PERMISSIONS_FIXTURE);
    renderRbacPage({ permissions: ["ROLE.VIEW"], listPermissions });
    await screen.findByText("Theo dõi lớp");

    expect(tabLinkHrefs()).toEqual([["Vai trò", "/rbac/roles"]]);
    expect(navMocks.router.replace).toHaveBeenCalledWith("/rbac/roles");
    expect(navMocks.currentUrl()).toBe("/rbac/roles");
    expect(listPermissions).not.toHaveBeenCalled();
    expect(
      screen.queryByText(viMessages.rbac.permissionsTab.bannerNote),
    ).not.toBeInTheDocument();

    expect(screen.queryByRole("button", { name: "+ Tạo vai trò" })).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: /Sửa vai trò/ })).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: /Xóa vai trò/ })).not.toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Xem vai trò Theo dõi lớp" })).toBeInTheDocument();
  });

  it("người chỉ có USER.ASSIGN mở /rbac/users?permission chuyển sang /rbac/bulk-assign và giữ mã quyền", async () => {
    navMocks.setLocation("/rbac/users?permission=CLASS.VIEW");
    const { listUsersByPermission } = renderRbacPage({ permissions: ["USER.ASSIGN"] });

    expect(await screen.findByRole("heading", { name: /1\. Chọn tài khoản/ })).toBeInTheDocument();
    expect(navMocks.router.replace).toHaveBeenCalledWith("/rbac/bulk-assign?permission=CLASS.VIEW");
    expect(tabLinkHrefs()).toEqual([["Gán hàng loạt", "/rbac/bulk-assign?permission=CLASS.VIEW"]]);
    expect(listUsersByPermission).not.toHaveBeenCalled();
  });

  it("thanh tab là nhóm điều hướng gồm các liên kết của tab được phép, mục đang mở mang aria-current=page", async () => {
    renderRbacPage();
    await screen.findByText("Theo dõi lớp");

    expect(tabLinkHrefs()).toEqual([
      ["Vai trò", "/rbac/roles"],
      ["Quyền", "/rbac/permissions"],
      ["Người dùng theo quyền", "/rbac/users"],
      ["Gán hàng loạt", "/rbac/bulk-assign"],
    ]);
    const nav = tabNav();
    expect(within(nav).getByRole("link", { name: "Vai trò" })).toHaveAttribute("aria-current", "page");
    for (const name of ["Quyền", "Người dùng theo quyền", "Gán hàng loạt"]) {
      expect(within(nav).getByRole("link", { name })).not.toHaveAttribute("aria-current");
    }
    expect(screen.queryByRole("tablist")).not.toBeInTheDocument();
    expect(screen.queryByRole("tab")).not.toBeInTheDocument();
    expect(navMocks.router.replace).not.toHaveBeenCalled();
  });

  it("đổi trang con: tiêu đề và thanh tab không dựng lại, tiêu điểm ở lại liên kết vừa bấm, nút Tạo vai trò chỉ có ở trang Vai trò", async () => {
    const user = userEvent.setup();
    renderRbacPage();
    await screen.findByText("Theo dõi lớp");

    const heading = screen.getByRole("heading", { level: 1, name: "Phân quyền" });
    const nav = tabNav();
    const permissionsLink = within(nav).getByRole("link", { name: "Quyền" });
    expect(screen.getByText(viMessages.rbac.tabsDescription.roles)).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "+ Tạo vai trò" })).toBeInTheDocument();

    await user.click(permissionsLink);

    expect(await screen.findByText(viMessages.rbac.permissionsTab.bannerNote)).toBeInTheDocument();
    expect(navMocks.currentUrl()).toBe("/rbac/permissions");
    expect(screen.getByRole("heading", { level: 1, name: "Phân quyền" })).toBe(heading);
    expect(tabNav()).toBe(nav);
    expect(permissionsLink).toHaveFocus();
    expect(permissionsLink).toHaveAttribute("aria-current", "page");
    expect(within(nav).getByRole("link", { name: "Vai trò" })).not.toHaveAttribute("aria-current");
    expect(screen.getByText(viMessages.rbac.tabsDescription.permissions)).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "+ Tạo vai trò" })).not.toBeInTheDocument();
    expect(screen.queryByText("Theo dõi lớp")).not.toBeInTheDocument();
  });

  it("từ một quyền sang Người dùng theo quyền mang ?permission; đổi quyền tại chỗ; sang Gán hàng loạt giữ mã", async () => {
    const user = userEvent.setup();
    navMocks.setLocation("/rbac/permissions");
    const { listUsersByPermission } = renderRbacPage();

    await user.click(
      await screen.findByRole("button", { name: "Xem tài khoản đang có quyền Xem lớp học" }),
    );
    expect(navMocks.router.push).toHaveBeenLastCalledWith("/rbac/users?permission=CLASS.VIEW");
    expect(await screen.findByText("class.watcher")).toBeInTheDocument();
    expect(listUsersByPermission).toHaveBeenCalledWith("p-class-view", expect.anything());
    expect(tabLinkHrefs()).toEqual([
      ["Vai trò", "/rbac/roles"],
      ["Quyền", "/rbac/permissions"],
      ["Người dùng theo quyền", "/rbac/users?permission=CLASS.VIEW"],
      ["Gán hàng loạt", "/rbac/bulk-assign?permission=CLASS.VIEW"],
    ]);

    // Chọn quyền khác trong cùng trang: chỉ đổi ?permission tại chỗ, không điều hướng.
    navMocks.router.push.mockClear();
    await user.click(screen.getByRole("button", { name: /AUDIT\.VIEW/ }));
    expect(navMocks.currentUrl()).toBe("/rbac/users?permission=AUDIT.VIEW");
    expect(window.history.replaceState).toHaveBeenLastCalledWith(
      null,
      "",
      "/rbac/users?permission=AUDIT.VIEW",
    );
    expect(navMocks.router.push).not.toHaveBeenCalled();
    await waitFor(() => {
      expect(listUsersByPermission).toHaveBeenCalledWith("p-audit-view", expect.anything());
    });

    await user.click(screen.getByRole("button", { name: viMessages.rbac.permissionUsers.assignMany }));
    expect(navMocks.router.push).toHaveBeenLastCalledWith("/rbac/bulk-assign?permission=AUDIT.VIEW");
    expect(await screen.findByRole("heading", { name: /1\. Chọn tài khoản/ })).toBeInTheDocument();
    expect(tabLinkHrefs()).toEqual([
      ["Vai trò", "/rbac/roles"],
      ["Quyền", "/rbac/permissions"],
      ["Người dùng theo quyền", "/rbac/users?permission=AUDIT.VIEW"],
      ["Gán hàng loạt", "/rbac/bulk-assign?permission=AUDIT.VIEW"],
    ]);
  });

  it("hiệu ứng vào chỉ ở vùng nội dung: chạy lại khi đổi trang con, không chạy lại khi chỉ đổi ?permission, tắt khi giảm chuyển động", async () => {
    const user = userEvent.setup();
    navMocks.setLocation("/rbac/users?permission=CLASS.VIEW");
    renderRbacPage();

    const usersContent = (await screen.findByText("class.watcher")).closest(".animate-route-in");
    expect(usersContent).toHaveClass("animate-route-in", "motion-reduce:animate-none");
    expect(usersContent).not.toContainElement(tabNav());
    expect(usersContent).not.toContainElement(screen.getByRole("heading", { level: 1 }));

    await user.click(screen.getByRole("button", { name: /AUDIT\.VIEW/ }));
    expect(navMocks.currentUrl()).toBe("/rbac/users?permission=AUDIT.VIEW");
    expect(screen.getByRole("button", { name: /AUDIT\.VIEW/ }).closest(".animate-route-in")).toBe(
      usersContent,
    );

    await user.click(within(tabNav()).getByRole("link", { name: "Vai trò" }));
    const rolesContent = (await screen.findByText("Theo dõi lớp")).closest(".animate-route-in");
    expect(rolesContent).toHaveClass("animate-route-in", "motion-reduce:animate-none");
    expect(rolesContent).not.toBe(usersContent);
    expect(usersContent).not.toBeInTheDocument();
  });

  it("Xem vai trò từ Người dùng theo quyền sang /rbac/roles và bỏ ?permission", async () => {
    const user = userEvent.setup();
    navMocks.setLocation("/rbac/users?permission=CLASS.VIEW");
    renderRbacPage();

    await user.click(
      await screen.findByRole("button", { name: "Xem vai trò của class.watcher" }),
    );
    expect(navMocks.router.push).toHaveBeenLastCalledWith("/rbac/roles");
    expect(await screen.findByText("Theo dõi lớp")).toBeInTheDocument();
    expect(navMocks.currentUrl()).toBe("/rbac/roles");
  });

  it("hiển thị trạng thái lỗi và nút thử lại khi tải phiên thất bại", async () => {
    const loadSession = vi
      .fn()
      .mockRejectedValueOnce(new Error("network"))
      .mockResolvedValueOnce({ ...TEST_ADMIN_SESSION });

    renderWithProviders(
      <SessionProvider loadSession={loadSession}>
        <RbacScreen
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

    await user.click(within(tabNav()).getByRole("link", { name: "Quyền" }));
    expect(await screen.findByText(viMessages.rbac.permissionsTab.bannerNote)).toBeInTheDocument();

    await user.click(within(tabNav()).getByRole("link", { name: "Vai trò" }));
    await screen.findByText("Theo dõi lớp");
    expect(screen.queryByRole("dialog")).not.toBeInTheDocument();

    // Bấm "+ Tạo vai trò" lại vẫn mở được dialog mới bình thường
    await user.click(screen.getByRole("button", { name: "+ Tạo vai trò" }));
    expect(await screen.findByRole("dialog")).toBeInTheDocument();
  });

  it("đang mở dialog Tạo vai trò mà rời trang (nút Back) rồi quay lại Vai trò thì dialog không tự mở lại", async () => {
    const user = userEvent.setup();
    renderRbacPage();

    await screen.findByText("Theo dõi lớp");
    await user.click(screen.getByRole("button", { name: "+ Tạo vai trò" }));
    expect(await screen.findByRole("dialog")).toBeInTheDocument();

    act(() => navMocks.setLocation("/rbac/permissions"));
    expect(await screen.findByText(viMessages.rbac.permissionsTab.bannerNote)).toBeInTheDocument();
    await waitFor(() => {
      expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
    });

    act(() => navMocks.setLocation("/rbac/roles"));
    await screen.findByText("Theo dõi lớp");
    expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
  });

  it("StrictMode: bấm + Tạo vai trò mở dialog không bị treo", async () => {
    const user = userEvent.setup();
    renderWithProviders(
      <StrictMode>
        <SessionProvider loadSession={async () => TEST_ADMIN_SESSION}>
          <RbacScreen
            rolesTabProps={{
              listRoles: async () => ({ items: ROLES_FIXTURE, page: 0, size: 20, totalItems: 3, totalPages: 1 }),
              listPermissions: async () => PERMISSIONS_FIXTURE,
            }}
          />
        </SessionProvider>
      </StrictMode>,
    );
    await screen.findByText("Theo dõi lớp");
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

  it("lưu dialog Tạo vai trò khi đang chọn Thi gửi access EXAM và không kèm quyền nào (R3-2)", async () => {
    const user = userEvent.setup();
    const { createRole } = renderRbacPage();

    await screen.findByText("Theo dõi lớp");
    await user.click(screen.getByRole("button", { name: "+ Tạo vai trò" }));

    const dialog = await screen.findByRole("dialog");
    fireEvent.change(within(dialog).getByLabelText(/Mã vai trò/), {
      target: { value: "EXAM_PROCTOR" },
    });
    fireEvent.change(within(dialog).getByLabelText(/Tên vai trò/), {
      target: { value: "Giám thị" },
    });
    await user.click(within(dialog).getByRole("checkbox", { name: "Cấp đối tượng Lớp học" }));
    await user.click(within(dialog).getByRole("button", { name: "Thi" }));
    await user.click(within(dialog).getByRole("button", { name: "Tạo vai trò" }));

    await waitFor(() => {
      expect(createRole).toHaveBeenCalledTimes(1);
    });
    expect(createRole).toHaveBeenCalledWith(
      expect.objectContaining({ code: "EXAM_PROCTOR", access: "EXAM", permissionCodes: [] }),
    );
  }, 15_000);

  it("sửa vai trò tự tạo đổi phạm vi và trạng thái gửi đúng giá trị mới (R3-2)", async () => {
    const user = userEvent.setup();
    const customRole = ROLES_FIXTURE.find((r) => r.id === "r-custom-unused")!;
    const getRole = vi.fn(
      async (): Promise<RoleDetailResponse> => ({
        ...customRole,
        description: undefined,
        access: "EXAM",
        status: "INACTIVE",
        permissionCodes: [],
        version: 3,
      }),
    );
    const updateRole = vi.fn(
      async (id: string, payload: UpdateRoleRequest): Promise<RoleDetailResponse> => ({
        ...customRole,
        id,
        access: payload.access ?? "EXAM",
        status: payload.status ?? "INACTIVE",
        permissionCodes: [],
        version: payload.version + 1,
      }),
    );
    const updateRolePermissions = vi.fn(
      async (): Promise<RoleDetailResponse> => ({ ...customRole, permissionCodes: [], version: 5 }),
    );
    renderRbacPage({ getRole, updateRole, updateRolePermissions });

    await screen.findByText("Vai trò tạm");
    await user.click(screen.getByRole("button", { name: "Sửa vai trò Vai trò tạm" }));

    const dialog = await screen.findByRole("dialog");
    const submit = await within(dialog).findByRole("button", { name: "Lưu vai trò" });
    await user.click(within(dialog).getByRole("button", { name: "Quản trị" }));
    await user.click(within(dialog).getByRole("combobox", { name: "Trạng thái" }));
    await user.click(await screen.findByRole("option", { name: "Hoạt động" }));
    await user.click(submit);

    await waitFor(() => {
      expect(updateRole).toHaveBeenCalledTimes(1);
    });
    expect(updateRole).toHaveBeenCalledWith("r-custom-unused", {
      access: "MANAGE",
      status: "ACTIVE",
      version: 3,
    });
    expect(updateRolePermissions).not.toHaveBeenCalled();
  }, 15_000);
});
