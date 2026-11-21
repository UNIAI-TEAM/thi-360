"use client";

import { useQuery } from "@tanstack/react-query";
import {
  Button,
  EmptyState,
  NavTab,
  NavTabs,
  Skeleton,
  StatusPage,
  cn,
  focusRing,
} from "@thi360/ui";
import { Question, type QuestionOutcome } from "@thi360/ui/question";
import {
  ArrowLeft,
  ChevronLeft,
  ChevronRight,
  Clock,
  Info,
  Lock,
  SearchX,
  ShieldCheck,
  TriangleAlert,
} from "lucide-react";
import { useLocale, useTranslations } from "next-intl";
import Link from "next/link";
import { useSearchParams } from "next/navigation";
import { useMemo, useState } from "react";
import { type HeaderAccount, StudentHeader } from "@/components/student-header";
import {
  ATTEMPT_RESULT_QUERY_KEY,
  loadAttemptResult,
  type AttemptResultLoader,
} from "@/features/attempt-result/api";
import {
  formatScoreValue,
  isKnownScope,
  resolveAttemptDisplayScore,
} from "@/features/attempt-result/format";
import { loadSession, SESSION_QUERY_KEY, type SessionLoader } from "@/features/session/api";
import { ApiStatusError } from "@/lib/api/client";
import { adminUrl } from "@/lib/env";
import {
  ATTEMPT_REVIEW_QUERY_KEY,
  loadAttemptReview,
  type AttemptReviewEssayFeedback,
  type AttemptReviewLoader,
  type AttemptReviewPart,
  type AttemptReviewResponse,
} from "./api";
import {
  computePaletteSummary,
  getItemsForPart,
  mapAttemptReview,
  type ReviewItemGroup,
} from "./mapper";

function outcomePaletteClasses(outcome: QuestionOutcome): string {
  switch (outcome) {
    case "CORRECT":
      return "border-success bg-success-bg text-success";
    case "PARTIAL":
      return "border-warning bg-warning-bg text-warning";
    case "WRONG":
      return "border-danger bg-danger-bg text-danger";
    case "PENDING_REVIEW":
      return "border-info bg-info-bg text-info";
    case "BLANK":
    default:
      return "border-border bg-subtle text-text-secondary";
  }
}

function EssayCriteriaSection({
  feedback,
  headingLabel,
  locale,
}: {
  feedback: AttemptReviewEssayFeedback;
  headingLabel?: string;
  locale: string;
}) {
  const t = useTranslations("review.essayCriteria");
  const criteria = feedback.criteria ?? [];
  if (criteria.length === 0) return null;

  return (
    <div className="rounded-xl border border-border bg-subtle p-4">
      <h3 className="mb-3 text-label font-semibold text-text">
        {headingLabel ? `${t("title")} · ${headingLabel}` : t("title")}
      </h3>
      <div className="overflow-x-auto rounded-lg border border-border bg-surface">
        <table className="w-full border-collapse text-left text-body">
          <thead>
            <tr className="border-b border-border bg-table-head text-caption font-semibold text-text-muted">
              <th scope="col" className="px-3.5 py-2.5">
                {t("criterionCol")}
              </th>
              <th scope="col" className="px-3.5 py-2.5 text-right whitespace-nowrap">
                {t("pointsCol")}
              </th>
              <th scope="col" className="px-3.5 py-2.5">
                {t("commentCol")}
              </th>
            </tr>
          </thead>
          <tbody className="divide-y divide-border">
            {criteria.map((criterion, index) => (
              <tr key={criterion.id ?? index}>
                <td className="px-3.5 py-2.5 font-medium text-text">
                  {criterion.id ? (
                    <span data-exam-content>{criterion.id}</span>
                  ) : (
                    <span>{t("criterionFallback", { index: index + 1 })}</span>
                  )}
                </td>
                <td className="px-3.5 py-2.5 text-right font-semibold whitespace-nowrap text-text tabular-nums">
                  {criterion.points != null
                    ? t("pointsValue", { points: formatScoreValue(criterion.points, locale) })
                    : "—"}
                </td>
                <td className="px-3.5 py-2.5 text-text-secondary">
                  {criterion.comment ? <span data-exam-content>{criterion.comment}</span> : "—"}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  );
}

type ReviewQueryOutcome =
  | { kind: "ok"; data: AttemptReviewResponse }
  | { kind: "not_included" };

export function AttemptReviewView({
  attemptId,
  loadCurrentSession = loadSession,
  loadReview = loadAttemptReview,
  loadResult = loadAttemptResult,
}: {
  attemptId: string;
  loadCurrentSession?: SessionLoader;
  loadReview?: AttemptReviewLoader;
  loadResult?: AttemptResultLoader;
}) {
  const t = useTranslations("review");
  const resultT = useTranslations("result");
  const common = useTranslations("common");
  const locale = useLocale();
  const searchParams = useSearchParams();
  const urlPartParam = searchParams.get("part");

  const [manualPart, setManualPart] = useState<{ urlKey: string | null; partKey: string } | null>(null);
  const [selectedItemId, setSelectedItemId] = useState<string | null>(null);

  const sessionQuery = useQuery({
    queryKey: SESSION_QUERY_KEY,
    queryFn: loadCurrentSession,
    staleTime: 60_000,
  });

  const userId = sessionQuery.data?.id ?? null;
  const isStudent = sessionQuery.data?.access === "EXAM";

  const reviewQuery = useQuery({
    queryKey: ATTEMPT_REVIEW_QUERY_KEY(attemptId, userId),
    queryFn: async (): Promise<ReviewQueryOutcome> => {
      try {
        return { kind: "ok", data: await loadReview(attemptId) };
      } catch (err) {
        if (err instanceof ApiStatusError && err.status === 403) {
          return { kind: "not_included" };
        }
        throw err;
      }
    },
    enabled: isStudent,
    retry: false,
  });

  const isNotIncluded = reviewQuery.data?.kind === "not_included";
  const reviewData = reviewQuery.data?.kind === "ok" ? reviewQuery.data.data : undefined;

  const resultQuery = useQuery({
    queryKey: ATTEMPT_RESULT_QUERY_KEY(attemptId, userId),
    queryFn: () => loadResult(attemptId),
    enabled: isStudent && Boolean(reviewData),
    retry: false,
    staleTime: 60_000,
  });

  const account: HeaderAccount = sessionQuery.data
    ? { kind: "user", session: sessionQuery.data }
    : sessionQuery.isLoading
      ? { kind: "loading" }
      : { kind: "none" };

  const error = reviewQuery.error;
  const isNotFound = error instanceof ApiStatusError && error.status === 404;
  const isNotAvailable = error instanceof ApiStatusError && error.status === 409;
  const resultHref = `/attempts/${encodeURIComponent(attemptId)}/result`;

  return (
    <div className="min-h-screen bg-page text-text">
      <StudentHeader account={account} />

      <main className="mx-auto flex max-w-[1170px] flex-col gap-6 px-4 py-8 sm:px-6 sm:py-10">
        <Link
          href={resultHref}
          className={cn(
            "inline-flex min-h-11 items-center gap-2 self-start rounded-sm font-medium text-primary-text hover:underline",
            focusRing,
          )}
        >
          <ArrowLeft aria-hidden className="size-4" />
          {t("backToResult")}
        </Link>

        {sessionQuery.isLoading && (
          <div role="status" aria-busy="true" aria-label={common("loading")} className="flex flex-col gap-4">
            <Skeleton className="h-9.5 w-72 max-w-full" />
            <Skeleton className="h-48 w-full rounded-2xl" />
            <Skeleton className="h-64 w-full rounded-2xl" />
          </div>
        )}

        {sessionQuery.isError && (
          <div className="rounded-xl border border-border bg-surface">
            <EmptyState
              headingLevel={1}
              icon={<TriangleAlert />}
              title={t("errorTitle")}
              description={t("errorDescription")}
              action={
                <Button type="button" onClick={() => void sessionQuery.refetch()}>
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

        {isStudent && reviewQuery.isLoading && !reviewQuery.data && (
          <div role="status" aria-busy="true" aria-label={t("loading")} className="flex flex-col gap-4">
            <Skeleton className="h-9.5 w-80 max-w-full" />
            <Skeleton className="h-12 w-full rounded-xl" />
            <Skeleton className="h-80 w-full rounded-2xl" />
          </div>
        )}

        {isStudent && isNotIncluded && (
          <div className="rounded-2xl border border-border bg-surface p-6">
            <EmptyState
              headingLevel={1}
              icon={<Lock />}
              title={t("notIncluded.title")}
              description={t("notIncluded.description")}
              action={
                <Button asChild size="lg">
                  <Link href={resultHref}>{t("notIncluded.backToResult")}</Link>
                </Button>
              }
            />
          </div>
        )}

        {isStudent && isNotAvailable && (
          <div className="rounded-2xl border border-border bg-surface p-6">
            <EmptyState
              headingLevel={1}
              icon={<Clock />}
              title={t("notAvailable.title")}
              description={t("notAvailable.description")}
              action={
                <Button asChild size="lg">
                  <Link href={resultHref}>{t("notAvailable.backToResult")}</Link>
                </Button>
              }
            />
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

        {isStudent && reviewQuery.isError && !isNotIncluded && !isNotAvailable && !isNotFound && !reviewQuery.data && (
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
            <Button type="button" variant="secondary" onClick={() => void reviewQuery.refetch()}>
              {common("retry")}
            </Button>
          </div>
        )}

        {isStudent && reviewData && (
          <LoadedReviewContent
            attemptId={attemptId}
            reviewData={reviewData}
            resultData={resultQuery.data}
            urlPartParam={urlPartParam}
            manualPart={manualPart}
            onSelectPart={(partKey) => setManualPart({ urlKey: urlPartParam, partKey })}
            selectedItemId={selectedItemId}
            onSelectItem={setSelectedItemId}
            locale={locale}
            resultT={resultT}
          />
        )}
      </main>
    </div>
  );
}

function resolvePartKey(part: AttemptReviewPart): string {
  return part.partCode ?? part.partId ?? "";
}

function LoadedReviewContent({
  attemptId,
  reviewData,
  resultData,
  urlPartParam,
  manualPart,
  onSelectPart,
  selectedItemId,
  onSelectItem,
  locale,
  resultT,
}: {
  attemptId: string;
  reviewData: AttemptReviewResponse;
  resultData?: ReturnType<typeof Object> & {
    examName?: string;
    scope?: string;
    attemptNo?: number;
    state?: "STARTING" | "IN_PROGRESS" | "GRADING" | "PENDING_REVIEW" | "SCORED" | "GRADING_ERROR" | "ABORTED";
    regrading?: boolean;
    displayScore?: number;
    rawScore?: number;
    scaleMax?: number;
    rawMaxScore?: number;
  };
  urlPartParam: string | null;
  manualPart: { urlKey: string | null; partKey: string } | null;
  onSelectPart: (partKey: string) => void;
  selectedItemId: string | null;
  onSelectItem: (itemId: string) => void;
  locale: string;
  resultT: ReturnType<typeof useTranslations>;
}) {
  const t = useTranslations("review");

  const mapped = useMemo(() => mapAttemptReview(reviewData), [reviewData]);
  const activeUrlKey = manualPart && manualPart.urlKey === urlPartParam ? manualPart.partKey : urlPartParam;

  const selectedPart = useMemo(() => {
    if (mapped.parts.length === 0) return undefined;
    const candidateKey = activeUrlKey ?? reviewData.selectedPartCode;
    if (candidateKey) {
      const matched = mapped.parts.find(
        (part) => part.partCode === candidateKey || part.partId === candidateKey,
      );
      if (matched) return matched;
    }
    return mapped.parts[0];
  }, [mapped.parts, activeUrlKey, reviewData.selectedPartCode]);

  const partItems = useMemo(() => getItemsForPart(mapped, selectedPart), [mapped, selectedPart]);
  const paletteSummary = useMemo(() => computePaletteSummary(partItems), [partItems]);

  const activeIndex = useMemo(() => {
    if (partItems.length === 0) return -1;
    if (selectedItemId) {
      const idx = partItems.findIndex((item) => item.id === selectedItemId);
      if (idx >= 0) return idx;
    }
    return 0;
  }, [partItems, selectedItemId]);

  const activeItem = activeIndex >= 0 ? partItems[activeIndex] : undefined;

  const scoreInfo = resultData ? resolveAttemptDisplayScore(resultData) : null;
  const scopeLabel = resultData?.scope
    ? isKnownScope(resultData.scope)
      ? resultT(`scopes.${resultData.scope}`)
      : resultData.scope
    : null;

  function formatGroupLabel(group: ReviewItemGroup): string {
    if (group.rangeFrom != null && group.rangeTo != null) {
      return t("questionRangeLabel", { from: group.rangeFrom, to: group.rangeTo });
    }
    return t("questionLabel", { no: group.displayNo });
  }

  function jumpToItem(group: ReviewItemGroup) {
    onSelectItem(group.id);
    if (typeof document !== "undefined") {
      const target = document.getElementById(`review-item-${group.id}`);
      if (target) {
        target.scrollIntoView?.({ behavior: "smooth", block: "start" });
        target.focus?.({ preventScroll: true });
      }
    }
  }

  function partHref(part: AttemptReviewPart): string {
    const key = resolvePartKey(part);
    const base = `/attempts/${encodeURIComponent(attemptId)}/review`;
    return key ? `${base}?part=${encodeURIComponent(key)}` : base;
  }

  return (
    <>
      <header className="flex flex-col gap-2.5">
        <h1 className="text-h1 font-bold text-text">{t("pageTitle")}</h1>
        {resultData?.examName && (
          <p className="flex flex-wrap items-center gap-x-2 gap-y-1 text-body text-text-secondary">
            <span data-exam-content className="font-semibold text-text">
              {resultData.examName}
            </span>
            {scopeLabel && <span>· {scopeLabel}</span>}
            {resultData.attemptNo != null && <span>· {resultT("attemptNo", { count: resultData.attemptNo })}</span>}
            {scoreInfo && (
              <span className="font-semibold text-primary-text tabular-nums">
                · {resultT("total.title")}: {formatScoreValue(scoreInfo.score, locale)}/
                {formatScoreValue(scoreInfo.max, locale)}
              </span>
            )}
          </p>
        )}
      </header>

      {reviewData.resultView === "SCORE_AND_ANSWERS" && (
        <div
          role="status"
          className="flex items-start gap-3 rounded-xl border border-border bg-subtle px-4 py-3 text-body text-text-secondary"
        >
          <Info aria-hidden className="mt-0.5 size-4.5 shrink-0 text-text-muted" />
          <span>{t("scoreOnlyNotice")}</span>
        </div>
      )}

      {reviewData.state === "PENDING_REVIEW" && (
        <div
          role="status"
          className="flex items-start gap-3 rounded-xl border border-warning bg-warning-bg px-4 py-3 text-body text-warning"
        >
          <Clock aria-hidden className="mt-0.5 size-4.5 shrink-0" />
          <span className="font-medium text-text">{t("pendingBanner")}</span>
        </div>
      )}

      {mapped.parts.length > 0 && (
        <NavTabs aria-label={t("nav.partsLabel")}>
          {mapped.parts.map((part, index) => {
            const key = resolvePartKey(part);
            const isSelected = selectedPart
              ? resolvePartKey(selectedPart) === key
              : index === 0;
            const skipped = part.state === "SKIPPED";
            return (
              <NavTab
                key={part.partId ?? part.partCode ?? index}
                asChild
                current={isSelected}
              >
                <Link
                  href={partHref(part)}
                  onClick={() => {
                    onSelectPart(key);
                    setManualSelectionSafe(onSelectItem);
                  }}
                >
                  <span data-exam-content>{part.partName || part.partCode}</span>
                  {skipped && (
                    <span className="ml-2 rounded-full border border-warning bg-warning-bg px-2 py-0.5 text-caption font-semibold text-warning">
                      {t("nav.skippedPartBadge")}
                    </span>
                  )}
                </Link>
              </NavTab>
            );
          })}
        </NavTabs>
      )}

      {selectedPart?.state === "SKIPPED" ? (
        <div className="rounded-2xl border border-warning bg-warning-bg p-6">
          <EmptyState
            headingLevel={2}
            icon={<Info />}
            title={t("skippedPart.title")}
            description={
              selectedPart.score != null && selectedPart.maxScore != null
                ? t("skippedPart.description", {
                    score: formatScoreValue(selectedPart.score, locale),
                    max: formatScoreValue(selectedPart.maxScore, locale),
                  })
                : t("skippedPart.descriptionNoScore")
            }
          />
        </div>
      ) : partItems.length === 0 ? (
        <div className="rounded-2xl border border-border bg-surface p-6">
          <EmptyState
            headingLevel={2}
            icon={<SearchX />}
            title={t("emptyPart.title")}
            description={t("emptyPart.description")}
          />
        </div>
      ) : (
        <div className="grid grid-cols-1 gap-6 lg:grid-cols-[minmax(0,1fr)_18rem] lg:items-start">
          <div className="flex min-w-0 flex-col gap-5">
            {partItems.map((group) => {
              const isActive = activeItem?.id === group.id;
              return (
                <article
                  key={group.id}
                  id={`review-item-${group.id}`}
                  tabIndex={-1}
                  className={cn(
                    "flex flex-col gap-4 rounded-2xl border bg-surface p-5 transition-colors outline-hidden sm:p-6",
                    isActive ? "border-primary-600 ring-1 ring-primary-600" : "border-border",
                  )}
                >
                  <Question
                    mode="review"
                    headingLevel={2}
                    question={group.question}
                    value={group.value}
                    review={group.review}
                  >
                    {group.children.length > 0 ? group.children : undefined}
                  </Question>

                  {group.essayFeedback?.criteria && group.essayFeedback.criteria.length > 0 && (
                    <EssayCriteriaSection feedback={group.essayFeedback} locale={locale} />
                  )}

                  {group.childEssayFeedbacks.map((entry) => (
                    <EssayCriteriaSection
                      key={entry.id}
                      feedback={entry.feedback}
                      headingLabel={t("questionLabel", { no: entry.displayNo })}
                      locale={locale}
                    />
                  ))}
                </article>
              );
            })}
          </div>

          <aside
            aria-label={t("nav.paletteTitle")}
            className="flex flex-col gap-4 rounded-2xl border border-border bg-surface p-5 lg:sticky lg:top-6"
          >
            <div className="flex flex-col gap-1">
              <h2 className="text-title font-bold text-text">{t("nav.paletteTitle")}</h2>
              <p className="text-caption text-text-muted tabular-nums">
                {t("nav.paletteSummary", paletteSummary)}
              </p>
            </div>

            <div className="grid grid-cols-4 gap-2 sm:grid-cols-6 lg:grid-cols-3">
              {partItems.map((group) => {
                const isActive = activeItem?.id === group.id;
                const label = formatGroupLabel(group);
                const outcomeText = t(`outcomes.${group.outcome}`);
                return (
                  <button
                    key={group.id}
                    type="button"
                    aria-current={isActive ? "true" : undefined}
                    aria-label={t("nav.paletteItemAria", { label, outcome: outcomeText })}
                    onClick={() => jumpToItem(group)}
                    className={cn(
                      "flex min-h-11 items-center justify-center rounded-lg border px-2 py-1.5 text-center text-caption font-semibold tabular-nums transition-colors duration-150 motion-reduce:transition-none",
                      outcomePaletteClasses(group.outcome),
                      isActive && "ring-2 ring-primary-text font-bold",
                      focusRing,
                    )}
                  >
                    <span>
                      {group.rangeFrom != null && group.rangeTo != null
                        ? `${group.rangeFrom}–${group.rangeTo}`
                        : group.displayNo}
                    </span>
                  </button>
                );
              })}
            </div>

            <div
              aria-label={t("nav.paletteLegend")}
              className="flex flex-wrap gap-2 border-t border-border pt-3 text-caption text-text-secondary"
            >
              <span className="inline-flex items-center gap-1.5">
                <span aria-hidden className="size-2.5 rounded-[3px] border border-success bg-success-bg" />
                {t("outcomes.CORRECT")}
              </span>
              <span className="inline-flex items-center gap-1.5">
                <span aria-hidden className="size-2.5 rounded-[3px] border border-warning bg-warning-bg" />
                {t("outcomes.PARTIAL")}
              </span>
              <span className="inline-flex items-center gap-1.5">
                <span aria-hidden className="size-2.5 rounded-[3px] border border-danger bg-danger-bg" />
                {t("outcomes.WRONG")}
              </span>
              <span className="inline-flex items-center gap-1.5">
                <span aria-hidden className="size-2.5 rounded-[3px] border border-border bg-subtle" />
                {t("outcomes.BLANK")}
              </span>
            </div>

            <div className="grid grid-cols-2 gap-2 border-t border-border pt-3">
              <Button
                type="button"
                variant="secondary"
                className="h-11 w-full min-w-0 px-2.5"
                disabled={activeIndex <= 0}
                onClick={() => {
                  const prev = partItems[activeIndex - 1];
                  if (prev) jumpToItem(prev);
                }}
              >
                <ChevronLeft aria-hidden />
                {t("nav.prev")}
              </Button>
              <Button
                type="button"
                variant="secondary"
                className="h-11 w-full min-w-0 px-2.5"
                disabled={activeIndex < 0 || activeIndex >= partItems.length - 1}
                onClick={() => {
                  const next = partItems[activeIndex + 1];
                  if (next) jumpToItem(next);
                }}
              >
                {t("nav.next")}
                <ChevronRight aria-hidden />
              </Button>
            </div>
          </aside>
        </div>
      )}
    </>
  );
}

function setManualSelectionSafe(onSelectItem: (id: string) => void) {
  onSelectItem("");
}
