import katex from "katex";
import { describe, expect, it } from "vitest";
import { MalformedOmmlError, UnsupportedOmmlError, ommlToLatex } from "../omml";

const run = (text: string, style?: string) =>
  `<m:r>${style ? `<m:rPr><m:sty m:val="${style}"/></m:rPr>` : ""}<w:rPr><w:rFonts w:ascii="Cambria Math"/></w:rPr><m:t>${text}</m:t></m:r>`;
const math = (inner: string) => `<m:oMath>${inner}</m:oMath>`;
const latex = (inner: string) => ommlToLatex(math(inner)).latex;

describe("ommlToLatex: element subset of spec 4.10", () => {
  it("m:f fraction, binomial (noBar) and linear fraction", () => {
    expect(latex(`<m:f><m:num>${run("1")}</m:num><m:den>${run("2")}</m:den></m:f>`)).toBe("\\frac{1}{2}");
    expect(latex(`<m:f><m:fPr><m:type m:val="noBar"/></m:fPr><m:num>${run("n")}</m:num><m:den>${run("k")}</m:den></m:f>`)).toBe(
      "\\binom{n}{k}",
    );
    expect(latex(`<m:f><m:fPr><m:type m:val="lin"/></m:fPr><m:num>${run("a")}</m:num><m:den>${run("b")}</m:den></m:f>`)).toBe("{a}/{b}");
  });

  it("m:rad square root, indexed root and hidden degree", () => {
    expect(latex(`<m:rad><m:radPr><m:degHide m:val="1"/></m:radPr><m:deg/><m:e>${run("x")}</m:e></m:rad>`)).toBe("\\sqrt{x}");
    expect(latex(`<m:rad><m:radPr/><m:deg>${run("3")}</m:deg><m:e>${run("x+1")}</m:e></m:rad>`)).toBe("\\sqrt[3]{x+1}");
  });

  it("m:sSup, m:sSub and m:sSubSup", () => {
    expect(latex(`<m:sSup><m:e>${run("x")}</m:e><m:sup>${run("2")}</m:sup></m:sSup>`)).toBe("x^2");
    expect(latex(`<m:sSup><m:e>${run("x")}</m:e><m:sup>${run("n+1")}</m:sup></m:sSup>`)).toBe("x^{n+1}");
    expect(latex(`<m:sSub><m:e>${run("a")}</m:e><m:sub>${run("n")}</m:sub></m:sSub>`)).toBe("a_n");
    expect(latex(`<m:sSubSup><m:e>${run("x")}</m:e><m:sub>${run("1")}</m:sub><m:sup>${run("2")}</m:sup></m:sSubSup>`)).toBe("x_1^2");
    // Cơ sở nhiều ký hiệu phải bọc nhóm để mũ chỉ gắn vào cả cụm.
    expect(latex(`<m:sSup><m:e>${run("x+y")}</m:e><m:sup>${run("2")}</m:sup></m:sSup>`)).toBe("{x+y}^2");
  });

  it("m:d parentheses, custom brackets, open-ended brace and separators", () => {
    expect(latex(`<m:d><m:dPr/><m:e>${run("a+b")}</m:e></m:d>`)).toBe("\\left(a+b\\right)");
    expect(latex(`<m:d><m:dPr><m:begChr m:val="["/><m:endChr m:val="]"/></m:dPr><m:e>${run("0;1")}</m:e></m:d>`)).toBe("\\left[0;1\\right]");
    expect(latex(`<m:d><m:dPr><m:begChr m:val="["/><m:endChr m:val=""/></m:dPr><m:e>${run("x")}</m:e></m:d>`)).toBe("\\left[x\\right.");
    expect(latex(`<m:d><m:dPr><m:begChr m:val="⟨"/><m:endChr m:val="⟩"/><m:sepChr m:val=","/></m:dPr><m:e>${run("a")}</m:e><m:e>${run("b")}</m:e></m:d>`)).toBe(
      "\\left\\langle a , b\\right\\rangle",
    );
  });

  it("m:eqArr system and aligned rows", () => {
    const system = `<m:d><m:dPr><m:begChr m:val="{"/><m:endChr m:val=""/></m:dPr><m:e><m:eqArr><m:e>${run("x+y=1")}</m:e><m:e>${run("x−y=3")}</m:e></m:eqArr></m:e></m:d>`;
    expect(latex(system)).toBe("\\left\\{\\begin{array}{l} x+y=1 \\\\ x-y=3 \\end{array}\\right.");
    expect(latex(`<m:eqArr><m:e>${run("a&amp;=b")}</m:e><m:e>${run("c&amp;=d")}</m:e></m:eqArr>`)).toBe(
      "\\begin{aligned} a&=b \\\\ c&=d \\end{aligned}",
    );
  });

  it("m:nary sum, integral with limits and product without limits", () => {
    expect(
      latex(`<m:nary><m:naryPr><m:chr m:val="∑"/></m:naryPr><m:sub>${run("i=1")}</m:sub><m:sup>${run("n")}</m:sup><m:e>${run("a")}</m:e></m:nary>`),
    ).toBe("\\sum_{i=1}^n a");
    expect(
      latex(`<m:nary><m:naryPr><m:chr m:val="∫"/></m:naryPr><m:sub>${run("0")}</m:sub><m:sup>${run("1")}</m:sup><m:e>${run("f(x)dx")}</m:e></m:nary>`),
    ).toBe("\\int_0^1 f(x)dx");
    expect(
      latex(`<m:nary><m:naryPr><m:chr m:val="∏"/><m:supHide m:val="1"/></m:naryPr><m:sub>${run("k")}</m:sub><m:sup/><m:e>${run("a")}</m:e></m:nary>`),
    ).toBe("\\prod_k a");
  });

  it("m:func named function, function with limit, unknown name", () => {
    expect(latex(`<m:func><m:fName>${run("sin", "p")}</m:fName><m:e>${run("x")}</m:e></m:func>`)).toBe("\\sin x");
    expect(
      latex(
        `<m:func><m:fName><m:limLow><m:e>${run("lim", "p")}</m:e><m:lim>${run("x→0")}</m:lim></m:limLow></m:fName><m:e>${run("f(x)")}</m:e></m:func>`,
      ),
    ).toBe("\\lim_{x\\rightarrow0} f(x)");
    expect(latex(`<m:func><m:fName>${run("tr", "p")}</m:fName><m:e>${run("A")}</m:e></m:func>`)).toBe("\\operatorname{tr} A");
  });

  it("m:m matrix, matrix inside brackets becomes pmatrix", () => {
    const matrix = `<m:m><m:mr><m:e>${run("1")}</m:e><m:e>${run("2")}</m:e></m:mr><m:mr><m:e>${run("3")}</m:e><m:e>${run("4")}</m:e></m:mr></m:m>`;
    expect(latex(matrix)).toBe("\\begin{matrix} 1 & 2 \\\\ 3 & 4 \\end{matrix}");
    expect(latex(`<m:d><m:dPr/><m:e>${matrix}</m:e></m:d>`)).toBe("\\begin{pmatrix} 1 & 2 \\\\ 3 & 4 \\end{pmatrix}");
    expect(latex(`<m:d><m:dPr><m:begChr m:val="["/><m:endChr m:val="]"/></m:dPr><m:e>${matrix}</m:e></m:d>`)).toContain("\\begin{bmatrix}");
  });

  it("m:acc hat, wide hat and vector arrows", () => {
    expect(latex(`<m:acc><m:accPr><m:chr m:val="̂"/></m:accPr><m:e>${run("A")}</m:e></m:acc>`)).toBe("\\hat{A}");
    expect(latex(`<m:acc><m:accPr><m:chr m:val="̂"/></m:accPr><m:e>${run("AIB")}</m:e></m:acc>`)).toBe("\\widehat{AIB}");
    expect(latex(`<m:acc><m:accPr><m:chr m:val="⃗"/></m:accPr><m:e>${run("a")}</m:e></m:acc>`)).toBe("\\vec{a}");
    expect(latex(`<m:acc><m:accPr><m:chr m:val="⃗"/></m:accPr><m:e>${run("AB")}</m:e></m:acc>`)).toBe("\\overrightarrow{AB}");
  });

  it("m:bar overline (default) and underline", () => {
    expect(latex(`<m:bar><m:barPr><m:pos m:val="top"/></m:barPr><m:e>${run("AB")}</m:e></m:bar>`)).toBe("\\overline{AB}");
    expect(latex(`<m:bar><m:barPr><m:pos m:val="bot"/></m:barPr><m:e>${run("AB")}</m:e></m:bar>`)).toBe("\\underline{AB}");
  });

  it("m:limLow and m:limUpp", () => {
    expect(latex(`<m:limLow><m:e>${run("lim", "p")}</m:e><m:lim>${run("n→∞")}</m:lim></m:limLow>`)).toBe("\\lim_{n\\rightarrow\\infty}");
    expect(latex(`<m:limLow><m:e>${run("x")}</m:e><m:lim>${run("def")}</m:lim></m:limLow>`)).toBe("\\underset{def}{x}");
    expect(latex(`<m:limUpp><m:e>${run("=")}</m:e><m:lim>${run("def")}</m:lim></m:limUpp>`)).toBe("\\overset{def}{=}");
  });

  it("m:r text: Greek letters, symbols, upright units, Vietnamese text and decimal commas", () => {
    expect(latex(run("α≤β"))).toBe("\\alpha\\leq\\beta");
    expect(latex(run("a⋅b×c"))).toBe("a\\cdot b\\times c");
    expect(latex(run("kJ", "p"))).toBe("\\mathrm{kJ}");
    expect(latex(run("tại", "nor"))).toBe("\\text{tại}");
    expect(latex(run("với x"))).toBe("\\text{với} x");
    expect(latex(run("179,2"))).toBe("179{,}2");
    // Quy ước Việt Nam: "1,2" là số thập phân, danh sách viết bằng dấu chấm phẩy hoặc có khoảng trắng sau dấu phẩy.
    expect(latex(run("f(1;2) a, b"))).toBe("f(1;2) a, b");
    expect(latex(run("50%"))).toBe("50\\%");
  });

  it("m:oMathPara is a display formula, m:oMath is inline", () => {
    expect(ommlToLatex(`<m:oMathPara>${math(run("x"))}</m:oMathPara>`)).toEqual({ latex: "x", display: true });
    expect(ommlToLatex(math(run("x")))).toEqual({ latex: "x", display: false });
  });

  it("every produced LaTeX renders in KaTeX without errors", () => {
    const samples = [
      `<m:f><m:num>${run("1")}</m:num><m:den><m:rad><m:radPr><m:degHide m:val="1"/></m:radPr><m:deg/><m:e>${run("x")}</m:e></m:rad></m:den></m:f>`,
      `<m:d><m:dPr><m:begChr m:val="{"/><m:endChr m:val=""/></m:dPr><m:e><m:eqArr><m:e>${run("x+y=1")}</m:e><m:e>${run("x−y=3")}</m:e></m:eqArr></m:e></m:d>`,
      `<m:nary><m:naryPr><m:chr m:val="∑"/></m:naryPr><m:sub>${run("i=1")}</m:sub><m:sup>${run("n")}</m:sup><m:e>${run("a")}</m:e></m:nary>`,
      `<m:func><m:fName><m:limLow><m:e>${run("lim", "p")}</m:e><m:lim>${run("x→0")}</m:lim></m:limLow></m:fName><m:e>${run("f(x)")}</m:e></m:func>`,
    ];
    for (const sample of samples) {
      expect(() => katex.renderToString(latex(sample), { throwOnError: true })).not.toThrow();
    }
  });
});

describe("ommlToLatex: outside the subset", () => {
  it.each([
    ["m:sPre", `<m:sPre><m:sub>${run("6")}</m:sub><m:sup/><m:e>${run("C")}</m:e></m:sPre>`],
    ["m:groupChr", `<m:groupChr><m:e>${run("abc")}</m:e></m:groupChr>`],
    ["m:borderBox", `<m:borderBox><m:e>${run("x")}</m:e></m:borderBox>`],
    ["m:phant", `<m:phant><m:e>${run("x")}</m:e></m:phant>`],
  ])("%s is rejected so the whole formula goes to \"Cần gõ lại\"", (element, inner) => {
    expect(() => ommlToLatex(math(inner))).toThrow(UnsupportedOmmlError);
    try {
      ommlToLatex(math(inner));
    } catch (error) {
      expect((error as UnsupportedOmmlError).element).toBe(element);
    }
  });

  it("a supported element nesting an unsupported one rejects the whole formula", () => {
    const nested = `<m:f><m:num>${run("1")}</m:num><m:den><m:sPre><m:sub>${run("2")}</m:sub><m:sup/><m:e>${run("x")}</m:e></m:sPre></m:den></m:f>`;
    expect(() => ommlToLatex(math(nested))).toThrow(UnsupportedOmmlError);
  });

  it("unknown accent and n-ary characters are rejected, truncated XML is malformed", () => {
    expect(() => ommlToLatex(math(`<m:acc><m:accPr><m:chr m:val="☃"/></m:accPr><m:e>${run("x")}</m:e></m:acc>`))).toThrow(UnsupportedOmmlError);
    expect(() => ommlToLatex(math(`<m:nary><m:naryPr><m:chr m:val="☃"/></m:naryPr><m:e>${run("x")}</m:e></m:nary>`))).toThrow(UnsupportedOmmlError);
    expect(() => ommlToLatex("<m:oMath><m:f><m:num>")).toThrow(MalformedOmmlError);
  });
});
