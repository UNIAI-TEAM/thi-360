import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { notify } from "@thi360/ui";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { NextIntlClientProvider } from "next-intl";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import type * as SessionApi from "@/features/session/api";
import { ApiStatusError } from "@/lib/api/client";
import en_messages from "@/messages/en.json";
import vi_messages from "@/messages/vi.json";
import { renderWithProviders } from "@/test/render";
import { ChangePasswordView } from "./change-password-view";

const mocks = vi.hoisted(() => ({
  callbackUrl: null as string | null,
  source: null as string | null,
  changeAccountPassword: vi.fn(),
  logoutSession: vi.fn(),
  refreshSession: vi.fn(),
  clearSessionBestEffort: vi.fn(),
  navigate: vi.fn(),
}));
vi.mock("next/navigation", () => ({
  useSearchParams: () =>
    new URLSearchParams({
      ...(mocks.callbackUrl ? { callbackUrl: mocks.callbackUrl } : {}),
      ...(mocks.source ? { source: mocks.source } : {}),
    }),
}));

vi.mock("@/features/session/api", async () => {
  const actual = await vi.importActual<typeof SessionApi>("@/features/session/api");
  return {
    ...actual,
    changeAccountPassword: mocks.changeAccountPassword,
    logoutSession: mocks.logoutSession,
    refreshSession: mocks.refreshSession,
    clearSessionBestEffort: mocks.clearSessionBestEffort,
  };
});

const STUDENT_SESSION = {
  id: "00000000-0000-4000-8000-000000000003",
  username: "student03",
  fullName: "Học viên 03",
  realm: "APP" as const,
  access: "EXAM" as const,
  permissions: [],
  homeUrl: "http://localhost:3000/home",
};

beforeEach(() => {
  mocks.callbackUrl = null;
  mocks.source = null;
});

afterEach(() => {
  mocks.changeAccountPassword.mockReset();
  mocks.logoutSession.mockReset();
  mocks.refreshSession.mockReset();
  mocks.clearSessionBestEffort.mockReset();
  mocks.navigate.mockReset();
});
describe("ChangePasswordView", () => {
  it("khung chờ và biểu mẫu dùng cùng vị trí và đệm của thẻ: bám mép trên ở màn hẹp, căn giữa từ md", async () => {
    // jsdom không dàn trang: kiểm lớp bố cục giống nhau giữa hai trạng thái để thẻ không nhảy khi phiên tải xong.
    const loadingView = renderWithProviders(
      <ChangePasswordView loadCurrentSession={() => new Promise(() => {})} navigate={mocks.navigate} />,
    );
    const loadingMain = screen.getByRole("main");
    const loadingCard = screen.getByRole("status", { name: "Đang tải" });
    expect(loadingMain).toHaveClass("items-start", "md:items-center");
    expect(loadingMain).not.toHaveClass("items-center");
    const mainClass = loadingMain.className;
    const cardClass = loadingCard.className;
    loadingView.unmount();

    renderWithProviders(
      <ChangePasswordView loadCurrentSession={async () => STUDENT_SESSION} navigate={mocks.navigate} />,
    );
    expect(await screen.findByText("student03")).toBeInTheDocument();
    const main = screen.getByRole("main");
    expect(main.className).toBe(mainClass);
    expect(main.firstElementChild?.className).toBe(cardClass);
  });

  it("kiểm tra 5 điều kiện mật khẩu và điều hướng tới callbackUrl hợp lệ sau khi đổi thành công", async () => {
    mocks.callbackUrl = "http://localhost:3001/users";
    mocks.changeAccountPassword.mockResolvedValue(undefined);

    renderWithProviders(
      <ChangePasswordView loadCurrentSession={async () => STUDENT_SESSION} navigate={mocks.navigate} />,
    );

    expect(await screen.findByText("student03")).toBeInTheDocument();

    await userEvent.type(screen.getByLabelText(/Mật khẩu hiện tại/), "OldPass1!");
    await userEvent.type(screen.getByLabelText(/^Mật khẩu mới/), "NewPass1!");
    expect(screen.getByText("Từ 8 đến 64 ký tự").closest("li")).toHaveAttribute("data-met", "true");
    await userEvent.type(screen.getByLabelText(/Nhập lại mật khẩu mới/), "NewPass1!");

    await userEvent.click(screen.getByRole("button", { name: "Lưu và tiếp tục" }));

    await waitFor(() =>
      expect(mocks.changeAccountPassword).toHaveBeenCalledWith({
        oldPassword: "OldPass1!",
        newPassword: "NewPass1!",
      }),
    );
    expect(mocks.navigate).toHaveBeenCalledWith("http://localhost:3001/users");
  });

  it("bỏ qua callbackUrl không hợp lệ và dùng homeUrl; hiện lỗi WRONG_OLD_PASSWORD", async () => {
    mocks.callbackUrl = "https://evil.example";
    mocks.changeAccountPassword
      .mockRejectedValueOnce(new ApiStatusError(400, "wrong", { code: "WRONG_OLD_PASSWORD" }))
      .mockResolvedValueOnce(undefined);

    renderWithProviders(
      <ChangePasswordView loadCurrentSession={async () => STUDENT_SESSION} navigate={mocks.navigate} />,
    );

    expect(await screen.findByText("student03")).toBeInTheDocument();

    await userEvent.type(screen.getByLabelText(/Mật khẩu hiện tại/), "Wrong1!");
    await userEvent.type(screen.getByLabelText(/^Mật khẩu mới/), "NewPass1!");
    await userEvent.type(screen.getByLabelText(/Nhập lại mật khẩu mới/), "NewPass1!");
    await userEvent.click(screen.getByRole("button", { name: "Lưu và tiếp tục" }));

    expect(await screen.findByText("Mật khẩu hiện tại không đúng.")).toBeInTheDocument();
    // Lỗi máy chủ gắn vào ô nào thì focus về ô đó.
    await waitFor(() => expect(screen.getByLabelText(/Mật khẩu hiện tại/)).toHaveFocus());

    await userEvent.click(screen.getByRole("button", { name: "Lưu và tiếp tục" }));
    await waitFor(() => expect(mocks.navigate).toHaveBeenCalledWith("http://localhost:3000/home"));
  });

  it("gửi biểu mẫu trống: focus chuyển tới ô lỗi đầu tiên; lỗi PASSWORD_WEAK từ máy chủ đưa focus về ô mật khẩu mới", async () => {
    mocks.changeAccountPassword.mockRejectedValueOnce(
      new ApiStatusError(400, "weak", { code: "PASSWORD_WEAK" }),
    );
    renderWithProviders(
      <ChangePasswordView loadCurrentSession={async () => STUDENT_SESSION} navigate={mocks.navigate} />,
    );
    expect(await screen.findByText("student03")).toBeInTheDocument();

    await userEvent.click(screen.getByRole("button", { name: "Lưu và tiếp tục" }));
    expect(await screen.findByText("Vui lòng nhập mật khẩu hiện tại.")).toBeInTheDocument();
    await waitFor(() => expect(screen.getByLabelText(/Mật khẩu hiện tại/)).toHaveFocus());
    expect(mocks.changeAccountPassword).not.toHaveBeenCalled();

    await userEvent.type(screen.getByLabelText(/Mật khẩu hiện tại/), "OldPass1!");
    await userEvent.type(screen.getByLabelText(/^Mật khẩu mới/), "NewPass1!");
    await userEvent.type(screen.getByLabelText(/Nhập lại mật khẩu mới/), "NewPass1!");
    await userEvent.click(screen.getByRole("button", { name: "Lưu và tiếp tục" }));

    expect(await screen.findByText("Mật khẩu mới chưa đáp ứng đủ năm điều kiện.")).toBeInTheDocument();
    await waitFor(() => expect(screen.getByLabelText(/^Mật khẩu mới/)).toHaveFocus());
  });

  it("ba ô mật khẩu bắt buộc, đúng autocomplete, gợi ý phím Enter và chữ 16px ở màn hẹp", async () => {
    renderWithProviders(
      <ChangePasswordView loadCurrentSession={async () => STUDENT_SESSION} navigate={mocks.navigate} />,
    );
    expect(await screen.findByText("student03")).toBeInTheDocument();

    const expected = [
      [screen.getByLabelText(/Mật khẩu hiện tại/), "current-password", "next"],
      [screen.getByLabelText(/^Mật khẩu mới/), "new-password", "next"],
      [screen.getByLabelText(/Nhập lại mật khẩu mới/), "new-password", "done"],
    ] as const;
    for (const [input, autocomplete, enterKeyHint] of expected) {
      expect(input).toBeRequired();
      expect(input).toHaveAttribute("autocomplete", autocomplete);
      expect(input).toHaveAttribute("enterkeyhint", enterKeyHint);
      expect(input).toHaveClass("max-sm:text-title", "pointer-coarse:text-title");
    }
  });

  it("bỏ qua callbackUrl trỏ về /change-password hoặc /login để tránh vòng lặp", async () => {
    mocks.callbackUrl = "http://localhost:3000/change-password?callbackUrl=%2Fhome";
    mocks.changeAccountPassword.mockResolvedValue(undefined);

    renderWithProviders(
      <ChangePasswordView loadCurrentSession={async () => STUDENT_SESSION} navigate={mocks.navigate} />,
    );
    expect(await screen.findByText("student03")).toBeInTheDocument();

    await userEvent.type(screen.getByLabelText(/Mật khẩu hiện tại/), "OldPass1!");
    await userEvent.type(screen.getByLabelText(/^Mật khẩu mới/), "NewPass1!");
    await userEvent.type(screen.getByLabelText(/Nhập lại mật khẩu mới/), "NewPass1!");
    await userEvent.click(screen.getByRole("button", { name: "Lưu và tiếp tục" }));

    await waitFor(() => expect(mocks.navigate).toHaveBeenCalledWith("http://localhost:3000/home"));
  });

  it("401 khi gửi biểu mẫu: gọi /auth/refresh một lần rồi thử lại, hoặc về /login nếu refresh trả 401", async () => {
    mocks.changeAccountPassword
      .mockRejectedValueOnce(new ApiStatusError(401, "expired"))
      .mockResolvedValueOnce(undefined);
    mocks.refreshSession.mockResolvedValueOnce(undefined);

    renderWithProviders(
      <ChangePasswordView loadCurrentSession={async () => STUDENT_SESSION} navigate={mocks.navigate} />,
    );
    expect(await screen.findByText("student03")).toBeInTheDocument();

    await userEvent.type(screen.getByLabelText(/Mật khẩu hiện tại/), "OldPass1!");
    await userEvent.type(screen.getByLabelText(/^Mật khẩu mới/), "NewPass1!");
    await userEvent.type(screen.getByLabelText(/Nhập lại mật khẩu mới/), "NewPass1!");
    await userEvent.click(screen.getByRole("button", { name: "Lưu và tiếp tục" }));

    await waitFor(() => expect(mocks.navigate).toHaveBeenCalledWith("http://localhost:3000/home"));
    expect(mocks.refreshSession).toHaveBeenCalledTimes(1);
    expect(mocks.changeAccountPassword).toHaveBeenCalledTimes(2);

    mocks.navigate.mockReset();
    mocks.changeAccountPassword.mockRejectedValueOnce(new ApiStatusError(401, "revoked"));
    mocks.refreshSession.mockRejectedValueOnce(new ApiStatusError(401, "revoked"));
    await userEvent.click(screen.getByRole("button", { name: "Lưu và tiếp tục" }));

    await waitFor(() =>
      expect(mocks.navigate).toHaveBeenCalledWith(
        `http://localhost:3000/login?callbackUrl=${encodeURIComponent(window.location.href)}`,
      ),
    );
    expect(mocks.clearSessionBestEffort).not.toHaveBeenCalled();
  });

  it("hiện thông báo lỗi khi Đăng nhập bằng tài khoản khác thất bại", async () => {
    const toastError = vi.spyOn(notify, "error").mockImplementation(() => "");
    mocks.logoutSession.mockRejectedValueOnce(new Error("network"));

    renderWithProviders(
      <ChangePasswordView loadCurrentSession={async () => STUDENT_SESSION} navigate={mocks.navigate} />,
    );
    expect(await screen.findByText("student03")).toBeInTheDocument();

    const otherAccount = screen.getByRole("button", { name: "Đăng nhập bằng tài khoản khác" });
    // Nút dạng chữ cao 20px: vùng bấm mở rộng thành 44px trên màn cảm ứng.
    expect(otherAccount).toHaveClass("relative", "pointer-coarse:after:absolute", "pointer-coarse:after:-inset-y-3");
    await userEvent.click(otherAccount);
    await waitFor(() => expect(toastError).toHaveBeenCalledWith(vi_messages.errors.logoutFailed));
    toastError.mockRestore();
  });

  it("dùng nhãn Show/Hide password theo tiếng Anh cho cả ba ô PasswordField", async () => {
    const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
    render(
      <NextIntlClientProvider locale="en" messages={en_messages} timeZone="Asia/Ho_Chi_Minh">
        <QueryClientProvider client={client}>
          <ChangePasswordView loadCurrentSession={async () => STUDENT_SESSION} navigate={mocks.navigate} />
        </QueryClientProvider>
      </NextIntlClientProvider>,
    );

    expect(await screen.findByText("student03")).toBeInTheDocument();
    // Ba nút hiện mật khẩu mang tên riêng theo ô, không trùng nhau.
    expect(screen.queryByRole("button", { name: "Show password" })).toBeNull();
    const showCurrent = screen.getByRole("button", { name: "Show current password" });
    expect(screen.getByRole("button", { name: "Show new password" })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Show confirmed password" })).toBeInTheDocument();

    await userEvent.click(showCurrent);
    expect(screen.getByRole("button", { name: "Hide current password" })).toBeInTheDocument();
    expect(screen.getByLabelText(/Current password/)).toHaveAttribute("type", "text");
    expect(screen.getByLabelText(/^New password/)).toHaveAttribute("type", "password");
  });

  it("lời dẫn trung tính; lối Quay lại về homeUrl chỉ hiện khi mở từ menu tài khoản; lỗi của ô dịch theo ngôn ngữ đang dùng", async () => {
    const forcedView = renderWithProviders(
      <ChangePasswordView loadCurrentSession={async () => STUDENT_SESSION} navigate={mocks.navigate} />,
    );
    expect(await screen.findByText("student03")).toBeInTheDocument();
    expect(screen.getByText(/Đặt mật khẩu mới cho tài khoản/)).toBeInTheDocument();
    expect(screen.queryByText(/trước khi tiếp tục/)).toBeNull();
    expect(screen.queryByRole("link", { name: /Quay lại/ })).toBeNull();
    const names = screen
      .getAllByRole("button", { name: /^Hiện mật khẩu/ })
      .map((button) => button.getAttribute("aria-label"));
    expect(names).toEqual(["Hiện mật khẩu hiện tại", "Hiện mật khẩu mới", "Hiện mật khẩu nhập lại"]);
    forcedView.unmount();

    mocks.source = "account-menu";
    mocks.changeAccountPassword.mockRejectedValueOnce(
      new ApiStatusError(400, "wrong", { code: "WRONG_OLD_PASSWORD" }),
    );
    const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
    render(
      <NextIntlClientProvider locale="en" messages={en_messages} timeZone="Asia/Ho_Chi_Minh">
        <QueryClientProvider client={client}>
          <ChangePasswordView loadCurrentSession={async () => STUDENT_SESSION} navigate={mocks.navigate} />
        </QueryClientProvider>
      </NextIntlClientProvider>,
    );
    expect(await screen.findByText("student03")).toBeInTheDocument();
    expect(screen.getByRole("link", { name: /Back/ })).toHaveAttribute("href", "http://localhost:3000/home");

    await userEvent.type(screen.getByLabelText(/Current password/), "Wrong1!");
    await userEvent.type(screen.getByLabelText(/^New password/), "NewPass1!");
    await userEvent.type(screen.getByLabelText(/Confirm new password/), "NewPass1!");
    await userEvent.click(screen.getByRole("button", { name: en_messages.changePassword.submit }));
    expect(await screen.findByText(en_messages.changePassword.wrongOldPassword)).toBeInTheDocument();
  });
});
