import { describe, expect, it } from "vitest";
import { composeCe, parseCeLatex, splitArrowConditions } from "../ce";

describe("ce helpers", () => {
  it("splits and recomposes conditions of the first arrow without changing the rest", () => {
    const body = "A + B ->[t°, xt][enzyme] C -> D";
    const parts = splitArrowConditions(body);
    expect(parts).toEqual({ source: "A + B -> C -> D", top: "t°, xt", bottom: "enzyme" });
    expect(composeCe(parts.source, parts.top, parts.bottom)).toBe(body);
  });

  it("does not treat a bracket after a space as a condition", () => {
    expect(splitArrowConditions("Fe -> [Cu(NH3)4]^2+")).toEqual({ source: "Fe -> [Cu(NH3)4]^2+", top: "", bottom: "" });
  });

  it("keeps braces inside a condition and the closing bracket of the group", () => {
    expect(splitArrowConditions("A ->[\\text{a]b}] B")).toEqual({ source: "A -> B", top: "\\text{a]b}", bottom: "" });
  });

  it("recognises every mhchem arrow form", () => {
    for (const arrow of ["->", "<-", "<->", "<-->", "<=>", "<=>>", "<<=>", "⇌", "→"]) {
      expect(splitArrowConditions(`A ${arrow}[x] B`).top).toBe("x");
    }
  });

  it("parses stored \\ce latex and refuses other latex", () => {
    expect(parseCeLatex("\\ce{H2O}")).toEqual({ source: "H2O", top: "", bottom: "" });
    expect(parseCeLatex("x^2")).toBeNull();
    expect(parseCeLatex("\\ce{A ->[x] B}")?.top).toBe("x");
  });

  it("refuses latex that only starts with \\ce{ and ends with }, instead of cutting it wrongly", () => {
    expect(parseCeLatex("\\ce{A}\\text{ note}")).toBeNull();
    expect(parseCeLatex("\\ce{A} + \\ce{B}")).toBeNull();
    expect(parseCeLatex("\\ce{^{14}_{6}C}")).toEqual({ source: "^{14}_{6}C", top: "", bottom: "" });
  });
});
