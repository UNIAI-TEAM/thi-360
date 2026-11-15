import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { NextIntlClientProvider } from "next-intl";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import type * as SessionApi from "@/features/session/api";
import { SessionProvider } from "@/features/session/session-provider";
import viMessages from "@/messages/vi.json";
import { FORBIDDEN_PATH, QueryProvider } from "@/providers/query-provider";
import { TEST_ADMIN_SESSION } from "@/test/fixtures";
import { toUsersApiError } from "../api";
import { CreateUserDialog } from "../components/create-user-dialog";
import { UserActionDialogs, useUserActionController } from "../components/user-action-dialogs";

const mocks = vi.hoisted(() => ({
  replace: vi.fn(),
  pathname: { current: "/users" },
  refreshSession: vi.fn(),
  clearSessionBestEffort: vi.fn(),
  navigate: vi.fn(),
}));

vi.mock("next/navigation", () => ({
  useRouter: () => ({ replace: mocks.replace }),
  usePathname: () => mocks.pathname.current,
}));

vi.mock("@/features/session/api", async (importOriginal) => {
  const actual = await importOriginal<typeof SessionApi>();
  return {
    ...actual,
    refreshSession: mocks.refreshSession,
    clearSessionBestEffort: mocks.clearSessionBestEffort,
  };
});

function DeactivateProbe({
  deactivateUser,
}: {
  deactivateUser: () => Promise<never>;
}) {
  const controller = useUserActionController({
    deps: {
      checkUserDeactivation: async () => ({ activeAttempt: false }),
      checkUserDeletion: async () => ({ deletable: true, blockers: [] }),
      deactivateUser,
      activateUser: async () => {
        throw new Error("unused");
      },
      deleteUser: async () => undefined,
      resetUserPassword: async () => {
        throw new Error("unused");
      },
    },
    onMutated: vi.fn(),
  });

  return (
    <>
      <button
        type="button"
        onClick={() => controller.deactivateMutation.mutate({ id: "u-1", reason: "Check 403" })}
      >
        Trigger deactivate
      </button>
      <UserActionDialogs controller={controller} />
    </>
  );
}

beforeEach(() => {
  mocks.pathname.current = "/users";
  mocks.refreshSession.mockResolvedValue(undefined);
  mocks.clearSessionBestEffort.mockResolvedValue(undefined);
  window.sessionStorage.clear();
});

afterEach(() => {
  mocks.replace.mockReset();
  mocks.refreshSession.mockReset();
  mocks.clearSessionBestEffort.mockReset();
  mocks.navigate.mockReset();
  window.sessionStorage.clear();
  vi.restoreAllMocks();
});

describe("UsersApiError 403 handling under real QueryProvider (R2-1 / R2-2)", () => {
  it("403 PRIVILEGE_ESCALATION hiển thị thông báo inline trong CreateUserDialog và không kích hoạt reload/forbidden", async () => {
    const createUserMock = vi.fn(async () => {
      throw toUsersApiError(403, {
        code: "PRIVILEGE_ESCALATION",
        message: "Cannot grant higher privilege",
        fieldErrors: [],
      });
    });

    render(
      <NextIntlClientProvider locale="vi" messages={viMessages} timeZone="Asia/Ho_Chi_Minh">
        <QueryProvider navigate={mocks.navigate}>
          <SessionProvider loadSession={async () => TEST_ADMIN_SESSION}>
            <CreateUserDialog
              open
              onOpenChange={vi.fn()}
              roles={[
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
              ]}
              onCreated={vi.fn()}
              createUser={createUserMock}
              checkUsernameAvailability={async () => ({ available: true })}
            />
          </SessionProvider>
        </QueryProvider>
      </NextIntlClientProvider>,
    );

    await userEvent.type(await screen.findByLabelText(/Tên đăng nhập/), "new.admin");
    await userEvent.click(screen.getByRole("button", { name: "Tạo ngẫu nhiên" }));
    await userEvent.type(screen.getByLabelText(/Họ và tên/), "New Admin");
    await userEvent.click(screen.getByRole("button", { name: "Tạo tài khoản" }));

    const alert = await screen.findByRole("alert");
    expect(alert).toHaveTextContent(viMessages.users.errorCodes.PRIVILEGE_ESCALATION);
    expect(mocks.refreshSession).not.toHaveBeenCalled();
    expect(mocks.navigate).not.toHaveBeenCalled();
    expect(mocks.replace).not.toHaveBeenCalled();
  });

  it("403 FORBIDDEN thường vẫn kích hoạt QueryProvider onForbidden (refreshSession + reload/redirect)", async () => {
    const deactivateMock = vi.fn(async () => {
      throw toUsersApiError(403, {
        code: "FORBIDDEN",
        message: "Forbidden",
        fieldErrors: [],
      });
    });

    render(
      <NextIntlClientProvider locale="vi" messages={viMessages} timeZone="Asia/Ho_Chi_Minh">
        <QueryProvider navigate={mocks.navigate}>
          <DeactivateProbe deactivateUser={deactivateMock} />
        </QueryProvider>
      </NextIntlClientProvider>,
    );

    await userEvent.click(screen.getByRole("button", { name: "Trigger deactivate" }));

    await waitFor(() => expect(mocks.navigate).toHaveBeenCalledWith(window.location.href));
    expect(mocks.refreshSession).toHaveBeenCalledTimes(1);
    expect(mocks.replace).not.toHaveBeenCalledWith(FORBIDDEN_PATH);
  });
});
