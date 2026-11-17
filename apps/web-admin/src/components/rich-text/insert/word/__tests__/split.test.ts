import { describe, expect, it } from "vitest";
import { splitByLabels } from "../split";

function paragraph(html: string): Element {
  const block = document.createElement("p");
  block.innerHTML = html;
  return block;
}

describe("splitByLabels: solution header in decomposed (NFD) Vietnamese", () => {
  it.each(["NFC", "NFD"] as const)("cuts exactly after the header when the clipboard text is %s", (form) => {
    const header = "Lời giải:".normalize(form);
    const split = splitByLabels([paragraph(`${header} Ta có x=1`)]);
    expect(split.found).toBe(true);
    expect(split.solution.map((piece) => piece.textContent)).toEqual(["Ta có x=1"]);
    expect(split.stem).toHaveLength(0);
  });

  it("keeps formatting around the header and the rest of the paragraph in NFD", () => {
    const header = "Hướng dẫn giải".normalize("NFD");
    const split = splitByLabels([paragraph(`<strong>${header}</strong>: <em>Đáp án</em> B`)]);
    expect(split.solution.map((piece) => piece.innerHTML)).toEqual(["<em>Đáp án</em> B"]);
  });
});
