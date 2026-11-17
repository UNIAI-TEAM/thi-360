import katex from "katex";
import { describe, expect, it } from "vitest";
import en from "@/messages/en.json";
import vi from "@/messages/vi.json";
import { PALETTE_LATEX, PALETTE_TABS, PLACEHOLDER, paletteGlyphLatex } from "../insert/math/math-palette";

function keyPaths(value: unknown, prefix = ""): string[] {
  if (typeof value !== "object" || value === null) return [prefix];
  return Object.entries(value).flatMap(([key, child]) => keyPaths(child, prefix ? `${prefix}.${key}` : key));
}

describe("math palette data", () => {
  it("has the eight tabs of the spec in order, each with items", () => {
    expect(PALETTE_TABS.map((tab) => tab.id)).toEqual([
      "common",
      "fractionRoot",
      "power",
      "limitIntegral",
      "matrix",
      "geometry",
      "sets",
      "greek",
    ]);
    for (const tab of PALETTE_TABS) expect(tab.items.length, tab.id).toBeGreaterThan(0);
  });

  it("refers only to known templates, once per tab", () => {
    for (const tab of PALETTE_TABS) {
      expect(new Set(tab.items).size, tab.id).toBe(tab.items.length);
      for (const id of tab.items) expect(PALETTE_LATEX[id], `${tab.id}/${id}`).toBeTruthy();
    }
  });

  it("names every template and tab in both languages with the same keys", () => {
    for (const messages of [vi, en]) {
      const math = messages.richText.insertDialog.math as { palette: Record<string, string>; tabs: Record<string, string> };
      expect(Object.keys(math.palette).sort()).toEqual(Object.keys(PALETTE_LATEX).sort());
      expect(Object.keys(math.tabs).sort()).toEqual(PALETTE_TABS.map((tab) => tab.id).sort());
      for (const label of [...Object.values(math.palette), ...Object.values(math.tabs)]) expect(label.trim()).not.toBe("");
    }
    expect(keyPaths(vi.richText.insertDialog.math).sort()).toEqual(keyPaths(en.richText.insertDialog.math).sort());
  });

  it("renders every template with KaTeX once its placeholders become squares", () => {
    for (const [id, latex] of Object.entries(PALETTE_LATEX)) {
      expect(() => katex.renderToString(paletteGlyphLatex(latex), { throwOnError: true, strict: "ignore" }), id).not.toThrow();
    }
  });

  it("uses the mathlive placeholder in templates that have empty slots", () => {
    expect(PALETTE_LATEX.frac).toBe(`\\frac{${PLACEHOLDER}}{${PLACEHOLDER}}`);
    expect(paletteGlyphLatex(PALETTE_LATEX.frac ?? "")).toBe("\\frac{\\square}{\\square}");
  });
});
