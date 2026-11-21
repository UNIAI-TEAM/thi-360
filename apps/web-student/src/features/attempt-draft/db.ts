import type { components } from "@thi360/api-client";

export const DRAFT_DB_NAME = "thi360-attempt-drafts";
export const DRAFT_DB_VERSION = 1;
export const ITEM_DRAFTS_STORE = "itemDrafts";
export const ATTEMPT_META_STORE = "attemptMeta";

export type AttemptAnswerValue = NonNullable<components["schemas"]["AnswerChange"]["answer"]>;

export interface AttemptPreChoiceSnapshot {
  optionCode: string;
  subjectCodes?: string[];
}

export interface AttemptPositionSnapshot {
  partId?: string;
  itemId?: string;
}

export interface ItemDraftRecord {
  id: string;
  userId: string;
  attemptId: string;
  itemId: string;
  seq: number;
  syncedSeq: number;
  answer: AttemptAnswerValue | null;
  flagged: boolean;
  dirtyAnswer: boolean;
  dirtyFlag: boolean;
  updatedAt: number;
}

export interface AttemptMetaRecord {
  id: string;
  userId: string;
  attemptId: string;
  lastPosition?: AttemptPositionSnapshot;
  entitlementId?: string;
  preChoice?: AttemptPreChoiceSnapshot;
  examName?: string;
  examKindLabel?: string;
  firstPartName?: string;
  firstPartDurationMinutes?: number | null;
  updatedAt: number;
}

export function idbRequestToPromise<T>(request: IDBRequest<T>): Promise<T> {
  return new Promise((resolve, reject) => {
    request.onsuccess = () => resolve(request.result);
    request.onerror = () => reject(request.error ?? new Error("IndexedDB request failed"));
  });
}

export function idbTransactionDone(tx: IDBTransaction): Promise<void> {
  return new Promise((resolve, reject) => {
    tx.oncomplete = () => resolve();
    tx.onabort = () => reject(tx.error ?? new Error("IndexedDB transaction aborted"));
    tx.onerror = () => reject(tx.error ?? new Error("IndexedDB transaction failed"));
  });
}

function ensureObjectStores(db: IDBDatabase): void {
  if (!db.objectStoreNames.contains(ITEM_DRAFTS_STORE)) {
    const store = db.createObjectStore(ITEM_DRAFTS_STORE, { keyPath: "id" });
    store.createIndex("byUser", "userId", { unique: false });
    store.createIndex("byUserAttempt", ["userId", "attemptId"], { unique: false });
  }
  if (!db.objectStoreNames.contains(ATTEMPT_META_STORE)) {
    const metaStore = db.createObjectStore(ATTEMPT_META_STORE, { keyPath: "id" });
    metaStore.createIndex("byUser", "userId", { unique: false });
  }
}

export function openDraftDatabase(): Promise<IDBDatabase | null> {
  if (typeof indexedDB === "undefined") {
    return Promise.resolve(null);
  }
  return new Promise((resolve) => {
    try {
      const request = indexedDB.open(DRAFT_DB_NAME, DRAFT_DB_VERSION);
      request.onupgradeneeded = () => {
        ensureObjectStores(request.result);
      };
      request.onsuccess = () => {
        const db = request.result;
        db.onversionchange = () => {
          db.close();
        };
        resolve(db);
      };
      request.onerror = () => resolve(null);
      request.onblocked = () => resolve(null);
    } catch {
      resolve(null);
    }
  });
}
