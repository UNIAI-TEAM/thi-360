"use client";

import { Button, FormField, Input } from "@thi360/ui";
import { Plus } from "lucide-react";
import { useTranslations } from "next-intl";
import { useId, useRef } from "react";
import { useFieldArray, useFormContext, useWatch } from "react-hook-form";
import { TRUE_FALSE_METHODS, type TrueFalseMethod } from "@/features/content/types";
import {
  DEFAULT_POINTS,
  emptyStatement,
  evenScoreTableText,
  evenSplitText,
  parsePoints,
  pointsTotal,
  type QuestionEditorValues,
} from "../editor-form";
import { PointsTotalLine } from "./question-editor-points-total";
import { RichTextField } from "./question-editor-rich-field";
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
 * Các ý của câu Đúng / Sai: mỗi ý một nhóm radio Đúng/Sai (canvas `CauHoiDungSaiForm`) và hai cách chấm (PBI-03 mục 10):
 * cộng điểm từng ý (mỗi ý có điểm, tổng bằng điểm của câu) hoặc bảng điểm theo số ý đúng (UNI-2599). Thêm hoặc xóa ý
 * thì điểm từng ý và bảng điểm chia đều lại theo số ý mới.
 */
export function StatementsEditor({ prefix }: { prefix: BodyPath }) {
  const t = useTranslations("questions.editor.statements");
  const tTable = useTranslations("questions.editor.scoreTable");
  const announce = useAnnounce();
  const groupId = useId();
  const addRef = useRef<HTMLButtonElement>(null);
  const { control, getValues, setValue } = useFormContext<QuestionEditorValues>();
  const { fields, append, remove, move } = useFieldArray({ control, name: `${prefix}.statements`, keyName: "rowKey" });
  const method = useWatch({ control, name: `${prefix}.scoringMethod` });
  const statements = useWatch({ control, name: `${prefix}.statements` }) ?? [];
  const pointsText = useWatch({ control, name: pointsPathOf(prefix) }) ?? "";
  const points = parsePoints(pointsText);
  const groupError = useEditorError(`${prefix}.statements`);

  const basePoints = () => {
    const value = parsePoints(getValues(pointsPathOf(prefix)));
    return value > 0 ? value : DEFAULT_POINTS;
  };

  /** Điểm từng ý chia đều theo điểm của câu. */
  const evenStatementPoints = (count: number) => {
    evenSplitText(count, basePoints()).forEach((value, index) =>
      setValue(`${prefix}.statements.${index}.points`, value, { shouldDirty: true }),
    );
  };

  /** Điểm từng ý và bảng điểm theo số ý mới, chia đều theo điểm của câu. */
  const reflow = (count: number) => {
    evenStatementPoints(count);
    setValue(`${prefix}.scoreTable`, evenScoreTableText(count, basePoints()), { shouldDirty: true });
    return method === "PER_STATEMENT" ? t("pointsReflowed", { count }) : tTable("reflowed", { count });
  };

  const chooseMethod = (next: TrueFalseMethod) => {
    if (next === "PER_STATEMENT") evenStatementPoints(fields.length);
    else if (getValues(`${prefix}.scoreTable`).length !== fields.length) {
      setValue(`${prefix}.scoreTable`, evenScoreTableText(fields.length, basePoints()), { shouldDirty: true });
    }
    announce(t("methodChosen", { method: t(`method.${next}.label`) }));
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
            perStatement={method === "PER_STATEMENT"}
            onMove={(from, to) => {
              move(from, to);
              announce(t("moved", { letter: statementLetter(from), position: to + 1 }));
            }}
            onRemove={() => {
              remove(index);
              announce(`${t("removed", { letter: statementLetter(index) })} ${reflow(fields.length - 1)}`);
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
          announce(`${t("added", { letter: statementLetter(fields.length) })} ${reflow(fields.length + 1)}`);
        }}
      >
        <Plus aria-hidden />
        {t("add")}
      </Button>
      <ScoringMethodField prefix={prefix} onChoose={chooseMethod} />
      {method === "PER_STATEMENT" ? (
        <PointsTotalLine
          total={pointsTotal(statements.map((statement) => statement.points))}
          points={points}
          count={fields.length}
          onEven={() => {
            evenStatementPoints(fields.length);
            announce(t("pointsReflowed", { count: fields.length }));
          }}
        />
      ) : (
        <ScoreTableEditor prefix={prefix} />
      )}
    </div>
  );
}

/** Cách chấm: hai lựa chọn hiện sẵn (radio), mỗi lựa chọn kèm giải thích ngắn. */
function ScoringMethodField({ prefix, onChoose }: { prefix: BodyPath; onChoose: (method: TrueFalseMethod) => void }) {
  const t = useTranslations("questions.editor.statements");
  const groupId = useId();
  const { register } = useFormContext<QuestionEditorValues>();
  const field = register(`${prefix}.scoringMethod`);
  return (
    <fieldset className="m-0 flex min-w-0 flex-col gap-2 rounded-lg border border-border p-3">
      <legend className="px-1 text-label font-semibold text-text">{t("methodLegend")}</legend>
      {TRUE_FALSE_METHODS.map((method) => (
        <div key={method} className="flex items-start gap-2.5">
          <input
            id={`${groupId}-${method}-input`}
            type="radio"
            value={method}
            className="mt-3 size-4 shrink-0 accent-primary-fill"
            aria-describedby={`${groupId}-${method}`}
            {...field}
            onChange={(event) => {
              void field.onChange(event);
              onChoose(method);
            }}
          />
          <div className="flex flex-col">
            <label htmlFor={`${groupId}-${method}-input`} className="flex min-h-10 items-center text-body font-medium text-text">
              {t(`method.${method}.label`)}
            </label>
            <p id={`${groupId}-${method}`} className="text-caption text-text-muted">
              {t(`method.${method}.description`)}
            </p>
          </div>
        </div>
      ))}
    </fieldset>
  );
}

function StatementRow({
  prefix,
  index,
  count,
  perStatement,
  onMove,
  onRemove,
}: {
  prefix: BodyPath;
  index: number;
  count: number;
  perStatement: boolean;
  onMove: (from: number, to: number) => void;
  onRemove: () => void;
}) {
  const t = useTranslations("questions.editor.statements");
  const { register } = useFormContext<QuestionEditorValues>();
  const letter = statementLetter(index);
  const inputId = useId();
  const contentError = useEditorError(`${prefix}.statements.${index}.content`);
  const answerError = useEditorError(`${prefix}.statements.${index}.answer`);
  const pointsError = useEditorError(`${prefix}.statements.${index}.points`);
  return (
    <li className="flex flex-col gap-2 rounded-lg border border-border bg-surface p-2.5">
      <div className="flex flex-wrap items-center gap-2 @md:flex-nowrap">
        <span aria-hidden className="w-6 shrink-0 font-semibold text-text">
          {letter})
        </span>
        <div className="min-w-0 flex-1 basis-48">
          <RichTextField
            id={inputId}
            aria-label={t("contentLabel", { letter })}
            aria-invalid={contentError ? true : undefined}
            aria-describedby={contentError ? `${inputId}-error` : undefined}
            name={`${prefix}.statements.${index}.content`}
          />
        </div>
        <RowActions index={index} count={count} name={t("rowName", { letter })} onMove={onMove} onRemove={onRemove} />
      </div>
      <FieldErrorText id={`${inputId}-error`} message={contentError} />
      <div className="flex flex-wrap items-center justify-between gap-x-5 gap-y-1 ps-8">
        <fieldset
          className="m-0 flex flex-wrap items-center gap-x-5 gap-y-1 border-0 p-0"
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
        {perStatement && (
          <FormField label={t("pointsLabel", { letter })} error={pointsError} className="w-28">
            {(controlProps) => (
              <Input
                {...controlProps}
                inputMode="decimal"
                autoComplete="off"
                {...register(`${prefix}.statements.${index}.points`)}
              />
            )}
          </FormField>
        )}
      </div>
      <GroupError id={`${inputId}-answer-error`} message={answerError} />
    </li>
  );
}
