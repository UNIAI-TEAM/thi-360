"use client";

import { Button, Skeleton } from "@thi360/ui";
import { Info, RefreshCw, TriangleAlert } from "lucide-react";
import { useLocale, useTranslations } from "next-intl";
import Link from "next/link";
import { withCallbackUrl } from "@/features/account/account-links";
import { checkoutPath } from "./access-action";
import { useExamAccess } from "./exam-access";
import { formatDayMonth, rangeText } from "./format";
import { ProductPrice } from "./product-price";
import type { BundleAccessExamItem, ProductDetail } from "./types";

function hasOwnedAttempts(item: BundleAccessExamItem): boolean {
  return item.attemptsLeft > 0 || item.state === "CAN_START" || item.state === "IN_PROGRESS";
}

/** Khung "Giá bộ" của trang chi tiết bộ đề (AC-3, AC-11, AC-12). */
export function BundleBuyPanel({
  product,
  unavailable = false,
}: {
  product: ProductDetail;
  unavailable?: boolean;
}) {
  const t = useTranslations("bundle");
  const detail = useTranslations("storefront.detail");
  const storefront = useTranslations("storefront");
  const accessT = useTranslations("storefront.access");
  const header = useTranslations("header");
  const locale = useLocale();

  const isUnavailable =
    unavailable || (product as ProductDetail & { saleState?: string }).saleState === "UNAVAILABLE";
  const access = useExamAccess(isUnavailable ? [] : [product.code]);
  const view = access.view(product.code);

  const items = product.items ?? [];
  const questions = rangeText(product.questionCount, locale);
  const minutes = rangeText(product.durationMinutes, locale);
  const promotion = product.promotion;
  const checkout = checkoutPath({ code: product.code, versionNo: product.versionNo, price: product.price });

  const accessItems = view.kind === "ready" ? (view.row.items ?? []) : [];
  const ownedItems = accessItems.filter(hasOwnedAttempts);
  const pendingItems = accessItems.filter((item) => item.hasPendingFulfillment);

  return (
    <aside
      aria-labelledby="buy-title"
      className="flex min-w-0 flex-[1_1_20rem] flex-col gap-3.5 rounded-xl border border-border bg-surface p-6 shadow-xs lg:sticky lg:top-4"
    >
      <h2 id="buy-title" className="text-overline font-semibold tracking-overline text-text-muted uppercase">
        {t("detail.priceLabel")}
      </h2>
      <ProductPrice
        listPrice={product.listPrice}
        price={product.price}
        promotion={promotion}
        priceClassName="text-h1"
      />
      {promotion && (
        <p className="text-field-label font-medium text-warning">
          <span data-exam-content>{promotion.name}</span>
          {" · "}
          {detail("promoEnds", { date: formatDayMonth(promotion.endsAt) })}
        </p>
      )}
      <dl className="flex flex-col gap-2 border-t border-border pt-3 text-text-secondary tabular-nums">
        <div className="flex items-baseline justify-between gap-3">
          <dt className="text-text-muted">{t("detail.examCountLabel")}</dt>
          <dd className="font-semibold text-text">{t("detail.examCountValue", { count: items.length })}</dd>
        </div>
        <div className="flex items-baseline justify-between gap-3">
          <dt className="text-text-muted">{t("detail.attemptsPerExamLabel")}</dt>
          <dd className="font-semibold text-text">{storefront("units.attempts", { count: product.attemptsGranted })}</dd>
        </div>
        {questions !== null && (
          <div className="flex items-baseline justify-between gap-3">
            <dt className="text-text-muted">{t("detail.totalQuestionsLabel")}</dt>
            <dd className="font-semibold text-text">
              {storefront("units.questions", { value: questions, count: product.questionCount?.max ?? 0 })}
            </dd>
          </div>
        )}
        {minutes !== null && (
          <div className="flex items-baseline justify-between gap-3">
            <dt className="text-text-muted">{t("detail.totalDurationLabel")}</dt>
            <dd className="font-semibold text-text">
              {storefront("units.minutes", { value: minutes, count: product.durationMinutes?.max ?? 0 })}
            </dd>
          </div>
        )}
      </dl>
      <p className="text-caption text-text-muted">{detail("includesTax")}</p>

      {isUnavailable ? (
        <div className="flex flex-col gap-3">
          <div
            role="alert"
            className="flex items-start gap-2.5 rounded-lg border border-warning bg-warning-bg p-3.5 text-body text-text"
          >
            <TriangleAlert aria-hidden className="mt-0.5 size-4.5 shrink-0 text-warning" />
            <div className="flex flex-col gap-0.5">
              <strong className="font-semibold text-warning">{t("detail.unavailableTitle")}</strong>
              <span className="text-field-label text-text">{t("detail.unavailableBody")}</span>
            </div>
          </div>
          <Button type="button" size="xl" className="w-full" disabled>
            {t("buyLabel")}
          </Button>
        </div>
      ) : view.kind === "loading" ? (
        <div role="status" aria-busy="true" aria-label={accessT("loading")}>
          <Skeleton className="h-13 w-full rounded-lg" />
        </div>
      ) : view.kind === "error" ? (
        <div className="flex flex-col gap-3">
          <p role="alert" className="text-field-label text-danger">
            {accessT("loadError")}
          </p>
          <Button type="button" variant="secondary" className="h-13 w-full" onClick={access.reload}>
            <RefreshCw aria-hidden />
            {accessT("reload")}
          </Button>
        </div>
      ) : view.kind === "admin" ? (
        <p className="text-field-label text-text-muted">{accessT("adminNote")}</p>
      ) : view.kind === "guest" ? (
        <>
          <Button asChild size="xl" className="w-full">
            <Link href={withCallbackUrl("/login", checkout)} prefetch={false}>
              {t("buyLabel")}
            </Link>
          </Button>
          <p className="text-field-label text-text-muted">
            {detail("guestHint")}{" "}
            <Link
              href={withCallbackUrl("/register", checkout)}
              prefetch={false}
              className="font-medium text-primary-text underline-offset-2 hover:underline"
            >
              {header("register")}
            </Link>
          </p>
        </>
      ) : (
        <>
          <Button asChild size="xl" className="w-full">
            <Link href={checkout} prefetch={false}>
              {t("buyLabel")}
            </Link>
          </Button>
          {ownedItems.length > 0 && (
            <div
              role="status"
              className="flex flex-col gap-2 rounded-lg border border-border-primary bg-info-bg p-3.5 text-field-label text-text"
            >
              {ownedItems.map((owned) => (
                <p key={owned.examId || owned.code} className="flex items-start gap-2">
                  <Info aria-hidden className="mt-0.5 size-4 shrink-0 text-info" />
                  <span>
                    {t.rich("detail.existingEntitlement", {
                      count: owned.attemptsLeft,
                      name: owned.name,
                      examContent: (chunks) => (
                        <span data-exam-content className="font-semibold">
                          {chunks}
                        </span>
                      ),
                    })}
                  </span>
                </p>
              ))}
            </div>
          )}
          {pendingItems.length > 0 && (
            <div
              role="status"
              className="flex flex-col gap-2 rounded-lg border border-warning bg-warning-bg p-3.5 text-field-label text-text"
            >
              {pendingItems.map((pending) => (
                <p key={pending.examId || pending.code} className="flex items-start gap-2">
                  <Info aria-hidden className="mt-0.5 size-4 shrink-0 text-warning" />
                  <span>
                    {pending.pendingOrderCode
                      ? t.rich("detail.pendingOrder", {
                          orderCode: pending.pendingOrderCode,
                          name: pending.name,
                          examContent: (chunks) => (
                            <span data-exam-content className="font-semibold">
                              {chunks}
                            </span>
                          ),
                        })
                      : t.rich("detail.pendingOrderNoCode", {
                          name: pending.name,
                          examContent: (chunks) => (
                            <span data-exam-content className="font-semibold">
                              {chunks}
                            </span>
                          ),
                        })}
                  </span>
                </p>
              ))}
            </div>
          )}
          <p className="text-caption text-text-muted">{t("buyNote")}</p>
        </>
      )}
    </aside>
  );
}
