"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { keepPreviousWhileSameScope } from "@/lib/api/keep-previous";
import { ATTEMPTS_QUERY_KEYS, defaultAttemptApiDeps, type AttemptApiDeps } from "./api";
import type {
  CompensateAttemptRequest,
  FaultAttemptRequest,
  ListAdminAttemptsQuery,
} from "./types";

export function useAdminAttempts(
  query: ListAdminAttemptsQuery,
  deps: Pick<AttemptApiDeps, "listAdminAttempts"> = defaultAttemptApiDeps,
) {
  return useQuery({
    queryKey: ATTEMPTS_QUERY_KEYS.list(query),
    queryFn: ({ signal }) => deps.listAdminAttempts(query, signal),
    placeholderData: keepPreviousWhileSameScope(ATTEMPTS_QUERY_KEYS.list(query)),
  });
}

export function useAdminAttemptDetail(
  attemptId: string,
  deps: Pick<AttemptApiDeps, "getAdminAttemptDetail"> = defaultAttemptApiDeps,
) {
  return useQuery({
    queryKey: ATTEMPTS_QUERY_KEYS.detail(attemptId),
    queryFn: ({ signal }) => deps.getAdminAttemptDetail(attemptId, signal),
  });
}

export function useAttemptExamKinds(
  deps: Pick<AttemptApiDeps, "fetchExamKindOptions"> = defaultAttemptApiDeps,
) {
  return useQuery({
    queryKey: ATTEMPTS_QUERY_KEYS.examKinds(),
    queryFn: ({ signal }) => deps.fetchExamKindOptions(signal),
    staleTime: 5 * 60_000,
  });
}

export function useRegradeAttempt(
  attemptId: string,
  deps: Pick<AttemptApiDeps, "regradeAdminAttempt"> = defaultAttemptApiDeps,
) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: () => deps.regradeAdminAttempt(attemptId),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ATTEMPTS_QUERY_KEYS.detail(attemptId) });
      void queryClient.invalidateQueries({ queryKey: ATTEMPTS_QUERY_KEYS.lists() });
    },
  });
}

export function useFaultAttempt(
  attemptId: string,
  deps: Pick<AttemptApiDeps, "faultAdminAttempt"> = defaultAttemptApiDeps,
) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (body: FaultAttemptRequest) => deps.faultAdminAttempt(attemptId, body),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ATTEMPTS_QUERY_KEYS.detail(attemptId) });
      void queryClient.invalidateQueries({ queryKey: ATTEMPTS_QUERY_KEYS.lists() });
    },
  });
}

export function useCompensateAttempt(
  attemptId: string,
  deps: Pick<AttemptApiDeps, "compensateAdminAttempt"> = defaultAttemptApiDeps,
) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({
      body,
      idempotencyKey,
    }: {
      body: CompensateAttemptRequest;
      idempotencyKey: string;
    }) => deps.compensateAdminAttempt(attemptId, body, idempotencyKey),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ATTEMPTS_QUERY_KEYS.detail(attemptId) });
      void queryClient.invalidateQueries({ queryKey: ATTEMPTS_QUERY_KEYS.lists() });
    },
  });
}
