"use client";

import { Fragment } from "react";
import { useUiStrings } from "../components/ui-strings";
import { cn } from "../lib/cn";
import { RichTextView } from "../rich-text";
import { SolutionBlock } from "./question-frame";
import type { KitQuestionProps } from "./shared";
import { childReview, reviewSolution } from "./shared";
import type { AnswerValue, KitQuestion, KitQuestionOf, QuestionReview, QuestionSetAnswer } from "./types";

type QuestionSetBody = Extract<KitQuestion["body"], { type: "QUESTION_SET" }>;

export interface QuestionSetProps
  extends KitQuestionProps<KitQuestionOf<QuestionSetBody>, QuestionSetAnswer> {
  /** Câu con theo id, đã cùng `displayNo` của riêng nó. */
  children: KitQuestion[];
  /** Số bắt đầu đánh chỗ trống trong ngữ liệu (BE 3.4); mặc định `blankNumberStart` của thân, rồi 1. */
  blankNumberStart?: number;
  /** Cờ đánh dấu xem lại của từng câu con theo id (cờ lưu theo `attempt_item` của câu con, LT 7.3). */
  flaggedIds?: Record<string, boolean>;
  /** Bấm cờ của câu con `childId`; có hàm này thì mỗi câu con hiện nút cờ ở chế độ làm bài. */
  onToggleChildFlag?: (childId: string) => void;
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
  flaggedIds,
  onToggleChildFlag,
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
  const HeadingTag = `h${headingLevel ?? 2}` as const;
  const solution = reviewSolution(mode, review);
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
      <div className="grid min-w-0 items-start gap-4 lg:grid-cols-[minmax(0,7fr)_minmax(0,8fr)]">
        <aside
          aria-label={strings.setPassageCaption(from, to)}
          className="rounded-xl border border-border bg-surface p-4 text-title text-text lg:sticky lg:top-[var(--attempt-sticky-top,1rem)] lg:max-h-[calc(100dvh-var(--attempt-sticky-top,1rem)-1.5rem)] lg:overflow-y-auto scroll-shadows-y"
        >
          <div
            aria-hidden="true"
            className="mb-2.5 hidden items-center gap-2 border-b border-border pb-2 text-caption font-semibold tracking-wide text-text-secondary uppercase lg:flex"
          >
            <span className="h-3.5 w-1 shrink-0 rounded-full bg-primary-600" />
            <span>{strings.questionRangeLabel(from, to)}</span>
          </div>
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
                headingLevel: Math.min(6, (headingLevel ?? 2) + 1) as 1 | 2 | 3 | 4 | 5 | 6,
                flagged: flaggedIds?.[child.id],
                onToggleFlag:
                  mode === "answer" && onToggleChildFlag ? () => onToggleChildFlag(child.id) : undefined,
              })}
            </Fragment>
          ))}
        </div>
      </div>
      {solution && <SolutionBlock html={solution} />}
    </div>
  );
}

export interface ChildQuestionProps {
  question: KitQuestion;
  mode: "answer" | "review";
  value: AnswerValue | null;
  onChange?: (value: AnswerValue) => void;
  review: QuestionReview | null;
  headingLevel?: 1 | 2 | 3 | 4 | 5 | 6;
  flagged?: boolean;
  onToggleFlag?: () => void;
}
