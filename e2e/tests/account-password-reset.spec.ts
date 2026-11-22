import { expect, test } from "@playwright/test";
import {
  createVerifiedAccount,
  NEW_PASSWORD,
  readResetToken,
  submitLoginForm,
  waitForMail,
  WEB_URL,
} from "./helpers/mail";

test.describe("Quên và đặt lại mật khẩu", () => {
  test("quên, đặt lại, đăng nhập (AC-2)", async ({ page }) => {
    const { account } = await createVerifiedAccount("reset");

    await page.goto(`${WEB_URL}/forgot-password`);
    await page.getByLabel(/^Email/).fill(account.email);
    await page.getByRole("button", { name: "Gửi hướng dẫn" }).click();
    await expect(page.getByRole("status").filter({ hasText: "Nếu email này" })).toHaveText(
      "Nếu email này đủ điều kiện khôi phục tài khoản, bạn sẽ nhận được hướng dẫn đặt lại mật khẩu.",
    );

    const token = readResetToken(await waitForMail(account.email, "PASSWORD_RESET"));
    await page.goto(`${WEB_URL}/reset-password?token=${encodeURIComponent(token)}`);
    await page.getByLabel(/^Mật khẩu mới/).fill(NEW_PASSWORD);
    await page.getByLabel(/^Nhập lại mật khẩu mới/).fill(NEW_PASSWORD);
    await page.getByRole("button", { name: "Đặt mật khẩu mới" }).click();

    // Về /login có thông báo thành công, không kèm callbackUrl và không tự đăng nhập.
    await expect(page).toHaveURL(`${WEB_URL}/login?reset=done`);
    await expect(page.getByRole("status")).toHaveText("Đã đặt lại mật khẩu. Hãy đăng nhập bằng mật khẩu mới.");
    await page.goto(`${WEB_URL}/home`);
    await expect(page).toHaveURL(new RegExp(`^${WEB_URL}/login\\?callbackUrl=`));

    // Mật khẩu cũ không còn dùng được, mật khẩu mới đăng nhập được.
    await submitLoginForm(page, account.username, account.password);
    // `main`: Next.js thêm một vùng `role=alert` rỗng ngoài `main` (route announcer).
    await expect(page.locator("main").getByRole("alert")).toHaveText("Tên đăng nhập hoặc mật khẩu không đúng.");
    await submitLoginForm(page, account.username, NEW_PASSWORD);
    await expect(page).toHaveURL(`${WEB_URL}/home`);

    // Liên kết đã dùng không còn hiệu lực: trang hướng dẫn gửi yêu cầu mới.
    await page.context().clearCookies();
    await page.goto(`${WEB_URL}/reset-password?token=${encodeURIComponent(token)}`);
    await page.getByLabel(/^Mật khẩu mới/).fill(NEW_PASSWORD);
    await page.getByLabel(/^Nhập lại mật khẩu mới/).fill(NEW_PASSWORD);
    await page.getByRole("button", { name: "Đặt mật khẩu mới" }).click();
    await expect(page.getByRole("heading", { name: "Liên kết không còn dùng được" })).toBeVisible();
    await expect(page.getByRole("link", { name: "Gửi yêu cầu mới" })).toHaveAttribute("href", "/forgot-password");
  });

  test("header Referrer-Policy của /reset-password (AC-11)", async ({ page }) => {
    const response = await page.goto(`${WEB_URL}/reset-password?token=bat-ky`);
    expect(response?.headers()["referrer-policy"]).toBe("no-referrer");
  });
});
