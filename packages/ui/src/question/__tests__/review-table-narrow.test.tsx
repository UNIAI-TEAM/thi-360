import { render, screen, waitFor } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { UI_STRINGS_VI } from "../../messages/ui-strings";
import type { QuestionReview } from "../types";
import { cloze, essay, Live, matching, trueFalse } from "./fixtures";

const t = UI_STRINGS_VI.question;

// jsdom không áp media query nên kiểm cấu trúc và lớp class của bố cục xếp chồng; hình học thật ở 390 do e2e
// (`question-kit-visual.spec.ts`) kiểm bằng hộp bao của từng ô.
const graded: QuestionReview = {
  outcome: "PARTIAL",
  earned: 1,
  max: 2,
  gradingDetail: {
    b1: { key: "b1", outcome: "CORRECT", earned: 1, max: 1 },
    b2: { key: "b2", outcome: "WRONG", earned: 0, max: 1 },
    s1: { key: "s1", outcome: "CORRECT", earned: 0.25, max: 0.25 },
    s2: { key: "s2", outcome: "WRONG", earned: 0, max: 0.25 },
    l1: { key: "l1", outcome: "CORRECT", earned: 0.5, max: 0.5 },
    l2: { key: "l2", outcome: "WRONG", earned: 0, max: 0.5 },
  },
};

/** Bảng xếp chồng đúng: tiêu đề chỉ cho trình đọc màn hình, mỗi ô (trừ ô mục) mang nhãn cột nhìn thấy dưới `sm`. */
function expectStackable(container: HTMLElement, headers: string[]) {
  const table = container.querySelector("table")!;
  expect(table).toHaveAttribute("role", "table");
  expect(table).toHaveClass("max-sm:block");
  expect(table.querySelector("thead")).toHaveClass("max-sm:sr-only");
  expect(screen.getAllByRole("columnheader").map((th) => th.textContent)).toEqual(headers);
  for (const row of table.querySelectorAll("tbody tr")) {
    expect(row).toHaveAttribute("role", "row");
    expect(row).toHaveClass("max-sm:flex", "max-sm:flex-col");
    const cells = [...row.querySelectorAll(":scope > td")];
    expect(cells).toHaveLength(headers.length);
    const labels = cells.map((cell) => cell.querySelector(":scope > .sm\\:hidden")?.textContent ?? null);
    // Ô đầu là mục (đã tự nêu số/nội dung); các ô sau mang nhãn cột của chính chúng.
    expect(labels.slice(1)).toEqual(headers.slice(1).map((header) => `${header}:`));
    for (const cell of cells) expect(cell).toHaveClass("max-sm:flex");
  }
}

describe("Bảng review xếp chồng trên màn hẹp (R1-8, TV1-1)", () => {
  it("Đúng/Sai", () => {
    const { container } = render(
      <Live question={trueFalse} mode="review" initial={{ statements: { s1: true, s2: true } }} review={graded} />,
    );
    expectStackable(container, [t.itemCol, t.youChoseCol, t.answerCol, t.pointsCol]);
  });

  it("Ghép đôi: cột Điểm vẫn có trong hàng", () => {
    const { container } = render(
      <Live question={matching} mode="review" initial={{ pairs: { l1: "r1", l2: "r1" } }} review={graded} />,
    );
    expectStackable(container, [t.itemCol, t.youChoseCol, t.answerCol, t.pointsCol]);
    expect(screen.getAllByText(t.points("0,5", "0,5"))).toHaveLength(1);
  });

  it("Điền khuyết", async () => {
    const { container } = render(
      <Live question={cloze} mode="review" initial={{ blanks: { b1: "100", b2: "5" } }} review={graded} />,
    );
    await waitFor(() => expect(container.querySelector("table")).toBeTruthy());
    expectStackable(container, [t.itemCol, t.youChoseCol, t.acceptedCol, t.pointsCol]);
  });

  it("Tự luận: bảng tiêu chí", () => {
    const { container } = render(
      <Live
        question={essay}
        mode="review"
        initial={{ html: "<p>Bài làm.</p>", wordCount: 2 }}
        review={{
          outcome: "CORRECT",
          earned: 20,
          max: 25,
          essay: { state: "GRADED", criteria: [{ description: "Lập luận", earned: 8, max: 10 }, { description: "Diễn đạt", earned: null, max: 5 }] },
        }}
      />,
    );
    expectStackable(container, [t.criterionCol, t.pointsCol]);
  });

  it("vẫn là bảng có ngữ nghĩa: ô dữ liệu đọc được theo vai trò", () => {
    render(<Live question={trueFalse} mode="review" initial={{ statements: { s1: true, s2: true } }} review={graded} />);
    expect(screen.getAllByRole("row")).toHaveLength(3);
    expect(screen.getAllByRole("cell")).toHaveLength(8);
  });
});
