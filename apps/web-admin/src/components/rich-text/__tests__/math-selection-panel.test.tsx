import { act, fireEvent, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import type { Editor } from "@tiptap/core";
import { NodeSelection } from "@tiptap/pm/state";
import { describe, expect, it, vi } from "vitest";
import en from "@/messages/en.json";
import type * as QuestionsApi from "@/features/questions/api";
import { renderWithProviders } from "@/test/render";
import { MATH_BLOCK_NODE, MATH_INLINE_NODE } from "../extensions";
import { RichTextEditor } from "../rich-text-editor";

vi.mock("mathlive", () => import("./mathlive-mock").then((module) => module.mathliveModule));
vi.mock("@/features/questions/api", async (importOriginal) => {
  const actual = await importOriginal<typeof QuestionsApi>();
  return { ...actual, uploadQuestionAsset: vi.fn() };
});

function renderEditor(value: string) {
  const onChange = vi.fn();
  renderWithProviders(<RichTextEditor value={value} onChange={onChange} />, { locale: "en", messages: en });
  return onChange;
}

function tiptap(): Editor {
  const el = document.querySelector<HTMLElement & { editor?: Editor }>(".ProseMirror");
  if (!el?.editor) throw new Error("Editor is not ready");
  return el.editor;
}

function selectFormula(editor: Editor) {
  let pos = -1;
  editor.state.doc.descendants((node, at) => {
    if (pos === -1 && (node.type.name === MATH_INLINE_NODE || node.type.name === MATH_BLOCK_NODE)) pos = at;
  });
  act(() => {
    editor.commands.setNodeSelection(pos);
  });
}

const panel = () => screen.findByRole("group", { name: "Selected formula" });
const lastHtml = (onChange: ReturnType<typeof vi.fn>) => onChange.mock.lastCall?.[0] as string;

describe("selected-formula panel", () => {
  it("is hidden until a formula is selected, then shows its LaTeX under the toolbar", async () => {
    renderEditor('<p>a <span class="math-inline">\\(\\frac{1}{2}\\)</span></p>');
    expect(screen.queryByRole("group", { name: "Selected formula" })).toBeNull();

    selectFormula(tiptap());

    const group = await panel();
    expect(within(group).getByRole("textbox", { name: "LaTeX of the selected formula" })).toHaveValue("\\frac{1}{2}");
    expect(within(group).getByRole("button", { name: "Inline" })).toHaveAttribute("aria-pressed", "true");
    expect(within(group).getByRole("button", { name: "Own line" })).toHaveAttribute("aria-pressed", "false");
    expect(within(group).getByRole("button", { name: "Edit" })).toBeInTheDocument();
    expect(within(group).getByRole("button", { name: "Delete formula" })).toBeInTheDocument();
  });

  it("shows a block formula as Own line", async () => {
    renderEditor('<p>a</p><span class="math-block">\\[x^2\\]</span>');
    selectFormula(tiptap());

    const group = await panel();
    expect(within(group).getByRole("button", { name: "Own line" })).toHaveAttribute("aria-pressed", "true");
  });

  it("edits the LaTeX of the selected node directly while the panel stays open", async () => {
    const onChange = renderEditor('<p>a <span class="math-inline">\\(x\\)</span> b</p>');
    selectFormula(tiptap());
    const input = within(await panel()).getByRole("textbox", { name: "LaTeX of the selected formula" });

    fireEvent.change(input, { target: { value: "x^2+1" } });

    await waitFor(() => expect(lastHtml(onChange)).toContain('<span class="math-inline">\\(x^2+1\\)</span>'));
    expect(screen.getByRole("textbox", { name: "LaTeX of the selected formula" })).toHaveValue("x^2+1");
    expect(tiptap().state.selection).toBeInstanceOf(NodeSelection);
  });

  it.each([
    ["inline", '<p>a <span class="math-inline">\\(x^2\\)</span> b</p>', '<span class="math-inline">\\(x^2\\)</span>'],
    ["block", '<p>a</p><span class="math-block">\\[x^2\\]</span>', '<span class="math-block">\\[x^2\\]</span>'],
  ])("never persists a blank %s formula: clearing the box and blurring restores the last valid LaTeX", async (_kind, html, expected) => {
    const user = userEvent.setup();
    const onChange = renderEditor(html);
    selectFormula(tiptap());
    const input = within(await panel()).getByRole("textbox", { name: "LaTeX of the selected formula" });

    await user.clear(input);
    expect(input).toHaveValue("");
    await user.click(document.body);

    await waitFor(() => expect(screen.getByRole("textbox", { name: "LaTeX of the selected formula" })).toHaveValue("x^2"));
    const blank = />\\\(\s*\\\)<|>\\\[\s*\\\]</;
    expect(tiptap().getHTML()).toContain(expected);
    expect(tiptap().getHTML()).not.toMatch(blank);
    for (const [value] of onChange.mock.calls) expect(value).not.toMatch(blank);
  });

  it("marks the LaTeX box invalid when the formula cannot be rendered", async () => {
    renderEditor('<p><span class="math-inline">\\(x\\)</span></p>');
    selectFormula(tiptap());
    const input = within(await panel()).getByRole("textbox", { name: "LaTeX of the selected formula" });

    fireEvent.change(input, { target: { value: "\\frac{1" } });

    await waitFor(() => expect(input).toHaveAttribute("aria-invalid", "true"));
  });

  it("switches between inline and own line, keeping the formula selected so the panel stays", async () => {
    const user = userEvent.setup();
    const onChange = renderEditor('<p>a <span class="math-inline">\\(x^2\\)</span> b</p>');
    selectFormula(tiptap());

    await user.click(within(await panel()).getByRole("button", { name: "Own line" }));

    await waitFor(() => expect(lastHtml(onChange)).toContain('<span class="math-block">\\[x^2\\]</span>'));
    expect(lastHtml(onChange)).not.toContain("math-inline");
    const group = await panel();
    expect(within(group).getByRole("button", { name: "Own line" })).toHaveAttribute("aria-pressed", "true");

    await user.click(within(group).getByRole("button", { name: "Inline" }));

    await waitFor(() => expect(lastHtml(onChange)).toContain('<span class="math-inline">\\(x^2\\)</span>'));
    expect(lastHtml(onChange)).not.toContain("math-block");
    expect(within(await panel()).getByRole("button", { name: "Inline" })).toHaveAttribute("aria-pressed", "true");
  });

  it("deletes the selected formula and hides the panel", async () => {
    const user = userEvent.setup();
    const onChange = renderEditor('<p>a <span class="math-inline">\\(x\\)</span> b</p>');
    selectFormula(tiptap());

    await user.click(within(await panel()).getByRole("button", { name: "Delete formula" }));

    await waitFor(() => expect(lastHtml(onChange)).not.toContain("math-inline"));
    await waitFor(() => expect(screen.queryByRole("group", { name: "Selected formula" })).toBeNull());
  });
});
