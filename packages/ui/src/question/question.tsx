"use client";

import { MultipleChoiceQuestion, SingleChoiceQuestion } from "./choice-body";
import { ClozeQuestion } from "./cloze";
import { EssayQuestion, type EssayEditorProps } from "./essay";
import { MatchingQuestion } from "./matching";
import { QuestionSet, type ChildQuestionProps } from "./question-set";
import { ShortAnswerQuestion } from "./short-answer";
import { TrueFalseQuestion } from "./true-false";
import type { KitQuestionProps } from "./shared";
import type {
  AnswerValue,
  EssayAnswer,
  KitQuestion,
  KitQuestionOf,
  QuestionBody,
  QuestionSetAnswer,
} from "./types";


export interface QuestionProps extends KitQuestionProps<KitQuestion, AnswerValue> {
  /** Câu con của `QUESTION_SET` theo `body.childIds` (bắt buộc với dạng chùm). */
  children?: KitQuestion[];
  /** Ghép đôi: "select" (mặc định) hay "pick". */
  matchUi?: "select" | "pick";
  /** Tự luận: lắp trình soạn thảo (Tiptap của FS-5). */
  renderEditor?: (props: EssayEditorProps) => React.ReactNode;
}

/**
 * Điểm vào duy nhất của kit: phân phối theo `question.body.type` tới đúng thành phần
 * (LT 8, FS-4). `QUESTION_SET` cần `children` (danh sách câu con theo `childIds`).
 */
export function Question({
  question,
  children,
  matchUi,
  renderEditor,
  ...rest
}: QuestionProps) {
  switch (question.body.type) {
    case "SINGLE_CHOICE":
      return (
        <SingleChoiceQuestion
          {...rest}
          question={question as KitQuestionOf<Extract<QuestionBody, { type: "SINGLE_CHOICE" }>>}
          value={rest.value as { optionId: string | null } | null | undefined}
          onChange={rest.onChange as ((value: { optionId: string | null }) => void) | undefined}
        />
      );
    case "MULTIPLE_CHOICE":
      return (
        <MultipleChoiceQuestion
          {...rest}
          question={question as KitQuestionOf<Extract<QuestionBody, { type: "MULTIPLE_CHOICE" }>>}
          value={rest.value as { optionIds: string[] } | null | undefined}
          onChange={rest.onChange as ((value: { optionIds: string[] }) => void) | undefined}
        />
      );
    case "SHORT_ANSWER":
      return (
        <ShortAnswerQuestion
          {...rest}
          question={question as KitQuestionOf<Extract<QuestionBody, { type: "SHORT_ANSWER" }>>}
          value={rest.value as { text: string } | null | undefined}
          onChange={rest.onChange as ((value: { text: string }) => void) | undefined}
        />
      );
    case "CLOZE":
      return (
        <ClozeQuestion
          {...rest}
          question={question as KitQuestionOf<Extract<QuestionBody, { type: "CLOZE" }>>}
          value={rest.value as { blanks: Record<string, string> } | null | undefined}
          onChange={rest.onChange as ((value: { blanks: Record<string, string> }) => void) | undefined}
        />
      );
    case "TRUE_FALSE":
      return (
        <TrueFalseQuestion
          {...rest}
          question={question as KitQuestionOf<Extract<QuestionBody, { type: "TRUE_FALSE" }>>}
          value={rest.value as { statements: Record<string, boolean | null> } | null | undefined}
          onChange={
            rest.onChange as
              | ((value: { statements: Record<string, boolean | null> }) => void)
              | undefined
          }
        />
      );
    case "MATCHING":
      return (
        <MatchingQuestion
          {...rest}
          question={question as KitQuestionOf<Extract<QuestionBody, { type: "MATCHING" }>>}
          value={rest.value as { pairs: Record<string, string | null> } | null | undefined}
          onChange={
            rest.onChange as
              | ((value: { pairs: Record<string, string | null> }) => void)
              | undefined
          }
          matchUi={matchUi}
        />
      );
    case "ESSAY":
      return (
        <EssayQuestion
          {...rest}
          question={question as KitQuestionOf<Extract<QuestionBody, { type: "ESSAY" }>>}
          value={rest.value as EssayAnswer | null | undefined}
          onChange={rest.onChange as ((value: EssayAnswer) => void) | undefined}
          renderEditor={renderEditor}
        />
      );
    case "QUESTION_SET":
      return (
        <QuestionSet
          question={question as KitQuestionOf<Extract<QuestionBody, { type: "QUESTION_SET" }>>}
          mode={rest.mode}
          value={rest.value as QuestionSetAnswer | null | undefined}
          onChange={rest.onChange as ((value: QuestionSetAnswer) => void) | undefined}
          review={rest.review}
          children={children ?? []}
          headingLevel={rest.headingLevel}
          className={rest.className}
          renderQuestion={(childProps: ChildQuestionProps) => (
            <Question
              question={childProps.question}
              mode={childProps.mode}
              value={childProps.value}
              onChange={childProps.onChange}
              review={childProps.review}
              headingLevel={childProps.headingLevel}
              matchUi={matchUi}
              renderEditor={renderEditor}
            />
          )}
        />
      );
  }
}
