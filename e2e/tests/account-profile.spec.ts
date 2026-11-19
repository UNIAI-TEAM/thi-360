import { expect, test } from "@playwright/test";
import { createVerifiedAccount, solidPng, WEB_URL } from "./helpers/account";

test.describe("Hồ sơ học viên", () => {
  test("lưu hồ sơ còn sau khi tải lại (AC-16)", async ({ context, page }) => {
    const { account, cookies } = await createVerifiedAccount("prof");
    await context.addCookies(cookies);
    await page.goto(`${WEB_URL}/account/profile`);

    const username = page.getByLabel("Tên đăng nhập", { exact: true });
    await expect(username).toHaveValue(account.username);
    await expect(username).toHaveAttribute("readonly", "");

    // Sai quy tắc: báo lỗi tại ô nhập, chưa lưu.
    const phone = page.getByLabel(/^Số điện thoại/);
    await phone.fill("12345");
    await phone.blur();
    await expect(page.getByText("Số điện thoại gồm 10 chữ số, bắt đầu bằng 0.")).toBeVisible();

    await page.getByLabel(/^Họ và tên/).fill("E2E Nguyen Van B");
    await phone.fill("0912345678");
    await page.getByRole("button", { name: "Lưu thay đổi" }).click();
    await expect(page.getByText("Đã lưu thông tin cá nhân.")).toBeVisible();

    await page.reload();
    await expect(page.getByLabel(/^Họ và tên/)).toHaveValue("E2E Nguyen Van B");
    await expect(page.getByLabel(/^Số điện thoại/)).toHaveValue("0912345678");
    await expect(page.getByLabel("Tên đăng nhập", { exact: true })).toHaveValue(account.username);
  });

  test("tải lên và xóa ảnh đại diện (AC-18)", async ({ context, page }) => {
    const { cookies } = await createVerifiedAccount("avatar");
    await context.addCookies(cookies);
    await page.goto(`${WEB_URL}/account/profile`);

    // Chưa có ảnh: hiện chữ cái đầu tên, không có nút Xóa ảnh.
    const initials = page.getByRole("img", { name: "Chưa có ảnh đại diện, đang hiện chữ cái đầu tên" });
    await expect(initials).toBeVisible();
    await expect(page.getByRole("button", { name: "Xóa ảnh" })).toHaveCount(0);

    await page.locator('input[type="file"]').setInputFiles({
      name: "anh-the.png",
      mimeType: "image/png",
      buffer: solidPng(64),
    });
    const avatar = page.getByRole("img", { name: "Ảnh đại diện hiện tại" });
    await expect(avatar).toBeVisible();

    // Ảnh mới còn sau khi tải lại và tải về được (đã nằm ở máy chủ).
    await page.reload();
    await expect(avatar).toBeVisible();
    const src = await avatar.getAttribute("src");
    expect(src).toMatch(/^\/api\/v1\/avatars\//);
    expect((await page.request.get(`${WEB_URL}${src}`)).status()).toBe(200);

    await page.getByRole("button", { name: "Xóa ảnh" }).click();
    await expect(initials).toBeVisible();
    await page.reload();
    await expect(initials).toBeVisible();
    await expect(page.getByRole("img", { name: "Ảnh đại diện hiện tại" })).toHaveCount(0);
  });

  test("mục Hồ sơ trên thanh đầu trang (AC-19)", async ({ context, page }) => {
    const { cookies } = await createVerifiedAccount("menu");
    await context.addCookies(cookies);
    await page.goto(`${WEB_URL}/home`);

    await page.getByRole("button", { name: "Menu tài khoản" }).click();
    await page.getByRole("menuitem", { name: "Hồ sơ" }).click();
    await expect(page).toHaveURL(`${WEB_URL}/account/profile`);
    await expect(page.getByRole("heading", { name: "Hồ sơ", level: 1 })).toBeVisible();
  });
});
