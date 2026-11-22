"use client";

import { useUiStrings } from "../components/ui-strings";
import { ChoiceList } from "./choice-question";
import { QuestionFrame, ReviewPoints } from "./question-frame";
import type { KitQuestionProps } from "./shared";
import { resolveReview, reviewSolution } from "./shared";
import type {
  KitQuestion,
  KitQuestionOf,
  MultipleChoiceAnswer,
  SingleChoiceAnswer,
} from "./types";

type ChoiceBody = Extract<KitQuestion["body"], { type: "SINGLE_CHOICE" | "MULTIPLE_CHOICE" }>;

/** Câu Một đáp án (SINGLE_CHOICE): nhóm radio, bấm lại đáp án đã chọn để xóa (LT 8). */
export function SingleChoiceQuestion({
  question,
  mode,
  value,
  onChange,
  flagged,
  onToggleFlag,
  review,
  headingLevel,
  className,
}: KitQuestionProps<KitQuestionOf<ChoiceBody>, SingleChoiceAnswer>) {
  const strings = useUiStrings().question;
  const resolved = resolveReview(review);
  const selectedId = value?.optionId ?? null;
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
      reviewSolutionHtml={reviewSolution(mode, resolved)}
      className={className}
    >
      <ChoiceList
        options={question.body.options}
        optionOrder={question.optionOrder}
        selected={selectedId === null ? [] : [selectedId]}
        multiple={false}
        onChange={
          mode === "answer"
            ? (ids) => onChange?.(ids.length === 0 ? { optionId: null } : { optionId: ids[0]! })
            : undefined
        }
        review={mode === "review" ? (resolved ?? { outcome: "BLANK" }) : undefined}
        groupLabel={strings.questionLabel(question.displayNo)}
      />
    </QuestionFrame>
  );
}

/** Câu Nhiều đáp án (MULTIPLE_CHOICE): checkbox; luật chấm `scoringRule` chỉ ảnh hưởng điểm, không đổi nhập. */
export function MultipleChoiceQuestion({
  question,
  mode,
  value,
  onChange,
  flagged,
  onToggleFlag,
  review,
  headingLevel,
  className,
}: KitQuestionProps<KitQuestionOf<ChoiceBody>, MultipleChoiceAnswer>) {
  const strings = useUiStrings().question;
  const resolved = resolveReview(review);
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
      reviewSolutionHtml={reviewSolution(mode, resolved)}
      className={className}
    >
      <ChoiceList
        options={question.body.options}
        optionOrder={question.optionOrder}
        selected={value?.optionIds ?? []}
        multiple
        onChange={mode === "answer" ? (ids) => onChange?.({ optionIds: ids }) : undefined}
        review={mode === "review" ? (resolved ?? { outcome: "BLANK" }) : undefined}
        groupLabel={strings.questionLabel(question.displayNo)}
      />
    </QuestionFrame>
  );
}
