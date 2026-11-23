import type { BlockMatrix, ExamSection, KnowledgeScope, QuestionBlock, TopicQuota } from "../types";
import { limitsOfEntry, limitViolations } from "./limits";
import type { SubjectFormEntry } from "./registry";

export type FitReason =
  | "COMBINED_MATRIX"
  /** Nhóm có số câu để trống ("lấy phần còn lại"). */
  | "COUNT_NULL"
  | "PERCENT_ENTRY"
  /** Đơn vị đếm khác `QUESTION` ở phân bố Bắt buộc. */
  | "COUNT_BASIS"
  | "GRADE_REQUIRED_HIDDEN"
  /** MT: hai nhóm cùng vai trò. */
  | "ROLE_DUPLICATE"
  /** MT: nhóm có dạng khác ba vai trò (câu đơn, câu chùm, điền đáp án). */
  | "ROLE_UNKNOWN"
  /** MT: nhóm có ma trận riêng. */
  | "BLOCK_MATRIX"
  | "BLOCK_TOPIC_QUOTAS"
  /** MT: nhóm có điều kiện vị trí câu con. */
  | "POSITION_CONDITION"
  /** MT: nhóm có điều kiện từng chùm. */
  | "SET_CONDITION"
  /** MT: nhóm câu đơn hoặc điền đáp án có phạm vi kiến thức riêng (trừ HSA) hoặc nhãn riêng. */
  | "BLOCK_SCOPE_OR_TAGS"
  /** MT: `answerQuotas` không thuộc một trong ba dạng. */
  | "ANSWER_QUOTAS_SHAPE"
  /** Nhóm chùm cỡ cố định có `setCount` ≤ 0 hoặc số câu khác số chùm × cỡ chùm. */
  | "SET_SIZE_MISMATCH"
  | "LIMITS_VIOLATED"
  /** DS: nhóm có điều kiện mà màn sửa dòng không có mục tương ứng. */
  | "ROW_CONDITION_UNSUPPORTED";

export type FitResult = { fits: true } | { fits: false; reason: FitReason };

export interface FitContext {
  kindCode: string;
  subjectCode: string;
}

const FITS: FitResult = { fits: true };

const CHILD_CONDITION_PAIRS: readonly string[] = ["SP2E:ANH", "VSAT:ANH"];

const unfit = (reason: FitReason): FitResult => ({ fits: false, reason });

function hasOwnScope(scope: KnowledgeScope | null): boolean {
  return scope !== null && (scope.nodeIds.length > 0 || (scope.competencyCodes?.length ?? 0) > 0);
}

/** Chiều đã khai (mảng không rỗng) của ma trận và mức của nó; `REQUIRED` khi không ghi. */
function requiredDimensions(matrix: BlockMatrix): { grade: boolean; difficulty: boolean } {
  const combined = matrix.mode === "COMBINED";
  const declaredGrade = combined ? matrix.cells.length > 0 : matrix.byGrade.length > 0;
  const declaredDifficulty = combined ? matrix.cells.length > 0 : matrix.byDifficulty.length > 0;
  const gradeRequired = (matrix.gradeEnforcement ?? "REQUIRED") === "REQUIRED";
  const difficultyRequired = combined ? gradeRequired : (matrix.difficultyEnforcement ?? "REQUIRED") === "REQUIRED";
  return { grade: declaredGrade && gradeRequired, difficulty: declaredDifficulty && difficultyRequired };
}

function matrixReason(matrix: BlockMatrix | null, entry: SubjectFormEntry, blockLevel: boolean): FitReason | null {
  if (!matrix) return null;
  if (matrix.mode === "COMBINED" && (entry.combinedMatrix === "NONE" || blockLevel)) return "COMBINED_MATRIX";
  if (matrix.unit === "PERCENT" && entry.percentEntry === "NONE") return "PERCENT_ENTRY";
  const required = requiredDimensions(matrix);
  if ((matrix.basis ?? "QUESTION") !== "QUESTION" && (required.grade || required.difficulty)) return "COUNT_BASIS";
  if (entry.hideGrade && required.grade) return "GRADE_REQUIRED_HIDDEN";
  return null;
}

function topicQuotaReason(quotas: readonly TopicQuota[] | undefined, entry: SubjectFormEntry): FitReason | null {
  for (const quota of quotas ?? []) {
    const required = quota.enforcement === "REQUIRED";
    if (quota.percent != null && entry.percentEntry === "NONE") return "PERCENT_ENTRY";
    if (required && (quota.basis ?? "QUESTION") !== "QUESTION") return "COUNT_BASIS";
    if (required && entry.hideGrade && (quota.gradeCodes?.length ?? 0) > 0) return "GRADE_REQUIRED_HIDDEN";
  }
  return null;
}

function commonReason(section: ExamSection, entry: SubjectFormEntry): FitReason | null {
  const sectionReason = matrixReason(section.matrix, entry, false) ?? topicQuotaReason(section.topicQuotas, entry);
  if (sectionReason) return sectionReason;
  if (section.answerQuotas?.unit === "PERCENT" && entry.percentEntry === "NONE") return "PERCENT_ENTRY";
  for (const block of section.blocks) {
    if (block.questionCount === null) return "COUNT_NULL";
    const reason = matrixReason(block.matrix, entry, true) ?? topicQuotaReason(block.topicQuotas, entry);
    if (reason) return reason;
  }
  return null;
}

function setSizeMismatch(block: QuestionBlock): boolean {
  if (block.questionType !== "QUESTION_SET" || (block.setCount === null && block.setSize === null)) return false;
  const count = block.setCount ?? 0;
  return count <= 0 || (block.setSize ?? 0) <= 0 || block.questionCount !== count * (block.setSize ?? 0);
}

const ANSWER_QUOTA_TYPES: readonly string[] = ["SINGLE_CHOICE", "QUESTION_SET", "SHORT_ANSWER"];

/** `answerQuotas` trống, dạng HSA-B (đơn + Bộ câu hỏi + điền) hoặc dạng cũ (trắc nghiệm gồm câu con + điền): chỉ ba loại, mỗi loại một lần. */
function answerQuotasFit(section: ExamSection): boolean {
  const items = section.answerQuotas?.items ?? [];
  const types = items.map((item) => item.answerType);
  return types.every((type) => ANSWER_QUOTA_TYPES.includes(type)) && new Set(types).size === types.length;
}

function matrixFormReason(section: ExamSection, entry: SubjectFormEntry, context: FitContext): FitReason | null {
  const hsa = context.kindCode === "HSA";
  const roles = new Set<string>();
  for (const block of section.blocks) {
    const role = block.questionType === null || block.questionType === "SINGLE_CHOICE" ? "SINGLE" : block.questionType === "QUESTION_SET" ? "SET" : block.questionType === "SHORT_ANSWER" ? "FILL" : null;
    if (role === null) return "ROLE_UNKNOWN";
    if (roles.has(role)) return "ROLE_DUPLICATE";
    roles.add(role);
    if (block.matrix !== null) return "BLOCK_MATRIX";
    if (!hsa && (block.topicQuotas?.length ?? 0) > 0) return "BLOCK_TOPIC_QUOTAS";
    if ((block.childConditions?.length ?? 0) > 0) return "POSITION_CONDITION";
    if ((block.slots?.length ?? 0) > 0) return "SET_CONDITION";
    if (role !== "SET" && ((!hsa && hasOwnScope(block.knowledgeScope)) || block.tagCodes.length > 0)) return "BLOCK_SCOPE_OR_TAGS";
  }
  if (!answerQuotasFit(section)) return "ANSWER_QUOTAS_SHAPE";
  if (section.blocks.some(setSizeMismatch)) return "SET_SIZE_MISMATCH";
  const limits = limitsOfEntry(entry);
  if (limits && limitViolations(section, limits).length > 0) return "LIMITS_VIOLATED";
  return null;
}

function hasCompetencyMatrix(section: ExamSection): boolean {
  return (section.matrix?.byCompetency?.length ?? 0) > 0 || section.blocks.some((block) => (block.matrix?.byCompetency?.length ?? 0) > 0);
}

function rowsFormReason(section: ExamSection, context: FitContext): FitReason | null {
  const childAllowed = CHILD_CONDITION_PAIRS.includes(`${context.kindCode}:${context.subjectCode}`);
  if (!childAllowed && section.blocks.some((block) => (block.childConditions?.length ?? 0) > 0)) return "ROW_CONDITION_UNSUPPORTED";
  if (hasCompetencyMatrix(section)) return "ROW_CONDITION_UNSUPPORTED";
  return section.blocks.some(setSizeMismatch) ? "SET_SIZE_MISMATCH" : null;
}

export function fitsForm(section: ExamSection, entry: SubjectFormEntry, context: FitContext): FitResult {
  const reason = commonReason(section, entry) ?? (entry.form === "MATRIX" ? matrixFormReason(section, entry, context) : rowsFormReason(section, context));
  return reason ? unfit(reason) : FITS;
}
