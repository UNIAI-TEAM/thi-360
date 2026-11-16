import { beforeEach, describe, expect, it } from "vitest";
import { questionStore } from "@/features/questions/mock/store";
import { MockApiError } from "@/lib/mock/content-mock";
import { examStore } from "../mock/store";
import { reviewStore } from "../mock/review-store";
import { DEEP_CHECK_POLLS_UNTIL_DONE } from "../review-api.mock";
import {
  discontinueExam,
  generatePreview,
  getCheckRun,
  getExamCheck,
  getExamVersion,
  listExamVersions,
  publishExam,
  runExamCheck,
  startDeepCheck,
} from "../review-api";

const HSA = "ex-hsa-f-001";

/** `revision` hiện tại của bài (D13): lệnh công bố gửi lại làm `expectedRevision`. */
const rev = (examId: string) => examStore.require(examId).revision;
const EN = "opt-30";
/** Phương án Khoa học (SUBJECT_CHOICE) của bài mẫu: HSA-F-001 chưa xác nhận số câu và thiếu nguồn Hóa học Lớp 12. */
const SCIENCE = "opt-sci-1";
/** HSA-F-000: đã xác nhận 17–17–16, mọi cách chọn môn đủ nguồn. */
const CONFIRMED = "ex-hsa-f-000";
const CONFIRMED_SCIENCE = "opt-sci-4";

beforeEach(() => {
  examStore.reset();
  questionStore.reset();
  reviewStore.reset();
});

describe("runExamCheck / getExamCheck", () => {
  it("trả kết quả gần nhất theo bài mẫu, chưa kiểm tra thì null", async () => {
    const seeded = await getExamCheck({ examId: HSA });
    expect(seeded?.checkedAt).toBe("2026-11-15T09:28:00+07:00");
    expect(seeded?.durationSeconds).toBe(31);
    expect(await getExamCheck({ examId: "ex-hsa-f-002" })).toBeNull();
  });

  it("chạy kiểm tra cập nhật trạng thái kiểm tra của bài thi", async () => {
    const result = await runExamCheck({ examId: "ex-hsa-f-002" });
    expect(result.paths).toHaveLength(2);
    const exam = examStore.require("ex-hsa-f-002");
    expect(exam.check).toEqual({ status: result.status, issueCount: result.issueCount, checkedAt: result.checkedAt });
  });

  it("kiểm tra lại với thời gian dài hơn xác nhận được cách chọn có Địa lí", async () => {
    const standard = await runExamCheck({ examId: HSA });
    const pick = (result: typeof standard) =>
      result.paths.find((path) => path.pathId === SCIENCE)!.selections.find((item) => item.subjectCodes.join() === "PHYSICS,BIOLOGY,GEOGRAPHY")?.result;
    expect(pick(standard)).toBe("UNCONFIRMED");
    const result = await runExamCheck({ examId: HSA, extendedTimeLimit: true });
    expect(result.timeLimitSeconds).toBe(120);
    expect(pick(result)).toBe("VALID");
    // Còn lại: phương án chờ chuyên gia xác nhận và chỉ tiêu Hóa học Lớp 12 thiếu nguồn.
    expect(result.issueCount).toBe(2);
    expect(examStore.require(HSA).check.issueCount).toBe(2);
  });
});

describe("generatePreview", () => {
  it("sinh đề đủ số câu, không lặp câu, đúng phân bổ từng ô", async () => {
    const result = await generatePreview({ examId: HSA, pathId: EN, seed: 7 });
    expect(result.status).toBe("GENERATED");
    if (result.status !== "GENERATED") return;
    const { paper } = result;
    expect(paper.totalQuestions).toBe(150);
    expect(paper.totalPoints).toBe(150);
    const ids = paper.parts.flatMap((part) =>
      part.blocks.flatMap((block) =>
        block.items.flatMap((item) => (item.kind === "SET" ? item.children : [item.question])).map((q) => q.questionId),
      ),
    );
    expect(new Set(ids).size).toBe(ids.length);
    expect(paper.checks.find((check) => check.code === "NO_DUPLICATES")?.status).toBe("PASS");
    // Toán HSA: nhóm câu đơn theo chỉ tiêu dạng trả lời của môn và chùm; mỗi môn đủ số câu của môn.
    expect(paper.parts[0]!.blocks.map((block) => [block.questionType, block.questionCount])).toEqual([
      [null, 40],
      ["QUESTION_SET", 10],
    ]);
    const cluster = paper.parts[0]!.blocks.find((block) => block.blockId === "blk-23")!;
    expect(cluster.allocation.every((cell) => cell.required === cell.generated)).toBe(true);
    const sets = paper.parts[1]!.blocks.find((block) => block.questionType === "QUESTION_SET")!;
    expect(sets.items).toHaveLength(2);
    expect(sets.items.every((item) => item.kind === "SET" && item.children.length === 5)).toBe(true);
    expect(paper.parts.map((part) => [part.firstNumber, part.lastNumber])).toEqual([
      [1, 50],
      [51, 100],
      [101, 150],
    ]);
  });

  it("ưu tiên câu Sẵn sàng thật trong kho, cùng hạt giống cho cùng đề", async () => {
    const first = await generatePreview({ examId: HSA, pathId: EN, seed: 3 });
    const again = await generatePreview({ examId: HSA, pathId: EN, seed: 3 });
    if (first.status !== "GENERATED" || again.status !== "GENERATED") throw new Error("expected paper");
    const codes = (paper: typeof first.paper) =>
      paper.parts.flatMap((part) => part.blocks.flatMap((block) => block.items.map((item) => (item.kind === "SET" ? item.code : item.question.code))));
    expect(codes(again.paper)).toEqual(codes(first.paper));
    // Câu thật được ưu tiên: CH-000010 là câu Toán duy nhất của ô Lớp 10 / Nhận biết trong phân bổ cấp môn.
    expect(codes(first.paper)).toContain("CH-000010");
    expect(codes(first.paper).some((code) => code.startsWith("SIM-"))).toBe(true);
  });

  it("đường không hợp lệ thì không sinh đề thiếu câu", async () => {
    const result = await generatePreview({ examId: HSA, pathId: SCIENCE, seed: 1, subjectCodes: ["PHYSICS", "CHEMISTRY", "BIOLOGY"] });
    expect(result.status).toBe("FAILED");
    if (result.status !== "FAILED") return;
    expect(result.reason).toBe("INVALID");
    expect(result.issues).toContainEqual(
      expect.objectContaining({ code: "SUBJECT_QUOTA_INFEASIBLE", subjectCode: "CHEMISTRY", gradeCode: "GRADE_12", required: 9, available: 4 }),
    );
  });

  it("đường Khoa học: xem trước cần đúng số môn đã chọn, thiếu hoặc sai thì 422 PREVIEW_SUBJECTS_INVALID", async () => {
    const request = { examId: CONFIRMED, pathId: CONFIRMED_SCIENCE, seed: 1 };
    for (const subjectCodes of [undefined, [], ["PHYSICS", "CHEMISTRY"], ["PHYSICS", "CHEMISTRY", "ENGLISH"], ["PHYSICS", "PHYSICS", "CHEMISTRY"]]) {
      await expect(generatePreview({ ...request, subjectCodes })).rejects.toMatchObject({ status: 422, code: "PREVIEW_SUBJECTS_INVALID" });
    }
    const result = await generatePreview({ ...request, subjectCodes: ["HISTORY", "PHYSICS", "CHEMISTRY"] });
    expect(result.status).toBe("GENERATED");
    if (result.status !== "GENERATED") return;
    const science = result.paper.parts[2]!;
    // Ba môn đã chọn theo thứ tự môn trong cấu trúc, số câu 17, 17, 16 (D25), không theo thứ tự gửi lên.
    const perSubject = new Map<string, number>();
    for (const block of science.blocks) perSubject.set(block.subjectCode, (perSubject.get(block.subjectCode) ?? 0) + block.questionCount);
    expect([...perSubject]).toEqual([
      ["PHYSICS", 17],
      ["CHEMISTRY", 17],
      ["HISTORY", 16],
    ]);
    expect(result.paper.totalQuestions).toBe(150);
  });

  it("sinh theo một phiên bản đã công bố", async () => {
    const result = await generatePreview({ examId: HSA, pathId: EN, seed: 1, version: 2 });
    expect(result.status).toBe("GENERATED");
    await expect(generatePreview({ examId: HSA, pathId: "opt-khong-co", seed: 1, version: 2 })).rejects.toMatchObject({ status: 404 });
  });
});

describe("listExamVersions / getExamVersion", () => {
  it("bản làm việc, thay đổi so với bản đang phục vụ và lịch sử công bố", async () => {
    const list = await listExamVersions({ examId: HSA });
    expect(list.workingCopy?.baseVersion).toBe(2);
    expect(list.workingCopy?.unpublishedPaths).toHaveLength(2);
    expect(list.changes.map((change) => [change.kind, change.warning])).toEqual([
      ["OPTION_ADDED", "INVALID"],
      ["MATRIX_CHANGED", null],
      ["ORDER_RULE_CHANGED", null],
    ]);
    expect(list.items.map((row) => [row.version, row.state, row.attemptCount, row.publishedPaths.length])).toEqual([
      [2, "SERVING", 1240, 2],
      [1, "RETIRED", 312, 2],
    ]);
    const v2 = await getExamVersion({ examId: HSA, version: 2 });
    expect(v2.parts[2]!.options.map((option) => option.code)).toEqual(["ENGLISH", "SCIENCE"]);
  });
});

describe("publishExam", () => {
  it("công bố một phần: phiên bản mới cho đường đã chọn, đường còn lại giữ bản cũ", async () => {
    await getExamCheck({ examId: HSA });
    const response = await publishExam({ examId: HSA, pathIds: [EN], note: "  Sửa ma trận  ", expectedRevision: rev(HSA) });
    expect(response.version).toBe(3);
    expect(response.keptPaths).toEqual([{ pathId: SCIENCE, version: 2 }]);

    const exam = examStore.require(HSA);
    expect(exam.servingVersion).toBe(3);
    expect(exam.hasUnpublishedChanges).toBe(true);
    expect(exam.versions.map((item) => [item.version, item.state])).toEqual([
      [null, "WORKING"],
      [3, "SERVING"],
      [2, "SERVING"],
      [1, "RETIRED"],
    ]);
    expect(exam.versions[1]!.note).toBe("Sửa ma trận");

    const list = await listExamVersions({ examId: HSA });
    expect(list.changes).toEqual([]);
    expect(list.workingCopy?.unpublishedPaths.map((path) => path.pathId)).toEqual([SCIENCE]);
    expect(list.items[1]!.servingPaths.map((path) => path.pathId)).toEqual([SCIENCE]);
  });

  it("công bố mọi đường: bản cũ thành đã thay thế, hết bản sửa chưa công bố", async () => {
    await runExamCheck({ examId: "ex-qda-l-ta-001" });
    await publishExam({ examId: "ex-qda-l-ta-001", pathIds: ["default"], note: null, expectedRevision: rev("ex-qda-l-ta-001") });
    const exam = examStore.require("ex-qda-l-ta-001");
    expect(exam.hasUnpublishedChanges).toBe(false);
    expect(exam.versions.map((item) => [item.version, item.state])).toEqual([
      [3, "SERVING"],
      [2, "RETIRED"],
      [1, "RETIRED"],
    ]);
    expect((await listExamVersions({ examId: "ex-qda-l-ta-001" })).workingCopy).toBeNull();
  });

  it("từ chối đường không hợp lệ, chưa chọn đường, hoặc chưa kiểm tra", async () => {
    await expect(publishExam({ examId: HSA, pathIds: [SCIENCE], note: null, expectedRevision: rev(HSA) })).rejects.toMatchObject({
      status: 422,
      code: "PATH_NOT_VALID",
    });
    await expect(publishExam({ examId: HSA, pathIds: [], note: null, expectedRevision: rev(HSA) })).rejects.toMatchObject({ code: "NO_PATH_SELECTED" });
    await expect(publishExam({ examId: HSA, pathIds: [EN], note: null, expectedRevision: rev(HSA) + 2 })).rejects.toMatchObject({
      status: 412,
      code: "EXAM_REVISION_CONFLICT",
    });
    await expect(publishExam({ examId: "ex-hsa-f-002", pathIds: ["opt-sci-3"], note: null, expectedRevision: rev("ex-hsa-f-002") })).rejects.toMatchObject({
      status: 409,
      code: "CHECK_REQUIRED",
    });
    const nothing = publishExam({ examId: "ex-qda-f-001", pathIds: ["default"], note: null, expectedRevision: rev("ex-qda-f-001") });
    await expect(nothing).rejects.toBeInstanceOf(MockApiError);
    await expect(nothing).rejects.toMatchObject({ status: 409, code: "NOTHING_TO_PUBLISH" });
  });

  it("công bố lần đầu bài nháp chuyển sang Đang phục vụ", async () => {
    const check = await runExamCheck({ examId: "ex-hsa-f-002" });
    const valid = check.paths.filter((path) => path.result === "VALID").map((path) => path.pathId);
    expect(valid.length).toBeGreaterThan(0);
    await publishExam({ examId: "ex-hsa-f-002", pathIds: valid, note: null, expectedRevision: rev("ex-hsa-f-002") });
    const exam = examStore.require("ex-hsa-f-002");
    expect(exam.status).toBe("PUBLISHED");
    expect(exam.servingVersion).toBe(1);
  });
});

describe("discontinueExam", () => {
  it("ngừng bán bài đang phục vụ, bài chưa công bố thì báo 409", async () => {
    const updated = await discontinueExam({ examId: "ex-qda-f-001", expectedRevision: rev("ex-qda-f-001") });
    expect(updated.status).toBe("DISCONTINUED");
    expect(examStore.require("ex-qda-f-001").status).toBe("DISCONTINUED");
    await expect(
      discontinueExam({ examId: "ex-hsa-f-002", expectedRevision: rev("ex-hsa-f-002") }),
    ).rejects.toMatchObject({ status: 409, code: "EXAM_NOT_PUBLISHED" });
  });

  it("revision cũ báo 412 EXAM_REVISION_CONFLICT", async () => {
    await expect(discontinueExam({ examId: "ex-qda-f-001", expectedRevision: rev("ex-qda-f-001") + 3 })).rejects.toMatchObject({
      status: 412,
      code: "EXAM_REVISION_CONFLICT",
    });
    expect(examStore.require("ex-qda-f-001").status).toBe("PUBLISHED");
  });
});

describe("startDeepCheck / getCheckRun", () => {
  it("RUNNING hai lần rồi DONE ghi kết quả vào bài, lần hỏi sau trả cùng kết quả", async () => {
    const { runId } = await startDeepCheck(HSA);
    for (let poll = 1; poll < DEEP_CHECK_POLLS_UNTIL_DONE; poll += 1) {
      await expect(getCheckRun(HSA, runId)).resolves.toMatchObject({ runId, state: "RUNNING", result: null, errorCode: null });
    }
    const done = await getCheckRun(HSA, runId);
    expect(done.state).toBe("DONE");
    expect(done.errorCode).toBeNull();
    const result = done.result;
    expect(result).not.toBeNull();
    expect(result!.examId).toBe(HSA);

    // Kết quả kiểm tra kỹ ghi vào bài như runExamCheck (thời gian xử lý dài hơn).
    const exam = examStore.require(HSA);
    expect(exam.check).toMatchObject({ status: result!.status, issueCount: result!.issueCount, checkedAt: result!.checkedAt });
    expect(reviewStore.getCheck(HSA)).toEqual(result);

    // Hỏi lại sau DONE: cùng runId trả cùng kết quả và checkedAt đã lưu, không tính lại.
    const again = await getCheckRun(HSA, runId);
    expect(again.state).toBe("DONE");
    expect(again.result).toEqual(result);
    expect(again.result!.checkedAt).toBe(result!.checkedAt);
  });

  it("bài thi khác hoặc runId không tồn tại báo 404 CHECK_RUN_NOT_FOUND", async () => {
    const { runId } = await startDeepCheck(HSA);
    await expect(getCheckRun("ex-qda-f-001", runId)).rejects.toMatchObject({ status: 404, code: "CHECK_RUN_NOT_FOUND" });
    await expect(getCheckRun(HSA, "run-mock-999")).rejects.toMatchObject({ status: 404, code: "CHECK_RUN_NOT_FOUND" });
  });
});
