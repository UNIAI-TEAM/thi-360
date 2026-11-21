import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { clearSessionBestEffort, logoutSession } from "@/features/session/api";
import {
  clearAllAttemptDrafts,
  clearAttemptDraftsForAttempt,
  clearAttemptStartContext,
  clearStoredEditSessionId,
  clearUserAttemptDrafts,
  loadAttemptDrafts,
  loadAttemptMeta,
  loadAttemptStartContext,
  readAttemptStartContext,
  readStoredEditSessionId,
  saveAttemptMeta,
  saveAttemptStartContext,
  saveItemDraft,
  writeStoredEditSessionId,
} from "./index";

const mocks = vi.hoisted(() => ({
  post: vi.fn(),
}));

vi.mock("@/lib/api/client", async (importOriginal) => {
  const actual = await importOriginal<typeof import("@/lib/api/client")>();
  return {
    ...actual,
    createBrowserApiClient: () => ({
      POST: mocks.post,
    }),
  };
});

/** Minimal in-memory IDB mock supporting keyPath, indexes, cursors/getAll/getAllKeys, put, delete, clear. */
function createInMemoryIdbFactory() {
  const stores = new Map<string, Map<string, Record<string, unknown>>>();
  const indexes = new Map<string, Map<string, string | string[]>>();

  function extractKeyPath(record: Record<string, unknown>, keyPath: string | string[]): unknown {
    if (Array.isArray(keyPath)) {
      return keyPath.map((k) => record[k]);
    }
    return record[keyPath];
  }

  function keysMatch(a: unknown, b: unknown): boolean {
    if (Array.isArray(a) && Array.isArray(b)) {
      return a.length === b.length && a.every((item, idx) => item === b[idx]);
    }
    return a === b;
  }

  function makeReq<T>(runner: () => T): IDBRequest<T> {
    const req = {
      result: undefined as unknown as T,
      error: null as DOMException | null,
      onsuccess: null as ((ev: Event) => void) | null,
      onerror: null as ((ev: Event) => void) | null,
    };
    queueMicrotask(() => {
      try {
        req.result = runner();
        req.onsuccess?.(new Event("success"));
      } catch (err) {
        req.error = err as DOMException;
        req.onerror?.(new Event("error"));
      }
    });
    return req as unknown as IDBRequest<T>;
  }

  function makeIndex(storeName: string, indexName: string) {
    const keyPath = indexes.get(storeName)?.get(indexName) ?? "id";
    return {
      getAll: (query?: IDBKeyRange | IDBValidKey) =>
        makeReq(() => {
          const map = stores.get(storeName) ?? new Map();
          const target = query && typeof query === "object" && "lower" in query ? query.lower : query;
          return [...map.values()]
            .filter((rec) => target === undefined || keysMatch(extractKeyPath(rec, keyPath), target))
            .map((rec) => structuredClone(rec));
        }),
      getAllKeys: (query?: IDBKeyRange | IDBValidKey) =>
        makeReq(() => {
          const map = stores.get(storeName) ?? new Map();
          const target = query && typeof query === "object" && "lower" in query ? query.lower : query;
          return [...map.entries()]
            .filter(([, rec]) => target === undefined || keysMatch(extractKeyPath(rec, keyPath), target))
            .map(([k]) => k);
        }),
    };
  }

  function makeTx(storeNames: string | string[]) {
    const names = Array.isArray(storeNames) ? storeNames : [storeNames];
    const tx = {
      error: null,
      oncomplete: null as ((ev: Event) => void) | null,
      onerror: null as ((ev: Event) => void) | null,
      onabort: null as ((ev: Event) => void) | null,
      objectStore: (name: string) => {
        if (!names.includes(name)) throw new Error(`Store ${name} not in transaction`);
        if (!stores.has(name)) stores.set(name, new Map());
        const map = stores.get(name)!;
        return {
          put: (value: Record<string, unknown>) =>
            makeReq(() => {
              const id = String(value.id);
              map.set(id, structuredClone(value));
              return id;
            }),
          get: (key: string) =>
            makeReq(() => {
              const found = map.get(String(key));
              return found ? structuredClone(found) : undefined;
            }),
          delete: (key: string) =>
            makeReq(() => {
              map.delete(String(key));
              return undefined;
            }),
          clear: () =>
            makeReq(() => {
              map.clear();
              return undefined;
            }),
          index: (indexName: string) => makeIndex(name, indexName),
        };
      },
    };
    // Complete transaction after pending microtasks flush
    setTimeout(() => {
      tx.oncomplete?.(new Event("complete"));
    }, 0);
    return tx as unknown as IDBTransaction;
  }

  const db = {
    objectStoreNames: {
      contains: (name: string) => stores.has(name),
    },
    createObjectStore: (name: string) => {
      if (!stores.has(name)) stores.set(name, new Map());
      if (!indexes.has(name)) indexes.set(name, new Map());
      return {
        createIndex: (indexName: string, keyPath: string | string[]) => {
          indexes.get(name)!.set(indexName, keyPath);
        },
      };
    },
    transaction: (storeNames: string | string[]) => makeTx(storeNames),
    close: () => {},
    onversionchange: null,
  };

  return {
    open: () => {
      const req = {
        result: db,
        error: null,
        onupgradeneeded: null as ((ev: IDBVersionChangeEvent) => void) | null,
        onsuccess: null as ((ev: Event) => void) | null,
        onerror: null as ((ev: Event) => void) | null,
        onblocked: null as ((ev: Event) => void) | null,
      };
      queueMicrotask(() => {
        if (!stores.has("itemDrafts") || !stores.has("attemptMeta")) {
          req.onupgradeneeded?.(new Event("upgradeneeded") as IDBVersionChangeEvent);
        }
        req.onsuccess?.(new Event("success"));
      });
      return req;
    },
  } as unknown as IDBFactory;
}

describe("attempt-draft storage", () => {
  beforeEach(() => {
    window.localStorage.clear();
    window.sessionStorage.clear();
    mocks.post.mockReset();
    vi.stubGlobal("indexedDB", createInMemoryIdbFactory());
    vi.stubGlobal("IDBKeyRange", {
      only: (value: unknown) => ({ lower: value }),
    });
  });

  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it("cô lập bản nháp và phiên sửa giữa hai tài khoản trên cùng trình duyệt (AC-24, AC-25)", async () => {
    await saveItemDraft({
      userId: "user-1",
      attemptId: "att-1",
      itemId: "item-1",
      seq: 2,
      syncedSeq: 1,
      answer: { type: "SINGLE_CHOICE", optionId: "opt-a" },
      flagged: true,
      dirtyAnswer: true,
      dirtyFlag: false,
    });
    await saveAttemptMeta({
      userId: "user-1",
      attemptId: "att-1",
      entitlementId: "ent-1",
      preChoice: { optionCode: "CA2" },
      examName: "Đề BCA",
      examKindLabel: "BCA",
      firstPartName: "Phần 1",
      firstPartDurationMinutes: 60,
      lastPosition: { partId: "part-1", itemId: "item-1" },
    });
    writeStoredEditSessionId("user-1", "att-1", "sess-u1");

    expect(await loadAttemptDrafts("user-1", "att-1")).toHaveLength(1);
    expect(await loadAttemptDrafts("user-2", "att-1")).toEqual([]);
    expect(await loadAttemptMeta("user-1", "att-1")).toMatchObject({
      entitlementId: "ent-1",
      lastPosition: { partId: "part-1", itemId: "item-1" },
    });
    expect(await loadAttemptMeta("user-2", "att-1")).toBeNull();
    expect(readStoredEditSessionId("user-1", "att-1")).toBe("sess-u1");
    expect(readStoredEditSessionId("user-2", "att-1")).toBeNull();
  });

  it("lưu và khôi phục startContext từ sessionStorage và fallback IndexedDB", async () => {
    await saveAttemptStartContext("user-1", "att-9", {
      entitlementId: "ent-9",
      preChoice: { optionCode: "KH", subjectCodes: ["LY", "HOA", "SINH"] },
      examName: "Đề HSA FULL",
      examKind: "HSA",
      firstPartName: "Phần 1",
      firstPartDurationMinutes: 75,
    });

    expect(readAttemptStartContext("user-1", "att-9")).toMatchObject({
      entitlementId: "ent-9",
      preChoice: { optionCode: "KH", subjectCodes: ["LY", "HOA", "SINH"] },
      examName: "Đề HSA FULL",
    });

    // Simulate new tab where sessionStorage is empty, falling back to IndexedDB attemptMeta
    clearAttemptStartContext("user-1", "att-9");
    expect(readAttemptStartContext("user-1", "att-9")).toBeNull();
    expect(await loadAttemptStartContext("user-1", "att-9")).toMatchObject({
      entitlementId: "ent-9",
      preChoice: { optionCode: "KH", subjectCodes: ["LY", "HOA", "SINH"] },
      examKind: "HSA",
      firstPartDurationMinutes: 75,
    });
  });

  it("xóa bản nháp theo lượt và theo tài khoản mà không ảnh hưởng tài khoản khác", async () => {
    await saveItemDraft({
      userId: "user-1",
      attemptId: "att-1",
      itemId: "item-1",
      seq: 1,
      syncedSeq: 0,
      answer: { type: "SHORT_ANSWER", text: "42" },
      flagged: false,
      dirtyAnswer: true,
      dirtyFlag: false,
    });
    await saveItemDraft({
      userId: "user-1",
      attemptId: "att-2",
      itemId: "item-2",
      seq: 1,
      syncedSeq: 0,
      answer: { type: "SHORT_ANSWER", text: "99" },
      flagged: false,
      dirtyAnswer: true,
      dirtyFlag: false,
    });
    await saveItemDraft({
      userId: "user-2",
      attemptId: "att-3",
      itemId: "item-3",
      seq: 1,
      syncedSeq: 0,
      answer: { type: "SHORT_ANSWER", text: "7" },
      flagged: false,
      dirtyAnswer: true,
      dirtyFlag: false,
    });
    writeStoredEditSessionId("user-1", "att-1", "s1");
    writeStoredEditSessionId("user-2", "att-3", "s2");

    await clearAttemptDraftsForAttempt("user-1", "att-1");
    expect(await loadAttemptDrafts("user-1", "att-1")).toEqual([]);
    expect(readStoredEditSessionId("user-1", "att-1")).toBeNull();
    expect(await loadAttemptDrafts("user-1", "att-2")).toHaveLength(1);

    await clearUserAttemptDrafts("user-1");
    expect(await loadAttemptDrafts("user-1", "att-2")).toEqual([]);
    expect(await loadAttemptDrafts("user-2", "att-3")).toHaveLength(1);
    expect(readStoredEditSessionId("user-2", "att-3")).toBe("s2");

    clearStoredEditSessionId("user-2", "att-3");
    expect(readStoredEditSessionId("user-2", "att-3")).toBeNull();
  });

  it("không ném lỗi khi IndexedDB không khả dụng (jsdom / chế độ hạn chế)", async () => {
    vi.stubGlobal("indexedDB", undefined);

    await expect(
      saveItemDraft({
        userId: "user-1",
        attemptId: "att-1",
        itemId: "item-1",
        seq: 1,
        syncedSeq: 0,
        answer: null,
        flagged: false,
        dirtyAnswer: false,
        dirtyFlag: false,
      }),
    ).resolves.toBeUndefined();
    await expect(loadAttemptDrafts("user-1", "att-1")).resolves.toEqual([]);
    await expect(loadAttemptMeta("user-1", "att-1")).resolves.toBeNull();
    await expect(clearAllAttemptDrafts()).resolves.toBeUndefined();
  });

  it("logoutSession xóa toàn bộ bản nháp (kể cả 401), nhưng clearSessionBestEffort giữ nguyên bản nháp (AC-24, AC-25)", async () => {
    await saveItemDraft({
      userId: "user-1",
      attemptId: "att-1",
      itemId: "item-1",
      seq: 3,
      syncedSeq: 1,
      answer: { type: "SHORT_ANSWER", text: "12" },
      flagged: false,
      dirtyAnswer: true,
      dirtyFlag: false,
    });
    writeStoredEditSessionId("user-1", "att-1", "sess-1");
    await saveAttemptStartContext("user-1", "att-1", { entitlementId: "ent-1", examName: "Exam" });

    // Session expiry best-effort cleanup MUST NOT delete drafts (AC-25)
    mocks.post.mockResolvedValueOnce({ response: { ok: true, status: 204 }, error: undefined });
    await clearSessionBestEffort();
    expect(await loadAttemptDrafts("user-1", "att-1")).toHaveLength(1);
    expect(readStoredEditSessionId("user-1", "att-1")).toBe("sess-1");
    expect(readAttemptStartContext("user-1", "att-1")).not.toBeNull();

    // Explicit logout clears all drafts and browser session markers (AC-24)
    mocks.post.mockResolvedValueOnce({ response: { ok: false, status: 401 }, error: undefined });
    await logoutSession();
    expect(await loadAttemptDrafts("user-1", "att-1")).toEqual([]);
    expect(readStoredEditSessionId("user-1", "att-1")).toBeNull();
    expect(readAttemptStartContext("user-1", "att-1")).toBeNull();
  });
});
