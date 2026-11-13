import { screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { THEME_STYLE_ELEMENT_ID } from "@thi360/ui";
import { afterEach, describe, expect, it, vi } from "vitest";
import { applyAppearanceToDocument } from "@/features/appearance/api";
import { renderWithProviders } from "@/test/render";
import DevUiPage from "./page";
const notFoundMock = vi.hoisted(() =>
  vi.fn(() => {
    throw new Error("NEXT_NOT_FOUND");
  }),
);
vi.mock("next/navigation", () => ({ notFound: notFoundMock }));

afterEach(() => {
  delete document.documentElement.dataset.theme;
  document.documentElement.style.cssText = "";
  document.getElementById(THEME_STYLE_ELEMENT_ID)?.remove();
});

describe("/dev/ui", () => {
  it("chặn ở production bằng notFound()", () => {
    vi.stubEnv("NODE_ENV", "production");

    expect(() => DevUiPage()).toThrow("NEXT_NOT_FOUND");
  });

  it("hiện đủ 5 màu StatusChip và chuyển đổi biến màu sáng/tối khi đổi data-theme sau khi áp dụng giao diện", async () => {
    applyAppearanceToDocument({
      palette: { presetId: "navy-teal" },
      mode: "light",
      version: 1,
    });

    const { unmount } = renderWithProviders(<DevUiPage />);

    for (const label of ["Hoạt động", "Chưa đăng nhập", "Bị khóa", "Hệ thống", "Ngừng sử dụng"]) {
      expect(screen.getAllByText(label).length).toBeGreaterThan(0);
    }

    expect(getComputedStyle(document.documentElement).getPropertyValue("--color-page").trim()).toBe("#f7f8f9");
    expect(getComputedStyle(document.documentElement).getPropertyValue("--color-primary-text").trim()).toBe("#063160");

    await userEvent.click(screen.getByRole("tab", { name: "Tối" }));
    expect(document.documentElement.dataset.theme).toBe("dark");
    expect(getComputedStyle(document.documentElement).getPropertyValue("--color-page").trim()).toBe("#0f1418");
    expect(getComputedStyle(document.documentElement).getPropertyValue("--color-primary-text").trim()).toBe("#89aad4");

    await userEvent.click(screen.getByRole("tab", { name: "Sáng" }));
    expect(document.documentElement.dataset.theme).toBe("light");
    expect(getComputedStyle(document.documentElement).getPropertyValue("--color-page").trim()).toBe("#f7f8f9");
    expect(getComputedStyle(document.documentElement).getPropertyValue("--color-primary-text").trim()).toBe("#063160");

    unmount();
    expect(document.documentElement.dataset.theme).toBe("light");
  });
});
