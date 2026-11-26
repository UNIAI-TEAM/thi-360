import { expect, test, type Browser, type BrowserContext, type Page } from "@playwright/test";

import { adminApi, ADMIN_URL, loginAdmin, RUN_ID, WEB_URL, type AdminApi } from "./helpers/editor-qa";
import { createPlatformApiContext } from "./helpers/platform";
import {
  applyMatrixCookies,
  expectMatrixClean,
  launchMatrixBrowser,
  matrixCells,
  openMatrixContext,
  type MatrixCell,
} from "./helpers/visual-matrix";

/**
 * FA-2 (UNI-2739): Web admin Khuyen mai / Voucher (`/promotions`, `/promotions/new`, `/promotions/[id]`, `/promotions/[id]/edit`).
 */

const TAG = `FA2-${RUN_ID}`.toUpperCase();
const TEST_USER_PASSWORD = process.env.THI360_E2E_USER_PASSWORD ?? "Thi360@e2e1!";
const admin = (path: string) => `${ADMIN_URL}/api/v1/admin${path}`;

interface CreatedPromotion {
  id: string;
  revision: number;
  name: string;
  code?: string;
}

async function createPromotionViaApi(
  { api, headers }: AdminApi,
  payload: Record<string, unknown>,
): Promise<CreatedPromotion> {
  const res = await api.post(admin("/promotions"), {
    headers: { ...headers, "idempotency-key": crypto.randomUUID() },
    data: {
      kind: "AUTO",
      name: `${TAG}-PROMO`,
      discountType: "PERCENT",
      discountValue: 15,
      ...(payload.discountType === "AMOUNT" ? {} : { maxDiscountVnd: 50000 }),
      startsAt: new Date(Date.now() + 60 * 60 * 1000).toISOString(),
      endsAt: new Date(Date.now() + 7 * 24 * 60 * 60 * 1000).toISOString(),
      scope: "ALL",
      status: "ACTIVE",
      ...payload,
    },
  });
  expect(res.status(), await res.text()).toBe(201);
  return (await res.json()) as CreatedPromotion;
}

async function cleanupPromotion({ api, headers }: AdminApi, id: string | undefined): Promise<void> {
  if (!id) return;
  const getRes = await api.get(admin(`/promotions/${id}`));
  if (!getRes.ok()) return;
  const detail = (await getRes.json()) as { usedCount: number; status: string; revision: number };
  if (detail.usedCount === 0) {
    await api.delete(admin(`/promotions/${id}`), { headers });
  } else if (detail.status === "ACTIVE") {
    await api.post(admin(`/promotions/${id}/stop`), {
      headers,
      data: { expectedRevision: detail.revision },
    });
  }
}

test.describe("FA-2: Web admin Khuyen mai", () => {
  test("tạo voucher: điều hướng menu, gợi ý mã, lưu voucher, kiểm tra trong danh sách", async ({ context, page }) => {
    await loginAdmin(context, "vi");
    const api = await adminApi();
    let createdId: string | undefined;

    try {
      await page.goto(`${ADMIN_URL}/promotions`);

      // Menu chính của platform admin hiện đầy đủ các mục Thương mại (Khuyến mãi, Bộ đề, Đơn hàng) và Lượt thi.
      const menu = page.getByRole("navigation", { name: "Menu chính" });
      for (const visible of ["Khuyến mãi", "Bộ đề", "Đơn hàng", "Lượt thi"]) {
        await expect(menu.getByRole("link", { name: visible })).toBeVisible();
      }

      await page.getByRole("radio", { name: "Mã ưu đãi" }).click();
      await page.locator("main").getByRole("link", { name: "Tạo voucher" }).first().click();
      await expect(page).toHaveURL(/\/promotions\/new\?kind=VOUCHER/);

      await page.getByLabel(/Tên khuyến mãi/).fill(`${TAG}-VOUCHER`);
      await page.getByRole("button", { name: "Gợi ý mã" }).click();
      const codeInput = page.getByLabel(/Mã ưu đãi/);
      await expect(codeInput).toHaveValue(/^[A-Z0-9]{6,12}$/);
      const generatedCode = await codeInput.inputValue();

      await page.getByLabel(/Phần trăm giảm/).fill("20");

      const [createRes] = await Promise.all([
        page.waitForResponse(
          (res) => res.url().endsWith("/api/v1/admin/promotions") && res.request().method() === "POST",
        ),
        page.getByRole("button", { name: "Tạo khuyến mãi" }).click(),
      ]);
      expect(createRes.status()).toBe(201);
      const created = (await createRes.json()) as { id: string };
      createdId = created.id;

      await expect(page).toHaveURL(new RegExp(`/promotions/${createdId}$`));
      await expect(page.getByRole("heading", { name: `${TAG}-VOUCHER` })).toBeVisible();
      await expect(page.getByText(generatedCode).first()).toBeVisible();

      // Quay lai danh sach, loc Ma uu dai -> voucher vua tao xuat hien
      await page.getByRole("link", { name: "Quay lại danh sách" }).click();
      await page.getByRole("radio", { name: "Mã ưu đãi" }).click();
      await expect(page.getByRole("link", { name: `${TAG}-VOUCHER` })).toBeVisible();
    } finally {
      await cleanupPromotion(api, createdId);
      await api.api.dispose();
    }
  });

  test("vai trò tùy chỉnh chỉ có PROMOTION.VIEW: xem được danh sách & chi tiết, ẩn CTA sửa/xóa, chặn /new và /edit", async ({
    page,
  }) => {
    const suffix = RUN_ID;
    const roleCode = `FA2_PROMO_VIEW_${suffix}`.toUpperCase();
    const username = `fa2.view.${suffix}`.toLowerCase();
    const { api, xsrf } = await createPlatformApiContext();
    const headers = { origin: ADMIN_URL, "x-xsrf-token": xsrf };
    let roleId: string | undefined;
    let userId: string | undefined;
    let promoId: string | undefined;

    try {
      const created = await createPromotionViaApi({ api, headers }, { name: `${TAG}-VIEWONLY` });
      promoId = created.id;

      const role = await api.post(admin("/roles"), {
        headers,
        data: {
          access: "MANAGE",
          code: roleCode,
          name: `FA2 view promo ${suffix}`,
          permissionCodes: ["PROMOTION.VIEW"],
        },
      });
      expect(role.status(), await role.text()).toBe(201);
      roleId = ((await role.json()) as { id: string }).id;

      const user = await api.post(admin("/users"), {
        headers,
        data: {
          username,
          password: TEST_USER_PASSWORD,
          fullName: "FA2 View Only",
          roleIds: [roleId],
          mustChangePassword: false,
        },
      });
      expect(user.status(), await user.text()).toBe(201);
      userId = ((await user.json()) as { id: string }).id;

      await page.goto(`${WEB_URL}/login`);
      await page.getByLabel(/Tên đăng nhập hoặc email/).fill(username);
      await page.getByLabel(/Mật khẩu/).fill(TEST_USER_PASSWORD);
      await page.getByRole("button", { name: "Đăng nhập" }).click();
      await expect(page).toHaveURL(new RegExp(`^${ADMIN_URL}/?$`));

      const menu = page.getByRole("navigation", { name: "Menu chính" });
      await expect(menu.getByRole("link", { name: "Khuyến mãi" })).toBeVisible();

      await page.goto(`${ADMIN_URL}/promotions`);
      await expect(page.getByRole("link", { name: `${TAG}-VIEWONLY` })).toBeVisible();
      await expect(page.getByRole("link", { name: "Tạo khuyến mãi" })).toHaveCount(0);
      await expect(page.getByRole("button", { name: /Chỉnh sửa/ })).toHaveCount(0);

      await page.goto(`${ADMIN_URL}/promotions/${promoId}`);
      await expect(page.getByRole("heading", { name: `${TAG}-VIEWONLY` })).toBeVisible();
      await expect(page.getByRole("link", { name: "Chỉnh sửa" })).toHaveCount(0);
      await expect(page.getByRole("button", { name: "Dừng" })).toHaveCount(0);
      await expect(page.getByRole("button", { name: "Xóa" })).toHaveCount(0);

      // Chan route /new va /edit
      await page.goto(`${ADMIN_URL}/promotions/new`);
      await expect(page.getByRole("button", { name: "Tạo khuyến mãi" })).toHaveCount(0);
      await expect(page.getByRole("heading", { name: "Bạn không có quyền vào trang này" })).toBeVisible();
      await page.goto(`${ADMIN_URL}/promotions/${promoId}/edit`);
      await expect(page.getByRole("button", { name: "Lưu thay đổi" })).toHaveCount(0);
      await expect(page.getByRole("heading", { name: "Bạn không có quyền vào trang này" })).toBeVisible();
    } finally {
      await cleanupPromotion({ api, headers }, promoId);
      if (userId) {
        await api.put(admin(`/users/${userId}/roles`), { headers, data: { roleIds: [] } });
        await api.delete(admin(`/users/${userId}?reason=E2E%20cleanup`), { headers });
      }
      if (roleId) await api.delete(admin(`/roles/${roleId}`), { headers });
      await api.dispose();
    }
  });

  test("thiếu quyền xem: không thấy menu Khuyến mãi và bị chặn khi vào /promotions", async ({ page }) => {
    const suffix = RUN_ID;
    const roleCode = `FA2_NO_PROMO_${suffix}`.toUpperCase();
    const username = `fa2.noview.${suffix}`.toLowerCase();
    const { api, xsrf } = await createPlatformApiContext();
    const headers = { origin: ADMIN_URL, "x-xsrf-token": xsrf };
    let roleId: string | undefined;
    let userId: string | undefined;

    try {
      const role = await api.post(admin("/roles"), {
        headers,
        data: {
          access: "MANAGE",
          code: roleCode,
          name: `FA2 no promo ${suffix}`,
          permissionCodes: ["USER.VIEW"],
        },
      });
      expect(role.status(), await role.text()).toBe(201);
      roleId = ((await role.json()) as { id: string }).id;

      const user = await api.post(admin("/users"), {
        headers,
        data: {
          username,
          password: TEST_USER_PASSWORD,
          fullName: "FA2 No View",
          roleIds: [roleId],
          mustChangePassword: false,
        },
      });
      expect(user.status(), await user.text()).toBe(201);
      userId = ((await user.json()) as { id: string }).id;

      await page.goto(`${WEB_URL}/login`);
      await page.getByLabel(/Tên đăng nhập hoặc email/).fill(username);
      await page.getByLabel(/Mật khẩu/).fill(TEST_USER_PASSWORD);
      await page.getByRole("button", { name: "Đăng nhập" }).click();
      await expect(page).toHaveURL(new RegExp(`^${ADMIN_URL}/?$`));

      const menu = page.getByRole("navigation", { name: "Menu chính" });
      await expect(menu.getByRole("link", { name: "Khuyến mãi" })).toHaveCount(0);

      await page.goto(`${ADMIN_URL}/promotions`);
      await expect(page.getByRole("radiogroup", { name: "Lọc theo loại khuyến mãi" })).toHaveCount(0);
      await expect(page.getByRole("heading", { name: "Bạn không có quyền vào trang này" })).toBeVisible();
    } finally {
      if (userId) {
        await api.put(admin(`/users/${userId}/roles`), { headers, data: { roleIds: [] } });
        await api.delete(admin(`/users/${userId}?reason=E2E%20cleanup`), { headers });
      }
      if (roleId) await api.delete(admin(`/roles/${roleId}`), { headers });
      await api.dispose();
    }
  });

  test("412 khi hai người cùng sửa: hiện xung đột, tải bản mới nhất và lưu thành công", async ({
    context,
    page,
  }) => {
    await loginAdmin(context, "vi");
    const api = await adminApi();
    let promoId: string | undefined;

    try {
      const created = await createPromotionViaApi(api, { name: `${TAG}-CONFLICT` });
      promoId = created.id;

      await page.goto(`${ADMIN_URL}/promotions/${promoId}/edit`);
      const nameInput = page.getByLabel(/Tên khuyến mãi/);
      await expect(nameInput).toHaveValue(`${TAG}-CONFLICT`);

      // Nguoi thu 2 sua bang API truoc
      const concurrentPatch = await api.api.patch(admin(`/promotions/${promoId}`), {
        headers: api.headers,
        data: { expectedRevision: created.revision, name: `${TAG}-CONFLICT-V2` },
      });
      expect(concurrentPatch.status(), await concurrentPatch.text()).toBe(200);

      // Nguoi thu 1 luu tren phien ban cu -> 412
      await nameInput.fill(`${TAG}-CONFLICT-STALE`);
      await page.getByRole("button", { name: "Lưu thay đổi" }).click();
      await expect(page.getByText("Xung đột phiên bản (412)")).toBeVisible();

      await page.getByRole("button", { name: "Tải bản mới nhất" }).click();
      await expect(nameInput).toHaveValue(`${TAG}-CONFLICT-V2`);

      await nameInput.fill(`${TAG}-CONFLICT-FINAL`);
      await page.getByRole("button", { name: "Lưu thay đổi" }).click();
      await expect(page).toHaveURL(new RegExp(`/promotions/${promoId}$`));
      await expect(page.getByRole("heading", { name: `${TAG}-CONFLICT-FINAL` })).toBeVisible();
    } finally {
      await cleanupPromotion(api, promoId);
      await api.api.dispose();
    }
  });
});

test.describe("FA-2: ảnh màn hình khuyến mãi (ma trận 2 khổ x 2 nền x 2 ngôn ngữ)", () => {
  test.setTimeout(90_000);

  let browser: Browser;
  let autoPromoId: string;
  let promoId: string;
  let sessionCookies: Awaited<ReturnType<BrowserContext["cookies"]>>;

  test.beforeAll(async () => {
    browser = await launchMatrixBrowser();
    const bootstrap = await browser.newContext();
    await loginAdmin(bootstrap, "vi");
    sessionCookies = await bootstrap.cookies();
    await bootstrap.close();

    const api = await adminApi();
    const createdAuto = await createPromotionViaApi(api, {
      kind: "AUTO",
      name: `${TAG}-AUTO-MATRIX`,
      description: `${TAG} auto matrix fixture`,
      discountType: "AMOUNT",
      discountValue: 26000,
    });
    autoPromoId = createdAuto.id;

    const created = await createPromotionViaApi(api, {
      kind: "VOUCHER",
      code: `${TAG}SHOT`.slice(0, 20),
      name: `${TAG}-MATRIX`,
      description: `${TAG} matrix fixture`,
    });
    promoId = created.id;
    await api.api.dispose();
  });

  test.afterAll(async () => {
    const api = await adminApi();
    await cleanupPromotion(api, autoPromoId);
    await cleanupPromotion(api, promoId);
    await api.api.dispose();
    await browser.close();
  });

  async function openCell(cell: MatrixCell): Promise<{ context: BrowserContext; page: Page }> {
    const context = await openMatrixContext(browser, cell, ADMIN_URL);
    await context.addCookies(sessionCookies);
    await applyMatrixCookies(context, cell, ADMIN_URL);
    return { context, page: await context.newPage() };
  }

  for (const cell of matrixCells()) {
    test(`ảnh màn: ${cell.width}px ${cell.mode} ${cell.lang}`, async () => {
      const { context, page } = await openCell(cell);
      try {
        // 1. List (danh sach gop)
        await page.goto(`${ADMIN_URL}/promotions`);
        await expect(
          page.getByRole("heading", { name: cell.lang === "vi" ? "Khuyến mãi" : "Promotions" }),
        ).toBeVisible();
        await expect(page.getByRole("link", { name: `${TAG}-AUTO-MATRIX` })).toBeVisible();
        await expectMatrixClean(page, "promotions-list", cell, {
          elementOverflow: { scope: "main", allowAccessibleScroller: false },
        });

        // 2. Create
        await page.goto(`${ADMIN_URL}/promotions/new`);
        await expect(
          page.getByRole("heading", {
            name: cell.lang === "vi" ? "Tạo khuyến mãi" : "Create promotion",
          }),
        ).toBeVisible();
        await expectMatrixClean(page, "promotions-new", cell);

        // 3. Detail
        await page.goto(`${ADMIN_URL}/promotions/${promoId}`);
        await expect(page.getByRole("heading", { name: `${TAG}-MATRIX` })).toBeVisible();
        await expectMatrixClean(page, "promotions-detail", cell);

        // 4. Edit
        await page.goto(`${ADMIN_URL}/promotions/${promoId}/edit`);
        await expect(
          page.getByRole("heading", {
            name: cell.lang === "vi" ? "Chỉnh sửa khuyến mãi" : "Edit promotion",
          }),
        ).toBeVisible();
        await expect(page.getByLabel(/Tên khuyến mãi|Promotion name/)).toHaveValue(`${TAG}-MATRIX`);
        await expectMatrixClean(page, "promotions-edit", cell);
      } finally {
        await context.close();
      }
    });
  }
});
