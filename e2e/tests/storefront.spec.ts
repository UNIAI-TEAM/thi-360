import { expect, request as playwrightRequest, type Page, test } from "@playwright/test";
import { loginAdmin } from "./helpers/editor-qa";
import {
  allExams,
  discontinuedExam,
  publishedExamSet,
  reviewExam,
  waitForPublishedExamSet,
} from "./helpers/published-exam-set";

/**
 * Trang giới thiệu, danh mục và chi tiết bài thi của web học viên (UNI-2730, FS-1, AC-1..AC-4, AC-6, AC-8, AC-9,
 * AC-11, AC-17, AC-18). Dữ liệu là bộ bài đã công bố của DS (`helpers/published-exam-set.ts`): `beforeAll` chờ bộ đó
 * xuất hiện đủ ở danh sách quản trị, nên bài ngừng bán và bài ôn luôn có và không ca nào bị bỏ qua. Backend chạy
 * profile `e2e` kèm `THI360_SECURITY_RATELIMIT_EXEMPTIPS` (xem `playwright.config.ts`): bộ này không có phản hồi 429.
 */
const API_URL = (process.env.THI360_API_URL ?? "http://localhost:8080").replace(/\/+$/, "");
const WEB_URL = (process.env.THI360_WEB_URL ?? "http://localhost:3000").replace(/\/+$/, "");
const DEV_PASSWORD = process.env.THI360_DEV_PASSWORD ?? "Thi360@dev";
const CATALOG_PAGE_SIZE = 12;
/** Bài đang bán trong bộ DS: mọi bài đã công bố trừ bài ngừng bán (mỗi bài đã công bố được chiếu thành một sản phẩm). */
const ON_SALE_COUNT = allExams().filter((exam) => exam.code !== publishedExamSet().discontinuedExam).length;

test.beforeAll(async ({ browser }) => {
  test.setTimeout(300_000);
  const context = await browser.newContext();
  try {
    await loginAdmin(context, "vi");
    await waitForPublishedExamSet(context.request, { timeoutMs: 240_000 });
  } finally {
    await context.close();
  }
});

interface Card {
  code: string;
  name: string;
  versionNo: number;
  price: number;
  listPrice: number;
  buildMode: string;
  format: string;
  promotion?: { name: string; endsAt: string } | null;
}
interface CatalogPage {
  items: Card[];
  totalItems: number;
  totalPages: number;
}

async function readCatalog(query = "size=48"): Promise<CatalogPage> {
  const api = await playwrightRequest.newContext();
  try {
    const response = await api.get(`${API_URL}/api/v1/public/storefront/products?${query}`);
    expect(response.status(), "GET /api/v1/public/storefront/products: cần dữ liệu DS và bảng chiếu đã dựng").toBe(200);
    return (await response.json()) as CatalogPage;
  } finally {
    await api.dispose();
  }
}

async function signIn(page: Page, username: string): Promise<void> {
  await page.getByLabel(/Tên đăng nhập hoặc email/).fill(username);
  await page.getByLabel(/Mật khẩu/).fill(DEV_PASSWORD);
  await page.getByRole("button", { name: "Đăng nhập" }).click();
}

function cardFor(page: Page, name: string) {
  return page.getByRole("article").filter({ has: page.getByRole("heading", { name }) });
}

test.describe("Danh mục ở trang giới thiệu", () => {
  test("AC-1: khách xem danh mục thật, thẻ đủ thông tin; không bị đẩy về /login", async ({ page }) => {
    const catalog = await readCatalog();
    expect(catalog.items.length, "DS phải công bố ít nhất một bài đang bán").toBeGreaterThan(0);
    const requests: string[] = [];
    page.on("request", (request) => requests.push(new URL(request.url()).pathname));

    await page.goto(`${WEB_URL}/`);
    await expect(page.getByRole("heading", { level: 2, name: "Chọn bài thi" })).toBeVisible();

    const first = catalog.items[0]!;
    const card = cardFor(page, first.name);
    await expect(card).toBeVisible();
    await expect(card.getByText(/\d+ câu · \d+ phút · \d+ lượt/)).toBeVisible();
    await expect(card.getByText(/\d[\d.]* đ/).first()).toBeVisible();
    await expect(card.getByRole("link", { name: "Xem chi tiết" })).toHaveAttribute("href", `/exams/${first.code}`);

    const promo = catalog.items.find((item) => item.promotion && item.listPrice > item.price);
    if (promo) {
      const promoCard = cardFor(page, promo.name);
      await expect(promoCard.locator("s")).toBeVisible();
      await expect(promoCard.getByText(/^Giảm \d+%$/)).toBeVisible();
      await expect(promoCard.getByText(/^Kết thúc \d{2}\/\d{2}$/)).toBeVisible();
    }

    // AC-11: khách không bị đưa về /login dù /api/v1/me trả 401, và không gọi exam-access.
    await expect(page.getByRole("link", { name: "Đăng nhập" })).toBeVisible();
    await expect(page).toHaveURL(`${WEB_URL}/`);
    expect(requests.filter((path) => path.includes("exam-access"))).toEqual([]);
  });

  test("AC-2: tìm, lọc, sắp xếp nằm trên URL, còn sau khi tải lại; Xóa bộ lọc về mặc định", async ({ page }) => {
    const catalog = await readCatalog();
    const target = catalog.items[0]!;

    await page.goto(`${WEB_URL}/`);
    await page.getByRole("searchbox", { name: "Tìm theo tên hoặc mã" }).fill(target.code);
    await expect(page).toHaveURL(new RegExp(`[?&]q=${encodeURIComponent(target.code)}`));
    await expect(cardFor(page, target.name)).toBeVisible();

    await page.getByRole("combobox", { name: "Sắp xếp" }).click();
    await page.getByRole("option", { name: "Giá tăng dần" }).click();
    await expect(page).toHaveURL(/sort=PRICE_ASC/);

    await page.reload();
    await expect(page).toHaveURL(/q=/);
    await expect(page).toHaveURL(/sort=PRICE_ASC/);
    await expect(page.getByRole("searchbox", { name: "Tìm theo tên hoặc mã" })).toHaveValue(target.code);
    await expect(page.getByRole("combobox", { name: "Sắp xếp" })).toContainText("Giá tăng dần");

    await page.getByRole("button", { name: "Xóa bộ lọc" }).first().click();
    await expect(page).toHaveURL(`${WEB_URL}/`);
    await expect(page.getByRole("searchbox", { name: "Tìm theo tên hoặc mã" })).toHaveValue("");
  });

  test("AC-2: lọc theo kỳ thi và hình thức; không có kết quả thì có thông báo và Xóa bộ lọc", async ({ page }) => {
    const catalog = await readCatalog();
    const target = catalog.items[0]!;

    await page.goto(`${WEB_URL}/`);
    await page.getByRole("combobox", { name: "Hình thức" }).click();
    await page.getByRole("option", { name: target.format === "FULL" ? "Bài thi FULL" : /Bài thi theo/ }).first().click();
    await expect(page).toHaveURL(/format=/);

    await page.getByRole("searchbox", { name: "Tìm theo tên hoặc mã" }).fill("khong-co-bai-nao-ten-nhu-the-zzz");
    await expect(page.getByRole("heading", { name: "Không tìm thấy bài thi phù hợp" })).toBeVisible();
    await page.getByRole("button", { name: "Xóa bộ lọc" }).last().click();
    await expect(page).toHaveURL(`${WEB_URL}/`);
    await expect(page.getByRole("heading", { name: "Không tìm thấy bài thi phù hợp" })).toBeHidden();
  });

  test("AC-3: chuyển trang giữ điều kiện; bấm thẻ mở đúng /exams/<code>", async ({ page }) => {
    // Bộ DS có hơn một trang bài đang bán (đếm từ tệp json): trang 2 phải có, không bỏ qua.
    expect(ON_SALE_COUNT, "bộ DS phải có hơn một trang bài đang bán").toBeGreaterThan(CATALOG_PAGE_SIZE);
    const catalog = await readCatalog(`size=${CATALOG_PAGE_SIZE}`);
    expect(catalog.totalPages, "danh mục công khai phải có trang 2").toBeGreaterThanOrEqual(2);

    await page.goto(`${WEB_URL}/?sort=PRICE_ASC`);
    await page.getByRole("button", { name: "Trang 2" }).click();
    await expect(page).toHaveURL(/sort=PRICE_ASC/);
    await expect(page).toHaveURL(/page=2/);
    await expect(page.getByRole("button", { name: "Trang 2" })).toHaveAttribute("aria-current", "page");

    const second = await readCatalog("sort=PRICE_ASC&page=1&size=12");
    const open = second.items[0]!;
    await cardFor(page, open.name).getByRole("heading").getByRole("link").click();
    await expect(page).toHaveURL(`${WEB_URL}/exams/${open.code}`);
    await expect(page.getByRole("heading", { level: 1, name: open.name })).toBeVisible();
  });

  test("AC-18: bài ôn đang bán có nhãn Bài ôn trên thẻ và trang chi tiết", async ({ page }) => {
    const review = reviewExam();

    await page.goto(`${WEB_URL}/?q=${encodeURIComponent(review.productCode)}`);
    await expect(cardFor(page, review.name).getByText("Bài ôn", { exact: true })).toBeVisible();

    await page.goto(`${WEB_URL}/exams/${review.productCode}`);
    await expect(page.getByText("Bài ôn", { exact: true }).first()).toBeVisible();
  });
});

test.describe("Chi tiết bài thi và nút theo quyền", () => {
  test("AC-17: HTML máy chủ đã có tên sản phẩm và tiêu đề trang là tên sản phẩm", async ({ request, page }) => {
    const product = (await readCatalog()).items[0]!;

    const html = await (await request.get(`${WEB_URL}/exams/${product.code}`)).text();
    expect(html).toContain(product.name);
    expect(html).toMatch(new RegExp(`<title>${product.name.replace(/[.*+?^${}()|[\]\\]/g, "\\$&")}`));

    await page.goto(`${WEB_URL}/exams/${product.code}`);
    await expect(page).toHaveTitle(new RegExp(`^${product.name.replace(/[.*+?^${}()|[\]\\]/g, "\\$&")}`));
  });

  test("AC-4, AC-6: khách bấm Mua bài thi sang /login kèm callbackUrl; đăng nhập xong về đúng đường dẫn, còn v và p", async ({
    page,
  }) => {
    const product = (await readCatalog()).items.find((item) => item.format !== "BUNDLE")!;
    expect(product, "cần ít nhất một bài thi lẻ đang bán").toBeDefined();
    const checkout = `/checkout/${product.code}?v=${product.versionNo}&p=${product.price}`;

    await page.goto(`${WEB_URL}/exams/${product.code}`);
    await expect(page.getByRole("link", { name: "Đăng nhập" })).toBeVisible();
    await page.getByRole("complementary").getByRole("link", { name: "Mua bài thi" }).click();

    await expect(page).toHaveURL(/\/login\?/);
    expect(new URL(page.url()).searchParams.get("callbackUrl")).toBe(checkout);

    await signIn(page, "student01");
    await page.waitForURL((url) => url.pathname.startsWith("/checkout/"));
    const landed = new URL(page.url());
    expect(landed.pathname).toBe(`/checkout/${product.code}`);
    expect(landed.searchParams.get("v")).toBe(String(product.versionNo));
    expect(landed.searchParams.get("p")).toBe(String(product.price));
  });

  test("AC-6: học viên chưa có quyền thấy Mua bài thi tới /checkout/<code>?v=&p=", async ({ page }) => {
    const product = (await readCatalog()).items.find((item) => item.format !== "BUNDLE")!;
    expect(product, "cần ít nhất một bài thi lẻ đang bán").toBeDefined();
    await page.goto(`${WEB_URL}/login`);
    await signIn(page, "student01");
    await page.waitForURL(`${WEB_URL}/home`);

    await page.goto(`${WEB_URL}/exams/${product.code}`);
    const buy = page.getByRole("complementary").getByRole("link", { name: "Mua bài thi" });
    await expect(buy).toBeVisible();
    await expect(buy).toHaveAttribute("href", `/checkout/${product.code}?v=${product.versionNo}&p=${product.price}`);
    await expect(page.getByRole("navigation", { name: "Điều hướng chính" }).getByRole("link", { name: "Bài thi của tôi" })).toBeVisible();
  });

  test("AC-6: quản trị viên mở trang công khai: không có nút chính, không bị đẩy sang /forbidden, không gọi exam-access", async ({
    page,
  }) => {
    const product = (await readCatalog()).items.find((item) => item.format !== "BUNDLE")!;
    expect(product, "cần ít nhất một bài thi lẻ đang bán").toBeDefined();
    await page.goto(`${WEB_URL}/login`);
    await signIn(page, "ops.admin");
    await page.waitForURL((url) => !url.pathname.startsWith("/login"));

    const requests: string[] = [];
    page.on("request", (request) => requests.push(new URL(request.url()).pathname));
    await page.goto(`${WEB_URL}/exams/${product.code}`);

    await expect(page.getByRole("heading", { level: 1, name: product.name })).toBeVisible();
    await expect(page.getByText(/không có nút mua hay vào bài thi/)).toBeVisible();
    await expect(page.getByRole("complementary").getByRole("link", { name: /Mua bài thi|Vào bài thi/ })).toHaveCount(0);
    await expect(page).toHaveURL(`${WEB_URL}/exams/${product.code}`);
    expect(requests.filter((path) => path.includes("exam-access"))).toEqual([]);
  });

  test("AC-9: mã không có trả HTTP 404 và trang 'Không tìm thấy bài thi' kèm nút về danh mục", async ({ page }) => {
    const response = await page.goto(`${WEB_URL}/exams/KHONG-CO-MA-NAY`);

    expect(response?.status()).toBe(404);
    await expect(page.getByRole("heading", { name: "Không tìm thấy bài thi" })).toBeVisible();
    await page.getByRole("link", { name: "Về danh mục bài thi" }).click();
    await expect(page).toHaveURL(`${WEB_URL}/#exams`);
  });

  test("AC-8: mã ngừng bán hiện 'Bài thi đã ngừng bán'; nút Bài thi của tôi chỉ có khi đã đăng nhập", async ({ page }) => {
    const discontinued = discontinuedExam().productCode;

    await page.goto(`${WEB_URL}/exams/${discontinued}`);
    await expect(page.getByRole("heading", { level: 1, name: "Bài thi đã ngừng bán" })).toBeVisible();
    await expect(page.getByRole("link", { name: "Về danh mục bài thi" })).toBeVisible();
    await expect(page.getByRole("link", { name: "Bài thi của tôi" })).toHaveCount(0);

    await page.goto(`${WEB_URL}/login`);
    await signIn(page, "student01");
    await page.waitForURL(`${WEB_URL}/home`);
    await page.goto(`${WEB_URL}/exams/${discontinued}`);
    await expect(page.getByRole("main").getByRole("link", { name: "Bài thi của tôi" })).toBeVisible();
  });
});
