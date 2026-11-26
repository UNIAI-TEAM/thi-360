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
    // Dấu hiệu mục đang mở vẽ bằng viền để còn thấy ở chế độ tương phản cao; mục khác không có thanh này.
    expect(dashboardLink).toHaveClass("before:border-l-2", "before:border-primary-text");
    expect(dashboardLink.className).not.toContain("before:bg-");
    const otherLink = screen.getByRole("link", { name: "Tài khoản" });
    expect(otherLink).not.toHaveAttribute("aria-current");
    expect(otherLink.className).not.toContain("before:border-l-2");
    // Vòng focus dùng chung của kit (màu đặc `primary-text`), không tự viết vòng mờ 30%.
    expect(dashboardLink).toHaveClass("focus-visible:ring-primary-text");
    expect(dashboardLink.className).not.toContain("ring-primary-600/30");

    // Mỗi nhóm menu là một danh sách có tên; liên kết là mục của danh sách đó.
    const usersGroup = within(nav).getByRole("list", { name: "Người dùng" });
    expect(within(usersGroup).getAllByRole("listitem")).toHaveLength(2);
    expect(usersGroup).toContainElement(otherLink);
    expect(within(nav).getByRole("list", { name: "Tổng quan" })).toContainElement(dashboardLink);
    expect(within(nav).getAllByRole("list")).toHaveLength(4);

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
    expect(trigger).toHaveClass("focus-visible:ring-primary-text");
    expect(trigger.className).not.toContain("ring-primary-600/30");
    await userEvent.click(trigger);

    expect(screen.getByRole("menuitem", { name: "Trang quản trị" })).toHaveAttribute("href", "/");
    expect(screen.getByRole("menuitem", { name: "Trang học viên" })).toHaveAttribute(
      "href",
      "http://localhost:3000/home",
    );
    expect(screen.queryByRole("menuitem", { name: "Hồ sơ" })).toBeNull();
    expect(screen.getByRole("menuitem", { name: "Đổi mật khẩu" })).toHaveAttribute(
      "href",
      "http://localhost:3000/change-password?source=account-menu",
    );

    await userEvent.click(screen.getByRole("menuitem", { name: "Đăng xuất" }));

    await waitFor(() => expect(mocks.logout).toHaveBeenCalledTimes(1));
  });

  it("dưới lg: sidebar ẩn, cột nội dung co theo cửa sổ, không bề rộng tối thiểu, không tự cuộn ngang; lề hẹp dưới sm", async () => {
    // jsdom không dàn trang và không áp media query: kiểm lớp quyết định bố cục ở 360px (e2e admin-responsive đo thật).
    renderWithProviders(
      <SessionProvider loadSession={async () => TEST_ADMIN_SESSION}>
        <AppShell skipLabel="Chuyển tới nội dung chính">
          <p>Nội dung trang</p>
        </AppShell>
      </SessionProvider>,
    );

    const column = screen.getByTestId("content-column");
    expect(column).toHaveClass("min-w-0", "flex-1", "flex-col");
    expect(column.className).not.toMatch(/(^|\s)(min-w-120|overflow-x-auto|overflow-x-scroll)(\s|$)/);
    expect(column.querySelector(".min-w-120")).toBeNull();
    const main = screen.getByRole("main");
    expect(screen.getAllByRole("main")).toHaveLength(1);
    expect(column).toContainElement(main);
    expect(main).toHaveAttribute("id", "main");
    expect(main).toHaveClass("min-w-0", "p-4", "sm:p-6");
    expect(main).not.toHaveClass("p-6");
    expect(screen.getByRole("banner")).toHaveClass("px-4", "sm:px-6", "h-14");

    const nav = screen.getByRole("navigation", { name: "Menu chính" });
    expect(column).not.toContainElement(nav);
    expect(nav).toHaveClass("hidden", "lg:flex", "w-62", "shrink-0");

    // Lối tắt tới nội dung chính trỏ vào đúng `<main>`.
    expect(screen.getByRole("link", { name: "Chuyển tới nội dung chính" })).toHaveAttribute("href", "#main");
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

  it("thanh trên ở 360px: breadcrumb chỉ còn trang hiện tại, menu avatar chỉ còn avatar và tên đăng nhập hiện trong menu", async () => {
    mocks.pathname.current = "/users";
    renderWithProviders(
      <SessionProvider loadSession={async () => TEST_ADMIN_SESSION}>
        <AppShell skipLabel="Chuyển tới nội dung chính">
          <p>Nội dung trang</p>
        </AppShell>
      </SessionProvider>,
    );

    const breadcrumb = await screen.findByRole("navigation", { name: "Vị trí hiện tại" });
    const [group, current] = within(breadcrumb).getAllByRole("listitem");
    expect(group).toHaveTextContent("Người dùng");
    expect(group).toHaveClass("max-sm:hidden");
    expect(current).toHaveTextContent("Tài khoản");
    expect(current).not.toHaveClass("max-sm:hidden");
    // Nhóm trái co được để breadcrumb cắt chữ thay vì đẩy các nút bên phải ra ngoài; nhóm phải không co.
    expect(breadcrumb.parentElement).toHaveClass("flex-1", "min-w-0");

    const banner = screen.getByRole("banner");
    const trigger = await within(banner).findByRole("button", { name: "Menu tài khoản" });
    expect(trigger.parentElement).toHaveClass("shrink-0");
    expect(within(trigger).getByText("ops.admin")).toHaveClass("hidden", "sm:inline");
    expect(trigger).toHaveClass("pointer-coarse:after:absolute", "relative");

    await userEvent.click(trigger);
    expect(screen.getByText("Tài khoản: ops.admin")).toHaveClass("sm:hidden");
  });

  it("dưới lg: nút menu mở ngăn kéo trái chứa cùng menu; bấm một mục thì đóng và focus về nút menu", async () => {
    mocks.pathname.current = "/";
    renderWithProviders(
      <SessionProvider loadSession={async () => TEST_ADMIN_SESSION}>
        <AppShell skipLabel="Chuyển tới nội dung chính">
          <p>Nội dung trang</p>
        </AppShell>
      </SessionProvider>,
    );

    const banner = screen.getByRole("banner");
    const menuButton = within(banner).getByRole("button", { name: "Mở menu điều hướng" });
    expect(menuButton).toHaveClass("lg:hidden");
    expect(menuButton).toHaveAttribute("aria-expanded", "false");
    expect(menuButton).toHaveAttribute("aria-haspopup", "dialog");
    expect(menuButton).not.toHaveAttribute("aria-controls");
    // Nút menu đứng đầu thanh trên, trước breadcrumb.
    const breadcrumb = screen.getByRole("navigation", { name: "Vị trí hiện tại" });
    expect(menuButton.compareDocumentPosition(breadcrumb) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy();
    await waitFor(() => screen.getByRole("link", { name: "Bảng điều khiển" }));

    await userEvent.click(menuButton);
    const drawer = await screen.findByRole("dialog", { name: "Menu điều hướng" });
    expect(drawer).toHaveClass("left-0", "rounded-r-xl");
    expect(menuButton).toHaveAttribute("aria-expanded", "true");
    expect(menuButton).toHaveAttribute("aria-controls", drawer.id);

    // Cùng danh sách với sidebar: bốn nhóm, mục đang mở đánh dấu như nhau. Sidebar nằm sau lớp phủ (aria-hidden).
    const drawerNav = within(drawer).getByRole("navigation", { name: "Menu chính" });
    expect(screen.getAllByRole("navigation", { name: "Menu chính" })).toEqual([drawerNav]);
    expect(within(drawerNav).getAllByRole("list")).toHaveLength(4);
    expect(within(drawerNav).getByRole("link", { name: "Bảng điều khiển" })).toHaveAttribute("aria-current", "page");
    expect(within(drawer).getByText("Thi360 · Quản trị")).toBeInTheDocument();

    // jsdom không có router của Next: chặn điều hướng tải lại trang (sau khi handler của React đã chạy).
    const stopDocumentNavigation = (event: MouseEvent) => event.preventDefault();
    document.addEventListener("click", stopDocumentNavigation);
    try {
      await userEvent.click(within(drawerNav).getByRole("link", { name: "Tài khoản" }));
    } finally {
      document.removeEventListener("click", stopDocumentNavigation);
    }
    await waitFor(() => expect(screen.queryByRole("dialog")).toBeNull());
    await waitFor(() => expect(menuButton).toHaveFocus());
    expect(menuButton).toHaveAttribute("aria-expanded", "false");
  });

  it("ngăn menu đóng bằng Esc, bằng nút × và khi đổi trang; focus về nút menu", async () => {
    mocks.pathname.current = "/";
    const tree = () => (
      <SessionProvider loadSession={async () => TEST_ADMIN_SESSION}>
        <AppShell skipLabel="Chuyển tới nội dung chính">
          <p>Nội dung trang</p>
        </AppShell>
      </SessionProvider>
    );
    const { rerender } = renderWithProviders(tree());
    const menuButton = screen.getByRole("button", { name: "Mở menu điều hướng" });

    await userEvent.click(menuButton);
    await screen.findByRole("dialog", { name: "Menu điều hướng" });
    await userEvent.keyboard("{Escape}");
    await waitFor(() => expect(screen.queryByRole("dialog")).toBeNull());
    await waitFor(() => expect(menuButton).toHaveFocus());

    await userEvent.click(menuButton);
    const drawer = await screen.findByRole("dialog", { name: "Menu điều hướng" });
    await userEvent.click(within(drawer).getByRole("button", { name: "Đóng menu điều hướng" }));
    await waitFor(() => expect(screen.queryByRole("dialog")).toBeNull());
    await waitFor(() => expect(menuButton).toHaveFocus());

    // Đổi trang không qua menu (nút Lùi của trình duyệt): ngăn tự đóng.
    await userEvent.click(menuButton);
    await screen.findByRole("dialog", { name: "Menu điều hướng" });
    mocks.pathname.current = "/classes";
    rerender(tree());
    await waitFor(() => expect(screen.queryByRole("dialog")).toBeNull());
    await waitFor(() => expect(menuButton).toHaveFocus());
    expect(menuButton).toHaveAttribute("aria-expanded", "false");
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
