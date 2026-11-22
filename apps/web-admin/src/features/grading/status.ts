import type { StatusTone } from "@thi360/ui";
import type { GradingQueueState } from "./types";

/** Chip tones for anonymous grading queue states (LT §9.2, BS §11). */
export const GRADING_STATUS_TONE: Record<GradingQueueState, StatusTone> = {
  WAITING: "warning",
  IN_PROGRESS: "info",
  DONE: "success",
};

/** Route to the single-item anonymous essay grading page. */
export function gradingItemHref(itemId: string): string {
  return `/grading/${encodeURIComponent(itemId)}`;
}

/** Short anonymous attempt identifier for display (never learner name/username/email). */
export function shortAttemptCode(attemptId: string | undefined): string {
  if (!attemptId) return "---";
  return attemptId.slice(0, 8).toUpperCase();
}
