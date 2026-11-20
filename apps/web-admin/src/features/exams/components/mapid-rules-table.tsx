"use client";

import { Button, cn, focusRing, IconButton, StatusChip } from "@thi360/ui";
import { Pencil, Plus, Trash2 } from "lucide-react";
import { useFormatter, useTranslations } from "next-intl";
import { useCallback, useId, useMemo, useRef, useState } from "react";
import { indexKnowledge } from "@/features/content/knowledge-tree";
import type { ContentReference } from "@/features/content/types";
import {
  deletedGroupRules,
  describeRule,
  type GroupRule,
  mapidRuleAnchor,
  MAX_MAPID_RULES,
  type RuleSentence,
} from "../mapid-rules";
import type { ExamSection, MapidRule, QuestionBlock } from "../types";
import { ExamContentText, markExamContent } from "./exam-content-text";
import { MapidRuleDialog } from "./mapid-rule-dialog";
import { ScrollableTable } from "./scrollable-table";

/**
 * Chữ của quy tắc MAPID: câu đầy đủ ("Trả lời ngắn · Đúng 3/15 câu thuộc Hàm số") cho bảng quy tắc, câu ngắn không có phạm vi cho
 * cột "Phạm vi nguồn" của bảng nhóm. Tên MAPID lấy từ cây kiến thức; mã lạ hiện chính mã. Các MAPID của một quy tắc hiểu theo
 * nghĩa "hoặc" nên nối bằng "hoặc".
 */
export function useMapidRuleText(reference: ContentReference | undefined) {
  const t = useTranslations("exams.mapidRules");
  const format = useFormatter();
  const knowledgeIndex = useMemo(() => indexKnowledge(reference?.knowledgeTree ?? []), [reference]);
  const topics = useCallback(
    (nodeIds: readonly string[]) => {
      // Danh mục chưa tải (đang tải hoặc lỗi): chưa có tên nên không hiện mã nút thô (UUID) mà hiện chữ giữ chỗ (R1-F9).
      if (!reference) return t("topicsPending");
      return format.list(
        nodeIds.map((nodeId) => {
          const node = knowledgeIndex.get(nodeId)?.node;
          return node?.name || node?.code || nodeId;
        }),
        { type: "disjunction" },
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
        topics: topics(rule.nodeIds),
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
  return { sentence, short, topics };
}

export interface MapidRulesTableProps {
  /** Môn đang sửa: mẫu số của quy tắc cả môn, `mapidRules` và các nhóm của môn. */
  section: ExamSection;
  /** Nhóm lấy câu của môn mà quy tắc có thể trỏ tới (thường `section.blocks`). */
  groups: readonly QuestionBlock[];
  subjectName: string;
  reference: ContentReference;
  /** Danh sách quy tắc mới sau khi thêm, sửa hoặc xóa. */
  onChange: (rules: MapidRule[]) => void;
}

/**
 * Bảng "Quy tắc MAPID" của một môn HSA ở chế độ Theo ma trận MAPID (UNI-1704 v2): mỗi quy tắc một dòng, đọc thành câu, có Sửa
 * và Xóa; "Thêm quy tắc" mở hộp thoại. Tối đa `MAX_MAPID_RULES` quy tắc một môn (Q5). Quy tắc trỏ tới nhóm đã xóa hiện chip
 * "Nhóm đã xóa" và chặn lưu cho tới khi sửa hoặc xóa. Dùng được riêng (không phụ thuộc trạng thái màn môn) để màn khác nhúng lại.
 * Mỏ neo mỗi dòng: `mapid-rule-<id>` (`id` và `data-field`), xem `focusMapidRule`.
 */
export function MapidRulesTable({ section, groups, subjectName, reference, onChange }: MapidRulesTableProps) {
  const t = useTranslations("exams.mapidRules");
  const headingId = useId();
  const rootRef = useRef<HTMLElement>(null);
  const text = useMapidRuleText(reference);
  const [editing, setEditing] = useState<MapidRule | "new" | null>(null);
  const rules = section.mapidRules ?? [];
  const full = rules.length >= MAX_MAPID_RULES;
  const view = useMemo(() => ({ ...section, blocks: [...groups] }), [section, groups]);
  const deleted = useMemo(() => new Set(deletedGroupRules(view).map((rule) => rule.id)), [view]);

  const remove = (rule: MapidRule) => {
    onChange(rules.filter((item) => item.id !== rule.id));
    // Dòng bị xóa kéo theo nút đang giữ tiêu điểm: đưa tiêu điểm về nút "Thêm quy tắc" (R1-26).
    requestAnimationFrame(() => rootRef.current?.querySelector<HTMLElement>('[data-testid="mapid-rule-add"]')?.focus());
  };
  const save = (rule: MapidRule) => {
    const exists = rules.some((item) => item.id === rule.id);
    onChange(exists ? rules.map((item) => (item.id === rule.id ? rule : item)) : [...rules, rule]);
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
            <span data-testid="mapid-rules-counter">{t("counter", { count: rules.length, max: MAX_MAPID_RULES })}</span> · {t("hint")}
          </p>
        </div>
        <Button
          type="button"
          variant="secondary"
          size="sm"
          data-testid="mapid-rule-add"
          aria-label={t("addAria", { subject: subjectName })}
          disabled={full}
          onClick={() => setEditing("new")}
        >
          <Plus />
          <span>{t("add")}</span>
        </Button>
      </div>
      {full && <p className="text-caption text-text-muted">{t("limit", { max: MAX_MAPID_RULES })}</p>}
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
      />
    </section>
  );
}
