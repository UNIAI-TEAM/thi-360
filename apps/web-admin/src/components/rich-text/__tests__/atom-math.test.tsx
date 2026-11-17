import { readFileSync } from "node:fs";
import { join } from "node:path";
import { Editor } from "@tiptap/core";
import { describe, expect, it } from "vitest";
import { createRichTextExtensions } from "../extensions";
import { stripTrailingEmptyParagraph } from "../html-contract";
import { sanitizeQuestionHtml } from "../sanitize";

const fixture = JSON.parse(
  readFileSync(join(process.cwd(), "../../docs/specs/fixtures/rich-text-samples.json"), "utf8"),
) as { samples: { name: string; allowImages: boolean; html: string }[] };

function sample(name: string) {
  const found = fixture.samples.find((candidate) => candidate.name === name);
  if (!found) throw new Error(`no sample ${name}`);
  return found;
}

describe("khối atom trong trình soạn dựng công thức", () => {
  it("renders the formula inside a data-table cell and still emits the stored HTML", () => {
    const { html, allowImages } = sample("data-table-merged-cell-with-math");
    const element = document.createElement("div");
    document.body.append(element);
    const editor = new Editor({ element, extensions: createRichTextExtensions({ allowImages }), content: html });

    const cell = element.querySelector("figure.data-table td");
    expect(cell?.querySelector(".katex")).toBeTruthy();
    expect(cell?.textContent).not.toContain("\\(");

    const emitted = stripTrailingEmptyParagraph(sanitizeQuestionHtml(editor.getHTML(), { allowImages }).html);
    editor.destroy();
    element.remove();
    expect(emitted).toBe(html);
  });

  it("renders a broken formula in an atom as a math-error fallback instead of throwing", () => {
    const element = document.createElement("div");
    document.body.append(element);
    const html = '<figure class="data-table"><table><tbody><tr><td><span class="math-inline">\\(\\badcmd{x}\\)</span></td></tr></tbody></table></figure>';
    const editor = new Editor({ element, extensions: createRichTextExtensions(), content: html });

    const fallback = element.querySelector("figure.data-table td .math-error");
    expect(fallback?.textContent).toBe("\\(\\badcmd{x}\\)");
    editor.destroy();
    element.remove();
  });
});
