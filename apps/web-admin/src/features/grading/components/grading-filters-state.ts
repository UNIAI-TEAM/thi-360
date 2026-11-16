"use client";

import { usePathname, useSearchParams } from "next/navigation";
import { useCallback, useMemo, useRef, useState } from "react";
import { DEFAULT_GRADING_SORT, SORTABLE_GRADING_FIELDS } from "../api";
import { GRADING_STATUSES, type GradingStatus, type ListGradingAttemptsQuery } from "../types";

/**
 * Bộ lọc của hàng chờ chấm, giữ trên URL (DESIGN.md mục 5.13, 5.14: tên và giá trị query bằng tiếng Anh). Tham số khác
 * (ví dụ `mockState`) được giữ nguyên.
 */

export interface GradingFilters {
  q: string;
  status: GradingStatus | null;
  kind: string | null;
  exam: string | null;
  /** Id người chấm hoặc `UNASSIGNED`. */
  grader: string | null;
  /** Trang tính từ 1. */
  page: number;
  size: number;
  /** `field,asc|desc`; `null` là mặc định (nộp trước lên trước). */
  sort: string | null;
}

export const GRADING_PAGE_SIZES = [10, 20, 50] as const;
export const DEFAULT_GRADING_PAGE_SIZE = 20;

export const DEFAULT_GRADING_FILTERS: GradingFilters = {
  q: "",
  status: null,
  kind: null,
  exam: null,
  grader: null,
  page: 1,
  size: DEFAULT_GRADING_PAGE_SIZE,
  sort: null,
};

const PARAMS = ["q", "status", "kind", "exam", "grader", "page", "size", "sort"] as const satisfies ReadonlyArray<
  keyof GradingFilters
>;

function text(value: string | null): string | null {
  const trimmed = value?.trim();
  return trimmed ? trimmed : null;
}

export function parseGradingFilters(search: string): GradingFilters {
  const params = new URLSearchParams(search);
  const page = Number.parseInt(params.get("page") ?? "", 10);
  const size = Number.parseInt(params.get("size") ?? "", 10);
  const status = params.get("status");
  const sort = params.get("sort");
  const [sortField, sortDirection] = sort?.split(",") ?? [];
  const validSort =
    sortField !== undefined &&
    (SORTABLE_GRADING_FIELDS as readonly string[]).includes(sortField) &&
    (sortDirection === "asc" || sortDirection === "desc");
  return {
    q: params.get("q")?.trim() ?? "",
    status: status !== null && (GRADING_STATUSES as readonly string[]).includes(status) ? (status as GradingStatus) : null,
    kind: text(params.get("kind")),
    exam: text(params.get("exam")),
    grader: text(params.get("grader")),
    page: Number.isFinite(page) && page > 0 ? page : 1,
    size: (GRADING_PAGE_SIZES as readonly number[]).includes(size) ? size : DEFAULT_GRADING_PAGE_SIZE,
    sort: validSort ? `${sortField},${sortDirection}` : null,
  };
}

/** Ghi bộ lọc vào chuỗi query, bỏ giá trị mặc định và giữ các tham số không thuộc bộ lọc. */
export function serializeGradingFilters(filters: GradingFilters, baseSearch = ""): string {
  const params = new URLSearchParams(baseSearch);
  for (const name of PARAMS) params.delete(name);
  const set = (name: string, value: string | null) => {
    if (value) params.set(name, value);
  };
  set("q", filters.q.trim() || null);
  set("status", filters.status);
  set("kind", filters.kind);
  set("exam", filters.exam);
  set("grader", filters.grader);
  set("page", filters.page > 1 ? String(filters.page) : null);
  set("size", filters.size !== DEFAULT_GRADING_PAGE_SIZE ? String(filters.size) : null);
  set("sort", filters.sort);
  return params.toString();
}

/** Tham số gọi API (trang tính từ 0). */
export function toListGradingQuery(filters: GradingFilters): ListGradingAttemptsQuery {
  return {
    ...(filters.q ? { q: filters.q } : {}),
    ...(filters.status ? { status: filters.status } : {}),
    ...(filters.kind ? { examKindCode: filters.kind } : {}),
    ...(filters.exam ? { examId: filters.exam } : {}),
    ...(filters.grader ? { graderId: filters.grader } : {}),
    page: filters.page - 1,
    size: filters.size,
    sort: filters.sort ?? DEFAULT_GRADING_SORT,
  };
}

/** Có điều kiện lọc nào khác mặc định (không tính trạng thái, trang, cỡ trang, sắp xếp). */
export function hasActiveGradingFilters(filters: GradingFilters): boolean {
  return Boolean(filters.q || filters.status || filters.kind || filters.exam || filters.grader);
}

/** Đổi bộ lọc thì về trang 1, trừ khi chính `page` được đổi. */
export function applyGradingFiltersPatch(current: GradingFilters, patch: Partial<GradingFilters>): GradingFilters {
  const next = { ...current, ...patch };
  if (patch.page === undefined) next.page = 1;
  return next;
}

/** Bộ lọc nằm trong state của React (khởi tạo từ URL), URL chỉ là bản sao ghi bằng History API. */
export function useGradingFiltersUrlState(): [GradingFilters, (patch: Partial<GradingFilters>) => void] {
  const pathname = usePathname();
  const searchParams = useSearchParams();
  const initialSearch = searchParams.toString();
  const [filters, setFilters] = useState(() => parseGradingFilters(initialSearch));
  const filtersRef = useRef(filters);

  const update = useCallback(
    (patch: Partial<GradingFilters>) => {
      const next = applyGradingFiltersPatch(filtersRef.current, patch);
      filtersRef.current = next;
      setFilters(next);
      if (typeof window !== "undefined") {
        const query = serializeGradingFilters(next, window.location.search);
        window.history.replaceState(null, "", query ? `${pathname}?${query}` : pathname);
      }
    },
    [pathname],
  );

  return useMemo(() => [filters, update], [filters, update]);
}
