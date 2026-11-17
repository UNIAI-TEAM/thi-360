import { describe, expect, it } from "vitest";
import {
  apportionByPercent,
  computeAllocation,
  convertMatrixMode,
  convertMatrixUnit,
  countsToPercents,
  gridToCells,
  groupCounts,
  splitEvenly,
  structureTotals,
  validateBlock,
  validateStructure,
  type MatrixAxes,
} from "../matrix";
import { effectiveScope } from "../subject-matrix";
import type { BlockMatrix, ExamPart, ExamSection, QuestionBlock } from "../types";
import { EXAMS_FIXTURE } from "./exam-fixture";

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
    knowledgeScope: { nodeIds: [] },
    tagCodes: [],
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

describe("tổng điểm của cấu trúc (G1)", () => {
  it("nhóm lấy phần còn lại nhận phần chênh của môn và tính điểm, không tính 0", () => {
    const part = structuredClone(EXAMS_FIXTURE.exams.find((item) => item.code === "HSA-F-001")!.parts[0]!);
    part.mode = "FIXED";
    part.options = [];
    const section = part.sections[0]!;
    section.questionCount = 10;
    section.blocks = [
      block({}, { id: "fixed", questionCount: 4, requiredPointsPerQuestion: 2 }),
      block({}, { id: "rest", questionCount: null, requiredPointsPerQuestion: 1 }),
    ];
    part.sections = [section];
    expect(structureTotals([part]).totalPoints).toBe(4 * 2 + 6 * 1);
  });

  it("phương án SUBJECT_CHOICE của HSA tính điểm theo countsByPick, không tính 0 (đề dev seed)", () => {
    // HSA-F-DEV-001 (seed): Khoa học 5 môn qc null, mỗi môn một nhóm lấy phần còn lại; tiêu đề từng báo 100 / 150.
    const exam = structuredClone(EXAMS_FIXTURE.exams.find((item) => item.code === "HSA-F-001")!);
    const part = exam.parts.find((item) => item.mode === "CHOICE")!;
    part.options = part.options.filter((item) => item.kind === "SUBJECT_CHOICE");
    for (const section of part.options[0]!.sections) {
      section.blocks = [block({}, { questionCount: null })];
    }
    const totals = structureTotals(exam.parts);
    expect(totals).toMatchObject({ totalQuestions: 150, totalPoints: 150, allocated: 150, missing: 0 });
  });

  it("phương án SUBJECT_CHOICE có nhóm cố định và nhóm phần còn lại (bài tạo từ mẫu) vẫn đủ 150 điểm", () => {
    // Mẫu HSA nạp sẵn: mỗi môn Khoa học một nhóm 10 câu + một nhóm lấy phần còn lại (17/17/16 − 10).
    const exam = structuredClone(EXAMS_FIXTURE.exams.find((item) => item.code === "HSA-F-001")!);
    const part = exam.parts.find((item) => item.mode === "CHOICE")!;
    part.options = part.options.filter((item) => item.kind === "SUBJECT_CHOICE");
    expect(structureTotals(exam.parts).totalPoints).toBe(150);
  });
});

describe("HSA 17 − 1 (H3)", () => {
  /** Phần Khoa học của đề HSA mẫu, chỉ giữ phương án `SUBJECT_CHOICE` (nhóm môn đầu của phần). */
  const hsaChoicePart = () => {
    const exam = structuredClone(EXAMS_FIXTURE.exams.find((item) => item.code === "HSA-F-001")!);
    const part = exam.parts.find((item) => item.mode === "CHOICE")!;
    part.options = part.options.filter((item) => item.kind === "SUBJECT_CHOICE");
    return { exam, part, science: part.options[0]! };
  };

  it("môn khai 17 câu: môn nhận cách chia 16 tính 16 câu trên đề, tổng điểm vẫn 150", () => {
    const { exam, science } = hsaChoicePart();
    for (const section of science.sections) {
      section.questionCount = 17;
      section.blocks = [
        block({}, { id: `${section.id}-fixed`, questionCount: 10 }),
        block({}, { id: `${section.id}-tail`, questionCount: 7 }),
      ];
    }
    // Cách chia đầu cho môn đầu 16 câu (17 − 1): điểm của môn đó 10 + 6; môn không được chọn tính 0.
    expect(structureTotals(exam.parts)).toMatchObject({ totalQuestions: 150, totalPoints: 150, allocated: 150 });
    // Tổng nhóm vẫn đối chiếu số câu khai báo 17 (mục 3.5), không báo thiếu.
    const structural = validateStructure(exam.parts, AXES).filter(
      (issue) => issue.code === "BLOCKS_TOTAL" || issue.code === "SECTIONS_TOTAL",
    );
    expect(structural).toEqual([]);
  });

  it("bỏ câu chỉ ở nhóm câu đơn: chùm giữ nguyên khi môn nhận 16", () => {
    const { exam, science } = hsaChoicePart();
    for (const section of science.sections) {
      section.questionCount = 17;
      section.blocks = [
        block({}, { id: `${section.id}-singles`, questionCount: 4 }),
        block({}, { id: `${section.id}-set`, questionType: "QUESTION_SET", questionCount: 13, setCount: 13, setSize: 1 }),
      ];
    }
    // Môn đầu nhận 16: chùm 13 câu không bị bỏ, nhóm câu đơn mất một câu (4 → 3).
    expect(groupCounts(science.sections[0]!, 16)).toEqual([3, 13]);
    expect(structureTotals(exam.parts).totalPoints).toBe(150);
  });

  it("nhóm lấy phần còn lại của môn khai 17 nhận phần chênh khi môn nhận 16", () => {
    const { science } = hsaChoicePart();
    const section = {
      ...science.sections[0]!,
      blocks: [block({}, { questionCount: 10 }), block({}, { questionCount: null })],
    };
    expect(groupCounts(section, 16)).toEqual([10, 6]);
  });

  /** Số câu mỗi môn khai và nhóm câu đơn của môn (một khối câu đơn, hoặc một khối chùm). */
  const declare = (science: ExamPart["options"][number], count: number, kind: "singles" | "set" = "singles") => {
    for (const section of science.sections) {
      section.questionCount = count;
      section.blocks =
        kind === "singles"
          ? [block({}, { id: `${section.id}-singles`, questionCount: count })]
          : [block({}, { id: `${section.id}-set`, questionType: "QUESTION_SET", questionCount: count, setCount: count, setSize: 1 })];
    }
  };
  const subjectIssues = (parts: Parameters<typeof validateStructure>[0]) =>
    validateStructure(parts, AXES).filter(
      (issue) => issue.code === "SUBJECT_COUNT_EXCEEDS_ALLOCATION" || issue.code === "SUBJECT_TRIM_IMPOSSIBLE",
    );

  it("cách chia 17, 17, 17: không bỏ câu nào, môn nào cũng 17 câu và không có lỗi", () => {
    const { exam, science } = hsaChoicePart();
    declare(science, 17);
    science.countsByPick = [17, 17, 17];
    const full = structureTotals(exam.parts).totalPoints;
    science.countsByPick = [17, 17, 16];
    // Chỉ môn nhận 16 mất một câu so với cách chia 17, 17, 17.
    expect(full - structureTotals(exam.parts).totalPoints).toBe(1);
    science.countsByPick = [17, 17, 17];
    expect(subjectIssues(exam.parts)).toEqual([]);
  });

  it("môn khai ít hơn cách chia (15 < 16): lấy số khai, không báo lỗi", () => {
    const { exam, science } = hsaChoicePart();
    declare(science, 17);
    science.countsByPick = [17, 17, 16];
    const base = structureTotals(exam.parts).totalPoints;
    declare(science, 15);
    // Môn nhận 16 → 15 (−1), hai môn nhận 17 → 15 (−2 mỗi môn).
    const picked = science.chooseSubjects ?? science.countsByPick.length;
    expect(base - structureTotals(exam.parts).totalPoints).toBe(1 + 2 * (picked - 1));
    expect(subjectIssues(exam.parts)).toEqual([]);
  });

  it("không có cách chia hợp lệ: lùi về số câu khai báo (mọi môn 17), tiêu đề lộ phần thiếu", () => {
    const { exam, part, science } = hsaChoicePart();
    declare(science, 17);
    science.countsByPick = [17, 17, 17];
    const base = structureTotals(exam.parts).totalPoints;
    // Số cách chia không khớp số môn được chọn: bảng rỗng, mỗi môn (kể cả môn không được chọn) tính 17 câu.
    science.countsByPick = [];
    const picked = science.chooseSubjects ?? 3;
    expect(structureTotals(exam.parts).totalPoints - base).toBe(17 * (science.sections.length - picked));
    expect(validateStructure(exam.parts, AXES)).toContainEqual(
      expect.objectContaining({ code: "SECTIONS_TOTAL", partId: part.id, optionId: science.id, actual: 0 }),
    );
  });

  it("khai nhiều hơn cách chia quá một câu (18 so với 16): báo lỗi thay vì cắt im lặng", () => {
    const { exam, science } = hsaChoicePart();
    declare(science, 18);
    science.countsByPick = [17, 17, 16];
    // Chỉ môn nhận 16 lệch hai câu; môn nhận 17 lệch một câu nên bỏ được.
    const issues = subjectIssues(exam.parts);
    expect(issues).toHaveLength(1);
    expect(issues[0]).toMatchObject({ code: "SUBJECT_COUNT_EXCEEDS_ALLOCATION", declared: 18, allocated: 16 });
  });

  it("môn toàn chùm cần bỏ một câu: báo SUBJECT_TRIM_IMPOSSIBLE, chùm không bị tách", () => {
    const { exam, science } = hsaChoicePart();
    declare(science, 17, "set");
    science.countsByPick = [17, 17, 16];
    const issues = subjectIssues(exam.parts);
    expect(issues).toHaveLength(1);
    expect(issues[0]).toMatchObject({ code: "SUBJECT_TRIM_IMPOSSIBLE", declared: 17, allocated: 16 });
    expect(groupCounts(science.sections[0]!, 16)).toEqual([17]);
  });
});

describe("cấu trúc của bài mẫu", () => {
  const exams = EXAMS_FIXTURE.exams;

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
    choiceGroup: null,
    blocks: [],
  });
  const singles = (knowledgeScope: QuestionBlock["knowledgeScope"]): QuestionBlock => ({
    ...block({}, { questionType: "SINGLE_CHOICE", questionCount: 5, matrix: null }),
    knowledgeScope,
  });
  it("group inherits subject scope; own scope not intersected", () => {
    const math = section({ nodeIds: ["kn-1D4"] });
    // Nhóm không có phạm vi riêng (null) dùng phạm vi của môn.
    expect(effectiveScope(singles(null), math)).toEqual(math.knowledgeScope);
    // Nhóm có phạm vi riêng chỉ dùng phạm vi riêng, không giao với phạm vi của môn (kn-0D1 nằm ngoài kn-1D4).
    const own = singles({ nodeIds: ["kn-0D1"] });
    expect(effectiveScope(own, math)).toEqual(own.knowledgeScope);
    // Cả hai cùng trống là không giới hạn MAPID.
    const open = section(null);
    expect(effectiveScope(singles(null), open)).toBeNull();
    // Phạm vi riêng chưa chọn nút (hủy hộp chọn, chỉ có năng lực) kế thừa nút của môn và giữ lọc năng lực (R1-3, R1-4).
    expect(effectiveScope(singles({ nodeIds: [] }), math)).toEqual(math.knowledgeScope);
    expect(effectiveScope(singles({ nodeIds: [], competencyCodes: ["VACT-TOAN"] }), math)).toEqual({
      nodeIds: ["kn-1D4"],
      competencyCodes: ["VACT-TOAN"],
    });
    expect(effectiveScope(singles({ nodeIds: [] }), open)).toEqual({ nodeIds: [] });
  });
});
