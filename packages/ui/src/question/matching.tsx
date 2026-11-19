"use client";

import { X } from "lucide-react";
import { useState } from "react";
import { useUiLocale, useUiStrings } from "../components/ui-strings";
import { cn } from "../lib/cn";
import { focusRing } from "../lib/styles";
import { RichTextView } from "../rich-text";
import { formatScore, matchLetter, orderById } from "./helpers";
import { MatchOptionContent, MatchSelect } from "./match-select";
import { OutcomeGlyph, QuestionFrame, ReviewPoints } from "./question-frame";
import type { KitQuestionProps } from "./shared";
import { answersVisible, resolveReview } from "./shared";
import { coarseTarget } from "./touch";
import type {
  KitQuestion,
  KitQuestionOf,
  MatchingAnswer,
  MatchingItem,
  QuestionReview,
} from "./types";

type MatchingBody = Extract<KitQuestion["body"], { type: "MATCHING" }>;

/**
 * Câu Ghép đôi: mỗi mục trái chọn một mục phải (LT 8, canvas `ThiGhepDoi`, `ThiMobileGhepDoi`).
 * `matchUi = "select"`: ô chọn ở mỗi hàng trái (mặc định, giống bản vẽ); `"pick"`: bấm mục trái
 * rồi bấm mục phải (cho màn cảm ứng). `reuseRight = true`: một mục phải dùng nhiều lần.
 * Review: bảng — mục trái, bạn chọn (kèm icon và chữ), cặp đúng, điểm từng cặp.
 */
export function MatchingQuestion({
  question,
  mode,
  value,
  onChange,
  flagged,
  onToggleFlag,
  review,
  headingLevel,
  className,
  matchUi = "select",
}: KitQuestionProps<KitQuestionOf<MatchingBody>, MatchingAnswer> & {
  /** Cách chọn ở chế độ làm bài: ô chọn ("select") hay bấm hai lượt ("pick"). */
  matchUi?: "select" | "pick";
}) {
  const strings = useUiStrings().question;
  const resolved = resolveReview(review);
  const body = question.body;
  const pairs = value?.pairs ?? {};
  const left = body.left;
  const right = orderById(body.right, question.optionOrder);
  const reuse = body.reuseRight === true;
  const done = left.filter((item) => pairs[item.id] != null).length;
  const rightUsedBy = new Map<string, string[]>();
  for (const item of left) {
    const picked = pairs[item.id];
    if (picked == null) continue;
    const list = rightUsedBy.get(picked) ?? [];
    list.push(item.id);
    rightUsedBy.set(picked, list);
  }
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
      className={className}
    >
      {mode === "answer" && (
        <div className="flex flex-col gap-3">
          <div className="flex flex-wrap items-center justify-between gap-2">
            <p className="text-caption text-text-secondary">
              {matchUi === "pick" ? strings.matchHelp : reuse ? strings.matchReusable : strings.matchExclusive}
            </p>
            <p role="status" className="text-caption font-medium text-text-secondary tabular-nums">
              {strings.matchDone(done, left.length)}
            </p>
          </div>
          {matchUi === "select" ? (
            <ul className="flex flex-col gap-2.5">
              {left.map((item, index) => {
                const picked = pairs[item.id];
                return (
                  <li
                    key={item.id}
                    className="flex flex-wrap items-center gap-3 rounded-lg border border-border bg-surface px-3 py-2.5"
                  >
                    <span className="grid size-7 shrink-0 place-items-center rounded-full bg-primary-50 text-label font-bold text-primary-text">
                      {index + 1}
                    </span>
                    <span data-exam-content className="min-w-0 flex-1 wrap-break-word text-title text-text">
                      <RichTextView html={item.content} />
                    </span>
                    <span className="text-caption font-medium whitespace-nowrap text-text-secondary">
                      {strings.matchWith}
                    </span>
                    <MatchSelect
                      aria-label={strings.matchPickLeft(index + 1)}
                      value={picked ?? ""}
                      placeholder={strings.matchPlaceholder}
                      options={right.map((option, optionIndex) => ({
                        value: option.id,
                        letter: matchLetter(optionIndex),
                        html: option.content,
                        disabled: !reuse && picked !== option.id && (rightUsedBy.get(option.id)?.length ?? 0) > 0,
                      }))}
                      onValueChange={(next) => onChange?.({ pairs: { ...pairs, [item.id]: next || null } })}
                      className="w-56 shrink-0"
                    />
                    {picked != null && (
                      <button
                        type="button"
                        aria-label={strings.matchClear(index + 1)}
                        onClick={() => onChange?.({ pairs: { ...pairs, [item.id]: null } })}
                        className={cn(
                          "grid size-8 shrink-0 place-items-center rounded-lg text-text-secondary hover:bg-subtle pointer-coarse:size-11",
                          focusRing,
                        )}
                      >
                        <X aria-hidden className="size-4" />
                      </button>
                    )}
                  </li>
                );
              })}
            </ul>
          ) : (
            <MatchingPickBoard
              left={left}
              right={right}
              pairs={pairs}
              reuse={reuse}
              rightUsedBy={rightUsedBy}
              onChange={onChange}
            />
          )}
        </div>
      )}
      {mode === "review" && (
        <MatchingReviewTable question={question} left={left} right={right} value={value} review={resolved} />
      )}
    </QuestionFrame>
  );
}

/** Bảng bấm-chọn của Ghép đôi: cột trái chọn trước, cột phải gán vào; bỏ ghép bằng nút X cạnh mục trái đã ghép. */
function MatchingPickBoard({
  left,
  right,
  pairs,
  reuse,
  rightUsedBy,
  onChange,
}: {
  left: MatchingItem[];
  right: MatchingItem[];
  pairs: Record<string, string | null>;
  reuse: boolean;
  rightUsedBy: Map<string, string[]>;
  onChange?: (value: MatchingAnswer) => void;
}) {
  const strings = useUiStrings().question;
  const [pendingLeft, setPendingLeft] = useState<string | null>(null);
  const leftIndex = new Map(left.map((item, index) => [item.id, index]));
  return (
    <div className="grid gap-4 sm:grid-cols-2">
      <div className="flex flex-col gap-2.5">
        <p className="text-caption font-semibold text-text-secondary">{strings.leftColumn}</p>
        <ul className="flex flex-col gap-2.5">
          {left.map((item, index) => {
            const picked = pairs[item.id];
            const active = pendingLeft === item.id;
            return (
              <li key={item.id} className="flex items-center gap-2">
                <button
                  type="button"
                  aria-pressed={active}
                  aria-label={strings.matchPickLeft(index + 1)}
                  onClick={() => setPendingLeft(active ? null : item.id)}
                  className={cn(
                    "flex min-w-0 flex-1 items-center gap-3 rounded-lg border px-3 py-2.5 text-left transition-colors duration-150 motion-reduce:transition-none pointer-coarse:min-h-11",
                    active ? "border-primary-600 bg-success-bg" : "border-border bg-surface hover:border-border-strong",
                    focusRing,
                  )}
                >
                  <span className="grid size-7 shrink-0 place-items-center rounded-full bg-primary-50 text-label font-bold text-primary-text">
                    {index + 1}
                  </span>
                  <span data-exam-content className="min-w-0 flex-1 wrap-break-word text-title text-text">
                    <RichTextView html={item.content} />
                  </span>
                  {picked != null && (
                    <span className="text-label font-semibold text-primary-text">
                      {matchLetter(right.findIndex((option) => option.id === picked))}
                    </span>
                  )}
                </button>
                {picked != null && (
                  <button
                    type="button"
                    aria-label={strings.matchClear(index + 1)}
                    onClick={() => {
                      onChange?.({ pairs: { ...pairs, [item.id]: null } });
                      if (pendingLeft === item.id) setPendingLeft(null);
                    }}
                    className={cn(
                      "grid size-8 shrink-0 place-items-center rounded-lg text-text-secondary hover:bg-subtle pointer-coarse:size-11",
                      focusRing,
                    )}
                  >
                    <X aria-hidden className="size-4" />
                  </button>
                )}
              </li>
            );
          })}
        </ul>
      </div>
      <div className="flex flex-col gap-2.5">
        <p className="text-caption font-semibold text-text-secondary">{strings.rightColumn}</p>
        <ul className="flex flex-col gap-2.5">
          {right.map((item, index) => {
            const usedBy = rightUsedBy.get(item.id) ?? [];
            const blocked = !reuse && usedBy.length > 0;
            const pendingUsedByOther = pendingLeft != null && usedBy.length > 0 && usedBy[0] !== pendingLeft;
            return (
              <li key={item.id} className="flex flex-col gap-1.5">
                <div
                  className={cn(
                    "flex items-center gap-3 rounded-lg border px-3 py-2.5",
                    blocked ? "border-border bg-subtle" : "border-border bg-surface",
                  )}
                >
                  <span className="grid size-7 shrink-0 place-items-center rounded-full bg-primary-50 text-label font-bold text-primary-text">
                    {matchLetter(index)}
                  </span>
                  <span data-exam-content className="min-w-0 flex-1 wrap-break-word text-title text-text">
                    <RichTextView html={item.content} />
                  </span>
                  {!blocked && pendingLeft != null && (
                    <button
                      type="button"
                      aria-label={strings.matchPickRight(
                        matchLetter(index),
                        usedBy.length > 0 ? usedBy.map((id) => String((leftIndex.get(id) ?? 0) + 1)).join(", ") : null,
                      )}
                      onClick={() => {
                        onChange?.({ pairs: { ...pairs, [pendingLeft]: item.id } });
                        setPendingLeft(null);
                      }}
                      className={cn(
                        "shrink-0 rounded-md bg-primary-fill px-2.5 py-1 text-caption font-semibold text-surface",
                        coarseTarget,
                        focusRing,
                      )}
                    >
                      {strings.matchWith}
                    </button>
                  )}
                  {blocked && (
                    <span className="text-caption font-semibold whitespace-nowrap text-text-muted">
                      {strings.matchUsedTag(
                        usedBy
                          .map((id) => String((leftIndex.get(id) ?? 0) + 1))
                          .join(", "),
                      )}
                    </span>
                  )}
                </div>
                {pendingUsedByOther && usedBy[0] != null && pendingLeft != null && (
                  <p className="rounded-md border border-warning/60 bg-warning-bg px-2.5 py-1.5 text-caption text-warning">
                    {strings.matchUsedBy((leftIndex.get(usedBy[0]) ?? 0) + 1)}
                  </p>
                )}
              </li>
            );
          })}
        </ul>
        <p className="text-caption text-text-muted">{strings.matchDoneNote}</p>
      </div>
    </div>
  );
}

function MatchingReviewTable({
  question,
  left,
  right,
  value,
  review,
}: {
  question: KitQuestionOf<MatchingBody>;
  left: MatchingItem[];
  right: MatchingItem[];
  value: MatchingAnswer | null | undefined;
  review: QuestionReview | null;
}) {
  const strings = useUiStrings().question;
  const locale = useUiLocale();
  const pairs = value?.pairs ?? {};
  const showAnswers = answersVisible(review);
  const correct = new Map((question.body.pairs ?? []).map((pair) => [pair.leftId, pair.rightId]));
  const rightById = new Map(right.map((item, index) => [item.id, { item, letter: matchLetter(index) }]));
  return (
    <div className="overflow-x-auto">
      <table className="w-full border-collapse text-left">
        <thead>
          <tr className="border-b border-border">
            <th className="py-2 pr-4 text-caption font-semibold text-text-secondary">{strings.itemCol}</th>
            <th className="py-2 pr-4 text-caption font-semibold text-text-secondary">{strings.youChoseCol}</th>
            {showAnswers && (
              <th className="py-2 pr-4 text-caption font-semibold text-text-secondary">{strings.answerCol}</th>
            )}
            <th className="py-2 text-caption font-semibold text-text-secondary">{strings.pointsCol}</th>
          </tr>
        </thead>
        <tbody>
          {left.map((item, index) => {
            const part = review?.gradingDetail?.[item.id];
            const pickedId = pairs[item.id];
            const picked = pickedId != null ? rightById.get(pickedId) : undefined;
            const correctId = correct.get(item.id);
            const correctRight = correctId != null ? rightById.get(correctId) : undefined;
            return (
              <tr key={item.id} className="border-b border-border/60 align-top last:border-0">
                <td className="py-2.5 pr-4">
                  <span className="flex items-baseline gap-2 text-label text-text">
                    <strong className="shrink-0 font-semibold tabular-nums">{index + 1}.</strong>
                    <span data-exam-content className="wrap-break-word">
                      <RichTextView html={item.content} />
                    </span>
                  </span>
                </td>
                <td className="py-2.5 pr-4 text-label text-text">
                  <span className="inline-flex items-center gap-2">
                    {part && <OutcomeGlyph outcome={part.outcome} />}
                    {picked ? (
                      <MatchOptionContent letter={picked.letter} html={picked.item.content} wrap />
                    ) : (
                      <span className="text-text-muted">{strings.notMatched}</span>
                    )}
                  </span>
                </td>
                {showAnswers && (
                  <td className="py-2.5 pr-4 text-label text-text">
                    {correctRight ? (
                      <MatchOptionContent letter={correctRight.letter} html={correctRight.item.content} wrap />
                    ) : (
                      "—"
                    )}
                  </td>
                )}
                <td className="py-2.5 text-label whitespace-nowrap text-text tabular-nums">
                  {part?.earned != null
                    ? strings.points(formatScore(part.earned, locale), formatScore(part.max ?? 1, locale))
                    : "—"}
                </td>
              </tr>
            );
          })}
        </tbody>
      </table>
    </div>
  );
}
