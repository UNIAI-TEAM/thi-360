import { randomUUID } from "node:crypto";
import { expect, test } from "@playwright/test";

import { getMyExams, getTestClockNow } from "./helpers/attempt-fixtures";
import { adminApi, ADMIN_URL, loginAdmin, RUN_ID, WEB_URL } from "./helpers/editor-qa";
import { createVerifiedAccount } from "./helpers/mail";
import { voucherExam, waitForPublishedExamSet } from "./helpers/published-exam-set";

/**
 * AC-5: Admin tạo voucher giảm 100% qua `/promotions/new?kind=VOUCHER` (`startsAt` ở quá khứ để
 * có hiệu lực ngay) -> trang chi tiết hiển thị đang chạy -> student áp voucher trên
 * `/checkout/DS-HSA-MATH-VOUCHER` nhận `0 đ (đã áp dụng voucher)` -> checkout bỏ qua `/mock-vnpay`
 * -> `/checkout/result` cấp bài thi -> `/home` xác nhận đủ lượt thi.
 */

interface PromotionDetail {
  id: string;
  revision: number;
  status: string;
  state: string;
  usedCount: number;
}

function formatVietnamDatetimeLocal(epochMs: number): string {
  return new Intl.DateTimeFormat("sv-SE", {
    timeZone: "Asia/Ho_Chi_Minh",
    year: "numeric",
    month: "2-digit",
    day: "2-digit",
    hour: "2-digit",
    minute: "2-digit",
    hour12: false,
  })
    .format(new Date(epochMs))
    .replace(" ", "T");
}

test.describe("QA Core Flow — AC-5: Admin tạo voucher 100% và student nhận bài 0đ bỏ qua VNPAY", () => {
  test.setTimeout(120_000);

  test.beforeAll(async ({ browser }) => {
    const context = await browser.newContext();
    try {
      await loginAdmin(context, "vi");
      await waitForPublishedExamSet(context.request);
    } finally {
      await context.close();
    }
  });

  test("AC-5: admin UI tạo voucher 100% hiệu lực ngay -> student áp mã 0đ không qua mock-vnpay", async ({
    browser,
  }) => {
    const voucherName = `QA-V100-${RUN_ID}`;
    const voucherCode = `QAV100${randomUUID().replace(/-/g, "").slice(0, 10).toUpperCase()}`;
    const exam = voucherExam();

    let admin: Awaited<ReturnType<typeof adminApi>> | undefined;
    let adminCtx: Awaited<ReturnType<typeof browser.newContext>> | undefined;
    let studentCtx: Awaited<ReturnType<typeof browser.newContext>> | undefined;
    let createdPromoId: string | undefined;

    try {
      admin = await adminApi();
      adminCtx = await browser.newContext();
      studentCtx = await browser.newContext();
      await loginAdmin(adminCtx, "vi");
      const adminPage = await adminCtx.newPage();

      // 1. Admin UI mở `/promotions` -> tab `Mã ưu đãi` -> `Tạo voucher` (`/promotions/new?kind=VOUCHER`)
      await adminPage.goto(`${ADMIN_URL}/promotions`);
      await expect(adminPage.getByRole("heading", { name: "Khuyến mãi" })).toBeVisible();
      await adminPage.getByRole("tab", { name: "Mã ưu đãi" }).click();
      await adminPage.locator("main").getByRole("link", { name: "Tạo voucher" }).first().click();
      await expect(adminPage).toHaveURL(/\/promotions\/new\?kind=VOUCHER/);

      // 2. Điền form voucher 100%; kéo `startsAt` (UTC+7) về quá khứ 15 phút để voucher RUNNING ngay (mặc định form là +1h)
      const clockIso = await getTestClockNow();
      const baseNowMs = Math.min(Date.now(), Date.parse(clockIso));
      const futureBaseMs = Math.max(Date.now(), Date.parse(clockIso));
      const pastStartLocal = formatVietnamDatetimeLocal(baseNowMs - 15 * 60_000);
      const futureEndLocal = formatVietnamDatetimeLocal(futureBaseMs + 7 * 24 * 60 * 60_000);

      await adminPage.getByLabel(/Tên khuyến mãi/).fill(voucherName);
      await adminPage.getByLabel(/Mã ưu đãi/).fill(voucherCode);
      await adminPage.getByLabel(/Phần trăm giảm/).fill("100");
      await adminPage.getByLabel(/Bắt đầu lúc/).fill(pastStartLocal);
      await adminPage.getByLabel(/Kết thúc lúc/).fill(futureEndLocal);

      const [createRes] = await Promise.all([
        adminPage.waitForResponse(
          (res) => res.url().endsWith("/api/v1/admin/promotions") && res.request().method() === "POST",
        ),
        adminPage.getByRole("button", { name: "Tạo khuyến mãi" }).click(),
      ]);
      expect(createRes.status(), await createRes.text()).toBe(201);
      const created = (await createRes.json()) as { id: string };
      createdPromoId = created.id;

      // 3. Trang chi tiết khuyến mãi hiển thị đang chạy (status=ACTIVE & state=RUNNING)
      await adminPage.waitForURL(new RegExp(`/promotions/${createdPromoId}$`), { timeout: 20_000 });
      await expect(adminPage.getByRole("heading", { name: voucherName })).toBeVisible();
      await expect(adminPage.getByText("Đang chạy").first()).toBeVisible();

      const promoDetailRes = await admin.api.get(`${ADMIN_URL}/api/v1/admin/promotions/${createdPromoId}`);
      expect(promoDetailRes.status()).toBe(200);
      const promoDetail = (await promoDetailRes.json()) as PromotionDetail;
      expect(promoDetail.status).toBe("ACTIVE");
      expect(promoDetail.state).toBe("RUNNING");

      // 4. Student đã xác minh vào `/checkout/DS-HSA-MATH-VOUCHER`, theo dõi toàn bộ navigation/request tới `/mock-vnpay`
      const { cookies } = await createVerifiedAccount("qa.v100");
      await studentCtx.addCookies(cookies);
      await studentCtx.addCookies([{ name: "thi360_locale", value: "vi", url: WEB_URL }]);

      const studentPage = await studentCtx.newPage();
      let visitedMockVnpay = false;
      studentPage.on("framenavigated", (frame) => {
        if (frame === studentPage.mainFrame() && frame.url().includes("/mock-vnpay")) {
          visitedMockVnpay = true;
        }
      });
      studentPage.on("request", (req) => {
        if (req.url().includes("/mock-vnpay")) {
          visitedMockVnpay = true;
        }
      });

      await studentPage.goto(`${WEB_URL}/checkout/${encodeURIComponent(exam.productCode)}`);
      await studentPage.getByLabel("Nhập mã giảm giá").fill(voucherCode);
      await studentPage.getByRole("button", { name: "Áp dụng" }).click();

      await expect(studentPage.getByText("0 đ (đã áp dụng voucher)")).toBeVisible({ timeout: 15_000 });
      await studentPage.getByRole("button", { name: "Dùng voucher và nhận bài thi" }).click();

      await studentPage.waitForURL(/\/checkout\/result\?order=/, { timeout: 25_000 });
      await expect(studentPage.getByRole("heading", { level: 1, name: "Đã cấp bài thi" })).toBeVisible();
      await expect(studentPage.getByText("0 đ (đã áp dụng voucher)")).toBeVisible();
      expect(visitedMockVnpay).toBe(false);

      // 5. Vào `/home` xác nhận bài thi được cấp với đủ lượt thi
      await studentPage.getByRole("link", { name: "Vào Bài thi của tôi" }).click();
      await expect(studentPage).toHaveURL(`${WEB_URL}/home`);
      await expect(studentPage.getByRole("heading", { name: exam.name })).toBeVisible();

      const myExams = await getMyExams(studentPage);
      const granted = myExams.find((item) => item.code === exam.code);
      if (!granted?.entitlementId) {
        throw new Error(`Không tìm thấy entitlementId của ${exam.code} trên /home`);
      }
      expect(granted.attemptsLeft).toBe(exam.listing.attemptsGranted);
      await expect(
        studentPage.locator(`a[href="/prepare/${encodeURIComponent(granted.entitlementId)}"]`),
      ).toBeVisible();

      const afterUseRes = await admin.api.get(`${ADMIN_URL}/api/v1/admin/promotions/${createdPromoId}`);
      expect(afterUseRes.status(), await afterUseRes.text()).toBe(200);
      const afterUseDetail = (await afterUseRes.json()) as PromotionDetail;
      expect(afterUseDetail.usedCount).toBe(1);
    } finally {
      try {
        if (createdPromoId && admin) {
          const getRes = await admin.api.get(`${ADMIN_URL}/api/v1/admin/promotions/${createdPromoId}`);
          if (getRes.ok()) {
            const detail = (await getRes.json()) as PromotionDetail;
            if (detail.usedCount === 0) {
              await admin.api.delete(`${ADMIN_URL}/api/v1/admin/promotions/${createdPromoId}`, {
                headers: admin.headers,
              });
            } else if (detail.status === "ACTIVE") {
              await admin.api.post(`${ADMIN_URL}/api/v1/admin/promotions/${createdPromoId}/stop`, {
                headers: admin.headers,
                data: { expectedRevision: detail.revision },
              });
            }
          }
        }
      } catch {
        // Ignore cleanup errors so teardown still closes browser contexts and reports root test failures.
      }
      await Promise.allSettled([adminCtx?.close(), studentCtx?.close(), admin?.api.dispose()]);
    }
  });
});
