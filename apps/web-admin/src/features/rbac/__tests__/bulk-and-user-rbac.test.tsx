import { act, fireEvent, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { Toaster } from "@thi360/ui";
import { afterEach, describe, expect, it, vi } from "vitest";
import { SessionProvider } from "@/features/session/session-provider";
import { createQueryClient } from "@/lib/api/query-client";
import enMessages from "@/messages/en.json";
import viMessages from "@/messages/vi.json";
import { TEST_ADMIN_SESSION } from "@/test/fixtures";
import { renderWithProviders } from "@/test/render";
import { RbacApiError, rbacKeys } from "../api";
import {
  BULK_USER_IDS_STORAGE_KEY,
  BulkAssignTab,
  ENABLE_CLASS_FILTER,
  PREVIEW_DEBOUNCE_MS,
} from "../components/bulk-assign-tab";
import { UserRbacTab } from "../components/user-rbac-tab";
import {
  KNOWN_BULK_REASON_CODES,
  resolveBulkReasonMessage,
  resolveRbacErrorMessage,
  type RbacTranslator,
} from "../messages";
import type {
  BulkAuthzRequest,
  BulkAuthzResponse,
  EffectivePermissionsResponse,
  PageResponseRoleSummaryResponse,
  PageResponseUserSummaryResponse,
  PermissionResponse,
  UserDirectPermissionsResponse,
  UserRolesResponse,
} from "../types";

const ROLES_PAGE: PageResponseRoleSummaryResponse = {
  items: [
    {
      id: "role-content",
      code: "CONTENT_ADMIN",
      name: "Quản trị nội dung",
      access: "MANAGE",
      system: true,
      status: "ACTIVE",
      permissionCount: 2,
      userCount: 1,
      version: 0,
      createdAt: "2026-11-14T00:00:00Z",
      updatedAt: "2026-11-14T00:00:00Z",
    },
    {
      id: "role-student",
      code: "STUDENT",
      name: "Học viên",
      access: "EXAM",
      system: true,
      status: "ACTIVE",
      permissionCount: 0,
      userCount: 10,
      version: 0,
      createdAt: "2026-11-14T00:00:00Z",
      updatedAt: "2026-11-14T00:00:00Z",
    },
  ],
  page: 0,
  size: 100,
  totalItems: 2,
  totalPages: 1,
};

const PERMISSIONS_LIST: PermissionResponse[] = [
  {
    id: "perm-audit-view",
    code: "AUDIT.VIEW",
    bitIndex: 11,
    groupCode: "AUDIT",
    groupName: "Nhật ký",
    name: "Xem",
    scope: "MANAGE",
    roleCount: 1,
    userCount: 1,
  },
  {
    id: "perm-catalog-view",
    code: "CATALOG.VIEW",
    bitIndex: 9,
    groupCode: "CATALOG",
    groupName: "Danh mục",
    name: "Xem",
    scope: "MANAGE",
    roleCount: 2,
    userCount: 2,
  },
];

const USERS_PAGE: PageResponseUserSummaryResponse = {
  items: [
    {
      id: "u-ops",
      code: "U000002",
      username: "ops.admin",
      fullName: "Vận hành",
      source: "ADMIN_CREATED",
      status: "ACTIVE",
      mustChangePassword: false,
      roles: [
        {
          id: "role-ops",
          code: "OPERATIONS",
          name: "Vận hành / CSKH",
          access: "MANAGE",
        },
      ],
      version: 0,
      createdAt: "2026-11-14T00:00:00Z",
      updatedAt: "2026-11-14T00:00:00Z",
    },
    {
      id: "u-stu",
      code: "U000003",
      username: "student01",
      fullName: "Học viên 01",
      source: "ADMIN_CREATED",
      status: "ACTIVE",
      mustChangePassword: false,
      roles: [
        {
          id: "role-student",
          code: "STUDENT",
          name: "Học viên",
          access: "EXAM",
        },
      ],
      version: 0,
      createdAt: "2026-11-14T00:00:00Z",
      updatedAt: "2026-11-14T00:00:00Z",
    },
  ],
  page: 0,
  size: 20,
  totalItems: 2,
  totalPages: 1,
};

function makeTranslator(messages: Record<string, unknown>): RbacTranslator {
  return (key) => {
    const parts = key.split(".");
    let cursor: unknown = messages;
    for (const part of parts) {
      if (cursor && typeof cursor === "object" && part in cursor) {
        cursor = (cursor as Record<string, unknown>)[part];
      } else {
        return key;
      }
    }
    return typeof cursor === "string" ? cursor : key;
  };
}

describe("BulkAssignTab & UserRbacTab", () => {
  afterEach(() => {
    vi.useRealTimers();
    vi.restoreAllMocks();
    window.sessionStorage.clear();
  });

  it("BulkAssignTab: đọc sessionStorage rbac.bulk.userIds, ẩn bộ lọc lớp, debounce 400ms và loại phản hồi cũ", async () => {
    expect(ENABLE_CLASS_FILTER).toBe(false);
    window.sessionStorage.setItem(
      BULK_USER_IDS_STORAGE_KEY,
      JSON.stringify(["u-ops", "u-stu"]),
    );

    const { promise: firstPromise, resolve: resolveFirstPreview } =
      Promise.withResolvers<BulkAuthzResponse>();

    const previewBulkAuthz = vi
      .fn()
      .mockImplementationOnce(() => firstPromise)
      .mockResolvedValueOnce({
        willChange: 1,
        noChange: 0,
        skipped: 1,
        items: [
          {
            userId: "u-ops",
            username: "ops.admin",
            outcome: "WILL_CHANGE",
            added: ["AUDIT.VIEW"],
            removed: [],
          },
          {
            userId: "u-stu",
            username: "student01",
            outcome: "SKIPPED",
            reasonCode: "DIRECT_PERMISSION_REQUIRES_MANAGE",
            added: [],
            removed: [],
          },
        ],
      });

    const applyBulkAuthz = vi.fn<
      (payload: BulkAuthzRequest, idempotencyKey: string) => Promise<BulkAuthzResponse>
    >(async () => ({
      batchId: "batch-1",
      willChange: 1,
      noChange: 0,
      skipped: 1,
      items: [
        {
          userId: "u-ops",
          username: "ops.admin",
          outcome: "WILL_CHANGE",
          added: ["AUDIT.VIEW"],
          removed: [],
        },
        {
          userId: "u-stu",
          username: "student01",
          outcome: "SKIPPED",
          reasonCode: "DIRECT_PERMISSION_REQUIRES_MANAGE",
          added: [],
          removed: [],
        },
      ],
    }));

    renderWithProviders(
      <SessionProvider loadSession={async () => TEST_ADMIN_SESSION}>
        <Toaster />
        <BulkAssignTab
          initialPermissionCode="AUDIT.VIEW"
          listUsers={async () => USERS_PAGE}
          listRoles={async () => ROLES_PAGE}
          listPermissions={async () => PERMISSIONS_LIST}
          previewBulkAuthz={previewBulkAuthz}
          applyBulkAuthz={applyBulkAuthz}
        />
      </SessionProvider>,
    );

    expect(await screen.findByText(/đã chọn 2/)).toBeInTheDocument();
    expect(screen.queryByLabelText("Lớp")).not.toBeInTheDocument();
    await screen.findByText("ops.admin");

    vi.useFakeTimers();

    const reasonInput = screen.getByPlaceholderText("Ví dụ: Hỗ trợ rà soát danh mục trước kỳ thi");
    fireEvent.change(reasonInput, { target: { value: "Lý do 1" } });

    await act(async () => {
      await vi.advanceTimersByTimeAsync(PREVIEW_DEBOUNCE_MS);
    });
    expect(previewBulkAuthz).toHaveBeenCalledTimes(1);

    fireEvent.change(reasonInput, { target: { value: "Lý do 2" } });
    await act(async () => {
      await vi.advanceTimersByTimeAsync(PREVIEW_DEBOUNCE_MS);
    });
    expect(previewBulkAuthz).toHaveBeenCalledTimes(2);

    // Resolve the stale 1st preview AFTER the 2nd one already resolved: UI must keep the 2nd preview.
    await act(async () => {
      resolveFirstPreview({
        willChange: 0,
        noChange: 2,
        skipped: 0,
        items: [
          { userId: "u-ops", username: "stale-user", outcome: "NO_CHANGE", added: [], removed: [] },
        ],
      });
      await Promise.resolve();
    });

    expect(screen.queryByText("stale-user")).not.toBeInTheDocument();
    expect(
      screen.getByText(/Tài khoản không có vai trò phạm vi Quản trị nên không nhận được quyền trực tiếp/),
    ).toBeInTheDocument();

    const applyBtn = screen.getByRole("button", { name: "Áp dụng cho 1 tài khoản" });
    expect(applyBtn).toBeEnabled();
    fireEvent.click(applyBtn);

    vi.useRealTimers();
    await waitFor(() => {
      expect(applyBulkAuthz).toHaveBeenCalledTimes(1);
    });
    expect(applyBulkAuthz.mock.calls[0]![1]).toMatch(/\S+/);
    expect(
      await screen.findByText(/Đã hoàn tất áp dụng: thay đổi 1 tài khoản, không đổi 0, bỏ qua 1/),
    ).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Làm lô khác" })).toBeInTheDocument();
  });

  it("UserRbacTab: tài khoản chỉ có vai trò Thi thì khóa nút Thêm quyền; readOnly ẩn toàn bộ nút ghi", async () => {
    const examRoles: UserRolesResponse = {
      userId: "u-stu",
      access: "EXAM",
      roles: [
        {
          roleId: "role-student",
          roleCode: "STUDENT",
          roleName: "Học viên",
          access: "EXAM",
          status: "ACTIVE",
          system: true,
          assignedAt: "2026-11-14T00:00:00Z",
        },
      ],
    };
    const directInactive: UserDirectPermissionsResponse = {
      userId: "u-stu",
      hasManageRole: false,
      permissions: [
        {
          permissionId: "perm-audit-view",
          permissionCode: "AUDIT.VIEW",
          permissionName: "Xem",
          groupCode: "AUDIT",
          groupName: "Nhật ký",
          effective: false,
          grantedAt: "2026-11-12T00:00:00Z",
          grantedByUsername: "platform",
          reason: "Tạm giữ",
        },
      ],
    };
    const emptyEffective: EffectivePermissionsResponse = {
      userId: "u-stu",
      effectiveAccess: "EXAM",
      hasManageRole: false,
      inactiveDirectPermissions: directInactive.permissions,
      permissions: [],
    };

    const { rerender } = renderWithProviders(
      <SessionProvider loadSession={async () => TEST_ADMIN_SESSION}>
        <UserRbacTab
          userId="u-stu"
          getUserRoles={async () => examRoles}
          getUserPermissions={async () => directInactive}
          getEffectivePermissions={async () => emptyEffective}
          listRoles={async () => ROLES_PAGE}
          listPermissions={async () => PERMISSIONS_LIST}
        />
      </SessionProvider>,
    );

    expect(await screen.findByText("Học viên")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Thêm quyền" })).toBeDisabled();
    expect(screen.getByText("Tạm không hiệu lực")).toBeInTheDocument();
    expect(
      screen.getByText(/Tài khoản này không có quyền quản trị nào/),
    ).toBeInTheDocument();

    rerender(
      <SessionProvider loadSession={async () => TEST_ADMIN_SESSION}>
        <UserRbacTab
          userId="u-stu"
          readOnly
          getUserRoles={async () => examRoles}
          getUserPermissions={async () => directInactive}
          getEffectivePermissions={async () => emptyEffective}
          listRoles={async () => ROLES_PAGE}
          listPermissions={async () => PERMISSIONS_LIST}
        />
      </SessionProvider>,
    );

    expect(screen.queryByRole("button", { name: "Gán vai trò" })).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Thêm quyền" })).not.toBeInTheDocument();
  });

  it("UserRbacTab: mở dialog Thêm quyền, bắt buộc nhập lý do và ánh xạ lỗi PRIVILEGE_ESCALATION", async () => {
    const user = userEvent.setup();
    const manageRoles: UserRolesResponse = {
      userId: "u-ops",
      access: "MANAGE",
      roles: [
        {
          roleId: "role-content",
          roleCode: "CONTENT_ADMIN",
          roleName: "Quản trị nội dung",
          access: "MANAGE",
          status: "ACTIVE",
          system: true,
          assignedAt: "2026-11-14T00:00:00Z",
        },
      ],
    };
    const emptyDirect: UserDirectPermissionsResponse = {
      userId: "u-ops",
      hasManageRole: true,
      permissions: [],
    };
    const effective: EffectivePermissionsResponse = {
      userId: "u-ops",
      effectiveAccess: "MANAGE",
      hasManageRole: true,
      inactiveDirectPermissions: [],
      permissions: [],
    };
    const updateUserPermissions = vi
      .fn()
      .mockRejectedValueOnce(new RbacApiError(403, "Forbidden", "PRIVILEGE_ESCALATION"));

    renderWithProviders(
      <SessionProvider loadSession={async () => TEST_ADMIN_SESSION}>
        <UserRbacTab
          userId="u-ops"
          username="ops.admin"
          fullName="Vận hành"
          getUserRoles={async () => manageRoles}
          getUserPermissions={async () => emptyDirect}
          updateUserPermissions={updateUserPermissions}
          getEffectivePermissions={async () => effective}
          listRoles={async () => ROLES_PAGE}
          listPermissions={async () => PERMISSIONS_LIST}
        />
      </SessionProvider>,
    );

    await user.click(await screen.findByRole("button", { name: "Thêm quyền" }));
    const dialog = await screen.findByRole("dialog");

    await user.click(within(dialog).getByRole("checkbox", { name: "Cấp đối tượng Nhật ký" }));
    await user.click(within(dialog).getByRole("button", { name: "Thêm quyền" }));

    expect(
      within(dialog).getByText("Vui lòng nhập lý do khi gán quyền trực tiếp."),
    ).toBeInTheDocument();
    expect(updateUserPermissions).not.toHaveBeenCalled();

    await user.type(
      within(dialog).getByPlaceholderText("Nhập lý do gán quyền trực tiếp"),
      "Kiểm tra nhật ký",
    );
    await user.click(within(dialog).getByRole("button", { name: "Thêm quyền" }));

    expect(
      await within(dialog).findByText(
        "Bạn không thể gán quyền hoặc vai trò vượt quá quyền hiện có của mình.",
      ),
    ).toBeInTheDocument();
  });

  it("UserRbacTab: giữ quyền riêng USER.ASSIGN khi USER.VIEW đến từ vai trò và người dùng thêm đối tượng khác", async () => {
    const user = userEvent.setup();
    const manageRoles: UserRolesResponse = {
      userId: "u-target",
      access: "MANAGE",
      roles: [
        {
          roleId: "role-ops",
          roleCode: "OPERATIONS",
          roleName: "Vận hành / CSKH",
          access: "MANAGE",
          status: "ACTIVE",
          system: true,
          assignedAt: "2026-11-14T00:00:00Z",
        },
      ],
    };
    const directWithAssign: UserDirectPermissionsResponse = {
      userId: "u-target",
      hasManageRole: true,
      permissions: [
        {
          permissionId: "perm-user-assign",
          permissionCode: "USER.ASSIGN",
          permissionName: "Gán vai trò và quyền",
          groupCode: "USER",
          groupName: "Tài khoản",
          effective: true,
          grantedAt: "2026-11-12T00:00:00Z",
          grantedByUsername: "platform",
          reason: "Hỗ trợ phân quyền",
        },
      ],
    };
    const effectiveWithRoleView: EffectivePermissionsResponse = {
      userId: "u-target",
      effectiveAccess: "MANAGE",
      hasManageRole: true,
      inactiveDirectPermissions: [],
      permissions: [
        {
          permissionId: "perm-user-view",
          code: "USER.VIEW",
          name: "Xem",
          groupCode: "USER",
          groupName: "Tài khoản",
          sources: [
            {
              type: "ROLE",
              roleId: "role-ops",
              roleCode: "OPERATIONS",
              roleName: "Vận hành / CSKH",
            },
          ],
        },
        {
          permissionId: "perm-user-assign",
          code: "USER.ASSIGN",
          name: "Gán vai trò và quyền",
          groupCode: "USER",
          groupName: "Tài khoản",
          sources: [{ type: "DIRECT", reason: "Hỗ trợ phân quyền" }],
        },
      ],
    };
    const permissionsCatalog: PermissionResponse[] = [
      {
        id: "perm-user-view",
        code: "USER.VIEW",
        bitIndex: 0,
        groupCode: "USER",
        groupName: "Tài khoản",
        name: "Xem",
        scope: "MANAGE",
        roleCount: 1,
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
        userCount: 1,
      },
      ...PERMISSIONS_LIST,
    ];
    const updateUserPermissions = vi.fn(async () => directWithAssign);

    renderWithProviders(
      <SessionProvider loadSession={async () => TEST_ADMIN_SESSION}>
        <UserRbacTab
          userId="u-target"
          username="target.user"
          fullName="Target"
          getUserRoles={async () => manageRoles}
          getUserPermissions={async () => directWithAssign}
          updateUserPermissions={updateUserPermissions}
          getEffectivePermissions={async () => effectiveWithRoleView}
          listRoles={async () => ROLES_PAGE}
          listPermissions={async () => permissionsCatalog}
        />
      </SessionProvider>,
    );

    await user.click(await screen.findByRole("button", { name: "Thêm quyền" }));
    const dialog = await screen.findByRole("dialog");

    await user.click(within(dialog).getByRole("checkbox", { name: "Cấp đối tượng Nhật ký" }));
    await user.type(
      within(dialog).getByPlaceholderText("Nhập lý do gán quyền trực tiếp"),
      "Bổ sung xem nhật ký",
    );
    await user.click(within(dialog).getByRole("button", { name: "Thêm quyền" }));

    await waitFor(() => {
      expect(updateUserPermissions).toHaveBeenCalledWith("u-target", {
        permissionCodes: ["USER.ASSIGN", "AUDIT.VIEW"],
        reason: "Bổ sung xem nhật ký",
      });
    });
  });

  it("BulkAssignTab (production QueryClient): không gọi GET /permissions ở chế độ Vai trò, khóa Quyền trực tiếp khi thiếu PERMISSION.VIEW và không kích hoạt onForbidden", async () => {
    const user = userEvent.setup();
    const onForbidden = vi.fn();
    const onUnauthenticated = vi.fn();
    const prodClient = createQueryClient({ onForbidden, onUnauthenticated });

    const listPermissions = vi.fn(async () => {
      throw new RbacApiError(403, "Forbidden", "FORBIDDEN");
    });
    const previewBulkAuthz = vi.fn(async (): Promise<BulkAuthzResponse> => ({
      willChange: 1,
      noChange: 0,
      skipped: 0,
      items: [
        {
          userId: "u-ops",
          username: "ops.admin",
          outcome: "WILL_CHANGE",
          added: ["STUDENT"],
          removed: [],
        },
      ],
    }));
    const applyBulkAuthz = vi.fn(async (): Promise<BulkAuthzResponse> => ({
      batchId: "batch-role",
      willChange: 1,
      noChange: 0,
      skipped: 0,
      items: [
        {
          userId: "u-ops",
          username: "ops.admin",
          outcome: "WILL_CHANGE",
          added: ["STUDENT"],
          removed: [],
        },
      ],
    }));

    renderWithProviders(
      <SessionProvider
        loadSession={async () => ({
          ...TEST_ADMIN_SESSION,
          permissions: ["USER.ASSIGN", "USER.VIEW", "ROLE.VIEW"],
        })}
      >
        <BulkAssignTab
          listUsers={async () => USERS_PAGE}
          listRoles={async () => ROLES_PAGE}
          listPermissions={listPermissions}
          previewBulkAuthz={previewBulkAuthz}
          applyBulkAuthz={applyBulkAuthz}
        />
      </SessionProvider>,
      { client: prodClient },
    );

    expect(await screen.findByText("ops.admin")).toBeInTheDocument();
    expect(listPermissions).not.toHaveBeenCalled();
    expect(onForbidden).not.toHaveBeenCalled();
    expect(screen.getByRole("button", { name: "Quyền trực tiếp" })).toBeDisabled();
    expect(screen.getByText(viMessages.rbac.bulk.missingPermissionViewNote)).toBeInTheDocument();

    await user.click(screen.getByRole("checkbox", { name: /ops\.admin/ }));
    await user.click(screen.getByRole("button", { name: /Học viên/ }));

    await waitFor(() => {
      expect(previewBulkAuthz).toHaveBeenCalledTimes(1);
    });
    await user.click(await screen.findByRole("button", { name: "Áp dụng cho 1 tài khoản" }));
    await waitFor(() => {
      expect(applyBulkAuthz).toHaveBeenCalledTimes(1);
    });
    expect(listPermissions).not.toHaveBeenCalled();
    expect(onForbidden).not.toHaveBeenCalled();
  });

  it("BulkAssignTab (production QueryClient): thiếu ROLE.VIEW không gọi GET /roles và khóa chế độ Vai trò với ghi chú", async () => {
    const onForbidden = vi.fn();
    const prodClient = createQueryClient({ onForbidden, onUnauthenticated: vi.fn() });
    const listRoles = vi.fn(async () => {
      throw new RbacApiError(403, "Forbidden", "FORBIDDEN");
    });

    renderWithProviders(
      <SessionProvider
        loadSession={async () => ({
          ...TEST_ADMIN_SESSION,
          permissions: ["USER.ASSIGN", "USER.VIEW", "PERMISSION.VIEW"],
        })}
      >
        <BulkAssignTab
          listUsers={async () => USERS_PAGE}
          listRoles={listRoles}
          listPermissions={async () => PERMISSIONS_LIST}
        />
      </SessionProvider>,
      { client: prodClient },
    );

    expect(await screen.findByText("ops.admin")).toBeInTheDocument();
    expect(listRoles).not.toHaveBeenCalled();
    expect(onForbidden).not.toHaveBeenCalled();
    expect(screen.getByRole("button", { name: "Vai trò" })).toBeDisabled();
    expect(screen.getByText(viMessages.rbac.bulk.missingRoleViewNote)).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "+ Thêm quyền" })).toBeEnabled();
  });

  it("UserRbacTab (production QueryClient): thiếu PERMISSION.VIEW hoặc ROLE.VIEW không gọi lookup trái phép và khóa dialog tương ứng", async () => {
    const onForbidden = vi.fn();
    const prodClient = createQueryClient({ onForbidden, onUnauthenticated: vi.fn() });
    const listPermissions = vi.fn(async () => {
      throw new RbacApiError(403, "Forbidden", "FORBIDDEN");
    });
    const listRoles = vi.fn(async () => {
      throw new RbacApiError(403, "Forbidden", "FORBIDDEN");
    });

    const manageRoles: UserRolesResponse = {
      userId: "u-ops",
      access: "MANAGE",
      roles: [
        {
          roleId: "role-content",
          roleCode: "CONTENT_ADMIN",
          roleName: "Quản trị nội dung",
          access: "MANAGE",
          status: "ACTIVE",
          system: true,
          assignedAt: "2026-11-14T00:00:00Z",
        },
      ],
    };
    const emptyDirect: UserDirectPermissionsResponse = {
      userId: "u-ops",
      hasManageRole: true,
      permissions: [],
    };
    const emptyEffective: EffectivePermissionsResponse = {
      userId: "u-ops",
      effectiveAccess: "MANAGE",
      hasManageRole: true,
      inactiveDirectPermissions: [],
      permissions: [],
    };

    const { unmount } = renderWithProviders(
      <SessionProvider
        loadSession={async () => ({
          ...TEST_ADMIN_SESSION,
          permissions: ["USER.ASSIGN", "USER.VIEW", "ROLE.VIEW"],
        })}
      >
        <UserRbacTab
          userId="u-ops"
          getUserRoles={async () => manageRoles}
          getUserPermissions={async () => emptyDirect}
          getEffectivePermissions={async () => emptyEffective}
          listRoles={async () => ROLES_PAGE}
          listPermissions={listPermissions}
        />
      </SessionProvider>,
      { client: prodClient },
    );

    expect(await screen.findByRole("button", { name: "Gán vai trò" })).toBeEnabled();
    expect(screen.getByRole("button", { name: "Thêm quyền" })).toBeDisabled();
    expect(
      screen.getByText(viMessages.rbac.userRbac.missingPermissionViewNote),
    ).toBeInTheDocument();
    expect(listPermissions).not.toHaveBeenCalled();
    expect(onForbidden).not.toHaveBeenCalled();

    unmount();
    const secondClient = createQueryClient({ onForbidden, onUnauthenticated: vi.fn() });
    renderWithProviders(
      <SessionProvider
        loadSession={async () => ({
          ...TEST_ADMIN_SESSION,
          permissions: ["USER.ASSIGN", "USER.VIEW", "PERMISSION.VIEW"],
        })}
      >
        <UserRbacTab
          userId="u-ops"
          getUserRoles={async () => manageRoles}
          getUserPermissions={async () => emptyDirect}
          getEffectivePermissions={async () => emptyEffective}
          listRoles={listRoles}
          listPermissions={async () => PERMISSIONS_LIST}
        />
      </SessionProvider>,
      { client: secondClient },
    );

    await waitFor(() => {
      expect(screen.getByRole("button", { name: "Gán vai trò" })).toBeDisabled();
    });
    expect(screen.getByRole("button", { name: "Thêm quyền" })).toBeEnabled();
    expect(screen.getByText(viMessages.rbac.userRbac.missingRoleViewNote)).toBeInTheDocument();
    expect(listRoles).not.toHaveBeenCalled();
    expect(onForbidden).not.toHaveBeenCalled();
  });

  it("BulkAssignTab & UserRbacTab (R1-2 Advisor decision): tài khoản chỉ có USER.ASSIGN + USER.VIEW thấy ghi chú tiếng Việt, không gọi /roles hay /permissions và không kích hoạt onForbidden", async () => {
    const onForbidden = vi.fn();
    const listRoles = vi.fn(async () => {
      throw new RbacApiError(403, "Forbidden", "FORBIDDEN");
    });
    const listPermissions = vi.fn(async () => {
      throw new RbacApiError(403, "Forbidden", "FORBIDDEN");
    });

    const bulkClient = createQueryClient({ onForbidden, onUnauthenticated: vi.fn() });
    const { unmount } = renderWithProviders(
      <SessionProvider
        loadSession={async () => ({
          ...TEST_ADMIN_SESSION,
          permissions: ["USER.ASSIGN", "USER.VIEW"],
        })}
      >
        <BulkAssignTab
          listUsers={async () => USERS_PAGE}
          listRoles={listRoles}
          listPermissions={listPermissions}
        />
      </SessionProvider>,
      { client: bulkClient },
    );

    expect(await screen.findByText("ops.admin")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Vai trò" })).toBeDisabled();
    expect(screen.getByRole("button", { name: "Quyền trực tiếp" })).toBeDisabled();
    expect(screen.getByText(viMessages.rbac.bulk.missingRoleViewNote)).toBeInTheDocument();
    expect(screen.getByText(viMessages.rbac.bulk.missingPermissionViewNote)).toBeInTheDocument();
    expect(listRoles).not.toHaveBeenCalled();
    expect(listPermissions).not.toHaveBeenCalled();
    expect(onForbidden).not.toHaveBeenCalled();
    unmount();

    const userClient = createQueryClient({ onForbidden, onUnauthenticated: vi.fn() });
    renderWithProviders(
      <SessionProvider
        loadSession={async () => ({
          ...TEST_ADMIN_SESSION,
          permissions: ["USER.ASSIGN", "USER.VIEW"],
        })}
      >
        <UserRbacTab
          userId="u-ops"
          getUserRoles={async () => ({
            userId: "u-ops",
            access: "MANAGE",
            roles: [
              {
                roleId: "role-content",
                roleCode: "CONTENT_ADMIN",
                roleName: "Quản trị nội dung",
                access: "MANAGE",
                status: "ACTIVE",
                system: true,
                assignedAt: "2026-11-14T00:00:00Z",
              },
            ],
          })}
          getUserPermissions={async () => ({
            userId: "u-ops",
            hasManageRole: true,
            permissions: [],
          })}
          getEffectivePermissions={async () => ({
            userId: "u-ops",
            effectiveAccess: "MANAGE",
            hasManageRole: true,
            inactiveDirectPermissions: [],
            permissions: [],
          })}
          listRoles={listRoles}
          listPermissions={listPermissions}
        />
      </SessionProvider>,
      { client: userClient },
    );

    expect(await screen.findByRole("button", { name: "Gán vai trò" })).toBeDisabled();
    expect(screen.getByRole("button", { name: "Thêm quyền" })).toBeDisabled();
    expect(screen.getByText(viMessages.rbac.userRbac.missingRoleViewNote)).toBeInTheDocument();
    expect(
      screen.getByText(viMessages.rbac.userRbac.missingPermissionViewNote),
    ).toBeInTheDocument();
    expect(listRoles).not.toHaveBeenCalled();
    expect(listPermissions).not.toHaveBeenCalled();
    expect(onForbidden).not.toHaveBeenCalled();
  });

  it("BulkAssignTab (R1-3): khóa các nút chỉnh sửa batch khi apply đang chạy, giữ nguyên payload đã bind và đồng bộ initialPermissionCode khi prop đổi", async () => {
    const user = userEvent.setup();
    const deferredApply = Promise.withResolvers<BulkAuthzResponse>();
    const applyBulkAuthz = vi.fn(() => deferredApply.promise);
    const previewBulkAuthz = vi.fn(async (payload: BulkAuthzRequest): Promise<BulkAuthzResponse> => ({
      willChange: payload.userIds.length,
      noChange: 0,
      skipped: 0,
      items: payload.userIds.map((userId) => ({
        userId,
        username: "ops.admin",
        outcome: "WILL_CHANGE",
        added: payload.permissionIds ?? payload.roleIds ?? [],
        removed: [],
      })),
    }));

    const { rerender } = renderWithProviders(
      <SessionProvider loadSession={async () => TEST_ADMIN_SESSION}>
        <Toaster />
        <BulkAssignTab
          initialPermissionCode="AUDIT.VIEW"
          listUsers={async () => USERS_PAGE}
          listRoles={async () => ROLES_PAGE}
          listPermissions={async () => PERMISSIONS_LIST}
          previewBulkAuthz={previewBulkAuthz}
          applyBulkAuthz={applyBulkAuthz}
        />
      </SessionProvider>,
    );

    expect(await screen.findByText("ops.admin")).toBeInTheDocument();

    await user.click(screen.getByRole("checkbox", { name: /ops\.admin/ }));
    await user.type(
      screen.getByPlaceholderText("Ví dụ: Hỗ trợ rà soát danh mục trước kỳ thi"),
      "Hỗ trợ audit",
    );

    const applyBtn = await screen.findByRole("button", { name: "Áp dụng cho 1 tài khoản" });
    await user.click(applyBtn);

    await waitFor(() => {
      expect(applyBulkAuthz).toHaveBeenCalledTimes(1);
    });

    expect(screen.getByRole("button", { name: viMessages.rbac.bulk.actionGrant })).toBeDisabled();
    expect(screen.getByRole("button", { name: viMessages.rbac.bulk.actionRevoke })).toBeDisabled();
    expect(screen.getByRole("button", { name: viMessages.rbac.bulk.targetRole })).toBeDisabled();
    expect(screen.getByRole("button", { name: viMessages.rbac.bulk.targetPermission })).toBeDisabled();
    expect(screen.getByRole("button", { name: viMessages.rbac.bulk.addPermission })).toBeDisabled();
    expect(screen.getByPlaceholderText(viMessages.rbac.bulk.reasonPlaceholder)).toBeDisabled();
    expect(screen.getByRole("checkbox", { name: /ops\.admin/ })).toBeDisabled();

    deferredApply.resolve({
      batchId: "batch-locked",
      willChange: 1,
      noChange: 0,
      skipped: 0,
      items: [
        {
          userId: "u-ops",
          username: "ops.admin",
          outcome: "WILL_CHANGE",
          added: ["AUDIT.VIEW"],
          removed: [],
        },
      ],
    });

    expect(
      await screen.findByText(/Đã hoàn tất áp dụng: thay đổi 1 tài khoản/),
    ).toBeInTheDocument();

    rerender(
      <SessionProvider loadSession={async () => TEST_ADMIN_SESSION}>
        <Toaster />
        <BulkAssignTab
          initialPermissionCode="USER.ASSIGN"
          listUsers={async () => USERS_PAGE}
          listRoles={async () => ROLES_PAGE}
          listPermissions={async () => PERMISSIONS_LIST}
          previewBulkAuthz={previewBulkAuthz}
          applyBulkAuthz={applyBulkAuthz}
        />
      </SessionProvider>,
    );

    await waitFor(() => {
      expect(screen.getByRole("button", { name: /Bỏ quyền/ })).toBeInTheDocument();
    });
  });

  it("BulkAssignTab & UserRbacTab (R1-5): tải đủ mọi trang vai trò (>100) và tìm kiếm được vai trò thứ 101", async () => {
    const user = userEvent.setup();
    const page0Roles = Array.from({ length: 100 }, (_, i) => ({
      id: `r-${i + 1}`,
      code: `ROLE_${i + 1}`,
      name: `Vai trò ${i + 1}`,
      access: "MANAGE" as const,
      status: "ACTIVE" as const,
      system: false,
      permissionCount: 1,
      userCount: 0,
      createdAt: "2026-11-14T00:00:00Z",
      version: 1,
      updatedAt: "2026-11-14T00:00:00Z",
    }));
    const role101 = {
      id: "r-101",
      code: "ROLE_101",
      name: "Vai trò đặc biệt 101",
      access: "MANAGE" as const,
      status: "ACTIVE" as const,
      system: false,
      permissionCount: 1,
      userCount: 0,
      createdAt: "2026-11-14T00:00:00Z",
      version: 1,
      updatedAt: "2026-11-14T00:00:00Z",
    };
    const listRolesPaged = vi.fn(async (params?: { page?: number }) => {
      const page = params?.page ?? 0;
      if (page === 0) {
        return { items: page0Roles, page: 0, size: 100, totalItems: 101, totalPages: 2 };
      }
      return { items: [role101], page: 1, size: 100, totalItems: 101, totalPages: 2 };
    });

    const { unmount } = renderWithProviders(
      <SessionProvider loadSession={async () => TEST_ADMIN_SESSION}>
        <BulkAssignTab
          listUsers={async () => USERS_PAGE}
          listRoles={listRolesPaged}
          listPermissions={async () => PERMISSIONS_LIST}
        />
      </SessionProvider>,
    );

    expect(await screen.findByText("Vai trò đặc biệt 101")).toBeInTheDocument();
    expect(listRolesPaged).toHaveBeenCalledWith(expect.objectContaining({ page: 0, size: 100 }));
    expect(listRolesPaged).toHaveBeenCalledWith(expect.objectContaining({ page: 1, size: 100 }));

    const bulkSearch = screen.getByPlaceholderText(viMessages.rbac.bulk.roleSearchPlaceholder);
    fireEvent.change(bulkSearch, { target: { value: "101" } });
    expect(screen.getByText("Vai trò đặc biệt 101")).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: /Vai trò 1 \(/ })).not.toBeInTheDocument();
    unmount();

    const listRolesUserPaged = vi.fn(async (params?: { page?: number }) => {
      const page = params?.page ?? 0;
      if (page === 0) {
        return { items: page0Roles, page: 0, size: 100, totalItems: 101, totalPages: 2 };
      }
      return { items: [role101], page: 1, size: 100, totalItems: 101, totalPages: 2 };
    });
    const updateUserRoles = vi.fn(async () => ({
      userId: "u-ops",
      access: "MANAGE" as const,
      roles: [],
    }));

    renderWithProviders(
      <SessionProvider loadSession={async () => TEST_ADMIN_SESSION}>
        <Toaster />
        <UserRbacTab
          userId="u-ops"
          getUserRoles={async () => ({ userId: "u-ops", access: "MANAGE", roles: [] })}
          getUserPermissions={async () => ({
            userId: "u-ops",
            hasManageRole: true,
            permissions: [],
          })}
          getEffectivePermissions={async () => ({
            userId: "u-ops",
            effectiveAccess: "MANAGE",
            hasManageRole: true,
            inactiveDirectPermissions: [],
            permissions: [],
          })}
          listRoles={listRolesUserPaged}
          listPermissions={async () => PERMISSIONS_LIST}
          updateUserRoles={updateUserRoles}
        />
      </SessionProvider>,
    );

    await user.click(await screen.findByRole("button", { name: "Gán vai trò" }));
    const dialog = await screen.findByRole("dialog");
    expect(await within(dialog).findByText("Vai trò đặc biệt 101")).toBeInTheDocument();

    const dialogSearch = within(dialog).getByPlaceholderText(
      viMessages.rbac.userRbac.roleSearchPlaceholder,
    );
    fireEvent.change(dialogSearch, { target: { value: "ROLE_101" } });
    await user.click(within(dialog).getByRole("checkbox", { name: /Vai trò đặc biệt 101/ }));
    await user.click(within(dialog).getByRole("button", { name: "Lưu vai trò" }));

    await waitFor(() => {
      expect(updateUserRoles).toHaveBeenCalledWith("u-ops", { roleIds: ["r-101"] });
    });
  }, 15_000);

  it("BulkAssignTab & UserRbacTab (R1-6): hiện lỗi + Thử lại khi lookup vai trò/quyền lỗi và khóa nút lưu cho tới khi có dữ liệu", async () => {
    const user = userEvent.setup();
    const listRolesBulk = vi
      .fn()
      .mockRejectedValueOnce(new RbacApiError(500, "Server error"))
      .mockResolvedValueOnce(ROLES_PAGE);
    const listPermissionsBulk = vi
      .fn()
      .mockRejectedValueOnce(new RbacApiError(500, "Server error"))
      .mockResolvedValueOnce(PERMISSIONS_LIST);

    const { unmount } = renderWithProviders(
      <SessionProvider loadSession={async () => TEST_ADMIN_SESSION}>
        <BulkAssignTab
          listUsers={async () => USERS_PAGE}
          listRoles={listRolesBulk}
          listPermissions={listPermissionsBulk}
        />
      </SessionProvider>,
    );

    expect(await screen.findByText(viMessages.rbac.bulk.errorRolesTitle)).toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: viMessages.rbac.actions.retry }));
    expect(await screen.findByText("Học viên")).toBeInTheDocument();

    await user.click(screen.getByRole("button", { name: viMessages.rbac.bulk.targetPermission }));
    expect(
      await screen.findByText(viMessages.rbac.bulk.errorPermissionsTitle),
    ).toBeInTheDocument();
    expect(
      screen.queryByRole("button", { name: viMessages.rbac.bulk.addPermission }),
    ).not.toBeInTheDocument();

    await user.click(screen.getByRole("button", { name: viMessages.rbac.actions.retry }));
    expect(
      await screen.findByRole("button", { name: viMessages.rbac.bulk.addPermission }),
    ).toBeEnabled();
    unmount();

    const listRolesUser = vi
      .fn()
      .mockRejectedValueOnce(new RbacApiError(500, "Server error"))
      .mockResolvedValueOnce(ROLES_PAGE);
    const listPermissionsUser = vi
      .fn()
      .mockRejectedValueOnce(new RbacApiError(500, "Server error"))
      .mockResolvedValueOnce(PERMISSIONS_LIST);

    renderWithProviders(
      <SessionProvider loadSession={async () => TEST_ADMIN_SESSION}>
        <Toaster />
        <UserRbacTab
          userId="u-ops"
          getUserRoles={async () => ({ userId: "u-ops", access: "MANAGE", roles: [] })}
          getUserPermissions={async () => ({
            userId: "u-ops",
            hasManageRole: true,
            permissions: [],
          })}
          getEffectivePermissions={async () => ({
            userId: "u-ops",
            effectiveAccess: "MANAGE",
            hasManageRole: true,
            inactiveDirectPermissions: [],
            permissions: [],
          })}
          listRoles={listRolesUser}
          listPermissions={listPermissionsUser}
        />
      </SessionProvider>,
    );

    await user.click(await screen.findByRole("button", { name: "Gán vai trò" }));
    let dialog = await screen.findByRole("dialog");
    expect(
      await within(dialog).findByText(viMessages.rbac.userRbac.errorRolesTitle),
    ).toBeInTheDocument();
    expect(within(dialog).getByRole("button", { name: "Lưu vai trò" })).toBeDisabled();

    await user.click(within(dialog).getByRole("button", { name: viMessages.rbac.actions.retry }));
    expect(await within(dialog).findByText("Học viên")).toBeInTheDocument();
    expect(within(dialog).getByRole("button", { name: "Lưu vai trò" })).toBeEnabled();

    await user.click(within(dialog).getByRole("button", { name: "Hủy" }));
    await waitFor(() => {
      expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
    });

    await user.click(screen.getByRole("button", { name: "Thêm quyền" }));
    dialog = await screen.findByRole("dialog");
    expect(
      await within(dialog).findByText(viMessages.rbac.userRbac.errorPermissionsTitle),
    ).toBeInTheDocument();
    expect(
      within(dialog).getByRole("button", { name: viMessages.rbac.userRbac.directDialogSubmit }),
    ).toBeDisabled();

    await user.click(within(dialog).getByRole("button", { name: viMessages.rbac.actions.retry }));
    expect(
      await within(dialog).findByRole("checkbox", { name: "Cấp đối tượng Nhật ký" }),
    ).toBeInTheDocument();
    expect(
      within(dialog).getByRole("button", { name: viMessages.rbac.userRbac.directDialogSubmit }),
    ).toBeEnabled();
  }, 15_000);

  it("BulkAssignTab (R1-9): làm mới đúng các query key rbac của từng tài khoản sau khi áp dụng", async () => {
    const user = userEvent.setup();
    const client = createQueryClient({ onForbidden: vi.fn(), onUnauthenticated: vi.fn() });
    const invalidateSpy = vi.spyOn(client, "invalidateQueries");

    renderWithProviders(
      <SessionProvider loadSession={async () => TEST_ADMIN_SESSION}>
        <Toaster />
        <BulkAssignTab
          listUsers={async () => USERS_PAGE}
          listRoles={async () => ROLES_PAGE}
          listPermissions={async () => PERMISSIONS_LIST}
          previewBulkAuthz={async () => ({
            willChange: 1,
            noChange: 0,
            skipped: 0,
            items: [
              {
                userId: "u-ops",
                username: "ops.admin",
                outcome: "WILL_CHANGE",
                added: ["STUDENT"],
                removed: [],
              },
            ],
          })}
          applyBulkAuthz={async () => ({
            batchId: "batch-invalidate",
            willChange: 1,
            noChange: 0,
            skipped: 0,
            items: [
              {
                userId: "u-ops",
                username: "ops.admin",
                outcome: "WILL_CHANGE",
                added: ["STUDENT"],
                removed: [],
              },
            ],
          })}
        />
      </SessionProvider>,
      { client },
    );

    await user.click(await screen.findByRole("checkbox", { name: /ops\.admin/ }));
    await user.click(screen.getByRole("button", { name: /Học viên/ }));
    await user.click(await screen.findByRole("button", { name: "Áp dụng cho 1 tài khoản" }));

    await waitFor(() => {
      expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: rbacKeys.userRoles("u-ops") });
      expect(invalidateSpy).toHaveBeenCalledWith({
        queryKey: rbacKeys.userDirectPermissions("u-ops"),
      });
      expect(invalidateSpy).toHaveBeenCalledWith({
        queryKey: rbacKeys.userEffectivePermissions("u-ops"),
      });
    });
    expect(invalidateSpy).not.toHaveBeenCalledWith({ queryKey: ["rbac", "user"] });
  });

  it("ánh xạ toàn bộ reasonCode và mã lỗi RBAC sang câu hiển thị tiếng Việt và tiếng Anh", () => {
    const viT = makeTranslator(viMessages.rbac);
    const enT = makeTranslator(enMessages.rbac);

    for (const code of KNOWN_BULK_REASON_CODES) {
      const viText = resolveBulkReasonMessage(viT, code);
      const enText = resolveBulkReasonMessage(enT, code);
      expect(viText).not.toBe(code);
      expect(enText).not.toBe(code);
      expect(viText).not.toContain("reasonCodes.");
      expect(enText).not.toContain("reasonCodes.");
    }

    const err = new RbacApiError(403, "Raw backend message", "DIRECT_PERMISSION_REQUIRES_MANAGE");
    expect(resolveRbacErrorMessage(viT, err, "bulk.applyFailed")).toContain("Quản trị");
    expect(resolveRbacErrorMessage(enT, err, "bulk.applyFailed")).toContain("Admin-scoped");
  });
});
