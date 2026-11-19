"use client";

import { useEffect, useRef, useState, type ReactNode } from "react";
import { createPortal } from "react-dom";
import { Input } from "../components/input";
import { useUiLocale, useUiStrings } from "../components/ui-strings";
import { cn } from "../lib/cn";
import { RichTextView } from "../rich-text";
import { formatScore } from "./helpers";
import { OutcomeGlyph, QuestionFrame, ReviewPoints } from "./question-frame";
import type { KitQuestionProps } from "./shared";
import { answersVisible, resolveReview } from "./shared";
import type { ClozeAnswer, KitQuestion, KitQuestionOf, PartResult, QuestionReview } from "./types";

const MARKER = /\[\[([^\]]+)\]\]/g;

interface Slot {
  blankId: string;
  element: HTMLElement;
}

/**
 * Ngữ liệu Điền khuyết: HTML có chỗ trống `[[id]]` nằm ngay trong dòng chữ (port từ
 * `apps/web-admin` ClozePassage, FS-4). Mỗi `[[id]]` được thay bằng một ô; `renderBlank`
 * của `RichTextView` chỉ dùng nội dung sau khi hydrate nên không lẫn vào ngữ liệu.
 */
export function ClozePassage({
  html,
  renderBlank,
  className,
}: {
  html: string;
  renderBlank: (blankId: string, ordinal: number) => ReactNode;
  className?: string;
}) {
  const rootRef = useRef<HTMLDivElement>(null);
  const [slots, setSlots] = useState<Slot[]>([]);
  useEffect(() => {
    const root = rootRef.current;
    if (!root) return;
    const scan = () => {
      const found = [...root.querySelectorAll<HTMLElement>("[data-cloze-slot]")];
      setSlots((current) =>
        current.length === found.length &&
        current.every((slot, index) => slot.element === found[index])
          ? current
          : found.map((element) => ({ blankId: element.dataset.clozeSlot ?? "", element })),
      );
    };
    scan();
    const observer = new MutationObserver(scan);
    observer.observe(root, { childList: true, subtree: true });
    return () => observer.disconnect();
  }, []);
  return (
    <div ref={rootRef} className={className}>
      <RichTextView html={html} />
      {slots.map((slot, ordinal) =>
        createPortal(renderBlank(slot.blankId, ordinal), slot.element, `${ordinal}-${slot.blankId}`),
      )}
    </div>
  );
}

/** Chuẩn hóa HTML ngữ liệu: đổi mỗi `[[id]]` thành `<span data-cloze-slot="id">` chứa ký hiệu. */
export function clozeHtml(passage: string, numberStart = 1): string {
  MARKER.lastIndex = 0;
  let index = 0;
  return passage.replace(MARKER, (_match, id: string) => {
    const blankId = id.trim();
    const ordinal = index;
    index += 1;
    return `<span data-cloze-slot="${blankId}" style="display:inline-block;min-width:96px">(${numberStart + ordinal}) ____</span>`;
  });
}

type ClozeBody = Extract<KitQuestion["body"], { type: "CLOZE" }>;

/**
 * Câu Điền khuyết: ô inline ngay trong đoạn (LT 8, canvas `ThiDienKhuyet`):
 * số ô = `displayNo + ordinal` (BS 5), bàn phím đi vào trực tiếp, dấu phẩy hay chấm đều được.
 * Review: ô chỉ đọc + icon kết quả, "Đáp án" (bảng chấp nhận) khi `showAnswers`.
 */
export function ClozeQuestion({
  question,
  mode,
  value,
  onChange,
  flagged,
  onToggleFlag,
  review,
  headingLevel,
  className,
}: KitQuestionProps<KitQuestionOf<ClozeBody>, ClozeAnswer>) {
  const strings = useUiStrings().question;
  const resolved = resolveReview(review);
  const body = question.body;
  const blanks = value?.blanks ?? {};
  const filled = body.blanks.filter((blank) => (blanks[blank.id] ?? "").trim() !== "").length;
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
        <div className="flex flex-wrap items-center justify-between gap-2">
          <p className="text-caption text-text-secondary">{strings.clozeHelp}</p>
          <p role="status" className="text-caption font-medium text-text-secondary tabular-nums">
            {strings.clozeFilled(filled, body.blanks.length)}
          </p>
        </div>
      )}
      <div data-exam-content>
        <ClozePassage
          html={clozeHtml(body.passage, question.displayNo)}
          renderBlank={(blankId, ordinal) => {
            const number = question.displayNo + ordinal;
            const part = resolved?.gradingDetail?.[blankId];
            return (
              <span className="relative mx-1 inline-flex items-center align-baseline">
                <Input
                  size="md"
                  value={blanks[blankId] ?? ""}
                  readOnly={mode === "review"}
                  aria-readonly={mode === "review" || undefined}
                  aria-label={strings.clozeBlankAria(number, ordinal + 1)}
                  placeholder={strings.clozePlaceholder(ordinal + 1)}
                  inputMode="decimal"
                  onChange={(event) =>
                    onChange?.({ blanks: { ...blanks, [blankId]: event.target.value } })
                  }
                  className={cn(
                    "inline-flex w-24 px-2.5 text-center font-semibold tabular-nums",
                    mode === "review" && partToneClass(part),
                  )}
                />
                {mode === "review" && part && (
                  <OutcomeGlyph outcome={part.outcome} className="absolute -top-1.5 -right-1.5 size-3.5" />
                )}
              </span>
            );
          }}
        />
      </div>
      {mode === "answer" && <p className="text-caption text-text-secondary">{strings.clozeBlankNote}</p>}
      {mode === "review" && <ClozeReviewTable question={question} value={value} review={resolved} />}
    </QuestionFrame>
  );
}

function partToneClass(part: PartResult | undefined): string | undefined {
  if (!part) return undefined;
  switch (part.outcome) {
    case "CORRECT":
      return "border-success bg-success-bg";
    case "PARTIAL":
      return "border-warning bg-warning-bg";
    case "WRONG":
      return "border-danger bg-danger-bg";
    default:
      return undefined;
  }
}

function ClozeReviewTable({
  question,
  value,
  review,
}: {
  question: KitQuestionOf<ClozeBody>;
  value: ClozeAnswer | null | undefined;
  review: QuestionReview | null;
}) {
  const strings = useUiStrings().question;
  const locale = useUiLocale();
  const blanks = value?.blanks ?? {};
  const showAnswers = answersVisible(review);
  const hasPoints = question.body.blanks.some((blank) => review?.gradingDetail?.[blank.id]?.earned != null);
  return (
    <div className="overflow-x-auto">
      <table className="w-full border-collapse text-left">
        <thead>
          <tr className="border-b border-border">
            <th className="py-2 pr-4 text-caption font-semibold text-text-secondary">{strings.itemCol}</th>
            <th className="py-2 pr-4 text-caption font-semibold text-text-secondary">{strings.youChoseCol}</th>
            {showAnswers && (
              <th className="py-2 pr-4 text-caption font-semibold text-text-secondary">{strings.acceptedCol}</th>
            )}
            {hasPoints && <th className="py-2 text-caption font-semibold text-text-secondary">{strings.pointsCol}</th>}
          </tr>
        </thead>
        <tbody>
          {question.body.blanks.map((blank, ordinal) => {
            const part = review?.gradingDetail?.[blank.id];
            const text = blanks[blank.id] ?? "";
            const number = question.displayNo + ordinal;
            return (
              <tr key={blank.id} className="border-b border-border/60 last:border-0">
                <td className="py-2.5 pr-4 text-label font-semibold text-text tabular-nums">
                  {strings.questionLabel(number)}
                </td>
                <td className="py-2.5 pr-4 text-label text-text">
                  <span className="inline-flex items-center gap-2">
                    {part && <OutcomeGlyph outcome={part.outcome} />}
                    {text === "" ? <span className="text-text-muted">{strings.blankFilledTag}</span> : text}
                  </span>
                </td>
                {showAnswers && (
                  <td className="py-2.5 pr-4 text-label text-text" data-exam-content>
                    {blank.answers.join(" · ")}
                  </td>
                )}
                {hasPoints && (
                  <td className="py-2.5 text-label text-text tabular-nums">
                    {part?.earned != null
                      ? strings.points(formatScore(part.earned, locale), formatScore(part.max ?? blank.points ?? 1, locale))
                      : "—"}
                  </td>
                )}
              </tr>
            );
          })}
        </tbody>
      </table>
    </div>
  );
}
