import { expect, test } from "@playwright/test";
import { adminApi, loginAdmin } from "./helpers/editor-qa";
import {
  allSections,
  blockingCodes,
  clickAndCapturePreview,
  continueToReviewInUi,
  countBySubject,
  createExamInUi,
  examCode,
  expectStructureQuestions,
  expectValidPaths,
  expectWellFormedPaper,
  getExam,
  getStructure,
  openPreviewTab,
  pathWithChoice,
  publishInUi,
  saveStructureInUi,
  SCENARIO_TIMEOUT_MS,
  selectPathInUi,
  SOLVER_TIMEOUT_MS,
  SUBJECT_NAME,
} from "./helpers/wave-2b";

test.setTimeout(SCENARIO_TIMEOUT_MS);

/** Sáu môn của kho phần 3 theo mẫu QDA_FULL: số câu của mục (Lý, Hóa, Sinh, Sử, Địa 25 câu; Tiếng Anh 50 câu). */
const PART3_SUBJECTS = [
  { code: "LY", count: 25 },
  { code: "HOA", count: 25 },
  { code: "SINH", count: 25 },
  { code: "SU", count: 25 },
  { code: "DIA", count: 25 },
  { code: "ANH", count: 50 },
] as const;

/**
 * Nghiệm thu 2B mục 16, kịch bản 1: QDA toàn bài. Tạo ở bước 1 (mẫu mặc định QDA_FULL) → thiết lập sáu môn của phần 3 ở
 * bước 2 (mở từng môn, xem số câu và chỉ tiêu trên màn, sửa một giá trị, lưu, kiểm tra riêng từng môn) → kiểm tra toàn bài
 * → sinh thử QĐ02 → công bố. Mọi bước ở giao diện.
 */
test("QDA toàn bài: tạo, thiết lập sáu môn, kiểm tra toàn bài, sinh thử QĐ02, công bố", async ({ context, page }) => {
  await loginAdmin(context, "vi");

  // 1. Bước 1: kỳ thi QDA, toàn bài; bảng tổng quan nạp số của mẫu mặc định.
  const id = await createExamInUi(page, { kind: "QDA", scope: "FULL", code: examCode("W2B-QDA"), name: "QDA toàn bài nghiệm thu 2B" });
  const admin = await adminApi();
  const exam = await getExam(admin, id);
  expect(exam.templateCode).toBe("QDA_FULL");
  expect(exam.questionCount).toBe(150);
  expect(exam.maxScore).toBe(150);

  // 2. Bước 2: phần 3 có kho sáu môn. Mở từng môn trong danh sách: tiêu đề, số câu của mục, chỉ tiêu, nhóm lấy câu.
  await expectStructureQuestions(page, /^150(\D|$)/);
  const structure = await getStructure(admin, id);
  expect(structure.parts).toHaveLength(3);
  const pool = structure.parts[2]!.subjectPool ?? [];
  expect(pool.map((section) => section.subjectCode).sort()).toEqual(["ANH", "DIA", "HOA", "LY", "SINH", "SU"]);

  const nav = page.getByRole("navigation", { name: "Cấu trúc" });
  for (const { code, count } of PART3_SUBJECTS) {
    const name = SUBJECT_NAME[code]!;
    await nav.getByRole("button", { name: new RegExp(`^${name}`) }).click();
    await expect(page.getByRole("heading", { name: new RegExp(`^Môn ${name}`) }), `môn ${name}`).toBeVisible();
    await expect(page.getByRole("textbox", { name: /^Số câu của mục/ }), `môn ${name}`).toHaveValue(String(count));
    // Mẫu không khai chỉ tiêu dạng trả lời, khối lớp, độ khó: màn nói rõ; một nhóm trắc nghiệm lấy hết số câu của môn.
    await expect(page.getByText("Môn không kiểm soát số câu theo dạng trả lời."), `môn ${name}`).toBeVisible();
    await expect(page.getByText("Môn không kiểm soát phân bổ khối lớp và độ khó."), `môn ${name}`).toBeVisible();
    await expect(
      page.getByRole("region", { name: `Các nhóm lấy câu của ${name}` }).getByRole("row", { name: new RegExp(`${count} câu`) }),
      `môn ${name}`,
    ).toBeVisible();
  }

  // Sửa một giá trị của môn Vật lí (tên nhóm lấy câu) rồi lưu.
  const ly = pool.find((section) => section.subjectCode === "LY")!;
  const lyBlock = ly.blocks![0]!;
  await nav.getByRole("button", { name: new RegExp(`^${SUBJECT_NAME.LY}`) }).click();
  await page.getByRole("button", { name: `Mở nhóm ${lyBlock.name}` }).click();
  const renamed = `${lyBlock.name} Vật lí 2B`;
  await page.getByRole("textbox", { name: /^Tên nhóm/ }).fill(renamed);
  await expect(page.getByText("Có thay đổi chưa lưu")).toBeVisible();
  await saveStructureInUi(page, id);
  const savedLy = allSections(await getStructure(admin, id)).find((section) => section.id === ly.id);
  expect(savedLy?.blocks?.[0]?.name).toBe(renamed);

  // Kiểm tra riêng từng môn bằng nút "Kiểm tra môn": mỗi môn không lỗi chặn và hiện "Đã kiểm tra" trong danh sách.
  for (const { code } of PART3_SUBJECTS) {
    const name = SUBJECT_NAME[code]!;
    const subjectButton = nav.getByRole("button", { name: new RegExp(`^${name}`) });
    await subjectButton.click();
    await expect(page.getByRole("heading", { name: new RegExp(`^Môn ${name}`) }), `môn ${name}`).toBeVisible();
    const [response] = await Promise.all([
      page.waitForResponse((item) => item.url().endsWith(`/exams/${id}/check`) && item.request().method() === "POST", {
        timeout: SOLVER_TIMEOUT_MS,
      }),
      page.getByRole("button", { name: "Kiểm tra môn", exact: true }).click(),
    ]);
    expect(response.status(), `môn ${name}: ${await response.text()}`).toBe(200);
    expect(blockingCodes(await response.json()), `môn ${name}`).toEqual([]);
    await expect(subjectButton, `môn ${name}`).toContainText("Đã kiểm tra");
  }

  // 3. "Tiếp tục": kiểm tra toàn bài, sang tab Kiểm tra cấu hình: năm mã QĐ01–QĐ05, cả năm hợp lệ.
  const check = await continueToReviewInUi(page, id);
  expect(check.status, `lỗi chặn: ${blockingCodes(check).join(", ")}`).toBe("VALID");
  expect(blockingCodes(check)).toEqual([]);
  expect(check.paths).toHaveLength(5);
  expect(check.paths.every((path) => path.result === "VALID")).toBe(true);
  await expectValidPaths(page, 5, 5);
  const allStates = (await getStructure(admin, id)).sectionStates ?? [];
  expect(allStates).toHaveLength(allSections(await getStructure(admin, id)).length);
  expect(allStates.filter((state) => state.state !== "CHECKED")).toEqual([]);

  // 4. Sinh thử QĐ02 (Hóa học – Sinh học) ở tab Đề xem trước.
  const preview = await openPreviewTab(page);
  await expect(preview.getByText("Bài có phần chọn mã: hãy chọn một mã.")).toBeVisible();
  await selectPathInUi(page, preview, /Q[DĐ]02/);
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
  const published = await publishInUi(page, id);
  expect(published.version).toBe(1);
  expect([...published.publishedPathIds].sort()).toEqual(check.paths.map((path) => path.pathId).sort());
  await expect.poll(async () => (await getExam(admin, id)).servingVersion).toBe(1);
});
