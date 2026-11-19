"use client";

import { Check } from "lucide-react";
import { useId } from "react";
import { Checkbox } from "../components/checkbox";
import { useUiStrings } from "../components/ui-strings";
import { cn } from "../lib/cn";
import { focusRing } from "../lib/styles";
import { RichTextView } from "../rich-text";
import { optionLetter, orderById } from "./helpers";
import type { ChoiceOption, QuestionReview } from "./types";

/**
 * Danh sách phương án của câu Một đáp án / Nhiều đáp án (LT 8, canvas `CauHoi*Thi`):
 * chữ cái theo `optionOrder` đã nhận, đáp án lưu id không lưu chữ cái (F22).
 * `review`: hiện "Bạn chọn" / "Đáp án đúng" trên từng phương án, không sửa được.
 */
export function ChoiceList({
  options,
  optionOrder,
  selected,
  multiple,
  onChange,
  review,
  groupLabel,
}: {
  options: ChoiceOption[];
  optionOrder?: string[];
  selected: string[];
  multiple: boolean;
  onChange?: (ids: string[]) => void;
  review?: QuestionReview;
  /** Tên nhóm radio/checkbox (ví dụ "Câu 19"). */
  groupLabel: string;
}) {
  const strings = useUiStrings().question;
  const name = useId();
  const ordered = orderById(options, optionOrder);
  // Không có khóa đáp án (`option.correct` thiếu ở mọi phương án) thì không có gì để so: ẩn đáp án, tránh
  // chip "Bạn chọn · Sai" tự suy ra từ dữ liệu thiếu.
  const showAnswers =
    review !== undefined &&
    (review.showAnswers ?? true) &&
    options.some((option) => option.correct !== undefined);
  return (
    <fieldset
      aria-label={groupLabel}
      className="m-0 flex min-w-0 flex-col gap-2.5 border-0 p-0"
      data-question-options=""
    >
      <legend className="sr-only">
        {multiple ? strings.chooseMultiple : strings.chooseSingle}
      </legend>
      {multiple && review === undefined && (
        <p className="text-caption text-text-secondary">{strings.chooseMultiple}</p>
      )}
      {ordered.map((option, index) => {
        const checked = selected.includes(option.id);
        const isCorrect = option.correct === true;
        const letter = optionLetter(index);
        const rowId = `${name}-${option.id}`;
        return (
          <label
            key={option.id}
            htmlFor={review ? undefined : rowId}
            className={cn(
              "flex min-h-13 flex-wrap items-center gap-x-3 gap-y-1 rounded-xl border px-3.5 py-2.5 text-title text-text",
              review === undefined && "cursor-pointer transition-colors duration-150 motion-reduce:transition-none",
              review === undefined && (checked ? "border-primary-600 bg-success-bg" : "border-border bg-surface hover:border-border-strong"),
              review !== undefined && optionReviewClass(checked, isCorrect, showAnswers),
            )}
          >
            {review === undefined ? (
              multiple ? (
                <Checkbox
                  id={rowId}
                  checked={checked}
                  onCheckedChange={(next) =>
                    onChange?.(
                      next === true
                        ? [...selected, option.id]
                        : selected.filter((id) => id !== option.id),
                    )
                  }
                />
              ) : (
                <input
                  id={rowId}
                  type="radio"
                  name={name}
                  checked={checked}
                  onChange={() => {
                    if (!checked) onChange?.([option.id]);
                  }}
                  onClick={() => {
                    // Bấm hay Space lên phương án đang chọn là bỏ chọn: giá trị về null (AC-7).
                    if (checked) onChange?.([]);
                  }}
                  className={cn("size-5 shrink-0 accent-primary-fill", focusRing)}
                />
              )
            ) : (
              <OptionMarker kind={multiple ? "checkbox" : "radio"} filled={checked} correct={isCorrect && showAnswers} />
            )}
            <span className="font-semibold">{letter}.</span>
            <div data-exam-content className="min-w-0 flex-1 wrap-break-word">
              <RichTextView html={option.content} />
            </div>
            {review !== undefined && checked && (
              <ReviewTag
                label={isCorrect || !showAnswers ? strings.yourPickTag : strings.yourPickWrongTag}
                tone={isCorrect || !showAnswers ? "neutral" : "wrong"}
              />
            )}
            {review !== undefined && showAnswers && isCorrect && (
              <ReviewTag label={strings.correctAnswerTag} tone="correct" />
            )}
            {review !== undefined && showAnswers && isCorrect && !checked && multiple && (
              <ReviewTag label={strings.notPickedTag} tone="partial" />
            )}
          </label>
        );
      })}
    </fieldset>
  );
}

function optionReviewClass(checked: boolean, correct: boolean, showAnswers: boolean): string {
  if (checked && correct) return "border-2 border-success bg-success-bg";
  if (checked && !correct && showAnswers) return "border-2 border-dashed border-danger bg-danger-bg";
  if (checked) return "border-2 border-primary-600 bg-success-bg";
  if (correct && showAnswers) return "border-2 border-success bg-success-bg";
  return "border-border bg-surface";
}

/** Radio/checkbox chỉ đọc của review: không focus, không đổi được. */
function OptionMarker({ kind, filled, correct }: { kind: "radio" | "checkbox"; filled: boolean; correct: boolean }) {
  const tone = correct ? "border-success text-success" : "border-border-strong text-text-muted";
  if (kind === "checkbox") {
    return (
      <span aria-hidden className={cn("grid size-5 shrink-0 place-items-center rounded-[5px] border-2", filled ? "border-transparent bg-success text-surface" : tone)}>
        {filled && <Check aria-hidden className="size-3.5" strokeWidth={3} />}
      </span>
    );
  }
  return (
    <span aria-hidden className={cn("grid size-5 shrink-0 place-items-center rounded-full border-2", tone)}>
      {filled && <span className={cn("size-2.5 rounded-full", correct ? "bg-success" : "bg-danger")} />}
    </span>
  );
}

/** Nhãn nhỏ trên từng phương án/ô ở review ("Bạn chọn", "Đáp án đúng"…). */
export function ReviewTag({ label, tone }: { label: string; tone: "correct" | "wrong" | "partial" | "neutral" | "info" }) {
  const toneClass =
    tone === "correct"
      ? "bg-success text-surface"
      : tone === "wrong"
        ? "border border-dashed border-danger bg-surface text-danger"
        : tone === "partial"
          ? "border border-dashed border-warning bg-surface text-warning"
          : tone === "info"
            ? "border border-info bg-info-bg text-info"
            : "border border-border-strong bg-surface text-text";
  return (
    <span className={cn("inline-flex h-6 items-center rounded-md px-2 text-caption font-semibold whitespace-nowrap", toneClass)}>
      {label}
    </span>
  );
}
