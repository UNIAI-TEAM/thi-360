import { expect, test } from "@playwright/test";
import { createVerifiedAccount, WEB_URL } from "./helpers/mail";
import {
  advanceTestClock,
  answerCurrentUnit,
  ensureDsReady,
  getAttemptStateApi,
  getTestClockNow,
  publishThptqgMath3PartExam,
  purchaseExamViaUi,
  resetTestClock,
  resolveActiveDeadlineIso,
  startAttemptFromPrepare,
  waitForSavedStatus,
} from "./helpers/attempt-fixtures";

test.describe("Luồng chuẩn bị & làm bài (AC-1, AC-9, AC-13, AC-33)", () => {
  test.setTimeout(240_000);

  test.beforeAll(async ({ browser }) => {
    test.setTimeout(300_000);
    await ensureDsReady(browser);
  });

  test("AC-1: màn chuẩn bị DS-HSA-FULL-MINI (và 404 khác tài khoản) -> bắt đầu lượt -> làm Phần 1 -> trạng thái Đã lưu", async ({
    browser,
    context,
    page,
  }) => {
    const { cookies } = await createVerifiedAccount("flow.ac1");
    await context.addCookies(cookies);

    const { entitlementId } = await purchaseExamViaUi(page, "DS-HSA-FULL-MINI");

    const { cookies: otherCookies } = await createVerifiedAccount("flow.ac1.other");
    const otherContext = await browser.newContext();
    try {
      await otherContext.addCookies(otherCookies);
      const otherPage = await otherContext.newPage();
      await otherPage.goto(`${WEB_URL}/prepare/${encodeURIComponent(entitlementId)}`);
      await expect(
        otherPage.getByRole("heading", { name: "Không tìm thấy bài thi trong tài khoản đang đăng nhập" }),
      ).toBeVisible({ timeout: 20_000 });
      await expect(otherPage.getByRole("button", { name: "Bắt đầu bài thi" })).toHaveCount(0);
    } finally {
      await otherContext.close();
    }

    await page.goto(`${WEB_URL}/prepare/${encodeURIComponent(entitlementId)}`);

    await expect(page.locator("h1[data-exam-content]")).toBeVisible();
    await expect(page.getByText("Bài thi FULL")).toBeVisible();
    await expect(page.locator('aside[aria-labelledby="sidebar-title"]')).toContainText("Sẵn sàng làm bài");
    await expect(page.getByRole("heading", { name: "Cấu trúc bài thi" })).toBeVisible();
    await expect(page.getByRole("heading", { name: "Hướng dẫn làm bài" })).toBeVisible();

    await page.getByRole("button", { name: "Bắt đầu bài thi" }).click();
    await page.waitForURL(/\/attempts\/[0-9a-f-]+$/i, { timeout: 25_000 });

    const nav = page.locator('nav[aria-label="Các phần của bài thi"]');
    await expect(nav).toBeVisible({ timeout: 30_000 });
    await expect(page.locator('[role="tablist"]')).toHaveCount(0);
    await expect(nav).toContainText("Đang làm");
    await expect(nav).toContainText("Đã khóa");

    const timer = page.locator('header [role="timer"]');
    await expect(timer).toBeVisible();
    await expect(timer).toHaveText(/^\d{1,2}:\d{2}(:\d{2})?$/);

    await answerCurrentUnit(page);
    await page.getByRole("button", { name: "Câu sau" }).click();
    await answerCurrentUnit(page);
    await waitForSavedStatus(page);

    await expect(page.locator('aside[aria-label="Danh sách câu hỏi"] button[data-completion="complete"]').first()).toBeVisible();
  });

  test("AC-9: tuần tự 3 phần DS-HSA-FULL-MINI -> chọn 3 môn Khoa học Phần 3 -> Nộp bài -> Lượt thi đã đóng -> Xem kết quả", async ({
    context,
    page,
  }) => {
    const { cookies } = await createVerifiedAccount("flow.ac9");
    await context.addCookies(cookies);

    const { entitlementId } = await purchaseExamViaUi(page, "DS-HSA-FULL-MINI");
    const attemptId = await startAttemptFromPrepare(page, entitlementId);

    const partNav = page.locator('nav[aria-label="Các phần của bài thi"]');
    await expect(partNav).toBeVisible();

    // Phần 1
    await answerCurrentUnit(page);
    await waitForSavedStatus(page);
    await page.getByRole("button", { name: "Hoàn thành phần" }).click();
    const finishDialog1 = page.getByRole("dialog");
    await expect(finishDialog1).toBeVisible();
    await finishDialog1.getByRole("button", { name: "Hoàn thành phần" }).click();

    await expect(partNav).toContainText("Đã hoàn thành", { timeout: 20_000 });
    await answerCurrentUnit(page);
    await waitForSavedStatus(page);

    // Phần 2 -> mở bước chọn Phần 3
    await page.getByRole("button", { name: "Hoàn thành phần" }).click();
    const finishDialog2 = page.getByRole("dialog");
    await expect(finishDialog2).toBeVisible();
    await finishDialog2.getByRole("button", { name: "Hoàn thành phần" }).click();

    await expect(page.getByRole("heading", { name: "Chọn phần thi thứ ba" })).toBeVisible({ timeout: 20_000 });
    await page.locator('input[type="radio"][name="attempt-part-choice"][value="SCIENCE"]').check();

    const checkboxes = page.locator('main input[type="checkbox"]:not(:disabled), main button[role="checkbox"]:not([disabled])');
    const cbTotal = await checkboxes.count();
    expect(cbTotal).toBeGreaterThanOrEqual(3);
    for (let i = 0; i < 3; i += 1) {
      await checkboxes.nth(i).click();
    }
    await expect(page.getByText("Đã chọn 3/3")).toBeVisible();
    await page.getByRole("button", { name: "Xác nhận chọn phần 3" }).click();

    await expect(page.getByRole("heading", { name: "Lượt thi đã đóng" })).toHaveCount(0);
    await expect(page.locator("main fieldset")).toBeVisible({ timeout: 45_000 });
    await expect(page.getByRole("button", { name: "Hoàn thành phần" })).toHaveCount(0);

    await answerCurrentUnit(page);
    await waitForSavedStatus(page);

    await page.getByRole("button", { name: "Nộp bài" }).click();
    const submitDialog = page.getByRole("dialog");
    await expect(submitDialog.getByRole("heading", { name: "Nộp bài?" })).toBeVisible();
    await expect(submitDialog).toContainText("Còn thiếu");
    await submitDialog.getByRole("button", { name: "Nộp bài" }).click();

    await expect(page.getByRole("heading", { name: "Lượt thi đã đóng" })).toBeVisible({ timeout: 20_000 });
    await expect(page.locator("main input:not([disabled]), main [contenteditable='true']")).toHaveCount(0);

    const viewResultLink = page.getByRole("link", { name: "Xem kết quả" });
    await expect(viewResultLink).toHaveAttribute("href", `/attempts/${attemptId}/result`);
    await viewResultLink.click();
    await expect(page).toHaveURL(/\/attempts\/[0-9a-f-]+\/result/i);
  });

  test("AC-13: hết giờ theo TestClock trên DS-BCA-FULL-MINI SHARED -> máy chủ tự đóng lượt, không cho sửa sau hạn", async ({
    context,
    page,
  }) => {
    const { cookies } = await createVerifiedAccount("flow.ac13");
    await context.addCookies(cookies);

    const { entitlementId } = await purchaseExamViaUi(page, "DS-BCA-FULL-MINI");
    const attemptId = await startAttemptFromPrepare(page, entitlementId, { preChoiceCode: "CA1" });

    try {
      await answerCurrentUnit(page);
      await waitForSavedStatus(page);

      // Hoàn thành Phần 1 (ESSAY) để mở Phần 2 (SINGLE_CHOICE OPEN) trước khi cho hết giờ toàn bài
      await page.getByRole("button", { name: "Hoàn thành phần" }).click();
      await page.getByRole("dialog").getByRole("button", { name: "Hoàn thành phần" }).click();

      await expect(page.locator('nav[aria-label="Các phần của bài thi"]')).toContainText("Đã hoàn thành", {
        timeout: 20_000,
      });
      await expect(page.locator("main fieldset")).toBeVisible({ timeout: 20_000 });

      const state2 = await getAttemptStateApi(page.request, attemptId);
      const activeDeadlineIso = resolveActiveDeadlineIso(state2);
      expect(activeDeadlineIso).toBeTruthy();
      const clockNow = Date.parse(await getTestClockNow(page.request));
      const deadlineMs = Date.parse(activeDeadlineIso!);
      const stepSeconds = Math.max(60, Math.ceil((deadlineMs - clockNow) / 1000) + 60);
      await advanceTestClock(page.request, stepSeconds);

      await page.reload();
      await expect(page.getByRole("heading", { name: "Đã hết giờ làm bài" })).toBeVisible({ timeout: 20_000 });
      await expect(page.locator("main input:not([disabled]), main [contenteditable='true']")).toHaveCount(0);

      const viewResultLink = page.getByRole("link", { name: "Xem kết quả" });
      await expect(viewResultLink).toHaveAttribute("href", `/attempts/${attemptId}/result`);
      await viewResultLink.click();
      await expect(page).toHaveURL(/\/attempts\/[0-9a-f-]+\/result/i);
    } finally {
      await resetTestClock(page.request);
    }
  });

  test("AC-33: đề THPTQG Toán 3 phần điều hướng tự do (FREE) -> nhảy tab Phần 3 <-> 2 <-> 1, lưu & khôi phục sau reload", async ({
    context,
    page,
  }) => {
    const thptqgExam = await publishThptqgMath3PartExam("free");
    const { cookies } = await createVerifiedAccount("flow.ac33");
    await context.addCookies(cookies);

    const { entitlementId } = await purchaseExamViaUi(page, thptqgExam.code);
    const attemptId = await startAttemptFromPrepare(page, entitlementId);

    await expect(page.getByRole("button", { name: "Hoàn thành phần" })).toHaveCount(0);
    await expect(page.locator('header [role="timer"]')).toHaveAttribute("aria-label", /Cả bài còn/);

    const tablist = page.locator('[role="tablist"][aria-label="Các phần của bài thi"]');
    await expect(tablist).toBeVisible();
    const tabs = tablist.locator('[role="tab"]');
    await expect(tabs).toHaveCount(3);

    // Nhảy thẳng sang Phần III · Trả lời ngắn
    await tabs.nth(2).click();
    await expect(tabs.nth(2)).toHaveAttribute("aria-selected", "true");
    await answerCurrentUnit(page, "12.5");
    await page.getByRole("button", { name: "Câu sau" }).click();
    await waitForSavedStatus(page);

    // Nhảy về Phần II · Đúng/Sai
    await tabs.nth(1).click();
    await expect(tabs.nth(1)).toHaveAttribute("aria-selected", "true");
    await answerCurrentUnit(page);
    await waitForSavedStatus(page);

    // Nhảy về Phần I · Trắc nghiệm
    await tabs.nth(0).click();
    await expect(tabs.nth(0)).toHaveAttribute("aria-selected", "true");
    await answerCurrentUnit(page);
    await waitForSavedStatus(page);

    // Reload và xác nhận câu trả lời đã lưu ở cả 3 phần
    await page.reload();
    await expect(tabs).toHaveCount(3);

    await tabs.nth(2).click();
    const p3Input = page.locator('main fieldset input:not([type="radio"]):not([type="checkbox"]):not([type="hidden"])').first();
    await expect(p3Input).toHaveValue("12.5");

    await tabs.nth(1).click();
    const p2Checked = page.locator('main fieldset input[type="radio"]:checked, main fieldset [role="radio"][aria-checked="true"]');
    expect(await p2Checked.count()).toBeGreaterThan(0);

    await tabs.nth(0).click();
    const p1Checked = page.locator('main fieldset input[type="radio"]:checked, main fieldset [role="radio"][aria-checked="true"]');
    expect(await p1Checked.count()).toBeGreaterThan(0);

    await page.getByRole("button", { name: "Nộp bài" }).click();
    const submitDialog = page.getByRole("dialog");
    await submitDialog.getByRole("button", { name: "Nộp bài" }).click();

    await expect(page.getByRole("heading", { name: "Lượt thi đã đóng" })).toBeVisible({ timeout: 20_000 });
    await page.getByRole("link", { name: "Xem kết quả" }).click();
    await expect(page).toHaveURL(new RegExp(`/attempts/${attemptId}/result`, "i"));
  });
});
