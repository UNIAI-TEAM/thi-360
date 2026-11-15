"use client";

import { Button, Checkbox, cn, Input } from "@thi360/ui";
import { Plus } from "lucide-react";
import { useTranslations } from "next-intl";
import { useId, useRef } from "react";
import { Controller, useFieldArray, useFormContext, useWatch } from "react-hook-form";
import { emptyOption, type QuestionEditorValues } from "../editor-form";
import {
  FieldErrorText,
  GroupError,
  optionLetter,
  RowActions,
  useAnnounce,
  useEditorError,
  type BodyPath,
} from "./question-editor-shared";

/**
 * Phương án của Trắc nghiệm 1 đáp án (ô tròn, đúng một đáp án) và nhiều đáp án (ô vuông, ít nhất một),
 * canvas `CauHoi1DapAnForm`, `CauHoiNhieuDapAnForm`. Đổi thứ tự bằng nút lên/xuống thay cho tay kéo của bản vẽ.
 */
export function ChoiceOptionsEditor({ prefix, multiple }: { prefix: BodyPath; multiple: boolean }) {
  const t = useTranslations("questions.editor.choice");
  const announce = useAnnounce();
  const groupId = useId();
  const addRef = useRef<HTMLButtonElement>(null);
  const { control, setValue, getValues, formState, trigger } = useFormContext<QuestionEditorValues>();
  const { fields, append, remove, move } = useFieldArray({
    control,
    name: `${prefix}.options`,
    keyName: "rowKey",
  });
  const options = useWatch({ control, name: `${prefix}.options` }) ?? [];
  const correctCount = options.filter((option) => option.correct).length;
  const groupError = useEditorError(`${prefix}.options`);

  const chooseSingle = (index: number) => {
    getValues(`${prefix}.options`).forEach((_, other) => {
      setValue(`${prefix}.options.${other}.correct`, other === index, { shouldDirty: true });
    });
    if (formState.isSubmitted) void trigger(`${prefix}.options`);
    announce(t("correctChosen", { letter: optionLetter(index) }));
  };

  return (
    <fieldset className="m-0 flex min-w-0 flex-col gap-3 border-0 p-0" aria-describedby={`${groupId}-hint`}>
      <legend className="sr-only">{multiple ? t("legendMultiple") : t("legendSingle")}</legend>
      <div className="flex flex-wrap items-center justify-between gap-2">
        <p id={`${groupId}-hint`} className="text-body text-text-muted">
          {multiple ? t("hintMultiple") : t("hintSingle")}
        </p>
        {multiple && (
          <span className="text-caption font-medium text-text-secondary">{t("correctCount", { count: correctCount })}</span>
        )}
      </div>
      <GroupError id={`${groupId}-error`} message={groupError} />
      <ol className="m-0 flex list-none flex-col gap-2 p-0">
        {fields.map((field, index) => (
          <OptionRow
            key={field.rowKey}
            prefix={prefix}
            index={index}
            count={fields.length}
            multiple={multiple}
            correct={options[index]?.correct ?? false}
            groupName={groupId}
            onChooseSingle={() => chooseSingle(index)}
            onMove={(from, to) => {
              move(from, to);
              announce(t("moved", { letter: optionLetter(from), position: to + 1 }));
            }}
            onRemove={() => {
              remove(index);
              announce(t("removed", { letter: optionLetter(index) }));
              addRef.current?.focus();
            }}
          />
        ))}
      </ol>
      <div className="flex flex-wrap items-center justify-between gap-3">
        <Button
          ref={addRef}
          type="button"
          variant="outline"
          size="sm"
          onClick={() => {
            append(emptyOption(), { shouldFocus: true, focusName: `${prefix}.options.${fields.length}.content` });
            announce(t("added", { letter: optionLetter(fields.length) }));
          }}
        >
          <Plus aria-hidden />
          {t("add")}
        </Button>
        <Controller
          control={control}
          name={`${prefix}.shuffleOptions`}
          render={({ field }) => (
            <label className="flex min-h-10 items-center gap-2.5 text-body text-text">
              <Checkbox checked={field.value} onCheckedChange={(checked) => field.onChange(checked === true)} />
              {t("shuffle")}
            </label>
          )}
        />
      </div>
    </fieldset>
  );
}

function OptionRow({
  prefix,
  index,
  count,
  multiple,
  correct,
  groupName,
  onChooseSingle,
  onMove,
  onRemove,
}: {
  prefix: BodyPath;
  index: number;
  count: number;
  multiple: boolean;
  correct: boolean;
  groupName: string;
  onChooseSingle: () => void;
  onMove: (from: number, to: number) => void;
  onRemove: () => void;
}) {
  const t = useTranslations("questions.editor.choice");
  const { register, setValue, formState, trigger } = useFormContext<QuestionEditorValues>();
  const letter = optionLetter(index);
  const inputId = useId();
  const error = useEditorError(`${prefix}.options.${index}.content`);
  return (
    <li
      className={cn(
        "flex flex-col gap-1.5 rounded-lg border p-2.5",
        correct ? "border-primary-600 bg-primary-50" : "border-border bg-surface",
      )}
    >
      <div className="flex flex-wrap items-center gap-2 @md:flex-nowrap">
        <label className="flex min-h-10 shrink-0 items-center gap-2 font-semibold text-text">
          <input
            type={multiple ? "checkbox" : "radio"}
            name={`${groupName}-correct`}
            checked={correct}
            onChange={(event) => {
              if (multiple) {
                setValue(`${prefix}.options.${index}.correct`, event.target.checked, { shouldDirty: true });
                if (formState.isSubmitted) void trigger(`${prefix}.options`);
              } else {
                onChooseSingle();
              }
            }}
            aria-label={t("correctLabel", { letter })}
            className="size-4 accent-primary-fill"
          />
          <span aria-hidden>{letter}.</span>
        </label>
        <Input
          id={inputId}
          aria-label={t("contentLabel", { letter })}
          aria-invalid={error ? true : undefined}
          aria-describedby={error ? `${inputId}-error` : undefined}
          className="min-w-0 flex-1 basis-48"
          autoComplete="off"
          {...register(`${prefix}.options.${index}.content`)}
        />
        <RowActions index={index} count={count} name={t("rowName", { letter })} onMove={onMove} onRemove={onRemove} />
      </div>
      {correct && <span className="ps-7 text-caption font-medium text-primary-text">{t("correctBadge")}</span>}
      <FieldErrorText id={`${inputId}-error`} message={error} />
    </li>
  );
}
