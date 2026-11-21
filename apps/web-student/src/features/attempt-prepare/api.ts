import type { components } from "@thi360/api-client";
import { createBrowserApiClient, toApiStatusError } from "@/lib/api/client";

export type AttemptPrepareResponse = components["schemas"]["AttemptPrepareResponse"];
export type PreparePartView = components["schemas"]["PreparePartView"];
export type PreChoiceView = components["schemas"]["PreChoiceView"];
export type AfterStartChoiceView = components["schemas"]["AfterStartChoiceView"];
export type ChoiceOptionView = components["schemas"]["ChoiceOptionView"];
export type ChoiceSubjectView = components["schemas"]["ChoiceSubjectView"];
export type ListeningPolicyView = components["schemas"]["ListeningPolicyView"];
export type OmittedGroupView = components["schemas"]["OmittedGroupView"];
export type StartAttemptRequest = components["schemas"]["StartAttemptRequest"];
export type StartAttemptResponse = components["schemas"]["StartAttemptResponse"];
export type AttemptStateResponse = components["schemas"]["AttemptStateResponse"];

export type ChoiceOptionWithMetrics = ChoiceOptionView & {
  questionCount?: number | null;
  durationMinutes?: number | null;
  maxPoints?: number | null;
};

export type AttemptPrepareLoader = (entitlementId: string) => Promise<AttemptPrepareResponse>;
export type AttemptStarter = (
  body: StartAttemptRequest,
  idempotencyKey: string,
) => Promise<StartAttemptResponse>;
export type AttemptStateLoader = (attemptId: string) => Promise<AttemptStateResponse>;

export const ATTEMPT_PREPARE_QUERY_KEY = (userId: string, entitlementId: string) =>
  ["attempt-prepare", userId, entitlementId] as const;

export async function loadAttemptPrepare(entitlementId: string): Promise<AttemptPrepareResponse> {
  const client = createBrowserApiClient();
  const { data, error, response } = await client.GET("/api/v1/me/entitlements/{entitlementId}/prepare", {
    params: { path: { entitlementId } },
  });
  if (!response.ok || !data) {
    throw toApiStatusError(response.status, error);
  }
  return data;
}

export async function startAttempt(
  body: StartAttemptRequest,
  idempotencyKey: string,
): Promise<StartAttemptResponse> {
  const client = createBrowserApiClient();
  const { data, error, response } = await client.POST("/api/v1/me/attempts", {
    body,
    params: {
      header: { "Idempotency-Key": idempotencyKey },
    },
  });
  if (!response.ok || !data) {
    throw toApiStatusError(response.status, error);
  }
  return data;
}

export async function loadAttemptState(attemptId: string): Promise<AttemptStateResponse> {
  const client = createBrowserApiClient();
  const { data, error, response } = await client.GET("/api/v1/me/attempts/{attemptId}", {
    params: { path: { attemptId } },
  });
  if (!response.ok || !data) {
    throw toApiStatusError(response.status, error);
  }
  return data;
}
