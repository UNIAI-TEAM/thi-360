import { examQuotaBlockers, saveBlockers } from "../allocation";
import type { MatrixAxes, StructureIssue } from "../matrix";
import type { BlockingIssue } from "../subject-forms/blocking";
import type { ExamPart, ExamQuota } from "../types";
import type { BlockField } from "./block-form-fields";
import { structureIssueBlockField } from "./block-form-fields";
import { findBlock, findSection, type StructureSelection } from "./exam-structure-draft";
import { issueTarget } from "./exam-structure-issues";

export interface QuickCheckItem {
  key: string;
  /** Nhãn phần/môn hiển thị đầu dòng (vd: "Toán học", "Phần 1", "Tổng quan bài"). */
  scopeLabel: string;
  /** Nguyên nhân chi tiết (vd: "Toán học đang vượt 1 câu"). */
  message: string;
  /** Vị trí đích khi bấm "Đến vị trí cần sửa". */
  target: QuickCheckTarget;
}

export type QuickCheckTarget =
  | { kind: "selection"; selection: StructureSelection; blockField?: BlockField | null; ruleId?: string | null; quotaId?: string | null }
  | { kind: "blocking"; issue: BlockingIssue };

export interface BuildQuickCheckInput {
  parts: readonly ExamPart[];
  quotas: readonly ExamQuota[];
  axes: MatrixAxes;
  issues: readonly StructureIssue[];
  blockingIssues: readonly BlockingIssue[];
  rateInvalid: boolean;
  activeSelection: StructureSelection | null;
  subjectName: (code: string) => string;
  issueText: (issue: StructureIssue) => string;
  blockingText: (issue: BlockingIssue, subjectLabel: string) => string;
  allocationText: (kind: "MATRIX_PERCENT" | "TOPIC_PERCENT" | "TOPIC_MAX" | "MAPID_GROUP", context: {
    subjectLabel: string;
    sectionId: string;
    ruleId?: string;
  }) => string;
  examQuotaText: (quota: ExamQuota) => string;
  rateBlockedText: string;
  overviewLabel: string;
}

/**
 * Tổng hợp toàn bộ vấn đề của bản nháp cấu trúc tại bước 2 (gồm thay đổi chưa lưu ở tất cả các môn),
 * chạy hoàn toàn trên dữ liệu đang nhập — không gọi lưu và không chuyển bước (UNI-1720 mục 4).
 */
export function buildQuickCheckItems({
  parts,
  quotas,
  axes,
  issues,
  blockingIssues,
  rateInvalid,
  activeSelection,
  subjectName,
  issueText,
  blockingText,
  allocationText,
  examQuotaText,
  rateBlockedText,
  overviewLabel,
}: BuildQuickCheckInput): QuickCheckItem[] {
  const items: QuickCheckItem[] = [];
  const percentBlockIds = new Set<string>();

  for (let index = 0; index < issues.length; index += 1) {
    const issue = issues[index]!;
    if (issue.code === "BLOCK" && issue.issue.code === "PERCENT_TOTAL") {
      percentBlockIds.add(issue.blockId);
    }
    const partId = "partId" in issue ? issue.partId : issue.location.partId;
    const part = parts.find((item) => item.id === partId);
    const sectionContext = "location" in issue ? findSection(parts, issue.location.sectionId) : null;
    const scopeLabel = sectionContext ? subjectName(sectionContext.section.subjectCode) : (part?.name ?? overviewLabel);
    const blockField = issue.code === "BLOCK" ? structureIssueBlockField(issue.issue) : null;
    items.push({
      key: `structure:${index}:${issue.code}:${"blockId" in issue ? issue.blockId : "location" in issue ? issue.location.sectionId : partId}`,
      scopeLabel,
      message: issueText(issue),
      target: {
        kind: "selection",
        selection: issueTarget(issue),
        blockField,
      },
    });
  }

  for (let index = 0; index < blockingIssues.length; index += 1) {
    const issue = blockingIssues[index]!;
    const section = findSection(parts, issue.sectionId);
    const scopeLabel = section ? subjectName(section.section.subjectCode) : overviewLabel;
    items.push({
      key: `form:${index}:${issue.sectionId}:${issue.blockId ?? ""}:${issue.field ?? ""}:${issue.code}`,
      scopeLabel,
      message: blockingText(issue, scopeLabel),
      target: { kind: "blocking", issue },
    });
  }

  const blockers = saveBlockers(parts, axes);
  for (let index = 0; index < blockers.length; index += 1) {
    const blocker = blockers[index]!;
    // Tránh lặp cảnh báo tổng tỷ lệ khối đã nằm trong `validateDraft`.
    if (blocker.kind === "MATRIX_PERCENT" && blocker.blockId && percentBlockIds.has(blocker.blockId)) {
      continue;
    }
    const section = findSection(parts, blocker.sectionId);
    const scopeLabel = section ? subjectName(section.section.subjectCode) : overviewLabel;
    items.push({
      key: `alloc:${index}:${blocker.kind}:${blocker.sectionId}:${blocker.blockId ?? ""}:${blocker.ruleId ?? ""}`,
      scopeLabel,
      message: allocationText(blocker.kind, {
        subjectLabel: scopeLabel,
        sectionId: blocker.sectionId,
        ruleId: blocker.ruleId,
      }),
      target: {
        kind: "selection",
        selection: blocker.blockId ? { kind: "block", blockId: blocker.blockId } : { kind: "section", sectionId: blocker.sectionId },
        ruleId: blocker.ruleId ?? null,
      },
    });
  }

  for (const quota of examQuotaBlockers(quotas)) {
    items.push({
      key: `quota:${quota.id}`,
      scopeLabel: overviewLabel,
      message: examQuotaText(quota),
      target: { kind: "selection", selection: { kind: "exam" }, quotaId: quota.id },
    });
  }

  if (rateInvalid) {
    const block = activeSelection?.kind === "block" ? findBlock(parts, activeSelection.blockId) : null;
    const scopeLabel = block ? subjectName(block.section.subjectCode) : overviewLabel;
    items.push({
      key: "rate:invalid",
      scopeLabel,
      message: rateBlockedText,
      target: { kind: "selection", selection: activeSelection ?? { kind: "exam" } },
    });
  }

  return items;
}
