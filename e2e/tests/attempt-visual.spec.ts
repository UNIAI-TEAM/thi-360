import { expect, test, type Browser, type BrowserContext, type Page } from "@playwright/test";
import { loginAdmin } from "./helpers/editor-qa";
import { createVerifiedAccount, submitLoginForm, WEB_URL, type SessionCookies } from "./helpers/mail";
import { waitForPublishedExamSet } from "./helpers/published-exam-set";
import {
  answerCurrentUnit,
  purchaseExamViaUi,
  startAttemptFromPrepare,
} from "./helpers/attempt-fixtures";
import {
  applyMatrixCookies,
  cellLabel,
  expectMatrixClean,
  launchMatrixBrowser,
  matrixCells,
  openMatrixContext,
  type MatrixCell,
  type MatrixLang,
} from "./helpers/visual-matrix";

test.setTimeout(120_000);

let browser: Browser;
let playerCookies: SessionCookies;
let preChoiceCookies: SessionCookies;
let partChoiceCookies: SessionCookies;
let takeoverCookies: SessionCookies;
let takeoverOwnerContext: BrowserContext | null = null;

let hsaEntitlementId = "";
let playerAttemptId = "";
let submitAttemptId = "";
let bcaEntitlementId = "";
let partChoiceAttemptId = "";
let takeoverAttemptId = "";

async function openCell(
  cell: MatrixCell,
  cookies: SessionCookies,
): Promise<{ context: BrowserContext; page: Page }> {
  const context = await openMatrixContext(browser, cell, WEB_URL);
  await context.addCookies(cookies);
  await applyMatrixCookies(context, cell, WEB_URL);
  const page = await context.newPage();
  await page.emulateMedia({ reducedMotion: "reduce" });
  return { context, page };
}

async function waitForDialogSettled(page: Page): Promise<void> {
  const dialogs = page.getByRole("dialog");
  await expect(dialogs).toHaveCount(1, { timeout: 20_000 });
  await expect(dialogs.last()).toBeVisible({ timeout: 20_000 });
  await page.waitForFunction(() => {
    const els = document.querySelectorAll('[role="dialog"]');
    if (els.length !== 1) return false;
    const el = els[0];
    if (!el) return false;
    const opacity = Number.parseFloat(getComputedStyle(el).opacity);
    return (
      Number.isFinite(opacity) &&
      opacity >= 0.99 &&
      el.getAnimations({ subtree: true }).every((a) => a.playState === "finished")
    );
  });
}

async function ensureActivePlayerSession(page: Page, lang: MatrixLang): Promise<void> {
  const readyAction = page.getByRole("button", {
    name: lang === "en" ? /^(Submit exam|Finish section)$/ : /^(Nộp bài|Hoàn thành phần)$/,
  });
  const partChoiceHeading = page.getByRole("heading", {
    name: lang === "en" ? "Choose the third section" : "Chọn phần thi thứ ba",
  });
  const takeoverBtn = page.getByRole("button", {
    name: lang === "en" ? "Continue here" : "Tiếp tục tại đây",
  });

  await expect
    .poll(
      async () => {
        if (await takeoverBtn.isVisible()) return "takeover";
        if (await partChoiceHeading.isVisible()) return "part-choice";
        if ((await readyAction.isVisible()) && (await readyAction.isEnabled())) return "ready";
        return "pending";
      },
      { timeout: 25_000 },
    )
    .not.toBe("pending");

  if (await takeoverBtn.isVisible()) {
    const takeoverSessionPromise = page
      .waitForResponse(
        (res) => res.url().includes("/session") && res.request().method() === "POST",
        { timeout: 20_000 },
      )
      .catch(() => null);
    await takeoverBtn.click();
    await takeoverSessionPromise;
    await expect(takeoverBtn).toBeHidden({ timeout: 20_000 });
    await expect
      .poll(
        async () =>
          (await partChoiceHeading.isVisible()) ||
          ((await readyAction.isVisible()) && (await readyAction.isEnabled())),
        { timeout: 20_000 },
      )
      .toBe(true);
  }
}

async function gotoAttemptAndEnsureActive(
  page: Page,
  attemptId: string,
  lang: MatrixLang,
): Promise<void> {
  const sessionPathFragment = `/api/v1/me/attempts/${encodeURIComponent(attemptId)}/session`;
  const initialSessionPromise = page.waitForResponse(
    (res) => res.url().includes(sessionPathFragment) && res.request().method() === "POST",
    { timeout: 25_000 },
  );
  await Promise.all([
    initialSessionPromise,
    page.goto(`${WEB_URL}/attempts/${encodeURIComponent(attemptId)}`),
  ]);
  await ensureActivePlayerSession(page, lang);
}

test.beforeAll(async () => {
  test.setTimeout(300_000);
  browser = await launchMatrixBrowser();

  const adminCtx = await browser.newContext();
  try {
    await loginAdmin(adminCtx, "vi");
    await waitForPublishedExamSet(adminCtx.request, { timeoutMs: 240_000 });
  } finally {
    await adminCtx.close();
  }

  // 1) Người dùng cho prepare + waiting + player + finish-part-dialog + submit-confirm-dialog
  const userPlayer = await createVerifiedAccount("vis.player");
  playerCookies = userPlayer.cookies;
  const ctx1 = await browser.newContext();
  try {
    await ctx1.addCookies(playerCookies);
    const p1 = await ctx1.newPage();
    const purchased = await purchaseExamViaUi(p1, "DS-HSA-FULL-MINI");
    hsaEntitlementId = purchased.entitlementId;
    playerAttemptId = await startAttemptFromPrepare(p1, hsaEntitlementId);

    const submitPurchased = await purchaseExamViaUi(p1, "DS-HSA-PART-MATH-MINI");
    submitAttemptId = await startAttemptFromPrepare(p1, submitPurchased.entitlementId);
  } finally {
    await ctx1.close();
  }

  // 2) Người dùng cho prepare-prechoice (chưa bắt đầu lượt, dùng DS-BCA-FULL-MINI có CA1..CA4)
  const userPreChoice = await createVerifiedAccount("vis.prechoice");
  preChoiceCookies = userPreChoice.cookies;
  const ctx2 = await browser.newContext();
  try {
    await ctx2.addCookies(preChoiceCookies);
    const p2 = await ctx2.newPage();
    const purchased = await purchaseExamViaUi(p2, "DS-BCA-FULL-MINI");
    bcaEntitlementId = purchased.entitlementId;
  } finally {
    await ctx2.close();
  }

  // 3) Người dùng cho màn chọn phần 3 (AWAITING_CHOICE trên DS-HSA-FULL-MINI)
  const userChoice = await createVerifiedAccount("vis.choice");
  partChoiceCookies = userChoice.cookies;
  const ctx3 = await browser.newContext();
  try {
    await ctx3.addCookies(partChoiceCookies);
    const p3 = await ctx3.newPage();
    const purchased = await purchaseExamViaUi(p3, "DS-HSA-FULL-MINI");
    partChoiceAttemptId = await startAttemptFromPrepare(p3, purchased.entitlementId);
    await answerCurrentUnit(p3);
    await p3.getByRole("button", { name: "Hoàn thành phần" }).click();
    await p3.getByRole("dialog").getByRole("button", { name: "Hoàn thành phần" }).click();
    await expect(p3.locator('nav[aria-label="Các phần của bài thi"]')).toContainText("Đã hoàn thành", {
      timeout: 20_000,
    });
    await answerCurrentUnit(p3);
    await p3.getByRole("button", { name: "Hoàn thành phần" }).click();
    await p3.getByRole("dialog").getByRole("button", { name: "Hoàn thành phần" }).click();
    await expect(p3.getByRole("heading", { name: "Chọn phần thi thứ ba" })).toBeVisible({ timeout: 20_000 });
  } finally {
    await ctx3.close();
  }

  // 4) Người dùng giữ phiên sống ở context chủ để các ô ma trận luôn gặp TakeoverDialog thật
  const userTakeover = await createVerifiedAccount("vis.takeover");
  takeoverCookies = userTakeover.cookies;
  takeoverOwnerContext = await browser.newContext();
  const ownerPage = await takeoverOwnerContext.newPage();
  await ownerPage.goto(`${WEB_URL}/login`);
  await submitLoginForm(ownerPage, userTakeover.account.email, userTakeover.account.password);
  await ownerPage.waitForURL((url) => !url.pathname.startsWith("/login"), { timeout: 20_000 });
  const takeoverPurchased = await purchaseExamViaUi(ownerPage, "DS-HSA-PART-MATH-MINI");
  takeoverAttemptId = await startAttemptFromPrepare(ownerPage, takeoverPurchased.entitlementId);
});

test.afterAll(async () => {
  await takeoverOwnerContext?.close().catch(() => {});
  await browser?.close().catch(() => {});
});

test.describe("Ma trận hiển thị 8 ô cho màn chuẩn bị & làm bài (AC-32)", () => {
  for (const cell of matrixCells()) {
    test(`AC-32 [${cellLabel(cell)}]: kiểm tra bố cục, tương phản và ngôn ngữ mọi màn hình FS-5`, async () => {
      // 1, 3, 4, 5, 6: prepare, waiting, player, finish-part-dialog, submit-confirm-dialog
      {
        const { context, page } = await openCell(cell, playerCookies);
        try {
          await page.goto(`${WEB_URL}/prepare/${encodeURIComponent(hsaEntitlementId)}`);
          await expect(page.locator("h1[data-exam-content]")).toBeVisible({ timeout: 20_000 });
          await expectMatrixClean(page, "prepare", cell);

          await page.route(`**/api/v1/me/attempts/${playerAttemptId}`, async (route) => {
            await route.fulfill({
              status: 200,
              contentType: "application/json",
              body: JSON.stringify({
                attemptId: playerAttemptId,
                examId: "00000000-0000-0000-0000-000000000001",
                examCode: "DS-HSA-FULL-MINI",
                examName: "Đề HSA thử nghiệm",
                state: "STARTING",
                serverNow: new Date().toISOString(),
                parts: [],
                items: [],
              }),
            });
          });
          await page.goto(`${WEB_URL}/attempts/${encodeURIComponent(playerAttemptId)}`);
          await expect(
            page.getByRole("heading", { name: cell.lang === "en" ? "Preparing your exam" : "Đang chuẩn bị đề" }),
          ).toBeVisible({ timeout: 20_000 });
          await expectMatrixClean(page, "waiting", cell);
          await page.unroute(`**/api/v1/me/attempts/${playerAttemptId}`);

          await gotoAttemptAndEnsureActive(page, playerAttemptId, cell.lang);
          await expect(page.locator("main fieldset[data-attempt-question-fieldset]")).toBeVisible({ timeout: 20_000 });
          await expectMatrixClean(page, "player", cell);

          await page.getByRole("button", { name: cell.lang === "en" ? "Finish section" : "Hoàn thành phần" }).click();
          await waitForDialogSettled(page);
          await expectMatrixClean(page, "finish-part-dialog", cell);
          await page
            .getByRole("dialog")
            .getByRole("button", { name: cell.lang === "en" ? "Keep working" : "Quay lại làm tiếp" })
            .click();

          await gotoAttemptAndEnsureActive(page, submitAttemptId, cell.lang);
          await expect(page.locator("main fieldset[data-attempt-question-fieldset]")).toBeVisible({ timeout: 20_000 });
          await page.getByRole("button", { name: cell.lang === "en" ? "Submit exam" : "Nộp bài" }).click();
          await waitForDialogSettled(page);
          await expectMatrixClean(page, "submit-confirm-dialog", cell);
          await page
            .getByRole("dialog")
            .getByRole("button", { name: cell.lang === "en" ? "Review answers" : "Quay lại kiểm tra" })
            .click();
        } finally {
          await context.close();
        }
      }

      // 2. prepare-prechoice
      {
        const { context, page } = await openCell(cell, preChoiceCookies);
        try {
          await page.goto(`${WEB_URL}/prepare/${encodeURIComponent(bcaEntitlementId)}`);
          await expect(page.locator('input[type="radio"][name="preChoice"]').first()).toBeVisible({ timeout: 20_000 });
          await expectMatrixClean(page, "prepare-prechoice", cell);
        } finally {
          await context.close();
        }
      }

      // 7. part-choice (AWAITING_CHOICE)
      {
        const { context, page } = await openCell(cell, partChoiceCookies);
        try {
          await gotoAttemptAndEnsureActive(page, partChoiceAttemptId, cell.lang);
          await expect(
            page.getByRole("heading", {
              name: cell.lang === "en" ? "Choose the third section" : "Chọn phần thi thứ ba",
            }),
          ).toBeVisible({ timeout: 20_000 });
          const scienceRadio = page.locator('input[type="radio"][name="attempt-part-choice"][value="SCIENCE"]');
          await expect(scienceRadio).toBeEnabled({ timeout: 20_000 });
          await scienceRadio.check();
          await expectMatrixClean(page, "part-choice", cell);
        } finally {
          await context.close();
        }
      }

      // 8. takeover-dialog
      {
        const { context, page } = await openCell(cell, takeoverCookies);
        try {
          await page.goto(`${WEB_URL}/attempts/${encodeURIComponent(takeoverAttemptId)}`);
          await expect(
            page.getByRole("heading", {
              name:
                cell.lang === "en"
                  ? "Attempt is open in another session"
                  : "Bài thi đang mở ở một phiên khác",
            }),
          ).toBeVisible({ timeout: 20_000 });
          await waitForDialogSettled(page);
          await expectMatrixClean(page, "takeover-dialog", cell);
        } finally {
          await context.close();
        }
      }
    });
  }
});
