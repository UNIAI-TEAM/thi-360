"use client";

import { Button, Card, cn, focusRing, FormField, Input } from "@thi360/ui";
import { Search, X } from "lucide-react";
import { useTranslations } from "next-intl";
import { useEffect, useId, useRef, useState, type RefObject } from "react";
import { FILTER_FORM, FILTER_SEARCH, FILTER_SELECT } from "@/features/users/components/list-filters";
import { ATTEMPT_STATES, type AttemptState } from "../types";
import {
  DEFAULT_ATTEMPT_FILTERS,
  hasActiveAttemptFilters,
  type AttemptFilters,
} from "./attempt-filters-state";

const SEARCH_DEBOUNCE_MS = 300;

export interface AttemptFiltersBarProps {
  filters: AttemptFilters;
  onChange: (patch: Partial<AttemptFilters>) => void;
  userInputRef?: RefObject<HTMLInputElement | null>;
}

interface ActiveFilter {
  key: string;
  label: string;
  clear: Partial<AttemptFilters>;
}

/**
 * Filter bar for admin attempt operations (`/attempts`, AC-10):
 * - Status radio strip (`All` + 7 `AttemptState` values)
 * - Debounced `user` (username/UUID) and `exam` (name/UUID) search inputs, `from`/`to` dates
 * - Active filter chips and "Clear all"
 */
export function AttemptFiltersBar({
  filters,
  onChange,
  userInputRef,
}: AttemptFiltersBarProps) {
  const t = useTranslations("attempts.filters");
  const statusT = useTranslations("attempts.status");
  const statusName = useId();
  const chipsRef = useRef<HTMLDivElement>(null);
  const localUserRef = useRef<HTMLInputElement>(null);
  const searchRef = userInputRef ?? localUserRef;

  const [userInput, setUserInput] = useState(filters.user);
  const [syncedUser, setSyncedUser] = useState(filters.user);
  if (syncedUser !== filters.user) {
    setSyncedUser(filters.user);
    setUserInput(filters.user);
  }

  const [examInput, setExamInput] = useState(filters.exam);
  const [syncedExam, setSyncedExam] = useState(filters.exam);
  if (syncedExam !== filters.exam) {
    setSyncedExam(filters.exam);
    setExamInput(filters.exam);
  }

  useEffect(() => {
    const trimmed = userInput.trim();
    if (trimmed === filters.user) return;
    if (trimmed === "") {
      onChange({ user: "" });
      return;
    }
    const timer = window.setTimeout(() => onChange({ user: trimmed }), SEARCH_DEBOUNCE_MS);
    return () => window.clearTimeout(timer);
  }, [userInput, filters.user, onChange]);

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

  const chip = (label: string, value: string) => t("chip", { label, value });
  const candidates: Array<ActiveFilter | null | false | ""> = [
    filters.state && {
      key: "state",
      label: chip(t("statusLegend"), statusT(filters.state)),
      clear: { state: null },
    },
    filters.user && {
      key: "user",
      label: chip(t("userSearch"), filters.user),
      clear: { user: "" },
    },
    filters.exam && {
      key: "exam",
      label: chip(t("examSearch"), filters.exam),
      clear: { exam: "" },
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

  function removeChip(index: number, clear: Partial<AttemptFilters>) {
    onChange(clear);
    if (clear.user !== undefined) setUserInput("");
    if (clear.exam !== undefined) setExamInput("");
    requestAnimationFrame(() => {
      const buttons = chipsRef.current?.querySelectorAll<HTMLButtonElement>("[data-chip-remove]") ?? [];
      const target = buttons[Math.min(index, buttons.length - 1)];
      (target ?? searchRef.current)?.focus();
    });
  }

  function resetAll() {
    setUserInput("");
    setExamInput("");
    onChange({ ...DEFAULT_ATTEMPT_FILTERS, size: filters.size });
    requestAnimationFrame(() => searchRef.current?.focus());
  }

  const statusOptions: Array<{ value: AttemptState | null; label: string }> = [
    { value: null, label: t("statusAll") },
    ...ATTEMPT_STATES.map((state) => ({
      value: state,
      label: statusT(state),
    })),
  ];

  return (
    <div role="search" className="block">
      <Card padding="md" className="@container">
        <form
          aria-label={t("ariaLabel")}
          onSubmit={(event) => {
            event.preventDefault();
            onChange({ user: userInput.trim(), exam: examInput.trim() });
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
                        "relative flex h-10 items-center gap-2 rounded-md px-3 text-label font-medium whitespace-nowrap text-text-muted sm:px-3.5",
                        "transition-colors duration-150 motion-reduce:transition-none hover:text-text",
                        "peer-checked:bg-surface peer-checked:text-primary-text peer-checked:shadow-sm",
                        "after:pointer-events-none after:absolute after:inset-x-2.5 after:bottom-1 peer-checked:after:border-b-2 peer-checked:after:border-current sm:after:inset-x-3",
                        "peer-focus-visible:outline-3 peer-focus-visible:outline-offset-2 peer-focus-visible:outline-primary-text",
                      )}
                    >
                      {option.label}
                    </span>
                  </label>
                ))}
            </div>
          </fieldset>

          <div className={FILTER_FORM}>
            <FormField label={t("userSearch")} className={FILTER_SEARCH}>
              {(controlProps) => (
                <Input
                  {...controlProps}
                  ref={searchRef}
                  type="search"
                  name="user"
                  leadingIcon={<Search />}
                  value={userInput}
                  onChange={(event) => setUserInput(event.target.value)}
                  placeholder={t("userSearchPlaceholder")}
                  autoComplete="off"
                  autoCorrect="off"
                  spellCheck={false}
                />
              )}
            </FormField>

            <FormField label={t("examSearch")} className={FILTER_SEARCH}>
              {(controlProps) => (
                <Input
                  {...controlProps}
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

          {hasActiveAttemptFilters(filters) && (
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
    </div>
  );
}
