"use client";

import type { ColumnDef } from "@tanstack/react-table";
import {
  Button,
  Card,
  cn,
  EmptyState,
  focusRing,
  IconButton,
  ListCard,
  ListCardTag,
  ListCardTitleLink,
  LiveStatus,
  PageHeader,
  Pagination,
  Skeleton,
  StatusChip,
  Table,
  type TableCards,
} from "@thi360/ui";
import { normalizeUiLocale } from "@thi360/web-kit/preferences";
import { Eye, Timer, TriangleAlert, WifiOff } from "lucide-react";
import { useFormatter, useLocale, useTranslations } from "next-intl";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { useEffect, useMemo, useRef, useState } from "react";
import { DISPLAY_NUMBER_FORMAT } from "@/features/exams/display-number";
import { defaultAttemptApiDeps, resolveAttemptErrorMessage, type AttemptApiDeps } from "../api";
import {
  hasAttemptScore,
  isKnownAbortReason,
  isKnownEndReason,
  isKnownScope,
  resolveExamKindLabel,
} from "../format";
import { useAdminAttempts, useAttemptExamKinds } from "../queries";
import { ATTEMPT_STATE_TONE, attemptDetailHref, shortAttemptCode } from "../status";
import type { AdminAttemptSummary, AttemptState } from "../types";
import { AttemptFiltersBar } from "./attempt-filters";
import {
  ATTEMPTS_PAGE_SIZES,
  DEFAULT_ATTEMPT_FILTERS,
  hasActiveAttemptFilters,
  toListAttemptsQuery,
  useAttemptFiltersUrlState,
} from "./attempt-filters-state";

/**
 * Hide lower-priority columns on narrower desktop containers (DESIGN.md 5.9).
 * Under 640px, `Table` switches automatically to `ListCard`.
 */
const DROP_LOW_PRIORITY_COLUMNS = [
  "@container",
  "@max-[74rem]:[&_:is(th,td):has([data-col=attemptNo])]:hidden",
  "@max-[66rem]:[&_:is(th,td):has([data-col=finishedAt])]:hidden",
  "@max-[62rem]:[&_:is(th,td):has([data-col=startedAt])]:hidden",
].join(" ");

export interface AttemptListViewProps {
  deps?: Partial<AttemptApiDeps>;
}

/** Admin attempt operations list (`/attempts`, AC-10, AC-28). */
export function AttemptListView({ deps }: AttemptListViewProps) {
  const t = useTranslations("attempts");
  const tErrors = useTranslations("attempts.errors");
  const format = useFormatter();
  const uiLocale = normalizeUiLocale(useLocale());
  const router = useRouter();
  const api = useMemo(() => ({ ...defaultAttemptApiDeps, ...deps }), [deps]);

  const [filters, setFilters] = useAttemptFiltersUrlState();
  const listParams = useMemo(() => toListAttemptsQuery(filters), [filters]);

  const listQuery = useAdminAttempts(listParams, api);
  const examKindsQuery = useAttemptExamKinds(api);

  const [announcement, setAnnouncement] = useState("");
  const [announcedKey, setAnnouncedKey] = useState<string | null>(null);
  const userInputRef = useRef<HTMLInputElement>(null);

  const isOffline =
    listQuery.fetchStatus === "paused" &&
    (listQuery.isPending || listQuery.isPlaceholderData);

  const items = useMemo(
    () => (isOffline ? [] : (listQuery.data?.items ?? [])),
    [isOffline, listQuery.data],
  );
  const totalItems = isOffline ? 0 : (listQuery.data?.totalItems ?? 0);
  const totalPages = Math.max(1, listQuery.data?.totalPages ?? 1);
  const hasFilters = hasActiveAttemptFilters(filters);

  const pageOutOfRange =
    listQuery.isSuccess && !listQuery.isPlaceholderData && totalItems > 0 && filters.page > totalPages;
  useEffect(() => {
    if (pageOutOfRange) setFilters({ page: totalPages });
  }, [pageOutOfRange, totalPages, setFilters]);

  const filterKey = JSON.stringify({
    state: listParams.state,
    user: listParams.user,
    exam: listParams.exam,
    from: listParams.from,
    to: listParams.to,
  });
  if (listQuery.isSuccess && !listQuery.isPlaceholderData && announcedKey !== filterKey) {
    setAnnouncedKey(filterKey);
    if (announcedKey !== null) setAnnouncement(t("list.filteredCount", { count: totalItems }));
  }

  const kindLabel = useMemo(
    () => (code: string | undefined) => resolveExamKindLabel(code, examKindsQuery.data, uiLocale),
    [examKindsQuery.data, uiLocale],
  );

  const scopeLabel = useMemo(
    () => (scope: string | undefined) => (isKnownScope(scope) ? t(`scope.${scope}`) : scope ?? ""),
    [t],
  );

  const endReasonText = useMemo(
    () => (item: AdminAttemptSummary) => {
      if (isKnownEndReason(item.endReason)) return t(`endReason.${item.endReason}`);
      if (isKnownAbortReason(item.abortReason)) return t(`abortReason.${item.abortReason}`);
      return "";
    },
    [t],
  );

  const formatScore = useMemo(
    () => (item: AdminAttemptSummary) => {
      if (!hasAttemptScore(item)) return "—";
      const earned = format.number(item.displayScore!, DISPLAY_NUMBER_FORMAT);
      if (item.maxScore != null && Number.isFinite(item.maxScore)) {
        return t("list.scoreValue", {
          score: earned,
          max: format.number(item.maxScore, DISPLAY_NUMBER_FORMAT),
        });
      }
      return earned;
    },
    [format, t],
  );

  const columns = useMemo<ColumnDef<AdminAttemptSummary>[]>(
    () => [
      {
        id: "code",
        header: t("list.table.code"),
        cell: ({ row }) => {
          const item = row.original;
          const attemptId = item.attemptId ?? "";
          const code = shortAttemptCode(item.attemptId);
          return (
            <Link
              href={attemptDetailHref(attemptId)}
              className={cn(
                "inline-block w-fit rounded-sm font-mono text-label font-semibold whitespace-nowrap text-primary-text hover:underline",
                focusRing,
              )}
            >
              {code}
            </Link>
          );
        },
      },
      {
        id: "learner",
        header: t("list.table.learner"),
        cell: ({ row }) => {
          const item = row.original;
          const primary = item.username?.trim() || shortAttemptCode(item.userId);
          return (
            <div className="flex min-w-28 max-w-36 flex-col gap-0.5">
              <span className="truncate font-medium text-text">{primary}</span>
              {item.userFullName && (
                <span className="line-clamp-1 text-caption text-text-muted">{item.userFullName}</span>
              )}
            </div>
          );
        },
      },
      {
        id: "exam",
        header: t("list.table.exam"),
        cell: ({ row }) => {
          const item = row.original;
          const kind = kindLabel(item.examKindCode);
          const scope = scopeLabel(item.scope);
          const metaParts = [kind, scope, item.partCode].filter(Boolean);
          return (
            <div className="flex min-w-36 max-w-48 flex-col gap-0.5">
              {item.examName ? (
                <span data-exam-content className="line-clamp-2 font-medium wrap-anywhere text-text">
                  {item.examName}
                </span>
              ) : (
                <span className="text-text-muted">—</span>
              )}
              {metaParts.length > 0 && (
                <span className="text-caption text-text-muted">{metaParts.join(" · ")}</span>
              )}
            </div>
          );
        },
      },
      {
        id: "attemptNo",
        header: () => (
          <span data-col="attemptNo" className="block text-end">
            {t("list.table.attemptNo")}
          </span>
        ),
        cell: ({ row }) => (
          <span data-col="attemptNo" className="block text-end text-text tabular-nums">
            {row.original.attemptNo != null ? format.number(row.original.attemptNo) : "—"}
          </span>
        ),
      },
      {
        id: "startedAt",
        header: () => <span data-col="startedAt">{t("list.table.startedAt")}</span>,
        cell: ({ row }) => (
          <span data-col="startedAt" className="text-caption whitespace-nowrap text-text tabular-nums">
            {row.original.startedAt
              ? format.dateTime(new Date(row.original.startedAt), "dateTime")
              : "—"}
          </span>
        ),
      },
      {
        id: "finishedAt",
        header: () => <span data-col="finishedAt">{t("list.table.finishedAt")}</span>,
        cell: ({ row }) => {
          const item = row.original;
          const reason = endReasonText(item);
          return (
            <div data-col="finishedAt" className="flex flex-col gap-0.5">
              <span className="whitespace-nowrap text-text tabular-nums">
                {item.finishedAt ? format.dateTime(new Date(item.finishedAt), "dateTime") : "—"}
              </span>
              {reason && <span className="text-caption text-text-muted">{reason}</span>}
            </div>
          );
        },
      },
      {
        id: "status",
        header: t("list.table.status"),
        cell: ({ row }) => {
          const item = row.original;
          const state: AttemptState = item.state ?? "STARTING";
          return (
            <div className="flex max-w-48 flex-wrap items-center gap-1.5">
              <StatusChip tone={ATTEMPT_STATE_TONE[state]}>{t(`status.${state}`)}</StatusChip>
              {item.faultFlag && (
                <StatusChip tone="warning">
                  {item.compensated ? t("list.faultCompensated") : t("list.faultFlag")}
                </StatusChip>
              )}
              {item.regrading && (
                <StatusChip tone="info">{t("list.regrading")}</StatusChip>
              )}
            </div>
          );
        },
      },
      {
        id: "score",
        header: () => <span className="block text-end">{t("list.table.score")}</span>,
        cell: ({ row }) => (
          <span
            className={cn(
              "block text-end whitespace-nowrap tabular-nums",
              hasAttemptScore(row.original) ? "font-semibold text-text" : "text-text-muted",
            )}
          >
            {formatScore(row.original)}
          </span>
        ),
      },
      {
        id: "actions",
        header: () => <span className="block text-end">{t("list.table.actions")}</span>,
        cell: ({ row }) => {
          const item = row.original;
          const code = shortAttemptCode(item.attemptId);
          const learner = item.username?.trim() || shortAttemptCode(item.userId);
          return (
            <div className="flex items-center justify-end">
              <IconButton
                icon={<Eye />}
                aria-label={t("list.viewAria", { code, user: learner })}
                tooltip={t("list.viewTooltip")}
                onClick={() => {
                  if (item.attemptId) router.push(attemptDetailHref(item.attemptId));
                }}
              />
            </div>
          );
        },
      },
    ],
    [t, format, router, kindLabel, scopeLabel, endReasonText, formatScore],
  );

  const cards = useMemo<TableCards<AdminAttemptSummary>>(
    () => ({
      render: (item) => {
        const attemptId = item.attemptId ?? "";
        const code = shortAttemptCode(item.attemptId);
        const state: AttemptState = item.state ?? "STARTING";
        const kind = kindLabel(item.examKindCode);
        const scope = scopeLabel(item.scope);
        const learner = [item.username?.trim(), item.userFullName?.trim()].filter(Boolean).join(" · ");
        const reason = endReasonText(item);
        return (
          <ListCard
            title={
              <ListCardTitleLink linkComponent={Link} href={attemptDetailHref(attemptId)}>
                <span className="font-mono">{code}</span>
              </ListCardTitleLink>
            }
            caption={learner || undefined}
            status={<StatusChip tone={ATTEMPT_STATE_TONE[state]}>{t(`status.${state}`)}</StatusChip>}
            tags={
              <>
                {kind && (
                  <span className="inline-flex min-h-6 max-w-full items-center rounded-full border border-border bg-surface px-2.5 py-0.5 text-caption leading-snug whitespace-normal wrap-break-word text-text-secondary">
                    {kind}
                  </span>
                )}
                {scope && <ListCardTag>{scope}</ListCardTag>}
                {item.faultFlag && (
                  <StatusChip tone="warning">
                    {item.compensated ? t("list.faultCompensated") : t("list.faultFlag")}
                  </StatusChip>
                )}
                {item.regrading && <StatusChip tone="info">{t("list.regrading")}</StatusChip>}
              </>
            }
            meta={[
              ...(item.examName
                ? [{ label: t("list.table.exam"), value: <span data-exam-content>{item.examName}</span> }]
                : []),
              ...(item.attemptNo != null
                ? [{ label: t("list.table.attemptNo"), value: format.number(item.attemptNo) }]
                : []),
              {
                label: t("list.table.startedAt"),
                value: item.startedAt ? format.dateTime(new Date(item.startedAt), "dateTime") : "—",
              },
              ...(item.finishedAt || reason
                ? [
                    {
                      label: t("list.table.finishedAt"),
                      value: [
                        item.finishedAt
                          ? format.dateTime(new Date(item.finishedAt), "dateTime")
                          : "—",
                        reason,
                      ]
                        .filter(Boolean)
                        .join(" · "),
                    },
                  ]
                : []),
              { label: t("list.table.score"), value: formatScore(item) },
            ]}
          />
        );
      },
    }),
    [t, format, kindLabel, scopeLabel, endReasonText, formatScore],
  );

  return (
    <div className="flex flex-col gap-5">
      <PageHeader title={t("list.title")} description={t("list.description")} />

      <AttemptFiltersBar
        filters={filters}
        onChange={setFilters}
        userInputRef={userInputRef}
      />

      <Card padding="none" className="overflow-hidden">
        <LiveStatus message={announcement} />
        <div className="flex min-w-0 flex-wrap items-center justify-between gap-2 border-b border-border px-4 py-3">
          <p className="text-body font-medium text-text tabular-nums">
            {isOffline
              ? t("list.offlineTitle")
              : listQuery.isLoading
                ? t("list.loadingCount")
                : listQuery.isError
                  ? t("list.errorCount")
                  : t.rich("list.filteredCountRich", {
                      count: totalItems,
                      strong: (chunks) => <strong className="font-semibold">{chunks}</strong>,
                    })}
          </p>
          <p className="text-caption text-text-muted">{t("list.hint")}</p>
        </div>

        <div className={DROP_LOW_PRIORITY_COLUMNS}>
          <Table
            aria-label={t("list.tableAriaLabel")}
            stickyColumns={{ start: true, end: true }}
            columns={columns}
            data={items}
            getRowId={(row) => row.attemptId ?? `${row.userId}-${row.createdAt}`}
            loading={listQuery.isLoading && !isOffline}
            loadingRows={filters.size}
            refreshing={listQuery.isPlaceholderData && !isOffline}
            cards={cards}
            error={
              isOffline ? (
                <EmptyState
                  icon={<WifiOff />}
                  title={t("list.offlineTitle")}
                  description={t("list.offlineDescription")}
                  headingLevel={2}
                  action={
                    <Button
                      variant="secondary"
                      onClick={() => {
                        void listQuery.refetch();
                        void examKindsQuery.refetch();
                      }}
                    >
                      {t("list.retry")}
                    </Button>
                  }
                />
              ) : listQuery.isError ? (
                <EmptyState
                  icon={<TriangleAlert />}
                  title={t("list.errorTitle")}
                  description={resolveAttemptErrorMessage(listQuery.error, tErrors)}
                  headingLevel={2}
                  action={
                    <Button
                      variant="secondary"
                      onClick={() => {
                        void listQuery.refetch();
                        void examKindsQuery.refetch();
                      }}
                    >
                      {t("list.retry")}
                    </Button>
                  }
                />
              ) : undefined
            }
            empty={
              <EmptyState
                icon={<Timer />}
                title={hasFilters ? t("list.emptyFilteredTitle") : t("list.emptyTitle")}
                description={
                  hasFilters ? t("list.emptyFilteredDescription") : t("list.emptyDescription")
                }
                headingLevel={2}
                action={
                  hasFilters ? (
                    <Button
                      variant="secondary"
                      onClick={() =>
                        setFilters({ ...DEFAULT_ATTEMPT_FILTERS, size: filters.size })
                      }
                    >
                      {t("filters.clearAll")}
                    </Button>
                  ) : undefined
                }
              />
            }
          />
        </div>

        {!isOffline && !listQuery.isError && totalItems > 0 && (
          <div className="border-t border-border px-4 py-3">
            <Pagination
              page={filters.page}
              pageSize={filters.size}
              total={totalItems}
              onPageChange={(page) => setFilters({ page })}
              pageSizeOptions={[...ATTEMPTS_PAGE_SIZES]}
              onPageSizeChange={(size) => setFilters({ size })}
              labels={{
                nav: t("list.paginationNav"),
                previous: t("list.paginationPrevious"),
                next: t("list.paginationNext"),
                rowsPerPage: t("list.paginationRowsPerPage"),
                page: (page) => t("list.paginationPage", { page: format.number(page) }),
                range: ({ from, to, total }) =>
                  t("list.paginationRange", {
                    from: format.number(from),
                    to: format.number(to),
                    total: format.number(total),
                  }),
              }}
            />
          </div>
        )}
      </Card>
    </div>
  );
}

export function AttemptListSkeleton() {
  return (
    <div className="flex flex-col gap-5" aria-busy="true">
      <Skeleton className="h-12 w-80 max-w-full" />
      <Skeleton className="h-40" />
      <Skeleton className="h-96" />
    </div>
  );
}
