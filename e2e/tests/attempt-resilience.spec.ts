import { expect, test } from "@playwright/test";
import { createVerifiedAccount, submitLoginForm, WEB_URL } from "./helpers/mail";
import {
  answerCurrentUnit,
  ensureDsReady,
  getAttemptStateApi,
  parseTimerSeconds,
  purchaseExamViaUi,
  readAttemptDraftsIdb,
  startAttemptFromPrepare,
  waitForSavedStatus,
} from "./helpers/attempt-fixtures";

test.describe("Độ bền lượt thi, đồng hồ, ngoại tuyến & bảo vệ phiên (AC-14..AC-25)", () => {
  test.setTimeout(240_000);

  test.beforeAll(async ({ browser }) => {
    test.setTimeout(300_000);
    await ensureDsReady(browser);
  });

  test("AC-14: page.clock mô phỏng +30 phút trên bài >35 phút, sau reload khớp deadline máy chủ +/-5s", async ({
    browser,
    context,
    page,
  }) => {
    const { cookies } = await createVerifiedAccount("res.ac14");
    await context.addCookies(cookies);

    const { entitlementId } = await purchaseExamViaUi(page, "DS-HSA-PART-MATH-MINI");
    const attemptId = await startAttemptFromPrepare(page, entitlementId);

    const initialState = await getAttemptStateApi(page.request, attemptId);
    const serverDeadlineMs = Date.parse(initialState.activePartDeadlineAt ?? initialState.examDeadlineAt ?? "");
    const initialServerSec = Math.max(
      0,
      Math.floor((serverDeadlineMs - Date.parse(initialState.serverNow)) / 1000),
    );
    expect(initialServerSec).toBeGreaterThan(35 * 60);

    // Mở context riêng có clock cài trước lần điều hướng đầu tiên để đo chuẩn xác
    await page.close();
    const clockContext = await browser.newContext();
    try {
      await clockContext.addCookies(cookies);
      await clockContext.clock.install({ time: new Date() });
      const clockPage = await clockContext.newPage();
      await clockPage.goto(`${WEB_URL}/attempts/${encodeURIComponent(attemptId)}`);

      const submitBtn = clockPage.getByRole("button", { name: "Nộp bài" });
      const takeoverBtn = clockPage.getByRole("button", { name: "Tiếp tục tại đây" });
      await expect(submitBtn.or(takeoverBtn)).toBeVisible({ timeout: 25_000 });
      if (await takeoverBtn.isVisible()) {
        await takeoverBtn.click();
      }

      const timer = clockPage.locator('header [role="timer"]');
      await expect(timer).toBeVisible({ timeout: 20_000 });
      const beforeFastForwardSec = parseTimerSeconds(await timer.innerText());

      const jumpSeconds = 30 * 60;
      await clockPage.clock.fastForward(jumpSeconds * 1000);
      await clockPage.clock.runFor(1_500);

      const afterFastForwardSec = parseTimerSeconds(await timer.innerText());
      expect(afterFastForwardSec).toBeLessThanOrEqual(beforeFastForwardSec - jumpSeconds + 5);
      expect(afterFastForwardSec).toBeGreaterThanOrEqual(beforeFastForwardSec - jumpSeconds - 5);

      await clockPage.clock.resume();
      await clockPage.reload();
      await expect(clockPage.locator('header [role="timer"]')).toBeVisible({ timeout: 20_000 });

      const syncedState = await getAttemptStateApi(clockPage.request, attemptId);
      const syncedServerDeadline = Date.parse(
        syncedState.activePartDeadlineAt ?? syncedState.examDeadlineAt ?? "",
      );
      const expectedRemainingAfterReload = Math.max(
        0,
        Math.floor((syncedServerDeadline - Date.parse(syncedState.serverNow)) / 1000),
      );
      const uiAfterReloadSec = parseTimerSeconds(await clockPage.locator('header [role="timer"]').innerText());
      expect(Math.abs(uiAfterReloadSec - expectedRemainingAfterReload)).toBeLessThanOrEqual(5);
    } finally {
      await clockContext.close();
    }
  });

  test("AC-17 + AC-18: mất mạng lưu nháp IndexedDB, mở lại PUT khi mạng lên, khôi phục câu chưa sync", async ({
    context,
    page,
  }) => {
    const { cookies } = await createVerifiedAccount("res.ac18");
    await context.addCookies(cookies);

    const { entitlementId } = await purchaseExamViaUi(page, "DS-HSA-PART-MATH-MINI");
    const attemptId = await startAttemptFromPrepare(page, entitlementId);

    // Câu 1 đồng bộ bình thường
    await answerCurrentUnit(page);
    await waitForSavedStatus(page);

    // Chuyển Câu 2, ngắt mạng thật và làm 2 câu
    await page.getByRole("button", { name: "Câu sau" }).click();
    await context.setOffline(true);
    await answerCurrentUnit(page);
    await page.getByRole("button", { name: "Câu sau" }).click();
    await answerCurrentUnit(page);

    await expect(
      page.getByText(/Mất kết nối|Connection lost|Chưa lưu|Unsaved/).first(),
    ).toBeVisible({ timeout: 10_000 });

    const idbOffline = await readAttemptDraftsIdb(page);
    const dirtyBeforeClose = idbOffline.itemDrafts.filter(
      (record) => record.attemptId === attemptId && record.dirtyAnswer,
    );
    expect(dirtyBeforeClose.length).toBeGreaterThanOrEqual(2);

    // Đóng tab cũ rồi mở tab mới (tạm chặn PUT /answers trước khi bật lại mạng để xác nhận hydrate từ IDB trước khi PUT)
    await page.close({ runBeforeUnload: false });
    const page2 = await context.newPage();

    let holdAnswerSync = true;
    const apiEvents: Array<"GET_STATE" | "PUT_ANSWERS"> = [];
    const putBodies: Array<Record<string, unknown>> = [];

    await page2.route(`**/api/v1/me/attempts/${attemptId}`, async (route) => {
      if (route.request().method() === "GET") {
        apiEvents.push("GET_STATE");
      }
      await route.continue();
    });

    await page2.route(`**/api/v1/me/attempts/${attemptId}/answers`, async (route) => {
      if (holdAnswerSync) {
        await route.abort("failed");
        return;
      }
      apiEvents.push("PUT_ANSWERS");
      const raw = route.request().postData();
      if (raw) {
        putBodies.push(JSON.parse(raw) as Record<string, unknown>);
      }
      await route.continue();
    });

    await context.setOffline(false);
    await page2.goto(`${WEB_URL}/attempts/${encodeURIComponent(attemptId)}`);
    await expect(page2.getByRole("button", { name: "Nộp bài" })).toBeVisible({ timeout: 20_000 });

    // Câu 3 vẫn có bản nháp khôi phục ngay trước khi PUT lên server
    const navButtons = page2.locator('aside[aria-label="Danh sách câu hỏi"] button');
    expect(await page2.locator('aside[aria-label="Danh sách câu hỏi"] button[data-unsaved="true"]').count()).toBeGreaterThanOrEqual(
      1,
    );
    await navButtons.nth(2).click();
    const checkedBeforeSync = page2.locator(
      'main fieldset input[type="radio"]:checked, main fieldset [role="radio"][aria-checked="true"]',
    );
    const textBeforeSync = page2.locator(
      'main fieldset input:not([type="radio"]):not([type="checkbox"]):not([type="hidden"])',
    );
    const restoredViaRadio = (await checkedBeforeSync.count()) > 0;
    const restoredViaInput =
      (await textBeforeSync.count()) > 0 && (await textBeforeSync.first().inputValue()).length > 0;
    expect(restoredViaRadio || restoredViaInput).toBe(true);

    // Mở chặn PUT và phát sự kiện online để chạy đồng bộ tuần tự (GET state trước rồi mới PUT phần bẩn)
    apiEvents.length = 0;
    holdAnswerSync = false;
    await page2.evaluate(() => window.dispatchEvent(new Event("online")));
    await waitForSavedStatus(page2);

    const firstPutIndex = apiEvents.indexOf("PUT_ANSWERS");
    const firstGetIndex = apiEvents.indexOf("GET_STATE");
    expect(firstPutIndex).toBeGreaterThan(-1);
    if (firstGetIndex !== -1) {
      expect(firstGetIndex).toBeLessThan(firstPutIndex);
    }

    const sentItemIds = putBodies.flatMap((payload) => {
      const items = Array.isArray(payload.answers) ? (payload.answers as Array<{ itemId?: string }>) : [];
      return items.map((entry) => entry.itemId).filter((id): id is string => Boolean(id));
    });
    expect(sentItemIds.length).toBeGreaterThanOrEqual(dirtyBeforeClose.length);
    for (const dirty of dirtyBeforeClose) {
      expect(sentItemIds).toContain(dirty.itemId);
    }

    const idbOnline = await readAttemptDraftsIdb(page2);
    const stillDirty = idbOnline.itemDrafts.filter(
      (record) => record.attemptId === attemptId && record.dirtyAnswer,
    );
    expect(stillDirty).toHaveLength(0);
  });

  test("AC-19: ngắt POST /submit hiện Chưa xác nhận nộp bài, đối chiếu GET rồi nộp thành công", async ({
    context,
    page,
  }) => {
    const { cookies } = await createVerifiedAccount("res.ac19");
    await context.addCookies(cookies);

    const { entitlementId } = await purchaseExamViaUi(page, "DS-HSA-PART-MATH-MINI");
    const attemptId = await startAttemptFromPrepare(page, entitlementId);

    await answerCurrentUnit(page);
    await waitForSavedStatus(page);

    let blockSubmit = true;
    await page.route(`**/api/v1/me/attempts/${attemptId}/submit`, async (route) => {
      if (blockSubmit) {
        await route.abort("failed");
        return;
      }
      await route.continue();
    });

    await page.getByRole("button", { name: "Nộp bài" }).click();
    const dialog = page.getByRole("dialog");
    await expect(dialog.getByRole("heading", { name: "Nộp bài?" })).toBeVisible();
    await dialog.getByRole("button", { name: "Nộp bài" }).click();

    await expect(dialog.getByRole("heading", { name: "Chưa xác nhận nộp bài" })).toBeVisible({ timeout: 10_000 });
    await expect(page.getByRole("heading", { name: "Lượt thi đã đóng" })).toHaveCount(0);

    blockSubmit = false;
    const reconcileButton = dialog.getByRole("button", { name: /Đang kiểm tra trạng thái lượt/ });
    await expect(reconcileButton).toBeVisible();

    const [stateResponse] = await Promise.all([
      page.waitForResponse(
        (res) =>
          res.url().includes(`/api/v1/me/attempts/${attemptId}`) &&
          !res.url().endsWith("/submit") &&
          res.request().method() === "GET",
      ),
      reconcileButton.click(),
    ]);
    expect(stateResponse.status()).toBe(200);

    await expect(dialog.getByRole("heading", { name: "Nộp bài?" })).toBeVisible({ timeout: 10_000 });
    await dialog.getByRole("button", { name: "Nộp bài" }).click();

    await expect(page.getByRole("heading", { name: "Lượt thi đã đóng" })).toBeVisible({ timeout: 20_000 });
    await page.getByRole("link", { name: "Xem kết quả" }).click();
    await expect(page).toHaveURL(new RegExp(`/attempts/${attemptId}/result`, "i"));
  });

  test("AC-21: phiên khác bấm Tiếp tục tại đây -> phiên cũ bị thay thế khi gửi đáp án", async ({
    browser,
    context,
    page,
  }) => {
    const { account, cookies } = await createVerifiedAccount("res.ac21");
    await context.addCookies(cookies);

    const { entitlementId } = await purchaseExamViaUi(page, "DS-HSA-PART-MATH-MINI");
    const attemptId = await startAttemptFromPrepare(page, entitlementId);
    await answerCurrentUnit(page);
    await waitForSavedStatus(page);

    const contextB = await browser.newContext();
    try {
      const pageB = await contextB.newPage();
      await pageB.goto(`${WEB_URL}/login`);
      await submitLoginForm(pageB, account.email, account.password);
      await pageB.waitForURL((url) => !url.pathname.startsWith("/login"), { timeout: 20_000 });

      await pageB.goto(`${WEB_URL}/attempts/${encodeURIComponent(attemptId)}`);
      await expect(
        pageB.getByRole("heading", { name: "Bài thi đang mở ở một phiên khác" }),
      ).toBeVisible({ timeout: 20_000 });

      await pageB.getByRole("button", { name: "Tiếp tục tại đây" }).click();
      await expect(pageB.getByRole("button", { name: "Nộp bài" })).toBeVisible({ timeout: 20_000 });

      // Phiên A thử sửa tiếp -> nhận 409 bị thu hồi và chuyển sang màn Lượt thi đã được tiếp tục ở thiết bị khác
      await page.getByRole("button", { name: "Câu sau" }).click();
      await answerCurrentUnit(page);
      await expect(
        page.getByRole("heading", { name: "Lượt thi đã được tiếp tục ở thiết bị khác." }),
      ).toBeVisible({ timeout: 20_000 });
      await expect(page.locator("main input:not([disabled]), main [contenteditable='true']")).toHaveCount(0);
    } finally {
      await contextB.close();
    }
  });

  test("AC-22: 2 tab cùng context -> tab phụ Chỉ xem, đóng tab chính thì tab phụ tự mở quyền sửa", async ({
    context,
    page,
  }) => {
    const { cookies } = await createVerifiedAccount("res.ac22");
    await context.addCookies(cookies);

    const { entitlementId } = await purchaseExamViaUi(page, "DS-HSA-PART-MATH-MINI");
    const attemptId = await startAttemptFromPrepare(page, entitlementId);
    await answerCurrentUnit(page);
    await waitForSavedStatus(page);

    const page2 = await context.newPage();
    await page2.goto(`${WEB_URL}/attempts/${encodeURIComponent(attemptId)}`);

    const readonlyNotice = page2
      .locator('[role="status"]')
      .filter({ hasText: "Tab này chỉ để xem. Đóng tab kia thì tab này tự động làm bài tiếp được." });
    await expect(readonlyNotice).toBeVisible({ timeout: 20_000 });
    await expect(page2.locator("main fieldset[disabled]")).toBeVisible();

    await page.close({ runBeforeUnload: true });

    await expect(readonlyNotice).toBeHidden({ timeout: 15_000 });
    await expect(page2.locator("main fieldset")).not.toHaveAttribute("disabled", "", { timeout: 15_000 });

    await page2.getByRole("button", { name: "Câu sau" }).click();
    await answerCurrentUnit(page2);
    await waitForSavedStatus(page2);
  });

  test("AC-23: beforeunload chỉ chặn khi có thay đổi chưa lưu, hết chặn sau khi Đã lưu", async ({
    context,
    page,
  }) => {
    const { cookies } = await createVerifiedAccount("res.ac23");
    await context.addCookies(cookies);

    const { entitlementId } = await purchaseExamViaUi(page, "DS-HSA-PART-MATH-MINI");
    const attemptId = await startAttemptFromPrepare(page, entitlementId);
    await waitForSavedStatus(page);

    const cleanPrevented = await page.evaluate(() => {
      const event = new Event("beforeunload", { cancelable: true });
      window.dispatchEvent(event);
      return event.defaultPrevented;
    });
    expect(cleanPrevented).toBe(false);

    let blockPut = true;
    await page.route(`**/api/v1/me/attempts/${attemptId}/answers`, async (route) => {
      if (blockPut) {
        await route.abort("failed");
        return;
      }
      await route.continue();
    });

    await answerCurrentUnit(page);
    const dirtyPrevented = await page.evaluate(() => {
      const event = new Event("beforeunload", { cancelable: true });
      window.dispatchEvent(event);
      return event.defaultPrevented;
    });
    expect(dirtyPrevented).toBe(true);

    blockPut = false;
    await page.evaluate(() => window.dispatchEvent(new Event("online")));
    await waitForSavedStatus(page);

    const savedPrevented = await page.evaluate(() => {
      const event = new Event("beforeunload", { cancelable: true });
      window.dispatchEvent(event);
      return event.defaultPrevented;
    });
    expect(savedPrevented).toBe(false);
  });

  test("AC-24: đổi tài khoản không lộ bản nháp và đăng xuất chủ động xóa sạch IndexedDB", async ({
    context,
    page,
  }) => {
    const { cookies: cookiesA } = await createVerifiedAccount("res.ac24a");
    await context.addCookies(cookiesA);

    const { entitlementId: entA } = await purchaseExamViaUi(page, "DS-HSA-PART-MATH-MINI");
    const attemptA = await startAttemptFromPrepare(page, entA);

    await page.route(`**/api/v1/me/attempts/${attemptA}/answers`, (route) => route.abort("failed"));
    await answerCurrentUnit(page);
    await page.waitForTimeout(900);

    const idbUserA = await readAttemptDraftsIdb(page);
    expect(idbUserA.itemDrafts.some((d) => d.attemptId === attemptA)).toBe(true);
    await page.unroute(`**/api/v1/me/attempts/${attemptA}/answers`);

    // Phần A: Phiên A kết thúc bên ngoài (mất cookie), đăng nhập B trên cùng trình duyệt
    await context.clearCookies();
    const { account: accountB, cookies: cookiesB } = await createVerifiedAccount("res.ac24b");
    await context.addCookies(cookiesB);

    await page.goto(`${WEB_URL}/attempts/${encodeURIComponent(attemptA)}`);
    await expect(page.getByRole("heading", { name: "Không tìm thấy lượt thi" })).toBeVisible({ timeout: 20_000 });

    const { entitlementId: entB } = await purchaseExamViaUi(page, "DS-HSA-PART-MATH-MINI");
    const attemptB = await startAttemptFromPrepare(page, entB);
    await page.route(`**/api/v1/me/attempts/${attemptB}/answers`, (route) => route.abort("failed"));
    const radiosCheckedInitial = page.locator(
      'main fieldset input[type="radio"]:checked, main fieldset [role="radio"][aria-checked="true"]',
    );
    await expect(radiosCheckedInitial).toHaveCount(0);

    await answerCurrentUnit(page);
    await page.waitForTimeout(900);
    await page.unroute(`**/api/v1/me/attempts/${attemptB}/answers`);

    // Phần B: Đăng xuất chủ động từ menu tài khoản xóa sạch IndexedDB
    page.once("dialog", (dialog) => {
      void dialog.accept();
    });
    await page.goto(`${WEB_URL}/home`);
    await page.getByRole("button", { name: "Menu tài khoản" }).click();
    await page.getByRole("menuitem", { name: "Đăng xuất" }).click();
    await page.waitForURL((url) => url.pathname === "/" || url.pathname === "/login", { timeout: 20_000 });

    const idbAfterLogout = await readAttemptDraftsIdb(page);
    expect(idbAfterLogout.itemDrafts).toHaveLength(0);
    expect(idbAfterLogout.attemptMeta).toHaveLength(0);
    expect(accountB.username.length).toBeGreaterThan(0);
  });

  test("AC-25: hết phiên (401) giữa chừng -> đăng nhập lại cùng tài khoản -> khôi phục & đồng bộ bản nháp", async ({
    context,
    page,
  }) => {
    const { account, cookies } = await createVerifiedAccount("res.ac25");
    await context.addCookies(cookies);

    const { entitlementId } = await purchaseExamViaUi(page, "DS-HSA-PART-MATH-MINI");
    const attemptId = await startAttemptFromPrepare(page, entitlementId);

    await page.route(`**/api/v1/me/attempts/${attemptId}/answers`, (route) => route.abort("failed"));
    await answerCurrentUnit(page);
    await page.waitForTimeout(900);

    const idbBeforeExpiry = await readAttemptDraftsIdb(page);
    expect(idbBeforeExpiry.itemDrafts.some((d) => d.attemptId === attemptId && d.dirtyAnswer)).toBe(true);

    // Giả lập hết phiên khi truy cập lại lượt thi
    await page.unroute(`**/api/v1/me/attempts/${attemptId}/answers`);
    await context.clearCookies();
    page.once("dialog", (dialog) => {
      void dialog.accept();
    });
    await page.goto(`${WEB_URL}/attempts/${encodeURIComponent(attemptId)}`);
    await page.waitForURL(/\/login/i, { timeout: 20_000 });

    await submitLoginForm(page, account.email, account.password);
    await page.waitForURL(new RegExp(`/attempts/${attemptId}`, "i"), { timeout: 25_000 });
    await expect(page.getByRole("button", { name: "Nộp bài" })).toBeVisible({ timeout: 20_000 });

    await waitForSavedStatus(page);

    const checkedRestored = page.locator(
      'main fieldset input[type="radio"]:checked, main fieldset [role="radio"][aria-checked="true"]',
    );
    expect(await checkedRestored.count()).toBeGreaterThan(0);

    const idbAfterRelogin = await readAttemptDraftsIdb(page);
    expect(idbAfterRelogin.itemDrafts.some((d) => d.attemptId === attemptId && d.dirtyAnswer)).toBe(false);

    await page.reload();
    await expect(checkedRestored.first()).toBeVisible({ timeout: 20_000 });
  });
});
