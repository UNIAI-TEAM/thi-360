import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { UI_STRINGS_VI } from "../../messages/ui-strings";
import { all, cloze, essay, Live, matching, multiple, questionSet, setChildA, setChildB, shortAnswer, single, trueFalse } from "./fixtures";
import type { QuestionReview } from "../types";

const t = UI_STRINGS_VI.question;

const graded: QuestionReview = { outcome: "CORRECT", earned: 1, max: 1 };

function noEditableControls(container: HTMLElement) {
  expect(container.querySelectorAll("textarea, select, input[type=radio], input[type=checkbox]")).toHaveLength(0);
  expect(screen.queryAllByRole("combobox")).toHaveLength(0);
  expect(screen.queryAllByRole("checkbox")).toHaveLength(0);
  for (const input of container.querySelectorAll("input")) expect(input).toHaveAttribute("readonly");
}

describe("Chế độ xem lại (AC-16)", () => {
  it.each(all.map((entry) => [entry.question.body.type, entry] as const))(
    "%s: không sửa được",
    (_type, { question, value }) => {
      const { container } = render(
        <Live question={question} mode="review" initial={value} review={{ ...graded, solutionHtml: null }} />,
      );
      noEditableControls(container);
    },
  );

  it("Một đáp án: hiện phương án đã chọn và đáp án đúng", () => {
    render(<Live question={single} mode="review" initial={{ optionId: "o2" }} review={{ outcome: "WRONG", earned: 0, max: 1 }} />);
    expect(screen.getByText(t.correctAnswerTag)).toBeInTheDocument();
    expect(screen.getByText(t.yourPickWrongTag)).toBeInTheDocument();
  });

  it("Nhiều đáp án: đáp án đúng mà chưa chọn được nhắc riêng", () => {
    render(<Live question={multiple} mode="review" initial={{ optionIds: ["m1"] }} review={{ outcome: "PARTIAL", earned: 0.5, max: 1 }} />);
    expect(screen.getAllByText(t.correctAnswerTag)).toHaveLength(2);
    expect(screen.getByText(t.notPickedTag)).toBeInTheDocument();
  });

  it("Trả lời ngắn: hiện câu trả lời đã nộp và đáp án chuẩn", () => {
    render(<Live question={shortAnswer} mode="review" initial={{ text: "41" }} review={{ outcome: "WRONG", earned: 0, max: 1 }} />);
    expect(screen.getByDisplayValue("41")).toHaveAttribute("readonly");
    expect(screen.getByText("42")).toBeInTheDocument();
  });

  it("Điền khuyết: bảng từng ô có đáp án chấp nhận và điểm từng ô khi có", async () => {
    render(
      <Live
        question={cloze}
        mode="review"
        initial={{ blanks: { b1: "100", b2: "5" } }}
        review={{
          outcome: "PARTIAL",
          earned: 1,
          max: 2,
          gradingDetail: {
            b1: { key: "b1", outcome: "CORRECT", earned: 1, max: 1 },
            b2: { key: "b2", outcome: "WRONG", earned: 0, max: 1 },
          },
        }}
      />,
    );
    expect(await screen.findByRole("columnheader", { name: t.acceptedCol })).toBeInTheDocument();
    expect(screen.getByRole("columnheader", { name: t.pointsCol })).toBeInTheDocument();
  });

  it("Đúng/Sai: bảng ý, bạn chọn và đáp án; không điểm từng ý thì không hiện số 0", () => {
    const { container } = render(
      <Live question={trueFalse} mode="review" initial={{ statements: { s1: true, s2: true } }} review={{ outcome: "BLANK" }} />,
    );
    expect(screen.getByRole("columnheader", { name: t.answerCol })).toBeInTheDocument();
    expect(screen.getByText(t.noPointsYet)).toBeInTheDocument();
    const cells = [...container.querySelectorAll("td")].map((td) => td.textContent);
    expect(cells.some((text) => text === "0" || text?.startsWith("0 /"))).toBe(false);
  });

  it("Đúng/Sai: điểm từng ý hiện khi có, kể cả 0", () => {
    render(
      <Live
        question={trueFalse}
        mode="review"
        initial={{ statements: { s1: true, s2: true } }}
        review={{
          outcome: "PARTIAL",
          earned: 0.25,
          max: 0.5,
          gradingDetail: {
            s1: { key: "s1", outcome: "CORRECT", earned: 0.25, max: 0.25 },
            s2: { key: "s2", outcome: "WRONG", earned: 0, max: 0.25 },
          },
        }}
      />,
    );
    expect(screen.getByText(t.points("0", "0,25"))).toBeInTheDocument();
  });

  it("Ghép đôi: hiện cặp đã chọn, cặp đúng; chưa ghép thì nói chưa ghép", () => {
    render(<Live question={matching} mode="review" initial={{ pairs: { l1: "r3", l2: null } }} review={{ outcome: "WRONG" }} />);
    expect(screen.getByText(t.notMatched)).toBeInTheDocument();
    expect(screen.getAllByText(/Áp suất/).length).toBeGreaterThan(0);
  });

  it("Ẩn đáp án khi kết quả chưa cho xem đáp án", () => {
    render(<Live question={single} mode="review" initial={{ optionId: "o2" }} review={{ outcome: "WRONG", earned: 0, max: 1, showAnswers: false }} />);
    expect(screen.queryByText(t.correctAnswerTag)).not.toBeInTheDocument();
  });

  it("Tự luận: chờ chấm, đã chấm, đang chấm lại, bỏ trống", () => {
    const html = "<p>Người lính trẻ.</p>";
    const { rerender } = render(
      <Live key="a" question={essay} mode="review" initial={{ html, wordCount: 3 }} review={{ outcome: "PENDING_REVIEW", essay: { state: "PENDING" } }} />,
    );
    expect(screen.getByText(t.pendingReviewNote)).toBeInTheDocument();
    rerender(
      <Live key="b" question={essay} mode="review" initial={{ html, wordCount: 3 }} review={{ outcome: "CORRECT", earned: 20, max: 25, essay: { state: "GRADED", comment: "Tốt", criteria: [{ description: "Ý", earned: 5, max: 10 }] } }} />,
    );
    expect(screen.getByText(t.gradingCommentLabel)).toBeInTheDocument();
    expect(screen.getByText("Tốt")).toBeInTheDocument();
    rerender(
      <Live key="c" question={essay} mode="review" initial={{ html, wordCount: 3 }} review={{ outcome: "CORRECT", earned: 20, max: 25, essay: { state: "REGRADING" } }} />,
    );
    expect(screen.getByText(t.regrading)).toBeInTheDocument();
    rerender(<Live key="d" question={essay} mode="review" initial={{ html: "", wordCount: 0 }} review={{ outcome: "BLANK" }} />);
    expect(screen.getByText(t.notAnswered)).toBeInTheDocument();
  });

  it("Bộ câu hỏi: không sửa được ở cả hai câu con", () => {
    const { container } = render(
      <Live
        question={questionSet}
        children={[setChildA, setChildB]}
        mode="review"
        initial={{ children: { "q-set-a": { optionId: "o1" }, "q-set-b": { text: "42" } } }}
        review={{ outcome: "CORRECT", earned: 2, max: 2 }}
      />,
    );
    noEditableControls(container);
  });
});
