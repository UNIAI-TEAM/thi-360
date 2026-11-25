import { randomUUID } from "node:crypto";
import { expect, test, type Page } from "@playwright/test";

import { adminApi, ADMIN_URL, WEB_URL } from "./helpers/editor-qa";
import {
  answerCurrentUnit,
  ensureDsReady,
  getAttemptStateApi,
  purchaseExamViaUi,
  waitForSavedStatus,
} from "./helpers/attempt-fixtures";
import { createVerifiedAccount } from "./helpers/mail";

async function startAttemptHandlingPreChoice(page: Page, entitlementId: string, preferredPreChoice?: RegExp): Promise<string> {
  await page.goto(`${WEB_URL}/prepare/${encodeURIComponent(entitlementId)}`);
  await expect(page.locator("h1[data-exam-content]")).toBeVisible({ timeout: 20_000 });

  const radios = page.locator('input[type="radio"][name="preChoice"]');
  const radioCount = await radios.count();
  if (radioCount > 0) {
    if (preferredPreChoice) {
      const targetRadio = page.getByRole("radio", { name: preferredPreChoice }).first();
      if ((await targetRadio.count()) > 0) {
        await targetRadio.check();
      } else {
        await radios.first().check();
      }
    } else {
      await radios.first().check();
    }
  }

  const startBtn = page.getByRole("button", { name: "Bắt đầu bài thi" });
  await expect(startBtn).toBeEnabled({ timeout: 15_000 });

  const [startRes] = await Promise.all([
    page.waitForResponse(
      (res) => res.url().endsWith("/api/v1/me/attempts") && res.request().method() === "POST",
    ),
    startBtn.click(),
  ]);
  expect(startRes.ok(), `POST /api/v1/me/attempts failed: ${startRes.status()}`).toBe(true);
  const { attemptId } = (await startRes.json()) as { attemptId: string };

  await page.waitForURL(new RegExp(`/attempts/${attemptId}`), { timeout: 45_000 });
  await expect(page.locator("main fieldset[data-attempt-question-fieldset]")).toBeVisible({ timeout: 45_000 });
  return attemptId;
}

async function answerOpenPartUnits(page: Page, _maxUnits = 30): Promise<void> {
  void _maxUnits;
  const changed = await answerCurrentUnit(page, "42");
  if (changed > 0) {
    await waitForSavedStatus(page);
  }
}

async function gradePendingEssayIfNeeded(
  page: Page,
  attemptId: string,
  admin: Awaited<ReturnType<typeof adminApi>>,
): Promise<string> {
  let finalState = "";
  await expect
    .poll(
      async () => {
        const res = await page.request.get(`${WEB_URL}/api/v1/me/attempts/${encodeURIComponent(attemptId)}/result`);
        if (!res.ok()) return "";
        finalState = ((await res.json()) as { state?: string }).state ?? "";
        return finalState;
      },
      { message: `wait attempt ${attemptId} graded or pending review`, timeout: 45_000, intervals: [500, 1_000] },
    )
    .toMatch(/^(SCORED|PENDING_REVIEW)$/);

  if (finalState === "SCORED") return "SCORED";

  const detailRes = await admin.api.get(`${ADMIN_URL}/api/v1/admin/attempts/${encodeURIComponent(attemptId)}`);
  expect(detailRes.ok(), `GET /admin/attempts/${attemptId}: ${detailRes.status()} ${await detailRes.text()}`).toBe(true);
  const attemptDetail = (await detailRes.json()) as {
    items: Array<{ itemId: string; questionType: string; pointsMax?: number }>;
  };

  for (const item of attemptDetail.items.filter((i) => i.questionType === "ESSAY")) {
    const qRes = await admin.api.get(`${ADMIN_URL}/api/v1/admin/grading/items/${encodeURIComponent(item.itemId)}`);
    expect(qRes.ok(), await qRes.text()).toBe(true);
    const qDetail = (await qRes.json()) as {
      pointsMax: number;
      rubric: Array<{ id: string; points?: number }>;
    };

    const claimRes = await admin.api.post(`${ADMIN_URL}/api/v1/admin/grading/items/${encodeURIComponent(item.itemId)}/claim`, {
      headers: { ...admin.headers, "idempotency-key": randomUUID() },
    });
    expect(claimRes.ok(), `claim ${item.itemId}: ${claimRes.status()} ${await claimRes.text()}`).toBe(true);
    const claimView = (await claimRes.json()) as { version: number };

    const criteria = qDetail.rubric.map((r) => ({
      id: r.id,
      points: r.points ?? 0,
      comment: "QA grade",
    }));
    const points =
      criteria.length > 0
        ? criteria.reduce((sum, c) => sum + c.points, 0)
        : Math.min(qDetail.pointsMax || 1, 1);

    const finalizeRes = await admin.api.post(
      `${ADMIN_URL}/api/v1/admin/grading/items/${encodeURIComponent(item.itemId)}/finalize`,
      {
        headers: { ...admin.headers, "idempotency-key": randomUUID() },
        data: {
          expectedVersion: claimView.version,
          criteria: criteria.length > 0 ? criteria : undefined,
          points,
          comment: "QA finalize for nine kinds sweep",
        },
      },
    );
    expect(finalizeRes.ok(), `finalize ${item.itemId}: ${finalizeRes.status()} ${await finalizeRes.text()}`).toBe(true);
  }

  await expect
    .poll(
      async () => {
        const res = await page.request.get(`${WEB_URL}/api/v1/me/attempts/${encodeURIComponent(attemptId)}/result`);
        if (!res.ok()) return "";
        return ((await res.json()) as { state?: string }).state ?? "";
      },
      { message: `wait attempt ${attemptId} transition to SCORED after manual grading`, timeout: 30_000 },
    )
    .toBe("SCORED");

  return "SCORED";
}

async function completeAttemptToScored(
  page: Page,
  attemptId: string,
  admin: Awaited<ReturnType<typeof adminApi>>,
  options?: { maxUnitsPerPart?: number },
): Promise<void> {
  const maxUnitsPerPart = options?.maxUnitsPerPart ?? 30;

  let submitted = false;
  for (let partStep = 0; partStep < 6; partStep += 1) {
    await answerOpenPartUnits(page, maxUnitsPerPart);

    const finishPartBtn = page.getByRole("button", { name: "Hoàn thành phần" });
    if ((await finishPartBtn.count()) > 0 && (await finishPartBtn.isVisible())) {
      await finishPartBtn.click();
      const finishDialog = page.getByRole("dialog");
      await expect(finishDialog).toBeVisible();
      await finishDialog.getByRole("button", { name: "Hoàn thành phần" }).click();
      await expect(finishDialog).toBeHidden({ timeout: 15_000 });

      // Nếu phần tiếp theo là AWAITING_CHOICE (vd HSA/QDA đầy đủ) thì chọn lựa chọn đầu
      const confirmChoiceBtn = page.getByRole("button", { name: /Xác nhận chọn phần/ });
      const fieldset = page.locator("main fieldset[data-attempt-question-fieldset]");
      await expect(confirmChoiceBtn.or(fieldset).first()).toBeVisible({ timeout: 25_000 });
      if ((await confirmChoiceBtn.count()) > 0 && (await confirmChoiceBtn.isVisible())) {
        const radio = page.locator('main input[type="radio"]').first();
        if ((await radio.count()) > 0) await radio.check();
        const checkboxes = page.locator(
          'main input[type="checkbox"]:not(:disabled), main button[role="checkbox"]:not([disabled])',
        );
        const checkboxCount = Math.min(3, await checkboxes.count());
        for (let idx = 0; idx < checkboxCount; idx += 1) {
          await checkboxes.nth(idx).check();
        }
        await expect(confirmChoiceBtn).toBeEnabled({ timeout: 10_000 });
        await confirmChoiceBtn.click();
      }
      await expect(fieldset).toBeVisible({ timeout: 25_000 });
      continue;
    }

    const submitBtn = page.getByRole("button", { name: "Nộp bài" });
    await expect(submitBtn).toBeVisible({ timeout: 10_000 });
    await submitBtn.click();
    const submitDialog = page.getByRole("dialog");
    await expect(submitDialog).toBeVisible();
    await submitDialog.getByRole("button", { name: "Nộp bài" }).click();
    submitted = true;
    break;
  }
  expect(submitted, "completeAttemptToScored must submit within max part iterations").toBe(true);

  await expect(page.getByRole("heading", { name: "Lượt thi đã đóng" })).toBeVisible({ timeout: 25_000 });
  const viewResultLink = page.getByRole("link", { name: "Xem kết quả" });
  await expect(viewResultLink).toHaveAttribute("href", `/attempts/${attemptId}/result`);
  await viewResultLink.click();
  await page.waitForURL(new RegExp(`/attempts/${attemptId}/result`), { timeout: 45_000 });
  const settledState = await gradePendingEssayIfNeeded(page, attemptId, admin);
  expect(settledState).toBe("SCORED");
  await page.reload();
  await expect(page.getByRole("heading", { level: 1 })).toBeVisible({ timeout: 20_000 });
  await expect(page.getByText("Đã chấm", { exact: true }).first()).toBeVisible({ timeout: 15_000 });
}

async function runStandardKindFlow(
  page: Page,
  examCode: string,
  admin: Awaited<ReturnType<typeof adminApi>>,
  options?: { preChoice?: RegExp; maxUnitsPerPart?: number },
): Promise<string> {
  const { entitlementId } = await purchaseExamViaUi(page, examCode);
  const attemptId = await startAttemptHandlingPreChoice(page, entitlementId, options?.preChoice);
  await completeAttemptToScored(page, attemptId, admin, options);
  return attemptId;
}

test.describe("AC-24 chín kỳ thi E2E", () => {
  test.beforeAll(async ({ browser }) => {
    await ensureDsReady(browser);
  });

  test("AC-24 chín kỳ thi: mua -> làm bài -> nộp bài -> kết quả SCORED cho cả 9 kỳ thi (HSA, QDA, BCA, VACT, SP2E, SPT, THPTQG, HSCA, VSAT)", async ({
    browser,
  }) => {
    test.setTimeout(600_000);

    let admin: Awaited<ReturnType<typeof adminApi>> | undefined;
    let context: Awaited<ReturnType<typeof browser.newContext>> | undefined;

    try {
      admin = await adminApi();
      const { cookies } = await createVerifiedAccount("qa.9kinds");
      context = await browser.newContext();
      await context.addCookies(cookies);
      const page = await context.newPage();
      page.setDefaultTimeout(25_000);
      await test.step("1. HSA (DS-HSA-PART-MATH-MINI)", async () => {
        await runStandardKindFlow(page, "DS-HSA-PART-MATH-MINI", admin!);
      });

      await test.step("2. QDA (DS-QDA-PART-SCIENCE-MINI)", async () => {
        await runStandardKindFlow(page, "DS-QDA-PART-SCIENCE-MINI", admin!, { preChoice: /Q[DĐ]01/i });
      });

      await test.step("3. BCA (DS-BCA-PART-TN-TU-CHON-MINI)", async () => {
        await runStandardKindFlow(page, "DS-BCA-PART-TN-TU-CHON-MINI", admin!, { preChoice: /CA1/i });
      });

      await test.step("4. VACT (DS-VACT-SUBJECT-TOAN-MINI)", async () => {
        await runStandardKindFlow(page, "DS-VACT-SUBJECT-TOAN-MINI", admin!);
      });

      await test.step("5. SP2E (DS-SP2E-MATH-2026-MINI)", async () => {
        await runStandardKindFlow(page, "DS-SP2E-MATH-2026-MINI", admin!);
      });

      await test.step("6. SPT (DS-SPT-INFORMATICS-MINI)", async () => {
        await runStandardKindFlow(page, "DS-SPT-INFORMATICS-MINI", admin!);
      });

      await test.step("7. THPTQG (DS-THPTQG-INFORMATICS-MINI): điều hướng FREE đa phần + /result Đã chấm", async () => {
        const { entitlementId } = await purchaseExamViaUi(page, "DS-THPTQG-INFORMATICS-MINI");
        const attemptId = await startAttemptHandlingPreChoice(page, entitlementId, /CS|Khoa học máy tính/i);

        const state = await getAttemptStateApi(page.request, attemptId);
        expect(state.navigationMode).toBe("FREE");
        expect(state.parts.length).toBeGreaterThanOrEqual(2);
        await expect(page.getByRole("button", { name: "Hoàn thành phần" })).toHaveCount(0);

        const partTabs = page.getByRole("tab");
        await expect(partTabs).toHaveCount(state.parts.length);
        // Điều hướng tự do ngược từ phần cuối về Phần 1 và trả lời câu hỏi ở mỗi phần
        for (let idx = state.parts.length - 1; idx >= 0; idx -= 1) {
          await partTabs.nth(idx).click();
          await expect(partTabs.nth(idx)).toHaveAttribute("aria-selected", "true");
          await answerOpenPartUnits(page, 30);
        }

        await page.getByRole("button", { name: "Nộp bài" }).click();
        const dialog = page.getByRole("dialog");
        await expect(dialog).toBeVisible();
        await dialog.getByRole("button", { name: "Nộp bài" }).click();
        await expect(page.getByRole("heading", { name: "Lượt thi đã đóng" })).toBeVisible({ timeout: 25_000 });
        const viewResultLink = page.getByRole("link", { name: "Xem kết quả" });
        await expect(viewResultLink).toHaveAttribute("href", `/attempts/${attemptId}/result`);
        await viewResultLink.click();
        await page.waitForURL(new RegExp(`/attempts/${attemptId}/result`), { timeout: 45_000 });
        expect(await gradePendingEssayIfNeeded(page, attemptId, admin!)).toBe("SCORED");
        await page.reload();
        await expect(page.getByRole("heading", { level: 1 })).toBeVisible({ timeout: 20_000 });
        await expect(page.getByText("Đã chấm", { exact: true }).first()).toBeVisible({ timeout: 15_000 });
      });

      await test.step("8. HSCA (DS-HSCA-ANH-MINI): nghe 2 lần hết lượt (0/2), không seek/audio[controls]", async () => {
        const { entitlementId } = await purchaseExamViaUi(page, "DS-HSCA-ANH-MINI");
        const attemptId = await startAttemptHandlingPreChoice(page, entitlementId);

        const audioRegion = page.getByRole("region", { name: "Trình phát audio" }).first();
        await expect(audioRegion).toBeVisible({ timeout: 20_000 });
        await expect(page.locator("audio[controls]")).toHaveCount(0);
        await expect(audioRegion.getByRole("slider")).toHaveCount(0);
        for (let playNo = 1; playNo <= 2; playNo += 1) {
          const expectedBadge = playNo === 1 ? "Còn 2/2 lần nghe" : "Còn 1/2 lần nghe";
          await expect(audioRegion.getByText(expectedBadge, { exact: true })).toBeVisible({ timeout: 15_000 });

          const playBtn = audioRegion.getByRole("button", { name: /^Nghe$/ });
          await expect(playBtn).toBeEnabled({ timeout: 10_000 });

          const endPromise = page.waitForResponse(
            (res) =>
              res.url().includes(`/attempts/${attemptId}/audio/`) &&
              res.url().endsWith("/end") &&
              res.request().method() === "POST",
            { timeout: 90_000 },
          );
          endPromise.catch(() => undefined);

          const [playStartRes] = await Promise.all([
            page.waitForResponse(
              (res) =>
                res.url().includes(`/attempts/${attemptId}/audio/`) &&
                res.url().endsWith("/plays") &&
                res.request().method() === "POST",
              { timeout: 30_000 },
            ),
            playBtn.click(),
          ]);
          expect(playStartRes.status(), `audio start #${playNo} failed: ${playStartRes.status()}`).toBe(200);

          const autoplayBlockedNotice = audioRegion.getByText(/Trình duyệt chặn tự động phát/);
          const settled = await Promise.race([
            endPromise.then((res) => ({ kind: "ended" as const, res })),
            autoplayBlockedNotice.waitFor({ state: "visible", timeout: 10_000 }).then(
              () => ({ kind: "autoplay_blocked" as const }),
              () => ({ kind: "no_autoplay_block" as const }),
            ),
          ]);

          let endRes: Awaited<typeof endPromise>;
          if (settled.kind === "ended") {
            endRes = settled.res;
          } else {
            if (settled.kind === "autoplay_blocked") {
              const resumeBtn = audioRegion.getByRole("button", { name: "Phát lại" });
              await expect(resumeBtn).toBeVisible({ timeout: 5_000 });
              await resumeBtn.click();
            }
            endRes = await endPromise;
          }
          expect(endRes.status(), `audio end #${playNo} failed: ${endRes.status()}`).toBe(200);

          if (playNo === 1) {
            await expect(audioRegion.getByText("Còn 1/2 lần nghe", { exact: true })).toBeVisible({ timeout: 15_000 });
          }
        }

        await expect(audioRegion.getByText("Đã hết lượt nghe (0/2)", { exact: true })).toBeVisible({ timeout: 15_000 });
        await expect(audioRegion.getByText("Bạn đã dùng hết số lần nghe cho tệp audio này.", { exact: true })).toBeVisible({
          timeout: 15_000,
        });
        await expect(audioRegion.getByRole("button", { name: /^Nghe$/ })).toBeDisabled({ timeout: 15_000 });

        await completeAttemptToScored(page, attemptId, admin!);
      });

      await test.step("9. VSAT (DS-VSAT-ANH): displaySegments Phần I-IV + segments[] breakdown trên /result", async () => {
        const { entitlementId } = await purchaseExamViaUi(page, "DS-VSAT-ANH");
        const attemptId = await startAttemptHandlingPreChoice(page, entitlementId);

        await expect(page.locator("[data-segment-header]").filter({ hasText: "Phần I" }).first()).toBeVisible({
          timeout: 15_000,
        });
        await completeAttemptToScored(page, attemptId, admin!, { maxUnitsPerPart: 30 });

        const segmentsSection = page.locator('section[aria-labelledby="result-segments-title"]');
        await expect(segmentsSection).toBeVisible({ timeout: 20_000 });
        for (const seg of ["Phần I", "Phần II", "Phần III", "Phần IV"]) {
          await expect(segmentsSection.getByText(seg).first()).toBeVisible();
        }
      });
    } finally {
      await Promise.allSettled([admin?.api.dispose(), context?.close()]);
    }
  });
});
