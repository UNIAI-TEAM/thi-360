"use client";

import { AlertTriangle } from "lucide-react";
import { useLocale, useTranslations } from "next-intl";

import { formatVnd } from "../format";
import type { CapWarning } from "../types";

export function PromotionCapWarningInline({ warnings }: { warnings: readonly CapWarning[] }) {
  const t = useTranslations("promotions.capWarning");
  const locale = useLocale();
  if (!warnings || warnings.length === 0) return null;

  const floorText = formatVnd(warnings[0]!.floorPrice, locale);

  return (
    <div className="mt-1 flex max-w-full items-start gap-1.5 rounded-md border border-warning/30 bg-warning-bg px-2 py-1 text-caption text-warning">
      <AlertTriangle aria-hidden className="mt-0.5 size-3.5 shrink-0" />
      <span className="min-w-0 font-medium">
        {t("inlineSummary", { floor: floorText, count: warnings.length })}
      </span>
    </div>
  );
}
