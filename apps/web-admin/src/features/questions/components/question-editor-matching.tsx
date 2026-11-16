"use client";

import { Button, Checkbox, FormField, Input, Label, Select } from "@thi360/ui";
import { Plus } from "lucide-react";
import { useTranslations } from "next-intl";
import { useId, useRef } from "react";
import { Controller, useFieldArray, useFormContext, useWatch } from "react-hook-form";
import {
  DEFAULT_POINTS,
  emptyMatchLeft,
  emptyMatchRight,
  evenSplitText,
  parsePoints,
  pointsTotal,
  type QuestionEditorValues,
} from "../editor-form";
import { PointsTotalLine } from "./question-editor-points-total";
import { RichTextField } from "./question-editor-rich-field";
import {
  FieldErrorText,
  GroupError,
  optionLetter,
  pointsPathOf,
  RowActions,
  useAnnounce,
  useEditorError,
  type BodyPath,
} from "./question-editor-shared";

/**
 * Các cặp của câu Ghép đôi (UniNote PBI-03 mục 10): cột trái (mục cần ghép) và cột phải (lựa chọn, được nhiều hơn cột
 * trái), mỗi mục trái chọn một lựa chọn đúng và có điểm của cặp. "Dùng lại lựa chọn" quyết định một lựa chọn gắn được
 * cho nhiều mục hay một lần; đảo cột chỉ đổi cách hiện, chấm theo mã mục. Tổng điểm các cặp bằng điểm của câu.
 */
export function MatchingEditor({ prefix }: { prefix: BodyPath }) {
  const t = useTranslations("questions.editor.matching");
  const announce = useAnnounce();
  const groupId = useId();
  const addPairRef = useRef<HTMLButtonElement>(null);
  const addRightRef = useRef<HTMLButtonElement>(null);
  const { control, getValues, setValue } = useFormContext<QuestionEditorValues>();
  const left = useFieldArray({ control, name: `${prefix}.left`, keyName: "rowKey" });
  const right = useFieldArray({ control, name: `${prefix}.right`, keyName: "rowKey" });
  const leftValues = useWatch({ control, name: `${prefix}.left` }) ?? [];
  const rightValues = useWatch({ control, name: `${prefix}.right` }) ?? [];
  const pointsText = useWatch({ control, name: pointsPathOf(prefix) }) ?? "";
  const points = parsePoints(pointsText);
  const groupError = useEditorError(`${prefix}.left`);
  const used = new Set(leftValues.map((item) => item.rightId).filter(Boolean));
  const spare = rightValues.filter((item) => !used.has(item.id)).length;

  const rightOptions = rightValues.map((item, index) => ({
    value: item.id,
    label: `${optionLetter(index)}. ${item.content.trim() || t("emptyOption")}`,
  }));

  /** Điểm của câu chia đều cho `count` cặp. */
  const reflowPoints = (count: number) => {
    const share = evenSplitText(count, points > 0 ? points : DEFAULT_POINTS);
    share.forEach((value, index) => setValue(`${prefix}.left.${index}.points`, value, { shouldDirty: true }));
    return t("reflowed", { count });
  };

  return (
    <div className="flex flex-col gap-4">
      <p className="text-body text-text-muted">{t("hint")}</p>
      <p className="text-caption text-text-secondary">
        {t("summary", { left: left.fields.length, right: right.fields.length, spare })}
      </p>
      <GroupError id={`${groupId}-error`} message={groupError} />

      <ol className="m-0 flex list-none flex-col gap-2 p-0">
        {left.fields.map((field, index) => (
          <PairRow
            key={field.rowKey}
            prefix={prefix}
            index={index}
            count={left.fields.length}
            rightOptions={rightOptions}
            onMove={(from, to) => {
              left.move(from, to);
              announce(t("pairMoved", { number: from + 1, position: to + 1 }));
            }}
            onRemove={() => {
              left.remove(index);
              announce(`${t("pairRemoved", { number: index + 1 })} ${reflowPoints(left.fields.length - 1)}`);
              addPairRef.current?.focus();
            }}
          />
        ))}
      </ol>
      <div className="flex flex-wrap items-center gap-2">
        <Button
          ref={addPairRef}
          type="button"
          variant="outline"
          size="sm"
          onClick={() => {
            left.append(emptyMatchLeft(), { shouldFocus: true, focusName: `${prefix}.left.${left.fields.length}.content` });
            announce(`${t("pairAdded", { number: left.fields.length + 1 })} ${reflowPoints(left.fields.length + 1)}`);
          }}
        >
          <Plus aria-hidden />
          {t("addPair")}
        </Button>
      </div>
      <PointsTotalLine
        total={pointsTotal(leftValues.map((item) => item.points))}
        points={points}
        count={left.fields.length}
        onEven={() => announce(reflowPoints(left.fields.length))}
      />

      <fieldset className="m-0 flex min-w-0 flex-col gap-2 border-0 border-t border-border p-0 pt-4">
        <legend className="float-left mb-2 w-full text-label font-semibold text-text">{t("rightLegend")}</legend>
        <p className="clear-both text-caption text-text-muted">{t("rightHint")}</p>
        <ol className="m-0 flex list-none flex-col gap-2 p-0">
          {right.fields.map((field, index) => (
            <RightRow
              key={field.rowKey}
              prefix={prefix}
              index={index}
              count={right.fields.length}
              onMove={(from, to) => {
                right.move(from, to);
                announce(t("rightMoved", { letter: optionLetter(from), position: to + 1 }));
              }}
              onRemove={() => {
                // Lựa chọn bị xóa không còn là đáp án của mục nào: các mục đó phải chọn lại.
                const removedId = getValues(`${prefix}.right.${index}.id`);
                getValues(`${prefix}.left`).forEach((item, leftIndex) => {
                  if (item.rightId === removedId) setValue(`${prefix}.left.${leftIndex}.rightId`, "", { shouldDirty: true });
                });
                right.remove(index);
                announce(t("rightRemoved", { letter: optionLetter(index) }));
                addRightRef.current?.focus();
              }}
            />
          ))}
        </ol>
        <Button
          ref={addRightRef}
          type="button"
          variant="outline"
          size="sm"
          className="self-start"
          onClick={() => {
            right.append(emptyMatchRight(), { shouldFocus: true, focusName: `${prefix}.right.${right.fields.length}.content` });
            announce(t("rightAdded", { letter: optionLetter(right.fields.length) }));
          }}
        >
          <Plus aria-hidden />
          {t("addRight")}
        </Button>
      </fieldset>

      <fieldset className="m-0 flex min-w-0 flex-col gap-1 rounded-lg border border-border p-3">
        <legend className="px-1 text-label font-semibold text-text">{t("optionsLegend")}</legend>
        <div className="flex flex-col">
          <ToggleOption prefix={prefix} name="reuseRight" label={t("reuseRight")} hint={t("reuseRightHint")} />
          <ToggleOption prefix={prefix} name="shuffleLeft" label={t("shuffleLeft")} />
          <ToggleOption prefix={prefix} name="shuffleRight" label={t("shuffleRight")} />
        </div>
      </fieldset>
    </div>
  );
}

function ToggleOption({
  prefix,
  name,
  label,
  hint,
}: {
  prefix: BodyPath;
  name: "reuseRight" | "shuffleLeft" | "shuffleRight";
  label: string;
  hint?: string;
}) {
  const { control } = useFormContext<QuestionEditorValues>();
  const id = useId();
  return (
    <Controller
      control={control}
      name={`${prefix}.${name}`}
      render={({ field }) => (
        <div className="flex flex-col">
          <label className="flex min-h-10 items-center gap-2.5 text-body text-text">
            <Checkbox
              checked={field.value}
              aria-describedby={hint ? `${id}-hint` : undefined}
              onCheckedChange={(checked) => field.onChange(checked === true)}
            />
            {label}
          </label>
          {hint && (
            <p id={`${id}-hint`} className="ps-7 text-caption text-text-muted">
              {hint}
            </p>
          )}
        </div>
      )}
    />
  );
}

function PairRow({
  prefix,
  index,
  count,
  rightOptions,
  onMove,
  onRemove,
}: {
  prefix: BodyPath;
  index: number;
  count: number;
  rightOptions: { value: string; label: string }[];
  onMove: (from: number, to: number) => void;
  onRemove: () => void;
}) {
  const t = useTranslations("questions.editor.matching");
  const tErrors = useTranslations("questions.editor.errors");
  const { control, register, formState, trigger } = useFormContext<QuestionEditorValues>();
  const number = index + 1;
  const rowId = useId();
  const contentError = useEditorError(`${prefix}.left.${index}.content`);
  const pointsError = useEditorError(`${prefix}.left.${index}.points`);
  return (
    <li className="flex flex-col gap-2 rounded-lg border border-border bg-surface p-3">
      <div className="flex items-center justify-between gap-2">
        <h3 className="text-label font-semibold text-text">{t("pairTitle", { number })}</h3>
        <RowActions index={index} count={count} name={t("pairName", { number })} onMove={onMove} onRemove={onRemove} />
      </div>
      <div className="grid gap-3 @lg:grid-cols-[minmax(0,1fr)_minmax(0,1fr)_7rem]">
        <div className="flex flex-col gap-1.5">
          <Label id={`${rowId}-left-label`} htmlFor={`${rowId}-left`}>
            {t("leftLabel")}
          </Label>
          <RichTextField
            id={`${rowId}-left`}
            aria-labelledby={`${rowId}-left-label`}
            aria-invalid={contentError ? true : undefined}
            aria-describedby={contentError ? `${rowId}-left-error` : undefined}
            name={`${prefix}.left.${index}.content`}
          />
          <FieldErrorText id={`${rowId}-left-error`} message={contentError} />
        </div>
        <Controller
          control={control}
          name={`${prefix}.left.${index}.rightId`}
          render={({ field, fieldState }) => (
            <FormField label={t("answerLabel")} error={fieldState.error?.message ? tErrors(fieldState.error.message) : undefined}>
              {(controlProps) => (
                <Select
                  {...controlProps}
                  name={field.name}
                  value={field.value || undefined}
                  placeholder={t("answerPlaceholder")}
                  options={rightOptions}
                  onValueChange={(value) => {
                    field.onChange(value);
                    if (formState.isSubmitted) void trigger(`${prefix}.left`);
                  }}
                />
              )}
            </FormField>
          )}
        />
        <FormField label={t("pointsLabel")} error={pointsError}>
          {(controlProps) => (
            <Input {...controlProps} inputMode="decimal" autoComplete="off" {...register(`${prefix}.left.${index}.points`)} />
          )}
        </FormField>
      </div>
    </li>
  );
}

function RightRow({
  prefix,
  index,
  count,
  onMove,
  onRemove,
}: {
  prefix: BodyPath;
  index: number;
  count: number;
  onMove: (from: number, to: number) => void;
  onRemove: () => void;
}) {
  const t = useTranslations("questions.editor.matching");
  const inputId = useId();
  const letter = optionLetter(index);
  const error = useEditorError(`${prefix}.right.${index}.content`);
  return (
    <li className="flex flex-col gap-1">
      <div className="flex flex-wrap items-center gap-2 @md:flex-nowrap">
        <span aria-hidden className="w-6 shrink-0 font-semibold text-text">
          {letter}.
        </span>
        <div className="min-w-0 flex-1 basis-48">
          <RichTextField
            id={inputId}
            aria-label={t("rightLabel", { letter })}
            aria-invalid={error ? true : undefined}
            aria-describedby={error ? `${inputId}-error` : undefined}
            name={`${prefix}.right.${index}.content`}
          />
        </div>
        <RowActions index={index} count={count} name={t("rightName", { letter })} onMove={onMove} onRemove={onRemove} />
      </div>
      <FieldErrorText id={`${inputId}-error`} message={error} />
    </li>
  );
}
