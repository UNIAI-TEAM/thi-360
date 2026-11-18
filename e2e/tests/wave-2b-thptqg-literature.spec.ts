import { expect, test } from "@playwright/test";
import { ADMIN_URL, adminApi, loginAdmin } from "./helpers/editor-qa";
import {
  checkExam,
  collectIssues,
  examCode,
  getExam,
  publishApi,
  saveStructure,
  SCENARIO_TIMEOUT_MS,
  SOLVER_TIMEOUT_MS,
} from "./helpers/wave-2b";

test.setTimeout(SCENARIO_TIMEOUT_MS);

/**
 * Nghiệm thu bổ sung V-ACT/THPTQG, kịch bản 9 (D11, H26): THPTQG Ngữ văn lưu được nhưng không công bố được. Bước 1 báo bài
 * chỉ lưu nháp; lưu cấu trúc thành công; kiểm tra báo `TEMPLATE_NOT_PUBLISHABLE`; công bố bị từ chối.
 */
test("THPTQG Ngữ văn: tạo, lưu được, kiểm tra báo TEMPLATE_NOT_PUBLISHABLE, công bố bị từ chối", async ({ context, page }) => {
  await loginAdmin(context, "vi");

  // Bước 1 báo ngay bài chỉ lưu nháp, và vẫn tạo được.
  await page.goto(`${ADMIN_URL}/exams/new`);
  await expect(page.getByRole("form", { name: "Tạo bài thi" })).toBeVisible();
  await page.getByRole("combobox", { name: /^Kỳ thi/ }).click();
  await page.getByRole("option", { name: /THPTQG/ }).click();
  await page.getByRole("radio", { name: /^Theo môn/ }).check();
  await page.getByRole("combobox", { name: /^Môn/ }).click();
  await page.getByRole("option", { name: "Ngữ văn" }).click();
  await expect(page.getByText("Bài này chỉ lưu nháp, không công bố được và không đưa vào bộ đề bán.")).toBeVisible();
  await expect(page.getByRole("textbox", { name: /^Số câu của/ }).first()).not.toHaveValue("");
  await page.getByRole("textbox", { name: /Mã bài thi/ }).fill(examCode("W2B-TQG-VAN"));
  await page.getByRole("textbox", { name: /Tên bài thi/ }).fill("THPTQG Ngữ văn nghiệm thu 2B");
  await page.getByRole("button", { name: "Lưu và tiếp tục" }).click();
  await page.waitForURL(/\/exams\/[^/]+\/structure$/);
  const id = /\/exams\/([^/]+)\/structure$/.exec(page.url())![1]!;

  const admin = await adminApi();
  const exam = await getExam(admin, id);
  expect(exam.templateCode).toBe("THPTQG_LITERATURE_2025");
  expect(exam.questionCount).toBe(7);
  expect(exam.maxScore).toBe(10);

  // Lưu thành công (PUT 200 ở saveStructure) và cấu trúc giữ nguyên: một phần, 7 câu.
  const saved = await saveStructure(admin, id);
  expect(saved.parts).toHaveLength(1);
  expect(saved.parts[0]!.questionCount).toBe(7);

  // Kiểm tra ở giao diện: lỗi mẫu chưa công bố được hiện trong kết quả.
  await page.goto(`${ADMIN_URL}/exams/${id}/review`);
  await page.getByRole("button", { name: "Kiểm tra cấu hình", exact: true }).click();
  await expect(page.getByText(/Mẫu chuẩn của môn .* chưa công bố được/).first()).toBeVisible({ timeout: SOLVER_TIMEOUT_MS });

  const check = await checkExam(admin, id);
  expect(check.status).toBe("INVALID");
  const notPublishable = collectIssues(check).filter((issue) => issue.code === "TEMPLATE_NOT_PUBLISHABLE");
  expect(notPublishable.length).toBeGreaterThan(0);
  expect(notPublishable.every((issue) => issue.severity === "ERROR" && issue.subjectCode === "VAN")).toBe(true);
  expect(check.paths.length).toBeGreaterThan(0);
  expect(check.paths.every((path) => path.result !== "VALID")).toBe(true);

  // Công bố bị từ chối: hộp thoại không cho chọn đường nào; máy chủ từ chối 422 PATH_NOT_VALID và không tạo phiên bản.
  await page.goto(`${ADMIN_URL}/exams/${id}/versions`);
  await page.getByRole("button", { name: "Công bố", exact: true }).click();
  const dialog = page.getByRole("dialog");
  await expect(dialog.getByRole("checkbox").first()).toBeDisabled();

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
