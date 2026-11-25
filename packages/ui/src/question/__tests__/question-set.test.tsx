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
    expect(passage).toHaveClass(
      "lg:sticky",
      "lg:top-[var(--attempt-sticky-top,1rem)]",
      "lg:max-h-[calc(100dvh-var(--attempt-sticky-top,1rem)-1.5rem)]",
      "lg:overflow-y-auto",
      "scroll-shadows-y",
    );
    expect(passage.querySelector("[data-exam-content]")).not.toBeNull();
    // Dưới breakpoint lg lưới không có cột: các khối xếp dọc; từ lg chia ~46% ngữ liệu : ~54% câu hỏi (7fr:8fr), gap 16px.
    const grid = passage.parentElement!;
    expect(grid).toHaveClass("gap-4", "lg:grid-cols-[minmax(0,7fr)_minmax(0,8fr)]");
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

  // R1-10: cờ lưu theo từng attempt_item, mà câu con là mục có số; cờ của con phải đi qua `Question`.
  it("cờ đánh dấu của từng câu con: gọi lại với id của con, trạng thái đọc theo flaggedIds", async () => {
    const user = userEvent.setup();
    const onToggleChildFlag = vi.fn();
    render(
      <Live
        question={questionSet}
        children={[setChildA, setChildB]}
        flaggedIds={{ "q-set-b": true }}
        onToggleChildFlag={onToggleChildFlag}
      />,
    );
    const flags = screen.getAllByRole("button", { name: /Đánh dấu xem lại|Bỏ đánh dấu/ });
    expect(flags).toHaveLength(2);
    expect(flags[0]).toHaveAttribute("aria-pressed", "false");
    expect(flags[1]).toHaveAttribute("aria-pressed", "true");
    await user.click(flags[0]!);
    expect(onToggleChildFlag).toHaveBeenLastCalledWith("q-set-a");
    await user.click(flags[1]!);
    expect(onToggleChildFlag).toHaveBeenLastCalledWith("q-set-b");
  });

  it("không truyền onToggleChildFlag thì câu con không có nút cờ", () => {
    render(<Live question={questionSet} children={[setChildA, setChildB]} />);
    expect(screen.queryByRole("button", { name: t.flagForReview })).toBeNull();
  });

  // R1-16: review của con thiếu (FS-6 chỉ ánh xạ dòng của chùm) không được bịa chip "Bỏ trống".
  it("xem lại thiếu review.children: câu con không có chip kết quả bịa ra", () => {
    render(
      <Live
        question={questionSet}
        children={[setChildA, setChildB]}
        mode="review"
        initial={{ children: { "q-set-a": { optionId: "o1" }, "q-set-b": { text: "42" } } }}
        review={{ outcome: "CORRECT", earned: 2, max: 2 }}
      />,
    );
    expect(screen.queryByText(t.outcomes.BLANK)).toBeNull();
  });

  it("thiếu review của con vẫn giữ showAnswers=false của chùm: không lộ đáp án", () => {
    render(
      <Live
        question={questionSet}
        children={[setChildA, setChildB]}
        mode="review"
        initial={{ children: { "q-set-a": { optionId: "o2" }, "q-set-b": { text: "41" } } }}
        review={{ outcome: "WRONG", earned: 0, max: 2, showAnswers: false }}
      />,
    );
    expect(screen.queryByText(t.correctAnswerTag)).toBeNull();
    expect(screen.queryByText(t.correctAnswerPrefix)).toBeNull();
  });
});
