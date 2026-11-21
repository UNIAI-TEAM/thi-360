"use client";

import { usePathname, useSearchParams } from "next/navigation";
import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { isValidCalendarDate } from "../format";
import { ATTEMPT_STATES, type AttemptState, type ListAdminAttemptsQuery } from "../types";

export interface AttemptFilters {
  state: AttemptState | null;
  user: string;
  exam: string;
  from: string;
  to: string;
  /** 1-indexed page number in the URL. */
  page: number;
  size: number;
}

export const ATTEMPTS_PAGE_SIZES = [10, 20, 50] as const;
export const DEFAULT_ATTEMPTS_PAGE_SIZE = 20;

export const DEFAULT_ATTEMPT_FILTERS: AttemptFilters = {
  state: null,
  user: "",
  exam: "",
  from: "",
  to: "",
  page: 1,
  size: DEFAULT_ATTEMPTS_PAGE_SIZE,
};

const PARAMS = ["state", "user", "exam", "from", "to", "page", "size"] as const;

export function parseAttemptFilters(search: string): AttemptFilters {
  const params = new URLSearchParams(search);
  const rawState = params.get("state");
  const state =
    rawState !== null && (ATTEMPT_STATES as readonly string[]).includes(rawState)
      ? (rawState as AttemptState)
      : null;

  const rawFrom = params.get("from") ?? "";
  const rawTo = params.get("to") ?? "";
  const from = rawFrom && isValidCalendarDate(rawFrom) ? rawFrom.trim() : "";
  const to = rawTo && isValidCalendarDate(rawTo) ? rawTo.trim() : "";

  const page = Number.parseInt(params.get("page") ?? "", 10);
  const size = Number.parseInt(params.get("size") ?? "", 10);

  return {
    state,
    user: params.get("user")?.trim() ?? "",
    exam: params.get("exam")?.trim() ?? "",
    from,
    to,
    page: Number.isFinite(page) && page > 0 ? page : 1,
    size: (ATTEMPTS_PAGE_SIZES as readonly number[]).includes(size)
      ? size
      : DEFAULT_ATTEMPTS_PAGE_SIZE,
  };
}

export function serializeAttemptFilters(filters: AttemptFilters, baseSearch = ""): string {
  const params = new URLSearchParams(baseSearch);
  for (const name of PARAMS) params.delete(name);

  const set = (name: string, value: string | null) => {
    if (value) params.set(name, value);
  };
  set("state", filters.state);
  set("user", filters.user.trim() || null);
  set("exam", filters.exam.trim() || null);
  set("from", filters.from || null);
  set("to", filters.to || null);
  set("page", filters.page > 1 ? String(filters.page) : null);
  set("size", filters.size !== DEFAULT_ATTEMPTS_PAGE_SIZE ? String(filters.size) : null);
  return params.toString();
}

/** Query params for `GET /api/v1/admin/attempts` (0-indexed page). */
export function toListAttemptsQuery(filters: AttemptFilters): ListAdminAttemptsQuery {
  return {
    ...(filters.state ? { state: filters.state } : {}),
    ...(filters.user ? { user: filters.user } : {}),
    ...(filters.exam ? { exam: filters.exam } : {}),
    ...(filters.from ? { from: filters.from } : {}),
    ...(filters.to ? { to: filters.to } : {}),
    page: filters.page - 1,
    size: filters.size,
  };
}

export function hasActiveAttemptFilters(filters: AttemptFilters): boolean {
  return Boolean(filters.state || filters.user || filters.exam || filters.from || filters.to);
}

export function applyAttemptFiltersPatch(
  current: AttemptFilters,
  patch: Partial<AttemptFilters>,
): AttemptFilters {
  const next = { ...current, ...patch };
  if (patch.page === undefined) next.page = 1;
  return next;
}

export function useAttemptFiltersUrlState(): [
  AttemptFilters,
  (patch: Partial<AttemptFilters>) => void,
] {
  const pathname = usePathname();
  const searchParams = useSearchParams();
  const searchKey = searchParams.toString();
  const [prevSearchKey, setPrevSearchKey] = useState(searchKey);
  const [filters, setFilters] = useState(() => parseAttemptFilters(searchKey));
  const filtersRef = useRef(filters);

  if (searchKey !== prevSearchKey) {
    setPrevSearchKey(searchKey);
    setFilters(parseAttemptFilters(searchKey));
  }

  useEffect(() => {
    filtersRef.current = filters;
  }, [filters]);

  const update = useCallback(
    (patch: Partial<AttemptFilters>) => {
      const next = applyAttemptFiltersPatch(filtersRef.current, patch);
      filtersRef.current = next;
      const query =
        typeof window !== "undefined"
          ? serializeAttemptFilters(next, window.location.search)
          : serializeAttemptFilters(next);
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
