"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { keepPreviousWhileSameScope } from "@/lib/api/keep-previous";
import {
  defaultQuestionBankApiDeps,
  defaultQuestionsApiDeps,
  QUESTIONS_QUERY_KEYS,
  type QuestionBankApiDeps,
  type QuestionsApiDeps,
} from "./api";
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

/** Số câu theo trạng thái cho thanh trạng thái; giữ số cũ khi đổi bộ lọc như danh sách. */
export function useQuestionStatusCounts(
  query: ListQuestionsQuery,
  deps: Pick<QuestionBankApiDeps, "getQuestionStatusCounts"> = defaultQuestionBankApiDeps,
) {
  return useQuery({
    queryKey: QUESTIONS_QUERY_KEYS.statusCounts(query),
    queryFn: () => deps.getQuestionStatusCounts(query),
    placeholderData: keepPreviousWhileSameScope(QUESTIONS_QUERY_KEYS.statusCounts(query)),
  });
}

export function useQuestionAuthors(
  deps: Pick<QuestionBankApiDeps, "listQuestionAuthors"> = defaultQuestionBankApiDeps,
) {
  return useQuery({
    queryKey: QUESTIONS_QUERY_KEYS.authors(),
    queryFn: () => deps.listQuestionAuthors(),
    staleTime: 5 * 60_000,
  });
}

/** Điều kiện chuyển sang Sẵn sàng; luôn tải lại khi mở hộp thoại để không dùng kết quả cũ. */
export function useQuestionReadiness(
  id: string | null,
  deps: Pick<QuestionBankApiDeps, "getQuestionReadiness"> = defaultQuestionBankApiDeps,
) {
  return useQuery({
    queryKey: QUESTIONS_QUERY_KEYS.readiness(id ?? ""),
    queryFn: () => deps.getQuestionReadiness(id ?? ""),
    enabled: id !== null,
    staleTime: 0,
    gcTime: 0,
  });
}

export function useQuestionRetireImpact(
  id: string | null,
  deps: Pick<QuestionBankApiDeps, "getQuestionRetireImpact"> = defaultQuestionBankApiDeps,
) {
  return useQuery({
    queryKey: QUESTIONS_QUERY_KEYS.retireImpact(id ?? ""),
    queryFn: () => deps.getQuestionRetireImpact(id ?? ""),
    enabled: id !== null,
    staleTime: 0,
    gcTime: 0,
  });
}

/** Sau khi đổi trạng thái: tải lại mọi truy vấn của câu hỏi (danh sách, số đếm, chi tiết, điều kiện). */
function useInvalidateQuestions() {
  const queryClient = useQueryClient();
  return () => queryClient.invalidateQueries({ queryKey: QUESTIONS_QUERY_KEYS.all });
}

export function useMarkQuestionReady(
  deps: Pick<QuestionBankApiDeps, "markQuestionReady"> = defaultQuestionBankApiDeps,
) {
  const invalidate = useInvalidateQuestions();
  return useMutation({
    mutationFn: (variables: { id: string; expectedRevision: number }) => deps.markQuestionReady(variables),
    onSettled: invalidate,
  });
}

export function useRetireQuestion(deps: Pick<QuestionBankApiDeps, "retireQuestion"> = defaultQuestionBankApiDeps) {
  const invalidate = useInvalidateQuestions();
  return useMutation({
    mutationFn: (variables: { id: string; expectedRevision: number }) => deps.retireQuestion(variables),
    onSettled: invalidate,
  });
}

export function useBulkMarkQuestionsReady(
  deps: Pick<QuestionBankApiDeps, "bulkMarkQuestionsReady"> = defaultQuestionBankApiDeps,
) {
  const invalidate = useInvalidateQuestions();
  return useMutation({
    mutationFn: (variables: { ids: string[] }) => deps.bulkMarkQuestionsReady(variables),
    onSettled: invalidate,
  });
}

export function useBulkRetireQuestions(
  deps: Pick<QuestionBankApiDeps, "bulkRetireQuestions"> = defaultQuestionBankApiDeps,
) {
  const invalidate = useInvalidateQuestions();
  return useMutation({
    mutationFn: (variables: { ids: string[] }) => deps.bulkRetireQuestions(variables),
    onSettled: invalidate,
  });
}
