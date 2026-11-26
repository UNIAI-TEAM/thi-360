import { expect, test, type Page } from "@playwright/test";

import {
  answerCurrentUnit,
  ensureDsReady,
  getMyExams,
  waitForSavedStatus,
} from "./helpers/attempt-fixtures";
import { RUN_ID, WEB_URL } from "./helpers/editor-qa";
import { fillRegisterForm, newAccount, readOtp, waitForMail } from "./helpers/mail";
import { examByCode } from "./helpers/published-exam-set";

/**
 * AC-4: Khách vãng lai -> xem catalog `/` -> mở chi tiết `DS-HSA-FULL-MINI` ->
 * đăng ký UI (`QA-` prefix) + xác minh OTP -> thanh toán `/mock-vnpay` ->
 * `/home` -> `/prepare` -> hoàn thành 3 phần tuần tự (Phần 3 chọn `SCIENCE` + 3 môn) ->
 * `/attempts/[id]/result` SCORED -> `/history` 1 dòng dẫn lại `/result`.
 */

interface AttemptResultSummary {
  attemptId: string;
  state: string;
  rawScore: number | null;
  displayScore: number | null;
}

async function answerAllUnitsInCurrentPart(page: Page): Promise<void> {
  const fieldset = page.locator("main fieldset[data-attempt-question-fieldset]").first();
  await expect(fieldset).toBeVisible({ timeout: 30_000 });
  await expect(fieldset).not.toHaveAttribute("disabled", "", { timeout: 30_000 });
  await answerCurrentUnit(page);
  await waitForSavedStatus(page);
}

async function completeCurrentSequentialPart(page: Page, expectedCompletedCount: number): Promise<void> {
  await answerAllUnitsInCurrentPart(page);
  await page.getByRole("button", { name: "Hoàn thành phần" }).click();
  const finishDialog = page.getByRole("dialog", { name: "Hoàn thành phần?" });
  await expect(finishDialog).toBeVisible();
  await finishDialog.getByRole("button", { name: "Hoàn thành phần" }).click();
  await expect(finishDialog).toBeHidden({ timeout: 20_000 });
  const partNav = page.locator('nav[aria-label="Các phần của bài thi"]');
  await expect
    .poll(async () => ((await partNav.textContent()) ?? "").match(/Đã hoàn thành/g)?.length ?? 0, {
      timeout: 25_000,
      message: `Đợi có ${expectedCompletedCount} phần Đã hoàn thành`,
    })
    .toBeGreaterThanOrEqual(expectedCompletedCount);
}

test.describe("QA Core Flow — AC-4: Guest đăng ký, mua và hoàn thành DS-HSA-FULL-MINI", () => {
  test.setTimeout(180_000);

  test.beforeAll(async ({ browser }) => {
    await ensureDsReady(browser);
  });

  test("AC-4: khách đăng ký mới + OTP + VNPAY + thi tuần tự 3 phần HSA + kết quả & lịch sử", async ({
    context,
    page,
  }) => {
    await context.addCookies([{ name: "thi360_locale", value: "vi", url: WEB_URL }]);

    const exam = examByCode("DS-HSA-FULL-MINI");
    const baseAccount = newAccount("qa.hsa");
    const account = {
      ...baseAccount,
      fullName: `QA-Hsa Student ${RUN_ID}`,
    };

    // 1. Guest truy cập `/` -> lọc catalog `#exams` -> mở chi tiết `/exams/DS-HSA-FULL-MINI`
    await page.goto(`${WEB_URL}/`, { waitUntil: "domcontentloaded" });
    await expect(page.getByRole("heading", { name: "Chọn bài thi" })).toBeVisible({ timeout: 20_000 });

    const catalogSearch = page.getByRole("searchbox", { name: "Tìm theo tên hoặc mã" });
    await catalogSearch.fill(exam.code);

    const examCard = page
      .getByRole("article")
      .filter({ has: page.getByRole("heading", { name: exam.name }) })
      .first();
    await expect(examCard).toBeVisible({ timeout: 20_000 });
    await examCard.locator(`a[href*="/exams/${encodeURIComponent(exam.code)}"]`).first().click();
    await expect(page).toHaveURL(new RegExp(`/exams/${exam.code}$`));
    await expect(page.getByRole("heading", { level: 1, name: exam.name })).toBeVisible();

    // 2. Nhấn Mua bài thi (guest chuyển qua /login?callbackUrl=...) -> sang /register giữ callbackUrl -> xác minh OTP
    const buyPanel = page.getByRole("complementary");
    await buyPanel.getByRole("link", { name: "Mua bài thi" }).click();
    await expect(page).toHaveURL(/\/login\?callbackUrl=/);

    await page.getByRole("main").getByRole("link", { name: "Đăng ký" }).click();
    await expect(page).toHaveURL(/\/register\?callbackUrl=/);
    await fillRegisterForm(page, account);
    await page.getByRole("button", { name: "Đăng ký" }).click();

    await expect(page.getByRole("heading", { name: "Xác minh email", level: 1 })).toBeVisible({ timeout: 20_000 });

    const verifyMail = await waitForMail(account.email, "EMAIL_VERIFY");
    const otp = readOtp(verifyMail);
    expect(otp).toMatch(/^\d{6}$/);

    await page.getByLabel(/^Mã xác minh/).fill(otp);
    await page.getByRole("button", { name: "Xác minh", exact: true }).click();

    // 3. Tự động quay về `/checkout/DS-HSA-FULL-MINI` -> thanh toán qua /mock-vnpay -> kết quả đã cấp bài thi
    await expect(page).toHaveURL(new RegExp(`/checkout/${exam.code}`), { timeout: 25_000 });
    const vnpayBtn = page.getByRole("button", { name: "Thanh toán qua VNPAY" });
    await expect(vnpayBtn).toBeVisible();
    await vnpayBtn.click();

    await page.waitForURL(/\/mock-vnpay/, { timeout: 20_000 });
    await page.locator('a[data-mock-action="success"]').click();

    await page.waitForURL(/\/checkout\/result/, { timeout: 25_000 });
    await expect(page.getByRole("heading", { level: 1, name: "Đã cấp bài thi" })).toBeVisible();

    // 4. Vào Bài thi của tôi (`/home`) -> kiểm tra card & lượt -> vào `/prepare/[entitlementId]` -> bắt đầu làm bài
    await page.getByRole("link", { name: "Vào Bài thi của tôi" }).click();
    await expect(page).toHaveURL(`${WEB_URL}/home`);
    await expect(page.getByRole("heading", { level: 1, name: "Bài thi của tôi" })).toBeVisible();
    await expect(page.getByRole("heading", { name: exam.name })).toBeVisible();

    const myExams = await getMyExams(page);
    const ownedExam = myExams.find((item) => item.code === exam.code);
    if (!ownedExam?.entitlementId) {
      throw new Error(`Không tìm thấy entitlementId của ${exam.code} trên /home`);
    }
    expect(ownedExam.attemptsLeft).toBe(exam.listing.attemptsGranted);

    await page.locator(`a[href="/prepare/${encodeURIComponent(ownedExam.entitlementId)}"]`).click();
    await expect(page).toHaveURL(new RegExp(`/prepare/${ownedExam.entitlementId}$`));
    await page.getByRole("button", { name: "Bắt đầu bài thi" }).click();
    await page.waitForURL(/\/attempts\/([0-9a-f-]+)$/i, { timeout: 30_000 });

    const attemptMatch = /\/attempts\/([0-9a-f-]+)$/i.exec(page.url());
    if (!attemptMatch?.[1]) {
      throw new Error(`Không tách được attemptId từ URL ${page.url()}`);
    }
    const attemptId = attemptMatch[1];

    const partNav = page.locator('nav[aria-label="Các phần của bài thi"]');
    await expect(partNav).toBeVisible({ timeout: 30_000 });

    // 5. Hoàn thành Phần 1 & Phần 2 tuần tự -> chọn Phần 3 (SCIENCE + 3 môn) -> trả lời & Nộp bài
    await completeCurrentSequentialPart(page, 1);

    await completeCurrentSequentialPart(page, 2);

    await expect(page.getByRole("heading", { name: "Chọn phần thi thứ ba" })).toBeVisible({
      timeout: 20_000,
    });
    await page.locator('input[type="radio"][name="attempt-part-choice"][value="SCIENCE"]').check();

    const subjectCheckboxes = page.locator(
      'main input[type="checkbox"]:not(:disabled), main button[role="checkbox"]:not([disabled])',
    );
    await expect(subjectCheckboxes.nth(2)).toBeVisible({ timeout: 10_000 });
    for (let i = 0; i < 3; i += 1) {
      await subjectCheckboxes.nth(i).check();
    }
    await expect(page.getByText("Đã chọn 3/3")).toBeVisible();
    await page.getByRole("button", { name: "Xác nhận chọn phần 3" }).click();

    await answerAllUnitsInCurrentPart(page);
    await page.getByRole("button", { name: "Nộp bài" }).click();
    const submitDialog = page.getByRole("dialog", { name: "Nộp bài?" });
    await expect(submitDialog).toBeVisible();
    await submitDialog.getByRole("button", { name: "Nộp bài" }).click();
    await expect(submitDialog).toBeHidden({ timeout: 20_000 });

    await expect(page.getByRole("heading", { name: "Lượt thi đã đóng" })).toBeVisible({
      timeout: 30_000,
    });

    // 6. Xem kết quả -> đợi SCORED -> xác nhận hiển thị điểm số trên UI
    const resultLink = page.getByRole("link", { name: "Xem kết quả" });
    await expect(resultLink).toHaveAttribute("href", `/attempts/${attemptId}/result`);
    await resultLink.click();
    await expect(page).toHaveURL(new RegExp(`/attempts/${attemptId}/result$`));

    let scoredResult: AttemptResultSummary | undefined;
    await expect
      .poll(
        async () => {
          const res = await context.request.get(`${WEB_URL}/api/v1/me/attempts/${attemptId}/result`);
          if (!res.ok()) return "";
          scoredResult = (await res.json()) as AttemptResultSummary;
          return scoredResult.state;
        },
        { timeout: 45_000, intervals: [300, 700, 1000], message: "Đợi kết quả chuyển SCORED" },
      )
      .toBe("SCORED");

    await page.reload();
    await expect(page.getByRole("heading", { level: 1, name: exam.name })).toBeVisible();
    await expect(page.getByText("Đã chấm").first()).toBeVisible({ timeout: 20_000 });

    const resolvedScore = scoredResult?.displayScore ?? scoredResult?.rawScore;
    expect(resolvedScore).not.toBeNull();
    const formattedViScore = new Intl.NumberFormat("vi-VN", { maximumFractionDigits: 2 }).format(
      Number(resolvedScore),
    );
    await expect(page.locator("main")).toContainText(formattedViScore);

    // 7. Vào `/history` -> xác nhận đúng 1 dòng của `DS-HSA-FULL-MINI` dẫn về `/attempts/[id]/result`
    await page.goto(`${WEB_URL}/history`);
    await expect(page.getByRole("heading", { level: 1, name: "Lịch sử luyện thi" })).toBeVisible();

    const rows = page.locator("tbody tr");
    await expect(rows).toHaveCount(1);
    const historyRow = rows.first();
    await expect(historyRow).toContainText(exam.name);
    await expect(historyRow).toContainText("Đã chấm");

    const rowResultLink = historyRow.getByRole("link", { name: "Xem kết quả" });
    await expect(rowResultLink).toHaveAttribute("href", `/attempts/${attemptId}/result`);
    await rowResultLink.click();
    await expect(page).toHaveURL(new RegExp(`/attempts/${attemptId}/result$`));
  });
});
