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
  reset: null as string | null,
  deleted: null as string | null,
  loginWithPassword: vi.fn(),
  navigate: vi.fn(),
  refresh: vi.fn(),
  replace: vi.fn(),
}));

vi.mock("next/navigation", () => ({
  usePathname: () => "/login",
  useRouter: () => ({ refresh: mocks.refresh, replace: mocks.replace }),
  useSearchParams: () =>
    new URLSearchParams({
      ...(mocks.callbackUrl ? { callbackUrl: mocks.callbackUrl } : {}),
      ...(mocks.reason ? { reason: mocks.reason } : {}),
      ...(mocks.reset ? { reset: mocks.reset } : {}),
      ...(mocks.deleted ? { deleted: mocks.deleted } : {}),
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
  mocks.reset = null;
  mocks.deleted = null;
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

  it("gửi khi thiếu mật khẩu: focus chuyển tới ô mật khẩu; thiếu cả hai thì tới ô tên đăng nhập", async () => {
    renderWithProviders(<LoginView navigate={mocks.navigate} />);

    const identifierInput = screen.getByLabelText(/Tên đăng nhập hoặc email/);
    const passwordInput = screen.getByLabelText(/Mật khẩu/);
    await userEvent.type(identifierInput, "student01");
    await userEvent.click(screen.getByRole("button", { name: "Đăng nhập" }));

    expect(await screen.findByText("Vui lòng nhập mật khẩu.")).toBeInTheDocument();
    await waitFor(() => expect(passwordInput).toHaveFocus());
    expect(mocks.loginWithPassword).not.toHaveBeenCalled();

    await userEvent.clear(identifierInput);
    await userEvent.click(screen.getByRole("button", { name: "Đăng nhập" }));
    await waitFor(() => expect(identifierInput).toHaveFocus());
  });

  it("biểu mẫu đứng trước phần giới thiệu trong DOM, tiêu đề trang là Đăng nhập và không khối nào có bề rộng tối thiểu cứng", () => {
    // jsdom không dàn trang: kiểm cấu trúc bảo đảm màn hẹp thấy ô nhập trước và không bị cắt ở 320px.
    renderWithProviders(<LoginView navigate={mocks.navigate} />);

    const title = screen.getByRole("heading", { level: 1, name: "Đăng nhập" });
    const headline = screen.getByRole("heading", { level: 2, name: "Luyện thi đánh giá năng lực HSA và QDA" });
    const submit = screen.getByRole("button", { name: "Đăng nhập" });
    expect(title.compareDocumentPosition(submit) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy();
    expect(submit.compareDocumentPosition(headline) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy();

    const formSection = title.closest("section");
    const introSection = headline.closest("section");
    expect(formSection).toHaveClass("min-w-0", "p-6");
    expect(introSection).toHaveClass("min-w-0", "p-6");
    expect(formSection?.parentElement).toHaveClass("flex-col", "md:flex-row-reverse");
    expect(screen.getByRole("main").innerHTML).not.toContain("min-w-[300px]");
  });

  it("ô tên đăng nhập và ô mật khẩu có thuộc tính cho bàn phím điện thoại, tự điền và chữ 16px ở màn hẹp", () => {
    renderWithProviders(<LoginView navigate={mocks.navigate} />);

    const identifierInput = screen.getByLabelText(/Tên đăng nhập hoặc email/);
    expect(identifierInput).toBeRequired();
    expect(identifierInput).toHaveAttribute("name", "identifier");
    expect(identifierInput).toHaveAttribute("autocomplete", "username");
    expect(identifierInput).toHaveAttribute("autocapitalize", "none");
    expect(identifierInput).toHaveAttribute("autocorrect", "off");
    expect(identifierInput).toHaveAttribute("spellcheck", "false");
    expect(identifierInput).toHaveAttribute("enterkeyhint", "next");
    expect(identifierInput).toHaveClass("max-sm:text-title", "pointer-coarse:text-title");

    const passwordInput = screen.getByLabelText(/Mật khẩu/);
    expect(passwordInput).toBeRequired();
    expect(passwordInput).toHaveAttribute("name", "password");
    expect(passwordInput).toHaveAttribute("autocomplete", "current-password");
    expect(passwordInput).toHaveAttribute("enterkeyhint", "done");
    expect(passwordInput).toHaveClass("max-sm:text-title", "pointer-coarse:text-title");
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

    mocks.loginWithPassword.mockRejectedValueOnce(
      new ApiStatusError(401, "backend msg", { code: "INVALID_CREDENTIALS" }),
    );
    await user.type(screen.getByLabelText(/Mật khẩu/), "wrong");
    await user.click(screen.getByRole("button", { name: "Đăng nhập" }));
    expect(await screen.findByText("Tên đăng nhập hoặc mật khẩu không đúng.")).toBeInTheDocument();

    const switcher = screen.getByRole("button", { name: "Chọn ngôn ngữ" });
    switcher.focus();
    expect(switcher).toHaveFocus();

    await user.keyboard("{Enter}");
    const viItem = await screen.findByRole("menuitemradio", { name: "Tiếng Việt" });
    const englishItem = screen.getByRole("menuitemradio", { name: "English" });
    expect(viItem).toHaveAttribute("aria-checked", "true");
    expect(englishItem).toHaveAttribute("aria-checked", "false");
    expect(viItem).toHaveFocus();
    await user.keyboard("{ArrowDown}{Enter}");
    await waitFor(() => expect(screen.getByRole("heading", { name: "Sign in" })).toBeInTheDocument());
    expect(document.cookie).toContain("thi360_locale=en");
    // Lỗi đang hiện đổi theo ngôn ngữ mới: lỗi máy chủ và lỗi của ô đều lưu bằng mã, dịch lúc hiển thị.
    expect(screen.getByText(en_messages.login.errors.INVALID_CREDENTIALS)).toBeInTheDocument();
    expect(screen.queryByText("Tên đăng nhập hoặc mật khẩu không đúng.")).toBeNull();
    expect(screen.getByLabelText(/Username or email/)).toBe(identifierInput);
    expect(identifierInput).toHaveValue("ops.admin");
    const updatedSwitcher = screen.getByRole("button", { name: "Change language" });
    expect(updatedSwitcher).toBe(switcher);
    expect(updatedSwitcher).toHaveFocus();
  });

  it("liên kết Đăng ký và Quên mật khẩu mang callbackUrl; ghi chú liên hệ quản trị viên không còn (AC-6)", () => {
    mocks.callbackUrl = "/checkout/BCA-FULL";
    renderWithProviders(<LoginView navigate={mocks.navigate} />);

    expect(screen.getByRole("link", { name: "Đăng ký" })).toHaveAttribute(
      "href",
      `/register?callbackUrl=${encodeURIComponent("/checkout/BCA-FULL")}`,
    );
    expect(screen.getByRole("link", { name: "Quên mật khẩu?" })).toHaveAttribute(
      "href",
      `/forgot-password?callbackUrl=${encodeURIComponent("/checkout/BCA-FULL")}`,
    );
    expect(screen.queryByText(/Liên hệ quản trị viên/)).toBeNull();
  });

  it("không có callbackUrl thì hai liên kết đi thẳng, không thêm tham số", () => {
    renderWithProviders(<LoginView navigate={mocks.navigate} />);

    expect(screen.getByRole("link", { name: "Đăng ký" })).toHaveAttribute("href", "/register");
    expect(screen.getByRole("link", { name: "Quên mật khẩu?" })).toHaveAttribute("href", "/forgot-password");
  });

  it("về từ trang đặt lại mật khẩu: hiện thông báo thành công, ô nhập vẫn trống (không tự đăng nhập)", () => {
    mocks.reset = "done";
    renderWithProviders(<LoginView navigate={mocks.navigate} />);

    expect(screen.getByRole("status")).toHaveTextContent("Đã đặt lại mật khẩu. Hãy đăng nhập bằng mật khẩu mới.");
    expect(screen.getByLabelText(/Tên đăng nhập hoặc email/)).toHaveValue("");
    expect(mocks.loginWithPassword).not.toHaveBeenCalled();
  });

  it("#deletedNotice: có ?deleted=1 hiện thông báo đã xóa tài khoản ở cả tiếng Việt và tiếng Anh (AC-7)", () => {
    mocks.deleted = "1";
    const { unmount } = renderWithProviders(<LoginView navigate={mocks.navigate} />);

    expect(screen.getByRole("status")).toHaveTextContent("Tài khoản của bạn đã được xóa.");
    expect(screen.getByLabelText(/Tên đăng nhập hoặc email/)).toHaveValue("");
    expect(mocks.loginWithPassword).not.toHaveBeenCalled();

    unmount();

    render(
      <NextIntlClientProvider locale="en" messages={en_messages} timeZone="Asia/Ho_Chi_Minh">
        <LoginView navigate={mocks.navigate} />
      </NextIntlClientProvider>,
    );
    expect(screen.getByRole("status")).toHaveTextContent("Your account has been deleted.");
  });

  it("đăng nhập với callbackUrl=/account/delete gửi callbackUrl và quay về /account/delete (AC-4)", async () => {
    mocks.callbackUrl = "/account/delete";
    mocks.loginWithPassword.mockResolvedValue({
      redirectTo: "/account/delete",
      mustChangePassword: false,
    });

    renderWithProviders(<LoginView navigate={mocks.navigate} />);

    await userEvent.type(screen.getByLabelText(/Tên đăng nhập hoặc email/), "student01");
    await userEvent.type(screen.getByLabelText(/Mật khẩu/), "Thi360@dev");
    await userEvent.click(screen.getByRole("button", { name: "Đăng nhập" }));

    await waitFor(() =>
      expect(mocks.loginWithPassword).toHaveBeenCalledWith({
        identifier: "student01",
        password: "Thi360@dev",
        callbackUrl: "/account/delete",
      }),
    );
    expect(mocks.navigate).toHaveBeenCalledWith("/account/delete");
  });
});
