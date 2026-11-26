"use client";

import { Button, IconButton, Input } from "@thi360/ui";
import { Plus, X } from "lucide-react";
import { useTranslations } from "next-intl";
import { useEffect, useId, useRef } from "react";
import { useFieldArray, useFormContext, useWatch } from "react-hook-form";
import {
  BLANK_MARKER,
  countBlankMarkers,
  draftRowId,
  emptyBlank,
  findInsertedBlankIndex,
  removeBlankMarker,
  type QuestionEditorValues,
} from "../editor-form";
import { FieldErrorText, GroupError, useAnnounce, useEditorError, useRichEditorRegistry, type BodyPath, type ContentPath } from "./question-editor-shared";

/**
 * Ô trống của câu Điền đáp án (canvas `CauHoiDienDapAnForm`). Chỗ trống là `___` trong đề bài: "Thêm ô trống" chèn
 * `___` đúng chỗ con trỏ trong ô Đề bài (cuối đề bài nếu ô chưa từng được focus) và thêm dòng đáp án đúng thứ tự; xóa
 * dòng thì bỏ chỗ trống tương ứng. Mỗi ô có thể có nhiều đáp án chấp nhận. Kiểu đáp án (Số / Văn bản) của bản vẽ chưa
 * có trong mô hình dữ liệu.
 */
export function BlanksEditor({ prefix, contentPath }: { prefix: BodyPath; contentPath: ContentPath }) {
  const t = useTranslations("questions.editor.blanks");
  const announce = useAnnounce();
  const groupId = useId();
  const addRef = useRef<HTMLButtonElement>(null);
  const registry = useRichEditorRegistry();
  const { control, getValues, setValue, formState, trigger } = useFormContext<QuestionEditorValues>();
  const { fields, insert, remove } = useFieldArray({ control, name: `${prefix}.blanks`, keyName: "rowKey" });
  const content = useWatch({ control, name: contentPath }) ?? "";
  const prevContentRef = useRef(content);
  const markers = countBlankMarkers(content);
  const groupError = useEditorError(`${prefix}.blanks`);

  const revalidate = () => {
    if (formState.isSubmitted) void trigger([contentPath, `${prefix}.blanks`]);
  };

  // Khi người dùng gõ trực tiếp `___` (hoặc dán nhiều `___`) vào Đề bài, tự chèn đủ ô đáp án đúng vị trí mới thêm mà không làm lệch các đáp án phía sau và không cướp tiêu điểm khỏi ô Đề bài.
  useEffect(() => {
    const previous = prevContentRef.current;
    prevContentRef.current = content;
    const current = getValues(`${prefix}.blanks`) ?? [];
    const missing = markers - current.length;
    if (missing <= 0) return;
    const insertAt = Math.min(current.length, findInsertedBlankIndex(previous, content));
    insert(
      insertAt,
      Array.from({ length: missing }, () => emptyBlank()),
      { shouldFocus: false },
    );
    if (formState.isSubmitted) void trigger([contentPath, `${prefix}.blanks`]);
  }, [content, contentPath, formState.isSubmitted, getValues, insert, markers, prefix, trigger]);

  const addBlank = () => {
    const editor = registry?.get(contentPath);
    if (!editor) return;
    // Chỗ trống mới vào đúng con trỏ trong ô Đề bài (cuối đề bài nếu ô chưa từng được focus).
    const { before, after } = editor.getInsertionContext();
    const spaceBefore = before.length > 0 && !/\s$/.test(before) ? " " : "";
    const spaceAfter = after.length > 0 && !/^\s/.test(after) ? " " : "";
    if (!editor.insertText(`${spaceBefore}${BLANK_MARKER}${spaceAfter}`)) return;
    const index = countBlankMarkers(before);
    insert(index, emptyBlank(), { shouldFocus: true, focusName: `${prefix}.blanks.${index}.answers.0.value` });
    announce(t("added", { number: index + 1 }));
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
