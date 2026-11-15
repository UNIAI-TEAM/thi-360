import { afterEach, describe, expect, it } from "vitest";
import {
  countAvailableQuestions,
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
import type { CreateExamRequest } from "../types";
import { MockApiError } from "@/lib/mock/content-mock";

const NEW_EXAM: CreateExamRequest = {
  name: "Đề HSA thử",
  code: "hsa-f-003",
  kind: "HSA",
  format: "FULL",
  templateCode: "HSA_FULL",
  singleSubjectCode: null,
  description: "  ",
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
    expect(await suggestExamCode({ kind: "HSA", format: "FULL" })).toBe("HSA-F-003");
    expect(await suggestExamCode({ kind: "QDA", format: "SINGLE" })).toBe("QDA-L-001");
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
    expect(exam.parts[2]!.options.map((option) => option.code)).toEqual(["ENGLISH", "KH-1", "KH-2", "KH-3"]);
    expect(exam.parts[2]!.options[1]!.sections.map((section) => section.questionCount)).toEqual([17, 17, 16]);
    expect(exam.parts.flatMap((part) => part.sections).every((section) => section.blocks.length === 0)).toBe(true);
    expect(exam.versions.map((version) => version.state)).toEqual(["WORKING"]);
    expect((await getExamStructure(exam.id)).parts).toHaveLength(3);
  });

  it("mã gợi ý của bài lẻ có đoạn môn", async () => {
    expect(await suggestExamCode({ kind: "HSA", format: "SINGLE", singleScope: "MATH" })).toBe("HSA-L-TOAN-002");
    expect(await suggestExamCode({ kind: "QDA", format: "SINGLE", singleScope: "SCIENCE" })).toBe("QDA-L-KH-001");
  });

  it("tạo theo cấu trúc khởi tạo đã chỉnh: phần cố định, Tiếng Anh, tổ hợp với số câu từng môn", async () => {
    const exam = await createExam({
      ...NEW_EXAM,
      kind: "QDA",
      templateCode: "QDA_FULL",
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
          combinations: [{ code: "KH-4", subjectCounts: [26, 24] }],
        },
      ],
    });
    expect(exam).toMatchObject({ totalQuestions: 90, durationMinutes: 130, englishOption: true, combinationCount: 1 });
    expect(exam.parts[1]!.options.map((option) => [option.code, option.sections.map((section) => section.questionCount)])).toEqual([
      ["ENGLISH", [50]],
      ["KH-4", [26, 24]],
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

  it("chặn mã trùng và phạm vi thiếu môn hoặc mẫu", async () => {
    await rejectsWith(createExam({ ...NEW_EXAM, code: "HSA-F-001" }), 409, "EXAM_CODE_TAKEN");
    await rejectsWith(createExam({ ...NEW_EXAM, templateCode: null }), 422, "EXAM_TEMPLATE_MISMATCH");
    await rejectsWith(
      createExam({ ...NEW_EXAM, format: "SINGLE", templateCode: null, singleSubjectCode: null }),
      422,
      "EXAM_SUBJECT_REQUIRED",
    );
  });
});

describe("sửa bài thi", () => {
  it("lưu cấu trúc: có bản sửa chưa công bố, bài đã kiểm tra chuyển Cần kiểm tra lại", async () => {
    const before = await getExam("ex-qda-f-001");
    expect(before.check.status).toBe("VALID");
    const parts = structuredClone(before.parts);
    parts[0]!.durationMinutes += 5;
    const saved = await updateExamStructure("ex-qda-f-001", { parts });
    expect(saved.hasUnpublishedChanges).toBe(true);
    expect(saved.check.status).toBe("NEEDS_RECHECK");
    expect(saved.durationMinutes).toBe(before.durationMinutes + 5);
    const filtered = await listExams({ hasUnpublishedChanges: true, size: 50 });
    expect(filtered.content.map((exam) => exam.id)).toContain("ex-qda-f-001");
  });

  it("bài chưa kiểm tra vẫn giữ Chưa kiểm tra sau khi lưu", async () => {
    const draft = await getExam("ex-hsa-f-002");
    const saved = await updateExamStructure(draft.id, { parts: draft.parts });
    expect(saved.check.status).toBe("UNCHECKED");
  });

  it("đã công bố thì không đổi được kỳ thi, hình thức, mẫu; tên và mã vẫn sửa được", async () => {
    const exam = await getExam("ex-hsa-f-001");
    const info = {
      name: "Đề HSA đổi tên",
      code: exam.code,
      kind: exam.kind,
      format: exam.format,
      templateCode: exam.templateCode,
      singleSubjectCode: exam.singleSubjectCode,
      description: null,
    };
    await rejectsWith(updateExamInfo(exam.id, { ...info, kind: "QDA", templateCode: "QDA_FULL" }), 409, "EXAM_SCOPE_LOCKED");
    const renamed = await updateExamInfo(exam.id, info);
    expect(renamed.name).toBe("Đề HSA đổi tên");
    expect(renamed.parts).toEqual(exam.parts);
  });

  it("bài chưa công bố đổi mẫu thì nạp lại cấu trúc theo mẫu mới", async () => {
    const exam = await getExam("ex-hsa-f-002");
    const changed = await updateExamInfo(exam.id, {
      name: exam.name,
      code: exam.code,
      kind: "QDA",
      format: "FULL",
      templateCode: "QDA_FULL",
      singleSubjectCode: null,
      description: null,
    });
    expect(changed.kind).toBe("QDA");
    expect(changed.parts.map((part) => [part.questionCount, part.durationMinutes, part.mode])).toEqual([
      [50, 80, "FIXED"],
      [50, 55, "FIXED"],
      [50, 60, "CHOICE"],
    ]);
    expect(changed.parts[2]!.options.map((option) => option.code)).toEqual(["ENGLISH", "KH-4", "KH-5", "KH-6"]);
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
    expect(exam.parts[2]!.options.map((option) => option.code)).toEqual(["ENGLISH", "KH-4", "KH-6"]);
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
    expect((await discontinueExam("ex-qda-f-001")).status).toBe("DISCONTINUED");
    await rejectsWith(discontinueExam("ex-hsa-f-002"), 409, "EXAM_NOT_PUBLISHED");
  });
});

describe("nguồn câu hỏi của khối", () => {
  it("đếm câu Sẵn sàng theo môn và dạng, thu hẹp khi chọn kiến thức", async () => {
    const all = await countAvailableQuestions({
      subjectCode: "MATH",
      questionType: "SINGLE_CHOICE",
      knowledgeScope: { nodeIds: [], match: "ANY", includeDescendants: false },
    });
    expect(all.total).toBeGreaterThan(0);
    expect(all.cells.reduce((total, cell) => total + cell.value, 0)).toBe(all.total);
    const none = await countAvailableQuestions({
      subjectCode: "MATH",
      questionType: "SINGLE_CHOICE",
      knowledgeScope: { nodeIds: ["khong-co"], match: "ANY", includeDescendants: true },
    });
    expect(none.total).toBe(0);
  });
});
