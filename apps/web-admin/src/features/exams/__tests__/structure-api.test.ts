import { beforeEach, describe, expect, it, vi } from "vitest";
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
import { ExamStructureSaveError, ExamsApiError } from "../review-api";
import type { AvailableQuestionsQuery, CreateExamRequest } from "../types";
import { ExamsBackend } from "./fake-exams-backend";

// Danh mục là dữ liệu của lane Nội dung; ở nhánh thật chưa có client, dùng danh mục mẫu để createExam nở tổ hợp.
vi.mock(import("@/features/content/api"), async () => {
  const { getContentReferenceFixture: getContentReference } = await import("@/test/fixtures/content-reference");
  return { getContentReference, defaultContentApiDeps: { getContentReference } };
});

let backend: ExamsBackend;

beforeEach(() => {
  backend = new ExamsBackend();
  backend.install();
});

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

async function rejectsWith(promise: Promise<unknown>, status: number, code: string) {
  const error = await promise.then(
    () => undefined,
    (caught: unknown) => caught,
  );
  expect(error).toBeInstanceOf(ExamsApiError);
  expect(error).toMatchObject({ status, code });
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

  it("cấu trúc khởi tạo sai bị chặn 422; mã tổ hợp lạ được giữ như phương án COMBINATION", async () => {
    const choice = {
      name: "Khoa học",
      questionCount: 50,
      durationMinutes: 60,
      mode: "CHOICE" as const,
      subjectCode: null,
      englishOption: false,
      combinations: [{ code: "KH-1", subjectCounts: [17, 17, 17] }],
    };
    // KH-1 là tổ hợp 3 môn của HSA: partsFromInput nở thành COMBINATION với số câu từng môn (máy chủ tin client).
    const exam = await createExam({ ...NEW_EXAM, code: "HSA-F-010", parts: [choice] });
    expect(exam.parts[0]!.options).toMatchObject([{ kind: "COMBINATION", code: "KH-1" }]);
    expect(exam.parts[0]!.options[0]!.sections.map((section) => section.questionCount)).toEqual([17, 17, 17]);
    // POST tạo xong thì PUT cấu trúc chặn: bài Nháp vẫn còn (ExamStructureSaveError bọc ExamsApiError 422).
    await rejectsWith(createExam({ ...NEW_EXAM, code: "HSA-F-011", parts: [{ ...choice, combinations: [] }] }), 422, "EXAM_STRUCTURE_INVALID");
    await rejectsWith(createExam({ ...NEW_EXAM, code: "HSA-F-012", parts: [] }), 422, "EXAM_STRUCTURE_INVALID");
  });

  it("N2-4: lưu cấu trúc lỗi sau khi tạo thì gửi lại với bài đã tạo chỉ lưu cấu trúc, không POST lại", async () => {
    const choice = {
      name: "Khoa học",
      questionCount: 50,
      durationMinutes: 60,
      mode: "CHOICE" as const,
      subjectCode: null,
      englishOption: false,
      combinations: [{ code: "KH-1", subjectCounts: [17, 17, 17] }],
    };
    const request = { ...NEW_EXAM, code: "HSA-F-013", parts: [{ ...choice, combinations: [] }] };
    const failure = await createExam(request).then(
      () => null,
      (error: unknown) => error,
    );
    expect(failure).toBeInstanceOf(ExamStructureSaveError);
    const created = (failure as ExamStructureSaveError).exam;

    // POST lại cùng mã sẽ trùng (409); gửi kèm bài đã tạo thì chỉ PUT cấu trúc đã sửa.
    await rejectsWith(createExam({ ...request, parts: [choice] }), 409, "EXAM_CODE_TAKEN");
    const exam = await createExam({ ...request, parts: [choice], existing: created });
    expect(exam.id).toBe(created.id);
    expect(exam.parts[0]!.options).toMatchObject([{ kind: "COMBINATION", code: "KH-1" }]);
  });

  it("chặn mã trùng và phạm vi không hợp lệ", async () => {
    await rejectsWith(createExam({ ...NEW_EXAM, code: "HSA-F-001" }), 409, "EXAM_CODE_TAKEN");
    await rejectsWith(createExam({ ...NEW_EXAM, kind: "VACT", scope: "SUBJECT" }), 422, "EXAM_SCOPE_NOT_ALLOWED");
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

  it("chỉ ngừng bán được bài Đang bán; revision cũ của nơi gọi thì máy chủ trả 412", async () => {
    const published = await getExam("ex-qda-f-001");
    await rejectsWith(discontinueExam(published.id, published.revision + 4), 412, "EXAM_REVISION_CONFLICT");

    // Nơi gọi gửi revision của bản đang xem: không đọc lại chi tiết, POST mang revision cũ.
    const posts = backend.calls("POST", "/api/v1/admin/exams/ex-qda-f-001/discontinue");
    expect(posts[0]!.body).toMatchObject({ expectedRevision: published.revision + 4 });
    expect(backend.calls("GET", "/api/v1/admin/exams/ex-qda-f-001")).toHaveLength(1);

    expect((await discontinueExam(published.id, published.revision)).status).toBe("DISCONTINUED");
    const draft = await getExam("ex-hsa-f-002");
    await rejectsWith(discontinueExam(draft.id, draft.revision), 409, "EXAM_NOT_PUBLISHED");
  });
});

describe("nguồn câu hỏi của khối", () => {
  it("đếm nguồn gửi đúng lọc theo môn, dạng và phạm vi kiến thức", async () => {
    backend.seedCounts("count", { status: 200, body: { total: 3, cells: [{ gradeCode: "GRADE_10", value: 3 }] } });
    const counted = await countAvailableQuestions({
      ...MATH_SINGLE,
      knowledgeScope: { nodeIds: ["kn-1D4"] },
    });
    expect(counted.total).toBe(3);
    const [call] = backend.calls("POST", "/api/v1/admin/exams/available-questions/count");
    expect(call?.body).toMatchObject({
      subjectCode: "MATH",
      questionType: "SINGLE_CHOICE",
      knowledgeScope: { nodeIds: ["kn-1D4"] },
    });
  });

  it("countSubjectAvailableQuestions gửi nhóm và ánh xạ byAnswerType", async () => {
    backend.seedCounts("count-subject", {
      status: 200,
      body: {
        total: 12,
        cells: [{ gradeCode: "GRADE_10", value: 12 }],
        byAnswerType: [
          { answerType: "SINGLE_CHOICE", value: 8 },
          { answerType: "SHORT_ANSWER", value: 4 },
        ],
      },
    });
    const counted = await countSubjectAvailableQuestions({
      subjectCode: "MATH",
      groups: [
        { ...MATH_SINGLE, knowledgeScope: { nodeIds: ["kn-1D4"] } },
        { ...MATH_SINGLE, questionType: null, answerTypes: ["SHORT_ANSWER"], knowledgeScope: null },
      ],
    });
    expect(counted.total).toBe(12);
    expect(counted.byAnswerType.map((item) => item.answerType)).toEqual(["SINGLE_CHOICE", "SHORT_ANSWER"]);
    const [call] = backend.calls("POST", "/api/v1/admin/exams/available-questions/count-subject");
    expect(call?.body).toMatchObject({
      subjectCode: "MATH",
      groups: [
        { knowledgeScope: { nodeIds: ["kn-1D4"] }, questionType: "SINGLE_CHOICE" },
        { answerTypes: ["SHORT_ANSWER"] },
      ],
    });
  });
});
