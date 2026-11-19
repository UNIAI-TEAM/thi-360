import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { UI_STRINGS_VI } from "../../messages/ui-strings";
import type { AnswerValue, KitQuestion, QuestionReview } from "../types";
import { all, Live, questionSet, setChildA, setChildB } from "./fixtures";

const t = UI_STRINGS_VI.question;
const SOLUTION = "<p>Lời giải mẫu: lấy 7 nhân 6.</p>";

const cases: { name: string; question: KitQuestion; value: AnswerValue; children?: KitQuestion[] }[] = [
  ...all.map(({ question, value }) => ({ name: question.body.type, question, value })),
  {
    name: "QUESTION_SET",
    question: questionSet,
    value: { children: { "q-set-a": { optionId: "o1" }, "q-set-b": { text: "42" } } },
    children: [setChildA, setChildB],
  },
];

function review(extra: Partial<QuestionReview>): QuestionReview {
  return {
    outcome: "CORRECT",
    earned: 1,
    max: 1,
    essay: { state: "GRADED" },
    ...extra,
  };
}

describe("Lời giải soạn sẵn ở chế độ xem lại, mọi dạng câu (R1-11, AD-11)", () => {
  it("phủ đủ 8 dạng câu", () => {
    expect(cases.map((c) => c.name).sort()).toEqual(
      ["CLOZE", "ESSAY", "MATCHING", "MULTIPLE_CHOICE", "QUESTION_SET", "SHORT_ANSWER", "SINGLE_CHOICE", "TRUE_FALSE"].sort(),
    );
  });

  it.each(cases)("$name: có solutionHtml thì hiện khối Lời giải đúng một lần, qua RichTextView", ({ question, value, children }) => {
    const { container } = render(
      <Live question={question} children={children} mode="review" initial={value} review={review({ solutionHtml: SOLUTION })} />,
    );
    expect(screen.getAllByText(t.solutionLabel)).toHaveLength(1);
    const body = screen.getByText(/Lời giải mẫu/);
    expect(body.closest("[data-exam-content]")).not.toBeNull();
    // Nhãn là chữ giao diện: nằm ngoài vùng nội dung đề.
    expect(screen.getByText(t.solutionLabel).closest("[data-exam-content]")).toBeNull();
    expect(container.querySelectorAll(".rich-text-body")).not.toHaveLength(0);
  });

  it.each(cases)("$name: không có solutionHtml thì không dựng khối nào", ({ question, value, children }) => {
    const { rerender } = render(
      <Live key="none" question={question} children={children} mode="review" initial={value} review={review({})} />,
    );
    expect(screen.queryByText(t.solutionLabel)).toBeNull();
    rerender(
      <Live key="null" question={question} children={children} mode="review" initial={value} review={review({ solutionHtml: null })} />,
    );
    expect(screen.queryByText(t.solutionLabel)).toBeNull();
    rerender(
      <Live key="empty" question={question} children={children} mode="review" initial={value} review={review({ solutionHtml: "" })} />,
    );
    expect(screen.queryByText(t.solutionLabel)).toBeNull();
  });

  it.each(cases)("$name: result_view không cho đáp án (showAnswers=false) thì không hiện lời giải", ({ question, value, children }) => {
    render(
      <Live
        question={question}
        children={children}
        mode="review"
        initial={value}
        review={review({ solutionHtml: SOLUTION, showAnswers: false })}
      />,
    );
    expect(screen.queryByText(t.solutionLabel)).toBeNull();
    expect(screen.queryByText(/Lời giải mẫu/)).toBeNull();
  });

  it("chế độ làm bài không hiện lời giải dù có dữ liệu", () => {
    const { question, value } = cases[0]!;
    render(<Live question={question} initial={value} review={review({ solutionHtml: SOLUTION })} />);
    expect(screen.queryByText(t.solutionLabel)).toBeNull();
  });
});
