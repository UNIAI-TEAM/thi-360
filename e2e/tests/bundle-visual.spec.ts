import { expect, request as playwrightRequest, test, type Browser, type BrowserContext, type Page } from "@playwright/test";
import { adminApi, ADMIN_URL, loginAdmin, RUN_ID, WEB_URL, type AdminApi } from "./helpers/editor-qa";
import { ACCOUNT_PASSWORD, createVerifiedAccount, type E2eAccount, type SessionCookies } from "./helpers/mail";
import { examByCode, reviewExam, sp2eBundle, waitForPublishedExamSet } from "./helpers/published-exam-set";
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
 * AC-15 (UNI-2737, FB): kiểm thử giao diện ma trận 8 ô (1280 & 390 x light & dark x vi & en)
 * cho các màn hình bộ đề ở web admin (`/bundles`, `/bundles/new`, `/orders/<code>`) và web học viên
 * (`/?format=BUNDLE#exams`, `/exams/<code>`, `/checkout/<code>`, `/home`).
 */
test.setTimeout(480_000);

const API_URL = (process.env.THI360_API_URL ?? "http://localhost:8080").replace(/\/+$/, "");
const CLEAN_RUN_SUFFIX = RUN_ID.replace(/[^a-z0-9]/gi, "").toUpperCase().slice(0, 8) || "VIS";
const SESSION_MAX_AGE_MS = 3 * 60 * 1000;

interface StudentSessionHolder {
  account: E2eAccount;
  cookies: SessionCookies;
  refreshedAt: number;
}

let browser: Browser;
let reviewBundleCode = "";
let reviewBundleId = "";
let bundleOrderCode = "";
let detailStudent: StudentSessionHolder;
let homeStudent: StudentSessionHolder;

type ConsoleError = { text: string; url?: string };

function escapeRegExp(value: string): string {
  return value.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
}

function isExpectedResourceError(error: ConsoleError): boolean {
  const status = /^Failed to load resource: the server responded with a status of (\d+)/.exec(error.text)?.[1];
  if (!status || !error.url) return false;
  if (status === "401") {
    const pathname = new URL(error.url).pathname;
    return pathname === "/api/v1/me" || pathname === "/api/v1/auth/refresh";
  }
  return false;
}

function attachConsoleErrorTrap(page: Page): ConsoleError[] {
  const errors: ConsoleError[] = [];
  page.on("console", (message) => {
    if (message.type() === "error") errors.push({ text: message.text(), url: message.location().url });
  });
  page.on("pageerror", (error) => errors.push({ text: String(error) }));
  return errors;
}

async function settle(page: Page): Promise<void> {
  await expect(page.locator('[aria-busy="true"]')).toHaveCount(0);
  await page.evaluate(() => document.fonts.ready);
}

let partNameForms: Promise<Set<string>> | undefined;

function knownPartNameForms(): Promise<Set<string>> {
  partNameForms ??= (async () => {
    const api = await playwrightRequest.newContext();
    try {
      const response = await api.get(`${API_URL}/api/v1/public/storefront/filters`);
      expect(response.status(), "API bộ lọc danh mục phải trả được").toBe(200);
      const data = (await response.json()) as { parts: Array<{ kindCode: string; name: string }> };
      const forms = new Set<string>();
      for (const part of data.parts) {
        forms.add(part.name);
        forms.add(`${part.kindCode} · ${part.name}`);
      }
      return forms;
    } finally {
      await api.dispose();
    }
  })();
  return partNameForms;
}

async function buildAllowLanguage(
  page: Page,
  options: { includeTitle?: boolean; includePartNames?: boolean } = {},
): Promise<RegExp[]> {
  const { optionTexts, examContentTexts } = await page.evaluate(() => ({
    optionTexts: [...document.querySelectorAll<HTMLOptionElement>('select[aria-hidden="true"] option')].map((opt) =>
      opt.text.replace(/\s+/g, " ").trim(),
    ),
    examContentTexts: [...document.querySelectorAll("[data-exam-content]")].map((el) =>
      (el.textContent ?? "").replace(/\s+/g, " ").trim(),
    ),
  }));
  const examContent = new Set(examContentTexts);
  if (options.includePartNames) {
    for (const partForm of await knownPartNameForms()) {
      examContent.add(partForm);
    }
  }
  const allowedStrings = [
    ...optionTexts.filter((text) => examContent.has(text)),
    ...(options.includeTitle ? [await page.title()] : []),
  ];
  return allowedStrings.filter(Boolean).map((text) => new RegExp(`^${escapeRegExp(text)}$`));
}

async function waitForSeededBundle({ api }: AdminApi, bundleCode: string): Promise<void> {
  await expect
    .poll(
      async () => {
        const res = await api.get(`${ADMIN_URL}/api/v1/admin/bundles`, {
          params: { q: bundleCode, page: "0", size: "20" },
        });
        if (!res.ok()) return false;
        const page = (await res.json()) as { items: Array<{ code: string; status: string }> };
        return page.items.some((item) => item.code === bundleCode && item.status === "PUBLISHED");
      },
      { message: `wait for seeded bundle ${bundleCode} to be PUBLISHED`, timeout: 60_000, intervals: [500, 1000] },
    )
    .toBe(true);
}

async function ensureFreshStudentCookies(holder: StudentSessionHolder): Promise<SessionCookies> {
  if (Date.now() - holder.refreshedAt < SESSION_MAX_AGE_MS) {
    return holder.cookies;
  }
  const api = await playwrightRequest.newContext();
  try {
    const res = await api.post(`${WEB_URL}/api/v1/auth/login`, {
      headers: { origin: WEB_URL },
      data: { identifier: holder.account.username, password: ACCOUNT_PASSWORD },
    });
    expect(res.ok(), `refresh student ${holder.account.username}: ${res.status()}`).toBe(true);
    holder.cookies = (await api.storageState()).cookies;
    holder.refreshedAt = Date.now();
    return holder.cookies;
  } finally {
    await api.dispose();
  }
}

async function buyProductViaCheckout(
  sessionCookies: SessionCookies,
  productCode: string,
): Promise<string> {
  const ctx = await browser.newContext();
  try {
    await ctx.addCookies(sessionCookies);
    const page = await ctx.newPage();
    await page.goto(`${WEB_URL}/checkout/${encodeURIComponent(productCode)}`);
    await page.getByRole("button", { name: "Thanh toán qua VNPAY" }).click();
    await page.waitForURL(/\/mock-vnpay/);
    await page.locator('a[data-mock-action="success"]').click();
    await page.waitForURL(/\/checkout\/result/);
    await expect(page.getByRole("heading", { level: 1, name: "Đã cấp bài thi" })).toBeVisible();

    const urlOrder = new URL(page.url()).searchParams.get("order");
    if (urlOrder) return urlOrder;

    const detailHref = await page.getByRole("link", { name: "Xem chi tiết đơn" }).getAttribute("href");
    const orderCode = (detailHref ?? "").split("/").pop() ?? "";
    expect(orderCode).toMatch(/^DH/i);
    return orderCode;
  } finally {
    await ctx.close();
  }
}

async function openStudentCell(
  cell: MatrixCell,
  holder: StudentSessionHolder,
): Promise<{ context: BrowserContext; page: Page; errors: ConsoleError[] }> {
  const cookies = await ensureFreshStudentCookies(holder);
  const context = await openMatrixContext(browser, cell, WEB_URL);
  await context.addCookies(cookies);
  await applyMatrixCookies(context, cell, WEB_URL);
  const page = await context.newPage();
  const errors = attachConsoleErrorTrap(page);
  return { context, page, errors };
}

test.beforeAll(async () => {
  browser = await launchMatrixBrowser();

  const dsCtx = await browser.newContext();
  try {
    await loginAdmin(dsCtx, "vi");
    await waitForPublishedExamSet(dsCtx.request, { timeoutMs: 240_000 });
  } finally {
    await dsCtx.close();
  }

  const admin = await adminApi();
  try {
    const seededSp2e = sp2eBundle();
    await waitForSeededBundle(admin, seededSp2e.code);

    const kindsRes = await admin.api.get(`${ADMIN_URL}/api/v1/catalog/options`, { params: { group: "EXAM" } });
    expect(kindsRes.status(), await kindsRes.text()).toBe(200);
    const kinds = (await kindsRes.json()) as Array<{ id: string; code: string }>;
    const sp2eKind = kinds.find((k) => k.code === "SP2E");
    const hsaKind = kinds.find((k) => k.code === "HSA");
    expect(sp2eKind, "Missing SP2E exam kind").toBeDefined();
    expect(hsaKind, "Missing HSA exam kind").toBeDefined();

    // 1. Tạo 1 bộ đề nháp SP2E để danh sách admin (/bundles) có đủ trạng thái Nháp và Đang bán
    const sp2eCandRes = await admin.api.get(`${ADMIN_URL}/api/v1/admin/bundles/candidates`, {
      params: { examKind: "SP2E" },
    });
    expect(sp2eCandRes.status(), await sp2eCandRes.text()).toBe(200);
    const sp2eCands = (await sp2eCandRes.json()) as Array<{ examId: string; code: string }>;
    const draftItemIds = seededSp2e.examCodes
      .slice(0, 2)
      .map((code) => sp2eCands.find((c) => c.code === code)?.examId)
      .filter((id): id is string => Boolean(id));
    expect(draftItemIds).toHaveLength(2);

    const draftRes = await admin.api.post(`${ADMIN_URL}/api/v1/admin/bundles`, {
      headers: admin.headers,
      data: {
        examKindItemId: sp2eKind!.id,
        code: `BO-VIS-DRAFT-${CLEAN_RUN_SUFFIX}`,
        name: `Bộ đề nháp trực quan ${CLEAN_RUN_SUFFIX}`,
        summary: `Bản nháp kiểm thử trực quan ${CLEAN_RUN_SUFFIX}`,
        description: `Bộ đề ở trạng thái Nháp để kiểm tra danh sách admin ${CLEAN_RUN_SUFFIX}.`,
        examIds: draftItemIds,
        listPriceVnd: 79000,
        attemptsPerExam: 2,
        benefitAiExplanation: true,
        benefitStrengthWeakness: false,
        benefitPractice: true,
        resultView: "SCORE_AND_ANSWERS",
      },
    });
    expect(draftRes.status(), await draftRes.text()).toBe(201);

    // 2. Tạo và công bố 1 bộ đề HSA có bài ôn để kiểm tra badge "Bài ôn" / "Review exam" trên /exams/<reviewBundleCode>
    const hsaCandRes = await admin.api.get(`${ADMIN_URL}/api/v1/admin/bundles/candidates`, {
      params: { examKind: "HSA" },
    });
    expect(hsaCandRes.status(), await hsaCandRes.text()).toBe(200);
    const hsaCands = (await hsaCandRes.json()) as Array<{ examId: string; code: string }>;
    const revExamId = hsaCands.find((c) => c.code === reviewExam().code)?.examId;
    const stdHsaId = hsaCands.find((c) => c.code === "DS-HSA-SUBJECT-TOAN")?.examId;
    expect(revExamId, "Missing DS-HSA-REVIEW candidate").toBeTruthy();
    expect(stdHsaId, "Missing DS-HSA-SUBJECT-TOAN candidate").toBeTruthy();

    reviewBundleCode = `BO-VIS-REV-${CLEAN_RUN_SUFFIX}`;
    const createRevRes = await admin.api.post(`${ADMIN_URL}/api/v1/admin/bundles`, {
      headers: admin.headers,
      data: {
        examKindItemId: hsaKind!.id,
        code: reviewBundleCode,
        name: `Bộ đề ôn HSA trực quan ${CLEAN_RUN_SUFFIX}`,
        summary: `Bộ đề HSA gồm bài ôn và bài thi theo môn ${CLEAN_RUN_SUFFIX}`,
        description: `Bộ đề kiểm tra hiển thị nhãn Bài ôn trên trang chi tiết ${CLEAN_RUN_SUFFIX}.`,
        examIds: [revExamId!, stdHsaId!],
        listPriceVnd: 59000,
        attemptsPerExam: 2,
        benefitAiExplanation: true,
        benefitStrengthWeakness: true,
        benefitPractice: false,
        resultView: "SCORE_AND_ANSWERS",
      },
    });
    expect(createRevRes.status(), await createRevRes.text()).toBe(201);
    const createdRev = (await createRevRes.json()) as { id: string; revision: number };
    reviewBundleId = createdRev.id;

    const pubRevRes = await admin.api.post(`${ADMIN_URL}/api/v1/admin/bundles/${reviewBundleId}/publish`, {
      headers: admin.headers,
      data: { expectedRevision: createdRev.revision },
    });
    expect(pubRevRes.status(), await pubRevRes.text()).toBe(200);
  } finally {
    await admin.api.dispose();
  }

  // 3. Học viên sở hữu sẵn 1 bài trong DS-BO-SP2E-3MON -> kiểm tra thông báo cộng lượt ở detail & checkout
  const detailAccount = await createVerifiedAccount("fb.vis.d");
  detailStudent = { ...detailAccount, refreshedAt: Date.now() };
  await buyProductViaCheckout(detailStudent.cookies, examByCode("DS-SP2E-MATH-2026").productCode);

  // 4. Học viên đã mua DS-BO-SP2E-3MON + 1 bài mua lẻ -> kiểm tra đơn bộ admin & nhóm thẻ trên /home
  const homeAccount = await createVerifiedAccount("fb.vis.h");
  homeStudent = { ...homeAccount, refreshedAt: Date.now() };
  bundleOrderCode = await buyProductViaCheckout(homeStudent.cookies, sp2eBundle().code);
  await buyProductViaCheckout(homeStudent.cookies, examByCode("DS-HSA-PART-MATH").productCode);
});

test.afterAll(async () => {
  if (reviewBundleId) {
    const admin = await adminApi();
    try {
      const detailRes = await admin.api.get(`${ADMIN_URL}/api/v1/admin/bundles/${reviewBundleId}`);
      if (detailRes.ok()) {
        const detail = (await detailRes.json()) as { status: string; revision: number };
        if (detail.status === "PUBLISHED") {
          await admin.api.post(`${ADMIN_URL}/api/v1/admin/bundles/${reviewBundleId}/discontinue`, {
            headers: admin.headers,
            data: { expectedRevision: detail.revision },
          });
        }
      }
    } finally {
      await admin.api.dispose();
    }
  }
  await browser?.close();
});

for (const cell of matrixCells()) {
  test.describe(cellLabel(cell), () => {
    test("admin: bundle-admin-list, bundle-admin-form, bundle-admin-orders", async () => {
      const context = await openMatrixContext(browser, cell, ADMIN_URL);
      await loginAdmin(context, cell.lang);
      await applyMatrixCookies(context, cell, ADMIN_URL);
      const page = await context.newPage();
      const errors = attachConsoleErrorTrap(page);

      try {
        // 1. bundle-admin-list
        await page.goto(`${ADMIN_URL}/bundles`);
        await expect(
          page.getByRole("heading", { level: 1, name: cell.lang === "vi" ? "Bộ đề" : "Bundles" }),
        ).toBeVisible();
        await expect(page.getByText(sp2eBundle().code, { exact: true }).first()).toBeVisible();
        await settle(page);
        await expectMatrixClean(page, "bundle-admin-list", cell, {
          elementOverflow: { scope: "main", allowAccessibleScroller: true },
          allowLanguage: await buildAllowLanguage(page),
        });

        // 2. bundle-admin-form
        await page.goto(`${ADMIN_URL}/bundles/new`);
        await expect(
          page.getByRole("heading", { level: 1, name: cell.lang === "vi" ? "Tạo bộ đề" : "Create bundle" }),
        ).toBeVisible();
        await page.getByRole("combobox", { name: cell.lang === "vi" ? "Kỳ thi" : "Exam kind" }).click();
        await page.getByRole("option", { name: /\(SP2E\)/ }).click();
        await expect(page.getByText("DS-SP2E-MATH-2026", { exact: true }).first()).toBeVisible();
        for (const examCode of sp2eBundle().examCodes) {
          const exam = examByCode(examCode);
          await page.getByRole("checkbox", { name: new RegExp(escapeRegExp(exam.name)) }).check();
        }
        await page.getByLabel(cell.lang === "vi" ? "Giá bộ" : "Bundle price").fill("99000");
        await page.getByLabel(cell.lang === "vi" ? "Số lượt mỗi bài" : "Attempts per exam").fill("3");
        await settle(page);
        await expectMatrixClean(page, "bundle-admin-form", cell, {
          allowLanguage: await buildAllowLanguage(page),
        });

        // 3. bundle-admin-orders
        await page.goto(`${ADMIN_URL}/orders/${encodeURIComponent(bundleOrderCode)}`);
        await expect(page.getByRole("heading", { level: 1 })).toContainText(bundleOrderCode);
        await expect(
          page.getByRole("heading", {
            level: 2,
            name: cell.lang === "vi" ? "Bài thi trong bộ đề" : "Included exams in bundle",
          }),
        ).toBeVisible();
        await settle(page);
        await expectMatrixClean(page, "bundle-admin-orders", cell, {
          elementOverflow: { scope: "main", allowAccessibleScroller: true },
          allowLanguage: await buildAllowLanguage(page),
        });

        const unexpected = errors.filter((e) => !isExpectedResourceError(e)).map((e) => e.text);
        expect(unexpected, `admin ${cellLabel(cell)}: không có lỗi console`).toEqual([]);
      } finally {
        await context.close();
      }
    });

    test("student: catalog, detail, detail-review, checkout, home", async () => {
      const detailCtx = await openStudentCell(cell, detailStudent);
      try {
        const { page, errors } = detailCtx;

        // 4. bundle-student-catalog
        await page.goto(`${WEB_URL}/?format=BUNDLE#exams`);
        await expect(
          page.getByRole("heading", { level: 2, name: cell.lang === "vi" ? "Chọn bài thi" : "Choose an exam" }),
        ).toBeVisible();
        await expect(page.getByRole("article").first()).toBeVisible();
        await expect(
          page.getByRole("link", { name: cell.lang === "vi" ? "Mua bộ đề" : "Buy bundle" }).first(),
        ).toBeVisible();
        await settle(page);
        await expectMatrixClean(page, "bundle-student-catalog", cell, {
          elementOverflow: { scope: "main", allowAccessibleScroller: true },
          allowLanguage: await buildAllowLanguage(page, { includePartNames: true }),
        });

        // 5. bundle-student-detail (với học viên đã sở hữu sẵn 1 bài trong bộ)
        await page.goto(`${WEB_URL}/exams/${encodeURIComponent(sp2eBundle().code)}`);
        await expect(page.getByRole("heading", { level: 1 })).toBeVisible();
        const buyPanel = page.getByRole("complementary");
        await expect(buyPanel.getByRole("link", { name: cell.lang === "vi" ? "Mua bộ đề" : "Buy bundle" })).toBeVisible();
        await expect(buyPanel.locator('[role="status"]')).toBeVisible();
        await settle(page);
        await expectMatrixClean(page, "bundle-student-detail", cell, {
          elementOverflow: { scope: "main", allowAccessibleScroller: true },
          allowLanguage: await buildAllowLanguage(page, { includeTitle: true }),
        });

        // 6. bundle-student-detail-review (hiện nhãn Bài ôn / Review exam)
        await page.goto(`${WEB_URL}/exams/${encodeURIComponent(reviewBundleCode)}`);
        await expect(page.getByRole("heading", { level: 1 })).toBeVisible();
        await expect(
          page
            .locator('section[aria-labelledby="b-items"]')
            .getByText(cell.lang === "vi" ? "Bài ôn" : "Review exam", { exact: true }),
        ).toBeVisible();
        await settle(page);
        await expectMatrixClean(page, "bundle-student-detail-review", cell, {
          elementOverflow: { scope: "main", allowAccessibleScroller: true },
          allowLanguage: await buildAllowLanguage(page, { includeTitle: true }),
        });

        // 7. bundle-student-checkout
        await page.goto(`${WEB_URL}/checkout/${encodeURIComponent(sp2eBundle().code)}`);
        await expect(
          page.getByRole("heading", { level: 1, name: cell.lang === "vi" ? "Xác nhận mua" : "Confirm purchase" }),
        ).toBeVisible();
        await expect(page.locator("#checkout-buy-button")).toBeVisible();
        await expect(page.locator("#checkout-buy-button")).toBeEnabled();
        await settle(page);
        await expectMatrixClean(page, "bundle-student-checkout", cell, {
          elementOverflow: { scope: "main", allowAccessibleScroller: true },
          allowLanguage: await buildAllowLanguage(page),
        });

        const unexpected = errors.filter((e) => !isExpectedResourceError(e)).map((e) => e.text);
        expect(unexpected, `student detail/checkout ${cellLabel(cell)}: không có lỗi console`).toEqual([]);
      } finally {
        await detailCtx.context.close();
      }

      const homeCtx = await openStudentCell(cell, homeStudent);
      try {
        const { page, errors } = homeCtx;

        // 8. bundle-student-home (nhóm bộ đề + bài mua lẻ)
        await page.goto(`${WEB_URL}/home`);
        await expect(page.locator('section[aria-labelledby^="bundle-group-"]').first()).toBeVisible();
        await expect(
          page.getByRole("heading", {
            level: 3,
            name: cell.lang === "vi" ? "Bài mua lẻ" : "Individual exams",
          }),
        ).toBeVisible();
        await settle(page);
        await expectMatrixClean(page, "bundle-student-home", cell, {
          elementOverflow: { scope: "main", allowAccessibleScroller: true },
          allowLanguage: await buildAllowLanguage(page),
        });

        const unexpected = errors.filter((e) => !isExpectedResourceError(e)).map((e) => e.text);
        expect(unexpected, `student home ${cellLabel(cell)}: không có lỗi console`).toEqual([]);
      } finally {
        await homeCtx.context.close();
      }
    });
  });
}
