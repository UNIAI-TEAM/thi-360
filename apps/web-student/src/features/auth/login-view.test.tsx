import { screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import type * as SessionApi from "@/features/session/api";
import { ApiStatusError } from "@/lib/api/client";
import { renderWithProviders } from "@/test/render";
import { LoginView } from "./login-view";

const mocks = vi.hoisted(() => ({
  callbackUrl: null as string | null,
  reason: null as string | null,
  loginWithPassword: vi.fn(),
  navigate: vi.fn(),
}));

vi.mock("next/navigation", () => ({
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
  ])("hiển thị thông báo lỗi đúng cho mã %s và giữ nguyên tên đăng nhập", async (code, expected) => {
    mocks.loginWithPassword.mockRejectedValue(new ApiStatusError(401, "backend msg", { code }));

    renderWithProviders(<LoginView navigate={mocks.navigate} />);

    const identifierInput = screen.getByLabelText(/Tên đăng nhập hoặc email/);
    await userEvent.type(identifierInput, "student01");
    await userEvent.type(screen.getByLabelText(/Mật khẩu/), "wrong");
    await userEvent.click(screen.getByRole("button", { name: "Đăng nhập" }));

    expect(await screen.findByRole("alert")).toHaveTextContent(expected);
    expect(identifierInput).toHaveValue("student01");
  });
});
