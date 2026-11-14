import { type BrowserContext, expect, type Page, test } from "@playwright/test";

const WEB_URL = (process.env.THI360_WEB_URL ?? "http://localhost:3000").replace(/\/+$/, "");
const ADMIN_URL = (process.env.THI360_ADMIN_URL ?? "http://localhost:3001").replace(/\/+$/, "");
const DEV_PASSWORD = process.env.THI360_DEV_PASSWORD ?? "Thi360@dev";

async function login(page: Page, identifier: string) {
  await page.goto(`${WEB_URL}/login`);
  await page.getByLabel(/Tên đăng nhập hoặc email/).fill(identifier);
  await page.getByLabel(/Mật khẩu/).fill(DEV_PASSWORD);
  await page.getByRole("button", { name: "Đăng nhập" }).click();
}

/**
 * Access token và cookie điều hướng sống 5 phút. Khi hết hạn trình duyệt tự bỏ hai cookie này, còn
 * refresh token thì vẫn ở lại: xóa đúng hai cookie đó là mô phỏng trung thực mà không phải chờ 5 phút.
 */
async function expireAccessToken(context: BrowserContext) {
  await context.clearCookies({ name: "thi360_at" });
  await context.clearCookies({ name: "thi360_ctx" });
  const names = (await context.cookies()).map((cookie) => cookie.name);
  expect(names).toContain("thi360_rt");
  expect(names).not.toContain("thi360_at");
}

async function cookieNames(context: BrowserContext) {
  return (await context.cookies()).map((cookie) => cookie.name);
}

/** Đếm số lần tài liệu trang được tải lại và ghi mọi lần đi qua trang đăng nhập. */
function watchNavigation(page: Page) {
  const state = { documents: 0, loginVisits: 0 };
  page.on("request", (request) => {
    if (request.resourceType() !== "document" || request.frame() !== page.mainFrame()) return;
    state.documents += 1;
    if (new URL(request.url()).pathname === "/login") state.loginVisits += 1;
  });
  return state;
}

test.describe("Access token hết hạn: phiên tự làm mới, không tải lại trang, không về trang đăng nhập (UNI-2574)", () => {
  test("gọi API trong trang sau khi hết hạn: kết quả về ngay, trang không tải lại, ô tìm kiếm giữ nội dung", async ({
    page,
    context,
  }) => {
    await login(page, "sys.admin");
    await expect(page).toHaveURL(new RegExp(`^${ADMIN_URL}/?$`));
    await page.goto(`${ADMIN_URL}/users`);
    await expect(page.getByRole("heading", { level: 1, name: "Tài khoản" })).toBeVisible();
    const search = page.getByLabel("Tìm kiếm");
    await expect(search).toBeVisible();

    const navigation = watchNavigation(page);
    await expireAccessToken(context);

    const listResponse = page.waitForResponse(
      (response) => response.url().includes("/api/v1/admin/users?") && response.url().includes("q=student03"),
    );
    await search.fill("student03");
    expect((await listResponse).status()).toBe(200);

    await expect(page.getByRole("cell", { name: "student03", exact: true })).toBeVisible();
    await expect(search).toHaveValue("student03");
    expect(navigation.documents).toBe(0);
    expect(navigation.loginVisits).toBe(0);
    expect(await cookieNames(context)).toEqual(expect.arrayContaining(["thi360_at", "thi360_ctx", "thi360_rt"]));
  });

  test("bấm mục menu sau khi hết hạn: vào đúng trang, không qua trang đăng nhập", async ({ page, context }) => {
    await login(page, "sys.admin");
    await expect(page).toHaveURL(new RegExp(`^${ADMIN_URL}/?$`));
    await page.goto(`${ADMIN_URL}/users`);
    await expect(page.getByRole("heading", { level: 1, name: "Tài khoản" })).toBeVisible();

    const navigation = watchNavigation(page);
    await expireAccessToken(context);

    await page.getByRole("link", { name: "Lớp học", exact: true }).click();

    await expect(page).toHaveURL(`${ADMIN_URL}/classes`);
    await expect(page.getByRole("heading", { level: 1, name: "Lớp học" })).toBeVisible();
    expect(navigation.loginVisits).toBe(0);
    expect(await cookieNames(context)).toEqual(expect.arrayContaining(["thi360_at", "thi360_ctx", "thi360_rt"]));
  });

  test("tải lại trang (F5) sau khi hết hạn: ở lại đúng trang, vẫn đăng nhập", async ({ page, context }) => {
    await login(page, "sys.admin");
    await expect(page).toHaveURL(new RegExp(`^${ADMIN_URL}/?$`));
    await page.goto(`${ADMIN_URL}/classes`);
    await expect(page.getByRole("heading", { level: 1, name: "Lớp học" })).toBeVisible();

    const navigation = watchNavigation(page);
    await expireAccessToken(context);
    await page.reload();

    await expect(page).toHaveURL(`${ADMIN_URL}/classes`);
    await expect(page.getByRole("heading", { level: 1, name: "Lớp học" })).toBeVisible();
    await expect(page.getByRole("button", { name: "Menu tài khoản" })).toContainText("sys.admin");
    expect(navigation.loginVisits).toBe(0);
  });

  test("học viên mở lại /login khi phiên còn hạn làm mới: được đưa về /home, không phải đăng nhập lại", async ({
    page,
    context,
  }) => {
    await login(page, "student02");
    await expect(page).toHaveURL(`${WEB_URL}/home`);

    await expireAccessToken(context);
    await page.goto(`${WEB_URL}/login`);

    await expect(page).toHaveURL(`${WEB_URL}/home`);
    await expect(page.getByRole("button", { name: "Menu tài khoản" })).toContainText("student02");
  });

  test("đã có phiên mà mở trang đăng nhập kèm callbackUrl: quay lại đúng trang đó, kể cả khi vừa hết hạn", async ({
    page,
    context,
  }) => {
    await login(page, "sys.admin");
    await expect(page).toHaveURL(new RegExp(`^${ADMIN_URL}/?$`));
    const target = `${ADMIN_URL}/classes?page=1`;

    await page.goto(`${WEB_URL}/login?callbackUrl=${encodeURIComponent(target)}`);
    await expect(page).toHaveURL(target);

    await expireAccessToken(context);
    await page.goto(`${WEB_URL}/login?callbackUrl=${encodeURIComponent(target)}`);
    await expect(page).toHaveURL(target);
    await expect(page.getByRole("heading", { level: 1, name: "Lớp học" })).toBeVisible();

    await page.goto(`${WEB_URL}/login?callbackUrl=${encodeURIComponent("https://trang-la.example")}`);
    await expect(page).toHaveURL(new RegExp(`^${ADMIN_URL}/?$`));
  });

  test("refresh token không còn: về trang đăng nhập kèm callbackUrl như trước", async ({ page, context }) => {
    await login(page, "sys.admin");
    await expect(page).toHaveURL(new RegExp(`^${ADMIN_URL}/?$`));

    await context.clearCookies({ name: "thi360_at" });
    await context.clearCookies({ name: "thi360_ctx" });
    await context.clearCookies({ name: "thi360_rt" });
    await page.goto(`${ADMIN_URL}/classes`);

    await expect(page).toHaveURL(new RegExp(`^${WEB_URL}/login\\?callbackUrl=`));
    await expect(page.getByRole("button", { name: "Đăng nhập" })).toBeVisible();
  });

  test("thao tác ghi sau khi hết hạn: tạo và xóa lớp qua API của web vẫn qua kiểm tra XSRF, không phải tải lại trang", async ({
    page,
    context,
  }) => {
    await login(page, "sys.admin");
    await expect(page).toHaveURL(new RegExp(`^${ADMIN_URL}/?$`));
    await page.goto(`${ADMIN_URL}/classes`);
    await expect(page.getByRole("heading", { level: 1, name: "Lớp học" })).toBeVisible();

    const navigation = watchNavigation(page);
    await expireAccessToken(context);

    // Gửi đúng như ứng dụng gửi: X-XSRF-TOKEN lấy từ cookie `thi360_xsrf` tại thời điểm gửi. Lượt làm
    // mới phía máy chủ đổi cookie này, nên proxy phải đổi header tương ứng để backend chấp nhận.
    const code = `E2E-RF-${Date.now().toString(36).toUpperCase()}`;
    const created = await page.evaluate(async (classCode) => {
      const xsrf = decodeURIComponent(document.cookie.match(/(?:^|; )thi360_xsrf=([^;]+)/)?.[1] ?? "");
      const response = await fetch("/api/v1/admin/classes", {
        method: "POST",
        headers: { "content-type": "application/json", "x-xsrf-token": xsrf },
        body: JSON.stringify({ code: classCode, name: "Lớp thử làm mới phiên" }),
      });
      return { status: response.status, body: (await response.json()) as { id?: string } };
    }, code);
    expect(created.status).toBe(201);
    expect(created.body.id).toBeTruthy();

    const deleted = await page.evaluate(async (id) => {
      const xsrf = decodeURIComponent(document.cookie.match(/(?:^|; )thi360_xsrf=([^;]+)/)?.[1] ?? "");
      const response = await fetch(`/api/v1/admin/classes/${id}?reason=${encodeURIComponent("e2e làm mới phiên")}`, {
        method: "DELETE",
        headers: { "x-xsrf-token": xsrf },
      });
      return response.status;
    }, created.body.id as string);
    expect(deleted).toBe(204);

    // Thiếu X-XSRF-TOKEN thì sau khi hết hạn vẫn bị từ chối: lượt làm mới không tự cấp header.
    await expireAccessToken(context);
    const forged = await page.evaluate(async () => {
      const response = await fetch("/api/v1/admin/classes", {
        method: "POST",
        headers: { "content-type": "application/json" },
        body: JSON.stringify({ code: "E2E-RF-NOXSRF", name: "Không được tạo" }),
      });
      return response.status;
    });
    expect(forged).toBe(403);
    expect(navigation.documents).toBe(0);
  });
});
