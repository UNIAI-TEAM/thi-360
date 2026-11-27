import { expect, test, type Browser, type BrowserContext, type Page } from "@playwright/test";
import { newAccount, registerViaApi, solidPng, WEB_URL, type SessionCookies } from "./helpers/mail";
import {
  applyMatrixCookies,
  cellLabel,
  expectMatrixClean,
  launchMatrixBrowser,
  matrixCells,
  openMatrixContext,
  type MatrixCell,
} from "./helpers/visual-matrix";

/**
 * AC-21: Ma trận kiểm tra 8 ô (390px/1280px × sáng/tối × vi/en) cho đủ 10 trạng thái Kết quả, Xem lại & Lịch sử:
 * result-scored, result-vact-subsections, result-pending-review, result-regrading-fault,
 * review-answers, review-full-solution, review-score-only-403, history-list, history-empty, history-error.
 */
test.setTimeout(180_000);

const ATTEMPT_SCORED = "11111111-1111-4111-8111-111111111101";
const ATTEMPT_VACT = "11111111-1111-4111-8111-111111111102";
const ATTEMPT_PENDING = "11111111-1111-4111-8111-111111111103";
const ATTEMPT_REGRADE = "11111111-1111-4111-8111-111111111104";
const ATTEMPT_ANSWERS = "22222222-2222-4222-8222-222222222201";
const ATTEMPT_SOLUTION = "22222222-2222-4222-8222-222222222202";
const ATTEMPT_FORBIDDEN = "22222222-2222-4222-8222-222222222203";
const SOLUTION_ASSET_ID = "33333333-3333-4333-8333-333333333301";

const CATALOG_EXAM_OPTIONS = [
  { id: "ek-hsa", code: "HSA", nameVi: "ĐGNL HSA", nameEn: "HSA Assessment", displayOrder: 1, value: 1 },
  { id: "ek-vact", code: "VACT", nameVi: "ĐGNL ĐHQG TP.HCM", nameEn: "V-ACT Assessment", displayOrder: 2, value: 2 },
];

const CATALOG_SUBJECT_OPTIONS = [
  { id: "s-toan", code: "TOAN", nameVi: "Toán học", nameEn: "Mathematics", displayOrder: 1, value: 1 },
  { id: "s-nguvan", code: "NGUVAN", nameVi: "Ngữ văn", nameEn: "Literature", displayOrder: 2, value: 2 },
  { id: "s-vatli", code: "VATLI", nameVi: "Vật lí", nameEn: "Physics", displayOrder: 3, value: 3 },
  { id: "s-hoahoc", code: "HOAHOC", nameVi: "Hóa học", nameEn: "Chemistry", displayOrder: 4, value: 4 },
];

const RESULT_FIXTURES: Record<string, object> = {
  [ATTEMPT_SCORED]: {
    attemptId: ATTEMPT_SCORED,
    attemptNo: 2,
    examId: "aaaa1111-1111-4111-8111-111111111111",
    examName: "Đề HSA tổng hợp số 1",
    examKindCode: "HSA",
    scope: "FULL",
    state: "SCORED",
    resultView: "FULL_SOLUTION",
    reviewAvailable: true,
    rawScore: 112.5,
    rawMaxScore: 150,
    displayScore: 112.5,
    startedAt: "2026-11-20T01:00:00Z",
    finishedAt: "2026-11-20T02:20:00Z",
    endReason: "SUBMITTED",
    choices: { p3: { optionCode: "SCIENCE", subjectCodes: ["VATLI", "HOAHOC"] } },
    statistics: { correctCount: 95, partialCount: 4, wrongCount: 15, blankCount: 6, pendingReviewCount: 0, totalCount: 120 },
    parts: [
      { partId: "p1", partCode: "TOAN", partName: "Phần 1 · Toán học", ord: 1, score: 44, maxScore: 50, pendingReview: false, skipped: false, stats: { correctCount: 44, partialCount: 0, wrongCount: 4, blankCount: 2, totalCount: 50 } },
    ],
    subjects: [
      { code: "TOAN", name: "Toán học", score: 44, maxScore: 50, pendingReview: false, stats: { correctCount: 44, partialCount: 0, wrongCount: 4, blankCount: 2, totalCount: 50 } },
      { code: "VATLI", name: "Vật lí", score: 36, maxScore: 50, pendingReview: false, stats: { correctCount: 36, partialCount: 0, wrongCount: 10, blankCount: 4, totalCount: 50 } },
    ],
  },
  [ATTEMPT_VACT]: {
    attemptId: ATTEMPT_VACT,
    attemptNo: 1,
    examId: "aaaa2222-2222-4222-8222-222222222222",
    examName: "Đề V-ACT chuẩn 120 câu",
    examKindCode: "VACT",
    scope: "FULL",
    state: "SCORED",
    resultView: "FULL_SOLUTION",
    reviewAvailable: true,
    practiceScoring: "PRACTICE",
    scaleMax: 1200,
    rawMaxScore: 120,
    displayScore: 980,
    startedAt: "2026-11-20T01:00:00Z",
    finishedAt: "2026-11-20T03:00:00Z",
    endReason: "SUBMITTED",
    statistics: { correctCount: 97, partialCount: 0, wrongCount: 20, blankCount: 3, pendingReviewCount: 0, totalCount: 120 },
    vactComponents: [
      { componentCode: "VIETNAMESE", correctCount: 25, totalCount: 30, correctRate: 83.33 },
      { componentCode: "ANH", correctCount: 24, totalCount: 30, correctRate: 80 },
      { componentCode: "TOAN", correctCount: 26, totalCount: 30, correctRate: 86.67 },
      {
        componentCode: "SCIENTIFIC_THINKING",
        correctCount: 22,
        totalCount: 30,
        correctRate: 73.33,
        subsections: [
          { sectionId: "logic", questionCount: 12, correctCount: 10, totalCount: 12, correctRate: 83.33 },
          { sectionId: "sci", questionCount: 18, correctCount: 12, totalCount: 18, correctRate: 66.67 },
        ],
      },
    ],
  },
  [ATTEMPT_PENDING]: {
    attemptId: ATTEMPT_PENDING,
    attemptNo: 1,
    examId: "aaaa3333-3333-4333-8333-333333333333",
    examName: "Đề thi có tự luận Ngữ văn",
    examKindCode: "HSA",
    scope: "FULL",
    state: "PENDING_REVIEW",
    resultView: "SCORE_AND_ANSWERS",
    reviewAvailable: true,
    rawMaxScore: 150,
    startedAt: "2026-11-20T01:00:00Z",
    finishedAt: "2026-11-20T02:10:00Z",
    endReason: "SUBMITTED",
    statistics: { correctCount: 68, partialCount: 2, wrongCount: 10, blankCount: 5, pendingReviewCount: 2, totalCount: 87 },
    parts: [
      { partId: "pa", partCode: "TOAN", partName: "Phần Toán", ord: 1, score: 75, maxScore: 100, pendingReview: false, stats: { correctCount: 68, partialCount: 2, wrongCount: 10, blankCount: 5, totalCount: 85 } },
      { partId: "pb", partCode: "VAN", partName: "Phần Tự luận Ngữ văn", ord: 2, maxScore: 50, pendingReview: true },
    ],
    subjects: [
      { code: "TOAN", name: "Toán học", score: 75, maxScore: 100, pendingReview: false, stats: { correctCount: 68, partialCount: 2, wrongCount: 10, blankCount: 5, totalCount: 85 } },
      { code: "NGUVAN", name: "Ngữ văn", maxScore: 50, pendingReview: true, stats: { correctCount: 0, partialCount: 0, wrongCount: 0, blankCount: 0, pendingReviewCount: 2, totalCount: 2 } },
    ],
  },
  [ATTEMPT_REGRADE]: {
    attemptId: ATTEMPT_REGRADE,
    attemptNo: 3,
    examId: "aaaa4444-4444-4444-8444-444444444444",
    examName: "Đề chấm lại sau sự cố",
    examKindCode: "HSA",
    scope: "FULL",
    state: "PENDING_REVIEW",
    regrading: true,
    compensated: true,
    faultFlag: "SYSTEM_FAULT",
    faultReason: "Lỗi mất kết nối tạm thời lúc 09:42",
    resultView: "SCORE_AND_ANSWERS",
    reviewAvailable: true,
    displayScore: 112,
    rawScore: 112,
    rawMaxScore: 150,
    startedAt: "2026-11-20T01:00:00Z",
    finishedAt: "2026-11-20T02:30:00Z",
    endReason: "FAULT_CLOSED",
    statistics: { correctCount: 80, partialCount: 4, wrongCount: 12, blankCount: 4, pendingReviewCount: 1, totalCount: 101 },
    parts: [
      { partId: "pr1", partCode: "P1", partName: "Phần 1 · Toán học", ord: 1, score: 80, maxScore: 100, pendingReview: false },
      { partId: "pr2", partCode: "P2", partName: "Phần 2 · Tự chọn", ord: 2, score: 0, maxScore: 50, skipped: true },
    ],
  },
  [ATTEMPT_ANSWERS]: {
    attemptId: ATTEMPT_ANSWERS,
    attemptNo: 1,
    examId: "aaaa1111-1111-4111-8111-111111111111",
    examName: "Đề HSA tổng hợp số 1",
    examKindCode: "HSA",
    scope: "FULL",
    state: "SCORED",
    resultView: "SCORE_AND_ANSWERS",
    reviewAvailable: true,
    rawScore: 5.5,
    rawMaxScore: 8,
    displayScore: 5.5,
    startedAt: "2026-11-20T01:00:00Z",
    finishedAt: "2026-11-20T01:45:00Z",
    endReason: "SUBMITTED",
  },
  [ATTEMPT_SOLUTION]: {
    attemptId: ATTEMPT_SOLUTION,
    attemptNo: 2,
    examId: "aaaa5555-5555-4555-8555-555555555555",
    examName: "Đề HSA theo môn Toán",
    examKindCode: "HSA",
    scope: "SUBJECT",
    subjectCode: "TOAN",
    state: "SCORED",
    resultView: "FULL_SOLUTION",
    reviewAvailable: true,
    rawScore: 6.5,
    rawMaxScore: 8,
    displayScore: 6.5,
    startedAt: "2026-11-20T03:00:00Z",
    finishedAt: "2026-11-20T03:40:00Z",
    endReason: "SUBMITTED",
  },
};

function makeReviewFixture(attemptId: string, resultView: "SCORE_AND_ANSWERS" | "FULL_SOLUTION") {
  const sol = resultView === "FULL_SOLUTION"
    ? `<p>Lời giải chi tiết bằng phương pháp thế.</p><figure class="image" data-width="medium" data-zoom="true"><img data-asset-id="${SOLUTION_ASSET_ID}" src="/api/v1/me/attempts/${attemptId}/solution-assets/${SOLUTION_ASSET_ID}" alt="Minh họa lời giải" /></figure>`
    : undefined;
  return {
    attemptId,
    state: "SCORED",
    resultView,
    selectedPartCode: "P1",
    parts: [
      { partId: "rp1", partCode: "P1", partName: "Phần 1 · Tổng hợp", ord: 1, score: 9, maxScore: 12, state: "CLOSED" },
      { partId: "rp2", partCode: "P2", partName: "Phần 2 · Bị bỏ qua", ord: 2, score: 0, maxScore: 10, state: "SKIPPED" },
    ],
    items: [
      {
        itemId: "ri-1",
        partId: "rp1",
        partCode: "P1",
        ord: 1,
        displayNo: 1,
        questionType: "SINGLE_CHOICE",
        content: "<p>Chọn phương án đúng cho phương trình x + 2 = 5.</p>",
        pointsMax: 1,
        earnedPoints: 1,
        outcome: "CORRECT",
        optionOrder: ["o2", "o1"],
        body: { options: [{ id: "o1", content: "<p>x = 4</p>" }, { id: "o2", content: "<p>x = 3</p>" }] },
        studentAnswer: { type: "SINGLE_CHOICE", optionId: "o2" },
        expectedAnswer: { expectedOptionIds: ["o2"] },
        ...(sol ? { solution: sol } : {}),
      },
      {
        itemId: "ri-2",
        partId: "rp1",
        partCode: "P1",
        ord: 2,
        displayNo: 2,
        questionType: "MULTIPLE_CHOICE",
        content: "<p>Chọn các số nguyên tố.</p>",
        pointsMax: 1,
        earnedPoints: 0.5,
        outcome: "PARTIAL",
        body: { scoringRule: "PARTIAL", options: [{ id: "m1", content: "<p>2</p>" }, { id: "m2", content: "<p>4</p>" }, { id: "m3", content: "<p>5</p>" }] },
        studentAnswer: { type: "MULTIPLE_CHOICE", optionIds: ["m1"] },
        expectedAnswer: { expectedOptionIds: ["m1", "m3"] },
      },
      {
        itemId: "ri-3",
        partId: "rp1",
        partCode: "P1",
        ord: 3,
        displayNo: 3,
        questionType: "SHORT_ANSWER",
        content: "<p>Tính giá trị 15 + 25.</p>",
        pointsMax: 1,
        earnedPoints: 0,
        outcome: "WRONG",
        body: { answerFormat: "INTEGER" },
        studentAnswer: { type: "SHORT_ANSWER", text: "39" },
        expectedAnswer: { acceptedTexts: ["40"] },
      },
      {
        itemId: "ri-4",
        partId: "rp1",
        partCode: "P1",
        ord: 4,
        displayNo: 4,
        questionType: "ESSAY",
        content: "<p>Trình bày ngắn gọn giải pháp tiết kiệm nước.</p>",
        pointsMax: 5,
        earnedPoints: 4,
        outcome: "PARTIAL",
        body: { wordLimit: 150 },
        studentAnswer: { type: "ESSAY", html: "<p>Khóa vòi nước khi không dùng.</p>", wordCount: 6 },
        gradingDetail: { wordCount: 6 },
        ...(resultView === "FULL_SOLUTION"
          ? {
              essayFeedback: {
                points: 4,
                comment: "Ý chính rõ ràng",
                criteria: [{ id: "Nội dung", points: 2.5, comment: "Đúng trọng tâm" }],
              },
            }
          : {}),
      },
    ],
  };
}

const HISTORY_LIST_FIXTURE = {
  page: 0,
  size: 20,
  totalItems: 3,
  totalPages: 1,
  items: [
    {
      attemptId: ATTEMPT_SCORED,
      examId: "aaaa1111-1111-4111-8111-111111111111",
      examName: "Đề HSA tổng hợp số 1",
      examKindCode: "HSA",
      scope: "FULL",
      attemptNo: 2,
      startedAt: "2026-11-20T01:00:00Z",
      state: "SCORED",
      action: "VIEW_RESULT",
      displayScore: 0,
      maxScore: 150,
    },
    {
      attemptId: ATTEMPT_VACT,
      examId: "aaaa2222-2222-4222-8222-222222222222",
      examName: "Đề V-ACT chuẩn 120 câu",
      examKindCode: "VACT",
      scope: "FULL",
      attemptNo: 1,
      startedAt: "2026-11-19T01:00:00Z",
      state: "SCORED",
      action: "VIEW_RESULT",
      displayScore: 980,
      scaleMax: 1200,
      maxScore: 120,
    },
    {
      attemptId: ATTEMPT_REGRADE,
      examId: "aaaa4444-4444-4444-8444-444444444444",
      examName: "Đề chấm lại sau sự cố",
      examKindCode: "HSA",
      scope: "SUBJECT",
      subjectCode: "TOAN",
      attemptNo: 3,
      startedAt: "2026-11-18T01:00:00Z",
      state: "PENDING_REVIEW",
      regrading: true,
      compensated: true,
      action: "VIEW_RESULT",
      displayScore: 112,
      maxScore: 150,
    },
  ],
};

let browser: Browser;
let sessionCookies: SessionCookies;

test.beforeAll(async () => {
  browser = await launchMatrixBrowser();
  sessionCookies = await registerViaApi(newAccount("visual.fs6"));
});

test.afterAll(async () => {
  await browser.close();
});

type HistoryMode = "list" | "empty" | "error";

async function openStudentCell(
  cell: MatrixCell,
): Promise<{ context: BrowserContext; page: Page; setHistoryMode: (mode: HistoryMode) => void }> {
  const context = await openMatrixContext(browser, cell, WEB_URL);
  await context.addCookies(sessionCookies);
  await applyMatrixCookies(context, cell, WEB_URL);
  const page = await context.newPage();
  page.setDefaultTimeout(20_000);

  let historyMode: HistoryMode = "list";

  await page.route(/\/api\/v1\/catalog\/options(\?.*)?$/, async (route) => {
    const group = new URL(route.request().url()).searchParams.get("group");
    const body = group === "SUBJECT" ? CATALOG_SUBJECT_OPTIONS : CATALOG_EXAM_OPTIONS;
    await route.fulfill({ status: 200, contentType: "application/json", body: JSON.stringify(body) });
  });

  await page.route(/\/api\/v1\/me\/attempts(\?.*)?$/, async (route) => {
    if (route.request().method() !== "GET") {
      await route.continue();
      return;
    }
    if (historyMode === "error") {
      await route.fulfill({
        status: 500,
        contentType: "application/json",
        body: JSON.stringify({ code: "INTERNAL_ERROR" }),
      });
      return;
    }
    if (historyMode === "empty") {
      await route.fulfill({
        status: 200,
        contentType: "application/json",
        body: JSON.stringify({ page: 0, size: 20, totalItems: 0, totalPages: 0, items: [] }),
      });
      return;
    }
    await route.fulfill({
      status: 200,
      contentType: "application/json",
      body: JSON.stringify(HISTORY_LIST_FIXTURE),
    });
  });

  await page.route(/\/api\/v1\/me\/attempts\/.+/, async (route) => {
    const url = route.request().url();
    if (url.includes("/solution-assets/")) {
      await route.fulfill({ status: 200, contentType: "image/png", body: solidPng(64) });
      return;
    }
    const resultMatch = /\/api\/v1\/me\/attempts\/([^/?#]+)\/result/.exec(url);
    if (resultMatch?.[1] && RESULT_FIXTURES[resultMatch[1]]) {
      await route.fulfill({
        status: 200,
        contentType: "application/json",
        body: JSON.stringify(RESULT_FIXTURES[resultMatch[1]]),
      });
      return;
    }
    const reviewMatch = /\/api\/v1\/me\/attempts\/([^/?#]+)\/review/.exec(url);
    if (reviewMatch?.[1]) {
      if (reviewMatch[1] === ATTEMPT_FORBIDDEN) {
        await route.fulfill({
          status: 403,
          contentType: "application/json",
          body: JSON.stringify({ code: "REVIEW_NOT_INCLUDED", message: "Review not included" }),
        });
        return;
      }
      const mode = reviewMatch[1] === ATTEMPT_SOLUTION ? "FULL_SOLUTION" : "SCORE_AND_ANSWERS";
      await route.fulfill({
        status: 200,
        contentType: "application/json",
        body: JSON.stringify(makeReviewFixture(reviewMatch[1], mode)),
      });
      return;
    }
    await route.continue();
  });

  return {
    context,
    page,
    setHistoryMode: (mode: HistoryMode) => {
      historyMode = mode;
    },
  };
}

async function settle(page: Page): Promise<void> {
  await expect(page.locator('[aria-busy="true"]')).toHaveCount(0);
  await page.evaluate(() => document.fonts.ready);
}

for (const cell of matrixCells()) {
  test.describe(cellLabel(cell), () => {
    test("AC-21: result (4 trạng thái) + review (3 trạng thái) + history (3 trạng thái)", async () => {
      const { context, page, setHistoryMode } = await openStudentCell(cell);
      try {
        // 1. result-scored (with visible StudentHeader)
        await page.goto(`${WEB_URL}/attempts/${ATTEMPT_SCORED}/result`);
        await expect(page.getByRole("heading", { level: 1, name: "Đề HSA tổng hợp số 1" })).toBeVisible();
        await settle(page);
        await expect(page.locator("header nav")).toBeVisible();
        await expectMatrixClean(page, "result-scored", cell);

        // 2. result-vact-subsections (expanded subsections)
        await page.goto(`${WEB_URL}/attempts/${ATTEMPT_VACT}/result`);
        await expect(page.getByRole("heading", { level: 1, name: "Đề V-ACT chuẩn 120 câu" })).toBeVisible();
        await settle(page);
        const sciToggle = page.locator("button[data-component-code='SCIENTIFIC_THINKING']");
        await sciToggle.click();
        await expect(sciToggle).toHaveAttribute("aria-expanded", "true");
        await expect(page.getByText(/10\/12/)).toBeVisible();
        await expect(page.getByText(/12\/18/)).toBeVisible();
        await expectMatrixClean(page, "result-vact-subsections", cell);

        // 3. result-pending-review
        await page.goto(`${WEB_URL}/attempts/${ATTEMPT_PENDING}/result`);
        await expect(page.getByRole("heading", { level: 1, name: "Đề thi có tự luận Ngữ văn" })).toBeVisible();
        await settle(page);
        await expectMatrixClean(page, "result-pending-review", cell);

        // 4. result-regrading-fault
        await page.goto(`${WEB_URL}/attempts/${ATTEMPT_REGRADE}/result`);
        await expect(page.getByRole("heading", { level: 1, name: "Đề chấm lại sau sự cố" })).toBeVisible();
        await expect(page.locator("main [role='status']").first()).toBeVisible();
        await settle(page);
        await expectMatrixClean(page, "result-regrading-fault", cell);

        // 5. review-answers (with visible StudentHeader, no essayFeedback under SCORE_AND_ANSWERS per R1-7)
        await page.goto(`${WEB_URL}/attempts/${ATTEMPT_ANSWERS}/review`);
        await expect(page.locator("#review-item-ri-1")).toBeVisible();
        await expect(page.locator("main")).not.toContainText(/Điểm từng tiêu chí|Criterion breakdown/);
        await settle(page);
        await expect(page.locator("header nav")).toBeVisible();
        await expectMatrixClean(page, "review-answers", cell);

        // 6. review-full-solution (solution PNG image loaded)
        await page.goto(`${WEB_URL}/attempts/${ATTEMPT_SOLUTION}/review`);
        await expect(page.locator("#review-item-ri-1")).toBeVisible();
        const solutionImg = page.locator('img[src*="/solution-assets/"]').first();
        await expect(solutionImg).toBeVisible();
        await expect
          .poll(() =>
            solutionImg.evaluate((node) => {
              const img = node as HTMLImageElement;
              return img.complete && img.naturalWidth > 0;
            }),
          )
          .toBe(true);
        await settle(page);
        await expectMatrixClean(page, "review-full-solution", cell);

        // 7. review-score-only-403
        await page.goto(`${WEB_URL}/attempts/${ATTEMPT_FORBIDDEN}/review`);
        await expect(
          page.getByRole("heading", {
            level: 1,
            name: /Bài thi này không có phần xem lại bài làm|This exam does not include attempt review/,
          }),
        ).toBeVisible();
        await expect(page.getByRole("heading", { name: /Không tìm thấy trang|Page not found/ })).toHaveCount(0);
        await settle(page);
        await expect(page).toHaveURL(new RegExp(`/attempts/${ATTEMPT_FORBIDDEN}/review$`));
        await expect(page.locator("header nav")).toBeVisible();
        await expectMatrixClean(page, "review-score-only-403", cell);

        // 8. history-list
        setHistoryMode("list");
        await page.goto(`${WEB_URL}/history`);
        await expect(page.getByText("Đề HSA tổng hợp số 1").first()).toBeVisible();
        await settle(page);
        await expectMatrixClean(page, "history-list", cell);

        // 9. history-empty
        setHistoryMode("empty");
        await page.goto(`${WEB_URL}/history?q=KHONGCO`);
        await expect(page.getByRole("button", { name: /Xóa bộ lọc|Clear filters/ }).last()).toBeVisible();
        await settle(page);
        await expectMatrixClean(page, "history-empty", cell);

        // 10. history-error
        setHistoryMode("error");
        await page.goto(`${WEB_URL}/history?page=1`);
        await expect(page.locator("main [role=alert]").first()).toBeVisible();
        await settle(page);
        await expectMatrixClean(page, "history-error", cell);
      } finally {
        await context.close();
      }
    });
  });
}
