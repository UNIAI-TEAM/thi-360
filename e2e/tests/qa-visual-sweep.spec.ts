import { expect, request as playwrightRequest, test, type Page } from "@playwright/test";

import { adminApi, ADMIN_URL, loginAdmin, WEB_URL } from "./helpers/editor-qa";
import {
  answerCurrentUnit,
  API_URL,
  ensureDsReady,
  purchaseExamViaUi,
  startAttemptFromPrepare,
  waitForSavedStatus,
} from "./helpers/attempt-fixtures";
import { ACCOUNT_PASSWORD, createVerifiedAccount, type E2eAccount, type SessionCookies } from "./helpers/mail";
import { sp2eBundle } from "./helpers/published-exam-set";
import { applyMatrixCookies, expectMatrixClean, launchMatrixBrowser, matrixCells, openMatrixContext } from "./helpers/visual-matrix";

const SESSION_MAX_AGE_MS = 3 * 60 * 1000;

function escapeRegExp(value: string): string {
  return value.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
}

let cachedAllowForms: Promise<Set<string>> | undefined;

function knownDynamicForms(): Promise<Set<string>> {
  cachedAllowForms ??= (async () => {
    const forms = new Set<string>();
    const api = await playwrightRequest.newContext();
    try {
      const filtersRes = await api.get(`${API_URL}/api/v1/public/storefront/filters`);
      if (filtersRes.ok()) {
        const data = (await filtersRes.json()) as {
          parts?: Array<{ kindCode: string; name: string }>;
          exams?: Array<{ code: string; name: string }>;
          kinds?: Array<{ code: string; name: string }>;
        };
        for (const p of data.parts ?? []) {
          forms.add(p.name);
          forms.add(`${p.kindCode} · ${p.name}`);
        }
        for (const e of data.exams ?? []) {
          forms.add(e.name);
        }
        for (const k of data.kinds ?? []) {
          forms.add(k.name);
        }
      }
    } finally {
      await api.dispose();
    }

    const admin = await adminApi();
    try {
      for (const path of [
        "/api/v1/admin/exams?page=0&size=100",
        "/api/v1/admin/bundles?page=0&size=100",
        "/api/v1/admin/promotions?page=0&size=100",
      ]) {
        const res = await admin.api.get(`${ADMIN_URL}${path}`);
        if (res.ok()) {
          const body = (await res.json()) as { items?: Array<{ name?: string; title?: string; code?: string }> };
          for (const item of body.items ?? []) {
            if (item.name) forms.add(item.name);
            if (item.title) forms.add(item.title);
          }
        }
      }
    } finally {
      await admin.api.dispose();
    }

    return forms;
  })();
  return cachedAllowForms;
}

async function buildAllowLanguage(page: Page, options: { includeTitle?: boolean } = {}): Promise<RegExp[]> {
  const { optionTexts, examContentTexts } = await page.evaluate(() => ({
    optionTexts: [...document.querySelectorAll<HTMLOptionElement>('select[aria-hidden="true"] option')].map((opt) =>
      opt.text.replace(/\s+/g, " ").trim(),
    ),
    examContentTexts: [...document.querySelectorAll("[data-exam-content]")].map((el) =>
      (el.textContent ?? "").replace(/\s+/g, " ").trim(),
    ),
  }));
  const known = await knownDynamicForms();
  for (const t of examContentTexts) {
    if (t) known.add(t);
  }
  const allowStrings = [
    ...optionTexts.filter((text) => known.has(text)),
    ...(options.includeTitle ? [await page.title()] : []),
  ].filter(Boolean);

  return [
    /^QA-/i,
    /^qa\./i,
    /^DS-/i,
    /^DH[0-9A-Z]+$/i,
    /^#?[0-9A-F]{8}$/i,
    ...allowStrings.map((text) => new RegExp(`^${escapeRegExp(text)}$`)),
  ];
}

async function waitForSettled(page: Page): Promise<void> {
  await expect(page.locator('[aria-busy="true"]')).toHaveCount(0);
  await page.evaluate(() => document.fonts.ready);
}

async function ensurePlayerActiveSession(page: Page, attemptId: string): Promise<void> {
  await page.goto(`${WEB_URL}/attempts/${encodeURIComponent(attemptId)}`);
  const takeoverHeading = page.getByRole("heading", {
    name: /Lượt thi đã được tiếp tục ở thiết bị khác|opened in another tab/i,
  });
  const fieldset = page.locator("main fieldset[data-attempt-question-fieldset]");

  for (let i = 0; i < 4; i += 1) {
    await Promise.race([
      fieldset.waitFor({ state: "visible", timeout: 10_000 }).catch(() => undefined),
      takeoverHeading.waitFor({ state: "visible", timeout: 10_000 }).catch(() => undefined),
    ]);
    if (await takeoverHeading.isVisible()) {
      await page.getByRole("button", { name: /Tiếp tục tại đây|Continue here/i }).click();
      await expect(takeoverHeading).toBeHidden({ timeout: 15_000 });
    }
    if (await fieldset.isVisible()) {
      break;
    }
  }
  await expect(fieldset).toBeVisible({ timeout: 20_000 });
  await expect(fieldset).not.toHaveAttribute("disabled", "", { timeout: 15_000 });
}

test.describe("AC-19 quét trực quan 12 màn hình x 8 ô ma trận", () => {
  let studentAccount: E2eAccount;
  let studentCookies: SessionCookies = [];
  let studentRefreshedAt = 0;
  let adminCookies: SessionCookies = [];
  let adminRefreshedAt = 0;
  let activeAttemptId = "";
  let scoredAttemptId = "";
  let bundleCode = "";

  async function freshStudentCookies(): Promise<SessionCookies> {
    if (Date.now() - studentRefreshedAt < SESSION_MAX_AGE_MS) {
      return studentCookies;
    }
    const api = await playwrightRequest.newContext();
    try {
      const res = await api.post(`${WEB_URL}/api/v1/auth/login`, {
        headers: { origin: WEB_URL },
        data: { identifier: studentAccount.username, password: ACCOUNT_PASSWORD },
      });
      expect(res.ok(), `refresh student session: ${res.status()}`).toBe(true);
      studentCookies = (await api.storageState()).cookies;
      studentRefreshedAt = Date.now();
      return studentCookies;
    } finally {
      await api.dispose();
    }
  }

  async function freshAdminCookies(matrixBrowser: Awaited<ReturnType<typeof launchMatrixBrowser>>): Promise<SessionCookies> {
    if (Date.now() - adminRefreshedAt < SESSION_MAX_AGE_MS) {
      return adminCookies;
    }
    const ctx = await matrixBrowser.newContext();
    try {
      await loginAdmin(ctx, "vi");
      adminCookies = await ctx.cookies();
      adminRefreshedAt = Date.now();
      return adminCookies;
    } finally {
      await ctx.close();
    }
  }

  test.beforeAll(async ({ browser }) => {
    await ensureDsReady(browser);
    bundleCode = sp2eBundle().code;

    const { account, cookies } = await createVerifiedAccount("qa.vis12");
    studentAccount = account;
    studentCookies = cookies;
    studentRefreshedAt = Date.now();

    const setupCtx = await browser.newContext();
    try {
      await setupCtx.addCookies(studentCookies);
      const page = await setupCtx.newPage();
      page.setDefaultTimeout(25_000);

      // 1. Tạo lượt đã SCORED (DS-HSA-PART-MATH-MINI) cho /attempts/[id]/result và /history
      const scoredPrep = await purchaseExamViaUi(page, "DS-HSA-PART-MATH-MINI");
      scoredAttemptId = await startAttemptFromPrepare(page, scoredPrep.entitlementId);
      await answerCurrentUnit(page);
      await waitForSavedStatus(page);
      await page.getByRole("button", { name: "Nộp bài" }).click();
      const submitDialog = page.getByRole("dialog");
      await expect(submitDialog).toBeVisible();
      await submitDialog.getByRole("button", { name: "Nộp bài" }).click();
      await page.waitForURL(new RegExp(`/attempts/${scoredAttemptId}/result`), { timeout: 45_000 });
      await expect
        .poll(
          async () => {
            const res = await page.request.get(`${WEB_URL}/api/v1/me/attempts/${encodeURIComponent(scoredAttemptId)}/result`);
            if (!res.ok()) return "";
            return ((await res.json()) as { state?: string }).state ?? "";
          },
          { message: "setup scored attempt for visual sweep", timeout: 45_000 },
        )
        .toBe("SCORED");

      // 2. Tạo lượt đang IN_PROGRESS (DS-HSA-FULL-MINI) cho /attempts/[activeId] và /home
      const activePrep = await purchaseExamViaUi(page, "DS-HSA-FULL-MINI");
      activeAttemptId = await startAttemptFromPrepare(page, activePrep.entitlementId);
    } finally {
      await setupCtx.close();
    }

    const adminCtx = await browser.newContext();
    try {
      await loginAdmin(adminCtx, "vi");
      adminCookies = await adminCtx.cookies();
      adminRefreshedAt = Date.now();
    } finally {
      await adminCtx.close();
    }
  });

  test("AC-19: 7 màn hình học viên và 5 màn hình quản trị đạt chuẩn trên cả 8 ô (1280/390 x light/dark x vi/en)", async () => {
    test.setTimeout(600_000);

    const matrixBrowser = await launchMatrixBrowser();
    const adminOverflow = { scope: "main", allowAccessibleScroller: true } as const;

    try {
      for (const cell of matrixCells()) {
        await test.step(`ô ${cell.width}px ${cell.mode} ${cell.lang}`, async () => {
          // 7 màn hình học viên
          const currentStudentCookies = await freshStudentCookies();
          const studentCtx = await openMatrixContext(matrixBrowser, cell, WEB_URL);
          try {
            await studentCtx.addCookies(currentStudentCookies);
            await applyMatrixCookies(studentCtx, cell, WEB_URL);
            const page = await studentCtx.newPage();
            page.setDefaultTimeout(25_000);

            await page.goto(`${WEB_URL}/`);
            await expect(page.getByRole("article").first()).toBeVisible({ timeout: 20_000 });
            await waitForSettled(page);
            await expectMatrixClean(page, "qa-catalog", cell, {
              allowLanguage: await buildAllowLanguage(page),
              elementOverflow: adminOverflow,
            });

            await page.goto(`${WEB_URL}/exams/${encodeURIComponent(bundleCode)}`);
            await expect(page.getByRole("heading", { level: 1 })).toBeVisible({ timeout: 20_000 });
            await waitForSettled(page);
            await expectMatrixClean(page, "qa-bundle-detail", cell, {
              allowLanguage: await buildAllowLanguage(page, { includeTitle: true }),
              elementOverflow: adminOverflow,
            });

            await page.goto(`${WEB_URL}/exams/DS-HSA-FULL-MINI`);
            await expect(page.getByRole("heading", { level: 1 })).toBeVisible({ timeout: 20_000 });
            await waitForSettled(page);
            await expectMatrixClean(page, "qa-exam-detail", cell, {
              allowLanguage: await buildAllowLanguage(page, { includeTitle: true }),
              elementOverflow: adminOverflow,
            });

            await page.goto(`${WEB_URL}/home`);
            await expect(page.getByRole("heading", { level: 1 })).toBeVisible({ timeout: 20_000 });
            await waitForSettled(page);
            await expectMatrixClean(page, "qa-home", cell, {
              allowLanguage: await buildAllowLanguage(page),
              elementOverflow: adminOverflow,
            });

            await ensurePlayerActiveSession(page, activeAttemptId);
            await waitForSettled(page);
            await expectMatrixClean(page, "qa-attempt", cell, {
              allowLanguage: await buildAllowLanguage(page, { includeTitle: true }),
            });

            await page.goto(`${WEB_URL}/attempts/${encodeURIComponent(scoredAttemptId)}/result`);
            await expect(page.getByRole("heading", { level: 1 })).toBeVisible({ timeout: 20_000 });
            await waitForSettled(page);
            await expectMatrixClean(page, "qa-result", cell, {
              allowLanguage: await buildAllowLanguage(page, { includeTitle: true }),
              elementOverflow: adminOverflow,
            });

            await page.goto(`${WEB_URL}/history`);
            await expect(page.getByRole("heading", { level: 1 })).toBeVisible({ timeout: 20_000 });
            await waitForSettled(page);
            await expectMatrixClean(page, "qa-history", cell, {
              allowLanguage: await buildAllowLanguage(page),
              elementOverflow: adminOverflow,
            });
          } finally {
            await studentCtx.close();
          }

          // 5 màn hình quản trị
          const currentAdminCookies = await freshAdminCookies(matrixBrowser);
          const adminCtx = await openMatrixContext(matrixBrowser, cell, ADMIN_URL);
          try {
            await adminCtx.addCookies(currentAdminCookies);
            await applyMatrixCookies(adminCtx, cell, ADMIN_URL);
            const page = await adminCtx.newPage();
            page.setDefaultTimeout(25_000);

            await page.goto(`${ADMIN_URL}/promotions`);
            await expect(page.getByRole("heading", { level: 1 })).toBeVisible({ timeout: 20_000 });
            await waitForSettled(page);
            await expectMatrixClean(page, "qa-admin-promotions", cell, {
              allowLanguage: await buildAllowLanguage(page),
              elementOverflow: adminOverflow,
            });

            await page.goto(`${ADMIN_URL}/orders`);
            await expect(page.getByRole("heading", { level: 1 })).toBeVisible({ timeout: 20_000 });
            await waitForSettled(page);
            await expectMatrixClean(page, "qa-admin-orders", cell, {
              allowLanguage: await buildAllowLanguage(page),
              elementOverflow: adminOverflow,
            });

            await page.goto(`${ADMIN_URL}/bundles`);
            await expect(page.getByRole("heading", { level: 1 })).toBeVisible({ timeout: 20_000 });
            await waitForSettled(page);
            await expectMatrixClean(page, "qa-admin-bundles", cell, {
              allowLanguage: await buildAllowLanguage(page),
              elementOverflow: adminOverflow,
            });

            await page.goto(`${ADMIN_URL}/grading`);
            await expect(page.getByRole("heading", { level: 1 })).toBeVisible({ timeout: 20_000 });
            await waitForSettled(page);
            await expectMatrixClean(page, "qa-admin-grading", cell, {
              allowLanguage: await buildAllowLanguage(page),
              elementOverflow: adminOverflow,
            });

            await page.goto(`${ADMIN_URL}/attempts`);
            await expect(page.getByRole("heading", { level: 1 })).toBeVisible({ timeout: 20_000 });
            await waitForSettled(page);
            await expectMatrixClean(page, "qa-admin-attempts", cell, {
              allowLanguage: await buildAllowLanguage(page),
              elementOverflow: adminOverflow,
            });
          } finally {
            await adminCtx.close();
          }
        });
      }
    } finally {
      await matrixBrowser.close();
    }
  });
});
