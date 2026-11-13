import { screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, describe, expect, it, vi } from "vitest";
import { TEST_ADMIN_SESSION } from "@/test/fixtures";
import { renderWithProviders } from "@/test/render";
import type * as SessionApi from "./api";
import { ForbiddenView } from "./forbidden-view";
import { SessionProvider } from "./session-provider";

const mocks = vi.hoisted(() => ({ logout: vi.fn() }));
vi.mock("./api", async (importOriginal) => {
  const actual = await importOriginal<typeof SessionApi>();
  return { ...actual, logout: mocks.logout };
});

afterEach(() => {
  mocks.logout.mockReset();
});

describe("ForbiddenView", () => {
  it("nêu tên tài khoản đang đăng nhập, liên kết Về trang chủ theo homeUrl và nút Đăng nhập bằng tài khoản khác", async () => {
    renderWithProviders(
      <SessionProvider
        loadSession={async () => ({
          ...TEST_ADMIN_SESSION,
          username: "student01",
          access: "EXAM",
          homeUrl: "http://localhost:3000/home",
          permissions: [],
        })}
      >
        <ForbiddenView />
      </SessionProvider>,
    );
    expect(await screen.findByText("student01")).toBeInTheDocument();
    expect(screen.getByRole("heading", { name: "Bạn không có quyền vào trang này" })).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Về trang chủ" })).toHaveAttribute(
      "href",
      "http://localhost:3000/home",
    );

    await userEvent.click(screen.getByRole("button", { name: "Đăng nhập bằng tài khoản khác" }));
    await waitFor(() => expect(mocks.logout).toHaveBeenCalledTimes(1));
  });
});
