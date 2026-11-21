import { act, fireEvent, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { NextIntlClientProvider } from "next-intl";
import { afterEach, describe, expect, it, vi } from "vitest";
import * as attemptDraft from "@/features/attempt-draft";
import { ApiStatusError } from "@/lib/api/client";
import viMessages from "@/messages/vi.json";
import { QueryProvider } from "@/providers/query-provider";
import { renderWithProviders } from "@/test/render";
import * as attemptApi from "./api";
import { AttemptPlayer, AttemptPlayerPageClient, type AttemptPlayerDeps } from "./attempt-player";
import type { TabLockChannel } from "./edit-session";
import type { AttemptStateResponse } from "./mappers";

vi.mock("next/navigation", () => ({
  useRouter: () => ({ push: vi.fn(), replace: vi.fn() }),
  usePathname: () => "/attempts/att-1",
}));

function makeBaseDeps(overrides: Partial<AttemptPlayerDeps> = {}): AttemptPlayerDeps {
  return {
    claimEditSessionFn: vi.fn(async () => ({
      conflict: false as const,
      sessionId: "sess-1",
      serverNow: "2026-11-21T10:00:00Z",
    })),
    sendHeartbeatFn: vi.fn(async () => ({
      serverNow: "2026-11-21T10:00:30Z",
      state: "IN_PROGRESS" as const,
    })),
    saveAnswersFn: vi.fn(async () => ({
      results: [],
      serverNow: "2026-11-21T10:00:01Z",
    })),
    finishPartFn: vi.fn(async () => ({
      attemptId: "att-1",
      attemptState: "IN_PROGRESS" as const,
      closedPart: { ord: 1, partCode: "PART_1", partId: "part-1", partName: "Phần 1", state: "CLOSED" as const },
      results: [],
      serverNow: "2026-11-21T10:05:00Z",
    })),
    submitAttemptFn: vi.fn(async () => ({
      attemptId: "att-1",
      state: "GRADING" as const,
      parts: [],
      results: [],
      serverNow: "2026-11-21T10:10:00Z",
    })),
    confirmPartChoiceFn: vi.fn(async () => ({
      attemptId: "att-1",
      attemptState: "IN_PROGRESS" as const,
      generationTries: 0,
      partCode: "PART_3",
      partId: "part-3",
      state: "GENERATING" as const,
      serverNow: "2026-11-21T10:06:00Z",
    })),
    retryPartGenerationFn: vi.fn(async () => ({
      attemptId: "att-1",
      attemptState: "IN_PROGRESS" as const,
      generationTries: 1,
      partCode: "PART_3",
      partId: "part-3",
      state: "GENERATING" as const,
      serverNow: "2026-11-21T10:07:00Z",
    })),
    loadAfterStartChoicesFn: vi.fn(async () => [
      {
        ord: 3,
        partCode: "PART_3",
        partName: "Phần 3 · Tự chọn",
        options: [{ code: "OPT_A", name: "Tổ hợp A" }],
      },
    ]),
    loadDraftsFn: vi.fn(async () => []),
    saveItemDraftFn: vi.fn(async () => undefined),
    deleteItemDraftFn: vi.fn(async () => undefined),
    saveAttemptMetaFn: vi.fn(async () => undefined),
    loadAttemptMetaFn: vi.fn(async () => null),
    clearAttemptDraftsFn: vi.fn(async () => undefined),
    loadAttemptStartContextFn: vi.fn(async () => null),
    saveAttemptStartContextFn: vi.fn(async () => undefined),
    startAttemptFn: vi.fn(async () => ({
      attemptId: "att-retried",
      state: "STARTING" as const,
      resumed: false,
      retryable: false,
    })),
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

function makeOpenMcqItem(itemId: string, displayNo: number, stem: string) {
  return {
    itemId,
    displayNo,
    unitCount: 1,
    content: stem,
    questionType: "SINGLE_CHOICE" as const,
    answerSeq: 0,
    flagged: false,
    questionBody: {
      type: "SINGLE_CHOICE" as const,
      options: [
        { id: `${itemId}-a`, content: "Lựa chọn 1" },
        { id: `${itemId}-b`, content: "Lựa chọn 2" },
        { id: `${itemId}-c`, content: "Lựa chọn 3" },
        { id: `${itemId}-d`, content: "Lựa chọn 4" },
      ],
    },
  };
}

describe("AttemptPlayer (AC-7, AC-20, AC-28, AC-29)", () => {
  afterEach(() => {
    vi.restoreAllMocks();
    vi.useRealTimers();
  });

  it("SEQUENTIAL flow: khóa chuyển phần trước, hoàn thành phần 1 rồi sang phần 2 với displayNo liên tục và chỉ hiện Nộp bài ở phần cuối (AC-7, FE-R1-6)", async () => {
    const user = userEvent.setup();

    const statePart1Open: AttemptStateResponse = {
      attemptId: "att-1",
      state: "IN_PROGRESS",
      navigationMode: "SEQUENTIAL",
      timing: "PER_PART",
      examName: "Đề HSA thử nghiệm",
      serverNow: "2026-11-21T10:00:00Z",
      parts: [
        {
          ord: 1,
          partId: "part-1",
          partName: "Phần 1 · Toán",
          state: "OPEN",
          deadlineAt: "2026-11-21T11:00:00Z",
          items: [makeOpenMcqItem("item-1", 1, "Câu toán số 1")],
        },
        {
          ord: 2,
          partId: "part-2",
          partName: "Phần 2 · Văn",
          state: "PENDING",
          items: [],
        },
      ],
    };

    const statePart2Open: AttemptStateResponse = {
      attemptId: "att-1",
      state: "IN_PROGRESS",
      navigationMode: "SEQUENTIAL",
      timing: "PER_PART",
      examName: "Đề HSA thử nghiệm",
      serverNow: "2026-11-21T10:05:00Z",
      parts: [
        {
          ord: 1,
          partId: "part-1",
          partName: "Phần 1 · Toán",
          state: "CLOSED",
          items: [],
        },
        {
          ord: 2,
          partId: "part-2",
          partName: "Phần 2 · Văn",
          state: "OPEN",
          deadlineAt: "2026-11-21T11:00:00Z",
          items: [makeOpenMcqItem("item-2", 2, "Câu văn số 2")],
        },
      ],
    };

    const loadAttemptStateFn = vi
      .fn<() => Promise<AttemptStateResponse>>()
      .mockResolvedValueOnce(statePart1Open)
      .mockResolvedValueOnce(statePart2Open);

    const finishPartFn = vi.fn(async () => ({
      attemptId: "att-1",
      attemptState: "IN_PROGRESS" as const,
      closedPart: { ord: 1, partCode: "PART_1", partId: "part-1", partName: "Phần 1 · Toán", state: "CLOSED" as const },
      results: [],
      serverNow: "2026-11-21T10:05:00Z",
    }));

    const deps = makeBaseDeps({ loadAttemptStateFn, finishPartFn });
    renderWithProviders(<AttemptPlayer attemptId="att-1" userId="user-1" deps={deps} />);

    expect(await screen.findByText("Câu toán số 1")).toBeInTheDocument();
    expect(screen.queryByRole("tablist")).not.toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Hoàn thành phần" })).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Nộp bài" })).not.toBeInTheDocument();

    await user.click(screen.getByRole("button", { name: "Hoàn thành phần" }));
    expect(screen.getByRole("dialog")).toBeInTheDocument();

    await user.click(screen.getByRole("button", { name: "Hoàn thành phần" }));

    await waitFor(() => {
      expect(finishPartFn).toHaveBeenCalledWith("att-1", "part-1", "sess-1", { finalAnswers: [] });
    });

    expect(await screen.findByText("Câu văn số 2")).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Hoàn thành phần" })).not.toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Nộp bài" })).toBeInTheDocument();
  });

  it("nộp bài gửi finalAnswers cho câu chờ lưu chưa đồng bộ và chuyển sang màn đã đóng với Link prefetch=false (AC-20)", async () => {
    const user = userEvent.setup();

    const stateOpen: AttemptStateResponse = {
      attemptId: "att-1",
      state: "IN_PROGRESS",
      navigationMode: "FREE",
      timing: "SHARED",
      deadlineAt: "2026-11-21T11:00:00Z",
      examName: "Đề kiểm tra tổng hợp",
      serverNow: "2026-11-21T10:00:00Z",
      parts: [
        {
          ord: 1,
          partId: "part-1",
          partName: "Phần 1",
          state: "OPEN",
          items: [
            makeOpenMcqItem("item-1", 1, "Câu hỏi nộp bài"),
            {
              itemId: "item-2",
              displayNo: 2,
              unitCount: 1,
              content: "Điền kết quả câu 2",
              questionType: "SHORT_ANSWER",
              answerSeq: 0,
              flagged: false,
              questionBody: {
                type: "SHORT_ANSWER",
              },
            },
          ],
        },
      ],
    };

    const stateGrading: AttemptStateResponse = {
      attemptId: "att-1",
      state: "GRADING",
      examName: "Đề kiểm tra tổng hợp",
      serverNow: "2026-11-21T10:02:00Z",
      parts: [],
    };

    const loadAttemptStateFn = vi
      .fn<() => Promise<AttemptStateResponse>>()
      .mockResolvedValueOnce(stateOpen)
      .mockResolvedValueOnce(stateGrading);

    // Keep saveAnswers failing/rejecting transiently so item remains dirty in queue before submit
    const saveAnswersFn = vi.fn(async () => {
      throw new Error("Network down");
    });

    const submitAttemptFn = vi.fn<NonNullable<AttemptPlayerDeps["submitAttemptFn"]>>(async () => ({
      attemptId: "att-1",
      state: "GRADING" as const,
      parts: [],
      results: [],
      serverNow: "2026-11-21T10:02:00Z",
    }));

    const clearAttemptDraftsFn = vi.fn(async () => undefined);

    const deps = makeBaseDeps({
      loadAttemptStateFn,
      saveAnswersFn,
      submitAttemptFn,
      clearAttemptDraftsFn,
    });

    renderWithProviders(<AttemptPlayer attemptId="att-1" userId="user-1" deps={deps} />);
    expect(await screen.findByText("Câu hỏi nộp bài")).toBeInTheDocument();

    // Select an option — saveAnswers throws, leaving item-1 dirty with seq=1 in saveQueue
    await user.click(screen.getByText("Lựa chọn 2"));

    // Open submit modal and confirm
    await user.click(screen.getByRole("button", { name: "Nộp bài" }));
    expect(screen.getByText("1/2 câu đã làm")).toBeInTheDocument();

    await user.click(screen.getByRole("button", { name: "Nộp bài" }));

    await waitFor(() => {
      expect(submitAttemptFn).toHaveBeenCalledTimes(1);
    });

    const submittedBody = submitAttemptFn.mock.calls[0]?.[3];
    expect(submittedBody?.finalAnswers).toEqual([
      {
        itemId: "item-1",
        seq: 1,
        answer: { type: "SINGLE_CHOICE", optionId: "item-1-b" },
      },
    ]);

    expect(await screen.findByText("Lượt thi đã đóng")).toBeInTheDocument();
    expect(clearAttemptDraftsFn).toHaveBeenCalledWith("user-1", "att-1");
    expect(screen.getByRole("link", { name: "Xem kết quả" })).toHaveAttribute(
      "href",
      "/attempts/att-1/result",
    );
  });

  it.each([
    { state: "STARTING" as const, expectedHeading: "Đang chuẩn bị đề" },
    { state: "ABORTED" as const, expectedHeading: "Chưa chuẩn bị được đề" },
    { state: "GRADING" as const, expectedHeading: "Lượt thi đã đóng" },
    { state: "PENDING_REVIEW" as const, expectedHeading: "Lượt thi đã đóng" },
    { state: "SCORED" as const, expectedHeading: "Lượt thi đã đóng" },
    { state: "GRADING_ERROR" as const, expectedHeading: "Lượt thi đã đóng" },
  ])("khôi phục trạng thái LT §7.4 cho $state không bị 404 (AC-28)", async ({ state, expectedHeading }) => {
    const loadAttemptStateFn = vi.fn(async (): Promise<AttemptStateResponse> => ({
      attemptId: "att-state",
      state,
      abortReason: state === "ABORTED" ? "GENERATION_FAILED" : undefined,
      examName: "Đề khôi phục",
      serverNow: "2026-11-21T10:00:00Z",
      parts: [],
    }));

    const deps = makeBaseDeps({ loadAttemptStateFn });
    renderWithProviders(<AttemptPlayer attemptId="att-state" userId="user-1" deps={deps} />);

    expect(await screen.findByRole("heading", { name: expectedHeading })).toBeInTheDocument();
  });

  it("phục hồi đúng phần AWAITING_CHOICE, GENERATING, GENERATION_FAILED khi F5 (AC-28)", async () => {
    const awaitingState: AttemptStateResponse = {
      attemptId: "att-c",
      state: "IN_PROGRESS",
      examName: "Đề có phần 3",
      serverNow: "2026-11-21T10:00:00Z",
      parts: [
        { ord: 1, partId: "p1", state: "CLOSED", items: [] },
        { ord: 3, partId: "p3", state: "AWAITING_CHOICE", items: [] },
      ],
    };

    const view = renderWithProviders(
      <AttemptPlayer
        attemptId="att-c"
        userId="user-1"
        deps={makeBaseDeps({ loadAttemptStateFn: vi.fn(async () => awaitingState) })}
      />,
    );
    expect(await screen.findByText("Chọn phần thi thứ ba")).toBeInTheDocument();
    view.unmount();

    const genState: AttemptStateResponse = {
      ...awaitingState,
      parts: [{ ord: 3, partId: "p3", state: "GENERATING", items: [] }],
    };
    const genView = renderWithProviders(
      <AttemptPlayer
        attemptId="att-g"
        userId="user-1"
        deps={makeBaseDeps({ loadAttemptStateFn: vi.fn(async () => genState) })}
      />,
    );
    expect(await screen.findByText("Đang chuẩn bị bộ câu hỏi cho phần thi")).toBeInTheDocument();
    genView.unmount();

    const failState: AttemptStateResponse = {
      ...awaitingState,
      parts: [{ ord: 3, partId: "p3", state: "GENERATION_FAILED", items: [] }],
    };
    renderWithProviders(
      <AttemptPlayer
        attemptId="att-f"
        userId="user-1"
        deps={makeBaseDeps({ loadAttemptStateFn: vi.fn(async () => failState) })}
      />,
    );
    expect(await screen.findByText("Chưa chuẩn bị được phần thi")).toBeInTheDocument();
  });

  it("phím tắt 1-4 chọn SINGLE_CHOICE, ArrowRight/n & ArrowLeft/p đổi câu, f gắn cờ và bỏ qua khi focus ở ô nhập (AC-29)", async () => {
    const user = userEvent.setup();
    const saveAnswersFn = vi.fn(async () => ({
      results: [{ itemId: "q1", seq: 1, status: "SAVED" as const }],
      serverNow: "2026-11-21T10:00:01Z",
    }));

    const stateOpen: AttemptStateResponse = {
      attemptId: "att-kbd",
      state: "IN_PROGRESS",
      navigationMode: "FREE",
      examName: "Đề phím tắt",
      serverNow: "2026-11-21T10:00:00Z",
      parts: [
        {
          ord: 1,
          partId: "part-1",
          partName: "Phần 1",
          state: "OPEN",
          items: [
            makeOpenMcqItem("q1", 1, "Câu trắc nghiệm số 1"),
            {
              itemId: "q2",
              displayNo: 2,
              unitCount: 1,
              content: "Câu trả lời ngắn số 2",
              questionType: "SHORT_ANSWER",
              answerSeq: 0,
              flagged: false,
              questionBody: {
                type: "SHORT_ANSWER",
              },
            },
          ],
        },
      ],
    };

    renderWithProviders(
      <AttemptPlayer
        attemptId="att-kbd"
        userId="user-1"
        deps={makeBaseDeps({
          loadAttemptStateFn: vi.fn(async () => stateOpen),
          saveAnswersFn,
        })}
      />,
    );

    expect(await screen.findByText("Câu trắc nghiệm số 1")).toBeInTheDocument();

    // Wait for claimEditSession to finish before triggering 0ms save via shortcut
    await waitFor(() => {
      expect(
        screen.getByText("Phím tắt: 1–4 chọn phương án · ← → chuyển câu · F đánh dấu xem lại"),
      ).toBeInTheDocument();
    });

    // Press "2" to pick option 2 on SINGLE_CHOICE
    await user.keyboard("2");
    await waitFor(() => {
      expect(saveAnswersFn).toHaveBeenCalledWith(
        "att-kbd",
        "sess-1",
        expect.objectContaining({
          changes: [
            { itemId: "q1", seq: 1, answer: { type: "SINGLE_CHOICE", optionId: "q1-b" } },
          ],
        }),
      );
    });

    // Press "f" to toggle flag
    await user.keyboard("f");
    expect(screen.getByRole("button", { name: /^1,/ })).toHaveAttribute("data-flagged", "true");

    // Press "n" / ArrowRight to go to Q2 (SHORT_ANSWER input)
    await user.keyboard("n");
    expect(await screen.findByText("Câu trả lời ngắn số 2")).toBeInTheDocument();

    // Focus the textbox and type "1pnf" — MUST NOT change question or toggle flag
    const textInput = screen.getByRole("textbox");
    await user.click(textInput);
    await user.keyboard("1pnf");
    expect(textInput).toHaveValue("1pnf");
    expect(screen.getByText("Câu trả lời ngắn số 2")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: /^2,/ })).toHaveAttribute("data-flagged", "false");

    // Blur back to document body and press ArrowLeft -> moves back to Q1
    textInput.blur();
    await user.keyboard("{ArrowLeft}");
    expect(await screen.findByText("Câu trắc nghiệm số 1")).toBeInTheDocument();
  });

  it("render không truyền deps không gây vòng lặp bootstrap (FE-R1-1) và không lặp tiêu đề Câu N (FE-R1-16)", async () => {
    const user = userEvent.setup();
    const stateOpen: AttemptStateResponse = {
      attemptId: "att-no-deps",
      state: "IN_PROGRESS",
      navigationMode: "FREE",
      timing: "SHARED",
      deadlineAt: "2026-11-21T11:00:00Z",
      examName: "Đề kiểm tra ổn định",
      serverNow: "2026-11-21T10:00:00Z",
      parts: [
        {
          ord: 1,
          partId: "part-1",
          partName: "Phần 1",
          state: "OPEN",
          items: [makeOpenMcqItem("q-stable", 1, "Câu hỏi no-deps")],
        },
      ],
    };

    const loadStateSpy = vi.spyOn(attemptApi, "loadAttemptState").mockResolvedValue(stateOpen);
    const claimSpy = vi.spyOn(attemptApi, "claimEditSession").mockResolvedValue({
      conflict: false,
      sessionId: "sess-stable",
      serverNow: "2026-11-21T10:00:00Z",
    });
    const saveSpy = vi.spyOn(attemptApi, "saveAnswers").mockResolvedValue({
      results: [{ itemId: "q-stable", seq: 1, status: "SAVED" }],
      serverNow: "2026-11-21T10:00:01Z",
    });
    vi.spyOn(attemptApi, "sendHeartbeat").mockResolvedValue({
      serverNow: "2026-11-21T10:00:30Z",
      state: "IN_PROGRESS",
    });
    vi.spyOn(attemptDraft, "loadAttemptDrafts").mockResolvedValue([]);
    vi.spyOn(attemptDraft, "loadAttemptMeta").mockResolvedValue(null);
    vi.spyOn(attemptDraft, "saveItemDraft").mockResolvedValue(undefined);
    vi.spyOn(attemptDraft, "deleteItemDraft").mockResolvedValue(undefined);
    vi.spyOn(attemptDraft, "saveAttemptMeta").mockResolvedValue(undefined);

    renderWithProviders(<AttemptPlayer attemptId="att-no-deps" userId="user-1" />);

    expect(await screen.findByText("Câu hỏi no-deps")).toBeInTheDocument();
    // Single Câu 1 heading from QuestionFrame (no duplicate <p>Câu 1</p>)
    expect(screen.getAllByText("Câu 1")).toHaveLength(1);

    await user.click(screen.getByText("Lựa chọn 1"));
    await waitFor(() => expect(saveSpy).toHaveBeenCalledTimes(1));
    await new Promise((r) => setTimeout(r, 180));

    expect(loadStateSpy).toHaveBeenCalledTimes(1);
    expect(claimSpy).toHaveBeenCalledTimes(1);
  });

  it("BroadcastChannel tab lock sau timeout vẫn gọi claimEditSession và gửi sessionId khi lưu (FE-R1-2)", async () => {
    const user = userEvent.setup();
    const bus = createInMemoryTabLockBus();

    const stateOpen: AttemptStateResponse = {
      attemptId: "att-bc",
      state: "IN_PROGRESS",
      navigationMode: "FREE",
      examName: "Đề BroadcastChannel",
      serverNow: "2026-11-21T10:00:00Z",
      parts: [
        {
          ord: 1,
          partId: "part-1",
          partName: "Phần 1",
          state: "OPEN",
          items: [makeOpenMcqItem("q-bc", 1, "Câu hỏi tab lock")],
        },
      ],
    };

    const claimEditSessionFn = vi.fn(async () => ({
      conflict: false as const,
      sessionId: "sess-bc-1",
      serverNow: "2026-11-21T10:00:00Z",
    }));
    const saveAnswersFn = vi.fn(async () => ({
      results: [{ itemId: "q-bc", seq: 1, status: "SAVED" as const }],
      serverNow: "2026-11-21T10:00:01Z",
    }));

    const deps = makeBaseDeps({
      loadAttemptStateFn: vi.fn(async () => stateOpen),
      claimEditSessionFn,
      saveAnswersFn,
      createTabLockChannelFn: bus,
    });

    renderWithProviders(<AttemptPlayer attemptId="att-bc" userId="user-1" deps={deps} />);
    expect(await screen.findByText("Câu hỏi tab lock")).toBeInTheDocument();

    await waitFor(() => expect(claimEditSessionFn).toHaveBeenCalledTimes(1), { timeout: 2000 });

    await user.click(screen.getByText("Lựa chọn 1"));
    await waitFor(() =>
      expect(saveAnswersFn).toHaveBeenCalledWith(
        "att-bc",
        "sess-bc-1",
        expect.objectContaining({
          changes: [{ itemId: "q-bc", seq: 1, answer: { type: "SINGLE_CHOICE", optionId: "q-bc-a" } }],
        }),
      ),
    );
  });

  it("tự đồng bộ bản nháp IndexedDB ngay khi claim session xong (FE-R1-7)", async () => {
    const stateOpen: AttemptStateResponse = {
      attemptId: "att-hydrate",
      state: "IN_PROGRESS",
      navigationMode: "FREE",
      examName: "Đề khôi phục nháp",
      serverNow: "2026-11-21T10:00:00Z",
      parts: [
        {
          ord: 1,
          partId: "part-1",
          partName: "Phần 1",
          state: "OPEN",
          items: [makeOpenMcqItem("q-hyd", 1, "Câu nháp IndexedDB")],
        },
      ],
    };

    const saveAnswersFn = vi.fn(async () => ({
      results: [{ itemId: "q-hyd", seq: 2, status: "SAVED" as const }],
      serverNow: "2026-11-21T10:00:02Z",
    }));

    const deps = makeBaseDeps({
      loadAttemptStateFn: vi.fn(async () => stateOpen),
      saveAnswersFn,
      loadDraftsFn: vi.fn(async () => [
        {
          id: "user-1:att-hydrate:q-hyd",
          userId: "user-1",
          attemptId: "att-hydrate",
          itemId: "q-hyd",
          seq: 2,
          syncedSeq: 0,
          answer: { type: "SINGLE_CHOICE" as const, optionId: "q-hyd-c" },
          flagged: false,
          dirtyAnswer: true,
          dirtyFlag: false,
          updatedAt: Date.now(),
        },
      ]),
    });

    renderWithProviders(<AttemptPlayer attemptId="att-hydrate" userId="user-1" deps={deps} />);
    expect(await screen.findByText("Câu nháp IndexedDB")).toBeInTheDocument();

    await waitFor(() =>
      expect(saveAnswersFn).toHaveBeenCalledWith(
        "att-hydrate",
        "sess-1",
        expect.objectContaining({
          changes: [{ itemId: "q-hyd", seq: 2, answer: { type: "SINGLE_CHOICE", optionId: "q-hyd-c" } }],
        }),
      ),
    );
    expect(await screen.findByText("Đã lưu")).toBeInTheDocument();
  });

  it("chuyển câu và đổi phần FREE gửi ngay câu đang debounce kèm vị trí mới, và dọn timer trước finish/submit (FE-R1-8)", async () => {
    const user = userEvent.setup();
    const stateFree: AttemptStateResponse = {
      attemptId: "att-nav",
      state: "IN_PROGRESS",
      navigationMode: "FREE",
      timing: "SHARED",
      deadlineAt: "2026-11-21T11:00:00Z",
      examName: "Đề điều hướng tự do",
      serverNow: "2026-11-21T10:00:00Z",
      parts: [
        {
          ord: 1,
          partId: "p-1",
          partName: "Phần 1",
          state: "OPEN",
          items: [
            {
              itemId: "sa-1",
              displayNo: 1,
              unitCount: 1,
              content: "Điền số câu 1",
              questionType: "SHORT_ANSWER",
              answerSeq: 0,
              flagged: false,
              questionBody: { type: "SHORT_ANSWER" },
            },
            {
              itemId: "sa-2",
              displayNo: 2,
              unitCount: 1,
              content: "Điền số câu 2",
              questionType: "SHORT_ANSWER",
              answerSeq: 0,
              flagged: false,
              questionBody: { type: "SHORT_ANSWER" },
            },
          ],
        },
        {
          ord: 2,
          partId: "p-2",
          partName: "Phần 2",
          state: "OPEN",
          items: [
            {
              itemId: "sa-3",
              displayNo: 3,
              unitCount: 1,
              content: "Điền số câu 3",
              questionType: "SHORT_ANSWER",
              answerSeq: 0,
              flagged: false,
              questionBody: { type: "SHORT_ANSWER" },
            },
          ],
        },
      ],
    };

    const saveAnswersFn = vi.fn(async (_a: string, _s: string, body: attemptApi.SaveAnswersRequest) => ({
      results: (body.changes ?? []).map((c) => ({
        itemId: c.itemId,
        seq: c.seq,
        status: "SAVED" as const,
      })),
      serverNow: "2026-11-21T10:00:05Z",
    }));

    const submitAttemptFn = vi.fn(async () => ({
      attemptId: "att-nav",
      state: "GRADING" as const,
      endReason: "SUBMITTED" as const,
      parts: [],
      results: [{ itemId: "sa-3", seq: 1, status: "SAVED" as const }],
      serverNow: "2026-11-21T10:01:00Z",
    }));

    const loadAttemptStateFn = vi
      .fn<() => Promise<AttemptStateResponse>>()
      .mockResolvedValueOnce(stateFree)
      .mockResolvedValue({ ...stateFree, state: "GRADING" });

    renderWithProviders(
      <AttemptPlayer
        attemptId="att-nav"
        userId="user-1"
        deps={makeBaseDeps({ loadAttemptStateFn, saveAnswersFn, submitAttemptFn })}
      />,
    );

    expect(await screen.findByText("Điền số câu 1")).toBeInTheDocument();
    await waitFor(() => expect(screen.getByRole("textbox")).toBeEnabled());

    // Type in SHORT_ANSWER (800ms debounce) then immediately click Next -> flushes immediately with position!
    await user.type(screen.getByRole("textbox"), "42");
    await user.click(screen.getByRole("button", { name: "Câu sau" }));

    await waitFor(() =>
      expect(saveAnswersFn).toHaveBeenCalledWith(
        "att-nav",
        "sess-1",
        expect.objectContaining({
          changes: [{ itemId: "sa-1", seq: 2, answer: { type: "SHORT_ANSWER", text: "42" } }],
          position: { partId: "p-1", itemId: "sa-2" },
        }),
      ),
    );

    // Type in Q2 then immediately switch tab to Phần 2 -> flushes Q2 AND persists Phần 2 position!
    await user.type(screen.getByRole("textbox"), "99");
    await user.click(screen.getByRole("tab", { name: /Phần 2/ }));

    await waitFor(() =>
      expect(saveAnswersFn).toHaveBeenCalledWith(
        "att-nav",
        "sess-1",
        expect.objectContaining({
          changes: [{ itemId: "sa-2", seq: 2, answer: { type: "SHORT_ANSWER", text: "99" } }],
          position: { partId: "p-2", itemId: "sa-3" },
        }),
      ),
    );

    // Type in Q3 and submit immediately (before 800ms debounce) -> timer cancelled, no extra PUT after submit
    const callsBeforeQ3Submit = saveAnswersFn.mock.calls.length;
    await user.type(screen.getByRole("textbox"), "7");
    await user.click(screen.getByRole("button", { name: "Nộp bài" }));
    await user.click(screen.getByRole("dialog").querySelectorAll("button")[1]!);

    await waitFor(() => expect(submitAttemptFn).toHaveBeenCalledTimes(1));
    await new Promise((r) => setTimeout(r, 900));
    expect(saveAnswersFn.mock.calls.length).toBe(callsBeforeQ3Submit);
  });

  it("các liên kết thoát trỏ về /home (FE-R1-5) và phím tắt 1-4 dùng optionOrder (FE-R1-18) và TakeoverDialog định dạng lastSeenAt (FE-R1-16)", async () => {
    const user = userEvent.setup();
    const navigateFn = vi.fn();

    // 1. Not found -> href="/home"
    const notFoundView = renderWithProviders(
      <AttemptPlayer
        attemptId="att-404"
        userId="user-1"
        deps={makeBaseDeps({
          loadAttemptStateFn: vi.fn(async () => {
            throw new ApiStatusError(404, "Not found", { code: "ATTEMPT_NOT_FOUND" });
          }),
        })}
      />,
    );
    expect(await screen.findByRole("link", { name: "Về Bài thi của tôi" })).toHaveAttribute("href", "/home");
    notFoundView.unmount();

    // 2. Closed -> href="/home"
    const closedView = renderWithProviders(
      <AttemptPlayer
        attemptId="att-closed"
        userId="user-1"
        deps={makeBaseDeps({
          loadAttemptStateFn: vi.fn(async () => ({
            attemptId: "att-closed",
            state: "SCORED" as const,
            serverNow: "2026-11-21T10:00:00Z",
            parts: [],
          })),
        })}
      />,
    );
    expect(await screen.findByRole("link", { name: "Về Bài thi của tôi" })).toHaveAttribute("href", "/home");
    closedView.unmount();

    // 3. Takeover conflict -> formatted lastSeenAt + Cancel navigates to /home
    const takeoverState: AttemptStateResponse = {
      attemptId: "att-take",
      state: "IN_PROGRESS",
      examName: "Đề tiếp quản",
      serverNow: "2026-11-21T10:00:00Z",
      parts: [{ ord: 1, partId: "p1", state: "OPEN", items: [makeOpenMcqItem("qt", 1, "Câu tiếp quản")] }],
    };
    const takeoverView = renderWithProviders(
      <AttemptPlayer
        attemptId="att-take"
        userId="user-1"
        deps={makeBaseDeps({
          loadAttemptStateFn: vi.fn(async () => takeoverState),
          claimEditSessionFn: vi.fn(async () => ({
            conflict: true as const,
            code: "ATTEMPT_OPEN_ELSEWHERE",
            message: "Open elsewhere",
            lastSeenAt: "2026-11-21T09:59:00Z",
          })),
          navigateFn,
        })}
      />,
    );
    expect(await screen.findByText("Bài thi đang mở ở một phiên khác")).toBeInTheDocument();
    expect(screen.queryByText("2026-11-21T09:59:00Z")).not.toBeInTheDocument();
    const lastSeenTime = document.querySelector('time[datetime="2026-11-21T09:59:00Z"]');
    expect(lastSeenTime).toBeInTheDocument();
    expect(lastSeenTime).toHaveTextContent(/16:59.*21\/11\/2026|21\/11\/2026.*16:59/);
    await user.click(screen.getByRole("button", { name: "Hủy" }));
    expect(navigateFn).toHaveBeenCalledWith("/home");
    takeoverView.unmount();

    // 4. Keyboard 1-4 respects optionOrder (FE-R1-18)
    const reorderedState: AttemptStateResponse = {
      attemptId: "att-ord",
      state: "IN_PROGRESS",
      navigationMode: "FREE",
      examName: "Đề đảo phương án",
      serverNow: "2026-11-21T10:00:00Z",
      parts: [
        {
          ord: 1,
          partId: "p1",
          state: "OPEN",
          items: [
            {
              ...makeOpenMcqItem("q-ord", 1, "Câu đảo thứ tự"),
              optionOrder: ["q-ord-d", "q-ord-c", "q-ord-b", "q-ord-a"],
            },
          ],
        },
      ],
    };
    const saveAnswersFn = vi.fn(async () => ({
      results: [{ itemId: "q-ord", seq: 1, status: "SAVED" as const }],
      serverNow: "2026-11-21T10:00:01Z",
    }));
    renderWithProviders(
      <AttemptPlayer
        attemptId="att-ord"
        userId="user-1"
        deps={makeBaseDeps({ loadAttemptStateFn: vi.fn(async () => reorderedState), saveAnswersFn })}
      />,
    );
    expect(await screen.findByText("Câu đảo thứ tự")).toBeInTheDocument();
    await waitFor(() =>
      expect(
        screen.getByText("Phím tắt: 1–4 chọn phương án · ← → chuyển câu · F đánh dấu xem lại"),
      ).toBeInTheDocument(),
    );
    await user.keyboard("1");
    await waitFor(() =>
      expect(saveAnswersFn).toHaveBeenCalledWith(
        "att-ord",
        "sess-1",
        expect.objectContaining({
          changes: [{ itemId: "q-ord", seq: 1, answer: { type: "SINGLE_CHOICE", optionId: "q-ord-d" } }],
        }),
      ),
    );
  });

  it("AttemptPlayerPageClient chuyển /login?callbackUrl= khi 401 và hiện màn lỗi khi 500, mid-attempt 401 giữ nguyên nháp IndexedDB (FE-R1-9)", async () => {
    const navigateAuth = vi.fn();
    const refreshSpy = vi.fn(async () => {
      throw new ApiStatusError(401, "Expired");
    });

    // Render AttemptPlayerPageClient with QueryProvider on 500 -> shows error+retry view (no /login redirect)
    const errView = renderWithProviders(
      <AttemptPlayerPageClient
        attemptId="att-500"
        loadSessionFn={async () => {
          throw new ApiStatusError(500, "Server error");
        }}
      />,
    );
    expect(await screen.findByRole("heading", { name: "Không tải được lượt thi" })).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Về Bài thi của tôi" })).toHaveAttribute("href", "/home");
    errView.unmount();

    // Render AttemptPlayerPageClient with QueryProvider on 401 -> redirects to /login?callbackUrl=...
    const sessionApi = await import("@/features/session/api");
    const refreshModuleSpy = vi.spyOn(sessionApi, "refreshSession").mockImplementation(refreshSpy);
    const qView = renderWithProviders(
      <NextIntlClientProvider locale="vi" messages={viMessages} timeZone="Asia/Ho_Chi_Minh">
        <QueryProvider navigate={navigateAuth}>
          <AttemptPlayerPageClient
            attemptId="att-401"
            loadSessionFn={async () => {
              throw new ApiStatusError(401, "Unauthorized");
            }}
          />
        </QueryProvider>
      </NextIntlClientProvider>,
    );
    await waitFor(() =>
      expect(navigateAuth).toHaveBeenCalledWith(
        `http://localhost:3000/login?callbackUrl=${encodeURIComponent(window.location.href)}`,
      ),
    );
    refreshModuleSpy.mockRestore();
    qView.unmount();

    // Mid-attempt 401 preserves IDB drafts (clearAttemptDraftsFn NOT called) and redirects with callbackUrl
    const user = userEvent.setup();
    const midState: AttemptStateResponse = {
      attemptId: "att-mid-401",
      state: "IN_PROGRESS",
      navigationMode: "FREE",
      examName: "Đề hết phiên giữa chừng",
      serverNow: "2026-11-21T10:00:00Z",
      parts: [{ ord: 1, partId: "p1", state: "OPEN", items: [makeOpenMcqItem("qm", 1, "Câu hết phiên")] }],
    };
    const clearAttemptDraftsFn = vi.fn(async () => undefined);
    const midNavigate = vi.fn();
    renderWithProviders(
      <AttemptPlayer
        attemptId="att-mid-401"
        userId="user-1"
        deps={makeBaseDeps({
          loadAttemptStateFn: vi.fn(async () => midState),
          saveAnswersFn: vi.fn(async () => {
            throw new ApiStatusError(401, "Expired");
          }),
          refreshSessionFn: vi.fn(async () => {
            throw new ApiStatusError(401, "Expired");
          }),
          clearAttemptDraftsFn,
          navigateFn: midNavigate,
        })}
      />,
    );
    expect(await screen.findByText("Câu hết phiên")).toBeInTheDocument();
    await user.click(screen.getByText("Lựa chọn 1"));

    await waitFor(() =>
      expect(midNavigate).toHaveBeenCalledWith(
        `http://localhost:3000/login?callbackUrl=${encodeURIComponent(window.location.href)}`,
      ),
    );
    expect(clearAttemptDraftsFn).not.toHaveBeenCalled();
  });

  it("ABORTED trên /attempts/[id] chỉ render 1 <main>, nút Thử lại khởi tạo lượt mới (FE-R1-12), và hiển thị đúng heading theo endReason / AWAITING_CHOICE hết hạn (FE-R1-11, FE-R1-13, FE-R1-14)", async () => {
    const user = userEvent.setup();
    const navigateFn = vi.fn();
    const startAttemptFn = vi.fn<NonNullable<AttemptPlayerDeps["startAttemptFn"]>>(async () => ({
      attemptId: "att-restarted",
      state: "STARTING" as const,
      resumed: false,
      retryable: false,
    }));

    const abortedView = renderWithProviders(
      <AttemptPlayer
        attemptId="att-aborted"
        userId="user-1"
        deps={makeBaseDeps({
          loadAttemptStateFn: vi.fn(async () => ({
            attemptId: "att-aborted",
            state: "ABORTED" as const,
            abortReason: "GENERATION_FAILED" as const,
            examName: "Đề bị hủy",
            serverNow: "2026-11-21T10:00:00Z",
            parts: [],
          })),
          loadAttemptStartContextFn: vi.fn(async () => ({
            entitlementId: "ent-aborted",
            preChoice: { optionCode: "CA1" },
          })),
          startAttemptFn,
          navigateFn,
        })}
      />,
    );

    expect(await screen.findByRole("heading", { name: "Chưa chuẩn bị được đề" })).toBeInTheDocument();
    expect(document.querySelectorAll("main")).toHaveLength(1);
    await user.click(screen.getByRole("button", { name: "Thử lại" }));
    await waitFor(() => expect(startAttemptFn).toHaveBeenCalledTimes(1));
    expect(startAttemptFn.mock.calls[0]?.[0]).toEqual({
      entitlementId: "ent-aborted",
      preChoice: { optionCode: "CA1" },
    });
    expect(navigateFn).toHaveBeenCalledWith("/attempts/att-restarted");
    abortedView.unmount();

    // FE-R1-11 + FE-R1-13: CHOICE_WINDOW_CLOSED reloads state -> headingChoiceTimeout
    const choiceState: AttemptStateResponse = {
      attemptId: "att-choice-err",
      state: "IN_PROGRESS",
      navigationMode: "SEQUENTIAL",
      timing: "PER_PART",
      examName: "Đề chọn phần 3",
      serverNow: "2026-11-21T10:00:00Z",
      parts: [
        { ord: 1, partId: "p1", state: "CLOSED", items: [] },
        {
          ord: 3,
          partId: "p3",
          state: "AWAITING_CHOICE",
          choiceDeadlineAt: "2026-11-21T10:15:00Z",
          items: [],
        },
      ],
    };
    const closedChoiceState: AttemptStateResponse = {
      attemptId: "att-choice-err",
      state: "GRADING",
      examName: "Đề chọn phần 3",
      serverNow: "2026-11-21T10:16:00Z",
      parts: [
        { ord: 1, partId: "p1", state: "CLOSED", items: [] },
        {
          ord: 3,
          partId: "p3",
          state: "SKIPPED",
          choiceDeadlineAt: "2026-11-21T10:15:00Z",
          closedAt: "2026-11-21T10:15:00Z",
          items: [],
        },
      ],
    };
    const loadChoiceStateFn = vi
      .fn<() => Promise<AttemptStateResponse>>()
      .mockResolvedValueOnce(choiceState)
      .mockResolvedValueOnce(closedChoiceState);
    const confirmPartChoiceFn = vi
      .fn()
      .mockRejectedValueOnce(new attemptApi.AttemptApiError(429, "Busy", { retryAfterSeconds: 7 }))
      .mockRejectedValueOnce(new ApiStatusError(409, "Closed", { code: "CHOICE_WINDOW_CLOSED" }));

    const choiceView = renderWithProviders(
      <AttemptPlayer
        attemptId="att-choice-err"
        userId="user-1"
        deps={makeBaseDeps({
          loadAttemptStateFn: loadChoiceStateFn,
          confirmPartChoiceFn,
        })}
      />,
    );
    expect(await screen.findByText("Chọn phần thi thứ ba")).toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: "Xác nhận chọn phần 3" }));
    expect(await screen.findByText("Đề vẫn đang được chuẩn bị. Trang tự kiểm tra lại.")).toBeInTheDocument();

    await user.click(screen.getByRole("button", { name: "Xác nhận chọn phần 3" }));
    expect(await screen.findByRole("heading", { name: "Đã quá thời gian chọn phần 3" })).toBeInTheDocument();
    choiceView.unmount();

    // FE-R1-13 TIME_UP heading + FE-R1-14 header pill & auto-reconcile on online
    const openOnlineState: AttemptStateResponse = {
      attemptId: "att-online",
      state: "IN_PROGRESS",
      navigationMode: "FREE",
      timing: "SHARED",
      deadlineAt: "2026-11-21T10:10:00Z",
      examName: "Đề online reconcile",
      serverNow: "2026-11-21T10:00:00Z",
      parts: [{ ord: 1, partId: "p1", state: "OPEN", items: [makeOpenMcqItem("qo", 1, "Câu đối soát")] }],
    };
    const timeUpClosedState: AttemptStateResponse = {
      attemptId: "att-online",
      state: "GRADING",
      timing: "SHARED",
      deadlineAt: "2026-11-21T10:10:00Z",
      examName: "Đề online reconcile",
      serverNow: "2026-11-21T10:11:00Z",
      parts: [
        {
          ord: 1,
          partId: "p1",
          state: "CLOSED",
          closedAt: "2026-11-21T10:10:00Z",
          items: [],
        },
      ],
    };
    const onlineLoadFn = vi
      .fn<() => Promise<AttemptStateResponse>>()
      .mockResolvedValueOnce(openOnlineState)
      .mockResolvedValueOnce(timeUpClosedState);

    renderWithProviders(
      <AttemptPlayer
        attemptId="att-online"
        userId="user-1"
        deps={makeBaseDeps({
          loadAttemptStateFn: onlineLoadFn,
          submitAttemptFn: vi.fn(async () => {
            throw new Error("Network lost");
          }),
        })}
      />,
    );
    expect(await screen.findByText("Câu đối soát")).toBeInTheDocument();

    // Offline pill includes "Chưa lưu · Mất kết nối"
    act(() => {
      window.dispatchEvent(new Event("offline"));
    });
    await user.click(screen.getByText("Lựa chọn 1"));
    expect(screen.getByText("Chưa lưu · Mất kết nối")).toBeInTheDocument();

    // Unconfirmed submit -> window online auto-reconciles and transitions to Time Up closed heading
    await user.click(screen.getByRole("button", { name: "Nộp bài" }));
    await user.click(screen.getByRole("dialog").querySelectorAll("button")[1]!);
    expect(await screen.findByText("Chưa xác nhận nộp bài")).toBeInTheDocument();

    fireEvent(window, new Event("online"));
    expect(await screen.findByRole("heading", { name: "Đã hết giờ làm bài" })).toBeInTheDocument();
  });

  it("AWAITING_CHOICE khi choiceDeadlineAt về 0 tự động gọi reloadServerState (FE-R1-13)", async () => {
    vi.useFakeTimers();
    const awaitingExpiredState: AttemptStateResponse = {
      attemptId: "att-choice-exp",
      state: "IN_PROGRESS",
      navigationMode: "SEQUENTIAL",
      timing: "PER_PART",
      examName: "Đề hết hạn chọn",
      serverNow: "2026-11-21T10:15:00Z",
      parts: [
        { ord: 1, partId: "p1", state: "CLOSED", items: [] },
        {
          ord: 3,
          partId: "p3",
          state: "AWAITING_CHOICE",
          choiceDeadlineAt: "2026-11-21T10:15:00Z",
          items: [],
        },
      ],
    };
    const closedAfterExpiry: AttemptStateResponse = {
      attemptId: "att-choice-exp",
      state: "GRADING",
      examName: "Đề hết hạn chọn",
      serverNow: "2026-11-21T10:15:01Z",
      parts: [
        { ord: 1, partId: "p1", state: "CLOSED", items: [] },
        {
          ord: 3,
          partId: "p3",
          state: "SKIPPED",
          choiceDeadlineAt: "2026-11-21T10:15:00Z",
          closedAt: "2026-11-21T10:15:00Z",
          items: [],
        },
      ],
    };

    const loadFn = vi
      .fn<() => Promise<AttemptStateResponse>>()
      .mockResolvedValueOnce(awaitingExpiredState)
      .mockResolvedValueOnce(closedAfterExpiry);

    renderWithProviders(
      <AttemptPlayer
        attemptId="att-choice-exp"
        userId="user-1"
        deps={makeBaseDeps({
          loadAttemptStateFn: loadFn,
          claimEditSessionFn: vi.fn(async () => ({
            conflict: false as const,
            sessionId: "sess-1",
            serverNow: "2026-11-21T10:15:00Z",
          })),
        })}
      />,
    );

    await act(async () => {
      await vi.advanceTimersByTimeAsync(100);
    });
    expect(screen.getByText("Chọn phần thi thứ ba")).toBeInTheDocument();

    await act(async () => {
      await vi.advanceTimersByTimeAsync(600);
    });

    expect(loadFn).toHaveBeenCalledTimes(2);
    expect(screen.getByRole("heading", { name: "Đã quá thời gian chọn phần 3" })).toBeInTheDocument();
  });
});
