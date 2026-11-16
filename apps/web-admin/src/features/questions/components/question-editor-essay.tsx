"use client";

import { Badge, Button, FormField, Input, Textarea } from "@thi360/ui";
import { CircleAlert, CircleCheck, PenLine, Plus } from "lucide-react";
import { useFormatter, useTranslations } from "next-intl";
import { useId, useRef } from "react";
import { useFieldArray, useFormContext, useWatch } from "react-hook-form";
import { emptyCriterion, parsePoints, rubricTotal, samePoints, type QuestionEditorValues } from "../editor-form";
import { CRITERION_MAX, SAMPLE_ANSWER_MAX } from "../editor-schema";
import {
  FieldErrorText,
  GroupError,
  pointsPathOf,
  RowActions,
  useAnnounce,
  useEditorError,
  type BodyPath,
} from "./question-editor-shared";

/**
 * Hướng dẫn chấm câu Tự luận (UNI-2599 mục 2.1, chấm tay): điểm tối đa là ô Điểm của câu; tiêu chí chấm thêm, xóa,
 * đổi thứ tự được, tổng điểm tiêu chí phải bằng điểm của câu; bài làm mẫu và số chữ gợi ý không bắt buộc. Dùng cho câu
 * đơn và câu con của câu chùm (đọc hiểu Ngữ văn).
 */
export function EssayEditor({ prefix }: { prefix: BodyPath }) {
  const t = useTranslations("questions.editor.essay");
  const format = useFormatter();
  const announce = useAnnounce();
  const groupId = useId();
  const addRef = useRef<HTMLButtonElement>(null);
  const { control, register } = useFormContext<QuestionEditorValues>();
  const { fields, append, remove, move } = useFieldArray({ control, name: `${prefix}.rubric`, keyName: "rowKey" });
  const rubric = useWatch({ control, name: `${prefix}.rubric` }) ?? [];
  const pointsText = useWatch({ control, name: pointsPathOf(prefix) }) ?? "";
  const points = parsePoints(pointsText);
  const pointsValid = Number.isFinite(points) && points > 0;
  const total = rubricTotal(rubric);
  const matches = pointsValid && samePoints(total, points);
  const groupError = useEditorError(`${prefix}.rubric`);
  const sampleError = useEditorError(`${prefix}.sampleAnswer`);
  const guideError = useEditorError(`${prefix}.gradingGuide`);
  const wordLimitError = useEditorError(`${prefix}.wordLimit`);

  return (
    <div className="flex flex-col gap-4">
      <div className="flex flex-wrap items-center gap-2">
        <Badge>
          <PenLine aria-hidden className="me-1 size-3.5" />
          {t("manualGrading")}
        </Badge>
        <span className="text-body text-text-muted">
          {pointsValid ? t("maxPoints", { points: format.number(points) }) : t("maxPointsUnknown")}
        </span>
      </div>

      <FormField label={t("gradingGuide")} required hint={t("gradingGuideHint")} error={guideError}>
        {(controlProps) => (
          <Textarea {...controlProps} rows={4} maxLength={SAMPLE_ANSWER_MAX} {...register(`${prefix}.gradingGuide`)} />
        )}
      </FormField>

      <fieldset className="m-0 flex min-w-0 flex-col gap-3 border-0 p-0" aria-describedby={`${groupId}-hint`}>
        <legend className="text-label font-semibold text-text">{t("rubricLegend")}</legend>
        <p id={`${groupId}-hint`} className="text-body text-text-muted">
          {t("rubricHint")}
        </p>
        <GroupError id={`${groupId}-error`} message={groupError} />
        <ol className="m-0 flex list-none flex-col gap-2 p-0">
          {fields.map((field, index) => (
            <CriterionRow
              key={field.rowKey}
              prefix={prefix}
              index={index}
              count={fields.length}
              onMove={(from, to) => {
                move(from, to);
                announce(t("moved", { number: from + 1, position: to + 1 }));
              }}
              onRemove={() => {
                remove(index);
                announce(t("removed", { number: index + 1 }));
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
              append(emptyCriterion(), { shouldFocus: true, focusName: `${prefix}.rubric.${fields.length}.description` });
              announce(t("added", { number: fields.length + 1 }));
            }}
          >
            <Plus aria-hidden />
            {t("add")}
          </Button>
          <p className="flex items-center gap-1.5 text-caption font-medium text-text-secondary">
            {pointsValid &&
              (matches ? (
                <CircleCheck aria-hidden className="size-4 text-success" />
              ) : (
                <CircleAlert aria-hidden className="size-4 text-warning" />
              ))}
            {pointsValid
              ? t(matches ? "totalMatches" : "totalDiffers", {
                  total: format.number(total),
                  points: format.number(points),
                })
              : t("totalOnly", { total: format.number(total) })}
          </p>
        </div>
      </fieldset>

      <div className="grid gap-3 @lg:grid-cols-[minmax(0,1fr)_12rem]">
        <FormField label={t("sampleAnswer")} hint={t("sampleAnswerHint")} error={sampleError}>
          {(controlProps) => (
            <Textarea {...controlProps} rows={4} maxLength={SAMPLE_ANSWER_MAX} {...register(`${prefix}.sampleAnswer`)} />
          )}
        </FormField>
        <FormField label={t("wordLimit")} hint={t("wordLimitHint")} error={wordLimitError}>
          {(controlProps) => (
            <Input {...controlProps} inputMode="numeric" autoComplete="off" {...register(`${prefix}.wordLimit`)} />
          )}
        </FormField>
      </div>
    </div>
  );
}

function CriterionRow({
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
  const t = useTranslations("questions.editor.essay");
  const { register } = useFormContext<QuestionEditorValues>();
  const number = index + 1;
  const inputId = useId();
  const descriptionError = useEditorError(`${prefix}.rubric.${index}.description`);
  const pointsError = useEditorError(`${prefix}.rubric.${index}.points`);
  return (
    <li className="flex flex-col gap-1.5 rounded-lg border border-border bg-surface p-2.5">
      <div className="flex flex-wrap items-start gap-2 @md:flex-nowrap">
        <span aria-hidden className="mt-2.5 w-6 shrink-0 font-semibold text-text">
          {number}.
        </span>
        <Textarea
          id={inputId}
          rows={2}
          maxLength={CRITERION_MAX}
          aria-label={t("descriptionLabel", { number })}
          aria-invalid={descriptionError ? true : undefined}
          aria-describedby={descriptionError ? `${inputId}-error` : undefined}
          className="min-w-0 flex-1 basis-48"
          {...register(`${prefix}.rubric.${index}.description`)}
        />
        <div className="flex w-24 shrink-0 flex-col">
          <Input
            id={`${inputId}-points`}
            inputMode="decimal"
            autoComplete="off"
            aria-label={t("pointsLabel", { number })}
            aria-invalid={pointsError ? true : undefined}
            aria-describedby={pointsError ? `${inputId}-points-error` : undefined}
            {...register(`${prefix}.rubric.${index}.points`)}
          />
        </div>
        <RowActions index={index} count={count} name={t("rowName", { number })} onMove={onMove} onRemove={onRemove} />
      </div>
      <FieldErrorText id={`${inputId}-error`} message={descriptionError} />
      <FieldErrorText id={`${inputId}-points-error`} message={pointsError} />
    </li>
  );
}
