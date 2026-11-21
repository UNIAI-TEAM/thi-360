import { expect, test, type Request } from "@playwright/test";
import { ADMIN_URL, loginAdmin, RUN_ID } from "./helpers/editor-qa";
import {
  ACCOUNT_PASSWORD,
  createVerifiedAccount,
  expectUrlWithCallback,
  submitLoginForm,
  WEB_URL,
} from "./helpers/mail";
import { createPlatformApiContext } from "./helpers/platform";
import {
  allExams,
  publishedExamSet,
  voucherExam,
  waitForPublishedExamSet,
  type PublishedExam,
} from "./helpers/published-exam-set";

function saleExam(): PublishedExam {
  const discontinued = publishedExamSet().discontinuedExam;
  return allExams().find((exam) => exam.code !== discontinued) ?? allExams()[0]!;
}

test.beforeAll(async ({ browser }) => {
  test.setTimeout(300_000);
  const context = await browser.newContext();
  try {
    await loginAdmin(context, "vi");
    await waitForPublishedExamSet(context.request, { timeoutMs: 240_000 });
  } finally {
    await context.close();
  }
});

test.describe("Luồng mua bài thi & kết quả VNPAY", () => {
  test.setTimeout(180_000);

  test("AC-1: mua qua Mock VNPAY thành công -> Đã cấp bài thi -> Vào Bài thi của tôi & Đơn của tôi", async ({
    context,
    page,
  }) => {
    const exam = saleExam();
    const { cookies } = await createVerifiedAccount("co.ok");
    await context.addCookies(cookies);

    await page.goto(`${WEB_URL}/checkout/${encodeURIComponent(exam.productCode)}`);
    await expect(page.getByRole("heading", { level: 1, name: "Xác nhận mua" })).toBeVisible();

    await page.getByRole("button", { name: "Thanh toán qua VNPAY" }).click();
    await page.waitForURL(/\/mock-vnpay/);
    await page.locator('a[data-mock-action="success"]').click();

    await page.waitForURL(/\/checkout\/result/);
    await expect(page.getByRole("heading", { level: 1, name: "Đã cấp bài thi" })).toBeVisible();

    await page.getByRole("link", { name: "Vào Bài thi của tôi" }).click();
    await expect(page).toHaveURL(`${WEB_URL}/home`);
    await expect(page.getByRole("heading", { level: 1, name: "Bài thi của tôi" })).toBeVisible();
    await expect(page.getByRole("heading", { name: exam.name })).toBeVisible();

    await page.getByRole("link", { name: "Đơn của tôi" }).click();
    await expect(page).toHaveURL(`${WEB_URL}/account/orders`);
    await expect(page.getByText("Đã cấp bài thi").first()).toBeVisible();
  });

  test("AC-2: thanh toán VNPAY thất bại rồi Thanh toán lại thành công trên cùng đơn", async ({ context, page }) => {
    const exam = saleExam();
    const { cookies } = await createVerifiedAccount("co.retry");
    await context.addCookies(cookies);

    await page.goto(`${WEB_URL}/checkout/${encodeURIComponent(exam.productCode)}`);
    await page.getByRole("button", { name: "Thanh toán qua VNPAY" }).click();
    await page.waitForURL(/\/mock-vnpay/);
    const firstTxnRef = new URL(page.url()).searchParams.get("vnp_TxnRef") ?? "";
    expect(firstTxnRef).toMatch(/^DH/i);

    await page.locator('a[data-mock-action="failed"]').click();
    await page.waitForURL(/\/checkout\/result/);
    await expect(page.getByRole("heading", { level: 1, name: "Thanh toán chưa thành công" })).toBeVisible();

    await page.getByRole("button", { name: "Thanh toán lại" }).click();
    await page.waitForURL(/\/mock-vnpay/);
    const secondTxnRef = new URL(page.url()).searchParams.get("vnp_TxnRef") ?? "";
    expect(secondTxnRef).toMatch(/^DH/i);
    expect(secondTxnRef).not.toBe(firstTxnRef);
    expect(secondTxnRef.slice(0, -2)).toBe(firstTxnRef.slice(0, -2));

    await page.locator('a[data-mock-action="success"]').click();
    await page.waitForURL(/\/checkout\/result/);
    await expect(page.getByRole("heading", { level: 1, name: "Đã cấp bài thi" })).toBeVisible();

    await page.goto(`${WEB_URL}/account/orders`);
    await expect(page.getByRole("heading", { level: 1, name: "Đơn của tôi" })).toBeVisible();
    await expect(page.locator("tbody tr")).toHaveCount(1);
  });

  test("AC-3: không nhận phản hồi IPN -> poll 2s và sau 60s giữ màn Đang kiểm tra thanh toán", async ({
    context,
    page,
  }) => {
    const exam = saleExam();
    const { cookies } = await createVerifiedAccount("co.pend");
    await context.addCookies(cookies);

    await page.goto(`${WEB_URL}/checkout/${encodeURIComponent(exam.productCode)}`);
    await page.getByRole("button", { name: "Thanh toán qua VNPAY" }).click();
    await page.waitForURL(/\/mock-vnpay/);
    await page.locator('a[data-mock-action="no_response"]').click();

    await page.waitForURL(/\/checkout\/result/);
    await expect(page.getByRole("heading", { level: 1, name: "Đang chờ xác nhận thanh toán" })).toBeVisible();

    const currentUrl = page.url();
    await page.clock.install();
    await page.goto(currentUrl);
    await expect(page.getByRole("heading", { level: 1, name: "Đang chờ xác nhận thanh toán" })).toBeVisible();

    let postTimeoutPolls = 0;
    page.on("request", (req: Request) => {
      if (req.method() === "GET" && req.url().includes("/api/v1/me/orders/")) {
        postTimeoutPolls += 1;
      }
    });

    await page.clock.fastForward(62_000);
    await expect(page.getByRole("heading", { level: 1, name: "Đang kiểm tra thanh toán" })).toBeVisible();
    await expect(page.getByText(/DH[A-Z0-9]+/i).first()).toBeVisible();
    await expect(page.getByText(/liên hệ hỗ trợ/)).toBeVisible();
    await expect(page.getByRole("button", { name: "Kiểm tra lại ngay" })).toBeVisible();

    postTimeoutPolls = 0;
    await page.clock.fastForward(10_000);
    expect(postTimeoutPolls).toBe(0);
  });

  test("AC-4: voucher giảm 100% -> 0 đ -> cấp bài thi ngay không qua VNPAY", async ({ context, page }) => {
    const exam = voucherExam();
    const { cookies } = await createVerifiedAccount("co.free");
    const platform = await createPlatformApiContext();
    const headers = { origin: ADMIN_URL, "x-xsrf-token": platform.xsrf };
    const voucherCode = `E2EFREE${RUN_ID}`.replace(/[^A-Z0-9]/gi, "").slice(0, 20).toUpperCase();
    let promoId: string | undefined;

    try {
      const created = await platform.api.post(`${ADMIN_URL}/api/v1/admin/promotions`, {
        headers: { ...headers, "idempotency-key": crypto.randomUUID() },
        data: {
          kind: "VOUCHER",
          code: voucherCode,
          name: `${RUN_ID} Free 100`,
          discountType: "PERCENT",
          discountValue: 100,
          combinable: false,
          scope: "ALL",
          audience: "ALL",
          status: "ACTIVE",
          startsAt: new Date(Date.now() - 60_000).toISOString(),
          endsAt: new Date(Date.now() + 2 * 3600_000).toISOString(),
        },
      });
      expect(created.status(), await created.text()).toBe(201);
      promoId = ((await created.json()) as { id: string }).id;

      await context.addCookies(cookies);
      await page.goto(`${WEB_URL}/checkout/${encodeURIComponent(exam.productCode)}`);

      await page.getByLabel("Nhập mã giảm giá").fill(voucherCode);
      await page.getByRole("button", { name: "Áp dụng" }).click();

      await expect(page.getByText("0 đ (đã áp dụng voucher)")).toBeVisible();
      await page.getByRole("button", { name: "Dùng voucher và nhận bài thi" }).click();

      await page.waitForURL(/\/checkout\/result\?order=/);
      await expect(page.getByRole("heading", { level: 1, name: "Đã cấp bài thi" })).toBeVisible();
      await expect(page.getByText("0 đ (đã áp dụng voucher)")).toBeVisible();

      await page.getByRole("link", { name: "Vào Bài thi của tôi" }).click();
      await expect(page).toHaveURL(`${WEB_URL}/home`);
      await expect(page.getByRole("heading", { name: exam.name })).toBeVisible();
    } finally {
      if (promoId) {
        const getRes = await platform.api.get(`${ADMIN_URL}/api/v1/admin/promotions/${promoId}`);
        if (getRes.ok()) {
          const detail = (await getRes.json()) as { usedCount: number; status: string; revision: number };
          if (detail.usedCount === 0) {
            await platform.api.delete(`${ADMIN_URL}/api/v1/admin/promotions/${promoId}`, { headers });
          } else if (detail.status === "ACTIVE") {
            await platform.api.post(`${ADMIN_URL}/api/v1/admin/promotions/${promoId}/stop`, {
              headers,
              data: { expectedRevision: detail.revision },
            });
          }
        }
      }
      await platform.api.dispose();
    }
  });

  test("AC-7: đổi tài khoản ở tab khác cảnh báo và yêu cầu xác nhận trước khi mua", async ({ browser }) => {
    const exam = saleExam();
    const { account: userA, cookies: cookiesA } = await createVerifiedAccount("co.tabA");
    const { account: userB } = await createVerifiedAccount("co.tabB");

    const sharedContext = await browser.newContext();
    try {
      await sharedContext.addCookies(cookiesA);

      const pageA = await sharedContext.newPage();
      await pageA.goto(`${WEB_URL}/checkout/${encodeURIComponent(exam.productCode)}`);
      await expect(
        pageA.getByRole("region", { name: "Tài khoản nhận bài thi" }).getByText(userA.username, { exact: true }),
      ).toBeVisible();

      const pageB = await sharedContext.newPage();
      await pageB.goto(`${WEB_URL}/home`);
      await pageB.getByRole("button", { name: "Menu tài khoản" }).click();
      await pageB.getByRole("menuitem", { name: "Đăng xuất" }).click();
      await pageB.waitForURL(/\/login/);
      await submitLoginForm(pageB, userB.username, ACCOUNT_PASSWORD);
      await pageB.waitForURL(/\/home/);
      await pageB.close();

      // Giu /api/v1/me truoc khi nhan Thanh toan de bat buoc kiem tra duong 409 ACCOUNT_CHANGED
      let holdMe = true;
      let releaseMe!: () => void;
      const meGate = new Promise<void>((resolve) => {
        releaseMe = resolve;
      });
      await pageA.route("**/api/v1/me", async (route) => {
        if (holdMe) {
          await meGate;
        }
        await route.fallback();
      });

      try {
        await pageA.bringToFront();
        const conflictResponsePromise = pageA.waitForResponse(
          (res) => res.request().method() === "POST" && res.url().includes("/api/v1/me/orders"),
        );
        await pageA.getByRole("button", { name: "Thanh toán qua VNPAY" }).click();
        const conflictResponse = await conflictResponsePromise;
        expect(conflictResponse.status()).toBe(409);
        const conflictBody = (await conflictResponse.json()) as { code?: string };
        expect(conflictBody.code).toBe("ACCOUNT_CHANGED");

        holdMe = false;
        releaseMe();
        await expect(pageA.getByRole("heading", { name: "Tài khoản đăng nhập đã thay đổi" })).toBeVisible();
      } finally {
        holdMe = false;
        releaseMe();
        await pageA.unroute("**/api/v1/me").catch(() => {});
      }

      const accountWarn = pageA.getByRole("heading", { name: "Tài khoản đăng nhập đã thay đổi" });
      await pageA.getByRole("button", { name: `Tiếp tục với ${userB.username}` }).click();
      await expect(accountWarn).toHaveCount(0);

      await pageA.getByRole("button", { name: "Thanh toán qua VNPAY" }).click();
      await pageA.waitForURL(/\/mock-vnpay/);
      await pageA.locator('a[data-mock-action="success"]').click();
      await pageA.waitForURL(/\/checkout\/result/);
      await expect(pageA.getByRole("heading", { level: 1, name: "Đã cấp bài thi" })).toBeVisible();
    } finally {
      await sharedContext.close();
    }

    // Không có đơn nào được tạo cho tài khoản cũ (userA)
    const checkCtxA = await browser.newContext();
    try {
      const loginRes = await checkCtxA.request.post(`${WEB_URL}/api/v1/auth/login`, {
        headers: { origin: WEB_URL },
        data: { identifier: userA.username, password: ACCOUNT_PASSWORD },
      });
      expect(loginRes.ok(), `re-login ${userA.username}: ${loginRes.status()}`).toBe(true);
      const checkPageA = await checkCtxA.newPage();
      await checkPageA.goto(`${WEB_URL}/account/orders`);
      await expect(checkPageA.getByRole("heading", { name: "Bạn chưa có đơn nào" })).toBeVisible();
    } finally {
      await checkCtxA.close();
    }
  });

  test("AC-11: nhấp đúp nút thanh toán chỉ tạo 1 đơn duy nhất", async ({ context, page }) => {
    const exam = saleExam();
    const { cookies } = await createVerifiedAccount("co.dbl");
    await context.addCookies(cookies);

    const orderRequests: Request[] = [];
    page.on("request", (req: Request) => {
      if (req.method() === "POST" && req.url().includes("/api/v1/me/orders")) {
        orderRequests.push(req);
      }
    });

    await page.goto(`${WEB_URL}/checkout/${encodeURIComponent(exam.productCode)}`);
    const payButton = page.getByRole("button", { name: "Thanh toán qua VNPAY" });
    await payButton.dblclick();
    await page.waitForURL(/\/mock-vnpay/);
    expect(orderRequests).toHaveLength(1);
    expect(orderRequests[0]!.headers()["idempotency-key"]).toBeTruthy();

    await page.goto(`${WEB_URL}/account/orders`);
    await expect(page.getByText(exam.name).first()).toBeVisible();
    await expect(page.locator("tbody tr")).toHaveCount(1);
  });

  test("AC-12, AC-13, AC-14: callbackUrl khách, kết quả VNPAY cho khách và đơn thuộc tài khoản khác", async ({
    browser,
  }) => {
    const exam = saleExam();
    const { account: userA, cookies: cookiesA } = await createVerifiedAccount("co.own");
    const { cookies: cookiesB } = await createVerifiedAccount("co.other");

    // AC-12: khách vào /checkout/[code]?v=1&p=1000 -> /login giữ callbackUrl -> đăng nhập quay lại đúng URL
    const guestContext = await browser.newContext();
    try {
      const guestPage = await guestContext.newPage();
      await guestPage.goto(`${WEB_URL}/checkout/${encodeURIComponent(exam.productCode)}?v=1&p=1000`);
      await expectUrlWithCallback(guestPage, "/login", `${WEB_URL}/checkout/${exam.productCode}?v=1&p=1000`);
      await submitLoginForm(guestPage, userA.username, ACCOUNT_PASSWORD);
      await expect(guestPage).toHaveURL(`${WEB_URL}/checkout/${encodeURIComponent(exam.productCode)}?v=1&p=1000`);
    } finally {
      await guestContext.close();
    }

    // AC-13: hoàn tất VNPAY rồi mở URL return bằng phiên khách -> không gọi /me/orders -> đăng nhập lại xem được đơn
    let resultUrl = "";
    let createdOrderCode = "";
    const ctxA = await browser.newContext();
    try {
      await ctxA.addCookies(cookiesA);
      const pageA = await ctxA.newPage();
      await pageA.goto(`${WEB_URL}/checkout/${encodeURIComponent(exam.productCode)}`);
      await pageA.getByRole("button", { name: "Thanh toán qua VNPAY" }).click();
      await pageA.waitForURL(/\/mock-vnpay/);
      await pageA.locator('a[data-mock-action="success"]').click();
      await pageA.waitForURL(/\/checkout\/result/);
      await expect(pageA.getByRole("heading", { level: 1, name: "Đã cấp bài thi" })).toBeVisible();
      resultUrl = pageA.url();
      const orderCodeMatch = /order=([^&]+)/.exec(resultUrl) ?? [];
      await pageA.getByRole("link", { name: "Xem chi tiết đơn" }).click();
      await pageA.waitForURL(/\/account\/orders\/([^/]+)/);
      createdOrderCode = pageA.url().split("/").pop() ?? orderCodeMatch[1] ?? "";
    } finally {
      await ctxA.close();
    }

    const vnpGuestCtx = await browser.newContext();
    try {
      const vnpGuestPage = await vnpGuestCtx.newPage();
      const orderApiCalls: string[] = [];
      vnpGuestPage.on("request", (req) => {
        if (req.url().includes("/api/v1/me/orders")) orderApiCalls.push(req.url());
      });
      await vnpGuestPage.goto(resultUrl);
      await expect(vnpGuestPage.getByRole("heading", { level: 1, name: "Thanh toán thành công" })).toBeVisible();
      expect(orderApiCalls).toEqual([]);

      const signInLink = vnpGuestPage.getByRole("link", { name: "Đăng nhập để xem bài thi" });
      await expect(signInLink).toHaveAttribute(
        "href",
        `/login?callbackUrl=%2Fcheckout%2Fresult%3Forder%3D${encodeURIComponent(createdOrderCode)}`,
      );
      await signInLink.click();
      await submitLoginForm(vnpGuestPage, userA.username, ACCOUNT_PASSWORD);
      await expect(vnpGuestPage.getByRole("heading", { level: 1, name: "Đã cấp bài thi" })).toBeVisible();
    } finally {
      await vnpGuestCtx.close();
    }

    // AC-14: tài khoản B mở đơn của tài khoản A -> 404 an toàn trên cả hai trang
    const ctxB = await browser.newContext();
    try {
      await ctxB.addCookies(cookiesB);
      const pageB = await ctxB.newPage();
      await pageB.goto(`${WEB_URL}/checkout/result?order=${encodeURIComponent(createdOrderCode)}`);
      await expect(
        pageB.getByRole("heading", { name: "Không tìm thấy đơn trong tài khoản đang đăng nhập" }),
      ).toBeVisible();
      await pageB.goto(`${WEB_URL}/account/orders/${encodeURIComponent(createdOrderCode)}`);
      await expect(pageB.getByRole("heading", { name: "Không tìm thấy đơn hàng" })).toBeVisible();
      await expect(pageB.getByText(/Không tìm thấy đơn trong tài khoản đang đăng nhập/)).toBeVisible();
    } finally {
      await ctxB.close();
    }
  });
});
