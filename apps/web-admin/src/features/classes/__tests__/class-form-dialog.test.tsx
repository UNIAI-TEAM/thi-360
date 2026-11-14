import { screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, describe, expect, it, vi } from "vitest";
import { SessionProvider } from "@/features/session/session-provider";
import type { Session } from "@/features/session/types";
import type { ListUsersQuery, PageResponseUserSummary, UserSummary } from "@/features/users/api";
import { TEST_ADMIN_SESSION } from "@/test/fixtures";
import { renderWithProviders } from "@/test/render";
import {
  ClassFormDialog,
  fetchDefaultActiveUsers,
} from "../components/class-form-dialog";

function makeUser(index: number): UserSummary {
  return {
    id: `00000000-0000-4000-8000-${String(index).padStart(12, "0")}`,
    code: `U${String(index).padStart(6, "0")}`,
    username: `user${String(index).padStart(3, "0")}`,
    fullName: `Người dùng ${index}`,
    source: "ADMIN_CREATED",
    status: "ACTIVE",
    mustChangePassword: false,
    roles: [],
    version: 0,
    createdAt: "2026-11-01T00:00:00Z",
    updatedAt: "2026-11-01T00:00:00Z",
  };
}

function renderDialog(
  props: Partial<Parameters<typeof ClassFormDialog>[0]> = {},
  session: Session = TEST_ADMIN_SESSION,
) {
  return renderWithProviders(
    <SessionProvider loadSession={async () => session}>
      <ClassFormDialog
        open
        onOpenChange={vi.fn()}
        onSaved={vi.fn()}
        createClass={vi.fn(async () => ({}) as never)}
        updateClass={vi.fn(async () => ({}) as never)}
        listActiveUsers={async () => [makeUser(1)]}
        {...props}
      />
    </SessionProvider>,
  );
}

afterEach(() => {
  vi.restoreAllMocks();
});

describe("ClassFormDialog owner selector", () => {
  it("hiển thị skeleton/hint loading khi đang tải danh sách người phụ trách", async () => {
    let resolveUsers!: (value: UserSummary[]) => void;
    const pending = new Promise<UserSummary[]>((resolve) => {
      resolveUsers = resolve;
    });
    renderDialog({ listActiveUsers: () => pending });

    const dialog = await screen.findByRole("dialog");
    // Session resolve → usersQuery bắt đầu fetch → hint loading hiển thị
    await within(dialog).findByText("Đang tải danh sách tài khoản...");
    expect(within(dialog).getByRole("combobox", { name: /Người phụ trách/ })).toBeDisabled();

    resolveUsers([makeUser(1)]);
    await waitFor(() => {
      expect(within(dialog).getByRole("combobox", { name: /Người phụ trách/ })).not.toBeDisabled();
    });
  });

  it("lỗi tải người phụ trách → báo lỗi + nút Thử lại, retry thành công enable select", async () => {
    const user = userEvent.setup();
    const listActiveUsersMock = vi
      .fn<() => Promise<UserSummary[]>>()
      .mockRejectedValueOnce(new Error("network"))
      .mockResolvedValueOnce([makeUser(1), makeUser(2)]);

    renderDialog({ listActiveUsers: listActiveUsersMock });

    const dialog = await screen.findByRole("dialog");
    await within(dialog).findByText("Không tải được danh sách người phụ trách.");
    expect(within(dialog).getByRole("combobox", { name: /Người phụ trách/ })).toBeDisabled();

    await user.click(within(dialog).getByRole("button", { name: "Thử lại" }));
    await waitFor(() => {
      expect(listActiveUsersMock).toHaveBeenCalledTimes(2);
    });
    await waitFor(() => {
      expect(within(dialog).getByRole("combobox", { name: /Người phụ trách/ })).not.toBeDisabled();
    });
  });
});

describe("fetchDefaultActiveUsers", () => {
  it("gộp toàn bộ trang khi tổng người dùng > 100", async () => {
    const fetchPage = vi.fn(async (query: ListUsersQuery): Promise<PageResponseUserSummary> => {
      const page = query.page ?? 0;
      const size = query.size ?? 20;
      // 150 users → 2 trang (100 + 50)
      const total = 150;
      const items = Array.from({ length: Math.min(size, total - page * size) }, (_, i) =>
        makeUser(page * size + i + 1),
      );
      return {
        items,
        page,
        size,
        totalItems: total,
        totalPages: Math.ceil(total / size),
      };
    });

    const result = await fetchDefaultActiveUsers(fetchPage);
    expect(result).toHaveLength(150);
    expect(fetchPage).toHaveBeenCalledTimes(2);
    expect(fetchPage).toHaveBeenNthCalledWith(
      1,
      expect.objectContaining({ status: "ACTIVE", page: 0, size: 100 }),
    );
    expect(fetchPage).toHaveBeenNthCalledWith(
      2,
      expect.objectContaining({ status: "ACTIVE", page: 1, size: 100 }),
    );
  });

  it("chỉ gọi 1 lần khi tổng ≤ 100", async () => {
    const fetchPage = vi.fn(async (query: ListUsersQuery): Promise<PageResponseUserSummary> => {
      const page = query.page ?? 0;
      const size = query.size ?? 20;
      return {
        items: [makeUser(1)],
        page,
        size,
        totalItems: 1,
        totalPages: 1,
      };
    });

    const result = await fetchDefaultActiveUsers(fetchPage);
    expect(result).toHaveLength(1);
    expect(fetchPage).toHaveBeenCalledTimes(1);
  });
});
