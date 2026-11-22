"use client";

import {
  AttemptAudioPlayer,
  createTrialAudioAdapter,
  type AudioClosedReason,
  type AudioPlayAdapter,
  type AudioPlaybackSnapshot,
} from "@thi360/ui/question";
import { useTranslations } from "next-intl";
import { useCallback, useEffect, useMemo, useRef, useState } from "react";
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

const EMPTY_SNAPSHOTS: Readonly<Record<string, AudioPlaybackSnapshot>> = {};
const EMPTY_STOP_SIGNALS: Readonly<Record<string, number>> = {};

export interface TrialAudioRegistry {
  adapters: ReadonlyMap<string, AudioPlayAdapter>;
  snapshots: Readonly<Record<string, AudioPlaybackSnapshot>>;
  stopSignals: Readonly<Record<string, number>>;
  globalStopSignal: number;
  scopeKey: string;
  onStateChange: (assetId: string, instanceKey: string, snapshot: AudioPlaybackSnapshot) => void;
  stopAll: () => void;
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
  previewId: string,
  resetEpoch: number,
  localResetEpoch: number,
): ReadonlyMap<string, AudioPlayAdapter> {
  void previewId;
  void resetEpoch;
  void localResetEpoch;
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
  const assetIdsKey = useMemo(() => collectTrialAudioAssetIds(paper).join("\0"), [paper]);
  const previewId = paper?.previewId ?? "";
  const [localResetEpoch, setLocalResetEpoch] = useState(0);
  const scopeKey = `${previewId}\0${resetEpoch}\0${localResetEpoch}`;

  const adapters = useMemo(() => {
    const assetIds = assetIdsKey ? assetIdsKey.split("\0") : [];
    return buildTrialAudioAdapters(assetIds, previewId, resetEpoch, localResetEpoch);
  }, [assetIdsKey, previewId, resetEpoch, localResetEpoch]);

  const [snapshotStore, setSnapshotStore] = useState<{
    scopeKey: string;
    entries: Record<string, AudioPlaybackSnapshot>;
  }>({ scopeKey, entries: {} });
  const [stopSignalStore, setStopSignalStore] = useState<{
    scopeKey: string;
    entries: Record<string, number>;
  }>({ scopeKey, entries: {} });
  const [globalStopSignal, setGlobalStopSignal] = useState(0);
  const activePlayingRef = useRef<{ scopeKey: string; instanceKey: string | null }>({
    scopeKey: "",
    instanceKey: null,
  });

  const snapshots = snapshotStore.scopeKey === scopeKey ? snapshotStore.entries : EMPTY_SNAPSHOTS;
  const stopSignals = stopSignalStore.scopeKey === scopeKey ? stopSignalStore.entries : EMPTY_STOP_SIGNALS;

  const onStateChange = useCallback(
    (assetId: string, instanceKey: string, next: AudioPlaybackSnapshot) => {
      setSnapshotStore((prev) => {
        const base = prev.scopeKey === scopeKey ? prev.entries : {};
        const current = base[assetId];
        if (prev.scopeKey === scopeKey && current && snapshotsEqual(current, next)) return prev;
        return { scopeKey, entries: { ...base, [assetId]: next } };
      });

      const currentActive =
        activePlayingRef.current.scopeKey === scopeKey ? activePlayingRef.current.instanceKey : null;

      if (next.state === "PLAYING") {
        activePlayingRef.current = { scopeKey, instanceKey };
        if (currentActive && currentActive !== instanceKey) {
          setStopSignalStore((prev) => {
            const base = prev.scopeKey === scopeKey ? prev.entries : {};
            return {
              scopeKey,
              entries: { ...base, [currentActive]: (base[currentActive] ?? 0) + 1 },
            };
          });
        }
      } else if (currentActive === instanceKey) {
        activePlayingRef.current = { scopeKey, instanceKey: null };
      }
    },
    [scopeKey],
  );

  const stopAll = useCallback(() => {
    activePlayingRef.current = { scopeKey, instanceKey: null };
    setGlobalStopSignal((count) => count + 1);
  }, [scopeKey]);

  const reset = useCallback(() => {
    activePlayingRef.current = { scopeKey: "", instanceKey: null };
    setSnapshotStore({ scopeKey: "", entries: {} });
    setStopSignalStore({ scopeKey: "", entries: {} });
    setGlobalStopSignal((count) => count + 1);
    setLocalResetEpoch((count) => count + 1);
  }, []);

  const registry = useMemo<TrialAudioRegistry>(
    () => ({
      adapters,
      snapshots,
      stopSignals,
      globalStopSignal,
      scopeKey,
      onStateChange,
      stopAll,
    }),
    [adapters, snapshots, stopSignals, globalStopSignal, scopeKey, onStateChange, stopAll],
  );

  return { registry, stopAll, reset };
}

interface TrialAudioPlayerItemProps {
  assetId: string;
  instanceKey: string;
  fileName: string | null;
  snapshot: AudioPlaybackSnapshot;
  adapter: AudioPlayAdapter;
  disabled: boolean;
  closedReason: AudioClosedReason | null;
  stopSignal: number;
  onStateChange: (next: AudioPlaybackSnapshot) => void;
}

function TrialAudioPlayerItem({
  assetId,
  fileName,
  snapshot,
  adapter,
  disabled,
  closedReason,
  stopSignal,
  onStateChange,
}: TrialAudioPlayerItemProps) {
  // Suppress auto-resume on remount (e.g., returning to a part with previously interrupted PLAYING snapshots)
  // while keeping `snapshot.state === "PLAYING"` so "Resume" works without consuming an extra play, even at 0/2.
  const [mountDeferred, setMountDeferred] = useState(() => snapshot.state === "PLAYING");

  useEffect(() => {
    if (!mountDeferred) return;
    let cancelled = false;
    queueMicrotask(() => {
      if (!cancelled) setMountDeferred(false);
    });
    return () => {
      cancelled = true;
    };
  }, [mountDeferred]);

  return (
    <AttemptAudioPlayer
      assetId={assetId}
      fileName={fileName}
      initialState={snapshot}
      maxPlays={MAX_TRIAL_AUDIO_PLAYS}
      adapter={adapter}
      disabled={disabled}
      initializing={mountDeferred}
      closedReason={closedReason}
      stopSignal={stopSignal}
      onStateChange={onStateChange}
    />
  );
}

export interface TrialAudioListProps {
  attachments: readonly PreviewTrialAttachment[] | undefined;
  registry: TrialAudioRegistry;
  ownerKey?: string;
  contextLabel?: string;
  groupLabel?: string;
  disabled?: boolean;
  closedReason?: AudioClosedReason | null;
}

export function TrialAudioList({
  attachments,
  registry,
  ownerKey = "trial-audio",
  contextLabel,
  groupLabel,
  disabled = false,
  closedReason = null,
}: TrialAudioListProps) {
  const t = useTranslations("exams.trial.audio");
  const files = filterTrialAudioAttachments(attachments);
  if (files.length === 0) return null;

  const resolvedGroupLabel = groupLabel ?? contextLabel ?? t("setFallback");

  return (
    <div role="group" aria-label={resolvedGroupLabel} className="flex flex-col gap-2">
      {contextLabel && (
        <p className="text-caption font-medium text-text-secondary">{contextLabel}</p>
      )}
      {files.map((file, index) => {
        const adapter = registry.adapters.get(file.id);
        if (!adapter) return null;
        const instanceKey = `${ownerKey}:${file.id}:${index}`;
        const snapshot = registry.snapshots[file.id] ?? INITIAL_TRIAL_AUDIO_SNAPSHOT;
        const stopSignal = (registry.stopSignals[instanceKey] ?? 0) + registry.globalStopSignal;
        const fileLabel =
          files.length > 1
            ? file.name
              ? `${t("fileOf", { index: index + 1, total: files.length })} · ${file.name}`
              : t("fileOf", { index: index + 1, total: files.length })
            : file.name || null;
        return (
          <TrialAudioPlayerItem
            key={`${registry.scopeKey}:${instanceKey}`}
            assetId={file.id}
            instanceKey={instanceKey}
            fileName={fileLabel}
            snapshot={snapshot}
            adapter={adapter}
            disabled={disabled}
            closedReason={closedReason}
            stopSignal={stopSignal}
            onStateChange={(next) => registry.onStateChange(file.id, instanceKey, next)}
          />
        );
      })}
    </div>
  );
}
