import {
  AudioAdapterError,
  type AudioEndReason,
  type AudioPlayAdapter,
  type AudioPlaybackSnapshot,
  type AudioPlayState,
} from "./types";

export interface CreateTrialAudioAdapterOptions {
  assetUrl: string;
  maxPlays?: number;
  durationMs?: number;
}

/**
 * Browser-local audio playback adapter for admin trial mode (FA-5 AC-4, BS 17.2).
 * Maintains all play counts and position entirely in memory with zero API or fetch calls.
 */
export function createTrialAudioAdapter({
  assetUrl,
  maxPlays = 2,
  durationMs = 0,
}: CreateTrialAudioAdapterOptions): AudioPlayAdapter {
  let playsUsed = 0;
  let playsLeft = Math.max(0, maxPlays);
  let positionMs = 0;
  let state: AudioPlayState = "IDLE";
  let endReason: AudioEndReason | null = null;
  let playId: string | null = null;
  let lastRequestId: string | null = null;
  let seq = 0;

  function snapshot(): AudioPlaybackSnapshot {
    return {
      playId,
      playsUsed,
      playsLeft,
      durationMs,
      positionMs,
      state,
      endReason,
    };
  }

  return {
    async openPlay(requestId: string): Promise<AudioPlaybackSnapshot> {
      if (lastRequestId !== null && lastRequestId === requestId) {
        return snapshot();
      }
      if (state === "PLAYING") {
        throw new AudioAdapterError("AUDIO_PLAY_IN_PROGRESS", "Audio play is already in progress", {
          playId,
        });
      }
      if (playsUsed >= maxPlays) {
        throw new AudioAdapterError("AUDIO_PLAYS_EXHAUSTED", "No audio plays remaining");
      }
      seq += 1;
      playId = `trial-play-${seq}`;
      lastRequestId = requestId;
      playsUsed += 1;
      playsLeft = Math.max(0, maxPlays - playsUsed);
      positionMs = 0;
      state = "PLAYING";
      endReason = null;
      return snapshot();
    },

    async resumePlay(targetPlayId: string): Promise<AudioPlaybackSnapshot> {
      if (state !== "PLAYING" || playId !== targetPlayId) {
        throw new AudioAdapterError("AUDIO_PLAY_STALE", "Audio play is no longer active");
      }
      return snapshot();
    },

    async reportProgress(targetPlayId: string, nextPositionMs: number): Promise<AudioPlaybackSnapshot> {
      if (state !== "PLAYING" || playId !== targetPlayId) {
        throw new AudioAdapterError("AUDIO_PLAY_STALE", "Audio play is no longer active");
      }
      const clamped = Math.max(
        positionMs,
        durationMs > 0 ? Math.min(nextPositionMs, durationMs) : nextPositionMs,
      );
      positionMs = clamped;
      if (durationMs > 0 && positionMs >= durationMs) {
        state = "ENDED";
        endReason = "FINISHED";
      }
      return snapshot();
    },

    async endPlay(targetPlayId: string): Promise<AudioPlaybackSnapshot> {
      if (state === "ENDED" && playId === targetPlayId) {
        return snapshot();
      }
      if (state !== "PLAYING" || playId !== targetPlayId) {
        throw new AudioAdapterError("AUDIO_PLAY_STALE", "Audio play is no longer active");
      }
      if (durationMs > 0) {
        positionMs = durationMs;
      }
      state = "ENDED";
      endReason = "FINISHED";
      return snapshot();
    },

    resolveStreamUrl(): string {
      return assetUrl;
    },

    async reloadState(): Promise<AudioPlaybackSnapshot | null> {
      return snapshot();
    },
  };
}
