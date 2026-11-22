import {
  EDIT_SESSION_KEY_PREFIX,
  safeStorageGet,
  safeStorageRemove,
  safeStorageSet,
} from "./session-storage";

export function makeEditSessionStorageKey(userId: string, attemptId: string): string {
  return `${EDIT_SESSION_KEY_PREFIX}${userId}:${attemptId}`;
}

export function readStoredEditSessionId(userId: string, attemptId: string): string | null {
  if (!userId || !attemptId) return null;
  const raw = safeStorageGet("local", makeEditSessionStorageKey(userId, attemptId));
  const trimmed = raw?.trim() ?? "";
  return trimmed.length > 0 ? trimmed : null;
}

export function writeStoredEditSessionId(userId: string, attemptId: string, sessionId: string): void {
  if (!userId || !attemptId || !sessionId.trim()) return;
  safeStorageSet("local", makeEditSessionStorageKey(userId, attemptId), sessionId.trim());
}

export function clearStoredEditSessionId(userId: string, attemptId: string): void {
  if (!userId || !attemptId) return;
  safeStorageRemove("local", makeEditSessionStorageKey(userId, attemptId));
}
