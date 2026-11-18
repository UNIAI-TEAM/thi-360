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
 * Nghiệm thu bổ sung V-ACT/THPTQG, kịch bản 8: THPTQG Tin học (K14, mẫu THPTQG_INFORMATICS_2025). Đề in 30 câu; học viên
 * làm 24 trắc nghiệm, 2 Đúng/Sai chung và 2 Đúng/Sai của nhánh đã chọn: mỗi nhánh 28 câu, 10 điểm.
 */
test("THPTQG Tin học: sinh thử từng nhánh, mỗi nhánh 28 câu và 10 điểm", async ({ context, page }) => {
  await loginAdmin(context, "vi");
  const admin = await adminApi();
  const exam = await createExam(admin, {
    kind: "THPTQG",
    scope: "SUBJECT",
    subjectCode: "INFORMATICS",
    code: examCode("W2B-TQG-TIN"),
    name: "THPTQG Tin học nghiệm thu 2B",
  });
  expect(exam.templateCode).toBe("THPTQG_INFORMATICS_2025");
  expect(exam.maxScore).toBe(10);
  await saveStructure(admin, exam.id);

  const check = await checkExam(admin, exam.id);
  expect(check.status, `lỗi chặn: ${blockingCodes(check).join(", ")}`).toBe("VALID");
  expect(check.paths).toHaveLength(2);
  expect(check.paths.every((path) => path.totalQuestions === 28)).toBe(true);

  for (const branch of ["CS", "ICT"]) {
    const path = pathWithChoice(check, branch);
    const { request, result } = await generateInUi(page, exam.id, path.pathId);
    expect(request.target, `nhánh ${branch}`).toEqual({ kind: "PATH", pathId: path.pathId });
    const questions = expectWellFormedPaper(result);
    expect(result.paper.totalQuestions, `nhánh ${branch}`).toBe(28);
    expect(result.paper.totalPoints, `nhánh ${branch}`).toBe(10);
    expect(result.paper.parts, `nhánh ${branch}`).toHaveLength(2);
    // Phần I: 24 trắc nghiệm 0,25 điểm = 6; phần II: 2 Đúng/Sai chung và 2 của nhánh, mỗi câu 1 điểm = 4.
    expect(questions.filter((question) => question.partIndex === 0), `nhánh ${branch}: phần I`).toHaveLength(24);
    expect(questions.filter((question) => question.partIndex === 1), `nhánh ${branch}: phần II`).toHaveLength(4);
    expect(result.paper.parts[0]!.points, `nhánh ${branch}: điểm phần I`).toBeCloseTo(6, 5);
    expect(result.paper.parts[1]!.points, `nhánh ${branch}: điểm phần II`).toBeCloseTo(4, 5);
    expect(countBySubject(result), `nhánh ${branch}`).toEqual({ INFORMATICS: 28 });
    expect(result.path?.choices?.map((choice) => choice.code), `nhánh ${branch}`).toContain(branch);
  }
});
