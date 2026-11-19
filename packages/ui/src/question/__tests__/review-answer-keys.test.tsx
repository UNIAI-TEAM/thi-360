import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { UI_STRINGS_VI } from "../../messages/ui-strings";
import type { AnswerValue, KitQuestion } from "../types";
import { Live, multiple, single, trueFalse } from "./fixtures";

const t = UI_STRINGS_VI.question;

/** Bỏ khóa đáp án khỏi dữ liệu: FS-6/FA-4 tự ánh xạ API nên thiếu một trường là có thể xảy ra. */
function withoutKeys(question: KitQuestion): KitQuestion {
  const body = question.body;
  if (body.type === "SINGLE_CHOICE" || body.type === "MULTIPLE_CHOICE") {
    const options = body.options.map((option) => {
      const copy = { ...option };
      delete copy.correct;
      return copy;
    });
    return { ...question, body: { ...body, options } };
  }
  if (body.type === "TRUE_FALSE") {
    const statements = body.statements.map((statement) => {
      const copy = { ...statement };
      delete copy.answer;
      return copy;
    });
    return { ...question, body: { ...body, statements } };
  }
  return question;
}

describe("Review khi thiếu khóa đáp án (R1-2)", () => {
  it("Đúng/Sai: không có `answer` thì không hiện cột Đáp án (không tự suy ra \"Sai\")", () => {
    render(
      <Live
        question={withoutKeys(trueFalse)}
        mode="review"
        initial={{ statements: { s1: true, s2: false } }}
        review={{ outcome: "WRONG", earned: 0, max: 1 }}
      />,
    );
    expect(screen.queryByRole("columnheader", { name: t.answerCol })).toBeNull();
  });

  it("Đúng/Sai: có `answer` thì vẫn hiện cột Đáp án mặc định", () => {
    render(
      <Live question={trueFalse} mode="review" initial={{ statements: { s1: true, s2: false } }} review={{ outcome: "CORRECT", earned: 1, max: 1 }} />,
    );
    expect(screen.getByRole("columnheader", { name: t.answerCol })).toBeInTheDocument();
  });

  const choiceCases: [string, KitQuestion, AnswerValue][] = [
    ["Một đáp án", single, { optionId: "o2" }],
    ["Nhiều đáp án", multiple, { optionIds: ["m2"] }],
  ];
  it.each(choiceCases)("%s: không có `correct` thì không hiện chip \"Bạn chọn · Sai\" hay \"Đáp án đúng\"", (_name, question, initial) => {
    render(<Live question={withoutKeys(question)} mode="review" initial={initial} review={{ outcome: "WRONG", earned: 0, max: 1 }} />);
    expect(screen.queryByText(t.yourPickWrongTag)).toBeNull();
    expect(screen.queryByText(t.correctAnswerTag)).toBeNull();
    expect(screen.getByText(t.yourPickTag)).toBeInTheDocument();
  });
});
