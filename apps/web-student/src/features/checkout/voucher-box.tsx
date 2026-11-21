"use client";

import { Button, cn, focusRing } from "@thi360/ui";
import { Check, Info, Tag, TriangleAlert, X } from "lucide-react";
import { useTranslations } from "next-intl";
import { type FormEvent, useState } from "react";
import { ApiStatusError } from "@/lib/api/client";
import {
  fetchPriceQuote,
  type PriceQuote,
  type PriceQuoteAction,
  ProductGoneError,
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
  seenPrice,
  seenVersionNo,
  quote,
  requestQuote = fetchPriceQuote,
  onQuoteApplied,
  onProductGone,
  disabled = false,
}: {
  productCode: string;
  seenPrice?: number;
  seenVersionNo?: number;
  quote: PriceQuote;
  requestQuote?: PriceQuoteAction;
  onQuoteApplied: (nextQuote: PriceQuote, meta: { action: "apply" | "remove"; submittedCode?: string }) => void;
  onProductGone?: (product: { code: string; name: string }) => void;
  disabled?: boolean;
}) {
  const t = useTranslations("checkout.voucher");
  const storefront = useTranslations("storefront");

  const [codeInput, setCodeInput] = useState(quote.voucher?.code ?? "");
  const [submitting, setSubmitting] = useState(false);
  const [feedback, setFeedback] = useState<VoucherFeedback | null>(null);

  const isDisabled = disabled || submitting;

  async function handleApply(event: FormEvent) {
    event.preventDefault();
    if (isDisabled) return;

    const trimmed = codeInput.trim();
    if (!trimmed) {
      setFeedback({ kind: "empty" });
      return;
    }

    setSubmitting(true);
    setFeedback(null);
    try {
      const nextQuote = await requestQuote({
        code: productCode,
        voucherCode: trimmed,
        ...(seenPrice !== undefined ? { seenPrice } : {}),
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
      onQuoteApplied(nextQuote, { action: "remove" });
    } catch (error) {
      if (error instanceof ProductGoneError) {
        onProductGone?.(error.product);
        return;
      }
      setFeedback({ kind: "error", errorKey: mapVoucherErrorKey(error) });
    } finally {
      setSubmitting(false);
    }
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
            className="h-auto px-2 py-1 text-caption text-text-secondary hover:text-text"
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
          <input
            id="voucher-code-input"
            name="voucherCode"
            type="text"
            value={codeInput}
            disabled={isDisabled}
            autoComplete="off"
            placeholder={t("placeholder")}
            onChange={(event) => {
              setCodeInput(event.target.value);
              if (feedback?.kind === "empty" || feedback?.kind === "error") {
                setFeedback(null);
              }
            }}
            className={cn(
              "h-10 w-full rounded-lg border border-border-input bg-surface px-3 text-body text-text placeholder:text-text-muted disabled:cursor-not-allowed disabled:opacity-60",
              focusRing,
            )}
          />
        </div>
        <Button type="submit" variant="secondary" loading={submitting} disabled={disabled} className="shrink-0">
          {submitting ? t("checking") : t("apply")}
        </Button>
      </form>

      {feedback?.kind === "empty" && (
        <p role="alert" className="flex items-start gap-2 text-caption font-medium text-danger">
          <TriangleAlert aria-hidden className="mt-0.5 size-4 shrink-0" />
          <span>{t("emptyInput")}</span>
        </p>
      )}

      {feedback?.kind === "error" && (
        <p role="alert" className="flex items-start gap-2 text-caption font-medium text-danger">
          <TriangleAlert aria-hidden className="mt-0.5 size-4 shrink-0" />
          <span>{t(`errors.${feedback.errorKey}`)}</span>
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
          className="flex items-start gap-2 rounded-lg border border-primary/30 bg-primary-50 px-3 py-2 text-caption font-medium text-primary-text"
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
          className="flex items-start gap-2 rounded-lg border border-primary/30 bg-primary-50 px-3 py-2 text-caption font-medium text-primary-text"
        >
          <Check aria-hidden className="mt-0.5 size-4 shrink-0" />
          <span>
            {t("applied", {
              code: quote.voucher.code,
              discount: storefront("price", { amount: quote.voucher.discountVnd }),
            })}
          </span>
        </p>
      )}
    </section>
  );
}
