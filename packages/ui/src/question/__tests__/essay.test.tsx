import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";
import { UI_STRINGS_VI } from "../../messages/ui-strings";
import { essay, Live } from "./fixtures";

const t = UI_STRINGS_VI.question;

describe("Câu Tự luận (AC-13)", () => {
  it("giá trị {html, wordCount}, bộ đếm theo từng lần gõ", async () => {
    const user = userEvent.setup();
    const onValue = vi.fn();
    render(<Live question={essay} onValue={onValue} />);
    await user.type(screen.getByRole("textbox", { name: t.essayEditorLabel }), "Người lính trẻ");
    expect(onValue).toHaveBeenLastCalledWith({ html: "<p>Người lính trẻ</p>", wordCount: 3 });
    expect(screen.getByRole("status")).toHaveTextContent(t.wordCount(3));
  });

  it("wordLimit là tối thiểu: chưa đủ thì nhắc, đủ thì hết nhắc, không chặn gõ", async () => {
    const user = userEvent.setup();
    render(<Live question={essay} />);
    const box = screen.getByRole("textbox", { name: t.essayEditorLabel });
    expect(screen.getByText(new RegExp(t.wordLimit(5).replace(/[.*+?^${}()|[\]\\]/g, "\\$&")))).toBeInTheDocument();
    await user.type(box, "một hai ba bốn");
    expect(box).toBeEnabled();
    expect(screen.queryByText(t.wordLimitMet)).not.toBeInTheDocument();
    await user.type(box, " năm");
    expect(screen.getByText(t.wordLimitMet)).toBeInTheDocument();
  });

  it("ký tự đặc biệt được thoát trong HTML lưu và không tính là chữ", async () => {
    const user = userEvent.setup();
    const onValue = vi.fn();
    render(<Live question={essay} onValue={onValue} />);
    await user.type(screen.getByRole("textbox", { name: t.essayEditorLabel }), "a < b & c");
    expect(onValue).toHaveBeenLastCalledWith({ html: "<p>a &lt; b &amp; c</p>", wordCount: 3 });
  });

  it("chỗ cắm trình soạn thay được ô nhập mặc định", async () => {
    const user = userEvent.setup();
    const onValue = vi.fn();
    render(
      <Live
        question={essay}
        onValue={onValue}
        renderEditor={(props) => (
          <div
            role="textbox"
            aria-label={props["aria-label"]}
            contentEditable
            suppressContentEditableWarning
            data-testid="plug"
            onInput={(event) => props.onChange(`<p>${event.currentTarget.textContent}</p>`)}
          />
        )}
      />,
    );
    expect(screen.getByTestId("plug")).toBeInTheDocument();
    await user.type(screen.getByTestId("plug"), "x y");
    expect(onValue).toHaveBeenCalled();
    expect(onValue.mock.lastCall![0].wordCount).toBe(2);
  });
});
