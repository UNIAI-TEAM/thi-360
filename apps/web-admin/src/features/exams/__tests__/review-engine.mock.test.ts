import { beforeEach, describe, expect, it } from "vitest";
import { questionStore } from "@/features/questions/mock/store";
import { reviewStore } from "../mock/review-store";
import { examStore } from "../mock/store";
import { DEEP_CHECK_POLLS_UNTIL_DONE } from "../review-api.mock";
import { generatePreview, getCheckRun, getExamCheck, runExamCheck, startDeepCheck } from "../review-api";
import { pinInMemoryBackend } from "./in-memory-backend";

/**
 * Bộ tính của nhánh mẫu (`review-api.mock.ts`): kiểm tra cấu hình, kiểm tra kỹ và sinh đề xem trước tính trên kho mẫu
 * trong bộ nhớ (`mock/review-store`, `questions/mock/store`). Ở nhánh thật các việc này do máy chủ làm, client chỉ
 * hiển thị (xem `review-view`, `preview-pick`, `review-kinds`, `deep-check` chạy trên `ExamsBackend`), nên tệp này chỉ
 * kiểm bộ tính mẫu và bị xóa cùng `review-api.mock.ts` khi QA bỏ lớp mẫu.
 */
pinInMemoryBackend();

const HSA = "ex-hsa-f-001";
const EN = "opt-30";
/** Phương án Khoa học (SUBJECT_CHOICE) của bài mẫu: HSA-F-001 thiếu nguồn Hóa học Lớp 12. */
const SCIENCE = "opt-sci-1";
/** HSA-F-000: mọi cách chọn môn đủ nguồn. */
const SOURCED = "ex-hsa-f-000";
const SOURCED_SCIENCE = "opt-sci-4";
const BCA = "ex-bca-f-001";
const THPTQG = "ex-thptqg-tin-001";

/** Mẫu BCA cố ý có một câu tự luận chưa có tiêu chí chấm; bổ sung để đường CA1 sinh được đề. */
function addMissingRubric() {
  questionStore.update("q-7002", (question) =>
    question.body.type === "ESSAY"
      ? { ...question, body: { ...question.body, rubric: [{ id: "r1", description: "Nội dung nghị luận", points: 25 }] } }
      : question,
  );
}

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

  it("kiểm tra kỹ xác nhận được cách chọn có Địa lí", async () => {
    const standard = await runExamCheck({ examId: HSA });
    const pick = (result: typeof standard) =>
      result.paths.find((path) => path.pathId === SCIENCE)!.selections.find((item) => item.subjectCodes.join() === "PHYSICS,BIOLOGY,GEOGRAPHY")?.result;
    expect(pick(standard)).toBe("UNCONFIRMED");
    const { runId } = await startDeepCheck(HSA);
    for (let poll = 1; poll < DEEP_CHECK_POLLS_UNTIL_DONE; poll += 1) await getCheckRun(HSA, runId);
    const result = (await getCheckRun(HSA, runId)).result!;
    expect(result.timeLimitSeconds).toBe(120);
    expect(pick(result)).toBe("VALID");
    // Còn lại: chỉ tiêu Hóa học Lớp 12 thiếu nguồn ở cả hai số câu môn có thể nhận (17 câu cần 9, 16 câu cần 8).
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
    const request = { examId: SOURCED, pathId: SOURCED_SCIENCE, seed: 1 };
    for (const subjectCodes of [undefined, [], ["PHYSICS", "CHEMISTRY"], ["PHYSICS", "CHEMISTRY", "ENGLISH"], ["PHYSICS", "PHYSICS", "CHEMISTRY"]]) {
      await expect(generatePreview({ ...request, subjectCodes })).rejects.toMatchObject({ status: 422, code: "PREVIEW_SUBJECTS_INVALID" });
    }
    const result = await generatePreview({ ...request, subjectCodes: ["HISTORY", "PHYSICS", "CHEMISTRY"] });
    expect(result.status).toBe("GENERATED");
    if (result.status !== "GENERATED") return;
    const science = result.paper.parts[2]!;
    // Ba môn đã chọn theo thứ tự môn trong cấu trúc, chia theo nhóm (B10): hai môn tự nhiên 17, môn xã hội 16.
    const perSubject = new Map<string, number>();
    for (const block of science.blocks) perSubject.set(block.subjectCode, (perSubject.get(block.subjectCode) ?? 0) + block.questionCount);
    expect([...perSubject]).toEqual([
      ["PHYSICS", 17],
      ["CHEMISTRY", 17],
      ["HISTORY", 16],
    ]);
    expect(result.paper.totalQuestions).toBe(150);
  });

  it("đường Khoa học, ba môn tự nhiên: môn nhận 16 câu chọn theo hạt giống, cùng hạt giống cùng môn", async () => {
    const countsFor = async (seed: number) => {
      const result = await generatePreview({ examId: SOURCED, pathId: SOURCED_SCIENCE, seed, subjectCodes: ["PHYSICS", "CHEMISTRY", "BIOLOGY"] });
      if (result.status !== "GENERATED") throw new Error("expected paper");
      const perSubject = new Map<string, number>();
      for (const block of result.paper.parts[2]!.blocks) perSubject.set(block.subjectCode, (perSubject.get(block.subjectCode) ?? 0) + block.questionCount);
      return [...perSubject].map(([, count]) => count);
    };
    const seen = new Set<string>();
    for (let seed = 1; seed <= 12; seed += 1) {
      const counts = await countsFor(seed);
      expect([...counts].sort()).toEqual([16, 17, 17]);
      expect(await countsFor(seed)).toEqual(counts);
      seen.add(String(counts.indexOf(16)));
    }
    expect(seen.size).toBeGreaterThan(1);
  });

  it("sinh theo một phiên bản đã công bố", async () => {
    const result = await generatePreview({ examId: HSA, pathId: EN, seed: 1, version: 2 });
    expect(result.status).toBe("GENERATED");
    await expect(generatePreview({ examId: HSA, pathId: "opt-khong-co", seed: 1, version: 2 })).rejects.toMatchObject({ status: 404 });
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

describe("đề xem trước theo bảy kỳ thi", () => {
  it("BCA: điểm từng phần, thời lượng chung, khối tự luận chấm tay kèm tiêu chí chấm", async () => {
    addMissingRubric();
    const result = await generatePreview({ examId: BCA, pathId: "opt-b3-1", seed: 1 });
    if (result.status !== "GENERATED") throw new Error(`expected a paper, got ${result.status}`);
    const { paper } = result;
    expect(paper).toMatchObject({ totalQuestions: 81, totalPoints: 100, totalMinutes: 180, sharedDuration: true, hasManualGrading: true });
    expect(paper.parts.map((part) => [part.points, part.maxPoints])).toEqual([
      [25, 25],
      [60, 60],
      [15, 15],
    ]);
    expect(paper.parts.every((part) => part.durationMinutes === null)).toBe(true);
    const essay = paper.parts[0]!.blocks[0]!;
    expect(essay.manualGrading).toBe(true);
    const item = essay.items[0]!;
    if (item.kind !== "QUESTION") throw new Error("essay block must hold single questions");
    expect(item.question.rubric).not.toBeNull();
  });

  it("BCA: câu tự luận thiếu tiêu chí chấm thì không sinh đề", async () => {
    const result = await generatePreview({ examId: BCA, pathId: "opt-b3-1", seed: 1 });
    expect(result.status).toBe("FAILED");
    if (result.status === "FAILED") expect(result.issues.map((issue) => issue.code)).toContain("RUBRIC_MISSING");
  });

  it("THPTQG Tin học: đường Khoa học máy tính có 28 câu, 10 điểm", async () => {
    const result = await generatePreview({ examId: THPTQG, pathId: "opt-t2-cs", seed: 3 });
    if (result.status !== "GENERATED") throw new Error(`expected a paper, got ${result.status}`);
    expect(result.paper).toMatchObject({ totalQuestions: 28, totalPoints: 10, totalMinutes: 50, hasManualGrading: false });
    expect(result.paper.parts.map((part) => part.points)).toEqual([6, 4]);
  });

});

describe("generatePreview: chỉ tiêu cấp môn trên đề", () => {
  it("mỗi môn trên đề kèm số câu theo B10 và chỉ tiêu dạng trả lời, khối lớp, độ khó khớp đề sinh", async () => {
    const result = await generatePreview({
      examId: SOURCED,
      pathId: SOURCED_SCIENCE,
      seed: 3,
      subjectCodes: ["BIOLOGY", "HISTORY", "GEOGRAPHY"],
    });
    if (result.status !== "GENERATED") throw new Error(`expected a paper, got ${result.status}`);
    const { subjects } = result.paper;
    // Hai môn xã hội một môn tự nhiên: môn tự nhiên 16 câu, hai môn xã hội 17 câu; môn cố định đi trước.
    expect(subjects.map((subject) => [subject.subjectCode, subject.questionCount])).toEqual([
      ["MATH", 50],
      ["LITERATURE", 50],
      ["BIOLOGY", 16],
      ["HISTORY", 17],
      ["GEOGRAPHY", 17],
    ]);
    expect(subjects.reduce((total, subject) => total + subject.questionCount, 0)).toBe(result.paper.totalQuestions);
    const withQuotas = subjects.filter((subject) => subject.quotas.length > 0);
    expect(withQuotas.length).toBeGreaterThan(0);
    for (const subject of withQuotas) {
      for (const kind of ["GRADE", "DIFFICULTY"] as const) {
        const rows = subject.quotas.filter((quota) => quota.kind === kind);
        // Chỉ tiêu theo khối lớp hoặc độ khó cộng lại bằng số câu của môn, và đề sinh đúng từng chỉ tiêu.
        if (rows.length) expect(rows.reduce((total, quota) => total + quota.required, 0)).toBe(subject.questionCount);
      }
      expect(subject.quotas.every((quota) => quota.required === quota.generated)).toBe(true);
    }
  });
});
