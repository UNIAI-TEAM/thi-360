import { expect, test } from "@playwright/test";
import { adminApi, loginAdmin } from "./helpers/editor-qa";
import {
  blockingCodes,
  checkExam,
  countBySubject,
  createExam,
  examCode,
  expectWellFormedPaper,
  generateInUi,
  pathWithChoice,
  saveStructure,
  SCENARIO_TIMEOUT_MS,
} from "./helpers/wave-2b";

test.setTimeout(SCENARIO_TIMEOUT_MS);

/**
 * Nghiệm thu 2B mục 16, kịch bản 4: SPT Tin học, hai nhánh (K2). Mẫu có 20 câu trắc nghiệm chung, 1 câu Đúng/Sai chung và
 * một câu Đúng/Sai riêng mỗi nhánh (Khoa học máy tính, Tin học ứng dụng); phần tự luận tạm bỏ qua. Mỗi nhánh 22 câu, 7 điểm.
 */
test("SPT Tin học: sinh thử cả hai nhánh, mỗi nhánh 22 câu", async ({ context, page }) => {
  await loginAdmin(context, "vi");
  const admin = await adminApi();
  const exam = await createExam(admin, {
    kind: "SPT",
    scope: "SUBJECT",
    subjectCode: "INFORMATICS",
    code: examCode("W2B-SPT-TIN"),
    name: "SPT Tin học nghiệm thu 2B",
  });
  expect(exam.templateCode).toBe("SPT_INFORMATICS_2025");
  await saveStructure(admin, exam.id);

  const check = await checkExam(admin, exam.id);
  expect(check.status, `lỗi chặn: ${blockingCodes(check).join(", ")}`).toBe("VALID");
  expect(check.paths).toHaveLength(2);
  expect(check.paths.every((path) => path.totalQuestions === 22)).toBe(true);

  for (const branch of ["CS", "ICT"]) {
    const path = pathWithChoice(check, branch);
    const { request, result } = await generateInUi(page, exam.id, path.pathId);
    expect(request.target, `nhánh ${branch}`).toEqual({ kind: "PATH", pathId: path.pathId });
    const questions = expectWellFormedPaper(result);
    // Mẫu in cả câu của hai nhánh, nhưng đề của một nhánh chỉ có câu của nhánh đó.
    expect(result.paper.totalQuestions, `nhánh ${branch}`).toBe(22);
    expect(result.paper.totalPoints, `nhánh ${branch}`).toBe(7);
    expect(result.paper.parts, `nhánh ${branch}`).toHaveLength(2);
    expect(questions.filter((question) => question.partIndex === 0), `nhánh ${branch}: phần I`).toHaveLength(20);
    expect(questions.filter((question) => question.partIndex === 1), `nhánh ${branch}: phần II`).toHaveLength(2);
    expect(countBySubject(result), `nhánh ${branch}`).toEqual({ INFORMATICS: 22 });
    expect(result.paper.hasManualGrading, `nhánh ${branch}: phần tự luận đã tạm bỏ qua`).toBeFalsy();
    expect(result.path?.choices?.map((choice) => choice.code), `nhánh ${branch}`).toContain(branch);
  }
});
