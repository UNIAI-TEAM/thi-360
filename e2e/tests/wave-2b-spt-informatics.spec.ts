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
 * Nghiệm thu 2B mục 16, kịch bản 4: SPT Tin học, hai nhánh (K2). Mẫu có 20 câu trắc nghiệm chung, 1 câu Đúng/Sai chung và
 * một câu Đúng/Sai riêng mỗi nhánh (Khoa học máy tính, Tin học ứng dụng); phần tự luận tạm bỏ qua. Mỗi nhánh 22 câu, 7 điểm.
 * Bước 1, bước 2, kiểm tra và sinh thử ở giao diện; đổi nhánh ngay ở tab Đề xem trước.
 */
test("SPT Tin học: sinh thử cả hai nhánh, mỗi nhánh 22 câu", async ({ context, page }) => {
  await loginAdmin(context, "vi");
  const id = await createExamInUi(page, {
    kind: "SPT",
    scope: "SUBJECT",
    subject: "INFORMATICS",
    code: examCode("W2B-SPT-TIN"),
    name: "SPT Tin học nghiệm thu 2B",
  });
  const admin = await adminApi();
  expect((await getExam(admin, id)).templateCode).toBe("SPT_INFORMATICS_2025");

  // Mẫu in cả câu của hai nhánh nên tổng ở bước 2 có thể ghi "câu làm (đề ghi …)": chỉ cần có số.
  await expectStructureQuestions(page, /^\d+(\D|$)/);
  await renameFirstBlockAndSaveInUi(page, admin, id);

  const check = await continueToReviewInUi(page, id);
  expect(check.status, `lỗi chặn: ${blockingCodes(check).join(", ")}`).toBe("VALID");
  expect(check.paths).toHaveLength(2);
  expect(check.paths.every((path) => path.totalQuestions === 22)).toBe(true);
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
    // Mẫu in cả câu của hai nhánh, nhưng đề của một nhánh chỉ có câu của nhánh đó.
    expect(result.paper.totalQuestions, `nhánh ${branch.code}`).toBe(22);
    expect(result.paper.totalPoints, `nhánh ${branch.code}`).toBe(7);
    expect(result.paper.parts, `nhánh ${branch.code}`).toHaveLength(2);
    expect(questions.filter((question) => question.partIndex === 0), `nhánh ${branch.code}: phần I`).toHaveLength(20);
    expect(questions.filter((question) => question.partIndex === 1), `nhánh ${branch.code}: phần II`).toHaveLength(2);
    expect(countBySubject(result), `nhánh ${branch.code}`).toEqual({ INFORMATICS: 22 });
    expect(result.paper.hasManualGrading, `nhánh ${branch.code}: phần tự luận đã tạm bỏ qua`).toBeFalsy();
    expect(result.path?.choices?.map((choice) => choice.code), `nhánh ${branch.code}`).toContain(branch.code);
  }
});
