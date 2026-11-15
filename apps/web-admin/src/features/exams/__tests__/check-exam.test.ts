import { describe, expect, it } from "vitest";
import {
  checkExam,
  enumeratePaths,
  EXTENDED_CHECK_TIME_LIMIT_SECONDS,
  largestRemainder,
  resolveBlockRequirement,
  solveSeparate,
} from "../check";
import { examStore } from "../mock/store";
import { mockAvailability, mockChoiceName } from "../mock/review-store";
import type { BlockAvailability, ExamAvailability } from "../review-types";
import type { ExamDetail, ExamPart, QuestionBlock } from "../types";

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
    pointsPerQuestion: 1,
    knowledgeScope: { nodeIds: [], match: "ANY", includeDescendants: false },
    tagCodes: [],
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
  return { ...seed, parts, totalQuestions };
}

function singlePart(blocks: QuestionBlock[], questionCount = 10): ExamPart {
  return {
    id: "p1",
    order: 1,
    name: "Phần 1",
    durationMinutes: 60,
    questionCount,
    mode: "FIXED",
    sections: [{ id: "s1", subjectCode: "MATH", questionCount, blocks }],
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
      block({ matrix: { ...block().matrix, unit: "PERCENT", byGrade: [{ code: "GRADE_10", value: 90 }] } }),
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
      "KH-1",
      "KH-2",
      "KH-3",
    ]);
    expect(enumeratePaths(examStore.require("ex-hsa-l-toan-001").parts)).toEqual([{ pathId: "default", choices: [] }]);
    expect(
      enumeratePaths(examStore.require("ex-qda-f-001").parts, mockChoiceName).map((path) =>
        path.choices.map((c) => c.code).join(),
      ),
    ).toEqual(["ENGLISH", "KH-4", "KH-6"]);
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

  it("HSA-F-001 theo dữ liệu mẫu: KH-2 thiếu câu Hóa học Lớp 12/Vận dụng, KH-3 chưa xác nhận được", () => {
    const hsa = examStore.require("ex-hsa-f-001");
    const outcome = checkExam(hsa, mockAvailability(hsa.id, hsa.parts), { nameOf: mockChoiceName });
    expect(outcome.paths.map((path) => [path.choices[0]!.code, path.result])).toEqual([
      ["ENGLISH", "VALID"],
      ["KH-1", "VALID"],
      ["KH-2", "INVALID"],
      ["KH-3", "UNCONFIRMED"],
    ]);
    const kh2 = outcome.paths[2]!;
    expect(kh2.issues).toEqual([
      expect.objectContaining({
        code: "CELL_SHORTAGE",
        subjectCode: "CHEMISTRY",
        gradeCode: "GRADE_12",
        difficultyCode: "APPLICATION",
        required: 3,
        available: 2,
      }),
    ]);
    expect(outcome.issueCount).toBe(2);
    expect(outcome.paths.every((path) => path.totalQuestions === 150 && path.totalMinutes === 195)).toBe(true);
    expect(outcome.totalPoints).toBe(150);
  });

  it("các bài mẫu đang Hợp lệ thì kiểm tra lại vẫn Hợp lệ", () => {
    for (const item of examStore.all().filter((entry) => entry.check.status === "VALID")) {
      const outcome = checkExam(item, mockAvailability(item.id, item.parts));
      expect(outcome.status, item.id).toBe("VALID");
    }
  });
});
