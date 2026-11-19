"use client";

import { Input } from "../components/input";
import { useUiLocale, useUiStrings } from "../components/ui-strings";
import { cn } from "../lib/cn";
import { formatScore } from "./helpers";
import { OutcomeGlyph, QuestionFrame, ReviewPoints } from "./question-frame";
import type { KitQuestionProps } from "./shared";
import { answersVisible, resolveReview } from "./shared";
import { touchFieldText } from "./touch";
import type { AnswerFormat, KitQuestion, KitQuestionOf, ShortAnswerAnswer } from "./types";

type ShortAnswerBody = Extract<KitQuestion["body"], { type: "SHORT_ANSWER" }>;

/**
 * Câu Trả lời ngắn: ô nhập đáp số văn bản + `inputHint`/`answerFormat` gợi ý định dạng (LT 4, F14).
 * Review: đáp án đã nộp (không sửa), kết quả, và đáp án chuẩn `answers[0]` khi được phép.
 */
export function ShortAnswerQuestion({
  question,
  mode,
  value,
  onChange,
  flagged,
  onToggleFlag,
  review,
  headingLevel,
  className,
}: KitQuestionProps<KitQuestionOf<ShortAnswerBody>, ShortAnswerAnswer>) {
  const strings = useUiStrings().question;
  const locale = useUiLocale();
  const resolved = resolveReview(review);
  const body = question.body;
  const text = value?.text ?? "";
  const inputId = `${question.id}-input`;
  const noteId = `${question.id}-note`;
  const hints = [body.inputHint, formatHint(body.answerFormat)].filter(
    (hint): hint is string => hint != null && hint !== "",
  );
  return (
    <QuestionFrame
      question={question}
      headingLevel={headingLevel}
      flagged={mode === "answer" ? flagged : undefined}
      onToggleFlag={mode === "answer" ? onToggleFlag : undefined}
      reviewOutcome={mode === "review" ? (resolved?.outcome ?? null) : undefined}
      reviewPoints={
        mode === "review" ? (
          <ReviewPoints earned={resolved?.earned} max={resolved?.max ?? question.points ?? 1} />
        ) : undefined
      }
      className={className}
    >
      <div className="flex max-w-[26rem] flex-col gap-2">
        <label htmlFor={inputId} className="text-label font-medium text-text">
          {strings.answerLabel}
        </label>
        <div className="flex items-center gap-3">
          <Input
            id={inputId}
            size="lg"
            value={text}
            readOnly={mode === "review"}
            aria-readonly={mode === "review" || undefined}
            placeholder={strings.answerPlaceholder}
            inputMode={
              body.answerFormat === "INTEGER" || body.answerFormat === "DECIMAL" || body.answerFormat === "FRACTION"
                ? "decimal"
                : undefined
            }
            aria-describedby={mode === "answer" ? noteId : undefined}
            onChange={(event) => onChange?.({ text: event.target.value })}
            className={cn("font-semibold tabular-nums", touchFieldText)}
          />
          {mode === "review" && resolved?.outcome && (
            <span className="inline-flex size-8 shrink-0 items-center justify-center rounded-full border border-border bg-surface">
              <OutcomeGlyph outcome={resolved.outcome} className="size-4" />
            </span>
          )}
        </div>
        {mode === "answer" && (
          <p id={noteId} className="text-caption text-text-secondary">
            {hints.length > 0 && (
              <>
                {strings.hintPrefix}{" "}
                <strong data-exam-content className="font-semibold text-text">
                  {hints.join(" · ")}
                </strong>
                {". "}
              </>
            )}
            {strings.shortAnswerNote}
          </p>
        )}
        {mode === "review" && (resolved?.outcome === "BLANK" || text.trim() === "") && (
          <p className="text-caption text-text-secondary">{strings.notAnswered}</p>
        )}
        {mode === "review" && answersVisible(resolved) && body.answers.length > 0 && (
          <p className="text-caption text-text-secondary">
            <span className="font-semibold text-text">{strings.correctAnswerPrefix} </span>
            {body.answers.map((answer, index) => (
              <span key={answer}>
                {index > 0 && " · "}
                <strong data-exam-content className={index === 0 ? "font-semibold text-text" : "font-normal"}>
                  {answer}
                </strong>
              </span>
            ))}
            {resolved?.earned != null && (
              <span className="text-text-muted">
                {" "}
                ({formatScore(resolved.earned, locale)} /{" "}
                {formatScore(resolved.max ?? question.points ?? 1, locale)} {strings.pointsSuffix})
              </span>
            )}
          </p>
        )}
      </div>
    </QuestionFrame>
  );
}

function formatHint(format: AnswerFormat | null | undefined): string | null {
  switch (format) {
    case "INTEGER":
      return "0-9";
    case "DECIMAL":
    case "FRACTION":
      return "0-9 · .";
    case "CHEM_FORMULA":
    case "CHEM_EQUATION":
      return "\\ce{}";
    case "TEXT":
    default:
      return null;
  }
}
