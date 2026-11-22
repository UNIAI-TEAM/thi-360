"use client";

import { Button, Card, cn, focusRing, FormField, Input, Select } from "@thi360/ui";
import { pickName } from "@thi360/web-kit/i18n";
import { normalizeUiLocale } from "@thi360/web-kit/preferences";
import { Search, X } from "lucide-react";
import { useFormatter, useLocale, useTranslations } from "next-intl";
import { useEffect, useId, useRef, useState, type RefObject } from "react";
import { FILTER_FORM, FILTER_SEARCH, FILTER_SELECT } from "@/features/users/components/list-filters";
import {
  GRADING_QUEUE_STATES,
  type ExamKindCatalogOption,
  type GradingQueueCountsResponse,
  type GradingQueueState,
} from "../types";
import {
  DEFAULT_GRADING_FILTERS,
  hasActiveGradingFilters,
  type GradingFilters,
} from "./grading-filters-state";

const ALL = "__all__";
const SEARCH_DEBOUNCE_MS = 300;

export interface GradingFiltersBarProps {
  filters: GradingFilters;
  onChange: (patch: Partial<GradingFilters>) => void;
  examKinds: readonly ExamKindCatalogOption[] | undefined;
  counts: GradingQueueCountsResponse | undefined;
  searchInputRef?: RefObject<HTMLInputElement | null>;
}

interface ActiveFilter {
  key: string;
  label: string;
  clear: Partial<GradingFilters>;
}

export function resolveExamKindLabel(
  code: string | undefined,
  examKinds: readonly ExamKindCatalogOption[] | undefined,
  uiLocale: "vi" | "en",
): string {
  if (!code) return "";
  const found = examKinds?.find((kind) => kind.code === code);
  if (!found) return code;
  return pickName({ nameVi: found.nameVi, nameEn: found.nameEn }, uiLocale) || code;
}

/**
 * Filter bar for the anonymous essay grading queue (`/grading`):
 * - Status strip backed by `GET /api/v1/admin/grading/items/counts` (total, waiting, inProgress, done)
 * - Debounced exam name/UUID search (`exam`), bilingual exam kind (`kind`), and `from`/`to` date range
 * - Active filter chips and "Clear all filters"
 */
export function GradingFiltersBar({
  filters,
  onChange,
  examKinds,
  counts,
  searchInputRef,
}: GradingFiltersBarProps) {
  const t = useTranslations("grading.filters");
  const statusT = useTranslations("grading.status");
  const format = useFormatter();
  const uiLocale = normalizeUiLocale(useLocale());
  const statusName = useId();
  const chipsRef = useRef<HTMLDivElement>(null);
  const localSearchRef = useRef<HTMLInputElement>(null);
  const searchRef = searchInputRef ?? localSearchRef;

  const [examInput, setExamInput] = useState(filters.exam);
  const [syncedExam, setSyncedExam] = useState(filters.exam);
  if (syncedExam !== filters.exam) {
    setSyncedExam(filters.exam);
    setExamInput(filters.exam);
  }

  useEffect(() => {
    const trimmed = examInput.trim();
    if (trimmed === filters.exam) return;
    if (trimmed === "") {
      onChange({ exam: "" });
      return;
    }
    const timer = window.setTimeout(() => onChange({ exam: trimmed }), SEARCH_DEBOUNCE_MS);
    return () => window.clearTimeout(timer);
  }, [examInput, filters.exam, onChange]);

  const kinds = examKinds ?? [];
  const kindLabel = (code: string) => resolveExamKindLabel(code, kinds, uiLocale);

  const kindOptions = [
    { value: ALL, label: t("all") },
    ...kinds.map((kind) => ({
      value: kind.code,
      label: pickName({ nameVi: kind.nameVi, nameEn: kind.nameEn }, uiLocale) || kind.code,
    })),
  ];
  if (filters.kind && !kinds.some((k) => k.code === filters.kind)) {
    kindOptions.push({ value: filters.kind, label: filters.kind });
  }

  const fromSelect = (value: string) => (value === ALL ? null : value);

  const chip = (label: string, value: string) => t("chip", { label, value });
  const candidates: Array<ActiveFilter | null | false | ""> = [
    filters.state && {
      key: "state",
      label: chip(t("statusLegend"), statusT(filters.state)),
      clear: { state: null },
    },
    filters.exam && {
      key: "exam",
      label: chip(t("examSearch"), filters.exam),
      clear: { exam: "" },
    },
    filters.kind && {
      key: "kind",
      label: chip(t("kind"), kindLabel(filters.kind)),
      clear: { kind: null },
    },
    filters.from && {
      key: "from",
      label: chip(t("from"), filters.from),
      clear: { from: "" },
    },
    filters.to && {
      key: "to",
      label: chip(t("to"), filters.to),
      clear: { to: "" },
    },
  ];
  const active = candidates.filter((item): item is ActiveFilter => Boolean(item));

  function removeChip(index: number, clear: Partial<GradingFilters>) {
    onChange(clear);
    if (clear.exam !== undefined) setExamInput("");
    requestAnimationFrame(() => {
      const buttons = chipsRef.current?.querySelectorAll<HTMLButtonElement>("[data-chip-remove]") ?? [];
      const target = buttons[Math.min(index, buttons.length - 1)];
      (target ?? searchRef.current)?.focus();
    });
  }

  function resetAll() {
    setExamInput("");
    onChange({ ...DEFAULT_GRADING_FILTERS, size: filters.size });
    requestAnimationFrame(() => searchRef.current?.focus());
  }

  const countByState: Record<GradingQueueState, number | undefined> = {
    WAITING: counts?.waiting,
    IN_PROGRESS: counts?.inProgress,
    DONE: counts?.done,
  };

  const statusOptions: Array<{
    value: GradingQueueState | null;
    label: string;
    count: number | undefined;
  }> = [
    { value: null, label: t("statusAll"), count: counts?.total },
    ...GRADING_QUEUE_STATES.map((state) => ({
      value: state,
      label: statusT(state),
      count: countByState[state],
    })),
  ];

  return (
    <search className="block">
      <Card padding="md" className="@container">
        <form
          aria-label={t("ariaLabel")}
          onSubmit={(event) => {
            event.preventDefault();
            onChange({ exam: examInput.trim() });
          }}
          className="flex flex-col gap-3"
        >
          <fieldset className="m-0 min-w-0 border-0 p-0">
            <legend className="sr-only">{t("statusLegend")}</legend>
            <div className="flex w-fit max-w-full flex-wrap gap-1 rounded-lg bg-subtle p-1">
                {statusOptions.map((option) => (
                  <label key={option.value ?? "all"} className="relative shrink-0 cursor-pointer">
                    <input
                      type="radio"
                      name={statusName}
                      value={option.value ?? ""}
                      checked={filters.state === option.value}
                      onChange={() => onChange({ state: option.value })}
                      className="peer sr-only"
                    />
                    <span
                      className={cn(
                        "relative flex h-10 items-center gap-2 rounded-md px-3 text-label font-medium whitespace-nowrap text-text-muted pointer-coarse:min-h-11 pointer-coarse:min-w-11 pointer-coarse:justify-center sm:px-3.5",
                        "transition-colors duration-150 motion-reduce:transition-none hover:text-text",
                        "peer-checked:bg-surface peer-checked:text-primary-text peer-checked:shadow-sm",
                        "after:pointer-events-none after:absolute after:inset-x-2.5 after:bottom-1 peer-checked:after:border-b-2 peer-checked:after:border-current sm:after:inset-x-3",
                        "peer-focus-visible:outline-3 peer-focus-visible:outline-offset-2 peer-focus-visible:outline-primary-text",
                      )}
                    >
                      {option.label}
                      {option.count !== undefined && (
                        <span className="rounded-full bg-neutral-bg px-2 text-caption text-neutral tabular-nums">
                          {format.number(option.count)}
                        </span>
                      )}
                    </span>
                  </label>
                ))}
            </div>
          </fieldset>

          <div className={FILTER_FORM}>
            <FormField label={t("examSearch")} className={FILTER_SEARCH}>
              {(controlProps) => (
                <Input
                  {...controlProps}
                  ref={searchRef}
                  type="search"
                  name="exam"
                  leadingIcon={<Search />}
                  value={examInput}
                  onChange={(event) => setExamInput(event.target.value)}
                  placeholder={t("examSearchPlaceholder")}
                  autoComplete="off"
                  autoCorrect="off"
                  spellCheck={false}
                />
              )}
            </FormField>

            <FormField label={t("kind")} className={cn(FILTER_SELECT, "@3xl:min-w-48")}>
              {(controlProps) => (
                <Select
                  {...controlProps}
                  name="kind"
                  options={kindOptions}
                  value={filters.kind ?? ALL}
                  onValueChange={(value) => onChange({ kind: fromSelect(value) })}
                />
              )}
            </FormField>

            <FormField label={t("from")} className={cn(FILTER_SELECT, "@3xl:min-w-40")}>
              {(controlProps) => (
                <Input
                  {...controlProps}
                  type="date"
                  name="from"
                  value={filters.from}
                  max={filters.to || undefined}
                  onChange={(event) => onChange({ from: event.target.value })}
                />
              )}
            </FormField>

            <FormField label={t("to")} className={cn(FILTER_SELECT, "@3xl:min-w-40")}>
              {(controlProps) => (
                <Input
                  {...controlProps}
                  type="date"
                  name="to"
                  value={filters.to}
                  min={filters.from || undefined}
                  onChange={(event) => onChange({ to: event.target.value })}
                />
              )}
            </FormField>
          </div>

          {hasActiveGradingFilters(filters) && (
            <div
              ref={chipsRef}
              role="group"
              aria-label={t("activeLabel")}
              className="flex flex-wrap items-center gap-2 border-t border-border pt-3"
            >
              <span aria-hidden className="text-caption font-medium text-text-muted">
                {t("activeLabel")}
              </span>
              {active.map((item, index) => (
                <span
                  key={item.key}
                  className="inline-flex h-8 max-w-full items-center gap-1 rounded-full border border-primary-200 bg-primary-50 ps-3 pe-1 text-caption font-medium text-primary-text"
                >
                  <span className="min-w-0 truncate">{item.label}</span>
                  <button
                    type="button"
                    data-chip-remove
                    aria-label={t("remove", { label: item.label })}
                    onClick={() => removeChip(index, item.clear)}
                    className={cn(
                      "relative grid size-6 shrink-0 place-items-center rounded-full hover:bg-primary-100",
                      "pointer-coarse:after:absolute pointer-coarse:after:-inset-2.5",
                      focusRing,
                    )}
                  >
                    <X aria-hidden className="size-3.5" />
                  </button>
                </span>
              ))}
              <Button type="button" variant="link" size="sm" onClick={resetAll}>
                {t("clearAll")}
              </Button>
            </div>
          )}
        </form>
      </Card>
    </search>
  );
}
