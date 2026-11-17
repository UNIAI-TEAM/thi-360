import { screen, waitFor, within } from "@testing-library/react";
import { createRef } from "react";
import { describe, expect, it } from "vitest";
import { renderWithProviders } from "@/test/render";
import { QuestionPreview } from "../components/question-preview";
import type { ImportQuestionDraft, ImportRowResult } from "../types";

const HTML_CELL = "<p>a<br>b</p>";
const FORMULA_CELL = "<p>Tính <span class=\"math-inline\">\\(x^2\\)</span></p>";

function draft(overrides: Partial<ImportQuestionDraft>): ImportQuestionDraft {
  return {
    type: null,
    content: "",
    options: [],
    statements: [],
    acceptedAnswers: [],
    scoringRule: null,
    trueFalseScoring: null,
    blanks: [],
    matching: null,
    commonMistakes: null,
    taskTypeCode: null,
    points: null,
    inputHint: null,
    gradingGuide: null,
    rubric: [],
    sampleAnswer: null,
    solution: null,
    subjectCode: null,
    gradeCode: null,
    difficultyCode: null,
    knowledge: [],
    tagCodes: [],
    setCode: null,
    ...overrides,
  };
}

function row(rowNumber: number, overrides: Partial<ImportRowResult> & { draft: ImportQuestionDraft }): ImportRowResult {
  return {
    rowNumber,
    sourceCode: null,
    status: "VALID",
    issues: [],
    duplicateOf: null,
    isSetPassage: false,
    parentRowNumber: null,
    ...overrides,
  };
}

function renderPreview(target: ImportRowResult, rows: ImportRowResult[] = [target]) {
  return renderWithProviders(
    <QuestionPreview
      row={target}
      rows={rows}
      reference={undefined}
      headingRef={createRef<HTMLHeadingElement>()}
      describe={() => ({ message: "", fix: "" })}
    />,
  );
}

describe("QuestionPreview with HTML cells", () => {
  it("renders content, options, statements, set children and solution as HTML, not as raw tags", async () => {
    const passage = row(1, {
      isSetPassage: true,
      draft: draft({
        type: "QUESTION_SET",
        content: FORMULA_CELL,
        solution: HTML_CELL,
      }),
    });
    const single = row(2, {
      parentRowNumber: 1,
      draft: draft({
        type: "SINGLE_CHOICE",
        content: HTML_CELL,
        options: [
          { letter: "A", content: FORMULA_CELL, correct: true },
          { letter: "B", content: HTML_CELL, correct: false },
        ],
      }),
    });
    const trueFalse = row(3, {
      parentRowNumber: 1,
      draft: draft({
        type: "TRUE_FALSE",
        content: HTML_CELL,
        statements: [{ letter: "A", content: FORMULA_CELL, answer: true }],
      }),
    });
    const rows = [passage, single, trueFalse];

    const { container } = renderPreview(passage, rows);

    await waitFor(() => expect(container.querySelectorAll(".katex").length).toBeGreaterThan(0));
    expect(container.textContent).not.toMatch(/<\/?(p|br|span)\b/);
    // Lời giải của dòng ngữ liệu và đề bài hai câu con: mỗi ô một `<br>` thành ngắt dòng, không lộ thẻ.
    expect(container.querySelectorAll("br")).toHaveLength(3);
    // Đề bài ngữ liệu có công thức do KaTeX dựng từ đúng LaTeX gốc.
    expect(container.querySelector(".katex-mathml annotation")?.textContent).toBe("x^2");
    expect(screen.getByText("Câu con (2)")).toBeInTheDocument();
  });

  it("renders option and statement HTML of the selected row without raw tags", async () => {
    const single = row(2, {
      draft: draft({
        type: "SINGLE_CHOICE",
        content: HTML_CELL,
        options: [
          { letter: "A", content: FORMULA_CELL, correct: true },
          { letter: "B", content: HTML_CELL, correct: false },
        ],
        solution: HTML_CELL,
      }),
    });

    const { container } = renderPreview(single);

    const options = await screen.findByRole("list", { name: "Các phương án" });
    await waitFor(() => expect(options.querySelector(".katex")).toBeTruthy());
    expect(options.textContent).not.toContain("<");
    expect(within(options).getAllByRole("listitem")).toHaveLength(2);
    expect(container.textContent).not.toMatch(/<\/?(p|br|span)\b/);
    // Đề bài + lựa chọn B + lời giải: ba ô có `<br>` thành ngắt dòng.
    expect(container.querySelectorAll("br")).toHaveLength(3);
  });

  it("shows plain text, not tags, where matching items are interpolated into messages", async () => {
    const matching = row(4, {
      draft: draft({
        type: "MATCHING",
        content: HTML_CELL,
        matching: {
          left: [{ key: "1", content: HTML_CELL }],
          right: [{ key: "A", content: FORMULA_CELL }],
          pairs: [{ leftKey: "1", rightKey: "A" }],
          reuseRight: false,
        },
      }),
    });

    const { container } = renderPreview(matching);

    await screen.findByText(/Lựa chọn bên phải:/);
    expect(container.textContent).not.toMatch(/<\/?(p|br|span)\b/);
    // `pairWith` và `rightChoices` nhận chữ thuần của HTML: không thẻ, còn nguyên dấu `\( … \)` của công thức.
    expect(container.textContent).toContain("ghép với A. Tính \\(x^2\\)");
    expect(container.textContent).toContain("Lựa chọn bên phải: A. Tính \\(x^2\\)");
    // Đề bài và mục bên trái là HTML: mỗi ô một `<br>` thành ngắt dòng.
    expect(container.querySelectorAll("br")).toHaveLength(2);
  });
});
