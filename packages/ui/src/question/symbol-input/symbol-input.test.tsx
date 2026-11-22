import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { useRef, useState } from "react";
import { describe, expect, it, vi } from "vitest";
import { UiStringsProvider } from "../../components/ui-strings";
import { UI_STRINGS_EN, UI_STRINGS_VI } from "../../messages/ui-strings";
import { Live } from "../__tests__/fixtures";
import type { KitQuestion } from "../types";
import { SymbolInputBar } from "./symbol-input-bar";
import { DEFAULT_ION_SYMBOLS, insertTextAtCursor, shouldShowSymbolInput } from "./symbols";

const chemQuestion: KitQuestion = {
  id: "q-hsca-hoa",
  displayNo: 5,
  points: 1,
  stem: "<p>Viết công thức ion sắt (III).</p>",
  body: {
    type: "SHORT_ANSWER",
    answers: ["Fe3+"],
    inputHint: "Viết công thức ion hoặc phân tử dạng chữ thường/số, ví dụ Fe3+, SO4(2-), H2O",
    answerFormat: "CHEM_FORMULA",
  },
};

const numericQuestion: KitQuestion = {
  id: "q-num",
  displayNo: 6,
  points: 1,
  stem: "<p>Làm tròn đến hàng phần trăm.</p>",
  body: {
    type: "SHORT_ANSWER",
    answers: ["3.14"],
    inputHint: "Số thập phân làm tròn 2 chữ số, đơn vị m/s",
    answerFormat: "DECIMAL",
  },
};

function Harness({ initial = "" }: { initial?: string }) {
  const ref = useRef<HTMLInputElement | null>(null);
  const [value, setValue] = useState(initial);
  return (
    <div>
      <input ref={ref} aria-label="answer" value={value} onChange={(e) => setValue(e.target.value)} />
      <SymbolInputBar targetRef={ref} value={value} onChange={setValue} />
    </div>
  );
}

describe("chèn ký hiệu", () => {
  it("ShortAnswerQuestion hiện inputHint và thanh ký hiệu cho CHEM_FORMULA, không hiện cho DECIMAL", () => {
    const { rerender } = render(<Live question={chemQuestion} />);
    const hint = screen.getByText(/Fe3\+, SO4\(2-\), H2O/);
    expect(hint).toBeInTheDocument();
    expect(hint.closest("[data-exam-content]")).not.toBeNull();
    expect(screen.getByRole("group", { name: UI_STRINGS_VI.question.symbolInput.groupLabel })).toBeInTheDocument();
    expect(screen.queryByText(/\\ce\{\}/)).toBeNull();

    rerender(<Live question={numericQuestion} />);
    expect(screen.getByText(/đơn vị m\/s/)).toBeInTheDocument();
    expect(screen.queryByRole("group", { name: UI_STRINGS_VI.question.symbolInput.groupLabel })).toBeNull();
  });

  it("chèn ký tự ASCII chính xác tại đầu, giữa, thay vùng chọn, cuối và giữ vị trí con trỏ", async () => {
    expect(insertTextAtCursor("O", 0, 0, "H")).toEqual({ nextValue: "HO", nextCursor: 1 });
    expect(insertTextAtCursor("HO", 1, 1, "2")).toEqual({ nextValue: "H2O", nextCursor: 2 });
    expect(insertTextAtCursor("HXXO", 1, 3, "2")).toEqual({ nextValue: "H2O", nextCursor: 2 });
    expect(insertTextAtCursor("Fe", null, null, "3+")).toEqual({ nextValue: "Fe3+", nextCursor: 4 });

    const user = userEvent.setup();
    render(<Harness initial="HO" />);
    const input = screen.getByRole("textbox", { name: "answer" }) as HTMLInputElement;
    input.focus();
    input.setSelectionRange(1, 1);

    await user.click(screen.getByRole("button", { name: UI_STRINGS_VI.question.symbolInput.symbolNames["sub-2"] }));
    expect(input.value).toBe("H2O");
    expect(input.selectionStart).toBe(2);
    expect(input.selectionEnd).toBe(2);

    await user.keyboard("x");
    expect(input.value).toBe("H2xO");

    input.setSelectionRange(1, 3);
    await user.click(screen.getByRole("button", { name: UI_STRINGS_VI.question.symbolInput.symbolNames["sub-3"] }));
    expect(input.value).toBe("H3O");
    expect(input.selectionStart).toBe(2);
  });

  it("gõ các đáp án thật của DS H-SCA Hóa học khớp ngân hàng câu hỏi", async () => {
    const user = userEvent.setup();
    const cases: { letters: string; buttons: string[]; expected: string }[] = [
      { letters: "Fe", buttons: ["charge-3plus"], expected: "Fe3+" },
      { letters: "Cu", buttons: ["charge-2plus"], expected: "Cu2+" },
      { letters: "Cl", buttons: ["minus"], expected: "Cl-" },
      { letters: "Na", buttons: ["plus"], expected: "Na+" },
      { letters: "NH", buttons: ["sub-4", "plus"], expected: "NH4+" },
      { letters: "OH", buttons: ["minus"], expected: "OH-" },
      { letters: "SO", buttons: ["sub-4", "lparen", "charge-2minus", "rparen"], expected: "SO4(2-)" },
      { letters: "SO4 ", buttons: ["charge-2minus"], expected: "SO4 2-" },
      { letters: "H", buttons: ["sub-2"], expected: "H2" },
    ];

    const onValue = vi.fn();
    const { rerender } = render(<Live key="init" question={chemQuestion} onValue={onValue} />);

    for (let idx = 0; idx < cases.length; idx += 1) {
      const c = cases[idx]!;
      rerender(<Live key={`case-${idx}`} question={chemQuestion} onValue={onValue} />);
      const input = screen.getByRole("textbox", { name: UI_STRINGS_VI.question.answerLabel });
      await user.type(input, c.letters);
      for (const btnId of c.buttons) {
        const label = UI_STRINGS_VI.question.symbolInput.symbolNames[btnId]!;
        await user.click(screen.getByRole("button", { name: label }));
      }
      expect(onValue).toHaveBeenLastCalledWith({ text: c.expected });
    }

    // Complete H2O with trailing letter O
    rerender(<Live key="h2o" question={chemQuestion} onValue={onValue} />);
    const h2oInput = screen.getByRole("textbox", { name: UI_STRINGS_VI.question.answerLabel });
    await user.type(h2oInput, "H");
    await user.click(screen.getByRole("button", { name: UI_STRINGS_VI.question.symbolInput.symbolNames["sub-2"]! }));
    await user.type(h2oInput, "O");
    expect(onValue).toHaveBeenLastCalledWith({ text: "H2O" });
  });

  it("hỗ trợ song ngữ vi/en và nút đạt vùng chạm >= 44px", () => {
    expect(shouldShowSymbolInput("CHEM_FORMULA")).toBe(true);
    expect(shouldShowSymbolInput("CHEM_EQUATION")).toBe(true);
    expect(shouldShowSymbolInput("TEXT")).toBe(true);
    expect(shouldShowSymbolInput("INTEGER", "Số nguyên")).toBe(false);
    expect(shouldShowSymbolInput(null, "Nhập công thức ion")).toBe(true);
    expect(shouldShowSymbolInput(null, "Enter symbol")).toBe(true);

    render(
      <UiStringsProvider locale="en">
        <Live question={chemQuestion} />
      </UiStringsProvider>,
    );

    expect(screen.getByRole("group", { name: UI_STRINGS_EN.question.symbolInput.groupLabel })).toBeInTheDocument();
    const buttons = screen.getAllByRole("button").filter((el) => el.hasAttribute("data-symbol-insert"));
    expect(buttons).toHaveLength(DEFAULT_ION_SYMBOLS.length);
    for (const sym of DEFAULT_ION_SYMBOLS) {
      const btn = screen.getByRole("button", { name: UI_STRINGS_EN.question.symbolInput.symbolNames[sym.id]! });
      expect(btn).toHaveClass("min-h-11", "min-w-11");
      expect(btn).toHaveAttribute("data-symbol-insert", sym.insert);
    }
  });

  it("khóa nút khi disabled, ẩn thanh ký hiệu ở chế độ review và truyền showSymbolBar xuống câu con của QUESTION_SET", async () => {
    const user = userEvent.setup();
    const onBarChange = vi.fn();

    function DisabledHarness() {
      const ref = useRef<HTMLInputElement | null>(null);
      return (
        <div>
          <input ref={ref} aria-label="disabled-answer" value="Fe" readOnly />
          <SymbolInputBar targetRef={ref} value="Fe" onChange={onBarChange} disabled />
        </div>
      );
    }

    const { unmount } = render(<DisabledHarness />);
    const disabledBtns = screen
      .getAllByRole("button")
      .filter((el) => el.hasAttribute("data-symbol-insert"));
    expect(disabledBtns.length).toBeGreaterThan(0);
    for (const btn of disabledBtns) {
      expect(btn).toBeDisabled();
    }
    await user.click(disabledBtns[0]!);
    expect(onBarChange).not.toHaveBeenCalled();
    unmount();

    const reviewView = render(
      <Live
        question={chemQuestion}
        mode="review"
        initial={{ text: "Fe3+" }}
        review={{ outcome: "CORRECT", earned: 1, max: 1 }}
      />,
    );
    expect(screen.queryByRole("group", { name: UI_STRINGS_VI.question.symbolInput.groupLabel })).toBeNull();
    reviewView.unmount();

    const parentSet: KitQuestion = {
      id: "set-chem",
      displayNo: 1,
      points: 2,
      body: {
        type: "QUESTION_SET",
        passage: "<p>Thí nghiệm hóa học.</p>",
        childIds: ["q-hsca-hoa"],
      },
    };
    const { rerender } = render(
      <Live question={parentSet} showSymbolBar={false}>
        {[chemQuestion]}
      </Live>,
    );
    expect(screen.queryByRole("group", { name: UI_STRINGS_VI.question.symbolInput.groupLabel })).toBeNull();

    const decimalSet: KitQuestion = {
      id: "set-num",
      displayNo: 1,
      points: 2,
      body: {
        type: "QUESTION_SET",
        passage: "<p>Tính toán.</p>",
        childIds: ["q-num"],
      },
    };
    rerender(
      <Live question={decimalSet} showSymbolBar={true}>
        {[numericQuestion]}
      </Live>,
    );
    expect(screen.getByRole("group", { name: UI_STRINGS_VI.question.symbolInput.groupLabel })).toBeInTheDocument();
  });
});
