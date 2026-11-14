import { expect, type Page, request as playwrightRequest, test } from "@playwright/test";

const WEB_URL = process.env.THI360_WEB_URL ?? "http://localhost:3000";
const ADMIN_URL = process.env.THI360_ADMIN_URL ?? "http://localhost:3001";
const PLATFORM_PASSWORD = process.env.THI360_DEV_PLATFORM_PASSWORD ?? "platform-dev-only";

async function submitLoginForm(page: Page, identifier: string, password: string) {
  await page.getByLabel(/Tên đăng nhập hoặc email/).fill(identifier);
  await page.getByLabel(/Mật khẩu/).fill(password);
  await page.getByRole("button", { name: "Đăng nhập" }).click();
}

async function createPlatformApiContext() {
  const api = await playwrightRequest.newContext({ baseURL: ADMIN_URL });
  const loginRes = await api.post(`${WEB_URL}/api/v1/auth/login`, {
    headers: { origin: WEB_URL },
    data: { identifier: "platform", password: PLATFORM_PASSWORD },
  });
  if (!loginRes.ok()) {
    throw new Error(`Platform API login failed: ${loginRes.status()} ${await loginRes.text()}`);
  }
  const state = await api.storageState();
  const xsrf = state.cookies.find((cookie) => cookie.name === "thi360_xsrf")?.value ?? "";
  return { api, xsrf };
}

test.describe("Hồ sơ học tập của học viên (UNI-2549)", () => {
  test("người dùng có quyền USER.LEARNING_VIEW mở tab Hồ sơ học tập và thấy 5 khối Chưa có dữ liệu", async ({
    page,
  }) => {
    const suffix = Date.now().toString(36);
    const roleCode = `E2E_LEARN_${suffix}`.toUpperCase();
    const username = `e2e.learn.${suffix}`;
    const password = `Thi360@${suffix}L1`;

    const { api, xsrf } = await createPlatformApiContext();
    let roleId: string | undefined;
    let userId: string | undefined;

    try {
      const roleRes = await api.post("/api/v1/admin/roles", {
        headers: { origin: ADMIN_URL, "x-xsrf-token": xsrf },
        data: {
          access: "MANAGE",
          code: roleCode,
          name: `E2E Learning Viewer ${suffix}`,
          permissionCodes: ["USER.VIEW", "USER.LEARNING_VIEW"],
        },
      });
      expect(roleRes.status()).toBe(201);
      const roleBody = (await roleRes.json()) as { id: string };
      roleId = roleBody.id;

      const userRes = await api.post("/api/v1/admin/users", {
        headers: { origin: ADMIN_URL, "x-xsrf-token": xsrf },
        data: {
          username,
          password,
          fullName: `Learning Viewer ${suffix}`,
          roleIds: [roleId],
          mustChangePassword: false,
        },
      });
      expect(userRes.status()).toBe(201);
      const userBody = (await userRes.json()) as { id: string };
      userId = userBody.id;

      await page.goto(`${WEB_URL}/dang-nhap`);
      await submitLoginForm(page, username, password);
      await expect(page).toHaveURL(new RegExp(`^${ADMIN_URL}/?$`));

      await page.getByRole("link", { name: "Tài khoản", exact: true }).click();
      await expect(page).toHaveURL(`${ADMIN_URL}/tai-khoan`);

      await page.getByLabel("Tìm kiếm").fill("student01");
      await page.getByRole("link", { name: "student01" }).click();
      await expect(page).toHaveURL(new RegExp(`^${ADMIN_URL}/tai-khoan/[^/]+$`));
      await expect(page.getByRole("heading", { name: /Học viên 01/ })).toBeVisible();

      const learningTab = page.getByRole("tab", { name: "Hồ sơ học tập" });
      await expect(learningTab).toBeVisible();

      const learningResponsePromise = page.waitForResponse(
        (response) =>
          response.url().includes("/api/v1/admin/users/") &&
          response.url().endsWith("/learning") &&
          response.request().method() === "GET",
      );
      await learningTab.click();
      const learningResponse = await learningResponsePromise;
      expect(learningResponse.status()).toBe(200);
      const learningBody = (await learningResponse.json()) as {
        dataAvailable: boolean;
        entitlements: unknown[];
        attempts: unknown[];
        assessments: unknown[];
        practice: unknown[];
        supportRequests: unknown[];
      };
      expect(learningBody.dataAvailable).toBe(false);

      for (const heading of [
        "Quyền sử dụng & gói bài thi",
        "Lịch sử lượt thi",
        "Đánh giá năng lực",
        "Lịch sử ôn luyện",
        "Yêu cầu hỗ trợ",
      ]) {
        await expect(page.getByRole("heading", { name: heading })).toBeVisible();
      }
      await expect(page.getByRole("heading", { name: "Chưa có dữ liệu" })).toHaveCount(5);
    } finally {
      if (userId) {
        await api.put(`/api/v1/admin/users/${userId}/roles`, {
          headers: { origin: ADMIN_URL, "x-xsrf-token": xsrf },
          data: { roleIds: [] },
        });
        await api.delete(`/api/v1/admin/users/${userId}?reason=E2E%20cleanup`, {
          headers: { origin: ADMIN_URL, "x-xsrf-token": xsrf },
        });
      }
      if (roleId) {
        await api.delete(`/api/v1/admin/roles/${roleId}`, {
          headers: { origin: ADMIN_URL, "x-xsrf-token": xsrf },
        });
      }
      await api.dispose();
    }
  });

  test("người dùng thiếu quyền USER.LEARNING_VIEW không thấy tab Hồ sơ học tập và không gọi API learning", async ({
    page,
  }) => {
    const suffix = Date.now().toString(36);
    const roleCode = `E2E_NO_LEARN_${suffix}`.toUpperCase();
    const username = `e2e.nolearn.${suffix}`;
    const password = `Thi360@${suffix}N1`;

    const { api, xsrf } = await createPlatformApiContext();
    let roleId: string | undefined;
    let userId: string | undefined;

    try {
      const roleRes = await api.post("/api/v1/admin/roles", {
        headers: { origin: ADMIN_URL, "x-xsrf-token": xsrf },
        data: {
          access: "MANAGE",
          code: roleCode,
          name: `E2E No Learning ${suffix}`,
          permissionCodes: ["USER.VIEW"],
        },
      });
      expect(roleRes.status()).toBe(201);
      const roleBody = (await roleRes.json()) as { id: string };
      roleId = roleBody.id;

      const userRes = await api.post("/api/v1/admin/users", {
        headers: { origin: ADMIN_URL, "x-xsrf-token": xsrf },
        data: {
          username,
          password,
          fullName: `No Learning ${suffix}`,
          roleIds: [roleId],
          mustChangePassword: false,
        },
      });
      expect(userRes.status()).toBe(201);
      const userBody = (await userRes.json()) as { id: string };
      userId = userBody.id;

      const learningRequests: string[] = [];
      page.on("request", (request) => {
        if (request.url().includes("/learning")) {
          learningRequests.push(request.url());
        }
      });

      await page.goto(`${WEB_URL}/dang-nhap`);
      await submitLoginForm(page, username, password);
      await expect(page).toHaveURL(new RegExp(`^${ADMIN_URL}/?$`));

      await page.getByRole("link", { name: "Tài khoản", exact: true }).click();
      await expect(page).toHaveURL(`${ADMIN_URL}/tai-khoan`);

      await page.getByLabel("Tìm kiếm").fill("student01");
      await page.getByRole("link", { name: "student01" }).click();
      await expect(page).toHaveURL(new RegExp(`^${ADMIN_URL}/tai-khoan/[^/]+$`));
      await expect(page.getByRole("heading", { name: /Học viên 01/ })).toBeVisible();

      await expect(page.getByRole("tab", { name: "Thông tin" })).toBeVisible();
      await expect(page.getByRole("tab", { name: "Hồ sơ học tập" })).toHaveCount(0);
      expect(learningRequests).toEqual([]);
    } finally {
      if (userId) {
        await api.put(`/api/v1/admin/users/${userId}/roles`, {
          headers: { origin: ADMIN_URL, "x-xsrf-token": xsrf },
          data: { roleIds: [] },
        });
        await api.delete(`/api/v1/admin/users/${userId}?reason=E2E%20cleanup`, {
          headers: { origin: ADMIN_URL, "x-xsrf-token": xsrf },
        });
      }
      if (roleId) {
        await api.delete(`/api/v1/admin/roles/${roleId}`, {
          headers: { origin: ADMIN_URL, "x-xsrf-token": xsrf },
        });
      }
      await api.dispose();
    }
  });
});
