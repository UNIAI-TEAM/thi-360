"use client";

import { useQuery } from "@tanstack/react-query";
import {
  Button,
  CardList,
  CardListItem,
  EmptyState,
  ListCard,
  ListCardTag,
  Skeleton,
  StatusChip,
  StatusPage,
  cn,
  focusRing,
  focusRingInset,
  useCardMode,
} from "@thi360/ui";
import {
  AlertCircle,
  ArrowLeft,
  Ban,
  Clock,
  Eye,
  Info,
  LoaderCircle,
  PenLine,
  RefreshCw,
  SearchX,
  ShieldCheck,
  TriangleAlert,
  WifiOff,
} from "lucide-react";
import { useLocale, useTranslations } from "next-intl";
import Link from "next/link";
import { useEffect, useMemo, useState } from "react";
import { type HeaderAccount, StudentHeader } from "@/components/student-header";
import { loadSession, SESSION_QUERY_KEY, type SessionLoader } from "@/features/session/api";
import { ApiStatusError } from "@/lib/api/client";
import { adminUrl } from "@/lib/env";
import {
  ATTEMPT_RESULT_QUERY_KEY,
  CATALOG_OPTIONS_QUERY_KEY,
  GRADING_POLL_INTERVAL_MS,
  GRADING_POLL_TIMEOUT_MS,
  loadAttemptResult,
  loadExamKindOptions,
  loadSubjectOptions,
  type AttemptResultLoader,
  type AttemptResultPart,
  type AttemptResultResponse,
  type AttemptResultSubject,
  type AttemptResultUnitStats,
  type CatalogOptionResponse,
  type CatalogOptionsLoader,
} from "./api";
import {
  buildCatalogMap,
  computeActualDuration,
  formatAttemptChoices,
  formatAttemptDateTime,
  formatScoreValue,
  isKnownEndReason,
  isKnownResultState,
  isKnownScope,
  isKnownVactComponentCode,
  isUuid,
  resolveAttemptDisplayScore,
  resolveCatalogLabel,
  resolveResultStatusTone,
} from "./format";
import { ThinkingSubsections } from "./thinking-subsections";
import { useInlineOverflow } from "./use-inline-overflow";

const SUBJECTS_CARD_BREAKPOINT = 680;

function StatBuckets({
  stats,
  locale,
  skippedEmptyPartCount,
  showPendingBucket = false,
}: {
  stats: AttemptResultUnitStats;
  locale: string;
  skippedEmptyPartCount?: number;
  showPendingBucket?: boolean;
}) {
  const t = useTranslations("result.stats");
  const correct = stats.correctCount ?? 0;
  const partial = stats.partialCount ?? 0;
  const wrong = stats.wrongCount ?? 0;
  const blank = stats.blankCount ?? 0;
  const pending = stats.pendingReviewCount ?? 0;
  const total = stats.totalCount ?? correct + partial + wrong + blank + pending;
  const denom = total > 0 ? total : 1;

  const buckets = [
    { key: "correct", label: t("correct"), count: correct, dot: "bg-success", highlight: false },
    { key: "partial", label: t("partial"), count: partial, dot: "bg-warning", highlight: false },
    { key: "wrong", label: t("wrong"), count: wrong, dot: "bg-danger", highlight: false },
    { key: "blank", label: t("blank"), count: blank, dot: "border border-border-strong bg-subtle", highlight: false },
    ...(pending > 0 || showPendingBucket
      ? [{ key: "pending", label: t("pending"), count: pending, dot: "bg-info", highlight: pending > 0 }]
      : []),
  ];

  return (
    <div className="flex min-w-0 flex-[2_1_28rem] flex-col gap-3.5">
      <div className="grid grid-cols-[repeat(auto-fit,minmax(7rem,1fr))] gap-3">
        {buckets.map((bucket) => (
          <div
            key={bucket.key}
            className={cn(
              "flex flex-col gap-0.5 rounded-xl border px-3.5 py-3",
              bucket.highlight ? "border-warning bg-warning-bg" : "border-border bg-surface",
            )}
          >
            <span className="flex items-center gap-2 text-caption font-medium text-text-secondary">
              <span aria-hidden className={cn("size-2.5 shrink-0 rounded-sm", bucket.dot)} />
              {bucket.label}
            </span>
            <span className="text-h2 leading-8 font-bold text-text tabular-nums">
              {formatScoreValue(bucket.count, locale)}
            </span>
          </div>
        ))}
      </div>

      <div aria-hidden className="flex h-2.5 overflow-hidden rounded-full bg-subtle">
        {correct > 0 && <span className="bg-success" style={{ width: `${(correct / denom) * 100}%` }} />}
        {partial > 0 && <span className="bg-warning" style={{ width: `${(partial / denom) * 100}%` }} />}
        {wrong > 0 && <span className="bg-danger" style={{ width: `${(wrong / denom) * 100}%` }} />}
        {pending > 0 && <span className="bg-info" style={{ width: `${(pending / denom) * 100}%` }} />}
      </div>

      <p className="text-caption text-text-muted tabular-nums">
        {t("denominator", { total: formatScoreValue(total, locale) })}
        {skippedEmptyPartCount ? ` (${formatScoreValue(skippedEmptyPartCount, locale)})` : ""}
      </p>
    </div>
  );
}

function PartBreakdownCard({
  part,
  endReason,
  locale,
}: {
  part: AttemptResultPart;
  endReason: AttemptResultResponse["endReason"];
  locale: string;
}) {
  const t = useTranslations("result.parts");
  const statsT = useTranslations("result.stats");

  const stats = part.stats;
  const correct = stats?.correctCount ?? 0;
  const partial = stats?.partialCount ?? 0;
  const wrong = stats?.wrongCount ?? 0;
  const blank = stats?.blankCount ?? 0;
  const pending = stats?.pendingReviewCount ?? 0;
  const total = stats?.totalCount ?? Math.max(1, correct + partial + wrong + blank + pending);

  if (part.skipped) {
    const skipReason =
      endReason === "CHOICE_TIMEOUT"
        ? t("skippedChoiceTimeout")
        : endReason === "FAULT_CLOSED"
          ? t("skippedFaultClosed")
          : t("skippedFallback");
    return (
      <article className="flex flex-col gap-3 rounded-xl border border-warning bg-warning-bg p-4 sm:p-5">
        <div className="flex flex-wrap items-start justify-between gap-3">
          <div className="flex min-w-0 flex-1 basis-64 flex-col gap-1.5">
            <div className="flex flex-wrap items-center gap-2">
              <h3 data-exam-content className="text-title font-semibold text-text">
                {part.partName || part.partCode}
              </h3>
              <span className="inline-flex h-6 items-center gap-1.5 rounded-full border border-warning bg-surface px-2.5 text-caption font-semibold text-warning">
                <Ban aria-hidden className="size-3.5 shrink-0" />
                {t("skippedBadge")}
              </span>
            </div>
            <p className="text-body text-text-secondary">{skipReason}</p>
          </div>
          {part.maxScore != null && (
            <div className="text-right tabular-nums">
              <div className="text-caption text-text-secondary">{t("partScoreLabel")}</div>
              <div className="text-h3 font-bold text-text">
                {formatScoreValue(0, locale)}
                <span className="text-body font-medium text-text-secondary">
                  /{formatScoreValue(part.maxScore, locale)}
                </span>
              </div>
            </div>
          )}
        </div>
      </article>
    );
  }

  if (part.pendingReview) {
    return (
      <article className="flex flex-col gap-2.5 rounded-xl border border-warning bg-warning-bg p-4 sm:p-5">
        <div className="flex flex-wrap items-start justify-between gap-3">
          <div className="flex min-w-0 flex-1 basis-64 flex-col gap-1.5">
            <div className="flex flex-wrap items-center gap-2">
              <h3 data-exam-content className="text-title font-semibold text-text">
                {part.partName || part.partCode}
              </h3>
              <span className="inline-flex h-6 items-center gap-1.5 rounded-full border border-border bg-surface px-2.5 text-caption font-medium text-text-secondary">
                <PenLine aria-hidden className="size-3.5 shrink-0" />
                {t("manualChip")}
              </span>
            </div>
            {part.maxScore != null && (
              <p className="text-body text-text-secondary tabular-nums">
                {t("maxScoreNote", { max: formatScoreValue(part.maxScore, locale) })}
              </p>
            )}
          </div>
          <span className="inline-flex h-8 items-center gap-1.5 rounded-full border border-warning bg-surface px-3 text-label font-semibold text-warning">
            <Clock aria-hidden className="size-4 shrink-0" />
            {statsT("pending")}
          </span>
        </div>
      </article>
    );
  }

  return (
    <article className="flex flex-col gap-3 rounded-xl border border-border bg-surface p-4 sm:p-5">
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div className="flex min-w-0 flex-1 basis-64 flex-col gap-1">
          <h3 data-exam-content className="text-title font-semibold text-text">
            {part.partName || part.partCode}
          </h3>
          {stats && (
            <div className="flex flex-wrap gap-x-4 gap-y-1 text-caption text-text-secondary tabular-nums">
              <span>
                {statsT("correct")}: <strong className="font-semibold text-text">{correct}</strong>
              </span>
              {partial > 0 && (
                <span>
                  {statsT("partial")}: <strong className="font-semibold text-text">{partial}</strong>
                </span>
              )}
              <span>
                {statsT("wrong")}: <strong className="font-semibold text-text">{wrong}</strong>
              </span>
              <span>
                {statsT("blank")}: <strong className="font-semibold text-text">{blank}</strong>
              </span>
            </div>
          )}
        </div>
        {part.score != null && part.maxScore != null && (
          <div className="text-right tabular-nums">
            <div className="text-caption text-text-muted">{t("partScoreLabel")}</div>
            <div className="text-h3 font-bold text-text">
              {formatScoreValue(part.score, locale)}
              <span className="text-body font-medium text-text-muted">
                /{formatScoreValue(part.maxScore, locale)}
              </span>
            </div>
          </div>
        )}
      </div>
      {stats && (
        <div aria-hidden className="flex h-2 overflow-hidden rounded-full bg-subtle">
          {correct > 0 && <span className="bg-success" style={{ width: `${(correct / total) * 100}%` }} />}
          {partial > 0 && <span className="bg-warning" style={{ width: `${(partial / total) * 100}%` }} />}
          {wrong > 0 && <span className="bg-danger" style={{ width: `${(wrong / total) * 100}%` }} />}
        </div>
      )}
    </article>
  );
}

function SubjectBreakdownSection({
  subjects,
  hasPendingAttempt,
  subjectMap,
  locale,
}: {
  subjects: AttemptResultSubject[];
  hasPendingAttempt: boolean;
  subjectMap: ReadonlyMap<string, CatalogOptionResponse>;
  locale: string;
}) {
  const t = useTranslations("result.subjects");
  const statsT = useTranslations("result.stats");
  const [containerRef, cardMode] = useCardMode<HTMLDivElement>(subjects.length > 0, SUBJECTS_CARD_BREAKPOINT);
  const [tableScrollerRef, tableOverflows] = useInlineOverflow(subjects.length > 0 && !cardMode);

  function renderSubjectName(subject: AttemptResultSubject) {
    if (subject.code && subjectMap.has(subject.code)) {
      return <span>{resolveCatalogLabel(subject.code, subjectMap, locale)}</span>;
    }
    if (subject.name) {
      return <span data-exam-content>{subject.name}</span>;
    }
    return <span>{subject.code ?? "—"}</span>;
  }

  return (
    <section
      aria-labelledby="result-subjects-title"
      className="flex flex-col gap-4 rounded-2xl border border-border bg-surface p-5 sm:p-6"
    >
      <div className="flex flex-wrap items-center justify-between gap-2">
        <div className="flex items-center gap-3">
          <span aria-hidden className="h-6 w-1 shrink-0 rounded-full bg-primary-600" />
          <h2 id="result-subjects-title" className="text-h3 font-bold text-text">
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
            {subjects.map((subject, index) => {
              const correct = subject.stats?.correctCount ?? 0;
              const partial = subject.stats?.partialCount ?? 0;
              const wrong = subject.stats?.wrongCount ?? 0;
              const blank = subject.stats?.blankCount ?? 0;
              const pending = subject.stats?.pendingReviewCount ?? 0;
              return (
                <CardListItem key={subject.code ?? index}>
                  <div className="min-w-0 flex-1">
                    <ListCard
                      title={renderSubjectName(subject)}
                      status={
                        subject.pendingReview ? (
                          <StatusChip tone="warning">{statsT("pending")}</StatusChip>
                        ) : subject.score != null && subject.maxScore != null ? (
                          <span className="text-label font-bold text-text tabular-nums">
                            {t("subjectScoreLabel")}: {formatScoreValue(subject.score, locale)}/
                            {formatScoreValue(subject.maxScore, locale)}
                          </span>
                        ) : undefined
                      }
                      tags={
                        subject.pendingReview ? (
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
            <table aria-labelledby="result-subjects-title" className="w-full border-collapse text-left tabular-nums">
              <thead>
                <tr className="border-b border-border bg-table-head text-caption font-semibold text-text-muted">
                  <th scope="col" className="px-4 py-3">
                    {t("columns.subject")}
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
                  <th scope="col" className="px-3 py-3 text-right">
                    {t("columns.pending")}
                  </th>
                  <th scope="col" className="px-4 py-3 text-right">
                    {t("columns.score")}
                  </th>
                </tr>
              </thead>
              <tbody className="divide-y divide-border">
                {subjects.map((subject, index) => (
                  <tr key={subject.code ?? index}>
                    <th scope="row" className="px-4 py-3 font-semibold text-text">
                      {renderSubjectName(subject)}
                    </th>
                    <td className="px-3 py-3 text-right text-text-secondary">
                      {subject.stats?.correctCount ?? 0}
                    </td>
                    <td className="px-3 py-3 text-right text-text-secondary">
                      {subject.stats?.partialCount ?? 0}
                    </td>
                    <td className="px-3 py-3 text-right text-text-secondary">
                      {subject.stats?.wrongCount ?? 0}
                    </td>
                    <td className="px-3 py-3 text-right text-text-secondary">
                      {subject.stats?.blankCount ?? 0}
                    </td>
                    <td className="px-3 py-3 text-right text-text-secondary">
                      {subject.stats?.pendingReviewCount ?? 0}
                    </td>
                    <td className="px-4 py-3 text-right whitespace-nowrap">
                      {subject.pendingReview ? (
                        <div className="inline-flex flex-wrap items-center justify-end gap-1.5">
                          <span className="inline-flex h-6 items-center gap-1 rounded-full border border-border bg-subtle px-2.5 text-caption font-medium text-text-secondary">
                            <PenLine aria-hidden className="size-3" />
                            {t("manualChip")}
                          </span>
                          <StatusChip tone="warning">{statsT("pending")}</StatusChip>
                        </div>
                      ) : subject.score != null && subject.maxScore != null ? (
                        <span className="font-bold text-text">
                          {formatScoreValue(subject.score, locale)}/{formatScoreValue(subject.maxScore, locale)}
                        </span>
                      ) : (
                        "—"
                      )}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </div>
    </section>
  );
}

export function AttemptResultView({
  attemptId,
  loadCurrentSession = loadSession,
  loadResult = loadAttemptResult,
  loadExamKinds = loadExamKindOptions,
  loadSubjects = loadSubjectOptions,
}: {
  attemptId: string;
  loadCurrentSession?: SessionLoader;
  loadResult?: AttemptResultLoader;
  loadExamKinds?: CatalogOptionsLoader;
  loadSubjects?: CatalogOptionsLoader;
}) {
  const t = useTranslations("result");
  const common = useTranslations("common");
  const locale = useLocale();

  const [timedOutToken, setTimedOutToken] = useState<string | null>(null);
  const [reloadCount, setReloadCount] = useState(0);

  const sessionQuery = useQuery({
    queryKey: SESSION_QUERY_KEY,
    queryFn: loadCurrentSession,
    staleTime: 60_000,
  });

  const userId = sessionQuery.data?.id ?? null;
  const isStudent = sessionQuery.data?.access === "EXAM";
  const validAttemptId = isUuid(attemptId);
  const normalizedAttemptId = validAttemptId ? attemptId.trim() : attemptId;

  const examKindsQuery = useQuery({
    queryKey: CATALOG_OPTIONS_QUERY_KEY("EXAM"),
    queryFn: loadExamKinds,
    enabled: isStudent,
    staleTime: 300_000,
  });

  const subjectsQuery = useQuery({
    queryKey: CATALOG_OPTIONS_QUERY_KEY("SUBJECT"),
    queryFn: loadSubjects,
    enabled: isStudent,
    staleTime: 300_000,
  });

  const resultQuery = useQuery({
    queryKey: ATTEMPT_RESULT_QUERY_KEY(normalizedAttemptId, userId),
    queryFn: () => loadResult(normalizedAttemptId),
    enabled: isStudent && validAttemptId,
    retry: false,
    refetchInterval: (query) => {
      if (query.state.data?.state === "GRADING") {
        const token = `${normalizedAttemptId}:${reloadCount}`;
        if (timedOutToken !== token) {
          return GRADING_POLL_INTERVAL_MS;
        }
      }
      return false;
    },
  });

  const isGrading = resultQuery.data?.state === "GRADING";
  const currentGradingToken = isGrading ? `${normalizedAttemptId}:${reloadCount}` : null;
  const gradingTimedOut = currentGradingToken !== null && timedOutToken === currentGradingToken;

  useEffect(() => {
    if (!currentGradingToken || gradingTimedOut) {
      return;
    }
    const timer = window.setTimeout(() => {
      setTimedOutToken(currentGradingToken);
    }, GRADING_POLL_TIMEOUT_MS);
    return () => window.clearTimeout(timer);
  }, [currentGradingToken, gradingTimedOut]);

  const examKindMap = useMemo(() => buildCatalogMap(examKindsQuery.data ?? []), [examKindsQuery.data]);
  const subjectMap = useMemo(() => buildCatalogMap(subjectsQuery.data ?? []), [subjectsQuery.data]);

  const account: HeaderAccount = sessionQuery.data
    ? { kind: "user", session: sessionQuery.data }
    : sessionQuery.isLoading && !sessionQuery.isPaused
      ? { kind: "loading" }
      : { kind: "none" };

  const error = resultQuery.error;
  const isNotFound = !validAttemptId || (error instanceof ApiStatusError && error.status === 404);
  const isNotFinished =
    error instanceof ApiStatusError && error.status === 409 && error.code === "ATTEMPT_NOT_FINISHED";

  const result = resultQuery.data;
  const isSessionOffline = sessionQuery.isPaused && !sessionQuery.data;
  const isOffline = isSessionOffline || (isStudent && validAttemptId && resultQuery.isPaused && !result);

  function handleReloadGrading() {
    setReloadCount((count) => count + 1);
    void resultQuery.refetch();
  }

  return (
    <div className="min-h-screen bg-page text-text">
      <StudentHeader account={account} />

      <main className="mx-auto flex max-w-[1170px] flex-col gap-6 px-4 py-8 sm:px-6 sm:py-10">
        <Link
          href="/history"
          className={cn(
            "inline-flex min-h-11 items-center gap-2 self-start rounded-sm font-medium text-primary-text hover:underline",
            focusRing,
          )}
        >
          <ArrowLeft aria-hidden className="size-4" />
          {t("backToHistory")}
        </Link>

        {sessionQuery.isLoading && !sessionQuery.isPaused && (
          <div role="status" aria-busy="true" aria-label={common("loading")} className="flex flex-col gap-4">
            <Skeleton className="h-9.5 w-72 max-w-full" />
            <Skeleton className="h-60 w-full rounded-2xl" />
            <Skeleton className="h-44 w-full rounded-2xl" />
          </div>
        )}

        {sessionQuery.isError && !sessionQuery.isPaused && !sessionQuery.data && (
          <div className="rounded-xl border border-border bg-surface">
            <EmptyState
              headingLevel={1}
              icon={<TriangleAlert />}
              title={t("errorTitle")}
              description={t("errorDescription")}
              action={
                <Button type="button" className="h-11" onClick={() => void sessionQuery.refetch()}>
                  {common("retry")}
                </Button>
              }
            />
          </div>
        )}

        {sessionQuery.data && sessionQuery.data.access === "MANAGE" && (
          <div className="rounded-xl border border-border bg-surface">
            <EmptyState
              headingLevel={1}
              icon={<ShieldCheck />}
              title={t("adminNotice.title")}
              description={t("adminNotice.description")}
              action={
                <Button asChild className="h-11">
                  <a href={adminUrl()}>{t("adminNotice.cta")}</a>
                </Button>
              }
            />
          </div>
        )}

        {isOffline && (!isStudent || (!isNotFound && !isNotFinished)) && (
          <div className="rounded-2xl border border-border bg-surface p-6">
            <EmptyState
              headingLevel={1}
              icon={<WifiOff />}
              title={common("offline.title")}
              description={common("offline.description")}
              action={
                <Button
                  type="button"
                  variant="secondary"
                  className="h-11"
                  onClick={() => void (isSessionOffline ? sessionQuery.refetch() : resultQuery.refetch())}
                >
                  {common("retry")}
                </Button>
              }
            />
          </div>
        )}

        {isStudent && validAttemptId && resultQuery.isLoading && !resultQuery.isPaused && !result && (
          <div role="status" aria-busy="true" aria-label={t("loading")} className="flex flex-col gap-4">
            <Skeleton className="h-9.5 w-80 max-w-full" />
            <Skeleton className="h-64 w-full rounded-2xl" />
            <Skeleton className="h-48 w-full rounded-2xl" />
          </div>
        )}

        {isStudent && isNotFound && (
          <StatusPage
            embedded
            icon={SearchX}
            eyebrow={t("notFound.eyebrow")}
            title={t("notFound.title")}
            description={t("notFound.description")}
            actions={
              <>
                <Button asChild size="lg">
                  <Link href="/history">{t("notFound.toHistory")}</Link>
                </Button>
                <Button asChild variant="secondary" size="lg">
                  <Link href="/home">{t("notFound.toHome")}</Link>
                </Button>
              </>
            }
          />
        )}

        {isStudent && isNotFinished && (
          <div className="rounded-2xl border border-border bg-surface p-6">
            <EmptyState
              headingLevel={1}
              icon={<Clock />}
              title={t("notFinished.title")}
              description={t("notFinished.description")}
              action={
                <div className="flex flex-wrap justify-center gap-3">
                  <Button asChild variant="exam" size="lg">
                    <Link href={`/attempts/${encodeURIComponent(normalizedAttemptId)}`}>{t("notFinished.continueAttempt")}</Link>
                  </Button>
                  <Button asChild variant="secondary" size="lg">
                    <Link href="/history">{t("notFinished.toHistory")}</Link>
                  </Button>
                </div>
              }
            />
          </div>
        )}

        {isStudent && resultQuery.isError && !isNotFound && !isNotFinished && !result && !resultQuery.isPaused && (
          <div
            role="alert"
            className="flex flex-col items-center gap-3 rounded-2xl border border-border bg-surface px-6 py-12 text-center"
          >
            <span
              aria-hidden
              className="grid size-12 place-items-center rounded-xl bg-danger-bg text-danger [&_svg]:size-6"
            >
              <TriangleAlert />
            </span>
            <h1 className="text-title font-semibold text-text">{t("errorTitle")}</h1>
            <p className="max-w-sm text-body text-text-muted">{t("errorDescription")}</p>
            <Button type="button" variant="secondary" className="h-11" onClick={() => void resultQuery.refetch()}>
              {common("retry")}
            </Button>
          </div>
        )}

        {isStudent && resultQuery.isError && !isNotFound && !isNotFinished && Boolean(result) && (
          <div
            role="alert"
            className="flex flex-wrap items-center justify-between gap-3 rounded-xl border border-warning bg-warning-bg px-4 py-3 text-body"
          >
            <div className="flex flex-col gap-0.5">
              <span className="font-semibold text-warning">{t("errorTitle")}</span>
              <span className="text-text-secondary">{t("errorDescription")}</span>
            </div>
            <Button
              type="button"
              variant="secondary"
              className="h-11"
              onClick={() => void resultQuery.refetch()}
            >
              {common("retry")}
            </Button>
          </div>
        )}

        {isStudent && result && !isNotFound && !isNotFinished && (
          <LoadedResultContent
            attemptId={normalizedAttemptId}
            result={result}
            examKindMap={examKindMap}
            subjectMap={subjectMap}
            locale={locale}
            gradingTimedOut={gradingTimedOut}
            isRefetching={resultQuery.isFetching}
            onReloadGrading={handleReloadGrading}
          />
        )}
      </main>
    </div>
  );
}

function LoadedResultContent({
  attemptId,
  result,
  examKindMap,
  subjectMap,
  locale,
  gradingTimedOut,
  isRefetching,
  onReloadGrading,
}: {
  attemptId: string;
  result: AttemptResultResponse;
  examKindMap: ReadonlyMap<string, CatalogOptionResponse>;
  subjectMap: ReadonlyMap<string, CatalogOptionResponse>;
  locale: string;
  gradingTimedOut: boolean;
  isRefetching: boolean;
  onReloadGrading: () => void;
}) {
  const t = useTranslations("result");

  const examKindLabel = result.examKindCode
    ? resolveCatalogLabel(result.examKindCode, examKindMap, locale)
    : "—";
  const scopeLabel = isKnownScope(result.scope) ? t(`scopes.${result.scope}`) : (result.scope ?? "—");
  const stateKey = result.regrading
    ? "REGRADING"
    : isKnownResultState(result.state)
      ? result.state
      : "SCORED";
  const statusTone = resolveResultStatusTone(result.state, result.regrading);
  const isVact = result.examKindCode === "VACT";

  const resolvedScore = resolveAttemptDisplayScore(result);
  const formattedChoices = formatAttemptChoices(result.choices, subjectMap, locale, (code) =>
    t(`choiceOptions.${code}`),
  );
  const startedText = formatAttemptDateTime(result.startedAt);
  const finishedText = formatAttemptDateTime(result.finishedAt);
  const durationParts = computeActualDuration(result.startedAt, result.finishedAt);
  const durationText = durationParts
    ? durationParts.kind === "zero"
      ? t("info.durationZero")
      : durationParts.kind === "seconds"
        ? t("info.durationSeconds", { seconds: durationParts.seconds })
        : durationParts.kind === "minutes"
          ? t("info.durationMinutes", { minutes: durationParts.minutes })
          : t("info.durationHoursMinutes", { hours: durationParts.hours, minutes: durationParts.minutes })
    : "—";
  const endReasonText = isKnownEndReason(result.endReason)
    ? t(`endReasons.${result.endReason}`)
    : (result.endReason ?? "—");

  const showSystemFaultBanner =
    result.compensated === true || result.faultFlag === "SYSTEM_FAULT" || result.endReason === "FAULT_CLOSED";

  const reviewHref = `/attempts/${encodeURIComponent(attemptId)}/review`;
  const canOpenReview =
    result.reviewAvailable === true &&
    result.resultView !== "SCORE" &&
    (result.state === "SCORED" || result.state === "PENDING_REVIEW");

  return (
    <>
      <div className="flex flex-col gap-2.5">
        <p className="text-overline font-semibold tracking-overline text-warning uppercase">{t("eyebrow")}</p>
        <h1 data-exam-content className="text-h1 font-bold wrap-break-word text-text">
          {result.examName}
        </h1>
        <div className="flex flex-wrap items-center gap-2">
          {result.examKindCode && (
            <span className="inline-flex h-7 items-center rounded-full bg-subtle px-3 text-caption font-medium text-text-secondary">
              {examKindLabel}
            </span>
          )}
          {result.scope && (
            <span className="inline-flex h-7 items-center rounded-full bg-subtle px-3 text-caption font-medium text-text-secondary">
              {scopeLabel}
            </span>
          )}
          {result.attemptNo != null && (
            <span className="inline-flex h-7 items-center rounded-full bg-subtle px-3 text-caption font-medium text-text-secondary tabular-nums">
              {t("attemptNo", { count: result.attemptNo })}
            </span>
          )}
          <StatusChip tone={statusTone}>{t(`states.${stateKey}`)}</StatusChip>
        </div>
      </div>

      {showSystemFaultBanner && (
        <div
          role="status"
          className={cn(
            "flex items-start gap-3 rounded-xl border px-4 py-3.5",
            result.compensated
              ? "border-info bg-info-bg text-info"
              : "border-warning bg-warning-bg text-warning",
          )}
        >
          <Info aria-hidden className="mt-0.5 size-4.5 shrink-0" />
          <div className="flex min-w-0 flex-col gap-1 text-body">
            <span className="font-semibold">
              {result.compensated ? t("fault.compensatedBanner") : t("fault.faultNoticeBanner")}
            </span>
            {result.faultReason && (
              <span className="text-text-secondary">
                {t("fault.reasonLabel")}{" "}
                <span data-exam-content>{result.faultReason}</span>
              </span>
            )}
          </div>
        </div>
      )}

      {result.state === "GRADING" && (
        <section
          aria-labelledby="result-grading-title"
          className="flex flex-col gap-4 rounded-2xl border border-border bg-surface p-6 shadow-xs"
        >
          {gradingTimedOut ? (
            <div className="flex flex-col gap-4">
              <div className="flex items-start gap-3.5">
                <span
                  aria-hidden
                  className="grid size-11 shrink-0 place-items-center rounded-xl bg-info-bg text-info"
                >
                  <Clock className="size-5.5" />
                </span>
                <div className="flex flex-col gap-1">
                  <h2 id="result-grading-title" className="text-h3 font-bold text-text">
                    {t("grading.timeoutHeading")}
                  </h2>
                  <p className="text-body text-text-secondary">{t("grading.timeoutDescription")}</p>
                </div>
              </div>
              <div className="flex flex-wrap gap-3">
                <Button
                  type="button"
                  variant="secondary"
                  loading={isRefetching}
                  onClick={onReloadGrading}
                  className="h-11"
                >
                  <RefreshCw aria-hidden />
                  {t("grading.reload")}
                </Button>
                <Button asChild variant="outline" className="h-11">
                  <Link href="/history">{t("backToHistory")}</Link>
                </Button>
              </div>
            </div>
          ) : (
            <div role="status" aria-live="polite" className="flex items-start gap-3.5">
              <LoaderCircle
                aria-hidden
                className="mt-0.5 size-8 shrink-0 animate-spin text-primary-text motion-reduce:animate-[spin_6s_linear_infinite]"
              />
              <div className="flex flex-col gap-1">
                <h2 id="result-grading-title" className="text-h3 font-bold text-text">
                  {t("grading.heading")}
                </h2>
                <p className="text-body text-text-secondary">{t("grading.description")}</p>
              </div>
            </div>
          )}
        </section>
      )}

      {result.state === "GRADING_ERROR" && (
        <section
          aria-labelledby="result-grading-error-title"
          className="flex flex-col gap-4 rounded-2xl border border-warning bg-warning-bg p-6"
        >
          <div role="status" className="flex items-start gap-3 text-warning">
            <AlertCircle aria-hidden className="mt-0.5 size-5 shrink-0" />
            <div className="flex flex-col gap-1">
              <h2 id="result-grading-error-title" className="text-h3 font-bold text-text">
                {t("gradingError.heading")}
              </h2>
              <p className="text-body font-medium text-text-secondary">{t("gradingError.message")}</p>
            </div>
          </div>
          <div className="flex flex-wrap gap-3">
            <Button asChild variant="secondary" className="h-11">
              <Link href="/history">{t("backToHistory")}</Link>
            </Button>
            <Button asChild variant="secondary" className="h-11">
              <Link href="/home">{t("actions.myExams")}</Link>
            </Button>
          </div>
        </section>
      )}

      {(result.state === "SCORED" || result.state === "PENDING_REVIEW") && (
        <section
          aria-labelledby="result-total-title"
          className="flex flex-col gap-6 rounded-2xl border border-border bg-surface p-6 shadow-xs"
        >
          <div className="flex flex-wrap items-start gap-6 sm:gap-10">
            <div className="flex min-w-0 flex-1 basis-64 flex-col gap-2">
              <h2 id="result-total-title" className="text-overline font-semibold tracking-overline text-text-muted uppercase">
                {t("total.title")}
              </h2>

              {resolvedScore ? (
                <>
                  {isVact && (
                    <span className="inline-flex h-6 self-start items-center rounded-full border border-warning bg-warning-bg px-2.5 text-caption font-semibold text-warning">
                      {t("vact.scoreLabel")}
                    </span>
                  )}
                  <div className="flex flex-wrap items-baseline gap-2 tabular-nums">
                    <span className="text-display font-bold text-primary-900">
                      {formatScoreValue(resolvedScore.score, locale)}
                    </span>
                    {resolvedScore.max != null && (
                      <span className="text-h2 font-semibold text-text-muted">
                        /{formatScoreValue(resolvedScore.max, locale)}
                      </span>
                    )}
                  </div>
                  {isVact && (
                    <p className="text-body font-semibold text-warning">{t("vact.practiceNote")}</p>
                  )}
                  {result.regrading ? (
                    <div className="flex flex-col gap-2 pt-1">
                      <StatusChip tone="warning" className="self-start">
                        {t("states.REGRADING")}
                      </StatusChip>
                      <p className="text-body text-text-secondary">{t("total.regradingNote")}</p>
                    </div>
                  ) : (
                    <p className="text-body text-text-secondary">
                      {isVact && resolvedScore.max != null
                        ? t("total.vactScaleDescription", { scaleMax: formatScoreValue(resolvedScore.max, locale) })
                        : t("total.scaleDescription")}
                    </p>
                  )}
                </>
              ) : (
                <>
                  <div className="flex items-center gap-3 pt-1">
                    <span
                      aria-hidden
                      className="grid size-12 shrink-0 place-items-center rounded-xl bg-warning-bg text-warning"
                    >
                      <Clock className="size-6" />
                    </span>
                    <span className="text-h1 font-bold text-warning">{t("total.pendingTitle")}</span>
                  </div>
                  <p className="text-body text-text-secondary">{t("total.pendingDescription")}</p>
                  <p className="text-caption text-text-muted">{t("total.pendingReturnNote")}</p>
                </>
              )}
            </div>

            {result.statistics && (
              <StatBuckets
                stats={result.statistics}
                locale={locale}
                showPendingBucket={result.state === "PENDING_REVIEW"}
              />
            )}
          </div>

          <div className="flex flex-wrap items-center gap-3 border-t border-border pt-5">
            {canOpenReview && (
              <Button asChild size="lg" className="h-auto min-h-12 px-4 py-2.5 whitespace-normal text-center">
                <Link href={reviewHref}>
                  <Eye aria-hidden />
                  {result.state === "PENDING_REVIEW" ? t("actions.reviewAutoGraded") : t("actions.reviewFull")}
                </Link>
              </Button>
            )}
            <Button asChild variant="secondary" className="h-11">
              <Link href="/home">{t("actions.myExams")}</Link>
            </Button>
            <Button asChild variant="secondary" className="h-11">
              <Link href="/history">{t("backToHistory")}</Link>
            </Button>
            {result.resultView === "SCORE" && (
              <span className="text-body text-text-muted">{t("actions.scoreOnlyNote")}</span>
            )}
            {result.resultView !== "SCORE" && result.state === "PENDING_REVIEW" && result.reviewAvailable && (
              <span className="text-body text-text-muted">{t("actions.pendingReviewNote")}</span>
            )}
          </div>
        </section>
      )}

      {isVact && result.vactComponents && result.vactComponents.length > 0 && (
        <section
          aria-labelledby="result-vact-components-title"
          className="flex flex-col gap-4 rounded-2xl border border-border bg-surface p-5 sm:p-6"
        >
          <div className="flex flex-wrap items-center justify-between gap-2">
            <div className="flex items-center gap-3">
              <span aria-hidden className="h-6 w-1 shrink-0 rounded-full bg-primary-600" />
              <h2 id="result-vact-components-title" className="text-h3 font-bold text-text">
                {t("vact.componentsTitle")}
              </h2>
            </div>
            <span className="text-caption text-text-muted">{t("vact.componentsCaption")}</span>
          </div>

          <div className="grid grid-cols-[repeat(auto-fit,minmax(min(15rem,100%),1fr))] items-start gap-3.5">
            {result.vactComponents.map((comp, index) => {
              const code = comp.componentCode ?? "";
              const label = isKnownVactComponentCode(code)
                ? t(`vact.components.${code}`)
                : resolveCatalogLabel(code, subjectMap, locale);
              const correct = comp.correctCount ?? 0;
              const total = comp.totalCount ?? 0;
              const rate = formatScoreValue(comp.correctRate ?? 0, locale);
              return (
                <article
                  key={code || index}
                  className="flex flex-col gap-1.5 rounded-xl border border-border bg-subtle p-4"
                >
                  <h3 className="text-label font-semibold text-text-secondary">{label}</h3>
                  <p className="text-title font-bold text-text tabular-nums">
                    {t("vact.correctRatio", { correct, total, rate })}
                  </p>
                  <ThinkingSubsections componentCode={code} subsections={comp.subsections} />
                </article>
              );
            })}
          </div>
        </section>
      )}

      <section
        aria-labelledby="result-info-title"
        className="flex flex-col gap-4 rounded-2xl border border-border bg-surface p-5 sm:p-6"
      >
        <div className="flex items-center gap-3">
          <span aria-hidden className="h-6 w-1 shrink-0 rounded-full bg-primary-600" />
          <h2 id="result-info-title" className="text-h3 font-bold text-text">
            {t("info.title")}
          </h2>
        </div>

        <dl className="grid grid-cols-[repeat(auto-fit,minmax(min(13.5rem,100%),1fr))] gap-x-6 gap-y-4">
          <div className="flex flex-col gap-0.5">
            <dt className="text-caption text-text-muted">{t("info.examKind")}</dt>
            <dd className="font-semibold wrap-break-word text-text">{examKindLabel}</dd>
          </div>
          <div className="flex flex-col gap-0.5">
            <dt className="text-caption text-text-muted">{t("info.scope")}</dt>
            <dd className="font-semibold wrap-break-word text-text">{scopeLabel}</dd>
          </div>
          {result.partCode && (
            <div className="flex flex-col gap-0.5">
              <dt className="text-caption text-text-muted">{t("info.part")}</dt>
              <dd className="font-semibold wrap-break-word text-text">{result.partCode}</dd>
            </div>
          )}
          {result.subjectCode && (
            <div className="flex flex-col gap-0.5">
              <dt className="text-caption text-text-muted">{t("info.subject")}</dt>
              <dd className="font-semibold wrap-break-word text-text">
                {resolveCatalogLabel(result.subjectCode, subjectMap, locale)}
              </dd>
            </div>
          )}
          {result.attemptNo != null && (
            <div className="flex flex-col gap-0.5">
              <dt className="text-caption text-text-muted">{t("info.attemptNo")}</dt>
              <dd className="font-semibold wrap-break-word text-text tabular-nums">
                {t("attemptNo", { count: result.attemptNo })}
              </dd>
            </div>
          )}
          <div className="flex flex-col gap-0.5">
            <dt className="text-caption text-text-muted">{t("info.choices")}</dt>
            <dd className="font-semibold wrap-break-word text-text">
              {formattedChoices.length > 0 ? formattedChoices.join(" · ") : t("info.noChoices")}
            </dd>
          </div>
          {startedText && (
            <div className="flex flex-col gap-0.5">
              <dt className="text-caption text-text-muted">{t("info.startedAt")}</dt>
              <dd className="font-semibold wrap-break-word text-text tabular-nums">{startedText}</dd>
            </div>
          )}
          {finishedText && (
            <div className="flex flex-col gap-0.5">
              <dt className="text-caption text-text-muted">{t("info.finishedAt")}</dt>
              <dd className="font-semibold wrap-break-word text-text tabular-nums">{finishedText}</dd>
            </div>
          )}
          <div className="flex flex-col gap-0.5">
            <dt className="text-caption text-text-muted">{t("info.duration")}</dt>
            <dd className="font-semibold wrap-break-word text-text tabular-nums">{durationText}</dd>
          </div>
          {result.endReason && (
            <div className="flex flex-col gap-0.5">
              <dt className="text-caption text-text-muted">{t("info.endReason")}</dt>
              <dd className="font-semibold wrap-break-word text-text">{endReasonText}</dd>
            </div>
          )}
        </dl>
      </section>

      {result.parts && result.parts.length > 0 && (
        <section
          aria-labelledby="result-parts-title"
          className="flex flex-col gap-4 rounded-2xl border border-border bg-surface p-5 sm:p-6"
        >
          <div className="flex flex-wrap items-center justify-between gap-2">
            <div className="flex items-center gap-3">
              <span aria-hidden className="h-6 w-1 shrink-0 rounded-full bg-primary-600" />
              <h2 id="result-parts-title" className="text-h3 font-bold text-text">
                {t("parts.title")}
              </h2>
            </div>
            <span className="text-caption text-text-muted">
              {result.state === "PENDING_REVIEW" && !result.regrading
                ? t("parts.pendingDescription")
                : t("parts.description")}
            </span>
          </div>

          <div className="flex flex-col gap-3">
            {result.parts.map((part, index) => (
              <PartBreakdownCard
                key={part.partId ?? part.partCode ?? index}
                part={part}
                endReason={result.endReason}
                locale={locale}
              />
            ))}
          </div>
        </section>
      )}

      {!isVact && result.subjects && result.subjects.length > 0 && (
        <SubjectBreakdownSection
          subjects={result.subjects}
          hasPendingAttempt={result.state === "PENDING_REVIEW" && !result.regrading}
          subjectMap={subjectMap}
          locale={locale}
        />
      )}
    </>
  );
}
