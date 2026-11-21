import type { components } from "@thi360/api-client";
import { loadAttemptStartContext } from "@/features/attempt-draft";
import {
  type AfterStartChoiceView,
  type AttemptPrepareLoader,
  type ChoiceOptionView,
  type ChoiceSubjectView,
  loadAttemptPrepare,
  loadAttemptState,
} from "@/features/attempt-prepare/api";
import { loadMyExams, type MyExamsLoader } from "@/features/my-exams/api";
import { ApiStatusError, createBrowserApiClient, toApiStatusError } from "@/lib/api/client";

export type ClaimSessionRequest = components["schemas"]["ClaimSessionRequest"];
export type ClaimSessionResponse = components["schemas"]["ClaimSessionResponse"];
export type AttemptSessionConflictResponse = components["schemas"]["AttemptSessionConflictResponse"];
export type HeartbeatResponse = components["schemas"]["HeartbeatResponse"];
export type SaveAnswersRequest = components["schemas"]["SaveAnswersRequest"];
export type SaveAnswersResponse = components["schemas"]["SaveAnswersResponse"];
export type FinishPartRequest = components["schemas"]["FinishPartRequest"];
export type FinishPartResponse = components["schemas"]["FinishPartResponse"];
export type SubmitAttemptRequest = components["schemas"]["SubmitAttemptRequest"];
export type SubmitAttemptResponse = components["schemas"]["SubmitAttemptResponse"];
export type ConfirmPartChoiceRequest = components["schemas"]["ConfirmPartChoiceRequest"];
export type PartChoiceResponse = components["schemas"]["PartChoiceResponse"];
export type RetryPartRequest = components["schemas"]["RetryPartRequest"];
export type FinalAnswerChange = components["schemas"]["FinalAnswerChange"];

export { loadAttemptState, type AfterStartChoiceView, type ChoiceOptionView, type ChoiceSubjectView };

export class AttemptApiError extends ApiStatusError {
  readonly retryAfterSeconds?: number;
  readonly lastSeenAt?: string;

  constructor(
    status: number,
    message = "",
    options: {
      code?: string;
      fieldErrors?: ApiStatusError["fieldErrors"];
      traceId?: string;
      retryAfterSeconds?: number;
      lastSeenAt?: string;
    } = {},
  ) {
    super(status, message, options);
    this.name = "AttemptApiError";
    this.retryAfterSeconds = options.retryAfterSeconds;
    this.lastSeenAt = options.lastSeenAt;
  }
}

export type ClaimEditSessionResult =
  | { conflict: false; sessionId: string; serverNow: string }
  | { conflict: true; code: string; message: string; lastSeenAt?: string };

export function parseRetryAfterSeconds(headerValue: string | null): number | undefined {
  if (!headerValue) return undefined;
  const trimmed = headerValue.trim();
  if (!trimmed) return undefined;
  const seconds = Number(trimmed);
  if (Number.isFinite(seconds) && seconds >= 0) {
    return Math.ceil(seconds);
  }
  const targetMs = Date.parse(trimmed);
  if (Number.isFinite(targetMs)) {
    return Math.max(1, Math.ceil((targetMs - Date.now()) / 1000));
  }
  return undefined;
}

export async function claimEditSession(
  attemptId: string,
  body: ClaimSessionRequest,
): Promise<ClaimEditSessionResult> {
  const client = createBrowserApiClient();
  const { data, error, response } = await client.POST("/api/v1/me/attempts/{attemptId}/session", {
    params: { path: { attemptId } },
    body,
  });

  if (response.status === 409) {
    const conflict = error as AttemptSessionConflictResponse | undefined;
    if (conflict?.code === "ATTEMPT_OPEN_ELSEWHERE") {
      return {
        conflict: true,
        code: conflict.code,
        message: conflict.message ?? "",
        lastSeenAt: conflict.lastSeenAt,
      };
    }
    throw new AttemptApiError(response.status, conflict?.message ?? "", {
      code: conflict?.code,
      fieldErrors: conflict?.fieldErrors,
      traceId: conflict?.traceId,
      lastSeenAt: conflict?.lastSeenAt,
    });
  }

  if (!response.ok || !data) {
    throw toApiStatusError(response.status, error);
  }

  return {
    conflict: false,
    sessionId: data.sessionId,
    serverNow: data.serverNow,
  };
}

export async function sendHeartbeat(
  attemptId: string,
  sessionId: string,
): Promise<HeartbeatResponse> {
  const client = createBrowserApiClient();
  const { data, error, response } = await client.POST("/api/v1/me/attempts/{attemptId}/heartbeat", {
    params: {
      path: { attemptId },
      header: { "X-Attempt-Session": sessionId },
    },
  });
  if (!response.ok || !data) {
    throw toApiStatusError(response.status, error);
  }
  return data;
}

function toAttemptApiError(
  response: Response,
  error: {
    message?: string;
    code?: string;
    fieldErrors?: ApiStatusError["fieldErrors"];
    traceId?: string;
  } | undefined,
): AttemptApiError {
  const retryAfterSeconds =
    response.status === 429 ? parseRetryAfterSeconds(response.headers.get("Retry-After")) : undefined;
  return new AttemptApiError(response.status, error?.message ?? "", {
    code: error?.code,
    fieldErrors: error?.fieldErrors,
    traceId: error?.traceId,
    retryAfterSeconds,
  });
}

export async function saveAnswers(
  attemptId: string,
  sessionId: string,
  body: SaveAnswersRequest,
): Promise<SaveAnswersResponse> {
  const client = createBrowserApiClient();
  const { data, error, response } = await client.PUT("/api/v1/me/attempts/{attemptId}/answers", {
    params: {
      path: { attemptId },
      header: { "X-Attempt-Session": sessionId },
    },
    body,
  });
  if (!response.ok || !data) {
    throw toAttemptApiError(response, error);
  }
  return data;
}

export async function finishPart(
  attemptId: string,
  partId: string,
  sessionId: string,
  body: FinishPartRequest,
): Promise<FinishPartResponse> {
  const client = createBrowserApiClient();
  const { data, error, response } = await client.POST(
    "/api/v1/me/attempts/{attemptId}/parts/{partId}/finish",
    {
      params: {
        path: { attemptId, partId },
        header: { "X-Attempt-Session": sessionId },
      },
      body,
    },
  );
  if (!response.ok || !data) {
    throw toApiStatusError(response.status, error);
  }
  return data;
}

export async function submitAttempt(
  attemptId: string,
  sessionId: string,
  idempotencyKey: string,
  body: SubmitAttemptRequest,
): Promise<SubmitAttemptResponse> {
  const client = createBrowserApiClient();
  const { data, error, response } = await client.POST("/api/v1/me/attempts/{attemptId}/submit", {
    params: {
      path: { attemptId },
      header: {
        "Idempotency-Key": idempotencyKey,
        "X-Attempt-Session": sessionId,
      },
    },
    body,
  });
  if (!response.ok || !data) {
    throw toApiStatusError(response.status, error);
  }
  return data;
}

export async function confirmPartChoice(
  attemptId: string,
  partId: string,
  sessionId: string,
  idempotencyKey: string,
  body: ConfirmPartChoiceRequest,
): Promise<PartChoiceResponse> {
  const client = createBrowserApiClient();
  const { data, error, response } = await client.POST(
    "/api/v1/me/attempts/{attemptId}/parts/{partId}/choice",
    {
      params: {
        path: { attemptId, partId },
        header: {
          "Idempotency-Key": idempotencyKey,
          "X-Attempt-Session": sessionId,
        },
      },
      body,
    },
  );
  if (!response.ok || !data) {
    throw toAttemptApiError(response, error);
  }
  return data;
}

export async function retryPartGeneration(
  attemptId: string,
  partId: string,
  sessionId: string,
  idempotencyKey: string,
  body: RetryPartRequest = {},
): Promise<PartChoiceResponse> {
  const client = createBrowserApiClient();
  const { data, error, response } = await client.POST(
    "/api/v1/me/attempts/{attemptId}/parts/{partId}/retry",
    {
      params: {
        path: { attemptId, partId },
        header: {
          "Idempotency-Key": idempotencyKey,
          "X-Attempt-Session": sessionId,
        },
      },
      body,
    },
  );
  if (!response.ok || !data) {
    throw toAttemptApiError(response, error);
  }
  return data;
}

export async function loadAfterStartChoicesForAttempt(
  attemptId: string,
  userId: string,
  deps: {
    loadStartContext?: typeof loadAttemptStartContext;
    loadExams?: MyExamsLoader;
    loadPrepare?: AttemptPrepareLoader;
  } = {},
): Promise<AfterStartChoiceView[]> {
  const loadStart = deps.loadStartContext ?? loadAttemptStartContext;
  const fetchExams = deps.loadExams ?? loadMyExams;
  const fetchPrepare = deps.loadPrepare ?? loadAttemptPrepare;

  let entitlementId: string | null = null;
  try {
    const startCtx = await loadStart(userId, attemptId);
    if (startCtx?.entitlementId) {
      entitlementId = startCtx.entitlementId;
    }
  } catch {
    // Fallback to my-exams below.
  }

  if (!entitlementId) {
    const myExams = await fetchExams();
    const matched = myExams.items.find((card) => card.activeAttemptId === attemptId);
    entitlementId = matched?.entitlementId ?? null;
  }

  if (!entitlementId) {
    return [];
  }

  const prepare = await fetchPrepare(entitlementId);
  return prepare.afterStartChoices ?? [];
}
