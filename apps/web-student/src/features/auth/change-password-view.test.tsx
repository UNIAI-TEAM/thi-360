import { screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import type * as SessionApi from "@/features/session/api";
import { ApiStatusError } from "@/lib/api/client";
import { renderWithProviders } from "@/test/render";
import { ChangePasswordView } from "./change-password-view";

const mocks = vi.hoisted(() => ({
  callbackUrl: null as string | null,
  changeAccountPassword: vi.fn(),
  logoutSession: vi.fn(),
  navigate: vi.fn(),
}));

vi.mock("next/navigation", () => ({
  useSearchParams: () =>
    new URLSearchParams(mocks.callbackUrl ? { callbackUrl: mocks.callbackUrl } : {}),
}));

vi.mock("@/features/session/api", async () => {
  const actual = await vi.importActual<typeof SessionApi>("@/features/session/api");
  return {
    ...actual,
    changeAccountPassword: mocks.changeAccountPassword,
    logoutSession: mocks.logoutSession,
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
});

afterEach(() => {
  mocks.changeAccountPassword.mockReset();
  mocks.logoutSession.mockReset();
  mocks.navigate.mockReset();
});

describe("ChangePasswordView", () => {
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

    await userEvent.click(screen.getByRole("button", { name: "Lưu và tiếp tục" }));
    await waitFor(() => expect(mocks.navigate).toHaveBeenCalledWith("http://localhost:3000/home"));
  });
});
