import {
  readStoredEditSessionId,
  writeStoredEditSessionId,
} from "@/features/attempt-draft";
import { ApiStatusError } from "@/lib/api/client";
import {
  type ClaimEditSessionResult,
  claimEditSession,
  type ClaimSessionRequest,
  type HeartbeatResponse,
  sendHeartbeat,
} from "./api";

export const HEARTBEAT_INTERVAL_MS = 30_000;
export const TAB_LOCK_ACQUIRE_TIMEOUT_MS = 120;

export type TabLockRole = "acquiring" | "primary_owner" | "secondary_readonly";

export interface TabLockChannel {
  postMessage(message: unknown): void;
  close(): void;
  onmessage: ((event: MessageEvent<unknown>) => void) | null;
}

type TabLockMessage =
  | { type: "PING_OWNER"; tabId: string; startedAt: number }
  | { type: "OWNER_ALIVE"; tabId: string; targetTabId?: string }
  | { type: "OWNER_CLAIMED"; tabId: string }
  | { type: "OWNER_RELEASED"; tabId: string }
  | { type: "FORCE_CLAIM"; tabId: string };

function isTabLockMessage(raw: unknown): raw is TabLockMessage {
  if (typeof raw !== "object" || raw === null) return false;
  const record = raw as Record<string, unknown>;
  return typeof record.type === "string" && typeof record.tabId === "string";
}

export interface AttemptTabLockOptions {
  userId: string;
  attemptId: string;
  tabId?: string;
  acquireTimeoutMs?: number;
  createChannel?: (name: string) => TabLockChannel | null;
  onRoleChange?: (role: TabLockRole) => void;
}

export class AttemptTabLock {
  readonly channelName: string;
  readonly tabId: string;
  private readonly acquireTimeoutMs: number;
  private readonly createChannelFn: (name: string) => TabLockChannel | null;
  private readonly onRoleChange?: (role: TabLockRole) => void;

  private channel: TabLockChannel | null = null;
  private role: TabLockRole = "acquiring";
  private acquireTimer: ReturnType<typeof setTimeout> | null = null;
  private startedAt = Date.now();
  private destroyed = false;
  private unloadHandler: (() => void) | null = null;

  constructor(options: AttemptTabLockOptions) {
    this.channelName = `thi360-attempt-tab:${options.userId}:${options.attemptId}`;
    this.tabId =
      options.tabId ??
      (typeof crypto !== "undefined" && typeof crypto.randomUUID === "function"
        ? crypto.randomUUID()
        : `tab-${Math.random().toString(36).slice(2)}`);
    this.acquireTimeoutMs = options.acquireTimeoutMs ?? TAB_LOCK_ACQUIRE_TIMEOUT_MS;
    this.createChannelFn =
      options.createChannel ??
      ((name) => (typeof BroadcastChannel !== "undefined" ? new BroadcastChannel(name) : null));
    this.onRoleChange = options.onRoleChange;
  }

  getRole(): TabLockRole {
    return this.role;
  }

  start(): TabLockRole {
    if (this.destroyed) return this.role;
    this.channel = this.createChannelFn(this.channelName);
    if (!this.channel) {
      this.setRole("primary_owner");
      return this.role;
    }

    this.channel.onmessage = (event) => {
      this.handleMessage(event.data);
    };

    if (typeof window !== "undefined") {
      this.unloadHandler = () => {
        this.release();
      };
      window.addEventListener("pagehide", this.unloadHandler);
    }

    this.beginAcquire();
    return this.role;
  }

  claimActiveTab(): void {
    if (this.destroyed) return;
    this.clearAcquireTimer();
    this.channel?.postMessage({ type: "FORCE_CLAIM", tabId: this.tabId } satisfies TabLockMessage);
    this.setRole("primary_owner");
    this.channel?.postMessage({ type: "OWNER_CLAIMED", tabId: this.tabId } satisfies TabLockMessage);
  }

  release(): void {
    this.clearAcquireTimer();
    if (this.role === "primary_owner") {
      this.channel?.postMessage({
        type: "OWNER_RELEASED",
        tabId: this.tabId,
      } satisfies TabLockMessage);
      this.setRole("secondary_readonly");
    }
  }

  destroy(): void {
    if (this.destroyed) return;
    this.release();
    this.destroyed = true;
    if (typeof window !== "undefined" && this.unloadHandler) {
      window.removeEventListener("pagehide", this.unloadHandler);
      this.unloadHandler = null;
    }
    if (this.channel) {
      this.channel.onmessage = null;
      this.channel.close();
      this.channel = null;
    }
  }

  private beginAcquire(): void {
    if (this.destroyed) return;
    this.clearAcquireTimer();
    this.startedAt = Date.now();
    this.setRole("acquiring");
    this.channel?.postMessage({
      type: "PING_OWNER",
      tabId: this.tabId,
      startedAt: this.startedAt,
    } satisfies TabLockMessage);

    this.acquireTimer = setTimeout(() => {
      this.acquireTimer = null;
      if (this.destroyed || this.role !== "acquiring") return;
      this.setRole("primary_owner");
      this.channel?.postMessage({
        type: "OWNER_CLAIMED",
        tabId: this.tabId,
      } satisfies TabLockMessage);
    }, this.acquireTimeoutMs);
  }

  private handleMessage(raw: unknown): void {
    if (this.destroyed || !isTabLockMessage(raw)) return;
    if (raw.tabId === this.tabId) return;

    switch (raw.type) {
      case "PING_OWNER": {
        if (this.role === "primary_owner") {
          this.channel?.postMessage({
            type: "OWNER_ALIVE",
            tabId: this.tabId,
            targetTabId: raw.tabId,
          } satisfies TabLockMessage);
        } else if (this.role === "acquiring") {
          const otherWins =
            raw.startedAt < this.startedAt ||
            (raw.startedAt === this.startedAt && raw.tabId < this.tabId);
          if (otherWins) {
            this.clearAcquireTimer();
            this.setRole("secondary_readonly");
          } else {
            this.channel?.postMessage({
              type: "OWNER_ALIVE",
              tabId: this.tabId,
              targetTabId: raw.tabId,
            } satisfies TabLockMessage);
          }
        }
        break;
      }
      case "OWNER_ALIVE":
      case "OWNER_CLAIMED": {
        this.clearAcquireTimer();
        this.setRole("secondary_readonly");
        break;
      }
      case "FORCE_CLAIM": {
        this.clearAcquireTimer();
        if (this.role !== "secondary_readonly") {
          this.setRole("secondary_readonly");
        }
        break;
      }
      case "OWNER_RELEASED": {
        if (this.role === "secondary_readonly") {
          this.beginAcquire();
        }
        break;
      }
    }
  }

  private setRole(next: TabLockRole): void {
    if (this.role === next) return;
    this.role = next;
    this.onRoleChange?.(next);
  }

  private clearAcquireTimer(): void {
    if (this.acquireTimer) {
      clearTimeout(this.acquireTimer);
      this.acquireTimer = null;
    }
  }
}

export type EditSessionStatus =
  | "idle"
  | "claiming"
  | "active"
  | "takeover_required"
  | "evicted"
  | "readonly_tab"
  | "error";

export interface EditSessionSnapshot {
  status: EditSessionStatus;
  sessionId: string | null;
  lastSeenAt?: string;
}

export interface EditSessionControllerOptions {
  attemptId: string;
  userId: string;
  heartbeatIntervalMs?: number;
  claimSessionFn?: (
    attemptId: string,
    body: ClaimSessionRequest,
  ) => Promise<ClaimEditSessionResult>;
  heartbeatFn?: (attemptId: string, sessionId: string) => Promise<HeartbeatResponse>;
  readSessionIdFn?: (userId: string, attemptId: string) => string | null;
  writeSessionIdFn?: (userId: string, attemptId: string, sessionId: string) => void;
  onStatusChange?: (snapshot: EditSessionSnapshot) => void;
  onServerNow?: (serverNowIso: string) => void;
  onAttemptClosed?: (stateOrReason: string) => void;
  onUnauthorized?: () => void;
}

export class EditSessionController {
  private readonly attemptId: string;
  private readonly userId: string;
  private readonly heartbeatIntervalMs: number;
  private readonly claimSessionFn: (
    attemptId: string,
    body: ClaimSessionRequest,
  ) => Promise<ClaimEditSessionResult>;
  private readonly heartbeatFn: (
    attemptId: string,
    sessionId: string,
  ) => Promise<HeartbeatResponse>;
  private readonly readSessionIdFn: (userId: string, attemptId: string) => string | null;
  private readonly writeSessionIdFn: (
    userId: string,
    attemptId: string,
    sessionId: string,
  ) => void;
  private readonly onStatusChange?: (snapshot: EditSessionSnapshot) => void;
  private readonly onServerNow?: (serverNowIso: string) => void;
  private readonly onAttemptClosed?: (stateOrReason: string) => void;
  private readonly onUnauthorized?: () => void;

  private status: EditSessionStatus = "idle";
  private sessionId: string | null = null;
  private lastSeenAt: string | undefined;
  private heartbeatTimer: ReturnType<typeof setInterval> | null = null;
  private pausedForReadonly = false;
  private destroyed = false;

  constructor(options: EditSessionControllerOptions) {
    this.attemptId = options.attemptId;
    this.userId = options.userId;
    this.heartbeatIntervalMs = options.heartbeatIntervalMs ?? HEARTBEAT_INTERVAL_MS;
    this.claimSessionFn = options.claimSessionFn ?? claimEditSession;
    this.heartbeatFn = options.heartbeatFn ?? sendHeartbeat;
    this.readSessionIdFn = options.readSessionIdFn ?? readStoredEditSessionId;
    this.writeSessionIdFn = options.writeSessionIdFn ?? writeStoredEditSessionId;
    this.onStatusChange = options.onStatusChange;
    this.onServerNow = options.onServerNow;
    this.onAttemptClosed = options.onAttemptClosed;
    this.onUnauthorized = options.onUnauthorized;
  }

  getSnapshot(): EditSessionSnapshot {
    return {
      status: this.status,
      sessionId: this.sessionId,
      lastSeenAt: this.lastSeenAt,
    };
  }

  getSessionId(): string | null {
    return this.sessionId;
  }

  async claim(takeover = false): Promise<EditSessionSnapshot> {
    if (this.destroyed || this.pausedForReadonly) {
      return this.getSnapshot();
    }

    this.stopHeartbeat();
    this.setStatus("claiming");

    const storedId = this.sessionId ?? this.readSessionIdFn(this.userId, this.attemptId);

    try {
      const result = await this.claimSessionFn(this.attemptId, {
        ...(storedId ? { sessionId: storedId } : {}),
        ...(takeover ? { takeover: true } : {}),
      });
      if (this.destroyed) return this.getSnapshot();

      if (result.conflict) {
        this.lastSeenAt = result.lastSeenAt;
        this.setStatus("takeover_required");
        return this.getSnapshot();
      }

      this.sessionId = result.sessionId;
      this.lastSeenAt = undefined;
      this.writeSessionIdFn(this.userId, this.attemptId, result.sessionId);
      this.onServerNow?.(result.serverNow);

      if (this.pausedForReadonly) {
        this.setStatus("readonly_tab");
        return this.getSnapshot();
      }

      this.setStatus("active");
      this.startHeartbeat();
      return this.getSnapshot();
    } catch (error) {
      if (this.destroyed) return this.getSnapshot();
      if (error instanceof ApiStatusError && error.status === 401) {
        this.stopHeartbeat();
        this.setStatus("error");
        this.onUnauthorized?.();
        return this.getSnapshot();
      }
      if (
        error instanceof ApiStatusError &&
        error.status === 409 &&
        (error.code === "TIME_UP" || error.code === "ATTEMPT_NOT_OPEN")
      ) {
        this.stopHeartbeat();
        this.onAttemptClosed?.(error.code);
        return this.getSnapshot();
      }
      this.setStatus("error");
      return this.getSnapshot();
    }
  }

  async sendHeartbeatNow(): Promise<void> {
    if (this.destroyed || this.pausedForReadonly || this.status !== "active" || !this.sessionId) {
      return;
    }

    try {
      const response = await this.heartbeatFn(this.attemptId, this.sessionId);
      if (this.destroyed || this.pausedForReadonly || this.status !== "active") return;
      this.onServerNow?.(response.serverNow);
      if (response.state !== "IN_PROGRESS") {
        this.stopHeartbeat();
        this.onAttemptClosed?.(response.state);
      }
    } catch (error) {
      if (this.destroyed) return;
      if (error instanceof ApiStatusError && error.status === 401) {
        this.stopHeartbeat();
        this.onUnauthorized?.();
        return;
      }
      if (error instanceof ApiStatusError && error.status === 409) {
        if (error.code === "ATTEMPT_SESSION_REVOKED" || error.code === "ATTEMPT_OPEN_ELSEWHERE") {
          this.markEvicted();
        } else if (error.code === "TIME_UP" || error.code === "ATTEMPT_NOT_OPEN") {
          this.stopHeartbeat();
          this.onAttemptClosed?.(error.code);
        }
      }
    }
  }

  async resumeAfterAuthRefresh(): Promise<void> {
    if (this.destroyed || this.pausedForReadonly) {
      return;
    }
    if (this.status === "error" || this.status === "idle" || this.status === "claiming") {
      await this.claim(false);
    } else if (this.status === "active" && this.sessionId) {
      this.startHeartbeat();
      await this.sendHeartbeatNow();
    }
  }

  markEvicted(): void {
    this.stopHeartbeat();
    this.setStatus("evicted");
  }

  setReadonlyTab(readonly: boolean): void {
    if (this.destroyed) return;
    if (readonly) {
      this.pausedForReadonly = true;
      this.stopHeartbeat();
      this.setStatus("readonly_tab");
    } else if (this.pausedForReadonly || this.status === "readonly_tab") {
      this.pausedForReadonly = false;
      void this.claim(false);
    }
  }

  destroy(): void {
    this.destroyed = true;
    this.stopHeartbeat();
  }

  private startHeartbeat(): void {
    this.stopHeartbeat();
    this.heartbeatTimer = setInterval(() => {
      void this.sendHeartbeatNow();
    }, this.heartbeatIntervalMs);
  }

  private stopHeartbeat(): void {
    if (this.heartbeatTimer) {
      clearInterval(this.heartbeatTimer);
      this.heartbeatTimer = null;
    }
  }

  private setStatus(next: EditSessionStatus): void {
    if (this.status === next && next !== "takeover_required") return;
    this.status = next;
    this.onStatusChange?.(this.getSnapshot());
  }
}
