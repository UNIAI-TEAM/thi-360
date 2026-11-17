"use client";

import { Badge, Button, cn, focusRing } from "@thi360/ui";
import { Check, PenLine, X } from "lucide-react";
import { useTranslations } from "next-intl";
import { useState } from "react";
import { RichTextView } from "@/components/rich-text";
import type { PreviewBlock, PreviewItem, PreviewPaper, PreviewQuestion } from "../review-types";
import { useLookup } from "./exam-review-shared";

/** Số mục (câu hoặc bộ) hiện sẵn của mỗi khối; phần còn lại mở bằng "Hiện tất cả". */
const VISIBLE_ITEMS = 3;
const OPTION_KEYS = ["A", "B", "C", "D", "E", "F", "G", "H"];

/** Đề đã sinh: phần › khối › câu theo đúng thứ tự thực tế, kèm đáp án và lời giải (chế độ quản trị). */
export function ExamPreviewPaper({ paper }: { paper: PreviewPaper }) {
  const t = useTranslations("exams.preview");
  return (
    <div className="flex flex-col gap-6">
      <p className="text-body text-text-secondary">
        {t("paperSummary", {
          questions: paper.totalQuestions,
          points: paper.totalPoints,
          minutes: paper.totalMinutes,
          shared: paper.sharedDuration ? "yes" : "no",
        })}
      </p>
      {paper.hasManualGrading && (
        <p className="flex items-start gap-2 rounded-md border border-border bg-subtle px-3 py-2 text-body text-text-secondary">
          <PenLine aria-hidden className="mt-0.5 size-4 shrink-0" />
          {t("manualGradingNote")}
        </p>
      )}
      {paper.parts.map((part) => (
        <section key={part.partId} aria-labelledby={`preview-part-${part.partId}`} className="flex flex-col gap-4">
          <h3 id={`preview-part-${part.partId}`} className="text-title font-semibold text-text">
            {part.name}{" "}
            <span className="font-normal text-text-muted">
              ·{" "}
              {t("partRange", {
                first: part.firstNumber,
                last: part.lastNumber,
                minutes: part.durationMinutes ?? 0,
                hasMinutes: part.durationMinutes === null ? "no" : "yes",
              })}{" "}
              ·{" "}
              {part.maxPoints !== null
                ? t("partPointsOf", { points: part.points, max: part.maxPoints })
                : t("partPoints", { points: part.points })}
            </span>
          </h3>
          {part.blocks.map((block) => (
            <PreviewBlockView key={block.blockId} block={block} />
          ))}
        </section>
      ))}
    </div>
  );
}

function PreviewBlockView({ block }: { block: PreviewBlock }) {
  const t = useTranslations("exams.preview");
  const lookup = useLookup();
  const [expanded, setExpanded] = useState(false);
  const name = `${lookup.subject(block.subjectCode)} · ${block.blockName}`;
  const visible = expanded ? block.items : block.items.slice(0, VISIBLE_ITEMS);
  const hidden = block.items.length - visible.length;
  const range =
    block.setCount !== null && block.setSize !== null
      ? t("blockRangeSets", { sets: block.setCount, size: block.setSize, first: block.firstNumber, last: block.lastNumber })
      : t("blockRange", { first: block.firstNumber, last: block.lastNumber, count: block.questionCount });
  const listId = `preview-block-${block.blockId}`;

  return (
    <div className="flex flex-col gap-3 rounded-lg border border-border bg-surface p-4">
      <h4 className="flex flex-wrap items-center gap-x-2 gap-y-1 text-label font-semibold text-text">
        <span>
          {t("blockHeading", { block: name })} <span className="font-normal text-text-muted">· {range}</span>
        </span>
        {block.manualGrading && <Badge>{t("manualGrading")}</Badge>}
      </h4>
      <ol id={listId} className="flex flex-col gap-3">
        {visible.map((item, index) => (
          <li key={item.kind === "SET" ? item.setId : item.question.questionId}>
            <PreviewItemView item={item} setIndex={index + 1} />
          </li>
        ))}
      </ol>
      {block.items.length > VISIBLE_ITEMS && (
        <div className="flex flex-wrap items-center justify-between gap-2 rounded-md bg-subtle px-3 py-2 text-body text-text-secondary">
          <span>{hidden > 0 ? t("hiddenCount", { count: hidden }) : t("allShown", { count: block.items.length })}</span>
          <Button variant="secondary" size="sm" aria-expanded={expanded} aria-controls={listId} onClick={() => setExpanded((value) => !value)}>
            {expanded ? t("showLess") : t("showAll", { count: block.items.length })}
          </Button>
        </div>
      )}
    </div>
  );
}

function PreviewItemView({ item, setIndex }: { item: PreviewItem; setIndex: number }) {
  const t = useTranslations("exams.preview");
  if (item.kind === "QUESTION") return <QuestionArticle question={item.question} />;
  const headingId = `preview-set-${item.setId}`;
  return (
    <article aria-labelledby={headingId} className="flex flex-col gap-3 rounded-md border border-border p-3">
      <div className="flex flex-wrap items-center gap-2">
        <h5 id={headingId} className="font-semibold text-text">
          {t("setHeading", { index: setIndex, count: item.children.length })}
        </h5>
        <Badge>{item.version !== null ? `${item.code} · v${item.version}` : item.code}</Badge>
      </div>
      {item.passage ? (
        <blockquote className="rounded-md border-s-4 border-border bg-subtle px-3 py-2 text-body text-text">
          <RichTextView html={item.passage} blankNumberStart={item.blankNumberStart} />
        </blockquote>
      ) : (
        <p className="text-body text-text-muted italic">{t("simulatedPassage")}</p>
      )}
      <ol className="flex flex-col gap-2">
        {item.children.map((child) => (
          <li key={child.questionId}>
            <QuestionArticle question={child} compact />
          </li>
        ))}
      </ol>
    </article>
  );
}

function QuestionArticle({ question, compact = false }: { question: PreviewQuestion; compact?: boolean }) {
  const t = useTranslations("exams.preview");
  const types = useTranslations("content.questionType");
  const lookup = useLookup();
  const headingId = `preview-q-${question.questionId}`;
  const isTrueFalse = question.type === "TRUE_FALSE";
  const Heading = compact ? "h6" : "h5";

  return (
    <article aria-labelledby={headingId} className={compact ? "flex flex-col gap-2" : "flex flex-col gap-2 rounded-md border border-border p-3"}>
      <div className="flex flex-wrap items-center gap-2">
        <Heading id={headingId} className="font-semibold text-text">
          {t("questionNumber", { number: question.number })}
        </Heading>
        <Badge>{question.version !== null ? `${question.code} · v${question.version}` : question.code}</Badge>
        <Badge>{types(question.type)}</Badge>
        <Badge>{lookup.grade(question.gradeCode)}</Badge>
        <Badge>{lookup.difficulty(question.difficultyCode)}</Badge>
        {question.knowledge && (
          <Badge className="whitespace-normal wrap-anywhere">{`${question.knowledge.code} · ${question.knowledge.name}`}</Badge>
        )}
      </div>
      {question.simulated ? (
        <p className="text-body text-text-muted italic">{t("simulatedQuestion")}</p>
      ) : (
        <RichTextView html={question.content ?? ""} className="text-body text-text" />
      )}
      {question.options && (
        <ol className="flex flex-col gap-1.5">
          {question.options.map((option, index) => {
            const marked = isTrueFalse || option.correct;
            return (
              <li
                key={option.id}
                className={`flex min-h-10 items-center gap-2.5 rounded-md border px-3 py-1.5 text-body ${
                  option.correct && !isTrueFalse ? "border-primary-600 bg-primary-50" : "border-border"
                }`}
              >
                <span aria-hidden className="grid size-6 shrink-0 place-items-center rounded-full bg-subtle text-caption font-semibold">
                  {OPTION_KEYS[index] ?? index + 1}
                </span>
                <RichTextView html={option.content} className="min-w-0 flex-1 text-text" />
                {marked && (
                  <span
                    className={`inline-flex shrink-0 items-center gap-1 text-caption font-medium ${
                      option.correct ? "text-primary-text" : "text-text-secondary"
                    }`}
                  >
                    {option.correct ? <Check aria-hidden className="size-3.5" /> : <X aria-hidden className="size-3.5" />}
                    {isTrueFalse ? (option.correct ? t("trueLabel") : t("falseLabel")) : t("correct")}
                  </span>
                )}
              </li>
            );
          })}
        </ol>
      )}
      {question.answer && (
        <p className="flex items-center gap-1.5 text-body text-primary-text">
          <Check aria-hidden className="size-4 shrink-0" />
          {t("answer", { answer: question.answer })}
        </p>
      )}
      {question.rubric && (
        <div className="flex flex-col gap-1 rounded-md bg-subtle px-3 py-2 text-body">
          <span className="font-medium text-text">{t("rubricTitle")}</span>
          {question.rubric.length === 0 ? (
            <span className="text-text-secondary">{t("rubricEmpty")}</span>
          ) : (
            <ul className="flex flex-col gap-0.5 text-text-secondary">
              {question.rubric.map((criterion) => (
                <li key={criterion.id}>{t("rubricItem", { description: criterion.description, points: criterion.points })}</li>
              ))}
            </ul>
          )}
        </div>
      )}
      {question.solution && (
        <details className="rounded-md bg-subtle px-3 py-2 text-body">
          <summary className={cn("cursor-pointer rounded-sm font-medium text-text", focusRing)}>{t("solution")}</summary>
          <RichTextView html={question.solution} className="mt-1.5 text-text-secondary" />
        </details>
      )}
    </article>
  );
}
