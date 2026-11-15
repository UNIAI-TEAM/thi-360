"use client";

import { useQuery } from "@tanstack/react-query";
import { keepPreviousWhileSameScope } from "@/lib/api/keep-previous";
import { defaultExamsApiDeps, EXAMS_QUERY_KEYS, type ExamsApiDeps } from "./api";
import type { ListExamsQuery } from "./types";

export function useExamList(query: ListExamsQuery, deps: ExamsApiDeps = defaultExamsApiDeps) {
  return useQuery({
    queryKey: EXAMS_QUERY_KEYS.list(query),
    queryFn: () => deps.listExams(query),
    placeholderData: keepPreviousWhileSameScope(EXAMS_QUERY_KEYS.list(query)),
  });
}

export function useExam(id: string, deps: ExamsApiDeps = defaultExamsApiDeps) {
  return useQuery({
    queryKey: EXAMS_QUERY_KEYS.detail(id),
    queryFn: () => deps.getExam(id),
  });
}

export function useExamTemplates(deps: ExamsApiDeps = defaultExamsApiDeps) {
  return useQuery({
    queryKey: EXAMS_QUERY_KEYS.templates(),
    queryFn: () => deps.listExamTemplates(),
    staleTime: 5 * 60_000,
  });
}
