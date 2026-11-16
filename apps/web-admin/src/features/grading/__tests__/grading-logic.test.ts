import { describe, expect, it } from "vitest";
import {
  essayInputTotal,
  essayProgress,
  findMissingScores,
  gradingSchema,
  toFormValues,
  toSaveRequest,
} from "../grading-form";
import { checkScoreInput, countWords, formatScoreInput, parseScore, sumScores } from "../scoring";
import type { GradingAttemptDetail, GradingEssay } from "../types";

function essay(overrides: Partial<GradingEssay> = {}): GradingEssay {
  return {
    questionId: "q-1",
    questionCode: "CH-1",
    partName: "Phan 1",
    number: 1,
    subjectCode: "LITERATURE",
    content: "De bai",
    passage: null,
    maxPoints: 3,
    rubric: [
      { id: "c1", description: "Bo cuc", points: 0.5 },
      { id: "c2", description: "Trien khai", points: 2.5 },
    ],
    sampleAnswer: null,
    wordLimit: null,
    answer: "Bai lam",
    scores: [
      { criterionId: "c1", score: 0.5 },
      { criterionId: "c2", score: null },
    ],
    comment: "",
    source: "ATTEMPT",
    ...overrides,
  };
}

function attempt(essays: GradingEssay[]): GradingAttemptDetail {
  return {
    id: "att-1",
    learner: { id: "hv-1", code: "HV-1", name: "Hoc vien" },
    exam: { id: "ex-1", code: "EX-1", name: "Bai thi", kindCode: "BCA", subjectCode: null, maxScore: 100 },
    submittedAt: "2026-11-10T09:00:00+07:00",
    status: "GRADING",
    objectiveScore: 50,
    objectiveMaxScore: 75,
    essayMaxScore: 3,
    essayScore: null,
    totalScore: null,
    essayCount: essays.length,
    gradedEssayCount: 0,
    grader: null,
    gradedAt: null,
    essays,
  };
}

describe("scoring", () => {
  it("doc diem bang dau phay hoac dau cham, toi da hai chu so thap phan", () => {
    expect(parseScore("1,5")).toBe(1.5);
    expect(parseScore(" 2.25 ")).toBe(2.25);
    expect(parseScore("12")).toBe(12);
    expect(parseScore("0,125")).toBeNaN();
    expect(parseScore("-1")).toBeNaN();
    expect(parseScore("abc")).toBeNaN();
    expect(parseScore("")).toBeNaN();
  });

  it("kiem tra o diem: trong, sai dang, vuot diem toi da", () => {
    expect(checkScoreInput("", 2.5, false)).toBeNull();
    expect(checkScoreInput("  ", 2.5, true)).toBe("required");
    expect(checkScoreInput("2,5", 2.5, true)).toBeNull();
    expect(checkScoreInput("0", 2.5, true)).toBeNull();
    expect(checkScoreInput("2,75", 2.5, false)).toBe("tooHigh");
    expect(checkScoreInput("1,2,3", 2.5, false)).toBe("invalid");
  });

  it("cong diem thap phan khong lech", () => {
    expect(sumScores([0.1, 0.2, null, undefined])).toBe(0.3);
    expect(sumScores([47.25, 11])).toBe(58.25);
  });

  it("dinh dang o nhap theo dau thap phan cua ngon ngu va dem so chu", () => {
    expect(formatScoreInput(1.5, ",")).toBe("1,5");
    expect(formatScoreInput(1.5, ".")).toBe("1.5");
    expect(formatScoreInput(null, ",")).toBe("");
    expect(countWords("  Bai   lam\nhai dong ")).toBe(4);
    expect(countWords("")).toBe(0);
  });
});

describe("grading form", () => {
  it("chuyen qua lai giua du lieu va bieu mau; o trong gui null", () => {
    const detail = attempt([essay()]);
    const values = toFormValues(detail, ",");
    expect(values.essays[0]).toEqual({ questionId: "q-1", scores: ["0,5", ""], comment: "" });

    values.essays[0]!.scores[1] = "1.75";
    values.essays[0]!.comment = "Tot";
    expect(toSaveRequest(detail, values)).toEqual({
      attemptId: "att-1",
      essays: [
        {
          questionId: "q-1",
          scores: [
            { criterionId: "c1", score: 0.5 },
            { criterionId: "c2", score: 1.75 },
          ],
          comment: "Tot",
        },
      ],
    });
  });

  it("schema bao loi vuot diem va sai dang, cho phep o trong khi luu nhap", () => {
    const essays = [essay()];
    const schema = gradingSchema(essays);
    expect(schema.safeParse({ essays: [{ questionId: "q-1", scores: ["", ""], comment: "" }] }).success).toBe(true);

    const result = schema.safeParse({ essays: [{ questionId: "q-1", scores: ["0,75", "abc"], comment: "" }] });
    expect(result.success).toBe(false);
    const issues = result.error?.issues.map((issue) => [issue.path.join("."), issue.message]);
    expect(issues).toEqual([
      ["essays.0.scores.0", "tooHigh"],
      ["essays.0.scores.1", "invalid"],
    ]);
  });

  it("tim o diem con trong theo thu tu cau roi tieu chi", () => {
    const essays = [essay(), essay({ questionId: "q-2" })];
    const values = {
      essays: [
        { questionId: "q-1", scores: ["0,5", "2"], comment: "" },
        { questionId: "q-2", scores: ["", "1"], comment: "" },
      ],
    };
    expect(findMissingScores(essays, values)).toEqual([{ essayIndex: 1, criterionIndex: 0 }]);
  });

  it("tinh diem cau dang nhap va tinh trang cua cau", () => {
    const item = essay();
    expect(essayInputTotal(["0,5", "1.25"])).toBe(1.75);
    expect(essayInputTotal(["0,5", "sai"])).toBe(0.5);
    expect(essayProgress(item, ["", ""])).toBe("empty");
    expect(essayProgress(item, ["0,5", ""])).toBe("partial");
    expect(essayProgress(item, ["0,5", "9"])).toBe("partial");
    expect(essayProgress(item, ["0,5", "2,5"])).toBe("complete");
  });
});
