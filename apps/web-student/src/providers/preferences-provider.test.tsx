import { LocaleSwitcher, ThemeToggle } from "@thi360/ui";
import { buildPreferenceCookieString } from "@thi360/web-kit/preferences";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, describe, expect, it, vi } from "vitest";
import { PreferencesProvider } from "./preferences-provider";

const mocks = vi.hoisted(() => ({
  refresh: vi.fn(),
}));

vi.mock("next/navigation", () => ({
  useRouter: () => ({ refresh: mocks.refresh }),
}));

function captureCookieWrites() {
  const writes: string[] = [];
  const descriptor = Object.getOwnPropertyDescriptor(Document.prototype, "cookie");
  const spy = vi.spyOn(document, "cookie", "set").mockImplementation((value: string) => {
    writes.push(value);
    descriptor?.set?.call(document, value);
  });
  return {
    writes,
    restore: () => spy.mockRestore(),
  };
}

afterEach(() => {
  mocks.refresh.mockReset();
  delete document.documentElement.dataset.theme;
  document.cookie = "thi360_theme=; Max-Age=0; Path=/";
  document.cookie = "thi360_locale=; Max-Age=0; Path=/";
});

describe("web-student PreferencesProvider", () => {
  it("ghi cookie kèm Domain và gọi router.refresh() khi đổi giao diện hoặc ngôn ngữ", async () => {
    const user = userEvent.setup();
    const { writes, restore } = captureCookieWrites();
    try {
      render(
        <PreferencesProvider locale="vi" initialTheme="light" cookieDomain=".thi360.vn">
          <ThemeToggle />
          <LocaleSwitcher />
        </PreferencesProvider>,
      );

      await user.click(screen.getByRole("button", { name: "Chuyển sang chế độ tối" }));
      expect(document.documentElement.dataset.theme).toBe("dark");
      expect(writes.at(-1)).toBe(
        buildPreferenceCookieString("thi360_theme", "dark", { domain: ".thi360.vn" }),
      );
      expect(mocks.refresh).not.toHaveBeenCalled();

      await user.click(screen.getByRole("button", { name: "Chọn ngôn ngữ" }));
      await user.click(screen.getByRole("menuitem", { name: "English" }));
      expect(writes.at(-1)).toBe(
        buildPreferenceCookieString("thi360_locale", "en", { domain: ".thi360.vn" }),
      );
      expect(mocks.refresh).toHaveBeenCalledTimes(1);
    } finally {
      restore();
    }
  });
});
