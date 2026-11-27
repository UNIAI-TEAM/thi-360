"use client";

import { cn } from "@thi360/ui";
import { Tag } from "lucide-react";
import { useTranslations } from "next-intl";
import { discountPercent, formatDayMonth } from "./format";
import type { PromotionBadge } from "./types";

/**
 * Giá của sản phẩm (TM 11.3). Không có khuyến mãi: chỉ giá cuối.
 * Có khuyến mãi tự động: một ô nổi bật ghi tên chương trình (vì sao được giảm), giá cuối kèm chip mức giảm
 * màu đặc, giá gốc gạch ngang và ngày kết thúc (UNI-2888).
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
  const finalPrice = (
    <span className={cn("text-h3 font-bold text-text tabular-nums", priceClassName)}>
      {t("price", { amount: price })}
    </span>
  );

  if (percent === null || !promotion) {
    return <div className="flex flex-col gap-1">{finalPrice}</div>;
  }

  return (
    <div className="flex flex-col gap-1.5 rounded-xl border border-warning/40 bg-warning-bg px-3 py-2.5">
      <p className="flex min-w-0 items-start gap-1.5 text-field-label font-semibold text-warning">
        <Tag aria-hidden className="mt-0.5 size-4 shrink-0" />
        <span className="sr-only">{t("promoLabel")} </span>
        {/* Tên dài thì xuống dòng, không cắt "…": người mua cần đọc đủ lý do được giảm. */}
        <span data-exam-content className="min-w-0 [overflow-wrap:anywhere]">
          {promotion.name}
        </span>
      </p>
      <div className="flex flex-wrap items-center gap-x-2 gap-y-1">
        {finalPrice}
        <span className="rounded-md bg-danger-solid px-2 py-0.5 text-field-label font-bold text-on-danger tabular-nums">
          <span aria-hidden>-{percent}%</span>
          <span className="sr-only">{t("discount", { percent })}</span>
        </span>
        <s className="text-field-label text-text-muted tabular-nums">
          <span className="sr-only">{t("listPriceLabel")} </span>
          {t("price", { amount: listPrice })}
        </s>
      </div>
      <span className="text-caption font-medium text-text-secondary">
        {t("promoEnds", { date: formatDayMonth(promotion.endsAt) })}
      </span>
    </div>
  );
}
