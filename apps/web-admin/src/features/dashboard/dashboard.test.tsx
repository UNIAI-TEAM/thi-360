import { screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { Home } from "lucide-react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import type { SessionLoader } from "@/features/session/api";
import { FAKE_SESSION } from "@/features/session/fake-session";
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
    loadMock.mockResolvedValueOnce(FAKE_SESSION);
    renderDashboard();

    const retry = await screen.findByRole("button", { name: "Thử lại" });
    await userEvent.click(retry);

    expect(await screen.findByRole("heading", { name: "Xin chào, Vận hành" })).toBeInTheDocument();
  });

  it("rỗng (không có mục lối tắt nào): chỉ hiện lời chào, không hiện khối Lối tắt", async () => {
    loadMock.mockResolvedValue(FAKE_SESSION);
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
    loadMock.mockResolvedValue({ ...FAKE_SESSION, permissions: ["USER.VIEW"] });
    renderDashboard();

    expect(await screen.findByRole("heading", { name: "Lối tắt" })).toBeInTheDocument();
    expect(screen.getByRole("link", { name: /Bảng điều khiển/ })).toHaveAttribute("href", "/a");
    expect(screen.queryByRole("link", { name: /forbiddenItem/ })).not.toBeInTheDocument();
  });
});
