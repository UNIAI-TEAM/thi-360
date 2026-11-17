import { describe, expect, it } from "vitest";
import { parseWordHtml } from "../parse";
import {
  acceptAllFixes,
  acceptFix,
  buildFields,
  editFormula,
  fieldOf,
  hasUnresolved,
  pendingLatexes,
  removeFormula,
  retypeFormula,
  splitReview,
  startReview,
  summarize,
  type ReviewFormula,
  type WordReview,
} from "../session";
import { wordFixture } from "./fixtures";

const review = () => startReview(parseWordHtml(wordFixture("synthetic-word365-choice.html")));
const formula = (state: WordReview, id: string) => state.items.find((item) => item.id === id) as ReviewFormula;

// Ba chỗ tự sửa của mẫu: hệ "hoặc" ở phương án A (w3) và D (w6), tên hàm ở "Lời giải" (w8). Ba ảnh công thức: w9–w11.
const SYSTEM = "\\left[\\begin{array}{l} m=0 \\\\ m=\\frac{8}{15} \\end{array}\\right.";

describe("startReview: bản vẽ SoanThaoDanTuWord", () => {
  it("counts converted, auto-fixed pending and image equations like the design's summary bar", () => {
    expect(summarize(review())).toEqual({ converted: 5, pending: 3, retype: 3 });
  });

  it("applies the fixers: the system fix and the function-name fix, keeping the raw conversion for comparison", () => {
    const state = review();
    expect(formula(state, "w3")).toMatchObject({ status: "pending", fixes: ["orSystem"], latex: SYSTEM, raw: "\\left[\\frac{m=0}{m=\\frac{8}{15}}\\right." });
    expect(formula(state, "w8")).toMatchObject({ status: "pending", fixes: ["functionName"], latex: "\\sin\\widehat{AIB}=1", raw: "sin\\widehat{AIB}=1" });
    expect(formula(state, "w4")).toMatchObject({ status: "ok", fixes: [] });
  });
});

describe("Chấp nhận / Sửa tay / Chấp nhận mọi chỗ tự sửa", () => {
  it("Chấp nhận confirms one fix and leaves the others pending", () => {
    const next = acceptFix(review(), "w3");
    expect(formula(next, "w3").status).toBe("accepted");
    expect(formula(next, "w3").latex).toBe(SYSTEM);
    expect(summarize(next)).toEqual({ converted: 6, pending: 2, retype: 3 });
    expect(pendingLatexes(next)).toEqual([SYSTEM.replace("m=0", "m=1"), "\\sin\\widehat{AIB}=1"]);
  });

  it("Chấp nhận does nothing on a formula that needs no fix", () => {
    const state = review();
    expect(acceptFix(state, "w4")).toEqual(state);
  });

  it("Sửa tay replaces the LaTeX, counts as reviewed, and the edited text reaches the output", () => {
    const next = editFormula(review(), "w8", " \\sin \\widehat{AIB} = 1 ");
    expect(formula(next, "w8")).toMatchObject({ status: "edited", latex: "\\sin \\widehat{AIB} = 1" });
    expect(summarize(next).pending).toBe(2);
    expect(buildFields(next, true).solution).toContain("\\(\\sin \\widehat{AIB} = 1\\)");
  });

  it("Chấp nhận mọi chỗ tự sửa clears every pending fix and nothing else", () => {
    const next = acceptAllFixes(review());
    expect(summarize(next)).toEqual({ converted: 8, pending: 0, retype: 3 });
    expect(pendingLatexes(next)).toEqual([]);
    expect(acceptAllFixes(next)).toEqual(next);
  });
});

describe("công thức cần gõ lại", () => {
  it("blocks the fill button until every one is retyped or removed", () => {
    let state = review();
    expect(hasUnresolved(state)).toBe(true);
    state = retypeFormula(state, "w9", "O");
    state = removeFormula(state, "w10");
    expect(hasUnresolved(state)).toBe(true);
    expect(summarize(state).retype).toBe(1);
    state = retypeFormula(state, "w11", "R=1");
    expect(hasUnresolved(state)).toBe(false);
    expect(summarize(state).retype).toBe(0);
  });

  it("puts a retyped formula at the same spot and drops a removed one", () => {
    let state = review();
    state = retypeFormula(state, "w9", "(C)");
    state = removeFormula(state, "w10");
    state = retypeFormula(state, "w11", "R");
    const { solution } = buildFields(state, true);
    expect(solution).toContain('<p>Đường tròn <span class="math-inline">\\((C)\\)</span> có tâm , bán kính <span class="math-inline">\\(R\\)</span></p>');
  });
});

describe("buildFields / splitReview", () => {
  it("fills question, options and solution with the fixed formulas in the editor's HTML form", () => {
    let state = acceptAllFixes(review());
    for (const id of ["w9", "w10", "w11"]) state = removeFormula(state, id);
    const fields = buildFields(state, true);
    expect(fields.matched).toBe(true);
    expect(fields.options.map((option) => option.label)).toEqual(["A", "B", "C", "D"]);
    expect(fields.options[0]!.html).toBe(`<p><span class="math-inline">\\(${SYSTEM}\\)</span></p>`);
    expect(fields.options[1]!.html).toBe('<p><span class="math-inline">\\(m=0\\)</span></p>');
    expect(fields.solution).toContain('<p>Dấu “=” xảy ra: <span class="math-inline">\\(\\sin\\widehat{AIB}=1\\)</span></p>');
    expect(fields.stem.startsWith("<p><strong>Câu 1.</strong> Trong mặt phẳng")).toBe(true);
  });

  it("puts everything in the pasted field when splitting is off", () => {
    const fields = buildFields(review(), false);
    expect(fields.options).toEqual([]);
    expect(fields.solution).toBe("");
    expect(fields.matched).toBe(false);
    expect(fields.stem).toContain("<p><strong>A.</strong>");
    expect(fields.stem).toContain("<p><strong>Lời giải</strong></p>");
  });

  it("says where each formula ended up, for the review list", () => {
    const state = review();
    const split = splitReview(state, true);
    expect(fieldOf(split, "w1")).toEqual({ field: "stem" });
    expect(fieldOf(split, "w3")).toEqual({ field: "option", label: "A" });
    expect(fieldOf(split, "w6")).toEqual({ field: "option", label: "D" });
    expect(fieldOf(split, "w8")).toEqual({ field: "solution" });
    expect(fieldOf(split, "w10")).toEqual({ field: "solution" });
  });
});
