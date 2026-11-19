import { expect, test } from "@playwright/test";
import {
  expectUrlWithCallback,
  fillRegisterForm,
  newAccount,
  readOtp,
  submitLoginForm,
  waitForMail,
  WEB_URL,
} from "./helpers/mail";

const DEV_PASSWORD = process.env.THI360_DEV_PASSWORD ?? "Thi360@dev";
// Đường dẫn bài mẫu của dữ liệu DS; trang chi tiết bài là của FS-1 nên chỉ kiểm điểm đến của URL.
const EXAM_PATH = process.env.THI360_E2E_EXAM_PATH ?? "/exams/BCA-FULL";

test.describe("Đăng ký, xác minh email và các đường tài khoản", () => {
  test("đăng ký, nhập OTP, quay lại callbackUrl (AC-1)", async ({ page }) => {
    const account = newAccount("reg");
    await page.goto(`${WEB_URL}/register?callbackUrl=${encodeURIComponent(EXAM_PATH)}`);
    await fillRegisterForm(page, account);
    await page.getByRole("button", { name: "Đăng ký" }).click();

    await page.waitForURL((url) => url.pathname === "/verify-email");
    await expect(page.getByRole("heading", { name: "Xác minh email" })).toBeVisible();
    await expect(page.getByText(account.email)).toBeVisible();

    const mail = await waitForMail(account.email, "EMAIL_VERIFY");
    await page.getByLabel(/^Mã xác minh/).fill(readOtp(mail));
    await page.getByRole("button", { name: "Xác minh", exact: true }).click();

    await page.waitForURL((url) => url.pathname === EXAM_PATH);
    expect(new URL(page.url()).origin).toBe(new URL(WEB_URL).origin);
  });

  test("giữ callbackUrl qua các trang; nút Để sau đi theo callbackUrl; callbackUrl ngoài hệ thống không được dùng (AC-7)", async ({
    page,
  }) => {
    await page.goto(`${WEB_URL}/login?callbackUrl=${encodeURIComponent(EXAM_PATH)}`);

    await page.getByRole("link", { name: "Đăng ký" }).click();
    await expectUrlWithCallback(page, "/register", EXAM_PATH);
    await page.getByRole("link", { name: "Đăng nhập" }).click();
    await expectUrlWithCallback(page, "/login", EXAM_PATH);
    await page.getByRole("link", { name: "Quên mật khẩu?" }).click();
    await expectUrlWithCallback(page, "/forgot-password", EXAM_PATH);
    await page.getByRole("link", { name: "Tạo tài khoản mới" }).click();
    await expectUrlWithCallback(page, "/register", EXAM_PATH);

    // Đăng ký xong, "Để sau" đi theo callbackUrl mà không cần nhập mã.
    const account = newAccount("later");
    await fillRegisterForm(page, account);
    await page.getByRole("button", { name: "Đăng ký" }).click();
    await page.waitForURL((url) => url.pathname === "/verify-email");
    await page.getByRole("link", { name: "Để sau" }).click();
    await page.waitForURL((url) => url.pathname === EXAM_PATH);

    // callbackUrl ngoài hệ thống: liên kết đi không mang theo.
    await page.context().clearCookies();
    await page.goto(`${WEB_URL}/login?callbackUrl=${encodeURIComponent("https://trang-la.example/x")}`);
    await expect(page.getByRole("link", { name: "Đăng ký" })).toHaveAttribute("href", "/register");
    await expect(page.getByRole("link", { name: "Quên mật khẩu?" })).toHaveAttribute("href", "/forgot-password");
  });

  test("đã đăng nhập mở /register về /home (AC-9)", async ({ page }) => {
    await page.goto(`${WEB_URL}/login`);
    await submitLoginForm(page, "student01", DEV_PASSWORD);
    await expect(page).toHaveURL(`${WEB_URL}/home`);

    await page.goto(`${WEB_URL}/register`);
    await expect(page).toHaveURL(`${WEB_URL}/home`);
  });

  test("đăng xuất về trang chủ và phiên đã hết (AC-10)", async ({ page }) => {
    await page.goto(`${WEB_URL}/login`);
    await submitLoginForm(page, "student01", DEV_PASSWORD);
    await expect(page).toHaveURL(`${WEB_URL}/home`);

    await page.getByRole("button", { name: "Menu tài khoản" }).click();
    await page.getByRole("menuitem", { name: "Đăng xuất" }).click();
    await expect(page).toHaveURL(`${WEB_URL}/`);

    expect((await page.request.get(`${WEB_URL}/api/v1/me`)).status()).toBe(401);
    await page.goto(`${WEB_URL}/home`);
    await expect(page).toHaveURL(new RegExp(`^${WEB_URL}/login\\?callbackUrl=`));
  });

  test("đường công khai và đường cần phiên (AC-20)", async ({ page }) => {
    await page.goto(`${WEB_URL}/register`);
    await expect(page.getByRole("heading", { name: "Đăng ký", level: 1 })).toBeVisible();

    await page.goto(`${WEB_URL}/forgot-password`);
    await expect(page.getByRole("heading", { name: "Quên mật khẩu", level: 1 })).toBeVisible();

    await page.goto(`${WEB_URL}/reset-password?token=khong-co-that`);
    await expect(page.getByRole("heading", { name: "Đặt lại mật khẩu", level: 1 })).toBeVisible();

    for (const path of ["/verify-email", "/account/profile"]) {
      await page.goto(`${WEB_URL}${path}`);
      await expect(page).toHaveURL(new RegExp(`^${WEB_URL}/login\\?callbackUrl=`));
      expect(new URL(page.url()).searchParams.get("callbackUrl")).toBe(`${WEB_URL}${path}`);
    }
  });

  test("trình duyệt còn thi360_rt hỏng vẫn đăng ký được và nhận phiên mới", async ({ context, page }) => {
    await context.addCookies([{ name: "thi360_rt", value: "refresh-token-hong", url: WEB_URL }]);
    const account = newAccount("broken");

    await page.goto(`${WEB_URL}/register`);
    await fillRegisterForm(page, account);
    await page.getByRole("button", { name: "Đăng ký" }).click();

    await page.waitForURL((url) => url.pathname === "/verify-email");
    await page.goto(`${WEB_URL}/home`);
    await expect(page).toHaveURL(`${WEB_URL}/home`);
    await expect(page.getByRole("button", { name: "Menu tài khoản" })).toContainText(account.username);
  });
});
