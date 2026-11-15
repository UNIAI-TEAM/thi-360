import { screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { Home } from "lucide-react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import type { SessionLoader } from "@/features/session/api";
import { TEST_ADMIN_SESSION } from "@/test/fixtures";
import { SessionProvider } from "@/features/session/session-provider";
import type { Session } from "@/features/session/types";
import { ApiStatusError } from "@/lib/api/query-client";
import type { NavGroup } from "@/lib/navigation";
import { renderWithProviders } from "@/test/render";
import { DashboardView } from "./dashboard-view";

const navMock = vi.hoisted(() => ({ groups: [] as readonly NavGroup[] }));

vi.mock("@/lib/navigation", async (importOriginal) => {
  const actual = await importOriginal<typeof import("@/lib/navigation")>();
  return {
    ...actual,
    get NAVIGATION() {
      return navMock.groups;
    },
  };
});

const loadMock = vi.fn<SessionLoader>();

function renderDashboard() {
  return renderWithProviders(
    <SessionProvider loadSession={loadMock}>
      <DashboardView />
    </SessionProvider>,
  );
}

beforeEach(() => {
  navMock.groups = [
    {
      id: "overview",
      items: [{ id: "dashboard", href: "/", icon: Home, permission: null, excludeFromShortcuts: true }],
    },
  ];
});

afterEach(() => {
  loadMock.mockReset();
});

describe("DashboardView: bốn trạng thái", () => {
  it("đang tải: hiện trạng thái tải, chưa hiện lời chào", () => {
    loadMock.mockReturnValue(Promise.withResolvers<Session>().promise);
    renderDashboard();

    expect(screen.getByRole("status", { name: "Đang tải" })).toBeInTheDocument();
    expect(screen.queryByRole("heading")).not.toBeInTheDocument();
  });

  it("lỗi: hiện thông báo và nút Thử lại nạp lại phiên", async () => {
    loadMock.mockRejectedValueOnce(new ApiStatusError(500));
    loadMock.mockResolvedValueOnce(TEST_ADMIN_SESSION);
    renderDashboard();

    const retry = await screen.findByRole("button", { name: "Thử lại" });
    await userEvent.click(retry);

    expect(await screen.findByRole("heading", { name: "Xin chào, Vận hành" })).toBeInTheDocument();
  });

  it("rỗng (không có mục lối tắt nào): chỉ hiện lời chào, không hiện khối Lối tắt", async () => {
    loadMock.mockResolvedValue(TEST_ADMIN_SESSION);
    renderDashboard();

    expect(await screen.findByRole("heading", { name: "Xin chào, Vận hành" })).toBeInTheDocument();
    expect(screen.queryByRole("heading", { name: "Lối tắt" })).not.toBeInTheDocument();
  });

  it("có dữ liệu: chỉ hiện thẻ lối tắt của mục tài khoản có quyền", async () => {
    navMock.groups = [
      {
        id: "users",
        items: [
          { id: "dashboard", href: "/a", icon: Home, permission: "USER.VIEW" },
          { id: "forbiddenItem", href: "/b", icon: Home, permission: "MISSING.CODE" },
        ],
      },
    ];
    loadMock.mockResolvedValue({ ...TEST_ADMIN_SESSION, permissions: ["USER.VIEW"] });
    renderDashboard();

    expect(await screen.findByRole("heading", { name: "Lối tắt" })).toBeInTheDocument();
    const shortcut = screen.getByRole("link", { name: /Bảng điều khiển/ });
    expect(shortcut).toHaveAttribute("href", "/a");
    // Vòng focus dùng chung của kit, không tự viết vòng mờ.
    expect(shortcut).toHaveClass("focus-visible:ring-primary-text");
    expect(shortcut.className).not.toContain("ring-primary-600/30");
    expect(screen.queryByRole("link", { name: /forbiddenItem/ })).not.toBeInTheDocument();

    // UNI-2586: thẻ lối tắt co theo vùng nội dung; dưới 260px (cửa sổ 360px) một thẻ chiếm hết chiều ngang.
    const list = shortcut.closest("ul")!;
    expect(list).toHaveClass("grid-cols-[repeat(auto-fit,minmax(min(16.25rem,100%),1fr))]");
    expect(list.className).not.toContain("minmax(16.25rem,1fr)");
  });
});
