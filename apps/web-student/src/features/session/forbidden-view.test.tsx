import { notify } from "@thi360/ui";
import { screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, describe, expect, it, vi } from "vitest";
import type * as SessionApi from "@/features/session/api";
import vi_messages from "@/messages/vi.json";
import { renderWithProviders } from "@/test/render";
import { StudentForbiddenView } from "./forbidden-view";

const mocks = vi.hoisted(() => ({
  logoutSession: vi.fn(),
  navigate: vi.fn(),
}));

vi.mock("@/features/session/api", async (importOriginal) => {
  const actual = await importOriginal<typeof SessionApi>();
  return { ...actual, logoutSession: mocks.logoutSession };
});

afterEach(() => {
  mocks.logoutSession.mockReset();
  mocks.navigate.mockReset();
});

describe("StudentForbiddenView", () => {
  it("hiện tên tài khoản, nút về trang chủ và báo lỗi khi đăng xuất thất bại", async () => {
    const toastError = vi.spyOn(notify, "error").mockImplementation(() => "");
    mocks.logoutSession.mockRejectedValueOnce(new Error("network"));

    renderWithProviders(
      <StudentForbiddenView
        loadCurrentSession={async () => ({
          id: "u-1",
          username: "ops.admin",
          fullName: "Vận hành",
          realm: "APP",
          access: "MANAGE",
          permissions: [],
          homeUrl: "http://localhost:3001",
        })}
        navigate={mocks.navigate}
      />,
    );

    expect(await screen.findByText("ops.admin")).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Về trang chủ" })).toHaveAttribute("href", "http://localhost:3001");

    await userEvent.click(screen.getByRole("button", { name: "Đăng nhập bằng tài khoản khác" }));
    await waitFor(() => expect(toastError).toHaveBeenCalledWith(vi_messages.errors.logoutFailed));
    expect(mocks.navigate).not.toHaveBeenCalled();
    toastError.mockRestore();
  });
});
