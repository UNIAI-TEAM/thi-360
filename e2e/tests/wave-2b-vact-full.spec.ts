import { expect, test } from "@playwright/test";
import { adminApi, loginAdmin } from "./helpers/editor-qa";
import {
  blockingCodes,
  checkExam,
  countBySubject,
  createExam,
  examCode,
  expectWellFormedPaper,
  generateInUi,
  getExam,
  publishApi,
  saveStructure,
  SCENARIO_TIMEOUT_MS,
} from "./helpers/wave-2b";

test.setTimeout(SCENARIO_TIMEOUT_MS);

/**
 * Nghiệm thu bổ sung V-ACT/THPTQG (addendum mục 5.1), kịch bản 6: V-ACT toàn bài, mẫu VACT_FULL. Tạo, kiểm tra đạt, đề thử
 * 120 câu (60 / 30 / 30 theo ba phần, 10 điểm mỗi câu), công bố được.
 */
test("V-ACT toàn bài: tạo, kiểm tra đạt, đề thử 120 câu, công bố", async ({ context, page }) => {
  await loginAdmin(context, "vi");
  const admin = await adminApi();
  const exam = await createExam(admin, { kind: "VACT", scope: "FULL", code: examCode("W2B-VACT"), name: "V-ACT toàn bài nghiệm thu 2B" });
  expect(exam.templateCode).toBe("VACT_FULL");
  expect(exam.questionCount).toBe(120);
  expect(exam.maxScore).toBe(1200);
  expect(exam.durationMinutes).toBe(150);
  await saveStructure(admin, exam.id);

  // Kiểm tra đạt: không lỗi chặn; cảnh báo phân bố tham khảo (DISTRIBUTION_DEVIATION) không chặn.
  const check = await checkExam(admin, exam.id);
  expect(check.status, `lỗi chặn: ${blockingCodes(check).join(", ")}`).toBe("VALID");
  expect(blockingCodes(check)).toEqual([]);
  expect(check.paths).toHaveLength(1);
  expect(check.paths[0]!.result).toBe("VALID");

  const { request, result } = await generateInUi(page, exam.id);
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
  await expect(page.getByRole("tabpanel", { name: "Đề xem trước" }).getByText(/^120 câu · /)).toBeVisible();

  // Công bố được: đường duy nhất hợp lệ, v1.
  const published = await publishApi(admin, exam.id, [check.paths[0]!.pathId]);
  expect(published.status(), await published.text()).toBe(200);
  const response = (await published.json()) as { version: number; publishedPathIds: string[] };
  expect(response.version).toBe(1);
  expect(response.publishedPathIds).toEqual([check.paths[0]!.pathId]);
  expect((await getExam(admin, exam.id)).servingVersion).toBe(1);
});
