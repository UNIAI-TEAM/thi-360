import { type MatrixAxes, sectionGroups, sum, validateMatrix } from "./matrix";
import type { ExamPart, ExamQuota, ExamSection, QuotaBasis, TopicQuota } from "./types";

const EPSILON = 1e-9;

/** Tỷ lệ phần trăm của `count` trên `total`, làm tròn một chữ số thập phân; `null` khi mẫu số không dương. */
export function ratePercent(count: number, total: number): number | null {
  if (!(total > 0)) return null;
  return Math.round((count / total) * 1000 + EPSILON) / 10;
}

/** Số câu của một dòng nhập theo tỷ lệ: làm tròn nửa lên, không âm. */
export function countFromPercent(percent: number, total: number): number {
  if (!(total > 0) || !(percent > 0)) return 0;
  return Math.floor((percent * total) / 100 + 0.5 + EPSILON);
}

/** Tỷ lệ chính xác (hai chữ số thập phân) để chuyển một dòng từ nhập số câu sang nhập tỷ lệ mà vẫn quy ra đúng số câu cũ. */
export function exactPercent(count: number, total: number): number {
  if (!(total > 0)) return 0;
  return Math.round((count / total) * 10000 + EPSILON) / 100;
}

export type EntryMode = "COUNT" | "PERCENT";

/** Tổng tỷ lệ nhập có bằng 100 không (bảng phân bổ toàn bộ số câu nhập theo tỷ lệ). */
export function percentTotalValid(values: readonly number[]): boolean {
  return Math.abs(sum(values) - 100) < 1e-6;
}


export const MAX_TOPIC_QUOTAS = 8;

export function isMappingPending(quota: Pick<TopicQuota, "nodeIds">): boolean {
  return quota.nodeIds.length === 0;
}

export function topicEntryMode(quotas: readonly TopicQuota[]): EntryMode {
  return quotas.length > 0 && quotas.every((quota) => quota.percent !== undefined && quota.percent !== null)
    ? "PERCENT"
    : "COUNT";
}

/** Số câu của dòng theo mẫu số mới: dòng nhập theo tỷ lệ quy đổi lại, dòng nhập theo số câu giữ nguyên. */
export function topicCountFor(quota: TopicQuota, total: number): number {
  return quota.percent !== undefined && quota.percent !== null ? countFromPercent(quota.percent, total) : quota.count;
}

/** Chuyển bảng sang nhập theo tỷ lệ (ghi `percent` từ số câu hiện tại) hoặc về nhập theo số câu (bỏ `percent`). */
export function convertTopicEntry(quotas: readonly TopicQuota[], mode: EntryMode, total: number): TopicQuota[] {
  return quotas.map((quota) => {
    const next: TopicQuota = { ...quota };
    if (mode === "PERCENT") next.percent = exactPercent(quota.count, total);
    else delete next.percent;
    return next;
  });
}

/** Áp mẫu số mới cho dòng nhập theo tỷ lệ: `count` được tính lại, `percent` giữ nguyên. */
export function applyTopicTotal(quotas: readonly TopicQuota[], total: number): TopicQuota[] {
  return quotas.map((quota) => ({ ...quota, count: topicCountFor(quota, total) }));
}

interface TopicQuotaView {
  counts: number[];
  rates: Array<number | null>;
  /** Tổng số câu các dòng có tên chủ đề. */
  sumCount: number;
  rest: number;
  restRate: number | null;
  /** Tổng cả dòng Phần còn lại, ví dụ 30/30; vượt mẫu số khi các chỉ tiêu cộng lại quá số câu. */
  grandCount: number;
  grandRate: number | null;
  /** Số câu vượt mẫu số; 0 khi không vượt. */
  over: number;
}

export function topicQuotaView(quotas: readonly TopicQuota[], total: number): TopicQuotaView {
  const counts = quotas.map((quota) => topicCountFor(quota, total));
  const sumCount = sum(counts);
  const rest = Math.max(0, total - sumCount);
  const grandCount = sumCount + rest;
  return {
    counts,
    rates: counts.map((count) => ratePercent(count, total)),
    sumCount,
    rest,
    restRate: ratePercent(rest, total),
    grandCount,
    grandRate: ratePercent(grandCount, total),
    over: Math.max(0, sumCount - total),
  };
}

/** Giá trị mặc định một dòng chỉ tiêu mới: chưa gắn MAPID, nhập theo số câu, mức Bắt buộc. */
export function newTopicQuota(id: string, name = ""): TopicQuota {
  return { id, name, nodeIds: [], count: 1, enforcement: "REQUIRED", basis: "QUESTION" };
}

export function blockingMappingRows(quotas: readonly TopicQuota[]): TopicQuota[] {
  return quotas.filter((quota) => isMappingPending(quota) && quota.enforcement !== "REFERENCE");
}


export function canRequire(basis: QuotaBasis | undefined): boolean {
  return (basis ?? "QUESTION") === "QUESTION";
}


interface SaveBlocker {
  kind: "MATRIX_PERCENT" | "TOPIC_MAX" | "TOPIC_PERCENT" | "MAPID_GROUP";
  sectionId: string;
  /** Có khi lỗi nằm ở nhóm; `null` khi ở môn. */
  blockId: string | null;
  /** `MAPID_GROUP`: quy tắc đầu tiên trỏ tới nhóm đã xóa, để thông báo gọi tên quy tắc. */
  ruleId?: string;
}

/** Môn mà màn Cấu trúc vẽ bằng panel bài ôn rút gọn (bài ôn, một nhóm). */
export function rendersReviewPanel(section: ExamSection, reviewMode: boolean): boolean {
  return reviewMode && section.blocks.length === 1;
}

export function fillReviewCounts(parts: readonly ExamPart[]): ExamPart[] {
  return parts.map((part) => {
    const [section] = part.sections;
    const [block] = section?.blocks ?? [];
    if (!section || !block || part.codeSource || part.mode === "CHOICE" || part.options.length > 0 || part.sections.length !== 1 || section.blocks.length !== 1) {
      return part;
    }
    const sectionCount = section.questionCount ?? (part.questionCount > 0 ? part.questionCount : null);
    const blockCount = block.questionCount ?? (block.setCount === null && block.setSize === null ? sectionCount : null);
    if (sectionCount === section.questionCount && blockCount === block.questionCount) return part;
    return { ...part, sections: [{ ...section, questionCount: sectionCount, blocks: [{ ...block, questionCount: blockCount }] }] };
  });
}

export function saveBlockers(parts: readonly ExamPart[], axes: MatrixAxes): SaveBlocker[] {
  const blockers: SaveBlocker[] = [];
  const topicIssue = (quotas: readonly TopicQuota[] | undefined, sectionId: string, blockId: string | null) => {
    if ((quotas?.length ?? 0) > MAX_TOPIC_QUOTAS) blockers.push({ kind: "TOPIC_MAX", sectionId, blockId });
    if (quotas?.some((quota) => (quota.percent ?? 0) > 100)) blockers.push({ kind: "TOPIC_PERCENT", sectionId, blockId });
  };
  for (const part of parts) {
    const sections = part.codeSource ? (part.subjectPool ?? []) : sectionGroups(part).flatMap((group) => group.sections);
    for (const section of sections) {
      if (validateMatrix(section.matrix, section.questionCount, axes).some((issue) => issue.code === "PERCENT_TOTAL")) {
        blockers.push({ kind: "MATRIX_PERCENT", sectionId: section.id, blockId: null });
      }
      topicIssue(section.topicQuotas, section.id, null);
      // Quy tắc MAPID trỏ tới nhóm đã xóa: máy chủ từ chối `groupId` lạ, người dùng sửa hoặc xóa quy tắc trước khi lưu.
      const groupIds = new Set(section.blocks.map((block) => block.id));
      const orphan = section.mapidRules?.find((rule) => !!rule.groupId && !groupIds.has(rule.groupId));
      if (orphan) blockers.push({ kind: "MAPID_GROUP", sectionId: section.id, blockId: null, ruleId: orphan.id });
      for (const block of section.blocks) {
        if (validateMatrix(block.matrix, block.questionCount, axes).some((issue) => issue.code === "PERCENT_TOTAL")) {
          blockers.push({ kind: "MATRIX_PERCENT", sectionId: section.id, blockId: block.id });
        }
        topicIssue(block.topicQuotas, section.id, block.id);
      }
    }
  }
  return blockers;
}

export function examQuotaBlockers(quotas: readonly ExamQuota[]): ExamQuota[] {
  return quotas.filter((quota) => {
    if (!quota.buckets.some((bucket) => bucket.percent !== undefined && bucket.percent !== null)) return false;
    const percents = quota.buckets.map((bucket) => bucket.percent ?? 0);
    return !percentTotalValid(percents) && !(quota.enforcement === "REFERENCE" && sum(percents) === 0);
  });
}
