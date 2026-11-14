import { screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import type * as SessionApi from "@/features/session/api";
import { SessionProvider } from "@/features/session/session-provider";
import { TEST_ADMIN_SESSION } from "@/test/fixtures";
import { renderWithProviders } from "@/test/render";
import { AppShell } from "./app-shell";
import { useSetBreadcrumbLeaf } from "./breadcrumb-leaf";
import { initialsOf } from "./user-menu";

const mocks = vi.hoisted(() => ({
  pathname: { current: "/" },
  logout: vi.fn(),
}));

vi.mock("next/navigation", () => ({
  usePathname: () => mocks.pathname.current,
}));
vi.mock("@/features/session/api", async (importOriginal) => {
  const actual = await importOriginal<typeof SessionApi>();
  return { ...actual, logout: mocks.logout };
});
beforeEach(() => {
  mocks.pathname.current = "/";
});

afterEach(() => {
  mocks.logout.mockReset();
});

function LeafPage({ label }: { label: string | undefined }) {
  useSetBreadcrumbLeaf(label);
  return <p>Trang con</p>;
}

describe("initialsOf", () => {
  it("ghép chữ cái đầu của từ đầu và từ cuối (bỏ qua từ số), hoặc hai chữ cái đầu khi chỉ có một từ", () => {
    expect(initialsOf("Quản trị nội dung", "content.admin")).toBe("QD");
    expect(initialsOf("Vận hành", "ops.admin")).toBe("VH");
    expect(initialsOf("Vận hành CSKH", "ops.admin")).toBe("VC");
    expect(initialsOf("Học viên 01", "student01")).toBe("HV");
    expect(initialsOf("", "ops.admin")).toBe("OA");
    expect(initialsOf("", "platform")).toBe("PL");
  });
});

describe("AppShell", () => {
  it("hiện sidebar, breadcrumb Tổng quan › Bảng điều khiển, và menu avatar có Đổi mật khẩu, Đăng xuất (ẩn Hồ sơ)", async () => {
    renderWithProviders(
      <SessionProvider loadSession={async () => TEST_ADMIN_SESSION}>
        <AppShell skipLabel="Chuyển tới nội dung chính">
          <p>Nội dung trang</p>
        </AppShell>
      </SessionProvider>,
    );

    const nav = screen.getByRole("navigation", { name: "Menu chính" });
    const dashboardLink = await waitFor(() =>
      screen.getByRole("link", { name: "Bảng điều khiển" }),
    );
    expect(nav).toContainElement(dashboardLink);
    expect(dashboardLink).toHaveAttribute("aria-current", "page");
    expect(dashboardLink).toHaveClass("before:w-0.5", "before:bg-primary-text");

    const breadcrumb = screen.getByRole("navigation", { name: "Vị trí hiện tại" });
    expect(breadcrumb).toHaveTextContent("Tổng quan");
    expect(breadcrumb).toHaveTextContent("Bảng điều khiển");

    const banner = screen.getByRole("banner");
    const themeButton = within(banner).getByRole("button", { name: "Chuyển sang chế độ tối" });
    const localeButton = within(banner).getByRole("button", { name: "Chọn ngôn ngữ" });
    const trigger = within(banner).getByRole("button", { name: "Menu tài khoản" });
    expect(themeButton.compareDocumentPosition(localeButton) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy();
    expect(localeButton.compareDocumentPosition(trigger) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy();
    expect(themeButton.compareDocumentPosition(trigger) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy();
    expect(trigger).toHaveTextContent("ops.admin");
    await userEvent.click(trigger);

    expect(screen.getByRole("menuitem", { name: "Trang quản trị" })).toHaveAttribute("href", "/");
    expect(screen.getByRole("menuitem", { name: "Trang học viên" })).toHaveAttribute(
      "href",
      "http://localhost:3000/home",
    );
    expect(screen.queryByRole("menuitem", { name: "Hồ sơ" })).toBeNull();
    expect(screen.getByRole("menuitem", { name: "Đổi mật khẩu" })).toHaveAttribute(
      "href",
      "http://localhost:3000/change-password",
    );

    await userEvent.click(screen.getByRole("menuitem", { name: "Đăng xuất" }));

    await waitFor(() => expect(mocks.logout).toHaveBeenCalledTimes(1));
  });

  it("cửa sổ hẹp: vùng nội dung cuộn ngang còn sidebar nằm ngoài vùng cuộn và không bị co", async () => {
    // jsdom không dàn trang nên chỉ kiểm cấu trúc: nội dung trong vùng cuộn ngang, sidebar thì không.
    renderWithProviders(
      <SessionProvider loadSession={async () => TEST_ADMIN_SESSION}>
        <AppShell skipLabel="Chuyển tới nội dung chính">
          <p>Nội dung trang</p>
        </AppShell>
      </SessionProvider>,
    );

    const scroller = screen.getByTestId("content-scroll");
    expect(scroller).toHaveClass("overflow-x-auto", "min-w-0");
    expect(scroller).toContainElement(screen.getByRole("main"));
    const nav = screen.getByRole("navigation", { name: "Menu chính" });
    expect(scroller).not.toContainElement(nav);
    expect(nav).toHaveClass("w-62", "shrink-0");
    await waitFor(() => screen.getByRole("link", { name: "Bảng điều khiển" }));
  });

  it("sidebar dính đầu trang, cao hết viewport và tự cuộn khi menu dài hơn", async () => {
    renderWithProviders(
      <SessionProvider loadSession={async () => TEST_ADMIN_SESSION}>
        <AppShell skipLabel="Chuyển tới nội dung chính">
          <p>Nội dung trang dài</p>
        </AppShell>
      </SessionProvider>,
    );

    const nav = await waitFor(() => screen.getByRole("navigation", { name: "Menu chính" }));
    expect(nav).toHaveClass("sticky", "top-0", "h-dvh", "overflow-y-auto", "w-62");
  });

  it("trang ngoài menu không có breadcrumb: menu avatar vẫn nằm bên phải thanh trên", async () => {
    mocks.pathname.current = "/trang-khong-co-trong-menu";
    renderWithProviders(
      <SessionProvider loadSession={async () => TEST_ADMIN_SESSION}>
        <AppShell skipLabel="Chuyển tới nội dung chính">
          <p>Nội dung trang</p>
        </AppShell>
      </SessionProvider>,
    );

    expect(screen.queryByRole("navigation", { name: "Vị trí hiện tại" })).not.toBeInTheDocument();
    const trigger = await screen.findByRole("button", { name: "Menu tài khoản" });
    // jsdom không dàn trang: `justify-end` là thứ giữ menu bên phải khi chỉ còn một phần tử.
    expect(screen.getByRole("banner")).toHaveClass("justify-end");
    expect(screen.getByRole("banner")).toContainElement(trigger);
  });

  it("trang con cung cấp mục cuối: breadcrumb Người dùng › Lớp học (liên kết) › mã lớp, gỡ mục cuối khi trang rời đi", async () => {
    mocks.pathname.current = "/classes/abc";
    const { rerender } = renderWithProviders(
      <SessionProvider loadSession={async () => TEST_ADMIN_SESSION}>
        <AppShell skipLabel="Chuyển tới nội dung chính">
          <LeafPage label="LOP-A" />
        </AppShell>
      </SessionProvider>,
    );

    const breadcrumb = await screen.findByRole("navigation", { name: "Vị trí hiện tại" });
    await waitFor(() => expect(breadcrumb).toHaveTextContent("LOP-A"));
    expect(breadcrumb).toHaveTextContent("Người dùng");
    expect(within(breadcrumb).getByRole("link", { name: "Lớp học" })).toHaveAttribute(
      "href",
      "/classes",
    );
    expect(within(breadcrumb).getByText("LOP-A")).toHaveAttribute("aria-current", "page");

    rerender(
      <SessionProvider loadSession={async () => TEST_ADMIN_SESSION}>
        <AppShell skipLabel="Chuyển tới nội dung chính">
          <LeafPage label={undefined} />
        </AppShell>
      </SessionProvider>,
    );
    await waitFor(() => expect(breadcrumb).not.toHaveTextContent("LOP-A"));
    expect(within(breadcrumb).queryByRole("link", { name: "Lớp học" })).toBeNull();
    expect(within(breadcrumb).getByText("Lớp học")).toHaveAttribute("aria-current", "page");
  });
});
