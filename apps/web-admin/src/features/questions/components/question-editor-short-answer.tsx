"use client";

import { Button, FormField, IconButton, Input, Select } from "@thi360/ui";
import { Plus, X } from "lucide-react";
import { useTranslations } from "next-intl";
import { useId } from "react";
import { Controller, useFieldArray, useFormContext } from "react-hook-form";
import { draftRowId, type QuestionEditorValues } from "../editor-form";
import { ANSWER_FORMATS } from "../types";
import { FieldErrorText, GroupError, useAnnounce, useEditorError, type BodyPath } from "./question-editor-shared";

const NONE = "__none__";

/**
 * Đáp án của câu Trả lời ngắn (spec 4.3): đáp án chuẩn đứng đầu, sau là các đáp án được chấp nhận (khớp đúng sau
 * chuẩn hóa, không tự nhận cách viết tương đương), kèm hướng dẫn nhập cho học viên. Lưu ở ô trống đầu tiên của biểu mẫu.
 */
export function ShortAnswerEditor({ prefix }: { prefix: BodyPath }) {
  const t = useTranslations("questions.editor.shortAnswer");
  const tFormat = useTranslations("questions.detail.body.answerFormats");
  const announce = useAnnounce();
  const groupId = useId();
  const { control, register } = useFormContext<QuestionEditorValues>();
  const { fields, append, remove } = useFieldArray({ control, name: `${prefix}.blanks.0.answers`, keyName: "rowKey" });
  const groupError = useEditorError(`${prefix}.blanks.0.answers`);

  return (
    <div className="flex flex-col gap-3">
      <p className="text-body text-text-muted">{t("hint")}</p>
      <GroupError id={`${groupId}-error`} message={groupError} />
      <ul className="m-0 flex list-none flex-col gap-2 p-0">
        {fields.map((field, index) => (
          <AnswerRow
            key={field.rowKey}
            prefix={prefix}
            index={index}
            canRemove={fields.length > 1}
            onRemove={() => {
              remove(index);
              announce(t("removed", { number: index + 1 }));
            }}
            register={register}
          />
        ))}
      </ul>
      <Button
        type="button"
        variant="outline"
        size="sm"
        className="self-start"
        onClick={() => {
          append({ id: draftRowId("a"), value: "" }, { shouldFocus: true, focusName: `${prefix}.blanks.0.answers.${fields.length}.value` });
          announce(t("added", { number: fields.length + 1 }));
        }}
      >
        <Plus aria-hidden />
        {t("add")}
      </Button>
      <FormField label={t("inputHint")} hint={t("inputHintHint")}>
        {(controlProps) => <Input {...controlProps} autoComplete="off" {...register(`${prefix}.inputHint`)} />}
      </FormField>
      <Controller
        control={control}
        name={`${prefix}.answerFormat`}
        render={({ field }) => (
          <FormField label={t("answerFormat")} hint={t("answerFormatHint")}>
            {(controlProps) => (
              <Select
                {...controlProps}
                name={field.name}
                value={field.value || NONE}
                options={[
                  { value: NONE, label: t("answerFormatNone") },
                  ...ANSWER_FORMATS.map((format) => ({ value: format, label: tFormat(format) })),
                ]}
                onValueChange={(value) => field.onChange(value === NONE ? "" : value)}
              />
            )}
          </FormField>
        )}
      />
    </div>
  );
}

function AnswerRow({
  prefix,
  index,
  canRemove,
  onRemove,
  register,
}: {
  prefix: BodyPath;
  index: number;
  canRemove: boolean;
  onRemove: () => void;
  register: ReturnType<typeof useFormContext<QuestionEditorValues>>["register"];
}) {
  const t = useTranslations("questions.editor.shortAnswer");
  const inputId = useId();
  const path = `${prefix}.blanks.0.answers.${index}.value` as const;
  const error = useEditorError(path);
  return (
    <li className="flex flex-col gap-1">
      <div className="flex items-center gap-2">
        <Input
          id={inputId}
          aria-label={index === 0 ? t("standardLabel") : t("acceptedLabel", { number: index })}
          aria-invalid={error ? true : undefined}
          aria-describedby={error ? `${inputId}-error` : undefined}
          autoComplete="off"
          spellCheck={false}
          className="min-w-0 flex-1"
          {...register(path)}
        />
        {canRemove && <IconButton tone="danger" aria-label={t("remove", { number: index + 1 })} icon={<X />} onClick={onRemove} />}
      </div>
      <FieldErrorText id={`${inputId}-error`} message={error} />
    </li>
  );
}
