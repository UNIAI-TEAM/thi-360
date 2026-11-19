import { expect, test } from "@playwright/test";
import { adminApi, loginAdmin } from "./helpers/editor-qa";
import {
  blockingCodes,
  clickAndCapturePreview,
  continueToReviewInUi,
  countBySubject,
  createExamInUi,
  examCode,
  expectStructureQuestions,
  expectValidPaths,
  expectWellFormedPaper,
  generateInUi,
  getExam,
  paperQuestions,
  pathWithChoice,
  renameFirstBlockAndSaveInUi,
  SCENARIO_TIMEOUT_MS,
  selectPathInUi,
} from "./helpers/wave-2b";

test.setTimeout(SCENARIO_TIMEOUT_MS);

/**
 * Nghiệm thu 2B mục 16, kịch bản 3: BCA đổi mã khi sinh thử (2B mục 8, R-15). Đề thử mã CA1 (Vật lí) đổi sang CA3
 * (Sinh học) trên cùng đề: chỉ phần 3 sinh lại, phần 1 và 2 giữ nguyên từng câu. Bước 1, bước 2, kiểm tra, sinh thử và đổi
 * mã đều ở giao diện.
 */
test("BCA: đổi mã CA1 sang CA3 khi sinh thử, phần 1–2 giữ nguyên câu, phần 3 sinh lại", async ({ context, page }) => {
  await loginAdmin(context, "vi");

  // Bước 1: tạo bài BCA toàn bài bằng mẫu mặc định.
  const id = await createExamInUi(page, { kind: "BCA", scope: "FULL", code: examCode("W2B-BCA"), name: "BCA đổi mã nghiệm thu 2B" });
  const admin = await adminApi();
  const exam = await getExam(admin, id);
  expect(exam.templateCode).toBe("BCA_FULL");
  expect(exam.questionCount).toBe(81);
  expect(exam.maxScore).toBe(100);

  // Bước 2: mở, sửa một giá trị (tên nhóm), lưu, rồi "Tiếp tục" kiểm tra cả bài.
  await expectStructureQuestions(page, /^81(\D|$)/);
  await renameFirstBlockAndSaveInUi(page, admin, id);
  expect((await getExam(admin, id)).questionCount).toBe(81);
  const check = await continueToReviewInUi(page, id);
  expect(check.status, `lỗi chặn: ${blockingCodes(check).join(", ")}`).toBe("VALID");
  await expectValidPaths(page, check.paths.length, check.paths.length);
  const ca1 = pathWithChoice(check, "CA1");
  const ca3 = pathWithChoice(check, "CA3");
  expect(ca1.pathId).not.toBe(ca3.pathId);

  // Sinh đề thử cho CA1 ở giao diện.
  const first = await generateInUi(page, id, /CA1/);
  expect(first.request.target).toEqual({ kind: "PATH", pathId: ca1.pathId });
  expectWellFormedPaper(first.result);
  expect(first.result.previewId).toBeTruthy();
  expect(first.result.paper.parts).toHaveLength(3);
  expect(first.result.paper.totalQuestions).toBe(81);
  expect(first.result.paper.totalPoints).toBe(100);
  expect(countBySubject(first.result, 2)).toEqual({ LY: 15 });

  // Đổi sang CA3 trên cùng đề thử: nút "Đổi mã, sinh lại <phần>" gửi basePreviewId và đúng một phần cần sinh lại.
  await selectPathInUi(page, first.panel, /CA3/);
  const second = await clickAndCapturePreview(page, id, first.panel.getByRole("button", { name: /^Đổi mã, sinh lại/ }));

  const part3Id = first.result.paper.parts[2]!.partId;
  expect(second.request.basePreviewId).toBe(first.result.previewId);
  expect(second.request.regeneratePartIds).toEqual([part3Id]);
  expect(second.request.seed).toBe(first.result.seed);
  expect(second.request.target).toEqual({ kind: "PATH", pathId: ca3.pathId });

  expectWellFormedPaper(second.result);
  expect(second.result.paper.totalQuestions).toBe(81);
  expect(second.result.paper.totalPoints).toBe(100);
  expect(countBySubject(second.result, 2)).toEqual({ SINH: 15 });
  const codesOf = (result: typeof first.result, partIndex: number) =>
    paperQuestions(result)
      .filter((question) => question.partIndex === partIndex)
      .map((question) => question.code);
  // Mẫu BCA_FULL: phần 1 Văn tự luận 1 câu, phần 2 trắc nghiệm bắt buộc 65 câu, phần 3 tự chọn 15 câu.
  expect(codesOf(first.result, 0)).toHaveLength(1);
  expect(codesOf(first.result, 1)).toHaveLength(65);
  expect(codesOf(second.result, 0)).toEqual(codesOf(first.result, 0));
  expect(codesOf(second.result, 1)).toEqual(codesOf(first.result, 1));
  expect(codesOf(second.result, 2)).not.toEqual(codesOf(first.result, 2));
  expect(second.result.path?.choices?.map((choice) => choice.code)).toContain("CA3");
});
