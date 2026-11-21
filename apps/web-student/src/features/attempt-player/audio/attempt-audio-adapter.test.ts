import { act, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { createElement } from "react";
import { afterEach, describe, expect, it, vi } from "vitest";
import { ApiStatusError } from "@/lib/api/client";
import { renderWithProviders } from "@/test/render";
import { AttemptPlayer, type AttemptPlayerDeps } from "../attempt-player";
import type { TabLockChannel } from "../edit-session";
import type { AttemptStateResponse } from "../mappers";
import {
  type AttemptAudioApi,
  createAttemptAudioAdapter,
  endAttemptAudioPlay,
  openAttemptAudioPlay,
  reportAttemptAudioProgress,
  resumeAttemptAudioPlay,
  toAudioPlaybackSnapshot,
} from "./attempt-audio-adapter";

vi.mock("next/navigation", () => ({
  useRouter: () => ({ push: vi.fn(), replace: vi.fn() }),
  usePathname: () => "/attempts/att-audio-1",
}));

function stubFetch(handler: (input: RequestInfo | URL, init?: RequestInit) => Promise<Response>) {
  const fetchMock = vi.fn(handler);
  vi.stubGlobal("fetch", fetchMock);
  return fetchMock;
}

function makeBaseDeps(overrides: Partial<AttemptPlayerDeps> = {}): AttemptPlayerDeps {
  return {
    claimEditSessionFn: vi.fn(async () => ({
      conflict: false as const,
      sessionId: "sess-audio-1",
      serverNow: "2026-11-22T01:00:00Z",
    })),
    sendHeartbeatFn: vi.fn(async () => ({
      serverNow: "2026-11-22T01:00:30Z",
      state: "IN_PROGRESS" as const,
    })),
    saveAnswersFn: vi.fn(async () => ({
      results: [],
      serverNow: "2026-11-22T01:00:01Z",
    })),
    finishPartFn: vi.fn(async () => ({
      attemptId: "att-audio-1",
      attemptState: "IN_PROGRESS" as const,
      closedPart: { ord: 1, partCode: "P1", partId: "p1", partName: "Phần 1", state: "CLOSED" as const },
      results: [],
      serverNow: "2026-11-22T01:05:00Z",
    })),
    submitAttemptFn: vi.fn(async () => ({
      attemptId: "att-audio-1",
      state: "GRADING" as const,
      parts: [],
      results: [],
      serverNow: "2026-11-22T01:10:00Z",
    })),
    confirmPartChoiceFn: vi.fn(),
    retryPartGenerationFn: vi.fn(),
    loadAfterStartChoicesFn: vi.fn(async () => []),
    loadDraftsFn: vi.fn(async () => []),
    saveItemDraftFn: vi.fn(async () => undefined),
    deleteItemDraftFn: vi.fn(async () => undefined),
    saveAttemptMetaFn: vi.fn(async () => undefined),
    loadAttemptMetaFn: vi.fn(async () => null),
    clearAttemptDraftsFn: vi.fn(async () => undefined),
    loadAttemptStartContextFn: vi.fn(async () => null),
    saveAttemptStartContextFn: vi.fn(async () => undefined),
    startAttemptFn: vi.fn(),
    refreshSessionFn: vi.fn(async () => undefined),
    navigateFn: vi.fn(),
    createTabLockChannelFn: () => null,
    ...overrides,
  };
}

function createInMemoryTabLockBus() {
  const channels = new Map<string, Set<TabLockChannel>>();
  return (name: string): TabLockChannel => {
    if (!channels.has(name)) {
      channels.set(name, new Set());
    }
    const group = channels.get(name)!;
    const channel: TabLockChannel = {
      onmessage: null,
      postMessage(message: unknown) {
        for (const peer of group) {
          if (peer === channel || !peer.onmessage) continue;
          const handler = peer.onmessage;
          queueMicrotask(() => {
            handler({ data: message } as MessageEvent<unknown>);
          });
        }
      },
      close() {
        group.delete(channel);
      },
    };
    group.add(channel);
    return channel;
  };
}

function makeAudioAttemptState(overrides: Partial<AttemptStateResponse> = {}): AttemptStateResponse {
  return {
    attemptId: "att-audio-1",
    state: "IN_PROGRESS",
    navigationMode: "FREE",
    timing: "SHARED",
    deadlineAt: "2026-11-22T02:00:00Z",
    examName: "Đề H-SCA Tiếng Anh có audio",
    serverNow: "2026-11-22T01:00:00Z",
    audioPolicy: { maxPlays: 2, seekable: false },
    audioPlays: [
      {
        assetId: "asset-1",
        partId: "p1",
        playId: undefined,
        playsUsed: 0,
        playsLeft: 2,
        durationMs: 45_000,
        positionMs: 0,
        state: "IDLE",
      },
    ],
    parts: [
      {
        ord: 1,
        partId: "p1",
        partName: "Phần Nghe",
        state: "OPEN",
        items: [
          {
            itemId: "set-audio-1",
            displayNo: 1,
            questionType: "QUESTION_SET",
            content: "",
            attachments: [
              {
                assetId: "asset-1",
                fileName: "listening-track-1.mp3",
                mime: "audio/mpeg",
                url: "/api/v1/me/attempts/att-audio-1/assets/asset-1",
              },
            ],
            questionBody: {
              type: "QUESTION_SET",
              passage: "<p>Nghe đoạn hội thoại và trả lời câu hỏi.</p>",
            },
          },
          {
            itemId: "q-audio-1",
            parentItemId: "set-audio-1",
            displayNo: 1,
            unitCount: 1,
            questionType: "SINGLE_CHOICE",
            content: "<p>What is the main topic?</p>",
            questionBody: {
              type: "SINGLE_CHOICE",
              options: [
                { id: "opt-a", content: "<p>Weather</p>" },
                { id: "opt-b", content: "<p>Travel</p>" },
              ],
            },
          },
        ],
      },
    ],
    ...overrides,
  };
}

function mockAudioMediaPrototype() {
  const pauseSpy = vi.spyOn(HTMLMediaElement.prototype, "pause").mockImplementation(() => undefined);
  const loadSpy = vi.spyOn(HTMLMediaElement.prototype, "load").mockImplementation(() => undefined);
  const playSpy = vi.spyOn(HTMLMediaElement.prototype, "play").mockImplementation(async () => undefined);
  return { pauseSpy, loadSpy, playSpy };
}

describe("attempt-audio-adapter & AttemptPlayer audio wiring (AC-5, AC-7)", () => {
  afterEach(() => {
    vi.restoreAllMocks();
    vi.unstubAllGlobals();
    vi.useRealTimers();
  });

  it("gọi đúng endpoint, header X-Attempt-Session và body cho openPlay, resumePlay, reportProgress, endPlay", async () => {
    const fetchMock = stubFetch(async (input, init) => {
      const req = input instanceof Request ? input : new Request(String(input), init);
      const url = new URL(req.url, "http://localhost");
      expect(req.headers.get("X-Attempt-Session")).toBe("sess-123");

      if (url.pathname.endsWith("/plays") && req.method === "POST") {
        expect(await req.json()).toEqual({ requestId: "req-1" });
        return new Response(
          JSON.stringify({
            playId: "play-1",
            playsUsed: 1,
            playsLeft: 1,
            positionMs: 0,
            durationMs: 60_000,
            state: "PLAYING",
          }),
          { status: 200, headers: { "content-type": "application/json" } },
        );
      }

      if (url.pathname.endsWith("/plays/play-1/resume") && req.method === "POST") {
        return new Response(
          JSON.stringify({
            playId: "play-1",
            playsUsed: 1,
            playsLeft: 1,
            positionMs: 12_000,
            durationMs: 60_000,
            state: "PLAYING",
          }),
          { status: 200, headers: { "content-type": "application/json" } },
        );
      }

      if (url.pathname.endsWith("/plays/play-1/progress") && req.method === "PUT") {
        expect(await req.json()).toEqual({ positionMs: 15_000 });
        return new Response(
          JSON.stringify({
            playId: "play-1",
            playsUsed: 1,
            playsLeft: 1,
            positionMs: 15_000,
            durationMs: 60_000,
            state: "PLAYING",
          }),
          { status: 200, headers: { "content-type": "application/json" } },
        );
      }

      if (url.pathname.endsWith("/plays/play-1/end") && req.method === "POST") {
        return new Response(
          JSON.stringify({
            playId: "play-1",
            playsUsed: 1,
            playsLeft: 1,
            positionMs: 60_000,
            durationMs: 60_000,
            state: "ENDED",
            endReason: "FINISHED",
          }),
          { status: 200, headers: { "content-type": "application/json" } },
        );
      }

      return new Response("unexpected", { status: 500 });
    });

    const opened = await openAttemptAudioPlay("att-1", "asset-1", "sess-123", "req-1");
    expect(opened).toMatchObject({ playId: "play-1", playsUsed: 1, playsLeft: 1, state: "PLAYING" });

    const resumed = await resumeAttemptAudioPlay("att-1", "asset-1", "play-1", "sess-123");
    expect(resumed).toMatchObject({ playId: "play-1", positionMs: 12_000, state: "PLAYING" });

    const progressed = await reportAttemptAudioProgress("att-1", "asset-1", "play-1", "sess-123", 15_000);
    expect(progressed).toMatchObject({ positionMs: 15_000, state: "PLAYING" });

    const ended = await endAttemptAudioPlay("att-1", "asset-1", "play-1", "sess-123");
    expect(ended).toMatchObject({ state: "ENDED", endReason: "FINISHED", positionMs: 60_000 });

    expect(fetchMock).toHaveBeenCalledTimes(4);
  });

  it("ánh xạ đủ mã lỗi 409, 401, lỗi mạng và phản hồi không xác định sang AudioAdapterError", async () => {
    const cases: Array<{
      status: number;
      body: Record<string, unknown>;
      expectedCode: string;
      extra?: Record<string, unknown>;
    }> = [
      {
        status: 409,
        body: { code: "AUDIO_PLAY_IN_PROGRESS", message: "In progress", playId: "play-active" },
        expectedCode: "AUDIO_PLAY_IN_PROGRESS",
        extra: { playId: "play-active", status: 409 },
      },
      {
        status: 409,
        body: { code: "AUDIO_SEEK_REJECTED", message: "Seek rejected", positionMs: 9500 },
        expectedCode: "AUDIO_SEEK_REJECTED",
        extra: { positionMs: 9500, status: 409 },
      },
      {
        status: 409,
        body: { code: "AUDIO_PLAY_STALE", message: "Stale" },
        expectedCode: "AUDIO_PLAY_STALE",
        extra: { status: 409 },
      },
      {
        status: 409,
        body: { code: "AUDIO_PLAYS_EXHAUSTED", message: "Exhausted" },
        expectedCode: "AUDIO_PLAYS_EXHAUSTED",
        extra: { status: 409 },
      },
      {
        status: 409,
        body: { code: "PART_CLOSED", message: "Part closed" },
        expectedCode: "PART_CLOSED",
        extra: { status: 409 },
      },
      {
        status: 409,
        body: { code: "TIME_UP", message: "Time up" },
        expectedCode: "TIME_UP",
        extra: { status: 409 },
      },
      {
        status: 409,
        body: { code: "ATTEMPT_SESSION_REVOKED", message: "Revoked" },
        expectedCode: "ATTEMPT_SESSION_REVOKED",
        extra: { status: 409 },
      },
      {
        status: 409,
        body: { code: "OTHER_CONFLICT", message: "Other" },
        expectedCode: "UNKNOWN",
        extra: { status: 409 },
      },
      {
        status: 401,
        body: { code: "UNAUTHORIZED", message: "Expired" },
        expectedCode: "UNAUTHORIZED",
        extra: { status: 401 },
      },
      {
        status: 500,
        body: { code: "INTERNAL_ERROR", message: "Server boom" },
        expectedCode: "UNKNOWN",
        extra: { status: 500 },
      },
    ];

    for (const c of cases) {
      stubFetch(async () =>
        new Response(JSON.stringify(c.body), {
          status: c.status,
          headers: { "content-type": "application/json" },
        }),
      );
      await expect(openAttemptAudioPlay("att-1", "asset-1", "sess-1", "req-x")).rejects.toMatchObject({
        name: "AudioAdapterError",
        code: c.expectedCode,
        ...c.extra,
      });
    }

    // Network rejection / TypeError -> NETWORK_ERROR
    stubFetch(async () => {
      throw new TypeError("Failed to fetch");
    });
    await expect(resumeAttemptAudioPlay("att-1", "asset-1", "play-1", "sess-1")).rejects.toMatchObject({
      name: "AudioAdapterError",
      code: "NETWORK_ERROR",
    });
  });

  it("createAttemptAudioAdapter tạo resolveStreamUrl, reloadState từ audioPlays và chặn khi thiếu sessionId", async () => {
    const adapter = createAttemptAudioAdapter({
      attemptId: "att/1",
      assetId: "asset-9",
      maxPlays: 2,
      getSessionId: () => "sess-9",
      reloadAttemptState: async () =>
        makeAudioAttemptState({
          audioPolicy: { maxPlays: 2, seekable: false },
          audioPlays: [
            {
              assetId: "asset-9",
              playId: "play-9",
              playsUsed: 1,
              playsLeft: 1,
              durationMs: 30_000,
              positionMs: 12_500,
              state: "PLAYING",
            },
          ],
        }),
    });

    expect(adapter.resolveStreamUrl("play 9")).toBe(
      "/api/v1/me/attempts/att%2F1/assets/asset-9?playId=play%209",
    );
    await expect(adapter.reloadState?.()).resolves.toEqual({
      playId: "play-9",
      playsUsed: 1,
      playsLeft: 1,
      durationMs: 30_000,
      positionMs: 12_500,
      state: "PLAYING",
      endReason: null,
    });

    expect(toAudioPlaybackSnapshot(undefined, 2)).toEqual({
      playId: null,
      playsUsed: 0,
      playsLeft: 2,
      durationMs: 0,
      positionMs: 0,
      state: "IDLE",
      endReason: null,
    });

    const missingSessionAdapter = createAttemptAudioAdapter({
      attemptId: "att-1",
      assetId: "asset-1",
      maxPlays: 2,
      getSessionId: () => null,
      reloadAttemptState: async () => makeAudioAttemptState(),
    });
    await expect(missingSessionAdapter.openPlay("req-1")).rejects.toMatchObject({
      code: "ATTEMPT_SESSION_REVOKED",
    });
  });

  it("AttemptPlayer không render <audio controls>, hiển thị audio trên childItems không trùng lặp, và chờ sessionId trước khi auto-resume (AC-5)", async () => {
    mockAudioMediaPrototype();

    let resolveClaim!: (value: { conflict: false; sessionId: string; serverNow: string }) => void;
    const pendingClaim = new Promise<{ conflict: false; sessionId: string; serverNow: string }>(
      (resolve) => {
        resolveClaim = resolve;
      },
    );

    const openPlay = vi.fn();
    const resumePlay = vi.fn(async () => ({
      playId: "play-resume-1",
      playsUsed: 1,
      playsLeft: 1,
      positionMs: 18_000,
      durationMs: 45_000,
      state: "PLAYING" as const,
    }));

    const audioApi: AttemptAudioApi = {
      openPlay,
      resumePlay,
      reportProgress: vi.fn(),
      endPlay: vi.fn(),
    };

    const state = makeAudioAttemptState({
      audioPlays: [
        {
          assetId: "asset-1",
          partId: "p1",
          playId: "play-resume-1",
          playsUsed: 1,
          playsLeft: 1,
          durationMs: 45_000,
          positionMs: 18_000,
          state: "PLAYING",
        },
        {
          assetId: "asset-child-only",
          partId: "p1",
          playsUsed: 0,
          playsLeft: 2,
          durationMs: 20_000,
          positionMs: 0,
          state: "IDLE",
        },
      ],
      parts: [
        {
          ord: 1,
          partId: "p1",
          partName: "Phần Nghe",
          state: "OPEN",
          items: [
            makeAudioAttemptState().parts![0]!.items![0]!,
            {
              ...makeAudioAttemptState().parts![0]!.items![1]!,
              attachments: [
                // Duplicate parent assetId -> must NOT render twice
                {
                  assetId: "asset-1",
                  fileName: "listening-track-1.mp3",
                  mime: "audio/mpeg",
                  url: "/api/v1/me/attempts/att-audio-1/assets/asset-1",
                },
                // Distinct child audio -> MUST render cleanly
                {
                  assetId: "asset-child-only",
                  fileName: "child-cue.mp3",
                  mime: "audio/mpeg",
                  url: "/api/v1/me/attempts/att-audio-1/assets/asset-child-only",
                },
              ],
            },
          ],
        },
      ],
    });

    renderWithProviders(
      createElement(AttemptPlayer, {
        attemptId: "att-audio-1",
        userId: "user-1",
        deps: makeBaseDeps({
          loadAttemptStateFn: vi.fn(async () => state),
          claimEditSessionFn: vi.fn(() => pendingClaim),
          audioApi,
        }),
      }),
    );

    expect(await screen.findByText("listening-track-1.mp3")).toBeInTheDocument();
    expect(screen.getByText("child-cue.mp3")).toBeInTheDocument();

    // No raw <audio controls> anywhere in DOM
    const audioEls = document.querySelectorAll("audio");
    expect(audioEls).toHaveLength(2);
    for (const el of audioEls) {
      expect(el).not.toHaveAttribute("controls");
    }

    // While claimEditSession is pending, player is disabled without flashing disabledReason banner and MUST NOT fire doomed resumePlay
    expect(resumePlay).not.toHaveBeenCalled();
    expect(openPlay).not.toHaveBeenCalled();
    expect(screen.queryByText("Trình phát audio đang tạm khóa.")).not.toBeInTheDocument();

    // Resolve claimEditSession -> React remounts with live sessionId -> auto-resumes with X-Attempt-Session without openPlay
    await act(async () => {
      resolveClaim({
        conflict: false,
        sessionId: "sess-live-5",
        serverNow: "2026-11-22T01:00:01Z",
      });
    });

    await waitFor(() => {
      expect(resumePlay).toHaveBeenCalledTimes(1);
    });
    expect(resumePlay).toHaveBeenCalledWith(
      "att-audio-1",
      "asset-1",
      "play-resume-1",
      "sess-live-5",
    );
    expect(openPlay).not.toHaveBeenCalled();
  });

  it("dừng phát ngay lập tức khi nộp bài, hoàn thành phần, hết giờ, bị tiếp quản và mở ở tab phụ (AC-7)", async () => {
    const user = userEvent.setup();
    const { pauseSpy } = mockAudioMediaPrototype();

    const makePlayingAudioApi = (): AttemptAudioApi => ({
      openPlay: vi.fn(async () => ({
        playId: "play-active",
        playsUsed: 1,
        playsLeft: 1,
        positionMs: 0,
        durationMs: 45_000,
        state: "PLAYING" as const,
      })),
      resumePlay: vi.fn(async () => ({
        playId: "play-active",
        playsUsed: 1,
        playsLeft: 1,
        positionMs: 5_000,
        durationMs: 45_000,
        state: "PLAYING" as const,
      })),
      reportProgress: vi.fn(async () => ({
        playId: "play-active",
        playsUsed: 1,
        playsLeft: 1,
        positionMs: 10_000,
        durationMs: 45_000,
        state: "PLAYING" as const,
      })),
      endPlay: vi.fn(),
    });

    // 1. Immediate stop on Submit BEFORE submitAttemptFn resolves
    let resolveSubmit!: (val: Awaited<ReturnType<NonNullable<AttemptPlayerDeps["submitAttemptFn"]>>>) => void;
    const submitAttemptFn = vi.fn(
      () =>
        new Promise<Awaited<ReturnType<NonNullable<AttemptPlayerDeps["submitAttemptFn"]>>>>(
          (resolve) => {
            resolveSubmit = resolve;
          },
        ),
    );
    const submitState = makeAudioAttemptState();
    const submitView = renderWithProviders(
      createElement(AttemptPlayer, {
        attemptId: "att-audio-1",
        userId: "user-1",
        deps: makeBaseDeps({
          loadAttemptStateFn: vi.fn(async () => submitState),
          submitAttemptFn,
          audioApi: makePlayingAudioApi(),
        }),
      }),
    );

    await waitFor(() => expect(screen.getByRole("button", { name: "Nghe" })).toBeEnabled());
    await user.click(screen.getByRole("button", { name: "Nghe" }));
    await waitFor(() => expect(screen.getByRole("button", { name: "Đang phát…" })).toBeInTheDocument());

    pauseSpy.mockClear();
    await user.click(screen.getByRole("button", { name: "Nộp bài" }));
    await user.click(screen.getAllByRole("button", { name: "Nộp bài" }).at(-1)!);

    // Media stops immediately even before submitAttempt resolves!
    await waitFor(() => expect(pauseSpy).toHaveBeenCalled());
    expect(submitAttemptFn).toHaveBeenCalledTimes(1);

    await act(async () => {
      resolveSubmit({
        attemptId: "att-audio-1",
        state: "GRADING",
        parts: [],
        results: [],
        serverNow: "2026-11-22T01:02:00Z",
      });
    });
    submitView.unmount();

    // 2. Immediate stop on FinishPart BEFORE finishPartFn resolves
    const seqState = makeAudioAttemptState({
      navigationMode: "SEQUENTIAL",
      timing: "PER_PART",
      parts: [
        ...makeAudioAttemptState().parts!,
        { ord: 2, partId: "p2", partName: "Phần 2", state: "PENDING", items: [] },
      ],
    });
    const finishPartFn = vi.fn(
      () =>
        new Promise<Awaited<ReturnType<NonNullable<AttemptPlayerDeps["finishPartFn"]>>>>(() => {}),
    );
    const finishView = renderWithProviders(
      createElement(AttemptPlayer, {
        attemptId: "att-audio-1",
        userId: "user-1",
        deps: makeBaseDeps({
          loadAttemptStateFn: vi.fn(async () => seqState),
          finishPartFn,
          audioApi: makePlayingAudioApi(),
        }),
      }),
    );

    await waitFor(() => expect(screen.getByRole("button", { name: "Nghe" })).toBeEnabled());
    await user.click(screen.getByRole("button", { name: "Nghe" }));
    await waitFor(() => expect(screen.getByRole("button", { name: "Đang phát…" })).toBeInTheDocument());

    pauseSpy.mockClear();
    await user.click(screen.getByRole("button", { name: "Hoàn thành phần" }));
    await user.click(screen.getAllByRole("button", { name: "Hoàn thành phần" }).at(-1)!);
    await waitFor(() => expect(pauseSpy).toHaveBeenCalled());
    finishView.unmount();

    // 3. Immediate stop on Time-Up (timer hits <= 0)
    const timeUpState = makeAudioAttemptState({
      serverNow: "2026-11-22T01:59:59Z",
      deadlineAt: "2026-11-22T02:00:00Z",
    });
    const timeUpView = renderWithProviders(
      createElement(AttemptPlayer, {
        attemptId: "att-audio-1",
        userId: "user-1",
        deps: makeBaseDeps({
          loadAttemptStateFn: vi
            .fn()
            .mockResolvedValueOnce(timeUpState)
            .mockResolvedValue({ ...timeUpState, state: "GRADING" as const }),
          claimEditSessionFn: vi.fn(async () => ({
            conflict: false as const,
            sessionId: "sess-audio-1",
            serverNow: "2026-11-22T01:59:59Z",
          })),
          audioApi: makePlayingAudioApi(),
        }),
      }),
    );
    await waitFor(() => expect(screen.getByRole("button", { name: "Nghe" })).toBeEnabled());
    await user.click(screen.getByRole("button", { name: "Nghe" }));
    await waitFor(() => expect(screen.getByRole("button", { name: "Đang phát…" })).toBeInTheDocument());

    pauseSpy.mockClear();
    await waitFor(
      () => {
        expect(pauseSpy).toHaveBeenCalled();
        expect(screen.getByText(/Đã hết giờ làm bài/)).toBeInTheDocument();
      },
      { timeout: 3500 },
    );
    timeUpView.unmount();

    // 4a. Immediate stop on BroadcastChannel FORCE_CLAIM + takeover_required from claimEditSessionFn
    const evictedApi = makePlayingAudioApi();
    const bus = createInMemoryTabLockBus();

    const tab1View = renderWithProviders(
      createElement(AttemptPlayer, {
        attemptId: "att-audio-bus",
        userId: "user-1",
        deps: makeBaseDeps({
          loadAttemptStateFn: vi.fn(async () => makeAudioAttemptState({ attemptId: "att-audio-bus" })),
          audioApi: evictedApi,
          createTabLockChannelFn: bus,
        }),
      }),
    );
    await waitFor(() => expect(screen.getByRole("button", { name: "Nghe" })).toBeEnabled(), {
      timeout: 2500,
    });
    await user.click(screen.getByRole("button", { name: "Nghe" }));
    await waitFor(() => expect(screen.getByRole("button", { name: "Đang phát…" })).toBeInTheDocument());

    pauseSpy.mockClear();
    const tab2View = renderWithProviders(
      createElement(AttemptPlayer, {
        attemptId: "att-audio-bus",
        userId: "user-1",
        deps: makeBaseDeps({
          loadAttemptStateFn: vi.fn(async () => makeAudioAttemptState({ attemptId: "att-audio-bus" })),
          audioApi: makePlayingAudioApi(),
          createTabLockChannelFn: bus,
          claimEditSessionFn: vi.fn(async () => ({
            conflict: true as const,
            code: "ATTEMPT_OPEN_ELSEWHERE",
            message: "Open elsewhere",
            lastSeenAt: "2026-11-22T01:00:10Z",
          })),
        }),
      }),
    );
    await waitFor(() =>
      expect(screen.getByRole("button", { name: "Chuyển sang làm bài ở tab này" })).toBeInTheDocument(),
    );
    await user.click(screen.getByRole("button", { name: "Chuyển sang làm bài ở tab này" }));
    await waitFor(() => expect(pauseSpy).toHaveBeenCalled());
    expect(await screen.findByText("Bài thi đang mở ở một phiên khác")).toBeInTheDocument();

    tab2View.unmount();
    tab1View.unmount();

    // 4b. Immediate stop & eviction on ATTEMPT_SESSION_REVOKED from audioApi.reportProgress
    vi.useFakeTimers({ shouldAdvanceTime: true });
    const fakeUser = userEvent.setup({ advanceTimers: vi.advanceTimersByTime });
    const revokedAudioApi: AttemptAudioApi = {
      ...makePlayingAudioApi(),
      reportProgress: vi.fn().mockRejectedValueOnce(
        new ApiStatusError(409, "Session revoked", { code: "ATTEMPT_SESSION_REVOKED" }),
      ),
    };
    const audioRevokeView = renderWithProviders(
      createElement(AttemptPlayer, {
        attemptId: "att-audio-revoke",
        userId: "user-1",
        deps: makeBaseDeps({
          loadAttemptStateFn: vi.fn(async () => makeAudioAttemptState({ attemptId: "att-audio-revoke" })),
          audioApi: revokedAudioApi,
        }),
      }),
    );
    await waitFor(() => expect(screen.getByRole("button", { name: "Nghe" })).toBeEnabled());
    await fakeUser.click(screen.getByRole("button", { name: "Nghe" }));
    await waitFor(() => expect(screen.getByRole("button", { name: "Đang phát…" })).toBeInTheDocument());

    const audioEl1 = audioRevokeView.container.querySelector("audio")!;
    expect(audioEl1.getAttribute("src")).toContain("play-active");
    pauseSpy.mockClear();

    await act(async () => {
      await vi.advanceTimersByTimeAsync(10_500);
    });

    await waitFor(() =>
      expect(screen.getByText("Lượt thi đã được tiếp tục ở thiết bị khác.")).toBeInTheDocument(),
    );
    expect(pauseSpy).toHaveBeenCalled();
    expect(audioEl1.getAttribute("src")).toBeNull();
    audioRevokeView.unmount();

    // 4c. Immediate stop & eviction on ATTEMPT_SESSION_REVOKED from sendHeartbeatFn
    const hbRevokeFn = vi.fn().mockRejectedValueOnce(
      new ApiStatusError(409, "Heartbeat revoked", { code: "ATTEMPT_SESSION_REVOKED" }),
    );
    const hbRevokeView = renderWithProviders(
      createElement(AttemptPlayer, {
        attemptId: "att-hb-revoke",
        userId: "user-1",
        deps: makeBaseDeps({
          loadAttemptStateFn: vi.fn(async () => makeAudioAttemptState({ attemptId: "att-hb-revoke" })),
          audioApi: makePlayingAudioApi(),
          sendHeartbeatFn: hbRevokeFn,
        }),
      }),
    );
    await waitFor(() => expect(screen.getByRole("button", { name: "Nghe" })).toBeEnabled());
    await fakeUser.click(screen.getByRole("button", { name: "Nghe" }));
    await waitFor(() => expect(screen.getByRole("button", { name: "Đang phát…" })).toBeInTheDocument());

    const audioEl2 = hbRevokeView.container.querySelector("audio")!;
    expect(audioEl2.getAttribute("src")).toContain("play-active");
    pauseSpy.mockClear();

    await act(async () => {
      await vi.advanceTimersByTimeAsync(30_500);
    });

    await waitFor(() =>
      expect(screen.getByText("Lượt thi đã được tiếp tục ở thiết bị khác.")).toBeInTheDocument(),
    );
    expect(hbRevokeFn).toHaveBeenCalled();
    expect(pauseSpy).toHaveBeenCalled();
    expect(audioEl2.getAttribute("src")).toBeNull();
    hbRevokeView.unmount();
  });

  it("FE-R1-03 & FE-R1-10: đối chiếu STALE khi đang nghe giữ nguyên phát audio đang chạy và hỗ trợ MIME chữ hoa", async () => {
    const user = userEvent.setup();
    const { pauseSpy } = mockAudioMediaPrototype();

    const initial = makeAudioAttemptState();
    // Uppercase MIME must still render AttemptAudioPlayer (FE-R1-10)
    initial.parts![0]!.items![0]!.attachments![0]!.mime = "Audio/Mpeg";

    const staleReconcileState = makeAudioAttemptState({
      audioPlays: [
        {
          assetId: "asset-1",
          partId: "p1",
          playId: undefined,
          playsUsed: 0,
          playsLeft: 2,
          durationMs: 45_000,
          positionMs: 0,
          state: "IDLE",
        },
      ],
    });
    staleReconcileState.parts![0]!.items![1]!.answerSeq = 2;
    staleReconcileState.parts![0]!.items![1]!.answer = { type: "SINGLE_CHOICE", optionId: "opt-b" };

    const loadAttemptStateFn = vi
      .fn()
      .mockResolvedValueOnce(initial)
      .mockResolvedValueOnce(staleReconcileState);

    const saveAnswersFn = vi.fn(async () => ({
      results: [{ itemId: "q-audio-1", seq: 1, status: "STALE" as const }],
      serverNow: "2026-11-22T01:00:05Z",
    }));

    const audioApi: AttemptAudioApi = {
      openPlay: vi.fn(async () => ({
        playId: "play-live-1",
        playsUsed: 1,
        playsLeft: 1,
        positionMs: 0,
        durationMs: 45_000,
        state: "PLAYING" as const,
      })),
      resumePlay: vi.fn(),
      reportProgress: vi.fn(async () => ({
        playId: "play-live-1",
        playsUsed: 1,
        playsLeft: 1,
        positionMs: 5_000,
        durationMs: 45_000,
        state: "PLAYING" as const,
      })),
      endPlay: vi.fn(),
    };

    const view = renderWithProviders(
      createElement(AttemptPlayer, {
        attemptId: "att-audio-1",
        userId: "user-1",
        deps: makeBaseDeps({
          loadAttemptStateFn,
          saveAnswersFn,
          audioApi,
        }),
      }),
    );

    await waitFor(() => expect(screen.getByRole("button", { name: "Nghe" })).toBeEnabled());
    await user.click(screen.getByRole("button", { name: "Nghe" }));
    await waitFor(() => expect(screen.getByRole("button", { name: "Đang phát…" })).toBeInTheDocument());

    const audio = view.container.querySelector("audio")!;
    expect(audio.getAttribute("src")).toContain("play-live-1");
    pauseSpy.mockClear();

    // Trigger STALE answer reconciliation while audio is playing
    await user.click(screen.getByRole("radio", { name: /Weather/ }));
    await waitFor(() => expect(loadAttemptStateFn).toHaveBeenCalledTimes(2));
    await waitFor(() => expect(screen.getByRole("radio", { name: /Travel/ })).toBeChecked());

    expect(pauseSpy).not.toHaveBeenCalled();
    expect(audio.getAttribute("src")).toContain("play-live-1");
    expect(screen.getByRole("button", { name: "Đang phát…" })).toBeDisabled();
    expect(screen.getByText("Còn 1/2 lần nghe")).toBeInTheDocument();
  });
});
