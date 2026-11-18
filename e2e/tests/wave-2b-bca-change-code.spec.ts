import { expect, test } from "@playwright/test";
import { adminApi, loginAdmin } from "./helpers/editor-qa";
import {
  blockingCodes,
  checkExam,
  clickAndCapturePreview,
  countBySubject,
  createExam,
  examCode,
  expectWellFormedPaper,
  getExam,
  generateInUi,
  pathWithChoice,
  paperQuestions,
  saveStructure,
  SCENARIO_TIMEOUT_MS,
} from "./helpers/wave-2b";

test.setTimeout(SCENARIO_TIMEOUT_MS);

/**
 * Nghiệm thu 2B mục 16, kịch bản 3: BCA đổi mã khi sinh thử (2B mục 8, R-15). Đề thử mã CA1 (Vật lí) đổi sang CA3
 * (Sinh học) trên cùng đề: chỉ phần 3 sinh lại, phần 1 và 2 giữ nguyên từng câu.
 */
test("BCA: đổi mã CA1 sang CA3 khi sinh thử, phần 1–2 giữ nguyên câu, phần 3 sinh lại", async ({ context, page }) => {
  await loginAdmin(context, "vi");
  const admin = await adminApi();
  const exam = await createExam(admin, { kind: "BCA", scope: "FULL", code: examCode("W2B-BCA"), name: "BCA đổi mã nghiệm thu 2B" });
  expect(exam.templateCode).toBe("BCA_FULL");
  expect(exam.questionCount).toBe(81);
  expect(exam.maxScore).toBe(100);
  await saveStructure(admin, exam.id);
  expect((await getExam(admin, exam.id)).questionCount).toBe(81);

  const check = await checkExam(admin, exam.id);
  expect(check.status, `lỗi chặn: ${blockingCodes(check).join(", ")}`).toBe("VALID");
  const ca1 = pathWithChoice(check, "CA1");
  const ca3 = pathWithChoice(check, "CA3");
  expect(ca1.pathId).not.toBe(ca3.pathId);

  // Sinh đề thử cho CA1 ở giao diện.
  const first = await generateInUi(page, exam.id, ca1.pathId);
  expect(first.request.target).toEqual({ kind: "PATH", pathId: ca1.pathId });
  expectWellFormedPaper(first.result);
  expect(first.result.previewId).toBeTruthy();
  expect(first.result.paper.parts).toHaveLength(3);
  expect(first.result.paper.totalQuestions).toBe(81);
  expect(first.result.paper.totalPoints).toBe(100);
  expect(countBySubject(first.result, 2)).toEqual({ LY: 15 });

  // Đổi sang CA3 trên cùng đề thử: nút "Đổi mã, sinh lại <phần>" gửi basePreviewId và đúng một phần cần sinh lại.
  await first.panel.getByRole("combobox", { name: "Đường lựa chọn" }).click();
  await page.getByRole("option", { name: /CA3/ }).click();
  const second = await clickAndCapturePreview(page, exam.id, first.panel.getByRole("button", { name: /^Đổi mã, sinh lại/ }));

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
  expect(codesOf(second.result, 0)).toEqual(codesOf(first.result, 0));
  expect(codesOf(second.result, 1)).toEqual(codesOf(first.result, 1));
  expect(codesOf(second.result, 2)).not.toEqual(codesOf(first.result, 2));
  expect(second.result.path?.choices?.map((choice) => choice.code)).toContain("CA3");
});
