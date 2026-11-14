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

test.describe("Quản lý lớp học (UNI-2547)", () => {
  test("tạo lớp -> thêm học viên -> chuyển lớp -> loại học viên -> xem lịch sử trên chi tiết tài khoản", async ({
    page,
  }) => {
    const suffix = Date.now().toString(36).toUpperCase();
    const classACode = `E2E-A-${suffix}`;
    const classBCode = `E2E-B-${suffix}`;
    const classAName = `Lớp E2E A ${suffix}`;
    const classBName = `Lớp E2E B ${suffix}`;

    const { api, xsrf } = await createPlatformApiContext();
    const createdClassIds: string[] = [];

    try {
      await page.goto(`${WEB_URL}/dang-nhap`);
      await submitLoginForm(page, "platform", PLATFORM_PASSWORD);
      await expect(page).toHaveURL(new RegExp(`^${ADMIN_URL}/?$`));

      await page.getByRole("link", { name: "Lớp học", exact: true }).click();
      await expect(page).toHaveURL(`${ADMIN_URL}/lop-hoc`);
      await expect(page.getByRole("heading", { name: "Lớp học" })).toBeVisible();

      // 1. Tạo hai lớp học mới
      await page.getByRole("button", { name: "Tạo lớp" }).click();
      const createDialogA = page.getByRole("dialog");
      await createDialogA.getByLabel(/Mã lớp/).fill(classACode);
      await createDialogA.getByLabel(/Tên lớp/).fill(classAName);
      await createDialogA.getByRole("button", { name: "Tạo lớp" }).click();
      await expect(createDialogA).toBeHidden();

      await page.getByRole("button", { name: "Tạo lớp" }).click();
      const createDialogB = page.getByRole("dialog");
      await createDialogB.getByLabel(/Mã lớp/).fill(classBCode);
      await createDialogB.getByLabel(/Tên lớp/).fill(classBName);
      await createDialogB.getByRole("button", { name: "Tạo lớp" }).click();
      await expect(createDialogB).toBeHidden();

      // Ghi nhận ID lớp mới để dọn dẹp sau test
      const listRes = await api.get(`${ADMIN_URL}/api/v1/admin/classes?q=${suffix}&size=20`);
      if (listRes.ok()) {
        const listJson = (await listRes.json()) as { items?: Array<{ id: string; code: string }> };
        for (const item of listJson.items ?? []) {
          if (item.code === classACode || item.code === classBCode) {
            createdClassIds.push(item.id);
          }
        }
      }

      // 2. Mở chi tiết lớp A và thêm student01, student02
      await page.getByLabel("Tìm kiếm").fill(classACode);
      const classALink = page.getByRole("link", { name: classACode });
      await expect(classALink).toBeVisible();
      await classALink.click();

      await expect(page).toHaveURL(new RegExp(`^${ADMIN_URL}/lop-hoc/[^/]+$`));
      await expect(page.getByRole("heading", { name: classAName })).toBeVisible();
      await expect(page.getByText(/Lịch sử thành viên/i)).toBeHidden();

      await page.getByRole("button", { name: "Thêm học viên" }).click();
      const addDialog = page.getByRole("dialog");
      await addDialog.getByLabel("Tìm học viên").fill("student0");
      await addDialog.getByRole("checkbox", { name: /student01/ }).check();
      await addDialog.getByRole("checkbox", { name: /student02/ }).check();
      await addDialog.getByRole("button", { name: /Thêm 2 học viên/ }).click();
      await expect(addDialog).toBeHidden();

      await expect(page.getByRole("link", { name: "student01" })).toBeVisible();
      await expect(page.getByRole("link", { name: "student02" })).toBeVisible();

      // 3. Chuyển student01 sang lớp B
      await page.getByRole("button", { name: "Chuyển student01 sang lớp khác" }).click();
      const transferDialog = page.getByRole("dialog");
      await transferDialog.getByRole("combobox", { name: /Lớp đích/ }).click();
      await page.getByRole("option", { name: new RegExp(`^${classBCode}`) }).click();
      await transferDialog.getByLabel(/Lý do chuyển lớp/).fill("E2E chuyển lớp");
      await transferDialog.getByRole("button", { name: "Chuyển lớp" }).click();
      await expect(transferDialog).toBeHidden();
      await expect(page.getByRole("link", { name: "student01" })).toBeHidden();

      // 4. Loại student02 khỏi lớp A
      await page.getByRole("button", { name: "Loại student02 khỏi lớp" }).click();
      const removeDialog = page.getByRole("dialog");
      await removeDialog.getByLabel(/Lý do loại khỏi lớp/).fill("E2E loại khỏi lớp");
      await removeDialog.getByRole("button", { name: "Loại khỏi lớp" }).click();
      await expect(removeDialog).toBeHidden();
      await expect(page.getByRole("link", { name: "student02" })).toBeHidden();

      // 5. Vào chi tiết tài khoản student01 -> tab Lớp học hiện lớp hiện tại và lịch sử chuyển lớp
      await page.getByRole("link", { name: "Tài khoản", exact: true }).click();
      await expect(page).toHaveURL(`${ADMIN_URL}/tai-khoan`);
      await page.getByLabel("Tìm kiếm").fill("student01");
      await page.getByRole("link", { name: "student01" }).click();

      await page.getByRole("tab", { name: "Lớp học" }).click();
      await expect(page.getByRole("heading", { name: /Lớp học hiện tại/ })).toBeVisible();
      await expect(page.getByRole("link", { name: classBCode })).toBeVisible();
      await expect(page.getByRole("heading", { name: /Lịch sử tham gia và chuyển lớp/ })).toBeVisible();
      await expect(page.getByText("Chuyển đến")).toBeVisible();
      await expect(page.getByText("Chuyển đi")).toBeVisible();
      await expect(page.getByText("E2E chuyển lớp").first()).toBeVisible();
    } finally {
      for (const classId of createdClassIds) {
        await api.delete(`${ADMIN_URL}/api/v1/admin/classes/${classId}?reason=E2E+cleanup`, {
          headers: {
            origin: ADMIN_URL,
            "x-xsrf-token": xsrf,
          },
        });
      }
      await api.dispose();
    }
  });
});
