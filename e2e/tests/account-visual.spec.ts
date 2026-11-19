import { expect, test, type Browser, type BrowserContext, type Page } from "@playwright/test";
import {
  applyMatrixCookies,
  cellLabel,
  expectMatrixClean,
  launchMatrixBrowser,
  matrixCells,
  openMatrixContext,
  type MatrixCell,
} from "./helpers/visual-matrix";
import { registerViaApi, newAccount, WEB_URL, type SessionCookies } from "./helpers/account";

/**
 * AC-21: năm màn tài khoản (đăng ký, xác minh email, quên mật khẩu, đặt lại mật khẩu, hồ sơ) cùng liên kết ở trang đăng
 * nhập, ở đủ trạng thái đang tải, lỗi, có dữ liệu (hồ sơ còn trạng thái rỗng: chưa có ảnh, chưa có số điện thoại), trên
 * 8 ô khổ x chế độ x ngôn ngữ. Mỗi ô chụp `page.screenshot` vào `THI360_VISUAL_DIR` rồi kiểm không cuộn ngang, tương
 * phản và lẫn ngôn ngữ (repo chưa có ảnh chuẩn `toHaveScreenshot`).
 */
test.setTimeout(240_000);

let browser: Browser;
let sessionCookies: SessionCookies;

test.beforeAll(async () => {
  browser = await launchMatrixBrowser();
  // Họ tên ASCII để kiểm lẫn ngôn ngữ ở bản en không bắt nhầm chữ có dấu của người dùng.
  sessionCookies = await registerViaApi(newAccount("visual"));
});

test.afterAll(async () => {
  await browser.close();
});

async function openCell(cell: MatrixCell, signedIn: boolean): Promise<{ context: BrowserContext; page: Page }> {
  const context = await openMatrixContext(browser, cell, WEB_URL);
  if (signedIn) {
    await context.addCookies(sessionCookies);
    await applyMatrixCookies(context, cell, WEB_URL);
  }
  return { context, page: await context.newPage() };
}

/** Giữ lại phản hồi của API `pattern` tới khi gọi `release()`: chụp được khung chờ mà không phụ thuộc tốc độ máy. */
async function holdApi(page: Page, pattern: string): Promise<() => void> {
  const { promise: gate, resolve: release } = Promise.withResolvers<void>();
  await page.route(pattern, async (route) => {
    await gate;
    await route.continue();
  });
  return release;
}

for (const cell of matrixCells()) {
  test.describe(cellLabel(cell), () => {
    test("màn công khai: đăng nhập, đăng ký, quên mật khẩu, đặt lại mật khẩu", async () => {
      const { context, page } = await openCell(cell, false);
      try {
        await page.goto(`${WEB_URL}/login`);
        await expect(page.getByRole("heading", { level: 1 })).toBeVisible();
        await expectMatrixClean(page, "login-links", cell);

        await page.goto(`${WEB_URL}/register`);
        await expect(page.getByRole("heading", { level: 1 })).toBeVisible();
        await expectMatrixClean(page, "register", cell);

        // Lỗi gắn ô: gửi biểu mẫu trống.
        await page.locator("form button[type=submit]").click();
        await expect(page.locator("[role=alert]").first()).toBeVisible();
        await expectMatrixClean(page, "register-errors", cell);

        // Hộp thoại điều khoản.
        await page.goto(`${WEB_URL}/register`);
        await page.getByRole("button", { name: cell.lang === "vi" ? "Xem Điều khoản sử dụng" : "View the Terms of Use" }).click();
        await expect(page.getByRole("dialog")).toBeVisible();
        await expectMatrixClean(page, "register-terms", cell);

        await page.goto(`${WEB_URL}/forgot-password`);
        await expect(page.getByRole("heading", { level: 1 })).toBeVisible();
        await expectMatrixClean(page, "forgot-password", cell);

        await page.getByLabel(/^Email/).fill("an@e2e.thi360.vn");
        await page.locator("form button[type=submit]").click();
        await expect(page.locator("#forgot-sent-title")).toBeVisible();
        await expectMatrixClean(page, "forgot-password-sent", cell);

        await page.goto(`${WEB_URL}/reset-password?token=khong-co-that`);
        await expect(page.getByRole("heading", { level: 1 })).toBeVisible();
        await expectMatrixClean(page, "reset-password", cell);

        await page.goto(`${WEB_URL}/reset-password`);
        await expect(page.getByRole("heading", { level: 1 })).toBeVisible();
        await expectMatrixClean(page, "reset-password-invalid", cell);
      } finally {
        await context.close();
      }
    });

    test("màn cần phiên: xác minh email và hồ sơ (đang tải, lỗi, có dữ liệu, rỗng)", async () => {
      const { context, page } = await openCell(cell, true);
      try {
        // Xác minh email: khung chờ, rồi biểu mẫu.
        const releaseVerify = await holdApi(page, "**/api/v1/me/profile");
        await page.goto(`${WEB_URL}/verify-email`, { waitUntil: "commit" });
        await expect(page.locator("[role=status][aria-label]")).toBeVisible();
        await expectMatrixClean(page, "verify-email-loading", cell);
        releaseVerify();
        await expect(page.getByRole("heading", { level: 1 })).toBeVisible();
        await expect(page.locator("input[autocomplete=one-time-code]")).toBeVisible();
        await expectMatrixClean(page, "verify-email", cell);

        // Hồ sơ: khung chờ.
        await page.unroute("**/api/v1/me/profile");
        const releaseProfile = await holdApi(page, "**/api/v1/me/profile");
        await page.goto(`${WEB_URL}/account/profile`, { waitUntil: "commit" });
        await expect(page.locator("[role=status][aria-label]")).toBeVisible();
        await expectMatrixClean(page, "profile-loading", cell);
        releaseProfile();

        // Hồ sơ: dữ liệu ở trạng thái rỗng (chưa có ảnh, chưa số điện thoại, email chưa xác minh).
        await expect(page.getByRole("heading", { level: 1 })).toBeVisible();
        await expect(page.locator("#profile-email-title")).toBeVisible();
        await expectMatrixClean(page, "profile-empty", cell);

        // Hồ sơ: lỗi tải.
        await page.unroute("**/api/v1/me/profile");
        await page.route("**/api/v1/me/profile", (route) =>
          route.fulfill({ status: 500, contentType: "application/json", body: JSON.stringify({ code: "INTERNAL_ERROR" }) }),
        );
        await page.goto(`${WEB_URL}/account/profile`);
        await expect(page.locator("main").getByRole("button")).toBeVisible();
        await expectMatrixClean(page, "profile-error", cell);
      } finally {
        await context.close();
      }
    });
  });
}
