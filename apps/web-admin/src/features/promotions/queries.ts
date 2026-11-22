import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";

import { keepPreviousWhileSameScope } from "@/lib/api/keep-previous";

import {
  createPromotion,
  deletePromotion,
  fetchExamKindOptions,
  getPromotion,
  listPromotions,
  previewPromotionPrice,
  resumePromotion,
  searchClasses,
  searchPublishedExams,
  searchUsers,
  stopPromotion,
  updatePromotion,
} from "./api";
import { promotionKeys } from "./keys";
import type {
  CreatePromotionRequest,
  PromotionDetailQueryParams,
  PromotionsListQueryParams,
  UpdatePromotionRequest,
} from "./types";

const DEFAULT_DETAIL_PARAMS: PromotionDetailQueryParams = {
  page: 0,
  size: 20,
  redemptionState: "ALL",
  redemptionQ: "",
};

export function usePromotionsListQuery(params: PromotionsListQueryParams) {
  const key = promotionKeys.list(params);
  return useQuery({
    queryKey: key,
    queryFn: ({ signal }) => listPromotions(params, signal),
    placeholderData: keepPreviousWhileSameScope(key),
  });
}

export function usePromotionDetailQuery(
  id: string,
  params: PromotionDetailQueryParams = DEFAULT_DETAIL_PARAMS,
  enabled = true,
) {
  const key = promotionKeys.detail(id, params);
  return useQuery({
    queryKey: key,
    queryFn: ({ signal }) => getPromotion(id, params, signal),
    enabled: Boolean(id) && enabled,
    placeholderData: keepPreviousWhileSameScope(key),
  });
}

export function useExamKindOptionsQuery(enabled = true) {
  return useQuery({
    queryKey: promotionKeys.examKinds(),
    queryFn: ({ signal }) => fetchExamKindOptions(signal),
    enabled,
    staleTime: 5 * 60 * 1000,
  });
}

export function useExamPickerQuery(q: string, enabled: boolean, size = 20) {
  return useQuery({
    queryKey: promotionKeys.examPicker(q, size),
    queryFn: ({ signal }) => searchPublishedExams(q, signal, size),
    enabled,
  });
}

export function useClassPickerQuery(q: string, enabled: boolean, size = 20) {
  return useQuery({
    queryKey: promotionKeys.classPicker(q, size),
    queryFn: ({ signal }) => searchClasses(q, signal, size),
    enabled,
  });
}

export function useUserPickerQuery(q: string, enabled: boolean) {
  return useQuery({
    queryKey: promotionKeys.userPicker(q),
    queryFn: ({ signal }) => searchUsers(q, signal),
    enabled,
  });
}

export function usePricePreviewQuery(promotionId: string | undefined, examId: string | undefined) {
  const pid = promotionId ?? "";
  const eid = examId ?? "";
  return useQuery({
    queryKey: promotionKeys.pricePreview(pid, eid),
    queryFn: ({ signal }) => previewPromotionPrice(pid, eid, signal),
    enabled: Boolean(pid) && Boolean(eid),
    retry: false,
  });
}

export function useCreatePromotionMutation() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ body, idempotencyKey }: { body: CreatePromotionRequest; idempotencyKey: string }) =>
      createPromotion(body, idempotencyKey),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: promotionKeys.lists() });
    },
  });
}

export function useUpdatePromotionMutation(id: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (body: UpdatePromotionRequest) => updatePromotion(id, body),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: promotionKeys.lists() });
      void queryClient.invalidateQueries({ queryKey: promotionKeys.detailBase(id) });
      void queryClient.invalidateQueries({ queryKey: promotionKeys.pricePreviewsFor(id) });
    },
  });
}

export function useStopPromotionMutation() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ id, expectedRevision }: { id: string; expectedRevision: number }) =>
      stopPromotion(id, expectedRevision),
    onSuccess: (_data, variables) => {
      void queryClient.invalidateQueries({ queryKey: promotionKeys.lists() });
      void queryClient.invalidateQueries({ queryKey: promotionKeys.detailBase(variables.id) });
      void queryClient.invalidateQueries({
        queryKey: promotionKeys.pricePreviewsFor(variables.id),
      });
    },
  });
}

export function useResumePromotionMutation() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ id, expectedRevision }: { id: string; expectedRevision: number }) =>
      resumePromotion(id, expectedRevision),
    onSuccess: (_data, variables) => {
      void queryClient.invalidateQueries({ queryKey: promotionKeys.lists() });
      void queryClient.invalidateQueries({ queryKey: promotionKeys.detailBase(variables.id) });
      void queryClient.invalidateQueries({
        queryKey: promotionKeys.pricePreviewsFor(variables.id),
      });
    },
  });
}

export function useDeletePromotionMutation() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (id: string) => deletePromotion(id),
    onSuccess: (_data, id) => {
      void queryClient.invalidateQueries({ queryKey: promotionKeys.lists() });
      void queryClient.invalidateQueries({ queryKey: promotionKeys.detailBase(id) });
      void queryClient.invalidateQueries({ queryKey: promotionKeys.pricePreviewsFor(id) });
    },
  });
}
