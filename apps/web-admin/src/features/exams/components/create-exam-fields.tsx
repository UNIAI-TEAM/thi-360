"use client";

import { Button, Checkbox, cn, FormField, Input, type InputProps, Select } from "@thi360/ui";
import { TriangleAlert, WandSparkles } from "lucide-react";
import { useTranslations } from "next-intl";
import { useCallback, useId, useMemo, useState } from "react";
import type { ExamKindOption } from "@/features/content/types";
import { useContentReference, useReferenceLookup } from "@/features/content/queries";
import {
  createExamInfoSchema,
  EXAM_CODE_MAX,
  EXAM_DESCRIPTION_MAX,
  EXAM_NAME_MAX,
  type ExamInfoSchemaMessages,
  isPartCode,
  kindOptionFor,
  scopesForKind,
  type TakenCodes,
  yearsDiffer,
} from "../create-exam-schema";
import type { ExamKind, ExamScope, ExamTemplate } from "../types";
import { ChoiceCardGroup, type ChoiceCardGroupProps, type ChoiceCardOption } from "./choice-card-group";

export { ChoiceCardGroup, type ChoiceCardGroupProps, type ChoiceCardOption };

function useExamInfoMessages(): ExamInfoSchemaMessages {
  const t = useTranslations("exams.create.validation");
  return {
    nameRequired: t("nameRequired"),
    nameMax: t("nameMax", { max: EXAM_NAME_MAX }),
    codeRequired: t("codeRequired"),
    codeInvalid: t("codeInvalid"),
    codeMax: t("codeMax", { max: EXAM_CODE_MAX }),
    codeTaken: t("codeTaken"),
    subjectRequired: t("subjectRequired"),
    kindSubjectRequired: t("kindSubjectRequired"),
    sharedDurationPositive: t("sharedDurationPositive"),
    maxScoreRequired: t("maxScoreRequired"),
    maxScorePositive: t("maxScorePositive"),
    descriptionMax: t("descriptionMax", { max: EXAM_DESCRIPTION_MAX }),
  };
}

export function useExamInfoSchema(takenCodes?: TakenCodes, kinds: readonly ExamKindOption[] = []) {
  const messages = useExamInfoMessages();
  return useMemo(() => createExamInfoSchema(messages, takenCodes, kinds), [messages, takenCodes, kinds]);
}

/** Tên ngắn của kỳ thi (`exams.kind.*`); mã mới của danh mục chưa có bản dịch thì hiện chính mã. */
export function useKindLabel() {
  const t = useTranslations("exams.kind");
  return useCallback((code: string) => (t.has(code) ? t(code) : code), [t]);
}

function useScopeOptions(scopes: readonly ExamScope[]): ChoiceCardOption<ExamScope>[] {
  const t = useTranslations("exams.create");
  const all: ChoiceCardOption<ExamScope>[] = [
    { value: "FULL", label: t("scopeFull"), description: t("scopeFullDescription") },
    { value: "PART", label: t("scopePart"), description: t("scopePartDescription") },
    { value: "SUBJECT", label: t("scopeSubject"), description: t("scopeSubjectDescription") },
  ];
  return all.filter((option) => scopes.includes(option.value));
}

export function usePartNames() {
  const t = useTranslations("exams.create.partName");
  const subjects = useReferenceLookup(useContentReference().data);
  const partName = useCallback(
    (code: string) => (isPartCode(code) || t.has(code) ? t(code) : subjects.subject(code)),
    [t, subjects],
  );
  return { partName };
}

/** Nút "Tự sinh mã" cạnh ô Mã bài thi. */
export function GenerateCodeButton({ onClick, loading }: { onClick: () => void; loading?: boolean }) {
  const t = useTranslations("exams.create");
  return (
    <Button type="button" variant="secondary" onClick={onClick} loading={loading} className="shrink-0">
      <WandSparkles />
      <span>{t("generateCode")}</span>
    </Button>
  );
}

const DECIMAL = /^\d+([.,]\d{1,2})?$/;

function parseOptionalNumber(text: string): number | null | undefined {
  const trimmed = text.trim();
  if (trimmed === "") return null;
  return DECIMAL.test(trimmed) ? Number(trimmed.replace(",", ".")) : undefined;
}

function formatOptionalNumber(value: number | null): string {
  return value === null ? "" : String(value).replace(".", ",");
}

type OptionalNumberInputProps = Omit<InputProps, "value" | "onChange" | "type" | "inputMode"> & {
  value: number | null;
  onValueChange: (value: number | null) => void;
};

export function OptionalNumberInput({ value, onValueChange, className, ...props }: OptionalNumberInputProps) {
  const [text, setText] = useState(() => formatOptionalNumber(value));
  const [lastValue, setLastValue] = useState(value);
  const parsed = parseOptionalNumber(text);
  if (value !== lastValue) {
    setLastValue(value);
    if (parsed !== value) setText(formatOptionalNumber(value));
  }
  return (
    <Input
      {...props}
      type="text"
      inputMode="decimal"
      autoComplete="off"
      value={text}
      aria-invalid={parsed === undefined || props["aria-invalid"] ? true : undefined}
      className={cn("text-right tabular-nums", className)}
      onChange={(event) => {
        const next = event.target.value;
        setText(next);
        const nextValue = parseOptionalNumber(next);
        if (nextValue !== undefined && nextValue !== value) {
          setLastValue(nextValue);
          onValueChange(nextValue);
        }
      }}
    />
  );
}

export function KindSelect({
  kinds,
  value,
  onChange,
  disabled,
}: {
  kinds: readonly ExamKindOption[];
  value: ExamKind;
  onChange: (kind: ExamKind) => void;
  disabled?: boolean;
}) {
  const t = useTranslations("exams.create");
  const kindLabel = useKindLabel();
  const current = kindOptionFor(kinds, value);
  const hint = current
    ? t(current.templateBy === "SUBJECT" ? "kindHintSubject" : "kindHintKind", {
        kind: kindLabel(current.code),
        timing: t(`timingMode.${current.timing}`),
      })
    : undefined;
  return (
    <FormField label={t("kind")} required hint={hint}>
      {(controlProps) => (
        <Select
          {...controlProps}
          name="kind"
          value={value || undefined}
          onValueChange={onChange}
          disabled={disabled}
          options={kinds.map((kind) => ({ value: kind.code, label: kind.name }))}
        />
      )}
    </FormField>
  );
}

export function ScopeField({
  kinds,
  kind,
  value,
  onChange,
  disabled,
}: {
  kinds: readonly ExamKindOption[];
  kind: ExamKind;
  value: ExamScope;
  onChange: (scope: ExamScope) => void;
  disabled?: boolean;
}) {
  const t = useTranslations("exams.create");
  const options = useScopeOptions(scopesForKind(kinds, kind));
  if (options.length <= 1) {
    return (
      <div className="flex flex-col gap-1.5">
        <p className="text-field-label font-medium text-text-secondary">{t("scope")}</p>
        <p className="flex min-h-10 flex-col justify-center text-body">
          <span className="font-semibold text-text">{options[0]?.label ?? t("scopeFull")}</span>
          <span className="text-caption text-text-muted">{t("scopeOnly")}</span>
        </p>
      </div>
    );
  }
  return (
    <ChoiceCardGroup
      legend={t("scope")}
      name="scope"
      value={value}
      required
      disabled={disabled}
      options={options}
      onChange={onChange}
    />
  );
}

function yearText(year: number | null | undefined): string {
  return year === null || year === undefined ? "" : String(year);
}

export function TemplateNotes({
  template,
  yearsConfirmed = false,
  onYearsConfirmedChange,
  disabled,
}: {
  template: Pick<ExamTemplate, "structureYear" | "matrixYear"> | null | undefined;
  yearsConfirmed?: boolean;
  onYearsConfirmedChange?: (confirmed: boolean) => void;
  disabled?: boolean;
}) {
  const t = useTranslations("exams.create.templateNotes");
  const checkboxId = useId();
  const hintId = useId();
  if (!template || !yearsDiffer(template)) return null;
  return (
    <div data-field="yearsConfirmed" className="flex flex-col gap-2 rounded-lg border border-warning/40 bg-warning-bg p-3">
      <p className="flex items-start gap-2 text-body font-medium text-text">
        <TriangleAlert aria-hidden className="mt-0.5 size-4 shrink-0 text-warning" />
        {t("yearsDiffer", {
          structureYear: yearText(template.structureYear),
          matrixYear: yearText(template.matrixYear),
        })}
      </p>
      {onYearsConfirmedChange ? (
        <div className="flex items-start gap-2.5">
          <Checkbox
            id={checkboxId}
            checked={yearsConfirmed}
            disabled={disabled}
            aria-describedby={hintId}
            onCheckedChange={(checked) => onYearsConfirmedChange(checked === true)}
            className="mt-0.5"
          />
          <div className="flex flex-col gap-0.5">
            <label htmlFor={checkboxId} className={cn("font-medium text-text", !disabled && "cursor-pointer")}>
              {t("yearsConfirm", { matrixYear: yearText(template.matrixYear) })}
            </label>
            <p id={hintId} className="text-caption text-text-secondary">
              {t("yearsConfirmHint")}
            </p>
          </div>
        </div>
      ) : (
        <p className="text-caption text-text-secondary">{t("yearsConfirmLater")}</p>
      )}
    </div>
  );
}
