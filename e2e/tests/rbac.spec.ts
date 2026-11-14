import { expect, type Page, test } from "@playwright/test";
import { createPlatformApiContext } from "./helpers/platform";

const WEB_URL = process.env.THI360_WEB_URL ?? "http://localhost:3000";
const ADMIN_URL = process.env.THI360_ADMIN_URL ?? "http://localhost:3001";
const DEV_PASSWORD = process.env.THI360_DEV_PASSWORD ?? "Thi360@dev";
const PLATFORM_PASSWORD = process.env.THI360_DEV_PLATFORM_PASSWORD ?? "platform-dev-only";
const TEST_USER_PASSWORD = process.env.THI360_E2E_USER_PASSWORD ?? "Thi360@e2e1!";

async function submitLoginForm(page: Page, identifier: string, password: string) {
  await page.getByLabel(/Tên đăng nhập hoặc email/).fill(identifier);
  await page.getByLabel(/Mật khẩu/).fill(password);
  await page.getByRole("button", { name: "Đăng nhập" }).click();
}

async function loginAs(page: Page, identifier: string, password: string) {
  await page.goto(`${WEB_URL}/dang-nhap`);
  await submitLoginForm(page, identifier, password);
}

async function logoutFromAdmin(page: Page) {
  await page.getByRole("button", { name: "Menu tài khoản" }).click();
  await page.getByRole("menuitem", { name: "Đăng xuất" }).click();
  await expect(page).toHaveURL(new RegExp(`^${WEB_URL}/dang-nhap`));
}

async function getXsrfHeaders(page: Page): Promise<Record<string, string>> {
  const cookies = await page.context().cookies(ADMIN_URL);
  const xsrf = cookies.find((cookie) => cookie.name === "thi360_xsrf")?.value;
  return {
    Origin: ADMIN_URL,
    ...(xsrf ? { "X-XSRF-TOKEN": decodeURIComponent(xsrf) } : {}),
  };
}

async function createUserViaAdminApi(
  page: Page,
  input: {
    username: string;
    fullName: string;
    email: string;
    roleIds: string[];
  },
): Promise<{ id: string; username: string }> {
  const headers = await getXsrfHeaders(page);
  const response = await page.request.post(`${ADMIN_URL}/api/v1/admin/users`, {
    headers,
    data: {
      username: input.username,
      fullName: input.fullName,
      email: input.email,
      password: TEST_USER_PASSWORD,
      mustChangePassword: false,
      roleIds: input.roleIds,
    },
  });
  expect(response.status()).toBe(201);
  return (await response.json()) as { id: string; username: string };
}

async function findRoleIdByCode(page: Page, code: string): Promise<string> {
  const response = await page.request.get(
    `${ADMIN_URL}/api/v1/admin/roles?q=${encodeURIComponent(code)}&size=20`,
  );
  expect(response.ok()).toBe(true);
  const payload = (await response.json()) as { items: Array<{ id: string; code: string }> };
  const matched = payload.items.find((item) => item.code === code);
  if (!matched) {
    throw new Error(`Role not found for code ${code}`);
  }
  return matched.id;
}

test.describe("Phân quyền (T08 - UNI-2545)", () => {
  test.describe.serial("E2E 1 & E2E 2: Tạo vai trò Theo dõi lớp, gán user và kiểm tra Người dùng theo quyền", () => {
    const suffix = Date.now();
    const roleCode = `CLASS_WATCH_${suffix}`;
    const roleName = `Theo dõi lớp ${suffix}`;
    const watchUsername = `e2e.watch.${suffix}`;

    test("E2E 1: platform tạo vai trò Chỉ xem Lớp học → gán cho user mới → user chỉ thấy menu Lớp học và quyền CLASS.VIEW", async ({
      page,
    }) => {
      await loginAs(page, "platform", PLATFORM_PASSWORD);
      await expect(page).toHaveURL(new RegExp(`^${ADMIN_URL}/?$`));

      const { api, xsrf } = await createPlatformApiContext();
      let watchUserId: string | undefined;
      let createdRoleId: string | undefined;
      try {
        await page
          .getByRole("navigation", { name: "Menu chính" })
          .getByRole("link", { name: "Phân quyền" })
          .click();
        await expect(page).toHaveURL(new RegExp(`${ADMIN_URL}/phan-quyen\\?tab=vai-tro`));

        await page.getByRole("button", { name: "+ Tạo vai trò" }).click();
        const dialog = page.getByRole("dialog");
        await expect(dialog).toBeVisible();

        await dialog.getByLabel(/Mã vai trò/).fill(roleCode);
        await dialog.getByLabel(/Tên vai trò/).fill(roleName);
        await dialog.getByRole("checkbox", { name: "Cấp đối tượng Lớp học" }).check();
        await dialog.getByRole("button", { name: "Tạo vai trò" }).click();

        await expect(dialog).toBeHidden();
        // Danh sách vai trò phân trang 20; tìm theo mã để hàng mới không rơi sang trang sau.
        await page.getByLabel("Tìm kiếm").fill(roleCode);
        await expect(page.getByText(roleCode)).toBeVisible();

        createdRoleId = await findRoleIdByCode(page, roleCode);
        const createdUser = await createUserViaAdminApi(page, {
          username: watchUsername,
          fullName: "Người theo dõi lớp",
          email: `${watchUsername}@thi360.vn`,
          roleIds: [createdRoleId],
        });
        watchUserId = createdUser.id;

        await logoutFromAdmin(page);

        await loginAs(page, watchUsername, TEST_USER_PASSWORD);
        await expect(page).toHaveURL(new RegExp(`^${ADMIN_URL}/?$`));

        const nav = page.getByRole("navigation", { name: "Menu chính" });
        await expect(nav.getByRole("link", { name: "Bảng điều khiển" })).toBeVisible();
        await expect(nav.getByRole("link", { name: "Tài khoản" })).toBeHidden();
        await expect(nav.getByRole("link", { name: "Phân quyền" })).toBeHidden();
        await expect(nav.getByRole("link", { name: "Tùy chỉnh giao diện" })).toBeHidden();

        // T10 (Lớp học) chưa merge nên kiểm tra quyền CLASS.VIEW và không có quyền ghi qua GET /api/v1/me;
        // kiểm tra cấp màn hình Lớp học sẽ thực hiện sau khi T10 merge.
        const meResponse = await page.request.get(`${ADMIN_URL}/api/v1/me`);
        expect(meResponse.ok()).toBe(true);
        const me = (await meResponse.json()) as { access: string; permissions: string[] };
        expect(me.access).toBe("MANAGE");
        expect(me.permissions).toContain("CLASS.VIEW");
        expect(me.permissions).toEqual(["CLASS.VIEW"]);
      } finally {
        // Dọn dẹp best-effort: gỡ vai trò khỏi user rồi xóa user, sau đó xóa vai trò.
        if (watchUserId) {
          await api.put(`/api/v1/admin/users/${watchUserId}/roles`, {
            headers: { origin: ADMIN_URL, "x-xsrf-token": xsrf },
            data: { roleIds: [] },
          });
          await api.delete(`/api/v1/admin/users/${watchUserId}?reason=E2E%20cleanup`, {
            headers: { origin: ADMIN_URL, "x-xsrf-token": xsrf },
          });
        }
        if (createdRoleId) {
          await api.delete(`/api/v1/admin/roles/${createdRoleId}`, {
            headers: { origin: ADMIN_URL, "x-xsrf-token": xsrf },
          });
        }
        await api.dispose();
      }
    });

    test("E2E 2: tab Người dùng theo quyền với CLASS.VIEW hiện đúng user ở E2E 1 với nguồn Từ vai trò", async ({
      page,
    }) => {
      await loginAs(page, "platform", PLATFORM_PASSWORD);
      await expect(page).toHaveURL(new RegExp(`^${ADMIN_URL}/?$`));

      await page.goto(`${ADMIN_URL}/phan-quyen?tab=quyen`);
      await page
        .getByRole("button", { name: "Xem tài khoản đang có quyền Xem lớp học" })
        .click();

      await expect(page).toHaveURL(
        new RegExp(`${ADMIN_URL}/phan-quyen\\?tab=nguoi-dung&permission=CLASS\\.VIEW`),
      );

      await page.getByLabel("Tìm tài khoản").fill(watchUsername);
      const row = page.locator("tbody tr", { hasText: watchUsername });
      await expect(row).toBeVisible();
      await expect(row.getByText("Từ vai trò")).toBeVisible();
      await expect(row.getByText(new RegExp(`Vai trò:.*${roleName}`))).toBeVisible();
    });
  });

  test("E2E 3: Gán hàng loạt quyền trực tiếp AUDIT.VIEW cho ops.admin và student01", async ({
    page,
  }) => {
    await loginAs(page, "platform", PLATFORM_PASSWORD);
    await expect(page).toHaveURL(new RegExp(`^${ADMIN_URL}/?$`));

    // Đảm bảo ops.admin chưa có AUDIT.VIEW trực tiếp trước khi chạy E2E 3
    const headers = await getXsrfHeaders(page);
    const opsLookup = await page.request.get(`${ADMIN_URL}/api/v1/admin/users?q=ops.admin&size=10`);
    expect(opsLookup.ok()).toBe(true);
    const opsPayload = (await opsLookup.json()) as {
      items: Array<{ id: string; username: string }>;
    };
    const opsUser = opsPayload.items.find((u) => u.username === "ops.admin");
    expect(opsUser).toBeDefined();

    const opsPermsResp = await page.request.get(
      `${ADMIN_URL}/api/v1/admin/users/${opsUser!.id}/permissions`,
    );
    expect(opsPermsResp.ok()).toBe(true);
    const opsPerms = (await opsPermsResp.json()) as {
      permissions: Array<{ permissionCode: string }>;
    };
    const hadAuditDirect = opsPerms.permissions.some((p) => p.permissionCode === "AUDIT.VIEW");
    if (hadAuditDirect) {
      const clearResp = await page.request.put(
        `${ADMIN_URL}/api/v1/admin/users/${opsUser!.id}/permissions`,
        {
          headers,
          data: {
            permissionCodes: opsPerms.permissions
              .map((p) => p.permissionCode)
              .filter((code) => code !== "AUDIT.VIEW"),
          },
        },
      );
      expect(clearResp.ok()).toBe(true);
    }

    await page.goto(`${ADMIN_URL}/phan-quyen?tab=gan-hang-loat`);
    await expect(page.getByRole("heading", { name: /1\. Chọn tài khoản/ })).toBeVisible();

    const searchInput = page.getByLabel("Tìm kiếm");
    await searchInput.fill("ops.admin");
    const opsRow = page.locator("label", { hasText: "ops.admin" }).first();
    await expect(opsRow).toBeVisible();
    await opsRow.getByRole("checkbox").check();

    await searchInput.fill("student01");
    const studentRow = page.locator("label", { hasText: "student01" }).first();
    await expect(studentRow).toBeVisible();
    await studentRow.getByRole("checkbox").check();

    await expect(page.getByText(/đã chọn 2/)).toBeVisible();

    await page
      .getByRole("group", { name: "Đối tượng" })
      .getByRole("button", { name: "Quyền trực tiếp" })
      .click();
    await page.getByRole("button", { name: "+ Thêm quyền" }).click();

    const pickerDialog = page.getByRole("dialog");
    await expect(pickerDialog).toBeVisible();
    await pickerDialog.getByRole("checkbox", { name: "Cấp đối tượng Nhật ký" }).check();
    await pickerDialog.getByRole("button", { name: "Xác nhận chọn" }).click();
    await expect(pickerDialog).toBeHidden();

    await page
      .getByPlaceholder("Ví dụ: Hỗ trợ rà soát danh mục trước kỳ thi")
      .fill("E2E gán quyền xem nhật ký");

    await expect(page.getByText("Sẽ thay đổi").first()).toBeVisible();
    await expect(
      page.getByText(
        "Tài khoản không có vai trò phạm vi Quản trị nên không nhận được quyền trực tiếp.",
      ),
    ).toBeVisible();

    const applyBtn = page.getByRole("button", { name: "Áp dụng cho 1 tài khoản" });
    await expect(applyBtn).toBeEnabled();
    await applyBtn.click();

    await expect(
      page.getByText(/Đã hoàn tất áp dụng: thay đổi 1 tài khoản, không đổi 0, bỏ qua 1/),
    ).toBeVisible();

    await logoutFromAdmin(page);

    await loginAs(page, "ops.admin", DEV_PASSWORD);
    await expect(page).toHaveURL(new RegExp(`^${ADMIN_URL}/?$`));

    const nav = page.getByRole("navigation", { name: "Menu chính" });
    await expect(nav.getByRole("link", { name: "Bảng điều khiển" })).toBeVisible();
    await expect(nav.getByRole("link", { name: "Phân quyền" })).toBeHidden();

    // Màn hình Nhật ký (audit screen) chưa merge; xác nhận ops.admin nhận AUDIT.VIEW qua GET /api/v1/me ở lần đăng nhập mới;
    // kiểm tra cấp màn hình Nhật ký sẽ thực hiện sau khi màn hình audit merge.
    const opsMeResponse = await page.request.get(`${ADMIN_URL}/api/v1/me`);
    expect(opsMeResponse.ok()).toBe(true);
    const opsMe = (await opsMeResponse.json()) as { access: string; permissions: string[] };
    expect(opsMe.access).toBe("MANAGE");
    expect(opsMe.permissions).toContain("AUDIT.VIEW");
  });

  test("người chỉ có ROLE.VIEW chỉ thấy tab Vai trò và không có nút ghi", async ({ page }) => {
    const suffix = Date.now();
    const roleCode = `ROLE_VIEW_${suffix}`;
    const roleName = `Chỉ xem vai trò ${suffix}`;
    const viewerUsername = `e2e.roleview.${suffix}`;

    await loginAs(page, "platform", PLATFORM_PASSWORD);
    await expect(page).toHaveURL(new RegExp(`^${ADMIN_URL}/?$`));

    const { api, xsrf } = await createPlatformApiContext();
    let roleId: string | undefined;
    let viewerId: string | undefined;
    try {
      const roleResp = await api.post("/api/v1/admin/roles", {
        headers: { origin: ADMIN_URL, "x-xsrf-token": xsrf },
        data: {
          access: "MANAGE",
          code: roleCode,
          name: roleName,
          permissionCodes: ["ROLE.VIEW"],
        },
      });
      expect(roleResp.status()).toBe(201);
      const createdRole = (await roleResp.json()) as { id: string };
      roleId = createdRole.id;

      const userResp = await api.post("/api/v1/admin/users", {
        headers: { origin: ADMIN_URL, "x-xsrf-token": xsrf },
        data: {
          username: viewerUsername,
          password: TEST_USER_PASSWORD,
          fullName: "Chỉ xem vai trò",
          roleIds: [roleId],
          mustChangePassword: false,
        },
      });
      expect(userResp.status()).toBe(201);
      const createdUser = (await userResp.json()) as { id: string };
      viewerId = createdUser.id;

      await page.goto(`${WEB_URL}/dang-nhap`);
      await submitLoginForm(page, viewerUsername, TEST_USER_PASSWORD);
      await expect(page).toHaveURL(new RegExp(`^${ADMIN_URL}/?$`));

      const nav = page.getByRole("navigation", { name: "Menu chính" });
      await expect(nav.getByRole("link", { name: "Phân quyền" })).toBeVisible();

      // Truy cập thẳng tab không có quyền -> chuyển về tab đầu tiên được phép (?tab=vai-tro)
      await page.goto(`${ADMIN_URL}/phan-quyen?tab=quyen`);
      await expect(page).toHaveURL(new RegExp(`${ADMIN_URL}/phan-quyen\\?tab=vai-tro`));

      const tabList = page.getByRole("tablist", { name: "Các tab phân quyền" });
      await expect(tabList.getByRole("tab", { name: "Vai trò" })).toBeVisible();
      await expect(tabList.getByRole("tab", { name: "Quyền" })).toBeHidden();
      await expect(tabList.getByRole("tab", { name: "Người dùng theo quyền" })).toBeHidden();
      await expect(tabList.getByRole("tab", { name: "Gán hàng loạt" })).toBeHidden();

      await expect(page.getByRole("button", { name: "+ Tạo vai trò" })).toBeHidden();
      await expect(page.getByRole("button", { name: /Sửa vai trò/ })).toHaveCount(0);
      await expect(page.getByRole("button", { name: /Xóa vai trò/ })).toHaveCount(0);
      await expect(page.getByRole("button", { name: /Xem vai trò/ }).first()).toBeVisible();
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
});
