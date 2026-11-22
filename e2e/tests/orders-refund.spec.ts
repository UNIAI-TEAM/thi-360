import { expect, test, type APIRequestContext, type Browser, type BrowserContext, type Request } from "@playwright/test";

import { adminApi, ADMIN_URL, loginAdmin, RUN_ID, WEB_URL } from "./helpers/editor-qa";
import { ACCOUNT_PASSWORD, createVerifiedAccount } from "./helpers/mail";
import { createPlatformApiContext } from "./helpers/platform";
import { allExams, publishedExamSet, waitForPublishedExamSet, type PublishedExam } from "./helpers/published-exam-set";
import { applyMatrixCookies, expectMatrixClean, launchMatrixBrowser, matrixCells, openMatrixContext } from "./helpers/visual-matrix";

let sharedExam: PublishedExam;
let sharedExamId = "";
let seq = 0;

function pickPaidExam(): PublishedExam {
  const set = publishedExamSet();
  const exam = allExams().find(
    (item) => item.code !== set.discontinuedExam && item.code !== set.voucherExam && item.listing.priceVnd > 0,
  );
  if (!exam) throw new Error("No suitable paid published exam found in published-exam-set.json");
  return exam;
}

async function resolveAdminExamId(api: APIRequestContext, code: string): Promise<string> {
  const res = await api.get(`${ADMIN_URL}/api/v1/admin/exams`, { params: { q: code, page: "0", size: "50" } });
  expect(res.ok(), `GET /admin/exams?q=${code}: ${res.status()}`).toBe(true);
  const body = (await res.json()) as { items: Array<{ id: string; code: string }> };
  const match = body.items.find((item) => item.code === code);
  expect(match, `Missing exam with code ${code}`).toBeDefined();
  return match!.id;
}

async function loginAsCredentials(context: BrowserContext, identifier: string, password: string, locale: "vi" | "en" = "vi") {
  const res = await context.request.post(`${WEB_URL}/api/v1/auth/login`, {
    headers: { origin: WEB_URL },
    data: { identifier, password },
  });
  expect(res.ok(), `login ${identifier}: ${res.status()}`).toBe(true);
  await context.addCookies([{ name: "thi360_locale", value: locale, url: ADMIN_URL }]);
}

async function createPaidOrderViaCheckout(browser: Browser, cookies: Awaited<ReturnType<typeof createVerifiedAccount>>["cookies"], exam: PublishedExam): Promise<string> {
  const ctx = await browser.newContext();
  try {
    await ctx.addCookies(cookies);
    const page = await ctx.newPage();
    await page.goto(`${WEB_URL}/checkout/${encodeURIComponent(exam.productCode)}`);
    await page.getByRole("button", { name: "Thanh toán qua VNPAY" }).click();
    await page.waitForURL(/\/mock-vnpay/);
    await page.locator('a[data-mock-action="success"]').click();
    await page.waitForURL(/\/checkout\/result/);
    await expect(page.getByRole("heading", { level: 1, name: "Đã cấp bài thi" })).toBeVisible();

    const urlOrder = new URL(page.url()).searchParams.get("order");
    if (urlOrder) return urlOrder;

    const orderLink = page.locator('main a[href^="/account/orders/"]').first();
    await expect(orderLink).toBeVisible();
    const href = await orderLink.getAttribute("href");
    const match = href ? /\/account\/orders\/([^/?#]+)/.exec(href) : null;
    expect(match?.[1], `Could not extract order code from href=${href}`).toBeTruthy();
    return decodeURIComponent(match![1]!);
  } finally {
    await ctx.close();
  }
}

async function createCustomAdmin(permissionCodes: string[]) {
  const { api, headers } = await adminApi();
  seq += 1;
  const suffix = `${RUN_ID}_${seq}`.toUpperCase().replace(/[^A-Z0-9_]/g, "");
  const roleCode = `FA3_ROLE_${suffix}`.slice(0, 40);
  const username = `fa3.${RUN_ID}.${seq}`.toLowerCase().slice(0, 30);

  let roleId = "";
  let userId = "";

  try {
    const roleRes = await api.post(`${ADMIN_URL}/api/v1/admin/roles`, {
      headers,
      data: {
        access: "MANAGE",
        code: roleCode,
        name: `FA3 Custom Role ${seq}`,
        permissionCodes,
        status: "ACTIVE",
      },
    });
    expect(roleRes.status(), `create role ${roleCode}: ${await roleRes.text()}`).toBe(201);
    roleId = ((await roleRes.json()) as { id: string }).id;

    const userRes = await api.post(`${ADMIN_URL}/api/v1/admin/users`, {
      headers,
      data: {
        username,
        fullName: `FA3 Tester ${seq}`,
        password: ACCOUNT_PASSWORD,
        mustChangePassword: false,
        roleIds: [roleId],
      },
    });
    expect(userRes.status(), `create user ${username}: ${await userRes.text()}`).toBe(201);
    userId = ((await userRes.json()) as { id: string }).id;
  } catch (err) {
    if (userId) {
      await api.delete(`${ADMIN_URL}/api/v1/admin/users/${userId}`, { headers }).catch(() => {});
    }
    if (roleId) {
      await api.delete(`${ADMIN_URL}/api/v1/admin/roles/${roleId}`, { headers }).catch(() => {});
    }
    await api.dispose().catch(() => {});
    throw err;
  }

  return {
    username,
    cleanup: async () => {
      try {
        if (userId) {
          await api.delete(`${ADMIN_URL}/api/v1/admin/users/${userId}`, { headers }).catch(() => {});
        }
        if (roleId) {
          await api.delete(`${ADMIN_URL}/api/v1/admin/roles/${roleId}`, { headers }).catch(() => {});
        }
      } finally {
        await api.dispose();
      }
    },
  };
}

test.describe("Web Admin Orders, Buyers & Refund (FA-3)", () => {
  test.beforeAll(async () => {
    const { api } = await createPlatformApiContext();
    try {
      await waitForPublishedExamSet(api);
      sharedExam = pickPaidExam();
      sharedExamId = await resolveAdminExamId(api, sharedExam.code);
    } finally {
      await api.dispose();
    }
  });

  test("AC-5, AC-6: một tài khoản mua hai đơn chỉ tính 1 người mua và điều hướng từ bài thi", async ({
    browser,
  }) => {
    const { account, cookies } = await createVerifiedAccount("fa3.two");
    const order1 = await createPaidOrderViaCheckout(browser, cookies, sharedExam);
    const order2 = await createPaidOrderViaCheckout(browser, cookies, sharedExam);

    const adminCtx = await browser.newContext();
    try {
      await loginAdmin(adminCtx, "vi");
      const page = await adminCtx.newPage();

      await page.goto(`${ADMIN_URL}/exams/${encodeURIComponent(sharedExamId)}/listing`);
      const buyersLink = page.getByRole("link", { name: /Người mua/ });
      await expect(buyersLink).toHaveAttribute("href", `/orders/buyers?exam=${sharedExamId}`);
      await buyersLink.click();

      await page.waitForURL(new RegExp(`/orders/buyers\\?exam=${sharedExamId}`));
      await expect(page.getByText(account.username, { exact: true })).toBeVisible();
      const row = page.locator("tr", { hasText: account.username });
      await expect(row.getByText("2 đơn đã trả")).toBeVisible();
      await expect(row.getByRole("link", { name: order1, exact: true })).toBeVisible();
      await expect(row.getByRole("link", { name: order2, exact: true })).toBeVisible();
    } finally {
      await adminCtx.close();
    }
  });

  test("AC-7, AC-9, AC-10, AC-20: kiểm soát quyền RBAC thiếu quyền xem và vai trò tùy chỉnh chỉ có ORDER.VIEW", async ({
    browser,
  }) => {
    const { cookies } = await createVerifiedAccount("fa3.rbac");
    const orderCode = await createPaidOrderViaCheckout(browser, cookies, sharedExam);

    const examOnlyAdmin = await createCustomAdmin(["EXAM.VIEW"]);
    const orderViewOnlyAdmin = await createCustomAdmin(["ORDER.VIEW"]);

    try {
      // AC-7 + AC-6 negative: EXAM.VIEW only cannot access /orders and does not see sidebar or Buyers link
      const examCtx = await browser.newContext();
      try {
        await loginAsCredentials(examCtx, examOnlyAdmin.username, ACCOUNT_PASSWORD, "vi");
        const page = await examCtx.newPage();
        await page.goto(`${ADMIN_URL}/exams/${encodeURIComponent(sharedExamId)}/listing`);
        await expect(page.getByRole("form", { name: "Thông tin bán" })).toBeVisible();
        const menu = page.getByRole("navigation", { name: "Menu chính" });
        await expect(menu.getByRole("link", { name: "Bài thi" })).toBeVisible();
        await expect(menu.getByRole("link", { name: "Đơn hàng" })).toHaveCount(0);
        await expect(page.getByRole("link", { name: /Người mua/ })).toHaveCount(0);

        for (const forbiddenRoute of [
          `${ADMIN_URL}/orders`,
          `${ADMIN_URL}/orders/${encodeURIComponent(orderCode)}`,
          `${ADMIN_URL}/orders/buyers?exam=${encodeURIComponent(sharedExamId)}`,
        ]) {
          await page.goto(forbiddenRoute);
          await expect(
            page.getByRole("heading", { name: "Bạn không có quyền vào trang này" }),
          ).toBeVisible();
          await expect(page.getByText(orderCode)).toHaveCount(0);
          await expect(page.locator("table")).toHaveCount(0);
        }
      } finally {
        await examCtx.close();
      }

      // AC-9 + AC-10 + AC-20: ORDER.VIEW only sees Orders sidebar link, opens detail without EXAM.VIEW, sees 0 action buttons
      const orderCtx = await browser.newContext();
      try {
        await loginAsCredentials(orderCtx, orderViewOnlyAdmin.username, ACCOUNT_PASSWORD, "vi");
        const page = await orderCtx.newPage();
        await page.goto(`${ADMIN_URL}/orders`);
        await expect(
          page.getByRole("navigation", { name: "Menu chính" }).getByRole("link", { name: "Đơn hàng" }),
        ).toBeVisible();
        await expect(page.getByRole("link", { name: orderCode, exact: true })).toBeVisible();

        await page.getByRole("link", { name: orderCode, exact: true }).click();
        await page.waitForURL(new RegExp(`/orders/${encodeURIComponent(orderCode)}`));
        await expect(page.getByRole("heading", { name: new RegExp(orderCode) })).toBeVisible();
        await expect(page.getByRole("button", { name: "Hoàn tiền" })).toHaveCount(0);
        await expect(page.getByRole("button", { name: "Tiếp tục cấp quyền" })).toHaveCount(0);
        await expect(page.getByRole("button", { name: "Xử lý tiền về muộn" })).toHaveCount(0);
        await expect(page.getByRole("button", { name: "Tra cứu lại" })).toHaveCount(0);

        // Non-vacuous AC-10 check on an eligible PAID order without ORDER.RECOVER
        const detailPattern = `**/api/v1/admin/orders/${encodeURIComponent(orderCode)}`;
        await page.route(detailPattern, async (route) => {
          const response = await route.fetch();
          const payload = (await response.json()) as Record<string, unknown> & {
            payments?: Array<Record<string, unknown>>;
          };
          const payments = Array.isArray(payload.payments)
            ? payload.payments.map((p, idx) =>
                idx === 0
                  ? {
                      ...p,
                      state: "SUCCEEDED",
                      duplicate: false,
                      refundedAmountVnd: 0,
                      refundableAmountVnd: p.amountVnd ?? payload.amountDueVnd ?? 100000,
                      refunds: [],
                    }
                  : p,
              )
            : [];
          await route.fulfill({
            response,
            json: {
              ...payload,
              state: "PAID",
              paymentStatus: "SUCCEEDED",
              refundStatus: "NONE",
              grants: [],
              payments,
            },
          });
        });
        try {
          await page.reload();
          await expect(page.getByRole("heading", { name: new RegExp(orderCode) })).toBeVisible();
          await expect(page.getByText("Đã thu tiền · Chờ cấp quyền").first()).toBeVisible();
          await expect(page.getByText("Đã thu tiền nhưng chưa cấp quyền")).toBeVisible();
          await expect(page.getByRole("button", { name: "Tiếp tục cấp quyền" })).toHaveCount(0);
        } finally {
          await page.unroute(detailPattern);
        }
      } finally {
        await orderCtx.close();
      }
    } finally {
      await examOnlyAdmin.cleanup();
      await orderViewOnlyAdmin.cleanup();
    }
  });

  test("AC-8, AC-16: hoàn tiền qua API thành công và bấm hoàn hai lần chỉ gửi 1 request với Idempotency-Key", async ({
    browser,
  }) => {
    const { cookies } = await createVerifiedAccount("fa3.ref");
    const orderCode = await createPaidOrderViaCheckout(browser, cookies, sharedExam);

    const adminCtx = await browser.newContext();
    try {
      await loginAdmin(adminCtx, "vi");
      const page = await adminCtx.newPage();

      const refundRequests: Request[] = [];
      page.on("request", (req) => {
        if (req.method() === "POST" && /\/api\/v1\/admin\/orders\/.+\/payments\/.+\/refunds$/.test(req.url())) {
          refundRequests.push(req);
        }
      });

      await page.goto(`${ADMIN_URL}/orders/${encodeURIComponent(orderCode)}`);
      await expect(page.getByRole("heading", { name: new RegExp(orderCode) })).toBeVisible();

      await page.getByRole("button", { name: "Hoàn tiền" }).first().click();
      const dialog = page.getByRole("dialog");
      await expect(dialog).toBeVisible();

      await dialog.getByRole("checkbox").check();
      await dialog.getByLabel(/Lý do hoàn tiền/).fill("FA3-E2E hoan tien qua API");

      const submitBtn = dialog.getByRole("button", { name: "Gửi yêu cầu hoàn tiền" });
      await submitBtn.dblclick();

      await expect(dialog).toHaveCount(0);
      expect(refundRequests).toHaveLength(1);
      expect(refundRequests[0]!.headers()["idempotency-key"]).toBeTruthy();

      await expect(page.getByText("Đã hoàn toàn bộ").first()).toBeVisible();
    } finally {
      await adminCtx.close();
    }
  });

  test("AC-21: ảnh màn hình, tương phản, lẫn ngôn ngữ và chống tràn ngang 8 ô cho các màn đơn hàng", async ({
    browser: runnerBrowser,
  }) => {
    const { cookies } = await createVerifiedAccount("fa3.vis");
    const orderCode = await createPaidOrderViaCheckout(runnerBrowser, cookies, sharedExam);

    const matrixBrowser = await launchMatrixBrowser();
    try {
      for (const cell of matrixCells()) {
        const ctx = await openMatrixContext(matrixBrowser, cell, ADMIN_URL);
        try {
          await loginAdmin(ctx, cell.lang);
          await applyMatrixCookies(ctx, cell, ADMIN_URL);
          const page = await ctx.newPage();

          await page.goto(`${ADMIN_URL}/orders`);
          await expect(page.getByRole("link", { name: orderCode, exact: true })).toBeVisible();
          await expectMatrixClean(page, "orders-list", cell, {
            elementOverflow: { scope: "main", allowAccessibleScroller: true },
          });

          await page.goto(`${ADMIN_URL}/orders/${encodeURIComponent(orderCode)}`);
          await expect(page.getByRole("heading", { name: new RegExp(orderCode) })).toBeVisible();
          await expectMatrixClean(page, "orders-detail", cell, {
            elementOverflow: { scope: "main", allowAccessibleScroller: true },
          });

          await page.goto(`${ADMIN_URL}/orders/buyers?exam=${encodeURIComponent(sharedExamId)}`);
          await expect(page.locator("table, [role='list']").first()).toBeVisible();
          await expectMatrixClean(page, "orders-buyers", cell, {
            elementOverflow: { scope: "main", allowAccessibleScroller: true },
          });

          await page.goto(`${ADMIN_URL}/orders/${encodeURIComponent(orderCode)}`);
          await page.getByRole("button", { name: /Hoàn tiền|Refund/ }).first().click();
          const dialog = page.getByRole("dialog");
          await expect(dialog).toBeVisible();
          await page.evaluate(async () => {
            await Promise.all(document.getAnimations().map((a) => a.finished.catch(() => undefined)));
          });
          await expect(dialog).toHaveCSS("opacity", "1");
          await expectMatrixClean(page, "orders-refund-dialog", cell);
        } finally {
          await ctx.close();
        }
      }
    } finally {
      await matrixBrowser.close();
    }
  });
});
