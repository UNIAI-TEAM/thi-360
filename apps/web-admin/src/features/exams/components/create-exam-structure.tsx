"use client";

import { Button, Checkbox, cn, FormField, IconButton, Input, Select } from "@thi360/ui";
import { ArrowDown, ArrowUp, Info, Link2, Plus, Trash2 } from "lucide-react";
import { useTranslations } from "next-intl";
import { useId, useState } from "react";
import { Controller, type Control } from "react-hook-form";
import type { CatalogOption, CombinationOption } from "@/features/content/types";
import {
  type CreateCombinationValues,
  type CreateExamFormValues,
  type CreatePartValues,
  defaultSubjectCounts,
  PART_NAME_MAX,
} from "../create-exam-schema";
import { sum } from "../matrix";
import type { ExamFormat, ExamKind } from "../types";
import { BlockNumberInput } from "./block-number-input";
import { CombinationAttachDialog } from "./combination-options";

/** Lỗi của một trường theo đường dẫn (`parts.0.name`); `undefined` khi trường hợp lệ. */
export type FieldErrorLookup = (path: string) => string | undefined;

function safeNumber(value: number): number {
  return Number.isFinite(value) && value > 0 ? value : 0;
}

/** Số đường lựa chọn của một phần tự chọn: nhánh Tiếng Anh cộng số tổ hợp đã gắn. */
export function choicePathCount(part: CreatePartValues): number {
  return (part.englishOption ? 1 : 0) + part.combinations.length;
}

/**
 * Cấu trúc khởi tạo của trang Tạo bài thi (bản vẽ TaoBaiThi mục 2; UniNote PBI-04 mục 5.1–5.2): phần theo thứ tự với
 * tên, môn cố định hoặc nhánh lựa chọn, số câu, thời lượng; đổi thứ tự, xóa, thêm phần (bài FULL). Mỗi phần là một
 * hàng có nhãn riêng thay cho bảng của bản vẽ để ô nhập không phải cuộn ngang ở 360px.
 */
export function CreateExamPartsEditor({
  control,
  parts,
  format,
  subjects,
  subjectName,
  errorFor,
  onMove,
  onRemove,
  onAdd,
}: {
  control: Control<CreateExamFormValues>;
  parts: readonly CreatePartValues[];
  format: ExamFormat;
  subjects: readonly CatalogOption[];
  subjectName: (code: string) => string;
  errorFor: FieldErrorLookup;
  onMove: (index: number, delta: -1 | 1) => void;
  onRemove: (index: number) => void;
  onAdd: () => void;
}) {
  const t = useTranslations("exams.create.parts");
  const subjectOptions = subjects.map((subject) => ({ value: subject.code, label: subject.name }));
  const single = format === "SINGLE";

  return (
    <div className="flex flex-col gap-3">
      <ol aria-label={t("caption")} className="flex flex-col gap-3">
        {parts.map((part, index) => {
          const order = index + 1;
          const base = `parts.${index}` as const;
          return (
            <li key={part.key} className="@container rounded-lg border border-border bg-surface p-3 sm:p-4">
              <div className="mb-2 flex items-center justify-between gap-2">
                <p className="text-caption font-semibold tracking-wide text-text-muted uppercase">{t("orderValue", { order })}</p>
                {!single && (
                  <div className="flex items-center gap-0.5">
                    <IconButton
                      aria-label={t("moveUpAria", { order })}
                      icon={<ArrowUp />}
                      disabled={index === 0}
                      onClick={() => onMove(index, -1)}
                    />
                    <IconButton
                      aria-label={t("moveDownAria", { order })}
                      icon={<ArrowDown />}
                      disabled={index === parts.length - 1}
                      onClick={() => onMove(index, 1)}
                    />
                    <IconButton
                      tone="danger"
                      aria-label={t("removeAria", { order })}
                      icon={<Trash2 />}
                      onClick={() => onRemove(index)}
                    />
                  </div>
                )}
              </div>
              <div className="grid grid-cols-1 gap-3 @lg:grid-cols-2 @3xl:grid-cols-[minmax(0,2fr)_minmax(0,2fr)_minmax(0,1fr)_minmax(0,1fr)]">
                <div data-field={`${base}.name`}>
                  <Controller
                    control={control}
                    name={`${base}.name`}
                    render={({ field }) => (
                      <FormField label={t("name")} required error={errorFor(`${base}.name`)}>
                        {(controlProps) => (
                          <Input
                            {...controlProps}
                            ref={field.ref}
                            name={field.name}
                            value={field.value}
                            onChange={field.onChange}
                            onBlur={field.onBlur}
                            autoComplete="off"
                            maxLength={PART_NAME_MAX}
                          />
                        )}
                      </FormField>
                    )}
                  />
                </div>
                <div data-field={`${base}.subjectCode`}>
                  {part.mode === "CHOICE" ? (
                    <FormField label={t("subject")}>
                      {(controlProps) => (
                        <p {...controlProps} className="flex min-h-10 flex-col justify-center text-body">
                          <span className="font-medium text-text">
                            {part.englishOption ? t("choiceWithEnglish") : t("choiceCombinations")}
                          </span>
                          <span className="text-caption text-text-muted">
                            {t("choicePaths", { count: choicePathCount(part) })}
                          </span>
                        </p>
                      )}
                    </FormField>
                  ) : single ? (
                    <FormField label={t("subject")}>
                      {(controlProps) => (
                        <p {...controlProps} className="flex min-h-10 items-center font-medium text-text">
                          {part.subjectCode ? subjectName(part.subjectCode) : "—"}
                        </p>
                      )}
                    </FormField>
                  ) : (
                    <Controller
                      control={control}
                      name={`${base}.subjectCode`}
                      render={({ field }) => (
                        <FormField label={t("subject")} required error={errorFor(`${base}.subjectCode`)}>
                          {(controlProps) => (
                            <Select
                              {...controlProps}
                              name={field.name}
                              value={field.value || undefined}
                              placeholder={t("subjectPlaceholder")}
                              onValueChange={field.onChange}
                              options={subjectOptions}
                            />
                          )}
                        </FormField>
                      )}
                    />
                  )}
                </div>
                <div data-field={`${base}.questionCount`}>
                  <Controller
                    control={control}
                    name={`${base}.questionCount`}
                    render={({ field }) => (
                      <FormField label={t("questionCount")} required error={errorFor(`${base}.questionCount`)}>
                        {(controlProps) => (
                          <BlockNumberInput
                            {...controlProps}
                            ref={field.ref}
                            name={field.name}
                            value={field.value}
                            onValueChange={field.onChange}
                            onBlur={field.onBlur}
                          />
                        )}
                      </FormField>
                    )}
                  />
                </div>
                <div data-field={`${base}.durationMinutes`}>
                  <Controller
                    control={control}
                    name={`${base}.durationMinutes`}
                    render={({ field }) => (
                      <FormField label={t("duration")} required error={errorFor(`${base}.durationMinutes`)}>
                        {(controlProps) => (
                          <BlockNumberInput
                            {...controlProps}
                            ref={field.ref}
                            name={field.name}
                            value={field.value}
                            onValueChange={field.onChange}
                            onBlur={field.onBlur}
                          />
                        )}
                      </FormField>
                    )}
                  />
                </div>
              </div>
            </li>
          );
        })}
      </ol>

      <div className="flex flex-wrap items-center justify-between gap-3 rounded-md bg-subtle px-3 py-2">
        <p className="flex flex-wrap gap-x-4 gap-y-1 text-body font-semibold text-text tabular-nums">
          <span>{t("total", { count: parts.length })}</span>
          <span>{t("questionsValue", { count: sum(parts.map((part) => safeNumber(part.questionCount))) })}</span>
          <span>{t("minutesValue", { count: sum(parts.map((part) => safeNumber(part.durationMinutes))) })}</span>
        </p>
        {!single && (
          <Button type="button" variant="secondary" size="sm" onClick={onAdd}>
            <Plus />
            <span>{t("add")}</span>
          </Button>
        )}
      </div>
      <p className="flex items-start gap-2 text-caption text-text-muted">
        <Info aria-hidden className="mt-0.5 size-4 shrink-0" />
        {t("templateNote")}
      </p>
    </div>
  );
}

/**
 * Phần tự chọn (bản vẽ TaoBaiThi mục 3; UniNote mục 5.3): cho chọn Tiếng Anh, tổ hợp khoa học đã gắn với số câu từng
 * môn theo thứ tự (mặc định chia đều theo phần dư lớn nhất: 17–17–16 cho HSA, 25–25 cho QDA) và tổng so với số câu của
 * phần ("51 / 50 câu · thừa 1 câu"). Gắn thêm tổ hợp qua hộp thoại.
 */
export function CreateExamChoiceEditor({
  control,
  part,
  partIndex,
  kind,
  combinations,
  subjectName,
  errorFor,
  onCombinationsChange,
  onAnnounce,
  englishAvailable,
}: {
  control: Control<CreateExamFormValues>;
  /** Bài FULL cho chọn Tiếng Anh thay Khoa học; bài lẻ Khoa học thì không. */
  englishAvailable: boolean;
  part: CreatePartValues;
  partIndex: number;
  kind: ExamKind;
  combinations: readonly CombinationOption[];
  subjectName: (code: string) => string;
  errorFor: FieldErrorLookup;
  onCombinationsChange: (next: CreateCombinationValues[]) => void;
  onAnnounce: (message: string) => void;
}) {
  const t = useTranslations("exams.create.choice");
  const idBase = useId();
  const [attachOpen, setAttachOpen] = useState(false);
  const base = `parts.${partIndex}` as const;
  const target = safeNumber(part.questionCount);
  const optionsError = errorFor(`${base}.combinations`);
  const byCode = new Map(combinations.map((combination) => [combination.code, combination]));
  const subjectsOf = (code: string) => byCode.get(code)?.subjectCodes ?? [];
  const available = combinations.filter((combination) => !part.combinations.some((item) => item.code === combination.code));
  const names = {
    subject: subjectName,
    combination: (code: string) => subjectsOf(code).map(subjectName).join(" – "),
  };

  const attach = (combination: CombinationOption) => {
    // Giữ thứ tự của danh mục.
    const codes = new Set([...part.combinations.map((item) => item.code), combination.code]);
    const next = combinations
      .filter((item) => codes.has(item.code))
      .map(
        (item) =>
          part.combinations.find((existing) => existing.code === item.code) ?? {
            code: item.code,
            subjectCounts: defaultSubjectCounts(part.questionCount, item.subjectCodes.length),
          },
      );
    onCombinationsChange(next);
    setAttachOpen(false);
    onAnnounce(t("attached", { code: combination.code }));
  };

  const detach = (code: string) => {
    onCombinationsChange(part.combinations.filter((item) => item.code !== code));
    onAnnounce(t("detached", { code }));
  };

  const setCount = (code: string, subjectIndex: number, value: number) => {
    onCombinationsChange(
      part.combinations.map((item) =>
        item.code === code
          ? { ...item, subjectCounts: item.subjectCounts.map((count, index) => (index === subjectIndex ? value : count)) }
          : item,
      ),
    );
  };

  return (
    <div className="flex flex-col gap-4">
      {englishAvailable && (
        <div data-field={`${base}.englishOption`} className="flex items-start gap-3 rounded-md border border-border p-3">
          <Controller
            control={control}
            name={`${base}.englishOption`}
            render={({ field }) => (
              <Checkbox
                id={`${idBase}-english`}
                ref={field.ref}
                checked={field.value}
                aria-describedby={`${idBase}-english-hint`}
                onCheckedChange={(checked) => field.onChange(checked === true)}
                className="mt-0.5"
              />
            )}
          />
          <div className="flex flex-col gap-0.5">
            <label htmlFor={`${idBase}-english`} className="cursor-pointer font-medium text-text">
              {t("english", { count: target })}
            </label>
            <p id={`${idBase}-english-hint`} className="text-caption text-text-muted">
              {t("englishHint")}
            </p>
          </div>
        </div>
      )}

      <section aria-labelledby={`${idBase}-combinations`} data-field={`${base}.combinations`} className="flex flex-col gap-3">
        <div className="flex flex-wrap items-end justify-between gap-2">
          <div className="flex flex-col gap-0.5">
            <h3 id={`${idBase}-combinations`} className="text-title font-semibold text-text">
              {t("combinations")}
            </h3>
            <p className="text-caption text-text-muted">{kind === "HSA" ? t("hintHSA") : t("hintQDA")}</p>
          </div>
          <Button type="button" variant="secondary" size="sm" onClick={() => setAttachOpen(true)}>
            <Link2 />
            <span>{t("attach")}</span>
          </Button>
        </div>

        {part.combinations.length === 0 ? (
          <p className="rounded-md border border-dashed border-border px-3 py-3 text-body text-text-muted">{t("emptyAllocation")}</p>
        ) : (
          <ul className="flex flex-col gap-2">
            {part.combinations.map((combination, combinationIndex) => {
              const path = `${base}.combinations.${combinationIndex}.subjectCounts`;
              const error = errorFor(path);
              const actual = sum(combination.subjectCounts.map(safeNumber));
              const cellsValid = combination.subjectCounts.every((count) => Number.isInteger(count) && count > 0);
              const ok = cellsValid && actual === target;
              const diff = actual - target;
              const sumId = `${idBase}-${combination.code}-sum`;
              const errorId = `${idBase}-${combination.code}-error`;
              const status =
                diff > 0
                  ? t("sumOver", { count: diff })
                  : diff < 0
                    ? t("sumUnder", { count: -diff })
                    : ok
                      ? null
                      : t("sumInvalid");
              return (
                <li
                  key={combination.code}
                  data-field={path}
                  className={cn(
                    "flex flex-wrap items-end gap-x-3.5 gap-y-2.5 rounded-lg border bg-surface py-2.5 pr-2 pl-3.5",
                    ok ? "border-border" : "border-danger",
                  )}
                >
                  <div className="flex min-w-36 flex-1 basis-40 flex-col gap-0.5 self-center">
                    <span className="font-semibold text-text">{combination.code}</span>
                    <span className="text-caption text-text-muted">{names.combination(combination.code)}</span>
                  </div>
                  {subjectsOf(combination.code).map((subjectCode, subjectIndex) => {
                    const label = t("subjectLabel", { index: subjectIndex + 1, subject: subjectName(subjectCode) });
                    const inputId = `${idBase}-${combination.code}-${subjectIndex}`;
                    return (
                      <div key={subjectCode} className="flex w-24 flex-col gap-1">
                        <label htmlFor={inputId} className="text-caption text-text-muted">
                          {label}
                        </label>
                        <BlockNumberInput
                          id={inputId}
                          value={combination.subjectCounts[subjectIndex] ?? 0}
                          onValueChange={(value) => setCount(combination.code, subjectIndex, value)}
                          aria-invalid={ok ? undefined : true}
                          aria-describedby={[sumId, error ? errorId : null].filter(Boolean).join(" ")}
                          className="h-10"
                        />
                      </div>
                    );
                  })}
                  <output
                    id={sumId}
                    className={cn(
                      "flex h-10 items-center rounded-md px-2.5 text-body font-semibold tabular-nums",
                      ok ? "bg-primary-50 text-primary-text" : "bg-danger-bg text-danger",
                    )}
                  >
                    {t("sumValue", { actual, expected: target })}
                    {status && ` · ${status}`}
                  </output>
                  <IconButton
                    tone="danger"
                    aria-label={t("removeAria", { code: combination.code })}
                    icon={<Trash2 />}
                    onClick={() => detach(combination.code)}
                  />
                  {error && (
                    <p id={errorId} className="w-full text-caption text-danger">
                      {error}
                    </p>
                  )}
                </li>
              );
            })}
          </ul>
        )}
        {optionsError && (
          <p role="alert" className="text-caption text-danger">
            {optionsError}
          </p>
        )}
        <ul className="flex list-disc flex-col gap-1 pl-5 text-caption text-text-muted">
          <li>{t("rules.attached")}</li>
          <li>{t("rules.total", { count: target })}</li>
          <li>{t("rules.fixed")}</li>
          <li>{t("rules.catalog")}</li>
        </ul>
      </section>

      <CombinationAttachDialog
        open={attachOpen}
        onOpenChange={setAttachOpen}
        available={available}
        names={names}
        questionCount={target}
        onAttach={attach}
      />
    </div>
  );
}
