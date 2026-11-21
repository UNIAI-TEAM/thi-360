import type { components } from "@thi360/api-client";
import {
  deleteItemDraft,
  type ItemDraftRecord,
  saveAttemptMeta,
  saveItemDraft,
} from "@/features/attempt-draft";
import { ApiStatusError } from "@/lib/api/client";
import {
  AttemptApiError,
  type FinalAnswerChange,
  loadAttemptState,
  saveAnswers,
  type SaveAnswersRequest,
  type SaveAnswersResponse,
} from "./api";
import type { ApiAnswerValue, ApiQuestionType, AttemptPosition, AttemptStateResponse } from "./mappers";

export const SAVE_DEBOUNCE_SHORT_MS = 800;
export const SAVE_DEBOUNCE_ESSAY_MS = 2000;
export const SAVE_ESSAY_MAX_WAIT_MS = 15_000;
export const SAVE_POSITION_DEBOUNCE_MS = 400;
export const SAVE_BATCH_SIZE = 50;
export const FINAL_ANSWERS_LIMIT = 500;
export const SAVE_BACKOFF_STEPS_MS = [1000, 2000, 4000, 8000, 16_000, 30_000] as const;

export type FinalAnswerResult = components["schemas"]["FinalAnswerResult"];

export type SaveReason =
  | "offline"
  | "rate_limited"
  | "network_error"
  | "server_error"
  | "session_conflict"
  | "rejected";

export type SaveStatusState = "saved" | "saving" | "unsaved";

export interface SaveStatusSnapshot {
  state: SaveStatusState;
  reason?: SaveReason;
  retryAfterSeconds?: number;
  pendingCount: number;
  hasUnsavedChanges: boolean;
  dirtyItemIds: string[];
  isOnline: boolean;
}

export interface ItemSyncState {
  itemId: string;
  partId: string;
  partClosed: boolean;
  questionType: ApiQuestionType;
  localSeq: number;
  serverConfirmedSeq: number;
  answer: ApiAnswerValue | null;
  flagged: boolean;
  serverFlagged: boolean;
  dirtyAnswer: boolean;
  dirtyFlag: boolean;
  rejectedReason?: string;
}

export interface SaveQueueDraftDeps {
  saveItemDraft: typeof saveItemDraft;
  deleteItemDraft: typeof deleteItemDraft;
  saveAttemptMeta: typeof saveAttemptMeta;
}

export interface AttemptSaveQueueOptions {
  attemptId: string;
  userId: string;
  saveAnswersFn?: (
    attemptId: string,
    sessionId: string,
    body: SaveAnswersRequest,
  ) => Promise<SaveAnswersResponse>;
  loadAttemptStateFn?: (attemptId: string) => Promise<AttemptStateResponse>;
  draftDeps?: SaveQueueDraftDeps;
  getSessionId: () => string | null;
  isReadOnly?: () => boolean;
  initialOnline?: boolean;
  onStatusChange?: (snapshot: SaveStatusSnapshot) => void;
  onServerNow?: (serverNowIso: string) => void;
  onStateReconciled?: (state: AttemptStateResponse) => void;
  onSessionRevoked?: (code: string) => void;
  onAttemptOrPartClosed?: (reason: string) => void;
}

function getDebounceMs(questionType: ApiQuestionType): number {
  switch (questionType) {
    case "SHORT_ANSWER":
    case "CLOZE":
      return SAVE_DEBOUNCE_SHORT_MS;
    case "ESSAY":
      return SAVE_DEBOUNCE_ESSAY_MS;
    default:
      return 0;
  }
}

function isAbortOrNetworkError(error: unknown): boolean {
  if (error instanceof ApiStatusError) return false;
  return error instanceof Error;
}

export class AttemptSaveQueue {
  private readonly attemptId: string;
  private readonly userId: string;
  private readonly saveAnswersFn: (
    attemptId: string,
    sessionId: string,
    body: SaveAnswersRequest,
  ) => Promise<SaveAnswersResponse>;
  private readonly loadAttemptStateFn: (attemptId: string) => Promise<AttemptStateResponse>;
  private readonly draftDeps: SaveQueueDraftDeps;
  private readonly getSessionId: () => string | null;
  private readonly isReadOnlyFn: () => boolean;
  private readonly onStatusChange?: (snapshot: SaveStatusSnapshot) => void;
  private readonly onServerNow?: (serverNowIso: string) => void;
  private readonly onStateReconciled?: (state: AttemptStateResponse) => void;
  private readonly onSessionRevoked?: (code: string) => void;
  private readonly onAttemptOrPartClosed?: (reason: string) => void;

  private readonly items = new Map<string, ItemSyncState>();
  private readonly itemDebounceTimers = new Map<string, ReturnType<typeof setTimeout>>();
  private readonly essayMaxWaitTimers = new Map<string, ReturnType<typeof setTimeout>>();
  private positionDebounceTimer: ReturnType<typeof setTimeout> | null = null;
  private retryTimer: ReturnType<typeof setTimeout> | null = null;

  private pendingPosition: AttemptPosition | null = null;
  private dirtyPosition = false;
  private inFlightPromise: Promise<void> | null = null;
  private retryAttempt = 0;
  private isOnline: boolean;
  private lastErrorReason: SaveReason | undefined;
  private retryAfterSeconds: number | undefined;
  private destroyed = false;

  constructor(options: AttemptSaveQueueOptions) {
    this.attemptId = options.attemptId;
    this.userId = options.userId;
    this.saveAnswersFn = options.saveAnswersFn ?? saveAnswers;
    this.loadAttemptStateFn = options.loadAttemptStateFn ?? loadAttemptState;
    this.draftDeps = options.draftDeps ?? {
      saveItemDraft,
      deleteItemDraft,
      saveAttemptMeta,
    };
    this.getSessionId = options.getSessionId;
    this.isReadOnlyFn = options.isReadOnly ?? (() => false);
    this.isOnline =
      options.initialOnline ??
      (typeof navigator !== "undefined" && typeof navigator.onLine === "boolean"
        ? navigator.onLine
        : true);
    this.onStatusChange = options.onStatusChange;
    this.onServerNow = options.onServerNow;
    this.onStateReconciled = options.onStateReconciled;
    this.onSessionRevoked = options.onSessionRevoked;
    this.onAttemptOrPartClosed = options.onAttemptOrPartClosed;
  }

  hydrateFromState(state: AttemptStateResponse, drafts: ItemDraftRecord[] = []): void {
    const draftByItem = new Map<string, ItemDraftRecord>();
    for (const draft of drafts) {
      if (draft.userId === this.userId && draft.attemptId === this.attemptId) {
        draftByItem.set(draft.itemId, draft);
      }
    }

    for (const part of state.parts ?? []) {
      const partId = part.partId ?? "";
      const partClosed = part.state !== "OPEN";
      for (const item of part.items ?? []) {
        if (!item.itemId || item.questionType === "QUESTION_SET") continue;
        const itemId = item.itemId;
        const existing = this.items.get(itemId);
        const serverSeq = item.answerSeq ?? 0;
        const serverFlagged = Boolean(item.flagged);
        const draft = draftByItem.get(itemId);

        let localSeq = serverSeq;
        let answer: ApiAnswerValue | null = item.answer ?? null;
        let dirtyAnswer = false;
        let flagged = serverFlagged;
        let dirtyFlag = false;

        if (existing) {
          const confirmed = Math.max(existing.serverConfirmedSeq, serverSeq);
          if (existing.localSeq > confirmed) {
            localSeq = existing.localSeq;
            answer = existing.answer;
            dirtyAnswer = !partClosed;
          } else {
            localSeq = confirmed;
            answer = item.answer ?? null;
            dirtyAnswer = false;
          }
          if (existing.dirtyFlag) {
            flagged = existing.flagged;
            dirtyFlag = !partClosed && flagged !== serverFlagged;
          }
        } else if (draft) {
          if (draft.seq > serverSeq) {
            localSeq = draft.seq;
            answer = draft.answer;
            dirtyAnswer = !partClosed;
          }
          if (draft.dirtyFlag) {
            flagged = draft.flagged;
            dirtyFlag = !partClosed && flagged !== serverFlagged;
          }
        }

        this.items.set(itemId, {
          itemId,
          partId,
          partClosed,
          questionType: item.questionType ?? "SINGLE_CHOICE",
          localSeq,
          serverConfirmedSeq: Math.max(existing?.serverConfirmedSeq ?? 0, serverSeq),
          answer,
          flagged,
          serverFlagged,
          dirtyAnswer,
          dirtyFlag,
        });
      }
    }

    if (!this.isOnline && this.getDirtyItemIds().length > 0) {
      this.lastErrorReason = "offline";
    }
    this.emitStatus();
  }

  getItemState(itemId: string): ItemSyncState | undefined {
    return this.items.get(itemId);
  }

  getAnswer(itemId: string): ApiAnswerValue | null {
    return this.items.get(itemId)?.answer ?? null;
  }

  isFlagged(itemId: string): boolean {
    return this.items.get(itemId)?.flagged ?? false;
  }

  isItemSaved(itemId: string): boolean {
    const item = this.items.get(itemId);
    if (!item) return true;
    return !item.dirtyAnswer && !item.dirtyFlag && item.localSeq === item.serverConfirmedSeq;
  }

  getSnapshotMaps(): {
    answers: Record<string, ApiAnswerValue | null>;
    flags: Record<string, boolean>;
  } {
    const answers: Record<string, ApiAnswerValue | null> = {};
    const flags: Record<string, boolean> = {};
    for (const [itemId, item] of this.items.entries()) {
      answers[itemId] = item.answer;
      flags[itemId] = item.flagged;
    }
    return { answers, flags };
  }

  getDirtyItemIds(): string[] {
    const dirty: string[] = [];
    for (const item of this.items.values()) {
      if (item.partClosed) continue;
      if (item.dirtyAnswer || item.dirtyFlag || item.localSeq !== item.serverConfirmedSeq) {
        dirty.push(item.itemId);
      }
    }
    return dirty;
  }

  hasUnsavedChanges(): boolean {
    if (this.isReadOnlyFn()) return false;
    return this.getDirtyItemIds().length > 0;
  }

  getStatusSnapshot(): SaveStatusSnapshot {
    const dirtyIds = this.getDirtyItemIds();
    const pendingCount = dirtyIds.length;
    const inFlight = this.inFlightPromise !== null;
    const hasUnsaved = pendingCount > 0;

    let state: SaveStatusState;
    let reason: SaveReason | undefined = this.lastErrorReason;

    if (!this.isOnline && (hasUnsaved || inFlight)) {
      state = "unsaved";
      reason = "offline";
    } else if (this.lastErrorReason) {
      state = "unsaved";
    } else if (hasUnsaved || inFlight) {
      state = "saving";
      reason = undefined;
    } else {
      state = "saved";
      reason = undefined;
    }

    return {
      state,
      reason,
      retryAfterSeconds: reason === "rate_limited" ? this.retryAfterSeconds : undefined,
      pendingCount,
      hasUnsavedChanges: hasUnsaved,
      dirtyItemIds: dirtyIds,
      isOnline: this.isOnline,
    };
  }

  recordAnswerChange(
    itemId: string,
    questionType: ApiQuestionType,
    answer: ApiAnswerValue | null,
    partId = "",
  ): void {
    if (this.destroyed || this.isReadOnlyFn()) return;

    const existing = this.items.get(itemId);
    if (existing?.partClosed) return;

    const nextSeq = (existing?.localSeq ?? 0) + 1;
    const nextItem: ItemSyncState = {
      itemId,
      partId: existing?.partId || partId,
      partClosed: false,
      questionType,
      localSeq: nextSeq,
      serverConfirmedSeq: existing?.serverConfirmedSeq ?? 0,
      answer,
      flagged: existing?.flagged ?? false,
      serverFlagged: existing?.serverFlagged ?? false,
      dirtyAnswer: true,
      dirtyFlag: existing?.dirtyFlag ?? false,
      rejectedReason: undefined,
    };
    this.items.set(itemId, nextItem);
    this.persistItemDraft(nextItem);

    if (this.lastErrorReason === "rejected") {
      this.lastErrorReason = undefined;
    }
    if (!this.isOnline) {
      this.lastErrorReason = "offline";
    }
    this.emitStatus();

    const debounceMs = getDebounceMs(questionType);
    this.clearItemDebounce(itemId);

    if (debounceMs === 0) {
      this.clearEssayMaxWait(itemId);
      if (this.isOnline) {
        void this.dispatchFlush();
      }
      return;
    }

    if (questionType === "ESSAY" && !this.essayMaxWaitTimers.has(itemId)) {
      const maxWaitTimer = setTimeout(() => {
        this.essayMaxWaitTimers.delete(itemId);
        this.clearItemDebounce(itemId);
        if (!this.destroyed && this.isOnline && !this.isReadOnlyFn()) {
          void this.dispatchFlush();
        }
      }, SAVE_ESSAY_MAX_WAIT_MS);
      this.essayMaxWaitTimers.set(itemId, maxWaitTimer);
    }

    const timer = setTimeout(() => {
      this.itemDebounceTimers.delete(itemId);
      if (questionType === "ESSAY") {
        this.clearEssayMaxWait(itemId);
      }
      if (!this.destroyed && this.isOnline && !this.isReadOnlyFn()) {
        void this.dispatchFlush();
      }
    }, debounceMs);
    this.itemDebounceTimers.set(itemId, timer);
  }

  recordFlagChange(itemId: string, flagged: boolean): void {
    if (this.destroyed || this.isReadOnlyFn()) return;
    const existing = this.items.get(itemId);
    if (!existing || existing.partClosed) return;

    const nextItem: ItemSyncState = {
      ...existing,
      flagged,
      dirtyFlag: flagged !== existing.serverFlagged,
    };
    this.items.set(itemId, nextItem);
    this.persistItemDraft(nextItem);

    if (!this.isOnline) {
      this.lastErrorReason = "offline";
    }
    this.emitStatus();

    if (this.isOnline) {
      void this.dispatchFlush();
    }
  }

  setPosition(position: AttemptPosition, flushPending = false): void {
    if (this.destroyed) return;
    this.pendingPosition = position;
    void this.draftDeps.saveAttemptMeta({
      userId: this.userId,
      attemptId: this.attemptId,
      lastPosition: { partId: position.partId, itemId: position.itemId },
    });

    if (this.isReadOnlyFn()) return;
    this.dirtyPosition = true;

    if (flushPending && this.getDirtyItemIds().length > 0) {
      void this.flushNow();
      return;
    }

    if (this.positionDebounceTimer) {
      clearTimeout(this.positionDebounceTimer);
    }
    this.positionDebounceTimer = setTimeout(() => {
      this.positionDebounceTimer = null;
      if (!this.destroyed && this.isOnline && !this.isReadOnlyFn() && this.dirtyPosition) {
        void this.dispatchFlush();
      }
    }, SAVE_POSITION_DEBOUNCE_MS);
  }

  async flushNow(): Promise<void> {
    if (this.destroyed || this.isReadOnlyFn()) return;
    this.clearAllPendingTimers();
    if (!this.isOnline) {
      if (this.getDirtyItemIds().length > 0) {
        this.lastErrorReason = "offline";
        this.emitStatus();
      }
      return;
    }
    await this.dispatchFlush();
  }

  async handleOnline(): Promise<AttemptStateResponse | null> {
    if (this.destroyed) return null;
    this.isOnline = true;
    this.clearRetryTimer();
    this.lastErrorReason = undefined;
    this.retryAfterSeconds = undefined;
    this.retryAttempt = 0;

    if (this.inFlightPromise) {
      try {
        await this.inFlightPromise;
      } catch {
        // Reconcile below will resolve latest state.
      }
    }

    try {
      const freshState = await this.loadAttemptStateFn(this.attemptId);
      if (this.destroyed) return null;

      this.onServerNow?.(freshState.serverNow);
      this.reconcileAgainstServerState(freshState);
      this.onStateReconciled?.(freshState);
      this.emitStatus();

      if (!this.isReadOnlyFn() && (this.getDirtyItemIds().length > 0 || this.dirtyPosition)) {
        await this.dispatchFlush();
      }
      return freshState;
    } catch (error) {
      if (isAbortOrNetworkError(error)) {
        this.lastErrorReason = "network_error";
      } else if (error instanceof ApiStatusError && error.status >= 500) {
        this.lastErrorReason = "server_error";
      }
      this.emitStatus();
      return null;
    }
  }

  handleOffline(): void {
    this.isOnline = false;
    this.clearRetryTimer();
    if (this.getDirtyItemIds().length > 0) {
      this.lastErrorReason = "offline";
    }
    this.emitStatus();
  }

  getFinalAnswers(itemIds?: Set<string> | readonly string[]): FinalAnswerChange[] {
    const allowedSet = itemIds
      ? itemIds instanceof Set
        ? itemIds
        : new Set(itemIds)
      : null;
    const changes: FinalAnswerChange[] = [];
    for (const item of this.items.values()) {
      if (item.partClosed) continue;
      if (allowedSet && !allowedSet.has(item.itemId)) continue;
      if (item.dirtyAnswer || item.localSeq > item.serverConfirmedSeq) {
        changes.push({
          itemId: item.itemId,
          seq: item.localSeq,
          answer: item.answer ?? undefined,
        });
      }
      if (changes.length >= FINAL_ANSWERS_LIMIT) break;
    }
    return changes;
  }

  markFinalAnswersResult(results: FinalAnswerResult[]): void {
    for (const res of results) {
      const item = this.items.get(res.itemId);
      if (!item) continue;
      if (res.status === "SAVED") {
        item.serverConfirmedSeq = Math.max(item.serverConfirmedSeq, res.seq);
        item.dirtyAnswer = item.localSeq > item.serverConfirmedSeq;
        this.syncDraftAfterAck(item);
      } else if (res.status === "REJECTED") {
        item.rejectedReason = res.reason ?? "INVALID_ANSWER";
        item.dirtyAnswer = false;
      }
    }
    this.emitStatus();
  }

  destroy(): void {
    this.destroyed = true;
    this.clearAllPendingTimers();
    this.clearRetryTimer();
  }

  private reconcileAgainstServerState(state: AttemptStateResponse): void {
    const serverItems = new Map<
      string,
      { partClosed: boolean; seq: number; answer: ApiAnswerValue | null; flagged: boolean }
    >();

    for (const part of state.parts ?? []) {
      const partClosed = part.state !== "OPEN";
      for (const item of part.items ?? []) {
        if (!item.itemId || item.questionType === "QUESTION_SET") continue;
        serverItems.set(item.itemId, {
          partClosed,
          seq: item.answerSeq ?? 0,
          answer: item.answer ?? null,
          flagged: Boolean(item.flagged),
        });
      }
    }

    for (const [itemId, local] of this.items.entries()) {
      const server = serverItems.get(itemId);
      if (!server) continue;

      local.partClosed = server.partClosed;
      local.serverConfirmedSeq = Math.max(local.serverConfirmedSeq, server.seq);
      local.serverFlagged = server.flagged;

      if (server.partClosed) {
        local.localSeq = server.seq;
        local.answer = server.answer;
        local.dirtyAnswer = false;
        local.flagged = server.flagged;
        local.dirtyFlag = false;
        this.clearItemDebounce(itemId);
        this.clearEssayMaxWait(itemId);
        void this.draftDeps.deleteItemDraft(this.userId, this.attemptId, itemId);
        continue;
      }

      if (local.localSeq <= server.seq) {
        local.localSeq = server.seq;
        local.answer = server.answer;
        local.dirtyAnswer = false;
        this.clearItemDebounce(itemId);
        this.clearEssayMaxWait(itemId);
        this.syncDraftAfterAck(local);
      } else {
        local.dirtyAnswer = true;
      }

      if (!local.dirtyFlag) {
        local.flagged = server.flagged;
      } else {
        local.dirtyFlag = local.flagged !== server.flagged;
      }
    }
  }

  private async dispatchFlush(): Promise<void> {
    if (this.destroyed || this.isReadOnlyFn() || !this.isOnline) return;
    if (this.inFlightPromise) {
      await this.inFlightPromise;
      return;
    }

    const run = async () => {
      try {
        while (!this.destroyed && this.isOnline && !this.isReadOnlyFn()) {
          const sessionId = this.getSessionId();
          if (!sessionId) break;

          const snapshotItems = [...this.items.values()].filter(
            (item) => !item.partClosed && this.hasReadyDirtyWork(item),
          );
          const pendingFlags = [...this.items.values()].filter(
            (item) => !item.partClosed && item.dirtyFlag,
          );
          const shouldSendPosition = this.dirtyPosition && this.pendingPosition !== null;

          if (snapshotItems.length === 0 && pendingFlags.length === 0 && !shouldSendPosition) {
            break;
          }

          this.emitStatus();

          const changeBatches = chunkArray(snapshotItems, SAVE_BATCH_SIZE);
          const flagBatches = chunkArray(pendingFlags, SAVE_BATCH_SIZE);
          const batchCount = Math.max(1, changeBatches.length, flagBatches.length);

          let staleDetected = false;
          let closedReasonDetected: string | null = null;

          for (let i = 0; i < batchCount; i++) {
            const changesChunk = changeBatches[i] ?? [];
            const flagsChunk = flagBatches[i] ?? [];
            const sentSeqs = new Map<string, number>();
            const sentFlags = new Map<string, boolean>();

            const payload: SaveAnswersRequest = {
              ...(changesChunk.length > 0
                ? {
                    changes: changesChunk.map((item) => {
                      sentSeqs.set(item.itemId, item.localSeq);
                      return {
                        itemId: item.itemId,
                        seq: item.localSeq,
                        answer: item.answer ?? undefined,
                      };
                    }),
                  }
                : {}),
              ...(flagsChunk.length > 0
                ? {
                    flags: flagsChunk.map((item) => {
                      sentFlags.set(item.itemId, item.flagged);
                      return {
                        itemId: item.itemId,
                        flagged: item.flagged,
                      };
                    }),
                  }
                : {}),
              ...(i === batchCount - 1 && shouldSendPosition && this.pendingPosition
                ? { position: this.pendingPosition }
                : {}),
            };

            const positionSnapshot = payload.position;
            const response = await this.saveAnswersFn(this.attemptId, sessionId, payload);
            if (this.destroyed) return;

            this.onServerNow?.(response.serverNow);
            this.retryAttempt = 0;
            this.lastErrorReason = undefined;
            this.retryAfterSeconds = undefined;

            if (positionSnapshot && this.pendingPosition === positionSnapshot) {
              this.dirtyPosition = false;
            }

            for (const result of response.results) {
              const item = this.items.get(result.itemId);
              if (!item) continue;

              if (result.status === "SAVED") {
                item.serverConfirmedSeq = Math.max(item.serverConfirmedSeq, result.seq);
                item.dirtyAnswer = item.localSeq > item.serverConfirmedSeq;
                this.syncDraftAfterAck(item);
              } else if (result.status === "STALE") {
                item.serverConfirmedSeq = Math.max(item.serverConfirmedSeq, result.seq);
                if (item.localSeq <= result.seq) {
                  item.dirtyAnswer = false;
                }
                staleDetected = true;
              } else if (result.status === "REJECTED") {
                item.rejectedReason = result.reason ?? "INVALID_ANSWER";
                item.dirtyAnswer = false;
                if (result.reason === "TIME_UP" || result.reason === "PART_CLOSED") {
                  item.partClosed = true;
                  closedReasonDetected = result.reason;
                } else {
                  this.lastErrorReason = "rejected";
                }
                this.syncDraftAfterAck(item);
              }
            }

            for (const [flaggedItemId, sentFlagged] of sentFlags.entries()) {
              const item = this.items.get(flaggedItemId);
              if (!item) continue;
              item.serverFlagged = sentFlagged;
              item.dirtyFlag = item.flagged !== sentFlagged;
              this.syncDraftAfterAck(item);
            }
          }

          if (closedReasonDetected) {
            this.onAttemptOrPartClosed?.(closedReasonDetected);
            break;
          }

          if (staleDetected) {
            const freshState = await this.loadAttemptStateFn(this.attemptId);
            if (!this.destroyed) {
              this.onServerNow?.(freshState.serverNow);
              this.reconcileAgainstServerState(freshState);
              this.onStateReconciled?.(freshState);
            }
          }

          // Break if nothing remaining is immediately ready (debounced items stay scheduled).
          const moreReady = [...this.items.values()].some(
            (item) => !item.partClosed && this.hasReadyDirtyWork(item),
          );
          if (!moreReady && !this.dirtyPosition) {
            break;
          }
        }
      } catch (error) {
        if (this.destroyed) return;
        this.handleFlushError(error);
      } finally {
        this.inFlightPromise = null;
        this.emitStatus();
      }
    };

    this.inFlightPromise = run();
    await this.inFlightPromise;
  }

  private hasReadyDirtyWork(item: ItemSyncState): boolean {
    if (item.dirtyFlag) return true;
    if (!item.dirtyAnswer && item.localSeq === item.serverConfirmedSeq) return false;
    // If no active debounce timer is waiting on this item, it's ready to send now.
    return !this.itemDebounceTimers.has(item.itemId);
  }

  private handleFlushError(error: unknown): void {
    if (!this.isOnline || (typeof navigator !== "undefined" && navigator.onLine === false)) {
      this.isOnline = false;
      this.lastErrorReason = "offline";
      return;
    }

    if (error instanceof ApiStatusError) {
      if (error.status === 409) {
        if (error.code === "ATTEMPT_SESSION_REVOKED" || error.code === "ATTEMPT_OPEN_ELSEWHERE") {
          this.lastErrorReason = "session_conflict";
          this.onSessionRevoked?.(error.code);
          return;
        }
        if (error.code === "TIME_UP" || error.code === "ATTEMPT_NOT_OPEN" || error.code === "PART_CLOSED") {
          this.onAttemptOrPartClosed?.(error.code);
          return;
        }
      }

      if (error.status === 429) {
        const retrySecs =
          error instanceof AttemptApiError && typeof error.retryAfterSeconds === "number"
            ? Math.max(1, error.retryAfterSeconds)
            : Math.ceil(this.nextBackoffMs() / 1000);
        this.lastErrorReason = "rate_limited";
        this.retryAfterSeconds = retrySecs;
        this.scheduleRetry(retrySecs * 1000);
        return;
      }

      this.lastErrorReason = error.status >= 500 ? "server_error" : "network_error";
      this.scheduleRetry(this.nextBackoffMs());
      return;
    }

    this.lastErrorReason = "network_error";
    this.scheduleRetry(this.nextBackoffMs());
  }

  private nextBackoffMs(): number {
    const idx = Math.min(this.retryAttempt, SAVE_BACKOFF_STEPS_MS.length - 1);
    const delay = SAVE_BACKOFF_STEPS_MS[idx] ?? 30_000;
    this.retryAttempt += 1;
    return delay;
  }

  private scheduleRetry(delayMs: number): void {
    this.clearRetryTimer();
    this.retryTimer = setTimeout(() => {
      this.retryTimer = null;
      if (!this.destroyed && this.isOnline && !this.isReadOnlyFn()) {
        void this.dispatchFlush();
      }
    }, delayMs);
  }

  private clearItemDebounce(itemId: string): void {
    const timer = this.itemDebounceTimers.get(itemId);
    if (timer) {
      clearTimeout(timer);
      this.itemDebounceTimers.delete(itemId);
    }
  }

  private clearEssayMaxWait(itemId: string): void {
    const timer = this.essayMaxWaitTimers.get(itemId);
    if (timer) {
      clearTimeout(timer);
      this.essayMaxWaitTimers.delete(itemId);
    }
  }

  private clearAllPendingTimers(): void {
    for (const timer of this.itemDebounceTimers.values()) {
      clearTimeout(timer);
    }
    this.itemDebounceTimers.clear();
    for (const timer of this.essayMaxWaitTimers.values()) {
      clearTimeout(timer);
    }
    this.essayMaxWaitTimers.clear();
    if (this.positionDebounceTimer) {
      clearTimeout(this.positionDebounceTimer);
      this.positionDebounceTimer = null;
    }
  }

  private clearRetryTimer(): void {
    if (this.retryTimer) {
      clearTimeout(this.retryTimer);
      this.retryTimer = null;
    }
  }

  private persistItemDraft(item: ItemSyncState): void {
    void this.draftDeps.saveItemDraft({
      userId: this.userId,
      attemptId: this.attemptId,
      itemId: item.itemId,
      seq: item.localSeq,
      syncedSeq: item.serverConfirmedSeq,
      answer: item.answer,
      flagged: item.flagged,
      dirtyAnswer: item.dirtyAnswer,
      dirtyFlag: item.dirtyFlag,
    });
  }

  private syncDraftAfterAck(item: ItemSyncState): void {
    if (!item.dirtyAnswer && !item.dirtyFlag && item.localSeq === item.serverConfirmedSeq) {
      void this.draftDeps.deleteItemDraft(this.userId, this.attemptId, item.itemId);
      return;
    }
    this.persistItemDraft(item);
  }

  private emitStatus(): void {
    this.onStatusChange?.(this.getStatusSnapshot());
  }
}

function chunkArray<T>(items: T[], size: number): T[][] {
  if (items.length === 0) return [];
  const chunks: T[][] = [];
  for (let i = 0; i < items.length; i += size) {
    chunks.push(items.slice(i, i + size));
  }
  return chunks;
}
