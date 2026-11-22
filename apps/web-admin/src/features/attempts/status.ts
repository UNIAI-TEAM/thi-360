import type { StatusTone } from "@thi360/ui";
import type { AttemptEventType, AttemptPartState, AttemptState } from "./types";

export const ATTEMPT_STATE_TONE: Record<AttemptState, StatusTone> = {
  STARTING: "neutral",
  IN_PROGRESS: "info",
  GRADING: "info",
  PENDING_REVIEW: "warning",
  SCORED: "success",
  GRADING_ERROR: "danger",
  ABORTED: "neutral",
};

export const ATTEMPT_PART_STATE_TONE: Record<AttemptPartState, StatusTone> = {
  PENDING: "neutral",
  AWAITING_CHOICE: "warning",
  GENERATING: "info",
  GENERATION_FAILED: "danger",
  OPEN: "info",
  CLOSED: "neutral",
  SKIPPED: "neutral",
};

export const ATTEMPT_EVENT_TONE: Record<AttemptEventType, StatusTone> = {
  STARTED: "info",
  GENERATION_FAILED: "danger",
  PART_OPENED: "neutral",
  PART_CLOSED: "neutral",
  CHOICE_CONFIRMED: "success",
  SESSION_CLAIMED: "neutral",
  SESSION_TAKEN_OVER: "warning",
  SAVE_REJECTED: "warning",
  SUBMITTED: "info",
  TIME_UP: "warning",
  CHOICE_TIMEOUT: "warning",
  GRADED: "success",
  GRADING_FAILED: "danger",
  REGRADED: "success",
  COMPENSATED: "success",
};

export function shortAttemptCode(attemptId: string | undefined): string {
  if (!attemptId) return "---";
  return attemptId.slice(0, 8).toUpperCase();
}

export function shortSessionCode(sessionId: string | undefined | null): string {
  if (!sessionId) return "";
  return sessionId.slice(0, 8).toUpperCase();
}

export function attemptDetailHref(attemptId: string): string {
  return `/attempts/${encodeURIComponent(attemptId)}`;
}
