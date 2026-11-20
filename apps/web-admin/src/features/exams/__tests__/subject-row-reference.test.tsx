import { describe, expect, it } from "vitest";
import { convertForPreview } from "../subject-forms/reference-combined";
import type { BlockMatrix } from "../types";

const axes = { grades: ["G10", "G11"], difficulties: ["NB", "TH"] };

describe("V-SAT part-level conversion preview", () => {
  it("preserves marginals and does not mutate the original separate matrix", () => {
    const matrix: BlockMatrix = {
      mode: "SEPARATE",
      unit: "COUNT",
      basis: "QUESTION",
      gradeEnforcement: "REFERENCE",
      difficultyEnforcement: "REFERENCE",
      cells: [],
      byGrade: [{ code: "G10", value: 4 }, { code: "G11", value: 6 }],
      byDifficulty: [{ code: "NB", value: 5 }, { code: "TH", value: 5 }],
    };
    const before = structuredClone(matrix);
    const combined = convertForPreview(matrix, "TO_COMBINED", axes);
    expect(matrix).toEqual(before);
    expect(combined).toMatchObject({ mode: "COMBINED", basis: "QUESTION", gradeEnforcement: "REFERENCE", difficultyEnforcement: "REFERENCE" });
    const restored = convertForPreview(combined, "TO_SEPARATE", axes);
    expect(restored.byGrade).toEqual(matrix.byGrade);
    expect(restored.byDifficulty).toEqual(matrix.byDifficulty);
  });

  it("creates a reference-only combined matrix without mutating a missing source", () => {
    const combined = convertForPreview(null, "TO_COMBINED", axes);
    expect(combined).toMatchObject({ mode: "COMBINED", gradeEnforcement: "REFERENCE", difficultyEnforcement: "REFERENCE" });
    expect(combined.cells.every((cell) => cell.value === 0)).toBe(true);
  });
});
