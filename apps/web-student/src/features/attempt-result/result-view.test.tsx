import { readFileSync } from "node:fs";
import path from "node:path";
import { act, fireEvent, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, describe, expect, it, vi } from "vitest";
import { ApiStatusError } from "@/lib/api/client";
import enMessages from "@/messages/en.json";
import { installContainerWidth, PHONE_CONTENT_WIDTH } from "@/test/container-width";
import { renderWithProviders } from "@/test/render";
import { ADMIN_SESSION, STUDENT_SESSION } from "@/test/storefront-fixtures";
import {
  GRADING_POLL_INTERVAL_MS,
  GRADING_POLL_TIMEOUT_MS,
  type AttemptResultResponse,
  type CatalogOptionResponse,
} from "./api";
import { AttemptResultView } from "./result-view";

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
    attemptId: "att-1",
    attemptNo: 2,
    examId: "exam-1",
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
    vi.useRealTimers();
    vi.restoreAllMocks();
  });

  it("chặn admin và xử lý các lỗi 404, 409 ATTEMPT_NOT_FINISHED, và lỗi mạng + Thử lại (AC-17)", async () => {
    const neverLoaded = vi.fn<() => Promise<AttemptResultResponse>>();
    const adminView = renderWithProviders(
      <AttemptResultView
        attemptId="att-1"
        loadCurrentSession={async () => ADMIN_SESSION}
        loadResult={neverLoaded}
        loadExamKinds={async () => EXAM_KINDS}
        loadSubjects={async () => SUBJECTS}
      />,
    );
    expect(await screen.findByRole("heading", { level: 1, name: "Tài khoản quản trị" })).toBeInTheDocument();
    expect(neverLoaded).not.toHaveBeenCalled();
    adminView.unmount();

    const notFoundView = renderWithProviders(
      <AttemptResultView
        attemptId="att-404"
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
        attemptId="att-open"
        loadCurrentSession={async () => STUDENT_SESSION}
        loadResult={async () => {
          throw new ApiStatusError(409, "Open", { code: "ATTEMPT_NOT_FINISHED" });
        }}
        loadExamKinds={async () => EXAM_KINDS}
        loadSubjects={async () => SUBJECTS}
      />,
    );
    expect(await screen.findByRole("heading", { level: 1, name: "Lượt thi chưa kết thúc" })).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Tiếp tục làm bài" })).toHaveAttribute("href", "/attempts/att-open");
    notFinishedView.unmount();

    const retryLoader = vi
      .fn<(id: string) => Promise<AttemptResultResponse>>()
      .mockRejectedValueOnce(new Error("network"))
      .mockResolvedValueOnce(makeResult());
    const errView = renderWithProviders(
      <AttemptResultView
        attemptId="att-1"
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
        attemptId="att-1"
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

    // 4 buckets on SCORED (pending bucket hidden when 0) + denominator
    expect(screen.getByText("Trên 120 câu phải làm.")).toBeInTheDocument();
    expect(within(screen.getByRole("region", { name: "Tổng điểm" })).queryByText("Chờ chấm")).not.toBeInTheDocument();
    // Metadata <dl>: choices + Asia/Ho_Chi_Minh (+07) timestamp + duration + endReason
    expect(await screen.findByText("Khoa học: Vật lí, Hóa học")).toBeInTheDocument();
    expect(screen.getByText("08:00, 20/11/2026")).toBeInTheDocument();
    expect(screen.getByText("09:15, 20/11/2026")).toBeInTheDocument();
    expect(screen.getByText("1 giờ 15 phút")).toBeInTheDocument();
    expect(screen.getByText("Quá hạn chọn phần 3")).toBeInTheDocument();

    // Review CTA visible for FULL_SOLUTION
    expect(screen.getByRole("link", { name: "Xem lại bài" })).toHaveAttribute("href", "/attempts/att-1/review");

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
        attemptId="att-zero"
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
        attemptId="att-1"
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

  it("poll GRADING mỗi 3s rồi tự dừng khi SCORED, và dừng kèm Tải lại sau 2 phút timeout (AC-6)", async () => {
    vi.useFakeTimers();

    const gradingThenScored = vi
      .fn<(id: string) => Promise<AttemptResultResponse>>()
      .mockResolvedValueOnce(makeResult({ state: "GRADING", displayScore: undefined, rawScore: undefined }))
      .mockResolvedValueOnce(makeResult({ state: "GRADING", displayScore: undefined, rawScore: undefined }))
      .mockResolvedValueOnce(makeResult({ state: "SCORED", displayScore: 130, rawMaxScore: 150 }));

    const pollingView = renderWithProviders(
      <AttemptResultView
        attemptId="att-poll"
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

    // Timeout flow at 120_000ms
    const alwaysGrading = vi
      .fn<(id: string) => Promise<AttemptResultResponse>>()
      .mockResolvedValue(makeResult({ state: "GRADING", displayScore: undefined, rawScore: undefined }));
    const timeoutView = renderWithProviders(
      <AttemptResultView
        attemptId="att-timeout"
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
        attemptId="att-gerr"
        loadCurrentSession={async () => STUDENT_SESSION}
        loadResult={async () =>
          makeResult({ state: "GRADING_ERROR", displayScore: undefined, rawScore: undefined, reviewAvailable: false })
        }
        loadExamKinds={async () => EXAM_KINDS}
        loadSubjects={async () => SUBJECTS}
      />,
    );
    expect(await screen.findByText("Bài làm đã được ghi nhận, kết quả đang được xử lý")).toBeInTheDocument();
    expect(screen.queryByRole("link", { name: /Xem lại/ })).not.toBeInTheDocument();
    expect(screen.queryByText(/\/150/)).not.toBeInTheDocument();
    gradingErrView.unmount();

    // PENDING_REVIEW with F16 part/subject scores
    const pendingView = renderWithProviders(
      <AttemptResultView
        attemptId="att-pending"
        loadCurrentSession={async () => STUDENT_SESSION}
        loadResult={async () =>
          makeResult({
            state: "PENDING_REVIEW",
            displayScore: undefined,
            rawScore: undefined,
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
    expect(screen.getByRole("link", { name: "Xem lại câu đã chấm tự động" })).toHaveAttribute(
      "href",
      "/attempts/att-pending/review",
    );
    expect(screen.getAllByText("Chấm tay").length).toBeGreaterThanOrEqual(2);
    // PENDING_REVIEW keeps the pending stat bucket visible alongside the pending score placeholder
    expect(within(screen.getByRole("region", { name: "Tổng điểm" })).getAllByText("Chờ chấm")).toHaveLength(2);
    pendingView.unmount();

    // REGRADING keeps previous score visible + FAULT + compensated banners
    const regradingView = renderWithProviders(
      <AttemptResultView
        attemptId="att-regrade"
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
        attemptId="att-fault"
        loadCurrentSession={async () => STUDENT_SESSION}
        loadResult={async () =>
          makeResult({
            compensated: false,
            faultFlag: "SYSTEM_FAULT",
            endReason: "FAULT_CLOSED",
          })
        }
        loadExamKinds={async () => EXAM_KINDS}
        loadSubjects={async () => SUBJECTS}
      />,
    );
    expect(await screen.findByText("Lượt kết thúc do lỗi hệ thống")).toBeInTheDocument();
    faultOnlyView.unmount();
  });

  it("render V-ACT điểm luyện tập với mọi thang điểm động (1200, 600, 300, 180, 120, fallback rawMaxScore), 4 thành phần không lộ điểm và 2 tiểu mục Tư duy khoa học (AC-2, AC-22)", async () => {
    const scales = [
      { scaleMax: 1200, rawMaxScore: 120, displayScore: 980, expectedDenom: "/1.200" },
      { scaleMax: 600, rawMaxScore: 60, displayScore: 490, expectedDenom: "/600" },
      { scaleMax: 300, rawMaxScore: 30, displayScore: 245, expectedDenom: "/300" },
      { scaleMax: 180, rawMaxScore: 18, displayScore: 150, expectedDenom: "/180" },
      { scaleMax: 120, rawMaxScore: 12, displayScore: 95, expectedDenom: "/120" },
      { scaleMax: undefined, rawMaxScore: 90, displayScore: 72, expectedDenom: "/90" },
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
          attemptId={`att-vact-${s.expectedDenom}`}
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
      expect(within(componentsRegion).getByText("Tiếng Anh")).toBeInTheDocument();
      expect(within(componentsRegion).getByText("Toán học")).toBeInTheDocument();
      expect(within(componentsRegion).getByText("Tư duy khoa học")).toBeInTheDocument();
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

  it("áp dụng pickName song ngữ cho examKind và subject ở locale en, fallback về name khi nameEn rỗng", async () => {
    renderWithProviders(
      <AttemptResultView
        attemptId="att-en"
        loadCurrentSession={async () => STUDENT_SESSION}
        loadResult={async () => makeResult({ subjectCode: "TOAN" })}
        loadExamKinds={async () => EXAM_KINDS}
        loadSubjects={async () => SUBJECTS}
      />,
      { locale: "en", messages: enMessages },
    );

    expect(await screen.findByText("Science: Physics, Hóa học")).toBeInTheDocument();
    expect(await screen.findAllByText("HSA Assessment")).toHaveLength(2);
    expect(screen.getAllByText("Mathematics").length).toBeGreaterThanOrEqual(1);
  });
});
