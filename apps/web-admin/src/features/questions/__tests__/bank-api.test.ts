import { describe, expect, it } from "vitest";
import { ContentApiError } from "@/features/content/api-error";
import {
  bulkMarkQuestionsReady,
  bulkRetireQuestions,
  getQuestionReadiness,
  getQuestionRetireImpact,
  getQuestionStatusCounts,
  listQuestionAuthors,
  listQuestions,
  markQuestionReady,
  retireQuestion,
} from "../api";
import { apiReadiness, withQuestionsServer } from "./api-fixtures";

/** Câu Nháp đủ điều kiện (q-0002) và câu Nháp chưa chọn đáp án đúng (q-0006) trong bộ mẫu. */
const READY_DRAFT = "q-0002";
const NOT_READY_DRAFT = "q-0006";

const store = withQuestionsServer({
  readiness: {
    [NOT_READY_DRAFT]: apiReadiness(false, [{ code: "NO_CORRECT_OPTION", path: "body.options[0].correct" }]),
  },
  usage: {
    "q-0003": { examIds: ["ex-1", "ex-2", "ex-3"], ledger: [{ examId: "ex-1", kind: "ATTEMPT", versionNo: 1 }] },
  },
});

const rev = (id: string) => store.require(id).revision;

describe("API Ngân hàng câu hỏi (nhánh thật, client giả)", () => {
  it("đếm câu theo trạng thái với cùng bộ lọc, bỏ điều kiện trạng thái", async () => {
    const counts = await getQuestionStatusCounts({ subjectCode: "MATH", status: "READY" });
    const all = await listQuestions({ subjectCode: "MATH", size: 100 });
    expect(counts.total).toBe(all.totalElements);
    expect(counts.byStatus.DRAFT + counts.byStatus.READY + counts.byStatus.RETIRED).toBe(counts.total);
  });

  it("lọc MAPID theo Bài gồm cả kiến thức bên dưới", async () => {
    const page = await listQuestions({ knowledgeIds: ["kn-1D4-1"], size: 100 });
    expect(page.content.length).toBeGreaterThan(0);
    expect(page.content.every((q) => q.knowledge.some((ref) => ref.code.startsWith("1D4-1")))).toBe(true);
  });

  it("lọc theo ngày tạo", async () => {
    const page = await listQuestions({ createdFrom: "2099-01-01", size: 100 });
    expect(page.totalElements).toBe(0);
  });

  it("danh sách đổi sang kiểu của web: bản Nháp hiện workingVersion thay phiên bản phục vụ", async () => {
    const page = await listQuestions({ size: 100 });
    const draft = page.content.find((q) => q.id === READY_DRAFT)!;
    expect(draft.status).toBe("DRAFT");
    expect(draft.version).toBe(1);
    const ready = page.content.find((q) => q.status === "READY")!;
    expect(ready.revision).toBeGreaterThan(0);
  });

  it("chuyển sang Sẵn sàng khi đủ điều kiện, 422 khi thiếu, 409 khi đã Sẵn sàng", async () => {
    const updated = await markQuestionReady({ id: READY_DRAFT, expectedRevision: rev(READY_DRAFT) });
    expect(updated.status).toBe("READY");
    expect(store.require(READY_DRAFT).status).toBe("READY");

    await expect(markQuestionReady({ id: READY_DRAFT, expectedRevision: rev(READY_DRAFT) })).rejects.toMatchObject({
      status: 409,
      code: "QUESTION_ALREADY_READY",
    });
    await expect(markQuestionReady({ id: NOT_READY_DRAFT, expectedRevision: rev(NOT_READY_DRAFT) })).rejects.toBeInstanceOf(
      ContentApiError,
    );
    await expect(markQuestionReady({ id: NOT_READY_DRAFT, expectedRevision: rev(NOT_READY_DRAFT) })).rejects.toMatchObject({
      status: 422,
      code: "QUESTION_NOT_READY",
    });
    expect((await getQuestionReadiness(NOT_READY_DRAFT)).ready).toBe(false);
  });

  it("lệnh ghi lệch revision bị từ chối 412 QUESTION_REVISION_CONFLICT và không đổi trạng thái", async () => {
    const stale = rev(READY_DRAFT) + 5;
    await expect(markQuestionReady({ id: READY_DRAFT, expectedRevision: stale })).rejects.toMatchObject({
      status: 412,
      code: "QUESTION_REVISION_CONFLICT",
    });
    await expect(retireQuestion({ id: "q-0001", expectedRevision: stale })).rejects.toMatchObject({
      status: 412,
      code: "QUESTION_REVISION_CONFLICT",
    });
    expect(store.require(READY_DRAFT).status).toBe("DRAFT");
    const before = rev(READY_DRAFT);
    await markQuestionReady({ id: READY_DRAFT, expectedRevision: before });
    expect(store.require(READY_DRAFT).revision).toBe(before + 1);
  });

  it("ngừng sử dụng không xóa câu; ngừng lần hai báo 409", async () => {
    const before = store.all().length;
    const retired = await retireQuestion({ id: "q-0001", expectedRevision: rev("q-0001") });
    expect(retired.status).toBe("RETIRED");
    expect(store.all()).toHaveLength(before);
    await expect(retireQuestion({ id: "q-0001", expectedRevision: rev("q-0001") })).rejects.toMatchObject({ status: 409 });
  });

  it("hàng loạt: bỏ qua câu chưa đủ điều kiện và câu đã ở trạng thái đích", async () => {
    const result = await bulkMarkQuestionsReady({ ids: [READY_DRAFT, NOT_READY_DRAFT, "q-0001", "khong-co"] });
    expect(result.succeeded.map((q) => q.id)).toEqual([READY_DRAFT]);
    expect(result.skipped.map((item) => [item.id, item.reason])).toEqual([
      [NOT_READY_DRAFT, "NOT_READY"],
      ["q-0001", "ALREADY_IN_STATUS"],
      ["khong-co", "NOT_FOUND"],
    ]);

    const retire = await bulkRetireQuestions({ ids: ["q-0001", "q-0004"] });
    expect(retire.succeeded.map((q) => q.id)).toEqual(["q-0001"]);
    expect(retire.skipped[0]).toMatchObject({ id: "q-0004", reason: "ALREADY_IN_STATUS" });
  });

  it("nơi đang dùng: đúng số bài thi theo usedInExamCount, câu đã dùng không xóa hẳn được", async () => {
    const impact = await getQuestionRetireImpact("q-0003");
    expect(impact.exams).toHaveLength(3);
    expect(impact.hardDeleteBlocked).toBe(true);
    expect(new Set(impact.exams.map((exam) => exam.examId)).size).toBe(impact.exams.length);

    const unused = await getQuestionRetireImpact("q-0002");
    expect(unused).toMatchObject({ exams: [], hardDeleteBlocked: false });
  });

  it("danh sách người tạo", async () => {
    expect((await listQuestionAuthors()).map((person) => person.id)).toContain("u-lan");
  });
});
