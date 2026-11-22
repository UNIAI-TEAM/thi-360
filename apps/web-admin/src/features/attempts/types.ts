import type { components } from "@thi360/api-client";

type Schemas = components["schemas"];

export const ATTEMPT_STATES = [
  "STARTING",
  "IN_PROGRESS",
  "GRADING",
  "PENDING_REVIEW",
  "SCORED",
  "GRADING_ERROR",
  "ABORTED",
] as const;

export type AttemptState = (typeof ATTEMPT_STATES)[number];

export const ATTEMPT_PART_STATES = [
  "PENDING",
  "AWAITING_CHOICE",
  "GENERATING",
  "GENERATION_FAILED",
  "OPEN",
  "CLOSED",
  "SKIPPED",
] as const;

export type AttemptPartState = (typeof ATTEMPT_PART_STATES)[number];

export const ATTEMPT_EVENT_TYPES = [
  "STARTED",
  "GENERATION_FAILED",
  "PART_OPENED",
  "PART_CLOSED",
  "CHOICE_CONFIRMED",
  "SESSION_CLAIMED",
  "SESSION_TAKEN_OVER",
  "SAVE_REJECTED",
  "SUBMITTED",
  "TIME_UP",
  "CHOICE_TIMEOUT",
  "GRADED",
  "GRADING_FAILED",
  "REGRADED",
  "COMPENSATED",
] as const;

export type AttemptEventType = (typeof ATTEMPT_EVENT_TYPES)[number];

export type AttemptEndReason = NonNullable<Schemas["AdminAttemptDetailResponse"]["endReason"]>;
export type AttemptAbortReason = NonNullable<Schemas["AdminAttemptDetailResponse"]["abortReason"]>;
export type AttemptCloseReason = NonNullable<Schemas["AdminAttemptPartView"]["closeReason"]>;
export type AttemptItemOutcome = NonNullable<Schemas["AdminAttemptItemView"]["outcome"]>;
export type AttemptQuestionType = NonNullable<Schemas["AdminAttemptItemView"]["questionType"]>;
export type AttemptAnswerValue = NonNullable<Schemas["AdminAttemptItemView"]["answer"]>;

export type AdminAttemptSummary = Schemas["AdminAttemptSummary"];
export type PageResponseAdminAttemptSummary = Schemas["PageResponseAdminAttemptSummary"];
export type AdminAttemptDetailResponse = Schemas["AdminAttemptDetailResponse"];
export type AdminAttemptPartView = Schemas["AdminAttemptPartView"];
export type AdminAttemptEventView = Schemas["AdminAttemptEventView"];
export type AdminAttemptItemView = Schemas["AdminAttemptItemView"];
export type AdminAttemptActionResponse = Schemas["AdminAttemptActionResponse"];
export type FaultAttemptRequest = Schemas["FaultAttemptRequest"];
export type CompensateAttemptRequest = Schemas["CompensateAttemptRequest"];
export type CompensateAttemptResponse = Schemas["CompensateAttemptResponse"];
export type AttemptChoiceSummary = Schemas["AttemptChoiceSummary"];
export type AttemptPosition = Schemas["AttemptPosition"];
export type ReviewGradingDetail = Schemas["ReviewGradingDetail"];

export interface ExamKindCatalogOption {
  id: string;
  code: string;
  nameVi: string;
  nameEn?: string;
}

export interface ListAdminAttemptsQuery {
  user?: string;
  exam?: string;
  state?: AttemptState;
  from?: string;
  to?: string;
  page?: number;
  size?: number;
}
