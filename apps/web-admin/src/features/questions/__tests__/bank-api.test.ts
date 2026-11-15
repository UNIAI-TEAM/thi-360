import { afterEach, describe, expect, it } from "vitest";
import { MockApiError } from "@/lib/mock/content-mock";
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
import { questionStore } from "../mock/store";

afterEach(() => questionStore.reset());

/** Cau Nhap du dieu kien va cau Nhap thieu kien thuc chinh trong du lieu mau. */
const READY_DRAFT = "q-0002";
const NOT_READY_DRAFT = "q-0006";

describe("API Ngan hang cau hoi (du lieu mau)", () => {
  it("dem cau theo trang thai voi cung bo loc, bo dieu kien trang thai", async () => {
    const counts = await getQuestionStatusCounts({ subjectCode: "MATH", status: "READY" });
    const all = await listQuestions({ subjectCode: "MATH", size: 100 });
    expect(counts.total).toBe(all.totalElements);
    expect(counts.byStatus.DRAFT + counts.byStatus.READY + counts.byStatus.RETIRED).toBe(counts.total);
  });

  it("loc MAPID theo Bai gom ca kien thuc ben duoi", async () => {
    const page = await listQuestions({ knowledgeIds: ["kn-1D4-1"], size: 100 });
    expect(page.content.length).toBeGreaterThan(0);
    expect(page.content.every((q) => q.knowledge.some((ref) => ref.code.startsWith("1D4-1")))).toBe(true);
  });

  it("loc theo ngay tao", async () => {
    const page = await listQuestions({ createdFrom: "2099-01-01", size: 100 });
    expect(page.totalElements).toBe(0);
  });

  it("danh sach kem goi y dieu kien con thieu cho cau chua Sẵn sàng", async () => {
    const page = await listQuestions({ size: 100 });
    const draft = page.content.find((q) => q.id === NOT_READY_DRAFT)!;
    expect(draft.readinessHint).toMatchObject({ code: "PRIMARY_KNOWLEDGE_MISSING" });
    expect(page.content.find((q) => q.status === "READY")!.readinessHint).toBeNull();
  });

  it("chuyen sang Sẵn sàng khi du dieu kien, 422 khi thieu, 409 khi da Sẵn sàng", async () => {
    const updated = await markQuestionReady({ id: READY_DRAFT });
    expect(updated.status).toBe("READY");
    expect(questionStore.require(READY_DRAFT).status).toBe("READY");

    await expect(markQuestionReady({ id: READY_DRAFT })).rejects.toMatchObject({ status: 409, code: "QUESTION_ALREADY_READY" });
    await expect(markQuestionReady({ id: NOT_READY_DRAFT })).rejects.toBeInstanceOf(MockApiError);
    await expect(markQuestionReady({ id: NOT_READY_DRAFT })).rejects.toMatchObject({ status: 422, code: "QUESTION_NOT_READY" });
    expect((await getQuestionReadiness(NOT_READY_DRAFT)).ready).toBe(false);
  });

  it("ngung su dung khong xoa cau; ngung lan hai bao 409", async () => {
    const before = questionStore.all().length;
    const retired = await retireQuestion({ id: "q-0001" });
    expect(retired.status).toBe("RETIRED");
    expect(questionStore.all()).toHaveLength(before);
    await expect(retireQuestion({ id: "q-0001" })).rejects.toMatchObject({ status: 409 });
  });

  it("hang loat: bo qua cau chua du dieu kien va cau da o trang thai dich", async () => {
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

  it("noi dang dung: dung so bai thi theo usedInExamCount, cau da dung khong xoa han duoc", async () => {
    const used = questionStore.require("q-0003");
    const impact = await getQuestionRetireImpact(used.id);
    expect(impact.exams).toHaveLength(used.usedInExamCount);
    expect(impact.hardDeleteBlocked).toBe(true);
    expect(new Set(impact.exams.map((exam) => exam.examId)).size).toBe(impact.exams.length);

    const unused = await getQuestionRetireImpact("q-0002");
    expect(unused).toMatchObject({ exams: [], hardDeleteBlocked: false });
  });

  it("danh sach nguoi tao", async () => {
    expect((await listQuestionAuthors()).map((person) => person.id)).toContain("u-lan");
  });
});
