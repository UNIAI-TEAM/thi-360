import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";
import { UI_STRINGS_VI } from "../../messages/ui-strings";
import { Live, questionSet, setChildA, setChildB } from "./fixtures";

const t = UI_STRINGS_VI.question;

describe("Bộ câu hỏi (AC-14)", () => {
  it("ngữ liệu hiện cùng các câu con, ghim cạnh ở màn rộng và xếp dọc ở màn hẹp", () => {
    const { container } = render(<Live question={questionSet} children={[setChildA, setChildB]} />);
    const passage = screen.getByLabelText(t.setPassageCaption(9, 10));
    expect(passage.textContent).toContain("Ngữ liệu chung");
    expect(passage).toHaveClass("lg:sticky");
    expect(passage.querySelector("[data-exam-content]")).not.toBeNull();
    // Dưới breakpoint lg lưới không có cột: các khối xếp dọc.
    const grid = passage.parentElement!;
    expect(grid.className).toContain("lg:grid-cols-");
    expect(grid.className).not.toMatch(/(^|\s)grid-cols-/);
    expect(screen.getByRole("heading", { name: t.questionLabel(9) })).toBeInTheDocument();
    expect(screen.getByRole("heading", { name: t.questionLabel(10) })).toBeInTheDocument();
    expect(container.querySelectorAll("fieldset,input[type=text],input:not([type])").length).toBeGreaterThan(0);
  });

  it("đáp án các câu con gom trong {children: {id: answer}}", async () => {
    const user = userEvent.setup();
    const onValue = vi.fn();
    render(<Live question={questionSet} children={[setChildA, setChildB]} onValue={onValue} />);
    await user.click(screen.getByRole("radio", { name: /Hà Nội/ }));
    expect(onValue).toHaveBeenLastCalledWith({ children: { "q-set-a": { optionId: "o1" } } });
    await user.type(screen.getByRole("textbox", { name: t.answerLabel }), "7");
    expect(onValue).toHaveBeenLastCalledWith({
      children: { "q-set-a": { optionId: "o1" }, "q-set-b": { text: "7" } },
    });
  });

  it("xem lại: kết quả từng câu con đọc từ review.children", () => {
    render(
      <Live
        question={questionSet}
        children={[setChildA, setChildB]}
        mode="review"
        initial={{ children: { "q-set-a": { optionId: "o1" }, "q-set-b": { text: "42" } } }}
        review={{
          outcome: "PARTIAL",
          earned: 1,
          max: 2,
          children: {
            "q-set-a": { outcome: "CORRECT", earned: 1, max: 1 },
            "q-set-b": { outcome: "WRONG", earned: 0, max: 1 },
          },
        }}
      />,
    );
    expect(screen.getByText(t.outcomes.CORRECT)).toBeInTheDocument();
    expect(screen.getByText(t.outcomes.WRONG)).toBeInTheDocument();
  });
});
