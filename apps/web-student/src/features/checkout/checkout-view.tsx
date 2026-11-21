"use client";

import { useQuery, useQueryClient } from "@tanstack/react-query";
import { Button, cn, EmptyState, focusRing, Skeleton, StatusChip } from "@thi360/ui";
import { pickName } from "@thi360/web-kit/i18n";
import {
  ArrowLeft,
  Check,
  Clock,
  ShieldCheck,
  TriangleAlert,
  UserCheck,
  X,
} from "lucide-react";
import { useLocale, useTranslations } from "next-intl";
import Link from "next/link";
import { useSearchParams } from "next/navigation";
import { useEffect, useMemo, useRef, useState } from "react";
import { type HeaderAccount, StudentHeader } from "@/components/student-header";
import { withCallbackUrl } from "@/features/account/account-links";
import { formatOrderSupportSummary, shouldResetIdempotencyKey } from "@/features/orders/format";
import {
  loadSession,
  SESSION_QUERY_KEY,
  type SessionLoader,
  type StudentSession,
} from "@/features/session/api";
import { type ExamAccessLoader, loadExamAccess } from "@/features/storefront/exam-access";
import { rangeText, REVIEW_BUILD_MODE } from "@/features/storefront/format";
import { FormatChip } from "@/features/storefront/product-card";
import { ApiStatusError } from "@/lib/api/client";
import { adminUrl } from "@/lib/env";
import { coarseTextHitArea } from "@/lib/styles";
import {
  CHECKOUT_PRODUCT_QUERY_KEY,
  CHECKOUT_QUOTE_QUERY_KEY,
  type CheckoutProductLoader,
  createOrder,
  fetchPriceQuote,
  loadProductForCheckout,
  type OrderCreateAction,
  OrderConflictError,
  type PriceQuote,
  type PriceQuoteAction,
  type ProductCheckoutLookup,
  ProductGoneError,
} from "./api";
import { mapVoucherErrorKey, VoucherBox, type VoucherErrorKey } from "./voucher-box";

function parseOptionalInt(raw: string | null, min: number): number | undefined {
  if (!raw || !/^\d+$/.test(raw.trim())) return undefined;
  const parsed = Number.parseInt(raw.trim(), 10);
  return Number.isSafeInteger(parsed) && parsed >= min ? parsed : undefined;
}

type SubmitErrorState =
  | { kind: "rateLimited" }
  | { kind: "paymentAttemptsExceeded" }
  | { kind: "voucher"; key: VoucherErrorKey }
  | { kind: "generic" };

export function CheckoutView({
  code,
  initialProduct,
  loadCurrentSession = loadSession,
  loadProduct = loadProductForCheckout,
  loadAccess = loadExamAccess,
  requestQuote = fetchPriceQuote,
  submitOrder = createOrder,
  navigate = (url: string) => window.location.assign(url),
}: {
  code: string;
  initialProduct?: ProductCheckoutLookup;
  loadCurrentSession?: SessionLoader;
  loadProduct?: CheckoutProductLoader;
  loadAccess?: ExamAccessLoader;
  requestQuote?: PriceQuoteAction;
  submitOrder?: OrderCreateAction;
  navigate?: (url: string) => void;
}) {
  const t = useTranslations("checkout");
  const ordersT = useTranslations("orders");
  const common = useTranslations("common");
  const storefront = useTranslations("storefront");
  const detailRights = useTranslations("storefront.detail.rights");
  const support = useTranslations("landing.support");
  const locale = useLocale();
  const searchParams = useSearchParams();
  const queryClient = useQueryClient();

  const seenVersionNo = parseOptionalInt(searchParams.get("v"), 1);
  const seenPrice = parseOptionalInt(searchParams.get("p"), 0);
  const currentQueryString = searchParams.toString();
  const currentCheckoutPath = useMemo(() => {
    const base = `/checkout/${encodeURIComponent(code)}`;
    return currentQueryString ? `${base}?${currentQueryString}` : base;
  }, [code, currentQueryString]);

  const [baselineSession, setBaselineSession] = useState<StudentSession | null>(null);
  const [accountConflict, setAccountConflict] = useState<{
    previousUsername: string;
    current: StudentSession;
  } | null>(null);
  const [overrideQuote, setOverrideQuote] = useState<PriceQuote | null>(null);
  const [goneProduct, setGoneProduct] = useState<{ code: string; name: string } | null>(null);
  const [priceChangedSeen, setPriceChangedSeen] = useState<{ seenPrice?: number } | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const [submitError, setSubmitError] = useState<SubmitErrorState | null>(null);
  const [voucherResetVersion, setVoucherResetVersion] = useState(0);

  const idempotencyKeyRef = useRef<string | null>(null);
  const inFlightSubmitRef = useRef(false);
  const paySectionRef = useRef<HTMLDivElement | null>(null);
  const payButtonRef = useRef<HTMLButtonElement | null>(null);
  const prevCodeRef = useRef(code);

  useEffect(() => {
    if (prevCodeRef.current === code) return;
    prevCodeRef.current = code;
    setOverrideQuote(null);
    setGoneProduct(null);
    setPriceChangedSeen(null);
    setSubmitError(null);
    setVoucherResetVersion((v) => v + 1);
    idempotencyKeyRef.current = null;
  }, [code]);

  const sessionQuery = useQuery({
    queryKey: SESSION_QUERY_KEY,
    queryFn: loadCurrentSession,
    staleTime: 0,
    refetchOnWindowFocus: true,
    retry: false,
  });

  const liveSession = sessionQuery.data;
  if (!baselineSession && liveSession?.access === "EXAM") {
    setBaselineSession(liveSession);
  }

  const isSessionUnauthorized =
    sessionQuery.isError &&
    sessionQuery.error instanceof ApiStatusError &&
    sessionQuery.error.status === 401;

  const isStudent = liveSession?.access === "EXAM";
  const acknowledgedSession = baselineSession ?? (isStudent ? liveSession : null);
  const accountChanged =
    accountConflict ??
    (baselineSession && liveSession && liveSession.access === "EXAM" && liveSession.id !== baselineSession.id
      ? { previousUsername: baselineSession.username, current: liveSession }
      : null);

  const productQuery = useQuery({
    queryKey: CHECKOUT_PRODUCT_QUERY_KEY(code),
    queryFn: () => loadProduct(code),
    initialData: initialProduct,
    enabled: !initialProduct && !goneProduct,
  });

  const baseQuoteQueryKey = CHECKOUT_QUOTE_QUERY_KEY(
    code,
    seenVersionNo,
    seenPrice,
    acknowledgedSession?.id ?? null,
  );

  const preQuoteLookup: ProductCheckoutLookup | undefined = goneProduct
    ? { status: "gone", product: goneProduct }
    : (productQuery.data ?? initialProduct);
  const preQuoteProduct = preQuoteLookup?.status === "ok" ? preQuoteLookup.product : undefined;

  const baseQuoteQuery = useQuery({
    queryKey: baseQuoteQueryKey,
    queryFn: () =>
      requestQuote({
        code,
        ...(seenPrice !== undefined ? { seenPrice } : {}),
        ...(seenVersionNo !== undefined ? { seenVersionNo } : {}),
      }),
    enabled: isStudent && Boolean(preQuoteProduct) && !goneProduct,
    retry: false,
  });

  const quoteGoneProduct =
    baseQuoteQuery.error instanceof ProductGoneError ? baseQuoteQuery.error.product : null;
  const resolvedGoneProduct = goneProduct ?? quoteGoneProduct;

  const effectiveLookup: ProductCheckoutLookup | undefined = resolvedGoneProduct
    ? { status: "gone", product: resolvedGoneProduct }
    : (productQuery.data ?? initialProduct);
  const product = effectiveLookup?.status === "ok" ? effectiveLookup.product : undefined;

  const accessQuery = useQuery({
    queryKey: ["checkout", "access", code, acknowledgedSession?.id ?? null],
    queryFn: () => loadAccess([code]),
    enabled: isStudent && Boolean(product),
    staleTime: 0,
  });

  const activeQuote = overrideQuote ?? baseQuoteQuery.data;

  const hasBaselineMismatch = useMemo(() => {
    if (!activeQuote) return false;
    if (activeQuote.changed) return true;
    if (seenVersionNo !== undefined && activeQuote.versionNo !== seenVersionNo) return true;
    if (baseQuoteQuery.data && activeQuote.versionNo !== baseQuoteQuery.data.versionNo) return true;
    if (!overrideQuote && seenPrice !== undefined && activeQuote.finalPrice !== seenPrice) return true;
    return false;
  }, [activeQuote, baseQuoteQuery.data, overrideQuote, seenVersionNo, seenPrice]);

  const showPriceChangedBanner = Boolean(priceChangedSeen || hasBaselineMismatch);
  const displaySeenPrice = priceChangedSeen?.seenPrice ?? seenPrice;

  const accessUnavailable =
    accessQuery.isLoading || accessQuery.isError || accessQuery.data?.kind !== "rows";

  const accessRow = useMemo(() => {
    if (accessQuery.data?.kind !== "rows") return undefined;
    return accessQuery.data.rows.find((row) => row.code === code);
  }, [accessQuery.data, code]);

  const pendingOrderCode = accessRow?.pendingOrderCode ?? null;
  const isBlockedByChecking = Boolean(accessRow && (accessRow.state === "CHECKING" || accessRow.hasPendingFulfillment));
  const hasExistingAccess = Boolean(accessRow && (accessRow.state === "CAN_START" || accessRow.state === "IN_PROGRESS"));

  const account: HeaderAccount = liveSession
    ? { kind: "user", session: liveSession }
    : sessionQuery.isLoading || isSessionUnauthorized
      ? { kind: "loading" }
      : { kind: "none" };

  const displaySession = accountChanged ? accountChanged.current : (acknowledgedSession ?? liveSession);
  const supportSummary = formatOrderSupportSummary();

  async function handleAcknowledgeNewAccount() {
    if (!accountChanged) return;
    const nextUser = accountChanged.current;
    setBaselineSession(nextUser);
    setAccountConflict(null);
    setOverrideQuote(null);
    setSubmitError(null);
    setVoucherResetVersion((v) => v + 1);
    idempotencyKeyRef.current = null;
    await Promise.all([
      queryClient.invalidateQueries({ queryKey: ["checkout", "access", code] }),
      queryClient.invalidateQueries({ queryKey: ["checkout", "quote", code] }),
    ]);
  }

  async function handleCreateOrder() {
    if (
      inFlightSubmitRef.current ||
      submitting ||
      accessUnavailable ||
      isBlockedByChecking ||
      Boolean(accountChanged) ||
      !activeQuote ||
      !acknowledgedSession
    ) {
      return;
    }

    inFlightSubmitRef.current = true;
    setSubmitting(true);
    setSubmitError(null);

    const idempotencyKey = idempotencyKeyRef.current ?? crypto.randomUUID();
    idempotencyKeyRef.current = idempotencyKey;

    try {
      const response = await submitOrder(
        {
          code,
          expectedAccountId: acknowledgedSession.id,
          expectedAmountDue: activeQuote.finalPrice,
          expectedVersionNo: activeQuote.versionNo,
          ...(activeQuote.voucher ? { voucherCode: activeQuote.voucher.code } : {}),
        },
        idempotencyKey,
      );

      idempotencyKeyRef.current = null;

      if (response.next === "FULFILLED") {
        navigate(`/checkout/result?order=${encodeURIComponent(response.order.code)}`);
        return;
      }
      if (response.next === "CHECKING") {
        navigate(`/checkout/result?order=${encodeURIComponent(response.order.code)}&state=checking`);
        return;
      }
      if (response.paymentUrl) {
        navigate(response.paymentUrl);
        return;
      }
      navigate(`/checkout/result?order=${encodeURIComponent(response.order.code)}`);
    } catch (error) {
      if (shouldResetIdempotencyKey(error)) {
        idempotencyKeyRef.current = null;
      }

      if (error instanceof ProductGoneError) {
        setGoneProduct(error.product);
        return;
      }

      if (error instanceof OrderConflictError) {
        if (error.code === "PRICE_CHANGED" && error.quote) {
          setPriceChangedSeen({ seenPrice: activeQuote.finalPrice });
          setOverrideQuote(error.quote);
          setVoucherResetVersion((v) => v + 1);
          return;
        }
        if (error.code === "ACCOUNT_CHANGED") {
          const refreshed = await sessionQuery.refetch();
          const current = refreshed.isSuccess ? refreshed.data : null;
          if (current && current.access === "EXAM" && current.id !== acknowledgedSession.id) {
            setAccountConflict({
              previousUsername: acknowledgedSession.username,
              current,
            });
          } else if (!current || current.access !== "EXAM") {
            navigate(withCallbackUrl("/login", currentCheckoutPath));
          } else {
            setSubmitError({ kind: "generic" });
          }
          return;
        }
      }

      if (error instanceof ApiStatusError) {
        if (error.status === 401) {
          void sessionQuery.refetch();
          return;
        }
        if (error.status === 429 || error.code === "RATE_LIMITED") {
          setSubmitError({ kind: "rateLimited" });
          return;
        }
        if (error.status === 422 || error.code === "PAYMENT_ATTEMPTS_EXCEEDED" || error.code?.startsWith("VOUCHER_")) {
          if (error.code === "PAYMENT_ATTEMPTS_EXCEEDED") {
            setSubmitError({ kind: "paymentAttemptsExceeded" });
            return;
          }
          if (error.code?.startsWith("VOUCHER_")) {
            setSubmitError({ kind: "voucher", key: mapVoucherErrorKey(error) });
            setOverrideQuote(null);
            setVoucherResetVersion((v) => v + 1);
            void baseQuoteQuery.refetch();
            return;
          }
        }
      }

      setSubmitError({ kind: "generic" });
    } finally {
      inFlightSubmitRef.current = false;
      setSubmitting(false);
    }
  }

  function renderSubmitErrorText(err: SubmitErrorState): string {
    if (err.kind === "rateLimited") return t("summary.errorRateLimited");
    if (err.kind === "paymentAttemptsExceeded") return ordersT("errors.PAYMENT_ATTEMPTS_EXCEEDED");
    if (err.kind === "voucher") return t(`voucher.errors.${err.key}`);
    return t("summary.errorGeneric");
  }

  function renderAccessBanner() {
    if (accessQuery.isError) {
      return (
        <section
          role="alert"
          className="flex flex-wrap items-center justify-between gap-4 rounded-xl border border-danger/30 bg-danger-bg p-5"
        >
          <div className="flex min-w-0 flex-1 basis-72 items-start gap-3">
            <TriangleAlert aria-hidden className="mt-0.5 size-5 shrink-0 text-danger" />
            <div className="flex flex-col gap-1">
              <h2 className="text-title font-semibold text-danger">{t("accessBanner.loadErrorTitle")}</h2>
              <p className="text-body text-text-secondary">{t("accessBanner.loadErrorDescription")}</p>
            </div>
          </div>
          <Button type="button" variant="secondary" onClick={() => void accessQuery.refetch()}>
            {common("retry")}
          </Button>
        </section>
      );
    }

    if (!accessRow) return null;

    if (isBlockedByChecking) {
      const resultHref = pendingOrderCode
        ? `/checkout/result?order=${encodeURIComponent(pendingOrderCode)}&state=checking`
        : "/account/orders";
      return (
        <section
          role="status"
          className="flex flex-wrap items-center justify-between gap-4 rounded-xl border border-warning border-l-4 bg-warning-bg p-5"
        >
          <div className="flex min-w-0 flex-1 basis-72 items-start gap-3">
            <Clock aria-hidden className="mt-0.5 size-5 shrink-0 text-warning" />
            <div className="flex flex-col gap-1">
              <h2 className="text-title font-semibold text-warning">
                {t("accessBanner.checkingTitle", { orderCode: pendingOrderCode ?? "" })}
              </h2>
              <p className="text-body text-text-secondary">{t("accessBanner.checkingDescription")}</p>
            </div>
          </div>
          <Button asChild variant="secondary" className="shrink-0">
            <Link href={resultHref}>{t("accessBanner.viewPaymentStatus")}</Link>
          </Button>
        </section>
      );
    }

    if (hasExistingAccess) {
      const isInProgress = accessRow.state === "IN_PROGRESS" && Boolean(accessRow.activeAttemptId);
      const enterHref = isInProgress
        ? `/attempts/${encodeURIComponent(accessRow.activeAttemptId!)}`
        : accessRow.entitlementId
          ? `/prepare/${encodeURIComponent(accessRow.entitlementId)}`
          : "/home";

      return (
        <section
          role="status"
          className="flex flex-wrap items-center justify-between gap-4 rounded-xl border border-border-primary bg-primary-50 p-5"
        >
          <div className="flex min-w-0 flex-1 basis-72 flex-col gap-1">
            <h2 className="text-title font-semibold text-primary-text">
              {isInProgress
                ? t("accessBanner.existingInProgressTitle", { count: accessRow.attemptsLeft })
                : t("accessBanner.existingTitle", { count: accessRow.attemptsLeft })}
            </h2>
          </div>
          <div className="flex flex-wrap items-center gap-2.5">
            <Button asChild variant="exam">
              <Link href={enterHref} prefetch={false}>
                {isInProgress ? t("accessBanner.continueAttempt") : t("accessBanner.enterExam")}
              </Link>
            </Button>
            <Button
              type="button"
              variant="secondary"
              onClick={() => {
                paySectionRef.current?.scrollIntoView({ behavior: "smooth", block: "center" });
                payButtonRef.current?.focus();
              }}
            >
              {t("accessBanner.buyMore")}
            </Button>
          </div>
        </section>
      );
    }

    return null;
  }

  function renderSkeleton() {
    return (
      <div role="status" aria-busy="true" aria-label={common("loading")} className="flex flex-col gap-4">
        <Skeleton className="h-9 w-64" />
        <Skeleton className="h-44 w-full rounded-xl" />
        <Skeleton className="h-64 w-full rounded-xl" />
      </div>
    );
  }

  return (
    <div className="min-h-screen bg-page text-text">
      <StudentHeader account={account} />

      <main className="mx-auto flex w-full max-w-[1170px] flex-col gap-6 px-4 py-8 sm:px-6">
        <div className="flex flex-wrap items-center gap-3 text-body">
          <Link
            href={`/exams/${encodeURIComponent(code)}`}
            className={cn(
              "inline-flex min-h-11 items-center gap-1.5 rounded-sm font-medium text-text-secondary transition-colors hover:text-text",
              focusRing,
            )}
          >
            <ArrowLeft aria-hidden className="size-4" />
            {t("backToExam")}
          </Link>
        </div>

        {sessionQuery.isLoading || isSessionUnauthorized ? (
          renderSkeleton()
        ) : sessionQuery.isError ? (
          <EmptyState
            headingLevel={1}
            icon={<TriangleAlert />}
            title={t("loadError.title")}
            description={t("loadError.description")}
            action={
              <Button type="button" variant="secondary" onClick={() => void sessionQuery.refetch()}>
                {common("retry")}
              </Button>
            }
          />
        ) : !liveSession ? (
          renderSkeleton()
        ) : liveSession.access !== "EXAM" ? (
          <EmptyState
            headingLevel={1}
            icon={<TriangleAlert />}
            title={t("account.adminBlockedTitle")}
            description={t("account.adminBlockedDescription")}
            action={
              <Button asChild variant="primary">
                <a href={adminUrl()}>{t("account.adminBlockedCta")}</a>
              </Button>
            }
          />
        ) : !effectiveLookup && productQuery.isLoading ? (
          renderSkeleton()
        ) : effectiveLookup?.status === "notFound" ? (
          <EmptyState
            headingLevel={1}
            icon={<TriangleAlert />}
            title={t("notFound.title")}
            description={t("notFound.description")}
            action={
              <Button asChild variant="primary">
                <Link href="/#exams">{t("backToCatalog")}</Link>
              </Button>
            }
          />
        ) : effectiveLookup?.status === "gone" ? (
          <div className="flex flex-col items-center gap-3 px-6 py-12 text-center">
            <div
              aria-hidden
              className="grid size-12 place-items-center rounded-full bg-primary-50 text-primary-text [&_svg]:size-6"
            >
              <TriangleAlert />
            </div>
            <h1 className="text-title font-semibold text-text">{t("gone.title")}</h1>
            <p className="max-w-sm text-body text-text-muted">
              {t.rich("gone.description", {
                name: effectiveLookup.product.name,
                examContent: (chunks) => <span data-exam-content>{chunks}</span>,
              })}
            </p>
            <Button asChild variant="primary">
              <Link href="/#exams">{t("backToCatalog")}</Link>
            </Button>
          </div>
        ) : !product ? (
          <EmptyState
            headingLevel={1}
            icon={<TriangleAlert />}
            title={t("loadError.title")}
            description={t("loadError.description")}
            action={
              <Button type="button" variant="secondary" onClick={() => void productQuery.refetch()}>
                {common("retry")}
              </Button>
            }
          />
        ) : (
          <>
            <header className="flex flex-col gap-1.5">
              <h1 className="text-h2 font-bold tracking-tight text-text">{t("heading")}</h1>
              <p className="text-body text-text-secondary">{t("subtitle")}</p>
            </header>

            {accountChanged && (
              <section
                role="alert"
                className="flex flex-wrap items-center justify-between gap-4 rounded-xl border border-danger/30 bg-danger-bg p-5"
              >
                <div className="flex min-w-0 flex-1 basis-72 items-start gap-3">
                  <TriangleAlert aria-hidden className="mt-0.5 size-5 shrink-0 text-danger" />
                  <div className="flex flex-col gap-1">
                    <h2 className="text-title font-semibold text-danger">{t("account.changedTitle")}</h2>
                    <p className="text-body text-text-secondary">
                      {t("account.changedDescription", {
                        previous: accountChanged.previousUsername,
                        current: accountChanged.current.username,
                      })}
                    </p>
                  </div>
                </div>
                <Button
                  type="button"
                  variant="primary"
                  onClick={() => void handleAcknowledgeNewAccount()}
                  className="h-auto min-h-11 max-w-full py-2.5 text-center whitespace-normal"
                >
                  {t("account.continueAsCurrent", { username: accountChanged.current.username })}
                </Button>
              </section>
            )}

            {showPriceChangedBanner && activeQuote && (
              <section
                role="status"
                className="flex items-start gap-3 rounded-xl border border-warning bg-warning-bg p-4"
              >
                <TriangleAlert aria-hidden className="mt-0.5 size-5 shrink-0 text-warning" />
                <div className="flex flex-col gap-1">
                  <h2 className="text-label font-semibold text-warning">{t("priceChanged.title")}</h2>
                  <p className="text-body text-text-secondary">
                    {displaySeenPrice !== undefined && displaySeenPrice !== activeQuote.finalPrice
                      ? t("priceChanged.descriptionWithSeen", {
                          latest: storefront("price", { amount: activeQuote.finalPrice }),
                          seen: storefront("price", { amount: displaySeenPrice }),
                        })
                      : t("priceChanged.description", {
                          latest: storefront("price", { amount: activeQuote.finalPrice }),
                        })}
                  </p>
                </div>
              </section>
            )}

            {renderAccessBanner()}

            <div className="grid grid-cols-1 items-start gap-6 lg:grid-cols-[minmax(0,1.15fr)_minmax(0,0.85fr)]">
              <div className="flex flex-col gap-5">
                {displaySession && (
                  <section
                    aria-labelledby="checkout-account-heading"
                    className="flex flex-col gap-3 rounded-xl border border-border bg-surface p-5"
                  >
                    <div className="flex flex-wrap items-center justify-between gap-2">
                      <h2
                        id="checkout-account-heading"
                        className="inline-flex items-center gap-2 text-title font-semibold text-text"
                      >
                        <UserCheck aria-hidden className="size-4 text-primary-text" />
                        {t("account.title")}
                      </h2>
                      <Link
                        href={withCallbackUrl("/login", currentCheckoutPath)}
                        className={cn(
                          "inline-flex items-center rounded-sm text-caption font-semibold text-primary-text hover:underline",
                          focusRing,
                          coarseTextHitArea,
                        )}
                      >
                        {t("account.switchAccount")}
                      </Link>
                    </div>
                    <div className="flex flex-col gap-0.5 rounded-lg bg-page px-3.5 py-2.5">
                      <span className="font-semibold text-text">
                        {displaySession.fullName?.trim() || displaySession.username}
                      </span>
                      <span className="text-caption text-text-secondary">{displaySession.username}</span>
                    </div>
                    <p className="text-body text-text-secondary">
                      {t("account.grantNotice", { username: displaySession.username })}
                    </p>
                  </section>
                )}

                <section
                  aria-labelledby="checkout-product-heading"
                  className="flex flex-col gap-4 rounded-xl border border-border bg-surface p-5"
                >
                  <div className="flex flex-wrap items-center gap-2">
                    <span className="inline-flex h-6 items-center rounded-full bg-subtle px-2.5 text-caption font-medium text-text-secondary">
                      {pickName(product.examKind, locale) || product.examKind.code}
                    </span>
                    <FormatChip format={product.format} scopeLabel={product.scopeLabel} />
                    {product.buildMode === REVIEW_BUILD_MODE && (
                      <span className="inline-flex h-6 items-center rounded-full bg-info-bg px-2.5 text-caption font-semibold text-info">
                        {storefront("review")}
                      </span>
                    )}
                    <StatusChip tone="neutral" className="ml-auto">
                      {t("product.version", { version: activeQuote?.versionNo ?? product.versionNo })}
                    </StatusChip>
                  </div>

                  <div className="flex flex-col gap-1">
                    <span className="text-caption font-medium text-text-muted">{t("product.sectionTitle")}</span>
                    <h2 id="checkout-product-heading" data-exam-content className="text-h3 font-bold text-text">
                      {product.name}
                    </h2>
                  </div>

                  <div className="flex flex-wrap items-center gap-x-4 gap-y-1 text-body text-text-secondary">
                    <span>
                      {t("product.attemptsGranted", { count: activeQuote?.attemptsGranted ?? product.attemptsGranted })}
                    </span>
                    {rangeText(product.questionCount) && (
                      <span>
                        {storefront("units.questions", {
                          value: rangeText(product.questionCount)!,
                          count: product.questionCount?.max ?? 0,
                        })}
                      </span>
                    )}
                    {rangeText(product.durationMinutes) && (
                      <span>
                        {storefront("units.minutes", {
                          value: rangeText(product.durationMinutes)!,
                          count: product.durationMinutes?.max ?? 0,
                        })}
                      </span>
                    )}
                  </div>

                  <div className="flex flex-wrap gap-x-5 gap-y-1.5 border-t border-border pt-3 text-caption text-text-secondary">
                    {product.resultView && (
                      <span>
                        {detailRights.has(`resultViews.${product.resultView}`)
                          ? detailRights(`resultViews.${product.resultView}`)
                          : product.resultView}
                      </span>
                    )}
                    <span className="inline-flex items-center gap-1">
                      {product.benefits.aiExplanation ? (
                        <Check aria-hidden className="size-3.5 text-primary-text" />
                      ) : (
                        <X aria-hidden className="size-3.5 text-text-muted" />
                      )}
                      {detailRights("ai")}: {product.benefits.aiExplanation ? detailRights("yes") : detailRights("no")}
                    </span>
                    <span className="inline-flex items-center gap-1">
                      {product.benefits.strengthWeakness ? (
                        <Check aria-hidden className="size-3.5 text-primary-text" />
                      ) : (
                        <X aria-hidden className="size-3.5 text-text-muted" />
                      )}
                      {detailRights("strengths")}:{" "}
                      {product.benefits.strengthWeakness ? detailRights("yes") : detailRights("no")}
                    </span>
                    <span className="inline-flex items-center gap-1">
                      {product.benefits.practice ? (
                        <Check aria-hidden className="size-3.5 text-primary-text" />
                      ) : (
                        <X aria-hidden className="size-3.5 text-text-muted" />
                      )}
                      {detailRights("practice")}: {product.benefits.practice ? detailRights("yes") : detailRights("no")}
                    </span>
                  </div>
                  {product.benefits.note && (
                    <p data-exam-content className="text-caption text-text-secondary">
                      {product.benefits.note}
                    </p>
                  )}
                </section>

                <section
                  aria-labelledby="checkout-refund-heading"
                  className="flex flex-col gap-1.5 rounded-xl border border-border bg-surface p-5 text-body text-text-secondary"
                >
                  <h2 id="checkout-refund-heading" className="inline-flex items-center gap-2 text-label font-semibold text-text">
                    <ShieldCheck aria-hidden className="size-4 text-primary-text" />
                    {t("refund.title")}
                  </h2>
                  <p>{t("refund.body")}</p>
                  <p className="text-caption text-text-muted">
                    {t("refund.supportLabel")} {supportSummary || support("pending")}
                  </p>
                </section>
              </div>

              <div ref={paySectionRef} className="flex flex-col gap-5">
                {activeQuote && (
                  <VoucherBox
                    key={`${code}:${acknowledgedSession?.id ?? ""}:${voucherResetVersion}`}
                    productCode={code}
                    seenPrice={seenPrice}
                    seenVersionNo={seenVersionNo}
                    quote={activeQuote}
                    requestQuote={requestQuote}
                    disabled={isBlockedByChecking || Boolean(accountChanged) || submitting}
                    onProductGone={(gone) => setGoneProduct(gone)}
                    onUnauthorized={() => void sessionQuery.refetch()}
                    onQuoteApplied={(nextQuote) => {
                      setOverrideQuote(nextQuote);
                      setSubmitError(null);
                      idempotencyKeyRef.current = null;
                    }}
                  />
                )}

                <section
                  aria-labelledby="checkout-summary-heading"
                  className="flex flex-col gap-4 rounded-xl border border-border bg-surface p-6 tabular-nums"
                >
                  <h2 id="checkout-summary-heading" className="text-h3 font-bold text-text">
                    {t("summary.title")}
                  </h2>

                  {baseQuoteQuery.isLoading && !activeQuote ? (
                    <div role="status" aria-busy="true" aria-label={common("loading")} className="flex flex-col gap-3">
                      <Skeleton className="h-5 w-full" />
                      <Skeleton className="h-5 w-3/4" />
                      <Skeleton className="h-11 w-full rounded-lg" />
                    </div>
                  ) : baseQuoteQuery.isError && !activeQuote ? (
                    <div className="flex flex-col gap-3">
                      <p role="alert" className="text-body text-danger">
                        {t("loadError.description")}
                      </p>
                      <Button type="button" variant="secondary" onClick={() => void baseQuoteQuery.refetch()}>
                        {common("retry")}
                      </Button>
                    </div>
                  ) : activeQuote ? (
                    <>
                      <dl className="flex flex-col gap-2.5 text-body">
                        <div className="flex items-baseline justify-between gap-2">
                          <dt className="text-text-secondary">{t("summary.listPrice")}</dt>
                          <dd className="font-medium text-text">
                            {storefront("price", { amount: activeQuote.listPrice })}
                          </dd>
                        </div>

                        {activeQuote.promotion && activeQuote.promotion.discountVnd > 0 && (
                          <div className="flex items-baseline justify-between gap-2 text-primary-text">
                            <dt>
                              {activeQuote.promotion.name
                                ? t.rich("summary.promotion", {
                                    name: activeQuote.promotion.name,
                                    examContent: (chunks) => <span data-exam-content>{chunks}</span>,
                                  })
                                : t("summary.promotionFallback")}
                            </dt>
                            <dd className="font-medium">
                              -{storefront("price", { amount: activeQuote.promotion.discountVnd })}
                            </dd>
                          </div>
                        )}

                        {activeQuote.voucher && activeQuote.voucher.discountVnd > 0 && (
                          <div className="flex items-baseline justify-between gap-2 text-primary-text">
                            <dt>{t("summary.voucherDiscount", { code: activeQuote.voucher.code })}</dt>
                            <dd className="font-medium">
                              -{storefront("price", { amount: activeQuote.voucher.discountVnd })}
                            </dd>
                          </div>
                        )}

                        <div className="mt-1 flex items-baseline justify-between gap-2 border-t border-border pt-3">
                          <dt className="font-semibold text-text">{t("summary.totalDue")}</dt>
                          <dd className="text-h3 font-bold text-primary-text">
                            {activeQuote.finalPrice === 0 && activeQuote.voucher
                              ? t("summary.freeVoucher")
                              : storefront("price", { amount: activeQuote.finalPrice })}
                          </dd>
                        </div>
                      </dl>

                      {submitError && (
                        <p role="alert" className="flex items-start gap-2 text-caption font-medium text-danger">
                          <TriangleAlert aria-hidden className="mt-0.5 size-4 shrink-0" />
                          <span>{renderSubmitErrorText(submitError)}</span>
                        </p>
                      )}

                      <Button
                        id="checkout-buy-button"
                        ref={payButtonRef}
                        type="button"
                        variant={activeQuote.finalPrice === 0 ? "exam" : "primary"}
                        size="lg"
                        loading={submitting}
                        disabled={
                          accessUnavailable ||
                          isBlockedByChecking ||
                          Boolean(accountChanged) ||
                          !acknowledgedSession
                        }
                        onClick={() => void handleCreateOrder()}
                        className="w-full"
                      >
                        {activeQuote.finalPrice === 0
                          ? activeQuote.voucher
                            ? t("summary.ctaFree")
                            : t("summary.ctaFreeNoVoucher")
                          : t("summary.ctaVnpay")}
                      </Button>
                    </>
                  ) : null}
                </section>
              </div>
            </div>
          </>
        )}
      </main>
    </div>
  );
}
