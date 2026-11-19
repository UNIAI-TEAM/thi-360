import { expect, test } from "@playwright/test";
import { readFileSync } from "node:fs";
import { dirname, resolve } from "node:path";
import { fileURLToPath } from "node:url";
import { allExams, miniOf, publishedExamSet, waitForPublishedExamSet } from "./helpers/published-exam-set";
import { ADMIN_URL, loginAdmin } from "./helpers/editor-qa";

const MESSAGES_PATH = resolve(dirname(fileURLToPath(import.meta.url)), "../../apps/web-admin/src/messages/vi.json");
const messages = JSON.parse(readFileSync(MESSAGES_PATH, "utf8")) as {
  exams: { status: { PUBLISHED: string; DISCONTINUED: string }; list: { filters: { search: string } } };
};

const PUBLISHED_LABEL = messages.exams.status.PUBLISHED;
const DISCONTINUED_LABEL = messages.exams.status.DISCONTINUED;
const SEARCH_LABEL = messages.exams.list.filters.search;

test("mọi bài đã công bố", async ({ page, context }) => {
  test.setTimeout(300_000);
  await loginAdmin(context, "vi");
  await waitForPublishedExamSet(context.request, { timeoutMs: 240_000 });

  await page.goto(`${ADMIN_URL}/exams`);
  const search = page.getByRole("searchbox", { name: SEARCH_LABEL });
  const discontinuedCode = publishedExamSet().discontinuedExam;

  for (const exam of allExams()) {
    await search.fill(exam.code);
    const row = page.getByRole("row").filter({ has: page.getByText(exam.code, { exact: true }) });
    await expect(row, `exam ${exam.code} appears in the admin list`).toBeVisible({ timeout: 5_000 });
    await expect(row).toContainText(exam.code === discontinuedCode ? DISCONTINUED_LABEL : PUBLISHED_LABEL, {
      timeout: 5_000,
    });
  }

  for (const exam of allExams().filter((item) => item.miniCode !== null)) {
    const mini = miniOf(exam.code);
    expect(mini?.code, `mini copy linked to ${exam.code}`).toBe(exam.miniCode);
    if (!mini) throw new Error(`Missing mini copy for ${exam.code}`);
    await search.fill(mini.code);
    const row = page.getByRole("row").filter({ has: page.getByText(mini.code, { exact: true }) });
    await expect(row, `mini copy ${mini.code} exists in the admin list`).toBeVisible({ timeout: 5_000 });
    await expect(row).toContainText(PUBLISHED_LABEL, { timeout: 5_000 });
  }

  await search.fill("DS-VACT-FULL-MINI");
  await expect(
    page.getByRole("row").filter({ has: page.getByText("DS-VACT-FULL-MINI", { exact: true }) }),
  ).toHaveCount(0, { timeout: 5_000 });
});
