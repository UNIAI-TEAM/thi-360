import { screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { Toaster } from "@thi360/ui";
import { afterEach, describe, expect, it, vi } from "vitest";
import { SessionProvider } from "@/features/session/session-provider";
import { createQueryClient } from "@/lib/api/query-client";
import { TEST_ADMIN_SESSION } from "@/test/fixtures";
import { renderWithProviders } from "@/test/render";
import { RbacApiError, rbacKeys } from "../api";
import { PermissionUsersTab } from "../components/permission-users-tab";
import { PermissionsTab } from "../components/permissions-tab";
import type {
  PageResponsePermissionUserResponse,
  PermissionResponse,
  UserDirectPermissionsResponse,
} from "../types";

const PERMISSIONS_FIXTURE: PermissionResponse[] = [
  {
    id: "perm-user-view",
    code: "USER.VIEW",
    bitIndex: 0,
    groupCode: "USER",
    groupName: "Tài khoản",
    name: "Xem",
    scope: "MANAGE",
    roleCount: 2,
    userCount: 1,
  },
  {
    id: "perm-user-assign",
    code: "USER.ASSIGN",
    bitIndex: 2,
    groupCode: "USER",
    groupName: "Tài khoản",
    name: "Gán vai trò và quyền",
    scope: "MANAGE",
    roleCount: 1,
    userCount: 0,
  },
  {
    id: "perm-catalog-view",
    code: "CATALOG.VIEW",
    bitIndex: 9,
    groupCode: "CATALOG",
    groupName: "Danh mục",
    name: "Xem",
    scope: "MANAGE",
    roleCount: 3,
    userCount: 2,
  },
];

const PERMISSION_USERS_FIXTURE: PageResponsePermissionUserResponse = {
  items: [
    {
      userId: "u-1",
      code: "U000001",
      username: "content.admin",
      fullName: "Quản trị nội dung",
      status: "ACTIVE",
      sources: [
        {
          type: "ROLE",
          roleId: "role-content",
          roleCode: "CONTENT_ADMIN",
          roleName: "Quản trị nội dung",
        },
      ],
    },
    {
      userId: "u-2",
      code: "U000002",
      username: "ops.admin",
      fullName: "Vận hành",
      status: "ACTIVE",
      sources: [
        {
          type: "DIRECT",
          grantedBy: "u-platform",
          grantedByUsername: "platform",
          grantedAt: "2026-11-12T08:00:00Z",
          reason: "Hỗ trợ rà soát danh mục",
        },
      ],
    },
  ],
  page: 0,
  size: 20,
  totalItems: 2,
  totalPages: 1,
};

describe("PermissionsTab & PermissionUsersTab", () => {
  afterEach(() => {
    vi.restoreAllMocks();
    vi.unstubAllGlobals();
  });

  it("PermissionsTab: hiển thị bảng gom theo đối tượng chỉ đọc và bấm số tài khoản sang tab Người dùng", async () => {
    const user = userEvent.setup();
    const onSelectPermissionUsers = vi.fn();

    renderWithProviders(
      <SessionProvider
        loadSession={async () => ({
          ...TEST_ADMIN_SESSION,
          permissions: ["PERMISSION.VIEW", "USER.VIEW"],
        })}
      >
        <PermissionsTab
          listPermissions={async () => PERMISSIONS_FIXTURE}
          onSelectPermissionUsers={onSelectPermissionUsers}
        />
      </SessionProvider>,
    );

    expect(await screen.findByText("CATALOG.VIEW")).toBeInTheDocument();
    expect(
      screen.getByText(/Danh sách quyền cố định theo phần mềm/),
    ).toBeInTheDocument();
    expect(screen.getByText("Quyền riêng: Gán vai trò và quyền")).toBeInTheDocument();
    expect(screen.getByText("3 quyền trên 2 đối tượng")).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: /\+ Tạo/ })).not.toBeInTheDocument();

    await user.click(
      screen.getByRole("button", { name: "Xem tài khoản đang có quyền Xem danh mục" }),
    );
    expect(onSelectPermissionUsers).toHaveBeenCalledWith("CATALOG.VIEW");
  });

  it("PermissionsTab: lọc theo từ khóa và đối tượng, hỗ trợ trạng thái lỗi và thử lại", async () => {
    const user = userEvent.setup();
    const listPermissions = vi
      .fn()
      .mockRejectedValueOnce(new RbacApiError(500, "Server error", "INTERNAL_ERROR"))
      .mockResolvedValueOnce(PERMISSIONS_FIXTURE);

    renderWithProviders(
      <SessionProvider
        loadSession={async () => ({
          ...TEST_ADMIN_SESSION,
          permissions: ["PERMISSION.VIEW"],
        })}
      >
        <PermissionsTab listPermissions={listPermissions} />
      </SessionProvider>,
    );

    await user.click(await screen.findByRole("button", { name: "Thử lại" }));
    expect(await screen.findByText("CATALOG.VIEW")).toBeInTheDocument();

    await user.type(screen.getByLabelText("Tìm kiếm"), "CATALOG");
    expect(screen.getByText("CATALOG.VIEW")).toBeInTheDocument();
    expect(screen.queryByText("USER.VIEW")).not.toBeInTheDocument();
    expect(screen.getByText("1 quyền trên 1 đối tượng")).toBeInTheDocument();
  });

  it("PermissionUsersTab: hiện nguồn Từ vai trò / Gán trực tiếp, xuất Excel và gỡ quyền trực tiếp qua ConfirmDialog", async () => {
    const user = userEvent.setup();
    const createObjectUrlSpy = vi.fn(() => "blob:mock-xlsx");
    const revokeObjectUrlSpy = vi.fn();
    vi.stubGlobal("URL", {
      ...URL,
      createObjectURL: createObjectUrlSpy,
      revokeObjectURL: revokeObjectUrlSpy,
    });
    const clickSpy = vi
      .spyOn(HTMLAnchorElement.prototype, "click")
      .mockImplementation(() => {});

    const exportUsersByPermission = vi.fn(async () => ({
      blob: new Blob(["xlsx-bytes"]),
      fileName: "permission-catalog-view-users.xlsx",
    }));
    const getUserPermissions = vi.fn(
      async (): Promise<UserDirectPermissionsResponse> => ({
        userId: "u-2",
        hasManageRole: true,
        permissions: [
          {
            permissionId: "perm-catalog-view",
            permissionCode: "CATALOG.VIEW",
            permissionName: "Xem",
            groupCode: "CATALOG",
            groupName: "Danh mục",
            effective: true,
            grantedAt: "2026-11-12T08:00:00Z",
            grantedByUsername: "platform",
            reason: "Hỗ trợ rà soát danh mục",
          },
        ],
      }),
    );
    const updateUserPermissions = vi.fn(
      async (): Promise<UserDirectPermissionsResponse> => ({
        userId: "u-2",
        hasManageRole: true,
        permissions: [],
      }),
    );
    const onAssignMany = vi.fn();
    const client = createQueryClient({ onForbidden: vi.fn(), onUnauthenticated: vi.fn() });
    const invalidateSpy = vi.spyOn(client, "invalidateQueries");

    renderWithProviders(
      <SessionProvider
        loadSession={async () => ({
          ...TEST_ADMIN_SESSION,
          permissions: ["PERMISSION.VIEW", "USER.VIEW", "USER.ASSIGN", "ROLE.VIEW"],
        })}
      >
        <Toaster />
        <PermissionUsersTab
          selectedPermissionCode="CATALOG.VIEW"
          listPermissions={async () => PERMISSIONS_FIXTURE}
          listUsersByPermission={async () => PERMISSION_USERS_FIXTURE}
          exportUsersByPermission={exportUsersByPermission}
          getUserPermissions={getUserPermissions}
          updateUserPermissions={updateUserPermissions}
          onAssignMany={onAssignMany}
          onViewRoles={vi.fn()}
        />
      </SessionProvider>,
      { client },
    );
    expect(await screen.findByText("content.admin")).toBeInTheDocument();
    expect(screen.getByText("ops.admin")).toBeInTheDocument();
    expect(screen.getByText("Vai trò: Quản trị nội dung")).toBeInTheDocument();
    expect(screen.getByText(/platform gán ngày .*Hỗ trợ rà soát danh mục/)).toBeInTheDocument();
    expect(screen.getByText(/Tài khoản platform có mọi quyền/)).toBeInTheDocument();

    await user.click(screen.getByRole("button", { name: "Xuất Excel" }));
    await waitFor(() => {
      expect(exportUsersByPermission).toHaveBeenCalledWith("perm-catalog-view", {});
      expect(createObjectUrlSpy).toHaveBeenCalled();
      expect(clickSpy).toHaveBeenCalled();
    });

    await user.click(screen.getByRole("button", { name: "Gán quyền này cho nhiều người" }));
    expect(onAssignMany).toHaveBeenCalledWith("CATALOG.VIEW");

    await user.click(screen.getByRole("button", { name: "Gỡ quyền trực tiếp của ops.admin" }));
    const confirmDialog = await screen.findByRole("dialog");
    expect(within(confirmDialog).getByText("Gỡ quyền gán trực tiếp?")).toBeInTheDocument();

    await user.click(within(confirmDialog).getByRole("button", { name: "Gỡ quyền" }));
    await waitFor(() => {
      expect(getUserPermissions).toHaveBeenCalledWith("u-2");
      expect(updateUserPermissions).toHaveBeenCalledWith("u-2", { permissionCodes: [] });
      expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: rbacKeys.userRoles("u-2") });
      expect(invalidateSpy).toHaveBeenCalledWith({
        queryKey: rbacKeys.userDirectPermissions("u-2"),
      });
      expect(invalidateSpy).toHaveBeenCalledWith({
        queryKey: rbacKeys.userEffectivePermissions("u-2"),
      });
    });
    expect(invalidateSpy).not.toHaveBeenCalledWith({ queryKey: ["rbac", "user"] });
    expect(await screen.findByText("Đã gỡ quyền trực tiếp của ops.admin.")).toBeInTheDocument();
  });
});
