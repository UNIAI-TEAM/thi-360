"use client";

import {
  Button,
  Card,
  cn,
  focusRing,
  FormField,
  Input,
  Select,
} from "@thi360/ui";
import { ChevronDown, ChevronRight, ChevronUp, Network, Search, SlidersHorizontal, X } from "lucide-react";
import { useFormatter, useTranslations } from "next-intl";
import { useEffect, useId, useMemo, useRef, useState, type RefObject } from "react";
import { KnowledgePickerDialog } from "@/features/content/components/knowledge-picker-dialog";
import { indexKnowledge } from "@/features/content/knowledge-tree";
import { useReferenceLookup } from "@/features/content/queries";
import {
  QUESTION_STATUSES,
  QUESTION_TYPES,
  type ContentReference,
  type KnowledgeRef,
  type PersonRef,
  type QuestionStatus,
  type QuestionType,
} from "@/features/content/types";
import { FILTER_FORM, FILTER_SEARCH, FILTER_SELECT } from "@/features/users/components/list-filters";
import type { QuestionStatusCounts } from "../types";
import {
  countAdvancedFilters,
  DEFAULT_QUESTION_FILTERS,
  hasActiveQuestionFilters,
  type QuestionFilters,
  type SolutionFilter,
} from "./question-filters-state";

const ALL = "__all__";
const SEARCH_DEBOUNCE_MS = 300;

export interface QuestionFiltersBarProps {
  filters: QuestionFilters;
  onChange: (patch: Partial<QuestionFilters>) => void;
  reference: ContentReference | undefined;
  authors: readonly PersonRef[];
  /** Số câu theo trạng thái với bộ lọc hiện tại; chưa có thì ẩn số. */
  counts: QuestionStatusCounts | undefined;
  /** Ô tìm kiếm, để màn chuyển focus về đây khi phần tử đang focus biến mất. */
  searchInputRef?: RefObject<HTMLInputElement | null>;
}

interface ActiveFilter {
  key: string;
  label: string;
  clear: Partial<QuestionFilters>;
}

/**
 * Thanh lọc của Ngân hàng câu hỏi (canvas `NganHangCauHoi`): dải trạng thái kèm số câu, hàng lọc chính (tìm, môn,
 * khối, độ khó, dạng câu, gồm cả Tự luận), "Bộ lọc khác" mở thêm năng lực (V-ACT), kiến thức (MAPID), nhãn, người tạo, ngày tạo, lời giải; cuối cùng
 * là các chip "Đang lọc" bỏ từng điều kiện và nút xóa tất cả. Đổi điều kiện áp dụng ngay; ô tìm chờ ngừng gõ 300ms.
 */
export function QuestionFiltersBar({
  filters,
  onChange,
  reference,
  authors,
  counts,
  searchInputRef,
}: QuestionFiltersBarProps) {
  const t = useTranslations("questions.filters");
  const contentT = useTranslations("content");
  const format = useFormatter();
  const lookup = useReferenceLookup(reference);
  const statusName = useId();
  const solutionName = useId();
  const advancedId = useId();
  const chipsRef = useRef<HTMLDivElement>(null);
  const localSearchRef = useRef<HTMLInputElement>(null);
  const searchRef = searchInputRef ?? localSearchRef;

  const advancedCount = countAdvancedFilters(filters);
  const [advancedOpen, setAdvancedOpen] = useState(advancedCount > 0);
  const [pickerOpen, setPickerOpen] = useState(false);

  // Ô tìm: giữ chữ đang gõ riêng, gửi sau 300ms ngừng gõ; xóa trống thì áp dụng ngay. Bộ lọc đổi từ ngoài (xóa tất
  // cả, bỏ chip) thì ô theo giá trị mới.
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

  const knowledgeIndex = useMemo(() => indexKnowledge(reference?.knowledgeTree ?? []), [reference]);
  const knowledgeRefs = useMemo<KnowledgeRef[]>(
    () =>
      filters.knowledge.map((id) => {
        const node = knowledgeIndex.get(id)?.node;
        return { nodeId: id, code: node?.code ?? id, name: node?.name ?? "", primary: false };
      }),
    [filters.knowledge, knowledgeIndex],
  );
  const knowledgeLabel = (ref: KnowledgeRef) => (ref.name ? `${ref.code} ${ref.name}` : ref.code);

  const competencyName = (code: string) =>
    reference?.competencies.find((option) => option.code === code)?.name ?? code;
  const authorName = (id: string) => authors.find((person) => person.id === id)?.name ?? id;
  const formatDay = (day: string) => format.dateTime(new Date(`${day}T00:00:00+07:00`), "date");

  const selectOptions = (options: ReadonlyArray<{ code: string; name: string }> | undefined) => [
    { value: ALL, label: t("all") },
    ...(options ?? []).map((option) => ({ value: option.code, label: option.name })),
  ];
  const typeOptions = [
    { value: ALL, label: t("all") },
    ...QUESTION_TYPES.map((type) => ({ value: type, label: contentT(`questionType.${type}`) })),
  ];
  const authorOptions = [
    { value: ALL, label: t("all") },
    ...authors.map((person) => ({ value: person.id, label: person.name })),
  ];
  const fromSelect = (value: string) => (value === ALL ? null : value);

  const chip = (label: string, value: string) => t("chip", { label, value });
  const candidates: Array<ActiveFilter | null | false | ""> = [
    filters.q && { key: "q", label: chip(t("search"), filters.q), clear: { q: "" } },
    filters.status && {
      key: "status",
      label: chip(t("statusLegend"), contentT(`questionStatus.${filters.status}`)),
      clear: { status: null },
    },
    filters.subject && { key: "subject", label: chip(t("subject"), lookup.subject(filters.subject)), clear: { subject: null } },
    filters.grade && { key: "grade", label: chip(t("grade"), lookup.grade(filters.grade)), clear: { grade: null } },
    filters.difficulty && {
      key: "difficulty",
      label: chip(t("difficulty"), lookup.difficulty(filters.difficulty)),
      clear: { difficulty: null },
    },
    filters.type && { key: "type", label: chip(t("type"), contentT(`questionType.${filters.type}`)), clear: { type: null } },
    filters.competency && {
      key: "competency",
      label: chip(t("competency"), competencyName(filters.competency)),
      clear: { competency: null },
    },
    ...knowledgeRefs.map((ref) => ({
      key: `knowledge-${ref.nodeId}`,
      label: chip(t("knowledgeShort"), knowledgeLabel(ref)),
      clear: { knowledge: filters.knowledge.filter((id) => id !== ref.nodeId) },
    })),
    filters.tag && { key: "tag", label: chip(t("tag"), lookup.tag(filters.tag)), clear: { tag: null } },
    filters.author && { key: "author", label: chip(t("author"), authorName(filters.author)), clear: { author: null } },
    filters.solution && {
      key: "solution",
      label: chip(t("solution"), filters.solution === "has" ? t("solutionHas") : t("solutionNone")),
      clear: { solution: null },
    },
    filters.createdFrom && {
      key: "createdFrom",
      label: t("chipCreatedFrom", { value: formatDay(filters.createdFrom) }),
      clear: { createdFrom: null },
    },
    filters.createdTo && {
      key: "createdTo",
      label: t("chipCreatedTo", { value: formatDay(filters.createdTo) }),
      clear: { createdTo: null },
    },
  ];
  const active = candidates.filter((item): item is ActiveFilter => Boolean(item));

  /** Bỏ một chip: focus sang chip kế tiếp (hoặc ô tìm khi hết chip), không để focus rơi về `<body>`. */
  function removeChip(index: number, clear: Partial<QuestionFilters>) {
    onChange(clear);
    requestAnimationFrame(() => {
      const buttons = chipsRef.current?.querySelectorAll<HTMLButtonElement>("[data-chip-remove]") ?? [];
      const target = buttons[Math.min(index, buttons.length - 1)];
      (target ?? searchRef.current)?.focus();
    });
  }

  function resetAll() {
    onChange({ ...DEFAULT_QUESTION_FILTERS, size: filters.size, sort: filters.sort });
    setSearchInput("");
    requestAnimationFrame(() => searchRef.current?.focus());
  }

  const statusOptions: Array<{ value: QuestionStatus | null; label: string; count: number | undefined }> = [
    { value: null, label: t("statusAll"), count: counts?.total },
    ...QUESTION_STATUSES.map((status) => ({
      value: status,
      label: contentT(`questionStatus.${status}`),
      count: counts?.byStatus[status],
    })),
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
                        // Gạch chân ở mục đang chọn: không chỉ dựa vào màu (như Tabs kiểu segmented).
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
            <FormField label={t("subject")} className={cn(FILTER_SELECT, "@3xl:min-w-32")}>
              {(controlProps) => (
                <Select
                  {...controlProps}
                  name="subject"
                  options={selectOptions(reference?.subjects)}
                  value={filters.subject ?? ALL}
                  onValueChange={(value) => onChange({ subject: fromSelect(value) })}
                />
              )}
            </FormField>
            <FormField label={t("grade")} className={cn(FILTER_SELECT, "@3xl:min-w-28")}>
              {(controlProps) => (
                <Select
                  {...controlProps}
                  name="grade"
                  options={selectOptions(reference?.grades)}
                  value={filters.grade ?? ALL}
                  onValueChange={(value) => onChange({ grade: fromSelect(value) })}
                />
              )}
            </FormField>
            <FormField label={t("difficulty")} className={cn(FILTER_SELECT, "@3xl:min-w-32")}>
              {(controlProps) => (
                <Select
                  {...controlProps}
                  name="difficulty"
                  options={selectOptions(reference?.difficulties)}
                  value={filters.difficulty ?? ALL}
                  onValueChange={(value) => onChange({ difficulty: fromSelect(value) })}
                />
              )}
            </FormField>
            <FormField label={t("type")} className={cn(FILTER_SELECT, "@3xl:min-w-44")}>
              {(controlProps) => (
                <Select
                  {...controlProps}
                  name="type"
                  options={typeOptions}
                  value={filters.type ?? ALL}
                  onValueChange={(value) => onChange({ type: value === ALL ? null : (value as QuestionType) })}
                />
              )}
            </FormField>
            <div className="col-span-full flex items-end @3xl:col-auto">
              <Button
                type="button"
                variant="secondary"
                aria-expanded={advancedOpen}
                aria-controls={advancedId}
                onClick={() => setAdvancedOpen((open) => !open)}
              >
                <SlidersHorizontal />
                <span>{t("more")}</span>
                {advancedCount > 0 && (
                  <span className="rounded-full bg-primary-fill px-1.5 text-caption font-semibold text-on-primary tabular-nums">
                    <span aria-hidden>{format.number(advancedCount)}</span>
                    <span className="sr-only">{t("moreCount", { count: advancedCount })}</span>
                  </span>
                )}
                {advancedOpen ? <ChevronUp /> : <ChevronDown />}
              </Button>
            </div>
          </div>

          <div id={advancedId} hidden={!advancedOpen} className="flex flex-col gap-3 border-t border-border pt-3">
            <div className="grid grid-cols-[repeat(auto-fill,minmax(min(12rem,100%),1fr))] items-end gap-3">
              <FormField label={t("knowledge")} className="col-span-full @3xl:col-span-2">
                {(controlProps) => (
                  <button
                    {...controlProps}
                    type="button"
                    onClick={() => setPickerOpen(true)}
                    className={cn(
                      "flex h-10 w-full min-w-0 items-center gap-2 rounded-md border border-border bg-surface px-3 text-start text-body text-text",
                      "transition-colors duration-150 motion-reduce:transition-none hover:border-border-strong",
                      focusRing,
                    )}
                  >
                    <Network aria-hidden className="size-4 shrink-0 text-text-muted" />
                    <span className={cn("min-w-0 flex-1 truncate", knowledgeRefs.length === 0 && "text-text-muted")}>
                      {knowledgeRefs[0] ? knowledgeLabel(knowledgeRefs[0]) : t("knowledgeAny")}
                    </span>
                    {knowledgeRefs.length > 1 && (
                      <span className="shrink-0 rounded-full bg-neutral-bg px-2 text-caption text-neutral">
                        <span aria-hidden>+{format.number(knowledgeRefs.length - 1)}</span>
                        <span className="sr-only">{t("knowledgeMore", { count: knowledgeRefs.length - 1 })}</span>
                      </span>
                    )}
                    <ChevronRight aria-hidden className="size-4 shrink-0 text-text-muted" />
                  </button>
                )}
              </FormField>
              <FormField label={t("competency")}>
                {(controlProps) => (
                  <Select
                    {...controlProps}
                    name="competency"
                    options={selectOptions(reference?.competencies)}
                    value={filters.competency ?? ALL}
                    onValueChange={(value) => onChange({ competency: fromSelect(value) })}
                  />
                )}
              </FormField>
              <FormField label={t("tag")}>
                {(controlProps) => (
                  <Select
                    {...controlProps}
                    name="tag"
                    options={selectOptions(reference?.tags)}
                    value={filters.tag ?? ALL}
                    onValueChange={(value) => onChange({ tag: fromSelect(value) })}
                  />
                )}
              </FormField>
              <FormField label={t("author")}>
                {(controlProps) => (
                  <Select
                    {...controlProps}
                    name="author"
                    options={authorOptions}
                    value={filters.author ?? ALL}
                    onValueChange={(value) => onChange({ author: fromSelect(value) })}
                  />
                )}
              </FormField>
              <FormField label={t("createdFrom")}>
                {(controlProps) => (
                  <Input
                    {...controlProps}
                    type="date"
                    name="createdFrom"
                    value={filters.createdFrom ?? ""}
                    max={filters.createdTo ?? undefined}
                    onChange={(event) => onChange({ createdFrom: event.target.value || null })}
                  />
                )}
              </FormField>
              <FormField label={t("createdTo")}>
                {(controlProps) => (
                  <Input
                    {...controlProps}
                    type="date"
                    name="createdTo"
                    value={filters.createdTo ?? ""}
                    min={filters.createdFrom ?? undefined}
                    onChange={(event) => onChange({ createdTo: event.target.value || null })}
                  />
                )}
              </FormField>
            </div>
            <fieldset className="m-0 flex min-w-0 flex-wrap items-center gap-x-5 gap-y-1 border-0 p-0">
              <legend className="float-left me-5 py-2 text-label font-medium text-text">{t("solution")}</legend>
              {(
                [
                  [null, t("solutionAll")],
                  ["has", t("solutionHas")],
                  ["none", t("solutionNone")],
                ] as Array<[SolutionFilter | null, string]>
              ).map(([value, label]) => (
                <label key={value ?? "all"} className="flex min-h-10 cursor-pointer items-center gap-2 text-body text-text">
                  <input
                    type="radio"
                    name={solutionName}
                    value={value ?? ""}
                    checked={filters.solution === value}
                    onChange={() => onChange({ solution: value })}
                    className="size-4 accent-primary-fill"
                  />
                  {label}
                </label>
              ))}
            </fieldset>
          </div>

          {hasActiveQuestionFilters(filters) && (
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

      <KnowledgePickerDialog
        open={pickerOpen}
        onOpenChange={setPickerOpen}
        value={knowledgeRefs}
        onApply={(refs) => onChange({ knowledge: refs.map((ref) => ref.nodeId) })}
        defaultSubjectCode={filters.subject}
        defaultGradeCode={filters.grade}
        withPrimary={false}
        selectableLevels={["CHAPTER", "LESSON", "KNOWLEDGE"]}
        description={t("knowledgePickerDescription")}
        selectionNote={null}
        applyLabel={(count) => t("knowledgeApply", { count })}
      />
    </search>
  );
}
