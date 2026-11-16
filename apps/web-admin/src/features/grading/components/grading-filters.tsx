"use client";

import { Button, Card, cn, focusRing, FormField, Input, Select } from "@thi360/ui";
import { Search, X } from "lucide-react";
import { useFormatter, useTranslations } from "next-intl";
import { useEffect, useId, useRef, useState, type RefObject } from "react";
import type { ContentReference } from "@/features/content/types";
import { FILTER_FORM, FILTER_SEARCH, FILTER_SELECT } from "@/features/users/components/list-filters";
import {
  GRADING_STATUSES,
  UNASSIGNED_GRADER,
  type GradingFilterOptions,
  type GradingStatus,
  type GradingStatusCounts,
} from "../types";
import { DEFAULT_GRADING_FILTERS, hasActiveGradingFilters, type GradingFilters } from "./grading-filters-state";

const ALL = "__all__";
const SEARCH_DEBOUNCE_MS = 300;

export interface GradingFiltersBarProps {
  filters: GradingFilters;
  onChange: (patch: Partial<GradingFilters>) => void;
  reference: ContentReference | undefined;
  options: GradingFilterOptions | undefined;
  /** Số lượt theo trạng thái với bộ lọc hiện tại; chưa có thì ẩn số. */
  counts: GradingStatusCounts | undefined;
  searchInputRef?: RefObject<HTMLInputElement | null>;
}

interface ActiveFilter {
  key: string;
  label: string;
  clear: Partial<GradingFilters>;
}

/**
 * Thanh lọc của hàng chờ chấm: dải trạng thái kèm số lượt (Tất cả, Đang chấm, Đã có điểm), hàng lọc (tìm học viên hoặc
 * mã bài, kỳ thi, bài thi, người chấm), rồi chip "Đang lọc" bỏ từng điều kiện và nút xóa tất cả (DESIGN.md mục 5.14).
 * Đổi kỳ thi thì bỏ bài thi đã chọn nếu bài đó không thuộc kỳ thi mới.
 */
export function GradingFiltersBar({ filters, onChange, reference, options, counts, searchInputRef }: GradingFiltersBarProps) {
  const t = useTranslations("grading.filters");
  const statusT = useTranslations("grading.status");
  const format = useFormatter();
  const statusName = useId();
  const chipsRef = useRef<HTMLDivElement>(null);
  const localSearchRef = useRef<HTMLInputElement>(null);
  const searchRef = searchInputRef ?? localSearchRef;

  // Ô tìm gửi sau 300ms ngừng gõ; xóa trống thì áp dụng ngay. Bộ lọc đổi từ ngoài thì ô theo giá trị mới.
  const [searchInput, setSearchInput] = useState(filters.q);
  const [syncedQ, setSyncedQ] = useState(filters.q);
  if (syncedQ !== filters.q) {
    setSyncedQ(filters.q);
    setSearchInput(filters.q);
  }
  useEffect(() => {
    const trimmed = searchInput.trim();
    if (trimmed === filters.q) return;
    if (trimmed === "") {
      onChange({ q: "" });
      return;
    }
    const timer = setTimeout(() => onChange({ q: trimmed }), SEARCH_DEBOUNCE_MS);
    return () => clearTimeout(timer);
  }, [searchInput, filters.q, onChange]);

  const kinds = reference?.examKinds ?? [];
  const kindName = (code: string) => kinds.find((kind) => kind.code === code)?.name ?? code;
  const exams = options?.exams ?? [];
  const examsOfKind = filters.kind ? exams.filter((exam) => exam.kindCode === filters.kind) : exams;
  const examLabel = (id: string) => {
    const exam = exams.find((item) => item.id === id);
    return exam ? t("examOption", { code: exam.code, name: exam.name }) : id;
  };
  const graders = options?.graders ?? [];
  const graderName = (id: string) =>
    id === UNASSIGNED_GRADER ? t("graderUnassigned") : (graders.find((person) => person.id === id)?.name ?? id);

  const kindOptions = [{ value: ALL, label: t("all") }, ...kinds.map((kind) => ({ value: kind.code, label: kind.name }))];
  const examOptions = [
    { value: ALL, label: t("all") },
    ...examsOfKind.map((exam) => ({ value: exam.id, label: t("examOption", { code: exam.code, name: exam.name }) })),
  ];
  const graderOptions = [
    { value: ALL, label: t("all") },
    { value: UNASSIGNED_GRADER, label: t("graderUnassigned") },
    ...graders.map((person) => ({ value: person.id, label: person.name })),
  ];
  const fromSelect = (value: string) => (value === ALL ? null : value);

  function changeKind(kind: string | null) {
    const examKind = filters.exam ? exams.find((exam) => exam.id === filters.exam)?.kindCode : undefined;
    const keepExam = kind === null || examKind === undefined || examKind === kind;
    onChange({ kind, ...(keepExam ? {} : { exam: null }) });
  }

  const chip = (label: string, value: string) => t("chip", { label, value });
  const candidates: Array<ActiveFilter | null | false | ""> = [
    filters.q && { key: "q", label: chip(t("search"), filters.q), clear: { q: "" } },
    filters.status && {
      key: "status",
      label: chip(t("statusLegend"), statusT(filters.status)),
      clear: { status: null },
    },
    filters.kind && { key: "kind", label: chip(t("kind"), kindName(filters.kind)), clear: { kind: null } },
    filters.exam && { key: "exam", label: chip(t("exam"), examLabel(filters.exam)), clear: { exam: null } },
    filters.grader && { key: "grader", label: chip(t("grader"), graderName(filters.grader)), clear: { grader: null } },
  ];
  const active = candidates.filter((item): item is ActiveFilter => Boolean(item));

  /** Bỏ một chip: focus sang chip kế tiếp (hoặc ô tìm khi hết chip), không để focus rơi về `<body>`. */
  function removeChip(index: number, clear: Partial<GradingFilters>) {
    onChange(clear);
    requestAnimationFrame(() => {
      const buttons = chipsRef.current?.querySelectorAll<HTMLButtonElement>("[data-chip-remove]") ?? [];
      const target = buttons[Math.min(index, buttons.length - 1)];
      (target ?? searchRef.current)?.focus();
    });
  }

  function resetAll() {
    onChange({ ...DEFAULT_GRADING_FILTERS, size: filters.size, sort: filters.sort });
    setSearchInput("");
    requestAnimationFrame(() => searchRef.current?.focus());
  }

  const statusOptions: Array<{ value: GradingStatus | null; label: string; count: number | undefined }> = [
    { value: null, label: t("statusAll"), count: counts?.total },
    ...GRADING_STATUSES.map((status) => ({ value: status, label: statusT(status), count: counts?.byStatus[status] })),
  ];

  return (
    <search className="block">
      <Card padding="md" className="@container">
        <form aria-label={t("ariaLabel")} onSubmit={(event) => event.preventDefault()} className="flex flex-col gap-3">
          <fieldset className="m-0 min-w-0 border-0 p-0">
            <legend className="sr-only">{t("statusLegend")}</legend>
            <div className="-m-1 flex max-w-[calc(100%+0.5rem)] overflow-x-auto p-1 [scrollbar-width:thin]">
              <div className="flex w-fit gap-1 rounded-lg bg-subtle p-1">
                {statusOptions.map((option) => (
                  <label key={option.value ?? "all"} className="relative shrink-0 cursor-pointer">
                    <input
                      type="radio"
                      name={statusName}
                      value={option.value ?? ""}
                      checked={filters.status === option.value}
                      onChange={() => onChange({ status: option.value })}
                      className="peer sr-only"
                    />
                    <span
                      className={cn(
                        "relative flex h-10 items-center gap-2 rounded-md px-3.5 text-label font-medium whitespace-nowrap text-text-muted",
                        "transition-colors duration-150 motion-reduce:transition-none hover:text-text",
                        "peer-checked:bg-surface peer-checked:text-primary-text peer-checked:shadow-sm",
                        "after:pointer-events-none after:absolute after:inset-x-3 after:bottom-1 peer-checked:after:border-b-2 peer-checked:after:border-current",
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
            </div>
          </fieldset>

          <div className={FILTER_FORM}>
            <FormField label={t("search")} className={FILTER_SEARCH}>
              {(controlProps) => (
                <Input
                  {...controlProps}
                  ref={searchRef}
                  type="search"
                  name="q"
                  leadingIcon={<Search />}
                  value={searchInput}
                  onChange={(event) => setSearchInput(event.target.value)}
                  placeholder={t("searchPlaceholder")}
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
                  onValueChange={(value) => changeKind(fromSelect(value))}
                />
              )}
            </FormField>
            <FormField label={t("exam")} className={cn(FILTER_SELECT, "@3xl:min-w-56")}>
              {(controlProps) => (
                <Select
                  {...controlProps}
                  name="exam"
                  options={examOptions}
                  value={filters.exam ?? ALL}
                  onValueChange={(value) => onChange({ exam: fromSelect(value) })}
                />
              )}
            </FormField>
            <FormField label={t("grader")} className={cn(FILTER_SELECT, "@3xl:min-w-40")}>
              {(controlProps) => (
                <Select
                  {...controlProps}
                  name="grader"
                  options={graderOptions}
                  value={filters.grader ?? ALL}
                  onValueChange={(value) => onChange({ grader: fromSelect(value) })}
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
