import { screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { SessionProvider } from "@/features/session/session-provider";
import type { Session } from "@/features/session/types";
import { TEST_ADMIN_SESSION } from "@/test/fixtures";
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
});

afterEach(() => {
  vi.restoreAllMocks();
});

describe("UserDetailView & action dialogs (Slice 2)", () => {
  it("hiển thị mã tài khoản, lần đăng nhập cuối, tab chỗ chờ T10/T08 và lịch sử thay đổi", async () => {
    const deps = makeDeps();
    renderDetail(deps);

    expect(await screen.findByRole("heading", { name: "Học viên 01" }, { timeout: 3000 })).toBeInTheDocument();
    expect(screen.getByText("U000101")).toBeInTheDocument();
    expect(screen.getByText(/student01 · U000101/)).toBeInTheDocument();

    await userEvent.click(screen.getByRole("tab", { name: "Lớp học" }));
    expect(await screen.findByText("Chưa kết nối dữ liệu lớp học")).toBeInTheDocument();

    await userEvent.click(screen.getByRole("tab", { name: "Vai trò & quyền" }));
    expect(
      await screen.findByText("Chưa ghép thành phần Vai trò & quyền (T08)"),
    ).toBeInTheDocument();

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
    renderDetail(deps);

    await userEvent.click(await screen.findByRole("button", { name: "Ngừng hoạt động" }));

    expect(
      await screen.findByText(/Cảnh báo: Tài khoản đang có lượt thi chưa hoàn thành/),
    ).toBeInTheDocument();

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
    expect(screen.getByText(/Tài khoản đã có đơn hàng thanh toán/)).toBeInTheDocument();

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
});
