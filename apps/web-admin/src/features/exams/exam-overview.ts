import { apportionByPercent, blockPointsEach, countOf, sum } from "./matrix";
import type { ExamPart, ExamQuota, ExamSection, MatrixUnit } from "./types";

/**
 * Tổng quan của bài (2B 15.3, 3.4; bản vẽ `ChiTieuCapBai2B`): điểm theo môn và chỉ tiêu cấp bài. Phép tính thuần để test
 * riêng: màn chỉ ghép chữ.
 */

/** Môn của phần theo mã nằm trong `subjectPool`; mọi phần khác nằm ở `sections` và các lựa chọn. */
function sectionsOf(part: ExamPart): ExamSection[] {
  if (part.codeSource) return part.subjectPool ?? [];
  return [...part.sections, ...part.options.flatMap((option) => option.sections)];
}

export interface PointsRow {
  key: string;
  partName: string;
  subjectCode: string;
  /** Môn của phần theo mã: số câu và điểm tính cho một mã. */
  coded: boolean;
  count: number;
  /** Điểm mỗi câu khi mọi nhóm của môn cùng một mức; `null` khi lẫn nhiều mức. */
  eachPoints: number | null;
  totalPoints: number;
  essayPoints: number;
}

function roundPoints(value: number): number {
  return Math.round(value * 1e6) / 1e6;
}

/** Điểm từng môn: câu × điểm mỗi câu của nhóm; nhóm tạm bỏ qua không tính; Tự luận tách riêng khỏi trắc nghiệm (R-49). */
export function pointsRows(parts: readonly ExamPart[]): PointsRow[] {
  const rows: PointsRow[] = [];
  for (const part of parts) {
    const sections = part.codeSource ? (part.subjectPool ?? []).slice(0, 1) : part.options.some((option) => option.kind === "SUBJECT_CHOICE") ? [] : sectionsOf(part);
    for (const section of sections) {
      const blocks = section.blocks.filter((block) => !block.omitted);
      const eachValues = new Set(blocks.map(blockPointsEach));
      rows.push({
        key: `${part.id}-${section.id}`,
        partName: part.name,
        subjectCode: section.subjectCode,
        coded: Boolean(part.codeSource),
        count: sum(blocks.map(countOf)),
        eachPoints: eachValues.size === 1 ? ([...eachValues][0] ?? null) : null,
        totalPoints: roundPoints(sum(blocks.map((block) => countOf(block) * blockPointsEach(block)))),
        essayPoints: roundPoints(sum(blocks.filter((block) => block.questionType === "ESSAY").map((block) => countOf(block) * blockPointsEach(block)))),
      });
    }
  }
  return rows;
}

export interface QuotaScope {
  /** Mẫu số: số câu của các môn thuộc tập câu áp dụng trên một đường (một mã). */
  count: number;
  subjectCodes: string[];
}

/**
 * Tập câu một chỉ tiêu cấp bài tính trên: môn trong `subjectCodes` (hoặc mọi môn) trừ `excludeSubjectCodes`. Phần theo mã
 * chỉ tính một mã (đối chiếu theo từng mã, 3.4): lấy số câu lớn nhất của một môn trong `subjectPool`.
 */
export function quotaScope(parts: readonly ExamPart[], quota: Pick<ExamQuota, "subjectCodes" | "excludeSubjectCodes">): QuotaScope {
  const included = (subjectCode: string) =>
    (quota.subjectCodes?.length ? quota.subjectCodes.includes(subjectCode) : true) && !(quota.excludeSubjectCodes ?? []).includes(subjectCode);
  const subjects = new Set<string>();
  let count = 0;
  for (const part of parts) {
    const matching = (part.codeSource ? (part.subjectPool ?? []) : part.options.some((option) => option.kind === "SUBJECT_CHOICE") ? [] : sectionsOf(part)).filter((section) =>
      included(section.subjectCode),
    );
    for (const section of matching) subjects.add(section.subjectCode);
    count += part.codeSource ? Math.max(0, ...matching.map((section) => section.questionCount ?? 0)) : sum(matching.map((section) => section.questionCount ?? 0));
  }
  return { count, subjectCodes: [...subjects] };
}

export function quotaUnitOf(quota: ExamQuota): MatrixUnit {
  return quota.buckets.some((bucket) => bucket.percent !== undefined && bucket.percent !== null) ? "PERCENT" : "COUNT";
}

/** Số câu quy đổi của từng mức trên mẫu số `total`: tỷ lệ quy bằng phần dư lớn nhất, số câu giữ nguyên. */
export function bucketCounts(quota: ExamQuota, total: number): number[] {
  return quotaUnitOf(quota) === "PERCENT"
    ? apportionByPercent(quota.buckets.map((bucket) => bucket.percent ?? 0), total).results
    : quota.buckets.map((bucket) => bucket.count ?? 0);
}

/** Chỉ tiêu trải nhiều môn chỉ là `REFERENCE` (H6): cho phép đặt `REQUIRED` khi tập câu đúng một môn. */
export function canRequireQuota(quota: Pick<ExamQuota, "subjectCodes">): boolean {
  return quota.subjectCodes?.length === 1;
}
