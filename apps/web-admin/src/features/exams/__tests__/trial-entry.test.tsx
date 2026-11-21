import { screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, describe, expect, it, vi } from "vitest";
import { renderWithProviders } from "@/test/render";
import { mapExamDetail } from "../api-mapping";
import { ExamPreviewPanel } from "../components/exam-preview-panel";
import type { PreviewBlock, PreviewPaper, PreviewQuestion } from "../review-types";
import { EXAM_ID, EXAM_PATH, examDetailWire, FakeServer } from "./fake-exams-server";

vi.mock(import("@/features/content/api"), async (importOriginal) => {
  const actual = await importOriginal();
  const { getContentReferenceFixture: getContentReference } = await import(
    "@/test/fixtures/content-reference"
  );
  return { ...actual, getContentReference, defaultContentApiDeps: { getContentReference } };
});

const PREVIEW_PATH = `${EXAM_PATH}/preview`;
const PREVIEW_ID = "33333333-3333-3333-3333-333333333333";
const TRIAL_PATH = `${PREVIEW_PATH}/${PREVIEW_ID}/trial`;

function question(number: number): PreviewQuestion {
  return {
    number,
    questionId: `q-${number}`,
    code: `Q${number}`,
    version: 1,
    type: "SINGLE_CHOICE",
    simulated: false,
    content: `<p>Stem ${number}</p>`,
    gradeCode: "GRADE_12",
    difficultyCode: "APPLICATION",
    knowledge: null,
    points: 1,
    options: [
      { id: "a", content: "<p>A</p>", correct: true },
      { id: "b", content: "<p>B</p>", correct: false },
    ],
    answer: null,
    solution: null,
    rubric: null,
    body: null,
    originalNumber: null,
    optionOrder: null,
    originalOptionOrder: null,
    assignedTopicQuotaIds: [],
  };
}

function generatedPreviewBody(overrides: Record<string, unknown> = {}) {
  const block: PreviewBlock = {
    blockId: "b1",
    blockName: "Group 1",
    subjectCode: "MATHEMATICS",
    questionType: "SINGLE_CHOICE",
    manualGrading: false,
    pointsEach: 1,
    firstNumber: 1,
    lastNumber: 1,
    questionCount: 1,
    setCount: null,
    setSize: null,
    requirement: { mode: "COMBINED", cells: [], byGrade: [], byDifficulty: [], byCompetency: [] },
    allocation: [],
    items: [{ kind: "QUESTION", question: question(1) }],
  };
  const paper: PreviewPaper = {
    totalQuestions: 1,
    totalPoints: 1,
    totalMinutes: 60,
    sharedDuration: false,
    hasManualGrading: false,
    subjects: [],
    checks: [],
    knowledgeStats: [],
    parts: [
      {
        partId: "part-1",
        name: "Phần 1",
        order: 1,
        durationMinutes: 60,
        firstNumber: 1,
        lastNumber: 1,
        points: 1,
        maxPoints: null,
        blocks: [block],
      },
    ],
  };
  return {
    examId: EXAM_ID,
    previewId: PREVIEW_ID,
    status: "GENERATED",
    seed: 1,
    generatedAt: new Date().toISOString(),
    expiresAt: new Date(Date.now() + 30 * 60_000).toISOString(),
    path: { pathId: "default", choices: [] },
    paper,
    compliance: [],
    droppedQuestions: [],
    ...overrides,
  };
}

function renderPanel(server: FakeServer) {
  server.install();
  return renderWithProviders(
    <ExamPreviewPanel
      exam={mapExamDetail(examDetailWire())}
      check={null}
      initialPathId={null}
      autoGenerate={false}
      version={null}
      onClearVersion={() => {}}
      onShowCheck={() => {}}
    />,
  );
}

afterEach(() => {
  vi.unstubAllGlobals();
  vi.restoreAllMocks();
});

describe("Nút Làm thử như học viên trong Xem trước (AC-17, AC-34)", () => {
  it("bấm nút gọi POST .../trial một lần, cập nhật expiresAt và mở tab /try với noopener,noreferrer", async () => {
    const user = userEvent.setup();
    const openSpy = vi.fn();
    vi.stubGlobal("open", openSpy);

    const extendedExpiresAt = new Date(Date.now() + 90 * 60_000).toISOString();
    const server = new FakeServer()
      .on("POST", PREVIEW_PATH, { status: 200, body: generatedPreviewBody() })
      .on("POST", TRIAL_PATH, { status: 200, body: { expiresAt: extendedExpiresAt } })
      .hold("POST", TRIAL_PATH);

    renderPanel(server);
    await user.click(await screen.findByRole("button", { name: "Sinh đề xem trước" }));
    const trialBtn = await screen.findByRole("button", { name: "Làm thử như học viên" });
    expect(trialBtn).toBeEnabled();

    await user.click(trialBtn);
    await user.click(trialBtn);
    expect(server.calls("POST", TRIAL_PATH)).toHaveLength(1);

    server.release("POST", TRIAL_PATH);

    await waitFor(() => {
      expect(openSpy).toHaveBeenCalledTimes(1);
    });
    expect(openSpy).toHaveBeenCalledWith(
      `/exams/${EXAM_ID}/preview/${PREVIEW_ID}/try?expiresAt=${encodeURIComponent(extendedExpiresAt)}`,
      "_blank",
      "noopener,noreferrer",
    );
    expect(await screen.findByText(/còn 90 phút/)).toBeInTheDocument();
  });

  it("khi POST .../trial trả 410 PREVIEW_EXPIRED: hiện thông báo hết hạn, không mở tab mới", async () => {
    const user = userEvent.setup();
    const openSpy = vi.fn();
    vi.stubGlobal("open", openSpy);

    const server = new FakeServer()
      .on("POST", PREVIEW_PATH, { status: 200, body: generatedPreviewBody() })
      .on("POST", TRIAL_PATH, {
        status: 410,
        body: { code: "PREVIEW_EXPIRED", message: "Expired" },
      });

    renderPanel(server);
    await user.click(await screen.findByRole("button", { name: "Sinh đề xem trước" }));
    await user.click(await screen.findByRole("button", { name: "Làm thử như học viên" }));

    const alert = await screen.findByRole("alert");
    expect(alert).toHaveTextContent("Đề thử đã hết hạn");
    expect(openSpy).not.toHaveBeenCalled();
  });

  it("khi previewId null hoặc status FAILED: nút Làm thử disabled kèm mô tả Chưa có đề thử để làm qua aria-describedby (AC-17)", async () => {
    const user = userEvent.setup();

    // 1. GENERATED nhưng previewId = null
    const serverNull = new FakeServer().on("POST", PREVIEW_PATH, {
      status: 200,
      body: generatedPreviewBody({ previewId: null }),
    });
    const view1 = renderPanel(serverNull);
    await user.click(await screen.findByRole("button", { name: "Sinh đề xem trước" }));
    const disabledBtn1 = await screen.findByRole("button", { name: "Làm thử như học viên" });
    expect(disabledBtn1).toBeDisabled();
    expect(disabledBtn1).toHaveAccessibleDescription("Chưa có đề thử để làm");
    expect(screen.getByText("Chưa có đề thử để làm")).toBeInTheDocument();
    view1.unmount();

    // 2. status === "FAILED"
    const serverFailed = new FakeServer().on("POST", PREVIEW_PATH, {
      status: 200,
      body: {
        examId: EXAM_ID,
        status: "FAILED",
        reason: "INVALID",
        seed: 1,
        generatedAt: "2026-11-17T01:00:00Z",
        path: { pathId: "default", choices: [] },
        issues: [
          {
            key: "k1",
            code: "SECTION_INCOMPLETE",
            severity: "ERROR",
            group: "STRUCTURE",
            subjectCode: "MATHEMATICS",
            sectionId: "sec-math",
          },
        ],
      },
    });
    renderPanel(serverFailed);
    await user.click(await screen.findByRole("button", { name: "Sinh đề xem trước" }));
    const disabledBtn2 = await screen.findByRole("button", { name: "Làm thử như học viên" });
    expect(disabledBtn2).toBeDisabled();
    expect(disabledBtn2).toHaveAccessibleDescription("Chưa có đề thử để làm");
    expect(screen.getByText("Chưa có đề thử để làm")).toBeInTheDocument();
  });
});
