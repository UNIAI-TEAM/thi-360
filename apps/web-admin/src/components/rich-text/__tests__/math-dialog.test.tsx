import { readFileSync } from "node:fs";
import { join } from "node:path";
import { act, fireEvent, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { QueryClient } from "@tanstack/react-query";
import type { Editor } from "@tiptap/core";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import en from "@/messages/en.json";
import viMessages from "@/messages/vi.json";
import { SESSION_QUERY_KEY } from "@/features/session/api";
import type * as QuestionsApi from "@/features/questions/api";
import { renderWithProviders } from "@/test/render";
import { MATH_BLOCK_NODE, MATH_INLINE_NODE } from "../extensions";
import { PALETTE_TABS } from "../insert/math/math-palette";
import { RichTextEditor } from "../rich-text-editor";
import { MockMathfield } from "./mathlive-mock";

vi.mock("mathlive", () => import("./mathlive-mock").then((module) => module.mathliveModule));
vi.mock("@/features/questions/api", async (importOriginal) => {
  const actual = await importOriginal<typeof QuestionsApi>();
  return { ...actual, uploadQuestionAsset: vi.fn() };
});

beforeEach(() => {
  MockMathfield.reset();
  window.localStorage.clear();
});
afterEach(() => window.localStorage.clear());

function renderEditor(props: Partial<Parameters<typeof RichTextEditor>[0]> = {}, client?: QueryClient) {
  const onChange = vi.fn();
  const view = renderWithProviders(<RichTextEditor value="" onChange={onChange} {...props} />, {
    locale: "en",
    messages: en,
    ...(client ? { client } : {}),
  });
  return { onChange, ...view };
}

const lastHtml = (onChange: ReturnType<typeof vi.fn>) => onChange.mock.lastCall?.[0] as string;

function tiptap(): Editor {
  const el = document.querySelector<HTMLElement & { editor?: Editor }>(".ProseMirror");
  if (!el?.editor) throw new Error("Editor is not ready");
  return el.editor;
}

async function openDialog(user: ReturnType<typeof userEvent.setup>) {
  await user.click(screen.getByRole("button", { name: "Math formula" }));
  const dialog = await screen.findByRole("dialog", { name: "Math formula" });
  await waitFor(() => expect(MockMathfield.instances.length).toBe(1));
  return dialog;
}

const latexBox = () => screen.getByRole("textbox", { name: "LaTeX code" }) as HTMLTextAreaElement;
const insertButton = () => screen.getByRole("button", { name: /^(Insert|Update) formula$/ });

/** Chọn node công thức (đầu tiên) để khung đang chọn hiện và nút Chèn mở lại hộp. */
function selectFormula(editor: Editor) {
  let pos = -1;
  editor.state.doc.descendants((node, at) => {
    if (pos === -1 && (node.type.name === MATH_INLINE_NODE || node.type.name === MATH_BLOCK_NODE)) pos = at;
  });
  act(() => {
    editor.commands.setNodeSelection(pos);
  });
}

describe("math dialog (mathlive mocked)", () => {
  it("loads mathlive with the contract configuration and no virtual keyboard", async () => {
    const user = userEvent.setup();
    renderEditor();
    await openDialog(user);

    expect(MockMathfield.fontsDirectory).toBe("/mathlive/fonts/");
    expect(MockMathfield.soundsDirectory).toBeNull();
    expect(MockMathfield.current.mathVirtualKeyboardPolicy).toBe("manual");
    expect(MockMathfield.current.menuItems).toEqual([]);
    expect(MockMathfield.current.getAttribute("aria-label")).toBe("Formula editing area");
  });

  it("copies the visual field into the LaTeX box using latex-expanded", async () => {
    const user = userEvent.setup();
    renderEditor();
    await openDialog(user);

    act(() => MockMathfield.current.type("\\frac{1}{2}"));

    expect(latexBox().value).toBe("\\frac{1}{2}");
    expect(MockMathfield.getValueFormats.every((format) => format === "latex-expanded")).toBe(true);
    expect(MockMathfield.getValueFormats.length).toBeGreaterThan(0);
  });

  it("copies the LaTeX box into the visual field without echoing back", async () => {
    const user = userEvent.setup();
    renderEditor();
    await openDialog(user);

    fireEvent.change(latexBox(), { target: { value: "x^2 + 1" } });

    expect(MockMathfield.current.value).toBe("x^2 + 1");
    expect(latexBox().value).toBe("x^2 + 1");
    // Ghi sang ô trực quan không được đọc ngược lại (getValue chỉ gọi khi người dùng sửa ô trực quan).
    expect(MockMathfield.getValueFormats).toEqual([]);
    expect(screen.getByText("Valid")).toBeInTheDocument();
  });

  it("keeps the edits flowing both ways in sequence", async () => {
    const user = userEvent.setup();
    renderEditor();
    await openDialog(user);

    act(() => MockMathfield.current.type("a"));
    fireEvent.change(latexBox(), { target: { value: "a+b" } });
    act(() => MockMathfield.current.type("+c"));

    expect(MockMathfield.current.value).toBe("a+b+c");
    expect(latexBox().value).toBe("a+b+c");
  });

  it("shows a syntax error with a readable message and disables Insert", async () => {
    const user = userEvent.setup();
    renderEditor();
    await openDialog(user);

    fireEvent.change(latexBox(), { target: { value: "\\frac{1" } });
    expect(screen.getByText("Syntax error")).toBeInTheDocument();
    expect(screen.getByText("A closing bracket is missing")).toBeInTheDocument();
    expect(insertButton()).toBeDisabled();
    expect(latexBox()).toHaveAttribute("aria-invalid", "true");

    fireEvent.change(latexBox(), { target: { value: "\\foo{x}" } });
    expect(screen.getByText("Unsupported command: \\foo")).toBeInTheDocument();
    expect(insertButton()).toBeDisabled();

    fireEvent.change(latexBox(), { target: { value: "x^{" } });
    expect(screen.getByText("Syntax error")).toBeInTheDocument();

    fireEvent.change(latexBox(), { target: { value: "x^2" } });
    expect(screen.getByText("Valid")).toBeInTheDocument();
    expect(screen.queryByText("Syntax error")).toBeNull();
    expect(insertButton()).toBeEnabled();
  });

  it("disables Insert while a placeholder slot is left, then enables it once filled", async () => {
    const user = userEvent.setup();
    renderEditor();
    const dialog = await openDialog(user);

    await user.click(within(dialog).getByRole("button", { name: "Fraction" }));

    expect(MockMathfield.inserts[0]?.options).toMatchObject({ format: "latex", selectionMode: "placeholder" });
    expect(latexBox().value).toBe("\\frac{\\placeholder{}}{\\placeholder{}}");
    expect(screen.getByText("Empty slots left")).toBeInTheDocument();
    expect(insertButton()).toBeDisabled();

    act(() => MockMathfield.current.replaceAll("\\frac{1}{2}"));
    expect(screen.getByText("Valid")).toBeInTheDocument();
    expect(insertButton()).toBeEnabled();
  });

  it("disables Insert for an empty formula", async () => {
    const user = userEvent.setup();
    renderEditor();
    await openDialog(user);

    expect(insertButton()).toBeDisabled();
    expect(screen.queryByText("Valid")).toBeNull();
  });

  it("has the eight tabs of the palette, each with named buttons", async () => {
    const user = userEvent.setup();
    renderEditor();
    const dialog = await openDialog(user);

    const labels = en.richText.insertDialog.math.tabs;
    const tabs = within(dialog).getAllByRole("tab");
    expect(tabs.map((tab) => tab.textContent)).toEqual([
      labels.common,
      labels.fractionRoot,
      labels.power,
      labels.limitIntegral,
      labels.matrix,
      labels.geometry,
      labels.sets,
      labels.greek,
    ]);

    for (const paletteTab of PALETTE_TABS) {
      await user.click(within(dialog).getByRole("tab", { name: labels[paletteTab.id] }));
      const group = within(dialog).getByRole("group", { name: labels[paletteTab.id] });
      expect(within(group).getAllByRole("button")).toHaveLength(paletteTab.items.length);
    }
    expect(within(dialog).getByRole("button", { name: "Alpha" })).toBeInTheDocument();
  });

  it("puts the chosen template into the visual field and keeps the dialog open", async () => {
    const user = userEvent.setup();
    renderEditor();
    const dialog = await openDialog(user);

    await user.click(within(dialog).getByRole("tab", { name: "Greek" }));
    await user.click(within(dialog).getByRole("button", { name: "Alpha" }));
    await user.click(within(dialog).getByRole("button", { name: "Beta" }));

    expect(latexBox().value).toBe("\\alpha\\beta");
    expect(screen.getByRole("dialog", { name: "Math formula" })).toBeInTheDocument();
  });

  it("inserts an inline formula, trimmed, and closes", async () => {
    const user = userEvent.setup();
    const { onChange } = renderEditor();
    await openDialog(user);

    fireEvent.change(latexBox(), { target: { value: "  x^2  " } });
    await user.click(insertButton());

    await waitFor(() => expect(lastHtml(onChange)).toContain('<span class="math-inline">\\(x^2\\)</span>'));
    expect(screen.queryByRole("dialog")).toBeNull();
  });

  it("inserts a block formula when Own line is chosen", async () => {
    const user = userEvent.setup();
    const { onChange } = renderEditor();
    const dialog = await openDialog(user);

    fireEvent.change(latexBox(), { target: { value: "\\sum_{i=1}^n i" } });
    await user.click(within(dialog).getByRole("button", { name: "Own line" }));
    await user.click(insertButton());

    await waitFor(() => expect(lastHtml(onChange)).toContain('<span class="math-block">\\[\\sum_{i=1}^n i\\]</span>'));
  });

  it("does not submit the surrounding page form", async () => {
    const user = userEvent.setup();
    const onPageSubmit = vi.fn((event: { preventDefault: () => void }) => event.preventDefault());
    const onChange = vi.fn();
    renderWithProviders(
      <form onSubmit={onPageSubmit}>
        <RichTextEditor value="" onChange={onChange} />
      </form>,
      { locale: "en", messages: en },
    );
    await openDialog(user);

    fireEvent.change(latexBox(), { target: { value: "x^2" } });
    await user.click(insertButton());

    await waitFor(() => expect(lastHtml(onChange)).toContain("math-inline"));
    expect(onPageSubmit).not.toHaveBeenCalled();
  });

  it("emits exactly the fixture form for the formulas of the shared sample table", async () => {
    const fixture = JSON.parse(
      readFileSync(join(process.cwd(), "../../docs/specs/fixtures/rich-text-samples.json"), "utf8"),
    ) as { samples: Array<{ name: string; html: string }> };
    const html = fixture.samples.find((sample) => sample.name === "math-inline-and-block")?.html ?? "";
    const spans = [...html.matchAll(/<span class="(math-inline|math-block)">(.*?)<\/span>/g)];
    expect(spans).toHaveLength(2);

    for (const [span, kind, delimited] of spans) {
      const latex = (delimited ?? "").slice(2, -2).replace(/&lt;/g, "<").replace(/&gt;/g, ">").replace(/&amp;/g, "&");
      const user = userEvent.setup();
      MockMathfield.reset();
      const { onChange, unmount } = renderEditor();
      const dialog = await openDialog(user);
      fireEvent.change(latexBox(), { target: { value: latex } });
      if (kind === "math-block") await user.click(within(dialog).getByRole("button", { name: "Own line" }));
      await user.click(insertButton());
      await waitFor(() => expect(lastHtml(onChange)).toContain(span));
      unmount();
    }
  });

  it("escapes LaTeX angle brackets and ampersands exactly like the stored fixture form", async () => {
    const user = userEvent.setup();
    const { onChange } = renderEditor();
    await openDialog(user);

    fireEvent.change(latexBox(), { target: { value: "a < b \\Rightarrow c > d" } });
    await user.click(insertButton());

    await waitFor(() => expect(lastHtml(onChange)).toContain("\\(a &lt; b \\Rightarrow c &gt; d\\)"));
  });

  it("reopens an inserted inline formula with its LaTeX and display restored", async () => {
    const user = userEvent.setup();
    const { onChange } = renderEditor({ value: '<p>a <span class="math-inline">\\(\\frac{1}{2}\\)</span> b</p>' });
    selectFormula(tiptap());

    // Nút Chèn đang bật (đang chọn công thức): bấm mở lại hộp để sửa, không chèn mới.
    expect(screen.getByRole("button", { name: "Math formula" })).toHaveAttribute("aria-pressed", "true");
    const dialog = await openDialog(user);

    expect(latexBox().value).toBe("\\frac{1}{2}");
    expect(MockMathfield.current.value).toBe("\\frac{1}{2}");
    expect(within(dialog).getByRole("button", { name: "Inline" })).toHaveAttribute("aria-pressed", "true");
    expect(insertButton()).toHaveTextContent("Update formula");

    fireEvent.change(latexBox(), { target: { value: "\\frac{3}{4}" } });
    await user.click(insertButton());
    await waitFor(() => expect(lastHtml(onChange)).toContain("\\(\\frac{3}{4}\\)"));
    expect(lastHtml(onChange)).not.toContain("\\frac{1}{2}");
    expect(lastHtml(onChange).match(/math-inline/g)).toHaveLength(1);
  });

  it("reopens a block formula in Own line mode and can turn it into an inline one", async () => {
    const user = userEvent.setup();
    const { onChange } = renderEditor({ value: '<p>a</p><span class="math-block">\\[x^2 + y^2 = z^2\\]</span>' });
    selectFormula(tiptap());
    const dialog = await openDialog(user);

    expect(latexBox().value).toBe("x^2 + y^2 = z^2");
    expect(within(dialog).getByRole("button", { name: "Own line" })).toHaveAttribute("aria-pressed", "true");

    await user.click(within(dialog).getByRole("button", { name: "Inline" }));
    await user.click(insertButton());
    await waitFor(() => expect(lastHtml(onChange)).toContain('<span class="math-inline">\\(x^2 + y^2 = z^2\\)</span>'));
    expect(lastHtml(onChange)).not.toContain("math-block");
  });

  it("opens from the Edit button of the selected-formula panel", async () => {
    const user = userEvent.setup();
    renderEditor({ value: '<p><span class="math-inline">\\(y\\)</span></p>' });
    selectFormula(tiptap());

    await user.click(await screen.findByRole("button", { name: "Edit" }));
    await screen.findByRole("dialog", { name: "Math formula" });
    await waitFor(() => expect(MockMathfield.instances.length).toBe(1));
    expect(latexBox().value).toBe("y");
  });

  it("previews the formula inside up to 80 characters of the surrounding sentence", async () => {
    const before = `${"a".repeat(60)} ${"b".repeat(40)}`;
    const after = `${"c".repeat(50)} ${"d".repeat(50)}`;
    const user = userEvent.setup();
    renderEditor({ value: `<p>${before}<span class="math-inline">\\(z\\)</span>${after}</p>` });
    selectFormula(tiptap());
    await openDialog(user);

    const preview = screen.getByTestId("math-preview");
    // Công thức đang sửa nằm giữa hai đoạn chữ; mỗi bên cắt còn 80 ký tự gần điểm chèn nhất.
    expect(preview).toHaveTextContent(before.slice(-80));
    expect(preview.textContent?.startsWith(before.slice(-80))).toBe(true);
    expect(preview.textContent?.endsWith(after.slice(0, 80))).toBe(true);
    expect(preview.querySelector(".katex")).toBeTruthy();
  });

  it("puts a new formula between the prose around the caret in the preview, up to 80 characters each side", async () => {
    const before = `Compute ${"a".repeat(90)} and then`;
    const after = ` find ${"b".repeat(90)} done`;
    const user = userEvent.setup();
    renderEditor({ value: `<p>${before}${after}</p>` });
    // Con trỏ nằm trong đoạn chữ (không chọn công thức nào): đầu đoạn là vị trí 1.
    act(() => {
      tiptap().commands.setTextSelection(1 + before.length);
    });
    await openDialog(user);
    fireEvent.change(latexBox(), { target: { value: "x^2" } });

    const preview = screen.getByTestId("math-preview");
    const [beforeText, formula, afterText] = Array.from(preview.children);
    expect(beforeText?.textContent).toBe(before.slice(-80));
    expect(formula?.querySelector(".katex")).toBeTruthy();
    expect(afterText?.textContent).toBe(after.slice(0, 80));
    expect(preview.children).toHaveLength(3);
  });

  it("shows the preview inline or on its own line following the display choice", async () => {
    const user = userEvent.setup();
    renderEditor();
    const dialog = await openDialog(user);

    fireEvent.change(latexBox(), { target: { value: "x^2" } });
    expect(screen.getByTestId("math-preview").querySelector(".katex-display")).toBeNull();
    await user.click(within(dialog).getByRole("button", { name: "Own line" }));
    expect(screen.getByTestId("math-preview").querySelector(".katex-display")).toBeTruthy();
  });

  it("offers the saved recent formulas and keeps the newest eight after an insert", async () => {
    const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
    client.setQueryData(SESSION_QUERY_KEY, { id: "user-1" });
    const key = "thi360-rich-text-recent:user-1:math";
    const saved = ["x_8", "x_7", "x_6", "x_5", "x_4", "x_3", "x_2", "x_1"];
    window.localStorage.setItem(key, JSON.stringify(saved));
    const user = userEvent.setup();
    renderEditor({}, client);

    let dialog = await openDialog(user);
    expect(within(dialog).getByText("Recently used")).toBeInTheDocument();
    expect(within(dialog).getAllByRole("button", { name: /^Insert formula x_/ })).toHaveLength(8);
    fireEvent.change(latexBox(), { target: { value: "y^2" } });
    await user.click(insertButton());
    await waitFor(() => expect(screen.queryByRole("dialog")).toBeNull());

    expect(JSON.parse(window.localStorage.getItem(key) ?? "[]")).toEqual(["y^2", ...saved.slice(0, 7)]);
    expect(window.localStorage.getItem("thi360-rich-text-recent:user-2:math")).toBeNull();

    MockMathfield.reset();
    dialog = await openDialog(user);
    await user.click(within(dialog).getByRole("button", { name: "Insert formula y^2" }));
    expect(MockMathfield.inserts.at(-1)?.latex).toBe("y^2");
  });

  it("shows no recent list when storage fails", async () => {
    const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
    client.setQueryData(SESSION_QUERY_KEY, { id: "user-1" });
    vi.spyOn(Storage.prototype, "getItem").mockImplementation(() => {
      throw new Error("blocked");
    });
    const user = userEvent.setup();
    renderEditor({}, client);
    const dialog = await openDialog(user);

    expect(within(dialog).queryByText("Recently used")).toBeNull();
  });

  it("labels the dialog in Vietnamese by default", async () => {
    const user = userEvent.setup();
    const onChange = vi.fn();
    renderWithProviders(<RichTextEditor value="" onChange={onChange} />, { locale: "vi", messages: viMessages });
    await user.click(screen.getByRole("button", { name: "Công thức toán" }));
    const dialog = await screen.findByRole("dialog", { name: "Công thức toán" });
    expect(within(dialog).getByRole("tab", { name: "Giới hạn, tích phân" })).toBeInTheDocument();
    expect(within(dialog).getByRole("textbox", { name: "Mã LaTeX" })).toBeInTheDocument();
    expect(within(dialog).getByText(/Công thức lưu dưới dạng LaTeX/)).toBeInTheDocument();
  });
});
