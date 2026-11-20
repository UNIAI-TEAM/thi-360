import { screen, within } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import enMessages from "@/messages/en.json";
import { renderWithProviders } from "@/test/render";
import { StudentHeader } from "./student-header";

const nav = vi.hoisted(() => ({ pathname: "/" }));
vi.mock("next/navigation", () => ({ usePathname: () => nav.pathname }));

const STUDENT = {
  id: "u-stu",
  username: "student01",
  fullName: "Học viên 01",
  realm: "APP" as const,
  access: "EXAM" as const,
  permissions: [],
  homeUrl: "http://localhost:3000/home",
};

describe("StudentHeader", () => {
  it("khách thấy Đăng nhập, Đăng ký; không có điều hướng; nút giao diện và ngôn ngữ còn đó (AC-14)", () => {
    nav.pathname = "/";
    renderWithProviders(<StudentHeader account={{ kind: "guest" }} />);

    const banner = screen.getByRole("banner");
    expect(within(banner).getByRole("link", { name: "Đăng nhập" })).toHaveAttribute("href", "/login");
    expect(within(banner).getByRole("link", { name: "Đăng ký" })).toHaveAttribute("href", "/register");
    expect(within(banner).queryByRole("navigation")).toBeNull();
    expect(within(banner).queryByRole("button", { name: "Menu tài khoản" })).toBeNull();
    expect(within(banner).getByRole("button", { name: "Chuyển sang chế độ tối" })).toBeInTheDocument();
    expect(within(banner).getByRole("button", { name: "Chọn ngôn ngữ" })).toBeInTheDocument();
  });

  it("khách ở trang con: Đăng nhập, Đăng ký mang callbackUrl về đúng trang đó", () => {
    nav.pathname = "/exams/HSA-FULL-01";
    renderWithProviders(<StudentHeader account={{ kind: "guest" }} />);

    expect(screen.getByRole("link", { name: "Đăng nhập" })).toHaveAttribute(
      "href",
      "/login?callbackUrl=%2Fexams%2FHSA-FULL-01",
    );
    expect(screen.getByRole("link", { name: "Đăng ký" })).toHaveAttribute(
      "href",
      "/register?callbackUrl=%2Fexams%2FHSA-FULL-01",
    );
  });

  it("đã đăng nhập thấy Bài thi của tôi, Hồ sơ và menu tài khoản; Lịch sử, Đơn của tôi nằm trong danh sách ẩn (AC-14)", () => {
    nav.pathname = "/home";
    renderWithProviders(<StudentHeader account={{ kind: "user", session: STUDENT }} />);

    const banner = screen.getByRole("banner");
    const navigation = within(banner).getByRole("navigation", { name: "Điều hướng chính" });
    const links = within(navigation).getAllByRole("link");
    expect(links.map((link) => [link.textContent, link.getAttribute("href")])).toEqual([
      ["Bài thi của tôi", "/home"],
      ["Hồ sơ", "/account/profile"],
    ]);
    expect(within(navigation).getByRole("link", { name: "Bài thi của tôi" })).toHaveAttribute("aria-current", "page");
    expect(within(navigation).getByRole("link", { name: "Hồ sơ" })).not.toHaveAttribute("aria-current");
    expect(within(banner).queryByRole("link", { name: "Lịch sử" })).toBeNull();
    expect(within(banner).queryByRole("link", { name: "Đơn của tôi" })).toBeNull();
    expect(within(banner).getByRole("button", { name: "Menu tài khoản" })).toBeInTheDocument();
    expect(within(banner).queryByRole("link", { name: "Đăng nhập" })).toBeNull();
    expect(within(banner).queryByRole("link", { name: "Đăng ký" })).toBeNull();
    expect(within(banner).getByRole("button", { name: "Chuyển sang chế độ tối" })).toBeInTheDocument();
    expect(within(banner).getByRole("button", { name: "Chọn ngôn ngữ" })).toBeInTheDocument();
  });

  it("tài khoản quản trị chỉ có menu tài khoản, không có điều hướng dẫn tới trang của học viên (R1-9)", () => {
    nav.pathname = "/";
    renderWithProviders(
      <StudentHeader
        account={{ kind: "user", session: { ...STUDENT, access: "MANAGE", permissions: ["USER.VIEW"] } }}
      />,
    );

    const banner = screen.getByRole("banner");
    expect(within(banner).queryByRole("navigation")).toBeNull();
    expect(within(banner).queryByRole("link", { name: "Bài thi của tôi" })).toBeNull();
    expect(within(banner).queryByRole("link", { name: "Hồ sơ" })).toBeNull();
    expect(within(banner).getByRole("button", { name: "Menu tài khoản" })).toBeInTheDocument();
  });

  it("mục điều hướng đánh dấu trang hiện tại cả với trang con", () => {
    nav.pathname = "/account/profile/avatar";
    renderWithProviders(<StudentHeader account={{ kind: "user", session: STUDENT }} />);

    expect(screen.getByRole("link", { name: "Hồ sơ" })).toHaveAttribute("aria-current", "page");
    expect(screen.getByRole("link", { name: "Bài thi của tôi" })).not.toHaveAttribute("aria-current");
  });

  it("phiên đang tải giữ chỗ nút tài khoản, chưa hiện Đăng nhập hay điều hướng", () => {
    nav.pathname = "/";
    renderWithProviders(<StudentHeader account={{ kind: "loading" }} />);

    expect(screen.getByTestId("user-menu-placeholder")).toBeInTheDocument();
    expect(screen.queryByRole("link", { name: "Đăng nhập" })).toBeNull();
    expect(screen.queryByRole("navigation")).toBeNull();
    expect(screen.getByRole("button", { name: "Chọn ngôn ngữ" })).toBeInTheDocument();
  });

  it("tiếng Anh: nhãn lấy từ file dịch, không lẫn tiếng Việt", () => {
    nav.pathname = "/";
    const view = renderWithProviders(<StudentHeader account={{ kind: "guest" }} />, {
      locale: "en",
      messages: enMessages,
    });
    expect(screen.getByRole("link", { name: "Sign in" })).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Register" })).toBeInTheDocument();
    view.unmount();

    renderWithProviders(<StudentHeader account={{ kind: "user", session: STUDENT }} />, {
      locale: "en",
      messages: enMessages,
    });
    expect(screen.getByRole("navigation", { name: "Main navigation" })).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "My exams" })).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Profile" })).toBeInTheDocument();
  });
});
