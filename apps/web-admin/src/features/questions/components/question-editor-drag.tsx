"use client";

import { Button, IconButton, Input } from "@thi360/ui";
import { Plus, Trash2 } from "lucide-react";
import { useTranslations } from "next-intl";
import { useId, useRef } from "react";
import { useFieldArray, useFormContext } from "react-hook-form";
import { emptyDistractor, emptyZone, type QuestionEditorValues } from "../editor-form";
import {
  FieldErrorText,
  GroupError,
  RowActions,
  useAnnounce,
  useEditorError,
  type BodyPath,
} from "./question-editor-shared";

/**
 * Ô thả và thẻ của câu Kéo thả (canvas `CauHoiKeoThaForm`): mỗi ô thả có nhãn (chỗ cần thả trong câu) và một thẻ
 * đúng; thẻ nhiễu là thẻ không thuộc ô nào. Hai tùy chọn "Trộn thứ tự thẻ" và "Mỗi thẻ chỉ dùng một lần" của bản vẽ
 * chưa có trong mô hình dữ liệu.
 */
export function DragDropEditor({ prefix }: { prefix: BodyPath }) {
  const t = useTranslations("questions.editor.dragDrop");
  const announce = useAnnounce();
  const groupId = useId();
  const addZoneRef = useRef<HTMLButtonElement>(null);
  const addDistractorRef = useRef<HTMLButtonElement>(null);
  const { control, register } = useFormContext<QuestionEditorValues>();
  const zones = useFieldArray({ control, name: `${prefix}.zones`, keyName: "rowKey" });
  const distractors = useFieldArray({ control, name: `${prefix}.distractors`, keyName: "rowKey" });
  const zonesError = useEditorError(`${prefix}.zones`);

  return (
    <div className="flex flex-col gap-4">
      <p className="text-body text-text-muted">
        {t("summary", {
          total: zones.fields.length + distractors.fields.length,
          correct: zones.fields.length,
          distractors: distractors.fields.length,
        })}
      </p>
      <GroupError id={`${groupId}-error`} message={zonesError} />
      <ol className="m-0 flex list-none flex-col gap-2 p-0">
        {zones.fields.map((field, index) => (
          <ZoneRow
            key={field.rowKey}
            prefix={prefix}
            index={index}
            count={zones.fields.length}
            onMove={(from, to) => {
              zones.move(from, to);
              announce(t("zoneMoved", { number: from + 1, position: to + 1 }));
            }}
            onRemove={() => {
              zones.remove(index);
              announce(t("zoneRemoved", { number: index + 1 }));
              addZoneRef.current?.focus();
            }}
          />
        ))}
      </ol>
      <Button
        ref={addZoneRef}
        type="button"
        variant="outline"
        size="sm"
        className="self-start"
        onClick={() => {
          zones.append(emptyZone(), { shouldFocus: true, focusName: `${prefix}.zones.${zones.fields.length}.label` });
          announce(t("zoneAdded", { number: zones.fields.length + 1 }));
        }}
      >
        <Plus aria-hidden />
        {t("addZone")}
      </Button>

      <fieldset className="m-0 flex min-w-0 flex-col gap-2 border-0 border-t border-border p-0 pt-4">
        <legend className="float-left mb-2 w-full text-label font-semibold text-text">{t("distractorsLegend")}</legend>
        <p className="clear-both text-caption text-text-muted">{t("distractorsHint")}</p>
        {distractors.fields.length === 0 && <p className="text-body text-text-muted">{t("noDistractors")}</p>}
        <ul className="m-0 flex list-none flex-col gap-2 p-0">
          {distractors.fields.map((field, index) => (
            <DistractorRow
              key={field.rowKey}
              prefix={prefix}
              index={index}
              register={register}
              onRemove={() => {
                distractors.remove(index);
                announce(t("distractorRemoved", { number: index + 1 }));
                addDistractorRef.current?.focus();
              }}
            />
          ))}
        </ul>
        <Button
          ref={addDistractorRef}
          type="button"
          variant="link"
          size="sm"
          className="self-start"
          onClick={() => {
            distractors.append(emptyDistractor(), {
              shouldFocus: true,
              focusName: `${prefix}.distractors.${distractors.fields.length}.content`,
            });
            announce(t("distractorAdded", { number: distractors.fields.length + 1 }));
          }}
        >
          <Plus aria-hidden />
          {t("addDistractor")}
        </Button>
      </fieldset>
    </div>
  );
}

function ZoneRow({
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
  const t = useTranslations("questions.editor.dragDrop");
  const { register } = useFormContext<QuestionEditorValues>();
  const rowId = useId();
  const number = index + 1;
  const labelError = useEditorError(`${prefix}.zones.${index}.label`);
  const itemError = useEditorError(`${prefix}.zones.${index}.itemContent`);
  return (
    <li className="flex flex-col gap-2 rounded-lg border border-border bg-surface p-3">
      <div className="flex items-center justify-between gap-2">
        <h3 className="text-label font-semibold text-text">{t("zoneTitle", { number })}</h3>
        <RowActions index={index} count={count} name={t("zoneName", { number })} onMove={onMove} onRemove={onRemove} />
      </div>
      <div className="grid gap-3 @lg:grid-cols-2">
        <div className="flex flex-col gap-1.5">
          <label htmlFor={`${rowId}-label`} className="text-field-label font-medium text-text-secondary">
            {t("zoneLabel")}
          </label>
          <Input
            id={`${rowId}-label`}
            aria-invalid={labelError ? true : undefined}
            aria-describedby={labelError ? `${rowId}-label-error` : `${rowId}-label-hint`}
            autoComplete="off"
            {...register(`${prefix}.zones.${index}.label`)}
          />
          {!labelError && (
            <p id={`${rowId}-label-hint`} className="text-caption text-text-muted">
              {t("zoneLabelHint")}
            </p>
          )}
          <FieldErrorText id={`${rowId}-label-error`} message={labelError} />
        </div>
        <div className="flex flex-col gap-1.5">
          <label htmlFor={`${rowId}-item`} className="text-field-label font-medium text-text-secondary">
            {t("zoneItem")}
          </label>
          <Input
            id={`${rowId}-item`}
            aria-invalid={itemError ? true : undefined}
            aria-describedby={itemError ? `${rowId}-item-error` : undefined}
            autoComplete="off"
            {...register(`${prefix}.zones.${index}.itemContent`)}
          />
          <FieldErrorText id={`${rowId}-item-error`} message={itemError} />
        </div>
      </div>
    </li>
  );
}

function DistractorRow({
  prefix,
  index,
  register,
  onRemove,
}: {
  prefix: BodyPath;
  index: number;
  register: ReturnType<typeof useFormContext<QuestionEditorValues>>["register"];
  onRemove: () => void;
}) {
  const t = useTranslations("questions.editor.dragDrop");
  const inputId = useId();
  const error = useEditorError(`${prefix}.distractors.${index}.content`);
  const number = index + 1;
  return (
    <li className="flex flex-col gap-1">
      <div className="flex items-center gap-2">
        <Input
          id={inputId}
          aria-label={t("distractorLabel", { number })}
          aria-invalid={error ? true : undefined}
          aria-describedby={error ? `${inputId}-error` : undefined}
          autoComplete="off"
          className="min-w-0 flex-1"
          {...register(`${prefix}.distractors.${index}.content`)}
        />
        <IconButton tone="danger" aria-label={t("removeDistractor", { number })} icon={<Trash2 />} onClick={onRemove} />
      </div>
      <FieldErrorText id={`${inputId}-error`} message={error} />
    </li>
  );
}
