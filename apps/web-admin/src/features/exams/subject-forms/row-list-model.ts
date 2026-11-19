import type { QuestionType } from "@/features/content/types";
import { applyBlockPatch, blockCountsLocked } from "../components/block-form";
import { isFlexibleSet, isSetBlock, type MatrixAxes, sum } from "../matrix";
import { isTemplateOmittedGroup } from "../omitted-part";
import type { BlockMatrix, ExamScope, ExamSection, ExamTemplate, QuestionBlock } from "../types";
import type { LockReasonCode } from "./lock-reason";
import type { SubjectFormEntry } from "./registry";

/**
 * Mô hình thuần của Danh sách dòng lấy câu (DS, FTM R-7, R-8, R-11, plan B1): đọc và ghi nhóm của môn theo `id`, năm loại khóa của
 * R-7 tách riêng (mỗi loại một lý do), ẩn lớp mà giữ dữ liệu lớp, chiều ma trận trống ghi mảng rỗng. Không phụ thuộc giao diện nên
 * test riêng; màn (`row-list-form.tsx`, `row-editor.tsx`, `reference-block.tsx`) chỉ vẽ kết quả.
 */

/** Chỗ cắm của bài đang soạn mà khóa của dòng phụ thuộc: phạm vi, bài ôn, mẫu. */
export interface RowContext {
  scope: ExamScope;
  reviewMode: boolean;
  template: Pick<ExamTemplate, "fixedBlockCounts" | "omittedLocked" | "parts"> | null;
}

/**
 * Khóa của một dòng (R-7 bảng "Khóa"). Khóa tổng (`fixed_counts`) thuộc đầu môn, không thuộc dòng nên không có ở đây; nhóm
 * tự luận lược của SP2E, SPT không khóa gì cả.
 */
export interface RowLocks {
  /** Khóa dòng (H42): Cách lấy, Số câu, Số chùm, Số câu con chỉ đọc. */
  counts: boolean;
  /** Khóa dạng (`answerTypeLocked`): Cách lấy chỉ đọc. */
  mode: boolean;
  /** Khóa nhóm đã lược (H43, H49): dòng "Viết – tạm bỏ qua" không có công tắc bật lại. */
  omitted: boolean;
}

export function rowsLockedOf(context: RowContext): boolean {
  return blockCountsLocked(context.template, context.scope, context.reviewMode);
}

export function rowLocks(block: QuestionBlock, context: RowContext): RowLocks {
  const omitted = context.template?.omittedLocked === true && block.omitted === true && isTemplateOmittedGroup(context.template as ExamTemplate, block);
  return { counts: rowsLockedOf(context), mode: block.answerTypeLocked, omitted };
}

/** Lý do chữ của các khóa đang áp lên dòng, theo thứ tự hiện (FTM R-15: khóa nào cũng có lý do). */
export function lockReasonsOf(locks: RowLocks): LockReasonCode[] {
  const reasons: LockReasonCode[] = [];
  if (locks.counts) reasons.push("rowCounts");
  if (locks.mode) reasons.push("answerType");
  if (locks.omitted) reasons.push("omitted");
  return reasons;
}

/** Cách lấy (Câu đơn / Câu chùm) chỉ đọc. */
export const modeReadOnly = (locks: RowLocks): boolean => locks.counts || locks.mode;

// ---------------------------------------------------------------- đọc, ghi theo id

export function readRow(section: ExamSection, blockId: string): QuestionBlock | null {
  return section.blocks.find((block) => block.id === blockId) ?? null;
}

/**
 * Ghi một phần thay đổi vào nhóm có `id` trong môn; nhóm khác, môn và thứ tự giữ nguyên. Đi qua `applyBlockPatch` như màn nhóm
 * (dọn điều kiện không còn áp khi đổi dạng).
 */
export function writeRow(section: ExamSection, blockId: string, patch: Partial<QuestionBlock>): ExamSection {
  let changed = false;
  const blocks = section.blocks.map((block) => {
    if (block.id !== blockId) return block;
    changed = true;
    return applyBlockPatch(block, patch);
  });
  return changed ? { ...section, blocks } : section;
}

/** Đổi Cách lấy: Câu đơn ↔ Câu chùm, cùng quy tắc với màn nhóm (chùm có `setCount` × `setSize`, đơn bỏ số chùm). */
export function modePatch(block: QuestionBlock, mode: "single" | "set"): Partial<QuestionBlock> {
  const count = block.questionCount ?? 0;
  if (mode === "set") {
    if (isSetBlock(block)) return {};
    return { questionType: "QUESTION_SET", setCount: 1, setSize: Math.max(count, 1), questionCount: Math.max(count, 1), childAnswerType: null, expectedScoring: undefined };
  }
  if (!isSetBlock(block)) return {};
  return { questionType: "SINGLE_CHOICE", setCount: null, setSize: null, childAnswerType: null, questionCount: Math.max(count, 1), expectedScoring: undefined };
}

// ---------------------------------------------------------------- chỉ số chỉ đọc, tóm tắt điều kiện

export interface RowFigures {
  /** Số câu của nhóm (câu con của chùm tính vào). */
  questions: number;
  /** Số ý của câu Đúng/Sai (số câu × số ý mỗi câu); `null` khi nhóm không phải Đúng/Sai hoặc mẫu chưa đặt số ý. */
  statements: number | null;
  /** Thang điểm của nhóm: số câu × điểm mỗi câu; `null` khi mẫu chưa đặt điểm mỗi câu. */
  points: number | null;
  /** Số chùm × cỡ chùm; `null` ở nhóm câu đơn hoặc chùm cỡ linh hoạt. */
  sets: { count: number; size: number } | null;
  trueFalse: boolean;
}

function round(value: number): number {
  return Math.round(value * 10_000) / 10_000;
}

/** Chỉ số chỉ đọc của dòng (R-7, 2B-HV R-133/139/140): ví dụ "4 câu Đ/S · 16 ý · thang 4 điểm". Không có ô sửa điểm. */
export function rowFigures(block: QuestionBlock): RowFigures {
  const questions = block.questionCount ?? 0;
  const trueFalse = block.questionType === "TRUE_FALSE";
  const perQuestion = block.itemShape?.statements;
  const each = block.requiredPointsPerQuestion;
  return {
    questions,
    trueFalse,
    statements: trueFalse && perQuestion !== undefined && perQuestion > 0 ? questions * perQuestion : null,
    points: each === null || each === undefined ? null : round(questions * each),
    sets: isSetBlock(block) && !isFlexibleSet(block) && block.setCount && block.setSize ? { count: block.setCount, size: block.setSize } : null,
  };
}

export type ConditionKey = "tags" | "scope" | "topicQuotas" | "difficulty" | "grade" | "slots" | "positions";

export interface RowConditionSummary {
  key: ConditionKey;
  /** Số mục của điều kiện (nhãn, MAPID, dòng chỉ tiêu, chùm có điều kiện, dải vị trí); `null`: điều kiện không đếm. */
  count: number | null;
}

/** Điều kiện đang có giá trị của dòng, theo thứ tự của bảng R-7; màn ghép thành một dòng tóm tắt. */
export function conditionSummary(block: QuestionBlock): RowConditionSummary[] {
  const summary: RowConditionSummary[] = [];
  if (block.tagCodes.length > 0) summary.push({ key: "tags", count: block.tagCodes.length });
  if ((block.knowledgeScope?.nodeIds.length ?? 0) > 0) summary.push({ key: "scope", count: block.knowledgeScope?.nodeIds.length ?? 0 });
  if ((block.topicQuotas?.length ?? 0) > 0) summary.push({ key: "topicQuotas", count: block.topicQuotas?.length ?? 0 });
  if (block.matrix) {
    if (block.matrix.byDifficulty.length > 0) summary.push({ key: "difficulty", count: null });
    if (block.matrix.byGrade.length > 0) summary.push({ key: "grade", count: null });
  }
  const slots = block.slots?.filter((slot) => (slot.tagCodes?.length ?? 0) > 0 || (slot.knowledgeScope?.nodeIds.length ?? 0) > 0).length ?? 0;
  if (slots > 0) summary.push({ key: "slots", count: slots });
  if ((block.childConditions?.length ?? 0) > 0) summary.push({ key: "positions", count: block.childConditions?.length ?? 0 });
  return summary;
}

/** Loại câu hiện ở cột "Cách lấy": dạng của nhóm đơn, hoặc dạng câu con của nhóm chùm (`null`: không giới hạn). */
export function rowAnswerType(block: Pick<QuestionBlock, "questionType" | "childAnswerType">): QuestionType | null {
  return block.questionType === "QUESTION_SET" ? block.childAnswerType : block.questionType;
}

// ---------------------------------------------------------------- Phân bổ đối chiếu: ẩn lớp, chiều trống, mức áp dụng

/** Khối lớp có hiện ở DS của cặp không (R-11). Dữ liệu lớp vẫn giữ nguyên khi không hiện. */
export const gradeVisible = (entry: Pick<SubjectFormEntry, "hideGrade">): boolean => !entry.hideGrade;

export type MatrixDimensionKey = "GRADE" | "DIFFICULTY";

const keyOf = (dimension: MatrixDimensionKey) => (dimension === "GRADE" ? "byGrade" : "byDifficulty");
const enforcementKeyOf = (dimension: MatrixDimensionKey) => (dimension === "GRADE" ? "gradeEnforcement" : "difficultyEnforcement");

/** Ma trận riêng từng chiều theo câu: chỗ mở đầu khi môn hay dòng chưa có ma trận (`SEPARATE`, `COUNT`, chiều trống). */
export function blankMatrix(): BlockMatrix {
  return { mode: "SEPARATE", unit: "COUNT", cells: [], byGrade: [], byDifficulty: [] };
}

/** Ma trận không còn giá trị nào (cả hai chiều trống, không ô kết hợp): về `null` như dòng chưa khai chỉ tiêu riêng. */
export function pruneMatrix(matrix: BlockMatrix): BlockMatrix | null {
  const empty = matrix.byGrade.length === 0 && matrix.byDifficulty.length === 0 && matrix.cells.length === 0 && (matrix.byCompetency?.length ?? 0) === 0;
  return empty ? null : matrix;
}

/** Giá trị đã khai của chiều theo mã; `null`: ô để trống (khác ô nhập 0, FTM AC 53 c). */
export function dimensionCell(matrix: BlockMatrix | null, dimension: MatrixDimensionKey, code: string): number | null {
  const found = matrix?.[keyOf(dimension)].find((item) => item.code === code);
  return found ? found.value : null;
}

/** Tổng đã khai của chiều (ô trống tính 0) và số ô đã khai. */
export function dimensionDeclared(matrix: BlockMatrix | null, dimension: MatrixDimensionKey): { total: number; cells: number } {
  const values = matrix?.[keyOf(dimension)] ?? [];
  return { total: sum(values.map((item) => item.value)), cells: values.length };
}

/**
 * Ghi một ô của chiều ma trận (R-8). Ô để trống (`null`) bị bỏ khỏi chiều, ô nhập 0 giữ giá trị 0; chiều không còn ô nào ghi mảng
 * rỗng (đúng kiểu `BlockMatrix`), không `null`. Chiều kia, ô kết hợp, đơn vị và mức không đụng tới: lớp bị ẩn giữ nguyên dữ liệu
 * (R-11). `matrix = null` mở đầu bằng ma trận trống riêng từng chiều theo câu. Mức áp dụng chỉ ghi khi cặp đặt mức cố định
 * (`FIXED_REFERENCE` của BCA ghi `REFERENCE`); HSA có HSA-C, QDA và THPTQG để mặc định `REQUIRED` (trường thiếu).
 */
export function setDimensionCell(
  matrix: BlockMatrix | null,
  dimension: MatrixDimensionKey,
  code: string,
  value: number | null,
  axes: MatrixAxes,
  enforcement: SubjectFormEntry["enforcement"] | null = null,
): BlockMatrix {
  const base = matrix ?? blankMatrix();
  const codes = dimension === "GRADE" ? axes.grades : axes.difficulties;
  const current = base[keyOf(dimension)].filter((item) => item.code !== code);
  const added = value === null ? current : [...current, { code, value }];
  const ordered = [...added].sort((a, b) => codeOrder(codes, a.code) - codeOrder(codes, b.code));
  return {
    ...base,
    [keyOf(dimension)]: ordered,
    ...(enforcement === "FIXED_REFERENCE" && ordered.length > 0 ? { [enforcementKeyOf(dimension)]: "REFERENCE" as const } : {}),
  };
}

function codeOrder(codes: readonly string[], code: string): number {
  const index = codes.indexOf(code);
  return index < 0 ? codes.length : index;
}

/** Chiều đang ở mức nào: `REQUIRED` khi không ghi. */
export function dimensionEnforcement(matrix: BlockMatrix | null, dimension: MatrixDimensionKey): "REQUIRED" | "REFERENCE" {
  return matrix?.[enforcementKeyOf(dimension)] ?? "REQUIRED";
}

/**
 * Cách vẽ mức áp dụng của một chiều: mức đã ghi `REFERENCE` ở kỳ mức cố định Bắt buộc (ngoại lệ HSA-C: lớp Tiếng Anh chờ chuyên
 * gia) hiện chip Tham khảo như hôm nay, không đổi thành Bắt buộc (R-2, R-8).
 */
export function enforcementModeOf(entry: SubjectFormEntry, matrix: BlockMatrix | null, dimension: MatrixDimensionKey): SubjectFormEntry["enforcement"] {
  if ((entry.enforcement === "FIXED_REQUIRED" || entry.enforcement === "DECLARED_REQUIRED") && dimensionEnforcement(matrix, dimension) === "REFERENCE") return "FIXED_REFERENCE";
  return entry.enforcement;
}
