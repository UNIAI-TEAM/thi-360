"use client";

import { keepPreviousData, useQuery } from "@tanstack/react-query";
import {
  Button,
  CardList,
  CardListItem,
  EmptyState,
  Input,
  ListCard,
  ListCardTag,
  ListCardTitleLink,
  Pagination,
  Select,
  Skeleton,
  StatusChip,
  cn,
  focusRing,
  focusRingInset,
  useCardMode,
  type StatusTone,
} from "@thi360/ui";
import {
  Search,
  SearchX,
  ShieldCheck,
  TriangleAlert,
  WifiOff,
  X,
} from "lucide-react";
import { useLocale, useTranslations } from "next-intl";
import Link from "next/link";
import { useRouter, useSearchParams } from "next/navigation";
import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { type HeaderAccount, StudentHeader } from "@/components/student-header";
import {
  CATALOG_OPTIONS_QUERY_KEY,
  loadExamKindOptions,
  loadSubjectOptions,
  type CatalogOptionResponse,
  type CatalogOptionsLoader,
} from "@/features/attempt-result/api";
import {
  buildCatalogMap,
  formatAttemptChoices,
  formatAttemptDateTime,
  formatScoreValue,
  resolveCatalogLabel,
  shouldShowAttemptScore,
} from "@/features/attempt-result/format";
import { useInlineOverflow } from "@/features/attempt-result/use-inline-overflow";
import { loadSession, SESSION_QUERY_KEY, type SessionLoader } from "@/features/session/api";
import { adminUrl } from "@/lib/env";
import { touchFieldText } from "@/lib/styles";
import {
  ATTEMPT_HISTORY_QUERY_KEY,
  HISTORY_PAGE_SIZE,
  loadAttemptHistory,
  type AttemptHistoryItem,
  type AttemptHistoryLoader,
} from "./api";
import {
  HISTORY_SCOPES,
  hasActiveHistoryFilters,
  isValidHistoryScope,
  parseHistorySearchParams,
  toHistoryApiParams,
  toHistorySearchString,
  type HistoryFilterState,
} from "./history-query";

const ALL_SENTINEL = "ALL";
const HISTORY_CARD_BREAKPOINT = 760;
const SEARCH_DEBOUNCE_MS = 300;

type KnownHistoryState =
  | "IN_PROGRESS"
  | "GRADING"
  | "PENDING_REVIEW"
  | "SCORED"
  | "GRADING_ERROR"
  | "REGRADING";

function resolveHistoryStatus(
  state: AttemptHistoryItem["state"],
  regrading: boolean | undefined,
): { key: KnownHistoryState; tone: StatusTone } {
  if (regrading) {
    return { key: "REGRADING", tone: "warning" };
  }
  switch (state) {
    case "SCORED":
      return { key: "SCORED", tone: "success" };
    case "PENDING_REVIEW":
      return { key: "PENDING_REVIEW", tone: "warning" };
    case "STARTING":
    case "IN_PROGRESS":
      return { key: "IN_PROGRESS", tone: "info" };
    case "GRADING":
      return { key: "GRADING", tone: "info" };
    case "GRADING_ERROR":
    case "ABORTED":
    default:
      return { key: "GRADING_ERROR", tone: "danger" };
  }
}

function resolveAttemptHref(item: AttemptHistoryItem): string {
  const id = encodeURIComponent(item.attemptId ?? "");
  return item.action === "CONTINUE" ? `/attempts/${id}` : `/attempts/${id}/result`;
}

function formatHistoryScopeDetail(
  item: AttemptHistoryItem,
  subjectMap: ReadonlyMap<string, CatalogOptionResponse>,
  locale: string,
  t: ReturnType<typeof useTranslations>,
): { scopeLabel: string; detail: string | null } {
  const scopeLabel = isValidHistoryScope(item.scope) ? t(`scopes.${item.scope}`) : (item.scope ?? "—");
  if (item.scope === "PART" && item.partCode) {
    return { scopeLabel, detail: t("scopeDetails.PART", { code: item.partCode }) };
  }
  if (item.scope === "SUBJECT" && item.subjectCode) {
    return {
      scopeLabel,
      detail: t("scopeDetails.SUBJECT", {
        name: resolveCatalogLabel(item.subjectCode, subjectMap, locale),
      }),
    };
  }
  return { scopeLabel, detail: null };
}

export function AttemptHistoryView({
  loadCurrentSession = loadSession,
  loadHistory = loadAttemptHistory,
  loadExamKinds = loadExamKindOptions,
  loadSubjects = loadSubjectOptions,
}: {
  loadCurrentSession?: SessionLoader;
  loadHistory?: AttemptHistoryLoader;
  loadExamKinds?: CatalogOptionsLoader;
  loadSubjects?: CatalogOptionsLoader;
}) {
  const t = useTranslations("history");
  const common = useTranslations("common");
  const locale = useLocale();
  const router = useRouter();
  const searchParams = useSearchParams();

  const searchParamsKey = searchParams.toString();
  const filterState = useMemo(
    () => parseHistorySearchParams(new URLSearchParams(searchParamsKey)),
    [searchParamsKey],
  );
  const [inputQ, setInputQ] = useState(filterState.q);
  const pushedQRef = useRef(filterState.q);
  const latestFilterStateRef = useRef(filterState);
  useEffect(() => {
    latestFilterStateRef.current = filterState;
  }, [filterState]);

  const updateQuery = useCallback(
    (patch: Partial<HistoryFilterState>, options?: { scroll?: boolean }) => {
      const next: HistoryFilterState = {
        ...latestFilterStateRef.current,
        ...patch,
      };
      latestFilterStateRef.current = next;
      if (patch.q !== undefined) {
        pushedQRef.current = next.q;
      }
      const qs = toHistorySearchString(next);
      router.replace(qs ? `/history?${qs}` : "/history", { scroll: options?.scroll ?? false });
    },
    [router],
  );

  useEffect(() => {
    const trimmed = inputQ.trim();
    if (trimmed === pushedQRef.current && trimmed === latestFilterStateRef.current.q) {
      return;
    }
    const timer = window.setTimeout(() => {
      pushedQRef.current = trimmed;
      const next: HistoryFilterState = {
        ...latestFilterStateRef.current,
        q: trimmed,
        page: 1,
      };
      latestFilterStateRef.current = next;
      const qs = toHistorySearchString(next);
      router.replace(qs ? `/history?${qs}` : "/history", { scroll: false });
    }, SEARCH_DEBOUNCE_MS);
    return () => window.clearTimeout(timer);
  }, [inputQ, router]);

  useEffect(() => {
    if (filterState.q !== pushedQRef.current) {
      pushedQRef.current = filterState.q;
      setInputQ(filterState.q);
    }
  }, [filterState.q]);

  const clearAllFilters = useCallback(() => {
    pushedQRef.current = "";
    setInputQ("");
    latestFilterStateRef.current = {
      q: "",
      kind: "",
      scope: "",
      examId: "",
      from: "",
      to: "",
      page: 1,
    };
    router.replace("/history", { scroll: false });
  }, [router]);

  const sessionQuery = useQuery({
    queryKey: SESSION_QUERY_KEY,
    queryFn: loadCurrentSession,
    staleTime: 60_000,
  });

  const userId = sessionQuery.data?.id ?? null;
  const isStudent = sessionQuery.data?.access === "EXAM";

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

  const apiParams = useMemo(() => toHistoryApiParams(filterState), [filterState]);

  const historyQuery = useQuery({
    queryKey: ATTEMPT_HISTORY_QUERY_KEY(apiParams, userId),
    queryFn: () => loadHistory(apiParams),
    enabled: isStudent,
    placeholderData: keepPreviousData,
    retry: false,
  });

  const subjectMap = useMemo(() => buildCatalogMap(subjectsQuery.data ?? []), [subjectsQuery.data]);
  const examKindOptions = useMemo(() => {
    const dynamicOptions = (examKindsQuery.data ?? [])
      .filter((item) => Boolean(item.code))
      .map((item) => ({
        value: item.code,
        label: resolveCatalogLabel(item.code, buildCatalogMap([item]), locale),
      }));
    const hasSelected =
      Boolean(filterState.kind) &&
      dynamicOptions.some((opt) => opt.value === filterState.kind);
    return [
      { value: ALL_SENTINEL, label: t("filters.kindAll") },
      ...dynamicOptions,
      ...(!hasSelected && filterState.kind
        ? [{ value: filterState.kind, label: filterState.kind }]
        : []),
    ];
  }, [examKindsQuery.data, filterState.kind, locale, t]);

  const scopeOptions = useMemo(
    () => [
      { value: ALL_SENTINEL, label: t("filters.scopeAll") },
      ...HISTORY_SCOPES.map((scope) => ({ value: scope, label: t(`scopes.${scope}`) })),
    ],
    [t],
  );

  const items = useMemo(() => historyQuery.data?.items ?? [], [historyQuery.data]);
  const isSessionOffline = sessionQuery.isPaused && !sessionQuery.data;
  const isHistoryOffline = isStudent && historyQuery.isPaused;
  const showItems = isStudent && !isHistoryOffline && Boolean(historyQuery.data) && items.length > 0;
  const [containerRef, cardMode] = useCardMode<HTMLDivElement>(
    isStudent && !isHistoryOffline && items.length > 0,
    HISTORY_CARD_BREAKPOINT,
  );
  const [tableScrollerRef, tableOverflows] = useInlineOverflow(showItems && !cardMode);

  const account: HeaderAccount = sessionQuery.data
    ? { kind: "user", session: sessionQuery.data }
    : sessionQuery.isLoading && !sessionQuery.isPaused
      ? { kind: "loading" }
      : { kind: "none" };

  const activeFilters = hasActiveHistoryFilters(filterState);
  const filteredExamName = useMemo(() => {
    if (!filterState.examId) return null;
    const matched = items.find((item) => item.examId === filterState.examId && item.examName);
    return matched?.examName ?? null;
  }, [filterState.examId, items]);

  const staleUpdatedText = historyQuery.dataUpdatedAt
    ? formatAttemptDateTime(new Date(historyQuery.dataUpdatedAt).toISOString())
    : null;

  return (
    <div className="min-h-screen bg-page text-text">
      <StudentHeader account={account} />

      <main className="mx-auto flex max-w-[1170px] flex-col gap-6 px-4 py-8 sm:px-6 sm:py-10">
        <header className="flex flex-col gap-1.5">
          <div className="flex flex-wrap items-baseline justify-between gap-2">
            <h1 className="text-h1 font-bold text-text">{t("pageTitle")}</h1>
            {isStudent && !isHistoryOffline && historyQuery.data && (
              <span role="status" className="text-label font-medium text-text-muted tabular-nums">
                {t("count", { count: historyQuery.data.totalItems })}
              </span>
            )}
          </div>
          <p className="text-body text-text-secondary">{t("subtitle")}</p>
        </header>

        {sessionQuery.isLoading && !sessionQuery.isPaused && (
          <div role="status" aria-busy="true" aria-label={common("loading")} className="flex flex-col gap-4">
            <Skeleton className="h-24 w-full rounded-2xl" />
            <Skeleton className="h-64 w-full rounded-2xl" />
          </div>
        )}

        {isSessionOffline && (
          <div className="rounded-xl border border-border bg-surface">
            <EmptyState
              headingLevel={2}
              icon={<WifiOff />}
              title={common("offline.title")}
              description={common("offline.description")}
              action={
                <Button
                  type="button"
                  variant="secondary"
                  className="h-11"
                  onClick={() => void sessionQuery.refetch()}
                >
                  {common("retry")}
                </Button>
              }
            />
          </div>
        )}

        {sessionQuery.isError && !sessionQuery.isPaused && !sessionQuery.data && (
          <div className="rounded-xl border border-border bg-surface">
            <EmptyState
              headingLevel={2}
              icon={<TriangleAlert />}
              title={t("error.title")}
              description={t("error.description")}
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
              headingLevel={2}
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

        {isStudent && (
          <>
            <section
              aria-label={t("filters.label")}
              className="flex flex-col gap-3 rounded-2xl border border-border bg-surface p-4 sm:p-5"
            >
              <form
                onSubmit={(e) => {
                  e.preventDefault();
                  const trimmed = inputQ.trim();
                  pushedQRef.current = trimmed;
                  updateQuery({ q: trimmed, page: 1 });
                }}
                className="flex flex-wrap items-end gap-3"
              >
                <div className="flex min-w-0 flex-[2_1_15rem] flex-col gap-1">
                  <label htmlFor="history-search" className="text-caption font-medium text-text-secondary">
                    {t("filters.q")}
                  </label>
                  <Input
                    id="history-search"
                    type="search"
                    size="lg"
                    leadingIcon={<Search />}
                    placeholder={t("filters.qPlaceholder")}
                    value={inputQ}
                    onChange={(e) => setInputQ(e.target.value)}
                    className={touchFieldText}
                  />
                </div>

                <div className="flex min-w-0 flex-[1_1_11rem] flex-col gap-1">
                  <label htmlFor="history-kind" className="text-caption font-medium text-text-secondary">
                    {t("filters.kind")}
                  </label>
                  <Select
                    id="history-kind"
                    size="lg"
                    options={examKindOptions}
                    value={filterState.kind || ALL_SENTINEL}
                    onValueChange={(value) =>
                      updateQuery({ kind: value === ALL_SENTINEL ? "" : value, page: 1 })
                    }
                  />
                </div>

                <div className="flex min-w-0 flex-[1_1_11rem] flex-col gap-1">
                  <label htmlFor="history-scope" className="text-caption font-medium text-text-secondary">
                    {t("filters.scope")}
                  </label>
                  <Select
                    id="history-scope"
                    size="lg"
                    options={scopeOptions}
                    value={filterState.scope || ALL_SENTINEL}
                    onValueChange={(value) =>
                      updateQuery({
                        scope:
                          value === ALL_SENTINEL
                            ? ""
                            : isValidHistoryScope(value)
                              ? value
                              : "",
                        page: 1,
                      })
                    }
                  />
                </div>

                <div className="flex min-w-0 flex-[1_1_11rem] flex-col gap-1">
                  <label htmlFor="history-from" className="text-caption font-medium text-text-secondary">
                    {t("filters.from")}
                  </label>
                  <Input
                    id="history-from"
                    type="date"
                    size="lg"
                    max={filterState.to || undefined}
                    value={filterState.from}
                    onChange={(e) => updateQuery({ from: e.target.value, page: 1 })}
                    className={touchFieldText}
                  />
                </div>

                <div className="flex min-w-0 flex-[1_1_11rem] flex-col gap-1">
                  <label htmlFor="history-to" className="text-caption font-medium text-text-secondary">
                    {t("filters.to")}
                  </label>
                  <Input
                    id="history-to"
                    type="date"
                    size="lg"
                    min={filterState.from || undefined}
                    value={filterState.to}
                    onChange={(e) => updateQuery({ to: e.target.value, page: 1 })}
                    className={touchFieldText}
                  />
                </div>

                {activeFilters && (
                  <Button
                    type="button"
                    variant="secondary"
                    className="h-12"
                    onClick={clearAllFilters}
                  >
                    {t("filters.clear")}
                  </Button>
                )}
              </form>

              {filterState.examId && (
                <div className="flex flex-wrap items-center gap-2 pt-1">
                  <span className="inline-flex min-h-10 items-center gap-2 rounded-full border border-border bg-subtle py-1.5 pr-2 pl-3 text-caption font-medium text-text">
                    {filteredExamName ? (
                      <span className="min-w-0 wrap-break-word">
                        {t.rich("examFilterChip", {
                          name: filteredExamName,
                          examContent: (chunks) => (
                            <span data-exam-content className="font-semibold">
                              {chunks}
                            </span>
                          ),
                        })}
                      </span>
                    ) : (
                      <span>{t("examFilterUnknown")}</span>
                    )}
                    <button
                      type="button"
                      aria-label={t("examFilterClear")}
                      onClick={() => updateQuery({ examId: "", page: 1 })}
                      className={cn(
                        "relative inline-flex size-6 shrink-0 items-center justify-center rounded-full text-text-muted hover:bg-surface hover:text-text after:absolute after:-inset-2.5",
                        focusRing,
                      )}
                    >
                      <X aria-hidden className="size-3.5" />
                    </button>
                  </span>
                </div>
              )}
            </section>

            {isHistoryOffline && (
              <div className="rounded-2xl border border-border bg-surface p-6">
                <EmptyState
                  headingLevel={2}
                  icon={<WifiOff />}
                  title={common("offline.title")}
                  description={common("offline.description")}
                  action={
                    <Button
                      type="button"
                      variant="secondary"
                      className="h-11"
                      onClick={() => void historyQuery.refetch()}
                    >
                      {common("retry")}
                    </Button>
                  }
                />
              </div>
            )}

            {!isHistoryOffline && historyQuery.isError && historyQuery.data && (
              <div
                role="alert"
                className="flex flex-wrap items-center justify-between gap-3 rounded-xl border border-warning bg-warning-bg px-4 py-3 text-body"
              >
                <div className="flex flex-col gap-0.5">
                  <span className="font-semibold text-warning">{t("stale.title")}</span>
                  <span className="text-text-secondary">
                    {staleUpdatedText
                      ? t("stale.description", { time: staleUpdatedText })
                      : t("stale.descriptionNoTime")}
                  </span>
                </div>
                <Button
                  type="button"
                  variant="secondary"
                  className="h-11"
                  onClick={() => void historyQuery.refetch()}
                >
                  {common("retry")}
                </Button>
              </div>
            )}

            {!isHistoryOffline && historyQuery.isLoading && !historyQuery.data && (
              <div
                role="status"
                aria-busy="true"
                aria-label={t("loading")}
                className="flex flex-col gap-3 rounded-2xl border border-border bg-surface p-5"
              >
                <Skeleton className="h-10 w-full" />
                <Skeleton className="h-14 w-full" />
                <Skeleton className="h-14 w-full" />
                <Skeleton className="h-14 w-full" />
              </div>
            )}

            {!isHistoryOffline && historyQuery.isError && !historyQuery.data && (
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
                <h2 className="text-title font-semibold text-text">{t("error.title")}</h2>
                <p className="max-w-sm text-body text-text-muted">{t("error.description")}</p>
                <Button
                  type="button"
                  variant="secondary"
                  className="h-11"
                  onClick={() => void historyQuery.refetch()}
                >
                  {common("retry")}
                </Button>
              </div>
            )}

            {!isHistoryOffline && historyQuery.data && items.length === 0 && (
              <div className="rounded-2xl border border-border bg-surface p-6">
                {filterState.page > 1 && historyQuery.data.totalItems > 0 ? (
                  <EmptyState
                    headingLevel={2}
                    icon={<SearchX />}
                    title={t("empty.outOfRangeTitle")}
                    description={t("empty.outOfRangeDescription")}
                    action={
                      <Button
                        type="button"
                        className="h-11"
                        onClick={() => updateQuery({ page: 1 }, { scroll: true })}
                      >
                        {t("empty.outOfRangeBack")}
                      </Button>
                    }
                  />
                ) : activeFilters ? (
                  <EmptyState
                    headingLevel={2}
                    icon={<SearchX />}
                    title={t("empty.title")}
                    description={t("empty.description")}
                    action={
                      <Button type="button" className="h-11" onClick={clearAllFilters}>
                        {t("empty.clearFilters")}
                      </Button>
                    }
                  />
                ) : (
                  <EmptyState
                    headingLevel={2}
                    icon={<SearchX />}
                    title={t("empty.noAttemptsTitle")}
                    description={t("empty.noAttemptsDescription")}
                    action={
                      <Button asChild className="h-11">
                        <Link href="/home">{t("empty.toMyExams")}</Link>
                      </Button>
                    }
                  />
                )}
              </div>
            )}

            {showItems && historyQuery.data && (
              <div
                ref={containerRef}
                aria-busy={historyQuery.isPlaceholderData || undefined}
                className={cn(
                  "flex min-w-0 flex-col gap-4 transition-opacity duration-150 motion-reduce:transition-none",
                  historyQuery.isPlaceholderData && "opacity-60",
                )}
              >
                {cardMode ? (
                  <CardList aria-label={t("listTitle")} className="rounded-2xl border border-border bg-surface">
                    {items.map((item) => {
                      const statusInfo = resolveStatusForItem(item);
                      const { scopeLabel, detail } = formatHistoryScopeDetail(item, subjectMap, locale, t);
                      const choices = formatAttemptChoices(item.choices, subjectMap, locale, (code) =>
                        t(`choiceOptions.${code}`),
                      );
                      const showScore = shouldShowAttemptScore(
                        item.state,
                        item.regrading,
                        item.displayScore,
                        item.rawScore,
                      );
                      const scoreVal = item.displayScore ?? item.rawScore ?? 0;
                      const maxVal = item.scaleMax ?? item.maxScore;
                      const started = formatAttemptDateTime(item.startedAt);
                      const href = resolveAttemptHref(item);
                      const isContinue = item.action === "CONTINUE";
                      const scoreText =
                        showScore && maxVal != null
                          ? `${formatScoreValue(scoreVal, locale)}/${formatScoreValue(maxVal, locale)}`
                          : showScore
                            ? formatScoreValue(scoreVal, locale)
                            : t("noScore");

                      return (
                        <CardListItem key={item.attemptId}>
                          <div className="min-w-0 flex-1">
                            <ListCard
                              title={
                                isContinue ? (
                                  <span data-exam-content>{item.examName}</span>
                                ) : (
                                  <ListCardTitleLink href={href} linkComponent={Link}>
                                    <span data-exam-content>{item.examName}</span>
                                  </ListCardTitleLink>
                                )
                              }
                              caption={
                                item.compensated ? (
                                  <span className="text-info">{t("fault.compensated")}</span>
                                ) : item.faultFlag === "SYSTEM_FAULT" ? (
                                  <span className="text-warning">{t("fault.systemFault")}</span>
                                ) : undefined
                              }
                              status={
                                <StatusChip tone={statusInfo.tone}>
                                  {t(`states.${statusInfo.key}`)}
                                </StatusChip>
                              }
                              tags={
                                <>
                                  <ListCardTag>
                                    {detail ? `${scopeLabel} · ${detail}` : scopeLabel}
                                  </ListCardTag>
                                  {item.examKindCode === "VACT" && showScore && (
                                    <ListCardTag>{t("vactPracticeNote")}</ListCardTag>
                                  )}
                                  {item.regrading && <ListCardTag>{t("regradingNote")}</ListCardTag>}
                                </>
                              }
                              meta={[
                                { label: t("columns.score"), value: scoreText },
                                {
                                  label: t("columns.choice"),
                                  value: choices.length > 0 ? choices.join(" · ") : t("noChoice"),
                                },
                                {
                                  label: t("columns.attemptStarted"),
                                  value: [
                                    item.attemptNo != null ? t("attemptNo", { count: item.attemptNo }) : null,
                                    started,
                                  ]
                                    .filter(Boolean)
                                    .join(" · "),
                                },
                              ]}
                            >
                              {isContinue && (
                                <div className="pt-1">
                                  <Button asChild variant="exam" className="h-11">
                                    <Link href={href}>{t("actions.continue")}</Link>
                                  </Button>
                                </div>
                              )}
                            </ListCard>
                          </div>
                        </CardListItem>
                      );
                    })}
                  </CardList>
                ) : (
                  <div
                    ref={tableScrollerRef}
                    role={tableOverflows ? "region" : undefined}
                    aria-label={tableOverflows ? t("tableLabel") : undefined}
                    tabIndex={tableOverflows ? 0 : undefined}
                    className={cn("overflow-x-auto rounded-2xl border border-border bg-surface", focusRingInset)}
                  >
                    <table aria-label={t("tableLabel")} className="w-full border-collapse text-left">
                      <thead>
                        <tr className="border-b border-border bg-table-head text-caption font-semibold text-text-muted">
                          <th scope="col" className="min-w-44 px-4 py-3.5">
                            {t("columns.exam")}
                          </th>
                          <th scope="col" className="px-3 py-3.5">
                            {t("columns.scope")}
                          </th>
                          <th scope="col" className="px-3 py-3.5">
                            {t("columns.choice")}
                          </th>
                          <th scope="col" className="px-3 py-3.5 whitespace-nowrap">
                            {t("columns.attemptStarted")}
                          </th>
                          <th scope="col" className="px-3 py-3.5 text-right whitespace-nowrap">
                            {t("columns.score")}
                          </th>
                          <th scope="col" className="px-3 py-3.5 whitespace-nowrap">
                            {t("columns.status")}
                          </th>
                          <th scope="col" className="px-4 py-3.5 text-right whitespace-nowrap">
                            {t("columns.actions")}
                          </th>
                        </tr>
                      </thead>
                      <tbody className="divide-y divide-border text-body">
                        {items.map((item) => {
                          const statusInfo = resolveStatusForItem(item);
                          const { scopeLabel, detail } = formatHistoryScopeDetail(item, subjectMap, locale, t);
                          const choices = formatAttemptChoices(item.choices, subjectMap, locale, (code) =>
                            t(`choiceOptions.${code}`),
                          );
                          const showScore = shouldShowAttemptScore(
                            item.state,
                            item.regrading,
                            item.displayScore,
                            item.rawScore,
                          );
                          const scoreVal = item.displayScore ?? item.rawScore ?? 0;
                          const maxVal = item.scaleMax ?? item.maxScore;
                          const started = formatAttemptDateTime(item.startedAt);
                          const href = resolveAttemptHref(item);

                          return (
                            <tr key={item.attemptId} className="align-middle hover:bg-subtle">
                              <td className="min-w-44 px-4 py-3.5">
                                <div className="flex flex-col gap-0.5">
                                  <span data-exam-content className="font-semibold text-text">
                                    {item.examName}
                                  </span>
                                  {item.compensated && (
                                    <span className="text-caption font-medium text-info">
                                      {t("fault.compensated")}
                                    </span>
                                  )}
                                  {!item.compensated && item.faultFlag === "SYSTEM_FAULT" && (
                                    <span className="text-caption font-medium text-warning">
                                      {t("fault.systemFault")}
                                    </span>
                                  )}
                                </div>
                              </td>
                              <td className="px-3 py-3.5 text-text-secondary">
                                <div className="flex flex-col gap-0.5">
                                  <span>{scopeLabel}</span>
                                  {detail && <span className="text-caption text-text-muted">{detail}</span>}
                                </div>
                              </td>
                              <td className="px-3 py-3.5 text-text-secondary">
                                {choices.length > 0 ? choices.join(" · ") : t("noChoice")}
                              </td>
                              <td className="px-3 py-3.5 whitespace-nowrap text-text-secondary tabular-nums">
                                <div className="flex flex-col gap-0.5">
                                  {item.attemptNo != null && (
                                    <span className="font-medium text-text">
                                      {t("attemptNo", { count: item.attemptNo })}
                                    </span>
                                  )}
                                  {started && <span className="text-caption text-text-muted">{started}</span>}
                                </div>
                              </td>
                              <td className="px-3 py-3.5 text-right whitespace-nowrap tabular-nums">
                                {showScore ? (
                                  <div className="flex flex-col items-end gap-0.5">
                                    <span className="font-bold text-text">
                                      {formatScoreValue(scoreVal, locale)}
                                      {maxVal != null && (
                                        <span className="font-medium text-text-muted">
                                          /{formatScoreValue(maxVal, locale)}
                                        </span>
                                      )}
                                    </span>
                                    {item.examKindCode === "VACT" && (
                                      <span className="text-caption text-warning">
                                        {t("vactPracticeNote")}
                                      </span>
                                    )}
                                    {item.regrading && (
                                      <span className="text-caption text-text-muted">
                                        {t("regradingNote")}
                                      </span>
                                    )}
                                  </div>
                                ) : (
                                  <span className="text-text-muted">{t("noScore")}</span>
                                )}
                              </td>
                              <td className="px-3 py-3.5 whitespace-nowrap">
                                <StatusChip tone={statusInfo.tone}>
                                  {t(`states.${statusInfo.key}`)}
                                </StatusChip>
                              </td>
                              <td className="px-4 py-3.5 text-right whitespace-nowrap">
                                <Button
                                  asChild
                                  variant={item.action === "CONTINUE" ? "exam" : "secondary"}
                                  className="h-11"
                                >
                                  <Link href={href}>
                                    {item.action === "CONTINUE"
                                      ? t("actions.continue")
                                      : t("actions.viewResult")}
                                  </Link>
                                </Button>
                              </td>
                            </tr>
                          );
                        })}
                      </tbody>
                    </table>
                  </div>
                )}

                {historyQuery.data.totalPages > 1 && (
                  <Pagination
                    className="justify-center pt-2"
                    page={filterState.page}
                    pageSize={historyQuery.data.size || HISTORY_PAGE_SIZE}
                    total={historyQuery.data.totalItems}
                    onPageChange={(nextPage) => updateQuery({ page: nextPage }, { scroll: true })}
                    labels={{ nav: t("paginationLabel"), range: () => "" }}
                  />
                )}
              </div>
            )}
          </>
        )}
      </main>
    </div>
  );
}

function resolveStatusForItem(item: AttemptHistoryItem) {
  return resolveHistoryStatus(item.state, item.regrading);
}
