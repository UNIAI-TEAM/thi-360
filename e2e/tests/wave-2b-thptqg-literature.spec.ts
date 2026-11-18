import { expect, test } from "@playwright/test";
import { adminApi, loginAdmin } from "./helpers/editor-qa";
import {
  collectIssues,
  continueToReviewInUi,
  createExamInUi,
  examCode,
  expectStructureQuestions,
  getExam,
  getStructure,
  openEditorTab,
  publishApi,
  renameFirstBlockAndSaveInUi,
  SCENARIO_TIMEOUT_MS,
  SOLVER_TIMEOUT_MS,
} from "./helpers/wave-2b";

test.setTimeout(SCENARIO_TIMEOUT_MS);

/**
 * Nghiệm thu bổ sung V-ACT/THPTQG, kịch bản 9 (D11, H26): THPTQG Ngữ văn lưu được nhưng không công bố được. Bước 1 báo bài
 * chỉ lưu nháp; lưu cấu trúc ở bước 2 thành công; kiểm tra báo `TEMPLATE_NOT_PUBLISHABLE`; công bố bị từ chối.
 */
test("THPTQG Ngữ văn: tạo, lưu được, kiểm tra báo TEMPLATE_NOT_PUBLISHABLE, công bố bị từ chối", async ({ context, page }) => {
  await loginAdmin(context, "vi");

  // Bước 1 báo ngay bài chỉ lưu nháp, và vẫn tạo được.
  const id = await createExamInUi(page, {
    kind: "THPTQG",
    scope: "SUBJECT",
    subject: "VAN",
    notice: "Bài này chỉ lưu nháp, không công bố được và không đưa vào bộ đề bán.",
    code: examCode("W2B-TQG-VAN"),
    name: "THPTQG Ngữ văn nghiệm thu 2B",
  });
  const admin = await adminApi();
  const exam = await getExam(admin, id);
  expect(exam.templateCode).toBe("THPTQG_LITERATURE_2025");
  expect(exam.questionCount).toBe(7);
  expect(exam.maxScore).toBe(10);

  // Bước 2: lưu thành công (PUT 200 ở giao diện) và cấu trúc giữ nguyên: một phần, 7 câu.
  await expectStructureQuestions(page, /^7(\D|$)/);
  await renameFirstBlockAndSaveInUi(page, admin, id);
  const saved = await getStructure(admin, id);
  expect(saved.parts).toHaveLength(1);
  expect(saved.parts[0]!.questionCount).toBe(7);

  // "Tiếp tục": kiểm tra có lỗi chặn nên ở lại bước 2; kết quả báo TEMPLATE_NOT_PUBLISHABLE.
  const check = await continueToReviewInUi(page, id);
  expect(check.status).toBe("INVALID");
  const notPublishable = collectIssues(check).filter((issue) => issue.code === "TEMPLATE_NOT_PUBLISHABLE");
  expect(notPublishable.length).toBeGreaterThan(0);
  expect(notPublishable.every((issue) => issue.severity === "ERROR" && issue.subjectCode === "VAN")).toBe(true);
  expect(check.paths.length).toBeGreaterThan(0);
  expect(check.paths.every((path) => path.result !== "VALID")).toBe(true);

  // Tab Kiểm tra cấu hình: lỗi mẫu chưa công bố được hiện trong kết quả.
  await openEditorTab(page, "Kiểm tra và xem trước", /\/exams\/[^/]+\/review/);
  await expect(page.getByText(/Mẫu chuẩn của môn .* chưa công bố được/).first()).toBeVisible({ timeout: SOLVER_TIMEOUT_MS });

  // Công bố bị từ chối: hộp thoại không cho chọn đường nào; máy chủ từ chối 422 PATH_NOT_VALID và không tạo phiên bản.
  await openEditorTab(page, "Phiên bản và công bố", /\/exams\/[^/]+\/versions/);
  await page.getByRole("button", { name: "Công bố", exact: true }).click();
  const dialog = page.getByRole("dialog");
  await expect(dialog.getByRole("checkbox").first()).toBeDisabled();

  // Giao diện không cho gửi; chỉ gọi thẳng API để khẳng định máy chủ cũng từ chối.
  const refused = await publishApi(
    admin,
    id,
    check.paths.map((path) => path.pathId),
  );
  expect(refused.status(), await refused.text()).toBe(422);
  const refusal: { code?: string } = await refused.json();
  expect(refusal.code).toBe("PATH_NOT_VALID");
  const after = await getExam(admin, id);
  expect(after.servingVersion ?? null).toBeNull();
});
