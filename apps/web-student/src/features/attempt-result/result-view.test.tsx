import { readFileSync } from "node:fs";
import path from "node:path";
import { onlineManager, QueryClient } from "@tanstack/react-query";
import { act, fireEvent, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, describe, expect, it, vi } from "vitest";
import { SESSION_QUERY_KEY } from "@/features/session/api";
import { ApiStatusError } from "@/lib/api/client";
import enMessages from "@/messages/en.json";
import { installContainerWidth, PHONE_CONTENT_WIDTH } from "@/test/container-width";
import { renderWithProviders } from "@/test/render";
import { ADMIN_SESSION, STUDENT_SESSION } from "@/test/storefront-fixtures";
import {
  ATTEMPT_RESULT_QUERY_KEY,
  GRADING_POLL_INTERVAL_MS,
  GRADING_POLL_TIMEOUT_MS,
  loadCatalogOptions,
  type AttemptResultResponse,
  type CatalogOptionResponse,
} from "./api";
import { AttemptResultView } from "./result-view";

const ATTEMPT_UUID_1 = "11111111-1111-4111-8111-111111111101";
const ATTEMPT_UUID_404 = "11111111-1111-4111-8111-111111111404";
const ATTEMPT_UUID_OPEN = "11111111-1111-4111-8111-111111111002";
const ATTEMPT_UUID_ZERO = "11111111-1111-4111-8111-111111111003";
const ATTEMPT_UUID_POLL = "11111111-1111-4111-8111-111111111004";
const ATTEMPT_UUID_TIMEOUT = "11111111-1111-4111-8111-111111111005";
const ATTEMPT_UUID_GERR = "11111111-1111-4111-8111-111111111006";
const ATTEMPT_UUID_PENDING = "11111111-1111-4111-8111-111111111007";
const ATTEMPT_UUID_REGRADE = "11111111-1111-4111-8111-111111111008";
const ATTEMPT_UUID_FAULT = "11111111-1111-4111-8111-111111111009";
const ATTEMPT_UUID_EN = "11111111-1111-4111-8111-111111111010";

const EXAM_KINDS: CatalogOptionResponse[] = [
  { id: "ek-hsa", code: "HSA", nameVi: "ĐGNL HSA", nameEn: "HSA Assessment", displayOrder: 1, value: 1 },
  { id: "ek-vact", code: "VACT", nameVi: "ĐGNL ĐHQG TP.HCM", nameEn: "", displayOrder: 2, value: 2 },
];

const SUBJECTS: CatalogOptionResponse[] = [
  { id: "s-toan", code: "TOAN", nameVi: "Toán học", nameEn: "Mathematics", displayOrder: 1, value: 1 },
  { id: "s-vatli", code: "VATLI", nameVi: "Vật lí", nameEn: "Physics", displayOrder: 2, value: 2 },
  { id: "s-hoahoc", code: "HOAHOC", nameVi: "Hóa học", nameEn: "", displayOrder: 3, value: 3 },
  { id: "s-nguvan", code: "NGUVAN", nameVi: "Ngữ văn", nameEn: "Literature", displayOrder: 4, value: 4 },
];

function makeResult(overrides: Partial<AttemptResultResponse> = {}): AttemptResultResponse {
  return {
    attemptId: ATTEMPT_UUID_1,
    attemptNo: 2,
    examId: "22222222-2222-4222-8222-222222222201",
    examName: "Đề HSA tổng hợp số 1",
    examVersionNo: 1,
    examKindCode: "HSA",
    scope: "FULL",
    state: "SCORED",
    resultView: "FULL_SOLUTION",
    reviewAvailable: true,
    rawScore: 108.5,
    rawMaxScore: 150,
    displayScore: 108.5,
    startedAt: "2026-11-20T01:00:00Z",
    finishedAt: "2026-11-20T02:15:30Z",
    gradedAt: "2026-11-20T02:15:35Z",
    endReason: "SUBMITTED",
    choices: {
      "part-3": { optionCode: "SCIENCE", subjectCodes: ["VATLI", "HOAHOC"] },
    },
    statistics: {
      correctCount: 95,
      partialCount: 4,
      wrongCount: 15,
      blankCount: 6,
      pendingReviewCount: 0,
      totalCount: 120,
    },
    parts: [
      {
        partId: "p-1",
        partCode: "TOAN",
        partName: "Phần 1 · Toán học & Xử lí số liệu",
        ord: 1,
        score: 42,
        maxScore: 50,
        pendingReview: false,
        skipped: false,
        stats: { correctCount: 42, partialCount: 0, wrongCount: 6, blankCount: 2, pendingReviewCount: 0, totalCount: 50 },
      },
    ],
    subjects: [
      {
        code: "TOAN",
        name: "Toán học",
        score: 42,
        maxScore: 50,
        pendingReview: false,
        stats: { correctCount: 42, partialCount: 0, wrongCount: 6, blankCount: 2, pendingReviewCount: 0, totalCount: 50 },
      },
      {
        code: "HOAHOC",
        name: "Hóa học",
        score: 30,
        maxScore: 50,
        pendingReview: false,
        stats: { correctCount: 30, partialCount: 0, wrongCount: 4, blankCount: 0, pendingReviewCount: 0, totalCount: 34 },
      },
    ],
    ...overrides,
  };
}

describe("AttemptResultView · AC-1..AC-9, AC-17..AC-19, AC-22", () => {
  afterEach(() => {
    onlineManager.setOnline(true);
    vi.useRealTimers();
    vi.restoreAllMocks();
  });

  it("chặn admin và xử lý các lỗi 404 (kể cả non-UUID), 409 ATTEMPT_NOT_FINISHED, offline, và lỗi mạng + Thử lại (AC-17)", async () => {
    const neverLoaded = vi.fn<() => Promise<AttemptResultResponse>>();
    const adminView = renderWithProviders(
      <AttemptResultView
        attemptId={ATTEMPT_UUID_1}
        loadCurrentSession={async () => ADMIN_SESSION}
        loadResult={neverLoaded}
        loadExamKinds={async () => EXAM_KINDS}
        loadSubjects={async () => SUBJECTS}
      />,
    );
    expect(await screen.findByRole("heading", { level: 1, name: "Tài khoản quản trị" })).toBeInTheDocument();
    expect(neverLoaded).not.toHaveBeenCalled();
    adminView.unmount();

    const nonUuidLoader = vi.fn<() => Promise<AttemptResultResponse>>();
    const nonUuidView = renderWithProviders(
      <AttemptResultView
        attemptId="not-a-uuid"
        loadCurrentSession={async () => STUDENT_SESSION}
        loadResult={nonUuidLoader}
        loadExamKinds={async () => EXAM_KINDS}
        loadSubjects={async () => SUBJECTS}
      />,
    );
    expect(await screen.findByRole("heading", { level: 1, name: "Không tìm thấy kết quả lượt thi" })).toBeInTheDocument();
    expect(nonUuidLoader).not.toHaveBeenCalled();
    nonUuidView.unmount();

    const notFoundView = renderWithProviders(
      <AttemptResultView
        attemptId={ATTEMPT_UUID_404}
        loadCurrentSession={async () => STUDENT_SESSION}
        loadResult={async () => {
          throw new ApiStatusError(404, "Not found", { code: "ATTEMPT_NOT_FOUND" });
        }}
        loadExamKinds={async () => EXAM_KINDS}
        loadSubjects={async () => SUBJECTS}
      />,
    );
    expect(await screen.findByRole("heading", { level: 1, name: "Không tìm thấy kết quả lượt thi" })).toBeInTheDocument();
    const mainNotFound = within(screen.getByRole("main"));
    const historyLinks = mainNotFound.getAllByRole("link", { name: "Lịch sử luyện thi" });
    expect(historyLinks.length).toBeGreaterThanOrEqual(2);
    expect(historyLinks[1]).toHaveAttribute("href", "/history");
    expect(mainNotFound.getByRole("link", { name: "Bài thi của tôi" })).toHaveAttribute("href", "/home");
    notFoundView.unmount();

    const notFinishedView = renderWithProviders(
      <AttemptResultView
        attemptId={ATTEMPT_UUID_OPEN}
        loadCurrentSession={async () => STUDENT_SESSION}
        loadResult={async () => {
          throw new ApiStatusError(409, "Open", { code: "ATTEMPT_NOT_FINISHED" });
        }}
        loadExamKinds={async () => EXAM_KINDS}
        loadSubjects={async () => SUBJECTS}
      />,
    );
    expect(await screen.findByRole("heading", { level: 1, name: "Lượt thi chưa kết thúc" })).toBeInTheDocument();
    const continueLink = screen.getByRole("link", { name: "Tiếp tục làm bài" });
    expect(continueLink).toHaveAttribute("href", `/attempts/${ATTEMPT_UUID_OPEN}`);
    expect(continueLink.className).toContain("bg-accent-500");
    notFinishedView.unmount();

    const retryLoader = vi
      .fn<(id: string) => Promise<AttemptResultResponse>>()
      .mockRejectedValueOnce(new Error("network"))
      .mockResolvedValueOnce(makeResult());
    const errView = renderWithProviders(
      <AttemptResultView
        attemptId={ATTEMPT_UUID_1}
        loadCurrentSession={async () => STUDENT_SESSION}
        loadResult={retryLoader}
        loadExamKinds={async () => EXAM_KINDS}
        loadSubjects={async () => SUBJECTS}
      />,
    );
    expect(await screen.findByRole("alert")).toHaveTextContent("Không tải được kết quả lượt thi");
    await userEvent.click(screen.getByRole("button", { name: "Thử lại" }));
    expect(await screen.findByRole("heading", { level: 1, name: "Đề HSA tổng hợp số 1" })).toBeInTheDocument();
    errView.unmount();

    try {
      onlineManager.setOnline(false);
      const offlineView = renderWithProviders(
        <AttemptResultView
          attemptId={ATTEMPT_UUID_1}
          loadCurrentSession={async () => STUDENT_SESSION}
          loadResult={async () => makeResult()}
          loadExamKinds={async () => EXAM_KINDS}
          loadSubjects={async () => SUBJECTS}
        />,
      );
      expect(await screen.findByRole("heading", { level: 1, name: "Không có kết nối" })).toBeInTheDocument();
      expect(screen.getByRole("button", { name: "Thử lại" })).toHaveClass("h-11");
      offlineView.unmount();
    } finally {
      onlineManager.setOnline(true);
    }
  });

  it("hiển thị đầy đủ kết quả SCORED, data-exam-content, SCORE ẩn CTA xem lại, SKIPPED part, và điểm 0 thật (AC-1, AC-3, AC-4, AC-8, AC-9)", async () => {
    const scoredFull = makeResult({
      parts: [
        ...makeResult().parts!,
        {
          partId: "p-3",
          partCode: "CHOICE_3",
          partName: "Phần 3 · Tự chọn",
          ord: 3,
          score: 0,
          maxScore: 50,
          skipped: true,
          pendingReview: false,
        },
      ],
      endReason: "CHOICE_TIMEOUT",
    });

    const view = renderWithProviders(
      <AttemptResultView
        attemptId={ATTEMPT_UUID_1}
        loadCurrentSession={async () => STUDENT_SESSION}
        loadResult={async () => scoredFull}
        loadExamKinds={async () => EXAM_KINDS}
        loadSubjects={async () => SUBJECTS}
      />,
    );

    const heading = await screen.findByRole("heading", { level: 1, name: "Đề HSA tổng hợp số 1" });
    expect(heading).toHaveAttribute("data-exam-content");
    const kindBadges = await screen.findAllByText("ĐGNL HSA");
    expect(kindBadges.length).toBeGreaterThanOrEqual(1);
    expect(kindBadges[0]).not.toHaveAttribute("data-exam-content");
    expect(screen.getAllByText("Lần 2").length).toBeGreaterThanOrEqual(2);
    expect(screen.getByText("Đã chấm")).toBeInTheDocument();
    expect(screen.getByText("108,5")).toBeInTheDocument();
    expect(screen.getByText("/150")).toBeInTheDocument();

    // 4 buckets on SCORED (pending bucket hidden when 0) + exact counts + denominator (R1-32)
    const totalSection = screen.getByRole("region", { name: "Tổng điểm" });
    expect(screen.getByText("Trên 120 câu phải làm.")).toBeInTheDocument();
    expect(within(totalSection).queryByText("Chờ chấm")).not.toBeInTheDocument();
    expect(within(totalSection).getByText("95")).toBeInTheDocument();
    expect(within(totalSection).getByText("4")).toBeInTheDocument();
    expect(within(totalSection).getByText("15")).toBeInTheDocument();
    expect(within(totalSection).getByText("6")).toBeInTheDocument();

    // Scored part breakdown + desktop <table> subject rows (R1-32, R1-45)
    const partsSection = screen.getByRole("region", { name: "Điểm từng phần" });
    expect(within(partsSection).getByText((_, node) => node?.tagName === "DIV" && node.textContent === "42/50")).toBeInTheDocument();
    const subjectsSection = screen.getByRole("region", { name: "Điểm theo môn" });
    const subjectsTable = within(subjectsSection).getByRole("table", { name: "Điểm theo môn" });
    const subjectRows = within(subjectsTable).getAllByRole("row");
    expect(subjectRows[1]).toHaveTextContent("Toán học");
    expect(subjectRows[1]).toHaveTextContent("42/50");
    expect(subjectRows[2]).toHaveTextContent("Hóa học");
    expect(subjectRows[2]).toHaveTextContent("30/50");

    // Metadata <dl>: choices + Asia/Ho_Chi_Minh (+07) timestamp + duration + endReason
    expect(await screen.findByText("Khoa học: Vật lí, Hóa học")).toBeInTheDocument();
    expect(screen.getByText("08:00, 20/11/2026")).toBeInTheDocument();
    expect(screen.getByText("09:15, 20/11/2026")).toBeInTheDocument();
    expect(screen.getByText("1 giờ 15 phút")).toBeInTheDocument();
    expect(screen.getByText("Quá hạn chọn phần 3")).toBeInTheDocument();

    // Review CTA visible for FULL_SOLUTION
    expect(screen.getByRole("link", { name: "Xem lại bài" })).toHaveAttribute("href", `/attempts/${ATTEMPT_UUID_1}/review`);

    // SKIPPED part with CHOICE_TIMEOUT
    expect(screen.getByText("Không làm phần 3: quá thời gian chọn")).toBeInTheDocument();
    expect(screen.getByText("Bị bỏ qua")).toBeInTheDocument();
    view.unmount();

    // Score-only + genuine 0 score (AC-4)
    const zeroScoreOnly = makeResult({
      resultView: "SCORE",
      reviewAvailable: false,
      displayScore: 0,
      rawScore: 0,
      rawMaxScore: 100,
    });
    const zeroView = renderWithProviders(
      <AttemptResultView
        attemptId={ATTEMPT_UUID_ZERO}
        loadCurrentSession={async () => STUDENT_SESSION}
        loadResult={async () => zeroScoreOnly}
        loadExamKinds={async () => EXAM_KINDS}
        loadSubjects={async () => SUBJECTS}
      />,
    );
    expect(await screen.findByText("Bài thi này chỉ cho xem điểm, không có phần xem lại bài làm.")).toBeInTheDocument();
    expect(screen.queryByRole("link", { name: /Xem lại/ })).not.toBeInTheDocument();
    const totalRegion = screen.getByRole("region", { name: "Tổng điểm" });
    expect(within(totalRegion).getByText((_, node) => node?.tagName === "DIV" && node.textContent === "0/100")).toBeInTheDocument();
    expect(screen.queryByText("Chưa có điểm")).not.toBeInTheDocument();
    zeroView.unmount();
  });

  it("hiển thị bảng môn dạng thẻ CardList ở màn hình hẹp (useCardMode)", async () => {
    installContainerWidth(PHONE_CONTENT_WIDTH);
    renderWithProviders(
      <AttemptResultView
        attemptId={ATTEMPT_UUID_1}
        loadCurrentSession={async () => STUDENT_SESSION}
        loadResult={async () => makeResult()}
        loadExamKinds={async () => EXAM_KINDS}
        loadSubjects={async () => SUBJECTS}
      />,
    );

    const subjectSection = await screen.findByRole("region", { name: "Điểm theo môn" });
    expect(within(subjectSection).queryByRole("table")).not.toBeInTheDocument();
    expect(within(subjectSection).getByRole("list", { name: "Điểm theo môn" })).toBeInTheDocument();
    expect(within(subjectSection).getByText("Điểm môn: 42/50")).toBeInTheDocument();
  });

  it("poll GRADING mỗi 3s rồi tự dừng khi SCORED, xử lý lỗi refetch/404/409 khi poll, và dừng kèm Tải lại sau 2 phút timeout (AC-6)", async () => {
    vi.useFakeTimers();

    const gradingThenScored = vi
      .fn<(id: string) => Promise<AttemptResultResponse>>()
      .mockResolvedValueOnce(makeResult({ state: "GRADING", displayScore: 0, rawScore: 0 }))
      .mockResolvedValueOnce(makeResult({ state: "GRADING", displayScore: 0, rawScore: 0 }))
      .mockResolvedValueOnce(makeResult({ state: "SCORED", displayScore: 130, rawMaxScore: 150 }));

    const pollingView = renderWithProviders(
      <AttemptResultView
        attemptId={ATTEMPT_UUID_POLL}
        loadCurrentSession={async () => STUDENT_SESSION}
        loadResult={gradingThenScored}
        loadExamKinds={async () => EXAM_KINDS}
        loadSubjects={async () => SUBJECTS}
      />,
    );

    await act(async () => {
      await vi.advanceTimersByTimeAsync(10);
    });
    expect(screen.getByRole("heading", { level: 2, name: "Đang chấm" })).toBeInTheDocument();
    expect(screen.queryByText(/\/150/)).not.toBeInTheDocument();
    expect(gradingThenScored).toHaveBeenCalledTimes(1);

    await act(async () => {
      await vi.advanceTimersByTimeAsync(GRADING_POLL_INTERVAL_MS);
    });
    expect(gradingThenScored).toHaveBeenCalledTimes(2);
    expect(screen.getByRole("heading", { level: 2, name: "Đang chấm" })).toBeInTheDocument();

    await act(async () => {
      await vi.advanceTimersByTimeAsync(GRADING_POLL_INTERVAL_MS);
    });
    expect(gradingThenScored).toHaveBeenCalledTimes(3);
    expect(screen.getByText("130")).toBeInTheDocument();

    // Does not poll again after SCORED
    await act(async () => {
      await vi.advanceTimersByTimeAsync(GRADING_POLL_INTERVAL_MS * 3);
    });
    expect(gradingThenScored).toHaveBeenCalledTimes(3);
    pollingView.unmount();

    // Poll network error renders inline role="alert" banner with Retry (R1-20, R1-37)
    const pollNetworkErr = vi
      .fn<(id: string) => Promise<AttemptResultResponse>>()
      .mockResolvedValueOnce(makeResult({ state: "GRADING", displayScore: undefined, rawScore: undefined }))
      .mockRejectedValueOnce(new Error("poll network error"));
    const pollErrView = renderWithProviders(
      <AttemptResultView
        attemptId="11111111-1111-4111-8111-111111111011"
        loadCurrentSession={async () => STUDENT_SESSION}
        loadResult={pollNetworkErr}
        loadExamKinds={async () => EXAM_KINDS}
        loadSubjects={async () => SUBJECTS}
      />,
    );
    await act(async () => {
      await vi.advanceTimersByTimeAsync(10);
    });
    await act(async () => {
      await vi.advanceTimersByTimeAsync(GRADING_POLL_INTERVAL_MS);
    });
    const inlineAlert = screen.getByRole("alert");
    expect(inlineAlert).toHaveTextContent("Không tải được kết quả lượt thi");
    expect(within(inlineAlert).getByRole("button", { name: "Thử lại" })).toBeInTheDocument();
    pollErrView.unmount();

    // Poll 404/409 mutual exclusion: LoadedResultContent must unmount (R1-21)
    const poll404 = vi
      .fn<(id: string) => Promise<AttemptResultResponse>>()
      .mockResolvedValueOnce(makeResult({ state: "GRADING", displayScore: undefined, rawScore: undefined }))
      .mockRejectedValueOnce(new ApiStatusError(404, "Missing", { code: "ATTEMPT_NOT_FOUND" }));
    const poll404View = renderWithProviders(
      <AttemptResultView
        attemptId="11111111-1111-4111-8111-111111111012"
        loadCurrentSession={async () => STUDENT_SESSION}
        loadResult={poll404}
        loadExamKinds={async () => EXAM_KINDS}
        loadSubjects={async () => SUBJECTS}
      />,
    );
    await act(async () => {
      await vi.advanceTimersByTimeAsync(10);
    });
    await act(async () => {
      await vi.advanceTimersByTimeAsync(GRADING_POLL_INTERVAL_MS);
    });
    expect(screen.getByRole("heading", { level: 1, name: "Không tìm thấy kết quả lượt thi" })).toBeInTheDocument();
    expect(screen.queryByRole("heading", { level: 1, name: "Đề HSA tổng hợp số 1" })).not.toBeInTheDocument();
    poll404View.unmount();

    // Timeout flow at 120_000ms
    const alwaysGrading = vi
      .fn<(id: string) => Promise<AttemptResultResponse>>()
      .mockResolvedValue(makeResult({ state: "GRADING", displayScore: undefined, rawScore: undefined }));
    const timeoutView = renderWithProviders(
      <AttemptResultView
        attemptId={ATTEMPT_UUID_TIMEOUT}
        loadCurrentSession={async () => STUDENT_SESSION}
        loadResult={alwaysGrading}
        loadExamKinds={async () => EXAM_KINDS}
        loadSubjects={async () => SUBJECTS}
      />,
    );

    await act(async () => {
      await vi.advanceTimersByTimeAsync(10);
    });
    expect(screen.getByRole("heading", { level: 2, name: "Đang chấm" })).toBeInTheDocument();

    await act(async () => {
      await vi.advanceTimersByTimeAsync(GRADING_POLL_TIMEOUT_MS + 100);
    });
    expect(screen.getByRole("heading", { level: 2, name: "Đang tính điểm, bạn có thể quay lại sau" })).toBeInTheDocument();
    const callsAtTimeout = alwaysGrading.mock.calls.length;

    await act(async () => {
      await vi.advanceTimersByTimeAsync(GRADING_POLL_INTERVAL_MS * 3);
    });
    expect(alwaysGrading).toHaveBeenCalledTimes(callsAtTimeout);

    fireEvent.click(screen.getByRole("button", { name: "Tải lại" }));
    await act(async () => {
      await vi.advanceTimersByTimeAsync(10);
    });
    expect(alwaysGrading.mock.calls.length).toBeGreaterThan(callsAtTimeout);
    timeoutView.unmount();
  });

  it("hiển thị GRADING_ERROR, PENDING_REVIEW (F16), REGRADING (AC-19), và F20 compensated/fault (AC-5, AC-7, AC-18)", async () => {
    const gradingErrView = renderWithProviders(
      <AttemptResultView
        attemptId={ATTEMPT_UUID_GERR}
        loadCurrentSession={async () => STUDENT_SESSION}
        loadResult={async () =>
          makeResult({ state: "GRADING_ERROR", displayScore: 0, rawScore: 0, reviewAvailable: false })
        }
        loadExamKinds={async () => EXAM_KINDS}
        loadSubjects={async () => SUBJECTS}
      />,
    );
    expect(await screen.findByText("Bài làm đã được ghi nhận, kết quả đang được xử lý")).toBeInTheDocument();
    expect(screen.queryByRole("link", { name: /Xem lại/ })).not.toBeInTheDocument();
    expect(screen.queryByText(/\/150/)).not.toBeInTheDocument();
    gradingErrView.unmount();

    // PENDING_REVIEW with non-null 0/numeric score suppressed + F16 part/subject scores & disclaimers (R1-32, R1-33)
    const pendingView = renderWithProviders(
      <AttemptResultView
        attemptId={ATTEMPT_UUID_PENDING}
        loadCurrentSession={async () => STUDENT_SESSION}
        loadResult={async () =>
          makeResult({
            state: "PENDING_REVIEW",
            displayScore: 0,
            rawScore: 75,
            statistics: {
              correctCount: 68,
              partialCount: 2,
              wrongCount: 10,
              blankCount: 5,
              pendingReviewCount: 2,
              totalCount: 87,
            },
            parts: [
              { partId: "p1", partCode: "TOAN", partName: "Toán", score: 75, maxScore: 100, pendingReview: false },
              { partId: "p2", partCode: "VAN", partName: "Ngữ văn", maxScore: 50, pendingReview: true },
            ],
            subjects: [
              { code: "TOAN", score: 75, maxScore: 100, pendingReview: false },
              { code: "NGUVAN", maxScore: 50, pendingReview: true },
            ],
          })
        }
        loadExamKinds={async () => EXAM_KINDS}
        loadSubjects={async () => SUBJECTS}
      />,
    );
    expect(await screen.findByText("75")).toBeInTheDocument();
    expect(screen.queryByText(/\/150/)).not.toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Xem lại câu đã chấm tự động" })).toHaveAttribute(
      "href",
      `/attempts/${ATTEMPT_UUID_PENDING}/review`,
    );
    expect(screen.getAllByText("Chấm tay").length).toBeGreaterThanOrEqual(2);
    // PENDING_REVIEW keeps the pending stat bucket visible alongside the pending score placeholder
    const pendingTotalSection = screen.getByRole("region", { name: "Tổng điểm" });
    const pendingLabels = within(pendingTotalSection).getAllByText("Chờ chấm");
    expect(pendingLabels).toHaveLength(2);
    const pendingBucket = pendingLabels[1]!.closest("div")!;
    expect(within(pendingBucket).getByText("2")).toBeInTheDocument();
    expect(within(pendingTotalSection).getAllByText("2")).toHaveLength(2);
    expect(within(pendingTotalSection).getByText("68")).toBeInTheDocument();
    // F16 disclaimers on part & subject breakdowns (R1-33)
    expect(screen.getByText("Điểm của phần đã chấm xong là điểm riêng của phần đó, chưa phải điểm tổng.")).toBeInTheDocument();
    expect(screen.getByText("Điểm của môn đã chấm xong là điểm riêng của môn đó, chưa phải điểm tổng.")).toBeInTheDocument();
    pendingView.unmount();

    // REGRADING keeps previous score visible + FAULT + compensated banners
    const regradingView = renderWithProviders(
      <AttemptResultView
        attemptId={ATTEMPT_UUID_REGRADE}
        loadCurrentSession={async () => STUDENT_SESSION}
        loadResult={async () =>
          makeResult({
            state: "PENDING_REVIEW",
            regrading: true,
            displayScore: 112,
            rawScore: 112,
            rawMaxScore: 150,
            compensated: true,
            faultFlag: "SYSTEM_FAULT",
            faultReason: "Mất kết nối máy chủ thi lúc 10:14",
          })
        }
        loadExamKinds={async () => EXAM_KINDS}
        loadSubjects={async () => SUBJECTS}
      />,
    );
    expect(await screen.findByText("Lượt bị ảnh hưởng bởi lỗi hệ thống, đã cấp bù 1 lượt")).toBeInTheDocument();
    const reasonEl = screen.getByText("Mất kết nối máy chủ thi lúc 10:14");
    expect(reasonEl).toHaveAttribute("data-exam-content");
    expect(screen.getAllByText("Đang chấm lại").length).toBeGreaterThanOrEqual(2);
    expect(screen.getByText("112")).toBeInTheDocument();
    expect(screen.getByText("/150")).toBeInTheDocument();
    regradingView.unmount();

    const faultOnlyView = renderWithProviders(
      <AttemptResultView
        attemptId={ATTEMPT_UUID_FAULT}
        loadCurrentSession={async () => STUDENT_SESSION}
        loadResult={async () =>
          makeResult({
            compensated: false,
            faultFlag: "SYSTEM_FAULT",
            endReason: "FAULT_CLOSED",
            parts: [
              {
                partId: "p-fault",
                partCode: "P2",
                partName: "Phần 2",
                ord: 2,
                score: 0,
                maxScore: 50,
                skipped: true,
                pendingReview: false,
              },
            ],
          })
        }
        loadExamKinds={async () => EXAM_KINDS}
        loadSubjects={async () => SUBJECTS}
      />,
    );
    expect(await screen.findByText("Lượt kết thúc do lỗi hệ thống")).toBeInTheDocument();
    expect(screen.getByText("Phần bị bỏ qua do lượt kết thúc vì lỗi hệ thống.")).toBeInTheDocument();
    faultOnlyView.unmount();

    // TIME_UP fallback skipped reason (R1-59)
    const timeUpSkipView = renderWithProviders(
      <AttemptResultView
        attemptId="11111111-1111-4111-8111-111111111013"
        loadCurrentSession={async () => STUDENT_SESSION}
        loadResult={async () =>
          makeResult({
            endReason: "TIME_UP",
            parts: [
              {
                partId: "p-tu",
                partCode: "P3",
                partName: "Phần 3",
                ord: 3,
                score: 0,
                maxScore: 50,
                skipped: true,
                pendingReview: false,
              },
            ],
          })
        }
        loadExamKinds={async () => EXAM_KINDS}
        loadSubjects={async () => SUBJECTS}
      />,
    );
    expect(await screen.findByText("Phần này đã bị bỏ qua và tính 0 điểm.")).toBeInTheDocument();
    timeUpSkipView.unmount();
  });

  it("render V-ACT điểm luyện tập với mọi thang điểm động (1200, 600, 300, 180, 120, fallback rawMaxScore), 4 thành phần không lộ điểm và 2 tiểu mục Tư duy khoa học (AC-2, AC-22)", async () => {
    const scales = [
      { scaleMax: 1200, rawMaxScore: 120, displayScore: 980, expectedDenom: "/1.200", uuid: "11111111-1111-4111-8111-111111111201" },
      { scaleMax: 600, rawMaxScore: 60, displayScore: 490, expectedDenom: "/600", uuid: "11111111-1111-4111-8111-111111111202" },
      { scaleMax: 300, rawMaxScore: 30, displayScore: 245, expectedDenom: "/300", uuid: "11111111-1111-4111-8111-111111111203" },
      { scaleMax: 180, rawMaxScore: 18, displayScore: 150, expectedDenom: "/180", uuid: "11111111-1111-4111-8111-111111111204" },
      { scaleMax: 120, rawMaxScore: 12, displayScore: 95, expectedDenom: "/120", uuid: "11111111-1111-4111-8111-111111111205" },
      { scaleMax: undefined, rawMaxScore: 90, displayScore: 72, expectedDenom: "/90", uuid: "11111111-1111-4111-8111-111111111206" },
    ];

    for (const s of scales) {
      const vactResult = makeResult({
        examKindCode: "VACT",
        examName: "Đề V-ACT tổng hợp",
        practiceScoring: "PRACTICE",
        scaleMax: s.scaleMax,
        rawMaxScore: s.rawMaxScore,
        displayScore: s.displayScore,
        vactComponents: [
          { componentCode: "VIETNAMESE", correctCount: 25, totalCount: 30, correctRate: 83.33 },
          { componentCode: "ANH", correctCount: 24, totalCount: 30, correctRate: 80 },
          { componentCode: "TOAN", correctCount: 26, totalCount: 30, correctRate: 86.67 },
          {
            componentCode: "SCIENTIFIC_THINKING",
            correctCount: 22,
            totalCount: 30,
            correctRate: 73.33,
            subsections: [
              { sectionId: "l", questionCount: 12, correctCount: 10, totalCount: 12, correctRate: 83.33 },
              { sectionId: "sr", questionCount: 18, correctCount: 12, totalCount: 18, correctRate: 66.67 },
            ],
          },
        ],
        subjects: [
          { code: "TOAN", score: 26, maxScore: 30 },
        ],
      });

      const vactView = renderWithProviders(
        <AttemptResultView
          attemptId={s.uuid}
          loadCurrentSession={async () => STUDENT_SESSION}
          loadResult={async () => vactResult}
          loadExamKinds={async () => EXAM_KINDS}
          loadSubjects={async () => SUBJECTS}
        />,
      );

      expect(await screen.findByText(s.expectedDenom)).toBeInTheDocument();
      expect(screen.getByRole("heading", { level: 2, name: "Tổng điểm" })).toBeInTheDocument();
      expect(screen.getAllByText("Điểm luyện tập")).toHaveLength(1);
      expect(screen.getByText("Không phải điểm V-ACT chính thức")).toBeInTheDocument();
      expect(screen.queryByRole("region", { name: "Điểm theo môn" })).not.toBeInTheDocument();

      const componentsRegion = screen.getByRole("region", { name: "Kết quả theo thành phần" });
      expect(within(componentsRegion).getByText("Tiếng Việt")).toBeInTheDocument();
      expect(within(componentsRegion).getByText("25/30 câu đúng · 83,33%")).toBeInTheDocument();
      expect(within(componentsRegion).getByText("Tiếng Anh")).toBeInTheDocument();
      expect(within(componentsRegion).getByText("24/30 câu đúng · 80%")).toBeInTheDocument();
      expect(within(componentsRegion).getByText("Toán học")).toBeInTheDocument();
      expect(within(componentsRegion).getByText("26/30 câu đúng · 86,67%")).toBeInTheDocument();
      expect(within(componentsRegion).getByText("Tư duy khoa học")).toBeInTheDocument();
      expect(within(componentsRegion).getByText("22/30 câu đúng · 73,33%")).toBeInTheDocument();
      expect(within(componentsRegion).queryByText(/\/1\.200|\/600|\/300|\/180|\/120|\/90/)).not.toBeInTheDocument();

      await userEvent.click(within(componentsRegion).getByRole("button", { name: /Xem riêng 2 tiểu mục/i }));
      expect(within(componentsRegion).getByText("Logic và phân tích số liệu")).toBeInTheDocument();
      expect(within(componentsRegion).getByText("Suy luận khoa học")).toBeInTheDocument();
      vactView.unmount();
    }

    // Verify no hardcoded 1200 scale fallback exists in production attempt-result source files
    const dir = path.dirname(new URL(import.meta.url).pathname.replace(/^\/([A-Za-z]:)/, "$1"));
    const resultViewSrc = readFileSync(path.join(dir, "result-view.tsx"), "utf8");
    const formatSrc = readFileSync(path.join(dir, "format.ts"), "utf8");
    expect(resultViewSrc).not.toMatch(/\b1200\b/);
    expect(formatSrc).not.toMatch(/\b1200\b/);
  });

  it("áp dụng pickName song ngữ cho examKind và subject ở locale en, fallback về name khi nameEn rỗng, hỗ trợ choiceOptions.ANH, fallback subject.name, và bỏ /0 khi max null", async () => {
    const enView = renderWithProviders(
      <AttemptResultView
        attemptId={ATTEMPT_UUID_EN}
        loadCurrentSession={async () => STUDENT_SESSION}
        loadResult={async () =>
          makeResult({
            subjectCode: "TOAN",
            choices: {
              "part-3": { optionCode: "ANH" },
            },
          })
        }
        loadExamKinds={async () => EXAM_KINDS}
        loadSubjects={async () => SUBJECTS}
      />,
      { locale: "en", messages: enMessages },
    );

    expect(await screen.findByText("English")).toBeInTheDocument();
    expect(await screen.findAllByText("HSA Assessment")).toHaveLength(2);
    expect(screen.getAllByText("Mathematics").length).toBeGreaterThanOrEqual(1);
    expect(screen.getByText("Hóa học")).toBeInTheDocument();
    enView.unmount();

    // Non-known optionCode in choices (VATLI) falls back to resolveCatalogLabel(optionCode, subjectMap, locale) -> "Physics" (R2-5)
    const vatliChoiceView = renderWithProviders(
      <AttemptResultView
        attemptId="11111111-1111-4111-8111-111111111015"
        loadCurrentSession={async () => STUDENT_SESSION}
        loadResult={async () =>
          makeResult({
            subjectCode: "TOAN",
            choices: {
              "part-4": { optionCode: "VATLI" },
            },
          })
        }
        loadExamKinds={async () => EXAM_KINDS}
        loadSubjects={async () => SUBJECTS}
      />,
      { locale: "en", messages: enMessages },
    );

    expect(await screen.findByText("Physics")).toBeInTheDocument();
    expect(screen.getByText("Hóa học")).toBeInTheDocument();
    vatliChoiceView.unmount();

    // Vietnamese ANH + subject.name fallback when catalog empty + nullable max omits /0 (R1-14, R1-16, R1-51)
    const fallbackView = renderWithProviders(
      <AttemptResultView
        attemptId="11111111-1111-4111-8111-111111111014"
        loadCurrentSession={async () => STUDENT_SESSION}
        loadResult={async () =>
          makeResult({
            displayScore: 88,
            rawScore: 88,
            rawMaxScore: undefined,
            scaleMax: undefined,
            choices: {
              "part-3": { optionCode: "ANH" },
            },
            subjects: [
              { code: "TOAN", name: "Toán học nâng cao", score: 40, maxScore: 50 },
            ],
          })
        }
        loadExamKinds={async () => EXAM_KINDS}
        loadSubjects={async () => []}
      />,
    );

    expect(await screen.findByText("Tiếng Anh")).toBeInTheDocument();
    expect(screen.getByText("88")).toBeInTheDocument();
    expect(screen.queryByText("/0")).not.toBeInTheDocument();
    const fallbackName = screen.getByText("Toán học nâng cao");
    expect(fallbackName).toHaveAttribute("data-exam-content");
    fallbackView.unmount();
  });

  it("loadCatalogOptions ném lỗi khi HTTP 500 thay vì nuốt lỗi thành [] (R1-12, R1-13)", async () => {
    vi.spyOn(globalThis, "fetch").mockResolvedValueOnce(
      new Response(JSON.stringify({ code: "INTERNAL_ERROR", message: "boom" }), {
        status: 500,
        headers: { "content-type": "application/json" },
      }),
    );
    await expect(loadCatalogOptions("EXAM")).rejects.toMatchObject({ status: 500 });
  });

  it("xử lý SESSION_QUERY_KEY stale khi offline (có/chưa có result cache, Thử lại gọi resultQuery), malformed UUID offline, và Thử lại offline sau lỗi session (R2-1, R2-2)", async () => {
    // Case (i): Stale STUDENT_SESSION + cached fresh result + offline -> renders LoadedResultContent with 1 <h1> and no offline card
    const cachedClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
    cachedClient.setQueryData(SESSION_QUERY_KEY, STUDENT_SESSION, {
      updatedAt: Date.now() - 120_000,
    });
    cachedClient.setQueryData(
      ATTEMPT_RESULT_QUERY_KEY(ATTEMPT_UUID_1, STUDENT_SESSION.id),
      makeResult(),
      { updatedAt: Date.now() },
    );

    try {
      onlineManager.setOnline(false);
      const cachedView = renderWithProviders(
        <AttemptResultView
          attemptId={ATTEMPT_UUID_1}
          loadCurrentSession={async () => STUDENT_SESSION}
          loadResult={async () => makeResult()}
          loadExamKinds={async () => EXAM_KINDS}
          loadSubjects={async () => SUBJECTS}
        />,
        { client: cachedClient },
      );

      expect(await screen.findByRole("heading", { level: 1, name: "Đề HSA tổng hợp số 1" })).toBeInTheDocument();
      expect(screen.getAllByRole("heading", { level: 1 })).toHaveLength(1);
      expect(screen.queryByText("Không có kết nối")).not.toBeInTheDocument();
      cachedView.unmount();
    } finally {
      onlineManager.setOnline(true);
    }

    // Case (ii): Stale STUDENT_SESSION + no cached result + offline -> 1 offline card & 1 <h1>, Retry targets resultQuery
    const coldResultClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
    coldResultClient.setQueryData(SESSION_QUERY_KEY, STUDENT_SESSION, {
      updatedAt: Date.now() - 120_000,
    });
    const loadResultSpy = vi.fn(async () => makeResult());

    try {
      onlineManager.setOnline(false);
      const coldResultView = renderWithProviders(
        <AttemptResultView
          attemptId={ATTEMPT_UUID_1}
          loadCurrentSession={async () => STUDENT_SESSION}
          loadResult={loadResultSpy}
          loadExamKinds={async () => EXAM_KINDS}
          loadSubjects={async () => SUBJECTS}
        />,
        { client: coldResultClient },
      );

      expect(await screen.findByRole("heading", { level: 1, name: "Không có kết nối" })).toBeInTheDocument();
      expect(screen.getAllByText("Không có kết nối")).toHaveLength(1);
      expect(screen.getAllByRole("heading", { level: 1 })).toHaveLength(1);
      expect(loadResultSpy).not.toHaveBeenCalled();

      const resultCacheQuery = coldResultClient
        .getQueryCache()
        .find({ queryKey: ATTEMPT_RESULT_QUERY_KEY(ATTEMPT_UUID_1, STUDENT_SESSION.id) })!;
      const sessionCacheQuery = coldResultClient.getQueryCache().find({ queryKey: SESSION_QUERY_KEY })!;
      const resultFetchSpy = vi.spyOn(resultCacheQuery, "fetch");
      const sessionFetchSpy = vi.spyOn(sessionCacheQuery, "fetch");

      act(() => {
        onlineManager.setOnline(true);
        fireEvent.click(screen.getByRole("button", { name: "Thử lại" }));
        expect(resultFetchSpy).toHaveBeenCalledTimes(1);
        expect(sessionFetchSpy).not.toHaveBeenCalled();
      });

      expect(await screen.findByRole("heading", { level: 1, name: "Đề HSA tổng hợp số 1" })).toBeInTheDocument();
      expect(loadResultSpy).toHaveBeenCalled();
      expect(screen.getAllByRole("heading", { level: 1 })).toHaveLength(1);
      expect(screen.queryByText("Không có kết nối")).not.toBeInTheDocument();
      coldResultView.unmount();
    } finally {
      onlineManager.setOnline(true);
    }

    // Offline with malformed attemptId still shows offline card cleanly when session has no data
    try {
      onlineManager.setOnline(false);
      const invalidOfflineView = renderWithProviders(
        <AttemptResultView
          attemptId="not-a-uuid"
          loadCurrentSession={async () => STUDENT_SESSION}
          loadResult={async () => makeResult()}
          loadExamKinds={async () => EXAM_KINDS}
          loadSubjects={async () => SUBJECTS}
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
      <AttemptResultView
        attemptId={ATTEMPT_UUID_1}
        loadCurrentSession={sessionFailThenOk}
        loadResult={async () => makeResult()}
        loadExamKinds={async () => EXAM_KINDS}
        loadSubjects={async () => SUBJECTS}
      />,
    );

    expect(await screen.findByRole("heading", { level: 1, name: "Không tải được kết quả lượt thi" })).toBeInTheDocument();

    try {
      onlineManager.setOnline(false);
      await userEvent.click(screen.getByRole("button", { name: "Thử lại" }));

      expect(await screen.findByRole("heading", { level: 1, name: "Không có kết nối" })).toBeInTheDocument();
      expect(screen.queryByText("Không tải được kết quả lượt thi")).not.toBeInTheDocument();
      expect(screen.getAllByRole("heading", { level: 1 })).toHaveLength(1);
      errRetryView.unmount();
    } finally {
      onlineManager.setOnline(true);
    }
  });
});
