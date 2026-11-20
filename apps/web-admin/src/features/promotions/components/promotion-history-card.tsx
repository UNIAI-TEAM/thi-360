"use client";

import { Card, EmptyState, StatusChip, type StatusTone } from "@thi360/ui";
import { History } from "lucide-react";
import { useLocale, useTranslations } from "next-intl";

import { formatPromotionDateTime } from "../datetime";
import { formatVnd } from "../format";
import type { PromotionChangeLogView } from "../types";

const ACTION_TONE: Record<PromotionChangeLogView["action"], StatusTone> = {
  CREATED: "success",
  UPDATED: "info",
  STOPPED: "warning",
  RESUMED: "success",
  DELETED: "danger",
};

const KNOWN_FIELDS = new Set([
  "audience",
  "audienceClassIds",
  "audienceUserIds",
  "code",
  "combinable",
  "description",
  "discountType",
  "discountValue",
  "endsAt",
  "examIds",
  "examKindIds",
  "kind",
  "maxDiscountVnd",
  "name",
  "perUserLimit",
  "scope",
  "startsAt",
  "status",
  "totalLimit",
]);

function formatHistoryValue(
  field: string,
  value: unknown,
  locale: string,
  tHistory: (key: string) => string,
): string {
  if (value === null || value === undefined || value === "") {
    return tHistory("emptyValue");
  }
  if (typeof value === "boolean") {
    return value ? tHistory("boolTrue") : tHistory("boolFalse");
  }
  if (Array.isArray(value)) {
    return value.length === 0 ? tHistory("emptyValue") : value.join(", ");
  }
  if ((field === "startsAt" || field === "endsAt") && typeof value === "string") {
    const formatted = formatPromotionDateTime(value, locale);
    return formatted || value;
  }
  if (field === "maxDiscountVnd" && typeof value === "number") {
    return formatVnd(value, locale);
  }
  return String(value);
}

export function PromotionHistoryCard({
  history,
}: {
  history: readonly PromotionChangeLogView[];
}) {
  const t = useTranslations("promotions.history");
  const locale = useLocale();

  const sortedHistory = [...history].sort((a, b) => {
    const diff = Date.parse(b.occurredAt) - Date.parse(a.occurredAt);
    return Number.isNaN(diff) ? 0 : diff;
  });

  return (
    <Card className="flex flex-col gap-4 p-5">
      <div className="flex flex-col gap-0.5">
        <h2 className="text-title font-semibold text-text">{t("title")}</h2>
        <p className="text-caption text-text-muted">{t("description")}</p>
      </div>

      {sortedHistory.length === 0 ? (
        <EmptyState
          icon={<History />}
          title={t("emptyTitle")}
          description={t("emptyDescription")}
        />
      ) : (
        <ol className="divide-y divide-border">
          {sortedHistory.map((entry) => {
            const tone = ACTION_TONE[entry.action] ?? "neutral";
            const actionLabel = t(`actions.${entry.action}`);
            return (
              <li key={entry.id} className="flex flex-col gap-2 py-3 first:pt-0 last:pb-0">
                <div className="flex flex-wrap items-center justify-between gap-2">
                  <div className="flex flex-wrap items-center gap-2">
                    <StatusChip tone={tone}>{actionLabel}</StatusChip>
                    <span className="text-body font-medium text-text" data-exam-content>
                      {entry.actorUsername ?? t("actorFallback")}
                    </span>
                  </div>
                  <span className="text-caption text-text-muted tabular-nums">
                    {formatPromotionDateTime(entry.occurredAt, locale)}
                  </span>
                </div>

                {entry.changes.length > 0 && (
                  <ul role="list" className="flex flex-col gap-1 pl-1 text-caption text-text-secondary">
                    {entry.changes.map((change) => {
                      const fieldLabel = KNOWN_FIELDS.has(change.field)
                        ? t(`fields.${change.field}`)
                        : change.field;
                      const fromText = formatHistoryValue(change.field, change.oldValue, locale, t);
                      const toText = formatHistoryValue(change.field, change.newValue, locale, t);
                      return (
                        <li key={change.field} className="flex flex-wrap items-baseline gap-1.5">
                          <span className="font-medium text-text">{fieldLabel}:</span>
                          <span data-exam-content className="wrap-anywhere">
                            {t("arrow", { from: fromText, to: toText })}
                          </span>
                        </li>
                      );
                    })}
                  </ul>
                )}
              </li>
            );
          })}
        </ol>
      )}
    </Card>
  );
}
