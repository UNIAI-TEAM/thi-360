"use client";

import { usePathname, useSearchParams } from "next/navigation";
import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { isValidCalendarDate } from "../scoring";
import {
  GRADING_QUEUE_STATES,
  type GradingCountsQuery,
  type GradingQueueState,
  type ListGradingItemsQuery,
} from "../types";

export interface GradingFilters {
  state: GradingQueueState | null;
  exam: string;
  kind: string | null;
  from: string;
  to: string;
  /** 1-indexed page number in the URL. */
  page: number;
  size: number;
}

export const GRADING_PAGE_SIZES = [10, 20, 50] as const;
export const DEFAULT_GRADING_PAGE_SIZE = 20;

export const DEFAULT_GRADING_FILTERS: GradingFilters = {
  state: null,
  exam: "",
  kind: null,
  from: "",
  to: "",
  page: 1,
  size: DEFAULT_GRADING_PAGE_SIZE,
};

const PARAMS = ["state", "exam", "kind", "from", "to", "page", "size"] as const;

function cleanText(value: string | null): string | null {
  const trimmed = value?.trim();
  return trimmed ? trimmed : null;
}

export function parseGradingFilters(search: string): GradingFilters {
  const params = new URLSearchParams(search);
  const rawState = params.get("state");
  const state =
    rawState !== null && (GRADING_QUEUE_STATES as readonly string[]).includes(rawState)
      ? (rawState as GradingQueueState)
      : null;

  const rawFrom = params.get("from") ?? "";
  const rawTo = params.get("to") ?? "";
  const from = rawFrom && isValidCalendarDate(rawFrom) ? rawFrom.trim() : "";
  const to = rawTo && isValidCalendarDate(rawTo) ? rawTo.trim() : "";

  const page = Number.parseInt(params.get("page") ?? "", 10);
  const size = Number.parseInt(params.get("size") ?? "", 10);

  return {
    state,
    exam: params.get("exam")?.trim() ?? "",
    kind: cleanText(params.get("kind")),
    from,
    to,
    page: Number.isFinite(page) && page > 0 ? page : 1,
    size: (GRADING_PAGE_SIZES as readonly number[]).includes(size) ? size : DEFAULT_GRADING_PAGE_SIZE,
  };
}

export function serializeGradingFilters(filters: GradingFilters, baseSearch = ""): string {
  const params = new URLSearchParams(baseSearch);
  for (const name of PARAMS) params.delete(name);

  const set = (name: string, value: string | null) => {
    if (value) params.set(name, value);
  };
  set("state", filters.state);
  set("exam", filters.exam.trim() || null);
  set("kind", filters.kind);
  set("from", filters.from || null);
  set("to", filters.to || null);
  set("page", filters.page > 1 ? String(filters.page) : null);
  set("size", filters.size !== DEFAULT_GRADING_PAGE_SIZE ? String(filters.size) : null);
  return params.toString();
}

/** Query params for `GET /api/v1/admin/grading/items` (0-indexed page). */
export function toListGradingQuery(filters: GradingFilters): ListGradingItemsQuery {
  return {
    ...(filters.state ? { state: filters.state } : {}),
    ...(filters.exam ? { exam: filters.exam } : {}),
    ...(filters.kind ? { kind: filters.kind } : {}),
    ...(filters.from ? { from: filters.from } : {}),
    ...(filters.to ? { to: filters.to } : {}),
    page: filters.page - 1,
    size: filters.size,
  };
}

/** Query params for `GET /api/v1/admin/grading/items/counts` (BS §11: excludes `state`). */
export function toGradingCountsQuery(filters: GradingFilters): GradingCountsQuery {
  return {
    ...(filters.exam ? { exam: filters.exam } : {}),
    ...(filters.kind ? { kind: filters.kind } : {}),
    ...(filters.from ? { from: filters.from } : {}),
    ...(filters.to ? { to: filters.to } : {}),
  };
}

export function hasActiveGradingFilters(filters: GradingFilters): boolean {
  return Boolean(filters.state || filters.exam || filters.kind || filters.from || filters.to);
}

export function applyGradingFiltersPatch(
  current: GradingFilters,
  patch: Partial<GradingFilters>,
): GradingFilters {
  const next = { ...current, ...patch };
  if (patch.page === undefined) next.page = 1;
  return next;
}

export function useGradingFiltersUrlState(): [
  GradingFilters,
  (patch: Partial<GradingFilters>) => void,
] {
  const pathname = usePathname();
  const searchParams = useSearchParams();
  const searchKey = searchParams.toString();
  const [prevSearchKey, setPrevSearchKey] = useState(searchKey);
  const [filters, setFilters] = useState(() => parseGradingFilters(searchKey));
  const filtersRef = useRef(filters);

  if (searchKey !== prevSearchKey) {
    setPrevSearchKey(searchKey);
    setFilters(parseGradingFilters(searchKey));
  }

  useEffect(() => {
    filtersRef.current = filters;
  }, [filters]);

  const update = useCallback(
    (patch: Partial<GradingFilters>) => {
      const next = applyGradingFiltersPatch(filtersRef.current, patch);
      filtersRef.current = next;
      const query =
        typeof window !== "undefined"
          ? serializeGradingFilters(next, window.location.search)
          : serializeGradingFilters(next);
      setPrevSearchKey(query);
      setFilters(next);
      if (typeof window !== "undefined") {
        window.history.replaceState(null, "", query ? `${pathname}?${query}` : pathname);
      }
    },
    [pathname],
  );

  return useMemo(() => [filters, update], [filters, update]);
}
