import { Editor } from "@tiptap/core";
import { afterEach, describe, expect, it } from "vitest";
import { createRichTextExtensions } from "../extensions";
import { MathInputRule } from "../extensions/math-input-rule";

let editor: Editor | null = null;

afterEach(() => {
  editor?.destroy();
  editor = null;
});

function createEditor(content = "<p></p>") {
  const element = document.createElement("div");
  document.body.append(element);
  editor = new Editor({ element, extensions: [...createRichTextExtensions(), MathInputRule], content });
  return editor;
}

/** Gõ từng ký tự như người dùng: input rule chạy ở `handleTextInput`, không khớp thì chèn chữ như thường. */
function type(target: Editor, text: string) {
  for (const char of text) {
    const { from, to } = target.state.selection;
    const handled = target.view.someProp("handleTextInput", (handler) => handler(target.view, from, to, char, () => target.state.tr.insertText(char, from, to)));
    if (!handled) target.view.dispatch(target.state.tr.insertText(char, from, to));
  }
}

describe("math input rule", () => {
  it("turns $…$ into an inline formula on the same line", () => {
    const target = createEditor();
    type(target, "Tính $x^2$");
    expect(target.getHTML()).toBe('<p>Tính <span class="math-inline">\\(x^2\\)</span></p>');
  });

  it("continues typing after the converted formula", () => {
    const target = createEditor();
    type(target, "$a+b$ và $c$ xong");
    expect(target.getHTML()).toBe(
      '<p><span class="math-inline">\\(a+b\\)</span> và <span class="math-inline">\\(c\\)</span> xong</p>',
    );
  });

  it("turns $$…$$ into a block formula outside any paragraph", () => {
    const target = createEditor();
    type(target, "$$x^2 + y^2 = z^2$$");
    expect(target.getHTML()).toBe('<span class="math-block">\\[x^2 + y^2 = z^2\\]</span><p></p>');
  });

  it("keeps the text before $$…$$ in its own paragraph", () => {
    const target = createEditor();
    type(target, "Ta có $$x$$");
    expect(target.getHTML()).toBe('<p>Ta có </p><span class="math-block">\\[x\\]</span><p></p>');
  });

  it("keeps \\$ as a literal dollar sign without the backslash", () => {
    const target = createEditor();
    type(target, "giá 5\\$ và \\$7");
    expect(target.getText()).toBe("giá 5$ và $7");
    expect(target.getHTML()).not.toContain("math-");
  });

  it("does not convert an amount like 5$ (no opening dollar)", () => {
    const target = createEditor();
    type(target, "giá 5$");
    expect(target.getHTML()).toBe("<p>giá 5$</p>");
  });

  it("does not convert an amount like $5 (no closing dollar)", () => {
    const target = createEditor();
    type(target, "giá $5");
    expect(target.getHTML()).toBe("<p>giá $5</p>");
  });

  it("does not convert when the dollars hug whitespace", () => {
    const target = createEditor();
    type(target, "thu $ 5 và 6 $");
    expect(target.getHTML()).toBe("<p>thu $ 5 và 6 $</p>");
  });

  it("does not convert an empty pair", () => {
    const target = createEditor();
    type(target, "$$");
    expect(target.getHTML()).toBe("<p>$$</p>");
  });

  it("restores the typed text on Backspace right after a conversion", () => {
    const target = createEditor();
    type(target, "$x$");
    expect(target.getHTML()).toContain("math-inline");
    target.commands.undoInputRule();
    expect(target.getHTML()).toBe("<p>$x$</p>");
  });
});
