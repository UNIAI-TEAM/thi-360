"use client";

import { AlertCircle, ArrowLeft, ExternalLink, RefreshCw, Users } from "lucide-react";
import Link from "next/link";
import { usePathname, useRouter, useSearchParams } from "next/navigation";
import { useLocale, useTranslations } from "next-intl";
import { type ComponentProps, useMemo } from "react";

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
  focusRing,
} from "@thi360/ui";

import { usePermissions } from "@/features/session/session-provider";

import { resolveOrderErrorMessage } from "../errors";
import { formatNumber, formatOrderDateTime, formatVnd } from "../format";
import {
  useAdminExamsForFilterQuery,
  useExamBuyersQuery,
  useOrderDerivedExamsForFilterQuery,
} from "../queries";
import type { ExamBuyerRow, ExamBuyersQueryParams } from "../types";
import { OrderStateChip } from "./order-status-chips";

const DEFAULT_PAGE_SIZE = 20;
const DEFAULT_SORT = "lastPaidAt,desc";

function parseParams(sp: ReturnType<typeof useSearchParams>): {
  examId: string;
  query: ExamBuyersQueryParams;
} {
  const examId = sp.get("exam") ?? "";
  const rawPage = Number(sp.get("page") ?? "0");
  const page = Number.isInteger(rawPage) && rawPage >= 0 ? rawPage : 0;
  const rawSize = Number(sp.get("size") ?? String(DEFAULT_PAGE_SIZE));
  const size = Number.isInteger(rawSize) && rawSize >= 1 && rawSize <= 100 ? rawSize : DEFAULT_PAGE_SIZE;
  const sort = sp.get("sort") || DEFAULT_SORT;
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

  const canViewExams = !permissions.isLoading && permissions.can("EXAM.VIEW");
  const adminExamsQuery = useAdminExamsForFilterQuery(canViewExams);
  const derivedExamsQuery = useOrderDerivedExamsForFilterQuery(!permissions.isLoading && !canViewExams);

  const buyersQuery = useExamBuyersQuery(examId, params, Boolean(examId));

  const updateUrl = (nextExamId: string, nextQuery: Partial<ExamBuyersQueryParams>) => {
    const merged: ExamBuyersQueryParams = { ...params, ...nextQuery };
    router.replace(`${pathname}${buildSearchString(nextExamId, merged)}`, { scroll: false });
  };

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
        label: examId,
        textValue: examId,
      });
    }
    return opts;
  }, [canViewExams, adminExamsQuery.data, derivedExamsQuery.data, examId]);

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
                    className={`font-mono text-xs text-primary-text hover:underline ${focusRing}`}
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
          <span className="font-mono text-xs font-semibold text-text whitespace-nowrap">
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
          <span className="font-mono text-xs text-text-secondary whitespace-nowrap">
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
                className={`font-mono text-xs font-medium text-primary-text hover:underline ${focusRing}`}
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
  const isOffline = buyersQuery.fetchStatus === "paused" && buyersQuery.isPending;

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
        <div className="flex flex-wrap items-end justify-between gap-3">
          <div className="flex min-w-[260px] flex-1 flex-col gap-1.5">
            <label htmlFor="buyers-exam-select" className="text-xs font-medium text-text-secondary">
              {t("buyers.examLabel")}
            </label>
            <Select
              id="buyers-exam-select"
              aria-label={t("buyers.examLabel")}
              placeholder={t("buyers.examPlaceholder")}
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
            <>
              <Table
                aria-label={t("buyers.tableLabel")}
                columns={columns}
                data={items}
                getRowId={(row) => row.userId}
                loading={buyersQuery.isPending}
                loadingRows={5}
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
                            value: `${buyer.attemptsRemaining} / ${buyer.attemptsTotal}`,
                          },
                        ]}
                        actions={
                          latest ? (
                            <Button asChild variant="secondary" size="sm">
                              <Link href={`/orders/${encodeURIComponent(latest.orderCode)}`}>
                                {t("buyers.latestOrderLink")}
                              </Link>
                            </Button>
                          ) : undefined
                        }
                      />
                    );
                  },
                }}
              />

              {buyersQuery.data && buyersQuery.data.totalPages > 1 ? (
                <Pagination
                  page={params.page + 1}
                  pageSize={params.size}
                  total={totalItems}
                  onPageChange={(nextPage) =>
                    updateUrl(examId, { page: Math.max(0, nextPage - 1) })
                  }
                />
              ) : null}
            </>
          )}
        </>
      )}
    </div>
  );
}
