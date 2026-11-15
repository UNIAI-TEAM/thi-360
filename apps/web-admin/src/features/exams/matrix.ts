import type {
  BlockMatrix,
  ExamPart,
  ExamSection,
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
}

export function computeAllocation(block: Pick<QuestionBlock, "questionCount" | "matrix">, axes: MatrixAxes): BlockAllocation {
  const { matrix, questionCount } = block;
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
  if (matrix.mode === "COMBINED") {
    const values = convert(combinedGrid(matrix, axes).flat());
    return { ...matrix, unit, cells: gridToCells(chunk(values, axes.difficulties.length), axes) };
  }
  return {
    ...matrix,
    unit,
    byGrade: toDimension(convert(dimensionValues(matrix.byGrade, axes.grades)), axes.grades),
    byDifficulty: toDimension(convert(dimensionValues(matrix.byDifficulty, axes.difficulties)), axes.difficulties),
  };
}

// ---------------------------------------------------------------- kiểm tra một khối

export type MatrixDimension = "COMBINED" | "GRADE" | "DIFFICULTY";

export type BlockIssue =
  | { code: "COUNT_NOT_POSITIVE" }
  | { code: "SET_SIZE_MISMATCH"; expected: number; actual: number }
  | { code: "INVALID_VALUE" }
  | { code: "PERCENT_TOTAL"; dimension: MatrixDimension; actual: number }
  | { code: "ALLOCATION_TOTAL"; dimension: MatrixDimension; expected: number; actual: number };

function matrixValues(matrix: BlockMatrix): number[] {
  return matrix.mode === "COMBINED"
    ? matrix.cells.map((cell) => cell.value)
    : [...matrix.byGrade, ...matrix.byDifficulty].map((item) => item.value);
}

const PERCENT_TOLERANCE = 1e-6;

/** Kiểm tra một khối với số câu của nó (UniNote PBI-04 mục 6.2, 7.3). */
export function validateBlock(block: QuestionBlock, axes: MatrixAxes): BlockIssue[] {
  const issues: BlockIssue[] = [];
  if (!Number.isInteger(block.questionCount) || block.questionCount <= 0) issues.push({ code: "COUNT_NOT_POSITIVE" });
  if (block.questionType === "QUESTION_SET") {
    const expected = (block.setCount ?? 0) * (block.setSize ?? 0);
    if (expected !== block.questionCount) {
      issues.push({ code: "SET_SIZE_MISMATCH", expected, actual: block.questionCount });
    }
  }
  const { matrix } = block;
  const values = matrixValues(matrix);
  if (values.some((value) => value < 0 || !Number.isFinite(value) || (matrix.unit === "COUNT" && !Number.isInteger(value)))) {
    issues.push({ code: "INVALID_VALUE" });
    return issues;
  }
  const allocation = computeAllocation(block, axes);
  const dimensions: Array<{ dimension: MatrixDimension; input: number; total: number }> =
    matrix.mode === "COMBINED"
      ? [{ dimension: "COMBINED", input: allocation.inputTotal, total: allocation.total }]
      : [
          { dimension: "GRADE", input: allocation.inputGradeTotal, total: allocation.gradeTotal },
          { dimension: "DIFFICULTY", input: allocation.inputDifficultyTotal, total: allocation.difficultyTotal },
        ];
  for (const { dimension, input, total } of dimensions) {
    if (matrix.unit === "PERCENT") {
      if (Math.abs(input - 100) > PERCENT_TOLERANCE) issues.push({ code: "PERCENT_TOTAL", dimension, actual: roundPrecision(input) });
    } else if (total !== block.questionCount) {
      issues.push({ code: "ALLOCATION_TOTAL", dimension, expected: block.questionCount, actual: total });
    }
  }
  return issues;
}

// ---------------------------------------------------------------- cấu trúc

/** Vị trí của một môn: phần, lựa chọn của phần tự chọn (nếu có), môn. */
export interface SectionLocation {
  partId: string;
  optionId: string | null;
  sectionId: string;
}

export type StructureIssue =
  | { code: "PART_COUNT_NOT_POSITIVE"; partId: string }
  | { code: "PART_DURATION_NOT_POSITIVE"; partId: string }
  | { code: "NO_OPTIONS"; partId: string }
  | { code: "SECTIONS_TOTAL"; partId: string; optionId: string | null; expected: number; actual: number }
  | { code: "BLOCKS_TOTAL"; location: SectionLocation; expected: number; actual: number }
  | { code: "BLOCK"; location: SectionLocation; blockId: string; issue: BlockIssue };

/** Mọi nhóm môn của phần: phần cố định có một nhóm (`optionId` null), phần tự chọn mỗi lựa chọn một nhóm. */
export function sectionGroups(part: ExamPart): Array<{ optionId: string | null; sections: ExamSection[] }> {
  return part.mode === "CHOICE"
    ? part.options.map((option) => ({ optionId: option.id, sections: option.sections }))
    : [{ optionId: null, sections: part.sections }];
}

/** Kiểm tra cấu trúc trên máy (mục 6.2, 5.3): số câu phần = tổng môn, môn = tổng khối, từng khối hợp lệ. */
export function validateStructure(parts: readonly ExamPart[], axes: MatrixAxes): StructureIssue[] {
  const issues: StructureIssue[] = [];
  for (const part of parts) {
    if (!Number.isInteger(part.questionCount) || part.questionCount <= 0) {
      issues.push({ code: "PART_COUNT_NOT_POSITIVE", partId: part.id });
    }
    if (!(part.durationMinutes > 0)) issues.push({ code: "PART_DURATION_NOT_POSITIVE", partId: part.id });
    if (part.mode === "CHOICE" && part.options.length === 0) issues.push({ code: "NO_OPTIONS", partId: part.id });
    for (const group of sectionGroups(part)) {
      const sectionsTotal = sum(group.sections.map((section) => section.questionCount));
      if (sectionsTotal !== part.questionCount) {
        issues.push({
          code: "SECTIONS_TOTAL",
          partId: part.id,
          optionId: group.optionId,
          expected: part.questionCount,
          actual: sectionsTotal,
        });
      }
      for (const section of group.sections) {
        const location = { partId: part.id, optionId: group.optionId, sectionId: section.id };
        const blocksTotal = sum(section.blocks.map((block) => block.questionCount));
        if (blocksTotal !== section.questionCount) {
          issues.push({ code: "BLOCKS_TOTAL", location, expected: section.questionCount, actual: blocksTotal });
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
  totalQuestions: number;
  totalMinutes: number;
  /** Tổng điểm theo số câu × điểm mỗi câu; phần tự chọn tính theo lựa chọn đầu tiên. */
  totalPoints: number;
  /** Số câu đã phân bổ vào khối; phần tự chọn lấy lựa chọn ít câu nhất (đường thiếu nhiều nhất). */
  allocated: number;
  missing: number;
}

function blocksOf(sections: readonly ExamSection[]): QuestionBlock[] {
  return sections.flatMap((section) => section.blocks);
}

export function structureTotals(parts: readonly ExamPart[]): StructureTotals {
  let allocated = 0;
  let totalPoints = 0;
  for (const part of parts) {
    const groups = sectionGroups(part).map((group) => blocksOf(group.sections));
    const counts = groups.map((blocks) => sum(blocks.map((block) => block.questionCount)));
    allocated += counts.length ? Math.min(...counts) : 0;
    const first = groups[0] ?? [];
    totalPoints += sum(first.map((block) => block.questionCount * block.pointsPerQuestion));
  }
  const totalQuestions = sum(parts.map((part) => part.questionCount));
  return {
    partCount: parts.length,
    totalQuestions,
    totalMinutes: sum(parts.map((part) => part.durationMinutes)),
    totalPoints: roundPrecision(totalPoints),
    allocated,
    missing: Math.max(0, totalQuestions - allocated),
  };
}
