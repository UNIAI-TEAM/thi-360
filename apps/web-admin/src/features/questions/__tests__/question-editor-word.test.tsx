import { fireEvent, screen, waitFor, within } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { wordFixture } from "@/components/rich-text/insert/word/__tests__/fixtures";
import { clearWordPending } from "@/components/rich-text/insert/word/pending-marks";
import { QuestionEditorView } from "../components/question-editor-view";
import { renderEn, rich } from "./editor-test-kit";

const navigation = vi.hoisted(() => ({ push: vi.fn() }));
vi.mock("next/navigation", () => ({
  usePathname: () => "/questions/new",
  useRouter: () => ({ push: navigation.push, replace: vi.fn(), back: vi.fn(), refresh: vi.fn(), prefetch: vi.fn() }),
}));

const CHOICE = wordFixture("synthetic-word365-choice.html");
const SYSTEM = "\\left[\\begin{array}{l} m=0 \\\\ m=\\frac{8}{15} \\end{array}\\right.";

beforeEach(() => {
  navigation.push.mockReset();
  clearWordPending();
});

const stemField = () => screen.getByRole("textbox", { name: /^Question text/, hidden: true });
const optionField = (letter: string) => screen.getByRole("textbox", { name: `Option ${letter} text`, hidden: true });
const solutionField = () => screen.getByRole("textbox", { name: /^Solution/, hidden: true });
// Hộp tải lười (import động) nên chờ lâu hơn mặc định khi máy đang chạy nhiều test.
const wordDialog = () => screen.findByRole("dialog", { name: "Paste from Word" }, { timeout: 10_000 });
const putIntoForm = () => screen.getByRole("button", { name: "Put into the form" });

function pasteInto(target: HTMLElement, html: string) {
  return fireEvent.paste(target, { clipboardData: { getData: (type: string) => (type === "text/html" ? html : ""), types: ["text/html"], files: [], items: [] } });
}

/** Xử lý hết ảnh công thức ("Bỏ") để nút "Đưa vào biểu mẫu" bật. */
async function removePictureFormulas() {
  for (let remaining = 3; remaining > 0; remaining--) {
    fireEvent.click(screen.getAllByRole("button", { name: "Remove" })[0]!);
    fireEvent.click(within(await screen.findByRole("dialog", { name: "Remove this formula?" })).getByRole("button", { name: "Remove" }));
    await waitFor(() => expect(screen.queryByRole("dialog", { name: "Remove this formula?" })).not.toBeInTheDocument());
  }
}

async function openSingleChoiceWithWord() {
  renderEn(<QuestionEditorView mode="create" type="SINGLE_CHOICE" />);
  await screen.findByRole("textbox", { name: /^Question text/ });
  pasteInto(document.querySelector<HTMLElement>(`#${CSS.escape(stemField().id)}`)!, CHOICE);
  return wordDialog();
}

describe("Dán từ Word trong biểu mẫu soạn câu (spec 4.10)", () => {
  it("has a \"Paste from Word\" button at the top that opens the dialog on a paste area", async () => {
    renderEn(<QuestionEditorView mode="create" type="SINGLE_CHOICE" />);
    fireEvent.click(await screen.findByRole("button", { name: "Paste from Word" }));
    const dialog = await wordDialog();
    const zone = within(dialog).getByRole("textbox", { name: "Area for pasting Word content" });
    pasteInto(zone, CHOICE);
    expect(within(dialog).getByText("3 formulas need retyping")).toBeInTheDocument();
  });

  it("catches a Word paste into the question text before the filter: the dialog opens and nothing is pasted into the field", async () => {
    const dialog = await openSingleChoiceWithWord();
    expect(within(dialog).getByText("5 formulas converted correctly")).toBeInTheDocument();
    expect(rich(stemField()).text).toBe("");
  });

  it("lets HTML that is not from Word take the old paste path", async () => {
    renderEn(<QuestionEditorView mode="create" type="SINGLE_CHOICE" />);
    await screen.findByRole("textbox", { name: /^Question text/ });
    const proseMirror = stemField();
    pasteInto(proseMirror, "<p>Plain <strong>paste</strong></p>");
    expect(screen.queryByRole("dialog", { name: "Paste from Word" })).not.toBeInTheDocument();
    // Đường dán cũ vẫn chạy: nội dung đi qua bộ lọc và vào ô.
    await waitFor(() => expect(rich(stemField()).text).toContain("Plain paste"));
  });

  it("fills the question, four options and solution with the converted formulas in the editor's HTML form", async () => {
    await openSingleChoiceWithWord();
    fireEvent.click(screen.getByRole("button", { name: "Accept all auto-fixes" }));
    expect(putIntoForm()).toBeDisabled();
    await removePictureFormulas();
    fireEvent.click(putIntoForm());

    await waitFor(() => expect(screen.queryByRole("dialog", { name: "Paste from Word" })).not.toBeInTheDocument());
    await waitFor(() => expect(rich(optionField("A")).html).toContain("math-inline"));
    expect(rich(stemField()).html).toContain('<span class="math-inline">\\((C):x^2+y^2+4x+4y+6=0\\)</span>');
    expect(rich(optionField("A")).html).toBe(`<p><span class="math-inline">\\(${SYSTEM}\\)</span></p>`);
    expect(rich(optionField("B")).html).toBe('<p><span class="math-inline">\\(m=0\\)</span></p>');
    expect(rich(optionField("C")).html).toBe('<p><span class="math-inline">\\(m=\\frac{8}{15}\\)</span></p>');
    expect(rich(solutionField()).html).toContain('<p>Dấu “=” xảy ra: <span class="math-inline">\\(\\sin\\widehat{AIB}=1\\)</span></p>');
  });

  it("asks before overwriting a field that already has content and keeps it when declined", async () => {
    renderEn(<QuestionEditorView mode="create" type="SINGLE_CHOICE" />);
    await screen.findByRole("textbox", { name: /^Question text/ });
    rich(stemField()).set("<p>Existing question</p>");
    pasteInto(stemField(), CHOICE);
    const dialog = await wordDialog();
    fireEvent.click(within(dialog).getByRole("button", { name: "Accept all auto-fixes" }));
    await removePictureFormulas();
    fireEvent.click(putIntoForm());

    const confirm = await screen.findByRole("dialog", { name: "Overwrite the current content?" });
    // Chỉ ô đề bài đang có nội dung; phương án và lời giải còn trống nên điền thẳng, không nằm trong danh sách hỏi.
    expect(within(confirm).getByText("These fields already have content and will be replaced with the content from Word: Question text.")).toBeInTheDocument();
    fireEvent.click(within(confirm).getByRole("button", { name: "Keep current content" }));
    await waitFor(() => expect(screen.queryByRole("dialog", { name: "Overwrite the current content?" })).not.toBeInTheDocument());
    expect(rich(stemField()).text).toBe("Existing question");
    expect(screen.getByRole("dialog", { name: "Paste from Word" })).toBeInTheDocument();

    fireEvent.click(putIntoForm());
    fireEvent.click(within(await screen.findByRole("dialog", { name: "Overwrite the current content?" })).getByRole("button", { name: "Overwrite" }));
    await waitFor(() => expect(rich(stemField()).text).toContain("Trong mặt phẳng tọa độ Oxy"));
    expect(rich(optionField("B")).html).toBe('<p><span class="math-inline">\\(m=0\\)</span></p>');
  });

  it("does not ask when every target field is empty", async () => {
    await openSingleChoiceWithWord();
    fireEvent.click(screen.getByRole("button", { name: "Accept all auto-fixes" }));
    await removePictureFormulas();
    fireEvent.click(putIntoForm());
    await waitFor(() => expect(screen.queryByRole("dialog", { name: "Paste from Word" })).not.toBeInTheDocument());
    expect(screen.queryByRole("dialog", { name: "Overwrite the current content?" })).not.toBeInTheDocument();
  });

  it("puts the whole paste into the question field when the split checkbox is off", async () => {
    await openSingleChoiceWithWord();
    fireEvent.click(screen.getByRole("checkbox", { name: /Split by the labels/ }));
    fireEvent.click(screen.getByRole("button", { name: "Accept all auto-fixes" }));
    await removePictureFormulas();
    fireEvent.click(putIntoForm());
    await waitFor(() => expect(rich(stemField()).html).toContain("<strong>A.</strong>"));
    expect(rich(optionField("A")).text).toBe("");
    expect(rich(solutionField()).text).toBe("");
  });

  it("offers no split checkbox for a type without options, and fills everything into the question field", async () => {
    renderEn(<QuestionEditorView mode="create" type="ESSAY" />);
    fireEvent.click(await screen.findByRole("button", { name: "Paste from Word" }));
    const dialog = await wordDialog();
    pasteInto(within(dialog).getByRole("textbox", { name: "Area for pasting Word content" }), CHOICE);
    expect(within(dialog).queryByRole("checkbox", { name: /Split by the labels/ })).not.toBeInTheDocument();
    fireEvent.click(within(dialog).getByRole("button", { name: "Accept all auto-fixes" }));
    await removePictureFormulas();
    fireEvent.click(putIntoForm());
    await waitFor(() => expect(rich(stemField()).html).toContain("<strong>Lời giải</strong>"));
  });
});

describe("chỗ tự sửa chưa xác nhận sau khi đưa vào biểu mẫu", () => {
  it("stays marked with a ProseMirror decoration that never reaches the HTML", async () => {
    await openSingleChoiceWithWord();
    // Không Chấp nhận: ba chỗ tự sửa (phương án A, D, lời giải) còn chờ xác nhận.
    await removePictureFormulas();
    fireEvent.click(putIntoForm());
    await waitFor(() => expect(optionField("A").querySelector('[data-word-pending="true"]')).not.toBeNull());

    expect(optionField("D").querySelector('[data-word-pending="true"]')).not.toBeNull();
    expect(solutionField().querySelector('[data-word-pending="true"]')).not.toBeNull();
    // Công thức đúng (phương án B, C) không bị đánh dấu.
    expect(optionField("B").querySelector('[data-word-pending="true"]')).toBeNull();
    expect(optionField("A").querySelector('[data-word-pending="true"]')?.getAttribute("title")).toBe("Auto-fixed from Word, waiting for confirmation");

    // Decoration không nằm trong HTML của trình soạn, nên không vào giá trị biểu mẫu và không chặn lưu.
    for (const field of [optionField("A"), optionField("D"), solutionField()]) {
      expect(rich(field).html).not.toMatch(/word-pending|bg-warning|outline/);
    }
  });

  it("goes away when the author edits the formula and when the form is left", async () => {
    const { unmount } = renderEn(<QuestionEditorView mode="create" type="SINGLE_CHOICE" />);
    await screen.findByRole("textbox", { name: /^Question text/ });
    pasteInto(stemField(), CHOICE);
    await wordDialog();
    await removePictureFormulas();
    fireEvent.click(putIntoForm());
    await waitFor(() => expect(optionField("A").querySelector('[data-word-pending="true"]')).not.toBeNull());

    // Sửa công thức đổi LaTeX nên dấu tự mất.
    rich(optionField("A")).set('<p><span class="math-inline">\\(x=1\\)</span></p>');
    await waitFor(() => expect(optionField("A").querySelector('[data-word-pending="true"]')).toBeNull());
    expect(optionField("D").querySelector('[data-word-pending="true"]')).not.toBeNull();

    unmount();
    renderEn(<QuestionEditorView mode="create" type="SINGLE_CHOICE" />);
    await screen.findByRole("textbox", { name: /^Question text/ });
    rich(optionField("D")).set(`<p><span class="math-inline">\\(${SYSTEM.replace("m=0", "m=1")}\\)</span></p>`);
    expect(optionField("D").querySelector('[data-word-pending="true"]')).toBeNull();
  });
});
