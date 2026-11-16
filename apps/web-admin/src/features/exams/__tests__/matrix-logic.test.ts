import { describe, expect, it } from "vitest";
import {
  apportionByPercent,
  computeAllocation,
  convertMatrixMode,
  convertMatrixUnit,
  countsToPercents,
  gridToCells,
  splitEvenly,
  structureTotals,
  validateBlock,
  validateStructure,
  type MatrixAxes,
} from "../matrix";
import { questionStore } from "@/features/questions/mock/store";
import fixture from "../mock/exams.json";
import { candidatesFor, matchesScope, queryOfBlock } from "../mock/source-count";
import { effectiveScope } from "../subject-matrix";
import type { BlockMatrix, ExamDetail, ExamSection, QuestionBlock } from "../types";

const AXES: MatrixAxes = {
  grades: ["GRADE_10", "GRADE_11", "GRADE_12"],
  difficulties: ["RECOGNITION", "COMPREHENSION", "APPLICATION", "HIGH_APPLICATION"],
};

/** Số liệu của bản vẽ BaiThiCauTruc: khối 20 câu, tỷ lệ theo ô và số câu sau quy đổi. */
const ARTBOARD_PERCENTS = [
  [12, 5, 5, 0],
  [10, 10, 5, 5],
  [8, 20, 15, 5],
];
const ARTBOARD_COUNTS = [
  [2, 1, 1, 0],
  [2, 2, 1, 1],
  [2, 4, 3, 1],
];

function block(matrix: Partial<BlockMatrix>, overrides: Partial<QuestionBlock> = {}): QuestionBlock {
  return {
    id: "blk-test",
    name: "Khối thử",
    questionType: "SHORT_ANSWER",
    questionCount: 20,
    setCount: null,
    setSize: null,
    requiredPointsPerQuestion: 1,
    knowledgeScope: { nodeIds: [], match: "ANY" },
    tagCodes: [],
    taskTypeCode: null,
    childAnswerType: null,
    templateGroupCode: null,
    answerTypeLocked: false,
    matrix: { mode: "COMBINED", unit: "COUNT", cells: [], byGrade: [], byDifficulty: [], ...matrix },
    ...overrides,
  };
}

describe("quy đổi tỷ lệ theo phần dư lớn nhất", () => {
  it("khối 20 câu của bản vẽ: phần nguyên 19, phần còn thiếu vào Lớp 12 · Nhận biết (0,6)", () => {
    const result = apportionByPercent(ARTBOARD_PERCENTS.flat(), 20);
    expect(result.theoretical).toEqual([2.4, 1, 1, 0, 2, 2, 1, 1, 1.6, 4, 3, 1]);
    expect(result.floorTotal).toBe(19);
    expect(result.missing).toBe(1);
    expect(result.bonus.flatMap((bonus, index) => (bonus ? [index] : []))).toEqual([8]);
    expect(result.results).toEqual(ARTBOARD_COUNTS.flat());
  });

  it("bằng phần thập phân thì ưu tiên theo thứ tự hiển thị", () => {
    expect(apportionByPercent([25, 25, 25, 25], 2).results).toEqual([1, 1, 0, 0]);
    expect(splitEvenly(50, 3)).toEqual([17, 17, 16]);
    expect(splitEvenly(50, 2)).toEqual([25, 25]);
  });

  it("tổng tỷ lệ khác 100% thì không bù đủ số câu của khối", () => {
    expect(apportionByPercent([50, 0], 20).results).toEqual([10, 0]);
  });

  it("đổi số câu sang tỷ lệ nguyên tổng 100", () => {
    expect(countsToPercents(ARTBOARD_COUNTS.flat(), 20)).toEqual([10, 5, 5, 0, 10, 10, 5, 5, 10, 20, 15, 5]);
    expect(countsToPercents([1, 1, 1], 3)).toEqual([34, 33, 33]);
  });
});

describe("phân bổ của một khối", () => {
  const percentGrid = gridToCells(ARTBOARD_PERCENTS, AXES);

  it("kết hợp theo % quy ra đúng lưới số câu, tổng dòng và cột", () => {
    const allocation = computeAllocation(block({ unit: "PERCENT", cells: percentGrid }), AXES);
    expect(allocation.grid).toEqual(ARTBOARD_COUNTS);
    expect(allocation.rowTotals).toEqual([4, 6, 10]);
    expect(allocation.columnTotals).toEqual([6, 7, 5, 2]);
    expect(allocation.inputTotal).toBe(100);
    expect(allocation.total).toBe(20);
  });

  it("khối khớp ma trận thì không có lỗi; lệch tổng thì báo đúng số", () => {
    expect(validateBlock(block({ cells: gridToCells(ARTBOARD_COUNTS, AXES) }), AXES)).toEqual([]);
    expect(validateBlock(block({ unit: "PERCENT", cells: percentGrid }), AXES)).toEqual([]);
    const short = gridToCells([[2, 1, 1, 0], [2, 2, 1, 1], [2, 4, 3, 0]], AXES);
    expect(validateBlock(block({ cells: short }), AXES)).toEqual([
      { code: "ALLOCATION_TOTAL", dimension: "COMBINED", expected: 20, actual: 19 },
    ]);
    const percent90 = gridToCells([[12, 5, 5, 0], [10, 10, 5, 5], [8, 20, 5, 5]], AXES);
    expect(validateBlock(block({ unit: "PERCENT", cells: percent90 }), AXES)).toEqual([
      { code: "PERCENT_TOTAL", dimension: "COMBINED", actual: 90 },
    ]);
  });

  it("riêng từng chiều: mỗi chiều phải đủ số câu", () => {
    const separate = block({
      mode: "SEPARATE",
      byGrade: [
        { code: "GRADE_10", value: 4 },
        { code: "GRADE_11", value: 6 },
        { code: "GRADE_12", value: 10 },
      ],
      byDifficulty: [
        { code: "RECOGNITION", value: 6 },
        { code: "COMPREHENSION", value: 7 },
        { code: "APPLICATION", value: 5 },
      ],
    });
    expect(validateBlock(separate, AXES)).toEqual([
      { code: "ALLOCATION_TOTAL", dimension: "DIFFICULTY", expected: 20, actual: 18 },
    ]);
  });

  it("câu chùm: số bộ × số câu mỗi bộ phải bằng số câu; số âm hoặc lẻ bị chặn", () => {
    const set = block({ cells: gridToCells(ARTBOARD_COUNTS, AXES) }, { questionType: "QUESTION_SET", setCount: 3, setSize: 5 });
    expect(validateBlock(set, AXES)).toEqual([{ code: "SET_SIZE_MISMATCH", expected: 15, actual: 20 }]);
    const negative = block({ cells: gridToCells([[-1, 0, 0, 0]], AXES) });
    expect(validateBlock(negative, AXES)).toEqual([{ code: "INVALID_VALUE" }]);
    expect(validateBlock(block({}, { questionCount: 0 }), AXES)[0]).toEqual({ code: "COUNT_NOT_POSITIVE" });
  });

  it("đổi cách khai báo và đơn vị giữ tổng số câu", () => {
    const combined: BlockMatrix = { mode: "COMBINED", unit: "COUNT", cells: gridToCells(ARTBOARD_COUNTS, AXES), byGrade: [], byDifficulty: [] };
    const separate = convertMatrixMode(combined, "SEPARATE", AXES);
    expect(separate.byGrade.map((item) => item.value)).toEqual([4, 6, 10]);
    expect(separate.byDifficulty.map((item) => item.value)).toEqual([6, 7, 5, 2]);
    const back = convertMatrixMode(separate, "COMBINED", AXES);
    expect(computeAllocation(block(back), AXES).total).toBe(20);
    expect(computeAllocation(block(back), AXES).rowTotals).toEqual([4, 6, 10]);
    expect(computeAllocation(block(back), AXES).columnTotals).toEqual([6, 7, 5, 2]);

    const percent = convertMatrixUnit(combined, "PERCENT", 20, AXES);
    expect(percent.cells.map((cell) => cell.value)).toEqual([10, 5, 5, 0, 10, 10, 5, 5, 10, 20, 15, 5]);
    const counts = convertMatrixUnit({ ...combined, unit: "PERCENT", cells: percentGrid }, "COUNT", 20, AXES);
    expect(counts.cells.map((cell) => cell.value)).toEqual(ARTBOARD_COUNTS.flat());
  });
});

describe("cấu trúc của bài mẫu", () => {
  const exams = (fixture as { exams: ExamDetail[] }).exams;

  it("HSA-F-001: 150 câu, 195 phút, phân bổ đủ", () => {
    const exam = exams.find((item) => item.code === "HSA-F-001")!;
    const totals = structureTotals(exam.parts);
    expect(totals).toMatchObject({ partCount: 3, totalQuestions: 150, totalMinutes: 195, allocated: 150, missing: 0 });
    expect(validateStructure(exam.parts, AXES).filter((issue) => issue.code !== "BLOCK")).toEqual([]);
  });

  it("tổ hợp có tổng môn lệch số câu của phần bị báo SECTIONS_TOTAL; SUBJECT_CHOICE theo countsByPick", () => {
    const exam = structuredClone(exams.find((item) => item.code === "BCA-F-001" || item.code === "QDA-F-001")!);
    const choice = exam.parts.find((part) => part.mode === "CHOICE")!;
    const combination = choice.options.find((option) => option.kind === "COMBINATION")!;
    combination.sections[0]!.questionCount = (combination.sections[0]!.questionCount ?? 0) + 1;
    expect(validateStructure(exam.parts, AXES)).toContainEqual(
      expect.objectContaining({ code: "SECTIONS_TOTAL", partId: choice.id, optionId: combination.id }),
    );
    const hsa = structuredClone(exams.find((item) => item.code === "HSA-F-001")!);
    const hsaChoice = hsa.parts.find((part) => part.mode === "CHOICE")!;
    const science = hsaChoice.options.find((option) => option.kind === "SUBJECT_CHOICE")!;
    science.countsByPick = [17, 17, 17];
    expect(validateStructure(hsa.parts, AXES)).toContainEqual({
      code: "SECTIONS_TOTAL",
      partId: hsaChoice.id,
      optionId: science.id,
      expected: 50,
      actual: 51,
    });
  });
});

describe("phạm vi MAPID của nhóm và môn (D21, D23)", () => {
  const section = (knowledgeScope: ExamSection["knowledgeScope"]): ExamSection => ({
    id: "s-math",
    subjectCode: "MATH",
    questionCount: 50,
    knowledgeScope,
    answerQuotas: null,
    matrix: null,
    groupOrder: "SHUFFLE",
    choiceGroup: null,
    blocks: [],
  });
  const singles = (knowledgeScope: QuestionBlock["knowledgeScope"]): QuestionBlock => ({
    ...block({}, { questionType: "SINGLE_CHOICE", questionCount: 5, matrix: null }),
    knowledgeScope,
  });
  const codesOf = (target: QuestionBlock, within: ExamSection) =>
    candidatesFor(queryOfBlock(target, within))
      .map((question) => question.code)
      .sort();

  it("group inherits subject scope; own scope not intersected; parent matches child", () => {
    // CH-000001 gắn kn-1D4-1-1 (con của kn-1D4-1, con của kn-1D4); CH-000010 gắn kn-0D1-2-1 và kn-0D1-1-1.
    const math = section({ nodeIds: ["kn-1D4"], match: "ANY" });
    // Nhóm không có phạm vi riêng (null) dùng phạm vi của môn; nút cha khớp câu gắn nút con.
    expect(effectiveScope(singles(null), math)).toEqual(math.knowledgeScope);
    expect(codesOf(singles(null), math)).toContain("CH-000001");
    expect(codesOf(singles(null), math)).not.toContain("CH-000010");
    // Nhóm có phạm vi riêng chỉ dùng phạm vi riêng, không giao với phạm vi của môn (kn-0D1 nằm ngoài kn-1D4).
    const own = singles({ nodeIds: ["kn-0D1"], match: "ANY" });
    expect(effectiveScope(own, math)).toEqual(own.knowledgeScope);
    expect(codesOf(own, math)).toContain("CH-000010");
    expect(codesOf(own, math)).not.toContain("CH-000001");
    // Cả hai cùng trống là không giới hạn MAPID: câu chưa gắn nút nào cũng khớp.
    const open = section(null);
    expect(effectiveScope(singles(null), open)).toBeNull();
    expect(codesOf(singles(null), open)).toEqual(expect.arrayContaining(["CH-000001", "CH-000010", "CH-008008"]));
    // Phạm vi riêng chưa chọn nút (hủy hộp chọn, chỉ có năng lực) kế thừa nút của môn và giữ lọc năng lực (R1-3, R1-4).
    expect(effectiveScope(singles({ nodeIds: [], match: "ANY" }), math)).toEqual(math.knowledgeScope);
    expect(effectiveScope(singles({ nodeIds: [], match: "ANY", competencyCodes: ["VACT-TOAN"] }), math)).toEqual({
      nodeIds: ["kn-1D4"],
      match: "ANY",
      competencyCodes: ["VACT-TOAN"],
    });
    expect(effectiveScope(singles({ nodeIds: [], match: "ANY" }), open)).toEqual({ nodeIds: [], match: "ANY" });
    // Nút cha đã chọn khớp nút con; nút con đã chọn không khớp câu chỉ gắn nút cha của nó.
    const byCode = (code: string) => questionStore.all().find((question) => question.code === code)!;
    expect(matchesScope(byCode("CH-000001"), { nodeIds: ["kn-1D4"], match: "ANY" })).toBe(true);
    expect(matchesScope(byCode("CH-000001"), { nodeIds: ["kn-1D4-1"], match: "ANY" })).toBe(true);
    expect(matchesScope(byCode("CH-000001"), { nodeIds: ["kn-1D4-3"], match: "ANY" })).toBe(false);
    expect(matchesScope(byCode("CH-000010"), { nodeIds: ["kn-0D1-1-1", "kn-0D1-2-1"], match: "ALL" })).toBe(true);
    expect(matchesScope(byCode("CH-000010"), { nodeIds: ["kn-0D1-1-1", "kn-1D4"], match: "ALL" })).toBe(false);
  });
});
