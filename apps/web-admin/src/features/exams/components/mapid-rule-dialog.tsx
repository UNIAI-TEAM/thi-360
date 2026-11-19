"use client";

import { Button, Checkbox, Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle, FormField, IconButton } from "@thi360/ui";
import { CircleMinus, ListTree } from "lucide-react";
import { useTranslations } from "next-intl";
import { useId, useMemo, useState } from "react";
import { KnowledgePickerDialog } from "@/features/content/components/knowledge-picker-dialog";
import { indexKnowledge } from "@/features/content/knowledge-tree";
import type { ContentReference, KnowledgeRef } from "@/features/content/types";
import {
  emptyRuleForm,
  formFromRule,
  groupSize,
  ruleCount,
  ruleFormIssues,
  type MapidRuleForm,
  ruleFromForm,
  ruleNeedsNumber,
  ruleUnits,
} from "../mapid-rules";
import type { ExamSection, MapidComparator, MapidRule, MapidRuleType, QuestionBlock } from "../types";
import { BlockNumberInput } from "./block-number-input";
import { ChoiceCardGroup } from "./create-exam-fields";
import { draftId } from "./exam-structure-draft";

const SUBJECT_SCOPE = "__subject__";
const RULE_TYPES: readonly MapidRuleType[] = ["PRESENT", "COUNT", "ONLY_IN_GROUP", "SOURCE"];

export interface MapidRuleDialogProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  /** Môn đang sửa: `mapidRules` và số câu của môn (mẫu số của quy tắc cả môn). */
  section: ExamSection;
  /** Nhóm lấy câu của môn mà quy tắc có thể trỏ tới. */
  groups: readonly QuestionBlock[];
  subjectName: string;
  reference: ContentReference;
  /** Quy tắc đang sửa; `null`: thêm mới. */
  rule: MapidRule | null;
  onSave: (rule: MapidRule) => void;
}

/**
 * Hộp thoại "Thêm quy tắc" của bảng Quy tắc MAPID (UNI-1704 v2, mục 4): Áp dụng cho (Toàn môn hoặc một nhóm, kèm dạng câu, quy mô
 * và đơn vị) → Loại quy tắc (bốn thẻ, hai quy tắc dễ nhầm được giải thích cạnh nhau) → MAPID (hộp chọn kiến thức) → ô số chỉ
 * khi quy tắc cần chỉ tiêu (Đúng hoặc Ít nhất, Số câu hoặc Tỷ lệ; mẫu số theo đúng phạm vi). Mỗi trường có `data-testid`
 * `mapid-rule-*` cố định.
 */
export function MapidRuleDialog(props: MapidRuleDialogProps) {
  const t = useTranslations("exams.mapidRules.dialog");
  return (
    <Dialog open={props.open} onOpenChange={props.onOpenChange}>
      <DialogContent size="lg" closeLabel={t("close")}>
        {props.open && <RuleForm key={props.rule?.id ?? "new"} {...props} />}
      </DialogContent>
    </Dialog>
  );
}

function RuleForm({ section, groups, subjectName, reference, rule, onOpenChange, onSave }: MapidRuleDialogProps) {
  const t = useTranslations("exams.mapidRules.dialog");
  const tContent = useTranslations("content");
  const tGroups = useTranslations("exams.structure.subject.groups");
  const withNumberId = useId();
  const [form, setForm] = useState<MapidRuleForm>(() => (rule ? formFromRule(rule) : emptyRuleForm(draftId("mr"))));
  const [picking, setPicking] = useState(false);
  const view = useMemo(() => ({ ...section, blocks: [...groups] }), [section, groups]);
  const knowledgeIndex = useMemo(() => indexKnowledge(reference.knowledgeTree), [reference]);

  const patch = (change: Partial<MapidRuleForm>) => setForm((current) => ({ ...current, ...change }));
  const refs: KnowledgeRef[] = form.nodeIds.map((nodeId) => {
    const node = knowledgeIndex.get(nodeId)?.node;
    return { nodeId, code: node?.code ?? nodeId, name: node?.name ?? "", primary: false };
  });
  const group = form.groupId ? (groups.find((item) => item.id === form.groupId) ?? null) : null;
  const scopeName = group ? group.name || tGroups("untitled") : subjectName;
  const scopeValue = form.groupId === null ? SUBJECT_SCOPE : group ? group.id : "";
  const units = ruleUnits(view, form);
  const issues = ruleFormIssues(form, view);
  const needsNumber = ruleNeedsNumber(form.type, form.withNumber);

  const sizeText = (block: QuestionBlock): string => {
    const size = groupSize(view, block);
    const total = size.total;
    if (size.units === null) return t("size.remainder");
    if (size.sets !== null && size.size !== null) {
      return total === null
        ? t("size.setsNoTotal", { sets: size.sets, size: size.size, units: size.units })
        : t("size.sets", { sets: size.sets, size: size.size, units: size.units, total });
    }
    return total === null ? t("size.singleNoTotal", { units: size.units }) : t("size.single", { units: size.units, total });
  };
  const typeText = (block: QuestionBlock): string => (block.questionType === null ? tGroups("typeOpen") : tContent(`questionType.${block.questionType}`));

  const scopeOptions = [
    {
      value: SUBJECT_SCOPE,
      label: t("scope.subject"),
      description: section.questionCount === null ? t("scope.subjectHint", { subject: subjectName }) : t("scope.subjectHintCount", { subject: subjectName, total: section.questionCount }),
    },
    ...groups.map((block) => ({
      value: block.id,
      label: block.name || tGroups("untitled"),
      description: t("scope.groupHint", { type: typeText(block), size: sizeText(block) }),
    })),
  ];
  const typeOptions = RULE_TYPES.map((type) => ({ value: type, label: t(`type.${type}.label`), description: t(`type.${type}.description`) }));

  return (
    <>
      <DialogHeader>
        <DialogTitle>{t(rule ? "titleEdit" : "title")}</DialogTitle>
        <DialogDescription>{t("description", { subject: subjectName })}</DialogDescription>
      </DialogHeader>
      <div className="flex max-h-[60vh] flex-col gap-5 overflow-y-auto p-1">
        <div data-testid="mapid-rule-scope">
          <ChoiceCardGroup
            legend={t("scope.legend")}
            name={`${form.id}-scope`}
            value={scopeValue}
            onChange={(value) => patch({ groupId: value === SUBJECT_SCOPE ? null : value })}
            options={scopeOptions}
          />
        </div>

        <div className="flex flex-col gap-2" data-testid="mapid-rule-type">
          <ChoiceCardGroup
            legend={t("type.legend")}
            name={`${form.id}-type`}
            value={form.type}
            onChange={(type) => patch({ type })}
            options={typeOptions}
            hint={t("type.contrast")}
          />
        </div>

        <fieldset className="flex flex-col gap-2">
          <legend className="mb-1.5 text-field-label font-medium text-text-secondary">{t("nodesLegend")}</legend>
          <div className="flex flex-wrap items-center gap-2">
            {refs.length === 0 && <span className="text-body text-text-muted">{t("nodesEmpty")}</span>}
            {refs.map((ref) => (
              <span key={ref.nodeId} className="inline-flex max-w-full items-center gap-1 rounded-full border border-border bg-subtle py-0.5 pr-0.5 pl-3 text-label text-text">
                <span className="min-w-0 wrap-anywhere">
                  <span className="font-semibold">{ref.code}</span> {ref.name}
                </span>
                <IconButton
                  icon={<CircleMinus />}
                  className="size-8"
                  aria-label={t("nodeRemove", { code: ref.code, name: ref.name })}
                  onClick={() => patch({ nodeIds: form.nodeIds.filter((id) => id !== ref.nodeId) })}
                />
              </span>
            ))}
            <Button type="button" variant="secondary" size="sm" data-testid="mapid-rule-mapid-picker" onClick={() => setPicking(true)}>
              <ListTree />
              <span>{t(refs.length === 0 ? "pick" : "pickEdit")}</span>
            </Button>
          </div>
          <p className="text-caption text-text-muted">{t("pickHint")}</p>
        </fieldset>

        {form.type === "ONLY_IN_GROUP" && (
          <div className="flex items-start gap-2">
            <Checkbox id={withNumberId} checked={form.withNumber} onCheckedChange={(checked) => patch({ withNumber: checked === true })} />
            <div className="flex flex-col">
              <label htmlFor={withNumberId} className="cursor-pointer text-body font-medium text-text">
                {t("withNumber")}
              </label>
              <span className="text-caption text-text-muted">{t("withNumberHint")}</span>
            </div>
          </div>
        )}

        {needsNumber && (
          <div className="flex flex-col gap-3 rounded-md border border-border p-3">
            <div className="flex flex-wrap items-start gap-4">
              <div data-testid="mapid-rule-comparator">
                <ChoiceCardGroup<MapidComparator>
                  variant="pill"
                  legend={t("comparator.legend")}
                  name={`${form.id}-comparator`}
                  value={form.comparator}
                  onChange={(comparator) => patch({ comparator })}
                  options={[
                    { value: "EXACT", label: t("comparator.EXACT") },
                    { value: "AT_LEAST", label: t("comparator.AT_LEAST") },
                  ]}
                />
              </div>
              <ChoiceCardGroup<MapidRuleForm["entry"]>
                variant="pill"
                legend={t("entry")}
                name={`${form.id}-entry`}
                value={form.entry}
                onChange={(entry) => patch({ entry })}
                options={[
                  { value: "COUNT", label: t("entryCount") },
                  { value: "PERCENT", label: t("entryPercent") },
                ]}
              />
            </div>
            <div className="flex flex-wrap items-end gap-3">
              {form.entry === "COUNT" ? (
                <FormField label={t("count")}>
                  {(controlProps) => (
                    <BlockNumberInput
                      {...controlProps}
                      data-testid="mapid-rule-count"
                      className="w-24"
                      aria-label={t("countAria")}
                      value={form.count}
                      onValueChange={(count) => patch({ count })}
                    />
                  )}
                </FormField>
              ) : (
                <FormField label={t("percent")}>
                  {(controlProps) => (
                    <BlockNumberInput
                      {...controlProps}
                      data-testid="mapid-rule-percent"
                      className="w-24"
                      aria-label={t("percentAria")}
                      allowDecimal
                      value={form.percent}
                      onValueChange={(percent) => patch({ percent })}
                    />
                  )}
                </FormField>
              )}
              <p className="flex flex-col pb-1 text-caption text-text-muted">
                <span>{units === null ? t("denominatorUnknown", { scope: scopeName }) : t("denominator", { units, scope: scopeName })}</span>
                {form.entry === "PERCENT" && (
                  <span data-testid="mapid-rule-percent-result">
                    {units === null ? t("percentUnknown") : t("percentResult", { count: ruleCount(view, { id: form.id, type: "COUNT", groupId: form.groupId, nodeIds: [], percent: form.percent }), units })}
                  </span>
                )}
              </p>
            </div>
          </div>
        )}
        {issues.length > 0 && (
          <p role="status" className="text-caption text-text-muted">
            {t(`issues.${issues[0]!}`)}
          </p>
        )}
      </div>
      <DialogFooter>
        <Button type="button" variant="secondary" onClick={() => onOpenChange(false)}>
          {t("cancel")}
        </Button>
        <Button type="button" data-testid="mapid-rule-save" disabled={issues.length > 0} onClick={() => onSave(ruleFromForm(form, view))}>
          {t("save")}
        </Button>
      </DialogFooter>
      <KnowledgePickerDialog
        open={picking}
        onOpenChange={setPicking}
        value={refs}
        selectableLevels={["CHAPTER", "LESSON", "KNOWLEDGE"]}
        withPrimary={false}
        defaultSubjectCode={section.subjectCode}
        description={t("pickerDescription", { name: scopeName })}
        onApply={(next) => {
          patch({ nodeIds: next.map((ref) => ref.nodeId) });
          setPicking(false);
        }}
      />
    </>
  );
}
