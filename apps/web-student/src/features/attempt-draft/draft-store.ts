import {
  ATTEMPT_META_STORE,
  idbRequestToPromise,
  idbTransactionDone,
  ITEM_DRAFTS_STORE,
  openDraftDatabase,
  type AttemptMetaRecord,
  type ItemDraftRecord,
} from "./db";
import {
  ATTEMPT_META_KEY_PREFIX,
  ATTEMPT_START_KEY_PREFIX,
  clearStorageByPrefixes,
  EDIT_SESSION_KEY_PREFIX,
  safeStorageRemove,
} from "./session-storage";

export type ItemDraftInput = Omit<ItemDraftRecord, "id" | "updatedAt"> & { updatedAt?: number };
export type AttemptMetaInput = Omit<AttemptMetaRecord, "id" | "updatedAt"> & { updatedAt?: number };

export function makeItemDraftId(userId: string, attemptId: string, itemId: string): string {
  return `${userId}:${attemptId}:${itemId}`;
}

export function makeAttemptMetaId(userId: string, attemptId: string): string {
  return `${userId}:${attemptId}`;
}

async function withDb<T>(fallback: T, fn: (db: IDBDatabase) => Promise<T>): Promise<T> {
  const db = await openDraftDatabase();
  if (!db) return fallback;
  try {
    return await fn(db);
  } catch {
    return fallback;
  } finally {
    db.close();
  }
}

export async function loadAttemptDrafts(userId: string, attemptId: string): Promise<ItemDraftRecord[]> {
  if (!userId || !attemptId) return [];
  return withDb<ItemDraftRecord[]>([], async (db) => {
    const tx = db.transaction(ITEM_DRAFTS_STORE, "readonly");
    const store = tx.objectStore(ITEM_DRAFTS_STORE);
    const index = store.index("byUserAttempt");
    const rows = (await idbRequestToPromise(
      index.getAll(IDBKeyRange.only([userId, attemptId])),
    )) as ItemDraftRecord[];
    await idbTransactionDone(tx);
    // Strict defense-in-depth account and attempt isolation (AC-24, AC-25).
    return rows.filter((row) => row.userId === userId && row.attemptId === attemptId);
  });
}

export async function saveItemDraft(draft: ItemDraftInput): Promise<void> {
  if (!draft.userId || !draft.attemptId || !draft.itemId) return;
  await saveItemDrafts([draft]);
}

export async function saveItemDrafts(drafts: ItemDraftInput[]): Promise<void> {
  const valid = drafts.filter((d) => Boolean(d.userId && d.attemptId && d.itemId));
  if (valid.length === 0) return;
  await withDb<void>(undefined, async (db) => {
    const tx = db.transaction(ITEM_DRAFTS_STORE, "readwrite");
    const store = tx.objectStore(ITEM_DRAFTS_STORE);
    const now = Date.now();
    for (const draft of valid) {
      const record: ItemDraftRecord = {
        ...draft,
        id: makeItemDraftId(draft.userId, draft.attemptId, draft.itemId),
        updatedAt: draft.updatedAt ?? now,
      };
      store.put(record);
    }
    await idbTransactionDone(tx);
  });
}

export async function deleteItemDraft(userId: string, attemptId: string, itemId: string): Promise<void> {
  if (!userId || !attemptId || !itemId) return;
  await withDb<void>(undefined, async (db) => {
    const tx = db.transaction(ITEM_DRAFTS_STORE, "readwrite");
    tx.objectStore(ITEM_DRAFTS_STORE).delete(makeItemDraftId(userId, attemptId, itemId));
    await idbTransactionDone(tx);
  });
}

export async function loadAttemptMeta(userId: string, attemptId: string): Promise<AttemptMetaRecord | null> {
  if (!userId || !attemptId) return null;
  return withDb<AttemptMetaRecord | null>(null, async (db) => {
    const tx = db.transaction(ATTEMPT_META_STORE, "readonly");
    const record = (await idbRequestToPromise(
      tx.objectStore(ATTEMPT_META_STORE).get(makeAttemptMetaId(userId, attemptId)),
    )) as AttemptMetaRecord | undefined;
    await idbTransactionDone(tx);
    if (!record || record.userId !== userId || record.attemptId !== attemptId) {
      return null;
    }
    return record;
  });
}

export async function saveAttemptMeta(meta: AttemptMetaInput): Promise<void> {
  if (!meta.userId || !meta.attemptId) return;
  await withDb<void>(undefined, async (db) => {
    const id = makeAttemptMetaId(meta.userId, meta.attemptId);
    const tx = db.transaction(ATTEMPT_META_STORE, "readwrite");
    const store = tx.objectStore(ATTEMPT_META_STORE);
    const existing = (await idbRequestToPromise(store.get(id))) as AttemptMetaRecord | undefined;
    const validExisting =
      existing && existing.userId === meta.userId && existing.attemptId === meta.attemptId
        ? existing
        : undefined;
    const merged: AttemptMetaRecord = {
      ...validExisting,
      ...meta,
      id,
      userId: meta.userId,
      attemptId: meta.attemptId,
      updatedAt: meta.updatedAt ?? Date.now(),
    };
    store.put(merged);
    await idbTransactionDone(tx);
  });
}

export async function clearAttemptDraftsForAttempt(userId: string, attemptId: string): Promise<void> {
  if (!userId || !attemptId) return;
  safeStorageRemove("local", `${EDIT_SESSION_KEY_PREFIX}${userId}:${attemptId}`);
  safeStorageRemove("session", `${ATTEMPT_START_KEY_PREFIX}${userId}:${attemptId}`);
  safeStorageRemove("local", `${ATTEMPT_META_KEY_PREFIX}${userId}:${attemptId}`);
  safeStorageRemove("session", `${ATTEMPT_META_KEY_PREFIX}${userId}:${attemptId}`);

  await withDb<void>(undefined, async (db) => {
    const tx = db.transaction([ITEM_DRAFTS_STORE, ATTEMPT_META_STORE], "readwrite");
    const itemStore = tx.objectStore(ITEM_DRAFTS_STORE);
    const keys = (await idbRequestToPromise(
      itemStore.index("byUserAttempt").getAllKeys(IDBKeyRange.only([userId, attemptId])),
    )) as IDBValidKey[];
    const prefix = `${userId}:${attemptId}:`;
    for (const key of keys) {
      if (typeof key === "string" && key.startsWith(prefix)) {
        itemStore.delete(key);
      }
    }
    tx.objectStore(ATTEMPT_META_STORE).delete(makeAttemptMetaId(userId, attemptId));
    await idbTransactionDone(tx);
  });
}

export async function clearUserAttemptDrafts(userId: string): Promise<void> {
  if (!userId) return;
  clearStorageByPrefixes([
    `${EDIT_SESSION_KEY_PREFIX}${userId}:`,
    `${ATTEMPT_META_KEY_PREFIX}${userId}:`,
    `${ATTEMPT_START_KEY_PREFIX}${userId}:`,
  ]);

  await withDb<void>(undefined, async (db) => {
    const tx = db.transaction([ITEM_DRAFTS_STORE, ATTEMPT_META_STORE], "readwrite");
    const itemStore = tx.objectStore(ITEM_DRAFTS_STORE);
    const metaStore = tx.objectStore(ATTEMPT_META_STORE);

    const [itemKeys, metaKeys] = (await Promise.all([
      idbRequestToPromise(itemStore.index("byUser").getAllKeys(IDBKeyRange.only(userId))),
      idbRequestToPromise(metaStore.index("byUser").getAllKeys(IDBKeyRange.only(userId))),
    ])) as [IDBValidKey[], IDBValidKey[]];

    const prefix = `${userId}:`;
    for (const key of itemKeys) {
      if (typeof key === "string" && key.startsWith(prefix)) {
        itemStore.delete(key);
      }
    }
    for (const key of metaKeys) {
      if (typeof key === "string" && key.startsWith(prefix)) {
        metaStore.delete(key);
      }
    }
    await idbTransactionDone(tx);
  });
}

export async function clearAllAttemptDrafts(): Promise<void> {
  clearStorageByPrefixes([EDIT_SESSION_KEY_PREFIX, ATTEMPT_META_KEY_PREFIX, ATTEMPT_START_KEY_PREFIX]);
  await withDb<void>(undefined, async (db) => {
    const tx = db.transaction([ITEM_DRAFTS_STORE, ATTEMPT_META_STORE], "readwrite");
    tx.objectStore(ITEM_DRAFTS_STORE).clear();
    tx.objectStore(ATTEMPT_META_STORE).clear();
    await idbTransactionDone(tx);
  });
}
