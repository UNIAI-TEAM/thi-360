import type { components } from "@thi360/api-client";
import { createBrowserApiClient, toApiStatusError } from "@/lib/api/client";

export type AttemptResultResponse = components["schemas"]["AttemptResultResponse"];
export type AttemptResultPart = components["schemas"]["AttemptResultPart"];
export type AttemptResultSubject = components["schemas"]["AttemptResultSubject"];
export type AttemptResultUnitStats = components["schemas"]["AttemptResultUnitStats"];
export type AttemptResultVactComponent = components["schemas"]["AttemptResultVactComponent"];
export type AttemptResultVactSubsection = components["schemas"]["AttemptResultVactSubsection"];
export type AttemptChoiceSummary = components["schemas"]["AttemptChoiceSummary"];
export type CatalogOptionResponse = components["schemas"]["CatalogOptionResponse"];

export type CatalogGroupCode = "EXAM" | "SUBJECT";

export const GRADING_POLL_INTERVAL_MS = 3_000;
export const GRADING_POLL_TIMEOUT_MS = 120_000;

export const ATTEMPT_RESULT_QUERY_KEY = (attemptId: string, userId: string | null = null) =>
  ["me", "attempts", "result", userId, attemptId] as const;

export const CATALOG_OPTIONS_QUERY_KEY = (group: CatalogGroupCode) =>
  ["catalog", "options", group] as const;

export type AttemptResultLoader = (attemptId: string) => Promise<AttemptResultResponse>;
export type CatalogOptionsLoader = () => Promise<CatalogOptionResponse[]>;

export async function loadAttemptResult(attemptId: string): Promise<AttemptResultResponse> {
  const client = createBrowserApiClient();
  const { data, error, response } = await client.GET("/api/v1/me/attempts/{attemptId}/result", {
    params: { path: { attemptId } },
  });
  if (!response.ok || !data) {
    throw toApiStatusError(response.status, error as never);
  }
  return data;
}

export async function loadCatalogOptions(group: CatalogGroupCode): Promise<CatalogOptionResponse[]> {
  const client = createBrowserApiClient();
  const { data, error, response } = await client.GET("/api/v1/catalog/options", {
    params: { query: { group } },
  });
  if (!response.ok || !data) {
    throw toApiStatusError(response.status, error as never);
  }
  return data;
}

export const loadExamKindOptions: CatalogOptionsLoader = () => loadCatalogOptions("EXAM");
export const loadSubjectOptions: CatalogOptionsLoader = () => loadCatalogOptions("SUBJECT");
