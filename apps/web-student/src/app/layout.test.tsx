import type * as NextIntlModule from "next-intl";
import type { ReactNode } from "react";
import { THEME_STYLE_ELEMENT_ID } from "@thi360/ui";
import { renderToStaticMarkup } from "react-dom/server";
import { afterEach, describe, expect, it, vi } from "vitest";
import enMessages from "@/messages/en.json";
import viMessages from "@/messages/vi.json";
import LoginPage from "./dang-nhap/page";
import RootLayout from "./layout";

const ssrState = vi.hoisted(() => ({
  locale: "vi" as "vi" | "en" | "en-GB",
  themeCookie: undefined as string | undefined,
}));

vi.mock("next/headers", () => ({
  cookies: async () => ({
    get: (name: string) =>
      name === "thi360_theme" && ssrState.themeCookie !== undefined
        ? { value: ssrState.themeCookie }
        : undefined,
  }),
}));
vi.mock("next/font/google", () => ({
  Be_Vietnam_Pro: () => ({ variable: "--font-be-vietnam-pro" }),
}));

vi.mock("next-intl", async (importOriginal) => {
  const actual = await importOriginal<typeof NextIntlModule>();
  const Provider = actual.NextIntlClientProvider;
  return {
    ...actual,
    NextIntlClientProvider: ({ children }: { children: ReactNode }) => (
      <Provider
        locale={ssrState.locale}
        messages={ssrState.locale === "vi" ? viMessages : enMessages}
        timeZone="Asia/Ho_Chi_Minh"
      >
        {children}
      </Provider>
    ),
  };
});

vi.mock("next-intl/server", () => ({
  getLocale: async () => ssrState.locale,
  getTranslations: async (namespace?: string) => (key: string) => `${namespace ?? ""}.${key}`,
}));

vi.mock("next/navigation", () => ({
  usePathname: () => "/dang-nhap",
  useRouter: () => ({ replace: vi.fn(), push: vi.fn(), refresh: vi.fn() }),
  useSearchParams: () => new URLSearchParams(),
}));

afterEach(() => {
  ssrState.locale = "vi";
  ssrState.themeCookie = undefined;
  vi.unstubAllGlobals();
});

describe("web-student RootLayout /dang-nhap SSR appearance", () => {
  it("HTML máy chủ của /dang-nhap nhúng <style id=\"thi360-theme-vars\"> chứa màu chính tùy chỉnh đã lưu và data-theme", async () => {
    const fetchMock = vi.fn(async () =>
      Response.json({
        palette: { presetId: "custom", custom: { primary: "#1d4ed8", accent: "#d97706" } },
        mode: "dark",
        version: 5,
      }),
    );
    vi.stubGlobal("fetch", fetchMock);

    const tree = await RootLayout({ children: <LoginPage /> });
    const html = renderToStaticMarkup(tree);

    expect(html).toContain('lang="vi"');
    expect(html).toContain('data-theme="dark"');
    expect(html).toContain(`<style id="${THEME_STYLE_ELEMENT_ID}">`);
    expect(html).toContain(':root[data-theme="light"]');
    expect(html).toContain(':root[data-theme="dark"]');
    expect(html).toContain("--color-primary-600: #1d4ed8;");
    expect(html).toContain("Đăng nhập");

    expect(fetchMock).toHaveBeenCalledTimes(1);
    const [request, init] = fetchMock.mock.calls[0] as unknown as [
      Request,
      RequestInit & { next?: { revalidate?: number; tags?: string[] } },
    ];
    expect(request.url).toMatch(/\/api\/v1\/appearance$/);
    expect(init.next).toEqual({ revalidate: 60, tags: ["appearance"] });
  });

  it("HTML máy chủ của /dang-nhap nhúng màu chính của bộ phối màu đã lưu", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn(async () =>
        Response.json({
          palette: { presetId: "navy-teal" },
          mode: "light",
          version: 3,
        }),
      ),
    );

    const html = renderToStaticMarkup(await RootLayout({ children: <LoginPage /> }));

    expect(html).toContain('data-theme="light"');
    expect(html).toContain(`<style id="${THEME_STYLE_ELEMENT_ID}">`);
    expect(html).toContain("--color-primary-600: #0f3c6e;");
  });

  it("rơi về màu mặc định #059669 và chế độ sáng khi tải cấu hình giao diện thất bại", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn(async () => {
        throw new Error("ECONNREFUSED");
      }),
    );

    const html = renderToStaticMarkup(await RootLayout({ children: <LoginPage /> }));

    expect(html).toContain('data-theme="light"');
    expect(html).toContain(`<style id="${THEME_STYLE_ELEMENT_ID}">`);
    expect(html).toContain("--color-primary-600: #059669;");
  });

  it("ưu tiên cookie thi360_theme hợp lệ và rơi về chế độ mặc định hệ thống khi cookie sai", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn(async () =>
        Response.json({
          palette: { presetId: "thi360-green" },
          mode: "light",
          version: 1,
        }),
      ),
    );

    ssrState.themeCookie = "dark";
    const darkHtml = renderToStaticMarkup(await RootLayout({ children: <LoginPage /> }));
    expect(darkHtml).toContain('data-theme="dark"');

    ssrState.themeCookie = "sepia";
    const fallbackHtml = renderToStaticMarkup(await RootLayout({ children: <LoginPage /> }));
    expect(fallbackHtml).toContain('data-theme="light"');
  });

  it("render <html lang=\"en\"> và nút chuyển đổi trên /dang-nhap khi locale=en hoặc en-GB", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn(async () =>
        Response.json({
          palette: { presetId: "thi360-green" },
          mode: "light",
          version: 1,
        }),
      ),
    );
    ssrState.locale = "en-GB";
    const html = renderToStaticMarkup(await RootLayout({ children: <LoginPage /> }));
    expect(html).toContain('<html lang="en"');
    expect(html).toContain("Sign in");
    expect(html).toContain("Switch to dark mode");
    expect(html).toContain("Change language");
  });
});
