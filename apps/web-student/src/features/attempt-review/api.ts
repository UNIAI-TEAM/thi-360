import type { components } from "@thi360/api-client";
import { createBrowserApiClient, toApiStatusError } from "@/lib/api/client";

export type AttemptReviewResponse = components["schemas"]["AttemptReviewResponse"];
export type AttemptReviewPart = components["schemas"]["AttemptReviewPart"];
export type AttemptReviewItem = components["schemas"]["AttemptReviewItem"];
export type AttemptReviewQuestionBody = components["schemas"]["AttemptReviewQuestionBody"];
export type AttemptReviewExpectedAnswer = components["schemas"]["AttemptReviewExpectedAnswer"];
export type ReviewGradingDetail = components["schemas"]["ReviewGradingDetail"];
export type AttemptReviewEssayFeedback = components["schemas"]["AttemptReviewEssayFeedback"];
export type AttemptReviewEssayCriterionScore = components["schemas"]["AttemptReviewEssayCriterionScore"];

export const ATTEMPT_REVIEW_QUERY_KEY = (attemptId: string, userId: string | null = null) =>
  ["me", "attempts", "review", userId, attemptId] as const;

export type AttemptReviewLoader = (attemptId: string) => Promise<AttemptReviewResponse>;

export async function loadAttemptReview(attemptId: string): Promise<AttemptReviewResponse> {
  const client = createBrowserApiClient();
  const { data, error, response } = await client.GET("/api/v1/me/attempts/{attemptId}/review", {
    params: { path: { attemptId } },
  });
  if (!response.ok || !data) {
    throw toApiStatusError(response.status, error as never);
  }
  return data;
}
