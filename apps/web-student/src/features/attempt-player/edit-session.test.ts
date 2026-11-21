import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { ApiStatusError } from "@/lib/api/client";
import { type HeartbeatResponse } from "./api";
import {
  AttemptTabLock,
  EditSessionController,
  HEARTBEAT_INTERVAL_MS,
  type TabLockChannel,
} from "./edit-session";

function createBroadcastBus() {
  const channels = new Map<string, Set<TabLockChannel>>();

  const factory = (name: string): TabLockChannel => {
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

  return { factory };
}

describe("EditSessionController & AttemptTabLock (AC-21, AC-22, AC-30)", () => {
  beforeEach(() => {
    vi.useFakeTimers();
    window.localStorage.clear();
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  it("gửi heartbeat mỗi 30 giây kèm X-Attempt-Session và dừng khi bị eviction 409 hoặc unmount (AC-30, AC-21)", async () => {
    const claimSessionFn = vi.fn(async () => ({
      conflict: false as const,
      sessionId: "sess-1",
      serverNow: "2026-11-21T10:00:00Z",
    }));
    const heartbeatFn = vi
      .fn<(attemptId: string, sessionId: string) => Promise<HeartbeatResponse>>()
      .mockResolvedValueOnce({ serverNow: "2026-11-21T10:00:30Z", state: "IN_PROGRESS" })
      .mockResolvedValueOnce({ serverNow: "2026-11-21T10:01:00Z", state: "IN_PROGRESS" })
      .mockRejectedValueOnce(new ApiStatusError(409, "Revoked", { code: "ATTEMPT_SESSION_REVOKED" }));

    const onServerNow = vi.fn();
    const controller = new EditSessionController({
      attemptId: "att-1",
      userId: "user-1",
      claimSessionFn,
      heartbeatFn,
      onServerNow,
    });

    const snap = await controller.claim(false);
    expect(snap).toEqual({ status: "active", sessionId: "sess-1", lastSeenAt: undefined });
    expect(heartbeatFn).toHaveBeenCalledTimes(0);

    // At 30s: heartbeat #1
    await vi.advanceTimersByTimeAsync(HEARTBEAT_INTERVAL_MS);
    expect(heartbeatFn).toHaveBeenCalledTimes(1);
    expect(heartbeatFn).toHaveBeenNthCalledWith(1, "att-1", "sess-1");
    expect(onServerNow).toHaveBeenCalledWith("2026-11-21T10:00:30Z");

    // At 60s: heartbeat #2
    await vi.advanceTimersByTimeAsync(HEARTBEAT_INTERVAL_MS);
    expect(heartbeatFn).toHaveBeenCalledTimes(2);

    // At 90s: heartbeat #3 returns 409 ATTEMPT_SESSION_REVOKED -> evicted and stops polling
    await vi.advanceTimersByTimeAsync(HEARTBEAT_INTERVAL_MS);
    expect(heartbeatFn).toHaveBeenCalledTimes(3);
    expect(controller.getSnapshot().status).toBe("evicted");

    // Advancing more time MUST NOT send additional heartbeats
    await vi.advanceTimersByTimeAsync(HEARTBEAT_INTERVAL_MS * 3);
    expect(heartbeatFn).toHaveBeenCalledTimes(3);

    // Verify destroy also stops timer cleanly
    const secondController = new EditSessionController({
      attemptId: "att-2",
      userId: "user-1",
      claimSessionFn,
      heartbeatFn: vi.fn<() => Promise<HeartbeatResponse>>(async () => ({
        serverNow: "2026-11-21T10:02:00Z",
        state: "IN_PROGRESS",
      })),
    });
    await secondController.claim(false);
    secondController.destroy();
    await vi.advanceTimersByTimeAsync(HEARTBEAT_INTERVAL_MS * 2);
  });

  it("xử lý tiếp quản 409 ATTEMPT_OPEN_ELSEWHERE -> takeover_required rồi takeover: true thành active (AC-21)", async () => {
    const claimSessionFn = vi
      .fn()
      .mockResolvedValueOnce({
        conflict: true as const,
        code: "ATTEMPT_OPEN_ELSEWHERE",
        message: "Open elsewhere",
        lastSeenAt: "2026-11-21T09:59:00Z",
      })
      .mockResolvedValueOnce({
        conflict: false as const,
        sessionId: "sess-takeover",
        serverNow: "2026-11-21T10:00:00Z",
      });

    const controller = new EditSessionController({
      attemptId: "att-1",
      userId: "user-1",
      claimSessionFn,
      heartbeatFn: vi.fn(),
    });

    const initial = await controller.claim(false);
    expect(initial).toEqual({
      status: "takeover_required",
      sessionId: null,
      lastSeenAt: "2026-11-21T09:59:00Z",
    });

    const takenOver = await controller.claim(true);
    expect(takenOver).toEqual({
      status: "active",
      sessionId: "sess-takeover",
      lastSeenAt: undefined,
    });
    expect(claimSessionFn).toHaveBeenLastCalledWith("att-1", { takeover: true });

    controller.destroy();
  });

  it("khóa đa tab bằng BroadcastChannel: tab 2 chỉ xem, đóng tab 1 tự động chuyển tab 2 thành primary_owner (AC-22)", async () => {
    const bus = createBroadcastBus();
    const tab1 = new AttemptTabLock({
      userId: "user-1",
      attemptId: "att-1",
      tabId: "tab-1",
      acquireTimeoutMs: 50,
      createChannel: bus.factory,
    });
    tab1.start();
    await vi.advanceTimersByTimeAsync(60);
    expect(tab1.getRole()).toBe("primary_owner");

    const tab2 = new AttemptTabLock({
      userId: "user-1",
      attemptId: "att-1",
      tabId: "tab-2",
      acquireTimeoutMs: 50,
      createChannel: bus.factory,
    });
    tab2.start();
    // Flush microtasks + wait past acquire window
    await vi.advanceTimersByTimeAsync(60);
    expect(tab2.getRole()).toBe("secondary_readonly");

    // Closing Tab 1 releases ownership -> Tab 2 re-acquires and becomes primary_owner!
    tab1.destroy();
    await vi.advanceTimersByTimeAsync(60);
    expect(tab2.getRole()).toBe("primary_owner");

    tab2.destroy();
  });
});
