import { screen, within } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import enMessages from "@/messages/en.json";
import { installContainerWidth, PHONE_CONTENT_WIDTH } from "@/test/container-width";
import { renderWithProviders } from "@/test/render";
import { STUDENT_SESSION } from "@/test/storefront-fixtures";
import type { AttemptResultResponse, CatalogOptionResponse } from "./api";
import { AttemptResultView } from "./result-view";

const ATTEMPT_UUID_VSAT = "33333333-3333-4333-8333-333333333301";

const EXAM_KINDS: CatalogOptionResponse[] = [
  { id: "ek-vsat", code: "VSAT", nameVi: "Đánh giá đầu vào V-SAT", nameEn: "V-SAT", displayOrder: 1, value: 1 },
];

const SUBJECTS: CatalogOptionResponse[] = [
  { id: "s-anh", code: "ANH", nameVi: "Tiếng Anh", nameEn: "English", displayOrder: 1, value: 1 },
];

const VIETNAMESE_DIACRITIC_RE =
  /[àáảãạăằắẳẵặâầấẩẫậèéẻẽẹêềếểễệìíỉĩịòóỏõọôồốổỗộơờớởỡợùúủũụưừứửữựỳýỷỹỵđÀÁẢÃẠĂẰẮẲẴẶÂẦẤẨẪẬÈÉẺẼẸÊỀẾỂỄỆÌÍỈĨỊÒÓỎÕỌÔỒỐỔỖỘƠỜỚỞỠỢÙÚỦŨỤƯỪỨỬỮỰỲÝỶỸỴĐ]/;

function makeVsatResult(overrides: Partial<AttemptResultResponse> = {}): AttemptResultResponse {
  return {
    attemptId: ATTEMPT_UUID_VSAT,
    attemptNo: 1,
    examId: "44444444-4444-4444-8444-444444444401",
    examName: "Đề V-SAT Tiếng Anh 25 câu",
    examVersionNo: 1,
    examKindCode: "VSAT",
    scope: "SUBJECT",
    subjectCode: "ANH",
    state: "SCORED",
    resultView: "FULL_SOLUTION",
    reviewAvailable: true,
    rawScore: 105,
    rawMaxScore: 150,
    displayScore: 105,
    startedAt: "2026-11-22T01:00:00Z",
    finishedAt: "2026-11-22T01:45:00Z",
    gradedAt: "2026-11-22T01:45:02Z",
    endReason: "SUBMITTED",
    statistics: {
      correctCount: 16,
      partialCount: 3,
      wrongCount: 4,
      blankCount: 2,
      pendingReviewCount: 0,
      totalCount: 25,
    },
    parts: [
      {
        partId: "p-1",
        partCode: "P1",
        partName: "Tiếng Anh V-SAT",
        ord: 1,
        score: 105,
        maxScore: 150,
        pendingReview: false,
        skipped: false,
        stats: {
          correctCount: 16,
          partialCount: 3,
          wrongCount: 4,
          blankCount: 2,
          pendingReviewCount: 0,
          totalCount: 25,
        },
      },
    ],
    segments: [
      {
        code: "I",
        name: "Phần I",
        partCode: "P1",
        fromPosition: 1,
        toPosition: 9,
        pendingReview: false,
        score: 40,
        maxPoints: 54,
        stats: { correctCount: 6, partialCount: 2, wrongCount: 1, blankCount: 0, pendingReviewCount: 0, totalCount: 9 },
      },
      {
        code: "II",
        name: "Phần II",
        partCode: "P1",
        fromPosition: 10,
        toPosition: 15,
        pendingReview: false,
        score: 24,
        maxPoints: 36,
        stats: { correctCount: 4, partialCount: 0, wrongCount: 1, blankCount: 1, pendingReviewCount: 0, totalCount: 6 },
      },
      {
        code: "III",
        name: "Phần III",
        partCode: "P1",
        fromPosition: 16,
        toPosition: 20,
        pendingReview: false,
        score: 21,
        maxPoints: 30,
        stats: { correctCount: 3, partialCount: 1, wrongCount: 1, blankCount: 0, pendingReviewCount: 0, totalCount: 5 },
      },
      {
        code: "IV",
        name: "Phần IV",
        partCode: "P1",
        fromPosition: 21,
        toPosition: 25,
        pendingReview: false,
        score: 20,
        maxPoints: 30,
        stats: { correctCount: 3, partialCount: 0, wrongCount: 1, blankCount: 1, pendingReviewCount: 0, totalCount: 5 },
      },
    ],
    ...overrides,
  };
}

describe("kết quả phân đoạn và trạng thái Đúng một phần (AC-12, BS §17.6)", () => {
  afterEach(() => {
    vi.restoreAllMocks();
  });

  it("hiển thị bảng điểm theo phân đoạn trên desktop gồm đủ 4 phân đoạn, dải câu, điểm/maxPoints, data-exam-content và cột Đúng một phần", async () => {
    renderWithProviders(
      <AttemptResultView
        attemptId={ATTEMPT_UUID_VSAT}
        loadCurrentSession={async () => STUDENT_SESSION}
        loadResult={async () => makeVsatResult()}
        loadExamKinds={async () => EXAM_KINDS}
        loadSubjects={async () => SUBJECTS}
      />,
    );

    const segSection = await screen.findByRole("region", { name: "Điểm theo phân đoạn" });
    const segTable = within(segSection).getByRole("table", { name: "Điểm theo phân đoạn" });

    expect(within(segTable).getByRole("columnheader", { name: "Đúng một phần" })).toBeInTheDocument();

    const rows = within(segTable).getAllByRole("row");
    expect(rows).toHaveLength(5);

    const p1Text = within(rows[1]!).getByText("Phần I");
    expect(p1Text).toHaveAttribute("data-exam-content");
    expect(rows[1]).toHaveTextContent("Câu 1–9");
    expect(rows[1]).toHaveTextContent("40/54");
    expect(rows[1]).toHaveTextContent("2");

    expect(within(rows[2]!).getByText("Phần II")).toHaveAttribute("data-exam-content");
    expect(rows[2]).toHaveTextContent("Câu 10–15");
    expect(rows[2]).toHaveTextContent("24/36");

    expect(within(rows[3]!).getByText("Phần III")).toHaveAttribute("data-exam-content");
    expect(rows[3]).toHaveTextContent("Câu 16–20");
    expect(rows[3]).toHaveTextContent("21/30");

    expect(within(rows[4]!).getByText("Phần IV")).toHaveAttribute("data-exam-content");
    expect(rows[4]).toHaveTextContent("Câu 21–25");
    expect(rows[4]).toHaveTextContent("20/30");
  });

  it("hiển thị khối phân đoạn dạng thẻ CardList ở khung 390px gồm nhãn Đúng một phần và dải câu", async () => {
    installContainerWidth(PHONE_CONTENT_WIDTH);

    renderWithProviders(
      <AttemptResultView
        attemptId={ATTEMPT_UUID_VSAT}
        loadCurrentSession={async () => STUDENT_SESSION}
        loadResult={async () => makeVsatResult()}
        loadExamKinds={async () => EXAM_KINDS}
        loadSubjects={async () => SUBJECTS}
      />,
    );

    const segSection = await screen.findByRole("region", { name: "Điểm theo phân đoạn" });
    expect(within(segSection).queryByRole("table")).not.toBeInTheDocument();

    const list = within(segSection).getByRole("list", { name: "Điểm theo phân đoạn" });
    expect(within(list).getByText("Phần I")).toHaveAttribute("data-exam-content");
    expect(within(list).getByText("Câu 1–9")).toBeInTheDocument();
    expect(within(list).getByText("Câu 10–15")).toBeInTheDocument();
    expect(within(list).getByText("Câu 16–20")).toBeInTheDocument();
    expect(within(list).getByText("Câu 21–25")).toBeInTheDocument();
    expect(within(list).getByText("Điểm phân đoạn: 40/54")).toBeInTheDocument();
    expect(within(list).getAllByText(/Đúng một phần:/).length).toBeGreaterThanOrEqual(2);
  });

  it("hỗ trợ locale en sạch sẽ không lọt chữ tiếng Việt ngoài data-exam-content", async () => {
    const { container } = renderWithProviders(
      <AttemptResultView
        attemptId={ATTEMPT_UUID_VSAT}
        loadCurrentSession={async () => STUDENT_SESSION}
        loadResult={async () => makeVsatResult()}
        loadExamKinds={async () => EXAM_KINDS}
        loadSubjects={async () => SUBJECTS}
      />,
      { locale: "en", messages: enMessages },
    );

    const segSection = await screen.findByRole("region", { name: "Segment breakdown" });
    expect(within(segSection).getByRole("columnheader", { name: "Partial" })).toBeInTheDocument();
    expect(within(segSection).getByText("Questions 1–9")).toBeInTheDocument();
    expect(within(segSection).getByText("Phần I")).toHaveAttribute("data-exam-content");

    const clone = container.cloneNode(true) as HTMLElement;
    for (const examNode of clone.querySelectorAll("[data-exam-content]")) {
      examNode.remove();
    }
    expect(VIETNAMESE_DIACRITIC_RE.test(clone.textContent ?? "")).toBe(false);
  });

  it("ẩn hoàn toàn khối phân đoạn khi segments rỗng hoặc không có trong payload", async () => {
    const omittedView = renderWithProviders(
      <AttemptResultView
        attemptId={ATTEMPT_UUID_VSAT}
        loadCurrentSession={async () => STUDENT_SESSION}
        loadResult={async () => makeVsatResult({ segments: undefined })}
        loadExamKinds={async () => EXAM_KINDS}
        loadSubjects={async () => SUBJECTS}
      />,
    );

    await screen.findByRole("heading", { level: 1, name: "Đề V-SAT Tiếng Anh 25 câu" });
    expect(screen.queryByRole("region", { name: "Điểm theo phân đoạn" })).not.toBeInTheDocument();
    omittedView.unmount();

    renderWithProviders(
      <AttemptResultView
        attemptId="33333333-3333-4333-8333-333333333302"
        loadCurrentSession={async () => STUDENT_SESSION}
        loadResult={async () => makeVsatResult({ segments: [] })}
        loadExamKinds={async () => EXAM_KINDS}
        loadSubjects={async () => SUBJECTS}
      />,
    );

    await screen.findByRole("heading", { level: 1, name: "Đề V-SAT Tiếng Anh 25 câu" });
    expect(screen.queryByRole("region", { name: "Điểm theo phân đoạn" })).not.toBeInTheDocument();
  });

  it("hiển thị phân đoạn đang chờ chấm (pendingReview) với chip Chấm tay, cột Chờ chấm và pendingDescription trên cả desktop và 390px", async () => {
    const pendingResult = makeVsatResult({
      state: "PENDING_REVIEW",
      rawScore: undefined,
      displayScore: undefined,
      segments: [
        {
          code: "I",
          name: "Phần Đọc hiểu",
          partCode: "P1",
          fromPosition: 1,
          toPosition: 4,
          pendingReview: false,
          score: 30,
          maxPoints: 40,
          stats: { correctCount: 3, partialCount: 0, wrongCount: 1, blankCount: 0, pendingReviewCount: 0, totalCount: 4 },
        },
        {
          code: "II",
          name: "Phần Viết",
          partCode: "P1",
          fromPosition: 5,
          toPosition: 6,
          pendingReview: true,
          score: undefined,
          maxPoints: 60,
          stats: { correctCount: 0, partialCount: 0, wrongCount: 0, blankCount: 0, pendingReviewCount: 2, totalCount: 2 },
        },
      ],
    });

    const desktopView = renderWithProviders(
      <AttemptResultView
        attemptId={ATTEMPT_UUID_VSAT}
        loadCurrentSession={async () => STUDENT_SESSION}
        loadResult={async () => pendingResult}
        loadExamKinds={async () => EXAM_KINDS}
        loadSubjects={async () => SUBJECTS}
      />,
    );

    const segSection = await screen.findByRole("region", { name: "Điểm theo phân đoạn" });
    expect(
      within(segSection).getByText("Điểm của phân đoạn đã chấm xong là điểm riêng của phân đoạn đó, chưa phải điểm tổng."),
    ).toBeInTheDocument();

    const segTable = within(segSection).getByRole("table", { name: "Điểm theo phân đoạn" });
    expect(within(segTable).getByRole("columnheader", { name: "Chờ chấm" })).toBeInTheDocument();
    const rows = within(segTable).getAllByRole("row");
    expect(rows[1]).toHaveTextContent("30/40");
    expect(rows[2]).toHaveTextContent("Phần Viết");
    expect(rows[2]).toHaveTextContent("Chấm tay");
    expect(rows[2]).toHaveTextContent("Chờ chấm");
    expect(rows[2]).toHaveTextContent("2");
    desktopView.unmount();

    installContainerWidth(PHONE_CONTENT_WIDTH);
    renderWithProviders(
      <AttemptResultView
        attemptId={ATTEMPT_UUID_VSAT}
        loadCurrentSession={async () => STUDENT_SESSION}
        loadResult={async () => pendingResult}
        loadExamKinds={async () => EXAM_KINDS}
        loadSubjects={async () => SUBJECTS}
      />,
    );

    const phoneSection = await screen.findByRole("region", { name: "Điểm theo phân đoạn" });
    expect(within(phoneSection).queryByRole("table")).not.toBeInTheDocument();
    const phoneList = within(phoneSection).getByRole("list", { name: "Điểm theo phân đoạn" });
    expect(within(phoneList).getByText("Phần Viết")).toHaveAttribute("data-exam-content");
    expect(within(phoneList).getByText("Chấm tay")).toBeInTheDocument();
    expect(within(phoneList).getByText("Chờ chấm")).toBeInTheDocument();
    expect(within(phoneList).getByText(/Chờ chấm:/)).toHaveTextContent("Chờ chấm: 2");
  });
});
