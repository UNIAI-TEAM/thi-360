import katex from "katex";
import { describe, expect, it } from "vitest";
import { autoFix } from "../fixers";

describe("autoFix: tên hàm bị tách chữ", () => {
  it.each([
    ["s i n x", "\\sin x"],
    ["sin\\widehat{AIB}=1", "\\sin\\widehat{AIB}=1"],
    ["cosx+tan x", "\\cos x+\\tan x"],
    ["arcsin x", "\\arcsin x"],
    ["l i m_{x\\to0}f(x)", "\\lim_{x\\to0}f(x)"],
    ["\\frac{s i n a}{c o s a}", "\\frac{\\sin a}{\\cos a}"],
  ])("%s → %s", (raw, fixed) => {
    expect(autoFix(raw)).toEqual({ latex: fixed, kinds: ["functionName"] });
  });

  it("leaves names that are already commands, upright text and environments alone", () => {
    for (const latex of ["\\sin x", "\\mathrm{sin}x", "\\text{cos a}", "\\operatorname{tr}A", "\\begin{matrix}1\\end{matrix}", "x+y", "a b"]) {
      expect(autoFix(latex)).toEqual({ latex, kinds: [] });
    }
  });
});

describe("autoFix: hệ \"hoặc\" bị đổi thành phân số", () => {
  it("turns a bracketed fraction of two relations into a two-row array", () => {
    expect(autoFix("\\left[\\frac{m=0}{m=\\frac{8}{15}}\\right.")).toEqual({
      latex: "\\left[\\begin{array}{l} m=0 \\\\ m=\\frac{8}{15} \\end{array}\\right.",
      kinds: ["orSystem"],
    });
    // Không có `\right.` (ngoặc bị cắt) cũng nhận ra.
    expect(autoFix("\\left[\\frac{x=1}{x\\leq3}").kinds).toEqual(["orSystem"]);
  });

  it("does not touch real fractions or a bracket that is closed", () => {
    for (const latex of ["\\left[\\frac{1}{2}\\right.", "\\left[\\frac{m=0}{m=1}\\right]", "\\frac{m=0}{m=1}", "\\left[\\frac{m=0}{m=1}\\right.+1"]) {
      expect(autoFix(latex).kinds).toEqual([]);
    }
  });

  it("produces LaTeX that KaTeX renders", () => {
    expect(() => katex.renderToString(autoFix("\\left[\\frac{m=0}{m=\\frac{8}{15}}\\right.").latex, { throwOnError: true })).not.toThrow();
  });
});

describe("autoFix: công thức hóa học bị bọc ngoặc", () => {
  it.each([
    ["\\left(H_2SO_4\\right)", "\\ce{H2SO4}"],
    ["\\left[H_{2}O\\right]", "\\ce{H2O}"],
    ["\\left(2H_2+O_2\\rightarrow2H_2O\\right)", "\\ce{2H2+O2->2H2O}"],
    ["\\left(Fe^{3+}\\right)", "\\ce{Fe^3+}"],
    ["\\left(CaCO_3\\left(s\\right)\\right)", "\\ce{CaCO3(s)}"],
  ])("%s → %s", (raw, fixed) => {
    expect(autoFix(raw)).toEqual({ latex: fixed, kinds: ["chemistry"] });
  });

  it("does not mistake planes, circles or bracketed math for chemistry", () => {
    for (const latex of ["\\left(SBC\\right)", "\\left(C\\right)", "\\left(P\\right)", "\\left(x_1\\right)", "\\left(H_2\\right)+x", "\\left(a+b\\right)", "H_2SO_4"]) {
      expect(autoFix(latex).kinds).toEqual([]);
    }
  });
});

describe("autoFix: ký hiệu độ", () => {
  it.each([
    ["A=30°", "A=30^{\\circ}"],
    ["25°C", "25^{\\circ}C"],
    ["\\widehat{AIB}=90º", "\\widehat{AIB}=90^{\\circ}"],
    ["{30}^o", "{30}^{\\circ}"],
    ["45^{o}+x", "45^{\\circ}+x"],
  ])("%s → %s", (raw, fixed) => {
    expect(autoFix(raw)).toEqual({ latex: fixed, kinds: ["degree"] });
  });
});

describe("autoFix: số mũ o không phải độ", () => {
  it("leaves a power of a variable alone", () => {
    for (const latex of ["x^o", "a^0", "2x^o", "10^{ob}"]) expect(autoFix(latex).kinds).toEqual([]);
  });
});

describe("autoFix: kết hợp", () => {
  it("applies several fixers to one formula in a fixed order and is idempotent", () => {
    const first = autoFix("sin 30°");
    expect(first).toEqual({ latex: "\\sin 30^{\\circ}", kinds: ["functionName", "degree"] });
    expect(autoFix(first.latex)).toEqual({ latex: first.latex, kinds: [] });
  });

  it("returns formulas without any of the four defects unchanged", () => {
    const latex = "S_{\\Delta IAB}=\\frac{1}{2}IA\\cdot IB\\cdot\\sin \\widehat{AIB}";
    expect(autoFix(latex)).toEqual({ latex, kinds: [] });
  });
});
