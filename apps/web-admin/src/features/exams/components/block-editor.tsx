"use client";

import {
  Button,
  Card,
  ConfirmDialog,
  FormField,
  IconButton,
  Input,
  Label,
  Select,
  Switch,
} from "@thi360/ui";
import { ArrowDown, ArrowUp, CircleMinus, ListTree, Trash2 } from "lucide-react";
import { useTranslations } from "next-intl";
import { useId, useMemo, useState } from "react";
import { KnowledgePickerDialog } from "@/features/content/components/knowledge-picker-dialog";
import { indexKnowledge } from "@/features/content/knowledge-tree";
import { QUESTION_TYPES, type ContentReference, type KnowledgeRef, type QuestionType } from "@/features/content/types";
import type { MatrixAxes } from "../matrix";
import { validateBlock } from "../matrix";
import { useAvailableQuestions } from "../queries";
import type { BlockShuffleRule, QuestionBlock } from "../types";
import { BlockMatrixEditor, type MatrixLabels } from "./block-matrix-editor";
import { BlockNumberInput } from "./block-number-input";
import { ChoiceCardGroup } from "./create-exam-fields";

const SHUFFLE_RULES: BlockShuffleRule[] = ["SHUFFLE", "KEEP_ORDER"];

export interface BlockEditorProps {
  block: QuestionBlock;
  /** Môn của khối (kế thừa từ phần/môn đang cấu hình). */
  subjectCode: string;
  /** "Phần 1 · Toán · khối 2 / 2". */
  locationText: string;
  position: number;
  siblingCount: number;
  axes: MatrixAxes;
  labels: MatrixLabels;
  reference: ContentReference | undefined;
  onChange: (block: QuestionBlock) => void;
  onMove: (offset: -1 | 1) => void;
  onRemove: () => void;
}

/**
 * Một khối câu hỏi (bản vẽ `BaiThiCauTruc`, UniNote PBI-04 mục 6.1): tên, dạng câu, số câu (câu chùm: số bộ × số câu
 * mỗi bộ), điểm mỗi câu, quy tắc đảo, phạm vi kiến thức (MAPID, khớp ít nhất một/tất cả, có lấy mã con), rồi ma trận.
 */
export function BlockEditor({
  block,
  subjectCode,
  locationText,
  position,
  siblingCount,
  axes,
  labels,
  reference,
  onChange,
  onMove,
  onRemove,
}: BlockEditorProps) {
  const t = useTranslations("exams.structure.block");
  const tContent = useTranslations("content");
  const titleId = useId();
  const descendantsId = useId();
  const descendantsHintId = useId();
  const [pickerOpen, setPickerOpen] = useState(false);
  const [confirmRemove, setConfirmRemove] = useState(false);

  const knowledgeIndex = useMemo(() => indexKnowledge(reference?.knowledgeTree ?? []), [reference]);
  const scopeRefs: KnowledgeRef[] = block.knowledgeScope.nodeIds.map((nodeId) => {
    const node = knowledgeIndex.get(nodeId)?.node;
    return { nodeId, code: node?.code ?? nodeId, name: node?.name ?? "", primary: false };
  });

  const availableQuery = useAvailableQuestions({
    subjectCode,
    questionType: block.questionType,
    knowledgeScope: block.knowledgeScope,
  });
  const availableState = availableQuery.isError ? "error" : availableQuery.data ? "ready" : "loading";

  const issues = validateBlock(block, axes);
  const countError = issues.some((issue) => issue.code === "COUNT_NOT_POSITIVE") ? t("errors.countPositive") : undefined;
  const setIssue = issues.find((issue) => issue.code === "SET_SIZE_MISMATCH");

  const update = (patch: Partial<QuestionBlock>) => onChange({ ...block, ...patch });
  const setType = (questionType: QuestionType) => {
    if (questionType === "QUESTION_SET") {
      const setSize = Math.max(block.questionCount, 1);
      update({ questionType, setCount: 1, setSize, questionCount: setSize });
    } else {
      update({ questionType, setCount: null, setSize: null });
    }
  };
  const setSets = (setCount: number, setSize: number) =>
    update({ setCount, setSize, questionCount: setCount * setSize });

  const isSet = block.questionType === "QUESTION_SET";

  return (
    <Card padding="md" className="flex flex-col gap-5 sm:p-6">
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div className="flex min-w-0 flex-col gap-0.5">
          <h2 id={titleId} className="text-h3 font-bold wrap-break-word text-text">
            {t("title", { name: block.name || t("untitled") })}
          </h2>
          <p className="text-caption text-text-muted">{locationText}</p>
        </div>
        <div className="flex items-center gap-0.5">
          <IconButton
            icon={<ArrowUp />}
            aria-label={t("moveUp", { name: block.name })}
            disabled={position <= 1}
            onClick={() => onMove(-1)}
          />
          <IconButton
            icon={<ArrowDown />}
            aria-label={t("moveDown", { name: block.name })}
            disabled={position >= siblingCount}
            onClick={() => onMove(1)}
          />
          <IconButton
            icon={<Trash2 />}
            tone="danger"
            aria-label={t("remove", { name: block.name })}
            onClick={() => setConfirmRemove(true)}
          />
        </div>
      </div>

      <div className="grid grid-cols-[repeat(auto-fill,minmax(min(14rem,100%),1fr))] gap-4" aria-labelledby={titleId} role="group">
        <FormField label={t("name")} required error={block.name.trim() ? undefined : t("errors.nameRequired")}>
          {(controlProps) => (
            <Input
              {...controlProps}
              value={block.name}
              autoComplete="off"
              maxLength={200}
              onChange={(event) => update({ name: event.target.value })}
            />
          )}
        </FormField>
        <FormField label={t("questionType")} required>
          {(controlProps) => (
            <Select
              {...controlProps}
              value={block.questionType}
              onValueChange={(value) => setType(value as QuestionType)}
              options={QUESTION_TYPES.map((type) => ({ value: type, label: tContent(`questionType.${type}`) }))}
            />
          )}
        </FormField>
        {isSet ? (
          <>
            <FormField label={t("setCount")} required>
              {(controlProps) => (
                <BlockNumberInput
                  {...controlProps}
                  value={block.setCount ?? 0}
                  onValueChange={(value) => setSets(value, block.setSize ?? 0)}
                />
              )}
            </FormField>
            <FormField label={t("setSize")} required hint={t("setHint", { count: block.questionCount })}>
              {(controlProps) => (
                <BlockNumberInput
                  {...controlProps}
                  value={block.setSize ?? 0}
                  onValueChange={(value) => setSets(block.setCount ?? 0, value)}
                />
              )}
            </FormField>
          </>
        ) : (
          <FormField label={t("questionCount")} required hint={t("questionCountHint")} error={countError}>
            {(controlProps) => (
              <BlockNumberInput
                {...controlProps}
                value={block.questionCount}
                onValueChange={(questionCount) => update({ questionCount })}
              />
            )}
          </FormField>
        )}
        <FormField label={t("points")} required>
          {(controlProps) => (
            <BlockNumberInput
              {...controlProps}
              allowDecimal
              value={block.pointsPerQuestion}
              onValueChange={(pointsPerQuestion) => update({ pointsPerQuestion })}
            />
          )}
        </FormField>
        <FormField label={t("shuffle")} required>
          {(controlProps) => (
            <Select
              {...controlProps}
              value={block.shuffle ?? "SHUFFLE"}
              onValueChange={(value) => update({ shuffle: value as BlockShuffleRule })}
              options={SHUFFLE_RULES.map((rule) => ({ value: rule, label: t(`shuffleRules.${rule}`) }))}
            />
          )}
        </FormField>
      </div>
      {!isSet && <p className="text-caption text-text-muted">{t("setNotApplicable")}</p>}
      {isSet && countError && <p className="text-caption text-danger">{countError}</p>}
      {setIssue && setIssue.code === "SET_SIZE_MISMATCH" && (
        <p className="text-caption text-danger">{t("errors.setMismatch", { expected: setIssue.expected })}</p>
      )}

      <fieldset className="flex flex-col gap-3 rounded-md border border-border p-4">
        <legend className="px-1 text-label font-semibold text-text">{t("scope")}</legend>
        <div className="flex flex-wrap items-center gap-2">
          {scopeRefs.length === 0 && <span className="text-body text-text-muted">{t("scopeNone")}</span>}
          {scopeRefs.map((ref) => (
            <span
              key={ref.nodeId}
              className="inline-flex max-w-full items-center gap-1 rounded-full border border-border bg-subtle py-0.5 pr-0.5 pl-3 text-label text-text"
            >
              <span className="min-w-0 wrap-anywhere">
                <span className="font-semibold">{ref.code}</span> {ref.name}
              </span>
              <IconButton
                icon={<CircleMinus />}
                className="size-8"
                aria-label={t("scopeRemove", { code: ref.code, name: ref.name })}
                onClick={() =>
                  update({
                    knowledgeScope: {
                      ...block.knowledgeScope,
                      nodeIds: block.knowledgeScope.nodeIds.filter((id) => id !== ref.nodeId),
                    },
                  })
                }
              />
            </span>
          ))}
          <Button type="button" variant="secondary" size="sm" onClick={() => setPickerOpen(true)}>
            <ListTree />
            <span>{t("scopePick")}</span>
          </Button>
        </div>
        <ChoiceCardGroup
          variant="pill"
          legend={t("match")}
          name={`${block.id}-match`}
          value={block.knowledgeScope.match}
          onChange={(match) => update({ knowledgeScope: { ...block.knowledgeScope, match } })}
          options={[
            { value: "ANY", label: t("matchAny") },
            { value: "ALL", label: t("matchAll") },
          ]}
        />
        <div className="flex items-start gap-3">
          <Switch
            id={descendantsId}
            checked={block.knowledgeScope.includeDescendants}
            aria-describedby={descendantsHintId}
            onCheckedChange={(includeDescendants) =>
              update({ knowledgeScope: { ...block.knowledgeScope, includeDescendants } })
            }
          />
          <div className="flex flex-col gap-0.5">
            <Label htmlFor={descendantsId} className="cursor-pointer text-body font-medium text-text">
              {t("descendants")}
            </Label>
            <p id={descendantsHintId} className="text-caption text-text-muted">
              {t("descendantsHint")}
            </p>
          </div>
        </div>
      </fieldset>

      <BlockMatrixEditor
        key={block.id}
        block={block}
        axes={axes}
        labels={labels}
        onChange={(matrix) => update({ matrix })}
        available={availableQuery.data}
        availableState={availableState}
      />

      <KnowledgePickerDialog
        open={pickerOpen}
        onOpenChange={setPickerOpen}
        value={scopeRefs}
        selectableLevels={["CHAPTER", "LESSON", "KNOWLEDGE"]}
        withPrimary={false}
        defaultSubjectCode={subjectCode}
        description={t("pickerDescription", { name: block.name })}
        onApply={(refs) => {
          update({ knowledgeScope: { ...block.knowledgeScope, nodeIds: refs.map((ref) => ref.nodeId) } });
          setPickerOpen(false);
        }}
      />
      <ConfirmDialog
        open={confirmRemove}
        onOpenChange={setConfirmRemove}
        title={t("removeTitle", { name: block.name })}
        description={t("removeDescription", { count: block.questionCount })}
        confirmLabel={t("removeConfirm")}
        onConfirm={() => {
          setConfirmRemove(false);
          onRemove();
        }}
      />
    </Card>
  );
}
