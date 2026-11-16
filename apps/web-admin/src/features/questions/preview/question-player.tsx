"use client";

import { Checkbox, cn, Input, Select, Textarea } from "@thi360/ui";
import { CircleCheck, CircleMinus, CircleX, Paperclip } from "lucide-react";
import { useTranslations } from "next-intl";
import { Fragment, useId } from "react";
import { optionLetter, statementLetter } from "../components/question-editor-shared";
import type { QuestionDetail } from "../types";
import { countWords, emptyAnswer, type PartResult, type PartState, type PreviewAnswer, type QuestionResult } from "./grading";
import { roundScore, useScoreFormat } from "./score-format";

/**
 * Bài làm thử của một câu theo giao diện học viên (UniNote PBI-03 mục 8.5): đề bài, ngữ liệu, thứ tự câu con và
 * thành phần trả lời của từng dạng. Có `result` thì mỗi ý hiện đúng, sai hoặc bỏ trống kèm điểm (chữ và icon, không
 * chỉ màu). Thứ tự phương án giữ như lúc soạn: đảo thứ tự chỉ có ở lượt thi.
 */

const BLANK_PICK = "__blank__";

export interface QuestionPlayerProps {
  question: QuestionDetail;
  answer: PreviewAnswer;
  onChange: (answer: PreviewAnswer) => void;
  /** `null`: chưa chấm thử, không hiện đáp án đúng. */
  result: QuestionResult | null;
  headingLevel?: 3 | 4;
}

export function QuestionPlayer({ question, answer, onChange, result, headingLevel = 3 }: QuestionPlayerProps) {
  const t = useTranslations("questions.preview");
  const body = question.body;
  const passage = body.type === "QUESTION_SET" ? body.passage || question.content : question.content;
  return (
    <div className="flex flex-col gap-4">
      {body.type !== "CLOZE" && (
        <p
          className={cn(
            "text-body wrap-break-word whitespace-pre-wrap text-text",
            body.type === "QUESTION_SET" && "rounded-lg border border-border bg-subtle p-3.5",
          )}
        >
          {passage || t("noContent")}
        </p>
      )}
      {question.attachmentIds.length > 0 && (
        <p className="inline-flex items-center gap-1.5 text-caption text-text-secondary">
          <Paperclip aria-hidden className="size-4" />
          {t("attachments", { count: question.attachmentIds.length })}
        </p>
      )}
      <Controls question={question} answer={answer} onChange={onChange} result={result} headingLevel={headingLevel} />
    </div>
  );
}

function Controls({ question, answer, onChange, result, headingLevel }: QuestionPlayerProps) {
  const body = question.body;
  switch (body.type) {
    case "SINGLE_CHOICE":
      return (
        <ChoiceList
          question={question}
          selected={answer.type === "SINGLE_CHOICE" && answer.selected ? [answer.selected] : []}
          multiple={false}
          onToggle={(id) => onChange({ type: "SINGLE_CHOICE", selected: id })}
          result={result}
        />
      );
    case "MULTIPLE_CHOICE": {
      const selected = answer.type === "MULTIPLE_CHOICE" ? answer.selected : [];
      return (
        <ChoiceList
          question={question}
          selected={selected}
          multiple
          onToggle={(id) =>
            onChange({
              type: "MULTIPLE_CHOICE",
              selected: selected.includes(id) ? selected.filter((item) => item !== id) : [...selected, id],
            })
          }
          result={result}
        />
      );
    }
    case "SHORT_ANSWER":
      return (
        <ShortAnswerInput
          hint={body.inputHint}
          value={answer.type === "SHORT_ANSWER" ? answer.text : ""}
          onChange={(text) => onChange({ type: "SHORT_ANSWER", text })}
          result={result}
          standard={body.answers[0] ?? null}
        />
      );
    case "CLOZE":
      return (
        <ClozeInputs
          body={body}
          values={answer.type === "CLOZE" ? answer.values : {}}
          onChange={(values) => onChange({ type: "CLOZE", values })}
          result={result}
        />
      );
    case "TRUE_FALSE":
      return (
        <TrueFalseInputs
          body={body}
          values={answer.type === "TRUE_FALSE" ? answer.values : {}}
          onChange={(values) => onChange({ type: "TRUE_FALSE", values })}
          result={result}
        />
      );
    case "MATCHING":
      return (
        <MatchingInputs
          body={body}
          picks={answer.type === "MATCHING" ? answer.picks : {}}
          onChange={(picks) => onChange({ type: "MATCHING", picks })}
          result={result}
        />
      );
    case "QUESTION_SET":
      return (
        <QuestionSetPlayer
          question={question}
          answers={answer.type === "QUESTION_SET" ? answer.children : {}}
          onChange={(children) => onChange({ type: "QUESTION_SET", children })}
          result={result}
          headingLevel={headingLevel ?? 3}
        />
      );
    case "ESSAY":
      return (
        <EssayInput
          body={body}
          text={answer.type === "ESSAY" ? answer.text : ""}
          onChange={(text) => onChange({ type: "ESSAY", text })}
          result={result}
        />
      );
  }
}

/** Nhãn kết quả của một ý: chữ và icon, kèm điểm của ý khi có. */
export function ResultMark({ part, state, className }: { part?: PartResult; state: PartState; className?: string }) {
  const t = useTranslations("questions.preview.result");
  const scoreText = useScoreFormat();
  const Icon = state === "correct" ? CircleCheck : state === "wrong" ? CircleX : CircleMinus;
  const tone = state === "correct" ? "text-success" : state === "wrong" ? "text-danger" : "text-text-muted";
  return (
    <span className={cn("inline-flex items-center gap-1 text-caption font-semibold", tone, className)}>
      <Icon aria-hidden className="size-4" />
      {t(state)}
      {part?.earned != null && part.max != null && (
        <span className="font-normal text-text-secondary">
          {t("partPoints", { earned: scoreText(part.earned), max: roundScore(part.max) })}
        </span>
      )}
    </span>
  );
}

function ChoiceList({
  question,
  selected,
  multiple,
  onToggle,
  result,
}: {
  question: QuestionDetail;
  selected: string[];
  multiple: boolean;
  onToggle: (id: string) => void;
  result: QuestionResult | null;
}) {
  const t = useTranslations("questions.preview");
  const name = useId();
  const body = question.body;
  if (body.type !== "SINGLE_CHOICE" && body.type !== "MULTIPLE_CHOICE") return null;
  return (
    <fieldset className="m-0 flex min-w-0 flex-col gap-2 border-0 p-0">
      <legend className="mb-1 text-caption font-medium text-text-secondary">
        {multiple ? t("hintMultiple") : t("hintSingle")}
      </legend>
      {body.options.map((option, index) => {
        const checked = selected.includes(option.id);
        const id = `${name}-${option.id}`;
        const graded = result !== null;
        return (
          <label
            key={option.id}
            htmlFor={id}
            className={cn(
              "flex cursor-pointer flex-wrap items-center gap-x-3 gap-y-1 rounded-lg border px-3.5 py-2.5 text-body text-text",
              graded && option.correct ? "border-success bg-success-bg" : graded && checked ? "border-danger bg-danger-bg" : "border-border",
              !graded && checked && "border-primary-600 bg-primary-50",
            )}
          >
            {multiple ? (
              <Checkbox id={id} checked={checked} onCheckedChange={() => onToggle(option.id)} />
            ) : (
              <input
                id={id}
                type="radio"
                name={name}
                checked={checked}
                onChange={() => onToggle(option.id)}
                className="size-5 shrink-0 accent-primary-fill"
              />
            )}
            <span className="font-semibold">{optionLetter(index)}.</span>
            <span className="min-w-0 flex-1 wrap-break-word">{option.content}</span>
            {graded && option.correct && (
              <span className="inline-flex items-center gap-1 text-caption font-semibold text-success">
                <CircleCheck aria-hidden className="size-4" />
                {t("result.correctOption")}
              </span>
            )}
            {graded && checked && !option.correct && (
              <span className="inline-flex items-center gap-1 text-caption font-semibold text-danger">
                <CircleX aria-hidden className="size-4" />
                {t("result.wrongPick")}
              </span>
            )}
          </label>
        );
      })}
    </fieldset>
  );
}

function ShortAnswerInput({
  hint,
  value,
  onChange,
  result,
  standard,
}: {
  hint: string | null;
  value: string;
  onChange: (value: string) => void;
  result: QuestionResult | null;
  standard: string | null;
}) {
  const t = useTranslations("questions.preview");
  const id = useId();
  return (
    <div className="flex flex-col gap-2">
      <label htmlFor={id} className="text-label font-medium text-text">
        {t("shortAnswerLabel")}
      </label>
      <Input
        id={id}
        value={value}
        onChange={(event) => onChange(event.target.value)}
        placeholder={hint ?? undefined}
        aria-describedby={hint ? `${id}-hint` : undefined}
        autoComplete="off"
      />
      {hint && (
        <p id={`${id}-hint`} className="text-caption text-text-secondary">
          {hint}
        </p>
      )}
      {result && (
        <div className="flex flex-wrap items-center gap-x-3 gap-y-1">
          <ResultMark state={result.state === "blank" ? "blank" : result.state === "correct" ? "correct" : "wrong"} />
          {standard !== null && result.state !== "correct" && (
            <span className="text-caption text-text-secondary">{t("result.standardAnswer", { answer: standard })}</span>
          )}
        </div>
      )}
    </div>
  );
}

function partOf(result: QuestionResult | null, key: string): PartResult | undefined {
  return result?.parts.find((part) => part.key === key);
}

function ClozeInputs({
  body,
  values,
  onChange,
  result,
}: {
  body: Extract<QuestionDetail["body"], { type: "CLOZE" }>;
  values: Record<string, string>;
  onChange: (values: Record<string, string>) => void;
  result: QuestionResult | null;
}) {
  const t = useTranslations("questions.preview");
  const name = useId();
  const segments = body.passage.split(/\[\[([^\]]+)\]\]/);
  // split với nhóm bắt: phần tử lẻ là id ô trống.
  const blankOrder = body.blanks.map((blank) => blank.id);
  return (
    <div className="flex flex-col gap-3">
      <p className="text-body leading-10 wrap-break-word whitespace-pre-wrap text-text">
        {segments.map((segment, index) => {
          if (index % 2 === 0) return <Fragment key={index}>{segment}</Fragment>;
          // Nhập tệp và kiểm tra sẵn sàng đều cắt khoảng trắng quanh mã ô (`[[ b1 ]]` là ô b1).
          const blankId = segment.trim();
          const blank = body.blanks.find((item) => item.id === blankId);
          const number = blankOrder.indexOf(blankId) + 1;
          const part = partOf(result, blankId);
          return (
            <Input
              key={index}
              id={`${name}-${blankId}`}
              aria-label={t("blankLabel", { number })}
              placeholder={t("blankPlaceholder", { number })}
              value={values[blankId] ?? ""}
              onChange={(event) => onChange({ ...values, [blankId]: event.target.value })}
              disabled={blank === undefined}
              autoComplete="off"
              className={cn(
                "mx-1 inline-block h-9 w-32 align-middle",
                part?.state === "correct" && "border-success",
                part?.state === "wrong" && "border-danger",
              )}
            />
          );
        })}
      </p>
      {result && (
        <ul className="m-0 flex list-none flex-col gap-1.5 p-0">
          {body.blanks.map((blank, index) => {
            const part = partOf(result, blank.id);
            return (
              <li key={blank.id} className="flex flex-wrap items-center gap-x-3 gap-y-1 text-caption text-text-secondary">
                <span className="font-semibold text-text">{t("blankLabel", { number: index + 1 })}</span>
                {part && <ResultMark part={part} state={part.state} />}
                {part?.state !== "correct" && (
                  <span>{t("result.standardAnswer", { answer: blank.answers.join(" / ") })}</span>
                )}
              </li>
            );
          })}
        </ul>
      )}
    </div>
  );
}

function TrueFalseInputs({
  body,
  values,
  onChange,
  result,
}: {
  body: Extract<QuestionDetail["body"], { type: "TRUE_FALSE" }>;
  values: Record<string, boolean | null>;
  onChange: (values: Record<string, boolean | null>) => void;
  result: QuestionResult | null;
}) {
  const t = useTranslations("questions.preview");
  const name = useId();
  return (
    <ol className="m-0 flex list-none flex-col gap-2 p-0">
      {body.statements.map((statement, index) => {
        const part = partOf(result, statement.id);
        const value = values[statement.id] ?? null;
        return (
          <li key={statement.id} className="flex flex-col gap-2 rounded-lg border border-border px-3.5 py-2.5">
            <div className="flex gap-3">
              <span className="font-semibold text-text">{statementLetter(index)})</span>
              <span className="min-w-0 flex-1 wrap-break-word text-text">{statement.content}</span>
            </div>
            <fieldset className="m-0 flex flex-wrap items-center gap-x-5 gap-y-1 border-0 p-0">
              <legend className="sr-only">{t("statementLegend", { letter: statementLetter(index) })}</legend>
              {([true, false] as const).map((option) => (
                <label key={String(option)} className="flex min-h-8 cursor-pointer items-center gap-2 text-body text-text">
                  <input
                    type="radio"
                    name={`${name}-${statement.id}`}
                    checked={value === option}
                    onChange={() => onChange({ ...values, [statement.id]: option })}
                    className="size-5 accent-primary-fill"
                  />
                  {option ? t("true") : t("false")}
                </label>
              ))}
              {part && <ResultMark part={part} state={part.state} className="ms-auto" />}
            </fieldset>
            {part && part.state !== "correct" && (
              <p className="text-caption text-text-secondary">
                {t("result.statementKey", { answer: statement.answer ? t("true") : t("false") })}
              </p>
            )}
          </li>
        );
      })}
    </ol>
  );
}

function MatchingInputs({
  body,
  picks,
  onChange,
  result,
}: {
  body: Extract<QuestionDetail["body"], { type: "MATCHING" }>;
  picks: Record<string, string | null>;
  onChange: (picks: Record<string, string | null>) => void;
  result: QuestionResult | null;
}) {
  const t = useTranslations("questions.preview");
  const contentOf = new Map(body.right.map((item) => [item.id, item.content]));
  const taken = new Set(Object.values(picks).filter((id): id is string => id !== null));
  return (
    <div className="flex flex-col gap-2">
      <p className="text-caption text-text-secondary">{body.reuseRight ? t("matchReuse") : t("matchOnce")}</p>
      <ol className="m-0 flex list-none flex-col gap-2 p-0">
        {body.left.map((item, index) => {
          const part = partOf(result, item.id);
          const pick = picks[item.id] ?? null;
          const key = body.pairs.find((pair) => pair.leftId === item.id)?.rightId;
          const options = [
            { value: BLANK_PICK, label: t("matchPlaceholder") },
            ...body.right.map((right, rightIndex) => ({
              value: right.id,
              label: `${rightIndex + 1}. ${right.content}`,
              disabled: !body.reuseRight && taken.has(right.id) && pick !== right.id,
            })),
          ];
          return (
            <li key={item.id} className="flex flex-col gap-2 rounded-lg border border-border px-3.5 py-2.5">
              <div className="flex gap-3">
                <span className="text-caption font-semibold text-text-muted">{t("matchItem", { number: index + 1 })}</span>
                <span className="min-w-0 flex-1 wrap-break-word text-text">{item.content}</span>
              </div>
              <Select
                aria-label={t("matchSelectLabel", { number: index + 1 })}
                options={options}
                value={pick ?? BLANK_PICK}
                onValueChange={(value) => onChange({ ...picks, [item.id]: value === BLANK_PICK ? null : value })}
              />
              {part && (
                <div className="flex flex-wrap items-center gap-x-3 gap-y-1">
                  <ResultMark part={part} state={part.state} />
                  {part.state !== "correct" && key !== undefined && (
                    <span className="text-caption text-text-secondary">
                      {t("result.matchKey", { answer: contentOf.get(key) ?? "" })}
                    </span>
                  )}
                </div>
              )}
            </li>
          );
        })}
      </ol>
    </div>
  );
}

function QuestionSetPlayer({
  question,
  answers,
  onChange,
  result,
  headingLevel,
}: {
  question: QuestionDetail;
  answers: Record<string, PreviewAnswer>;
  onChange: (answers: Record<string, PreviewAnswer>) => void;
  result: QuestionResult | null;
  headingLevel: 3 | 4;
}) {
  const t = useTranslations("questions.preview");
  const tType = useTranslations("content.questionType");
  const Heading = headingLevel === 3 ? "h3" : "h4";
  return (
    <ol className="m-0 flex list-none flex-col gap-3 p-0">
      {question.children.map((child, index) => {
        const childResult = result?.children[child.id] ?? null;
        return (
          <li key={child.id} className="flex flex-col gap-3 rounded-lg border border-border p-3.5">
            <div className="flex flex-wrap items-center gap-2">
              <Heading className="text-label font-semibold text-text">{t("childTitle", { number: index + 1 })}</Heading>
              <span className="text-caption text-text-secondary">{tType(child.type)}</span>
              <span className="text-caption text-text-muted">{t("points", { points: child.points })}</span>
            </div>
            <QuestionPlayer
              question={child}
              answer={answers[child.id] ?? emptyAnswer(child)}
              onChange={(next) => onChange({ ...answers, [child.id]: next })}
              result={childResult}
              headingLevel={4}
            />
          </li>
        );
      })}
    </ol>
  );
}

function EssayInput({
  body,
  text,
  onChange,
  result,
}: {
  body: Extract<QuestionDetail["body"], { type: "ESSAY" }>;
  text: string;
  onChange: (text: string) => void;
  result: QuestionResult | null;
}) {
  const t = useTranslations("questions.preview");
  const scoreText = useScoreFormat();
  const id = useId();
  const words = countWords(text);
  return (
    <div className="flex flex-col gap-3">
      <label htmlFor={id} className="text-label font-medium text-text">
        {t("essayLabel")}
      </label>
      <Textarea id={id} rows={6} value={text} onChange={(event) => onChange(event.target.value)} />
      <p className="text-caption text-text-secondary">
        {body.wordLimit === null
          ? t("essayWords", { count: words })
          : t("essayWordsLimit", { count: words, limit: scoreText(body.wordLimit) })}
      </p>
      {result && <EssayCompare body={body} text={text} />}
    </div>
  );
}

/** Đối chiếu bài làm với hướng dẫn chấm, tiêu chí và bài mẫu (câu Tự luận chấm tay, UniNote 8.5). */
function EssayCompare({ body, text }: { body: Extract<QuestionDetail["body"], { type: "ESSAY" }>; text: string }) {
  const t = useTranslations("questions.preview.essay");
  const block = "rounded-lg border border-border bg-subtle p-3.5 text-body wrap-break-word whitespace-pre-wrap text-text";
  return (
    <section aria-label={t("title")} className="flex flex-col gap-3 @container">
      <h4 className="text-label font-semibold text-text">{t("title")}</h4>
      <div className="grid gap-3 @md:grid-cols-2">
        <div className="flex min-w-0 flex-col gap-1">
          <p className="text-caption font-semibold text-text-secondary">{t("yourAnswer")}</p>
          <p className={block}>{text.trim() === "" ? t("noAnswer") : text}</p>
        </div>
        <div className="flex min-w-0 flex-col gap-1">
          <p className="text-caption font-semibold text-text-secondary">{t("sampleAnswer")}</p>
          <p className={block}>{body.sampleAnswer ?? t("noSample")}</p>
        </div>
      </div>
      <div className="flex flex-col gap-1">
        <p className="text-caption font-semibold text-text-secondary">{t("guide")}</p>
        <p className={block}>{body.gradingGuide || t("noGuide")}</p>
      </div>
      {body.rubric.length > 0 && (
        <ol className="m-0 flex list-none flex-col gap-1.5 p-0">
          {body.rubric.map((criterion, index) => (
            <li key={criterion.id} className="flex flex-wrap items-baseline gap-x-3 text-body text-text">
              <span className="font-semibold">{index + 1}.</span>
              <span className="min-w-0 flex-1 wrap-break-word">{criterion.description}</span>
              <span className="text-caption font-semibold text-text-secondary">
                {t("criterionPoints", { points: criterion.points })}
              </span>
            </li>
          ))}
        </ol>
      )}
    </section>
  );
}
