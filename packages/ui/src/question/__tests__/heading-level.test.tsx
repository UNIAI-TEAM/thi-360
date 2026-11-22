import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { UI_STRINGS_VI } from "../../messages/ui-strings";
import { Question } from "../question";
import { questionSet, setChildA, setChildB, single } from "./fixtures";

const t = UI_STRINGS_VI.question;

describe("headingLevel 1..6 (AD-178)", () => {
  it.each([4, 5, 6] as const)("câu đơn hỗ trợ headingLevel=%i", (level) => {
    render(<Question question={single} mode="review" headingLevel={level} />);
    const heading = screen.getByRole("heading", { level, name: t.questionLabel(1) });
    expect(heading.tagName).toBe(`H${level}`);
  });

  it("QuestionSet headingLevel=5 đặt tiêu đề bộ h5 và câu con h6", () => {
    render(
      <Question question={questionSet} mode="review" headingLevel={5}>
        {[setChildA, setChildB]}
      </Question>,
    );
    expect(screen.getByRole("heading", { level: 5, name: t.setPassageCaption(9, 10) }).tagName).toBe("H5");
    expect(screen.getByRole("heading", { level: 6, name: t.questionLabel(9) }).tagName).toBe("H6");
    expect(screen.getByRole("heading", { level: 6, name: t.questionLabel(10) }).tagName).toBe("H6");
  });

  it("QuestionSet headingLevel=6 kẹp câu con ở h6 (không vượt h6)", () => {
    render(
      <Question question={questionSet} mode="review" headingLevel={6}>
        {[setChildA, setChildB]}
      </Question>,
    );
    expect(screen.getByRole("heading", { level: 6, name: t.setPassageCaption(9, 10) }).tagName).toBe("H6");
    expect(screen.getByRole("heading", { level: 6, name: t.questionLabel(9) }).tagName).toBe("H6");
    expect(screen.getByRole("heading", { level: 6, name: t.questionLabel(10) }).tagName).toBe("H6");
  });
});
