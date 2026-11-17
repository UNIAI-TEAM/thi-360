import { describe, expect, it } from "vitest";
import { isEmptyContent, planWordFill } from "../fill-form";
import type { WordPasteResult } from "../word-paste-dialog";

const result = (overrides: Partial<WordPasteResult["fields"]> = {}, split = true): Pick<WordPasteResult, "fields" | "split"> => ({
  split,
  fields: {
    stem: "<p>De</p>",
    options: [
      { label: "A", html: "<p>a</p>" },
      { label: "B", html: "<p>b</p>" },
    ],
    solution: "<p>Giai</p>",
    matched: true,
    ...overrides,
  },
});

describe("isEmptyContent", () => {
  it("treats blank markup as empty and anything with text, a formula, a picture or a table as content", () => {
    for (const empty of ["", "<p></p>", "<p> </p>", "<p><br></p>", null, undefined]) expect(isEmptyContent(empty)).toBe(true);
    for (const filled of ["<p>x</p>", '<p><span class="math-inline">\\(x\\)</span></p>', '<img src="/a" alt="">', "<ul><li><p></p></li></ul>", "<table><tbody><tr><td></td></tr></tbody></table>"]) {
      expect(isEmptyContent(filled)).toBe(false);
    }
  });
});

describe("planWordFill", () => {
  it("writes the question, each option and the solution to their fields when split", () => {
    expect(planWordFill(result(), { choice: true, hasSolution: true }).map((write) => [write.path, write.html])).toEqual([
      ["content", "<p>De</p>"],
      ["body.options.0.content", "<p>a</p>"],
      ["body.options.1.content", "<p>b</p>"],
      ["solution", "<p>Giai</p>"],
    ]);
  });

  it("skips fields the paste left empty, so they keep what the form already has", () => {
    const writes = planWordFill(result({ stem: "", solution: "", options: [{ label: "A", html: "<p></p>" }] }), { choice: true, hasSolution: true });
    expect(writes).toEqual([]);
  });

  it("puts everything into the pasted field when not split", () => {
    const writes = planWordFill(result({ stem: "<p>Tat ca</p>", options: [], solution: "" }, false), { choice: true, hasSolution: true });
    expect(writes).toEqual([{ target: { field: "content" }, path: "content", html: "<p>Tat ca</p>" }]);
  });

  it("does not fill option fields for types without options, and no solution where the form has none", () => {
    const writes = planWordFill(result(), { choice: false, hasSolution: false });
    expect(writes.map((write) => write.path)).toEqual(["content"]);
  });
});
