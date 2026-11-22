import { onlineManager } from "@tanstack/react-query";
import { createTrialAudioAdapter } from "@thi360/ui/question";
import { act, fireEvent, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import en from "@/messages/en.json";
import { renderWithProviders } from "@/test/render";
import { PreviewTrialView } from "../trial/preview-trial-view";
import { useTrialAudioRegistry } from "../trial/trial-audio";
import { TrialPaperView } from "../trial/trial-paper-view";
import type { PreviewGradeResponse, PreviewTrialPaperResponse } from "../trial/types";
import { EXAM_ID, EXAM_PATH, FakeServer } from "./fake-exams-server";

const routerMock = vi.hoisted(() => ({ push: vi.fn(), replace: vi.fn() }));
vi.mock("next/navigation", () => ({
  useRouter: () => routerMock,
  usePathname: () => `/exams/${EXAM_ID}/preview/${PREVIEW_ID}/try`,
  useSearchParams: () => new URLSearchParams(),
}));

const PREVIEW_ID = "77777777-7777-4777-8777-777777777777";
const PAPER_PATH = `${EXAM_PATH}/preview/${PREVIEW_ID}/paper`;
const GRADE_PATH = `${EXAM_PATH}/preview/${PREVIEW_ID}/grade`;

function buildAudioTrialPaper(): PreviewTrialPaperResponse {
  return {
    examId: EXAM_ID,
    previewId: PREVIEW_ID,
    versionNo: 1,
    kindCode: "HSCA",
    timing: "PER_PART",
    durationMinutes: 60,
    parts: [
      {
        partId: "part-listening",
        name: "Phần 1 · Nghe hiểu",
        order: 1,
        durationMinutes: 30,
        firstNumber: 1,
        lastNumber: 3,
        points: 3,
        blocks: [
          {
            blockId: "blk-listen",
            name: "Nghe đơn & Nghe theo đoạn",
            questionType: "SINGLE_CHOICE",
            pointsEach: 1,
            firstNumber: 1,
            lastNumber: 3,
            questionCount: 3,
            items: [
              {
                kind: "QUESTION",
                question: {
                  number: 1,
                  questionId: "q-audio-1",
                  code: "AUD-1",
                  versionNo: 1,
                  type: "SINGLE_CHOICE",
                  content: "<p>Listen to the announcement.</p>",
                  points: 1,
                  attachments: [
                    { id: "asset-q1", name: "cau-1-station.mp3", mime: "AUDIO/MPEG" },
                    { id: "asset-pdf", name: "tai-lieu-kem.pdf", mime: "application/pdf" },
                  ],
                  body: {
                    type: "SINGLE_CHOICE",
                    options: [
                      { id: "opt-1a", content: "<p>Platform 2</p>" },
                      { id: "opt-1b", content: "<p>Platform 5</p>" },
                    ],
                  } as never,
                },
              },
              {
                kind: "SET",
                set: {
                  questionId: "set-audio-1",
                  content: "<p>Listening set instructions</p>",
                  passage: "<p>Listen to the conversation and answer questions 2–3.</p>",
                  attachments: [{ id: "asset-set-1", name: "bo-hoi-thoai.mp3", mime: "audio/mpeg" }],
                  children: [
                    {
                      number: 2,
                      questionId: "q-set-2",
                      code: "AUD-2",
                      versionNo: 1,
                      type: "SINGLE_CHOICE",
                      content: "<p>Where are the speakers?</p>",
                      points: 1,
                      attachments: [{ id: "asset-child-2", name: "child-cue.ogg", mime: "audio/ogg" }],
                      body: {
                        type: "SINGLE_CHOICE",
                        options: [
                          { id: "opt-2a", content: "<p>Library</p>" },
                          { id: "opt-2b", content: "<p>Museum</p>" },
                        ],
                      } as never,
                    },
                    {
                      number: 3,
                      questionId: "q-set-3",
                      code: "AUD-3",
                      versionNo: 1,
                      type: "SINGLE_CHOICE",
                      content: "<p>What time do they leave?</p>",
                      points: 1,
                      body: {
                        type: "SINGLE_CHOICE",
                        options: [
                          { id: "opt-3a", content: "<p>At 9:00</p>" },
                          { id: "opt-3b", content: "<p>At 10:00</p>" },
                        ],
                      } as never,
                    },
                  ],
                },
              },
            ],
          },
        ],
      },
      {
        partId: "part-reading",
        name: "Phần 2 · Đọc hiểu",
        order: 2,
        durationMinutes: 30,
        firstNumber: 4,
        lastNumber: 4,
        points: 1,
        blocks: [
          {
            blockId: "blk-read",
            name: "Đọc hiểu ngắn",
            questionType: "SINGLE_CHOICE",
            pointsEach: 1,
            firstNumber: 4,
            lastNumber: 4,
            questionCount: 1,
            items: [
              {
                kind: "QUESTION",
                question: {
                  number: 4,
                  questionId: "q-read-4",
                  code: "RD-4",
                  versionNo: 1,
                  type: "SINGLE_CHOICE",
                  content: "<p>Choose the synonym.</p>",
                  points: 1,
                  body: {
                    type: "SINGLE_CHOICE",
                    options: [
                      { id: "opt-4a", content: "<p>Swift</p>" },
                      { id: "opt-4b", content: "<p>Slow</p>" },
                    ],
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

function buildGradeResponse(): PreviewGradeResponse {
  return {
    examId: EXAM_ID,
    previewId: PREVIEW_ID,
    kindCode: "HSCA",
    rawScore: 0,
    maxScore: 4,
    hasPendingManualGrading: false,
    statistics: {
      totalCount: 4,
      correctCount: 0,
      partialCount: 0,
      wrongCount: 0,
      blankCount: 4,
      pendingReviewCount: 0,
    },
    items: [],
  };
}

function mockMediaElements() {
  const playSpy = vi.spyOn(HTMLMediaElement.prototype, "play").mockImplementation(async () => {});
  const pauseSpy = vi.spyOn(HTMLMediaElement.prototype, "pause").mockImplementation(() => {});
  const loadSpy = vi.spyOn(HTMLMediaElement.prototype, "load").mockImplementation(() => {});
  return { playSpy, pauseSpy, loadSpy };
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

describe("hai lần nghe", () => {
  it("dựng AttemptAudioPlayer (không seek/pause/tốc độ/controls, preload=none), đếm 2 lần nghe, chặn lần 3 và không gọi API audio", async () => {
    const user = userEvent.setup();
    mockMediaElements();
    const server = new FakeServer().on("GET", PAPER_PATH, {
      status: 200,
      body: buildAudioTrialPaper(),
    });
    server.install();

    const { container } = renderWithProviders(<PreviewTrialView examId={EXAM_ID} previewId={PREVIEW_ID} />);
    await screen.findByText("Listen to the announcement.");

    // Không render tệp đính kèm không phải audio (PDF); giữ tệp audio MIME chữ hoa AUDIO/MPEG, câu chùm và câu con.
    expect(screen.queryByText("tai-lieu-kem.pdf")).not.toBeInTheDocument();
    expect(screen.getByText("cau-1-station.mp3")).toBeInTheDocument();
    expect(screen.getByText("bo-hoi-thoai.mp3")).toBeInTheDocument();
    expect(screen.getByText("child-cue.ogg")).toBeInTheDocument();

    const regions = screen.getAllByRole("region", { name: "Trình phát audio" });
    expect(regions).toHaveLength(3);

    // Không có thanh tua, nút tạm dừng, điều khiển tốc độ hay native controls; preload="none".
    expect(container.querySelector('input[type="range"]')).toBeNull();
    expect(screen.queryByRole("slider")).toBeNull();
    expect(screen.queryByRole("button", { name: /tạm dừng|pause|tốc độ|speed|1\.5x|2x/i })).toBeNull();

    const q1Region = regions[0]!;
    const q1Audio = q1Region.querySelector("audio")!;
    expect(q1Audio).not.toBeNull();
    expect(q1Audio).not.toHaveAttribute("controls");
    expect(q1Audio).toHaveAttribute("preload", "none");
    expect(within(q1Region).getByText("Còn 2/2 lần nghe")).toBeInTheDocument();

    // Lần nghe 1: bấm Nghe -> gán đúng URL stream admin, còn 1/2, kết thúc -> nút Nghe bật lại.
    await user.click(within(q1Region).getByRole("button", { name: "Nghe" }));
    await waitFor(() => expect(within(q1Region).getByRole("button", { name: "Đang phát…" })).toBeDisabled());
    expect(q1Audio).toHaveAttribute("src", "/api/v1/admin/question-assets/asset-q1");
    expect(within(q1Region).getByText("Còn 1/2 lần nghe")).toBeInTheDocument();

    await act(async () => {
      fireEvent(q1Audio, new Event("ended"));
    });
    await waitFor(() => expect(within(q1Region).getByRole("button", { name: "Nghe" })).toBeEnabled());

    // Lần nghe 2: bấm Nghe -> còn 0/2, kết thúc -> khóa nút Nghe + aria-describedby giải thích hết lượt.
    await user.click(within(q1Region).getByRole("button", { name: "Nghe" }));
    await waitFor(() => expect(within(q1Region).getByRole("button", { name: "Đang phát…" })).toBeDisabled());
    expect(within(q1Region).getByText("Còn 0/2 lần nghe")).toBeInTheDocument();

    await act(async () => {
      fireEvent(q1Audio, new Event("ended"));
    });

    const disabledBtn = await within(q1Region).findByRole("button", { name: "Nghe" });
    expect(disabledBtn).toBeDisabled();
    expect(within(q1Region).getByText("Đã hết lượt nghe (0/2)")).toBeInTheDocument();
    const describedBy = disabledBtn.getAttribute("aria-describedby");
    expect(describedBy).toBeTruthy();
    expect(document.getElementById(describedBy!)?.textContent).toContain("hết số lần nghe");

    // Lần 3 bị chặn: bấm nút không mở thêm lượt, adapter trực tiếp ném AUDIO_PLAYS_EXHAUSTED.
    await user.click(disabledBtn);
    expect(within(q1Region).getByText("Đã hết lượt nghe (0/2)")).toBeInTheDocument();

    const directAdapter = createTrialAudioAdapter({
      assetUrl: "/api/v1/admin/question-assets/asset-q1",
      maxPlays: 2,
    });
    const p1 = await directAdapter.openPlay("r1");
    await directAdapter.endPlay(p1.playId!);
    const p2 = await directAdapter.openPlay("r2");
    await directAdapter.endPlay(p2.playId!);
    await expect(directAdapter.openPlay("r3")).rejects.toMatchObject({ code: "AUDIO_PLAYS_EXHAUSTED" });

    // Không gọi bất kỳ endpoint audio hay /api/v1/me/attempts nào: chỉ duy nhất GET .../paper.
    expect(server.requests).toHaveLength(1);
    expect(server.requests[0]!.method).toBe("GET");
    expect(server.requests[0]!.url.pathname).toBe(PAPER_PATH);
    expect(server.requests.some((req) => req.url.pathname.includes("/me/attempts"))).toBe(false);
    expect(server.requests.some((req) => req.url.pathname.includes("audio"))).toBe(false);

    // Audio của câu chùm (set) đặt phía trên QuestionSet và hoạt động độc lập.
    const setCard = document.getElementById("trial-question-set-audio-1")!;
    const setRegion = within(setCard).getAllByRole("region", { name: "Trình phát audio" })[0]!;
    const setHeading = within(setCard).getByText("Listen to the conversation and answer questions 2–3.");
    expect(setRegion.compareDocumentPosition(setHeading) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy();
    await user.click(within(setRegion).getByRole("button", { name: "Nghe" }));
    await waitFor(() =>
      expect(setRegion.querySelector("audio")).toHaveAttribute("src", "/api/v1/admin/question-assets/asset-set-1"),
    );
    expect(within(setRegion).getByText("Còn 1/2 lần nghe")).toBeInTheDocument();
  });

  it("chuyển sang phần khác rồi quay lại vẫn giữ nguyên số lượt nghe đã dùng trong bộ nhớ trình duyệt; Làm lại reset về 2/2", async () => {
    const user = userEvent.setup();
    const { pauseSpy } = mockMediaElements();
    const server = new FakeServer()
      .on("GET", PAPER_PATH, { status: 200, body: buildAudioTrialPaper() })
      .on("POST", GRADE_PATH, { status: 200, body: buildGradeResponse() });
    server.install();

    renderWithProviders(<PreviewTrialView examId={EXAM_ID} previewId={PREVIEW_ID} />);
    await screen.findByText("Listen to the announcement.");

    const firstRegion = screen.getAllByRole("region", { name: "Trình phát audio" })[0]!;
    const firstAudio = firstRegion.querySelector("audio")!;

    await user.click(within(firstRegion).getByRole("button", { name: "Nghe" }));
    await waitFor(() => expect(within(firstRegion).getByText("Còn 1/2 lần nghe")).toBeInTheDocument());
    await act(async () => {
      fireEvent(firstAudio, new Event("ended"));
    });
    await waitFor(() => expect(within(firstRegion).getByRole("button", { name: "Nghe" })).toBeEnabled());

    // Chuyển sang Phần 2 (unmount Phần 1 -> pause audio).
    pauseSpy.mockClear();
    await user.click(screen.getByRole("button", { name: /Phần 2 · Đọc hiểu/ }));
    expect(await screen.findByText("Choose the synonym.")).toBeInTheDocument();
    expect(pauseSpy).toHaveBeenCalled();

    // Chuyển lại Phần 1 -> số lượt nghe còn lại vẫn là 1/2 (không bị reset về 2/2), hoàn thành lượt 2 -> khóa 0/2.
    await user.click(screen.getByRole("button", { name: /Phần 1 · Nghe hiểu/ }));
    await screen.findByText("Listen to the announcement.");
    const restoredRegion = screen.getAllByRole("region", { name: "Trình phát audio" })[0]!;
    expect(within(restoredRegion).getByText("Còn 1/2 lần nghe")).toBeInTheDocument();
    expect(within(restoredRegion).getByRole("button", { name: "Nghe" })).toBeEnabled();

    await user.click(within(restoredRegion).getByRole("button", { name: "Nghe" }));
    await waitFor(() => expect(within(restoredRegion).getByText("Còn 0/2 lần nghe")).toBeInTheDocument());
    await act(async () => {
      fireEvent(restoredRegion.querySelector("audio")!, new Event("ended"));
    });
    const exhaustedBtn = await within(restoredRegion).findByRole("button", { name: "Nghe" });
    expect(exhaustedBtn).toBeDisabled();
    expect(within(restoredRegion).getByText("Đã hết lượt nghe (0/2)")).toBeInTheDocument();

    // Nộp bài -> Làm lại đề này reset về 2/2 và phát trọn vẹn 2 lượt mới (R1-15).
    await user.click(screen.getByRole("button", { name: "Nộp bài & Chấm thử" }));
    await screen.findByRole("heading", { level: 1, name: "Kết quả làm thử" });
    await user.click(screen.getByRole("button", { name: "Làm lại đề này" }));
    await screen.findByText("Listen to the announcement.");
    const resetRegion = screen.getAllByRole("region", { name: "Trình phát audio" })[0]!;
    const resetAudio = resetRegion.querySelector("audio")!;
    expect(within(resetRegion).getByText("Còn 2/2 lần nghe")).toBeInTheDocument();

    await user.click(within(resetRegion).getByRole("button", { name: "Nghe" }));
    await waitFor(() => expect(within(resetRegion).getByText("Còn 1/2 lần nghe")).toBeInTheDocument());
    await act(async () => {
      fireEvent(resetAudio, new Event("ended"));
    });
    await waitFor(() => expect(within(resetRegion).getByRole("button", { name: "Nghe" })).toBeEnabled());

    await user.click(within(resetRegion).getByRole("button", { name: "Nghe" }));
    await waitFor(() => expect(within(resetRegion).getByText("Còn 0/2 lần nghe")).toBeInTheDocument());
    await act(async () => {
      fireEvent(resetAudio, new Event("ended"));
    });
    expect(await within(resetRegion).findByRole("button", { name: "Nghe" })).toBeDisabled();
    expect(within(resetRegion).getByText("Đã hết lượt nghe (0/2)")).toBeInTheDocument();
  });

  it("phát tệp thứ hai trên cùng phần tự dừng tệp đang phát không trừ lượt; đang nộp bài khóa tạm (không báo đã nộp) và unmount dừng phát", async () => {
    const user = userEvent.setup();
    const { pauseSpy } = mockMediaElements();
    const server = new FakeServer()
      .on("GET", PAPER_PATH, { status: 200, body: buildAudioTrialPaper() })
      .on("POST", GRADE_PATH, { status: 200, body: buildGradeResponse() })
      .hold("POST", GRADE_PATH);
    server.install();

    const view = renderWithProviders(<PreviewTrialView examId={EXAM_ID} previewId={PREVIEW_ID} />);
    await screen.findByText("Listen to the announcement.");

    const regions = screen.getAllByRole("region", { name: "Trình phát audio" });
    const q1Region = regions[0]!;
    const setRegion = regions[1]!;

    // Phát tệp 1 -> tệp 1 đang phát (1/2).
    await user.click(within(q1Region).getByRole("button", { name: "Nghe" }));
    await waitFor(() => expect(within(q1Region).getByRole("button", { name: "Đang phát…" })).toBeDisabled());

    // Bấm phát tệp 2 -> tệp 1 tự dừng & hiện "Phát lại" (không trừ thêm lượt: vẫn còn 1/2).
    pauseSpy.mockClear();
    await user.click(within(setRegion).getByRole("button", { name: "Nghe" }));
    await waitFor(() => expect(within(setRegion).getByRole("button", { name: "Đang phát…" })).toBeDisabled());
    expect(pauseSpy).toHaveBeenCalled();
    expect(within(q1Region).getByRole("button", { name: "Phát lại" })).toBeEnabled();
    expect(within(q1Region).getByText("Còn 1/2 lần nghe")).toBeInTheDocument();

    // Khi đang nộp bài (pending), dừng audio và khóa trình phát bằng disabled={submitting} (KHÔNG báo đã nộp trước HTTP 200).
    pauseSpy.mockClear();
    await user.click(screen.getByRole("button", { name: "Nộp bài & Chấm thử" }));
    await waitFor(() => expect(pauseSpy).toHaveBeenCalled());
    expect(within(setRegion).getByRole("button", { name: "Nghe" })).toBeDisabled();
    expect(within(setRegion).getByText("Trình phát audio đang tạm khóa.")).toBeInTheDocument();
    expect(screen.queryByText("Bài thi đã nộp, audio đã dừng phát.")).not.toBeInTheDocument();

    server.release("POST", GRADE_PATH);
    await screen.findByRole("heading", { level: 1, name: "Kết quả làm thử" });

    // Unmount trực tiếp khi đang phát cũng gọi pause().
    await user.click(screen.getByRole("button", { name: "Làm lại đề này" }));
    await screen.findByText("Listen to the announcement.");
    const resetFirst = screen.getAllByRole("region", { name: "Trình phát audio" })[0]!;
    await user.click(within(resetFirst).getByRole("button", { name: "Nghe" }));
    await waitFor(() => expect(within(resetFirst).getByRole("button", { name: "Đang phát…" })).toBeDisabled());
    pauseSpy.mockClear();
    view.unmount();
    expect(pauseSpy).toHaveBeenCalled();
  });

  it("R1-11: POST /grade trả lỗi 500 mở khóa lại trình phát và cho phát lại không tốn thêm lượt", async () => {
    const user = userEvent.setup();
    const { pauseSpy } = mockMediaElements();
    const server = new FakeServer()
      .on("GET", PAPER_PATH, { status: 200, body: buildAudioTrialPaper() })
      .on("POST", GRADE_PATH, { status: 500, body: { code: "INTERNAL_ERROR" } })
      .hold("POST", GRADE_PATH);
    server.install();

    renderWithProviders(<PreviewTrialView examId={EXAM_ID} previewId={PREVIEW_ID} />);
    await screen.findByText("Listen to the announcement.");

    const q1Region = screen.getAllByRole("region", { name: "Trình phát audio" })[0]!;
    await user.click(within(q1Region).getByRole("button", { name: "Nghe" }));
    await waitFor(() => expect(within(q1Region).getByRole("button", { name: "Đang phát…" })).toBeDisabled());
    expect(within(q1Region).getByText("Còn 1/2 lần nghe")).toBeInTheDocument();

    pauseSpy.mockClear();
    await user.click(screen.getByRole("button", { name: "Nộp bài & Chấm thử" }));
    await waitFor(() => expect(pauseSpy).toHaveBeenCalled());
    expect(within(q1Region).getByRole("button", { name: "Nghe" })).toBeDisabled();
    expect(within(q1Region).getByText("Trình phát audio đang tạm khóa.")).toBeInTheDocument();
    expect(screen.queryByText("Bài thi đã nộp, audio đã dừng phát.")).not.toBeInTheDocument();

    server.release("POST", GRADE_PATH);
    expect(await screen.findByRole("alert")).toHaveTextContent("Không chấm được bài làm thử");

    const resumeBtn = within(q1Region).getByRole("button", { name: "Phát lại" });
    expect(resumeBtn).toBeEnabled();
    expect(within(q1Region).queryByText("Trình phát audio đang tạm khóa.")).not.toBeInTheDocument();

    await user.click(resumeBtn);
    await waitFor(() => expect(within(q1Region).getByRole("button", { name: "Đang phát…" })).toBeDisabled());
    expect(within(q1Region).getByText("Còn 1/2 lần nghe")).toBeInTheDocument();
  });

  it("R1-16: xác nhận rời trang (Về Xem trước -> Bỏ) dừng phát audio đang chạy", async () => {
    const user = userEvent.setup();
    const { pauseSpy } = mockMediaElements();
    const server = new FakeServer().on("GET", PAPER_PATH, {
      status: 200,
      body: buildAudioTrialPaper(),
    });
    server.install();

    renderWithProviders(<PreviewTrialView examId={EXAM_ID} previewId={PREVIEW_ID} />);
    await screen.findByText("Listen to the announcement.");

    const q1Region = screen.getAllByRole("region", { name: "Trình phát audio" })[0]!;
    await user.click(within(q1Region).getByRole("button", { name: "Nghe" }));
    await waitFor(() => expect(within(q1Region).getByRole("button", { name: "Đang phát…" })).toBeDisabled());

    pauseSpy.mockClear();
    await user.click(screen.getByRole("button", { name: "Về Xem trước" }));
    const dialog = await screen.findByRole("dialog");
    await user.click(within(dialog).getByRole("button", { name: "Bỏ" }));
    expect(pauseSpy).toHaveBeenCalled();
    expect(routerMock.push).toHaveBeenCalledWith(`/exams/${EXAM_ID}/review?tab=preview`);
  });

  it("R1-5/R1-6/R1-7: shallow-clone paper không tạo lại adapter, gọi reset() độc lập reset lượt nghe và đổi previewId không rò state", async () => {
    const user = userEvent.setup();
    mockMediaElements();

    function RegistryHarness({
      paper,
      epoch = 0,
    }: {
      paper: PreviewTrialPaperResponse;
      epoch?: number;
    }) {
      const { registry, reset } = useTrialAudioRegistry(paper, epoch);
      return (
        <div>
          <button type="button" onClick={reset}>
            Reset registry only
          </button>
          <TrialPaperView
            paper={paper}
            answers={{}}
            onAnswerChange={() => {}}
            onSubmit={() => {}}
            onRequestLeave={() => {}}
            submitting={false}
            submitError={null}
            audioRegistry={registry}
          />
        </div>
      );
    }

    const basePaper = buildAudioTrialPaper();
    const view = renderWithProviders(<RegistryHarness paper={basePaper} />);
    await screen.findByText("Listen to the announcement.");

    const q1Group = () => screen.getByRole("group", { name: "Trình phát audio · Câu 1" });
    await user.click(within(q1Group()).getByRole("button", { name: "Nghe" }));
    await waitFor(() => expect(within(q1Group()).getByText("Còn 1/2 lần nghe")).toBeInTheDocument());

    // Shallow-clone paper (reference mới, cùng previewId và assetIds): adapter giữ nguyên, ended thành công không AUDIO_PLAY_STALE.
    view.rerender(<RegistryHarness paper={{ ...basePaper }} />);
    expect(within(q1Group()).getByText("Còn 1/2 lần nghe")).toBeInTheDocument();
    await act(async () => {
      fireEvent(q1Group().querySelector("audio")!, new Event("ended"));
    });
    await waitFor(() => expect(within(q1Group()).getByRole("button", { name: "Nghe" })).toBeEnabled());
    expect(within(q1Group()).getByText("Còn 1/2 lần nghe")).toBeInTheDocument();
    expect(within(q1Group()).queryByText(/Không phát được|thử lại/i)).not.toBeInTheDocument();

    // Dùng hết lượt 2 -> bị khóa 0/2 -> gọi reset() mà không tăng external epoch -> nghe lại được 2 lần mới.
    await user.click(within(q1Group()).getByRole("button", { name: "Nghe" }));
    await waitFor(() => expect(within(q1Group()).getByText("Còn 0/2 lần nghe")).toBeInTheDocument());
    await act(async () => {
      fireEvent(q1Group().querySelector("audio")!, new Event("ended"));
    });
    expect(await within(q1Group()).findByRole("button", { name: "Nghe" })).toBeDisabled();
    expect(within(q1Group()).getByText("Đã hết lượt nghe (0/2)")).toBeInTheDocument();

    await user.click(screen.getByRole("button", { name: "Reset registry only" }));
    await waitFor(() => expect(within(q1Group()).getByText("Còn 2/2 lần nghe")).toBeInTheDocument());
    expect(within(q1Group()).getByRole("button", { name: "Nghe" })).toBeEnabled();

    await user.click(within(q1Group()).getByRole("button", { name: "Nghe" }));
    await waitFor(() => expect(within(q1Group()).getByText("Còn 1/2 lần nghe")).toBeInTheDocument());
    await act(async () => {
      fireEvent(q1Group().querySelector("audio")!, new Event("ended"));
    });
    await user.click(within(q1Group()).getByRole("button", { name: "Nghe" }));
    await waitFor(() => expect(within(q1Group()).getByText("Còn 0/2 lần nghe")).toBeInTheDocument());
    await act(async () => {
      fireEvent(q1Group().querySelector("audio")!, new Event("ended"));
    });
    expect(await within(q1Group()).findByText("Đã hết lượt nghe (0/2)")).toBeInTheDocument();

    // Đổi previewId mà không unmount -> state cũ không rò sang preview mới.
    view.rerender(<RegistryHarness paper={{ ...basePaper, previewId: "88888888-8888-4888-8888-888888888888" }} />);
    await waitFor(() => expect(within(q1Group()).getByText("Còn 2/2 lần nghe")).toBeInTheDocument());
    expect(within(q1Group()).getByRole("button", { name: "Nghe" })).toBeEnabled();

    // TrialPaperView standalone (không truyền audioRegistry) dùng focusHeadingKey cho fallbackAudioRegistry.
    const standalone = renderWithProviders(
      <TrialPaperView
        paper={basePaper}
        answers={{}}
        onAnswerChange={() => {}}
        onSubmit={() => {}}
        onRequestLeave={() => {}}
        submitting={false}
        submitError={null}
        focusHeadingKey={0}
      />,
    );
    const standaloneGroups = screen.getAllByRole("group", { name: "Trình phát audio · Câu 1" });
    const standaloneQ1 = standaloneGroups[1]!;
    await user.click(within(standaloneQ1).getByRole("button", { name: "Nghe" }));
    await waitFor(() => expect(within(standaloneQ1).getByText("Còn 1/2 lần nghe")).toBeInTheDocument());
    await act(async () => {
      fireEvent(standaloneQ1.querySelector("audio")!, new Event("ended"));
    });
    await user.click(within(standaloneQ1).getByRole("button", { name: "Nghe" }));
    await act(async () => {
      fireEvent(standaloneQ1.querySelector("audio")!, new Event("ended"));
    });
    expect(await within(standaloneQ1).findByText("Đã hết lượt nghe (0/2)")).toBeInTheDocument();

    standalone.rerender(
      <TrialPaperView
        paper={basePaper}
        answers={{}}
        onAnswerChange={() => {}}
        onSubmit={() => {}}
        onRequestLeave={() => {}}
        submitting={false}
        submitError={null}
        focusHeadingKey={1}
      />,
    );
    await waitFor(() => expect(within(standaloneQ1).getByText("Còn 2/2 lần nghe")).toBeInTheDocument());
    await user.click(within(standaloneQ1).getByRole("button", { name: "Nghe" }));
    await waitFor(() => expect(within(standaloneQ1).getByText("Còn 1/2 lần nghe")).toBeInTheDocument());
  });

  it("R1-8/R1-9: cùng assetId gắn ở hai phân đoạn (hoặc trùng tệp) vẫn chặn phát đồng thời và dùng chung 2 lượt nghe", async () => {
    const user = userEvent.setup();
    const { pauseSpy } = mockMediaElements();
    const splitPaper: PreviewTrialPaperResponse = {
      examId: EXAM_ID,
      previewId: PREVIEW_ID,
      parts: [
        {
          partId: "part-split",
          name: "Phần phân đoạn",
          order: 1,
          firstNumber: 1,
          lastNumber: 2,
          points: 2,
          displaySegments: [
            { code: "SEG_1", name: "Phân đoạn 1", fromPosition: 1, toPosition: 1 },
            { code: "SEG_2", name: "Phân đoạn 2", fromPosition: 2, toPosition: 2 },
          ],
          blocks: [
            {
              blockId: "blk-split",
              name: "Khối đọc nghe tách đoạn",
              questionType: "SINGLE_CHOICE",
              firstNumber: 1,
              lastNumber: 2,
              questionCount: 2,
              items: [
                {
                  kind: "SET",
                  set: {
                    questionId: "set-split",
                    passage: "<p>Ngữ liệu chung chia hai đoạn.</p>",
                    attachments: [{ id: "asset-shared", name: "shared-cue.mp3", mime: "audio/mpeg" }],
                    children: [
                      {
                        number: 1,
                        questionId: "q-split-1",
                        type: "SINGLE_CHOICE",
                        content: "<p>Câu đoạn 1</p>",
                        points: 1,
                        body: { type: "SINGLE_CHOICE", options: [{ id: "a", content: "<p>A</p>" }] } as never,
                      },
                      {
                        number: 2,
                        questionId: "q-split-2",
                        type: "SINGLE_CHOICE",
                        content: "<p>Câu đoạn 2</p>",
                        points: 1,
                        body: { type: "SINGLE_CHOICE", options: [{ id: "b", content: "<p>B</p>" }] } as never,
                      },
                    ],
                  },
                },
              ],
            },
          ],
        },
      ],
    };

    const server = new FakeServer().on("GET", PAPER_PATH, { status: 200, body: splitPaper });
    server.install();

    renderWithProviders(<PreviewTrialView examId={EXAM_ID} previewId={PREVIEW_ID} />);
    await screen.findByText("Câu đoạn 1");

    const regions = screen.getAllByRole("region", { name: "Trình phát audio" });
    expect(regions).toHaveLength(2);
    const firstInstance = regions[0]!;
    const secondInstance = regions[1]!;

    await user.click(within(firstInstance).getByRole("button", { name: "Nghe" }));
    await waitFor(() => expect(within(firstInstance).getByRole("button", { name: "Đang phát…" })).toBeDisabled());
    expect(within(secondInstance).getByText("Còn 1/2 lần nghe")).toBeInTheDocument();

    pauseSpy.mockClear();
    await user.click(within(secondInstance).getByRole("button", { name: "Phát lại" }));
    await waitFor(() => expect(within(secondInstance).getByRole("button", { name: "Đang phát…" })).toBeDisabled());
    expect(pauseSpy).toHaveBeenCalled();
    expect(within(firstInstance).getByRole("button", { name: "Phát lại" })).toBeEnabled();
    expect(within(firstInstance).getByText("Còn 1/2 lần nghe")).toBeInTheDocument();
  });

  it("R1-10: ngắt A bằng B, sang Phần 2 rồi quay lại Phần 1 không tự phát ping-pong và Phát lại hoạt động cả khi 0/2 lượt thứ hai", async () => {
    const user = userEvent.setup();
    const { playSpy } = mockMediaElements();
    const server = new FakeServer().on("GET", PAPER_PATH, {
      status: 200,
      body: buildAudioTrialPaper(),
    });
    server.install();

    renderWithProviders(<PreviewTrialView examId={EXAM_ID} previewId={PREVIEW_ID} />);
    await screen.findByText("Listen to the announcement.");

    const getA = () => screen.getByRole("group", { name: "Trình phát audio · Câu 1" });
    const getB = () => screen.getByRole("group", { name: "Trình phát audio chung · Câu 2–3" });

    // Đưa A sang lượt 2 (0/2 đang phát), rồi ngắt A bằng B.
    await user.click(within(getA()).getByRole("button", { name: "Nghe" }));
    await waitFor(() => expect(within(getA()).getByText("Còn 1/2 lần nghe")).toBeInTheDocument());
    await act(async () => {
      fireEvent(getA().querySelector("audio")!, new Event("ended"));
    });
    await user.click(within(getA()).getByRole("button", { name: "Nghe" }));
    await waitFor(() => expect(within(getA()).getByRole("button", { name: "Đang phát…" })).toBeDisabled());
    expect(within(getA()).getByText("Còn 0/2 lần nghe")).toBeInTheDocument();

    await user.click(within(getB()).getByRole("button", { name: "Nghe" }));
    await waitFor(() => expect(within(getB()).getByRole("button", { name: "Đang phát…" })).toBeDisabled());
    expect(within(getA()).getByRole("button", { name: "Phát lại" })).toBeEnabled();

    // Chuyển sang Phần 2 rồi quay lại Phần 1: không tự gọi play() (không ping-pong stopSignals).
    await user.click(screen.getByRole("button", { name: /Phần 2 · Đọc hiểu/ }));
    await screen.findByText("Choose the synonym.");

    playSpy.mockClear();
    await user.click(screen.getByRole("button", { name: /Phần 1 · Nghe hiểu/ }));
    await screen.findByText("Listen to the announcement.");
    await act(async () => {
      await Promise.resolve();
    });

    expect(playSpy).not.toHaveBeenCalled();
    expect(within(getA()).getByRole("button", { name: "Phát lại" })).toBeEnabled();
    expect(within(getA()).getByText("Còn 0/2 lần nghe")).toBeInTheDocument();
    expect(within(getB()).getByRole("button", { name: "Phát lại" })).toBeEnabled();
    expect(within(getB()).getByText("Còn 1/2 lần nghe")).toBeInTheDocument();

    // Bấm Phát lại trên B: tiếp tục phát không trừ thêm lượt (vẫn 1/2).
    await user.click(within(getB()).getByRole("button", { name: "Phát lại" }));
    await waitFor(() => expect(within(getB()).getByRole("button", { name: "Đang phát…" })).toBeDisabled());
    expect(within(getB()).getByText("Còn 1/2 lần nghe")).toBeInTheDocument();

    // Bấm Phát lại trên A (đang 0/2 lượt 2): phát tiếp được, kết thúc mới khóa 0/2.
    await user.click(within(getA()).getByRole("button", { name: "Phát lại" }));
    await waitFor(() => expect(within(getA()).getByRole("button", { name: "Đang phát…" })).toBeDisabled());
    expect(within(getB()).getByRole("button", { name: "Phát lại" })).toBeEnabled();
    await act(async () => {
      fireEvent(getA().querySelector("audio")!, new Event("ended"));
    });
    expect(await within(getA()).findByText("Đã hết lượt nghe (0/2)")).toBeInTheDocument();
    expect(within(getA()).getByRole("button", { name: "Nghe" })).toBeDisabled();
  });

  it("R1-12/R1-13: nhãn ngữ cảnh và tên nhóm role=group riêng biệt cho từng câu, bộ và câu con (vi & en), câu con đứng sau ngữ liệu", async () => {
    mockMediaElements();
    const server = new FakeServer().on("GET", PAPER_PATH, {
      status: 200,
      body: buildAudioTrialPaper(),
    });
    server.install();

    const viView = renderWithProviders(<PreviewTrialView examId={EXAM_ID} previewId={PREVIEW_ID} />);
    await screen.findByText("Listen to the announcement.");

    const q1Group = screen.getByRole("group", { name: "Trình phát audio · Câu 1" });
    const setGroup = screen.getByRole("group", { name: "Trình phát audio chung · Câu 2–3" });
    const childGroup = screen.getByRole("group", { name: "Trình phát audio · Câu 2" });
    expect(within(q1Group).getByText("Audio · Câu 1")).toBeInTheDocument();
    expect(within(setGroup).getByText("Audio chung · Câu 2–3")).toBeInTheDocument();
    expect(within(childGroup).getByText("Audio · Câu 2")).toBeInTheDocument();

    const passage = screen.getByText("Listen to the conversation and answer questions 2–3.");
    expect(setGroup.compareDocumentPosition(passage) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy();
    expect(passage.compareDocumentPosition(childGroup) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy();
    viView.unmount();

    renderWithProviders(<PreviewTrialView examId={EXAM_ID} previewId={PREVIEW_ID} />, {
      locale: "en",
      messages: en,
    });
    await screen.findByText("Listen to the announcement.");

    const enQ1 = screen.getByRole("group", { name: "Audio player · Question 1" });
    const enSet = screen.getByRole("group", { name: "Shared audio player · Questions 2–3" });
    const enChild = screen.getByRole("group", { name: "Audio player · Question 2" });
    expect(within(enQ1).getByText("Audio · Question 1")).toBeInTheDocument();
    expect(within(enSet).getByText("Shared audio · Questions 2–3")).toBeInTheDocument();
    expect(within(enChild).getByText("Audio · Question 2")).toBeInTheDocument();
  });

  it("hỗ trợ tiếng Anh đầy đủ qua UiStringsProvider", async () => {
    const user = userEvent.setup();
    mockMediaElements();
    const server = new FakeServer().on("GET", PAPER_PATH, {
      status: 200,
      body: buildAudioTrialPaper(),
    });
    server.install();

    renderWithProviders(<PreviewTrialView examId={EXAM_ID} previewId={PREVIEW_ID} />, {
      locale: "en",
      messages: en,
    });
    await screen.findByText("Listen to the announcement.");

    const regions = screen.getAllByRole("region", { name: "Audio player" });
    const firstRegion = regions[0]!;
    expect(within(firstRegion).getByText("2/2 plays left")).toBeInTheDocument();

    await user.click(within(firstRegion).getByRole("button", { name: "Play" }));
    await waitFor(() => expect(within(firstRegion).getByText("1/2 plays left")).toBeInTheDocument());

    await act(async () => {
      fireEvent(firstRegion.querySelector("audio")!, new Event("ended"));
    });
    await user.click(within(firstRegion).getByRole("button", { name: "Play" }));
    await act(async () => {
      fireEvent(firstRegion.querySelector("audio")!, new Event("ended"));
    });

    const playBtn = within(firstRegion).getByRole("button", { name: "Play" });
    expect(playBtn).toBeDisabled();
    expect(within(firstRegion).getByText("No plays left (0/2)")).toBeInTheDocument();
    expect(within(firstRegion).getByText("You have used all plays for this audio.")).toBeInTheDocument();
  });
});
