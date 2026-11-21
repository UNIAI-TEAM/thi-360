import { expect, test, type Request } from "@playwright/test";
import { ADMIN_URL, RUN_ID } from "./helpers/editor-qa";
import {
  ACCOUNT_PASSWORD,
  createVerifiedAccount,
  expectUrlWithCallback,
  submitLoginForm,
  WEB_URL,
} from "./helpers/mail";
import { createPlatformApiContext } from "./helpers/platform";
import { allExams, publishedExamSet, waitForPublishedExamSet } from "./helpers/published-exam-set";

function saleExamCode(): string {
  const discontinued = publishedExamSet().discontinuedExam;
  return (allExams().find((exam) => exam.code !== discontinued) ?? allExams()[0]!).code;
}

test.describe("Luồng mua bài thi & kết quả VNPAY", () => {
  test.setTimeout(180_000);

  test("AC-1: mua qua Mock VNPAY thành công -> Đã cấp bài thi -> Vào Bài thi của tôi & Đơn của tôi", async ({
    context,
    page,
  }) => {
    await waitForPublishedExamSet(context.request, { timeoutMs: 120_000 });
    const examCode = saleExamCode();
    const { cookies } = await createVerifiedAccount("co.ok");
    await context.addCookies(cookies);

    await page.goto(`${WEB_URL}/checkout/${encodeURIComponent(examCode)}`);
    await expect(page.getByRole("heading", { level: 1, name: "Xác nhận mua" })).toBeVisible();

    await page.getByRole("button", { name: "Thanh toán qua VNPAY" }).click();
    await page.waitForURL(/\/mock-vnpay/);
    await page.locator('a[data-mock-action="success"]').click();

    await page.waitForURL(/\/checkout\/result/);
    await expect(page.getByRole("heading", { level: 1, name: "Đã cấp bài thi" })).toBeVisible();

    await page.getByRole("link", { name: "Vào Bài thi của tôi" }).click();
    await expect(page).toHaveURL(`${WEB_URL}/home`);
    await expect(page.getByRole("heading", { level: 1, name: "Bài thi của tôi" })).toBeVisible();
    await expect(page.locator("[data-exam-content]").first()).toBeVisible();

    await page.getByRole("link", { name: "Đơn của tôi" }).click();
    await expect(page).toHaveURL(`${WEB_URL}/account/orders`);
    await expect(page.getByText("Đã cấp bài thi").first()).toBeVisible();
  });

  test("AC-2: thanh toán VNPAY thất bại rồi Thanh toán lại thành công trên cùng đơn", async ({ context, page }) => {
    await waitForPublishedExamSet(context.request, { timeoutMs: 120_000 });
    const examCode = saleExamCode();
    const { cookies } = await createVerifiedAccount("co.retry");
    await context.addCookies(cookies);

    await page.goto(`${WEB_URL}/checkout/${encodeURIComponent(examCode)}`);
    await page.getByRole("button", { name: "Thanh toán qua VNPAY" }).click();
    await page.waitForURL(/\/mock-vnpay/);

    await page.locator('a[data-mock-action="failed"]').click();
    await page.waitForURL(/\/checkout\/result/);
    await expect(page.getByRole("heading", { level: 1, name: "Thanh toán chưa thành công" })).toBeVisible();

    await page.getByRole("button", { name: "Thanh toán lại" }).click();
    await page.waitForURL(/\/mock-vnpay/);
    await page.locator('a[data-mock-action="success"]').click();

    await page.waitForURL(/\/checkout\/result/);
    await expect(page.getByRole("heading", { level: 1, name: "Đã cấp bài thi" })).toBeVisible();
  });

  test("AC-3: không nhận phản hồi IPN -> poll 2s và sau 60s giữ màn Đang chờ xác nhận thanh toán", async ({
    context,
    page,
  }) => {
    await waitForPublishedExamSet(context.request, { timeoutMs: 120_000 });
    const examCode = saleExamCode();
    const { cookies } = await createVerifiedAccount("co.pend");
    await context.addCookies(cookies);

    await page.goto(`${WEB_URL}/checkout/${encodeURIComponent(examCode)}`);
    await page.getByRole("button", { name: "Thanh toán qua VNPAY" }).click();
    await page.waitForURL(/\/mock-vnpay/);
    await page.locator('a[data-mock-action="no_response"]').click();

    await page.waitForURL(/\/checkout\/result/);
    await expect(page.getByRole("heading", { level: 1, name: "Đang kiểm tra thanh toán" })).toBeVisible();

    const currentUrl = page.url();
    await page.clock.install();
    await page.goto(currentUrl);
    await expect(page.getByRole("heading", { level: 1, name: "Đang kiểm tra thanh toán" })).toBeVisible();

    await page.clock.fastForward(62_000);
    await expect(page.getByRole("heading", { level: 1, name: "Đang chờ xác nhận thanh toán" })).toBeVisible();
    await expect(page.getByRole("button", { name: "Kiểm tra lại ngay" })).toBeVisible();
  });

  test("AC-4: voucher giảm 100% -> 0 đ -> cấp bài thi ngay không qua VNPAY", async ({ context, page }) => {
    await waitForPublishedExamSet(context.request, { timeoutMs: 120_000 });
    const examCode = saleExamCode();
    const { cookies } = await createVerifiedAccount("co.free");
    const platform = await createPlatformApiContext();
    const headers = { origin: ADMIN_URL, "x-xsrf-token": platform.xsrf };
    const voucherCode = `E2EFREE${RUN_ID}`.replace(/[^A-Z0-9]/gi, "").slice(0, 20).toUpperCase();
    let promoId: string | undefined;

    try {
      const created = await platform.api.post(`${ADMIN_URL}/api/v1/admin/promotions`, {
        headers,
        data: {
          kind: "VOUCHER",
          code: voucherCode,
          name: `${RUN_ID} Free 100`,
          discountType: "PERCENT",
          discountValue: 100,
          combinable: false,
          scope: "ALL",
          audience: "ALL",
          startsAt: new Date(Date.now() - 60_000).toISOString(),
          endsAt: new Date(Date.now() + 2 * 3600_000).toISOString(),
        },
      });
      expect(created.status(), await created.text()).toBe(201);
      promoId = ((await created.json()) as { id: string }).id;

      await context.addCookies(cookies);
      await page.goto(`${WEB_URL}/checkout/${encodeURIComponent(examCode)}`);

      await page.getByLabel("Nhập mã giảm giá").fill(voucherCode);
      await page.getByRole("button", { name: "Áp dụng" }).click();

      await expect(page.getByText("0 đ (đã áp dụng voucher)")).toBeVisible();
      await page.getByRole("button", { name: "Dùng voucher và nhận bài thi" }).click();

      await page.waitForURL(/\/checkout\/result\?order=/);
      await expect(page.getByRole("heading", { level: 1, name: "Đã cấp bài thi" })).toBeVisible();
      await expect(page.getByText("0 đ (đã áp dụng voucher)")).toBeVisible();
    } finally {
      if (promoId) {
        await platform.api.delete(`${ADMIN_URL}/api/v1/admin/promotions/${promoId}`, { headers });
      }
      await platform.api.dispose();
    }
  });

  test("AC-7: đổi tài khoản ở tab khác cảnh báo và yêu cầu xác nhận trước khi mua", async ({ browser }) => {
    const setupCtx = await browser.newContext();
    await waitForPublishedExamSet(setupCtx.request, { timeoutMs: 120_000 });
    await setupCtx.close();

    const examCode = saleExamCode();
    const { account: userA, cookies: cookiesA } = await createVerifiedAccount("co.tabA");
    const { account: userB } = await createVerifiedAccount("co.tabB");

    const sharedContext = await browser.newContext();
    await sharedContext.addCookies(cookiesA);

    const pageA = await sharedContext.newPage();
    await pageA.goto(`${WEB_URL}/checkout/${encodeURIComponent(examCode)}`);
    await expect(pageA.getByText(userA.username)).toBeVisible();

    const pageB = await sharedContext.newPage();
    await pageB.goto(`${WEB_URL}/home`);
    await pageB.getByRole("button", { name: "Menu tài khoản" }).click();
    await pageB.getByRole("menuitem", { name: "Đăng xuất" }).click();
    await pageB.waitForURL(/\/login/);
    await submitLoginForm(pageB, userB.username, ACCOUNT_PASSWORD);
    await pageB.waitForURL(/\/home/);
    await pageB.close();

    await pageA.bringToFront();
    const accountWarn = pageA.getByRole("heading", { name: "Tài khoản đăng nhập đã thay đổi" });
    if (!(await accountWarn.isVisible())) {
      await pageA.getByRole("button", { name: "Thanh toán qua VNPAY" }).click();
    }
    await expect(accountWarn).toBeVisible();

    await pageA.getByRole("button", { name: `Tiếp tục với ${userB.username}` }).click();
    await expect(accountWarn).toHaveCount(0);

    await pageA.getByRole("button", { name: "Thanh toán qua VNPAY" }).click();
    await pageA.waitForURL(/\/mock-vnpay/);
    await pageA.locator('a[data-mock-action="success"]').click();
    await pageA.waitForURL(/\/checkout\/result/);
    await expect(pageA.getByRole("heading", { level: 1, name: "Đã cấp bài thi" })).toBeVisible();

    await sharedContext.close();
  });

  test("AC-11: nhấp đúp nút thanh toán chỉ tạo 1 đơn duy nhất", async ({ context, page }) => {
    await waitForPublishedExamSet(context.request, { timeoutMs: 120_000 });
    const examCode = saleExamCode();
    const { cookies } = await createVerifiedAccount("co.dbl");
    await context.addCookies(cookies);

    let postCount = 0;
    page.on("request", (req: Request) => {
      if (req.method() === "POST" && req.url().includes("/api/v1/me/orders")) {
        postCount += 1;
      }
    });

    await page.goto(`${WEB_URL}/checkout/${encodeURIComponent(examCode)}`);
    const payButton = page.getByRole("button", { name: "Thanh toán qua VNPAY" });
    await payButton.dblclick();
    await page.waitForURL(/\/mock-vnpay/);
    expect(postCount).toBe(1);

    await page.goto(`${WEB_URL}/account/orders`);
    await expect(page.getByText(examCode).first()).toBeVisible();
    expect(await page.locator("tbody tr").count()).toBe(1);
  });

  test("AC-12, AC-13, AC-14: callbackUrl khách, kết quả VNPAY cho khách và đơn thuộc tài khoản khác", async ({
    browser,
  }) => {
    const setupCtx = await browser.newContext();
    await waitForPublishedExamSet(setupCtx.request, { timeoutMs: 120_000 });
    await setupCtx.close();

    const examCode = saleExamCode();
    const { account: userA, cookies: cookiesA } = await createVerifiedAccount("co.own");
    const { cookies: cookiesB } = await createVerifiedAccount("co.other");

    // AC-12: khach vao /checkout/[code]?v=1&p=1000 -> /login giữ callbackUrl -> dang nhap quay lai dung URL
    const guestContext = await browser.newContext();
    const guestPage = await guestContext.newPage();
    await guestPage.goto(`${WEB_URL}/checkout/${encodeURIComponent(examCode)}?v=1&p=1000`);
    await expectUrlWithCallback(guestPage, "/login", `/checkout/${examCode}?v=1&p=1000`);
    await submitLoginForm(guestPage, userA.username, ACCOUNT_PASSWORD);
    await expect(guestPage).toHaveURL(`${WEB_URL}/checkout/${encodeURIComponent(examCode)}?v=1&p=1000`);
    await guestContext.close();

    // AC-13: hoan tat VNPAY roi mo URL return bang phien khach -> khong goi /me/orders -> dang nhap lai xem duoc don
    const ctxA = await browser.newContext();
    await ctxA.addCookies(cookiesA);
    const pageA = await ctxA.newPage();
    await pageA.goto(`${WEB_URL}/checkout/${encodeURIComponent(examCode)}`);
    await pageA.getByRole("button", { name: "Thanh toán qua VNPAY" }).click();
    await pageA.waitForURL(/\/mock-vnpay/);
    await pageA.locator('a[data-mock-action="success"]').click();
    await pageA.waitForURL(/\/checkout\/result/);
    await expect(pageA.getByRole("heading", { level: 1, name: "Đã cấp bài thi" })).toBeVisible();
    const resultUrl = pageA.url();
    const orderCodeMatch = /order=([^&]+)/.exec(resultUrl) ?? [];
    await pageA.getByRole("link", { name: "Xem chi tiết đơn" }).click();
    await pageA.waitForURL(/\/account\/orders\/([^/]+)/);
    const createdOrderCode = pageA.url().split("/").pop() ?? orderCodeMatch[1] ?? "";
    await ctxA.close();

    const vnpGuestCtx = await browser.newContext();
    const vnpGuestPage = await vnpGuestCtx.newPage();
    const orderApiCalls: string[] = [];
    vnpGuestPage.on("request", (req) => {
      if (req.url().includes("/api/v1/me/orders")) orderApiCalls.push(req.url());
    });
    await vnpGuestPage.goto(resultUrl);
    await expect(vnpGuestPage.getByRole("heading", { level: 1, name: "Thanh toán thành công" })).toBeVisible();
    expect(orderApiCalls).toEqual([]);

    await vnpGuestPage.getByRole("link", { name: "Đăng nhập để xem bài thi" }).click();
    await submitLoginForm(vnpGuestPage, userA.username, ACCOUNT_PASSWORD);
    await expect(vnpGuestPage.getByRole("heading", { level: 1, name: "Đã cấp bài thi" })).toBeVisible();
    await vnpGuestCtx.close();

    // AC-14: tai khoan B mo don cua tai khoan A -> 404 an toan
    const ctxB = await browser.newContext();
    await ctxB.addCookies(cookiesB);
    const pageB = await ctxB.newPage();
    await pageB.goto(`${WEB_URL}/checkout/result?order=${encodeURIComponent(createdOrderCode)}`);
    await expect(
      pageB.getByRole("heading", { name: "Không tìm thấy đơn trong tài khoản đang đăng nhập" }),
    ).toBeVisible();
    await pageB.goto(`${WEB_URL}/account/orders/${encodeURIComponent(createdOrderCode)}`);
    await expect(pageB.getByRole("heading", { name: "Không tìm thấy đơn hàng" })).toBeVisible();
    await ctxB.close();
  });
});
