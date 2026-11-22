export type AudioPlayState = "IDLE" | "PLAYING" | "ENDED";

export type AudioEndReason = "FINISHED" | "CLOSED";

export type AudioClosedReason = "PART_CLOSED" | "TIME_UP" | "SUBMITTED" | "EVICTED";

export interface AudioPlaybackSnapshot {
  playId: string | null;
  playsUsed: number;
  playsLeft: number;
  durationMs: number;
  positionMs: number;
  state: AudioPlayState;
  endReason?: AudioEndReason | null;
}

export type AudioAdapterErrorCode =
  | "AUDIO_PLAY_IN_PROGRESS"
  | "AUDIO_PLAYS_EXHAUSTED"
  | "AUDIO_SEEK_REJECTED"
  | "AUDIO_PLAY_STALE"
  | "PART_CLOSED"
  | "TIME_UP"
  | "ATTEMPT_SESSION_REVOKED"
  | "UNAUTHORIZED"
  | "NETWORK_ERROR"
  | "UNKNOWN";

export interface AudioAdapterErrorOptions {
  playId?: string | null;
  positionMs?: number | null;
  status?: number;
  message?: string;
}

export class AudioAdapterError extends Error {
  readonly code: AudioAdapterErrorCode;
  readonly playId?: string | null;
  readonly positionMs?: number | null;
  readonly status?: number;

  constructor(
    code: AudioAdapterErrorCode,
    messageOrOptions?: string | AudioAdapterErrorOptions,
    options?: AudioAdapterErrorOptions,
  ) {
    const resolvedOptions =
      typeof messageOrOptions === "object" && messageOrOptions !== null
        ? messageOrOptions
        : options;
    const message =
      typeof messageOrOptions === "string"
        ? messageOrOptions
        : resolvedOptions?.message ?? code;
    super(message);
    this.name = "AudioAdapterError";
    this.code = code;
    this.playId = resolvedOptions?.playId;
    this.positionMs = resolvedOptions?.positionMs;
    this.status = resolvedOptions?.status;
  }
}

export interface AudioPlayAdapter {
  openPlay(requestId: string): Promise<AudioPlaybackSnapshot>;
  resumePlay(playId: string): Promise<AudioPlaybackSnapshot>;
  reportProgress(playId: string, positionMs: number): Promise<AudioPlaybackSnapshot>;
  endPlay(playId: string): Promise<AudioPlaybackSnapshot>;
  resolveStreamUrl(playId: string): string;
  reloadState?: () => Promise<AudioPlaybackSnapshot | null>;
}
