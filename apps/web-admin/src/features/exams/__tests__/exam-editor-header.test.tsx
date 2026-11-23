import { screen, waitFor, within } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { ExamEditorLayout } from "../components/exam-editor-layout";
import type { ExamDetail, ExamPart, QuestionBlock } from "../types";
import { ExamsBackend, toWireExam } from "./fake-exams-backend";
import { renderExamsUi } from "./render-exams-ui";

vi.mock(import("@/features/content/api"), async (importOriginal) => {
  const actual = await importOriginal();
  const { getContentReferenceFixture: getContentReference } = await import("@/test/fixtures/content-reference");
  return { ...actual, getContentReference, defaultContentApiDeps: { getContentReference } };
});

vi.mock("next/navigation", () => ({
  useRouter: () => ({ push: vi.fn(), replace: vi.fn() }),
  usePathname: () => window.location.pathname,
}));

const EXAM_ID = "ex-header-001";
const EXAM_PATH = `/api/v1/admin/exams/${EXAM_ID}`;
const STRUCTURE_PATH = `${EXAM_PATH}/structure`;

const makeBlock = (id: string, count: number, pointsPerQuestion: number): QuestionBlock => ({
  id,
  name: `Nhóm ${id}`,
  questionType: "SINGLE_CHOICE",
  questionCount: count,
  setCount: null,
  setSize: null,
  requiredPointsPerQuestion: pointsPerQuestion,
  knowledgeScope: null,
  tagCodes: [],
  matrix: null,
  childAnswerType: null,
  templateGroupCode: null,
  answerTypeLocked: false,
});

const makePart = (overrides: Partial<ExamPart> & { id: string; order: number; name: string }): ExamPart => ({
  durationMinutes: 60,
  questionCount: 50,
  questionCountOnPaper: 50,
  questionCountRequired: 50,
  participation: { mode: "ALL", choose: null },
  mode: "FIXED",
  sections: [
    {
      id: `sec-${overrides.order}`,
      subjectCode: "MATH",
      questionCount: 50,
      knowledgeScope: null,
      answerQuotas: null,
      matrix: null,
      choiceGroup: null,
      blocks: [makeBlock(`blk-${overrides.order}`, 50, 1)],
    },
  ],
  options: [],
  ...overrides,
});

let backend: ExamsBackend;

beforeEach(() => {
  backend = new ExamsBackend();
  backend.install();
});

describe("ExamEditorLayout & ExamEditorHeader (AC-01, AC-02)", () => {
  it("AC-01: chế độ tạo mới (!examId) hiện link quay lại /exams, thanh 5 tab với Thông tin chung đang chọn và 4 tab sau khóa tĩnh không bọc thẻ <a>", () => {
    window.history.replaceState(null, "", "/exams/new");
    renderExamsUi(
      <ExamEditorLayout>
        <div data-testid="create-child">Nội dung tạo bài</div>
      </ExamEditorLayout>,
    );

    expect(screen.getByRole("banner", { name: "Tạo bài thi" })).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Danh sách bài thi" })).toHaveAttribute("href", "/exams");
    expect(screen.getByRole("heading", { name: "Tạo bài thi", level: 1 })).toBeInTheDocument();
    expect(screen.getByTestId("create-child")).toBeInTheDocument();

    const nav = screen.getByRole("navigation", { name: "Các phần của bài thi" });
    const generalLink = within(nav).getByRole("link", { name: "Thông tin chung" });
    expect(generalLink).toHaveAttribute("aria-current", "page");
    expect(generalLink).toHaveAttribute("href", "/exams/new");

    for (const label of ["Cấu trúc và ma trận", "Thông tin bán", "Kiểm tra và xem trước", "Phiên bản và công bố"]) {
      const tab = within(nav).getByText(label);
      expect(tab).toHaveAttribute("aria-disabled", "true");
      expect(tab.closest("a")).toBeNull();
    }
  });

  it("AC-02: dùng useKindLabel (V-ACT), ẩn thời lượng khi durationMinutes <= 0 và hiện hậu tố số câu trên đề khi printedQuestions khác totalQuestions", async () => {
    const seeded: ExamDetail = {
      ...backend.app("ex-hsa-f-001"),
      id: EXAM_ID,
      code: "VACT-PART-001",
      name: "V-ACT phần lẻ chưa nhập thời lượng",
      kind: "VACT",
      scope: "PART",
      partCode: "LANGUAGE",
      timing: "SHARED",
      durationMinutes: 0,
      maxScore: null,
      parts: [
        makePart({
          id: "part-1",
          order: 1,
          name: "Sử dụng ngôn ngữ",
          durationMinutes: null,
          questionCount: 40,
          questionCountOnPaper: 50,
          questionCountRequired: 40,
          sections: [
            {
              id: "sec-1",
              subjectCode: "VIETNAMESE",
              questionCount: 40,
              knowledgeScope: null,
              answerQuotas: null,
              matrix: null,
              choiceGroup: null,
              blocks: [makeBlock("blk-1", 40, 10)],
            },
          ],
        }),
      ],
    };
    backend.seedExam(seeded);

    window.history.replaceState(null, "", `/exams/${EXAM_ID}`);
    renderExamsUi(
      <ExamEditorLayout examId={EXAM_ID}>
        <div>Chi tiết</div>
      </ExamEditorLayout>,
    );

    const summary = await screen.findByRole("list", { hidden: true }).catch(() => null);
    void summary;
    const dl = await screen.findByLabelText("Tổng hợp cấu trúc");
    expect(within(dl).getByText("V-ACT")).toBeInTheDocument();
    expect(within(dl).queryByText("exams.kind.VACT")).toBeNull();
    expect(within(dl).queryByText("Thời lượng")).toBeNull();
    await waitFor(() => expect(within(dl).getByText("40 (50 câu trên đề)")).toBeInTheDocument());
  });

  it("AC-02 (S2-1): hiện cảnh báo lệch thang điểm trên bài STANDARD nhưng ẩn cảnh báo trên bài REVIEW", async () => {
    const standardMismatch: ExamDetail = {
      ...backend.app("ex-hsa-f-001"),
      id: EXAM_ID,
      code: "HSA-MISMATCH-01",
      name: "HSA lệch thang điểm",
      kind: "HSA",
      scope: "SUBJECT",
      subjectCode: "MATH",
      buildMode: "STANDARD",
      maxScore: 100,
      parts: [makePart({ id: "part-1", order: 1, name: "Toán", questionCount: 50 })], // 50 * 1 = 50 != 100
    };
    backend.seedExam(standardMismatch);

    window.history.replaceState(null, "", `/exams/${EXAM_ID}`);
    const first = renderExamsUi(
      <ExamEditorLayout examId={EXAM_ID}>
        <div>Tab 1</div>
      </ExamEditorLayout>,
    );

    expect(await screen.findByText("Tổng điểm phần (50) khác thang điểm (100)")).toBeInTheDocument();
    first.unmount();

    const reviewExam: ExamDetail = {
      ...standardMismatch,
      id: "ex-review-01",
      code: "HSA-REVIEW-01",
      buildMode: "REVIEW",
    };
    backend.seedExam(reviewExam);
    window.history.replaceState(null, "", "/exams/ex-review-01");
    renderExamsUi(
      <ExamEditorLayout examId="ex-review-01">
        <div>Tab 1</div>
      </ExamEditorLayout>,
    );

    await screen.findByLabelText("Tổng hợp cấu trúc");
    expect(screen.queryByText(/Tổng điểm phần/)).toBeNull();
  });

  it("AC-02 (P1-5): bài QDA/BCA có codeSource không nháy cảnh báo lệch điểm giả trước khi GET /structure (combinations) tải xong", async () => {
    const qda = backend.app("ex-qda-f-001");
    backend.server.on("GET", `/api/v1/admin/exams/${qda.id}`, () => ({ status: 200, body: toWireExam(qda) }));
    backend.server.hold("GET", `/api/v1/admin/exams/${qda.id}/structure`);

    window.history.replaceState(null, "", `/exams/${qda.id}`);
    renderExamsUi(
      <ExamEditorLayout examId={qda.id}>
        <div>Tab 1</div>
      </ExamEditorLayout>,
    );

    await screen.findByLabelText("Tổng hợp cấu trúc");
    // Trước khi combinations tải về: không được nháy cảnh báo lệch điểm giả từ môn chung.
    expect(screen.queryByText(/Tổng điểm phần/)).toBeNull();

    backend.server.release("GET", `/api/v1/admin/exams/${qda.id}/structure`);
    await waitFor(() => expect(backend.calls("GET", STRUCTURE_PATH.replace(EXAM_ID, qda.id))).toHaveLength(1));
    expect(screen.queryByText(/Tổng điểm phần/)).toBeNull();
  });

  it("SF-4: hiện thời lượng từ structure.parts khi exam.durationMinutes = 0 nhưng cấu trúc bản làm việc có tổng phút > 0", async () => {
    const baseExam: ExamDetail = {
      ...backend.app("ex-hsa-f-001"),
      id: EXAM_ID,
      code: "HSA-SF4-001",
      timing: "PER_PART",
      durationMinutes: 0,
      parts: [makePart({ id: "part-1", order: 1, name: "Toán", durationMinutes: null, questionCount: 50 })],
    };
    const { FakeServer } = await import("./fake-exams-server");
    new FakeServer()
      .on("GET", EXAM_PATH, { status: 200, body: toWireExam(baseExam) })
      .on("GET", STRUCTURE_PATH, {
        status: 200,
        body: {
          examId: EXAM_ID,
          revision: baseExam.revision,
          combinations: [],
          parts: [makePart({ id: "part-1", order: 1, name: "Toán", durationMinutes: 45, questionCount: 50 })],
          examQuotas: [],
        },
      })
      .install();

    window.history.replaceState(null, "", `/exams/${EXAM_ID}/structure`);
    renderExamsUi(
      <ExamEditorLayout examId={EXAM_ID}>
        <div>Tab 2</div>
      </ExamEditorLayout>,
    );

    const dl = await screen.findByLabelText("Tổng hợp cấu trúc");
    expect(await within(dl).findByText("45 phút")).toBeInTheDocument();
  });
});
