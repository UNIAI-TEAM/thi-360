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
  Tabs,
  TabsContent,
  TabsList,
  TabsTrigger,
  cn,
  focusRing,
} from "@thi360/ui";
import { Plus, Search, Tag, X } from "lucide-react";
import { useLocale, useTranslations } from "next-intl";
import Link from "next/link";
import { usePathname, useRouter, useSearchParams } from "next/navigation";
import { useCallback, useEffect, useMemo, useRef, useState } from "react";

import { usePermissions } from "@/features/session/session-provider";

import { formatPromotionDateTime } from "../datetime";
import { resolvePromotionErrorMessage } from "../errors";
import { formatDiscountSummary } from "../format";
import { usePromotionsListQuery } from "../queries";
import type { PromotionKind, PromotionStateFilter, PromotionSummary } from "../types";
import { PromotionCapWarningInline } from "./promotion-cap-warning-inline";
import { PromotionRowActions } from "./promotion-row-actions";
import { PromotionStatusChip } from "./promotion-status-chip";

const PAGE_SIZE = 20;
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

function parseInitialKind(raw: string | null): PromotionKind {
  return raw === "VOUCHER" ? "VOUCHER" : "AUTO";
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

  const [kind, setKind] = useState<PromotionKind>(() => parseInitialKind(searchParams.get("kind")));
  const [stateFilter, setStateFilter] = useState<PromotionStateFilter>(() =>
    parseInitialStateFilter(searchParams.get("state")),
  );
  const [searchInput, setSearchInput] = useState<string>(() => searchParams.get("q") ?? "");
  const [debouncedQ, setDebouncedQ] = useState<string>(() => searchParams.get("q") ?? "");
  const [page, setPage] = useState<number>(() => parseInitialPage(searchParams.get("page")));
  const [liveMessage, setLiveMessage] = useState<string>("");
  const searchTimerRef = useRef<number | null>(null);

  const syncUrl = useCallback(
    (nextKind: PromotionKind, nextState: PromotionStateFilter, nextQ: string, nextPage: number) => {
      const next = new URLSearchParams();
      if (nextKind !== "AUTO") next.set("kind", nextKind);
      if (nextState !== "ALL") next.set("state", nextState);
      if (nextQ.trim()) next.set("q", nextQ.trim());
      if (nextPage > 1) next.set("page", String(nextPage));
      const qs = next.toString();
      router.replace(qs ? `${pathname}?${qs}` : pathname, { scroll: false });
    },
    [pathname, router],
  );

  useEffect(() => {
    return () => {
      if (searchTimerRef.current !== null) {
        window.clearTimeout(searchTimerRef.current);
      }
    };
  }, []);

  const handleSearchChange = (nextValue: string) => {
    if (searchTimerRef.current !== null) {
      window.clearTimeout(searchTimerRef.current);
      searchTimerRef.current = null;
    }
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

  const handleTabChange = (value: string) => {
    const nextKind: PromotionKind = value === "VOUCHER" ? "VOUCHER" : "AUTO";
    setKind(nextKind);
    setPage(1);
    syncUrl(nextKind, stateFilter, debouncedQ, 1);
  };

  const handleStateFilterChange = (nextState: PromotionStateFilter) => {
    setStateFilter(nextState);
    setPage(1);
    syncUrl(kind, nextState, debouncedQ, 1);
  };

  const handleClearFilters = () => {
    if (searchTimerRef.current !== null) {
      window.clearTimeout(searchTimerRef.current);
      searchTimerRef.current = null;
    }
    setStateFilter("ALL");
    setSearchInput("");
    setDebouncedQ("");
    setPage(1);
    syncUrl(kind, "ALL", "", 1);
  };

  const handlePageChange = (nextPage: number) => {
    setPage(nextPage);
    syncUrl(kind, stateFilter, debouncedQ, nextPage);
  };

  const scopeSummaryText = useCallback(
    (item: PromotionSummary) => {
      if (item.scope === "EXAM") return t("list.scopeSummary.EXAM");
      if (item.scope === "EXAM_KIND") return t("list.scopeSummary.EXAM_KIND");
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
            onMutationConflict={() => void listQuery.refetch()}
          />
        ),
      });
    }

    return baseCols;
  }, [audienceSummaryText, canModify, listQuery, locale, scopeSummaryText, t]);

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
                onMutationConflict={() => void listQuery.refetch()}
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
    [audienceSummaryText, canModify, listQuery, locale, scopeSummaryText, t],
  );

  const items = listQuery.data?.items ?? [];
  const totalItems = listQuery.data?.totalItems ?? 0;
  const isFiltered = stateFilter !== "ALL" || debouncedQ.trim() !== "";

  const statusAnnouncement = liveMessage
    ? liveMessage
    : listQuery.isLoading || listQuery.isPlaceholderData || listQuery.isError
      ? ""
      : t("list.summaryCount", { total: totalItems });

  const errorNode = listQuery.isError ? (
    <EmptyState
      title={t("list.errorTitle")}
      description={resolvePromotionErrorMessage(listQuery.error, tErrors)}
      action={
        <Button variant="secondary" onClick={() => void listQuery.refetch()}>
          {t("actions.retry")}
        </Button>
      }
    />
  ) : undefined;

  const createCtaLabel = kind === "VOUCHER" ? t("actions.createVoucher") : t("actions.create");

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
            <Link href={`/promotions/new?kind=${kind}`}>
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
              value={searchInput}
              onChange={(event) => handleSearchChange(event.target.value)}
              placeholder={t("list.searchPlaceholder")}
              aria-label={t("list.searchAria")}
              className="pr-9 pl-9"
            />
            {searchInput && (
              <button
                type="button"
                aria-label={t("actions.clearFilters")}
                onClick={() => handleSearchChange("")}
                className={cn(
                  "absolute top-1/2 right-2 inline-flex size-7 -translate-y-1/2 items-center justify-center rounded-md text-text-muted hover:bg-subtle hover:text-text",
                  focusRing,
                )}
              >
                <X aria-hidden className="size-4" />
              </button>
            )}
          </div>

          <div
            role="radiogroup"
            aria-label={t("list.stateGroupAria")}
            className="flex flex-wrap items-center gap-1.5"
          >
            {STATE_FILTER_OPTIONS.map((option) => {
              const selected = stateFilter === option;
              return (
                <button
                  key={option}
                  type="button"
                  role="radio"
                  aria-checked={selected}
                  onClick={() => handleStateFilterChange(option)}
                  className={cn(
                    "inline-flex h-8 items-center rounded-full border px-3 text-caption font-medium transition-colors duration-150 motion-reduce:transition-none",
                    selected
                      ? "border-primary-fill bg-primary-fill text-on-primary"
                      : "border-border bg-surface text-text-secondary hover:bg-subtle",
                    focusRing,
                  )}
                >
                  {t(`list.stateFilters.${option}`)}
                </button>
              );
            })}
          </div>
        </div>

        {!listQuery.isLoading && !listQuery.isError && (
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
          refreshing={listQuery.isFetching && !listQuery.isLoading}
          error={errorNode}
          empty={emptyNode}
          cards={cards}
          stickyColumns={canModify ? { start: true, end: true } : { start: true }}
          aria-label={t("list.title")}
        />
      </div>

      {totalItems > PAGE_SIZE && (
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
              <Link href={`/promotions/new?kind=${kind}`}>
                <Plus />
                <span>{createCtaLabel}</span>
              </Link>
            </Button>
          ) : undefined
        }
      />

      <LiveStatus message={statusAnnouncement} />

      <Tabs value={kind} onValueChange={handleTabChange} variant="content">
        <TabsList>
          <TabsTrigger value="AUTO">{t("list.tabs.AUTO")}</TabsTrigger>
          <TabsTrigger value="VOUCHER">{t("list.tabs.VOUCHER")}</TabsTrigger>
        </TabsList>
        <TabsContent value="AUTO" className="mt-4">
          {kind === "AUTO" ? tablePanel : null}
        </TabsContent>
        <TabsContent value="VOUCHER" className="mt-4">
          {kind === "VOUCHER" ? tablePanel : null}
        </TabsContent>
      </Tabs>
    </div>
  );
}
