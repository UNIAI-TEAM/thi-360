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
    await expect(page.getByRole("heading", { name: new RegExp(`^Môn ${name}`) }), `môn ${name}`).toBeVisible();
    await expect(page.getByRole("textbox", { name: /^Số câu của mục/ }), `môn ${name}`).toHaveValue(String(count));
    // Mẫu không khai chỉ tiêu dạng trả lời: màn nói rõ cho cả sáu môn.
    await expect(page.getByText("Môn không kiểm soát số câu theo dạng trả lời."), `môn ${name}`).toBeVisible();
    if (matrix) {
      // Môn khai chỉ tiêu khối lớp (và độ khó nếu có): khối "Khối lớp và độ khó" hiện ma trận với đúng số của mẫu.
      await expect(page.getByRole("heading", { name: "Khối lớp và độ khó" }), `môn ${name}`).toBeVisible();
      await expect(page.getByText("Môn không kiểm soát phân bổ khối lớp và độ khó."), `môn ${name}`).toHaveCount(0);
      const region = page.getByRole("region", { name: "Ma trận phân bổ" });
      await expect(region, `môn ${name}`).toBeVisible();
      // Bảng "Theo khối lớp" có bốn ô: Lớp 10, 11, 12 và "Liên lớp". Ba ô lớp theo tên ô, ô "Liên lớp" bằng giá trị của mẫu (0 nếu mẫu không khai).
      const byGrade = section.matrix!.byGrade!;
      // Mã khối trong mẫu: G10/G11/G12; ô "Liên lớp" là GRADE_MIXED (mẫu không khai thì 0).
      const gradeItems = byGrade.filter((item) => /^G\d+$/.test(item.code));
      const mixedValue = String(byGrade.find((item) => item.code === "GRADE_MIXED")?.value ?? 0);
      const gradeTable = region.getByRole("table", { name: "Theo khối lớp" });
      const gradeInputs = gradeTable.getByRole("textbox", { name: /^Lớp \d+ · số câu/ });
      await expect(gradeInputs, `môn ${name}: chỉ tiêu theo khối lớp`).toHaveCount(gradeItems.length);
      for (const item of gradeItems) {
        const grade = item.code.replace(/^G(RADE_)?/, "");
        await expect(
          gradeTable.getByRole("textbox", { name: new RegExp(`^Lớp ${grade} · số câu`) }),
          `môn ${name}: khối lớp ${grade}`,
        ).toHaveValue(String(item.value));
      }
      await expect(gradeTable.getByRole("textbox", { name: /^Liên lớp · số câu/ }), `môn ${name}: liên lớp`).toHaveValue(mixedValue);
      // Độ khó: chỉ Hóa và Sử khai; các số khác 0 trên màn là đúng các số khác 0 của mẫu (ô không khai hiện 0).
      const difficultyValues = (section.matrix!.byDifficulty ?? []).map((item) => String(item.value)).filter((value) => value !== "0").sort();
      const difficultyInputs = region.getByRole("table", { name: "Theo độ khó" }).getByRole("textbox");
      const shown = (await difficultyInputs.evaluateAll((inputs) => inputs.map((input) => (input as HTMLInputElement).value))).filter(
        (value) => value !== "0",
      );
      expect(shown.sort(), `môn ${name}: chỉ tiêu theo độ khó`).toEqual(difficultyValues);
    } else {
      await expect(page.getByText("Môn không kiểm soát phân bổ khối lớp và độ khó."), `môn ${name}`).toBeVisible();
      await expect(page.getByRole("heading", { name: "Khối lớp và độ khó" }), `môn ${name}`).toBeVisible();
      await expect(page.getByRole("region", { name: "Ma trận phân bổ" }), `môn ${name}`).toHaveCount(0);
    }
    // Nhóm lấy câu: mẫu khai đúng các nhóm trên (tên, số câu), tổng bằng số câu của mục; màn có đúng từng dòng và không thừa dòng.
    expect(
      (section.blocks ?? []).map((block) => [block.name, block.questionCount]),
      `mẫu QDA_FULL: nhóm lấy câu của ${name}`,
    ).toEqual(groups.map((group) => [group.name, group.questions]));
    expect(groups.reduce((total, group) => total + group.questions, 0), `môn ${name}: tổng số câu của các nhóm`).toBe(count);
    const groupTable = page.getByRole("region", { name: `Các nhóm lấy câu của ${name}` }).getByRole("table", { name: `Các nhóm lấy câu của ${name}` });
    // Một dòng tiêu đề cột và mỗi nhóm một dòng.
    await expect(groupTable.getByRole("row"), `môn ${name}: số dòng nhóm`).toHaveCount(groups.length + 1);
    for (const group of groups) {
      const row = groupTable.getByRole("row").filter({ has: page.getByRole("rowheader", { name: group.name, exact: true }) });
      const questionsText = group.sets
        ? `${group.sets.count} chùm × ${group.sets.size} câu con = ${group.questions} câu`
        : `${group.questions} câu`;
      // Các ô của dòng theo thứ tự: Dạng trả lời, Số câu, Tỷ lệ, Phạm vi nguồn, Thao tác.
      await expect(row.getByRole("cell").nth(1), `môn ${name}: nhóm ${group.name}, số câu`).toHaveText(questionsText);
      await expect(row.getByRole("cell").nth(2), `môn ${name}: nhóm ${group.name}, tỷ lệ`).toHaveText(`${Math.round((group.questions * 100) / count)}%`);
    }
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
