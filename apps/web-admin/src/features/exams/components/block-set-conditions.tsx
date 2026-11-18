"use client";

import { Button, cn, focusRing, FormField, IconButton, Select } from "@thi360/ui";
import { CircleMinus, ListTree, Plus, Trash2 } from "lucide-react";
import { useTranslations } from "next-intl";
import { useId, useMemo, useState, type ReactNode } from "react";
import { KnowledgePickerDialog } from "@/features/content/components/knowledge-picker-dialog";
import { indexKnowledge } from "@/features/content/knowledge-tree";
import { QUESTION_TYPES, type ContentReference, type KnowledgeRef, type QuestionType } from "@/features/content/types";
import { blockPointsEach, countOf, isFlexibleSet } from "../matrix";
import type { BlockSlot, ChildCondition, ItemShape, QuestionBlock } from "../types";
import {
  childConditionIssue,
  positionRows,
  newChildCondition,
  slotAt,
  slotHasCondition,
  withSlot,
} from "./block-form";
import { blockFieldAnchor } from "./block-form-fields";
import { BlockNumberInput } from "./block-number-input";
import { BlockTagPicker } from "./block-tag-picker";

export interface BlockSetConditionsProps {
  block: QuestionBlock;
  reference: ContentReference | undefined;
  subjectCode: string;
  onChange: (patch: Partial<QuestionBlock>) => void;
  /** Số lệnh hỏi của nhóm do máy chủ tính (R-142); `null`: chưa có hoặc bản nháp đã đổi. */
  commandCount?: number | null;
  /** Mẫu khóa dòng (H42): dạng câu con chỉ đọc; MAPID vẫn sửa được. */
  locked?: boolean;
}

const ANY = "__ANY__";
/** Dạng của câu con trong chùm: mọi dạng câu đơn. */
const CHILD_TYPES = QUESTION_TYPES.filter((type) => type !== "QUESTION_SET" && type !== "CLOZE");

/** Bảng và nút chọn MAPID dùng chung cho điều kiện của chùm và điều kiện theo vị trí. */
function useKnowledgeRefs(reference: ContentReference | undefined) {
  const index = useMemo(() => indexKnowledge(reference?.knowledgeTree ?? []), [reference]);
  return (nodeIds: readonly string[]): KnowledgeRef[] =>
    nodeIds.map((nodeId) => {
      const node = index.get(nodeId)?.node;
      return { nodeId, code: node?.code ?? nodeId, name: node?.name ?? "", primary: false };
    });
}

/**
 * Điều kiện của nhóm chùm (2B 3.3): điều kiện riêng từng chùm (`slots`: nhãn, MAPID) và điều kiện MAPID theo vị trí câu
 * con (`childConditions`). Chùm không đặt gì dùng điều kiện của nhóm. Chùm cỡ linh hoạt (chỉ nhập tổng câu con) không
 * có cỡ chùm cố định nên không có hai bảng này; một dòng ghi lý do.
 */
export function BlockSetConditions({ block, reference, subjectCode, onChange, commandCount = null, locked = false }: BlockSetConditionsProps) {
  const t = useTranslations("exams.structure.blockForm.childConditions");
  if (isFlexibleSet(block)) return <p className="text-caption text-text-muted">{t("flexibleNote")}</p>;
  return (
    <>
      <SlotsEditor block={block} reference={reference} subjectCode={subjectCode} onChange={onChange} />
      <ChildConditionsEditor block={block} reference={reference} subjectCode={subjectCode} onChange={onChange} locked={locked} />
      <PositionTable block={block} commandCount={commandCount} />
    </>
  );
}

function ConditionSection({ title, hint, children, action }: { title: string; hint: string; children: ReactNode; action?: ReactNode }) {
  return (
    <section className="flex flex-col gap-3 rounded-md border border-border p-4" aria-label={title}>
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div className="flex min-w-0 flex-col gap-0.5">
          <h3 className="text-label font-semibold text-text">{title}</h3>
          <p className="text-caption text-text-muted">{hint}</p>
        </div>
        {action}
      </div>
      {children}
    </section>
  );
}

function MapidChips({ refs, onRemove, label }: { refs: readonly KnowledgeRef[]; onRemove: (nodeId: string) => void; label: (ref: KnowledgeRef) => string }) {
  return (
    <>
      {refs.map((ref) => (
        <span
          key={ref.nodeId}
          className="inline-flex max-w-full items-center gap-1 rounded-full border border-border bg-subtle py-0.5 pr-0.5 pl-3 text-label text-text"
        >
          <span className="min-w-0 wrap-anywhere">
            <span className="font-semibold">{ref.code}</span> {ref.name}
          </span>
          <IconButton icon={<CircleMinus />} className="size-8" aria-label={label(ref)} onClick={() => onRemove(ref.nodeId)} />
        </span>
      ))}
    </>
  );
}

// ---------------------------------------------------------------- điều kiện riêng từng chùm

function SlotsEditor({ block, reference, subjectCode, onChange }: BlockSetConditionsProps) {
  const t = useTranslations("exams.structure.blockForm.slots");
  const tagTitle = useId();
  const refsOf = useKnowledgeRefs(reference);
  const [editing, setEditing] = useState<number | null>(null);
  const [pickerOpen, setPickerOpen] = useState(false);
  const setCount = block.setCount ?? 0;
  if (setCount < 1) return null;

  const tagName = (code: string) => reference?.tags.find((tag) => tag.code === code)?.name ?? code;
  const patchSlot = (index: number, patch: Partial<Omit<BlockSlot, "index">>) => onChange({ slots: withSlot(block.slots, index, patch, setCount) });
  const current = editing === null ? null : slotAt(block.slots, editing);
  const currentRefs = refsOf(current?.knowledgeScope?.nodeIds ?? []);

  return (
    <ConditionSection title={t("title")} hint={t("hint")}>
      <div role="region" tabIndex={0} aria-labelledby={tagTitle} className={cn("max-w-full overflow-x-auto rounded-md border border-border", focusRing)}>
        <table className="w-full border-separate border-spacing-0 text-body">
          <caption id={tagTitle} className="sr-only">
            {t("caption", { count: setCount })}
          </caption>
          <thead>
            <tr>
              {(["set", "tags", "mapid", "actions"] as const).map((column) => (
                <th
                  key={column}
                  scope="col"
                  className={cn(
                    "border-b border-border bg-subtle px-3 py-2 text-left text-caption font-semibold whitespace-nowrap text-text-muted",
                    column === "set" && "sticky left-0 z-10",
                  )}
                >
                  {t(`columns.${column}`)}
                </th>
              ))}
            </tr>
          </thead>
          <tbody>
            {Array.from({ length: setCount }, (_, offset) => {
              const index = offset + 1;
              const slot = slotAt(block.slots, index);
              const open = editing === index;
              const tags = slot.tagCodes ?? [];
              const nodeCount = slot.knowledgeScope?.nodeIds.length ?? 0;
              return (
                <tr key={index} className={cn(open && "bg-primary-50")}>
                  <th
                    scope="row"
                    className={cn(
                      "sticky left-0 z-10 border-b border-border px-3 py-2 text-left font-medium whitespace-nowrap text-text",
                      open ? "bg-primary-50" : "bg-surface",
                    )}
                  >
                    {t("setName", { index })}
                  </th>
                  <td className="border-b border-border px-3 py-2 align-top">
                    {tags.length > 0 ? (
                      <span className="font-medium text-primary-text">{tags.map(tagName).join(", ")}</span>
                    ) : (
                      <span className="text-text-muted">{t("inheritsGroup")}</span>
                    )}
                  </td>
                  <td className="border-b border-border px-3 py-2 align-top">
                    {nodeCount > 0 ? (
                      <span className="font-medium text-primary-text">{t("mapidCount", { count: nodeCount })}</span>
                    ) : (
                      <span className="text-text-muted">{t("inheritsGroup")}</span>
                    )}
                  </td>
                  <td className="border-b border-border px-3 py-2 align-top">
                    <Button
                      type="button"
                      variant="link"
                      size="sm"
                      data-field={index === 1 ? blockFieldAnchor(block.id, "slots") : undefined}
                      aria-expanded={open}
                      aria-label={t(open ? "closeEdit" : "edit", { index })}
                      onClick={() => setEditing(open ? null : index)}
                    >
                      {t(open ? "close" : "editLabel")}
                    </Button>
                    {slotHasCondition(slot) && (
                      <Button
                        type="button"
                        variant="link"
                        size="sm"
                        className="ml-3"
                        aria-label={t("clear", { index })}
                        onClick={() => patchSlot(index, { tagCodes: undefined, knowledgeScope: undefined })}
                      >
                        {t("clearLabel")}
                      </Button>
                    )}
                  </td>
                </tr>
              );
            })}
          </tbody>
        </table>
      </div>

      {editing !== null && current && (
        <div className="flex flex-col gap-3 rounded-md border border-border bg-subtle/40 p-3" role="group" aria-label={t("editorTitle", { index: editing })}>
          <h4 className="text-label font-semibold text-text">{t("editorTitle", { index: editing })}</h4>
          <BlockTagPicker
            value={current.tagCodes ?? []}
            onChange={(tagCodes) => patchSlot(editing, { tagCodes })}
            options={reference?.tags ?? []}
            scopeName={t("setName", { index: editing })}
          />
          <div className="flex flex-wrap items-center gap-2">
            <span className="text-label font-medium text-text-secondary">{t("mapidLabel")}</span>
            {currentRefs.length === 0 && <span className="text-body text-text-muted">{t("inheritsGroup")}</span>}
            <MapidChips
              refs={currentRefs}
              label={(ref) => t("removeMapid", { code: ref.code, name: ref.name, index: editing })}
              onRemove={(nodeId) =>
                patchSlot(editing, {
                  knowledgeScope: { nodeIds: (current.knowledgeScope?.nodeIds ?? []).filter((id) => id !== nodeId) },
                })
              }
            />
            <Button type="button" variant="secondary" size="sm" onClick={() => setPickerOpen(true)}>
              <ListTree />
              <span>{t("pickMapid")}</span>
            </Button>
          </div>
          <KnowledgePickerDialog
            open={pickerOpen}
            onOpenChange={setPickerOpen}
            value={currentRefs}
            selectableLevels={["CHAPTER", "LESSON", "KNOWLEDGE"]}
            withPrimary={false}
            defaultSubjectCode={subjectCode}
            description={t("pickerDescription", { index: editing })}
            onApply={(refs) => {
              patchSlot(editing, { knowledgeScope: { nodeIds: refs.map((ref) => ref.nodeId) } });
              setPickerOpen(false);
            }}
          />
        </div>
      )}
    </ConditionSection>
  );
}

// ---------------------------------------------------------------- điều kiện theo vị trí câu con

function ChildConditionsEditor({ block, reference, subjectCode, onChange, locked = false }: BlockSetConditionsProps) {
  const t = useTranslations("exams.structure.blockForm.childConditions");
  const refsOf = useKnowledgeRefs(reference);
  const [pickerFor, setPickerFor] = useState<number | null>(null);
  const conditions = block.childConditions ?? [];
  const setSize = block.setSize ?? 0;

  const replace = (next: ChildCondition[]) => onChange({ childConditions: next.length > 0 ? next : undefined });
  const patch = (index: number, change: Partial<ChildCondition>) =>
    replace(conditions.map((condition, position) => (position === index ? { ...condition, ...change } : condition)));
  const tContent = useTranslations("content");
  /** Đổi dạng câu con: hình dạng của dạng cũ không còn nghĩa nên bỏ (R-132, H39). */
  const setAnswerType = (index: number, answerType: QuestionType | undefined) =>
    replace(
      conditions.map((condition, position) => {
        if (position !== index) return condition;
        const next: ChildCondition = { ...condition };
        delete next.answerType;
        delete next.itemShape;
        if (answerType) next.answerType = answerType;
        return next;
      }),
    );
  const setShape = (index: number, shape: ItemShape) => patch(index, { itemShape: Object.keys(shape).length > 0 ? shape : undefined });

  return (
    <ConditionSection
      title={t("title")}
      hint={t("hint")}
      action={
        <Button
          type="button"
          variant="secondary"
          size="sm"
          data-field={blockFieldAnchor(block.id, "childConditions")}
          onClick={() => replace([...conditions, newChildCondition(setSize)])}
        >
          <Plus />
          <span>{t("add")}</span>
        </Button>
      }
    >
      {conditions.length === 0 && <p className="text-body text-text-muted">{t("empty")}</p>}
      {conditions.map((condition, index) => {
        const issue = childConditionIssue(condition, setSize);
        const refs = refsOf(condition.nodeIds);
        const name = t("rowName", { index: index + 1 });
        return (
          <div key={index} role="group" aria-label={name} className="flex flex-col gap-2 rounded-md border border-border bg-subtle/40 p-3">
            <div className="flex flex-wrap items-end gap-3">
              <label className="flex w-28 flex-col gap-1 text-field-label font-medium text-text-secondary">
                {t("from")}
                <BlockNumberInput value={condition.from} aria-invalid={issue === "FROM_MIN" || issue === "RANGE_ORDER" ? true : undefined} onValueChange={(from) => patch(index, { from })} />
              </label>
              <label className="flex w-28 flex-col gap-1 text-field-label font-medium text-text-secondary">
                {t("to")}
                <BlockNumberInput value={condition.to} aria-invalid={issue === "RANGE_ORDER" || issue === "OUT_OF_SET" ? true : undefined} onValueChange={(to) => patch(index, { to })} />
              </label>
              <div className="w-56">
                <FormField label={t("answerType")}>
                  {(controlProps) => (
                    <Select
                      {...controlProps}
                      disabled={locked}
                      value={condition.answerType ?? ANY}
                      onValueChange={(value) => setAnswerType(index, value === ANY ? undefined : (value as QuestionType))}
                      options={[
                        { value: ANY, label: t("answerTypeAny") },
                        ...CHILD_TYPES.map((type) => ({ value: type, label: tContent(`questionType.${type}`) })),
                      ]}
                    />
                  )}
                </FormField>
              </div>
              {condition.answerType === "TRUE_FALSE" && (
                <label className="flex w-28 flex-col gap-1 text-field-label font-medium text-text-secondary">
                  {t("statementsLabel")}
                  <BlockNumberInput
                    value={condition.itemShape?.statements ?? 0}
                    readOnly={locked}
                    aria-readonly={locked || undefined}
                    onValueChange={(statements) => setShape(index, statements > 0 ? { statements } : {})}
                  />
                </label>
              )}
              {(condition.answerType === "SINGLE_CHOICE" || condition.answerType === "MULTIPLE_CHOICE") && (
                <label className="flex w-28 flex-col gap-1 text-field-label font-medium text-text-secondary">
                  {t("optionsLabel")}
                  <BlockNumberInput
                    value={condition.itemShape?.options ?? 0}
                    readOnly={locked}
                    aria-readonly={locked || undefined}
                    onValueChange={(options) => setShape(index, options > 0 ? { options } : {})}
                  />
                </label>
              )}
              <div className="ml-auto">
                <IconButton
                  icon={<Trash2 />}
                  tone="danger"
                  aria-label={t("remove", { name })}
                  onClick={() => replace(conditions.filter((_, position) => position !== index))}
                />
              </div>
            </div>
            <div className="flex flex-wrap items-center gap-2">
              <span className="text-label font-medium text-text-secondary">{t("mapidLabel")}</span>
              <MapidChips
                refs={refs}
                label={(ref) => t("removeMapid", { code: ref.code, name: ref.name })}
                onRemove={(nodeId) => patch(index, { nodeIds: condition.nodeIds.filter((id) => id !== nodeId) })}
              />
              <Button type="button" variant="secondary" size="sm" onClick={() => setPickerFor(index)}>
                <ListTree />
                <span>{t("pickMapid")}</span>
              </Button>
            </div>
            {issue && (
              <p role="alert" className="text-caption text-danger">
                {t(`issues.${issue}`, { size: setSize })}
              </p>
            )}
          </div>
        );
      })}
      {pickerFor !== null && conditions[pickerFor] && (
        <KnowledgePickerDialog
          open
          onOpenChange={(open) => !open && setPickerFor(null)}
          value={refsOf(conditions[pickerFor].nodeIds)}
          selectableLevels={["CHAPTER", "LESSON", "KNOWLEDGE"]}
          withPrimary={false}
          defaultSubjectCode={subjectCode}
          description={t("pickerDescription", { name: t("rowName", { index: pickerFor + 1 }) })}
          onApply={(refs) => {
            patch(pickerFor, { nodeIds: refs.map((ref) => ref.nodeId) });
            setPickerFor(null);
          }}
        />
      )}
    </ConditionSection>
  );
}

/**
 * Bảng chỉ đọc "Loại câu hỏi theo vị trí trong chùm" (bản vẽ `BaiDocDungChung2B`, R-132): dải vị trí, câu trên đề, dạng, hình
 * dạng, lệnh hỏi, điểm của MỘT chùm; dòng tổng tính cho CẢ khối (số câu của khối, điểm = số câu × điểm mỗi câu, lệnh hỏi do
 * máy chủ tính theo cả khối `commandCount`; chưa có thì bỏ phần lệnh). Khối nhiều chùm có một dòng nói bảng là của một chùm.
 */
function PositionTable({ block, commandCount }: { block: QuestionBlock; commandCount: number | null }) {
  const t = useTranslations("exams.structure.blockForm.positions");
  const tContent = useTranslations("content");
  const rows = positionRows(block, blockPointsEach(block));
  const captionId = useId();
  if (rows.length === 0) return null;
  const from = block.positionRange?.from;
  const single = (block.setCount ?? 1) <= 1 && from !== undefined;
  const questions = countOf(block);
  const points = Math.round(questions * blockPointsEach(block) * 10_000) / 10_000;
  const sets = block.setCount ?? 1;
  const shapeText = (row: (typeof rows)[number]) =>
    row.itemShape?.statements
      ? t("shape.statements", { count: row.itemShape.statements })
      : row.itemShape?.options
        ? t("shape.options", { count: row.itemShape.options })
        : t("shape.any");
  const cell = "border-b border-border px-3 py-2 align-top";
  return (
    <section className="flex flex-col gap-2 rounded-md border border-border p-4" aria-labelledby={captionId}>
      <h3 id={captionId} className="text-label font-semibold text-text">
        {t("title")}
      </h3>
      <div role="region" tabIndex={0} aria-labelledby={captionId} className={cn("max-w-full overflow-x-auto rounded-md border border-border", focusRing)}>
        <table className="w-full min-w-[40rem] border-separate border-spacing-0 text-body tabular-nums">
          <caption className="sr-only">{t("title")}</caption>
          <thead>
            <tr className="text-left text-caption text-text-muted">
              <th scope="col" className="border-b border-border bg-subtle px-3 py-2 font-semibold">{t("columns.position")}</th>
              <th scope="col" className="border-b border-border bg-subtle px-3 py-2 font-semibold">{t("columns.onPaper")}</th>
              <th scope="col" className="border-b border-border bg-subtle px-3 py-2 font-semibold">{t("columns.type")}</th>
              <th scope="col" className="border-b border-border bg-subtle px-3 py-2 font-semibold">{t("columns.shape")}</th>
              <th scope="col" className="border-b border-border bg-subtle px-3 py-2 text-right font-semibold">{t("columns.commands")}</th>
              <th scope="col" className="border-b border-border bg-subtle px-3 py-2 text-right font-semibold">{t("columns.points")}</th>
            </tr>
          </thead>
          <tbody>
            {rows.map((row) => (
              <tr key={`${row.from}-${row.to}`}>
                <th scope="row" className={cn(cell, "text-left font-medium whitespace-nowrap text-text")}>
                  {t("position", { from: row.from, to: row.to, same: row.from === row.to ? "yes" : "no" })}
                </th>
                <td className={cell}>
                  {single ? t("onPaper", { from: from + row.from - 1, to: from + row.to - 1, same: row.from === row.to ? "yes" : "no" }) : "—"}
                </td>
                <td className={cell}>{tContent(`questionType.${row.answerType}`)}</td>
                <td className={cell}>{shapeText(row)}</td>
                <td className={cn(cell, "text-right")}>{row.commands}</td>
                <td className={cn(cell, "text-right")}>{row.points}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
      {sets >= 2 && <p className="text-caption text-text-muted">{t("perSetNote", { sets })}</p>}
      <p className="text-label font-semibold text-text">
        {commandCount !== null ? t("total", { questions, commands: commandCount, points }) : t("totalNoCommands", { questions, points })}
      </p>
    </section>
  );
}
