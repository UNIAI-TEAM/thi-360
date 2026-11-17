import { readFileSync } from "node:fs";
import { join } from "node:path";
import { render } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { RichTextView } from "../view/rich-text-view";

interface Sample {
  name: string;
  valid: boolean;
  allowImages: boolean;
  html: string;
}

const fixture = JSON.parse(
  readFileSync(join(process.cwd(), "../../docs/specs/fixtures/rich-text-samples.json"), "utf8"),
) as { samples: Sample[] };

const byName = (name: string) => {
  const sample = fixture.samples.find((item) => item.name === name && item.valid);
  if (!sample) throw new Error(`missing valid fixture sample: ${name}`);
  return sample;
};

describe("RichTextView renders every fixture block", () => {
  it("renders inline and display formulas with KaTeX, including mhchem", () => {
    const { container } = render(<RichTextView html={byName("math-inline-and-block").html} />);
    expect(container.querySelectorAll(".katex").length).toBeGreaterThanOrEqual(2);
    expect(container.querySelector(".katex-display")).toBeTruthy();

    const chemistry = render(<RichTextView html={byName("chemistry-mhchem").html} />);
    expect(chemistry.container.querySelectorAll(".katex")).toHaveLength(2);
    // \ce dựng thành công (không phải khung cảnh báo).
    expect(chemistry.container.querySelector(".math-error")).toBeNull();
  });

  it("shows the LaTeX source in a warning frame when the formula is broken", () => {
    const { container } = render(<RichTextView html={'<p><span class="math-inline">\\(\\broken{syntax}\\)</span></p>'} />);
    const error = container.querySelector("span.math-error");
    expect(error).toBeTruthy();
    expect(error!.textContent).toContain("\\broken{syntax}");
  });

  it("renders a data-table figure with caption, headers and num cells", () => {
    const { container } = render(<RichTextView html={byName("data-table").html} />);
    const figure = container.querySelector("figure.data-table");
    expect(figure).toBeTruthy();
    expect(figure!.querySelector("figcaption")).toHaveTextContent("TRỊ GIÁ XUẤT");
    expect(figure!.querySelectorAll('th[scope="col"]')).toHaveLength(3);
    expect(figure!.querySelectorAll("td.num")).toHaveLength(4);
    expect(figure!.querySelector("p.figure-note")).toHaveTextContent("Đơn vị");
    expect(figure!.querySelector("p.figure-source")).toHaveTextContent("Nguồn");
  });

  it("renders a data-table with merged cells and an inline formula", () => {
    const { container } = render(<RichTextView html={byName("data-table-merged-cell-with-math").html} />);
    expect(container.querySelector('th[colspan="2"]')).toBeTruthy();
    expect(container.querySelector("td .katex")).toBeTruthy();
    expect(container.querySelector("td sub")?.textContent).toBe("2");
  });

  it("renders an image figure with alt text, width attr and source", () => {
    const { container } = render(<RichTextView html={byName("image-figure").html} />);
    const figure = container.querySelector("figure.image");
    expect(figure).toBeTruthy();
    expect(figure!.getAttribute("data-width")).toBe("medium");
    const img = figure!.querySelector("img");
    expect(img).toBeTruthy();
    expect(img!.getAttribute("alt")).toBe("Biểu đồ số lượng lợn 2015 - 2021");
    expect(figure!.querySelector("figcaption")).toHaveTextContent("Biểu đồ");
    expect(figure!.querySelector("p.figure-source")).toHaveTextContent("Nguồn");
  });

  it("renders a poem quote with continuous line numbers every 5 lines", () => {
    const { container } = render(<RichTextView html={byName("quote-poem").html} />);
    const quote = container.querySelector("blockquote.quote");
    expect(quote).toBeTruthy();
    expect(quote!.getAttribute("data-kind")).toBe("poem");
    const numbers = [...quote!.querySelectorAll(".quote-line-number")].map((node) => node.textContent);
    // 6 dòng: chỉ dòng thứ 5 mang số.
    expect(numbers).toHaveLength(6);
    expect(numbers.filter(Boolean)).toEqual(["5"]);
    expect(quote!.querySelector("footer cite")).toHaveTextContent("Truyện Kiều");
  });

  it("renders a prose quote without line numbers", () => {
    const { container } = render(<RichTextView html={byName("quote-prose-with-source").html} />);
    const quote = container.querySelector("blockquote.quote");
    expect(quote).toBeTruthy();
    expect(quote!.querySelectorAll(".quote-line")).toHaveLength(0);
    expect(quote!.querySelector("p.figure-source")).toHaveTextContent("NXB Giáo dục");
  });

  it("renders labelled underlines and leaves plain underline plain", () => {
    const { container } = render(<RichTextView html={byName("underline-labels").html} />);
    const labelled = [...container.querySelectorAll("u[data-label]")];
    expect(labelled).toHaveLength(2);
    expect(labelled.map((node) => node.getAttribute("data-label"))).toEqual(["A", "B"]);
    const plain = [...container.querySelectorAll("u:not([data-label])")];
    expect(plain).toHaveLength(1);
    expect(plain[0]).toHaveTextContent("Plain underline");
  });

  it("renders numbered blanks honoring blankNumberStart", () => {
    const { container, rerender } = render(
      <RichTextView html={byName("set-passage-blanks").html} blankNumberStart={31} />,
    );
    const blanks = [...container.querySelectorAll("span.blank")];
    expect(blanks.map((node) => node.textContent)).toEqual(["(31) ____", "(32) ____"]);

    rerender(<RichTextView html={byName("set-passage-blanks").html} blankNumberStart={5} />);
    expect([...container.querySelectorAll("span.blank")].map((node) => node.textContent)).toEqual([
      "(5) ____",
      "(6) ____",
    ]);
  });

  it("defaults blankNumberStart to 1", () => {
    const { container } = render(<RichTextView html={byName("set-passage-blanks").html} />);
    expect(container.querySelector("span.blank")?.textContent).toBe("(1) ____");
  });

  it("renders a cross diagram as a figure table with row headers", () => {
    const { container } = render(<RichTextView html={byName("cross-diagram").html} />);
    const figure = container.querySelector("figure.cross");
    expect(figure).toBeTruthy();
    expect([...figure!.querySelectorAll('th[scope="row"]')].map((node) => node.textContent)).toEqual(["P", "G", "F1"]);
    expect(figure!.querySelectorAll("td")).toHaveLength(3);
  });

  it("renders a legacy plain table with cell paragraphs", () => {
    const { container } = render(<RichTextView html={byName("legacy-plain-table").html} />);
    const table = container.querySelector("table");
    expect(table).toBeTruthy();
    expect(table!.querySelectorAll("td p")).toHaveLength(2);
  });
});
