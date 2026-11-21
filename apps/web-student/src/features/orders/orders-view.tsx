"use client";

import { useQuery, useQueryClient } from "@tanstack/react-query";
import {
  Button,
  ConfirmDialog,
  EmptyState,
  notify,
  Pagination,
  Skeleton,
  StatusChip,
  cn,
  focusRing,
} from "@thi360/ui";
import { ArrowRight, Receipt, ShieldCheck, TriangleAlert } from "lucide-react";
import { useTranslations } from "next-intl";
import Link from "next/link";
import { useRef, useState } from "react";
import { type HeaderAccount, StudentHeader } from "@/components/student-header";
import { loadSession, SESSION_QUERY_KEY, type SessionLoader } from "@/features/session/api";
import { adminUrl } from "@/lib/env";
import {
  cancelMyOrder,
  loadMyOrders,
  MY_ORDER_DETAIL_QUERY_KEY,
  MY_ORDERS_QUERY_KEY,
  type OrderCancelAction,
  type OrderPayAction,
  ORDERS_PAGE_SIZE,
  type OrdersLoader,
  type OrderSummary,
  payMyOrder,
} from "./api";
import {
  formatDateTime,
  formatOrderSupportSummary,
  formatTimeOnly,
  mapOrderActionError,
  ORDER_STATE_TONE,
  type OrderErrorKey,
} from "./format";

function OrderAmountCell({ order }: { order: OrderSummary }) {
  const t = useTranslations("orders");
  const storefront = useTranslations("storefront");
  const totalDiscount = order.promotionDiscountVnd + order.voucherDiscountVnd;

  if (order.paymentMethod === "FREE" || order.amountDueVnd === 0) {
    return (
      <div className="tabular-nums">
        <div className="font-semibold text-text">{storefront("price", { amount: 0 })}</div>
        <div className="text-caption text-text-muted">{t("amount.freeVoucherNote")}</div>
      </div>
    );
  }

  return (
    <div className="tabular-nums">
      <div className="font-semibold text-text">{storefront("price", { amount: order.amountDueVnd })}</div>
      <div className="text-caption text-text-muted">
        {totalDiscount > 0
          ? `${t("amount.vnpay")} · ${t("amount.discountSummary", { amount: storefront("price", { amount: totalDiscount }) })}`
          : t("amount.vnpay")}
      </div>
    </div>
  );
}

function OrderStatusCell({ order }: { order: OrderSummary }) {
  const t = useTranslations("orders");
  return (
    <div className="flex flex-col items-start gap-1">
      <StatusChip tone={ORDER_STATE_TONE[order.state]}>{t(`states.${order.state}`)}</StatusChip>
      {order.state === "PENDING_PAYMENT" && order.expiresAt && (
        <span className="text-caption text-text-muted tabular-nums">
          {t("pendingHoldUntil", { time: formatTimeOnly(order.expiresAt) })}
        </span>
      )}
      {order.state === "PAID" && (
        <span className="text-caption text-text-muted">{t("stateSubtext.PAID")}</span>
      )}
    </div>
  );
}

export function OrdersView({
  loadCurrentSession = loadSession,
  loadOrders = loadMyOrders,
  payOrder = payMyOrder,
  cancelOrder = cancelMyOrder,
  navigate = (url: string) => window.location.assign(url),
}: {
  loadCurrentSession?: SessionLoader;
  loadOrders?: OrdersLoader;
  payOrder?: OrderPayAction;
  cancelOrder?: OrderCancelAction;
  navigate?: (url: string) => void;
}) {
  const t = useTranslations("orders");
  const common = useTranslations("common");
  const support = useTranslations("landing.support");
  const queryClient = useQueryClient();

  const [page, setPage] = useState(0);
  const [payingCode, setPayingCode] = useState<string | null>(null);
  const [actionError, setActionError] = useState<{ code: OrderErrorKey; productCode: string } | null>(null);
  const [cancelTarget, setCancelTarget] = useState<OrderSummary | null>(null);
  const [cancelling, setCancelling] = useState(false);
  const [cancelError, setCancelError] = useState<string | undefined>();

  const payKeyRef = useRef<{ orderCode: string; key: string } | null>(null);
  const cancelKeyRef = useRef<{ orderCode: string; key: string } | null>(null);

  const sessionQuery = useQuery({
    queryKey: SESSION_QUERY_KEY,
    queryFn: loadCurrentSession,
    staleTime: 60_000,
  });

  const isStudent = sessionQuery.data?.access === "EXAM";
  const ordersQuery = useQuery({
    queryKey: MY_ORDERS_QUERY_KEY(page, ORDERS_PAGE_SIZE),
    queryFn: () => loadOrders({ page, size: ORDERS_PAGE_SIZE }),
    enabled: isStudent,
  });

  const account: HeaderAccount = sessionQuery.data
    ? { kind: "user", session: sessionQuery.data }
    : sessionQuery.isLoading
      ? { kind: "loading" }
      : { kind: "none" };

  const supportSummary = formatOrderSupportSummary();

  async function handlePay(order: OrderSummary) {
    if (payingCode) return;
    setActionError(null);
    const key =
      payKeyRef.current?.orderCode === order.code ? payKeyRef.current.key : crypto.randomUUID();
    payKeyRef.current = { orderCode: order.code, key };
    setPayingCode(order.code);
    try {
      const response = await payOrder(order.code, key);
      payKeyRef.current = null;
      navigate(response.paymentUrl);
    } catch (error) {
      payKeyRef.current = null;
      setActionError({ code: mapOrderActionError(error), productCode: order.productCode });
      void ordersQuery.refetch();
    } finally {
      setPayingCode(null);
    }
  }

  function openCancelDialog(order: OrderSummary) {
    setCancelError(undefined);
    cancelKeyRef.current = { orderCode: order.code, key: crypto.randomUUID() };
    setCancelTarget(order);
  }

  async function handleConfirmCancel() {
    if (!cancelTarget || cancelling) return;
    const key =
      cancelKeyRef.current?.orderCode === cancelTarget.code
        ? cancelKeyRef.current.key
        : crypto.randomUUID();
    cancelKeyRef.current = { orderCode: cancelTarget.code, key };
    setCancelling(true);
    setCancelError(undefined);
    try {
      await cancelOrder(cancelTarget.code, key);
      notify.success(t("cancelDialog.success", { code: cancelTarget.code }));
      void queryClient.invalidateQueries({ queryKey: MY_ORDER_DETAIL_QUERY_KEY(cancelTarget.code) });
      setCancelTarget(null);
      cancelKeyRef.current = null;
      void ordersQuery.refetch();
    } catch (error) {
      cancelKeyRef.current = null;
      const errKey = mapOrderActionError(error);
      setCancelError(t(`errors.${errKey}`));
      void ordersQuery.refetch();
    } finally {
      setCancelling(false);
    }
  }

  function renderOrderActions(order: OrderSummary, compact: boolean) {
    const detailHref = `/account/orders/${encodeURIComponent(order.code)}`;
    const btnSize = compact ? "md" : "sm";

    return (
      <div className={cn("flex flex-wrap items-center gap-2", compact && "pt-1")}>
        {order.state === "PENDING_PAYMENT" && (
          <>
            <Button
              type="button"
              size={btnSize}
              loading={payingCode === order.code}
              disabled={payingCode !== null && payingCode !== order.code}
              onClick={() => void handlePay(order)}
              className={compact ? "flex-1" : undefined}
            >
              {t("actions.continuePayment")}
            </Button>
            <Button
              type="button"
              variant="secondary"
              size={btnSize}
              onClick={() => openCancelDialog(order)}
            >
              {t("actions.cancel")}
            </Button>
          </>
        )}
        {order.state === "FULFILLED" && (
          <Button asChild variant="soft" size={btnSize}>
            <Link href="/home">{t("actions.goMyExams")}</Link>
          </Button>
        )}
        {order.state === "PAID" && (
          <p className="text-caption text-text-secondary">
            {supportSummary
              ? t("paidCheckingSupport", { code: order.code, contact: supportSummary })
              : t("paidCheckingSupportPending", { code: order.code, pending: support("pending") })}
          </p>
        )}
        <Button asChild variant="link" size={btnSize}>
          <Link href={detailHref}>
            {t("actions.viewDetail")}
            <ArrowRight aria-hidden />
          </Link>
        </Button>
      </div>
    );
  }

  return (
    <div className="min-h-screen bg-page text-text">
      <StudentHeader account={account} />

      <main className="mx-auto flex max-w-[1170px] flex-col gap-6 px-4 py-10 sm:px-6">
        {sessionQuery.isLoading && (
          <div role="status" aria-busy="true" aria-label={common("loading")} className="flex flex-col gap-4">
            <Skeleton className="h-9.5 w-48 max-w-full" />
            <Skeleton className="h-5.25 w-96 max-w-full" />
            <Skeleton className="h-64 w-full rounded-xl" />
          </div>
        )}

        {sessionQuery.isError && (
          <div className="rounded-xl border border-border bg-surface">
            <EmptyState
              icon={<TriangleAlert />}
              title={t("errorTitle")}
              description={t("errorDescription")}
              action={
                <Button type="button" onClick={() => void sessionQuery.refetch()}>
                  {common("retry")}
                </Button>
              }
            />
          </div>
        )}

        {sessionQuery.data && sessionQuery.data.access === "MANAGE" && (
          <div className="rounded-xl border border-border bg-surface">
            <EmptyState
              headingLevel={1}
              icon={<ShieldCheck />}
              title={t("adminNotice.title")}
              description={t("adminNotice.description")}
              action={
                <Button asChild className="h-11">
                  <a href={adminUrl()}>{t("adminNotice.cta")}</a>
                </Button>
              }
            />
          </div>
        )}

        {isStudent && sessionQuery.data && (
          <>
            <div className="flex flex-col gap-1.5">
              <h1 className="text-h1 font-bold text-text">{t("pageTitle")}</h1>
              <p className="text-body text-text-muted">
                {t("subtitle", { username: sessionQuery.data.username })}
              </p>
            </div>

            {actionError && (
              <div
                role="alert"
                className="flex flex-wrap items-center justify-between gap-3 rounded-xl border border-danger/30 bg-danger-bg px-4 py-3.5 text-danger"
              >
                <div className="flex min-w-0 flex-1 basis-72 items-start gap-2.5">
                  <TriangleAlert aria-hidden className="mt-0.5 size-4.5 shrink-0" />
                  <span className="text-body">{t(`errors.${actionError.code}`)}</span>
                </div>
                <Button asChild variant="secondary" size="sm">
                  <Link
                    href={
                      actionError.code === "PRODUCT_DISCONTINUED" || actionError.code === "PRODUCT_NOT_FOUND"
                        ? "/#exams"
                        : `/checkout/${encodeURIComponent(actionError.productCode)}`
                    }
                  >
                    {actionError.code === "PRODUCT_DISCONTINUED" || actionError.code === "PRODUCT_NOT_FOUND"
                      ? t("actions.toCatalog")
                      : t("actions.createNew")}
                  </Link>
                </Button>
              </div>
            )}

            {ordersQuery.isLoading && (
              <div
                role="status"
                aria-busy="true"
                aria-label={common("loading")}
                className="flex flex-col gap-3 rounded-xl border border-border bg-surface p-5"
              >
                {[0, 1, 2, 3].map((row) => (
                  <Skeleton key={row} className="h-12 w-full rounded-lg" />
                ))}
              </div>
            )}

            {ordersQuery.isError && (
              <div
                role="alert"
                className="flex flex-col items-center gap-3 rounded-xl border border-border bg-surface px-6 py-12 text-center"
              >
                <span
                  aria-hidden
                  className="grid size-12 place-items-center rounded-xl bg-danger-bg text-danger [&_svg]:size-6"
                >
                  <TriangleAlert />
                </span>
                <h2 className="text-title font-semibold text-text">{t("errorTitle")}</h2>
                <p className="max-w-sm text-body text-text-muted">{t("errorDescription")}</p>
                <Button type="button" variant="secondary" onClick={() => void ordersQuery.refetch()}>
                  {common("retry")}
                </Button>
              </div>
            )}

            {ordersQuery.data && ordersQuery.data.items.length === 0 && (
              <div className="rounded-xl border border-border bg-surface">
                <EmptyState
                  icon={<Receipt />}
                  title={t("emptyTitle")}
                  description={t("emptyDescription")}
                  action={
                    <Button asChild className="h-11">
                      <Link href="/#exams">{t("exploreExams")}</Link>
                    </Button>
                  }
                />
              </div>
            )}

            {ordersQuery.data && ordersQuery.data.items.length > 0 && (
              <>
                <div className="flex flex-col gap-3 md:hidden">
                  {ordersQuery.data.items.map((order) => (
                    <article
                      key={order.id}
                      className="flex flex-col gap-3 rounded-xl border border-border bg-surface p-4"
                    >
                      <div className="flex flex-wrap items-center justify-between gap-2">
                        <Link
                          href={`/account/orders/${encodeURIComponent(order.code)}`}
                          className={cn("font-semibold text-primary-text hover:underline tabular-nums", focusRing)}
                        >
                          {order.code}
                        </Link>
                        <OrderStatusCell order={order} />
                      </div>

                      <div className="flex flex-wrap items-center gap-2">
                        {order.productType === "BUNDLE" && (
                          <span className="inline-flex h-6 items-center rounded-full bg-info-bg px-2.5 text-caption font-semibold text-info">
                            {t("bundleBadge")}
                          </span>
                        )}
                        <span data-exam-content className="font-medium text-text">
                          {order.productName}
                        </span>
                      </div>

                      <div className="flex flex-wrap items-baseline justify-between gap-2 border-t border-border pt-2.5">
                        <span className="text-caption text-text-muted tabular-nums">
                          {formatDateTime(order.createdAt)}
                        </span>
                        <OrderAmountCell order={order} />
                      </div>

                      {renderOrderActions(order, true)}
                    </article>
                  ))}
                </div>

                <div
                  role="region"
                  aria-label={t("tableLabel")}
                  tabIndex={0}
                  className={cn(
                    "hidden overflow-x-auto rounded-xl border border-border bg-surface md:block",
                    focusRing,
                  )}
                >
                  <table className="w-full border-collapse text-left">
                    <thead>
                      <tr className="border-b border-border bg-table-head text-caption font-semibold text-text-muted">
                        <th scope="col" className="px-4 py-3">
                          {t("columns.code")}
                        </th>
                        <th scope="col" className="px-4 py-3">
                          {t("columns.product")}
                        </th>
                        <th scope="col" className="px-4 py-3">
                          {t("columns.createdAt")}
                        </th>
                        <th scope="col" className="px-4 py-3 text-right">
                          {t("columns.amount")}
                        </th>
                        <th scope="col" className="px-4 py-3">
                          {t("columns.status")}
                        </th>
                        <th scope="col" className="px-4 py-3">
                          {t("columns.actions")}
                        </th>
                      </tr>
                    </thead>
                    <tbody className="divide-y divide-border">
                      {ordersQuery.data.items.map((order) => (
                        <tr key={order.id} className="hover:bg-subtle/50">
                          <td className="px-4 py-3.5 align-top font-semibold whitespace-nowrap tabular-nums">
                            <Link
                              href={`/account/orders/${encodeURIComponent(order.code)}`}
                              className={cn("text-primary-text hover:underline", focusRing)}
                            >
                              {order.code}
                            </Link>
                          </td>
                          <td className="px-4 py-3.5 align-top">
                            <div className="flex flex-wrap items-center gap-2">
                              {order.productType === "BUNDLE" && (
                                <span className="inline-flex h-6 items-center rounded-full bg-info-bg px-2.5 text-caption font-semibold text-info">
                                  {t("bundleBadge")}
                                </span>
                              )}
                              <span data-exam-content className="font-medium text-text">
                                {order.productName}
                              </span>
                            </div>
                          </td>
                          <td className="px-4 py-3.5 align-top text-text-secondary whitespace-nowrap tabular-nums">
                            {formatDateTime(order.createdAt)}
                          </td>
                          <td className="px-4 py-3.5 text-right align-top whitespace-nowrap">
                            <OrderAmountCell order={order} />
                          </td>
                          <td className="px-4 py-3.5 align-top">
                            <OrderStatusCell order={order} />
                          </td>
                          <td className="px-4 py-3.5 align-top">{renderOrderActions(order, false)}</td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>

                {ordersQuery.data.totalPages > 1 && (
                  <Pagination
                    page={page + 1}
                    pageSize={ORDERS_PAGE_SIZE}
                    total={ordersQuery.data.totalItems}
                    onPageChange={(nextPage) => setPage(nextPage - 1)}
                  />
                )}
              </>
            )}
          </>
        )}
      </main>

      <ConfirmDialog
        open={cancelTarget !== null}
        onOpenChange={(open) => {
          if (!open && !cancelling) {
            setCancelTarget(null);
            setCancelError(undefined);
            cancelKeyRef.current = null;
          }
        }}
        title={cancelTarget ? t("cancelDialog.title", { code: cancelTarget.code }) : ""}
        description={cancelTarget ? t("cancelDialog.description", { code: cancelTarget.code }) : ""}
        confirmLabel={t("cancelDialog.confirm")}
        cancelLabel={t("cancelDialog.keep")}
        tone="danger"
        loading={cancelling}
        error={cancelError}
        onConfirm={() => void handleConfirmCancel()}
      />
    </div>
  );
}
