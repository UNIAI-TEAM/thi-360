import type { components } from "@thi360/api-client";

type Schemas = components["schemas"];

/**
 * Queue states of an essay grading item (LT §9.2, BS §11).
 * `WAITING` = Awaiting grading, `IN_PROGRESS` = In progress (claimed within 30 min), `DONE` = Graded.
 */
export const GRADING_QUEUE_STATES = ["WAITING", "IN_PROGRESS", "DONE"] as const;
export type GradingQueueState = (typeof GRADING_QUEUE_STATES)[number];

export type GradingQueueItemView = Schemas["GradingQueueItemView"];
export type GradingQueueCountsResponse = Schemas["GradingQueueCountsResponse"];
export type GradingItemDetailResponse = Schemas["GradingItemDetailResponse"];
export type EssayGradeView = Schemas["EssayGradeView"];
export type GradingClaimedResponse = Schemas["GradingClaimedResponse"];
export type SaveEssayDraftRequest = Schemas["SaveEssayDraftRequest"];
export type FinalizeEssayRequest = Schemas["FinalizeEssayRequest"];
export type FinalizeEssayResponse = Schemas["FinalizeEssayResponse"];
export type ReopenEssayRequest = Schemas["ReopenEssayRequest"];
export type GradingRubricCriterionView = Schemas["GradingRubricCriterionView"];
export type EssayCriterionInput = Schemas["EssayCriterionInput"];
export type EssayCriterionScore = Schemas["EssayCriterionScore"];
export type PageResponseGradingQueueItemView = Schemas["PageResponseGradingQueueItemView"];

export interface ExamKindCatalogOption {
  id: string;
  code: string;
  nameVi: string;
  nameEn?: string;
}

export interface ListGradingItemsQuery {
  state?: GradingQueueState;
  exam?: string;
  kind?: string;
  from?: string;
  to?: string;
  page?: number;
  size?: number;
}

/** Query parameters for `/api/v1/admin/grading/items/counts` (BS §11: excludes `state`). */
export interface GradingCountsQuery {
  exam?: string;
  kind?: string;
  from?: string;
  to?: string;
}
