"use client";

import {
  CardList,
  CardListItem,
  ListCard,
  ListCardTag,
  StatusChip,
  cn,
  focusRingInset,
  useCardMode,
} from "@thi360/ui";
import { PenLine } from "lucide-react";
import { useTranslations } from "next-intl";
import type { AttemptResultSegment } from "./api";
import { formatScoreValue } from "./format";
import { useInlineOverflow } from "./use-inline-overflow";

const SEGMENTS_CARD_BREAKPOINT = 680;

export interface SegmentBreakdownSectionProps {
  segments: AttemptResultSegment[];
  hasPendingAttempt: boolean;
  locale: string;
}

/**
 * Displays per-display-segment scores and outcome buckets (including PARTIAL) in a responsive
 * table on desktop and CardList on narrow screens (BS 17.6, FS-7 AC-12).
 */
export function SegmentBreakdownSection({
  segments,
  hasPendingAttempt,
  locale,
}: SegmentBreakdownSectionProps) {
  const t = useTranslations("result.segments");
  const statsT = useTranslations("result.stats");
  const [containerRef, cardMode] = useCardMode<HTMLDivElement>(
    segments.length > 0,
    SEGMENTS_CARD_BREAKPOINT,
  );
  const [tableScrollerRef, tableOverflows] = useInlineOverflow(segments.length > 0 && !cardMode);

  const hasPendingSegment = segments.some(
    (seg) => Boolean(seg.pendingReview) || (seg.stats?.pendingReviewCount ?? 0) > 0,
  );

  function formatRange(segment: AttemptResultSegment): string | null {
    if (segment.fromPosition == null || segment.toPosition == null) {
      return null;
    }
    return t("range", { from: segment.fromPosition, to: segment.toPosition });
  }

  function renderSegmentScoreText(segment: AttemptResultSegment): string | null {
    if (segment.score == null) return null;
    if (segment.maxPoints != null) {
      return `${formatScoreValue(segment.score, locale)}/${formatScoreValue(segment.maxPoints, locale)}`;
    }
    return formatScoreValue(segment.score, locale);
  }

  return (
    <section
      aria-labelledby="result-segments-title"
      className="flex flex-col gap-4 rounded-2xl border border-border bg-surface p-5 sm:p-6"
    >
      <div className="flex flex-wrap items-center justify-between gap-2">
        <div className="flex items-center gap-3">
          <span aria-hidden className="h-6 w-1 shrink-0 rounded-full bg-primary-600" />
          <h2 id="result-segments-title" className="text-h3 font-bold text-text">
            {t("title")}
          </h2>
        </div>
        <span className="text-caption text-text-muted">
          {hasPendingAttempt ? t("pendingDescription") : t("description")}
        </span>
      </div>

      <div ref={containerRef} className="min-w-0">
        {cardMode ? (
          <CardList aria-label={t("title")} className="rounded-xl border border-border bg-surface">
            {segments.map((segment, index) => {
              const correct = segment.stats?.correctCount ?? 0;
              const partial = segment.stats?.partialCount ?? 0;
              const wrong = segment.stats?.wrongCount ?? 0;
              const blank = segment.stats?.blankCount ?? 0;
              const pending = segment.stats?.pendingReviewCount ?? 0;
              const rangeText = formatRange(segment);
              const scoreText = renderSegmentScoreText(segment);
              return (
                <CardListItem key={`${segment.partCode ?? ""}-${segment.code ?? index}`}>
                  <div className="min-w-0 flex-1">
                    <ListCard
                      title={<span data-exam-content>{segment.name || segment.code || "—"}</span>}
                      caption={rangeText ?? undefined}
                      status={
                        segment.pendingReview ? (
                          <StatusChip tone="warning">{statsT("pending")}</StatusChip>
                        ) : scoreText ? (
                          <span className="text-label font-bold text-text tabular-nums">
                            {t("segmentScoreLabel")}: {scoreText}
                          </span>
                        ) : undefined
                      }
                      tags={
                        segment.pendingReview ? (
                          <ListCardTag>
                            <span className="inline-flex items-center gap-1">
                              <PenLine aria-hidden className="size-3" />
                              {t("manualChip")}
                            </span>
                          </ListCardTag>
                        ) : undefined
                      }
                      meta={[
                        { label: t("columns.correct"), value: correct },
                        ...(partial > 0 ? [{ label: t("columns.partial"), value: partial }] : []),
                        { label: t("columns.wrong"), value: wrong },
                        { label: t("columns.blank"), value: blank },
                        ...(pending > 0 ? [{ label: t("columns.pending"), value: pending }] : []),
                      ]}
                    />
                  </div>
                </CardListItem>
              );
            })}
          </CardList>
        ) : (
          <div
            ref={tableScrollerRef}
            role={tableOverflows ? "region" : undefined}
            aria-label={tableOverflows ? t("title") : undefined}
            tabIndex={tableOverflows ? 0 : undefined}
            className={cn("overflow-x-auto rounded-xl border border-border", focusRingInset)}
          >
            <table
              aria-labelledby="result-segments-title"
              className="w-full border-collapse text-left tabular-nums"
            >
              <thead>
                <tr className="border-b border-border bg-table-head text-caption font-semibold text-text-muted">
                  <th scope="col" className="px-4 py-3">
                    {t("columns.segment")}
                  </th>
                  <th scope="col" className="px-3 py-3 whitespace-nowrap">
                    {t("columns.range")}
                  </th>
                  <th scope="col" className="px-3 py-3 text-right">
                    {t("columns.correct")}
                  </th>
                  <th scope="col" className="px-3 py-3 text-right">
                    {t("columns.partial")}
                  </th>
                  <th scope="col" className="px-3 py-3 text-right">
                    {t("columns.wrong")}
                  </th>
                  <th scope="col" className="px-3 py-3 text-right">
                    {t("columns.blank")}
                  </th>
                  {hasPendingSegment && (
                    <th scope="col" className="px-3 py-3 text-right">
                      {t("columns.pending")}
                    </th>
                  )}
                  <th scope="col" className="px-4 py-3 text-right">
                    {t("columns.score")}
                  </th>
                </tr>
              </thead>
              <tbody className="divide-y divide-border">
                {segments.map((segment, index) => {
                  const rangeText = formatRange(segment);
                  const scoreText = renderSegmentScoreText(segment);
                  return (
                    <tr key={`${segment.partCode ?? ""}-${segment.code ?? index}`}>
                      <th scope="row" className="px-4 py-3 font-semibold text-text">
                        <span data-exam-content>{segment.name || segment.code || "—"}</span>
                      </th>
                      <td className="px-3 py-3 whitespace-nowrap text-text-secondary">
                        {rangeText ?? "—"}
                      </td>
                      <td className="px-3 py-3 text-right text-text-secondary">
                        {segment.stats?.correctCount ?? 0}
                      </td>
                      <td className="px-3 py-3 text-right text-text-secondary">
                        {segment.stats?.partialCount ?? 0}
                      </td>
                      <td className="px-3 py-3 text-right text-text-secondary">
                        {segment.stats?.wrongCount ?? 0}
                      </td>
                      <td className="px-3 py-3 text-right text-text-secondary">
                        {segment.stats?.blankCount ?? 0}
                      </td>
                      {hasPendingSegment && (
                        <td className="px-3 py-3 text-right text-text-secondary">
                          {segment.stats?.pendingReviewCount ?? 0}
                        </td>
                      )}
                      <td className="px-4 py-3 text-right whitespace-nowrap">
                        {segment.pendingReview ? (
                          <div className="inline-flex flex-wrap items-center justify-end gap-1.5">
                            <span className="inline-flex h-6 items-center gap-1 rounded-full border border-border bg-subtle px-2.5 text-caption font-medium text-text-secondary">
                              <PenLine aria-hidden className="size-3" />
                              {t("manualChip")}
                            </span>
                            <StatusChip tone="warning">{statsT("pending")}</StatusChip>
                          </div>
                        ) : scoreText ? (
                          <span className="font-bold text-text">{scoreText}</span>
                        ) : (
                          "—"
                        )}
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>
        )}
      </div>
    </section>
  );
}
