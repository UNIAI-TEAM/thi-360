"use client";

import { Button, Input } from "@thi360/ui";
import { Plus } from "lucide-react";
import { useTranslations } from "next-intl";
import { useId, useRef } from "react";
import { useFieldArray, useFormContext } from "react-hook-form";
import { DEFAULT_POINTS, emptyStatement, evenScoreTableText, parsePoints, type QuestionEditorValues } from "../editor-form";
import { ScoreTableEditor } from "./question-editor-score-table";
import {
  FieldErrorText,
  GroupError,
  pointsPathOf,
  RowActions,
  statementLetter,
  useAnnounce,
  useEditorError,
  type BodyPath,
} from "./question-editor-shared";

/**
 * Các ý của câu Đúng / Sai: mỗi ý một nhóm radio Đúng/Sai (canvas `CauHoiDungSaiForm`), kèm bảng điểm theo số ý đúng
 * (UNI-2599). Thêm hoặc xóa ý thì bảng điểm chia đều lại theo số ý mới.
 */
export function StatementsEditor({ prefix }: { prefix: BodyPath }) {
  const t = useTranslations("questions.editor.statements");
  const tTable = useTranslations("questions.editor.scoreTable");
  const announce = useAnnounce();
  const groupId = useId();
  const addRef = useRef<HTMLButtonElement>(null);
  const { control, getValues, setValue } = useFormContext<QuestionEditorValues>();
  const { fields, append, remove, move } = useFieldArray({ control, name: `${prefix}.statements`, keyName: "rowKey" });
  const groupError = useEditorError(`${prefix}.statements`);

  /** Bảng điểm theo số ý mới, chia đều theo điểm của câu. */
  const reflowTable = (count: number) => {
    const points = parsePoints(getValues(pointsPathOf(prefix)));
    setValue(`${prefix}.scoreTable`, evenScoreTableText(count, points > 0 ? points : DEFAULT_POINTS), { shouldDirty: true });
    return tTable("reflowed", { count });
  };

  return (
    <div className="flex flex-col gap-3">
      <p className="text-body text-text-muted">{t("hint")}</p>
      <GroupError id={`${groupId}-error`} message={groupError} />
      <ol className="m-0 flex list-none flex-col gap-2 p-0">
        {fields.map((field, index) => (
          <StatementRow
            key={field.rowKey}
            prefix={prefix}
            index={index}
            count={fields.length}
            onMove={(from, to) => {
              move(from, to);
              announce(t("moved", { letter: statementLetter(from), position: to + 1 }));
            }}
            onRemove={() => {
              remove(index);
              announce(`${t("removed", { letter: statementLetter(index) })} ${reflowTable(fields.length - 1)}`);
              addRef.current?.focus();
            }}
          />
        ))}
      </ol>
      <Button
        ref={addRef}
        type="button"
        variant="outline"
        size="sm"
        className="self-start"
        onClick={() => {
          append(emptyStatement(), { shouldFocus: true, focusName: `${prefix}.statements.${fields.length}.content` });
          announce(`${t("added", { letter: statementLetter(fields.length) })} ${reflowTable(fields.length + 1)}`);
        }}
      >
        <Plus aria-hidden />
        {t("add")}
      </Button>
      <ScoreTableEditor prefix={prefix} />
    </div>
  );
}

function StatementRow({
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
  const t = useTranslations("questions.editor.statements");
  const { register } = useFormContext<QuestionEditorValues>();
  const letter = statementLetter(index);
  const inputId = useId();
  const contentError = useEditorError(`${prefix}.statements.${index}.content`);
  const answerError = useEditorError(`${prefix}.statements.${index}.answer`);
  return (
    <li className="flex flex-col gap-2 rounded-lg border border-border bg-surface p-2.5">
      <div className="flex flex-wrap items-center gap-2 @md:flex-nowrap">
        <span aria-hidden className="w-6 shrink-0 font-semibold text-text">
          {letter})
        </span>
        <Input
          id={inputId}
          aria-label={t("contentLabel", { letter })}
          aria-invalid={contentError ? true : undefined}
          aria-describedby={contentError ? `${inputId}-error` : undefined}
          autoComplete="off"
          className="min-w-0 flex-1 basis-48"
          {...register(`${prefix}.statements.${index}.content`)}
        />
        <RowActions index={index} count={count} name={t("rowName", { letter })} onMove={onMove} onRemove={onRemove} />
      </div>
      <FieldErrorText id={`${inputId}-error`} message={contentError} />
      <fieldset
        className="m-0 flex flex-wrap items-center gap-x-5 gap-y-1 border-0 p-0 ps-8"
        aria-describedby={answerError ? `${inputId}-answer-error` : undefined}
      >
        <legend className="sr-only">{t("answerLegend", { letter })}</legend>
        {(["TRUE", "FALSE"] as const).map((value) => (
          <label key={value} className="flex min-h-10 items-center gap-2 text-body text-text">
            <input
              type="radio"
              value={value}
              className="size-4 accent-primary-fill"
              {...register(`${prefix}.statements.${index}.answer`)}
            />
            {t(value === "TRUE" ? "true" : "false")}
          </label>
        ))}
      </fieldset>
      <GroupError id={`${inputId}-answer-error`} message={answerError} />
    </li>
  );
}
