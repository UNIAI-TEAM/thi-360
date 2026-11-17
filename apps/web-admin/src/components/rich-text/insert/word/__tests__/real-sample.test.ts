import katex from "katex";
import "katex/contrib/mhchem";
import { describe, expect, it } from "vitest";
import { isWordHtml } from "../detect";
import { parseWordHtml } from "../parse";
import { acceptAllFixes, buildFields, startReview, summarize, type ReviewFormula } from "../session";
import { wordFixture } from "./fixtures";

// Mẫu clipboard THẬT chụp từ Word 365 (Windows), 13 công thức OMML: xem `__fixtures__/word365-sample.txt` cho chữ nhìn thấy.
const html = wordFixture("word365-sample.html");
const review = startReview(parseWordHtml(html));
const formulas = review.items as ReviewFormula[];

describe("mẫu clipboard thật của Word 365", () => {
  it("is recognised as Word and every one of its 13 equations is read (no picture, no out-of-subset element)", () => {
    expect(isWordHtml(html)).toBe(true);
    expect(review.items).toHaveLength(13);
    expect(review.items.every((item) => item.kind === "formula")).toBe(true);
    expect(review.images).toEqual([]);
  });

  it("converts each OMML formula to the LaTeX of the expression typed in Word", () => {
    expect(formulas.map((formula) => formula.raw)).toEqual([
      "x=\\frac{-b\\pm\\sqrt{b^2-4ac}}{2a}",
      "x_1+y_n^2",
      "\\sqrt[3]{x+1}+\\left(a+b\\right)^3+\\sin x+{log}_2 8",
      "\\sum_{k=1}^n k^2=\\frac{n(n+1)(2n+1)}{6}",
      "\\int_0^1 x^2dx=\\frac{1}{3}",
      "\\lim_{x\\rightarrow0} \\frac{\\sin x}{x}=1",
      "A=\\begin{pmatrix} 1 & 2 \\\\ 3 & 4 \\end{pmatrix}",
      "\\left\\{\\begin{array}{l} x+y=3 \\\\ x-y=1 \\end{array}\\right.",
      "\\overline{x}+\\vec{a}",
      "sinx+cosx=1",
      "\\left[\\frac{x=1}{x=2}\\right.",
      "\\left(H_2SO_4\\right)",
      "{30}^o",
    ]);
  });

  it("finds exactly the four defects Word's own text shows: function names, \"or\" system, chemistry in brackets, degree", () => {
    const fixed = formulas.filter((formula) => formula.status === "pending");
    expect(fixed.map((formula) => [formula.id, formula.fixes])).toEqual([
      ["w3", ["functionName"]],
      ["w10", ["functionName"]],
      ["w11", ["orSystem"]],
      ["w12", ["chemistry"]],
      ["w13", ["degree"]],
    ]);
    expect(formulas.find((formula) => formula.id === "w10")!.latex).toBe("\\sin x+\\cos x=1");
    expect(formulas.find((formula) => formula.id === "w11")!.latex).toBe("\\left[\\begin{array}{l} x=1 \\\\ x=2 \\end{array}\\right.");
    expect(formulas.find((formula) => formula.id === "w12")!.latex).toBe("\\ce{H2SO4}");
    expect(formulas.find((formula) => formula.id === "w13")!.latex).toBe("{30}^{\\circ}");
    expect(summarize(review)).toEqual({ converted: 8, pending: 5, retype: 0 });
  });

  it("every final formula renders in KaTeX (mhchem loaded)", () => {
    for (const formula of acceptAllFixes(review).items as ReviewFormula[]) {
      expect(() => katex.renderToString(formula.latex, { throwOnError: true }), formula.latex).not.toThrow();
    }
  });

  it("splits the question, options A-D and solution and writes display formulas as span.math-block", () => {
    const fields = buildFields(acceptAllFixes(review), true);
    expect(fields.matched).toBe(true);
    expect(fields.stem.startsWith("<p>Câu 1. Nghiệm của phương trình bậc hai được tính theo công thức sau:</p><span class=\"math-block\">\\[x=\\frac{-b\\pm\\sqrt{b^2-4ac}}{2a}\\]</span>")).toBe(true);
    expect(fields.stem).toContain('<p>Với <span class="math-inline">\\(x_1+y_n^2\\)</span> là các nghiệm, căn bậc ba</p>');
    expect(fields.stem).toContain('<span class="math-block">\\[\\ce{H2SO4}\\]</span>');
    expect(fields.stem).toContain('<span class="math-block">\\[{30}^{\\circ}\\]</span>');
    expect(fields.stem).toContain("<p>Chọn đáp án đúng:</p>");
    expect(fields.options).toEqual([
      { label: "A", html: "<p>1</p>" },
      { label: "B", html: "<p>2</p>" },
      { label: "C", html: "<p>3</p>" },
      { label: "D", html: "<p>4</p>" },
    ]);
    expect(fields.solution).toBe("<p>Áp dụng công thức nghiệm, ta được x = 1.</p>");
  });
});
