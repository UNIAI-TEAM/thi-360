import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { APP_TIME_ZONE, INTL_FORMATS, resolveIntlLocale, type AppLocale } from "@thi360/web-kit/preferences";
import { NextIntlClientProvider } from "next-intl";
import { useState } from "react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import type * as SessionApi from "@/features/session/api";
import { ApiStatusError } from "@/lib/api/client";
import en_messages from "@/messages/en.json";
import vi_messages from "@/messages/vi.json";
import { PreferencesProvider } from "@/providers/preferences-provider";
import { QueryProvider } from "@/providers/query-provider";
import { renderWithProviders } from "@/test/render";
import { LoginView } from "./login-view";
const mocks = vi.hoisted(() => ({
  callbackUrl: null as string | null,
  reason: null as string | null,
  loginWithPassword: vi.fn(),
  navigate: vi.fn(),
  refresh: vi.fn(),
  replace: vi.fn(),
}));

vi.mock("next/navigation", () => ({
  usePathname: () => "/dang-nhap",
  useRouter: () => ({ refresh: mocks.refresh, replace: mocks.replace }),
  useSearchParams: () =>
    new URLSearchParams({
      ...(mocks.callbackUrl ? { callbackUrl: mocks.callbackUrl } : {}),
      ...(mocks.reason ? { reason: mocks.reason } : {}),
    }),
}));

vi.mock("@/features/session/api", async () => {
  const actual = await vi.importActual<typeof SessionApi>("@/features/session/api");
  return {
    ...actual,
    loginWithPassword: mocks.loginWithPassword,
  };
});

beforeEach(() => {
  mocks.callbackUrl = null;
  mocks.reason = null;
});

afterEach(() => {
  mocks.loginWithPassword.mockReset();
  mocks.navigate.mockReset();
  mocks.refresh.mockReset();
  mocks.replace.mockReset();
  document.cookie = "thi360_locale=; Max-Age=0; Path=/";
});

describe("LoginView", () => {
  it("gửi identifier, password, callbackUrl và chuyển hướng theo redirectTo", async () => {
    mocks.callbackUrl = "http://localhost:3001/users";
    mocks.loginWithPassword.mockResolvedValue({
      redirectTo: "http://localhost:3001/users",
      mustChangePassword: false,
    });

    renderWithProviders(<LoginView navigate={mocks.navigate} />);

    expect(screen.queryByRole("button", { name: /google/i })).toBeNull();

    await userEvent.type(screen.getByLabelText(/Tên đăng nhập hoặc email/), "ops.admin");
    await userEvent.type(screen.getByLabelText(/Mật khẩu/), "Thi360@dev");
    await userEvent.click(screen.getByRole("button", { name: "Đăng nhập" }));

    await waitFor(() =>
      expect(mocks.loginWithPassword).toHaveBeenCalledWith({
        identifier: "ops.admin",
        password: "Thi360@dev",
        callbackUrl: "http://localhost:3001/users",
      }),
    );
    expect(mocks.navigate).toHaveBeenCalledWith("http://localhost:3001/users");
  });

  it.each([
    ["INVALID_CREDENTIALS", "Tên đăng nhập hoặc mật khẩu không đúng."],
    ["LOGIN_THROTTLED", "Bạn đã nhập sai quá nhiều lần. Vui lòng thử lại sau 15 phút."],
    ["ACCOUNT_INACTIVE", "Tài khoản đã ngừng hoạt động. Vui lòng liên hệ quản trị viên."],
    ["SESSION_REVOKED", "Không thể đăng nhập. Vui lòng thử lại."],
  ])("hiển thị thông báo lỗi đúng cho mã %s và giữ nguyên tên đăng nhập", async (code, expected) => {
    mocks.loginWithPassword.mockRejectedValue(new ApiStatusError(401, "backend msg", { code }));

    renderWithProviders(<LoginView navigate={mocks.navigate} />);

    const identifierInput = screen.getByLabelText(/Tên đăng nhập hoặc email/);
    await userEvent.type(identifierInput, "student01");
    await userEvent.type(screen.getByLabelText(/Mật khẩu/), "wrong");
    await userEvent.click(screen.getByRole("button", { name: "Đăng nhập" }));

    const alert = await screen.findByRole("alert");
    expect(alert).toHaveTextContent(expected);
    expect(alert).not.toHaveTextContent("backend msg");
    expect(screen.queryByText(/backend msg/)).not.toBeInTheDocument();
    expect(identifierInput).toHaveValue("student01");
  });

  it("dùng nhãn Show/Hide password theo ngôn ngữ tiếng Anh cho PasswordField", async () => {
    render(
      <NextIntlClientProvider locale="en" messages={en_messages} timeZone="Asia/Ho_Chi_Minh">
        <LoginView navigate={mocks.navigate} />
      </NextIntlClientProvider>,
    );

    const toggle = screen.getByRole("button", { name: "Show password" });
    await userEvent.click(toggle);
    expect(screen.getByRole("button", { name: "Hide password" })).toBeInTheDocument();
  });

  it("đổi ngôn ngữ bằng bàn phím dưới PreferencesProvider + QueryProvider giữ nguyên giá trị đang nhập và giữ focus ở nút chọn ngôn ngữ", async () => {
    const user = userEvent.setup();
    function RootHarness() {
      const [locale, setLocale] = useState<AppLocale>("vi");
      mocks.refresh.mockImplementation(() => setLocale("en"));
      return (
        <NextIntlClientProvider
          locale={resolveIntlLocale(locale)}
          messages={locale === "en" ? en_messages : vi_messages}
          timeZone={APP_TIME_ZONE}
          formats={INTL_FORMATS}
        >
          <PreferencesProvider locale={locale} initialTheme="light">
            <QueryProvider navigate={mocks.navigate}>
              <LoginView navigate={mocks.navigate} />
            </QueryProvider>
          </PreferencesProvider>
        </NextIntlClientProvider>
      );
    }

    render(<RootHarness />);

    const identifierInput = screen.getByLabelText(/Tên đăng nhập hoặc email/);
    await user.type(identifierInput, "ops.admin");
    expect(identifierInput).toHaveValue("ops.admin");

    const switcher = screen.getByRole("button", { name: "Chọn ngôn ngữ" });
    switcher.focus();
    expect(switcher).toHaveFocus();

    await user.keyboard("{Enter}");
    const englishItem = await screen.findByRole("menuitem", { name: "English" });
    englishItem.focus();
    await user.keyboard("{Enter}");

    await waitFor(() => expect(screen.getByRole("heading", { name: "Sign in" })).toBeInTheDocument());
    expect(document.cookie).toContain("thi360_locale=en");
    expect(screen.getByLabelText(/Username or email/)).toBe(identifierInput);
    expect(identifierInput).toHaveValue("ops.admin");
    const updatedSwitcher = screen.getByRole("button", { name: "Change language" });
    expect(updatedSwitcher).toBe(switcher);
    expect(updatedSwitcher).toHaveFocus();
  });
});
