"use client";

import { AlertTriangle } from "lucide-react";
import { useTranslations } from "next-intl";

import type { CapWarning } from "../types";

export function PromotionCapWarningInline({ warnings }: { warnings: readonly CapWarning[] }) {
  const t = useTranslations("promotions.capWarning");
  if (!warnings || warnings.length === 0) return null;

  const firstNames = warnings.slice(0, 2);
  const extraCount = warnings.length - firstNames.length;

  return (
    <div className="mt-1 inline-flex max-w-full flex-wrap items-center gap-1 rounded-md border border-warning/30 bg-warning-bg px-2 py-0.5 text-caption text-warning">
      <AlertTriangle aria-hidden className="size-3.5 shrink-0" />
      <span className="font-medium">{t("badge")}</span>
      <span className="truncate">
        {firstNames.map((item, idx) => (
          <span key={item.examId}>
            {idx > 0 ? ", " : ": "}
            <span data-exam-content>{item.examName}</span>
          </span>
        ))}
        {extraCount > 0 ? ` ${t("listMore", { count: extraCount })}` : ""}
      </span>
    </div>
  );
}
