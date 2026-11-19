import { render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";
import { UI_STRINGS_VI } from "../../messages/ui-strings";
import { Live, matching } from "./fixtures";

const t = UI_STRINGS_VI.question;

describe("Câu Ghép đôi (AC-12)", () => {
  it("chọn mục cột phải bằng danh sách chọn, bỏ cặp về null, không cần kéo thả", async () => {
    const user = userEvent.setup();
    const onValue = vi.fn();
    render(<Live question={matching} onValue={onValue} />);
    const first = screen.getByRole("combobox", { name: t.matchPickLeft(1) });
    await user.click(first);
    await user.click(await screen.findByRole("option", { name: /Áp suất/ }));
    expect(onValue).toHaveBeenLastCalledWith({ pairs: { l1: "r1" } });
    await user.click(screen.getByRole("button", { name: t.matchClear(1) }));
    expect(onValue).toHaveBeenLastCalledWith({ pairs: { l1: null } });
  });

  it("mục cột phải đã ghép với mục khác bị khóa khi không cho dùng lại", async () => {
    const user = userEvent.setup();
    render(<Live question={matching} initial={{ pairs: { l1: "r1" } }} />);
    await user.click(screen.getByRole("combobox", { name: t.matchPickLeft(2) }));
    const listbox = await screen.findByRole("listbox");
    expect(within(listbox).getByRole("option", { name: /Áp suất/ })).toHaveAttribute("aria-disabled", "true");
    expect(within(listbox).getByRole("option", { name: /Năng lượng/ })).not.toHaveAttribute("aria-disabled", "true");
  });

  it("cách bấm hai lượt (cho màn cảm ứng) cũng ra cặp đúng định dạng", async () => {
    const user = userEvent.setup();
    const onValue = vi.fn();
    render(<Live question={matching} matchUi="pick" onValue={onValue} />);
    await user.click(screen.getByRole("button", { name: t.matchPickLeft(2) }));
    const rightRows = screen.getAllByRole("button", { name: t.matchWith });
    await user.click(rightRows[1]!);
    expect(onValue).toHaveBeenLastCalledWith({ pairs: { l2: "r2" } });
  });
});
