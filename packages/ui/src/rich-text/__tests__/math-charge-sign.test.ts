import { readFileSync } from "node:fs";
import { join } from "node:path";
import katex from "katex";
import "katex/contrib/mhchem";
import { describe, expect, it } from "vitest";
import { markChargeSigns } from "../math-render";

function rendered(latex: string): HTMLElement {
  const host = document.createElement("span");
  katex.render(latex, host, { throwOnError: true });
  markChargeSigns(latex, host);
  return host;
}

const signs = (host: HTMLElement) => [...host.querySelectorAll(".math-charge-sign")].map((node) => node.textContent);

describe("markChargeSigns", () => {
  it("marks only the + / − of ion charges in a \\ce formula, not the digits next to them", () => {
    expect(signs(rendered("\\ce{Fe^3-}"))).toEqual(["\u2212"]);
    expect(signs(rendered("\\ce{SO4^2-}"))).toEqual(["\u2212"]);
    expect(signs(rendered("\\ce{Fe^3+}"))).toEqual(["+"]);
    expect(signs(rendered("\\ce{Na+ + Cl-}"))).toEqual(["+", "\u2212"]);
    expect(signs(rendered("\\ce{2H2 + O2 -> 2H2O}"))).toEqual([]);
  });

  it("leaves plain math untouched", () => {
    expect(signs(rendered("x^{n-1} + y^+"))).toEqual([]);
  });

  it("thickens the marked sign with the text colour only (no opacity, no new colour)", () => {
    const css = readFileSync(join(__dirname, "../math-render.css"), "utf8");
    const rule = /\.katex \.math-charge-sign\s*\{([^}]*)\}/.exec(css)?.[1] ?? "";
    expect(rule).toMatch(/-webkit-text-stroke:\s*[\d.]+em currentColor;/);
    expect(rule).not.toMatch(/opacity|color:/);
  });
});
