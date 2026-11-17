import { readFileSync } from "node:fs";
import { join } from "node:path";
import { act, render, screen, waitFor } from "@testing-library/react";
import userEvent, { type UserEvent } from "@testing-library/user-event";
import { Editor } from "@tiptap/core";
import { StrictMode } from "react";
import { afterEach, describe, expect, it, vi } from "vitest";
import { renderWithProviders } from "@/test/render";
import { createRichTextExtensions } from "../extensions";
import { stripTrailingEmptyParagraph } from "../html-contract";
import QuoteInsertDialog from "../insert/quote/dialog";
import { createQuoteDraft, parseQuote, serializeQuote } from "../insert/quote/quote-model";
import { sanitizeQuestionHtml } from "../sanitize";
import { RichTextView } from "../view/rich-text-view";

interface Sample {
  name: string;
  html: string;
}
const samples = (
  JSON.parse(readFileSync(join(process.cwd(), "../../docs/specs/fixtures/rich-text-samples.json"), "utf8")) as {
    samples: Sample[];
  }
).samples;
const fixture = (name: string) => samples.find((sample) => sample.name === name)!.html;

const POEM = [
  "Buồn trông cửa bể chiều hôm,",
  "Thuyền ai thấp thoáng cánh buồm xa xa?",
  "Buồn trông ngọn nước mới sa,",
  "Hoa trôi man mác biết là về đâu?",
  "",
  "Buồn trông nội cỏ rầu rầu,",
  "Chân mây mặt đất một màu xanh xanh.",
].join("\n");

const editors: Editor[] = [];
function createEditor(content: string): Editor {
  const element = document.createElement("div");
  document.body.append(element);
  const editor = new Editor({ element, extensions: createRichTextExtensions(), content });
  editors.push(editor);
  return editor;
}
const emitted = (editor: Editor) => stripTrailingEmptyParagraph(sanitizeQuestionHtml(editor.getHTML()).html);

afterEach(() => {
  for (const editor of editors.splice(0)) editor.destroy();
});

function renderDialog(editor: Editor) {
  const onOpenChange = vi.fn();
  renderWithProviders(<QuoteInsertDialog editor={editor} open onOpenChange={onOpenChange} editing={null} />);
  return onOpenChange;
}

async function fillContent(user: UserEvent, text: string) {
  await user.click(screen.getByRole("textbox", { name: /Nội dung/ }));
  await user.paste(text);
}

describe("quote model", () => {
  it.each(["quote-poem", "quote-prose-with-source", "quote-footer-then-source"])("reads and writes fixture %s unchanged", (name) => {
    const html = fixture(name);
    const template = document.createElement("template");
    template.innerHTML = html;
    const block = template.content.firstElementChild!;
    const draft = parseQuote({
      kind: block.getAttribute("data-kind"),
      lineNumbers: block.getAttribute("data-line-numbers"),
      html: block.innerHTML,
    });
    expect(serializeQuote(draft)).toBe(html);
  });

  it("writes author-only, work-only and no footer", () => {
    const draft = { ...createQuoteDraft(), content: "x", numberLines: false };
    expect(serializeQuote({ ...draft, author: "A" })).toContain("<footer>(A)</footer>");
    expect(serializeQuote({ ...draft, work: "W" })).toContain("<footer>(<cite>W</cite>)</footer>");
    expect(serializeQuote(draft)).not.toContain("footer");
  });
});

describe("quote dialog", () => {
  it("builds the poem fixture: stanzas as paragraphs, lines as br, footer, numbering every 5 lines", async () => {
    const user = userEvent.setup();
    const editor = createEditor("");
    const onOpenChange = renderDialog(editor);

    expect(screen.getByRole("button", { name: "Chèn đoạn trích" })).toBeDisabled();
    await fillContent(user, POEM);
    await user.type(screen.getByLabelText("Tác giả"), "Nguyễn Du");
    await user.type(screen.getByLabelText("Tác phẩm"), "Truyện Kiều");
    await user.click(screen.getByRole("button", { name: "Chèn đoạn trích" }));

    expect(emitted(editor)).toBe(fixture("quote-poem"));
    expect(onOpenChange).toHaveBeenCalledWith(false);
  });

  it("builds the prose fixture without numbering or line breaks, with a source note", async () => {
    const user = userEvent.setup();
    const editor = createEditor("");
    renderDialog(editor);

    await user.click(screen.getByRole("radio", { name: "Văn xuôi" }));
    expect(screen.getByRole("checkbox", { name: "Giữ nguyên ngắt dòng" })).toBeDisabled();
    expect(screen.getByRole("checkbox", { name: /Đánh số dòng/ })).toBeDisabled();
    await fillContent(user, "Đoạn văn thứ nhất.\n\nĐoạn văn thứ hai.");
    await user.type(screen.getByLabelText("Ghi chú nguồn"), "NXB Giáo dục, 2020, tr. 12");
    await user.click(screen.getByRole("button", { name: "Chèn đoạn trích" }));

    expect(emitted(editor)).toBe(fixture("quote-prose-with-source"));
  });

  it("joins the lines of a prose paragraph and of a poem stanza when line breaks are off", async () => {
    const user = userEvent.setup();
    const editor = createEditor("");
    renderDialog(editor);

    await fillContent(user, "một\nhai\n\nba\nbốn");
    await user.click(screen.getByRole("checkbox", { name: "Giữ nguyên ngắt dòng" }));
    await user.click(screen.getByRole("checkbox", { name: /Đánh số dòng/ }));
    await user.click(screen.getByRole("button", { name: "Chèn đoạn trích" }));

    expect(emitted(editor)).toBe(
      '<blockquote class="quote" data-kind="poem" data-line-numbers="0"><p>một hai</p><p>ba bốn</p></blockquote>',
    );
  });

  it("previews continuous line numbers across stanzas: 5 and 10 only", async () => {
    const user = userEvent.setup();
    renderDialog(createEditor(""));
    const stanza = (n: number) => Array.from({ length: 4 }, (_, i) => `khổ ${n} dòng ${i + 1}`).join("\n");
    await fillContent(user, [stanza(1), stanza(2), stanza(3)].join("\n\n"));

    await waitFor(() => expect(document.querySelectorAll(".quote-line")).toHaveLength(12));
    const numbers = [...document.querySelectorAll(".quote-line-number")].map((marker) => marker.textContent);
    expect(numbers).toEqual(["", "", "", "", "5", "", "", "", "", "10", "", ""]);
  });

  it("reopens a selected poem with every field restored and writes it back unchanged", async () => {
    const user = userEvent.setup();
    const html = fixture("quote-poem");
    const editor = createEditor(`<p>Trước</p>${html}`);
    act(() => {
      editor.commands.setNodeSelection(editor.state.doc.firstChild!.nodeSize);
    });
    const onOpenChange = renderDialog(editor);

    expect(screen.getByRole("radio", { name: "Thơ" })).toBeChecked();
    expect((screen.getByRole("textbox", { name: /Nội dung/ }) as HTMLTextAreaElement).value).toBe(POEM);
    expect(screen.getByRole("checkbox", { name: "Giữ nguyên ngắt dòng" })).toBeChecked();
    expect(screen.getByRole("checkbox", { name: /Đánh số dòng/ })).toBeChecked();
    expect((screen.getByLabelText("Tác giả") as HTMLInputElement).value).toBe("Nguyễn Du");
    expect((screen.getByLabelText("Tác phẩm") as HTMLInputElement).value).toBe("Truyện Kiều");

    await user.click(screen.getByRole("checkbox", { name: /Đánh số dòng/ }));
    await user.type(screen.getByLabelText("Ghi chú nguồn"), "NXB Văn học");
    await user.click(screen.getByRole("button", { name: "Cập nhật đoạn trích" }));

    expect(emitted(editor)).toBe(
      `<p>Trước</p>${html.replace('data-line-numbers="5"', 'data-line-numbers="0"').replace("</blockquote>", '<p class="figure-source">NXB Văn học</p></blockquote>')}`,
    );
    expect(onOpenChange).toHaveBeenCalledWith(false);
  });

  it("reopens a prose excerpt whose footer has no parentheses without changing it", async () => {
    const user = userEvent.setup();
    const html = fixture("quote-footer-then-source");
    const editor = createEditor(html);
    act(() => {
      editor.commands.setNodeSelection(0);
    });
    renderDialog(editor);

    expect(screen.getByRole("radio", { name: "Văn xuôi" })).toBeChecked();
    await user.click(screen.getByRole("button", { name: "Cập nhật đoạn trích" }));
    expect(emitted(editor)).toBe(html);
  });

  it("escapes markup typed into the content", async () => {
    const user = userEvent.setup();
    const editor = createEditor("");
    renderDialog(editor);

    await fillContent(user, "a <b>x</b> & y");
    await user.click(screen.getByRole("button", { name: "Chèn đoạn trích" }));

    expect(emitted(editor)).toContain("<p>a &lt;b&gt;x&lt;/b&gt; &amp; y</p>");
  });
});

describe("RichTextView poem lines", () => {
  it("renders each poem line once in a flat DOM, also after the effect runs again", async () => {
    const poem = fixture("quote-poem");
    const { container, rerender } = render(
      <StrictMode>
        <RichTextView html={poem} blankNumberStart={1} />
      </StrictMode>,
    );
    await waitFor(() => expect(container.querySelectorAll(".quote-line")).toHaveLength(6));
    // Đổi blankNumberStart chạy lại effect trên DOM đã dựng.
    rerender(
      <StrictMode>
        <RichTextView html={poem} blankNumberStart={7} />
      </StrictMode>,
    );

    expect(container.querySelectorAll(".quote-line")).toHaveLength(6);
    expect(container.querySelector(".quote-line .quote-line")).toBeNull();
    expect([...container.querySelectorAll(".quote-line-number")].map((marker) => marker.textContent)).toEqual([
      "",
      "",
      "",
      "",
      "5",
      "",
    ]);
    expect([...container.querySelectorAll(".quote-line-text")].map((line) => line.textContent)).toEqual(
      POEM.split("\n").filter(Boolean),
    );
  });

  it("does not count the source note as a poem line", async () => {
    const html =
      '<blockquote class="quote" data-kind="poem" data-line-numbers="5"><p>a<br>b</p><p class="figure-source">NXB</p></blockquote>';
    const { container } = render(<RichTextView html={html} />);
    await waitFor(() => expect(container.querySelectorAll(".quote-line")).toHaveLength(2));
    expect(container.querySelector("p.figure-source")?.textContent).toBe("NXB");
  });
});
