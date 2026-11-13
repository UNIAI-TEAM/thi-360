import { THEME_STYLE_ELEMENT_ID } from "@thi360/ui";
import type { ReactElement } from "react";
import { afterEach, describe, expect, it, vi } from "vitest";
import RootLayout from "./layout";
vi.mock("next/font/google", () => ({
  Be_Vietnam_Pro: () => ({ variable: "--font-be-vietnam-pro" }),
}));

vi.mock("next-intl", () => ({
  NextIntlClientProvider: ({ children }: { children: unknown }) => children,
}));

vi.mock("next-intl/server", () => ({
  getLocale: async () => "vi",
}));

vi.mock("@/providers/query-provider", () => ({
  QueryProvider: ({ children }: { children: unknown }) => children,
}));

vi.mock("@/features/session/session-provider", () => ({
  SessionProvider: ({ children }: { children: unknown }) => children,
}));

afterEach(() => {
  vi.unstubAllGlobals();
});

describe("RootLayout appearance SSR", () => {
  it("chèn <style> cho cả sáng và tối trong <head> và data-theme lên <html>, không đặt inline style", async () => {
    const fetchMock = vi.fn(async () =>
      Response.json({
        palette: { presetId: "navy-teal" },
        mode: "dark",
        version: 7,
      }),
    );
    vi.stubGlobal("fetch", fetchMock);

    const tree = await RootLayout({ children: "ok" });
    expect(tree.props["data-theme"]).toBe("dark");
    expect(tree.props.style).toBeUndefined();
    const children = Array.isArray(tree.props.children) ? tree.props.children : [tree.props.children];
    const headEl = children.find((node: ReactElement) => node?.type === "head") as ReactElement<{
      children: ReactElement<{ id: string; dangerouslySetInnerHTML: { __html: string } }>;
    }>;
    expect(headEl).toBeDefined();
    const styleEl = headEl.props.children;
    expect(styleEl.type).toBe("style");
    expect(styleEl.props.id).toBe(THEME_STYLE_ELEMENT_ID);
    const css = styleEl.props.dangerouslySetInnerHTML.__html;
    expect(css).toContain(':root[data-theme="light"]');
    expect(css).toContain(':root[data-theme="dark"]');
    expect(css).toContain("--color-primary-600: #0f3c6e;");
    expect(css).toContain("--color-page: #f7f8f9;");
    expect(css).toContain("--color-page: #0f1418;");
    expect(fetchMock).toHaveBeenCalledTimes(1);
    const [request, init] = fetchMock.mock.calls[0] as unknown as [
      Request,
      RequestInit & { next?: { revalidate?: number; tags?: string[] } },
    ];
    expect(request.url).toMatch(/\/api\/v1\/appearance$/);
    expect(init.next).toEqual({ revalidate: 60, tags: ["appearance"] });
  });

  it("rơi về cấu hình mặc định khi fetch /api/v1/appearance lỗi", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn(async () => {
        throw new Error("ECONNREFUSED");
      }),
    );

    const tree = await RootLayout({ children: "ok" });
    expect(tree.props["data-theme"]).toBe("light");
    expect(tree.props.style).toBeUndefined();
    const children = Array.isArray(tree.props.children) ? tree.props.children : [tree.props.children];
    const headEl = children.find((node: ReactElement) => node?.type === "head") as ReactElement<{
      children: ReactElement<{ dangerouslySetInnerHTML: { __html: string } }>;
    }>;
    const css = headEl.props.children.props.dangerouslySetInnerHTML.__html;
    expect(css).toContain("--color-primary-600: #059669;");
  });
});
