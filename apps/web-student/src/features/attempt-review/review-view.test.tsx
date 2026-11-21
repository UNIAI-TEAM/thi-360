import { screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { ApiStatusError } from "@/lib/api/client";
import enMessages from "@/messages/en.json";
import { renderWithProviders } from "@/test/render";
import { ADMIN_SESSION, STUDENT_SESSION } from "@/test/storefront-fixtures";
import type { AttemptReviewResponse } from "./api";
import { AttemptReviewView } from "./review-view";

const navMocks = vi.hoisted(() => ({
  search: "",
}));

vi.mock("next/navigation", () => ({
  usePathname: () => "/attempts/att-1/review",
  useRouter: () => ({ push: vi.fn(), replace: vi.fn(), refresh: vi.fn() }),
  useSearchParams: () => new URLSearchParams(navMocks.search),
}));

function makeFullReview(resultView: "FULL_SOLUTION" | "SCORE_AND_ANSWERS" = "FULL_SOLUTION"): AttemptReviewResponse {
  return {
    attemptId: "att-1",
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
        earnedPoints: 1,
        outcome: "CORRECT",
        body: {
          left: [{ id: "l1", content: "<p>Nhật Bản</p>" }],
          right: [{ id: "r1", content: "<p>Tokyo</p>" }],
          reuseRight: false,
        },
        studentAnswer: { type: "MATCHING", pairs: { l1: "r1" } },
        expectedAnswer: { matchingKeys: [{ leftId: "l1", rightId: "r1" }] },
        gradingDetail: {
          pairResults: [{ leftId: "l1", matched: true, pointsEarned: 1 }],
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
    navMocks.search = "";
  });

  it("chặn admin và xử lý 403 REVIEW_NOT_INCLUDED, 409 REVIEW_NOT_AVAILABLE, 404, và lỗi mạng (AC-10, AC-17)", async () => {
    const blockedLoader = vi.fn<() => Promise<AttemptReviewResponse>>();
    const adminView = renderWithProviders(
      <AttemptReviewView
        attemptId="att-1"
        loadCurrentSession={async () => ADMIN_SESSION}
        loadReview={blockedLoader}
      />,
    );
    expect(await screen.findByRole("heading", { level: 1, name: "Tài khoản quản trị" })).toBeInTheDocument();
    expect(blockedLoader).not.toHaveBeenCalled();
    adminView.unmount();

    const forbiddenView = renderWithProviders(
      <AttemptReviewView
        attemptId="att-score-only"
        loadCurrentSession={async () => STUDENT_SESSION}
        loadReview={async () => {
          throw new ApiStatusError(403, "Score only", { code: "REVIEW_NOT_INCLUDED" });
        }}
      />,
    );
    expect(await screen.findByRole("heading", { level: 1, name: "Bài thi này không có phần xem lại bài làm" })).toBeInTheDocument();
    const backLinks403 = screen.getAllByRole("link", { name: "Về kết quả" });
    expect(backLinks403[0]).toHaveAttribute("href", "/attempts/att-score-only/result");
    forbiddenView.unmount();

    const conflictView = renderWithProviders(
      <AttemptReviewView
        attemptId="att-grading"
        loadCurrentSession={async () => STUDENT_SESSION}
        loadReview={async () => {
          throw new ApiStatusError(409, "Not ready", { code: "REVIEW_NOT_AVAILABLE" });
        }}
      />,
    );
    expect(await screen.findByRole("heading", { level: 1, name: "Chưa thể xem lại bài làm" })).toBeInTheDocument();
    conflictView.unmount();

    const notFoundView = renderWithProviders(
      <AttemptReviewView
        attemptId="att-404"
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
        attemptId="att-1"
        loadCurrentSession={async () => STUDENT_SESSION}
        loadReview={retryLoader}
        loadResult={async () => ({ examName: "Đề HSA thử" })}
      />,
    );
    expect(await screen.findByRole("alert")).toHaveTextContent("Không tải được bài làm");
    await userEvent.click(screen.getByRole("button", { name: "Thử lại" }));
    expect(await screen.findByRole("heading", { level: 1, name: "Xem lại bài làm" })).toBeInTheDocument();
    errView.unmount();
  });

  it("render đủ 8 dạng câu, giữ optionOrder, criteria tự luận không bịa max, và bảng chọn câu + Câu trước/Câu sau (AC-11)", async () => {
    renderWithProviders(
      <AttemptReviewView
        attemptId="att-1"
        loadCurrentSession={async () => STUDENT_SESSION}
        loadReview={async () => makeFullReview("FULL_SOLUTION")}
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

    // AC-11 optionOrder preserved: "Hà Nội" (opt-b) precedes "TP.HCM" (opt-a) in Q1
    const q1Article = document.getElementById("review-item-q1")!;
    const q1Text = q1Article.textContent ?? "";
    expect(q1Text.indexOf("Hà Nội")).toBeLessThan(q1Text.indexOf("TP.HCM"));

    // All 8 types rendered including SHORT_ANSWER, CLOZE, TRUE_FALSE, MATCHING, ESSAY, QUESTION_SET
    expect(screen.getByText("2 + 3 = ?")).toBeInTheDocument();
    expect(screen.getByText(/Nước sôi ở/)).toBeInTheDocument();
    expect(screen.getByText("Đánh giá mệnh đề")).toBeInTheDocument();
    expect(screen.getByText("Ghép nước và thủ đô")).toBeInTheDocument();
    expect(screen.getByText("Diễn đạt mạch lạc")).toBeInTheDocument();
    expect(screen.getByText("Lập luận")).toHaveAttribute("data-exam-content");
    expect(screen.getByText("Ví dụ rõ ràng")).toHaveAttribute("data-exam-content");
    expect(screen.getByText("2,5 điểm")).toBeInTheDocument();
    expect(screen.queryByText(/2,5\s*\//)).not.toBeInTheDocument();
    expect(screen.getByText("Đọc đoạn văn khoa học dưới đây.")).toBeInTheDocument();

    // Palette navigation & Prev/Next
    const palette = screen.getByRole("complementary", { name: "Danh sách câu" });
    const prevBtn = within(palette).getByRole("button", { name: "Câu trước" });
    const nextBtn = within(palette).getByRole("button", { name: "Câu sau" });
    expect(prevBtn).toBeDisabled();
    expect(within(palette).getByRole("button", { name: /Câu 8–9/ })).toBeInTheDocument();

    await userEvent.click(nextBtn);
    expect(prevBtn).toBeEnabled();
    await userEvent.click(within(palette).getByRole("button", { name: /Câu 8–9/ }));
    expect(nextBtn).toBeDisabled();
  });

  it("SCORE_AND_ANSWERS tuyệt đối không render lời giải/ảnh /solution-assets/; FULL_SOLUTION hiển thị ảnh lời giải (AC-23)", async () => {
    const scoreOnlyAnswersView = renderWithProviders(
      <AttemptReviewView
        attemptId="att-sa"
        loadCurrentSession={async () => STUDENT_SESSION}
        loadReview={async () => makeFullReview("SCORE_AND_ANSWERS")}
      />,
    );

    expect(await screen.findByText("Không hiển thị lời giải cho mức quyền lợi hiện tại.")).toBeInTheDocument();
    const imgs = document.querySelectorAll('img[src*="/solution-assets/"]');
    expect(imgs).toHaveLength(0);
    expect(screen.queryByText(/Đáp án đúng là Hà Nội/)).not.toBeInTheDocument();
    scoreOnlyAnswersView.unmount();

    const fullSolutionView = renderWithProviders(
      <AttemptReviewView
        attemptId="att-full"
        loadCurrentSession={async () => STUDENT_SESSION}
        loadReview={async () => makeFullReview("FULL_SOLUTION")}
      />,
    );

    expect(await screen.findByAltText("Minh họa lời giải")).toHaveAttribute("src", "/solution-assets/sol-1.png");
    expect(screen.queryByText("Không hiển thị lời giải cho mức quyền lợi hiện tại.")).not.toBeInTheDocument();
    fullSolutionView.unmount();
  });

  it("lọc câu PENDING_REVIEW ở trạng thái PENDING_REVIEW và chuyển tab Phần 2 SKIPPED qua nav & ?part= (AC-12)", async () => {
    const pendingReviewPayload: AttemptReviewResponse = {
      attemptId: "att-pending",
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
      ],
    };

    renderWithProviders(
      <AttemptReviewView
        attemptId="att-pending"
        loadCurrentSession={async () => STUDENT_SESSION}
        loadReview={async () => pendingReviewPayload}
      />,
    );

    expect(await screen.findByText(/Lượt thi còn câu tự luận đang chờ chấm/)).toBeInTheDocument();
    expect(screen.getByText("Câu tự động chấm")).toBeInTheDocument();
    expect(screen.queryByText("Câu tự luận chờ chấm")).not.toBeInTheDocument();
    expect(screen.queryByText("Chùm chờ chấm toàn phần")).not.toBeInTheDocument();

    const partsNav = screen.getByRole("navigation", { name: "Các phần của đề" });
    await userEvent.click(within(partsNav).getByRole("link", { name: /Phần 2 · Tự chọn/ }));
    expect(await screen.findByRole("heading", { level: 2, name: "Phần này bị bỏ qua" })).toBeInTheDocument();
    expect(screen.getByText(/0\/50 điểm/)).toBeInTheDocument();
  });

  it("hỗ trợ song ngữ en sạch sẽ cho UI chrome và criteria", async () => {
    renderWithProviders(
      <AttemptReviewView
        attemptId="att-en"
        loadCurrentSession={async () => STUDENT_SESSION}
        loadReview={async () => makeFullReview("SCORE_AND_ANSWERS")}
        loadResult={async () => ({ examName: "Đề HSA", scope: "FULL", attemptNo: 1, state: "SCORED", displayScore: 100, rawMaxScore: 150 })}
      />,
      { locale: "en", messages: enMessages },
    );

    expect(await screen.findByRole("heading", { level: 1, name: "Review attempt" })).toBeInTheDocument();
    expect(screen.getByText("Solutions are not included in your purchased tier.")).toBeInTheDocument();
    expect(screen.getByText("Criterion breakdown")).toBeInTheDocument();
    expect(screen.getByText("2.5 pts")).toBeInTheDocument();
  });
});
