"use client";

import { Button, Dialog, DialogContent, DialogHeader, DialogTitle, cn, focusRing } from "@thi360/ui";
import { ChevronLeft, ChevronRight, Flag, LayoutGrid } from "lucide-react";
import { useTranslations } from "next-intl";
import { useMemo, useState } from "react";
import type { ItemCompletionStatus } from "./mappers";

export type QuestionNavFilter = "all" | "unanswered" | "flagged";

export interface QuestionNavItem {
  itemId: string;
  displayNo: number;
  unitIndex: number;
  completion: ItemCompletionStatus;
  flagged: boolean;
  unsaved: boolean;
  isCurrent: boolean;
}

export interface QuestionNavProps {
  items: QuestionNavItem[];
  currentUnitIndex: number;
  totalUnits: number;
  currentDisplayNumber: number;
  totalDisplayItems: number;
  onSelectUnit: (unitIndex: number) => void;
  onPrev: () => void;
  onNext: () => void;
  canPrev: boolean;
  canNext: boolean;
}

interface QuestionNavGridProps {
  items: QuestionNavItem[];
  filter: QuestionNavFilter;
  onChangeFilter: (filter: QuestionNavFilter) => void;
  onSelectUnit: (unitIndex: number) => void;
}

function QuestionNavGrid({
  items,
  filter,
  onChangeFilter,
  onSelectUnit,
}: QuestionNavGridProps) {
  const t = useTranslations("attempt.nav");

  const answeredCount = useMemo(
    () => items.filter((item) => item.completion === "complete").length,
    [items],
  );

  const filteredItems = useMemo(() => {
    if (filter === "unanswered") {
      return items.filter((item) => item.completion !== "complete");
    }
    if (filter === "flagged") {
      return items.filter((item) => item.flagged);
    }
    return items;
  }, [filter, items]);

  function buildItemAriaLabel(item: QuestionNavItem): string {
    const parts: string[] = [`${item.displayNo}`];
    if (item.isCurrent) parts.push(t("stateCurrent"));
    if (item.completion === "complete") {
      parts.push(t("stateAnswered"));
    } else if (item.completion === "partial") {
      parts.push(t("statePartial"));
    } else {
      parts.push(t("stateBlank"));
    }
    parts.push(item.unsaved ? t("stateUnsaved") : t("stateSaved"));
    if (item.flagged) parts.push(t("stateFlagged"));
    return parts.join(", ");
  }

  return (
    <div className="flex flex-col gap-4">
      <div className="flex items-center justify-between gap-2">
        <span className="text-caption font-semibold text-text-secondary">
          {t("answeredCount", { answered: answeredCount, total: items.length })}
        </span>
      </div>

      <div
        role="group"
        aria-label={t("gridLabel")}
        className="flex flex-wrap gap-1.5"
      >
        {(["all", "unanswered", "flagged"] as const).map((filterKey) => {
          const isActive = filter === filterKey;
          const label =
            filterKey === "all"
              ? t("filterAll")
              : filterKey === "unanswered"
                ? t("filterUnanswered")
                : t("filterFlagged");

          return (
            <button
              key={filterKey}
              type="button"
              aria-pressed={isActive}
              onClick={() => onChangeFilter(filterKey)}
              className={cn(
                "rounded-md border px-2.5 py-1 text-caption font-medium transition-colors pointer-coarse:min-h-11 pointer-coarse:min-w-11",
                isActive
                  ? "border-primary-600 bg-primary-50 text-primary-800"
                  : "border-border bg-surface text-text-secondary hover:bg-subtle",
                focusRing,
              )}
            >
              {label}
            </button>
          );
        })}
      </div>

      {filteredItems.length === 0 ? (
        <p className="rounded-lg border border-border bg-subtle px-3 py-4 text-center text-caption text-text-muted">
          {t("emptyFiltered")}
        </p>
      ) : (
        <div
          aria-label={t("gridLabel")}
          className="grid grid-cols-5 gap-2"
        >
          {filteredItems.map((item) => (
            <button
              key={item.itemId}
              type="button"
              data-completion={item.completion}
              data-flagged={item.flagged ? "true" : "false"}
              data-unsaved={item.unsaved ? "true" : "false"}
              aria-current={item.isCurrent ? "step" : undefined}
              aria-label={buildItemAriaLabel(item)}
              onClick={() => onSelectUnit(item.unitIndex)}
              className={cn(
                "relative inline-flex h-9 items-center justify-center rounded-lg border text-label font-semibold tabular-nums transition-colors pointer-coarse:min-h-11 pointer-coarse:min-w-11",
                item.completion === "complete" &&
                  "border-primary-600 bg-primary-fill text-on-primary",
                item.completion === "partial" &&
                  "border-primary-600 border-dashed bg-primary-50 text-primary-800",
                item.completion === "blank" &&
                  "border-border bg-surface text-text hover:border-border-strong hover:bg-subtle",
                item.isCurrent && "ring-2 ring-primary-700 ring-offset-2 ring-offset-surface",
                focusRing,
              )}
            >
              <span>{item.displayNo}</span>

              {item.unsaved ? (
                <span
                  aria-hidden="true"
                  className="absolute -top-1 -right-1 size-2.5 rounded-full border border-surface bg-warning"
                />
              ) : null}

              {item.flagged ? (
                <Flag
                  aria-hidden="true"
                  className={cn(
                    "absolute bottom-0.5 right-0.5 size-2.5",
                    item.completion === "complete" ? "text-gold-400" : "text-warning",
                  )}
                />
              ) : null}
            </button>
          ))}
        </div>
      )}

      <dl className="grid grid-cols-2 gap-x-3 gap-y-1.5 border-t border-border pt-3 text-caption text-text-muted">
        <div className="flex items-center gap-1.5">
          <dt className="size-3 rounded-sm border-2 border-primary-700 bg-surface" aria-hidden="true" />
          <dd>{t("legendCurrent")}</dd>
        </div>
        <div className="flex items-center gap-1.5">
          <dt className="size-3 rounded-sm bg-primary-fill" aria-hidden="true" />
          <dd>{t("legendAnswered")}</dd>
        </div>
        <div className="flex items-center gap-1.5">
          <dt className="size-3 rounded-sm border border-dashed border-primary-600 bg-primary-50" aria-hidden="true" />
          <dd>{t("legendPartial")}</dd>
        </div>
        <div className="flex items-center gap-1.5">
          <dt className="size-3 rounded-sm border border-border bg-surface" aria-hidden="true" />
          <dd>{t("legendBlank")}</dd>
        </div>
        <div className="flex items-center gap-1.5">
          <dt className="size-2.5 rounded-full bg-warning" aria-hidden="true" />
          <dd>{t("legendUnsaved")}</dd>
        </div>
        <div className="flex items-center gap-1.5">
          <dt className="inline-flex items-center text-warning" aria-hidden="true">
            <Flag className="size-3" />
          </dt>
          <dd>{t("legendFlagged")}</dd>
        </div>
      </dl>
    </div>
  );
}

export function QuestionNav({
  items,
  currentUnitIndex,
  totalUnits,
  currentDisplayNumber,
  totalDisplayItems,
  onSelectUnit,
  onPrev,
  onNext,
  canPrev,
  canNext,
}: QuestionNavProps) {
  const t = useTranslations("attempt.nav");
  const dialogT = useTranslations("attempt.dialogs");
  const [filter, setFilter] = useState<QuestionNavFilter>("all");
  const [isDrawerOpen, setIsDrawerOpen] = useState(false);

  return (
    <>
      <div
        role="region"
        aria-label={t("controlsLabel")}
        className="flex flex-wrap items-center justify-between gap-2 border-t border-border bg-surface px-4 py-3"
      >
        <Button
          type="button"
          variant="secondary"
          size="sm"
          className="pointer-coarse:min-h-11"
          disabled={!canPrev}
          onClick={onPrev}
        >
          <ChevronLeft className="size-4" aria-hidden="true" />
          <span>{t("prev")}</span>
        </Button>

        <div className="flex items-center gap-2">
          <Button
            type="button"
            variant="secondary"
            size="sm"
            aria-expanded={isDrawerOpen}
            className="pointer-coarse:min-h-11 lg:hidden"
            onClick={() => setIsDrawerOpen(true)}
          >
            <LayoutGrid className="size-4" aria-hidden="true" />
            <span>
              {t("openGrid", {
                current: Math.max(1, currentDisplayNumber),
                total: Math.max(1, totalDisplayItems),
              })}
            </span>
          </Button>

          <span className="hidden text-caption font-medium text-text-muted tabular-nums lg:inline">
            {t("questionPosition", {
              current: Math.min(totalUnits, currentUnitIndex + 1),
              total: Math.max(1, totalUnits),
            })}
          </span>
        </div>

        <Button
          type="button"
          variant="secondary"
          size="sm"
          className="pointer-coarse:min-h-11"
          disabled={!canNext}
          onClick={onNext}
        >
          <span>{t("next")}</span>
          <ChevronRight className="size-4" aria-hidden="true" />
        </Button>
      </div>

      {!isDrawerOpen ? (
        <aside
          aria-label={t("gridLabel")}
          className="hidden w-72 shrink-0 rounded-xl border border-border bg-surface p-4 lg:block"
        >
          <QuestionNavGrid
            items={items}
            filter={filter}
            onChangeFilter={setFilter}
            onSelectUnit={onSelectUnit}
          />
        </aside>
      ) : null}

      <Dialog open={isDrawerOpen} onOpenChange={setIsDrawerOpen}>
        <DialogContent size="md" closeLabel={dialogT("closeLabel")}>
          <DialogHeader>
            <DialogTitle>{t("gridLabel")}</DialogTitle>
          </DialogHeader>
          <QuestionNavGrid
            items={items}
            filter={filter}
            onChangeFilter={setFilter}
            onSelectUnit={(unitIndex) => {
              onSelectUnit(unitIndex);
              setIsDrawerOpen(false);
            }}
          />
          <div className="flex justify-end pt-2">
            <Button
              type="button"
              variant="secondary"
              size="sm"
              className="pointer-coarse:min-h-11"
              onClick={() => setIsDrawerOpen(false)}
            >
              {t("closeGrid")}
            </Button>
          </div>
        </DialogContent>
      </Dialog>
    </>
  );
}
