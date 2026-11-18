import { expect, test } from "@playwright/test";
import { ADMIN_URL, adminApi, loginAdmin } from "./helpers/editor-qa";
import {
  blockingCodes,
  checkExam,
  collectIssues,
  countBySubject,
  createExam,
  examCode,
  expectWellFormedPaper,
  generateInUi,
  saveStructure,
  SCENARIO_TIMEOUT_MS,
  SOLVER_TIMEOUT_MS,
} from "./helpers/wave-2b";

test.setTimeout(SCENARIO_TIMEOUT_MS);

/**
 * Nghiệm thu 2B mục 16, kịch bản 5 (K7, K15): SP2E Ngữ văn theo môn, mẫu mặc định "Đề minh họa cũ" (SP2E_VAN_OLD): Đọc hiểu
 * 30 câu chấm tự động, thang 6 điểm, nhóm Viết tạm bỏ qua. Đề thử 30 câu và không có cảnh báo chấm tay.
 */
test("SP2E Ngữ văn theo môn, mẫu mặc định: 30 câu, không có cảnh báo chấm tay", async ({ context, page }) => {
  await loginAdmin(context, "vi");
  const admin = await adminApi();
  const exam = await createExam(admin, {
    kind: "SP2E",
    scope: "SUBJECT",
    subjectCode: "VAN",
    durationMinutes: 60,
    code: examCode("W2B-SP2E-VAN"),
    name: "SP2E Ngữ văn nghiệm thu 2B",
  });
  expect(exam.templateCode).toBe("SP2E_VAN_OLD");
  expect(exam.questionCount).toBe(30);
  expect(exam.maxScore).toBe(6);
  await saveStructure(admin, exam.id);

  const check = await checkExam(admin, exam.id);
  expect(check.status, `lỗi chặn: ${blockingCodes(check).join(", ")}`).toBe("VALID");
  expect(
    collectIssues(check).map((issue) => issue.code),
    "kiểm tra không được báo chấm tay (K15)",
  ).not.toContain("MANUAL_GRADING_REQUIRED");

  // Tab Kiểm tra cấu hình không hiện câu cảnh báo chấm tay.
  await page.goto(`${ADMIN_URL}/exams/${exam.id}/review`);
  await page.getByRole("button", { name: /^Kiểm tra (lại|cấu hình)$/ }).click();
  await expect(page.getByText("1 / 1 hợp lệ")).toBeVisible({ timeout: SOLVER_TIMEOUT_MS });
  await expect(page.getByText(/có câu tự luận: điểm phần này chỉ có sau khi chấm tay/)).toHaveCount(0);

  const { result, panel } = await generateInUi(page, exam.id);
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
