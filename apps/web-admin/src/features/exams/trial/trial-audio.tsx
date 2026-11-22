"use client";

import {
  AttemptAudioPlayer,
  createTrialAudioAdapter,
  type AudioClosedReason,
  type AudioPlayAdapter,
  type AudioPlaybackSnapshot,
} from "@thi360/ui/question";
import { useCallback, useMemo, useRef, useState } from "react";
import { toTrialAttachments } from "./mappers";
import type { PreviewTrialAttachment, PreviewTrialPaperResponse } from "./types";

export const MAX_TRIAL_AUDIO_PLAYS = 2;

export const INITIAL_TRIAL_AUDIO_SNAPSHOT: AudioPlaybackSnapshot = {
  playId: null,
  playsUsed: 0,
  playsLeft: MAX_TRIAL_AUDIO_PLAYS,
  durationMs: 0,
  positionMs: 0,
  state: "IDLE",
  endReason: null,
};

export interface TrialAudioRegistry {
  adapters: ReadonlyMap<string, AudioPlayAdapter>;
  snapshots: Readonly<Record<string, AudioPlaybackSnapshot>>;
  stopSignals: Readonly<Record<string, number>>;
  globalStopSignal: number;
  onStateChange: (assetId: string, snapshot: AudioPlaybackSnapshot) => void;
}

export function filterTrialAudioAttachments(
  attachments: readonly PreviewTrialAttachment[] | undefined,
): Array<{ id: string; name: string; mime: string }> {
  return toTrialAttachments(attachments).filter((att) =>
    att.mime.toLowerCase().startsWith("audio/"),
  );
}

export function collectTrialAudioAssetIds(
  paper: PreviewTrialPaperResponse | undefined,
): string[] {
  if (!paper) return [];
  const ids = new Set<string>();
  const add = (attachments: readonly PreviewTrialAttachment[] | undefined) => {
    for (const att of filterTrialAudioAttachments(attachments)) {
      ids.add(att.id);
    }
  };
  for (const part of paper.parts ?? []) {
    for (const block of part.blocks ?? []) {
      for (const item of block.items ?? []) {
        if (item.kind === "QUESTION" && item.question) {
          add(item.question.attachments);
        } else if (item.kind === "SET" && item.set) {
          add(item.set.attachments);
          for (const child of item.set.children ?? []) {
            add(child.attachments);
          }
        }
      }
    }
  }
  return [...ids];
}

function buildTrialAudioAdapters(
  assetIds: readonly string[],
  resetEpoch: number,
): ReadonlyMap<string, AudioPlayAdapter> {
  void resetEpoch;
  const map = new Map<string, AudioPlayAdapter>();
  for (const assetId of assetIds) {
    map.set(
      assetId,
      createTrialAudioAdapter({
        assetUrl: `/api/v1/admin/question-assets/${encodeURIComponent(assetId)}`,
        maxPlays: MAX_TRIAL_AUDIO_PLAYS,
      }),
    );
  }
  return map;
}

function snapshotsEqual(a: AudioPlaybackSnapshot, b: AudioPlaybackSnapshot): boolean {
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

/**
 * In-memory audio state & adapters for admin trial mode (FA-5 AC-4, BS 17.2).
 * Lifted above `TrialPaperView` so switching parts preserves play counts without DB writes.
 */
export function useTrialAudioRegistry(
  paper: PreviewTrialPaperResponse | undefined,
  resetEpoch: number,
): {
  registry: TrialAudioRegistry;
  stopAll: () => void;
  reset: () => void;
} {
  const assetIds = useMemo(() => collectTrialAudioAssetIds(paper), [paper]);
  const adapters = useMemo(
    () => buildTrialAudioAdapters(assetIds, resetEpoch),
    [assetIds, resetEpoch],
  );
  const [snapshots, setSnapshots] = useState<Record<string, AudioPlaybackSnapshot>>({});
  const [stopSignals, setStopSignals] = useState<Record<string, number>>({});
  const [globalStopSignal, setGlobalStopSignal] = useState(0);
  const activePlayingIdRef = useRef<string | null>(null);

  const onStateChange = useCallback((assetId: string, next: AudioPlaybackSnapshot) => {
    setSnapshots((prev) => {
      const current = prev[assetId];
      if (current && snapshotsEqual(current, next)) return prev;
      return { ...prev, [assetId]: next };
    });

    if (next.state === "PLAYING") {
      const prevActive = activePlayingIdRef.current;
      activePlayingIdRef.current = assetId;
      if (prevActive && prevActive !== assetId) {
        setStopSignals((prev) => ({
          ...prev,
          [prevActive]: (prev[prevActive] ?? 0) + 1,
        }));
      }
    } else if (activePlayingIdRef.current === assetId) {
      activePlayingIdRef.current = null;
    }
  }, []);

  const stopAll = useCallback(() => {
    activePlayingIdRef.current = null;
    setGlobalStopSignal((count) => count + 1);
  }, []);

  const reset = useCallback(() => {
    activePlayingIdRef.current = null;
    setSnapshots({});
    setStopSignals({});
  }, []);

  const registry = useMemo<TrialAudioRegistry>(
    () => ({
      adapters,
      snapshots,
      stopSignals,
      globalStopSignal,
      onStateChange,
    }),
    [adapters, snapshots, stopSignals, globalStopSignal, onStateChange],
  );

  return { registry, stopAll, reset };
}

export interface TrialAudioListProps {
  attachments: readonly PreviewTrialAttachment[] | undefined;
  registry: TrialAudioRegistry;
  closedReason?: AudioClosedReason | null;
}

export function TrialAudioList({
  attachments,
  registry,
  closedReason = null,
}: TrialAudioListProps) {
  const files = filterTrialAudioAttachments(attachments);
  if (files.length === 0) return null;

  return (
    <div className="flex flex-col gap-2">
      {files.map((file, index) => {
        const adapter = registry.adapters.get(file.id);
        if (!adapter) return null;
        const snapshot = registry.snapshots[file.id] ?? INITIAL_TRIAL_AUDIO_SNAPSHOT;
        const stopSignal = (registry.stopSignals[file.id] ?? 0) + registry.globalStopSignal;
        return (
          <AttemptAudioPlayer
            key={`${file.id}-${index}`}
            assetId={file.id}
            fileName={file.name || null}
            initialState={snapshot}
            maxPlays={MAX_TRIAL_AUDIO_PLAYS}
            adapter={adapter}
            closedReason={closedReason}
            stopSignal={stopSignal}
            onStateChange={(next) => registry.onStateChange(file.id, next)}
          />
        );
      })}
    </div>
  );
}
