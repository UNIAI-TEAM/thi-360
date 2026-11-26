"use client";

import { Button, Dialog, DialogContent, DialogHeader, DialogTitle, cn, focusRing } from "@thi360/ui";
import { Flag, LayoutGrid } from "lucide-react";
import { useTranslations } from "next-intl";
import { useId, useMemo, useState, type ReactNode } from "react";
import { createPortal } from "react-dom";
import type { ItemCompletionStatus } from "./mappers";
import { type AttemptDisplaySegmentView, groupNavItemsBySegment } from "./segments/segment-units";

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
  segments?: AttemptDisplaySegmentView[];
  /**
   * Controls whether `data-exam-content` is rendered on segment group headings.
   * Defaults to `true` for author-defined `displaySegments`; pass `false` (or a set via
   * `examContentSegmentCodes`) when segments represent UI-localized subject names.
   */
  segmentsAreExamContent?: boolean;
  examContentSegmentCodes?: ReadonlySet<string>;
  currentUnitIndex: number;
  totalUnits: number;
  currentDisplayNumber: number;
  totalDisplayItems: number;
  onSelectUnit: (unitIndex: number) => void;
  onSelectItem?: (item: QuestionNavItem) => void;
  onPrev?: () => void;
  onNext?: () => void;
  canPrev?: boolean;
  canNext?: boolean;
  children?: ReactNode;
  footer?: ReactNode;
  /**
   * Chỗ trong đầu trang dính của màn làm bài (UNI-2826). Khi có, thanh "đã trả lời · danh sách câu" (dưới `lg`) được gắn
   * vào đó bằng portal để luôn thấy khi cuộn; khi chưa có, thanh nằm đầu cột nội dung như cũ.
   */
  controlsBarContainer?: HTMLElement | null;
}

interface QuestionNavGridProps {
  items: QuestionNavItem[];
  segments?: AttemptDisplaySegmentView[];
  segmentsAreExamContent?: boolean;
  examContentSegmentCodes?: ReadonlySet<string>;
  filter: QuestionNavFilter;
  onChangeFilter: (filter: QuestionNavFilter) => void;
  onSelectUnit: (unitIndex: number) => void;
  onSelectItem?: (item: QuestionNavItem) => void;
}

function QuestionNavGrid({
  items,
  segments,
  segmentsAreExamContent = true,
  examContentSegmentCodes,
  filter,
  onChangeFilter,
  onSelectUnit,
  onSelectItem,
}: QuestionNavGridProps) {
  const t = useTranslations("attempt.nav");
  const segT = useTranslations("attempt.segments");
  const groupIdPrefix = useId();

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

  const segmentGroups = useMemo(() => {
    if (!segments || segments.length === 0) return null;
    return groupNavItemsBySegment(filteredItems, segments, items);
  }, [filteredItems, items, segments]);

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

  function renderNavItemButton(item: QuestionNavItem) {
    return (
      <button
        key={item.itemId}
        type="button"
        data-completion={item.completion}
        data-flagged={item.flagged ? "true" : "false"}
        data-unsaved={item.unsaved ? "true" : "false"}
        aria-current={item.isCurrent ? "step" : undefined}
        aria-label={buildItemAriaLabel(item)}
        onClick={() => {
          onSelectUnit(item.unitIndex);
          onSelectItem?.(item);
        }}
        className={cn(
          "relative inline-flex h-9 items-center justify-center rounded-lg border text-label font-semibold tabular-nums transition-colors pointer-coarse:min-h-11 pointer-coarse:min-w-11",
          item.flagged
            ? [
                item.completion === "complete" &&
                  "border-danger bg-danger-solid text-on-danger hover:bg-danger-solid-hover",
                item.completion === "partial" &&
                  "border-danger border-dashed bg-danger-bg text-danger",
                item.completion === "blank" &&
                  "border-danger bg-danger-bg text-danger hover:border-danger-solid",
              ]
            : [
                item.completion === "complete" &&
                  "border-primary-600 bg-primary-fill text-on-primary",
                item.completion === "partial" &&
                  "border-primary-600 border-dashed bg-primary-50 text-primary-800",
                item.completion === "blank" &&
                  "border-border bg-surface text-text hover:border-border-strong hover:bg-subtle",
              ],
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
              "absolute bottom-0.5 right-0.5 size-2.5 fill-current",
              item.completion === "complete" ? "text-on-danger" : "text-danger",
            )}
          />
        ) : null}
      </button>
    );
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
      ) : segmentGroups ? (
        <div aria-label={t("gridLabel")} className="flex flex-col gap-3">
          {segmentGroups.map((group, groupIdx) => {
            const headingId = `${groupIdPrefix}-seg-${groupIdx}`;
            return (
              <div
                key={group.segment?.code ?? `seg-${groupIdx}`}
                role="group"
                aria-labelledby={headingId}
                className="flex flex-col gap-1.5"
              >
                {group.segment ? (
                  <div
                    id={headingId}
                    {...((
                      examContentSegmentCodes
                        ? Boolean(group.segment.code && examContentSegmentCodes.has(group.segment.code))
                        : segmentsAreExamContent
                    )
                      ? { "data-exam-content": "" }
                      : {})}
                    className="text-caption font-semibold text-text-secondary"
                  >
                    {group.segment.name || group.segment.code}
                  </div>
                ) : (
                  <div
                    id={headingId}
                    className="text-caption font-semibold text-text-secondary"
                  >
                    {segT("ungrouped")}
                  </div>
                )}
                <div className="grid grid-cols-5 gap-2">
                  {group.items.map(renderNavItemButton)}
                </div>
              </div>
            );
          })}
        </div>
      ) : (
        <div
          aria-label={t("gridLabel")}
          className="grid grid-cols-5 gap-2"
        >
          {filteredItems.map(renderNavItemButton)}
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
          <dt
            data-legend="flagged"
            className="inline-flex size-3.5 items-center justify-center rounded-sm border border-danger bg-danger-bg text-danger"
            aria-hidden="true"
          >
            <Flag className="size-2.5 fill-current" />
          </dt>
          <dd>{t("legendFlagged")}</dd>
        </div>
      </dl>
    </div>
  );
}

export function QuestionNav({
  items,
  segments,
  segmentsAreExamContent = true,
  examContentSegmentCodes,
  currentDisplayNumber,
  totalDisplayItems,
  onSelectUnit,
  onSelectItem,
  children,
  footer,
  controlsBarContainer,
}: QuestionNavProps) {
  const t = useTranslations("attempt.nav");
  const dialogT = useTranslations("attempt.dialogs");
  const [filter, setFilter] = useState<QuestionNavFilter>("all");
  const [isDrawerOpen, setIsDrawerOpen] = useState(false);

  const answeredCount = useMemo(
    () => items.filter((item) => item.completion === "complete").length,
    [items],
  );

  const inHeader = Boolean(controlsBarContainer);
  const controlsBarElement = (
    <div
      role="region"
      aria-label={t("controlsLabel")}
      className={cn(
        "flex items-center justify-between gap-2 lg:hidden",
        inHeader
          ? "mx-auto w-full px-4 py-1.5"
          : "rounded-xl border border-border bg-surface px-3 py-2",
      )}
    >
      <span className="text-caption font-semibold text-text-secondary tabular-nums">
        {t("answeredCount", { answered: answeredCount, total: items.length })}
      </span>

      <Button
        type="button"
        variant="secondary"
        size="sm"
        aria-expanded={isDrawerOpen}
        aria-label={t("openGrid", {
          current: Math.max(1, currentDisplayNumber),
          total: Math.max(1, totalDisplayItems),
        })}
        className="shrink-0 whitespace-nowrap px-2.5 text-caption pointer-coarse:min-h-11 sm:px-3 sm:text-label [&>span]:gap-1.5 sm:[&>span]:gap-2"
        onClick={() => setIsDrawerOpen(true)}
      >
        <LayoutGrid className="size-4" aria-hidden="true" />
        <span aria-hidden="true" className="tabular-nums">
          {t("openGridCompact", {
            current: Math.max(1, currentDisplayNumber),
            total: Math.max(1, totalDisplayItems),
          })}
        </span>
      </Button>
    </div>
  );
  const controlsBar = controlsBarContainer ? createPortal(controlsBarElement, controlsBarContainer) : controlsBarElement;

  const sidebar = !isDrawerOpen ? (
    <aside
      aria-label={t("gridLabel")}
      className="hidden shrink-0 rounded-xl border border-border bg-surface p-4 lg:sticky lg:top-[var(--attempt-sticky-top,7rem)] lg:block lg:w-[282px] lg:max-h-[calc(100dvh-var(--attempt-sticky-top,7rem)-1.5rem)] lg:overflow-y-auto min-[1440px]:w-[300px] scroll-shadows-y"
    >
      <QuestionNavGrid
        items={items}
        segments={segments}
        segmentsAreExamContent={segmentsAreExamContent}
        examContentSegmentCodes={examContentSegmentCodes}
        filter={filter}
        onChangeFilter={setFilter}
        onSelectUnit={onSelectUnit}
        onSelectItem={onSelectItem}
      />
    </aside>
  ) : null;

  const dialog = (
    <Dialog open={isDrawerOpen} onOpenChange={setIsDrawerOpen}>
      <DialogContent size="md" closeLabel={dialogT("closeLabel")}>
        <DialogHeader>
          <DialogTitle>{t("gridLabel")}</DialogTitle>
        </DialogHeader>
        <QuestionNavGrid
          items={items}
          segments={segments}
          segmentsAreExamContent={segmentsAreExamContent}
          examContentSegmentCodes={examContentSegmentCodes}
          filter={filter}
          onChangeFilter={setFilter}
          onSelectUnit={(unitIndex) => {
            onSelectUnit(unitIndex);
            setIsDrawerOpen(false);
          }}
          onSelectItem={(item) => {
            setIsDrawerOpen(false);
            onSelectItem?.(item);
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
  );

  if (children !== undefined) {
    return (
      <div className="flex flex-1 flex-col gap-5 lg:flex-row lg:items-start min-[1440px]:gap-6">
        <div className="min-w-0 flex-1 space-y-4">
          {controlsBar}
          {children}
          {footer}
        </div>
        {sidebar}
        {dialog}
      </div>
    );
  }

  return (
    <>
      {controlsBar}
      {sidebar}
      {dialog}
    </>
  );
}
