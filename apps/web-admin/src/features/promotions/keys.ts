import type { PromotionDetailQueryParams, PromotionsListQueryParams } from "./types";

export const promotionKeys = {
  all: ["promotions"] as const,
  lists: () => [...promotionKeys.all, "list"] as const,
  list: (params: PromotionsListQueryParams) =>
    [...promotionKeys.lists(), params.kind, params] as const,
  details: () => [...promotionKeys.all, "detail"] as const,
  detailBase: (id: string) => [...promotionKeys.details(), id] as const,
  detail: (id: string, params: PromotionDetailQueryParams) =>
    [...promotionKeys.detailBase(id), params] as const,
  examKinds: () => [...promotionKeys.all, "exam-kinds"] as const,
  examPicker: (q: string) => [...promotionKeys.all, "exam-picker", q] as const,
  classPicker: (q: string) => [...promotionKeys.all, "class-picker", q] as const,
  userPicker: (q: string) => [...promotionKeys.all, "user-picker", q] as const,
  pricePreviewsFor: (promotionId: string) =>
    [...promotionKeys.all, "price-preview", promotionId] as const,
  pricePreview: (promotionId: string, examId: string) =>
    [...promotionKeys.pricePreviewsFor(promotionId), examId] as const,
};
