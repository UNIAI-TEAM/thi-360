import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it } from "vitest";
import { UI_STRINGS_VI } from "../../messages/ui-strings";
import { cloze, essay, Live, matching, shortAnswer, single, trueFalse } from "./fixtures";

const t = UI_STRINGS_VI.question;

// jsdom không áp media query: `pointer-coarse:` biên dịch thành `@media (pointer: coarse)`, nên kiểm lớp class
// (cùng cách `preferences.test.tsx` và `dropdown-menu.test.tsx`); hình học thật do e2e ở 390 kiểm.
const COARSE_TARGET = ["pointer-coarse:min-h-11", "pointer-coarse:min-w-11"];
const TOUCH_FIELD_TEXT = ["max-sm:text-title", "pointer-coarse:text-title"];

describe("Vùng bấm 44x44 trên màn cảm ứng (R1-6)", () => {
  it("Đúng/Sai: nhãn radio Đúng/Sai", () => {
    render(<Live question={trueFalse} />);
    for (const radio of screen.getAllByRole("radio")) expect(radio.closest("label")).toHaveClass(...COARSE_TARGET);
  });

  it("nút cờ đánh dấu", () => {
    render(<Live question={single} onToggleFlag={() => {}} />);
    expect(screen.getByRole("button", { name: t.flagForReview })).toHaveClass("pointer-coarse:size-11");
  });

  it("Ghép đôi (ô chọn): nút bỏ ghép và ô chọn", () => {
    render(<Live question={matching} initial={{ pairs: { l1: "r1" } }} />);
    expect(screen.getByRole("button", { name: t.matchClear(1) })).toHaveClass("pointer-coarse:size-11");
    expect(screen.getByRole("combobox", { name: t.matchPickLeft(1) })).toHaveClass("pointer-coarse:h-11");
  });

  it("Ghép đôi (bấm hai lượt): mục trái, nút bỏ ghép, nút gán ở cột phải", async () => {
    const user = userEvent.setup();
    render(<Live question={matching} matchUi="pick" initial={{ pairs: { l1: "r1" } }} />);
    expect(screen.getByRole("button", { name: t.matchClear(1) })).toHaveClass("pointer-coarse:size-11");
    expect(screen.getByRole("button", { name: t.matchPickLeft(1) })).toHaveClass("pointer-coarse:min-h-11");
    await user.click(screen.getByRole("button", { name: t.matchPickLeft(2) }));
    expect(screen.getByRole("button", { name: t.matchPickRight("b", null) })).toHaveClass(...COARSE_TARGET);
  });

  it("Tự luận review: nút Xem toàn bộ bài viết", () => {
    render(
      <Live
        question={essay}
        mode="review"
        initial={{ html: `<p>${"chữ ".repeat(200)}</p>`, wordCount: 200 }}
        review={{ outcome: "CORRECT", earned: 20, max: 25, essay: { state: "GRADED" } }}
      />,
    );
    expect(screen.getByRole("button", { name: t.showFullEssay })).toHaveClass(...COARSE_TARGET);
  });

  it("Điền khuyết: ô trống cao 44px", async () => {
    render(<Live question={cloze} />);
    await waitFor(() => expect(screen.getAllByRole("textbox")).toHaveLength(2));
    for (const box of screen.getAllByRole("textbox")) expect(box).toHaveClass("pointer-coarse:h-11");
  });
});

describe("Ô nhập 16px trên màn hẹp và cảm ứng, iOS không phóng to trang (R1-7)", () => {
  it("Trả lời ngắn", () => {
    render(<Live question={shortAnswer} />);
    expect(screen.getByRole("textbox")).toHaveClass(...TOUCH_FIELD_TEXT);
  });

  it("Điền khuyết", async () => {
    render(<Live question={cloze} />);
    await waitFor(() => expect(screen.getAllByRole("textbox")).toHaveLength(2));
    for (const box of screen.getAllByRole("textbox")) expect(box).toHaveClass(...TOUCH_FIELD_TEXT);
  });

  it("Tự luận (ô viết mặc định)", () => {
    render(<Live question={essay} />);
    expect(screen.getByRole("textbox")).toHaveClass(...TOUCH_FIELD_TEXT);
  });

  it("Ghép đôi: ô chọn", () => {
    render(<Live question={matching} />);
    expect(screen.getByRole("combobox", { name: t.matchPickLeft(1) })).toHaveClass(...TOUCH_FIELD_TEXT);
  });
});
