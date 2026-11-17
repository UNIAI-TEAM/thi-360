import { act, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import type { Editor } from "@tiptap/core";
import { NodeSelection } from "@tiptap/pm/state";
import { FormProvider, useForm } from "react-hook-form";
import { describe, expect, it, vi } from "vitest";
import fixtures from "../../../../../../docs/specs/fixtures/rich-text-samples.json";
import en from "@/messages/en.json";
import { emptyBody, emptyChild, emptyEditorValues, type QuestionEditorValues } from "@/features/questions/editor-form";
import { renderWithProviders } from "@/test/render";
import { BLANK_NODE } from "../extensions";
import { RichTextEditor } from "../rich-text-editor";

const FIXTURE = (fixtures.samples as { name: string; html: string }[]).find((sample) => sample.name === "set-passage-blanks")!.html;
/** Mẫu `set-passage-blanks` với hai chỗ trống thay bằng chữ giữ chỗ X1, X2. */
const PLAIN = FIXTURE.replace(/<span class="blank" data-blank="(\d)"><\/span>/g, "X$1");

function Harness({ value, onChange, start = "1", childCount = 2 }: { value: string; onChange: (html: string) => void; start?: string; childCount?: number }) {
  const form = useForm<QuestionEditorValues>({
    defaultValues: {
      ...emptyEditorValues("QUESTION_SET"),
      body: { ...emptyBody("QUESTION_SET"), blankNumberStart: start },
      children: Array.from({ length: childCount }, () => emptyChild("SINGLE_CHOICE")),
    },
  });
  return (
    <FormProvider {...form}>
      <RichTextEditor value={value} onChange={onChange} fieldContext="set-passage" aria-label="Passage" />
    </FormProvider>
  );
}

function renderPassage(props: { value: string; start?: string; childCount?: number }) {
  const onChange = vi.fn();
  renderWithProviders(<Harness {...props} onChange={onChange} />, { locale: "en", messages: en });
  return { emitted: () => onChange.mock.lastCall?.[0] as string };
}

function tiptap(): Editor {
  const el = document.querySelector<HTMLElement & { editor?: Editor }>(".ProseMirror");
  if (!el?.editor) throw new Error("Editor is not ready");
  return el.editor;
}

function selectText(fragment: string) {
  const editor = tiptap();
  let found = -1;
  editor.state.doc.descendants((node, pos) => {
    const index = found === -1 && node.isText ? (node.text ?? "").indexOf(fragment) : -1;
    if (index !== -1) found = pos + index;
  });
  if (found === -1) throw new Error(`Text not found: ${fragment}`);
  act(() => {
    editor.commands.setTextSelection({ from: found, to: found + fragment.length });
  });
}

function selectBlank(number: number) {
  const editor = tiptap();
  let found = -1;
  editor.state.doc.descendants((node, pos) => {
    if (node.type.name === BLANK_NODE && node.attrs.blank === number) found = pos;
  });
  if (found === -1) throw new Error(`No blank ${number}`);
  act(() => {
    editor.view.dispatch(editor.state.tr.setSelection(NodeSelection.create(editor.state.doc, found)));
  });
}

async function openBlankDialog() {
  const user = userEvent.setup();
  await user.click(screen.getByRole("button", { name: "Blank" }));
  const dialog = await screen.findByRole("dialog", { name: "Blank" });
  return { user, dialog: within(dialog) };
}

const ready = () => waitFor(() => expect(document.querySelector(".ProseMirror")).not.toBeNull());

describe("hộp Chỗ trống đánh số", () => {
  it("gắn câu con đầu tiên chưa có chỗ trống; hai lần chèn ra đúng mẫu set-passage-blanks", async () => {
    const { emitted } = renderPassage({ value: PLAIN });
    await ready();

    selectText("X1");
    let { user, dialog } = await openBlankDialog();
    expect(dialog.getByText(/The new blank is linked to Sub-question 1 ·/)).toBeInTheDocument();
    expect(dialog.getByText("(1) ____")).toBeInTheDocument();
    await user.click(dialog.getByRole("button", { name: "Insert blank" }));
    await waitFor(() => expect(screen.queryByRole("dialog")).toBeNull());
    expect(emitted()).toContain('<span class="blank" data-blank="1"></span>');

    selectText("X2");
    ({ user, dialog } = await openBlankDialog());
    expect(dialog.getByText(/The new blank is linked to Sub-question 2 ·/)).toBeInTheDocument();
    await user.click(dialog.getByRole("button", { name: "Insert blank" }));
    await waitFor(() => expect(screen.queryByRole("dialog")).toBeNull());
    expect(emitted()).toBe(FIXTURE);
  });

  it("hiện số theo Đánh số từ; hết câu con chưa có chỗ trống thì báo Thêm câu con trước", async () => {
    renderPassage({ value: FIXTURE, start: "31" });
    await ready();
    selectText("by Dutch");
    const { dialog } = await openBlankDialog();
    expect(dialog.getByRole("alert")).toHaveTextContent("Add a sub-question first");
    expect(dialog.getByRole("button", { name: "Insert blank" })).toBeDisabled();
  });

  it("câu con thứ ba còn trống: nhận số theo Đánh số từ", async () => {
    renderPassage({ value: FIXTURE, start: "31", childCount: 3 });
    await ready();
    selectText("by Dutch");
    const { dialog } = await openBlankDialog();
    expect(dialog.getByText(/linked to Sub-question 3/)).toBeInTheDocument();
    expect(dialog.getByText("(33) ____")).toBeInTheDocument();
  });

  it("mở lại chỗ trống đang chọn: khôi phục câu con, đổi sang câu con trống khác", async () => {
    const { emitted } = renderPassage({ value: FIXTURE, childCount: 3 });
    await ready();
    selectBlank(1);
    const { user, dialog } = await openBlankDialog();
    // Chỉ câu con 1 (của chính nó) và câu con 3 (chưa có chỗ trống); câu con 2 đã có.
    expect(dialog.getByRole("radio", { name: /Sub-question 1 ·/ })).toBeChecked();
    expect(dialog.queryByRole("radio", { name: /Sub-question 2 ·/ })).toBeNull();
    await user.click(dialog.getByRole("radio", { name: /Sub-question 3 ·/ }));
    await user.click(dialog.getByRole("button", { name: "Change sub-question" }));
    await waitFor(() => expect(screen.queryByRole("dialog")).toBeNull());
    expect(emitted()).toBe(FIXTURE.replace('data-blank="1"', 'data-blank="3"'));
  });

  it("chỗ trống mồ côi: không chọn sẵn câu con nào, nút Đổi khóa tới khi chọn", async () => {
    const { emitted } = renderPassage({ value: FIXTURE.replace('data-blank="2"', 'data-blank="5"'), childCount: 3 });
    await ready();
    selectBlank(5);
    const { user, dialog } = await openBlankDialog();
    const change = dialog.getByRole("button", { name: "Change sub-question" });
    expect(dialog.queryAllByRole("radio", { checked: true })).toHaveLength(0);
    expect(change).toBeDisabled();
    await user.click(dialog.getByRole("radio", { name: /Sub-question 2 ·/ }));
    expect(change).toBeEnabled();
    await user.click(change);
    await waitFor(() => expect(screen.queryByRole("dialog")).toBeNull());
    expect(emitted()).toBe(FIXTURE);
  });

  it("Xóa chỗ trống chỉ bỏ chỗ trống đó", async () => {
    const { emitted } = renderPassage({ value: FIXTURE });
    await ready();
    selectBlank(1);
    const { user, dialog } = await openBlankDialog();
    await user.click(dialog.getByRole("button", { name: "Remove blank" }));
    await waitFor(() => expect(screen.queryByRole("dialog")).toBeNull());
    expect(emitted()).toBe(FIXTURE.replace('<span class="blank" data-blank="1"></span>', ""));
  });
});

describe("số hiển thị của chỗ trống trong trình soạn theo blankNumberStart", () => {
  const shown = () => [...document.querySelectorAll(".rich-text-blank")].map((node) => node.textContent);

  it("hiện (21) (22); đổi số thì dựng lại hiển thị, HTML giữ nguyên và không phát onChange", async () => {
    const onChange = vi.fn();
    const view = renderWithProviders(<RichTextEditor value={FIXTURE} onChange={onChange} fieldContext="set-passage" blankNumberStart={21} aria-label="Passage" />, {
      locale: "en",
      messages: en,
    });
    await waitFor(() => expect(shown()).toEqual(["(21) ____", "(22) ____"]));

    view.rerender(<RichTextEditor value={FIXTURE} onChange={onChange} fieldContext="set-passage" blankNumberStart={31} aria-label="Passage" />);
    await waitFor(() => expect(shown()).toEqual(["(31) ____", "(32) ____"]));
    expect(tiptap().getHTML()).toBe(FIXTURE);
    expect(onChange).not.toHaveBeenCalled();
  });
});
