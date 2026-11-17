"use client";

import { Badge, Button, Card, Checkbox, cn, ConfirmDialog, FormField, IconButton, Input, Select } from "@thi360/ui";
import { ArrowDown, ArrowUp, ChevronRight, Lock, Trash2 } from "lucide-react";
import { useTranslations } from "next-intl";
import { useId, useState } from "react";
import {
  QUESTION_TYPES,
  type CompetencyOption,
  type ContentReference,
  type QuestionType,
  type ScoringDefaults,
} from "@/features/content/types";
import type { MatrixAxes } from "../matrix";
import { blockPointsEach, emptyMatrix, isSetBlock, validateBlock } from "../matrix";
import { useAvailableQuestions } from "../queries";
import { effectiveScope } from "../subject-matrix";
import type { ExamSection, KnowledgeScope, QuestionBlock } from "../types";
import { BlockMatrixEditor, type MatrixLabels } from "./block-matrix-editor";
import { BlockNumberInput } from "./block-number-input";
import { BlockScoringEditor, expectedScoringFor } from "./block-scoring-editor";
import { OptionalNumberInput } from "./create-exam-fields";
import { KnowledgeScopeField } from "./knowledge-scope-field";

/** Giá trị của ô chọn dạng trả lời cho nhóm lấy theo chỉ tiêu dạng trả lời của môn (`questionType = null`). */
const OPEN_TYPE = "__OPEN__";
/** Ô chọn "không lọc" của dạng bài và dạng câu con. */
const ANY = "__ANY__";
/** D19: câu con của Bộ câu hỏi không phải Bộ câu hỏi hay Điền khuyết. */
const CHILD_TYPES = QUESTION_TYPES.filter((type) => type !== "QUESTION_SET" && type !== "CLOZE");

export interface BlockEditorProps {
  block: QuestionBlock;
  /** Môn chứa nhóm: nhóm không đặt phạm vi MAPID thì dùng phạm vi của môn. */
  section: ExamSection;
  subjectCode: string;
  /** "Phần 1 · Toán · nhóm 2 / 2". */
  locationText: string;
  position: number;
  siblingCount: number;
  axes: MatrixAxes;
  labels: MatrixLabels;
  reference: ContentReference | undefined;
  /** Quy tắc chấm mặc định của kỳ thi, nạp khi nhóm đổi sang Đúng/Sai hoặc Nhiều đáp án. */
  scoringDefaults: ScoringDefaults | null;
  /** Năng lực của kỳ thi (V-ACT); rỗng: không có lọc và chiều năng lực. */
  competencies: readonly CompetencyOption[];
  onChange: (block: QuestionBlock) => void;
  onMove: (offset: -1 | 1) => void;
  onRemove: () => void;
  /** Về màn môn chứa nhóm. */
  onOpenSection: () => void;
}

/**
 * Một nhóm lấy câu của môn (bản vẽ `BaiThiCauTruc`, UniNote PBI-04 mục 6.1, spec B9): nhóm câu đơn khai số câu và phạm vi
 * nguồn; nhóm câu chùm khai số chùm, số câu con mỗi chùm, phạm vi ngữ liệu hoặc dạng bài và luôn lấy nguyên chùm. Dạng
 * trả lời mẫu đã điền sẵn thì chỉ đọc. Phạm vi MAPID để trống là dùng phạm vi của môn. Ma trận lớp, độ khó của riêng
 * nhóm là mục mở thêm, mặc định đóng; câu của nhóm vẫn tính vào chỉ tiêu của môn.
 */
export function BlockEditor({
  block,
  section,
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
  onOpenSection,
}: BlockEditorProps) {
  const t = useTranslations("exams.structure.block");
  const tContent = useTranslations("content");
  const titleId = useId();
  const [confirmRemove, setConfirmRemove] = useState(false);

  const isSet = isSetBlock(block);
  const remainder = block.questionCount === null;
  // Môn chỉ có một nhóm lấy phần còn lại (R1-5): nhóm khác đã giữ thì ô này khóa, trừ khi chính nhóm này đang giữ.
  const remainderTaken = !remainder && section.blocks.some((other) => other.id !== block.id && other.questionCount === null);
  const questionCount = block.questionCount ?? 0;
  const matrix = block.matrix ?? emptyMatrix();
  const editableMatrix = { ...block, matrix, questionCount };

  // Nguồn đếm theo phạm vi có hiệu lực (của nhóm, hoặc của môn khi nhóm kế thừa) và dạng trả lời của nhóm.
  const quotaTypes = section.answerQuotas?.items.map((item) => item.answerType) ?? [];
  const availableQuery = useAvailableQuestions({
    subjectCode,
    questionType: block.questionType,
    answerTypes: block.questionType === null ? quotaTypes : [],
    childAnswerType: block.childAnswerType,
    knowledgeScope: effectiveScope(block, section),
    tagCodes: block.tagCodes,
    requiredPointsPerQuestion: block.requiredPointsPerQuestion,
  });
  const availableState = availableQuery.isError ? "error" : availableQuery.data ? "ready" : "loading";

  const issues = validateBlock(block, axes);
  const countError = issues.some((issue) => issue.code === "COUNT_NOT_POSITIVE") ? t("errors.countPositive") : undefined;
  const setIssue = issues.find((issue) => issue.code === "SET_SIZE_MISMATCH");

  const update = (patch: Partial<QuestionBlock>) => onChange({ ...block, ...patch });
  const setType = (questionType: QuestionType | null) => {
    // Đổi dạng thì quy tắc chấm mong đợi theo dạng mới (mặc định của kỳ thi).
    const expectedScoring = expectedScoringFor(questionType, scoringDefaults, blockPointsEach(block));
    if (questionType === "QUESTION_SET" || questionType === "CLOZE") {
      const setSize = Math.max(questionCount, 1);
      update({ questionType, setCount: 1, setSize, questionCount: setSize, childAnswerType: null, expectedScoring });
    } else {
      update({
        questionType,
        setCount: null,
        setSize: null,
        childAnswerType: null,
        questionCount: remainder ? null : Math.max(questionCount, 1),
        expectedScoring,
      });
    }
  };
  const setSets = (setCount: number, setSize: number) => update({ setCount, setSize, questionCount: setCount * setSize });
  const setScope = (knowledgeScope: KnowledgeScope | null) => update({ knowledgeScope });

  // Năng lực chọn được: năng lực của môn của nhóm (V-ACT gắn năng lực theo môn); môn không có năng lực riêng thì mọi năng lực.
  const subjectCompetencies = competencies.filter((competency) => competency.subjectCode === subjectCode);
  const competencyChoices = subjectCompetencies.length ? subjectCompetencies : competencies;
  const selectedCompetencies = block.knowledgeScope?.competencyCodes ?? [];
  const competencyName = (code: string) => competencies.find((item) => item.code === code)?.name ?? code;
  const setCompetencies = (codes: string[]) => {
    // Lọc năng lực sống trong `knowledgeScope.competencyCodes` nhưng tách khỏi kế thừa MAPID (R1-4): chọn năng lực
    // trên nhóm chưa chọn nút nào vẫn kế thừa phạm vi của môn (`effectiveScope`); bỏ hết mã và chưa có nút riêng thì
    // quy về `null` cho gọn.
    const knowledgeScope =
      codes.length === 0 && (block.knowledgeScope?.nodeIds.length ?? 0) === 0
        ? null
        : { nodeIds: [], match: "ANY" as const, ...block.knowledgeScope, competencyCodes: codes };
    // Chiều năng lực của ma trận đi theo năng lực đã chọn (giữ giá trị của năng lực còn lại).
    const byCompetency = matrix.byCompetency?.length
      ? (codes.length ? codes : competencyChoices.map((item) => item.code)).map((code) => ({
          code,
          value: matrix.byCompetency?.find((item) => item.code === code)?.value ?? 0,
        }))
      : matrix.byCompetency;
    update({ knowledgeScope, matrix: block.matrix ? { ...matrix, byCompetency } : block.matrix });
  };
  const matrixCompetencies = (selectedCompetencies.length ? selectedCompetencies : competencyChoices.map((item) => item.code)).map(
    (code) => ({ code, name: competencyName(code) }),
  );

  const typeLabel = (type: QuestionType) => tContent(`questionType.${type}`);

  return (
    <Card padding="md" className="flex flex-col gap-5 sm:p-6">
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div className="flex min-w-0 flex-col gap-0.5">
          <h2 id={titleId} className="flex flex-wrap items-center gap-2 text-h3 font-bold wrap-break-word text-text">
            {t("title", { name: block.name || t("untitled") })}
            {block.questionType === "ESSAY" && <Badge className="font-medium">{t("manualGrading")}</Badge>}
          </h2>
          <p className="text-caption text-text-muted">{locationText}</p>
          {block.templateGroupCode && <p className="text-caption text-text-muted">{t("fromTemplate", { code: block.templateGroupCode })}</p>}
          <Button type="button" variant="link" size="sm" className="w-fit px-0" onClick={onOpenSection}>
            {t("backToSection")}
          </Button>
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
        {block.answerTypeLocked ? (
          <FormField label={t("questionType")} hint={t("typeLocked")}>
            {(controlProps) => (
              <div className="relative">
                <Input
                  {...controlProps}
                  readOnly
                  value={block.questionType ? typeLabel(block.questionType) : t("typeOpen")}
                  className="pr-9"
                />
                <Lock aria-hidden className="pointer-events-none absolute top-1/2 right-3 size-4 -translate-y-1/2 text-text-muted" />
              </div>
            )}
          </FormField>
        ) : (
          <FormField label={t("questionType")} required hint={block.questionType === null ? t("typeOpenHint") : undefined}>
            {(controlProps) => (
              <Select
                {...controlProps}
                value={block.questionType ?? OPEN_TYPE}
                onValueChange={(value) => setType(value === OPEN_TYPE ? null : (value as QuestionType))}
                options={[
                  ...(isSet ? [] : [{ value: OPEN_TYPE, label: t("typeOpen") }]),
                  ...QUESTION_TYPES.map((type) => ({ value: type, label: typeLabel(type) })),
                ]}
              />
            )}
          </FormField>
        )}
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
            {block.questionType === "QUESTION_SET" && (
              <FormField label={t("childAnswerType")} hint={t("childAnswerTypeHint")}>
                {(controlProps) => (
                  <Select
                    {...controlProps}
                    value={block.childAnswerType ?? ANY}
                    onValueChange={(value) => update({ childAnswerType: value === ANY ? null : (value as QuestionType) })}
                    options={[
                      { value: ANY, label: t("childAnswerTypeAny") },
                      ...CHILD_TYPES.map((type) => ({ value: type, label: typeLabel(type) })),
                    ]}
                  />
                )}
              </FormField>
            )}
          </>
        ) : (
          <FormField
            label={t("questionCount")}
            required={!remainder}
            hint={remainder ? t("remainderHint") : remainderTaken ? t("remainderTaken") : t("questionCountHint")}
            error={countError}
          >
            {(controlProps) => (
              <div className="flex flex-col gap-2">
                <BlockNumberInput
                  {...controlProps}
                  value={questionCount}
                  disabled={remainder}
                  onValueChange={(value) => update({ questionCount: value })}
                />
                <div className="flex items-center gap-2">
                  <Checkbox
                    id={`${titleId}-remainder`}
                    checked={remainder}
                    disabled={remainderTaken}
                    onCheckedChange={(checked) => update({ questionCount: checked === true ? null : 1 })}
                  />
                  <label htmlFor={`${titleId}-remainder`} className={cn("text-body text-text", remainderTaken ? "cursor-not-allowed opacity-50" : "cursor-pointer")}>
                    {t("remainder")}
                  </label>
                </div>
              </div>
            )}
          </FormField>
        )}
        <FormField
          label={t("points")}
          hint={
            block.requiredPointsPerQuestion === null
              ? t("pointsOpenHint")
              : t("pointsTotal", {
                  points: block.requiredPointsPerQuestion,
                  total: Math.round(questionCount * block.requiredPointsPerQuestion * 10_000) / 10_000,
                })
          }
        >
          {(controlProps) => (
            <OptionalNumberInput
              {...controlProps}
              value={block.requiredPointsPerQuestion}
              onValueChange={(requiredPointsPerQuestion) => update({ requiredPointsPerQuestion })}
            />
          )}
        </FormField>
      </div>
      {isSet && countError && <p className="text-caption text-danger">{countError}</p>}
      {setIssue && setIssue.code === "SET_SIZE_MISMATCH" && (
        <p className="text-caption text-danger">{t("errors.setMismatch", { expected: setIssue.expected })}</p>
      )}
      {isSet && <p className="text-caption text-text-muted">{t("setWholeNote")}</p>}

      <KnowledgeScopeField
        fieldId={block.id}
        value={block.knowledgeScope}
        onChange={setScope}
        inherited={{ scope: section.knowledgeScope }}
        reference={reference}
        subjectCode={subjectCode}
        name={block.name}
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

      <BlockScoringEditor
        block={block}
        defaults={scoringDefaults}
        onChange={(expectedScoring) => update({ expectedScoring })}
      />

      <details className="group rounded-md border border-border">
        <summary className="flex cursor-pointer list-none items-center gap-2 px-4 py-3 text-label font-semibold text-text">
          <ChevronRight aria-hidden className="size-4 transition-transform group-open:rotate-90 motion-reduce:transition-none" />
          {t("ownMatrix")}
          <span className="text-caption font-normal text-text-muted">
            {block.matrix === null ? t("ownMatrixNone") : t("ownMatrixSet")}
          </span>
        </summary>
        <div className="flex flex-col gap-3 border-t border-border p-4">
          <p className="text-caption text-text-muted">{t("ownMatrixHint")}</p>
          {remainder ? (
            <p className="text-body text-text-secondary">{t("ownMatrixNeedsCount")}</p>
          ) : (
            <>
              <BlockMatrixEditor
                key={block.id}
                block={editableMatrix}
                axes={axes}
                labels={labels}
                competencies={competencies.length ? matrixCompetencies : []}
                onChange={(next) => update({ matrix: next })}
                available={availableQuery.data}
                availableState={availableState}
                declared={block.matrix !== null}
              />
              {block.matrix !== null && (
                <Button type="button" variant="secondary" size="sm" className="w-fit" onClick={() => update({ matrix: null })}>
                  {t("ownMatrixRemove")}
                </Button>
              )}
            </>
          )}
        </div>
      </details>

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
