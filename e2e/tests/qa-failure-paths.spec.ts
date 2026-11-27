import { randomUUID } from "node:crypto";
import { expect, test, type APIRequestContext, type Browser, type Page } from "@playwright/test";

import { adminApi, ADMIN_URL, loginAdmin, WEB_URL } from "./helpers/editor-qa";
import {
  advanceTestClock,
  answerCurrentUnit,
  API_URL,
  ensureDsReady,
  getAttemptStateApi,
  getMyExams,
  getTestClockNow,
  purchaseExamViaUi,
  resetTestClock,
  resolveActiveDeadlineIso,
  startAttemptFromPrepare,
  waitForSavedStatus,
} from "./helpers/attempt-fixtures";
import { createVerifiedAccount, type SessionCookies } from "./helpers/mail";

async function studentWriteHeaders(page: Page): Promise<Record<string, string>> {
  const cookies = await page.context().cookies(WEB_URL);
  const xsrf = cookies.find((c) => c.name === "thi360_xsrf")?.value ?? "";
  expect(xsrf, "Expected live thi360_xsrf cookie on student context").toBeTruthy();
  return {
    origin: WEB_URL,
    "x-xsrf-token": xsrf,
  };
}

async function extractOrderCodeFromResultPage(page: Page): Promise<string> {
  const fromQuery = new URL(page.url()).searchParams.get("order");
  if (fromQuery) return fromQuery;

  const orderLink = page.locator('main a[href^="/account/orders/"]').first();
  await expect(orderLink).toBeVisible({ timeout: 15_000 });
  const href = await orderLink.getAttribute("href");
  const match = href ? /\/account\/orders\/([^/?#]+)/.exec(href) : null;
  expect(match?.[1], `Cannot extract order code from href=${href}`).toBeTruthy();
  return decodeURIComponent(match![1]!);
}

async function setAudioFault(examId: string, enabled: boolean): Promise<void> {
  const method = enabled ? "POST" : "DELETE";
  const res = await fetch(`${API_URL}/api/v1/test/audio-faults/${encodeURIComponent(examId)}`, { method });
  if (!res.ok) {
    throw new Error(`${method} /api/v1/test/audio-faults/${examId} failed: ${res.status}`);
  }
}

async function startAttemptCapturingSession(
  page: Page,
  entitlementId: string,
): Promise<{ attemptId: string; sessionId: string }> {
  let capturedSessionId = "";
  const onResponse = (res: { url(): string; request(): { method(): string }; status(): number; json(): Promise<unknown> }) => {
    if (res.url().includes("/api/v1/me/attempts/") && res.url().endsWith("/session") && res.request().method() === "POST") {
      void res
        .json()
        .then((body) => {
          const data = body as { sessionId?: string };
          if (data.sessionId) capturedSessionId = data.sessionId;
        })
        .catch(() => undefined);
    }
  };
  page.on("response", onResponse);
  try {
    const attemptId = await startAttemptFromPrepare(page, entitlementId);
    if (!capturedSessionId) {
      await expect
        .poll(() => capturedSessionId, { message: "capture attempt session id", timeout: 10_000 })
        .not.toBe("")
        .catch(() => undefined);
    }
    return { attemptId, sessionId: capturedSessionId };
  } finally {
    page.off("response", onResponse);
  }
}

async function ensureSessionId(
  page: Page,
  attemptId: string,
  capturedSessionId: string,
): Promise<string> {
  if (capturedSessionId) return capturedSessionId;
  const res = await page.request.post(`${WEB_URL}/api/v1/me/attempts/${encodeURIComponent(attemptId)}/session`, {
    headers: await studentWriteHeaders(page),
    data: { takeover: true },
  });
  expect(res.ok(), `POST /session: ${res.status()} ${await res.text()}`).toBe(true);
  const body = (await res.json()) as { sessionId: string };
  return body.sessionId;
}

async function getAdminOrder(
  api: APIRequestContext,
  orderCode: string,
): Promise<{
  state: string;
  replacedByOrderCode?: string | null;
  needsReview?: boolean;
  grants: Array<{ grantId?: string; attempts: number; status?: string }>;
  payments: Array<{ id: string; txnRef: string; state: string }>;
}> {
  const res = await api.get(`${ADMIN_URL}/api/v1/admin/orders/${encodeURIComponent(orderCode)}`);
  expect(res.ok(), `GET /admin/orders/${orderCode}: ${res.status()} ${await res.text()}`).toBe(true);
  return (await res.json()) as {
    state: string;
    replacedByOrderCode?: string | null;
    needsReview?: boolean;
    grants: Array<{ grantId?: string; attempts: number; status?: string }>;
    payments: Array<{ id: string; txnRef: string; state: string }>;
  };
}

async function openStudentPage(browser: Browser, cookies: SessionCookies): Promise<{ context: Awaited<ReturnType<Browser["newContext"]>>; page: Page }> {
  const context = await browser.newContext();
  await context.addCookies(cookies);
  const page = await context.newPage();
  page.setDefaultTimeout(25_000);
  return { context, page };
}

test.describe("AC-18 QA failure paths (IPN replay, late expiry, timeout submit, system/part-3 fault, refund while IN_PROGRESS)", () => {
  test.describe.configure({ mode: "serial" });

  test.beforeAll(async ({ browser }) => {
    await ensureDsReady(browser);
  });

  test("1. Replay IPN trùng sau thanh toán thành công trả RspCode=02 và giữ đúng 1 lần cấp quyền", async ({ browser }) => {
    const { cookies } = await createVerifiedAccount("qa.fp1");
    const { context, page } = await openStudentPage(browser, cookies);
    const admin = await adminApi();

    try {
      await page.goto(`${WEB_URL}/checkout/DS-HSA-PART-MATH-MINI`);
      await expect(page.getByRole("heading", { level: 1, name: "Xác nhận mua" })).toBeVisible();
      await page.getByRole("button", { name: "Thanh toán qua VNPAY" }).click();
      await page.waitForURL(/\/mock-vnpay/);

      const successLink = page.locator('a[data-mock-action="success"]');
      await expect(successLink).toBeVisible();
      const ipnUrl = await successLink.getAttribute("data-ipn-url");
      expect(ipnUrl, "Mock VNPAY must expose data-ipn-url").toBeTruthy();

      await successLink.click();
      await page.waitForURL(/\/checkout\/result/);
      await expect(page.getByRole("heading", { level: 1, name: "Đã cấp bài thi" })).toBeVisible();
      const orderCode = await extractOrderCodeFromResultPage(page);

      const firstExams = await getMyExams(page);
      const grantedBefore = firstExams.filter((item) => item.code === "DS-HSA-PART-MATH-MINI");
      expect(grantedBefore).toHaveLength(1);
      expect(grantedBefore[0]?.attemptsLeft).toBe(3);

      // Replay đúng IPN thành công lần 2: gateway idempotency phải trả RspCode = "02"
      const replayRes = await page.request.get(`${API_URL}${ipnUrl!}`);
      expect(replayRes.status(), await replayRes.text()).toBe(200);
      const replayJson = (await replayRes.json()) as { RspCode: string; Message?: string };
      expect(replayJson.RspCode).toBe("02");

      const adminOrder = await getAdminOrder(admin.api, orderCode);
      expect(adminOrder.state).toBe("FULFILLED");
      expect(adminOrder.grants).toHaveLength(1);
      expect(adminOrder.grants[0]?.attempts).toBe(3);

      const examsAfter = await getMyExams(page);
      const grantedAfter = examsAfter.filter((item) => item.code === "DS-HSA-PART-MATH-MINI");
      expect(grantedAfter).toHaveLength(1);
      expect(grantedAfter[0]?.attemptsLeft).toBe(3);
    } finally {
      await admin.api.dispose();
      await context.close();
    }
  });

  test("2. Thanh toán muộn sau khi đơn quá hạn và bị thay thế: pay cũ 409 ORDER_EXPIRED, IPN trễ trả 00 và FULFILLED 3 lượt", async ({
    browser,
  }) => {
    const { cookies } = await createVerifiedAccount("qa.fp2");
    const { context, page } = await openStudentPage(browser, cookies);
    const admin = await adminApi();

    try {
      // Tạo đơn 1 và rời cổng thanh toán với no_response (payment 1 giữ trạng thái CREATED)
      await page.goto(`${WEB_URL}/checkout/DS-HSA-PART-MATH-MINI`);
      await expect(page.getByRole("heading", { level: 1, name: "Xác nhận mua" })).toBeVisible();
      await page.getByRole("button", { name: "Thanh toán qua VNPAY" }).click();
      await page.waitForURL(/\/mock-vnpay/);

      const successIpnUrl1 = await page.locator('a[data-mock-action="success"]').getAttribute("data-ipn-url");
      expect(successIpnUrl1, "Missing data-ipn-url on mock-vnpay").toBeTruthy();

      await page.locator('a[data-mock-action="no_response"]').click();
      await page.waitForURL(/\/checkout\/result/);
      const orderCode1 = await extractOrderCodeFromResultPage(page);

      // Đưa TestClock vượt hạn thanh toán (20 phút > PT15M mặc định)
      await advanceTestClock(20 * 60);

      // Mở lại /pay trên đơn 1 quá hạn phải bị từ chối 409 ORDER_EXPIRED
      const expiredPayRes = await page.request.post(`${WEB_URL}/api/v1/me/orders/${encodeURIComponent(orderCode1)}/pay`, {
        headers: await studentWriteHeaders(page),
        data: {},
      });
      expect(expiredPayRes.status(), await expiredPayRes.text()).toBe(409);
      const expiredBody = (await expiredPayRes.json()) as { code?: string };
      expect(expiredBody.code).toBe("ORDER_EXPIRED");

      // Tạo phiên checkout mới làm đơn 1 chuyển ngay sang REPLACED, rồi hủy đơn 2 để chỉ còn đơn 1 nhận IPN trễ
      await page.goto(`${WEB_URL}/checkout/DS-HSA-PART-MATH-MINI`);
      await page.getByRole("button", { name: "Thanh toán qua VNPAY" }).click();
      await page.waitForURL(/\/mock-vnpay/);
      await page.locator('a[data-mock-action="no_response"]').click();
      await page.waitForURL(/\/checkout\/result/);
      const orderCode2 = await extractOrderCodeFromResultPage(page);
      expect(orderCode2).not.toBe(orderCode1);

      const cancelRes = await page.request.post(`${WEB_URL}/api/v1/me/orders/${encodeURIComponent(orderCode2)}/cancel`, {
        headers: await studentWriteHeaders(page),
        data: {},
      });
      expect(cancelRes.ok(), `cancel replacement order ${orderCode2}: ${cancelRes.status()} ${await cancelRes.text()}`).toBe(true);

      const beforeLateIpn = await getAdminOrder(admin.api, orderCode1);
      expect(beforeLateIpn.state).toBe("REPLACED");
      expect(beforeLateIpn.replacedByOrderCode).toBe(orderCode2);

      // Gửi IPN thành công trễ của đơn 1: hệ thống reclaimed REPLACED -> PAID -> FULFILLED và cấp đúng 3 lượt
      const lateIpnRes = await page.request.get(`${API_URL}${successIpnUrl1!}`);
      expect(lateIpnRes.status(), await lateIpnRes.text()).toBe(200);
      const lateIpnJson = (await lateIpnRes.json()) as { RspCode: string };
      expect(lateIpnJson.RspCode).toBe("00");

      await expect
        .poll(async () => (await getAdminOrder(admin.api, orderCode1)).state, {
          message: "wait replaced order to be fulfilled by late IPN",
          timeout: 20_000,
        })
        .toBe("FULFILLED");

      const afterLateIpn = await getAdminOrder(admin.api, orderCode1);
      expect(afterLateIpn.grants).toHaveLength(1);
      expect(afterLateIpn.grants[0]?.attempts).toBe(3);

      const exams = await getMyExams(page);
      const granted = exams.find((item) => item.code === "DS-HSA-PART-MATH-MINI");
      expect(granted?.attemptsLeft).toBe(3);
    } finally {
      await resetTestClock();
      await admin.api.dispose();
      await context.close();
    }
  });

  test("3. Nộp bài sát/vượt mốc hết giờ: không có lỗi 500, chốt SCORED gọn gàng và trừ đúng 1 lượt", async ({ browser }) => {
    const { cookies } = await createVerifiedAccount("qa.fp3");
    const { context, page } = await openStudentPage(browser, cookies);

    try {
      const { entitlementId } = await purchaseExamViaUi(page, "DS-HSA-PART-MATH-MINI");
      const beforeStart = (await getMyExams(page)).find((item) => item.entitlementId === entitlementId);
      expect(beforeStart?.attemptsLeft).toBe(3);

      const { attemptId, sessionId: initialSessionId } = await startAttemptCapturingSession(page, entitlementId);
      await answerCurrentUnit(page);
      await waitForSavedStatus(page);

      const state = await getAttemptStateApi(page.request, attemptId);
      const deadlineIso = resolveActiveDeadlineIso(state);
      expect(deadlineIso, "Active attempt must have deadlineAt").toBeTruthy();

      const nowIso = await getTestClockNow();
      const deltaSeconds = Math.max(5, Math.ceil((Date.parse(deadlineIso!) - Date.parse(nowIso)) / 1000) + 5);

      const sessionId = await ensureSessionId(page, attemptId, initialSessionId);
      await advanceTestClock(deltaSeconds);

      const key1 = randomUUID();
      const key2 = randomUUID();
      const writeHeaders = await studentWriteHeaders(page);
      const submitUrl = `${WEB_URL}/api/v1/me/attempts/${encodeURIComponent(attemptId)}/submit`;

      // Nộp trùng đồng thời ngay sau mốc deadline + replay idempotent: trả 200 sạch, không 409/500
      const [submitRes1, submitRes2] = await Promise.all([
        page.request.post(submitUrl, {
          headers: {
            ...writeHeaders,
            "Idempotency-Key": key1,
            "X-Attempt-Session": sessionId,
          },
          data: { finalAnswers: [] },
        }),
        page.request.post(submitUrl, {
          headers: {
            ...writeHeaders,
            "Idempotency-Key": key2,
            "X-Attempt-Session": sessionId,
          },
          data: { finalAnswers: [] },
        }),
      ]);
      expect(submitRes1.status(), `Concurrent boundary submit #1 must return 200: ${await submitRes1.text()}`).toBe(200);
      expect(submitRes2.status(), `Concurrent boundary submit #2 must return 200: ${await submitRes2.text()}`).toBe(200);
      const submitBody1 = (await submitRes1.json()) as { attemptId?: string; state?: string; endReason?: string | null };
      const submitBody2 = (await submitRes2.json()) as { attemptId?: string; state?: string; endReason?: string | null };
      for (const body of [submitBody1, submitBody2]) {
        expect(body.attemptId).toBe(attemptId);
        expect(["GRADING", "SCORED"]).toContain(body.state);
        expect(["TIME_UP", "SUBMITTED"]).toContain(body.endReason);
      }

      const replaySubmitRes = await page.request.post(submitUrl, {
        headers: {
          ...writeHeaders,
          "Idempotency-Key": key1,
          "X-Attempt-Session": sessionId,
        },
        data: { finalAnswers: [] },
      });
      expect(replaySubmitRes.status(), `Idempotent boundary submit replay: ${await replaySubmitRes.text()}`).toBe(200);
      const replayBody = (await replaySubmitRes.json()) as { attemptId?: string; state?: string; endReason?: string | null };
      expect(replayBody.attemptId).toBe(attemptId);
      expect(replayBody.state).toBe(submitBody1.state);
      expect(replayBody.endReason).toBe(submitBody1.endReason);

      await expect
        .poll(
          async () => {
            const res = await page.request.get(`${WEB_URL}/api/v1/me/attempts/${encodeURIComponent(attemptId)}/result`);
            if (!res.ok()) return "";
            return ((await res.json()) as { state?: string }).state ?? "";
          },
          { message: "boundary submit settles to SCORED", timeout: 45_000, intervals: [500, 1_000] },
        )
        .toBe("SCORED");

      await page.goto(`${WEB_URL}/attempts/${encodeURIComponent(attemptId)}/result`);
      await expect(page.getByRole("heading", { level: 1 })).toBeVisible({ timeout: 20_000 });
      await expect(page.getByRole("heading", { name: /Đã có lỗi máy chủ|Server error/ })).toHaveCount(0);

      const afterSubmit = (await getMyExams(page)).find((item) => item.entitlementId === entitlementId);
      expect(afterSubmit?.attemptsLeft).toBe((beforeStart?.attemptsLeft ?? 3) - 1);
    } finally {
      await resetTestClock();
      await context.close();
    }
  });

  test("4. Lỗi sinh đề/thiếu tệp khi bắt đầu hoàn lượt tự động & lỗi hệ thống ở Phần 3 -> FAULT_CLOSED -> cấp bù đúng 1 lượt (409 lần 2)", async ({
    browser,
  }) => {
    const { cookies } = await createVerifiedAccount("qa.fp4");
    const { context, page } = await openStudentPage(browser, cookies);
    const admin = await adminApi();
    let faultedAudioExamId = "";

    try {
      // 4a. Thiếu tệp audio khi bắt đầu làm bài -> ABORTED và tự trả lại lượt (attemptsLeft giữ 3)
      const hsca = await purchaseExamViaUi(page, "DS-HSCA-ANH-MINI");
      const prepRes = await page.request.get(
        `${WEB_URL}/api/v1/me/entitlements/${encodeURIComponent(hsca.entitlementId)}/prepare`,
      );
      expect(prepRes.ok(), await prepRes.text()).toBe(true);
      faultedAudioExamId = ((await prepRes.json()) as { examId: string }).examId;

      await setAudioFault(faultedAudioExamId, true);
      await page.goto(`${WEB_URL}/prepare/${encodeURIComponent(hsca.entitlementId)}`);
      const startBtn = page.getByRole("button", { name: "Bắt đầu bài thi" });
      await expect(startBtn).toBeVisible();
      await startBtn.click();

      const alertBox = page.locator('main [role="alert"]');
      await expect(alertBox).toContainText("Thiếu tệp âm thanh cần thiết cho đề thi.", { timeout: 25_000 });
      await expect(alertBox).toContainText("Lượt đã được trả lại, bạn chưa mất lượt nào. Đồng hồ chưa chạy.");

      await setAudioFault(faultedAudioExamId, false);
      faultedAudioExamId = "";

      const hscaExam = (await getMyExams(page)).find((item) => item.entitlementId === hsca.entitlementId);
      expect(hscaExam?.attemptsLeft).toBe(3);

      // 4b. Học viên làm HSA đầy đủ tới Phần 3 -> admin đánh dấu FAULT_CLOSED -> cấp bù 1 lượt (+1 & banner /result & 409 lần 2)
      const hsa = await purchaseExamViaUi(page, "DS-HSA-FULL-MINI");
      const attemptId = await startAttemptFromPrepare(page, hsa.entitlementId);

      // Hoàn thành Phần 1 & Phần 2 để vào màn chọn Phần 3 (SCIENCE: 3 môn)
      const partNav = page.locator('nav[aria-label="Các phần của bài thi"]');
      for (let step = 0; step < 2; step += 1) {
        await page.getByRole("button", { name: "Hoàn thành phần" }).click();
        const dialog = page.getByRole("dialog");
        await expect(dialog).toBeVisible();
        await dialog.getByRole("button", { name: "Hoàn thành phần" }).click();
        await expect(dialog).toBeHidden({ timeout: 25_000 });
        await expect
          .poll(async () => ((await partNav.textContent()) ?? "").match(/Đã hoàn thành/g)?.length ?? 0, {
            timeout: 25_000,
            message: `Đợi có ${step + 1} phần Đã hoàn thành`,
          })
          .toBeGreaterThanOrEqual(step + 1);
      }

      await expect(page.getByRole("heading", { name: "Chọn phần thi thứ ba" })).toBeVisible({ timeout: 25_000 });
      await page.locator('input[type="radio"][name="attempt-part-choice"][value="SCIENCE"]').check();
      const subjectCheckboxes = page.locator(
        'main input[type="checkbox"]:not(:disabled), main button[role="checkbox"]:not([disabled])',
      );
      await expect(subjectCheckboxes.nth(2)).toBeVisible({ timeout: 10_000 });
      for (let idx = 0; idx < 3; idx += 1) {
        await subjectCheckboxes.nth(idx).check();
      }
      const confirmPart3Btn = page.getByRole("button", { name: "Xác nhận chọn phần 3" });
      await expect(confirmPart3Btn).toBeEnabled({ timeout: 10_000 });
      await confirmPart3Btn.click();
      await expect(page.locator("main fieldset[data-attempt-question-fieldset]")).toBeVisible({ timeout: 25_000 });

      const afterStart = (await getMyExams(page)).find((item) => item.entitlementId === hsa.entitlementId);
      expect(afterStart?.attemptsLeft).toBe(2);

      // Admin mở /attempts/[id] đánh dấu lỗi hệ thống và cấp bù đúng 1 lượt
      const adminCtx = await browser.newContext();
      try {
        await loginAdmin(adminCtx, "vi");
        const adminPage = await adminCtx.newPage();
        await adminPage.goto(`${ADMIN_URL}/attempts/${encodeURIComponent(attemptId)}`);
        await expect(adminPage.getByRole("heading", { level: 1 })).toBeVisible({ timeout: 20_000 });

        await adminPage.getByRole("button", { name: "Đánh dấu hỏng" }).click();
        const faultDialog = adminPage.getByRole("dialog");
        await expect(faultDialog).toBeVisible();
        await faultDialog.getByLabel(/Lý do/).fill("Loi he thong o Phan 3 QA E2E");
        await faultDialog.getByRole("button", { name: "Đánh dấu hỏng và đóng lượt" }).click();
        await expect(faultDialog).toBeHidden({ timeout: 15_000 });

        await expect
          .poll(
            async () => {
              const res = await admin.api.get(`${ADMIN_URL}/api/v1/admin/attempts/${encodeURIComponent(attemptId)}`);
              if (!res.ok()) return "";
              const body = (await res.json()) as { endReason?: string; faultFlag?: string };
              return `${body.endReason ?? ""}:${body.faultFlag ?? ""}`;
            },
            { message: "attempt becomes FAULT_CLOSED with SYSTEM_FAULT", timeout: 15_000 },
          )
          .toBe("FAULT_CLOSED:SYSTEM_FAULT");

        await adminPage.getByRole("button", { name: "Cấp bù 1 lượt" }).click();
        const compDialog = adminPage.getByRole("dialog");
        await expect(compDialog).toBeVisible();
        await compDialog.getByLabel(/Lý do/).fill("Cap bu 1 luot do loi he thong QA E2E");
        await compDialog.getByRole("button", { name: "Cấp bù 1 lượt" }).click();
        await expect(compDialog).toBeHidden({ timeout: 15_000 });
        await expect(adminPage.getByText("Lượt đã đánh dấu hỏng và đã cấp bù 1 lượt.").first()).toBeVisible({
          timeout: 15_000,
        });
      } finally {
        await adminCtx.close();
      }

      // Học viên nhận lại +1 lượt và thấy banner cấp bù ở /result
      await expect
        .poll(
          async () => (await getMyExams(page)).find((item) => item.entitlementId === hsa.entitlementId)?.attemptsLeft,
          { message: "student attemptsLeft restored to 3 after compensation", timeout: 15_000 },
        )
        .toBe(3);

      await page.goto(`${WEB_URL}/attempts/${encodeURIComponent(attemptId)}/result`);
      await expect(page.getByText("Lượt bị ảnh hưởng bởi lỗi hệ thống, đã cấp bù 1 lượt").first()).toBeVisible({
        timeout: 20_000,
      });

      // Gọi compensate lần 2 qua Admin API phải trả 409 ALREADY_COMPENSATED và không cộng thêm lượt
      const duplicateCompRes = await admin.api.post(`${ADMIN_URL}/api/v1/admin/attempts/${encodeURIComponent(attemptId)}/compensate`, {
        headers: { ...admin.headers, "idempotency-key": randomUUID() },
        data: { reason: "Thu cap bu lan 2" },
      });
      expect(duplicateCompRes.status(), await duplicateCompRes.text()).toBe(409);
      const duplicateCompBody = (await duplicateCompRes.json()) as { code?: string };
      expect(duplicateCompBody.code).toBe("ALREADY_COMPENSATED");

      const finalHsa = (await getMyExams(page)).find((item) => item.entitlementId === hsa.entitlementId);
      expect(finalHsa?.attemptsLeft).toBe(3);
    } finally {
      if (faultedAudioExamId) {
        await setAudioFault(faultedAudioExamId, false).catch(() => undefined);
      }
      await admin.api.dispose();
      await context.close();
    }
  });

  test("5. Hoàn tiền toàn bộ khi học viên đang có lượt IN_PROGRESS: lượt còn lại về 0, không thể mở lượt mới, lượt đang làm vẫn nộp SCORED", async ({
    browser,
  }) => {
    const { cookies } = await createVerifiedAccount("qa.fp5");
    const { context, page } = await openStudentPage(browser, cookies);

    try {
      await page.goto(`${WEB_URL}/checkout/DS-HSA-PART-MATH-MINI`);
      await page.getByRole("button", { name: "Thanh toán qua VNPAY" }).click();
      await page.waitForURL(/\/mock-vnpay/);
      await page.locator('a[data-mock-action="success"]').click();
      await page.waitForURL(/\/checkout\/result/);
      await expect(page.getByRole("heading", { level: 1, name: "Đã cấp bài thi" })).toBeVisible();
      const orderCode = await extractOrderCodeFromResultPage(page);

      const exams = await getMyExams(page);
      const target = exams.find((item) => item.code === "DS-HSA-PART-MATH-MINI");
      expect(target?.entitlementId).toBeTruthy();
      const entitlementId = target!.entitlementId!;

      const attemptId = await startAttemptFromPrepare(page, entitlementId);
      await answerCurrentUnit(page);
      await waitForSavedStatus(page);

      // Admin hoàn tiền toàn bộ đơn khi lượt thi đang IN_PROGRESS
      const adminCtx = await browser.newContext();
      try {
        await loginAdmin(adminCtx, "vi");
        const adminPage = await adminCtx.newPage();
        await adminPage.goto(`${ADMIN_URL}/orders/${encodeURIComponent(orderCode)}`);
        await expect(adminPage.getByRole("heading", { name: new RegExp(orderCode) })).toBeVisible({ timeout: 20_000 });

        await adminPage.getByRole("button", { name: "Hoàn tiền" }).first().click();
        const dialog = adminPage.getByRole("dialog");
        await expect(dialog).toBeVisible();
        await dialog.getByLabel(/Xác nhận đây là lỗi hệ thống/).check();
        await dialog.getByLabel(/Lý do hoàn tiền/).fill("Hoan tien toan bo khi dang lam bai QA E2E");
        await dialog.getByRole("button", { name: "Gửi yêu cầu hoàn tiền" }).click();
        await expect(dialog).toBeHidden({ timeout: 25_000 });
        await expect(adminPage.getByText("Đã hoàn toàn bộ").first()).toBeVisible({ timeout: 15_000 });
      } finally {
        await adminCtx.close();
      }

      // Quyền chỉ còn lượt đang dở, attemptsLeft về 0, không thể mở lượt mới
      const afterRefundExams = await getMyExams(page);
      const updated = afterRefundExams.find((item) => item.entitlementId === entitlementId);
      expect(updated?.attemptsLeft).toBe(0);
      expect(updated?.activeAttemptId).toBe(attemptId);
      expect(updated?.activeAttemptState).toBe("IN_PROGRESS");

      await page.goto(`${WEB_URL}/home`);
      const examCard = page.locator("article", { hasText: "HSA theo phần Toán (thu nhỏ)" }).first();
      await expect(examCard).toBeVisible({ timeout: 15_000 });
      await expect(examCard.locator("strong")).toHaveText("0");
      await expect(examCard.getByRole("link", { name: "Tiếp tục" })).toBeVisible();
      await expect(examCard.getByRole("link", { name: "Bắt đầu" })).toHaveCount(0);

      // Khi lượt thi đang IN_PROGRESS sau hoàn tiền: POST /attempts nối lại lượt đang mở (200 resumed: true, LT F6) mà không trừ lượt mới
      const resumeAttemptRes = await page.request.post(`${WEB_URL}/api/v1/me/attempts`, {
        headers: {
          ...(await studentWriteHeaders(page)),
          "Idempotency-Key": randomUUID(),
        },
        data: { entitlementId },
      });
      expect(resumeAttemptRes.status(), await resumeAttemptRes.text()).toBe(200);
      const resumeAttemptBody = (await resumeAttemptRes.json()) as {
        attemptId?: string;
        state?: string;
        resumed?: boolean;
      };
      expect(resumeAttemptBody.attemptId).toBe(attemptId);
      expect(resumeAttemptBody.state).toBe("IN_PROGRESS");
      expect(resumeAttemptBody.resumed).toBe(true);

      // Học viên quay lại lượt đang làm và nộp bài thành công tới SCORED
      await examCard.getByRole("link", { name: "Tiếp tục" }).click();
      await page.waitForURL(new RegExp(`/attempts/${attemptId}`));
      await expect(page.locator("main fieldset[data-attempt-question-fieldset]")).toBeVisible({ timeout: 20_000 });
      await page.getByRole("button", { name: "Nộp bài" }).click();
      const submitDialog = page.getByRole("dialog");
      await expect(submitDialog).toBeVisible();
      await submitDialog.getByRole("button", { name: "Nộp bài" }).click();

      await expect(page.getByRole("heading", { name: "Lượt thi đã đóng" })).toBeVisible({ timeout: 25_000 });
      const viewResultLink = page.getByRole("link", { name: "Xem kết quả" });
      await expect(viewResultLink).toHaveAttribute("href", `/attempts/${attemptId}/result`);
      await viewResultLink.click();
      await page.waitForURL(new RegExp(`/attempts/${attemptId}/result`), { timeout: 45_000 });
      await expect
        .poll(
          async () => {
            const res = await page.request.get(`${WEB_URL}/api/v1/me/attempts/${encodeURIComponent(attemptId)}/result`);
            if (!res.ok()) return "";
            return ((await res.json()) as { state?: string }).state ?? "";
          },
          { message: "in-progress attempt settles to SCORED after refund", timeout: 45_000, intervals: [500, 1_000] },
        )
        .toBe("SCORED");

      // Sau khi lượt đang dở đã SCORED: không thể mở lượt mới (409 NO_ATTEMPTS_LEFT) và /home chỉ còn 0 lượt, Hết lượt / Mua lại
      const blockedAfterScoredRes = await page.request.post(`${WEB_URL}/api/v1/me/attempts`, {
        headers: {
          ...(await studentWriteHeaders(page)),
          "Idempotency-Key": randomUUID(),
        },
        data: { entitlementId },
      });
      expect(blockedAfterScoredRes.status(), await blockedAfterScoredRes.text()).toBe(409);
      const blockedAfterScoredBody = (await blockedAfterScoredRes.json()) as { code?: string };
      expect(blockedAfterScoredBody.code).toBe("NO_ATTEMPTS_LEFT");

      await page.goto(`${WEB_URL}/home`);
      await page.reload();
      const cardAfterScored = page.locator("article", { hasText: "HSA theo phần Toán (thu nhỏ)" }).first();
      await expect(cardAfterScored).toBeVisible({ timeout: 15_000 });
      await expect(cardAfterScored.locator("strong")).toHaveText("0");
      await expect(cardAfterScored.getByRole("link", { name: "Bắt đầu" })).toHaveCount(0);
      await expect(cardAfterScored.getByRole("link", { name: "Tiếp tục" })).toHaveCount(0);
      await expect(cardAfterScored.getByText("Hết lượt").first()).toBeVisible();
      await expect(cardAfterScored.getByRole("link", { name: "Mua lại" })).toBeVisible();
    } finally {
      await context.close();
    }
  });
});
