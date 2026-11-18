"use client";

import { Badge, Button, cn, focusRing, StatusChip } from "@thi360/ui";
import { Check, ListOrdered, PenLine, X } from "lucide-react";
import { useFormatter, useTranslations } from "next-intl";
import { useMemo, useState } from "react";
import { RichTextView } from "@/components/rich-text";
import { DISPLAY_NUMBER_FORMAT } from "../display-number";
import type { PreviewBlock, PreviewDisplaySegment, PreviewItem, PreviewPaper, PreviewPart, PreviewQuestion } from "../review-types";
import { audioAttachments, PreviewAudioList } from "./exam-preview-audio";
import { useLookup } from "./exam-review-shared";

/** Số mục (câu hoặc bộ) hiện sẵn của mỗi khối; phần còn lại mở bằng "Hiện tất cả". */
const VISIBLE_ITEMS = 3;
const OPTION_KEYS = ["A", "B", "C", "D", "E", "F", "G", "H"];

/** Số câu hiện trên đề: số đã đảo của đề thật, hoặc vị trí gốc khi xem thứ tự gốc (K12). */
function displayNumber(question: PreviewQuestion, original: boolean): number {
  return original ? (question.originalNumber ?? question.number) : question.number;
}

export interface SegmentSlice {
  segment: PreviewDisplaySegment;
  blocks: PreviewBlock[];
}

/**
 * Chia các khối của một phần theo `displaySegments` (H40): câu nằm ở phân đoạn có dải chứa vị trí của câu (số trên đề
 * trừ số đầu của phần cộng 1). Bộ bị ranh giới cắt giữa chừng xuất hiện ở cả hai phân đoạn, mỗi nơi chỉ giữ câu con của
 * mình, nên ngữ liệu hiện ở cả hai. Không đổi số câu. `null` khi phần không có phân đoạn: hiện như trước.
 */
export function sliceBySegments(part: PreviewPart, original: boolean): SegmentSlice[] | null {
  const segments = part.displaySegments;
  if (!segments || segments.length === 0) return null;
  return [...segments]
    .sort((a, b) => a.fromPosition - b.fromPosition)
    .map((segment) => {
      const inside = (question: PreviewQuestion) => {
        const position = displayNumber(question, original) - part.firstNumber + 1;
        return position >= segment.fromPosition && position <= segment.toPosition;
      };
      const blocks = part.blocks.flatMap((block): PreviewBlock[] => {
        const items = block.items.flatMap((item): PreviewItem[] => {
          if (item.kind === "QUESTION") return inside(item.question) ? [item] : [];
          const children = item.children.filter(inside);
          if (children.length === 0) return [];
          return [children.length === item.children.length ? item : { ...item, children }];
        });
        if (items.length === 0) return [];
        if (items.length === block.items.length && items.every((item, index) => item === block.items[index])) return [block];
        const numbers = items.flatMap((item) =>
          (item.kind === "QUESTION" ? [item.question] : item.children).map((question) => displayNumber(question, original)),
        );
        return [
          {
            ...block,
            items,
            firstNumber: Math.min(...numbers),
            lastNumber: Math.max(...numbers),
            questionCount: numbers.length,
            setCount: null,
            setSize: null,
          },
        ];
      });
      return { segment, blocks };
    });
}

/** Đề đã sinh: phần › khối › câu theo đúng thứ tự thực tế, kèm đáp án và lời giải (chế độ quản trị). `original`: thứ tự gốc (K12). */
export function ExamPreviewPaper({ paper, original = false }: { paper: PreviewPaper; original?: boolean }) {
  const t = useTranslations("exams.preview");
  return (
    <div className="flex flex-col gap-6">
      <p className="text-body text-text-secondary">
        {t("paperSummary", {
          questions: paper.totalQuestions,
          points: paper.totalPoints,
          minutes: paper.totalMinutes,
          shared: paper.sharedDuration ? "yes" : "no",
        })}
      </p>
      {original && (
        <p className="flex items-start gap-2 rounded-md border border-border bg-subtle px-3 py-2 text-body text-text-secondary">
          <ListOrdered aria-hidden className="mt-0.5 size-4 shrink-0" />
          {t("originalOrderNote")}
        </p>
      )}
      {paper.hasManualGrading && (
        <p className="flex items-start gap-2 rounded-md border border-border bg-subtle px-3 py-2 text-body text-text-secondary">
          <PenLine aria-hidden className="mt-0.5 size-4 shrink-0" />
          {t("manualGradingNote")}
        </p>
      )}
      {paper.parts.map((part) => (
        <section key={part.partId} aria-labelledby={`preview-part-${part.partId}`} className="flex flex-col gap-4">
          <h3 id={`preview-part-${part.partId}`} className="text-title font-semibold text-text">
            {part.name}{" "}
            <span className="font-normal text-text-muted">
              ·{" "}
              {t("partRange", {
                first: part.firstNumber,
                last: part.lastNumber,
                minutes: part.durationMinutes ?? 0,
                hasMinutes: part.durationMinutes === null ? "no" : "yes",
              })}{" "}
              ·{" "}
              {part.maxPoints !== null
                ? t("partPointsOf", { points: part.points, max: part.maxPoints })
                : t("partPoints", { points: part.points })}
            </span>
          </h3>
          <PreviewPartBody part={part} original={original} />
        </section>
      ))}
    </div>
  );
}

function PreviewPartBody({ part, original }: { part: PreviewPart; original: boolean }) {
  const t = useTranslations("exams.preview");
  const format = useFormatter();
  const slices = useMemo(() => sliceBySegments(part, original), [part, original]);
  if (slices === null) {
    return part.blocks.map((block) => <PreviewBlockView key={block.blockId} block={block} original={original} />);
  }
  return slices.map(({ segment, blocks }) => {
    const headingId = `preview-segment-${part.partId}-${segment.code}`;
    return (
      <section key={segment.code} aria-labelledby={headingId} className="flex flex-col gap-3">
        <h4 id={headingId} className="text-label font-semibold text-text">
          {segment.name}{" "}
          <span className="font-normal text-text-muted">
            ·{" "}
            {t("segmentRange", {
              first: part.firstNumber + segment.fromPosition - 1,
              last: part.firstNumber + segment.toPosition - 1,
              commands: segment.commandCount,
              points: format.number(segment.maxPoints, DISPLAY_NUMBER_FORMAT),
            })}
          </span>
        </h4>
        {blocks.map((block) => (
          <PreviewBlockView key={block.blockId} block={block} original={original} scope={segment.code} />
        ))}
      </section>
    );
  });
}

function PreviewBlockView({ block, original, scope = "" }: { block: PreviewBlock; original: boolean; scope?: string }) {
  const t = useTranslations("exams.preview");
  const lookup = useLookup();
  const [expanded, setExpanded] = useState(false);
  const name = `${lookup.subject(block.subjectCode)} · ${block.blockName}`;
  const items = useMemo(
    () =>
      original
        ? [...block.items].sort((a, b) => (a.originalOrder ?? Number.MAX_SAFE_INTEGER) - (b.originalOrder ?? Number.MAX_SAFE_INTEGER))
        : block.items,
    [block.items, original],
  );
  const visible = expanded ? items : items.slice(0, VISIBLE_ITEMS);
  const hidden = items.length - visible.length;
  const range =
    block.setCount !== null && block.setSize !== null
      ? t("blockRangeSets", { sets: block.setCount, size: block.setSize, first: block.firstNumber, last: block.lastNumber })
      : t("blockRange", { first: block.firstNumber, last: block.lastNumber, count: block.questionCount });
  const listId = `preview-block-${scope}${block.blockId}`;

  return (
    <div className="flex flex-col gap-3 rounded-lg border border-border bg-surface p-4">
      <h4 className="flex flex-wrap items-center gap-x-2 gap-y-1 text-label font-semibold text-text">
        <span>
          {t("blockHeading", { block: name })} <span className="font-normal text-text-muted">· {range}</span>
        </span>
        {block.manualGrading && <Badge>{t("manualGrading")}</Badge>}
      </h4>
      <ol id={listId} className="flex flex-col gap-3">
        {visible.map((item, index) => (
          <li key={item.kind === "SET" ? item.setId : item.question.questionId}>
            <PreviewItemView item={item} setIndex={index + 1} original={original} cloze={block.questionType === "CLOZE"} scope={scope} />
          </li>
        ))}
      </ol>
      {block.items.length > VISIBLE_ITEMS && (
        <div className="flex flex-wrap items-center justify-between gap-2 rounded-md bg-subtle px-3 py-2 text-body text-text-secondary">
          <span>{hidden > 0 ? t("hiddenCount", { count: hidden }) : t("allShown", { count: block.items.length })}</span>
          <Button variant="secondary" size="sm" aria-expanded={expanded} aria-controls={listId} onClick={() => setExpanded((value) => !value)}>
            {expanded ? t("showLess") : t("showAll", { count: block.items.length })}
          </Button>
        </div>
      )}
    </div>
  );
}

function PreviewItemView({
  item,
  setIndex,
  original,
  cloze,
  scope,
}: {
  item: PreviewItem;
  setIndex: number;
  original: boolean;
  cloze: boolean;
  /** Mã phân đoạn chứa mục: bộ bị cắt hiện ở hai phân đoạn nên mã nhận dạng phải khác nhau. */
  scope: string;
}) {
  const t = useTranslations("exams.preview");
  if (item.kind === "QUESTION") return <QuestionArticle question={item.question} original={original} />;
  // Bài đọc điền: chỗ trống của ngữ liệu mang số của câu con đầu, câu con thứ n ứng với chỗ trống thứ n (spec 3.4).
  const blankStart = cloze && item.children[0] ? displayNumber(item.children[0], original) : undefined;
  const headingId = `preview-set-${scope}${item.setId}`;
  const setNumbers = item.children.map((child) => displayNumber(child, original));
  const setFirst = setNumbers[0] ?? 0;
  const setLast = setNumbers[setNumbers.length - 1] ?? 0;
  return (
    <article aria-labelledby={headingId} className="flex flex-col gap-3 rounded-md border border-border p-3">
      <div className="flex flex-wrap items-center gap-2">
        <h5 id={headingId} className="font-semibold text-text">
          {t("setHeading", { index: setIndex, count: item.children.length })}
        </h5>
        <Badge>{item.version !== null ? `${item.code} · v${item.version}` : item.code}</Badge>
        {audioAttachments(item.attachments).length > 0 && <StatusChip tone="info">{t("audio.badge")}</StatusChip>}
      </div>
      <PreviewAudioList attachments={item.attachments} name={t("audio.forSet", { first: setFirst, last: setLast })} />
      {item.passage ? (
        <blockquote className="rounded-md border-s-4 border-border bg-subtle px-3 py-2 text-body text-text">
          <RichTextView html={item.passage} blankNumberStart={blankStart ?? item.blankNumberStart} />
        </blockquote>
      ) : (
        <p className="text-body text-text-muted italic">{t("simulatedPassage")}</p>
      )}
      <ol className="flex flex-col gap-2">
        {item.children.map((child) => (
          <li key={child.questionId}>
            <QuestionArticle
              question={child}
              compact
              original={original}
              blank={blankStart === undefined ? undefined : blankStart + item.children.indexOf(child)}
            />
          </li>
        ))}
      </ol>
    </article>
  );
}

function QuestionArticle({
  question,
  compact = false,
  original = false,
  blank,
}: {
  question: PreviewQuestion;
  compact?: boolean;
  original?: boolean;
  /** Số hiện ở chỗ trống của ngữ liệu mà câu con này điền (bài đọc điền); bỏ trống với câu thường. */
  blank?: number;
}) {
  const t = useTranslations("exams.preview");
  const types = useTranslations("content.questionType");
  const format = useFormatter();
  const lookup = useLookup();
  const headingId = `preview-q-${question.questionId}`;
  const isTrueFalse = question.type === "TRUE_FALSE";
  const Heading = compact ? "h6" : "h5";
  const number = displayNumber(question, original);
  // Xem thứ tự gốc: phương án theo thứ tự lúc soạn (`originalOptionOrder`); không có thì giữ thứ tự đang hiện.
  const options =
    original && question.options && question.originalOptionOrder
      ? [...question.options].sort(
          (a, b) => (question.originalOptionOrder ?? []).indexOf(a.id) - (question.originalOptionOrder ?? []).indexOf(b.id),
        )
      : question.options;

  return (
    <article aria-labelledby={headingId} className={compact ? "flex flex-col gap-2" : "flex flex-col gap-2 rounded-md border border-border p-3"}>
      <div className="flex flex-wrap items-center gap-2">
        <Heading id={headingId} className="font-semibold text-text">
          {t("questionNumber", { number })}
        </Heading>
        {/* R-112: điểm tối đa của câu (chip thông tin, như bản vẽ `SinhThuVact2B`). */}
        <StatusChip tone="info">
          {t("questionPoints", { points: format.number(question.points, DISPLAY_NUMBER_FORMAT), value: question.points })}
        </StatusChip>
        <Badge>{question.version !== null ? `${question.code} · v${question.version}` : question.code}</Badge>
        <Badge>{types(question.type)}</Badge>
        <Badge>{lookup.grade(question.gradeCode)}</Badge>
        <Badge>{lookup.difficulty(question.difficultyCode)}</Badge>
        {question.knowledge && (
          <Badge className="whitespace-normal wrap-anywhere">{`${question.knowledge.code} · ${question.knowledge.name}`}</Badge>
        )}
        {audioAttachments(question.attachments).length > 0 && <StatusChip tone="info">{t("audio.badge")}</StatusChip>}
      </div>
      <PreviewAudioList attachments={question.attachments} name={t("audio.forQuestion", { number })} />
      {blank !== undefined && (
        <p className="text-caption text-text-muted">
          {t("clozePosition", { number, blank, type: types(question.type) })}
        </p>
      )}
      {question.simulated ? (
        <p className="text-body text-text-muted italic">{t("simulatedQuestion")}</p>
      ) : (
        <RichTextView html={question.content ?? ""} className="text-body text-text" />
      )}
      {options && (
        <ol className="flex flex-col gap-1.5">
          {options.map((option, index) => {
            const marked = isTrueFalse || option.correct;
            return (
              <li
                key={option.id}
                className={`flex min-h-10 items-center gap-2.5 rounded-md border px-3 py-1.5 text-body ${
                  option.correct && !isTrueFalse ? "border-primary-600 bg-primary-50" : "border-border"
                }`}
              >
                <span aria-hidden className="grid size-6 shrink-0 place-items-center rounded-full bg-subtle text-caption font-semibold">
                  {OPTION_KEYS[index] ?? index + 1}
                </span>
                <RichTextView html={option.content} className="min-w-0 flex-1 text-text" />
                {marked && (
                  <span
                    className={`inline-flex shrink-0 items-center gap-1 text-caption font-medium ${
                      option.correct ? "text-primary-text" : "text-text-secondary"
                    }`}
                  >
                    {option.correct ? <Check aria-hidden className="size-3.5" /> : <X aria-hidden className="size-3.5" />}
                    {isTrueFalse ? (option.correct ? t("trueLabel") : t("falseLabel")) : t("correct")}
                  </span>
                )}
              </li>
            );
          })}
        </ol>
      )}
      {question.answer && (
        <p className="flex items-center gap-1.5 text-body text-primary-text">
          <Check aria-hidden className="size-4 shrink-0" />
          {t("answer", { answer: question.answer })}
        </p>
      )}
      {question.rubric && (
        <div className="flex flex-col gap-1 rounded-md bg-subtle px-3 py-2 text-body">
          <span className="font-medium text-text">{t("rubricTitle")}</span>
          {question.rubric.length === 0 ? (
            <span className="text-text-secondary">{t("rubricEmpty")}</span>
          ) : (
            <ul className="flex flex-col gap-0.5 text-text-secondary">
              {question.rubric.map((criterion) => (
                <li key={criterion.id}>{t("rubricItem", { description: criterion.description, points: criterion.points })}</li>
              ))}
            </ul>
          )}
        </div>
      )}
      {question.solution && (
        <details className="rounded-md bg-subtle px-3 py-2 text-body">
          <summary className={cn("cursor-pointer rounded-sm font-medium text-text", focusRing)}>{t("solution")}</summary>
          <RichTextView html={question.solution} className="mt-1.5 text-text-secondary" />
        </details>
      )}
    </article>
  );
}
