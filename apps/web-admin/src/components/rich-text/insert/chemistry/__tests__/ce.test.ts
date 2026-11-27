import { describe, expect, it } from "vitest";
import { arrowGlyph, findArrows, parseCeLatex, setArrowConditions } from "../ce";

const conditions = (body: string) => findArrows(body).map(({ arrow, top, bottom }) => ({ arrow, top, bottom }));

describe("ce helpers: reading arrows", () => {
  it("reads every arrow in order with the conditions typed right after it", () => {
    expect(conditions("A + B ->[t°, xt][enzyme] C <=>[p] D -> E")).toEqual([
      { arrow: "->", top: "t°, xt", bottom: "enzyme" },
      { arrow: "<=>", top: "p", bottom: "" },
      { arrow: "->", top: "", bottom: "" },
    ]);
  });

  it("reads conditions typed inline on the second arrow only", () => {
    expect(conditions("c2h5(oh)3 + o2 -> co2 ->[12, 123][11] x")).toEqual([
      { arrow: "->", top: "", bottom: "" },
      { arrow: "->", top: "12, 123", bottom: "11" },
    ]);
  });

  it("does not treat a bracket after a space as a condition", () => {
    expect(conditions("Fe -> [Cu(NH3)4]^2+")).toEqual([{ arrow: "->", top: "", bottom: "" }]);
  });

  it("keeps braces and nested brackets inside a condition, and an arrow inside a condition is not an arrow", () => {
    expect(conditions("A ->[\\text{a]b}][{[x]}] B ->[A->B] C")).toEqual([
      { arrow: "->", top: "\\text{a]b}", bottom: "{[x]}" },
      { arrow: "->", top: "A->B", bottom: "" },
    ]);
  });

  it("keeps a condition that is being typed with an unclosed brace", () => {
    expect(conditions("A ->[\\text{ab] B")[0]!.top).toBe("\\text{ab");
  });

  it("recognises every mhchem arrow form and shows its glyph", () => {
    for (const arrow of ["->", "<-", "<->", "<-->", "<=>", "<=>>", "<<=>", "⇌", "→"]) {
      expect(conditions(`A ${arrow}[x] B`)).toEqual([{ arrow, top: "x", bottom: "" }]);
    }
    expect(arrowGlyph("->")).toBe("→");
    expect(arrowGlyph("<=>")).toBe("⇌");
    expect(arrowGlyph("<-")).toBe("←");
    expect(arrowGlyph("⟶")).toBe("⟶");
  });

  it("drops the row of an arrow removed from the source and keeps the others with their own conditions", () => {
    expect(conditions("A ->[x] B -> C ->[z] D")).toHaveLength(3);
    expect(conditions("A ->[x] B C ->[z] D")).toEqual([
      { arrow: "->", top: "x", bottom: "" },
      { arrow: "->", top: "z", bottom: "" },
    ]);
  });
});

describe("ce helpers: writing conditions", () => {
  it("writes the conditions of one arrow and leaves every other character unchanged", () => {
    const body = "A -> B ->[old][keep] C";
    expect(setArrowConditions(body, 0, "t°", "")).toBe("A ->[t°] B ->[old][keep] C");
    expect(setArrowConditions(body, 1, "new", "keep")).toBe("A -> B ->[new][keep] C");
    expect(setArrowConditions(body, 1, "", "below")).toBe("A -> B ->[][below] C");
  });

  it("removes the groups when both conditions are empty, and ignores an arrow that does not exist", () => {
    expect(setArrowConditions("A ->[x][y] B", 0, "", "")).toBe("A -> B");
    expect(setArrowConditions("H2O", 0, "t°", "")).toBe("H2O");
    expect(setArrowConditions("A -> B", 1, "t°", "")).toBe("A -> B");
  });

  it("round-trips what it reads, including text that would close the group early", () => {
    const body = "A ->[\\text{a]b}] B <=>[x][y] C";
    const [first, second] = findArrows(body);
    expect(setArrowConditions(setArrowConditions(body, 0, first!.top, first!.bottom), 1, second!.top, second!.bottom)).toBe(body);

    const written = setArrowConditions("A -> B", 0, "a]b", "");
    expect(written).toBe("A ->[{a]b}] B");
    expect(findArrows(written)[0]!.top).toBe("{a]b}");
  });
});

describe("ce helpers: stored latex", () => {
  it("reads stored \\ce latex back to the quick-typing text with every arrow's conditions", () => {
    expect(parseCeLatex("\\ce{H2O}")).toBe("H2O");
    expect(parseCeLatex("\\ce{A ->[x][y] B <=>[p] C}")).toBe("A ->[x][y] B <=>[p] C");
    expect(parseCeLatex("\\ce A -> B")).toBe("A -> B");
    expect(parseCeLatex("x^2")).toBeNull();
  });

  it("refuses latex that only starts with \\ce{ and ends with }, instead of cutting it wrongly", () => {
    expect(parseCeLatex("\\ce{A}\\text{ note}")).toBeNull();
    expect(parseCeLatex("\\ce{A} + \\ce{B}")).toBeNull();
    expect(parseCeLatex("\\ce{^{14}_{6}C}")).toBe("^{14}_{6}C");
  });
});
