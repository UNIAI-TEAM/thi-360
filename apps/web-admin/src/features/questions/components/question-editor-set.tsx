"use client";

import { questionHtmlText } from "@/components/rich-text";
import { Button, Chip, cn, FormField, IconButton, Input, Label } from "@thi360/ui";
import { ChevronDown, ChevronUp, Plus } from "lucide-react";
import { useTranslations } from "next-intl";
import { useId, useRef, useState } from "react";
import { get, useFieldArray, useFormContext, useFormState, useWatch } from "react-hook-form";
import type { ContentReference } from "@/features/content/types";
import { emptyChild, questionSetPoints, type EditorChild, type QuestionEditorValues } from "../editor-form";
import { CHILD_QUESTION_TYPES, type ChildQuestionType } from "../editor-types";
import { RichTextField } from "./question-editor-rich-field";
import { QuestionBodyEditor } from "./question-editor-body";
import { ChildClassification } from "./question-editor-child-classification";
import { FieldErrorText, GroupError, optionLetter, RowActions, statementLetter, useAnnounce, useEditorError } from "./question-editor-shared";

/**
 * Câu con của câu chùm (canvas `CauHoiChumForm`): mỗi câu con là một câu đầy đủ với dạng riêng, phân loại theo câu
 * cha, điểm riêng (mặc định 1); điểm câu chùm là tổng điểm câu con (UniNote PBI-03 mục 4.2). Câu con đã có thu gọn
 * thành dòng tóm tắt; câu con có lỗi tự mở ra để sửa.
 */
export function QuestionSetEditor({ reference }: { reference: ContentReference }) {
  const t = useTranslations("questions.editor.set");
  const tType = useTranslations("content.questionType");
  const announce = useAnnounce();
  const groupId = useId();
  const addGroupRef = useRef<HTMLDivElement>(null);
  const { control } = useFormContext<QuestionEditorValues>();
  const { fields, append, remove, move } = useFieldArray({ control, name: "children", keyName: "rowKey" });
  const children = useWatch({ control, name: "children" }) ?? [];
  const [expanded, setExpanded] = useState<ReadonlySet<string>>(new Set());
  const groupError = useEditorError("children");

  const toggle = (key: string) =>
    setExpanded((current) => {
      const next = new Set(current);
      if (next.has(key)) next.delete(key);
      else next.add(key);
      return next;
    });

  const add = (type: ChildQuestionType) => {
    const child = emptyChild(type);
    setExpanded((current) => new Set(current).add(child.key));
    append(child, { shouldFocus: true, focusName: `children.${fields.length}.content` });
    announce(t("added", { number: fields.length + 1, type: tType(type) }));
  };

  return (
    <div className="flex flex-col gap-3">
      <p className="text-body text-text-muted">{t("hint")}</p>
      <p className="text-caption font-medium text-text-secondary">
        {t("totalPoints", { points: questionSetPoints(children) })}
      </p>
      <GroupError id={`${groupId}-error`} message={groupError} />
      <ol className="m-0 flex list-none flex-col gap-3 p-0">
        {fields.map((field, index) => (
          <ChildCard
            key={field.rowKey}
            index={index}
            reference={reference}
            count={fields.length}
            child={children[index] ?? field}
            expanded={expanded.has(field.key)}
            onToggle={() => toggle(field.key)}
            onMove={(from, to) => {
              move(from, to);
              announce(t("moved", { number: from + 1, position: to + 1 }));
            }}
            onRemove={() => {
              remove(index);
              announce(t("removed", { number: index + 1 }));
              addGroupRef.current?.querySelector("button")?.focus();
            }}
          />
        ))}
      </ol>
      <div ref={addGroupRef} role="group" aria-labelledby={`${groupId}-add`} className="flex flex-col gap-2">
        <span id={`${groupId}-add`} className="text-label font-semibold text-text">
          {t("addLegend")}
        </span>
        <div className="flex flex-wrap gap-2">
          {CHILD_QUESTION_TYPES.map((type) => (
            <Button key={type} type="button" variant="outline" size="sm" onClick={() => add(type)}>
              <Plus aria-hidden />
              {tType(type)}
            </Button>
          ))}
        </div>
      </div>
    </div>
  );
}

function ChildCard({
  index,
  count,
  child,
  reference,
  expanded,
  onToggle,
  onMove,
  onRemove,
}: {
  index: number;
  count: number;
  child: EditorChild;
  reference: ContentReference;
  expanded: boolean;
  onToggle: () => void;
  onMove: (from: number, to: number) => void;
  onRemove: () => void;
}) {
  const t = useTranslations("questions.editor.set");
  const tType = useTranslations("content.questionType");
  const tErrors = useTranslations("questions.editor.errors");
  const { register } = useFormContext<QuestionEditorValues>();
  const { errors } = useFormState<QuestionEditorValues>({ name: `children.${index}` });
  const hasError = Boolean(get(errors, `children.${index}`));
  const open = expanded || hasError;
  const cardId = useId();
  const number = index + 1;
  const contentError = get(errors, `children.${index}.content.message`) as string | undefined;
  const pointsError = get(errors, `children.${index}.points.message`) as string | undefined;
  const solutionError = get(errors, `children.${index}.solution.message`) as string | undefined;

  return (
    <li
      className={cn(
        "flex flex-col gap-3 rounded-lg border bg-surface p-3",
        hasError ? "border-danger" : "border-border",
      )}
    >
      <div className="flex flex-wrap items-start justify-between gap-2">
        <div className="flex min-w-0 flex-1 flex-col gap-1">
          <div className="flex flex-wrap items-center gap-2">
            <h3 id={`${cardId}-title`} className="text-label font-semibold text-text">
              {t("childTitle", { number })}
            </h3>
            <Chip variant="tag" className="h-6">
              {tType(child.type)}
            </Chip>
            {hasError && <span className="text-caption font-medium text-danger">{t("hasErrors")}</span>}
          </div>
          {!open && (
            <>
              <p className="line-clamp-2 text-body text-text">{questionHtmlText(child.content).trim() || t("noContent")}</p>
              <p className="text-caption text-text-muted">{t("answerSummary", { answer: answerSummary(child, t) })}</p>
            </>
          )}
        </div>
        <div className="flex items-center">
          <IconButton
            aria-label={open ? t("collapse", { number }) : t("expand", { number })}
            aria-expanded={open}
            aria-controls={`${cardId}-body`}
            icon={open ? <ChevronUp /> : <ChevronDown />}
            aria-disabled={hasError || undefined}
            tooltip={hasError ? t("cannotCollapse") : undefined}
            onClick={onToggle}
          />
          <RowActions index={index} count={count} name={t("childName", { number })} onMove={onMove} onRemove={onRemove} />
        </div>
      </div>
      <div id={`${cardId}-body`} hidden={!open} className="flex flex-col gap-4">
        {open && (
          <>
            <div className="flex flex-col gap-1.5">
              <Label id={`${cardId}-content-label`} htmlFor={`${cardId}-content`} required>
                {t("contentLabel")}
              </Label>
              <RichTextField
                id={`${cardId}-content`}
                aria-labelledby={`${cardId}-content-label`}
                aria-required
                aria-invalid={contentError ? true : undefined}
                aria-describedby={contentError ? `${cardId}-content-error` : undefined}
                name={`children.${index}.content`}
              />
              <FieldErrorText
                id={`${cardId}-content-error`}
                message={contentError ? tErrors(contentError) : undefined}
              />
            </div>
            <div className="flex flex-col gap-3 rounded-lg bg-subtle p-3">
              <QuestionBodyEditor
                type={child.type}
                prefix={`children.${index}.body`}
                contentPath={`children.${index}.content`}
              />
            </div>
            <div className="grid gap-3 @lg:grid-cols-[10rem_minmax(0,1fr)]">
              <FormField label={t("pointsLabel")} required error={pointsError ? tErrors(pointsError) : undefined}>
                {(controlProps) => (
                  <Input {...controlProps} inputMode="decimal" autoComplete="off" {...register(`children.${index}.points`)} />
                )}
              </FormField>
              <FormField
                label={t("solutionLabel")}
                hint={t("solutionHint")}
                error={solutionError ? tErrors(solutionError) : undefined}
              >
                {(controlProps) => (
                  <RichTextField {...controlProps} aria-label={t("solutionLabel")} name={`children.${index}.solution`} />
                )}
              </FormField>
            </div>
            <ChildClassification index={index} reference={reference} />
          </>
        )}
      </div>
    </li>
  );
}

function answerSummary(child: EditorChild, t: ReturnType<typeof useTranslations>): string {
  const { body } = child;
  switch (child.type) {
    case "SINGLE_CHOICE":
    case "MULTIPLE_CHOICE": {
      const letters = body.options.flatMap((option, index) => (option.correct ? [optionLetter(index)] : []));
      return letters.length ? letters.join(", ") : t("noAnswer");
    }
    case "TRUE_FALSE": {
      const parts = body.statements.map(
        (statement, index) =>
          `${statementLetter(index)}) ${statement.answer === "TRUE" ? t("true") : statement.answer === "FALSE" ? t("false") : "?"}`,
      );
      return parts.length ? parts.join(", ") : t("noAnswer");
    }
    case "SHORT_ANSWER": {
      const answers = (body.blanks[0]?.answers ?? []).map((answer) => answer.value.trim()).filter(Boolean);
      return answers.length ? answers.join(" / ") : t("noAnswer");
    }
    case "MATCHING": {
      const pairs = body.left.map((item, position) => {
        const rightIndex = body.right.findIndex((right) => right.id === item.rightId);
        return `${position + 1}-${rightIndex === -1 ? "?" : optionLetter(rightIndex)}`;
      });
      return pairs.length ? pairs.join(", ") : t("noAnswer");
    }
    case "ESSAY": {
      const guide = questionHtmlText(body.gradingGuide).trim();
      return guide || t("noAnswer");
    }
  }
}
