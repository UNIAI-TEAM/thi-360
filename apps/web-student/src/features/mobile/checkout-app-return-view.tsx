"use client";

import { useQuery } from "@tanstack/react-query";
import { BrandWordmark, Button, LocaleSwitcher, Skeleton, StatusChip, ThemeToggle } from "@thi360/ui";
import { CheckCircle2, Clock, TriangleAlert, XCircle } from "lucide-react";
import { useTranslations } from "next-intl";
import { useSearchParams } from "next/navigation";
import { useMemo, useSyncExternalStore } from "react";
import {
  VNPAY_RETURN_QUERY_KEY,
  verifyVnpayReturn,
  type VnpayReturnResponse,
  type VnpayReturnVerifier,
} from "@/features/checkout/api";
import { mapVnpayResponseCode } from "@/features/orders/format";
import { ApiStatusError } from "@/lib/api/client";

const APP_RETURN_PATH = "/checkout/result/app";

export type AppReturnState =
  | { readonly kind: "verifying" }
  | {
      readonly kind: "verified";
      readonly orderCode: string;
      readonly paymentState: "SUCCEEDED" | "FAILED" | (string & {});
      readonly responseCode?: string;
    }
  | { readonly kind: "invalidSignature" }
  | { readonly kind: "orderNotFound" }
  | { readonly kind: "loadError" };

function extractVnpParams(searchParams: URLSearchParams): Record<string, string> {
  const result: Record<string, string> = {};
  searchParams.forEach((value, key) => {
    if (key.startsWith("vnp_")) {
      result[key] = value;
    }
  });
  return result;
}

function deriveAppReturnState({
  hasVnpParams,
  isLoading,
  isError,
  error,
  data,
  responseCode,
}: {
  hasVnpParams: boolean;
  isLoading: boolean;
  isError: boolean;
  error: unknown;
  data: VnpayReturnResponse | undefined;
  responseCode: string | undefined;
}): AppReturnState {
  if (!hasVnpParams) {
    return { kind: "invalidSignature" };
  }
  if (isLoading) {
    return { kind: "verifying" };
  }
  if (isError) {
    if (error instanceof ApiStatusError) {
      if (error.code === "VNPAY_SIGNATURE_INVALID" || error.status === 422 || error.status === 400) {
        return { kind: "invalidSignature" };
      }
      if (error.status === 404 || error.code === "ORDER_NOT_FOUND") {
        return { kind: "orderNotFound" };
      }
    }
    return { kind: "loadError" };
  }
  if (data) {
    return {
      kind: "verified",
      orderCode: data.orderCode,
      paymentState: data.paymentState,
      responseCode,
    };
  }
  return { kind: "verifying" };
}

const emptySubscribe = () => () => {};

function useCurrentAppReturnUrl(searchParams: URLSearchParams, overrideUrl?: string): string {
  const qs = searchParams.toString();
  const serverSnapshot = qs ? `${APP_RETURN_PATH}?${qs}` : APP_RETURN_PATH;

  return useSyncExternalStore(
    emptySubscribe,
    () => {
      if (overrideUrl) return overrideUrl;
      if (typeof window === "undefined") return serverSnapshot;
      // Preserve exact raw query parameter order when opened on /checkout/result/app.
      const rawSearch =
        window.location.pathname === APP_RETURN_PATH && window.location.search
          ? window.location.search
          : qs
            ? `?${qs}`
            : "";
      return `${window.location.origin}${APP_RETURN_PATH}${rawSearch}`;
    },
    () => overrideUrl ?? serverSnapshot,
  );
}

export function CheckoutAppReturnView({
  verifyReturn = verifyVnpayReturn,
  currentUrl,
}: {
  verifyReturn?: VnpayReturnVerifier;
  currentUrl?: string;
}) {
  const t = useTranslations("checkout.result");
  const ordersT = useTranslations("orders");
  const common = useTranslations("common");
  const searchParams = useSearchParams();

  const vnpParams = useMemo(() => extractVnpParams(searchParams), [searchParams]);
  const hasVnpParams = Object.keys(vnpParams).length > 0;
  const serializedVnp = useMemo(
    () =>
      Object.entries(vnpParams)
        .map(([k, v]) => `${k}=${v}`)
        .join("&"),
    [vnpParams],
  );
  const vnpResponseCode = searchParams.get("vnp_ResponseCode") ?? undefined;
  const openAppHref = useCurrentAppReturnUrl(searchParams, currentUrl);

  const verifyQuery = useQuery({
    queryKey: VNPAY_RETURN_QUERY_KEY(`app:${serializedVnp}`),
    queryFn: () => verifyReturn(vnpParams),
    enabled: hasVnpParams,
    retry: false,
    staleTime: Infinity,
  });

  const state = deriveAppReturnState({
    hasVnpParams,
    isLoading: verifyQuery.isLoading,
    isError: verifyQuery.isError,
    error: verifyQuery.error,
    data: verifyQuery.data,
    responseCode: vnpResponseCode,
  });

  function renderCard() {
    switch (state.kind) {
      case "verifying":
        return (
          <div
            role="status"
            aria-busy="true"
            aria-label={common("loading")}
            className="flex flex-col gap-4 rounded-xl border border-border bg-surface p-6"
          >
            <Skeleton className="h-8 w-56" />
            <Skeleton className="h-5 w-full" />
            <Skeleton className="h-10 w-44" />
          </div>
        );

      case "verified": {
        const orderLabel = ordersT("detail.heading", { code: state.orderCode });

        if (state.paymentState === "SUCCEEDED") {
          return (
            <section className="flex flex-col gap-5 rounded-xl border border-border-primary bg-surface p-6">
              <div className="flex items-start gap-3">
                <CheckCircle2 aria-hidden className="mt-0.5 size-6 shrink-0 text-primary-text" />
                <div className="flex min-w-0 flex-col gap-1.5">
                  <h1 className="text-h2 font-bold text-text">{t("guestSuccessHeading")}</h1>
                  <p className="text-body font-medium text-text tabular-nums">{orderLabel}</p>
                  <p className="text-body text-text-secondary">{t("app.returnHint")}</p>
                </div>
              </div>
              <div className="flex flex-wrap gap-3">
                <Button asChild variant="exam">
                  <a href={openAppHref}>{t("app.openApp")}</a>
                </Button>
              </div>
            </section>
          );
        }

        if (state.paymentState === "FAILED") {
          const reason = ordersT(`vnpayReasons.${mapVnpayResponseCode(state.responseCode)}`);
          return (
            <section role="alert" className="flex flex-col gap-5 rounded-xl border border-danger/30 bg-surface p-6">
              <div className="flex items-start gap-3">
                <XCircle aria-hidden className="mt-0.5 size-6 shrink-0 text-danger" />
                <div className="flex min-w-0 flex-col gap-1.5">
                  <h1 className="text-h2 font-bold text-text">{t("guestFailedHeading")}</h1>
                  <p className="text-body font-medium text-text tabular-nums">{orderLabel}</p>
                  <p className="text-body font-medium text-danger">{reason}</p>
                  <p className="text-body text-text-secondary">{t("app.returnHint")}</p>
                </div>
              </div>
              <div className="flex flex-wrap gap-3">
                <Button asChild variant="primary">
                  <a href={openAppHref}>{t("app.openApp")}</a>
                </Button>
              </div>
            </section>
          );
        }

        return (
          <section className="flex flex-col gap-5 rounded-xl border border-warning bg-surface p-6">
            <div role="status" className="flex items-start gap-3">
              <Clock aria-hidden className="mt-0.5 size-6 shrink-0 text-warning" />
              <div className="flex min-w-0 flex-col gap-1.5">
                <div className="flex flex-wrap items-center gap-2">
                  <h1 className="text-h2 font-bold text-text">{t("guestCheckingHeading")}</h1>
                  <StatusChip tone="warning">{t("checkingBadge")}</StatusChip>
                </div>
                <p className="text-body font-medium text-text tabular-nums">{orderLabel}</p>
                <p className="text-body text-text-secondary">{t("app.returnHint")}</p>
              </div>
            </div>
            <div className="flex flex-wrap gap-3">
              <Button asChild variant="primary">
                <a href={openAppHref}>{t("app.openApp")}</a>
              </Button>
            </div>
          </section>
        );
      }

      case "invalidSignature":
        return (
          <section role="alert" className="flex flex-col gap-5 rounded-xl border border-danger/30 bg-surface p-6">
            <div className="flex items-start gap-3">
              <XCircle aria-hidden className="mt-0.5 size-6 shrink-0 text-danger" />
              <div className="flex min-w-0 flex-col gap-1.5">
                <h1 className="text-h2 font-bold text-text">{t("invalidSignatureHeading")}</h1>
                <p className="text-body text-text-secondary">{t("app.returnHint")}</p>
              </div>
            </div>
            <div className="flex flex-wrap gap-3">
              <Button asChild variant="primary">
                <a href={openAppHref}>{t("app.openApp")}</a>
              </Button>
            </div>
          </section>
        );

      case "orderNotFound":
        return (
          <section role="alert" className="flex flex-col gap-5 rounded-xl border border-border bg-surface p-6">
            <div className="flex items-start gap-3">
              <TriangleAlert aria-hidden className="mt-0.5 size-6 shrink-0 text-warning" />
              <div className="flex min-w-0 flex-col gap-1.5">
                <h1 className="text-h2 font-bold text-text">{ordersT("notFound.title")}</h1>
                <p className="text-body text-text-secondary">{t("app.returnHint")}</p>
              </div>
            </div>
            <div className="flex flex-wrap gap-3">
              <Button asChild variant="primary">
                <a href={openAppHref}>{t("app.openApp")}</a>
              </Button>
            </div>
          </section>
        );

      case "loadError":
        return (
          <section role="alert" className="flex flex-col gap-5 rounded-xl border border-danger/30 bg-surface p-6">
            <div className="flex items-start gap-3">
              <TriangleAlert aria-hidden className="mt-0.5 size-6 shrink-0 text-danger" />
              <div className="flex min-w-0 flex-col gap-1.5">
                <h1 className="text-h2 font-bold text-text">{t("loadErrorHeading")}</h1>
                <p className="text-body text-text-secondary">{t("loadErrorBody")}</p>
                <p className="text-body text-text-secondary">{t("app.returnHint")}</p>
              </div>
            </div>
            <div className="flex flex-wrap gap-3">
              <Button
                type="button"
                variant="secondary"
                loading={verifyQuery.isFetching}
                onClick={() => void verifyQuery.refetch()}
              >
                {common("retry")}
              </Button>
              <Button asChild variant="primary">
                <a href={openAppHref}>{t("app.openApp")}</a>
              </Button>
            </div>
          </section>
        );
    }
  }

  return (
    <div className="min-h-screen bg-page text-text">
      <header className="border-b border-border bg-surface">
        <div className="mx-auto flex w-full max-w-[760px] items-center justify-between gap-3 px-4 py-3 sm:px-6">
          <BrandWordmark alt={common("appName")} className="h-8" />
          <div className="flex items-center gap-2">
            <ThemeToggle />
            <LocaleSwitcher />
          </div>
        </div>
      </header>
      <main className="mx-auto flex w-full max-w-[760px] flex-col gap-6 px-4 py-8 sm:px-6">{renderCard()}</main>
    </div>
  );
}
