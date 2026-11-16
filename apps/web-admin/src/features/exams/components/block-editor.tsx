"use client";

import {
  Badge,
  Button,
  Card,
  Checkbox,
  ConfirmDialog,
  FormField,
  IconButton,
  Input,
  Select,
} from "@thi360/ui";
import { ArrowDown, ArrowUp, CircleMinus, ListTree, Trash2 } from "lucide-react";
import { useTranslations } from "next-intl";
import { useId, useMemo, useState } from "react";
import { KnowledgePickerDialog } from "@/features/content/components/knowledge-picker-dialog";
import { indexKnowledge } from "@/features/content/knowledge-tree";
import {
  QUESTION_TYPES,
  type CompetencyOption,
  type ContentReference,
  type KnowledgeRef,
  type QuestionType,
  type ScoringDefaults,
} from "@/features/content/types";
import type { MatrixAxes } from "../matrix";
import { blockPointsEach, emptyMatrix, validateBlock } from "../matrix";
import { useAvailableQuestions } from "../queries";
import type { BlockShuffleRule, KnowledgeScope, QuestionBlock } from "../types";
import { BlockMatrixEditor, type MatrixLabels } from "./block-matrix-editor";
import { BlockNumberInput } from "./block-number-input";
import { BlockScoringEditor, expectedScoringFor } from "./block-scoring-editor";
import { ChoiceCardGroup } from "./create-exam-fields";

const SHUFFLE_RULES: BlockShuffleRule[] = ["SHUFFLE", "KEEP_ORDER"];
const EMPTY_SCOPE: KnowledgeScope = { nodeIds: [], match: "ANY" };

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
  /** Quy tắc chấm mặc định của kỳ thi, nạp khi khối đổi sang Đúng/Sai hoặc Nhiều đáp án. */
  scoringDefaults: ScoringDefaults | null;
  /** Năng lực của kỳ thi (V-ACT); rỗng: không có lọc và chiều năng lực. */
  competencies: readonly CompetencyOption[];
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
  scoringDefaults,
  competencies,
  onChange,
  onMove,
  onRemove,
}: BlockEditorProps) {
  const t = useTranslations("exams.structure.block");
  const tContent = useTranslations("content");
  const titleId = useId();
  const [pickerOpen, setPickerOpen] = useState(false);
  const [confirmRemove, setConfirmRemove] = useState(false);

  // Nhóm kế thừa phạm vi MAPID của môn (`knowledgeScope = null`) hiện như phạm vi trống tới khi FE-3c làm màn theo môn.
  const scope = block.knowledgeScope ?? EMPTY_SCOPE;
  const matrix = block.matrix ?? emptyMatrix();
  const questionCount = block.questionCount ?? 0;
  const editable = { ...block, knowledgeScope: scope, matrix, questionCount };
  const knowledgeIndex = useMemo(() => indexKnowledge(reference?.knowledgeTree ?? []), [reference]);
  const scopeRefs: KnowledgeRef[] = scope.nodeIds.map((nodeId) => {
    const node = knowledgeIndex.get(nodeId)?.node;
    return { nodeId, code: node?.code ?? nodeId, name: node?.name ?? "", primary: false };
  });

  const availableQuery = useAvailableQuestions({
    subjectCode,
    questionType: block.questionType,
    answerTypes: [],
    childAnswerType: block.childAnswerType,
    knowledgeScope: block.knowledgeScope,
    taskTypeCode: block.taskTypeCode,
    tagCodes: block.tagCodes,
    requiredPointsPerQuestion: block.requiredPointsPerQuestion,
  });
  const availableState = availableQuery.isError ? "error" : availableQuery.data ? "ready" : "loading";

  const issues = validateBlock(block, axes);
  const countError = issues.some((issue) => issue.code === "COUNT_NOT_POSITIVE") ? t("errors.countPositive") : undefined;
  const setIssue = issues.find((issue) => issue.code === "SET_SIZE_MISMATCH");

  const update = (patch: Partial<QuestionBlock>) => onChange({ ...block, ...patch });
  const setType = (questionType: QuestionType) => {
    // Đổi dạng câu thì quy tắc chấm mong đợi theo dạng mới (mặc định của kỳ thi).
    const expectedScoring = expectedScoringFor(questionType, scoringDefaults, blockPointsEach(block));
    if (questionType === "QUESTION_SET") {
      const setSize = Math.max(questionCount, 1);
      update({ questionType, setCount: 1, setSize, questionCount: setSize, expectedScoring });
    } else {
      update({ questionType, setCount: null, setSize: null, expectedScoring });
    }
  };

  // Năng lực chọn được: năng lực của môn khối (V-ACT gắn năng lực theo môn); môn không có năng lực riêng thì mọi năng lực.
  const subjectCompetencies = competencies.filter((competency) => competency.subjectCode === subjectCode);
  const competencyChoices = subjectCompetencies.length ? subjectCompetencies : competencies;
  const selectedCompetencies = scope.competencyCodes ?? [];
  const competencyName = (code: string) => competencies.find((item) => item.code === code)?.name ?? code;
  const setCompetencies = (codes: string[]) => {
    const knowledgeScope = { ...scope, competencyCodes: codes };
    // Chiều năng lực của ma trận đi theo năng lực đã chọn (giữ giá trị của năng lực còn lại).
    const byCompetency = matrix.byCompetency?.length
      ? (codes.length ? codes : competencyChoices.map((item) => item.code)).map((code) => ({
          code,
          value: matrix.byCompetency?.find((item) => item.code === code)?.value ?? 0,
        }))
      : matrix.byCompetency;
    update({ knowledgeScope, matrix: { ...matrix, byCompetency } });
  };
  const matrixCompetencies = (selectedCompetencies.length ? selectedCompetencies : competencyChoices.map((item) => item.code)).map(
    (code) => ({ code, name: competencyName(code) }),
  );
  const setSets = (setCount: number, setSize: number) =>
    update({ setCount, setSize, questionCount: setCount * setSize });

  const isSet = block.questionType === "QUESTION_SET";

  return (
    <Card padding="md" className="flex flex-col gap-5 sm:p-6">
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div className="flex min-w-0 flex-col gap-0.5">
          <h2 id={titleId} className="flex flex-wrap items-center gap-2 text-h3 font-bold wrap-break-word text-text">
            {t("title", { name: block.name || t("untitled") })}
            {block.questionType === "ESSAY" && <Badge className="font-medium">{t("manualGrading")}</Badge>}
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
              value={block.questionType ?? undefined}
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
            <FormField label={t("setSize")} required hint={t("setHint", { count: questionCount })}>
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
                value={questionCount}
                onValueChange={(value) => update({ questionCount: value })}
              />
            )}
          </FormField>
        )}
        <FormField
          label={t("points")}
          required
          hint={t("pointsTotal", { points: Math.round(questionCount * blockPointsEach(block) * 10_000) / 10_000 })}
        >
          {(controlProps) => (
            <BlockNumberInput
              {...controlProps}
              allowDecimal
              value={blockPointsEach(block)}
              onValueChange={(requiredPointsPerQuestion) => update({ requiredPointsPerQuestion })}
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
                    knowledgeScope: { ...scope, nodeIds: scope.nodeIds.filter((id) => id !== ref.nodeId) },
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
          value={scope.match}
          onChange={(match) => update({ knowledgeScope: { ...scope, match } })}
          options={[
            { value: "ANY", label: t("matchAny") },
            { value: "ALL", label: t("matchAll") },
          ]}
        />
        {competencies.length > 0 && (
          <fieldset className="flex flex-col gap-2">
            <legend className="mb-1 text-field-label font-medium text-text-secondary">{t("competencies")}</legend>
            <div className="flex flex-wrap gap-x-5 gap-y-2">
              {competencyChoices.map((competency) => {
                const id = `${block.id}-competency-${competency.code}`;
                return (
                  <div key={competency.code} className="flex items-center gap-2">
                    <Checkbox
                      id={id}
                      checked={selectedCompetencies.includes(competency.code)}
                      onCheckedChange={(checked) =>
                        setCompetencies(
                          checked === true
                            ? [...selectedCompetencies, competency.code]
                            : selectedCompetencies.filter((code) => code !== competency.code),
                        )
                      }
                    />
                    <label htmlFor={id} className="cursor-pointer text-body text-text">
                      {competency.name}
                    </label>
                  </div>
                );
              })}
            </div>
            <p className="text-caption text-text-muted">{t("competenciesHint")}</p>
          </fieldset>
        )}
      </fieldset>

      <BlockScoringEditor
        block={block}
        defaults={scoringDefaults}
        onChange={(expectedScoring) => update({ expectedScoring })}
      />

      <BlockMatrixEditor
        key={block.id}
        block={editable}
        axes={axes}
        labels={labels}
        competencies={competencies.length ? matrixCompetencies : []}
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
          update({ knowledgeScope: { ...scope, nodeIds: refs.map((ref) => ref.nodeId) } });
          setPickerOpen(false);
        }}
      />
      <ConfirmDialog
        open={confirmRemove}
        onOpenChange={setConfirmRemove}
        title={t("removeTitle", { name: block.name })}
        description={t("removeDescription", { count: questionCount })}
        confirmLabel={t("removeConfirm")}
        onConfirm={() => {
          setConfirmRemove(false);
          onRemove();
        }}
      />
    </Card>
  );
}
