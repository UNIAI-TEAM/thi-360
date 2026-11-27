import { expect, test, type Browser, type BrowserContext, type Page } from "@playwright/test";
import { randomUUID } from "node:crypto";
import {
  advanceTestClock,
  answerCurrentUnit,
  ensureDsReady,
  getMyExams,
  getTestClockNow,
  purchaseExamViaUi,
  resetTestClock,
  startAttemptFromPrepare,
  waitForSavedStatus,
} from "./helpers/attempt-fixtures";
import { ADMIN_URL, adminApi, loginAdmin, RUN_ID, WEB_URL, type AdminApi } from "./helpers/editor-qa";
import {
  ACCOUNT_PASSWORD,
  newAccount,
  registerViaApi,
  verifyEmailViaApi,
  type E2eAccount,
  type SessionCookies,
} from "./helpers/mail";
import {
  applyMatrixCookies,
  expectMatrixClean,
  launchMatrixBrowser,
  matrixCells,
  openMatrixContext,
} from "./helpers/visual-matrix";

test.describe.configure({ mode: "serial" });
test.setTimeout(180_000);

interface CustomAdminUser {
  id: string;
  username: string;
  fullName: string;
  password: string;
  roleId: string;
  roleCode: string;
}

interface GradingQueueItem {
  itemId: string;
  attemptId: string;
  examId: string;
  examName: string;
  examKindCode: string;
  partCode: string;
  partName: string;
  displayNo: number | null;
  pointsMax: number;
  submittedAt: string | null;
  state: string;
  regrading: boolean;
  graderId: string | null;
  graderName: string | null;
  claimExpired: boolean;
  claimedAt: string | null;
  lastActivityAt: string | null;
  finalizedAt: string | null;
  earnedPoints: number | null;
}

interface GradingQueueResponse {
  items: GradingQueueItem[];
  page: number;
  size: number;
  totalItems: number;
  totalPages: number;
}

interface GradingDetailResponse {
  itemId: string;
  attemptId: string;
  examId: string;
  examName: string;
  examKindCode: string | null;
  partCode: string | null;
  partName: string | null;
  displayNo: number | null;
  submittedAt: string | null;
  regrading: boolean;
  questionId: string;
  questionVersionNo: number;
  pointsMax: number;
  state: string;
  content: string;
  passage: string | null;
  answerHtml: string | null;
  wordCount: number;
  wordLimit: number | null;
  gradingGuide: string | null;
  rubric: Array<{ id: string; description: string; points: number }>;
  sampleAnswer: string | null;
  activeGrade: {
    gradeId: string;
    graderId: string | null;
    graderName: string | null;
    points: number | null;
    comment: string | null;
    criteria?: Array<{ id: string; points: number; comment?: string | null }>;
  } | null;
}

interface OrderListResponse {
  items: Array<{
    orderId: string;
    code: string;
    buyerUsername: string;
    orderState: string;
    paymentStatus: string;
    refundStatus: string;
  }>;
}

interface OrderDetailResponse {
  code: string;
  payments: Array<{
    id: string;
    state: string;
    amountVnd: number;
    refundableAmountVnd: number;
  }>;
}

interface AttemptResultSummary {
  attemptId: string;
  state: string;
  regrading?: boolean;
  rawScore: number | null;
  displayScore: number | null;
  maxScore?: number | null;
  rawMaxScore?: number | null;
  faultFlag?: string | null;
  compensated?: boolean;
}

interface SubmittedEssayAttempt {
  attemptId: string;
  shortCode: string;
  entitlementId: string;
  itemId: string;
  examId: string;
  examName: string;
  student: E2eAccount;
  cookies: SessionCookies;
}

const SHORT_RUN = RUN_ID.toLowerCase().replace(/[^a-z0-9]/g, "").slice(-6);
let seq = 0;

function nextTag(): string {
  seq += 1;
  return `${SHORT_RUN}${seq.toString(36)}`;
}

function makeStaffUsername(label: string): string {
  const clean = label.toLowerCase().replace(/[^a-z0-9]/g, "").slice(0, 12);
  return `fa4.${clean}.${nextTag()}`.slice(0, 29);
}

async function registerAnonStudent(tag: string): Promise<{ account: E2eAccount; cookies: SessionCookies }> {
  const base = newAccount(`fa4s.${tag}`);
  const account: E2eAccount = {
    ...base,
    username: makeStaffUsername(`s${tag}`),
    fullName: `FA4 HocVien AnDanh ${tag} ${SHORT_RUN}`,
  };
  const cookies = await registerViaApi(account);
  await verifyEmailViaApi(cookies, account);
  return { account, cookies };
}

async function createCustomAdmin(
  admin: AdminApi,
  label: string,
  permissionCodes: string[],
  fullNameOverride?: string,
): Promise<CustomAdminUser> {
  const uniq = nextTag();
  const roleCode = `FA4-ROLE-${label.toUpperCase().replace(/[^A-Z0-9]/g, "")}-${uniq}`.slice(0, 60);
  const roleRes = await admin.api.post(`${ADMIN_URL}/api/v1/admin/roles`, {
    headers: { ...admin.headers, "idempotency-key": randomUUID() },
    data: {
      code: roleCode,
      name: `FA4-Role ${label} ${uniq}`,
      description: `FA-4 E2E role ${label}`,
      access: "MANAGE",
      status: "ACTIVE",
      permissionCodes,
    },
  });
  expect(roleRes.status(), `POST /admin/roles (${roleCode}): ${await roleRes.text()}`).toBe(201);
  const roleJson = (await roleRes.json()) as { id: string; code: string };

  const username = makeStaffUsername(label);
  const fullName = fullNameOverride ?? `FA4-${label}-${uniq}`;
  const userRes = await admin.api.post(`${ADMIN_URL}/api/v1/admin/users`, {
    headers: { ...admin.headers, "idempotency-key": randomUUID() },
    data: {
      username,
      password: ACCOUNT_PASSWORD,
      fullName,
      email: `${username}@e2e.thi360.test`,
      roleIds: [roleJson.id],
      mustChangePassword: false,
    },
  });
  expect(userRes.status(), `POST /admin/users (${username}): ${await userRes.text()}`).toBe(201);
  const userJson = (await userRes.json()) as { id: string };

  return {
    id: userJson.id,
    username,
    fullName,
    password: ACCOUNT_PASSWORD,
    roleId: roleJson.id,
    roleCode: roleJson.code,
  };
}

async function loginStaffContext(
  context: BrowserContext,
  username: string,
  password: string,
  locale: "vi" | "en" = "vi",
): Promise<void> {
  const res = await context.request.post(`${WEB_URL}/api/v1/auth/login`, {
    headers: { "Content-Type": "application/json", origin: WEB_URL },
    data: { identifier: username, password },
  });
  expect(res.ok(), `Staff login (${username}): ${res.status()} ${await res.text()}`).toBe(true);
  await context.addCookies([
    { name: "thi360_locale", value: locale, url: WEB_URL },
    { name: "thi360_locale", value: locale, url: ADMIN_URL },
  ]);
}

async function staffApiFromContext(context: BrowserContext): Promise<{ headers: Record<string, string> }> {
  const cookies = await context.cookies();
  const xsrf = cookies.find((c) => c.name === "thi360_xsrf")?.value ?? "";
  return {
    headers: {
      origin: ADMIN_URL,
      ...(xsrf ? { "x-xsrf-token": xsrf } : {}),
    },
  };
}

async function submitBcaMiniEssayAttempt(
  browser: Browser,
  admin: AdminApi,
  tag: string,
  essayText: string,
): Promise<SubmittedEssayAttempt> {
  const { account, cookies } = await registerAnonStudent(tag);
  const studentCtx = await browser.newContext();
  let attemptId = "";
  let entitlementId = "";
  try {
    await studentCtx.addCookies(cookies);
    await studentCtx.addCookies([{ name: "thi360_locale", value: "vi", url: WEB_URL }]);
    const page = await studentCtx.newPage();

    const purchased = await purchaseExamViaUi(page, "DS-BCA-FULL-MINI");
    entitlementId = purchased.entitlementId;
    attemptId = await startAttemptFromPrepare(page, entitlementId, { preChoiceCode: "CA1" });

    const finishPartBtn = page.getByRole("button", { name: "Hoàn thành phần" });
    const submitBtn = page.getByRole("button", { name: "Nộp bài" });

    // Phần 1 (ESSAY)
    await answerCurrentUnit(page, essayText);
    await waitForSavedStatus(page);
    await finishPartBtn.click();
    const finishDialog1 = page.getByRole("dialog", { name: "Hoàn thành phần?" });
    await expect(finishDialog1).toBeVisible();
    await finishDialog1.getByRole("button", { name: "Hoàn thành phần" }).click();
    await expect(finishDialog1).toBeHidden({ timeout: 20_000 });

    // Các phần tiếp theo cho tới khi chuyển sang bước Nộp bài
    await expect(finishPartBtn.or(submitBtn)).toBeVisible({ timeout: 30_000 });
    while (await finishPartBtn.isVisible()) {
      const fieldset = page.locator("main fieldset[data-attempt-question-fieldset]");
      await expect(fieldset).toBeVisible({ timeout: 30_000 });
      await expect(fieldset).not.toHaveAttribute("disabled", "", { timeout: 30_000 });
      await answerCurrentUnit(page, "1");
      await waitForSavedStatus(page);
      await finishPartBtn.click();
      const finishDialog = page.getByRole("dialog", { name: "Hoàn thành phần?" });
      await expect(finishDialog).toBeVisible();
      await finishDialog.getByRole("button", { name: "Hoàn thành phần" }).click();
      await expect(finishDialog).toBeHidden({ timeout: 20_000 });
      await expect(finishPartBtn.or(submitBtn)).toBeVisible({ timeout: 30_000 });
    }

    // Phần cuối (SINGLE_CHOICE CA1) -> Nộp bài -> Lượt thi đã đóng -> Xem kết quả
    const lastFieldset = page.locator("main fieldset[data-attempt-question-fieldset]");
    await expect(lastFieldset).toBeVisible({ timeout: 30_000 });
    await expect(lastFieldset).not.toHaveAttribute("disabled", "", { timeout: 30_000 });
    await answerCurrentUnit(page, "1");
    await waitForSavedStatus(page);
    await submitBtn.click();
    const submitDialog = page.getByRole("dialog", { name: "Nộp bài?" });
    await expect(submitDialog).toBeVisible();
    await submitDialog.getByRole("button", { name: "Nộp bài" }).click();
    await expect(submitDialog).toBeHidden({ timeout: 20_000 });

    await expect(page.getByRole("heading", { name: "Lượt thi đã đóng" })).toBeVisible({
      timeout: 30_000,
    });
    await page.getByRole("link", { name: "Xem kết quả" }).click();
    await expect(page).toHaveURL(new RegExp(`/attempts/${attemptId}/result`), { timeout: 30_000 });

    await expect
      .poll(
        async () => {
          const r = await studentCtx.request.get(`${WEB_URL}/api/v1/me/attempts/${attemptId}/result`);
          if (!r.ok()) return "";
          const data = (await r.json()) as AttemptResultSummary;
          return data.state;
        },
        { timeout: 30_000, message: `Đợi lượt ${attemptId} về PENDING_REVIEW` },
      )
      .toBe("PENDING_REVIEW");
  } finally {
    await studentCtx.close();
  }

  let matched: GradingQueueItem | undefined;
  await expect
    .poll(
      async () => {
        const res = await admin.api.get(`${ADMIN_URL}/api/v1/admin/grading/items?state=WAITING&size=50`);
        if (!res.ok()) return undefined;
        const queue = (await res.json()) as GradingQueueResponse;
        matched = queue.items.find((item) => item.attemptId === attemptId);
        return matched?.itemId;
      },
      { timeout: 20_000, message: `Tìm câu tự luận của lượt ${attemptId} trong hàng chờ chấm` },
    )
    .toBeTruthy();

  expect(matched).toBeDefined();
  return {
    attemptId,
    shortCode: attemptId.slice(0, 8).toUpperCase(),
    entitlementId,
    itemId: matched!.itemId,
    examId: matched!.examId,
    examName: matched!.examName,
    student: account,
    cookies,
  };
}

async function waitAdminSettled(page: Page): Promise<void> {
  await expect(page.locator('[aria-busy="true"]')).toHaveCount(0, { timeout: 20_000 });
  await expect(page.locator(".animate-pulse")).toHaveCount(0, { timeout: 20_000 });
  await page.evaluate(() => document.fonts.ready);
}

async function testClockDayIso(): Promise<string> {
  const iso = await getTestClockNow();
  return new Intl.DateTimeFormat("en-CA", { timeZone: "Asia/Ho_Chi_Minh" }).format(new Date(iso));
}

test.describe("FA-4 Hàng chờ chấm tự luận & vận hành lượt thi", () => {
  test.beforeAll(async ({ browser }) => {
    await ensureDsReady(browser);
  });

  test("chấm ẩn danh: hàng chờ chấm, bộ lọc và chi tiết không lộ thông tin học viên (AC-1, AC-2)", async ({
    browser,
  }) => {
    const admin = await adminApi();
    const markerEssay = `Bai lam tu luan an danh FA4 ${RUN_ID} lap luan mach lac va dan chung ro rang.`;
    const submitted = await submitBcaMiniEssayAttempt(browser, admin, "anon", markerEssay);

    const queueApiRes = await admin.api.get(`${ADMIN_URL}/api/v1/admin/grading/items?state=WAITING&size=50`);
    expect(queueApiRes.status()).toBe(200);
    const queueRaw = await queueApiRes.text();
    expect(queueRaw).not.toContain(submitted.student.username);
    expect(queueRaw).not.toContain(submitted.student.fullName);

    const adminCtx = await browser.newContext();
    try {
      await loginAdmin(adminCtx, "vi");
      const page = await adminCtx.newPage();

      await page.goto(`${ADMIN_URL}/grading`);
      await expect(page.getByRole("heading", { level: 1, name: "Chấm tự luận" })).toBeVisible();

      const table = page.getByRole("table", { name: "Danh sách câu tự luận cần chấm" });
      await expect(table).toBeVisible();
      const targetRow = table.getByRole("link", {
        name: new RegExp(`#${submitted.shortCode}.*Câu`),
      });
      await expect(targetRow).toBeVisible();

      // Ẩn danh ở danh sách
      await expect(page.locator("main")).not.toContainText(submitted.student.username);
      await expect(page.locator("main")).not.toContainText(submitted.student.fullName);

      // Lọc theo trạng thái "Đã chấm" -> ẩn item WAITING; "Chờ chấm" -> hiện lại
      const statusGroup = page.getByRole("group", { name: "Trạng thái" });
      await statusGroup.locator("label").filter({ hasText: /^Đã chấm/ }).click();
      await expect(statusGroup.getByRole("radio", { name: /^Đã chấm/ })).toBeChecked();
      await expect(targetRow).toHaveCount(0);

      await page.getByRole("button", { name: "Xóa tất cả bộ lọc" }).first().click();
      await expect(targetRow).toBeVisible();

      const today = await testClockDayIso();
      await statusGroup.locator("label").filter({ hasText: /^Chờ chấm/ }).click();
      await expect(statusGroup.getByRole("radio", { name: /^Chờ chấm/ })).toBeChecked();
      const [filteredRes] = await Promise.all([
        page.waitForResponse((r) => {
          if (r.request().method() !== "GET") return false;
          const url = new URL(r.url());
          return (
            url.pathname === "/api/v1/admin/grading/items" &&
            url.searchParams.get("state") === "WAITING" &&
            url.searchParams.get("exam") === "BCA" &&
            url.searchParams.get("from") === today &&
            url.searchParams.get("to") === today
          );
        }),
        (async () => {
          await page.getByLabel("Bài thi").fill("BCA");
          await page.getByLabel("Từ ngày").fill(today);
          await page.getByLabel("Đến ngày").fill(today);
        })(),
      ]);
      expect(filteredRes.ok()).toBe(true);
      await expect(page).toHaveURL(
        (url) =>
          url.pathname.endsWith("/grading") &&
          url.searchParams.get("state") === "WAITING" &&
          url.searchParams.get("exam") === "BCA" &&
          url.searchParams.get("from") === today &&
          url.searchParams.get("to") === today,
      );
      await expect(table).not.toHaveAttribute("aria-busy", "true");
      await expect(targetRow).toBeVisible();
      await expect(targetRow).toHaveAttribute("href", new RegExp(`/grading/${submitted.itemId}$`));

      // Mở chi tiết chấm và xác nhận nội dung + ẩn danh
      await targetRow.click();
      await expect(page).toHaveURL(new RegExp(`/grading/${submitted.itemId}`));
      await expect(
        page.getByRole("heading", { level: 1, name: new RegExp(`#${submitted.shortCode}`) }),
      ).toBeVisible();
      await expect(page.locator("main")).toContainText(markerEssay);
      await expect(page.locator("main")).not.toContainText(submitted.student.username);
      await expect(page.locator("main")).not.toContainText(submitted.student.fullName);
    } finally {
      await adminCtx.close();
    }
  });

  test("hai người chấm tranh khóa và nhận lại sau 30 phút (AC-3, AC-4)", async ({ browser }) => {
    const admin = await adminApi();
    const submitted = await submitBcaMiniEssayAttempt(
      browser,
      admin,
      "lock",
      `Bai luan kiem tra tranh khoa hai nguoi cham FA4 ${RUN_ID}.`,
    );

    const graderA = await createCustomAdmin(admin, "GraderA", ["GRADING.VIEW", "GRADING.MODIFY"]);
    const graderB = await createCustomAdmin(admin, "GraderB", ["GRADING.VIEW", "GRADING.MODIFY"]);

    const ctxA = await browser.newContext();
    const ctxB = await browser.newContext();
    try {
      await loginStaffContext(ctxA, graderA.username, graderA.password, "vi");
      await loginStaffContext(ctxB, graderB.username, graderB.password, "vi");

      const pageA = await ctxA.newPage();
      const pageB = await ctxB.newPage();

      // Người chấm A nhận câu thành công
      await pageA.goto(`${ADMIN_URL}/grading/${submitted.itemId}`);
      await pageA.getByRole("button", { name: "Nhận câu để chấm" }).click();
      await expect(pageA.getByText("Đã nhận câu để chấm.").first()).toBeVisible();
      await expect(pageA.getByLabel("Tổng điểm câu")).toBeVisible();

      // Người chấm B mở cùng câu -> bị khóa bởi người chấm A, không có nút chấm/lưu/hoàn tất
      await pageB.goto(`${ADMIN_URL}/grading/${submitted.itemId}`);
      await expect(pageB.getByRole("heading", { name: "Đang có người chấm khác giữ" })).toBeVisible();
      await expect(pageB.locator("main")).toContainText(graderA.fullName);
      await expect(pageB.getByRole("button", { name: "Nhận câu để chấm" })).toHaveCount(0);
      await expect(pageB.getByRole("button", { name: "Lưu nháp" })).toHaveCount(0);
      await expect(pageB.getByRole("button", { name: "Hoàn tất chấm" })).toHaveCount(0);
      await expect(pageB.getByLabel("Tổng điểm câu")).toHaveCount(0);

      // Gọi API claim trực tiếp dưới phiên B -> 409 GRADING_CLAIMED
      const bHeaders = await staffApiFromContext(ctxB);
      const conflictRes = await ctxB.request.post(`${ADMIN_URL}/api/v1/admin/grading/items/${submitted.itemId}/claim`, {
        headers: { ...bHeaders.headers, "idempotency-key": randomUUID() },
      });
      expect(conflictRes.status()).toBe(409);
      const conflictJson = (await conflictRes.json()) as { code?: string };
      expect(conflictJson.code).toBe("GRADING_CLAIMED");

      // Tua đồng hồ +31 phút -> hết hạn giữ 30 phút -> B tiếp quản thành công
      try {
        await advanceTestClock(31 * 60);

        await pageB.reload();
        await expect(pageB.getByRole("heading", { name: "Tiếp quản câu chấm" })).toBeVisible();
        await pageB.getByRole("button", { name: "Tiếp quản chấm" }).click();
        await expect(pageB.getByLabel("Tổng điểm câu")).toBeVisible();
        await expect(pageB.getByRole("button", { name: "Hoàn tất chấm" })).toBeVisible();

        // Người chấm A tải lại -> thấy B đang giữ khóa mới
        await pageA.reload();
        await expect(pageA.getByRole("heading", { name: "Đang có người chấm khác giữ" })).toBeVisible();
        await expect(pageA.locator("main")).toContainText(graderB.fullName);
      } finally {
        await resetTestClock();
      }
    } finally {
      await ctxA.close();
      await ctxB.close();
    }
  });

  test("tự luận tới điểm tổng và chấm lại tới kết quả học viên (AC-5, AC-10)", async ({ browser }) => {
    const admin = await adminApi();
    const essayContent = "Mot hai ba bon nam sau bay tam chin muoi tu tu luan FA4.";
    const submitted = await submitBcaMiniEssayAttempt(browser, admin, "score", essayContent);
    const grader = await createCustomAdmin(admin, "GraderFlow", ["GRADING.VIEW", "GRADING.MODIFY"]);

    const graderCtx = await browser.newContext();
    const studentCtx = await browser.newContext();
    try {
      await loginStaffContext(graderCtx, grader.username, grader.password, "vi");
      await studentCtx.addCookies(submitted.cookies);
      await studentCtx.addCookies([{ name: "thi360_locale", value: "vi", url: WEB_URL }]);

      const graderPage = await graderCtx.newPage();
      const studentPage = await studentCtx.newPage();

      // Trước khi chấm xong: học viên thấy trạng thái Chờ chấm
      await studentPage.goto(`${WEB_URL}/attempts/${submitted.attemptId}/result`);
      await expect(studentPage.getByText("Chờ chấm").first()).toBeVisible();

      // Người chấm nhận câu, kiểm tra đếm chữ, lưu nháp và reload kiểm tra giữ dữ liệu
      await graderPage.goto(`${ADMIN_URL}/grading/${submitted.itemId}`);
      await graderPage.getByRole("button", { name: "Nhận câu để chấm" }).click();
      await expect(graderPage.getByTestId("essay-word-count")).toContainText(/\d+.*chữ/);

      const detailRes = await admin.api.get(`${ADMIN_URL}/api/v1/admin/grading/items/${submitted.itemId}`);
      expect(detailRes.status()).toBe(200);
      const detail = (await detailRes.json()) as GradingDetailResponse;
      const toViInput = (value: number): string => String(Number(value.toFixed(2))).replace(".", ",");
      const rubric = detail.rubric ?? [];
      let firstScore: number;
      let secondScore: number;

      const pointsInput = graderPage.getByLabel("Tổng điểm câu");
      const commentInput = graderPage.getByLabel("Nhận xét");
      if (rubric.length > 0) {
        const halfValues = rubric.map((c) => Number(((c.points ?? 0) / 2).toFixed(2)));
        firstScore = Number(halfValues.reduce((sum, val) => sum + val, 0).toFixed(2));
        secondScore = Number(rubric.reduce((sum, c) => sum + (c.points ?? 0), 0).toFixed(2));
        for (let i = 0; i < rubric.length; i += 1) {
          await graderPage.getByLabel(new RegExp(`^Tiêu chí ${i + 1}\\.`)).fill(toViInput(halfValues[i]!));
        }
        await pointsInput.fill(toViInput(firstScore));
      } else {
        firstScore = Number((detail.pointsMax / 2).toFixed(2));
        secondScore = detail.pointsMax;
        await pointsInput.fill(toViInput(firstScore));
      }

      await commentInput.fill("Nhan xet lan cham 1 FA4");
      await graderPage.getByRole("button", { name: "Lưu nháp" }).click();
      await expect(graderPage.getByText("Đã lưu nháp điểm chấm.").first()).toBeVisible();

      await graderPage.reload();
      await expect(graderPage.getByLabel("Tổng điểm câu")).toHaveValue(toViInput(firstScore));
      await expect(graderPage.getByLabel("Nhận xét")).toHaveValue("Nhan xet lan cham 1 FA4");

      // Hoàn tất chấm
      await graderPage.getByRole("button", { name: "Hoàn tất chấm" }).click();
      const finalizeConfirm = graderPage.getByRole("region", { name: "Hoàn tất chấm câu tự luận?" });
      await expect(finalizeConfirm).toBeVisible();
      await finalizeConfirm.getByRole("button", { name: "Xác nhận hoàn tất" }).click();
      await expect(graderPage.getByRole("heading", { name: "Đã chốt điểm" })).toBeVisible();

      // Học viên thấy SCORED và điểm tổng đã tính
      await studentPage.reload();
      await expect(studentPage.getByText("Đã chấm").first()).toBeVisible();
      const scoredRes1 = await studentCtx.request.get(
        `${WEB_URL}/api/v1/me/attempts/${submitted.attemptId}/result`,
      );
      expect(scoredRes1.status()).toBe(200);
      const result1 = (await scoredRes1.json()) as AttemptResultSummary;
      expect(result1.state).toBe("SCORED");
      const scoreBeforeRegrade = result1.displayScore ?? result1.rawScore ?? 0;

      // Mở lại để chấm lại (kiểm tra validation lý do rỗng trước khi nhập lý do hợp lệ)
      await graderPage.getByRole("button", { name: "Mở lại để chấm lại" }).click();
      const reopenDialog = graderPage.getByRole("dialog", { name: "Mở lại bản chấm?" });
      await expect(reopenDialog).toBeVisible();
      await reopenDialog.getByRole("button", { name: "Xác nhận mở lại" }).click();
      await expect(reopenDialog.getByLabel(/Lý do mở lại/)).toHaveAttribute("aria-invalid", "true");

      await reopenDialog.getByLabel(/Lý do mở lại/).fill("Can ra soat lai dan chung cua thi sinh");
      await reopenDialog.getByRole("button", { name: "Xác nhận mở lại" }).click();
      await expect(graderPage.getByLabel("Tổng điểm câu")).toBeVisible();

      // Học viên thấy trạng thái "Đang chấm lại", vẫn giữ điểm cũ + ghi chú đang chấm lại ở /result và /history
      await studentPage.reload();
      await expect(studentPage.getByText("Đang chấm lại").first()).toBeVisible();
      await expect(
        studentPage.getByText(
          "Người chấm đang chấm lại câu tự luận. Điểm bên trên là điểm trước khi chấm lại và có thể đổi khi chấm xong.",
        ),
      ).toBeVisible();
      const regradingRes = await studentCtx.request.get(
        `${WEB_URL}/api/v1/me/attempts/${submitted.attemptId}/result`,
      );
      const resultRegrading = (await regradingRes.json()) as AttemptResultSummary;
      expect(resultRegrading.state).toBe("PENDING_REVIEW");
      expect(resultRegrading.regrading).toBe(true);
      expect(resultRegrading.displayScore ?? resultRegrading.rawScore).toBe(scoreBeforeRegrade);

      await studentPage.goto(`${WEB_URL}/history`);
      await expect(studentPage.getByText("Đang chấm lại").first()).toBeVisible();
      await expect(studentPage.getByText("Điểm trước khi chấm lại").first()).toBeVisible();

      // Người chấm đổi điểm lên mức mới rồi chốt lại
      if (rubric.length > 0) {
        for (let i = 0; i < rubric.length; i += 1) {
          await graderPage
            .getByLabel(new RegExp(`^Tiêu chí ${i + 1}\\.`))
            .fill(toViInput(rubric[i]!.points ?? 0));
        }
      }
      await graderPage.getByLabel("Tổng điểm câu").fill(toViInput(secondScore));
      await graderPage.getByLabel("Nhận xét").fill("Da cham lai va nang diem FA4");
      await graderPage.getByRole("button", { name: "Hoàn tất chấm" }).click();
      await graderPage
        .getByRole("region", { name: "Hoàn tất chấm câu tự luận?" })
        .getByRole("button", { name: "Xác nhận hoàn tất" })
        .click();
      await expect(graderPage.getByRole("heading", { name: "Đã chốt điểm" })).toBeVisible();

      // Học viên thấy điểm mới ở trạng thái Đã chấm
      await studentPage.goto(`${WEB_URL}/attempts/${submitted.attemptId}/result`);
      await expect(studentPage.getByText("Đã chấm").first()).toBeVisible();
      const scoredRes2 = await studentCtx.request.get(
        `${WEB_URL}/api/v1/me/attempts/${submitted.attemptId}/result`,
      );
      const result2 = (await scoredRes2.json()) as AttemptResultSummary;
      expect(result2.state).toBe("SCORED");
      expect(result2.regrading).toBeFalsy();
      expect(result2.displayScore ?? result2.rawScore).toBeGreaterThan(scoreBeforeRegrade);
    } finally {
      await graderCtx.close();
      await studentCtx.close();
    }
  });

  test("đánh dấu hỏng rồi cấp bù, giữ lũy đẳng và chặn 409 khi đã hoàn tiền (AC-14, AC-15)", async ({
    browser,
  }) => {
    const admin = await adminApi();

    // Subflow A: Đánh dấu hỏng lượt đang làm + cấp bù 1 lượt + kiểm tra học viên thấy banner F20
    const stuA = await registerAnonStudent("fltA");
    const ctxStuA = await browser.newContext();
    let attemptA = "";
    try {
      await ctxStuA.addCookies(stuA.cookies);
      await ctxStuA.addCookies([{ name: "thi360_locale", value: "vi", url: WEB_URL }]);
      const pageStuA = await ctxStuA.newPage();

      const purchasedA = await purchaseExamViaUi(pageStuA, "DS-BCA-FULL-MINI");
      attemptA = await startAttemptFromPrepare(pageStuA, purchasedA.entitlementId, { preChoiceCode: "CA1" });
      await pageStuA.waitForLoadState("networkidle");

      const myExamsBefore = await getMyExams(pageStuA);
      const entryBefore = myExamsBefore.find((e) => e.entitlementId === purchasedA.entitlementId);
      const remainingBefore = entryBefore?.attemptsLeft ?? 0;

      const opsCtx = await browser.newContext();
      try {
        await loginAdmin(opsCtx, "vi");
        const opsPage = await opsCtx.newPage();
        await opsPage.goto(`${ADMIN_URL}/attempts/${attemptA}`);

        const codeA = attemptA.slice(0, 8).toUpperCase();
        await expect(opsPage.getByRole("heading", { level: 1, name: new RegExp(`Lượt ${codeA}`) })).toBeVisible();
        await expect(opsPage.getByRole("button", { name: "Cấp bù 1 lượt" })).toHaveCount(0);

        // Kiểm tra validation lý do rỗng khi Đánh dấu hỏng
        await opsPage.getByRole("button", { name: "Đánh dấu hỏng" }).click();
        const faultDialog = opsPage.getByRole("dialog");
        await expect(faultDialog).toBeVisible();
        await faultDialog.getByRole("button", { name: /Đánh dấu hỏng/ }).click();
        await expect(faultDialog.getByText("Nhập lý do đánh dấu lượt hỏng.")).toBeVisible();

        await faultDialog.getByLabel(/Lý do/).fill("Loi ket noi may chu khi thi FA4");
        await faultDialog.getByRole("button", { name: /Đánh dấu hỏng/ }).click();
        await expect(
          opsPage.getByRole("alert").filter({ hasText: "Lượt đã được đánh dấu hỏng do lỗi hệ thống." }),
        ).toBeVisible();
        await expect(opsPage.getByRole("button", { name: "Cấp bù 1 lượt" })).toBeVisible();

        // Cấp bù 1 lượt (bắt Idempotency-Key + kiểm tra lý do rỗng)
        const compensateHeaders: Array<string | undefined> = [];
        opsPage.on("request", (req) => {
          if (req.method() === "POST" && req.url().includes(`/attempts/${attemptA}/compensate`)) {
            compensateHeaders.push(req.headers()["idempotency-key"]);
          }
        });

        await opsPage.getByRole("button", { name: "Cấp bù 1 lượt" }).click();
        const compDialog = opsPage.getByRole("dialog");
        await expect(compDialog).toBeVisible();
        await compDialog.getByRole("button", { name: "Cấp bù 1 lượt" }).click();
        await expect(compDialog.getByText("Nhập lý do cấp bù lượt.")).toBeVisible();

        await compDialog.getByLabel(/Lý do/).fill("Cap bu 1 luot do loi he thong FA4");
        await compDialog.getByRole("button", { name: "Cấp bù 1 lượt" }).click();
        await expect(
          opsPage.getByRole("alert").filter({ hasText: "Lượt đã đánh dấu hỏng và đã cấp bù 1 lượt." }),
        ).toBeVisible();
        await expect(opsPage.getByRole("complementary").getByText("Đã cấp bù 1 lượt").first()).toBeVisible();
        await expect(opsPage.getByRole("button", { name: "Cấp bù 1 lượt" })).toHaveCount(0);
        expect(compensateHeaders[0]).toBeTruthy();

        // Kiểm tra lũy đẳng khi gọi lặp lại compensate API không cộng thêm lượt
        const retryComp = await admin.api.post(`${ADMIN_URL}/api/v1/admin/attempts/${attemptA}/compensate`, {
          headers: { ...admin.headers, "idempotency-key": randomUUID() },
          data: { reason: "Thu goi lap lai FA4" },
        });
        expect(retryComp.status()).toBe(409);
      } finally {
        await opsCtx.close();
      }

      // Kiểm tra lượt còn lại của học viên tăng đúng +1 và hiện banner F20 ở /result + /history
      await expect
        .poll(async () => {
          const items = await getMyExams(pageStuA);
          return items.find((e) => e.entitlementId === purchasedA.entitlementId)?.attemptsLeft ?? -1;
        })
        .toBe(remainingBefore + 1);

      await pageStuA.goto(`${WEB_URL}/attempts/${attemptA}/result`);
      await expect(pageStuA.getByText("Lượt bị ảnh hưởng bởi lỗi hệ thống, đã cấp bù 1 lượt")).toBeVisible();

      await pageStuA.goto(`${WEB_URL}/history`);
      await expect(
        pageStuA.getByText("Lượt bị ảnh hưởng bởi lỗi hệ thống, đã cấp bù 1 lượt").first(),
      ).toBeVisible();
    } finally {
      await ctxStuA.close();
    }

    // Subflow B: Đơn hàng đã hoàn tiền hết -> GRANT_REFUNDED 409 khi bấm Cấp bù 1 lượt
    const stuB = await registerAnonStudent("fltB");
    const ctxStuB = await browser.newContext();
    let attemptB = "";
    try {
      await ctxStuB.addCookies(stuB.cookies);
      await ctxStuB.addCookies([{ name: "thi360_locale", value: "vi", url: WEB_URL }]);
      const pageStuB = await ctxStuB.newPage();
      const purchasedB = await purchaseExamViaUi(pageStuB, "DS-BCA-FULL-MINI");
      attemptB = await startAttemptFromPrepare(pageStuB, purchasedB.entitlementId, { preChoiceCode: "CA1" });
    } finally {
      await ctxStuB.close();
    }

    // Đánh dấu hỏng lượt B rồi hoàn tiền đơn hàng của học viên B
    const faultRes = await admin.api.post(`${ADMIN_URL}/api/v1/admin/attempts/${attemptB}/fault`, {
      headers: { ...admin.headers, "idempotency-key": randomUUID() },
      data: { reason: "Loi he thong truoc khi hoan tien FA4" },
    });
    expect(faultRes.status(), await faultRes.text()).toBe(200);

    const ordersRes = await admin.api.get(
      `${ADMIN_URL}/api/v1/admin/orders?q=${encodeURIComponent(stuB.account.username)}&size=10`,
    );
    expect(ordersRes.status()).toBe(200);
    const ordersJson = (await ordersRes.json()) as OrderListResponse;
    const order = ordersJson.items.find((o) => o.buyerUsername === stuB.account.username);
    expect(order, "Phải tìm thấy đơn hàng của học viên B").toBeDefined();

    const detailRes = await admin.api.get(`${ADMIN_URL}/api/v1/admin/orders/${order!.code}`);
    expect(detailRes.status()).toBe(200);
    const orderDetail = (await detailRes.json()) as OrderDetailResponse;
    const paidPayment = orderDetail.payments.find((p) => p.state === "SUCCEEDED" && p.refundableAmountVnd > 0);
    expect(paidPayment, "Phải có khoản thanh toán SUCCEEDED").toBeDefined();

    const refundRes = await admin.api.post(
      `${ADMIN_URL}/api/v1/admin/orders/${order!.code}/payments/${paidPayment!.id}/refunds`,
      {
        headers: { ...admin.headers, "idempotency-key": randomUUID() },
        data: {
          amountVnd: paidPayment!.refundableAmountVnd,
          kind: "API",
          reason: "Hoan tien toan bo truoc khi kiem tra cap bu FA4",
          verifiedSystemError: true,
        },
      },
    );
    expect(refundRes.status(), await refundRes.text()).toBe(201);
    const refundBody = (await refundRes.json()) as { state?: string };
    expect(refundBody.state).toBe("SUCCEEDED");

    const opsCtxB = await browser.newContext();
    try {
      await loginAdmin(opsCtxB, "vi");
      const pageB = await opsCtxB.newPage();
      await pageB.goto(`${ADMIN_URL}/attempts/${attemptB}`);
      await pageB.getByRole("button", { name: "Cấp bù 1 lượt" }).click();
      const dialog = pageB.getByRole("dialog");
      await dialog.getByLabel(/Lý do/).fill("Thu cap bu don da hoan tien FA4");

      const [compResp] = await Promise.all([
        pageB.waitForResponse(
          (r) => r.url().includes(`/attempts/${attemptB}/compensate`) && r.request().method() === "POST",
        ),
        dialog.getByRole("button", { name: "Cấp bù 1 lượt" }).click(),
      ]);
      expect(compResp.status()).toBe(409);
      const errBody = (await compResp.json()) as { code?: string };
      expect(errBody.code).toBe("GRANT_REFUNDED");
      await expect(
        dialog.getByText("Không cấp bù được: lần cấp mà lượt này đã dùng đã được hoàn tiền hết."),
      ).toBeVisible();
    } finally {
      await opsCtxB.close();
    }
  });

  test("vai trò tùy chỉnh chấm: chỉ xem vs không có quyền (AC-9, AC-31)", async ({ browser }) => {
    const admin = await adminApi();
    const submitted = await submitBcaMiniEssayAttempt(
      browser,
      admin,
      "rbgrd",
      `Bai luan kiem tra RBAC cham tu luan FA4 ${RUN_ID}.`,
    );

    const roGrader = await createCustomAdmin(admin, "ROGrader", ["GRADING.VIEW"]);
    const noGrading = await createCustomAdmin(admin, "NoGrading", ["EXAM.VIEW"]);

    const roCtx = await browser.newContext();
    const noCtx = await browser.newContext();
    try {
      await loginStaffContext(roCtx, roGrader.username, roGrader.password, "vi");
      await loginStaffContext(noCtx, noGrading.username, noGrading.password, "vi");

      // 1) Tài khoản chỉ có GRADING.VIEW
      const roPage = await roCtx.newPage();
      await roPage.goto(`${ADMIN_URL}/grading`);
      const nav = roPage.getByRole("navigation", { name: "Menu chính" });
      await expect(nav.getByRole("link", { name: "Chấm tự luận" })).toBeVisible();
      await expect(nav.getByRole("link", { name: "Lượt thi" })).toHaveCount(0);

      await roPage.goto(`${ADMIN_URL}/grading/${submitted.itemId}`);
      await expect(roPage.getByRole("heading", { name: "Chế độ chỉ xem" })).toBeVisible();
      await expect(roPage.getByRole("button", { name: "Nhận câu để chấm" })).toHaveCount(0);
      await expect(roPage.getByRole("button", { name: "Lưu nháp" })).toHaveCount(0);
      await expect(roPage.getByRole("button", { name: "Hoàn tất chấm" })).toHaveCount(0);
      await expect(roPage.getByRole("button", { name: "Mở lại để chấm lại" })).toHaveCount(0);

      const roHeaders = await staffApiFromContext(roCtx);
      const claim403 = await roCtx.request.post(`${ADMIN_URL}/api/v1/admin/grading/items/${submitted.itemId}/claim`, {
        headers: { ...roHeaders.headers, "idempotency-key": randomUUID() },
      });
      expect(claim403.status()).toBe(403);

      await roPage.goto(`${ADMIN_URL}/attempts`);
      await expect(roPage.getByText("Không có quyền", { exact: true })).toBeVisible();
      await expect(roPage.getByRole("heading", { name: "Bạn không có quyền vào trang này" })).toBeVisible();

      // 2) Tài khoản không có GRADING.VIEW / ATTEMPT.VIEW
      const noPage = await noCtx.newPage();
      await noPage.goto(`${ADMIN_URL}/grading`);
      await expect(noPage.getByRole("heading", { name: "Bạn không có quyền vào trang này" })).toBeVisible();
      const noNav = noPage.getByRole("navigation", { name: "Menu chính" });
      await expect(noNav.getByRole("link", { name: "Chấm tự luận" })).toHaveCount(0);
      await expect(noNav.getByRole("link", { name: "Lượt thi" })).toHaveCount(0);

      await noPage.goto(`${ADMIN_URL}/grading/${submitted.itemId}`);
      await expect(noPage.getByRole("heading", { name: "Bạn không có quyền vào trang này" })).toBeVisible();
    } finally {
      await roCtx.close();
      await noCtx.close();
    }
  });

  test("vai trò tùy chỉnh vận hành: chỉ ATTEMPT.VIEW ẩn khu vực xử lý và chặn 403 API (AC-14, AC-31)", async ({
    browser,
  }) => {
    const admin = await adminApi();
    const submitted = await submitBcaMiniEssayAttempt(
      browser,
      admin,
      "rbops",
      `Bai luan kiem tra RBAC van hanh luot FA4 ${RUN_ID}.`,
    );

    const roOps = await createCustomAdmin(admin, "ROOps", ["ATTEMPT.VIEW"]);
    const opsCtx = await browser.newContext();
    try {
      await loginStaffContext(opsCtx, roOps.username, roOps.password, "vi");
      const page = await opsCtx.newPage();

      await page.goto(`${ADMIN_URL}/attempts`);
      const nav = page.getByRole("navigation", { name: "Menu chính" });
      await expect(nav.getByRole("link", { name: "Lượt thi" })).toBeVisible();
      await expect(nav.getByRole("link", { name: "Chấm tự luận" })).toHaveCount(0);

      const searchResPromise = page.waitForResponse((r) => {
        if (r.request().method() !== "GET") return false;
        const url = new URL(r.url());
        return (
          url.pathname === "/api/v1/admin/attempts" &&
          url.searchParams.get("user") === submitted.student.username
        );
      });
      await page.getByRole("searchbox", { name: "Học viên" }).fill(submitted.student.username);
      const searchRes = await searchResPromise;
      expect(searchRes.ok()).toBe(true);
      const table = page.getByRole("table", { name: "Danh sách lượt thi" });
      const rowLink = table.locator(`a[href$="/attempts/${submitted.attemptId}"]`);
      await expect(rowLink).toBeVisible();
      await rowLink.click();

      await expect(page).toHaveURL(new RegExp(`/attempts/${submitted.attemptId}`));
      await expect(
        page.getByRole("heading", { level: 1, name: new RegExp(`Lượt ${submitted.shortCode}`) }),
      ).toBeVisible();
      await expect(page.getByRole("heading", { name: "Xử lý lượt" })).toHaveCount(0);
      await expect(page.getByRole("button", { name: "Đánh dấu hỏng" })).toHaveCount(0);
      await expect(page.getByRole("button", { name: "Cấp bù 1 lượt" })).toHaveCount(0);
      await expect(page.getByRole("button", { name: "Chấm lại" })).toHaveCount(0);

      const roHeaders = await staffApiFromContext(opsCtx);
      const fault403 = await opsCtx.request.post(
        `${ADMIN_URL}/api/v1/admin/attempts/${submitted.attemptId}/fault`,
        {
          headers: { ...roHeaders.headers, "idempotency-key": randomUUID() },
          data: { reason: "Thu danh dau hong khong co quyen FA4" },
        },
      );
      expect(fault403.status()).toBe(403);
    } finally {
      await opsCtx.close();
    }
  });

  test("ảnh màn 8 ô chuẩn cho hàng chờ chấm và vận hành lượt (AC-29)", async ({ browser }) => {
    const admin = await adminApi();
    const submitted = await submitBcaMiniEssayAttempt(
      browser,
      admin,
      "vis",
      `Bai luan kiem tra ma tran anh man hinh 8 o FA4 ${RUN_ID}.`,
    );

    // Đánh dấu hỏng & cấp bù trước để trang chi tiết lượt hiển thị đầy đủ trạng thái, banner và lịch sử sự kiện
    const faultRes = await admin.api.post(`${ADMIN_URL}/api/v1/admin/attempts/${submitted.attemptId}/fault`, {
      headers: { ...admin.headers, "idempotency-key": randomUUID() },
      data: { reason: "Du lieu ma tran anh man hinh FA4" },
    });
    expect(faultRes.status(), await faultRes.text()).toBe(200);
    const compRes = await admin.api.post(`${ADMIN_URL}/api/v1/admin/attempts/${submitted.attemptId}/compensate`, {
      headers: { ...admin.headers, "idempotency-key": randomUUID() },
      data: { reason: "Cap bu cho ma tran anh man hinh FA4" },
    });
    expect(compRes.status(), await compRes.text()).toBe(200);

    // Tạo phiên đăng nhập admin 1 lần để dùng chung trên 8 ô, tránh chạm giới hạn /auth/login
    const seedCtx = await browser.newContext();
    await loginAdmin(seedCtx, "vi");
    // Admin chủ động claim câu để form chấm hiển thị đầy đủ vùng nhập điểm và đếm chữ
    const claimHeaders = await staffApiFromContext(seedCtx);
    const claimRes = await seedCtx.request.post(
      `${ADMIN_URL}/api/v1/admin/grading/items/${submitted.itemId}/claim`,
      { headers: { ...claimHeaders.headers, "idempotency-key": randomUUID() } },
    );
    expect(claimRes.status(), await claimRes.text()).toBe(200);
    const adminCookies = await seedCtx.cookies();
    await seedCtx.close();

    const matrixBrowser = await launchMatrixBrowser();
    try {
      const cells = matrixCells();
      const allowLanguage = [/^FA4/i, /^fa4\./i, /^#[0-9A-F]{8}/, /^[0-9A-F]{8}$/];
      const overflowOpts = { scope: "main", allowAccessibleScroller: true } as const;

      for (const cell of cells) {
        const ctx = await openMatrixContext(matrixBrowser, cell, ADMIN_URL);
        try {
          await ctx.addCookies(adminCookies);
          await applyMatrixCookies(ctx, cell, ADMIN_URL);
          const page = await ctx.newPage();

          await page.goto(`${ADMIN_URL}/grading`);
          await expect(
            page.getByRole("link", { name: new RegExp(`#${submitted.shortCode}`) }).first(),
          ).toBeVisible({ timeout: 20_000 });
          await waitAdminSettled(page);
          await expectMatrixClean(page, "fa4-grading-list", cell, {
            allowLanguage,
            elementOverflow: overflowOpts,
          });

          await page.goto(`${ADMIN_URL}/grading/${submitted.itemId}`);
          await expect(page.getByTestId("essay-word-count")).toBeVisible({ timeout: 20_000 });
          await waitAdminSettled(page);
          await expectMatrixClean(page, "fa4-grading-item", cell, {
            allowLanguage,
            elementOverflow: overflowOpts,
          });

          await page.goto(`${ADMIN_URL}/attempts`);
          await expect(
            page.getByRole("link", { name: new RegExp(submitted.shortCode) }).first(),
          ).toBeVisible({ timeout: 20_000 });
          await waitAdminSettled(page);
          await expectMatrixClean(page, "fa4-attempts-list", cell, {
            allowLanguage,
            elementOverflow: {
              ...overflowOpts,
              // attempt-list-view.tsx:176-177 applies max-w-36 + span.truncate on the learner username cell (tracked as a P2 post-wave UI follow-up under QA's zero-product-code-edits scope; AD-180 3=B specifically named exam-detail-view.tsx:198)
              ignore: "pre,input,textarea,select,.katex-display,tbody td span.truncate",
            },
          });

          await page.goto(`${ADMIN_URL}/attempts/${submitted.attemptId}`);
          await expect(
            page.getByRole("heading", {
              level: 1,
              name: new RegExp(`(?:Lượt|Attempt) ${submitted.shortCode}`),
            }),
          ).toBeVisible({ timeout: 20_000 });
          await expect(
            page.getByRole("heading", { name: /Dòng sự kiện|Event timeline/ }),
          ).toBeVisible({ timeout: 20_000 });
          await waitAdminSettled(page);
          await expectMatrixClean(page, "fa4-attempt-detail", cell, {
            allowLanguage,
            elementOverflow: overflowOpts,
          });
        } finally {
          await ctx.close();
        }
      }
    } finally {
      await matrixBrowser.close();
    }
  });
});
