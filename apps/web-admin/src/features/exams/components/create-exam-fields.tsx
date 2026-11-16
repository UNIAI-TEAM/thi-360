"use client";

import { Button, cn, focusRing, FormField, Input, Textarea } from "@thi360/ui";
import { Info, WandSparkles } from "lucide-react";
import { useTranslations } from "next-intl";
import { useCallback, useId, type ReactNode } from "react";
import { Controller, type Control, type FieldErrors, type UseFormRegister } from "react-hook-form";
import {
  createExamInfoSchema,
  EXAM_CODE_MAX,
  EXAM_DESCRIPTION_MAX,
  EXAM_NAME_MAX,
  type CreateExamSchemaMessages,
  type ExamInfoFormValues,
  type ExamInfoSchemaMessages,
  isSingleScope,
  SINGLE_SCOPES,
  type SingleScope,
  type TakenCodes,
} from "../create-exam-schema";
import { sum } from "../matrix";
import type { ExamFormat, ExamKind, ExamTemplate } from "../types";

/** Chữ báo lỗi của Thông tin chung (`exams.create.validation`). */
export function useExamInfoMessages(): ExamInfoSchemaMessages {
  const t = useTranslations("exams.create.validation");
  return {
    nameRequired: t("nameRequired"),
    nameMax: t("nameMax", { max: EXAM_NAME_MAX }),
    codeRequired: t("codeRequired"),
    codeInvalid: t("codeInvalid"),
    codeMax: t("codeMax", { max: EXAM_CODE_MAX }),
    codeTaken: t("codeTaken"),
    subjectRequired: t("subjectRequired"),
    descriptionMax: t("descriptionMax", { max: EXAM_DESCRIPTION_MAX }),
  };
}

export function useExamInfoSchema(takenCodes?: TakenCodes) {
  return createExamInfoSchema(useExamInfoMessages(), takenCodes);
}

/** Chữ báo lỗi của trang Tạo bài thi: Thông tin chung và cấu trúc khởi tạo. */
export function useCreateExamMessages(): CreateExamSchemaMessages {
  const t = useTranslations("exams.create.validation");
  return {
    ...useExamInfoMessages(),
    partNameRequired: t("partNameRequired"),
    partNameMax: t("partNameMax", { max: EXAM_NAME_MAX }),
    partSubjectRequired: t("partSubjectRequired"),
    questionCountPositive: t("questionCountPositive"),
    durationPositive: t("durationPositive"),
    noOptions: t("noOptions"),
    subjectCountPositive: t("subjectCountPositive"),
    allocationTotal: (actual, expected) => t("allocationTotal", { actual, expected }),
    partsRequired: t("partsRequired"),
  };
}

/** Lựa chọn Kỳ thi và Hình thức dạng thẻ (bản vẽ TaoBaiThi). */
export function useScopeOptions() {
  const t = useTranslations("exams.create");
  const kindOptions: ChoiceCardOption<ExamKind>[] = [
    { value: "HSA", label: "HSA", description: t("kindHsaDescription") },
    { value: "QDA", label: "QDA", description: t("kindQdaDescription") },
  ];
  const formatOptions: ChoiceCardOption<ExamFormat>[] = [
    { value: "FULL", label: t("formatFull"), description: t("formatFullDescription") },
    { value: "SINGLE", label: t("formatSingle"), description: t("formatSingleDescription") },
  ];
  return { kindOptions, formatOptions };
}

/** Phần/môn áp dụng của bài lẻ: Toán, Văn, Khoa học, Tiếng Anh (bản vẽ TaoBaiThi). */
export function useSingleScopes() {
  const t = useTranslations("exams.create.singleScope");
  const scopeName = useCallback((scope: string) => (isSingleScope(scope) ? t(scope) : scope), [t]);
  const options: ChoiceCardOption<SingleScope>[] = SINGLE_SCOPES.map((scope) => ({ value: scope, label: t(scope) }));
  return { options, scopeName };
}

export interface ChoiceCardOption<T extends string> {
  value: T;
  label: string;
  description?: string;
}

/**
 * Nhóm radio có tên (DESIGN.md mục 5.2: chọn một trong vài giá trị là nhóm radio, không dùng Tabs). `card` là thẻ chọn
 * của bản vẽ TaoBaiThi, `pill` là nút viên thuốc của ma trận. Mục đang chọn có viền đậm và nền nhạt.
 */
export function ChoiceCardGroup<T extends string>({
  legend,
  name,
  value,
  onChange,
  options,
  required,
  disabled,
  hint,
  variant = "card",
  className,
}: {
  legend: string;
  name: string;
  value: T;
  onChange: (value: T) => void;
  options: readonly ChoiceCardOption<T>[];
  required?: boolean;
  disabled?: boolean;
  hint?: string;
  variant?: "card" | "pill";
  className?: string;
}) {
  const hintId = useId();
  return (
    <fieldset
      className={cn("flex min-w-0 flex-col gap-1.5", className)}
      aria-describedby={hint ? hintId : undefined}
      disabled={disabled}
    >
      <legend className="mb-1.5 text-field-label font-medium text-text-secondary">
        {legend}
        {required && (
          <span aria-hidden className="ml-0.5 text-danger">
            *
          </span>
        )}
      </legend>
      <div className={cn(variant === "card" ? "grid grid-cols-[repeat(auto-fit,minmax(min(9rem,100%),1fr))] gap-2" : "flex flex-wrap gap-2")}>
        {options.map((option) => {
          const checked = option.value === value;
          return (
            <label
              key={option.value}
              className={cn(
                "flex min-w-0 cursor-pointer items-start gap-2.5 rounded-md border transition-colors duration-150 motion-reduce:transition-none",
                variant === "card" ? "p-3" : "min-h-10 items-center px-3.5",
                checked ? "border-primary-text bg-primary-50" : "border-border bg-surface hover:border-border-strong",
                disabled && "cursor-not-allowed opacity-60",
              )}
            >
              <input
                type="radio"
                name={name}
                value={option.value}
                checked={checked}
                required={required}
                onChange={() => onChange(option.value)}
                className={cn("mt-0.5 size-4 shrink-0 accent-primary-fill", variant === "pill" && "mt-0", focusRing)}
              />
              <span className="flex min-w-0 flex-col gap-0.5">
                <span className={cn("font-semibold", checked ? "text-primary-text" : "text-text")}>{option.label}</span>
                {option.description && <span className="text-caption text-text-muted">{option.description}</span>}
              </span>
            </label>
          );
        })}
      </div>
      {hint && (
        <p id={hintId} className="text-caption text-text-muted">
          {hint}
        </p>
      )}
    </fieldset>
  );
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

/**
 * Các trường Thông tin chung của tab Thông tin chung (UniNote PBI-04 mục 4). Mẫu cấu trúc đi theo kỳ thi và hình thức,
 * không chọn tay. `lockScope` khóa kỳ thi, hình thức, môn (bài đã công bố).
 */
export function ExamInfoFields({
  control,
  register,
  errors,
  values,
  lockScope = false,
  lockScopeHint,
  onScopeChange,
  onGenerateCode,
  generatingCode,
}: {
  control: Control<ExamInfoFormValues>;
  register: UseFormRegister<ExamInfoFormValues>;
  errors: FieldErrors<ExamInfoFormValues>;
  values: Pick<ExamInfoFormValues, "kind" | "format">;
  lockScope?: boolean;
  lockScopeHint?: string;
  /** Gọi sau khi đổi kỳ thi hoặc hình thức, để chọn lại mẫu theo kỳ thi và hình thức. */
  onScopeChange: (next: { kind: ExamKind; format: ExamFormat }) => void;
  onGenerateCode?: () => void;
  generatingCode?: boolean;
}) {
  const t = useTranslations("exams.create");
  const { kindOptions, formatOptions } = useScopeOptions();
  const singleScopes = useSingleScopes();

  return (
    <div className="flex flex-col gap-4">
      <div className="grid grid-cols-1 gap-4 sm:grid-cols-[3fr_2fr]">
        <FormField label={t("name")} required error={errors.name?.message}>
          {(controlProps) => (
            <Input
              {...controlProps}
              {...register("name")}
              autoComplete="off"
              maxLength={EXAM_NAME_MAX}
              placeholder={t("namePlaceholder")}
            />
          )}
        </FormField>
        <FormField label={t("code")} required hint={t("codeHint")} error={errors.code?.message}>
          {(controlProps) => (
            <div className="flex gap-2">
              <Input
                {...controlProps}
                {...register("code")}
                autoComplete="off"
                autoCapitalize="characters"
                spellCheck={false}
                maxLength={EXAM_CODE_MAX}
                className="min-w-0 flex-1"
              />
              {onGenerateCode && <GenerateCodeButton onClick={onGenerateCode} loading={generatingCode} />}
            </div>
          )}
        </FormField>
      </div>

      <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
        <Controller
          control={control}
          name="kind"
          render={({ field }) => (
            <ChoiceCardGroup
              legend={t("kind")}
              name={field.name}
              value={field.value}
              required
              disabled={lockScope}
              options={kindOptions}
              onChange={(kind) => {
                field.onChange(kind);
                onScopeChange({ kind, format: values.format });
              }}
            />
          )}
        />
        <Controller
          control={control}
          name="format"
          render={({ field }) => (
            <ChoiceCardGroup
              legend={t("format")}
              name={field.name}
              value={field.value}
              required
              disabled={lockScope}
              options={formatOptions}
              onChange={(format) => {
                field.onChange(format);
                onScopeChange({ kind: values.kind, format });
              }}
            />
          )}
        />
      </div>
      {lockScope && lockScopeHint && (
        <p className="flex items-start gap-2 text-caption text-text-muted">
          <Info aria-hidden className="mt-0.5 size-4 shrink-0" />
          {lockScopeHint}
        </p>
      )}

      {values.format === "SINGLE" && (
        <Controller
          control={control}
          name="singleSubjectCode"
          render={({ field }) => (
            <div className="flex flex-col gap-1">
              <ChoiceCardGroup
                legend={t("singleSubject")}
                name={field.name}
                value={(isSingleScope(field.value) ? field.value : "") as SingleScope}
                required
                disabled={lockScope}
                variant="pill"
                hint={t("singleSubjectHint")}
                options={singleScopes.options}
                onChange={field.onChange}
              />
              {errors.singleSubjectCode?.message && (
                <p role="alert" className="text-caption text-danger">
                  {errors.singleSubjectCode.message}
                </p>
              )}
            </div>
          )}
        />
      )}

      <FormField label={t("descriptionLabel")} error={errors.description?.message}>
        {(controlProps) => (
          <Textarea
            {...controlProps}
            {...register("description")}
            rows={2}
            maxLength={EXAM_DESCRIPTION_MAX}
            placeholder={t("descriptionPlaceholder")}
          />
        )}
      </FormField>
    </div>
  );
}

/** Bảng "Cấu trúc sẽ nạp" của bản vẽ TaoBaiThi: phần, số câu, thời lượng của mẫu đã chọn. */
export function TemplatePreview({
  template,
  format,
  subjectName,
  headingLevel = 3,
  footer,
}: {
  template: ExamTemplate | undefined;
  format: ExamFormat;
  subjectName: string | null;
  headingLevel?: 2 | 3;
  footer?: ReactNode;
}) {
  const t = useTranslations("exams.create.preview");
  const headingId = useId();
  const Heading = headingLevel === 2 ? "h2" : "h3";
  const rows =
    format === "SINGLE"
      ? [
          {
            name: subjectName ?? t("subjectPending"),
            questionCount: template?.parts[0]?.questionCount ?? 0,
            durationMinutes: template?.parts[0]?.durationMinutes ?? 0,
            note: template ? null : t("emptyNote"),
          },
        ]
      : (template?.parts ?? []).map((part) => ({
          name: part.name,
          questionCount: part.questionCount,
          durationMinutes: part.durationMinutes,
          note: part.mode === "CHOICE" ? t("choiceNote") : null,
        }));

  return (
    <section aria-labelledby={headingId} className="flex flex-col gap-3 rounded-lg border border-border bg-subtle p-4">
      <div className="flex flex-wrap items-baseline justify-between gap-2">
        <Heading id={headingId} className="text-title font-semibold text-text">
          {template ? t("title", { name: template.name }) : t("titleEmpty")}
        </Heading>
        <span className="text-caption text-text-muted">{t("editable")}</span>
      </div>
      <div role="region" tabIndex={0} aria-labelledby={headingId} className={cn("overflow-x-auto rounded-md", focusRing)}>
        <table className="w-full min-w-72 border-separate border-spacing-0 text-body tabular-nums">
          <caption className="sr-only">{t("caption")}</caption>
          <thead>
            <tr className="text-left text-caption text-text-muted">
              <th scope="col" className="border-b border-border py-2 pr-3 font-semibold">
                {t("part")}
              </th>
              <th scope="col" className="border-b border-border px-2 py-2 text-right font-semibold">
                {t("questions")}
              </th>
              <th scope="col" className="border-b border-border py-2 pl-2 text-right font-semibold">
                {t("duration")}
              </th>
            </tr>
          </thead>
          <tbody>
            {rows.map((row, index) => (
              <tr key={index}>
                <th scope="row" className="border-b border-border py-2 pr-3 text-left font-medium text-text">
                  {row.name}
                  {row.note && <span className="block text-caption font-normal text-text-muted">{row.note}</span>}
                </th>
                <td className="border-b border-border px-2 py-2 text-right whitespace-nowrap">
                  {t("questionsValue", { count: row.questionCount })}
                </td>
                <td className="border-b border-border py-2 pl-2 text-right whitespace-nowrap">
                  {t("durationValue", { count: row.durationMinutes ?? 0 })}
                </td>
              </tr>
            ))}
          </tbody>
          <tfoot>
            <tr className="font-semibold text-text">
              <th scope="row" className="py-2 pr-3 text-left">
                {t("total")}
              </th>
              <td className="px-2 py-2 text-right whitespace-nowrap">
                {t("questionsValue", { count: sum(rows.map((row) => row.questionCount)) })}
              </td>
              <td className="py-2 pl-2 text-right whitespace-nowrap">
                {t("durationValue", { count: sum(rows.map((row) => row.durationMinutes ?? 0)) })}
              </td>
            </tr>
          </tfoot>
        </table>
      </div>
      <p className="flex items-start gap-2 text-caption text-text-muted">
        <Info aria-hidden className="mt-0.5 size-4 shrink-0" />
        {t("note")}
      </p>
      {footer}
    </section>
  );
}
