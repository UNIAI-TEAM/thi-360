import { expect, test, type Browser, type BrowserContext, type Page } from "@playwright/test";
import path from "node:path";
import {
  applyMatrixCookies,
  auditContrast,
  auditElementOverflow,
  auditMixedLanguage,
  cellLabel,
  expectMatrixClean,
  launchMatrixBrowser,
  matrixCells,
  openMatrixContext,
  visualDir,
  type MatrixCell,
} from "./helpers/visual-matrix";

const WEB_URL = (process.env.THI360_WEB_URL ?? "http://localhost:3000").replace(/\/+$/, "");
const DEV_PASSWORD = process.env.THI360_DEV_PASSWORD ?? "Thi360@dev";
const SAMPLE_COUNT = 11;

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
  await expect(page.locator('[data-sample="clozeText"] input')).toHaveCount(2);
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
      await expectMatrixClean(page, "question-kit-review", cell, { elementOverflow: true });
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

test.describe("xem lại từng dạng câu ở 390px (TV1-1)", () => {
  for (const cell of matrixCells().filter((item) => item.width === 390)) {
    test(`mỗi mẫu vừa khung, không bị cắt: ${cellLabel(cell)}`, async () => {
      test.setTimeout(120_000);
      const { context, page } = await openCell(cell);
      try {
        await page.getByRole("button", { name: REVIEW_LABEL[cell.lang], exact: true }).click();
        await expect(page.getByTestId("value-single")).toHaveCount(0);
        await waitForKit(page);
        const keys = await page
          .locator("[data-sample]")
          .evaluateAll((elements) => elements.map((element) => element.getAttribute("data-sample") ?? ""));
        expect(keys, "đủ mẫu của trang thử").toHaveLength(SAMPLE_COUNT);
        for (const key of keys) {
          const scope = `[data-sample="${key}"]`;
          const sample = page.locator(scope);
          await sample.screenshot({
            path: path.join(visualDir(), `question-kit-review-${key}-${cell.width}-${cell.mode}-${cell.lang}.png`),
          });
          const box = (await sample.boundingBox())!;
          expect(box.x + box.width, `${key}: thẻ nằm trong khung ${cell.width}px`).toBeLessThanOrEqual(cell.width + 1);
          expect(await auditElementOverflow(page, { scope }), `${key}: phần tử bị cắt hoặc phải cuộn ngang`).toEqual([]);
          expect(await auditContrast(page, { scope }), `${key}: tương phản`).toEqual([]);
        }
      } finally {
        await context.close();
      }
    });
  }
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

  /** Trang trắng ở ngôn ngữ `lang` (mặc định `en`), `head` là phần tử đặt trong `<head>` (`<title>`, `<style>`). */
  async function inBlank<T>(
    html: string,
    run: (page: Page) => Promise<T>,
    options: { lang?: "vi" | "en"; head?: string } = {},
  ): Promise<T> {
    const context = await browser.newContext();
    try {
      const page = await context.newPage();
      await page.setContent(
        `<!doctype html><html lang="${options.lang ?? "en"}"><head>${options.head ?? ""}</head><body>${html}</body></html>`,
      );
      return await run(page);
    } finally {
      await context.close();
    }
  }

  test("tiêu đề tab (document.title) cũng bị kiểm (R1-12)", async () => {
    const body = "<main><h1>Question 1</h1></main>";
    expect(
      await inBlank(body, (page) => auditMixedLanguage(page, "en"), { head: "<title>Thử bộ câu hỏi</title>" }),
    ).toEqual([{ kind: "attribute", value: "Thử bộ câu hỏi" }]);
    expect(
      await inBlank("<main><h1>Câu hỏi</h1></main>", (page) => auditMixedLanguage(page, "vi"), {
        lang: "vi",
        head: "<title>Question kit</title>",
      }),
    ).toEqual([{ kind: "attribute", value: "Question kit" }]);
    expect(
      await inBlank("<main><h1>Câu hỏi</h1></main>", (page) => auditMixedLanguage(page, "vi"), {
        lang: "vi",
        head: "<title>Thử bộ câu hỏi · Thi 360</title>",
      }),
    ).toEqual([]);
  });

  test("trang vi bắt được chuỗi tiếng Anh của bộ câu hỏi và catalog en (R1-13)", async () => {
    const audit = (html: string) => inBlank(html, (page) => auditMixedLanguage(page, "vi"), { lang: "vi" });
    expect(await audit("<table><tr><th>Points</th></tr></table>")).toEqual([{ kind: "text", value: "Points" }]);
    // "Left column", "Not chosen": không có trong danh sách từ thường gặp, chỉ lớp catalog `en` bắt được.
    expect(await audit("<table><tr><th>Left column</th><td>Not chosen</td></tr></table>")).toEqual([
      { kind: "text", value: "Left column" },
      { kind: "text", value: "Not chosen" },
    ]);
    expect(await audit(`<button aria-label="Your choice">Chọn</button>`)).toEqual([
      { kind: "attribute", value: "Your choice" },
    ]);
    expect(await audit("<table><tr><th>Điểm</th><th>Cột trái</th></tr><tr><td>Thi 360</td></tr></table>")).toEqual([]);
  });

  test("chữ gợi ý (placeholder) của ô trống được kiểm tương phản (R1-22)", async () => {
    const field = `<input placeholder="Nhập đáp số" />`;
    const style = (placeholder: string) =>
      `<style>input{color:#000;background:#fff}input::placeholder{color:${placeholder};opacity:1}</style>`;
    const light = await inBlank(field, (page) => auditContrast(page), { head: style("#cccccc") });
    expect(light).toHaveLength(1);
    expect(light[0]).toMatchObject({ element: "input", text: "Nhập đáp số" });
    expect(light[0]!.ratio).toBeLessThan(4.5);
    expect(await inBlank(field, (page) => auditContrast(page), { head: style("#555555") })).toEqual([]);
  });

  test("phép kiểm cấp phần tử bắt bảng bị cắt trong thẻ (TV1-1)", async () => {
    const scroller = (attrs: string) =>
      `<div ${attrs} style="width:200px;overflow-x:auto"><table><tr><td style="min-width:400px">Điểm</td></tr></table></div>`;
    const clipped = await inBlank(scroller(""), (page) => auditElementOverflow(page));
    expect(clipped).toHaveLength(1);
    expect(clipped[0]).toMatchObject({ element: "div", clientWidth: 200, accessibleScroller: false });
    const named = scroller(`role="region" aria-label="Bảng điểm" tabindex="0"`);
    expect(await inBlank(named, (page) => auditElementOverflow(page))).toHaveLength(1);
    expect(await inBlank(named, (page) => auditElementOverflow(page, { allowAccessibleScroller: true }))).toEqual([]);
    expect(
      await inBlank(`<div style="width:200px;overflow-x:auto"><p>Ngắn</p></div>`, (page) => auditElementOverflow(page)),
    ).toEqual([]);
  });

  test("phép kiểm cấp phần tử bỏ qua phần tử ẩn thị giác nhưng vẫn bắt bảng bị cắt thật (TV2-1)", async () => {
    const head = `<style>
      .sr-only{position:absolute;width:1px;height:1px;padding:0;margin:-1px;overflow:hidden;clip:rect(0,0,0,0);white-space:nowrap;border-width:0}
      .math{position:absolute;width:1px;height:1px;overflow:hidden;clip:rect(1px,1px,1px,1px)}
      .inset{position:absolute;width:100px;height:20px;overflow:hidden;clip-path:inset(50%)}
    </style>`;
    const wide = `<span style="display:inline-block;width:400px">Điểm rất dài</span>`;
    const hidden = [
      `<span class="sr-only">${wide}</span>`,
      `<span class="math">${wide}</span>`,
      `<span class="inset">${wide}</span>`,
      `<table><thead class="sr-only"><tr><th style="min-width:400px">Cột</th></tr></thead></table>`,
      `<div style="display:none;width:50px;overflow:hidden">${wide}</div>`,
      `<div style="visibility:hidden;width:50px;overflow:hidden">${wide}</div>`,
      `<div class="sr-only"><div style="width:50px;overflow-x:auto">${wide}</div></div>`,
    ].join("");
    expect(await inBlank(hidden, (page) => auditElementOverflow(page), { head })).toEqual([]);
    // Ẩn thị giác nằm cạnh cột bị cắt thật: chỉ cột thật bị báo.
    const clipped = await inBlank(
      `${hidden}<div style="width:200px;overflow-x:hidden"><table><tr><td style="min-width:400px">Điểm</td></tr></table></div>`,
      (page) => auditElementOverflow(page),
      { head },
    );
    expect(clipped).toHaveLength(1);
    expect(clipped[0]).toMatchObject({ element: "div", clientWidth: 200, accessibleScroller: false });
  });
});
