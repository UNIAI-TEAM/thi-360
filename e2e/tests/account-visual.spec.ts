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
import { newAccount, registerViaApi, solidPng, WEB_URL, type SessionCookies } from "./helpers/mail";

/**
 * AC-21: năm màn tài khoản (đăng ký, xác minh email, quên mật khẩu, đặt lại mật khẩu, hồ sơ) cùng liên kết ở trang đăng
 * nhập, ở đủ trạng thái đang tải, đang gửi, lỗi và có dữ liệu (hồ sơ còn trạng thái rỗng, có ảnh và có email đang chờ),
 * trên 8 ô khổ x chế độ x ngôn ngữ. Mỗi ô chụp `page.screenshot` vào `THI360_VISUAL_DIR` rồi kiểm không cuộn ngang,
 * tương phản và lẫn ngôn ngữ (repo chưa có ảnh chuẩn `toHaveScreenshot`). Trạng thái lỗi và dữ liệu có ảnh, có email
 * chờ dựng bằng `page.route` để không phụ thuộc máy chủ.
 */
test.setTimeout(300_000);

const AVATAR_ID = "0192f3a1-0000-7000-8000-000000000001";

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

/**
 * Giữ lại yêu cầu tới `pattern` tới khi gọi `release()`: chụp được khung chờ hay nút đang gửi mà không phụ thuộc tốc
 * độ máy. `abort` thay cho `continue` khi yêu cầu có tác dụng thật (đăng ký tài khoản).
 *
 * `release()` mở cổng, chờ mọi yêu cầu đang giữ được xử lý đúng một lần rồi mới gỡ route: gỡ route hay điều hướng khi
 * yêu cầu còn treo làm `route.abort` báo "Route is already handled!".
 */
async function holdApi(
  page: Page,
  pattern: string,
  finish: "continue" | "abort" = "continue",
): Promise<() => Promise<void>> {
  const { promise: gate, resolve: open } = Promise.withResolvers<void>();
  const handled: Promise<void>[] = [];
  await page.route(pattern, (route) => {
    const once = gate.then(() => (finish === "abort" ? route.abort() : route.continue()));
    handled.push(once);
    return once;
  });
  return async () => {
    open();
    await Promise.all(handled);
    await page.unroute(pattern);
  };
}

async function failApi(page: Page, pattern: string, status: number, body: object): Promise<void> {
  await page.route(pattern, (route) => route.fulfill({ status, contentType: "application/json", body: JSON.stringify(body) }));
}

const SERVER_ERROR = { code: "INTERNAL_ERROR" };

async function expectSubmitting(page: Page): Promise<void> {
  await expect(page.locator("form button[type=submit][aria-busy=true]")).toBeVisible();
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
        await expect(page.locator("main [role=alert]").first()).toBeVisible();
        await expectMatrixClean(page, "register-errors", cell);

        // Đang gửi: nút khóa và quay; yêu cầu bị giữ rồi hủy để không tạo tài khoản.
        await page.goto(`${WEB_URL}/register`);
        const releaseRegister = await holdApi(page, "**/api/v1/auth/register", "abort");
        await page.locator('input[name="username"]').fill("visual.submitting");
        await page.locator('input[name="fullName"]').fill("E2E Student");
        await page.locator('input[name="email"]').fill("visual.submitting@e2e.thi360.vn");
        await page.locator('input[name="password"]').fill("Thi360@e2e1");
        await page.getByRole("checkbox").check();
        await page.locator("form button[type=submit]").click();
        await expectSubmitting(page);
        await expectMatrixClean(page, "register-submitting", cell);
        await releaseRegister();

        // Hộp thoại điều khoản.
        await page.goto(`${WEB_URL}/register`);
        await page.getByRole("button", { name: cell.lang === "vi" ? "Xem Điều khoản sử dụng" : "View the Terms of Use" }).click();
        const dialog = page.getByRole("dialog");
        await expect(dialog).toBeVisible();
        // Chờ hiệu ứng mở xong: giữa chừng chữ còn mờ (độ đục < 1) nên kiểm tương phản đọc ra 3,3 đến 3,8 dù màu
        // `text-muted` đạt 4,5:1.
        await dialog.evaluate((element) =>
          Promise.all(element.getAnimations({ subtree: true }).map((animation) => animation.finished)),
        );
        await expectMatrixClean(page, "register-terms", cell);

        // Bước 2 của đăng ký: xác minh mã OTP 6 chữ số.
        await page.route("**/api/v1/auth/register", (route) =>
          route.fulfill({
            status: 200,
            contentType: "application/json",
            body: JSON.stringify({
              registrationId: "0192f3a1-0000-7000-8000-000000000002",
              email: "visual.submitting@e2e.thi360.vn",
              resendAfterSeconds: 60,
            }),
          }),
        );
        await page.goto(`${WEB_URL}/register`);
        await page.locator('input[name="username"]').fill("visual.submitting");
        await page.locator('input[name="fullName"]').fill("E2E Student");
        await page.locator('input[name="email"]').fill("visual.submitting@e2e.thi360.vn");
        await page.locator('input[name="password"]').fill("Thi360@e2e1");
        await page.getByRole("checkbox").check();
        await page.locator("form button[type=submit]").click();
        await expect(page.locator("input[autocomplete=one-time-code]")).toBeVisible();
        await expectMatrixClean(page, "register-otp", cell);
        await page.unroute("**/api/v1/auth/register");

        await page.goto(`${WEB_URL}/forgot-password`);
        await expect(page.getByRole("heading", { level: 1 })).toBeVisible();
        await expectMatrixClean(page, "forgot-password", cell);

        // Lỗi máy chủ.
        await failApi(page, "**/api/v1/auth/password/forgot", 500, SERVER_ERROR);
        await page.getByLabel(/^Email/).fill("an@e2e.thi360.vn");
        await page.locator("form button[type=submit]").click();
        await expect(page.locator("main [role=alert]")).toBeVisible();
        await expectMatrixClean(page, "forgot-password-error", cell);
        await page.unroute("**/api/v1/auth/password/forgot");

        // Đang gửi.
        const releaseForgot = await holdApi(page, "**/api/v1/auth/password/forgot", "abort");
        await page.locator("form button[type=submit]").click();
        await expectSubmitting(page);
        await expectMatrixClean(page, "forgot-password-submitting", cell);
        await releaseForgot();

        // Đã gửi.
        await page.goto(`${WEB_URL}/forgot-password`);
        await page.getByLabel(/^Email/).fill("an@e2e.thi360.vn");
        await page.locator("form button[type=submit]").click();
        await expect(page.locator("#forgot-sent-title")).toBeVisible();
        await expectMatrixClean(page, "forgot-password-sent", cell);

        await page.goto(`${WEB_URL}/reset-password?token=khong-co-that`);
        await expect(page.getByRole("heading", { level: 1 })).toBeVisible();
        await expectMatrixClean(page, "reset-password", cell);

        // Lỗi máy chủ.
        await failApi(page, "**/api/v1/auth/password/reset", 503, SERVER_ERROR);
        await page.locator('input[name="newPassword"]').fill("Thi360@moi2");
        await page.locator('input[name="confirmPassword"]').fill("Thi360@moi2");
        await page.locator("form button[type=submit]").click();
        await expect(page.locator("form [role=alert]")).toBeVisible();
        await expectMatrixClean(page, "reset-password-error", cell);
        await page.unroute("**/api/v1/auth/password/reset");

        // Đang gửi.
        const releaseReset = await holdApi(page, "**/api/v1/auth/password/reset", "abort");
        await page.locator("form button[type=submit]").click();
        await expectSubmitting(page);
        await expectMatrixClean(page, "reset-password-submitting", cell);
        await releaseReset();

        await page.goto(`${WEB_URL}/reset-password`);
        await expect(page.getByRole("heading", { level: 1 })).toBeVisible();
        await expectMatrixClean(page, "reset-password-invalid", cell);
      } finally {
        await context.close();
      }
    });

    test("màn cần phiên: xác minh email và hồ sơ (đang tải, lỗi, có dữ liệu, rỗng, có ảnh và email chờ)", async () => {
      const { context, page } = await openCell(cell, true);
      try {
        // Xác minh email (dành cho tài khoản do admin tạo chưa xác minh email): khung chờ, rồi biểu mẫu.
        const unverifiedProfile = {
          username: "visual.student",
          fullName: "E2E Student",
          email: "visual.student@e2e.thi360.vn",
          emailVerified: false,
          phone: null,
          avatarAssetId: null,
          avatarUrl: null,
          pendingEmail: null,
        };
        const { promise: profileGate, resolve: openProfileGate } = Promise.withResolvers<void>();
        await page.route("**/api/v1/me/profile", async (route) => {
          await profileGate;
          await route.fulfill({
            status: 200,
            contentType: "application/json",
            body: JSON.stringify(unverifiedProfile),
          });
        });
        await page.goto(`${WEB_URL}/verify-email`, { waitUntil: "commit" });
        await expect(page.locator("[role=status][aria-label]")).toBeVisible();
        await expectMatrixClean(page, "verify-email-loading", cell);
        openProfileGate();
        await expect(page.getByRole("heading", { level: 1 })).toBeVisible();
        await expect(page.locator("input[autocomplete=one-time-code]")).toBeVisible();
        await expectMatrixClean(page, "verify-email", cell);
        await page.unroute("**/api/v1/me/profile");

        // Xác minh email: lỗi tải hồ sơ.
        await failApi(page, "**/api/v1/me/profile", 500, SERVER_ERROR);
        await page.goto(`${WEB_URL}/verify-email`);
        await expect(page.getByRole("button", { name: cell.lang === "vi" ? "Thử lại" : "Retry", exact: true })).toBeVisible();
        await expectMatrixClean(page, "verify-email-error", cell);
        await page.unroute("**/api/v1/me/profile");

        // Hồ sơ: khung chờ, rồi dữ liệu ở trạng thái rỗng (chưa có ảnh, chưa số điện thoại, email chưa xác minh).
        const { promise: emptyProfileGate, resolve: openEmptyProfileGate } = Promise.withResolvers<void>();
        await page.route("**/api/v1/me/profile", async (route) => {
          await emptyProfileGate;
          await route.fulfill({
            status: 200,
            contentType: "application/json",
            body: JSON.stringify(unverifiedProfile),
          });
        });
        await page.goto(`${WEB_URL}/account/profile`, { waitUntil: "commit" });
        await expect(page.locator("[role=status][aria-label]")).toBeVisible();
        await expectMatrixClean(page, "profile-loading", cell);
        openEmptyProfileGate();

        await expect(page.getByRole("heading", { level: 1 })).toBeVisible();
        await expect(page.locator("#profile-email-title")).toBeVisible();
        await expectMatrixClean(page, "profile-empty", cell);
        await page.unroute("**/api/v1/me/profile");

        // Hồ sơ: có ảnh đại diện, số điện thoại và email mới đang chờ xác minh.
        await page.route("**/api/v1/me/profile", (route) =>
          route.fulfill({
            status: 200,
            contentType: "application/json",
            body: JSON.stringify({
              username: "visual.student",
              fullName: "E2E Student",
              email: "visual.student@e2e.thi360.vn",
              emailVerified: true,
              phone: "0912345678",
              avatarAssetId: AVATAR_ID,
              avatarUrl: `/api/v1/avatars/${AVATAR_ID}`,
              pendingEmail: "visual.next@e2e.thi360.vn",
            }),
          }),
        );
        await page.route(`**/api/v1/avatars/${AVATAR_ID}`, (route) =>
          route.fulfill({ status: 200, contentType: "image/png", body: solidPng(112) }),
        );
        await page.goto(`${WEB_URL}/account/profile`);
        await expect(page.locator("img[src^='/api/v1/avatars/']")).toBeVisible();
        await expect(page.locator("#profile-pending-title")).toBeVisible();
        await expectMatrixClean(page, "profile-avatar-pending", cell);
        await page.unroute("**/api/v1/me/profile");

        // Hồ sơ: lỗi tải.
        await failApi(page, "**/api/v1/me/profile", 500, SERVER_ERROR);
        await page.goto(`${WEB_URL}/account/profile`);
        await expect(page.getByRole("button", { name: cell.lang === "vi" ? "Thử lại" : "Retry", exact: true })).toBeVisible();
        await expectMatrixClean(page, "profile-error", cell);
      } finally {
        await context.close();
      }
    });
  });
}
