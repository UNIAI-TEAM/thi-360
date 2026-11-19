"use client";

import { useUiLocale, useUiStrings } from "../components/ui-strings";
import { cn } from "../lib/cn";
import { focusRing } from "../lib/styles";
import { RichTextView } from "../rich-text";
import { formatScore, statementLetter } from "./helpers";
import { OutcomeGlyph, QuestionFrame, ReviewPoints } from "./question-frame";
import type { KitQuestionProps } from "./shared";
import { answersVisible, resolveReview } from "./shared";
import type { KitQuestion, KitQuestionOf, QuestionReview, TrueFalseAnswer } from "./types";

type TrueFalseBody = Extract<KitQuestion["body"], { type: "TRUE_FALSE" }>;

/**
 * Câu Đúng/Sai: mỗi ý một hàng radio Đúng/Sai (LT 8, canvas `CauHoiDungSaiThi`).
 * Review: bảng ý — bạn chọn (kèm icon), đáp án khi được phép, điểm từng ý (PER_STATEMENT)
 * hoặc tóm tắt theo bảng số ý đúng (COUNT_TABLE).
 */
export function TrueFalseQuestion({
  question,
  mode,
  value,
  onChange,
  flagged,
  onToggleFlag,
  review,
  headingLevel,
  className,
}: KitQuestionProps<KitQuestionOf<TrueFalseBody>, TrueFalseAnswer>) {
  const strings = useUiStrings().question;
  const locale = useUiLocale();
  const resolved = resolveReview(review);
  const body = question.body;
  const statements = value?.statements ?? {};
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
      {mode === "answer" && (
        <div className="flex flex-col gap-3">
          <p className="text-caption text-text-secondary">{strings.trueFalseHelp}</p>
          <ul className="flex flex-col gap-2.5">
            {body.statements.map((statement, index) => {
              const letter = statementLetter(index);
              const picked = statements[statement.id];
              const textId = `${question.id}-${statement.id}-text`;
              return (
                <li
                  key={statement.id}
                  className={cn(
                    "flex min-h-12 flex-wrap items-center justify-between gap-x-4 gap-y-2 rounded-lg border px-3 py-2",
                    picked === undefined || picked === null
                      ? "border-border bg-surface"
                      : "border-border bg-subtle",
                  )}
                >
                  <span className="flex min-w-0 flex-1 items-baseline gap-2 text-title text-text">
                    <strong className="shrink-0 font-semibold" aria-hidden>
                      {letter})
                    </strong>
                    <span id={textId} data-exam-content className="min-w-0 flex-1 wrap-break-word">
                      <RichTextView html={statement.content} />
                    </span>
                  </span>
                  <span role="radiogroup" aria-labelledby={textId} className="flex items-center gap-3">
                    <TrueFalseRadio
                      name={`${question.id}-${statement.id}`}
                      checked={picked === true}
                      label={strings.trueOption}
                      onPick={() => onChange?.({ statements: { ...statements, [statement.id]: true } })}
                    />
                    <TrueFalseRadio
                      name={`${question.id}-${statement.id}`}
                      checked={picked === false}
                      label={strings.falseOption}
                      onPick={() => onChange?.({ statements: { ...statements, [statement.id]: false } })}
                    />
                  </span>
                </li>
              );
            })}
          </ul>
        </div>
      )}
      {mode === "review" && (
        <TrueFalseReview question={question} value={value} review={resolved} locale={locale} />
      )}
    </QuestionFrame>
  );
}

function TrueFalseRadio({
  name,
  checked,
  label,
  onPick,
}: {
  name: string;
  checked: boolean;
  label: string;
  onPick: () => void;
}) {
  return (
    <label className="flex min-h-8 cursor-pointer items-center gap-1.5 text-label font-semibold whitespace-nowrap text-text">
      <input
        type="radio"
        name={name}
        checked={checked}
        onChange={onPick}
        className={cn("size-4.5 accent-primary-fill", focusRing)}
      />
      {label}
    </label>
  );
}

function TrueFalseReview({
  question,
  value,
  review,
  locale,
}: {
  question: KitQuestionOf<TrueFalseBody>;
  value: TrueFalseAnswer | null | undefined;
  review: QuestionReview | null;
  locale: "vi" | "en";
}) {
  const strings = useUiStrings().question;
  const statements = value?.statements ?? {};
  const showAnswers = answersVisible(review);
  const perStatement = question.body.scoringMethod === "PER_STATEMENT";
  const graded = question.body.statements.filter(
    (statement) => review?.gradingDetail?.[statement.id] != null,
  );
  const correctCount = graded.filter(
    (statement) => review?.gradingDetail?.[statement.id]?.outcome === "CORRECT",
  ).length;
  return (
    <div className="flex flex-col gap-3">
      <div className="overflow-x-auto">
        <table className="w-full border-collapse text-left">
          <thead>
            <tr className="border-b border-border">
              <th className="py-2 pr-4 text-caption font-semibold text-text-secondary">{strings.itemCol}</th>
              <th className="py-2 pr-4 text-caption font-semibold text-text-secondary">{strings.youChoseCol}</th>
              {showAnswers && (
                <th className="py-2 pr-4 text-caption font-semibold text-text-secondary">{strings.answerCol}</th>
              )}
              <th className="py-2 text-caption font-semibold text-text-secondary">{strings.pointsCol}</th>
            </tr>
          </thead>
          <tbody>
            {question.body.statements.map((statement, index) => {
              const part = review?.gradingDetail?.[statement.id];
              const picked = statements[statement.id];
              const answerLabel = statement.answer === true ? strings.trueOption : strings.falseOption;
              return (
                <tr key={statement.id} className="border-b border-border/60 last:border-0 align-top">
                  <td className="py-2.5 pr-4">
                    <span className="flex items-baseline gap-2 text-label text-text">
                      <strong className="shrink-0 font-semibold">{statementLetter(index)})</strong>
                      <span data-exam-content className="wrap-break-word">
                        <RichTextView html={statement.content} />
                      </span>
                    </span>
                  </td>
                  <td className="py-2.5 pr-4 text-label font-semibold whitespace-nowrap text-text">
                    <span className="inline-flex items-center gap-2">
                      {part && <OutcomeGlyph outcome={part.outcome} />}
                      {picked === true ? strings.trueOption : picked === false ? strings.falseOption : (
                        <span className="font-normal text-text-muted">{strings.notAnswered}</span>
                      )}
                    </span>
                  </td>
                  {showAnswers && (
                    <td className="py-2.5 pr-4 text-label text-text">{answerLabel}</td>
                  )}
                  <td className="py-2.5 text-label text-text tabular-nums">
                    {part?.earned != null
                      ? strings.points(
                          formatScore(part.earned, locale),
                          formatScore(part.max ?? statement.points ?? 1, locale),
                        )
                      : "—"}
                  </td>
                </tr>
              );
            })}
          </tbody>
        </table>
      </div>
      {!perStatement && review?.earned != null && (
        <p className="text-caption text-text-secondary" data-exam-content>
          <strong className="font-semibold text-text">
            {strings.countTableSummary(
              correctCount,
              question.body.statements.length,
              formatScore(review.earned, locale),
            )}
          </strong>
        </p>
      )}
    </div>
  );
}
