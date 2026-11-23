"use client";

import {
  Button,
  Checkbox,
  cn,
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
  focusRing,
  FormField,
  IconButton,
  Select,
} from "@thi360/ui";
import { CircleMinus, ListTree, Replace, TriangleAlert } from "lucide-react";
import { useFormatter, useTranslations } from "next-intl";
import { useEffect, useId, useMemo, useRef, useState } from "react";
import { KnowledgePickerDialog } from "@/features/content/components/knowledge-picker-dialog";
import { indexKnowledge } from "@/features/content/knowledge-tree";
import type { ContentReference, KnowledgeRef } from "@/features/content/types";
import { countFromPercent } from "../allocation";
import { checkMapidRuleConflicts } from "../mapid-rule-form-conflicts";
import {
  emptyRuleForm,
  formFromRule,
  groupSize,
  type MapidRuleForm,
  ruleCount,
  ruleFormIssues,
  ruleFromForm,
  type RuleIssue,
  ruleNeedsNumber,
  ruleUnits,
} from "../mapid-rules";
import type { ExamSection, MapidComparator, MapidRule, MapidRuleType, QuestionBlock } from "../types";
import { BlockNumberInput } from "./block-number-input";
import { ChoiceCardGroup } from "./create-exam-fields";
import { ExamContentText, markExamContent } from "./exam-content-text";
import { draftId } from "./exam-structure-draft";
import { useMapidRuleText } from "./mapid-rules-table";

const SUBJECT_SCOPE = "__subject__";
const ALL_RULE_TYPES: readonly MapidRuleType[] = ["PRESENT", "COUNT", "ONLY_IN_GROUP", "SOURCE"];
const SUBJECT_RULE_TYPES: readonly MapidRuleType[] = ["PRESENT", "COUNT", "SOURCE"];

interface MapidRuleDialogProps {
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
  onSave: (rule: MapidRule, options?: { replaceIds?: readonly string[] }) => void;
  /** Chuyển hộp thoại sang sửa một quy tắc hiện có (khi phát hiện trùng, bao hàm hoặc xung đột). */
  onOpenRule?: (ruleId: string) => void;
}

/**
 * Hộp thoại "Thêm / Sửa quy tắc" của bảng Quy tắc MAPID (UNI-1707): bố cục 2 cột trên màn rộng (trái: Áp dụng cho + Loại quy tắc;
 * phải: MAPID + Điều kiện + Tóm tắt + Kiểm tra chéo 3 chiều) và 1 cột trên màn hẹp, dùng duy nhất thanh cuộn của `DialogContent`.
 */
export function MapidRuleDialog(props: MapidRuleDialogProps) {
  const t = useTranslations("exams.mapidRules.dialog");
  const contentRef = useRef<HTMLDivElement>(null);
  const prevRuleKeyRef = useRef<string | null>(null);
  const currentKey = props.open ? (props.rule?.id ?? "new") : null;

  useEffect(() => {
    const prevKey = prevRuleKeyRef.current;
    prevRuleKeyRef.current = currentKey;
    if (prevKey !== null && currentKey !== null && prevKey !== currentKey) {
      contentRef.current?.querySelector<HTMLElement>('[data-testid="mapid-rule-scope"] [role="combobox"]')?.focus();
    }
  }, [currentKey]);

  return (
    <Dialog open={props.open} onOpenChange={props.onOpenChange}>
      <DialogContent ref={contentRef} size="xl" closeLabel={t("close")}>
        {props.open && <RuleForm key={props.rule?.id ?? "new"} {...props} />}
      </DialogContent>
    </Dialog>
  );
}

function RuleForm({ section, groups, subjectName, reference, rule, onOpenChange, onSave, onOpenRule }: MapidRuleDialogProps) {
  const t = useTranslations("exams.mapidRules.dialog");
  const tContent = useTranslations("content");
  const tGroups = useTranslations("exams.structure.subject.groups");
  const format = useFormatter();
  const ruleText = useMapidRuleText(reference);

  const withNumberId = useId();
  const typeErrorId = useId();
  const nodesErrorId = useId();
  const conditionErrorId = useId();
  const crossCheckId = useId();

  const [form, setForm] = useState<MapidRuleForm>(() => (rule ? formFromRule(rule) : emptyRuleForm(draftId("mr"))));
  const [picking, setPicking] = useState(false);
  /** Ô số đang giữ chữ sai định dạng: `BlockNumberInput` giữ giá trị hợp lệ cuối nên cần khóa nút Lưu (R1-F7). */
  const [numberInvalid, setNumberInvalid] = useState(false);
  /** Ghi nhớ việc bỏ chọn `ONLY_IN_GROUP` khi chuyển từ một nhóm sang Toàn môn để hiển thị thông báo phù hợp. */
  const [typeClearedForSubject, setTypeClearedForSubject] = useState(false);

  const view = useMemo(() => ({ ...section, blocks: [...groups] }), [section, groups]);
  const knowledgeIndex = useMemo(() => indexKnowledge(reference.knowledgeTree), [reference]);

  const patch = (change: Partial<MapidRuleForm>) => {
    if ("type" in change || "entry" in change || "withNumber" in change) setNumberInvalid(false);
    setForm((current) => ({ ...current, ...change }));
  };

  const handleScopeChange = (value: string) => {
    const nextGroupId = value === SUBJECT_SCOPE ? null : value;
    if (nextGroupId === null && form.type === "ONLY_IN_GROUP") {
      setNumberInvalid(false);
      setTypeClearedForSubject(true);
      setForm((current) => ({ ...current, groupId: null, type: null, withNumber: false }));
      return;
    }
    if (nextGroupId !== null) setTypeClearedForSubject(false);
    patch({ groupId: nextGroupId });
  };

  const handleTypeChange = (type: MapidRuleType) => {
    setTypeClearedForSubject(false);
    patch({ type });
  };

  const refs: KnowledgeRef[] = form.nodeIds.map((nodeId) => {
    const node = knowledgeIndex.get(nodeId)?.node;
    return { nodeId, code: node?.code ?? nodeId, name: node?.name ?? "", primary: false };
  });

  const group = form.groupId ? (groups.find((item) => item.id === form.groupId) ?? null) : null;
  const groupGone = form.groupId !== null && !group;
  const scopeName = group ? group.name || tGroups("untitled") : subjectName;
  const markedScopeName = group?.name ? markExamContent(scopeName) : scopeName;
  const scopeValue = form.groupId === null ? SUBJECT_SCOPE : group ? group.id : "";
  const units = ruleUnits(view, form);
  const issues = ruleFormIssues(form, view);
  const needsNumber = ruleNeedsNumber(form.type, form.withNumber);

  const typeText = (block: QuestionBlock): string =>
    block.questionType === null ? tGroups("typeOpen") : tContent(`questionType.${block.questionType}`);

  const scopeOptionLabel = (block: QuestionBlock): string => {
    const name = block.name || tGroups("untitled");
    const size = groupSize(view, block);
    if (size.remainder) {
      return size.units === null ? t("scope.groupRemainder", { name }) : t("scope.groupRemainderUnits", { name, units: size.units });
    }
    if (size.sets !== null && size.size !== null) {
      return t("scope.groupSets", { name, sets: size.sets, size: size.size });
    }
    if (size.units !== null) {
      return t("scope.groupSingle", { name, units: size.units });
    }
    return t("scope.groupUnknown", { name });
  };

  const scopeOptions = [
    {
      value: SUBJECT_SCOPE,
      label: section.questionCount === null ? t("scope.subject") : t("scope.subjectCount", { total: section.questionCount }),
    },
    ...groups
      .filter((block) => block.omitted !== true || block.id === form.groupId)
      .map((block) => ({
        value: block.id,
        label: scopeOptionLabel(block),
      })),
  ];

  const scopeHint = groupGone
    ? undefined
    : group
      ? (() => {
          const size = groupSize(view, group);
          if (size.units !== null && size.total !== null) {
            return t("scope.groupHint", { type: typeText(group), units: size.units, total: size.total });
          }
          if (size.units !== null) {
            return t("scope.groupHintNoTotal", { type: typeText(group), units: size.units });
          }
          return t("scope.groupHintUnknown", { type: typeText(group) });
        })()
      : section.questionCount === null
        ? t("scope.subjectHint", { subject: subjectName })
        : t("scope.subjectHintCount", { subject: subjectName, total: section.questionCount });

  const visibleTypes = form.groupId === null ? SUBJECT_RULE_TYPES : ALL_RULE_TYPES;

  const blockingNumberIssue = issues.find(
    (item): item is Extract<RuleIssue, "PERCENT_RANGE" | "PERCENT_POOL" | "PERCENT_DENOMINATOR_UNKNOWN" | "COUNT_ABOVE_GROUP" | "COUNT_ABOVE_TOTAL"> =>
      item === "PERCENT_RANGE" ||
      item === "PERCENT_POOL" ||
      item === "PERCENT_DENOMINATOR_UNKNOWN" ||
      item === "COUNT_ABOVE_GROUP" ||
      item === "COUNT_ABOVE_TOTAL",
  );

  const conditionError = !needsNumber
    ? undefined
    : numberInvalid
      ? t("issues.NUMBER_INVALID")
      : blockingNumberIssue === "COUNT_ABOVE_GROUP"
        ? t("issues.COUNT_ABOVE_GROUP", { units: units ?? 0, scope: markedScopeName })
        : blockingNumberIssue
          ? t(`issues.${blockingNumberIssue}`, { total: section.questionCount ?? 0 })
          : undefined;

  const canEvaluateCrossRow =
    form.type !== null &&
    form.nodeIds.length > 0 &&
    !groupGone &&
    !(form.type === "ONLY_IN_GROUP" && !form.groupId) &&
    !numberInvalid &&
    !issues.includes("PERCENT_RANGE") &&
    !issues.includes("PERCENT_POOL") &&
    !issues.includes("PERCENT_DENOMINATOR_UNKNOWN") &&
    !issues.includes("COUNT_ABOVE_GROUP") &&
    !issues.includes("COUNT_ABOVE_TOTAL");

  const crossCheck = useMemo(() => {
    if (!canEvaluateCrossRow) return { kind: "OK" } as const;
    return checkMapidRuleConflicts({
      candidate: ruleFromForm(form, view),
      section: view,
      knowledgeIndex,
    });
  }, [canEvaluateCrossRow, form, view, knowledgeIndex]);

  const capacityHandledByField =
    crossCheck.kind === "CONFLICT" &&
    crossCheck.code === "CAPACITY_EXCEEDED" &&
    (issues.includes("COUNT_ABOVE_GROUP") || issues.includes("COUNT_ABOVE_TOTAL"));

  const showCrossCheck = crossCheck.kind !== "OK" && !capacityHandledByField;

  const summaryText = (() => {
    if (groupGone) return t("summary.pendingScope");
    if (form.type === null) return t("summary.pendingType");
    if (form.nodeIds.length === 0) return t("summary.pendingNodes");
    if (needsNumber && (numberInvalid || issues.includes("PERCENT_RANGE"))) return t("summary.pendingCondition");
    const topics = ruleText.topics(form.nodeIds, true);
    const scopePhrase =
      form.groupId === null
        ? section.questionCount === null
          ? t("summary.scopeSubject")
          : t("summary.scopeSubjectCount", { total: section.questionCount })
        : units === null
          ? t("summary.scopeGroupUnknown", { group: markedScopeName })
          : t("summary.scopeGroup", { units, group: markedScopeName });

    if (form.type === "PRESENT") return t("summary.PRESENT", { scopePhrase, topics });
    if (form.type === "SOURCE") {
      return form.groupId === null
        ? t("summary.SOURCE_SUBJECT", { topics })
        : t("summary.SOURCE_GROUP", { group: markedScopeName, topics });
    }

    const resolvedCount =
      form.entry === "COUNT" ? form.count : units !== null ? countFromPercent(form.percent, units) : null;
    const cmp = form.comparator;

    if (form.type === "ONLY_IN_GROUP") {
      if (!form.withNumber) return t("summary.ONLY_IN_GROUP", { topics, group: markedScopeName });
      if (form.entry === "PERCENT") {
        return resolvedCount === null
          ? t(`summary.ONLY_IN_GROUP_${cmp}_PERCENT_UNKNOWN`, { topics, group: markedScopeName, percent: form.percent })
          : t(`summary.ONLY_IN_GROUP_${cmp}_PERCENT`, {
              topics,
              group: markedScopeName,
              count: resolvedCount,
              percent: form.percent,
            });
      }
      return t(`summary.ONLY_IN_GROUP_${cmp}`, { topics, group: markedScopeName, count: form.count });
    }

    if (form.entry === "PERCENT") {
      return resolvedCount === null
        ? t(`summary.COUNT_${cmp}_PERCENT_UNKNOWN`, { scopePhrase, percent: form.percent, topics })
        : t(`summary.COUNT_${cmp}_PERCENT`, { scopePhrase, count: resolvedCount, percent: form.percent, topics });
    }
    return t(`summary.COUNT_${cmp}`, { scopePhrase, count: form.count, topics });
  })();

  const crossCheckMessage = (() => {
    if (!showCrossCheck) return "";
    if (crossCheck.kind === "EQUIVALENT") {
      return t("conflict.EQUIVALENT", {
        scope: ruleText.scopeLabel(groups, form.groupId),
        topics: ruleText.topics(form.nodeIds, true),
        rule: ruleText.sentence(view, crossCheck.relatedRules[0]!, true),
      });
    }
    if (crossCheck.kind === "SUBSUMED_BY") {
      return t("conflict.SUBSUMED_BY", {
        rule: ruleText.sentence(view, crossCheck.relatedRules[0]!, true),
      });
    }
    if (crossCheck.kind === "SUBSUMES") {
      const rulesList = format.list(
        crossCheck.relatedRules.map((item) => ruleText.sentence(view, item, true)),
        { type: "conjunction" },
      );
      const key =
        crossCheck.reason === "EXACT_COVERS_PRESENT"
          ? "SUBSUMES_EXACT_PRESENT"
          : crossCheck.reason === "AT_LEAST_COVERS_PRESENT"
            ? "SUBSUMES_AT_LEAST_PRESENT"
            : crossCheck.reason === "COVERS_PRESENT"
              ? "SUBSUMES_PRESENT"
              : crossCheck.reason === "EXACT_COVERS_RULE"
                ? "SUBSUMES_EXACT_RULE"
                : crossCheck.reason === "AT_LEAST_COVERS_RULE"
                  ? "SUBSUMES_AT_LEAST_RULE"
                  : crossCheck.reason === "ONLY_IN_GROUP_COVERS_UNNUMBERED"
                    ? "SUBSUMES_ONLY_IN_GROUP"
                    : "SUBSUMES_RULE";
      return t(`conflict.${key}`, { count: crossCheck.count ?? 0, rules: rulesList });
    }

    const firstRelated = crossCheck.relatedRules[0];
    const relatedSentence = firstRelated ? ruleText.sentence(view, firstRelated, true) : "";
    const rulesList = format.list(
      crossCheck.relatedRules.map((item) => ruleText.sentence(view, item, true)),
      { type: "conjunction" },
    );
    const topics = ruleText.topics(crossCheck.nodeIds, true);
    const scope = ruleText.scopeLabel(groups, crossCheck.scopeGroupId);
    const onlyGroup = crossCheck.onlyGroupId !== undefined ? ruleText.scopeLabel(groups, crossCheck.onlyGroupId) : scope;
    const otherGroup = crossCheck.otherGroupId !== undefined ? ruleText.scopeLabel(groups, crossCheck.otherGroupId) : scope;
    if (crossCheck.code === "CAPACITY_EXCEEDED") {
      return t(crossCheck.scopeGroupId === null ? "conflict.CAPACITY_EXCEEDED_SUBJECT" : "conflict.CAPACITY_EXCEEDED_GROUP", {
        scope,
        required: crossCheck.required ?? 0,
        available: crossCheck.available ?? 0,
        topics,
      });
    }
    return t(`conflict.${crossCheck.code}`, {
      scope,
      onlyGroup,
      otherGroup,
      topics,
      rule: relatedSentence,
      rules: rulesList,
      required: crossCheck.required ?? 0,
      available: crossCheck.available ?? 0,
    });
  })();

  const isBlockingCrossCheck =
    crossCheck.kind === "CONFLICT" || crossCheck.kind === "EQUIVALENT" || crossCheck.kind === "SUBSUMED_BY";
  const canSave = !numberInvalid && issues.length === 0 && !isBlockingCrossCheck;
  const saveDescribedBy = [
    form.type === null && typeErrorId,
    form.nodeIds.length === 0 && nodesErrorId,
    conditionError && conditionErrorId,
    showCrossCheck && isBlockingCrossCheck && crossCheckId,
  ]
    .filter(Boolean)
    .join(" ");

  return (
    <>
      <DialogHeader>
        <DialogTitle>{t(rule ? "titleEdit" : "title")}</DialogTitle>
        <DialogDescription>{t("description", { subject: subjectName })}</DialogDescription>
      </DialogHeader>

      <div className="grid grid-cols-1 gap-5 p-1 md:grid-cols-2 md:items-start">
        <div className="flex min-w-0 flex-col gap-4">
          <div data-testid="mapid-rule-scope">
            <FormField label={t("scope.legend")} hint={scopeHint} error={groupGone ? t("issues.GROUP_MISSING") : undefined}>
              {(controlProps) => (
                <Select
                  {...controlProps}
                  name={`${form.id}-scope`}
                  value={scopeValue}
                  placeholder={
                    groupGone
                      ? rule?.deletedGroupName
                        ? t("scope.deletedNamed", { name: rule.deletedGroupName })
                        : t("scope.deletedUnnamed")
                      : undefined
                  }
                  onValueChange={handleScopeChange}
                  options={scopeOptions}
                />
              )}
            </FormField>
          </div>

          <div className="flex min-w-0 flex-col gap-1.5">
            <fieldset
              data-testid="mapid-rule-type"
              aria-describedby={form.type === null ? typeErrorId : undefined}
              className="flex min-w-0 flex-col gap-2"
            >
              <legend className="mb-1.5 text-field-label font-medium text-text-secondary">{t("type.legend")}</legend>
              <div className="flex flex-col gap-2">
                {visibleTypes.map((type) => {
                  const checked = form.type === type;
                  return (
                    <label
                      key={type}
                      data-testid={`mapid-rule-type-${type}`}
                      className={cn(
                        "flex min-w-0 cursor-pointer items-start gap-2.5 rounded-md border p-3 transition-colors duration-150 motion-reduce:transition-none",
                        checked ? "border-primary-text bg-primary-50" : "border-border bg-surface hover:border-border-strong",
                      )}
                    >
                      <input
                        type="radio"
                        name={`${form.id}-type`}
                        value={type}
                        checked={checked}
                        onChange={() => handleTypeChange(type)}
                        className={cn("mt-0.5 size-4 shrink-0 accent-primary-fill", focusRing)}
                      />
                      <span className="flex min-w-0 flex-col gap-0.5">
                        <span className={cn("font-semibold", checked ? "text-primary-text" : "text-text")}>{t(`type.${type}.label`)}</span>
                        {checked && <span className="text-caption text-text-muted">{t(`type.${type}.description`)}</span>}
                      </span>
                    </label>
                  );
                })}
              </div>
            </fieldset>
            {form.type === null && (
              <p id={typeErrorId} role="alert" className="text-caption text-danger">
                {t(typeClearedForSubject ? "issues.TYPE_CLEARED_FOR_SUBJECT" : "issues.TYPE_REQUIRED")}
              </p>
            )}
          </div>
        </div>

        <div className="flex min-w-0 flex-col gap-4">
          <div className="flex min-w-0 flex-col gap-1.5">
            <fieldset aria-describedby={form.nodeIds.length === 0 ? nodesErrorId : undefined} className="flex min-w-0 flex-col gap-2">
              <legend className="mb-1.5 text-field-label font-medium text-text-secondary">{t("nodesLegend")}</legend>
              <div className="flex flex-wrap items-center gap-2">
                {refs.length === 0 && <span className="text-body text-text-muted">{t("nodesEmpty")}</span>}
                {refs.map((ref) => (
                  <span
                    key={ref.nodeId}
                    className="inline-flex max-w-full items-center gap-1 rounded-full border border-border bg-subtle py-0.5 pr-0.5 pl-3 text-label text-text"
                  >
                    <span className="min-w-0 wrap-anywhere">
                      <span className="font-semibold">{ref.code}</span>
                      {ref.name && (
                        <>
                          {" "}
                          <span data-exam-content>{ref.name}</span>
                        </>
                      )}
                    </span>
                    <IconButton
                      icon={<CircleMinus />}
                      className="relative size-8 before:pointer-coarse:absolute before:pointer-coarse:-inset-1.5"
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
              <p className="text-caption text-text-muted">{t(form.type === "SOURCE" ? "pickHintSource" : "pickHint")}</p>
            </fieldset>
            {form.nodeIds.length === 0 && (
              <p id={nodesErrorId} role="alert" className="text-caption text-danger">
                {t("issues.NO_NODES")}
              </p>
            )}
          </div>

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
                        key={`${form.type}-${form.entry}`}
                        data-testid="mapid-rule-count"
                        className="w-24"
                        aria-label={t("countAria")}
                        aria-invalid={conditionError ? true : undefined}
                        aria-describedby={conditionError ? conditionErrorId : undefined}
                        value={form.count}
                        onValueChange={(count) => patch({ count })}
                        onInvalidChange={setNumberInvalid}
                      />
                    )}
                  </FormField>
                ) : (
                  <FormField label={t("percent")}>
                    {(controlProps) => (
                      <BlockNumberInput
                        {...controlProps}
                        key={`${form.type}-${form.entry}`}
                        data-testid="mapid-rule-percent"
                        className="w-24"
                        aria-label={t("percentAria")}
                        aria-invalid={conditionError ? true : undefined}
                        aria-describedby={conditionError ? conditionErrorId : undefined}
                        allowDecimal
                        value={form.percent}
                        onValueChange={(percent) => patch({ percent })}
                        onInvalidChange={setNumberInvalid}
                      />
                    )}
                  </FormField>
                )}
                <p className="flex flex-col pb-1 text-caption text-text-muted">
                  <span>
                    <ExamContentText
                      text={
                        units === null
                          ? t("denominatorUnknown", { scope: markedScopeName })
                          : t("denominator", { units, scope: markedScopeName })
                      }
                    />
                  </span>
                  {form.entry === "PERCENT" && (
                    <span data-testid="mapid-rule-percent-result">
                      {units === null
                        ? t("percentUnknown")
                        : t("percentResult", {
                            count: ruleCount(view, { id: form.id, type: "COUNT", groupId: form.groupId, nodeIds: [], percent: form.percent }),
                            units,
                          })}
                    </span>
                  )}
                </p>
              </div>
              {conditionError && (
                <p id={conditionErrorId} role="alert" className="text-caption text-danger">
                  <ExamContentText text={conditionError} />
                </p>
              )}
            </div>
          )}

          <div data-testid="mapid-rule-summary" className="min-w-0 max-w-full rounded-md border border-border bg-subtle px-3 py-2.5">
            <p className="text-caption font-medium text-text-secondary">{t("summary.label")}</p>
            <p className="mt-0.5 text-body text-text wrap-anywhere">
              <ExamContentText text={summaryText} />
            </p>
          </div>

          {showCrossCheck && (
            <div
              id={crossCheckId}
              data-testid="mapid-rule-cross-check"
              role={isBlockingCrossCheck ? "alert" : "note"}
              className={cn(
                "flex min-w-0 max-w-full flex-col gap-2.5 rounded-md border px-3 py-2.5 text-body text-text",
                isBlockingCrossCheck ? "border-danger/40 bg-danger-bg" : "border-warning/40 bg-warning-bg",
              )}
            >
              <div className="flex min-w-0 items-start gap-2">
                <TriangleAlert aria-hidden className={cn("mt-0.5 size-4 shrink-0", isBlockingCrossCheck ? "text-danger" : "text-warning")} />
                <p className="min-w-0 flex-1 wrap-anywhere">
                  <ExamContentText text={crossCheckMessage} />
                </p>
              </div>
              {(crossCheck.kind === "SUBSUMES" || (onOpenRule && crossCheck.relatedRules.length > 0)) && (
                <div className="flex min-w-0 max-w-full flex-wrap gap-2">
                  {crossCheck.kind === "SUBSUMES" && (
                    <Button
                      type="button"
                      variant="outline"
                      size="sm"
                      disabled={!canSave}
                      className="h-auto min-h-9 min-w-0 max-w-full whitespace-normal py-1.5 text-left [&>span]:min-w-0 [&>span]:max-w-full"
                      data-testid="mapid-rule-replace"
                      onClick={() => onSave(ruleFromForm(form, view), { replaceIds: crossCheck.relatedRules.map((item) => item.id) })}
                    >
                      <Replace />
                      <span className="min-w-0 wrap-anywhere">{t("conflict.replaceRule")}</span>
                    </Button>
                  )}
                  {onOpenRule &&
                    crossCheck.relatedRules.map((related) => (
                      <Button
                        key={related.id}
                        type="button"
                        variant="secondary"
                        size="sm"
                        className="h-auto min-h-9 min-w-0 max-w-full whitespace-normal py-1.5 text-left [&>span]:min-w-0 [&>span]:max-w-full"
                        data-testid={`mapid-rule-open-${related.id}`}
                        aria-label={t("conflict.openRuleNamed", { rule: ruleText.sentence(view, related, false) })}
                        onClick={() => onOpenRule(related.id)}
                      >
                        <span className="min-w-0 wrap-anywhere">
                          {crossCheck.relatedRules.length === 1 ? (
                            t("conflict.openRule")
                          ) : (
                            <ExamContentText text={t("conflict.openRuleNamed", { rule: ruleText.sentence(view, related, true) })} />
                          )}
                        </span>
                      </Button>
                    ))}
                </div>
              )}
            </div>
          )}
        </div>
      </div>

      <DialogFooter>
        <Button type="button" variant="secondary" onClick={() => onOpenChange(false)}>
          {t("cancel")}
        </Button>
        <Button
          type="button"
          data-testid="mapid-rule-save"
          disabled={!canSave}
          aria-describedby={saveDescribedBy || undefined}
          onClick={() => onSave(ruleFromForm(form, view))}
        >
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
