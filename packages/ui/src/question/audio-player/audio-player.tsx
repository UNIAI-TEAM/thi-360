"use client";

import { Play, RotateCcw, Volume2 } from "lucide-react";
import { useCallback, useEffect, useId, useRef, useState } from "react";
import { Button } from "../../components/button";
import { useUiStrings } from "../../components/ui-strings";
import { cn } from "../../lib/cn";
import {
  AudioAdapterError,
  type AudioClosedReason,
  type AudioPlayAdapter,
  type AudioPlaybackSnapshot,
} from "./types";

export interface AttemptAudioPlayerProps {
  assetId: string;
  fileName?: string | null;
  initialState: AudioPlaybackSnapshot;
  maxPlays?: number;
  adapter: AudioPlayAdapter;
  disabled?: boolean;
  closedReason?: AudioClosedReason | null;
  stopSignal?: number;
  isOnline?: boolean;
  progressIntervalMs?: number;
  onStateChange?: (snapshot: AudioPlaybackSnapshot) => void;
  onClosed?: (reason: "PART_CLOSED" | "TIME_UP") => void;
  onSessionRevoked?: () => void;
  onUnauthorized?: () => void;
  className?: string;
}

type NoticeTone = "info" | "warning" | "danger" | "neutral";

interface StatusNotice {
  tone: NoticeTone;
  message: string;
}

const DEFAULT_PROGRESS_INTERVAL_MS = 10_000;

function createRequestId(): string {
  if (typeof globalThis.crypto?.randomUUID === "function") {
    return globalThis.crypto.randomUUID();
  }
  return `audio-req-${Date.now().toString(36)}-${Math.random().toString(36).slice(2, 10)}`;
}

function formatAudioTime(ms: number): string {
  const safeSeconds = Math.max(0, Math.floor(ms / 1000));
  const minutes = Math.floor(safeSeconds / 60);
  const seconds = safeSeconds % 60;
  return `${String(minutes).padStart(2, "0")}:${String(seconds).padStart(2, "0")}`;
}

function asAudioAdapterError(err: unknown): AudioAdapterError | null {
  if (err instanceof AudioAdapterError) return err;
  if (err && typeof err === "object" && "code" in err && typeof (err as { code: unknown }).code === "string") {
    return err as AudioAdapterError;
  }
  return null;
}

function snapshotsMatch(a: AudioPlaybackSnapshot, b: AudioPlaybackSnapshot): boolean {
  return (
    a.playId === b.playId &&
    a.playsUsed === b.playsUsed &&
    a.playsLeft === b.playsLeft &&
    a.durationMs === b.durationMs &&
    a.positionMs === b.positionMs &&
    a.state === b.state &&
    (a.endReason ?? null) === (b.endReason ?? null)
  );
}

const NOTICE_STYLES: Record<NoticeTone, string> = {
  info: "border-info bg-info-bg text-info",
  warning: "border-warning bg-warning-bg text-warning",
  danger: "border-danger bg-danger-bg text-danger",
  neutral: "border-border bg-subtle text-text-secondary",
};

/**
 * Shared limited-play audio player (`@thi360/ui/question`, BS 17.2, FS-7 AC-1..4, FA-5 AC-4).
 * Enforces no seek bar, no playback-rate changes, no pause control, idempotent request retries,
 * resume without consuming extra plays, and immediate teardown on close/submit/time-up/takeover.
 */
export function AttemptAudioPlayer({
  assetId,
  fileName,
  initialState,
  maxPlays = 2,
  adapter,
  disabled = false,
  closedReason = null,
  stopSignal = 0,
  isOnline,
  progressIntervalMs = DEFAULT_PROGRESS_INTERVAL_MS,
  onStateChange,
  onClosed,
  onSessionRevoked,
  onUnauthorized,
  className,
}: AttemptAudioPlayerProps) {
  const strings = useUiStrings().question.audioPlayer;
  const reasonId = useId();

  const [snapshot, setSnapshot] = useState<AudioPlaybackSnapshot>(initialState);
  const [elapsedMs, setElapsedMs] = useState<number>(initialState.positionMs);
  const [mediaDurationMs, setMediaDurationMs] = useState<number>(0);
  const [isPlayingMedia, setIsPlayingMedia] = useState(false);
  const [isBusy, setIsBusy] = useState(false);
  const [needsUserResume, setNeedsUserResume] = useState(false);
  const [apiClosedReason, setApiClosedReason] = useState<"PART_CLOSED" | "TIME_UP" | null>(null);
  const [statusNotice, setStatusNotice] = useState<StatusNotice | null>(null);
  const [browserOnline, setBrowserOnline] = useState<boolean>(() =>
    typeof navigator === "undefined" ? true : navigator.onLine !== false,
  );

  const effectiveOnline = (isOnline ?? true) && browserOnline;
  const effectiveClosedReason: AudioClosedReason | null = closedReason ?? apiClosedReason;
  const isClosed = effectiveClosedReason !== null;
  const isLockedOut = isClosed || disabled;

  const audioRef = useRef<HTMLAudioElement | null>(null);
  const mountedRef = useRef(true);
  const epochRef = useRef(0);
  const snapshotRef = useRef(snapshot);
  snapshotRef.current = snapshot;
  const activePlayIdRef = useRef<string | null>(initialState.playId);
  const pendingRequestIdRef = useRef<string | null>(null);
  const openInFlightRef = useRef(false);
  const resumeInFlightRef = useRef(false);
  const endSentForPlayIdRef = useRef<string | null>(null);
  const progressTimerRef = useRef<ReturnType<typeof setInterval> | null>(null);
  const lastConfirmedMsRef = useRef<number>(initialState.positionMs);
  const expectedTimeSecRef = useRef<number>(initialState.positionMs / 1000);
  const pendingSeekSecRef = useRef<number | null>(null);
  const internalSeekRef = useRef(false);
  const wasPlayingBeforeOfflineRef = useRef(false);
  const effectiveOnlineRef = useRef(effectiveOnline);
  effectiveOnlineRef.current = effectiveOnline;
  const isLockedOutRef = useRef(isLockedOut);
  isLockedOutRef.current = isLockedOut;
  const prevOnlineRef = useRef(effectiveOnline);
  const prevStopSignalRef = useRef(stopSignal);
  const prevLockedOutRef = useRef(isLockedOut);
  const prevAssetIdRef = useRef(assetId);
  const prevInitialStateRef = useRef(initialState);
  const initialStateRef = useRef(initialState);
  initialStateRef.current = initialState;

  const adapterRef = useRef(adapter);
  adapterRef.current = adapter;
  const stringsRef = useRef(strings);
  stringsRef.current = strings;
  const onStateChangeRef = useRef(onStateChange);
  onStateChangeRef.current = onStateChange;
  const onClosedRef = useRef(onClosed);
  onClosedRef.current = onClosed;
  const onSessionRevokedRef = useRef(onSessionRevoked);
  onSessionRevokedRef.current = onSessionRevoked;
  const onUnauthorizedRef = useRef(onUnauthorized);
  onUnauthorizedRef.current = onUnauthorized;
  const progressIntervalMsRef = useRef(progressIntervalMs);
  progressIntervalMsRef.current = progressIntervalMs;

  const clearProgressTimer = useCallback(() => {
    if (progressTimerRef.current !== null) {
      clearInterval(progressTimerRef.current);
      progressTimerRef.current = null;
    }
  }, []);

  const stopMediaAndTimers = useCallback(() => {
    epochRef.current += 1;
    clearProgressTimer();
    pendingSeekSecRef.current = null;
    const audio = audioRef.current;
    if (audio) {
      try {
        audio.pause();
        audio.removeAttribute("src");
        audio.load();
      } catch {
        // Ignore jsdom or detached media teardown edge errors.
      }
    }
  }, [clearProgressTimer]);

  const commitSnapshot = useCallback((next: AudioPlaybackSnapshot) => {
    snapshotRef.current = next;
    setSnapshot(next);
    onStateChangeRef.current?.(next);
  }, []);

  const seekAudioInternal = useCallback((audio: HTMLAudioElement, targetSec: number) => {
    expectedTimeSecRef.current = targetSec;
    if (Math.abs(audio.currentTime - targetSec) > 0.05) {
      internalSeekRef.current = true;
      try {
        audio.currentTime = targetSec;
      } catch {
        // Browsers before metadata load may defer seek until loadedmetadata.
      } finally {
        void Promise.resolve().then(() => {
          internalSeekRef.current = false;
        });
      }
    }
  }, []);

  const handleAdapterError = useCallback(
    (err: unknown, contextEpoch: number, context?: { playId?: string; fromOpenPlay?: boolean }) => {
      if (!mountedRef.current) return;
      if (epochRef.current !== contextEpoch) return;
      if (context?.playId && activePlayIdRef.current !== context.playId) return;

      const adapterErr = asAudioAdapterError(err);
      const code = adapterErr?.code ?? "UNKNOWN";

      switch (code) {
        case "AUDIO_PLAYS_EXHAUSTED": {
          pendingRequestIdRef.current = null;
          clearProgressTimer();
          setIsPlayingMedia(false);
          const current = snapshotRef.current;
          commitSnapshot({
            ...current,
            playsLeft: 0,
            state: current.state === "PLAYING" ? current.state : "ENDED",
          });
          setStatusNotice(null);
          return;
        }
        case "AUDIO_SEEK_REJECTED": {
          const serverMs = Math.max(0, adapterErr?.positionMs ?? lastConfirmedMsRef.current);
          lastConfirmedMsRef.current = serverMs;
          const audio = audioRef.current;
          if (audio) {
            seekAudioInternal(audio, serverMs / 1000);
          } else {
            expectedTimeSecRef.current = serverMs / 1000;
          }
          setElapsedMs(serverMs);
          commitSnapshot({ ...snapshotRef.current, positionMs: serverMs });
          return;
        }
        case "AUDIO_PLAY_STALE": {
          stopMediaAndTimers();
          setIsPlayingMedia(false);
          setNeedsUserResume(false);
          activePlayIdRef.current = null;
          const reload = adapterRef.current.reloadState;
          if (reload) {
            const reloadEpoch = epochRef.current;
            void reload()
              .then((fresh) => {
                if (!mountedRef.current || epochRef.current !== reloadEpoch || !fresh) return;
                activePlayIdRef.current = fresh.playId;
                lastConfirmedMsRef.current = fresh.positionMs;
                expectedTimeSecRef.current = fresh.positionMs / 1000;
                setElapsedMs(fresh.positionMs);
                commitSnapshot(fresh);
              })
              .catch(() => {
                // Reload failure leaves stale playback stopped without unhandled rejections.
              });
          }
          return;
        }
        case "PART_CLOSED":
        case "TIME_UP": {
          pendingRequestIdRef.current = null;
          stopMediaAndTimers();
          setIsPlayingMedia(false);
          setNeedsUserResume(false);
          setApiClosedReason(code);
          onClosedRef.current?.(code);
          return;
        }
        case "ATTEMPT_SESSION_REVOKED": {
          pendingRequestIdRef.current = null;
          stopMediaAndTimers();
          setIsPlayingMedia(false);
          setNeedsUserResume(false);
          onSessionRevokedRef.current?.();
          return;
        }
        case "UNAUTHORIZED": {
          pendingRequestIdRef.current = null;
          stopMediaAndTimers();
          setIsPlayingMedia(false);
          setNeedsUserResume(false);
          onUnauthorizedRef.current?.();
          return;
        }
        default: {
          // Keep pendingRequestIdRef on transient/network errors so retrying openPlay reuses it.
          clearProgressTimer();
          setIsPlayingMedia(false);
          if (!context?.fromOpenPlay && snapshotRef.current.state === "PLAYING") {
            setNeedsUserResume(true);
          }
          setStatusNotice({ tone: "danger", message: stringsRef.current.playErrorRetry });
          return;
        }
      }
    },
    [clearProgressTimer, commitSnapshot, seekAudioInternal, stopMediaAndTimers],
  );

  const startProgressTimer = useCallback(
    (playId: string, myEpoch: number) => {
      clearProgressTimer();
      const interval = Math.max(100, progressIntervalMsRef.current);
      progressTimerRef.current = setInterval(() => {
        if (
          !mountedRef.current ||
          epochRef.current !== myEpoch ||
          activePlayIdRef.current !== playId ||
          isLockedOutRef.current ||
          !effectiveOnlineRef.current ||
          snapshotRef.current.state !== "PLAYING"
        ) {
          clearProgressTimer();
          return;
        }
        const audio = audioRef.current;
        const currentMs = audio ? Math.round(audio.currentTime * 1000) : lastConfirmedMsRef.current;
        let boundedMs = Math.max(lastConfirmedMsRef.current, currentMs);
        if (snapshotRef.current.durationMs > 0) {
          boundedMs = Math.min(boundedMs, snapshotRef.current.durationMs);
        }
        void adapterRef.current
          .reportProgress(playId, boundedMs)
          .then((nextSnap) => {
            if (
              !mountedRef.current ||
              epochRef.current !== myEpoch ||
              activePlayIdRef.current !== playId ||
              isLockedOutRef.current
            ) {
              return;
            }
            lastConfirmedMsRef.current = Math.max(lastConfirmedMsRef.current, nextSnap.positionMs);
            commitSnapshot(nextSnap);
            if (nextSnap.state === "ENDED") {
              clearProgressTimer();
              setIsPlayingMedia(false);
              setElapsedMs(nextSnap.positionMs);
            }
          })
          .catch((err) => {
            handleAdapterError(err, myEpoch, { playId });
          });
      }, interval);
    },
    [clearProgressTimer, commitSnapshot, handleAdapterError],
  );

  const startPlaybackFromSnapshot = useCallback(
    async (snap: AudioPlaybackSnapshot, myEpoch: number) => {
      if (!mountedRef.current || epochRef.current !== myEpoch || isLockedOutRef.current) return;

      activePlayIdRef.current = snap.playId;
      lastConfirmedMsRef.current = snap.positionMs;
      setElapsedMs(snap.positionMs);
      commitSnapshot(snap);

      if (snap.state !== "PLAYING" || !snap.playId) {
        clearProgressTimer();
        setIsPlayingMedia(false);
        setNeedsUserResume(false);
        return;
      }

      const audio = audioRef.current;
      if (!audio) return;

      const streamUrl = adapterRef.current.resolveStreamUrl(snap.playId);
      audio.defaultPlaybackRate = 1;
      audio.playbackRate = 1;
      if (audio.getAttribute("src") !== streamUrl) {
        audio.setAttribute("src", streamUrl);
      }

      const targetSec = Math.max(0, snap.positionMs / 1000);
      pendingSeekSecRef.current = targetSec;
      seekAudioInternal(audio, targetSec);

      try {
        const playResult = audio.play();
        if (playResult !== undefined) {
          await playResult;
        }
        if (!mountedRef.current || epochRef.current !== myEpoch || isLockedOutRef.current) {
          try {
            audio.pause();
          } catch {
            // Ignore pause error during teardown.
          }
          return;
        }
        setNeedsUserResume(false);
        setIsPlayingMedia(true);
        setStatusNotice(null);
        startProgressTimer(snap.playId, myEpoch);
      } catch {
        if (!mountedRef.current || epochRef.current !== myEpoch || isLockedOutRef.current) return;
        clearProgressTimer();
        setIsPlayingMedia(false);
        setNeedsUserResume(true);
        setStatusNotice({ tone: "info", message: stringsRef.current.autoplayBlocked });
      }
    },
    [clearProgressTimer, commitSnapshot, seekAudioInternal, startProgressTimer],
  );

  const resumeActivePlay = useCallback(
    async (playId: string, myEpoch: number) => {
      if (resumeInFlightRef.current || isLockedOutRef.current || !effectiveOnlineRef.current) return;
      resumeInFlightRef.current = true;
      setIsBusy(true);
      try {
        const resumed = await adapterRef.current.resumePlay(playId);
        if (!mountedRef.current || epochRef.current !== myEpoch || isLockedOutRef.current) return;
        await startPlaybackFromSnapshot(resumed, myEpoch);
      } catch (err) {
        handleAdapterError(err, myEpoch, { playId });
      } finally {
        resumeInFlightRef.current = false;
        if (mountedRef.current) setIsBusy(false);
      }
    },
    [handleAdapterError, startPlaybackFromSnapshot],
  );

  // Listen to browser online/offline events.
  useEffect(() => {
    if (typeof window === "undefined") return;
    const onOffline = () => setBrowserOnline(false);
    const onOnline = () => setBrowserOnline(true);
    window.addEventListener("offline", onOffline);
    window.addEventListener("online", onOnline);
    return () => {
      window.removeEventListener("offline", onOffline);
      window.removeEventListener("online", onOnline);
    };
  }, []);

  // Unmount teardown safety.
  useEffect(() => {
    mountedRef.current = true;
    return () => {
      mountedRef.current = false;
      stopMediaAndTimers();
    };
  }, [stopMediaAndTimers]);

  // Mount / asset change: sync state and auto-resume any active PLAYING snapshot without calling openPlay.
  useEffect(() => {
    const currentInitial = initialStateRef.current;
    const assetChanged = prevAssetIdRef.current !== assetId;
    if (assetChanged) {
      prevAssetIdRef.current = assetId;
      prevInitialStateRef.current = currentInitial;
      stopMediaAndTimers();
      pendingRequestIdRef.current = null;
      endSentForPlayIdRef.current = null;
      wasPlayingBeforeOfflineRef.current = false;
      activePlayIdRef.current = currentInitial.playId;
      lastConfirmedMsRef.current = currentInitial.positionMs;
      expectedTimeSecRef.current = currentInitial.positionMs / 1000;
      snapshotRef.current = currentInitial;
      setSnapshot(currentInitial);
      setElapsedMs(currentInitial.positionMs);
      setMediaDurationMs(0);
      setIsPlayingMedia(false);
      setNeedsUserResume(false);
      setApiClosedReason(null);
      setStatusNotice(null);
    }

    if (
      currentInitial.state === "PLAYING" &&
      currentInitial.playId &&
      !isLockedOutRef.current &&
      effectiveOnlineRef.current
    ) {
      const myEpoch = (epochRef.current += 1);
      void resumeActivePlay(currentInitial.playId, myEpoch);
    }

    return () => {
      stopMediaAndTimers();
    };
  }, [assetId, resumeActivePlay, stopMediaAndTimers]);

  // Sync external initialState updates (e.g. parent re-fetch) for the same assetId.
  useEffect(() => {
    if (prevAssetIdRef.current !== assetId) return;
    if (snapshotsMatch(initialState, prevInitialStateRef.current)) return;
    prevInitialStateRef.current = initialState;
    if (snapshotsMatch(initialState, snapshotRef.current)) return;

    activePlayIdRef.current = initialState.playId;
    lastConfirmedMsRef.current = initialState.positionMs;
    expectedTimeSecRef.current = initialState.positionMs / 1000;
    snapshotRef.current = initialState;
    setSnapshot(initialState);
    setElapsedMs(initialState.positionMs);
    if (initialState.state !== "PLAYING") {
      stopMediaAndTimers();
      setIsPlayingMedia(false);
      setNeedsUserResume(false);
    }
  }, [assetId, initialState, stopMediaAndTimers]);

  // Immediate teardown on closedReason, stopSignal increment, or disabled=true.
  useEffect(() => {
    const stopSignalChanged = stopSignal !== prevStopSignalRef.current;
    const lockedOutChanged = isLockedOut !== prevLockedOutRef.current;
    prevStopSignalRef.current = stopSignal;
    prevLockedOutRef.current = isLockedOut;

    if (isLockedOut && lockedOutChanged) {
      stopMediaAndTimers();
      setIsPlayingMedia(false);
      setNeedsUserResume(false);
      return;
    }

    if (stopSignalChanged) {
      stopMediaAndTimers();
      setIsPlayingMedia(false);
      if (!isLockedOut && snapshotRef.current.state === "PLAYING") {
        setNeedsUserResume(true);
      }
    }
  }, [isLockedOut, stopMediaAndTimers, stopSignal]);

  // Offline -> Online pause & resume without consuming a play.
  useEffect(() => {
    const wasOnline = prevOnlineRef.current;
    prevOnlineRef.current = effectiveOnline;
    if (wasOnline === effectiveOnline) return;

    if (!effectiveOnline) {
      epochRef.current += 1;
      clearProgressTimer();
      if (isPlayingMedia || snapshotRef.current.state === "PLAYING") {
        wasPlayingBeforeOfflineRef.current = true;
      }
      try {
        audioRef.current?.pause();
      } catch {
        // Ignore pause error in jsdom.
      }
      setIsPlayingMedia(false);
      return;
    }

    const activePlayId = snapshotRef.current.playId;
    if (
      snapshotRef.current.state === "PLAYING" &&
      activePlayId &&
      !isLockedOutRef.current &&
      (wasPlayingBeforeOfflineRef.current || activePlayIdRef.current === activePlayId)
    ) {
      wasPlayingBeforeOfflineRef.current = false;
      const myEpoch = (epochRef.current += 1);
      void resumeActivePlay(activePlayId, myEpoch);
    }
  }, [clearProgressTimer, effectiveOnline, isPlayingMedia, resumeActivePlay]);

  async function handleActionClick() {
    if (isLockedOutRef.current || !effectiveOnlineRef.current) return;

    const current = snapshotRef.current;
    if (current.state === "PLAYING" && current.playId) {
      if (openInFlightRef.current || resumeInFlightRef.current) return;
      const myEpoch = (epochRef.current += 1);
      await resumeActivePlay(current.playId, myEpoch);
      return;
    }

    if (current.playsLeft <= 0) return;
    if (openInFlightRef.current || resumeInFlightRef.current) return;

    openInFlightRef.current = true;
    setIsBusy(true);
    const requestId = pendingRequestIdRef.current ?? createRequestId();
    pendingRequestIdRef.current = requestId;
    const myEpoch = (epochRef.current += 1);

    try {
      const opened = await adapterRef.current.openPlay(requestId);
      pendingRequestIdRef.current = null;
      if (!mountedRef.current || epochRef.current !== myEpoch || isLockedOutRef.current) return;
      await startPlaybackFromSnapshot(opened, myEpoch);
    } catch (err) {
      const adapterErr = asAudioAdapterError(err);
      if (adapterErr?.code === "AUDIO_PLAY_IN_PROGRESS" && adapterErr.playId) {
        pendingRequestIdRef.current = null;
        openInFlightRef.current = false;
        await resumeActivePlay(adapterErr.playId, myEpoch);
        return;
      }
      handleAdapterError(err, myEpoch, { fromOpenPlay: true });
    } finally {
      openInFlightRef.current = false;
      if (mountedRef.current) setIsBusy(false);
    }
  }

  function handleAudioEnded() {
    const playId = activePlayIdRef.current;
    if (!playId || isLockedOutRef.current || snapshotRef.current.state !== "PLAYING") return;
    if (endSentForPlayIdRef.current === playId) return;

    endSentForPlayIdRef.current = playId;
    const myEpoch = epochRef.current;
    clearProgressTimer();
    setIsPlayingMedia(false);
    setNeedsUserResume(false);

    void adapterRef.current
      .endPlay(playId)
      .then((ended) => {
        if (!mountedRef.current || epochRef.current !== myEpoch || activePlayIdRef.current !== playId) {
          return;
        }
        activePlayIdRef.current = ended.playId;
        lastConfirmedMsRef.current = ended.positionMs;
        expectedTimeSecRef.current = ended.positionMs / 1000;
        setElapsedMs(ended.positionMs);
        commitSnapshot(ended);
      })
      .catch((err) => {
        const adapterErr = asAudioAdapterError(err);
        const code = adapterErr?.code ?? "UNKNOWN";
        if (code === "NETWORK_ERROR" || code === "UNKNOWN") {
          endSentForPlayIdRef.current = null;
        }
        handleAdapterError(err, myEpoch, { playId });
      });
  }

  function handleLoadedMetadata() {
    const audio = audioRef.current;
    if (!audio) return;
    if (Number.isFinite(audio.duration) && audio.duration > 0) {
      setMediaDurationMs(Math.round(audio.duration * 1000));
    }
    if (pendingSeekSecRef.current != null) {
      const targetSec = pendingSeekSecRef.current;
      pendingSeekSecRef.current = null;
      seekAudioInternal(audio, targetSec);
    }
  }

  function handleTimeUpdate() {
    const audio = audioRef.current;
    if (!audio || audio.seeking) return;
    expectedTimeSecRef.current = audio.currentTime;
    const rawMs = Math.max(0, Math.round(audio.currentTime * 1000));
    const totalMs = snapshotRef.current.durationMs;
    setElapsedMs(totalMs > 0 ? Math.min(rawMs, totalMs) : rawMs);
  }

  function handleSeeking() {
    const audio = audioRef.current;
    if (!audio || internalSeekRef.current) return;
    if (Math.abs(audio.currentTime - expectedTimeSecRef.current) > 0.05) {
      internalSeekRef.current = true;
      try {
        audio.currentTime = expectedTimeSecRef.current;
      } catch {
        // Ignore seek lock failures on unready media.
      } finally {
        void Promise.resolve().then(() => {
          internalSeekRef.current = false;
        });
      }
    }
  }

  function handleRateChange() {
    const audio = audioRef.current;
    if (!audio) return;
    if (audio.playbackRate !== 1) {
      audio.playbackRate = 1;
    }
  }

  const effectiveMaxPlays = Math.max(maxPlays, snapshot.playsUsed + snapshot.playsLeft);
  const displayDurationMs = snapshot.durationMs > 0 ? snapshot.durationMs : mediaDurationMs;
  const clampedElapsedMs =
    displayDurationMs > 0 ? Math.min(Math.max(0, elapsedMs), displayDurationMs) : Math.max(0, elapsedMs);
  const progressPercent =
    displayDurationMs > 0 ? Math.min(100, Math.round((clampedElapsedMs / displayDurationMs) * 100)) : 0;

  const isExhausted = snapshot.playsLeft <= 0 && snapshot.state !== "PLAYING";
  const isWaitingResume =
    !isClosed &&
    !disabled &&
    snapshot.state === "PLAYING" &&
    (needsUserResume || (!isPlayingMedia && !isBusy));
  const isActivelyPlaying =
    !isClosed && !disabled && effectiveOnline && snapshot.state === "PLAYING" && !isWaitingResume;

  let closedReasonText: string | null = null;
  if (effectiveClosedReason === "PART_CLOSED") closedReasonText = strings.partClosedReason;
  else if (effectiveClosedReason === "TIME_UP") closedReasonText = strings.timeUpReason;
  else if (effectiveClosedReason === "SUBMITTED") closedReasonText = strings.submittedReason;
  else if (effectiveClosedReason === "EVICTED") closedReasonText = strings.evictedReason;

  let disabledReasonText: string | null = null;
  if (closedReasonText) disabledReasonText = closedReasonText;
  else if (disabled) disabledReasonText = strings.disabledReason;
  else if (isExhausted) disabledReasonText = strings.exhaustedReason;

  let activeBanner: StatusNotice | null = null;
  if (disabledReasonText) {
    activeBanner = {
      tone:
        effectiveClosedReason === "TIME_UP" || effectiveClosedReason === "EVICTED"
          ? "danger"
          : effectiveClosedReason != null
            ? "warning"
            : "neutral",
      message: disabledReasonText,
    };
  } else if (!effectiveOnline) {
    activeBanner = { tone: "warning", message: strings.offlineWaiting };
  } else if (statusNotice) {
    activeBanner = statusNotice;
  } else if (needsUserResume) {
    activeBanner = { tone: "info", message: strings.autoplayBlocked };
  }

  const buttonDisabled =
    isLockedOut || !effectiveOnline || isExhausted || isActivelyPlaying;
  const buttonLabel = isActivelyPlaying
    ? strings.playing
    : isWaitingResume
      ? strings.resume
      : strings.play;

  const badgeText =
    snapshot.playsLeft <= 0 && snapshot.state !== "PLAYING"
      ? strings.playsExhaustedBadge(effectiveMaxPlays)
      : strings.playsRemaining(snapshot.playsLeft, effectiveMaxPlays);

  const srOnlyStatus = activeBanner
    ? ""
    : isActivelyPlaying
      ? `${strings.regionLabel}: ${strings.playing}`
      : snapshot.state === "ENDED"
        ? `${strings.regionLabel}: ${badgeText}`
        : "";

  return (
    <div
      role="region"
      aria-label={strings.regionLabel}
      className={cn(
        "flex flex-col gap-3 rounded-xl border border-border bg-surface p-3.5 shadow-xs",
        className,
      )}
    >
      <audio
        ref={audioRef}
        preload="none"
        onEnded={handleAudioEnded}
        onLoadedMetadata={handleLoadedMetadata}
        onTimeUpdate={handleTimeUpdate}
        onSeeking={handleSeeking}
        onRateChange={handleRateChange}
      />

      {fileName != null && fileName !== "" && (
        <div className="flex items-center gap-2 text-caption font-medium text-text-secondary">
          <Volume2 aria-hidden className="size-4 shrink-0 text-primary-text" />
          <p data-exam-content className="min-w-0 truncate">
            {fileName}
          </p>
        </div>
      )}

      <div className="flex flex-wrap items-center gap-3">
        <Button
          type="button"
          variant={isWaitingResume ? "outline" : "primary"}
          size="md"
          loading={isBusy}
          disabled={buttonDisabled}
          aria-describedby={activeBanner ? reasonId : undefined}
          onClick={() => void handleActionClick()}
          className="pointer-coarse:min-h-11 pointer-coarse:min-w-11 shrink-0"
        >
          {isWaitingResume ? <RotateCcw aria-hidden /> : <Play aria-hidden />}
          {buttonLabel}
        </Button>

        <div className="flex min-w-44 flex-1 flex-col gap-1.5">
          <div className="flex items-center justify-between gap-2">
            <span
              className={cn(
                "inline-flex items-center rounded-full border px-2.5 py-0.5 text-caption font-semibold whitespace-nowrap",
                snapshot.playsLeft > 0
                  ? "border-border-primary bg-primary-50 text-primary-text"
                  : "border-border bg-neutral-bg text-neutral",
              )}
            >
              {badgeText}
            </span>
            <span className="text-caption font-medium text-text-secondary tabular-nums">
              {formatAudioTime(clampedElapsedMs)} / {formatAudioTime(displayDurationMs)}
            </span>
          </div>
          <div
            role="progressbar"
            aria-label={strings.progressLabel}
            aria-valuemin={0}
            aria-valuemax={displayDurationMs}
            aria-valuenow={clampedElapsedMs}
            className="h-2 w-full overflow-hidden rounded-full bg-subtle"
          >
            <div
              className="h-full rounded-full bg-primary-fill transition-[width] duration-150 motion-reduce:transition-none"
              style={{ width: `${progressPercent}%` }}
            />
          </div>
        </div>
      </div>

      <div
        role="status"
        aria-live="polite"
        aria-atomic="true"
        className={activeBanner ? undefined : "sr-only"}
      >
        {activeBanner ? (
          <p
            id={reasonId}
            className={cn(
              "rounded-lg border px-3 py-2 text-caption font-medium",
              NOTICE_STYLES[activeBanner.tone],
            )}
          >
            {activeBanner.message}
          </p>
        ) : (
          srOnlyStatus
        )}
      </div>
    </div>
  );
}

export const AudioPlayer = AttemptAudioPlayer;
