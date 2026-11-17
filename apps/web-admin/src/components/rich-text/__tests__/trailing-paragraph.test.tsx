import { act, waitFor } from "@testing-library/react";
import type { Editor } from "@tiptap/core";
import { useState } from "react";
import { describe, expect, it, vi } from "vitest";
import en from "@/messages/en.json";
import { renderWithProviders } from "@/test/render";
import { RichTextEditor } from "../rich-text-editor";

if (typeof document.elementFromPoint !== "function") document.elementFromPoint = () => null;

const QUOTE =
  '<blockquote class="quote" data-kind="prose" data-line-numbers="0"><p>Đoạn văn thứ nhất.</p><p class="figure-source">NXB Giáo dục</p></blockquote>';

const editable = () => document.querySelector<HTMLElement>(".ProseMirror");

function tiptap(): Editor {
  const element = editable() as (HTMLElement & { editor?: Editor }) | null;
  if (!element?.editor) throw new Error("Editor is not ready");
  return element.editor;
}

/** Trình soạn điều khiển như nơi dùng thật: giá trị phát ra được đưa ngược lại qua `value`. */
async function renderControlled(initial: string) {
  const emitted: string[] = [];
  function Harness() {
    const [html, setHtml] = useState(initial);
    return (
      <RichTextEditor
        value={html}
        onChange={(next) => {
          emitted.push(next);
          setHtml(next);
        }}
      />
    );
  }
  renderWithProviders(<Harness />, { locale: "en", messages: en });
  await waitFor(() => expect(editable()).toBeTruthy());
  const editor = tiptap();
  return { emitted, editor, setContent: vi.spyOn(editor.commands, "setContent") };
}

const settle = () => act(async () => {});

/** Người dùng bấm vào đoạn cuối: con trỏ đặt trong đoạn cuối của tài liệu (TrailingNode đã thêm nó ở transaction đầu). */
async function clickLastParagraph(editor: Editor) {
  act(() => {
    editor.commands.focus();
  });
  await settle();
  act(() => {
    editor.commands.setTextSelection(editor.state.doc.content.size - 1);
  });
  await settle();
}

/** Gõ từng ký tự tại con trỏ (mỗi phím một transaction), chờ React đồng bộ `value` giữa các phím. */
async function type(editor: Editor, text: string) {
  for (const char of text) {
    act(() => {
      editor.commands.insertContent(char);
    });
    await settle();
  }
}

async function backspace(editor: Editor, count: number) {
  for (let i = 0; i < count; i += 1) {
    act(() => {
      const at = editor.state.selection.from;
      editor.commands.deleteRange({ from: at - 1, to: at });
    });
    await settle();
  }
}

const caretAtEnd = (editor: Editor) => editor.state.selection.from === editor.state.doc.content.size - 1;

describe("trailing paragraph after an atom block", () => {
  it("emits nothing when the user only focuses the editor", async () => {
    const { emitted, editor } = await renderControlled(QUOTE);
    await clickLastParagraph(editor);

    // TrailingNode đã thêm `<p></p>` sau khối, nhưng người dùng chưa đổi gì: không onChange (không làm biểu mẫu "dirty").
    expect(editor.getHTML()).toBe(`${QUOTE}<p></p>`);
    expect(emitted).toEqual([]);
  });

  it("keeps every character and the caret when the user clicks after the block and types", async () => {
    const { emitted, editor, setContent } = await renderControlled(QUOTE);
    await clickLastParagraph(editor);
    await type(editor, "abc");

    expect(emitted).toEqual([`${QUOTE}<p>a</p>`, `${QUOTE}<p>ab</p>`, `${QUOTE}<p>abc</p>`]);
    expect(editor.getHTML()).toBe(`${QUOTE}<p>abc</p>`);
    expect(caretAtEnd(editor)).toBe(true);
    expect(setContent).not.toHaveBeenCalled();
  });

  it("keeps the caret in the trailing paragraph when the user deletes what they typed", async () => {
    const { emitted, editor, setContent } = await renderControlled(QUOTE);
    await clickLastParagraph(editor);
    await type(editor, "ab");
    await backspace(editor, 2);

    // Đoạn cuối lại rỗng: giá trị phát ra là chuỗi gốc (không có đoạn rỗng), và trình soạn không dựng lại nội dung.
    expect(emitted.at(-1)).toBe(QUOTE);
    expect(editor.getHTML()).toBe(`${QUOTE}<p></p>`);
    expect(caretAtEnd(editor)).toBe(true);
    expect(setContent).not.toHaveBeenCalled();

    // Gõ tiếp vẫn vào đúng đoạn đó.
    await type(editor, "z");
    expect(emitted.at(-1)).toBe(`${QUOTE}<p>z</p>`);
    expect(editor.getHTML()).toBe(`${QUOTE}<p>z</p>`);
    expect(setContent).not.toHaveBeenCalled();
  });
});

describe("empty last paragraph made by the user", () => {
  it("lets the user press Enter at the end, then type, without losing characters", async () => {
    const { emitted, editor, setContent } = await renderControlled("<p>first</p>");
    act(() => {
      editor.chain().focus("end").run();
    });
    await settle();
    act(() => {
      editor.commands.splitBlock();
    });
    await settle();

    // Enter: đoạn rỗng cuối do người dùng tạo; giá trị phát ra bỏ đoạn rỗng đó nên không đổi so với giá trị đưa vào.
    expect(editor.getHTML()).toBe("<p>first</p><p></p>");
    expect(setContent).not.toHaveBeenCalled();

    await type(editor, "second");
    expect(emitted.at(-1)).toBe("<p>first</p><p>second</p>");
    expect(editor.getHTML()).toBe("<p>first</p><p>second</p>");
    expect(caretAtEnd(editor)).toBe(true);
    expect(setContent).not.toHaveBeenCalled();
  });

  it("keeps the caret when the user deletes the text again after Enter", async () => {
    const { emitted, editor, setContent } = await renderControlled("<p>first</p>");
    act(() => {
      editor.chain().focus("end").run();
    });
    await settle();
    act(() => {
      editor.commands.splitBlock();
    });
    await settle();
    await type(editor, "xy");
    await backspace(editor, 2);

    expect(emitted.at(-1)).toBe("<p>first</p>");
    expect(editor.getHTML()).toBe("<p>first</p><p></p>");
    expect(caretAtEnd(editor)).toBe(true);
    expect(setContent).not.toHaveBeenCalled();
  });

  it("drops only one trailing empty paragraph when the user made two", async () => {
    const { emitted, editor } = await renderControlled("<p>first</p>");
    act(() => {
      editor.chain().focus("end").run();
    });
    await settle();
    act(() => {
      editor.commands.splitBlock();
      editor.commands.splitBlock();
    });
    await settle();

    expect(editor.getHTML()).toBe("<p>first</p><p></p><p></p>");
    expect(emitted.at(-1)).toBe("<p>first</p><p></p>");
    expect(caretAtEnd(editor)).toBe(true);
  });
});
