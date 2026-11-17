import { act, fireEvent, screen, waitFor } from "@testing-library/react";
import type { Editor } from "@tiptap/core";
import { describe, expect, it, vi } from "vitest";
import en from "@/messages/en.json";
import viMessages from "@/messages/vi.json";
import type * as QuestionsApi from "@/features/questions/api";
import { renderWithProviders } from "@/test/render";
import { RichTextEditor } from "../rich-text-editor";

vi.mock("mathlive", () => import("./mathlive-mock").then((module) => module.mathliveModule));
vi.mock("@/features/questions/api", async (importOriginal) => {
  const actual = await importOriginal<typeof QuestionsApi>();
  return { ...actual, uploadQuestionAsset: vi.fn() };
});

function tiptap(): Editor {
  const el = document.querySelector<HTMLElement & { editor?: Editor }>(".ProseMirror");
  if (!el?.editor) throw new Error("Editor is not ready");
  return el.editor;
}

function typeText(editor: Editor, text: string) {
  act(() => {
    for (const char of text) editor.view.dispatch(editor.state.tr.insertText(char));
  });
}

const key = (editor: Editor, name: string) => act(() => void fireEvent.keyDown(editor.view.dom, { key: name }));

describe("slash menu inside RichTextEditor", () => {
  it("opens from / and Enter on a filtered entry removes the text and opens that entry's dialog", async () => {
    renderWithProviders(<RichTextEditor value="" onChange={vi.fn()} />, { locale: "en", messages: en });
    const editor = tiptap();

    typeText(editor, "Tính /math");
    const list = await screen.findByRole("listbox", { name: "Insert" });
    expect(list.querySelectorAll('[role="option"]')).toHaveLength(1);

    key(editor, "Enter");

    expect(await screen.findByRole("dialog", { name: "Math formula" })).toBeInTheDocument();
    expect(editor.getText()).toBe("Tính ");
    expect(screen.queryByRole("listbox")).toBeNull();
  });

  it("lists the entries of the field context with Vietnamese labels, hiding Image when images are off", async () => {
    renderWithProviders(<RichTextEditor value="" onChange={vi.fn()} allowImages={false} fieldContext="instructions" />, {
      locale: "vi",
      messages: viMessages,
    });
    typeText(tiptap(), "/");

    const list = await screen.findByRole("listbox", { name: "Chèn" });
    const text = list.textContent ?? "";
    expect(text).toContain(viMessages.richText.insert.math.label);
    expect(text).toContain(viMessages.richText.insert.cross.label);
    expect(text).not.toContain(viMessages.richText.insert.image.label);
    expect(text).not.toContain(viMessages.richText.insert.blank.label);
  });

  it("follows a changed fieldContext without rebuilding the editor", async () => {
    const view = renderWithProviders(<RichTextEditor value="" onChange={vi.fn()} fieldContext="default" />, {
      locale: "en",
      messages: en,
    });
    const editor = tiptap();
    view.rerender(<RichTextEditor value="" onChange={vi.fn()} fieldContext="set-passage" />);
    typeText(editor, "/");

    const list = await screen.findByRole("listbox");
    await waitFor(() => expect(list.textContent).toContain(en.richText.insert.blank.label));
  });
});
