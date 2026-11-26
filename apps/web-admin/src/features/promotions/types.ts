import type { components } from "@thi360/api-client";

export type PromotionSummary = components["schemas"]["PromotionSummary"];
export type PromotionDetail = components["schemas"]["PromotionDetail"];
export type CreatePromotionRequest = components["schemas"]["CreatePromotionRequest"];
export type UpdatePromotionRequest = components["schemas"]["UpdatePromotionRequest"];
export type PromotionRedemptionView = components["schemas"]["PromotionRedemptionView"];
export type PromotionChangeLogView = components["schemas"]["PromotionChangeLogView"];
export type PromotionFieldChange = components["schemas"]["PromotionFieldChange"];
export type PromotionAudienceClassView = components["schemas"]["PromotionAudienceClassView"];
export type PromotionAudienceUserView = components["schemas"]["PromotionAudienceUserView"];
export type PromotionScopeItemView = components["schemas"]["PromotionScopeItemView"];
export type CapWarning = components["schemas"]["CapWarning"];
export type PriceQuote = components["schemas"]["PriceQuote"];
export type ProductGoneResponse = components["schemas"]["ProductGoneResponse"];
export type PageResponsePromotionSummary = components["schemas"]["PageResponsePromotionSummary"];

export type PromotionKind = PromotionSummary["kind"];
export type PromotionDiscountType = PromotionSummary["discountType"];
export type PromotionStatus = PromotionSummary["status"];
export type PromotionState = PromotionSummary["state"];
export type PromotionScope = PromotionSummary["scope"];
export type PromotionAudience = "ALL" | "NEW_ACCOUNT" | "SELECTED";

export type PromotionStateFilter = "ALL" | "UPCOMING" | "RUNNING" | "ENDED" | "STOPPED";
/** Lọc nhanh theo loại trên danh sách gộp (uniworkos UNI-1713); `ALL` gửi API không kèm `kind`. */
export type PromotionKindFilter = "ALL" | PromotionKind;
export type RedemptionStateFilter = "ALL" | "RESERVED" | "CONFIRMED" | "RELEASED";

export interface PromotionsListQueryParams {
  kind: PromotionKindFilter;
  stateFilter: PromotionStateFilter;
  q: string;
  page: number;
  size: number;
}

export interface PromotionDetailQueryParams {
  page: number;
  size: number;
  redemptionState: RedemptionStateFilter;
  redemptionQ: string;
}

export interface ExamKindOption {
  id: string;
  code: string;
  nameVi: string;
  nameEn?: string;
}

export interface ExamPickerOption {
  id: string;
  code: string;
  name: string;
}

export interface ClassPickerOption {
  id: string;
  code: string;
  name: string;
}

export interface UserPickerOption {
  id: string;
  username: string;
  fullName: string;
  email?: string;
}
