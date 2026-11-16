import type {
  BlockAvailability,
  BlockCheck,
  BlockRequirement,
  CheckGroup,
  CheckGroupResult,
  CheckIssue,
  CheckIssueCode,
  ExamAvailability,
  ExamCheckOutcome,
  ExamPathRef,
  PathCheck,
  PathChoice,
  PathSelectionCheck,
  SectionAvailability,
  SubjectCheck,
} from "./review-types";
import { apportionByPercent, blockPointsEach, countOf, isSetBlock, printedPartQuestions, sum } from "./matrix";
import { CHECK_GROUPS } from "./review-types";
import type {
  BlockMatrix,
  ExamDetail,
  ExamPart,
  ExamPartOption,
  ExamSection,
  MatrixDimensionValue,
  QuestionBlock,
} from "./types";

/**
 * Kiểm tra cấu hình bài thi (UniNote PBI-04 mục 7–9), hàm thuần: cùng bài thi và cùng nguồn cho cùng kết quả.
 * Backend sẽ làm việc này thật (kèm bộ giải ghép đề); bản này dùng cho mockup và để màn hình có cùng quy tắc.
 *
 * Quy đổi tỷ lệ theo phần dư lớn nhất dùng `apportionByPercent` của `matrix.ts`, cùng một quy tắc với màn Cấu trúc.
 */

/** Giới hạn xử lý mặc định và giới hạn dài hơn khi người dùng chọn "Kiểm tra lại với thời gian dài hơn" (mẫu). */
export const CHECK_TIME_LIMIT_SECONDS = 30;
export const EXTENDED_CHECK_TIME_LIMIT_SECONDS = 120;

/* ---------- Quy đổi tỷ lệ ---------- */

/** Chia `total` câu theo tỷ lệ phần trăm (tổng 100) theo phần dư lớn nhất; dùng chung quy tắc với màn Cấu trúc. */
export function largestRemainder(total: number, percents: readonly number[]): number[] {
  return apportionByPercent(percents, total).results;
}

/* ---------- Đường lựa chọn ---------- */

export type ChoiceNameResolver = (kind: PathChoice["kind"], code: string) => string;

const codeAsName: ChoiceNameResolver = (_kind, code) => code;

export const DEFAULT_PATH_ID = "default";

export function sortedParts(parts: readonly ExamPart[]): ExamPart[] {
  return [...parts].sort((a, b) => a.order - b.order);
}

/** Liệt kê mọi đường lựa chọn: tích Descartes các lựa chọn của mọi phần tự chọn (theo thứ tự phần). */
export function enumeratePaths(parts: readonly ExamPart[], nameOf: ChoiceNameResolver = codeAsName): ExamPathRef[] {
  let combos: PathChoice[][] = [[]];
  for (const part of sortedParts(parts)) {
    if (part.mode !== "CHOICE" || part.options.length === 0) continue;
    combos = combos.flatMap((prefix) =>
      part.options.map((option) => [
        ...prefix,
        {
          partId: part.id,
          optionId: option.id,
          kind: option.kind,
          code: option.code,
          // Nhánh và tổ hợp sinh từ "chọn k trong n" (chưa có mã danh mục) mang tên riêng; còn lại lấy theo danh mục.
          name: option.name ?? (option.kind === "BRANCH" ? option.code : nameOf(option.kind, option.code)),
        },
      ]),
    );
  }
  return combos.map((choices) => ({
    pathId: choices.length ? choices.map((choice) => choice.optionId).join("+") : DEFAULT_PATH_ID,
    choices,
  }));
}

export interface PathSectionGroup {
  part: ExamPart;
  optionId: string | null;
  sections: ExamSection[];
}

/**
 * Các phần của một đường, mỗi phần kèm môn mà đường đó làm: phần cố định mọi môn; phần tự chọn môn chung (nếu có) rồi
 * môn của lựa chọn đã chọn.
 */
export function pathSections(parts: readonly ExamPart[], path: ExamPathRef): PathSectionGroup[] {
  return sortedParts(parts).map((part) => {
    if (part.mode !== "CHOICE") return { part, optionId: null, sections: part.sections };
    const choice = path.choices.find((item) => item.partId === part.id);
    const option = part.options.find((item) => item.id === choice?.optionId);
    return { part, optionId: option?.id ?? null, sections: [...part.sections, ...(option?.sections ?? [])] };
  });
}

/** Lựa chọn chứa môn trong đường: môn chung của phần tự chọn không thuộc lựa chọn nào (`null`). */
export function sectionOptionId(group: PathSectionGroup, section: ExamSection): string | null {
  return group.part.mode === "CHOICE" && group.part.sections.some((item) => item.id === section.id) ? null : group.optionId;
}

/* ---------- Chỉ tiêu của khối ---------- */

interface MatrixIssue {
  code: Extract<CheckIssueCode, "MATRIX_NEGATIVE" | "PERCENT_TOTAL_INVALID" | "MATRIX_TOTAL_MISMATCH">;
  dimension: "GRADE" | "DIFFICULTY" | "COMPETENCY" | null;
  required: number;
  available: number;
}

function resolveValues(
  total: number,
  unit: BlockMatrix["unit"],
  values: readonly number[],
  dimension: MatrixIssue["dimension"],
): { counts: number[] | null; issues: MatrixIssue[] } {
  if (values.some((value) => value < 0 || !Number.isFinite(value))) {
    return { counts: null, issues: [{ code: "MATRIX_NEGATIVE", dimension, required: 0, available: Math.min(...values) }] };
  }
  const sum = values.reduce((acc, value) => acc + value, 0);
  if (unit === "PERCENT") {
    if (Math.abs(sum - 100) > 1e-6) {
      return { counts: null, issues: [{ code: "PERCENT_TOTAL_INVALID", dimension, required: 100, available: sum }] };
    }
    return { counts: largestRemainder(total, values), issues: [] };
  }
  if (sum !== total) {
    return { counts: null, issues: [{ code: "MATRIX_TOTAL_MISMATCH", dimension, required: total, available: sum }] };
  }
  return { counts: [...values], issues: [] };
}

/** Quy đổi một ma trận thành số câu nguyên cho `total` câu; ma trận sai thì `requirement` là `null` kèm vấn đề. */
export function resolveMatrixRequirement(
  matrix: BlockMatrix,
  total: number,
): {
  requirement: BlockRequirement | null;
  issues: MatrixIssue[];
} {
  const dimension = (values: readonly MatrixDimensionValue[], name: "GRADE" | "DIFFICULTY" | "COMPETENCY") => {
    const resolved = resolveValues(total, matrix.unit, values.map((item) => item.value), name);
    return {
      list: resolved.counts?.map((required, index) => ({ code: values[index]!.code, required })) ?? null,
      issues: resolved.issues,
    };
  };
  // Chiều năng lực (V-ACT) cộng thêm vào ma trận Lớp × Độ khó; khối không khai báo thì bỏ qua.
  const competency: { list: BlockRequirement["byCompetency"] | null; issues: MatrixIssue[] } = matrix.byCompetency?.length
    ? dimension(matrix.byCompetency, "COMPETENCY")
    : { list: [], issues: [] };
  if (matrix.mode === "COMBINED") {
    const resolved = resolveValues(total, matrix.unit, matrix.cells.map((cell) => cell.value), null);
    const issues = [...resolved.issues, ...competency.issues];
    if (!resolved.counts || !competency.list) return { requirement: null, issues };
    const counts = resolved.counts;
    return {
      requirement: {
        mode: "COMBINED",
        cells: matrix.cells.map((cell, index) => ({
          gradeCode: cell.gradeCode,
          difficultyCode: cell.difficultyCode,
          required: counts[index]!,
        })),
        byGrade: [],
        byDifficulty: [],
        byCompetency: competency.list,
      },
      issues,
    };
  }
  const byGrade = dimension(matrix.byGrade, "GRADE");
  const byDifficulty = dimension(matrix.byDifficulty, "DIFFICULTY");
  const issues = [...byGrade.issues, ...byDifficulty.issues, ...competency.issues];
  if (!byGrade.list || !byDifficulty.list || !competency.list) return { requirement: null, issues };
  return {
    requirement: {
      mode: "SEPARATE",
      cells: [],
      byGrade: byGrade.list,
      byDifficulty: byDifficulty.list,
      byCompetency: competency.list,
    },
    issues,
  };
}

/**
 * Chỉ tiêu nguyên của nhóm. Nhóm không có chỉ tiêu riêng (`matrix = null`) có chỉ tiêu rỗng: nhóm chỉ cần đủ số câu,
 * chỉ tiêu của môn nằm ở cấp môn. `total` mặc định là `questionCount` (nhóm lấy phần còn lại truyền số câu đã tính).
 */
export function resolveBlockRequirement(
  block: Pick<QuestionBlock, "questionCount" | "matrix">,
  total: number = block.questionCount ?? 0,
): {
  requirement: BlockRequirement | null;
  issues: MatrixIssue[];
} {
  if (!block.matrix) {
    return { requirement: { mode: "COMBINED", cells: [], byGrade: [], byDifficulty: [], byCompetency: [] }, issues: [] };
  }
  return resolveMatrixRequirement(block.matrix, total);
}

/* ---------- Ma trận riêng: tìm phân bổ thỏa đồng thời hai chiều ---------- */

export function cellKey(gradeCode: string, difficultyCode: string): string {
  return `${gradeCode}:${difficultyCode}`;
}

/**
 * Tìm số câu cho từng ô Lớp × Độ khó sao cho tổng theo lớp và tổng theo độ khó đúng chỉ tiêu, mỗi ô không vượt
 * nguồn (`capacity`). Luồng cực đại trên đồ thị nguồn → lớp → độ khó → đích; không có phương án thì trả `null`.
 */
export function solveSeparate(
  byGrade: readonly { code: string; required: number }[],
  byDifficulty: readonly { code: string; required: number }[],
  capacity: (gradeCode: string, difficultyCode: string) => number,
): Map<string, number> | null {
  const total = byGrade.reduce((sum, item) => sum + item.required, 0);
  if (total !== byDifficulty.reduce((sum, item) => sum + item.required, 0)) return null;
  const gradeLeft = byGrade.map((item) => item.required);
  const difficultyLeft = byDifficulty.map((item) => item.required);
  const flow = byGrade.map(() => byDifficulty.map(() => 0));
  const cap = byGrade.map((grade) => byDifficulty.map((difficulty) => capacity(grade.code, difficulty.code)));

  // Tìm đường tăng luồng bằng BFS trên đồ thị dư (Edmonds–Karp); đồ thị nhỏ (3 × 4) nên đủ nhanh.
  const gradeCount = byGrade.length;
  const difficultyCount = byDifficulty.length;
  let sent = 0;
  while (sent < total) {
    // Nút: 0..g-1 là lớp, g..g+d-1 là độ khó.
    const previous = new Array<number>(gradeCount + difficultyCount).fill(-2);
    const queue: number[] = [];
    for (let g = 0; g < gradeCount; g += 1) {
      if (gradeLeft[g]! > 0) {
        previous[g] = -1;
        queue.push(g);
      }
    }
    let end = -1;
    while (queue.length && end < 0) {
      const node = queue.shift()!;
      if (node < gradeCount) {
        for (let d = 0; d < difficultyCount; d += 1) {
          const target = gradeCount + d;
          if (previous[target] === -2 && flow[node]![d]! < cap[node]![d]!) {
            previous[target] = node;
            if (difficultyLeft[d]! > 0) {
              end = target;
              break;
            }
            queue.push(target);
          }
        }
      } else {
        const d = node - gradeCount;
        for (let g = 0; g < gradeCount; g += 1) {
          if (previous[g] === -2 && flow[g]![d]! > 0) {
            previous[g] = node;
            queue.push(g);
          }
        }
      }
    }
    if (end < 0) return null;
    difficultyLeft[end - gradeCount]! -= 1;
    let node = end;
    while (previous[node] !== -1) {
      const from = previous[node]!;
      if (from < gradeCount) flow[from]![node - gradeCount]! += 1;
      else flow[node]![from - gradeCount]! -= 1;
      node = from;
    }
    gradeLeft[node]! -= 1;
    sent += 1;
  }
  const allocation = new Map<string, number>();
  byGrade.forEach((grade, g) =>
    byDifficulty.forEach((difficulty, d) => allocation.set(cellKey(grade.code, difficulty.code), flow[g]![d]!)),
  );
  return allocation;
}

/** Số câu cần cho từng ô: ma trận kết hợp lấy thẳng; ma trận riêng lấy phương án tìm được (hoặc `null`). */
export function allocateRequirement(
  requirement: BlockRequirement,
  capacity: (gradeCode: string, difficultyCode: string) => number,
): Map<string, number> | null {
  if (requirement.mode === "COMBINED") {
    return new Map(requirement.cells.map((cell) => [cellKey(cell.gradeCode, cell.difficultyCode), cell.required]));
  }
  return solveSeparate(requirement.byGrade, requirement.byDifficulty, capacity);
}

export function availabilityLookup(availability: Pick<BlockAvailability, "cells"> | undefined) {
  const map = new Map((availability?.cells ?? []).map((cell) => [cellKey(cell.gradeCode, cell.difficultyCode), cell.count]));
  return (gradeCode: string, difficultyCode: string) => map.get(cellKey(gradeCode, difficultyCode)) ?? 0;
}

/* ---------- Kiểm tra ---------- */

const GROUP_OF: Record<CheckIssueCode, CheckGroup> = {
  INFO_MISSING: "INFO",
  OPTION_EMPTY: "STRUCTURE",
  PATH_TOTAL_MISMATCH: "STRUCTURE",
  PART_TOTAL_MISMATCH: "STRUCTURE",
  SECTION_TOTAL_MISMATCH: "STRUCTURE",
  MATRIX_TOTAL_MISMATCH: "MATRIX",
  MATRIX_NEGATIVE: "MATRIX",
  PERCENT_TOTAL_INVALID: "MATRIX",
  SET_SIZE_MISMATCH: "QUESTION_SET",
  CELL_SHORTAGE: "SOURCE",
  DIMENSION_SHORTAGE: "SOURCE",
  MATRIX_INFEASIBLE: "SOURCE",
  SET_SHORTAGE: "QUESTION_SET",
  OVERLAP_UNCONFIRMED: "OVERLAP",
  YEARS_UNCONFIRMED: "INFO",
  OPTION_UNCONFIRMED: "STRUCTURE",
  PART_POINTS_MISMATCH: "POINTS",
  POINTS_TOTAL_MISMATCH: "POINTS",
  COMPETENCY_SHORTAGE: "SOURCE",
  RUBRIC_MISSING: "SOURCE",
  SCORING_MISMATCH: "POINTS",
  ANSWER_QUOTA_TOTAL_MISMATCH: "MATRIX",
  SUBJECT_MATRIX_TOTAL_MISMATCH: "MATRIX",
  GROUP_ANSWER_TYPE_MISSING: "STRUCTURE",
  SUBJECT_CHOICE_COUNTS_INVALID: "STRUCTURE",
  REMAINDER_GROUP_INVALID: "STRUCTURE",
  SUBJECT_QUOTA_COUNT_VARIES: "MATRIX",
  ANSWER_TYPE_SHORTAGE: "SOURCE",
  SUBJECT_QUOTA_INFEASIBLE: "SOURCE",
  PREVIEW_SUBJECTS_INVALID: "STRUCTURE",
};

/** Mức của từng mã: chưa xác nhận được (giới hạn xử lý), cảnh báo không chặn công bố; còn lại là lỗi. */
const SEVERITY_OF: Partial<Record<CheckIssueCode, CheckIssue["severity"]>> = {
  OVERLAP_UNCONFIRMED: "UNCONFIRMED",
  SCORING_MISMATCH: "WARNING",
};

type IssueInput = Partial<Omit<CheckIssue, "key" | "code" | "severity" | "group">> & { code: CheckIssueCode };

function makeIssue(input: IssueInput): CheckIssue {
  const issue: Omit<CheckIssue, "key"> = {
    code: input.code,
    severity: SEVERITY_OF[input.code] ?? "ERROR",
    group: GROUP_OF[input.code],
    partId: input.partId ?? null,
    partName: input.partName ?? null,
    sectionId: input.sectionId ?? null,
    subjectCode: input.subjectCode ?? null,
    blockId: input.blockId ?? null,
    blockName: input.blockName ?? null,
    optionId: input.optionId ?? null,
    gradeCode: input.gradeCode ?? null,
    difficultyCode: input.difficultyCode ?? null,
    dimension: input.dimension ?? null,
    competencyCode: input.competencyCode ?? null,
    answerType: input.answerType ?? null,
    subjectCount: input.subjectCount ?? null,
    required: input.required ?? null,
    available: input.available ?? null,
    timeLimitSeconds: input.timeLimitSeconds ?? null,
    questionCodes: input.questionCodes ?? null,
  };
  const key = [
    issue.code,
    issue.blockId ?? issue.sectionId ?? issue.partId ?? "exam",
    issue.optionId ?? "",
    issue.gradeCode ?? "",
    issue.difficultyCode ?? "",
    issue.dimension ?? "",
    issue.competencyCode ?? "",
    issue.answerType ?? "",
    issue.subjectCount ?? "",
  ].join("|");
  return { key, ...issue };
}

interface BlockContext {
  part: ExamPart;
  section: ExamSection;
  optionId: string | null;
  /** Số câu của nhóm trong trường hợp đang kiểm (nhóm "lấy phần còn lại" nhận phần chênh); bỏ trống: `questionCount`. */
  count?: number;
}

/** Thiếu nguồn ở ma trận riêng: nêu chiều thiếu; nguồn của chiều đó chỉ nằm ở một ô thì nêu đúng ô đó. */
function diagnoseSeparate(
  requirement: BlockRequirement,
  capacity: (gradeCode: string, difficultyCode: string) => number,
  base: IssueInput,
): CheckIssue[] {
  const issues: CheckIssue[] = [];
  const dimension = (
    list: BlockRequirement["byGrade"],
    others: BlockRequirement["byGrade"],
    name: "GRADE" | "DIFFICULTY",
  ) => {
    for (const item of list) {
      const cells = others.map((other) => {
        const [gradeCode, difficultyCode] = name === "GRADE" ? [item.code, other.code] : [other.code, item.code];
        return { gradeCode, difficultyCode, count: capacity(gradeCode, difficultyCode) };
      });
      const available = cells.reduce((sum, cell) => sum + cell.count, 0);
      if (available >= item.required) continue;
      const nonEmpty = cells.filter((cell) => cell.count > 0);
      if (nonEmpty.length === 1) {
        issues.push(
          makeIssue({ ...base, code: "CELL_SHORTAGE", ...nonEmpty[0]!, required: item.required, available }),
        );
      } else {
        issues.push(
          makeIssue({
            ...base,
            code: "DIMENSION_SHORTAGE",
            dimension: name,
            gradeCode: name === "GRADE" ? item.code : null,
            difficultyCode: name === "DIFFICULTY" ? item.code : null,
            required: item.required,
            available,
          }),
        );
      }
    }
  };
  dimension(requirement.byGrade, requirement.byDifficulty, "GRADE");
  dimension(requirement.byDifficulty, requirement.byGrade, "DIFFICULTY");
  if (issues.length === 0) {
    const required = requirement.byGrade.reduce((sum, item) => sum + item.required, 0);
    issues.push(makeIssue({ ...base, code: "MATRIX_INFEASIBLE", required }));
  }
  return issues;
}

export function checkBlock(
  block: QuestionBlock,
  context: BlockContext,
  availability: BlockAvailability | undefined,
  timeLimitSeconds: number,
): BlockCheck {
  const { part, section, optionId } = context;
  const count = context.count ?? block.questionCount ?? 0;
  const base: IssueInput = {
    code: "CELL_SHORTAGE",
    partId: part.id,
    partName: part.name,
    sectionId: section.id,
    subjectCode: section.subjectCode,
    blockId: block.id,
    blockName: block.name,
    optionId,
  };
  const issues: CheckIssue[] = [];
  const isSet = isSetBlock(block);

  if (
    isSet &&
    block.questionCount !== null &&
    (block.setCount === null || block.setSize === null || block.setCount * block.setSize !== block.questionCount)
  ) {
    issues.push(
      makeIssue({
        ...base,
        code: "SET_SIZE_MISMATCH",
        required: block.questionCount,
        available: (block.setCount ?? 0) * (block.setSize ?? 0),
      }),
    );
  }

  const { requirement, issues: matrixIssues } = resolveBlockRequirement(block, count);
  for (const issue of matrixIssues) issues.push(makeIssue({ ...base, ...issue }));

  const capacity = availabilityLookup(availability);
  const available = (availability?.cells ?? []).reduce((total, cell) => total + cell.count, 0);

  if (!block.matrix) {
    // Không có chỉ tiêu riêng: nhóm chỉ cần đủ số câu nguồn; chỉ tiêu của môn do kiểm tra cấp môn báo.
    if (!isSet && available < count) issues.push(makeIssue({ ...base, code: "CELL_SHORTAGE", required: count, available }));
  } else if (requirement?.mode === "COMBINED") {
    for (const cell of requirement.cells) {
      const have = capacity(cell.gradeCode, cell.difficultyCode);
      if (cell.required > have) {
        issues.push(
          makeIssue({
            ...base,
            code: "CELL_SHORTAGE",
            gradeCode: cell.gradeCode,
            difficultyCode: cell.difficultyCode,
            required: cell.required,
            available: have,
          }),
        );
      }
    }
  } else if (requirement?.mode === "SEPARATE" && !allocateRequirement(requirement, capacity)) {
    issues.push(...diagnoseSeparate(requirement, capacity, base));
  }

  // Chiều năng lực: mỗi năng lực cần đủ câu nguồn (kiểm riêng, chưa ghép đồng thời với Lớp × Độ khó).
  for (const item of requirement?.byCompetency ?? []) {
    const have = availability?.competencies?.find((entry) => entry.code === item.code)?.count ?? 0;
    if (item.required > have) {
      issues.push(
        makeIssue({
          ...base,
          code: "COMPETENCY_SHORTAGE",
          dimension: "COMPETENCY",
          competencyCode: item.code,
          required: item.required,
          available: have,
        }),
      );
    }
  }

  // Tự luận chấm tay theo tiêu chí: câu nguồn thiếu tiêu chí chấm thì chưa công bố được.
  if (availability?.missingRubric?.length) {
    issues.push(
      makeIssue({
        ...base,
        code: "RUBRIC_MISSING",
        available: availability.missingRubric.length,
        questionCodes: availability.missingRubric,
      }),
    );
  }

  const availableSets = isSet ? (availability?.setCount ?? 0) : null;
  if (isSet && block.setCount !== null && availableSets !== null && availableSets < block.setCount) {
    issues.push(makeIssue({ ...base, code: "SET_SHORTAGE", required: block.setCount, available: availableSets }));
  }

  const overlap = availability?.overlap;
  if (issues.length === 0 && overlap && overlap.solveSeconds > timeLimitSeconds) {
    issues.push(
      makeIssue({ ...base, code: "OVERLAP_UNCONFIRMED", timeLimitSeconds, available: overlap.sharedCount }),
    );
  }

  // Quy tắc chấm nằm trên từng câu: câu nguồn khác quy tắc mong đợi của khối chỉ cảnh báo, không chặn sinh đề.
  if (availability?.scoringMismatch?.length) {
    issues.push(
      makeIssue({
        ...base,
        code: "SCORING_MISMATCH",
        available: availability.scoringMismatch.length,
        questionCodes: availability.scoringMismatch,
      }),
    );
  }

  const hasError = issues.some((issue) => issue.severity === "ERROR");
  const hasUnconfirmed = issues.some((issue) => issue.severity === "UNCONFIRMED");
  return {
    partId: part.id,
    partName: part.name,
    partOrder: part.order,
    sectionId: section.id,
    subjectCode: section.subjectCode,
    optionId,
    blockId: block.id,
    blockName: block.name,
    questionType: block.questionType,
    required: count,
    setCount: block.setCount,
    setSize: block.setSize,
    available,
    availableSets,
    requirement,
    result: hasError ? "NOT_FOUND" : hasUnconfirmed ? "UNCONFIRMED" : "FOUND",
    issues,
  };
}

/* ---------- Môn: chỉ tiêu cấp môn, nhóm lấy phần còn lại, SUBJECT_CHOICE (B9, D25) ---------- */

/**
 * Số câu từng nhóm khi môn nhận `count` câu: nhóm câu đơn `questionCount = null` ("lấy phần còn lại") nhận phần chênh
 * giữa số câu của môn và tổng nhóm cố định; môn có hơn một nhóm như vậy không chia được (báo `REMAINDER_GROUP_INVALID`).
 */
export function groupCounts(section: ExamSection, count: number): number[] {
  const remainderGroups = section.blocks.filter((block) => block.questionCount === null);
  const remainder = remainderGroups.length === 1 ? Math.max(0, count - sum(section.blocks.map(countOf))) : 0;
  return section.blocks.map((block) => block.questionCount ?? remainder);
}

/** Một cách chọn môn của `SUBJECT_CHOICE`: các môn xếp theo thứ tự trong cấu trúc, môn thứ i nhận `countsByPick[i]` (D25). */
export interface SubjectPick {
  sectionIds: string[];
  subjectCodes: string[];
  counts: number[];
}

/** Tối đa 8 môn trong một `SUBJECT_CHOICE` (spec 5.2). */
export const SUBJECT_CHOICE_MAX_SECTIONS = 8;

export function enumerateSubjectPicks(option: ExamPartOption): SubjectPick[] {
  const pick = option.chooseSubjects ?? 0;
  const { sections } = option;
  if (pick <= 0 || pick > sections.length || sections.length > SUBJECT_CHOICE_MAX_SECTIONS) return [];
  const picks: SubjectPick[] = [];
  const walk = (start: number, chosen: number[]) => {
    if (chosen.length === pick) {
      picks.push({
        sectionIds: chosen.map((index) => sections[index]!.id),
        subjectCodes: chosen.map((index) => sections[index]!.subjectCode),
        counts: chosen.map((_, position) => option.countsByPick?.[position] ?? 0),
      });
      return;
    }
    for (let index = start; index < sections.length; index += 1) walk(index + 1, [...chosen, index]);
  };
  walk(0, []);
  return picks;
}

/** Các số câu một môn có thể nhận qua mọi cách chọn môn, theo thứ tự xuất hiện. */
function possibleCountsBySection(picks: readonly SubjectPick[]): Map<string, number[]> {
  const result = new Map<string, number[]>();
  for (const pick of picks) {
    pick.sectionIds.forEach((sectionId, index) => {
      const counts = result.get(sectionId) ?? [];
      if (!counts.includes(pick.counts[index]!)) counts.push(pick.counts[index]!);
      result.set(sectionId, counts);
    });
  }
  return result;
}

/** `countsByPick` sai số phần tử hoặc sai tổng (tổng bằng số câu của phương án), hoặc số môn chọn không hợp lệ. */
function subjectChoiceCountsIssue(
  option: ExamPartOption,
  target: number,
  context: { part: ExamPart; optionId: string | null },
): CheckIssue | null {
  const base: IssueInput = { code: "SUBJECT_CHOICE_COUNTS_INVALID", partId: context.part.id, partName: context.part.name, optionId: context.optionId };
  const pick = option.chooseSubjects ?? 0;
  if (pick < 1 || pick > option.sections.length || option.sections.length > SUBJECT_CHOICE_MAX_SECTIONS) {
    return makeIssue({ ...base, required: pick, available: option.sections.length });
  }
  const counts = option.countsByPick ?? [];
  if (counts.length !== pick) return makeIssue({ ...base, required: pick, available: counts.length });
  const total = sum(counts);
  if (counts.some((value) => !Number.isInteger(value) || value < 0) || total !== target) {
    return makeIssue({ ...base, required: target, available: total });
  }
  return null;
}

interface SectionContext {
  part: ExamPart;
  optionId: string | null;
  /** Số câu của môn trong trường hợp đang kiểm. */
  count: number;
  /** Mọi số câu môn có thể nhận (`SUBJECT_CHOICE`: 16 hoặc 17); phần cố định chỉ có một giá trị. */
  possibleCounts: readonly number[];
}

/** Số câu theo từng ô và theo chiều Lớp, Độ khó của môn từ nguồn. */
function sectionDimensionCapacity(availability: SectionAvailability | undefined) {
  const byGrade = new Map<string, number>();
  const byDifficulty = new Map<string, number>();
  for (const cell of availability?.cells ?? []) {
    byGrade.set(cell.gradeCode, (byGrade.get(cell.gradeCode) ?? 0) + cell.count);
    byDifficulty.set(cell.difficultyCode, (byDifficulty.get(cell.difficultyCode) ?? 0) + cell.count);
  }
  return { byGrade, byDifficulty };
}

/**
 * Kiểm tra cấp môn (B9): tổng nhóm và nhóm lấy phần còn lại, chỉ tiêu dạng trả lời, khối lớp, độ khó của môn và nguồn
 * của từng chỉ tiêu. Chỉ tiêu theo `PERCENT` quy ra số câu bằng cùng hàm `largestRemainder` với nhóm; theo số câu chỉ
 * hợp lệ khi môn luôn nhận cùng một số câu.
 */
function checkSection(
  section: ExamSection,
  context: SectionContext,
  availability: SectionAvailability | undefined,
): { issues: CheckIssue[]; subject: SubjectCheck } {
  const { part, optionId, count, possibleCounts } = context;
  const base: IssueInput = {
    code: "SECTION_TOTAL_MISMATCH",
    partId: part.id,
    partName: part.name,
    sectionId: section.id,
    subjectCode: section.subjectCode,
    optionId,
    subjectCount: possibleCounts.length > 1 ? count : null,
  };
  const issues: CheckIssue[] = [];
  const varies = new Set(possibleCounts).size > 1;
  const remainderGroups = section.blocks.filter((block) => block.questionCount === null);
  const fixed = sum(section.blocks.map(countOf));

  const remainderInvalid =
    remainderGroups.length > 1 ||
    remainderGroups.some((block) => isSetBlock(block)) ||
    (remainderGroups.length === 1 && fixed > Math.min(...possibleCounts)) ||
    (varies && remainderGroups.length === 0);
  if (remainderInvalid) {
    issues.push(makeIssue({ ...base, code: "REMAINDER_GROUP_INVALID", required: Math.min(...possibleCounts), available: fixed }));
  } else if (remainderGroups.length === 0 && fixed !== count) {
    issues.push(makeIssue({ ...base, code: "SECTION_TOTAL_MISMATCH", required: count, available: fixed }));
  }

  if (!section.answerQuotas) {
    for (const block of section.blocks) {
      if (block.questionType === null && !isSetBlock(block)) {
        issues.push(makeIssue({ ...base, code: "GROUP_ANSWER_TYPE_MISSING", blockId: block.id, blockName: block.name }));
      }
    }
  }

  const quotaCountVaries =
    varies && (section.answerQuotas?.unit === "COUNT" || (section.matrix !== null && section.matrix.unit === "COUNT"));
  if (quotaCountVaries) {
    issues.push(makeIssue({ ...base, code: "SUBJECT_QUOTA_COUNT_VARIES", required: Math.min(...possibleCounts), available: Math.max(...possibleCounts) }));
  }

  const capacity = availabilityLookup(availability);
  const dimensions = sectionDimensionCapacity(availability);
  const subject: SubjectCheck = {
    sectionId: section.id,
    subjectCode: section.subjectCode,
    optionId,
    questionCount: section.questionCount,
    answerQuotas: [],
    byGrade: [],
    byDifficulty: [],
  };

  if (section.answerQuotas) {
    const { items, unit } = section.answerQuotas;
    const resolved = resolveValues(count, unit, items.map((item) => item.value), null);
    for (const issue of resolved.issues) {
      if (issue.code === "MATRIX_TOTAL_MISMATCH" && quotaCountVaries) continue;
      issues.push(
        makeIssue({ ...base, ...issue, code: issue.code === "MATRIX_TOTAL_MISMATCH" ? "ANSWER_QUOTA_TOTAL_MISMATCH" : issue.code }),
      );
    }
    resolved.counts?.forEach((required, index) => {
      const answerType = items[index]!.answerType;
      const have = availability?.byAnswerType.find((entry) => entry.answerType === answerType)?.count ?? 0;
      subject.answerQuotas.push({ answerType, required, achieved: Math.min(required, have) });
      if (have < required) {
        issues.push(makeIssue({ ...base, code: "ANSWER_TYPE_SHORTAGE", answerType, required, available: have }));
      }
    });
  }

  if (section.matrix) {
    const { requirement, issues: matrixIssues } = resolveMatrixRequirement(section.matrix, count);
    for (const issue of matrixIssues) {
      if (issue.code === "MATRIX_TOTAL_MISMATCH" && quotaCountVaries) continue;
      issues.push(
        makeIssue({ ...base, ...issue, code: issue.code === "MATRIX_TOTAL_MISMATCH" ? "SUBJECT_MATRIX_TOTAL_MISMATCH" : issue.code }),
      );
    }
    if (requirement) {
      const requiredByGrade = new Map<string, number>();
      const requiredByDifficulty = new Map<string, number>();
      if (requirement.mode === "COMBINED") {
        for (const cell of requirement.cells) {
          requiredByGrade.set(cell.gradeCode, (requiredByGrade.get(cell.gradeCode) ?? 0) + cell.required);
          requiredByDifficulty.set(cell.difficultyCode, (requiredByDifficulty.get(cell.difficultyCode) ?? 0) + cell.required);
          if (cell.required > capacity(cell.gradeCode, cell.difficultyCode)) {
            issues.push(
              makeIssue({
                ...base,
                code: "SUBJECT_QUOTA_INFEASIBLE",
                gradeCode: cell.gradeCode,
                difficultyCode: cell.difficultyCode,
                required: cell.required,
                available: capacity(cell.gradeCode, cell.difficultyCode),
              }),
            );
          }
        }
      } else {
        for (const item of requirement.byGrade) requiredByGrade.set(item.code, item.required);
        for (const item of requirement.byDifficulty) requiredByDifficulty.set(item.code, item.required);
        if (!allocateRequirement(requirement, capacity)) {
          // Nêu chiều hoặc ô thiếu nguồn như nhóm; mã là SUBJECT_QUOTA_INFEASIBLE (chỉ tiêu cấp môn, kèm số câu của môn).
          for (const shortage of diagnoseSeparate(requirement, capacity, base)) {
            issues.push(makeIssue({ ...shortage, code: "SUBJECT_QUOTA_INFEASIBLE" }));
          }
        }
      }
      for (const [code, required] of requiredByGrade) {
        subject.byGrade.push({ code, required, achieved: Math.min(required, dimensions.byGrade.get(code) ?? 0) });
      }
      for (const [code, required] of requiredByDifficulty) {
        subject.byDifficulty.push({ code, required, achieved: Math.min(required, dimensions.byDifficulty.get(code) ?? 0) });
      }
    }
  }
  return { issues, subject };
}

function dedupe(issues: readonly CheckIssue[]): CheckIssue[] {
  const seen = new Map<string, CheckIssue>();
  for (const issue of issues) if (!seen.has(issue.key)) seen.set(issue.key, issue);
  return [...seen.values()];
}

/** Kết quả của đường: có lỗi là Không hợp lệ; có vấn đề chưa xác nhận được là Chưa xác nhận; cảnh báo không tính. */
export function pathResultOf(issues: readonly CheckIssue[]): PathCheck["result"] {
  if (issues.some((issue) => issue.severity === "ERROR")) return "INVALID";
  if (issues.some((issue) => issue.severity === "UNCONFIRMED")) return "UNCONFIRMED";
  return "VALID";
}

/** Năm cấu trúc khác năm ma trận (SP2E 2026 dùng ma trận 2025) mà chuyên gia chưa xác nhận chỉ tiêu. */
export function yearsNeedConfirmation(exam: Pick<ExamDetail, "structureYear" | "matrixYear" | "yearsConfirmed">): boolean {
  return (
    exam.structureYear !== null && exam.matrixYear !== null && exam.structureYear !== exam.matrixYear && !exam.yearsConfirmed
  );
}

const POINTS_TOLERANCE = 1e-6;

function roundPoints(value: number): number {
  return Math.round(value * 1e6) / 1e6;
}

function pointsOf(outcomes: readonly SectionOutcome[]): number {
  return roundPoints(outcomes.reduce((total, outcome) => total + outcome.points, 0));
}

export interface CheckExamOptions {
  timeLimitSeconds?: number;
  nameOf?: ChoiceNameResolver;
}

/** Kết quả kiểm tra một môn khi nhận `count` câu: các nhóm, vấn đề cấp môn và nhóm, chỉ tiêu cấp môn. */
interface SectionOutcome {
  blocks: BlockCheck[];
  issues: CheckIssue[];
  subject: SubjectCheck;
  questions: number;
  points: number;
}

/**
 * Kiểm tra mọi đường lựa chọn của bài (không chỉ đường đầu tiên, AC-04.10): cấu trúc, ma trận, nguồn theo từng ô,
 * bộ câu hỏi, nguồn giao nhau. Đủ nguồn từng dòng không tự coi là hợp lệ: ma trận riêng phải tìm được phân bổ thỏa
 * cả hai chiều (AC-04.9). Theo B9: chỉ tiêu cấp môn trải trên mọi nhóm của môn, phương án `SUBJECT_CHOICE` được kiểm
 * cho mọi cách chọn môn (`PathCheck.selections`).
 */
export function checkExam(exam: ExamDetail, availability: ExamAvailability, options: CheckExamOptions = {}): ExamCheckOutcome {
  const timeLimitSeconds = options.timeLimitSeconds ?? CHECK_TIME_LIMIT_SECONDS;
  const parts = sortedParts(exam.parts);
  const examIssues: CheckIssue[] = [];
  if (!exam.name.trim() || !exam.code.trim() || (exam.scope === "PART" && !exam.partCode)) {
    examIssues.push(makeIssue({ code: "INFO_MISSING" }));
  }
  if (yearsNeedConfirmation(exam)) {
    examIssues.push(
      makeIssue({ code: "YEARS_UNCONFIRMED", required: exam.structureYear ?? 0, available: exam.matrixYear ?? 0 }),
    );
  }
  for (const part of parts) {
    if (part.mode === "CHOICE" && part.options.length === 0) {
      examIssues.push(makeIssue({ code: "OPTION_EMPTY", partId: part.id, partName: part.name }));
    }
  }

  const blockCache = new Map<string, BlockCheck>();
  const sectionOutcome = (
    part: ExamPart,
    section: ExamSection,
    optionId: string | null,
    count: number,
    possibleCounts: readonly number[],
  ): SectionOutcome => {
    const perGroup = groupCounts(section, count);
    const blocks = section.blocks.map((block, index) => {
      const key = `${block.id}:${perGroup[index]}`;
      let checked = blockCache.get(key);
      if (!checked) {
        checked = checkBlock(
          block,
          { part, section, optionId, count: perGroup[index] },
          availability.blocks[block.id],
          timeLimitSeconds,
        );
        blockCache.set(key, checked);
      }
      return checked;
    });
    const sectionChecked = checkSection(section, { part, optionId, count, possibleCounts }, availability.sections?.[section.id]);
    return {
      blocks,
      issues: [...sectionChecked.issues, ...blocks.flatMap((block) => block.issues)],
      subject: sectionChecked.subject,
      questions: sum(perGroup),
      points: roundPoints(section.blocks.reduce((total, block, index) => total + perGroup[index]! * blockPointsEach(block), 0)),
    };
  };

  const paths: PathCheck[] = enumeratePaths(parts, options.nameOf).map((path) => {
    const issues: CheckIssue[] = [...examIssues];
    const blocks: BlockCheck[] = [];
    const subjects: SubjectCheck[] = [];
    const selections: PathSelectionCheck[] = [];
    let totalQuestions = 0;
    let totalMinutes = 0;
    let totalPoints = 0;
    for (const choice of path.choices) {
      const part = parts.find((item) => item.id === choice.partId);
      const option = part?.options.find((item) => item.id === choice.optionId);
      // Số câu từng môn của lựa chọn chờ chuyên gia xác nhận (HSA chia 17–17–16): chưa công bố được đường này.
      if (part && option?.expertConfirmed === false) {
        issues.push(makeIssue({ code: "OPTION_UNCONFIRMED", partId: part.id, partName: part.name, optionId: option.id }));
      }
    }
    for (const group of pathSections(parts, path)) {
      const { part, optionId, sections } = group;
      const subjectChoice = part.options.find((item) => item.id === optionId && item.kind === "SUBJECT_CHOICE") ?? null;
      // Môn của `SUBJECT_CHOICE` không có số câu cố định: kiểm theo từng cách chọn bên dưới.
      const fixedSections = subjectChoice ? part.sections : sections;
      totalMinutes += part.durationMinutes ?? 0;
      const outcomes: SectionOutcome[] = [];
      for (const section of fixedSections) {
        const count = section.questionCount ?? sum(section.blocks.map(countOf));
        const outcome = sectionOutcome(part, section, sectionOptionId(group, section), count, [count]);
        outcomes.push(outcome);
        blocks.push(...outcome.blocks);
        subjects.push(outcome.subject);
        issues.push(...outcome.issues);
        totalQuestions += outcome.questions;
      }
      let sectionSum = sum(fixedSections.map(countOf));
      if (subjectChoice) {
        const target = part.questionCount - sectionSum;
        const invalid = subjectChoiceCountsIssue(subjectChoice, target, { part, optionId });
        if (invalid) {
          issues.push(invalid);
        } else {
          const picks = enumerateSubjectPicks(subjectChoice);
          const possible = possibleCountsBySection(picks);
          for (const pick of picks) {
            const pickIssues: CheckIssue[] = [];
            pick.sectionIds.forEach((sectionId, index) => {
              const section = subjectChoice.sections.find((item) => item.id === sectionId)!;
              const outcome = sectionOutcome(part, section, optionId, pick.counts[index]!, possible.get(sectionId) ?? []);
              pickIssues.push(...outcome.issues);
            });
            issues.push(...pickIssues);
            selections.push({
              subjectCodes: pick.subjectCodes,
              counts: pick.counts,
              result: pathResultOf(pickIssues),
              issues: pickIssues,
            });
          }
          // Mỗi môn hiện một lần ở số câu đầu tiên môn có thể nhận (bảng nhóm và chỉ tiêu của màn Kiểm tra).
          const shown: SectionOutcome[] = [];
          for (const section of subjectChoice.sections) {
            const first = possible.get(section.id)?.[0];
            if (first === undefined) continue;
            const outcome = sectionOutcome(part, section, optionId, first, possible.get(section.id) ?? []);
            shown.push(outcome);
            blocks.push(...outcome.blocks);
            subjects.push(outcome.subject);
          }
          const firstPick = picks[0];
          if (firstPick) {
            const pickOutcomes = firstPick.sectionIds.map((sectionId) => {
              const index = subjectChoice.sections.findIndex((item) => item.id === sectionId);
              return shown[index]!;
            });
            outcomes.push(...pickOutcomes);
          }
        }
        totalQuestions += sum(subjectChoice.countsByPick ?? []);
        sectionSum += sum(subjectChoice.countsByPick ?? []);
      }
      if (part.maxPoints !== null && part.maxPoints !== undefined) {
        const points = pointsOf(outcomes);
        if (Math.abs(points - part.maxPoints) > POINTS_TOLERANCE) {
          issues.push(
            makeIssue({
              code: "PART_POINTS_MISMATCH",
              partId: part.id,
              partName: part.name,
              optionId,
              required: part.maxPoints,
              available: points,
            }),
          );
        }
      }
      totalPoints += pointsOf(outcomes);
      if (sectionSum !== part.questionCount) {
        issues.push(
          makeIssue({
            code: "PART_TOTAL_MISMATCH",
            partId: part.id,
            partName: part.name,
            optionId,
            required: part.questionCount,
            available: sectionSum,
          }),
        );
      }
    }
    if (totalQuestions !== exam.totalQuestions) {
      issues.push(makeIssue({ code: "PATH_TOTAL_MISMATCH", required: exam.totalQuestions, available: totalQuestions }));
    }
    totalPoints = roundPoints(totalPoints);
    // Kỳ thi có thang điểm (BCA 100, THPTQG 10): tổng điểm mỗi đường phải bằng thang.
    if (exam.maxScore !== null && Math.abs(totalPoints - exam.maxScore) > POINTS_TOLERANCE) {
      issues.push(makeIssue({ code: "POINTS_TOTAL_MISMATCH", required: exam.maxScore, available: totalPoints }));
    }
    return {
      ...path,
      result: pathResultOf(issues),
      partIds: parts.map((part) => part.id),
      totalQuestions,
      totalMinutes: exam.timing === "SHARED" ? exam.durationMinutes : totalMinutes,
      totalPoints,
      blocks,
      subjects,
      selections,
      issues: dedupe(issues),
    };
  });

  const allIssues = dedupe([...examIssues, ...paths.flatMap((path) => path.issues)]);
  const groups: CheckGroupResult[] = CHECK_GROUPS.map((group) => {
    const issues = allIssues.filter((issue) => issue.group === group);
    const status = issues.some((issue) => issue.severity === "ERROR") ? "FAIL" : issues.length ? "WARN" : "PASS";
    return { group, status, issues };
  });
  const blocking = allIssues.some((issue) => issue.severity !== "WARNING");

  return {
    status: !blocking && paths.length > 0 ? "VALID" : "INVALID",
    issueCount: allIssues.length,
    paths,
    groups,
    partCount: parts.length,
    totalPoints: paths[0]?.totalPoints ?? 0,
    printedQuestions: parts.reduce((total, part) => total + printedPartQuestions(part), 0),
    maxScore: exam.maxScore,
  };
}
