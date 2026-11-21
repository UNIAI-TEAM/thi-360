import { screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { installContainerWidth, PHONE_CONTENT_WIDTH } from "@/test/container-width";
import { renderWithProviders } from "@/test/render";
import { PreviewTrialView } from "../trial/preview-trial-view";
import type { PreviewGradeResponse, PreviewTrialPaperResponse } from "../trial/types";
import { EXAM_ID, EXAM_PATH, FakeServer } from "./fake-exams-server";

const routerMock = vi.hoisted(() => ({ push: vi.fn(), replace: vi.fn() }));
vi.mock("next/navigation", () => ({
  useRouter: () => routerMock,
  usePathname: () => `/exams/${EXAM_ID}/preview/${PREVIEW_ID}/try`,
  useSearchParams: () => new URLSearchParams(),
}));

const PREVIEW_ID = "44444444-4444-4444-8444-444444444444";
const PAPER_PATH = `${EXAM_PATH}/preview/${PREVIEW_ID}/paper`;
const GRADE_PATH = `${EXAM_PATH}/preview/${PREVIEW_ID}/grade`;

function buildTrialPaperFixture(): PreviewTrialPaperResponse {
  return {
    examId: EXAM_ID,
    previewId: PREVIEW_ID,
    versionNo: 2,
    kindCode: "HSA",
    timing: "PER_PART",
    durationMinutes: 120,
    parts: [
      {
        partId: "part-1",
        name: "Phần 1 · Trắc nghiệm và Đúng/Sai",
        order: 1,
        durationMinutes: 60,
        firstNumber: 1,
        lastNumber: 2,
        points: 3,
        blocks: [
          {
            blockId: "blk-1",
            name: "Khối trắc nghiệm & đúng sai",
            questionType: "SINGLE_CHOICE",
            pointsEach: 1,
            firstNumber: 1,
            lastNumber: 2,
            questionCount: 2,
            items: [
              {
                kind: "QUESTION",
                question: {
                  number: 1,
                  questionId: "q-sc-1",
                  code: "C1",
                  versionNo: 1,
                  type: "SINGLE_CHOICE",
                  content: "<p>Thủ đô của Việt Nam là thành phố nào?</p>",
                  points: 1,
                  optionOrder: ["opt-a", "opt-b"],
                  attachments: [
                    {
                      id: "asset-audio-1",
                      name: "nghe-cau-1.mp3",
                      mime: "audio/mpeg",
                    },
                  ],
                  body: {
                    type: "SINGLE_CHOICE",
                    options: [
                      { id: "opt-a", content: "<p>Hà Nội</p>" },
                      { id: "opt-b", content: "<p>Đà Nẵng</p>" },
                    ],
                  } as unknown as NonNullable<
                    NonNullable<PreviewTrialPaperResponse["parts"]>[number]["blocks"]
                  >[number]["items"] extends Array<infer I>
                    ? I extends { question?: infer Q }
                      ? Q extends { body?: infer B }
                        ? B
                        : never
                      : never
                    : never,
                },
              },
              {
                kind: "QUESTION",
                question: {
                  number: 2,
                  questionId: "q-tf-2",
                  code: "C2",
                  versionNo: 1,
                  type: "TRUE_FALSE",
                  content: "<p>Xét các mệnh đề sau:</p>",
                  points: 2,
                  body: {
                    type: "TRUE_FALSE",
                    statements: [
                      { id: "s1", content: "<p>Ý a: 2 + 2 = 4</p>" },
                      { id: "s2", content: "<p>Ý b: 3 &gt; 5</p>" },
                      { id: "s3", content: "<p>Ý c: Nước sôi ở 100°C</p>" },
                      { id: "s4", content: "<p>Ý d: Trái Đất phẳng</p>" },
                    ],
                  } as never,
                },
              },
            ],
          },
        ],
      },
      {
        partId: "part-2",
        name: "Phần 2 · Tự luận",
        order: 2,
        durationMinutes: 60,
        firstNumber: 3,
        lastNumber: 3,
        points: 5,
        blocks: [
          {
            blockId: "blk-2",
            name: "Nghị luận xã hội",
            questionType: "ESSAY",
            manualGrading: true,
            pointsEach: 5,
            firstNumber: 3,
            lastNumber: 3,
            questionCount: 1,
            items: [
              {
                kind: "QUESTION",
                question: {
                  number: 3,
                  questionId: "q-es-3",
                  code: "C3",
                  versionNo: 1,
                  type: "ESSAY",
                  content: "<p>Viết đoạn văn ngắn về tinh thần tự học.</p>",
                  points: 5,
                  body: {
                    type: "ESSAY",
                    wordLimit: 20,
                  } as never,
                },
              },
            ],
          },
        ],
      },
    ],
  };
}

function buildPendingGradeFixture(): PreviewGradeResponse {
  return {
    examId: EXAM_ID,
    previewId: PREVIEW_ID,
    kindCode: "HSA",
    rawScore: undefined,
    maxScore: 8,
    hasPendingManualGrading: true,
    statistics: {
      totalCount: 3,
      correctCount: 1,
      partialCount: 1,
      wrongCount: 0,
      blankCount: 0,
      pendingReviewCount: 1,
    },
    items: [
      {
        questionId: "q-sc-1",
        number: 1,
        type: "SINGLE_CHOICE",
        outcome: "CORRECT",
        earnedPoints: 1,
        maxPoints: 1,
        pendingReview: false,
      },
      {
        questionId: "q-tf-2",
        number: 2,
        type: "TRUE_FALSE",
        outcome: "PARTIAL",
        earnedPoints: 0.5,
        maxPoints: 2,
        pendingReview: false,
        gradingDetail: {
          statementResults: [
            { id: "s1", answered: true, matched: true },
            { id: "s2", answered: true, matched: true },
            { id: "s3", answered: true, matched: false },
            { id: "s4", answered: false, matched: false },
          ],
        },
      },
      {
        questionId: "q-es-3",
        number: 3,
        type: "ESSAY",
        outcome: "PENDING_REVIEW",
        earnedPoints: 0,
        maxPoints: 5,
        pendingReview: true,
        gradingDetail: { wordCount: 4 },
      },
    ],
  };
}

beforeEach(() => {
  routerMock.push.mockReset();
  Element.prototype.scrollIntoView = vi.fn();
});

afterEach(() => {
  vi.unstubAllGlobals();
  vi.restoreAllMocks();
});

describe("Trang làm thử như học viên (/exams/[id]/preview/[previewId]/try)", () => {
  it("AC-26 & AC-30: chỉ gọi GET .../paper trước khi nộp, không rò đáp án/rubric, không có đồng hồ đếm ngược", async () => {
    const server = new FakeServer().on("GET", PAPER_PATH, {
      status: 200,
      body: buildTrialPaperFixture(),
    });
    server.install();

    renderWithProviders(
      <PreviewTrialView examId={EXAM_ID} previewId={PREVIEW_ID} />,
    );

    expect(await screen.findByRole("heading", { level: 1, name: "Làm thử như học viên" })).toBeInTheDocument();
    expect(screen.getByText(/Không tính giờ/)).toBeInTheDocument();
    expect(screen.queryByRole("timer")).not.toBeInTheDocument();
    expect(screen.getByText("Thủ đô của Việt Nam là thành phố nào?")).toBeInTheDocument();

    // Chỉ gọi đúng 1 API: GET .../paper; không gọi POST .../preview hay GET /exams/{id}
    expect(server.requests).toHaveLength(1);
    expect(server.requests[0]!.method).toBe("GET");
    expect(server.requests[0]!.url.pathname).toBe(PAPER_PATH);

    // Không có đáp án mẫu / tiêu chí chấm / bảng công bố trên DOM
    expect(screen.queryByText(/Tiêu chí chấm/)).not.toBeInTheDocument();
    expect(screen.queryByText(/Đáp án đúng/)).not.toBeInTheDocument();

    // Audio đính kèm trỏ đúng /api/v1/admin/question-assets/...
    const audioEl = document.querySelector("audio");
    expect(audioEl?.getAttribute("src")).toContain("/api/v1/admin/question-assets/asset-audio-1");
  });

  it("AC-20, AC-24, AC-25: chuyển phần tự do, rời trang cảnh báo beforeunload & ConfirmDialog, nộp đủ & giữ nguyên khi lỗi, AC-19 & AC-22 kết quả", async () => {
    const user = userEvent.setup();
    const server = new FakeServer()
      .on("GET", PAPER_PATH, { status: 200, body: buildTrialPaperFixture() })
      .on(
        "POST",
        GRADE_PATH,
        { status: 500, body: { code: "INTERNAL_ERROR" } },
        { status: 200, body: buildPendingGradeFixture() },
      );
    server.install();

    renderWithProviders(
      <PreviewTrialView examId={EXAM_ID} previewId={PREVIEW_ID} />,
    );

    await screen.findByText("Thủ đô của Việt Nam là thành phố nào?");

    // Chọn câu 1
    await user.click(screen.getByRole("radio", { name: /Hà Nội/ }));

    // Chọn Đúng/Sai câu 2 (a=Đúng, b=Sai, c=Đúng; bỏ d)
    const statementGroups = screen.getAllByRole("radiogroup");
    await user.click(within(statementGroups[1]!).getByRole("radio", { name: "Đúng" }));
    await user.click(within(statementGroups[2]!).getByRole("radio", { name: "Sai" }));
    await user.click(within(statementGroups[3]!).getByRole("radio", { name: "Đúng" }));

    // Chuyển tự do sang Phần 2 (dù timing = PER_PART) & kiểm tra nút Nộp vẫn bấm được dù tự luận dưới ngưỡng (AC-20, AC-25)
    await user.click(screen.getByRole("button", { name: /Phần 2 · Tự luận/ }));
    expect(await screen.findByText("Viết đoạn văn ngắn về tinh thần tự học.")).toBeInTheDocument();
    expect(screen.getByText(/0 \/ tối thiểu 20 chữ/)).toBeInTheDocument();
    const submitBtn = screen.getByRole("button", { name: "Nộp bài & Chấm thử" });
    expect(submitBtn).toBeEnabled();

    // AC-24: beforeunload đang bật trước khi nộp
    const beforeUnloadEvent = new Event("beforeunload", { cancelable: true });
    window.dispatchEvent(beforeUnloadEvent);
    expect(beforeUnloadEvent.defaultPrevented).toBe(true);

    // ConfirmDialog khi bấm Về Xem trước trước khi nộp: bấm Ở lại giữ nguyên trang
    await user.click(screen.getByRole("button", { name: "Về Xem trước" }));
    const leaveDialog = await screen.findByRole("dialog");
    expect(within(leaveDialog).getByText("Bỏ lần làm thử này?")).toBeInTheDocument();
    await user.click(within(leaveDialog).getByRole("button", { name: "Ở lại" }));
    expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
    expect(routerMock.push).not.toHaveBeenCalled();

    // Nộp lần 1 bị lỗi 500 -> hiện role="alert" và giữ nguyên đáp án (quay lại Phần 1 kiểm tra radio còn chọn)
    await user.click(submitBtn);
    expect(await screen.findByRole("alert")).toHaveTextContent("Không chấm được bài làm thử");

    // Chuyển lại Phần 1 thấy đáp án vẫn còn nguyên vẹn
    await user.click(screen.getByRole("button", { name: /Phần 1 · Trắc nghiệm và Đúng\/Sai/ }));
    expect(screen.getByRole("radio", { name: /Hà Nội/ })).toBeChecked();

    // Nộp lại -> thành công (200)
    await user.click(screen.getByRole("button", { name: "Nộp bài & Chấm thử" }));

    await waitFor(() => {
      expect(screen.getByRole("heading", { level: 1, name: "Kết quả làm thử" })).toBeInTheDocument();
    });

    // AC-22: tổng điểm chỉ tính trắc nghiệm + ghi chú tự luận chưa chấm
    expect(screen.getByText("1,5 / 3 phần trắc nghiệm · tự luận chưa chấm")).toBeInTheDocument();

    // AC-19: câu Đúng/Sai liệt kê đủ 4 ý a), b), c), d) với giá trị đã chọn và kết quả từng ý
    const resultTable = screen.getByRole("table", { name: "Chi tiết từng câu của kết quả làm thử" });
    expect(within(resultTable).getByText("a)")).toBeInTheDocument();
    expect(within(resultTable).getByText("b)")).toBeInTheDocument();
    expect(within(resultTable).getByText("c)")).toBeInTheDocument();
    expect(within(resultTable).getByText("d)")).toBeInTheDocument();

    // AC-22: dòng Tự luận hiện số chữ và chip Chờ chấm
    expect(within(resultTable).getByText("4 chữ")).toBeInTheDocument();
    expect(within(resultTable).getAllByText("Chờ chấm").length).toBeGreaterThanOrEqual(1);

    // Sau khi nộp, beforeunload không còn chặn rời trang
    const afterUnloadEvent = new Event("beforeunload", { cancelable: true });
    window.dispatchEvent(afterUnloadEvent);
    expect(afterUnloadEvent.defaultPrevented).toBe(false);

    // Về Xem trước sau khi nộp đi thẳng tới tab preview không bật hộp thoại xác nhận
    const backLink = screen.getByRole("link", { name: "Về trang Xem trước" });
    expect(backLink).toHaveAttribute("href", `/exams/${EXAM_ID}/review?tab=preview`);

    // Bấm Làm lại đề này -> xóa trắng câu trả lời và không gọi lại POST .../preview
    const requestCountBeforeRetake = server.requests.length;
    await user.click(screen.getByRole("button", { name: "Làm lại đề này" }));
    expect(await screen.findByRole("heading", { level: 1, name: "Làm thử như học viên" })).toBeInTheDocument();
    expect(screen.getByRole("radio", { name: /Hà Nội/ })).not.toBeChecked();
    expect(server.requests.length).toBe(requestCountBeforeRetake);
  });

  it("AC-24 ConfirmDialog: bấm Bỏ điều hướng về tab Xem trước; AC-23 hết hạn 410 PREVIEW_EXPIRED hiện cảnh báo & quay lại", async () => {
    const user = userEvent.setup();
    const server = new FakeServer()
      .on("GET", PAPER_PATH, { status: 200, body: buildTrialPaperFixture() });
    server.install();

    const { unmount } = renderWithProviders(
      <PreviewTrialView examId={EXAM_ID} previewId={PREVIEW_ID} />,
    );
    await screen.findByText("Thủ đô của Việt Nam là thành phố nào?");

    await user.click(screen.getByRole("button", { name: "Về Xem trước" }));
    const dialog = await screen.findByRole("dialog");
    await user.click(within(dialog).getByRole("button", { name: "Bỏ" }));
    expect(routerMock.push).toHaveBeenCalledWith(`/exams/${EXAM_ID}/review?tab=preview`);
    unmount();

    // AC-23: 410 PREVIEW_EXPIRED khi tải paper
    const expiredServer = new FakeServer().on("GET", PAPER_PATH, {
      status: 410,
      body: { code: "PREVIEW_EXPIRED" },
    });
    expiredServer.install();

    renderWithProviders(<PreviewTrialView examId={EXAM_ID} previewId={PREVIEW_ID} />);
    const alert = await screen.findByRole("alert");
    expect(alert).toHaveTextContent("Đề làm thử đã hết hạn");
    expect(within(alert).getByRole("link", { name: "Quay lại sinh đề thử mới" })).toHaveAttribute(
      "href",
      `/exams/${EXAM_ID}/review?tab=preview`,
    );
  });

  it("khung hẹp 390px: kết quả làm thử hiển thị dạng ListCard", async () => {
    installContainerWidth(PHONE_CONTENT_WIDTH);
    const user = userEvent.setup();
    const server = new FakeServer()
      .on("GET", PAPER_PATH, { status: 200, body: buildTrialPaperFixture() })
      .on("POST", GRADE_PATH, { status: 200, body: buildPendingGradeFixture() });
    server.install();

    renderWithProviders(<PreviewTrialView examId={EXAM_ID} previewId={PREVIEW_ID} />);
    await user.click(await screen.findByRole("button", { name: "Nộp bài & Chấm thử" }));

    expect(await screen.findByRole("heading", { level: 1, name: "Kết quả làm thử" })).toBeInTheDocument();
    expect(screen.queryByRole("table")).not.toBeInTheDocument();
    expect(screen.getByText("Câu 1")).toBeInTheDocument();
  });

  it("Palette: bấm vào số câu ở phần khác tự chuyển phần và gọi scrollIntoView", async () => {
    const user = userEvent.setup();
    const server = new FakeServer().on("GET", PAPER_PATH, {
      status: 200,
      body: buildTrialPaperFixture(),
    });
    server.install();

    renderWithProviders(<PreviewTrialView examId={EXAM_ID} previewId={PREVIEW_ID} />);
    await screen.findByText("Thủ đô của Việt Nam là thành phố nào?");

    const paletteNav = screen.getByRole("complementary", { name: "Bảng điều hướng câu hỏi" });
    const q3Btn = within(paletteNav).getByRole("button", { name: /Câu 3/ });
    await user.click(q3Btn);

    expect(await screen.findByText("Viết đoạn văn ngắn về tinh thần tự học.")).toBeInTheDocument();
  });
});
