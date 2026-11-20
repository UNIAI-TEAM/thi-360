import { describe, expect, it } from "vitest";
import { convertForPreview } from "../subject-forms/reference-combined";
import { commandDenominator, referenceQuotaEntries } from "../subject-forms/reference-quotas";
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

  it("preserves difficultyEnforcement REQUIRED on TO_COMBINED and clears empty dimensions on TO_SEPARATE", () => {
    const withDiffRequired: BlockMatrix = {
      mode: "SEPARATE",
      unit: "COUNT",
      basis: "QUESTION",
      gradeEnforcement: "REFERENCE",
      difficultyEnforcement: "REQUIRED",
      cells: [],
      byGrade: [],
      byDifficulty: [{ code: "NB", value: 10 }],
    };
    const combined = convertForPreview(withDiffRequired, "TO_COMBINED", axes);
    expect(combined).toMatchObject({ mode: "COMBINED", gradeEnforcement: "REQUIRED", difficultyEnforcement: "REQUIRED" });

    const emptyCombined: BlockMatrix = {
      mode: "COMBINED",
      unit: "COUNT",
      basis: "QUESTION",
      gradeEnforcement: "REFERENCE",
      difficultyEnforcement: "REFERENCE",
      cells: [
        { gradeCode: "G10", difficultyCode: "NB", value: 0 },
        { gradeCode: "G11", difficultyCode: "TH", value: 0 },
      ],
      byGrade: [],
      byDifficulty: [],
    };
    const separate = convertForPreview(emptyCombined, "TO_SEPARATE", axes);
    expect(separate.byGrade).toEqual([]);
    expect(separate.byDifficulty).toEqual([]);
  });
});

describe("exam quota references", () => {
  it("locks prompt and multi-subject quotas and reports unclassified prompts", () => {
    const entries = referenceQuotaEntries("TOAN", [
      { id: "grade", name: "grade", dimension: "GRADE", basis: "PROMPT", enforcement: "REFERENCE", subjectCodes: ["TOAN"], buckets: [{ codes: ["G10"], count: 67 }] },
      { id: "difficulty", name: "difficulty", dimension: "DIFFICULTY", basis: "PROMPT", enforcement: "REFERENCE", subjectCodes: ["TOAN"], buckets: [{ codes: ["NB"], count: 60 }] },
      { id: "multi", name: "multi", dimension: "DIFFICULTY", basis: "QUESTION", enforcement: "REFERENCE", subjectCodes: ["TOAN", "LY"], buckets: [{ codes: ["NB"], count: 10 }] },
    ], true);
    expect(entries.map((entry) => [entry.quota.id, entry.lock, entry.unclassified])).toEqual([
      ["grade", "promptBasis", 0],
      ["difficulty", "promptBasis", 7],
      ["multi", "multiSubject", 0],
    ]);
    expect(commandDenominator(entries)).toBe(67);
  });

  it("computes PROMPT denominator from count buckets before filtering hidden GRADE (R1-15)", () => {
    const entries = referenceQuotaEntries("ANH", [
      { id: "hidden-grade", name: "grade", dimension: "GRADE", basis: "PROMPT", enforcement: "REFERENCE", subjectCodes: ["ANH"], buckets: [{ codes: ["G12"], count: 67 }] },
      { id: "percent-prompt", name: "pct", dimension: "DIFFICULTY", basis: "PROMPT", enforcement: "REFERENCE", subjectCodes: ["ANH"], buckets: [{ codes: ["NB"], percent: 100 }] },
      { id: "diff", name: "diff", dimension: "DIFFICULTY", basis: "PROMPT", enforcement: "REFERENCE", subjectCodes: ["ANH"], buckets: [{ codes: ["NB"], count: 60 }] },
    ], false);
    expect(entries.map((entry) => [entry.quota.id, entry.unclassified])).toEqual([
      ["percent-prompt", 0],
      ["diff", 7],
    ]);
    expect(commandDenominator(entries)).toBe(67);
  });
});
