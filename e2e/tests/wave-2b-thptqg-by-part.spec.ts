import { expect, test } from "@playwright/test";
import { adminApi, loginAdmin } from "./helpers/editor-qa";
import {
  continueToReviewInUi,
  blockingCodes,
  examCode,
  expectStructureQuestions,
  fillExamStep1InUi,
  generateInUi,
  getExam,
  getStructure,
  partCardNames,
  renameFirstBlockAndSaveInUi,
  submitExamStep1InUi,
  expectWellFormedPaper,
  SCENARIO_TIMEOUT_MS,
  type UiExamSpec,
} from "./helpers/wave-2b";

test.setTimeout(SCENARIO_TIMEOUT_MS);

/** Bước 1 của bài THPTQG theo phần: phạm vi "Theo phần", môn chọn sau phạm vi. */
const byPart = (subject: string, code: string, name: string): UiExamSpec => ({
  kind: "THPTQG",
  scope: "SUBJECT",
  scopeLabel: /^Theo phần/,
  subject,
  code: examCode(code),
  name,
});

/**
 * Nghiệm thu bổ sung (D25, FU-QA-D25-ROW6): THPTQG theo phần trên máy chủ thật. Ngữ văn và Tiếng Anh giữ một phần nên
 * nạp sẵn thời lượng của mẫu (120, 50 phút); Toán tách ba phần, thời lượng để trống và bắt buộc nhập (H35, R-111).
 */
test("THPTQG Ngữ văn theo phần: thời lượng nạp sẵn 120, tạo và lưu bước 2", async ({ context, page }) => {
  await loginAdmin(context, "vi");
  const spec = byPart("VAN", "W2B-TQG-VANP", "THPTQG Ngữ văn theo phần nghiệm thu 2B");
  await fillExamStep1InUi(page, spec);

  expect(await partCardNames(page)).toEqual(["Ngữ văn"]);
  await expect(page.getByRole("textbox", { name: "Thời lượng của Ngữ văn" })).toHaveValue("120");
  await expect(page.getByRole("textbox", { name: "Số câu của Ngữ văn" })).toHaveValue("7");
  await expect(page.getByRole("textbox", { name: "Điểm của Ngữ văn" })).toHaveValue("10");

  const id = await submitExamStep1InUi(page, spec);
  const admin = await adminApi();
  const exam = await getExam(admin, id);
  expect(exam.scope).toBe("PART");
  expect(exam.questionCount).toBe(7);
  expect(exam.maxScore).toBe(10);
  // THPTQG Ngữ văn theo phần có `timing = PER_PART`: đồng hồ nằm ở phần, không ở bài.
  expect(exam.durationMinutes).toBeNull();
  expect((await getStructure(admin, id)).parts[0]!.durationMinutes).toBe(120);

  await expectStructureQuestions(page, /^7(\D|$)/);
  await renameFirstBlockAndSaveInUi(page, admin, id);
  expect((await getStructure(admin, id)).parts).toHaveLength(1);
});

test("THPTQG Tiếng Anh theo phần: thời lượng nạp sẵn 50, kiểm tra và sinh thử", async ({ context, page }) => {
  await loginAdmin(context, "vi");
  const spec = byPart("ANH", "W2B-TQG-ANHP", "THPTQG Tiếng Anh theo phần nghiệm thu 2B");
  await fillExamStep1InUi(page, spec);

  expect(await partCardNames(page)).toEqual(["Tiếng Anh"]);
  await expect(page.getByRole("textbox", { name: "Thời lượng của Tiếng Anh" })).toHaveValue("50");
  await expect(page.getByRole("textbox", { name: "Số câu của Tiếng Anh" })).toHaveValue("40");

  const id = await submitExamStep1InUi(page, spec);
  const admin = await adminApi();
  const exam = await getExam(admin, id);
  expect(exam.scope).toBe("PART");
  expect(exam.questionCount).toBe(40);
  // `timing = PER_PART`: đồng hồ nằm ở phần, không ở bài.
  expect(exam.durationMinutes).toBeNull();
  expect((await getStructure(admin, id)).parts[0]!.durationMinutes).toBe(50);

  await renameFirstBlockAndSaveInUi(page, admin, id);
  const check = await continueToReviewInUi(page, id);
  expect(check.status, `lỗi chặn: ${blockingCodes(check).join(", ")}`).toBe("VALID");
  const { result } = await generateInUi(page, id);
  expectWellFormedPaper(result);
  expect(result.paper.totalQuestions).toBe(40);
});

test("THPTQG Toán theo phần: ba phần, thời lượng trống và bắt buộc nhập", async ({ context, page }) => {
  await loginAdmin(context, "vi");
  const spec = byPart("TOAN", "W2B-TQG-TOANP", "THPTQG Toán theo phần nghiệm thu 2B");
  await fillExamStep1InUi(page, spec);

  const cards = await partCardNames(page);
  expect(cards, `thẻ phần: ${cards.join(" | ")}`).toEqual(["Phần I · Trắc nghiệm", "Phần II · Đúng/Sai", "Phần III · Trả lời ngắn"]);
  const duration = page.getByRole("textbox", { name: "Thời lượng chung" });
  await expect(duration).toHaveValue("");

  // Để trống thì "Tiếp tục: Cấu trúc và ma trận" không gửi: ở lại bước 1, ô thời lượng báo sai kèm lời nhắc.
  await page.getByRole("textbox", { name: /Mã bài thi/ }).fill(spec.code);
  await page.getByRole("textbox", { name: /Tên bài thi/ }).fill(spec.name);
  await page.getByRole("button", { name: "Tiếp tục: Cấu trúc và ma trận" }).click();
  await expect(duration).toHaveAttribute("aria-invalid", "true");
  await expect(page.getByText("Chưa nhập thời lượng. Bài theo phần phải có thời lượng riêng.").first()).toBeVisible();
  await expect(page).toHaveURL(/\/exams\/new$/);

  await duration.fill("30");
  const id = await submitExamStep1InUi(page, spec);
  const admin = await adminApi();
  const exam = await getExam(admin, id);
  expect(exam.scope).toBe("PART");
  expect(exam.durationMinutes).toBe(30);
  expect(exam.questionCount).toBe(12);
  const structure = await getStructure(admin, id);
  expect(structure.parts).toHaveLength(1);
  expect(structure.parts[0]!.name).toBe("Phần I · Trắc nghiệm");
});
