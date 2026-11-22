import { screen } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { LoginView } from "@/features/auth/login-view";
import { renderWithProviders } from "@/test/render";
import { withCallbackUrl } from "./account-links";
import { ForgotPasswordView } from "./forgot-password-view";
import { RegisterView } from "./register-view";
import { VerifyEmailView } from "./verify-email-view";

const mocks = vi.hoisted(() => ({ callbackUrl: null as string | null }));

vi.mock("next/navigation", () => ({
  usePathname: () => "/login",
  useRouter: () => ({ refresh: vi.fn(), replace: vi.fn() }),
  useSearchParams: () => new URLSearchParams(mocks.callbackUrl ? { callbackUrl: mocks.callbackUrl } : {}),
}));

beforeEach(() => {
  mocks.callbackUrl = null;
});

const CHECKOUT = "/checkout/BCA-FULL";
const encoded = (value: string) => encodeURIComponent(value);

describe("withCallbackUrl", () => {
  it("giữ callbackUrl tương đối và callbackUrl thuộc tên miền hệ thống", () => {
    expect(withCallbackUrl("/register", CHECKOUT)).toBe(`/register?callbackUrl=${encoded(CHECKOUT)}`);
    expect(withCallbackUrl("/register", "http://localhost:3001/users")).toBe(
      `/register?callbackUrl=${encoded("http://localhost:3001/users")}`,
    );
  });

  it.each([
    ["https://trang-la.example/x"],
    ["//trang-la.example"],
    ["/\\trang-la.example"],
    ["javascript:alert(1)"],
    [""],
    [null],
    [undefined],
  ])("bỏ callbackUrl ngoài hệ thống hoặc méo: %s", (raw) => {
    expect(withCallbackUrl("/register", raw)).toBe("/register");
  });
});

describe("chuyển qua lại /login, /register, /forgot-password giữ callbackUrl (AC-7)", () => {
  it("/login → /register và /forgot-password", () => {
    mocks.callbackUrl = CHECKOUT;
    renderWithProviders(<LoginView />);

    expect(screen.getByRole("link", { name: "Đăng ký" })).toHaveAttribute(
      "href",
      `/register?callbackUrl=${encoded(CHECKOUT)}`,
    );
    expect(screen.getByRole("link", { name: "Quên mật khẩu?" })).toHaveAttribute(
      "href",
      `/forgot-password?callbackUrl=${encoded(CHECKOUT)}`,
    );
  });

  it("/register → /login", () => {
    mocks.callbackUrl = CHECKOUT;
    renderWithProviders(<RegisterView />);

    expect(screen.getByRole("link", { name: "Đăng nhập" })).toHaveAttribute(
      "href",
      `/login?callbackUrl=${encoded(CHECKOUT)}`,
    );
  });

  it("/forgot-password → /login và /register", () => {
    mocks.callbackUrl = CHECKOUT;
    renderWithProviders(<ForgotPasswordView />);

    expect(screen.getByRole("link", { name: /Quay lại đăng nhập/ })).toHaveAttribute(
      "href",
      `/login?callbackUrl=${encoded(CHECKOUT)}`,
    );
    expect(screen.getByRole("link", { name: "Tạo tài khoản mới" })).toHaveAttribute(
      "href",
      `/register?callbackUrl=${encoded(CHECKOUT)}`,
    );
  });

  it("callbackUrl ngoài hệ thống bị bỏ ở mọi liên kết", () => {
    mocks.callbackUrl = "https://trang-la.example/x";
    renderWithProviders(<LoginView />);

    expect(screen.getByRole("link", { name: "Đăng ký" })).toHaveAttribute("href", "/register");
    expect(screen.getByRole("link", { name: "Quên mật khẩu?" })).toHaveAttribute("href", "/forgot-password");
  });

  it("nút Để sau ở /verify-email đi theo callbackUrl hợp lệ, ngoài hệ thống thì về /home", async () => {
    mocks.callbackUrl = CHECKOUT;
    const profile = {
      username: "hocvien01",
      fullName: "An",
      email: "an@thi360.vn",
      emailVerified: false,
    };
    const first = renderWithProviders(<VerifyEmailView loadProfileApi={async () => profile} />);
    expect(await screen.findByRole("link", { name: "Để sau" })).toHaveAttribute("href", CHECKOUT);
    first.unmount();

    mocks.callbackUrl = "https://trang-la.example/x";
    renderWithProviders(<VerifyEmailView loadProfileApi={async () => profile} />);
    expect(await screen.findByRole("link", { name: "Để sau" })).toHaveAttribute("href", "/home");
  });
});
