import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";
import { UI_STRINGS_VI } from "../../messages/ui-strings";
import { Live, shortAnswer } from "./fixtures";

describe("Câu Trả lời ngắn (AC-9)", () => {
  it("giá trị {text} theo từng ký tự gõ", async () => {
    const user = userEvent.setup();
    const onValue = vi.fn();
    render(<Live question={shortAnswer} onValue={onValue} />);
    await user.type(screen.getByRole("textbox", { name: UI_STRINGS_VI.question.answerLabel }), "42");
    expect(onValue).toHaveBeenLastCalledWith({ text: "42" });
  });

  it("có hướng dẫn nhập và không có dòng dấu phẩy hoặc dấu chấm đều được", () => {
    const { container } = render(<Live question={shortAnswer} />);
    expect(container.textContent).toContain("Số nguyên");
    expect(container.textContent).not.toMatch(/dấu phẩy|dấu chấm/i);
  });

  // Outcome khác BLANK: nhánh `text.trim() === ""` mới là thứ làm hiện "Chưa trả lời" (R1-17).
  it("chuỗi chỉ có khoảng trắng tính là bỏ trống ở màn xem lại", () => {
    render(
      <Live
        question={shortAnswer}
        mode="review"
        initial={{ text: "   " }}
        review={{ outcome: "WRONG", earned: 0, max: 1 }}
      />,
    );
    expect(screen.getByText(UI_STRINGS_VI.question.notAnswered)).toBeInTheDocument();
  });
});
