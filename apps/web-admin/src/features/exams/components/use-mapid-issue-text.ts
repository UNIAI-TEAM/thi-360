"use client";

import { useFormatter, useTranslations } from "next-intl";
import { useCallback, useMemo } from "react";
import { useContentReference } from "@/features/content/queries";
import { indexKnowledge } from "@/features/content/knowledge-tree";
import { findSectionById, type BlockContext } from "../check";
import type { CheckIssue } from "../review-types";
import type { ExamDetail } from "../types";
import { useMapidRuleText } from "./mapid-rules-table";

/** Mã vấn đề có câu riêng cho quy tắc MAPID (`exams.review.issue`); các mã còn lại có `ruleId` là thiếu nguồn cho quy tắc. */
const RULE_OWN_CODES: Readonly<Record<string, true>> = { MAPID_RULE_CONFLICT: true, MAPID_RULE_GROUP_MISSING: true };

export interface MapidIssueText {
  /** Giá trị thêm cho câu của mã (`rule`, `rules`, `kind`, `topics`, số chùm). */
  values: Record<string, string | number>;
  /** Câu hoàn chỉnh khi vấn đề thuộc dạng riêng (thiếu nguồn của quy tắc, thiếu chùm theo quy tắc); `null`: dùng câu của mã. */
  text: string | null;
}

/**
 * Phần chữ của vấn đề liên quan quy tắc MAPID (UNI-2758): quy tắc gọi bằng câu mô tả của nó («Toàn môn · Phải có Hàm số»), lấy từ
 * `mapidRules` của môn trong cấu trúc (`exam`); không có cấu trúc thì gọi bằng mã quy tắc. MAPID của quy tắc bị thiếu nguồn
 * nêu bằng mã trong cây kiến thức.
 */
export function useMapidIssueText(exam?: Pick<ExamDetail, "parts">) {
  const t = useTranslations("exams.mapidRules.issue");
  const format = useFormatter();
  const reference = useContentReference().data;
  const { sentence } = useMapidRuleText(reference);
  const knowledge = useMemo(() => indexKnowledge(reference?.knowledgeTree ?? []), [reference]);
  return useCallback(
    (issue: CheckIssue, where: BlockContext | null, base: Record<string, string | number>): MapidIssueText => {
      const section = exam ? findSectionById(exam, issue.sectionId ?? where?.section?.id) : null;
      const ruleLabel = (ruleId: string) => {
        const rule = section?.mapidRules?.find((item) => item.id === ruleId);
        return t("quoted", { sentence: section && rule ? sentence(section, rule) : ruleId });
      };
      const ruleIds = issue.ruleIds?.length ? issue.ruleIds : issue.ruleId ? [issue.ruleId] : [];
      // Danh mục chưa tải: không có mã để nêu, bỏ phần "(MAPID …)" thay vì in UUID của nút (R1-F9).
      const topics = reference ? (issue.nodeIds ?? []).map((nodeId) => knowledge.get(nodeId)?.node.code || nodeId).join(", ") : "";
      const setSize = where?.block.setSize ?? 0;
      const values = {
        rule: issue.ruleId ? ruleLabel(issue.ruleId) : "",
        rules: format.list(ruleIds.map(ruleLabel), { type: "conjunction" }),
        kind: issue.conflictKind ?? "OTHER",
        topics,
        hasTopics: topics ? "yes" : "no",
        requiredSets: issue.requiredSets ?? 0,
        availableSets: issue.availableSets ?? 0,
        availableSingles: issue.availableSingles ?? 0,
        setSize,
        group: where?.block.name || String(base.block),
      };
      const all = { ...base, ...values };
      if (issue.code === "SET_SHORTAGE" && issue.requiredSets != null && issue.availableSingles != null && setSize > 0) {
        return { values, text: t("setShortage", all) };
      }
      if (issue.ruleId && !RULE_OWN_CODES[issue.code]) {
        return { values, text: t("shortage", all) };
      }
      return { values, text: null };
    },
    [exam, t, format, sentence, knowledge, reference],
  );
}
