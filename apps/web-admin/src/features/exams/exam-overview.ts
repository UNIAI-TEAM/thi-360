import { activeGroupCounts, apportionByPercent, blockPointsEach, countOf, subjectChoiceCounts, sum } from "./matrix";
import type { ExamPart, ExamQuota, ExamSection, MatrixUnit, StructureCombination } from "./types";

/** Môn học viên làm trên một đường của phần, kèm số câu của môn trên đường đó (`null`: môn không khai số câu). */
interface PathSection {
  section: ExamSection;
  count: number | null;
}

function pathOf(part: ExamPart, combinations: readonly StructureCombination[], prefer: (subjectCode: string) => boolean): PathSection[] {
  const own = (sections: readonly ExamSection[]) => sections.map((section) => ({ section, count: section.questionCount }));
  const common = own(part.sections);
  if (part.codeSource) {
    const pool = part.subjectPool ?? [];
    const codes = combinations.filter((item) => item.partId === part.id || item.partId === null);
    const code = codes.find((item) => item.subjectCodes.some(prefer)) ?? codes[0];
    if (code) return [...common, ...own(code.subjectCodes.flatMap((subjectCode) => pool.filter((section) => section.subjectCode === subjectCode)))];
    const chosen: ExamSection[] = [];
    let used = 0;
    for (const section of pool) {
      if (chosen.length > 0 && used >= part.questionCount) break;
      chosen.push(section);
      used += countOf(section);
    }
    return [...common, ...own(chosen)];
  }
  if (part.mode !== "CHOICE" || part.options.length === 0) return common;
  const option = part.options.find((item) => item.sections.some((section) => prefer(section.subjectCode))) ?? part.options[0]!;
  // `SUBJECT_CHOICE`: chỉ các môn được chọn, số câu theo cách chia của phương án (không có trên môn, D25).
  const picked = option.kind === "SUBJECT_CHOICE" ? subjectChoiceCounts(option) : null;
  return [
    ...common,
    ...option.sections.flatMap((section, index) => {
      const count = picked?.get(index);
      return count === 0 ? [] : [{ section, count: count ?? section.questionCount }];
    }),
  ];
}

export function hasChoicePath(part: ExamPart, combinations: readonly StructureCombination[] = []): boolean {
  if (part.codeSource) return combinations.filter((item) => item.partId === part.id || item.partId === null).length > 1;
  return part.mode === "CHOICE" && (part.options.length > 1 || part.options.some((option) => option.kind === "SUBJECT_CHOICE"));
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

export function pointsRows(parts: readonly ExamPart[], combinations: readonly StructureCombination[] = []): PointsRow[] {
  const rows: PointsRow[] = [];
  for (const part of parts) {
    for (const { section, count } of pathOf(part, combinations, () => true)) {
      // Nhóm tạm bỏ qua không tham gia chia: lọc trước `groupCounts` để nhóm bỏ qua không lấy mất phần số câu của môn (R2-06).
      const { blocks, counts: perGroup } = activeGroupCounts(section, count);
      const taken = blocks.map((block, index) => ({ block, count: perGroup[index] ?? 0 }));
      const eachValues = new Set(taken.map((item) => blockPointsEach(item.block)));
      rows.push({
        key: `${part.id}-${section.id}`,
        partName: part.name,
        subjectCode: section.subjectCode,
        coded: Boolean(part.codeSource),
        count: sum(taken.map((item) => item.count)),
        eachPoints: eachValues.size === 1 ? ([...eachValues][0] ?? null) : null,
        totalPoints: roundPoints(sum(taken.map((item) => item.count * blockPointsEach(item.block)))),
        essayPoints: roundPoints(
          sum(taken.filter((item) => item.block.questionType === "ESSAY").map((item) => item.count * blockPointsEach(item.block))),
        ),
      });
    }
  }
  return rows;
}

interface QuotaScope {
  /** Mẫu số: số câu của các môn thuộc tập câu áp dụng trên một đường (một mã, một lựa chọn). */
  count: number;
  subjectCodes: string[];
}

export function quotaScope(
  parts: readonly ExamPart[],
  quota: Pick<ExamQuota, "subjectCodes" | "excludeSubjectCodes">,
  combinations: readonly StructureCombination[] = [],
): QuotaScope {
  const included = (subjectCode: string) =>
    (quota.subjectCodes?.length ? quota.subjectCodes.includes(subjectCode) : true) && !(quota.excludeSubjectCodes ?? []).includes(subjectCode);
  const subjects = new Set<string>();
  let count = 0;
  for (const part of parts) {
    for (const { section, count: sectionCount } of pathOf(part, combinations, included)) {
      if (!included(section.subjectCode)) continue;
      subjects.add(section.subjectCode);
      count += sectionCount ?? 0;
    }
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

export function canRequireQuota(quota: Pick<ExamQuota, "subjectCodes">): boolean {
  return quota.subjectCodes?.length === 1;
}
