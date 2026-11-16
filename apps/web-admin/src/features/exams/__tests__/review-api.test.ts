import { beforeEach, describe, expect, it } from "vitest";
import { questionStore } from "@/features/questions/mock/store";
import { MockApiError } from "@/lib/mock/content-mock";
import { examStore } from "../mock/store";
import { reviewStore } from "../mock/review-store";
import {
  discontinueExam,
  generatePreview,
  getExamCheck,
  getExamVersion,
  listExamVersions,
  publishExam,
  runExamCheck,
} from "../review-api";

const HSA = "ex-hsa-f-001";

/** `revision` hiện tại của bài (D13): lệnh công bố gửi lại làm `expectedRevision`. */
const rev = (examId: string) => examStore.require(examId).revision;
const KH1 = "opt-1";
const EN = "opt-30";
const KH2 = "opt-8";
const KH3 = "opt-15";

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
    expect(result.paths).toHaveLength(3);
    const exam = examStore.require("ex-hsa-f-002");
    expect(exam.check).toEqual({ status: result.status, issueCount: result.issueCount, checkedAt: result.checkedAt });
  });

  it("kiểm tra lại với thời gian dài hơn xác nhận được KH-3", async () => {
    const result = await runExamCheck({ examId: HSA, extendedTimeLimit: true });
    expect(result.timeLimitSeconds).toBe(120);
    expect(result.paths.find((path) => path.pathId === KH3)?.result).toBe("VALID");
    expect(result.issueCount).toBe(1);
    expect(examStore.require(HSA).check.issueCount).toBe(1);
  });
});

describe("generatePreview", () => {
  it("sinh đề đủ số câu, không lặp câu, đúng phân bổ từng ô", async () => {
    const result = await generatePreview({ examId: HSA, pathId: KH1, seed: 7 });
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
    const fillIn = paper.parts[0]!.blocks.find((block) => block.blockId === "blk-23")!;
    expect(fillIn.allocation.every((cell) => cell.required === cell.generated)).toBe(true);
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
    expect(codes(first.paper)).toContain("CH-000001");
  });

  it("đường không hợp lệ thì không sinh đề thiếu câu", async () => {
    const result = await generatePreview({ examId: HSA, pathId: KH2, seed: 1 });
    expect(result.status).toBe("FAILED");
    if (result.status !== "FAILED") return;
    expect(result.reason).toBe("INVALID");
    expect(result.issues[0]).toMatchObject({ code: "CELL_SHORTAGE", required: 3, available: 2 });
    const unconfirmed = await generatePreview({ examId: HSA, pathId: KH3, seed: 1 });
    expect(unconfirmed).toMatchObject({ status: "FAILED", reason: "UNCONFIRMED" });
  });

  it("sinh theo một phiên bản đã công bố", async () => {
    const result = await generatePreview({ examId: HSA, pathId: KH1, seed: 1, version: 2 });
    expect(result.status).toBe("GENERATED");
    await expect(generatePreview({ examId: HSA, pathId: KH3, seed: 1, version: 2 })).rejects.toMatchObject({ status: 404 });
  });
});

describe("listExamVersions / getExamVersion", () => {
  it("bản làm việc, thay đổi so với bản đang phục vụ và lịch sử công bố", async () => {
    const list = await listExamVersions({ examId: HSA });
    expect(list.workingCopy?.baseVersion).toBe(2);
    expect(list.workingCopy?.unpublishedPaths).toHaveLength(4);
    expect(list.changes.map((change) => [change.kind, change.warning])).toEqual([
      ["OPTION_ADDED", "UNCONFIRMED"],
      ["MATRIX_CHANGED", null],
      ["ORDER_RULE_CHANGED", null],
    ]);
    expect(list.items.map((row) => [row.version, row.state, row.attemptCount, row.publishedPaths.length])).toEqual([
      [2, "SERVING", 1240, 3],
      [1, "RETIRED", 312, 3],
    ]);
    const v2 = await getExamVersion({ examId: HSA, version: 2 });
    expect(v2.parts[2]!.options.map((option) => option.code)).toEqual(["ENGLISH", "KH-1", "KH-2"]);
  });
});

describe("publishExam", () => {
  it("công bố một phần: phiên bản mới cho đường đã chọn, đường còn lại giữ bản cũ", async () => {
    await getExamCheck({ examId: HSA });
    const response = await publishExam({ examId: HSA, pathIds: [EN, KH1], note: "  Sửa ma trận  ", expectedRevision: rev(HSA) });
    expect(response.version).toBe(3);
    expect(response.keptPaths).toEqual([{ pathId: KH2, version: 2 }]);

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
    expect(list.workingCopy?.unpublishedPaths.map((path) => path.pathId)).toEqual([KH2, KH3]);
    expect(list.items[1]!.servingPaths.map((path) => path.pathId)).toEqual([KH2]);
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
    await expect(publishExam({ examId: HSA, pathIds: [KH2], note: null, expectedRevision: rev(HSA) })).rejects.toMatchObject({
      status: 422,
      code: "PATH_NOT_VALID",
    });
    await expect(publishExam({ examId: HSA, pathIds: [], note: null, expectedRevision: rev(HSA) })).rejects.toMatchObject({ code: "NO_PATH_SELECTED" });
    await expect(publishExam({ examId: HSA, pathIds: [EN], note: null, expectedRevision: rev(HSA) + 2 })).rejects.toMatchObject({
      status: 412,
      code: "EXAM_REVISION_CONFLICT",
    });
    await expect(publishExam({ examId: "ex-hsa-f-002", pathIds: ["opt-72"], note: null, expectedRevision: rev("ex-hsa-f-002") })).rejects.toMatchObject({
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
    await discontinueExam({ examId: "ex-qda-f-001" });
    expect(examStore.require("ex-qda-f-001").status).toBe("DISCONTINUED");
    await expect(discontinueExam({ examId: "ex-hsa-f-002" })).rejects.toMatchObject({ status: 409, code: "NOT_PUBLISHED" });
  });
});
