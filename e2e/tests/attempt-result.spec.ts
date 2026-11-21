import { randomUUID } from "node:crypto";
import { expect, test, type APIRequestContext, type Browser, type BrowserContext, type Page } from "@playwright/test";
import { ADMIN_URL, adminApi, loginAdmin, RUN_ID } from "./helpers/editor-qa";
import { createVerifiedAccount, WEB_URL, type SessionCookies } from "./helpers/mail";
import { examByCode, publishedExamSet, waitForPublishedExamSet } from "./helpers/published-exam-set";
import { publishApi, type CheckResult } from "./helpers/wave-2b";

/**
 * E2E chức năng cho FS-6 (UNI-2735):
 * - AC-10: SCORE không cho xem lại (ẩn CTA ở /result, truy cập thẳng /review hiện màn 403 thân thiện)
 * - AC-13 & AC-16: Lịch sử có đủ dòng FULL & theo phần riêng biệt; lọc scope/tìm kiếm + /history?examId=<uuid> + Back giữ query
 * - AC-17: 404 (id lạ & cross-user trên cả /result và /review), 409 ATTEMPT_NOT_FINISHED khi lượt còn mở
 * - AC-23: Ảnh lời giải (/solution-assets/) trên DS-HSA-SUBJECT-TOAN — 0 request khi SCORE_AND_ANSWERS;
 *          khi admin chuyển listing sang FULL_SOLUTION và publish, tài khoản mới mua lượt mở /review tải ảnh 200 & naturalWidth > 0 (khôi phục trong finally).
 */
test.setTimeout(300_000);

const API_URL = (process.env.THI360_API_URL ?? "http://localhost:8080").replace(/\/+$/, "");

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

interface AttemptStatePayload {
  state: string;
  navigationMode?: string | null;
  parts?: Array<{ partId: string; partCode: string; ord: number; state: string }>;
}

function xsrfFromCookies(cookies: SessionCookies): string {
  return cookies.find((c) => c.name === "thi360_xsrf")?.value ?? "";
}

function cookieHeader(cookies: SessionCookies): string {
  return cookies.map((c) => `${c.name}=${c.value}`).join("; ");
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
    const freeSubmit = page.getByRole("button", { name: "Nhận bài thi miễn phí" });
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
): Promise<{ entitlementId: string; attemptsLeft: number }> {
  const res = await request.get(`${WEB_URL}/api/v1/me/exams`);
  expect(res.ok(), `GET /api/v1/me/exams: ${res.status()}`).toBe(true);
  const body = (await res.json()) as {
    items: Array<{
      entitlementId?: string | null;
      code: string;
      attemptsLeft: number;
    }>;
  };
  const match = body.items.find((item) => item.code === examCode);
  expect(match, `Missing entitlement for ${examCode}`).toBeDefined();
  expect(match?.entitlementId, `Missing entitlementId for ${examCode}`).toBeTruthy();
  return {
    entitlementId: match!.entitlementId!,
    attemptsLeft: match!.attemptsLeft,
  };
}

async function startAttemptToInProgress(
  request: APIRequestContext,
  cookies: SessionCookies,
  entitlementId: string,
): Promise<{ attemptId: string; sessionId: string }> {
  const xsrf = xsrfFromCookies(cookies);
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

  const sessionRes = await request.post(`${API_URL}/api/v1/me/attempts/${attemptId}/session`, {
    headers: {
      cookie: cookieHeader(cookies),
      origin: WEB_URL,
      "x-xsrf-token": xsrf,
    },
    data: { takeover: true },
  });
  expect(sessionRes.ok(), `POST /session: ${sessionRes.status()} ${await sessionRes.text()}`).toBe(true);
  const { sessionId } = (await sessionRes.json()) as { sessionId: string };

  return { attemptId, sessionId };
}

async function advanceSequentialPartsToLast(
  request: APIRequestContext,
  cookies: SessionCookies,
  attemptId: string,
  sessionId: string,
  afterStartOptionCode = "ANH",
): Promise<void> {
  const xsrf = xsrfFromCookies(cookies);
  const apiHeaders = {
    cookie: cookieHeader(cookies),
    origin: WEB_URL,
    "x-xsrf-token": xsrf,
    "X-Attempt-Session": sessionId,
  };

  for (let step = 0; step < 40; step++) {
    const stateRes = await request.get(`${WEB_URL}/api/v1/me/attempts/${attemptId}`);
    expect(stateRes.ok(), `GET /attempts/${attemptId}: ${stateRes.status()}`).toBe(true);
    const stateBody = (await stateRes.json()) as AttemptStatePayload;
    if (stateBody.navigationMode !== "SEQUENTIAL") {
      return;
    }
    const parts = [...(stateBody.parts ?? [])].sort((a, b) => a.ord - b.ord);
    if (parts.length <= 1 || parts[parts.length - 1]?.state === "OPEN") {
      return;
    }

    const openNonLast = parts.slice(0, -1).find((p) => p.state === "OPEN");
    if (openNonLast) {
      const finishRes = await request.post(
        `${API_URL}/api/v1/me/attempts/${attemptId}/parts/${openNonLast.partId}/finish`,
        { headers: apiHeaders, data: {} },
      );
      expect(
        finishRes.ok(),
        `POST /parts/${openNonLast.partId}/finish: ${finishRes.status()} ${await finishRes.text()}`,
      ).toBe(true);
      continue;
    }

    const awaitingPart = parts.find((p) => p.state === "AWAITING_CHOICE");
    if (awaitingPart) {
      const choiceRes = await request.post(
        `${API_URL}/api/v1/me/attempts/${attemptId}/parts/${awaitingPart.partId}/choice`,
        {
          headers: { ...apiHeaders, "Idempotency-Key": randomUUID() },
          data: { optionCode: afterStartOptionCode },
        },
      );
      expect(
        choiceRes.ok(),
        `POST /parts/${awaitingPart.partId}/choice: ${choiceRes.status()} ${await choiceRes.text()}`,
      ).toBe(true);
      await new Promise((resolve) => setTimeout(resolve, 500));
      continue;
    }

    await new Promise((resolve) => setTimeout(resolve, 500));
  }

  throw new Error(`Sequential attempt ${attemptId} did not reach last OPEN part in time`);
}

async function submitAndWaitScored(
  request: APIRequestContext,
  cookies: SessionCookies,
  attemptId: string,
  sessionId: string,
): Promise<{ examId: string }> {
  await advanceSequentialPartsToLast(request, cookies, attemptId, sessionId);

  const xsrf = xsrfFromCookies(cookies);
  const submitRes = await request.post(`${API_URL}/api/v1/me/attempts/${attemptId}/submit`, {
    headers: {
      cookie: cookieHeader(cookies),
      origin: WEB_URL,
      "x-xsrf-token": xsrf,
      "Idempotency-Key": randomUUID(),
      "X-Attempt-Session": sessionId,
    },
    data: {},
  });
  expect(submitRes.ok(), `POST /submit: ${submitRes.status()} ${await submitRes.text()}`).toBe(true);

  for (let i = 0; i < 45; i++) {
    const res = await request.get(`${WEB_URL}/api/v1/me/attempts/${attemptId}/result`);
    if (res.ok()) {
      const data = (await res.json()) as { state: string; examId: string };
      if (data.state === "SCORED" || data.state === "PENDING_REVIEW") {
        return { examId: data.examId };
      }
    }
    await new Promise((resolve) => setTimeout(resolve, 1_000));
  }
  throw new Error(`Attempt ${attemptId} did not finish grading`);
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
      const pathIds = checkBody.paths.filter((p) => p.result === "VALID").map((p) => p.pathId);
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

  test("AC-10, AC-13, AC-16, AC-17: SCORE ẩn xem lại, 403/404/409, và Lịch sử 2 dòng + lọc + Back giữ query", async ({
    browser,
    context,
    page,
  }) => {
    const set = publishedExamSet();
    const scoreOnly = examByCode(set.scoreOnlyExam);
    const fullMini = examByCode("DS-HSA-FULL-MINI");

    const owner = await createVerifiedAccount(`fs6.owner.${RUN_ID}`);
    await context.addCookies(owner.cookies);

    await buyExamViaCheckout(browser, owner.cookies, scoreOnly.productCode);
    const ent = await findEntitlementId(context.request, scoreOnly.code);

    // 1. In-progress attempt -> /result shows 409 ATTEMPT_NOT_FINISHED fallback (AC-17)
    const { attemptId, sessionId } = await startAttemptToInProgress(context.request, owner.cookies, ent.entitlementId);
    await page.goto(`${WEB_URL}/attempts/${attemptId}/result`);
    await expect(page.getByRole("heading", { level: 1, name: "Lượt thi chưa kết thúc" })).toBeVisible();
    await expect(page.getByRole("link", { name: "Tiếp tục làm bài" })).toHaveAttribute("href", `/attempts/${attemptId}`);

    // 2. Submit -> SCORED + SCORE hides review CTA on /result, direct /review shows 403 (AC-10)
    const { examId: scoreOnlyExamId } = await submitAndWaitScored(context.request, owner.cookies, attemptId, sessionId);
    await page.reload();
    await expect(page.getByRole("heading", { level: 1, name: scoreOnly.name })).toBeVisible();
    await expect(page.getByRole("link", { name: /Xem lại/ })).toHaveCount(0);
    await expect(page.getByText(/chỉ cho xem điểm/i)).toBeVisible();

    await page.goto(`${WEB_URL}/attempts/${attemptId}/review`);
    await expect(page.getByRole("heading", { level: 1, name: "Bài thi này không có phần xem lại bài làm" })).toBeVisible();
    await page.getByRole("link", { name: "Về kết quả" }).first().click();
    await expect(page).toHaveURL(`${WEB_URL}/attempts/${attemptId}/result`);

    // 3. Unknown 404 (on /result & /review) & cross-user ownership isolation -> 404 on BOTH /result & /review (AC-17)
    await page.goto(`${WEB_URL}/attempts/00000000-0000-4000-8000-000000000404/result`);
    await expect(page.getByRole("heading", { level: 1, name: "Không tìm thấy kết quả lượt thi" })).toBeVisible();
    await page.goto(`${WEB_URL}/attempts/00000000-0000-4000-8000-000000000404/review`);
    await expect(page.getByRole("heading", { level: 1, name: "Không tìm thấy bài làm" })).toBeVisible();

    const stranger = await createVerifiedAccount(`fs6.other.${RUN_ID}`);
    const strangerCtx: BrowserContext = await browser.newContext();
    try {
      await strangerCtx.addCookies(stranger.cookies);
      const strangerPage: Page = await strangerCtx.newPage();
      await strangerPage.goto(`${WEB_URL}/attempts/${attemptId}/result`);
      await expect(strangerPage.getByRole("heading", { level: 1, name: "Không tìm thấy kết quả lượt thi" })).toBeVisible();
      await strangerPage.goto(`${WEB_URL}/attempts/${attemptId}/review`);
      await expect(strangerPage.getByRole("heading", { level: 1, name: "Không tìm thấy bài làm" })).toBeVisible();
    } finally {
      await strangerCtx.close();
    }

    // 4. Buy & complete a second FULL attempt (DS-HSA-FULL-MINI) -> 2 separate rows in /history (AC-13)
    await buyExamViaCheckout(browser, owner.cookies, fullMini.productCode);
    const fullMiniEnt = await findEntitlementId(context.request, fullMini.code);
    const { attemptId: fullMiniAttemptId, sessionId: fullMiniSessionId } = await startAttemptToInProgress(
      context.request,
      owner.cookies,
      fullMiniEnt.entitlementId,
    );
    await submitAndWaitScored(context.request, owner.cookies, fullMiniAttemptId, fullMiniSessionId);

    // 5. /history verifies 2 rows (FULL + PART), scope & q URL filters, Back preserves query, ?examId=<uuid> filters strictly (AC-13, AC-16)
    await page.goto(`${WEB_URL}/history`);
    await expect(page.getByRole("heading", { level: 1, name: "Lịch sử luyện thi" })).toBeVisible();
    const rows = page.locator("tbody tr");
    await expect(rows).toHaveCount(2);

    const fullRow = rows.filter({ hasText: fullMini.name });
    const partRow = rows.filter({ hasText: scoreOnly.name });
    await expect(fullRow).toHaveCount(1);
    await expect(fullRow).toContainText("Bài thi FULL");
    await expect(partRow).toHaveCount(1);
    await expect(partRow).toContainText("Bài thi theo phần");

    await fullRow.getByRole("link", { name: "Xem kết quả" }).click();
    await expect(page).toHaveURL(new RegExp(`/attempts/${fullMiniAttemptId}/result$`));
    await page.goBack();
    await expect(rows).toHaveCount(2);

    // Filter by scope = PART -> URL updates & narrows to 1 row
    await page.getByRole("combobox", { name: "Phạm vi thi" }).click();
    await page.getByRole("option", { name: "Bài thi theo phần" }).click();
    await expect(page).toHaveURL(/\/history\?.*scope=PART/);
    await expect(rows).toHaveCount(1);
    await expect(rows.first()).toContainText(scoreOnly.name);

    // Also filter by q -> URL preserves both q & scope, and Back from /result keeps exact filteredUrl & rows
    const searchInput = page.getByLabel("Tìm theo tên bài");
    await searchInput.fill(scoreOnly.name.slice(0, 12));
    await expect(page).toHaveURL(/\/history\?.*q=.*scope=PART/);
    const filteredUrl = page.url();

    await rows.first().getByRole("link", { name: "Xem kết quả" }).click();
    await expect(page).toHaveURL(new RegExp(`/attempts/${attemptId}/result$`));
    await page.goBack();
    await expect(page).toHaveURL(filteredUrl);
    await expect(rows).toHaveCount(1);
    await expect(rows.first()).toContainText(scoreOnly.name);

    // /history?examId=<uuid> filters strictly to scoreOnlyExamId
    await page.goto(`${WEB_URL}/history?examId=${encodeURIComponent(scoreOnlyExamId)}`);
    await expect(page.getByRole("button", { name: "Bỏ lọc theo bài" })).toBeVisible();
    await expect(rows).toHaveCount(1);
    await expect(rows.first()).toContainText(scoreOnly.name);
    await expect(page.getByText(fullMini.name)).toHaveCount(0);
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

    await buyExamViaCheckout(browser, student.cookies, imgExam.productCode);
    const ent1 = await findEntitlementId(context.request, imgExam.code);
    const { attemptId: saAttemptId, sessionId: saSessionId } = await startAttemptToInProgress(
      context.request,
      student.cookies,
      ent1.entitlementId,
    );
    await submitAndWaitScored(context.request, student.cookies, saAttemptId, saSessionId);

    const solutionRequestsWhenLocked: string[] = [];
    const reqListener = (req: { url(): string }) => {
      const reqUrl = req.url();
      if (reqUrl.includes("/solution-assets/")) {
        solutionRequestsWhenLocked.push(reqUrl);
      }
    };
    page.on("request", reqListener);
    await page.goto(`${WEB_URL}/attempts/${saAttemptId}/review`);
    await expect(page.getByRole("heading", { level: 1, name: "Xem lại bài làm" })).toBeVisible();
    await expect(page.getByText("Không hiển thị lời giải cho mức quyền lợi hiện tại.")).toBeVisible();
    page.off("request", reqListener);

    expect(solutionRequestsWhenLocked).toHaveLength(0);
    await expect(page.locator('img[src*="/solution-assets/"]')).toHaveCount(0);

    // Toggle DS-HSA-SUBJECT-TOAN listing to FULL_SOLUTION, publish, then use a fresh 2nd student so entitlement snapshots FULL_SOLUTION; always restore in finally
    const previousResultView = await setExamResultViewAndPublish(imgExam.code, "FULL_SOLUTION");
    try {
      const fullStudent = await createVerifiedAccount(`fs6.imgfull.${RUN_ID}`);
      const fullCtx = await browser.newContext();
      try {
        await fullCtx.addCookies(fullStudent.cookies);
        await buyExamViaCheckout(browser, fullStudent.cookies, imgExam.productCode);
        const fullEnt = await findEntitlementId(fullCtx.request, imgExam.code);
        const { attemptId: fullAttemptId, sessionId: fullSessionId } = await startAttemptToInProgress(
          fullCtx.request,
          fullStudent.cookies,
          fullEnt.entitlementId,
        );
        await submitAndWaitScored(fullCtx.request, fullStudent.cookies, fullAttemptId, fullSessionId);

        const fullPage = await fullCtx.newPage();
        const solutionStatuses: number[] = [];
        const resListener = (res: { url(): string; status(): number }) => {
          if (res.url().includes("/solution-assets/")) {
            solutionStatuses.push(res.status());
          }
        };
        fullPage.on("response", resListener);
        await fullPage.goto(`${WEB_URL}/attempts/${fullAttemptId}/review`);
        await expect(fullPage.getByRole("heading", { level: 1, name: "Xem lại bài làm" })).toBeVisible();

        const solutionImgs = fullPage.locator('img[src*="/solution-assets/"]');
        await expect(solutionImgs.first()).toBeVisible();
        await expect
          .poll(() =>
            solutionImgs.evaluateAll((nodes) =>
              nodes.some((node) => {
                const img = node as HTMLImageElement;
                return img.complete && img.naturalWidth > 0;
              }),
            ),
          )
          .toBe(true);
        fullPage.off("response", resListener);

        expect(solutionStatuses.length).toBeGreaterThan(0);
        for (const status of solutionStatuses) {
          expect(status).toBe(200);
        }
      } finally {
        await fullCtx.close();
      }
    } finally {
      await setExamResultViewAndPublish(imgExam.code, previousResultView);
    }
  });
});
