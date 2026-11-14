import { screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { THEME_STYLE_ELEMENT_ID, ThemeToggle, UiPreferencesProvider } from "@thi360/ui";
import { createTranslator } from "next-intl";
import { afterEach, describe, expect, it, vi } from "vitest";
import { applyAppearanceToDocument } from "@/features/appearance/api";
import enMessages from "@/messages/en.json";
import viMessages from "@/messages/vi.json";
import { renderWithProviders } from "@/test/render";
import DevUiPage, { generateMetadata } from "./page";

const mocks = vi.hoisted(() => ({
  locale: "vi" as "vi" | "en",
  notFound: vi.fn(() => {
    throw new Error("NEXT_NOT_FOUND");
  }),
}));

vi.mock("next/navigation", () => ({ notFound: mocks.notFound }));
vi.mock("next-intl/server", () => ({
  getTranslations: async (namespace?: "devUi") =>
    createTranslator({
      locale: mocks.locale,
      messages: mocks.locale === "en" ? enMessages : viMessages,
      namespace,
    }),
}));
afterEach(() => {
  mocks.locale = "vi";
  delete document.documentElement.dataset.theme;
  document.documentElement.style.cssText = "";
  document.getElementById(THEME_STYLE_ELEMENT_ID)?.remove();
  document.cookie = "thi360_theme=; Max-Age=0; Path=/";
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

  it("giữ nguyên data-theme=dark từ SSR khi tải trang và đồng bộ với ThemeToggle của shell", async () => {
    document.documentElement.dataset.theme = "dark";

    renderWithProviders(
      <UiPreferencesProvider initialTheme="dark">
        <ThemeToggle />
        <DevUiPage />
      </UiPreferencesProvider>,
    );

    expect(document.documentElement.dataset.theme).toBe("dark");
    expect(screen.getByRole("tab", { name: "Tối" })).toHaveAttribute("aria-selected", "true");
    expect(screen.getByRole("tab", { name: "Sáng" })).toHaveAttribute("aria-selected", "false");

    await userEvent.click(screen.getByRole("button", { name: "Chuyển sang chế độ sáng" }));
    expect(document.documentElement.dataset.theme).toBe("light");
    expect(screen.getByRole("tab", { name: "Sáng" })).toHaveAttribute("aria-selected", "true");
  });

  it("đọc data-theme=dark ngay cả khi không bọc UiPreferencesProvider", () => {
    document.documentElement.dataset.theme = "dark";
    renderWithProviders(<DevUiPage />);
    expect(document.documentElement.dataset.theme).toBe("dark");
    expect(screen.getByRole("tab", { name: "Tối" })).toHaveAttribute("aria-selected", "true");
  });

  it("generateMetadata trả tiêu đề tab đã dịch theo locale vi và en", async () => {
    mocks.locale = "vi";
    expect(await generateMetadata()).toEqual({ title: "Thành phần giao diện (/dev/ui) · Thi 360" });

    mocks.locale = "en";
    expect(await generateMetadata()).toEqual({ title: "UI components (/dev/ui) · Thi 360" });
  });
});
