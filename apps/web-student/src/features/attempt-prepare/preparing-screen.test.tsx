import { act, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import viMessages from "@/messages/vi.json";
import { renderWithProviders } from "@/test/render";
import { STUDENT_SESSION } from "@/test/storefront-fixtures";
import type { AttemptPrepareResponse, AttemptStateResponse, StartAttemptResponse } from "./api";
import { PrepareView } from "./prepare-view";
import { PreparingScreen } from "./preparing-screen";

vi.mock("next/navigation", () => ({
  useRouter: () => ({ push: vi.fn(), replace: vi.fn() }),
  usePathname: () => "/prepare/ent-1",
}));

describe("PreparingScreen", () => {
  beforeEach(() => {
    vi.useFakeTimers();
    window.localStorage.clear();
    window.sessionStorage.clear();
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  it("hỏi lại trạng thái mỗi 1 giây, gọi onReady khi IN_PROGRESS; quá 90 giây hiện 'Hệ thống đang bận' và hỏi lại mỗi 5 giây (AC-3)", async () => {
    const onReady = vi.fn();
    const pollState = vi
      .fn<(id: string) => Promise<AttemptStateResponse>>()
      .mockResolvedValueOnce({
        attemptId: "att-1",
        state: "STARTING",
        serverNow: "2026-11-21T13:00:01Z",
      })
      .mockResolvedValueOnce({
        attemptId: "att-1",
        state: "IN_PROGRESS",
        serverNow: "2026-11-21T13:00:02Z",
      });

    const firstView = renderWithProviders(
      <PreparingScreen
        attemptId="att-1"
        examName="Đề luyện tập HSA FULL số 01"
        examKindLabel="HSA"
        scopeLabel="Bài thi FULL"
        firstPartName="Phần 1 · Toán học"
        firstPartDurationMinutes={75}
        pollState={pollState}
        onReady={onReady}
      />,
    );

    expect(screen.getByRole("heading", { level: 1, name: "Đang chuẩn bị đề" })).toBeInTheDocument();
    expect(screen.getByText("Chưa tính giờ")).toBeInTheDocument();
    expect(pollState).toHaveBeenCalledTimes(0);

    await act(async () => {
      await vi.advanceTimersByTimeAsync(1000);
    });
    expect(pollState).toHaveBeenCalledTimes(1);
    expect(onReady).not.toHaveBeenCalled();

    await act(async () => {
      await vi.advanceTimersByTimeAsync(1000);
    });
    expect(pollState).toHaveBeenCalledTimes(2);
    expect(onReady).toHaveBeenCalledWith("att-1");
    firstView.unmount();

    // Second scenario: stays STARTING past 90s and survives transient network error
    const slowPoll = vi.fn<(id: string) => Promise<AttemptStateResponse>>(async () => ({
      attemptId: "att-2",
      state: "STARTING",
      serverNow: "2026-11-21T13:02:00Z",
    }));
    slowPoll.mockRejectedValueOnce(new Error("transient network error"));

    renderWithProviders(
      <PreparingScreen
        attemptId="att-2"
        examName="Đề HSA"
        pollState={slowPoll}
        onReady={vi.fn()}
      />,
    );

    // Advance 89s -> 89 polls (1 every 1000ms), still in waiting state
    await act(async () => {
      await vi.advanceTimersByTimeAsync(89_000);
    });
    expect(slowPoll).toHaveBeenCalledTimes(89);
    expect(screen.getByRole("heading", { level: 1, name: "Đang chuẩn bị đề" })).toBeInTheDocument();

    // Advance to 90s -> 90th poll + switches to "Hệ thống đang bận"
    await act(async () => {
      await vi.advanceTimersByTimeAsync(1000);
    });
    expect(slowPoll).toHaveBeenCalledTimes(90);
    expect(screen.getByRole("heading", { level: 1, name: "Hệ thống đang bận" })).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Về Bài thi của tôi" })).toHaveAttribute("href", "/home");

    // Verify 5000ms cadence: +4000ms should NOT fire a poll, +1000ms fires #91, +5000ms fires #92
    await act(async () => {
      await vi.advanceTimersByTimeAsync(4000);
    });
    expect(slowPoll).toHaveBeenCalledTimes(90);

    await act(async () => {
      await vi.advanceTimersByTimeAsync(1000);
    });
    expect(slowPoll).toHaveBeenCalledTimes(91);

    await act(async () => {
      await vi.advanceTimersByTimeAsync(5000);
    });
    expect(slowPoll).toHaveBeenCalledTimes(92);
  });

  it("hiện lý do riêng cho từng mã ABORTED và nút Thử lại (AC-4)", async () => {
    const reasons = [
      ["GENERATION_FAILED", viMessages.attempt.abortReasons.GENERATION_FAILED],
      ["GENERATION_TIMEOUT", viMessages.attempt.abortReasons.GENERATION_TIMEOUT],
      ["GENERATION_BUSY", viMessages.attempt.abortReasons.GENERATION_BUSY],
      ["STALE_STARTING", viMessages.attempt.abortReasons.STALE_STARTING],
      ["AUDIO_ASSET_MISSING", viMessages.attempt.abortReasons.AUDIO_ASSET_MISSING],
      ["AUDIO_ASSET_INVALID", viMessages.attempt.abortReasons.AUDIO_ASSET_INVALID],
    ] as const;

    for (const [reason, message] of reasons) {
      const pollState = vi.fn(async (): Promise<AttemptStateResponse> => ({
        attemptId: `att-${reason}`,
        state: "ABORTED",
        abortReason: reason,
        retryable: true,
        serverNow: "2026-11-21T13:00:01Z",
      }));

      const view = renderWithProviders(
        <PreparingScreen
          attemptId={`att-${reason}`}
          examName="Đề thử nghiệm"
          pollState={pollState}
          onReady={vi.fn()}
          onRetry={vi.fn()}
        />,
      );

      await act(async () => {
        await vi.advanceTimersByTimeAsync(1000);
      });

      expect(screen.getByRole("heading", { level: 1, name: "Chưa chuẩn bị được đề" })).toBeInTheDocument();
      expect(screen.getByText(message)).toBeInTheDocument();
      expect(screen.getByRole("button", { name: "Thử lại" })).toBeInTheDocument();
      view.unmount();
    }
  });

  it("bấm Thử lại gửi Idempotency-Key mới qua startAttempt và chuyển sang lượt mới (AC-4)", async () => {
    vi.useRealTimers();
    const user = userEvent.setup();

    const prepare: AttemptPrepareResponse = {
      entitlementId: "ent-retry",
      examId: "exam-1",
      examCode: "HSA-FULL-01",
      examName: "Đề luyện tập HSA FULL số 01",
      examKind: { id: "k1", code: "HSA", nameVi: "HSA", nameEn: "HSA" },
      scope: "FULL",
      buildMode: "STANDARD",
      timing: "PER_PART",
      durationMinutes: 195,
      navigationMode: "SEQUENTIAL",
      attemptsLeft: 2,
      servingStatus: "PUBLISHED",
      omittedGroups: [],
      parts: [
        {
          partCode: "P1",
          ord: 0,
          name: "Phần 1",
          required: true,
          choiceTiming: "NONE",
          durationMinutes: 75,
          questionCount: 50,
          maxPoints: 50,
        },
      ],
      afterStartChoices: [],
      answerFormats: ["SINGLE_CHOICE"],
    };

    const startCalls: Array<{ body: unknown; idempotencyKey: string }> = [];
    const onStartAttempt = vi.fn(
      async (body: unknown, idempotencyKey: string): Promise<StartAttemptResponse> => {
        startCalls.push({ body, idempotencyKey });
        const attemptId = startCalls.length === 1 ? "att-first" : "att-second";
        return {
          attemptId,
          state: startCalls.length === 1 ? "ABORTED" : "STARTING",
          abortReason: startCalls.length === 1 ? "GENERATION_FAILED" : undefined,
          resumed: false,
          retryable: startCalls.length === 1,
        };
      },
    );

    const pollAttemptState = vi.fn(async (attemptId: string): Promise<AttemptStateResponse> => ({
      attemptId,
      state: "IN_PROGRESS",
      serverNow: "2026-11-21T13:01:00Z",
    }));

    const navigate = vi.fn();

    renderWithProviders(
      <PrepareView
        entitlementId="ent-retry"
        loadCurrentSession={async () => STUDENT_SESSION}
        loadPrepare={async () => prepare}
        onStartAttempt={onStartAttempt}
        pollAttemptState={pollAttemptState}
        navigate={navigate}
      />,
    );

    await user.click(await screen.findByRole("button", { name: "Bắt đầu bài thi" }));
    expect(await screen.findByRole("heading", { level: 1, name: "Chưa chuẩn bị được đề" })).toBeInTheDocument();
    expect(screen.getByText(viMessages.attempt.abortReasons.GENERATION_FAILED)).toBeInTheDocument();

    await user.click(screen.getByRole("button", { name: "Thử lại" }));

    await waitFor(() => expect(startCalls).toHaveLength(2));
    expect(startCalls[0]!.idempotencyKey).toBeTruthy();
    expect(startCalls[1]!.idempotencyKey).toBeTruthy();
    expect(startCalls[1]!.idempotencyKey).not.toBe(startCalls[0]!.idempotencyKey);
    expect(await screen.findByRole("heading", { level: 1, name: "Đang chuẩn bị đề" })).toBeInTheDocument();
  });
});
