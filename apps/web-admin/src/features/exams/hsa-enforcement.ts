"use client";

import { createContext, useContext } from "react";
import { canRequire } from "./allocation";
import { canRequireQuota } from "./exam-overview";
import type { BlockMatrix, ExamPart, ExamPartOption, ExamQuota, ExamSection, QuestionBlock, QuotaBasis, QuotaEnforcement, TopicQuota } from "./types";

/**
 * Kỳ thi HSA sinh đề luôn theo ma trận (UNI-1701): mức của phân bố là Bắt buộc và không đổi được. Mức Tham khảo chỉ còn
 * ở nơi quy tắc H6 (chỉ tiêu trải nhiều môn) hoặc H7 (đơn vị đếm không phải câu) cấm Bắt buộc. Kỳ thi khác giữ hai mức.
 */
export const HSA_EXAM_KIND = "HSA";

export function isHsaExam(kind: string | null | undefined): boolean {
  return kind === HSA_EXAM_KIND;
}

/** Bài đang soạn có phải HSA không; `EnforcementToggle` đọc để hiện mức cố định. Ngoài trình soạn cấu trúc: không. */
export const HsaExamContext = createContext(false);

export function useHsaExam(): boolean {
  return useContext(HsaExamContext);
}

/** Mức cố định của một phân bố HSA: Bắt buộc khi đơn vị đếm là câu và không có khóa H6, ngược lại Tham khảo. */
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

function normalizeMatrix(matrix: BlockMatrix | null): BlockMatrix | null {
  if (!matrix || !canRequire(matrix.basis)) return matrix;
  if (matrix.gradeEnforcement !== "REFERENCE" && matrix.difficultyEnforcement !== "REFERENCE") return matrix;
  return { ...matrix, gradeEnforcement: "REQUIRED", difficultyEnforcement: "REQUIRED" };
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
  if (matrix === section.matrix && topicQuotas === section.topicQuotas && blocks === section.blocks) return section;
  return { ...section, matrix, blocks, ...(topicQuotas ? { topicQuotas } : {}) };
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

/**
 * Chuyển mức Tham khảo đã lưu của ma trận khối lớp, độ khó và chỉ tiêu chủ đề sang Bắt buộc ở nơi quy tắc cho phép (đơn vị
 * câu). Trả về chính `parts` khi không có gì đổi.
 */
export function normalizeHsaParts(parts: readonly ExamPart[]): ExamPart[] {
  return mapKeep(parts, normalizePart);
}

/** Như `normalizeHsaParts` cho chỉ tiêu cấp bài: chỉ tiêu một môn, đơn vị câu thành Bắt buộc; chỉ tiêu nhiều môn ở lại Tham khảo (H6). */
export function normalizeHsaQuotas(quotas: readonly ExamQuota[]): ExamQuota[] {
  return mapKeep(quotas, (quota) =>
    quota.enforcement === "REFERENCE" && canRequire(quota.basis) && canRequireQuota(quota) ? { ...quota, enforcement: "REQUIRED" } : quota,
  );
}
