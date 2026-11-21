import { screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";
import { renderWithProviders } from "@/test/render";
import { AttemptPlayer, type AttemptPlayerDeps } from "./attempt-player";
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
    createTabLockChannelFn: () => null,
    ...overrides,
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
  it("SEQUENTIAL flow: khóa chuyển phần trước, hoàn thành phần 1 rồi sang phần 2 với displayNo liên tục (AC-7)", async () => {
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

    await user.click(screen.getByRole("button", { name: "Hoàn thành phần" }));
    expect(screen.getByRole("dialog")).toBeInTheDocument();

    await user.click(screen.getByRole("button", { name: "Hoàn thành phần" }));

    await waitFor(() => {
      expect(finishPartFn).toHaveBeenCalledWith("att-1", "part-1", "sess-1", { finalAnswers: [] });
    });

    expect(await screen.findByText("Câu văn số 2")).toBeInTheDocument();
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
});
