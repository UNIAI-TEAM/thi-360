"use client";

import { useQuery } from "@tanstack/react-query";
import { Button, EmptyState, Skeleton, StatusChip } from "@thi360/ui";
import { CheckCircle2, Clock, RefreshCw, TriangleAlert, XCircle } from "lucide-react";
import { useTranslations } from "next-intl";
import Link from "next/link";
import { useSearchParams } from "next/navigation";
import { useEffect, useMemo, useRef, useState } from "react";
import { type HeaderAccount, StudentHeader } from "@/components/student-header";
import { withCallbackUrl } from "@/features/account/account-links";
import {
  loadMyOrderDetail,
  MY_ORDER_DETAIL_QUERY_KEY,
  type OrderDetail,
  type OrderDetailLoader,
  type OrderPayAction,
  payMyOrder,
} from "@/features/orders/api";
import {
  formatDateTime,
  formatOrderSupportSummary,
  mapOrderActionError,
  mapVnpayResponseCode,
  type OrderErrorKey,
} from "@/features/orders/format";
import { loadOptionalSession, type OptionalSessionLoader } from "@/features/session/api";
import { useOptionalSession } from "@/features/session/use-optional-session";
import { ApiStatusError } from "@/lib/api/client";
import { adminUrl } from "@/lib/env";
import { VNPAY_RETURN_QUERY_KEY, verifyVnpayReturn, type VnpayReturnVerifier } from "./api";

const POLL_INTERVAL_MS = 2000;
const POLL_TIMEOUT_MS = 60_000;

function extractVnpParams(searchParams: URLSearchParams): Record<string, string> {
  const result: Record<string, string> = {};
  searchParams.forEach((value, key) => {
    if (key.startsWith("vnp_")) {
      result[key] = value;
    }
  });
  return result;
}

function getLatestPayment(order: OrderDetail) {
  if (order.payments.length === 0) return undefined;
  return [...order.payments].sort((a, b) => b.attemptSeq - a.attemptSeq)[0];
}

export function CheckoutResultView({
  loadCurrentSession = loadOptionalSession,
  verifyReturn = verifyVnpayReturn,
  loadOrderDetail = loadMyOrderDetail,
  payOrder = payMyOrder,
  navigate = (url: string) => window.location.assign(url),
}: {
  loadCurrentSession?: OptionalSessionLoader;
  verifyReturn?: VnpayReturnVerifier;
  loadOrderDetail?: OrderDetailLoader;
  payOrder?: OrderPayAction;
  navigate?: (url: string) => void;
}) {
  const t = useTranslations("checkout.result");
  const ordersT = useTranslations("orders");
  const common = useTranslations("common");
  const storefront = useTranslations("storefront");
  const support = useTranslations("landing.support");
  const searchParams = useSearchParams();

  const vnpParams = useMemo(() => extractVnpParams(searchParams), [searchParams]);
  const hasVnpParams = Object.keys(vnpParams).length > 0;
  const serializedVnp = useMemo(
    () =>
      Object.entries(vnpParams)
        .sort(([a], [b]) => a.localeCompare(b))
        .map(([k, v]) => `${k}=${v}`)
        .join("&"),
    [vnpParams],
  );

  const directOrderCode = searchParams.get("order") ?? searchParams.get("orderCode") ?? "";
  const vnpResponseCode = searchParams.get("vnp_ResponseCode") ?? undefined;
  const currentResultUrl = useMemo(() => {
    const qs = searchParams.toString();
    return qs ? `/checkout/result?${qs}` : "/checkout/result";
  }, [searchParams]);

  const [retryingPay, setRetryingPay] = useState(false);
  const [retryPayError, setRetryPayError] = useState<OrderErrorKey | null>(null);
  const [timedOut, setTimedOut] = useState(false);

  const retryPayKeyRef = useRef<string | null>(null);
  const pollStartedAtRef = useRef<number | null>(null);

  const sessionQuery = useOptionalSession(loadCurrentSession);
  const session = sessionQuery.data;
  const isStudent = session?.access === "EXAM";

  const verifyQuery = useQuery({
    queryKey: VNPAY_RETURN_QUERY_KEY(serializedVnp),
    queryFn: () => verifyReturn(vnpParams),
    enabled: hasVnpParams,
    retry: false,
    staleTime: Infinity,
  });

  const isInvalidSignature =
    hasVnpParams &&
    verifyQuery.isError &&
    verifyQuery.error instanceof ApiStatusError &&
    (verifyQuery.error.code === "VNPAY_SIGNATURE_INVALID" ||
      verifyQuery.error.status === 422 ||
      verifyQuery.error.status === 400);

  const verifiedOrderCode = hasVnpParams ? (verifyQuery.data?.orderCode ?? "") : directOrderCode.trim();
  const canLoadOrder = Boolean(
    isStudent &&
      verifiedOrderCode &&
      (!hasVnpParams || (verifyQuery.isSuccess && !isInvalidSignature)),
  );

  const orderQuery = useQuery({
    queryKey: MY_ORDER_DETAIL_QUERY_KEY(verifiedOrderCode),
    queryFn: () => loadOrderDetail(verifiedOrderCode),
    enabled: canLoadOrder,
    retry: false,
  });

  const order = orderQuery.data;
  const latestPayment = order ? getLatestPayment(order) : undefined;
  const isOrderNotFound =
    orderQuery.isError &&
    orderQuery.error instanceof ApiStatusError &&
    (orderQuery.error.status === 404 || orderQuery.error.code === "ORDER_NOT_FOUND");

  const shouldPoll =
    Boolean(order) &&
    !timedOut &&
    (order!.state === "PAID" ||
      (order!.state === "PENDING_PAYMENT" && latestPayment?.state !== "FAILED"));

  const { isFetching: isOrderFetching, dataUpdatedAt: orderUpdatedAt, refetch: refetchOrder } = orderQuery;

  useEffect(() => {
    if (!shouldPoll) {
      pollStartedAtRef.current = null;
      return;
    }

    const startedAt = pollStartedAtRef.current ?? Date.now();
    pollStartedAtRef.current = startedAt;

    if (isOrderFetching) {
      return;
    }

    const remaining = Math.min(POLL_INTERVAL_MS, Math.max(0, POLL_TIMEOUT_MS - (Date.now() - startedAt)));
    const timer = window.setTimeout(() => {
      if (Date.now() - startedAt >= POLL_TIMEOUT_MS) {
        setTimedOut(true);
      } else {
        void refetchOrder();
      }
    }, remaining);

    return () => window.clearTimeout(timer);
  }, [shouldPoll, order?.state, isOrderFetching, orderUpdatedAt, refetchOrder]);

  async function handleRetryPay() {
    if (!order || retryingPay) return;
    setRetryPayError(null);
    const key = retryPayKeyRef.current ?? crypto.randomUUID();
    retryPayKeyRef.current = key;
    setRetryingPay(true);
    try {
      const response = await payOrder(order.code, key);
      retryPayKeyRef.current = null;
      navigate(response.paymentUrl);
    } catch (error) {
      retryPayKeyRef.current = null;
      setRetryPayError(mapOrderActionError(error));
      void orderQuery.refetch();
    } finally {
      setRetryingPay(false);
    }
  }

  function handleCheckAgain() {
    pollStartedAtRef.current = Date.now();
    setTimedOut(false);
    void orderQuery.refetch();
  }

  const account: HeaderAccount = session
    ? { kind: "user", session }
    : sessionQuery.isLoading
      ? { kind: "loading" }
      : { kind: "none" };

  const supportSummary = formatOrderSupportSummary();

  function renderBody() {
    if (hasVnpParams && verifyQuery.isLoading) {
      return (
        <div className="flex flex-col gap-4 rounded-xl border border-border bg-surface p-6">
          <Skeleton className="h-8 w-56" />
          <Skeleton className="h-5 w-full" />
          <Skeleton className="h-10 w-44" />
        </div>
      );
    }

    if (isInvalidSignature) {
      return (
        <section className="flex flex-col gap-5 rounded-xl border border-danger/30 bg-surface p-6">
          <div className="flex items-start gap-3">
            <XCircle aria-hidden className="mt-0.5 size-6 shrink-0 text-danger" />
            <div className="flex flex-col gap-1.5">
              <h1 className="text-h2 font-bold text-text">{t("invalidSignatureHeading")}</h1>
              <p className="text-body text-text-secondary">{t("invalidSignatureBody")}</p>
            </div>
          </div>
          <div className="flex flex-wrap gap-3">
            <Button asChild variant="primary">
              <Link href="/account/orders">{t("viewOrders")}</Link>
            </Button>
            <Button asChild variant="secondary">
              <Link href="/home">{t("viewMyExams")}</Link>
            </Button>
          </div>
        </section>
      );
    }

    if (hasVnpParams && verifyQuery.isError) {
      return (
        <section className="flex flex-col gap-5 rounded-xl border border-danger/30 bg-surface p-6">
          <div className="flex items-start gap-3">
            <TriangleAlert aria-hidden className="mt-0.5 size-6 shrink-0 text-danger" />
            <div className="flex flex-col gap-1.5">
              <h1 className="text-h2 font-bold text-text">{t("loadErrorHeading")}</h1>
              <p className="text-body text-text-secondary">{t("loadErrorBody")}</p>
            </div>
          </div>
          <div className="flex flex-wrap gap-3">
            <Button type="button" variant="secondary" onClick={() => void verifyQuery.refetch()}>
              {common("retry")}
            </Button>
            <Button asChild variant="primary">
              <Link href="/account/orders">{t("viewOrders")}</Link>
            </Button>
          </div>
        </section>
      );
    }

    if (sessionQuery.isLoading) {
      return (
        <div className="flex flex-col gap-4 rounded-xl border border-border bg-surface p-6">
          <Skeleton className="h-8 w-56" />
          <Skeleton className="h-5 w-full" />
        </div>
      );
    }

    if (!session) {
      if (!verifiedOrderCode) {
        return (
          <EmptyState
            icon={<TriangleAlert />}
            title={t("missingOrderHeading")}
            description={t("missingOrderBody")}
            action={
              <Button asChild variant="primary">
                <Link href={withCallbackUrl("/login", currentResultUrl)}>{ordersT("guest.login")}</Link>
              </Button>
            }
          />
        );
      }

      const vnpState = verifyQuery.data?.paymentState;
      const loginHref = withCallbackUrl("/login", currentResultUrl);

      if (vnpState === "SUCCEEDED") {
        return (
          <section className="flex flex-col gap-5 rounded-xl border border-primary/30 bg-surface p-6">
            <div className="flex items-start gap-3">
              <CheckCircle2 aria-hidden className="mt-0.5 size-6 shrink-0 text-primary-text" />
              <div className="flex flex-col gap-1.5">
                <h1 className="text-h2 font-bold text-text">{t("guestSuccessHeading")}</h1>
                <p className="text-body text-text-secondary">
                  {t("guestSuccessBody", { code: verifiedOrderCode })}
                </p>
              </div>
            </div>
            <div className="flex flex-wrap gap-3">
              <Button asChild variant="exam">
                <Link href={loginHref}>{t("guestSignInView")}</Link>
              </Button>
              <Button asChild variant="secondary">
                <Link href="/">{common("backHome")}</Link>
              </Button>
            </div>
          </section>
        );
      }

      if (vnpState === "FAILED") {
        const reason = ordersT(`vnpayReasons.${mapVnpayResponseCode(vnpResponseCode)}`);
        return (
          <section className="flex flex-col gap-5 rounded-xl border border-danger/30 bg-surface p-6">
            <div className="flex items-start gap-3">
              <XCircle aria-hidden className="mt-0.5 size-6 shrink-0 text-danger" />
              <div className="flex flex-col gap-1.5">
                <h1 className="text-h2 font-bold text-text">{t("guestFailedHeading")}</h1>
                <p className="text-body text-text-secondary">
                  {t("failedGuestBody", { code: verifiedOrderCode, reason })}
                </p>
              </div>
            </div>
            <div className="flex flex-wrap gap-3">
              <Button asChild variant="primary">
                <Link href={loginHref}>{t("guestSignInCheck")}</Link>
              </Button>
            </div>
          </section>
        );
      }

      return (
        <section className="flex flex-col gap-5 rounded-xl border border-warning bg-surface p-6">
          <div className="flex items-start gap-3">
            <Clock aria-hidden className="mt-0.5 size-6 shrink-0 text-warning" />
            <div className="flex flex-col gap-1.5">
              <div className="flex flex-wrap items-center gap-2">
                <h1 className="text-h2 font-bold text-text">{t("guestCheckingHeading")}</h1>
                <StatusChip tone="warning">{t("checkingBadge")}</StatusChip>
              </div>
              <p className="text-body text-text-secondary">
                {t("guestCheckingBody", { code: verifiedOrderCode })}
              </p>
            </div>
          </div>
          <div className="flex flex-wrap gap-3">
            <Button asChild variant="primary">
              <Link href={loginHref}>{t("guestSignInCheck")}</Link>
            </Button>
          </div>
        </section>
      );
    }

    if (session.access !== "EXAM") {
      return (
        <EmptyState
          icon={<TriangleAlert />}
          title={ordersT("adminNotice.title")}
          description={ordersT("adminNotice.description")}
          action={
            <Button asChild variant="primary">
              <a href={adminUrl()}>{ordersT("adminNotice.cta")}</a>
            </Button>
          }
        />
      );
    }

    if (!verifiedOrderCode) {
      return (
        <EmptyState
          icon={<TriangleAlert />}
          title={t("missingOrderHeading")}
          description={t("missingOrderBody")}
          action={
            <Button asChild variant="primary">
              <Link href="/account/orders">{t("viewOrders")}</Link>
            </Button>
          }
        />
      );
    }

    if (orderQuery.isLoading) {
      return (
        <div className="flex flex-col gap-4 rounded-xl border border-border bg-surface p-6">
          <Skeleton className="h-8 w-56" />
          <Skeleton className="h-5 w-full" />
          <Skeleton className="h-10 w-44" />
        </div>
      );
    }

    if (isOrderNotFound) {
      return (
        <EmptyState
          icon={<TriangleAlert />}
          title={t("notOwnedHeading")}
          description={t("notOwnedBody", { code: verifiedOrderCode, username: session.username })}
          action={
            <div className="flex flex-wrap justify-center gap-3">
              <Button asChild variant="primary">
                <Link href={withCallbackUrl("/login", currentResultUrl)}>{ordersT("notFound.otherAccount")}</Link>
              </Button>
              <Button asChild variant="secondary">
                <Link href="/account/orders">{t("viewOrders")}</Link>
              </Button>
            </div>
          }
        />
      );
    }

    if (orderQuery.isError || !order) {
      return (
        <EmptyState
          icon={<TriangleAlert />}
          title={t("loadErrorHeading")}
          description={t("loadErrorBody")}
          action={
            <Button type="button" variant="secondary" onClick={() => void orderQuery.refetch()}>
              {common("retry")}
            </Button>
          }
        />
      );
    }

    if (order.state === "FULFILLED") {
      const isFreeVoucher = order.amountDueVnd === 0 && order.voucherDiscountVnd > 0;
      return (
        <section className="flex flex-col gap-5 rounded-xl border border-primary/30 bg-surface p-6">
          <div className="flex items-start gap-3">
            <CheckCircle2 aria-hidden className="mt-0.5 size-6 shrink-0 text-primary-text" />
            <div className="flex flex-col gap-1.5">
              <div className="flex flex-wrap items-center gap-2">
                <h1 className="text-h2 font-bold text-text">{t("successHeading")}</h1>
                <StatusChip tone="success">{ordersT("states.FULFILLED")}</StatusChip>
              </div>
              <p className="text-body text-text-secondary">
                {isFreeVoucher ? t("successFreeBody", { code: order.code }) : t("successBody", { code: order.code })}
              </p>
            </div>
          </div>

          <div className="flex flex-col gap-1.5 rounded-lg bg-page p-4 tabular-nums">
            <span data-exam-content className="text-title font-semibold text-text">
              {order.productName}
            </span>
            <div className="flex flex-wrap items-center gap-x-4 gap-y-1 text-caption text-text-secondary">
              <span>{order.code}</span>
              <span>
                {isFreeVoucher
                  ? ordersT("amount.freeVoucher")
                  : storefront("price", { amount: order.amountDueVnd })}
              </span>
              {order.fulfilledAt && <span>{formatDateTime(order.fulfilledAt)}</span>}
            </div>
          </div>

          <div className="flex flex-wrap gap-3">
            <Button asChild variant="exam">
              <Link href="/home">{t("viewMyExams")}</Link>
            </Button>
            <Button asChild variant="secondary">
              <Link href={`/account/orders/${encodeURIComponent(order.code)}`}>{t("viewOrder")}</Link>
            </Button>
          </div>
        </section>
      );
    }

    if (order.state === "PENDING_PAYMENT" && latestPayment?.state === "FAILED") {
      const reasonText = ordersT(`vnpayReasons.${mapVnpayResponseCode(latestPayment.responseCode)}`);
      return (
        <section className="flex flex-col gap-5 rounded-xl border border-danger/30 bg-surface p-6">
          <div className="flex items-start gap-3">
            <XCircle aria-hidden className="mt-0.5 size-6 shrink-0 text-danger" />
            <div className="flex flex-col gap-1.5">
              <h1 className="text-h2 font-bold text-text">{t("failedHeading")}</h1>
              <p className="text-body text-text-secondary">{t("failedBody", { code: order.code })}</p>
              <p className="text-body font-medium text-danger">{reasonText}</p>
            </div>
          </div>

          <div className="flex flex-col gap-1 rounded-lg bg-page p-4 text-caption text-text-secondary tabular-nums">
            <span data-exam-content className="text-body font-semibold text-text">
              {order.productName}
            </span>
            <span>
              {t("txnRefLabel")} {latestPayment.txnRef} · {storefront("price", { amount: order.amountDueVnd })}
            </span>
          </div>

          {retryPayError && (
            <p role="alert" className="text-body font-medium text-danger">
              {ordersT(`errors.${retryPayError}`)}
            </p>
          )}

          <div className="flex flex-wrap gap-3">
            <Button
              type="button"
              variant="primary"
              loading={retryingPay}
              onClick={() => void handleRetryPay()}
            >
              {retryingPay ? t("retryingPay") : t("retryPay")}
            </Button>
            <Button asChild variant="secondary">
              <Link href={`/account/orders/${encodeURIComponent(order.code)}`}>{t("viewOrder")}</Link>
            </Button>
          </div>
        </section>
      );
    }

    if (order.state === "PENDING_PAYMENT" || order.state === "PAID") {
      return (
        <section className="flex flex-col gap-5 rounded-xl border border-warning bg-surface p-6">
          <div className="flex items-start gap-3">
            <Clock aria-hidden className="mt-0.5 size-6 shrink-0 text-warning" />
            <div className="flex flex-col gap-1.5">
              <div className="flex flex-wrap items-center gap-2">
                <h1 className="text-h2 font-bold text-text">
                  {timedOut ? t("checkingTimeoutHeading") : t("checkingHeading")}
                </h1>
                <StatusChip tone="warning">{t("checkingBadge")}</StatusChip>
              </div>
              <p className="text-body text-text-secondary">
                {timedOut
                  ? t("checkingTimeoutBody", { code: order.code })
                  : t("checkingPending", { code: order.code })}
              </p>
              {timedOut && (
                <p className="text-caption text-text-secondary">
                  {t("checkingTimeoutSupport", {
                    code: order.code,
                    contact: supportSummary || support("pending"),
                  })}
                </p>
              )}
            </div>
          </div>

          <div className="flex flex-col gap-1 rounded-lg bg-page p-4 text-caption text-text-secondary tabular-nums">
            <span data-exam-content className="text-body font-semibold text-text">
              {order.productName}
            </span>
            <span>
              {order.code} · {storefront("price", { amount: order.amountDueVnd })}
            </span>
          </div>

          <div className="flex flex-wrap gap-3">
            <Button
              type="button"
              variant="secondary"
              loading={orderQuery.isFetching}
              onClick={handleCheckAgain}
            >
              <RefreshCw aria-hidden className="size-4" />
              {t("checkAgain")}
            </Button>
            <Button asChild variant="primary">
              <Link href={`/account/orders/${encodeURIComponent(order.code)}`}>{t("viewOrders")}</Link>
            </Button>
            <Button asChild variant="secondary">
              <Link href="/home">{t("viewMyExams")}</Link>
            </Button>
          </div>
        </section>
      );
    }

    return (
      <section className="flex flex-col gap-5 rounded-xl border border-border bg-surface p-6">
        <div className="flex items-start gap-3">
          <TriangleAlert aria-hidden className="mt-0.5 size-6 shrink-0 text-text-muted" />
          <div className="flex flex-col gap-1.5">
            <div className="flex flex-wrap items-center gap-2">
              <h1 className="text-h2 font-bold text-text">{t("closedHeading")}</h1>
              <StatusChip tone="neutral">{ordersT(`states.${order.state}`)}</StatusChip>
            </div>
            <p className="text-body text-text-secondary">{t("closedBody", { code: order.code })}</p>
          </div>
        </div>
        <div className="flex flex-wrap gap-3">
          {order.replacedByOrderCode ? (
            <Button asChild variant="primary">
              <Link href={`/account/orders/${encodeURIComponent(order.replacedByOrderCode)}`}>
                {ordersT("actions.viewReplacement", { code: order.replacedByOrderCode })}
              </Link>
            </Button>
          ) : (
            <Button asChild variant="primary">
              <Link href={`/checkout/${encodeURIComponent(order.productCode)}`}>{t("buyAgain")}</Link>
            </Button>
          )}
          <Button asChild variant="secondary">
            <Link href="/account/orders">{t("viewOrders")}</Link>
          </Button>
        </div>
      </section>
    );
  }

  return (
    <div className="min-h-screen bg-page text-text">
      <StudentHeader account={account} />
      <main className="mx-auto flex w-full max-w-[760px] flex-col gap-6 px-4 py-8 sm:px-6">{renderBody()}</main>
    </div>
  );
}
