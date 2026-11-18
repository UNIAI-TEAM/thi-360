import { cleanup, screen, within } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import en from "@/messages/en.json";
import vi_ from "@/messages/vi.json";
import { renderWithProviders } from "@/test/render";
import { issueStructureHref } from "../check";
import { ExamPreviewCompliance } from "../components/exam-preview-compliance";
import { ExamPreviewPaper } from "../components/exam-preview-paper";
import { useIssueText } from "../components/exam-review-shared";
import { complianceDifference, topicQuotaPending } from "../review-compliance";
import type {
  CheckIssue,
  PreviewBlock,
  PreviewCompliance,
  PreviewItem,
  PreviewPaper,
  PreviewQuestion,
  TopicQuotaIndex,
} from "../review-types";
import { useContentReference } from "@/features/content/queries";
import type { ExamDetail } from "../types";
import { EXAM_ID, EXAM_PATH, FakeServer } from "./fake-exams-server";

vi.mock(import("@/features/content/api"), async (importOriginal) => {
  const actual = await importOriginal();
  const { getContentReferenceFixture: getContentReference } = await import("@/test/fixtures/content-reference");
  return { ...actual, getContentReference, defaultContentApiDeps: { getContentReference } };
});

/* ---------- Dữ liệu ---------- */

const row = (over: Partial<PreviewCompliance>): PreviewCompliance => ({
  criterion: "GRADE:GRADE_12",
  scope: "EXAM",
  required: 5,
  actual: 5,
  enforcement: "REQUIRED",
  basis: "QUESTION",
  independent: false,
  note: null,
  ...over,
});

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
    options: [{ id: "a", content: "<p>A</p>", correct: true }],
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

function paperOf(blocks: PreviewBlock[]): PreviewPaper {
  return {
    totalQuestions: 1,
    totalPoints: 10,
    totalMinutes: 60,
    sharedDuration: false,
    hasManualGrading: false,
    subjects: [],
    checks: [],
    knowledgeStats: [],
    parts: [
      {
        partId: "p1",
        name: "Phần I",
        order: 1,
        durationMinutes: 30,
        firstNumber: 1,
        lastNumber: 1,
        points: 1,
        maxPoints: null,
        blocks,
      },
    ],
  };
}

const blockOf = (items: PreviewItem[], over: Partial<PreviewBlock> = {}): PreviewBlock => ({
  blockId: "b1",
  blockName: "Nhóm 1",
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
  items,
  ...over,
});

const quotaIndex = (entries: Array<[string, Partial<{ name: string; enforcement: "REQUIRED" | "REFERENCE"; nodeIds: string[]; difficultyCodes: string[] }>]>): TopicQuotaIndex =>
  new Map(
    entries.map(([id, quota]) => [
      id,
      { name: "", enforcement: "REQUIRED", nodeIds: [], difficultyCodes: [], ...quota },
    ]),
  );

const structureWire = (topicQuotas: unknown[]) => ({
  examId: EXAM_ID,
  revision: 1,
  parts: [
    {
      id: "p1",
      name: "Phần I",
      sections: [
        {
          id: "s1",
          subjectCode: "VIETNAMESE",
          topicQuotas,
          blocks: [],
        },
      ],
    },
  ],
});

/* ---------- Cột Chênh lệch (R-124) ---------- */

describe("cột Chênh lệch của bảng đối chiếu", () => {
  const paper = paperOf([blockOf([{ kind: "QUESTION", question: question(1) }])]);

  function renderTable(rows: PreviewCompliance[], locale?: "en") {
    return renderWithProviders(
      <ExamPreviewCompliance paper={paper} compliance={rows} dropped={[]} />,
      locale ? { locale, messages: en } : undefined,
    );
  }

  it("Thực tế trừ Yêu cầu: âm ghi dấu trừ thật, bằng nhau ghi 0, dương ghi dấu +", async () => {
    renderTable([
      row({ criterion: "GRADE:GRADE_12", required: 12, actual: 10 }),
      row({ criterion: "GRADE:GRADE_11", required: 8, actual: 8 }),
      row({ criterion: "GRADE:GRADE_10", required: 1, actual: 2 }),
    ]);

    const table = await screen.findByRole("table", { name: "Yêu cầu và thực tế của đề thử" });
    const rows = within(table).getAllByRole("row").slice(1);
    expect(within(rows[0]!).getByText("\u22122")).toBeInTheDocument();
    expect(within(rows[1]!).getByText("0")).toBeInTheDocument();
    expect(within(rows[2]!).getByText("+1")).toBeInTheDocument();
    expect(within(rows[0]!).getByText("Lệch · bắt buộc")).toBeInTheDocument();
  });

  it("chênh lệch nhỏ hơn epsilon (sai số BigDecimal) ghi 0", () => {
    const tiny = row({ required: 1, actual: 1 + 1e-12, basis: "POINTS" });
    expect(complianceDifference(tiny)).toBe(0);
    expect(complianceDifference(row({ required: 2, actual: 1.5 }))).toBeCloseTo(-0.5);
    expect(complianceDifference(row({ criterion: "TOPIC:t1", required: 3, actual: 4 }))).toBeCloseTo(1);
  });

  it("chỉ tiêu chủ đề ghi 'ít nhất' ở cột Yêu cầu và chênh lệch vẫn là hiệu số", async () => {
    renderTable([row({ criterion: "TOPIC:t1", scope: "SECTION:s1", required: 3, actual: 4 })]);

    const table = await screen.findByRole("table");
    const only = within(table).getAllByRole("row")[1]!;
    expect(within(only).getByText("ít nhất 3")).toBeInTheDocument();
    expect(within(only).getByText("+1")).toBeInTheDocument();
  });

  it("bản en: cột Difference và 'at least'", async () => {
    renderTable([row({ criterion: "TOPIC:t1", required: 3, actual: 1 })], "en");

    const table = await screen.findByRole("table", { name: "Required and actual values of the trial paper" });
    expect(within(table).getByText("Difference")).toBeInTheDocument();
    expect(within(table).getByText("at least 3")).toBeInTheDocument();
    expect(within(table).getByText("\u22122")).toBeInTheDocument();
  });
});

/* ---------- Điểm tối đa từng câu (R-112) ---------- */

describe("điểm tối đa của từng câu trên đề thử", () => {
  it("mỗi câu hiện điểm tối đa lấy từ `points`, định dạng theo ngôn ngữ (THPTQG Toán: 0,25 / 1 / 0,5)", async () => {
    const paper = paperOf([
      blockOf(
        [
          { kind: "QUESTION", question: question(1, { points: 0.25 }) },
          { kind: "QUESTION", question: question(2, { points: 1 }) },
          { kind: "QUESTION", question: question(3, { points: 0.5 }) },
        ],
        { lastNumber: 3, questionCount: 3 },
      ),
    ]);

    renderWithProviders(<ExamPreviewPaper paper={paper} />);

    expect(await screen.findByText("Tối đa 0,25 điểm")).toBeInTheDocument();
    expect(screen.getByText("Tối đa 1 điểm")).toBeInTheDocument();
    expect(screen.getByText("Tối đa 0,5 điểm")).toBeInTheDocument();
  });

  it("câu con của chùm cũng hiện điểm tối đa; bản en dùng dấu chấm", async () => {
    const paper = paperOf([
      blockOf(
        [
          {
            kind: "SET",
            setId: "set-1",
            code: "S1",
            version: 1,
            simulated: false,
            passage: "<p>Đọc đoạn văn</p>",
            children: [question(1, { points: 0.25 })],
          },
        ],
        { questionType: "QUESTION_SET", setCount: 1, setSize: 1 },
      ),
    ]);

    renderWithProviders(<ExamPreviewPaper paper={paper} />, { locale: "en", messages: en });

    expect(await screen.findByText("Max 0.25 points")).toBeInTheDocument();
  });

  it("bản en chia số ít/số nhiều: 1 điểm là 'point', 0.5 và 0.25 là 'points'", async () => {
    const paper = paperOf([
      blockOf(
        [
          { kind: "QUESTION", question: question(1, { points: 1 }) },
          { kind: "QUESTION", question: question(2, { points: 0.5 }) },
        ],
        { lastNumber: 2, questionCount: 2 },
      ),
    ]);

    renderWithProviders(<ExamPreviewPaper paper={paper} />, { locale: "en", messages: en });

    expect(await screen.findByText("Max 1 point")).toBeInTheDocument();
    expect(screen.getByText("Max 0.5 points")).toBeInTheDocument();
  });
});

/* ---------- Dòng REFERENCE chờ mapping (H31) ---------- */

describe("chỉ tiêu REFERENCE chưa gắn MAPID", () => {
  it("nhận biết theo id chỉ tiêu trong cấu trúc: chỉ REFERENCE rỗng nodeIds mới chờ", () => {
    const quotas = quotaIndex([
      ["pending", { enforcement: "REFERENCE", nodeIds: [] }],
      ["mapped", { enforcement: "REFERENCE", nodeIds: ["n1"] }],
      ["required", { enforcement: "REQUIRED", nodeIds: [] }],
    ]);
    const pendingRow = row({ criterion: "TOPIC:pending", enforcement: "REFERENCE", required: 5, actual: 0 });

    expect(topicQuotaPending(pendingRow, quotas)).toBe(true);
    expect(topicQuotaPending(row({ criterion: "TOPIC:mapped", enforcement: "REFERENCE" }), quotas)).toBe(false);
    expect(topicQuotaPending(row({ criterion: "TOPIC:required", nodeIds: [] } as never), quotas)).toBe(false);
    // Chưa tải được cấu trúc: không coi là chờ (hiện số thực tế), không đoán.
    expect(topicQuotaPending(pendingRow, undefined)).toBe(false);
    expect(topicQuotaPending(pendingRow, new Map())).toBe(false);
  });

  it("ô Thực tế và Chênh lệch ghi 'Chưa có dữ liệu đối chiếu', Kết quả 'Chờ mapping', không tính là lệch", async () => {
    new FakeServer()
      .on("GET", `${EXAM_PATH}/structure`, {
        status: 200,
        body: structureWire([
          { id: "pending-1", name: "Chính tả, từ vựng và ngữ nghĩa", enforcement: "REFERENCE", nodeIds: [], difficultyCodes: ["RECOGNITION"], count: 5 },
        ]),
      })
      .install();
    const paper = paperOf([blockOf([{ kind: "QUESTION", question: question(1) }])]);
    renderWithProviders(
      <ExamPreviewCompliance
        paper={paper}
        examId={EXAM_ID}
        dropped={[]}
        compliance={[row({ criterion: "TOPIC:pending-1", scope: "SECTION:s1", enforcement: "REFERENCE", required: 5, actual: 0 })]}
      />,
    );

    await screen.findByRole("table", { name: "Yêu cầu và thực tế của đề thử" });
    // Đợi truy vấn cấu trúc xong trước khi lấy dòng: bảng vẽ lại khi chỉ số chủ đề tải xong.
    expect(await screen.findByText("Chờ mapping")).toBeInTheDocument();
    const only = within(screen.getByRole("table", { name: "Yêu cầu và thực tế của đề thử" })).getAllByRole("row")[1]!;
    expect(within(only).getAllByText("Chưa có dữ liệu đối chiếu")).toHaveLength(2);
    expect(within(only).getByText("Chính tả, từ vựng và ngữ nghĩa · Nhận biết")).toBeInTheDocument();
    expect(within(only).getByText("ít nhất 5")).toBeInTheDocument();
    expect(within(only).queryByText("Lệch · tham khảo")).not.toBeInTheDocument();
    // Dòng chờ không được đếm vào "N dòng lệch".
    expect(screen.getByText("Đạt mọi tiêu chí")).toBeInTheDocument();
  });

  it("dòng REQUIRED rỗng nodeIds giữ bảng bình thường: hiện số và vẫn là lệch bắt buộc", async () => {
    new FakeServer()
      .on("GET", `${EXAM_PATH}/structure`, {
        status: 200,
        body: structureWire([
          { id: "req-1", name: "Xác suất", enforcement: "REQUIRED", nodeIds: [], count: 3 },
        ]),
      })
      .install();
    const paper = paperOf([blockOf([{ kind: "QUESTION", question: question(1) }])]);
    renderWithProviders(
      <ExamPreviewCompliance
        paper={paper}
        examId={EXAM_ID}
        dropped={[]}
        compliance={[row({ criterion: "TOPIC:req-1", scope: "SECTION:s1", enforcement: "REQUIRED", required: 3, actual: 0 })]}
      />,
    );

    await screen.findByRole("table", { name: "Yêu cầu và thực tế của đề thử" });
    // Cột chỉ tiêu chỉ điền tên khi truy vấn cấu trúc xong; đợi nó để bảng không còn vẽ lại.
    expect(await screen.findByText("Xác suất")).toBeInTheDocument();
    expect(await screen.findByText("Lệch · bắt buộc")).toBeInTheDocument();
    const only = within(screen.getByRole("table", { name: "Yêu cầu và thực tế của đề thử" })).getAllByRole("row")[1]!;
    expect(within(only).getByText("\u22123")).toBeInTheDocument();
    expect(within(only).queryByText("Chưa có dữ liệu đối chiếu")).not.toBeInTheDocument();
    expect(screen.getByText("1 dòng lệch")).toBeInTheDocument();
  });
});

/* ---------- Ba mã kiểm tra mới (R-122, BE-15) ---------- */

function issueOf(overrides: Partial<CheckIssue>): CheckIssue {
  return {
    key: "k",
    code: "TOPIC_QUOTA_MAPPING_MISSING",
    severity: "ERROR",
    group: "MATRIX",
    partId: "p1",
    partName: "Phần 1",
    sectionId: "s1",
    subjectCode: "VIETNAMESE",
    blockId: "b1",
    blockName: "Nhóm 1",
    optionId: null,
    gradeCode: null,
    difficultyCode: null,
    dimension: null,
    competencyCode: null,
    answerType: null,
    subjectCount: null,
    required: null,
    available: null,
    timeLimitSeconds: null,
    questionCodes: null,
    ...overrides,
  };
}

function IssueLine({ issue, exam }: { issue: CheckIssue; exam?: Pick<ExamDetail, "parts"> }) {
  const issueText = useIssueText(exam);
  const loaded = useContentReference().isSuccess;
  return loaded ? <p data-testid="line">{issueText(issue)}</p> : null;
}

async function textOf(issue: CheckIssue, options?: { en?: boolean }) {
  renderWithProviders(
    <IssueLine issue={issue} />,
    options?.en ? { locale: "en", messages: en } : undefined,
  );
  const text = (await screen.findByTestId("line")).textContent ?? "";
  cleanup();
  return text;
}

describe("câu chữ của ba mã kiểm tra mới", () => {
  const NEW_CODES = ["TEMPLATE_NOT_PUBLISHABLE", "STRUCTURE_COUNT_LOCKED", "TOPIC_QUOTA_MAPPING_MISSING"] as const;

  it("mỗi mã có câu riêng vi và en, khác câu INFO_MISSING", () => {
    const viIssue = vi_.exams.review.issue as Record<string, string>;
    const enIssue = en.exams.review.issue as Record<string, string>;
    for (const code of NEW_CODES) {
      expect(viIssue[code], `vi ${code}`).toBeTruthy();
      expect(enIssue[code], `en ${code}`).toBeTruthy();
      expect(viIssue[code]).not.toBe(viIssue.INFO_MISSING);
      expect(enIssue[code]).not.toBe(enIssue.INFO_MISSING);
    }
  });

  it("TOPIC_QUOTA_MAPPING_MISSING: câu nêu dòng chỉ tiêu bắt buộc chưa gắn MAPID; bấm chọn đúng nhóm ở bước 2", async () => {
    const issue = issueOf({ code: "TOPIC_QUOTA_MAPPING_MISSING" });

    expect(await textOf(issue)).toContain("chưa gắn MAPID");
    // Nhóm của vấn đề → liên kết ?block=<id> (bước Cấu trúc mở đúng nhóm); mức môn → ?section=.
    expect(issueStructureHref(EXAM_ID, issue)).toBe(`/exams/${EXAM_ID}/structure?block=b1`);
    expect(issueStructureHref(EXAM_ID, { ...issue, blockId: null })).toBe(`/exams/${EXAM_ID}/structure?section=s1`);
  });

  it("STRUCTURE_COUNT_LOCKED: câu nêu số của mẫu và số hiện có", async () => {
    const text = await textOf(
      issueOf({ code: "STRUCTURE_COUNT_LOCKED", group: "STRUCTURE", blockId: null, blockName: null, required: 12, available: 10 }),
    );
    expect(text).toContain("12");
    expect(text).toContain("10");
    expect(text).toContain("Tiếng Việt");
  });

  it("TEMPLATE_NOT_PUBLISHABLE: câu nêu môn và không công bố được; lỗi cả bài không có chỗ sửa", async () => {
    const issue = issueOf({
      code: "TEMPLATE_NOT_PUBLISHABLE",
      group: "STRUCTURE",
      partId: null,
      partName: null,
      sectionId: null,
      blockId: null,
      blockName: null,
    });

    const text = await textOf(issue);
    expect(text).toContain("Tiếng Việt");
    expect(text).toContain("không công bố");
    expect(issueStructureHref(EXAM_ID, issue)).toBeNull();

    const enText = await textOf(issue, { en: true });
    expect(enText).toContain("cannot be published");
  });
});
