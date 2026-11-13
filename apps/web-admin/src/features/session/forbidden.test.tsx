import { screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, describe, expect, it, vi } from "vitest";
import { renderWithProviders } from "@/test/render";
import { FAKE_SESSION } from "./fake-session";
import { ForbiddenView } from "./forbidden-view";
import { SessionProvider } from "./session-provider";

const mocks = vi.hoisted(() => ({ redirectToLogin: vi.fn() }));
vi.mock("@/lib/auth-redirect", () => ({ redirectToLogin: mocks.redirectToLogin }));

afterEach(() => {
  mocks.redirectToLogin.mockReset();
});

describe("ForbiddenView", () => {
  it("nêu tên tài khoản đang đăng nhập, có liên kết Về trang chủ và nút Đăng nhập bằng tài khoản khác", async () => {
    renderWithProviders(
      <SessionProvider loadSession={async () => ({ ...FAKE_SESSION, username: "student01" })}>
        <ForbiddenView />
      </SessionProvider>,
    );
    expect(await screen.findByText("student01")).toBeInTheDocument();
    expect(screen.getByRole("heading", { name: "Bạn không có quyền vào trang này" })).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Về trang chủ" })).toHaveAttribute("href", "/");

    await userEvent.click(screen.getByRole("button", { name: "Đăng nhập bằng tài khoản khác" }));
    await waitFor(() =>
      expect(mocks.redirectToLogin).toHaveBeenCalledWith({ returnHere: false }),
    );
  });
});
