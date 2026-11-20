"use client";

import { cn } from "@thi360/ui";
import { useTranslations } from "next-intl";
import { discountPercent, formatDayMonth } from "./format";
import type { PromotionBadge } from "./types";

/**
 * Giá của sản phẩm (TM 11.3): có khuyến mãi tự động thì giá gốc gạch ngang, chip mức giảm và "Kết thúc dd/MM".
 * `priceClassName` đổi cỡ chữ giá cuối (thẻ 20px, trang chi tiết 30px).
 */
export function ProductPrice({
  listPrice,
  price,
  promotion,
  priceClassName,
}: {
  listPrice: number;
  price: number;
  promotion?: PromotionBadge | null;
  priceClassName?: string;
}) {
  const t = useTranslations("storefront");
  const percent = promotion ? discountPercent(listPrice, price) : null;

  return (
    <div className="flex flex-col gap-1">
      {percent !== null && (
        <div className="flex flex-wrap items-center gap-2">
          <s className="text-field-label text-text-muted">
            <span className="sr-only">{t("listPriceLabel")} </span>
            {t("price", { amount: listPrice })}
          </s>
          <span className="rounded-full bg-warning-bg px-2 py-px text-caption font-semibold text-warning">
            {t("discount", { percent })}
          </span>
        </div>
      )}
      <div className="flex flex-wrap items-baseline justify-between gap-x-2">
        <span className={cn("text-h3 font-bold text-text", priceClassName)}>{t("price", { amount: price })}</span>
        {percent !== null && promotion && (
          <span className="text-caption font-medium text-warning">
            {t("promoEnds", { date: formatDayMonth(promotion.endsAt) })}
          </span>
        )}
      </div>
    </div>
  );
}
