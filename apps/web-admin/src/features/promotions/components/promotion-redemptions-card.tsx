"use client";

import type { ColumnDef } from "@tanstack/react-table";
import {
  Button,
  Card,
  EmptyState,
  Input,
  ListCard,
  Pagination,
  StatusChip,
  type StatusTone,
  Table,
  type TableCards,
  cn,
  focusRing,
} from "@thi360/ui";
import { Search, Ticket, X } from "lucide-react";
import { useLocale, useTranslations } from "next-intl";
import { useMemo } from "react";

import { formatPromotionDateTime } from "../datetime";
import { resolvePromotionErrorMessage } from "../errors";
import { formatVnd } from "../format";
import type {
  PromotionDetail,
  PromotionRedemptionView,
  RedemptionStateFilter,
} from "../types";

const REDEMPTION_FILTERS: readonly RedemptionStateFilter[] = [
  "ALL",
  "CONFIRMED",
  "RESERVED",
  "RELEASED",
];

const REDEMPTION_TONE: Record<PromotionRedemptionView["state"], StatusTone> = {
  CONFIRMED: "success",
  RESERVED: "info",
  RELEASED: "neutral",
};

export function PromotionRedemptionsCard({
  redemptions,
  page,
  onPageChange,
  stateFilter,
  onStateFilterChange,
  searchInput,
  onSearchInputChange,
  onClearFilters,
  refreshing,
  error,
  onRetry,
}: {
  redemptions: PromotionDetail["redemptions"];
  page: number;
  onPageChange: (nextPage: number) => void;
  stateFilter: RedemptionStateFilter;
  onStateFilterChange: (nextState: RedemptionStateFilter) => void;
  searchInput: string;
  onSearchInputChange: (nextQ: string) => void;
  onClearFilters: () => void;
  refreshing: boolean;
  error: unknown;
  onRetry: () => void;
}) {
  const t = useTranslations("promotions");
  const tErrors = useTranslations("promotions.errors");
  const locale = useLocale();

  const isFiltered = stateFilter !== "ALL" || searchInput.trim() !== "";

  const columns = useMemo<ColumnDef<PromotionRedemptionView>[]>(
    () => [
      {
        id: "student",
        header: t("redemptions.columns.student"),
        enableSorting: false,
        cell: ({ row }) => {
          const item = row.original;
          const primary = item.fullName ?? item.username ?? item.userId.slice(0, 8);
          return (
            <div className="flex flex-col">
              <span data-exam-content className="font-medium text-text">
                {primary}
              </span>
              {item.username && (
                <span data-exam-content className="text-caption text-text-muted">
                  @{item.username}
                </span>
              )}
            </div>
          );
        },
      },
      {
        id: "orderCode",
        header: t("redemptions.columns.orderCode"),
        enableSorting: false,
        cell: ({ row }) => (
          <span className="font-mono text-caption text-text">
            {row.original.orderCode ?? row.original.orderId.slice(0, 8)}
          </span>
        ),
      },
      {
        id: "discountVnd",
        header: t("redemptions.columns.discountVnd"),
        enableSorting: false,
        cell: ({ row }) => (
          <span className="font-medium text-text tabular-nums">
            -{formatVnd(row.original.discountVnd, locale)}
          </span>
        ),
      },
      {
        id: "state",
        header: t("redemptions.columns.state"),
        enableSorting: false,
        cell: ({ row }) => (
          <StatusChip tone={REDEMPTION_TONE[row.original.state]}>
            {t(`redemptions.statusLabels.${row.original.state}`)}
          </StatusChip>
        ),
      },
      {
        id: "timestamp",
        header: t("redemptions.columns.timestamp"),
        enableSorting: false,
        cell: ({ row }) => (
          <span className="whitespace-nowrap text-caption text-text-secondary tabular-nums">
            {formatPromotionDateTime(row.original.createdAt, locale)}
          </span>
        ),
      },
    ],
    [locale, t],
  );

  const cards = useMemo<TableCards<PromotionRedemptionView>>(
    () => ({
      render: (item) => {
        const studentLabel = item.fullName ?? item.username ?? item.userId.slice(0, 8);
        return (
          <ListCard
            title={<span data-exam-content>{studentLabel}</span>}
            caption={item.username ? <span data-exam-content>@{item.username}</span> : undefined}
            status={
              <StatusChip tone={REDEMPTION_TONE[item.state]}>
                {t(`redemptions.statusLabels.${item.state}`)}
              </StatusChip>
            }
            meta={[
              { label: t("redemptions.columns.orderCode"), value: item.orderCode ?? item.orderId.slice(0, 8) },
              { label: t("redemptions.columns.discountVnd"), value: `-${formatVnd(item.discountVnd, locale)}` },
              {
                label: t("redemptions.columns.timestamp"),
                value: formatPromotionDateTime(item.createdAt, locale),
              },
            ]}
          />
        );
      },
    }),
    [locale, t],
  );

  const errorNode = error ? (
    <EmptyState
      title={t("redemptions.errorTitle")}
      description={resolvePromotionErrorMessage(error, tErrors)}
      action={
        <Button variant="secondary" onClick={onRetry}>
          {t("actions.retry")}
        </Button>
      }
    />
  ) : undefined;

  const emptyNode = isFiltered ? (
    <EmptyState
      icon={<Search />}
      title={t("redemptions.emptyFilteredTitle")}
      description={t("redemptions.emptyFilteredDescription")}
      action={
        <Button variant="secondary" onClick={onClearFilters}>
          {t("actions.clearFilters")}
        </Button>
      }
    />
  ) : (
    <EmptyState
      icon={<Ticket />}
      title={t("redemptions.emptyNeverUsedTitle")}
      description={t("redemptions.emptyNeverUsedDescription")}
    />
  );

  return (
    <Card className="overflow-hidden p-0">
      <div className="flex flex-col gap-3 border-b border-border p-5">
        <div className="flex flex-col gap-1">
          <h2 className="text-title font-semibold text-text">{t("redemptions.title")}</h2>
          <p className="text-caption text-text-muted">{t("redemptions.description")}</p>
        </div>

        <div className="flex flex-col gap-3">
          <div className="relative w-full max-w-md">
            <Search
              aria-hidden
              className="pointer-events-none absolute top-1/2 left-3 size-4 -translate-y-1/2 text-text-muted"
            />
            <Input
              value={searchInput}
              onChange={(event) => onSearchInputChange(event.target.value)}
              placeholder={t("redemptions.searchPlaceholder")}
              aria-label={t("redemptions.searchAria")}
              className="pr-9 pl-9"
            />
            {searchInput && (
              <button
                type="button"
                aria-label={t("actions.clearFilters")}
                onClick={() => onSearchInputChange("")}
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
            aria-label={t("redemptions.columns.state")}
            className="flex flex-wrap items-center gap-1.5"
          >
            {REDEMPTION_FILTERS.map((option) => {
              const selected = stateFilter === option;
              return (
                <button
                  key={option}
                  type="button"
                  role="radio"
                  aria-checked={selected}
                  onClick={() => onStateFilterChange(option)}
                  className={cn(
                    "inline-flex h-8 items-center rounded-full border px-3 text-caption font-medium transition-colors duration-150 motion-reduce:transition-none",
                    selected
                      ? "border-primary-fill bg-primary-fill text-on-primary"
                      : "border-border bg-surface text-text-secondary hover:bg-subtle",
                    focusRing,
                  )}
                >
                  {t(`redemptions.filters.${option}`)}
                </button>
              );
            })}
          </div>
        </div>
      </div>

      <Table
        columns={columns}
        data={redemptions.items}
        getRowId={(row) => row.id}
        refreshing={refreshing}
        error={errorNode}
        empty={emptyNode}
        cards={cards}
        aria-label={t("redemptions.title")}
      />

      {redemptions.totalItems > redemptions.size && (
        <div className="border-t border-border p-4">
          <Pagination
            page={page}
            pageSize={redemptions.size}
            total={redemptions.totalItems}
            onPageChange={onPageChange}
          />
        </div>
      )}
    </Card>
  );
}
