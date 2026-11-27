"use client";

import * as PopoverPrimitive from "@radix-ui/react-popover";
import { Check, ChevronDown, Search } from "lucide-react";
import {
  useEffect,
  useId,
  useMemo,
  useRef,
  useState,
  type KeyboardEvent,
  type ReactElement,
  type RefObject,
} from "react";
import { cn } from "../lib/cn";
import { matchesFilter, nodeText } from "../lib/filter-text";
import { fieldBase } from "../lib/styles";
import type { SelectOption } from "./select";
import { useUiStrings } from "./ui-strings";

export type ComboboxOption = SelectOption;

/** Chữ của ô lọc. Thiếu khóa nào thì lấy từ `UiStringsProvider` (`combobox`). */
export type ComboboxLabels = {
  filterPlaceholder: string;
  /** Tên truy cập của ô lọc. */
  filterLabel: string;
  /** Trạng thái rỗng khi không mục nào khớp. */
  noMatch: string;
};

/*
 * Popover `modal` như `Select` và `DropdownMenu` của Radix: khóa cuộn trang nhưng vẫn cuộn được danh sách, kể cả khi
 * nằm trong một `Dialog` (khóa cuộn của hộp thoại chặn lăn chuột trên phần nội dung portal không modal).
 */

/**
 * Khung danh sách chung của `Combobox` và `ComboboxMenu` (DESIGN.md mục 5.2): ô lọc cố định ở trên, danh sách cuộn
 * bên trong cao tối đa 20rem (khoảng 8 dòng); cả khung không vượt chỗ trống Radix đo được (trừ lề 8px).
 */
const contentBase =
  "z-50 flex max-h-[calc(var(--radix-popover-content-available-height)-0.5rem)] flex-col overflow-hidden rounded-lg border border-border bg-surface shadow-md motion-safe:data-[state=open]:animate-fade-in";

function optionText(option: ComboboxOption): string {
  return option.textValue ?? (typeof option.label === "string" ? option.label : nodeText(option.label));
}

function useLabels(labels?: Partial<ComboboxLabels>): ComboboxLabels {
  const base = useUiStrings().combobox;
  return { ...base, ...labels };
}

type FilterPanelProps = {
  options: ComboboxOption[];
  /** Giá trị đang chọn (Combobox): được đánh dấu `aria-selected` và tô sáng khi mở. */
  selectedValue?: string;
  initialQuery: string;
  labels: ComboboxLabels;
  listLabel?: string;
  onPick: (value: string) => void;
  inputRef: RefObject<HTMLInputElement | null>;
};

/**
 * Ô lọc (`role="combobox"`, giữ focus) và `role="listbox"` theo mẫu ARIA combobox: mục đang tô sáng chỉ qua
 * `aria-activedescendant`, focus không rời ô lọc. ↑/↓ chuyển mục (vòng lại), Enter chọn, Esc do Popover đóng và trả
 * focus về nút mở.
 */
function FilterPanel({ options, selectedValue, initialQuery, labels, listLabel, onPick, inputRef }: FilterPanelProps) {
  const listId = useId();
  const [query, setQuery] = useState(initialQuery);
  const matches = useMemo(
    () =>
      options
        .map((option, index) => ({ option, id: `${listId}-${index}` }))
        .filter(({ option }) => matchesFilter(optionText(option), query)),
    [options, query, listId],
  );
  const enabled = matches.filter(({ option }) => !option.disabled);
  const [activeValue, setActiveValue] = useState<string | undefined>(() => {
    const selected = enabled.find(({ option }) => option.value === selectedValue);
    return (selected ?? enabled[0])?.option.value;
  });
  const active = enabled.find(({ option }) => option.value === activeValue) ?? enabled[0];

  useEffect(() => {
    if (active) document.getElementById(active.id)?.scrollIntoView({ block: "nearest" });
  }, [active]);

  const move = (step: 1 | -1) => {
    if (enabled.length === 0) return;
    const current = active ? enabled.indexOf(active) : -1;
    const next = (current + step + enabled.length) % enabled.length;
    setActiveValue(enabled[next]!.option.value);
  };

  const onKeyDown = (event: KeyboardEvent<HTMLInputElement>) => {
    if (event.key === "ArrowDown" || event.key === "ArrowUp") {
      event.preventDefault();
      move(event.key === "ArrowDown" ? 1 : -1);
    } else if (event.key === "Enter") {
      event.preventDefault();
      if (active) onPick(active.option.value);
    }
  };

  return (
    <>
      <div className="relative shrink-0 border-b border-border p-1.5">
        <Search aria-hidden className="pointer-events-none absolute start-4 top-1/2 size-4 -translate-y-1/2 text-text-muted" />
        <input
          ref={inputRef}
          type="text"
          role="combobox"
          aria-expanded
          aria-controls={listId}
          aria-autocomplete="list"
          aria-activedescendant={active?.id}
          aria-label={labels.filterLabel}
          placeholder={labels.filterPlaceholder}
          autoComplete="off"
          spellCheck={false}
          value={query}
          onChange={(event) => {
            setQuery(event.target.value);
            setActiveValue(undefined);
          }}
          onKeyDown={onKeyDown}
          className={cn(fieldBase, "h-9 ps-8 pe-3")}
        />
      </div>
      {matches.length === 0 ? (
        <p role="status" className="m-0 px-3 py-3 text-body text-text-muted">
          {labels.noMatch}
        </p>
      ) : (
        <div role="listbox" id={listId} aria-label={listLabel} className="max-h-80 min-h-0 overflow-y-auto p-1">
          {matches.map(({ option, id }) => {
            const selected = selectedValue !== undefined && option.value === selectedValue;
            return (
              <div
                key={option.value}
                id={id}
                role="option"
                aria-selected={selected}
                aria-disabled={option.disabled || undefined}
                data-highlighted={active?.id === id ? "" : undefined}
                // Bấm chuột không lấy mất focus của ô lọc.
                onMouseDown={(event) => event.preventDefault()}
                onPointerMove={() => {
                  if (!option.disabled && activeValue !== option.value) setActiveValue(option.value);
                }}
                onClick={() => {
                  if (!option.disabled) onPick(option.value);
                }}
                className={cn(
                  "relative flex min-h-9 cursor-default items-center rounded-sm py-1.5 pr-8 pl-3 text-label font-medium text-text select-none pointer-coarse:min-h-11",
                  "aria-disabled:opacity-50 data-[highlighted]:bg-subtle aria-selected:text-primary-text",
                )}
              >
                {option.label}
                {selected && <Check aria-hidden className="absolute right-2.5 size-4" />}
              </div>
            );
          })}
        </div>
      )}
    </>
  );
}

const OPEN_KEYS = new Set(["ArrowDown", "ArrowUp"]);

function isPrintableKey(event: KeyboardEvent) {
  return event.key.length === 1 && event.key !== " " && !event.ctrlKey && !event.metaKey && !event.altKey;
}

export type ComboboxProps = {
  options: ComboboxOption[];
  value?: string;
  defaultValue?: string;
  onValueChange?: (value: string) => void;
  placeholder?: string;
  disabled?: boolean;
  /** Có `name` thì kèm một ô ẩn mang giá trị, để gửi cùng `<form>` như `Select`. */
  name?: string;
  id?: string;
  size?: "md" | "lg";
  className?: string;
  labels?: Partial<ComboboxLabels>;
  "aria-label"?: string;
  "aria-invalid"?: boolean;
  "aria-required"?: boolean;
  "aria-describedby"?: string;
};

/**
 * Ô chọn một giá trị có ô lọc, cho danh sách dài lấy từ dữ liệu (DESIGN.md mục 5.2). Cùng giao diện nút với
 * `Select`; `Select` tự chuyển sang đây khi danh sách dài hơn `SELECT_FILTER_THRESHOLD`. Gõ chữ trên nút đóng thì mở
 * và điền chữ đó vào ô lọc; ↓/↑ cũng mở.
 */
export function Combobox({
  options,
  value,
  defaultValue,
  onValueChange,
  placeholder,
  disabled,
  name,
  size = "md",
  className,
  labels,
  ...triggerProps
}: ComboboxProps) {
  const resolved = useLabels(labels);
  const [open, setOpen] = useState(false);
  const [initialQuery, setInitialQuery] = useState("");
  const [uncontrolled, setUncontrolled] = useState(defaultValue);
  const current = value !== undefined ? value : uncontrolled;
  const selected = options.find((option) => option.value === current);
  const inputRef = useRef<HTMLInputElement>(null);

  const pick = (next: string) => {
    setOpen(false);
    if (next === current) return;
    if (value === undefined) setUncontrolled(next);
    onValueChange?.(next);
  };

  return (
    <PopoverPrimitive.Root
      modal
      open={open}
      onOpenChange={(next) => {
        if (next) setInitialQuery("");
        setOpen(next);
      }}
    >
      <PopoverPrimitive.Trigger asChild>
        <button
          type="button"
          role="combobox"
          aria-haspopup="listbox"
          disabled={disabled}
          data-placeholder={selected ? undefined : ""}
          onKeyDown={(event) => {
            if (open) return;
            if (OPEN_KEYS.has(event.key)) {
              event.preventDefault();
              setInitialQuery("");
              setOpen(true);
            } else if (isPrintableKey(event)) {
              event.preventDefault();
              setInitialQuery(event.key);
              setOpen(true);
            }
          }}
          className={cn(
            fieldBase,
            "flex items-center justify-between gap-2 px-3 text-left data-[placeholder]:text-text-placeholder",
            size === "lg" ? "min-h-12 py-2" : "min-h-10 py-1.5",
            className,
          )}
          {...triggerProps}
        >
          <span className="min-w-0 truncate">{selected ? selected.label : placeholder}</span>
          <ChevronDown aria-hidden className="size-4 shrink-0 text-text-muted" />
        </button>
      </PopoverPrimitive.Trigger>
      {name !== undefined && <input type="hidden" name={name} value={current ?? ""} />}
      <PopoverPrimitive.Portal>
        <PopoverPrimitive.Content
          align="start"
          sideOffset={4}
          onOpenAutoFocus={(event) => {
            event.preventDefault();
            inputRef.current?.focus();
          }}
          className={cn(contentBase, "w-(--radix-popover-trigger-width) min-w-56 max-w-[calc(100vw-2rem)]")}
        >
          <FilterPanel
            options={options}
            selectedValue={current}
            initialQuery={initialQuery}
            labels={resolved}
            listLabel={triggerProps["aria-label"]}
            onPick={pick}
            inputRef={inputRef}
          />
        </PopoverPrimitive.Content>
      </PopoverPrimitive.Portal>
    </PopoverPrimitive.Root>
  );
}

export type ComboboxMenuProps = {
  /** Nút mở (một phần tử nhận ref, vd. `Button`). */
  trigger: ReactElement;
  options: ComboboxOption[];
  /** Gọi khi chọn một mục; danh sách đóng lại và focus về nút mở. */
  onSelect: (value: string) => void;
  align?: "start" | "center" | "end";
  /** Tên truy cập của danh sách. */
  "aria-label"?: string;
  labels?: Partial<ComboboxLabels>;
  contentClassName?: string;
};

/**
 * Menu "chọn để thêm" có ô lọc (vd. Thêm nhãn của câu hỏi): không giữ giá trị, mỗi lần chọn gọi `onSelect`.
 * Dùng thay `DropdownMenu` khi danh sách lấy từ dữ liệu và có thể dài (DESIGN.md mục 5.2).
 */
export function ComboboxMenu({
  trigger,
  options,
  onSelect,
  align = "start",
  "aria-label": listLabel,
  labels,
  contentClassName,
}: ComboboxMenuProps) {
  const resolved = useLabels(labels);
  const [open, setOpen] = useState(false);
  const inputRef = useRef<HTMLInputElement>(null);

  return (
    <PopoverPrimitive.Root modal open={open} onOpenChange={setOpen}>
      <PopoverPrimitive.Trigger asChild aria-haspopup="listbox">
        {trigger}
      </PopoverPrimitive.Trigger>
      <PopoverPrimitive.Portal>
        <PopoverPrimitive.Content
          align={align}
          sideOffset={6}
          onOpenAutoFocus={(event) => {
            event.preventDefault();
            inputRef.current?.focus();
          }}
          className={cn(contentBase, "w-72 max-w-[calc(100vw-2rem)]", contentClassName)}
        >
          <FilterPanel
            options={options}
            initialQuery=""
            labels={resolved}
            listLabel={listLabel}
            onPick={(value) => {
              setOpen(false);
              onSelect(value);
            }}
            inputRef={inputRef}
          />
        </PopoverPrimitive.Content>
      </PopoverPrimitive.Portal>
    </PopoverPrimitive.Root>
  );
}
