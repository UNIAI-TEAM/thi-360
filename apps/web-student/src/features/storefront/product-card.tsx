"use client";

import { pickName } from "@thi360/web-kit/i18n";
import { BookOpen, LayoutGrid, Package, PenLine } from "lucide-react";
import { useLocale, useTranslations } from "next-intl";
import Link from "next/link";
import type { ReactNode } from "react";
import { discountPercent, isBundleProduct, REVIEW_BUILD_MODE, rangeText } from "./format";
import { ProductPrice } from "./product-price";
import type { ProductCard as ProductCardData } from "./types";

const chipBase = "inline-flex min-h-6 items-center rounded-full px-2.5 py-0.5 text-caption leading-[1.1875rem]";

/** Các tag hình thức và phần/môn đi kèm của sản phẩm: tách riêng từng tag để không bị gãy thành hai cột trong một chip. */
export function FormatChip({
  format,
  scopeLabel,
}: {
  format: string;
  scopeLabel?: ProductCardData["scopeLabel"];
}) {
  const t = useTranslations("storefront.format");
  const locale = useLocale();
  const known = format === "FULL" || format === "PART" || format === "SUBJECT" || format === "BUNDLE";
  const scopeName = scopeLabel ? pickName(scopeLabel, locale) : "";
  return (
    <>
      <span className={`${chipBase} bg-subtle font-medium text-text-secondary`}>
        {known ? t(format) : format}
      </span>
      {scopeName && (
        /* Tên phần (PART) là nội dung đề nên đánh dấu data-exam-content; tên môn (SUBJECT) qua pickName từ danh
           mục song ngữ, phép kiểm lẫn ngôn ngữ vẫn áp cho nó. */
        <span
          data-exam-content={format === "PART" ? "" : undefined}
          className={`${chipBase} bg-subtle font-medium text-text-secondary`}
        >
          {scopeName}
        </span>
      )}
    </>
  );
}

/** Họa tiết vòng cung quỹ đạo trang trí trên bìa thẻ mặc định (đồng bộ ngôn ngữ màn Auth). */
function CoverOrbitDecoration() {
  return (
    <svg
      aria-hidden="true"
      viewBox="0 0 160 160"
      fill="none"
      className="pointer-events-none absolute -top-8 -right-8 size-36 opacity-15 select-none"
    >
      <circle cx="80" cy="80" r="68" stroke="currentColor" strokeWidth="1.5" />
      <circle cx="80" cy="80" r="48" stroke="currentColor" strokeWidth="1.2" strokeDasharray="4 4" />
    </svg>
  );
}

function DefaultProductCover({
  product,
  isBundle,
  bundleLabel,
  locale,
}: {
  product: ProductCardData;
  isBundle: boolean;
  bundleLabel: string;
  locale: string;
}) {
  const percent = product.promotion ? discountPercent(product.listPrice, product.price) : null;
  const discountBadge = percent !== null && (
    <span className="shrink-0 rounded-full bg-yellow-400 px-2.5 py-0.5 text-caption font-bold text-on-accent shadow-xs">
      -{percent}%
    </span>
  );

  if (isBundle) {
    return (
      <div className="relative flex aspect-video flex-col justify-between overflow-hidden bg-primary-fill bg-[linear-gradient(125deg,var(--color-primary-fill-active),var(--color-primary-fill))] p-4 text-on-primary forced-colors:bg-[Canvas] forced-colors:text-[CanvasText]">
        <CoverOrbitDecoration />
        <div className="relative z-1 flex items-center justify-between gap-2">
          <span className="rounded-full border border-on-primary/35 bg-primary-fill-active px-2.5 py-0.5 text-caption font-semibold text-on-primary forced-colors:border-[CanvasText] forced-colors:bg-[Canvas] forced-colors:text-[CanvasText]">
            {product.examKind.code}
          </span>
          {discountBadge}
        </div>
        <div className="relative z-1 flex items-center gap-2">
          <Package aria-hidden className="size-6 shrink-0" />
          <span className="text-title font-bold">{bundleLabel}</span>
        </div>
      </div>
    );
  }

  const kindLabel = pickName(product.examKind, locale) || product.examKind.code;

  if (product.format === "FULL") {
    return (
      <div className="relative flex aspect-video flex-col justify-between overflow-hidden bg-primary-fill bg-[linear-gradient(125deg,var(--color-primary-fill-active),var(--color-primary-fill))] p-4 text-on-primary forced-colors:bg-[Canvas] forced-colors:text-[CanvasText]">
        <CoverOrbitDecoration />
        <div className="relative z-1 flex items-center justify-between gap-2">
          <span className="line-clamp-1 rounded-full border border-on-primary/35 bg-primary-fill-active px-2.5 py-0.5 text-caption font-semibold text-on-primary forced-colors:border-[CanvasText] forced-colors:bg-[Canvas] forced-colors:text-[CanvasText]">
            {kindLabel}
          </span>
          {discountBadge}
        </div>
        <div className="relative z-1 flex items-center gap-2 text-caption font-semibold">
          <BookOpen aria-hidden className="size-4 shrink-0" />
          <span className="line-clamp-1 break-all">{product.code}</span>
        </div>
      </div>
    );
  }

  if (product.format === "PART") {
    return (
      <div className="relative flex aspect-video flex-col justify-between overflow-hidden border-b border-border-primary bg-primary-50 bg-[linear-gradient(135deg,var(--color-primary-50),var(--color-primary-100))] p-4 text-primary-900">
        <CoverOrbitDecoration />
        <div className="relative z-1 flex items-center justify-between gap-2">
          <span className="line-clamp-1 rounded-full border border-primary-600 bg-surface px-2.5 py-0.5 text-caption font-semibold text-primary-text">
            {kindLabel}
          </span>
          {discountBadge}
        </div>
        <div className="relative z-1 flex items-center gap-2 text-caption font-semibold text-primary-800">
          <LayoutGrid aria-hidden className="size-4 shrink-0" />
          <span className="line-clamp-1 break-all">{product.code}</span>
        </div>
      </div>
    );
  }

  return (
    <div className="relative flex aspect-video flex-col justify-between overflow-hidden border-b border-border bg-surface bg-[linear-gradient(135deg,var(--color-surface),var(--color-primary-50))] p-4 text-primary-900">
      <CoverOrbitDecoration />
      <div className="relative z-1 flex items-center justify-between gap-2">
        <span className="line-clamp-1 rounded-full border border-primary-600 bg-primary-50 px-2.5 py-0.5 text-caption font-semibold text-primary-700">
          {kindLabel}
        </span>
        {discountBadge}
      </div>
      <div className="relative z-1 flex items-center gap-2 text-caption font-semibold text-primary-800">
        <PenLine aria-hidden className="size-4 shrink-0" />
        <span className="line-clamp-1 break-all">{product.code}</span>
      </div>
    </div>
  );
}

export function ProductCard({ product, action }: { product: ProductCardData; action: ReactNode }) {
  const t = useTranslations("storefront");
  const locale = useLocale();
  const href = `/exams/${encodeURIComponent(product.code)}`;
  const isBundle = isBundleProduct(product);
  const questions = rangeText(product.questionCount, locale);
  const minutes = rangeText(product.durationMinutes, locale);
  const meta = [
    questions !== null && t("units.questions", { value: questions, count: product.questionCount?.max ?? 0 }),
    minutes !== null && t("units.minutes", { value: minutes, count: product.durationMinutes?.max ?? 0 }),
    isBundle
      ? t("units.attemptsPerExam", { count: product.attemptsGranted })
      : t("units.attempts", { count: product.attemptsGranted }),
  ].filter(Boolean);

  return (
    <article className="group flex w-full flex-col overflow-hidden rounded-2xl border border-border bg-surface/92 shadow-xs backdrop-blur-sm transition-[transform,box-shadow,border-color] duration-200 hover:border-primary-600 hover:shadow-lg motion-safe:hover:-translate-y-1 motion-reduce:transition-none">
      <Link href={href} tabIndex={-1} aria-hidden className="block">
        {!isBundle && product.coverUrl ? (
          // Ảnh bìa do máy chủ phát qua proxy cùng nguồn (TM 10.2); tên bài đã có ở tiêu đề thẻ nên ảnh chỉ để trang trí.
          // eslint-disable-next-line @next/next/no-img-element
          <img src={product.coverUrl} alt="" loading="lazy" className="aspect-video w-full object-cover" />
        ) : (
          <DefaultProductCover
            product={product}
            isBundle={isBundle}
            bundleLabel={t("format.BUNDLE")}
            locale={locale}
          />
        )}
      </Link>
      <div className="flex flex-1 flex-col gap-3 p-4">
        <div className="flex flex-wrap gap-1.5">
          <span className={`${chipBase} border border-primary-600 bg-primary-50 font-semibold text-primary-700`}>
            {product.examKind.code}
          </span>
          <FormatChip format={product.format} scopeLabel={product.scopeLabel} />
          {product.buildMode === REVIEW_BUILD_MODE && (
            <span className={`${chipBase} bg-info-bg font-semibold text-info`}>{t("review")}</span>
          )}
        </div>
        <h3 className="line-clamp-2 min-h-12 text-title font-bold text-text transition-colors group-hover:text-primary-text">
          <Link href={href} data-exam-content className="hover:underline">
            {product.name}
          </Link>
        </h3>
        {meta.length > 0 && (
          <p className="rounded-lg border border-border/80 bg-subtle/80 px-3 py-2 text-field-label font-medium text-text-secondary tabular-nums">
            {meta.join(" · ")}
          </p>
        )}
        <div className="mt-auto flex flex-col gap-3 pt-1">
          <ProductPrice listPrice={product.listPrice} price={product.price} promotion={product.promotion} />
          {action}
        </div>
      </div>
    </article>
  );
}
