"use client";

import { Chip, cn } from "@thi360/ui";
import { CircleCheck, CircleX } from "lucide-react";
import { useTranslations } from "next-intl";
import { Fragment, type ReactNode } from "react";
import { BLANK_MARKER } from "../editor-form";
import type { QuestionBody, QuestionDetail } from "../types";
import { optionLetter, statementLetter } from "./question-editor-shared";

type BodySource = Pick<QuestionDetail, "type" | "content" | "body" | "children" | "solution">;

/**
 * Đề bài và đáp án của một câu theo dạng (canvas `CauHoi*ChiTiet`). Dùng ở trang chi tiết, hộp thoại xem một
 * phiên bản cũ và cho từng câu con của câu chùm. Đáp án đúng có icon và chữ, không chỉ bằng màu.
 */
export function QuestionBodyView({ question, headingLevel = 3 }: { question: BodySource; headingLevel?: 3 | 4 }) {
  const t = useTranslations("questions.detail.body");
  if (question.body.type === "QUESTION_SET") {
    return <QuestionSetView question={question} headingLevel={headingLevel} />;
  }
  return (
    <div className="flex flex-col gap-3">
      {question.body.type === "FILL_IN" ? (
        <FillInContent content={question.content} body={question.body} />
      ) : (
        <p className="text-body whitespace-pre-wrap text-text">{question.content || t("noContent")}</p>
      )}
      <AnswersView body={question.body} />
    </div>
  );
}

function QuestionSetView({ question, headingLevel }: { question: BodySource; headingLevel: 3 | 4 }) {
  const t = useTranslations("questions.detail.body");
  const tType = useTranslations("content.questionType");
  const Heading = headingLevel === 3 ? "h3" : "h4";
  const passage = question.body.type === "QUESTION_SET" ? question.body.passage || question.content : question.content;
  return (
    <div className="flex flex-col gap-4">
      <p className="rounded-lg border border-border bg-subtle p-3.5 text-body whitespace-pre-wrap text-text">{passage}</p>
      <ol className="m-0 flex list-none flex-col gap-3 p-0">
        {question.children.map((child, index) => (
          <li key={child.id} className="flex flex-col gap-2.5 rounded-lg border border-border p-3.5">
            <div className="flex flex-wrap items-center gap-2">
              <Heading className="text-label font-semibold text-text">{t("childTitle", { number: index + 1 })}</Heading>
              <Chip variant="tag" className="h-6">
                {tType(child.type)}
              </Chip>
              <span className="text-caption text-text-muted">{t("points", { points: child.points })}</span>
            </div>
            <QuestionBodyView question={child} headingLevel={4} />
            {child.solution && (
              <p className="text-caption text-text-secondary">
                <span className="font-semibold">{t("childSolution")}</span> {child.solution}
              </p>
            )}
          </li>
        ))}
      </ol>
    </div>
  );
}

/** Đề bài Điền đáp án: mỗi `___` thành ô có số thứ tự và đáp án chấp nhận. */
function FillInContent({ content, body }: { content: string; body: Extract<QuestionBody, { type: "FILL_IN" }> }) {
  const t = useTranslations("questions.detail.body");
  const parts = content.split(BLANK_MARKER);
  return (
    <p className="text-body leading-8 whitespace-pre-wrap text-text">
      {parts.map((part, index) => {
        const blank = index < parts.length - 1 ? body.blanks[index] : undefined;
        return (
          <Fragment key={index}>
            {part}
            {index < parts.length - 1 && (
              <mark className="mx-0.5 inline-flex items-baseline gap-1.5 rounded-md border border-primary-600 bg-primary-50 px-2 text-primary-text">
                <span className="text-caption font-semibold">{t("blankLabel", { number: index + 1 })}</span>
                <span className="font-semibold">
                  {blank?.acceptedAnswers.length ? blank.acceptedAnswers.join(" / ") : t("noAnswer")}
                </span>
              </mark>
            )}
          </Fragment>
        );
      })}
    </p>
  );
}

function CorrectMark({ correct, children }: { correct: boolean; children?: ReactNode }) {
  const t = useTranslations("questions.detail.body");
  if (!correct) return children ?? null;
  return (
    <span className="inline-flex items-center gap-1 text-caption font-semibold text-success">
      <CircleCheck aria-hidden className="size-4" />
      {t("correct")}
    </span>
  );
}

export function AnswersView({ body }: { body: QuestionBody }) {
  const t = useTranslations("questions.detail.body");
  switch (body.type) {
    case "SINGLE_CHOICE":
    case "MULTIPLE_CHOICE":
      return (
        <ol className="m-0 flex list-none flex-col gap-2 p-0">
          {body.options.map((option, index) => (
            <li
              key={option.id}
              className={cn(
                "flex flex-wrap items-center gap-x-3 gap-y-1 rounded-lg border px-3.5 py-2.5",
                option.correct ? "border-success bg-success-bg" : "border-border",
              )}
            >
              <span className="font-semibold text-text">{optionLetter(index)}.</span>
              <span className="min-w-0 flex-1 wrap-break-word text-text">{option.content}</span>
              <CorrectMark correct={option.correct} />
            </li>
          ))}
        </ol>
      );
    case "TRUE_FALSE":
      return (
        <ol className="m-0 flex list-none flex-col gap-2 p-0">
          {body.statements.map((statement, index) => (
            <li key={statement.id} className="flex flex-wrap items-center gap-x-3 gap-y-1 rounded-lg border border-border px-3.5 py-2.5">
              <span className="font-semibold text-text">{statementLetter(index)})</span>
              <span className="min-w-0 flex-1 wrap-break-word text-text">{statement.content}</span>
              <span
                className={cn(
                  "inline-flex items-center gap-1 text-caption font-semibold",
                  statement.answer ? "text-success" : "text-danger",
                )}
              >
                {statement.answer ? <CircleCheck aria-hidden className="size-4" /> : <CircleX aria-hidden className="size-4" />}
                {statement.answer ? t("true") : t("false")}
              </span>
            </li>
          ))}
        </ol>
      );
    case "FILL_IN":
      return null;
    case "DRAG_DROP": {
      const contentOf = new Map(body.items.map((item) => [item.id, item.content]));
      const used = new Set(body.zones.map((zone) => zone.correctItemId));
      const distractors = body.items.filter((item) => !used.has(item.id));
      return (
        <div className="flex flex-col gap-2">
          <ol className="m-0 flex list-none flex-col gap-2 p-0">
            {body.zones.map((zone, index) => (
              <li key={zone.id} className="flex flex-wrap items-center gap-x-3 gap-y-1 rounded-lg border border-border px-3.5 py-2.5">
                <span className="text-caption font-semibold text-text-muted">{t("zoneLabel", { number: index + 1 })}</span>
                <span className="min-w-0 flex-1 wrap-break-word text-text">{zone.label}</span>
                <span className="inline-flex items-center gap-1.5 rounded-md border border-success bg-success-bg px-2 py-0.5 font-semibold text-success">
                  <CircleCheck aria-hidden className="size-4" />
                  <span className="sr-only">{t("correctItem")}</span>
                  {contentOf.get(zone.correctItemId) ?? t("noAnswer")}
                </span>
              </li>
            ))}
          </ol>
          <p className="text-caption text-text-secondary">
            {distractors.length
              ? t("distractors", { items: distractors.map((item) => item.content).join(", ") })
              : t("noDistractors")}
          </p>
        </div>
      );
    }
    case "QUESTION_SET":
      return null;
  }
}

/** Mô tả ngắn phần đáp án dạng chữ (so sánh phiên bản). */
export function describeBody(body: QuestionBody, t: (key: string, values?: Record<string, string | number>) => string): string[] {
  switch (body.type) {
    case "SINGLE_CHOICE":
    case "MULTIPLE_CHOICE":
      return body.options.map(
        (option, index) => `${optionLetter(index)}. ${option.content}${option.correct ? ` (${t("correct")})` : ""}`,
      );
    case "TRUE_FALSE":
      return body.statements.map(
        (statement, index) => `${statementLetter(index)}) ${statement.content}: ${statement.answer ? t("true") : t("false")}`,
      );
    case "FILL_IN":
      return body.blanks.map(
        (blank, index) => `${t("blankLabel", { number: index + 1 })}: ${blank.acceptedAnswers.join(" / ") || t("noAnswer")}`,
      );
    case "DRAG_DROP": {
      const contentOf = new Map(body.items.map((item) => [item.id, item.content]));
      const used = new Set(body.zones.map((zone) => zone.correctItemId));
      const distractors = body.items.filter((item) => !used.has(item.id));
      return [
        ...body.zones.map(
          (zone, index) =>
            `${t("zoneLabel", { number: index + 1 })} ${zone.label} → ${contentOf.get(zone.correctItemId) ?? t("noAnswer")}`,
        ),
        ...(distractors.length ? [t("distractors", { items: distractors.map((item) => item.content).join(", ") })] : []),
      ];
    }
    case "QUESTION_SET":
      return [body.passage];
  }
}
