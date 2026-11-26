import { expect, test } from "@playwright/test";
import { adminApi, loginAdmin } from "./helpers/editor-qa";
import {
  blockingCodes,
  continueToReviewInUi,
  countBySubject,
  createExamInUi,
  ensureListingFilledInUi,
  examCode,
  expectStructureQuestions,
  expectValidPaths,
  expectWellFormedPaper,
  generateInUi,
  getExam,
  openEditorTab,
  publishInUi,
  renameFirstBlockAndSaveInUi,
  SCENARIO_TIMEOUT_MS,
} from "./helpers/wave-2b";

test.setTimeout(SCENARIO_TIMEOUT_MS);
test.use({ actionTimeout: 30_000, navigationTimeout: 45_000 });

/**
 * Nghiệm thu bổ sung V-ACT/THPTQG (addendum mục 5.1), kịch bản 6: V-ACT toàn bài, mẫu VACT_FULL. Tạo, kiểm tra đạt, đề thử
 * 120 câu (60 / 30 / 30 theo ba phần, 10 điểm mỗi câu), công bố được. Mọi bước ở giao diện.
 */
test("V-ACT toàn bài: tạo, kiểm tra đạt, đề thử 120 câu, công bố", async ({ context, page }) => {
  await loginAdmin(context, "vi");
  const id = await createExamInUi(page, { kind: "VACT", scope: "FULL", code: examCode("W2B-VACT"), name: "V-ACT toàn bài nghiệm thu 2B" });
  const admin = await adminApi();
  const exam = await getExam(admin, id);
  expect(exam.templateCode).toBe("VACT_FULL");
  expect(exam.questionCount).toBe(120);
  expect(exam.maxScore).toBe(1200);
  expect(exam.durationMinutes).toBe(150);

  // Bước 2: mở, sửa tên một nhóm (số câu của mẫu V-ACT bị khóa), lưu.
  await expectStructureQuestions(page, /^120(\D|$)/);
  await renameFirstBlockAndSaveInUi(page, admin, id);

  // Điền thông tin bán trước khi Kiểm tra toàn bài để `listingCheck` không báo lỗi `LISTING_*_MISSING` trong `blockingCodes(check)`.
  await ensureListingFilledInUi(page, id);
  await openEditorTab(page, "Cấu trúc và ma trận", /\/exams\/[^/]+\/structure/);
  await expectStructureQuestions(page, /^120(\D|$)/);

  // Kiểm tra đạt: không lỗi chặn; cảnh báo phân bố tham khảo (DISTRIBUTION_DEVIATION) không chặn.
  const check = await continueToReviewInUi(page, id);
  expect(check.status, `lỗi chặn: ${blockingCodes(check).join(", ")}`).toBe("VALID");
  expect(blockingCodes(check)).toEqual([]);
  expect(check.paths).toHaveLength(1);
  expect(check.paths[0]!.result).toBe("VALID");
  await expectValidPaths(page, 1, 1);

  const { request, result, panel } = await generateInUi(page, id);
  expect(request.target).toEqual({ kind: "EXAM" });
  expectWellFormedPaper(result);
  expect(result.paper.totalQuestions).toBe(120);
  expect(result.paper.totalPoints).toBe(1200);
  expect(result.paper.parts.map((_, index) => countBySubject(result, index))).toEqual([
    { VIETNAMESE: 30, ANH: 30 },
    { TOAN: 30 },
    { SCIENTIFIC_THINKING: 30 },
  ]);
  // Tiếng Anh: năm nhóm theo thứ tự 5 / 5 / 5 / 7 / 8 câu.
  const english = result.paper.parts[0]!.blocks!.filter((block) => block.subjectCode === "ANH");
  expect(english.map((block) => block.questionCount)).toEqual([5, 5, 5, 7, 8]);
  await expect(panel.getByText(/^120 câu · 1\.200 điểm/)).toBeVisible();

  // Công bố được: đường duy nhất hợp lệ, v1.
  const published = await publishInUi(page, id);
  expect(published.version).toBe(1);
  expect(published.publishedPathIds).toEqual([check.paths[0]!.pathId]);
  await expect.poll(async () => (await getExam(admin, id)).servingVersion).toBe(1);
});
