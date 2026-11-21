"use client";

import type { ColumnDef } from "@tanstack/react-table";
import {
  Button,
  Card,
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
  EmptyState,
  IconButton,
  Input,
  ListCard,
  ListCardTag,
  ListCardTitleLink,
  LiveStatus,
  PageHeader,
  Pagination,
  Select,
  type SelectOption,
  Table,
  type TableCards,
  Tooltip,
  cn,
  focusRing,
} from "@thi360/ui";
import { pickName } from "@thi360/web-kit/i18n";
import { normalizeUiLocale } from "@thi360/web-kit/preferences";
import {
  Ban,
  Eye,
  MoreHorizontal,
  Package,
  Pencil,
  Plus,
  RefreshCw,
  Search,
  Upload,
  WifiOff,
  X,
} from "lucide-react";
import { useLocale, useTranslations } from "next-intl";
import Link from "next/link";
import { usePathname, useRouter, useSearchParams } from "next/navigation";
import { useCallback, useEffect, useId, useMemo, useRef, useState } from "react";

import { usePermissions } from "@/features/session/session-provider";

import { resolveBundleErrorMessage } from "../errors";
import { formatBundleDateTime, formatNumber, formatVnd } from "../format";
import { useBundlesListQuery, useExamKindOptionsQuery } from "../queries";
import type { BundleStatusFilter, BundleSummaryResponse } from "../types";
import {
  type BundleActionTarget,
  BundleDiscontinueDialog,
  BundlePublishDialog,
} from "./bundle-action-dialogs";
import { BundleStatusBadges } from "./bundle-status-chips";

const DEFAULT_PAGE_SIZE = 20;
const PAGE_SIZE_OPTIONS = [10, 20, 50];
const STATUS_FILTER_OPTIONS: readonly BundleStatusFilter[] = [
  "ALL",
  "DRAFT",
  "PUBLISHED",
  "DISCONTINUED",
];

/**
 * Hide lower-priority columns progressively on narrower desktop containers (DESIGN.md 5.9).
 * Under 640px `Table` switches automatically to `ListCard` mode without duplicate DOM trees.
 */
const DROP_LOW_PRIORITY_COLUMNS = [
  "@container",
  "@max-[74rem]:[&_:is(th,td):has([data-col=updatedAt])]:hidden",
  "@max-[64rem]:[&_:is(th,td):has([data-col=attempts])]:hidden",
  "@max-[56rem]:[&_:is(th,td):has([data-col=itemCount])]:hidden",
].join(" ");

function parseStatusFilter(raw: string | null): BundleStatusFilter {
  if (raw === "DRAFT" || raw === "PUBLISHED" || raw === "DISCONTINUED") return raw;
  return "ALL";
}

function parsePage(raw: string | null): number {
  const num = raw ? Number(raw) : 1;
  return Number.isSafeInteger(num) && num >= 1 ? num : 1;
}

function parseSize(raw: string | null): number {
  const num = raw ? Number(raw) : DEFAULT_PAGE_SIZE;
  return PAGE_SIZE_OPTIONS.includes(num) ? num : DEFAULT_PAGE_SIZE;
}

export function BundleListView() {
  const t = useTranslations("bundles");
  const tErrors = useTranslations("bundles.errors");
  const locale = useLocale();
  const uiLocale = normalizeUiLocale(locale);
  const { can } = usePermissions();
  const canModify = can("EXAM.MODIFY");
  const canPublish = can("EXAM.PUBLISH");

  const router = useRouter();
  const pathname = usePathname();
  const searchParams = useSearchParams();
  const searchParamsKey = searchParams.toString();
  const [prevSearchKey, setPrevSearchKey] = useState(searchParamsKey);

  const [statusFilter, setStatusFilter] = useState<BundleStatusFilter>(() =>
    parseStatusFilter(searchParams.get("status")),
  );
  const [examKindFilter, setExamKindFilter] = useState<string>(() => searchParams.get("examKind") ?? "");
  const [searchInput, setSearchInput] = useState<string>(() => searchParams.get("q") ?? "");
  const [debouncedQ, setDebouncedQ] = useState<string>(() => searchParams.get("q") ?? "");
  const [page, setPage] = useState<number>(() => parsePage(searchParams.get("page")));
  const [pageSize, setPageSize] = useState<number>(() => parseSize(searchParams.get("size")));
  const [liveMessage, setLiveMessage] = useState<string>("");

  const [publishTarget, setPublishTarget] = useState<BundleActionTarget | null>(null);
  const [discontinueTarget, setDiscontinueTarget] = useState<BundleActionTarget | null>(null);

  const searchTimerRef = useRef<number | null>(null);
  const searchInputId = useId();
  const examKindSelectId = useId();

  if (searchParamsKey !== prevSearchKey) {
    setPrevSearchKey(searchParamsKey);
    const nextStatus = parseStatusFilter(searchParams.get("status"));
    const nextExamKind = searchParams.get("examKind") ?? "";
    const nextQ = searchParams.get("q") ?? "";
    const nextPage = parsePage(searchParams.get("page"));
    const nextSize = parseSize(searchParams.get("size"));
    setStatusFilter(nextStatus);
    setExamKindFilter(nextExamKind);
    setSearchInput(nextQ);
    setDebouncedQ(nextQ);
    setPage(nextPage);
    setPageSize(nextSize);
    setLiveMessage("");
  }

  const cancelPendingSearch = useCallback(() => {
    if (searchTimerRef.current !== null) {
      window.clearTimeout(searchTimerRef.current);
      searchTimerRef.current = null;
    }
  }, []);

  useEffect(() => {
    cancelPendingSearch();
    return cancelPendingSearch;
  }, [cancelPendingSearch, searchParamsKey]);

  const syncUrl = useCallback(
    (
      nextStatus: BundleStatusFilter,
      nextExamKind: string,
      nextQ: string,
      nextPage: number,
      nextSize: number,
    ) => {
      const next = new URLSearchParams();
      if (nextStatus !== "ALL") next.set("status", nextStatus);
      if (nextExamKind) next.set("examKind", nextExamKind);
      if (nextQ.trim()) next.set("q", nextQ.trim());
      if (nextPage > 1) next.set("page", String(nextPage));
      if (nextSize !== DEFAULT_PAGE_SIZE) next.set("size", String(nextSize));
      const qs = next.toString();
      router.replace(qs ? `${pathname}?${qs}` : pathname, { scroll: false });
    },
    [pathname, router],
  );

  const handleSearchChange = (nextValue: string) => {
    cancelPendingSearch();
    setLiveMessage("");
    setSearchInput(nextValue);
    if (nextValue === "") {
      setDebouncedQ("");
      setPage(1);
      syncUrl(statusFilter, examKindFilter, "", 1, pageSize);
      return;
    }
    searchTimerRef.current = window.setTimeout(() => {
      setDebouncedQ(nextValue);
      setPage(1);
      syncUrl(statusFilter, examKindFilter, nextValue, 1, pageSize);
      searchTimerRef.current = null;
    }, 300);
  };

  const handleStatusChange = (nextStatus: BundleStatusFilter) => {
    cancelPendingSearch();
    setLiveMessage("");
    setStatusFilter(nextStatus);
    setDebouncedQ(searchInput);
    setPage(1);
    syncUrl(nextStatus, examKindFilter, searchInput, 1, pageSize);
  };

  const handleExamKindChange = (value: string) => {
    cancelPendingSearch();
    setLiveMessage("");
    const nextKind = value === "__ALL__" ? "" : value;
    setExamKindFilter(nextKind);
    setDebouncedQ(searchInput);
    setPage(1);
    syncUrl(statusFilter, nextKind, searchInput, 1, pageSize);
  };

  const handleClearFilters = () => {
    cancelPendingSearch();
    setLiveMessage("");
    setStatusFilter("ALL");
    setExamKindFilter("");
    setSearchInput("");
    setDebouncedQ("");
    setPage(1);
    syncUrl("ALL", "", "", 1, pageSize);
  };

  const handlePageChange = (nextPage: number) => {
    cancelPendingSearch();
    setLiveMessage("");
    setPage(nextPage);
    syncUrl(statusFilter, examKindFilter, debouncedQ, nextPage, pageSize);
  };

  const handlePageSizeChange = (nextSize: number) => {
    cancelPendingSearch();
    setLiveMessage("");
    setPageSize(nextSize);
    setPage(1);
    syncUrl(statusFilter, examKindFilter, debouncedQ, 1, nextSize);
  };

  const examKindsQuery = useExamKindOptionsQuery(true);
  const listQuery = useBundlesListQuery({
    status: statusFilter,
    examKind: examKindFilter,
    q: debouncedQ,
    page: page - 1,
    size: pageSize,
  });

  const isOffline =
    listQuery.fetchStatus === "paused" && (listQuery.isLoading || listQuery.isPlaceholderData);

  const examKindOptions: SelectOption[] = useMemo(() => {
    const base: SelectOption[] = [{ value: "__ALL__", label: t("list.filters.examKindAll") }];
    for (const kind of examKindsQuery.data ?? []) {
      const label =
        pickName({ nameVi: kind.nameVi, nameEn: kind.nameEn }, uiLocale) || kind.code;
      base.push({ value: kind.code, label });
    }
    return base;
  }, [examKindsQuery.data, t, uiLocale]);

  const kindLabelOf = useCallback(
    (bundle: BundleSummaryResponse) =>
      pickName(
        { nameVi: bundle.examKindNameVi, nameEn: bundle.examKindNameEn },
        uiLocale,
      ) || bundle.examKindCode,
    [uiLocale],
  );

  const renderRowActions = useCallback(
    (bundle: BundleSummaryResponse) => {
      const canOfferPublish = canPublish && bundle.status !== "DISCONTINUED";
      const canOfferDiscontinue = canPublish && bundle.status === "PUBLISHED";
      const hasMenu = canOfferPublish || canOfferDiscontinue;
      const editOrViewLabel = canModify
        ? t("actions.editAria", { code: bundle.code })
        : t("actions.viewAria", { code: bundle.code });
      const editOrViewTooltip = canModify ? t("actions.edit") : t("actions.view");
      const toTarget = (): BundleActionTarget => ({
        id: bundle.id,
        code: bundle.code,
        name: bundle.name,
        revision: bundle.revision,
        status: bundle.status,
      });

      return (
        <div className="flex items-center justify-end gap-1">
          <Tooltip content={editOrViewTooltip}>
            <Link
              href={`/bundles/${bundle.id}`}
              aria-label={editOrViewLabel}
              className={cn(
                "inline-flex size-10 items-center justify-center rounded-lg text-text-secondary transition-colors hover:bg-subtle hover:text-text pointer-coarse:min-h-11 pointer-coarse:min-w-11",
                focusRing,
              )}
            >
              {canModify ? (
                <Pencil className="size-[18px]" aria-hidden />
              ) : (
                <Eye className="size-[18px]" aria-hidden />
              )}
            </Link>
          </Tooltip>
          {hasMenu && (
            <DropdownMenu>
              <DropdownMenuTrigger asChild>
                <IconButton
                  icon={<MoreHorizontal />}
                  aria-label={t("actions.moreAria", { code: bundle.code })}
                />
              </DropdownMenuTrigger>
              <DropdownMenuContent align="end">
                <DropdownMenuItem onSelect={() => router.push(`/bundles/${bundle.id}`)}>
                  {canModify ? <Pencil /> : <Eye />}
                  <span>{editOrViewTooltip}</span>
                </DropdownMenuItem>
                {canOfferPublish && (
                  <DropdownMenuItem onSelect={() => setPublishTarget(toTarget())}>
                    <Upload />
                    <span>
                      {bundle.status === "PUBLISHED"
                        ? t("actions.republish")
                        : t("actions.publish")}
                    </span>
                  </DropdownMenuItem>
                )}
                {canOfferDiscontinue && (
                  <>
                    <DropdownMenuSeparator />
                    <DropdownMenuItem
                      destructive
                      onSelect={() => setDiscontinueTarget(toTarget())}
                    >
                      <Ban />
                      <span>{t("actions.discontinue")}</span>
                    </DropdownMenuItem>
                  </>
                )}
              </DropdownMenuContent>
            </DropdownMenu>
          )}
        </div>
      );
    },
    [canModify, canPublish, router, t],
  );

  const columns = useMemo<ColumnDef<BundleSummaryResponse>[]>(
    () => [
      {
        id: "bundle",
        header: t("list.columns.bundle"),
        enableSorting: false,
        cell: ({ row }) => {
          const b = row.original;
          return (
            <div className="flex min-w-44 max-w-xs flex-col gap-0.5 py-1">
              <Link
                href={`/bundles/${b.id}`}
                className={cn("font-semibold text-primary-text hover:underline", focusRing)}
              >
                <span data-exam-content className="line-clamp-2 wrap-anywhere">
                  {b.name}
                </span>
              </Link>
              <span data-exam-content className="font-mono text-caption wrap-anywhere text-text-muted">
                {b.code}
              </span>
              {b.summary && (
                <span data-exam-content className="line-clamp-2 text-caption wrap-anywhere text-text-secondary">
                  {b.summary}
                </span>
              )}
              <div className="hidden flex-wrap gap-x-3 gap-y-0.5 text-caption tabular-nums @max-[74rem]:flex">
                <span
                  data-col-summary="itemCount"
                  className="hidden text-text-secondary @max-[56rem]:inline"
                >
                  {t("list.summary.itemCount", { count: b.itemCount })}
                </span>
                <span
                  data-col-summary="attempts"
                  className="hidden text-text-secondary @max-[64rem]:inline"
                >
                  {t("list.summary.attempts", {
                    value:
                      b.attemptsPerExam != null
                        ? formatNumber(b.attemptsPerExam, locale)
                        : t("list.notSet"),
                  })}
                </span>
                <span
                  data-col-summary="updatedAt"
                  className="hidden font-mono text-text-muted @max-[74rem]:inline"
                >
                  {t("list.summary.updatedAt", {
                    date: formatBundleDateTime(b.updatedAt, locale),
                  })}
                </span>
              </div>
            </div>
          );
        },
      },
      {
        id: "examKind",
        header: t("list.columns.examKind"),
        enableSorting: false,
        cell: ({ row }) => (
          <span className="whitespace-nowrap text-text">{kindLabelOf(row.original)}</span>
        ),
      },
      {
        id: "itemCount",
        header: () => (
          <span data-col="itemCount" className="block text-right">
            {t("list.columns.itemCount")}
          </span>
        ),
        enableSorting: false,
        cell: ({ row }) => (
          <span data-col="itemCount" className="block text-right text-text tabular-nums">
            {formatNumber(row.original.itemCount, locale)}
          </span>
        ),
      },
      {
        id: "price",
        header: () => <span className="block text-right">{t("list.columns.price")}</span>,
        enableSorting: false,
        cell: ({ row }) => (
          <span className="block text-right font-medium whitespace-nowrap text-text tabular-nums">
            {row.original.listPriceVnd != null
              ? formatVnd(row.original.listPriceVnd, locale)
              : t("list.notSet")}
          </span>
        ),
      },
      {
        id: "attempts",
        header: () => (
          <span data-col="attempts" className="block text-right">
            {t("list.columns.attempts")}
          </span>
        ),
        enableSorting: false,
        cell: ({ row }) => (
          <span data-col="attempts" className="block text-right text-text tabular-nums">
            {row.original.attemptsPerExam != null
              ? formatNumber(row.original.attemptsPerExam, locale)
              : t("list.notSet")}
          </span>
        ),
      },
      {
        id: "status",
        header: t("list.columns.status"),
        enableSorting: false,
        cell: ({ row }) => (
          <BundleStatusBadges
            status={row.original.status}
            saleState={row.original.saleState}
            hasUnpublishedChanges={row.original.hasUnpublishedChanges}
          />
        ),
      },
      {
        id: "updatedAt",
        header: () => <span data-col="updatedAt">{t("list.columns.updatedAt")}</span>,
        enableSorting: false,
        cell: ({ row }) => (
          <span
            data-col="updatedAt"
            className="whitespace-nowrap text-caption text-text-secondary tabular-nums"
          >
            {formatBundleDateTime(row.original.updatedAt, locale)}
          </span>
        ),
      },
      {
        id: "actions",
        header: () => <span className="block text-right">{t("list.columns.actions")}</span>,
        enableSorting: false,
        cell: ({ row }) => renderRowActions(row.original),
      },
    ],
    [kindLabelOf, locale, renderRowActions, t],
  );

  const cards = useMemo<TableCards<BundleSummaryResponse>>(
    () => ({
      render: (bundle) => (
        <ListCard
          title={
            <ListCardTitleLink linkComponent={Link} href={`/bundles/${bundle.id}`}>
              <span data-exam-content>{bundle.name}</span>
            </ListCardTitleLink>
          }
          caption={
            <span data-exam-content className="font-mono">
              {bundle.code}
            </span>
          }
          actions={renderRowActions(bundle)}
          status={
            <BundleStatusBadges
              status={bundle.status}
              saleState={bundle.saleState}
              hasUnpublishedChanges={bundle.hasUnpublishedChanges}
            />
          }
          tags={<ListCardTag>{kindLabelOf(bundle)}</ListCardTag>}
          meta={[
            {
              label: t("list.columns.itemCount"),
              value: formatNumber(bundle.itemCount, locale),
            },
            {
              label: t("list.columns.price"),
              value:
                bundle.listPriceVnd != null
                  ? formatVnd(bundle.listPriceVnd, locale)
                  : t("list.notSet"),
            },
            {
              label: t("list.columns.attempts"),
              value:
                bundle.attemptsPerExam != null
                  ? formatNumber(bundle.attemptsPerExam, locale)
                  : t("list.notSet"),
            },
            {
              label: t("list.columns.updatedAt"),
              value: formatBundleDateTime(bundle.updatedAt, locale),
            },
          ]}
        >
          {bundle.summary && (
            <p data-exam-content className="line-clamp-2 text-caption text-text-secondary">
              {bundle.summary}
            </p>
          )}
        </ListCard>
      ),
    }),
    [kindLabelOf, locale, renderRowActions, t],
  );

  const items = isOffline ? [] : (listQuery.data?.items ?? []);
  const totalItems = isOffline ? 0 : (listQuery.data?.totalItems ?? 0);
  const totalPages = isOffline ? 0 : (listQuery.data?.totalPages ?? 0);
  const isFiltered = statusFilter !== "ALL" || examKindFilter !== "" || debouncedQ.trim() !== "";

  const statusAnnouncement = liveMessage
    ? liveMessage
    : listQuery.isLoading || listQuery.isPlaceholderData || listQuery.isError || isOffline
      ? ""
      : t("list.totalItems", { total: totalItems });

  const errorNode = isOffline ? (
    <EmptyState
      icon={<WifiOff />}
      title={t("list.offlineTitle")}
      description={t("list.offlineDescription")}
      action={
        <Button variant="secondary" onClick={() => void listQuery.refetch()}>
          {t("actions.retry")}
        </Button>
      }
    />
  ) : listQuery.isError ? (
    <EmptyState
      title={t("list.errorTitle")}
      description={resolveBundleErrorMessage(listQuery.error, tErrors)}
      action={
        <Button variant="secondary" onClick={() => void listQuery.refetch()}>
          {t("actions.retry")}
        </Button>
      }
    />
  ) : undefined;

  const emptyNode = isFiltered ? (
    <EmptyState
      icon={<Search />}
      title={t("list.filteredEmptyTitle")}
      description={t("list.filteredEmptyDescription")}
      action={
        <Button variant="secondary" onClick={handleClearFilters}>
          {t("actions.clearFilters")}
        </Button>
      }
    />
  ) : (
    <EmptyState
      icon={<Package />}
      title={t("list.emptyTitle")}
      description={t("list.emptyDescription")}
      action={
        canModify ? (
          <Button asChild>
            <Link href="/bundles/new">
              <Plus />
              <span>{t("actions.create")}</span>
            </Link>
          </Button>
        ) : undefined
      }
    />
  );

  return (
    <div className="flex flex-col gap-5">
      <PageHeader
        title={t("list.title")}
        description={t("list.description")}
        actions={
          <div className="flex flex-wrap items-center gap-2">
            <Button
              type="button"
              variant="secondary"
              size="sm"
              onClick={() => void listQuery.refetch()}
            >
              <RefreshCw className="size-4" aria-hidden />
              <span>{t("actions.refresh")}</span>
            </Button>
            {canModify && (
              <Button asChild>
                <Link href="/bundles/new">
                  <Plus />
                  <span>{t("actions.create")}</span>
                </Link>
              </Button>
            )}
          </div>
        }
      />

      <LiveStatus message={statusAnnouncement} />

      <Card padding="none" className="overflow-hidden">
        <div className="flex flex-col gap-3 border-b border-border p-4">
          <div
            role="radiogroup"
            aria-label={t("list.filters.statusGroupAria")}
            className="flex flex-wrap items-center gap-1.5"
          >
            {STATUS_FILTER_OPTIONS.map((option) => {
              const selected = statusFilter === option;
              return (
                <button
                  key={option}
                  type="button"
                  role="radio"
                  aria-checked={selected}
                  onClick={() => handleStatusChange(option)}
                  className={cn(
                    "relative inline-flex h-8 items-center rounded-full border px-3 text-caption font-medium transition-colors duration-150 pointer-coarse:after:absolute pointer-coarse:after:-inset-1.5 motion-reduce:transition-none",
                    selected
                      ? "border-primary-fill bg-primary-fill text-on-primary"
                      : "border-border bg-surface text-text-secondary hover:bg-subtle",
                    focusRing,
                  )}
                >
                  {t(`list.filters.statusOptions.${option}`)}
                </button>
              );
            })}
          </div>

          <div className="flex flex-col gap-3 sm:flex-row sm:items-center">
            <div className="relative min-w-0 flex-1">
              <label htmlFor={searchInputId} className="sr-only">
                {t("list.filters.searchAria")}
              </label>
              <Search
                aria-hidden
                className="pointer-events-none absolute top-1/2 left-3 size-4 -translate-y-1/2 text-text-muted"
              />
              <Input
                id={searchInputId}
                type="search"
                value={searchInput}
                onChange={(event) => handleSearchChange(event.target.value)}
                placeholder={t("list.filters.searchPlaceholder")}
                aria-label={t("list.filters.searchAria")}
                className={cn("pl-9", searchInput && "pr-9")}
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

            <div className="flex items-center gap-2 sm:w-56">
              <label htmlFor={examKindSelectId} className="shrink-0 text-label font-medium text-text-secondary">
                {t("list.filters.examKind")}
              </label>
              <Select
                id={examKindSelectId}
                aria-label={t("list.filters.examKind")}
                options={examKindOptions}
                value={examKindFilter || "__ALL__"}
                onValueChange={handleExamKindChange}
                className="min-w-0 flex-1"
              />
            </div>

            {isFiltered && (
              <Button type="button" variant="secondary" size="sm" onClick={handleClearFilters}>
                {t("actions.clearFilters")}
              </Button>
            )}
          </div>

          {!listQuery.isLoading && !listQuery.isError && !isOffline && (
            <p className="text-caption text-text-muted tabular-nums">
              {t("list.totalItems", { total: totalItems })}
            </p>
          )}
        </div>

        <div className={DROP_LOW_PRIORITY_COLUMNS}>
          <Table
            aria-label={t("list.tableAria")}
            columns={columns}
            data={items}
            getRowId={(row) => row.id}
            loading={listQuery.isLoading}
            loadingRows={pageSize}
            refreshing={listQuery.isFetching && !listQuery.isLoading && !isOffline}
            error={errorNode}
            empty={emptyNode}
            cards={cards}
            stickyColumns={{ start: true, end: true }}
          />
        </div>

        {!isOffline && !listQuery.isError && (totalPages > 1 || page > 1 || totalItems > PAGE_SIZE_OPTIONS[0]!) && (
          <div className="border-t border-border px-4 py-3">
            <Pagination
              page={page}
              pageSize={pageSize}
              total={totalItems}
              pageSizeOptions={PAGE_SIZE_OPTIONS}
              onPageChange={handlePageChange}
              onPageSizeChange={handlePageSizeChange}
            />
          </div>
        )}
      </Card>

      <BundlePublishDialog
        bundle={publishTarget}
        open={publishTarget !== null}
        onOpenChange={(open) => {
          if (!open) setPublishTarget(null);
        }}
        onCompleted={(updated) => {
          setLiveMessage(t("dialogs.publish.successToast", { code: updated.code }));
        }}
      />

      <BundleDiscontinueDialog
        bundle={discontinueTarget}
        open={discontinueTarget !== null}
        onOpenChange={(open) => {
          if (!open) setDiscontinueTarget(null);
        }}
        onCompleted={(updated) => {
          setLiveMessage(t("dialogs.discontinue.successToast", { code: updated.code }));
        }}
      />
    </div>
  );
}
