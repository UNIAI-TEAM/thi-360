import { describe, expect, it } from "vitest";
import {
  checkExam,
  enumeratePaths,
  enumerateSubjectPicks,
  EXTENDED_CHECK_TIME_LIMIT_SECONDS,
  groupCounts,
  largestRemainder,
  resolveBlockRequirement,
  resolveMatrixRequirement,
  solveSeparate,
} from "../check";
import { questionStore } from "@/features/questions/mock/store";
import { examStore } from "../mock/store";
import { countForSubject } from "../mock/source-count";
import { mockAvailability, mockChoiceName } from "../mock/review-store";
import type { BlockAvailability, ExamAvailability } from "../review-types";
import type { ExamDetail, ExamPart, ExamSection, QuestionBlock } from "../types";

const GRADES = ["GRADE_10", "GRADE_11", "GRADE_12"];
const LEVELS = ["RECOGNITION", "COMPREHENSION", "APPLICATION", "HIGH_APPLICATION"];

function block(overrides: Partial<QuestionBlock> = {}): QuestionBlock {
  return {
    id: "b1",
    name: "Trắc nghiệm",
    questionType: "SINGLE_CHOICE",
    questionCount: 10,
    setCount: null,
    setSize: null,
    requiredPointsPerQuestion: 1,
    knowledgeScope: { nodeIds: [], match: "ANY" },
    tagCodes: [],
    taskTypeCode: null,
    childAnswerType: null,
    templateGroupCode: null,
    answerTypeLocked: false,
    matrix: {
      mode: "SEPARATE",
      unit: "COUNT",
      cells: [],
      byGrade: [
        { code: "GRADE_10", value: 2 },
        { code: "GRADE_11", value: 3 },
        { code: "GRADE_12", value: 5 },
      ],
      byDifficulty: [
        { code: "RECOGNITION", value: 3 },
        { code: "COMPREHENSION", value: 4 },
        { code: "APPLICATION", value: 2 },
        { code: "HIGH_APPLICATION", value: 1 },
      ],
    },
    ...overrides,
  };
}

function availability(blockId: string, count: (grade: string, level: string) => number, extra: Partial<BlockAvailability> = {}): BlockAvailability {
  return {
    blockId,
    cells: GRADES.flatMap((gradeCode) => LEVELS.map((difficultyCode) => ({ gradeCode, difficultyCode, count: count(gradeCode, difficultyCode) }))),
    setCount: null,
    overlap: null,
    ...extra,
  };
}

function exam(parts: ExamPart[], totalQuestions: number): ExamDetail {
  const seed = examStore.require("ex-hsa-l-toan-001");
  // Các ca kiểm tra dưới đây không xét thang điểm; ca có thang điểm đặt `maxScore` riêng.
  return { ...seed, parts, totalQuestions, maxScore: null };
}

function singlePart(blocks: QuestionBlock[], questionCount = 10): ExamPart {
  return {
    id: "p1",
    order: 1,
    name: "Phần 1",
    durationMinutes: 60,
    questionCount,
    questionCountOnPaper: questionCount,
    questionCountRequired: questionCount,
    participation: { mode: "ALL", choose: null },
    mode: "FIXED",
    sections: [
      {
        id: "s1",
        subjectCode: "MATH",
        questionCount,
        knowledgeScope: null,
        answerQuotas: null,
        matrix: null,
        groupOrder: "SHUFFLE",
        choiceGroup: null,
        blocks,
      },
    ],
    options: [],
  };
}

describe("largestRemainder", () => {
  it("lấy phần nguyên rồi chia phần thiếu cho phần thập phân lớn nhất", () => {
    expect(largestRemainder(17, [30, 30, 40])).toEqual([5, 5, 7]);
    expect(largestRemainder(10, [33, 33, 34])).toEqual([3, 3, 4]);
  });

  it("bằng nhau thì ưu tiên mục đứng trước", () => {
    expect(largestRemainder(10, [25, 25, 25, 25])).toEqual([3, 3, 2, 2]);
    expect(largestRemainder(3, [50, 50])).toEqual([2, 1]);
  });

  it("tổng luôn bằng số câu", () => {
    const counts = largestRemainder(50, [12.5, 17.5, 40, 30]);
    expect(counts.reduce((a, b) => a + b, 0)).toBe(50);
  });
});

describe("resolveBlockRequirement", () => {
  it("quy đổi tỷ lệ của ma trận kết hợp thành số câu nguyên", () => {
    const { requirement, issues } = resolveBlockRequirement(
      block({
        questionCount: 20,
        matrix: {
          mode: "COMBINED",
          unit: "PERCENT",
          cells: [
            { gradeCode: "GRADE_10", difficultyCode: "RECOGNITION", value: 33 },
            { gradeCode: "GRADE_11", difficultyCode: "COMPREHENSION", value: 33 },
            { gradeCode: "GRADE_12", difficultyCode: "APPLICATION", value: 34 },
          ],
          byGrade: [],
          byDifficulty: [],
        },
      }),
    );
    expect(issues).toEqual([]);
    expect(requirement?.cells.map((cell) => cell.required)).toEqual([7, 6, 7]);
  });

  it("báo tổng tỷ lệ khác 100% và tổng số câu lệch chỉ tiêu", () => {
    const percent = resolveBlockRequirement(
      block({ matrix: { ...block().matrix!, unit: "PERCENT", byGrade: [{ code: "GRADE_10", value: 90 }] } }),
    );
    expect(percent.requirement).toBeNull();
    expect(percent.issues[0]).toMatchObject({ code: "PERCENT_TOTAL_INVALID", dimension: "GRADE", available: 90 });

    const count = resolveBlockRequirement(block({ questionCount: 11 }));
    expect(count.issues.map((issue) => issue.code)).toEqual(["MATRIX_TOTAL_MISMATCH", "MATRIX_TOTAL_MISMATCH"]);
  });
});

describe("solveSeparate", () => {
  it("tìm phân bổ thỏa đồng thời hai chiều trong giới hạn nguồn", () => {
    const req = resolveBlockRequirement(block()).requirement!;
    const allocation = solveSeparate(req.byGrade, req.byDifficulty, (grade, level) => (grade === "GRADE_12" && level === "HIGH_APPLICATION" ? 0 : 3));
    expect(allocation).not.toBeNull();
    const sum = (filter: (key: string) => boolean) =>
      [...allocation!].filter(([key]) => filter(key)).reduce((total, [, value]) => total + value, 0);
    expect(sum((key) => key.startsWith("GRADE_12"))).toBe(5);
    expect(sum((key) => key.endsWith(":COMPREHENSION"))).toBe(4);
    expect(allocation!.get("GRADE_12:HIGH_APPLICATION")).toBe(0);
  });

  it("đủ nguồn từng chiều nhưng không ghép được thì trả null", () => {
    // Lớp 12 cần 5 câu nhưng chỉ có câu Nhận biết (2) và Vận dụng cao (1).
    const req = resolveBlockRequirement(block()).requirement!;
    const allocation = solveSeparate(req.byGrade, req.byDifficulty, (grade, level) =>
      grade === "GRADE_12" ? (level === "RECOGNITION" ? 2 : level === "HIGH_APPLICATION" ? 1 : 0) : 10,
    );
    expect(allocation).toBeNull();
  });
});

describe("enumeratePaths", () => {
  it("mỗi lựa chọn của phần tự chọn là một đường; bài không có phần tự chọn có một đường", () => {
    const hsa = examStore.require("ex-hsa-f-001");
    expect(enumeratePaths(hsa.parts, mockChoiceName).map((path) => path.choices.map((c) => c.code).join())).toEqual([
      "ENGLISH",
      "SCIENCE",
    ]);
    expect(enumeratePaths(examStore.require("ex-hsa-l-toan-001").parts)).toEqual([{ pathId: "default", choices: [] }]);
    expect(
      enumeratePaths(examStore.require("ex-qda-f-001").parts, mockChoiceName).map((path) =>
        path.choices.map((c) => c.code).join(),
      ),
    ).toEqual(["ENGLISH", "QD01", "QD04"]);
  });
});

describe("checkExam", () => {
  it("ma trận kết hợp: báo đúng ô thiếu nguồn", () => {
    const combined = block({
      questionCount: 4,
      matrix: {
        mode: "COMBINED",
        unit: "COUNT",
        cells: [
          { gradeCode: "GRADE_12", difficultyCode: "APPLICATION", value: 3 },
          { gradeCode: "GRADE_12", difficultyCode: "RECOGNITION", value: 1 },
        ],
        byGrade: [],
        byDifficulty: [],
      },
    });
    const source: ExamAvailability = {
      blocks: { b1: availability("b1", (grade, level) => (grade === "GRADE_12" && level === "APPLICATION" ? 2 : 9)) },
    };
    const outcome = checkExam(exam([singlePart([combined], 4)], 4), source);
    expect(outcome.status).toBe("INVALID");
    expect(outcome.paths[0]!.result).toBe("INVALID");
    expect(outcome.paths[0]!.issues).toEqual([
      expect.objectContaining({
        code: "CELL_SHORTAGE",
        gradeCode: "GRADE_12",
        difficultyCode: "APPLICATION",
        required: 3,
        available: 2,
        group: "SOURCE",
      }),
    ]);
    expect(outcome.groups.find((group) => group.group === "SOURCE")?.status).toBe("FAIL");
  });

  it("ma trận riêng: đủ nguồn từng dòng chưa chắc ghép được đề (AC-04.9)", () => {
    const source: ExamAvailability = {
      blocks: {
        b1: availability("b1", (grade, level) =>
          grade === "GRADE_12" ? (level === "RECOGNITION" ? 2 : level === "HIGH_APPLICATION" ? 1 : 0) : 10,
        ),
      },
    };
    const outcome = checkExam(exam([singlePart([block()])], 10), source);
    const codes = outcome.paths[0]!.issues.map((issue) => issue.code);
    expect(codes).toContain("DIMENSION_SHORTAGE");
  });

  it("thiếu nguồn ở một chiều mà nguồn chỉ nằm ở một ô thì nêu ô đó", () => {
    const source: ExamAvailability = {
      blocks: {
        b1: availability("b1", (grade, level) => (level === "APPLICATION" ? (grade === "GRADE_12" ? 1 : 0) : 10)),
      },
    };
    const outcome = checkExam(exam([singlePart([block()])], 10), source);
    expect(outcome.paths[0]!.issues).toEqual([
      expect.objectContaining({ code: "CELL_SHORTAGE", gradeCode: "GRADE_12", difficultyCode: "APPLICATION", required: 2, available: 1 }),
    ]);
  });

  it("báo tổng số câu của môn và của phần lệch chỉ tiêu", () => {
    const source: ExamAvailability = { blocks: { b1: availability("b1", () => 10) } };
    const part = singlePart([block()], 12);
    const outcome = checkExam(exam([part], 12), source);
    expect(outcome.paths[0]!.issues.map((issue) => issue.code)).toEqual(["SECTION_TOTAL_MISMATCH", "PATH_TOTAL_MISMATCH"]);
    expect(outcome.groups.find((group) => group.group === "STRUCTURE")?.status).toBe("FAIL");
  });

  it("câu chùm: lấy nguyên bộ, thiếu bộ thì không hợp lệ", () => {
    const set = block({ questionType: "QUESTION_SET", setCount: 2, setSize: 5 });
    const source: ExamAvailability = { blocks: { b1: availability("b1", () => 10, { setCount: 1 }) } };
    const outcome = checkExam(exam([singlePart([set])], 10), source);
    expect(outcome.paths[0]!.issues).toEqual([expect.objectContaining({ code: "SET_SHORTAGE", required: 2, available: 1 })]);
  });

  it("nguồn giao nhau chưa giải xong trong giới hạn: Chưa xác nhận được, không kết luận thiếu câu", () => {
    const source: ExamAvailability = {
      blocks: { b1: availability("b1", () => 10, { overlap: { nodeIds: ["x"], sharedCount: 4, solveSeconds: 90 } }) },
    };
    const standard = checkExam(exam([singlePart([block()])], 10), source);
    expect(standard.paths[0]!.result).toBe("UNCONFIRMED");
    expect(standard.paths[0]!.blocks[0]!.result).toBe("UNCONFIRMED");
    expect(standard.groups.find((group) => group.group === "OVERLAP")?.status).toBe("WARN");

    const extended = checkExam(exam([singlePart([block()])], 10), source, { timeLimitSeconds: EXTENDED_CHECK_TIME_LIMIT_SECONDS });
    expect(extended.paths[0]!.result).toBe("VALID");
    expect(extended.status).toBe("VALID");
  });

  it("HSA-F-001 theo dữ liệu mẫu: Khoa học là một đường, mỗi cách chọn môn có kết quả riêng", () => {
    const hsa = examStore.require("ex-hsa-f-001");
    const outcome = checkExam(hsa, mockAvailability(hsa.id, hsa.parts), { nameOf: mockChoiceName });
    expect(outcome.paths.map((path) => [path.choices[0]!.code, path.result])).toEqual([
      ["ENGLISH", "VALID"],
      ["SCIENCE", "INVALID"],
    ]);
    const science = outcome.paths[1]!;
    expect(science.selections).toHaveLength(12);
    const resultOf = (...subjects: string[]) => science.selections.find((item) => item.subjectCodes.join() === subjects.join())?.result;
    // Hóa học thiếu câu Lớp 12 (chỉ tiêu cấp môn): mọi cách chọn có Hóa học là INVALID; Địa lí có nguồn giao nhau chưa xác nhận được.
    expect(resultOf("PHYSICS", "BIOLOGY", "HISTORY")).toBe("VALID");
    expect(resultOf("PHYSICS", "BIOLOGY", "GEOGRAPHY")).toBe("UNCONFIRMED");
    expect(resultOf("PHYSICS", "CHEMISTRY", "BIOLOGY")).toBe("INVALID");
    expect(science.selections.filter((item) => item.subjectCodes.includes("CHEMISTRY")).every((item) => item.result === "INVALID")).toBe(true);
    expect(science.issues).toContainEqual(
      expect.objectContaining({ code: "SUBJECT_QUOTA_INFEASIBLE", subjectCode: "CHEMISTRY", gradeCode: "GRADE_12", required: 9, available: 4 }),
    );
    expect(science.issues.some((issue) => issue.code === "SUBJECT_CHOICE_COUNTS_INVALID")).toBe(false);
    expect(outcome.paths.every((path) => path.totalQuestions === 150 && path.totalMinutes === 195)).toBe(true);
    expect(outcome.totalPoints).toBe(150);
  });

  it("answer quota counts children of sets", () => {
    // Chùm Ngữ văn CH-000027 có 3 câu con Một đáp án: chỉ tiêu dạng trả lời đếm 3 câu con, không đếm chùm là một.
    const sets = questionStore.all().filter((item) => item.status === "READY" && item.type === "QUESTION_SET" && item.subjectCode === "LITERATURE");
    const children = sets.flatMap((set) => set.children);
    expect(children.length).toBeGreaterThan(sets.length);
    const counted = countForSubject({
      subjectCode: "LITERATURE",
      groups: [{ subjectCode: "LITERATURE", questionType: "QUESTION_SET", answerTypes: [], childAnswerType: null, knowledgeScope: null }],
    });
    expect(counted.total).toBe(children.length);
    const byType = new Map<string, number>();
    for (const child of children) byType.set(child.type, (byType.get(child.type) ?? 0) + 1);
    expect(Object.fromEntries(counted.byAnswerType.map((item) => [item.answerType, item.value]))).toEqual(Object.fromEntries(byType));

    // Chỉ tiêu dạng trả lời của môn thiếu nguồn thì báo ANSWER_TYPE_SHORTAGE kèm dạng và số câu.
    const section: ExamSection = {
      ...singlePart([block({ id: "b1", questionType: null, questionCount: 6, matrix: null })], 6).sections[0]!,
      questionCount: 6,
      answerQuotas: {
        unit: "COUNT",
        items: [
          { answerType: "SINGLE_CHOICE", value: 4 },
          { answerType: "SHORT_ANSWER", value: 2 },
        ],
      },
    };
    const part = { ...singlePart([], 6), sections: [section] };
    const source: ExamAvailability = {
      blocks: { b1: availability("b1", () => 1) },
      sections: {
        s1: {
          sectionId: "s1",
          total: 6,
          cells: availability("s1", () => 1).cells,
          byAnswerType: [
            { answerType: "SINGLE_CHOICE", count: 9 },
            { answerType: "SHORT_ANSWER", count: 1 },
          ],
        },
      },
    };
    const outcome = checkExam(exam([part], 6), source);
    expect(outcome.paths[0]!.issues).toEqual([
      expect.objectContaining({ code: "ANSWER_TYPE_SHORTAGE", answerType: "SHORT_ANSWER", required: 2, available: 1, subjectCode: "MATH" }),
    ]);
    expect(outcome.paths[0]!.subjects[0]!.answerQuotas).toEqual([
      { answerType: "SINGLE_CHOICE", required: 4, achieved: 4 },
      { answerType: "SHORT_ANSWER", required: 2, achieved: 1 },
    ]);
  });

  it("SUBJECT_CHOICE splits by choiceGroup: 17/17/16, 16/17/17, three NATURAL give 3 cases, 12 in total (B10)", () => {
    const hsa = examStore.require("ex-hsa-f-001");
    const science = hsa.parts[2]!.options.find((option) => option.kind === "SUBJECT_CHOICE")!;
    expect(science.sections.map((section) => [section.subjectCode, section.choiceGroup])).toEqual([
      ["PHYSICS", "NATURAL"],
      ["CHEMISTRY", "NATURAL"],
      ["BIOLOGY", "NATURAL"],
      ["HISTORY", "SOCIAL"],
      ["GEOGRAPHY", "SOCIAL"],
    ]);
    const picks = enumerateSubjectPicks(science);
    const countsOf = (...subjects: string[]) =>
      picks.filter((pick) => pick.subjectCodes.join() === subjects.join()).map((pick) => pick.counts);
    // Hai môn tự nhiên + một môn xã hội: môn tự nhiên 17, môn xã hội 16.
    expect(countsOf("PHYSICS", "CHEMISTRY", "HISTORY")).toEqual([[17, 17, 16]]);
    // Một môn tự nhiên + hai môn xã hội: môn tự nhiên 16, môn xã hội 17.
    expect(countsOf("BIOLOGY", "HISTORY", "GEOGRAPHY")).toEqual([[16, 17, 17]]);
    // Ba môn tự nhiên: ba trường hợp, mỗi môn một lần nhận 16.
    expect(countsOf("PHYSICS", "CHEMISTRY", "BIOLOGY")).toEqual([
      [16, 17, 17],
      [17, 16, 17],
      [17, 17, 16],
    ]);
    expect(picks).toHaveLength(12);
    expect(new Set(picks.map((pick) => pick.subjectCodes.join())).size).toBe(10);
    expect(picks.every((pick) => pick.counts.reduce((total, count) => total + count, 0) === 50)).toBe(true);

    const outcome = checkExam(hsa, mockAvailability(hsa.id, hsa.parts));
    const selections = outcome.paths.find((path) => path.choices[0]!.kind === "SUBJECT_CHOICE")!.selections;
    expect(selections.map((item) => [item.subjectCodes.join(), item.counts.join("/")])).toEqual(
      picks.map((pick) => [pick.subjectCodes.join(), pick.counts.join("/")]),
    );

    // Số câu của phương án đổi theo countsByPick; nhóm "lấy phần còn lại" nhận phần chênh.
    const custom = structuredClone(hsa);
    const customScience = custom.parts[2]!.options.find((option) => option.kind === "SUBJECT_CHOICE")!;
    customScience.countsByPick = [18, 18, 14];
    expect(enumerateSubjectPicks(customScience).find((pick) => pick.subjectCodes.join() === "PHYSICS,CHEMISTRY,HISTORY")?.counts).toEqual([18, 18, 14]);
    expect(groupCounts(customScience.sections[0]!, 18)).toEqual([10, 8]);
    expect(groupCounts(customScience.sections[0]!, 14)).toEqual([10, 4]);
  });

  it("SUBJECT_CHOICE: môn thiếu choiceGroup không chia được số câu, báo SUBJECT_CHOICE_COUNTS_INVALID", () => {
    const hsa = structuredClone(examStore.require("ex-hsa-f-002"));
    const science = hsa.parts[2]!.options.find((option) => option.kind === "SUBJECT_CHOICE")!;
    science.sections[3]!.choiceGroup = null;
    const result = checkExam(hsa, mockAvailability(hsa.id, hsa.parts));
    const issues = result.paths.flatMap((path) => path.issues).filter((issue) => issue.code === "SUBJECT_CHOICE_COUNTS_INVALID");
    expect(issues).toHaveLength(1);
    expect(result.paths.find((path) => path.choices[0]!.kind === "SUBJECT_CHOICE")!.selections).toEqual([]);
  });

  it("SUBJECT_CHOICE: countsByPick sai tổng, thiếu nhóm phần còn lại, chỉ tiêu theo số câu khi số câu môn đổi", () => {
    const codesOf = (target: ExamDetail) => {
      const result = checkExam(target, mockAvailability(target.id, target.parts));
      return new Set(result.paths.flatMap((path) => path.issues.map((issue) => issue.code)));
    };
    const hsa = examStore.require("ex-hsa-f-002");
    expect(codesOf(hsa).has("SUBJECT_CHOICE_COUNTS_INVALID")).toBe(false);

    const wrongSum = structuredClone(hsa);
    wrongSum.parts[2]!.options.find((option) => option.kind === "SUBJECT_CHOICE")!.countsByPick = [17, 17, 17];
    expect(codesOf(wrongSum).has("SUBJECT_CHOICE_COUNTS_INVALID")).toBe(true);

    const noRemainder = structuredClone(hsa);
    for (const section of noRemainder.parts[2]!.options.find((option) => option.kind === "SUBJECT_CHOICE")!.sections) {
      section.blocks[1]!.questionCount = 7;
    }
    expect(codesOf(noRemainder).has("REMAINDER_GROUP_INVALID")).toBe(true);

    const twoRemainders = structuredClone(hsa);
    twoRemainders.parts[2]!.options.find((option) => option.kind === "SUBJECT_CHOICE")!.sections[0]!.blocks[0]!.questionCount = null;
    expect(codesOf(twoRemainders).has("REMAINDER_GROUP_INVALID")).toBe(true);

    const byCount = structuredClone(hsa);
    for (const section of byCount.parts[2]!.options.find((option) => option.kind === "SUBJECT_CHOICE")!.sections) {
      section.matrix = { ...section.matrix!, unit: "COUNT" };
    }
    expect(codesOf(byCount).has("SUBJECT_QUOTA_COUNT_VARIES")).toBe(true);
  });

  it("chỉ tiêu cấp môn theo % quy ra số câu bằng phần dư lớn nhất của nhóm (17 và 16 câu)", () => {
    const hsa = examStore.require("ex-hsa-f-001");
    const section = hsa.parts[2]!.options.find((option) => option.kind === "SUBJECT_CHOICE")!.sections[0]!;
    const percents = section.matrix!.byGrade.map((item) => item.value);
    for (const count of [17, 16]) {
      const { requirement } = resolveMatrixRequirement(section.matrix!, count);
      expect(requirement!.byGrade.map((item) => item.required)).toEqual(largestRemainder(count, percents));
      expect(requirement!.byGrade.reduce((total, item) => total + item.required, 0)).toBe(count);
    }
    expect(largestRemainder(17, percents)).toEqual([3, 5, 9]);
    expect(largestRemainder(16, percents)).toEqual([3, 5, 8]);
  });

  it("Toán HSA: tổng chỉ tiêu dạng trả lời, lớp, độ khó khác số câu của môn bị báo", () => {
    const hsa = structuredClone(examStore.require("ex-hsa-f-002"));
    const math = hsa.parts[0]!.sections[0]!;
    expect(checkExam(hsa, mockAvailability(hsa.id, hsa.parts)).paths[0]!.issues.map((issue) => issue.code)).not.toContain(
      "ANSWER_QUOTA_TOTAL_MISMATCH",
    );
    math.answerQuotas!.items[0]!.value = 36;
    math.matrix!.byGrade[0]!.value = 4;
    const codes = checkExam(hsa, mockAvailability(hsa.id, hsa.parts)).paths[0]!.issues.map((issue) => issue.code);
    expect(codes).toContain("ANSWER_QUOTA_TOTAL_MISMATCH");
    expect(codes).toContain("SUBJECT_MATRIX_TOTAL_MISMATCH");
    math.answerQuotas = null;
    math.blocks[0]!.questionType = null;
    expect(checkExam(hsa, mockAvailability(hsa.id, hsa.parts)).paths[0]!.issues.map((issue) => issue.code)).toContain(
      "GROUP_ANSWER_TYPE_MISSING",
    );
  });

  it("các bài mẫu đang Hợp lệ thì kiểm tra lại vẫn Hợp lệ", () => {
    for (const item of examStore.all().filter((entry) => entry.check.status === "VALID")) {
      const outcome = checkExam(item, mockAvailability(item.id, item.parts));
      expect(outcome.status, item.id).toBe("VALID");
    }
  });
});
