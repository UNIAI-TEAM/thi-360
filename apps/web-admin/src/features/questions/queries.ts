"use client";

import { useQuery } from "@tanstack/react-query";
import { keepPreviousWhileSameScope } from "@/lib/api/keep-previous";
import { defaultQuestionsApiDeps, QUESTIONS_QUERY_KEYS, type QuestionsApiDeps } from "./api";
import type { ListQuestionsQuery } from "./types";

export function useQuestionList(query: ListQuestionsQuery, deps: QuestionsApiDeps = defaultQuestionsApiDeps) {
  return useQuery({
    queryKey: QUESTIONS_QUERY_KEYS.list(query),
    queryFn: () => deps.listQuestions(query),
    placeholderData: keepPreviousWhileSameScope(QUESTIONS_QUERY_KEYS.list(query)),
  });
}

export function useQuestion(id: string, deps: QuestionsApiDeps = defaultQuestionsApiDeps) {
  return useQuery({
    queryKey: QUESTIONS_QUERY_KEYS.detail(id),
    queryFn: () => deps.getQuestion(id),
  });
}
