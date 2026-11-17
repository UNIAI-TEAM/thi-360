import { Editor } from "@tiptap/core";
import { Image } from "@tiptap/extension-image";
import StarterKit from "@tiptap/starter-kit";
import { describe, expect, it } from "vitest";
import { MathCommands, MathInline } from "../extensions";

const IMG = '<img src="/api/v1/admin/question-assets/a1" data-asset-id="a1">';

function editorWith(html: string) {
  return new Editor({ extensions: [StarterKit, Image.configure({ inline: true }), MathInline, MathCommands], content: html });
}

describe("setMath", () => {
  it("F-05: chèn công thức khi đang chọn ảnh thì giữ ảnh và đặt công thức ngay sau", () => {
    const editor = editorWith(`<p>a${IMG}b</p>`);
    let imagePos = -1;
    editor.state.doc.descendants((node, pos) => {
      if (node.type.name === "image") imagePos = pos;
    });
    editor.commands.setNodeSelection(imagePos);
    expect(editor.commands.setMath({ latex: "x^2", display: false })).toBe(true);
    const html = editor.getHTML();
    expect(html).toContain("question-assets/a1");
    expect(html.indexOf("<img")).toBeLessThan(html.indexOf('class="math-inline"'));
    editor.destroy();
  });

  it("chọn công thức có sẵn thì thay công thức đó", () => {
    const editor = editorWith('<p>a<span class="math-inline">\\(y\\)</span>b</p>');
    let mathPos = -1;
    editor.state.doc.descendants((node, pos) => {
      if (node.type.name === "mathInline") mathPos = pos;
    });
    editor.commands.setNodeSelection(mathPos);
    editor.commands.setMath({ latex: "z", display: false });
    const html = editor.getHTML();
    expect(html).toContain("\\(z\\)");
    expect(html).not.toContain("\\(y\\)");
    editor.destroy();
  });
});
