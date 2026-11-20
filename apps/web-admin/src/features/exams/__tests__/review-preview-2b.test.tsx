import { screen, waitFor, within } from "@testing-library/react";
import userEvent, { type UserEvent } from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";
import en from "@/messages/en.json";
import { renderWithProviders } from "@/test/render";
import { mapExamDetail } from "../api-mapping";
import { ExamPreviewCompliance } from "../components/exam-preview-compliance";
import { ExamPreviewPanel } from "../components/exam-preview-panel";
import { ExamPreviewPaper } from "../components/exam-preview-paper";
import { competencyName, complianceLevel, droppedCount, examQuotaNames, parseCriterion, topicAssignments } from "../review-compliance";
import { changedPartIds, examNeedsPathChoice, listPreviewSections } from "../review-scope";
import type {
  ExamCheckResult,
  PreviewBlock,
  PreviewCompliance,
  PreviewItem,
  PreviewPaper,
  PreviewQuestion,
} from "../review-types";
import { EXAM_ID, EXAM_PATH, examDetailWire, FakeServer, type Recorded } from "./fake-exams-server";
import { byTextContent } from "./text-content";

vi.mock(import("@/features/content/api"), async (importOriginal) => {
  const actual = await importOriginal();
  const { getContentReferenceFixture: getContentReference } = await import("@/test/fixtures/content-reference");
  return { ...actual, getContentReference, defaultContentApiDeps: { getContentReference } };
});

const PREVIEW_PATH = `${EXAM_PATH}/preview`;
const PREVIEW_ID = "33333333-3333-3333-3333-333333333333";

/* ---------- Dữ liệu ---------- */

function question(number: number, over: Partial<PreviewQuestion> = {}): PreviewQuestion {
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
      { id: "a", content: "<p>Option A text</p>", correct: true },
      { id: "b", content: "<p>Option B text</p>", correct: false },
    ],
    answer: null,
    solution: null,
    rubric: null,
    body: null,
    originalNumber: null,
    optionOrder: null,
    originalOptionOrder: null,
    assignedTopicQuotaIds: [],
    ...over,
  };
}

function block(over: Partial<PreviewBlock> & { items: PreviewItem[] }): PreviewBlock {
  return {
    blockId: "b1",
    blockName: "Group",
    subjectCode: "MATHEMATICS",
    questionType: "SINGLE_CHOICE",
    manualGrading: false,
    pointsEach: 1,
    firstNumber: 1,
    lastNumber: over.items.length,
    questionCount: over.items.length,
    setCount: null,
    setSize: null,
    requirement: { mode: "COMBINED", cells: [], byGrade: [], byDifficulty: [], byCompetency: [] },
    allocation: [],
    ...over,
  };
}

function paperOf(parts: Array<{ id: string; name: string; first: number; last: number; blocks: PreviewBlock[] }>): PreviewPaper {
  return {
    totalQuestions: Math.max(...parts.map((part) => part.last)),
    totalPoints: 10,
    totalMinutes: 60,
    sharedDuration: false,
    hasManualGrading: false,
    subjects: [],
    checks: [],
    knowledgeStats: [],
    parts: parts.map((part, index) => ({
      partId: part.id,
      name: part.name,
      order: index + 1,
      durationMinutes: 30,
      firstNumber: part.first,
      lastNumber: part.last,
      points: 1,
      maxPoints: null,
      blocks: part.blocks,
    })),
  };
}

const item = (value: PreviewQuestion, originalOrder: number | null = null): PreviewItem => ({
  kind: "QUESTION",
  question: value,
  originalOrder,
});

/* ---------- Bảng Yêu cầu / Thực tế ---------- */

const row = (over: Partial<PreviewCompliance>): PreviewCompliance => ({
  criterion: "GRADE:GRADE_12",
  scope: "EXAM",
  required: 5,
  actual: 5,
  enforcement: "REQUIRED",
  basis: "QUESTION",
  independent: false,
  note: null,
  originalRequired: null,
  topicName: null,
  ...over,
});

describe("mức lệch của dòng Yêu cầu / Thực tế", () => {
  it("Bắt buộc lệch là lỗi, Tham khảo lệch là cảnh báo, bằng nhau là đạt", () => {
    expect(complianceLevel(row({}))).toBe("OK");
    expect(complianceLevel(row({ actual: 4 }))).toBe("REQUIRED_DEVIATION");
    expect(complianceLevel(row({ actual: 6 }))).toBe("REQUIRED_DEVIATION");
    expect(complianceLevel(row({ actual: 4, enforcement: "REFERENCE" }))).toBe("REFERENCE_DEVIATION");
  });

  it("số thập phân bằng nhau vẫn đạt; chênh thật thì lệch", () => {
    expect(complianceLevel(row({ required: 2.5, actual: 2.5, basis: "POINTS" }))).toBe("OK");
    expect(complianceLevel(row({ required: 2.5, actual: 2.25, basis: "POINTS" }))).toBe("REQUIRED_DEVIATION");
  });

  it("chỉ tiêu chủ đề là 'ít nhất': nhiều hơn vẫn đạt, thiếu mới lệch", () => {
    expect(complianceLevel(row({ criterion: "TOPIC:t1", required: 2, actual: 3 }))).toBe("OK");
    expect(complianceLevel(row({ criterion: "TOPIC:t1", required: 2, actual: 1 }))).toBe("REQUIRED_DEVIATION");
  });

  it("đọc tiêu chí, ghi chú bỏ câu", () => {
    expect(parseCriterion("CELL:G12:VD")).toEqual({ kind: "CELL", gradeCode: "G12", difficultyCode: "VD" });
    expect(parseCriterion("EXAM:q1:G10,G11")).toEqual({ kind: "EXAM", quotaId: "q1", codes: ["G10", "G11"] });
    expect(droppedCount("dropped 1")).toBe(1);
    expect(droppedCount(null)).toBe(0);
  });
});

describe("bảng Yêu cầu / Thực tế", () => {
  const paper = paperOf([
    {
      id: "p1",
      name: "Part 1",
      first: 1,
      last: 3,
      blocks: [
        block({
          items: [
            item(question(1, { assignedTopicQuotaIds: ["t1"] })),
            item(question(2)),
            item(question(3, { assignedTopicQuotaIds: ["t1"] })),
          ],
        }),
      ],
    },
  ]);

  function renderTable(rows: PreviewCompliance[], dropped: PreviewQuestion[] = [], locale?: "en") {
    return renderWithProviders(
      <ExamPreviewCompliance paper={paper} compliance={rows} dropped={dropped} />,
      locale ? { locale, messages: en } : undefined,
    );
  }

  it("dòng lệch được đánh dấu theo mức: Bắt buộc là lỗi, Tham khảo là cảnh báo, đạt giữ nguyên", async () => {
    renderTable([
      row({ criterion: "GRADE:GRADE_12", required: 5, actual: 5 }),
      row({ criterion: "DIFFICULTY:APPLICATION", required: 3, actual: 1 }),
      row({ criterion: "DIFFICULTY:RECOGNITION", required: 3, actual: 5, enforcement: "REFERENCE" }),
    ]);

    const table = await screen.findByRole("table", { name: "Yêu cầu và thực tế của đề thử" });
    const rows = within(table).getAllByRole("row").slice(1);
    expect(within(rows[0]!).getByText("Đạt")).toBeInTheDocument();
    expect(within(rows[1]!).getByText("Lệch · bắt buộc")).toBeInTheDocument();
    expect(within(rows[2]!).getByText("Lệch · tham khảo")).toBeInTheDocument();
    expect(within(rows[2]!).getByText("Tham khảo · câu")).toBeInTheDocument();
    expect(screen.getByText("2 dòng lệch")).toBeInTheDocument();
  });

  it("chip tổng đầu bảng: Bắt buộc lệch là lỗi (danger), chỉ Tham khảo lệch là cảnh báo (warning)", async () => {
    renderTable([
      row({ criterion: "GRADE:GRADE_12", required: 5, actual: 4 }),
      row({ criterion: "DIFFICULTY:APPLICATION", required: 3, actual: 1, enforcement: "REFERENCE" }),
    ]);
    renderTable([row({ criterion: "DIFFICULTY:RECOGNITION", required: 3, actual: 5, enforcement: "REFERENCE" })]);

    expect((await screen.findByText("2 dòng lệch")).closest("[data-tone]")).toHaveAttribute("data-tone", "danger");
    expect(screen.getByText("1 dòng lệch").closest("[data-tone]")).toHaveAttribute("data-tone", "warning");
  });

  it("chỉ tiêu chủ đề nêu các câu được gán; thực tế nhiều hơn 'ít nhất' vẫn đạt", async () => {
    renderTable([row({ criterion: "TOPIC:t1", scope: "SECTION:s1", required: 1, actual: 2 })]);

    const table = await screen.findByRole("table");
    const only = within(table).getAllByRole("row")[1]!;
    expect(within(only).getByText("2 · gán câu 1, 3")).toBeInTheDocument();
    expect(within(only).getByText("Đạt")).toBeInTheDocument();
    expect(topicAssignments(paper).get("t1")).toEqual([1, 3]);
  });

  it("xem thứ tự gốc: chỉ tiêu chủ đề nêu số gốc của câu được gán, khớp số trên đề đang xem", async () => {
    const shuffled = paperOf([
      {
        id: "p1",
        name: "Part 1",
        first: 1,
        last: 3,
        blocks: [
          block({
            items: [
              item(question(1, { assignedTopicQuotaIds: ["t1"], originalNumber: 3 })),
              item(question(2, { originalNumber: 1 })),
              item(question(3, { assignedTopicQuotaIds: ["t1"], originalNumber: 2 })),
            ],
          }),
        ],
      },
    ]);
    const rows = [row({ criterion: "TOPIC:t1", scope: "SECTION:s1", required: 1, actual: 2 })];

    expect(topicAssignments(shuffled).get("t1")).toEqual([1, 3]);
    expect(topicAssignments(shuffled, true).get("t1")).toEqual([2, 3]);

    const { unmount } = renderWithProviders(<ExamPreviewCompliance paper={shuffled} compliance={rows} dropped={[]} />);
    expect(await screen.findByText("2 · gán câu 1, 3")).toBeInTheDocument();
    unmount();
    renderWithProviders(<ExamPreviewCompliance paper={shuffled} compliance={rows} dropped={[]} original />);
    expect(await screen.findByText("2 · gán câu 2, 3")).toBeInTheDocument();
  });

  it("chỉ tiêu cấp bài: mã là khối lớp hay độ khó theo danh mục, không theo chữ đầu của mã", () => {
    const reference = {
      grades: [{ code: "K12", name: "Lớp 12" }, { code: "GRADE_10", name: "Lớp 10" }],
      difficulties: [{ code: "GOOD", name: "Khá" }, { code: "HIGH", name: "Cao" }],
    };

    // GOOD bắt đầu bằng G nhưng là độ khó; K12 không bắt đầu bằng G nhưng là khối lớp.
    expect(examQuotaNames(["K12", "GOOD", "GRADE_10", "HIGH"], reference)).toEqual(["Lớp 12", "Khá", "Lớp 10", "Cao"]);
    expect(examQuotaNames(["UNKNOWN"], reference)).toEqual(["UNKNOWN"]);
    expect(examQuotaNames(["K12"], undefined)).toEqual(["K12"]);
  });

  it("dòng chỉ tiêu cấp bài và năng lực hiện tên theo danh mục", async () => {
    expect(competencyName("VACT-TOAN", { competencies: [{ code: "VACT-TOAN", name: "Toán học", subjectCode: null, examKindCodes: [] }] })).toBe("Toán học");
    expect(competencyName("NO-SUCH", undefined)).toBe("NO-SUCH");

    renderTable([
      row({ criterion: "EXAM:q1:GRADE_10,HIGH_APPLICATION", required: 2, actual: 2 }),
      row({ criterion: "COMPETENCY:VACT-TOAN", required: 2, actual: 2 }),
    ]);

    expect(await screen.findByText("Chỉ tiêu cấp bài Lớp 10 + Vận dụng cao")).toBeInTheDocument();
    expect(await screen.findByText("Năng lực Toán học")).toBeInTheDocument();
  });

  it("môn 16 câu: ghi 'môn đã bỏ 1 câu' ở cột Yêu cầu và nêu câu đã bỏ", async () => {
    renderTable(
      [row({ criterion: "GRADE:GRADE_11", required: 5, actual: 5, note: "dropped 1" })],
      [question(9, { code: "PHY-9", gradeCode: "GRADE_11", difficultyCode: "UNDERSTANDING" })],
    );

    expect(await screen.findByText("5 (môn đã bỏ 1 câu)")).toBeInTheDocument();
    expect(screen.getByText(/Đã bỏ câu PHY-9 · Một đáp án, /)).toBeInTheDocument();
  });

  it("bản en", async () => {
    renderTable([row({ criterion: "DIFFICULTY:APPLICATION", required: 3, actual: 1 })], [], "en");

    expect(await screen.findByText("Deviates · required")).toBeInTheDocument();
    expect(screen.getByText("Required · questions")).toBeInTheDocument();
  });
});

/* ---------- Bài đọc điền và thứ tự gốc ---------- */

describe("bài đọc điền: ngữ liệu và vị trí câu con", () => {
  it("chỗ trống mang số của câu con đầu và mỗi câu con nêu chỗ trống của nó", async () => {
    const passage =
      '<p>Many students <span class="blank" data-blank="1"></span> a routine and <span class="blank" data-blank="2"></span> goals.</p>';
    const paper = paperOf([
      {
        id: "p1",
        name: "Part 1",
        first: 31,
        last: 32,
        blocks: [
          block({
            blockName: "Cloze",
            questionType: "CLOZE",
            items: [
              {
                kind: "SET",
                setId: "s1",
                code: "CL-1",
                version: 1,
                simulated: false,
                passage,
                children: [question(31), question(32)],
              },
            ],
          }),
        ],
      },
    ]);

    const { container } = renderWithProviders(<ExamPreviewPaper paper={paper} />);

    await waitFor(() =>
      expect([...container.querySelectorAll(".blank")].map((node) => node.textContent)).toEqual(["(31) ____", "(32) ____"]),
    );
    expect(screen.getByText("Câu 31 → chỗ trống (31) · Một đáp án")).toBeInTheDocument();
    expect(screen.getByText("Câu 32 → chỗ trống (32) · Một đáp án")).toBeInTheDocument();
  });
});

describe("thứ tự gốc (K12)", () => {
  const shuffled = paperOf([
    {
      id: "p1",
      name: "Part 1",
      first: 1,
      last: 2,
      blocks: [
        block({
          items: [
            item(
              question(1, {
                questionId: "q-b",
                code: "QB",
                originalNumber: 2,
                options: [
                  { id: "b", content: "<p>Second authored</p>", correct: false },
                  { id: "a", content: "<p>First authored</p>", correct: true },
                ],
                optionOrder: ["b", "a"],
                originalOptionOrder: ["a", "b"],
              }),
              2,
            ),
            item(question(2, { questionId: "q-a", code: "QA", originalNumber: 1 }), 1),
          ],
        }),
      ],
    },
  ]);

  const numbersOnPage = () => screen.getAllByRole("heading", { name: /^Câu \d+$/ }).map((node) => node.textContent);
  const optionsOf = (heading: string) => {
    const article = screen.getByRole("heading", { name: heading }).closest("article")!;
    return within(article).getAllByRole("listitem").map((node) => node.textContent?.replace(/^[A-H]/, "").replace(/Đáp án đúng$/, ""));
  };

  it("mặc định đúng thứ tự đã đảo: số câu, câu và phương án như đề thật", () => {
    renderWithProviders(<ExamPreviewPaper paper={shuffled} />);
    expect(numbersOnPage()).toEqual(["Câu 1", "Câu 2"]);
    expect(screen.getAllByText(/^Q[AB] · v1$/).map((node) => node.textContent)).toEqual(["QB · v1", "QA · v1"]);
    expect(optionsOf("Câu 1")).toEqual(["Second authored", "First authored"]);
    expect(screen.queryByText(/Đang xem thứ tự gốc/)).not.toBeInTheDocument();
  });

  it("original: câu theo vị trí gốc (QA trước QB), số câu là số gốc, phương án theo thứ tự soạn", async () => {
    renderWithProviders(<ExamPreviewPaper paper={shuffled} original />);

    expect(screen.getByText(/Đang xem thứ tự gốc/)).toBeInTheDocument();
    // Mục có originalOrder 1 (QA, số gốc 1) đứng trước mục có originalOrder 2 (QB, số gốc 2).
    expect(numbersOnPage()).toEqual(["Câu 1", "Câu 2"]);
    const codes = screen.getAllByText(/^Q[AB] · v1$/).map((node) => node.textContent);
    expect(codes).toEqual(["QA · v1", "QB · v1"]);
    expect(optionsOf("Câu 2")).toEqual(["First authored", "Second authored"]);
  });
});

/* ---------- Panel: phạm vi, đổi mã, hết hạn ---------- */

const HSA_PARTS = [
  {
    id: "part-math",
    order: 1,
    name: "Math",
    durationMinutes: 60,
    questionCount: 50,
    participation: { mode: "ALL" },
    sections: [
      { id: "sec-math", subjectCode: "MATHEMATICS", questionCount: 50, blocks: [{ id: "blk-math", name: "Group", questionType: "SINGLE_CHOICE", questionCount: 50 }] },
    ],
  },
  {
    id: "part-sci",
    order: 2,
    name: "Science",
    durationMinutes: 60,
    questionCount: 50,
    participation: { mode: "CHOICE", choose: 1 },
    options: [
      {
        id: "opt-sci",
        kind: "SUBJECT_CHOICE",
        code: "SCIENCE",
        name: "Science",
        chooseSubjects: 3,
        countsByPick: [17, 17, 16],
        sections: [
          { id: "sec-phy", subjectCode: "PHYSICS", questionCount: null, choiceGroup: "NATURAL", blocks: [] },
          { id: "sec-chem", subjectCode: "CHEMISTRY", questionCount: null, choiceGroup: "NATURAL", blocks: [] },
          { id: "sec-bio", subjectCode: "BIOLOGY", questionCount: null, choiceGroup: "NATURAL", blocks: [] },
        ],
      },
    ],
  },
];

function hsaExam() {
  return mapExamDetail(examDetailWire({ parts: HSA_PARTS as never }));
}

function checkOf(paths: Array<{ pathId: string; choices: Array<{ partId: string; optionId: string; kind: string; code: string; name: string }> }>): ExamCheckResult {
  return {
    examId: EXAM_ID,
    status: "VALID",
    checkedAt: "2026-11-17T01:00:00Z",
    durationSeconds: 1,
    timeLimitSeconds: 30,
    stale: false,
    invalidReason: null,
    issueCount: 0,
    partCount: 2,
    totalPoints: 0,
    printedQuestions: 0,
    maxScore: null,
    groups: [],
    paths: paths.map((path) => ({
      ...path,
      result: "VALID",
      partIds: [],
      totalQuestions: 0,
      totalMinutes: 0,
      totalPoints: 0,
      blocks: [],
      subjects: [],
      selections: [],
      issues: [],
    })),
  } as unknown as ExamCheckResult;
}

const generatedWire = (over: Record<string, unknown> = {}) => ({
  examId: EXAM_ID,
  status: "GENERATED",
  version: null,
  seed: 1,
  generatedAt: "2026-11-17T01:00:00Z",
  previewId: PREVIEW_ID,
  path: { pathId: "default", choices: [] },
  compliance: [],
  droppedQuestions: [],
  paper: {
    totalQuestions: 3,
    totalPoints: 3,
    totalMinutes: 60,
    sharedDuration: false,
    hasManualGrading: false,
    checks: [],
    knowledgeStats: [],
    subjects: [],
    parts: [
      {
        partId: "part-math",
        name: "Math",
        order: 1,
        firstNumber: 1,
        lastNumber: 3,
        points: 3,
        blocks: [
          {
            blockId: "blk-math",
            blockName: "Group",
            subjectCode: "MATHEMATICS",
            questionType: "SINGLE_CHOICE",
            firstNumber: 1,
            lastNumber: 3,
            questionCount: 3,
            items: [3, 1, 2].map((original, index) => ({
              kind: "QUESTION",
              originalOrder: original,
              question: {
                number: index + 1,
                originalNumber: original,
                questionId: `qq-${original}`,
                code: `MQ${original}`,
                version: 1,
                type: "SINGLE_CHOICE",
                content: `<p>Stem ${original}</p>`,
                gradeCode: "GRADE_12",
                difficultyCode: "APPLICATION",
                points: 1,
                options: [{ id: "x", content: "<p>Opt</p>", correct: true }],
              },
            })),
          },
        ],
      },
    ],
  },
  ...over,
});

function bodyOf(call: Recorded | undefined) {
  return (call?.body ?? {}) as Record<string, unknown>;
}

function renderPanel(server: FakeServer, exam = hsaExam(), check: ExamCheckResult | null = null, initialPathId: string | null = null) {
  server.install();
  return renderWithProviders(
    <ExamPreviewPanel
      exam={exam}
      check={check}
      initialPathId={initialPathId}
      autoGenerate={false}
      version={null}
      onClearVersion={() => {}}
      onShowCheck={() => {}}
    />,
  );
}

async function pick(user: UserEvent, label: RegExp | string, option: RegExp | string) {
  await user.click(await screen.findByRole("combobox", { name: label }));
  await user.click(await screen.findByRole("option", { name: option }));
}

describe("phạm vi sinh thử", () => {
  it("bài có phần chọn: Cả bài khóa kèm lời nhắc, mặc định sinh theo một đường", async () => {
    const user = userEvent.setup();
    const server = new FakeServer().on("POST", PREVIEW_PATH, { status: 200, body: generatedWire() });
    renderPanel(server, hsaExam(), checkOf([{ pathId: "opt-sci", choices: [{ partId: "part-sci", optionId: "opt-sci", kind: "SUBJECT_CHOICE", code: "SCIENCE", name: "Science" }] }]));

    expect(await screen.findByText("Bài có phần chọn mã: hãy chọn một mã.")).toBeInTheDocument();
    const scope = screen.getByRole("group", { name: "Phạm vi sinh thử" });
    expect(within(scope).getAllByRole("radio").map((radio) => (radio as HTMLInputElement).labels?.[0]?.textContent)).toEqual(["Một môn", "Một mã hoặc nhánh", "Cả bài"]);
    expect(within(scope).getByRole("radio", { name: "Cả bài" })).toBeDisabled();
    expect(within(scope).getByRole("radio", { name: "Một mã hoặc nhánh" })).toBeChecked();
    await user.click(screen.getByRole("button", { name: "Sinh đề xem trước" }));

    await waitFor(() => expect(server.calls("POST", PREVIEW_PATH)).toHaveLength(1));
    expect(bodyOf(server.calls("POST", PREVIEW_PATH)[0]).target).toEqual({
      kind: "PATH",
      pathId: "opt-sci",
      subjectCodes: ["PHYSICS", "CHEMISTRY", "BIOLOGY"],
    });
  });

  it("một môn của HSA Khoa học: chọn 16 câu gửi sectionQuestionCount 16, mặc định 17", async () => {
    const user = userEvent.setup();
    const server = new FakeServer().on("POST", PREVIEW_PATH, { status: 200, body: generatedWire() });
    renderPanel(server);

    await user.click(screen.getByRole("radio", { name: "Một môn" }));
    await pick(user, "Môn", /Vật lí/);
    await user.click(screen.getByRole("button", { name: "Sinh đề xem trước" }));
    await waitFor(() => expect(server.calls("POST", PREVIEW_PATH)).toHaveLength(1));
    expect(bodyOf(server.calls("POST", PREVIEW_PATH)[0]).target).toEqual({
      kind: "SECTION",
      sectionId: "sec-phy",
      sectionQuestionCount: 17,
    });

    await pick(user, "Số câu của môn", "16 câu (bỏ 1 câu)");
    await user.click(await screen.findByRole("button", { name: "Sinh đề thử mới" }));
    await waitFor(() => expect(server.calls("POST", PREVIEW_PATH)).toHaveLength(2));
    expect(bodyOf(server.calls("POST", PREVIEW_PATH)[1]).target).toEqual({
      kind: "SECTION",
      sectionId: "sec-phy",
      sectionQuestionCount: 16,
    });
  });

  it("bài không có lựa chọn: sinh cả bài (target EXAM)", async () => {
    const user = userEvent.setup();
    const server = new FakeServer().on("POST", PREVIEW_PATH, { status: 200, body: generatedWire() });
    renderPanel(server, mapExamDetail(examDetailWire()));

    await user.click(await screen.findByRole("button", { name: "Sinh đề xem trước" }));

    await waitFor(() => expect(server.calls("POST", PREVIEW_PATH)).toHaveLength(1));
    expect(bodyOf(server.calls("POST", PREVIEW_PATH)[0]).target).toEqual({ kind: "EXAM" });
    expect(screen.queryByText("Bài có phần chọn mã: hãy chọn một mã.")).not.toBeInTheDocument();
  });

  it("listPreviewSections, examNeedsPathChoice, changedPartIds", () => {
    const parts = hsaExam().parts;
    const sections = listPreviewSections(parts);
    expect(sections.map((value) => [value.sectionId, value.counts])).toEqual([
      ["sec-math", null],
      ["sec-phy", [17, 16]],
      ["sec-chem", [17, 16]],
      ["sec-bio", [17, 16]],
    ]);
    expect(examNeedsPathChoice(parts)).toBe(true);
    expect(examNeedsPathChoice(parts.slice(0, 1))).toBe(false);
    const from = { pathId: "a", choices: [{ partId: "p1", optionId: "o1" }, { partId: "p3", optionId: "ca1" }] };
    const to = { pathId: "b", choices: [{ partId: "p1", optionId: "o1" }, { partId: "p3", optionId: "ca2" }] };
    expect(changedPartIds(from as never, to as never)).toEqual(["p3"]);
  });
});

/** BCA: Phần 1, 2 cố định, Phần 3 chọn mã CA1–CA4; đổi mã giữ Phần 1–2. */
const BCA_PARTS = [
  { id: "p1", order: 1, name: "Part 1", durationMinutes: 60, questionCount: 40, participation: { mode: "ALL" }, sections: [] },
  { id: "p2", order: 2, name: "Part 2", durationMinutes: 60, questionCount: 25, participation: { mode: "ALL" }, sections: [] },
  {
    id: "p3",
    order: 3,
    name: "Part 3",
    durationMinutes: 60,
    questionCount: 15,
    participation: { mode: "CHOICE", choose: 1 },
    codeSource: { source: "GLOBAL_LIST", branch: null },
    options: [],
    sections: [],
  },
];

const bcaPart = (id: string, name: string, first: number, last: number) => ({
  partId: id,
  name,
  order: 1,
  firstNumber: first,
  lastNumber: last,
  points: 1,
  blocks: [],
});

const bcaWire = (pathId: string, over: Record<string, unknown> = {}) => {
  const base = generatedWire({
    path: { pathId, choices: [{ partId: "p3", optionId: pathId, kind: "COMBINATION", code: pathId.toUpperCase(), name: pathId.toUpperCase() }] },
    ...over,
  });
  return { ...base, paper: { ...(base.paper as object), totalQuestions: 80, parts: [bcaPart("p1", "Part 1", 1, 40), bcaPart("p2", "Part 2", 41, 65), bcaPart("p3", "Part 3", 66, 80)] } };
};

const BCA_CHECK = checkOf(
  ["ca1", "ca2"].map((code) => ({
    pathId: code,
    choices: [{ partId: "p3", optionId: code, kind: "COMBINATION", code: code.toUpperCase(), name: code.toUpperCase() }],
  })),
);

describe("BCA đổi mã trên cùng đề thử", () => {
  async function generateCa1(user: UserEvent, server: FakeServer) {
    renderPanel(server, mapExamDetail(examDetailWire({ parts: BCA_PARTS as never })), BCA_CHECK, "ca1");
    await user.click(await screen.findByRole("button", { name: "Sinh đề xem trước" }));
    await screen.findByText(/Đề thử · CA1/);
  }

  it("đổi sang CA2 gửi basePreviewId và chỉ phần 3 để sinh lại; ghi rõ phần giữ nguyên", async () => {
    const user = userEvent.setup();
    const server = new FakeServer().on("POST", PREVIEW_PATH, { status: 200, body: bcaWire("ca1", { seed: 7 }) }, { status: 200, body: bcaWire("ca2", { seed: 7 }) });
    await generateCa1(user, server);
    expect(screen.queryByRole("button", { name: /^Đổi mã, sinh lại/ })).not.toBeInTheDocument();

    await pick(user, "Đường lựa chọn", /CA2/);
    expect(
      await screen.findByText(
        byTextContent(
          "Đề thử đang dùng CA1. Đổi sang CA2 giữ nguyên Part 1, Part 2 (câu 1–65), chỉ sinh lại Part 3 (câu 66–80). Sinh đề thử mới sinh lại cả bài với hạt giống mới.",
        ),
      ),
    ).toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: "Đổi mã, sinh lại Part 3" }));

    await waitFor(() => expect(server.calls("POST", PREVIEW_PATH)).toHaveLength(2));
    expect(bodyOf(server.calls("POST", PREVIEW_PATH)[1])).toMatchObject({
      target: { kind: "PATH", pathId: "ca2" },
      basePreviewId: PREVIEW_ID,
      regeneratePartIds: ["p3"],
      seed: 7,
    });
    expect(await screen.findByText(/Đề thử · CA2/)).toBeInTheDocument();
  });

  it("'Sinh đề thử mới' sinh lại cả bài: không gửi basePreviewId", async () => {
    const user = userEvent.setup();
    const server = new FakeServer().on("POST", PREVIEW_PATH, { status: 200, body: bcaWire("ca1") });
    await generateCa1(user, server);

    await user.click(screen.getByRole("button", { name: "Sinh đề thử mới" }));

    await waitFor(() => expect(server.calls("POST", PREVIEW_PATH)).toHaveLength(2));
    const second = bodyOf(server.calls("POST", PREVIEW_PATH)[1]);
    expect(second.basePreviewId).toBeUndefined();
    expect(second.regeneratePartIds).toBeUndefined();
    expect(second.seed).toBe(2);
  });

  it("PREVIEW_EXPIRED (410): báo đề thử hết hạn kèm nút sinh đề thử mới, bấm thì sinh lại không kèm basePreviewId", async () => {
    const user = userEvent.setup();
    const server = new FakeServer().on(
      "POST",
      PREVIEW_PATH,
      { status: 200, body: bcaWire("ca1") },
      { status: 410, body: { code: "PREVIEW_EXPIRED", message: "expired" } },
      { status: 200, body: bcaWire("ca2") },
    );
    await generateCa1(user, server);
    await pick(user, "Đường lựa chọn", /CA2/);
    await user.click(await screen.findByRole("button", { name: "Đổi mã, sinh lại Part 3" }));

    expect(await screen.findByRole("heading", { name: "Đề thử đã hết hạn" })).toBeInTheDocument();
    expect(screen.getByText("Đề thử chỉ giữ 30 phút nên không đổi mã trên đề này được nữa.")).toBeInTheDocument();
    expect(screen.queryByText("expired")).not.toBeInTheDocument();

    const regenerate = screen.getByRole("button", { name: "Sinh đề thử mới" });
    await user.click(regenerate);

    await waitFor(() => expect(server.calls("POST", PREVIEW_PATH)).toHaveLength(3));
    const third = bodyOf(server.calls("POST", PREVIEW_PATH)[2]);
    expect(third.basePreviewId).toBeUndefined();
    expect(third.target).toEqual({ kind: "PATH", pathId: "ca2" });
    expect(await screen.findByText(/Đề thử · CA2/)).toBeInTheDocument();
  });
});

describe("xem thứ tự gốc trên màn", () => {
  it("nút chuyển qua lại giữa thứ tự đã đảo và thứ tự gốc, câu theo vị trí gốc", async () => {
    const user = userEvent.setup();
    const server = new FakeServer().on("POST", PREVIEW_PATH, { status: 200, body: generatedWire() });
    renderPanel(server, mapExamDetail(examDetailWire()));
    await user.click(await screen.findByRole("button", { name: "Sinh đề xem trước" }));
    await screen.findByText("MQ3 · v1");
    const codes = () => screen.getAllByText(/^MQ\d · v1$/).map((node) => node.textContent);

    expect(codes()).toEqual(["MQ3 · v1", "MQ1 · v1", "MQ2 · v1"]);
    await user.click(screen.getByRole("button", { name: "Xem thứ tự gốc" }));
    expect(codes()).toEqual(["MQ1 · v1", "MQ2 · v1", "MQ3 · v1"]);
    expect(screen.getByText(/Đang xem thứ tự gốc/)).toBeInTheDocument();

    await user.click(screen.getByRole("button", { name: "Xem thứ tự đã đảo" }));
    expect(codes()).toEqual(["MQ3 · v1", "MQ1 · v1", "MQ2 · v1"]);
    expect(screen.queryByText(/Đang xem thứ tự gốc/)).not.toBeInTheDocument();
  });
});

describe("sinh thử không được", () => {
  it("lỗi của đường: mở chỗ lỗi ở bước Cấu trúc theo khối của lỗi", async () => {
    const user = userEvent.setup();
    const failed = {
      examId: EXAM_ID,
      status: "FAILED",
      reason: "INVALID",
      seed: 1,
      generatedAt: "2026-11-17T01:00:00Z",
      path: { pathId: "default", choices: [] },
      issues: [{ key: "k1", code: "SECTION_INCOMPLETE", severity: "ERROR", group: "STRUCTURE", subjectCode: "MATHEMATICS", sectionId: "sec-math" }],
    };
    const server = new FakeServer().on("POST", PREVIEW_PATH, { status: 200, body: failed });
    renderPanel(server, mapExamDetail(examDetailWire()));

    await user.click(await screen.findByRole("button", { name: "Sinh đề xem trước" }));

    expect(await screen.findByText(/Môn .+ chưa hoàn tất cấu hình\./)).toBeInTheDocument();
    expect(screen.getByRole("link", { name: /Đến dòng ma trận/ })).toHaveAttribute("href", `/exams/${EXAM_ID}/structure?section=sec-math`);
  });

  it("DISPLAY_SEGMENT_MISMATCH: hiện trong danh sách lỗi với điểm thập phân, không in mã phân đoạn, và mở đúng phần", async () => {
    const user = userEvent.setup();
    const failed = {
      examId: EXAM_ID,
      status: "FAILED",
      reason: "INVALID",
      seed: 1,
      generatedAt: "2026-11-17T01:00:00Z",
      path: { pathId: "default", choices: [] },
      issues: [
        {
          key: "seg",
          code: "DISPLAY_SEGMENT_MISMATCH",
          severity: "ERROR",
          group: "DISPLAY",
          partName: "Part 1",
          dimension: "POINTS",
          segmentCode: "SEG_RAW_CODE",
          required: 12.5,
          available: 10.25,
          location: { partId: "part-1" },
        },
      ],
    };
    const server = new FakeServer().on("POST", PREVIEW_PATH, { status: 200, body: failed });
    renderPanel(server, mapExamDetail(examDetailWire()));

    await user.click(await screen.findByRole("button", { name: "Sinh đề xem trước" }));

    expect(await screen.findByText("Part 1: một phân đoạn có 10,25 điểm trong khi khai báo 12,5 điểm.")).toBeInTheDocument();
    expect(screen.queryByText(/SEG_RAW_CODE/)).not.toBeInTheDocument();
    expect(screen.getByRole("link", { name: /Đến dòng ma trận/ })).toHaveAttribute("href", `/exams/${EXAM_ID}/structure?part=part-1`);
  });
});

describe("phân đoạn hiển thị của sinh thử một môn (BE-20)", () => {
  /** Đề thử của `generatedWire` với `displaySegments` trên phần duy nhất; `segments` rỗng = chiếu cục bộ. */
  function sectionWire(segments: Array<{ code: string; name: string; fromPosition: number; toPosition: number; commandCount: number; maxPoints: number }>) {
    const base = generatedWire();
    const paper = base.paper as { parts: Array<Record<string, unknown>> };
    return { ...base, paper: { ...paper, parts: [{ ...paper.parts[0], displaySegments: segments }] } };
  }

  async function generateSection(user: UserEvent, body: unknown) {
    const server = new FakeServer().on("POST", PREVIEW_PATH, { status: 200, body });
    renderPanel(server);
    await user.click(screen.getByRole("radio", { name: "Một môn" }));
    await pick(user, "Môn", /Vật lí/);
    await user.click(screen.getByRole("button", { name: "Sinh đề xem trước" }));
    await waitFor(() => expect(server.calls("POST", PREVIEW_PATH)).toHaveLength(1));
    expect(bodyOf(server.calls("POST", PREVIEW_PATH)[0]).target).toMatchObject({ kind: "SECTION", sectionId: "sec-phy" });
  }

  it("phần sinh đủ có displaySegments: tiêu đề phân đoạn hiện với dải câu, lệnh và điểm khai báo", async () => {
    const user = userEvent.setup();
    await generateSection(
      user,
      sectionWire([
        { code: "P1", name: "Phan I", fromPosition: 1, toPosition: 2, commandCount: 2, maxPoints: 2 },
        { code: "P2", name: "Phan II", fromPosition: 3, toPosition: 3, commandCount: 1, maxPoints: 1.5 },
      ]),
    );

    // Tiêu đề phân đoạn (cấp 4, trên tiêu đề khối một mức) nêu dải câu, số lệnh và điểm khai báo.
    expect(await screen.findByRole("heading", { level: 4, name: /Phan I · câu 1–2 · 2 lệnh · 2 điểm/ })).toBeInTheDocument();
    // Điểm khai báo thập phân của BE-20 (maxPoints 1.5) qua định dạng vi.
    expect(screen.getByRole("heading", { level: 4, name: /Phan II · câu 3–3 · 1 lệnh · 1,5 điểm/ })).toBeInTheDocument();
    // Tiêu đề phần (cấp 3) và tiêu đề khối (cấp 5, dưới phân đoạn cấp 4) vẫn hiện như thường.
    expect(screen.getByRole("heading", { level: 3, name: /Math/ })).toBeInTheDocument();
    expect(screen.getAllByRole("heading", { level: 5, name: /^Khối / })).toHaveLength(2);
  });

  it("phần sinh không đủ gửi displaySegments []: không có tiêu đề phân đoạn, hiện như trước", async () => {
    const user = userEvent.setup();
    await generateSection(user, sectionWire([]));

    expect(await screen.findByRole("heading", { level: 3, name: /Math/ })).toBeInTheDocument();
    // Không có tiêu đề phân đoạn nào: tiêu đề cấp 4 chỉ còn của khối, hiện như khi phần không có phân đoạn.
    for (const heading of screen.getAllByRole("heading", { level: 4 })) expect(heading).toHaveTextContent(/^Khối /);
    expect(screen.queryByText(/Phan I\b/)).not.toBeInTheDocument();
  });
});
