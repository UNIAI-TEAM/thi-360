"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { keepPreviousWhileSameScope } from "@/lib/api/keep-previous";
import { defaultGradingApiDeps, GRADING_QUERY_KEYS, type GradingApiDeps } from "./api";
import type { ListGradingAttemptsQuery, SaveGradingRequest } from "./types";

export function useGradingAttempts(
  query: ListGradingAttemptsQuery,
  deps: Pick<GradingApiDeps, "listGradingAttempts"> = defaultGradingApiDeps,
) {
  return useQuery({
    queryKey: GRADING_QUERY_KEYS.list(query),
    queryFn: () => deps.listGradingAttempts(query),
    placeholderData: keepPreviousWhileSameScope(GRADING_QUERY_KEYS.list(query)),
  });
}

/** Số lượt theo trạng thái cho dải trạng thái; giữ số cũ khi đổi bộ lọc như danh sách. */
export function useGradingStatusCounts(
  query: ListGradingAttemptsQuery,
  deps: Pick<GradingApiDeps, "getGradingStatusCounts"> = defaultGradingApiDeps,
) {
  return useQuery({
    queryKey: GRADING_QUERY_KEYS.statusCounts(query),
    queryFn: () => deps.getGradingStatusCounts(query),
    placeholderData: keepPreviousWhileSameScope(GRADING_QUERY_KEYS.statusCounts(query)),
  });
}

export function useGradingFilterOptions(
  deps: Pick<GradingApiDeps, "getGradingFilterOptions"> = defaultGradingApiDeps,
) {
  return useQuery({
    queryKey: GRADING_QUERY_KEYS.filterOptions(),
    queryFn: () => deps.getGradingFilterOptions(),
    staleTime: 5 * 60_000,
  });
}

export function useGradingAttempt(id: string, deps: Pick<GradingApiDeps, "getGradingAttempt"> = defaultGradingApiDeps) {
  return useQuery({
    queryKey: GRADING_QUERY_KEYS.detail(id),
    queryFn: () => deps.getGradingAttempt(id),
  });
}

/** Sau khi lưu: cập nhật ngay chi tiết bằng kết quả trả về, rồi tải lại danh sách, số đếm và bộ lọc. */
function useGradingMutation(mutationFn: (request: SaveGradingRequest) => ReturnType<GradingApiDeps["saveGradingDraft"]>) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn,
    onSuccess: (detail) => {
      queryClient.setQueryData(GRADING_QUERY_KEYS.detail(detail.id), detail);
    },
    onSettled: () => queryClient.invalidateQueries({ queryKey: GRADING_QUERY_KEYS.all }),
  });
}

export function useSaveGradingDraft(deps: Pick<GradingApiDeps, "saveGradingDraft"> = defaultGradingApiDeps) {
  return useGradingMutation((request) => deps.saveGradingDraft(request));
}

export function useFinishGrading(deps: Pick<GradingApiDeps, "finishGrading"> = defaultGradingApiDeps) {
  return useGradingMutation((request) => deps.finishGrading(request));
}
