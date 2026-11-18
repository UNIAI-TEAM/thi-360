import { apportionByPercent, blockPointsEach, countOf, groupCounts, subjectChoiceCounts, sum } from "./matrix";
import type { ExamPart, ExamQuota, ExamSection, MatrixUnit, StructureCombination } from "./types";

/**
 * Tổng quan của bài (2B 15.3, 3.4; bản vẽ `ChiTieuCapBai2B`): điểm theo môn và chỉ tiêu cấp bài. Phép tính thuần để test
 * riêng: màn chỉ ghép chữ.
 */

/** Môn học viên làm trên một đường của phần, kèm số câu của môn trên đường đó (`null`: môn không khai số câu). */
interface PathSection {
  section: ExamSection;
  count: number | null;
}

/**
 * Một đường hợp lệ của phần (cùng quy tắc với tổng bài `structureTotals`: lấy đường đầu tiên, không cộng các đường loại
 * trừ nhau): môn chung cộng môn của một lựa chọn. Phần cố định: các môn của phần. Phần tự chọn: môn chung cộng một lựa chọn
 * (một tổ hợp, một nhánh, hoặc các môn được chọn của `SUBJECT_CHOICE` theo cách chia `countsByPick`). Phần theo mã: môn chung
 * cộng các môn của một mã (QDA: 2 môn × 25 câu), môn đọc từ `subjectPool`; chưa có mã nào thì lấy môn đầu tiên cho đủ số
 * câu của phần. `prefer`: ưu tiên đường có môn thỏa điều kiện (chỉ tiêu của một môn tính trên đường chứa môn đó).
 */
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

/**
 * Điểm từng môn trên một đường hợp lệ của mỗi phần (`pathOf`): câu × điểm mỗi câu của nhóm; nhóm "lấy phần còn lại" nhận
 * phần chênh của môn (cùng cách chia với tổng bài); nhóm tạm bỏ qua không tính; Tự luận tách riêng khỏi trắc nghiệm (R-49).
 */
export function pointsRows(parts: readonly ExamPart[], combinations: readonly StructureCombination[] = []): PointsRow[] {
  const rows: PointsRow[] = [];
  for (const part of parts) {
    for (const { section, count } of pathOf(part, combinations, () => true)) {
      const perGroup = count === null ? section.blocks.map(countOf) : groupCounts(section, count);
      const taken = section.blocks.flatMap((block, index) => (block.omitted ? [] : [{ block, count: perGroup[index] ?? 0 }]));
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

export interface QuotaScope {
  /** Mẫu số: số câu của các môn thuộc tập câu áp dụng trên một đường (một mã, một lựa chọn). */
  count: number;
  subjectCodes: string[];
}

/**
 * Tập câu một chỉ tiêu cấp bài tính trên: môn trong `subjectCodes` (hoặc mọi môn) trừ `excludeSubjectCodes`, trên một đường
 * hợp lệ của mỗi phần (`pathOf`, ưu tiên đường có môn của chỉ tiêu): phần theo mã tính một mã, phần chọn môn hay tổ hợp
 * tính một lựa chọn (đối chiếu theo từng đường, 3.4).
 */
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

/** Chỉ tiêu trải nhiều môn chỉ là `REFERENCE` (H6): cho phép đặt `REQUIRED` khi tập câu đúng một môn. */
export function canRequireQuota(quota: Pick<ExamQuota, "subjectCodes">): boolean {
  return quota.subjectCodes?.length === 1;
}
