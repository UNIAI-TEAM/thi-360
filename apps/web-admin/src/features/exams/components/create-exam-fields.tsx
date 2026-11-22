"use client";

import { Button, Checkbox, cn, focusRing, FormField, Input, type InputProps, Select, Textarea } from "@thi360/ui";
import { Info, TriangleAlert, WandSparkles } from "lucide-react";
import { useTranslations } from "next-intl";
import { useCallback, useId, useMemo, useState, type ReactNode } from "react";
import { Controller, type Control, type FieldErrors, type UseFormRegister } from "react-hook-form";
import type { ExamKindOption } from "@/features/content/types";
import { useContentReference, useReferenceLookup } from "@/features/content/queries";
import { RichTextEditor } from "@/components/rich-text";
import {
  createExamInfoSchema,
  EXAM_CODE_MAX,
  EXAM_DESCRIPTION_MAX,
  EXAM_NAME_MAX,
  type CreateExamSchemaMessages,
  type ExamInfoFormValues,
  type ExamInfoSchemaMessages,
  isPartCode,
  kindOptionFor,
  scopesForKind,
  type TakenCodes,
  yearsDiffer,
} from "../create-exam-schema";
import type { ExamKind, ExamScope, ExamTemplate, ExamTiming } from "../types";

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
    pointsPositive: t("pointsPositive"),
    noOptions: t("noOptions"),
    subjectCountPositive: t("subjectCountPositive"),
    allocationTotal: (actual, expected) => t("allocationTotal", { actual, expected }),
    commonTooLarge: (common, total) => t("commonTooLarge", { common, total }),
    partsRequired: t("partsRequired"),
  };
}

/** Tên ngắn của kỳ thi (`exams.kind.*`); mã mới của danh mục chưa có bản dịch thì hiện chính mã. */
export function useKindLabel() {
  const t = useTranslations("exams.kind");
  return useCallback((code: string) => (t.has(code) ? t(code) : code), [t]);
}

/** Phạm vi bài thi dạng thẻ (PBI-04 mục 4), chỉ các phạm vi kỳ thi cho phép. */
export function useScopeOptions(scopes: readonly ExamScope[]): ChoiceCardOption<ExamScope>[] {
  const t = useTranslations("exams.create");
  const all: ChoiceCardOption<ExamScope>[] = [
    { value: "FULL", label: t("scopeFull"), description: t("scopeFullDescription") },
    { value: "PART", label: t("scopePart"), description: t("scopePartDescription") },
    { value: "SUBJECT", label: t("scopeSubject"), description: t("scopeSubjectDescription") },
  ];
  return all.filter((option) => scopes.includes(option.value));
}

/**
 * Phần của bài Theo phần: Toán, Văn, Khoa học, Tiếng Anh (bản vẽ TaoBaiThi) và các mã phần riêng có thông điệp
 * (`partName.LANGUAGE`… của V-ACT, spec bổ sung 5.3). Mã không có thông điệp (dữ liệu thật dùng mã môn của danh
 * mục như `TOAN`, `VAN`) thì hiện tên môn theo ngôn ngữ đang chọn; không có trong danh mục mới hiện chính mã.
 */
export function usePartNames() {
  const t = useTranslations("exams.create.partName");
  const subjects = useReferenceLookup(useContentReference().data);
  const partName = useCallback(
    (code: string) => (isPartCode(code) || t.has(code) ? t(code) : subjects.subject(code)),
    [t, subjects],
  );
  return { partName };
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

const DECIMAL = /^\d+([.,]\d{1,2})?$/;

/** Đọc chữ đã gõ: ô trống là `null`; sai định dạng trả `undefined`. */
function parseOptionalNumber(text: string): number | null | undefined {
  const trimmed = text.trim();
  if (trimmed === "") return null;
  return DECIMAL.test(trimmed) ? Number(trimmed.replace(",", ".")) : undefined;
}

function formatOptionalNumber(value: number | null): string {
  return value === null ? "" : String(value).replace(".", ",");
}

export type OptionalNumberInputProps = Omit<InputProps, "value" | "onChange" | "type" | "inputMode"> & {
  value: number | null;
  onValueChange: (value: number | null) => void;
};

/**
 * Ô số thập phân được để trống (thang điểm, điểm của phần): `type="text"` + `inputMode="decimal"`, ô trống là `null`,
 * nhận dấu phẩy hoặc chấm, tối đa hai chữ số sau dấu phẩy. Chữ sai định dạng thì `aria-invalid` và giữ giá trị cũ.
 */
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

/**
 * Kỳ thi: thẻ chọn của bản vẽ TaoBaiThi, một thẻ cho mỗi kỳ thi của danh mục (mã ngắn và tên đầy đủ). Gợi ý dưới nhóm
 * nêu cách lấy mẫu (theo kỳ thi hay theo môn) và cách tính thời lượng của kỳ thi đang chọn.
 */
export function KindField({
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
  const options = kinds.map((kind) => ({ value: kind.code, label: kindLabel(kind.code), description: kind.name }));
  const hint = current
    ? t(current.templateBy === "SUBJECT" ? "kindHintSubject" : "kindHintKind", {
        kind: kindLabel(current.code),
        timing: t(`timingMode.${current.timing}`),
      })
    : undefined;
  return (
    <ChoiceCardGroup
      legend={t("kind")}
      name="kind"
      value={value}
      required
      disabled={disabled}
      hint={hint}
      options={options}
      onChange={onChange}
    />
  );
}

/**
 * Kỳ thi dạng ô chọn của bản vẽ TaoBaiThi2B (bước 1, khối Thông tin chung): một mục cho mỗi kỳ thi của danh mục, tên
 * đầy đủ từ danh mục. Gợi ý dưới ô nêu cách lấy mẫu và cách tính thời lượng của kỳ thi đang chọn (như `KindField`).
 */
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

/**
 * Phạm vi bài thi (Toàn bài, Theo phần, Theo môn) theo `scopes` của kỳ thi. Kỳ thi chỉ có một phạm vi thì không cần
 * chọn: hiện một dòng nêu phạm vi thay cho nhóm radio một lựa chọn.
 */
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

/** Năm của mẫu, không định dạng số (2025, không phải 2.025). */
function yearText(year: number | null | undefined): string {
  return year === null || year === undefined ? "" : String(year);
}

/**
 * Ghi chú của mẫu đã nạp: năm cấu trúc khác năm ma trận thì cảnh báo, kèm ô xác nhận chuyên môn khi có
 * `onYearsConfirmedChange` (chưa xác nhận thì kiểm tra cấu hình chặn công bố), không thì chỉ nhắc xác nhận sau khi tạo.
 * Hai năm trùng nhau thì không dựng gì.
 */
export function TemplateNotes({
  template,
  yearsConfirmed = false,
  onYearsConfirmedChange,
  disabled,
}: {
  template: ExamTemplate | undefined;
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
            <label htmlFor={checkboxId} className="cursor-pointer font-medium text-text">
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

/**
 * Thời lượng và thang điểm: kỳ thi dùng thời lượng chung có một ô thời lượng chung (phần không có ô thời lượng); thang
 * điểm bắt buộc trừ kỳ thi không có thang cố định (V-ACT để trống được).
 */
export function TimingScoreFields({
  timing,
  sharedDuration,
  onSharedDurationChange,
  sharedDurationError,
  partMinutes,
  maxScore,
  onMaxScoreChange,
  maxScoreError,
  maxScoreOptional,
  disabled,
  children,
}: {
  timing: ExamTiming;
  sharedDuration: number;
  onSharedDurationChange: (minutes: number) => void;
  sharedDurationError?: string;
  /** Tổng thời lượng các phần khi tính theo phần (chỉ đọc). */
  partMinutes: number;
  maxScore: number | null;
  onMaxScoreChange: (value: number | null) => void;
  maxScoreError?: string;
  maxScoreOptional: boolean;
  disabled?: boolean;
  children?: ReactNode;
}) {
  const t = useTranslations("exams.create.timing");
  return (
    <div className="flex flex-col gap-4">
      <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
        {timing === "SHARED" ? (
          <div data-field="sharedDuration">
            <FormField label={t("sharedDuration")} required hint={t("sharedHint")} error={sharedDurationError}>
              {(controlProps) => (
                <OptionalNumberInput
                  {...controlProps}
                  name="sharedDuration"
                  disabled={disabled}
                  value={sharedDuration > 0 ? sharedDuration : null}
                  onValueChange={(value) => onSharedDurationChange(value ?? 0)}
                />
              )}
            </FormField>
          </div>
        ) : (
          <div className="flex flex-col gap-1.5">
            <p className="text-field-label font-medium text-text-secondary">{t("perPart")}</p>
            <p className="flex min-h-10 flex-col justify-center text-body">
              <span className="font-semibold text-text tabular-nums">{t("minutesValue", { count: partMinutes })}</span>
              <span className="text-caption text-text-muted">{t("perPartHint")}</span>
            </p>
          </div>
        )}
        <div data-field="maxScore">
          <FormField
            label={t("maxScore")}
            required={!maxScoreOptional}
            hint={maxScoreOptional ? t("maxScoreOptionalHint") : t("maxScoreHint")}
            error={maxScoreError}
          >
            {(controlProps) => (
              <OptionalNumberInput
                {...controlProps}
                name="maxScore"
                disabled={disabled}
                value={maxScore}
                onValueChange={onMaxScoreChange}
              />
            )}
          </FormField>
        </div>
      </div>
      {children}
    </div>
  );
}

/**
 * Các trường Thông tin chung của tab Thông tin chung (UniNote PBI-04 mục 4; spec 7 kỳ thi): tên, mã, kỳ thi, phạm vi,
 * phần hoặc môn, ghi chú của mẫu đã nạp, thời lượng chung, thang điểm, mô tả, hướng dẫn làm bài. Phần kỳ thi, phạm vi,
 * phần, môn do `scopeFields` dựng (cùng nhóm chọn với bước 1, `ExamKindScopeFields`); `lockScope` chỉ thêm dòng giải
 * thích khi bài đã công bố. Phiên bản năm chọn ở bước Cấu trúc.
 */
export function ExamInfoFields({
  control,
  register,
  errors,
  values,
  kinds,
  template,
  partMinutes,
  scopeFields,
  lockScope = false,
  lockScopeHint,
  onGenerateCode,
  generatingCode,
}: {
  control: Control<ExamInfoFormValues>;
  register: UseFormRegister<ExamInfoFormValues>;
  errors: FieldErrors<ExamInfoFormValues>;
  values: ExamInfoFormValues;
  kinds: readonly ExamKindOption[];
  /** Mẫu đã nạp của bài (hoặc mẫu của phạm vi đang chọn); `undefined` khi không có mẫu. */
  template: ExamTemplate | undefined;
  /** Tổng thời lượng các phần đang lưu (bài tính thời lượng theo phần). */
  partMinutes: number;
  /** Nhóm chọn kỳ thi, phạm vi, phần, môn (`ExamKindScopeFields`). */
  scopeFields: ReactNode;
  lockScope?: boolean;
  lockScopeHint?: string;
  onGenerateCode?: () => void;
  generatingCode?: boolean;
}) {
  const t = useTranslations("exams.create");
  const tStep = useTranslations("exams.create.step1");
  const kind = kindOptionFor(kinds, values.kind);
  const timingHeadingId = useId();
  // Bài theo phần không có thời lượng mặc định (R-141): ô để trống phải nhập, câu báo như bước 1.
  const sharedDurationError = errors.sharedDuration?.message
    ? values.scope === "PART"
      ? tStep("durationRequiredPart")
      : errors.sharedDuration.message
    : undefined;

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
      {scopeFields}
      {lockScope && lockScopeHint && (
        <p className="flex items-start gap-2 text-caption text-text-muted">
          <Info aria-hidden className="mt-0.5 size-4 shrink-0" />
          {lockScopeHint}
        </p>
      )}

      <Controller
        control={control}
        name="yearsConfirmed"
        render={({ field }) => (
          <TemplateNotes template={template} yearsConfirmed={field.value} onYearsConfirmedChange={field.onChange} />
        )}
      />

      <section aria-labelledby={timingHeadingId} className="flex flex-col gap-3 rounded-lg border border-border p-4">
        <h3 id={timingHeadingId} className="text-title font-semibold text-text">
          {t("sections.timing")}
        </h3>
        <Controller
          control={control}
          name="sharedDuration"
          render={({ field: durationField }) => (
            <Controller
              control={control}
              name="maxScore"
              render={({ field: scoreField }) => (
                <TimingScoreFields
                  timing={values.timing}
                  sharedDuration={durationField.value}
                  onSharedDurationChange={durationField.onChange}
                  sharedDurationError={sharedDurationError}
                  partMinutes={partMinutes}
                  maxScore={scoreField.value}
                  onMaxScoreChange={scoreField.onChange}
                  maxScoreError={errors.maxScore?.message}
                  maxScoreOptional={kind?.maxScore === null || (values.scope === "SUBJECT" && values.buildMode === "REVIEW")}
                />
              )}
            />
          )}
        />
      </section>

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
      <FormField label={t("instructionsLabel")} hint={t("instructionsHint")} error={errors.instructions?.message}>
        {(controlProps) => (
          <Controller
            control={control}
            name="instructions"
            render={({ field }) => (
              <RichTextEditor
                {...controlProps}
                value={field.value}
                onChange={field.onChange}
                aria-label={t("instructionsLabel")}
                allowImages={false}
                fieldContext="instructions"
                invalid={Boolean(errors.instructions)}
                placeholder={t("instructionsPlaceholder")}
              />
            )}
          />
        )}
      </FormField>
    </div>
  );
}
