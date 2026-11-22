import { expect, test, type APIRequestContext, type Page } from "@playwright/test";

import { adminApi, ADMIN_URL, loginAdmin, WEB_URL } from "./helpers/editor-qa";
import { API_URL, ensureDsReady, getMyExams } from "./helpers/attempt-fixtures";
import { createVerifiedAccount } from "./helpers/mail";

interface AdminOrderPaymentView {
  id: string;
  attemptSeq: number;
  txnRef: string;
  state: string;
  duplicate: boolean;
  excessReason?: string | null;
  refundableAmountVnd: number;
  refundedAmountVnd: number;
  refunds: Array<{ id: string; state: string; amountVnd: number }>;
}

interface AdminOrderDetailPayload {
  orderCode: string;
  state: string;
  paymentStatus: string;
  refundStatus: string;
  needsReview: boolean;
  attentionTypes?: string[];
  payments: AdminOrderPaymentView[];
  grants: Array<{ grantId?: string; attempts: number; status?: string }>;
}

async function extractOrderCodeFromUrlOrDom(page: Page): Promise<string> {
  const fromQuery = new URL(page.url()).searchParams.get("order");
  if (fromQuery) return fromQuery;
  const link = page.locator('main a[href^="/account/orders/"]').first();
  await expect(link).toBeVisible({ timeout: 15_000 });
  const href = await link.getAttribute("href");
  const match = href ? /\/account\/orders\/([^/?#]+)/.exec(href) : null;
  expect(match?.[1], `Cannot extract order code from href=${href}`).toBeTruthy();
  return decodeURIComponent(match![1]!);
}

async function fetchAdminOrderDetail(api: APIRequestContext, orderCode: string): Promise<AdminOrderDetailPayload> {
  const res = await api.get(`${ADMIN_URL}/api/v1/admin/orders/${encodeURIComponent(orderCode)}`);
  expect(res.ok(), `GET /admin/orders/${orderCode}: ${res.status()} ${await res.text()}`).toBe(true);
  return (await res.json()) as AdminOrderDetailPayload;
}

test.describe("AC-25 BS 14 khoản thu thừa từ thanh toán nhiều lần", () => {
  test.beforeAll(async ({ browser }) => {
    await ensureDsReady(browser);
  });

  test("AC-25 BS 14 khoản thu thừa: thử 1 lỗi -> thử 2 thành công -> IPN trễ lần 1 -> hoàn riêng lần 1 giữ FULFILLED và 3 lượt, replay lần 2 trả 02", async ({
    browser,
  }) => {
    const { cookies } = await createVerifiedAccount("qa.pay14");
    const studentCtx = await browser.newContext();
    await studentCtx.addCookies(cookies);
    const studentPage = await studentCtx.newPage();
    studentPage.setDefaultTimeout(25_000);
    const admin = await adminApi();

    try {
      // 1. Tạo thanh toán lần 1 -> lưu IPN thành công của lần 1 -> chọn fail trên /mock-vnpay
      await studentPage.goto(`${WEB_URL}/checkout/DS-HSA-PART-MATH-MINI`);
      await expect(studentPage.getByRole("heading", { level: 1, name: "Xác nhận mua" })).toBeVisible();
      await studentPage.getByRole("button", { name: "Thanh toán qua VNPAY" }).click();
      await studentPage.waitForURL(/\/mock-vnpay/);

      const successLink1 = studentPage.locator('a[data-mock-action="success"]');
      await expect(successLink1).toBeVisible();
      const payment1SuccessIpnUrl = await successLink1.getAttribute("data-ipn-url");
      expect(payment1SuccessIpnUrl, "Mock VNPAY attempt 1 must expose data-ipn-url").toBeTruthy();
      const txnRef1 = new URLSearchParams(payment1SuccessIpnUrl!.split("?")[1] ?? "").get("vnp_TxnRef") ?? "";
      expect(txnRef1).not.toBe("");

      await studentPage.locator('a[data-mock-action="failed"]').click();
      await studentPage.waitForURL(/\/checkout\/result/);
      const orderCode = await extractOrderCodeFromUrlOrDom(studentPage);

      // 2. Bấm Thanh toán lại để tạo thanh toán lần 2 trên cùng đơn hàng và hoàn tất thành công
      const retryBtn = studentPage.getByRole("button", { name: "Thanh toán lại" });
      await expect(retryBtn).toBeVisible({ timeout: 15_000 });
      await retryBtn.click();
      await studentPage.waitForURL(/\/mock-vnpay/);

      const successLink2 = studentPage.locator('a[data-mock-action="success"]');
      await expect(successLink2).toBeVisible();
      const payment2SuccessIpnUrl = await successLink2.getAttribute("data-ipn-url");
      expect(payment2SuccessIpnUrl).toBeTruthy();
      const txnRef2 = new URLSearchParams(payment2SuccessIpnUrl!.split("?")[1] ?? "").get("vnp_TxnRef") ?? "";
      expect(txnRef2).not.toBe("");
      expect(txnRef2).not.toBe(txnRef1);

      await successLink2.click();
      await studentPage.waitForURL(/\/checkout\/result/);
      await expect(studentPage.getByRole("heading", { level: 1, name: "Đã cấp bài thi" })).toBeVisible();

      const examsBeforeExcess = await getMyExams(studentPage);
      const grantedBefore = examsBeforeExcess.find((item) => item.code === "DS-HSA-PART-MATH-MINI");
      expect(grantedBefore?.attemptsLeft).toBe(3);

      // 3. Bắn lại IPN thành công của lần 1 -> đơn đã FULFILLED nên lần 1 ghi nhận AFTER_COUNTED (khoản thu thừa)
      const lateAttempt1IpnRes = await studentPage.request.get(`${API_URL}${payment1SuccessIpnUrl!}`);
      expect(lateAttempt1IpnRes.status(), await lateAttempt1IpnRes.text()).toBe(200);
      const lateAttempt1Json = (await lateAttempt1IpnRes.json()) as { RspCode: string };
      expect(lateAttempt1Json.RspCode).toBe("00");

      await expect
        .poll(
          async () => {
            const detail = await fetchAdminOrderDetail(admin.api, orderCode);
            const p1 = detail.payments.find((p) => p.txnRef === txnRef1);
            return p1 ? `${p1.duplicate}:${p1.excessReason ?? ""}` : "";
          },
          { message: "payment 1 flagged as AFTER_COUNTED duplicate", timeout: 15_000 },
        )
        .toBe("true:AFTER_COUNTED");

      // 4. Admin lọc đơn cần chú ý trên /orders -> mở /orders/[code] -> hoàn riêng giao dịch lần 1
      const adminCtx = await browser.newContext();
      try {
        await loginAdmin(adminCtx, "vi");
        const adminPage = await adminCtx.newPage();
        adminPage.setDefaultTimeout(25_000);

        await adminPage.goto(`${ADMIN_URL}/orders`);
        const duplicateAttentionBtn = adminPage.getByRole("button", { name: /Thanh toán dư chưa hoàn/ }).first();
        await expect(duplicateAttentionBtn).toBeVisible({ timeout: 20_000 });
        await duplicateAttentionBtn.click();

        const orderLink = adminPage.getByRole("link", { name: orderCode, exact: true }).first();
        await expect(orderLink).toBeVisible({ timeout: 20_000 });
        await orderLink.click();

        await adminPage.waitForURL(new RegExp(`/orders/${orderCode}`));
        await expect(adminPage.getByRole("heading", { name: new RegExp(orderCode) })).toBeVisible();
        await expect(adminPage.getByText(txnRef1).first()).toBeVisible();
        await expect(adminPage.getByText(/Thu thừa \(cần hoàn\)|Thu thừa sau khi đơn đã ghi nhận đủ/).first()).toBeVisible();

        const payment1RefundBtn = adminPage
          .getByRole("button", {
            name: new RegExp(`Hoàn tiền giao dịch lần .*${txnRef1.replace(/[.*+?^${}()|[\]\\]/g, "\\$&")}`),
          })
          .first();
        await expect(payment1RefundBtn).toBeVisible();
        await payment1RefundBtn.click();

        const dialog = adminPage.getByRole("dialog");
        await expect(dialog).toBeVisible();
        await expect(dialog.getByText("Khoản thu thừa cần hoàn trả").first()).toBeVisible();
        await dialog.getByLabel(/Xác nhận đây là lỗi hệ thống/).check();
        await dialog.getByLabel(/Lý do hoàn tiền/).fill("Hoan rieng khoan thu thua giao dich lan 1 theo BS 14");
        await dialog.getByRole("button", { name: "Gửi yêu cầu hoàn tiền" }).click();
        await expect(dialog).toBeHidden({ timeout: 15_000 });

        await expect
          .poll(
            async () => {
              const detail = await fetchAdminOrderDetail(admin.api, orderCode);
              const p1 = detail.payments.find((p) => p.txnRef === txnRef1);
              return `${detail.state}:${p1?.refundedAmountVnd ?? 0}:${p1?.refunds.length ?? 0}`;
            },
            { message: "payment 1 refunded while order remains FULFILLED", timeout: 20_000 },
          )
          .not.toMatch(/:0:0$/);

        const afterRefundDetail = await fetchAdminOrderDetail(admin.api, orderCode);
        expect(afterRefundDetail.state).toBe("FULFILLED");
        expect(afterRefundDetail.grants).toHaveLength(1);
        const payment1 = afterRefundDetail.payments.find((p) => p.txnRef === txnRef1);
        const payment2 = afterRefundDetail.payments.find((p) => p.txnRef === txnRef2);
        expect(payment1?.refundedAmountVnd).toBeGreaterThan(0);
        expect(payment1?.refunds).toHaveLength(1);
        expect(payment2?.refundedAmountVnd).toBe(0);
        expect(payment2?.refunds).toHaveLength(0);
      } finally {
        await adminCtx.close();
      }

      // 5. Học viên vẫn giữ nguyên FULFILLED và 3 lượt làm bài
      const examsAfterRefund = await getMyExams(studentPage);
      const grantedAfter = examsAfterRefund.find((item) => item.code === "DS-HSA-PART-MATH-MINI");
      expect(grantedAfter?.attemptsLeft).toBe(3);

      // 6. Replay IPN lần 1 thêm lần nữa -> gateway idempotency trả RspCode=02, không sinh thêm payment/refund
      const replayAgainRes = await studentPage.request.get(`${API_URL}${payment1SuccessIpnUrl!}`);
      expect(replayAgainRes.status(), await replayAgainRes.text()).toBe(200);
      const replayAgainJson = (await replayAgainRes.json()) as { RspCode: string };
      expect(replayAgainJson.RspCode).toBe("02");

      const finalDetail = await fetchAdminOrderDetail(admin.api, orderCode);
      expect(finalDetail.state).toBe("FULFILLED");
      expect(finalDetail.payments).toHaveLength(2);
      const finalPayment1 = finalDetail.payments.find((p) => p.txnRef === txnRef1);
      expect(finalPayment1?.refunds).toHaveLength(1);
    } finally {
      await admin.api.dispose();
      await studentCtx.close();
    }
  });
});
