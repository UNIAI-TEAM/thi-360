"use client";

import { Button, IconButton, Input } from "@thi360/ui";
import { Plus, X } from "lucide-react";
import { useTranslations } from "next-intl";
import { useId, useRef } from "react";
import { useFieldArray, useFormContext, useWatch } from "react-hook-form";
import {
  countBlankMarkers,
  draftRowId,
  emptyBlank,
  insertBlankMarker,
  removeBlankMarker,
  type QuestionEditorValues,
} from "../editor-form";
import { FieldErrorText, GroupError, useAnnounce, useEditorError, type BodyPath, type ContentPath } from "./question-editor-shared";

/**
 * Ô trống của câu Điền đáp án (canvas `CauHoiDienDapAnForm`). Chỗ trống là `___` trong đề bài: "Thêm ô trống" chèn
 * `___` cuối đoạn văn cuối của ô Đề bài và thêm dòng đáp án đúng thứ tự; xóa dòng thì bỏ chỗ trống tương ứng. Mỗi ô có
 * thể có nhiều đáp án chấp nhận. Kiểu đáp án (Số / Văn bản) của bản vẽ chưa có trong mô hình dữ liệu.
 */
export function BlanksEditor({ prefix, contentPath }: { prefix: BodyPath; contentPath: ContentPath }) {
  const t = useTranslations("questions.editor.blanks");
  const announce = useAnnounce();
  const groupId = useId();
  const addRef = useRef<HTMLButtonElement>(null);
  const { control, getValues, setValue, formState, trigger } = useFormContext<QuestionEditorValues>();
  const { fields, insert, remove } = useFieldArray({ control, name: `${prefix}.blanks`, keyName: "rowKey" });
  const content = useWatch({ control, name: contentPath }) ?? "";
  const markers = countBlankMarkers(content);
  const groupError = useEditorError(`${prefix}.blanks`);

  const revalidate = () => {
    if (formState.isSubmitted) void trigger([contentPath, `${prefix}.blanks`]);
  };

  const addBlank = () => {
    const current = getValues(contentPath);
    // Trình soạn HTML không lộ vị trí con trỏ dạng chỉ số chuỗi: chỗ trống mới chèn cuối đoạn văn cuối cùng.
    const position = current.endsWith("</p>") ? current.length - "</p>".length : current.length;
    const result = insertBlankMarker(current, position);
    setValue(contentPath, result.content, { shouldDirty: true });
    insert(result.index, emptyBlank(), { shouldFocus: true, focusName: `${prefix}.blanks.${result.index}.answers.0.value` });
    announce(t("added", { number: result.index + 1 }));
    revalidate();
  };

  return (
    <div className="flex flex-col gap-3">
      <p className="text-body text-text-muted">{t("hint")}</p>
      <p className="text-caption text-text-secondary">
        {t("markerCount", { markers, blanks: fields.length })}
      </p>
      <GroupError id={`${groupId}-error`} message={groupError} />
      <ol className="m-0 flex list-none flex-col gap-2 p-0">
        {fields.map((field, index) => (
          <BlankRow
            key={field.rowKey}
            prefix={prefix}
            index={index}
            onRemove={() => {
              remove(index);
              setValue(contentPath, removeBlankMarker(getValues(contentPath), index), { shouldDirty: true });
              announce(t("removed", { number: index + 1 }));
              addRef.current?.focus();
              revalidate();
            }}
          />
        ))}
      </ol>
      <Button ref={addRef} type="button" variant="outline" size="sm" className="self-start" onClick={addBlank}>
        <Plus aria-hidden />
        {t("add")}
      </Button>
    </div>
  );
}

function BlankRow({ prefix, index, onRemove }: { prefix: BodyPath; index: number; onRemove: () => void }) {
  const t = useTranslations("questions.editor.blanks");
  const announce = useAnnounce();
  const rowId = useId();
  const { control, register } = useFormContext<QuestionEditorValues>();
  const { fields, append, remove } = useFieldArray({
    control,
    name: `${prefix}.blanks.${index}.answers`,
    keyName: "rowKey",
  });
  const error = useEditorError(`${prefix}.blanks.${index}.answers`);
  const pointsError = useEditorError(`${prefix}.blanks.${index}.points`);
  const number = index + 1;
  return (
    <li className="flex flex-col gap-2 rounded-lg border border-border bg-surface p-3">
      <div className="flex flex-wrap items-center justify-between gap-2">
        <h3 id={`${rowId}-title`} className="text-label font-semibold text-text">
          {t("blankTitle", { number })}
        </h3>
        <div className="flex items-center gap-1">
          <label htmlFor={`${rowId}-points`} className="text-caption font-medium text-text-secondary">
            {t("pointsLabel")}
          </label>
          <Input
            id={`${rowId}-points`}
            inputMode="decimal"
            autoComplete="off"
            aria-label={t("pointsAria", { number })}
            aria-invalid={pointsError ? true : undefined}
            aria-describedby={pointsError ? `${rowId}-points-error` : undefined}
            className="w-20"
            {...register(`${prefix}.blanks.${index}.points`)}
          />
          <IconButton tone="danger" aria-label={t("remove", { number })} icon={<X />} onClick={onRemove} />
        </div>
      </div>
      <FieldErrorText id={`${rowId}-points-error`} message={pointsError} />
      <fieldset className="m-0 flex min-w-0 flex-col gap-2 border-0 p-0" aria-labelledby={`${rowId}-title`}>
        <legend className="sr-only">{t("answersLegend", { number })}</legend>
        <ul className="m-0 flex list-none flex-col gap-2 p-0">
          {fields.map((answer, answerIndex) => (
            <AnswerInput
              key={answer.rowKey}
              label={t("answerLabel", { number, index: answerIndex + 1 })}
              removeLabel={t("removeAnswer", { number, index: answerIndex + 1 })}
              canRemove={fields.length > 1}
              path={`${prefix}.blanks.${index}.answers.${answerIndex}.value`}
              registerProps={register(`${prefix}.blanks.${index}.answers.${answerIndex}.value`)}
              invalid={Boolean(error) && answerIndex === 0}
              describedBy={error ? `${rowId}-error` : undefined}
              onRemove={() => remove(answerIndex)}
            />
          ))}
        </ul>
        <GroupError id={`${rowId}-error`} message={error} />
        <Button
          type="button"
          variant="link"
          size="sm"
          className="self-start"
          onClick={() => {
            append(
              { id: draftRowId("a"), value: "" },
              { shouldFocus: true, focusName: `${prefix}.blanks.${index}.answers.${fields.length}.value` },
            );
            announce(t("answerAdded", { number }));
          }}
        >
          <Plus aria-hidden />
          {t("addAnswer")}
        </Button>
      </fieldset>
    </li>
  );
}

function AnswerInput({
  label,
  removeLabel,
  canRemove,
  path,
  registerProps,
  invalid,
  describedBy,
  onRemove,
}: {
  label: string;
  removeLabel: string;
  canRemove: boolean;
  path: string;
  registerProps: ReturnType<ReturnType<typeof useFormContext<QuestionEditorValues>>["register"]>;
  invalid: boolean;
  describedBy: string | undefined;
  onRemove: () => void;
}) {
  const inputId = useId();
  const maxError = useEditorError(path);
  return (
    <li className="flex flex-col gap-1">
      <div className="flex items-center gap-2">
        <Input
          id={inputId}
          aria-label={label}
          aria-invalid={invalid || maxError ? true : undefined}
          aria-describedby={maxError ? `${inputId}-error` : describedBy}
          autoComplete="off"
          spellCheck={false}
          className="min-w-0 flex-1"
          {...registerProps}
        />
        {canRemove && <IconButton tone="danger" aria-label={removeLabel} icon={<X />} onClick={onRemove} />}
      </div>
      <FieldErrorText id={`${inputId}-error`} message={maxError} />
    </li>
  );
}
