"use client";

import { createContext, useContext } from "react";
import { canRequire } from "./allocation";
import { canRequireQuota } from "./exam-overview";
import { normalizeMapidSection } from "./mapid-rules";
import { awaitsExpert } from "./reference-distribution";
import type { BlockMatrix, ExamPart, ExamPartOption, ExamQuota, ExamSection, QuestionBlock, QuotaBasis, QuotaEnforcement, TopicQuota } from "./types";

const HSA_EXAM_KIND = "HSA";
const MAPID_RULE_EXAM_KINDS: ReadonlySet<string> = new Set(["HSA", "QDA", "BCA"]);

export function isHsaExam(kind: string | null | undefined): boolean {
  return kind === HSA_EXAM_KIND;
}

/** Các kỳ thi dùng luồng quy tắc MAPID (HSA, QDA, BCA — UNI-1729 / UNI-1731). */
export function supportsMapidRules(kind: string | null | undefined): boolean {
  return kind != null && MAPID_RULE_EXAM_KINDS.has(kind);
}

/** Bài đang soạn có phải HSA không; `EnforcementControl` đọc để hiện mức cố định. Ngoài trình soạn cấu trúc: không. */
export const HsaExamContext = createContext(false);

export function useHsaExam(): boolean {
  return useContext(HsaExamContext);
}

export function hsaFixedEnforcement(basis: QuotaBasis | undefined, requiredLocked: boolean): QuotaEnforcement {
  return canRequire(basis) && !requiredLocked ? "REQUIRED" : "REFERENCE";
}

/** Giữ nguyên tham chiếu khi không phần tử nào đổi, để so sánh bản nháp với bản đã lưu không báo thay đổi giả. */
function mapKeep<T>(items: readonly T[], change: (item: T) => T): T[] {
  let changed = false;
  const next = items.map((item) => {
    const result = change(item);
    if (result !== item) changed = true;
    return result;
  });
  return changed ? next : (items as T[]);
}

const hasValue = (rows: ReadonlyArray<{ value: number }> | undefined): boolean => (rows ?? []).some((row) => row.value > 0);

function normalizeMatrix(matrix: BlockMatrix | null): BlockMatrix | null {
  if (!matrix || !canRequire(matrix.basis)) return matrix;
  if (matrix.mode === "COMBINED") {
    const reference = matrix.gradeEnforcement === "REFERENCE" || matrix.difficultyEnforcement === "REFERENCE";
    return reference && matrix.cells.some((cell) => cell.value > 0)
      ? { ...matrix, gradeEnforcement: "REQUIRED", difficultyEnforcement: "REQUIRED" }
      : matrix;
  }
  const grade = matrix.gradeEnforcement === "REFERENCE" && hasValue(matrix.byGrade);
  const difficulty = matrix.difficultyEnforcement === "REFERENCE" && hasValue(matrix.byDifficulty);
  if (!grade && !difficulty) return matrix;
  return { ...matrix, ...(grade ? { gradeEnforcement: "REQUIRED" as const } : {}), ...(difficulty ? { difficultyEnforcement: "REQUIRED" as const } : {}) };
}

export function hsaMatrixUndeclared(matrix: BlockMatrix, dimension?: "grade" | "difficulty"): boolean {
  if (matrix.mode === "SEPARATE" && dimension !== undefined) {
    const reference = dimension === "grade" ? matrix.gradeEnforcement === "REFERENCE" : matrix.difficultyEnforcement === "REFERENCE";
    return reference && !hasValue(dimension === "grade" ? matrix.byGrade : matrix.byDifficulty);
  }
  const reference = matrix.gradeEnforcement === "REFERENCE" || matrix.difficultyEnforcement === "REFERENCE";
  return reference && !matrix.cells.some((cell) => cell.value > 0);
}

function normalizeTopicQuotas(quotas: TopicQuota[] | undefined): TopicQuota[] | undefined {
  if (!quotas) return quotas;
  return mapKeep(quotas, (quota) => (quota.enforcement === "REFERENCE" && canRequire(quota.basis) ? { ...quota, enforcement: "REQUIRED" } : quota));
}

function normalizeBlock(block: QuestionBlock): QuestionBlock {
  const matrix = normalizeMatrix(block.matrix);
  const topicQuotas = normalizeTopicQuotas(block.topicQuotas);
  if (matrix === block.matrix && topicQuotas === block.topicQuotas) return block;
  return { ...block, matrix, ...(topicQuotas ? { topicQuotas } : {}) };
}

function normalizeSection(section: ExamSection): ExamSection {
  const matrix = normalizeMatrix(section.matrix);
  const topicQuotas = normalizeTopicQuotas(section.topicQuotas);
  const blocks = mapKeep(section.blocks, normalizeBlock);
  const enforced =
    matrix === section.matrix && topicQuotas === section.topicQuotas && blocks === section.blocks
      ? section
      : { ...section, matrix, blocks, ...(topicQuotas ? { topicQuotas } : {}) };
  return normalizeMapidSection(enforced);
}

function normalizeOption(option: ExamPartOption): ExamPartOption {
  const sections = mapKeep(option.sections, normalizeSection);
  const blocks = option.blocks ? mapKeep(option.blocks, normalizeBlock) : option.blocks;
  if (sections === option.sections && blocks === option.blocks) return option;
  return { ...option, sections, ...(blocks ? { blocks } : {}) };
}

function normalizePart(part: ExamPart): ExamPart {
  const sections = mapKeep(part.sections, normalizeSection);
  const options = mapKeep(part.options, normalizeOption);
  const subjectPool = part.subjectPool ? mapKeep(part.subjectPool, normalizeSection) : part.subjectPool;
  if (sections === part.sections && options === part.options && subjectPool === part.subjectPool) return part;
  return { ...part, sections, options, ...(subjectPool ? { subjectPool } : {}) };
}

export function normalizeHsaParts(parts: readonly ExamPart[]): ExamPart[] {
  return mapKeep(parts, normalizePart);
}

function normalizeMapidOption(option: ExamPartOption): ExamPartOption {
  const sections = mapKeep(option.sections, normalizeMapidSection);
  return sections === option.sections ? option : { ...option, sections };
}

function normalizeMapidPart(part: ExamPart): ExamPart {
  const sections = mapKeep(part.sections, normalizeMapidSection);
  const options = mapKeep(part.options, normalizeMapidOption);
  const subjectPool = part.subjectPool ? mapKeep(part.subjectPool, normalizeMapidSection) : part.subjectPool;
  if (sections === part.sections && options === part.options && subjectPool === part.subjectPool) return part;
  return { ...part, sections, options, ...(subjectPool ? { subjectPool } : {}) };
}

/** Chuẩn hóa phần MAPID cho các kỳ dùng luồng quy tắc MAPID ngoài HSA (QDA, BCA) mà không đổi mức enforcement. */
export function normalizeMapidParts(parts: readonly ExamPart[]): ExamPart[] {
  return mapKeep(parts, normalizeMapidPart);
}

export function hsaQuotaAwaitsExpert(quota: Pick<ExamQuota, "subjectCodes" | "dimension">): boolean {
  return quota.subjectCodes?.length === 1 && awaitsExpert(HSA_EXAM_KIND, quota.subjectCodes[0]!, quota.dimension);
}

export function normalizeHsaQuotas(quotas: readonly ExamQuota[]): ExamQuota[] {
  return mapKeep(quotas, (quota) =>
    quota.enforcement === "REFERENCE" && canRequire(quota.basis) && canRequireQuota(quota) && !hsaQuotaAwaitsExpert(quota)
      ? { ...quota, enforcement: "REQUIRED" }
      : quota,
  );
}

/** Bản đã lưu còn mức Tham khảo mà HSA không cho: màn hình ghi chú cho tới khi lưu. `quotaJson` là bản đã lưu của chỉ tiêu cấp bài. */
export function hsaNeedsNormalizing(hsa: boolean, parts: readonly ExamPart[], quotaJson: string): boolean {
  if (!hsa) return false;
  const quotas = JSON.parse(quotaJson) as ExamQuota[];
  return normalizeHsaParts(parts) !== parts || normalizeHsaQuotas(quotas) !== quotas;
}

export function hsaLoadedBaselines(hsa: boolean, partsJson: string, quotaJson: string): { parts: string; quotas: string } {
  if (!hsa) return { parts: partsJson, quotas: quotaJson };
  const parts = JSON.parse(partsJson) as ExamPart[];
  const quotas = JSON.parse(quotaJson) as ExamQuota[];
  const nextParts = normalizeHsaParts(parts);
  const nextQuotas = normalizeHsaQuotas(quotas);
  return {
    parts: nextParts === parts ? partsJson : JSON.stringify(nextParts),
    quotas: nextQuotas === quotas ? quotaJson : JSON.stringify(nextQuotas),
  };
}

export function useHsaNormalizedDraft(
  hsa: boolean,
  draft: ExamPart[],
  setDraft: (parts: ExamPart[]) => void,
  quotas: ExamQuota[],
  setQuotas: (quotas: ExamQuota[]) => void,
): void {
  if (!hsa) return;
  const nextParts = normalizeHsaParts(draft);
  if (nextParts !== draft) setDraft(nextParts);
  const nextQuotas = normalizeHsaQuotas(quotas);
  if (nextQuotas !== quotas) setQuotas(nextQuotas);
}
