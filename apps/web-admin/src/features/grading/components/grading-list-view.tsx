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
  ListCardTitleLink,
  LiveStatus,
  PageHeader,
  Pagination,
  StatusChip,
  Table,
  type TableCards,
} from "@thi360/ui";
import { normalizeUiLocale } from "@thi360/web-kit/preferences";
import { ClipboardCheck, Eye, PenLine, TriangleAlert, WifiOff } from "lucide-react";
import { useFormatter, useLocale, useTranslations } from "next-intl";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { useEffect, useMemo, useRef, useState } from "react";
import { defaultGradingApiDeps, resolveGradingErrorMessage, type GradingApiDeps } from "../api";
import { useGradingCounts, useGradingExamKinds, useGradingItems } from "../queries";
import { GRADING_STATUS_TONE, gradingItemHref, shortAttemptCode } from "../status";
import type { GradingQueueItemView, GradingQueueState } from "../types";
import { GradingFiltersBar, resolveExamKindLabel } from "./grading-filters";
import {
  DEFAULT_GRADING_FILTERS,
  GRADING_PAGE_SIZES,
  hasActiveGradingFilters,
  toGradingCountsQuery,
  toListGradingQuery,
  useGradingFiltersUrlState,
} from "./grading-filters-state";

/**
 * Hide secondary columns on narrower desktop containers (DESIGN.md 5.9).
 * Under 640px, `Table` automatically renders `ListCard`.
 */
const DROP_LOW_PRIORITY_COLUMNS = [
  "@container",
  "@max-[70rem]:[&_:is(th,td):has([data-col=grader])]:hidden",
  "@max-[58rem]:[&_:is(th,td):has([data-col=kind])]:hidden",
].join(" ");

export interface GradingListViewProps {
  deps?: Partial<GradingApiDeps>;
}

/** Anonymous essay grading queue (`/grading`, LT §9.2, BS §11). */
export function GradingListView({ deps }: GradingListViewProps) {
  const t = useTranslations("grading");
  const tErrors = useTranslations("grading.errors");
  const format = useFormatter();
  const uiLocale = normalizeUiLocale(useLocale());
  const router = useRouter();
  const api = useMemo(() => ({ ...defaultGradingApiDeps, ...deps }), [deps]);

  const [filters, setFilters] = useGradingFiltersUrlState();
  const listParams = useMemo(() => toListGradingQuery(filters), [filters]);
  const countsParams = useMemo(() => toGradingCountsQuery(filters), [filters]);

  const listQuery = useGradingItems(listParams, api);
  const countsQuery = useGradingCounts(countsParams, api);
  const examKindsQuery = useGradingExamKinds(api);

  const [announcement, setAnnouncement] = useState("");
  const [announcedKey, setAnnouncedKey] = useState<string | null>(null);
  const searchInputRef = useRef<HTMLInputElement>(null);

  const items = useMemo(() => listQuery.data?.items ?? [], [listQuery.data]);
  const totalItems = listQuery.data?.totalItems ?? 0;
  const totalPages = Math.max(1, listQuery.data?.totalPages ?? 1);
  const hasFilters = hasActiveGradingFilters(filters);

  const pageOutOfRange =
    listQuery.isSuccess && !listQuery.isPlaceholderData && totalItems > 0 && filters.page > totalPages;
  useEffect(() => {
    if (pageOutOfRange) setFilters({ page: totalPages });
  }, [pageOutOfRange, totalPages, setFilters]);

  const filterKey = JSON.stringify({
    state: listParams.state,
    exam: listParams.exam,
    kind: listParams.kind,
    from: listParams.from,
    to: listParams.to,
  });
  if (listQuery.isSuccess && !listQuery.isPlaceholderData && announcedKey !== filterKey) {
    setAnnouncedKey(filterKey);
    if (announcedKey !== null) setAnnouncement(t("list.filteredCount", { count: totalItems }));
  }

  const isOffline =
    listQuery.fetchStatus === "paused" &&
    (listQuery.isPending || listQuery.isPlaceholderData);

  const kindLabel = useMemo(
    () => (code: string | undefined) => resolveExamKindLabel(code, examKindsQuery.data, uiLocale),
    [examKindsQuery.data, uiLocale],
  );

  const columns = useMemo<ColumnDef<GradingQueueItemView>[]>(
    () => [
      {
        id: "item",
        header: t("list.table.item"),
        cell: ({ row }) => {
          const item = row.original;
          const itemId = item.itemId ?? "";
          const attemptCode = shortAttemptCode(item.attemptId);
          return (
            <div className="flex min-w-36 max-w-52 flex-col gap-0.5">
              <div className="flex flex-wrap items-center gap-1.5">
                <Link
                  href={gradingItemHref(itemId)}
                  className={cn(
                    "w-fit rounded-sm font-mono text-label font-semibold text-text hover:text-primary-text hover:underline",
                    focusRing,
                  )}
                >
                  #{attemptCode} · {t("list.questionNo", { number: item.displayNo ?? 0 })}
                </Link>
                {item.regrading && (
                  <StatusChip tone="warning">{t("list.regrading")}</StatusChip>
                )}
              </div>
              {item.examName && (
                <span data-exam-content className="line-clamp-2 text-caption font-medium text-text-secondary">
                  {item.examName}
                </span>
              )}
              {item.partName && (
                <span data-exam-content className="line-clamp-1 text-caption text-text-muted">
                  {item.partName}
                </span>
              )}
            </div>
          );
        },
      },
      {
        id: "kind",
        header: () => <span data-col="kind">{t("list.table.kind")}</span>,
        cell: ({ row }) => (
          <span
            data-col="kind"
            className="line-clamp-2 max-w-40 text-caption leading-snug wrap-break-word text-text-secondary"
          >
            {kindLabel(row.original.examKindCode) || "—"}
          </span>
        ),
      },
      {
        id: "submittedAt",
        header: t("list.table.submittedAt"),
        cell: ({ row }) => (
          <span className="text-caption whitespace-nowrap text-text tabular-nums">
            {row.original.submittedAt
              ? format.dateTime(new Date(row.original.submittedAt), "dateTime")
              : "—"}
          </span>
        ),
      },
      {
        id: "score",
        header: () => <span className="block text-end">{t("list.table.score")}</span>,
        cell: ({ row }) => (
          <div className="flex min-w-16 justify-end text-end">
            <ScoreText item={row.original} />
          </div>
        ),
      },
      {
        id: "grader",
        header: () => <span data-col="grader">{t("list.table.grader")}</span>,
        cell: ({ row }) => {
          const item = row.original;
          return (
            <span
              data-col="grader"
              className={cn("block min-w-28", item.graderName ? "text-text" : "text-text-muted")}
            >
              {item.graderName
                ? item.claimExpired
                  ? t("list.graderExpired", { name: item.graderName })
                  : item.graderName
                : t("list.noGrader")}
            </span>
          );
        },
      },
      {
        id: "status",
        header: t("list.table.status"),
        cell: ({ row }) => {
          const state: GradingQueueState = row.original.state ?? "WAITING";
          return (
            <StatusChip tone={GRADING_STATUS_TONE[state]}>{t(`status.${state}`)}</StatusChip>
          );
        },
      },
      {
        id: "actions",
        header: () => <span className="block text-end">{t("list.table.actions")}</span>,
        cell: ({ row }) => (
          <div className="flex items-center justify-end">
            <RowAction
              item={row.original}
              onOpen={(id) => router.push(gradingItemHref(id))}
            />
          </div>
        ),
      },
    ],
    [t, format, router, kindLabel],
  );

  const cards = useMemo<TableCards<GradingQueueItemView>>(
    () => ({
      render: (item) => {
        const itemId = item.itemId ?? "";
        const attemptCode = shortAttemptCode(item.attemptId);
        const state: GradingQueueState = item.state ?? "WAITING";
        return (
          <ListCard
            title={
              <ListCardTitleLink linkComponent={Link} href={gradingItemHref(itemId)}>
                #{attemptCode} · {t("list.questionNo", { number: item.displayNo ?? 0 })}
              </ListCardTitleLink>
            }
            caption={
              item.examName ? (
                <span data-exam-content>{item.examName}</span>
              ) : undefined
            }
            status={<StatusChip tone={GRADING_STATUS_TONE[state]}>{t(`status.${state}`)}</StatusChip>}
            tags={
              <>
                {item.examKindCode && (
                  <span className="inline-flex min-h-6 max-w-full items-center rounded-full border border-border bg-surface px-2.5 py-0.5 text-caption leading-snug whitespace-normal wrap-break-word text-text-secondary">
                    {kindLabel(item.examKindCode)}
                  </span>
                )}
                {item.regrading && (
                  <StatusChip tone="warning">{t("list.regrading")}</StatusChip>
                )}
              </>
            }
            meta={[
              ...(item.partName
                ? [{ label: t("list.table.part"), value: <span data-exam-content>{item.partName}</span> }]
                : []),
              {
                label: t("list.table.submittedAt"),
                value: item.submittedAt
                  ? format.dateTime(new Date(item.submittedAt), "dateTime")
                  : "—",
              },
              { label: t("list.table.score"), value: <ScoreText item={item} /> },
              {
                label: t("list.table.grader"),
                value: item.graderName
                  ? item.claimExpired
                    ? t("list.graderExpired", { name: item.graderName })
                    : item.graderName
                  : t("list.noGrader"),
              },
            ]}
          />
        );
      },
    }),
    [t, format, kindLabel],
  );

  return (
    <div className="flex flex-col gap-5">
      <PageHeader title={t("list.title")} description={t("list.description")} />

      <GradingFiltersBar
        filters={filters}
        onChange={setFilters}
        examKinds={examKindsQuery.data}
        counts={countsQuery.data}
        searchInputRef={searchInputRef}
      />

      <Card padding="none" className="overflow-hidden">
        <LiveStatus message={announcement} />
        <div className="flex min-w-0 flex-wrap items-center justify-between gap-2 border-b border-border px-4 py-3">
          <p className="text-body font-medium text-text tabular-nums">
            {listQuery.isLoading
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
            getRowId={(row) => row.itemId ?? `${row.attemptId}-${row.displayNo}`}
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
                    <Button variant="secondary" onClick={() => void listQuery.refetch()}>
                      {t("list.retry")}
                    </Button>
                  }
                />
              ) : listQuery.isError ? (
                <EmptyState
                  icon={<TriangleAlert />}
                  title={t("list.errorTitle")}
                  description={resolveGradingErrorMessage(listQuery.error, tErrors)}
                  headingLevel={2}
                  action={
                    <Button variant="secondary" onClick={() => void listQuery.refetch()}>
                      {t("list.retry")}
                    </Button>
                  }
                />
              ) : undefined
            }
            empty={
              <EmptyState
                icon={<ClipboardCheck />}
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
                        setFilters({ ...DEFAULT_GRADING_FILTERS, size: filters.size })
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

        {totalItems > 0 && (
          <div className="border-t border-border px-4 py-3">
            <Pagination
              page={filters.page}
              pageSize={filters.size}
              total={totalItems}
              onPageChange={(page) => setFilters({ page })}
              pageSizeOptions={[...GRADING_PAGE_SIZES]}
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

function ScoreText({ item }: { item: GradingQueueItemView }) {
  const t = useTranslations("grading.list");
  const format = useFormatter();
  const max = format.number(item.pointsMax ?? 0);
  if (item.state === "DONE" && item.earnedPoints !== undefined && item.earnedPoints !== null) {
    return (
      <span className="font-semibold whitespace-nowrap text-text tabular-nums">
        {t("scoreValue", { score: format.number(item.earnedPoints), max })}
      </span>
    );
  }
  return (
    <span className="whitespace-nowrap text-text-muted tabular-nums">
      {t("scorePending", { max })}
    </span>
  );
}

function RowAction({
  item,
  onOpen,
}: {
  item: GradingQueueItemView;
  onOpen: (itemId: string) => void;
}) {
  const t = useTranslations("grading.list.actions");
  const done = item.state === "DONE";
  const code = shortAttemptCode(item.attemptId);
  const number = item.displayNo ?? 0;
  return (
    <IconButton
      icon={done ? <Eye /> : <PenLine />}
      aria-label={done ? t("viewAria", { code, number }) : t("gradeAria", { code, number })}
      tooltip={done ? t("viewTooltip") : t("gradeTooltip")}
      onClick={() => {
        if (item.itemId) onOpen(item.itemId);
      }}
    />
  );
}

export function GradingListSkeleton() {
  return (
    <div className="flex flex-col gap-5" aria-busy="true">
      <div className="h-12 w-80 max-w-full animate-pulse rounded-lg bg-subtle" />
      <div className="h-36 animate-pulse rounded-xl bg-subtle" />
      <div className="h-96 animate-pulse rounded-xl bg-subtle" />
    </div>
  );
}
