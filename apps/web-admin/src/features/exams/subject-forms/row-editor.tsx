"use client";

import { Button, Card, FormField, Input } from "@thi360/ui";
import { useFormatter, useTranslations } from "next-intl";
import { useEffect, useId, useRef, useState } from "react";
import type { ContentReference, QuestionType } from "@/features/content/types";
import { type BlockField, blockFieldAnchor } from "../components/block-form-fields";
import { isFlexibleSet, isSetBlock, type MatrixAxes, validateBlock } from "../matrix";
import { resizeSets } from "../components/block-form";
import { BlockConditions, type ExtraCondition } from "../components/block-conditions";
import { BlockMatrixEditor, type MatrixLabels } from "../components/block-matrix-editor";
import { BlockNumberInput } from "../components/block-number-input";
import { BlockRadioGroup } from "../components/block-radio-group";
import { BlockSetConditions } from "../components/block-set-conditions";
import { BlockSwitches } from "../components/block-switches";
import { AudioChip, sectionAudio } from "../components/block-row-metrics";
import { KnowledgeScopeField } from "../components/knowledge-scope-field";
import { LOCKED_FIELD } from "../components/locked-field";
import { TopicQuotaTable, type TopicQuotaServerError } from "../components/topic-quota-table";
import type { BlockMatrix, ExamSection, ExamTemplate, QuestionBlock } from "../types";
import { LockReason } from "./lock-reason";
import { CountPercentInput } from "./count-percent-input";
import { percentToCount } from "./percent";
import type { RawInputStore } from "./raw-input-store";
import { COUNT_FIELD, SPLIT_FIELD } from "./row-fields";
import { RowSplitPanel } from "./row-split-panel";
import type { SplitState } from "./row-split";
import type { SubjectFormEntry } from "./registry";
import {
  blankMatrix,
  dimensionDeclared,
  gradeVisible,
  lockReasonsOf,
  modePatch,
  modeReadOnly,
  type MatrixDimensionKey,
  pruneMatrix,
  rowAnswerType,
  rowLocks,
  type RowContext,
} from "./row-list-model";

/** Kỳ có điều kiện theo vị trí câu con trong màn sửa dòng (R-7): SP2E Anh sửa được, V-SAT Anh do mẫu khóa. */
const POSITION_PAIRS: Readonly<Record<string, "EDITABLE" | "TEMPLATE">> = { "SP2E:ANH": "EDITABLE", "VSAT:ANH": "TEMPLATE" };
/** SP2E và SPT gọi phạm vi theo chủ đề là "Theo mạch kiến thức". */
const STRAND_KINDS: readonly string[] = ["SP2E", "SPT"];

/** Điều khiển nhận focus trong ô được điều hướng tới. */
const CONTROLS = "input:not([disabled]), button:not([disabled]), select:not([disabled]), textarea:not([disabled])";

export interface RowEditorProps {
  block: QuestionBlock;
  section: ExamSection;
  kindCode: string;
  entry: SubjectFormEntry;
  position: number;
  total: number;
  context: RowContext;
  template: ExamTemplate | null;
  axes: MatrixAxes;
  labels: MatrixLabels;
  reference: ContentReference;
  topicError: TopicQuotaServerError;
  onChange: (patch: Partial<QuestionBlock>) => void;
  onClose: () => void;
  /** Tăng mỗi lần điều hướng báo lại đúng dòng đang mở: màn đặt focus lại vào tiêu đề. */
  focusNonce: number;
  /** Số câu của mục chứa dòng: mẫu số của tỷ lệ (R-9); `null` khi chưa biết. */
  sectionCount: number | null;
  rawInputs: RawInputStore;
  /** Nút tách dòng (R-7): ẩn, khóa kèm lý do, hoặc mở. */
  split: SplitState;
  onSplit: (parts: number[]) => void;
  /** Ô của màn sửa nhận focus khi điều hướng báo lỗi tại ô (`data-form-field`); `null`: tiêu đề của màn. */
  focusField: string | null;
}

/**
 * Màn sửa dòng gọn (FTM R-7): Tên, Cách lấy (Câu đơn / Câu chùm theo khóa), Số câu hoặc Số chùm và Số câu con, nút Thêm điều kiện
 * (Nhãn hoặc loại văn bản, MAPID lọc, Phân bổ theo MAPID, Độ khó, Khối lớp, điều kiện từng chùm, theo vị trí câu con), công tắc
 * đảo chùm và tạm bỏ qua theo mẫu. Trường do mẫu đặt hiện thành một dòng chữ chỉ đọc. Không có Lên, Xuống, Xóa dòng (R-13).
 * Khung gắn `data-form-row-editor`; mở màn thì focus vào tiêu đề để người dùng bàn phím không mất chỗ.
 */
export function RowEditor({ block, section, kindCode, entry, position, total, context, template, axes, labels, reference, topicError, onChange, onClose, focusNonce, sectionCount, rawInputs, split, onSplit, focusField }: RowEditorProps) {
  const t = useTranslations("exams.subjectForm.rows.editor");
  const tBlock = useTranslations("exams.structure.block");
  const tForm = useTranslations("exams.structure.blockForm");
  const tContent = useTranslations("content");
  const format = useFormatter();
  const headingRef = useRef<HTMLHeadingElement>(null);
  const titleId = useId();
  const lockId = useId();
  const [droppedSlots, setDroppedSlots] = useState<number[]>([]);
  const [splitOpen, setSplitOpen] = useState(() => rawInputs.get(section.id, block.id, SPLIT_FIELD) !== null);
  const splitReasonId = useId();
  const anchor = (field: BlockField) => blockFieldAnchor(block.id, field);

  // Mở màn (bằng nút Sửa, `?block=`, bấm lỗi) thì focus vào tiêu đề: đích của điều hướng vẽ muộn hơn khung (R1-8), nên màn tự đặt focus.
  useEffect(() => {
    const field = focusField
      ? document.querySelector<HTMLElement>(`[data-form-row-editor="${CSS.escape(block.id)}"] [data-form-field="${CSS.escape(focusField)}"]`)
      : null;
    const raw = rawInputs.get(section.id, block.id, COUNT_FIELD);
    const percentTarget = focusField === COUNT_FIELD && (raw?.code.startsWith("PERCENT:") || raw?.code === "setNotWhole");
    const control = field?.matches(CONTROLS) ? field : (field?.querySelector<HTMLElement>(percentTarget ? 'input[inputmode="decimal"]' : CONTROLS) ?? null);
    (control ?? headingRef.current)?.focus();
  }, [block.id, focusNonce, focusField, rawInputs, section.id]);

  const locks = rowLocks(block, context);
  const reasons = lockReasonsOf(locks);
  const modeLocked = modeReadOnly(locks);
  const isSet = isSetBlock(block);
  const flexible = isFlexibleSet(block);
  const questionCount = block.questionCount ?? 0;
  const audio = sectionAudio(section);
  const issues = validateBlock(block, axes);
  const countError = issues.some((issue) => issue.code === "COUNT_NOT_POSITIVE") ? tBlock("errors.countPositive") : undefined;
  const setIssue = issues.find((issue) => issue.code === "SET_SIZE_MISMATCH");
  const lockProps = locks.counts ? { readOnly: true, "aria-readonly": true as const, className: LOCKED_FIELD, "aria-describedby": lockId } : {};
  const typeLabel = (type: QuestionType) => tContent(`questionType.${type}`);
  const answerType = rowAnswerType(block);
  const range = block.positionRange;

  const setSets = (setCount: number, setSize: number) => {
    const { patch, dropped } = resizeSets(block, setCount, setSize);
    onChange(patch);
    setDroppedSlots(dropped);
  };

  const dimensionExtra = (dimension: MatrixDimensionKey): ExtraCondition => {
    const grade = dimension === "GRADE";
    const key = grade ? "grade" : "difficulty";
    const declared = dimensionDeclared(block.matrix, dimension).cells > 0;
    return {
      key,
      label: t(`conditions.${key}.label`),
      hint: t(`conditions.${key}.hint`),
      state: declared ? "added" : "available",
      onRemove: () => {
        const base = block.matrix ?? blankMatrix();
        onChange({ matrix: pruneMatrix({ ...base, [grade ? "byGrade" : "byDifficulty"]: [] } as BlockMatrix) });
      },
      body: (
        <BlockMatrixEditor
          key={`${block.id}-${key}`}
          block={{ ...block, matrix: block.matrix ?? blankMatrix(), questionCount }}
          axes={axes}
          labels={labels}
          competencies={[]}
          available={undefined}
          availableState="loading"
          declared={declared}
          dimensions={[dimension]}
          compact
          onChange={(matrix) => onChange({ matrix: pruneMatrix(matrix) })}
        />
      ),
    };
  };

  const positionKind = POSITION_PAIRS[`${kindCode}:${section.subjectCode}`];
  const extras: ExtraCondition[] = [
    {
      key: "scope",
      label: t("conditions.scope.label"),
      hint: t("conditions.scope.hint"),
      state: (block.knowledgeScope?.nodeIds.length ?? 0) > 0 ? "added" : "available",
      onRemove: () => onChange({ knowledgeScope: null }),
      body: (
        <KnowledgeScopeField
          value={block.knowledgeScope}
          onChange={(knowledgeScope) => onChange({ knowledgeScope })}
          inherited={{ scope: section.knowledgeScope }}
          reference={reference}
          subjectCode={section.subjectCode}
          name={block.name}
          anchor={anchor("scope")}
          strand={STRAND_KINDS.includes(kindCode)}
        />
      ),
    },
    {
      key: "topicQuotas",
      label: t("conditions.topicQuotas.label"),
      hint: t("conditions.topicQuotas.hint"),
      state: (block.topicQuotas?.length ?? 0) > 0 ? "added" : "available",
      onRemove: () => onChange({ topicQuotas: [] }),
      body: (
        <TopicQuotaTable
          id={block.id}
          quotas={block.topicQuotas ?? []}
          onChange={(topicQuotas) => onChange({ topicQuotas })}
          total={questionCount}
          levelName={block.name || t("untitled")}
          subjectCode={section.subjectCode}
          reference={reference}
          axes={axes}
          labels={labels}
          serverError={topicError}
          entry={entry.percentEntry === "NONE" ? "COUNT_ONLY" : undefined}
        />
      ),
    },
    dimensionExtra("DIFFICULTY"),
    ...(gradeVisible(entry) ? [dimensionExtra("GRADE")] : []),
    {
      key: "slots",
      label: t("conditions.slots.label"),
      hint: t("conditions.slots.hint"),
      state: !isSet || flexible ? "unavailable" : (block.slots?.length ?? 0) > 0 ? "added" : "available",
      unavailableHint: t("conditions.slots.unavailable"),
      onRemove: () => onChange({ slots: undefined }),
      body: <BlockSetConditions block={block} reference={reference} subjectCode={section.subjectCode} onChange={onChange} locked={locks.counts} show="slots" />,
    },
    ...(positionKind
      ? [
          {
            key: "positions",
            label: t("conditions.positions.label"),
            hint: t("conditions.positions.hint"),
            state: (block.childConditions?.length ?? 0) > 0 ? ("added" as const) : !isSet || flexible || positionKind === "TEMPLATE" ? ("unavailable" as const) : ("available" as const),
            unavailableHint: positionKind === "TEMPLATE" ? t("conditions.positions.template") : t("conditions.positions.unavailable"),
            onRemove: () => onChange({ childConditions: undefined }),
            body: <BlockSetConditions block={block} reference={reference} subjectCode={section.subjectCode} onChange={onChange} locked={locks.counts || positionKind === "TEMPLATE"} show="positions" />,
          } satisfies ExtraCondition,
        ]
      : []),
  ];

  // Nhập theo tỷ lệ (R-9): kỳ cho nhập % hiện ô Số câu / Tỷ lệ; số quy ra là số nguyên trên số câu của mục, lưu số câu (không giữ %).
  const percentRows = entry.percentEntry === "COUNT_OR_PERCENT" && !locks.counts;
  const tCount = useTranslations("exams.subjectForm.common.countPercent");
  const rawCount = rawInputs.get(section.id, block.id, COUNT_FIELD);
  const rawSource = rawCount?.code.startsWith("PERCENT:") || rawCount?.code === "setNotWhole" ? "PERCENT" : "COUNT";
  const rawCode = rawCount?.code.replace(/^PERCENT:/, "");
  const countRawError = rawCount
    ? rawCode === "setCountNotWhole"
      ? t("countNotWhole", { count: Number(rawCount.text), size: block.setSize ?? 0 })
      : rawCode === "setNotWhole"
        ? tCount("setNotWhole", { count: percentToCount(Number(rawCount.text.replace(",", ".")), sectionCount ?? 0), size: block.setSize ?? 0 })
        : tCount("invalidNumber")
    : undefined;
  const trackRaw = (raw: { text: string; code: string; source?: "COUNT" | "PERCENT" } | null) => {
    if (raw) rawInputs.set({ sectionId: section.id, blockId: block.id, field: COUNT_FIELD, text: raw.text, code: raw.source === "PERCENT" ? `PERCENT:${raw.code}` : raw.code });
    else rawInputs.clear(section.id, block.id, COUNT_FIELD);
  };
  const countEntry = sectionCount !== null && sectionCount > 0 ? "COUNT_OR_PERCENT" : "NONE";
  const splitReason = split.status === "LOCKED" && !["rowCounts", "answerType"].includes(split.reason) ? split.reason : null;
  // Dòng chữ chỉ đọc của các trường do mẫu đặt (R-7, R-13): loại câu, số ý, điểm mỗi câu, định dạng đáp án, cách chấm dự kiến.
  const number = (value: number) => format.number(value, { maximumFractionDigits: 4 });
  const factParts: string[] = [];
  if (answerType) factParts.push(typeLabel(answerType));
  const shape = block.itemShape;
  if (shape?.statements) factParts.push(t("facts.statements", { count: shape.statements }));
  if (shape?.leftItems) factParts.push(t("facts.matching", { left: shape.leftItems, right: shape.rightItems ?? 0 }));
  if (shape?.options) factParts.push(t("facts.options", { count: shape.options }));
  if (block.requiredPointsPerQuestion !== null && block.requiredPointsPerQuestion !== undefined) factParts.push(t("facts.points", { points: number(block.requiredPointsPerQuestion) }));
  if (block.answerFormats && block.answerFormats.length > 0) factParts.push(t("facts.formats", { formats: block.answerFormats.join(", ") }));
  const table = block.expectedScoring?.trueFalseTable;
  if (table && table.length > 0) factParts.push(t("facts.trueFalseTable", { table: table.map(number).join(" · ") }));
  if (block.expectedScoring?.multipleChoiceRule) factParts.push(t(`facts.rule.${block.expectedScoring.multipleChoiceRule}`));
  const facts = factParts.length > 0 ? t("facts.line", { facts: factParts.join(" · ") }) : null;

  return (
    <Card padding="md" className="flex flex-col gap-5 sm:p-6" data-form-row-editor={block.id} aria-labelledby={titleId}>
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div className="flex min-w-0 flex-col gap-1">
          <h3 id={titleId} ref={headingRef} tabIndex={-1} className="flex flex-wrap items-center gap-2 text-title font-semibold wrap-break-word text-text">
            {t("title", { name: block.name || t("untitled") })}
          </h3>
          <p className="flex flex-wrap items-center gap-2 text-caption text-text-muted">
            <span>{t("position", { position, total })}</span>
            {range && <span>{tForm("positionRange", { from: range.from, to: range.to, same: range.from === range.to ? "yes" : "no" })}</span>}
            {audio && <AudioChip audio={audio} />}
          </p>
        </div>
        <div className="flex flex-wrap items-center gap-2">
          {split.status !== "HIDDEN" && (
            <Button
              type="button"
              variant="secondary"
              size="sm"
              disabled={split.status === "LOCKED"}
              aria-expanded={split.status === "OPEN" ? splitOpen : undefined}
              aria-describedby={splitReason ? splitReasonId : undefined}
              onClick={() => setSplitOpen((open) => !open)}
            >
              {t("split")}
            </Button>
          )}
          <Button type="button" variant="secondary" size="sm" onClick={onClose}>
            {t("close")}
          </Button>
        </div>
      </div>

      {reasons.length > 0 && (
        <ul className="flex flex-col gap-1" aria-label={t("lockedList")}>
          {reasons.map((code) => (
            <li key={code}>
              <LockReason code={code} id={code === "rowCounts" ? lockId : undefined} />
            </li>
          ))}
        </ul>
      )}
      {splitReason && <LockReason code={splitReason} id={splitReasonId} />}
      {split.status === "OPEN" && splitOpen && (
        <RowSplitPanel
          block={block}
          units={split.units}
          sectionId={section.id}
          rawInputs={rawInputs}
          onApply={(parts) => {
            setSplitOpen(false);
            onSplit(parts);
          }}
          onCancel={() => setSplitOpen(false)}
        />
      )}

      <div role="group" aria-labelledby={titleId} className="grid grid-cols-[repeat(auto-fill,minmax(min(14rem,100%),1fr))] gap-4">
        <FormField label={tBlock("name")} required error={block.name.trim() ? undefined : tBlock("errors.nameRequired")}>
          {(controlProps) => (
            <Input {...controlProps} data-field={anchor("name")} value={block.name} autoComplete="off" maxLength={200} onChange={(event) => onChange({ name: event.target.value })} />
          )}
        </FormField>
        <BlockRadioGroup
          legend={tForm("mode.legend")}
          value={isSet ? "set" : "single"}
          disabled={modeLocked}
          anchor={anchor("mode")}
          options={[
            { value: "single", label: tForm("mode.single") },
            { value: "set", label: tForm("mode.set") },
          ]}
          onChange={(mode) => onChange(modePatch(block, mode))}
        />
        {isSet && !flexible ? (
          <>
            <FormField label={tBlock("setCount")} required>
              {(controlProps) => (
                <BlockNumberInput {...controlProps} {...lockProps} data-field={anchor("setCount")} value={block.setCount ?? 0} onValueChange={(value) => setSets(value, block.setSize ?? 0)} />
              )}
            </FormField>
            <FormField label={tBlock("setSize")} required hint={tBlock("setHint", { count: questionCount })}>
              {(controlProps) => (
                <BlockNumberInput {...controlProps} {...lockProps} data-field={anchor("setSize")} value={block.setSize ?? 0} onValueChange={(value) => setSets(block.setCount ?? 0, value)} />
              )}
            </FormField>
            {percentRows && (
              <CountPercentInput
                label={t("quantityPercent")}
                entry={countEntry}
                total={sectionCount ?? 0}
                count={questionCount}
                setSize={block.setSize}
                fieldName={COUNT_FIELD}
                error={countRawError}
                rawText={rawCount?.text}
                rawSource={rawSource}
                onRawInput={trackRaw}
                onChange={({ count }) => {
                  const size = block.setSize ?? 0;
                  if (size > 0 && count % size === 0) setSets(count / size, size);
                  else trackRaw({ text: String(count), code: "setCountNotWhole" });
                }}
              />
            )}
          </>
        ) : (
          percentRows ? (
            <CountPercentInput
              label={flexible ? tForm("setMode.totalField") : tBlock("questionCount")}
              entry={countEntry}
              total={sectionCount ?? 0}
              count={questionCount}
              setSize={null}
              fieldName={COUNT_FIELD}
              error={countRawError ?? countError}
              rawText={rawCount?.text}
              rawSource={rawSource}
              onRawInput={trackRaw}
              onChange={({ count }) => onChange({ questionCount: count })}
            />
          ) : (
            <FormField label={flexible ? tForm("setMode.totalField") : tBlock("questionCount")} required error={countError}>
              {(controlProps) => (
                <BlockNumberInput {...controlProps} {...lockProps} data-field={anchor("questionCount")} value={questionCount} onValueChange={(value) => onChange({ questionCount: value })} />
              )}
            </FormField>
          )
        )}
      </div>
      {isSet && countError && <p className="text-caption text-danger">{countError}</p>}
      {setIssue && setIssue.code === "SET_SIZE_MISMATCH" && <p className="text-caption text-danger">{tBlock("errors.setMismatch", { expected: setIssue.expected })}</p>}
      {droppedSlots.length > 0 && (
        <p role="status" className="rounded-md border border-info/30 bg-info-bg px-3 py-2 text-body text-info">
          {tForm("slots.dropped", { sets: block.setCount ?? 0, list: droppedSlots.join(", ") })}
        </p>
      )}

      {facts && <p className="text-body text-text-secondary" data-template-facts>{facts}</p>}

      <BlockSwitches block={block} onChange={onChange} template={template} lockedOmittedAsText />

      <BlockConditions block={block} reference={reference} onChange={onChange} onAddMapid={() => undefined} kinds={["TAGS"]} extras={extras} />
    </Card>
  );
}
