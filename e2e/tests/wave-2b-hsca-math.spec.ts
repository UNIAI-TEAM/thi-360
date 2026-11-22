import { expect, test } from "@playwright/test";
import { adminApi, loginAdmin } from "./helpers/editor-qa";
import {
  allSections,
  blockCountCell,
  continueToReviewInUi,
  countBySubject,
  createExamInUi,
  examCode,
  expectStructureQuestions,
  expectValidPaths,
  expectWellFormedPaper,
  generateInUi,
  getExam,
  getStructure,
  openBlockInUi,
  publishInUi,
  renameFirstBlockAndSaveInUi,
  SCENARIO_TIMEOUT_MS,
} from "./helpers/wave-2b";

test.setTimeout(SCENARIO_TIMEOUT_MS);

/**
 * Nghiệm thu bổ sung H-SCA/V-SAT (QA addendum mục 1): H-SCA Toán cả môn. Tạo ở bước 1, các dòng lấy câu của mẫu khóa
 * (`fixedBlockCounts`, H42) ở bước 2, kiểm tra, sinh thử và công bố được. Mọi bước đi qua giao diện.
 */
test("H-SCA Toán cả môn: tạo, dòng khóa, kiểm tra, sinh thử, công bố", async ({ context, page }) => {
  await loginAdmin(context, "vi");

  const id = await createExamInUi(page, {
    kind: "HSCA",
    scope: "SUBJECT",
    scopeLabel: /^Cả môn/,
    subject: "TOAN",
    code: examCode("W2B-HSCA-TOAN"),
    name: "H-SCA Toán cả môn nghiệm thu 2B",
  });
  const admin = await adminApi();

  const exam = await getExam(admin, id);
  expect(exam.templateCode).toBe("HSCA_SUBJECT_TOAN");
  expect(exam.questionCount).toBe(40);
  expect(exam.durationMinutes).toBe(90);
  expect(exam.maxScore).toBe(10);

  // Cấu trúc của mẫu: ba phần 25 / 5 / 10 câu; phần 1 tách hai dòng 20 + 5.
  const structure = await getStructure(admin, id);
  expect(structure.parts.map((part) => [part.name, part.questionCount])).toEqual([
    ["Phần 1 · Trắc nghiệm", 25],
    ["Phần 2 · Dữ liệu tổng hợp", 5],
    ["Phần 3 · Điền đáp án", 10],
  ]);
  const blocks = structure.parts.map((part) => (part.sections ?? []).flatMap((section) => section.blocks ?? []));
  expect(blocks[0]!.map((block) => [block.name, block.questionType, block.questionCount])).toEqual([
    ["Trắc nghiệm chọn một", "SINGLE_CHOICE", 20],
    ["Trắc nghiệm chọn nhiều", "MULTIPLE_CHOICE", 5],
  ]);
  expect(blocks[1]!.map((block) => [block.questionType, block.questionCount])).toEqual([["QUESTION_SET", 5]]);
  expect(blocks[2]!.map((block) => [block.questionType, block.questionCount])).toEqual([["SHORT_ANSWER", 10]]);
  await expectStructureQuestions(page, /40/);

  // Dòng lấy câu bị mẫu khóa trên Danh sách dòng (FTM R-7, H42): lý do khóa chữ, ô số câu chỉ đọc,
  // cách lấy khóa, dạng câu chỉ đọc ở [data-template-facts], không có nút xóa nhóm.
  const firstBlock = allSections(structure).flatMap((section) => section.blocks ?? [])[0]!;
  await openBlockInUi(page, id, firstBlock.id!, firstBlock.name);
  const rowEditor = page.locator(`[data-form-row-editor="${firstBlock.id}"]`);
  await expect(rowEditor).toBeVisible();
  await expect(rowEditor.locator('[data-lock-reason="rowCounts"]')).toHaveText("Theo mẫu đề chuẩn, không đổi số lượng");
  const countCell = blockCountCell(page);
  await expect(countCell).toHaveJSProperty("readOnly", true);
  await expect(countCell).toHaveValue("20");
  await expect(countCell).toHaveAccessibleDescription(/Theo mẫu đề chuẩn, không đổi số lượng/);
  await expect(rowEditor.locator("[data-template-facts]")).toContainText("Một đáp án");
  await expect(page.getByRole("button", { name: /^Xóa nhóm / })).toHaveCount(0);
  // Nhóm do mẫu khóa không cho đổi cách lấy câu.
  await expect(page.getByRole("radio", { name: /^Câu chùm/ })).toBeDisabled();

  // Lưu qua giao diện (đổi tên nhóm: giá trị sửa được vô hại khi dòng bị khóa).
  await renameFirstBlockAndSaveInUi(page, admin, id);

  const check = await continueToReviewInUi(page, id);
  expect(check.status, `lỗi chặn: ${JSON.stringify(check.paths.map((item) => item.issues))}`).toBe("VALID");
  await expectValidPaths(page, 1, 1);

  const { request, result, panel } = await generateInUi(page, id);
  expect(request.target).toEqual({ kind: "EXAM" });
  const questions = expectWellFormedPaper(result);
  expect(result.paper.totalQuestions).toBe(40);
  expect(result.paper.totalPoints).toBe(10);
  expect(countBySubject(result)).toEqual({ TOAN: 40 });
  // Số câu từng phần của đề thử khớp dòng của mẫu: 25 / 5 / 10 (phần 1 gồm hai dòng 20 + 5).
  for (const [index, expected] of [25, 5, 10].entries()) {
    expect(questions.filter((question) => question.partIndex === index), `phần ${index + 1} của đề thử`).toHaveLength(expected);
  }
  const part1 = result.paper.parts[0]!;
  expect((part1.blocks ?? []).map((block) => block.questionCount)).toEqual([20, 5]);
  await expect(panel.getByText(/^40 câu · 10 điểm · 90 phút/)).toBeVisible();

  const published = await publishInUi(page, id);
  expect(published.version).toBe(1);
  expect((await getExam(admin, id)).servingVersion).toBe(1);
});
