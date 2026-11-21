import crypto from "node:crypto";
import { expect, type APIRequestContext, type Browser, type Locator, type Page } from "@playwright/test";
import { ADMIN_URL, loginAdmin, RUN_ID } from "./editor-qa";
import { WEB_URL } from "./mail";
import { createPlatformApiContext } from "./platform";
import { allExams, waitForPublishedExamSet, type PublishedExam } from "./published-exam-set";

export const API_URL = (process.env.THI360_API_URL ?? "http://localhost:8080").replace(/\/+$/, "");

export interface MyExamItem {
  entitlementId: string | null;
  code: string;
  name: string;
  activeAttemptId: string | null;
  activeAttemptState: string | null;
  attemptsLeft: number;
}

export interface AttemptStatePartSummary {
  partId: string;
  ord: number;
  name: string;
  state: string;
  deadlineAt: string | null;
}

export interface AttemptStateApiResponse {
  attemptId: string;
  examId: string;
  examCode: string;
  examName: string;
  state: string;
  navigationMode?: string;
  serverNow: string;
  examDeadlineAt: string | null;
  activePartOrd: number | null;
  activePartDeadlineAt?: string | null;
  parts: AttemptStatePartSummary[];
}

export interface AttemptDraftIdbSnapshot {
  itemDrafts: Array<{
    id: string;
    userId: string;
    attemptId: string;
    itemId: string;
    seq: number;
    syncedSeq: number;
    answer: unknown;
    flagged: boolean;
    dirtyAnswer: boolean;
    dirtyFlag: boolean;
  }>;
  attemptMeta: Array<{
    id: string;
    userId: string;
    attemptId: string;
    position: { partId: string; itemId: string } | null;
    updatedAt: number;
  }>;
}

function resolveExamFixture(examCodeOrProduct: string): { code: string; productCode: string; fixture?: PublishedExam } {
  const found = allExams().find(
    (exam) => exam.code === examCodeOrProduct || exam.productCode === examCodeOrProduct,
  );
  if (found) {
    return { code: found.code, productCode: found.productCode, fixture: found };
  }
  return { code: examCodeOrProduct, productCode: examCodeOrProduct };
}

export async function ensureDsReady(browser: Browser): Promise<void> {
  const context = await browser.newContext();
  try {
    await loginAdmin(context, "vi");
    await waitForPublishedExamSet(context.request, { timeoutMs: 240_000 });
  } finally {
    await context.close();
  }
}

export async function getTestClockNow(request: APIRequestContext): Promise<string> {
  const response = await request.get(`${API_URL}/api/v1/test/clock`);
  expect(response.status(), `GET /api/v1/test/clock: ${await response.text()}`).toBe(200);
  const body = (await response.json()) as { serverNow: string };
  return body.serverNow;
}

export async function advanceTestClock(request: APIRequestContext, advanceSeconds: number): Promise<string> {
  const response = await request.post(`${API_URL}/api/v1/test/clock`, {
    data: { advanceSeconds },
  });
  expect(response.status(), `POST /api/v1/test/clock advance: ${await response.text()}`).toBe(200);
  const body = (await response.json()) as { serverNow: string };
  return body.serverNow;
}

export async function resetTestClock(request: APIRequestContext): Promise<void> {
  const response = await request.post(`${API_URL}/api/v1/test/clock`, {
    data: { reset: true },
  });
  expect(response.status(), `POST /api/v1/test/clock reset: ${await response.text()}`).toBe(200);
}

export async function getMyExams(page: Page): Promise<MyExamItem[]> {
  const response = await page.request.get(`${WEB_URL}/api/v1/me/exams`);
  expect(response.status(), `GET /api/v1/me/exams: ${await response.text()}`).toBe(200);
  const body = (await response.json()) as { items?: MyExamItem[] };
  return body.items ?? [];
}

export async function purchaseExamViaUi(page: Page, examCodeOrProduct: string): Promise<{ entitlementId: string; code: string }> {
  const { code, productCode } = resolveExamFixture(examCodeOrProduct);
  await page.goto(`${WEB_URL}/checkout/${encodeURIComponent(productCode)}`);
  const payButton = page.getByRole("button", { name: "Thanh toán qua VNPAY" });
  const createFreeButton = page.getByRole("button", { name: "Nhận bài thi" });

  await expect(payButton.or(createFreeButton)).toBeVisible({ timeout: 20_000 });
  if (await payButton.isVisible()) {
    await payButton.click();
    await page.waitForURL(/\/mock-vnpay/, { timeout: 20_000 });
    await page.locator('a[data-mock-action="success"]').click();
  } else {
    await createFreeButton.click();
  }

  await page.waitForURL(/\/checkout\/result/, { timeout: 20_000 });
  await expect(page.getByRole("heading", { name: /Đã cấp bài thi/ })).toBeVisible({ timeout: 20_000 });

  let matchedEntitlementId: string | null = null;
  await expect
    .poll(
      async () => {
        const items = await getMyExams(page);
        const match = items.find((item) => item.code === code && item.entitlementId);
        matchedEntitlementId = match?.entitlementId ?? null;
        return matchedEntitlementId;
      },
      { timeout: 20_000, message: `Chờ entitlement cho đề ${code}` },
    )
    .not.toBeNull();

  return { entitlementId: matchedEntitlementId!, code };
}

export async function publishThptqgMath3PartExam(
  label: string,
): Promise<{ id: string; code: string; name: string }> {
  const suffix = crypto.randomUUID().slice(0, 6).toUpperCase();
  const code = `AC33-TOAN-${RUN_ID}-${label}-${suffix}`.toUpperCase();
  const name = `Đề Toán THPTQG 3 phần (${label}-${suffix})`;
  const { api, xsrf } = await createPlatformApiContext();

  try {
    const authHeaders = (withIdempotency: boolean): Record<string, string> => ({
      origin: ADMIN_URL,
      "x-xsrf-token": xsrf,
      ...(withIdempotency ? { "Idempotency-Key": crypto.randomUUID() } : {}),
    });

    const createRes = await api.post(`${ADMIN_URL}/api/v1/admin/exams`, {
      headers: authHeaders(true),
      data: {
        kind: "THPTQG",
        scope: "SUBJECT",
        subjectCode: "TOAN",
        code,
        name,
        durationMinutes: 90,
        buildMode: "STANDARD",
      },
    });
    expect(createRes.status(), `create admin exam: ${await createRes.text()}`).toBe(201);
    const created = (await createRes.json()) as {
      id: string;
      templateCode?: string | null;
      revision: number;
    };

    let currentRevision = created.revision;
    if (created.templateCode !== "THPTQG_MATH_2025") {
      const tplRes = await api.post(`${ADMIN_URL}/api/v1/admin/exams/${created.id}/structure/template`, {
        headers: authHeaders(false),
        data: {
          templateCode: "THPTQG_MATH_2025",
          confirmReplace: true,
          overviewPolicy: "USE_TEMPLATE",
          expectedRevision: currentRevision,
        },
      });
      expect(tplRes.status(), `load THPTQG_MATH_2025 template: ${await tplRes.text()}`).toBe(200);
      const afterTpl = (await tplRes.json()) as { revision: number };
      currentRevision = afterTpl.revision;
    } else {
      const detailRes = await api.get(`${ADMIN_URL}/api/v1/admin/exams/${created.id}`);
      expect(detailRes.status()).toBe(200);
      const detail = (await detailRes.json()) as { revision: number };
      currentRevision = detail.revision;
    }

    const listingRes = await api.put(`${ADMIN_URL}/api/v1/admin/exams/${created.id}/listing`, {
      headers: authHeaders(false),
      data: {
        summary: "Đề Toán THPTQG 2025 ba phần cho kiểm thử tự do điều hướng.",
        objectives: "Luyện tập đầy đủ 3 phần Trắc nghiệm, Đúng/Sai và Trả lời ngắn.",
        audience: "Thí sinh ôn thi THPTQG môn Toán.",
        listPriceVnd: 19000,
        attemptsGranted: 3,
        benefitAiExplanation: true,
        benefitStrengthWeakness: true,
        benefitPractice: true,
        resultView: "SCORE_AND_ANSWERS",
        description: name,
        expectedRevision: currentRevision,
      },
    });
    expect(listingRes.status(), `update listing: ${await listingRes.text()}`).toBe(200);

    const checkRes = await api.post(`${ADMIN_URL}/api/v1/admin/exams/${created.id}/check`, {
      headers: authHeaders(false),
      data: {},
    });
    expect(checkRes.status(), `check exam: ${await checkRes.text()}`).toBe(200);
    const checkBody = (await checkRes.json()) as {
      status: string;
      paths?: Array<{ pathId: string; result: string }>;
    };
    expect(checkBody.status, `exam check response: ${JSON.stringify(checkBody)}`).toBe("VALID");
    const validPathIds = (checkBody.paths ?? [])
      .filter((path) => path.result === "VALID")
      .map((path) => path.pathId);
    expect(validPathIds.length).toBeGreaterThan(0);

    const prePublishDetail = await api.get(`${ADMIN_URL}/api/v1/admin/exams/${created.id}`);
    expect(prePublishDetail.status()).toBe(200);
    const latestDetail = (await prePublishDetail.json()) as { revision: number };

    const publishRes = await api.post(`${ADMIN_URL}/api/v1/admin/exams/${created.id}/publish`, {
      headers: authHeaders(true),
      data: {
        pathIds: validPathIds,
        note: "FS-5 AC-33 automated E2E",
        expectedRevision: latestDetail.revision,
      },
    });
    expect(publishRes.status(), `publish exam: ${await publishRes.text()}`).toBe(200);

    await expect
      .poll(
        async () => {
          const storefrontRes = await api.get(
            `${WEB_URL}/api/v1/public/storefront/products/${encodeURIComponent(code)}`,
          );
          return storefrontRes.status();
        },
        { timeout: 25_000, message: `Chờ storefront đồng bộ đề ${code}` },
      )
      .toBe(200);

    return { id: created.id, code, name };
  } finally {
    await api.dispose();
  }
}

export function extractAttemptId(url: string): string {
  const match = url.match(/\/attempts\/([0-9a-f-]+)/i);
  if (!match?.[1]) {
    throw new Error(`Không trích xuất được attemptId từ URL: ${url}`);
  }
  return match[1];
}

export async function startAttemptFromPrepare(
  page: Page,
  entitlementId: string,
  options?: { preChoiceCode?: string },
): Promise<string> {
  await page.goto(`${WEB_URL}/prepare/${encodeURIComponent(entitlementId)}`);
  await expect(page.locator("h1[data-exam-content]")).toBeVisible({ timeout: 20_000 });

  const radios = page.locator('input[type="radio"][name="preChoice"]');
  const radioCount = await radios.count();
  if (radioCount > 0) {
    if (options?.preChoiceCode) {
      await page.locator(`input[type="radio"][name="preChoice"][value="${options.preChoiceCode}"]`).check();
    } else {
      await radios.first().check();
    }
  }

  const startButton = page.getByRole("button", { name: "Bắt đầu bài thi" });
  await expect(startButton).toBeEnabled({ timeout: 10_000 });
  await startButton.click();

  await page.waitForURL(/\/attempts\/[0-9a-f-]+$/i, { timeout: 25_000 });
  const attemptId = extractAttemptId(page.url());

  const submitButton = page.getByRole("button", { name: "Nộp bài" });
  const partChoiceHeading = page.getByRole("heading", { name: "Chọn phần thi thứ ba" });
  const closedHeading = page.getByRole("heading", { name: "Lượt thi đã đóng" });
  await expect(submitButton.or(partChoiceHeading).or(closedHeading)).toBeVisible({ timeout: 45_000 });

  return attemptId;
}

export async function getAttemptStateApi(
  request: APIRequestContext,
  attemptId: string,
): Promise<AttemptStateApiResponse> {
  const response = await request.get(`${WEB_URL}/api/v1/me/attempts/${encodeURIComponent(attemptId)}`);
  expect(response.status(), `GET /api/v1/me/attempts/${attemptId}: ${await response.text()}`).toBe(200);
  return (await response.json()) as AttemptStateApiResponse;
}

export async function waitForSavedStatus(page: Page): Promise<void> {
  const statusPill = page
    .locator('header [role="status"]')
    .filter({ hasText: /^(Đã lưu|Saved)$/ })
    .first();
  await expect(statusPill).toBeVisible({ timeout: 15_000 });
}

export function parseTimerSeconds(rawText: string): number {
  const cleaned = rawText.trim();
  const parts = cleaned.split(":").map((segment) => Number.parseInt(segment, 10));
  if (parts.some((n) => Number.isNaN(n))) {
    throw new Error(`Không phân tích được đồng hồ đếm ngược: "${rawText}"`);
  }
  if (parts.length === 2) {
    return parts[0]! * 60 + parts[1]!;
  }
  if (parts.length === 3) {
    return parts[0]! * 3600 + parts[1]! * 60 + parts[2]!;
  }
  throw new Error(`Định dạng đồng hồ không hợp lệ: "${rawText}"`);
}

async function waitForEssayEditorIfPresent(fieldset: Locator): Promise<void> {
  const loadingText = fieldset.getByText("Đang tải trình soạn bài viết…");
  if ((await loadingText.count()) > 0) {
    await expect(loadingText).toBeHidden({ timeout: 20_000 });
  }
}

export async function answerCurrentUnit(page: Page, textAnswer = "42"): Promise<number> {
  const fieldset = page.locator("main fieldset").first();
  await expect(fieldset).toBeVisible({ timeout: 15_000 });
  await expect(fieldset).not.toHaveAttribute("disabled", "", { timeout: 15_000 });
  await waitForEssayEditorIfPresent(fieldset);

  let interactions = 0;

  const radioGroups = fieldset.locator('[role="radiogroup"]');
  const rgCount = await radioGroups.count();
  for (let i = 0; i < rgCount; i += 1) {
    const group = radioGroups.nth(i);
    const nativeChecked = await group.locator('input[type="radio"]:checked').count();
    const ariaChecked = await group.locator('[role="radio"][aria-checked="true"]').count();
    if (nativeChecked > 0 || ariaChecked > 0) {
      continue;
    }
    const nativeRadios = group.locator('input[type="radio"]:not(:disabled)');
    if ((await nativeRadios.count()) > 0) {
      await nativeRadios.first().click();
      interactions += 1;
      continue;
    }
    const roleRadios = group.locator('[role="radio"]:not([aria-disabled="true"])');
    if ((await roleRadios.count()) > 0) {
      await roleRadios.first().click();
      interactions += 1;
    }
  }

  const choiceContainers = fieldset.locator("[data-question-options]");
  const ccCount = await choiceContainers.count();
  for (let i = 0; i < ccCount; i += 1) {
    const container = choiceContainers.nth(i);
    const hasRadios = (await container.locator('input[type="radio"], [role="radio"]').count()) > 0;
    if (hasRadios) {
      const checkedCount = await container.locator('input[type="radio"]:checked, [role="radio"][aria-checked="true"]').count();
      if (checkedCount === 0) {
        const firstRadio = container.locator('input[type="radio"]:not(:disabled), [role="radio"]:not([aria-disabled="true"])').first();
        await firstRadio.click();
        interactions += 1;
      }
      continue;
    }
    const checkboxes = container.locator('button[role="checkbox"]:not([disabled]), input[type="checkbox"]:not(:disabled)');
    if ((await checkboxes.count()) > 0) {
      const alreadyChecked = await container
        .locator('button[role="checkbox"][aria-checked="true"], input[type="checkbox"]:checked')
        .count();
      if (alreadyChecked === 0) {
        await checkboxes.first().click();
        interactions += 1;
      }
    }
  }

  const comboboxes = fieldset.locator('button[role="combobox"]:not([disabled])');
  const cbCount = await comboboxes.count();
  for (let i = 0; i < cbCount; i += 1) {
    const combo = comboboxes.nth(i);
    const hasPlaceholder = (await combo.locator("[data-placeholder]").count()) > 0;
    if (!hasPlaceholder) {
      continue;
    }
    await combo.click();
    const firstOption = page.locator('[role="option"]:not([data-disabled])').first();
    await expect(firstOption).toBeVisible({ timeout: 5_000 });
    await firstOption.click();
    interactions += 1;
  }

  const textInputs = fieldset.locator(
    'input:not([type="radio"]):not([type="checkbox"]):not([type="hidden"]):not([type="file"]):not(:disabled)',
  );
  const inputCount = await textInputs.count();
  for (let i = 0; i < inputCount; i += 1) {
    const input = textInputs.nth(i);
    if ((await input.inputValue()).trim().length === 0) {
      await input.fill(textAnswer);
      interactions += 1;
    }
  }

  const essayEditors = fieldset.locator('[contenteditable="true"]');
  const essayCount = await essayEditors.count();
  for (let i = 0; i < essayCount; i += 1) {
    const editor = essayEditors.nth(i);
    if ((await editor.innerText()).trim().length === 0) {
      await editor.click();
      await page.keyboard.type(`Bài làm tự luận E2E ${textAnswer}`, { delay: 0 });
      interactions += 1;
    }
  }

  return interactions;
}

export async function readAttemptDraftsIdb(page: Page): Promise<AttemptDraftIdbSnapshot> {
  return page.evaluate(async () => {
    const empty: AttemptDraftIdbSnapshot = { itemDrafts: [], attemptMeta: [] };
    if (typeof indexedDB === "undefined") {
      return empty;
    }

    return new Promise<AttemptDraftIdbSnapshot>((resolveSnapshot) => {
      const request = indexedDB.open("thi360-attempt-drafts", 1);
      let createdFresh = false;
      request.onupgradeneeded = () => {
        createdFresh = true;
      };
      request.onerror = () => resolveSnapshot(empty);
      request.onsuccess = () => {
        const db = request.result;
        if (createdFresh || !db.objectStoreNames.contains("itemDrafts")) {
          db.close();
          if (createdFresh) {
            indexedDB.deleteDatabase("thi360-attempt-drafts");
          }
          resolveSnapshot(empty);
          return;
        }

        const stores: string[] = ["itemDrafts"];
        if (db.objectStoreNames.contains("attemptMeta")) {
          stores.push("attemptMeta");
        }
        const tx = db.transaction(stores, "readonly");
        const itemReq = tx.objectStore("itemDrafts").getAll();
        const metaReq = stores.includes("attemptMeta") ? tx.objectStore("attemptMeta").getAll() : null;
        tx.oncomplete = () => {
          db.close();
          resolveSnapshot({
            itemDrafts: (itemReq.result ?? []) as AttemptDraftIdbSnapshot["itemDrafts"],
            attemptMeta: (metaReq?.result ?? []) as AttemptDraftIdbSnapshot["attemptMeta"],
          });
        };
        tx.onerror = () => {
          db.close();
          resolveSnapshot(empty);
        };
      };
    });
  });
}
