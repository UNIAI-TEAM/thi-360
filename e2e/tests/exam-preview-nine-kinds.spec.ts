import { expect, test, type Locator, type Page } from "@playwright/test";
import { ensureDsReady } from "./helpers/attempt-fixtures";
import { ADMIN_URL, adminApi, loginAdmin, type AdminApi } from "./helpers/editor-qa";
import {
  applyMatrixCookies,
  cellLabel,
  expectMatrixClean,
  launchMatrixBrowser,
  matrixCells,
  openMatrixContext,
  type MatrixCell,
} from "./helpers/visual-matrix";
import {
  clickAndCapturePreview,
  expectWellFormedPaper,
  openPreviewTab,
  SCENARIO_TIMEOUT_MS,
  selectPathInUi,
} from "./helpers/wave-2b";

test.describe.configure({ mode: "serial" });

interface AdminExamListItem {
  id: string;
  code: string;
  name: string;
  kindCode: string;
}

interface AdminExamListPayload {
  items?: AdminExamListItem[];
  content?: AdminExamListItem[];
}

interface TargetExamKind {
  kind: string;
  code: string;
  pathChoice?: RegExp;
}

const NINE_EXAM_KINDS: readonly TargetExamKind[] = [
  { kind: "HSA", code: "DS-HSA-SUBJECT-TOAN-MINI" },
  { kind: "QDA", code: "DS-QDA-PART-SCIENCE-MINI", pathChoice: /Q[DĐ]01/i },
  { kind: "BCA", code: "DS-BCA-FULL-MINI", pathChoice: /CA1/i },
  { kind: "VACT", code: "DS-VACT-SUBJECT-TOAN-MINI" },
  { kind: "SP2E", code: "DS-SP2E-MATH-2026-MINI" },
  { kind: "SPT", code: "DS-SPT-INFORMATICS-MINI" },
  { kind: "THPTQG", code: "DS-THPTQG-INFORMATICS-MINI" },
  { kind: "HSCA", code: "DS-HSCA-ANH-MINI" },
  { kind: "VSAT", code: "DS-VSAT-ANH" },
];

const MATRIX_ALLOW_LANGUAGE: RegExp[] = [
  /^DS-/i,
  /^(?:HSA|QDA|BCA|VACT|SP2E|SPT|THPTQG|HSCA|VSAT)\b/i,
  /^(?:KN|QT|SC|MC|TF|SA|CL|MT|ES)-[\w-]+/i,
  /^v\d+$/i,
  /^Thi\s*360$/,
];

const MATRIX_OVERFLOW_OPTS = {
  scope: "main",
  allowAccessibleScroller: true,
  ignore: "pre,input,textarea,select,.katex-display,nav ul.overflow-x-auto",
} as const;

async function findExamByCode(admin: AdminApi, code: string): Promise<AdminExamListItem> {
  const res = await admin.api.get(`${ADMIN_URL}/api/v1/admin/exams?q=${encodeURIComponent(code)}&size=20`);
  expect(res.status(), `GET /admin/exams?q=${code}: ${await res.text()}`).toBe(200);
  const payload = (await res.json()) as AdminExamListPayload;
  const rows = payload.items ?? payload.content ?? [];
  const matched = rows.find((item) => item.code === code);
  expect(matched, `Khong tim thay de thi mau ${code}`).toBeDefined();
  return matched as AdminExamListItem;
}

function trackPageErrors(page: Page): string[] {
  const errors: string[] = [];
  page.on("pageerror", (error) => {
    errors.push(`pageerror: ${error.message}`);
  });
  page.on("console", (msg) => {
    if (msg.type() === "error") {
      errors.push(`console.error: ${msg.text()}`);
    }
  });
  return errors;
}

async function openPreviewTabForLocale(page: Page, lang: "vi" | "en"): Promise<Locator> {
  if (lang === "vi") {
    return openPreviewTab(page);
  }
  const tab = page.getByRole("tab", { name: "Preview paper" });
  await expect(tab).toBeVisible({ timeout: 20_000 });
  await tab.click();
  const panel = page.getByRole("tabpanel", { name: "Preview paper" });
  await expect(panel).toBeVisible({ timeout: 20_000 });
  return panel;
}

async function waitPreviewSettled(page: Page): Promise<void> {
  await expect(page.locator('[aria-busy="true"]')).toHaveCount(0, { timeout: 20_000 });
  await expect(page.locator(".animate-pulse")).toHaveCount(0, { timeout: 20_000 });
  await page.evaluate(() => document.fonts.ready);
}

async function expandAllBlocks(panel: Locator): Promise<void> {
  const showAllButtons = panel.getByRole("button", { name: /^(Hiện tất cả|Show all) / });
  for (let step = 0; step < 50; step += 1) {
    const remaining = await showAllButtons.count();
    if (remaining === 0) break;
    await showAllButtons.first().click();
    await expect(showAllButtons).toHaveCount(remaining - 1, { timeout: 10_000 });
  }
  await expect(showAllButtons).toHaveCount(0);
}

async function countRenderedQuestions(panel: Locator): Promise<number> {
  const headings = panel.getByRole("heading", { name: /^(?:Câu|Questions?)\s+\d+(?:[–-]\d+)?$/ });
  const texts = await headings.allInnerTexts();
  let total = 0;
  for (const text of texts) {
    const match = /^(?:Câu|Questions?)\s+(\d+)(?:[–-](\d+))?$/.exec(text.trim());
    if (!match) continue;
    const from = Number(match[1]);
    const to = match[2] ? Number(match[2]) : from;
    expect(to, `Dai cau khong hop le: ${text.trim()}`).toBeGreaterThanOrEqual(from);
    total += to - from + 1;
  }
  return total;
}

async function generateExamPreview(
  page: Page,
  examId: string,
  lang: "vi" | "en",
  pathChoice?: RegExp,
): Promise<{ panel: Locator; totalQuestions: number; previewId: string | null }> {
  await page.goto(`${ADMIN_URL}/exams/${examId}/review`);
  const panel = await openPreviewTabForLocale(page, lang);
  if (pathChoice) {
    if (lang === "en") {
      await panel.getByRole("combobox", { name: "Choice path" }).click();
      await page.getByRole("option", { name: pathChoice }).click();
    } else {
      await selectPathInUi(page, panel, pathChoice);
    }
  }
  const genButton = panel.getByRole("button", {
    name: /^(Sinh đề xem trước|Sinh đề thử mới|Generate preview paper|Generate a new trial paper)$/,
  });
  await expect(genButton).toBeVisible({ timeout: 20_000 });
  await expect(genButton).toBeEnabled({ timeout: 20_000 });

  const { result } = await clickAndCapturePreview(page, examId, genButton);
  expectWellFormedPaper(result);
  expect(result.paper.totalQuestions).toBeGreaterThan(0);

  await expect(panel.getByRole("heading", { name: /^(?:Câu|Questions?)\s+\d+/ }).first()).toBeVisible({
    timeout: 20_000,
  });
  await waitPreviewSettled(page);
  return { panel, totalQuestions: result.paper.totalQuestions, previewId: result.previewId ?? null };
}

test.describe("FA-5: đề xem trước qua @thi360/ui/question cho 9 kỳ thi và làm thử audio HSCA", () => {
  test("chín kỳ thi", async ({ browser, context, page }) => {
    test.setTimeout(SCENARIO_TIMEOUT_MS);
    await ensureDsReady(browser);
    await loginAdmin(context, "vi");
    const admin = await adminApi();
    const pageErrors = trackPageErrors(page);

    try {
      for (const entry of NINE_EXAM_KINDS) {
        const exam = await findExamByCode(admin, entry.code);
        const { panel, totalQuestions, previewId } = await generateExamPreview(
          page,
          exam.id,
          "vi",
          entry.pathChoice,
        );

        await expect(panel.getByText(new RegExp(`(^|\\D)${totalQuestions}\\s+câu(?:\\b|$)`)).first()).toBeVisible();
        await expandAllBlocks(panel);
        expect(await countRenderedQuestions(panel), `${entry.kind} (${entry.code}): so cau hien thi`).toBe(
          totalQuestions,
        );

        if (entry.kind === "VSAT") {
          for (const roman of ["I", "II", "III", "IV"]) {
            await expect(panel.getByRole("heading", { name: new RegExp(`^Phần ${roman}\\b`) })).toBeVisible();
          }
        }

        if (entry.kind === "HSCA") {
          expect(previewId, "HSCA previewId").toBeTruthy();
          await expect(panel.getByText("Có audio").first()).toBeVisible();
          await expect(panel.getByRole("group", { name: /^Trình phát audio · / }).first()).toBeVisible();

          const tryButton = panel.getByRole("button", { name: "Làm thử như học viên" });
          await expect(tryButton).toBeEnabled();
          const [trialPage] = await Promise.all([context.waitForEvent("page"), tryButton.click()]);
          const trialErrors = trackPageErrors(trialPage);
          const forbiddenTrialRequests: string[] = [];
          trialPage.on("request", (req) => {
            const url = req.url();
            if (
              url.includes("/attempts/") ||
              url.includes("/play-log") ||
              url.includes("/audio/plays") ||
              (req.method() !== "GET" && url.includes("audio"))
            ) {
              forbiddenTrialRequests.push(`${req.method()} ${url}`);
            }
          });
          try {
            await trialPage.waitForLoadState("domcontentloaded");
            await expect(trialPage).toHaveURL(new RegExp(`/exams/${exam.id}/preview/.+/try`));
            await expect(
              trialPage.getByRole("group", { name: /^Trình phát audio(?: chung)? · Câu \d+/ }).first(),
            ).toBeVisible({ timeout: 20_000 });
            const regions = trialPage.getByRole("region", { name: "Trình phát audio" });
            await expect(regions.first()).toBeVisible({ timeout: 20_000 });
            const firstRegion = regions.first();
            await expect(firstRegion.getByText("Còn 2/2 lần nghe")).toBeVisible();
            await expect(trialPage.locator('input[type="range"]')).toHaveCount(0);
            await expect(trialPage.getByRole("slider")).toHaveCount(0);
            const firstAudio = firstRegion.locator("audio");
            await expect(firstAudio).toHaveAttribute("preload", "none");
            expect(await firstAudio.getAttribute("controls")).toBeNull();

            const [assetResponse] = await Promise.all([
              trialPage.waitForResponse(
                (res) =>
                  res.url().includes("/api/v1/admin/question-assets/") && res.request().method() === "GET",
                { timeout: 20_000 },
              ),
              firstRegion.getByRole("button", { name: "Nghe" }).click(),
            ]);
            expect([200, 206]).toContain(assetResponse.status());
            await expect(firstRegion.getByText("Còn 1/2 lần nghe")).toBeVisible();
            await expect(firstAudio).toHaveAttribute("src", /\/api\/v1\/admin\/question-assets\//);
            expect(forbiddenTrialRequests, `HSCA trial unexpected requests: ${forbiddenTrialRequests.join("; ")}`).toEqual([]);
            expect(trialErrors, `HSCA trial console errors: ${trialErrors.join("; ")}`).toEqual([]);
          } finally {
            await trialPage.close();
          }
        }
      }

      expect(pageErrors, `preview console errors: ${pageErrors.join("; ")}`).toEqual([]);
    } finally {
      await admin.api.dispose();
    }
  });

  test("ma trận giao diện", async ({ browser }) => {
    test.setTimeout(SCENARIO_TIMEOUT_MS);
    await ensureDsReady(browser);
    const admin = await adminApi();

    try {
      const [vsat, hsca] = await Promise.all([
        findExamByCode(admin, "DS-VSAT-ANH"),
        findExamByCode(admin, "DS-HSCA-ANH-MINI"),
      ]);
      const storage = await admin.api.storageState();
      const matrixBrowser = await launchMatrixBrowser();

      try {
        const cells: MatrixCell[] = matrixCells();
        for (const cell of cells) {
          const ctx = await openMatrixContext(matrixBrowser, cell, ADMIN_URL);
          try {
            await ctx.addCookies(storage.cookies);
            await applyMatrixCookies(ctx, cell, ADMIN_URL);
            const page = await ctx.newPage();
            const errors = trackPageErrors(page);

            // 1. exam-preview-paper (DS-VSAT-ANH: Phần I–IV + nhiều dạng câu hỏi)
            await generateExamPreview(page, vsat.id, cell.lang);
            await expectMatrixClean(page, "exam-preview-paper", cell, {
              allowLanguage: MATRIX_ALLOW_LANGUAGE,
              elementOverflow: MATRIX_OVERFLOW_OPTS,
            });

            // 2. exam-preview-hsca-audio (DS-HSCA-ANH-MINI: PreviewAudioPlayer)
            const { panel: hscaPanel } = await generateExamPreview(page, hsca.id, cell.lang);
            await expect(
              hscaPanel.getByRole("group", { name: /^(?:Trình phát audio|Audio player) · / }).first(),
            ).toBeVisible();
            await waitPreviewSettled(page);
            await expectMatrixClean(page, "exam-preview-hsca-audio", cell, {
              allowLanguage: MATRIX_ALLOW_LANGUAGE,
              elementOverflow: MATRIX_OVERFLOW_OPTS,
            });

            // 3. exam-trial-hsca-audio (/exams/[id]/preview/[previewId]/try: AttemptAudioPlayer)
            const tryBtn = hscaPanel.getByRole("button", {
              name: /^(?:Làm thử như học viên|Try as student)$/,
            });
            await expect(tryBtn).toBeEnabled();
            const [trialPage] = await Promise.all([ctx.waitForEvent("page"), tryBtn.click()]);
            const trialErrors = trackPageErrors(trialPage);
            try {
              await trialPage.waitForLoadState("domcontentloaded");
              await expect(
                trialPage.getByRole("group", {
                  name: /^(?:Trình phát audio(?: chung)?|Shared audio player|Audio player) · /,
                }).first(),
              ).toBeVisible({ timeout: 20_000 });
              await expect(
                trialPage.getByRole("region", { name: /^(?:Trình phát audio|Audio player)$/ }).first(),
              ).toBeVisible({ timeout: 20_000 });
              await waitPreviewSettled(trialPage);
              await expectMatrixClean(trialPage, "exam-trial-hsca-audio", cell, {
                allowLanguage: MATRIX_ALLOW_LANGUAGE,
                elementOverflow: MATRIX_OVERFLOW_OPTS,
              });
              expect(trialErrors, `trial matrix errors (${cellLabel(cell)})`).toEqual([]);
            } finally {
              await trialPage.close();
            }

            expect(errors, `preview matrix errors (${cellLabel(cell)})`).toEqual([]);
          } finally {
            await ctx.close();
          }
        }
      } finally {
        await matrixBrowser.close();
      }
    } finally {
      await admin.api.dispose();
    }
  });
});
