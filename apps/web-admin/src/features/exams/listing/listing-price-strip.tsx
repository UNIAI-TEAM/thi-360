"use client";

import { ArrowRight, Info } from "lucide-react";
import { useFormatter, useTranslations } from "next-intl";
import Link from "next/link";
import { examTabHref } from "../editor-tabs";
import { differingTerms, type ListingTermField } from "./form";
import type { ExamListing } from "./types";

/**
 * Dải "Giá đang bán: … · Sẽ đổi khi công bố" (TM 6.4, AC-3): chỉ hiện khi bản đang bán có thông tin bán và khác bản làm
 * việc đã lưu. Chưa công bố, hoặc phiên bản đang phục vụ có trước thông tin bán, hoặc hai bản giống nhau thì không hiện.
 * Tab chỉ xem vẫn hiện dải này.
 */
export function ListingPriceStrip({ listing }: { listing: ExamListing }) {
  const t = useTranslations("exams.listing.strip");
  const format = useFormatter();
  const published = listing.published;
  if (!published) return null;
  const fields = differingTerms(listing, published.terms);
  if (fields.length === 0) return null;

  const money = (amount: number | null) => (amount === null ? t("empty") : t("money", { amount: format.number(amount) }));
  const count = (amount: number | null) => (amount === null ? t("empty") : format.number(amount));
  const labelOf = (field: ListingTermField): string => {
    if (field === "listPriceVnd") {
      return t("fields.listPriceVnd", { from: money(published.terms.listPriceVnd), to: money(listing.listPriceVnd) });
    }
    if (field === "attemptsGranted") {
      return t("fields.attemptsGranted", {
        from: count(published.terms.attemptsGranted),
        to: count(listing.attemptsGranted),
      });
    }
    return t(`fields.${field}`);
  };
  const version = published.versionNo;

  return (
    <div
      role="note"
      aria-labelledby="listing-strip-title"
      className="flex flex-wrap items-center justify-between gap-x-4 gap-y-2 rounded-lg border border-warning/30 bg-warning-bg px-4 py-3"
    >
      <div className="flex min-w-0 flex-1 basis-96 gap-2.5">
        <Info aria-hidden className="mt-0.5 size-5 shrink-0 text-warning" />
        <div className="flex min-w-0 flex-col gap-0.5">
          <strong id="listing-strip-title" className="font-semibold text-text tabular-nums">
            {t("title", { price: money(published.terms.listPriceVnd) })}
          </strong>
          <span className="text-body text-text-secondary tabular-nums">
            {t("detail", { fields: fields.map(labelOf).join(", "), hasVersion: version === null ? "no" : "yes", version: version ?? 0 })}
          </span>
        </div>
      </div>
      <Link
        href={examTabHref(listing.examId, "versions")}
        className="inline-flex min-h-11 items-center gap-1.5 rounded-sm font-semibold text-primary-text hover:underline"
      >
        {t("compare", { hasVersion: version === null ? "no" : "yes", version: version ?? 0 })}
        <ArrowRight aria-hidden className="size-4" />
      </Link>
    </div>
  );
}
