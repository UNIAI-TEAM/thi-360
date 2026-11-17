import { readFileSync } from "node:fs";
import { join } from "node:path";
import { describe, expect, it } from "vitest";
import { cellSegments, crossRows, crossToHtml, emptyCrossModel, htmlToCross } from "../cross-model";

const fixture = JSON.parse(
  readFileSync(join(process.cwd(), "../../docs/specs/fixtures/rich-text-samples.json"), "utf8"),
) as { samples: { name: string; html: string }[] };
const crossSample = fixture.samples.find((sample) => sample.name === "cross-diagram")!.html;
// innerHTML của figure.cross: bỏ thẻ ngoài của mẫu.
const crossInner = crossSample.replace('<figure class="cross">', "").replace("</figure>", "");

describe("cross diagram model", () => {
  it("reads the cross-diagram fixture and writes it back byte for byte", () => {
    const model = htmlToCross(crossInner);
    expect(model).toMatchObject({
      sexA: "male",
      genotypeA: "AaBb",
      sexB: "female",
      genotypeB: "aabb",
      gametesA: "AB, Ab, aB, ab",
      gametesB: "ab",
      filial: [{ genotype: "1 AaBb : 1 Aabb : 1 aaBb : 1 aabb", phenotype: "" }],
    });
    expect(crossToHtml(model)).toBe(crossInner);
  });

  it("turns _ and ^ into sub/sup and escapes markup", () => {
    expect(cellSegments("F_1 X^A_{ab} a<b & _")).toEqual([
      { kind: "text", value: "F" },
      { kind: "sub", value: "1" },
      { kind: "text", value: " X" },
      { kind: "sup", value: "A" },
      { kind: "sub", value: "ab" },
      { kind: "text", value: " a<b & _" },
    ]);
    const model = { ...emptyCrossModel(), genotypeA: "X^AX^a", gametesA: "a<b & c" };
    expect(crossToHtml(model)).toContain("<td>♂ X<sup>A</sup>X<sup>a</sup></td>");
    expect(crossToHtml(model)).toContain("<td>a&lt;b &amp; c</td>");
  });

  it("round-trips scripts, using braces where a short form would read back differently", () => {
    const model = { ...emptyCrossModel(), sexA: "none" as const, genotypeA: "X^AX^a", gametesA: "A_1 2 ; B_{12}3" };
    model.filial = [{ genotype: "F_{1}2", phenotype: "3 high : 1 low" }];
    const html = crossToHtml(model);
    expect(html).toContain("<sub>1</sub>");
    const back = htmlToCross(html);
    expect(crossToHtml(back)).toBe(html);
    expect(back.filial[0]).toEqual(model.filial[0]);
  });

  it("round-trips sub/sup text that contains braces or backslashes", () => {
    for (const cell of ["a<sup>x}y</sup>b", "a<sub>{k\\</sub>"]) {
      const wrapped = `<table><tbody><tr><th scope="row">P</th><td>${cell}</td></tr></tbody></table>`;
      expect(crossToHtml(htmlToCross(wrapped))).toBe(wrapped);
    }
    expect(cellSegments("X^{a\\}b}c")).toEqual([
      { kind: "text", value: "X" },
      { kind: "sup", value: "a}b" },
      { kind: "text", value: "c" },
    ]);
  });

  it("writes a phenotype ratio into the same generation row and reads it back", () => {
    const model = emptyCrossModel();
    model.genotypeA = "Aa";
    model.filial = [{ genotype: "1 AA : 2 Aa : 1 aa", phenotype: "3 tall : 1 short" }];
    const html = crossToHtml(model);
    expect(html).toContain("<td>1 AA : 2 Aa : 1 aa ; 3 tall : 1 short</td>");
    expect(htmlToCross(html).filial[0]).toEqual(model.filial[0]);
  });

  it("keeps generation numbers, trims trailing empty generations, skips empty P and G", () => {
    const model = emptyCrossModel();
    model.filial = [
      { genotype: "", phenotype: "" },
      { genotype: "x", phenotype: "" },
      { genotype: "", phenotype: "" },
    ];
    expect(crossRows(model).map((row) => row.label)).toEqual([{ filial: 1 }, { filial: 2 }]);
    expect(crossToHtml(emptyCrossModel())).toBe("");
  });

  it("reads rows in any order, ignores unknown labels and keeps only the text of unknown inline tags", () => {
    const html =
      '<table><tbody><tr><th scope="row">F<sub>2</sub></th><td>b</td></tr><tr><th scope="row">?</th><td>skip</td></tr><tr><th scope="row">P</th><td><strong>♀ Aa</strong></td></tr></tbody></table>';
    const model = htmlToCross(html);
    expect(model.filial).toEqual([
      { genotype: "", phenotype: "" },
      { genotype: "b", phenotype: "" },
    ]);
    expect(model).toMatchObject({ sexA: "female", genotypeA: "Aa", sexB: "none", genotypeB: "" });
  });
});
