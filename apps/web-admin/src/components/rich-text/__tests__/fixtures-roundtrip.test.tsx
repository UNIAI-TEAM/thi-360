import { readFileSync } from "node:fs";
import { join } from "node:path";
import { Editor } from "@tiptap/core";
import { describe, expect, it } from "vitest";
import { createRichTextExtensions } from "../extensions";
import { stripTrailingEmptyParagraph } from "../html-contract";
import { sanitizeQuestionHtml } from "../sanitize";

interface Sample {
  name: string;
  valid: boolean;
  allowImages: boolean;
  html: string;
}
const fixture = JSON.parse(
  readFileSync(join(process.cwd(), "../../docs/specs/fixtures/rich-text-samples.json"), "utf8"),
) as { samples: Sample[] };
const validSamples = fixture.samples.filter((sample) => sample.valid);

/**
 * Bảng mẫu là hợp đồng hai phía (spec 3, plan B2): mỗi mẫu hợp lệ nạp vào trình soạn rồi
 * xuất ra đúng chuỗi. Đường đi giống `onChange`: `getHTML` → lọc → bỏ đoạn rỗng cuối mà
 * TrailingNode của StarterKit tự thêm sau khối atom.
 */
describe("fixture round-trip qua trình soạn", () => {
  for (const sample of validSamples) {
    it(`emits the identical string for ${sample.name}`, () => {
      const element = document.createElement("div");
      document.body.append(element);
      const editor = new Editor({
        element,
        extensions: createRichTextExtensions({ allowImages: sample.allowImages }),
        content: sample.html,
      });

      const emitted = stripTrailingEmptyParagraph(
        sanitizeQuestionHtml(editor.getHTML(), { allowImages: sample.allowImages }).html,
      );
      editor.destroy();
      element.remove();

      expect(emitted).toBe(sample.html);
    });
  }

  it("parses every valid sample without dropping content at load time", () => {
    for (const sample of validSamples) {
      const { html, violations } = sanitizeQuestionHtml(sample.html, { allowImages: sample.allowImages });
      expect(violations, `${sample.name}: ${JSON.stringify(violations)}`).toEqual([]);
      expect(html, sample.name).toBe(sample.html);
    }
  });

  it("strips only a trailing empty paragraph", () => {
    expect(stripTrailingEmptyParagraph("<p>x</p><p></p>")).toBe("<p>x</p>");
    expect(stripTrailingEmptyParagraph("<p>x</p><p> </p>")).toBe("<p>x</p>");
    expect(stripTrailingEmptyParagraph("<p>x</p>")).toBe("<p>x</p>");
    expect(stripTrailingEmptyParagraph("<p></p><p>x</p>")).toBe("<p></p><p>x</p>");
    expect(stripTrailingEmptyParagraph("")).toBe("");
  });
});
