"use client";

import { ChevronLeft, ChevronRight } from "lucide-react";
import { cn } from "../lib/cn";
import { focusRing } from "../lib/styles";
import { Select } from "./select";

export type PaginationLabels = {
  /** Tên vùng điều hướng, ví dụ "Phân trang". */
  nav: string;
  previous: string;
  next: string;
  rowsPerPage: string;
  /** Tên nút số trang, ví dụ `Trang ${page}`. */
  page: (page: number) => string;
  /** Dòng tóm tắt, ví dụ `${from}–${to} / ${total}`. */
  range: (range: { from: number; to: number; total: number }) => string;
};

export type PaginationProps = {
  /** Trang hiện tại, đánh số từ 1. */
  page: number;
  pageSize: number;
  total: number;
  onPageChange: (page: number) => void;
  /** Có thì hiện ô chọn số dòng mỗi trang. */
  pageSizeOptions?: number[];
  onPageSizeChange?: (pageSize: number) => void;
  labels: PaginationLabels;
  className?: string;
};

/** Dãy số trang có dấu "…" (null) khi dài: luôn có trang đầu, cuối và hai bên trang hiện tại. */
export function pageItems(page: number, pageCount: number): (number | null)[] {
  if (pageCount <= 7) return Array.from({ length: pageCount }, (_, i) => i + 1);
  const wanted = new Set([1, pageCount, page - 1, page, page + 1]);
  if (page <= 3) [2, 3, 4].forEach((n) => wanted.add(n));
  if (page >= pageCount - 2) [pageCount - 3, pageCount - 2, pageCount - 1].forEach((n) => wanted.add(n));
  const sorted = [...wanted].filter((n) => n >= 1 && n <= pageCount).sort((a, b) => a - b);
  const items: (number | null)[] = [];
  sorted.forEach((n, i) => {
    const previous = sorted[i - 1];
    if (previous !== undefined && n - previous > 1) items.push(null);
    items.push(n);
  });
  return items;
}

const pageButton = cn(
  "inline-flex size-9 items-center justify-center rounded-md text-label font-medium text-text-secondary tabular-nums transition-colors duration-150 motion-reduce:transition-none",
  "hover:bg-subtle disabled:pointer-events-none disabled:opacity-40 aria-[current=page]:bg-primary-50 aria-[current=page]:font-semibold aria-[current=page]:text-primary-600",
  focusRing,
);

export function Pagination({
  page,
  pageSize,
  total,
  onPageChange,
  pageSizeOptions,
  onPageSizeChange,
  labels,
  className,
}: PaginationProps) {
  const pageCount = Math.max(1, Math.ceil(total / pageSize));
  const current = Math.min(Math.max(page, 1), pageCount);
  const from = total === 0 ? 0 : (current - 1) * pageSize + 1;
  const to = Math.min(current * pageSize, total);

  return (
    <nav
      aria-label={labels.nav}
      className={cn("flex flex-wrap items-center justify-between gap-4", className)}
    >
      <div className="flex items-center gap-3 text-body text-text-muted">
        <span className="tabular-nums">{labels.range({ from, to, total })}</span>
        {pageSizeOptions && onPageSizeChange && (
          <Select
            aria-label={labels.rowsPerPage}
            className="h-9 w-24"
            value={String(pageSize)}
            onValueChange={(value) => onPageSizeChange(Number(value))}
            options={pageSizeOptions.map((size) => ({ value: String(size), label: String(size) }))}
          />
        )}
      </div>
      <ul className="flex items-center gap-1">
        <li>
          <button
            type="button"
            aria-label={labels.previous}
            className={pageButton}
            disabled={current <= 1}
            onClick={() => onPageChange(current - 1)}
          >
            <ChevronLeft aria-hidden className="size-4" />
          </button>
        </li>
        {pageItems(current, pageCount).map((item, index) =>
          item === null ? (
            <li key={`gap-${index}`} aria-hidden className="px-1 text-text-placeholder">
              …
            </li>
          ) : (
            <li key={item}>
              <button
                type="button"
                aria-label={labels.page(item)}
                aria-current={item === current ? "page" : undefined}
                className={pageButton}
                onClick={() => onPageChange(item)}
              >
                {item}
              </button>
            </li>
          ),
        )}
        <li>
          <button
            type="button"
            aria-label={labels.next}
            className={pageButton}
            disabled={current >= pageCount}
            onClick={() => onPageChange(current + 1)}
          >
            <ChevronRight aria-hidden className="size-4" />
          </button>
        </li>
      </ul>
    </nav>
  );
}
