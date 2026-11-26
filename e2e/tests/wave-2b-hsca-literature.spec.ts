import { expect, test } from "@playwright/test";
import { ADMIN_URL, adminApi, loginAdmin } from "./helpers/editor-qa";
import {
  allSections,
  blockingCodes,
  collectIssues,
  continueToReviewInUi,
  countBySubject,
  createExamInUi,
  examCode,
  expectPublishRefused,
  expectStructureQuestions,
  expectValidPaths,
  expectWellFormedPaper,
  expectWritingSwitchLocked,
  fillExamStep1InUi,
  forceBlockOmitted,
  generateInUi,
  getExam,
  getStructure,
  openBlockInUi,
  publishInUi,
  renameFirstBlockAndSaveInUi,
  restoreOmittedBlockInUi,
  SCENARIO_TIMEOUT_MS,
  SOLVER_TIMEOUT_MS,
} from "./helpers/wave-2b";

test.setTimeout(SCENARIO_TIMEOUT_MS);

/**
 * Nghiệm thu bổ sung H-SCA/V-SAT (QA addendum mục 3): H-SCA Ngữ văn. Bài cả môn có 20 câu Đọc hiểu (ba chùm 5 + 7 + 8)
 * và hai phần Viết lưu tham chiếu (`omitted`, `omittedLocked`, H43): kiểm tra đạt, sinh thử 20 câu · 20 điểm, công bố được.
 * Bật lại nhóm Viết ở bước 2 bị kiểm tra báo `OMITTED_BLOCK_LOCKED`; ở bước 1, bài theo phần không chào hai phần Viết.
 */
test("H-SCA Ngữ văn: công bố bài Đọc hiểu; phần Viết không bật lại được", async ({ context, page }) => {
  await loginAdmin(context, "vi");

  const id = await createExamInUi(page, {
    kind: "HSCA",
    scope: "SUBJECT",
    scopeLabel: /^Cả môn/,
    subject: "VAN",
    // Mẫu để trống thời lượng (Q-35): nhập khi tạo.
    overview: { durationMinutes: 90 },
    code: examCode("W2B-HSCA-VAN"),
    name: "H-SCA Ngữ văn nghiệm thu 2B",
  });
  const admin = await adminApi();

  const exam = await getExam(admin, id);
  expect(exam.templateCode).toBe("HSCA_SUBJECT_VAN");
  expect(exam.questionCount).toBe(20);
  expect(exam.maxScore).toBe(20);
  expect(exam.durationMinutes).toBe(90);
  expect(exam.timing).toBe("SHARED");

  // Cấu trúc: Đọc hiểu 5 + 7 + 8 câu; hai phần Viết 0 câu, mỗi phần một nhóm Tự luận đã lược.
  const structure = await getStructure(admin, id);
  expect(structure.parts.map((part) => [part.name, part.questionCount])).toEqual([
    ["Phần 1 · Đọc hiểu", 20],
    ["Phần 2 · Viết đoạn văn", 0],
    ["Phần 3 · Viết bài văn", 0],
  ]);
  const reading = allSections(structure).flatMap((section) => section.blocks ?? []).filter((block) => block.omitted !== true);
  expect(reading.map((block) => block.questionCount)).toEqual([5, 7, 8]);
  const writing = allSections(structure)
    .flatMap((section) => section.blocks ?? [])
    .filter((block) => block.questionType === "ESSAY");
  expect(writing.map((block) => block.omitted)).toEqual([true, true]);
  await expectStructureQuestions(page, /^20(\D|$)/);

  // Mỗi nhóm Viết mở ra ở trạng thái "Tạm bỏ qua" bật sẵn, tắt (không bấm được) và có lý do đi kèm (R3-09).
  for (const block of writing) {
    await openBlockInUi(page, id, block.id!, block.name);
    await expectWritingSwitchLocked(page);
    await expect(page.getByText("Tự luận – tạm bỏ qua")).toBeVisible();
  }

  // Lưu bước 2 qua giao diện bằng một giá trị sửa được (tên nhóm Đọc hiểu đầu tiên).
  await renameFirstBlockAndSaveInUi(page, admin, id);

  // Lưới an toàn của máy chủ: PUT ép bật lại nhóm Viết được nhận (200) nhưng kiểm tra báo OMITTED_BLOCK_LOCKED ở bước 4,
  // và công bố bị từ chối.
  await forceBlockOmitted(admin, id, writing[0]!.id!, false);
  await page.goto(`${ADMIN_URL}/exams/${id}/structure`);
  await expectStructureQuestions(page, /^\d+(\D|$)/);
  const reenabled = await continueToReviewInUi(page, id);
  expect(reenabled.status).toBe("INVALID");
  const locked = collectIssues(reenabled).filter((issue) => issue.code === "OMITTED_BLOCK_LOCKED");
  expect(locked.length, `không có OMITTED_BLOCK_LOCKED: ${JSON.stringify(blockingCodes(reenabled))}`).toBeGreaterThan(0);
  expect(locked.every((issue) => issue.severity === "ERROR")).toBe(true);
  await expect(page).toHaveURL(/\/exams\/[^/]+\/review/);
  await expect(page.getByText(/nhóm đã lược của mẫu chưa có trong V1 nên không bật lại được/).first()).toBeVisible({ timeout: SOLVER_TIMEOUT_MS });
  await expectPublishRefused(page, admin, id, reenabled);

  // Trả nhóm về trạng thái tham chiếu bằng giao diện: bật "Tạm bỏ qua" (còn bấm được vì nhóm đang được bật lại), thấy công
  // tắc khóa kèm lý do, Lưu; rồi kiểm tra đạt.
  await restoreOmittedBlockInUi(page, admin, id, writing[0]!.id!, writing[0]!.name);
  await page.goto(`${ADMIN_URL}/exams/${id}/structure`);
  await expectStructureQuestions(page, /^20(\D|$)/);
  const fixed = await continueToReviewInUi(page, id);
  expect(fixed.status, `lỗi chặn: ${blockingCodes(fixed).join(", ")}`).toBe("VALID");
  expect(collectIssues(fixed).map((issue) => issue.code)).not.toContain("OMITTED_BLOCK_LOCKED");
  await expectValidPaths(page, 1, 1);

  // Sinh thử: 20 câu · 20 điểm, tất cả ở phần Đọc hiểu; Viết không vào đề.
  const { request, result, panel } = await generateInUi(page, id);
  expect(request.target).toEqual({ kind: "EXAM" });
  const questions = expectWellFormedPaper(result);
  expect(result.paper.totalQuestions).toBe(20);
  expect(result.paper.totalPoints).toBe(20);
  expect(countBySubject(result)).toEqual({ VAN: 20 });
  expect(questions.every((question) => question.partIndex === 0), "có câu ngoài phần Đọc hiểu").toBe(true);
  await expect(panel.getByText(/^20 câu · 20 điểm · 90 phút/)).toBeVisible();

  const published = await publishInUi(page, id);
  expect(published.version).toBe(1);
  expect((await getExam(admin, id)).servingVersion).toBe(1);
});

test("H-SCA Ngữ văn theo phần: chỉ chào phần Đọc hiểu, không chào hai phần Viết", async ({ context, page }) => {
  await loginAdmin(context, "vi");

  // Bước 1 đến hết bảng tổng quan; không bấm "Lưu và tiếp tục" vì chỉ xét danh sách phần.
  await fillExamStep1InUi(page, {
    kind: "HSCA",
    scope: "SUBJECT",
    scopeLabel: /^Theo phần/,
    subject: "VAN",
    part: /Đọc hiểu/,
    overview: { durationMinutes: 45 },
    code: examCode("W2B-HSCA-VANP"),
    name: "H-SCA Ngữ văn theo phần nghiệm thu 2B",
  });
  const parts = page.getByRole("group", { name: /^Phần thi/ });
  await expect(parts.getByRole("radio")).toHaveCount(1);
  await expect(parts.getByRole("radio", { name: /Đọc hiểu/ })).toBeChecked();
  await expect(parts.getByRole("radio", { name: /Viết/ })).toHaveCount(0);
  await expect(page.getByText("Phần viết chưa có trong V1 nên không chọn được.")).toBeVisible();
});
