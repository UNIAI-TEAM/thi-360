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
  pathWithChoice,
  renameFirstBlockAndSaveInUi,
  SCENARIO_TIMEOUT_MS,
  switchPathInUi,
} from "./helpers/wave-2b";

test.setTimeout(SCENARIO_TIMEOUT_MS);

/** Hai nhánh của mẫu: mã nhánh và tên hiện ở ô "Đường lựa chọn" của tab Đề xem trước. */
const BRANCHES = [
  { code: "CS", label: /Khoa học máy tính/ },
  { code: "ICT", label: /Tin học ứng dụng/ },
] as const;

/**
 * Nghiệm thu bổ sung V-ACT/THPTQG, kịch bản 8: THPTQG Tin học (K14, mẫu THPTQG_INFORMATICS_2025). Đề in 30 câu; học viên
 * làm 24 trắc nghiệm, 2 Đúng/Sai chung và 2 Đúng/Sai của nhánh đã chọn: mỗi nhánh 28 câu, 10 điểm. Bước 1, bước 2, kiểm
 * tra và sinh thử ở giao diện; đổi nhánh ngay ở tab Đề xem trước.
 */
test("THPTQG Tin học: sinh thử từng nhánh, mỗi nhánh 28 câu và 10 điểm", async ({ context, page }) => {
  await loginAdmin(context, "vi");
  const id = await createExamInUi(page, {
    kind: "THPTQG",
    scope: "SUBJECT",
    subject: "INFORMATICS",
    code: examCode("W2B-TQG-TIN"),
    name: "THPTQG Tin học nghiệm thu 2B",
  });
  const admin = await adminApi();
  const exam = await getExam(admin, id);
  expect(exam.templateCode).toBe("THPTQG_INFORMATICS_2025");
  expect(exam.maxScore).toBe(10);

  // Đề in 30 câu, học viên làm 28: tổng ở bước 2 ghi "câu làm (đề ghi …)"; chỉ cần có số.
  await expectStructureQuestions(page, /^\d+(\D|$)/);
  await renameFirstBlockAndSaveInUi(page, admin, id);

  const check = await continueToReviewInUi(page, id);
  expect(check.status, `lỗi chặn: ${blockingCodes(check).join(", ")}`).toBe("VALID");
  expect(check.paths).toHaveLength(2);
  expect(check.paths.every((path) => path.totalQuestions === 28)).toBe(true);
  await expectValidPaths(page, 2, 2);

  // Nhánh đầu: chọn rồi "Sinh đề xem trước"; nhánh sau: đổi nhánh trên cùng tab rồi "Sinh đề thử mới".
  const first = await generateInUi(page, id, BRANCHES[0].label);
  const second = await switchPathInUi(page, id, first.panel, BRANCHES[1].label, /^Sinh đề thử mới$/);
  for (const [branch, { request, result }] of [
    [BRANCHES[0], first],
    [BRANCHES[1], second],
  ] as const) {
    const path = pathWithChoice(check, branch.code);
    expect(request.target, `nhánh ${branch.code}`).toEqual({ kind: "PATH", pathId: path.pathId });
    const questions = expectWellFormedPaper(result);
    expect(result.paper.totalQuestions, `nhánh ${branch.code}`).toBe(28);
    expect(result.paper.totalPoints, `nhánh ${branch.code}`).toBe(10);
    expect(result.paper.parts, `nhánh ${branch.code}`).toHaveLength(2);
    // Phần I: 24 trắc nghiệm 0,25 điểm = 6; phần II: 2 Đúng/Sai chung và 2 của nhánh, mỗi câu 1 điểm = 4.
    expect(questions.filter((question) => question.partIndex === 0), `nhánh ${branch.code}: phần I`).toHaveLength(24);
    expect(questions.filter((question) => question.partIndex === 1), `nhánh ${branch.code}: phần II`).toHaveLength(4);
    expect(result.paper.parts[0]!.points, `nhánh ${branch.code}: điểm phần I`).toBeCloseTo(6, 5);
    expect(result.paper.parts[1]!.points, `nhánh ${branch.code}: điểm phần II`).toBeCloseTo(4, 5);
    expect(countBySubject(result), `nhánh ${branch.code}`).toEqual({ INFORMATICS: 28 });
    expect(result.path?.choices?.map((choice) => choice.code), `nhánh ${branch.code}`).toContain(branch.code);
  }
});
