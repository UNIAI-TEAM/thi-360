import { expect, test } from "@playwright/test";
import { ADMIN_URL, adminApi, loginAdmin } from "./helpers/editor-qa";
import {
  allSections,
  blockingCodes,
  checkExam,
  clickAndCapturePreview,
  countBySubject,
  examCode,
  expectWellFormedPaper,
  getExam,
  getStructure,
  pathWithChoice,
  SCENARIO_TIMEOUT_MS,
  SOLVER_TIMEOUT_MS,
} from "./helpers/wave-2b";

test.setTimeout(SCENARIO_TIMEOUT_MS);

/**
 * Nghiệm thu 2B mục 16, kịch bản 1: QDA toàn bài. Tạo ở bước 1 (mẫu mặc định QDA_FULL) → thiết lập sáu môn của phần 3
 * (Lý, Hóa, Sinh, Sử, Địa, Tiếng Anh) → kiểm tra toàn bài → sinh thử QĐ02 → công bố.
 */
test("QDA toàn bài: tạo, thiết lập sáu môn, kiểm tra toàn bài, sinh thử QĐ02, công bố", async ({ context, page }) => {
  await loginAdmin(context, "vi");

  // 1. Bước 1: kỳ thi QDA, toàn bài; bảng tổng quan nạp số của mẫu mặc định.
  await page.goto(`${ADMIN_URL}/exams/new`);
  await expect(page.getByRole("form", { name: "Tạo bài thi" })).toBeVisible();
  await page.getByRole("combobox", { name: /^Kỳ thi/ }).click();
  await page.getByRole("option", { name: /QDA/ }).click();
  await page.getByRole("radio", { name: /^Toàn bài/ }).check();
  await expect(page.getByRole("textbox", { name: /^Số câu của/ }).first()).not.toHaveValue("");
  await page.getByRole("textbox", { name: /Mã bài thi/ }).fill(examCode("W2B-QDA"));
  await page.getByRole("textbox", { name: /Tên bài thi/ }).fill("QDA toàn bài nghiệm thu 2B");
  await page.getByRole("button", { name: "Lưu và tiếp tục" }).click();
  await page.waitForURL(/\/exams\/[^/]+\/structure$/);
  const id = /\/exams\/([^/]+)\/structure$/.exec(page.url())![1]!;

  const admin = await adminApi();
  const exam = await getExam(admin, id);
  expect(exam.templateCode).toBe("QDA_FULL");
  expect(exam.questionCount).toBe(150);
  expect(exam.maxScore).toBe(150);

  // 2. Thiết lập sáu môn: phần 3 chọn mã có kho sáu môn. Mỗi môn kiểm tra riêng rồi phải ở trạng thái "Đã kiểm tra".
  const structure = await getStructure(admin, id);
  expect(structure.parts).toHaveLength(3);
  const pool = structure.parts[2]!.subjectPool ?? [];
  expect(pool.map((section) => section.subjectCode).sort()).toEqual(["ANH", "DIA", "HOA", "LY", "SINH", "SU"]);
  expect(Object.fromEntries(pool.map((section) => [section.subjectCode, section.questionCount]))).toEqual({
    LY: 25,
    HOA: 25,
    SINH: 25,
    SU: 25,
    DIA: 25,
    ANH: 50,
  });
  for (const section of pool) {
    const result = await checkExam(admin, id, { scope: "SECTION", sectionId: section.id });
    expect(blockingCodes(result), `môn ${section.subjectCode}`).toEqual([]);
    const states = (await getStructure(admin, id)).sectionStates ?? [];
    expect(states.find((state) => state.sectionId === section.id)?.state, `môn ${section.subjectCode}`).toBe("CHECKED");
  }

  // 3. Kiểm tra toàn bài ở tab Kiểm tra cấu hình: năm mã QĐ01–QĐ05, cả năm hợp lệ.
  await page.goto(`${ADMIN_URL}/exams/${id}/review`);
  await page.getByRole("button", { name: "Kiểm tra cấu hình", exact: true }).click();
  await expect(page.getByText("5 / 5 hợp lệ")).toBeVisible({ timeout: SOLVER_TIMEOUT_MS });

  const check = await checkExam(admin, id);
  expect(check.status, `lỗi chặn: ${blockingCodes(check).join(", ")}`).toBe("VALID");
  expect(blockingCodes(check)).toEqual([]);
  expect(check.paths).toHaveLength(5);
  expect(check.paths.every((path) => path.result === "VALID")).toBe(true);
  const afterCheck = await getStructure(admin, id);
  const allStates = afterCheck.sectionStates ?? [];
  expect(allStates).toHaveLength(allSections(afterCheck).length);
  expect(allStates.filter((state) => state.state !== "CHECKED")).toEqual([]);

  // 4. Sinh thử QĐ02 (Hóa học – Sinh học) ở tab Đề xem trước.
  await page.getByRole("tab", { name: "Đề xem trước" }).click();
  const preview = page.getByRole("tabpanel", { name: "Đề xem trước" });
  await expect(preview).toBeVisible();
  await expect(preview.getByText("Bài có phần chọn mã: hãy chọn một mã.")).toBeVisible();
  await preview.getByRole("combobox", { name: "Đường lựa chọn" }).click();
  await page.getByRole("option", { name: /Q[DĐ]02/ }).click();
  const { request, result } = await clickAndCapturePreview(page, id, preview.getByRole("button", { name: "Sinh đề xem trước" }));

  expect(request.target).toEqual({ kind: "PATH", pathId: pathWithChoice(check, "QD02").pathId });
  const questions = expectWellFormedPaper(result);
  expect(result.paper.totalQuestions).toBe(150);
  expect(result.paper.totalPoints).toBe(150);
  expect(result.paper.parts).toHaveLength(3);
  expect(result.path?.choices?.map((choice) => choice.code)).toContain("QD02");
  expect(countBySubject(result, 0)).toEqual({ TOAN: 50 });
  expect(countBySubject(result, 1)).toEqual({ VAN: 50 });
  expect(countBySubject(result, 2)).toEqual({ HOA: 25, SINH: 25 });
  // Hóa học là 25 câu đầu của phần 3, Sinh học 25 câu sau (số câu tính từ đầu phần).
  const first = result.paper.parts[2]!.firstNumber ?? 0;
  const science = questions.filter((question) => question.partIndex === 2);
  expect(science.filter((question) => question.subjectCode === "HOA").map((question) => question.number - first).sort((a, b) => a - b)).toEqual(
    Array.from({ length: 25 }, (_, index) => index),
  );
  expect(science.filter((question) => question.subjectCode === "SINH").map((question) => question.number - first).sort((a, b) => a - b)).toEqual(
    Array.from({ length: 25 }, (_, index) => 25 + index),
  );
  await expect(preview.getByText(/Đề thử · Q[DĐ]02/)).toBeVisible();

  // 5. Công bố: hộp thoại chọn đủ năm mã hợp lệ, máy chủ tạo v1 cho cả năm đường.
  await page.goto(`${ADMIN_URL}/exams/${id}/versions`);
  await page.getByRole("button", { name: "Công bố", exact: true }).click();
  const dialog = page.getByRole("dialog");
  await expect(dialog).toBeVisible();
  const [published] = await Promise.all([
    page.waitForResponse((item) => item.url().endsWith(`/exams/${id}/publish`) && item.request().method() === "POST", {
      timeout: SOLVER_TIMEOUT_MS,
    }),
    dialog.getByRole("button", { name: /^Công bố \d+ đường lựa chọn$/ }).click(),
  ]);
  expect(published.status(), await published.text()).toBe(200);
  const response = (await published.json()) as { version: number; publishedPathIds: string[] };
  expect(response.version).toBe(1);
  expect([...response.publishedPathIds].sort()).toEqual(check.paths.map((path) => path.pathId).sort());
  await expect.poll(async () => (await getExam(admin, id)).servingVersion).toBe(1);
});
