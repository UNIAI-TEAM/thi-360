"use client";

import { useQuery, useQueryClient } from "@tanstack/react-query";
import {
  Button,
  CardList,
  CardListItem,
  ConfirmDialog,
  EmptyState,
  ListCard,
  ListCardTag,
  ListCardTitleLink,
  notify,
  Pagination,
  Skeleton,
  StatusChip,
  cn,
  focusRing,
  focusRingInset,
  useCardMode,
} from "@thi360/ui";
import { ArrowRight, Receipt, ShieldCheck, TriangleAlert } from "lucide-react";
import { useTranslations } from "next-intl";
import Link from "next/link";
import { useEffect, useRef, useState } from "react";
import { type HeaderAccount, StudentHeader } from "@/components/student-header";
import { loadSession, SESSION_QUERY_KEY, type SessionLoader } from "@/features/session/api";
import { adminUrl } from "@/lib/env";
import { coarseTextHitArea } from "@/lib/styles";
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
  focusAfterDialogUnmount,
  formatDateTime,
  formatOrderSupportSummary,
  formatTimeOnly,
  mapOrderActionError,
  ORDER_STATE_TONE,
  type OrderErrorKey,
  shouldResetIdempotencyKey,
} from "./format";

const ORDERS_CARD_BREAKPOINT = 768;

function useInlineOverflow(active: boolean) {
  const ref = useRef<HTMLDivElement>(null);
  const [overflows, setOverflows] = useState(false);

  useEffect(() => {
    if (!active) return;
    const scroller = ref.current;
    if (!scroller || typeof ResizeObserver === "undefined") return;
    const measure = () => setOverflows(scroller.scrollWidth - scroller.clientWidth > 1);
    measure();
    const observer = new ResizeObserver(measure);
    observer.observe(scroller);
    if (scroller.firstElementChild) observer.observe(scroller.firstElementChild);
    return () => observer.disconnect();
  }, [active]);

  return [ref, active && overflows] as const;
}

function getFreeAmountText(order: OrderSummary, t: ReturnType<typeof useTranslations<"orders">>) {
  return order.voucherDiscountVnd > 0 ? t("amount.freeVoucherNote") : t("amount.free");
}

function OrderAmountCell({ order }: { order: OrderSummary }) {
  const t = useTranslations("orders");
  const storefront = useTranslations("storefront");
  const totalDiscount = order.promotionDiscountVnd + order.voucherDiscountVnd;

  if (order.paymentMethod === "FREE" || order.amountDueVnd === 0) {
    return (
      <div className="tabular-nums">
        <div className="font-semibold text-text">{storefront("price", { amount: 0 })}</div>
        <div className="text-caption text-text-muted">{getFreeAmountText(order, t)}</div>
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
  const storefront = useTranslations("storefront");
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
  const inFlightActionRef = useRef(false);
  const headingRef = useRef<HTMLHeadingElement | null>(null);
  const pendingCancelFocusRef = useRef<string | null>(null);

  const sessionQuery = useQuery({
    queryKey: SESSION_QUERY_KEY,
    queryFn: loadCurrentSession,
    staleTime: 60_000,
  });

  const userId = sessionQuery.data?.id ?? null;
  const isStudent = sessionQuery.data?.access === "EXAM";
  const ordersQuery = useQuery({
    queryKey: MY_ORDERS_QUERY_KEY(page, ORDERS_PAGE_SIZE, userId),
    queryFn: () => loadOrders({ page, size: ORDERS_PAGE_SIZE }),
    enabled: isStudent,
  });

  const hasOrders = Boolean(isStudent && ordersQuery.data && ordersQuery.data.items.length > 0);
  const [ordersContainerRef, cardMode] = useCardMode<HTMLDivElement>(hasOrders, ORDERS_CARD_BREAKPOINT);
  const [tableScrollerRef, tableOverflows] = useInlineOverflow(hasOrders && !cardMode);

  useEffect(() => {
    const cancelledCode = pendingCancelFocusRef.current;
    if (cancelTarget !== null || !cancelledCode) return;
    pendingCancelFocusRef.current = null;
    return focusAfterDialogUnmount(() => {
      const orderLink = ordersContainerRef.current?.querySelector<HTMLAnchorElement>(
        `a[href="/account/orders/${encodeURIComponent(cancelledCode)}"]`,
      );
      return orderLink ?? headingRef.current;
    });
  }, [cancelTarget, ordersContainerRef]);

  const account: HeaderAccount = sessionQuery.data
    ? { kind: "user", session: sessionQuery.data }
    : sessionQuery.isLoading
      ? { kind: "loading" }
      : { kind: "none" };

  const supportSummary = formatOrderSupportSummary();

  async function handlePay(order: OrderSummary) {
    if (inFlightActionRef.current || payingCode !== null || cancelling) return;
    inFlightActionRef.current = true;
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
      if (shouldResetIdempotencyKey(error)) {
        payKeyRef.current = null;
      }
      setActionError({ code: mapOrderActionError(error), productCode: order.productCode });
      void ordersQuery.refetch();
    } finally {
      inFlightActionRef.current = false;
      setPayingCode(null);
    }
  }

  function openCancelDialog(order: OrderSummary) {
    if (inFlightActionRef.current || payingCode !== null || cancelling) return;
    setCancelError(undefined);
    if (cancelKeyRef.current?.orderCode !== order.code) {
      cancelKeyRef.current = { orderCode: order.code, key: crypto.randomUUID() };
    }
    setCancelTarget(order);
  }

  async function handleConfirmCancel() {
    if (!cancelTarget || inFlightActionRef.current || payingCode !== null || cancelling) return;
    inFlightActionRef.current = true;
    const cancelledCode = cancelTarget.code;
    const key =
      cancelKeyRef.current?.orderCode === cancelledCode
        ? cancelKeyRef.current.key
        : crypto.randomUUID();
    cancelKeyRef.current = { orderCode: cancelledCode, key };
    setCancelling(true);
    setCancelError(undefined);
    try {
      await cancelOrder(cancelledCode, key);
      notify.success(t("cancelDialog.success", { code: cancelledCode }));
      void queryClient.invalidateQueries({ queryKey: MY_ORDER_DETAIL_QUERY_KEY(cancelledCode, userId) });
      pendingCancelFocusRef.current = cancelledCode;
      setCancelTarget(null);
      cancelKeyRef.current = null;
      void ordersQuery.refetch();
    } catch (error) {
      if (shouldResetIdempotencyKey(error)) {
        cancelKeyRef.current = null;
      }
      const errKey = mapOrderActionError(error);
      setCancelError(t(`errors.${errKey}`));
      void ordersQuery.refetch();
    } finally {
      inFlightActionRef.current = false;
      setCancelling(false);
    }
  }

  function renderOrderActions(order: OrderSummary, compact: boolean) {
    const detailHref = `/account/orders/${encodeURIComponent(order.code)}`;
    const btnSize = compact ? "md" : "sm";
    const anyActionPending = payingCode !== null || cancelling;

    return (
      <div className={cn("flex flex-wrap items-center gap-2", compact && "pt-1")}>
        {order.state === "PENDING_PAYMENT" && (
          <>
            <Button
              type="button"
              size={btnSize}
              loading={payingCode === order.code}
              disabled={cancelling || (payingCode !== null && payingCode !== order.code)}
              onClick={() => void handlePay(order)}
              className={cn(compact && "flex-1", "pointer-coarse:min-h-11")}
            >
              {t("actions.continuePayment")}
            </Button>
            <Button
              type="button"
              variant="secondary"
              size={btnSize}
              disabled={anyActionPending}
              onClick={() => openCancelDialog(order)}
              className="pointer-coarse:min-h-11"
            >
              {t("actions.cancel")}
            </Button>
          </>
        )}
        {order.state === "FULFILLED" && (
          <Button asChild variant="soft" size={btnSize} className="pointer-coarse:min-h-11">
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
        {!compact && (
          <Button asChild variant="link" size={btnSize} className={coarseTextHitArea}>
            <Link href={detailHref}>
              {t("actions.viewDetail")}
              <ArrowRight aria-hidden />
            </Link>
          </Button>
        )}
      </div>
    );
  }

  function renderActionErrorCta(error: { code: OrderErrorKey; productCode: string }) {
    if (error.code === "ORDER_EXPIRED" || error.code === "PAYMENT_ATTEMPTS_EXCEEDED") {
      return (
        <Button asChild variant="secondary" size="sm" className={coarseTextHitArea}>
          <Link href={`/checkout/${encodeURIComponent(error.productCode)}`}>{t("actions.createNew")}</Link>
        </Button>
      );
    }
    if (error.code === "PRODUCT_DISCONTINUED" || error.code === "PRODUCT_NOT_FOUND") {
      return (
        <Button asChild variant="secondary" size="sm" className={coarseTextHitArea}>
          <Link href="/#exams">{t("actions.toCatalog")}</Link>
        </Button>
      );
    }
    return null;
  }

  return (
    <div className="min-h-screen bg-page text-text">
      <StudentHeader account={account} />

      <main className="mx-auto flex max-w-[1440px] flex-col gap-6 px-4 py-10 sm:px-6">
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
              headingLevel={1}
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
              <h1 ref={headingRef} tabIndex={-1} className="text-h1 font-bold text-text outline-hidden">
                {t("pageTitle")}
              </h1>
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
                {renderActionErrorCta(actionError)}
              </div>
            )}

            {ordersQuery.isLoading && !ordersQuery.data && (
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

            {ordersQuery.isError && !ordersQuery.data && (
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

            {ordersQuery.isError && ordersQuery.data && (
              <div
                role="alert"
                className="flex flex-wrap items-center justify-between gap-3 rounded-xl border border-warning bg-warning-bg px-4 py-3.5"
              >
                <div className="flex min-w-0 flex-1 basis-72 items-start gap-2.5 text-warning">
                  <TriangleAlert aria-hidden className="mt-0.5 size-4.5 shrink-0" />
                  <p className="text-body">
                    <strong className="font-semibold">{t("staleTitle")}</strong>{" "}
                    <span className="text-text-secondary">
                      {ordersQuery.dataUpdatedAt > 0
                        ? t("staleDescription", {
                            time: formatTimeOnly(new Date(ordersQuery.dataUpdatedAt).toISOString()),
                          })
                        : t("staleDescriptionNoTime")}
                    </span>
                  </p>
                </div>
                <Button
                  type="button"
                  variant="secondary"
                  loading={ordersQuery.isFetching}
                  onClick={() => void ordersQuery.refetch()}
                >
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

            {hasOrders && ordersQuery.data && (
              <>
                <div ref={ordersContainerRef} className="min-w-0">
                  {cardMode ? (
                    <CardList
                      aria-label={t("tableLabel")}
                      className="rounded-xl border border-border bg-surface"
                    >
                      {ordersQuery.data.items.map((order) => (
                        <CardListItem key={order.id}>
                          <div className="min-w-0 flex-1">
                            <ListCard
                              title={
                                <ListCardTitleLink
                                  href={`/account/orders/${encodeURIComponent(order.code)}`}
                                  linkComponent={Link}
                                >
                                  <span className="tabular-nums">{order.code}</span>
                                </ListCardTitleLink>
                              }
                              caption={<span data-exam-content>{order.productName}</span>}
                              status={
                                <StatusChip tone={ORDER_STATE_TONE[order.state]}>
                                  {t(`states.${order.state}`)}
                                </StatusChip>
                              }
                              tags={
                                order.productType === "BUNDLE" ? (
                                  <ListCardTag>{t("bundleBadge")}</ListCardTag>
                                ) : undefined
                              }
                              meta={[
                                { label: t("columns.createdAt"), value: formatDateTime(order.createdAt) },
                                {
                                  label: t("columns.amount"),
                                  value:
                                    order.paymentMethod === "FREE" || order.amountDueVnd === 0
                                      ? order.voucherDiscountVnd > 0
                                        ? t("amount.freeVoucher")
                                        : storefront("price", { amount: 0 })
                                      : storefront("price", { amount: order.amountDueVnd }),
                                },
                              ]}
                            >
                              {order.state === "PENDING_PAYMENT" && order.expiresAt && (
                                <p className="text-caption text-text-muted tabular-nums">
                                  {t("pendingHoldUntil", { time: formatTimeOnly(order.expiresAt) })}
                                </p>
                              )}
                              {order.state === "PAID" && (
                                <p className="text-caption text-text-muted">{t("stateSubtext.PAID")}</p>
                              )}
                              <div className="relative z-1 pt-1">{renderOrderActions(order, true)}</div>
                            </ListCard>
                          </div>
                        </CardListItem>
                      ))}
                    </CardList>
                  ) : (
                    <div
                      ref={tableScrollerRef}
                      role={tableOverflows ? "region" : undefined}
                      aria-label={tableOverflows ? t("tableLabel") : undefined}
                      tabIndex={tableOverflows ? 0 : undefined}
                      className={cn("overflow-x-auto rounded-xl border border-border bg-surface", focusRingInset)}
                    >
                      <table aria-label={t("tableLabel")} className="w-full border-collapse text-left">
                        <thead>
                          <tr className="border-b border-border bg-table-head text-caption font-semibold text-text-muted">
                            <th scope="col" className="px-4 py-3">
                              {t("columns.code")}
                            </th>
                            <th scope="col" className="min-w-48 px-4 py-3">
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
                                  className={cn(
                                    "inline-flex items-center rounded-sm text-primary-text hover:underline",
                                    focusRing,
                                    coarseTextHitArea,
                                  )}
                                >
                                  {order.code}
                                </Link>
                              </td>
                              <td className="min-w-48 px-4 py-3.5 align-top">
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
                  )}
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
            if (!cancelError) {
              cancelKeyRef.current = null;
            }
            setCancelTarget(null);
            setCancelError(undefined);
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
