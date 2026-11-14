import { act, fireEvent, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { Toaster } from "@thi360/ui";
import { afterEach, describe, expect, it, vi } from "vitest";
import { SessionProvider } from "@/features/session/session-provider";
import enMessages from "@/messages/en.json";
import viMessages from "@/messages/vi.json";
import { TEST_ADMIN_SESSION } from "@/test/fixtures";
import { renderWithProviders } from "@/test/render";
import { RbacApiError } from "../api";
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
