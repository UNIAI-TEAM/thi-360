import type { components } from "@thi360/api-client";
import { createBrowserApiClient, toApiStatusError } from "@/lib/api/client";

export type AttemptHistoryItem = components["schemas"]["AttemptHistoryItem"];
export type PageResponseAttemptHistoryItem = components["schemas"]["PageResponseAttemptHistoryItem"];

export const HISTORY_PAGE_SIZE = 20;

export interface AttemptHistoryQueryParams {
  q?: string;
  kind?: string;
  scope?: string;
  examId?: string;
  from?: string;
  to?: string;
  page: number;
  size: number;
}

export const ATTEMPT_HISTORY_QUERY_KEY = (
  params: AttemptHistoryQueryParams,
  userId: string | null = null,
) => ["me", "attempts", userId, params] as const;

export type AttemptHistoryLoader = (
  params: AttemptHistoryQueryParams,
) => Promise<PageResponseAttemptHistoryItem>;

export async function loadAttemptHistory(
  params: AttemptHistoryQueryParams,
): Promise<PageResponseAttemptHistoryItem> {
  const client = createBrowserApiClient();
  const query: Record<string, string | number> = {
    page: params.page,
    size: params.size,
  };
  if (params.q) query.q = params.q;
  if (params.kind) query.kind = params.kind;
  if (params.scope) query.scope = params.scope;
  if (params.examId) query.examId = params.examId;
  if (params.from) query.from = params.from;
  if (params.to) query.to = params.to;

  const { data, error, response } = await client.GET("/api/v1/me/attempts", {
    params: { query },
  });
  if (!response.ok || !data) {
    throw toApiStatusError(response.status, error as never);
  }
  return data;
}
