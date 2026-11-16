import { afterEach, describe, expect, it } from "vitest";
import { MockApiError } from "@/lib/mock/content-mock";
import { applyExamTemplate, createExam, listExams, suggestExamCode, updateExamInfo, updateExamStructure } from "../api";
import { examStore } from "../mock/store";
import type { CreateExamRequest } from "../types";
import { pinInMemoryBackend } from "./in-memory-backend";

pinInMemoryBackend();

afterEach(() => {
  examStore.reset();
});

function request(overrides: Partial<CreateExamRequest>): CreateExamRequest {
  return {
    name: "Bai thu",
    code: "THU-001",
    kind: "HSA",
    scope: "FULL",
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
    const exam = await createExam(request({ kind: "BCA", code: "bca-t-001" }));
    expect(exam).toMatchObject({ timing: "SHARED", durationMinutes: 180, maxScore: 100, hasEssay: true, subjectCode: null });
    expect(exam.parts.map((part) => [part.maxPoints, part.durationMinutes])).toEqual([
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
        scope: "SUBJECT",
        subjectCode: "INFORMATICS",
      }),
    );
    expect(exam).toMatchObject({ timing: "SHARED", durationMinutes: 50, subjectCode: "INFORMATICS", maxScore: 10 });
    const choice = exam.parts[1]!;
    expect(choice.mode).toBe("CHOICE");
    expect(choice.sections).toMatchObject([{ subjectCode: "INFORMATICS", questionCount: 2 }]);
    expect(choice.sections[0]!.blocks[0]!.expectedScoring).toEqual({ trueFalseMethod: "COUNT_TABLE", trueFalseTable: [0.1, 0.25, 0.5, 1] });
    expect(choice.options.map((option) => [option.kind, option.code, option.sections[0]?.subjectCode])).toEqual([
      ["BRANCH", "CS", "INFORMATICS"],
      ["BRANCH", "ICT", "INFORMATICS"],
    ]);
    const page = await listExams({ subjectCode: "INFORMATICS" });
    expect(page.content.map((item) => [item.code, item.branchCount, item.timing])).toContainEqual(["THPTQG-F-TIN-009", 2, "SHARED"]);
  });

  it("V-ACT: thang 1.200, điểm phần 600/300/300; năng lực của mẫu thành phạm vi kiến thức của khối", async () => {
    const exam = await createExam(request({ kind: "VACT", code: "VACT-T-001" }));
    expect(exam.maxScore).toBe(1200);
    expect(exam.parts.map((part) => part.maxPoints)).toEqual([600, 300, 300]);
    expect(exam.parts[1]!.sections[0]!.blocks[0]!.knowledgeScope?.competencyCodes).toEqual(["VACT-TOAN"]);
  });

  it("SP2E Toán: bước 1 nạp mẫu mới nhất (cấu trúc 2026, ma trận 2025, chưa xác nhận); năm cũ nạp ở bước Cấu trúc", async () => {
    const exam = await createExam(request({ kind: "SP2E", code: "SP2E-T-001", scope: "SUBJECT", subjectCode: "MATH" }));
    expect(exam).toMatchObject({ templateCode: "SP2E_MATH_2026", structureYear: 2026, matrixYear: 2025, yearsConfirmed: false });
    const older = await applyExamTemplate(exam.id, {
      templateCode: "SP2E_MATH_2025",
      confirmReplace: true,
      expectedRevision: exam.revision,
    });
    expect(older).toMatchObject({ templateCode: "SP2E_MATH_2025", structureYear: 2025, yearsConfirmed: true });
    expect(older.revision).toBe(exam.revision + 1);
  });

  it("từ chối phạm vi sai: thiếu phần hoặc môn, phạm vi kỳ thi không cho, thang điểm không hợp lệ", async () => {
    await rejectsWith(createExam(request({ kind: "THPTQG", scope: "SUBJECT" })), 422, "EXAM_PART_OR_SUBJECT_REQUIRED");
    await rejectsWith(createExam(request({ kind: "HSA", scope: "PART" })), 422, "EXAM_PART_OR_SUBJECT_REQUIRED");
    await rejectsWith(createExam(request({ kind: "BCA", scope: "PART", partCode: "MATH" })), 422, "EXAM_SCOPE_NOT_ALLOWED");
    await rejectsWith(createExam(request({ kind: "THPTQG", scope: "FULL" })), 422, "EXAM_SCOPE_NOT_ALLOWED");
    await rejectsWith(createExam(request({ maxScore: 0 })), 422, "EXAM_MAX_SCORE_INVALID");
  });

  it("mẫu của bước Cấu trúc phải khớp bài thi", async () => {
    const exam = await createExam(request({ kind: "THPTQG", code: "THPTQG-S-TOAN-555", scope: "SUBJECT", subjectCode: "MATH" }));
    await rejectsWith(
      applyExamTemplate(exam.id, { templateCode: "THPTQG_PHYSICS_2025", confirmReplace: true, expectedRevision: exam.revision }),
      422,
      "EXAM_TEMPLATE_MISMATCH",
    );
  });
});

describe("mã gợi ý và sửa Thông tin chung", () => {
  it("mã gợi ý theo kỳ thi, phạm vi và môn: THPTQG-S-TOAN-nnn, số tiếp theo chưa dùng", async () => {
    const first = await suggestExamCode({ kind: "THPTQG", scope: "SUBJECT", subjectCode: "MATH" });
    expect(first).toMatch(/^THPTQG-S-TOAN-\d{3}$/);
    await createExam(request({ kind: "THPTQG", code: first, scope: "SUBJECT", subjectCode: "MATH" }));
    const next = await suggestExamCode({ kind: "THPTQG", scope: "SUBJECT", subjectCode: "MATH" });
    expect(next).not.toBe(first);
    expect(next).toMatch(/^THPTQG-S-TOAN-\d{3}$/);
  });

  it("đổi môn của bài nháp nạp lại cấu trúc theo mẫu của môn mới; thang điểm sửa được", async () => {
    const exam = await createExam(
      request({ kind: "THPTQG", code: "THPTQG-S-TOAN-777", scope: "SUBJECT", subjectCode: "MATH" }),
    );
    const info = {
      name: exam.name,
      code: exam.code,
      kind: "THPTQG",
      scope: "SUBJECT" as const,
      subjectCode: "LITERATURE",
      maxScore: 10,
      description: null,
      confirmReplace: true,
    };
    const updated = await updateExamInfo(exam.id, { ...info, expectedRevision: exam.revision });
    expect(updated).toMatchObject({ subjectCode: "LITERATURE", totalQuestions: 7, durationMinutes: 120, hasEssay: true });
    const rescored = await updateExamInfo(exam.id, { ...info, maxScore: 20, expectedRevision: updated.revision });
    expect(rescored.maxScore).toBe(20);
    expect(rescored.parts.map((part) => part.id)).toEqual(updated.parts.map((part) => part.id));
  });

  it("sửa Thông tin chung: 412 EXAM_REVISION_CONFLICT khi revision lệch, 409 khi cần xác nhận nạp lại cấu trúc", async () => {
    const exam = await createExam(request({ kind: "THPTQG", code: "THPTQG-S-TOAN-778", scope: "SUBJECT", subjectCode: "MATH" }));
    const info = {
      name: exam.name,
      code: exam.code,
      kind: "THPTQG",
      scope: "SUBJECT" as const,
      subjectCode: "MATH",
      maxScore: 10,
      description: "Mo ta moi",
    };
    await rejectsWith(updateExamInfo(exam.id, { ...info, expectedRevision: exam.revision + 3 }), 412, "EXAM_REVISION_CONFLICT");
    const saved = await updateExamInfo(exam.id, { ...info, expectedRevision: exam.revision });
    expect(saved.revision).toBe(exam.revision + 1);
    // Đã sửa cấu trúc tay thì đổi môn cần xác nhận.
    await updateExamStructure(exam.id, { parts: saved.parts, expectedRevision: saved.revision });
    await rejectsWith(
      updateExamInfo(exam.id, { ...info, subjectCode: "LITERATURE", expectedRevision: saved.revision + 1 }),
      409,
      "EXAM_STRUCTURE_REPLACE_CONFIRM",
    );
  });
});
