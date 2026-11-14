"use client";

import { usePathname, useSearchParams } from "next/navigation";
import { useCallback, useEffect, useMemo, useRef, useState } from "react";

export interface CatalogSelectionPatch {
  /** `undefined` keeps the current group, `null` clears it. */
  group?: string | null;
  /** `undefined` keeps the current node, `null` clears it. */
  node?: string | null;
}

/**
 * Applies a selection change to a query string. Changing the group always drops `node`
 * (a node belongs to one group); unrelated params are kept.
 */
export function applyCatalogSelectionPatch(query: string, patch: CatalogSelectionPatch): string {
  const next = new URLSearchParams(query);
  if (patch.group !== undefined) {
    if (patch.group) next.set("group", patch.group);
    else next.delete("group");
    next.delete("node");
  }
  if (patch.node !== undefined) {
    if (patch.node) next.set("node", patch.node);
    else next.delete("node");
  }
  return next.toString();
}

export function buildCatalogHref(pathname: string, query: string): string {
  return query ? `${pathname}?${query}` : pathname;
}

export interface CatalogUrlState {
  group: string | null;
  node: string | null;
  select: (patch: CatalogSelectionPatch) => void;
}

/**
 * Selected group / tree node of `/cau-hinh-chung`.
 *
 * The selection lives in React state (initialised from `useSearchParams()`), so it never
 * depends on a Next RSC navigation: in a production build a search-only `router.replace`
 * issued from a page that was loaded with `?group=` was dropped (VIS-1). The URL is only a
 * mirror, written with the native History API (Next >= 14.1 keeps `useSearchParams` in sync
 * with it, with no server round-trip). Back/forward (`popstate`) and any other external change
 * of the search params are followed; the echo of our own writes is ignored so a late echo
 * cannot roll the selection back.
 */
export function useCatalogUrlState(): CatalogUrlState {
  const pathname = usePathname();
  const searchParams = useSearchParams();
  const urlQuery = searchParams.toString();

  const [query, setQueryState] = useState(urlQuery);
  const queryRef = useRef(urlQuery);
  const seenUrlQueryRef = useRef(urlQuery);
  const pendingWritesRef = useRef<string[]>([]);

  const commit = useCallback((next: string) => {
    queryRef.current = next;
    setQueryState(next);
  }, []);

  useEffect(() => {
    if (seenUrlQueryRef.current === urlQuery) return;
    seenUrlQueryRef.current = urlQuery;
    const echoIndex = pendingWritesRef.current.lastIndexOf(urlQuery);
    if (echoIndex >= 0) {
      pendingWritesRef.current.splice(0, echoIndex + 1);
      return;
    }
    pendingWritesRef.current = [];
    commit(urlQuery);
  }, [commit, urlQuery]);

  useEffect(() => {
    function handlePopState() {
      pendingWritesRef.current = [];
      commit(window.location.search.replace(/^\?/, ""));
    }
    window.addEventListener("popstate", handlePopState);
    return () => window.removeEventListener("popstate", handlePopState);
  }, [commit]);

  const select = useCallback(
    (patch: CatalogSelectionPatch) => {
      const next = applyCatalogSelectionPatch(queryRef.current, patch);
      if (next === queryRef.current) return;
      commit(next);
      pendingWritesRef.current.push(next);
      window.history.replaceState(null, "", buildCatalogHref(pathname, next));
    },
    [commit, pathname],
  );

  return useMemo(() => {
    const params = new URLSearchParams(query);
    return { group: params.get("group"), node: params.get("node"), select };
  }, [query, select]);
}
