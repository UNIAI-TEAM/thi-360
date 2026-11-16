import { afterEach, describe, expect, it } from "vitest";
import { MockApiError } from "@/lib/mock/content-mock";
import { createExam, listExams, suggestExamCode, updateExamInfo } from "../api";
import { examStore } from "../mock/store";
import type { CreateExamRequest } from "../types";

afterEach(() => {
  examStore.reset();
});

function request(overrides: Partial<CreateExamRequest>): CreateExamRequest {
  return {
    name: "Bai thu",
    code: "THU-001",
    kind: "HSA",
    format: "FULL",
    templateCode: "HSA_FULL",
    singleSubjectCode: null,
    description: null,
    ...overrides,
  };
}

async function rejectsWith(promise: Promise<unknown>, status: number, code: string) {
  await expect(promise).rejects.toBeInstanceOf(MockApiError);
  await promise.catch((error: MockApiError) => {
    expect(error.status).toBe(status);
    expect(error.code).toBe(code);
  });
}

describe("tạo bài thi theo bảy kỳ thi (nạp nguyên mẫu)", () => {
  it("BCA: thời lượng chung 180, thang 100, điểm phần 25/60/15, phần 2 ba môn, CA1–CA4 kèm khối; có tự luận", async () => {
    const exam = await createExam(request({ kind: "BCA", code: "bca-t-001", templateCode: "BCA_FULL" }));
    expect(exam).toMatchObject({ timing: "SHARED", durationMinutes: 180, maxScore: 100, hasEssay: true, subjectCode: null });
    expect(exam.parts.map((part) => [part.points, part.durationMinutes])).toEqual([
      [25, null],
      [60, null],
      [15, null],
    ]);
    expect(exam.parts[1]!.sections.map((section) => [section.subjectCode, section.questionCount])).toEqual([
      ["MATH", 35],
      ["HISTORY", 10],
      ["ENGLISH", 20],
    ]);
    const options = exam.parts[2]!.options;
    expect(options.map((option) => option.code)).toEqual(["CA1", "CA2", "CA3", "CA4"]);
    expect(options.every((option) => option.sections[0]?.blocks.length === 1)).toBe(true);
  });

  it("THPTQG Tin học: phần chung và hai nhánh; khối mang cách chấm mong đợi của mẫu", async () => {
    const exam = await createExam(
      request({
        kind: "THPTQG",
        code: "THPTQG-F-TIN-009",
        templateCode: "THPTQG_INFORMATICS_2025",
        subjectCode: "INFORMATICS",
      }),
    );
    expect(exam).toMatchObject({ timing: "SHARED", durationMinutes: 50, subjectCode: "INFORMATICS", maxScore: 10 });
    const choice = exam.parts[1]!;
    expect(choice.mode).toBe("CHOICE");
    expect(choice.sections).toMatchObject([{ subjectCode: "INFORMATICS", questionCount: 2 }]);
    expect(choice.sections[0]!.blocks[0]!.expectedScoring).toEqual({ trueFalseTable: [0.1, 0.25, 0.5, 1] });
    expect(choice.options.map((option) => [option.kind, option.code, option.sections[0]?.subjectCode])).toEqual([
      ["BRANCH", "CS", "INFORMATICS"],
      ["BRANCH", "ICT", "INFORMATICS"],
    ]);
    const page = await listExams({ subjectCode: "INFORMATICS" });
    expect(page.content.map((item) => [item.code, item.branchCount, item.timing])).toContainEqual(["THPTQG-F-TIN-009", 2, "SHARED"]);
  });

  it("V-ACT: thang 1.200, điểm phần 600/300/300; năng lực của mẫu thành phạm vi kiến thức của khối", async () => {
    const exam = await createExam(request({ kind: "VACT", code: "VACT-T-001", templateCode: "VACT_FULL" }));
    expect(exam.maxScore).toBe(1200);
    expect(exam.parts.map((part) => part.points)).toEqual([600, 300, 300]);
    expect(exam.parts[1]!.sections[0]!.blocks[0]!.knowledgeScope.competencyCodes).toEqual(["VACT-TOAN"]);
  });

  it("SP2E Toán 2026 dùng ma trận 2025: giữ xác nhận của người tạo", async () => {
    const pending = await createExam(
      request({ kind: "SP2E", code: "SP2E-T-001", templateCode: "SP2E_MATH_2026", subjectCode: "MATH" }),
    );
    expect(pending).toMatchObject({ structureYear: 2026, matrixYear: 2025, yearsConfirmed: false });
    const confirmed = await createExam(
      request({
        kind: "SP2E",
        code: "SP2E-T-002",
        templateCode: "SP2E_MATH_2026",
        subjectCode: "MATH",
        yearsConfirmed: true,
      }),
    );
    expect(confirmed.yearsConfirmed).toBe(true);
  });

  it("từ chối phạm vi sai: thiếu môn, hình thức không có, mẫu khác môn, thang điểm không hợp lệ", async () => {
    await rejectsWith(createExam(request({ kind: "THPTQG", templateCode: null })), 422, "EXAM_SUBJECT_REQUIRED");
    await rejectsWith(
      createExam(request({ kind: "BCA", format: "SINGLE", templateCode: null, singleSubjectCode: "MATH" })),
      422,
      "EXAM_FORMAT_NOT_ALLOWED",
    );
    await rejectsWith(
      createExam(request({ kind: "THPTQG", subjectCode: "PHYSICS", templateCode: "THPTQG_MATH_2025" })),
      422,
      "EXAM_TEMPLATE_MISMATCH",
    );
    await rejectsWith(createExam(request({ maxScore: 0 })), 422, "EXAM_MAX_SCORE_INVALID");
  });
});

describe("mã gợi ý và sửa Thông tin chung", () => {
  it("mã gợi ý theo kỳ thi và môn: THPTQG-F-TOAN-nnn, số tiếp theo chưa dùng", async () => {
    const first = await suggestExamCode({ kind: "THPTQG", format: "FULL", singleScope: null, subjectCode: "MATH" });
    expect(first).toMatch(/^THPTQG-F-TOAN-\d{3}$/);
    await createExam(request({ kind: "THPTQG", code: first, templateCode: "THPTQG_MATH_2025", subjectCode: "MATH" }));
    const next = await suggestExamCode({ kind: "THPTQG", format: "FULL", singleScope: null, subjectCode: "MATH" });
    expect(next).not.toBe(first);
    expect(next).toMatch(/^THPTQG-F-TOAN-\d{3}$/);
  });

  it("đổi môn của bài nháp nạp lại cấu trúc theo mẫu của môn mới; thang điểm sửa được", async () => {
    const exam = await createExam(
      request({ kind: "THPTQG", code: "THPTQG-F-TOAN-777", templateCode: "THPTQG_MATH_2025", subjectCode: "MATH" }),
    );
    const updated = await updateExamInfo(exam.id, {
      name: exam.name,
      code: exam.code,
      kind: "THPTQG",
      format: "FULL",
      templateCode: "THPTQG_LITERATURE_2025",
      singleSubjectCode: null,
      subjectCode: "LITERATURE",
      maxScore: 10,
      description: null,
    });
    expect(updated).toMatchObject({ subjectCode: "LITERATURE", totalQuestions: 7, durationMinutes: 120, hasEssay: true });
    const rescored = await updateExamInfo(exam.id, {
      name: exam.name,
      code: exam.code,
      kind: "THPTQG",
      format: "FULL",
      templateCode: "THPTQG_LITERATURE_2025",
      singleSubjectCode: null,
      subjectCode: "LITERATURE",
      maxScore: 20,
      description: null,
    });
    expect(rescored.maxScore).toBe(20);
    expect(rescored.parts.map((part) => part.id)).toEqual(updated.parts.map((part) => part.id));
  });
});
