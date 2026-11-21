import { onlineManager, QueryClient } from "@tanstack/react-query";
import { act, fireEvent, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { useSyncExternalStore, type ComponentProps } from "react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { SESSION_QUERY_KEY } from "@/features/session/api";
import { ApiStatusError } from "@/lib/api/client";
import enMessages from "@/messages/en.json";
import { renderWithProviders } from "@/test/render";
import { ADMIN_SESSION, STUDENT_SESSION } from "@/test/storefront-fixtures";
import { ATTEMPT_REVIEW_QUERY_KEY, type AttemptReviewResponse } from "./api";
import { AttemptReviewView } from "./review-view";

const ATTEMPT_UUID_1 = "22222222-2222-4222-8222-222222222201";
const ATTEMPT_UUID_SCORE_ONLY = "22222222-2222-4222-8222-222222222202";
const ATTEMPT_UUID_GRADING = "22222222-2222-4222-8222-222222222203";
const ATTEMPT_UUID_404 = "22222222-2222-4222-8222-222222222404";
const ATTEMPT_UUID_SA = "22222222-2222-4222-8222-222222222205";
const ATTEMPT_UUID_FULL = "22222222-2222-4222-8222-222222222206";
const ATTEMPT_UUID_PENDING = "22222222-2222-4222-8222-222222222207";
const ATTEMPT_UUID_EN = "22222222-2222-4222-8222-222222222208";
const ATTEMPT_UUID_GUARD = "22222222-2222-4222-8222-222222222209";

const navMocks = vi.hoisted(() => ({
  search: "",
  listeners: new Set<() => void>(),
  setSearch(next: string) {
    this.search = next;
    for (const listener of this.listeners) {
      listener();
    }
  },
}));

vi.mock("next/navigation", () => ({
  usePathname: () => `/attempts/${ATTEMPT_UUID_1}/review`,
  useRouter: () => ({ push: vi.fn(), replace: vi.fn(), refresh: vi.fn() }),
  useSearchParams: () => {
    const search = useSyncExternalStore(
      (cb) => {
        navMocks.listeners.add(cb);
        return () => navMocks.listeners.delete(cb);
      },
      () => navMocks.search,
      () => navMocks.search,
    );
    return new URLSearchParams(search);
  },
}));

vi.mock("next/link", () => ({
  default: ({ href, onClick, children, ...rest }: ComponentProps<"a"> & { href: string }) => (
    <a
      href={href}
      {...rest}
      onClick={(event) => {
        onClick?.(event);
        if (!event.defaultPrevented && typeof href === "string" && href.includes("/review")) {
          event.preventDefault();
          const qIndex = href.indexOf("?");
          navMocks.setSearch(qIndex >= 0 ? href.slice(qIndex) : "");
        }
      }}
    >
      {children}
    </a>
  ),
}));

function makeFullReview(resultView: "FULL_SOLUTION" | "SCORE_AND_ANSWERS" = "FULL_SOLUTION"): AttemptReviewResponse {
  return {
    attemptId: ATTEMPT_UUID_1,
    state: "SCORED",
    resultView,
    selectedPartCode: "P1",
    parts: [
      { partId: "p1", partCode: "P1", partName: "Phần 1 · Tổng hợp", ord: 1, score: 8, maxScore: 10, state: "CLOSED" },
      { partId: "p2", partCode: "P2", partName: "Phần 2 · Tự chọn", ord: 2, score: 0, maxScore: 50, state: "SKIPPED" },
    ],
    items: [
      {
        itemId: "q1",
        partId: "p1",
        partCode: "P1",
        ord: 1,
        displayNo: 1,
        questionType: "SINGLE_CHOICE",
        content: "<p>Chọn thủ đô của Việt Nam</p>",
        pointsMax: 1,
        earnedPoints: 1,
        outcome: "CORRECT",
        optionOrder: ["opt-b", "opt-a"],
        body: {
          options: [
            { id: "opt-a", content: "<p>TP.HCM</p>" },
            { id: "opt-b", content: "<p>Hà Nội</p>" },
          ],
        },
        studentAnswer: { type: "SINGLE_CHOICE", optionId: "opt-b" },
        expectedAnswer: { expectedOptionIds: ["opt-b"] },
        solution: '<p>Đáp án đúng là Hà Nội <img src="/solution-assets/sol-1.png" alt="Minh họa lời giải" /></p>',
      },
      {
        itemId: "q2",
        partId: "p1",
        partCode: "P1",
        ord: 2,
        displayNo: 2,
        questionType: "MULTIPLE_CHOICE",
        content: "<p>Chọn số chẵn</p>",
        pointsMax: 1,
        earnedPoints: 0.5,
        outcome: "PARTIAL",
        body: {
          scoringRule: "PARTIAL",
          options: [
            { id: "m1", content: "<p>2</p>" },
            { id: "m2", content: "<p>3</p>" },
            { id: "m3", content: "<p>4</p>" },
          ],
        },
        studentAnswer: { type: "MULTIPLE_CHOICE", optionIds: ["m1"] },
        expectedAnswer: { expectedOptionIds: ["m1", "m3"] },
      },
      {
        itemId: "q3",
        partId: "p1",
        partCode: "P1",
        ord: 3,
        displayNo: 3,
        questionType: "SHORT_ANSWER",
        content: "<p>2 + 3 = ?</p>",
        pointsMax: 1,
        earnedPoints: 0,
        outcome: "WRONG",
        body: { answerFormat: "INTEGER", inputHint: "Nhập số nguyên" },
        studentAnswer: { type: "SHORT_ANSWER", text: "6" },
        expectedAnswer: { acceptedTexts: ["5"] },
      },
      {
        itemId: "q4",
        partId: "p1",
        partCode: "P1",
        ord: 4,
        displayNo: 4,
        questionType: "CLOZE",
        content: "",
        pointsMax: 2,
        earnedPoints: 1,
        outcome: "PARTIAL",
        body: {
          passage: "<p>Nước sôi ở [[b1]] °C và đóng băng ở [[b2]] °C.</p>",
          blanks: [{ id: "b1" }, { id: "b2" }],
        },
        studentAnswer: { type: "CLOZE", blanks: { b1: "100", b2: "1" } },
        expectedAnswer: {
          clozeKeys: [
            { blankId: "b1", acceptedTexts: ["100"] },
            { blankId: "b2", acceptedTexts: ["0"] },
          ],
        },
        gradingDetail: {
          blankResults: [
            { id: "b1", outcome: "CORRECT", pointsEarned: 1, pointsMax: 1 },
            { id: "b2", outcome: "WRONG", pointsEarned: 0, pointsMax: 1 },
          ],
        },
      },
      {
        itemId: "q5",
        partId: "p1",
        partCode: "P1",
        ord: 5,
        displayNo: 5,
        questionType: "TRUE_FALSE",
        content: "<p>Đánh giá mệnh đề</p>",
        pointsMax: 1,
        earnedPoints: 0.5,
        outcome: "PARTIAL",
        body: {
          scoringRule: "PER_STATEMENT",
          statements: [
            { id: "s1", content: "<p>1 &lt; 2</p>" },
            { id: "s2", content: "<p>3 &lt; 2</p>" },
          ],
        },
        studentAnswer: { type: "TRUE_FALSE", statements: { s1: true, s2: true } },
        expectedAnswer: {
          trueFalseKeys: [
            { statementId: "s1", expected: true },
            { statementId: "s2", expected: false },
          ],
        },
        gradingDetail: {
          scoringMethod: "PER_STATEMENT",
          statementResults: [
            { id: "s1", answered: true, matched: true },
            { id: "s2", answered: true, matched: false },
          ],
        },
      },
      {
        itemId: "q6",
        partId: "p1",
        partCode: "P1",
        ord: 6,
        displayNo: 6,
        questionType: "MATCHING",
        content: "<p>Ghép nước và thủ đô</p>",
        pointsMax: 1,
        earnedPoints: 0.25,
        outcome: "PARTIAL",
        optionOrder: ["r2", "r1", "r3", "r4"],
        body: {
          left: [
            { id: "l1", content: "<p>Nhật Bản</p>" },
            { id: "l2", content: "<p>Pháp</p>" },
            { id: "l3", content: "<p>Thái Lan</p>" },
            { id: "l4", content: "<p>Ai Cập</p>" },
          ],
          right: [
            { id: "r1", content: "<p>Tokyo</p>" },
            { id: "r2", content: "<p>Paris</p>" },
            { id: "r3", content: "<p>Bangkok</p>" },
            { id: "r4", content: "<p>Cairo</p>" },
          ],
          reuseRight: false,
        },
        studentAnswer: { type: "MATCHING", pairs: { l1: "r1", l2: "r3" } },
        expectedAnswer: {
          matchingKeys: [
            { leftId: "l1", rightId: "r1" },
            { leftId: "l2", rightId: "r2" },
            { leftId: "l3", rightId: "r3" },
            { leftId: "l4", rightId: "r4" },
          ],
        },
        gradingDetail: {
          pairResults: [
            { leftId: "l1", matched: true, pointsEarned: 0.25 },
            { leftId: "l2", matched: false, pointsEarned: 0 },
            { leftId: "l3", matched: false, pointsEarned: 0 },
            { leftId: "l4", matched: false, pointsEarned: 0 },
          ],
        },
      },
      {
        itemId: "q7",
        partId: "p1",
        partCode: "P1",
        ord: 7,
        displayNo: 7,
        questionType: "ESSAY",
        content: "<p>Viết đoạn văn ngắn</p>",
        pointsMax: 5,
        earnedPoints: 4,
        outcome: "PARTIAL",
        body: { wordLimit: 200 },
        studentAnswer: { type: "ESSAY", html: "<p>Bài viết mẫu ngắn.</p>", wordCount: 4 },
        gradingDetail: { wordCount: 4 },
        essayFeedback: {
          points: 4,
          comment: "Diễn đạt mạch lạc",
          criteria: [{ id: "Lập luận", points: 2.5, comment: "Ví dụ rõ ràng" }],
        },
      },
      {
        itemId: "qset-1",
        partId: "p1",
        partCode: "P1",
        ord: 8,
        displayNo: 8,
        questionType: "QUESTION_SET",
        pointsMax: 2,
        earnedPoints: 1,
        outcome: "PARTIAL",
        body: { passage: "<p>Đọc đoạn văn khoa học dưới đây.</p>" },
      },
      {
        itemId: "q8",
        parentItemId: "qset-1",
        partId: "p1",
        partCode: "P1",
        ord: 8,
        displayNo: 8,
        questionType: "SINGLE_CHOICE",
        content: "<p>Ý chính của đoạn văn</p>",
        pointsMax: 1,
        earnedPoints: 1,
        outcome: "CORRECT",
        body: { options: [{ id: "c1", content: "<p>Bảo tồn nước</p>" }] },
        studentAnswer: { type: "SINGLE_CHOICE", optionId: "c1" },
        expectedAnswer: { expectedOptionIds: ["c1"] },
      },
      {
        itemId: "q9",
        parentItemId: "qset-1",
        partId: "p1",
        partCode: "P1",
        ord: 9,
        displayNo: 9,
        questionType: "SINGLE_CHOICE",
        content: "<p>Tác giả nhấn mạnh điều gì</p>",
        pointsMax: 1,
        earnedPoints: 0,
        outcome: "BLANK",
        body: { options: [{ id: "c2", content: "<p>Tái sử dụng</p>" }] },
        expectedAnswer: { expectedOptionIds: ["c2"] },
      },
    ],
  };
}

describe("AttemptReviewView · AC-10..AC-12, AC-17, AC-23", () => {
  beforeEach(() => {
    navMocks.setSearch("");
  });

  afterEach(() => {
    onlineManager.setOnline(true);
    vi.restoreAllMocks();
  });

  it("chặn admin và xử lý 403 REVIEW_NOT_INCLUDED, 403 khác đẩy QueryCache, 409 REVIEW_NOT_AVAILABLE vs 409 khác, 404 (kể cả non-UUID), offline, và lỗi mạng (AC-10, AC-17)", async () => {
    const blockedLoader = vi.fn<() => Promise<AttemptReviewResponse>>();
    const adminView = renderWithProviders(
      <AttemptReviewView
        attemptId={ATTEMPT_UUID_1}
        loadCurrentSession={async () => ADMIN_SESSION}
        loadReview={blockedLoader}
      />,
    );
    expect(await screen.findByRole("heading", { level: 1, name: "Tài khoản quản trị" })).toBeInTheDocument();
    expect(blockedLoader).not.toHaveBeenCalled();
    adminView.unmount();

    const nonUuidLoader = vi.fn<() => Promise<AttemptReviewResponse>>();
    const nonUuidView = renderWithProviders(
      <AttemptReviewView
        attemptId="not-a-uuid"
        loadCurrentSession={async () => STUDENT_SESSION}
        loadReview={nonUuidLoader}
      />,
    );
    expect(await screen.findByRole("heading", { level: 1, name: "Không tìm thấy bài làm" })).toBeInTheDocument();
    expect(nonUuidLoader).not.toHaveBeenCalled();
    nonUuidView.unmount();

    const forbiddenView = renderWithProviders(
      <AttemptReviewView
        attemptId={ATTEMPT_UUID_SCORE_ONLY}
        loadCurrentSession={async () => STUDENT_SESSION}
        loadReview={async () => {
          throw new ApiStatusError(403, "Score only", { code: "REVIEW_NOT_INCLUDED" });
        }}
      />,
    );
    expect(await screen.findByRole("heading", { level: 1, name: "Bài thi này không có phần xem lại bài làm" })).toBeInTheDocument();
    const backLinks403 = screen.getAllByRole("link", { name: "Về kết quả" });
    expect(backLinks403[0]).toHaveAttribute("href", `/attempts/${ATTEMPT_UUID_SCORE_ONLY}/result`);
    forbiddenView.unmount();

    // Non-REVIEW_NOT_INCLUDED 403 MUST propagate to QueryCache and NOT render not_included (R1-3, R1-4, R1-5)
    const client403 = new QueryClient({ defaultOptions: { queries: { retry: false } } });
    const accessForbiddenErrors: unknown[] = [];
    const unsub403 = client403.getQueryCache().subscribe((event) => {
      if (event.type === "updated" && event.action.type === "error") {
        accessForbiddenErrors.push(event.action.error);
      }
    });
    try {
      const accessForbiddenView = renderWithProviders(
        <AttemptReviewView
          attemptId={ATTEMPT_UUID_1}
          loadCurrentSession={async () => STUDENT_SESSION}
          loadReview={async () => {
            throw new ApiStatusError(403, "Forbidden", { code: "ATTEMPT_ACCESS_FORBIDDEN" });
          }}
        />,
        { client: client403 },
      );
      expect(await screen.findByRole("alert")).toHaveTextContent("Không tải được bài làm");
      expect(accessForbiddenErrors).toHaveLength(1);
      expect(screen.queryByText("Bài thi này không có phần xem lại bài làm")).not.toBeInTheDocument();
      accessForbiddenView.unmount();
    } finally {
      unsub403();
    }

    const conflictView = renderWithProviders(
      <AttemptReviewView
        attemptId={ATTEMPT_UUID_GRADING}
        loadCurrentSession={async () => STUDENT_SESSION}
        loadReview={async () => {
          throw new ApiStatusError(409, "Not ready", { code: "REVIEW_NOT_AVAILABLE" });
        }}
      />,
    );
    expect(await screen.findByRole("heading", { level: 1, name: "Chưa thể xem lại bài làm" })).toBeInTheDocument();
    conflictView.unmount();

    // Non-REVIEW_NOT_AVAILABLE 409 MUST render generic error alert, NOT notAvailable (R1-3, R1-35)
    const other409View = renderWithProviders(
      <AttemptReviewView
        attemptId={ATTEMPT_UUID_GRADING}
        loadCurrentSession={async () => STUDENT_SESSION}
        loadReview={async () => {
          throw new ApiStatusError(409, "Conflict", { code: "OTHER" });
        }}
      />,
    );
    expect(await screen.findByRole("alert")).toHaveTextContent("Không tải được bài làm");
    expect(screen.queryByText("Chưa thể xem lại bài làm")).not.toBeInTheDocument();
    other409View.unmount();

    const notFoundView = renderWithProviders(
      <AttemptReviewView
        attemptId={ATTEMPT_UUID_404}
        loadCurrentSession={async () => STUDENT_SESSION}
        loadReview={async () => {
          throw new ApiStatusError(404, "Not found", { code: "ATTEMPT_NOT_FOUND" });
        }}
      />,
    );
    expect(await screen.findByRole("heading", { level: 1, name: "Không tìm thấy bài làm" })).toBeInTheDocument();
    notFoundView.unmount();

    const retryLoader = vi
      .fn<() => Promise<AttemptReviewResponse>>()
      .mockRejectedValueOnce(new Error("net"))
      .mockResolvedValueOnce(makeFullReview());
    const errView = renderWithProviders(
      <AttemptReviewView
        attemptId={ATTEMPT_UUID_1}
        loadCurrentSession={async () => STUDENT_SESSION}
        loadReview={retryLoader}
        loadResult={async () => ({ examName: "Đề HSA thử" })}
      />,
    );
    expect(await screen.findByRole("alert")).toHaveTextContent("Không tải được bài làm");
    await userEvent.click(screen.getByRole("button", { name: "Thử lại" }));
    expect(await screen.findByRole("heading", { level: 1, name: "Xem lại bài làm" })).toBeInTheDocument();
    errView.unmount();

    // Offline isPaused handling (R1-36)
    try {
      onlineManager.setOnline(false);
      const offlineView = renderWithProviders(
        <AttemptReviewView
          attemptId={ATTEMPT_UUID_1}
          loadCurrentSession={async () => STUDENT_SESSION}
          loadReview={async () => makeFullReview()}
        />,
      );
      expect(await screen.findByRole("heading", { level: 1, name: "Không có kết nối" })).toBeInTheDocument();
      expect(screen.getByRole("button", { name: "Thử lại" })).toHaveClass("h-11");
      offlineView.unmount();
    } finally {
      onlineManager.setOnline(true);
    }
  });

  it("render đủ 8 dạng câu, giữ optionOrder, criteria tự luận không bịa max, đếm từ khi thiếu wordCount, và bảng chọn câu + Câu trước/Câu sau (AC-11)", async () => {
    const scrollIntoViewSpy = vi.fn();
    Element.prototype.scrollIntoView = scrollIntoViewSpy;
    vi.spyOn(window, "matchMedia").mockImplementation((query: string) => ({
      matches: query === "(prefers-reduced-motion: reduce)",
      media: query,
      onchange: null,
      addListener: vi.fn(),
      removeListener: vi.fn(),
      addEventListener: vi.fn(),
      removeEventListener: vi.fn(),
      dispatchEvent: vi.fn(),
    }));

    const fullReview = makeFullReview("FULL_SOLUTION");
    // Omit ESSAY wordCount to verify EssayReview computes real countWords(html) instead of 0 (R1-34)
    fullReview.items![6] = {
      ...fullReview.items![6]!,
      studentAnswer: { type: "ESSAY", html: "<p>Một hai ba bốn</p>" } as AttemptReviewResponse["items"] extends (infer T)[] ? T extends { studentAnswer?: infer S } ? S : never : never,
      gradingDetail: {},
    };

    renderWithProviders(
      <AttemptReviewView
        attemptId={ATTEMPT_UUID_1}
        loadCurrentSession={async () => STUDENT_SESSION}
        loadReview={async () => fullReview}
        loadResult={async () => ({
          examName: "Đề tổng hợp HSA",
          scope: "FULL",
          attemptNo: 1,
          state: "SCORED",
          displayScore: 100,
          rawMaxScore: 150,
        })}
      />,
    );

    expect(await screen.findByRole("heading", { level: 1, name: "Xem lại bài làm" })).toBeInTheDocument();
    expect(await screen.findByText("Đề tổng hợp HSA")).toHaveAttribute("data-exam-content");

    // 1. SINGLE_CHOICE: optionOrder + student pick + expected + score (R1-6)
    const q1Article = document.getElementById("review-item-q1")!;
    const q1Text = q1Article.textContent ?? "";
    expect(q1Text.indexOf("Hà Nội")).toBeLessThan(q1Text.indexOf("TP.HCM"));
    expect(within(q1Article).getByText("Bạn chọn")).toBeInTheDocument();
    expect(within(q1Article).getByText("Đáp án đúng")).toBeInTheDocument();
    expect(within(q1Article).getByText((_, node) => node?.tagName === "SPAN" && node.textContent === "1 / 1 điểm")).toBeInTheDocument();

    // 2. MULTIPLE_CHOICE: stem, student pick, correct tag, not-picked tag, score (R1-6)
    const q2Article = document.getElementById("review-item-q2")!;
    expect(within(q2Article).getByText("Chọn số chẵn")).toBeInTheDocument();
    expect(within(q2Article).getByText("Bạn chọn")).toBeInTheDocument();
    expect(within(q2Article).getAllByText("Đáp án đúng")).toHaveLength(2);
    expect(within(q2Article).getByText("Bạn không chọn")).toBeInTheDocument();
    expect(within(q2Article).getByText((_, node) => node?.tagName === "SPAN" && node.textContent === "0,5 / 1 điểm")).toBeInTheDocument();

    // 3. SHORT_ANSWER: student value "6", expected "5", score 0 / 1 (R1-6)
    const q3Article = document.getElementById("review-item-q3")!;
    expect(within(q3Article).getByText("2 + 3 = ?")).toBeInTheDocument();
    expect(within(q3Article).getByDisplayValue("6")).toBeInTheDocument();
    expect(within(q3Article).getByText("5")).toBeInTheDocument();
    expect(within(q3Article).getByText((_, node) => node?.tagName === "SPAN" && node.textContent === "0 / 1 điểm")).toBeInTheDocument();

    // 4. CLOZE: blank values "100"/"1", accepted "100"/"0", per-blank points, and palette range "4–5" (R1-6, R1-38)
    const q4Article = document.getElementById("review-item-q4")!;
    expect(within(q4Article).getByText(/Nước sôi ở/)).toBeInTheDocument();
    expect(within(q4Article).getByDisplayValue("100")).toBeInTheDocument();
    expect(within(q4Article).getByDisplayValue("1")).toBeInTheDocument();
    expect(within(q4Article).getByText("1 / 1 điểm")).toBeInTheDocument();
    expect(within(q4Article).getByText("0 / 1 điểm")).toBeInTheDocument();

    // 5. TRUE_FALSE: statements, student picks, expected answers, outcomes, score (R1-6)
    const q5Article = document.getElementById("review-item-q5")!;
    expect(within(q5Article).getByText("Đánh giá mệnh đề")).toBeInTheDocument();
    expect(within(q5Article).getByText("1 < 2")).toBeInTheDocument();
    expect(within(q5Article).getByText("3 < 2")).toBeInTheDocument();
    expect(within(q5Article).getAllByText("Đúng").length).toBeGreaterThanOrEqual(2);
    expect(within(q5Article).getAllByText("Sai")).toHaveLength(2);

    // 6. MATCHING: right-column optionOrder (r2 is A, r1 is B), expected pairs, and pair max 0,25 / 0,25 (R1-6, R1-23)
    const q6Article = document.getElementById("review-item-q6")!;
    expect(within(q6Article).getByText("Ghép nước và thủ đô")).toBeInTheDocument();
    const matchRows = within(q6Article).getAllByRole("row");
    expect(matchRows[1]).toHaveTextContent(/b\.\s*Tokyo/);
    expect(matchRows[2]).toHaveTextContent(/a\.\s*Paris/);
    expect(within(matchRows[1]!).getByText("0,25 / 0,25 điểm")).toBeInTheDocument();
    expect(within(q6Article).queryByText("0,25 / 1 điểm")).not.toBeInTheDocument();

    // 7. ESSAY: real word count computed when omitted ("4 / tối thiểu 200 chữ"), grader comment, criteria table a11y (R1-6, R1-34, R1-45)
    const q7Article = document.getElementById("review-item-q7")!;
    expect(within(q7Article).getByText("Một hai ba bốn")).toBeInTheDocument();
    expect(within(q7Article).getByText("4 / tối thiểu 200 chữ")).toBeInTheDocument();
    expect(within(q7Article).getByText("Diễn đạt mạch lạc")).toBeInTheDocument();
    const criterionEl = within(q7Article).getByText("Lập luận");
    expect(criterionEl).toHaveAttribute("data-exam-content");
    expect(criterionEl.closest("th")).toHaveAttribute("scope", "row");
    expect(within(q7Article).getByRole("table", { name: "Điểm từng tiêu chí" })).toBeInTheDocument();
    expect(within(q7Article).getByText("Ví dụ rõ ràng")).toHaveAttribute("data-exam-content");
    expect(within(q7Article).getByText("2,5 điểm")).toBeInTheDocument();
    expect(screen.queryByText(/2,5\s*\//)).not.toBeInTheDocument();

    // 8. QUESTION_SET: passage + child stems, answers, outcomes (R1-6)
    const qsetArticle = document.getElementById("review-item-qset-1")!;
    expect(within(qsetArticle).getByText("Đọc đoạn văn khoa học dưới đây.")).toBeInTheDocument();
    expect(within(qsetArticle).getByText("Ý chính của đoạn văn")).toBeInTheDocument();
    expect(within(qsetArticle).getByText("Bảo tồn nước")).toBeInTheDocument();
    expect(within(qsetArticle).getByText("Tác giả nhấn mạnh điều gì")).toBeInTheDocument();
    expect(within(qsetArticle).getByText("Tái sử dụng")).toBeInTheDocument();
    expect(within(qsetArticle).getByText("Bỏ trống")).toBeInTheDocument();

    // Palette leaf summary (9 leaf items across 7 standalone + 2 set children, R1-49), CLOZE "4–5" (R1-38), border differentiation (R1-44)
    const palette = screen.getByRole("complementary", { name: "Danh sách câu" });
    expect(within(palette).getByText("Đúng 2 · Đúng một phần 5 · Sai 1 · Bỏ trống 1")).toBeInTheDocument();
    const clozeBtn = within(palette).getByRole("button", { name: /Câu 4–5/ });
    expect(clozeBtn).toHaveClass("border-2");
    expect(within(palette).getByRole("button", { name: /Câu 3,/ })).toHaveClass("border-2", "border-dashed");
    expect(within(palette).getByRole("button", { name: /Câu 8–9/ })).toBeInTheDocument();

    // Prev/Next keyboard usability: focus stays on Next button, reduced motion uses behavior: "auto" (R1-27, R1-43)
    const prevBtn = within(palette).getByRole("button", { name: "Câu trước" });
    const nextBtn = within(palette).getByRole("button", { name: "Câu sau" });
    expect(prevBtn).toBeDisabled();
    nextBtn.focus();
    await userEvent.click(nextBtn);
    expect(prevBtn).toBeEnabled();
    expect(document.activeElement).toBe(nextBtn);
    expect(scrollIntoViewSpy).toHaveBeenCalledWith({ behavior: "auto", block: "start" });

    await userEvent.click(within(palette).getByRole("button", { name: /Câu 8–9/ }));
    expect(nextBtn).toBeDisabled();
  });

  it("SCORE_AND_ANSWERS tuyệt đối không render lời giải, ảnh /solution-assets/, hay essayFeedback; FULL_SOLUTION hiển thị đầy đủ (R1-7, AC-23)", async () => {
    const scoreOnlyAnswersView = renderWithProviders(
      <AttemptReviewView
        attemptId={ATTEMPT_UUID_SA}
        loadCurrentSession={async () => STUDENT_SESSION}
        loadReview={async () => makeFullReview("SCORE_AND_ANSWERS")}
      />,
    );

    expect(await screen.findByText("Không hiển thị lời giải cho mức quyền lợi hiện tại.")).toBeInTheDocument();
    const imgs = document.querySelectorAll('img[src*="/solution-assets/"]');
    expect(imgs).toHaveLength(0);
    expect(screen.queryByText(/Đáp án đúng là Hà Nội/)).not.toBeInTheDocument();
    // LT §10.2 / AT-4: essayFeedback comment and criteria table MUST be hidden under SCORE_AND_ANSWERS even if in payload
    expect(screen.queryByText("Diễn đạt mạch lạc")).not.toBeInTheDocument();
    expect(screen.queryByText("Điểm từng tiêu chí")).not.toBeInTheDocument();
    expect(screen.queryByText("Lập luận")).not.toBeInTheDocument();
    scoreOnlyAnswersView.unmount();

    const fullSolutionView = renderWithProviders(
      <AttemptReviewView
        attemptId={ATTEMPT_UUID_FULL}
        loadCurrentSession={async () => STUDENT_SESSION}
        loadReview={async () => makeFullReview("FULL_SOLUTION")}
      />,
    );

    expect(await screen.findByAltText("Minh họa lời giải")).toHaveAttribute("src", "/solution-assets/sol-1.png");
    expect(screen.queryByText("Không hiển thị lời giải cho mức quyền lợi hiện tại.")).not.toBeInTheDocument();
    expect(screen.getByText("Diễn đạt mạch lạc")).toBeInTheDocument();
    expect(screen.getByText("Điểm từng tiêu chí")).toBeInTheDocument();
    fullSolutionView.unmount();
  });

  it("lọc câu PENDING_REVIEW, xử lý QUESTION_SET chờ chấm một phần, khởi tạo từ ?part=P2, và nút Back về phần trước (R1-1, R1-2, R1-22, R1-28, R1-38, R1-50, AC-12)", async () => {
    const pendingReviewPayload: AttemptReviewResponse = {
      attemptId: ATTEMPT_UUID_PENDING,
      state: "PENDING_REVIEW",
      resultView: "SCORE_AND_ANSWERS",
      parts: [
        { partId: "p1", partCode: "P1", partName: "Phần 1 · Trắc nghiệm", ord: 1, state: "CLOSED" },
        { partId: "p2", partCode: "P2", partName: "Phần 2 · Tự chọn", ord: 2, score: 0, maxScore: 50, state: "SKIPPED" },
      ],
      items: [
        {
          itemId: "auto-1",
          partId: "p1",
          partCode: "P1",
          ord: 1,
          displayNo: 1,
          questionType: "SINGLE_CHOICE",
          content: "<p>Câu tự động chấm</p>",
          outcome: "CORRECT",
          body: { options: [{ id: "a", content: "<p>A</p>" }] },
          studentAnswer: { type: "SINGLE_CHOICE", optionId: "a" },
          expectedAnswer: { expectedOptionIds: ["a"] },
        },
        {
          itemId: "pending-essay",
          partId: "p1",
          partCode: "P1",
          ord: 2,
          displayNo: 2,
          questionType: "ESSAY",
          content: "<p>Câu tự luận chờ chấm</p>",
          outcome: "PENDING_REVIEW",
          body: { wordLimit: 300 },
        },
        {
          itemId: "pending-set",
          partId: "p1",
          partCode: "P1",
          ord: 3,
          displayNo: 3,
          questionType: "QUESTION_SET",
          body: { passage: "<p>Chùm chờ chấm toàn phần</p>" },
        },
        {
          itemId: "pending-set-child",
          parentItemId: "pending-set",
          partId: "p1",
          partCode: "P1",
          ord: 3,
          displayNo: 3,
          questionType: "ESSAY",
          content: "<p>Câu con chờ chấm</p>",
          outcome: "PENDING_REVIEW",
          body: {},
        },
        {
          itemId: "partial-null-set",
          partId: "p1",
          partCode: "P1",
          ord: 99,
          questionType: "QUESTION_SET",
          body: { passage: "<p>Chùm nửa chờ chấm (outcome null)</p>" },
        },
        {
          itemId: "partial-null-c1",
          parentItemId: "partial-null-set",
          partId: "p1",
          partCode: "P1",
          ord: 1,
          displayNo: 4,
          questionType: "SINGLE_CHOICE",
          content: "<p>Câu con tự động đúng</p>",
          outcome: "CORRECT",
          body: { options: [{ id: "x1", content: "<p>X1</p>" }] },
          studentAnswer: { type: "SINGLE_CHOICE", optionId: "x1" },
          expectedAnswer: { expectedOptionIds: ["x1"] },
        },
        {
          itemId: "partial-null-c2",
          parentItemId: "partial-null-set",
          partId: "p1",
          partCode: "P1",
          ord: 2,
          displayNo: 5,
          questionType: "ESSAY",
          content: "<p>Câu con chờ chấm ẩn</p>",
          outcome: "PENDING_REVIEW",
          body: {},
        },
        {
          itemId: "partial-pending-set",
          partId: "p1",
          partCode: "P1",
          ord: 100,
          questionType: "QUESTION_SET",
          outcome: "PENDING_REVIEW",
          body: { passage: "<p>Chùm nửa chờ chấm (outcome PENDING_REVIEW)</p>" },
        },
        {
          itemId: "partial-pending-c1",
          parentItemId: "partial-pending-set",
          partId: "p1",
          partCode: "P1",
          ord: 1,
          displayNo: 6,
          questionType: "SINGLE_CHOICE",
          content: "<p>Câu con 6 tự động đúng</p>",
          outcome: "CORRECT",
          body: { options: [{ id: "y1", content: "<p>Y1</p>" }] },
          studentAnswer: { type: "SINGLE_CHOICE", optionId: "y1" },
          expectedAnswer: { expectedOptionIds: ["y1"] },
        },
        {
          itemId: "partial-pending-c2",
          parentItemId: "partial-pending-set",
          partId: "p1",
          partCode: "P1",
          ord: 2,
          displayNo: 7,
          questionType: "ESSAY",
          content: "<p>Câu con 7 chờ chấm ẩn</p>",
          outcome: "PENDING_REVIEW",
          body: {},
        },
      ],
    };

    const view = renderWithProviders(
      <AttemptReviewView
        attemptId={ATTEMPT_UUID_PENDING}
        loadCurrentSession={async () => STUDENT_SESSION}
        loadReview={async () => pendingReviewPayload}
      />,
    );

    expect(await screen.findByText(/Lượt thi còn câu tự luận đang chờ chấm/)).toBeInTheDocument();
    expect(screen.getByText("Câu tự động chấm")).toBeInTheDocument();
    expect(screen.queryByText("Câu tự luận chờ chấm")).not.toBeInTheDocument();
    expect(screen.queryByText("Chùm chờ chấm toàn phần")).not.toBeInTheDocument();
    expect(screen.getByText("Câu con tự động đúng")).toBeInTheDocument();
    expect(screen.getByText("Câu con 6 tự động đúng")).toBeInTheDocument();
    expect(screen.queryByText("Câu con chờ chấm ẩn")).not.toBeInTheDocument();

    // Partially-pending QUESTION_SET uses child displayNo (4 and 6) and CORRECT outcome (R1-22, R1-38, R1-50)
    const palette = screen.getByRole("complementary", { name: "Danh sách câu" });
    expect(within(palette).getByRole("button", { name: "Câu 4, đúng" })).toBeInTheDocument();
    expect(within(palette).getByRole("button", { name: "Câu 6, đúng" })).toBeInTheDocument();

    const partsNav = screen.getByRole("navigation", { name: "Các phần của đề" });
    const part1Tab = within(partsNav).getByRole("link", { name: /Phần 1 · Trắc nghiệm/ });
    const part2Tab = within(partsNav).getByRole("link", { name: /Phần 2 · Tự chọn/ });
    expect(part1Tab).toHaveAttribute("aria-current", "page");

    await userEvent.click(part2Tab);
    expect(await screen.findByRole("heading", { level: 2, name: "Phần này bị bỏ qua" })).toBeInTheDocument();
    expect(screen.getByText(/0\/50 điểm/)).toBeInTheDocument();
    expect(part2Tab).toHaveAttribute("aria-current", "page");

    // Browser Back returns to "" -> Part 1 MUST reactivate (R1-1, R1-2)
    act(() => {
      navMocks.setSearch("");
    });
    expect(await screen.findByText("Câu tự động chấm")).toBeInTheDocument();
    expect(screen.queryByText("Phần này bị bỏ qua")).not.toBeInTheDocument();
    expect(part1Tab).toHaveAttribute("aria-current", "page");
    view.unmount();

    // Initial mount with ?part=P2 and case-insensitive ?part=p2 (R1-28)
    for (const query of ["?part=P2", "?part=p2"]) {
      navMocks.setSearch(query);
      const deepLinkView = renderWithProviders(
        <AttemptReviewView
          attemptId={ATTEMPT_UUID_PENDING}
          loadCurrentSession={async () => STUDENT_SESSION}
          loadReview={async () => pendingReviewPayload}
        />,
      );
      expect(await screen.findByRole("heading", { level: 2, name: "Phần này bị bỏ qua" })).toBeInTheDocument();
      expect(within(screen.getByRole("navigation", { name: "Các phần của đề" })).getByRole("link", { name: /Phần 2/ })).toHaveAttribute(
        "aria-current",
        "page",
      );
      deepLinkView.unmount();
    }
  });

  it("hỗ trợ song ngữ en sạch sẽ cho UI chrome và ẩn/hiện criteria đúng theo resultView (R1-7)", async () => {
    const saEnView = renderWithProviders(
      <AttemptReviewView
        attemptId={ATTEMPT_UUID_EN}
        loadCurrentSession={async () => STUDENT_SESSION}
        loadReview={async () => makeFullReview("SCORE_AND_ANSWERS")}
        loadResult={async () => ({ examName: "Đề HSA", scope: "FULL", attemptNo: 1, state: "SCORED", displayScore: 100, rawMaxScore: 150 })}
      />,
      { locale: "en", messages: enMessages },
    );

    expect(await screen.findByRole("heading", { level: 1, name: "Review attempt" })).toBeInTheDocument();
    expect(screen.getByText("Solutions are not included in your purchased tier.")).toBeInTheDocument();
    expect(screen.queryByText("Criterion breakdown")).not.toBeInTheDocument();
    expect(screen.queryByText("2.5 pts")).not.toBeInTheDocument();
    const palette = screen.getByRole("complementary", { name: "Question list" });
    expect(within(palette).getByRole("button", { name: "Previous" })).toBeDisabled();
    expect(within(palette).getByRole("button", { name: "Next" })).toBeEnabled();
    saEnView.unmount();

    const fullEnView = renderWithProviders(
      <AttemptReviewView
        attemptId={ATTEMPT_UUID_FULL}
        loadCurrentSession={async () => STUDENT_SESSION}
        loadReview={async () => makeFullReview("FULL_SOLUTION")}
        loadResult={async () => ({ examName: "Đề HSA", scope: "FULL", attemptNo: 1, state: "SCORED", displayScore: 100, rawMaxScore: 150 })}
      />,
      { locale: "en", messages: enMessages },
    );
    expect(await screen.findByText("Criterion breakdown")).toBeInTheDocument();
    expect(screen.getByText("2.5 pts")).toBeInTheDocument();
    fullEnView.unmount();
  });

  it("không đẩy lỗi 403 REVIEW_NOT_INCLUDED ra QueryCache nên QueryProvider không toast hay chuyển /forbidden", async () => {
    const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
    const cacheErrors: unknown[] = [];
    const unsubscribe = client.getQueryCache().subscribe((event) => {
      if (event.type === "updated" && event.action.type === "error") {
        cacheErrors.push(event.action.error);
      }
    });
    const loadResultSpy = vi.fn();

    try {
      renderWithProviders(
        <AttemptReviewView
          attemptId={ATTEMPT_UUID_GUARD}
          loadCurrentSession={async () => STUDENT_SESSION}
          loadReview={async () => {
            throw new ApiStatusError(403, "Score only", { code: "REVIEW_NOT_INCLUDED" });
          }}
          loadResult={loadResultSpy}
        />,
        { client },
      );

      expect(
        await screen.findByRole("heading", { level: 1, name: "Bài thi này không có phần xem lại bài làm" }),
      ).toBeInTheDocument();
      const backLinks = screen.getAllByRole("link", { name: "Về kết quả" });
      expect(backLinks.length).toBeGreaterThanOrEqual(1);
      expect(backLinks[0]).toHaveAttribute("href", `/attempts/${ATTEMPT_UUID_GUARD}/result`);
      expect(cacheErrors).toHaveLength(0);
      expect(loadResultSpy).not.toHaveBeenCalled();
    } finally {
      unsubscribe();
    }
  });

  it("ẩn nội dung review cũ khi refetch trả 404 hoặc 409 (R1-52)", async () => {
    const refetchErrorLoader = vi
      .fn<() => Promise<AttemptReviewResponse>>()
      .mockResolvedValueOnce(makeFullReview("FULL_SOLUTION"))
      .mockRejectedValueOnce(new ApiStatusError(404, "Gone", { code: "ATTEMPT_NOT_FOUND" }));

    const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
    renderWithProviders(
      <AttemptReviewView
        attemptId={ATTEMPT_UUID_1}
        loadCurrentSession={async () => STUDENT_SESSION}
        loadReview={refetchErrorLoader}
      />,
      { client },
    );

    expect(await screen.findByText("Chọn thủ đô của Việt Nam")).toBeInTheDocument();
    await act(async () => {
      await client.invalidateQueries();
    });
    expect(await screen.findByRole("heading", { level: 1, name: "Không tìm thấy bài làm" })).toBeInTheDocument();
    expect(screen.queryByText("Chọn thủ đô của Việt Nam")).not.toBeInTheDocument();
  });

  it("xử lý SESSION_QUERY_KEY stale khi offline (có/chưa có review cache, Thử lại gọi reviewQuery), malformed UUID offline, và Thử lại offline sau lỗi session (R2-1, R2-2)", async () => {
    // Case (i): Stale STUDENT_SESSION + cached fresh review + offline -> renders LoadedReviewContent with 1 <h1> and no offline card
    const cachedClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
    cachedClient.setQueryData(SESSION_QUERY_KEY, STUDENT_SESSION, {
      updatedAt: Date.now() - 120_000,
    });
    cachedClient.setQueryData(
      ATTEMPT_REVIEW_QUERY_KEY(ATTEMPT_UUID_1, STUDENT_SESSION.id),
      { kind: "ok", data: makeFullReview("FULL_SOLUTION") },
      { updatedAt: Date.now() },
    );

    try {
      onlineManager.setOnline(false);
      const cachedView = renderWithProviders(
        <AttemptReviewView
          attemptId={ATTEMPT_UUID_1}
          loadCurrentSession={async () => STUDENT_SESSION}
          loadReview={async () => makeFullReview("FULL_SOLUTION")}
        />,
        { client: cachedClient },
      );

      expect(await screen.findByRole("heading", { level: 1, name: "Xem lại bài làm" })).toBeInTheDocument();
      expect(screen.getAllByRole("heading", { level: 1 })).toHaveLength(1);
      expect(screen.queryByText("Không có kết nối")).not.toBeInTheDocument();
      cachedView.unmount();
    } finally {
      onlineManager.setOnline(true);
    }

    // Case (ii): Stale STUDENT_SESSION + no cached review + offline -> 1 offline card & 1 <h1>, Retry targets reviewQuery
    const coldReviewClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
    coldReviewClient.setQueryData(SESSION_QUERY_KEY, STUDENT_SESSION, {
      updatedAt: Date.now() - 120_000,
    });
    const loadReviewSpy = vi.fn(async () => makeFullReview("FULL_SOLUTION"));

    try {
      onlineManager.setOnline(false);
      const coldReviewView = renderWithProviders(
        <AttemptReviewView
          attemptId={ATTEMPT_UUID_1}
          loadCurrentSession={async () => STUDENT_SESSION}
          loadReview={loadReviewSpy}
        />,
        { client: coldReviewClient },
      );

      expect(await screen.findByRole("heading", { level: 1, name: "Không có kết nối" })).toBeInTheDocument();
      expect(screen.getAllByText("Không có kết nối")).toHaveLength(1);
      expect(screen.getAllByRole("heading", { level: 1 })).toHaveLength(1);
      expect(loadReviewSpy).not.toHaveBeenCalled();

      const reviewCacheQuery = coldReviewClient
        .getQueryCache()
        .find({ queryKey: ATTEMPT_REVIEW_QUERY_KEY(ATTEMPT_UUID_1, STUDENT_SESSION.id) })!;
      const sessionCacheQuery = coldReviewClient.getQueryCache().find({ queryKey: SESSION_QUERY_KEY })!;
      const reviewFetchSpy = vi.spyOn(reviewCacheQuery, "fetch");
      const sessionFetchSpy = vi.spyOn(sessionCacheQuery, "fetch");

      act(() => {
        onlineManager.setOnline(true);
        fireEvent.click(screen.getByRole("button", { name: "Thử lại" }));
        expect(reviewFetchSpy).toHaveBeenCalledTimes(1);
        expect(sessionFetchSpy).not.toHaveBeenCalled();
      });

      expect(await screen.findByRole("heading", { level: 1, name: "Xem lại bài làm" })).toBeInTheDocument();
      expect(loadReviewSpy).toHaveBeenCalled();
      expect(screen.getAllByRole("heading", { level: 1 })).toHaveLength(1);
      expect(screen.queryByText("Không có kết nối")).not.toBeInTheDocument();
      coldReviewView.unmount();
    } finally {
      onlineManager.setOnline(true);
    }

    // Offline with malformed attemptId still shows offline card cleanly when session has no data
    try {
      onlineManager.setOnline(false);
      const invalidOfflineView = renderWithProviders(
        <AttemptReviewView
          attemptId="not-a-uuid"
          loadCurrentSession={async () => STUDENT_SESSION}
          loadReview={async () => makeFullReview("FULL_SOLUTION")}
        />,
      );
      expect(await screen.findByRole("heading", { level: 1, name: "Không có kết nối" })).toBeInTheDocument();
      expect(screen.getAllByRole("heading", { level: 1 })).toHaveLength(1);
      invalidOfflineView.unmount();
    } finally {
      onlineManager.setOnline(true);
    }

    // Offline Retry after session error shows offline without stacking session error card
    const sessionFailThenOk = vi
      .fn<() => Promise<typeof STUDENT_SESSION>>()
      .mockRejectedValueOnce(new Error("session fail"))
      .mockResolvedValueOnce(STUDENT_SESSION);
    const errRetryView = renderWithProviders(
      <AttemptReviewView
        attemptId={ATTEMPT_UUID_1}
        loadCurrentSession={sessionFailThenOk}
        loadReview={async () => makeFullReview("FULL_SOLUTION")}
      />,
    );

    expect(await screen.findByRole("heading", { level: 1, name: "Không tải được bài làm" })).toBeInTheDocument();

    try {
      onlineManager.setOnline(false);
      await userEvent.click(screen.getByRole("button", { name: "Thử lại" }));

      expect(await screen.findByRole("heading", { level: 1, name: "Không có kết nối" })).toBeInTheDocument();
      expect(screen.queryByText("Không tải được bài làm")).not.toBeInTheDocument();
      expect(screen.getAllByRole("heading", { level: 1 })).toHaveLength(1);
      errRetryView.unmount();
    } finally {
      onlineManager.setOnline(true);
    }
  });
});
