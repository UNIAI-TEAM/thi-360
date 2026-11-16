import { afterEach, describe, expect, it } from "vitest";
import {
  countAvailableQuestions,
  countSubjectAvailableQuestions,
  createExam,
  discontinueExam,
  duplicateExam,
  getExam,
  getExamStructure,
  listExams,
  suggestExamCode,
  updateExamInfo,
  updateExamStructure,
} from "../api";
import { examStore } from "../mock/store";
import type { AvailableQuestionsQuery, CreateExamRequest } from "../types";
import { MockApiError } from "@/lib/mock/content-mock";
import { useInMemoryBackend } from "./in-memory-backend";

useInMemoryBackend();

const NEW_EXAM: CreateExamRequest = {
  name: "Đề HSA thử",
  code: "hsa-f-003",
  kind: "HSA",
  scope: "FULL",
  description: "  ",
};

/** Nhóm câu đơn Toán chọn một đáp án, chưa giới hạn phạm vi; các test đặt `knowledgeScope` riêng. */
const MATH_SINGLE: AvailableQuestionsQuery = {
  subjectCode: "MATH",
  questionType: "SINGLE_CHOICE",
  answerTypes: [],
  childAnswerType: null,
  knowledgeScope: null,
};

afterEach(() => {
  examStore.reset();
});

async function rejectsWith(promise: Promise<unknown>, status: number, code: string) {
  await expect(promise).rejects.toBeInstanceOf(MockApiError);
  await promise.catch((error: MockApiError) => {
    expect(error.status).toBe(status);
    expect(error.code).toBe(code);
  });
}

describe("tạo bài thi", () => {
  it("gợi ý mã tiếp theo chưa dùng", async () => {
    expect(await suggestExamCode({ kind: "HSA", scope: "FULL" })).toBe("HSA-F-003");
    expect(await suggestExamCode({ kind: "QDA", scope: "PART" })).toBe("QDA-P-001");
  });

  it("tạo bài Nháp theo mẫu, mã viết hoa, cấu trúc 3 phần với khối để trống", async () => {
    const exam = await createExam(NEW_EXAM);
    expect(exam).toMatchObject({
      code: "HSA-F-003",
      status: "DRAFT",
      servingVersion: null,
      hasUnpublishedChanges: true,
      description: null,
      check: { status: "UNCHECKED" },
    });
    expect(exam.parts.map((part) => part.mode)).toEqual(["FIXED", "FIXED", "CHOICE"]);
    expect(exam.parts[2]!.options.map((option) => option.code)).toEqual(["ENGLISH", "SCIENCE"]);
    const science = exam.parts[2]!.options[1]!;
    expect(science).toMatchObject({ kind: "SUBJECT_CHOICE", chooseSubjects: 3, countsByPick: [17, 17, 16] });
    expect(science).not.toHaveProperty("expertConfirmed");
    expect(science.sections.map((section) => section.choiceGroup)).toEqual(["NATURAL", "NATURAL", "NATURAL", "SOCIAL", "SOCIAL"]);
    expect(science.sections.map((section) => section.questionCount)).toEqual([null, null, null, null, null]);
    expect(exam.combinationCount).toBe(0);
    expect(exam.versions.map((version) => version.state)).toEqual(["WORKING"]);
    expect((await getExamStructure(exam.id)).parts).toHaveLength(3);
  });

  it("mã gợi ý của bài lẻ có đoạn môn", async () => {
    expect(await suggestExamCode({ kind: "HSA", scope: "PART", partCode: "MATH" })).toBe("HSA-P-TOAN-001");
    expect(await suggestExamCode({ kind: "QDA", scope: "PART", partCode: "SCIENCE" })).toBe("QDA-P-KH-001");
  });

  it("tạo theo cấu trúc khởi tạo đã chỉnh: phần cố định, Tiếng Anh, tổ hợp với số câu từng môn", async () => {
    const exam = await createExam({
      ...NEW_EXAM,
      kind: "QDA",
      code: "QDA-F-009",
      parts: [
        { name: "Toán", questionCount: 40, durationMinutes: 70, mode: "FIXED", subjectCode: "MATH", englishOption: false, combinations: [] },
        {
          name: "Khoa học hoặc Tiếng Anh",
          questionCount: 50,
          durationMinutes: 60,
          mode: "CHOICE",
          subjectCode: null,
          englishOption: true,
          combinations: [{ code: "QD01", subjectCounts: [26, 24] }],
        },
      ],
    });
    expect(exam).toMatchObject({ totalQuestions: 90, durationMinutes: 130, englishOption: true, combinationCount: 1 });
    expect(exam.parts[1]!.options.map((option) => [option.code, option.sections.map((section) => section.questionCount)])).toEqual([
      ["ENGLISH", [50]],
      ["QD01", [26, 24]],
    ]);
  });

  it("cấu trúc khởi tạo sai bị chặn 422", async () => {
    const choice = {
      name: "Khoa học",
      questionCount: 50,
      durationMinutes: 60,
      mode: "CHOICE" as const,
      subjectCode: null,
      englishOption: false,
      combinations: [{ code: "KH-1", subjectCounts: [17, 17, 17] }],
    };
    await rejectsWith(createExam({ ...NEW_EXAM, parts: [choice] }), 422, "EXAM_STRUCTURE_INVALID");
    await rejectsWith(createExam({ ...NEW_EXAM, parts: [{ ...choice, combinations: [] }] }), 422, "EXAM_STRUCTURE_INVALID");
    await rejectsWith(createExam({ ...NEW_EXAM, parts: [] }), 422, "EXAM_STRUCTURE_INVALID");
  });

  it("chặn mã trùng và phạm vi không hợp lệ", async () => {
    await rejectsWith(createExam({ ...NEW_EXAM, code: "HSA-F-001" }), 409, "EXAM_CODE_TAKEN");
    await rejectsWith(createExam({ ...NEW_EXAM, scope: "SUBJECT" }), 422, "EXAM_SCOPE_NOT_ALLOWED");
    await rejectsWith(createExam({ ...NEW_EXAM, scope: "PART", partCode: null }), 422, "EXAM_PART_OR_SUBJECT_REQUIRED");
  });
});

describe("sửa bài thi", () => {
  it("lưu cấu trúc lệch revision bị từ chối 412 EXAM_REVISION_CONFLICT và không đổi bài", async () => {
    const before = await getExam("ex-qda-f-001");
    await rejectsWith(
      updateExamStructure(before.id, { parts: before.parts, expectedRevision: before.revision + 1 }),
      412,
      "EXAM_REVISION_CONFLICT",
    );
    expect((await getExam(before.id)).revision).toBe(before.revision);
    const saved = await updateExamStructure(before.id, { parts: before.parts, expectedRevision: before.revision });
    expect(saved.revision).toBe(before.revision + 1);
  });

  it("lưu cấu trúc: có bản sửa chưa công bố, bài đã kiểm tra chuyển Cần kiểm tra lại", async () => {
    const before = await getExam("ex-qda-f-001");
    expect(before.check.status).toBe("VALID");
    const parts = structuredClone(before.parts);
    parts[0]!.durationMinutes = (parts[0]!.durationMinutes ?? 0) + 5;
    const saved = await updateExamStructure("ex-qda-f-001", { parts, expectedRevision: before.revision });
    expect(saved.hasUnpublishedChanges).toBe(true);
    expect(saved.check.status).toBe("NEEDS_RECHECK");
    expect(saved.durationMinutes).toBe(before.durationMinutes + 5);
    const filtered = await listExams({ hasUnpublishedChanges: true, size: 50 });
    expect(filtered.content.map((exam) => exam.id)).toContain("ex-qda-f-001");
  });

  it("bài chưa kiểm tra vẫn giữ Chưa kiểm tra sau khi lưu", async () => {
    const draft = await getExam("ex-hsa-f-002");
    const saved = await updateExamStructure(draft.id, { parts: draft.parts, expectedRevision: draft.revision });
    expect(saved.check.status).toBe("UNCHECKED");
  });

  it("đã công bố thì không đổi được kỳ thi, phạm vi; tên và mã vẫn sửa được", async () => {
    const exam = await getExam("ex-hsa-f-001");
    const info = {
      name: "Đề HSA đổi tên",
      code: exam.code,
      kind: exam.kind,
      scope: exam.scope,
      partCode: exam.partCode,
      description: null,
      expectedRevision: exam.revision,
    };
    await rejectsWith(updateExamInfo(exam.id, { ...info, kind: "QDA" }), 409, "EXAM_SCOPE_LOCKED");
    const renamed = await updateExamInfo(exam.id, info);
    expect(renamed.name).toBe("Đề HSA đổi tên");
    expect(renamed.parts).toEqual(exam.parts);
  });

  it("bài chưa công bố đổi kỳ thi thì nạp lại cấu trúc theo mẫu mới", async () => {
    const exam = await getExam("ex-hsa-f-002");
    const changed = await updateExamInfo(exam.id, {
      name: exam.name,
      code: exam.code,
      kind: "QDA",
      scope: "FULL",
      description: null,
      expectedRevision: exam.revision,
    });
    expect(changed.kind).toBe("QDA");
    expect(changed.parts.map((part) => [part.questionCount, part.durationMinutes, part.mode])).toEqual([
      [50, 80, "FIXED"],
      [50, 55, "FIXED"],
      [50, 60, "CHOICE"],
    ]);
    expect(changed.parts[2]!.options.map((option) => option.code)).toEqual(["ENGLISH", "QD01", "QD02", "QD03", "QD04"]);
    expect(changed.parts[2]!.options[1]!.sections.map((section) => section.questionCount)).toEqual([25, 25]);
  });
});

describe("mẫu QDA theo UniNote mục 5.1", () => {
  it("QDA FULL: Toán 50/80, Văn 50/55, Khoa học hoặc Tiếng Anh 50/60; bài mẫu đủ 150 câu, 195 phút", async () => {
    const exam = await getExam("ex-qda-f-001");
    expect(exam).toMatchObject({ totalQuestions: 150, durationMinutes: 195, englishOption: true, combinationCount: 2 });
    expect(exam.parts.map((part) => [part.questionCount, part.durationMinutes, part.mode])).toEqual([
      [50, 80, "FIXED"],
      [50, 55, "FIXED"],
      [50, 60, "CHOICE"],
    ]);
    expect(exam.parts[2]!.options.map((option) => option.code)).toEqual(["ENGLISH", "QD01", "QD04"]);
  });
});

describe("sao chép và ngừng bán", () => {
  it("sao chép thành bài Nháp mới, mã -COPY rồi -COPY-2, mã định danh mới", async () => {
    const source = await getExam("ex-hsa-f-001");
    const first = await duplicateExam(source.id, { name: "Bản sao" });
    const second = await duplicateExam(source.id, { name: "Bản sao 2" });
    expect(first.code).toBe("HSA-F-001-COPY");
    expect(second.code).toBe("HSA-F-001-COPY-2");
    expect(first).toMatchObject({ status: "DRAFT", servingVersion: null, check: { status: "UNCHECKED" } });
    expect(first.totalQuestions).toBe(source.totalQuestions);
    const sourceIds = new Set(source.parts.map((part) => part.id));
    expect(first.parts.some((part) => sourceIds.has(part.id))).toBe(false);
  });

  it("chỉ ngừng bán được bài Đang bán", async () => {
    const published = await getExam("ex-qda-f-001");
    await rejectsWith(discontinueExam(published.id, published.revision + 4), 412, "EXAM_REVISION_CONFLICT");
    expect((await discontinueExam(published.id, published.revision)).status).toBe("DISCONTINUED");
    const draft = await getExam("ex-hsa-f-002");
    await rejectsWith(discontinueExam(draft.id, draft.revision), 409, "EXAM_NOT_PUBLISHED");
  });
});

describe("nguồn câu hỏi của khối", () => {
  it("đếm câu Sẵn sàng theo môn và dạng, thu hẹp khi chọn kiến thức", async () => {
    const all = await countAvailableQuestions({
      ...MATH_SINGLE,
      knowledgeScope: { nodeIds: [], match: "ANY" },
    });
    expect(all.total).toBeGreaterThan(0);
    expect(all.cells.reduce((total, cell) => total + cell.value, 0)).toBe(all.total);
    const none = await countAvailableQuestions({
      ...MATH_SINGLE,
      knowledgeScope: { nodeIds: ["khong-co"], match: "ANY" },
    });
    expect(none.total).toBe(0);
  });

  it("countSubjectAvailableQuestions dedups across groups", async () => {
    // Hai nhóm cùng môn Toán có nguồn giao nhau: CH-000001 gắn kn-1D4-1-1 nằm trong cả hai phạm vi (nút cha khớp nút con, D23).
    const group = (nodeIds: string[]) => ({ ...MATH_SINGLE, knowledgeScope: { nodeIds, match: "ANY" as const } });
    const first = await countAvailableQuestions(group(["kn-1D4"]));
    const second = await countAvailableQuestions(group(["kn-1D4-1"]));
    expect(first.total).toBeGreaterThan(0);
    expect(second.total).toBeGreaterThan(0);
    const union = await countSubjectAvailableQuestions({
      subjectCode: "MATH",
      groups: [group(["kn-1D4"]), group(["kn-1D4-1"])],
    });
    expect(union.total).toBeLessThan(first.total + second.total);
    expect(union.total).toBe(first.total);
    // Nhóm lấy theo chỉ tiêu dạng trả lời của môn: hợp các dạng, byAnswerType cộng đúng tổng.
    const byQuota = await countSubjectAvailableQuestions({
      subjectCode: "MATH",
      groups: [{ ...MATH_SINGLE, questionType: null, answerTypes: ["SINGLE_CHOICE", "SHORT_ANSWER"], knowledgeScope: null }],
    });
    expect(byQuota.byAnswerType.map((item) => item.answerType).sort()).toEqual(["SHORT_ANSWER", "SINGLE_CHOICE"]);
    expect(byQuota.byAnswerType.reduce((total, item) => total + item.value, 0)).toBe(byQuota.total);
  });
});
