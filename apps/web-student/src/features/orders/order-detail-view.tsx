"use client";

import { useQuery, useQueryClient } from "@tanstack/react-query";
import { Button, ConfirmDialog, EmptyState, notify, Skeleton, StatusChip, cn, focusRing } from "@thi360/ui";
import { ArrowLeft, Check, Clock, ShieldCheck, TriangleAlert, X } from "lucide-react";
import { useTranslations } from "next-intl";
import Link from "next/link";
import { useRef, useState } from "react";
import { type HeaderAccount, StudentHeader } from "@/components/student-header";
import { withCallbackUrl } from "@/features/account/account-links";
import { loadSession, SESSION_QUERY_KEY, type SessionLoader } from "@/features/session/api";
import { ApiStatusError } from "@/lib/api/client";
import { adminUrl } from "@/lib/env";
import {
  cancelMyOrder,
  loadMyOrderDetail,
  MY_ORDER_DETAIL_QUERY_KEY,
  type OrderCancelAction,
  type OrderDetailLoader,
  type OrderPayAction,
  payMyOrder,
} from "./api";
import {
  formatDateTime,
  formatOrderSupportSummary,
  mapOrderActionError,
  mapVnpayResponseCode,
  ORDER_STATE_TONE,
  type OrderErrorKey,
  PAYMENT_STATE_TONE,
  readBenefitsSnapshot,
} from "./format";

export function OrderDetailView({
  code,
  loadCurrentSession = loadSession,
  loadOrderDetail = loadMyOrderDetail,
  payOrder = payMyOrder,
  cancelOrder = cancelMyOrder,
  navigate = (url: string) => window.location.assign(url),
}: {
  code: string;
  loadCurrentSession?: SessionLoader;
  loadOrderDetail?: OrderDetailLoader;
  payOrder?: OrderPayAction;
  cancelOrder?: OrderCancelAction;
  navigate?: (url: string) => void;
}) {
  const t = useTranslations("orders");
  const common = useTranslations("common");
  const storefront = useTranslations("storefront");
  const detailRights = useTranslations("storefront.detail.rights");
  const support = useTranslations("landing.support");
  const queryClient = useQueryClient();

  const [paying, setPaying] = useState(false);
  const [actionError, setActionError] = useState<OrderErrorKey | null>(null);
  const [cancelOpen, setCancelOpen] = useState(false);
  const [cancelling, setCancelling] = useState(false);
  const [cancelError, setCancelError] = useState<string | undefined>();

  const payKeyRef = useRef<string | null>(null);
  const cancelKeyRef = useRef<string | null>(null);

  const sessionQuery = useQuery({
    queryKey: SESSION_QUERY_KEY,
    queryFn: loadCurrentSession,
    staleTime: 60_000,
  });

  const isStudent = sessionQuery.data?.access === "EXAM";
  const orderQuery = useQuery({
    queryKey: MY_ORDER_DETAIL_QUERY_KEY(code),
    queryFn: () => loadOrderDetail(code),
    enabled: isStudent,
  });

  const account: HeaderAccount = sessionQuery.data
    ? { kind: "user", session: sessionQuery.data }
    : sessionQuery.isLoading
      ? { kind: "loading" }
      : { kind: "none" };

  const order = orderQuery.data;
  const isOrderNotFound =
    orderQuery.isError &&
    orderQuery.error instanceof ApiStatusError &&
    (orderQuery.error.status === 404 || orderQuery.error.code === "ORDER_NOT_FOUND");

  const supportSummary = formatOrderSupportSummary();

  async function handlePay() {
    if (!order || paying) return;
    setActionError(null);
    const key = payKeyRef.current ?? crypto.randomUUID();
    payKeyRef.current = key;
    setPaying(true);
    try {
      const response = await payOrder(order.code, key);
      payKeyRef.current = null;
      navigate(response.paymentUrl);
    } catch (error) {
      payKeyRef.current = null;
      setActionError(mapOrderActionError(error));
      void orderQuery.refetch();
    } finally {
      setPaying(false);
    }
  }

  function openCancelDialog() {
    setCancelError(undefined);
    cancelKeyRef.current = crypto.randomUUID();
    setCancelOpen(true);
  }

  async function handleConfirmCancel() {
    if (!order || cancelling) return;
    const key = cancelKeyRef.current ?? crypto.randomUUID();
    cancelKeyRef.current = key;
    setCancelling(true);
    setCancelError(undefined);
    try {
      const updated = await cancelOrder(order.code, key);
      queryClient.setQueryData(MY_ORDER_DETAIL_QUERY_KEY(code), updated);
      notify.success(t("cancelDialog.success", { code: order.code }));
      setCancelOpen(false);
      cancelKeyRef.current = null;
      void orderQuery.refetch();
    } catch (error) {
      cancelKeyRef.current = null;
      const errKey = mapOrderActionError(error);
      setCancelError(t(`errors.${errKey}`));
      setActionError(errKey);
      void orderQuery.refetch();
    } finally {
      setCancelling(false);
    }
  }

  function formatStateName(rawState: string): string {
    if (
      rawState === "PENDING_PAYMENT" ||
      rawState === "PAID" ||
      rawState === "FULFILLED" ||
      rawState === "EXPIRED" ||
      rawState === "REPLACED" ||
      rawState === "CANCELLED" ||
      rawState === "REFUNDED"
    ) {
      return t(`states.${rawState}`);
    }
    return rawState;
  }

  return (
    <div className="min-h-screen bg-page text-text">
      <StudentHeader account={account} />

      <main className="mx-auto flex max-w-[1170px] flex-col gap-6 px-4 py-10 sm:px-6">
        <Link
          href="/account/orders"
          className={cn(
            "inline-flex min-h-11 items-center gap-1.5 self-start font-medium text-primary-text hover:underline",
            focusRing,
          )}
        >
          <ArrowLeft aria-hidden className="size-4" />
          {t("backToList")}
        </Link>

        {sessionQuery.isLoading && (
          <div role="status" aria-busy="true" aria-label={common("loading")} className="flex flex-col gap-4">
            <Skeleton className="h-9.5 w-64 max-w-full" />
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

        {isStudent && orderQuery.isLoading && (
          <div role="status" aria-busy="true" aria-label={common("loading")} className="flex flex-col gap-4">
            <Skeleton className="h-9.5 w-64 max-w-full" />
            <Skeleton className="h-64 w-full rounded-xl" />
            <Skeleton className="h-48 w-full rounded-xl" />
          </div>
        )}

        {isStudent && isOrderNotFound && (
          <div
            role="alert"
            className="flex flex-col items-start gap-4 rounded-xl border border-danger/30 bg-surface p-6 sm:p-8"
          >
            <div className="flex items-start gap-3 text-danger">
              <TriangleAlert aria-hidden className="mt-0.5 size-5 shrink-0" />
              <div className="flex flex-col gap-1">
                <h1 className="text-h3 font-bold text-text">{t("notFound.title")}</h1>
                <p className="text-body text-text-secondary">{t("notFound.description")}</p>
              </div>
            </div>
            <div className="flex flex-wrap gap-3">
              <Button asChild variant="secondary">
                <Link href={withCallbackUrl("/login", `/account/orders/${encodeURIComponent(code)}`)}>
                  {t("notFound.otherAccount")}
                </Link>
              </Button>
              <Button asChild>
                <Link href="/account/orders">{t("notFound.backToOrders")}</Link>
              </Button>
            </div>
          </div>
        )}

        {isStudent && orderQuery.isError && !isOrderNotFound && (
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
            <h1 className="text-title font-semibold text-text">{t("errorTitle")}</h1>
            <p className="max-w-sm text-body text-text-muted">{t("errorDescription")}</p>
            <Button type="button" variant="secondary" onClick={() => void orderQuery.refetch()}>
              {common("retry")}
            </Button>
          </div>
        )}

        {isStudent && order && (
          <>
            <div className="flex flex-wrap items-start justify-between gap-4">
              <div className="flex flex-col gap-2">
                <h1 className="text-h1 font-bold text-text tabular-nums">
                  {t("detail.heading", { code: order.code })}
                </h1>
                <div className="flex flex-wrap items-center gap-2">
                  <StatusChip tone={ORDER_STATE_TONE[order.state]}>{t(`states.${order.state}`)}</StatusChip>
                  <span className="inline-flex h-6 items-center rounded-full bg-subtle px-2.5 text-caption font-medium text-text-secondary">
                    {order.paymentMethod === "FREE" ? t("amount.freeVoucherNote") : t("amount.vnpay")}
                  </span>
                  {order.productType === "BUNDLE" && (
                    <span className="inline-flex h-6 items-center rounded-full bg-info-bg px-2.5 text-caption font-semibold text-info">
                      {t("bundleBadge")}
                    </span>
                  )}
                </div>
              </div>

              {order.state === "PENDING_PAYMENT" && (
                <div className="flex flex-wrap items-center gap-2.5">
                  <Button type="button" loading={paying} onClick={() => void handlePay()}>
                    {t("actions.continuePayment")}
                  </Button>
                  <Button type="button" variant="secondary" onClick={openCancelDialog}>
                    {t("actions.cancel")}
                  </Button>
                </div>
              )}

              {order.state === "FULFILLED" && (
                <Button asChild>
                  <Link href="/home">{t("actions.goMyExams")}</Link>
                </Button>
              )}

              {order.state === "REPLACED" && order.replacedByOrderCode && (
                <Button asChild variant="secondary">
                  <Link href={`/account/orders/${encodeURIComponent(order.replacedByOrderCode)}`}>
                    {t("actions.viewReplacement", { code: order.replacedByOrderCode })}
                  </Link>
                </Button>
              )}
            </div>

            {actionError && (
              <div
                role="alert"
                className="flex flex-wrap items-center justify-between gap-3 rounded-xl border border-danger/30 bg-danger-bg px-4 py-3.5 text-danger"
              >
                <div className="flex min-w-0 flex-1 basis-72 items-start gap-2.5">
                  <TriangleAlert aria-hidden className="mt-0.5 size-4.5 shrink-0" />
                  <span className="text-body">{t(`errors.${actionError}`)}</span>
                </div>
                <Button asChild variant="secondary" size="sm">
                  <Link
                    href={
                      actionError === "PRODUCT_DISCONTINUED" || actionError === "PRODUCT_NOT_FOUND"
                        ? "/#exams"
                        : `/checkout/${encodeURIComponent(order.productCode)}`
                    }
                  >
                    {actionError === "PRODUCT_DISCONTINUED" || actionError === "PRODUCT_NOT_FOUND"
                      ? t("actions.toCatalog")
                      : t("actions.createNew")}
                  </Link>
                </Button>
              </div>
            )}

            {order.state === "PAID" && (
              <div
                role="status"
                className="flex items-start gap-3 rounded-xl border border-warning bg-warning-bg px-4 py-3.5 text-warning"
              >
                <Clock aria-hidden className="mt-0.5 size-4.5 shrink-0" />
                <div className="flex flex-col gap-1 text-body text-text-secondary">
                  <strong className="font-semibold text-warning">{t("paidChecking", { code: order.code })}</strong>
                  <span>
                    {supportSummary
                      ? t("paidCheckingSupport", { code: order.code, contact: supportSummary })
                      : t("paidCheckingSupportPending", { code: order.code, pending: support("pending") })}
                  </span>
                </div>
              </div>
            )}

            {(() => {
              const benefits = readBenefitsSnapshot(order.benefits);
              const versionNo = order.bundleVersionNo ?? order.examVersionNo;
              const resultViewLabel = detailRights.has(`resultViews.${order.resultView}`)
                ? detailRights(`resultViews.${order.resultView}`)
                : order.resultView;

              return (
                <section
                  aria-labelledby="order-snapshot-title"
                  className="flex flex-col gap-4 rounded-xl border border-border bg-surface p-6"
                >
                  <h2 id="order-snapshot-title" className="text-h3 font-bold text-text">
                    {t("detail.snapshotTitle")}
                  </h2>
                  <dl className="grid grid-cols-[repeat(auto-fit,minmax(min(15rem,100%),1fr))] gap-x-6 gap-y-4 tabular-nums">
                    <div className="flex flex-col gap-0.5">
                      <dt className="text-caption text-text-muted">{t("detail.fields.buyer")}</dt>
                      <dd className="font-semibold text-text">
                        {order.buyerUsername}{" "}
                        <span className="font-normal text-text-secondary">· {order.buyerFullName}</span>
                      </dd>
                    </div>
                    <div className="flex flex-col gap-0.5">
                      <dt className="text-caption text-text-muted">{t("detail.fields.product")}</dt>
                      <dd className="font-semibold text-text">
                        <span data-exam-content>{order.productName}</span>{" "}
                        <span className="font-normal text-text-muted">· {order.productCode}</span>
                      </dd>
                    </div>
                    <div className="flex flex-col gap-0.5">
                      <dt className="text-caption text-text-muted">{t("detail.fields.version")}</dt>
                      <dd className="font-medium text-text">{versionNo ? `v${versionNo}` : "—"}</dd>
                    </div>
                    <div className="flex flex-col gap-0.5">
                      <dt className="text-caption text-text-muted">{t("detail.fields.listPrice")}</dt>
                      <dd className="font-medium text-text">{storefront("price", { amount: order.listPriceVnd })}</dd>
                    </div>
                    {order.promotionDiscountVnd > 0 && (
                      <div className="flex flex-col gap-0.5">
                        <dt className="text-caption text-text-muted">{t("detail.fields.promotion")}</dt>
                        <dd className="font-medium text-success">
                          −{storefront("price", { amount: order.promotionDiscountVnd })}
                        </dd>
                      </div>
                    )}
                    {order.voucherCode && (
                      <div className="flex flex-col gap-0.5">
                        <dt className="text-caption text-text-muted">{t("detail.fields.voucher")}</dt>
                        <dd className="font-medium text-text">
                          {order.voucherCode}{" "}
                          {order.voucherDiscountVnd > 0 && (
                            <span className="text-success">
                              −{storefront("price", { amount: order.voucherDiscountVnd })}
                            </span>
                          )}
                        </dd>
                      </div>
                    )}
                    <div className="flex flex-col gap-0.5">
                      <dt className="text-caption text-text-muted">{t("detail.fields.amountDue")}</dt>
                      <dd className="text-title font-bold text-primary-900">
                        {order.paymentMethod === "FREE" || order.amountDueVnd === 0
                          ? t("amount.freeVoucher")
                          : storefront("price", { amount: order.amountDueVnd })}
                      </dd>
                    </div>
                    <div className="flex flex-col gap-0.5">
                      <dt className="text-caption text-text-muted">{t("detail.fields.attempts")}</dt>
                      <dd className="font-medium text-text">
                        {t("detail.attemptsValue", { count: order.attemptsGranted })}
                      </dd>
                    </div>
                    <div className="flex flex-col gap-0.5">
                      <dt className="text-caption text-text-muted">{t("detail.fields.createdAt")}</dt>
                      <dd className="font-medium text-text">{formatDateTime(order.createdAt)}</dd>
                    </div>
                    {order.expiresAt && (
                      <div className="flex flex-col gap-0.5">
                        <dt className="text-caption text-text-muted">{t("detail.fields.expiresAt")}</dt>
                        <dd className="font-medium text-text">{formatDateTime(order.expiresAt)}</dd>
                      </div>
                    )}
                    {order.paidAt && (
                      <div className="flex flex-col gap-0.5">
                        <dt className="text-caption text-text-muted">{t("detail.fields.paidAt")}</dt>
                        <dd className="font-medium text-text">{formatDateTime(order.paidAt)}</dd>
                      </div>
                    )}
                    {order.fulfilledAt && (
                      <div className="flex flex-col gap-0.5">
                        <dt className="text-caption text-text-muted">{t("detail.fields.fulfilledAt")}</dt>
                        <dd className="font-medium text-text">{formatDateTime(order.fulfilledAt)}</dd>
                      </div>
                    )}
                  </dl>

                  <div className="border-t border-border pt-4">
                    <h3 className="mb-2 text-label font-semibold text-text">{t("detail.fields.benefits")}</h3>
                    <div className="flex flex-wrap gap-x-6 gap-y-2 text-body text-text-secondary">
                      <span>
                        {t("detail.benefits.resultView")}: <strong className="text-text">{resultViewLabel}</strong>
                      </span>
                      <span className="inline-flex items-center gap-1">
                        {benefits.aiExplanation ? (
                          <Check aria-hidden className="size-4 text-primary-text" />
                        ) : (
                          <X aria-hidden className="size-4 text-text-muted" />
                        )}
                        {t("detail.benefits.ai")}: {benefits.aiExplanation ? t("detail.benefits.yes") : t("detail.benefits.no")}
                      </span>
                      <span className="inline-flex items-center gap-1">
                        {benefits.strengthWeakness ? (
                          <Check aria-hidden className="size-4 text-primary-text" />
                        ) : (
                          <X aria-hidden className="size-4 text-text-muted" />
                        )}
                        {t("detail.benefits.strengths")}:{" "}
                        {benefits.strengthWeakness ? t("detail.benefits.yes") : t("detail.benefits.no")}
                      </span>
                      <span className="inline-flex items-center gap-1">
                        {benefits.practice ? (
                          <Check aria-hidden className="size-4 text-primary-text" />
                        ) : (
                          <X aria-hidden className="size-4 text-text-muted" />
                        )}
                        {t("detail.benefits.practice")}:{" "}
                        {benefits.practice ? t("detail.benefits.yes") : t("detail.benefits.no")}
                      </span>
                    </div>
                    {benefits.note && (
                      <p data-exam-content className="mt-2 text-field-label text-text-muted">
                        {benefits.note}
                      </p>
                    )}
                  </div>

                  {order.items.length > 0 && (order.productType === "BUNDLE" || order.items.length > 1) && (
                    <div className="border-t border-border pt-4">
                      <h3 className="mb-2 text-label font-semibold text-text">
                        {t("detail.itemsTitle", { count: order.items.length })}
                      </h3>
                      <ul className="flex flex-col gap-2">
                        {order.items.map((item) => (
                          <li
                            key={item.id}
                            className="flex flex-wrap items-center justify-between gap-2 rounded-lg bg-page px-3.5 py-2.5"
                          >
                            <div className="flex flex-wrap items-center gap-2">
                              <span data-exam-content className="font-medium text-text">
                                {item.examName}
                              </span>
                              <span className="text-caption text-text-muted">({item.examCode})</span>
                            </div>
                            <span className="text-field-label font-medium text-text-secondary tabular-nums">
                              {t("detail.attemptsValue", { count: item.attemptsGranted })}
                            </span>
                          </li>
                        ))}
                      </ul>
                    </div>
                  )}
                </section>
              );
            })()}

            <section
              aria-labelledby="order-payments-title"
              className="flex flex-col gap-4 rounded-xl border border-border bg-surface p-6"
            >
              <h2 id="order-payments-title" className="text-h3 font-bold text-text">
                {t("detail.payments.title")}
              </h2>
              {order.payments.length === 0 ? (
                <p className="text-body text-text-muted">{t("detail.payments.empty")}</p>
              ) : (
                <>
                  <div className="flex flex-col gap-3 md:hidden">
                    {order.payments.map((payment) => (
                      <div
                        key={payment.id}
                        className="flex flex-col gap-2 rounded-lg border border-border bg-page p-4 tabular-nums"
                      >
                        <div className="flex flex-wrap items-center justify-between gap-2">
                          <span className="font-semibold text-text">
                            #{String(payment.attemptSeq).padStart(2, "0")} · {payment.txnRef}
                          </span>
                          <StatusChip tone={PAYMENT_STATE_TONE[payment.state]}>
                            {t(`detail.payments.states.${payment.state}`)}
                          </StatusChip>
                        </div>
                        <div className="flex flex-wrap items-center justify-between gap-2 text-body">
                          <span className="text-text-secondary">
                            {[payment.bankCode, payment.cardType].filter(Boolean).join(" · ") || "VNPAY"}
                          </span>
                          <span className="font-semibold text-text">
                            {storefront("price", { amount: payment.amountVnd })}
                          </span>
                        </div>
                        {payment.responseCode && payment.state !== "SUCCEEDED" && (
                          <p className="text-caption text-danger">
                            {t(`vnpayReasons.${mapVnpayResponseCode(payment.responseCode)}`)}
                          </p>
                        )}
                        <div className="flex flex-wrap items-center justify-between gap-2 text-caption text-text-muted">
                          <span>{formatDateTime(payment.createdAt)}</span>
                          {payment.duplicate && (
                            <span className="rounded-full bg-warning-bg px-2 py-0.5 font-semibold text-warning">
                              {t("detail.payments.duplicate")}
                            </span>
                          )}
                        </div>
                      </div>
                    ))}
                  </div>

                  <div
                    role="region"
                    aria-label={t("detail.payments.tableLabel")}
                    tabIndex={0}
                    className={cn("hidden overflow-x-auto rounded-lg border border-border md:block", focusRing)}
                  >
                    <table className="w-full border-collapse text-left tabular-nums">
                      <thead>
                        <tr className="border-b border-border bg-table-head text-caption font-semibold text-text-muted">
                          <th scope="col" className="px-3.5 py-2.5">
                            {t("detail.payments.attemptSeq")}
                          </th>
                          <th scope="col" className="px-3.5 py-2.5">
                            {t("detail.payments.txnRef")}
                          </th>
                          <th scope="col" className="px-3.5 py-2.5">
                            {t("detail.payments.bank")}
                          </th>
                          <th scope="col" className="px-3.5 py-2.5 text-right">
                            {t("detail.payments.amount")}
                          </th>
                          <th scope="col" className="px-3.5 py-2.5">
                            {t("detail.payments.status")}
                          </th>
                          <th scope="col" className="px-3.5 py-2.5">
                            {t("detail.payments.reason")}
                          </th>
                          <th scope="col" className="px-3.5 py-2.5">
                            {t("detail.payments.createdAt")}
                          </th>
                        </tr>
                      </thead>
                      <tbody className="divide-y divide-border">
                        {order.payments.map((payment) => (
                          <tr key={payment.id}>
                            <td className="px-3.5 py-3 font-semibold">
                              {String(payment.attemptSeq).padStart(2, "0")}
                            </td>
                            <td className="px-3.5 py-3 font-medium text-text">{payment.txnRef}</td>
                            <td className="px-3.5 py-3 text-text-secondary">
                              {[payment.bankCode, payment.cardType].filter(Boolean).join(" · ") || "—"}
                            </td>
                            <td className="px-3.5 py-3 text-right font-semibold">
                              {storefront("price", { amount: payment.amountVnd })}
                            </td>
                            <td className="px-3.5 py-3">
                              <div className="flex flex-wrap items-center gap-1.5">
                                <StatusChip tone={PAYMENT_STATE_TONE[payment.state]}>
                                  {t(`detail.payments.states.${payment.state}`)}
                                </StatusChip>
                                {payment.duplicate && (
                                  <span className="rounded-full bg-warning-bg px-2 py-0.5 text-caption font-semibold text-warning">
                                    {t("detail.payments.duplicate")}
                                  </span>
                                )}
                              </div>
                            </td>
                            <td className="px-3.5 py-3 text-caption text-text-secondary">
                              {payment.responseCode && payment.state !== "SUCCEEDED"
                                ? t(`vnpayReasons.${mapVnpayResponseCode(payment.responseCode)}`)
                                : "—"}
                            </td>
                            <td className="px-3.5 py-3 text-text-secondary whitespace-nowrap">
                              {formatDateTime(payment.createdAt)}
                            </td>
                          </tr>
                        ))}
                      </tbody>
                    </table>
                  </div>
                </>
              )}
            </section>

            <section
              aria-labelledby="order-history-title"
              className="flex flex-col gap-4 rounded-xl border border-border bg-surface p-6"
            >
              <h2 id="order-history-title" className="text-h3 font-bold text-text">
                {t("detail.history.title")}
              </h2>
              {order.history.length === 0 ? (
                <p className="text-body text-text-muted">{t("detail.history.empty")}</p>
              ) : (
                <ol className="flex flex-col divide-y divide-border">
                  {order.history.map((entry) => (
                    <li key={entry.id} className="flex flex-wrap items-baseline justify-between gap-2 py-3 first:pt-0 last:pb-0">
                      <div className="flex flex-col gap-0.5">
                        <div className="flex flex-wrap items-center gap-2">
                          <span className="font-semibold text-text">
                            {entry.fromState
                              ? t("detail.history.transition", {
                                  from: formatStateName(entry.fromState),
                                  to: formatStateName(entry.toState),
                                })
                              : t("detail.history.created")}
                          </span>
                          <span className="text-caption text-text-muted">
                            {entry.actor === "LEARNER"
                              ? t("detail.history.actorLearner")
                              : entry.actor === "SYSTEM"
                                ? t("detail.history.actorSystem")
                                : entry.actor}
                          </span>
                        </div>
                        {entry.reason && <span className="text-caption text-text-secondary">{entry.reason}</span>}
                      </div>
                      <span className="text-caption text-text-muted tabular-nums">
                        {formatDateTime(entry.at)}
                      </span>
                    </li>
                  ))}
                </ol>
              )}
            </section>
          </>
        )}
      </main>

      <ConfirmDialog
        open={cancelOpen}
        onOpenChange={(open) => {
          if (!open && !cancelling) {
            setCancelOpen(false);
            setCancelError(undefined);
            cancelKeyRef.current = null;
          }
        }}
        title={order ? t("cancelDialog.title", { code: order.code }) : ""}
        description={order ? t("cancelDialog.description", { code: order.code }) : ""}
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
