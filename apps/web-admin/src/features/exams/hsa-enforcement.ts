"use client";

import { createContext, useContext } from "react";
import { canRequire } from "./allocation";
import { canRequireQuota } from "./exam-overview";
import { normalizeMapidSection } from "./mapid-rules";
import { awaitsExpert } from "./reference-distribution";
import type { BlockMatrix, ExamPart, ExamPartOption, ExamQuota, ExamSection, QuestionBlock, QuotaBasis, QuotaEnforcement, TopicQuota } from "./types";

/**
 * Kỳ thi HSA sinh đề luôn theo ma trận (UNI-1701): mức của phân bố là Bắt buộc và không đổi được. Mức Tham khảo chỉ còn
 * ở nơi quy tắc H6 (chỉ tiêu trải nhiều môn) hoặc H7 (đơn vị đếm không phải câu) cấm Bắt buộc. Kỳ thi khác giữ hai mức.
 */
const HSA_EXAM_KIND = "HSA";

export function isHsaExam(kind: string | null | undefined): boolean {
  return kind === HSA_EXAM_KIND;
}

/** Bài đang soạn có phải HSA không; `EnforcementControl` đọc để hiện mức cố định. Ngoài trình soạn cấu trúc: không. */
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

const hasValue = (rows: ReadonlyArray<{ value: number }> | undefined): boolean => (rows ?? []).some((row) => row.value > 0);

/**
 * Mỗi chiều chuyển riêng và chỉ khi có số: chiều trống ở Tham khảo nghĩa là chưa khai báo (ví dụ Tiếng Anh chờ chuyên gia),
 * đổi sang Bắt buộc thì sinh cảnh báo thiếu câu mà người dùng không gỡ được. Ma trận kết hợp chỉ có một mức cho hai chiều.
 */
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

/**
 * Chiều của ma trận mà lớp chuẩn hóa giữ nguyên ở Tham khảo (R1-2): mức hiển thị là Tham khảo chỉ vì chiều chưa có số,
 * tức chưa được khai báo. Chip của chiều đó hiện Tham khảo kèm ghi chú "chưa khai báo", không mâu thuẫn bản nháp; khi
 * có số, lớp chuẩn hóa đổi sang Bắt buộc và chip theo đúng bản nháp. `dimension` chỉ dùng cho ma trận riêng từng chiều.
 */
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

/**
 * Mức cho phép của một môn, rồi quy tắc MAPID (UNI-1704 v2): môn dùng ma trận còn phạm vi, chỉ tiêu chủ đề kiểu cũ thì chuyển
 * sang quy tắc, và số câu của quy tắc nhập theo tỷ lệ luôn theo số câu hiện tại của môn, nhóm (`mapid-rules.ts`).
 */
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

/**
 * Chuyển mức Tham khảo đã lưu của ma trận khối lớp, độ khó và chỉ tiêu chủ đề sang Bắt buộc ở nơi quy tắc cho phép (đơn vị
 * câu). Trả về chính `parts` khi không có gì đổi.
 */
export function normalizeHsaParts(parts: readonly ExamPart[]): ExamPart[] {
  return mapKeep(parts, normalizePart);
}

/**
 * Chỉ tiêu cấp bài chờ chuyên gia xác nhận (ADV:FE-3b:2, mẫu HSA_FULL, HSA_PART_ENGLISH, HSA_SUBJECT_ANH): phân bố khối lớp
 * của Tiếng Anh. Luôn ở Tham khảo cho tới khi người dùng chủ động "Áp dụng phân bổ này"; bộ giải không dùng nó.
 */
export function hsaQuotaAwaitsExpert(quota: Pick<ExamQuota, "subjectCodes" | "dimension">): boolean {
  return quota.subjectCodes?.length === 1 && awaitsExpert(HSA_EXAM_KIND, quota.subjectCodes[0]!, quota.dimension);
}

/**
 * Như `normalizeHsaParts` cho chỉ tiêu cấp bài: chỉ tiêu một môn, đơn vị câu thành Bắt buộc; chỉ tiêu nhiều môn ở lại Tham
 * khảo (H6), cũng như chỉ tiêu đang chờ chuyên gia.
 */
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

/**
 * Bản đã lưu tạm sau khi nạp một phiên bản cấu trúc (R1-10): bài HSA chuyển mức ở cả hai, để bản nháp (đã chuyển mức) bằng bản đã
 * lưu tạm. Nếu không, chỉ tiêu Tham khảo cũ làm bản nháp "chưa lưu" và lần tải lại kế tiếp không đưa chỉ tiêu của mẫu mới vào.
 * Lần tải lại đó thay cả hai bản đã lưu bằng dữ liệu máy chủ.
 */
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

/**
 * Giữ bản nháp HSA luôn ở mức cho phép, ở mọi lối ghi: mở bài, nạp lại, hủy thay đổi, nạp phiên bản, đổi đơn vị sang câu,
 * "Dùng ma trận tham khảo". Gọi trong lúc render (như đồng bộ `syncKey`); `normalize*` trả đúng mảng cũ khi không đổi nên
 * không lặp. Bản đã lưu giữ nguyên nên bản nháp đã chuyển hiện "có thay đổi chưa lưu" và lưu gửi đúng mức.
 */
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
