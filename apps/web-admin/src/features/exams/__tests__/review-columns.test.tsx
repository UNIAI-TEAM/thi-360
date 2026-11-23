import { screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";
import { renderWithProviders } from "@/test/render";
import { mapExamDetail } from "../api-mapping";
import { ExamPreviewAllocation } from "../components/exam-preview-allocation";
import { ExamPreviewCompliance } from "../components/exam-preview-compliance";
import { ExamPreviewPanel } from "../components/exam-preview-panel";
import { ExamReviewCheck } from "../components/exam-review-check";
import type { PreviewBlock, PreviewCompliance, PreviewPaper, PreviewSubject } from "../review-types";
import { checkResultWire, EXAM_ID, EXAM_PATH, examDetailWire, FakeServer } from "./fake-exams-server";

vi.mock(import("@/features/content/api"), async (importOriginal) => {
  const actual = await importOriginal();
  const { getContentReferenceFixture: getContentReference } = await import("@/test/fixtures/content-reference");
  return { ...actual, getContentReference, defaultContentApiDeps: { getContentReference } };
});

/**
 * Cột số của các bảng Kiểm tra và Xem trước (UNI-2760): `Table` của kit luôn căn trái tiêu đề cột, nên cột số
 * (ô căn phải) phải căn phải cả tiêu đề, không thì số nằm lệch bên phải tiêu đề của nó.
 * `text-align` không có tác dụng trên hộp inline, nên phần tử căn phải phải là block (hoặc chính `th`/`td`).
 */
const isRight = (element: Element) =>
  element.classList.contains("text-right") || element.querySelector(".block.text-right") !== null;

function columnIndex(table: HTMLElement, header: string) {
  const headers = within(table).getAllByRole("columnheader");
  const index = headers.findIndex((cell) => cell.textContent === header);
  expect(index, `cột "${header}"`).toBeGreaterThanOrEqual(0);
  return index;
}

/** Tiêu đề và mọi ô dữ liệu của các cột `names` đều căn phải. */
function expectRightAligned(table: HTMLElement, names: string[]) {
  const headers = within(table).getAllByRole("columnheader");
  const rows = within(table).getAllByRole("row").slice(1);
  expect(rows.length).toBeGreaterThan(0);
  for (const name of names) {
    const index = columnIndex(table, name);
    expect(isRight(headers[index]!), `tiêu đề "${name}"`).toBe(true);
    for (const row of rows) {
      expect(isRight(within(row).getAllByRole("cell")[index]!), `ô "${name}"`).toBe(true);
    }
  }
}

function renderCheck(body: Record<string, unknown>) {
  new FakeServer().on("GET", `${EXAM_PATH}/check`, { status: 200, body }).install();
  return renderWithProviders(<ExamReviewCheck exam={mapExamDetail(examDetailWire())} onPreview={() => {}} active />);
}

const paperOf = (over: Partial<PreviewPaper>): PreviewPaper => ({
  totalQuestions: 0,
  totalPoints: 0,
  totalMinutes: 0,
  sharedDuration: false,
  hasManualGrading: false,
  parts: [],
  subjects: [],
  checks: [],
  knowledgeStats: [],
  ...over,
});

describe("AC-1: bảng đối chiếu Yêu cầu / Thực tế / Chênh lệch", () => {
  it("tiêu đề và giá trị của ba cột số đều căn phải", async () => {
    const row = (criterion: string, required: number, actual: number): PreviewCompliance => ({
      criterion,
      scope: "EXAM",
      required,
      actual,
      enforcement: "REQUIRED",
      basis: "QUESTION",
      independent: false,
      note: null,
      originalRequired: null,
      topicName: null,
    });
    renderWithProviders(
      <ExamPreviewCompliance
        paper={paperOf({})}
        compliance={[row("GRADE:GRADE_12", 12, 10), row("GRADE:GRADE_11", 8, 8)]}
        dropped={[]}
      />,
    );

    const table = await screen.findByRole("table", { name: "Yêu cầu và thực tế của đề thử" });
    expectRightAligned(table, ["Yêu cầu", "Thực tế", "Chênh lệch"]);
  });
});

describe("AC-2: chỉ tiêu cấp môn gộp vào bảng đối chiếu ở tab Đối chiếu phân bổ (R-10.3)", () => {
  it("gộp subjects[].quotas vào bảng Yêu cầu và thực tế của đề thử, ba cột số đều căn phải", async () => {
    const subjects: PreviewSubject[] = [
      {
        sectionId: "s1",
        subjectCode: "MATHEMATICS",
        questionCount: 16,
        quotas: [
          { kind: "GRADE", code: "GRADE_12", required: 8, generated: 8 },
          { kind: "DIFFICULTY", code: "APPLICATION", required: 6, generated: 5 },
        ],
      },
    ];
    renderWithProviders(<ExamPreviewAllocation paper={paperOf({ subjects })} />);

    const table = await screen.findByRole("table", { name: "Yêu cầu và thực tế của đề thử" });
    expectRightAligned(table, ["Yêu cầu", "Thực tế", "Chênh lệch"]);
    expect(screen.getByText("1 dòng lệch")).toBeInTheDocument();
  });
});

describe("R-10.3 & AC-16: tab Đề đã sinh chỉ hiện banner 1 dòng khi lệch và nút nhảy sang Đối chiếu phân bổ", () => {
  it("có lệch tiêu chí thì hiện banner 1 dòng thay cho bảng đầy đủ, bấm nút chuyển sang Đối chiếu phân bổ; không lệch thì ẩn banner", async () => {
    const user = userEvent.setup();
    const exam = mapExamDetail(examDetailWire());
    const makeGenerated = (actual: number) => ({
      examId: EXAM_ID,
      status: "GENERATED",
      version: null,
      seed: 1,
      generatedAt: "2026-11-17T01:00:00Z",
      previewId: "55555555-5555-4555-8555-555555555555",
      path: { pathId: "default", choices: [] },
      compliance: [
        {
          criterion: "GRADE:GRADE_12",
          scope: "EXAM",
          required: 10,
          actual,
          enforcement: "REFERENCE",
          basis: "QUESTION",
        },
      ],
      droppedQuestions: [],
      paper: {
        totalQuestions: 10,
        totalPoints: 10,
        totalMinutes: 45,
        sharedDuration: false,
        hasManualGrading: false,
        checks: [],
        knowledgeStats: [],
        subjects: [],
        parts: [],
      },
    });

    let call = 0;
    new FakeServer()
      .on("GET", `${EXAM_PATH}/structure`, { status: 200, body: { examId: EXAM_ID, revision: 1, parts: [] } })
      .on("POST", `${EXAM_PATH}/preview`, () => ({
        status: 200,
        body: makeGenerated(call++ === 0 ? 8 : 10),
      }))
      .install();

    renderWithProviders(
      <ExamPreviewPanel
        exam={exam}
        check={null}
        initialPathId={null}
        autoGenerate={false}
        version={null}
        onClearVersion={() => {}}
        onShowCheck={() => {}}
      />,
    );

    await user.click(await screen.findByRole("button", { name: "Sinh đề xem trước" }));
    expect(await screen.findByText("Đề thử lệch 1 tiêu chí so với cấu hình.")).toBeInTheDocument();
    expect(screen.queryByRole("table", { name: "Yêu cầu và thực tế của đề thử" })).not.toBeInTheDocument();

    await user.click(screen.getByRole("button", { name: "Xem đối chiếu phân bổ" }));
    expect(screen.getByRole("tab", { name: "Đối chiếu phân bổ" })).toHaveAttribute("aria-selected", "true");
    expect(await screen.findByRole("table", { name: "Yêu cầu và thực tế của đề thử" })).toBeInTheDocument();

    await user.click(screen.getByRole("tab", { name: "Đề đã sinh" }));
    await user.click(screen.getByRole("button", { name: "Sinh đề thử mới" }));
    await screen.findByText(/Lần sinh thứ 2/);
    expect(screen.queryByText(/Đề thử lệch .* tiêu chí so với cấu hình/)).not.toBeInTheDocument();
  });
});

describe("AC-3: bảng Chi tiết theo khối", () => {
  it("Số câu yêu cầu, Nguồn phù hợp căn phải ở tiêu đề và ô; giá trị không xuống dòng", async () => {
    const user = userEvent.setup();
    const block = (over: Record<string, unknown>) => ({
      partId: "part-1",
      partName: "Part 1",
      partOrder: 1,
      sectionId: "sec-1",
      subjectCode: "MATH",
      blockName: "Choice",
      questionType: "SINGLE_CHOICE",
      result: "FOUND",
      issues: [],
      ...over,
    });
    renderCheck({
      ...checkResultWire("VALID"),
      groups: [],
      paths: [
        {
          pathId: "default",
          result: "VALID",
          issues: [],
          blocks: [
            block({ blockId: "b1", required: 32, available: 40 }),
            block({ blockId: "b2", required: 3, setCount: 1, setSize: 3, available: 25, availableSets: 4 }),
          ],
        },
      ],
    });

    await user.click(await screen.findByRole("button", { name: "Hiện chi tiết theo khối" }));
    const table = await screen.findByRole("table", { name: "Chi tiết theo khối" });
    expectRightAligned(table, ["Số câu yêu cầu", "Nguồn phù hợp"]);
    for (const name of ["Số câu yêu cầu", "Nguồn phù hợp"]) {
      const index = columnIndex(table, name);
      for (const row of within(table).getAllByRole("row").slice(1)) {
        expect(within(row).getAllByRole("cell")[index]!.querySelector(".whitespace-nowrap")).not.toBeNull();
      }
    }
  });
});

describe("AC-4: bảng Theo mã và phần", () => {
  it("Lỗi, Cảnh báo căn phải ở tiêu đề và ô, cả ô số lớn hơn 0 lẫn dấu –", async () => {
    const base = checkResultWire("INVALID");
    renderCheck({
      ...base,
      issueCount: 1,
      sectionStates: [{ partId: "part-1", sectionId: "sec-1", subjectCode: "MATH", state: "INCOMPLETE" }],
      groups: [
        {
          group: "STRUCTURE",
          status: "FAIL",
          issues: [
            {
              key: "e1",
              code: "SECTION_INCOMPLETE",
              severity: "ERROR",
              group: "STRUCTURE",
              subjectCode: "MATH",
              partId: "part-1",
              sectionId: "sec-1",
              affectedCodes: [],
            },
          ],
        },
      ],
      paths: [{ pathId: "default", result: "INVALID", issues: [] }],
    });

    const table = await screen.findByRole("table", { name: "Theo mã và phần" });
    expectRightAligned(table, ["Lỗi", "Cảnh báo"]);
  });
});

describe("AC-5: ma trận phân bổ của khối", () => {
  it("bố cục cố định, cột đầu giữ bề rộng riêng, các cột số chia đều", async () => {
    const block: PreviewBlock = {
      blockId: "b1",
      blockName: "Group",
      subjectCode: "MATHEMATICS",
      questionType: "SINGLE_CHOICE",
      manualGrading: false,
      pointsEach: 1,
      firstNumber: 1,
      lastNumber: 4,
      questionCount: 4,
      setCount: null,
      setSize: null,
      requirement: {
        mode: "COMBINED",
        cells: [],
        byGrade: [],
        byDifficulty: [],
        byCompetency: [],
      },
      allocation: [
        { gradeCode: "GRADE_12", difficultyCode: "APPLICATION", required: 2, generated: 2 },
        { gradeCode: "GRADE_12", difficultyCode: "RECOGNITION", required: 1, generated: 1 },
        { gradeCode: "GRADE_11", difficultyCode: "APPLICATION", required: 1, generated: 1 },
      ],
      items: [],
    };
    renderWithProviders(<ExamPreviewAllocation paper={paperOf({ parts: [{ partId: "p1", name: "P", order: 1, durationMinutes: 30, firstNumber: 1, lastNumber: 4, points: 4, maxPoints: null, blocks: [block] }] })} />);

    const table = await screen.findByRole("table", { name: /^Đối chiếu phân bổ khối/ });
    expect(table).toHaveClass("table-fixed");
    const headers = within(table).getAllByRole("columnheader");
    const cols = [...table.querySelectorAll("colgroup > col")];
    expect(cols).toHaveLength(headers.length);
    expect(cols[0]!.className).toMatch(/\bw-/);
    // Cột số không đặt bề rộng riêng: bố cục cố định chia đều phần còn lại.
    for (const col of cols.slice(1)) expect(col.className).not.toMatch(/\bw-/);
    // Khung cuộn riêng giữ bảng không bị ép nhỏ ở màn hẹp.
    expect(table.className).toMatch(/\bmin-w-/);
    // Tiêu đề mức độ và tên khối lớp dài (nhất là bản en) xuống dòng trong ô cố định, không tràn sang cột bên cạnh.
    for (const cell of [...headers.slice(1), ...within(table).getAllByRole("rowheader")]) {
      expect(cell, cell.textContent ?? "").toHaveClass("break-words");
      expect(cell).not.toHaveClass("whitespace-nowrap");
    }
  });
});
