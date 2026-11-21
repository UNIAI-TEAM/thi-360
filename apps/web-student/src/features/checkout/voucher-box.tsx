"use client";

import { Button, Input } from "@thi360/ui";
import { Check, Info, Tag, TriangleAlert, X } from "lucide-react";
import { useTranslations } from "next-intl";
import { type FormEvent, useEffect, useRef, useState } from "react";
import { ApiStatusError } from "@/lib/api/client";
import { coarseTextHitArea, touchFieldText } from "@/lib/styles";
import {
  fetchPriceQuote,
  type PriceQuote,
  type PriceQuoteAction,
  ProductGoneError,
  ProductUnavailableError,
} from "./api";

export type VoucherErrorKey =
  | "RATE_LIMITED"
  | "VOUCHER_EXHAUSTED"
  | "VOUCHER_EXPIRED"
  | "VOUCHER_INVALID"
  | "VOUCHER_NOT_APPLICABLE"
  | "VOUCHER_NOT_ELIGIBLE"
  | "VOUCHER_USER_LIMIT"
  | "generic";

const KNOWN_VOUCHER_ERRORS = new Set<string>([
  "RATE_LIMITED",
  "VOUCHER_EXHAUSTED",
  "VOUCHER_EXPIRED",
  "VOUCHER_INVALID",
  "VOUCHER_NOT_APPLICABLE",
  "VOUCHER_NOT_ELIGIBLE",
  "VOUCHER_USER_LIMIT",
]);

export function mapVoucherErrorKey(error: unknown): VoucherErrorKey {
  if (error instanceof ApiStatusError) {
    if (error.status === 429 || error.code === "RATE_LIMITED") {
      return "RATE_LIMITED";
    }
    if (error.code && KNOWN_VOUCHER_ERRORS.has(error.code)) {
      return error.code as VoucherErrorKey;
    }
  }
  return "generic";
}

type VoucherFeedback =
  | { kind: "empty" }
  | { kind: "error"; errorKey: VoucherErrorKey }
  | { kind: "notBetter"; code: string }
  | { kind: "applied"; variant: "applied" | "combined" | "replacedPromo"; code: string; discountVnd: number };

export function VoucherBox({
  productCode,
  isBundle = false,
  seenPrice,
  seenVersionNo,
  quote,
  requestQuote = fetchPriceQuote,
  onQuoteApplied,
  onProductGone,
  onProductUnavailable,
  onUnauthorized,
  disabled = false,
}: {
  productCode: string;
  isBundle?: boolean;
  seenPrice?: number;
  seenVersionNo?: number;
  quote: PriceQuote;
  requestQuote?: PriceQuoteAction;
  onQuoteApplied: (nextQuote: PriceQuote, meta: { action: "apply" | "remove"; submittedCode?: string }) => void;
  onProductGone?: (product: { code: string; name: string }) => void;
  onProductUnavailable?: () => void;
  onUnauthorized?: () => Promise<boolean | void> | void;
  disabled?: boolean;
}) {
  const t = useTranslations("checkout.voucher");
  const storefront = useTranslations("storefront");

  const [codeInput, setCodeInput] = useState(quote.voucher?.code ?? "");
  const [submitting, setSubmitting] = useState(false);
  const [feedback, setFeedback] = useState<VoucherFeedback | null>(null);

  const inputRef = useRef<HTMLInputElement>(null);
  const shouldFocusInputRef = useRef(false);

  const isDisabled = disabled || submitting;
  const hasError = feedback?.kind === "empty" || feedback?.kind === "error";

  useEffect(() => {
    if (!submitting && shouldFocusInputRef.current) {
      shouldFocusInputRef.current = false;
      inputRef.current?.focus();
    }
  }, [submitting]);

  async function handleApply(event: FormEvent) {
    event.preventDefault();
    if (isDisabled) return;

    const trimmed = codeInput.trim();
    if (!trimmed) {
      setFeedback({ kind: "empty" });
      inputRef.current?.focus();
      return;
    }

    setSubmitting(true);
    setFeedback(null);
    try {
      const nextQuote = await requestQuote({
        code: productCode,
        voucherCode: trimmed,
        ...(seenVersionNo !== undefined ? { seenVersionNo } : {}),
      });

      onQuoteApplied(nextQuote, { action: "apply", submittedCode: trimmed });

      if (nextQuote.voucherNotBetter) {
        setFeedback({ kind: "notBetter", code: trimmed });
      } else if (nextQuote.voucher) {
        setCodeInput(nextQuote.voucher.code);
        const variant =
          nextQuote.discountApplied === "COMBINED"
            ? "combined"
            : quote.promotion && !nextQuote.promotion
              ? "replacedPromo"
              : "applied";
        setFeedback({
          kind: "applied",
          variant,
          code: nextQuote.voucher.code,
          discountVnd: nextQuote.voucher.discountVnd,
        });
      }
    } catch (error) {
      if (error instanceof ProductGoneError) {
        onProductGone?.(error.product);
        return;
      }
      if (
        error instanceof ProductUnavailableError ||
        (error instanceof ApiStatusError && error.status === 409 && error.code === "PRODUCT_UNAVAILABLE")
      ) {
        onProductUnavailable?.();
        return;
      }
      if (error instanceof ApiStatusError && error.status === 401) {
        const sessionStillActive = await onUnauthorized?.();
        if (sessionStillActive) {
          shouldFocusInputRef.current = true;
          setFeedback({ kind: "error", errorKey: "generic" });
        }
        return;
      }
      shouldFocusInputRef.current = true;
      setFeedback({ kind: "error", errorKey: mapVoucherErrorKey(error) });
    } finally {
      setSubmitting(false);
    }
  }

  async function handleRemove() {
    if (isDisabled) return;
    setSubmitting(true);
    setFeedback(null);
    try {
      const nextQuote = await requestQuote({
        code: productCode,
        ...(seenPrice !== undefined ? { seenPrice } : {}),
        ...(seenVersionNo !== undefined ? { seenVersionNo } : {}),
      });
      setCodeInput("");
      shouldFocusInputRef.current = true;
      onQuoteApplied(nextQuote, { action: "remove" });
    } catch (error) {
      if (error instanceof ProductGoneError) {
        onProductGone?.(error.product);
        return;
      }
      if (
        error instanceof ProductUnavailableError ||
        (error instanceof ApiStatusError && error.status === 409 && error.code === "PRODUCT_UNAVAILABLE")
      ) {
        onProductUnavailable?.();
        return;
      }
      if (error instanceof ApiStatusError && error.status === 401) {
        const sessionStillActive = await onUnauthorized?.();
        if (sessionStillActive) {
          shouldFocusInputRef.current = true;
          setFeedback({ kind: "error", errorKey: "generic" });
        }
        return;
      }
      shouldFocusInputRef.current = true;
      setFeedback({ kind: "error", errorKey: mapVoucherErrorKey(error) });
    } finally {
      setSubmitting(false);
    }
  }

  function renderErrorText(errorKey: VoucherErrorKey): string {
    if (errorKey === "VOUCHER_NOT_APPLICABLE" && isBundle) {
      return t("errors.VOUCHER_NOT_APPLICABLE_BUNDLE");
    }
    return t(`errors.${errorKey}`);
  }

  return (
    <section
      aria-labelledby="checkout-voucher-heading"
      className="flex flex-col gap-3 rounded-xl border border-border bg-surface p-5"
    >
      <div className="flex items-center justify-between gap-2">
        <h2 id="checkout-voucher-heading" className="inline-flex items-center gap-2 text-title font-semibold text-text">
          <Tag aria-hidden className="size-4 text-primary-text" />
          {t("sectionTitle")}
        </h2>
        {quote.voucher && (
          <Button
            type="button"
            variant="outline"
            size="sm"
            disabled={isDisabled}
            onClick={() => void handleRemove()}
            className={cnLocalRemoveButtonClass()}
          >
            <X aria-hidden className="size-3.5" />
            {t("remove")}
          </Button>
        )}
      </div>

      <form onSubmit={(event) => void handleApply(event)} className="flex flex-wrap items-end gap-2">
        <div className="flex min-w-0 flex-1 basis-52 flex-col gap-1.5">
          <label htmlFor="voucher-code-input" className="text-caption font-medium text-text-secondary">
            {t("label")}
          </label>
          <Input
            ref={inputRef}
            id="voucher-code-input"
            name="voucherCode"
            type="text"
            size="lg"
            value={codeInput}
            disabled={isDisabled}
            autoComplete="off"
            placeholder={t("placeholder")}
            aria-invalid={hasError || undefined}
            aria-describedby={hasError ? "voucher-error-msg" : undefined}
            className={touchFieldText}
            onChange={(event) => {
              setCodeInput(event.target.value);
              if (feedback?.kind === "empty" || feedback?.kind === "error") {
                setFeedback(null);
              }
            }}
          />
        </div>
        <Button type="submit" variant="secondary" size="lg" loading={submitting} disabled={disabled} className="shrink-0">
          {t("apply")}
        </Button>
      </form>

      {feedback?.kind === "empty" && (
        <p id="voucher-error-msg" role="alert" className="flex items-start gap-2 text-caption font-medium text-danger">
          <TriangleAlert aria-hidden className="mt-0.5 size-4 shrink-0" />
          <span>{t("emptyInput")}</span>
        </p>
      )}

      {feedback?.kind === "error" && (
        <p id="voucher-error-msg" role="alert" className="flex items-start gap-2 text-caption font-medium text-danger">
          <TriangleAlert aria-hidden className="mt-0.5 size-4 shrink-0" />
          <span>{renderErrorText(feedback.errorKey)}</span>
        </p>
      )}

      {feedback?.kind === "notBetter" && (
        <p
          role="status"
          className="flex items-start gap-2 rounded-lg border border-info/30 bg-info-bg px-3 py-2 text-caption text-text-secondary"
        >
          <Info aria-hidden className="mt-0.5 size-4 shrink-0 text-info" />
          <span>{t("notBetter", { code: feedback.code })}</span>
        </p>
      )}

      {feedback?.kind === "applied" && (
        <p
          role="status"
          className="flex items-start gap-2 rounded-lg border border-border-primary bg-primary-50 px-3 py-2 text-caption font-medium text-primary-text"
        >
          <Check aria-hidden className="mt-0.5 size-4 shrink-0" />
          <span>
            {feedback.variant === "combined"
              ? t("appliedCombined", {
                  code: feedback.code,
                  discount: storefront("price", { amount: feedback.discountVnd }),
                })
              : feedback.variant === "replacedPromo"
                ? t("appliedReplacedPromo", {
                    code: feedback.code,
                    discount: storefront("price", { amount: feedback.discountVnd }),
                  })
                : t("applied", {
                    code: feedback.code,
                    discount: storefront("price", { amount: feedback.discountVnd }),
                  })}
          </span>
        </p>
      )}

      {!feedback && quote.voucher && (
        <p
          role="status"
          className="flex items-start gap-2 rounded-lg border border-border-primary bg-primary-50 px-3 py-2 text-caption font-medium text-primary-text"
        >
          <Check aria-hidden className="mt-0.5 size-4 shrink-0" />
          <span>
            {quote.discountApplied === "COMBINED"
              ? t("appliedCombined", {
                  code: quote.voucher.code,
                  discount: storefront("price", { amount: quote.voucher.discountVnd }),
                })
              : t("applied", {
                  code: quote.voucher.code,
                  discount: storefront("price", { amount: quote.voucher.discountVnd }),
                })}
          </span>
        </p>
      )}
    </section>
  );
}

function cnLocalRemoveButtonClass() {
  return `h-auto min-h-8 px-2.5 py-1 text-caption text-text-secondary hover:text-text ${coarseTextHitArea}`;
}
