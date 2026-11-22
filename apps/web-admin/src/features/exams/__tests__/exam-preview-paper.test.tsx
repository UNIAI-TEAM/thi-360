import { readFileSync } from "node:fs";
import path from "node:path";
import { screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, describe, expect, it, vi } from "vitest";
import en from "@/messages/en.json";
import { renderWithProviders } from "@/test/render";
import { ExamPreviewPaper } from "../components/exam-preview-paper";
import type { PreviewBlock, PreviewItem, PreviewPaper, PreviewQuestion } from "../review-types";
import { byTextContent } from "./text-content";

vi.mock(import("@/features/content/api"), async (importOriginal) => {
  const actual = await importOriginal();
  const { getContentReferenceFixture: getContentReference } = await import("@/test/fixtures/content-reference");
  return { ...actual, getContentReference, defaultContentApiDeps: { getContentReference } };
});

function makeQuestion(number: number, over: Partial<PreviewQuestion> = {}): PreviewQuestion {
  return {
    number,
    questionId: `q-${number}`,
    code: `Q-${number}`,
    version: 1,
    type: "SINGLE_CHOICE",
    simulated: false,
    content: `<p>Stem ${number}</p>`,
    gradeCode: "GRADE_12",
    difficultyCode: "APPLICATION",
    knowledge: { nodeId: `k-${number}`, code: `KN-${number}`, name: `Kiến thức ${number}` },
    points: 1,
    options: null,
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

function makeBlock(blockId: string, blockName: string, items: PreviewItem[], over: Partial<PreviewBlock> = {}): PreviewBlock {
  const numbers = items.flatMap((it) =>
    it.kind === "QUESTION" ? [it.question.number] : it.children.map((c) => c.number),
  );
  return {
    blockId,
    blockName,
    subjectCode: "MATH",
    questionType: items[0]?.kind === "QUESTION" ? items[0].question.type : "QUESTION_SET",
    manualGrading: false,
    pointsEach: 1,
    firstNumber: Math.min(...numbers),
    lastNumber: Math.max(...numbers),
    questionCount: numbers.length,
    setCount: null,
    setSize: null,
    requirement: { mode: "COMBINED", cells: [], byGrade: [], byDifficulty: [], byCompetency: [] },
    allocation: [],
    items,
    ...over,
  };
}

function buildAllEightTypesPaper(): PreviewPaper {
  const q1 = makeQuestion(1, {
    code: "SC-1",
    type: "SINGLE_CHOICE",
    content: "<p>Thủ đô của Việt Nam là gì?</p>",
    points: 0.5,
    optionOrder: ["sc-b", "sc-a"],
    originalOptionOrder: ["sc-a", "sc-b"],
    solution: "<p>Giải thích: Hà Nội là thủ đô.</p>",
    body: {
      type: "SINGLE_CHOICE",
      options: [
        { id: "sc-a", content: "<p>Hà Nội</p>", correct: true },
        { id: "sc-b", content: "<p>Đà Nẵng</p>", correct: false },
      ],
    },
  });

  const q2 = makeQuestion(2, {
    code: "MC-2",
    type: "MULTIPLE_CHOICE",
    content: "<p>Những số nào là số nguyên tố?</p>",
    body: {
      type: "MULTIPLE_CHOICE",
      scoringRule: "PARTIAL",
      options: [
        { id: "mc-1", content: "<p>2</p>", correct: true },
        { id: "mc-2", content: "<p>4</p>", correct: false },
        { id: "mc-3", content: "<p>7</p>", correct: true },
      ],
    },
  });

  const q3 = makeQuestion(3, {
    code: "SA-3",
    type: "SHORT_ANSWER",
    content: "<p>Tính 7 × 6.</p>",
    body: {
      type: "SHORT_ANSWER",
      answers: ["42", "42.0"],
      inputHint: "Số nguyên",
      answerFormat: "INTEGER",
    },
  });

  const q4 = makeQuestion(4, {
    code: "CL-4",
    type: "CLOZE",
    content: null,
    points: 2,
    body: {
      type: "CLOZE",
      passage: "<p>Nước sôi ở [[b1]] độ C và đóng băng ở [[b2]] độ C.</p>",
      blanks: [
        { id: "b1", answers: ["100", "100.0"], points: 1 },
        { id: "b2", answers: ["0"], points: 1 },
      ],
    },
  });

  const q6 = makeQuestion(6, {
    code: "TF-6",
    type: "TRUE_FALSE",
    content: "<p>Xét các mệnh đề sau:</p>",
    body: {
      type: "TRUE_FALSE",
      scoringMethod: "PER_STATEMENT",
      scoreTable: [],
      statements: [
        { id: "st-1", content: "<p>2 + 2 = 4</p>", answer: true, points: 0.5 },
        { id: "st-2", content: "<p>3 > 5</p>", answer: false, points: 0.5 },
      ],
    },
  });

  const q7 = makeQuestion(7, {
    code: "MT-7",
    type: "MATCHING",
    content: "<p>Ghép đơn vị đo:</p>",
    optionOrder: ["r2", "r1"],
    originalOptionOrder: ["r1", "r2"],
    body: {
      type: "MATCHING",
      left: [
        { id: "l1", content: "<p>Pa (pascal)</p>" },
        { id: "l2", content: "<p>Joule</p>" },
      ],
      right: [
        { id: "r1", content: "<p>Áp suất</p>" },
        { id: "r2", content: "<p>Năng lượng</p>" },
      ],
      pairs: [
        { leftId: "l1", rightId: "r1", points: 0.5 },
        { leftId: "l2", rightId: "r2", points: 0.5 },
      ],
      reuseRight: false,
    },
  });

  const q8Child1 = makeQuestion(8, {
    code: "SET-C1",
    type: "SINGLE_CHOICE",
    content: "<p>Ý chính của đoạn văn là gì?</p>",
    knowledge: { nodeId: "k-set-1", code: "KN-S1", name: "Đọc hiểu văn bản" },
    body: {
      type: "SINGLE_CHOICE",
      options: [
        { id: "sc1-a", content: "<p>Bảo vệ môi trường</p>", correct: true },
        { id: "sc1-b", content: "<p>Du lịch biển</p>", correct: false },
      ],
    },
  });

  const q9Child2 = makeQuestion(9, {
    code: "SET-C2",
    type: "SHORT_ANSWER",
    content: "<p>Từ khóa xuất hiện ở dòng 1?</p>",
    knowledge: { nodeId: "k-set-2", code: "KN-S2", name: "Từ vựng đọc hiểu" },
    body: {
      type: "SHORT_ANSWER",
      answers: ["sinh thái"],
      inputHint: null,
      answerFormat: "TEXT",
    },
  });

  const q10 = makeQuestion(10, {
    code: "ES-10",
    type: "ESSAY",
    content: "<p>Phân tích hình tượng nhân vật.</p>",
    points: 5,
    rubric: [
      { id: "rub-1", description: "Mở bài và kết bài rõ ràng", points: 1 },
      { id: "rub-2", description: "Phân tích sâu dẫn chứng", points: 4 },
    ],
    solution: "<p>Gợi ý làm bài chi tiết.</p>",
    body: {
      type: "ESSAY",
      gradingGuide: "<p>Chấm theo ý</p>",
      rubric: [
        { id: "rub-1", description: "Mở bài và kết bài rõ ràng", points: 1 },
        { id: "rub-2", description: "Phân tích sâu dẫn chứng", points: 4 },
      ],
      sampleAnswer: null,
      wordLimit: 150,
    },
  });

  return {
    totalQuestions: 10,
    totalPoints: 13,
    totalMinutes: 90,
    sharedDuration: false,
    hasManualGrading: true,
    subjects: [],
    checks: [],
    knowledgeStats: [],
    parts: [
      {
        partId: "part-all",
        name: "Phần tổng hợp tám dạng",
        order: 1,
        durationMinutes: 90,
        firstNumber: 1,
        lastNumber: 10,
        points: 13,
        maxPoints: 13,
        blocks: [
          makeBlock("blk-obj-1", "Khối trắc nghiệm & điền", [
            { kind: "QUESTION", question: q1 },
            { kind: "QUESTION", question: q2 },
            { kind: "QUESTION", question: q3 },
            { kind: "QUESTION", question: q4 },
          ]),
          makeBlock("blk-obj-2", "Khối đúng sai & ghép đôi", [
            { kind: "QUESTION", question: q6 },
            { kind: "QUESTION", question: q7 },
          ]),
          makeBlock(
            "blk-set",
            "Khối đọc hiểu",
            [
              {
                kind: "SET",
                setId: "set-reading-1",
                code: "SET-R1",
                version: 2,
                simulated: false,
                passage: "<p>Đoạn văn mẫu về bảo vệ sinh thái.</p>",
                children: [q8Child1, q9Child2],
              },
            ],
            { setCount: 1, setSize: 2, firstNumber: 8, lastNumber: 9, questionCount: 2 },
          ),
          makeBlock(
            "blk-essay",
            "Khối tự luận",
            [{ kind: "QUESTION", question: q10 }],
            { manualGrading: true },
          ),
        ],
      },
    ],
  };
}

afterEach(() => {
  vi.restoreAllMocks();
});

describe("tám dạng câu", () => {
  it("hiển thị đủ 8 dạng câu qua @thi360/ui/question ở chế độ review: không chỉnh sửa được, hiện đáp án, tiêu chí chấm, lời giải, metadata và không có lỗi console", async () => {
    const user = userEvent.setup();
    const errorSpy = vi.spyOn(console, "error").mockImplementation(() => {});

    const { container } = renderWithProviders(<ExamPreviewPaper paper={buildAllEightTypesPaper()} />);

    // Mở hết các khối thu gọn để cả 8 dạng đều hiển thị.
    for (const btn of screen.queryAllByRole("button", { name: /^Hiện tất cả/ })) {
      await user.click(btn);
    }

    // Chờ danh mục nạp xong để đối chiếu nhãn lớp / độ khó.
    await screen.findAllByText("Lớp 12");

    // Chế độ review: không có input/select/textarea/radio/checkbox chỉnh sửa được; mọi input phải readonly.
    expect(container.querySelectorAll("textarea, select, input[type=radio], input[type=checkbox]")).toHaveLength(0);
    expect(screen.queryAllByRole("combobox")).toHaveLength(0);
    expect(screen.queryAllByRole("checkbox")).toHaveLength(0);
    expect(screen.queryAllByRole("radio")).toHaveLength(0);
    for (const input of container.querySelectorAll("input")) {
      expect(input).toHaveAttribute("readonly");
    }

    // 1. SINGLE_CHOICE + solution + optionOrder (Đà Nẵng trước Hà Nội).
    const q1Heading = screen.getByRole("heading", { name: "Câu 1" });
    const q1Article = q1Heading.closest("article")!;
    expect(within(q1Article).getByText("Đáp án đúng")).toBeInTheDocument();
    expect(within(q1Article).getByText("Lời giải")).toBeInTheDocument();
    expect(within(q1Article).getByText("Giải thích: Hà Nội là thủ đô.")).toBeInTheDocument();
    const q1OptionTexts = [...q1Article.querySelectorAll("fieldset[data-question-options] [data-exam-content]")].map(
      (n) => n.textContent?.trim(),
    );
    expect(q1OptionTexts).toEqual(["Đà Nẵng", "Hà Nội"]);

    // 2. MULTIPLE_CHOICE.
    const q2Article = screen.getByRole("heading", { name: "Câu 2" }).closest("article")!;
    expect(within(q2Article).getAllByText("Đáp án đúng")).toHaveLength(2);

    // 3. SHORT_ANSWER.
    const q3Article = screen.getByRole("heading", { name: "Câu 3" }).closest("article")!;
    expect(within(q3Article).getByText("42")).toBeInTheDocument();
    expect(within(q3Article).getByText("42.0")).toBeInTheDocument();

    // 4. CLOZE (2 blanks -> Câu 4–5).
    const q4Article = screen.getByRole("heading", { name: "Câu 4–5" }).closest("article")!;
    expect(within(q4Article).getByText("100 · 100.0")).toBeInTheDocument();
    expect(within(q4Article).getByText("0")).toBeInTheDocument();

    // 5. TRUE_FALSE.
    const q6Article = screen.getByRole("heading", { name: "Câu 6" }).closest("article")!;
    expect(within(q6Article).getByRole("columnheader", { name: "Đáp án" })).toBeInTheDocument();
    expect(within(q6Article).getByText("Đúng")).toBeInTheDocument();
    expect(within(q6Article).getByText("Sai")).toBeInTheDocument();

    // 6. MATCHING (cột phải theo optionOrder ["r2", "r1"] -> a. Năng lượng, b. Áp suất).
    const q7Article = screen.getByRole("heading", { name: "Câu 7" }).closest("article")!;
    expect(within(q7Article).getByRole("columnheader", { name: "Đáp án" })).toBeInTheDocument();
    expect(within(q7Article).getByText("Áp suất")).toBeInTheDocument();
    expect(within(q7Article).getByText("Năng lượng")).toBeInTheDocument();
    const q7RightLetters = [...q7Article.querySelectorAll("tbody td:nth-child(3) [data-exam-content]")].map((el) =>
      el.textContent?.replace(/\s+/g, " ").trim(),
    );
    expect(q7RightLetters).toEqual(["b.Áp suất", "a.Năng lượng"]);

    // 7. QUESTION_SET & metadata cho cả câu đơn lẫn câu con.
    expect(screen.getByText("Đoạn văn mẫu về bảo vệ sinh thái.")).toBeInTheDocument();
    const q8Article = screen.getByRole("heading", { name: "Câu 8" }).closest("article")!;
    expect(within(q8Article).getByText("SET-C1 · v1")).toBeInTheDocument();
    expect(within(q8Article).getByText("Tối đa 1 điểm")).toBeInTheDocument();
    expect(within(q8Article).getByText("Lớp 12")).toBeInTheDocument();
    expect(within(q8Article).getByText("Vận dụng")).toBeInTheDocument();
    expect(within(q8Article).getByText(byTextContent("KN-S1 · Đọc hiểu văn bản"))).toBeInTheDocument();
    expect(within(q8Article).getByText("Đáp án đúng")).toBeInTheDocument();

    const q9Article = screen.getByRole("heading", { name: "Câu 9" }).closest("article")!;
    expect(within(q9Article).getByText("SET-C2 · v1")).toBeInTheDocument();
    expect(within(q9Article).getByText("sinh thái")).toBeInTheDocument();

    // 8. ESSAY (tiêu chí chấm + lời giải, không hiện khung bài viết 0 chữ giả).
    const q10Article = screen.getByRole("heading", { name: "Câu 10" }).closest("article")!;
    expect(within(q10Article).getByText("Tiêu chí chấm")).toBeInTheDocument();
    expect(within(q10Article).getByText(byTextContent("Mở bài và kết bài rõ ràng: 1 điểm"))).toBeInTheDocument();
    expect(within(q10Article).getByText(byTextContent("Phân tích sâu dẫn chứng: 4 điểm"))).toBeInTheDocument();
    expect(within(q10Article).getByText("Gợi ý làm bài chi tiết.")).toBeInTheDocument();
    expect(within(q10Article).queryByText("Bài viết của bạn")).not.toBeInTheDocument();

    // Metadata câu đơn.
    expect(within(q1Article).getByText("SC-1 · v1")).toBeInTheDocument();
    expect(within(q1Article).getByText("Tối đa 0,5 điểm")).toBeInTheDocument();
    expect(within(q1Article).getByText(byTextContent("KN-1 · Kiến thức 1"))).toBeInTheDocument();

    // Không có cảnh báo React key hay console.error nào.
    expect(errorSpy).not.toHaveBeenCalled();
  });

  it("hỗ trợ fallback khi question.body === null, rubric rỗng và câu mô phỏng (simulated)", () => {
    const paper: PreviewPaper = {
      totalQuestions: 4,
      totalPoints: 4,
      totalMinutes: 30,
      sharedDuration: false,
      hasManualGrading: true,
      subjects: [],
      checks: [],
      knowledgeStats: [],
      parts: [
        {
          partId: "p-fb",
          name: "Phần fallback",
          order: 1,
          durationMinutes: 30,
          firstNumber: 1,
          lastNumber: 4,
          points: 4,
          maxPoints: null,
          blocks: [
            makeBlock(
              "blk-fb",
              "Nhóm fallback",
              [
                {
                  kind: "QUESTION",
                  question: makeQuestion(1, {
                    type: "SHORT_ANSWER",
                    content: "<p>Hỏi ngắn không body</p>",
                    answer: "99",
                    body: null,
                  }),
                },
                {
                  kind: "QUESTION",
                  question: makeQuestion(2, {
                    type: "TRUE_FALSE",
                    content: "<p>Đúng/Sai không body</p>",
                    options: [
                      { id: "tf-1", content: "<p>Mệnh đề 1</p>", correct: true },
                      { id: "tf-2", content: "<p>Mệnh đề 2</p>", correct: false },
                    ],
                    body: null,
                  }),
                },
                {
                  kind: "QUESTION",
                  question: makeQuestion(3, {
                    type: "ESSAY",
                    content: "<p>Tự luận rubric rỗng</p>",
                    rubric: [],
                    body: null,
                  }),
                },
              ],
            ),
            makeBlock("blk-sim", "Nhóm mô phỏng", [
              {
                kind: "QUESTION",
                question: makeQuestion(4, {
                  simulated: true,
                  content: null,
                  body: null,
                }),
              },
            ]),
          ],
        },
      ],
    };

    renderWithProviders(<ExamPreviewPaper paper={paper} />);

    expect(screen.getByText("99")).toBeInTheDocument();
    expect(screen.getByText("Mệnh đề 1")).toBeInTheDocument();
    expect(screen.getByText("Câu chưa có tiêu chí chấm.")).toBeInTheDocument();
    expect(screen.getByText(/Câu của nguồn mô phỏng/)).toBeInTheDocument();
  });

  it("original={true} đảo lại thứ tự câu, số câu và thứ tự phương án / cột phải MATCHING theo authored order", () => {
    const paper: PreviewPaper = {
      totalQuestions: 2,
      totalPoints: 2,
      totalMinutes: 30,
      sharedDuration: false,
      hasManualGrading: false,
      subjects: [],
      checks: [],
      knowledgeStats: [],
      parts: [
        {
          partId: "p-ord",
          name: "Phần đảo",
          order: 1,
          durationMinutes: 30,
          firstNumber: 1,
          lastNumber: 2,
          points: 2,
          maxPoints: null,
          blocks: [
            makeBlock("blk-ord", "Khối thứ tự", [
              {
                kind: "QUESTION",
                originalOrder: 2,
                question: makeQuestion(1, {
                  questionId: "q-orig-2",
                  code: "Q-SECOND",
                  originalNumber: 2,
                  type: "MATCHING",
                  content: "<p>Ghép đôi đảo</p>",
                  optionOrder: ["r-b", "r-a"],
                  originalOptionOrder: ["r-a", "r-b"],
                  body: {
                    type: "MATCHING",
                    left: [{ id: "l-1", content: "<p>Trái 1</p>" }],
                    right: [
                      { id: "r-b", content: "<p>Phải B</p>" },
                      { id: "r-a", content: "<p>Phải A</p>" },
                    ],
                    pairs: [{ leftId: "l-1", rightId: "r-a", points: 1 }],
                    reuseRight: false,
                  },
                }),
              },
              {
                kind: "QUESTION",
                originalOrder: 1,
                question: makeQuestion(2, {
                  questionId: "q-orig-1",
                  code: "Q-FIRST",
                  originalNumber: 1,
                  type: "SINGLE_CHOICE",
                  optionOrder: ["o-b", "o-a"],
                  originalOptionOrder: ["o-a", "o-b"],
                  body: {
                    type: "SINGLE_CHOICE",
                    options: [
                      { id: "o-b", content: "<p>Sau</p>", correct: false },
                      { id: "o-a", content: "<p>Đầu</p>", correct: true },
                    ],
                  },
                }),
              },
            ]),
          ],
        },
      ],
    };

    const { unmount } = renderWithProviders(<ExamPreviewPaper paper={paper} />);
    expect(screen.getAllByText(/^Q-(FIRST|SECOND) · v1$/).map((n) => n.textContent)).toEqual([
      "Q-SECOND · v1",
      "Q-FIRST · v1",
    ]);
    const matchShuffled = screen.getByRole("heading", { name: "Câu 1" }).closest("article")!;
    expect(
      matchShuffled
        .querySelector("tbody td:nth-child(3) [data-exam-content]")
        ?.textContent?.replace(/\s+/g, " ")
        .trim(),
    ).toBe("b.Phải A");
    unmount();

    renderWithProviders(<ExamPreviewPaper paper={paper} original />);
    expect(screen.getAllByText(/^Q-(FIRST|SECOND) · v1$/).map((n) => n.textContent)).toEqual([
      "Q-FIRST · v1",
      "Q-SECOND · v1",
    ]);
    const scOriginal = screen.getByRole("heading", { name: "Câu 1" }).closest("article")!;
    expect(
      [...scOriginal.querySelectorAll("fieldset[data-question-options] [data-exam-content]")].map((n) =>
        n.textContent?.trim(),
      ),
    ).toEqual(["Đầu", "Sau"]);
    const matchOriginal = screen.getByRole("heading", { name: "Câu 2" }).closest("article")!;
    expect(
      matchOriginal
        .querySelector("tbody td:nth-child(3) [data-exam-content]")
        ?.textContent?.replace(/\s+/g, " ")
        .trim(),
    ).toBe("a.Phải A");
  });

  it("bản en: nhãn danh mục song ngữ (pickName + fallback khi thiếu mã), nội dung đề nằm trong [data-exam-content]", async () => {
    const paper: PreviewPaper = {
      ...buildAllEightTypesPaper(),
      parts: [
        {
          ...buildAllEightTypesPaper().parts[0]!,
          blocks: [
            makeBlock("blk-en", "Khối tiếng Việt", [
              {
                kind: "QUESTION",
                question: makeQuestion(1, {
                  code: "EN-1",
                  gradeCode: "GRADE_12",
                  difficultyCode: "UNKNOWN_DIFF",
                  content: "<p>Nội dung câu tiếng Việt</p>",
                  knowledge: { nodeId: "k1", code: "K1", name: "Kiến thức tiếng Việt" },
                  body: {
                    type: "SINGLE_CHOICE",
                    options: [{ id: "a", content: "<p>Phương án A</p>", correct: true }],
                  },
                }),
              },
              {
                kind: "QUESTION",
                question: makeQuestion(2, {
                  code: "EN-2",
                  type: "ESSAY",
                  gradeCode: "GRADE_12",
                  difficultyCode: "APPLICATION",
                  content: "<p>Đề tự luận tiếng Việt</p>",
                  rubric: [{ id: "r1", description: "Tiêu chí tiếng Việt", points: 2 }],
                  body: null,
                }),
              },
            ]),
          ],
        },
      ],
    };

    const { container } = renderWithProviders(<ExamPreviewPaper paper={paper} />, {
      locale: "en",
      messages: en,
    });

    await waitFor(() => expect(screen.getAllByText("Grade 12").length).toBeGreaterThan(0));
    expect(screen.getByText(/Mathematics/)).toBeInTheDocument();
    expect(screen.getByText("Application")).toBeInTheDocument();
    expect(screen.getByText("UNKNOWN_DIFF")).toBeInTheDocument();
    expect(screen.getByText("Question 1")).toBeInTheDocument();
    expect(screen.getByText("Rubric")).toBeInTheDocument();

    // Mọi nội dung đề tiếng Việt đều nằm bên trong [data-exam-content].
    for (const examText of [
      "Phần tổng hợp tám dạng",
      "Khối tiếng Việt",
      "Nội dung câu tiếng Việt",
      "Phương án A",
      "Kiến thức tiếng Việt",
      "Tiêu chí tiếng Việt",
    ]) {
      const el = screen.getByText(examText);
      expect(el.closest("[data-exam-content]")).not.toBeNull();
    }

    // Ngoài [data-exam-content] không lẫn chữ Việt có dấu.
    const vietnameseRe =
      /[àáảãạăằắẳẵặâầấẩẫậèéẻẽẹêềếểễệìíỉĩịòóỏõọôồốổỗộơờớởỡợùúủũụưừứửữựỳýỷỹỵđÀÁẢÃẠĂẰẮẲẴẶÂẦẤẨẪẬÈÉẺẼẸÊỀẾỂỄỆÌÍỈĨỊÒÓỎÕỌÔỒỐỔỖỘƠỜỚỞỠỢÙÚỦŨỤƯỪỨỬỮỰỲÝỶỸỴĐ]/;
    const walker = document.createTreeWalker(container, NodeFilter.SHOW_TEXT);
    const leaked: string[] = [];
    while (walker.nextNode()) {
      const node = walker.currentNode as Text;
      if (!node.data.trim()) continue;
      if (node.parentElement?.closest("[data-exam-content]")) continue;
      if (vietnameseRe.test(node.data)) leaked.push(node.data.trim());
    }
    expect(leaked).toEqual([]);
  });
});

describe("dùng thành phần chung", () => {
  it("nhập Question/QuestionSet từ @thi360/ui/question và không còn mã tự dựng câu hỏi cũ", () => {
    const sourcePath = path.resolve(import.meta.dirname, "../components/exam-preview-paper.tsx");
    const source = readFileSync(sourcePath, "utf8");

    expect(source).toMatch(/from\s+["']@thi360\/ui\/question["']/);
    expect(source).toContain("Question");
    expect(source).toContain("QuestionSet");

    expect(source).not.toContain("@thi360/ui/rich-text");
    expect(source).not.toContain("RichTextView");
    expect(source).not.toContain("OPTION_KEYS");
    expect(source).not.toContain("dangerouslySetInnerHTML");
    expect(source).not.toMatch(/<ol[^>]*>[\s\S]*options\.map/);
  });
});
