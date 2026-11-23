import type { QuestionType } from "@/features/content/types";
import type { AudioCountQuery } from "./api";
import { apportionByPercent, computeAllocation, type MatrixAxes, sum } from "./matrix";
import type { AnswerQuotas, CountKnowledgeScope, ExamPartOption, ExamSection, KnowledgeScope, QuestionBlock } from "./types";

/** Quy chỉ tiêu cấp môn ra số câu: số câu giữ nguyên, tỉ lệ % quy theo phần dư lớn nhất trên số câu của môn. */
export function resolveQuotaCounts(unit: AnswerQuotas["unit"], values: readonly number[], count: number): number[] {
  return unit === "PERCENT" ? apportionByPercent(values, count).results : [...values];
}

export function sectionCaseCounts(section: Pick<ExamSection, "questionCount">, option: ExamPartOption | null): number[] {
  if (section.questionCount !== null) return [section.questionCount];
  if (option?.kind === "SUBJECT_CHOICE" && option.countsByPick) {
    return [...new Set(option.countsByPick)].sort((a, b) => a - b);
  }
  return [];
}

/** Dạng trả lời của mỗi đơn vị đếm của nhóm; `null`: chưa xác định (lấy theo chỉ tiêu của môn, hoặc câu con không giới hạn). */
export function groupAnswerType(block: Pick<QuestionBlock, "questionType" | "childAnswerType">): QuestionType | null {
  if (block.questionType === "QUESTION_SET") return block.childAnswerType;
  return block.questionType;
}

export interface QuotaRow {
  /** Mã dạng trả lời, mã khối lớp hoặc mã độ khó. */
  key: string;
  quota: number;
  allocated: number;
  /** Âm: đã phân bổ vượt chỉ tiêu. */
  missing: number;
}

export interface SubjectAllocation {
  /** Số câu của môn trong trường hợp đang tính; `null` khi chưa biết. */
  count: number | null;
  /** Tổng câu các nhóm có số câu cố định (nhóm chùm tính theo câu con). */
  fixedTotal: number;
  /** Số câu nhóm "lấy phần còn lại" nhận: `count − fixedTotal`; `null` khi chưa biết số câu của môn. */
  remainder: number | null;
  /** Tổng câu của mọi nhóm kể cả nhóm lấy phần còn lại. */
  allocated: number;
  /** `count − allocated`; `null` khi chưa biết số câu của môn. Âm: nhóm vượt số câu của môn. */
  missing: number | null;
  /** Theo chỉ tiêu dạng trả lời; `null` khi môn không khai chỉ tiêu này. */
  answerTypes: QuotaRow[] | null;
  /** Câu của nhóm chưa xác định dạng trả lời, sẽ lấp phần chỉ tiêu dạng trả lời còn thiếu. */
  openAllocated: number;
  /** Phần chỉ tiêu dạng trả lời còn thiếu mà nhóm mở chưa phủ hết; `null` khi môn không khai chỉ tiêu này. */
  openMissing: number | null;
  openSplit: Array<{ key: string; count: number }>;
  /** Theo khối lớp và độ khó của môn, đã phân bổ ở chỉ tiêu riêng của nhóm; `null` khi môn không khai. */
  grades: QuotaRow[] | null;
  difficulties: QuotaRow[] | null;
}

function blockUnits(blocks: readonly QuestionBlock[], remainder: number | null): number[] {
  const single = remainderGroupCount(blocks) === 1;
  return blocks.map((block) => block.questionCount ?? (single ? (remainder ?? 0) : 0));
}

export function remainderGroupCount(blocks: readonly QuestionBlock[]): number {
  return blocks.filter((block) => block.questionCount === null).length;
}

export function subjectAllocation(section: ExamSection, axes: MatrixAxes, count: number | null): SubjectAllocation {
  const { blocks } = section;
  const fixedTotal = sum(blocks.map((block) => block.questionCount ?? 0));
  const remainder = count === null ? null : Math.max(0, count - fixedTotal);
  const units = blockUnits(blocks, remainder);
  const allocated = sum(units);

  const hasSetRow = section.answerQuotas?.items.some((item) => item.answerType === "QUESTION_SET") ?? false;
  const typed = new Map<string, number>();
  let openAllocated = 0;
  blocks.forEach((block, index) => {
    const type = hasSetRow && block.questionType === "QUESTION_SET" ? "QUESTION_SET" : groupAnswerType(block);
    if (type === null) openAllocated += units[index] ?? 0;
    else typed.set(type, (typed.get(type) ?? 0) + (units[index] ?? 0));
  });

  let answerTypes: QuotaRow[] | null = null;
  let openMissing: number | null = null;
  let openSplit: Array<{ key: string; count: number }> = [];
  const quotas = section.answerQuotas;
  if (quotas && count !== null) {
    const resolved = resolveQuotaCounts(
      quotas.unit,
      quotas.items.map((item) => item.value),
      count,
    );
    const quotaByType = new Map(quotas.items.map((item, index) => [item.answerType as string, resolved[index] ?? 0]));
    const keys = [...quotaByType.keys(), ...[...typed.keys()].filter((key) => !quotaByType.has(key))];
    answerTypes = keys.map((key) => {
      const quota = quotaByType.get(key) ?? 0;
      const used = typed.get(key) ?? 0;
      return { key, quota, allocated: used, missing: quota - used };
    });
    // Nhóm câu đơn mở không bao giờ lấp dòng "Bộ câu hỏi": dòng này chỉ do nhóm chùm lấp.
    const fillable = answerTypes.filter((row) => row.key !== "QUESTION_SET");
    openMissing = sum(fillable.map((row) => Math.max(0, row.missing))) - openAllocated;
    if (openAllocated > 0 && openMissing === 0) {
      openSplit = fillable.filter((row) => row.missing > 0).map((row) => ({ key: row.key, count: row.missing }));
    }
  }

  const dimension = (codes: readonly string[], pick: "byGrade" | "byDifficulty"): QuotaRow[] | null => {
    if (!section.matrix || count === null) return null;
    const quota = computeAllocation({ questionCount: count, matrix: section.matrix }, axes)[pick];
    // Nhóm "lấy phần còn lại" cũng tính: số câu của nhóm là phần còn lại của môn (`units`), không phải 0.
    const own = blocks.flatMap((block, index) =>
      block.matrix ? [computeAllocation({ questionCount: units[index] ?? 0, matrix: block.matrix }, axes)[pick]] : [],
    );
    return codes.map((key, index) => {
      const used = sum(own.map((values) => values[index] ?? 0));
      return { key, quota: quota[index] ?? 0, allocated: used, missing: (quota[index] ?? 0) - used };
    });
  };

  return {
    count,
    fixedTotal,
    remainder,
    allocated,
    missing: count === null ? null : count - allocated,
    answerTypes,
    openAllocated,
    openMissing,
    openSplit,
    grades: dimension(axes.grades, "byGrade"),
    difficulties: dimension(axes.difficulties, "byDifficulty"),
  };
}

/** Phạm vi MAPID có hiệu lực của nhóm: riêng khi đã chọn nút; chưa chọn nút (`null` hoặc `nodeIds` rỗng) kế thừa môn. */
export function effectiveScopeSource(
  block: Pick<QuestionBlock, "knowledgeScope">,
  section: Pick<ExamSection, "knowledgeScope">,
): "GROUP" | "SECTION" | "NONE" {
  if (block.knowledgeScope !== null && block.knowledgeScope.nodeIds.length > 0) return "GROUP";
  return section.knowledgeScope !== null && section.knowledgeScope.nodeIds.length > 0 ? "SECTION" : "NONE";
}

export function effectiveScope(block: Pick<QuestionBlock, "knowledgeScope">, section: Pick<ExamSection, "knowledgeScope">): KnowledgeScope | null {
  const own = block.knowledgeScope;
  if (own && own.nodeIds.length > 0) return own;
  const base = section.knowledgeScope;
  if (!base) return own;
  return own?.competencyCodes?.length ? { ...base, competencyCodes: own.competencyCodes } : base;
}

export function subjectAudioGroups(
  section: ExamSection,
  quotaTypes: readonly QuestionType[],
  scopeOf: (block: QuestionBlock, section: ExamSection) => CountKnowledgeScope | null,
): AudioCountQuery[] {
  const audio =
    section.audioRequirement === "REQUIRED" || section.audioRequirement === "FORBIDDEN"
      ? { audioRequirement: section.audioRequirement }
      : {};
  return section.blocks.map(
    (block): AudioCountQuery => ({
      subjectCode: section.subjectCode,
      questionType: block.questionType,
      answerTypes: block.questionType === null ? [...quotaTypes] : [],
      childAnswerType: block.childAnswerType,
      knowledgeScope: scopeOf(block, section),
      tagCodes: block.tagCodes,
      requiredPointsPerQuestion: block.requiredPointsPerQuestion,
      itemShape: block.itemShape,
      answerFormats: block.answerFormats,
      childConditions: block.childConditions,
      ...audio,
    }),
  );
}
