import { expect, test } from "@playwright/test";
import {
  adminApi,
  createKnowledgeTree,
  editable,
  fillChoices,
  fillClassification,
  insertButton,
  loginAdmin,
  markReady,
  openNewQuestion,
  openQuestionPreview,
  saveDraft,
  storedQuestion,
} from "./helpers/editor-qa";

test.describe.configure({ mode: "serial" });
test.setTimeout(180_000);

test("ED-QA flow 1: Toan question with a formula built in the visual math field", async ({ page, context }) => {
  await loginAdmin(context);
  const admin = await adminApi();
  const tree = await createKnowledgeTree(admin, "TOAN", "F1");

  await openNewQuestion(page, "SINGLE_CHOICE");
  await fillClassification(page, { subject: "Mathematics", difficulty: "Remembering", knowledgeCode: tree.leafCode });
  await editable(page, 0).click();
  await page.keyboard.type("Evaluate ");
  await insertButton(page, "Math formula").click();
  const dialog = page.getByRole("dialog");
  await expect(dialog.getByRole("heading", { name: "Math formula" })).toBeVisible();
  const field = dialog.locator("math-field");
  await expect(field).toBeVisible();
  await field.click();
  await page.keyboard.type("x^2+1/2");
  await expect(dialog.getByLabel("LaTeX code")).toHaveValue("x^2+\\frac12");
  await dialog.getByRole("button", { name: "Insert formula" }).click();
  await expect(dialog).toBeHidden();
  await fillChoices(page, 1);

  const saved = await saveDraft(page);
  const stored = await storedQuestion(admin, saved.id);
  expect(stored.content).toBe('<p>Evaluate <span class="math-inline">\\(x^2+\\frac12\\)</span></p>');

  await markReady(page);
  const main = await openQuestionPreview(page, saved.id);
  const formula = main.locator(".math-inline").first();
  await expect(formula.locator(".katex")).toBeVisible();
  await expect(formula.locator('annotation[encoding="application/x-tex"]')).toHaveText("x^2+\\frac12");
});

test("ED-QA flow 2: Hoa question with a chemical equation from the chemistry dialog", async ({ page, context }) => {
  await loginAdmin(context);
  const admin = await adminApi();
  const tree = await createKnowledgeTree(admin, "HOA", "F2");

  await openNewQuestion(page, "SINGLE_CHOICE");
  await fillClassification(page, { subject: "Chemistry", difficulty: "Understanding", knowledgeCode: tree.leafCode });
  await editable(page, 0).click();
  await page.keyboard.type("Balance: ");
  await insertButton(page, "Chemistry formula").click();
  const dialog = page.getByRole("dialog");
  await expect(dialog.getByRole("heading", { name: "Chemistry formula" })).toBeVisible();
  await dialog.getByLabel("Quick typing").fill("2H2 + O2 -> 2H2O");
  await expect(dialog.getByTestId("chemistry-preview").locator(".katex")).toBeVisible();
  await dialog.getByRole("button", { name: "Insert into content" }).click();
  await expect(dialog).toBeHidden();
  await fillChoices(page, 1);

  const saved = await saveDraft(page);
  const stored = await storedQuestion(admin, saved.id);
  expect(stored.content).toMatch(/^<p>Balance: <span class="math-inline">\\\(\\ce\{2H2 \+ O2 -&gt; 2H2O\}\\\)<\/span><\/p>$/);

  await markReady(page);
  const main = await openQuestionPreview(page, saved.id);
  const equation = main.locator(".math-inline").first();
  await expect(equation.locator(".katex")).toBeVisible();
  await expect(equation.locator('annotation[encoding="application/x-tex"]')).toContainText("\\ce{2H2 + O2 -> 2H2O}");
});
