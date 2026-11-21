import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import type { ItemDraftRecord } from "@/features/attempt-draft";
import { ApiStatusError } from "@/lib/api/client";
import { AttemptApiError, type SaveAnswersRequest, type SaveAnswersResponse } from "./api";
import type { AttemptStateResponse } from "./mappers";
import { AttemptSaveQueue, type SaveStatusSnapshot } from "./save-queue";

function makeBaseState(overrides: Partial<AttemptStateResponse> = {}): AttemptStateResponse {
  return {
    attemptId: "att-1",
    state: "IN_PROGRESS",
    serverNow: "2026-11-21T10:00:00Z",
    timing: "PER_PART",
    navigationMode: "SEQUENTIAL",
    parts: [
      {
        partId: "p-1",
        partCode: "P1",
        partName: "Phần 1",
        ord: 0,
        state: "OPEN",
        items: [
          {
            itemId: "item-sc",
            displayNo: 1,
            questionType: "SINGLE_CHOICE",
            answerSeq: 0,
            flagged: false,
          },
          {
            itemId: "item-sa",
            displayNo: 2,
            questionType: "SHORT_ANSWER",
            answerSeq: 0,
            flagged: false,
          },
          {
            itemId: "item-cloze",
            displayNo: 3,
            questionType: "CLOZE",
            answerSeq: 0,
            flagged: false,
          },
          {
            itemId: "item-essay",
            displayNo: 4,
            questionType: "ESSAY",
            answerSeq: 0,
            flagged: false,
          },
        ],
      },
    ],
    ...overrides,
  };
}

function makeDraftDeps() {
  return {
    saveItemDraft: vi.fn(async () => {}),
    deleteItemDraft: vi.fn(async () => {}),
    saveAttemptMeta: vi.fn(async () => {}),
  };
}

describe("AttemptSaveQueue (AC-15, AC-16, AC-17)", () => {
  beforeEach(() => {
    vi.useFakeTimers();
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  it("gửi theo đúng nhịp AC-15: trắc nghiệm 0ms, nhập ngắn/cloze 800ms, tự luận 2000ms idle và tối đa 15000ms khi gõ liên tục, flush khi chuyển câu", async () => {
    const saveCalls: SaveAnswersRequest[] = [];
    const saveAnswersFn = vi.fn(
      async (_attemptId: string, _sessionId: string, body: SaveAnswersRequest): Promise<SaveAnswersResponse> => {
        saveCalls.push(body);
        return {
          serverNow: "2026-11-21T10:00:05Z",
          results: (body.changes ?? []).map((c) => ({
            itemId: c.itemId,
            seq: c.seq,
            status: "SAVED",
          })),
        };
      },
    );

    const draftDeps = makeDraftDeps();
    const queue = new AttemptSaveQueue({
      attemptId: "att-1",
      userId: "user-1",
      saveAnswersFn,
      loadAttemptStateFn: vi.fn(async () => makeBaseState()),
      draftDeps,
      getSessionId: () => "sess-1",
      initialOnline: true,
    });
    queue.hydrateFromState(makeBaseState());

    // 1. Choice saves immediately (0ms)
    queue.recordAnswerChange("item-sc", "SINGLE_CHOICE", { type: "SINGLE_CHOICE", optionId: "opt-a" });
    await vi.advanceTimersByTimeAsync(0);
    expect(saveAnswersFn).toHaveBeenCalledTimes(1);
    expect(saveCalls[0]?.changes).toEqual([
      { itemId: "item-sc", seq: 1, answer: { type: "SINGLE_CHOICE", optionId: "opt-a" } },
    ]);
    expect(draftDeps.saveItemDraft).toHaveBeenCalled();

    // 2. Short answer & Cloze wait 800ms idle
    queue.recordAnswerChange("item-sa", "SHORT_ANSWER", { type: "SHORT_ANSWER", text: "4" });
    await vi.advanceTimersByTimeAsync(400);
    queue.recordAnswerChange("item-sa", "SHORT_ANSWER", { type: "SHORT_ANSWER", text: "42" });
    await vi.advanceTimersByTimeAsync(799);
    expect(saveAnswersFn).toHaveBeenCalledTimes(1);
    await vi.advanceTimersByTimeAsync(1);
    expect(saveAnswersFn).toHaveBeenCalledTimes(2);
    expect(saveCalls[1]?.changes).toEqual([
      { itemId: "item-sa", seq: 2, answer: { type: "SHORT_ANSWER", text: "42" } },
    ]);

    queue.recordAnswerChange("item-cloze", "CLOZE", { type: "CLOZE", blanks: { b1: "ok" } });
    await vi.advanceTimersByTimeAsync(799);
    expect(saveAnswersFn).toHaveBeenCalledTimes(2);
    await vi.advanceTimersByTimeAsync(1);
    expect(saveAnswersFn).toHaveBeenCalledTimes(3);

    // 3. Essay saves after 2000ms idle AND at least every 15_000ms during continuous typing
    for (let step = 0; step < 15; step++) {
      queue.recordAnswerChange("item-essay", "ESSAY", {
        type: "ESSAY",
        html: `<p>word ${step}</p>`,
        wordCount: 2,
      });
      await vi.advanceTimersByTimeAsync(1000);
      if (step < 14) {
        expect(saveAnswersFn).toHaveBeenCalledTimes(3);
      }
    }
    // At t = 15_000ms max-wait triggers automatically even without 2000ms idle!
    expect(saveAnswersFn).toHaveBeenCalledTimes(4);
    expect(saveCalls[3]?.changes?.[0]?.itemId).toBe("item-essay");

    // And idle 2000ms after stopping also flushes essay
    queue.recordAnswerChange("item-essay", "ESSAY", {
      type: "ESSAY",
      html: "<p>final idle</p>",
      wordCount: 2,
    });
    await vi.advanceTimersByTimeAsync(1999);
    expect(saveAnswersFn).toHaveBeenCalledTimes(4);
    await vi.advanceTimersByTimeAsync(1);
    expect(saveAnswersFn).toHaveBeenCalledTimes(5);

    // 4. Navigation flushNow cancels debounce and dispatches immediately
    queue.recordAnswerChange("item-sa", "SHORT_ANSWER", { type: "SHORT_ANSWER", text: "99" });
    expect(saveAnswersFn).toHaveBeenCalledTimes(5);
    await queue.flushNow();
    expect(saveAnswersFn).toHaveBeenCalledTimes(6);
    expect(saveCalls[5]?.changes?.[0]?.seq).toBe(3);

    queue.destroy();
  });

  it("chỉ hiện 'Đã lưu' khi seq máy chủ trả về bằng seq mới nhất, và 'Chưa lưu' luôn kèm lý do (AC-16)", async () => {
    let resolveFirst!: (val: SaveAnswersResponse) => void;
    let resolveSecond!: (val: SaveAnswersResponse) => void;

    const saveAnswersFn = vi
      .fn<() => Promise<SaveAnswersResponse>>()
      .mockImplementationOnce(
        () =>
          new Promise((resolve) => {
            resolveFirst = resolve;
          }),
      )
      .mockImplementationOnce(
        () =>
          new Promise((resolve) => {
            resolveSecond = resolve;
          }),
      );

    const statuses: SaveStatusSnapshot[] = [];
    const queue = new AttemptSaveQueue({
      attemptId: "att-1",
      userId: "user-1",
      saveAnswersFn,
      draftDeps: makeDraftDeps(),
      getSessionId: () => "sess-1",
      initialOnline: true,
      onStatusChange: (s) => statuses.push(s),
    });
    queue.hydrateFromState(makeBaseState());

    // Edit #1 (seq = 1) starts saving immediately
    queue.recordAnswerChange("item-sc", "SINGLE_CHOICE", { type: "SINGLE_CHOICE", optionId: "opt-a" });
    expect(queue.getStatusSnapshot().state).toBe("saving");
    expect(queue.isItemSaved("item-sc")).toBe(false);
    expect(queue.hasUnsavedChanges()).toBe(true);

    // Edit #2 (seq = 2) happens while seq = 1 is still in-flight
    queue.recordAnswerChange("item-sc", "SINGLE_CHOICE", { type: "SINGLE_CHOICE", optionId: "opt-b" });

    // Server ACKs seq = 1 -> MUST NOT become "saved" because localSeq is 2!
    resolveFirst({
      serverNow: "2026-11-21T10:00:01Z",
      results: [{ itemId: "item-sc", seq: 1, status: "SAVED" }],
    });
    await vi.advanceTimersByTimeAsync(0);

    expect(queue.getStatusSnapshot().state).toBe("saving");
    expect(queue.isItemSaved("item-sc")).toBe(false);
    expect(queue.hasUnsavedChanges()).toBe(true);

    // Server ACKs seq = 2 -> now serverConfirmedSeq === localSeq -> "saved"!
    resolveSecond({
      serverNow: "2026-11-21T10:00:02Z",
      results: [{ itemId: "item-sc", seq: 2, status: "SAVED" }],
    });
    await vi.advanceTimersByTimeAsync(0);

    expect(queue.getStatusSnapshot().state).toBe("saved");
    expect(queue.isItemSaved("item-sc")).toBe(true);
    expect(queue.hasUnsavedChanges()).toBe(false);

    // Offline produces "unsaved" with reason "offline"
    queue.handleOffline();
    queue.recordAnswerChange("item-sc", "SINGLE_CHOICE", { type: "SINGLE_CHOICE", optionId: "opt-c" });
    const offlineSnap = queue.getStatusSnapshot();
    expect(offlineSnap.state).toBe("unsaved");
    expect(offlineSnap.reason).toBe("offline");
    expect(offlineSnap.dirtyItemIds).toContain("item-sc");
    expect(statuses.length).toBeGreaterThan(0);

    queue.destroy();
  });

  it("thử lại theo cấp số nhân, tôn trọng 429 Retry-After và khi có mạng lại gọi loadAttemptState trước rồi chỉ gửi câu có seq > answerSeq máy chủ (AC-17)", async () => {
    const saveCalls: SaveAnswersRequest[] = [];
    const saveAnswersFn = vi
      .fn<(a: string, s: string, body: SaveAnswersRequest) => Promise<SaveAnswersResponse>>()
      .mockRejectedValueOnce(new AttemptApiError(503, "Unavailable"))
      .mockRejectedValueOnce(new AttemptApiError(429, "Rate limited", { retryAfterSeconds: 4 }))
      .mockImplementation(async (_a, _s, body) => {
        saveCalls.push(body);
        return {
          serverNow: "2026-11-21T10:01:00Z",
          results: (body.changes ?? []).map((c) => ({
            itemId: c.itemId,
            seq: c.seq,
            status: "SAVED",
          })),
        };
      });

    const loadAttemptStateFn = vi.fn(async (): Promise<AttemptStateResponse> =>
      makeBaseState({
        parts: [
          {
            partId: "p-1",
            partCode: "P1",
            partName: "Phần 1",
            ord: 0,
            state: "OPEN",
            items: [
              {
                itemId: "item-sc",
                displayNo: 1,
                questionType: "SINGLE_CHOICE",
                // Server already has newer answerSeq = 5! Local draft seq = 1 should be dropped!
                answerSeq: 5,
                answer: { type: "SINGLE_CHOICE", optionId: "opt-server" },
              },
              {
                itemId: "item-sa",
                displayNo: 2,
                questionType: "SHORT_ANSWER",
                // Server has answerSeq = 1; local draft seq = 3 MUST be sent!
                answerSeq: 1,
                answer: { type: "SHORT_ANSWER", text: "old" },
              },
            ],
          },
        ],
      }),
    );

    const queue = new AttemptSaveQueue({
      attemptId: "att-1",
      userId: "user-1",
      saveAnswersFn,
      loadAttemptStateFn,
      draftDeps: makeDraftDeps(),
      getSessionId: () => "sess-1",
      initialOnline: true,
    });

    const drafts: ItemDraftRecord[] = [
      {
        id: "user-1:att-1:item-sc",
        userId: "user-1",
        attemptId: "att-1",
        itemId: "item-sc",
        seq: 1,
        syncedSeq: 0,
        answer: { type: "SINGLE_CHOICE", optionId: "opt-stale" },
        flagged: false,
        dirtyAnswer: true,
        dirtyFlag: false,
        updatedAt: 1000,
      },
      {
        id: "user-1:att-1:item-sa",
        userId: "user-1",
        attemptId: "att-1",
        itemId: "item-sa",
        seq: 3,
        syncedSeq: 1,
        answer: { type: "SHORT_ANSWER", text: "newer-local" },
        flagged: false,
        dirtyAnswer: true,
        dirtyFlag: false,
        updatedAt: 1000,
      },
    ];
    queue.hydrateFromState(makeBaseState(), drafts);

    // Flush #1 fails with 503 -> schedules 1000ms backoff
    await queue.flushNow();
    expect(saveAnswersFn).toHaveBeenCalledTimes(1);
    expect(queue.getStatusSnapshot()).toMatchObject({ state: "unsaved", reason: "server_error" });

    // Before 1000ms, retry does not run
    await vi.advanceTimersByTimeAsync(999);
    expect(saveAnswersFn).toHaveBeenCalledTimes(1);

    // At 1000ms retry runs -> fails with 429 Retry-After: 4 -> waits 4000ms
    await vi.advanceTimersByTimeAsync(1);
    expect(saveAnswersFn).toHaveBeenCalledTimes(2);
    expect(queue.getStatusSnapshot()).toMatchObject({
      state: "unsaved",
      reason: "rate_limited",
      retryAfterSeconds: 4,
    });

    await vi.advanceTimersByTimeAsync(3999);
    expect(saveAnswersFn).toHaveBeenCalledTimes(2);

    // Go offline and then come back online -> reconnect MUST call loadAttemptStateFn FIRST,
    // drop item-sc (local seq 1 <= server seq 5), and upload ONLY item-sa (local seq 3 > server seq 1)!
    queue.handleOffline();
    await queue.handleOnline();

    expect(loadAttemptStateFn).toHaveBeenCalledTimes(1);
    expect(saveCalls).toHaveLength(1);
    expect(saveCalls[0]?.changes).toEqual([
      { itemId: "item-sa", seq: 3, answer: { type: "SHORT_ANSWER", text: "newer-local" } },
    ]);
    expect(queue.getAnswer("item-sc")).toEqual({ type: "SINGLE_CHOICE", optionId: "opt-server" });
    expect(queue.getStatusSnapshot().state).toBe("saved");

    queue.destroy();
  });

  it("gắn cờ câu chưa trả lời (seq = 0) chỉ gửi flags không gửi changes, và không làm lọt câu tự luận đang debounce (FE-R1-3)", async () => {
    const saveCalls: SaveAnswersRequest[] = [];
    const saveAnswersFn = vi.fn(
      async (_a: string, _s: string, body: SaveAnswersRequest): Promise<SaveAnswersResponse> => {
        saveCalls.push(body);
        return {
          serverNow: "2026-11-21T10:00:05Z",
          results: (body.changes ?? []).map((c) => ({
            itemId: c.itemId,
            seq: c.seq,
            status: "SAVED",
          })),
        };
      },
    );

    const draftDeps = makeDraftDeps();
    const queue = new AttemptSaveQueue({
      attemptId: "att-1",
      userId: "user-1",
      saveAnswersFn,
      draftDeps,
      getSessionId: () => "sess-1",
      initialOnline: true,
    });
    queue.hydrateFromState(makeBaseState());

    // Flag unanswered item (localSeq === 0): MUST send ONLY flags, omit changes
    queue.recordFlagChange("item-sc", true);
    await vi.advanceTimersByTimeAsync(0);

    expect(saveAnswersFn).toHaveBeenCalledTimes(1);
    expect(saveCalls[0]).toEqual({
      flags: [{ itemId: "item-sc", flagged: true }],
    });
    expect(saveCalls[0]?.changes).toBeUndefined();
    expect(queue.getStatusSnapshot().state).toBe("saved");

    // Flag a SHORT_ANSWER item whose answer is currently debouncing -> MUST NOT prematurely flush un-debounced answer
    queue.recordAnswerChange("item-sa", "SHORT_ANSWER", { type: "SHORT_ANSWER", text: "draft" });
    queue.recordFlagChange("item-sa", true);
    await vi.advanceTimersByTimeAsync(0);

    expect(saveAnswersFn).toHaveBeenCalledTimes(2);
    expect(saveCalls[1]).toEqual({
      flags: [{ itemId: "item-sa", flagged: true }],
    });
    expect(saveCalls[1]?.changes).toBeUndefined();

    // After 800ms debounce, the answer itself flushes cleanly
    await vi.advanceTimersByTimeAsync(800);
    expect(saveAnswersFn).toHaveBeenCalledTimes(3);
    expect(saveCalls[2]?.changes).toEqual([
      { itemId: "item-sa", seq: 1, answer: { type: "SHORT_ANSWER", text: "draft" } },
    ]);

    queue.destroy();
  });

  it("dừng vòng lặp khi máy chủ trả REJECTED cho đến khi học viên sửa lại (FE-R1-4)", async () => {
    const saveAnswersFn = vi
      .fn<() => Promise<SaveAnswersResponse>>()
      .mockResolvedValueOnce({
        serverNow: "2026-11-21T10:00:01Z",
        results: [{ itemId: "item-sc", seq: 1, status: "REJECTED", reason: "INVALID_ANSWER" }],
      })
      .mockResolvedValueOnce({
        serverNow: "2026-11-21T10:00:02Z",
        results: [{ itemId: "item-sc", seq: 2, status: "SAVED" }],
      });

    const queue = new AttemptSaveQueue({
      attemptId: "att-1",
      userId: "user-1",
      saveAnswersFn,
      draftDeps: makeDraftDeps(),
      getSessionId: () => "sess-1",
      initialOnline: true,
    });
    queue.hydrateFromState(makeBaseState());

    queue.recordAnswerChange("item-sc", "SINGLE_CHOICE", { type: "SINGLE_CHOICE", optionId: "bad-opt" });
    await vi.advanceTimersByTimeAsync(500);

    // Must be called ONLY ONCE (no infinite retry loop!)
    expect(saveAnswersFn).toHaveBeenCalledTimes(1);
    expect(queue.getStatusSnapshot()).toMatchObject({
      state: "unsaved",
      reason: "rejected",
      hasUnsavedChanges: false,
      dirtyItemIds: [],
    });
    expect(queue.hasUnsavedChanges()).toBe(false);
    expect(queue.getFinalAnswers()).toEqual([]);

    // Next fresh edit clears rejection and saves cleanly
    queue.recordAnswerChange("item-sc", "SINGLE_CHOICE", { type: "SINGLE_CHOICE", optionId: "good-opt" });
    await vi.advanceTimersByTimeAsync(0);
    expect(saveAnswersFn).toHaveBeenCalledTimes(2);
    expect(queue.getStatusSnapshot().state).toBe("saved");

    // Also test markFinalAnswersResult with REJECTED clears dirtyItemIds & hasUnsavedChanges
    queue.handleOffline();
    queue.recordAnswerChange("item-sc", "SINGLE_CHOICE", { type: "SINGLE_CHOICE", optionId: "final-bad" });
    expect(queue.hasUnsavedChanges()).toBe(true);
    queue.markFinalAnswersResult([
      { itemId: "item-sc", seq: 3, status: "REJECTED", reason: "INVALID_ANSWER" },
    ]);
    expect(queue.getDirtyItemIds()).toEqual([]);
    expect(queue.hasUnsavedChanges()).toBe(false);
    expect(queue.getStatusSnapshot()).toMatchObject({ state: "unsaved", reason: "rejected" });

    queue.destroy();
  });

  it("gặp 401 khi lưu bài thì gọi onUnauthorized và giữ nguyên nháp IndexedDB (FE-R1-9)", async () => {
    const draftDeps = makeDraftDeps();
    const onUnauthorized = vi.fn();
    const saveAnswersFn = vi.fn(async () => {
      throw new ApiStatusError(401, "Expired");
    });

    const queue = new AttemptSaveQueue({
      attemptId: "att-1",
      userId: "user-1",
      saveAnswersFn,
      draftDeps,
      getSessionId: () => "sess-1",
      initialOnline: true,
      onUnauthorized,
    });
    queue.hydrateFromState(makeBaseState());

    queue.recordAnswerChange("item-sc", "SINGLE_CHOICE", { type: "SINGLE_CHOICE", optionId: "opt-a" });
    await vi.advanceTimersByTimeAsync(0);

    expect(onUnauthorized).toHaveBeenCalledTimes(1);
    expect(draftDeps.saveItemDraft).toHaveBeenCalled();
    expect(draftDeps.deleteItemDraft).not.toHaveBeenCalled();

    // Must not schedule backoff retries after 401
    await vi.advanceTimersByTimeAsync(5000);
    expect(saveAnswersFn).toHaveBeenCalledTimes(1);

    queue.destroy();
  });

  it("không treo inFlightPromise khi gọi lưu lúc sessionId còn null rồi flushNow sau khi có session", async () => {
    let sessionId: string | null = null;
    const saveAnswersFn = vi.fn(async () => ({
      results: [{ itemId: "item-sc", seq: 1, status: "SAVED" as const }],
      serverNow: "2026-11-21T10:00:01Z",
    }));

    const queue = new AttemptSaveQueue({
      attemptId: "att-1",
      userId: "user-1",
      saveAnswersFn,
      draftDeps: makeDraftDeps(),
      getSessionId: () => sessionId,
      initialOnline: true,
    });
    queue.hydrateFromState(makeBaseState());

    queue.recordAnswerChange("item-sc", "SINGLE_CHOICE", { type: "SINGLE_CHOICE", optionId: "opt-a" });
    await Promise.resolve();
    expect(saveAnswersFn).not.toHaveBeenCalled();

    sessionId = "sess-ready";
    await queue.flushNow();

    expect(saveAnswersFn).toHaveBeenCalledTimes(1);
    expect(queue.getStatusSnapshot().state).toBe("saved");
    queue.destroy();
  });
});
