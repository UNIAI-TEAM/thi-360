import { screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";
import enMessages from "@/messages/en.json";
import { renderWithProviders } from "@/test/render";
import { AttemptPlayer, type AttemptPlayerDeps } from "../attempt-player";
import type { AttemptItemView, AttemptStateResponse } from "../mappers";
import { type AttemptDisplaySegmentView, buildSegmentedQuestionUnits } from "./segment-units";

vi.mock("next/navigation", () => ({
  useRouter: () => ({ push: vi.fn(), replace: vi.fn() }),
  usePathname: () => "/attempts/att-vsat-1",
}));

const VSAT_SEGMENTS: AttemptDisplaySegmentView[] = [
  { code: "I", name: "Phần I", fromPosition: 1, toPosition: 9, commandCount: 36, maxPoints: 54 },
  { code: "II", name: "Phần II", fromPosition: 10, toPosition: 15, commandCount: 6, maxPoints: 36 },
  { code: "III", name: "Phần III", fromPosition: 16, toPosition: 20, commandCount: 5, maxPoints: 30 },
  { code: "IV", name: "Phần IV", fromPosition: 21, toPosition: 25, commandCount: 5, maxPoints: 30 },
];

function makeMcqItem(id: string, displayNo: number, stem: string, parentItemId?: string): AttemptItemView {
  return {
    itemId: id,
    ...(parentItemId ? { parentItemId } : {}),
    displayNo,
    unitCount: 1,
    questionType: "SINGLE_CHOICE",
    content: `<p>${stem}</p>`,
    answerSeq: 0,
    flagged: false,
    questionBody: {
      type: "SINGLE_CHOICE",
      options: [
        { id: `${id}-a`, content: "<p>Option A</p>" },
        { id: `${id}-b`, content: "<p>Option B</p>" },
      ],
    },
  };
}

function makeTrueFalseItem(
  id: string,
  displayNo: number,
  stem: string,
  parentItemId?: string,
): AttemptItemView {
  return {
    itemId: id,
    ...(parentItemId ? { parentItemId } : {}),
    displayNo,
    unitCount: 1,
    questionType: "TRUE_FALSE",
    content: `<p>${stem}</p>`,
    answerSeq: 0,
    flagged: false,
    questionBody: {
      type: "TRUE_FALSE",
      statements: [
        { id: `${id}-s1`, content: "<p>Statement 1</p>" },
        { id: `${id}-s2`, content: "<p>Statement 2</p>" },
        { id: `${id}-s3`, content: "<p>Statement 3</p>" },
        { id: `${id}-s4`, content: "<p>Statement 4</p>" },
      ],
    },
  };
}

function makeVsatEnglish25State(): AttemptStateResponse {
  const items: AttemptItemView[] = [];

  // Q1..Q7 standalone in Phần I
  for (let i = 1; i <= 7; i++) {
    items.push(makeMcqItem(`q-${i}`, i, `Câu lẻ Phần I số ${i}`));
  }

  // Shared QUESTION_SET spanning Q8–15: Q8–9 TRUE_FALSE (Phần I) + Q10–15 SINGLE_CHOICE (Phần II)
  items.push({
    itemId: "shared-reading-set",
    displayNo: 8,
    questionType: "QUESTION_SET",
    content: "",
    questionBody: {
      type: "QUESTION_SET",
      passage: "<p>V-SAT English shared reading passage</p>",
    },
  });
  items.push(makeTrueFalseItem("q-8", 8, "Câu 8 Đúng/Sai trong chùm chung", "shared-reading-set"));
  items.push(makeTrueFalseItem("q-9", 9, "Câu 9 Đúng/Sai trong chùm chung", "shared-reading-set"));
  for (let i = 10; i <= 15; i++) {
    items.push(makeMcqItem(`q-${i}`, i, `Câu ${i} trắc nghiệm trong chùm chung`, "shared-reading-set"));
  }

  // Q16..Q20 in Phần III
  for (let i = 16; i <= 20; i++) {
    items.push(makeMcqItem(`q-${i}`, i, `Câu Phần III số ${i}`));
  }

  // Q21..Q25 in Phần IV
  for (let i = 21; i <= 25; i++) {
    items.push(makeMcqItem(`q-${i}`, i, `Câu Phần IV số ${i}`));
  }

  return {
    attemptId: "att-vsat-1",
    state: "IN_PROGRESS",
    navigationMode: "FREE",
    timing: "SHARED",
    deadlineAt: "2026-11-22T03:00:00Z",
    examName: "Đề V-SAT Tiếng Anh 25 câu",
    serverNow: "2026-11-22T02:00:00Z",
    parts: [
      {
        ord: 1,
        partId: "part-vsat",
        partCode: "VSAT_ANH",
        partName: "Tiếng Anh V-SAT",
        state: "OPEN",
        displaySegments: VSAT_SEGMENTS,
        items,
      },
    ],
  };
}

function makeDeps(overrides: Partial<AttemptPlayerDeps> = {}): AttemptPlayerDeps {
  return {
    claimEditSessionFn: vi.fn(async () => ({
      conflict: false as const,
      sessionId: "sess-vsat-1",
      serverNow: "2026-11-22T02:00:00Z",
    })),
    sendHeartbeatFn: vi.fn(async () => ({
      serverNow: "2026-11-22T02:00:30Z",
      state: "IN_PROGRESS" as const,
    })),
    saveAnswersFn: vi.fn(async (_a, _s, body: { changes?: Array<{ itemId: string; seq: number }> }) => ({
      results: (body.changes ?? []).map((c) => ({
        itemId: c.itemId,
        seq: c.seq,
        status: "SAVED" as const,
      })),
      serverNow: "2026-11-22T02:00:02Z",
    })),
    finishPartFn: vi.fn(),
    submitAttemptFn: vi.fn(),
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

describe("phân đoạn hiển thị và chùm cắt ngang ranh giới (AC-9, BS §17.3)", () => {
  it("tách QUESTION_SET Q8–15 thành hai lát Phần I (Q8–9) và Phần II (Q10–15) giữ nguyên ngữ liệu và đánh số liền mạch 1..25", () => {
    const state = makeVsatEnglish25State();
    const part = state.parts![0]!;
    const units = buildSegmentedQuestionUnits(part.items, "att-vsat-1", part.displaySegments);

    // 7 standalone (1..7) + 2 slices of shared-reading-set (8..9, 10..15) + 5 (16..20) + 5 (21..25) = 19 units
    expect(units).toHaveLength(19);

    expect(units[0]!.segment?.code).toBe("I");
    expect(units[0]!.isSegmentStart).toBe(true);
    expect(units[1]!.isSegmentStart).toBe(false);

    const slice1 = units[7]!;
    expect(slice1.id).toBe("shared-reading-set::segment::I");
    expect(slice1.kind).toBe("QUESTION_SET");
    expect(slice1.segment?.code).toBe("I");
    expect(slice1.isSegmentStart).toBe(false);
    expect(slice1.displayStart).toBe(8);
    expect(slice1.displayEnd).toBe(9);
    expect(slice1.childItems.map((c) => c.displayNo)).toEqual([8, 9]);

    const slice2 = units[8]!;
    expect(slice2.id).toBe("shared-reading-set::segment::II");
    expect(slice2.kind).toBe("QUESTION_SET");
    expect(slice2.segment?.code).toBe("II");
    expect(slice2.isSegmentStart).toBe(true);
    expect(slice2.displayStart).toBe(10);
    expect(slice2.displayEnd).toBe(15);
    expect(slice2.childItems.map((c) => c.displayNo)).toEqual([10, 11, 12, 13, 14, 15]);
    expect(slice2.kitQuestion.body.type === "QUESTION_SET" && slice2.kitQuestion.body.passage).toContain(
      "V-SAT English shared reading passage",
    );

    expect(units[9]!.segment?.code).toBe("III");
    expect(units[9]!.isSegmentStart).toBe(true);
    expect(units[14]!.segment?.code).toBe("IV");
    expect(units[14]!.isSegmentStart).toBe(true);
  });

  it("render tiêu đề phân đoạn có data-exam-content, không thêm đồng hồ thứ hai, hiện đủ 1..25 theo nhóm trong QuestionNav và lưu riêng từng câu con Q8/Q10", async () => {
    const user = userEvent.setup();
    const saveAnswersFn = vi.fn(async (_a: string, _s: string, body: { changes?: Array<{ itemId: string; seq: number; answer?: unknown }> }) => ({
      results: (body.changes ?? []).map((c) => ({
        itemId: c.itemId,
        seq: c.seq,
        status: "SAVED" as const,
      })),
      serverNow: "2026-11-22T02:00:05Z",
    }));

    renderWithProviders(
      <AttemptPlayer
        attemptId="att-vsat-1"
        userId="user-1"
        deps={makeDeps({
          loadAttemptStateFn: vi.fn(async () => makeVsatEnglish25State()),
          saveAnswersFn,
        })}
      />,
    );

    // Unit 1: Phần I header with data-exam-content, range, commandCount, maxPoints, strictly 1 [role="timer"]
    expect(await screen.findByText("Câu lẻ Phần I số 1")).toBeInTheDocument();
    const segHeader = document.querySelector("[data-segment-header]") as HTMLElement;
    expect(segHeader).toBeInTheDocument();
    expect(segHeader).toHaveAttribute("data-segment-code", "I");
    expect(segHeader).toHaveAttribute("data-segment-start", "true");
    const segHeading = within(segHeader).getByRole("heading", { level: 2, name: "Phần I" });
    expect(segHeading).toHaveAttribute("data-exam-content");
    expect(within(segHeader).getByText("Câu 1–9")).toBeInTheDocument();
    expect(within(segHeader).getByText("36 lệnh hỏi")).toBeInTheDocument();
    expect(within(segHeader).getByText("54 điểm")).toBeInTheDocument();

    // Strictly 1 timer for the whole exam (no per-segment timer)
    expect(screen.getAllByRole("timer")).toHaveLength(1);
    expect(within(segHeader).queryByRole("timer")).not.toBeInTheDocument();

    // QuestionNav groups 1..25 under Phần I..IV headings
    const navAside = screen.getByRole("complementary", { name: "Danh sách câu hỏi" });
    expect(within(navAside).getByText("Phần I")).toHaveAttribute("data-exam-content");
    expect(within(navAside).getByText("Phần II")).toHaveAttribute("data-exam-content");
    expect(within(navAside).getByText("Phần III")).toHaveAttribute("data-exam-content");
    expect(within(navAside).getByText("Phần IV")).toHaveAttribute("data-exam-content");
    for (let n = 1; n <= 25; n++) {
      expect(within(navAside).getByRole("button", { name: new RegExp(`^${n},`) })).toBeInTheDocument();
    }

    // Jump to Q8 (first slice of shared reading QUESTION_SET in Phần I)
    await user.click(within(navAside).getByRole("button", { name: /^8,/ }));
    expect(await screen.findByText("V-SAT English shared reading passage")).toBeInTheDocument();
    expect(screen.getByText("Câu 8 Đúng/Sai trong chùm chung")).toBeInTheDocument();
    expect(screen.getByText("Câu 9 Đúng/Sai trong chùm chung")).toBeInTheDocument();
    expect(screen.queryByText("Câu 10 trắc nghiệm trong chùm chung")).not.toBeInTheDocument();
    expect(document.querySelector("[data-segment-header]")).toHaveAttribute("data-segment-start", "false");

    // Answer Q8 Statement 1 -> saves itemId "q-8"
    await waitFor(() => {
      const fs = document.querySelector("fieldset[data-attempt-question-fieldset]");
      expect(fs).not.toBeDisabled();
    });
    const q8Region = screen.getByRole("region", { name: /Câu 8/ });
    const q8Radios = within(q8Region).getAllByRole("radio", { name: "Đúng" });
    await user.click(q8Radios[0]!);
    await waitFor(() =>
      expect(saveAnswersFn).toHaveBeenCalledWith(
        "att-vsat-1",
        "sess-vsat-1",
        expect.objectContaining({
          changes: [
            expect.objectContaining({
              itemId: "q-8",
              answer: { type: "TRUE_FALSE", statements: { "q-8-s1": true } },
            }),
          ],
        }),
      ),
    );

    // Jump to Q10 (second slice of shared reading QUESTION_SET in Phần II)
    await user.click(within(navAside).getByRole("button", { name: /^10,/ }));
    expect(await screen.findByText("Câu 10 trắc nghiệm trong chùm chung")).toBeInTheDocument();
    // Same shared reading passage is still visible in Phần II!
    expect(screen.getByText("V-SAT English shared reading passage")).toBeInTheDocument();
    // Q8 & Q9 are NOT rendered in Phần II slice
    expect(screen.queryByText("Câu 8 Đúng/Sai trong chùm chung")).not.toBeInTheDocument();
    const seg2Header = document.querySelector("[data-segment-header]") as HTMLElement;
    expect(seg2Header).toHaveAttribute("data-segment-code", "II");
    expect(seg2Header).toHaveAttribute("data-segment-start", "true");
    expect(within(seg2Header).getByText("Câu 10–15")).toBeInTheDocument();
    expect(within(seg2Header).getByText("6 lệnh hỏi")).toBeInTheDocument();
    expect(within(seg2Header).getByText("36 điểm")).toBeInTheDocument();

    // Answer Q10 -> saves itemId "q-10"
    const q10Region = screen.getByRole("region", { name: /Câu 10/ });
    await user.click(within(q10Region).getByText("Option A"));
    await waitFor(() =>
      expect(saveAnswersFn).toHaveBeenCalledWith(
        "att-vsat-1",
        "sess-vsat-1",
        expect.objectContaining({
          changes: [
            expect.objectContaining({
              itemId: "q-10",
              answer: { type: "SINGLE_CHOICE", optionId: "q-10-a" },
            }),
          ],
        }),
      ),
    );
  });

  it("hỗ trợ locale en sạch sẽ với data-exam-content và không hiện phân đoạn khi displaySegments rỗng", async () => {
    const enView = renderWithProviders(
      <AttemptPlayer
        attemptId="att-vsat-1"
        userId="user-1"
        deps={makeDeps({ loadAttemptStateFn: vi.fn(async () => makeVsatEnglish25State()) })}
      />,
      { locale: "en", messages: enMessages },
    );

    expect(await screen.findByText("Câu lẻ Phần I số 1")).toBeInTheDocument();
    const segHeader = document.querySelector("[data-segment-header]") as HTMLElement;
    expect(within(segHeader).getByRole("heading", { level: 2, name: "Phần I" })).toHaveAttribute(
      "data-exam-content",
    );
    expect(within(segHeader).getByText("Questions 1–9")).toBeInTheDocument();
    expect(within(segHeader).getByText("36 items")).toBeInTheDocument();
    expect(within(segHeader).getByText("54 pts")).toBeInTheDocument();
    enView.unmount();

    // Non-segmented exam -> NO [data-segment-header] and flat QuestionNav
    const nonSegmentedState = makeVsatEnglish25State();
    nonSegmentedState.parts![0]!.displaySegments = [];
    renderWithProviders(
      <AttemptPlayer
        attemptId="att-vsat-plain"
        userId="user-1"
        deps={makeDeps({ loadAttemptStateFn: vi.fn(async () => nonSegmentedState) })}
      />,
    );

    expect(await screen.findByText("Câu lẻ Phần I số 1")).toBeInTheDocument();
    expect(document.querySelector("[data-segment-header]")).toBeNull();
    const navAside = screen.getByRole("complementary", { name: "Danh sách câu hỏi" });
    expect(within(navAside).queryByText("Phần I")).not.toBeInTheDocument();
    expect(within(navAside).queryByText("Phần II")).not.toBeInTheDocument();
  });
});
