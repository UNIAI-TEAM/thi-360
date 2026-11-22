import { expect, test, type Page } from "@playwright/test";

import {
  answerCurrentUnit,
  ensureDsReady,
  getTestClockNow,
  purchaseExamViaUi,
  startAttemptFromPrepare,
  waitForSavedStatus,
} from "./helpers/attempt-fixtures";
import { adminApi, ADMIN_URL, loginAdmin, RUN_ID, WEB_URL } from "./helpers/editor-qa";
import { createVerifiedAccount } from "./helpers/mail";

/**
 * AC-6: Student hoàn thành `DS-BCA-FULL-MINI` (`preChoiceCode: CA1`, Phần 1 ESSAY + các phần sau) ->
 * `/attempts/[id]/result` ở trạng thái `PENDING_REVIEW` (`Chờ chấm`, `displayScore: null`) ->
 * Admin vào `/grading` xác nhận ẩn danh, mở `#shortCode`, bấm `Nhận câu để chấm`, nhập điểm & nhận xét,
 * bấm `Hoàn tất chấm` -> Student reload `/attempts/[id]/result` nhận `SCORED` (`Đã chấm`) kèm điểm số.
 */

interface AttemptResultSummary {
  attemptId: string;
  state: string;
  rawScore: number | null;
  displayScore: number | null;
}

interface GradingQueueResponse {
  items: Array<{
    itemId: string;
    attemptId: string;
    state: string;
  }>;
}

interface GradingDetailResponse {
  itemId: string;
  pointsMax: number;
  rubric: Array<{ id: string; description: string; points: number }>;
}

function toViInput(value: number): string {
  return String(Number(value.toFixed(2))).replace(".", ",");
}

async function answerAllUnitsInCurrentPart(page: Page, fallbackText: string): Promise<void> {
  const fieldset = page.locator("main fieldset[data-attempt-question-fieldset]").first();
  await expect(fieldset).toBeVisible({ timeout: 30_000 });
  await expect(fieldset).not.toHaveAttribute("disabled", "", { timeout: 30_000 });
  const nextBtn = page.getByRole("button", { name: "Câu sau" });
  for (let step = 0; step < 20; step += 1) {
    await answerCurrentUnit(page, fallbackText);
    if ((await nextBtn.isVisible()) && (await nextBtn.isEnabled())) {
      await nextBtn.click();
      await expect(fieldset).toBeVisible({ timeout: 20_000 });
      await expect(fieldset).not.toHaveAttribute("disabled", "", { timeout: 20_000 });
    } else {
      break;
    }
  }
  await waitForSavedStatus(page);
}

test.describe("QA Core Flow — AC-6: Chấm tự luận BCA ẩn danh tới SCORED", () => {
  test.setTimeout(180_000);

  test.beforeAll(async ({ browser }) => {
    await ensureDsReady(browser);
  });

  test("AC-6: student nộp BCA có tự luận -> Chờ chấm -> admin chấm ẩn danh -> student thấy Đã chấm & điểm số", async ({
    browser,
  }) => {
    const admin = await adminApi();
    const { account, cookies } = await createVerifiedAccount("qa.bca");
    const essayMarker = `QA-BCA-ESSAY-${RUN_ID} lap luan mach lac va dan chung cu the.`;

    const studentCtx = await browser.newContext();
    const adminCtx = await browser.newContext();
    try {
      await studentCtx.addCookies(cookies);
      await studentCtx.addCookies([{ name: "thi360_locale", value: "vi", url: WEB_URL }]);
      const studentPage = await studentCtx.newPage();

      // 1. Student mua & thi `DS-BCA-FULL-MINI` với CA1
      const { entitlementId } = await purchaseExamViaUi(studentPage, "DS-BCA-FULL-MINI");
      const attemptId = await startAttemptFromPrepare(studentPage, entitlementId, { preChoiceCode: "CA1" });
      const shortCode = attemptId.slice(0, 8).toUpperCase();

      const finishPartBtn = studentPage.getByRole("button", { name: "Hoàn thành phần" });
      const submitBtn = studentPage.getByRole("button", { name: "Nộp bài" });

      // Phần 1 (ESSAY)
      await answerCurrentUnit(studentPage, essayMarker);
      await waitForSavedStatus(studentPage);
      await finishPartBtn.click();
      const finishDialog1 = studentPage.getByRole("dialog", { name: "Hoàn thành phần?" });
      await expect(finishDialog1).toBeVisible();
      await finishDialog1.getByRole("button", { name: "Hoàn thành phần" }).click();
      await expect(finishDialog1).toBeHidden({ timeout: 20_000 });

      // Các phần tuần tự tiếp theo trước khi đến phần cuối
      await expect(finishPartBtn.or(submitBtn)).toBeVisible({ timeout: 30_000 });
      while (await finishPartBtn.isVisible()) {
        await answerAllUnitsInCurrentPart(studentPage, "1");
        await finishPartBtn.click();
        const finishDialog = studentPage.getByRole("dialog", { name: "Hoàn thành phần?" });
        await expect(finishDialog).toBeVisible();
        await finishDialog.getByRole("button", { name: "Hoàn thành phần" }).click();
        await expect(finishDialog).toBeHidden({ timeout: 20_000 });
        await expect(finishPartBtn.or(submitBtn)).toBeVisible({ timeout: 30_000 });
      }

      // Phần cuối -> Nộp bài
      await answerAllUnitsInCurrentPart(studentPage, "1");

      await submitBtn.click();
      const submitDialog = studentPage.getByRole("dialog", { name: "Nộp bài?" });
      await expect(submitDialog).toBeVisible();
      await submitDialog.getByRole("button", { name: "Nộp bài" }).click();
      await expect(submitDialog).toBeHidden({ timeout: 20_000 });

      await expect(studentPage.getByRole("heading", { name: "Lượt thi đã đóng" })).toBeVisible({
        timeout: 30_000,
      });

      // 2. Mở kết quả -> xác nhận `Chờ chấm` & API `PENDING_REVIEW` (`displayScore: null`)
      await studentPage.getByRole("link", { name: "Xem kết quả" }).click();
      await expect(studentPage).toHaveURL(new RegExp(`/attempts/${attemptId}/result$`));

      let pendingSummary: AttemptResultSummary | undefined;
      await expect
        .poll(
          async () => {
            const res = await studentCtx.request.get(`${WEB_URL}/api/v1/me/attempts/${attemptId}/result`);
            if (!res.ok()) return "";
            pendingSummary = (await res.json()) as AttemptResultSummary;
            return pendingSummary.state;
          },
          { timeout: 30_000, message: "Đợi kết quả chuyển PENDING_REVIEW" },
        )
        .toBe("PENDING_REVIEW");

      expect(pendingSummary?.displayScore).toBeNull();
      await studentPage.reload();
      await expect(studentPage.getByText("Chờ chấm").first()).toBeVisible();

      // Đợi item xuất hiện trong hàng chờ chấm trước khi mở UI admin
      let itemId: string | undefined;
      await expect
        .poll(
          async () => {
            const queueRes = await admin.api.get(`${ADMIN_URL}/api/v1/admin/grading/items?state=WAITING&size=50`);
            if (!queueRes.ok()) return undefined;
            const queue = (await queueRes.json()) as GradingQueueResponse;
            itemId = queue.items.find((item) => item.attemptId === attemptId)?.itemId;
            return itemId;
          },
          { timeout: 25_000, message: `Tìm câu tự luận của lượt ${attemptId}` },
        )
        .toBeTruthy();

      // 3. Admin mở `/grading`, kiểm tra ẩn danh, tìm `#shortCode`, nhận câu và hoàn tất chấm
      await loginAdmin(adminCtx, "vi");
      const adminPage = await adminCtx.newPage();

      await adminPage.goto(`${ADMIN_URL}/grading`);
      await expect(adminPage.getByRole("heading", { level: 1, name: "Chấm tự luận" })).toBeVisible();

      const queueTable = adminPage.getByRole("table", { name: "Danh sách câu tự luận cần chấm" });
      await expect(queueTable).toBeVisible();

      let targetRow = queueTable.getByRole("link", { name: new RegExp(`#${shortCode}`) });
      if ((await targetRow.count()) === 0) {
        const today = new Intl.DateTimeFormat("en-CA", { timeZone: "Asia/Ho_Chi_Minh" }).format(
          new Date(await getTestClockNow()),
        );
        await adminPage.getByLabel("Bài thi").fill("BCA");
        await adminPage.getByLabel("Từ ngày").fill(today);
        await adminPage.getByLabel("Đến ngày").fill(today);
        targetRow = queueTable.getByRole("link", { name: new RegExp(`#${shortCode}`) });
      }

      await expect(targetRow).toBeVisible({ timeout: 20_000 });
      await expect(adminPage.locator("main")).not.toContainText(account.username);
      await expect(adminPage.locator("main")).not.toContainText(account.email);

      await targetRow.click();
      await expect(adminPage).toHaveURL(new RegExp(`/grading/${itemId}$`));
      await expect(adminPage.getByRole("heading", { level: 1, name: new RegExp(`#${shortCode}`) })).toBeVisible();
      await expect(adminPage.locator("main")).toContainText(essayMarker);
      await expect(adminPage.locator("main")).not.toContainText(account.username);
      await expect(adminPage.locator("main")).not.toContainText(account.email);

      await adminPage.getByRole("button", { name: "Nhận câu để chấm" }).click();
      const pointsInput = adminPage.getByLabel("Tổng điểm câu");
      await expect(pointsInput).toBeVisible({ timeout: 15_000 });

      const detailRes = await admin.api.get(`${ADMIN_URL}/api/v1/admin/grading/items/${itemId}`);
      expect(detailRes.status()).toBe(200);
      const detail = (await detailRes.json()) as GradingDetailResponse;

      if (detail.rubric.length > 0) {
        const rubricScores = detail.rubric.map((criterion) =>
          Number(((criterion.points ?? 0) / 2).toFixed(2)),
        );
        const totalScore = Number(rubricScores.reduce((sum, score) => sum + score, 0).toFixed(2));
        for (let i = 0; i < detail.rubric.length; i += 1) {
          await adminPage
            .getByLabel(new RegExp(`^Tiêu chí ${i + 1}\\.`))
            .fill(toViInput(rubricScores[i] ?? 0));
        }
        await pointsInput.fill(toViInput(totalScore));
      } else {
        const halfPoints = Number((detail.pointsMax / 2).toFixed(2));
        await pointsInput.fill(toViInput(halfPoints));
      }

      await adminPage.getByLabel("Nhận xét").fill(`QA-E2E Nhận xét chấm tự luận ${RUN_ID}`);
      await adminPage.getByRole("button", { name: "Hoàn tất chấm" }).click();

      const finalizeConfirm = adminPage.getByRole("region", { name: "Hoàn tất chấm câu tự luận?" });
      await expect(finalizeConfirm).toBeVisible();
      await finalizeConfirm.getByRole("button", { name: "Xác nhận hoàn tất" }).click();
      await expect(adminPage.getByRole("heading", { name: "Đã chốt điểm" })).toBeVisible({
        timeout: 20_000,
      });

      // 4. Student reload `/attempts/[id]/result` -> thấy `Đã chấm`, API `SCORED` và điểm số hiển thị
      let scoredSummary: AttemptResultSummary | undefined;
      await expect
        .poll(
          async () => {
            const res = await studentCtx.request.get(`${WEB_URL}/api/v1/me/attempts/${attemptId}/result`);
            if (!res.ok()) return "";
            scoredSummary = (await res.json()) as AttemptResultSummary;
            return scoredSummary.state;
          },
          { timeout: 30_000, message: "Đợi kết quả chuyển SCORED sau khi chấm tự luận" },
        )
        .toBe("SCORED");

      const finalScore = scoredSummary?.displayScore ?? scoredSummary?.rawScore;
      expect(finalScore).not.toBeNull();
      expect(Number(finalScore)).toBeGreaterThan(0);

      await studentPage.reload();
      await expect(studentPage.getByText("Đã chấm").first()).toBeVisible({ timeout: 20_000 });
      const formattedFinalScore = new Intl.NumberFormat("vi-VN", { maximumFractionDigits: 2 }).format(
        Number(finalScore),
      );
      await expect(studentPage.locator("main")).toContainText(formattedFinalScore);
    } finally {
      await studentCtx.close();
      await adminCtx.close();
      await admin.api.dispose();
    }
  });
});
