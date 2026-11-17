import { act, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { useEffect } from "react";
import type { Editor } from "@tiptap/core";
import { EditorContent, useEditor } from "@tiptap/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import en from "@/messages/en.json";
import { createRichTextExtensions } from "../extensions";
import { filterSlashEntries, foldText, SlashMenu } from "../extensions/slash-menu";
import { slashMenuEntries, type FieldContext, type InsertEntry } from "../insert/registry";

/** Tra khóa `richText.<a>.<b>` trong bản tiếng Anh. */
function translate(key: string): string {
  let value: unknown = en.richText;
  for (const part of key.split(".")) value = (value as Record<string, unknown>)[part];
  return String(value);
}

let current: Editor | null = null;
const setCurrent = (editor: Editor | null) => {
  current = editor;
};

function Host({ fieldContext, onSelect, onEditor }: { fieldContext: FieldContext; onSelect: (entry: InsertEntry) => void; onEditor: (editor: Editor | null) => void }) {
  const editor = useEditor({
    immediatelyRender: false,
    extensions: [
      ...createRichTextExtensions(),
      SlashMenu.configure({ getEntries: () => slashMenuEntries(fieldContext), onSelect, translate, listLabelKey: "toolbar.groupInsert" }),
    ],
    content: "<p></p>",
  });
  useEffect(() => onEditor(editor), [editor, onEditor]);
  return <EditorContent editor={editor} />;
}

function setup(fieldContext: FieldContext = "default") {
  const onSelect = vi.fn();
  render(<Host fieldContext={fieldContext} onSelect={onSelect} onEditor={setCurrent} />);
  return { onSelect };
}

async function ready(): Promise<Editor> {
  await waitFor(() => expect(current?.view?.dom).toBeTruthy());
  return current as Editor;
}

/** Gõ chữ vào cuối tài liệu: mỗi ký tự là một giao dịch, như khi gõ phím. */
function typeText(editor: Editor, text: string) {
  act(() => {
    for (const char of text) editor.view.dispatch(editor.state.tr.insertText(char));
  });
}

const options = () => screen.queryAllByRole("option").map((option) => option.textContent ?? "");
const key = (editor: Editor, name: string) => act(() => void fireEvent.keyDown(editor.view.dom, { key: name }));

afterEach(() => {
  current = null;
});

describe("slash menu", () => {
  it("opens on / at the start of a line, with name and description per entry", async () => {
    setup();
    const editor = await ready();
    typeText(editor, "/");

    const list = await screen.findByRole("listbox", { name: "Insert" });
    expect(list).toBeInTheDocument();
    const names = screen.getAllByRole("option").map((option) => option.querySelector("span.font-semibold")?.textContent);
    expect(names).toEqual(slashMenuEntries("default").map((entry) => en.richText.insert[entry.id as "math"].label));
    expect(options()[0]).toContain(en.richText.insert.math.description);
    expect(options().some((text) => text.includes(en.richText.insert.cross.label))).toBe(true);
  });

  it("opens after a space but not in the middle of a word", async () => {
    setup();
    const editor = await ready();
    typeText(editor, "và/hoặc");
    expect(screen.queryByRole("listbox")).toBeNull();

    typeText(editor, " /");
    expect(await screen.findByRole("listbox")).toBeInTheDocument();
  });

  it("filters by what follows the slash, ignoring case and Vietnamese accents", async () => {
    setup();
    const editor = await ready();
    typeText(editor, "/");
    await screen.findByRole("listbox");

    typeText(editor, "FORM");
    await waitFor(() => expect(options().length).toBe(2));
    expect(options().map((text) => text.slice(0, 8))).toEqual(["Math for", "Chemistr"]);

    typeText(editor, "ula");
    await waitFor(() => expect(options().length).toBe(2));

    typeText(editor, "zzz");
    await waitFor(() => expect(screen.queryByRole("listbox")).toBeNull());
  });

  it("folds Vietnamese accents and đ for matching", () => {
    expect(foldText("Công thức đồ thị")).toBe("cong thuc do thi");
    const vi = (key: string) => (key.endsWith("label") ? "Đoạn trích" : "Thơ, văn xuôi");
    const entries = slashMenuEntries("default");
    expect(filterSlashEntries(entries, "doan trich", vi)).toHaveLength(entries.length);
    expect(filterSlashEntries(entries, "xyz", vi)).toHaveLength(0);
    expect(filterSlashEntries(entries, "  ", vi)).toBe(entries);
  });

  it("respects the field context of each entry", async () => {
    setup("instructions");
    const editor = await ready();
    typeText(editor, "/");
    await screen.findByRole("listbox");

    const text = options().join("|");
    expect(text).not.toContain(en.richText.insert.image.label);
    expect(text).not.toContain(en.richText.insert["underline-label"].label);
    expect(text).not.toContain(en.richText.insert.blank.label);
    expect(text).toContain(en.richText.insert.cross.label);
  });

  it("moves with the arrow keys, wrapping around, and reports the active option", async () => {
    setup();
    const editor = await ready();
    typeText(editor, "/");
    await screen.findByRole("listbox");
    const selected = () => screen.getAllByRole("option").findIndex((option) => option.getAttribute("aria-selected") === "true");

    expect(selected()).toBe(0);
    expect(editor.view.dom.getAttribute("aria-activedescendant")).toBe(screen.getAllByRole("option")[0]?.id);
    key(editor, "ArrowDown");
    await waitFor(() => expect(selected()).toBe(1));
    key(editor, "ArrowUp");
    key(editor, "ArrowUp");
    await waitFor(() => expect(selected()).toBe(screen.getAllByRole("option").length - 1));
    expect(editor.view.dom.getAttribute("aria-expanded")).toBe("true");
  });

  it("selects the active entry with Enter, removing the /query text first", async () => {
    const { onSelect } = setup();
    const editor = await ready();
    typeText(editor, "abc /tab");
    await waitFor(() => expect(options().length).toBe(1));

    key(editor, "Enter");

    await waitFor(() => expect(onSelect).toHaveBeenCalledTimes(1));
    expect(onSelect.mock.calls[0]?.[0].id).toBe("table");
    expect(editor.getText()).toBe("abc ");
    expect(screen.queryByRole("listbox")).toBeNull();
    expect(editor.view.dom.hasAttribute("aria-expanded")).toBe(false);
  });

  it("selects an entry by clicking it", async () => {
    const { onSelect } = setup();
    const editor = await ready();
    typeText(editor, "/");
    await screen.findByRole("listbox");

    const option = screen.getAllByRole("option").find((item) => item.textContent?.includes(en.richText.insert.quote.label));
    fireEvent.click(option!);

    expect(onSelect.mock.calls[0]?.[0].id).toBe("quote");
    expect(editor.getText()).toBe("");
  });

  it("closes on Escape and keeps the typed text", async () => {
    const { onSelect } = setup();
    const editor = await ready();
    typeText(editor, "/ma");
    await screen.findByRole("listbox");

    key(editor, "Escape");

    await waitFor(() => expect(screen.queryByRole("listbox")).toBeNull());
    expect(editor.getText()).toBe("/ma");
    expect(onSelect).not.toHaveBeenCalled();
  });

  it("lets Enter act normally when no entry matches", async () => {
    const { onSelect } = setup();
    const editor = await ready();
    typeText(editor, "/zzz");
    await waitFor(() => expect(screen.queryByRole("listbox")).toBeNull());

    key(editor, "Enter");
    expect(onSelect).not.toHaveBeenCalled();
  });
});
