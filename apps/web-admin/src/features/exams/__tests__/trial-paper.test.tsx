import { onlineManager } from "@tanstack/react-query";
import { act, fireEvent, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import Link from "next/link";
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
  onlineManager.setOnline(true);
  routerMock.push.mockReset();
  vi.spyOn(Element.prototype, "scrollIntoView").mockImplementation(() => {});
});

afterEach(() => {
  onlineManager.setOnline(true);
  vi.unstubAllGlobals();
  vi.restoreAllMocks();
});

describe("Trang làm thử như học viên (/exams/[id]/preview/[previewId]/try)", () => {
  it("AC-26 & AC-30: chỉ gọi GET .../paper trước khi nộp, không rò đáp án/rubric, không có đồng hồ đếm ngược", async () => {
    const user = userEvent.setup();
    vi.spyOn(HTMLMediaElement.prototype, "play").mockResolvedValue();
    vi.spyOn(HTMLMediaElement.prototype, "pause").mockImplementation(() => {});
    vi.spyOn(HTMLMediaElement.prototype, "load").mockImplementation(() => {});
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

    // Audio đính kèm dùng AttemptAudioPlayer (preload="none"), bấm Nghe trỏ đúng /api/v1/admin/question-assets/...
    const audioRegion = screen.getByRole("region", { name: "Trình phát audio" });
    const audioEl = audioRegion.querySelector("audio");
    expect(audioEl).toHaveAttribute("preload", "none");
    await user.click(within(audioRegion).getByRole("button", { name: "Nghe" }));
    await waitFor(() => {
      expect(audioEl?.getAttribute("src")).toContain("/api/v1/admin/question-assets/asset-audio-1");
    });
    expect(server.requests).toHaveLength(1);
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
      const resultHeading = screen.getByRole("heading", { level: 1, name: "Kết quả làm thử" });
      expect(resultHeading).toBeInTheDocument();
      expect(resultHeading).toHaveFocus();
    });

    // R1-33: xác minh exact POST .../grade request body khớp đáp án đã chọn
    const gradeRequests = server.calls("POST", GRADE_PATH);
    expect(gradeRequests).toHaveLength(2);
    expect(gradeRequests[1]!.body).toEqual({
      answers: {
        "q-sc-1": { type: "SINGLE_CHOICE", optionId: "opt-a" },
        "q-tf-2": {
          type: "TRUE_FALSE",
          statements: { s2: true, s3: false, s4: true },
        },
      },
    });

    // R1-32: LiveStatus thông báo đã chấm xong
    expect(document.querySelector('[role="status"][aria-live="polite"]')).toHaveTextContent(
      "Đã chấm xong bài làm thử.",
    );

    // AC-22: tổng điểm chỉ tính trắc nghiệm + ghi chú tự luận chưa chấm
    expect(screen.getByText("1,5 / 3 phần trắc nghiệm · tự luận chưa chấm")).toBeInTheDocument();

    // R1-4/R1-5: Phần 2 chỉ có Tự luận không được hiện "0 / 5"
    expect(screen.getByText("Chờ chấm tự luận")).toBeInTheDocument();
    expect(screen.queryByText(/0 \/ 5 trắc nghiệm/)).not.toBeInTheDocument();

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

    // Bấm Làm lại đề này -> xóa trắng câu trả lời, focus tiêu đề h1 và báo LiveStatus (R1-32)
    const requestCountBeforeRetake = server.requests.length;
    await user.click(screen.getByRole("button", { name: "Làm lại đề này" }));
    const retakeHeading = await screen.findByRole("heading", { level: 1, name: "Làm thử như học viên" });
    await waitFor(() => expect(retakeHeading).toHaveFocus());
    expect(document.querySelector('[role="status"][aria-live="polite"]')).toHaveTextContent(
      "Đã xóa bài làm trước để làm lại đề thử.",
    );
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
    await waitFor(() => expect(alert).toHaveFocus());
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
    const scrollSpy = vi.spyOn(Element.prototype, "scrollIntoView").mockImplementation(() => {});

    const matchMediaMock = vi.fn().mockImplementation((query: string) => ({
      matches: query === "(prefers-reduced-motion: reduce)",
      media: query,
      addEventListener: vi.fn(),
      removeEventListener: vi.fn(),
    }));
    vi.stubGlobal("matchMedia", matchMediaMock);

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
    await waitFor(() => expect(scrollSpy).toHaveBeenCalled());
    expect(scrollSpy).toHaveBeenCalledWith({ behavior: "auto", block: "start" });
  });

  it("R1-10: submit-time 410 PREVIEW_EXPIRED hiện cảnh báo hết hạn và gỡ beforeunload", async () => {
    const user = userEvent.setup();
    const server = new FakeServer()
      .on("GET", PAPER_PATH, { status: 200, body: buildTrialPaperFixture() })
      .on("POST", GRADE_PATH, { status: 410, body: { code: "PREVIEW_EXPIRED" } });
    server.install();

    renderWithProviders(<PreviewTrialView examId={EXAM_ID} previewId={PREVIEW_ID} />);
    await screen.findByText("Thủ đô của Việt Nam là thành phố nào?");

    await user.click(screen.getByRole("button", { name: "Nộp bài & Chấm thử" }));

    const alert = await screen.findByRole("alert");
    expect(alert).toHaveTextContent("Đề làm thử đã hết hạn");
    await waitFor(() => expect(alert).toHaveFocus());
    expect(within(alert).getByRole("link", { name: "Quay lại sinh đề thử mới" })).toHaveAttribute(
      "href",
      `/exams/${EXAM_ID}/review?tab=preview`,
    );

    const unloadEvent = new Event("beforeunload", { cancelable: true });
    window.dispatchEvent(unloadEvent);
    expect(unloadEvent.defaultPrevented).toBe(false);
  });

  it("R1-9 & R1-11: tiêu đề Câu 1 không nằm trong [data-exam-content] và optionOrder đảo ngược thứ tự DOM", async () => {
    const fixture = buildTrialPaperFixture();
    fixture.parts![0]!.blocks![0]!.items![0]!.question!.optionOrder = ["opt-b", "opt-a"];
    const server = new FakeServer().on("GET", PAPER_PATH, { status: 200, body: fixture });
    server.install();

    renderWithProviders(<PreviewTrialView examId={EXAM_ID} previewId={PREVIEW_ID} />);
    await screen.findByText("Thủ đô của Việt Nam là thành phố nào?");

    const heading = screen.getByRole("heading", { level: 3, name: "Câu 1" });
    expect(heading.closest("[data-exam-content]")).toBeNull();

    const q1Card = document.getElementById("trial-question-q-sc-1")!;
    const optionTexts = Array.from(q1Card.querySelectorAll("[data-question-options] [data-exam-content]")).map((el) =>
      el.textContent?.trim(),
    );
    expect(optionTexts).toEqual(["Đà Nẵng", "Hà Nội"]);
  });

  it("R1-6/R1-7 & R1-14: CLOZE render đề bài đúng 1 lần và điền trống ô 2 giữ nguyên Ô 2 ở kết quả", async () => {
    const user = userEvent.setup();
    const clozePaper: PreviewTrialPaperResponse = {
      examId: EXAM_ID,
      previewId: PREVIEW_ID,
      parts: [
        {
          partId: "part-cloze",
          name: "Phần Điền khuyết",
          order: 1,
          firstNumber: 1,
          lastNumber: 2,
          points: 2,
          blocks: [
            {
              blockId: "blk-c",
              questionType: "CLOZE",
              items: [
                {
                  kind: "QUESTION",
                  question: {
                    number: 1,
                    questionId: "q-cloze-1",
                    type: "CLOZE",
                    content: "<p>Điền từ thích hợp vào hai ô trống dưới đây:</p>",
                    points: 2,
                    body: {
                      type: "CLOZE",
                      passage: "<p>Thủ đô là [[b1]] bên bờ [[b2]].</p>",
                      blanks: [{ id: "b1" }, { id: "b2" }],
                    } as never,
                  },
                },
              ],
            },
          ],
        },
      ],
    };

    const server = new FakeServer()
      .on("GET", PAPER_PATH, { status: 200, body: clozePaper })
      .on("POST", GRADE_PATH, {
        status: 200,
        body: {
          examId: EXAM_ID,
          previewId: PREVIEW_ID,
          rawScore: 1,
          maxScore: 2,
          hasPendingManualGrading: false,
          items: [
            {
              questionId: "q-cloze-1",
              number: 1,
              type: "CLOZE",
              outcome: "PARTIAL",
              earnedPoints: 1,
              maxPoints: 2,
              pendingReview: false,
            },
          ],
        },
      });
    server.install();

    renderWithProviders(<PreviewTrialView examId={EXAM_ID} previewId={PREVIEW_ID} />);
    await screen.findByText("Điền từ thích hợp vào hai ô trống dưới đây:");
    expect(screen.getAllByText("Điền từ thích hợp vào hai ô trống dưới đây:")).toHaveLength(1);

    const inputs = await screen.findAllByRole("textbox");
    expect(inputs).toHaveLength(2);
    await user.type(inputs[1]!, "Sông Hồng");

    await user.click(screen.getByRole("button", { name: "Nộp bài & Chấm thử" }));
    const resultTable = await screen.findByRole("table", { name: "Chi tiết từng câu của kết quả làm thử" });
    expect(server.calls("POST", GRADE_PATH)[0]!.body).toEqual({
      answers: {
        "q-cloze-1": { type: "CLOZE", blanks: { b2: "Sông Hồng" } },
      },
    });
    expect(within(resultTable).getByText(/Ô 2:/)).toBeInTheDocument();
    expect(within(resultTable).getByText("Sông Hồng")).toBeInTheDocument();
    expect(within(resultTable).queryByText(/Ô 1:/)).not.toBeInTheDocument();
  });

  it("R1-18: bấm liên kết nội bộ trong app bật ConfirmDialog rời trang và đổi previewId xóa trắng đáp án", async () => {
    const user = userEvent.setup();
    const previewId2 = "55555555-5555-4555-8555-555555555555";
    const paperPath2 = `${EXAM_PATH}/preview/${previewId2}/paper`;
    const server = new FakeServer()
      .on("GET", PAPER_PATH, { status: 200, body: buildTrialPaperFixture() })
      .on("GET", paperPath2, {
        status: 200,
        body: { ...buildTrialPaperFixture(), previewId: previewId2 },
      });
    server.install();

    const view = renderWithProviders(
      <>
        <Link href="/exams">Danh sách bài thi</Link>
        <PreviewTrialView examId={EXAM_ID} previewId={PREVIEW_ID} />
      </>,
    );

    await screen.findByText("Thủ đô của Việt Nam là thành phố nào?");
    await user.click(screen.getByRole("radio", { name: /Hà Nội/ }));
    expect(screen.getByRole("radio", { name: /Hà Nội/ })).toBeChecked();

    await user.click(screen.getByRole("link", { name: "Danh sách bài thi" }));
    const dialog = await screen.findByRole("dialog");
    expect(within(dialog).getByText("Bỏ lần làm thử này?")).toBeInTheDocument();
    expect(routerMock.push).not.toHaveBeenCalled();

    await user.click(within(dialog).getByRole("button", { name: "Bỏ" }));
    expect(routerMock.push).toHaveBeenCalledWith("/exams");

    // Đổi previewId reset toàn bộ đáp án đã chọn
    view.rerender(
      <>
        <Link href="/exams">Danh sách bài thi</Link>
        <PreviewTrialView examId={EXAM_ID} previewId={previewId2} />
      </>,
    );
    await waitFor(() => expect(screen.getByRole("radio", { name: /Hà Nội/ })).not.toBeChecked());
  });

  it("R1-27: ngoại tuyến khi tải đề ban đầu hiện EmptyState ngoại tuyến kèm nút Thử lại", async () => {
    const user = userEvent.setup();
    onlineManager.setOnline(false);

    const server = new FakeServer().on("GET", PAPER_PATH, {
      status: 200,
      body: buildTrialPaperFixture(),
    });
    server.install();

    renderWithProviders(<PreviewTrialView examId={EXAM_ID} previewId={PREVIEW_ID} />);
    expect(await screen.findByRole("heading", { level: 1, name: "Mất kết nối mạng" })).toBeInTheDocument();

    onlineManager.setOnline(true);
    await user.click(screen.getByRole("button", { name: "Thử lại" }));
    expect(await screen.findByText("Thủ đô của Việt Nam là thành phố nào?")).toBeInTheDocument();
  });

  it("R1-30 & R1-39: TrialEssayEditor toolbar chỉ có 1 Tab stop với roving ArrowRight và part thiếu partId vẫn nhảy phần đúng", async () => {
    const user = userEvent.setup();
    const fixture = buildTrialPaperFixture();
    delete fixture.parts![0]!.partId;
    delete fixture.parts![1]!.partId;

    const server = new FakeServer().on("GET", PAPER_PATH, { status: 200, body: fixture });
    server.install();

    renderWithProviders(<PreviewTrialView examId={EXAM_ID} previewId={PREVIEW_ID} />);
    await screen.findByText("Thủ đô của Việt Nam là thành phố nào?");

    const paletteNav = screen.getByRole("complementary", { name: "Bảng điều hướng câu hỏi" });
    await user.click(within(paletteNav).getByRole("button", { name: /Câu 3/ }));
    expect(await screen.findByText("Viết đoạn văn ngắn về tinh thần tự học.")).toBeInTheDocument();

    const toolbar = screen.getByRole("toolbar", { name: "Thanh định dạng bài viết" });
    const buttons = within(toolbar).getAllByRole("button");
    await waitFor(() => expect(buttons[0]).not.toHaveAttribute("aria-disabled", "true"));
    expect(buttons.filter((btn) => btn.tabIndex === 0)).toHaveLength(1);
    expect(buttons.every((btn) => !btn.hasAttribute("disabled"))).toBe(true);

    buttons[0]!.focus();
    expect(buttons[0]).toHaveFocus();
    await user.keyboard("{ArrowRight}");
    expect(buttons[1]).toHaveFocus();

    // R2-30: phím tắt kèm Alt/Ctrl/Meta/Shift không cướp roving focus trong toolbar.
    await user.keyboard("{Alt>}{ArrowLeft}{/Alt}");
    expect(buttons[1]).toHaveFocus();

    // R3-7 & R3-8: không trộn focus-visible:outline-hidden với focus-visible:outline-3; nút bật dùng ::after, nút tắt không dùng border-transparent.
    for (const btn of buttons) {
      expect(btn.className).toContain("focus-visible:outline-hidden");
      expect(btn.className).not.toContain("focus-visible:outline-3");
    }
    await user.click(buttons[0]!);
    await waitFor(() => expect(buttons[0]).toHaveAttribute("aria-pressed", "true"));
    expect(buttons[0]!.className).toContain("after:border-primary-600");
    expect(buttons[1]!.className).not.toContain("border-transparent");

    // R2-31: mousedown trên nút aria-disabled bị preventDefault để không cướp focus khỏi trình soạn.
    const disabledUndoBtn = buttons[4]!;
    expect(disabledUndoBtn).toHaveAttribute("aria-disabled", "true");
    const downEvent = new MouseEvent("mousedown", { bubbles: true, cancelable: true });
    expect(fireEvent(disabledUndoBtn, downEvent)).toBe(false);
    expect(downEvent.defaultPrevented).toBe(true);
  });

  it("R3-9, R3-20 & R3-22: nút chuyển phần dùng border + ::after (không ring-inset) và 3 thao tác chuyển phần thủ công xóa pendingScrollRef", async () => {
    const user = userEvent.setup();
    const scrollSpy = vi.spyOn(Element.prototype, "scrollIntoView").mockImplementation(() => {});

    const threePartPaper = buildTrialPaperFixture();
    threePartPaper.parts!.push({
      partId: "part-3",
      name: "Phần 3 · Đọc hiểu",
      order: 3,
      durationMinutes: 30,
      firstNumber: 4,
      lastNumber: 4,
      points: 2,
      blocks: [
        {
          blockId: "blk-3",
          name: "Khối 3",
          questionType: "SINGLE_CHOICE",
          firstNumber: 4,
          lastNumber: 4,
          questionCount: 1,
          items: [
            {
              kind: "QUESTION",
              question: {
                number: 4,
                questionId: "q-sc-4",
                code: "C4",
                versionNo: 1,
                type: "SINGLE_CHOICE",
                content: "<p>Câu hỏi phần 3?</p>",
                points: 2,
                body: {
                  type: "SINGLE_CHOICE",
                  options: [{ id: "opt-3a", content: "<p>Phương án A</p>" }],
                } as never,
              },
            },
          ],
        },
      ],
    });

    const server = new FakeServer().on("GET", PAPER_PATH, {
      status: 200,
      body: threePartPaper,
    });
    server.install();

    renderWithProviders(<PreviewTrialView examId={EXAM_ID} previewId={PREVIEW_ID} />);
    await screen.findByText("Thủ đô của Việt Nam là thành phố nào?");

    const partNav = screen.getByRole("navigation", { name: "Chuyển phần đề thi" });
    const partTabs = within(partNav).getAllByRole("button");
    expect(partTabs[0]!.className).toContain("border ");
    expect(partTabs[0]!.className).toContain("border-primary-600");
    expect(partTabs[0]!.className).toContain("after:border-b-2");
    expect(partTabs[0]!.className).not.toContain("ring-inset");
    expect(partTabs[0]!.className).not.toContain("border-2");

    const paletteNav = screen.getByRole("complementary", { name: "Bảng điều hướng câu hỏi" });
    const q4Btn = within(paletteNav).getByRole("button", { name: /Câu 4/ });

    // 1. Batch palette (target part-3) + tab click thủ công (part-2) -> tới part-3 không tự cuộn.
    act(() => {
      fireEvent.click(q4Btn);
      fireEvent.click(partTabs[1]!);
    });
    expect(await screen.findByText("Viết đoạn văn ngắn về tinh thần tự học.")).toBeInTheDocument();
    await user.click(partTabs[2]!);
    expect(await screen.findByText("Câu hỏi phần 3?")).toBeInTheDocument();
    expect(scrollSpy).not.toHaveBeenCalled();

    // 2. Từ part-1: batch palette (target part-3) + Phần tiếp theo (part-2) -> tới part-3 không tự cuộn.
    await user.click(partTabs[0]!);
    await screen.findByText("Thủ đô của Việt Nam là thành phố nào?");
    act(() => {
      fireEvent.click(q4Btn);
      fireEvent.click(screen.getByRole("button", { name: "Phần tiếp theo" }));
    });
    await screen.findByText("Viết đoạn văn ngắn về tinh thần tự học.");
    await user.click(partTabs[2]!);
    await screen.findByText("Câu hỏi phần 3?");
    expect(scrollSpy).not.toHaveBeenCalled();

    // 3. Từ part-2: batch palette (target part-3) + Phần trước (part-1) -> tới part-3 không tự cuộn.
    await user.click(partTabs[1]!);
    await screen.findByText("Viết đoạn văn ngắn về tinh thần tự học.");
    act(() => {
      fireEvent.click(q4Btn);
      fireEvent.click(screen.getByRole("button", { name: "Phần trước" }));
    });
    await screen.findByText("Thủ đô của Việt Nam là thành phố nào?");
    await user.click(partTabs[2]!);
    await screen.findByText("Câu hỏi phần 3?");
    expect(scrollSpy).not.toHaveBeenCalled();

    // 4. Bấm palette Câu 1 bình thường (không ngắt) -> cuộn 1 lần.
    await user.click(within(paletteNav).getByRole("button", { name: /Câu 1/ }));
    await waitFor(() => expect(scrollSpy).toHaveBeenCalledTimes(1));
  });

  it("R2-29, R2-33..R2-36, R3-15, R3-16, R3-20: offline submit, target/mailto, đóng dialog khi nộp xong, Làm lại và đổi prop re-arm leave guard", async () => {
    const user = userEvent.setup();
    const previewId2 = "66666666-6666-4666-8666-666666666666";
    const paperPath2 = `${EXAM_PATH}/preview/${previewId2}/paper`;
    const server = new FakeServer()
      .on("GET", PAPER_PATH, { status: 200, body: buildTrialPaperFixture() })
      .on("GET", paperPath2, { status: 200, body: { ...buildTrialPaperFixture(), previewId: previewId2 } })
      .on(
        "POST",
        GRADE_PATH,
        { status: 500, body: { code: "INTERNAL_ERROR" } },
        { status: 200, body: buildPendingGradeFixture() },
        { status: 200, body: buildPendingGradeFixture() },
      );
    server.install();

    const view = renderWithProviders(
      <>
        <Link href="/exams" target=" _BLANK ">Tab mới</Link>
        <a href="mailto:support@thi360.test">Hỗ trợ</a>
        <Link href="/exams">Danh sách bài thi</Link>
        <PreviewTrialView examId={EXAM_ID} previewId={PREVIEW_ID} initialExpiresAt="2026-11-22T10:00:00Z" />
      </>,
    );
    await screen.findByText("Thủ đô của Việt Nam là thành phố nào?");

    // R2-33 & R2-34: target=" _BLANK " và non-http mailto không kích hoạt ConfirmDialog rời trang.
    await user.click(screen.getByText("Tab mới"));
    await user.click(screen.getByText("Hỗ trợ"));
    expect(screen.queryByRole("dialog")).not.toBeInTheDocument();

    // R2-29 & R3-20: networkMode: "always" + try/finally bảo vệ onlineManager.
    try {
      onlineManager.setOnline(false);
      await user.click(screen.getByRole("button", { name: "Nộp bài & Chấm thử" }));
      expect(await screen.findByRole("alert")).toBeInTheDocument();
      expect(server.calls("POST", GRADE_PATH)).toHaveLength(1);
    } finally {
      onlineManager.setOnline(true);
    }

    // R3-15/R3-16: Bấm Bỏ trên ConfirmDialog -> đặt leaveBypassRef = true (beforeunload không chặn).
    await user.click(screen.getByRole("button", { name: "Về Xem trước" }));
    const leaveDialog1 = await screen.findByRole("dialog");
    await user.click(within(leaveDialog1).getByRole("button", { name: "Bỏ" }));
    expect(routerMock.push).toHaveBeenCalledWith(`/exams/${EXAM_ID}/review?tab=preview`);
    const bypassedUnload = new Event("beforeunload", { cancelable: true });
    window.dispatchEvent(bypassedUnload);
    expect(bypassedUnload.defaultPrevented).toBe(false);

    // Nộp thành công -> Làm lại đề này phải reset leaveBypassRef = false (re-arm cả beforeunload & ConfirmDialog).
    await user.click(screen.getByRole("button", { name: "Nộp bài & Chấm thử" }));
    await screen.findByRole("heading", { level: 1, name: "Kết quả làm thử" });
    await user.click(screen.getByRole("button", { name: "Làm lại đề này" }));
    await screen.findByText("Thủ đô của Việt Nam là thành phố nào?");

    const rearmedUnload = new Event("beforeunload", { cancelable: true });
    window.dispatchEvent(rearmedUnload);
    expect(rearmedUnload.defaultPrevented).toBe(true);

    await user.click(screen.getByRole("link", { name: "Danh sách bài thi" }));
    const rearmedDialog = await screen.findByRole("dialog");
    await user.click(within(rearmedDialog).getByRole("button", { name: "Ở lại" }));

    // Mở ConfirmDialog khi POST /grade đang held -> khi POST xong phải tự đóng dialog (pendingLeaveHref = null).
    server.hold("POST", GRADE_PATH);
    await user.click(screen.getByRole("button", { name: "Nộp bài & Chấm thử" }));
    await user.click(screen.getByRole("link", { name: "Danh sách bài thi" }));
    expect(await screen.findByRole("dialog")).toBeInTheDocument();
    server.release("POST", GRADE_PATH);
    await screen.findByRole("heading", { level: 1, name: "Kết quả làm thử" });
    expect(screen.queryByRole("dialog")).not.toBeInTheDocument();

    // Quay lại làm bài, bấm Bỏ để arm leaveBypassRef = true rồi đổi prop initialExpiresAt -> re-arm leave guard.
    await user.click(screen.getByRole("button", { name: "Làm lại đề này" }));
    await screen.findByText("Thủ đô của Việt Nam là thành phố nào?");
    await user.click(screen.getByRole("button", { name: "Về Xem trước" }));
    await user.click(within(await screen.findByRole("dialog")).getByRole("button", { name: "Bỏ" }));
    const bypassedUnload2 = new Event("beforeunload", { cancelable: true });
    window.dispatchEvent(bypassedUnload2);
    expect(bypassedUnload2.defaultPrevented).toBe(false);

    view.rerender(
      <>
        <Link href="/exams" target=" _BLANK ">Tab mới</Link>
        <a href="mailto:support@thi360.test">Hỗ trợ</a>
        <Link href="/exams">Danh sách bài thi</Link>
        <PreviewTrialView examId={EXAM_ID} previewId={PREVIEW_ID} initialExpiresAt="2026-11-22T12:00:00Z" />
      </>,
    );
    await waitFor(() => {
      const ev = new Event("beforeunload", { cancelable: true });
      window.dispatchEvent(ev);
      expect(ev.defaultPrevented).toBe(true);
    });
    await user.click(screen.getByRole("link", { name: "Danh sách bài thi" }));
    const propRearmedDialog = await screen.findByRole("dialog");
    await user.click(within(propRearmedDialog).getByRole("button", { name: "Bỏ" }));

    // Đổi previewId cũng re-arm leave guard sạch.
    view.rerender(
      <>
        <Link href="/exams" target=" _BLANK ">Tab mới</Link>
        <a href="mailto:support@thi360.test">Hỗ trợ</a>
        <Link href="/exams">Danh sách bài thi</Link>
        <PreviewTrialView examId={EXAM_ID} previewId={previewId2} initialExpiresAt="2026-11-22T12:00:00Z" />
      </>,
    );
    await screen.findByText("Thủ đô của Việt Nam là thành phố nào?");
    const preview2Unload = new Event("beforeunload", { cancelable: true });
    window.dispatchEvent(preview2Unload);
    expect(preview2Unload.defaultPrevented).toBe(true);
    await user.click(screen.getByRole("link", { name: "Danh sách bài thi" }));
    expect(await screen.findByRole("dialog")).toBeInTheDocument();
  });
});
