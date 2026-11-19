import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";
import { UI_STRINGS_VI } from "../../messages/ui-strings";
import { cloze, essay, Live, matching, multiple, shortAnswer, single, trueFalse } from "./fixtures";

const t = UI_STRINGS_VI.question;

describe("Bàn phím và tên truy cập (AC-17)", () => {
  it("Một đáp án: Tab vào nhóm, Space chọn, Space lại bỏ chọn", async () => {
    const user = userEvent.setup();
    const onValue = vi.fn();
    render(<Live question={single} onValue={onValue} />);
    await user.tab();
    await user.keyboard(" ");
    expect(onValue).toHaveBeenLastCalledWith({ optionId: "o1" });
    await user.keyboard(" ");
    expect(onValue).toHaveBeenLastCalledWith({ optionId: null });
    expect(screen.getByRole("group", { name: t.questionLabel(1) })).toBeInTheDocument();
  });

  it("Nhiều đáp án: Tab rồi Space tích, mỗi ô có tên", async () => {
    const user = userEvent.setup();
    const onValue = vi.fn();
    render(<Live question={multiple} onValue={onValue} />);
    await user.tab();
    await user.keyboard(" ");
    expect(onValue).toHaveBeenLastCalledWith({ optionIds: ["m1"] });
    for (const box of screen.getAllByRole("checkbox")) expect(box).toHaveAccessibleName();
  });

  it("Trả lời ngắn: Tab vào ô và gõ", async () => {
    const user = userEvent.setup();
    const onValue = vi.fn();
    render(<Live question={shortAnswer} onValue={onValue} />);
    await user.tab();
    await user.keyboard("42");
    expect(onValue).toHaveBeenLastCalledWith({ text: "42" });
    expect(screen.getByRole("textbox")).toHaveAccessibleName(t.answerLabel);
  });

  it("Điền khuyết: Tab đi qua từng ô, mỗi ô có tên", async () => {
    const user = userEvent.setup();
    const onValue = vi.fn();
    render(<Live question={cloze} onValue={onValue} />);
    await waitFor(() => expect(screen.getAllByRole("textbox")).toHaveLength(2));
    await user.tab();
    await user.keyboard("1");
    await user.tab();
    await user.keyboard("2");
    expect(onValue).toHaveBeenLastCalledWith({ blanks: { b1: "1", b2: "2" } });
    for (const box of screen.getAllByRole("textbox")) expect(box).toHaveAccessibleName();
  });

  it("Đúng/Sai: bàn phím chọn Đúng cho ý đầu, mỗi nhóm có tên", async () => {
    const user = userEvent.setup();
    const onValue = vi.fn();
    render(<Live question={trueFalse} onValue={onValue} />);
    for (const group of screen.getAllByRole("radiogroup")) expect(group).toHaveAccessibleName();
    await user.tab();
    await user.keyboard(" ");
    expect(onValue).toHaveBeenLastCalledWith({ statements: { s1: true } });
  });

  it("Ghép đôi: mở danh sách chọn, mũi tên, Enter chọn; bỏ cặp bằng nút có tên", async () => {
    const user = userEvent.setup();
    const onValue = vi.fn();
    render(<Live question={matching} onValue={onValue} />);
    const trigger = screen.getByRole("combobox", { name: t.matchPickLeft(1) });
    trigger.focus();
    await user.keyboard("{Enter}");
    await screen.findByRole("listbox");
    await user.keyboard("{ArrowDown}{Enter}");
    await waitFor(() => expect(onValue).toHaveBeenCalled());
    expect(onValue.mock.lastCall![0].pairs.l1).toMatch(/^r\d$/);
    expect(screen.getByRole("button", { name: t.matchClear(1) })).toBeInTheDocument();
  });

  it("Tự luận: Tab vào ô viết, có tên và vùng đếm chữ báo trạng thái", async () => {
    const user = userEvent.setup();
    render(<Live question={essay} />);
    await user.tab();
    await user.keyboard("một hai");
    expect(screen.getByRole("textbox")).toHaveAccessibleName(t.essayEditorLabel);
    expect(screen.getByRole("status")).toHaveTextContent(t.essayWordCount(2, 5));
  });

  it("Cờ đánh dấu xem lại: nút bấm có trạng thái và tên", async () => {
    const user = userEvent.setup();
    const onToggle = vi.fn();
    render(<Live question={single} onToggleFlag={onToggle} />);
    const flag = screen.getByRole("button", { name: t.flagForReview });
    expect(flag).toHaveAttribute("aria-pressed", "false");
    await user.click(flag);
    expect(onToggle).toHaveBeenCalledTimes(1);
  });
});
