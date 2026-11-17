import { act, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { Editor } from "@tiptap/core";
import { describe, expect, it, vi } from "vitest";
import fixtures from "../../../../../../docs/specs/fixtures/rich-text-samples.json";
import en from "@/messages/en.json";
import { renderWithProviders } from "@/test/render";
import { createRichTextExtensions } from "../extensions";
import { nextUnderlineLabel, readLabelSelection, underlinePhrasesOfHtml } from "../insert/underline-label/labels";
import UnderlineLabelSelectionPanel from "../insert/underline-label/selection-panel";
import { RichTextEditor } from "../rich-text-editor";

const FIXTURE = (fixtures.samples as { name: string; html: string }[]).find((sample) => sample.name === "underline-labels")!.html;
/** Mẫu `underline-labels` chưa đánh nhãn: hai cụm "has been", "to" là chữ thường; gạch chân thường giữ nguyên. */
const PLAIN = FIXTURE.replace(/<u data-label="[A-H]">([^<]*)<\/u>/g, "$1");

function tiptap(): Editor {
  const el = document.querySelector<HTMLElement & { editor?: Editor }>(".ProseMirror");
  if (!el?.editor) throw new Error("Editor is not ready");
  return el.editor;
}

function rangeOf(editor: Editor, fragment: string): { from: number; to: number } {
  let found = -1;
  editor.state.doc.descendants((node, pos) => {
    const index = found === -1 && node.isText ? (node.text ?? "").indexOf(fragment) : -1;
    if (index !== -1) found = pos + index;
  });
  if (found === -1) throw new Error(`Text not found: ${fragment}`);
  return { from: found, to: found + fragment.length };
}

function select(fragment: string, caretOnly = false) {
  const editor = tiptap();
  const { from, to } = rangeOf(editor, fragment);
  act(() => {
    editor.commands.setTextSelection(caretOnly ? from + 1 : { from, to });
  });
}

function renderEditor(value: string) {
  const onChange = vi.fn();
  renderWithProviders(<RichTextEditor value={value} onChange={onChange} fieldContext="stem-single-choice" aria-label="Question text" />, {
    locale: "en",
    messages: en,
  });
  return { emitted: () => onChange.mock.lastCall?.[0] as string };
}

async function openDialog() {
  const user = userEvent.setup();
  await user.click(screen.getByRole("button", { name: "Labeled underline" }));
  const dialog = await screen.findByRole("dialog", { name: "Labeled underline" });
  return { user, dialog: within(dialog) };
}

describe("nhãn kế tiếp và đọc cụm", () => {
  it("lấy chữ kế tiếp chưa dùng; hết A–H thì null", () => {
    expect(nextUnderlineLabel([])).toBe("A");
    expect(nextUnderlineLabel(["A", "B"])).toBe("C");
    expect(nextUnderlineLabel(["A", "C"])).toBe("B");
    expect(nextUnderlineLabel(["A", "B", "C", "D", "E", "F", "G", "H"])).toBeNull();
  });

  it("đọc cụm gạch chân đánh nhãn của HTML theo thứ tự, bỏ gạch chân thường và định dạng", () => {
    expect(underlinePhrasesOfHtml(FIXTURE)).toEqual([
      { label: "A", text: "has been" },
      { label: "B", text: "to" },
    ]);
    expect(underlinePhrasesOfHtml('<p><u data-label="C"><strong>so</strong>me</u></p>')).toEqual([{ label: "C", text: "some" }]);
    expect(underlinePhrasesOfHtml("<p>no label</p>")).toEqual([]);
  });
});

describe("hộp Gạch chân đánh nhãn", () => {
  it("bôi đen hai cụm lần lượt: nhãn A rồi B, HTML xuất ra đúng mẫu underline-labels", async () => {
    const { emitted } = renderEditor(PLAIN);
    await waitFor(() => expect(document.querySelector(".ProseMirror")).not.toBeNull());

    select("has been");
    let { user, dialog } = await openDialog();
    expect(dialog.getByRole("radio", { name: "A" })).toBeChecked();
    expect(dialog.getByText("Next unused label: A")).toBeInTheDocument();
    await user.click(dialog.getByRole("button", { name: "Underline" }));
    await waitFor(() => expect(screen.queryByRole("dialog")).toBeNull());
    expect(emitted()).toContain('<u data-label="A">has been</u>');

    select("to");
    ({ user, dialog } = await openDialog());
    // A đã dùng ở cụm khác: khóa; nhãn gợi ý là B.
    expect(dialog.getByRole("radio", { name: "A, used by another phrase" })).toBeDisabled();
    expect(dialog.getByRole("radio", { name: "B" })).toBeChecked();
    await user.click(dialog.getByRole("button", { name: "Underline" }));
    await waitFor(() => expect(screen.queryByRole("dialog")).toBeNull());
    expect(emitted()).toBe(FIXTURE);
  });

  it("mở lại cụm đã gạch: khôi phục nhãn, đổi nhãn", async () => {
    const { emitted } = renderEditor(FIXTURE);
    await waitFor(() => expect(document.querySelector(".ProseMirror")).not.toBeNull());

    select("has been", true);
    const { user, dialog } = await openDialog();
    expect(dialog.getByText("has been")).toBeInTheDocument();
    expect(dialog.getByRole("radio", { name: "A" })).toBeChecked();
    expect(dialog.getByRole("radio", { name: "B, used by another phrase" })).toBeDisabled();
    expect(dialog.getByRole("button", { name: "Change label" })).toBeInTheDocument();

    await user.click(dialog.getByRole("radio", { name: "C" }));
    await user.click(dialog.getByRole("button", { name: "Change label" }));
    await waitFor(() => expect(screen.queryByRole("dialog")).toBeNull());
    expect(emitted()).toBe(FIXTURE.replace('<u data-label="A">has been</u>', '<u data-label="C">has been</u>'));
  });

  it("Bỏ gạch chân gỡ nhãn khỏi cả cụm; cụm kia giữ nguyên", async () => {
    const { emitted } = renderEditor(FIXTURE);
    await waitFor(() => expect(document.querySelector(".ProseMirror")).not.toBeNull());

    select("has been", true);
    const { user, dialog } = await openDialog();
    await user.click(dialog.getByRole("button", { name: "Remove underline" }));
    await waitFor(() => expect(screen.queryByRole("dialog")).toBeNull());
    expect(emitted()).toBe(FIXTURE.replace('<u data-label="A">has been</u>', "has been"));
  });

  it("gạch chân thường bị thay bằng nhãn, không còn <u> trần ở cụm đó", async () => {
    const { emitted } = renderEditor(FIXTURE);
    await waitFor(() => expect(document.querySelector(".ProseMirror")).not.toBeNull());

    select("Plain underline");
    const { user, dialog } = await openDialog();
    expect(dialog.getByRole("radio", { name: "C" })).toBeChecked();
    await user.click(dialog.getByRole("button", { name: "Underline" }));
    await waitFor(() => expect(screen.queryByRole("dialog")).toBeNull());
    expect(emitted()).toBe(FIXTURE.replace("<u>Plain underline</u>", '<u data-label="C">Plain underline</u>'));
  });

  it("con trỏ trong cụm đã gạch: nút Chèn bật và khung Cụm đang chọn hiện; Đổi nhãn mở hộp, Bỏ gạch chân gỡ nhãn", async () => {
    const { emitted } = renderEditor(FIXTURE);
    await waitFor(() => expect(document.querySelector(".ProseMirror")).not.toBeNull());

    select("has been", true);
    expect(await screen.findByRole("button", { name: "Labeled underline", pressed: true })).toBeInTheDocument();
    const panel = await screen.findByRole("group", { name: "Selected phrase: label A" });
    const user = userEvent.setup();
    await user.click(within(panel).getByRole("button", { name: "Change label" }));
    expect(await screen.findByRole("dialog", { name: "Labeled underline" })).toBeInTheDocument();
    await user.keyboard("{Escape}");
    await waitFor(() => expect(screen.queryByRole("dialog")).toBeNull());

    await user.click(within(await screen.findByRole("group", { name: "Selected phrase: label A" })).getByRole("button", { name: "Remove underline" }));
    expect(emitted()).toBe(FIXTURE.replace('<u data-label="A">has been</u>', "has been"));
  });

  it("chưa bôi đen thì Gạch chân bị tắt và có hướng dẫn", async () => {
    renderEditor(PLAIN);
    await waitFor(() => expect(document.querySelector(".ProseMirror")).not.toBeNull());
    act(() => {
      tiptap().commands.setTextSelection(3);
    });
    const { dialog } = await openDialog();
    expect(dialog.getByRole("status")).toHaveTextContent("Select a phrase in the question text first");
    expect(dialog.getByRole("button", { name: "Underline" })).toBeDisabled();
  });

  it("đã dùng hết A–H thì báo và không cho gạch chân thêm", async () => {
    const letters = ["A", "B", "C", "D", "E", "F", "G", "H"];
    const html = `<p>${letters.map((letter) => `<u data-label="${letter}">w${letter}</u>`).join(" ")} free</p>`;
    renderEditor(html);
    await waitFor(() => expect(document.querySelector(".ProseMirror")).not.toBeNull());
    select("free");
    const { dialog } = await openDialog();
    expect(dialog.getByText("All labels A–H are in use.")).toBeInTheDocument();
    expect(dialog.getByRole("button", { name: "Underline" })).toBeDisabled();
  });
});

describe("khung Cụm đang chọn", () => {
  it("hiện nhãn, Đổi nhãn mở hộp, Bỏ gạch chân gỡ nhãn khỏi cả cụm", async () => {
    const user = userEvent.setup();
    const editor = new Editor({ extensions: createRichTextExtensions(), content: FIXTURE });
    const { from } = rangeOf(editor, "has been");
    editor.commands.setTextSelection(from + 2);
    const mark = editor.state.selection.$from.marks()[0]!;
    const openDialog = vi.fn();
    renderWithProviders(<UnderlineLabelSelectionPanel editor={editor} target={mark} openDialog={openDialog} />, { locale: "en", messages: en });

    expect(screen.getByRole("group", { name: "Selected phrase: label A" })).toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: "Change label" }));
    expect(openDialog).toHaveBeenCalledTimes(1);

    await user.click(screen.getByRole("button", { name: "Remove underline" }));
    expect(editor.getHTML()).toBe(FIXTURE.replace('<u data-label="A">has been</u>', "has been"));
    expect(readLabelSelection(editor.state).label).toBeNull();
    editor.destroy();
  });
});
