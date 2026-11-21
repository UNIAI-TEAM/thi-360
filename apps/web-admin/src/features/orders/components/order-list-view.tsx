"use client";

import { AlertCircle, ReceiptText, RefreshCw, RotateCcw, Search, Users } from "lucide-react";
import Link from "next/link";
import { usePathname, useRouter, useSearchParams } from "next/navigation";
import { useLocale, useTranslations } from "next-intl";
import { type ComponentProps, useEffect, useMemo, useState } from "react";

import {
  Button,
  EmptyState,
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
  focusRing,
} from "@thi360/ui";

import { usePermissions } from "@/features/session/session-provider";
import {
  FILTER_FORM,
  FILTER_SEARCH,
  FILTER_SELECT,
  FILTER_TOGGLES,
} from "@/features/users/components/list-filters";

import { resolveOrderErrorMessage } from "../errors";
import { formatNumber, formatOrderDateTime, formatVnd } from "../format";
import {
  useAdminExamsForFilterQuery,
  useAdminOrderAttentionQuery,
  useAdminOrdersListQuery,
  useOrderDerivedExamsForFilterQuery,
} from "../queries";
import type {
  AdminOrderSummary,
  AdminPaymentStatus,
  AdminRefundStatus,
  OrderAttentionType,
  OrdersListQueryParams,
  PaymentMethod,
} from "../types";
import { OrderAttentionStrip } from "./order-attention-strip";
import {
  OrderPaymentStatusChip,
  OrderRefundStatusChip,
  OrderStateChip,
} from "./order-status-chips";
import { ReconciliationReportCard } from "./reconciliation-report-card";

const DEFAULT_PAGE_SIZE = 20;
const DEFAULT_SORT = "createdAt,desc";

const VALID_PAYMENT_STATUSES = new Set<AdminPaymentStatus>([
  "SUCCEEDED",
  "PENDING",
  "FAILED",
  "FREE",
]);
const VALID_REFUND_STATUSES = new Set<AdminRefundStatus>([
  "NONE",
  "IN_PROGRESS",
  "PARTIAL",
  "FULL",
]);
const VALID_PAYMENT_METHODS = new Set<PaymentMethod>(["VNPAY", "FREE"]);
const VALID_ATTENTION_TYPES = new Set<OrderAttentionType>([
  "LATE_SUCCESS_PENDING",
  "PAID_UNFULFILLED",
  "PAYMENT_UNKNOWN_OVER_24H",
  "DUPLICATE_UNREFUNDED",
  "NEEDS_REVIEW",
  "REFUND_UNKNOWN",
]);

function parseQueryParams(sp: ReturnType<typeof useSearchParams>): OrdersListQueryParams {
  const q = sp.get("q") ?? "";
  const examId = sp.get("examId") ?? "";
  const fromDate = sp.get("from") ?? "";
  const toDate = sp.get("to") ?? "";

  const rawPaymentStatus = sp.get("paymentStatus") ?? "ALL";
  const paymentStatus = VALID_PAYMENT_STATUSES.has(rawPaymentStatus as AdminPaymentStatus)
    ? (rawPaymentStatus as AdminPaymentStatus)
    : "ALL";

  const rawRefundStatus = sp.get("refundStatus") ?? "ALL";
  const refundStatus = VALID_REFUND_STATUSES.has(rawRefundStatus as AdminRefundStatus)
    ? (rawRefundStatus as AdminRefundStatus)
    : "ALL";

  const rawPaymentMethod = sp.get("paymentMethod") ?? "ALL";
  const paymentMethod = VALID_PAYMENT_METHODS.has(rawPaymentMethod as PaymentMethod)
    ? (rawPaymentMethod as PaymentMethod)
    : "ALL";

  const rawAttention = sp.get("attention") ?? "";
  const attention = VALID_ATTENTION_TYPES.has(rawAttention as OrderAttentionType)
    ? (rawAttention as OrderAttentionType)
    : "";

  const rawPage = Number(sp.get("page") ?? "0");
  const page = Number.isInteger(rawPage) && rawPage >= 0 ? rawPage : 0;

  const rawSize = Number(sp.get("size") ?? String(DEFAULT_PAGE_SIZE));
  const size = Number.isInteger(rawSize) && rawSize >= 1 && rawSize <= 100 ? rawSize : DEFAULT_PAGE_SIZE;

  const sort = sp.get("sort") || DEFAULT_SORT;

  return {
    q,
    examId,
    fromDate,
    toDate,
    paymentStatus,
    refundStatus,
    paymentMethod,
    attention,
    page,
    size,
    sort,
  };
}

function buildSearchString(params: OrdersListQueryParams): string {
  const next = new URLSearchParams();
  if (params.q.trim()) next.set("q", params.q.trim());
  if (params.examId) next.set("examId", params.examId);
  if (params.fromDate) next.set("from", params.fromDate);
  if (params.toDate) next.set("to", params.toDate);
  if (params.paymentStatus !== "ALL") next.set("paymentStatus", params.paymentStatus);
  if (params.refundStatus !== "ALL") next.set("refundStatus", params.refundStatus);
  if (params.paymentMethod !== "ALL") next.set("paymentMethod", params.paymentMethod);
  if (params.attention) next.set("attention", params.attention);
  if (params.page > 0) next.set("page", String(params.page));
  if (params.size !== DEFAULT_PAGE_SIZE) next.set("size", String(params.size));
  if (params.sort !== DEFAULT_SORT) next.set("sort", params.sort);
  const str = next.toString();
  return str ? `?${str}` : "";
}

export function OrderListView() {
  const t = useTranslations("orders");
  const tErrors = useTranslations("orders.errors");
  const locale = useLocale();
  const router = useRouter();
  const pathname = usePathname();
  const searchParams = useSearchParams();
  const permissions = usePermissions();

  const params = useMemo(() => parseQueryParams(searchParams), [searchParams]);
  const [searchDraft, setSearchDraft] = useState<string | null>(null);
  const searchInput = searchDraft ?? params.q;

  const updateUrlParams = (patch: Partial<OrdersListQueryParams>) => {
    const merged: OrdersListQueryParams = { ...params, ...patch };
    const qs = buildSearchString(merged);
    router.replace(`${pathname}${qs}`, { scroll: false });
  };

  useEffect(() => {
    if (searchDraft === null) return;
    const trimmedInput = searchDraft.trim();
    const trimmedParam = params.q.trim();

    if (trimmedInput === "") {
      if (trimmedParam !== "") {
        updateUrlParams({ q: "", page: 0 });
      }
      return;
    }

    if (trimmedInput === trimmedParam) return;

    const handle = window.setTimeout(() => {
      updateUrlParams({ q: trimmedInput, page: 0 });
      setSearchDraft(null);
    }, 300);
    return () => window.clearTimeout(handle);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [searchDraft, params.q]);

  const canViewExams = !permissions.isLoading && permissions.can("EXAM.VIEW");
  const adminExamsQuery = useAdminExamsForFilterQuery(canViewExams);
  const derivedExamsQuery = useOrderDerivedExamsForFilterQuery(!permissions.isLoading && !canViewExams);

  const listQuery = useAdminOrdersListQuery(params);
  const attentionQuery = useAdminOrderAttentionQuery(true);

  const examOptions: SelectOption[] = useMemo(() => {
    const source = canViewExams ? adminExamsQuery.data : derivedExamsQuery.data;
    const base: SelectOption[] = [
      { value: "__ALL__", label: t("filters.examAll"), textValue: t("filters.examAll") },
    ];
    for (const exam of source ?? []) {
      const display = exam.code ? `${exam.name} (${exam.code})` : exam.name;
      base.push({
        value: exam.id,
        label: <span data-exam-content>{display}</span>,
        textValue: display,
      });
    }
    return base;
  }, [canViewExams, adminExamsQuery.data, derivedExamsQuery.data, t]);

  const paymentStatusOptions: SelectOption[] = useMemo(
    () => [
      { value: "ALL", label: t("filters.paymentStatusAll") },
      { value: "SUCCEEDED", label: t("paymentStatuses.SUCCEEDED") },
      { value: "PENDING", label: t("paymentStatuses.PENDING") },
      { value: "FAILED", label: t("paymentStatuses.FAILED") },
      { value: "FREE", label: t("paymentStatuses.FREE") },
    ],
    [t],
  );

  const refundStatusOptions: SelectOption[] = useMemo(
    () => [
      { value: "ALL", label: t("filters.refundStatusAll") },
      { value: "NONE", label: t("refundStatuses.NONE") },
      { value: "IN_PROGRESS", label: t("refundStatuses.IN_PROGRESS") },
      { value: "PARTIAL", label: t("refundStatuses.PARTIAL") },
      { value: "FULL", label: t("refundStatuses.FULL") },
    ],
    [t],
  );

  const paymentMethodOptions: SelectOption[] = useMemo(
    () => [
      { value: "ALL", label: t("filters.paymentMethodAll") },
      { value: "VNPAY", label: t("paymentMethods.VNPAY") },
      { value: "FREE", label: t("paymentMethods.FREE") },
    ],
    [t],
  );

  const hasActiveFilters = Boolean(
    params.q ||
      params.examId ||
      params.fromDate ||
      params.toDate ||
      params.paymentStatus !== "ALL" ||
      params.refundStatus !== "ALL" ||
      params.paymentMethod !== "ALL" ||
      params.attention,
  );

  const handleResetFilters = () => {
    setSearchDraft(null);
    router.replace(pathname, { scroll: false });
  };

  const columns: ComponentProps<typeof Table<AdminOrderSummary>>["columns"] = useMemo(
    () => [
      {
        id: "code",
        header: t("columns.code"),
        cell: ({ row }) => {
          const item = row.original;
          return (
            <div className="flex flex-col gap-1">
              <Link
                href={`/orders/${encodeURIComponent(item.code)}`}
                className={`font-mono text-xs font-semibold text-primary-text hover:underline ${focusRing}`}
              >
                {item.code}
              </Link>
              {item.needsReview ? (
                <span className="inline-flex w-fit rounded border border-warning/40 bg-warning-bg px-1.5 py-0.5 text-[11px] font-medium text-warning">
                  {t("list.needsReviewTag")}
                </span>
              ) : null}
            </div>
          );
        },
      },
      {
        id: "buyer",
        header: t("columns.buyer"),
        cell: ({ row }) => {
          const item = row.original;
          return (
            <div className="flex max-w-[140px] min-w-0 flex-col">
              <span className="truncate text-xs font-medium text-text" data-exam-content>
                {item.buyerFullName}
              </span>
              <span className="truncate font-mono text-[11px] text-text-secondary">
                {item.buyerUsername}
              </span>
            </div>
          );
        },
      },
      {
        id: "exam",
        header: t("columns.exam"),
        cell: ({ row }) => {
          const item = row.original;
          return (
            <div className="flex max-w-[160px] min-w-0 flex-col">
              <span className="line-clamp-2 text-xs font-medium text-text" data-exam-content>
                {item.productName}
              </span>
              <span className="truncate font-mono text-[11px] text-text-secondary">
                {item.productCode}
              </span>
            </div>
          );
        },
      },
      {
        id: "amount",
        header: t("columns.amount"),
        cell: ({ row }) => (
          <div className="text-right font-mono text-xs font-semibold whitespace-nowrap text-text">
            {formatVnd(row.original.amountDueVnd, locale)}
          </div>
        ),
      },
      {
        id: "state",
        header: t("columns.state"),
        cell: ({ row }) => <OrderStateChip state={row.original.orderState} />,
      },
      {
        id: "paymentStatus",
        header: t("columns.paymentStatus"),
        cell: ({ row }) => <OrderPaymentStatusChip status={row.original.paymentStatus} />,
      },
      {
        id: "refundStatus",
        header: t("columns.refundStatus"),
        cell: ({ row }) => <OrderRefundStatusChip status={row.original.refundStatus} />,
      },
      {
        id: "createdAt",
        header: t("columns.createdAt"),
        cell: ({ row }) => (
          <span className="font-mono text-xs text-text-secondary whitespace-nowrap">
            {formatOrderDateTime(row.original.createdAt, locale)}
          </span>
        ),
      },
      {
        id: "paidAt",
        header: t("columns.paidAt"),
        cell: ({ row }) => (
          <span className="font-mono text-xs text-text-secondary whitespace-nowrap">
            {row.original.paidAt ? formatOrderDateTime(row.original.paidAt, locale) : "—"}
          </span>
        ),
      },
      {
        id: "actions",
        header: t("columns.actions"),
        cell: ({ row }) => (
          <div className="text-right whitespace-nowrap">
            <Link
              href={`/orders/${encodeURIComponent(row.original.code)}`}
              className={`text-xs font-medium text-primary-text hover:underline ${focusRing}`}
            >
              {t("nav.viewOrder")}
            </Link>
          </div>
        ),
      },
    ],
    [locale, t],
  );

  const isOffline = listQuery.fetchStatus === "paused" && listQuery.isPending;
  const items = listQuery.data?.items ?? [];
  const totalItems = listQuery.data?.totalItems ?? 0;

  return (
    <div className="@container flex flex-col gap-5">
      <PageHeader
        title={t("title")}
        description={t("description")}
        actions={
          <div className="flex flex-wrap items-center gap-2">
            <Button type="button" variant="secondary" size="sm" onClick={() => void listQuery.refetch()}>
              <RefreshCw className="h-4 w-4" aria-hidden="true" />
              <span>{t("filters.refresh")}</span>
            </Button>
            <Button asChild variant="outline" size="sm">
              <Link href="/orders/buyers">
                <Users aria-hidden="true" />
                <span>{t("nav.buyers")}</span>
              </Link>
            </Button>
          </div>
        }
      />

      <ReconciliationReportCard />

      <OrderAttentionStrip
        summary={listQuery.data?.attention ?? attentionQuery.data}
        isLoading={listQuery.isPending && attentionQuery.isPending}
        isError={listQuery.isError && attentionQuery.isError}
        activeAttention={params.attention}
        onSelectAttention={(next) => updateUrlParams({ attention: next, page: 0 })}
      />

      <form
        className={FILTER_FORM}
        onSubmit={(event) => {
          event.preventDefault();
          updateUrlParams({ q: searchInput.trim(), page: 0 });
        }}
      >
        <div className={FILTER_SEARCH}>
          <div className="relative flex-1 min-w-[220px]">
            <Search
              className="pointer-events-none absolute top-1/2 left-3 h-4 w-4 -translate-y-1/2 text-text-muted"
              aria-hidden="true"
            />
            <Input
              type="search"
              value={searchInput}
              onChange={(event) => setSearchDraft(event.target.value)}
              placeholder={t("filters.searchPlaceholder")}
              aria-label={t("filters.searchLabel")}
              className="pl-9"
            />
          </div>
          {hasActiveFilters ? (
            <Button type="button" variant="secondary" size="md" onClick={handleResetFilters}>
              <RotateCcw className="h-4 w-4" aria-hidden="true" />
              <span>{t("filters.clear")}</span>
            </Button>
          ) : null}
        </div>

        <div className={FILTER_TOGGLES}>
          <div className={FILTER_SELECT}>
            <Select
              aria-label={t("filters.exam")}
              options={examOptions}
              value={params.examId || "__ALL__"}
              onValueChange={(value) =>
                updateUrlParams({ examId: value === "__ALL__" ? "" : value, page: 0 })
              }
            />
          </div>

          <div className={FILTER_SELECT}>
            <Select
              aria-label={t("filters.paymentStatus")}
              options={paymentStatusOptions}
              value={params.paymentStatus}
              onValueChange={(value) =>
                updateUrlParams({ paymentStatus: value as OrdersListQueryParams["paymentStatus"], page: 0 })
              }
            />
          </div>

          <div className={FILTER_SELECT}>
            <Select
              aria-label={t("filters.refundStatus")}
              options={refundStatusOptions}
              value={params.refundStatus}
              onValueChange={(value) =>
                updateUrlParams({ refundStatus: value as OrdersListQueryParams["refundStatus"], page: 0 })
              }
            />
          </div>

          <div className={FILTER_SELECT}>
            <Select
              aria-label={t("filters.paymentMethod")}
              options={paymentMethodOptions}
              value={params.paymentMethod}
              onValueChange={(value) =>
                updateUrlParams({ paymentMethod: value as OrdersListQueryParams["paymentMethod"], page: 0 })
              }
            />
          </div>

          <div className="flex items-center gap-2">
            <Input
              type="date"
              value={params.fromDate}
              onChange={(event) => updateUrlParams({ fromDate: event.target.value, page: 0 })}
              aria-label={t("filters.from")}
              className="w-36"
            />
            <Input
              type="date"
              value={params.toDate}
              onChange={(event) => updateUrlParams({ toDate: event.target.value, page: 0 })}
              aria-label={t("filters.to")}
              className="w-36"
            />
          </div>
        </div>
      </form>

      <LiveStatus
        message={
          listQuery.isPending
            ? t("list.loading")
            : listQuery.data
              ? t("list.totalItems", { total: formatNumber(totalItems, locale) })
              : ""
        }
      />

      {isOffline ? (
        <EmptyState
          headingLevel={2}
          icon={<AlertCircle className="h-6 w-6" />}
          title={t("list.offlineTitle")}
          description={t("list.offlineDescription")}
          action={
            <Button type="button" variant="secondary" onClick={() => void listQuery.refetch()}>
              {t("list.retry")}
            </Button>
          }
        />
      ) : listQuery.isError ? (
        <EmptyState
          headingLevel={2}
          icon={<AlertCircle className="h-6 w-6" />}
          title={t("list.errorTitle")}
          description={resolveOrderErrorMessage(listQuery.error, tErrors)}
          action={
            <Button type="button" variant="secondary" onClick={() => void listQuery.refetch()}>
              {t("list.retry")}
            </Button>
          }
        />
      ) : (
        <>
          <Table
            aria-label={t("list.tableLabel")}
            columns={columns}
            data={items}
            getRowId={(row) => row.orderId}
            stickyColumns={{ start: true, end: true }}
            loading={listQuery.isPending}
            loadingRows={6}
            refreshing={listQuery.isFetching && !listQuery.isPending}
            empty={
              <EmptyState
                headingLevel={2}
                icon={<ReceiptText className="h-6 w-6" />}
                title={hasActiveFilters ? t("list.filteredEmptyTitle") : t("list.emptyTitle")}
                description={
                  hasActiveFilters ? t("list.filteredEmptyDescription") : t("list.emptyDescription")
                }
                action={
                  hasActiveFilters ? (
                    <Button type="button" variant="secondary" onClick={handleResetFilters}>
                      {t("filters.clear")}
                    </Button>
                  ) : undefined
                }
              />
            }
            cards={{
              render: (row) => (
                <ListCard
                  title={
                    <ListCardTitleLink
                      linkComponent={Link}
                      href={`/orders/${encodeURIComponent(row.code)}`}
                    >
                      {row.code}
                    </ListCardTitleLink>
                  }
                  caption={
                    <span data-exam-content>
                      {row.productName} · {row.buyerFullName}
                    </span>
                  }
                  status={<OrderStateChip state={row.orderState} />}
                  tags={
                    <>
                      <OrderPaymentStatusChip status={row.paymentStatus} />
                      <OrderRefundStatusChip status={row.refundStatus} />
                      {row.needsReview ? (
                        <ListCardTag>{t("list.needsReviewTag")}</ListCardTag>
                      ) : null}
                    </>
                  }
                  meta={[
                    {
                      label: t("columns.amount"),
                      value: (
                        <span className="font-mono font-semibold text-text">
                          {formatVnd(row.amountDueVnd, locale)}
                        </span>
                      ),
                    },
                    {
                      label: row.paidAt ? t("columns.paidAt") : t("columns.createdAt"),
                      value: row.paidAt
                        ? formatOrderDateTime(row.paidAt, locale)
                        : formatOrderDateTime(row.createdAt, locale),
                    },
                  ]}
                  actions={
                    <Button asChild variant="secondary" size="sm">
                      <Link href={`/orders/${encodeURIComponent(row.code)}`}>
                        {t("nav.viewOrder")}
                      </Link>
                    </Button>
                  }
                >
                  <p className="text-xs text-text-secondary">
                    {t("list.cardAccount", { username: row.buyerUsername })}
                  </p>
                </ListCard>
              ),
            }}
          />

          {listQuery.data && listQuery.data.totalPages > 1 ? (
            <Pagination
              page={params.page + 1}
              pageSize={params.size}
              total={totalItems}
              onPageChange={(nextPage) => updateUrlParams({ page: Math.max(0, nextPage - 1) })}
            />
          ) : null}
        </>
      )}
    </div>
  );
}
