import type { components } from "@thi360/api-client";
import {
  AudioAdapterError,
  type AudioPlayAdapter,
  type AudioPlaybackSnapshot,
} from "@thi360/ui/question";
import { ApiStatusError, createBrowserApiClient } from "@/lib/api/client";
import { toAttemptAssetUrl } from "../attempt-assets";
import type { AttemptStateResponse } from "../mappers";

export type AttemptAudioPlayView = components["schemas"]["AttemptAudioPlayView"];
export type AttemptAudioPlayResponse = components["schemas"]["AttemptAudioPlayResponse"];

export interface AttemptAudioApi {
  openPlay: typeof openAttemptAudioPlay;
  resumePlay: typeof resumeAttemptAudioPlay;
  reportProgress: typeof reportAttemptAudioProgress;
  endPlay: typeof endAttemptAudioPlay;
}

export interface CreateAttemptAudioAdapterOptions {
  attemptId: string;
  assetId: string;
  maxPlays: number;
  getSessionId: () => string | null;
  reloadAttemptState: () => Promise<AttemptStateResponse>;
  audioApi?: AttemptAudioApi;
}

interface RawAudioErrorShape {
  code?: unknown;
  message?: unknown;
  playId?: unknown;
  positionMs?: unknown;
}

function readMessage(error: unknown): string | undefined {
  if (error && typeof error === "object" && "message" in error) {
    const msg = (error as RawAudioErrorShape).message;
    if (typeof msg === "string" && msg.length > 0) return msg;
  }
  return undefined;
}

/**
 * Maps an HTTP error payload from the attempt audio endpoints into an {@link AudioAdapterError}.
 */
export function mapAudioHttpError(status: number, error: unknown): AudioAdapterError {
  const message = readMessage(error);
  if (status === 401) {
    return new AudioAdapterError("UNAUTHORIZED", { status: 401, message });
  }

  const raw = error && typeof error === "object" ? (error as RawAudioErrorShape) : undefined;
  const code = typeof raw?.code === "string" ? raw.code : undefined;

  if (status === 409) {
    switch (code) {
      case "AUDIO_PLAY_IN_PROGRESS":
        return new AudioAdapterError("AUDIO_PLAY_IN_PROGRESS", {
          status: 409,
          message,
          playId: typeof raw?.playId === "string" ? raw.playId : null,
        });
      case "AUDIO_SEEK_REJECTED":
        return new AudioAdapterError("AUDIO_SEEK_REJECTED", {
          status: 409,
          message,
          positionMs: typeof raw?.positionMs === "number" ? raw.positionMs : null,
        });
      case "AUDIO_PLAYS_EXHAUSTED":
      case "AUDIO_PLAY_STALE":
      case "PART_CLOSED":
      case "TIME_UP":
      case "ATTEMPT_SESSION_REVOKED":
        return new AudioAdapterError(code, { status: 409, message });
      default:
        return new AudioAdapterError("UNKNOWN", { status: 409, message });
    }
  }

  return new AudioAdapterError("UNKNOWN", { status, message });
}

function normalizeAudioThrownError(error: unknown): AudioAdapterError {
  if (error instanceof AudioAdapterError) {
    return error;
  }
  if (error instanceof ApiStatusError) {
    return mapAudioHttpError(error.status, error);
  }
  const message = error instanceof Error ? error.message : "Network error";
  return new AudioAdapterError("NETWORK_ERROR", { message });
}

async function executeAudioRequest<T>(
  requestFn: () => Promise<{ data?: T; error?: unknown; response: Response }>,
): Promise<T> {
  let result: { data?: T; error?: unknown; response: Response };
  try {
    result = await requestFn();
  } catch (err) {
    throw normalizeAudioThrownError(err);
  }
  if (!result.response.ok || result.data === undefined) {
    throw mapAudioHttpError(result.response.status, result.error);
  }
  return result.data;
}

/** Opens a new audio play session on the server using an idempotent client requestId. */
export async function openAttemptAudioPlay(
  attemptId: string,
  assetId: string,
  sessionId: string,
  requestId: string,
): Promise<AttemptAudioPlayResponse> {
  const client = createBrowserApiClient();
  return executeAudioRequest(() =>
    client.POST("/api/v1/me/attempts/{attemptId}/audio/{assetId}/plays", {
      params: {
        path: { attemptId, assetId },
        header: { "X-Attempt-Session": sessionId },
      },
      body: { requestId },
    }),
  );
}

/** Resumes an in-progress audio play session and renews its stream lease. */
export async function resumeAttemptAudioPlay(
  attemptId: string,
  assetId: string,
  playId: string,
  sessionId: string,
): Promise<AttemptAudioPlayResponse> {
  const client = createBrowserApiClient();
  return executeAudioRequest(() =>
    client.POST("/api/v1/me/attempts/{attemptId}/audio/{assetId}/plays/{playId}/resume", {
      params: {
        path: { attemptId, assetId, playId },
        header: { "X-Attempt-Session": sessionId },
      },
    }),
  );
}

/** Reports current playback position in milliseconds and validates anti-seek tolerance. */
export async function reportAttemptAudioProgress(
  attemptId: string,
  assetId: string,
  playId: string,
  sessionId: string,
  positionMs: number,
): Promise<AttemptAudioPlayResponse> {
  const client = createBrowserApiClient();
  return executeAudioRequest(() =>
    client.PUT("/api/v1/me/attempts/{attemptId}/audio/{assetId}/plays/{playId}/progress", {
      params: {
        path: { attemptId, assetId, playId },
        header: { "X-Attempt-Session": sessionId },
      },
      body: { positionMs },
    }),
  );
}

/** Marks the active audio play session as finished. */
export async function endAttemptAudioPlay(
  attemptId: string,
  assetId: string,
  playId: string,
  sessionId: string,
): Promise<AttemptAudioPlayResponse> {
  const client = createBrowserApiClient();
  return executeAudioRequest(() =>
    client.POST("/api/v1/me/attempts/{attemptId}/audio/{assetId}/plays/{playId}/end", {
      params: {
        path: { attemptId, assetId, playId },
        header: { "X-Attempt-Session": sessionId },
      },
    }),
  );
}

/** Converts a server {@link AttemptAudioPlayView} (or response) into a UI {@link AudioPlaybackSnapshot}. */
export function toAudioPlaybackSnapshot(
  view: AttemptAudioPlayView | AttemptAudioPlayResponse | undefined,
  maxPlays: number,
): AudioPlaybackSnapshot {
  if (!view) {
    return {
      playId: null,
      playsUsed: 0,
      playsLeft: maxPlays,
      durationMs: 0,
      positionMs: 0,
      state: "IDLE",
      endReason: null,
    };
  }
  const playsUsed = typeof view.playsUsed === "number" ? view.playsUsed : 0;
  const playsLeft =
    typeof view.playsLeft === "number" ? view.playsLeft : Math.max(0, maxPlays - playsUsed);
  return {
    playId: view.playId ?? null,
    playsUsed,
    playsLeft,
    durationMs: typeof view.durationMs === "number" ? view.durationMs : 0,
    positionMs: typeof view.positionMs === "number" ? view.positionMs : 0,
    state: view.state ?? "IDLE",
    endReason: view.endReason ?? null,
  };
}

const DEFAULT_AUDIO_API: AttemptAudioApi = {
  openPlay: openAttemptAudioPlay,
  resumePlay: resumeAttemptAudioPlay,
  reportProgress: reportAttemptAudioProgress,
  endPlay: endAttemptAudioPlay,
};

/** Creates an {@link AudioPlayAdapter} bound to a specific attempt audio asset and edit session. */
export function createAttemptAudioAdapter({
  attemptId,
  assetId,
  maxPlays,
  getSessionId,
  reloadAttemptState,
  audioApi = DEFAULT_AUDIO_API,
}: CreateAttemptAudioAdapterOptions): AudioPlayAdapter {
  function requireSessionId(): string {
    const sessionId = getSessionId();
    if (!sessionId) {
      throw new AudioAdapterError("ATTEMPT_SESSION_REVOKED", {
        message: "Missing active attempt session",
      });
    }
    return sessionId;
  }

  async function runWithSession(
    fn: (sessionId: string) => Promise<AttemptAudioPlayResponse>,
  ): Promise<AudioPlaybackSnapshot> {
    const sessionId = requireSessionId();
    try {
      const response = await fn(sessionId);
      return toAudioPlaybackSnapshot(response, maxPlays);
    } catch (error) {
      throw normalizeAudioThrownError(error);
    }
  }

  return {
    openPlay(requestId) {
      return runWithSession((sessionId) =>
        audioApi.openPlay(attemptId, assetId, sessionId, requestId),
      );
    },
    resumePlay(playId) {
      return runWithSession((sessionId) =>
        audioApi.resumePlay(attemptId, assetId, playId, sessionId),
      );
    },
    reportProgress(playId, positionMs) {
      return runWithSession((sessionId) =>
        audioApi.reportProgress(attemptId, assetId, playId, sessionId, positionMs),
      );
    },
    endPlay(playId) {
      return runWithSession((sessionId) =>
        audioApi.endPlay(attemptId, assetId, playId, sessionId),
      );
    },
    resolveStreamUrl(playId) {
      return `${toAttemptAssetUrl(attemptId, assetId)}?playId=${encodeURIComponent(playId)}`;
    },
    async reloadState() {
      const fresh = await reloadAttemptState();
      const entry = fresh.audioPlays?.find((p) => p.assetId === assetId);
      const effectiveMax = fresh.audioPolicy?.maxPlays ?? maxPlays;
      return toAudioPlaybackSnapshot(entry, effectiveMax);
    },
  };
}
