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
  it("chèn biến CSS và data-theme từ GET /api/v1/appearance lên thẻ <html>", async () => {
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
    expect(tree.props.style["--color-primary-600"]).toBe("#0f3c6e");
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
    expect(tree.props.style["--color-primary-600"]).toBe("#059669");
  });
});
