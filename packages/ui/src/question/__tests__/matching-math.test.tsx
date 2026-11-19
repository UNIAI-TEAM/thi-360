import { render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it } from "vitest";
import { UI_STRINGS_VI } from "../../messages/ui-strings";
import type { KitQuestion } from "../types";
import { Live, matching } from "./fixtures";

const t = UI_STRINGS_VI.question;

/** Mục phải chứa công thức: dùng `htmlToText` sẽ lộ chuỗi LaTeX thô trong ô chọn và bảng review. */
const mathMatching: KitQuestion = {
  ...matching,
  id: "q-match-math",
  body: {
    type: "MATCHING",
    left: [{ id: "l1", content: "<p>Bình phương của x</p>" }],
    right: [
      { id: "r1", content: '<p><span class="math-inline">\\(x^2\\)</span></p>' },
      { id: "r2", content: "<p>Căn của x</p>" },
    ],
    pairs: [{ leftId: "l1", rightId: "r1" }],
    reuseRight: false,
  },
};

describe("Ghép đôi: mục phải có công thức (R1-9)", () => {
  it("review: ô \"bạn chọn\" và \"đáp án\" dựng công thức, không lộ LaTeX thô", async () => {
    const { container } = render(
      <Live
        question={mathMatching}
        mode="review"
        initial={{ pairs: { l1: "r1" } }}
        review={{ outcome: "CORRECT", earned: 1, max: 1, gradingDetail: { l1: { key: "l1", outcome: "CORRECT", earned: 1, max: 1 } } }}
      />,
    );
    await waitFor(() => expect(container.querySelectorAll("td .katex")).toHaveLength(2));
    expect(container.querySelector("table")!.textContent).not.toContain("\\(x^2\\)");
  });

  it("chế độ chọn: ô chọn và danh sách mở đều dựng công thức", async () => {
    const user = userEvent.setup();
    const { container } = render(<Live question={mathMatching} initial={{ pairs: { l1: "r1" } }} />);
    const trigger = screen.getByRole("combobox", { name: t.matchPickLeft(1) });
    await waitFor(() => expect(trigger.querySelector(".katex")).toBeTruthy());
    expect(container.textContent).not.toContain("\\(x^2\\)");
    await user.click(trigger);
    const listbox = await screen.findByRole("listbox");
    await waitFor(() => expect(listbox.querySelector(".katex")).toBeTruthy());
    expect(within(listbox).getByText(/Căn của x/)).toBeInTheDocument();
  });
});
