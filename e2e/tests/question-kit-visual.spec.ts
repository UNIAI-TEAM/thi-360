import { expect, test, type Browser, type BrowserContext, type Page } from "@playwright/test";
import {
  applyMatrixCookies,
  auditMixedLanguage,
  cellLabel,
  expectMatrixClean,
  launchMatrixBrowser,
  matrixCells,
  openMatrixContext,
  type MatrixCell,
} from "./helpers/visual-matrix";

const WEB_URL = (process.env.THI360_WEB_URL ?? "http://localhost:3000").replace(/\/+$/, "");
const DEV_PASSWORD = process.env.THI360_DEV_PASSWORD ?? "Thi360@dev";
const SAMPLE_COUNT = 8;

// Trang thử `/dev/question-kit` là 404 ở bản build (AC-18): spec chạy bằng `next dev`.
test.skip(process.env.E2E_USE_BUILT === "1", "trang thử chỉ có khi chạy dev");

let browser: Browser;
let sessionState: Awaited<ReturnType<BrowserContext["storageState"]>>;

const REVIEW_LABEL = { vi: "Xem lại", en: "Review" } as const;

async function loginStudent(page: Page): Promise<void> {
  await page.goto(`${WEB_URL}/login`);
  await page.getByLabel(/Tên đăng nhập hoặc email|Username or email/).fill("student01");
  await page.getByLabel(/Mật khẩu|Password/).fill(DEV_PASSWORD);
  await page.getByRole("button", { name: /^(Đăng nhập|Sign in)$/ }).click();
  await page.waitForURL((url) => !url.pathname.startsWith("/login"));
}

test.beforeAll(async () => {
  browser = await launchMatrixBrowser();
  const context = await browser.newContext();
  await loginStudent(await context.newPage());
  sessionState = await context.storageState();
  await context.close();
});

test.afterAll(async () => {
  await browser.close();
});

/** Context của ô, đã đăng nhập `student01` (một lần cho cả file) và đặt lại cookie giao diện, ngôn ngữ. */
async function openCell(cell: MatrixCell): Promise<{ context: BrowserContext; page: Page; errors: string[] }> {
  const context = await openMatrixContext(browser, cell, WEB_URL);
  await context.addCookies(sessionState.cookies);
  await applyMatrixCookies(context, cell, WEB_URL);
  const page = await context.newPage();
  const errors: string[] = [];
  page.on("console", (message) => {
    if (message.type() === "error") errors.push(message.text());
  });
  page.on("pageerror", (error) => errors.push(String(error)));
  await page.goto(`${WEB_URL}/dev/question-kit`);
  await expect(page.locator("[data-sample]")).toHaveCount(SAMPLE_COUNT);
  await waitForKit(page);
  return { context, page, errors };
}

/** Điền khuyết dựng ô nhập sau khi hydrate: chờ đủ ô trước khi chụp. */
async function waitForKit(page: Page): Promise<void> {
  await expect(page.locator('[data-sample="cloze"] input')).toHaveCount(2);
  await page.evaluate(() => document.fonts.ready);
}

for (const cell of matrixCells()) {
  test(`8 dạng câu ở hai chế độ: ${cellLabel(cell)}`, async () => {
    test.setTimeout(120_000);
    const { context, page, errors } = await openCell(cell);
    try {
      await expectMatrixClean(page, "question-kit-answer", cell);
      await page.getByRole("button", { name: REVIEW_LABEL[cell.lang], exact: true }).click();
      await expect(page.getByTestId("value-single")).toHaveCount(0);
      await waitForKit(page);
      await expectMatrixClean(page, "question-kit-review", cell);
      expect(errors, "không có lỗi console (key React, hydrate…)").toEqual([]);
    } finally {
      await context.close();
    }
  });
}

test.describe("bố cục Bộ câu hỏi (AC-14)", () => {
  test("ngữ liệu ghim cạnh câu con ở 1280px, xếp dọc ở 390px", async () => {
    test.setTimeout(90_000);
    for (const cell of matrixCells().filter((item) => item.mode === "light" && item.lang === "vi")) {
      const { context, page } = await openCell(cell);
      try {
        const set = page.locator('[data-sample="set"]');
        const passage = set.locator("aside").first();
        const firstChild = set.locator("section").first();
        const a = (await passage.boundingBox())!;
        const b = (await firstChild.boundingBox())!;
        if (cell.width === 1280) {
          expect(a.x + a.width, "ngữ liệu nằm bên trái câu con").toBeLessThanOrEqual(b.x + 1);
          expect(Math.abs(a.y - b.y), "hai khối ngang hàng").toBeLessThan(80);
          const position = await passage.evaluate((el) => getComputedStyle(el).position);
          expect(position).toBe("sticky");
        } else {
          expect(a.y + a.height, "ngữ liệu nằm trên câu con").toBeLessThanOrEqual(b.y + 1);
          expect(Math.abs(a.x - b.x), "cùng cột").toBeLessThan(4);
        }
      } finally {
        await context.close();
      }
    }
  });
});

test.describe("phép kiểm lẫn ngôn ngữ của visual-matrix (AC-23)", () => {
  async function blank(html: string): Promise<{ context: BrowserContext; page: Page }> {
    const context = await browser.newContext();
    const page = await context.newPage();
    await page.setContent(`<!doctype html><html lang="en"><body>${html}</body></html>`);
    return { context, page };
  }

  test("nội dung đề không tính lẫn ngôn ngữ", async () => {
    const exam = `<section data-exam-content aria-label="Bài thi thử"><h2>Câu hỏi tiếng Việt</h2><p>Tính giới hạn của hàm số.</p><input placeholder="Nhập đáp số" /></section>`;
    const clean = await blank(`<main><h1>Question 1</h1><button>Next</button>${exam}</main>`);
    try {
      expect(await auditMixedLanguage(clean.page, "en")).toEqual([]);
    } finally {
      await clean.context.close();
    }
    const dirtyText = await blank(`<main><h1>Question 1</h1><button>Nộp bài</button>${exam}</main>`);
    try {
      const findings = await auditMixedLanguage(dirtyText.page, "en");
      expect(findings).toEqual([{ kind: "text", value: "Nộp bài" }]);
    } finally {
      await dirtyText.context.close();
    }
    const dirtyAttr = await blank(`<main><button aria-label="Đánh dấu xem lại">Flag</button>${exam}</main>`);
    try {
      const findings = await auditMixedLanguage(dirtyAttr.page, "en");
      expect(findings).toEqual([{ kind: "attribute", value: "Đánh dấu xem lại" }]);
    } finally {
      await dirtyAttr.context.close();
    }
  });
});
