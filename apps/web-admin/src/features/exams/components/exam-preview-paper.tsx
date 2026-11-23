"use client";

import { Badge, Button, StatusChip } from "@thi360/ui";
import {
  filterAudioAttachments,
  parseRawQuestionBody,
  Question,
  QuestionSet,
  type ChildQuestionProps,
  type EssayCriterion,
  type KitQuestion,
  type KitQuestionOf,
  type QuestionBody as KitQuestionBody,
  type QuestionReview,
} from "@thi360/ui/question";
import { ListOrdered, PenLine } from "lucide-react";
import { useFormatter, useTranslations } from "next-intl";
import { Fragment, useMemo, useState } from "react";
import { DISPLAY_NUMBER_FORMAT } from "../display-number";
import type { PreviewBlock, PreviewDisplaySegment, PreviewItem, PreviewPaper, PreviewPart, PreviewQuestion } from "../review-types";
import { PreviewAudioList } from "./exam-preview-audio";
import { ExamName, richNodes, useLookup } from "./exam-review-shared";

/** Số mục (câu hoặc bộ) hiện sẵn của mỗi khối; phần còn lại mở bằng "Hiện tất cả". */
const VISIBLE_ITEMS = 3;

type HeadingLevel = 1 | 2 | 3 | 4 | 5 | 6;

function clampHeadingLevel(level: number): HeadingLevel {
  return Math.min(6, Math.max(1, level)) as HeadingLevel;
}

function displayNumber(question: PreviewQuestion, original: boolean): number {
  return original ? (question.originalNumber ?? question.number) : question.number;
}

function resolveOptionOrder(question: PreviewQuestion, original: boolean): string[] | undefined {
  const order = original ? question.originalOptionOrder : question.optionOrder;
  return order && order.length > 0 ? order : undefined;
}

function resolveEssayRubric(question: PreviewQuestion): EssayCriterion[] | null {
  if (question.rubric !== null) return question.rubric;
  if (question.body?.type === "ESSAY") return question.body.rubric;
  return null;
}

function buildKitQuestion(question: PreviewQuestion, original: boolean): KitQuestion {
  const body = parseRawQuestionBody(question.body, {
    questionType: question.type,
    includeAnswers: true,
    fallbackOptions: question.options,
    fallbackAnswer: question.answer,
    fallbackPassage: question.content,
    fallbackRubric: resolveEssayRubric(question) ?? undefined,
  });
  const stem =
    body.type === "CLOZE" && (!question.content || question.content === body.passage)
      ? undefined
      : (question.content ?? undefined);

  return {
    id: question.questionId,
    displayNo: displayNumber(question, original),
    points: question.points,
    stem,
    body,
    optionOrder: resolveOptionOrder(question, original),
  };
}

function buildQuestionReview(question: PreviewQuestion): QuestionReview {
  return {
    showAnswers: true,
    solutionHtml: question.solution,
    max: question.points,
    ...(question.type === "ESSAY" ? { outcome: "BLANK" as const } : {}),
  };
}

export interface SegmentSlice {
  segment: PreviewDisplaySegment;
  blocks: PreviewBlock[];
}

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
            <ExamName>{part.name}</ExamName>{" "}
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
          <ExamName>{segment.name}</ExamName>{" "}
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
  // Tên khối do mẫu đặt là nội dung đề: nằm trong `ExamName`, tên môn theo danh mục ở ngoài.
  const name = (
    <Fragment key="block-name">
      {lookup.subject(block.subjectCode)} · <ExamName>{block.blockName}</ExamName>
    </Fragment>
  );
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

  /** Trong phân đoạn, tiêu đề khối nhỏ hơn tiêu đề phân đoạn (h4) một mức; câu và bộ theo sau một mức nữa. */
  const level = scope !== "" ? 5 : 4;
  const Heading = `h${level}` as "h4" | "h5";

  return (
    <div className="flex flex-col gap-3 rounded-lg border border-border bg-surface p-4">
      <Heading className="flex flex-wrap items-center gap-x-2 gap-y-1 text-label font-semibold text-text">
        <span>
          {richNodes(t, "blockHeading", { block: name })} <span className="font-normal text-text-muted">· {range}</span>
        </span>
        {block.manualGrading && <Badge>{t("manualGrading")}</Badge>}
      </Heading>
      <ol id={listId} className="flex flex-col gap-3">
        {visible.map((item, index) => (
          <li key={item.kind === "SET" ? item.setId : item.question.questionId}>
            <PreviewItemView
              item={item}
              setIndex={index + 1}
              original={original}
              cloze={block.questionType === "CLOZE"}
              scope={scope}
              level={Math.min(level + 1, 6)}
            />
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
  level = 5,
}: {
  item: PreviewItem;
  setIndex: number;
  original: boolean;
  cloze: boolean;
  /** Mã phân đoạn chứa mục: bộ bị cắt hiện ở hai phân đoạn nên mã nhận dạng phải khác nhau. */
  scope: string;
  /** Mức tiêu đề của mục: nhỏ hơn tiêu đề khối một mức, cao nhất h6. */
  level?: number;
}) {
  const t = useTranslations("exams.preview");
  if (item.kind === "QUESTION") {
    return (
      <PreviewQuestionCard
        question={item.question}
        original={original}
        level={clampHeadingLevel(level)}
      />
    );
  }

  // Bài đọc điền: chỗ trống của ngữ liệu mang số của câu con đầu, câu con thứ n ứng với chỗ trống thứ n (spec 3.4).
  const blankStart = cloze && item.children[0] ? displayNumber(item.children[0], original) : undefined;
  const headingId = `preview-set-${scope}${item.setId}`;
  const setNumbers = item.children.map((child) => displayNumber(child, original));
  const setFirst = setNumbers[0] ?? 0;
  const setLast = setNumbers[setNumbers.length - 1] ?? 0;
  const setHeadingLevel = clampHeadingLevel(level);
  const SetHeading = `h${setHeadingLevel}` as const;

  const setKitId = scope ? `${scope}-${item.setId}` : item.setId;
  const childById = new Map(item.children.map((child) => [child.questionId, child]));
  const childKits = item.children.map((child) => buildKitQuestion(child, original));

  return (
    <article aria-labelledby={headingId} className="flex flex-col gap-3 rounded-md border border-border p-3">
      <div className="flex min-w-0 flex-wrap items-center gap-2">
        <SetHeading id={headingId} className="font-semibold text-text">
          {t("setHeading", { index: setIndex, count: item.children.length })}
        </SetHeading>
        {/* "Có audio" đứng ngay sau số câu như bản vẽ `SinhThuAudio2B` (V-8). */}
        {filterAudioAttachments(item.attachments).length > 0 && <StatusChip tone="info">{t("audio.badge")}</StatusChip>}
        <Badge className="whitespace-normal wrap-anywhere">{item.version !== null ? `${item.code} · v${item.version}` : item.code}</Badge>
      </div>
      <PreviewAudioList attachments={item.attachments} name={t("audio.forSet", { first: setFirst, last: setLast })} />
      {item.simulated && !item.passage ? (
        <>
          <p className="text-body text-text-muted italic">{t("simulatedPassage")}</p>
          <ol className="flex flex-col gap-2">
            {item.children.map((child, childIndex) => (
              <li key={child.questionId}>
                <PreviewQuestionCard
                  question={child}
                  compact
                  original={original}
                  level={clampHeadingLevel(level + 1)}
                  blank={blankStart === undefined ? undefined : blankStart + childIndex}
                />
              </li>
            ))}
          </ol>
        </>
      ) : (
        <QuestionSet
          question={
            {
              id: setKitId,
              displayNo: setFirst || 1,
              body: {
                type: "QUESTION_SET",
                passage: item.passage ?? "",
                blankNumberStart: blankStart ?? item.blankNumberStart,
                childIds: item.children.map((child) => child.questionId),
              },
            } satisfies KitQuestionOf<Extract<KitQuestionBody, { type: "QUESTION_SET" }>>
          }
          mode="review"
          review={{
            showAnswers: true,
            children: Object.fromEntries(
              item.children.map((child) => [child.questionId, buildQuestionReview(child)]),
            ),
          }}
          blankNumberStart={blankStart ?? item.blankNumberStart}
          headingLevel={setHeadingLevel}
          renderQuestion={(childProps: ChildQuestionProps) => {
            const child = childById.get(childProps.question.id);
            if (!child) return null;
            const childIndex = item.children.indexOf(child);
            return (
              <PreviewQuestionCard
                question={child}
                kitQuestion={childProps.question}
                review={childProps.review ?? buildQuestionReview(child)}
                compact
                original={original}
                level={childProps.headingLevel ?? clampHeadingLevel(level + 1)}
                blank={blankStart === undefined ? undefined : blankStart + childIndex}
              />
            );
          }}
        >
          {childKits}
        </QuestionSet>
      )}
    </article>
  );
}

function PreviewQuestionCard({
  question,
  kitQuestion: providedKit,
  review: providedReview,
  compact = false,
  original = false,
  blank,
  level,
}: {
  question: PreviewQuestion;
  kitQuestion?: KitQuestion;
  review?: QuestionReview;
  compact?: boolean;
  original?: boolean;
  /** Số hiện ở chỗ trống của ngữ liệu mà câu con này điền (bài đọc điền); bỏ trống với câu thường. */
  blank?: number;
  /** Mức tiêu đề: mặc định h5 cho câu đứng một mình, h6 cho câu con của bộ. */
  level?: HeadingLevel;
}) {
  const t = useTranslations("exams.preview");
  const types = useTranslations("content.questionType");
  const format = useFormatter();
  const lookup = useLookup();

  const resolvedLevel = clampHeadingLevel(level ?? (compact ? 6 : 5));
  const number = displayNumber(question, original);
  const kitQuestion = useMemo(
    () => providedKit ?? buildKitQuestion(question, original),
    [providedKit, question, original],
  );
  const review = useMemo(
    () => providedReview ?? buildQuestionReview(question),
    [providedReview, question],
  );
  const rubric = question.type === "ESSAY" ? resolveEssayRubric(question) : null;
  const SimulatedHeading = `h${resolvedLevel}` as const;
  const headingId = question.simulated
    ? `preview-q-${question.questionId}`
    : `${kitQuestion.id}-title`;

  return (
    <article
      aria-labelledby={question.simulated ? headingId : undefined}
      className={compact ? "flex flex-col gap-2" : "flex flex-col gap-2 rounded-md border border-border p-3"}
    >
      <div className="flex min-w-0 flex-wrap items-center gap-2">
        {question.simulated && (
          <SimulatedHeading id={headingId} className="font-semibold text-text">
            {t("questionNumber", { number })}
          </SimulatedHeading>
        )}
        {/* "Có audio" đứng ngay sau số câu như bản vẽ `SinhThuAudio2B` (V-8). */}
        {filterAudioAttachments(question.attachments).length > 0 && <StatusChip tone="info">{t("audio.badge")}</StatusChip>}
        {/* R-112: điểm tối đa của câu (chip thông tin, như bản vẽ `SinhThuVact2B`). */}
        <StatusChip tone="info">
          {t("questionPoints", { points: format.number(question.points, DISPLAY_NUMBER_FORMAT), value: question.points })}
        </StatusChip>
        {/* Mã câu dài được xuống dòng (V-7): hàng đã wrap, chip không giữ nowrap. */}
        <Badge className="whitespace-normal wrap-anywhere">
          {question.version !== null ? `${question.code} · v${question.version}` : question.code}
        </Badge>
        {question.simulated && <Badge>{types(question.type)}</Badge>}
        <Badge>{lookup.grade(question.gradeCode)}</Badge>
        <Badge>{lookup.difficulty(question.difficultyCode)}</Badge>
        {question.knowledge && (
          <Badge className="whitespace-normal wrap-anywhere">
            {question.knowledge.code} · <ExamName>{question.knowledge.name}</ExamName>
          </Badge>
        )}
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
        <Question
          question={kitQuestion}
          mode="review"
          value={null}
          review={review}
          headingLevel={resolvedLevel}
        />
      )}
      {!question.simulated && rubric !== null && (
        <div className="flex flex-col gap-1 rounded-md bg-subtle px-3 py-2 text-body">
          <span className="font-medium text-text">{t("rubricTitle")}</span>
          {rubric.length === 0 ? (
            <span className="text-text-secondary">{t("rubricEmpty")}</span>
          ) : (
            <ul className="flex flex-col gap-0.5 text-text-secondary">
              {rubric.map((criterion) => (
                <li key={criterion.id}>
                  {richNodes(t, "rubricItem", {
                    description: <ExamName>{criterion.description}</ExamName>,
                    points: criterion.points,
                  })}
                </li>
              ))}
            </ul>
          )}
        </div>
      )}
    </article>
  );
}
