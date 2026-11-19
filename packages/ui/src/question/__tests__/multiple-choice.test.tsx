import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";
import { UI_STRINGS_VI } from "../../messages/ui-strings";
import { Live, multiple } from "./fixtures";

describe("Câu Nhiều đáp án (AC-8)", () => {
  it("tích, bỏ tích; giá trị {optionIds}, bỏ hết là mảng rỗng", async () => {
    const user = userEvent.setup();
    const onValue = vi.fn();
    render(<Live question={multiple} onValue={onValue} />);
    await user.click(screen.getByRole("checkbox", { name: /^A\. 2/ }));
    expect(onValue).toHaveBeenLastCalledWith({ optionIds: ["m1"] });
    await user.click(screen.getByRole("checkbox", { name: /^C\. 7/ }));
    expect(onValue).toHaveBeenLastCalledWith({ optionIds: ["m1", "m3"] });
    await user.click(screen.getByRole("checkbox", { name: /^A\. 2/ }));
    expect(onValue).toHaveBeenLastCalledWith({ optionIds: ["m3"] });
    await user.click(screen.getByRole("checkbox", { name: /^C\. 7/ }));
    expect(onValue).toHaveBeenLastCalledWith({ optionIds: [] });
  });

  it("có dòng nhắc chọn các phương án đúng", () => {
    render(<Live question={multiple} />);
    expect(screen.getAllByText(UI_STRINGS_VI.question.chooseMultiple).length).toBeGreaterThan(0);
  });
});
