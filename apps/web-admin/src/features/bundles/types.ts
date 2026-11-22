import type { components } from "@thi360/api-client";

export type BundleSummaryResponse = components["schemas"]["BundleSummaryResponse"];
export type BundleDetailResponse = components["schemas"]["BundleDetailResponse"];
export type BundleItemView = components["schemas"]["BundleItemView"];
export type BundleBenefitsView = components["schemas"]["BundleBenefitsView"];
export type BundleCandidateResponse = components["schemas"]["BundleCandidateResponse"];
export type CreateBundleRequest = components["schemas"]["CreateBundleRequest"];
export type UpdateBundleRequest = components["schemas"]["UpdateBundleRequest"];
export type BundleRevisionRequest = components["schemas"]["BundleRevisionRequest"];
export type PageResponseBundleSummaryResponse = components["schemas"]["PageResponseBundleSummaryResponse"];
export type IntRange = components["schemas"]["IntRange"];
export type DecimalRange = components["schemas"]["DecimalRange"];
export type LocalizedName = components["schemas"]["LocalizedName"];

export type BundleStatus = BundleSummaryResponse["status"];
export type BundleSaleState = NonNullable<BundleSummaryResponse["saleState"]>;
export type BundleResultView = BundleDetailResponse["resultView"];

export const BUNDLE_RESULT_VIEWS = ["SCORE", "SCORE_AND_ANSWERS", "FULL_SOLUTION"] as const;

export type BundleStatusFilter = "ALL" | BundleStatus;

export interface BundlesListQueryParams {
  status: BundleStatusFilter;
  examKind: string;
  q: string;
  page: number;
  size: number;
  sort?: string;
}

export interface ExamKindOption {
  id: string;
  code: string;
  nameVi: string;
  nameEn?: string;
}
