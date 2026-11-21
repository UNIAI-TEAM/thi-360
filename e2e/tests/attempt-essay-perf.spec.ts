import { expect, test } from "@playwright/test";
import { createVerifiedAccount, WEB_URL } from "./helpers/mail";
import {
  ensureDsReady,
  purchaseExamViaUi,
  startAttemptFromPrepare,
} from "./helpers/attempt-fixtures";

interface JsChunkRecord {
  url: string;
  body: string;
}

const TIPTAP_RE = /ProseMirror|@tiptap\/pm|tiptap/i;
const MATHLIVE_RE = /mathlive|MathfieldElement|compute-engine/i;

function percentile(sorted: number[], p: number): number {
  if (sorted.length === 0) {
    return 0;
  }
  const index = Math.min(sorted.length - 1, Math.max(0, Math.ceil((p / 100) * sorted.length) - 1));
  return sorted[index]!;
}

test.describe("Hiệu năng soạn tự luận & tách gói lazy chunk (AC-26, AC-27)", () => {
  test.setTimeout(240_000);

  test.beforeAll(async ({ browser }) => {
    test.setTimeout(300_000);
    await ensureDsReady(browser);
  });

  test("AC-27: đề không tự luận không tải TipTap; đề có ESSAY lazy-load TipTap; không gói nào chứa mathlive", async ({
    browser,
  }) => {
    const { cookies } = await createVerifiedAccount("perf.ac27");

    // Giai đoạn 1: Đề thuần trắc nghiệm / trả lời ngắn (DS-HSA-PART-MATH-MINI) trên context sạch
    const nonEssayCtx = await browser.newContext();
    let nonEssayUrls = new Set<string>();
    try {
      await nonEssayCtx.addCookies(cookies);
      const page1 = await nonEssayCtx.newPage();

      const { entitlementId: mathEntId } = await purchaseExamViaUi(page1, "DS-HSA-PART-MATH-MINI");
      const mathAttemptId = await startAttemptFromPrepare(page1, mathEntId);

      // Mở page mới để đo duy nhất các JS chunk khi tải màn làm bài không tự luận
      await page1.close();
      const measurePage1 = await nonEssayCtx.newPage();
      const nonEssayChunks: JsChunkRecord[] = [];
      measurePage1.on("response", async (response) => {
        const url = response.url();
        const contentType = response.headers()["content-type"] ?? "";
        if (url.includes("/_next/static/chunks/") && contentType.includes("javascript")) {
          const body = await response.text().catch(() => "");
          nonEssayChunks.push({ url, body });
        }
      });

      await measurePage1.goto(`${WEB_URL}/attempts/${encodeURIComponent(mathAttemptId)}`);
      await expect(measurePage1.getByRole("button", { name: "Nộp bài" })).toBeVisible({ timeout: 25_000 });
      await expect(measurePage1.locator("main fieldset")).toBeVisible();

      expect(nonEssayChunks.length).toBeGreaterThan(0);
      for (const chunk of nonEssayChunks) {
        expect(chunk.body, `Chunk không được chứa TipTap ở đề không tự luận: ${chunk.url}`).not.toMatch(TIPTAP_RE);
        expect(chunk.body, `Chunk không được chứa mathlive: ${chunk.url}`).not.toMatch(MATHLIVE_RE);
      }
      nonEssayUrls = new Set(nonEssayChunks.map((chunk) => chunk.url));
    } finally {
      await nonEssayCtx.close();
    }

    // Giai đoạn 2: Đề có câu ESSAY ở Phần 1 (DS-BCA-FULL-MINI) trên context sạch
    const essayCtx = await browser.newContext();
    try {
      await essayCtx.addCookies(cookies);
      const page2 = await essayCtx.newPage();

      const { entitlementId: bcaEntId } = await purchaseExamViaUi(page2, "DS-BCA-FULL-MINI");
      const bcaAttemptId = await startAttemptFromPrepare(page2, bcaEntId, { preChoiceCode: "CA1" });

      await page2.close();
      const measurePage2 = await essayCtx.newPage();
      const essayChunks: JsChunkRecord[] = [];
      measurePage2.on("response", async (response) => {
        const url = response.url();
        const contentType = response.headers()["content-type"] ?? "";
        if (url.includes("/_next/static/chunks/") && contentType.includes("javascript")) {
          const body = await response.text().catch(() => "");
          essayChunks.push({ url, body });
        }
      });

      await measurePage2.goto(`${WEB_URL}/attempts/${encodeURIComponent(bcaAttemptId)}`);
      await expect(measurePage2.locator('main fieldset [contenteditable="true"]').first()).toBeVisible({
        timeout: 30_000,
      });

      const lazyLoadedTiptap = essayChunks.some(
        (chunk) => !nonEssayUrls.has(chunk.url) && TIPTAP_RE.test(chunk.body),
      );
      expect(lazyLoadedTiptap, "Đề tự luận phải lazy-load chunk chứa TipTap/ProseMirror").toBe(true);

      for (const chunk of essayChunks) {
        expect(chunk.body, `Chunk đề tự luận không được chứa mathlive: ${chunk.url}`).not.toMatch(MATHLIVE_RE);
      }
    } finally {
      await essayCtx.close();
    }
  });

  test("AC-26: gõ tự luận 60 ký tự dưới CDP CPU throttling 4x có p95 keydown->rAF <= 200ms", async ({
    context,
    page,
  }) => {
    const { cookies } = await createVerifiedAccount("perf.ac26");
    await context.addCookies(cookies);

    const { entitlementId } = await purchaseExamViaUi(page, "DS-BCA-FULL-MINI");
    await startAttemptFromPrepare(page, entitlementId, { preChoiceCode: "CA1" });

    const editor = page.locator('main fieldset [contenteditable="true"]').first();
    await expect(editor).toBeVisible({ timeout: 30_000 });
    await editor.click();

    await page.evaluate(() => {
      const target = document.querySelector('main fieldset [contenteditable="true"]');
      if (!target) {
        throw new Error("Không tìm thấy trình soạn tự luận");
      }
      const win = window as Window & { __ac26Samples?: number[] };
      win.__ac26Samples = [];
      target.addEventListener("keydown", () => {
        const start = performance.now();
        requestAnimationFrame(() => {
          win.__ac26Samples?.push(performance.now() - start);
        });
      });
    });

    const cdp = await context.newCDPSession(page);
    try {
      await cdp.send("Emulation.setCPUThrottlingRate", { rate: 4 });
      const sampleText = "Thi thu dai hoc mon Ngu van giup thi sinh luyen lap luan ro.";
      await page.keyboard.type(sampleText, { delay: 40 });
      await page.waitForTimeout(300);
    } finally {
      await cdp.send("Emulation.setCPUThrottlingRate", { rate: 1 }).catch(() => {});
      await cdp.detach().catch(() => {});
    }

    const samples = await page.evaluate(() => {
      const win = window as Window & { __ac26Samples?: number[] };
      return win.__ac26Samples ?? [];
    });

    expect(samples.length).toBeGreaterThanOrEqual(50);
    const sorted = [...samples].sort((a, b) => a - b);
    const p50 = percentile(sorted, 50);
    const p95 = percentile(sorted, 95);
    const max = sorted[sorted.length - 1] ?? 0;

    console.log(
      `[AC-26 ESSAY PERF] count=${samples.length} p50=${p50.toFixed(1)}ms p95=${p95.toFixed(1)}ms max=${max.toFixed(1)}ms`,
    );
    expect(p95).toBeLessThanOrEqual(200);
  });
});
