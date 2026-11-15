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
} from "./review-types";
import { apportionByPercent } from "./matrix";
import { CHECK_GROUPS } from "./review-types";
import type { BlockMatrix, ExamDetail, ExamPart, ExamSection, MatrixDimensionValue, QuestionBlock } from "./types";

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
        { partId: part.id, optionId: option.id, kind: option.kind, code: option.code, name: nameOf(option.kind, option.code) },
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

/** Các phần của một đường, mỗi phần kèm môn mà đường đó làm (phần cố định: mọi môn; phần tự chọn: môn của lựa chọn). */
export function pathSections(parts: readonly ExamPart[], path: ExamPathRef): PathSectionGroup[] {
  return sortedParts(parts).map((part) => {
    if (part.mode !== "CHOICE") return { part, optionId: null, sections: part.sections };
    const choice = path.choices.find((item) => item.partId === part.id);
    const option = part.options.find((item) => item.id === choice?.optionId);
    return { part, optionId: option?.id ?? null, sections: option?.sections ?? [] };
  });
}

/* ---------- Chỉ tiêu của khối ---------- */

interface MatrixIssue {
  code: Extract<CheckIssueCode, "MATRIX_NEGATIVE" | "PERCENT_TOTAL_INVALID" | "MATRIX_TOTAL_MISMATCH">;
  dimension: "GRADE" | "DIFFICULTY" | null;
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

/** Quy đổi ma trận của khối thành số câu nguyên; ma trận sai thì `requirement` là `null` kèm vấn đề. */
export function resolveBlockRequirement(block: QuestionBlock): {
  requirement: BlockRequirement | null;
  issues: MatrixIssue[];
} {
  const { matrix, questionCount } = block;
  if (matrix.mode === "COMBINED") {
    const resolved = resolveValues(questionCount, matrix.unit, matrix.cells.map((cell) => cell.value), null);
    if (!resolved.counts) return { requirement: null, issues: resolved.issues };
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
      },
      issues: [],
    };
  }
  const dimension = (values: MatrixDimensionValue[], name: "GRADE" | "DIFFICULTY") => {
    const resolved = resolveValues(questionCount, matrix.unit, values.map((item) => item.value), name);
    return {
      list: resolved.counts?.map((required, index) => ({ code: values[index]!.code, required })) ?? null,
      issues: resolved.issues,
    };
  };
  const byGrade = dimension(matrix.byGrade, "GRADE");
  const byDifficulty = dimension(matrix.byDifficulty, "DIFFICULTY");
  const issues = [...byGrade.issues, ...byDifficulty.issues];
  if (!byGrade.list || !byDifficulty.list) return { requirement: null, issues };
  return {
    requirement: { mode: "SEPARATE", cells: [], byGrade: byGrade.list, byDifficulty: byDifficulty.list },
    issues,
  };
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

export function availabilityLookup(availability: BlockAvailability | undefined) {
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
};

type IssueInput = Partial<Omit<CheckIssue, "key" | "code" | "severity" | "group">> & { code: CheckIssueCode };

function makeIssue(input: IssueInput): CheckIssue {
  const issue: Omit<CheckIssue, "key"> = {
    code: input.code,
    severity: input.code === "OVERLAP_UNCONFIRMED" ? "UNCONFIRMED" : "ERROR",
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
    required: input.required ?? null,
    available: input.available ?? null,
    timeLimitSeconds: input.timeLimitSeconds ?? null,
  };
  const key = [
    issue.code,
    issue.blockId ?? issue.sectionId ?? issue.partId ?? "exam",
    issue.optionId ?? "",
    issue.gradeCode ?? "",
    issue.difficultyCode ?? "",
    issue.dimension ?? "",
  ].join("|");
  return { key, ...issue };
}

interface BlockContext {
  part: ExamPart;
  section: ExamSection;
  optionId: string | null;
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
  const isSet = block.questionType === "QUESTION_SET";

  if (isSet && (block.setCount === null || block.setSize === null || block.setCount * block.setSize !== block.questionCount)) {
    issues.push(
      makeIssue({
        ...base,
        code: "SET_SIZE_MISMATCH",
        required: block.questionCount,
        available: (block.setCount ?? 0) * (block.setSize ?? 0),
      }),
    );
  }

  const { requirement, issues: matrixIssues } = resolveBlockRequirement(block);
  for (const issue of matrixIssues) issues.push(makeIssue({ ...base, ...issue }));

  const capacity = availabilityLookup(availability);
  const available = (availability?.cells ?? []).reduce((sum, cell) => sum + cell.count, 0);

  if (requirement?.mode === "COMBINED") {
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

  const hasError = issues.some((issue) => issue.severity === "ERROR");
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
    required: block.questionCount,
    setCount: block.setCount,
    setSize: block.setSize,
    available,
    availableSets,
    requirement,
    result: hasError ? "NOT_FOUND" : issues.length ? "UNCONFIRMED" : "FOUND",
    issues,
  };
}

function dedupe(issues: readonly CheckIssue[]): CheckIssue[] {
  const seen = new Map<string, CheckIssue>();
  for (const issue of issues) if (!seen.has(issue.key)) seen.set(issue.key, issue);
  return [...seen.values()];
}

export function pathResultOf(issues: readonly CheckIssue[]): PathCheck["result"] {
  if (issues.some((issue) => issue.severity === "ERROR")) return "INVALID";
  if (issues.length) return "UNCONFIRMED";
  return "VALID";
}

export interface CheckExamOptions {
  timeLimitSeconds?: number;
  nameOf?: ChoiceNameResolver;
}

/**
 * Kiểm tra mọi đường lựa chọn của bài (không chỉ đường đầu tiên, AC-04.10): cấu trúc, ma trận, nguồn theo từng ô,
 * bộ câu hỏi, nguồn giao nhau. Đủ nguồn từng dòng không tự coi là hợp lệ: ma trận riêng phải tìm được phân bổ thỏa
 * cả hai chiều (AC-04.9).
 */
export function checkExam(exam: ExamDetail, availability: ExamAvailability, options: CheckExamOptions = {}): ExamCheckOutcome {
  const timeLimitSeconds = options.timeLimitSeconds ?? CHECK_TIME_LIMIT_SECONDS;
  const parts = sortedParts(exam.parts);
  const examIssues: CheckIssue[] = [];
  if (!exam.name.trim() || !exam.code.trim() || (exam.format === "SINGLE" && !exam.singleSubjectCode)) {
    examIssues.push(makeIssue({ code: "INFO_MISSING" }));
  }
  for (const part of parts) {
    if (part.mode === "CHOICE" && part.options.length === 0) {
      examIssues.push(makeIssue({ code: "OPTION_EMPTY", partId: part.id, partName: part.name }));
    }
  }

  const blockCache = new Map<string, BlockCheck>();
  const paths: PathCheck[] = enumeratePaths(parts, options.nameOf).map((path) => {
    const issues: CheckIssue[] = [...examIssues];
    const blocks: BlockCheck[] = [];
    let totalQuestions = 0;
    let totalMinutes = 0;
    let totalPoints = 0;
    for (const group of pathSections(parts, path)) {
      const { part, optionId, sections } = group;
      totalMinutes += part.durationMinutes;
      const sectionSum = sections.reduce((sum, section) => sum + section.questionCount, 0);
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
      for (const section of sections) {
        const blockSum = section.blocks.reduce((sum, block) => sum + block.questionCount, 0);
        if (blockSum !== section.questionCount) {
          issues.push(
            makeIssue({
              code: "SECTION_TOTAL_MISMATCH",
              partId: part.id,
              partName: part.name,
              sectionId: section.id,
              subjectCode: section.subjectCode,
              optionId,
              required: section.questionCount,
              available: blockSum,
            }),
          );
        }
        for (const block of section.blocks) {
          let checked = blockCache.get(block.id);
          if (!checked) {
            checked = checkBlock(block, { part, section, optionId }, availability.blocks[block.id], timeLimitSeconds);
            blockCache.set(block.id, checked);
          }
          blocks.push(checked);
          issues.push(...checked.issues);
          totalQuestions += block.questionCount;
          totalPoints += block.questionCount * block.pointsPerQuestion;
        }
      }
    }
    if (totalQuestions !== exam.totalQuestions) {
      issues.push(makeIssue({ code: "PATH_TOTAL_MISMATCH", required: exam.totalQuestions, available: totalQuestions }));
    }
    return {
      ...path,
      result: pathResultOf(issues),
      partIds: parts.map((part) => part.id),
      totalQuestions,
      totalMinutes,
      totalPoints,
      blocks,
      issues,
    };
  });

  const allIssues = dedupe([...examIssues, ...paths.flatMap((path) => path.issues)]);
  const groups: CheckGroupResult[] = CHECK_GROUPS.map((group) => {
    const issues = allIssues.filter((issue) => issue.group === group);
    const status = issues.some((issue) => issue.severity === "ERROR") ? "FAIL" : issues.length ? "WARN" : "PASS";
    return { group, status, issues };
  });

  return {
    status: allIssues.length === 0 && paths.length > 0 ? "VALID" : "INVALID",
    issueCount: allIssues.length,
    paths,
    groups,
    partCount: parts.length,
    totalPoints: paths[0]?.totalPoints ?? 0,
  };
}
