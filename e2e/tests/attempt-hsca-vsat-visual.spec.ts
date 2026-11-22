import { expect, request as playwrightRequest, test, type Browser, type BrowserContext, type Page } from "@playwright/test";
import {
  API_URL,
  ensureDsReady,
  getAttemptStateApi,
  purchaseExamViaUi,
  startAttemptFromPrepare,
} from "./helpers/attempt-fixtures";
import { WEB_URL, createVerifiedAccount, type SessionCookies } from "./helpers/mail";
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

interface AttemptStateWithItemsPayload {
  parts?: Array<{
    items?: Array<{
      displayNo?: number | null;
      questionType?: string;
      questionBody?: {
        answerFormat?: string;
        inputHint?: string;
      };
    }>;
  }>;
}

const MOCK_VSAT_ATTEMPT_ID = "00000000-0000-4000-8000-00000000f571";
const SYMBOL_BAR_ANSWER_FORMATS = new Set(["CHEM_FORMULA", "CHEM_EQUATION", "TEXT"]);

let browser: Browser;
let prepareCookies: SessionCookies;
let playerCookies: SessionCookies;
let miniPrepareEntitlementId = "";
let vsatPrepareEntitlementId = "";
let hscaMiniAttemptId = "";
let hscaHoaAttemptId = "";
let hoaShortDisplayNo = 1;
let vsatAttemptId = "";
let abortedAudioAttemptId = "";

async function setE2eAudioFault(examId: string, active: boolean): Promise<void> {
  const api = await playwrightRequest.newContext({ baseURL: API_URL });
  try {
    const url = `/api/v1/test/audio-faults/${encodeURIComponent(examId)}`;
    const res = active ? await api.post(url) : await api.delete(url);
    expect(res.status(), `${active ? "POST" : "DELETE"} ${url}: ${await res.text()}`).toBe(200);
  } finally {
    await api.dispose();
  }
}

async function openCell(
  cell: MatrixCell,
  accountCookies: SessionCookies,
): Promise<{ context: BrowserContext; page: Page }> {
  const context = await openMatrixContext(browser, cell, WEB_URL);
  await context.addCookies(accountCookies);
  await applyMatrixCookies(context, cell, WEB_URL);
  const page = await context.newPage();
  page.setDefaultTimeout(20_000);
  await page.emulateMedia({ reducedMotion: "reduce" });
  return { context, page };
}

async function ensureActivePlayerSession(page: Page, lang: MatrixLang): Promise<void> {
  const readyAction = page.getByRole("button", {
    name: lang === "en" ? /^(Submit exam|Finish section)$/ : /^(Nộp bài|Hoàn thành phần)$/,
  });
  const takeoverBtn = page.getByRole("button", {
    name: lang === "en" ? "Continue here" : "Tiếp tục tại đây",
  });

  await expect
    .poll(
      async () => {
        if (await takeoverBtn.isVisible()) return "takeover";
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
    await expect(readyAction).toBeVisible({ timeout: 20_000 });
  }
}

async function jumpToQuestionDisplayNo(page: Page, displayNo: number, cell: MatrixCell): Promise<void> {
  if (cell.width === 390) {
    await page.getByRole("button", { name: cell.lang === "en" ? /^Questions / : /^Danh sách câu/ }).click();
    const dialog = page.getByRole("dialog");
    await expect(dialog).toBeVisible({ timeout: 10_000 });
    await dialog
      .locator("button[data-completion]")
      .filter({ has: page.locator(`:text-is("${displayNo}")`) })
      .or(dialog.getByRole("button", { name: new RegExp(`^${displayNo}(?:,|\\s|$)`) }))
      .first()
      .click();
    await expect(dialog).toBeHidden({ timeout: 10_000 });
    return;
  }

  const sidebar = page.locator(
    `aside[aria-label="${cell.lang === "en" ? "Question list" : "Danh sách câu hỏi"}"]`,
  );
  await expect(sidebar).toBeVisible({ timeout: 10_000 });
  await sidebar
    .locator("button[data-completion]")
    .filter({ has: page.locator(`:text-is("${displayNo}")`) })
    .or(sidebar.getByRole("button", { name: new RegExp(`^${displayNo}(?:,|\\s|$)`) }))
    .first()
    .click();
}

function makeVsatScoredResultFixture() {
  return {
    attemptId: MOCK_VSAT_ATTEMPT_ID,
    attemptNo: 1,
    examId: "00000000-0000-4000-8000-00000000e571",
    examVersionNo: 1,
    examName: "V-SAT Tiếng Anh · English Proficiency",
    examCode: "DS-VSAT-ANH",
    examKindCode: "VSAT",
    scope: "SUBJECT",
    subjectCode: "ANH",
    state: "SCORED",
    resultView: "SCORE_AND_ANSWERS",
    reviewAvailable: true,
    startedAt: "2026-11-22T01:25:00.000Z",
    finishedAt: "2026-11-22T02:15:00.000Z",
    gradedAt: "2026-11-22T02:15:05.000Z",
    endReason: "SUBMITTED",
    rawScore: 108,
    displayScore: 108,
    rawMaxScore: 150,
    statistics: {
      correctCount: 15,
      partialCount: 5,
      wrongCount: 3,
      blankCount: 2,
      pendingReviewCount: 0,
      totalCount: 25,
    },
    parts: [
      {
        partId: "part-vsat-anh",
        partCode: "ANH",
        partName: "Tiếng Anh",
        ord: 1,
        score: 108,
        maxScore: 150,
        pendingReview: false,
        skipped: false,
        stats: {
          correctCount: 15,
          partialCount: 5,
          wrongCount: 3,
          blankCount: 2,
          pendingReviewCount: 0,
          totalCount: 25,
        },
      },
    ],
    subjects: [
      {
        code: "ANH",
        name: "Tiếng Anh",
        pendingReview: false,
        score: 108,
        maxScore: 150,
        stats: {
          correctCount: 15,
          partialCount: 5,
          wrongCount: 3,
          blankCount: 2,
          pendingReviewCount: 0,
          totalCount: 25,
        },
      },
    ],
    segments: [
      {
        code: "P1",
        name: "Phần I",
        partCode: "ANH",
        fromPosition: 1,
        toPosition: 9,
        pendingReview: false,
        score: 40,
        maxPoints: 54,
        stats: { correctCount: 5, partialCount: 2, wrongCount: 1, blankCount: 1, pendingReviewCount: 0, totalCount: 9 },
      },
      {
        code: "P2",
        name: "Phần II",
        partCode: "ANH",
        fromPosition: 10,
        toPosition: 15,
        pendingReview: false,
        score: 26,
        maxPoints: 36,
        stats: { correctCount: 4, partialCount: 1, wrongCount: 1, blankCount: 0, pendingReviewCount: 0, totalCount: 6 },
      },
      {
        code: "P3",
        name: "Phần III",
        partCode: "ANH",
        fromPosition: 16,
        toPosition: 20,
        pendingReview: false,
        score: 20,
        maxPoints: 30,
        stats: { correctCount: 3, partialCount: 1, wrongCount: 1, blankCount: 0, pendingReviewCount: 0, totalCount: 5 },
      },
      {
        code: "P4",
        name: "Phần IV",
        partCode: "ANH",
        fromPosition: 21,
        toPosition: 25,
        pendingReview: false,
        score: 22,
        maxPoints: 30,
        stats: { correctCount: 3, partialCount: 1, wrongCount: 0, blankCount: 1, pendingReviewCount: 0, totalCount: 5 },
      },
    ],
  };
}

function makeVsatReviewPartialFixture() {
  return {
    attemptId: MOCK_VSAT_ATTEMPT_ID,
    state: "SCORED",
    resultView: "SCORE_AND_ANSWERS",
    selectedPartCode: "ANH",
    parts: [
      {
        partId: "part-vsat-anh",
        partCode: "ANH",
        partName: "Tiếng Anh",
        ord: 1,
        score: 108,
        maxScore: 150,
        state: "CLOSED",
      },
    ],
    items: [
      {
        itemId: "vsat-ri-1",
        partId: "part-vsat-anh",
        partCode: "ANH",
        parentItemId: null,
        displayNo: 1,
        ord: 1,
        unitCount: 4,
        questionType: "TRUE_FALSE",
        content: "<p>Read the short passage carefully and decide whether each statement is true or false.</p>",
        attachments: [],
        body: {
          scoringRule: "PARTIAL",
          statements: [
            { id: "st-a", content: "<p>Urban transit reduces morning congestion.</p>" },
            { id: "st-b", content: "<p>Electric buses require zero charging stations.</p>" },
            { id: "st-c", content: "<p>Dedicated lanes raise peak-hour reliability.</p>" },
            { id: "st-d", content: "<p>Fare cards work across all city districts.</p>" },
          ],
        },
        studentAnswer: {
          type: "TRUE_FALSE",
          statements: {
            "st-a": true,
            "st-b": true,
            "st-c": true,
            "st-d": false,
          },
        },
        pointsMax: 6,
        earnedPoints: 2,
        outcome: "PARTIAL",
        flagged: false,
        gradingDetail: {
          itemType: "TRUE_FALSE",
          scoringMethod: "COUNT_TABLE",
          matchedCount: 2,
          statementCount: 4,
          statementResults: [
            { id: "st-a", answered: true, matched: true },
            { id: "st-b", answered: true, matched: false },
            { id: "st-c", answered: true, matched: true },
            { id: "st-d", answered: true, matched: false },
          ],
        },
        expectedAnswer: {
          trueFalseKeys: [
            { statementId: "st-a", expected: true },
            { statementId: "st-b", expected: false },
            { statementId: "st-c", expected: true },
            { statementId: "st-d", expected: true },
          ],
        },
      },
      {
        itemId: "vsat-ri-2",
        partId: "part-vsat-anh",
        partCode: "ANH",
        parentItemId: null,
        displayNo: 2,
        ord: 2,
        unitCount: 4,
        questionType: "MATCHING",
        content: "<p>Match each term in column A with its best description in column B.</p>",
        attachments: [],
        body: {
          scoringRule: "PARTIAL",
          left: [
            { id: "l1", content: "<p>Solar grid</p>" },
            { id: "l2", content: "<p>Rain garden</p>" },
          ],
          right: [
            { id: "r1", content: "<p>Renewable micro-distribution</p>" },
            { id: "r2", content: "<p>Stormwater absorption basin</p>" },
            { id: "r3", content: "<p>Geothermal turbine</p>" },
          ],
        },
        studentAnswer: {
          type: "MATCHING",
          pairs: {
            l1: "r1",
            l2: "r3",
          },
        },
        pointsMax: 6,
        earnedPoints: 3,
        outcome: "PARTIAL",
        flagged: false,
        gradingDetail: {
          itemType: "MATCHING",
          reuseRight: false,
          pairCount: 2,
          pairResults: [
            { leftId: "l1", matched: true, pointsEarned: 3 },
            { leftId: "l2", matched: false, pointsEarned: 0 },
          ],
        },
        expectedAnswer: {
          matchingKeys: [
            { leftId: "l1", rightId: "r1" },
            { leftId: "l2", rightId: "r2" },
          ],
        },
      },
    ],
  };
}

async function installVsatResultAndReviewRoutes(page: Page): Promise<void> {
  await page.route(/\/api\/v1\/catalog\/options(\?.*)?$/, async (route) => {
    const group = new URL(route.request().url()).searchParams.get("group");
    const items =
      group === "SUBJECT"
        ? [{ id: "s-anh", code: "ANH", nameVi: "Tiếng Anh", nameEn: "English" }]
        : [{ id: "ek-vsat", code: "VSAT", nameVi: "V-SAT", nameEn: "V-SAT" }];
    await route.fulfill({ status: 200, contentType: "application/json", body: JSON.stringify(items) });
  });

  await page.route(`**/api/v1/me/attempts/${MOCK_VSAT_ATTEMPT_ID}/**`, async (route) => {
    const url = route.request().url();
    if (url.endsWith("/result")) {
      await route.fulfill({
        status: 200,
        contentType: "application/json",
        body: JSON.stringify(makeVsatScoredResultFixture()),
      });
      return;
    }
    if (url.endsWith("/review")) {
      await route.fulfill({
        status: 200,
        contentType: "application/json",
        body: JSON.stringify(makeVsatReviewPartialFixture()),
      });
      return;
    }
    await route.continue();
  });
}

test.describe("Ma trận giao diện FS-7 · AC-15 (8 màn hình × 8 ô = 64 ảnh)", () => {
  test.beforeAll(async () => {
    browser = await launchMatrixBrowser();
    await ensureDsReady(browser);

    const prepAccount = await createVerifiedAccount("visp7");
    prepareCookies = prepAccount.cookies;
    const prepContext = await browser.newContext();
    await prepContext.addCookies(prepareCookies);
    const prepPage = await prepContext.newPage();
    prepPage.setDefaultTimeout(25_000);
    try {
      const miniPrep = await purchaseExamViaUi(prepPage, "DS-HSCA-ANH-MINI");
      miniPrepareEntitlementId = miniPrep.entitlementId;
      const vsatPrep = await purchaseExamViaUi(prepPage, "DS-VSAT-ANH");
      vsatPrepareEntitlementId = vsatPrep.entitlementId;
    } finally {
      await prepContext.close();
    }

    const playAccount = await createVerifiedAccount("vis7");
    playerCookies = playAccount.cookies;

    const context = await browser.newContext();
    await context.addCookies(playerCookies);
    const page = await context.newPage();
    page.setDefaultTimeout(25_000);

    let faultedExamId = "";
    try {
      const mini = await purchaseExamViaUi(page, "DS-HSCA-ANH-MINI");
      const vsat = await purchaseExamViaUi(page, "DS-VSAT-ANH");
      const hoa = await purchaseExamViaUi(page, "DS-HSCA-HOA");

      // Create one real ABORTED audio-fault attempt on DS-HSCA-ANH-MINI first (refunds entitlement!).
      const prepRes = await page.request.get(
        `${WEB_URL}/api/v1/me/entitlements/${encodeURIComponent(mini.entitlementId)}/prepare`,
      );
      expect(prepRes.status()).toBe(200);
      const prepJson = (await prepRes.json()) as { examId: string };
      faultedExamId = prepJson.examId;

      await setE2eAudioFault(faultedExamId, true);

      await page.goto(`${WEB_URL}/prepare/${encodeURIComponent(mini.entitlementId)}`);
      const startBtn = page.getByRole("button", { name: "Bắt đầu bài thi" });
      await expect(startBtn).toBeVisible({ timeout: 15_000 });
      const createAbortAttemptPromise = page.waitForResponse(
        (res) => res.url().endsWith("/api/v1/me/attempts") && res.request().method() === "POST",
      );
      await startBtn.click();
      const createAbortJson = (await (await createAbortAttemptPromise).json()) as { attemptId?: string };
      abortedAudioAttemptId = createAbortJson.attemptId ?? "";
      expect(abortedAudioAttemptId).not.toBe("");

      await expect(page.locator('main [role="alert"]')).toContainText("Thiếu tệp âm thanh cần thiết cho đề thi.", {
        timeout: 25_000,
      });

      await setE2eAudioFault(faultedExamId, false);
      faultedExamId = "";

      // Start real active attempts reused across the 8 matrix cells.
      hscaMiniAttemptId = await startAttemptFromPrepare(page, mini.entitlementId);
      vsatAttemptId = await startAttemptFromPrepare(page, vsat.entitlementId);
      hscaHoaAttemptId = await startAttemptFromPrepare(page, hoa.entitlementId);

      // Advance DS-HSCA-HOA through Part 1 and Part 2 to reach Part 3 (SHORT_ANSWER + SymbolInputBar).
      await page.goto(`${WEB_URL}/attempts/${encodeURIComponent(hscaHoaAttemptId)}`);
      await expect(page.locator('header [role="timer"]')).toBeVisible({ timeout: 25_000 });
      for (let round = 0; round < 3; round += 1) {
        const hoaState = (await getAttemptStateApi(page.request, hscaHoaAttemptId)) as AttemptStateWithItemsPayload;
        const matchedPart = (hoaState.parts ?? []).find((part) =>
          (part.items ?? []).some((item) => item.questionType === "SHORT_ANSWER" && item.displayNo),
        );
        const symbolSa = matchedPart?.items?.find(
          (item) =>
            item.questionType === "SHORT_ANSWER" &&
            item.displayNo &&
            SYMBOL_BAR_ANSWER_FORMATS.has(item.questionBody?.answerFormat ?? ""),
        );
        const firstSa =
          symbolSa ?? matchedPart?.items?.find((item) => item.questionType === "SHORT_ANSWER" && item.displayNo);
        if (firstSa?.displayNo) {
          hoaShortDisplayNo = firstSa.displayNo;
          break;
        }
        await page.getByRole("button", { name: "Hoàn thành phần" }).click();
        const dialog = page.getByRole("dialog");
        await expect(dialog).toBeVisible({ timeout: 10_000 });
        await dialog.getByRole("button", { name: "Hoàn thành phần" }).click();
        await expect
          .poll(async () => {
            const st = (await getAttemptStateApi(page.request, hscaHoaAttemptId)) as AttemptStateWithItemsPayload;
            return (st.parts ?? []).some((p) => (p.items ?? []).some((i) => i.questionType === "SHORT_ANSWER"));
          }, { timeout: 15_000 })
          .toBeTruthy()
          .catch(() => undefined);
      }
    } finally {
      if (faultedExamId) {
        await setE2eAudioFault(faultedExamId, false).catch(() => undefined);
      }
      await context.close();
    }
  });

  test.afterAll(async () => {
    await browser?.close();
  });

  for (const cell of matrixCells()) {
    test(`AC-15 ô ${cellLabel(cell)}: 8 màn hình sạch tràn ngang, tương phản, ngôn ngữ và theme`, async () => {
      const strictOverflowOpts = { elementOverflow: { allowAccessibleScroller: true } } as const;
      const playerOverflowOpts = {
        elementOverflow: {
          allowAccessibleScroller: true,
          ignore: "pre,input,textarea,select,.katex-display,nav.overflow-x-auto",
        },
      } as const;

      const prepCell = await openCell(cell, prepareCookies);
      try {
        // 1. hsca-prepare-audio
        await prepCell.page.goto(`${WEB_URL}/prepare/${encodeURIComponent(miniPrepareEntitlementId)}`);
        await expect(
          prepCell.page.getByRole("button", { name: cell.lang === "en" ? "Start exam" : "Bắt đầu bài thi" }),
        ).toBeVisible({ timeout: 20_000 });
        await expectMatrixClean(prepCell.page, "hsca-prepare-audio", cell, strictOverflowOpts);

        // 2. vsat-prepare
        await prepCell.page.goto(`${WEB_URL}/prepare/${encodeURIComponent(vsatPrepareEntitlementId)}`);
        await expect(
          prepCell.page.getByRole("button", { name: cell.lang === "en" ? "Start exam" : "Bắt đầu bài thi" }),
        ).toBeVisible({ timeout: 20_000 });
        await expectMatrixClean(prepCell.page, "vsat-prepare", cell, strictOverflowOpts);
      } finally {
        await prepCell.context.close();
      }

      const { context, page } = await openCell(cell, playerCookies);
      try {
        await installVsatResultAndReviewRoutes(page);

        // 3. hsca-player-audio
        await page.goto(`${WEB_URL}/attempts/${encodeURIComponent(hscaMiniAttemptId)}`);
        await ensureActivePlayerSession(page, cell.lang);
        await expect(
          page.getByRole("region", { name: cell.lang === "en" ? "Audio player" : "Trình phát audio" }),
        ).toBeVisible({ timeout: 20_000 });
        await expectMatrixClean(page, "hsca-player-audio", cell, playerOverflowOpts);

        // 4. hsca-player-symbols
        await page.goto(`${WEB_URL}/attempts/${encodeURIComponent(hscaHoaAttemptId)}`);
        await ensureActivePlayerSession(page, cell.lang);
        await expect(page.locator("main fieldset[data-attempt-question-fieldset]")).toBeVisible({ timeout: 20_000 });
        const symbolGroup = page.getByRole("group", {
          name: cell.lang === "en" ? "Symbol input bar" : "Thanh chèn ký hiệu",
        });
        if (!(await symbolGroup.isVisible().catch(() => false)) && hoaShortDisplayNo > 1) {
          await jumpToQuestionDisplayNo(page, hoaShortDisplayNo, cell);
        }
        await expect(symbolGroup).toBeVisible({ timeout: 20_000 });
        await expectMatrixClean(page, "hsca-player-symbols", cell, playerOverflowOpts);

        // 5. vsat-player-segments (split cluster Q8-9 with shared passage & SegmentHeader)
        await page.goto(`${WEB_URL}/attempts/${encodeURIComponent(vsatAttemptId)}`);
        await ensureActivePlayerSession(page, cell.lang);
        await jumpToQuestionDisplayNo(page, 8, cell);
        await expect(page.getByText(cell.lang === "en" ? /^Question 8$/ : /^Câu 8$/).first()).toBeVisible({
          timeout: 10_000,
        });
        await expect(page.locator("[data-segment-header]")).toBeVisible({ timeout: 20_000 });
        await page.evaluate(() => window.scrollTo(0, 0));
        await expectMatrixClean(page, "vsat-player-segments", cell, playerOverflowOpts);

        // 6. vsat-result-segments
        await page.goto(`${WEB_URL}/attempts/${MOCK_VSAT_ATTEMPT_ID}/result`);
        await expect(page.locator('section[aria-labelledby="result-segments-title"]')).toBeVisible({
          timeout: 20_000,
        });
        await expectMatrixClean(page, "vsat-result-segments", cell, strictOverflowOpts);

        // 7. vsat-review-partial
        await page.goto(`${WEB_URL}/attempts/${MOCK_VSAT_ATTEMPT_ID}/review`);
        await expect(page.locator("#review-item-vsat-ri-1")).toBeVisible({ timeout: 20_000 });
        await expect(
          page.locator("#review-item-vsat-ri-1").getByText(
            cell.lang === "en" ? "Partially correct" : "Đúng một phần",
            { exact: true },
          ),
        ).toBeVisible();
        await expectMatrixClean(page, "vsat-review-partial", cell, strictOverflowOpts);

        // 8. hsca-audio-fault-retry
        await page.goto(`${WEB_URL}/attempts/${encodeURIComponent(abortedAudioAttemptId)}`);
        await expect(page.locator('main [role="alert"]')).toContainText(
          cell.lang === "en"
            ? "A required audio file for this exam is missing."
            : "Thiếu tệp âm thanh cần thiết cho đề thi.",
          { timeout: 20_000 },
        );
        await expect(
          page.getByRole("button", { name: cell.lang === "en" ? "Try again" : "Thử lại" }),
        ).toBeVisible();
        await expectMatrixClean(page, "hsca-audio-fault-retry", cell, strictOverflowOpts);
      } finally {
        await context.close();
      }
    });
  }
});
