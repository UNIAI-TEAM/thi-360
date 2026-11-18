"use client";

import { Badge, Button, Card, Checkbox, cn, ConfirmDialog, FormField, IconButton, Input, Select } from "@thi360/ui";
import { ArrowDown, ArrowUp, ChevronRight, Lock, Trash2 } from "lucide-react";
import { useTranslations } from "next-intl";
import { useId, useState, type ReactNode } from "react";
import {
  QUESTION_TYPES,
  type CompetencyOption,
  type ContentReference,
  type QuestionType,
  type ScoringDefaults,
} from "@/features/content/types";
import type { MatrixAxes } from "../matrix";
import { roundForDisplay } from "../display-number";
import { blockPointsEach, emptyMatrix, isFlexibleSet, isSetBlock, validateBlock } from "../matrix";
import { useAvailableQuestions } from "../queries";
import { effectiveScope } from "../subject-matrix";
import type { ExamSection, KnowledgeScope, QuestionBlock } from "../types";
import { applyBlockPatch, resizeSets, switchSetMode } from "./block-form";
import { blockFieldAnchor, type BlockField } from "./block-form-fields";
import { BlockConditions } from "./block-conditions";
import { BlockMatrixEditor, type MatrixLabels } from "./block-matrix-editor";
import { BlockNumberInput } from "./block-number-input";
import { BlockRadioGroup } from "./block-radio-group";
import { BlockScoringEditor, expectedScoringFor } from "./block-scoring-editor";
import { BlockSetConditions } from "./block-set-conditions";
import { BlockSwitches } from "./block-switches";
import { KnowledgeScopeField } from "./knowledge-scope-field";

/** Giá trị của ô chọn dạng trả lời cho nhóm lấy theo chỉ tiêu dạng trả lời của môn (`questionType = null`). */
const OPEN_TYPE = "__OPEN__";
/** Ô chọn "không lọc" của dạng câu con. */
const ANY = "__ANY__";
/** Loại câu không phải nhóm chùm: dạng của câu con (D19) và dạng của nhóm câu đơn. */
const PLAIN_TYPES = QUESTION_TYPES.filter((type) => type !== "QUESTION_SET" && type !== "CLOZE");
/** Loại câu của nhóm chùm. */
const SET_TYPES: readonly QuestionType[] = ["QUESTION_SET", "CLOZE"];
/** SP2E và SPT gọi phạm vi theo chủ đề là "Theo mạch kiến thức". */
const STRAND_KINDS: readonly string[] = ["SP2E", "SPT"];
/** Mẫu cấu trúc bật sẵn ô "Mọi câu con thuộc chủ đề" (R-120): bài ôn V-ACT theo chủ đề. */
const EVERY_CHILD_DEFAULT_TEMPLATES: readonly string[] = ["VACT_REVIEW"];

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
  /** Mã kỳ thi của bài; chỉ để chọn tên "Theo chủ đề" hay "Theo mạch kiến thức". */
  examKind?: string;
  /** Mã mẫu cấu trúc của bài; bài từ `VACT_REVIEW` bật sẵn "Mọi câu con thuộc chủ đề" khi nhóm chùm có MAPID. */
  templateCode?: string | null;
  /** Quy tắc chấm mặc định của kỳ thi, nạp khi nhóm đổi sang Đúng/Sai hoặc Nhiều đáp án. */
  scoringDefaults: ScoringDefaults | null;
  /** Năng lực của kỳ thi (V-ACT); rỗng: không có lọc và chiều năng lực. */
  competencies: readonly CompetencyOption[];
  onChange: (block: QuestionBlock) => void;
  onMove: (offset: -1 | 1) => void;
  onRemove: () => void;
  /** Về màn môn chứa nhóm. */
  onOpenSection: () => void;
  /** Nút nằm ở đầu vùng chi tiết (bản vẽ `BaiThiBuoc2`: "Kiểm tra môn"). */
  headerAction?: ReactNode;
}

/**
 * Một nhóm lấy câu của môn (2B 3.3, 15.3; bản vẽ `NhomChum2B`, `NhomCauDon2B`): chọn cách lấy (câu đơn hoặc câu chùm),
 * loại câu hỏi (không còn dạng bài, K11), số lượng, phạm vi kiến thức "Toàn môn" hoặc "Theo chủ đề", rồi các điều kiện
 * thêm bằng menu "Thêm điều kiện" (nhãn, hình dạng câu, định dạng đáp án, điểm mỗi câu). Nhóm chùm có thêm điều kiện
 * riêng từng chùm và điều kiện theo vị trí câu con. Mỗi trường có mỏ neo `data-field` (xem `block-form-fields.ts`).
 * Ma trận lớp, độ khó của riêng nhóm là mục mở thêm, mặc định đóng; câu của nhóm vẫn tính vào chỉ tiêu của môn.
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
  examKind,
  templateCode,
  scoringDefaults,
  competencies,
  onChange,
  onMove,
  onRemove,
  onOpenSection,
  headerAction,
}: BlockEditorProps) {
  const t = useTranslations("exams.structure.block");
  const tForm = useTranslations("exams.structure.blockForm");
  const tContent = useTranslations("content");
  const titleId = useId();
  const [confirmRemove, setConfirmRemove] = useState(false);
  const [mapidRequest, setMapidRequest] = useState(0);
  const [droppedSlots, setDroppedSlots] = useState<number[]>([]);
  const anchor = (field: BlockField) => blockFieldAnchor(block.id, field);

  const isSet = isSetBlock(block);
  const flexible = isFlexibleSet(block);
  const everyChildDefault = templateCode != null && EVERY_CHILD_DEFAULT_TEMPLATES.includes(templateCode);
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
    itemShape: block.itemShape,
    answerFormats: block.answerFormats,
    childConditions: block.childConditions,
  });
  const availableState = availableQuery.isError ? "error" : availableQuery.data ? "ready" : "loading";

  const issues = validateBlock(block, axes);
  const countError = issues.some((issue) => issue.code === "COUNT_NOT_POSITIVE") ? t("errors.countPositive") : undefined;
  const setIssue = issues.find((issue) => issue.code === "SET_SIZE_MISMATCH");

  const update = (patch: Partial<QuestionBlock>) => onChange(applyBlockPatch(block, patch));
  const setType = (questionType: QuestionType | null) => {
    // Đổi dạng thì quy tắc chấm mong đợi theo dạng mới (mặc định của kỳ thi).
    const expectedScoring = expectedScoringFor(questionType, scoringDefaults, blockPointsEach(block));
    if (questionType === "QUESTION_SET" || questionType === "CLOZE") {
      // Chùm cỡ linh hoạt chỉ có ở Bộ câu hỏi: đổi sang Điền khuyết thì thành một chùm cỡ bằng tổng câu con.
      if (flexible && questionType === "QUESTION_SET") {
        update({ questionType, childAnswerType: null, expectedScoring });
        setDroppedSlots([]);
        return;
      }
      const setSize = Math.max(isSet ? (block.setSize ?? questionCount) : questionCount, 1);
      const setCount = isSet ? Math.max(block.setCount ?? 1, 1) : 1;
      update({ questionType, setCount, setSize, questionCount: setCount * setSize, childAnswerType: null, expectedScoring });
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
    setDroppedSlots([]);
  };
  const setSets = (setCount: number, setSize: number) => {
    const { patch, dropped } = resizeSets(block, setCount, setSize);
    update(patch);
    setDroppedSlots(dropped);
  };
  const setSetMode = (mode: "total" | "sets") => {
    if ((mode === "total") === flexible) return;
    update(switchSetMode(block, mode));
    setDroppedSlots([]);
  };
  const setScope = (knowledgeScope: KnowledgeScope | null) => {
    if (!isSet || !knowledgeScope) return update({ knowledgeScope });
    // Chọn MAPID đầu tiên của bài ôn: ghi sẵn `true` để lưu đúng cái màn đang hiện; hết MAPID thì bỏ cờ.
    const { everyChildInScope, ...rest } = knowledgeScope;
    if (knowledgeScope.nodeIds.length === 0) return update({ knowledgeScope: rest });
    update({ knowledgeScope: everyChildInScope === undefined && everyChildDefault ? { ...rest, everyChildInScope: true } : knowledgeScope });
  };
  const setEveryChild = (checked: boolean) => {
    const scope: KnowledgeScope = { ...(block.knowledgeScope ?? { nodeIds: [] }) };
    delete scope.everyChildInScope;
    update({ knowledgeScope: checked || everyChildDefault ? { ...scope, everyChildInScope: checked } : scope });
  };

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
        : { nodeIds: [], ...block.knowledgeScope, competencyCodes: codes };
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

  // Dòng tổng của nhóm (bản vẽ `NhomChumLinhHoat2B`): số câu, số ý (Đúng/Sai) và điểm tối đa = số câu × điểm mỗi câu.
  const maxPoints = roundForDisplay(questionCount * blockPointsEach(block));
  const statements = block.itemShape?.statements ?? 0;
  const summaryText =
    isSet && questionCount > 0
      ? flexible
        ? tForm("summary.flexible", { count: questionCount, points: maxPoints })
        : tForm("summary.sets", { sets: block.setCount ?? 0, size: block.setSize ?? 0, count: questionCount, points: maxPoints })
      : block.questionType === "TRUE_FALSE" && questionCount > 0 && statements > 0
        ? tForm("summary.trueFalse", { count: questionCount, statements: questionCount * statements, points: maxPoints })
        : null;

  const typeLabel = (type: QuestionType) => tContent(`questionType.${type}`);
  const range = block.positionRange;

  return (
    <Card padding="md" className="flex flex-col gap-5 sm:p-6">
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div className="flex min-w-0 flex-col gap-0.5">
          <h2 id={titleId} className="flex flex-wrap items-center gap-2 text-h3 font-bold wrap-break-word text-text">
            {t("title", { name: block.name || t("untitled") })}
            {range && <Badge className="font-medium">{tForm("positionRange", { from: range.from, to: range.to, same: range.from === range.to ? "yes" : "no" })}</Badge>}
            {block.questionType === "ESSAY" && <Badge className="font-medium">{t("manualGrading")}</Badge>}
            {block.omitted === true && <Badge className="font-medium">{tForm("omittedBadge")}</Badge>}
          </h2>
          <p className="text-caption text-text-muted">{locationText}</p>
          {block.templateGroupCode && <p className="text-caption text-text-muted">{t("fromTemplate", { code: block.templateGroupCode })}</p>}
          <Button type="button" variant="link" size="sm" className="w-fit px-0" onClick={onOpenSection}>
            {t("backToSection")}
          </Button>
        </div>
        <div className="flex flex-wrap items-center justify-end gap-2">
          {headerAction}
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
      </div>

      <div className="grid grid-cols-[repeat(auto-fill,minmax(min(14rem,100%),1fr))] gap-4" aria-labelledby={titleId} role="group">
        <FormField label={t("name")} required error={block.name.trim() ? undefined : t("errors.nameRequired")}>
          {(controlProps) => (
            <Input
              {...controlProps}
              data-field={anchor("name")}
              value={block.name}
              autoComplete="off"
              maxLength={200}
              onChange={(event) => update({ name: event.target.value })}
            />
          )}
        </FormField>
        <BlockRadioGroup
          legend={tForm("mode.legend")}
          value={isSet ? "set" : "single"}
          disabled={block.answerTypeLocked}
          anchor={anchor("mode")}
          options={[
            { value: "single", label: tForm("mode.single") },
            { value: "set", label: tForm("mode.set") },
          ]}
          onChange={(mode) => {
            if (mode === "set" && !isSet) setType("QUESTION_SET");
            if (mode === "single" && isSet) setType("SINGLE_CHOICE");
          }}
        />
        {block.answerTypeLocked ? (
          <FormField label={t("questionType")} hint={t("typeLocked")}>
            {(controlProps) => (
              <div className="relative">
                <Input
                  {...controlProps}
                  data-field={anchor("questionType")}
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
                data-field={anchor("questionType")}
                value={block.questionType ?? OPEN_TYPE}
                onValueChange={(value) => setType(value === OPEN_TYPE ? null : (value as QuestionType))}
                options={[
                  ...(isSet ? [] : [{ value: OPEN_TYPE, label: t("typeOpen") }]),
                  ...(isSet ? SET_TYPES : PLAIN_TYPES).map((type) => ({ value: type, label: typeLabel(type) })),
                ]}
              />
            )}
          </FormField>
        )}
        {isSet ? (
          <>
            {block.questionType === "QUESTION_SET" && (
              <BlockRadioGroup
                legend={tForm("setMode.legend")}
                value={flexible ? "total" : "sets"}
                anchor={anchor("setMode")}
                options={[
                  { value: "total", label: tForm("setMode.total"), hint: tForm("setMode.totalHint") },
                  { value: "sets", label: tForm("setMode.sets"), hint: tForm("setMode.setsHint") },
                ]}
                onChange={setSetMode}
              />
            )}
            {flexible ? (
              <FormField label={tForm("setMode.totalField")} required hint={tForm("setMode.totalFieldHint")}>
                {(controlProps) => (
                  <BlockNumberInput
                    {...controlProps}
                    data-field={anchor("questionCount")}
                    value={questionCount}
                    onValueChange={(value) => update({ questionCount: value })}
                  />
                )}
              </FormField>
            ) : (
              <>
                <FormField label={t("setCount")} required>
                  {(controlProps) => (
                    <BlockNumberInput
                      {...controlProps}
                      data-field={anchor("setCount")}
                      value={block.setCount ?? 0}
                      onValueChange={(value) => setSets(value, block.setSize ?? 0)}
                    />
                  )}
                </FormField>
                <FormField label={t("setSize")} required hint={t("setHint", { count: questionCount })}>
                  {(controlProps) => (
                    <BlockNumberInput
                      {...controlProps}
                      data-field={anchor("setSize")}
                      value={block.setSize ?? 0}
                      onValueChange={(value) => setSets(block.setCount ?? 0, value)}
                    />
                  )}
                </FormField>
              </>
            )}
            {block.questionType === "QUESTION_SET" && (
              <FormField label={t("childAnswerType")} hint={t("childAnswerTypeHint")}>
                {(controlProps) => (
                  <Select
                    {...controlProps}
                    data-field={anchor("childAnswerType")}
                    value={block.childAnswerType ?? ANY}
                    onValueChange={(value) => update({ childAnswerType: value === ANY ? null : (value as QuestionType) })}
                    options={[
                      { value: ANY, label: t("childAnswerTypeAny") },
                      ...PLAIN_TYPES.map((type) => ({ value: type, label: typeLabel(type) })),
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
                  data-field={anchor("questionCount")}
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
      </div>
      {isSet && countError && <p className="text-caption text-danger">{countError}</p>}
      {setIssue && setIssue.code === "SET_SIZE_MISMATCH" && (
        <p className="text-caption text-danger">{t("errors.setMismatch", { expected: setIssue.expected })}</p>
      )}
      {isSet && <p className="text-caption text-text-muted">{flexible ? tForm("setMode.note") : t("setWholeNote")}</p>}
      {summaryText && <p className="text-label font-semibold text-text tabular-nums">{summaryText}</p>}
      {droppedSlots.length > 0 && (
        <p role="status" className="rounded-md border border-info/30 bg-info-bg px-3 py-2 text-body text-info">
          {tForm("slots.dropped", { sets: block.setCount ?? 0, list: droppedSlots.join(", ") })}
        </p>
      )}

      <BlockSwitches block={block} onChange={update} />

      <KnowledgeScopeField
        value={block.knowledgeScope}
        onChange={setScope}
        inherited={{ scope: section.knowledgeScope }}
        reference={reference}
        subjectCode={subjectCode}
        name={block.name}
        anchor={anchor("scope")}
        strand={examKind !== undefined && STRAND_KINDS.includes(examKind)}
        openRequest={mapidRequest}
        everyChild={
          isSet
            ? {
                checked: block.knowledgeScope?.everyChildInScope ?? everyChildDefault,
                onChange: setEveryChild,
                anchor: anchor("everyChildInScope"),
              }
            : undefined
        }
      />

      <BlockConditions block={block} reference={reference} onChange={update} onAddMapid={() => setMapidRequest((count) => count + 1)} />

      {isSet && <BlockSetConditions block={block} reference={reference} subjectCode={subjectCode} onChange={update} />}

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
