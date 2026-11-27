import { screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, describe, expect, it, vi } from "vitest";
import type * as Ui from "@thi360/ui";
import type * as SessionApi from "@/features/session/api";
import { ApiStatusError } from "@/lib/api/client";
import { renderWithProviders } from "@/test/render";
import { StudentUserMenu } from "./student-user-menu";

const mocks = vi.hoisted(() => ({
  logoutSession: vi.fn(),
  navigate: vi.fn(),
  notifyError: vi.fn(),
}));

vi.mock("@/features/session/api", async () => {
  const actual = await vi.importActual<typeof SessionApi>("@/features/session/api");
  return { ...actual, logoutSession: mocks.logoutSession };
});

vi.mock("@thi360/ui", async () => {
  const actual = await vi.importActual<typeof Ui>("@thi360/ui");
  return { ...actual, notify: { ...actual.notify, error: mocks.notifyError } };
});

const STUDENT = {
  id: "u-stu",
  username: "student01",
  fullName: "Học viên 01",
  realm: "APP" as const,
  access: "EXAM" as const,
  permissions: [],
  homeUrl: "http://localhost:3000/home",
};

afterEach(() => {
  mocks.logoutSession.mockReset();
  mocks.navigate.mockReset();
  mocks.notifyError.mockReset();
});

async function openMenu() {
  renderWithProviders(<StudentUserMenu session={STUDENT} navigate={mocks.navigate} />);
  await userEvent.click(screen.getByRole("button", { name: "Menu tài khoản" }));
}

describe("StudentUserMenu", () => {
  it("chỉ có Đổi mật khẩu và Đăng xuất: không lặp Hồ sơ, Trang học viên của thanh điều hướng (UNI-2877)", async () => {
    await openMenu();
    expect(screen.getAllByRole("menuitem").map((item) => item.textContent)).toEqual(["Đổi mật khẩu", "Đăng xuất"]);
  });

  it("Đăng xuất gọi API rồi về trang chủ /, không về /login (AC-10)", async () => {
    mocks.logoutSession.mockResolvedValue(undefined);
    await openMenu();

    await userEvent.click(screen.getByRole("menuitem", { name: "Đăng xuất" }));

    await waitFor(() => expect(mocks.navigate).toHaveBeenCalledWith("/"));
    expect(mocks.logoutSession).toHaveBeenCalledTimes(1);
    expect(mocks.navigate).not.toHaveBeenCalledWith("/login");
  });

  it("Đăng xuất lỗi thì ở lại trang và báo lỗi", async () => {
    mocks.logoutSession.mockRejectedValue(new ApiStatusError(500, "x"));
    await openMenu();

    await userEvent.click(screen.getByRole("menuitem", { name: "Đăng xuất" }));

    await waitFor(() => expect(mocks.notifyError).toHaveBeenCalledWith("Không đăng xuất được. Hãy thử lại."));
    expect(mocks.navigate).not.toHaveBeenCalled();
  });
});
