import { screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";
import { renderWithProviders } from "@/test/render";
import DevUiPage from "./page";

const notFoundMock = vi.hoisted(() =>
  vi.fn(() => {
    throw new Error("NEXT_NOT_FOUND");
  }),
);
vi.mock("next/navigation", () => ({ notFound: notFoundMock }));

describe("/dev/ui", () => {
  it("chặn ở production bằng notFound()", () => {
    vi.stubEnv("NODE_ENV", "production");

    expect(() => DevUiPage()).toThrow("NEXT_NOT_FOUND");
  });

  it("hiện đủ 5 màu StatusChip và chuyển data-theme='dark' trên <html>", async () => {
    renderWithProviders(<DevUiPage />);

    for (const label of ["Hoạt động", "Chưa đăng nhập", "Bị khóa", "Hệ thống", "Ngừng sử dụng"]) {
      expect(screen.getAllByText(label).length).toBeGreaterThan(0);
    }

    await userEvent.click(screen.getByRole("tab", { name: "Tối" }));
    expect(document.documentElement.dataset.theme).toBe("dark");

    await userEvent.click(screen.getByRole("tab", { name: "Sáng" }));
    expect(document.documentElement.dataset.theme).toBeUndefined();
  });
});
