import { allocateSubjectCounts } from "./subject-choice";
import type {
  BlockMatrix,
  ExamPart,
  ExamSection,
  ExamTiming,
  MatrixDimensionValue,
  MatrixMode,
  MatrixUnit,
  QuestionBlock,
} from "./types";

/**
 * Phép tính thuần của cấu trúc và ma trận bài thi (UniNote PBI-04 mục 6, 7): quy đổi tỷ lệ theo phần dư lớn nhất,
 * tổng dòng/cột, kiểm tra một khối và cả cấu trúc. Không phụ thuộc giao diện để test riêng và dùng lại khi nối backend
 * (backend vẫn là nơi kiểm tra cuối cùng).
 */

/** Trục của ma trận: mã khối lớp (dòng) và mã độ khó (cột) theo thứ tự hiển thị của danh mục. */
export interface MatrixAxes {
  grades: readonly string[];
  difficulties: readonly string[];
}

export interface Apportionment {
  /** Số câu lý thuyết theo tỷ lệ (`tỷ lệ × số câu / 100`). */
  theoretical: number[];
  /** Số câu nguyên cuối cùng. */
  results: number[];
  /** Ô được cộng một câu từ phần còn thiếu. */
  bonus: boolean[];
  /** Tổng phần nguyên trước khi phân phần còn thiếu. */
  floorTotal: number;
  /** Số câu còn thiếu sau khi lấy phần nguyên. */
  missing: number;
}

const EPSILON = 1e-9;

function roundPrecision(value: number): number {
  return Math.round(value * 1e9) / 1e9;
}

export function sum(values: readonly number[]): number {
  return values.reduce((total, value) => total + value, 0);
}

/**
 * Quy đổi tỷ lệ % thành số câu nguyên theo phần dư lớn nhất (UniNote PBI-04 mục 7.3): tính số câu lý thuyết, lấy phần
 * nguyên, phân phần còn thiếu cho mục có phần thập phân lớn nhất; bằng nhau thì theo thứ tự hiển thị.
 */
export function apportionByPercent(percents: readonly number[], total: number): Apportionment {
  const theoretical = percents.map((percent) => roundPrecision((percent * total) / 100));
  const floors = theoretical.map((value) => Math.floor(value + EPSILON));
  const floorTotal = sum(floors);
  const target = Math.round(sum(theoretical));
  const missing = Math.max(0, target - floorTotal);
  const ranked = theoretical
    .map((value, index) => ({ index, fraction: roundPrecision(value - floors[index]!) }))
    .sort((a, b) => b.fraction - a.fraction || a.index - b.index);
  const bonus = percents.map(() => false);
  for (let k = 0; k < Math.min(missing, ranked.length); k += 1) bonus[ranked[k]!.index] = true;
  return {
    theoretical,
    results: floors.map((value, index) => value + (bonus[index] ? 1 : 0)),
    bonus,
    floorTotal,
    missing,
  };
}

/** Chia đều `total` cho `parts` mục theo cùng quy tắc (50 câu, 3 môn: 17–17–16). */
export function splitEvenly(total: number, parts: number): number[] {
  if (parts <= 0) return [];
  return apportionByPercent(Array.from({ length: parts }, () => 100 / parts), total).results;
}

/** Đổi số câu sang tỷ lệ % nguyên có tổng 100 (cùng quy tắc phần dư lớn nhất). */
export function countsToPercents(counts: readonly number[], blockCount: number): number[] {
  if (blockCount <= 0) return counts.map(() => 0);
  return apportionByPercent(
    counts.map((count) => (count * 100) / blockCount),
    100,
  ).results;
}

// ---------------------------------------------------------------- lưới ma trận

/** Lưới giá trị đã nhập của chế độ kết hợp: dòng là khối lớp, cột là độ khó. Ô không có trong `cells` là 0. */
export function combinedGrid(matrix: BlockMatrix, axes: MatrixAxes): number[][] {
  return axes.grades.map((grade) =>
    axes.difficulties.map(
      (difficulty) =>
        matrix.cells.find((cell) => cell.gradeCode === grade && cell.difficultyCode === difficulty)?.value ?? 0,
    ),
  );
}

export function gridToCells(grid: readonly (readonly number[])[], axes: MatrixAxes): BlockMatrix["cells"] {
  return axes.grades.flatMap((gradeCode, row) =>
    axes.difficulties.map((difficultyCode, column) => ({ gradeCode, difficultyCode, value: grid[row]?.[column] ?? 0 })),
  );
}

export function dimensionValues(values: readonly MatrixDimensionValue[], codes: readonly string[]): number[] {
  return codes.map((code) => values.find((item) => item.code === code)?.value ?? 0);
}

export function toDimension(values: readonly number[], codes: readonly string[]): MatrixDimensionValue[] {
  return codes.map((code, index) => ({ code, value: values[index] ?? 0 }));
}

export function emptyMatrix(): BlockMatrix {
  return { mode: "COMBINED", unit: "COUNT", cells: [], byGrade: [], byDifficulty: [] };
}

function rowTotals(grid: readonly (readonly number[])[]): number[] {
  return grid.map((row) => sum(row));
}

function columnTotals(grid: readonly (readonly number[])[], columns: number): number[] {
  return Array.from({ length: columns }, (_, column) => sum(grid.map((row) => row[column] ?? 0)));
}

function chunk(values: readonly number[], size: number): number[][] {
  const rows: number[][] = [];
  if (size <= 0) return rows;
  for (let index = 0; index < values.length; index += size) rows.push(values.slice(index, index + size));
  return rows;
}

/** Phân bổ của một khối sau quy đổi: số câu nguyên làm căn cứ sinh đề. */
export interface BlockAllocation {
  mode: MatrixMode;
  unit: MatrixUnit;
  /** Chế độ kết hợp: số câu theo ô (dòng khối lớp, cột độ khó). Chế độ riêng: `null`. */
  grid: number[][] | null;
  /** Chế độ kết hợp nhập theo %: quy đổi theo thứ tự ô (hết dòng này sang dòng sau). */
  gridConversion: Apportionment | null;
  /** Chế độ riêng: số câu theo khối lớp và theo độ khó. */
  byGrade: number[];
  byDifficulty: number[];
  gradeConversion: Apportionment | null;
  difficultyConversion: Apportionment | null;
  /** Tổng số câu sau quy đổi theo dòng/cột (kết hợp) hoặc theo từng chiều (riêng). */
  rowTotals: number[];
  columnTotals: number[];
  /** Tổng giá trị đã nhập (số câu hoặc %), theo dòng/cột và toàn bảng. */
  inputRowTotals: number[];
  inputColumnTotals: number[];
  /** Kết hợp: tổng ô. Riêng: tổng theo khối lớp và theo độ khó. */
  inputTotal: number;
  inputGradeTotal: number;
  inputDifficultyTotal: number;
  /** Kết hợp: tổng số câu sau quy đổi. Riêng: tổng theo khối lớp (chiều độ khó có `difficultyTotal`). */
  total: number;
  gradeTotal: number;
  difficultyTotal: number;
  /**
   * Chiều năng lực (V-ACT), cộng thêm vào ma trận Lớp × Độ khó: mã theo thứ tự đã khai báo, số câu sau quy đổi, tổng
   * đã nhập và tổng số câu. Khối không dùng chiều này: `null`.
   */
  competency: {
    codes: string[];
    input: number[];
    counts: number[];
    conversion: Apportionment | null;
    inputTotal: number;
    total: number;
  } | null;
}

/** Khối có dùng chiều năng lực: `byCompetency` có ít nhất một mã. */
export function hasCompetencyDimension(matrix: BlockMatrix): boolean {
  return (matrix.byCompetency?.length ?? 0) > 0;
}

function competencyAllocation(matrix: BlockMatrix, questionCount: number): BlockAllocation["competency"] {
  if (!hasCompetencyDimension(matrix)) return null;
  const values = matrix.byCompetency ?? [];
  const input = values.map((item) => item.value);
  const conversion = matrix.unit === "PERCENT" ? apportionByPercent(input, questionCount) : null;
  const counts = conversion ? conversion.results : input;
  return {
    codes: values.map((item) => item.code),
    input,
    counts,
    conversion,
    inputTotal: sum(input),
    total: sum(counts),
  };
}

/** Nhóm câu chùm (B9): lấy nguyên chùm, số câu là `setCount × setSize`. */
export function isSetBlock(block: Pick<QuestionBlock, "questionType">): boolean {
  return block.questionType === "QUESTION_SET" || block.questionType === "CLOZE";
}

export function computeAllocation(
  block: { questionCount: number | null; matrix: BlockMatrix | null },
  axes: MatrixAxes,
): BlockAllocation {
  const matrix = block.matrix ?? emptyMatrix();
  const questionCount = block.questionCount ?? 0;
  const percent = matrix.unit === "PERCENT";
  if (matrix.mode === "COMBINED") {
    const input = combinedGrid(matrix, axes);
    const conversion = percent ? apportionByPercent(input.flat(), questionCount) : null;
    const grid = conversion ? chunk(conversion.results, axes.difficulties.length) : input;
    const rows = rowTotals(grid);
    return {
      mode: "COMBINED",
      unit: matrix.unit,
      grid,
      gridConversion: conversion,
      byGrade: rows,
      byDifficulty: columnTotals(grid, axes.difficulties.length),
      gradeConversion: null,
      difficultyConversion: null,
      rowTotals: rows,
      columnTotals: columnTotals(grid, axes.difficulties.length),
      inputRowTotals: rowTotals(input),
      inputColumnTotals: columnTotals(input, axes.difficulties.length),
      inputTotal: sum(input.flat()),
      inputGradeTotal: sum(input.flat()),
      inputDifficultyTotal: sum(input.flat()),
      total: sum(rows),
      gradeTotal: sum(rows),
      difficultyTotal: sum(rows),
      competency: competencyAllocation(matrix, questionCount),
    };
  }
  const gradeInput = dimensionValues(matrix.byGrade, axes.grades);
  const difficultyInput = dimensionValues(matrix.byDifficulty, axes.difficulties);
  const gradeConversion = percent ? apportionByPercent(gradeInput, questionCount) : null;
  const difficultyConversion = percent ? apportionByPercent(difficultyInput, questionCount) : null;
  const byGrade = gradeConversion ? gradeConversion.results : gradeInput;
  const byDifficulty = difficultyConversion ? difficultyConversion.results : difficultyInput;
  return {
    mode: "SEPARATE",
    unit: matrix.unit,
    grid: null,
    gridConversion: null,
    byGrade,
    byDifficulty,
    gradeConversion,
    difficultyConversion,
    rowTotals: byGrade,
    columnTotals: byDifficulty,
    inputRowTotals: gradeInput,
    inputColumnTotals: difficultyInput,
    inputTotal: sum(gradeInput),
    inputGradeTotal: sum(gradeInput),
    inputDifficultyTotal: sum(difficultyInput),
    total: sum(byGrade),
    gradeTotal: sum(byGrade),
    difficultyTotal: sum(byDifficulty),
    competency: competencyAllocation(matrix, questionCount),
  };
}

// ---------------------------------------------------------------- chuyển cách khai báo

/**
 * Đổi cách khai báo của ma trận (UniNote PBI-04 mục 7.1): dữ liệu được chuyển để quản trị viên kiểm tra, không giữ
 * hai bộ ràng buộc. Kết hợp → riêng: tổng dòng và tổng cột. Riêng → kết hợp: chia ô theo tích hai chiều, làm tròn bằng
 * phần dư lớn nhất. Giữ nguyên đơn vị đang nhập.
 */
export function convertMatrixMode(matrix: BlockMatrix, mode: MatrixMode, axes: MatrixAxes): BlockMatrix {
  if (matrix.mode === mode) return matrix;
  if (mode === "SEPARATE") {
    const grid = combinedGrid(matrix, axes);
    return {
      ...matrix,
      mode,
      cells: [],
      byGrade: toDimension(rowTotals(grid), axes.grades),
      byDifficulty: toDimension(columnTotals(grid, axes.difficulties.length), axes.difficulties),
    };
  }
  const grades = dimensionValues(matrix.byGrade, axes.grades);
  const difficulties = dimensionValues(matrix.byDifficulty, axes.difficulties);
  return {
    ...matrix,
    mode,
    cells: gridToCells(fillToMarginals(grades, difficulties), axes),
    byGrade: [],
    byDifficulty: [],
  };
}

/**
 * Lưới số nguyên có tổng dòng và tổng cột đúng bằng hai chiều đã khai báo (khi hai chiều cùng tổng): lấy phần nguyên
 * của tích tỷ lệ, rồi cộng từng đơn vị còn thiếu vào ô có phần thập phân lớn nhất mà dòng và cột đều còn thiếu.
 * Hai chiều lệch tổng thì chia theo tích tỷ lệ trên tổng của chiều lớp.
 */
function fillToMarginals(rows: readonly number[], columns: readonly number[]): number[][] {
  const total = sum(rows);
  const columnTotal = sum(columns);
  const valid = [...rows, ...columns].every((value) => Number.isInteger(value) && value >= 0);
  if (total <= 0 || columnTotal <= 0) return rows.map(() => columns.map(() => 0));
  const theoretical = rows.map((row) => columns.map((column) => (row * column) / columnTotal));
  if (!valid || total !== columnTotal) {
    return chunk(apportionByPercent(theoretical.flat().map((value) => (value * 100) / total), total).results, columns.length);
  }
  const grid = theoretical.map((row) => row.map((value) => Math.floor(value + EPSILON)));
  const rowMissing = rows.map((row, index) => row - sum(grid[index] ?? []));
  const columnMissing = columns.map((column, index) => column - sum(grid.map((row) => row[index] ?? 0)));
  const order = theoretical
    .flatMap((row, i) => row.map((value, j) => ({ i, j, fraction: Math.round((value - Math.floor(value + EPSILON)) * 1e9) })))
    .sort((a, b) => b.fraction - a.fraction || a.i - b.i || a.j - b.j);
  let missing = sum(rowMissing);
  while (missing > 0) {
    let placed = false;
    for (const { i, j } of order) {
      if ((rowMissing[i] ?? 0) > 0 && (columnMissing[j] ?? 0) > 0) {
        grid[i]![j]! += 1;
        rowMissing[i]! -= 1;
        columnMissing[j]! -= 1;
        missing -= 1;
        placed = true;
      }
    }
    if (!placed) break;
  }
  return grid;
}

/** Đổi đơn vị nhập: % → số câu theo phần dư lớn nhất; số câu → % nguyên tổng 100. */
export function convertMatrixUnit(
  matrix: BlockMatrix,
  unit: MatrixUnit,
  questionCount: number,
  axes: MatrixAxes,
): BlockMatrix {
  if (matrix.unit === unit) return matrix;
  const convert = (values: readonly number[]) =>
    unit === "COUNT" ? apportionByPercent(values, questionCount).results : countsToPercents(values, questionCount);
  const competency = hasCompetencyDimension(matrix)
    ? {
        byCompetency: toDimension(
          convert((matrix.byCompetency ?? []).map((item) => item.value)),
          (matrix.byCompetency ?? []).map((item) => item.code),
        ),
      }
    : {};
  if (matrix.mode === "COMBINED") {
    const values = convert(combinedGrid(matrix, axes).flat());
    return { ...matrix, ...competency, unit, cells: gridToCells(chunk(values, axes.difficulties.length), axes) };
  }
  return {
    ...matrix,
    ...competency,
    unit,
    byGrade: toDimension(convert(dimensionValues(matrix.byGrade, axes.grades)), axes.grades),
    byDifficulty: toDimension(convert(dimensionValues(matrix.byDifficulty, axes.difficulties)), axes.difficulties),
  };
}

// ---------------------------------------------------------------- kiểm tra một khối

export type MatrixDimension = "COMBINED" | "GRADE" | "DIFFICULTY" | "COMPETENCY";

export type BlockIssue =
  | { code: "COUNT_NOT_POSITIVE" }
  | { code: "SET_SIZE_MISMATCH"; expected: number; actual: number }
  | { code: "INVALID_VALUE" }
  | { code: "PERCENT_TOTAL"; dimension: MatrixDimension; actual: number }
  | { code: "ALLOCATION_TOTAL"; dimension: MatrixDimension; expected: number; actual: number };

function matrixValues(matrix: BlockMatrix): number[] {
  const base =
    matrix.mode === "COMBINED"
      ? matrix.cells.map((cell) => cell.value)
      : [...matrix.byGrade, ...matrix.byDifficulty].map((item) => item.value);
  return [...base, ...(matrix.byCompetency ?? []).map((item) => item.value)];
}

const PERCENT_TOLERANCE = 1e-6;

/** Kiểm tra một khối với số câu của nó (UniNote PBI-04 mục 6.2, 7.3). */
export function validateBlock(block: QuestionBlock, axes: MatrixAxes): BlockIssue[] {
  const issues: BlockIssue[] = [];
  // `questionCount = null`: nhóm "lấy phần còn lại" của môn, số câu do môn quyết định.
  if (block.questionCount !== null && (!Number.isInteger(block.questionCount) || block.questionCount <= 0)) {
    issues.push({ code: "COUNT_NOT_POSITIVE" });
  }
  if (isSetBlock(block) && block.questionCount !== null) {
    const expected = (block.setCount ?? 0) * (block.setSize ?? 0);
    if (expected !== block.questionCount) {
      issues.push({ code: "SET_SIZE_MISMATCH", expected, actual: block.questionCount });
    }
  }
  return [...issues, ...validateMatrix(block.matrix, block.questionCount, axes)];
}

/**
 * Kiểm tra ma trận với số câu của khối hoặc của môn: giá trị hợp lệ, tổng tỷ lệ bằng 100, tổng số câu bằng số câu. Số
 * câu `null` (nhóm lấy phần còn lại) và ma trận trống thì không có gì để so.
 */
export function validateMatrix(matrix: BlockMatrix | null, questionCount: number | null, axes: MatrixAxes): BlockIssue[] {
  const issues: BlockIssue[] = [];
  // Chỉ tiêu riêng của nhóm là tùy chọn; chỉ tiêu theo % không cần số câu của nhóm khi nhóm lấy phần còn lại.
  if (!matrix || questionCount === null) return issues;
  const values = matrixValues(matrix);
  if (values.some((value) => value < 0 || !Number.isFinite(value) || (matrix.unit === "COUNT" && !Number.isInteger(value)))) {
    issues.push({ code: "INVALID_VALUE" });
    return issues;
  }
  const allocation = computeAllocation({ questionCount, matrix }, axes);
  const dimensions: Array<{ dimension: MatrixDimension; input: number; total: number }> =
    matrix.mode === "COMBINED"
      ? [{ dimension: "COMBINED", input: allocation.inputTotal, total: allocation.total }]
      : [
          { dimension: "GRADE", input: allocation.inputGradeTotal, total: allocation.gradeTotal },
          { dimension: "DIFFICULTY", input: allocation.inputDifficultyTotal, total: allocation.difficultyTotal },
        ];
  if (allocation.competency) {
    dimensions.push({ dimension: "COMPETENCY", input: allocation.competency.inputTotal, total: allocation.competency.total });
  }
  const questionBasis = (matrix.basis ?? "QUESTION") === "QUESTION";
  for (const { dimension, input, total } of dimensions) {
    // Bảng tham khảo chưa có số (HSA Tiếng Anh khối lớp, chờ chuyên gia): chưa khai, không báo lệch tổng.
    const enforcement =
      dimension === "GRADE" || dimension === "COMBINED"
        ? matrix.gradeEnforcement
        : dimension === "DIFFICULTY"
          ? matrix.difficultyEnforcement
          : undefined;
    if (enforcement === "REFERENCE" && input === 0) continue;
    if (matrix.unit === "PERCENT") {
      if (Math.abs(input - 100) > PERCENT_TOLERANCE) issues.push({ code: "PERCENT_TOTAL", dimension, actual: roundPrecision(input) });
    } else if (questionBasis && total !== questionCount) {
      // Đơn vị lệnh hỏi hoặc điểm (chỉ ở bảng tham khảo, H7) không phải số câu nên không so với số câu.
      issues.push({ code: "ALLOCATION_TOTAL", dimension, expected: questionCount, actual: total });
    }
  }
  return issues;
}

// ---------------------------------------------------------------- cấu trúc

/** Vị trí của một môn: phần, lựa chọn của phần tự chọn (nếu có), môn. Môn chung của phần tự chọn có `optionId` null. */
export interface SectionLocation {
  partId: string;
  optionId: string | null;
  sectionId: string;
}

export type StructureIssue =
  | { code: "PART_COUNT_NOT_POSITIVE"; partId: string }
  | { code: "PART_DURATION_NOT_POSITIVE"; partId: string }
  | { code: "NO_OPTIONS"; partId: string }
  | { code: "PART_PAPER_COUNT"; partId: string; onPaper: number; required: number }
  | { code: "PART_CHOOSE_INVALID"; partId: string; choose: number; options: number }
  | { code: "SECTIONS_TOTAL"; partId: string; optionId: string | null; expected: number; actual: number }
  | { code: "PART_POINTS"; partId: string; optionId: string | null; expected: number; actual: number }
  | { code: "BLOCKS_TOTAL"; location: SectionLocation; expected: number; actual: number }
  | { code: "SUBJECT_COUNT_EXCEEDS_ALLOCATION"; location: SectionLocation; declared: number; allocated: number }
  | { code: "SUBJECT_TRIM_IMPOSSIBLE"; location: SectionLocation; declared: number; allocated: number }
  | { code: "BLOCK"; location: SectionLocation; blockId: string; issue: BlockIssue };

/**
 * Nhóm môn theo vị trí lưu: phần cố định có một nhóm (`optionId` null); phần tự chọn có nhóm môn chung (`optionId` null,
 * khi `sections` khác rỗng) rồi mỗi lựa chọn một nhóm. Dùng để tìm, sửa, liệt kê khối; mỗi khối nằm đúng một nhóm.
 */
export function sectionGroups(part: ExamPart): Array<{ optionId: string | null; sections: ExamSection[] }> {
  if (part.mode !== "CHOICE") return [{ optionId: null, sections: part.sections }];
  const common = part.sections.length ? [{ optionId: null, sections: part.sections }] : [];
  return [...common, ...part.options.map((option) => ({ optionId: option.id, sections: option.sections }))];
}

/**
 * Các môn học viên làm theo từng lựa chọn của phần: phần cố định một nhóm; phần tự chọn mỗi lựa chọn một nhóm gồm môn
 * chung cộng môn của lựa chọn (THPTQG Tin học: 2 câu chung + 2 câu của nhánh).
 */
export function choiceGroups(part: ExamPart): Array<{ optionId: string | null; sections: ExamSection[] }> {
  if (part.mode !== "CHOICE") return [{ optionId: null, sections: part.sections }];
  return part.options.map((option) => ({ optionId: option.id, sections: [...part.sections, ...option.sections] }));
}

/** Số câu của môn hoặc nhóm khi khai báo sẵn; `null` (nhóm lấy phần còn lại, dữ liệu trước H3) tính 0 ở tổng cố định. */
export function countOf(item: { questionCount: number | null }): number {
  return item.questionCount ?? 0;
}

/**
 * Số câu từng nhóm khi môn nhận `count` câu và phần câu thừa chưa bỏ được (`excess` > 0 nghĩa là không có câu đơn nào
 * để bỏ): nhóm câu đơn `questionCount = null` ("lấy phần còn lại", dữ liệu trước H3) nhận phần chênh giữa số câu của
 * môn và tổng nhóm cố định; môn có hơn một nhóm như vậy không chia được. Tổng nhóm cố
 * định vượt `count` (môn `SUBJECT_CHOICE` khai 17 nhưng nhận 16, H3) thì bỏ câu từ cuối, chỉ ở nhóm câu đơn; chùm giữ
 * nguyên, cùng nguyên tắc với `TrimPolicy` của máy chủ.
 *
 * Chỉ là ước lượng cho bản xem trước: máy chủ chọn câu bị bỏ bằng hạt giống trong mọi nhóm câu đơn (ưu tiên câu không
 * góp vào chỉ tiêu bắt buộc), nên nhóm nào mất câu — và điểm theo nhóm — trên đề thật có thể khác. Tổng số câu thì đúng.
 */
function trimGroupCounts(section: ExamSection, count: number): { counts: number[]; excess: number } {
  const remainderGroups = section.blocks.filter((block) => block.questionCount === null);
  const remainder = remainderGroups.length === 1 ? Math.max(0, count - sum(section.blocks.map(countOf))) : 0;
  const counts = section.blocks.map((block) => block.questionCount ?? remainder);
  let excess = sum(counts) - count;
  for (let index = counts.length - 1; index >= 0 && excess > 0; index--) {
    const block = section.blocks[index]!;
    if (block.questionCount === null || isSetBlock(block)) continue;
    const drop = Math.min(counts[index]!, excess);
    counts[index] = counts[index]! - drop;
    excess -= drop;
  }
  return { counts, excess };
}

/** Số câu từng nhóm khi môn nhận `count` câu (xem `trimGroupCounts`; phần thừa không bỏ được do `validateStructure` báo). */
export function groupCounts(section: ExamSection, count: number): number[] {
  return trimGroupCounts(section, count).counts;
}

/**
 * Cách chia số câu của môn trên các nhóm đang tính: lọc nhóm `omitted` trước `groupCounts` để nhóm bỏ qua không lấy mất
 * phần số câu của môn và không vào tổng điểm (2B 3.3, 5; R2-06). `count` `null` (môn không khai số câu) trả về số câu khai
 * báo của nhóm; `count` 0 (môn của `SUBJECT_CHOICE` không được chọn) trả về toàn 0. Hàm chung của `pointsRows` và
 * `structureTotals` để hai nơi không lệch nhau.
 */
export function activeGroupCounts(section: ExamSection, count: number | null): { blocks: QuestionBlock[]; counts: number[] } {
  const blocks = section.blocks.filter((block) => !block.omitted);
  return { blocks, counts: count === null ? blocks.map(countOf) : count === 0 ? blocks.map(() => 0) : groupCounts({ ...section, blocks }, count) };
}

/**
 * Cách chia số câu `countsByPick` cho các môn được chọn của phương án `SUBJECT_CHOICE`, ánh xạ theo chỉ số môn trong
 * `option.sections`; môn không được chọn không có mục. Duyệt các tổ hợp `chooseSubjects` môn theo thứ tự cấu trúc và lấy
 * cách chia hợp lệ đầu tiên — cùng quy tắc B10 của máy chủ (`ChoiceGroupAllocator`). Không có cách chia hợp lệ (cấu
 * hình sai) trả bảng rỗng.
 */
function subjectChoiceAllocations(option: ExamPart["options"][number]): Map<number, number> {
  const allocations = new Map<number, number>();
  if (!option.countsByPick) return allocations;
  const choose = option.chooseSubjects ?? option.countsByPick.length;
  const total = option.sections.length;
  const pick: number[] = [];
  const visit = (start: number): boolean => {
    if (pick.length === choose) {
      const found = allocateSubjectCounts(
        pick.map((index) => option.sections[index]!.choiceGroup),
        option.countsByPick!,
      );
      if (found.length === 0) return false;
      pick.forEach((sectionIndex, position) => allocations.set(sectionIndex, found[0]![position]!));
      return true;
    }
    for (let index = start; index <= total - (choose - pick.length); index++) {
      pick.push(index);
      if (visit(index + 1)) return true;
      pick.pop();
    }
    return false;
  };
  visit(0);
  return allocations;
}

/**
 * Số câu học viên làm của từng môn trong phương án `SUBJECT_CHOICE`, ánh xạ theo chỉ số môn trong `option.sections`;
 * môn không được chọn nhận 0. Quy tắc HSA 17 − 1 (H3): mỗi môn khai `questionCount` là số câu cần giải (17), còn số
 * câu lên đề do cách chia `countsByPick` quyết — môn nhận cách chia nhỏ hơn lên đề ít hơn (17 → 16, máy chủ giải 17
 * rồi `TrimPolicy` bỏ một câu). Số câu trên đề của môn là `min(questionCount, cách chia)`; dữ liệu cũ để `null`
 * (trước H3) vẫn nhận trọn cách chia. Khai nhiều hơn cách chia quá một câu, hoặc không bỏ được câu đơn nào, là cấu hình
 * máy chủ từ chối; `validateStructure` báo, còn đây vẫn cắt về cách chia để bản xem trước có số.
 *
 * Không có cách chia hợp lệ (cấu hình sai) trả bảng rỗng: người gọi lùi về số câu khai báo để tiêu đề lộ phần thiếu.
 */
export function subjectChoiceCounts(option: ExamPart["options"][number]): Map<number, number> {
  const allocations = subjectChoiceAllocations(option);
  const counts = new Map<number, number>();
  if (allocations.size === 0) return counts;
  // Môn không được chọn tính 0 câu: học viên chọn một cách chia, không làm cả năm môn.
  option.sections.forEach((section, index) => {
    const allocated = allocations.get(index);
    counts.set(index, allocated === undefined ? 0 : Math.min(section.questionCount ?? allocated, allocated));
  });
  return counts;
}

function blocksOf(sections: readonly ExamSection[]): QuestionBlock[] {
  return sections.flatMap((section) => section.blocks);
}

/** Điểm mỗi câu mặc định (B5) khi khối không đặt điểm mỗi câu yêu cầu. */
export const DEFAULT_POINTS_PER_QUESTION = 1;

/** Điểm mỗi câu của khối: điểm mỗi câu yêu cầu nếu có, không thì mặc định 1 (điểm thật lấy từ phiên bản câu khi sinh đề). */
export function blockPointsEach(block: Pick<QuestionBlock, "requiredPointsPerQuestion">): number {
  return block.requiredPointsPerQuestion ?? DEFAULT_POINTS_PER_QUESTION;
}

/** Tổng điểm các khối của một nhóm môn (số câu × điểm mỗi câu). */
export function sectionsPoints(sections: readonly ExamSection[]): number {
  return roundPrecision(sum(blocksOf(sections).map((block) => countOf(block) * blockPointsEach(block))));
}

/** Điểm riêng của phần (BCA 25 / 60 / 15); `null` khi phần không chia điểm. */
export function partPoints(part: ExamPart): number | null {
  return part.maxPoints ?? null;
}

export interface ValidateStructureOptions {
  /** `PER_PART`: phần phải có thời lượng dương. `SHARED`: phần không có thời lượng riêng. Bỏ trống: chỉ kiểm khi có. */
  timing?: ExamTiming;
}

/**
 * Kiểm tra cấu trúc trên máy (mục 6.2, 5.3): số câu phần = tổng môn (mỗi lựa chọn tính cả môn chung), môn = tổng khối,
 * điểm phần = tổng điểm khối khi phần có điểm riêng (BCA), từng khối hợp lệ.
 */
export function validateStructure(
  parts: readonly ExamPart[],
  axes: MatrixAxes,
  options: ValidateStructureOptions = {},
): StructureIssue[] {
  const issues: StructureIssue[] = [];
  for (const part of parts) {
    if (!Number.isInteger(part.questionCount) || part.questionCount <= 0) {
      issues.push({ code: "PART_COUNT_NOT_POSITIVE", partId: part.id });
    }
    // Thời lượng `null`: bài dùng thời lượng chung (BCA, V-ACT), phần không có thời lượng riêng.
    const missing = options.timing === "PER_PART" && part.durationMinutes === null;
    const notPositive = part.durationMinutes !== null && !(part.durationMinutes > 0);
    if (options.timing !== "SHARED" && (missing || notPositive)) {
      issues.push({ code: "PART_DURATION_NOT_POSITIVE", partId: part.id });
    }
    if (part.mode === "CHOICE" && part.options.length === 0) issues.push({ code: "NO_OPTIONS", partId: part.id });
    // Số câu trên đề phải đủ cho số câu học viên làm (PBI-04 mục 5.2).
    if (!(part.questionCountOnPaper >= part.questionCountRequired)) {
      issues.push({
        code: "PART_PAPER_COUNT",
        partId: part.id,
        onPaper: part.questionCountOnPaper,
        required: part.questionCountRequired,
      });
    }
    if (part.mode === "CHOICE" && part.options.length > 0) {
      const choose = part.participation.choose ?? 1;
      if (!Number.isInteger(choose) || choose < 1 || choose > part.options.length) {
        issues.push({ code: "PART_CHOOSE_INVALID", partId: part.id, choose, options: part.options.length });
      }
    }
    const expectedPoints = partPoints(part);
    for (const group of choiceGroups(part)) {
      const option = part.options.find((item) => item.id === group.optionId);
      const sectionsTotal =
        option?.kind === "SUBJECT_CHOICE"
          ? sum(option.countsByPick ?? []) + sum(part.sections.map(countOf))
          : sum(group.sections.map(countOf));
      if (sectionsTotal !== part.questionCount) {
        issues.push({
          code: "SECTIONS_TOTAL",
          partId: part.id,
          optionId: group.optionId,
          expected: part.questionCount,
          actual: sectionsTotal,
        });
      }
      if (expectedPoints !== null && option?.kind !== "SUBJECT_CHOICE") {
        const actual = sectionsPoints(group.sections);
        if (Math.abs(actual - expectedPoints) > PERCENT_TOLERANCE) {
          issues.push({ code: "PART_POINTS", partId: part.id, optionId: group.optionId, expected: expectedPoints, actual });
        }
      }
    }
    for (const group of sectionGroups(part)) {
      for (const section of group.sections) {
        const location = { partId: part.id, optionId: group.optionId, sectionId: section.id };
        // Môn của `SUBJECT_CHOICE` khai `questionCount` là số cần giải (HSA: 17, H3) nên vẫn đối chiếu tổng nhóm;
        // chỉ dữ liệu cũ để `null` thì tổng nhóm do kiểm tra cấu hình báo theo từng cách chọn.
        if (section.questionCount !== null) {
          const blocksTotal = sum(section.blocks.map(countOf));
          if (blocksTotal !== section.questionCount) {
            issues.push({ code: "BLOCKS_TOTAL", location, expected: section.questionCount, actual: blocksTotal });
          }
        }
        const subjectChoice = part.options.find((item) => item.id === group.optionId);
        if (subjectChoice?.kind === "SUBJECT_CHOICE") {
          const allocated = subjectChoiceAllocations(subjectChoice).get(subjectChoice.sections.indexOf(section));
          const declared = section.questionCount;
          // Chỉ môn được chọn có cách chia; môn không được chọn hoặc dữ liệu cũ `null` không có gì để đối chiếu.
          if (allocated !== undefined && declared !== null && declared > allocated) {
            if (declared - allocated > 1) {
              // Máy chủ chỉ bỏ được đúng một câu (`PaperSolver`: "requires exactly one count below the maximum by one").
              issues.push({ code: "SUBJECT_COUNT_EXCEEDS_ALLOCATION", location, declared, allocated });
            } else if (trimGroupCounts(section, allocated).excess > 0) {
              // Không còn câu đơn để bỏ (cả môn là chùm, hoặc nhóm câu đơn rỗng): máy chủ trả `NO_JOINT_SOLUTION`.
              issues.push({ code: "SUBJECT_TRIM_IMPOSSIBLE", location, declared, allocated });
            }
          }
        }
        for (const block of section.blocks) {
          for (const issue of validateBlock(block, axes)) {
            issues.push({ code: "BLOCK", location, blockId: block.id, issue });
          }
        }
      }
    }
  }
  return issues;
}

export interface StructureTotals {
  partCount: number;
  /** Số câu học viên làm (mỗi phần tự chọn tính một lựa chọn). */
  totalQuestions: number;
  /**
   * Số câu in trên đề: phần tự chọn theo nhánh nội dung (`BRANCH`) in cả môn chung và mọi nhánh (THPTQG Tin học in 30,
   * học viên làm 28). Phần chọn môn hoặc tổ hợp là các đề riêng, tính như số câu học viên làm.
   */
  printedQuestions: number;
  /** Bài dùng thời lượng chung: thời lượng chung; không thì tổng thời lượng các phần. */
  totalMinutes: number;
  /** Tổng điểm theo số câu × điểm mỗi câu; phần tự chọn tính theo lựa chọn đầu tiên (cộng môn chung). */
  totalPoints: number;
  /** Số câu đã phân bổ vào khối; phần tự chọn lấy lựa chọn ít câu nhất (đường thiếu nhiều nhất). */
  allocated: number;
  missing: number;
}

export interface StructureTotalsOptions {
  /** Thời lượng chung của bài (`timing = SHARED`); `null` hoặc bỏ trống: cộng thời lượng các phần. */
  sharedDuration?: number | null;
}

/** Số câu in trên đề của một phần (xem `StructureTotals.printedQuestions`). */
export function printedPartQuestions(part: ExamPart): number {
  if (part.mode !== "CHOICE" || !part.options.some((option) => option.kind === "BRANCH")) return part.questionCount;
  return sum([...part.sections, ...part.options.flatMap((option) => option.sections)].map(countOf));
}

export function structureTotals(parts: readonly ExamPart[], options: StructureTotalsOptions = {}): StructureTotals {
  let allocated = 0;
  let totalPoints = 0;
  for (const part of parts) {
    const groups = choiceGroups(part);
    const counts = groups.map((group) => {
      const option = part.options.find((item) => item.id === group.optionId);
      // `SUBJECT_CHOICE`: số câu từng môn tính theo `countsByPick`, không có trên môn (D25). Hai vế cộng số câu khai
      // báo của các nhóm đang tính (không gồm nhóm `omitted`): nhóm "lấy phần còn lại" góp 0, chênh lệch lộ ở `missing`.
      if (option?.kind === "SUBJECT_CHOICE")
        return sum(option.countsByPick ?? []) + sum(part.sections.map((section) => sum(activeGroupCounts(section, null).counts)));
      return sum(group.sections.map((section) => sum(activeGroupCounts(section, null).counts)));
    });
    allocated += counts.length ? Math.min(...counts) : 0;
    const first = groups[0];
    // G1: nhóm "lấy phần còn lại" nhận phần chênh của môn (cùng cách chia với kiểm tra), không tính 0 điểm; môn của
    // `SUBJECT_CHOICE` nhận số câu trên đề theo `subjectChoiceCounts` (HSA 17 − 1: môn được bỏ câu tính 16). Nhóm
    // `omitted` bị lọc trước khi chia, cùng quy tắc với `pointsRows`.
    const option = part.options.find((item) => item.id === first?.optionId);
    const subjectCounts = option?.kind === "SUBJECT_CHOICE" ? subjectChoiceCounts(option) : null;
    totalPoints += sum(
      (first?.sections ?? []).flatMap((section, index) => {
        const count = subjectCounts?.get(index - part.sections.length) ?? section.questionCount ?? null;
        const { blocks, counts: perGroup } = activeGroupCounts(section, count);
        return blocks.map((block, blockIndex) => perGroup[blockIndex]! * blockPointsEach(block));
      }),
    );
  }
  const totalQuestions = sum(parts.map((part) => part.questionCount));
  return {
    partCount: parts.length,
    totalQuestions,
    printedQuestions: sum(parts.map((part) => Math.max(printedPartQuestions(part), part.questionCountOnPaper))),
    totalMinutes: options.sharedDuration ?? sum(parts.map((part) => part.durationMinutes ?? 0)),
    totalPoints: roundPrecision(totalPoints),
    allocated,
    missing: Math.max(0, totalQuestions - allocated),
  };
}
