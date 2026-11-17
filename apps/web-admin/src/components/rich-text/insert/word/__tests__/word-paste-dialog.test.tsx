import { fireEvent, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";
import en from "@/messages/en.json";
import { renderWithProviders } from "@/test/render";
import { WordPasteDialog, type WordPasteResult } from "../word-paste-dialog";
import { wordFixture } from "./fixtures";

// Mỗi hộp dựng KaTeX và một hộp công thức thật: dựng chậm trong jsdom.
vi.setConfig({ testTimeout: 20_000 });

const CHOICE = wordFixture("synthetic-word365-choice.html");
const EXTRAS = wordFixture("synthetic-word365-extras.html");

const SYSTEM = "\\left[\\begin{array}{l} m=0 \\\\ m=\\frac{8}{15} \\end{array}\\right.";

function renderDialog({ html = CHOICE, splitAvailable = true }: { html?: string | null; splitAvailable?: boolean } = {}) {
  const onApply = vi.fn<(result: WordPasteResult) => void>();
  const onOpenChange = vi.fn();
  renderWithProviders(<WordPasteDialog open onOpenChange={onOpenChange} initialHtml={html} splitAvailable={splitAvailable} onApply={onApply} />, {
    locale: "en",
    messages: en,
  });
  return { onApply, onOpenChange };
}

/** Các thẻ mục của danh sách xem lại (không tính mục  lồng bên trong thẻ). */
const reviewRows = () => [...(screen.getByRole("region", { name: "To review" }).querySelector("ol")?.children ?? [])] as HTMLElement[];
const applyButton = () => screen.getByRole("button", { name: "Put into the form" });

/** Mở hộp công thức toán của registry, gõ LaTeX rồi chèn (bản tạm của ED-0b có ô LaTeX và nút "Insert formula"). */
async function retypeWith(row: HTMLElement, latex: string) {
  fireEvent.click(within(row).getByRole("button", { name: "Retype formula" }));
  const dialog = await screen.findByRole("dialog", { name: "Insert formula" });
  fireEvent.change(within(dialog).getByLabelText("LaTeX code"), { target: { value: latex } });
  fireEvent.click(within(dialog).getByRole("button", { name: "Insert formula" }));
  await waitFor(() => expect(screen.queryByRole("dialog", { name: "Insert formula" })).not.toBeInTheDocument());
}

/** "Bỏ" công thức cần gõ lại đầu tiên còn hiện, qua hộp xác nhận. */
async function removeFirstRetype(skip = 0) {
  fireEvent.click(screen.getAllByRole("button", { name: "Remove" })[skip]!);
  fireEvent.click(within(await screen.findByRole("dialog", { name: "Remove this formula?" })).getByRole("button", { name: "Remove" }));
  await waitFor(() => expect(screen.queryByRole("dialog", { name: "Remove this formula?" })).not.toBeInTheDocument());
}

describe("WordPasteDialog: summary bar and review list (design SoanThaoDanTuWord)", () => {
  it("shows the three counts and one row per unhandled item", () => {
    renderDialog();
    expect(screen.getByText("5 formulas converted correctly")).toBeInTheDocument();
    expect(screen.getByText("3 formulas auto-fixed, waiting for confirmation")).toBeInTheDocument();
    expect(screen.getByText("3 formulas need retyping")).toBeInTheDocument();
    expect(reviewRows()).toHaveLength(6);
    // Ô tách mặc định bật: mục tự sửa gắn tên ô (Phương án A, D, Lời giải).
    expect(within(reviewRows()[0]!).getByText("Option A")).toBeInTheDocument();
    expect(within(reviewRows()[1]!).getByText("Option D")).toBeInTheDocument();
    expect(within(reviewRows()[2]!).getByText("Solution")).toBeInTheDocument();
  });

  it("renders the pasted content with the question, options and solution as separate parts", () => {
    renderDialog();
    const preview = screen.getByRole("region", { name: "Pasted content" });
    expect(within(preview).getByText("Question text")).toBeInTheDocument();
    expect(within(preview).getByText("Options")).toBeInTheDocument();
    expect(within(preview).getByText("Solution")).toBeInTheDocument();
    expect(within(preview).getByText("A.")).toBeInTheDocument();
    expect(preview.querySelectorAll(".katex").length).toBeGreaterThan(5);
    // Ba ảnh công thức hiện là chỗ "ảnh công thức"; chỗ tự sửa chờ xác nhận được đánh dấu.
    expect(within(preview).getAllByText("formula picture")).toHaveLength(3);
    expect(preview.querySelectorAll('[data-pending="true"]')).toHaveLength(3);
  });

  it("shows the old and the fixed formula and says what was fixed", () => {
    renderDialog();
    const row = reviewRows()[0]!;
    expect(within(row).getByText("After conversion from Word")).toBeInTheDocument();
    expect(within(row).getByText("Fixed to")).toBeInTheDocument();
    expect(within(row).getByText("“Or” system turned into a fraction.")).toBeInTheDocument();
    expect(within(reviewRows()[2]!).getByText("Function name split into single letters.")).toBeInTheDocument();
    expect(within(reviewRows()[3]!).getByText("The formula is a picture, so its content cannot be read.")).toBeInTheDocument();
  });

  it("\"Show unhandled items only\" hides items that were handled", async () => {
    renderDialog();
    fireEvent.click(within(reviewRows()[0]!).getByRole("button", { name: "Accept" }));
    expect(reviewRows()).toHaveLength(5);
    fireEvent.click(screen.getByRole("checkbox", { name: "Show unhandled items only" }));
    expect(reviewRows()).toHaveLength(6);
    expect(within(reviewRows()[0]!).getByText("Accepted")).toBeInTheDocument();
  });
});

describe("WordPasteDialog: Chấp nhận / Sửa tay / Chấp nhận mọi chỗ", () => {
  it("Accept confirms one auto-fix and updates the summary", () => {
    renderDialog();
    fireEvent.click(within(reviewRows()[0]!).getByRole("button", { name: "Accept" }));
    expect(screen.getByText("2 formulas auto-fixed, waiting for confirmation")).toBeInTheDocument();
    expect(screen.getByText("6 formulas converted correctly")).toBeInTheDocument();
  });

  it("Edit by hand opens the LaTeX, saves the edit and sends it to the form", async () => {
    const { onApply } = renderDialog();
    const user = userEvent.setup();
    fireEvent.click(within(reviewRows()[2]!).getByRole("button", { name: "Edit by hand" }));
    const input = screen.getByLabelText("LaTeX code of this spot");
    expect(input).toHaveValue("\\sin\\widehat{AIB}=1");
    await user.clear(input);
    await user.type(input, "\\sin \\widehat{AIB}=1{Enter}");
    // Enter chỉ lưu bản sửa, không gửi cả hộp.
    expect(onApply).not.toHaveBeenCalled();
    expect(screen.getByText("2 formulas auto-fixed, waiting for confirmation")).toBeInTheDocument();
    fireEvent.click(screen.getByRole("checkbox", { name: "Show unhandled items only" }));
    expect(screen.getByText("Edited")).toBeInTheDocument();
  });

  it("Accept all auto-fixes clears every pending item and then disables itself", () => {
    renderDialog();
    fireEvent.click(screen.getByRole("button", { name: "Accept all auto-fixes" }));
    expect(screen.getByText("0 formulas auto-fixed, waiting for confirmation")).toBeInTheDocument();
    expect(screen.getByText("8 formulas converted correctly")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Accept all auto-fixes" })).toBeDisabled();
    // Ba ảnh công thức vẫn chưa xử lý.
    expect(reviewRows()).toHaveLength(3);
  });
});

describe("WordPasteDialog: công thức cần gõ lại chặn \"Đưa vào biểu mẫu\"", () => {
  it("keeps the fill button disabled until every picture formula is retyped or removed", async () => {
    const { onApply } = renderDialog();
    expect(applyButton()).toBeDisabled();
    expect(screen.getByText("3 formulas still need retyping: retype or remove them before putting the content into the form.")).toBeInTheDocument();

    await retypeWith(reviewRows()[3]!, "(C)");
    expect(screen.getByText("2 formulas need retyping")).toBeInTheDocument();
    expect(applyButton()).toBeDisabled();

    // "Bỏ" hỏi xác nhận trước khi xóa chỗ đó.
    fireEvent.click(within(reviewRows()[3]!).getByRole("button", { name: "Remove" }));
    const confirm = await screen.findByRole("dialog", { name: "Remove this formula?" });
    expect(within(confirm).getByText("Remove this formula?")).toBeInTheDocument();
    fireEvent.click(within(confirm).getByRole("button", { name: "Keep" }));
    await waitFor(() => expect(screen.queryByRole("dialog", { name: "Remove this formula?" })).not.toBeInTheDocument());
    expect(screen.getByText("2 formulas need retyping")).toBeInTheDocument();

    await removeFirstRetype();
    await removeFirstRetype();
    expect(screen.getByText("0 formulas need retyping")).toBeInTheDocument();
    expect(applyButton()).toBeEnabled();

    fireEvent.click(applyButton());
    expect(onApply).toHaveBeenCalledTimes(1);
  });

  it("puts a retyped formula at the spot of the picture and hands it to the form", async () => {
    const { onApply } = renderDialog();
    fireEvent.click(screen.getByRole("checkbox", { name: "Show unhandled items only" }));
    await retypeWith(reviewRows()[3]!, "(C)");
    // Mục đã gõ lại vẫn hiện (bỏ lọc) và bấm được lần nữa: gõ lại thay công thức vừa gõ.
    await retypeWith(reviewRows()[3]!, "(C'): x^2");
    // Mục đã gõ lại vẫn có nút "Bỏ" (đứng đầu): bỏ hai ảnh còn lại.
    await removeFirstRetype(1);
    await removeFirstRetype(1);
    fireEvent.click(applyButton());
    const { fields } = onApply.mock.calls[0]![0];
    expect(fields.solution).toContain('<p>Đường tròn <span class="math-inline">\\((C\'): x^2\\)</span> có tâm , bán kính</p>');
  });

  it("a cancelled formula box changes nothing", async () => {
    renderDialog();
    fireEvent.click(within(reviewRows()[3]!).getByRole("button", { name: "Retype formula" }));
    const dialog = await screen.findByRole("dialog", { name: "Insert formula" });
    fireEvent.click(within(dialog).getByRole("button", { name: "Cancel" }));
    await waitFor(() => expect(screen.queryByRole("dialog", { name: "Insert formula" })).not.toBeInTheDocument());
    expect(screen.getByText("3 formulas need retyping")).toBeInTheDocument();
  });
});

describe("WordPasteDialog: split checkbox and output", () => {
  async function resolveAll(onlyAccept = true) {
    if (onlyAccept) fireEvent.click(screen.getByRole("button", { name: "Accept all auto-fixes" }));
    for (let remaining = 3; remaining > 0; remaining--) await removeFirstRetype();
  }

  it("hands over the question, four options and solution in the editor's HTML form, with the fixed formulas", async () => {
    const { onApply } = renderDialog();
    await resolveAll();
    fireEvent.click(applyButton());
    const result = onApply.mock.calls[0]![0];
    expect(result.split).toBe(true);
    expect(result.pending).toEqual([]);
    expect(result.fields.options.map((option) => option.label)).toEqual(["A", "B", "C", "D"]);
    expect(result.fields.options[0]!.html).toBe(`<p><span class="math-inline">\\(${SYSTEM}\\)</span></p>`);
    expect(result.fields.options[2]!.html).toBe('<p><span class="math-inline">\\(m=\\frac{8}{15}\\)</span></p>');
    expect(result.fields.solution).toBe(
      '<p><span class="math-inline">\\(S_{\\Delta IAB}=\\frac{1}{2}IA\\cdot IB\\cdot\\sin \\widehat{AIB}\\leq\\frac{1}{2}IA\\cdot IB=\\frac{1}{2}R^2=1\\)</span></p><p>Dấu “=” xảy ra: <span class="math-inline">\\(\\sin\\widehat{AIB}=1\\)</span></p><p>Đường tròn có tâm , bán kính</p>',
    );
  });

  it("reports the unconfirmed auto-fixes so the form can keep them marked", async () => {
    const { onApply } = renderDialog();
    await resolveAll(false);
    fireEvent.click(applyButton());
    expect([...onApply.mock.calls[0]![0].pending].sort()).toEqual([SYSTEM.replace("m=0", "m=1"), "\\sin\\widehat{AIB}=1", SYSTEM].sort());
  });

  it("with the split checkbox off, everything goes into the pasted field", async () => {
    const { onApply } = renderDialog();
    fireEvent.click(screen.getByRole("checkbox", { name: /Split by the labels/ }));
    expect(within(reviewRows()[0]!).getByText("Content")).toBeInTheDocument();
    await resolveAll();
    fireEvent.click(applyButton());
    const result = onApply.mock.calls[0]![0];
    expect(result.split).toBe(false);
    expect(result.fields.options).toEqual([]);
    expect(result.fields.solution).toBe("");
    expect(result.fields.stem).toContain("<p><strong>A.</strong>");
    expect(result.fields.stem).toContain("<p><strong>Solution</strong></p>".replace("Solution", "Lời giải"));
  });

  it("hides the split checkbox for question types that have no options or solution here", () => {
    renderDialog({ splitAvailable: false });
    expect(screen.queryByRole("checkbox", { name: /Split by the labels/ })).not.toBeInTheDocument();
    expect(screen.getByRole("region", { name: "Pasted content" })).not.toHaveTextContent("Options");
  });

  it("says so when there are no labels to split on", () => {
    renderDialog({ html: EXTRAS });
    expect(screen.getByText(/No label A\., B\., C\., D\. or “Solution” found/)).toBeInTheDocument();
  });
});

describe("WordPasteDialog: other findings and the paste area", () => {
  it("lists pictures that were not brought in, and out-of-subset formulas as items to retype", () => {
    renderDialog({ html: EXTRAS });
    const images = screen.getByRole("region", { name: "Pictures not brought in" });
    expect(within(images).getByText("Picture 1")).toBeInTheDocument();
    expect(within(images).getByText(/Hình vẽ tam giác ABC/)).toBeInTheDocument();
    expect(within(reviewRows().at(-1)!).getByText(/not supported yet \(m:sPre\)/)).toBeInTheDocument();
    expect(screen.getByText("1 formula needs retyping")).toBeInTheDocument();
  });

  it("opens on a paste area, rejects non-Word HTML and goes on to review after a Word paste", () => {
    renderDialog({ html: null });
    const zone = screen.getByRole("textbox", { name: "Area for pasting Word content" });
    const paste = (html: string) => fireEvent.paste(zone, { clipboardData: { getData: (type: string) => (type === "text/html" ? html : "") } });
    paste("<p>Plain</p>");
    expect(screen.getByRole("alert")).toHaveTextContent("The pasted content is not from Word");
    paste(CHOICE);
    expect(screen.getByText("3 formulas need retyping")).toBeInTheDocument();
    expect(screen.queryByRole("textbox", { name: "Area for pasting Word content" })).not.toBeInTheDocument();
  });
});
