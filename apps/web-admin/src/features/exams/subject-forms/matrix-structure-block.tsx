"use client";

import { cn, FormField, Input } from "@thi360/ui";
import { useTranslations } from "next-intl";
import { useState } from "react";
import { parseNumberText } from "../components/block-number-input";
import { LOCKED_FIELD } from "../components/locked-field";
import { CountPercentInput } from "./count-percent-input";
import { LockReason } from "./lock-reason";
import { MatrixCell } from "./matrix-cell";
import type { MatrixBlockProps } from "./matrix-block-props";
import { MATRIX_FIELDS, MATRIX_ISSUE_CODES, matrixBlockingIssues } from "./matrix-blocking";
import { structureOf, writeStructure, type StructureInput } from "./matrix-form-model";
import { MatrixSetSource } from "./matrix-set-source";
import { percentsToCounts } from "./percent";
import { subjectFormLimits } from "./limits";

type RowKey = "CHOICE" | "SINGLE" | "SETS" | "CHILDREN" | "FILL";

/** Ô nhập số câu của một dòng; `data-form-block` cho đích `?block=` của nhóm tương ứng. */
function ReadOnlyCount({ label, value, hint }: { label: string; value: number; hint: string }) {
  return (
    <div className="flex max-w-xs flex-col gap-1.5">
      <FormField label={label} hint={hint}>
        {(controlProps) => <Input {...controlProps} value={String(value)} readOnly aria-readonly="true" className={cn("w-24 tabular-nums", LOCKED_FIELD)} />}
      </FormField>
    </div>
  );
}

/**
 * Cấu trúc câu của MT (FTM R-3): Trắc nghiệm T, Số chùm k (cỡ chùm cố định theo mẫu), Điền đáp án S; Trắc nghiệm đơn tự tính, chỉ đọc.
 * Mỗi vai trò chỉ hiện khi bài có nhóm của vai trò đó. V-ACT nhập hai cột Số câu / Tỷ lệ cho từng vai trò (R-9): tỷ lệ chỉ quy ra
 * số khi các vai trò cộng đủ 100%, chưa đủ thì giữ ở kho nhập thô và chặn lưu.
 */
export function MatrixStructureBlock(props: MatrixBlockProps) {
  const { section, entry, kindCode, total, raw, onChange, templateLocked } = props;
  const t = useTranslations("exams.subjectForm.matrix.structure");
  const tErrors = useTranslations("exams.subjectForm.matrix.errors");
  const structure = structureOf(section);
  const { single, set, fill } = structure.blocks;
  const twoColumns = entry.percentEntry === "TWO_COLUMNS";
  const limits = subjectFormLimits(kindCode, section.subjectCode);
  const issues = matrixBlockingIssues(section, kindCode);
  const [percentDraft, setPercentDraft] = useState<Partial<Record<RowKey, number>>>({});

  const errorOf = (field: string): string | undefined => {
    const stored = raw.get(field);
    const code = issues.find((issue) => issue.field === field)?.code ?? stored?.code;
    switch (code) {
      case MATRIX_ISSUE_CODES.setMin:
        return tErrors("setMin");
      case MATRIX_ISSUE_CODES.setRange:
        return tErrors("setRange", { min: limits?.setCount.min ?? 1, max: limits?.setCount.max ?? 1 });
      case MATRIX_ISSUE_CODES.shortAnswerMin:
        return tErrors("shortAnswerMin", { min: limits?.shortAnswerMin ?? 1 });
      case MATRIX_ISSUE_CODES.choiceBelowChildren:
        return tErrors("choiceBelowChildren", { children: structure.children });
      case MATRIX_ISSUE_CODES.structurePercentTotal:
        return tErrors("percentTotal", { total: stored?.text ?? "" });
      default:
        return undefined;
    }
  };
  const write = (input: StructureInput) => onChange((current) => writeStructure(current, input).section);
  const rawInputOf = (field: string) => (rawInput: { text: string; code: string } | null) => (rawInput ? raw.set(field, rawInput.text, rawInput.code) : raw.clear(field));

  // T đang chờ (R-3): chữ đã gõ ở ô Trắc nghiệm nhỏ hơn câu con còn ở kho nhập thô, không thì T của bản nháp. Đổi số chùm hay câu con tính theo T này,
  // nên không bao giờ tự nâng T lên theo câu con; câu con vượt T thì lỗi ở ô Trắc nghiệm và chặn lưu.
  const choiceRaw = raw.get(MATRIX_FIELDS.choice);
  const belowRaw = choiceRaw?.code === MATRIX_ISSUE_CODES.choiceBelowChildren ? choiceRaw : null;
  const pendingChoice = belowRaw ? (parseNumberText(belowRaw.text, false) ?? structure.choice) : structure.choice;
  /** Giữ hay gỡ lỗi "Ít hơn số câu con" theo T và số câu con mới; không đụng tới mục nhập thô khác của ô (số sai). */
  const settleChoice = (children: number, choice: number) => {
    if (single !== null && children > choice) {
      if (!choiceRaw || belowRaw) raw.set(MATRIX_FIELDS.choice, String(choice), MATRIX_ISSUE_CODES.choiceBelowChildren);
    } else if (belowRaw) raw.clear(MATRIX_FIELDS.choice);
  };

  const writeCount = (key: RowKey, count: number) => {
    if (key === "CHOICE") {
      if (count < structure.children) raw.set(MATRIX_FIELDS.choice, String(count), MATRIX_ISSUE_CODES.choiceBelowChildren);
      else write({ choice: count });
    } else if (key === "SINGLE") write({ choice: count + structure.children });
    else if (key === "SETS" || key === "CHILDREN") {
      const children = key === "SETS" ? count * (structure.setSize ?? 0) : count;
      const input: StructureInput = key === "SETS" ? { sets: count } : { children: count };
      if (twoColumns) write(key === "CHILDREN" ? { ...input, choice: structure.single + children } : input);
      else {
        write({ ...input, choice: pendingChoice });
        settleChoice(children, pendingChoice);
      }
    } else write({ fill: count });
  };

  /** Hai cột (V-ACT): tỷ lệ gõ ở một vai trò chờ các vai trò còn lại; đủ 100% mới quy ra số bằng phần dư lớn nhất. */
  const percentRoles: RowKey[] = [single ? "SINGLE" : null, set && structure.flexible ? "CHILDREN" : null, fill ? "FILL" : null].filter((key): key is RowKey => key !== null);
  const changeTwoColumn = (key: RowKey, edit: { unit: "COUNT" | "PERCENT"; value: number | null }) => {
    if (edit.unit === "COUNT") {
      setPercentDraft({});
      raw.clear(MATRIX_FIELDS.structure);
      if (edit.value !== null) writeCount(key, edit.value);
      return;
    }
    const next = { ...percentDraft };
    if (edit.value === null) delete next[key];
    else next[key] = edit.value;
    setPercentDraft(next);
    if (Object.keys(next).length === 0) return raw.clear(MATRIX_FIELDS.structure);
    const complete = percentRoles.every((role) => next[role] !== undefined);
    const sum = percentRoles.reduce((all, role) => all + (next[role] ?? 0), 0);
    if (!complete || Math.abs(sum - 100) > 1e-6) {
      raw.set(MATRIX_FIELDS.structure, String(Math.round(sum * 100) / 100), MATRIX_ISSUE_CODES.structurePercentTotal);
      return;
    }
    const counts = percentsToCounts(percentRoles.map((role) => next[role]!), total ?? 0);
    const countOf = (role: RowKey) => counts[percentRoles.indexOf(role)] ?? 0;
    raw.clear(MATRIX_FIELDS.structure);
    setPercentDraft({});
    write({ choice: countOf("SINGLE") + countOf("CHILDREN"), ...(set && structure.flexible ? { children: countOf("CHILDREN") } : {}), ...(fill ? { fill: countOf("FILL") } : {}) });
  };

  const valueOf = (key: RowKey) => (key === "CHOICE" ? pendingChoice : key === "SINGLE" ? structure.single : key === "SETS" ? (structure.sets ?? 0) : key === "CHILDREN" ? structure.children : structure.fill);
  const row = (key: RowKey, field: string, label: string, hint?: string) => {
    // Ô của nhóm nào mang `data-form-block` của nhóm đó (đích `?block=`); Trắc nghiệm theo nhóm câu đơn, không có thì theo nhóm chùm.
    const blockId = key === "CHOICE" || key === "SINGLE" ? (single?.id ?? set?.id) : key === "FILL" ? fill?.id : set?.id;
    const percentRow = twoColumns && percentRoles.includes(key);
    // Mẫu khóa số câu của mục (H27, V-ACT Tiếng Việt FULL): nhóm câu đơn là cả mục, nên số câu chỉ đọc kèm lý do.
    const locked = templateLocked && key === "SINGLE" && !set && !fill;
    return (
      <div key={key} data-form-block={blockId} className="flex min-w-0 flex-col gap-1">
        {percentRow ? (
          <>
            <MatrixCell
              label={label}
              field={field}
              stored={valueOf(key)}
              count={valueOf(key)}
              unit="COUNT"
              total={total}
              twoColumns
              readOnly={locked}
              onEdit={(edit) => changeTwoColumn(key, edit)}
              onRaw={rawInputOf(field)}
            />
            {locked && <LockReason code="templateTotal" />}
          </>
        ) : (
          <CountPercentInput
            label={label}
            entry="NONE"
            total={key === "SETS" ? 0 : (total ?? 0)}
            count={valueOf(key)}
            fieldName={field}
            onChange={(next) => writeCount(key, next.count)}
            onRawInput={rawInputOf(field)}
            error={errorOf(field)}
          />
        )}
        {hint && <p className="text-caption text-text-muted">{hint}</p>}
      </div>
    );
  };

  const structureError = errorOf(MATRIX_FIELDS.structure);
  return (
    <section aria-label={t("title")} data-form-field={MATRIX_FIELDS.structure} className="flex flex-col gap-4 rounded-md border border-border p-4">
      <div className="flex flex-col gap-0.5">
        <h3 className="text-title font-semibold text-text">{t("title")}</h3>
        <p className="text-caption text-text-muted">{t("hint")}</p>
      </div>
      <div className="grid gap-4 sm:grid-cols-2">
        {twoColumns ? (
          <>
            {single && row("SINGLE", MATRIX_FIELDS.single, t("single"))}
            {set && (structure.flexible ? row("CHILDREN", MATRIX_FIELDS.children, t("children"), t("childrenHint")) : row("SETS", MATRIX_FIELDS.sets, t("sets"), t("setSize", { size: structure.setSize ?? 0 })))}
            {fill && row("FILL", MATRIX_FIELDS.fill, t("fill"))}
          </>
        ) : (
          <>
            {(single || set) && row("CHOICE", MATRIX_FIELDS.choice, t("choice"), t("choiceHint"))}
            {set && !structure.flexible && row("SETS", MATRIX_FIELDS.sets, t("sets"), t("setSize", { size: structure.setSize ?? 0 }))}
            {set && structure.flexible && row("CHILDREN", MATRIX_FIELDS.children, t("children"), t("childrenHint"))}
            {fill && row("FILL", MATRIX_FIELDS.fill, t("fill"), limits && limits.shortAnswerMin > 0 ? t("fillMin", { min: limits.shortAnswerMin }) : undefined)}
          </>
        )}
      </div>
      {!twoColumns && single && <ReadOnlyCount label={t("singleAuto")} value={structure.single} hint={t("singleAutoHint")} />}
      {twoColumns && (single || set) && <ReadOnlyCount label={t("choiceTotal")} value={structure.choice} hint={t("choiceTotalHint")} />}
      {structureError && (
        <p role="alert" className="text-caption text-danger">
          {structureError}
        </p>
      )}
      {entry.setSource && set && <MatrixSetSource {...props} />}
    </section>
  );
}
