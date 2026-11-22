import { expect, test, type Page, type Response } from "@playwright/test";

const WEB_URL = (process.env.THI360_WEB_URL ?? "http://localhost:3000").replace(/\/+$/, "");

/**
 * AC-5: JS mà trình duyệt tải ở `/` và `/login` không chứa KaTeX. `@thi360/ui` (tệp gốc) không xuất lại
 * `rich-text`, `question`; mã hiển thị toán chỉ vào trang nào import cổng riêng. Chạy trên bản build:
 * `E2E_USE_BUILT=1 pnpm --filter @thi360/e2e test tests/ui-bundle-boundary.spec.ts`.
 */
async function collectScripts(page: Page, route: string): Promise<{ url: string; body: string }[]> {
  const pending: Promise<{ url: string; body: string } | null>[] = [];
  const onResponse = (response: Response) => {
    const type = response.headers()["content-type"] ?? "";
    if (!/javascript/.test(type)) return;
    pending.push(
      response
        .text()
        .then((body) => ({ url: response.url(), body }))
        .catch(() => null),
    );
  };
  page.on("response", onResponse);
  await page.goto(`${WEB_URL}${route}`, { waitUntil: "networkidle" });
  page.off("response", onResponse);
  return (await Promise.all(pending)).filter((item): item is { url: string; body: string } => item !== null);
}

for (const route of ["/", "/login"]) {
  test(`JS của ${route} không chứa KaTeX`, async ({ page }) => {
    const scripts = await collectScripts(page, route);
    expect(scripts.length, "đã tải được JS của trang").toBeGreaterThan(0);
    const offenders = scripts.filter((script) => /katex/i.test(script.body)).map((script) => script.url);
    expect(offenders).toEqual([]);
  });
}
