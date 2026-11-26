"use client";

import { Button, cn, focusRing, IconButton, StatusChip } from "@thi360/ui";
import { Pencil, Plus, Trash2, TriangleAlert, Wrench } from "lucide-react";
import { useFormatter, useTranslations } from "next-intl";
import { useCallback, useId, useMemo, useRef, useState } from "react";
import { indexKnowledge } from "@/features/content/knowledge-tree";
import type { ContentReference } from "@/features/content/types";
import { mapidRuleWarnings, type RuleWarning, withRuleCount } from "../mapid-rule-budget";
import {
  deletedGroupRules,
  describeRule,
  formatRuleTopicNames,
  type GroupRule,
  mapidRuleAnchor,
  type RuleSentence,
} from "../mapid-rules";
import type { ExamSection, MapidRule, QuestionBlock } from "../types";
import { ExamContentText, markExamContent } from "./exam-content-text";
import { MapidRuleDialog } from "./mapid-rule-dialog";
import { ScrollableTable } from "./scrollable-table";

export function useMapidRuleText(reference: ContentReference | undefined) {
  const t = useTranslations("exams.mapidRules");
  const format = useFormatter();
  const knowledgeIndex = useMemo(() => indexKnowledge(reference?.knowledgeTree ?? []), [reference]);
  const topics = useCallback(
    (nodeIds: readonly string[], marked = false) => {
      // Danh mục chưa tải (đang tải hoặc lỗi): chưa có tên nên không hiện mã nút thô (UUID) mà hiện chữ giữ chỗ (R1-F9).
      if (!reference) return t("topicsPending");
      return formatRuleTopicNames(
        nodeIds,
        knowledgeIndex,
        (items) => format.list(items, { type: "disjunction" }),
        marked ? markExamContent : undefined,
      );
    },
    [format, knowledgeIndex, reference, t],
  );
  const groupName = useCallback(
    (group: QuestionBlock | null, deletedName: string | null = null, marked = false) => {
      if (group) return group.name ? (marked ? markExamContent(group.name) : group.name) : t("untitledGroup");
      return deletedName ? t("groupDeleted", { name: marked ? markExamContent(deletedName) : deletedName }) : t("scopeDeleted");
    },
    [t],
  );
  const scopeText = useCallback(
    (sentence: RuleSentence, marked = false) => (sentence.scope === "SUBJECT" ? t("scopeSubject") : groupName(sentence.group, sentence.deletedGroupName, marked)),
    [groupName, t],
  );
  const sentence = useCallback(
    (section: ExamSection, rule: MapidRule, marked = false): string => {
      const data = describeRule(section, rule);
      return t(`sentence.${data.kind}`, {
        scope: scopeText(data, marked),
        group: groupName(data.group, data.deletedGroupName, marked),
        topics: topics(rule.nodeIds, marked),
        amount: data.amount ?? "",
      });
    },
    [groupName, scopeText, t, topics],
  );
  const short = useCallback(
    (section: ExamSection, item: GroupRule): string => {
      const data = describeRule(section, item.rule);
      return t(`short.${item.role === "ELSEWHERE" ? "ELSEWHERE" : data.kind}`, { topics: topics(item.rule.nodeIds), amount: data.amount ?? "" });
    },
    [t, topics],
  );
  const scopeLabel = useCallback(
    (groups: readonly QuestionBlock[], groupId: string | null): string =>
      groupId === null ? t("scopeSubject") : groupName(groups.find((item) => item.id === groupId) ?? null, null, true),
    [groupName, t],
  );
  return { sentence, short, topics, scopeLabel };
}

interface MapidRulesTableProps {
  /** Môn đang sửa: mẫu số của quy tắc cả môn, `mapidRules` và các nhóm của môn. */
  section: ExamSection;
  /** Nhóm lấy câu của môn mà quy tắc có thể trỏ tới (thường `section.blocks`). */
  groups: readonly QuestionBlock[];
  subjectName: string;
  reference: ContentReference;
  /** Danh sách quy tắc mới sau khi thêm, sửa hoặc xóa. */
  onChange: (rules: MapidRule[]) => void;
}

export function MapidRulesTable({ section, groups, subjectName, reference, onChange }: MapidRulesTableProps) {
  const t = useTranslations("exams.mapidRules");
  const headingId = useId();
  const rootRef = useRef<HTMLElement>(null);
  const text = useMapidRuleText(reference);
  const [editing, setEditing] = useState<MapidRule | "new" | null>(null);
  const rules = section.mapidRules ?? [];
  const view = useMemo(() => ({ ...section, blocks: [...groups] }), [section, groups]);
  const deleted = useMemo(() => new Set(deletedGroupRules(view).map((rule) => rule.id)), [view]);
  const knowledgeIndex = useMemo(() => indexKnowledge(reference.knowledgeTree), [reference]);
  // UNI-2824 điểm 6: sửa cấu trúc làm số không khớp vẫn lưu được; dòng bị ảnh hưởng hiện cảnh báo kèm nút sửa nhanh.
  const warnings = useMemo(() => mapidRuleWarnings(view, knowledgeIndex), [view, knowledgeIndex]);
  const format = useFormatter();

  const applyFix = (rule: MapidRule, count: number) => onChange(rules.map((item) => (item.id === rule.id ? withRuleCount(item, count) : item)));

  const warningText = (rule: MapidRule, warning: RuleWarning): string => {
    switch (warning.code) {
      case "SUBJECT_BELOW_GROUPS":
        return t("warning.SUBJECT_BELOW_GROUPS", {
          allocated: warning.allocated,
          shares: format.list(
            warning.shares.map((share) => t("dialog.budget.share", { group: text.scopeLabel(groups, share.groupId), count: share.need })),
            { type: "unit" },
          ),
        });
      case "GROUP_ABOVE_SUBJECT":
        return t("warning.GROUP_ABOVE_SUBJECT", {
          remaining: warning.remaining,
          topics: text.topics(warning.subjectRule.nodeIds, true),
          rule: text.sentence(view, warning.subjectRule, true),
        });
      case "ABOVE_UNITS":
        return t(rule.groupId ? "warning.ABOVE_UNITS_GROUP" : "warning.ABOVE_UNITS_SUBJECT", { units: warning.units });
      case "ABOVE_ONLY_GROUP":
        return t("warning.ABOVE_ONLY_GROUP", {
          topics: text.topics(rule.nodeIds, true),
          group: text.scopeLabel(groups, warning.groupId),
          units: warning.units,
        });
      case "OUTSIDE_SUBJECT_SOURCE":
        return t("warning.OUTSIDE_SUBJECT_SOURCE", {
          rules: format.list(
            warning.sources.map((source) => text.sentence(view, source, true)),
            { type: "conjunction" },
          ),
        });
    }
  };

  const fixLabel = (rule: MapidRule, warning: RuleWarning, count: number): string =>
    rule.groupId
      ? t("warning.fixGroup", { group: text.scopeLabel(groups, rule.groupId), count })
      : t(warning.code === "SUBJECT_BELOW_GROUPS" ? "warning.fixRaiseSubject" : "warning.fixLowerSubject", { count });

  const remove = (rule: MapidRule) => {
    onChange(rules.filter((item) => item.id !== rule.id));
    // Dòng bị xóa kéo theo nút đang giữ tiêu điểm: đưa tiêu điểm về nút "Thêm quy tắc" (R1-26).
    requestAnimationFrame(() => rootRef.current?.querySelector<HTMLElement>('[data-testid="mapid-rule-add"]')?.focus());
  };
  const save = (rule: MapidRule, options?: { replaceIds?: readonly string[] }) => {
    const replaceSet = new Set(options?.replaceIds ?? []);
    const remaining = rules.filter((item) => item.id === rule.id || !replaceSet.has(item.id));
    const exists = remaining.some((item) => item.id === rule.id);
    onChange(exists ? remaining.map((item) => (item.id === rule.id ? rule : item)) : [...remaining, rule]);
    setEditing(null);
  };

  return (
    <section ref={rootRef} data-testid="mapid-rules-table" tabIndex={-1} aria-labelledby={headingId} className={cn("flex scroll-mt-24 flex-col gap-3 rounded-md border border-border p-4", focusRing)}>
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div className="flex min-w-0 flex-col gap-0.5">
          <h4 id={headingId} className="text-label font-semibold text-text">
            {t("title")}
          </h4>
          <p className="text-caption text-text-muted">
            <span data-testid="mapid-rules-counter">{t("counter", { count: rules.length })}</span> · {t("hint")}
          </p>
        </div>
        <Button
          type="button"
          variant="secondary"
          size="sm"
          data-testid="mapid-rule-add"
          aria-label={t("addAria", { subject: subjectName })}
          onClick={() => setEditing("new")}
        >
          <Plus />
          <span>{t("add")}</span>
        </Button>
      </div>
      {rules.length === 0 ? (
        <p className="text-body text-text-muted">{t("empty")}</p>
      ) : (
        <ScrollableTable label={t("caption", { subject: subjectName })} className="rounded-md border border-border">
          <table className="w-full border-separate border-spacing-0 text-body">
            <caption className="sr-only">{t("caption", { subject: subjectName })}</caption>
            <thead>
              <tr className="text-left text-caption text-text-muted">
                <th scope="col" className="border-b border-border bg-subtle px-3 py-2 font-semibold">{t("rule")}</th>
                <th scope="col" className="border-b border-border bg-subtle px-3 py-2 text-right font-semibold whitespace-nowrap">
                  <span className="sr-only">{t("actions")}</span>
                </th>
              </tr>
            </thead>
            <tbody>
              {rules.map((rule) => {
                const sentence = text.sentence(view, rule);
                const anchor = mapidRuleAnchor(rule.id);
                return (
                  <tr key={rule.id} id={anchor} data-field={anchor} data-testid={`mapid-rule-row-${rule.id}`} tabIndex={-1} className={cn("scroll-mt-24 data-[mapid-highlight]:bg-primary-50 data-[mapid-focus]:outline-2 data-[mapid-focus]:-outline-offset-2 data-[mapid-focus]:outline-primary-text", focusRing)}>
                    <td className="border-b border-border px-3 py-2 text-text">
                      <span className="wrap-anywhere"><ExamContentText text={text.sentence(view, rule, true)} /></span>
                      {(warnings.get(rule.id) ?? []).map((warning) => {
                        const fix = "fix" in warning ? warning.fix : null;
                        return (
                          <span
                            key={warning.code}
                            data-testid={`mapid-rule-warning-${rule.id}`}
                            data-code={warning.code}
                            className="mt-1.5 flex min-w-0 flex-wrap items-center gap-x-2 gap-y-1"
                          >
                            <span className="flex min-w-0 items-start gap-1.5 text-caption text-text-secondary">
                              <TriangleAlert aria-hidden className="mt-0.5 size-3.5 shrink-0 text-warning" />
                              <span className="min-w-0 wrap-anywhere">
                                <ExamContentText text={warningText(rule, warning)} />
                              </span>
                            </span>
                            {fix !== null && (
                              <Button
                                type="button"
                                variant="outline"
                                size="sm"
                                data-testid={`mapid-rule-fix-${rule.id}`}
                                className="h-auto min-h-8 min-w-0 max-w-full whitespace-normal py-1 text-left [&>span]:min-w-0"
                                onClick={() => applyFix(rule, fix)}
                              >
                                <Wrench />
                                <span className="min-w-0 wrap-anywhere">
                                  <ExamContentText text={fixLabel(rule, warning, fix)} />
                                </span>
                              </Button>
                            )}
                          </span>
                        );
                      })}
                      {deleted.has(rule.id) && (
                        <span className="mt-1 flex flex-wrap items-center gap-2">
                          <StatusChip tone="danger">{rule.deletedGroupName ? t("deletedGroupNamed", { name: rule.deletedGroupName }) : t("deletedGroup")}</StatusChip>
                          <span className="text-caption text-danger">{t("deletedGroupFix")}</span>
                        </span>
                      )}
                    </td>
                    <td className="border-b border-border px-3 py-1 text-right whitespace-nowrap">
                      <IconButton icon={<Pencil />} aria-label={t("editAria", { sentence })} title={t("edit")} onClick={() => setEditing(rule)} />
                      <IconButton icon={<Trash2 />} aria-label={t("deleteAria", { sentence })} title={t("delete")} onClick={() => remove(rule)} />
                    </td>
                  </tr>
                );
              })}
            </tbody>
          </table>
        </ScrollableTable>
      )}
      <MapidRuleDialog
        open={editing !== null}
        onOpenChange={(open) => {
          if (!open) setEditing(null);
        }}
        section={view}
        groups={groups}
        subjectName={subjectName}
        reference={reference}
        rule={editing === "new" ? null : editing}
        onSave={save}
        onOpenRule={(id) => {
          const target = rules.find((item) => item.id === id);
          if (target) setEditing(target);
        }}
      />
    </section>
  );
}
