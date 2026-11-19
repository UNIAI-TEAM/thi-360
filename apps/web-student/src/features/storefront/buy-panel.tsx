"use client";

import { useTranslations } from "next-intl";
import Link from "next/link";
import { withCallbackUrl } from "@/features/account/account-links";
import { AccessAction, checkoutPath } from "./access-action";
import { useExamAccess } from "./exam-access";
import { formatDayMonth, rangeText } from "./format";
import { ProductPrice } from "./product-price";
import type { ProductDetail } from "./types";

/** Khung "Giá bán" của trang chi tiết: giá, quyền lợi ngắn và nút theo quyền của người xem (TM 11.3). */
export function BuyPanel({ product }: { product: ProductDetail }) {
  const t = useTranslations("storefront.detail");
  const header = useTranslations("header");
  const access = useExamAccess([product.code]);
  const view = access.view(product.code);
  const questions = rangeText(product.questionCount);
  const minutes = rangeText(product.durationMinutes);
  const promotion = product.promotion;

  return (
    <aside
      aria-labelledby="buy-title"
      className="flex min-w-0 flex-[1_1_20rem] flex-col gap-3.5 rounded-xl border border-border bg-surface p-6 shadow-xs lg:sticky lg:top-4"
    >
      <h2 id="buy-title" className="text-overline font-semibold tracking-overline text-text-muted uppercase">
        {t("priceHeading")}
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
          {t("promoEnds", { date: formatDayMonth(promotion.endsAt) })}
        </p>
      )}
      <ul className="flex flex-col gap-2 border-t border-border pt-3 text-text-secondary">
        <li>{t("includesTax")}</li>
        <li>{t("attemptsPerPurchase", { count: product.attemptsGranted })}</li>
        {questions !== null && minutes !== null && (
          <li>{t("perAttempt", { questions, minutes })}</li>
        )}
      </ul>
      <AccessAction
        variant="detail"
        product={{ code: product.code, versionNo: product.versionNo, price: product.price }}
        view={view}
        onReload={access.reload}
      />
      {view.kind === "guest" && (
        <p className="text-field-label text-text-muted">
          {t("guestHint")}{" "}
          <Link
            href={withCallbackUrl("/register", checkoutPath(product))}
            className="font-medium text-primary-text underline-offset-2 hover:underline"
          >
            {header("register")}
          </Link>
        </p>
      )}
    </aside>
  );
}
