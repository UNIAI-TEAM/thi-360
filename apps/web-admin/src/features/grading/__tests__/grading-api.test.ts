import { afterEach, beforeEach, describe, expect, it } from "vitest";
import { questionStore } from "@/features/questions/mock/store";
import type { QuestionDetail } from "@/features/questions/types";
import { MockApiError } from "@/lib/mock/content-mock";
import {
  finishGrading,
  getGradingAttempt,
  getGradingFilterOptions,
  getGradingStatusCounts,
  listGradingAttempts,
  saveGradingDraft,
} from "../api";
import { attemptStore } from "../mock/store";
import { UNASSIGNED_GRADER } from "../types";

beforeEach(() => window.history.replaceState(null, "", "/grading"));

afterEach(() => {
  attemptStore.reset();
  questionStore.reset();
  window.history.replaceState(null, "", "/grading");
});

async function expectApiError(promise: Promise<unknown>, status: number, code: string) {
  const error = await promise.then(
    () => null,
    (reason: unknown) => reason,
  );
  expect(error).toBeInstanceOf(MockApiError);
  expect((error as MockApiError).status).toBe(status);
  expect((error as MockApiError).code).toBe(code);
}

describe("grading api: danh sach", () => {
  it("nop truoc len truoc, co du ba ky thi BCA, SPT, THPTQG", async () => {
    const page = await listGradingAttempts({});
    expect(page.totalElements).toBe(11);
    const submitted = page.content.map((item) => item.submittedAt);
    expect(submitted).toEqual([...submitted].sort());
    expect(new Set(page.content.map((item) => item.exam.kindCode))).toEqual(new Set(["BCA", "SPT", "THPTQG"]));
  });

  it("loc theo trang thai, ky thi, bai thi, nguoi cham va tim theo ten", async () => {
    expect((await listGradingAttempts({ status: "SCORED" })).totalElements).toBe(4);
    expect((await listGradingAttempts({ examKindCode: "SPT" })).totalElements).toBe(4);
    expect((await listGradingAttempts({ examId: "ex-thptqg-f-van-001" })).totalElements).toBe(3);
    expect((await listGradingAttempts({ graderId: UNASSIGNED_GRADER })).content.map((item) => item.id)).toEqual([
      "att-bca-001",
      "att-spt-001",
      "att-thpt-001",
    ]);
    expect((await listGradingAttempts({ graderId: "u-grader-01" })).totalElements).toBe(4);
    expect((await listGradingAttempts({ q: "hv-002010" })).content[0]?.id).toBe("att-spt-001");
  });

  it("so luot theo trang thai bo qua dieu kien trang thai", async () => {
    expect(await getGradingStatusCounts({ status: "SCORED", examKindCode: "BCA" })).toEqual({
      total: 4,
      byStatus: { GRADING: 3, SCORED: 1 },
    });
  });

  it("tong diem = trac nghiem + tu luan khi da co diem; dang cham thi chua co", async () => {
    const page = await listGradingAttempts({ examKindCode: "BCA" });
    const scored = page.content.find((item) => item.id === "att-bca-003")!;
    expect(scored).toMatchObject({ objectiveScore: 47.25, essayScore: 11, totalScore: 58.25, essayMaxScore: 25 });
    const grading = page.content.find((item) => item.id === "att-bca-002")!;
    expect(grading).toMatchObject({ essayScore: null, totalScore: null, gradedEssayCount: 0 });
  });

  it("bo loc: moi bai thi mot lan va danh sach nguoi cham", async () => {
    const options = await getGradingFilterOptions();
    expect(options.exams.map((exam) => exam.code)).toEqual(["BCA-F-001", "SPT-F-TOAN-001", "THPTQG-F-VAN-001"]);
    expect(options.graders).toHaveLength(3);
  });

  it("?mockState=empty tra danh sach rong", async () => {
    window.history.replaceState(null, "", "/grading?mockState=empty");
    expect((await listGradingAttempts({})).totalElements).toBe(0);
  });
});

describe("grading api: chi tiet", () => {
  it("THPTQG Ngu van: cau doc hieu kem doan doc hieu, hai cau viet, tieu chi tong bang diem cau", async () => {
    const detail = await getGradingAttempt("att-thpt-001");
    expect(detail.essays).toHaveLength(7);
    expect(detail.essayMaxScore).toBe(10);
    expect(detail.objectiveMaxScore).toBe(0);
    expect(detail.essays[0]?.passage?.code).toBe("CH-TL-THPT-010");
    expect(detail.essays[6]?.passage).toBeNull();
    for (const item of detail.essays) {
      expect(item.rubric.reduce((total, criterion) => total + criterion.points, 0)).toBeCloseTo(item.maxPoints);
      expect(item.source).toBe("ATTEMPT");
    }
  });

  it("doc de va tieu chi tu ngan hang cau hoi khi trung id", async () => {
    const bankQuestion: QuestionDetail = {
      ...questionStore.all()[0]!,
      id: "gq-spt-toan-23",
      code: "CH-009999",
      type: "ESSAY",
      content: "De tu ngan hang",
      points: 1,
      body: {
        type: "ESSAY",
        gradingGuide: "Huong dan cham",
        rubric: [
          { id: "c1", description: "Dieu kien", points: 0.25 },
          { id: "c9", description: "Tieu chi moi", points: 0.75 },
        ],
        sampleAnswer: null,
        wordLimit: null,
      },
      children: [],
    };
    questionStore.insert(bankQuestion);
    const detail = await getGradingAttempt("att-spt-002");
    const first = detail.essays[0]!;
    expect(first).toMatchObject({ source: "BANK", questionCode: "CH-009999", content: "De tu ngan hang" });
    // Diem da luu theo id tieu chi: c1 giu nguyen, tieu chi moi chua co diem.
    expect(first.scores).toEqual([
      { criterionId: "c1", score: 0 },
      { criterionId: "c9", score: null },
    ]);
  });

  it("lot thi khong ton tai: 404", async () => {
    await expectApiError(getGradingAttempt("khong-co"), 404, "NOT_FOUND");
  });
});

describe("grading api: luu nhap va hoan tat", () => {
  it("luu nhap giu diem, nhan luot cho nguoi cham hien tai, van Dang cham", async () => {
    const saved = await saveGradingDraft({
      attemptId: "att-bca-001",
      essays: [
        {
          questionId: "gq-bca-van-01",
          scores: [
            { criterionId: "c1", score: 2 },
            { criterionId: "c2", score: null },
          ],
          comment: "  Can them dan chung  ",
        },
      ],
    });
    expect(saved.status).toBe("GRADING");
    expect(saved.grader?.id).toBe("u-grader-01");
    expect(saved.essays[0]?.scores.slice(0, 2)).toEqual([
      { criterionId: "c1", score: 2 },
      { criterionId: "c2", score: null },
    ]);
    expect(saved.essays[0]?.comment).toBe("Can them dan chung");
    expect((await getGradingAttempt("att-bca-001")).essays[0]?.scores[0]?.score).toBe(2);
  });

  it("diem vuot tieu chi: 422 SCORE_OUT_OF_RANGE", async () => {
    await expectApiError(
      saveGradingDraft({
        attemptId: "att-bca-001",
        essays: [{ questionId: "gq-bca-van-01", scores: [{ criterionId: "c1", score: 3 }], comment: "" }],
      }),
      422,
      "SCORE_OUT_OF_RANGE",
    );
  });

  it("hoan tat khi con tieu chi trong: 422 GRADING_INCOMPLETE", async () => {
    await expectApiError(
      finishGrading({
        attemptId: "att-bca-001",
        essays: [{ questionId: "gq-bca-van-01", scores: [{ criterionId: "c1", score: 2 }], comment: "" }],
      }),
      422,
      "GRADING_INCOMPLETE",
    );
  });

  it("hoan tat du diem: Da co diem, tong = trac nghiem + tu luan; luot da co diem khong luu duoc nua", async () => {
    const scores = [2.5, 2, 10, 2, 3.5].map((score, index) => ({ criterionId: `c${index + 1}`, score }));
    const done = await finishGrading({
      attemptId: "att-bca-001",
      essays: [{ questionId: "gq-bca-van-01", scores, comment: "" }],
    });
    expect(done).toMatchObject({ status: "SCORED", essayScore: 20, totalScore: 72.5, gradedEssayCount: 1 });
    expect(done.gradedAt).not.toBeNull();
    await expectApiError(
      saveGradingDraft({ attemptId: "att-bca-001", essays: [] }),
      409,
      "ATTEMPT_ALREADY_SCORED",
    );
  });
});
