"use client";

import { useState } from "react";
import { Textarea } from "../components/textarea";
import { useUiLocale, useUiStrings } from "../components/ui-strings";
import { cn } from "../lib/cn";
import { focusRing } from "../lib/styles";
import { RichTextView } from "../rich-text";
import { countWords, textForWordCount } from "./count-words";
import { formatScore, htmlToPlainText, plainTextToHtml } from "./helpers";
import { QuestionFrame, ReviewPoints } from "./question-frame";
import type { KitQuestionProps } from "./shared";
import { answersVisible, resolveReview } from "./shared";
import { coarseTarget, touchFieldText } from "./touch";
import type { EssayAnswer, EssayGrading, KitQuestion, KitQuestionOf, QuestionReview } from "./types";

type EssayBody = Extract<KitQuestion["body"], { type: "ESSAY" }>;

/** Prop mà một trình soạn thảo Tự luận (Tiptap của FS-5) nhận khi gắn vào `renderEditor`. */
export interface EssayEditorProps {
  id: string;
  value: string;
  onChange: (html: string) => void;
  placeholder: string;
  "aria-label": string;
  "aria-describedby"?: string;
  readOnly?: boolean;
  minHeightClass: string;
}

/**
 * Câu Tự luận: ô viết (mặc định `Textarea`; lắp trình soạn thảo qua `renderEditor` — F14),
 * đếm chữ theo BS 6 (vùng `role="status"` cập nhật mỗi lần gõ), cảnh báo `wordLimit` tối thiểu
 * khi chưa đủ và vẫn cho nộp.
 * Review: PENDING → hộp "bài viết đã ghi nhận, chờ chấm"; GRADED → điểm + bài viết (thu gọn/mở),
 * tiêu chí và nhận xét khi có; BLANK → "Bỏ trống"; REGRADING → điểm cũ + "Đang chấm lại".
 */
export function EssayQuestion({
  question,
  mode,
  value,
  onChange,
  flagged,
  onToggleFlag,
  review,
  headingLevel,
  className,
  renderEditor,
}: KitQuestionProps<KitQuestionOf<EssayBody>, EssayAnswer> & {
  /** Lắp trình soạn thảo (Tiptap của FS-5). Nhận HTML thô và gọi `onChange` với HTML mới. */
  renderEditor?: (props: EssayEditorProps) => React.ReactNode;
}) {
  const strings = useUiStrings().question;
  const locale = useUiLocale();
  const resolved = resolveReview(review);
  const body = question.body;
  const html = value?.html ?? "";
  const count = countWords(html);
  const min = body.wordLimit ?? null;
  const under = min !== null && count < min;
  const editorId = `${question.id}-editor`;
  const countId = `${question.id}-count`;
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
      manualTag={mode === "review"}
      className={className}
    >
      {mode === "answer" && (
        <div className="flex flex-col gap-2">
          <label htmlFor={editorId} className="text-label font-medium text-text">
            {strings.essayEditorLabel}
          </label>
          {renderEditor ? (
            renderEditor({
              id: editorId,
              value: html,
              onChange: (next) => onChange?.({ html: next, wordCount: countWords(next) }),
              placeholder: strings.essayPlaceholder,
              "aria-label": strings.essayEditorLabel,
              "aria-describedby": countId,
              minHeightClass: "min-h-60",
            })
          ) : (
            <Textarea
              id={editorId}
              rows={10}
              value={htmlToPlainText(html)}
              placeholder={strings.essayPlaceholder}
              aria-describedby={countId}
              onChange={(event) => {
                const next = plainTextToHtml(event.target.value);
                onChange?.({ html: next, wordCount: countWords(next) });
              }}
              className={cn("min-h-60", touchFieldText)}
            />
          )}
          <div className="flex flex-wrap items-center justify-between gap-2">
            <p id={countId} role="status" className="text-caption text-text-secondary tabular-nums">
              {strings.essayWordCount(count, min)}
            </p>
            {min !== null && (
              <p className={cn("text-caption font-medium", under ? "text-warning" : "text-success")}>
                {under ? strings.wordsNeeded(min - count) : strings.wordLimitMet}
              </p>
            )}
          </div>
          <p className="text-caption text-text-muted">{strings.essayNote}</p>
        </div>
      )}
      {mode === "review" && (
        <EssayReview question={question} value={value} review={resolved} locale={locale} />
      )}
    </QuestionFrame>
  );
}

function EssayReview({
  question,
  value,
  review,
  locale,
}: {
  question: KitQuestionOf<EssayBody>;
  value: EssayAnswer | null | undefined;
  review: QuestionReview | null;
  locale: "vi" | "en";
}) {
  const strings = useUiStrings().question;
  const [expanded, setExpanded] = useState(false);
  const html = value?.html ?? "";
  const grading: EssayGrading | undefined = review?.essay;
  const count = grading?.wordCount ?? (html ? countWords(html) : 0);
  const min = question.body.wordLimit ?? null;
  if (review?.outcome === "BLANK") {
    return (
      <p className="rounded-lg border border-border bg-subtle px-4 py-3 text-title text-text-secondary">
        {strings.notAnswered}
      </p>
    );
  }
  if (grading?.state === "PENDING" || (!grading && review?.outcome === "PENDING_REVIEW")) {
    return (
      <div className="flex flex-col gap-3">
        <p className="rounded-lg border border-info bg-info-bg px-4 py-3 text-title text-info">
          {strings.pendingReviewNote}
        </p>
        <EssayBody html={html} count={count} min={min} expanded={expanded} onToggle={() => setExpanded((v) => !v)} />
      </div>
    );
  }
  return (
    <div className="flex flex-col gap-3">
      {grading?.state === "REGRADING" && (
        <p className="rounded-lg border border-warning/60 bg-warning-bg px-4 py-2.5 text-label font-semibold text-warning">
          {strings.regrading}
        </p>
      )}
      <p className="text-caption text-text-secondary">{strings.essayWordCount(count, min)}</p>
      <div>
        <p className="mb-1.5 text-label font-semibold text-text">{strings.yourEssayLabel}</p>
        <EssayBody html={html} count={count} min={min} expanded={expanded} onToggle={() => setExpanded((v) => !v)} />
      </div>
      {grading?.criteria && grading.criteria.length > 0 && (
        <div className="overflow-x-auto">
          <p className="mb-1.5 text-label font-semibold text-text">{strings.criteriaLabel}</p>
          <table className="w-full border-collapse text-left">
            <thead>
              <tr className="border-b border-border">
                <th className="py-2 pr-4 text-caption font-semibold text-text-secondary">{strings.criterionCol}</th>
                <th className="py-2 text-caption font-semibold text-text-secondary">{strings.pointsCol}</th>
              </tr>
            </thead>
            <tbody>
              {grading.criteria.map((criterion, index) => (
                <tr key={index} className="border-b border-border/60 last:border-0">
                  <td className="py-2.5 pr-4 text-label text-text" data-exam-content>
                    {criterion.description}
                  </td>
                  <td className="py-2.5 text-label whitespace-nowrap text-text tabular-nums">
                    {criterion.earned != null
                      ? strings.points(formatScore(criterion.earned, locale), formatScore(criterion.max, locale))
                      : "—"}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
      {grading?.comment != null && grading.comment !== "" && (
        <div className="rounded-lg border border-border bg-subtle px-4 py-3">
          <p className="mb-1 text-caption font-semibold text-text-secondary">{strings.gradingCommentLabel}</p>
          <p className="text-title text-text" data-exam-content>
            {grading.comment}
          </p>
        </div>
      )}
      {answersVisible(review) && review?.solutionHtml && (
        <div className="rounded-lg border border-success/50 bg-success-bg px-4 py-3">
          <p className="mb-1 text-caption font-semibold text-primary-text">{strings.solutionLabel}</p>
          <div data-exam-content className="text-title text-text">
            <RichTextView html={review.solutionHtml} />
          </div>
        </div>
      )}
    </div>
  );
}

function EssayBody({
  html,
  count,
  min,
  expanded,
  onToggle,
}: {
  html: string;
  count: number;
  min: number | null;
  expanded: boolean;
  onToggle: () => void;
}) {
  const strings = useUiStrings().question;
  const plain = textForWordCount(html);
  const long = plain.length > 600;
  return (
    <div className="flex flex-col gap-1.5">
      <div
        data-exam-content
        className={cn("text-title text-text wrap-break-word", !expanded && long && "line-clamp-6")}
      >
        <RichTextView html={html} />
      </div>
      {long && (
        <button
          type="button"
          onClick={onToggle}
          className={cn(
            "inline-flex items-center self-start text-caption font-semibold text-primary-text hover:underline",
            coarseTarget,
            focusRing,
          )}
        >
          {expanded ? strings.hideFullEssay : strings.showFullEssay}
        </button>
      )}
      {min !== null && count < min && (
        <p className="text-caption text-warning">{strings.wordsNeeded(Math.max(0, min - count))}</p>
      )}
    </div>
  );
}
