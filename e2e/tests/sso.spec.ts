import { expect, type Page, test } from "@playwright/test";

const WEB_URL = (process.env.THI360_WEB_URL ?? "http://localhost:3000").replace(/\/+$/, "");
const ADMIN_URL = (process.env.THI360_ADMIN_URL ?? "http://localhost:3001").replace(/\/+$/, "");
const DEV_PASSWORD = process.env.THI360_DEV_PASSWORD ?? "Thi360@dev";
const PLATFORM_PASSWORD = process.env.THI360_DEV_PLATFORM_PASSWORD ?? "platform-dev-only";

async function submitLoginForm(page: Page, identifier: string, password: string) {
  await page.getByLabel(/Tên đăng nhập hoặc email/).fill(identifier);
  await page.getByLabel(/Mật khẩu/).fill(password);
  await page.getByRole("button", { name: "Đăng nhập" }).click();
}

test.describe("Đăng nhập một nơi (SSO) và điều hướng theo vai trò", () => {
  test("ops.admin đăng nhập ở web học viên rồi vào thẳng web admin không cần đăng nhập lại", async ({ page }) => {
    await page.goto(`${WEB_URL}/dang-nhap`);
    await submitLoginForm(page, "ops.admin", DEV_PASSWORD);

    await expect(page).toHaveURL(new RegExp(`^${ADMIN_URL}/?$`));
    await expect(page.getByRole("heading", { name: /Xin chào/ })).toBeVisible();
    await expect(page.getByRole("button", { name: "Menu tài khoản" })).toContainText("ops.admin");
  });

  test("student01 đăng nhập được đưa tới /home trên web học viên", async ({ page }) => {
    await page.goto(`${WEB_URL}/dang-nhap`);
    await submitLoginForm(page, "student01", DEV_PASSWORD);

    await expect(page).toHaveURL(`${WEB_URL}/home`);
    await expect(page.getByRole("heading", { name: /Chào/ })).toBeVisible();
    await expect(page.getByRole("button", { name: "Menu tài khoản" })).toContainText("student01");
  });

  test("mở thẳng /users trên web-admin khi chưa đăng nhập sẽ quay lại đúng /users sau khi đăng nhập", async ({
    page,
  }) => {
    await page.goto(`${ADMIN_URL}/users`);
    await expect(page).toHaveURL(new RegExp(`^${WEB_URL}/dang-nhap\\?callbackUrl=`));

    await submitLoginForm(page, "ops.admin", DEV_PASSWORD);
    await expect(page).toHaveURL(`${ADMIN_URL}/users`);
  });

  test("callbackUrl trỏ ra ngoài hệ thống bị bỏ qua: ops.admin tới trang chủ web admin", async ({ page }) => {
    const foreign = encodeURIComponent("https://trang-la.example");
    await page.goto(`${WEB_URL}/dang-nhap?callbackUrl=${foreign}`);
    await submitLoginForm(page, "ops.admin", DEV_PASSWORD);

    await expect(page).toHaveURL(new RegExp(`^${ADMIN_URL}/?$`));
    await expect(page.getByRole("heading", { name: /Xin chào/ })).toBeVisible();
  });

  test("callbackUrl trỏ ra ngoài hệ thống bị bỏ qua: student01 tới /home", async ({ page }) => {
    const foreign = encodeURIComponent("https://trang-la.example");
    await page.goto(`${WEB_URL}/dang-nhap?callbackUrl=${foreign}`);
    await submitLoginForm(page, "student01", DEV_PASSWORD);

    await expect(page).toHaveURL(`${WEB_URL}/home`);
    await expect(page.getByRole("heading", { name: /Chào/ })).toBeVisible();
  });

  test("student01 mở web admin bị chặn ở /forbidden và API quản trị trả 403", async ({ page }) => {
    await page.goto(`${WEB_URL}/dang-nhap`);
    await submitLoginForm(page, "student01", DEV_PASSWORD);
    await expect(page).toHaveURL(`${WEB_URL}/home`);

    await page.goto(ADMIN_URL);
    await expect(page).toHaveURL(`${ADMIN_URL}/forbidden`);
    await expect(page.getByRole("heading", { name: "Bạn không có quyền vào trang này" })).toBeVisible();

    const apiResponse = await page.request.get(`${ADMIN_URL}/api/v1/admin/users`);
    expect(apiResponse.status()).toBe(403);
  });

  test("đăng xuất ở web admin xóa phiên trên cả web học viên", async ({ page }) => {
    await page.goto(`${WEB_URL}/dang-nhap`);
    await submitLoginForm(page, "ops.admin", DEV_PASSWORD);
    await expect(page).toHaveURL(new RegExp(`^${ADMIN_URL}/?$`));

    await page.getByRole("button", { name: "Menu tài khoản" }).click();
    await page.getByRole("menuitem", { name: "Đăng xuất" }).click();
    await expect(page).toHaveURL(`${WEB_URL}/dang-nhap`);

    await page.goto(`${WEB_URL}/home`);
    await expect(page).toHaveURL(new RegExp(`^${WEB_URL}/dang-nhap\\?callbackUrl=`));
  });

  test("tài khoản platform (realm master) đăng nhập vào được web admin", async ({ page }) => {
    await page.goto(`${WEB_URL}/dang-nhap`);
    await submitLoginForm(page, "platform", PLATFORM_PASSWORD);

    await expect(page).toHaveURL(new RegExp(`^${ADMIN_URL}/?$`));
    await expect(page.getByRole("button", { name: "Menu tài khoản" })).toContainText("platform");
  });
});
