"use client";

import { Card, EmptyState, StatusChip, type StatusTone } from "@thi360/ui";
import { History } from "lucide-react";
import { useLocale, useTranslations } from "next-intl";

import { formatPromotionDateTime } from "../datetime";
import { formatNumber, formatVnd } from "../format";
import type {
  PromotionChangeLogView,
  PromotionDiscountType,
  PromotionFieldChange,
} from "../types";

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
  "deletedAt",
  "description",
  "discountType",
  "discountValue",
  "endsAt",
  "examIds",
  "examKindIds",
  "kind",
  "maxDiscountVnd",
  "minOrderVnd",
  "name",
  "perUserLimit",
  "scope",
  "startsAt",
  "status",
  "totalLimit",
]);

const FREE_TEXT_FIELDS = new Set(["name", "description", "code"]);
const DATE_FIELDS = new Set(["startsAt", "endsAt", "deletedAt"]);
const VND_FIELDS = new Set(["maxDiscountVnd", "minOrderVnd"]);
const COUNT_FIELDS = new Set(["totalLimit", "perUserLimit"]);
const ID_LIST_FIELDS = new Set(["examKindIds", "examIds", "audienceClassIds", "audienceUserIds"]);

const KNOWN_ENUM_VALUES: Record<string, ReadonlySet<string>> = {
  kind: new Set(["AUTO", "VOUCHER"]),
  discountType: new Set(["PERCENT", "AMOUNT"]),
  scope: new Set(["ALL", "EXAM_KIND", "EXAM", "BUNDLE"]),
  audience: new Set(["ALL", "NEW_ACCOUNT", "SELECTED"]),
  status: new Set(["ACTIVE", "STOPPED"]),
};

function isEmptyHistoryValue(value: unknown): boolean {
  return value === null || value === undefined || value === "";
}

function resolveDiscountTypeForChange(
  changes: readonly PromotionFieldChange[],
  which: "oldValue" | "newValue",
  fallback?: PromotionDiscountType,
): PromotionDiscountType | undefined {
  const dtChange = changes.find((c) => c.field === "discountType");
  const candidate: unknown = dtChange ? dtChange[which] : undefined;
  if (candidate === "PERCENT" || candidate === "AMOUNT") return candidate;
  return fallback;
}

function formatHistoryValue(
  field: string,
  value: unknown,
  locale: string,
  tHistory: (key: string, values?: Record<string, string | number>) => string,
  tKinds: (key: string) => string,
  resolvedDiscountType?: PromotionDiscountType,
): string {
  if (isEmptyHistoryValue(value)) {
    return tHistory("emptyValue");
  }
  if (typeof value === "boolean") {
    return value ? tHistory("boolTrue") : tHistory("boolFalse");
  }
  if (Array.isArray(value)) {
    if (value.length === 0) return tHistory("emptyValue");
    if (ID_LIST_FIELDS.has(field)) {
      return tHistory(`listCounts.${field}`, { count: value.length });
    }
    return value.join(", ");
  }
  if (DATE_FIELDS.has(field) && typeof value === "string") {
    const formatted = formatPromotionDateTime(value, locale);
    return formatted || value;
  }
  if (VND_FIELDS.has(field) && typeof value === "number") {
    return formatVnd(value, locale);
  }
  if (field === "discountValue" && typeof value === "number") {
    if (resolvedDiscountType === "AMOUNT") {
      return formatVnd(value, locale);
    }
    if (resolvedDiscountType === "PERCENT") {
      return `${formatNumber(value, locale)}%`;
    }
    return formatNumber(value, locale);
  }
  if (COUNT_FIELDS.has(field) && typeof value === "number") {
    return formatNumber(value, locale);
  }
  if (typeof value === "string" && field in KNOWN_ENUM_VALUES && KNOWN_ENUM_VALUES[field]!.has(value)) {
    if (field === "kind") {
      return tKinds(value);
    }
    return tHistory(`values.${field}.${value}`);
  }
  return String(value);
}

export function PromotionHistoryCard({
  history,
  discountType,
}: {
  history: readonly PromotionChangeLogView[];
  discountType?: PromotionDiscountType;
}) {
  const t = useTranslations("promotions.history");
  const tKinds = useTranslations("promotions.kinds");
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
            const oldDiscountType = resolveDiscountTypeForChange(entry.changes, "oldValue", discountType);
            const newDiscountType = resolveDiscountTypeForChange(entry.changes, "newValue", discountType);
            return (
              <li key={entry.id} className="flex flex-col gap-2 py-3 first:pt-0 last:pb-0">
                <div className="flex flex-wrap items-center justify-between gap-2">
                  <div className="flex flex-wrap items-center gap-2">
                    <StatusChip tone={tone}>{actionLabel}</StatusChip>
                    {entry.actorUsername ? (
                      <span className="text-body font-medium text-text" data-exam-content>
                        {entry.actorUsername}
                      </span>
                    ) : (
                      <span className="text-body font-medium text-text">{t("actorFallback")}</span>
                    )}
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
                      const fromText = formatHistoryValue(
                        change.field,
                        change.oldValue,
                        locale,
                        t,
                        tKinds,
                        oldDiscountType,
                      );
                      const toText = formatHistoryValue(
                        change.field,
                        change.newValue,
                        locale,
                        t,
                        tKinds,
                        newDiscountType,
                      );
                      const isFreeText = FREE_TEXT_FIELDS.has(change.field);
                      const wrapFrom = isFreeText && !isEmptyHistoryValue(change.oldValue);
                      const wrapTo = isFreeText && !isEmptyHistoryValue(change.newValue);
                      return (
                        <li key={change.field} className="flex flex-wrap items-baseline gap-1.5">
                          <span className="font-medium text-text">{fieldLabel}:</span>
                          <span className="wrap-anywhere">
                            {wrapFrom ? <span data-exam-content>{fromText}</span> : fromText}
                            {" \u2192 "}
                            {wrapTo ? <span data-exam-content>{toText}</span> : toText}
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
