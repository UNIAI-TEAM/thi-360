import { expect, test } from "@playwright/test";
import { adminApi, loginAdmin } from "./helpers/editor-qa";
import {
  allSections,
  blockCountCell,
  blockingCodes,
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
  getStructureSummary,
  openBlockInUi,
  publishInUi,
  renameFirstBlockAndSaveInUi,
  SCENARIO_TIMEOUT_MS,
} from "./helpers/wave-2b";

test.setTimeout(SCENARIO_TIMEOUT_MS);

/**
 * Nghiệm thu bổ sung H-SCA/V-SAT (QA addendum mục 4): V-SAT Toán cả môn, 25 câu · 67 lệnh · 150 điểm · 90 phút. Bốn phần:
 * I Đúng/Sai 9 câu (36 lệnh, 54 điểm), II chọn một 2 chùm × 3 (6, 36), III ghép đôi 5 câu (20, 30), IV trả lời ngắn 5 câu
 * (5, 30). Tạo, khóa dòng, lưu, kiểm tra, sinh thử và công bố đều đi qua giao diện.
 */
test("V-SAT Toán cả môn: 25 câu, 67 lệnh, 150 điểm; kiểm tra, sinh thử, công bố", async ({ context, page }) => {
  await loginAdmin(context, "vi");

  const id = await createExamInUi(page, {
    kind: "VSAT",
    scope: "SUBJECT",
    scopeLabel: /^Cả môn/,
    subject: "TOAN",
    code: examCode("W2B-VSAT-TOAN"),
    name: "V-SAT Toán cả môn nghiệm thu 2B",
  });
  const admin = await adminApi();

  const exam = await getExam(admin, id);
  expect(exam.templateCode).toBe("VSAT_SUBJECT_TOAN");
  expect(exam.questionCount).toBe(25);
  expect(exam.maxScore).toBe(150);
  expect(exam.durationMinutes).toBe(90);
  expect(exam.timing).toBe("SHARED");

  const structure = await getStructure(admin, id);
  expect(structure.parts.map((part) => [part.name, part.questionCount, part.maxPoints])).toEqual([
    ["Phần I · Đúng/Sai", 9, 54],
    ["Phần II · Trắc nghiệm chọn một", 6, 36],
    ["Phần III · Ghép đôi", 5, 30],
    ["Phần IV · Trả lời ngắn", 5, 30],
  ]);
  // 67 lệnh: số lệnh từng phần do máy chủ cộng (Đúng/Sai bốn ý = 4 lệnh, ghép đôi bốn mục = 4 lệnh).
  const summary = await getStructureSummary(admin, id);
  expect(summary.parts.map((row) => row.commandCount)).toEqual([36, 6, 20, 5]);
  expect(summary.parts.reduce((sum, row) => sum + (row.commandCount ?? 0), 0)).toBe(67);
  await expectStructureQuestions(page, /^25(\D|$)/);

  // Dòng lấy câu bị mẫu khóa trên Danh sách dòng (FTM R-7, H42): lý do khóa chữ, ô số câu chỉ đọc, cách lấy khóa, không có nút xóa nhóm.
  const firstBlock = allSections(structure).flatMap((section) => section.blocks ?? [])[0]!;
  await openBlockInUi(page, id, firstBlock.id!, firstBlock.name);
  const rowEditor = page.locator(`[data-form-row-editor="${firstBlock.id}"]`);
  await expect(rowEditor).toBeVisible();
  await expect(rowEditor.locator('[data-lock-reason="rowCounts"]')).toHaveText("Theo mẫu đề chuẩn, không đổi số lượng");
  const countCell = blockCountCell(page);
  await expect(countCell).toHaveJSProperty("readOnly", true);
  await expect(countCell).toHaveValue("9");
  await expect(countCell).toHaveAccessibleDescription(/Theo mẫu đề chuẩn, không đổi số lượng/);
  await expect(rowEditor.locator("[data-template-facts]")).toContainText("Đúng/Sai");
  await expect(page.getByRole("radio", { name: /^Câu chùm/ })).toBeDisabled();
  await expect(page.getByRole("button", { name: /^Xóa nhóm / })).toHaveCount(0);

  await renameFirstBlockAndSaveInUi(page, admin, id);

  const check = await continueToReviewInUi(page, id);
  expect(check.status, `lỗi chặn: ${blockingCodes(check).join(", ")}`).toBe("VALID");
  await expectValidPaths(page, 1, 1);

  const { request, result, panel } = await generateInUi(page, id);
  expect(request.target).toEqual({ kind: "EXAM" });
  const questions = expectWellFormedPaper(result);
  expect(result.paper.totalQuestions).toBe(25);
  expect(result.paper.totalPoints).toBe(150);
  expect(countBySubject(result)).toEqual({ TOAN: 25 });
  for (const [index, expected] of [9, 6, 5, 5].entries()) {
    expect(questions.filter((question) => question.partIndex === index), `phần ${index + 1} của đề thử`).toHaveLength(expected);
  }
  await expect(panel.getByText(/^25 câu · 150 điểm · 90 phút/)).toBeVisible();

  const published = await publishInUi(page, id);
  expect(published.version).toBe(1);
  expect((await getExam(admin, id)).servingVersion).toBe(1);
});
