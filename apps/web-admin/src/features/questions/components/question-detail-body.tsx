"use client";

import { Badge, Chip, cn } from "@thi360/ui";
import { CircleCheck, CircleX, PenLine } from "lucide-react";
import { useFormatter, useTranslations } from "next-intl";
import { type ReactNode } from "react";
import { questionHtmlText, RichTextView } from "@/components/rich-text";
import { ClozePassage } from "./cloze-passage";
import { blankNumberStartOf, type QuestionBody, type QuestionDetail } from "../types";
import { optionLetter, statementLetter } from "./question-editor-shared";

type BodySource = Pick<QuestionDetail, "type" | "content" | "body" | "children" | "solution" | "points">;

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
      {question.body.type === "CLOZE" ? (
        <ClozeContent body={question.body} />
      ) : (
        <div className="text-body text-text">
          {question.content ? <RichTextView html={question.content} /> : t("noContent")}
        </div>
      )}
      <AnswersView body={question.body} points={question.points} />
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
      <div className="rounded-lg border border-border bg-subtle p-3.5 text-body text-text">
        <RichTextView html={passage} blankNumberStart={blankNumberStartOf(question.body)} />
      </div>
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
              <div className="flex gap-1 text-caption text-text-secondary">
                <span className="font-semibold">{t("childSolution")}</span>
                <RichTextView className="min-w-0 flex-1" html={child.solution} />
              </div>
            )}
          </li>
        ))}
      </ol>
    </div>
  );
}

/** Ngữ liệu Điền khuyết: mỗi `[[id]]` thành ô có số thứ tự và đáp án. */
function ClozeContent({ body }: { body: Extract<QuestionBody, { type: "CLOZE" }> }) {
  const t = useTranslations("questions.detail.body");
  return (
    <ClozePassage
      html={body.passage}
      className="text-body leading-8 text-text"
      renderBlank={(_blankId, index) => {
        const blank = body.blanks[index];
        return (
          <mark className="mx-0.5 inline-flex items-baseline gap-1.5 rounded-md border border-primary-600 bg-primary-50 px-2 text-primary-text">
            <span className="text-caption font-semibold">{t("blankLabel", { number: index + 1 })}</span>
            <span className="font-semibold">{blank?.answers.length ? blank.answers.join(" / ") : t("noAnswer")}</span>
          </mark>
        );
      }}
    />
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

/** Nhãn "Chấm tay" của câu Tự luận (chữ và icon, không chỉ màu). */
export function ManualGradingBadge() {
  const t = useTranslations("questions.detail.body");
  return (
    <Badge>
      <PenLine aria-hidden className="me-1 size-3.5" />
      {t("manualGrading")}
    </Badge>
  );
}

export function AnswersView({ body, points }: { body: QuestionBody; points?: number }) {
  const t = useTranslations("questions.detail.body");
  switch (body.type) {
    case "SINGLE_CHOICE":
    case "MULTIPLE_CHOICE":
      return (
        <div className="flex flex-col gap-2">
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
                <RichTextView className="min-w-0 flex-1 wrap-break-word text-text" html={option.content} />
                <CorrectMark correct={option.correct} />
              </li>
            ))}
          </ol>
          {body.type === "MULTIPLE_CHOICE" && (
            <p className="text-caption text-text-secondary">
              <span className="font-semibold">{t("scoringRule")}</span> {t(`scoringRules.${body.scoringRule}`)}
            </p>
          )}
        </div>
      );
    case "TRUE_FALSE":
      return (
        <div className="flex flex-col gap-3">
          <ol className="m-0 flex list-none flex-col gap-2 p-0">
            {body.statements.map((statement, index) => (
              <li key={statement.id} className="flex flex-wrap items-center gap-x-3 gap-y-1 rounded-lg border border-border px-3.5 py-2.5">
                <span className="font-semibold text-text">{statementLetter(index)})</span>
                <RichTextView className="min-w-0 flex-1 wrap-break-word text-text" html={statement.content} />
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
          <ScoreTableView table={body.scoringMethod === "COUNT_TABLE" ? body.scoreTable : undefined} />
        </div>
      );
    case "ESSAY":
      return (
        <div className="flex flex-col gap-3">
          <div className="flex flex-wrap items-center gap-2">
            <ManualGradingBadge />
            <span className="text-caption text-text-secondary">
              {points !== undefined && t("essayMaxPoints", { points })}
              {body.wordLimit !== null && ` · ${t("wordLimit", { count: body.wordLimit })}`}
            </span>
          </div>
          <div className="flex flex-col gap-1">
            <p className="text-label font-semibold text-text">{t("gradingGuideTitle")}</p>
            <div className="rounded-lg border border-border bg-subtle p-3.5 text-body text-text">
              {body.gradingGuide ? <RichTextView html={body.gradingGuide} /> : t("noGradingGuide")}
            </div>
          </div>
          <div className="flex flex-col gap-2">
            <p className="text-label font-semibold text-text">{t("rubricTitle")}</p>
            {body.rubric.length === 0 ? (
              <p className="text-body text-text-muted">{t("noRubric")}</p>
            ) : (
              <ol className="m-0 flex list-none flex-col gap-2 p-0">
                {body.rubric.map((criterion, index) => (
                  <li
                    key={criterion.id}
                    className="flex flex-wrap items-baseline gap-x-3 gap-y-1 rounded-lg border border-border px-3.5 py-2.5"
                  >
                    <span className="font-semibold text-text">{index + 1}.</span>
                    <RichTextView
                      className="min-w-0 flex-1 wrap-break-word text-text"
                      html={criterion.description || t("noContent")}
                    />
                    <span className="shrink-0 text-caption font-semibold text-text-secondary">
                      {t("points", { points: criterion.points })}
                    </span>
                  </li>
                ))}
              </ol>
            )}
          </div>
          {body.sampleAnswer && (
            <div className="flex flex-col gap-1">
              <p className="text-label font-semibold text-text">{t("sampleAnswer")}</p>
              <div className="rounded-lg border border-border bg-subtle p-3.5 text-body text-text">
                <RichTextView html={body.sampleAnswer} />
              </div>
            </div>
          )}
        </div>
      );
    case "CLOZE":
      return null;
    case "SHORT_ANSWER":
      return (
        <div className="flex flex-col gap-2">
          <ol className="m-0 flex list-none flex-col gap-2 p-0">
            {body.answers.map((answer, index) => (
              <li key={index} className="flex flex-wrap items-center gap-x-3 gap-y-1 rounded-lg border border-success bg-success-bg px-3.5 py-2.5">
                <span className="text-caption font-semibold text-text-muted">
                  {index === 0 ? t("standardAnswer") : t("acceptedAnswer")}
                </span>
                <span className="min-w-0 flex-1 wrap-break-word text-text">{answer}</span>
              </li>
            ))}
          </ol>
          {body.inputHint && (
            <p className="text-caption text-text-secondary">
              <span className="font-semibold">{t("inputHint")}</span> {body.inputHint}
            </p>
          )}
        </div>
      );
    case "MATCHING": {
      const contentOf = new Map(body.right.map((item) => [item.id, questionHtmlText(item.content)]));
      const pairOf = new Map(body.pairs.map((pair) => [pair.leftId, pair.rightId]));
      const used = new Set(body.pairs.map((pair) => pair.rightId));
      const distractors = body.right.filter((item) => !used.has(item.id));
      return (
        <div className="flex flex-col gap-2">
          <ol className="m-0 flex list-none flex-col gap-2 p-0">
            {body.left.map((item, index) => (
              <li key={item.id} className="flex flex-wrap items-center gap-x-3 gap-y-1 rounded-lg border border-border px-3.5 py-2.5">
                <span className="text-caption font-semibold text-text-muted">{t("zoneLabel", { number: index + 1 })}</span>
                <RichTextView className="min-w-0 flex-1 wrap-break-word text-text" html={item.content} />
                <span className="inline-flex items-center gap-1.5 rounded-md border border-success bg-success-bg px-2 py-0.5 font-semibold text-success">
                  <CircleCheck aria-hidden className="size-4" />
                  <span className="sr-only">{t("correctItem")}</span>
                  {contentOf.get(pairOf.get(item.id) ?? "") ?? t("noAnswer")}
                </span>
              </li>
            ))}
          </ol>
          <p className="text-caption text-text-secondary">
            {distractors.length
              ? t("distractors", { items: distractors.map((item) => questionHtmlText(item.content)).join(", ") })
              : t("noDistractors")}
          </p>
        </div>
      );
    }
    case "QUESTION_SET":
      return null;
  }
}

/** Bảng điểm Đúng/Sai theo số ý đúng, dạng danh sách mô tả (đọc được bằng trình đọc màn hình). */
function ScoreTableView({ table }: { table: readonly number[] | undefined }) {
  const t = useTranslations("questions.detail.body");
  const format = useFormatter();
  if (!table?.length) return null;
  return (
    <div className="flex flex-col gap-1.5">
      <p className="text-label font-semibold text-text">{t("scoreTableTitle")}</p>
      <dl className="m-0 grid grid-cols-2 gap-2 @md:grid-cols-4">
        {table.map((value, index) => (
          <div key={index} className="flex flex-col rounded-lg border border-border px-3 py-2">
            <dt className="text-caption text-text-muted">{t("scoreTableCell", { count: index + 1 })}</dt>
            <dd className="m-0 font-semibold text-text tabular-nums">{format.number(value)}</dd>
          </div>
        ))}
      </dl>
    </div>
  );
}

/** Mô tả ngắn phần đáp án dạng chữ (so sánh phiên bản). */
export function describeBody(body: QuestionBody, t: (key: string, values?: Record<string, string | number>) => string): string[] {
  switch (body.type) {
    case "SINGLE_CHOICE":
    case "MULTIPLE_CHOICE":
      return [
        ...body.options.map(
          (option, index) => `${optionLetter(index)}. ${questionHtmlText(option.content)}${option.correct ? ` (${t("correct")})` : ""}`,
        ),
        ...(body.type === "MULTIPLE_CHOICE" ? [`${t("scoringRule")} ${t(`scoringRules.${body.scoringRule}`)}`] : []),
      ];
    case "TRUE_FALSE":
      return [
        ...body.statements.map(
          (statement, index) => `${statementLetter(index)}) ${questionHtmlText(statement.content)}: ${statement.answer ? t("true") : t("false")}`,
        ),
        ...(body.scoringMethod === "COUNT_TABLE" && body.scoreTable.length
          ? [t("scoreTableLine", { values: body.scoreTable.map((value) => t("scoreValue", { value })).join(" / ") })]
          : []),
      ];
    case "ESSAY":
      return [
        `${t("gradingGuideTitle")}: ${questionHtmlText(body.gradingGuide) || t("noGradingGuide")}`,
        ...body.rubric.map(
          (criterion, index) => `${index + 1}. ${questionHtmlText(criterion.description)} (${t("points", { points: criterion.points })})`,
        ),
        ...(body.sampleAnswer ? [`${t("sampleAnswer")}: ${questionHtmlText(body.sampleAnswer)}`] : []),
        ...(body.wordLimit !== null ? [t("wordLimit", { count: body.wordLimit })] : []),
      ];
    case "SHORT_ANSWER":
      return [
        body.answers.join(" / ") || t("noAnswer"),
        ...(body.inputHint ? [`${t("inputHint")} ${body.inputHint}`] : []),
      ];
    case "CLOZE":
      return body.blanks.map(
        (blank, index) => `${t("blankLabel", { number: index + 1 })}: ${blank.answers.join(" / ") || t("noAnswer")}`,
      );
    case "MATCHING": {
      const contentOf = new Map(body.right.map((item) => [item.id, questionHtmlText(item.content)]));
      const pairOf = new Map(body.pairs.map((pair) => [pair.leftId, pair.rightId]));
      const used = new Set(body.pairs.map((pair) => pair.rightId));
      const distractors = body.right.filter((item) => !used.has(item.id));
      return [
        ...body.left.map(
          (item, index) =>
            `${t("zoneLabel", { number: index + 1 })} ${questionHtmlText(item.content)} → ${contentOf.get(pairOf.get(item.id) ?? "") ?? t("noAnswer")}`,
        ),
        ...(distractors.length ? [t("distractors", { items: distractors.map((item) => questionHtmlText(item.content)).join(", ") })] : []),
      ];
    }
    case "QUESTION_SET":
      return [questionHtmlText(body.passage)];
  }
}
