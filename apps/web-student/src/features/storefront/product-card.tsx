"use client";

import { pickName } from "@/lib/pick-name";
import { useLocale, useTranslations } from "next-intl";
import Link from "next/link";
import type { ReactNode } from "react";
import { rangeText } from "./format";
import { ProductPrice } from "./product-price";
import type { ProductCard as ProductCardData } from "./types";

/** Nhãn "Bài ôn" khi bài xây từ chế độ ôn tập (`buildMode = REVIEW`, BS 2). */
export const REVIEW_BUILD_MODE = "REVIEW";

const chipBase = "rounded-full px-2.5 py-0.5 text-caption leading-[1.1875rem]";

/** Chip hình thức của một sản phẩm: "Bài thi FULL", "Bài thi theo phần · <phần>", "Bài thi theo môn · <môn>". */
export function FormatChip({
  format,
  scopeLabel,
}: {
  format: string;
  scopeLabel?: ProductCardData["scopeLabel"];
}) {
  const t = useTranslations("storefront.format");
  const locale = useLocale();
  const known = format === "FULL" || format === "PART" || format === "SUBJECT";
  return (
    <span className={`${chipBase} bg-subtle font-medium text-text-secondary`}>
      {known ? t(format) : format}
      {scopeLabel && (
        <>
          {" · "}
          {/* Tên phần là nội dung bài thi (không dịch), tên môn lấy từ danh mục song ngữ: vùng nội dung đề bỏ qua phép kiểm lẫn ngôn ngữ. */}
          <span data-exam-content>{pickName(scopeLabel, locale)}</span>
        </>
      )}
    </span>
  );
}

export function ProductCard({ product, action }: { product: ProductCardData; action: ReactNode }) {
  const t = useTranslations("storefront");
  const common = useTranslations("common");
  const href = `/exams/${encodeURIComponent(product.code)}`;
  const questions = rangeText(product.questionCount);
  const minutes = rangeText(product.durationMinutes);
  const meta = [
    questions !== null && t("units.questions", { value: questions, count: product.questionCount?.max ?? 0 }),
    minutes !== null && t("units.minutes", { value: minutes, count: product.durationMinutes?.max ?? 0 }),
    t("units.attempts", { count: product.attemptsGranted }),
  ].filter(Boolean);

  return (
    <article className="flex flex-col overflow-hidden rounded-xl border border-border bg-surface">
      <Link href={href} tabIndex={-1} aria-hidden className="block">
        {product.coverUrl ? (
          // Ảnh bìa do máy chủ phát qua proxy cùng nguồn (TM 10.2); tên bài đã có ở tiêu đề thẻ nên ảnh chỉ để trang trí.
          // eslint-disable-next-line @next/next/no-img-element
          <img src={product.coverUrl} alt="" loading="lazy" className="aspect-video w-full object-cover" />
        ) : (
          <div className="flex aspect-video flex-col items-center justify-center gap-2 bg-subtle">
            <span className="flex size-11 items-center justify-center rounded-lg border border-border bg-surface font-bold text-primary-text">
              {common("logoMark")}
            </span>
            <span className="text-caption font-medium text-text-secondary">{product.examKind.code}</span>
          </div>
        )}
      </Link>
      <div className="flex flex-1 flex-col gap-2.5 p-4">
        <div className="flex flex-wrap gap-1.5">
          <span className={`${chipBase} border border-primary-600 bg-primary-50 font-semibold text-primary-700`}>
            {product.examKind.code}
          </span>
          <FormatChip format={product.format} scopeLabel={product.scopeLabel} />
          {product.buildMode === REVIEW_BUILD_MODE && (
            <span className={`${chipBase} bg-info-bg font-semibold text-info`}>{t("review")}</span>
          )}
        </div>
        <h3 className="line-clamp-2 text-title font-semibold text-text">
          <Link href={href} data-exam-content className="hover:underline">
            {product.name}
          </Link>
        </h3>
        <p className="text-field-label text-text-secondary">{meta.join(" · ")}</p>
        <div className="mt-auto flex flex-col gap-3 pt-1.5">
          <ProductPrice listPrice={product.listPrice} price={product.price} promotion={product.promotion} />
          {action}
        </div>
      </div>
    </article>
  );
}
