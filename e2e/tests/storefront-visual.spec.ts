import { expect, request as playwrightRequest, type Browser, type Page, test } from "@playwright/test";
import {
  cellLabel,
  expectMatrixClean,
  launchMatrixBrowser,
  matrixCells,
  openMatrixContext,
  type MatrixCell,
} from "./helpers/visual-matrix";

/**
 * AC-20 (UNI-2730, FS-1): `/`, `/exams/<code>`, trang ngừng bán và trang 404 đúng khi sáng và tối, vi và en, 390px và
 * 1280px: không tràn ngang, tương phản >= 4,5, không lẫn ngôn ngữ. Gọi `helpers/visual-matrix.ts` của FS-4, không sửa.
 * Tên bài, tên phần, ngữ liệu mô tả là nội dung đề (`data-exam-content`) nên phép kiểm lẫn ngôn ngữ bỏ qua.
 * Trang ngừng bán cần `THI360_E2E_DISCONTINUED_CODE` (mã trong tệp hằng số của DS); thiếu thì ca đó bỏ qua.
 */
const API_URL = (process.env.THI360_API_URL ?? "http://localhost:8080").replace(/\/+$/, "");
const WEB_URL = (process.env.THI360_WEB_URL ?? "http://localhost:3000").replace(/\/+$/, "");
const DISCONTINUED_CODE = process.env.THI360_E2E_DISCONTINUED_CODE?.trim();

let browser: Browser;
let productCode = "";

test.beforeAll(async () => {
  browser = await launchMatrixBrowser();
  const api = await playwrightRequest.newContext();
  try {
    const response = await api.get(`${API_URL}/api/v1/public/storefront/products?size=12`);
    expect(response.status(), "cần dữ liệu DS và bảng chiếu đã dựng").toBe(200);
    const page = (await response.json()) as { items: Array<{ code: string }> };
    expect(page.items.length, "DS phải công bố ít nhất một bài đang bán").toBeGreaterThan(0);
    productCode = page.items[0]!.code;
  } finally {
    await api.dispose();
  }
});

test.afterAll(async () => {
  await browser.close();
});

async function settle(page: Page): Promise<void> {
  // Phần theo phiên chạy phía trình duyệt: chờ hết khung giữ chỗ trước khi đo.
  await expect(page.locator('[aria-busy="true"]')).toHaveCount(0);
  await page.evaluate(() => document.fonts.ready);
}

async function check(screen: string, path: string, cell: MatrixCell, ready: (page: Page) => Promise<void>) {
  const context = await openMatrixContext(browser, cell, WEB_URL);
  const page = await context.newPage();
  const errors: string[] = [];
  page.on("console", (message) => {
    if (message.type() === "error") errors.push(message.text());
  });
  page.on("pageerror", (error) => errors.push(String(error)));
  try {
    await page.goto(`${WEB_URL}${path}`);
    await ready(page);
    await settle(page);
    await expectMatrixClean(page, screen, cell, { elementOverflow: { allowAccessibleScroller: true } });
    expect(errors, `${screen} ${cellLabel(cell)}: không có lỗi console`).toEqual([]);
  } finally {
    await context.close();
  }
}

const HEADING = {
  catalog: { vi: "Chọn bài thi", en: "Choose an exam" },
  gone: { vi: "Bài thi đã ngừng bán", en: "This exam is no longer on sale" },
  notFound: { vi: "Không tìm thấy bài thi", en: "Exam not found" },
  buy: { vi: "Mua bài thi", en: "Buy exam" },
} as const;

for (const cell of matrixCells()) {
  test(`trang giới thiệu: ${cellLabel(cell)}`, async () => {
    test.setTimeout(120_000);
    await check("storefront-landing", "/", cell, async (page) => {
      await expect(page.getByRole("heading", { level: 2, name: HEADING.catalog[cell.lang] })).toBeVisible();
      await expect(page.getByRole("article").first()).toBeVisible();
      await expect(page.getByRole("link", { name: HEADING.buy[cell.lang] }).first()).toBeVisible();
    });
  });

  test(`chi tiết bài thi: ${cellLabel(cell)}`, async () => {
    test.setTimeout(120_000);
    await check("storefront-detail", `/exams/${productCode}`, cell, async (page) => {
      await expect(page.getByRole("heading", { level: 1 })).toBeVisible();
      await expect(page.getByRole("complementary").getByRole("link", { name: HEADING.buy[cell.lang] })).toBeVisible();
    });
  });

  test(`bài thi đã ngừng bán: ${cellLabel(cell)}`, async () => {
    test.skip(!DISCONTINUED_CODE, "chưa đặt THI360_E2E_DISCONTINUED_CODE (mã bài ngừng bán trong tệp hằng số của DS)");
    test.setTimeout(120_000);
    await check("storefront-gone", `/exams/${DISCONTINUED_CODE}`, cell, async (page) => {
      await expect(page.getByRole("heading", { level: 1, name: HEADING.gone[cell.lang] })).toBeVisible();
    });
  });

  test(`bài thi không có (404): ${cellLabel(cell)}`, async () => {
    test.setTimeout(120_000);
    await check("storefront-404", "/exams/KHONG-CO-MA-NAY", cell, async (page) => {
      await expect(page.getByRole("heading", { name: HEADING.notFound[cell.lang] })).toBeVisible();
    });
  });
}
