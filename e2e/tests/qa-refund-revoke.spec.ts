import { randomUUID } from "node:crypto";
import { expect, test, type APIRequestContext } from "@playwright/test";

import { ensureDsReady, getMyExams } from "./helpers/attempt-fixtures";
import { ADMIN_URL, loginAdmin, WEB_URL } from "./helpers/editor-qa";
import { createVerifiedAccount } from "./helpers/mail";
import { examByCode } from "./helpers/published-exam-set";

/**
 * AC-7: Student mua `DS-HSA-PART-MATH-MINI` qua `/mock-vnpay` -> nhận 3 lượt thi trên `/home` ->
 * Admin vào `/orders/[orderCode]` thực hiện `Hoàn tiền` toàn bộ (chưa dùng lượt nào) ->
 * đơn chuyển `Đã hoàn toàn bộ` & cấp quyền `Đã thu hồi` -> Student mất toàn bộ lượt
 * (`attemptsLeft === 0`, `/home` hiện `Hết lượt`, `/prepare/[entitlementId]` khóa nút bắt đầu
 * và `POST /api/v1/me/attempts` trả `409 NO_ATTEMPTS_LEFT`).
 */

function readXsrfCookie(
  cookies: Awaited<ReturnType<APIRequestContext["storageState"]>>["cookies"],
): string {
  return (
    cookies.find((cookie) => cookie.name === "thi360_xsrf")?.value ??
    cookies.find((cookie) => cookie.name === "XSRF-TOKEN")?.value ??
    ""
  );
}

test.describe("QA Core Flow — AC-7: Hoàn tiền toàn bộ thu hồi lượt thi chưa dùng", () => {
  test.setTimeout(120_000);

  test.beforeAll(async ({ browser }) => {
    await ensureDsReady(browser);
  });

  test("AC-7: student mua bài 3 lượt -> admin hoàn tiền -> đơn Đã hoàn toàn bộ và thu hồi 100% lượt", async ({
    browser,
  }) => {
    const exam = examByCode("DS-HSA-PART-MATH-MINI");
    const { cookies } = await createVerifiedAccount("qa.ref");

    const studentCtx = await browser.newContext();
    const adminCtx = await browser.newContext();
    try {
      await studentCtx.addCookies(cookies);
      await studentCtx.addCookies([{ name: "thi360_locale", value: "vi", url: WEB_URL }]);
      const studentPage = await studentCtx.newPage();

      // 1. Student thanh toán `DS-HSA-PART-MATH-MINI` qua `/checkout` -> `/mock-vnpay` -> `/checkout/result?order=...`
      await studentPage.goto(`${WEB_URL}/checkout/${encodeURIComponent(exam.productCode)}`);
      await studentPage.getByRole("button", { name: "Thanh toán qua VNPAY" }).click();
      await studentPage.waitForURL(/\/mock-vnpay/, { timeout: 20_000 });
      await studentPage.locator('a[data-mock-action="success"]').click();

      await studentPage.waitForURL(/\/checkout\/result\?order=/, { timeout: 25_000 });
      await expect(studentPage.getByRole("heading", { level: 1, name: "Đã cấp bài thi" })).toBeVisible();

      const orderCode = new URL(studentPage.url()).searchParams.get("order");
      if (!orderCode) {
        throw new Error(`Không tách được orderCode từ URL ${studentPage.url()}`);
      }

      // 2. Trên `/home` và `/api/v1/me/exams`, student có đủ 3 lượt thi + liên kết `Bắt đầu`
      await studentPage.getByRole("link", { name: "Vào Bài thi của tôi" }).click();
      await expect(studentPage).toHaveURL(`${WEB_URL}/home`);
      await expect(studentPage.getByRole("heading", { name: exam.name })).toBeVisible();

      const initialExams = await getMyExams(studentPage);
      const ownedBefore = initialExams.find((item) => item.code === exam.code);
      if (!ownedBefore?.entitlementId) {
        throw new Error(`Không tìm thấy entitlementId của ${exam.code} trước khi hoàn tiền`);
      }
      expect(ownedBefore.attemptsLeft).toBe(3);

      const prepareLink = studentPage.locator(
        `a[href="/prepare/${encodeURIComponent(ownedBefore.entitlementId)}"]`,
      );
      await expect(prepareLink).toBeVisible();
      await expect(prepareLink).toContainText("Bắt đầu");

      // 3. Admin mở `/orders/[orderCode]` -> `Hoàn tiền` toàn bộ
      await loginAdmin(adminCtx, "vi");
      const adminPage = await adminCtx.newPage();

      await adminPage.goto(`${ADMIN_URL}/orders/${encodeURIComponent(orderCode)}`);
      await expect(adminPage.getByRole("heading", { name: new RegExp(orderCode) })).toBeVisible({
        timeout: 20_000,
      });

      await adminPage.getByRole("button", { name: "Hoàn tiền" }).first().click();
      const refundDialog = adminPage.getByRole("dialog");
      await expect(refundDialog).toBeVisible();

      await refundDialog.getByRole("checkbox").check();
      await refundDialog
        .getByLabel(/Lý do hoàn tiền/)
        .fill("QA-E2E full refund revoke unused attempts");
      await refundDialog.getByRole("button", { name: "Gửi yêu cầu hoàn tiền" }).click();

      await expect(refundDialog).toHaveCount(0, { timeout: 25_000 });
      await expect(adminPage.getByText("Đã hoàn toàn bộ").first()).toBeVisible({ timeout: 20_000 });
      await expect(adminPage.getByText("Đã thu hồi").first()).toBeVisible({ timeout: 20_000 });

      // 4. Student reload `/home` -> lượt còn `0`, trạng thái `Hết lượt`, nút `Mua lại`, không còn `Bắt đầu`
      await expect
        .poll(
          async () => {
            const refreshed = await getMyExams(studentPage);
            return refreshed.find((item) => item.code === exam.code)?.attemptsLeft ?? -1;
          },
          { timeout: 20_000, message: "Đợi attemptsLeft về 0 sau hoàn tiền" },
        )
        .toBe(0);

      await studentPage.reload();
      const examCard = studentPage
        .getByRole("article")
        .filter({ has: studentPage.getByRole("heading", { name: exam.name }) })
        .first();
      await expect(examCard).toBeVisible();
      await expect(examCard.getByText("Hết lượt").first()).toBeVisible();
      await expect(examCard.getByRole("link", { name: "Mua lại" })).toBeVisible();
      await expect(prepareLink).toHaveCount(0);

      // 5. Truy cập thẳng `/prepare/[entitlementId]` -> cảnh báo hết lượt, nút bắt đầu bị khóa, API trả 409 NO_ATTEMPTS_LEFT
      await studentPage.goto(`${WEB_URL}/prepare/${encodeURIComponent(ownedBefore.entitlementId)}`);
      await expect(studentPage.getByRole("alert")).toContainText("Bạn đã dùng hết lượt của bài này.");
      await expect(studentPage.getByRole("button", { name: "Bắt đầu bài thi" })).toBeDisabled();

      const xsrf = readXsrfCookie(await studentCtx.cookies(WEB_URL));
      const blockedStartRes = await studentCtx.request.post(`${WEB_URL}/api/v1/me/attempts`, {
        headers: {
          Origin: WEB_URL,
          ...(xsrf ? { "X-XSRF-TOKEN": xsrf } : {}),
          "Idempotency-Key": randomUUID(),
        },
        data: { entitlementId: ownedBefore.entitlementId },
      });
      expect(blockedStartRes.status()).toBe(409);
      const blockedBody = (await blockedStartRes.json()) as { code?: string };
      expect(blockedBody.code).toBe("NO_ATTEMPTS_LEFT");
    } finally {
      await studentCtx.close();
      await adminCtx.close();
    }
  });
});
