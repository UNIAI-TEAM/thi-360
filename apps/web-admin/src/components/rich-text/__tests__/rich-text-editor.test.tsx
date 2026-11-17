import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { StrictMode, useState } from "react";
import { describe, expect, it, vi } from "vitest";
import en from "@/messages/en.json";
import { renderWithProviders } from "@/test/render";
import type * as QuestionsApi from "@/features/questions/api";
import { uploadQuestionAsset } from "@/features/questions/api";
import { RichTextEditor } from "../rich-text-editor";
import { RichTextView } from "../view/rich-text-view";
import { hasUnbalancedMath, sanitizeQuestionHtml } from "../sanitize";

vi.mock("@/features/questions/api", async (importOriginal) => {
  const actual = await importOriginal<typeof QuestionsApi>();
  // Lớp mẫu đã gỡ: trả tài nguyên giả theo hợp đồng `uploadQuestionAsset` (QA UNI-2621).
  const uploadQuestionAsset = vi.fn(async (file: File): Promise<QuestionsApi.QuestionAsset> => {
    const id = crypto.randomUUID();
    return { id, url: `/api/v1/admin/question-assets/${id}`, fileName: file.name, size: file.size };
  });
  return { ...actual, uploadQuestionAsset };
});

function renderEditor(props: Partial<Parameters<typeof RichTextEditor>[0]> = {}) {
  const onChange = vi.fn();
  const view = renderWithProviders(
    <RichTextEditor value="" onChange={onChange} {...props} />,
    { locale: "en", messages: en },
  );
  return { onChange, ...view };
}

const editable = () => document.querySelector<HTMLElement>(".ProseMirror");

/** Dán HTML thật vào soạn (clipboardData.text/html) — `user.paste` chỉ gửi text/plain. */
function pasteHtml(html: string) {
  fireEvent.paste(editable()!, {
    clipboardData: {
      getData: (format: string) => (format === "text/html" ? html : ""),
      types: ["text/html"],
      files: [],
      items: [],
    },
  });
}

/** Dán clipboard có thể kèm tệp ảnh (Word/Docs gửi `text/html` cùng ảnh chụp đoạn văn). */
function pasteClipboard({ html, files }: { html: string; files: File[] }) {
  fireEvent.paste(editable()!, {
    clipboardData: {
      getData: (format: string) => (format === "text/html" ? html : ""),
      types: html ? ["text/html", "Files"] : ["Files"],
      files,
      items: files.map((file) => ({ kind: "file", type: file.type, getAsFile: () => file })),
    },
  });
}

describe("RichTextEditor", () => {
  it("inserts a formula chip from the toolbar dialog and emits span.math-inline", async () => {
    const user = userEvent.setup();
    const { onChange } = renderEditor();

    await user.click(screen.getByRole("button", { name: "Insert formula" }));
    await user.type(screen.getByLabelText("LaTeX code"), "x^2");
    await user.click(screen.getByRole("button", { name: "Insert formula" }));

    await waitFor(() => {
      expect(onChange).toHaveBeenLastCalledWith(expect.stringContaining('class="math-inline"'));
    });
    const html = onChange.mock.lastCall?.[0] as string;
    expect(html).toContain("\\(x^2\\)");
    expect(editable()?.querySelector("span.math-inline")).toBeTruthy();
  });

  it("does not submit the surrounding page form when the formula dialog is submitted", async () => {
    const user = userEvent.setup();
    const onPageSubmit = vi.fn((event: { preventDefault: () => void }) => event.preventDefault());
    const onChange = vi.fn();
    renderWithProviders(
      <form onSubmit={onPageSubmit}>
        <RichTextEditor value="" onChange={onChange} />
      </form>,
      { locale: "en", messages: en },
    );

    await user.click(screen.getByRole("button", { name: "Insert formula" }));
    await user.type(screen.getByLabelText("LaTeX code"), "x^2");
    await user.click(screen.getByRole("button", { name: "Insert formula" }));

    await waitFor(() => {
      expect(onChange).toHaveBeenLastCalledWith(expect.stringContaining('class="math-inline"'));
    });
    expect(onPageSubmit).not.toHaveBeenCalled();
  });

  it("shows a tooltip with the aria-label text when hovering a toolbar icon button", async () => {
    const user = userEvent.setup();
    renderEditor();

    await user.hover(screen.getByRole("button", { name: "Bold" }));

    expect(await screen.findByRole("tooltip")).toHaveTextContent("Bold");
  });

  it("inserts a block formula when Block is chosen", async () => {
    const user = userEvent.setup();
    const { onChange } = renderEditor();

    await user.click(screen.getByRole("button", { name: "Insert formula" }));
    await user.click(screen.getByRole("radio", { name: "Block" }));
    fireEvent.change(screen.getByLabelText("LaTeX code"), { target: { value: "\\sum_{i=1}^n i" } });
    await user.click(screen.getByRole("button", { name: "Insert formula" }));

    await waitFor(() => {
      expect(onChange).toHaveBeenLastCalledWith(expect.stringContaining('class="math-block"'));
    });
  });

  it("inserts an image via uploadQuestionAsset (mock) with data-asset-id", async () => {
    const user = userEvent.setup();
    const { onChange, container } = renderEditor();

    const input = container.querySelector<HTMLInputElement>('input[type="file"]');
    expect(input).toBeTruthy();
    const file = new File(["png"], "diagram.png", { type: "image/png" });
    await user.upload(input!, file);

    await waitFor(() => {
      expect(onChange).toHaveBeenLastCalledWith(expect.stringContaining("<img"));
    });
    const html = onChange.mock.lastCall?.[0] as string;
    expect(html).toContain("data-asset-id=");
    expect(html).toContain('src="/api/v1/admin/question-assets/');
  });

  it("drops disallowed tags and attributes from pasted HTML", async () => {
    const { onChange } = renderEditor();

    editable()!.focus();
    pasteHtml(
      '<p onclick="x()">ok</p><script>alert(1)</script><iframe src="https://evil"></iframe><a href="https://evil">link</a><div>wrapped</div>',
    );

    await waitFor(() => expect(onChange).toHaveBeenCalled());
    const html = onChange.mock.lastCall?.[0] as string;
    expect(html).not.toContain("script");
    expect(html).not.toContain("iframe");
    expect(html).not.toContain("onclick");
    expect(html).not.toContain("<a");
    expect(html).not.toContain("<div");
    expect(html).toContain("ok");
    expect(html).toContain("wrapped");
  });

  it("blocks image paste and shows no image button when allowImages=false", async () => {
    const { onChange } = renderEditor({ allowImages: false });

    expect(screen.queryByRole("button", { name: "Insert image" })).toBeNull();

    editable()!.focus();
    pasteHtml(
      '<img src="/api/v1/admin/question-assets/9b8f8b1f-9b8f-4b8f-9b8f-9b8f8b8f8b8f" data-asset-id="9b8f8b1f-9b8f-4b8f-9b8f-9b8f8b8f8b8f" alt="x"><p>after</p>',
    );

    await waitFor(() => expect(onChange).toHaveBeenCalled());
    const html = onChange.mock.lastCall?.[0] as string;
    expect(html).not.toContain("<img");
    expect(html).toContain("after");
  });

  describe("paste with an image file on the clipboard", () => {
    const imageFile = () => new File(["png"], "shot.png", { type: "image/png" });

    for (const allowImages of [true, false]) {
      it(`keeps the pasted text when text/html is present (allowImages=${allowImages})`, async () => {
        vi.mocked(uploadQuestionAsset).mockClear();
        const { onChange } = renderEditor({ allowImages });

        editable()!.focus();
        pasteClipboard({ html: "<p>Word text</p>", files: [imageFile()] });

        await waitFor(() => expect(onChange).toHaveBeenCalledWith(expect.stringContaining("Word text")));
        expect(uploadQuestionAsset).not.toHaveBeenCalled();
        expect(onChange.mock.lastCall?.[0]).not.toContain("<img");
      });
    }

    it("uploads a bare image file when text/html is empty and allowImages=true", async () => {
      vi.mocked(uploadQuestionAsset).mockClear();
      const { onChange } = renderEditor();

      editable()!.focus();
      pasteClipboard({ html: "", files: [imageFile()] });

      await waitFor(() => expect(onChange).toHaveBeenCalledWith(expect.stringContaining("<img")));
      expect(uploadQuestionAsset).toHaveBeenCalledTimes(1);
    });

    it("ignores a bare image file when text/html is empty and allowImages=false", async () => {
      vi.mocked(uploadQuestionAsset).mockClear();
      const { onChange } = renderEditor({ allowImages: false });

      editable()!.focus();
      pasteClipboard({ html: "", files: [imageFile()] });

      const { promise: settled, resolve } = Promise.withResolvers<void>();
      setTimeout(resolve, 50);
      await settled;
      expect(uploadQuestionAsset).not.toHaveBeenCalled();
      expect(onChange).not.toHaveBeenCalled();
    });
  });

  it("updates aria-invalid, aria-label and aria-describedby on the editable after mount", async () => {
    const user = userEvent.setup();
    function Harness() {
      const [invalid, setInvalid] = useState(false);
      return (
        <>
          <button onClick={() => setInvalid((current) => !current)}>toggle</button>
          <RichTextEditor
            value=""
            onChange={() => {}}
            invalid={invalid}
            aria-label={invalid ? "Row 2" : "Row 1"}
            aria-describedby={invalid ? "err" : undefined}
          />
        </>
      );
    }
    renderWithProviders(<Harness />, { locale: "en", messages: en });
    await waitFor(() => expect(editable()).toBeTruthy());
    expect(editable()).toHaveAttribute("aria-label", "Row 1");
    expect(editable()).not.toHaveAttribute("aria-invalid");

    await user.click(screen.getByRole("button", { name: "toggle" }));
    await waitFor(() => expect(editable()).toHaveAttribute("aria-invalid", "true"));
    expect(editable()).toHaveAttribute("aria-label", "Row 2");
    expect(editable()).toHaveAttribute("aria-describedby", "err");
    expect(editable()).toHaveClass("rich-text-editor-content");

    await user.click(screen.getByRole("button", { name: "toggle" }));
    await waitFor(() => expect(editable()).not.toHaveAttribute("aria-invalid"));
    expect(editable()).toHaveAttribute("aria-label", "Row 1");
    expect(editable()).not.toHaveAttribute("aria-describedby");
  });
  it("shows the unbalanced-delimiter warning (NO_GAPS wording) while editing", async () => {
    function Harness() {
      const [html, setHtml] = useState("");
      return <RichTextEditor value={html} onChange={setHtml} />;
    }
    renderWithProviders(<Harness />, { locale: "en", messages: en });

    editable()!.focus();
    pasteHtml("<p>formula \\( x^2 open</p>");

    expect(await screen.findByRole("alert")).toHaveTextContent("Math delimiters are unbalanced");
  });
});

describe("RichTextView", () => {
  it("renders formulas with KaTeX inside math spans", () => {
    const { container } = render(
      <RichTextView
        html={'<p>Cho <span class="math-inline">\\(x^2\\)</span> và <span class="math-block">\\[\\int_0^1 x\\]</span></p>'}
      />,
    );
    expect(container.querySelectorAll(".katex").length).toBeGreaterThanOrEqual(2);
    expect(container.querySelector(".katex-display")).toBeTruthy();
  });

  it("keeps rendered formulas when the parent re-renders with the same html", async () => {
    const user = userEvent.setup();
    function Harness() {
      const [count, setCount] = useState(0);
      return (
        <>
          <button onClick={() => setCount(count + 1)}>rerender {count}</button>
          <RichTextView html={'<p><span class="math-inline">\\(x^2\\)</span></p>'} />
        </>
      );
    }
    const { container } = render(<Harness />);
    const formula = container.querySelector(".katex");
    expect(formula).toBeTruthy();

    await user.click(screen.getByRole("button", { name: "rerender 0" }));
    await screen.findByRole("button", { name: "rerender 1" });
    expect(container.querySelector(".katex")).toBe(formula);
  });

  it("renders each formula once with the original LaTeX when effects run twice", async () => {
    const user = userEvent.setup();
    const html = '<p><span class="math-inline">\\(x^2+\\frac{1}{2}\\)</span> và Tính \\(a+b\\)</p>';
    function Harness() {
      const [count, setCount] = useState(0);
      return (
        <>
          <button onClick={() => setCount(count + 1)}>rerender {count}</button>
          <RichTextView html={html} />
        </>
      );
    }
    const { container } = render(
      <StrictMode>
        <Harness />
      </StrictMode>,
    );
    await user.click(screen.getByRole("button", { name: "rerender 0" }));
    await screen.findByRole("button", { name: "rerender 1" });

    const annotations = [...container.querySelectorAll(".katex-mathml annotation")].map((node) => node.textContent);
    expect(annotations).toEqual(["x^2+\\frac{1}{2}", "a+b"]);
    expect(container.querySelectorAll(".katex")).toHaveLength(2);
  });

  it("renders plain-text delimiters with KaTeX too", () => {
    const { container } = render(<RichTextView html={"<p>Tính \\(1+1\\)</p>"} />);
    expect(container.querySelector(".katex")).toBeTruthy();
  });

  it("strips disallowed tags and attributes before rendering", () => {
    const { container } = render(
      <RichTextView
        html={'<p>ok</p><script>alert(1)</script><a href="https://e">x</a><p style="color:red">y</p>'}
      />,
    );
    expect(container.innerHTML).not.toContain("script");
    expect(container.innerHTML).not.toContain("<a");
    expect(container.innerHTML).not.toContain("style=");
  });

  it("drops images when allowImages=false", () => {
    const { container } = render(
      <RichTextView
        allowImages={false}
        html={'<p>before<img src="/x.png" data-asset-id="9b8f8b1f-9b8f-4b8f-9b8f-9b8f8b8f8b8f" alt="a">after</p>'}
      />,
    );
    expect(container.querySelector("img")).toBeNull();
    expect(container.textContent).toContain("before");
    expect(container.textContent).toContain("after");
  });
});

describe("sanitizeQuestionHtml / hasUnbalancedMath", () => {
  it("keeps td/th colspan+rowspan and span math classes only", () => {
    const { html } = sanitizeQuestionHtml(
      '<table><tr><th colspan="2" style="x">h</th></tr><tr><td rowspan="2" data-x="1">d</td></tr></table><span class="math-inline">\\(a\\)</span><span class="other">b</span>',
    );
    expect(html).toContain('colspan="2"');
    expect(html).toContain('rowspan="2"');
    expect(html).not.toContain("style");
    expect(html).not.toContain("data-x");
    expect(html).toContain('class="math-inline"');
    expect(html).not.toContain('class="other"');
  });

  it("drops external img src and keeps internal paths", () => {
    const { html } = sanitizeQuestionHtml(
      '<img src="https://evil/x.png" data-asset-id="9b8f8b1f-9b8f-4b8f-9b8f-9b8f8b8f8b8f"><img src="/api/v1/admin/question-assets/9b8f8b1f-9b8f-4b8f-9b8f-9b8f8b8f8b8f" data-asset-id="9b8f8b1f-9b8f-4b8f-9b8f-9b8f8b8f8b8f" alt="k">',
    );
    expect(html).not.toContain("https://evil");
    expect(html).toContain('src="/api/v1/admin/question-assets/9b8f8b1f-9b8f-4b8f-9b8f-9b8f8b8f8b8f"');
  });

  it("reports violations for dropped parts", () => {
    const { violations } = sanitizeQuestionHtml('<script>s</script><p onclick="a()">x</p>');
    expect(violations.some((v) => v.tag === "script")).toBe(true);
    expect(violations.some((v) => v.tag === "p" && v.attribute === "onclick")).toBe(true);
  });

  it("detects unbalanced formula delimiters like the backend NO_GAPS check", () => {
    expect(hasUnbalancedMath("<p>\\(x^2\\)</p>")).toBe(false);
    expect(hasUnbalancedMath("<p>\\[x\\]</p>")).toBe(false);
    expect(hasUnbalancedMath("<p>\\(x</p>")).toBe(true);
    expect(hasUnbalancedMath("<p>x\\)</p>")).toBe(true);
    expect(hasUnbalancedMath("<p>\\(a\\]b\\)</p>")).toBe(true);
  });
});
