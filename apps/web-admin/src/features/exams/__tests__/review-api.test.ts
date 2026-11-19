import { beforeEach, describe, expect, it } from "vitest";
import { getExam } from "../api";
import {
  discontinueExam,
  getCheckRun,
  getExamCheck,
  getExamVersion,
  listExamVersions,
  mapIssue,
  publishExam,
  runExamCheck,
  startDeepCheck,
} from "../review-api";
import { ExamsApiError } from "../review-api";
import { ExamsBackend } from "./fake-exams-backend";

let backend: ExamsBackend;

beforeEach(() => {
  backend = new ExamsBackend();
  backend.install();
});

const HSA = "ex-hsa-f-001";
/** Phương án Tiếng Anh và Khoa học (id wire) của bài mẫu HSA-F-001. */
const EN = "opt-30";
const SCIENCE = "opt-sci-1";
const VALID_CHECK = { status: "VALID", checkedAt: "2026-11-15T09:28:00+07:00", durationSeconds: 31, timeLimitSeconds: 30, issueCount: 0 };

async function rejected(promise: Promise<unknown>) {
  const error = await promise.then(
    () => undefined,
    (caught: unknown) => caught,
  );
  expect(error).toBeInstanceOf(ExamsApiError);
  return error as ExamsApiError;
}

describe("runExamCheck / getExamCheck", () => {
  it("trả null khi chưa kiểm tra, kết quả đã seed sau đó", async () => {
    expect(await getExamCheck({ examId: "ex-hsa-f-002" })).toBeNull();
    backend.seedCheck(HSA, {
      examId: HSA,
      ...VALID_CHECK,
      paths: [
        { pathId: EN, result: "VALID", issues: [] },
        { pathId: SCIENCE, result: "VALID", issues: [] },
      ],
    });
    const seeded = await getExamCheck({ examId: HSA });
    expect(seeded?.checkedAt).toBe("2026-11-15T09:28:00+07:00");
    expect(seeded?.durationSeconds).toBe(31);
  });

  it("chạy kiểm tra cập nhật trạng thái kiểm tra của bài thi", async () => {
    const result = await runExamCheck({ examId: "ex-hsa-f-002" });
    expect(result.status).toBe("VALID");
    const exam = await getExam("ex-hsa-f-002");
    expect(exam.check).toEqual({ status: result.status, issueCount: result.issueCount, checkedAt: result.checkedAt });
  });

  it("kiểm tra kỹ hoàn thành thì kết quả ghi vào bài", async () => {
    const result = { examId: HSA, ...VALID_CHECK, timeLimitSeconds: 120, issueCount: 2, paths: [] };
    const { runId } = await startDeepCheck(HSA);
    backend.queueRun(runId, { state: "RUNNING" }, { state: "RUNNING" }, { state: "DONE", result });
    expect((await getCheckRun(HSA, runId)).state).toBe("RUNNING");
    expect((await getCheckRun(HSA, runId)).state).toBe("RUNNING");
    const done = await getCheckRun(HSA, runId);
    expect(done.state).toBe("DONE");
    expect(done.result?.examId).toBe(HSA);
    expect(done.result?.timeLimitSeconds).toBe(120);
    const exam = await getExam(HSA);
    expect(exam.check.issueCount).toBe(2);
    // Hỏi lại sau DONE: cùng runId trả cùng kết quả.
    const again = await getCheckRun(HSA, runId);
    expect(again.state).toBe("DONE");
    expect(again.result).toEqual(done.result);
  });

  it("bài thi khác hoặc runId không tồn tại báo 404 CHECK_RUN_NOT_FOUND", async () => {
    const { runId } = await startDeepCheck(HSA);
    await expect(getCheckRun("ex-qda-f-001", runId)).rejects.toMatchObject({ status: 404, code: "CHECK_RUN_NOT_FOUND" });
    await expect(getCheckRun(HSA, "run-mock-999")).rejects.toMatchObject({ status: 404, code: "CHECK_RUN_NOT_FOUND" });
  });
});

describe("listExamVersions / getExamVersion", () => {
  it("bản làm việc có baseVersion và đường chưa công bố; lịch sử giữ số lần bán", async () => {
    const list = await listExamVersions({ examId: HSA });
    expect(list.workingCopy?.baseVersion).toBe(2);
    expect(list.workingCopy?.unpublishedPaths.map((path) => path.pathId)).toEqual([EN, SCIENCE]);
    expect(list.items.map((row) => [row.version, row.state, row.attemptCount])).toEqual([
      [2, "SERVING", 1240],
      [1, "RETIRED", 312],
    ]);
    const v2 = await getExamVersion({ examId: HSA, version: 2 });
    expect(v2.parts[2]!.options.map((option) => option.code)).toEqual(["ENGLISH", "SCIENCE"]);
  });
});

describe("publishExam", () => {
  const seedValidCheck = (examId: string, pathIds: string[]) =>
    backend.seedCheck(examId, {
      examId,
      ...VALID_CHECK,
      paths: pathIds.map((pathId) => ({ pathId, result: "VALID", issues: [] })),
    });

  it("công bố một phần: phiên bản mới cho đường đã chọn, đường còn lại giữ bản cũ", async () => {
    seedValidCheck(HSA, [EN, SCIENCE]);
    const exam = await getExam(HSA);
    const response = await publishExam({ examId: HSA, pathIds: [EN], note: "  Sửa ma trận  ", expectedRevision: exam.revision });
    expect(response.version).toBe(3);
    expect(response.keptPaths).toEqual([{ pathId: SCIENCE, version: 2 }]);

    const updated = await getExam(HSA);
    expect(updated.servingVersion).toBe(3);
    expect(updated.hasUnpublishedChanges).toBe(true);
    expect(updated.versions.map((item) => [item.version, item.state])).toEqual([
      [null, "WORKING"],
      [3, "SERVING"],
      [2, "SERVING"],
      [1, "RETIRED"],
    ]);
    expect(updated.versions[1]!.note).toBe("Sửa ma trận");

    const list = await listExamVersions({ examId: HSA });
    expect(list.workingCopy?.unpublishedPaths.map((path) => path.pathId)).toEqual([SCIENCE]);
    expect(list.items[1]!.servingPaths.map((path) => path.pathId)).toEqual([SCIENCE]);
  });

  it("công bố mọi đường: bản cũ thành đã thay thế, hết bản sửa chưa công bố", async () => {
    seedValidCheck("ex-qda-l-ta-001", ["default"]);
    const before = await getExam("ex-qda-l-ta-001");
    await publishExam({ examId: "ex-qda-l-ta-001", pathIds: ["default"], note: null, expectedRevision: before.revision });
    const exam = await getExam("ex-qda-l-ta-001");
    expect(exam.hasUnpublishedChanges).toBe(false);
    expect(exam.versions.map((item) => [item.version, item.state])).toEqual([
      [3, "SERVING"],
      [2, "RETIRED"],
      [1, "RETIRED"],
    ]);
    expect((await listExamVersions({ examId: "ex-qda-l-ta-001" })).workingCopy).toBeNull();
  });

  it("từ chối đường không hợp lệ, chưa chọn đường, hoặc chưa kiểm tra", async () => {
    const exam = await getExam(HSA);
    backend.seedCheck(HSA, {
      examId: HSA,
      ...VALID_CHECK,
      issueCount: 1,
      paths: [
        { pathId: EN, result: "VALID", issues: [] },
        { pathId: SCIENCE, result: "INVALID", issues: [] },
      ],
    });
    await expect(publishExam({ examId: HSA, pathIds: [SCIENCE], note: null, expectedRevision: exam.revision })).rejects.toMatchObject({
      status: 422,
      code: "PATH_NOT_VALID",
    });
    seedValidCheck(HSA, [EN, SCIENCE]);
    await expect(publishExam({ examId: HSA, pathIds: [], note: null, expectedRevision: exam.revision })).rejects.toMatchObject({ code: "NO_PATH_SELECTED" });
    await expect(publishExam({ examId: HSA, pathIds: [EN], note: null, expectedRevision: exam.revision + 2 })).rejects.toMatchObject({
      status: 412,
      code: "EXAM_REVISION_CONFLICT",
    });
    const draft = await getExam("ex-hsa-f-002");
    await expect(publishExam({ examId: "ex-hsa-f-002", pathIds: ["opt-sci-3"], note: null, expectedRevision: draft.revision })).rejects.toMatchObject({
      status: 409,
      code: "CHECK_REQUIRED",
    });
    const nothing = await rejected(publishExam({ examId: "ex-qda-f-001", pathIds: ["default"], note: null, expectedRevision: (await getExam("ex-qda-f-001")).revision }));
    expect(nothing).toMatchObject({ status: 409, code: "NOTHING_TO_PUBLISH" });
  });

  it("công bố lần đầu bài nháp chuyển sang Đang phục vụ", async () => {
    seedValidCheck("ex-hsa-f-002", ["opt-sci-3"]);
    const draft = await getExam("ex-hsa-f-002");
    await publishExam({ examId: "ex-hsa-f-002", pathIds: ["opt-sci-3"], note: null, expectedRevision: draft.revision });
    const exam = await getExam("ex-hsa-f-002");
    expect(exam.status).toBe("PUBLISHED");
    expect(exam.servingVersion).toBe(1);
  });
});

describe("discontinueExam", () => {
  it("ngừng bán bài đang phục vụ, bài chưa công bố thì báo 409", async () => {
    const exam = await getExam("ex-qda-f-001");
    const updated = await discontinueExam({ examId: "ex-qda-f-001", expectedRevision: exam.revision });
    expect(updated.status).toBe("DISCONTINUED");
    const draft = await getExam("ex-hsa-f-002");
    await expect(discontinueExam({ examId: "ex-hsa-f-002", expectedRevision: draft.revision })).rejects.toMatchObject({
      status: 409,
      code: "EXAM_NOT_PUBLISHED",
    });
  });

  it("revision cũ báo 412 EXAM_REVISION_CONFLICT", async () => {
    const exam = await getExam("ex-qda-f-001");
    await expect(discontinueExam({ examId: "ex-qda-f-001", expectedRevision: exam.revision + 3 })).rejects.toMatchObject({
      status: 412,
      code: "EXAM_REVISION_CONFLICT",
    });
    expect((await getExam("ex-qda-f-001")).status).toBe("PUBLISHED");
  });
});

describe("mapIssue: khóa dự phòng khi máy chủ không gửi key", () => {
  it("hai vấn đề cùng khối chỉ khác dimension giữ hai khóa khác nhau", () => {
    const count = mapIssue({ code: "BLOCK_COUNT_LOCKED", blockId: "b1", dimension: "COUNT" });
    const setSize = mapIssue({ code: "BLOCK_COUNT_LOCKED", blockId: "b1", dimension: "SET_SIZE" });
    expect(count.key).not.toBe(setSize.key);
  });

  it("hai vấn đề cùng phân đoạn chỉ khác dimension giữ hai khóa khác nhau", () => {
    const commands = mapIssue({ code: "DISPLAY_SEGMENT_MISMATCH", partId: "p1", dimension: "COMMANDS", segmentCode: "P2" });
    const points = mapIssue({ code: "DISPLAY_SEGMENT_MISMATCH", partId: "p1", dimension: "POINTS", segmentCode: "P2" });
    expect(commands.key).not.toBe(points.key);
  });

  it("vấn đề cùng khối chỉ khác segmentCode giữ hai khóa khác nhau", () => {
    const first = mapIssue({ code: "DISPLAY_SEGMENT_MISMATCH", partId: "p1", dimension: "POINTS", segmentCode: "P1" });
    const second = mapIssue({ code: "DISPLAY_SEGMENT_MISMATCH", partId: "p1", dimension: "POINTS", segmentCode: "P2" });
    expect(first.key).not.toBe(second.key);
  });

  it("máy chủ gửi key thì giữ nguyên, không tự ghép", () => {
    expect(mapIssue({ code: "INFO_MISSING", key: "server-key" }).key).toBe("server-key");
  });
});
