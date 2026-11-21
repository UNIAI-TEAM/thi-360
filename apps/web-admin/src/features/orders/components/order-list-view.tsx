"use client";

import { AlertCircle, Eye, ReceiptText, RefreshCw, RotateCcw, Search, Users } from "lucide-react";
import Link from "next/link";
import { usePathname, useRouter, useSearchParams } from "next/navigation";
import { useLocale, useTranslations } from "next-intl";
import { type ComponentProps, useEffect, useId, useMemo, useState } from "react";

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
  Select,
  type SelectOption,
  Table,
  Tooltip,
  cn,
  focusRing,
} from "@thi360/ui";

import { usePermissions } from "@/features/session/session-provider";

import { resolveOrderErrorMessage } from "../errors";
import { formatOrderDateTime, formatVnd } from "../format";
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
import { useExamOptionContentMarker } from "./use-exam-option-content-marker";

const DEFAULT_PAGE_SIZE = 20;
const DEFAULT_SORT = "createdAt,desc";

/**
 * Hide low-priority columns when the table container is narrower than `@68rem` / `@54rem`
 * (e.g. 1280px viewport with 248px sidebar has ~967px card width) so all remaining columns
 * fit without slicing or colliding with the sticky action column (DESIGN.md 5.9).
 */
const DROP_LOW_PRIORITY_COLUMNS = [
  "@container",
  "@max-[68rem]:[&_:is(th,td):has([data-col=timestamps])]:hidden",
  "@max-[54rem]:[&_:is(th,td):has([data-col=refundStatus])]:hidden",
].join(" ");

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

  const searchId = useId();
  const fromId = useId();
  const toId = useId();
  const examFilterId = useId();
  const paymentStatusId = useId();
  const refundStatusId = useId();
  const paymentMethodId = useId();

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

  const examSelectRef = useExamOptionContentMarker(
    examOptions.map((opt) => opt.value).join("|"),
  );

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
                className={cn("font-mono text-xs font-semibold text-primary-text hover:underline", focusRing)}
              >
                {item.code}
              </Link>
              <span
                data-col-summary="timestamps"
                className="hidden font-mono text-[11px] text-text-secondary @max-[68rem]:block"
              >
                {item.paidAt
                  ? t("list.cardPaid", { date: formatOrderDateTime(item.paidAt, locale) })
                  : t("list.cardCreated", { date: formatOrderDateTime(item.createdAt, locale) })}
              </span>
              {item.refundStatus !== "NONE" ? (
                <div data-col-summary="refundStatus" className="hidden @max-[54rem]:block">
                  <OrderRefundStatusChip status={item.refundStatus} />
                </div>
              ) : null}
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
            <div className="flex max-w-[115px] min-w-0 flex-col">
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
            <div className="flex max-w-[130px] min-w-0 flex-col">
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
        header: () => <span className="block text-right">{t("columns.amount")}</span>,
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
        header: () => <span data-col="refundStatus">{t("columns.refundStatus")}</span>,
        cell: ({ row }) => (
          <div data-col="refundStatus">
            <OrderRefundStatusChip status={row.original.refundStatus} />
          </div>
        ),
      },
      {
        id: "timestamps",
        header: () => <span data-col="timestamps">{t("columns.timestamps")}</span>,
        cell: ({ row }) => {
          const item = row.original;
          return (
            <div
              data-col="timestamps"
              className="flex flex-col gap-0.5 font-mono text-xs whitespace-nowrap text-text-secondary"
            >
              <span>
                {t("list.cardCreated", { date: formatOrderDateTime(item.createdAt, locale) })}
              </span>
              <span>
                {item.paidAt
                  ? t("list.cardPaid", { date: formatOrderDateTime(item.paidAt, locale) })
                  : t("list.cardNotPaid")}
              </span>
            </div>
          );
        },
      },
      {
        id: "actions",
        header: () => <span className="block text-right">{t("columns.actions")}</span>,
        cell: ({ row }) => {
          const actionLabel = t("nav.viewOrderTransactions", { code: row.original.code });
          return (
            <div className="flex justify-end">
              <Tooltip content={actionLabel}>
                <Link
                  href={`/orders/${encodeURIComponent(row.original.code)}`}
                  aria-label={actionLabel}
                  className={cn(
                    "inline-flex size-10 items-center justify-center rounded-lg text-text-secondary transition-colors hover:bg-subtle hover:text-text",
                    focusRing,
                  )}
                >
                  <Eye className="size-[18px]" aria-hidden="true" />
                </Link>
              </Tooltip>
            </div>
          );
        },
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

      <Card className="@container p-4">
        <form
          className="grid grid-cols-1 items-end gap-3 @sm:grid-cols-2 @3xl:grid-cols-4"
          onSubmit={(event) => {
            event.preventDefault();
            updateUrlParams({ q: searchInput.trim(), page: 0 });
          }}
        >
          <div className="col-span-full flex flex-col gap-1 @3xl:col-span-2">
            <label htmlFor={searchId} className="text-field-label font-medium text-text-secondary">
              {t("filters.searchLabel")}
            </label>
            <div className="flex items-center gap-2">
              <div className="relative min-w-0 flex-1">
                <Search
                  className="pointer-events-none absolute top-1/2 left-3 h-4 w-4 -translate-y-1/2 text-text-muted"
                  aria-hidden="true"
                />
                <Input
                  id={searchId}
                  type="search"
                  value={searchInput}
                  onChange={(event) => setSearchDraft(event.target.value)}
                  placeholder={t("filters.searchPlaceholder")}
                  aria-label={t("filters.searchLabel")}
                  className="pl-9"
                />
              </div>
              {hasActiveFilters ? (
                <Button
                  type="button"
                  variant="secondary"
                  size="md"
                  className="shrink-0"
                  onClick={handleResetFilters}
                >
                  <RotateCcw className="h-4 w-4" aria-hidden="true" />
                  <span>{t("filters.clear")}</span>
                </Button>
              ) : null}
            </div>
          </div>

          <div className="flex min-w-0 flex-col gap-1">
            <label htmlFor={fromId} className="text-field-label font-medium text-text-secondary">
              {t("filters.from")}
            </label>
            <Input
              id={fromId}
              type="date"
              value={params.fromDate}
              onChange={(event) => updateUrlParams({ fromDate: event.target.value, page: 0 })}
              aria-label={t("filters.from")}
            />
          </div>

          <div className="flex min-w-0 flex-col gap-1">
            <label htmlFor={toId} className="text-field-label font-medium text-text-secondary">
              {t("filters.to")}
            </label>
            <Input
              id={toId}
              type="date"
              value={params.toDate}
              onChange={(event) => updateUrlParams({ toDate: event.target.value, page: 0 })}
              aria-label={t("filters.to")}
            />
          </div>

          <div ref={examSelectRef} className="flex min-w-0 flex-col gap-1">
            <label htmlFor={examFilterId} className="text-field-label font-medium text-text-secondary">
              {t("filters.exam")}
            </label>
            <Select
              id={examFilterId}
              aria-label={t("filters.exam")}
              options={examOptions}
              value={params.examId || "__ALL__"}
              className="[&>span:first-child]:truncate"
              onValueChange={(value) =>
                updateUrlParams({ examId: value === "__ALL__" ? "" : value, page: 0 })
              }
            />
          </div>

          <div className="flex min-w-0 flex-col gap-1">
            <label htmlFor={paymentStatusId} className="text-field-label font-medium text-text-secondary">
              {t("filters.paymentStatus")}
            </label>
            <Select
              id={paymentStatusId}
              aria-label={t("filters.paymentStatus")}
              options={paymentStatusOptions}
              value={params.paymentStatus}
              className="[&>span:first-child]:truncate"
              onValueChange={(value) =>
                updateUrlParams({ paymentStatus: value as OrdersListQueryParams["paymentStatus"], page: 0 })
              }
            />
          </div>

          <div className="flex min-w-0 flex-col gap-1">
            <label htmlFor={refundStatusId} className="text-field-label font-medium text-text-secondary">
              {t("filters.refundStatus")}
            </label>
            <Select
              id={refundStatusId}
              aria-label={t("filters.refundStatus")}
              options={refundStatusOptions}
              value={params.refundStatus}
              className="[&>span:first-child]:truncate"
              onValueChange={(value) =>
                updateUrlParams({ refundStatus: value as OrdersListQueryParams["refundStatus"], page: 0 })
              }
            />
          </div>

          <div className="flex min-w-0 flex-col gap-1">
            <label htmlFor={paymentMethodId} className="text-field-label font-medium text-text-secondary">
              {t("filters.paymentMethod")}
            </label>
            <Select
              id={paymentMethodId}
              aria-label={t("filters.paymentMethod")}
              options={paymentMethodOptions}
              value={params.paymentMethod}
              className="[&>span:first-child]:truncate"
              onValueChange={(value) =>
                updateUrlParams({ paymentMethod: value as OrdersListQueryParams["paymentMethod"], page: 0 })
              }
            />
          </div>
        </form>
      </Card>

      <LiveStatus
        message={
          listQuery.isPending
            ? t("list.loading")
            : listQuery.data
              ? t("list.totalItems", { total: totalItems })
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
          <Card padding="none" className={cn("overflow-hidden", DROP_LOW_PRIORITY_COLUMNS)}>
            <Table
              aria-label={t("list.tableLabel")}
              columns={columns}
              data={items}
              getRowId={(row) => row.orderId}
              stickyColumns={{ start: true, end: true }}
              loading={listQuery.isPending}
              loadingRows={6}
              refreshing={listQuery.isFetching && !listQuery.isPending}
              className="[&_:is(th,td)]:px-2.5 [&_:is(th,td):first-child]:ps-4 [&_:is(th,td):last-child]:pe-4"
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
                  >
                    <p className="text-xs text-text-secondary">
                      {t("list.cardAccount", { username: row.buyerUsername })}
                    </p>
                  </ListCard>
                ),
              }}
            />
          </Card>

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
