import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";
import { UI_STRINGS_VI } from "../../messages/ui-strings";
import { Live, trueFalse } from "./fixtures";

const t = UI_STRINGS_VI.question;

describe("Câu Đúng/Sai (AC-11)", () => {
  it("từng ý chọn Đúng, Sai, bỏ chọn về null; các ý khác không đổi", async () => {
    const user = userEvent.setup();
    const onValue = vi.fn();
    render(<Live question={trueFalse} onValue={onValue} />);
    const groups = screen.getAllByRole("radiogroup");
    expect(groups).toHaveLength(2);
    const [first, second] = groups;
    const pick = (group: HTMLElement, label: string) => {
      const radios = [...group.querySelectorAll("label")].find((l) => l.textContent === label)!;
      return radios.querySelector("input")!;
    };
    await user.click(pick(first!, t.trueOption));
    expect(onValue).toHaveBeenLastCalledWith({ statements: { s1: true } });
    await user.click(pick(second!, t.falseOption));
    expect(onValue).toHaveBeenLastCalledWith({ statements: { s1: true, s2: false } });
    await user.click(pick(first!, t.falseOption));
    expect(onValue).toHaveBeenLastCalledWith({ statements: { s1: false, s2: false } });
    await user.click(pick(first!, t.falseOption));
    expect(onValue).toHaveBeenLastCalledWith({ statements: { s1: null, s2: false } });
  });
});
