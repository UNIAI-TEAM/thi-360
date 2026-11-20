import { expect, test } from "@playwright/test";
import { adminApi, loginAdmin } from "./helpers/editor-qa";
import {
  blockingCodes,
  collectIssues,
  continueToReviewInUi,
  countBySubject,
  createExamInUi,
  examCode,
  expectStructureQuestions,
  expectValidPaths,
  expectWellFormedPaper,
  generateInUi,
  getExam,
  renameFirstBlockAndSaveInUi,
  SCENARIO_TIMEOUT_MS,
} from "./helpers/wave-2b";

test.setTimeout(SCENARIO_TIMEOUT_MS);

/**
 * Nghiệm thu 2B mục 16, kịch bản 5 (K7, K15): SP2E Ngữ văn theo môn, mẫu mặc định "Đề minh họa cũ" (SP2E_VAN_OLD): Đọc hiểu
 * 30 câu chấm tự động, thang 6 điểm, nhóm Viết tạm bỏ qua. Đề thử 30 câu và không có cảnh báo chấm tay. Bước 1, bước 2,
 * kiểm tra và sinh thử ở giao diện.
 */
test("SP2E Ngữ văn theo môn, mẫu mặc định: 30 câu, không có cảnh báo chấm tay", async ({ context, page }) => {
  await loginAdmin(context, "vi");
  const id = await createExamInUi(page, {
    kind: "SP2E",
    scope: "SUBJECT",
    subject: "VAN",
    code: examCode("W2B-SP2E-VAN"),
    name: "SP2E Ngữ văn nghiệm thu 2B",
    // Mẫu SP2E_VAN_OLD không có thời lượng: "Thời lượng chung" phải nhập. Phần II · Viết giữ số của mẫu (không nhập 1 cho qua).
    overview: { durationMinutes: 60 },
  });
  const admin = await adminApi();
  const exam = await getExam(admin, id);
  expect(exam.templateCode).toBe("SP2E_VAN_OLD");
  expect(exam.questionCount).toBe(30);
  expect(exam.maxScore).toBe(6);

  await expectStructureQuestions(page, /^30(\D|$)/);
  await renameFirstBlockAndSaveInUi(page, admin, id);

  const check = await continueToReviewInUi(page, id);
  expect(check.status, `lỗi chặn: ${blockingCodes(check).join(", ")}`).toBe("VALID");
  expect(
    collectIssues(check).map((issue) => issue.code),
    "kiểm tra không được báo chấm tay (K15)",
  ).not.toContain("MANUAL_GRADING_REQUIRED");

  // Tab Kiểm tra cấu hình hiện kết quả vừa nhận và không có câu cảnh báo chấm tay.
  await expectValidPaths(page, 1, 1);
  await expect(page.getByRole("heading", { name: "Có câu chấm tay" })).toHaveCount(0);
  await expect(page.getByText(/có câu chấm tay\. Điểm tổng hiện sau khi người chấm chấm xong/)).toHaveCount(0);

  const { result, panel } = await generateInUi(page, id);
  expectWellFormedPaper(result);
  expect(result.paper.totalQuestions).toBe(30);
  expect(result.paper.totalPoints).toBeCloseTo(6, 5);
  expect(result.paper.hasManualGrading).toBeFalsy();
  expect(countBySubject(result)).toEqual({ VAN: 30 });
  expect(
    result.paper.parts.flatMap((part) => part.blocks ?? []).filter((block) => block.manualGrading),
    "không nhóm nào chấm tay",
  ).toEqual([]);
  await expect(panel.getByText(/^30 câu · 6 điểm/)).toBeVisible();
  await expect(panel.getByText(/Đề có câu tự luận chấm tay/)).toHaveCount(0);
});
