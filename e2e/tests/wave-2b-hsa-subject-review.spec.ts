import { expect, test } from "@playwright/test";
import { ADMIN_URL, adminApi, loginAdmin } from "./helpers/editor-qa";
import {
  blockingCodes,
  type CheckResult,
  checkExam,
  collectIssues,
  countBySubject,
  createExam,
  examCode,
  expectWellFormedPaper,
  generateInUi,
  getStructure,
  issueCodes,
  type PreviewResultLite,
  saveStructure,
  SCENARIO_TIMEOUT_MS,
  SOLVER_TIMEOUT_MS,
} from "./helpers/wave-2b";

test.setTimeout(SCENARIO_TIMEOUT_MS);

/**
 * Nghiệm thu 2B mục 16, kịch bản 2: HSA theo môn và bài ôn HSA. Tạo → lưu → sinh thử.
 */
test.describe("HSA theo môn và bài ôn", () => {
  test("HSA theo môn Toán: mẫu môn 50 câu, lưu, kiểm tra, sinh thử", async ({ context, page }) => {
    await loginAdmin(context, "vi");
    const admin = await adminApi();
    const exam = await createExam(admin, {
      kind: "HSA",
      scope: "SUBJECT",
      subjectCode: "TOAN",
      code: examCode("W2B-HSA-TOAN"),
      name: "HSA theo môn Toán nghiệm thu 2B",
    });
    expect(exam.templateCode).toBe("HSA_SUBJECT_TOAN");
    expect(exam.questionCount).toBe(50);
    expect(exam.maxScore).toBe(50);

    // Lưu bản mẫu nguyên vẹn: máy chủ nhận, cấu trúc đọc lại vẫn một phần một môn 50 câu.
    const saved = await saveStructure(admin, exam.id);
    expect(saved.parts).toHaveLength(1);
    expect(saved.parts[0]!.sections?.map((section) => [section.subjectCode, section.questionCount])).toEqual([["TOAN", 50]]);

    const check = await checkExam(admin, exam.id);
    expect(check.status, `lỗi chặn: ${blockingCodes(check).join(", ")}`).toBe("VALID");

    const { request, result } = await generateInUi(page, exam.id);
    expect(request.target).toEqual({ kind: "EXAM" });
    expectWellFormedPaper(result);
    expect(result.paper.totalQuestions).toBe(50);
    expect(result.paper.totalPoints).toBe(50);
    expect(countBySubject(result)).toEqual({ TOAN: 50 });
    await expect(page.getByRole("tabpanel", { name: "Đề xem trước" }).getByText(/^50 câu · 50 điểm/)).toBeVisible();
  });

  test("Bài ôn HSA Toán: đặt dạng câu và số lượng, lưu, sinh thử đúng số câu", async ({ context, page }) => {
    await loginAdmin(context, "vi");
    const admin = await adminApi();
    const exam = await createExam(admin, {
      kind: "HSA",
      scope: "SUBJECT",
      subjectCode: "TOAN",
      buildMode: "REVIEW",
      maxScore: 10,
      code: examCode("W2B-HSA-ON"),
      name: "Bài ôn HSA Toán nghiệm thu 2B",
    });
    expect(exam.templateCode).toBe("HSA_REVIEW");
    const skeleton = await getStructure(admin, exam.id);
    expect(skeleton.parts).toHaveLength(1);
    expect(skeleton.parts[0]!.sections).toHaveLength(1);
    expect(skeleton.parts[0]!.sections![0]!.subjectCode).toBe("TOAN");
    expect(skeleton.parts[0]!.sections![0]!.blocks).toHaveLength(1);

    // FU-FE3B-V12: bài ôn nguyên vẹn (một nhóm trống) làm gì ở kiểm tra và sinh thử; ghi lại, không khẳng định.
    const untouchedCheck = await admin.api.post(`${ADMIN_URL}/api/v1/admin/exams/${exam.id}/check`, {
      headers: admin.headers,
      data: { scope: "EXAM" },
      timeout: SOLVER_TIMEOUT_MS,
    });
    const untouchedResult: CheckResult | null = untouchedCheck.ok() ? await untouchedCheck.json() : null;
    const untouchedPreview = await admin.api.post(`${ADMIN_URL}/api/v1/admin/exams/${exam.id}/preview`, {
      headers: admin.headers,
      data: { target: { kind: "EXAM" }, seed: 1 },
      timeout: SOLVER_TIMEOUT_MS,
    });
    const untouchedPaper = untouchedPreview.ok() ? ((await untouchedPreview.json()) as PreviewResultLite) : null;
    test.info().annotations.push({
      type: "FU-FE3B-V12",
      description: JSON.stringify({
        checkHttp: untouchedCheck.status(),
        checkStatus: untouchedResult?.status ?? (await untouchedCheck.text()),
        checkCodes: untouchedResult ? issueCodes(untouchedResult) : [],
        checkIssues: untouchedResult ? collectIssues(untouchedResult).map((issue) => `${issue.code}/${issue.severity}`) : [],
        previewHttp: untouchedPreview.status(),
        previewStatus: untouchedPaper?.status ?? null,
        previewTotalPoints: untouchedPaper?.paper?.totalPoints ?? null,
        previewTotalQuestions: untouchedPaper?.paper?.totalQuestions ?? null,
      }),
    });

    // Như màn Bài ôn ở bước 2: một nhóm trắc nghiệm một đáp án, 10 câu, mỗi câu 1 điểm; số câu của phần và môn theo nhóm.
    const saved = await saveStructure(admin, exam.id, (structure) => {
      const part = structure.parts[0]!;
      part.questionCount = 10;
      part.questionCountOnPaper = 10;
      part.questionCountRequired = 10;
      part.maxPoints = 10;
      part.durationMinutes = 15;
      const section = part.sections![0]!;
      section.questionCount = 10;
      const block = section.blocks![0]!;
      block.questionType = "SINGLE_CHOICE";
      block.questionCount = 10;
      block.requiredPointsPerQuestion = 1;
    });
    const block = saved.parts[0]!.sections![0]!.blocks![0]!;
    expect([block.questionType, block.questionCount, block.requiredPointsPerQuestion]).toEqual(["SINGLE_CHOICE", 10, 1]);

    const check = await checkExam(admin, exam.id);
    expect(check.status, `lỗi chặn: ${blockingCodes(check).join(", ")}`).toBe("VALID");

    const { request, result } = await generateInUi(page, exam.id);
    expect(request.target).toEqual({ kind: "EXAM" });
    const questions = expectWellFormedPaper(result);
    expect(result.paper.totalQuestions).toBe(10);
    expect(result.paper.totalPoints).toBe(10);
    expect(questions.every((question) => question.subjectCode === "TOAN" && question.points === 1)).toBe(true);
  });
});
