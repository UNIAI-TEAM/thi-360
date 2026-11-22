"use client";

import {
  Card,
  CardList,
  CardListItem,
  ListCard,
  StatusChip,
  cn,
  focusRingInset,
  useCardMode,
} from "@thi360/ui";
import { AlertTriangle } from "lucide-react";
import { useLocale, useTranslations } from "next-intl";

import { formatVnd } from "../format";
import type { CapWarning } from "../types";

export function PromotionCapWarningCard({
  warnings,
}: {
  warnings: readonly CapWarning[];
}) {
  const t = useTranslations("promotions.capWarning");
  const locale = useLocale();
  const [containerRef, cardMode] = useCardMode<HTMLDivElement>(warnings.length > 0, 640);

  if (!warnings || warnings.length === 0) return null;

  return (
    <Card className="flex flex-col gap-3 border-warning/40 p-5">
      <div className="flex items-start gap-2.5">
        <AlertTriangle aria-hidden className="mt-0.5 size-5 shrink-0 text-warning" />
        <div className="flex flex-col gap-0.5">
          <h2 className="text-title font-semibold text-text">
            {t("cardTitle", { count: warnings.length })}
          </h2>
          <p className="text-caption text-text-secondary">{t("cardDescription")}</p>
        </div>
      </div>

      <div ref={containerRef} className="min-w-0">
        {cardMode ? (
          <CardList className="rounded-md border border-border">
            {warnings.map((item) => (
              <CardListItem key={item.examId}>
                <ListCard
                  title={<span data-exam-content>{item.examName}</span>}
                  status={<StatusChip tone="warning">{t("badge")}</StatusChip>}
                  meta={[
                    { label: t("listPrice"), value: formatVnd(item.listPrice, locale) },
                    { label: t("floorPrice"), value: formatVnd(item.floorPrice, locale) },
                    { label: t("discountedPrice"), value: formatVnd(item.discountedPrice, locale) },
                  ]}
                />
              </CardListItem>
            ))}
          </CardList>
        ) : (
          <div
            role="region"
            tabIndex={0}
            aria-label={t("tableLabel")}
            className={cn("overflow-x-auto rounded-md border border-border", focusRingInset)}
          >
            <table aria-label={t("tableLabel")} className="w-full border-collapse text-left text-body">
              <thead>
                <tr className="border-b border-border bg-table-head text-field-label text-text-muted">
                  <th scope="col" className="px-3 py-2 font-semibold">
                    {t("examName")}
                  </th>
                  <th scope="col" className="px-3 py-2 font-semibold">
                    {t("listPrice")}
                  </th>
                  <th scope="col" className="px-3 py-2 font-semibold">
                    {t("floorPrice")}
                  </th>
                  <th scope="col" className="px-3 py-2 font-semibold">
                    {t("discountedPrice")}
                  </th>
                </tr>
              </thead>
              <tbody className="divide-y divide-border">
                {warnings.map((item) => (
                  <tr key={item.examId}>
                    <td className="px-3 py-2 font-medium text-text">
                      <span data-exam-content>{item.examName}</span>
                    </td>
                    <td className="px-3 py-2 text-text-secondary tabular-nums">
                      {formatVnd(item.listPrice, locale)}
                    </td>
                    <td className="px-3 py-2 text-text-secondary tabular-nums">
                      {formatVnd(item.floorPrice, locale)}
                    </td>
                    <td className="px-3 py-2 tabular-nums">
                      <span className="inline-flex items-center gap-2 font-medium text-warning">
                        <span>{formatVnd(item.discountedPrice, locale)}</span>
                        <StatusChip tone="warning">{t("badge")}</StatusChip>
                      </span>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </div>
    </Card>
  );
}
