"use client";

import {
  clearAttemptDraftsForAttempt,
  deleteItemDraft,
  loadAttemptDrafts,
  loadAttemptMeta,
  loadAttemptStartContext,
  saveAttemptMeta,
  saveAttemptStartContext,
  saveItemDraft,
} from "@/features/attempt-draft";
import { PreparingScreen } from "@/features/attempt-prepare";
import { startAttempt } from "@/features/attempt-prepare/api";
import { SESSION_QUERY_KEY, loadSession, refreshSession, type SessionLoader } from "@/features/session/api";
import { ApiStatusError } from "@/lib/api/client";
import { webUrl } from "@/lib/env";
import { useQuery } from "@tanstack/react-query";
import { Button, cn, focusRing } from "@thi360/ui";
import { Question, orderById } from "@thi360/ui/question";
import { buildLoginUrl } from "@thi360/web-kit/redirect";
import {
  AlertCircle,
  CheckCircle2,
  Clock,
  CloudOff,
  FileText,
  LoaderCircle,
  RotateCw,
} from "lucide-react";
import { useLocale, useTranslations } from "next-intl";
import Link from "next/link";
import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import {
  type AfterStartChoiceView,
  AttemptApiError,
  claimEditSession,
  confirmPartChoice,
  finishPart,
  loadAfterStartChoicesForAttempt,
  loadAttemptState,
  retryPartGeneration,
  saveAnswers,
  sendHeartbeat,
  submitAttempt,
} from "./api";
import {
  AttemptTabLock,
  EditSessionController,
  type EditSessionSnapshot,
  type TabLockChannel,
  type TabLockRole,
} from "./edit-session";
import { FinishPartDialog } from "./finish-part-dialog";
import { LazyEssayEditor } from "./lazy-essay-editor";
import {
  type ApiAnswerValue,
  type AttemptItemView,
  type AttemptPartStateView,
  type AttemptStateResponse,
  buildQuestionSetKitValue,
  buildQuestionUnits,
  classifyItemCompletion,
  getEssayLimitStatus,
  getLeafItems,
  parseLastPosition,
  toApiAnswer,
  toKitAnswer,
} from "./mappers";
import { PartChoiceView } from "./part-choice-view";
import { PartNavigation } from "./part-navigation";
import { QuestionNav, type QuestionNavItem } from "./question-nav";
import { AttemptSaveQueue, type SaveStatusSnapshot } from "./save-queue";
import {
  type ServerClockAnchor,
  createServerClockAnchor,
  formatClockDuration,
  getClockDurationParts,
  getRemainingMs,
  reanchorServerClock,
} from "./server-clock";
import {
  type EssayUnderLimitWarning,
  SubmitConfirmDialog,
  type SubmitPartBreakdownRow,
} from "./submit-confirm-dialog";
import { ReadonlyTabBanner, SessionEvictedView, TakeoverDialog } from "./takeover-dialog";

export interface AttemptPlayerDeps {
  loadAttemptStateFn?: typeof loadAttemptState;
  claimEditSessionFn?: typeof claimEditSession;
  sendHeartbeatFn?: typeof sendHeartbeat;
  saveAnswersFn?: typeof saveAnswers;
  finishPartFn?: typeof finishPart;
  submitAttemptFn?: typeof submitAttempt;
  confirmPartChoiceFn?: typeof confirmPartChoice;
  retryPartGenerationFn?: typeof retryPartGeneration;
  loadAfterStartChoicesFn?: (attemptId: string, userId: string) => Promise<AfterStartChoiceView[]>;
  loadDraftsFn?: typeof loadAttemptDrafts;
  saveItemDraftFn?: typeof saveItemDraft;
  deleteItemDraftFn?: typeof deleteItemDraft;
  saveAttemptMetaFn?: typeof saveAttemptMeta;
  loadAttemptMetaFn?: typeof loadAttemptMeta;
  clearAttemptDraftsFn?: typeof clearAttemptDraftsForAttempt;
  loadAttemptStartContextFn?: typeof loadAttemptStartContext;
  saveAttemptStartContextFn?: typeof saveAttemptStartContext;
  startAttemptFn?: typeof startAttempt;
  refreshSessionFn?: typeof refreshSession;
  navigateFn?: (url: string) => void;
  createTabLockChannelFn?: (name: string) => TabLockChannel | null;
}

export interface AttemptPlayerProps {
  attemptId: string;
  userId: string;
  deps?: AttemptPlayerDeps;
}

const EMPTY_DEPS: AttemptPlayerDeps = {};

function defaultNavigate(url: string): void {
  if (typeof window !== "undefined") {
    window.location.assign(url);
  }
}

const KNOWN_RETRY_START_ERRORS = new Set<string>([
  "NO_ATTEMPTS_LEFT",
  "CHOICE_NOT_ALLOWED",
  "NO_SERVING_OPTION",
  "SUBJECTS_INVALID",
  "RATE_LIMITED",
]);

type AttemptEndReason = "SUBMITTED" | "TIME_UP" | "CHOICE_TIMEOUT" | "FAULT_CLOSED";

function inferAttemptEndReason(state: AttemptStateResponse): AttemptEndReason | null {
  if (!CLOSED_STATES.has(state.state)) return null;
  const parts = [...(state.parts ?? [])].sort((a, b) => (a.ord ?? 0) - (b.ord ?? 0));
  for (const part of parts) {
    if (
      part.state === "SKIPPED" &&
      part.choiceDeadlineAt &&
      part.closedAt &&
      Date.parse(part.closedAt) >= Date.parse(part.choiceDeadlineAt)
    ) {
      return "CHOICE_TIMEOUT";
    }
  }
  if (parts.some((part) => part.state === "SKIPPED" && !part.choiceDeadlineAt)) {
    return "TIME_UP";
  }
  if (
    state.timing === "SHARED" &&
    state.deadlineAt &&
    parts.some((part) => part.closedAt && Date.parse(part.closedAt) >= Date.parse(state.deadlineAt!))
  ) {
    return "TIME_UP";
  }
  const lastPart = parts[parts.length - 1];
  if (
    lastPart?.closedAt &&
    lastPart?.deadlineAt &&
    Date.parse(lastPart.closedAt) >= Date.parse(lastPart.deadlineAt)
  ) {
    return "TIME_UP";
  }
  return null;
}

const DEFAULT_SAVE_STATUS: SaveStatusSnapshot = {
  state: "saved",
  pendingCount: 0,
  hasUnsavedChanges: false,
  dirtyItemIds: [],
  isOnline: true,
};

const INITIAL_EDIT_SESSION_SNAP: EditSessionSnapshot = {
  status: "idle",
  sessionId: null,
};

const CLOSED_STATES = new Set(["GRADING", "PENDING_REVIEW", "SCORED", "GRADING_ERROR"]);

function isChoiceOrGeneratingState(state: string | undefined): boolean {
  return (
    state === "AWAITING_CHOICE" ||
    state === "GENERATING" ||
    state === "GENERATION_FAILED" ||
    state === "GEN_FAILED"
  );
}

function resolveInitialPartOrd(
  state: AttemptStateResponse,
  fallbackPartId?: string,
): number | null {
  const parts = [...(state.parts ?? [])].sort((a, b) => (a.ord ?? 0) - (b.ord ?? 0));
  if (parts.length === 0) return null;

  const preferredPartId =
    parseLastPosition(state.lastPosition as Record<string, unknown> | undefined)?.partId ??
    fallbackPartId;

  if (preferredPartId) {
    const preferred = parts.find((p) => p.partId === preferredPartId);
    if (preferred && (preferred.state === "OPEN" || isChoiceOrGeneratingState(preferred.state))) {
      return preferred.ord ?? null;
    }
  }

  const awaitingOrGen = parts.find((p) => isChoiceOrGeneratingState(p.state));
  const firstOpen = parts.find((p) => p.state === "OPEN");
  if (awaitingOrGen && (!firstOpen || (awaitingOrGen.ord ?? 0) < (firstOpen.ord ?? 0))) {
    return awaitingOrGen.ord ?? null;
  }
  if (firstOpen) return firstOpen.ord ?? null;
  if (awaitingOrGen) return awaitingOrGen.ord ?? null;
  return parts[0]?.ord ?? null;
}

function buildChoiceTitleMap(
  state: AttemptStateResponse | null,
  afterStartChoices: AfterStartChoiceView[],
): Record<number, { name: string }> {
  const map: Record<number, { name: string }> = {};
  if (!state?.choices) return map;

  const choicesRecord = state.choices as Record<string, unknown>;
  for (const part of state.parts ?? []) {
    const ord = part.ord;
    if (typeof ord !== "number") continue;
    const rawEntry = choicesRecord[String(ord)] ?? (part.choice as unknown);
    if (!rawEntry || typeof rawEntry !== "object") continue;
    const optionCode = (rawEntry as Record<string, unknown>).optionCode;
    if (typeof optionCode !== "string") continue;

    const spec = afterStartChoices.find((c) => c.ord === ord || c.partCode === part.partCode);
    const option = spec?.options?.find((opt) => opt.code === optionCode);
    if (option?.name) {
      map[ord] = { name: option.name };
    }
  }
  return map;
}

function findNextPartState(
  parts: AttemptPartStateView[],
  currentOrd: number | null,
): string | null {
  if (currentOrd === null) return null;
  const sorted = [...parts].sort((a, b) => (a.ord ?? 0) - (b.ord ?? 0));
  const next = sorted.find((p) => (p.ord ?? 0) > currentOrd);
  return next?.state ?? null;
}

function formatStaticDeadlineRemaining(
  deadlineIso: string | undefined,
  clock: ServerClockAnchor | null,
): number | null {
  if (!deadlineIso) return null;
  const deadlineMs = Date.parse(deadlineIso);
  if (!Number.isFinite(deadlineMs)) return null;
  const baseMs = clock ? clock.serverNowMs : deadlineMs;
  return Math.max(0, deadlineMs - baseMs);
}

function makeUuid(): string {
  if (typeof crypto !== "undefined" && typeof crypto.randomUUID === "function") {
    return crypto.randomUUID();
  }
  return `idem-${Date.now()}-${Math.random().toString(36).slice(2)}`;
}

function isEditableKeyboardTarget(target: EventTarget | null): boolean {
  if (!(target instanceof HTMLElement)) return false;
  const tag = target.tagName;
  if (tag === "INPUT" || tag === "TEXTAREA" || tag === "SELECT") {
    return true;
  }
  if (target.isContentEditable) return true;
  if (target.closest('[contenteditable="true"], [role="textbox"], [role="combobox"], [role="listbox"]')) {
    return true;
  }
  return false;
}

export function AttemptPlayer({ attemptId, userId, deps = EMPTY_DEPS }: AttemptPlayerProps) {
  const t = useTranslations("attempt");
  const tPrepare = useTranslations("prepare");
  const locale = useLocale();

  const resolvedDeps = useMemo(
    () => ({
      loadAttemptStateFn: deps.loadAttemptStateFn ?? loadAttemptState,
      claimEditSessionFn: deps.claimEditSessionFn ?? claimEditSession,
      sendHeartbeatFn: deps.sendHeartbeatFn ?? sendHeartbeat,
      saveAnswersFn: deps.saveAnswersFn ?? saveAnswers,
      finishPartFn: deps.finishPartFn ?? finishPart,
      submitAttemptFn: deps.submitAttemptFn ?? submitAttempt,
      confirmPartChoiceFn: deps.confirmPartChoiceFn ?? confirmPartChoice,
      retryPartGenerationFn: deps.retryPartGenerationFn ?? retryPartGeneration,
      loadAfterStartChoicesFn: deps.loadAfterStartChoicesFn ?? loadAfterStartChoicesForAttempt,
      loadDraftsFn: deps.loadDraftsFn ?? loadAttemptDrafts,
      saveItemDraftFn: deps.saveItemDraftFn ?? saveItemDraft,
      deleteItemDraftFn: deps.deleteItemDraftFn ?? deleteItemDraft,
      saveAttemptMetaFn: deps.saveAttemptMetaFn ?? saveAttemptMeta,
      loadAttemptMetaFn: deps.loadAttemptMetaFn ?? loadAttemptMeta,
      clearAttemptDraftsFn: deps.clearAttemptDraftsFn ?? clearAttemptDraftsForAttempt,
      loadAttemptStartContextFn: deps.loadAttemptStartContextFn ?? loadAttemptStartContext,
      saveAttemptStartContextFn: deps.saveAttemptStartContextFn ?? saveAttemptStartContext,
      startAttemptFn: deps.startAttemptFn ?? startAttempt,
      refreshSessionFn: deps.refreshSessionFn ?? refreshSession,
      navigateFn: deps.navigateFn ?? defaultNavigate,
      createTabLockChannelFn: deps.createTabLockChannelFn,
    }),
    [deps],
  );
  const resolvedDepsRef = useRef(resolvedDeps);
  useEffect(() => {
    resolvedDepsRef.current = resolvedDeps;
  }, [resolvedDeps]);

  const [loadPhase, setLoadPhase] = useState<"loading" | "ready" | "not_found" | "error">("loading");
  const [attemptState, setAttemptState] = useState<AttemptStateResponse | null>(null);
  const [closedEndReason, setClosedEndReason] = useState<AttemptEndReason | null>(null);
  const [afterStartChoices, setAfterStartChoices] = useState<AfterStartChoiceView[]>([]);
  const [serverClock, setServerClock] = useState<ServerClockAnchor | null>(null);
  const [selectedPartOrd, setSelectedPartOrd] = useState<number | null>(null);
  const [unitIndex, setUnitIndex] = useState(0);
  const [answersByItem, setAnswersByItem] = useState<Record<string, ApiAnswerValue | null>>({});
  const [flagsByItem, setFlagsByItem] = useState<Record<string, boolean>>({});
  const [saveStatus, setSaveStatus] = useState<SaveStatusSnapshot>(DEFAULT_SAVE_STATUS);
  const [sessionSnap, setSessionSnap] = useState<EditSessionSnapshot>(INITIAL_EDIT_SESSION_SNAP);
  const [tabRole, setTabRole] = useState<TabLockRole>("acquiring");

  const [choiceErrorCode, setChoiceErrorCode] = useState<string | null>(null);
  const [choiceBusySec, setChoiceBusySec] = useState<number | null>(null);
  const [choiceSubmitting, setChoiceSubmitting] = useState(false);
  const [choiceRetrying, setChoiceRetrying] = useState(false);
  const [abortRetrying, setAbortRetrying] = useState(false);
  const [abortRetryError, setAbortRetryError] = useState<string | null>(null);

  const [finishDialogOpen, setFinishDialogOpen] = useState(false);
  const [finishDialogStatus, setFinishDialogStatus] = useState<
    "idle" | "submitting" | "unconfirmed" | "reconciling"
  >("idle");
  const [submitDialogOpen, setSubmitDialogOpen] = useState(false);
  const [submitDialogStatus, setSubmitDialogStatus] = useState<
    "idle" | "submitting" | "unconfirmed" | "reconciling"
  >("idle");
  const [takeoverPending, setTakeoverPending] = useState(false);

  const [timerTick, setTimerTick] = useState<{ deadlineKey: string; remainingMs: number | null }>({
    deadlineKey: "",
    remainingMs: null,
  });

  const saveQueueRef = useRef<AttemptSaveQueue | null>(null);
  const editSessionRef = useRef<EditSessionController | null>(null);
  const tabLockRef = useRef<AttemptTabLock | null>(null);
  const choiceIdempotencyRef = useRef<{ key: string; target: string } | null>(null);
  const submitIdempotencyRef = useRef<string | null>(null);
  const expiredHandledDeadlineRef = useRef<string | null>(null);
  const expiredChoiceDeadlineRef = useRef<string | null>(null);
  const tabRoleRef = useRef<TabLockRole>("acquiring");
  const sessionSnapRef = useRef<EditSessionSnapshot>(INITIAL_EDIT_SESSION_SNAP);
  const loadPhaseRef = useRef<"loading" | "ready" | "not_found" | "error">("loading");
  const redirectingForAuthRef = useRef(false);
  const unauthorizedPromiseRef = useRef<Promise<void> | null>(null);

  useEffect(() => {
    tabRoleRef.current = tabRole;
  }, [tabRole]);

  useEffect(() => {
    sessionSnapRef.current = sessionSnap;
  }, [sessionSnap]);

  useEffect(() => {
    loadPhaseRef.current = loadPhase;
  }, [loadPhase]);

  const syncMapsFromQueue = useCallback((queue: AttemptSaveQueue | null) => {
    if (!queue) return;
    const { answers, flags } = queue.getSnapshotMaps();
    setAnswersByItem(answers);
    setFlagsByItem(flags);
  }, []);

  const applyFreshServerState = useCallback(
    (nextState: AttemptStateResponse, fallbackPos?: { partId?: string; itemId?: string } | null) => {
      setAttemptState(nextState);
      setServerClock((prev) =>
        prev ? reanchorServerClock(prev, nextState.serverNow) : createServerClockAnchor(nextState.serverNow),
      );

      if (nextState.state === "IN_PROGRESS") {
        setClosedEndReason(null);
      } else if (nextState.state === "ABORTED" || CLOSED_STATES.has(nextState.state)) {
        const inferred = inferAttemptEndReason(nextState);
        if (inferred) {
          setClosedEndReason((prev) => prev ?? inferred);
        }
        void resolvedDepsRef.current.clearAttemptDraftsFn(userId, attemptId);
      }

      setSelectedPartOrd((currentOrd) => {
        const parts = [...(nextState.parts ?? [])].sort((a, b) => (a.ord ?? 0) - (b.ord ?? 0));
        if (currentOrd !== null) {
          const existing = parts.find((p) => p.ord === currentOrd);
          if (existing && (existing.state === "OPEN" || isChoiceOrGeneratingState(existing.state))) {
            return currentOrd;
          }
        }
        return resolveInitialPartOrd(nextState, fallbackPos?.partId);
      });
    },
    [attemptId, userId],
  );

  const reloadServerState = useCallback(async () => {
    const fresh = await resolvedDepsRef.current.loadAttemptStateFn(attemptId);
    saveQueueRef.current?.hydrateFromState(fresh);
    syncMapsFromQueue(saveQueueRef.current);
    applyFreshServerState(fresh);
    return fresh;
  }, [attemptId, applyFreshServerState, syncMapsFromQueue]);

  const [reloadNonce, setReloadNonce] = useState(0);

  const handleAttemptUnauthorized = useCallback(() => {
    if (unauthorizedPromiseRef.current) {
      return unauthorizedPromiseRef.current;
    }

    const run = (async () => {
      try {
        await resolvedDepsRef.current.refreshSessionFn();
        if (loadPhaseRef.current !== "ready") {
          setReloadNonce((n) => n + 1);
        } else {
          await editSessionRef.current?.resumeAfterAuthRefresh();
          if ((saveQueueRef.current?.getDirtyItemIds().length ?? 0) > 0) {
            void saveQueueRef.current?.flushNow();
          }
        }
      } catch {
        redirectingForAuthRef.current = true;
        const currentHref =
          typeof window !== "undefined"
            ? window.location.href
            : `${webUrl()}/attempts/${encodeURIComponent(attemptId)}`;
        resolvedDepsRef.current.navigateFn(buildLoginUrl(webUrl(), currentHref));
      } finally {
        unauthorizedPromiseRef.current = null;
      }
    })();

    unauthorizedPromiseRef.current = run;
    return run;
  }, [attemptId]);

  const applyFreshServerStateRef = useRef(applyFreshServerState);
  const reloadServerStateRef = useRef(reloadServerState);
  const syncMapsFromQueueRef = useRef(syncMapsFromQueue);
  const handleAttemptUnauthorizedRef = useRef(handleAttemptUnauthorized);

  useEffect(() => {
    applyFreshServerStateRef.current = applyFreshServerState;
    reloadServerStateRef.current = reloadServerState;
    syncMapsFromQueueRef.current = syncMapsFromQueue;
    handleAttemptUnauthorizedRef.current = handleAttemptUnauthorized;
  }, [applyFreshServerState, handleAttemptUnauthorized, reloadServerState, syncMapsFromQueue]);

  // Initial mount: load state, drafts, meta, afterStartChoices, then start queue + edit session + tab lock
  useEffect(() => {
    let cancelled = false;
    const activeDeps = resolvedDepsRef.current;

    const tabLock = new AttemptTabLock({
      userId,
      attemptId,
      createChannel: activeDeps.createTabLockChannelFn,
      onRoleChange: (nextRole) => {
        if (cancelled) return;
        tabRoleRef.current = nextRole;
        setTabRole(nextRole);
        const controller = editSessionRef.current;
        if (!controller) return;
        if (nextRole === "secondary_readonly") {
          controller.setReadonlyTab(true);
        } else if (nextRole === "primary_owner") {
          controller.setReadonlyTab(false);
          if (controller.getSnapshot().status === "idle") {
            void controller.claim(false);
          }
        }
      },
    });
    tabLockRef.current = tabLock;

    const editSession = new EditSessionController({
      attemptId,
      userId,
      claimSessionFn: activeDeps.claimEditSessionFn,
      heartbeatFn: activeDeps.sendHeartbeatFn,
      onStatusChange: (snap) => {
        if (cancelled) return;
        sessionSnapRef.current = snap;
        setSessionSnap(snap);
        if (snap.status === "takeover_required") {
          setFinishDialogOpen(false);
          setSubmitDialogOpen(false);
        }
        if (snap.status === "active" && saveQueueRef.current && saveQueueRef.current.getDirtyItemIds().length > 0) {
          void saveQueueRef.current.flushNow();
        }
      },
      onServerNow: (serverNowIso) => {
        if (!cancelled) {
          setServerClock((prev) => reanchorServerClock(prev, serverNowIso));
        }
      },
      onAttemptClosed: (reason) => {
        if (!cancelled) {
          if (reason === "TIME_UP") {
            setClosedEndReason("TIME_UP");
          }
          void reloadServerStateRef.current().catch(() => undefined);
        }
      },
      onUnauthorized: () => {
        if (!cancelled) {
          void handleAttemptUnauthorizedRef.current();
        }
      },
    });
    editSessionRef.current = editSession;

    const saveQueue = new AttemptSaveQueue({
      attemptId,
      userId,
      saveAnswersFn: activeDeps.saveAnswersFn,
      loadAttemptStateFn: activeDeps.loadAttemptStateFn,
      draftDeps: {
        saveItemDraft: activeDeps.saveItemDraftFn,
        deleteItemDraft: activeDeps.deleteItemDraftFn,
        saveAttemptMeta: activeDeps.saveAttemptMetaFn,
      },
      getSessionId: () => editSession.getSessionId(),
      isReadOnly: () =>
        tabRoleRef.current === "secondary_readonly" ||
        sessionSnapRef.current.status === "evicted",
      onStatusChange: (snap) => {
        if (!cancelled) setSaveStatus(snap);
      },
      onServerNow: (serverNowIso) => {
        if (!cancelled) {
          setServerClock((prev) => reanchorServerClock(prev, serverNowIso));
        }
      },
      onStateReconciled: (fresh) => {
        if (!cancelled) {
          syncMapsFromQueueRef.current(saveQueue);
          applyFreshServerStateRef.current(fresh);
        }
      },
      onSessionRevoked: () => {
        editSession.markEvicted();
      },
      onAttemptOrPartClosed: (reason) => {
        if (!cancelled) {
          if (reason === "TIME_UP") {
            setClosedEndReason("TIME_UP");
          }
          void reloadServerStateRef.current().catch(() => undefined);
        }
      },
      onUnauthorized: () => {
        if (!cancelled) {
          void handleAttemptUnauthorizedRef.current();
        }
      },
    });
    saveQueueRef.current = saveQueue;

    const handleOnline = () => {
      void saveQueue.handleOnline();
    };
    const handleOffline = () => {
      saveQueue.handleOffline();
    };
    const handleBeforeUnload = (event: BeforeUnloadEvent) => {
      if (!redirectingForAuthRef.current && saveQueue.hasUnsavedChanges()) {
        event.preventDefault();
      }
    };

    window.addEventListener("online", handleOnline);
    window.addEventListener("offline", handleOffline);
    window.addEventListener("beforeunload", handleBeforeUnload);

    async function bootstrap() {
      loadPhaseRef.current = "loading";
      try {
        const depsNow = resolvedDepsRef.current;
        const [initialState, drafts, meta] = await Promise.all([
          depsNow.loadAttemptStateFn(attemptId),
          depsNow.loadDraftsFn(userId, attemptId).catch(() => []),
          depsNow.loadAttemptMetaFn(userId, attemptId).catch(() => null),
        ]);
        if (cancelled) return;

        applyFreshServerStateRef.current(initialState, meta?.lastPosition);
        loadPhaseRef.current = "ready";
        setLoadPhase("ready");

        if (initialState.state !== "IN_PROGRESS") {
          return;
        }

        saveQueue.hydrateFromState(initialState, drafts);
        syncMapsFromQueueRef.current(saveQueue);

        const targetPartOrd = resolveInitialPartOrd(initialState, meta?.lastPosition?.partId);
        const targetPart = (initialState.parts ?? []).find((p) => p.ord === targetPartOrd);
        const preferredItemId =
          parseLastPosition(initialState.lastPosition as Record<string, unknown> | undefined)?.itemId ??
          meta?.lastPosition?.itemId;
        if (targetPart && preferredItemId) {
          const units = buildQuestionUnits(targetPart.items, attemptId);
          const matchedUnitIdx = units.findIndex(
            (u) => u.id === preferredItemId || u.leafItems.some((c) => c.itemId === preferredItemId),
          );
          if (matchedUnitIdx >= 0) {
            setUnitIndex(matchedUnitIdx);
          }
        }

        const hasChoicePart = (initialState.parts ?? []).some(
          (p) => p.state === "AWAITING_CHOICE" || p.choice,
        );
        if (hasChoicePart) {
          void depsNow
            .loadAfterStartChoicesFn(attemptId, userId)
            .then((choices) => {
              if (!cancelled) setAfterStartChoices(choices);
            })
            .catch(() => undefined);
        }

        const role = tabLock.start();
        tabRoleRef.current = role;
        setTabRole(role);
        if (role === "primary_owner" && editSession.getSnapshot().status === "idle") {
          await editSession.claim(false);
        }
      } catch (error) {
        if (cancelled) return;
        if (error instanceof ApiStatusError && error.status === 401) {
          void handleAttemptUnauthorizedRef.current();
          return;
        }
        if (error instanceof ApiStatusError && (error.status === 404 || error.code === "ATTEMPT_NOT_FOUND")) {
          loadPhaseRef.current = "not_found";
          setLoadPhase("not_found");
        } else {
          loadPhaseRef.current = "error";
          setLoadPhase("error");
        }
      }
    }

    void bootstrap();

    return () => {
      cancelled = true;
      window.removeEventListener("online", handleOnline);
      window.removeEventListener("offline", handleOffline);
      window.removeEventListener("beforeunload", handleBeforeUnload);
      saveQueue.destroy();
      editSession.destroy();
      tabLock.destroy();
      saveQueueRef.current = null;
      editSessionRef.current = null;
      tabLockRef.current = null;
    };
  }, [attemptId, userId, reloadNonce]);

  const parts = useMemo(
    () => [...(attemptState?.parts ?? [])].sort((a, b) => (a.ord ?? 0) - (b.ord ?? 0)),
    [attemptState],
  );

  const activePart = useMemo(() => {
    if (parts.length === 0) return null;
    return parts.find((p) => p.ord === selectedPartOrd) ?? parts[0] ?? null;
  }, [parts, selectedPartOrd]);

  const activePartId = activePart?.partId;

  const handleReconcileFinishPart = useCallback(async () => {
    setFinishDialogStatus("reconciling");
    try {
      const fresh = await reloadServerState();
      const targetPart = (fresh.parts ?? []).find((p) => p.partId === activePartId);
      if (!targetPart || targetPart.state !== "OPEN" || fresh.state !== "IN_PROGRESS") {
        setUnitIndex(0);
        setFinishDialogOpen(false);
        setFinishDialogStatus("idle");
      } else {
        setFinishDialogStatus("idle");
      }
      if (fresh.state === "IN_PROGRESS") {
        void saveQueueRef.current?.flushNow();
      }
    } catch (error) {
      if (error instanceof ApiStatusError && error.status === 401) {
        await handleAttemptUnauthorized();
        if (!redirectingForAuthRef.current) {
          setFinishDialogStatus("unconfirmed");
        }
        return;
      }
      setFinishDialogStatus("unconfirmed");
    }
  }, [activePartId, handleAttemptUnauthorized, reloadServerState]);

  const handleReconcileSubmit = useCallback(async () => {
    setSubmitDialogStatus("reconciling");
    try {
      const fresh = await reloadServerState();
      if (fresh.state !== "IN_PROGRESS") {
        await resolvedDepsRef.current.clearAttemptDraftsFn(userId, attemptId).catch(() => undefined);
        setSubmitDialogOpen(false);
        setSubmitDialogStatus("idle");
      } else {
        setSubmitDialogStatus("idle");
        void saveQueueRef.current?.flushNow();
      }
    } catch (error) {
      if (error instanceof ApiStatusError && error.status === 401) {
        await handleAttemptUnauthorized();
        if (!redirectingForAuthRef.current) {
          setSubmitDialogStatus("unconfirmed");
        }
        return;
      }
      setSubmitDialogStatus("unconfirmed");
    }
  }, [attemptId, handleAttemptUnauthorized, reloadServerState, userId]);

  useEffect(() => {
    const finishUnconfirmed = finishDialogOpen && finishDialogStatus === "unconfirmed";
    const submitUnconfirmed = submitDialogOpen && submitDialogStatus === "unconfirmed";
    if (!finishUnconfirmed && !submitUnconfirmed) return;

    const onOnline = () => {
      if (finishUnconfirmed) {
        void handleReconcileFinishPart();
      }
      if (submitUnconfirmed) {
        void handleReconcileSubmit();
      }
    };

    window.addEventListener("online", onOnline);
    return () => window.removeEventListener("online", onOnline);
  }, [
    finishDialogOpen,
    finishDialogStatus,
    handleReconcileFinishPart,
    handleReconcileSubmit,
    submitDialogOpen,
    submitDialogStatus,
  ]);

  const choiceTitleMap = useMemo(
    () => buildChoiceTitleMap(attemptState, afterStartChoices),
    [attemptState, afterStartChoices],
  );

  useEffect(() => {
    if (
      attemptState?.state === "IN_PROGRESS" &&
      activePart?.state === "AWAITING_CHOICE" &&
      afterStartChoices.length === 0
    ) {
      let active = true;
      void resolvedDepsRef.current
        .loadAfterStartChoicesFn(attemptId, userId)
        .then((choices) => {
          if (active && choices.length > 0) setAfterStartChoices(choices);
        })
        .catch(() => undefined);
      return () => {
        active = false;
      };
    }
  }, [activePart?.state, afterStartChoices.length, attemptId, attemptState?.state, userId]);

  useEffect(() => {
    if (attemptState?.state !== "IN_PROGRESS" || activePart?.state !== "GENERATING") return;
    const interval = window.setInterval(() => {
      void reloadServerStateRef.current().catch(() => undefined);
    }, 1500);
    return () => window.clearInterval(interval);
  }, [activePart?.state, attemptState?.state]);

  const activeDeadlineIso =
    attemptState?.timing === "SHARED" ? attemptState.deadlineAt : activePart?.deadlineAt;
  const clockAnchorKey = serverClock ? `${serverClock.serverNowMs}:${serverClock.perfNowMs}` : "none";
  const timerKey = `${activeDeadlineIso ?? ""}:${clockAnchorKey}`;

  useEffect(() => {
    if (attemptState?.state !== "IN_PROGRESS" || !activeDeadlineIso || !serverClock) return;
    if (activePart?.state !== "OPEN" && attemptState.timing !== "SHARED") return;

    const updateTick = () => {
      const nextMs = getRemainingMs(activeDeadlineIso, serverClock);
      setTimerTick({ deadlineKey: timerKey, remainingMs: nextMs });
      if (nextMs !== null && nextMs <= 0 && expiredHandledDeadlineRef.current !== activeDeadlineIso) {
        expiredHandledDeadlineRef.current = activeDeadlineIso;
        setClosedEndReason((prev) => prev ?? "TIME_UP");
        window.setTimeout(() => {
          void reloadServerStateRef.current().catch(() => undefined);
        }, 500);
      }
    };

    const initTimer = window.setTimeout(updateTick, 0);
    const interval = window.setInterval(updateTick, 1000);
    return () => {
      window.clearTimeout(initTimer);
      window.clearInterval(interval);
    };
  }, [activeDeadlineIso, activePart?.state, attemptState?.state, attemptState?.timing, serverClock, timerKey]);

  const awaitingChoiceDeadlineIso =
    attemptState?.state === "IN_PROGRESS" && activePart?.state === "AWAITING_CHOICE"
      ? activePart.choiceDeadlineAt
      : undefined;

  const hasServerClock = serverClock !== null;
  const serverClockRef = useRef(serverClock);
  useEffect(() => {
    serverClockRef.current = serverClock;
  }, [serverClock]);

  useEffect(() => {
    if (!awaitingChoiceDeadlineIso || !hasServerClock) return;
    let pollTimeout: number | undefined;
    let cancelled = false;

    const scheduleReload = (delayMs: number) => {
      if (cancelled || pollTimeout !== undefined) return;
      pollTimeout = window.setTimeout(() => {
        pollTimeout = undefined;
        if (cancelled) return;
        void reloadServerStateRef.current().catch(() => undefined);
      }, delayMs);
    };

    const checkChoiceDeadline = () => {
      const clock = serverClockRef.current;
      if (!clock) return;
      const remaining = getRemainingMs(awaitingChoiceDeadlineIso, clock);
      if (remaining !== null && remaining <= 0) {
        if (expiredChoiceDeadlineRef.current !== awaitingChoiceDeadlineIso) {
          expiredChoiceDeadlineRef.current = awaitingChoiceDeadlineIso;
          setClosedEndReason((prev) => prev ?? "CHOICE_TIMEOUT");
          scheduleReload(500);
        } else {
          scheduleReload(2000);
        }
      }
    };

    const initTimer = window.setTimeout(checkChoiceDeadline, 0);
    const interval = window.setInterval(checkChoiceDeadline, 1000);
    return () => {
      cancelled = true;
      window.clearTimeout(initTimer);
      window.clearInterval(interval);
      if (pollTimeout !== undefined) {
        window.clearTimeout(pollTimeout);
      }
    };
  }, [awaitingChoiceDeadlineIso, hasServerClock]);

  const units = useMemo(
    () => (activePart ? buildQuestionUnits(activePart.items, attemptId) : []),
    [activePart, attemptId],
  );

  const safeUnitIndex = units.length > 0 ? Math.min(unitIndex, units.length - 1) : 0;
  const activeUnit = units[safeUnitIndex] ?? null;

  const isSecondaryTab = tabRole === "secondary_readonly";
  const isEvicted = sessionSnap.status === "evicted";
  const isSessionClaimPending =
    tabRole === "acquiring" || sessionSnap.status === "idle" || sessionSnap.status === "claiming";
  const isSessionLocked =
    isSecondaryTab ||
    isEvicted ||
    sessionSnap.status === "takeover_required" ||
    attemptState?.state !== "IN_PROGRESS";
  const isReadOnly = isSessionLocked || activePart?.state !== "OPEN";

  const dirtySet = useMemo(() => new Set(saveStatus.dirtyItemIds), [saveStatus.dirtyItemIds]);

  const getAnswer = useCallback(
    (itemId: string): ApiAnswerValue | null => answersByItem[itemId] ?? null,
    [answersByItem],
  );

  const getFlagged = useCallback(
    (itemId: string): boolean => Boolean(flagsByItem[itemId]),
    [flagsByItem],
  );

  const recordAnswerUpdate = useCallback(
    (itemId: string, qType: NonNullable<AttemptItemView["questionType"]>, apiAns: ApiAnswerValue | null) => {
      setAnswersByItem((prev) => ({ ...prev, [itemId]: apiAns }));
      saveQueueRef.current?.recordAnswerChange(itemId, qType, apiAns, activePart?.partId ?? "");
    },
    [activePart?.partId],
  );

  const toggleItemFlag = useCallback(
    (itemId: string) => {
      const nextFlagged = !Boolean(flagsByItem[itemId]);
      setFlagsByItem((prev) => ({ ...prev, [itemId]: nextFlagged }));
      saveQueueRef.current?.recordFlagChange(itemId, nextFlagged);
    },
    [flagsByItem],
  );

  const navItems: QuestionNavItem[] = useMemo(() => {
    const items: QuestionNavItem[] = [];
    units.forEach((unit, uIdx) => {
      for (const leaf of unit.leafItems) {
        const id = leaf.itemId ?? "";
        if (!id) continue;
        const ans = getAnswer(id);
        items.push({
          itemId: id,
          displayNo: leaf.displayNo ?? uIdx + 1,
          unitIndex: uIdx,
          completion: classifyItemCompletion(leaf, ans),
          flagged: getFlagged(id),
          unsaved: dirtySet.has(id),
          isCurrent: uIdx === safeUnitIndex,
        });
      }
    });
    return items;
  }, [dirtySet, getAnswer, getFlagged, safeUnitIndex, units]);

  const goToUnit = useCallback(
    (nextIndex: number) => {
      if (units.length === 0) return;
      const clamped = Math.max(0, Math.min(nextIndex, units.length - 1));
      setUnitIndex(clamped);
      const targetUnit = units[clamped];
      const partId = activePart?.partId;
      const itemId = targetUnit?.leafItems[0]?.itemId ?? targetUnit?.id;
      if (partId && itemId) {
        saveQueueRef.current?.setPosition({ partId, itemId }, true);
      }
    },
    [activePart?.partId, units],
  );

  // Global keyboard shortcuts (AC-29)
  useEffect(() => {
    if (
      attemptState?.state !== "IN_PROGRESS" ||
      activePart?.state !== "OPEN" ||
      finishDialogOpen ||
      submitDialogOpen ||
      sessionSnap.status === "takeover_required"
    ) {
      return;
    }

    const handleKeyDown = (event: KeyboardEvent) => {
      if (event.ctrlKey || event.metaKey || event.altKey) return;
      if (document.querySelector('[role="dialog"]')) return;
      if (isEditableKeyboardTarget(event.target)) return;

      if (event.key === "ArrowLeft" || event.key === "p" || event.key === "P") {
        if (safeUnitIndex > 0) {
          event.preventDefault();
          goToUnit(safeUnitIndex - 1);
        }
        return;
      }

      if (event.key === "ArrowRight" || event.key === "n" || event.key === "N") {
        if (safeUnitIndex < units.length - 1) {
          event.preventDefault();
          goToUnit(safeUnitIndex + 1);
        }
        return;
      }

      if (isReadOnly) return;

      if (event.key === "f" || event.key === "F") {
        const leaf = activeUnit?.leafItems[0];
        if (leaf?.itemId) {
          event.preventDefault();
          toggleItemFlag(leaf.itemId);
        }
        return;
      }

      if (
        (event.key === "1" || event.key === "2" || event.key === "3" || event.key === "4") &&
        activeUnit?.kind === "LEAF" &&
        activeUnit.kitQuestion.body.type === "SINGLE_CHOICE"
      ) {
        const optionIdx = Number(event.key) - 1;
        const orderedOptions = orderById(
          activeUnit.kitQuestion.body.options,
          activeUnit.kitQuestion.optionOrder,
        );
        const option = orderedOptions[optionIdx];
        if (option?.id && activeUnit.id) {
          event.preventDefault();
          recordAnswerUpdate(activeUnit.id, "SINGLE_CHOICE", {
            type: "SINGLE_CHOICE",
            optionId: option.id,
          });
        }
      }
    };

    window.addEventListener("keydown", handleKeyDown);
    return () => window.removeEventListener("keydown", handleKeyDown);
  }, [
    activePart?.state,
    activeUnit,
    attemptState?.state,
    finishDialogOpen,
    goToUnit,
    isReadOnly,
    recordAnswerUpdate,
    safeUnitIndex,
    sessionSnap.status,
    submitDialogOpen,
    toggleItemFlag,
    units.length,
  ]);

  if (loadPhase === "loading") {
    return (
      <main className="flex min-h-dvh items-center justify-center bg-page p-6">
        <div role="status" className="flex items-center gap-3 text-body text-text-muted">
          <LoaderCircle className="size-5 animate-spin motion-reduce:animate-none text-primary-text" aria-hidden="true" />
          <span>{t("waiting.title")}</span>
        </div>
      </main>
    );
  }

  if (loadPhase === "not_found" || loadPhase === "error" || !attemptState) {
    const isNotFound = loadPhase === "not_found";
    return (
      <main className="flex min-h-dvh items-center justify-center bg-page p-6">
        <section className="flex max-w-md flex-col items-center gap-4 rounded-2xl border border-border bg-surface p-8 text-center shadow-sm">
          <AlertCircle className="size-10 text-danger" aria-hidden="true" />
          <h1 className="text-h3 font-bold text-text">
            {isNotFound ? t("errors.notFoundTitle") : t("errors.loadTitle")}
          </h1>
          <p className="text-body text-text-muted">
            {isNotFound ? t("errors.notFoundDescription") : t("errors.loadDescription")}
          </p>
          <div className="flex flex-wrap justify-center gap-3 pt-2">
            {!isNotFound ? (
              <Button
                type="button"
                variant="primary"
                onClick={() => {
                  setLoadPhase("loading");
                  setReloadNonce((n) => n + 1);
                }}
              >
                <RotateCw className="size-4" aria-hidden="true" />
                <span>{t("errors.retry")}</span>
              </Button>
            ) : null}
            <Link
              href="/home"
              className={cn(
                "inline-flex h-10 items-center justify-center rounded-lg border border-border bg-surface px-4 text-label font-medium text-text-secondary hover:bg-subtle",
                focusRing,
              )}
            >
              {t("errors.loadBackToMyExams")}
            </Link>
          </div>
        </section>
      </main>
    );
  }

  if (attemptState.state === "STARTING" || attemptState.state === "ABORTED") {
    const handleAbortRetry = async () => {
      if (abortRetrying) return;
      setAbortRetrying(true);
      setAbortRetryError(null);
      try {
        const startCtx = await resolvedDepsRef.current.loadAttemptStartContextFn(userId, attemptId);
        if (!startCtx?.entitlementId) {
          resolvedDepsRef.current.navigateFn("/home");
          return;
        }
        const started = await resolvedDepsRef.current.startAttemptFn(
          {
            entitlementId: startCtx.entitlementId,
            ...(startCtx.preChoice ? { preChoice: startCtx.preChoice } : {}),
          },
          makeUuid(),
        );
        await resolvedDepsRef.current
          .saveAttemptStartContextFn(userId, started.attemptId, startCtx)
          .catch(() => undefined);
        if (started.attemptId === attemptId) {
          setReloadNonce((n) => n + 1);
        } else {
          resolvedDepsRef.current.navigateFn(`/attempts/${encodeURIComponent(started.attemptId)}`);
        }
      } catch (error) {
        if (error instanceof ApiStatusError && error.status === 401) {
          void handleAttemptUnauthorized();
          return;
        }
        if (
          error instanceof ApiStatusError &&
          (error.code === "CHOICE_NOT_ALLOWED" || error.code === "SUBJECTS_INVALID")
        ) {
          const ctx = await resolvedDepsRef.current
            .loadAttemptStartContextFn(userId, attemptId)
            .catch(() => null);
          if (ctx?.entitlementId) {
            resolvedDepsRef.current.navigateFn(`/prepare/${encodeURIComponent(ctx.entitlementId)}`);
            return;
          }
        }
        const code =
          error instanceof ApiStatusError && error.status === 429
            ? "RATE_LIMITED"
            : error instanceof ApiStatusError && KNOWN_RETRY_START_ERRORS.has(error.code)
              ? error.code
              : "generic";
        setAbortRetryError(tPrepare(`startErrors.${code as "generic"}`));
      } finally {
        setAbortRetrying(false);
      }
    };

    return (
      <PreparingScreen
        attemptId={attemptId}
        examName={attemptState.examName}
        initialState={attemptState.state}
        initialAbortReason={attemptState.abortReason ?? null}
        pollState={resolvedDeps.loadAttemptStateFn}
        onReady={() => {
          setReloadNonce((n) => n + 1);
        }}
        onRetry={handleAbortRetry}
        retrying={abortRetrying}
        retryErrorMessage={abortRetryError}
      />
    );
  }

  if (CLOSED_STATES.has(attemptState.state)) {
    const resolvedEndReason = closedEndReason ?? inferAttemptEndReason(attemptState);
    const closedHeading =
      resolvedEndReason === "TIME_UP"
        ? t("closed.headingTimeUp")
        : resolvedEndReason === "CHOICE_TIMEOUT"
          ? t("closed.headingChoiceTimeout")
          : t("closed.heading");

    return (
      <main className="flex min-h-dvh items-center justify-center bg-page p-6">
        <section className="flex max-w-lg flex-col items-center gap-4 rounded-2xl border border-border bg-surface p-8 text-center shadow-sm">
          <div className="flex size-12 items-center justify-center rounded-full bg-success-bg text-success">
            <CheckCircle2 className="size-6" aria-hidden="true" />
          </div>
          <h1 className="text-h3 font-bold text-text">{closedHeading}</h1>
          <p className="text-body text-text-muted">{t("closed.description")}</p>
          <div className="flex flex-wrap justify-center gap-3 pt-2">
            <Link
              href={`/attempts/${encodeURIComponent(attemptId)}/result`}
              prefetch={false}
              className={cn(
                "inline-flex h-10 items-center justify-center rounded-lg bg-primary-fill px-4 text-label font-semibold text-on-primary hover:bg-primary-fill-hover",
                focusRing,
              )}
            >
              {t("closed.viewResult")}
            </Link>
            <Link
              href="/home"
              className={cn(
                "inline-flex h-10 items-center justify-center rounded-lg border border-border bg-surface px-4 text-label font-medium text-text-secondary hover:bg-subtle",
                focusRing,
              )}
            >
              {t("closed.backToMyExams")}
            </Link>
          </div>
        </section>
      </main>
    );
  }

  if (isEvicted) {
    return (
      <main className="flex min-h-dvh items-center justify-center bg-page p-6">
        <SessionEvictedView
          isTakingOver={takeoverPending}
          onTakeoverHere={async () => {
            setTakeoverPending(true);
            try {
              const snap = await editSessionRef.current?.claim(true);
              if (snap?.status === "active") {
                await reloadServerState();
              }
            } finally {
              setTakeoverPending(false);
            }
          }}
        />
      </main>
    );
  }

  const activePartLeaves = activePart ? getLeafItems(activePart.items) : [];
  const finishMissingCount = activePartLeaves.filter(
    (item) => classifyItemCompletion(item, getAnswer(item.itemId ?? "")) === "blank",
  ).length;
  const finishPartialCount = activePartLeaves.filter(
    (item) => classifyItemCompletion(item, getAnswer(item.itemId ?? "")) === "partial",
  ).length;

  const allLeafItems: AttemptItemView[] = [];
  const partBreakdown: SubmitPartBreakdownRow[] = [];
  const essayWarnings: EssayUnderLimitWarning[] = [];
  let totalEssayWords = 0;
  let hasAnyEssay = false;
  let hasUnsavedEssay = false;

  for (const part of parts) {
    const partLeaves = getLeafItems(part.items);
    let answeredInPart = 0;
    let missingInPart = 0;

    for (const leaf of partLeaves) {
      allLeafItems.push(leaf);
      const id = leaf.itemId ?? "";
      const ans = getAnswer(id);
      const status = classifyItemCompletion(leaf, ans);
      if (status === "complete") {
        answeredInPart += 1;
      } else {
        missingInPart += 1;
      }

      const essayStatus = getEssayLimitStatus(leaf, ans);
      if (essayStatus.isEssay) {
        hasAnyEssay = true;
        totalEssayWords += essayStatus.wordCount;
        if (dirtySet.has(id)) hasUnsavedEssay = true;
        if (essayStatus.underLimit && essayStatus.wordLimit !== null) {
          essayWarnings.push({
            itemId: id,
            displayNo: leaf.displayNo ?? 1,
            wordCount: essayStatus.wordCount,
            wordLimit: essayStatus.wordLimit,
          });
        }
      }
    }

    if (partLeaves.length > 0) {
      const ord = part.ord ?? 0;
      partBreakdown.push({
        ord,
        partName: choiceTitleMap[ord]?.name ?? part.partName ?? part.partCode ?? `#${ord}`,
        answeredCount: answeredInPart,
        totalCount: partLeaves.length,
        missingCount: missingInPart,
      });
    }
  }

  const totalAnswered = allLeafItems.filter(
    (leaf) => classifyItemCompletion(leaf, getAnswer(leaf.itemId ?? "")) === "complete",
  ).length;
  const totalPartial = allLeafItems.filter(
    (leaf) => classifyItemCompletion(leaf, getAnswer(leaf.itemId ?? "")) === "partial",
  ).length;
  const totalBlank = allLeafItems.length - totalAnswered - totalPartial;
  const totalFlagged = allLeafItems.filter((leaf) => getFlagged(leaf.itemId ?? "")).length;

  const remainingMs =
    timerTick.deadlineKey === timerKey
      ? timerTick.remainingMs
      : formatStaticDeadlineRemaining(activeDeadlineIso, serverClock);
  const timerLabel =
    attemptState.timing === "SHARED"
      ? t("timer.sharedRemaining")
      : t("timer.partRemaining", { ord: activePart?.ord ?? 1 });
  const durationParts = remainingMs !== null ? getClockDurationParts(remainingMs) : null;
  const isTimerUrgent = remainingMs !== null && remainingMs <= 60_000;

  const isFreeMode = attemptState.navigationMode === "FREE";
  const canFinishPart =
    !isFreeMode &&
    activePart?.state === "OPEN" &&
    parts.some((p) => (p.ord ?? 0) > (activePart.ord ?? 0) && p.state !== "CLOSED" && p.state !== "SKIPPED");
  const canSubmitAttempt = isFreeMode
    ? attemptState.state === "IN_PROGRESS"
    : activePart?.state === "OPEN" && !canFinishPart;

  async function handleConfirmPartChoice(optionCode: string, selectedSubjects?: string[]) {
    const partId = activePart?.partId;
    const sessionId = editSessionRef.current?.getSessionId();
    if (!partId || !sessionId) return;

    const targetSig = `${partId}:${optionCode}:${(selectedSubjects ?? []).join(",")}`;
    if (!choiceIdempotencyRef.current || choiceIdempotencyRef.current.target !== targetSig) {
      choiceIdempotencyRef.current = { target: targetSig, key: makeUuid() };
    }

    setChoiceSubmitting(true);
    setChoiceErrorCode(null);
    setChoiceBusySec(null);

    try {
      await resolvedDepsRef.current.confirmPartChoiceFn(
        attemptId,
        partId,
        sessionId,
        choiceIdempotencyRef.current.key,
        {
          optionCode,
          ...(selectedSubjects && selectedSubjects.length > 0 ? { subjectCodes: selectedSubjects } : {}),
        },
      );
      await reloadServerState();
    } catch (error) {
      if (error instanceof ApiStatusError && error.status === 401) {
        void handleAttemptUnauthorized();
        return;
      }
      if (error instanceof ApiStatusError && error.status === 429) {
        setChoiceBusySec(error instanceof AttemptApiError ? (error.retryAfterSeconds ?? 5) : 5);
        setChoiceErrorCode("RATE_LIMITED");
      } else if (error instanceof ApiStatusError) {
        if (error.code === "ATTEMPT_SESSION_REVOKED") {
          editSessionRef.current?.markEvicted();
        } else if (
          error.code === "CHOICE_WINDOW_CLOSED" ||
          error.code === "CHOICE_LOCKED" ||
          error.code === "PART_NOT_AWAITING" ||
          error.code === "PART_GENERATING"
        ) {
          if (error.code === "CHOICE_WINDOW_CLOSED") {
            setClosedEndReason("CHOICE_TIMEOUT");
          }
          void reloadServerState().catch(() => undefined);
        }
        setChoiceErrorCode(error.code ?? "generic");
      } else {
        setChoiceErrorCode("generic");
      }
    } finally {
      setChoiceSubmitting(false);
    }
  }

  async function handleRetryGeneration() {
    const partId = activePart?.partId;
    const sessionId = editSessionRef.current?.getSessionId();
    if (!partId || !sessionId) return;

    setChoiceRetrying(true);
    setChoiceErrorCode(null);
    setChoiceBusySec(null);
    try {
      await resolvedDepsRef.current.retryPartGenerationFn(attemptId, partId, sessionId, makeUuid(), {});
      await reloadServerState();
    } catch (error) {
      if (error instanceof ApiStatusError && error.status === 401) {
        void handleAttemptUnauthorized();
        return;
      }
      if (error instanceof ApiStatusError && error.status === 429) {
        setChoiceBusySec(error instanceof AttemptApiError ? (error.retryAfterSeconds ?? 5) : 5);
        setChoiceErrorCode("RATE_LIMITED");
      } else if (error instanceof ApiStatusError) {
        if (error.code === "ATTEMPT_SESSION_REVOKED") {
          editSessionRef.current?.markEvicted();
        } else if (
          error.code === "CHOICE_WINDOW_CLOSED" ||
          error.code === "CHOICE_LOCKED" ||
          error.code === "PART_NOT_AWAITING" ||
          error.code === "PART_GENERATING"
        ) {
          if (error.code === "CHOICE_WINDOW_CLOSED") {
            setClosedEndReason("CHOICE_TIMEOUT");
          }
          void reloadServerState().catch(() => undefined);
        }
        setChoiceErrorCode(error.code ?? "generic");
      } else {
        setChoiceErrorCode("generic");
      }
    } finally {
      setChoiceRetrying(false);
    }
  }

  async function handleFinishPart() {
    const partId = activePart?.partId;
    const sessionId = editSessionRef.current?.getSessionId();
    const queue = saveQueueRef.current;
    if (!partId || !sessionId || !queue) return;

    setFinishDialogStatus("submitting");
    queue.cancelPendingTimers();
    const partIds = activePartLeaves.map((l) => l.itemId ?? "").filter(Boolean);
    const finalAnswers = queue.getFinalAnswers(partIds);

    try {
      const response = await resolvedDepsRef.current.finishPartFn(attemptId, partId, sessionId, {
        finalAnswers,
      });
      queue.markFinalAnswersResult(response.results);
      if (response.endReason) {
        setClosedEndReason(response.endReason);
      }
      await reloadServerState();
      setUnitIndex(0);
      setFinishDialogOpen(false);
      setFinishDialogStatus("idle");
    } catch (error) {
      if (error instanceof ApiStatusError) {
        if (error.status === 401) {
          await handleAttemptUnauthorized();
          if (!redirectingForAuthRef.current) {
            setFinishDialogStatus("idle");
          }
          return;
        }
        if (error.code === "ATTEMPT_SESSION_REVOKED") {
          setFinishDialogOpen(false);
          editSessionRef.current?.markEvicted();
          return;
        }
        if (error.status >= 400 && error.status < 500) {
          await reloadServerState().catch(() => undefined);
          setFinishDialogOpen(false);
          setFinishDialogStatus("idle");
          return;
        }
      }
      setFinishDialogStatus("unconfirmed");
    }
  }

  async function handleConfirmSubmit() {
    const sessionId = editSessionRef.current?.getSessionId();
    const queue = saveQueueRef.current;
    if (!sessionId || !queue) return;

    if (!submitIdempotencyRef.current) {
      submitIdempotencyRef.current = makeUuid();
    }

    setSubmitDialogStatus("submitting");
    queue.cancelPendingTimers();
    const finalAnswers = queue.getFinalAnswers();

    try {
      const response = await resolvedDepsRef.current.submitAttemptFn(
        attemptId,
        sessionId,
        submitIdempotencyRef.current,
        { finalAnswers },
      );
      submitIdempotencyRef.current = null;
      queue.markFinalAnswersResult(response.results);
      if (response.endReason) {
        setClosedEndReason(response.endReason);
      }
      await resolvedDepsRef.current.clearAttemptDraftsFn(userId, attemptId).catch(() => undefined);
      await reloadServerState();
      setSubmitDialogOpen(false);
      setSubmitDialogStatus("idle");
    } catch (error) {
      if (error instanceof ApiStatusError) {
        if (error.status === 401) {
          await handleAttemptUnauthorized();
          if (!redirectingForAuthRef.current) {
            setSubmitDialogStatus("idle");
          }
          return;
        }
        if (error.code === "ATTEMPT_SESSION_REVOKED") {
          setSubmitDialogOpen(false);
          editSessionRef.current?.markEvicted();
          return;
        }
        if (error.status === 409) {
          await reloadServerState().catch(() => undefined);
          setSubmitDialogOpen(false);
          setSubmitDialogStatus("idle");
          return;
        }
      }
      setSubmitDialogStatus("unconfirmed");
    }
  }

  const activeChoiceSpec =
    afterStartChoices.find(
      (c) => c.ord === activePart?.ord || (activePart?.partCode && c.partCode === activePart.partCode),
    ) ?? null;

  const activeQuestionHasEssay = Boolean(
    activeUnit &&
      (activeUnit.kitQuestion.body.type === "ESSAY" ||
        activeUnit.childKitQuestions.some((child) => child.body.type === "ESSAY")),
  );

  const saveStatusReasonText =
    saveStatus.reason === "rate_limited"
      ? t("saveStatus.reasons.rate_limited", { seconds: saveStatus.retryAfterSeconds ?? 5 })
      : saveStatus.reason
        ? t(`saveStatus.reasons.${saveStatus.reason}`)
        : null;

  const saveStatusText =
    saveStatus.state === "saved"
      ? t("saveStatus.saved")
      : saveStatus.state === "saving"
        ? t("saveStatus.saving")
        : saveStatusReasonText
          ? `${t("saveStatus.unsaved")} · ${saveStatusReasonText}`
          : t("saveStatus.unsaved");

  return (
    <div className="flex min-h-dvh flex-col bg-page text-text">
      <header className="sticky top-0 z-20 border-b border-border bg-surface">
        <div className="mx-auto flex max-w-7xl flex-wrap items-center justify-between gap-3 px-4 py-3">
          <div className="w-full min-w-0 space-y-1 md:w-auto md:flex-1">
            {attemptState.examName ? (
              <h1 className="line-clamp-2 text-title font-bold text-text" data-exam-content="">
                {attemptState.examName}
              </h1>
            ) : null}
            <PartNavigation
              parts={parts}
              currentPartOrd={activePart?.ord ?? null}
              navigationMode={attemptState.navigationMode ?? "SEQUENTIAL"}
              choiceMap={choiceTitleMap}
              disabled={isSessionLocked}
              onSelectPart={(ord) => {
                setSelectedPartOrd(ord);
                setUnitIndex(0);
                const targetPart = parts.find((p) => p.ord === ord);
                const targetUnits = targetPart ? buildQuestionUnits(targetPart.items, attemptId) : [];
                const targetItemId = targetUnits[0]?.leafItems[0]?.itemId ?? targetUnits[0]?.id;
                if (targetPart?.partId && targetItemId) {
                  const hadDirtyItems = (saveQueueRef.current?.getDirtyItemIds().length ?? 0) > 0;
                  saveQueueRef.current?.setPosition({ partId: targetPart.partId, itemId: targetItemId }, true);
                  if (!hadDirtyItems) {
                    void saveQueueRef.current?.flushNow();
                  }
                } else if (saveQueueRef.current && saveQueueRef.current.getDirtyItemIds().length > 0) {
                  void saveQueueRef.current.flushNow();
                }
              }}
            />
          </div>

          <div className="flex flex-wrap items-center gap-2">
            {remainingMs !== null && durationParts ? (
              <div
                role="timer"
                aria-label={
                  durationParts.hours > 0
                    ? t("timer.ariaLabel", {
                        label: timerLabel,
                        hours: durationParts.hours,
                        minutes: durationParts.minutes,
                        seconds: durationParts.seconds,
                      })
                    : t("timer.ariaLabelShort", {
                        label: timerLabel,
                        minutes: durationParts.minutes,
                        seconds: durationParts.seconds,
                      })
                }
                className={cn(
                  "inline-flex items-center gap-2 rounded-lg border px-3 py-1.5 text-label font-semibold tabular-nums",
                  isTimerUrgent
                    ? "border-danger bg-danger-bg text-danger"
                    : "border-border bg-subtle text-text",
                )}
              >
                <Clock className="size-4 shrink-0" aria-hidden="true" />
                <span>{formatClockDuration(remainingMs)}</span>
              </div>
            ) : null}

            <div
              role="status"
              aria-live="polite"
              className={cn(
                "rounded-lg border px-2.5 py-1 text-caption font-medium",
                saveStatus.state === "saved"
                  ? "border-border bg-subtle text-text-muted"
                  : saveStatus.state === "saving"
                    ? "border-primary-200 bg-primary-50 text-primary-800"
                    : "border-warning bg-warning-bg text-warning",
              )}
            >
              {saveStatusText}
            </div>

            {canFinishPart ? (
              <Button
                type="button"
                variant="secondary"
                size="sm"
                className="pointer-coarse:min-h-11"
                disabled={isReadOnly || isSessionClaimPending}
                onClick={() => {
                  setFinishDialogStatus("idle");
                  setFinishDialogOpen(true);
                }}
              >
                {t("header.finishPart")}
              </Button>
            ) : null}

            {canSubmitAttempt ? (
              <Button
                type="button"
                variant="primary"
                size="sm"
                className="pointer-coarse:min-h-11"
                disabled={
                  isSecondaryTab ||
                  isEvicted ||
                  isSessionClaimPending ||
                  sessionSnap.status === "takeover_required"
                }
                onClick={() => {
                  setSubmitDialogStatus("idle");
                  setSubmitDialogOpen(true);
                }}
              >
                {t("header.submit")}
              </Button>
            ) : null}
          </div>
        </div>

        {!saveStatus.isOnline ? (
          <div
            role="status"
            className="flex items-center gap-2 border-t border-warning bg-warning-bg px-4 py-2 text-caption text-warning"
          >
            <CloudOff className="size-4 shrink-0" aria-hidden="true" />
            <span>{t("player.offlineBanner")}</span>
          </div>
        ) : null}

        {isSecondaryTab ? (
          <ReadonlyTabBanner
            onSwitchHere={() => {
              tabLockRef.current?.claimActiveTab();
              tabRoleRef.current = "primary_owner";
              setTabRole("primary_owner");
              editSessionRef.current?.setReadonlyTab(false);
            }}
          />
        ) : null}
      </header>

      <main className="mx-auto flex w-full max-w-7xl flex-1 flex-col gap-6 p-4 lg:p-6">
        {activePart && isChoiceOrGeneratingState(activePart.state) ? (
          <PartChoiceView
            part={activePart}
            afterStartChoice={activeChoiceSpec}
            locale={locale}
            serverClock={serverClock ?? createServerClockAnchor(attemptState.serverNow)}
            errorCode={choiceErrorCode}
            busyRetryAfterSec={choiceBusySec}
            isSubmitting={choiceSubmitting}
            isRetrying={choiceRetrying}
            readOnly={isSessionLocked}
            onConfirmChoice={handleConfirmPartChoice}
            onRetryGeneration={handleRetryGeneration}
          />
        ) : activeUnit ? (
          <QuestionNav
            items={navItems}
            currentUnitIndex={safeUnitIndex}
            totalUnits={units.length}
            currentDisplayNumber={activeUnit.displayStart}
            totalDisplayItems={navItems.length}
            onSelectUnit={goToUnit}
            onPrev={() => goToUnit(safeUnitIndex - 1)}
            onNext={() => goToUnit(safeUnitIndex + 1)}
            canPrev={safeUnitIndex > 0}
            canNext={safeUnitIndex < units.length - 1}
            footer={<p className="text-caption text-text-muted">{t("player.shortcutHint")}</p>}
          >
            <div className="rounded-2xl border border-border bg-surface p-5 shadow-xs sm:p-6">
              {activeUnit.attachments.length > 0 ? (
                <div
                  aria-label={t("player.attachmentsLabel")}
                  className="mb-4 space-y-2 rounded-xl border border-border bg-subtle p-3.5"
                >
                  {activeUnit.attachments.map((att, idx) => {
                    const safeUrl = att.url;
                    if (!safeUrl) return null;
                    if (att.mime?.startsWith("audio/")) {
                      return (
                        <audio
                          key={att.assetId ?? idx}
                          controls
                          preload="none"
                          src={safeUrl}
                          className="w-full"
                        />
                      );
                    }
                    return (
                      <a
                        key={att.assetId ?? idx}
                        href={safeUrl}
                        target="_blank"
                        rel="noopener noreferrer"
                        className={cn(
                          "inline-flex items-center gap-2 text-caption font-medium text-primary-text hover:underline",
                          focusRing,
                        )}
                      >
                        <FileText className="size-4 shrink-0" aria-hidden="true" />
                        <span>{att.fileName ?? safeUrl}</span>
                      </a>
                    );
                  })}
                </div>
              ) : null}

              <fieldset
                disabled={isReadOnly}
                aria-disabled={isReadOnly || undefined}
                data-attempt-question-fieldset=""
                className="min-w-0"
              >
                {activeUnit.kind === "QUESTION_SET" ? (
                  <Question
                    mode="answer"
                    question={activeUnit.kitQuestion}
                    value={buildQuestionSetKitValue(activeUnit.childItems, getAnswer)}
                    flaggedIds={Object.fromEntries(
                      activeUnit.childItems
                        .map((child) => child.itemId)
                        .filter((id): id is string => Boolean(id))
                        .map((id) => [id, getFlagged(id)]),
                    )}
                    onToggleChildFlag={toggleItemFlag}
                    renderEditor={
                      activeQuestionHasEssay
                        ? (props) => <LazyEssayEditor {...props} readOnly={isReadOnly || props.readOnly} />
                        : undefined
                    }
                    onChange={(nextValue) => {
                      if (!nextValue || typeof nextValue !== "object" || !("children" in nextValue)) {
                        return;
                      }
                      const childMap = nextValue.children ?? {};
                      for (const child of activeUnit.childItems) {
                        const cid = child.itemId;
                        const cType = child.questionType;
                        if (!cid || !cType || cType === "QUESTION_SET") continue;
                        const nextKitChild = childMap[cid];
                        const nextApiAns = toApiAnswer(cType, nextKitChild);
                        const prevApiAns = getAnswer(cid);
                        if (JSON.stringify(prevApiAns) !== JSON.stringify(nextApiAns)) {
                          recordAnswerUpdate(cid, cType, nextApiAns);
                        }
                      }
                    }}
                  >
                    {activeUnit.childKitQuestions}
                  </Question>
                ) : (
                  <Question
                    mode="answer"
                    question={activeUnit.kitQuestion}
                    value={toKitAnswer(getAnswer(activeUnit.id))}
                    flagged={getFlagged(activeUnit.id)}
                    onToggleFlag={() => toggleItemFlag(activeUnit.id)}
                    renderEditor={
                      activeQuestionHasEssay
                        ? (props) => <LazyEssayEditor {...props} readOnly={isReadOnly || props.readOnly} />
                        : undefined
                    }
                    onChange={(nextValue) => {
                      const qType = activeUnit.parentItem.questionType;
                      if (!qType || qType === "QUESTION_SET") return;
                      recordAnswerUpdate(activeUnit.id, qType, toApiAnswer(qType, nextValue));
                    }}
                  />
                )}
              </fieldset>
            </div>
          </QuestionNav>
        ) : (
          <div className="rounded-2xl border border-border bg-surface p-8 text-center text-body text-text-muted">
            {t("player.noActiveQuestion")}
          </div>
        )}
      </main>

      <TakeoverDialog
        open={sessionSnap.status === "takeover_required"}
        examTitle={attemptState.examName}
        lastSeenAt={sessionSnap.lastSeenAt}
        isSubmitting={takeoverPending}
        onCancel={() => {
          resolvedDepsRef.current.navigateFn("/home");
        }}
        onConfirmTakeover={async () => {
          setTakeoverPending(true);
          try {
            const snap = await editSessionRef.current?.claim(true);
            if (snap?.status === "active") {
              await reloadServerState();
            }
          } finally {
            setTakeoverPending(false);
          }
        }}
      />

      <FinishPartDialog
        open={finishDialogOpen && sessionSnap.status !== "takeover_required"}
        onOpenChange={setFinishDialogOpen}
        partName={
          activePart
            ? choiceTitleMap[activePart.ord ?? 0]?.name ?? activePart.partName ?? activePart.partCode
            : undefined
        }
        missingCount={finishMissingCount}
        partialCount={finishPartialCount}
        totalCount={activePartLeaves.length}
        nextPartState={findNextPartState(parts, activePart?.ord ?? null)}
        status={finishDialogStatus}
        onConfirm={handleFinishPart}
        onReconcile={handleReconcileFinishPart}
      />

      <SubmitConfirmDialog
        open={submitDialogOpen && sessionSnap.status !== "takeover_required"}
        onOpenChange={setSubmitDialogOpen}
        answeredCount={totalAnswered}
        partialCount={totalPartial}
        blankCount={totalBlank}
        flaggedCount={totalFlagged}
        totalCount={allLeafItems.length}
        partBreakdown={partBreakdown}
        essayWarnings={essayWarnings}
        essayStatus={hasAnyEssay ? { wordCount: totalEssayWords, unsavedWords: hasUnsavedEssay } : null}
        status={submitDialogStatus}
        onConfirmSubmit={handleConfirmSubmit}
        onReconcile={handleReconcileSubmit}
      />
    </div>
  );
}

export function AttemptPlayerPageClient({
  attemptId,
  loadSessionFn = loadSession,
}: {
  attemptId: string;
  loadSessionFn?: SessionLoader;
}) {
  const t = useTranslations("attempt");

  const sessionQuery = useQuery({
    queryKey: SESSION_QUERY_KEY,
    queryFn: () => loadSessionFn(),
    retry: false,
  });

  const sessionErrorStatus =
    sessionQuery.error instanceof ApiStatusError ? sessionQuery.error.status : null;
  const isUnauthorized = sessionQuery.isError && sessionErrorStatus === 401;

  useEffect(() => {
    if (sessionQuery.isSuccess && !sessionQuery.data) {
      const currentHref =
        typeof window !== "undefined"
          ? window.location.href
          : `${webUrl()}/attempts/${encodeURIComponent(attemptId)}`;
      defaultNavigate(buildLoginUrl(webUrl(), currentHref));
    }
  }, [attemptId, sessionQuery.data, sessionQuery.isSuccess]);

  if (sessionQuery.isError && !isUnauthorized) {
    return (
      <main className="flex min-h-dvh items-center justify-center bg-page p-6">
        <section className="flex max-w-md flex-col items-center gap-4 rounded-2xl border border-border bg-surface p-8 text-center shadow-sm">
          <AlertCircle className="size-10 text-danger" aria-hidden="true" />
          <h1 className="text-h3 font-bold text-text">{t("errors.loadTitle")}</h1>
          <p className="text-body text-text-muted">{t("errors.loadDescription")}</p>
          <div className="flex flex-wrap justify-center gap-3 pt-2">
            <Button type="button" variant="primary" onClick={() => void sessionQuery.refetch()}>
              <RotateCw className="size-4" aria-hidden="true" />
              <span>{t("errors.retry")}</span>
            </Button>
            <Link
              href="/home"
              className={cn(
                "inline-flex h-10 items-center justify-center rounded-lg border border-border bg-surface px-4 text-label font-medium text-text-secondary hover:bg-subtle",
                focusRing,
              )}
            >
              {t("errors.loadBackToMyExams")}
            </Link>
          </div>
        </section>
      </main>
    );
  }

  if (sessionQuery.isLoading || isUnauthorized || !sessionQuery.data) {
    return (
      <main className="flex min-h-dvh items-center justify-center bg-page p-6">
        <div role="status" className="flex items-center gap-3 text-body text-text-muted">
          <LoaderCircle className="size-5 animate-spin motion-reduce:animate-none text-primary-text" aria-hidden="true" />
          <span>{t("waiting.title")}</span>
        </div>
      </main>
    );
  }

  return <AttemptPlayer attemptId={attemptId} userId={sessionQuery.data.id} />;
}
