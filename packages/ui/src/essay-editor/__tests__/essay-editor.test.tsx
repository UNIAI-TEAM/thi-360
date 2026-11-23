import { fireEvent, render, screen, waitFor, within } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import {
  DEFAULT_ESSAY_EDITOR_LABELS,
  TiptapEssayEditor,
  normalizeEssayHtml,
} from "../index";

describe("TiptapEssayEditor (@thi360/ui/essay-editor)", () => {
  it("normalizeEssayHtml sanitizes disallowed tags/images and collapses empty paragraph", () => {
    expect(normalizeEssayHtml("<p></p>")).toBe("");
    expect(normalizeEssayHtml("<p>Hello</p><p></p>")).toBe("<p>Hello</p>");
    expect(
      normalizeEssayHtml('<p>Safe <img src="https://evil.test/x.png"><script>alert(1)</script></p>'),
    ).toBe("<p>Safe </p>");
  });

  it("renders with default ASCII labels, placeholder, and textbox ARIA attributes", async () => {
    const onChange = vi.fn();
    render(
      <TiptapEssayEditor
        id="essay-q1"
        value=""
        onChange={onChange}
        placeholder="Write your essay here..."
        ariaLabel="Essay answer"
        ariaDescribedBy="essay-hint-1"
      />,
    );

    const toolbar = screen.getByRole("toolbar", {
      name: DEFAULT_ESSAY_EDITOR_LABELS.toolbarLabel,
    });
    expect(within(toolbar).getByRole("button", { name: DEFAULT_ESSAY_EDITOR_LABELS.bold })).toBeInTheDocument();
    expect(within(toolbar).getByRole("button", { name: DEFAULT_ESSAY_EDITOR_LABELS.italic })).toBeInTheDocument();
    expect(within(toolbar).getByRole("button", { name: DEFAULT_ESSAY_EDITOR_LABELS.bulletList })).toBeInTheDocument();
    expect(within(toolbar).getByRole("button", { name: DEFAULT_ESSAY_EDITOR_LABELS.orderedList })).toBeInTheDocument();
    expect(within(toolbar).getByRole("button", { name: DEFAULT_ESSAY_EDITOR_LABELS.undo })).toBeInTheDocument();
    expect(within(toolbar).getByRole("button", { name: DEFAULT_ESSAY_EDITOR_LABELS.redo })).toBeInTheDocument();

    expect(screen.getByText("Write your essay here...")).toBeInTheDocument();

    await waitFor(() => {
      const textbox = screen.getByRole("textbox", { name: "Essay answer" });
      expect(textbox).toHaveAttribute("id", "essay-q1");
      expect(textbox).toHaveAttribute("aria-multiline", "true");
      expect(textbox).toHaveAttribute("aria-describedby", "essay-hint-1");
    });
  });

  it("supports localized labels, roving-tabindex navigation, modifier ignore, and active toggle styling (R2-30, R2-31, R3-7)", async () => {
    const onChange = vi.fn();
    render(
      <TiptapEssayEditor
        id="essay-vi"
        value="<p>Xin chào</p>"
        onChange={onChange}
        placeholder="Nhập bài luận..."
        aria-label="Bài làm câu 1"
        labels={{
          toolbarLabel: "Thanh định dạng bài viết",
          bold: "In đậm",
          italic: "In nghiêng",
          bulletList: "Danh sách dấu chấm",
          orderedList: "Danh sách đánh số",
          undo: "Hoàn tác",
          redo: "Làm lại",
        }}
      />,
    );

    const toolbar = screen.getByRole("toolbar", { name: "Thanh định dạng bài viết" });
    const buttons = within(toolbar).getAllByRole("button");
    expect(buttons).toHaveLength(6);

    await waitFor(() => {
      expect(screen.getByRole("textbox", { name: "Bài làm câu 1" })).toBeInTheDocument();
    });

    // Roving tabindex: single tab stop on first button, no HTML disabled attributes
    expect(buttons.map((btn) => btn.getAttribute("tabindex"))).toEqual(["0", "-1", "-1", "-1", "-1", "-1"]);
    expect(buttons.every((btn) => !btn.hasAttribute("disabled"))).toBe(true);
    expect(buttons.every((btn) => !btn.className.includes("border-transparent"))).toBe(true);

    buttons[0]!.focus();
    fireEvent.keyDown(document.activeElement!, { key: "ArrowRight" });
    expect(document.activeElement).toBe(buttons[1]);
    expect(buttons.map((btn) => btn.getAttribute("tabindex"))).toEqual(["-1", "0", "-1", "-1", "-1", "-1"]);

    // Modifier keys must be ignored so OS/browser shortcuts pass through
    const ctrlRight = new KeyboardEvent("keydown", { key: "ArrowRight", ctrlKey: true, bubbles: true, cancelable: true });
    document.activeElement!.dispatchEvent(ctrlRight);
    expect(ctrlRight.defaultPrevented).toBe(false);
    expect(document.activeElement).toBe(buttons[1]);

    // End -> Undo/Redo (aria-disabled), Home -> first button, ArrowLeft -> wraps to last
    fireEvent.keyDown(document.activeElement!, { key: "End" });
    expect(document.activeElement).toBe(buttons[5]);
    expect(buttons[5]).toHaveAttribute("aria-disabled", "true");

    const disabledMouseDown = new MouseEvent("mousedown", { bubbles: true, cancelable: true });
    buttons[5]!.dispatchEvent(disabledMouseDown);
    expect(disabledMouseDown.defaultPrevented).toBe(true);

    fireEvent.keyDown(document.activeElement!, { key: "Home" });
    expect(document.activeElement).toBe(buttons[0]);
    fireEvent.keyDown(document.activeElement!, { key: "ArrowLeft" });
    expect(document.activeElement).toBe(buttons[5]);

    // Toggling Bold updates aria-pressed and high-contrast pseudo-border class
    fireEvent.click(buttons[0]!);
    await waitFor(() => {
      expect(buttons[0]).toHaveAttribute("aria-pressed", "true");
    });
    expect(buttons[0]!.className).toContain("after:border-primary-600");
  });

  it("marks textbox and toolbar controls non-editable when readOnly or disabled", async () => {
    render(
      <TiptapEssayEditor
        id="essay-disabled"
        value="<p>Readonly text</p>"
        onChange={vi.fn()}
        ariaLabel="Disabled essay"
        disabled
      />,
    );

    await waitFor(() => {
      const textbox = screen.getByRole("textbox", { name: "Disabled essay" });
      expect(textbox).toHaveAttribute("aria-readonly", "true");
      expect(textbox).toHaveAttribute("aria-disabled", "true");
    });

    const toolbar = screen.getByRole("toolbar");
    for (const btn of within(toolbar).getAllByRole("button")) {
      expect(btn).toHaveAttribute("aria-disabled", "true");
    }
  });
});
