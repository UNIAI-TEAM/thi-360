import { expect, test } from "@playwright/test";
import { createVerifiedAccount, newAccount, readOtp, waitForMail, WEB_URL } from "./helpers/mail";

test("đổi email bằng OTP (AC-3)", async ({ context, page }) => {
  const { account, cookies } = await createVerifiedAccount("chg");
  const newEmail = newAccount("moi").email;
  await context.addCookies(cookies);

  await page.goto(`${WEB_URL}/account/profile`);
  const emailSection = page.getByRole("region", { name: "Email", exact: true });
  await expect(emailSection).toContainText(account.email);
  await expect(emailSection).toContainText("Đã xác minh");

  // Nhập email mới và mật khẩu hiện tại: mã OTP gửi tới email mới.
  await emailSection.getByRole("button", { name: "Đổi email" }).click();
  await page.getByLabel(/^Email mới/).fill(newEmail);
  await page.getByLabel(/^Mật khẩu hiện tại/).fill(account.password);
  await page.getByRole("button", { name: "Gửi mã xác minh" }).click();
  await expect(page.getByRole("heading", { name: `Đang chờ xác minh email mới: ${newEmail}` })).toBeVisible();
  // Trong lúc chờ, hồ sơ vẫn giữ email cũ.
  await expect(emailSection).toContainText(account.email);

  const mail = await waitForMail(newEmail, "EMAIL_CHANGE");
  await page.getByLabel(/^Mã xác minh/).fill(readOtp(mail));
  await page.getByRole("button", { name: "Xác nhận" }).click();

  await expect(emailSection).toContainText(newEmail);
  await expect(emailSection).toContainText("Đã xác minh");
  await expect(page.getByRole("heading", { name: /Đang chờ xác minh email mới/ })).toHaveCount(0);

  // Còn sau khi tải lại: máy chủ đã đổi email, không chỉ giao diện.
  await page.reload();
  await expect(emailSection).toContainText(newEmail);
  await expect(emailSection).toContainText("Đã xác minh");
  await expect(emailSection).not.toContainText(account.email);
});
