"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { keepPreviousWhileSameScope } from "@/lib/api/keep-previous";
import { defaultGradingApiDeps, GRADING_QUERY_KEYS, type GradingApiDeps } from "./api";
import type {
  FinalizeEssayRequest,
  GradingCountsQuery,
  GradingItemDetailResponse,
  ListGradingItemsQuery,
  ReopenEssayRequest,
  SaveEssayDraftRequest,
} from "./types";

export function useGradingItems(
  query: ListGradingItemsQuery,
  deps: Pick<GradingApiDeps, "listGradingItems"> = defaultGradingApiDeps,
) {
  return useQuery({
    queryKey: GRADING_QUERY_KEYS.list(query),
    queryFn: ({ signal }) => deps.listGradingItems(query, signal),
    placeholderData: keepPreviousWhileSameScope(GRADING_QUERY_KEYS.list(query)),
  });
}

export function useGradingCounts(
  query: GradingCountsQuery,
  deps: Pick<GradingApiDeps, "getGradingItemCounts"> = defaultGradingApiDeps,
) {
  return useQuery({
    queryKey: GRADING_QUERY_KEYS.counts(query),
    queryFn: ({ signal }) => deps.getGradingItemCounts(query, signal),
    placeholderData: keepPreviousWhileSameScope(GRADING_QUERY_KEYS.counts(query)),
  });
}

export function useGradingExamKinds(
  deps: Pick<GradingApiDeps, "fetchExamKindOptions"> = defaultGradingApiDeps,
) {
  return useQuery({
    queryKey: GRADING_QUERY_KEYS.examKinds(),
    queryFn: ({ signal }) => deps.fetchExamKindOptions(signal),
    staleTime: 5 * 60_000,
  });
}

export function useGradingItemDetail(
  itemId: string,
  deps: Pick<GradingApiDeps, "getGradingItemDetail"> = defaultGradingApiDeps,
) {
  return useQuery({
    queryKey: GRADING_QUERY_KEYS.detail(itemId),
    queryFn: ({ signal }) => deps.getGradingItemDetail(itemId, signal),
  });
}

export function useClaimGradingItem(
  itemId: string,
  deps: Pick<GradingApiDeps, "claimGradingItem"> = defaultGradingApiDeps,
) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: () => deps.claimGradingItem(itemId),
    onSuccess: (grade) => {
      queryClient.setQueryData<GradingItemDetailResponse>(GRADING_QUERY_KEYS.detail(itemId), (old) =>
        old
          ? {
              ...old,
              state: "IN_PROGRESS",
              activeGrade: grade,
            }
          : old,
      );
      void queryClient.invalidateQueries({ queryKey: ["grading", "list"] });
      void queryClient.invalidateQueries({ queryKey: ["grading", "counts"] });
    },
  });
}

export function useSaveEssayDraft(
  itemId: string,
  deps: Pick<GradingApiDeps, "saveEssayDraft"> = defaultGradingApiDeps,
) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (body: SaveEssayDraftRequest) => deps.saveEssayDraft(itemId, body),
    onSuccess: (grade) => {
      queryClient.setQueryData<GradingItemDetailResponse>(GRADING_QUERY_KEYS.detail(itemId), (old) =>
        old
          ? {
              ...old,
              state: "IN_PROGRESS",
              activeGrade: grade,
            }
          : old,
      );
      void queryClient.invalidateQueries({ queryKey: ["grading", "list"] });
      void queryClient.invalidateQueries({ queryKey: ["grading", "counts"] });
    },
  });
}

export function useFinalizeEssayGrade(
  itemId: string,
  deps: Pick<GradingApiDeps, "finalizeEssayGrade"> = defaultGradingApiDeps,
) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (body: FinalizeEssayRequest) => deps.finalizeEssayGrade(itemId, body),
    onSuccess: (result) => {
      queryClient.setQueryData<GradingItemDetailResponse>(GRADING_QUERY_KEYS.detail(itemId), (old) =>
        old
          ? {
              ...old,
              state: "DONE",
              regrading: false,
              activeGrade: result.grade ?? old.activeGrade,
            }
          : old,
      );
      void queryClient.invalidateQueries({ queryKey: ["grading", "list"] });
      void queryClient.invalidateQueries({ queryKey: ["grading", "counts"] });
    },
  });
}

export function useReopenEssayGrade(
  itemId: string,
  deps: Pick<GradingApiDeps, "reopenEssayGrade"> = defaultGradingApiDeps,
) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (body: ReopenEssayRequest) => deps.reopenEssayGrade(itemId, body),
    onSuccess: (grade) => {
      queryClient.setQueryData<GradingItemDetailResponse>(GRADING_QUERY_KEYS.detail(itemId), (old) => {
        if (!old) return old;
        const history = old.activeGrade
          ? [...(old.history ?? []), { ...old.activeGrade, ...(grade.id ? { supersededBy: grade.id } : {}) }]
          : old.history;
        return {
          ...old,
          state: "IN_PROGRESS",
          regrading: true,
          activeGrade: grade,
          history,
        };
      });
      void queryClient.invalidateQueries({ queryKey: ["grading", "list"] });
      void queryClient.invalidateQueries({ queryKey: ["grading", "counts"] });
    },
  });
}
