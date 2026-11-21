export const EDIT_SESSION_KEY_PREFIX = "thi360:attempt-session:";
export const ATTEMPT_META_KEY_PREFIX = "thi360:attempt-meta:";
export const ATTEMPT_START_KEY_PREFIX = "thi360:attempt-start:";

export type StorageKind = "local" | "session";

function resolveStorage(kind: StorageKind): Storage | null {
  if (typeof window === "undefined") return null;
  try {
    return kind === "local" ? window.localStorage : window.sessionStorage;
  } catch {
    return null;
  }
}

export function safeStorageGet(kind: StorageKind, key: string): string | null {
  const storage = resolveStorage(kind);
  if (!storage) return null;
  try {
    return storage.getItem(key);
  } catch {
    return null;
  }
}

export function safeStorageSet(kind: StorageKind, key: string, value: string): void {
  const storage = resolveStorage(kind);
  if (!storage) return;
  try {
    storage.setItem(key, value);
  } catch {
    // Ignore storage quota / private mode failures.
  }
}

export function safeStorageRemove(kind: StorageKind, key: string): void {
  const storage = resolveStorage(kind);
  if (!storage) return;
  try {
    storage.removeItem(key);
  } catch {
    // Ignore restricted storage failures.
  }
}

export function clearStorageByPrefixes(prefixes: readonly string[]): void {
  for (const kind of ["local", "session"] as const) {
    const storage = resolveStorage(kind);
    if (!storage) continue;
    try {
      const keysToDelete: string[] = [];
      for (let i = 0; i < storage.length; i += 1) {
        const key = storage.key(i);
        if (key && prefixes.some((prefix) => key.startsWith(prefix))) {
          keysToDelete.push(key);
        }
      }
      for (const key of keysToDelete) {
        storage.removeItem(key);
      }
    } catch {
      // Ignore storage iteration failures.
    }
  }
}

export {
  clearStoredEditSessionId,
  readStoredEditSessionId,
  writeStoredEditSessionId,
} from "./edit-session-storage";
export {
  clearAttemptStartContext,
  loadAttemptStartContext,
  readAttemptStartContext,
  saveAttemptStartContext,
  type AttemptStartContext,
} from "./start-context";
