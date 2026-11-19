"use client";

import { Fragment } from "react";
import { useUiStrings } from "../components/ui-strings";
import { cn } from "../lib/cn";
import { RichTextView } from "../rich-text";
import type { KitQuestionProps } from "./shared";
import { childReview } from "./shared";
import type { AnswerValue, KitQuestion, KitQuestionOf, QuestionReview, QuestionSetAnswer } from "./types";

type QuestionSetBody = Extract<KitQuestion["body"], { type: "QUESTION_SET" }>;

export interface QuestionSetProps
  extends KitQuestionProps<KitQuestionOf<QuestionSetBody>, QuestionSetAnswer> {
  /** Câu con theo id, đã cùng `displayNo` của riêng nó. */
  children: KitQuestion[];
  /** Số bắt đầu đánh chỗ trống trong ngữ liệu (BE 3.4); mặc định `blankNumberStart` của thân, rồi 1. */
  blankNumberStart?: number;
}

/**
 * Bộ câu hỏi: ngữ liệu chung ghim cạnh trên màn rộng (sticky, cuộn riêng), xếp chồng trên điện
 * thoại (LT 8, canvas `CauHoiChumThi`, `ThiMobileChumBaiDoc`). Mỗi câu con là một `<Question>`
 * đầy đủ; đáp án gom trong `{children: {<id>: answer}}`, review của con đọc từ `review.children`.
 */
export function QuestionSet({
  question,
  mode,
  value,
  onChange,
  review,
  children,
  blankNumberStart,
  headingLevel,
  className,
  renderQuestion,
}: QuestionSetProps & {
  /** Vòng tránh import: kit truyền dispatcher `Question`; người dùng có thể đổi cách dựng con. */
  renderQuestion: (props: ChildQuestionProps) => React.ReactNode;
}) {
  const strings = useUiStrings().question;
  const childAnswers = value?.children ?? {};
  const numberStart = blankNumberStart ?? question.body.blankNumberStart ?? 1;
  const nos = children.map((child) => child.displayNo);
  const from = nos.length === 0 ? question.displayNo : Math.min(...nos);
  const to = nos.length === 0 ? question.displayNo : Math.max(...nos);
  const HeadingTag = `h${headingLevel ?? 2}` as "h1" | "h2" | "h3";
  return (
    <div className={cn("flex min-w-0 flex-col gap-4", className)}>
      <p className="text-label font-semibold tracking-wide text-text-secondary uppercase">
        {strings.setPassageCaption(from, to)}
      </p>
      {question.stem != null && question.stem !== "" && (
        <div data-exam-content className="text-title text-text">
          <RichTextView html={question.stem} />
        </div>
      )}
      <div className="grid min-w-0 items-start gap-5 lg:grid-cols-[minmax(0,1.1fr)_minmax(0,1fr)]">
        <aside
          aria-label={strings.setPassageCaption(from, to)}
          className="rounded-xl border border-border bg-surface p-4 text-title text-text lg:sticky lg:top-4 lg:max-h-[75vh] lg:overflow-y-auto"
        >
          <div data-exam-content>
            <RichTextView html={question.body.passage} blankNumberStart={numberStart} />
          </div>
        </aside>
        <div className="flex min-w-0 flex-col gap-6">
          <HeadingTag id={`${question.id}-title`} className="sr-only">
            {strings.setPassageCaption(from, to)}
          </HeadingTag>
          {children.map((child) => (
            <Fragment key={child.id}>
              {renderQuestion({
                question: child,
                mode,
                value: childAnswers[child.id] ?? null,
                onChange:
                  mode === "answer"
                    ? (next: AnswerValue) =>
                        onChange?.({ children: { ...childAnswers, [child.id]: next } })
                    : undefined,
                review: mode === "review" ? childReview(review, child.id) : null,
                headingLevel: Math.min(3, (headingLevel ?? 2) + 1) as 1 | 2 | 3,
              })}
            </Fragment>
          ))}
        </div>
      </div>
    </div>
  );
}

export interface ChildQuestionProps {
  question: KitQuestion;
  mode: "answer" | "review";
  value: AnswerValue | null;
  onChange?: (value: AnswerValue) => void;
  review: QuestionReview | null;
  headingLevel?: 1 | 2 | 3;
}
