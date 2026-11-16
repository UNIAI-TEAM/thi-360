import { afterEach, describe, expect, it } from "vitest";
import { questionStore } from "@/features/questions/mock/store";
import { checkExam, enumeratePaths, yearsNeedConfirmation } from "../check";
import { examStore } from "../mock/store";
import { mockAvailability, mockChoiceName } from "../mock/review-store";
import type { CheckIssue, ExamCheckOutcome } from "../review-types";
import type { ExamDetail } from "../types";

afterEach(() => {
  examStore.reset();
  questionStore.reset();
});

function load(id: string): ExamDetail {
  return structuredClone(examStore.require(id));
}

function run(exam: ExamDetail): ExamCheckOutcome {
  return checkExam(exam, mockAvailability(exam.id, exam.parts), { nameOf: mockChoiceName });
}

function codes(issues: readonly CheckIssue[]): string[] {
  return [...new Set(issues.map((issue) => issue.code))].sort();
}

describe("checkExam theo bảy kỳ thi", () => {
  it("THPTQG Tin học: hai đường (phần chung + một nhánh), 28 câu làm, 30 câu in, 10 điểm; bảng điểm khác chỉ cảnh báo", () => {
    const exam = load("ex-thptqg-tin-001");
    expect(enumeratePaths(exam.parts, mockChoiceName).map((path) => path.choices.map((choice) => choice.name))).toEqual([
      ["Khoa học máy tính"],
      ["Tin học ứng dụng"],
    ]);
    const result = run(exam);
    expect(result.status).toBe("VALID");
    expect(result.printedQuestions).toBe(30);
    expect(result.maxScore).toBe(10);
    for (const path of result.paths) {
      expect(path.result).toBe("VALID");
      expect(path).toMatchObject({ totalQuestions: 28, totalMinutes: 50, totalPoints: 10 });
      // Khối của phần chung nằm trên mọi đường.
      expect(path.blocks.map((block) => block.blockId)).toContain("blk-t2-0");
    }
    const warning = result.paths[0]!.issues.find((issue) => issue.code === "SCORING_MISMATCH");
    expect(warning).toMatchObject({ severity: "WARNING", group: "POINTS", questionCodes: ["CH-007008"] });
    expect(result.groups.find((group) => group.group === "POINTS")?.status).toBe("WARN");
  });

  it("BCA: câu tự luận nguồn thiếu tiêu chí chấm chặn công bố; tổ hợp chờ chuyên gia chặn đường của nó", () => {
    const result = run(load("ex-bca-f-001"));
    expect(result.status).toBe("INVALID");
    expect(result.paths).toHaveLength(4);
    const rubric = result.paths[0]!.issues.find((issue) => issue.code === "RUBRIC_MISSING");
    expect(rubric).toMatchObject({ severity: "ERROR", blockId: "blk-b1", available: 1, questionCodes: ["CH-007002"] });
    const ca4 = result.paths.find((path) => path.choices.some((choice) => choice.code === "CA4"))!;
    expect(codes(ca4.issues)).toContain("OPTION_UNCONFIRMED");
    const ca1 = result.paths.find((path) => path.choices.some((choice) => choice.code === "CA1"))!;
    expect(codes(ca1.issues)).not.toContain("OPTION_UNCONFIRMED");
    expect(ca1.totalPoints).toBe(100);
    expect(codes(ca1.issues)).not.toContain("POINTS_TOTAL_MISMATCH");
    expect(codes(ca1.issues)).not.toContain("SCORING_MISMATCH");
  });

  it("BCA: bổ sung tiêu chí chấm và xác nhận CA4 thì mọi đường hợp lệ (cảnh báo không chặn)", () => {
    questionStore.update("q-7002", (essay) =>
      essay.body.type === "ESSAY"
        ? { ...essay, body: { ...essay.body, rubric: [{ id: "r1", description: "Nội dung", points: 25 }] } }
        : essay,
    );
    const exam = load("ex-bca-f-001");
    for (const part of exam.parts) for (const option of part.options) option.expertConfirmed = true;
    const result = run(exam);
    expect(result.paths.map((path) => path.result)).toEqual(["VALID", "VALID", "VALID", "VALID"]);
    expect(result.status).toBe("VALID");
  });

  it("điểm phần và tổng điểm lệch thang thì không hợp lệ", () => {
    const exam = load("ex-bca-f-001");
    exam.parts[1]!.sections[2]!.blocks[0]!.requiredPointsPerQuestion = 1;
    const issues = run(exam).paths[0]!.issues;
    expect(issues.find((issue) => issue.code === "PART_POINTS_MISMATCH")).toMatchObject({
      partId: "part-b2",
      required: 60,
      available: 65,
    });
    expect(issues.find((issue) => issue.code === "POINTS_TOTAL_MISMATCH")).toMatchObject({ required: 100, available: 105 });
  });

  it("V-ACT: không có thang điểm thì không xét tổng điểm; thiếu câu theo năng lực bị báo", () => {
    const exam = load("ex-vact-f-001");
    const result = run(exam);
    expect(result.maxScore).toBeNull();
    expect(result.status).toBe("VALID");
    expect(result.paths[0]).toMatchObject({ totalQuestions: 120, totalMinutes: 150 });

    const availability = mockAvailability(exam.id, exam.parts);
    availability.blocks["blk-v3"] = {
      ...availability.blocks["blk-v3"]!,
      competencies: [
        { code: "VACT-TD-LOGIC", count: 5 },
        { code: "VACT-TD-SL", count: 40 },
      ],
    };
    const short = checkExam(exam, availability, { nameOf: mockChoiceName });
    expect(short.paths[0]!.issues.find((issue) => issue.code === "COMPETENCY_SHORTAGE")).toMatchObject({
      competencyCode: "VACT-TD-LOGIC",
      required: 12,
      available: 5,
      group: "SOURCE",
    });
  });

  it("năm cấu trúc khác năm ma trận chưa xác nhận chặn mọi đường; xác nhận rồi thì thôi", () => {
    const exam = load("ex-thptqg-tin-001");
    exam.structureYear = 2026;
    exam.yearsConfirmed = false;
    expect(yearsNeedConfirmation(exam)).toBe(true);
    const result = run(exam);
    expect(result.status).toBe("INVALID");
    for (const path of result.paths) {
      expect(path.issues.find((issue) => issue.code === "YEARS_UNCONFIRMED")).toMatchObject({
        required: 2026,
        available: 2025,
        group: "INFO",
      });
    }
    exam.yearsConfirmed = true;
    expect(yearsNeedConfirmation(exam)).toBe(false);
    expect(codes(run(exam).paths[0]!.issues)).not.toContain("YEARS_UNCONFIRMED");
  });
});
