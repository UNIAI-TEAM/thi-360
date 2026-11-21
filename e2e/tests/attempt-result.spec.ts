import { randomUUID } from "node:crypto";
import { expect, test, type APIRequestContext, type Browser, type BrowserContext, type Page } from "@playwright/test";
import { ADMIN_URL, adminApi, loginAdmin, RUN_ID } from "./helpers/editor-qa";
import { createVerifiedAccount, WEB_URL, type SessionCookies } from "./helpers/mail";
import { examByCode, publishedExamSet, waitForPublishedExamSet } from "./helpers/published-exam-set";
import { publishApi, type CheckResult } from "./helpers/wave-2b";

/**
 * E2E chức năng cho FS-6 (UNI-2735):
 * - AC-10: SCORE không cho xem lại (ẩn CTA ở /result, truy cập thẳng /review hiện màn 403 thân thiện)
 * - AC-13 & AC-16: /history lọc + tìm kiếm, điều hướng tới /result rồi Back giữ nguyên URL query & dữ liệu
 * - AC-17: 404 (id lạ & cross-user), 409 ATTEMPT_NOT_FINISHED khi lượt còn mở
 * - AC-23: Ảnh lời giải (/solution-assets/) trên DS-HSA-SUBJECT-TOAN — 0 request khi SCORE_AND_ANSWERS;
 *          khi admin chuyển listing sang FULL_SOLUTION và publish, mở /review tải ảnh 200 & naturalWidth > 0 (khôi phục trong finally).
 */
test.setTimeout(300_000);

interface ListingSnapshot {
  revision: number;
  coverAssetId?: string | null;
  summary?: string | null;
  description?: string | null;
  objectives?: string | null;
  audience?: string | null;
  listPriceVnd?: number | null;
  attemptsGranted?: number | null;
  benefitAiExplanation?: boolean;
  benefitStrengthWeakness?: boolean;
  benefitPractice?: boolean;
  benefitNote?: string | null;
  resultView: string;
}

function xsrfFromCookies(cookies: SessionCookies): string {
  return cookies.find((c) => c.name === "thi360_xsrf")?.value ?? "";
}

async function buyExamViaCheckout(
  browser: Browser,
  cookies: SessionCookies,
  productCode: string,
): Promise<void> {
  const ctx = await browser.newContext();
  try {
    await ctx.addCookies(cookies);
    const page = await ctx.newPage();
    await page.goto(`${WEB_URL}/checkout/${encodeURIComponent(productCode)}`);
    await expect(page.getByRole("heading", { level: 1, name: "Xác nhận mua" })).toBeVisible();
    const freeSubmit = page.getByRole("button", { name: "Xác nhận nhận bài thi" });
    if (await freeSubmit.isVisible()) {
      await freeSubmit.click();
    } else {
      await page.getByRole("button", { name: "Thanh toán qua VNPAY" }).click();
      await page.waitForURL(/\/mock-vnpay/);
      await page.locator('a[data-mock-action="success"]').click();
    }
    await page.waitForURL(/\/checkout\/result/);
    await expect(page.getByRole("heading", { level: 1, name: "Đã cấp bài thi" })).toBeVisible();
  } finally {
    await ctx.close();
  }
}

async function findEntitlementId(
  request: APIRequestContext,
  examCode: string,
): Promise<{ entitlementId: string; examId: string; remainingAttempts: number }> {
  const res = await request.get(`${WEB_URL}/api/v1/me/exams`);
  expect(res.ok(), `GET /api/v1/me/exams: ${res.status()}`).toBe(true);
  const body = (await res.json()) as {
    items: Array<{
      entitlementId: string;
      examId: string;
      code: string;
      remainingAttempts?: number | null;
    }>;
  };
  const match = body.items.find((item) => item.code === examCode);
  expect(match, `Missing entitlement for ${examCode}`).toBeDefined();
  return {
    entitlementId: match!.entitlementId,
    examId: match!.examId,
    remainingAttempts: match!.remainingAttempts ?? 0,
  };
}

async function startAttemptToInProgress(
  request: APIRequestContext,
  xsrf: string,
  entitlementId: string,
): Promise<{ attemptId: string; sessionId: string }> {
  const startRes = await request.post(`${WEB_URL}/api/v1/me/attempts`, {
    headers: {
      origin: WEB_URL,
      "x-xsrf-token": xsrf,
      "Idempotency-Key": randomUUID(),
    },
    data: { entitlementId },
  });
  expect(startRes.ok(), `POST /api/v1/me/attempts: ${startRes.status()} ${await startRes.text()}`).toBe(true);
  const { attemptId } = (await startRes.json()) as { attemptId: string };

  let inProgress = false;
  for (let i = 0; i < 60; i++) {
    const stateRes = await request.get(`${WEB_URL}/api/v1/me/attempts/${attemptId}`);
    expect(stateRes.ok(), `GET /attempts/${attemptId}: ${stateRes.status()}`).toBe(true);
    const stateBody = (await stateRes.json()) as { state: string };
    if (stateBody.state === "IN_PROGRESS") {
      inProgress = true;
      break;
    }
    if (stateBody.state === "ABORTED") {
      throw new Error(`Attempt ${attemptId} aborted while starting`);
    }
    await new Promise((resolve) => setTimeout(resolve, 1_000));
  }
  expect(inProgress, `Attempt ${attemptId} did not enter IN_PROGRESS`).toBe(true);

  const sessionRes = await request.post(`${WEB_URL}/api/v1/me/attempts/${attemptId}/session`, {
    headers: { origin: WEB_URL, "x-xsrf-token": xsrf },
    data: { takeover: true },
  });
  expect(sessionRes.ok(), `POST /session: ${sessionRes.status()} ${await sessionRes.text()}`).toBe(true);
  const { sessionId } = (await sessionRes.json()) as { sessionId: string };

  return { attemptId, sessionId };
}

async function submitAndWaitScored(
  request: APIRequestContext,
  xsrf: string,
  attemptId: string,
  sessionId: string,
): Promise<void> {
  const submitRes = await request.post(`${WEB_URL}/api/v1/me/attempts/${attemptId}/submit`, {
    headers: {
      origin: WEB_URL,
      "x-xsrf-token": xsrf,
      "Idempotency-Key": randomUUID(),
      "X-Attempt-Session": sessionId,
    },
    data: {},
  });
  expect(submitRes.ok(), `POST /submit: ${submitRes.status()} ${await submitRes.text()}`).toBe(true);

  let scored = false;
  for (let i = 0; i < 45; i++) {
    const res = await request.get(`${WEB_URL}/api/v1/me/attempts/${attemptId}/result`);
    if (res.ok()) {
      const data = (await res.json()) as { state: string };
      if (data.state === "SCORED" || data.state === "PENDING_REVIEW") {
        scored = true;
        break;
      }
    }
    await new Promise((resolve) => setTimeout(resolve, 1_000));
  }
  expect(scored, `Attempt ${attemptId} did not finish grading`).toBe(true);
}

async function setExamResultViewAndPublish(examCode: string, resultView: string): Promise<string> {
  const admin = await adminApi();
  const { api, headers } = admin;
  try {
    const listRes = await api.get(`${ADMIN_URL}/api/v1/admin/exams`, { params: { q: examCode, page: "0", size: "50" } });
    expect(listRes.ok(), `GET /admin/exams?q=${examCode}`).toBe(true);
    const listBody = (await listRes.json()) as { items: Array<{ id: string; code: string }> };
    const exam = listBody.items.find((item) => item.code === examCode);
    expect(exam, `Exam ${examCode} not found`).toBeDefined();
    const examId = exam!.id;

    const getListingRes = await api.get(`${ADMIN_URL}/api/v1/admin/exams/${examId}/listing`);
    expect(getListingRes.ok(), `GET listing ${examId}`).toBe(true);
    const before = (await getListingRes.json()) as ListingSnapshot;
    const previousResultView = before.resultView;

    if (previousResultView !== resultView) {
      const putRes = await api.put(`${ADMIN_URL}/api/v1/admin/exams/${examId}/listing`, {
        headers,
        data: {
          expectedRevision: before.revision,
          coverAssetId: before.coverAssetId ?? null,
          summary: before.summary ?? null,
          description: before.description ?? null,
          objectives: before.objectives ?? null,
          audience: before.audience ?? null,
          listPriceVnd: before.listPriceVnd ?? null,
          attemptsGranted: before.attemptsGranted ?? null,
          benefitAiExplanation: before.benefitAiExplanation ?? false,
          benefitStrengthWeakness: before.benefitStrengthWeakness ?? false,
          benefitPractice: before.benefitPractice ?? false,
          benefitNote: before.benefitNote ?? null,
          resultView,
        },
      });
      expect(putRes.ok(), `PUT listing ${examId}: ${await putRes.text()}`).toBe(true);

      const checkRes = await api.post(`${ADMIN_URL}/api/v1/admin/exams/${examId}/check`, {
        headers,
        data: {},
      });
      expect(checkRes.ok(), `POST check ${examId}: ${await checkRes.text()}`).toBe(true);
      const checkBody = (await checkRes.json()) as CheckResult;
      const pathIds = checkBody.paths.map((p) => p.pathId);
      expect(pathIds.length, `No publishable pathIds for ${examCode}`).toBeGreaterThan(0);

      const pubRes = await publishApi(admin, examId, pathIds);
      expect(pubRes.ok(), `POST publish ${examId}: ${await pubRes.text()}`).toBe(true);
    }

    return previousResultView;
  } finally {
    await api.dispose();
  }
}

test.describe("FS-6 Kết quả, Xem lại & Lịch sử", () => {
  test.beforeAll(async ({ browser }) => {
    const context = await browser.newContext();
    try {
      await loginAdmin(context, "vi");
      await waitForPublishedExamSet(context.request, { timeoutMs: 240_000 });
    } finally {
      await context.close();
    }
  });

  test("AC-10, AC-13, AC-16, AC-17: SCORE ẩn xem lại, 403/404/409, và Lịch sử lọc + Back giữ query", async ({
    browser,
    context,
    page,
  }) => {
    const set = publishedExamSet();
    const scoreOnly = examByCode(set.scoreOnlyExam);

    const owner = await createVerifiedAccount(`fs6.owner.${RUN_ID}`);
    await context.addCookies(owner.cookies);
    const xsrf = xsrfFromCookies(owner.cookies);

    await buyExamViaCheckout(browser, owner.cookies, scoreOnly.productCode);
    const ent = await findEntitlementId(context.request, scoreOnly.code);

    // 1. In-progress attempt -> /result shows 409 ATTEMPT_NOT_FINISHED fallback (AC-17)
    const { attemptId, sessionId } = await startAttemptToInProgress(context.request, xsrf, ent.entitlementId);
    await page.goto(`${WEB_URL}/attempts/${attemptId}/result`);
    await expect(page.getByRole("heading", { level: 1, name: "Lượt thi chưa kết thúc" })).toBeVisible();
    await expect(page.getByRole("link", { name: "Tiếp tục làm bài" })).toHaveAttribute("href", `/attempts/${attemptId}`);

    // 2. Submit -> SCORED + SCORE hides review CTA on /result, direct /review shows 403 (AC-10)
    await submitAndWaitScored(context.request, xsrf, attemptId, sessionId);
    await page.reload();
    await expect(page.getByRole("heading", { level: 1, name: scoreOnly.name })).toBeVisible();
    await expect(page.getByRole("link", { name: /Xem lại/ })).toHaveCount(0);
    await expect(page.getByText(/chỉ cho xem điểm/i)).toBeVisible();

    await page.goto(`${WEB_URL}/attempts/${attemptId}/review`);
    await expect(page.getByRole("heading", { level: 1, name: "Bài thi này không có phần xem lại bài làm" })).toBeVisible();
    await page.getByRole("link", { name: "Về kết quả" }).first().click();
    await expect(page).toHaveURL(`${WEB_URL}/attempts/${attemptId}/result`);

    // 3. Unknown 404 & cross-user ownership isolation -> 404 (AC-17)
    await page.goto(`${WEB_URL}/attempts/00000000-0000-4000-8000-000000000404/result`);
    await expect(page.getByRole("heading", { level: 1, name: "Không tìm thấy kết quả lượt thi" })).toBeVisible();

    const stranger = await createVerifiedAccount(`fs6.other.${RUN_ID}`);
    const strangerCtx: BrowserContext = await browser.newContext();
    try {
      await strangerCtx.addCookies(stranger.cookies);
      const strangerPage: Page = await strangerCtx.newPage();
      await strangerPage.goto(`${WEB_URL}/attempts/${attemptId}/result`);
      await expect(strangerPage.getByRole("heading", { level: 1, name: "Không tìm thấy kết quả lượt thi" })).toBeVisible();
    } finally {
      await strangerCtx.close();
    }

    // 4. /history search + Back from /result preserves URL query and rows (AC-13, AC-16)
    await page.goto(`${WEB_URL}/history`);
    await expect(page.getByRole("heading", { level: 1, name: "Lịch sử luyện thi" })).toBeVisible();
    const searchInput = page.getByLabel("Tìm theo tên bài");
    await searchInput.fill(scoreOnly.name.slice(0, 12));
    await expect(page).toHaveURL(/\/history\?q=/);
    const filteredUrl = page.url();

    await page.getByRole("link", { name: "Xem kết quả" }).first().click();
    await expect(page).toHaveURL(new RegExp(`/attempts/${attemptId}/result$`));
    await page.goBack();
    await expect(page).toHaveURL(filteredUrl);
    await expect(page.getByText(scoreOnly.name).first()).toBeVisible();
  });

  test("AC-23: ảnh lời giải (/solution-assets/) ẩn hoàn toàn ở SCORE_AND_ANSWERS và tải 200 ở FULL_SOLUTION", async ({
    browser,
    context,
    page,
  }) => {
    const set = publishedExamSet();
    const imgExam = examByCode(set.imageQuestion.examCode);

    const student = await createVerifiedAccount(`fs6.img.${RUN_ID}`);
    await context.addCookies(student.cookies);
    const xsrf = xsrfFromCookies(student.cookies);

    await buyExamViaCheckout(browser, student.cookies, imgExam.productCode);
    const ent1 = await findEntitlementId(context.request, imgExam.code);
    const { attemptId: saAttemptId, sessionId: saSessionId } = await startAttemptToInProgress(
      context.request,
      xsrf,
      ent1.entitlementId,
    );
    await submitAndWaitScored(context.request, xsrf, saAttemptId, saSessionId);

    const solutionRequestsWhenLocked: string[] = [];
    const onReq = (reqUrl: string) => {
      if (reqUrl.includes("/solution-assets/")) {
        solutionRequestsWhenLocked.push(reqUrl);
      }
    };
    const reqListener = (req: { url(): string }) => onReq(req.url());
    page.on("request", reqListener);
    await page.goto(`${WEB_URL}/attempts/${saAttemptId}/review`);
    await expect(page.getByRole("heading", { level: 1, name: "Xem lại bài làm" })).toBeVisible();
    await expect(page.getByText("Không hiển thị lời giải cho mức quyền lợi hiện tại.")).toBeVisible();
    page.off("request", reqListener);

    expect(solutionRequestsWhenLocked).toHaveLength(0);
    await expect(page.locator('img[src*="/solution-assets/"]')).toHaveCount(0);

    // Toggle DS-HSA-SUBJECT-TOAN listing to FULL_SOLUTION, re-publish, then always restore in finally
    const previousResultView = await setExamResultViewAndPublish(imgExam.code, "FULL_SOLUTION");
    try {
      let entAfter = await findEntitlementId(context.request, imgExam.code);
      if (entAfter.remainingAttempts <= 0) {
        await buyExamViaCheckout(browser, student.cookies, imgExam.productCode);
        entAfter = await findEntitlementId(context.request, imgExam.code);
      }
      const { attemptId: fullAttemptId, sessionId: fullSessionId } = await startAttemptToInProgress(
        context.request,
        xsrf,
        entAfter.entitlementId,
      );
      await submitAndWaitScored(context.request, xsrf, fullAttemptId, fullSessionId);

      const solutionStatuses: number[] = [];
      const resListener = (res: { url(): string; status(): number }) => {
        if (res.url().includes("/solution-assets/")) {
          solutionStatuses.push(res.status());
        }
      };
      page.on("response", resListener);
      await page.goto(`${WEB_URL}/attempts/${fullAttemptId}/review`);
      await expect(page.getByRole("heading", { level: 1, name: "Xem lại bài làm" })).toBeVisible();
      page.off("response", resListener);

      const solutionImgs = page.locator('img[src*="/solution-assets/"]');
      await expect(solutionImgs.first()).toBeVisible();
      expect(solutionStatuses.length).toBeGreaterThan(0);
      for (const status of solutionStatuses) {
        expect(status).toBe(200);
      }
      const hasLoadedImage = await solutionImgs.evaluateAll((nodes) =>
        nodes.some((node) => {
          const img = node as HTMLImageElement;
          return img.complete && img.naturalWidth > 0;
        }),
      );
      expect(hasLoadedImage).toBe(true);
    } finally {
      await setExamResultViewAndPublish(imgExam.code, previousResultView);
    }
  });
});
