import { screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { FAKE_SESSION } from "@/features/session/fake-session";
import { SessionProvider } from "@/features/session/session-provider";
import { renderWithProviders } from "@/test/render";
import { AppShell } from "./app-shell";
import { initialsOf } from "./user-menu";

const mocks = vi.hoisted(() => ({
  pathname: { current: "/" },
  redirectToLogin: vi.fn(),
}));

vi.mock("next/navigation", () => ({
  usePathname: () => mocks.pathname.current,
}));
vi.mock("@/lib/auth-redirect", () => ({ redirectToLogin: mocks.redirectToLogin }));

beforeEach(() => {
  mocks.pathname.current = "/";
});

afterEach(() => {
  mocks.redirectToLogin.mockReset();
});

describe("initialsOf", () => {
  it("ghép chữ cái đầu của từ đầu và từ cuối, hoặc hai chữ cái đầu khi chỉ có một từ", () => {
    expect(initialsOf("Quản trị nội dung", "content.admin")).toBe("QD");
    expect(initialsOf("Vận hành", "ops.admin")).toBe("VH");
    expect(initialsOf("", "ops.admin")).toBe("OA");
    expect(initialsOf("", "platform")).toBe("PL");
  });
});

describe("AppShell", () => {
  it("hiện sidebar, breadcrumb Tổng quan › Bảng điều khiển, và menu avatar có Hồ sơ, Đổi mật khẩu, Đăng xuất", async () => {
    renderWithProviders(
      <SessionProvider loadSession={async () => FAKE_SESSION}>
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

    const breadcrumb = screen.getByRole("navigation", { name: "Vị trí hiện tại" });
    expect(breadcrumb).toHaveTextContent("Tổng quan");
    expect(breadcrumb).toHaveTextContent("Bảng điều khiển");

    const trigger = screen.getByRole("button", { name: "Menu tài khoản" });
    expect(trigger).toHaveTextContent("ops.admin");
    await userEvent.click(trigger);

    expect(screen.getByRole("menuitem", { name: "Hồ sơ" })).toHaveAttribute(
      "href",
      "http://localhost:3000/ho-so",
    );
    expect(screen.getByRole("menuitem", { name: "Đổi mật khẩu" })).toHaveAttribute(
      "href",
      "http://localhost:3000/doi-mat-khau",
    );

    await userEvent.click(screen.getByRole("menuitem", { name: "Đăng xuất" }));

    await waitFor(() =>
      expect(mocks.redirectToLogin).toHaveBeenCalledWith({ returnHere: false }),
    );
  });

  it("cửa sổ hẹp: vùng nội dung cuộn ngang còn sidebar nằm ngoài vùng cuộn và không bị co", async () => {
    // jsdom không dàn trang nên chỉ kiểm cấu trúc: nội dung trong vùng cuộn ngang, sidebar thì không.
    renderWithProviders(
      <SessionProvider loadSession={async () => FAKE_SESSION}>
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
      <SessionProvider loadSession={async () => FAKE_SESSION}>
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
      <SessionProvider loadSession={async () => FAKE_SESSION}>
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
});
