import { expect, test, type Browser, type BrowserContext, type Page } from "@playwright/test";
import { loginAdmin } from "./helpers/editor-qa";
import { createVerifiedAccount, WEB_URL, type SessionCookies } from "./helpers/mail";
import { allExams, publishedExamSet, waitForPublishedExamSet } from "./helpers/published-exam-set";
import {
  applyMatrixCookies,
  cellLabel,
  expectMatrixClean,
  launchMatrixBrowser,
  matrixCells,
  openMatrixContext,
  type MatrixCell,
} from "./helpers/visual-matrix";

/**
 * AC-23: 10 man hinh FS-3 tren ma tran 8 o (2 kho x 2 che do x 2 ngon ngu).
 * Dung du lieu that cho checkout, result (thanh cong, that bai, dang kiem tra, khach),
 * bai thi cua toi (trong + da mua), don cua toi va chi tiet don;
 * chi mock bang page.route cho my-exams-error va my-exams-checking.
 */
test.setTimeout(420_000);

let browser: Browser;
let emptyCookies: SessionCookies;
let richCookies: SessionCookies;
let examCode = "";
let fulfilledOrderCode = "";
let failedOrderCode = "";
let checkingOrderCode = "";
let guestResultUrl = "";

function pickDistinctSaleExamCodes(): [string, string, string] {
  const discontinued = publishedExamSet().discontinuedExam;
  const saleExams = allExams().filter((exam) => exam.code !== discontinued);
  expect(saleExams.length).toBeGreaterThanOrEqual(3);
  return [saleExams[0]!.productCode, saleExams[1]!.productCode, saleExams[2]!.productCode];
}

test.beforeAll(async () => {
  browser = await launchMatrixBrowser();

  const adminCtx = await browser.newContext();
  try {
    await loginAdmin(adminCtx, "vi");
    await waitForPublishedExamSet(adminCtx.request, { timeoutMs: 240_000 });
  } finally {
    await adminCtx.close();
  }

  const emptyUser = await createVerifiedAccount("vis.empty");
  emptyCookies = emptyUser.cookies;

  const richUser = await createVerifiedAccount("vis.rich");
  richCookies = richUser.cookies;

  const setupCtx = await browser.newContext();
  try {
    const [fulfilledExamCode, failedExamCode, checkingExamCode] = pickDistinctSaleExamCodes();
    examCode = fulfilledExamCode;
    await setupCtx.addCookies(richCookies);

    const setupPage = await setupCtx.newPage();

    // 1. Don thanh cong (FULFILLED -> cho /home populated, result success, guest result & order detail)
    await setupPage.goto(`${WEB_URL}/checkout/${encodeURIComponent(fulfilledExamCode)}`);
    await setupPage.getByRole("button", { name: "Thanh toán qua VNPAY" }).click();
    await setupPage.waitForURL(/\/mock-vnpay/);
    await setupPage.locator('a[data-mock-action="success"]').click();
    await setupPage.waitForURL(/\/checkout\/result/);
    await expect(setupPage.getByRole("heading", { level: 1, name: "Đã cấp bài thi" })).toBeVisible();
    guestResultUrl = setupPage.url();
    const successDetailHref = await setupPage.getByRole("link", { name: "Xem chi tiết đơn" }).getAttribute("href");
    fulfilledOrderCode = (successDetailHref ?? "").split("/").pop() ?? "";

    // 2. Don that bai (FAILED) — dung de thi rieng de tranh dung don dang cho / trung san pham
    await setupPage.goto(`${WEB_URL}/checkout/${encodeURIComponent(failedExamCode)}`);
    await setupPage.getByRole("button", { name: "Thanh toán qua VNPAY" }).click();
    await setupPage.waitForURL(/\/mock-vnpay/);
    await setupPage.locator('a[data-mock-action="failed"]').click();
    await setupPage.waitForURL(/\/checkout\/result/);
    await expect(setupPage.getByRole("heading", { level: 1, name: "Thanh toán chưa thành công" })).toBeVisible();
    const failedDetailHref = await setupPage.getByRole("link", { name: "Xem chi tiết đơn" }).getAttribute("href");
    failedOrderCode = (failedDetailHref ?? "").split("/").pop() ?? "";

    // 3. Don cho / dang kiem tra (CHECKING) — dung de thi thu 3 rieng biet
    await setupPage.goto(`${WEB_URL}/checkout/${encodeURIComponent(checkingExamCode)}`);
    await setupPage.getByRole("button", { name: "Thanh toán qua VNPAY" }).click();
    await setupPage.waitForURL(/\/mock-vnpay/);
    await setupPage.locator('a[data-mock-action="no_response"]').click();
    await setupPage.waitForURL(/\/checkout\/result/);
    await expect(setupPage.getByRole("heading", { level: 1, name: "Đang chờ xác nhận thanh toán" })).toBeVisible();
    const chkLink = await setupPage.getByRole("link", { name: "Xem chi tiết đơn" }).getAttribute("href");
    checkingOrderCode = (chkLink ?? "").split("/").pop() ?? "";

    expect(fulfilledOrderCode).toMatch(/^DH/i);
    expect(failedOrderCode).toMatch(/^DH/i);
    expect(checkingOrderCode).toMatch(/^DH/i);
    expect(fulfilledOrderCode).not.toBe(failedOrderCode);
    expect(failedOrderCode).not.toBe(checkingOrderCode);
    expect(fulfilledOrderCode).not.toBe(checkingOrderCode);
  } finally {
    await setupCtx.close();
  }
});

test.afterAll(async () => {
  await browser?.close();
});

async function openCell(
  cell: MatrixCell,
  cookies: SessionCookies | null,
): Promise<{ context: BrowserContext; page: Page }> {
  const context = await openMatrixContext(browser, cell, WEB_URL);
  if (cookies) {
    await context.addCookies(cookies);
  }
  await applyMatrixCookies(context, cell, WEB_URL);
  return { context, page: await context.newPage() };
}

for (const cell of matrixCells()) {
  test.describe(cellLabel(cell), () => {
    test("luong checkout, ket qua, bai thi cua toi va don cua toi", async () => {
      // Guest result screen
      {
        const { context, page } = await openCell(cell, null);
        try {
          await page.goto(guestResultUrl);
          await expect(
            page.getByRole("heading", {
              level: 1,
              name: cell.lang === "vi" ? "Thanh toán thành công" : "Payment successful",
            }),
          ).toBeVisible();
          await expectMatrixClean(page, "checkout-result-guest", cell);
        } finally {
          await context.close();
        }
      }

      // Empty + synthetic error/checking on /home
      {
        const { context, page } = await openCell(cell, emptyCookies);
        try {
          await page.goto(`${WEB_URL}/home`);
          await expect(
            page.getByRole("heading", {
              name: cell.lang === "vi" ? "Bạn chưa có bài thi nào" : "You have no exams yet",
            }),
          ).toBeVisible();
          await expectMatrixClean(page, "my-exams-empty", cell);

          await page.route("**/api/v1/me/exams", (route) =>
            route.fulfill({
              status: 500,
              contentType: "application/json",
              body: JSON.stringify({ code: "INTERNAL_ERROR" }),
            }),
          );
          await page.reload();
          await expect(
            page.getByRole("heading", {
              name: cell.lang === "vi" ? "Không tải được danh sách bài thi" : "Unable to load exam list",
            }),
          ).toBeVisible();
          await expectMatrixClean(page, "my-exams-error", cell);

          await page.unroute("**/api/v1/me/exams");
          await page.route("**/api/v1/me/exams", (route) =>
            route.fulfill({
              status: 200,
              contentType: "application/json",
              body: JSON.stringify({
                items: [
                  {
                    code: examCode,
                    name: "Đề thi thử HSA tổng hợp",
                    examKind: {
                      id: "kind-hsa",
                      code: "HSA",
                      nameVi: "Đánh giá năng lực HSA",
                      nameEn: "HSA Aptitude Exam",
                    },
                    format: "FULL",
                    buildMode: "STANDARD",
                    saleState: "ON_SALE",
                    state: "CHECKING",
                    attemptsLeft: 0,
                    attemptsTotal: 0,
                    hasPendingFulfillment: true,
                    pendingOrderCode: "DH261121VISCHK",
                    actions: ["CHECKING"],
                  },
                ],
              }),
            }),
          );
          await page.reload();
          await expect(page.getByText(/DH261121VISCHK/)).toBeVisible();
          await expectMatrixClean(page, "my-exams-checking", cell);
        } finally {
          await context.close();
        }
      }

      // Rich user: checkout, results (success/checking/failed), populated home, orders list & detail
      {
        const { context, page } = await openCell(cell, richCookies);
        try {
          await page.goto(`${WEB_URL}/checkout/${encodeURIComponent(examCode)}`);
          await expect(
            page.getByRole("heading", {
              level: 1,
              name: cell.lang === "vi" ? "Xác nhận mua" : "Confirm purchase",
            }),
          ).toBeVisible();
          await expect(page.locator("#checkout-buy-button")).toBeVisible();
          await expect(page.locator("#checkout-buy-button")).toBeEnabled();
          await expectMatrixClean(page, "checkout-confirm", cell);

          await page.goto(`${WEB_URL}/checkout/result?order=${encodeURIComponent(fulfilledOrderCode)}`);
          await expect(
            page.getByRole("heading", {
              level: 1,
              name: cell.lang === "vi" ? "Đã cấp bài thi" : "Exam granted",
            }),
          ).toBeVisible();
          await expectMatrixClean(page, "checkout-result-success", cell);

          await page.goto(`${WEB_URL}/checkout/result?order=${encodeURIComponent(checkingOrderCode)}&state=checking`);
          await expect(
            page.getByRole("heading", {
              level: 1,
              name: cell.lang === "vi" ? "Đang chờ xác nhận thanh toán" : "Awaiting payment confirmation",
            }),
          ).toBeVisible();
          await expectMatrixClean(page, "checkout-result-checking", cell);

          await page.goto(`${WEB_URL}/checkout/result?order=${encodeURIComponent(failedOrderCode)}`);
          await expect(
            page.getByRole("heading", {
              level: 1,
              name: cell.lang === "vi" ? "Thanh toán chưa thành công" : "Payment unsuccessful",
            }),
          ).toBeVisible();
          await expectMatrixClean(page, "checkout-result-failed", cell);

          await page.goto(`${WEB_URL}/home`);
          await expect(page.locator("[data-exam-content]").first()).toBeVisible();
          await expectMatrixClean(page, "my-exams-populated", cell);

          await page.goto(`${WEB_URL}/account/orders`);
          await expect(
            page.getByRole("heading", {
              level: 1,
              name: cell.lang === "vi" ? "Đơn của tôi" : "My orders",
            }),
          ).toBeVisible();
          await expect(page.getByText(fulfilledOrderCode)).toBeVisible();
          await expectMatrixClean(page, "orders-list", cell, {
            elementOverflow: { scope: "main", allowAccessibleScroller: true },
          });

          await page.goto(`${WEB_URL}/account/orders/${encodeURIComponent(fulfilledOrderCode)}`);
          await expect(page.getByRole("heading", { level: 1 })).toContainText(fulfilledOrderCode);
          await expectMatrixClean(page, "order-detail", cell, {
            elementOverflow: { scope: "main", allowAccessibleScroller: true },
          });
        } finally {
          await context.close();
        }
      }
    });
  });
}
