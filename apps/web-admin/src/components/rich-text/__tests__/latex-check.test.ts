import { describe, expect, it } from "vitest";
import { checkLatex, describeKatexError } from "../insert/math/latex-check";

describe("checkLatex", () => {
  it("treats blank input as empty", () => {
    expect(checkLatex("", false)).toEqual({ status: "empty" });
    expect(checkLatex("  \n ", true)).toEqual({ status: "empty" });
  });

  it("accepts renderable formulas in both display modes", () => {
    expect(checkLatex("\\lim_{x \\to 1} \\frac{2x - \\sqrt{3x + 1}}{x^2 - 1}", false)).toEqual({ status: "valid" });
    expect(checkLatex("\\begin{cases} x+y=1 \\\\ x-y=0 \\end{cases}", true)).toEqual({ status: "valid" });
  });

  it("reports a left-over mathlive placeholder before trying to render", () => {
    expect(checkLatex("\\frac{1}{\\placeholder{}}", false)).toEqual({ status: "placeholder" });
    expect(checkLatex("x+\\placeholder[a]{}", false)).toEqual({ status: "placeholder" });
  });

  it("explains a missing closing bracket", () => {
    expect(checkLatex("\\frac{1", false)).toEqual({ status: "error", error: { kind: "missingClose" } });
    expect(checkLatex("x^{2", false)).toEqual({ status: "error", error: { kind: "missingClose" } });
  });

  it("names an unsupported command", () => {
    expect(checkLatex("\\foo{x}", false)).toEqual({
      status: "error",
      error: { kind: "unsupportedCommand", command: "\\foo" },
    });
  });

  it("falls back to a generic message for any other KaTeX error", () => {
    const result = checkLatex("x^2^3", false);
    expect(result).toEqual({ status: "error", error: { kind: "generic" } });
    expect(describeKatexError("something else entirely")).toEqual({ kind: "generic" });
  });
});
