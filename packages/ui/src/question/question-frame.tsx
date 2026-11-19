"use client";

import { Flag } from "lucide-react";
import type { ReactNode } from "react";
import { IconButton } from "../components/icon-button";
import { useUiLocale, useUiStrings } from "../components/ui-strings";
import { cn } from "../lib/cn";
import { focusRing } from "../lib/styles";
import { RichTextView } from "../rich-text";
import { formatScore } from "./helpers";
import type { KitQuestion, QuestionOutcome, QuestionType } from "./types";

/**
 * Khung một câu hỏi: tiêu đề "Câu n", chip dạng câu, điểm/chip kết quả khi review, đề bài
 * (`RichTextView`), rồi phần điều khiển của từng dạng. Gốc luôn gắn `data-exam-content` trên vùng
 * nội dung đề (tiêu đề/chip nhỏ là chrome nên không đánh dấu) — DESIGN.md mục 8, LT 8.
 */
export function QuestionFrame({
  question,
  children,
  headingLevel = 2,
  flagged,
  onToggleFlag,
  reviewOutcome,
  reviewPoints,
  manualTag = false,
  className,
}: {
  question: KitQuestion;
  children: ReactNode;

  headingLevel?: 1 | 2 | 3;
  flagged?: boolean;
  onToggleFlag?: () => void;
  /** Chế độ review: chip kết quả của câu. */
  reviewOutcome?: QuestionOutcome | null;
  /** Chế độ review: "earned / max" (đã format); `null` khi chưa có điểm. */
  reviewPoints?: ReactNode;
  /** Chip "Chấm tay" cho câu Tự luận. */
  manualTag?: boolean;
  className?: string;
}) {
  const strings = useUiStrings().question;
  const HeadingTag = `h${headingLevel}` as "h1" | "h2" | "h3";
  const type = question.body.type;
  const flagLabel = flagged ? strings.flagRemove : strings.flagForReview;
  return (
    <section aria-labelledby={`${question.id}-title`} className={cn("flex min-w-0 flex-col gap-5", className)}>
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div className="flex min-w-0 flex-wrap items-center gap-2.5">
          <HeadingTag id={`${question.id}-title`} className="text-h3 font-bold text-text">
            {titleOf(question, strings.questionLabel, strings.questionRangeLabel)}
          </HeadingTag>
          <span className="inline-flex h-6 items-center rounded-full bg-success-bg px-2.5 text-caption font-medium whitespace-nowrap text-primary-text">
            {typeLabel(type, question, strings.typeLabels)}
          </span>
          {manualTag && (
            <span className="inline-flex h-6 items-center rounded-full border border-info/40 bg-info-bg px-2.5 text-caption font-semibold whitespace-nowrap text-info">
              {strings.manualGrading}
            </span>
          )}
          {reviewOutcome && <OutcomeChip outcome={reviewOutcome} />}
        </div>
        <div className="flex items-center gap-3">
          {reviewPoints != null && (
            <span className="text-title font-bold text-text tabular-nums">{reviewPoints}</span>
          )}
          {onToggleFlag && (
            <IconButton
              aria-label={flagLabel}
              aria-pressed={flagged ?? false}
              onClick={onToggleFlag}
              icon={<Flag aria-hidden className={flagged ? "fill-warning text-warning" : undefined} />}
            />
          )}
        </div>
      </div>
      {question.stem != null && question.stem !== "" && (
        <div data-exam-content className="text-title wrap-break-word text-text">
          <RichTextView html={question.stem} />
        </div>
      )}
      {children}
    </section>
  );
}

function titleOf(
  question: KitQuestion,
  one: (no: number) => string,
  range: (from: number, to: number) => string,
): string {
  if (question.body.type === "CLOZE" && question.body.blanks.length > 1) {
    return range(question.displayNo, question.displayNo + question.body.blanks.length - 1);
  }
  return one(question.displayNo);
}

function typeLabel(type: QuestionType, question: KitQuestion, labels: Record<QuestionType, string>): string {
  if (type === "CLOZE" && question.body.type === "CLOZE") {
    return `${labels.CLOZE} · ${question.body.blanks.length}`;
  }
  return labels[type];
}

const OUTCOME_STYLES: Record<QuestionOutcome, { chip: string; icon: string }> = {
  CORRECT: { chip: "border border-success bg-success text-surface", icon: "text-surface" },
  PARTIAL: { chip: "border border-warning bg-warning-bg text-warning", icon: "text-warning" },
  WRONG: { chip: "border border-dashed border-danger bg-danger-bg text-danger", icon: "text-danger" },
  BLANK: { chip: "border border-border-strong bg-subtle text-text-secondary", icon: "text-text-secondary" },
  PENDING_REVIEW: { chip: "border border-info bg-info-bg text-info", icon: "text-info" },
};

/** Chip kết quả của một câu/ý: chữ và icon, màu không đứng một mình (DESIGN.md 8). */
export function OutcomeChip({ outcome, className }: { outcome: QuestionOutcome; className?: string }) {
  const strings = useUiStrings().question;
  const styles = OUTCOME_STYLES[outcome];
  return (
    <span
      className={cn(
        "inline-flex h-6.5 items-center gap-1.5 rounded-full px-2.5 text-caption font-semibold whitespace-nowrap",
        styles.chip,
        className,
      )}
    >
      <OutcomeGlyph outcome={outcome} className={styles.icon} />
      {strings.outcomes[outcome]}
    </span>
  );
}

/** Icon nhỏ của kết quả (tick, nửa vòng, x, gạch ngang, đồng hồ) vẽ inline để dùng ngoài chip. */
export function OutcomeGlyph({ outcome, className }: { outcome: QuestionOutcome; className?: string }) {
  const common = {
    "aria-hidden": true as const,
    className: cn("size-3.5 shrink-0", className),
    viewBox: "0 0 24 24",
    fill: "none",
    stroke: "currentColor",
    strokeLinecap: "round" as const,
    strokeLinejoin: "round" as const,
  };
  switch (outcome) {
    case "CORRECT":
      return (
        <svg {...common} strokeWidth={3}>
          <path d="M20 6 9 17l-5-5" />
        </svg>
      );
    case "WRONG":
      return (
        <svg {...common} strokeWidth={3}>
          <path d="M18 6 6 18M6 6l12 12" />
        </svg>
      );
    case "PARTIAL":
      return (
        <svg {...common} strokeWidth={2.4}>
          <circle cx="12" cy="12" r="9" />
          <path d="M12 3a9 9 0 0 1 0 18z" fill="currentColor" />
        </svg>
      );
    case "PENDING_REVIEW":
      return (
        <svg {...common} strokeWidth={2.4}>
          <path d="M12 22a10 10 0 1 0 0-20 10 10 0 0 0 0 20zM12 6v6l4 2" />
        </svg>
      );
    case "BLANK":
      return (
        <svg {...common} strokeWidth={3}>
          <path d="M6 12h12" />
        </svg>
      );
  }
}

/** "earned / max điểm" của câu ở review; điểm chưa có thì hiện "Chưa có điểm · tối đa m". */
export function ReviewPoints({ earned, max }: { earned: number | null | undefined; max: number }) {
  const strings = useUiStrings().question;
  const locale = useUiLocale();
  if (earned === null || earned === undefined) {
    return (
      <span className="text-title font-semibold text-text-secondary">
        {strings.noPointsYet}{" "}
        <span className="font-normal text-text-muted">· {strings.maxPoints(formatScore(max, locale))}</span>
      </span>
    );
  }
  return (
    <>
      {formatScore(earned, locale)}{" "}
      <span className="font-medium text-text-muted">/ {formatScore(max, locale)} {strings.pointsSuffix}</span>
    </>
  );
}

/** Nút phụ kiểu khung (Câu trước/Câu sau trong bản vẽ do màn làm bài đặt; ở đây chỉ có bật/tắt). */
export function toggleButtonClass(active: boolean): string {
  return cn(
    "inline-flex h-11 items-center gap-2 rounded-xl border px-3.5 text-label font-semibold transition-colors duration-150 motion-reduce:transition-none",
    active ? "border-warning bg-warning-bg text-warning" : "border-border bg-surface text-text-secondary hover:border-border-strong",
    focusRing,
  );
}
