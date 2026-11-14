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

test.describe("Quản lý tài khoản (UNI-2546)", () => {
  test("vòng đời tài khoản: tạo -> ngừng hoạt động (đăng nhập thất bại) -> kích hoạt -> reset -> xóa mềm -> tạo lại cùng tên", async ({
    page,
    browser,
  }) => {
    const suffix = Date.now().toString(36);
    const username = `e2e.u.${suffix}`;
    const fullName = `E2E User ${suffix}`;
    const initialPassword = `Thi360@${suffix}1A`;
    const resetPassword = `Thi360@${suffix}2B`;
    const recreatedPassword = `Thi360@${suffix}3C`;

    await page.goto(`${WEB_URL}/dang-nhap`);
    await submitLoginForm(page, "platform", PLATFORM_PASSWORD);
    await expect(page).toHaveURL(new RegExp(`^${ADMIN_URL}/?$`));

    await page.getByRole("link", { name: "Tài khoản", exact: true }).click();
    await expect(page).toHaveURL(`${ADMIN_URL}/tai-khoan`);
    await expect(page.getByRole("heading", { name: "Tài khoản" })).toBeVisible();

    // 1. Tạo tài khoản mới
    await page.getByRole("button", { name: "Tạo tài khoản" }).click();
    const createDialog = page.getByRole("dialog");
    await expect(createDialog.getByRole("heading", { name: "Tạo tài khoản" })).toBeVisible();

    const usernameInput = createDialog.getByLabel(/Tên đăng nhập/);
    await usernameInput.fill(username);
    await usernameInput.blur();
    await expect(createDialog.getByText(/Có thể dùng\. 4–30 ký tự/)).toBeVisible();

    await createDialog.getByLabel(/Mật khẩu/).fill(initialPassword);
    await createDialog.getByLabel(/Họ và tên/).fill(fullName);
    await createDialog.getByRole("button", { name: "Tạo tài khoản", exact: true }).click();

    await expect(createDialog).toBeHidden();

    const searchInput = page.getByLabel("Tìm kiếm");
    await searchInput.fill(username);
    const userRowLink = page.getByRole("link", { name: username });
    await expect(userRowLink).toBeVisible();
    await userRowLink.click();

    await expect(page).toHaveURL(new RegExp(`^${ADMIN_URL}/tai-khoan/[^/]+$`));
    await expect(page.getByRole("heading", { name: fullName })).toBeVisible();

    // 2. Ngừng hoạt động -> đăng nhập thất bại
    await page.getByRole("button", { name: "Ngừng hoạt động" }).click();
    const deactivateDialog = page.getByRole("dialog");
    await deactivateDialog.getByLabel(/Lý do ngừng hoạt động/).fill("E2E kiểm tra khóa");
    await deactivateDialog.getByRole("button", { name: "Ngừng hoạt động" }).click();
    await expect(deactivateDialog).toBeHidden();
    await expect(page.getByText("Ngừng hoạt động", { exact: true }).first()).toBeVisible();

    const guestContext = await browser.newContext();
    try {
      const guestPage = await guestContext.newPage();
      await guestPage.goto(`${WEB_URL}/dang-nhap`);
      await submitLoginForm(guestPage, username, initialPassword);
      await expect(guestPage).toHaveURL(new RegExp(`^${WEB_URL}/dang-nhap`));
      await expect(guestPage.getByRole("alert")).toBeVisible();
    } finally {
      await guestContext.close();
    }

    // 3. Kích hoạt lại
    await page.getByRole("button", { name: "Kích hoạt lại" }).click();
    const activateDialog = page.getByRole("dialog");
    await activateDialog.getByRole("button", { name: "Kích hoạt lại" }).click();
    await expect(activateDialog).toBeHidden();
    await expect(page.getByRole("button", { name: "Ngừng hoạt động" })).toBeVisible();

    // 4. Reset mật khẩu -> đăng nhập thành công bằng mật khẩu mới
    await page.getByRole("button", { name: "Đặt lại mật khẩu" }).click();
    const resetDialog = page.getByRole("dialog");
    await resetDialog.getByLabel(/Mật khẩu mới/).fill(resetPassword);
    await resetDialog.getByLabel(/Lý do đặt lại mật khẩu/).fill("E2E reset");
    await resetDialog.getByRole("button", { name: "Đặt lại mật khẩu" }).click();
    await expect(resetDialog).toBeHidden();

    const resetLoginContext = await browser.newContext();
    try {
      const resetLoginPage = await resetLoginContext.newPage();
      await resetLoginPage.goto(`${WEB_URL}/dang-nhap`);
      await submitLoginForm(resetLoginPage, username, resetPassword);
      await expect(resetLoginPage).toHaveURL(`${WEB_URL}/home`);
    } finally {
      await resetLoginContext.close();
    }

    // 5. Xóa mềm
    await page.getByRole("button", { name: "Xóa" }).click();
    const deleteDialog = page.getByRole("dialog");
    await deleteDialog.getByLabel(/Lý do xóa tài khoản/).fill("E2E xóa mềm");
    await deleteDialog.getByRole("button", { name: "Xóa tài khoản" }).click();
    await expect(deleteDialog).toBeHidden();
    await expect(page.getByText("Đã xóa", { exact: true }).first()).toBeVisible();

    // 6. Tạo lại cùng tên đăng nhập
    await page.getByRole("link", { name: /Quay lại danh sách/ }).click();
    await expect(page).toHaveURL(`${ADMIN_URL}/tai-khoan`);

    await page.getByRole("button", { name: "Tạo tài khoản" }).click();
    const recreateDialog = page.getByRole("dialog");
    const recreateUsernameInput = recreateDialog.getByLabel(/Tên đăng nhập/);
    await recreateUsernameInput.fill(username);
    await recreateUsernameInput.blur();
    await expect(recreateDialog.getByText(/Có thể dùng\. 4–30 ký tự/)).toBeVisible();

    await recreateDialog.getByLabel(/Mật khẩu/).fill(recreatedPassword);
    await recreateDialog.getByLabel(/Họ và tên/).fill(`${fullName} Recreated`);
    await recreateDialog.getByRole("button", { name: "Tạo tài khoản", exact: true }).click();
    await expect(recreateDialog).toBeHidden();

    await page.getByLabel("Tìm kiếm").fill(username);
    await expect(page.getByRole("link", { name: username })).toBeVisible();

    // Dọn dẹp tài khoản vừa tạo lại
    await page.getByRole("link", { name: username }).click();
    await page.getByRole("button", { name: "Xóa" }).click();
    const cleanupDialog = page.getByRole("dialog");
    await cleanupDialog.getByLabel(/Lý do xóa tài khoản/).fill("E2E cleanup");
    await cleanupDialog.getByRole("button", { name: "Xóa tài khoản" }).click();
    await expect(cleanupDialog).toBeHidden();
  });

  test("người dùng chỉ có quyền USER.VIEW không thấy nút hành động trên danh sách và chi tiết", async ({
    page,
  }) => {
    const suffix = Date.now().toString(36);
    const roleCode = `E2E_VIEW_${suffix}`.toUpperCase();
    const viewerUsername = `e2e.view.${suffix}`;
    const viewerPassword = `Thi360@${suffix}V1`;

    const { api, xsrf } = await createPlatformApiContext();
    let roleId: string | undefined;
    let viewerId: string | undefined;

    try {
      const roleRes = await api.post("/api/v1/admin/roles", {
        headers: { origin: ADMIN_URL, "x-xsrf-token": xsrf },
        data: {
          access: "MANAGE",
          code: roleCode,
          name: `E2E Viewer ${suffix}`,
          permissionCodes: ["USER.VIEW"],
        },
      });
      expect(roleRes.status()).toBe(201);
      const roleBody = (await roleRes.json()) as { id: string };
      roleId = roleBody.id;

      const userRes = await api.post("/api/v1/admin/users", {
        headers: { origin: ADMIN_URL, "x-xsrf-token": xsrf },
        data: {
          username: viewerUsername,
          password: viewerPassword,
          fullName: `Viewer ${suffix}`,
          roleIds: [roleId],
          mustChangePassword: false,
        },
      });
      expect(userRes.status()).toBe(201);
      const userBody = (await userRes.json()) as { id: string };
      viewerId = userBody.id;

      await page.goto(`${WEB_URL}/dang-nhap`);
      await submitLoginForm(page, viewerUsername, viewerPassword);
      await expect(page).toHaveURL(new RegExp(`^${ADMIN_URL}/?$`));

      await page.getByRole("link", { name: "Tài khoản", exact: true }).click();
      await expect(page).toHaveURL(`${ADMIN_URL}/tai-khoan`);

      await page.getByLabel("Tìm kiếm").fill("student01");
      await expect(page.getByRole("link", { name: "student01" })).toBeVisible();

      await expect(page.getByRole("button", { name: "Tạo tài khoản" })).toHaveCount(0);
      await expect(page.getByRole("checkbox", { name: "Chọn tất cả tài khoản" })).toHaveCount(0);
      await expect(page.getByRole("button", { name: /Thao tác với/ })).toHaveCount(0);

      await page.getByRole("link", { name: "student01" }).click();
      await expect(page).toHaveURL(new RegExp(`^${ADMIN_URL}/tai-khoan/[^/]+$`));
      await expect(page.getByRole("heading", { name: /Học viên 01/ })).toBeVisible();

      await expect(page.getByRole("button", { name: "Đặt lại mật khẩu" })).toHaveCount(0);
      await expect(page.getByRole("button", { name: "Ngừng hoạt động" })).toHaveCount(0);
      await expect(page.getByRole("button", { name: "Kích hoạt lại" })).toHaveCount(0);
      await expect(page.getByRole("button", { name: "Xóa" })).toHaveCount(0);
      await expect(page.getByRole("button", { name: "Lưu thay đổi" })).toHaveCount(0);
      await expect(page.getByRole("tab", { name: "Lịch sử thay đổi" })).toHaveCount(0);
    } finally {
      if (viewerId) {
        await api.put(`/api/v1/admin/users/${viewerId}/roles`, {
          headers: { origin: ADMIN_URL, "x-xsrf-token": xsrf },
          data: { roleIds: [] },
        });
        await api.delete(`/api/v1/admin/users/${viewerId}?reason=E2E%20cleanup`, {
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

  test("thao tác hàng loạt qua web UI gửi Idempotency-Key qua proxy để ngừng hoạt động và kích hoạt lại", async ({
    page,
  }) => {
    const suffix = Date.now().toString(36);
    const prefix = `e2e.bulk.${suffix}`;
    const userA = `${prefix}.a`;
    const userB = `${prefix}.b`;
    const password = `Thi360@${suffix}B1`;

    const { api, xsrf } = await createPlatformApiContext();
    let userAId: string | undefined;
    let userBId: string | undefined;

    try {
      for (const [username, fullName] of [
        [userA, `Bulk A ${suffix}`],
        [userB, `Bulk B ${suffix}`],
      ] as const) {
        const res = await api.post("/api/v1/admin/users", {
          headers: { origin: ADMIN_URL, "x-xsrf-token": xsrf },
          data: {
            username,
            password,
            fullName,
            mustChangePassword: false,
          },
        });
        expect(res.status()).toBe(201);
        const created = (await res.json()) as { id: string };
        if (username === userA) userAId = created.id;
        else userBId = created.id;
      }

      await page.goto(`${WEB_URL}/dang-nhap`);
      await submitLoginForm(page, "platform", PLATFORM_PASSWORD);
      await expect(page).toHaveURL(new RegExp(`^${ADMIN_URL}/?$`));

      await page.getByRole("link", { name: "Tài khoản", exact: true }).click();
      await expect(page).toHaveURL(`${ADMIN_URL}/tai-khoan`);

      await page.getByLabel("Tìm kiếm").fill(prefix);
      await expect(page.getByRole("link", { name: userA })).toBeVisible();
      await expect(page.getByRole("link", { name: userB })).toBeVisible();

      // 1. Ngừng hoạt động hàng loạt qua proxy thật
      await page.getByRole("checkbox", { name: `Chọn tài khoản ${userA}` }).click();
      await page.getByRole("checkbox", { name: `Chọn tài khoản ${userB}` }).click();

      const toolbar = page.getByRole("toolbar", { name: "Thao tác với các tài khoản đã chọn" });
      await expect(toolbar).toContainText("Đã chọn 2 tài khoản");
      await toolbar.getByRole("button", { name: "Ngừng hoạt động" }).click();

      const deactivateDialog = page.getByRole("dialog");
      await expect(
        deactivateDialog.getByRole("heading", { name: "Ngừng hoạt động nhiều tài khoản" }),
      ).toBeVisible();
      await expect(
        deactivateDialog.getByText("2 / 2 tài khoản đủ điều kiện thực hiện thao tác."),
      ).toBeVisible();
      await deactivateDialog.getByLabel(/Lý do thực hiện/).fill("E2E bulk deactivate");

      const deactivateResponsePromise = page.waitForResponse(
        (response) =>
          response.url().startsWith(`${ADMIN_URL}/api/v1/admin/users/bulk-actions`) &&
          !response.url().endsWith("/preview") &&
          response.request().method() === "POST",
      );
      await deactivateDialog.getByRole("button", { name: "Ngừng hoạt động 2 tài khoản" }).click();
      const deactivateResponse = await deactivateResponsePromise;
      expect(deactivateResponse.status()).toBe(200);
      expect(deactivateResponse.request().headers()["idempotency-key"]).toBeTruthy();

      await expect(deactivateDialog).toBeHidden();
      await expect(toolbar).toBeHidden();
      await expect(page.locator("tbody tr", { hasText: userA })).toContainText("Ngừng hoạt động");
      await expect(page.locator("tbody tr", { hasText: userB })).toContainText("Ngừng hoạt động");

      // 2. Kích hoạt lại hàng loạt qua proxy thật
      await page.getByRole("checkbox", { name: `Chọn tài khoản ${userA}` }).click();
      await page.getByRole("checkbox", { name: `Chọn tài khoản ${userB}` }).click();
      await expect(toolbar).toContainText("Đã chọn 2 tài khoản");
      await toolbar.getByRole("button", { name: "Kích hoạt lại" }).click();

      const activateDialog = page.getByRole("dialog");
      await expect(
        activateDialog.getByRole("heading", { name: "Kích hoạt lại nhiều tài khoản" }),
      ).toBeVisible();
      await expect(
        activateDialog.getByText("2 / 2 tài khoản đủ điều kiện thực hiện thao tác."),
      ).toBeVisible();

      const activateResponsePromise = page.waitForResponse(
        (response) =>
          response.url().startsWith(`${ADMIN_URL}/api/v1/admin/users/bulk-actions`) &&
          !response.url().endsWith("/preview") &&
          response.request().method() === "POST",
      );
      await activateDialog.getByRole("button", { name: "Kích hoạt 2 tài khoản" }).click();
      const activateResponse = await activateResponsePromise;
      expect(activateResponse.status()).toBe(200);
      expect(activateResponse.request().headers()["idempotency-key"]).toBeTruthy();

      await expect(activateDialog).toBeHidden();
      await expect(toolbar).toBeHidden();
      await expect(page.locator("tbody tr", { hasText: userA })).toContainText("Chưa đăng nhập");
      await expect(page.locator("tbody tr", { hasText: userB })).toContainText("Chưa đăng nhập");
    } finally {
      for (const id of [userAId, userBId]) {
        if (id) {
          await api.delete(`/api/v1/admin/users/${id}?reason=E2E%20cleanup`, {
            headers: { origin: ADMIN_URL, "x-xsrf-token": xsrf },
          });
        }
      }
      await api.dispose();
    }
  });
});
