"use client";

import { Button, Card, cn, focusRing, FormField, Input, Textarea } from "@thi360/ui";
import { ChevronLeft, ChevronRight, Circle, CircleAlert, CircleCheck, CircleDashed } from "lucide-react";
import { useFormatter, useTranslations } from "next-intl";
import { useId, type Ref } from "react";
import { useFormContext, useFormState, useWatch } from "react-hook-form";
import { essayInputTotal, essayProgress, type EssayProgress, type GradingFormValues } from "../grading-form";
import { COMMENT_MAX, countWords, sumScores } from "../scoring";
import type { GradingEssay } from "../types";

/** "Đọc hiểu · Câu 1"; dùng cho thanh chọn câu, tiêu đề câu và nhãn của nút. */
export function useEssayLabel() {
  const t = useTranslations("grading.detail");
  return (essay: GradingEssay) => t("essayLabel", { part: essay.partName, number: essay.number });
}

type NavigatorState = EssayProgress | "error";

const NAVIGATOR_ICON: Record<NavigatorState, typeof Circle> = {
  complete: CircleCheck,
  partial: CircleDashed,
  empty: Circle,
  error: CircleAlert,
};

/**
 * Thanh chọn câu tự luận của lượt thi: mỗi câu một nút nêu điểm đang có và tình trạng (đủ điểm, đang chấm dở, chưa
 * chấm, có lỗi) bằng hình icon và chữ cho trình đọc màn hình, không chỉ bằng màu. Câu đang mở có `aria-current` và gạch
 * chân. Bấm nút thì focus ở lại nút (như tab), phần dưới đổi sang câu đó.
 */
export interface EssayNavigatorItem {
  essay: GradingEssay;
  /** Điểm đang có của từng tiêu chí (chuỗi như ô nhập). */
  scores: readonly string[] | undefined;
  hasError: boolean;
}

export function EssayNavigator({
  items,
  activeIndex,
  onSelect,
}: {
  items: readonly EssayNavigatorItem[];
  activeIndex: number;
  onSelect: (index: number) => void;
}) {
  const t = useTranslations("grading.detail.navigator");
  const format = useFormatter();
  const label = useEssayLabel();

  return (
    <nav aria-label={t("label")}>
      <ol className="flex flex-wrap gap-2">
        {items.map(({ essay, scores, hasError }, index) => {
          const state: NavigatorState = hasError ? "error" : essayProgress(essay, scores);
          const Icon = NAVIGATOR_ICON[state];
          const current = index === activeIndex;
          return (
            <li key={essay.questionId}>
              <button
                type="button"
                aria-current={current ? "true" : undefined}
                onClick={() => onSelect(index)}
                className={cn(
                  "relative flex min-h-11 flex-col items-start gap-0.5 rounded-lg border px-3 py-1.5 text-start",
                  "transition-colors duration-150 motion-reduce:transition-none",
                  current
                    ? "border-primary-text bg-primary-50 text-primary-text after:absolute after:inset-x-3 after:bottom-0.5 after:border-b-2 after:border-current"
                    : "border-border bg-surface text-text hover:bg-subtle",
                  focusRing,
                )}
              >
                <span className="text-label font-medium whitespace-nowrap">{label(essay)}</span>
                <span
                  className={cn(
                    "flex items-center gap-1 text-caption whitespace-nowrap tabular-nums",
                    state === "error" ? "text-danger" : current ? "text-primary-text" : "text-text-muted",
                  )}
                >
                  <Icon aria-hidden className="size-3.5 shrink-0" />
                  {t("score", {
                    score: format.number(essayInputTotal(scores)),
                    max: format.number(essay.maxPoints),
                  })}
                  <span className="sr-only">, {t(`state.${state}`)}</span>
                </span>
              </button>
            </li>
          );
        })}
      </ol>
    </nav>
  );
}

/**
 * Cột trái: đoạn đọc hiểu (câu con của câu chùm), đề bài, bài viết của học viên (giữ xuống dòng) kèm số chữ, và đáp án
 * gợi ý thu gọn để người chấm tự mở khi cần.
 */
export function EssayQuestionPanel({
  essay,
  headingRef,
}: {
  essay: GradingEssay;
  headingRef?: Ref<HTMLHeadingElement>;
}) {
  const t = useTranslations("grading.detail.question");
  const format = useFormatter();
  const label = useEssayLabel();
  const headingId = useId();
  const words = countWords(essay.answer);

  return (
    <section aria-labelledby={headingId} className="min-w-0">
      <Card padding="none" className="flex flex-col gap-5 p-4 sm:p-5">
        <header className="flex flex-col gap-1">
          <h2 id={headingId} ref={headingRef} tabIndex={-1} className={cn("w-fit rounded-sm text-h3 font-bold text-text", focusRing)}>
            {label(essay)}
          </h2>
          <p className="text-caption text-text-muted">
            {t("meta", {
              code: essay.questionCode,
              points: format.number(essay.maxPoints),
            })}
            {" · "}
            {t(`source.${essay.source}`)}
          </p>
        </header>

        {essay.passage && (
          <details open className="group rounded-lg border border-border bg-subtle">
            <summary className={cn("cursor-pointer rounded-lg px-4 py-2.5 text-label font-semibold text-text", focusRing)}>
              {t("passage", { code: essay.passage.code })}
            </summary>
            <p className="px-4 pb-4 text-body whitespace-pre-line text-text">{essay.passage.passage}</p>
          </details>
        )}

        <div className="flex flex-col gap-1.5">
          <h3 className="text-label font-semibold text-text-secondary">{t("prompt")}</h3>
          <p className="text-body whitespace-pre-line text-text">{essay.content}</p>
        </div>

        <div className="flex flex-col gap-1.5">
          <div className="flex flex-wrap items-baseline justify-between gap-x-3 gap-y-1">
            <h3 className="text-label font-semibold text-text-secondary">{t("answer")}</h3>
            <p className="text-caption text-text-muted tabular-nums">
              {essay.wordLimit
                ? t("wordCountWithLimit", { count: words, limit: essay.wordLimit })
                : t("wordCount", { count: words })}
            </p>
          </div>
          {essay.answer.trim() ? (
            <div className="rounded-lg border border-border bg-surface px-4 py-3 text-body leading-relaxed whitespace-pre-wrap text-text wrap-anywhere">
              {essay.answer}
            </div>
          ) : (
            <p className="rounded-lg border border-dashed border-border px-4 py-3 text-body text-text-muted">
              {t("noAnswer")}
            </p>
          )}
        </div>

        {essay.sampleAnswer && (
          <details className="rounded-lg border border-border">
            <summary className={cn("cursor-pointer rounded-lg px-4 py-2.5 text-label font-semibold text-text", focusRing)}>
              {t("sampleAnswer")}
            </summary>
            <p className="px-4 pb-4 text-body whitespace-pre-line text-text">{essay.sampleAnswer}</p>
          </details>
        )}
      </Card>
    </section>
  );
}

/**
 * Cột phải khi đang chấm: mỗi tiêu chí một ô điểm (nhãn là nội dung tiêu chí, gợi ý nêu điểm tối đa), điểm câu tính
 * ngay, ô nhận xét và nút sang câu trước/sau. Lỗi gắn với từng ô (`aria-invalid`, `aria-describedby`).
 */
export function EssayRubricForm({
  essay,
  essayIndex,
  onPrevious,
  onNext,
}: {
  essay: GradingEssay;
  essayIndex: number;
  onPrevious: (() => void) | null;
  onNext: (() => void) | null;
}) {
  const t = useTranslations("grading.detail.rubric");
  const validationT = useTranslations("grading.detail.validation");
  const format = useFormatter();
  const label = useEssayLabel();
  const headingId = useId();
  const { control, register } = useFormContext<GradingFormValues>();
  const scores = useWatch({ control, name: `essays.${essayIndex}.scores` });
  const { errors } = useFormState({ control, name: `essays.${essayIndex}` });
  const essayErrors = errors.essays?.[essayIndex];
  const message = (key: string | undefined, max?: number) =>
    key ? validationT(key, { max: format.number(max ?? 0), limit: format.number(COMMENT_MAX) }) : undefined;

  return (
    <section aria-labelledby={headingId} className="min-w-0">
      <Card padding="none" className="flex flex-col gap-4 p-4 sm:p-5">
        <header className="flex flex-wrap items-baseline justify-between gap-x-3 gap-y-1">
          <h2 id={headingId} className="text-h3 font-bold text-text">
            {t("title")}
          </h2>
          <p className="text-caption text-text-muted">{t("max", { max: format.number(essay.maxPoints) })}</p>
        </header>

        <fieldset className="m-0 flex min-w-0 flex-col gap-4 border-0 p-0">
          <legend className="sr-only">{t("legend", { label: label(essay) })}</legend>
          {essay.rubric.map((criterion, criterionIndex) => (
            <FormField
              key={criterion.id}
              label={t("criterion", { index: criterionIndex + 1, description: criterion.description })}
              hint={t("criterionHint", { max: format.number(criterion.points) })}
              error={message(essayErrors?.scores?.[criterionIndex]?.message, criterion.points)}
            >
              {(controlProps) => (
                <div className="flex items-center gap-2">
                  <Input
                    {...controlProps}
                    {...register(`essays.${essayIndex}.scores.${criterionIndex}`)}
                    inputMode="decimal"
                    autoComplete="off"
                    enterKeyHint="next"
                    className="w-28"
                  />
                  <span aria-hidden className="text-body text-text-muted tabular-nums">
                    / {format.number(criterion.points)}
                  </span>
                </div>
              )}
            </FormField>
          ))}
        </fieldset>

        <p className="flex flex-wrap items-baseline justify-between gap-2 rounded-lg bg-subtle px-3 py-2 text-body text-text">
          <span>{t("subtotal")}</span>
          <strong className="font-semibold tabular-nums">
            {t("subtotalValue", {
              score: format.number(essayInputTotal(scores)),
              max: format.number(essay.maxPoints),
            })}
          </strong>
        </p>

        <FormField label={t("comment")} hint={t("commentHint")} error={message(essayErrors?.comment?.message)}>
          {(controlProps) => (
            <Textarea {...controlProps} {...register(`essays.${essayIndex}.comment`)} rows={3} maxLength={COMMENT_MAX} />
          )}
        </FormField>

        <EssayStepper onPrevious={onPrevious} onNext={onNext} />
      </Card>
    </section>
  );
}

/** Cột phải khi lượt đã có điểm: điểm từng tiêu chí và nhận xét, chỉ để đọc. */
export function EssayRubricResult({
  essay,
  onPrevious,
  onNext,
}: {
  essay: GradingEssay;
  onPrevious: (() => void) | null;
  onNext: (() => void) | null;
}) {
  const t = useTranslations("grading.detail.rubric");
  const format = useFormatter();
  const headingId = useId();
  const total = sumScores(essay.scores.map((item) => item.score));

  return (
    <section aria-labelledby={headingId} className="min-w-0">
      <Card padding="none" className="flex flex-col gap-4 p-4 sm:p-5">
        <header className="flex flex-wrap items-baseline justify-between gap-x-3 gap-y-1">
          <h2 id={headingId} className="text-h3 font-bold text-text">
            {t("title")}
          </h2>
          <p className="text-caption text-text-muted">{t("max", { max: format.number(essay.maxPoints) })}</p>
        </header>
        <dl className="m-0 flex flex-col divide-y divide-border">
          {essay.rubric.map((criterion, index) => (
            <div key={criterion.id} className="flex items-start justify-between gap-3 py-2.5 first:pt-0">
              <dt className="text-body text-text">
                {t("criterion", { index: index + 1, description: criterion.description })}
              </dt>
              <dd className="m-0 shrink-0 font-semibold whitespace-nowrap text-text tabular-nums">
                {t("subtotalValue", {
                  score: format.number(essay.scores[index]?.score ?? 0),
                  max: format.number(criterion.points),
                })}
              </dd>
            </div>
          ))}
        </dl>
        <p className="flex flex-wrap items-baseline justify-between gap-2 rounded-lg bg-subtle px-3 py-2 text-body text-text">
          <span>{t("subtotal")}</span>
          <strong className="font-semibold tabular-nums">
            {t("subtotalValue", { score: format.number(total), max: format.number(essay.maxPoints) })}
          </strong>
        </p>
        <div className="flex flex-col gap-1">
          <h3 className="text-label font-semibold text-text-secondary">{t("comment")}</h3>
          <p className={cn("text-body whitespace-pre-line", essay.comment ? "text-text" : "text-text-muted")}>
            {essay.comment || t("noComment")}
          </p>
        </div>
        <EssayStepper onPrevious={onPrevious} onNext={onNext} />
      </Card>
    </section>
  );
}

function EssayStepper({ onPrevious, onNext }: { onPrevious: (() => void) | null; onNext: (() => void) | null }) {
  const t = useTranslations("grading.detail.rubric");
  if (!onPrevious && !onNext) return null;
  return (
    <div className="flex flex-wrap justify-between gap-2 border-t border-border pt-4">
      {onPrevious ? (
        <Button type="button" variant="secondary" size="sm" onClick={onPrevious}>
          <ChevronLeft />
          <span>{t("previous")}</span>
        </Button>
      ) : (
        <span />
      )}
      {onNext && (
        <Button type="button" variant="secondary" size="sm" onClick={onNext}>
          <span>{t("next")}</span>
          <ChevronRight />
        </Button>
      )}
    </div>
  );
}
