"use client";

import { Button, Card, Input, Label, Select, Skeleton, StatusChip, cn, focusRing } from "@thi360/ui";
import { AlertTriangle, Lock, Search, X } from "lucide-react";
import { useLocale, useTranslations } from "next-intl";
import { useEffect, useId, useMemo, useRef, useState } from "react";

import { usePermissions } from "@/features/session/session-provider";

import { resolvePromotionErrorMessage } from "../errors";
import { formatVnd } from "../format";
import { useExamPickerQuery, usePricePreviewQuery } from "../queries";
import type { CapWarning, PromotionScopeItemView } from "../types";

export function PromotionPricePreviewCard({
  promotionId,
  scopeItems = [],
  capWarnings = [],
}: {
  promotionId?: string;
  scopeItems?: readonly PromotionScopeItemView[];
  capWarnings?: readonly CapWarning[];
}) {
  const t = useTranslations("promotions");
  const tErrors = useTranslations("promotions.errors");
  const locale = useLocale();
  const { can } = usePermissions();
  const canViewExams = can("EXAM.VIEW");
  const reasonId = useId();
  const selectId = useId();

  const [searchText, setSearchText] = useState("");
  const [debouncedQ, setDebouncedQ] = useState("");
  const timerRef = useRef<number | null>(null);

  useEffect(() => {
    return () => {
      if (timerRef.current !== null) window.clearTimeout(timerRef.current);
    };
  }, []);

  const handleSearchChange = (next: string) => {
    if (timerRef.current !== null) {
      window.clearTimeout(timerRef.current);
      timerRef.current = null;
    }
    setSearchText(next);
    if (next === "") {
      setDebouncedQ("");
      return;
    }
    timerRef.current = window.setTimeout(() => {
      setDebouncedQ(next);
      timerRef.current = null;
    }, 250);
  };

  const saved = Boolean(promotionId);
  const examsQuery = useExamPickerQuery(debouncedQ, saved && canViewExams, 100);

  const candidates = useMemo(() => {
    const map = new Map<string, { id: string; label: string }>();
    const trimmedFilter = debouncedQ.trim().toLowerCase();
    const matchesSearch = (text: string) =>
      !trimmedFilter || text.toLowerCase().includes(trimmedFilter);

    for (const item of scopeItems) {
      if (item.type === "EXAM") {
        const label =
          item.code && item.name ? `${item.code} - ${item.name}` : (item.name ?? item.code ?? item.id);
        if (matchesSearch(label)) {
          map.set(item.id, { id: item.id, label });
        }
      }
    }
    for (const warn of capWarnings) {
      if (!map.has(warn.examId) && matchesSearch(warn.examName)) {
        map.set(warn.examId, { id: warn.examId, label: warn.examName });
      }
    }
    for (const exam of examsQuery.data ?? []) {
      if (!map.has(exam.id)) {
        map.set(exam.id, { id: exam.id, label: `${exam.code} - ${exam.name}` });
      }
    }
    return Array.from(map.values());
  }, [capWarnings, debouncedQ, examsQuery.data, scopeItems]);

  const [selectedExamId, setSelectedExamId] = useState<string>("");
  const activeExamId = candidates.some((c) => c.id === selectedExamId)
    ? selectedExamId
    : (candidates[0]?.id ?? "");

  const previewQuery = usePricePreviewQuery(promotionId, activeExamId || undefined);
  const matchingCapWarning = capWarnings.find((w) => w.examId === activeExamId);

  const discountTotal = previewQuery.data
    ? Math.max(0, previewQuery.data.listPrice - previewQuery.data.finalPrice)
    : 0;

  return (
    <Card className="flex flex-col gap-3 p-4">
      <div className="flex flex-col gap-1">
        <h2 className="text-body font-semibold text-text">{t("pricePreview.title")}</h2>
        <p className="text-caption text-text-muted">{t("pricePreview.description")}</p>
      </div>

      {!saved ? (
        <div className="flex flex-col gap-2">
          <Label htmlFor={selectId}>{t("pricePreview.examLabel")}</Label>
          <Select
            id={selectId}
            disabled
            aria-describedby={reasonId}
            value=""
            onValueChange={() => {}}
            options={[]}
            placeholder={t("pricePreview.examPlaceholder")}
          />
          <p id={reasonId} className="flex items-start gap-1.5 text-caption text-text-secondary">
            <Lock aria-hidden className="mt-0.5 size-3.5 shrink-0 text-text-muted" />
            <span>{t("pricePreview.saveFirstReason")}</span>
          </p>
        </div>
      ) : (
        <div className="flex flex-col gap-3">
          {canViewExams && (
            <div className="relative">
              <Search
                aria-hidden
                className="pointer-events-none absolute top-1/2 left-3 size-4 -translate-y-1/2 text-text-muted"
              />
              <Input
                value={searchText}
                onChange={(event) => handleSearchChange(event.target.value)}
                placeholder={t("pricePreview.searchPlaceholder")}
                aria-label={t("pricePreview.searchAria")}
                className={cn("pl-9", searchText && "pr-9")}
              />
              {searchText && (
                <button
                  type="button"
                  aria-label={t("actions.clearSearch")}
                  onClick={() => handleSearchChange("")}
                  className={cn(
                    "absolute top-1/2 right-2 inline-flex size-7 -translate-y-1/2 items-center justify-center rounded-md text-text-muted hover:bg-subtle hover:text-text pointer-coarse:after:absolute pointer-coarse:after:-inset-2",
                    focusRing,
                  )}
                >
                  <X aria-hidden className="size-4" />
                </button>
              )}
            </div>
          )}

          {examsQuery.isError && candidates.length === 0 ? (
            <div
              role="alert"
              className="flex flex-col items-start gap-2 rounded-md border border-danger/30 bg-danger-bg px-3 py-2 text-caption text-danger"
            >
              <p>{resolvePromotionErrorMessage(examsQuery.error, tErrors)}</p>
              <Button type="button" variant="secondary" size="sm" onClick={() => void examsQuery.refetch()}>
                {t("actions.retry")}
              </Button>
            </div>
          ) : candidates.length === 0 ? (
            <p className="text-caption text-text-muted">
              {examsQuery.isLoading ? t("common.loading") : t("pricePreview.emptyExams")}
            </p>
          ) : (
            <>
              <div className="flex flex-col gap-1.5">
                <Label htmlFor={selectId}>{t("pricePreview.examLabel")}</Label>
                <div data-exam-content>
                  <Select
                    id={selectId}
                    value={activeExamId}
                    onValueChange={setSelectedExamId}
                    options={candidates.map((c) => ({
                      value: c.id,
                      textValue: c.label,
                      label: <span data-exam-content>{c.label}</span>,
                    }))}
                    placeholder={t("pricePreview.examPlaceholder")}
                  />
                </div>
              </div>

              {previewQuery.isLoading && (
                <div className="flex flex-col gap-2" aria-busy="true">
                  <Skeleton className="h-4 w-3/4" />
                  <Skeleton className="h-6 w-1/2" />
                </div>
              )}

              {previewQuery.isError && (
                <div
                  role="alert"
                  className="flex flex-col items-start gap-2 rounded-md border border-danger/30 bg-danger-bg px-3 py-2 text-caption text-danger"
                >
                  <p>{resolvePromotionErrorMessage(previewQuery.error, tErrors)}</p>
                  <Button
                    type="button"
                    variant="secondary"
                    size="sm"
                    onClick={() => void previewQuery.refetch()}
                  >
                    {t("actions.retry")}
                  </Button>
                </div>
              )}

              {previewQuery.data && (
                <dl className="flex flex-col gap-2 rounded-md border border-border bg-subtle p-3 text-caption">
                  <div className="flex items-center justify-between gap-2">
                    <dt className="text-text-muted">{t("pricePreview.listPrice")}</dt>
                    <dd className="font-medium text-text tabular-nums">
                      {formatVnd(previewQuery.data.listPrice, locale)}
                    </dd>
                  </div>
                  <div className="flex items-center justify-between gap-2">
                    <dt className="text-text-muted">{t("pricePreview.finalDiscountAmount")}</dt>
                    <dd className="font-medium text-primary-text tabular-nums">
                      -{formatVnd(discountTotal, locale)}
                    </dd>
                  </div>
                  {matchingCapWarning && (
                    <div className="flex items-center justify-between gap-2 rounded-sm border border-warning/30 bg-warning-bg px-2 py-1">
                      <dt className="font-medium text-warning">{t("pricePreview.floorPrice")}</dt>
                      <dd className="font-medium text-warning tabular-nums">
                        {formatVnd(matchingCapWarning.floorPrice, locale)}
                      </dd>
                    </div>
                  )}
                  <div className="flex items-center justify-between gap-2 border-t border-border pt-2">
                    <dt className="font-semibold text-text">{t("pricePreview.discountedPrice")}</dt>
                    <dd className="text-body font-semibold text-text tabular-nums">
                      {formatVnd(previewQuery.data.finalPrice, locale)}
                    </dd>
                  </div>
                  {matchingCapWarning && (
                    <div className="pt-1">
                      <StatusChip tone="warning">{t("pricePreview.floorBadge")}</StatusChip>
                    </div>
                  )}
                  {previewQuery.data.discountApplied === "NONE" && (
                    <p className="flex items-center gap-1.5 text-caption text-text-muted">
                      <AlertTriangle aria-hidden className="size-3.5 shrink-0" />
                      <span>{t("pricePreview.noDiscountReason")}</span>
                    </p>
                  )}
                </dl>
              )}
            </>
          )}
        </div>
      )}
    </Card>
  );
}
