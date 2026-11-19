import { render, screen, waitFor } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { UI_STRINGS_EN, UI_STRINGS_VI } from "../../messages/ui-strings";
import type { QuestionReview } from "../types";
import { all, cloze, essay, Live, matching, questionSet, setChildA, setChildB, shortAnswer, trueFalse } from "./fixtures";

/** Mọi chuỗi giao diện của kit ở cả hai ngôn ngữ (chuỗi thuần, và kết quả hàm với số mẫu). */
function uiTexts(): Set<string> {
  const out = new Set<string>();
  const visit = (node: unknown) => {
    if (typeof node === "string") out.add(node);
    else if (typeof node === "function") {
      for (const result of [
        (node as (...args: unknown[]) => unknown)(1, 2, 3),
        (node as (...args: unknown[]) => unknown)(1, null),
        (node as (...args: unknown[]) => unknown)("a", null),
        (node as (...args: unknown[]) => unknown)(1, "2"),
      ]) {
        if (typeof result === "string") out.add(result);
      }
    } else if (node && typeof node === "object") for (const child of Object.values(node)) visit(child);
  };
  visit(UI_STRINGS_VI.question);
  visit(UI_STRINGS_EN.question);
  return out;
}

const review: QuestionReview = {
  outcome: "PARTIAL",
  earned: 1,
  max: 2,
  showAnswers: true,
  gradingDetail: {
    b1: { key: "b1", outcome: "CORRECT", earned: 1, max: 1 },
    b2: { key: "b2", outcome: "WRONG", earned: 0, max: 1 },
    s1: { key: "s1", outcome: "CORRECT", earned: 0.25, max: 0.25 },
    s2: { key: "s2", outcome: "WRONG", earned: 0, max: 0.25 },
    l1: { key: "l1", outcome: "CORRECT", earned: 0.5, max: 0.5 },
    l2: { key: "l2", outcome: "WRONG", earned: 0, max: 0.5 },
  },
};

function violations(container: HTMLElement): string[] {
  const ui = uiTexts();
  const found: string[] = [];
  for (const zone of container.querySelectorAll("[data-exam-content]")) {
    for (const el of [zone, ...zone.querySelectorAll("*")]) {
      for (const name of ["aria-label", "placeholder"]) {
        if (el.hasAttribute(name)) found.push(`${name}="${el.getAttribute(name)}" trong vùng nội dung đề`);
      }
    }
    const walker = document.createTreeWalker(zone, NodeFilter.SHOW_TEXT);
    while (walker.nextNode()) {
      const text = (walker.currentNode as Text).data.replace(/\s+/g, " ").trim();
      if (ui.has(text)) found.push(`chữ giao diện "${text}" trong vùng nội dung đề`);
    }
  }
  return found;
}

describe("data-exam-content chỉ bọc nội dung đề (R1-3)", () => {
  it.each(["answer", "review"] as const)("%s: không có nhãn, aria-label, placeholder giao diện trong vùng nội dung", async (mode) => {
    const initial = Object.fromEntries(all.map(({ question, value }) => [question.id, value]));
    const { container } = render(
      <div>
        {all.map(({ question }) => (
          <Live
            key={question.id}
            question={question}
            mode={mode}
            matchUi={question.id === matching.id ? "pick" : undefined}
            initial={initial[question.id] as never}
            review={mode === "review" ? review : undefined}
          />
        ))}
        <Live
          question={questionSet}
          children={[setChildA, setChildB]}
          mode={mode}
          review={mode === "review" ? { ...review, children: { "q-set-a": review, "q-set-b": review } } : undefined}
        />
      </div>,
    );
    await waitFor(() => expect(container.querySelectorAll("[data-cloze-slot] input").length).toBeGreaterThan(0));
    expect(violations(container)).toEqual([]);
  });

  it("Điền khuyết: ô nhập nằm ngoài vùng nội dung, chữ ngữ liệu nằm trong vùng", async () => {
    render(<Live question={cloze} />);
    await waitFor(() => expect(screen.getAllByRole("textbox")).toHaveLength(2));
    for (const input of screen.getAllByRole("textbox")) expect(input.closest("[data-exam-content]")).toBeNull();
    expect(screen.getByText(/Nước sôi ở/).closest("[data-exam-content]")).not.toBeNull();
  });

  it("Trả lời ngắn (review): tiền tố \"Đáp án:\" và điểm nằm ngoài, đáp án nằm trong vùng", () => {
    render(
      <Live question={shortAnswer} mode="review" initial={{ text: "41" }} review={{ outcome: "WRONG", earned: 0, max: 1 }} />,
    );
    expect(screen.getByText(/Đáp án:/).closest("[data-exam-content]")).toBeNull();
    expect(screen.getByText("42").closest("[data-exam-content]")).not.toBeNull();
  });

  it("Đúng/Sai bảng theo số ý đúng: dòng tóm tắt là chữ giao diện, nằm ngoài vùng nội dung", () => {
    const countTable = { ...trueFalse, body: { ...trueFalse.body, scoringMethod: "COUNT_TABLE" as const } };
    render(
      <Live question={countTable} mode="review" initial={{ statements: { s1: true, s2: false } }} review={{ ...review, earned: 0.5 }} />,
    );
    expect(screen.getByText(/ý →/).closest("[data-exam-content]")).toBeNull();
  });

  it("Tự luận (review): nhãn nhận xét ngoài vùng, nội dung nhận xét trong vùng", () => {
    render(
      <Live
        question={essay}
        mode="review"
        initial={{ html: "<p>Bài làm.</p>", wordCount: 2 }}
        review={{ outcome: "CORRECT", earned: 20, max: 25, essay: { state: "GRADED", comment: "Tốt" } }}
      />,
    );
    expect(screen.getByText("Nhận xét của người chấm").closest("[data-exam-content]")).toBeNull();
    expect(screen.getByText("Tốt").closest("[data-exam-content]")).not.toBeNull();
  });
});
