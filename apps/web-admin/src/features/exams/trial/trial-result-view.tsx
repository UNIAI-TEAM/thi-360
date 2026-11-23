"use client";

import type { ColumnDef } from "@tanstack/react-table";
import {
  Button,
  Card,
  cn,
  EmptyState,
  focusRing,
  ListCard,
  ListCardTag,
  StatusChip,
  Table,
  type TableCards,
} from "@thi360/ui";
import {
  countWords,
  matchLetter,
  optionLetter,
  orderById,
  readRawQuestionBody,
  type AnswerValue as KitAnswerValue,
  type ClozeAnswer as KitClozeAnswer,
  type EssayAnswer as KitEssayAnswer,
  type MatchingAnswer as KitMatchingAnswer,
  type MultipleChoiceAnswer as KitMultipleChoiceAnswer,
  type ShortAnswerAnswer as KitShortAnswerAnswer,
  type SingleChoiceAnswer as KitSingleChoiceAnswer,
} from "@thi360/ui/question";
import { ArrowLeft, RotateCcw } from "lucide-react";
import { useFormatter, useTranslations } from "next-intl";
import Link from "next/link";
import { useEffect, useMemo, useRef } from "react";
import { DISPLAY_NUMBER_FORMAT } from "../display-number";
import { reviewPreviewHref } from "./api";
import {
  computeObjectiveTotals,
  computePartScoreSummaries,
  extractClozeResultEntries,
  extractTrueFalseEntries,
  listPaperLeafQuestions,
  TRIAL_OUTCOME_TONE,
  type TrialLeafDescriptor,
} from "./mappers";
import type {
  PreviewGradeResponse,
  PreviewGradedItem,
  PreviewTrialOutcome,
  PreviewTrialPaperResponse,
  PreviewTrialQuestion,
} from "./types";

function optionLetters(question: PreviewTrialQuestion): Map<string, string> {
  const options = (readRawQuestionBody(question.body)?.options ?? [])
    .map((opt) => ({ id: opt.id ?? "" }))
    .filter((opt) => opt.id.length > 0);
  return new Map(orderById(options, question.optionOrder).map((opt, idx) => [opt.id, optionLetter(idx)]));
}

function matchingLabels(question: PreviewTrialQuestion): {
  leftLabels: Map<string, string>;
  rightLabels: Map<string, string>;
} {
  const raw = readRawQuestionBody(question.body);
  const leftLabels = new Map<string, string>();
  (raw?.left ?? []).forEach((item, idx) => {
    if (item.id) leftLabels.set(item.id, String(idx + 1));
  });
  const rightItems = (raw?.right ?? [])
    .map((item) => ({ id: item.id ?? "" }))
    .filter((item) => item.id.length > 0);
  const rightLabels = new Map(
    orderById(rightItems, question.optionOrder).map((item, idx) => [item.id, matchLetter(idx)]),
  );
  return { leftLabels, rightLabels };
}

interface GradedQuestionRow {
  questionId: string;
  number: number;
  partName: string;
  type: NonNullable<PreviewGradedItem["type"]>;
  outcome: PreviewTrialOutcome;
  earnedPoints: number;
  maxPoints: number;
  pendingReview: boolean;
  gradedItem: PreviewGradedItem;
  descriptor?: TrialLeafDescriptor;
  answer?: KitAnswerValue;
}

export interface TrialResultViewProps {
  examId: string;
  paper: PreviewTrialPaperResponse;
  grade: PreviewGradeResponse;
  answers: Readonly<Record<string, KitAnswerValue>>;
  onRetake: () => void;
  focusHeadingOnMount?: boolean;
}

export function TrialResultView({
  examId,
  paper,
  grade,
  answers,
  onRetake,
  focusHeadingOnMount = false,
}: TrialResultViewProps) {
  const t = useTranslations("exams.trial");
  const tContent = useTranslations("content.questionType");
  const format = useFormatter();
  const headingRef = useRef<HTMLHeadingElement>(null);

  useEffect(() => {
    if (focusHeadingOnMount) {
      headingRef.current?.focus();
    }
  }, [focusHeadingOnMount]);

  const objective = useMemo(() => computeObjectiveTotals(grade), [grade]);
  const partSummaries = useMemo(() => computePartScoreSummaries(paper, grade), [paper, grade]);
  const hasPending = Boolean(grade.hasPendingManualGrading);

  const descriptorByQuestionId = useMemo(() => {
    const map = new Map<string, TrialLeafDescriptor>();
    for (const leaf of listPaperLeafQuestions(paper)) {
      map.set(leaf.questionId, leaf);
    }
    return map;
  }, [paper]);

  const rows = useMemo<GradedQuestionRow[]>(() => {
    return [...(grade.items ?? [])]
      .sort((a, b) => (a.number ?? 0) - (b.number ?? 0))
      .map((item, idx) => {
        const questionId = item.questionId ?? `row-${idx}`;
        const descriptor = item.questionId ? descriptorByQuestionId.get(item.questionId) : undefined;
        const type = item.type ?? descriptor?.type ?? "SINGLE_CHOICE";
        const pendingReview = Boolean(item.pendingReview) || item.outcome === "PENDING_REVIEW";
        const outcome: PreviewTrialOutcome =
          item.outcome ?? (pendingReview ? "PENDING_REVIEW" : "BLANK");
        return {
          questionId,
          number: item.number ?? descriptor?.number ?? idx + 1,
          partName: descriptor?.partName ?? "",
          type,
          outcome,
          earnedPoints: item.earnedPoints ?? 0,
          maxPoints: item.maxPoints ?? descriptor?.question.points ?? 0,
          pendingReview,
          gradedItem: item,
          descriptor,
          answer: item.questionId ? answers[item.questionId] : undefined,
        };
      });
  }, [grade.items, descriptorByQuestionId, answers]);

  const stats = grade.statistics;
  const backHref = reviewPreviewHref(examId);

  const tableRows = useMemo(() => [...rows], [rows]);

  const columns = useMemo<ColumnDef<GradedQuestionRow>[]>(
    () => [
      {
        id: "number",
        header: t("result.columns.question"),
        cell: ({ row }) => (
          <div className="flex flex-col gap-0.5">
            <span className="font-semibold whitespace-nowrap text-text tabular-nums">
              {t("result.questionLabel", { number: format.number(row.original.number) })}
            </span>
            {row.original.partName && (
              <span data-exam-content className="text-caption text-text-muted">
                {row.original.partName}
              </span>
            )}
          </div>
        ),
      },
      {
        id: "type",
        header: t("result.columns.type"),
        cell: ({ row }) => (
          <span className="whitespace-nowrap text-text-secondary">
            {tContent(row.original.type)}
          </span>
        ),
      },
      {
        id: "answer",
        header: t("result.columns.answer"),
        cell: ({ row }) => (
          <div className="max-w-md min-w-44">
            <TrialResultAnswerCell row={row.original} />
          </div>
        ),
      },
      {
        id: "outcome",
        header: t("result.columns.outcome"),
        cell: ({ row }) => (
          <StatusChip tone={TRIAL_OUTCOME_TONE[row.original.outcome]}>
            {t(`result.outcome.${row.original.outcome}`)}
          </StatusChip>
        ),
      },
      {
        id: "points",
        header: () => <span className="block text-end">{t("result.columns.points")}</span>,
        cell: ({ row }) => (
          <span className="block text-end whitespace-nowrap text-text tabular-nums">
            {row.original.pendingReview
              ? t("result.scorePendingCell")
              : t("result.pointsPair", {
                  earned: format.number(row.original.earnedPoints, DISPLAY_NUMBER_FORMAT),
                  max: format.number(row.original.maxPoints, DISPLAY_NUMBER_FORMAT),
                })}
          </span>
        ),
      },
    ],
    [t, tContent, format],
  );

  const cards = useMemo<TableCards<GradedQuestionRow>>(
    () => ({
      render: (row) => (
        <ListCard
          title={t("result.questionLabel", { number: format.number(row.number) })}
          caption={tContent(row.type)}
          status={
            <StatusChip tone={TRIAL_OUTCOME_TONE[row.outcome]}>
              {t(`result.outcome.${row.outcome}`)}
            </StatusChip>
          }
          tags={
            row.partName ? (
              <ListCardTag>
                <span data-exam-content>{row.partName}</span>
              </ListCardTag>
            ) : undefined
          }
          meta={[
            { label: t("result.columns.answer"), value: <TrialResultAnswerCell row={row} /> },
            {
              label: t("result.columns.points"),
              value: row.pendingReview
                ? t("result.scorePendingCell")
                : t("result.pointsPair", {
                    earned: format.number(row.earnedPoints, DISPLAY_NUMBER_FORMAT),
                    max: format.number(row.maxPoints, DISPLAY_NUMBER_FORMAT),
                  }),
            },
          ]}
        />
      ),
    }),
    [t, tContent, format],
  );

  return (
    <div className="@container flex flex-col gap-5">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div className="flex min-w-0 flex-wrap items-center gap-2.5">
          <Button asChild variant="secondary" size="sm">
            <Link href={backHref}>
              <ArrowLeft aria-hidden />
              {t("result.backToPreview")}
            </Link>
          </Button>
          <h1
            ref={headingRef}
            tabIndex={-1}
            className={cn("rounded-sm text-page-title font-bold tracking-tight text-text", focusRing)}
          >
            {t("result.heading")}
          </h1>
          <StatusChip tone="neutral">{t("result.notRecordedBadge")}</StatusChip>
        </div>

        <Button type="button" variant="secondary" onClick={onRetake}>
          <RotateCcw aria-hidden />
          {t("result.retakeButton")}
        </Button>
      </div>

      <div className="grid gap-4 @3xl:grid-cols-2">
        <Card className="flex flex-col gap-3">
          <p className="text-caption font-medium text-text-muted">{t("result.scoreLabel")}</p>
          <p className="text-h2 font-bold text-text tabular-nums">
            {hasPending
              ? t("result.scorePendingSummary", {
                  earned: format.number(objective.earnedPoints, DISPLAY_NUMBER_FORMAT),
                  max: format.number(objective.maxPoints, DISPLAY_NUMBER_FORMAT),
                })
              : t("result.scoreValue", {
                  score: format.number(grade.rawScore ?? 0, DISPLAY_NUMBER_FORMAT),
                  max: format.number(grade.maxScore ?? 0, DISPLAY_NUMBER_FORMAT),
                })}
          </p>
          <p className="text-caption text-text-secondary">
            {hasPending ? t("result.scorePendingNote") : t("result.scoreNote")}
          </p>
        </Card>

        {stats && (
          <Card className="flex flex-col gap-3">
            <p className="text-caption font-medium text-text-muted">{t("result.statsTitle")}</p>
            <div className="flex flex-wrap gap-2">
              <StatusChip tone="success">
                {t("result.statCorrect", { count: format.number(stats.correctCount ?? 0) })}
              </StatusChip>
              {(stats.partialCount ?? 0) > 0 && (
                <StatusChip tone="warning">
                  {t("result.statPartial", { count: format.number(stats.partialCount ?? 0) })}
                </StatusChip>
              )}
              <StatusChip tone="danger">
                {t("result.statWrong", { count: format.number(stats.wrongCount ?? 0) })}
              </StatusChip>
              <StatusChip tone="neutral">
                {t("result.statBlank", { count: format.number(stats.blankCount ?? 0) })}
              </StatusChip>
              {(stats.pendingReviewCount ?? 0) > 0 && (
                <StatusChip tone="warning">
                  {t("result.statPending", { count: format.number(stats.pendingReviewCount ?? 0) })}
                </StatusChip>
              )}
            </div>
          </Card>
        )}
      </div>

      {partSummaries.length > 0 && (
        <Card className="flex flex-col gap-3">
          <h2 className="text-title font-semibold text-text">{t("result.partsTitle")}</h2>
          <div className="grid gap-3 @2xl:grid-cols-2 @4xl:grid-cols-3">
            {partSummaries.map((summary) => (
              <div
                key={summary.partId || summary.partName}
                className="flex flex-col gap-1 rounded-lg border border-border bg-subtle px-3.5 py-2.5"
              >
                <span data-exam-content className="text-label font-semibold text-text">
                  {summary.partName}
                </span>
                <span className="text-caption text-text-secondary tabular-nums">
                  {summary.hasPending
                    ? summary.objectiveCount === 0
                      ? t("result.partEssayOnlyPending")
                      : t("result.partPendingSummary", {
                          earned: format.number(summary.earnedPoints, DISPLAY_NUMBER_FORMAT),
                          max: format.number(summary.maxPoints, DISPLAY_NUMBER_FORMAT),
                        })
                    : t("result.pointsPair", {
                        earned: format.number(summary.earnedPoints, DISPLAY_NUMBER_FORMAT),
                        max: format.number(summary.maxPoints, DISPLAY_NUMBER_FORMAT),
                      })}
                </span>
              </div>
            ))}
          </div>
        </Card>
      )}

      <Card padding="none" className="overflow-hidden">
        <div className="border-b border-border px-4 py-3">
          <h2 className="text-title font-semibold text-text">{t("result.tableTitle")}</h2>
        </div>
        <Table
          aria-label={t("result.tableAriaLabel")}
          columns={columns}
          data={tableRows}
          getRowId={(row) => row.questionId}
          cards={cards}
          empty={<EmptyState headingLevel={3} title={t("result.empty")} className="py-8" />}
        />
      </Card>
    </div>
  );
}

function TrialResultAnswerCell({ row }: { row: GradedQuestionRow }) {
  const t = useTranslations("exams.trial");
  const format = useFormatter();

  if (row.type === "TRUE_FALSE") {
    const entries = extractTrueFalseEntries(
      row.descriptor?.question,
      row.answer,
      row.gradedItem,
    );
    if (entries.length === 0) {
      return <span className="text-text-muted">{t("result.blank")}</span>;
    }
    return (
      <span className="flex flex-col gap-1">
        {entries.map((entry) => {
          const chosenLabel =
            entry.chosen === null
              ? t("result.blank")
              : entry.chosen
                ? t("result.tfTrue")
                : t("result.tfFalse");
          return (
            <span key={entry.id} className="inline-flex flex-wrap items-center gap-1.5 text-caption">
              <span className="font-semibold text-text">
                {t("result.statementPrefix", { letter: entry.letter })}
              </span>
              <span className="text-text-secondary">{chosenLabel}</span>
              <StatusChip tone={TRIAL_OUTCOME_TONE[entry.outcome]}>
                {t(`result.outcome.${entry.outcome}`)}
              </StatusChip>
            </span>
          );
        })}
      </span>
    );
  }

  if (row.type === "ESSAY") {
    const essay = row.answer as KitEssayAnswer | undefined;
    const html = essay?.html ?? "";
    const words =
      row.gradedItem.gradingDetail?.wordCount ??
      (typeof essay?.wordCount === "number" ? essay.wordCount : countWords(html));
    return (
      <span className="inline-flex flex-wrap items-center gap-2 text-caption">
        <span className="font-medium text-text tabular-nums">
          {words > 0 ? t("result.essayWords", { count: format.number(words) }) : t("result.blank")}
        </span>
        {row.pendingReview && (
          <StatusChip tone="warning">{t("result.outcome.PENDING_REVIEW")}</StatusChip>
        )}
      </span>
    );
  }

  if (!row.answer || row.outcome === "BLANK") {
    return <span className="text-text-muted">{t("result.blank")}</span>;
  }

  switch (row.type) {
    case "SINGLE_CHOICE": {
      const a = row.answer as KitSingleChoiceAnswer;
      if (!a.optionId) return <span className="text-text-muted">{t("result.blank")}</span>;
      const letters = row.descriptor ? optionLetters(row.descriptor.question) : new Map<string, string>();
      const letter = letters.get(a.optionId) ?? a.optionId;
      return <span className="font-medium text-text">{letter}</span>;
    }
    case "MULTIPLE_CHOICE": {
      const a = row.answer as KitMultipleChoiceAnswer;
      const ids = a.optionIds ?? [];
      if (ids.length === 0) return <span className="text-text-muted">{t("result.blank")}</span>;
      const letters = row.descriptor ? optionLetters(row.descriptor.question) : new Map<string, string>();
      return (
        <span className="font-medium text-text">
          {ids.map((id) => letters.get(id) ?? id).join(", ")}
        </span>
      );
    }
    case "SHORT_ANSWER": {
      const a = row.answer as KitShortAnswerAnswer;
      const txt = (a.text ?? "").trim();
      return txt ? (
        <span data-exam-content className="wrap-anywhere text-text">
          {txt}
        </span>
      ) : (
        <span className="text-text-muted">{t("result.blank")}</span>
      );
    }
    case "CLOZE": {
      const a = row.answer as KitClozeAnswer;
      const entries = extractClozeResultEntries(row.descriptor?.question, a.blanks);
      if (entries.length === 0) return <span className="text-text-muted">{t("result.blank")}</span>;
      return (
        <span className="wrap-anywhere text-text">
          {entries.map((entry, idx) => (
            <span key={entry.index}>
              {idx > 0 && " · "}
              {t("result.clozeItem", { index: entry.index })}{" "}
              <span data-exam-content>{entry.value}</span>
            </span>
          ))}
        </span>
      );
    }
    case "MATCHING": {
      const a = row.answer as KitMatchingAnswer;
      const pairs = Object.entries(a.pairs ?? {}).filter(
        (entry): entry is [string, string] => typeof entry[1] === "string" && entry[1].length > 0,
      );
      if (pairs.length === 0) return <span className="text-text-muted">{t("result.blank")}</span>;
      const maps = row.descriptor
        ? matchingLabels(row.descriptor.question)
        : { leftLabels: new Map<string, string>(), rightLabels: new Map<string, string>() };
      return (
        <span className="wrap-anywhere text-text">
          {pairs
            .map(([l, r]) => `${maps.leftLabels.get(l) ?? l} → ${maps.rightLabels.get(r) ?? r}`)
            .join(", ")}
        </span>
      );
    }
    default:
      return <span className="text-text-muted">{t("result.blank")}</span>;
  }
}
