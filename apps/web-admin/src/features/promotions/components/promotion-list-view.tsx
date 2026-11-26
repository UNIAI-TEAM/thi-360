"use client";

import type { ColumnDef } from "@tanstack/react-table";
import {
  Button,
  Card,
  EmptyState,
  Input,
  ListCard,
  ListCardTag,
  ListCardTitleLink,
  LiveStatus,
  PageHeader,
  Pagination,
  Table,
  type TableCards,
  StatusChip,
  cn,
  focusRing,
} from "@thi360/ui";
import { Plus, Search, Tag, WifiOff, X } from "lucide-react";
import { useLocale, useTranslations } from "next-intl";
import Link from "next/link";
import { usePathname, useRouter, useSearchParams } from "next/navigation";
import { useCallback, useEffect, useMemo, useRef, useState } from "react";

import { usePermissions } from "@/features/session/session-provider";

import { formatPromotionDateTime } from "../datetime";
import { resolvePromotionErrorMessage } from "../errors";
import { formatDiscountSummary } from "../format";
import { usePromotionsListQuery } from "../queries";
import type { PromotionKind, PromotionKindFilter, PromotionStateFilter, PromotionSummary } from "../types";
import { PromotionCapWarningInline } from "./promotion-cap-warning-inline";
import { PromotionRowActions } from "./promotion-row-actions";
import { PromotionStatusChip } from "./promotion-status-chip";
import { handleRadioGroupKeyDown } from "./radio-group-key";

const PAGE_SIZE = 20;
const FILTER_PILL_CLASS =
  "relative inline-flex h-8 items-center rounded-full border px-3 text-caption font-medium transition-colors duration-150 pointer-coarse:after:absolute pointer-coarse:after:-inset-1.5 motion-reduce:transition-none";
const FILTER_PILL_SELECTED = "border-primary-fill bg-primary-fill text-on-primary";
const FILTER_PILL_IDLE = "border-border bg-surface text-text-secondary hover:bg-subtle";
const KIND_FILTER_OPTIONS: readonly PromotionKindFilter[] = ["ALL", "AUTO", "VOUCHER"];
const STATE_FILTER_OPTIONS: readonly PromotionStateFilter[] = [
  "ALL",
  "RUNNING",
  "UPCOMING",
  "ENDED",
  "STOPPED",
];

/**
 * Ẩn cột phụ theo bề rộng vùng chứa (DESIGN.md mục 5.9): dưới 68rem (1280px có sidebar) ẩn Phạm vi & đối tượng
 * và tóm tắt ở cột đầu; dưới 52rem ẩn thêm Hiệu lực.
 */
const DROP_LOW_PRIORITY_COLUMNS = [
  "@container",
  "@max-[68rem]:[&_:is(th,td):has([data-col=scopeAndAudience])]:hidden",
  "@max-[52rem]:[&_:is(th,td):has([data-col=period])]:hidden",
].join(" ");

function parseInitialKind(raw: string | null): PromotionKindFilter {
  return raw === "AUTO" || raw === "VOUCHER" ? raw : "ALL";
}

function PromotionKindChip({ kind }: { kind: PromotionKind }) {
  const t = useTranslations("promotions.kinds");
  return <StatusChip tone={kind === "AUTO" ? "info" : "neutral"}>{t(kind)}</StatusChip>;
}

function parseInitialStateFilter(raw: string | null): PromotionStateFilter {
  if (raw === "UPCOMING" || raw === "RUNNING" || raw === "ENDED" || raw === "STOPPED") {
    return raw;
  }
  return "ALL";
}

function parseInitialPage(raw: string | null): number {
  const num = raw ? Number(raw) : 1;
  return Number.isSafeInteger(num) && num >= 1 ? num : 1;
}

export function PromotionListView() {
  const t = useTranslations("promotions");
  const tErrors = useTranslations("promotions.errors");
  const locale = useLocale();
  const { can } = usePermissions();
  const canModify = can("PROMOTION.MODIFY");

  const router = useRouter();
  const pathname = usePathname();
  const searchParams = useSearchParams();

  const searchParamsKey = searchParams.toString();
  const [prevSearchKey, setPrevSearchKey] = useState(searchParamsKey);

  const [kind, setKind] = useState<PromotionKindFilter>(() => parseInitialKind(searchParams.get("kind")));
  const [stateFilter, setStateFilter] = useState<PromotionStateFilter>(() =>
    parseInitialStateFilter(searchParams.get("state")),
  );
  const [searchInput, setSearchInput] = useState<string>(() => searchParams.get("q") ?? "");
  const [debouncedQ, setDebouncedQ] = useState<string>(() => searchParams.get("q") ?? "");
  const [page, setPage] = useState<number>(() => parseInitialPage(searchParams.get("page")));
  const [liveMessage, setLiveMessage] = useState<string>("");
  const [deleteFocusSeq, setDeleteFocusSeq] = useState(0);
  const searchTimerRef = useRef<number | null>(null);
  const searchInputRef = useRef<HTMLInputElement>(null);

  if (searchParamsKey !== prevSearchKey) {
    setPrevSearchKey(searchParamsKey);
    const nextKind = parseInitialKind(searchParams.get("kind"));
    const nextState = parseInitialStateFilter(searchParams.get("state"));
    const nextQ = searchParams.get("q") ?? "";
    const nextPage = parseInitialPage(searchParams.get("page"));
    setKind(nextKind);
    setStateFilter(nextState);
    setSearchInput(nextQ);
    setDebouncedQ(nextQ);
    setPage(nextPage);
    setLiveMessage("");
  }

  const cancelPendingSearch = useCallback(() => {
    if (searchTimerRef.current !== null) {
      window.clearTimeout(searchTimerRef.current);
      searchTimerRef.current = null;
    }
  }, []);

  const syncUrl = useCallback(
    (nextKind: PromotionKindFilter, nextState: PromotionStateFilter, nextQ: string, nextPage: number) => {
      const next = new URLSearchParams();
      if (nextKind !== "ALL") next.set("kind", nextKind);
      if (nextState !== "ALL") next.set("state", nextState);
      if (nextQ.trim()) next.set("q", nextQ.trim());
      if (nextPage > 1) next.set("page", String(nextPage));
      const qs = next.toString();
      router.replace(qs ? `${pathname}?${qs}` : pathname, { scroll: false });
    },
    [pathname, router],
  );

  useEffect(() => {
    cancelPendingSearch();
    return cancelPendingSearch;
  }, [cancelPendingSearch, searchParamsKey]);

  useEffect(() => {
    if (deleteFocusSeq > 0) {
      searchInputRef.current?.focus();
    }
  }, [deleteFocusSeq]);

  const handleSearchChange = (nextValue: string) => {
    cancelPendingSearch();
    setLiveMessage("");
    setSearchInput(nextValue);
    if (nextValue === "") {
      setDebouncedQ("");
      setPage(1);
      syncUrl(kind, stateFilter, "", 1);
      return;
    }
    searchTimerRef.current = window.setTimeout(() => {
      setDebouncedQ(nextValue);
      setPage(1);
      syncUrl(kind, stateFilter, nextValue, 1);
      searchTimerRef.current = null;
    }, 300);
  };

  const listQuery = usePromotionsListQuery({
    kind,
    stateFilter,
    q: debouncedQ,
    page: page - 1,
    size: PAGE_SIZE,
  });
  const refetchList = listQuery.refetch;
  const isOffline =
    listQuery.fetchStatus === "paused" && (listQuery.isPlaceholderData || !listQuery.data);

  const maxPage =
    !isOffline && listQuery.data && !listQuery.isPlaceholderData
      ? Math.max(1, Math.ceil(listQuery.data.totalItems / PAGE_SIZE))
      : null;

  if (maxPage !== null && page > maxPage) {
    setPage(maxPage);
  }

  const prevPageRef = useRef(page);
  useEffect(() => {
    if (maxPage !== null && prevPageRef.current > maxPage && page <= maxPage) {
      syncUrl(kind, stateFilter, debouncedQ, page);
    }
    prevPageRef.current = page;
  }, [debouncedQ, kind, maxPage, page, stateFilter, syncUrl]);

  const handleKindFilterChange = (nextKind: PromotionKindFilter) => {
    cancelPendingSearch();
    setLiveMessage("");
    setKind(nextKind);
    setDebouncedQ(searchInput);
    setPage(1);
    syncUrl(nextKind, stateFilter, searchInput, 1);
  };

  const handleStateFilterChange = (nextState: PromotionStateFilter) => {
    cancelPendingSearch();
    setLiveMessage("");
    setStateFilter(nextState);
    setDebouncedQ(searchInput);
    setPage(1);
    syncUrl(kind, nextState, searchInput, 1);
  };

  const handleClearFilters = () => {
    cancelPendingSearch();
    setLiveMessage("");
    setKind("ALL");
    setStateFilter("ALL");
    setSearchInput("");
    setDebouncedQ("");
    setPage(1);
    syncUrl("ALL", "ALL", "", 1);
  };

  const handlePageChange = (nextPage: number) => {
    cancelPendingSearch();
    setLiveMessage("");
    setPage(nextPage);
    syncUrl(kind, stateFilter, debouncedQ, nextPage);
  };

  const handleMutationConflict = useCallback(() => {
    void refetchList();
  }, [refetchList]);

  const handleRowDeleted = useCallback(() => {
    setDeleteFocusSeq((seq) => seq + 1);
  }, []);

  const scopeSummaryText = useCallback(
    (item: PromotionSummary) => {
      if (item.scope === "EXAM") return t("list.scopeSummary.EXAM");
      if (item.scope === "EXAM_KIND") return t("list.scopeSummary.EXAM_KIND");
      if (item.scope === "BUNDLE") return t("list.scopeSummary.BUNDLE");
      return t("list.scopeSummary.ALL");
    },
    [t],
  );

  const audienceSummaryText = useCallback(
    (item: PromotionSummary) => {
      if (item.kind === "AUTO") return t("list.audienceSummary.ALL");
      if (item.audience === "NEW_ACCOUNT") return t("list.audienceSummary.NEW_ACCOUNT");
      if (item.audience === "SELECTED") return t("list.audienceSummary.SELECTED");
      return t("list.audienceSummary.ALL");
    },
    [t],
  );

  const columns = useMemo<ColumnDef<PromotionSummary>[]>(() => {
    const baseCols: ColumnDef<PromotionSummary>[] = [
      {
        id: "nameAndCode",
        header: t("list.columns.nameAndCode"),
        enableSorting: false,
        cell: ({ row }) => {
          const item = row.original;
          const summaryParts = [
            scopeSummaryText(item),
            ...(item.kind === "VOUCHER" ? [audienceSummaryText(item)] : []),
          ];
          return (
            <div className="flex min-w-40 max-w-[13rem] flex-col gap-0.5 py-1">
              <Link
                href={`/promotions/${item.id}`}
                className={cn("font-semibold text-primary-text hover:underline", focusRing)}
              >
                <span data-exam-content className="line-clamp-2 wrap-anywhere">
                  {item.name}
                </span>
              </Link>
              {item.code && (
                <span data-exam-content className="font-mono text-caption wrap-anywhere text-text-muted">
                  {item.code}
                </span>
              )}
              <span className="hidden text-caption text-text-muted @max-[68rem]:block">
                {summaryParts.join(" · ")}
              </span>
              <PromotionCapWarningInline warnings={item.capWarnings} />
            </div>
          );
        },
      },
      {
        id: "kind",
        header: t("list.columns.kind"),
        enableSorting: false,
        cell: ({ row }) => <PromotionKindChip kind={row.original.kind} />,
      },
      {
        id: "discount",
        header: t("list.columns.discount"),
        enableSorting: false,
        cell: ({ row }) => {
          const item = row.original;
          return (
            <div className="flex max-w-[8rem] flex-col gap-0.5 tabular-nums">
              <span className="font-medium text-text">
                {formatDiscountSummary(
                  item.discountType,
                  item.discountValue,
                  item.maxDiscountVnd,
                  locale,
                  (k, v) => t(`discountLabels.${k}`, v),
                )}
              </span>
              {item.kind === "VOUCHER" && item.perUserLimit != null && (
                <span className="text-caption text-text-muted">
                  {t("discountLabels.perUserOnce", { limit: item.perUserLimit })}
                </span>
              )}
            </div>
          );
        },
      },
      {
        id: "scopeAndAudience",
        header: () => <span data-col="scopeAndAudience">{t("list.columns.scopeAndAudience")}</span>,
        enableSorting: false,
        cell: ({ row }) => {
          const item = row.original;
          return (
            <div data-col="scopeAndAudience" className="flex min-w-28 flex-col gap-0.5">
              <span className="text-body text-text">{scopeSummaryText(item)}</span>
              {item.kind === "VOUCHER" && (
                <span className="text-caption text-text-muted">{audienceSummaryText(item)}</span>
              )}
            </div>
          );
        },
      },
      {
        id: "period",
        header: () => <span data-col="period">{t("list.columns.period")}</span>,
        enableSorting: false,
        cell: ({ row }) => (
          <div
            data-col="period"
            className="flex flex-col gap-0.5 whitespace-nowrap text-caption text-text-secondary tabular-nums"
          >
            <span>{formatPromotionDateTime(row.original.startsAt, locale)}</span>
            <span className="text-text-muted">{formatPromotionDateTime(row.original.endsAt, locale)}</span>
          </div>
        ),
      },
      {
        id: "used",
        header: t("list.columns.used"),
        enableSorting: false,
        cell: ({ row }) => {
          const item = row.original;
          return (
            <span className="block max-w-[6.5rem] text-text tabular-nums">
              {item.totalLimit != null
                ? t("detail.metrics.usageWithLimit", { used: item.usedCount, limit: item.totalLimit })
                : t("detail.metrics.usageWithoutLimit", { used: item.usedCount })}
            </span>
          );
        },
      },
      {
        id: "status",
        header: t("list.columns.status"),
        enableSorting: false,
        cell: ({ row }) => (
          <PromotionStatusChip status={row.original.status} state={row.original.state} />
        ),
      },
    ];

    if (canModify) {
      baseCols.push({
        id: "actions",
        header: () => <span className="block text-right">{t("list.columns.actions")}</span>,
        enableSorting: false,
        cell: ({ row }) => (
          <PromotionRowActions
            promotion={row.original}
            onAnnounce={setLiveMessage}
            onMutationConflict={handleMutationConflict}
            onDeleted={handleRowDeleted}
          />
        ),
      });
    }

    return baseCols;
  }, [
    audienceSummaryText,
    canModify,
    handleMutationConflict,
    handleRowDeleted,
    locale,
    scopeSummaryText,
    t,
  ]);

  const cards = useMemo<TableCards<PromotionSummary>>(
    () => ({
      render: (item) => (
        <ListCard
          title={
            <ListCardTitleLink linkComponent={Link} href={`/promotions/${item.id}`}>
              <span data-exam-content>{item.name}</span>
            </ListCardTitleLink>
          }
          caption={item.code ? <span data-exam-content className="font-mono">{item.code}</span> : undefined}
          status={<PromotionStatusChip status={item.status} state={item.state} />}
          tags={
            <>
              <ListCardTag>{t(`kinds.${item.kind}`)}</ListCardTag>
              <ListCardTag>
                {formatDiscountSummary(
                  item.discountType,
                  item.discountValue,
                  item.maxDiscountVnd,
                  locale,
                  (k, v) => t(`discountLabels.${k}`, v),
                )}
              </ListCardTag>
              <ListCardTag>{scopeSummaryText(item)}</ListCardTag>
              {item.kind === "VOUCHER" && <ListCardTag>{audienceSummaryText(item)}</ListCardTag>}
            </>
          }
          actions={
            canModify ? (
              <PromotionRowActions
                promotion={item}
                onAnnounce={setLiveMessage}
                onMutationConflict={handleMutationConflict}
                onDeleted={handleRowDeleted}
              />
            ) : undefined
          }
          meta={[
            {
              label: t("list.columns.used"),
              value:
                item.totalLimit != null
                  ? t("detail.metrics.usageWithLimit", { used: item.usedCount, limit: item.totalLimit })
                  : t("detail.metrics.usageWithoutLimit", { used: item.usedCount }),
            },
            {
              label: t("list.columns.period"),
              value: `${formatPromotionDateTime(item.startsAt, locale)} - ${formatPromotionDateTime(item.endsAt, locale)}`,
            },
          ]}
        >
          <PromotionCapWarningInline warnings={item.capWarnings} />
        </ListCard>
      ),
    }),
    [
      audienceSummaryText,
      canModify,
      handleMutationConflict,
      handleRowDeleted,
      locale,
      scopeSummaryText,
      t,
    ],
  );

  const items = isOffline ? [] : (listQuery.data?.items ?? []);
  const totalItems = isOffline ? 0 : (listQuery.data?.totalItems ?? 0);
  const isFiltered = kind !== "ALL" || stateFilter !== "ALL" || debouncedQ.trim() !== "";

  const statusAnnouncement = liveMessage
    ? liveMessage
    : listQuery.isLoading || listQuery.isPlaceholderData || listQuery.isError || isOffline
      ? ""
      : t("list.summaryCount", { total: totalItems });

  const errorNode = isOffline ? (
    <EmptyState
      icon={<WifiOff />}
      title={t("list.offlineTitle")}
      description={t("list.offlineDescription")}
      action={
        <Button variant="secondary" onClick={() => void refetchList()}>
          {t("actions.retry")}
        </Button>
      }
    />
  ) : listQuery.isError ? (
    <EmptyState
      title={t("list.errorTitle")}
      description={resolvePromotionErrorMessage(listQuery.error, tErrors)}
      action={
        <Button variant="secondary" onClick={() => void refetchList()}>
          {t("actions.retry")}
        </Button>
      }
    />
  ) : undefined;

  const createCtaLabel = kind === "VOUCHER" ? t("actions.createVoucher") : t("actions.create");
  const createHref = kind === "ALL" ? "/promotions/new" : `/promotions/new?kind=${kind}`;

  const emptyNode = isFiltered ? (
    <EmptyState
      icon={<Search />}
      title={t("list.emptyFilteredTitle")}
      description={t("list.emptyFilteredDescription")}
      action={
        <Button variant="secondary" onClick={handleClearFilters}>
          {t("actions.clearFilters")}
        </Button>
      }
    />
  ) : (
    <EmptyState
      icon={<Tag />}
      title={t("list.emptyTitle")}
      description={t("list.emptyDescription")}
      action={
        canModify ? (
          <Button asChild>
            <Link href={createHref}>
              <Plus />
              <span>{createCtaLabel}</span>
            </Link>
          </Button>
        ) : undefined
      }
    />
  );

  const tablePanel = (
    <Card className="overflow-hidden p-0">
      <div className="flex flex-col gap-3 border-b border-border p-4">
        <div className="flex flex-col gap-3 lg:flex-row lg:items-center lg:justify-between">
          <div className="relative w-full lg:max-w-sm">
            <Search
              aria-hidden
              className="pointer-events-none absolute top-1/2 left-3 size-4 -translate-y-1/2 text-text-muted"
            />
            <Input
              ref={searchInputRef}
              value={searchInput}
              onChange={(event) => handleSearchChange(event.target.value)}
              placeholder={t("list.searchPlaceholder")}
              aria-label={t("list.searchAria")}
              className="pr-9 pl-9"
            />
            {searchInput && (
              <button
                type="button"
                aria-label={t("actions.clearSearch")}
                onClick={() => handleSearchChange("")}
                className={cn(
                  "absolute top-1/2 right-2 inline-flex size-7 -translate-y-1/2 items-center justify-center rounded-md text-text-muted hover:bg-subtle hover:text-text pointer-coarse:after:absolute pointer-coarse:after:-inset-2",
                  focusRing,
                )}
              >
                <X aria-hidden className="size-4" />
              </button>
            )}
          </div>

          <div className="flex flex-col gap-2 lg:items-end">
            <div
              role="radiogroup"
              aria-label={t("list.kindGroupAria")}
              className="flex flex-wrap items-center gap-1.5"
            >
              {KIND_FILTER_OPTIONS.map((option, index) => {
                const selected = kind === option;
                return (
                  <button
                    key={option}
                    type="button"
                    role="radio"
                    aria-checked={selected}
                    tabIndex={selected ? 0 : -1}
                    onClick={() => handleKindFilterChange(option)}
                    onKeyDown={(event) =>
                      handleRadioGroupKeyDown(event, KIND_FILTER_OPTIONS, index, handleKindFilterChange)
                    }
                    className={cn(FILTER_PILL_CLASS, selected ? FILTER_PILL_SELECTED : FILTER_PILL_IDLE, focusRing)}
                  >
                    {t(`list.kindFilters.${option}`)}
                  </button>
                );
              })}
            </div>

          <div
            role="radiogroup"
            aria-label={t("list.stateGroupAria")}
            className="flex flex-wrap items-center gap-1.5"
          >
            {STATE_FILTER_OPTIONS.map((option, index) => {
              const selected = stateFilter === option;
              return (
                <button
                  key={option}
                  type="button"
                  role="radio"
                  aria-checked={selected}
                  tabIndex={selected ? 0 : -1}
                  onClick={() => handleStateFilterChange(option)}
                  onKeyDown={(event) =>
                    handleRadioGroupKeyDown(event, STATE_FILTER_OPTIONS, index, handleStateFilterChange)
                  }
                  className={cn(FILTER_PILL_CLASS, selected ? FILTER_PILL_SELECTED : FILTER_PILL_IDLE, focusRing)}
                >
                  {t(`list.stateFilters.${option}`)}
                </button>
              );
            })}
          </div>
          </div>
        </div>

        {!listQuery.isLoading && !listQuery.isError && !isOffline && (
          <p className="text-caption text-text-muted tabular-nums">
            {t("list.summaryCount", { total: totalItems })}
          </p>
        )}
      </div>

      <div className={DROP_LOW_PRIORITY_COLUMNS}>
        <Table
          columns={columns}
          data={items}
          getRowId={(row) => row.id}
          loading={listQuery.isLoading}
          loadingRows={PAGE_SIZE}
          refreshing={listQuery.isFetching && !listQuery.isLoading && !isOffline}
          error={errorNode}
          empty={emptyNode}
          cards={cards}
          stickyColumns={canModify ? { start: true, end: true } : { start: true }}
          aria-label={t("list.title")}
        />
      </div>

      {!isOffline && !listQuery.isError && totalItems > PAGE_SIZE && (
        <div className="border-t border-border p-4">
          <Pagination
            page={page}
            pageSize={PAGE_SIZE}
            total={totalItems}
            onPageChange={handlePageChange}
          />
        </div>
      )}
    </Card>
  );

  return (
    <div className="flex flex-col gap-5">
      <PageHeader
        title={t("list.title")}
        description={t("list.description")}
        actions={
          canModify ? (
            <Button asChild>
              <Link href={createHref}>
                <Plus />
                <span>{createCtaLabel}</span>
              </Link>
            </Button>
          ) : undefined
        }
      />

      <LiveStatus message={statusAnnouncement} />

      {tablePanel}
    </div>
  );
}
