import { expect, test, type Locator } from "@playwright/test";
import {
  adminApi,
  createKnowledgeTree,
  editable,
  fillChoices,
  fillClassification,
  fixtureSample,
  generateExamPreview,
  word365FormulaCount,
  word365Html,
  insertButton,
  SOURCE_LABEL,
  UNIT_LABEL,
  loginAdmin,
  markReady,
  openNewQuestion,
  openQuestionPreview,
  richEditor,
  saveDraft,
  selectText,
  shape,
  storedQuestion,
  tinyPng,
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
  // Mũ giữ nhiều chữ số (smartSuperscript tắt, UNI-2878): → để ra khỏi mũ trước khi gõ tiếp.
  await page.keyboard.type("x^2");
  await page.keyboard.press("ArrowRight");
  await page.keyboard.type("+1/2");
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

  const paper = await generateExamPreview(page, admin, {
    subjectCode: "TOAN",
    questionType: "SINGLE_CHOICE",
    points: 1,
    leafId: tree.leafId,
  });
  await expect(paper.getByText(saved.code)).toBeVisible();
  await expect(paper.locator(".math-inline .katex").first()).toBeVisible();
});

test("UNI-2878: multi-digit exponents stay in the superscript for every power template", async ({ page, context }) => {
  await loginAdmin(context);
  await openNewQuestion(page, "SINGLE_CHOICE");
  await editable(page, 0).click();
  await insertButton(page, "Math formula").click();
  const dialog = page.getByRole("dialog");
  const field = dialog.locator("math-field");
  await expect(field).toBeVisible();
  const latex = dialog.getByLabel("LaTeX code");

  // Typed caret: mathlive's default smartSuperscript used to jump out after the first digit (2^12 became 2^1 2).
  await field.click();
  await page.keyboard.type("2^12");
  await expect(latex).toHaveValue("2^{12}");

  const cases: Array<{ button: string; keys: string[]; expected: string }> = [
    { button: "Power of e", keys: ["12"], expected: "e^{12}" },
    { button: "Power of ten", keys: ["12"], expected: "10^{12}" },
    { button: "Power", keys: ["2", "Tab", "12"], expected: "2^{12}" },
    { button: "Subscript and superscript", keys: ["a", "Tab", "123", "Tab", "45"], expected: "a_{123}^{45}" },
  ];
  await dialog.getByRole("tab", { name: "Powers, indices" }).click();
  for (const item of cases) {
    await latex.fill("");
    await dialog.getByRole("button", { name: item.button, exact: true }).click();
    for (const key of item.keys) {
      if (key === "Tab") await page.keyboard.press("Tab");
      else await page.keyboard.type(key);
    }
    await expect(latex).toHaveValue(item.expected);
  }
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

  const paper = await generateExamPreview(page, admin, {
    subjectCode: "HOA",
    questionType: "SINGLE_CHOICE",
    points: 1,
    leafId: tree.leafId,
  });
  await expect(paper.getByText(saved.code)).toBeVisible();
  await expect(paper.locator(".math-inline .katex").first()).toBeVisible();
});

test("ED-QA flow 3: Dia question with a data table and an image", async ({ page, context }) => {
  await loginAdmin(context);
  const admin = await adminApi();
  const tree = await createKnowledgeTree(admin, "DIA", "F3");

  await openNewQuestion(page, "SINGLE_CHOICE");
  await fillClassification(page, { subject: "Geography", difficulty: "Applying", knowledgeCode: tree.leafCode });
  await editable(page, 0).click();
  await page.keyboard.type("Read the table and the chart.");

  await insertButton(page, "Data table").click();
  const tableDialog = page.getByRole("dialog");
  await expect(tableDialog.getByRole("heading", { name: "Data table" })).toBeVisible();
  await tableDialog.getByLabel("Table title").fill("TRADE VALUE OF THE US");
  await tableDialog.getByLabel("Unit").fill("billion USD");
  await tableDialog.getByLabel("Source", { exact: true }).fill("World Bank, 2022");
  // Lưới mới có bốn cột: bỏ cột thừa để bảng ba cột như bảng mẫu.
  await tableDialog.getByLabel("Cell row 1, column 4", { exact: true }).click();
  await tableDialog.getByRole("button", { name: "Delete column" }).click();
  const cells = [
    ["Year", "2000", "2020"],
    ["Exports", "1096,1", "2148,6"],
    ["Imports", "1477,2", "2776,1"],
  ];
  for (const [row, values] of cells.entries()) {
    for (const [col, value] of values.entries()) {
      await tableDialog.getByLabel(`Cell row ${row + 1}, column ${col + 1}`, { exact: true }).fill(value);
    }
  }
  await tableDialog.getByRole("button", { name: "Insert table" }).click();
  await expect(tableDialog).toBeHidden();
  await expect(richEditor(page, 0).locator("figure.data-table")).toBeVisible();

  await insertButton(page, "Image").click();
  const imageDialog = page.getByRole("dialog");
  await expect(imageDialog.getByRole("heading", { name: "Image" })).toBeVisible();
  await imageDialog.getByLabel("Choose image file").setInputFiles({ name: "chart.png", mimeType: "image/png", buffer: tinyPng() });
  await expect(imageDialog.getByRole("img", { name: "No image yet" })).toBeHidden();
  await imageDialog.getByLabel("Alternative text").fill("Pig numbers 2015 - 2021");
  await imageDialog.getByLabel("Caption below the image").fill("Pigs and pork output");
  await imageDialog.getByLabel("Source", { exact: true }).fill("GSO");
  // Nút radio ẩn thị giác (sr-only), người dùng bấm nhãn; kiểm tra radio đã được chọn.
  await imageDialog.locator("label").filter({ hasText: "Medium" }).click();
  await expect(imageDialog.getByRole("radio", { name: "Medium" })).toBeChecked();
  await imageDialog.getByRole("button", { name: "Insert image" }).click();
  await expect(imageDialog).toBeHidden();
  await expect(richEditor(page, 0).locator("figure.image img")).toBeVisible();
  await fillChoices(page, 1);

  const saved = await saveDraft(page);
  const stored = await storedQuestion(admin, saved.id);
  expect(stored.content).toContain(
    `<figure class="data-table"><figcaption>TRADE VALUE OF THE US</figcaption><table><thead><tr><th scope="col">Year</th><th scope="col">2000</th><th scope="col">2020</th></tr></thead><tbody><tr><th scope="row">Exports</th><td class="num">1096,1</td><td class="num">2148,6</td></tr><tr><th scope="row">Imports</th><td class="num">1477,2</td><td class="num">2776,1</td></tr></tbody></table><p class="figure-note">${UNIT_LABEL}billion USD</p><p class="figure-source">${SOURCE_LABEL}World Bank, 2022</p></figure>`,
  );
  expect(shape(stored.content)).toContain(shape(fixtureSample("data-table")));
  expect(shape(stored.content)).toContain(shape(fixtureSample("image-figure")));
  expect(stored.content).toMatch(/<img src="\/api\/v1\/admin\/question-assets\/([0-9a-f-]{36})" alt="Pig numbers 2015 - 2021" data-asset-id="\1">/);

  await markReady(page);
  const main = await openQuestionPreview(page, saved.id);
  await expectTableAndImage(main);

  const paper = await generateExamPreview(page, admin, {
    subjectCode: "DIA",
    questionType: "SINGLE_CHOICE",
    points: 1,
    leafId: tree.leafId,
  });
  await expect(paper.getByText(saved.code)).toBeVisible();
  await expectTableAndImage(paper);
});

async function expectTableAndImage(scope: Locator): Promise<void> {
  const table = scope.locator("figure.data-table").first();
  await expect(table.locator("figcaption")).toHaveText("TRADE VALUE OF THE US");
  await expect(table.locator("thead th[scope=col]")).toHaveCount(3);
  await expect(table.locator("tbody th[scope=row]")).toHaveText(["Exports", "Imports"]);
  await expect(table.locator("td.num")).toHaveCount(4);
  await expect(table.locator("p.figure-note")).toHaveText(`${UNIT_LABEL}billion USD`);
  await expect(table.locator("p.figure-source")).toHaveText(`${SOURCE_LABEL}World Bank, 2022`);
  const figure = scope.locator("figure.image").first();
  await expect(figure.locator("img")).toBeVisible();
  await expect(figure.locator("img")).toHaveJSProperty("complete", true);
  expect(await figure.locator("img").evaluate((img: HTMLImageElement) => img.naturalWidth)).toBeGreaterThan(0);
  await expect(figure.locator("figcaption")).toHaveText("Pigs and pork output");
}

test("ED-QA flow 4: Van question with a poem excerpt (quote, line numbers)", async ({ page, context }) => {
  await loginAdmin(context);
  const admin = await adminApi();
  const tree = await createKnowledgeTree(admin, "VAN", "F4");

  await openNewQuestion(page, "SINGLE_CHOICE");
  await fillClassification(page, { subject: "Literature", difficulty: "Understanding", knowledgeCode: tree.leafCode });
  await editable(page, 0).click();
  await page.keyboard.type("Read the excerpt and answer.");

  await insertButton(page, "Quote").click();
  const dialog = page.getByRole("dialog");
  await dialog.getByRole("radio", { name: "Poem" }).check();
  await dialog
    .getByLabel("Content", { exact: false })
    .first()
    .fill(
      [
        "Buon trong cua be chieu hom,",
        "Thuyen ai thap thoang canh buom xa xa?",
        "Buon trong ngon nuoc moi sa,",
        "Hoa troi man mac biet la ve dau?",
        "",
        "Buon trong noi co rau rau,",
        "Chan may mat dat mot mau xanh xanh.",
      ].join("\n"),
    );
  await dialog.getByRole("checkbox", { name: "Keep line breaks" }).check();
  await dialog.getByRole("checkbox", { name: "Number lines, every 5 lines" }).check();
  await dialog.getByLabel("Author").fill("Nguyen Du");
  await dialog.getByLabel("Work").fill("Truyen Kieu");
  await expect(dialog.locator("blockquote.quote")).toBeVisible();
  await dialog.getByRole("button", { name: "Insert quote" }).click();
  await expect(dialog).toBeHidden();
  await fillChoices(page, 1);

  const saved = await saveDraft(page);
  const stored = await storedQuestion(admin, saved.id);
  expect(stored.content).toContain(
    '<blockquote class="quote" data-kind="poem" data-line-numbers="5"><p>Buon trong cua be chieu hom,<br>Thuyen ai thap thoang canh buom xa xa?<br>Buon trong ngon nuoc moi sa,<br>Hoa troi man mac biet la ve dau?</p><p>Buon trong noi co rau rau,<br>Chan may mat dat mot mau xanh xanh.</p><footer>(Nguyen Du, <cite>Truyen Kieu</cite>)</footer></blockquote>',
  );
  expect(shape(stored.content)).toContain(shape(fixtureSample("quote-poem")));

  await markReady(page);
  const main = await openQuestionPreview(page, saved.id);
  await expectPoem(main);

  const paper = await generateExamPreview(page, admin, {
    subjectCode: "VAN",
    questionType: "SINGLE_CHOICE",
    points: 1,
    leafId: tree.leafId,
  });
  await expect(paper.getByText(saved.code)).toBeVisible();
  await expectPoem(paper);
});

async function expectPoem(scope: Locator): Promise<void> {
  const quote = scope.locator("blockquote.quote").first();
  await expect(quote).toHaveAttribute("data-kind", "poem");
  await expect(quote).toHaveAttribute("data-line-numbers", "5");
  // RichTextView dựng mỗi dòng thơ thành một hàng; cột số chỉ điền ở dòng thứ 5, đếm liên tục qua các khổ.
  await expect(quote.locator(".quote-line")).toHaveCount(6);
  await expect(quote.locator(".quote-line-number")).toHaveText(["", "", "", "", "5", ""]);
  await expect(quote.locator(".quote-line-text").first()).toHaveText("Buon trong cua be chieu hom,");
  await expect(quote.locator("footer")).toHaveText("(Nguyen Du, Truyen Kieu)");
  await expect(quote.locator("footer cite")).toHaveText("Truyen Kieu");
}

test("ED-QA flow 5: Tieng Anh find-the-error question with labeled underlines", async ({ page, context }) => {
  await loginAdmin(context);
  const admin = await adminApi();
  const tree = await createKnowledgeTree(admin, "ANH", "F5");

  await openNewQuestion(page, "SINGLE_CHOICE");
  await fillClassification(page, { subject: "English", difficulty: "Applying", knowledgeCode: tree.leafCode });
  const sentence = "For millennia, the coconuts has been central to the lives of Polynesian peoples.";
  await editable(page, 0).click();
  await page.keyboard.type(sentence);

  const phrases = ["has been", "to", "of", "peoples"];
  for (const [index, phrase] of phrases.entries()) {
    await selectText(page, 0, sentence.indexOf(phrase), phrase.length);
    await insertButton(page, "Labeled underline").click();
    const dialog = page.getByRole("dialog");
    await expect(dialog.getByText(phrase, { exact: true })).toBeVisible();
    await expect(dialog.getByText(`Next unused label: ${"ABCD"[index]}`)).toBeVisible();
    await dialog.getByRole("button", { name: "Underline" }).click();
    await expect(dialog).toBeHidden();
  }
  await expect(richEditor(page, 0).locator("u[data-label]")).toHaveCount(4);
  // Cụm thứ hai trở đi bật phương án tự lấy: bốn phương án lấy chữ của cụm A-D.
  await expect(page.getByText("from underlined phrase")).toHaveCount(4);
  await page.getByRole("radio", { name: "Option A is correct" }).check();

  const saved = await saveDraft(page);
  const stored = await storedQuestion(admin, saved.id);
  expect(stored.content).toBe(
    "<p>For millennia, the coconuts <u data-label=\"A\">has been</u> central <u data-label=\"B\">to</u> the lives <u data-label=\"C\">of</u> Polynesian <u data-label=\"D\">peoples</u>.</p>",
  );
  const options = (stored.body as { options: Array<{ content: string; correct: boolean }> }).options;
  expect(options.map((option) => option.content)).toEqual(["<p>has been</p>", "<p>to</p>", "<p>of</p>", "<p>peoples</p>"]);
  expect(options.map((option) => option.correct)).toEqual([true, false, false, false]);

  await markReady(page);
  const main = await openQuestionPreview(page, saved.id);
  await expectLabeledUnderlines(main);

  const paper = await generateExamPreview(page, admin, {
    subjectCode: "ANH",
    questionType: "SINGLE_CHOICE",
    points: 1,
    leafId: tree.leafId,
  });
  await expect(paper.getByText(saved.code)).toBeVisible();
  await expectLabeledUnderlines(paper);
});

async function expectLabeledUnderlines(scope: Locator): Promise<void> {
  const underlined = scope.locator("u[data-label]");
  await expect(underlined).toHaveCount(4);
  await expect(underlined).toHaveText(["has been", "to", "of", "peoples"]);
  await expect(underlined.nth(0)).toHaveAttribute("data-label", "A");
  await expect(underlined.nth(3)).toHaveAttribute("data-label", "D");
}

test("ED-QA flow 6: QUESTION_SET reading passage with 5 numbered blanks", async ({ page, context }) => {
  await loginAdmin(context);
  const admin = await adminApi();
  const tree = await createKnowledgeTree(admin, "ANH", "F6");

  await openNewQuestion(page, "QUESTION_SET");
  await fillClassification(page, { subject: "English", difficulty: "Understanding", knowledgeCode: tree.leafCode });

  // Mỗi chỗ trống gắn với một câu con: thêm đủ năm câu con trước khi chèn chỗ trống.
  for (let child = 0; child < 5; child++) {
    await page.getByRole("button", { name: "Short answer" }).click();
    await editable(page, 1 + 2 * child).click();
    await page.keyboard.type(`Word for blank ${child + 1}`);
    await page.getByLabel("Standard answer").nth(child).fill(`answer${child + 1}`);
    await page.locator(`input[name="children.${child}.points"]`).fill("1");
  }

  const lead = ["The word was", " by Paul in 2002, and the epoch began", " years ago, while the", " of the", " ended"];
  await editable(page, 0).click();
  for (const [index, text] of lead.entries()) {
    await page.keyboard.type(`${text} `);
    await insertButton(page, "Blank", 0).click();
    const dialog = page.getByRole("dialog");
    await expect(dialog.getByText(`Sub-question ${index + 1}`, { exact: false })).toBeVisible();
    await dialog.getByRole("button", { name: "Insert blank" }).click();
    await expect(dialog).toBeHidden();
  }
  await expect(richEditor(page, 0).locator("span.blank")).toHaveCount(5);

  const saved = await saveDraft(page);
  const stored = await storedQuestion(admin, saved.id);
  const passage = (stored.body as { passage: string }).passage;
  const blanks = [...passage.matchAll(/<span class="blank" data-blank="(\d+)"><\/span>/g)].map((match) => match[1]);
  expect(blanks).toEqual(["1", "2", "3", "4", "5"]);
  expect(passage).toContain('<p>The word was <span class="blank" data-blank="1"></span> by Paul');
  // Dạng đã lưu khớp mẫu `set-passage-blanks` (số chỗ trống và số thứ tự khác nhau nên bỏ qua; đoạn cuối của mẫu là chữ
  // sau chỗ trống cuối, luồng này kết thúc ngay sau chỗ trống nên bỏ phần đó khi so).
  const blankShape = (html: string) => shape(html).replace(/data-blank="\d+"/g, 'data-blank="N"');
  expect(blankShape(passage)).toContain(blankShape(fixtureSample("set-passage-blanks")).replace(/T<\/p>$/, ""));

  await markReady(page);
  const main = await openQuestionPreview(page, saved.id);
  await expectNumberedBlanks(main);

  const paper = await generateExamPreview(page, admin, {
    subjectCode: "ANH",
    questionType: "QUESTION_SET",
    points: 5,
    leafId: tree.leafId,
    setCount: 1,
    setSize: 5,
  });
  await expect(paper.getByText(`${saved.code} \u00b7 v1`, { exact: true })).toBeVisible();
  await expectNumberedBlanks(paper);
});

async function expectNumberedBlanks(scope: Locator): Promise<void> {
  const blanks = scope.locator("span.blank");
  await expect(blanks).toHaveCount(5);
  for (let number = 1; number <= 5; number++) {
    await expect(blanks.nth(number - 1)).toContainText(`(${number})`);
  }
}

test("ED-QA flow 7: question pasted from Word (real Office 365 clipboard sample)", async ({ page, context }) => {
  await loginAdmin(context);
  const admin = await adminApi();
  const tree = await createKnowledgeTree(admin, "TOAN", "F7");

  await openNewQuestion(page, "SINGLE_CHOICE");
  await fillClassification(page, { subject: "Mathematics", difficulty: "Applying", knowledgeCode: tree.leafCode });
  await editable(page, 0).click();
  await editable(page, 0).evaluate((element, html) => {
    const clipboardData = new DataTransfer();
    clipboardData.setData("text/html", html);
    clipboardData.setData("text/plain", "Word clipboard");
    element.dispatchEvent(new ClipboardEvent("paste", { clipboardData, bubbles: true, cancelable: true }));
  }, word365Html());

  const dialog = page.getByRole("dialog");
  await expect(dialog.getByRole("heading", { name: "Paste from Word" })).toBeVisible();
  await expect(dialog.getByText("5 formulas auto-fixed, waiting for confirmation")).toBeVisible();
  await dialog.getByRole("button", { name: "Accept all auto-fixes" }).click();
  await dialog.getByRole("button", { name: "Put into the form" }).click();
  await expect(dialog).toBeHidden();
  await page.getByRole("radio", { name: "Option B is correct" }).check();

  const saved = await saveDraft(page);
  const stored = await storedQuestion(admin, saved.id);
  expect(stored.content).toContain('<span class="math-block">\\[x=\\frac{-b\\pm\\sqrt{b^2-4ac}}{2a}\\]</span>');
  expect(stored.content).toContain('<span class="math-inline">\\(x_1+y_n^2\\)</span>');
  expect(stored.content).toContain('<span class="math-block">\\[\\ce{H2SO4}\\]</span>');
  expect(stored.content).toContain('<span class="math-block">\\[{30}^{\\circ}\\]</span>');
  expect(stored.content).toContain("\\sin x+\\cos x=1");
  expect(stored.content).not.toMatch(/mso-|<o:|<!--/);
  const options = (stored.body as { options: Array<{ content: string; correct: boolean }> }).options;
  expect(options.map((option) => option.content)).toEqual(["<p>1</p>", "<p>2</p>", "<p>3</p>", "<p>4</p>"]);
  expect(options.map((option) => option.correct)).toEqual([false, true, false, false]);
  expect(stored.solution).toMatch(/^<p>.+ x = 1\.<\/p>$/);

  await markReady(page);
  const main = await openQuestionPreview(page, saved.id);
  await expect(main.locator(".katex").first()).toBeVisible();
  expect(await main.locator(".math-inline .katex, .math-block .katex").count()).toBeGreaterThanOrEqual(word365FormulaCount());
  await expect(main.locator('annotation[encoding="application/x-tex"]').filter({ hasText: "\\ce{H2SO4}" })).toHaveCount(1);

  const paper = await generateExamPreview(page, admin, {
    subjectCode: "TOAN",
    questionType: "SINGLE_CHOICE",
    points: 1,
    leafId: tree.leafId,
  });
  await expect(paper.getByText(saved.code)).toBeVisible();
  await expect(paper.locator(".math-block .katex").first()).toBeVisible();
  await expect(paper.locator('annotation[encoding="application/x-tex"]').filter({ hasText: "\\ce{H2SO4}" })).toHaveCount(1);
});
