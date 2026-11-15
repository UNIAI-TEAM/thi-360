"use client";

import { useQuery } from "@tanstack/react-query";
import { useMemo } from "react";
import { CONTENT_QUERY_KEYS, defaultContentApiDeps, type ContentApiDeps } from "./api";
import type { CatalogOption, ContentReference } from "./types";

export function useContentReference(deps: ContentApiDeps = defaultContentApiDeps) {
  return useQuery({
    queryKey: CONTENT_QUERY_KEYS.reference(),
    queryFn: () => deps.getContentReference(),
    staleTime: 5 * 60_000,
  });
}

/** Tra tên theo mã cho môn, khối, độ khó, nhãn; mã không có trong danh mục thì hiện chính mã. */
export function useReferenceLookup(reference: ContentReference | undefined) {
  return useMemo(() => {
    const index = (options: CatalogOption[] | undefined) =>
      new Map((options ?? []).map((option) => [option.code, option.name]));
    const subjects = index(reference?.subjects);
    const grades = index(reference?.grades);
    const difficulties = index(reference?.difficulties);
    const tags = index(reference?.tags);
    return {
      subject: (code: string) => subjects.get(code) ?? code,
      grade: (code: string) => grades.get(code) ?? code,
      difficulty: (code: string) => difficulties.get(code) ?? code,
      tag: (code: string) => tags.get(code) ?? code,
    };
  }, [reference]);
}
