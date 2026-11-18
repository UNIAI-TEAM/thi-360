import { expect, test } from "@playwright/test";
import { adminApi, loginAdmin } from "./helpers/editor-qa";
import {
  blockingCodes,
  continueToReviewInUi,
  countBySubject,
  createExamInUi,
  examCode,
  expectStructureQuestions,
  expectValidPaths,
  expectWellFormedPaper,
  generateInUi,
  getExam,
  getStructure,
  renameFirstBlockAndSaveInUi,
  saveStructureInUi,
  SCENARIO_TIMEOUT_MS,
} from "./helpers/wave-2b";

test.setTimeout(SCENARIO_TIMEOUT_MS);

/**
 * Nghiệm thu 2B mục 16, kịch bản 2: HSA theo môn và bài ôn HSA. Tạo ở bước 1 → mở và lưu ở bước 2 → kiểm tra → sinh thử,
 * tất cả ở giao diện.
 */
test.describe("HSA theo môn và bài ôn", () => {
  test("HSA theo môn Toán: mẫu môn 50 câu, lưu, kiểm tra, sinh thử", async ({ context, page }) => {
    await loginAdmin(context, "vi");
    const id = await createExamInUi(page, {
      kind: "HSA",
      scope: "SUBJECT",
      subject: "TOAN",
      code: examCode("W2B-HSA-TOAN"),
      name: "HSA theo môn Toán nghiệm thu 2B",
    });
    const admin = await adminApi();
    const exam = await getExam(admin, id);
    expect(exam.templateCode).toBe("HSA_SUBJECT_TOAN");
    expect(exam.questionCount).toBe(50);
    expect(exam.maxScore).toBe(50);

    // Bước 2: mở, sửa tên một nhóm, lưu; cấu trúc đọc lại vẫn một phần một môn 50 câu.
    await expectStructureQuestions(page, /^50(\D|$)/);
    await renameFirstBlockAndSaveInUi(page, admin, id);
    const saved = await getStructure(admin, id);
    expect(saved.parts).toHaveLength(1);
    expect(saved.parts[0]!.sections?.map((section) => [section.subjectCode, section.questionCount])).toEqual([["TOAN", 50]]);

    const check = await continueToReviewInUi(page, id);
    expect(check.status, `lỗi chặn: ${blockingCodes(check).join(", ")}`).toBe("VALID");
    await expectValidPaths(page, 1, 1);

    const { request, result, panel } = await generateInUi(page, id);
    expect(request.target).toEqual({ kind: "EXAM" });
    expectWellFormedPaper(result);
    expect(result.paper.totalQuestions).toBe(50);
    expect(result.paper.totalPoints).toBe(50);
    expect(countBySubject(result)).toEqual({ TOAN: 50 });
    await expect(panel.getByText(/^50 câu · 50 điểm/)).toBeVisible();
  });

  test("Bài ôn HSA Toán: chọn dạng câu, lưu, kiểm tra và sinh thử đúng số câu", async ({ context, page }) => {
    await loginAdmin(context, "vi");
    // Bước 1: "Tự thiết lập bài ôn" nhập số câu và thời lượng vào bảng tổng quan; thang điểm bỏ trống (tùy chọn, bài ôn chấm
    // LINEAR). Không ghi cấu trúc bằng API (FU-FE3B-V12 là chỗ bài ôn có thể sai ở kiểm tra, sinh thử; nếu sai thì giữ
    // khẳng định trung thực).
    const id = await createExamInUi(page, {
      kind: "HSA",
      scope: "SUBJECT",
      subject: "TOAN",
      review: true,
      code: examCode("W2B-HSA-ON"),
      name: "Bài ôn HSA Toán nghiệm thu 2B",
      overview: { questionCount: 10, durationMinutes: 15 },
    });
    const admin = await adminApi();
    const exam = await getExam(admin, id);
    expect(exam.templateCode).toBe("HSA_REVIEW");
    // Bước 1 đã lưu số câu và thời lượng đã nhập.
    expect([exam.questionCount, exam.durationMinutes]).toEqual([10, 15]);
    const skeleton = await getStructure(admin, id);
    expect(skeleton.parts).toHaveLength(1);
    expect(skeleton.parts[0]!.sections).toHaveLength(1);
    expect(skeleton.parts[0]!.sections![0]!.subjectCode).toBe("TOAN");
    expect(skeleton.parts[0]!.sections![0]!.blocks).toHaveLength(1);

    // Bước 2: màn Bài ôn nạp số câu của bước 1. Người dùng chọn dạng câu của nhóm là "Một đáp án" ở ô Dạng câu rồi bấm Lưu;
    // không ghi trường cấu trúc nào khác (số câu giữ như bước 1; bài ôn không đặt điểm mỗi câu).
    await expectStructureQuestions(page, /^10(\D|$)/);
    const typeField = page.getByRole("combobox", { name: /^Dạng câu/ });
    if (!(await typeField.isVisible())) await page.getByRole("navigation", { name: "Cấu trúc" }).getByRole("button", { name: /^Môn Toán học/ }).click();
    await expect(page.getByRole("heading", { name: /^Bài ôn · Toán học · 10 câu/ })).toBeVisible();
    await typeField.click();
    await page.getByRole("option", { name: "Một đáp án", exact: true }).click();
    await expect(page.getByText("Có thay đổi chưa lưu")).toBeVisible();
    await saveStructureInUi(page, id);
    const savedBlock = (await getStructure(admin, id)).parts[0]!.sections![0]!.blocks![0]!;
    expect([savedBlock.questionType, savedBlock.questionCount]).toEqual(["SINGLE_CHOICE", 10]);

    // "Tiếp tục": kiểm tra cả bài như người dùng nhận.
    const check = await continueToReviewInUi(page, id);
    expect(check.status, `lỗi chặn: ${blockingCodes(check).join(", ")}`).toBe("VALID");
    await expectValidPaths(page, 1, 1);

    const { request, result } = await generateInUi(page, id);
    expect(request.target).toEqual({ kind: "EXAM" });
    const questions = expectWellFormedPaper(result);
    expect(result.paper.totalQuestions).toBe(10);
    expect(questions.every((question) => question.subjectCode === "TOAN")).toBe(true);
  });
});
