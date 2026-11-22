import { expect, test } from "@playwright/test";
import { adminApi, loginAdmin } from "./helpers/editor-qa";
import {
  allSections,
  blockingCodes,
  clickAndCapturePreview,
  continueToReviewInUi,
  countBySubject,
  createExamInUi,
  ensureListingFilledInUi,
  examCode,
  expectStructureQuestions,
  expectValidPaths,
  expectWellFormedPaper,
  getExam,
  getStructure,
  openEditorTab,
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

/**
 * Một dòng của bảng "Các nhóm lấy câu" theo mẫu QDA_FULL: tên nhóm (ô tiêu đề dòng), số câu của nhóm và, với nhóm chùm,
 * số chùm × số câu con (ô "Số câu" ghi "1 chùm × 3 câu con = 3 câu").
 */
interface GroupRow {
  name: string;
  questions: number;
  sets?: { count: number; size: number };
}

/** Lý, Hóa, Sinh: 21 câu trắc nghiệm độc lập, 1 chùm × 3 câu con, 1 câu trả lời ngắn. */
const SCIENCE_GROUPS: GroupRow[] = [
  { name: "Trắc nghiệm độc lập", questions: 21 },
  { name: "Chùm", questions: 3, sets: { count: 1, size: 3 } },
  { name: "Trả lời ngắn", questions: 1 },
];

/** Sử, Địa: 22 câu trắc nghiệm độc lập, 1 chùm × 3 câu con. */
const SOCIAL_GROUPS: GroupRow[] = [
  { name: "Trắc nghiệm độc lập", questions: 22 },
  { name: "Chùm", questions: 3, sets: { count: 1, size: 3 } },
];

/** Tiếng Anh: mười nhóm, từ "Hoàn thành câu" đến "Suy luận", tổng 50 câu. */
const ENGLISH_GROUPS: GroupRow[] = [
  { name: "Hoàn thành câu", questions: 10 },
  { name: "Đồng nghĩa hoặc trái nghĩa", questions: 4 },
  { name: "Hội thoại", questions: 4 },
  { name: "Thông báo", questions: 4 },
  { name: "Viết lại câu", questions: 4 },
  { name: "Kết hợp câu", questions: 4 },
  { name: "Đọc điền", questions: 5, sets: { count: 1, size: 5 } },
  { name: "Đọc hiểu 1", questions: 5, sets: { count: 1, size: 5 } },
  { name: "Đọc hiểu 2", questions: 5, sets: { count: 1, size: 5 } },
  { name: "Suy luận", questions: 5 },
];

/**
 * Sáu môn của kho phần 3 theo mẫu QDA_FULL: số câu của mục (Lý, Hóa, Sinh, Sử, Địa 25 câu; Tiếng Anh 50 câu), các nhóm lấy
 * câu của mục và việc mẫu có khai chỉ tiêu khối lớp (`matrix.byGrade`, bắt buộc) hay không: năm môn khai, chỉ Tiếng Anh không khai.
 */
const PART3_SUBJECTS = [
  { code: "LY", count: 25, matrix: true, groups: SCIENCE_GROUPS },
  { code: "HOA", count: 25, matrix: true, groups: SCIENCE_GROUPS },
  { code: "SINH", count: 25, matrix: true, groups: SCIENCE_GROUPS },
  { code: "SU", count: 25, matrix: true, groups: SOCIAL_GROUPS },
  { code: "DIA", count: 25, matrix: true, groups: SOCIAL_GROUPS },
  { code: "ANH", count: 50, matrix: false, groups: ENGLISH_GROUPS },
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
  for (const { code, count, matrix, groups } of PART3_SUBJECTS) {
    const name = SUBJECT_NAME[code]!;
    const section = pool.find((item) => item.subjectCode === code)!;
    expect(Boolean(section.matrix), `mẫu QDA_FULL: môn ${name} khai chỉ tiêu khối lớp`).toBe(matrix);
    await nav.getByRole("button", { name: new RegExp(`^${name}`) }).click();
    const header = page.getByTestId("subject-form-header");
    await expect(header.getByRole("heading", { level: 2, name: new RegExp(`^Môn ${name}`) }), `môn ${name}`).toBeVisible();
    // Số câu của mục không còn ô riêng (UNI-2753): dòng "N câu" ngay dưới tiêu đề môn nêu số câu của mục.
    await expect(
      header.getByRole("heading", { level: 2, name: new RegExp(`^Môn ${name}`) }).locator("xpath=..").getByText(new RegExp(`^${count} câu`)),
      `môn ${name}`,
    ).toBeVisible();
    await expect(header.getByText(`${count} / ${count} câu`), `môn ${name}: đã phân bổ`).toBeVisible();
    await expect(header.getByText("Khớp số câu của môn"), `môn ${name}`).toBeVisible();

    expect(
      (section.blocks ?? []).map((block) => [block.name, block.questionCount]),
      `mẫu QDA_FULL: nhóm lấy câu của ${name}`,
    ).toEqual(groups.map((group) => [group.name, group.questions]));
    expect(groups.reduce((total, group) => total + group.questions, 0), `môn ${name}: tổng số câu của các nhóm`).toBe(count);

    if (matrix) {
      // Năm môn Khoa học/Xã hội (FTM MT): Cấu trúc câu, Khối lớp và độ khó, Phạm vi và chỉ tiêu chủ đề.
      await expect(page.locator('[data-form-kind="MATRIX"]'), `môn ${name}: biểu mẫu MT`).toBeVisible();
      await expect(page.getByRole("heading", { level: 3, name: "Cấu trúc câu" }), `môn ${name}`).toBeVisible();
      await expect(page.getByRole("button", { name: /Thêm nhóm/ }), `môn ${name}`).toHaveCount(0);

      const singleGroup = groups.find((group) => !group.sets && group.name !== "Trả lời ngắn");
      const setGroup = groups.find((group) => group.sets !== undefined);
      const fillGroup = groups.find((group) => group.name === "Trả lời ngắn");
      const expectedChoice = (singleGroup?.questions ?? 0) + (setGroup?.questions ?? 0);
      await expect(page.getByRole("textbox", { name: "Trắc nghiệm", exact: true }), `môn ${name}: Trắc nghiệm`).toHaveValue(String(expectedChoice));
      await expect(page.getByRole("textbox", { name: "Số chùm", exact: true }), `môn ${name}: Số chùm`).toHaveValue(String(setGroup!.sets!.count));
      await expect(page.getByRole("textbox", { name: "Trắc nghiệm đơn", exact: true }), `môn ${name}: Trắc nghiệm đơn`).toHaveValue(
        String(singleGroup!.questions),
      );
      if (fillGroup) {
        await expect(page.getByRole("textbox", { name: "Điền đáp án", exact: true }), `môn ${name}: Điền đáp án`).toHaveValue(
          String(fillGroup.questions),
        );
      } else {
        await expect(page.getByRole("textbox", { name: "Điền đáp án", exact: true }), `môn ${name}: không có Điền đáp án`).toHaveCount(0);
      }

      // Khối lớp và độ khó (MT): Khối lớp khai đủ 25/25 và Bắt buộc; Độ khó hiện các mức của mẫu (trống nếu chưa khai).
      await expect(page.getByRole("heading", { level: 3, name: "Khối lớp và độ khó" }), `môn ${name}`).toBeVisible();
      const gradeField = page.locator('[data-form-field="GRADE"]');
      await expect(gradeField, `môn ${name}: khối lớp`).toBeVisible();
      await expect(gradeField.locator('[data-enforcement-mode="DECLARED_REQUIRED"]')).toHaveText("Bắt buộc");
      await expect(gradeField.getByText(`Đã khai ${count}/${count}`), `môn ${name}: tổng khối lớp`).toBeVisible();
      const byGrade = section.matrix!.byGrade!;
      const gradeItems = byGrade.filter((item) => /^G\d+$/.test(item.code));
      for (const item of gradeItems) {
        const grade = item.code.replace(/^G(RADE_)?/, "");
        await expect(gradeField.getByLabel(`Lớp ${grade}`, { exact: true }), `môn ${name}: khối lớp ${grade}`).toHaveValue(String(item.value));
      }
      await expect(gradeField.getByLabel("Liên lớp", { exact: true }), `môn ${name}: không có Liên lớp`).toHaveCount(0);

      const difficultyField = page.locator('[data-form-field="DIFFICULTY"]');
      await expect(difficultyField, `môn ${name}: độ khó`).toBeVisible();
      const difficultyValues = (section.matrix!.byDifficulty ?? []).map((item) => String(item.value)).filter((value) => value !== "0").sort();
      await expect(difficultyField.locator('[data-enforcement-mode="DECLARED_REQUIRED"]')).toHaveText(
        difficultyValues.length > 0 ? "Bắt buộc" : "Chưa khai báo",
      );
      const difficultyInputs = difficultyField.getByRole("textbox");
      const shown = (await difficultyInputs.evaluateAll((inputs) => inputs.map((input) => (input as HTMLInputElement).value))).filter(
        (value) => value !== "" && value !== "0",
      );
      expect(shown.sort(), `môn ${name}: chỉ tiêu theo độ khó`).toEqual(difficultyValues);
      await expect(page.getByRole("heading", { level: 3, name: "Phạm vi và chỉ tiêu chủ đề" }), `môn ${name}`).toBeVisible();
    } else {
      // Tiếng Anh (FTM DS): Danh sách dòng lấy câu 10 dòng khớp mẫu + Phân bổ đối chiếu chưa khai báo.
      await expect(page.locator('[data-subject-form="ROWS"]'), `môn ${name}: biểu mẫu DS`).toBeVisible();
      await expect(page.getByRole("heading", { level: 3, name: "Các dòng lấy câu" }), `môn ${name}`).toBeVisible();
      await expect(page.locator("[data-form-row]"), `môn ${name}: số dòng nhóm`).toHaveCount(groups.length);
      for (const [index, group] of groups.entries()) {
        const block = section.blocks![index]!;
        const row = page.locator(`[data-form-row="${block.id}"]`);
        await expect(row.locator("[data-exam-content]"), `môn ${name}: tên dòng ${group.name}`).toHaveText(group.name);
        const questionsText = group.sets
          ? `${group.sets.count} chùm × ${group.sets.size} (${group.questions} câu)`
          : `${group.questions} câu`;
        await expect(row.getByRole("cell").nth(1), `môn ${name}: nhóm ${group.name}, số câu`).toContainText(questionsText);
      }
      const refBlock = page.locator("[data-reference-block]");
      await refBlock.locator("summary").click();
      await expect(refBlock.locator('[data-reference-dimension="GRADE"]')).toContainText("Chưa khai báo");
      await expect(refBlock.locator('[data-reference-dimension="DIFFICULTY"]')).toContainText("Chưa khai báo");
    }
  }

  // Sửa một giá trị của môn Vật lí trên MT (Số chùm 1 -> 2, lưu PUT 200 và kiểm tra API, rồi trả về 1 để kiểm tra/công bố chuẩn mẫu).
  const ly = pool.find((section) => section.subjectCode === "LY")!;
  await nav.getByRole("button", { name: new RegExp(`^${SUBJECT_NAME.LY}`) }).click();
  const setsInput = page.getByRole("textbox", { name: "Số chùm", exact: true });
  await setsInput.fill("2");
  await expect(page.getByRole("textbox", { name: "Trắc nghiệm đơn", exact: true })).toHaveValue("18");
  await expect(page.getByText("Có thay đổi chưa lưu")).toBeVisible();
  await saveStructureInUi(page, id);
  const savedLy = allSections(await getStructure(admin, id)).find((section) => section.id === ly.id)!;
  expect(savedLy.blocks?.find((block) => block.questionType === "QUESTION_SET")).toMatchObject({ setCount: 2, setSize: 3, questionCount: 6 });
  expect(savedLy.blocks?.find((block) => block.questionType === "SINGLE_CHOICE")).toMatchObject({ questionCount: 18 });

  await setsInput.fill("1");
  await expect(page.getByRole("textbox", { name: "Trắc nghiệm đơn", exact: true })).toHaveValue("21");
  await saveStructureInUi(page, id);
  const restoredLy = allSections(await getStructure(admin, id)).find((section) => section.id === ly.id)!;
  expect(restoredLy.blocks?.find((block) => block.questionType === "QUESTION_SET")).toMatchObject({ setCount: 1, setSize: 3, questionCount: 3 });

  // Điền thông tin bán trước khi chạy Kiểm tra môn và Kiểm tra toàn bài (phản hồi `/check` luôn đính kèm `listingCheck`).
  await ensureListingFilledInUi(page, id);
  await openEditorTab(page, "Cấu trúc và ma trận", /\/exams\/[^/]+\/structure/);
  await expectStructureQuestions(page, /^150(\D|$)/);

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
  // Tab Kiểm tra: "Môn đã kiểm tra 8/8" (Toán, Văn và sáu môn của kho); máy chủ ghi cả tám môn ở trạng thái đã kiểm tra.
  await expect(page.getByText("8/8", { exact: true })).toBeVisible();
  const allStates = (await getStructure(admin, id)).sectionStates ?? [];
  expect(allStates.map((state) => state.subjectCode).sort()).toEqual(["ANH", "DIA", "HOA", "LY", "SINH", "SU", "TOAN", "VAN"]);
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

  // 5. Công bố: hộp thoại phải liệt kê đủ năm đường QĐ01–QĐ05 (đã chọn) trước khi bấm; máy chủ tạo v1 cho cả năm đường.
  const published = await publishInUi(page, id, [1, 2, 3, 4, 5].map((number) => new RegExp(`^Q[DĐ]0${number} ·`)));
  expect(published.version).toBe(1);
  expect([...published.publishedPathIds].sort()).toEqual(check.paths.map((path) => path.pathId).sort());
  await expect.poll(async () => (await getExam(admin, id)).servingVersion).toBe(1);
});
