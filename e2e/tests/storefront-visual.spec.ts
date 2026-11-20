import { expect, request as playwrightRequest, type Browser, type Page, test } from "@playwright/test";
import { loginAdmin } from "./helpers/editor-qa";
import { discontinuedExam, waitForPublishedExamSet } from "./helpers/published-exam-set";
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
 * Chỉ ô có sản phẩm (chi tiết, ngừng bán) dùng dữ liệu DS: chúng chờ bộ DS và lấy bài đầu danh mục khi chạy, không
 * qua `beforeAll` chung, nên ô trang giới thiệu và 404 chạy được dù danh mục trống hay DS chưa xong.
 */
const API_URL = (process.env.THI360_API_URL ?? "http://localhost:8080").replace(/\/+$/, "");
const WEB_URL = (process.env.THI360_WEB_URL ?? "http://localhost:3000").replace(/\/+$/, "");

let browser: Browser;

test.beforeAll(async () => {
  browser = await launchMatrixBrowser();
});

test.afterAll(async () => {
  await browser.close();
});

let dsReady: Promise<void> | undefined;
let firstProduct: Promise<string> | undefined;

/** Chờ bộ DS đủ ở danh sách quản trị (một lần cho cả lần chạy); chỉ ô có sản phẩm gọi. */
function waitForDsData(): Promise<void> {
  dsReady ??= (async () => {
    const context = await browser.newContext();
    try {
      await loginAdmin(context, "vi");
      await waitForPublishedExamSet(context.request, { timeoutMs: 240_000 });
    } finally {
      await context.close();
    }
  })();
  return dsReady;
}

/** Mã bài đầu danh mục công khai sau khi DS xong. */
function firstProductCode(): Promise<string> {
  firstProduct ??= (async () => {
    await waitForDsData();
    const api = await playwrightRequest.newContext();
    try {
      const response = await api.get(`${API_URL}/api/v1/public/storefront/products?size=12`);
      expect(response.status(), "cần dữ liệu DS và bảng chiếu đã dựng").toBe(200);
      const page = (await response.json()) as { items: Array<{ code: string }> };
      expect(page.items.length, "DS phải công bố ít nhất một bài đang bán").toBeGreaterThan(0);
      return page.items[0]!.code;
    } finally {
      await api.dispose();
    }
  })();
  return firstProduct;
}

let partNameForms: Promise<Set<string>> | undefined;

/** Dạng chữ hợp lệ của tên phần (PART) theo bộ lọc trang dùng: `name` và `kindCode · name`. */
function knownPartNameForms(): Promise<Set<string>> {
  partNameForms ??= (async () => {
    const api = await playwrightRequest.newContext();
    try {
      const response = await api.get(`${API_URL}/api/v1/public/storefront/filters`);
      expect(response.status(), "API bộ lọc danh mục phải trả được").toBe(200);
      const data = (await response.json()) as { parts: Array<{ kindCode: string; name: string }> };
      const forms = new Set<string>();
      for (const part of data.parts) {
        forms.add(part.name);
        forms.add(`${part.kindCode} · ${part.name}`);
      }
      return forms;
    } finally {
      await api.dispose();
    }
  })();
  return partNameForms;
}

async function settle(page: Page): Promise<void> {
  // Phần theo phiên chạy phía trình duyệt: chờ hết khung giữ chỗ trước khi đo.
  await expect(page.locator('[aria-busy="true"]')).toHaveCount(0);
  await page.evaluate(() => document.fonts.ready);
}

type ConsoleError = { text: string; url?: string };

function escapeRegExp(value: string): string {
  return value.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
}

/**
 * Lỗi tài nguyên được phép (lọc theo URL, không chỉ theo dòng chữ): khách không có phiên thì `/api/v1/me` và
 * `/api/v1/auth/refresh` trả 401 đúng thiết kế (AC-11), và ô 404 thì bản thân tài liệu điều hướng trả 404.
 * Mọi lỗi khác, kể cả prefetch RSC tới trang chưa có, vẫn làm ca kiểm thử đỏ.
 */
function isExpectedResourceError(error: ConsoleError, path: string, allowDocument404: boolean): boolean {
  const status = /^Failed to load resource: the server responded with a status of (\d+)/.exec(error.text)?.[1];
  if (!status || !error.url) return false;
  const pathname = new URL(error.url).pathname;
  if (status === "401") return pathname === "/api/v1/me" || pathname === "/api/v1/auth/refresh";
  return status === "404" && allowDocument404 && pathname === path;
}

async function check(
  screen: string,
  path: string,
  cell: MatrixCell,
  ready: (page: Page) => Promise<void>,
  options: { allowDocument404?: boolean; allowDocumentTitle?: boolean } = {},
) {
  const context = await openMatrixContext(browser, cell, WEB_URL);
  const page = await context.newPage();
  const errors: ConsoleError[] = [];
  page.on("console", (message) => {
    if (message.type() === "error") errors.push({ text: message.text(), url: message.location().url });
  });
  page.on("pageerror", (error) => errors.push({ text: String(error) }));
  try {
    await page.goto(`${WEB_URL}${path}`);
    await ready(page);
    await settle(page);
    // `<select aria-hidden>` của Radix (visual-4 F2) mang chữ option trần; chỉ miễn option bằng văn một chuỗi
    // nội dung đề: chữ trong `[data-exam-content]` đang có trên trang hoặc tên phần (PART) theo bộ lọc
    // (DESIGN mục 8). Nhãn giao diện trong select ẩn vẫn bị phép kiểm lẫn ngôn ngữ xử.
    // Tiêu đề tài liệu mang tên bài như lưu (AC-17) nhưng `<title>` không gắn được `data-exam-content`: miễn đúng
    // giá trị tiêu đề khi ô cờ `allowDocumentTitle`.
    const { optionTexts, examContentTexts } = await page.evaluate(() => ({
      optionTexts: [...document.querySelectorAll<HTMLOptionElement>('select[aria-hidden="true"] option')].map(
        (option) => option.text.replace(/\s+/g, " ").trim(),
      ),
      examContentTexts: [...document.querySelectorAll("[data-exam-content]")].map((element) =>
        (element.textContent ?? "").replace(/\s+/g, " ").trim(),
      ),
    }));
    const examContent = new Set([...examContentTexts, ...(await knownPartNameForms())]);
    const allowLanguage = [
      ...optionTexts.filter((text) => examContent.has(text)),
      ...(options.allowDocumentTitle ? [await page.title()] : []),
    ].map((text) => new RegExp(`^${escapeRegExp(text)}$`));
    await expectMatrixClean(page, screen, cell, {
      elementOverflow: { allowAccessibleScroller: true },
      allowLanguage,
    });
    const unexpected = errors
      .filter((error) => !isExpectedResourceError(error, path, options.allowDocument404 ?? false))
      .map((error) => error.text);
    expect(unexpected, `${screen} ${cellLabel(cell)}: không có lỗi console`).toEqual([]);
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

// Chờ DS có thể lâu (tối đa 240 giây) nên ô có sản phẩm có hạn riêng; ô giới thiệu và 404 không chờ gì.
const DS_CELL_TIMEOUT_MS = 360_000;

for (const cell of matrixCells()) {
  test(`trang giới thiệu: ${cellLabel(cell)}`, async () => {
    test.setTimeout(120_000);
    await check("storefront-landing", "/", cell, async (page) => {
      await expect(page.getByRole("heading", { level: 2, name: HEADING.catalog[cell.lang] })).toBeVisible();
    });
  });

  test(`chi tiết bài thi: ${cellLabel(cell)}`, async () => {
    test.setTimeout(DS_CELL_TIMEOUT_MS);
    const productCode = await firstProductCode();
    await check("storefront-detail", `/exams/${productCode}`, cell, async (page) => {
      await expect(page.getByRole("heading", { level: 1 })).toBeVisible();
      await expect(page.getByRole("complementary").getByRole("link", { name: HEADING.buy[cell.lang] })).toBeVisible();
    }, { allowDocumentTitle: true });
  });

  test(`bài thi đã ngừng bán: ${cellLabel(cell)}`, async () => {
    test.setTimeout(DS_CELL_TIMEOUT_MS);
    await waitForDsData();
    await check("storefront-gone", `/exams/${discontinuedExam().productCode}`, cell, async (page) => {
      await expect(page.getByRole("heading", { level: 1, name: HEADING.gone[cell.lang] })).toBeVisible();
    }, { allowDocumentTitle: true });
  });

  test(`bài thi không có (404): ${cellLabel(cell)}`, async () => {
    test.setTimeout(120_000);
    await check("storefront-404", "/exams/KHONG-CO-MA-NAY", cell, async (page) => {
      await expect(page.getByRole("heading", { name: HEADING.notFound[cell.lang] })).toBeVisible();
    }, { allowDocument404: true });
  });
}
