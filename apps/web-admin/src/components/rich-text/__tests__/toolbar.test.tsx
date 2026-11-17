import { fireEvent, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";
import type { Editor } from "@tiptap/core";
import en from "@/messages/en.json";
import type * as QuestionsApi from "@/features/questions/api";
import { renderWithProviders } from "@/test/render";
import { RichTextEditor } from "../rich-text-editor";

vi.mock("@/features/questions/api", async (importOriginal) => {
  const actual = await importOriginal<typeof QuestionsApi>();
  const uploadQuestionAsset = vi.fn(async (file: File) => ({
    id: "9b8f8b1f-9b8f-4b8f-9b8f-9b8f8b8f8b8f",
    url: `/api/v1/admin/question-assets/9b8f8b1f-9b8f-4b8f-9b8f-9b8f8b8f8b8f`,
    fileName: file.name,
  }));
  return { ...actual, uploadQuestionAsset };
});

function renderEditor(props: Partial<Parameters<typeof RichTextEditor>[0]> = {}) {
  const onChange = vi.fn();
  const view = renderWithProviders(<RichTextEditor value="" onChange={onChange} {...props} />, {
    locale: "en",
    messages: en,
  });
  return { onChange, ...view };
}

const editable = () => document.querySelector<HTMLElement>(".ProseMirror");

/** Editor TipTap nằm trên phần tử .ProseMirror (kit `rich()` của question tests dùng cùng cách). */
function tiptap(): Editor {
  const el = editable() as (HTMLElement & { editor?: Editor }) | null;
  if (!el?.editor) throw new Error("Editor is not ready");
  return el.editor;
}

const toolbarButtons = () => [...document.querySelectorAll<HTMLButtonElement>('[role="toolbar"] button')];

describe("RichTextEditor toolbar", () => {
  it("is one Tab stop: arrow keys move between buttons, Home/End jump to edges", async () => {
    const user = userEvent.setup();
    renderEditor();
    const buttons = toolbarButtons();
    expect(buttons.length).toBeGreaterThan(8);
    // Chỉ đúng một nút trong thanh nhận Tab.
    expect(buttons.filter((b) => b.tabIndex === 0)).toHaveLength(1);

    await user.tab();
    expect(document.activeElement).toBe(buttons[0]);

    await user.keyboard("{ArrowRight}");
    expect(document.activeElement).toBe(buttons[1]);
    await user.keyboard("{ArrowLeft}");
    expect(document.activeElement).toBe(buttons[0]);

    await user.keyboard("{End}");
    expect(document.activeElement).toBe(buttons[buttons.length - 1]);
    await user.keyboard("{Home}");
    expect(document.activeElement).toBe(buttons[0]);

    // Mũi tên quấn quanh hai đầu; nút nhận focus cập nhật điểm dừng Tab.
    await user.keyboard("{ArrowLeft}");
    expect(document.activeElement).toBe(buttons[buttons.length - 1]);
    expect(buttons[buttons.length - 1].tabIndex).toBe(0);
  });

  it("undo/redo buttons reflect history state and work", async () => {
    const user = userEvent.setup();
    renderEditor();
    await waitFor(() => expect(tiptap()).toBeTruthy());

    const undo = screen.getByRole("button", { name: "Undo" });
    const redo = screen.getByRole("button", { name: "Redo" });
    expect(undo).toHaveAttribute("aria-disabled", "true");
    expect(redo).toHaveAttribute("aria-disabled", "true");

    tiptap().commands.insertContent("<p>hello</p>");
    await waitFor(() => expect(undo).not.toHaveAttribute("aria-disabled"));
    expect(redo).toHaveAttribute("aria-disabled", "true");

    await user.click(undo);
    await waitFor(() => expect(redo).not.toHaveAttribute("aria-disabled"));
    await user.click(redo);
    await waitFor(() => expect(tiptap().getText()).toContain("hello"));
  });

  it("toggles aria-pressed for active formats", async () => {
    const user = userEvent.setup();
    renderEditor();
    await waitFor(() => expect(tiptap()).toBeTruthy());

    const bold = screen.getByRole("button", { name: "Bold" });
    expect(bold).toHaveAttribute("aria-pressed", "false");
    await user.click(bold);
    await waitFor(() => expect(bold).toHaveAttribute("aria-pressed", "true"));
  });

  it("shows the default Insert group (no image button when allowImages=false)", async () => {
    renderEditor();
    const labels = toolbarButtons().map((b) => b.getAttribute("aria-label"));
    for (const expected of ["Math formula", "Chemistry formula", "Symbols and units", "Data table", "Image", "Quote"]) {
      expect(labels).toContain(expected);
    }
    expect(labels).not.toContain("Blank");
    expect(labels).not.toContain("Labeled underline");
    expect(labels).not.toContain("Punnett square");
    expect(labels).not.toContain("Paste from Word");
  });

  it("fieldContext=stem-single-choice adds Labeled underline", () => {
    renderEditor({ fieldContext: "stem-single-choice" });
    const labels = toolbarButtons().map((b) => b.getAttribute("aria-label"));
    expect(labels).toContain("Labeled underline");
    expect(labels).not.toContain("Blank");
  });

  it("fieldContext=set-passage adds Blank", () => {
    renderEditor({ fieldContext: "set-passage" });
    const labels = toolbarButtons().map((b) => b.getAttribute("aria-label"));
    expect(labels).toContain("Blank");
    expect(labels).not.toContain("Labeled underline");
  });

  it("fieldContext=instructions hides the image entry", () => {
    renderEditor({ fieldContext: "instructions", allowImages: false });
    const labels = toolbarButtons().map((b) => b.getAttribute("aria-label"));
    expect(labels).not.toContain("Image");
    expect(labels).toContain("Math formula");
  });

  it("opens the interim stub dialog for a registry entry without a real dialog", async () => {
    const user = userEvent.setup();
    renderEditor();
    await user.click(screen.getByRole("button", { name: "Symbols and units" }));
    expect(await screen.findByRole("dialog")).toBeTruthy();
    expect(await screen.findByText("Work in progress")).toBeTruthy();
    await user.keyboard("{Escape}");
    await waitFor(() => expect(screen.queryByRole("dialog")).toBeNull());
  });

  it("calls onRichPaste with the raw clipboardData before sanitizing, and skips default paste on true", async () => {
    const onRichPaste = vi.fn(() => true);
    const { onChange } = renderEditor({ onRichPaste });

    editable()!.focus();
    const transfer = {
      getData: (format: string) => (format === "text/html" ? "<p>from word</p>" : ""),
      types: ["text/html"],
      files: [],
      items: [],
    };
    fireEvent.paste(editable()!, { clipboardData: transfer });

    expect(onRichPaste).toHaveBeenCalledTimes(1);
    expect(onRichPaste).toHaveBeenCalledWith(transfer);
    // Trả true → trình soạn bỏ qua dán mặc định, không có nội dung nào vào.
    expect(onChange).not.toHaveBeenCalled();
  });
});
