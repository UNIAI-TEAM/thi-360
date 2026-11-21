"use client";

import { AlertCircle, ArrowLeft, ExternalLink, RefreshCw, Users } from "lucide-react";
import Link from "next/link";
import { usePathname, useRouter, useSearchParams } from "next/navigation";
import { useLocale, useTranslations } from "next-intl";
import { type ComponentProps, useCallback, useEffect, useMemo, useRef } from "react";

import {
  Button,
  Card,
  EmptyState,
  ListCard,
  ListCardTitleLink,
  LiveStatus,
  PageHeader,
  Pagination,
  Select,
  type SelectOption,
  Table,
  cn,
  focusRing,
} from "@thi360/ui";

import { usePermissions } from "@/features/session/session-provider";

import { resolveOrderErrorMessage } from "../errors";
import {
  formatNumber,
  formatOrderDateTime,
  formatVnd,
  parseAllowedSort,
} from "../format";
import {
  useAdminExamsForFilterQuery,
  useExamBuyersQuery,
  useOrderDerivedExamsForFilterQuery,
} from "../queries";
import type { ExamBuyerRow, ExamBuyersQueryParams } from "../types";
import { OrderStateChip } from "./order-status-chips";
import { useExamOptionContentMarker } from "./use-exam-option-content-marker";

const DEFAULT_PAGE_SIZE = 20;
const DEFAULT_SORT = "lastPaidAt,desc";
const BUYER_SORT_FIELDS = new Set(["lastPaidAt", "userId", "purchaseCount", "username"]);

function parseParams(sp: ReturnType<typeof useSearchParams>): {
  examId: string;
  query: ExamBuyersQueryParams;
} {
  const examId = sp.get("exam") ?? "";
  const rawPage = Number(sp.get("page") ?? "0");
  const page = Number.isInteger(rawPage) && rawPage >= 0 ? rawPage : 0;
  const rawSize = Number(sp.get("size") ?? String(DEFAULT_PAGE_SIZE));
  const size = Number.isInteger(rawSize) && rawSize >= 1 && rawSize <= 100 ? rawSize : DEFAULT_PAGE_SIZE;
  const sort = parseAllowedSort(sp.get("sort"), BUYER_SORT_FIELDS, DEFAULT_SORT);
  return { examId, query: { page, size, sort } };
}

function buildSearchString(examId: string, query: ExamBuyersQueryParams): string {
  const next = new URLSearchParams();
  if (examId) next.set("exam", examId);
  if (query.page > 0) next.set("page", String(query.page));
  if (query.size !== DEFAULT_PAGE_SIZE) next.set("size", String(query.size));
  if (query.sort !== DEFAULT_SORT) next.set("sort", query.sort);
  const str = next.toString();
  return str ? `?${str}` : "";
}

function sumBuyerPaidVnd(row: ExamBuyerRow): number {
  return row.orders.reduce((acc, o) => acc + o.amountDueVnd, 0);
}

export function OrderBuyersView() {
  const t = useTranslations("orders");
  const tErrors = useTranslations("orders.errors");
  const locale = useLocale();
  const router = useRouter();
  const pathname = usePathname();
  const searchParams = useSearchParams();
  const permissions = usePermissions();

  const { examId, query: params } = useMemo(() => parseParams(searchParams), [searchParams]);
  const stateRef = useRef({ examId, params });
  useEffect(() => {
    stateRef.current = { examId, params };
  }, [examId, params]);

  const canViewExams = !permissions.isLoading && permissions.can("EXAM.VIEW");
  const adminExamsQuery = useAdminExamsForFilterQuery(canViewExams);
  const derivedExamsQuery = useOrderDerivedExamsForFilterQuery(!permissions.isLoading && !canViewExams);
  const activeExamQuery = canViewExams ? adminExamsQuery : derivedExamsQuery;
  const examsLoading = permissions.isLoading || activeExamQuery.isPending;
  const examsError = !permissions.isLoading && activeExamQuery.isError;

  const buyersQuery = useExamBuyersQuery(examId, params, Boolean(examId));

  const updateUrl = useCallback(
    (nextExamId: string, nextQuery: Partial<ExamBuyersQueryParams>) => {
      const merged: ExamBuyersQueryParams = { ...stateRef.current.params, ...nextQuery };
      stateRef.current = { examId: nextExamId, params: merged };
      router.replace(`${pathname}${buildSearchString(nextExamId, merged)}`, { scroll: false });
    },
    [pathname, router],
  );

  const totalPages = buyersQuery.data?.totalPages ?? 0;
  useEffect(() => {
    if (buyersQuery.data && totalPages > 0 && params.page >= totalPages) {
      updateUrl(examId, { page: Math.max(0, totalPages - 1) });
    }
  }, [buyersQuery.data, totalPages, params.page, examId, updateUrl]);

  const examOptions: SelectOption[] = useMemo(() => {
    const source = canViewExams ? adminExamsQuery.data : derivedExamsQuery.data;
    const opts: SelectOption[] = [];
    for (const exam of source ?? []) {
      const display = exam.code ? `${exam.name} (${exam.code})` : exam.name;
      opts.push({
        value: exam.id,
        label: <span data-exam-content>{display}</span>,
        textValue: display,
      });
    }
    if (examId && !opts.some((opt) => opt.value === examId)) {
      opts.unshift({
        value: examId,
        label: <span data-exam-content>{examId}</span>,
        textValue: examId,
      });
    }
    return opts;
  }, [canViewExams, adminExamsQuery.data, derivedExamsQuery.data, examId]);

  const examSelectRef = useExamOptionContentMarker(
    examOptions.map((opt) => opt.value).join("|"),
  );

  const pageSizeOptions = useMemo(
    () => Array.from(new Set([10, 20, 50, 100, params.size])).sort((a, b) => a - b),
    [params.size],
  );

  const columns: ComponentProps<typeof Table<ExamBuyerRow>>["columns"] = useMemo(
    () => [
      {
        id: "user",
        header: t("buyers.colUser"),
        cell: ({ row }) => {
          const buyer = row.original;
          return (
            <div className="flex flex-col">
              <span className="text-sm font-medium text-text" data-exam-content>
                {buyer.fullName}
              </span>
              <span className="font-mono text-xs text-text-secondary">{buyer.username}</span>
            </div>
          );
        },
      },
      {
        id: "orders",
        header: t("buyers.colOrders"),
        cell: ({ row }) => {
          const buyer = row.original;
          return (
            <div className="flex flex-col gap-1">
              <span className="text-xs font-medium text-text">
                {t("buyers.paidCount", { count: buyer.purchaseCount })}
              </span>
              <div className="flex flex-wrap gap-1.5">
                {buyer.orders.map((ord) => (
                  <Link
                    key={ord.orderId}
                    href={`/orders/${encodeURIComponent(ord.orderCode)}`}
                    className={cn("font-mono text-xs text-primary-text hover:underline", focusRing)}
                  >
                    {ord.orderCode}
                  </Link>
                ))}
              </div>
            </div>
          );
        },
      },
      {
        id: "paidAmount",
        header: t("buyers.colPaidAmount"),
        cell: ({ row }) => (
          <span className="font-mono text-xs font-semibold whitespace-nowrap text-text">
            {formatVnd(sumBuyerPaidVnd(row.original), locale)}
          </span>
        ),
      },
      {
        id: "attempts",
        header: t("buyers.colAttempts"),
        cell: ({ row }) => {
          const buyer = row.original;
          return (
            <span className="font-mono text-xs text-text">
              {formatNumber(buyer.attemptsRemaining, locale)} /{" "}
              {formatNumber(buyer.attemptsTotal, locale)}
            </span>
          );
        },
      },
      {
        id: "lastPaid",
        header: t("buyers.colLastPaid"),
        cell: ({ row }) => (
          <span className="font-mono text-xs whitespace-nowrap text-text-secondary">
            {row.original.lastPaidAt ? formatOrderDateTime(row.original.lastPaidAt, locale) : "—"}
          </span>
        ),
      },
      {
        id: "latestOrder",
        header: t("buyers.colLatestOrder"),
        cell: ({ row }) => {
          const latest = row.original.orders[0];
          if (!latest) return <span className="text-xs text-text-secondary">—</span>;
          return (
            <div className="flex items-center gap-2">
              <Link
                href={`/orders/${encodeURIComponent(latest.orderCode)}`}
                aria-label={t("buyers.latestOrderLinkAria", {
                  code: latest.orderCode,
                  username: row.original.username,
                })}
                className={cn("font-mono text-xs font-medium text-primary-text hover:underline", focusRing)}
              >
                {t("buyers.latestOrderLink")}
              </Link>
              <OrderStateChip state={latest.state} />
            </div>
          );
        },
      },
    ],
    [locale, t],
  );

  const items = buyersQuery.data?.items ?? [];
  const totalBuyers = buyersQuery.data?.totalBuyers ?? 0;
  const totalItems = buyersQuery.data?.totalItems ?? 0;
  const isOffline =
    buyersQuery.fetchStatus === "paused" &&
    (buyersQuery.isPending || buyersQuery.isPlaceholderData);

  return (
    <div className="@container flex flex-col gap-5">
      <PageHeader
        title={t("buyers.title")}
        description={t("buyers.description")}
        actions={
          <div className="flex flex-wrap items-center gap-2">
            <Button asChild variant="secondary" size="sm">
              <Link href="/orders">
                <ArrowLeft aria-hidden="true" />
                <span>{t("nav.backToOrders")}</span>
              </Link>
            </Button>
            {examId ? (
              <Button
                type="button"
                variant="secondary"
                size="sm"
                onClick={() => void buyersQuery.refetch()}
              >
                <RefreshCw className="h-4 w-4" aria-hidden="true" />
                <span>{t("filters.refresh")}</span>
              </Button>
            ) : null}
          </div>
        }
      />

      <Card className="p-4">
        <div className="flex flex-col gap-3">
          <div className="flex flex-wrap items-end justify-between gap-3">
            <div ref={examSelectRef} className="flex min-w-[260px] flex-1 flex-col gap-1.5">
              <label htmlFor="buyers-exam-select" className="text-xs font-medium text-text-secondary">
                {t("buyers.examLabel")}
              </label>
              <Select
                id="buyers-exam-select"
                aria-label={t("buyers.examLabel")}
                placeholder={examsLoading ? t("filters.loadingExams") : t("buyers.examPlaceholder")}
                disabled={examsLoading && examOptions.length === 0}
                options={examOptions}
                value={examId}
                onValueChange={(nextExamId) => updateUrl(nextExamId, { page: 0 })}
              />
            </div>

            {examId && canViewExams ? (
              <Button asChild variant="outline" size="md">
                <Link href={`/exams/${encodeURIComponent(examId)}/listing`}>
                  <span>{t("buyers.openExamLink")}</span>
                  <ExternalLink aria-hidden="true" />
                </Link>
              </Button>
            ) : null}
          </div>

          {examsError ? (
            <div
              role="alert"
              className="flex flex-wrap items-center justify-between gap-2 rounded-lg border border-danger/30 bg-danger-bg px-3 py-2 text-xs text-danger"
            >
              <div className="flex items-center gap-2">
                <AlertCircle className="h-4 w-4 shrink-0" aria-hidden="true" />
                <span>{t("buyers.examLoadError")}</span>
              </div>
              <Button
                type="button"
                variant="outline"
                size="sm"
                onClick={() => void activeExamQuery.refetch()}
              >
                <RefreshCw className="h-3.5 w-3.5" aria-hidden="true" />
                <span>{t("buyers.retry")}</span>
              </Button>
            </div>
          ) : null}
        </div>
      </Card>

      {!examId ? (
        <EmptyState
          headingLevel={2}
          icon={<Users className="h-6 w-6" />}
          title={t("buyers.selectExamTitle")}
          description={t("buyers.selectExamDescription")}
        />
      ) : (
        <>
          {buyersQuery.data ? (
            <Card className="p-4">
              <div className="flex flex-wrap items-baseline justify-between gap-2">
                <dl className="flex flex-wrap items-baseline gap-2">
                  <dt className="text-sm text-text-secondary">{t("buyers.summaryTotalBuyers")}:</dt>
                  <dd className="font-mono text-xl font-bold text-text">
                    {formatNumber(totalBuyers, locale)}
                  </dd>
                </dl>
                <p className="text-xs text-text-secondary">{t("buyers.summaryNote")}</p>
              </div>
            </Card>
          ) : null}

          <LiveStatus
            message={
              buyersQuery.isPending
                ? t("list.loading")
                : buyersQuery.data
                  ? t("buyers.summaryTotalBuyers") + ": " + formatNumber(totalBuyers, locale)
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
                <Button type="button" variant="secondary" onClick={() => void buyersQuery.refetch()}>
                  {t("buyers.retry")}
                </Button>
              }
            />
          ) : buyersQuery.isError ? (
            <EmptyState
              headingLevel={2}
              icon={<AlertCircle className="h-6 w-6" />}
              title={t("buyers.errorTitle")}
              description={resolveOrderErrorMessage(buyersQuery.error, tErrors)}
              action={
                <Button type="button" variant="secondary" onClick={() => void buyersQuery.refetch()}>
                  {t("buyers.retry")}
                </Button>
              }
            />
          ) : (
            <Card padding="none" className="overflow-hidden">
              <div className="flex flex-wrap items-center justify-between gap-2 border-b border-border px-4 py-3 text-body">
                <p className="font-medium text-text tabular-nums">
                  {buyersQuery.isPending
                    ? t("list.loading")
                    : t("buyers.totalItems", { total: totalItems })}
                </p>
              </div>
              <Table
                aria-label={t("buyers.tableLabel")}
                columns={columns}
                data={items}
                getRowId={(row) => row.userId}
                loading={buyersQuery.isPending}
                loadingRows={params.size}
                refreshing={buyersQuery.isFetching && !buyersQuery.isPending}
                empty={
                  <EmptyState
                    headingLevel={2}
                    icon={<Users className="h-6 w-6" />}
                    title={t("buyers.emptyTitle")}
                    description={t("buyers.emptyDescription")}
                  />
                }
                cards={{
                  render: (buyer) => {
                    const latest = buyer.orders[0];
                    return (
                      <ListCard
                        title={
                          latest ? (
                            <ListCardTitleLink
                              linkComponent={Link}
                              href={`/orders/${encodeURIComponent(latest.orderCode)}`}
                            >
                              <span data-exam-content>{buyer.fullName}</span>
                            </ListCardTitleLink>
                          ) : (
                            <span data-exam-content>{buyer.fullName}</span>
                          )
                        }
                        caption={buyer.username}
                        status={latest ? <OrderStateChip state={latest.state} /> : undefined}
                        meta={[
                          {
                            label: t("buyers.colOrders"),
                            value: t("buyers.paidCount", { count: buyer.purchaseCount }),
                          },
                          {
                            label: t("buyers.colPaidAmount"),
                            value: formatVnd(sumBuyerPaidVnd(buyer), locale),
                          },
                          {
                            label: t("buyers.colAttempts"),
                            value: `${formatNumber(buyer.attemptsRemaining, locale)} / ${formatNumber(buyer.attemptsTotal, locale)}`,
                          },
                        ]}
                      >
                        {buyer.orders.length > 0 ? (
                          <div className="relative z-1 flex flex-wrap gap-1.5">
                            {buyer.orders.map((ord) => (
                              <Link
                                key={ord.orderId}
                                href={`/orders/${encodeURIComponent(ord.orderCode)}`}
                                className={cn(
                                  "relative z-1 inline-flex items-center rounded border border-border bg-subtle px-2 py-1 font-mono text-xs text-primary-text hover:underline pointer-coarse:min-h-11",
                                  focusRing,
                                )}
                              >
                                {ord.orderCode}
                              </Link>
                            ))}
                          </div>
                        ) : null}
                      </ListCard>
                    );
                  },
                }}
              />

              {buyersQuery.data && (buyersQuery.data.totalPages > 1 || params.page > 0) ? (
                <div className="border-t border-border px-4 py-3">
                  <Pagination
                    page={params.page + 1}
                    pageSize={params.size}
                    total={totalItems}
                    pageSizeOptions={pageSizeOptions}
                    onPageSizeChange={(nextSize) => updateUrl(examId, { size: nextSize, page: 0 })}
                    onPageChange={(nextPage) =>
                      updateUrl(examId, { page: Math.max(0, nextPage - 1) })
                    }
                  />
                </div>
              ) : null}
            </Card>
          )}
        </>
      )}
    </div>
  );
}
