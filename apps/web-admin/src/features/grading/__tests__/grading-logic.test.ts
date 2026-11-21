import { describe, expect, it } from "vitest";
import {
  applyGradingFiltersPatch,
  DEFAULT_GRADING_FILTERS,
  parseGradingFilters,
  serializeGradingFilters,
  toGradingCountsQuery,
  toListGradingQuery,
} from "../components/grading-filters-state";
import {
  criteriaInputTotal,
  findFinalizeIssues,
  gradingDraftSchema,
  isItemClaimable,
  isItemClaimedByOther,
  isItemFinalized,
  isItemOwnedDraft,
  toFinalizeRequest,
  toItemFormValues,
  toSaveDraftRequest,
} from "../grading-form";
import {
  checkScoreInput,
  formatScoreInput,
  isValidCalendarDate,
  parseScore,
  roundScore,
  sumScores,
} from "../scoring";
import { GRADER_1_ID, GRADER_2_ID, makeDetail, makeEssayGrade } from "./grading-fixtures";

describe("scoring helpers", () => {
  it("parses comma and dot decimals up to 2 decimals and rejects invalid values", () => {
    expect(parseScore("1,5")).toBe(1.5);
    expect(parseScore(" 2.25 ")).toBe(2.25);
    expect(parseScore("10")).toBe(10);
    expect(parseScore("0,125")).toBeNaN();
    expect(parseScore("-1")).toBeNaN();
    expect(parseScore("abc")).toBeNaN();
    expect(parseScore("")).toBeNaN();
  });

  it("validates score inputs against [0, max]", () => {
    expect(checkScoreInput("", 5, false)).toBeNull();
    expect(checkScoreInput("   ", 5, true)).toBe("required");
    expect(checkScoreInput("5", 5, true)).toBeNull();
    expect(checkScoreInput("0,5", 5, true)).toBeNull();
    expect(checkScoreInput("5,25", 5, false)).toBe("tooHigh");
    expect(checkScoreInput("-0.5", 5, false)).toBe("invalid");
  });

  it("avoids floating point drift in sums and formats decimal separators", () => {
    expect(roundScore(0.1 + 0.2)).toBe(0.3);
    expect(sumScores([0.1, 0.2, null, undefined])).toBe(0.3);
    expect(formatScoreInput(2.5, ",")).toBe("2,5");
    expect(formatScoreInput(2.5, ".")).toBe("2.5");
    expect(formatScoreInput(null, ",")).toBe("");
    expect(isValidCalendarDate("2026-11-20")).toBe(true);
    expect(isValidCalendarDate("2026-02-30")).toBe(false);
  });
});

describe("ownership & state predicates", () => {
  it("distinguishes waiting, claimed-by-me, claimed-by-other, expired, and finalized", () => {
    const waiting = makeDetail({ state: "WAITING", activeGrade: undefined });
    expect(isItemClaimable(waiting)).toBe(true);
    expect(isItemOwnedDraft(waiting, GRADER_1_ID)).toBe(false);
    expect(isItemClaimedByOther(waiting, GRADER_1_ID)).toBe(false);
    expect(isItemFinalized(waiting)).toBe(false);

    const mine = makeDetail({
      state: "IN_PROGRESS",
      activeGrade: makeEssayGrade({ graderId: GRADER_1_ID, claimExpired: false }),
    });
    expect(isItemClaimable(mine)).toBe(false);
    expect(isItemOwnedDraft(mine, GRADER_1_ID)).toBe(true);
    expect(isItemClaimedByOther(mine, GRADER_1_ID)).toBe(false);

    const other = makeDetail({
      state: "IN_PROGRESS",
      activeGrade: makeEssayGrade({ graderId: GRADER_2_ID, claimExpired: false }),
    });
    expect(isItemClaimable(other)).toBe(false);
    expect(isItemOwnedDraft(other, GRADER_1_ID)).toBe(false);
    expect(isItemClaimedByOther(other, GRADER_1_ID)).toBe(true);

    const expired = makeDetail({
      state: "WAITING",
      activeGrade: makeEssayGrade({ graderId: GRADER_2_ID, claimExpired: true }),
    });
    expect(isItemClaimable(expired)).toBe(true);
    expect(isItemClaimedByOther(expired, GRADER_1_ID)).toBe(false);

    const done = makeDetail({
      state: "DONE",
      activeGrade: makeEssayGrade({ state: "FINAL", points: 8 }),
    });
    expect(isItemFinalized(done)).toBe(true);
    expect(isItemClaimable(done)).toBe(false);
  });
});

describe("grading form conversion & validation", () => {
  it("converts activeGrade to form values and back to draft/finalize payloads with expectedVersion", () => {
    const detail = makeDetail({
      state: "IN_PROGRESS",
      activeGrade: makeEssayGrade({
        version: 4,
        points: 7.5,
        criteria: [
          { id: "c1", points: 3.5, comment: "Clear thesis" },
          { id: "c2", points: 4 },
        ],
        comment: "Good work",
      }),
    });

    const values = toItemFormValues(detail, ",");
    expect(values).toEqual({
      criteria: ["3,5", "4"],
      totalPoints: "7,5",
      comment: "Good work",
    });

    expect(toSaveDraftRequest(detail, { criteria: ["3,5", ""], totalPoints: "3,5", comment: "  Note  " })).toEqual({
      expectedVersion: 4,
      points: 3.5,
      criteria: [
        { id: "c1", points: 3.5, comment: "Clear thesis" },
        { id: "c2" },
      ],
      comment: "Note",
    });

    expect(toFinalizeRequest(detail, { criteria: ["3,5", "5"], totalPoints: "8,5", comment: "Final" })).toEqual({
      expectedVersion: 4,
      points: 8.5,
      criteria: [
        { id: "c1", points: 3.5, comment: "Clear thesis" },
        { id: "c2", points: 5 },
      ],
      comment: "Final",
    });
  });

  it("draft schema allows blanks while rejecting out-of-range scores", () => {
    const detail = makeDetail();
    const schema = gradingDraftSchema(detail);
    expect(schema.safeParse({ criteria: ["", ""], totalPoints: "", comment: "" }).success).toBe(true);

    const bad = schema.safeParse({ criteria: ["4,5", "2"], totalPoints: "11", comment: "" });
    expect(bad.success).toBe(false);
    expect(bad.error?.issues.map((i) => [i.path.join("."), i.message])).toEqual([
      ["criteria.0", "tooHigh"],
      ["totalPoints", "tooHigh"],
    ]);
  });

  it("findFinalizeIssues catches missing, out-of-range, and criteria-sum mismatch, and supports rubric-less essays", () => {
    const withRubric = makeDetail({ pointsMax: 10 });
    expect(findFinalizeIssues(withRubric, { criteria: ["", "5"], totalPoints: "5", comment: "" })).toEqual([
      { field: "criteria.0", code: "required", max: 4 },
    ]);

    expect(findFinalizeIssues(withRubric, { criteria: ["3", "5"], totalPoints: "9", comment: "" })).toEqual([
      { field: "totalPoints", code: "criteriaSumMismatch" },
    ]);

    expect(findFinalizeIssues(withRubric, { criteria: ["3,5", "5,5"], totalPoints: "9", comment: "" })).toEqual([]);
    expect(criteriaInputTotal(["3,5", "5,5"])).toBe(9);

    const noRubric = makeDetail({ rubric: [], pointsMax: 10 });
    expect(findFinalizeIssues(noRubric, { criteria: [], totalPoints: "12", comment: "" })).toEqual([
      { field: "totalPoints", code: "tooHigh", max: 10 },
    ]);
    expect(findFinalizeIssues(noRubric, { criteria: [], totalPoints: "8,5", comment: "" })).toEqual([]);
  });
});

describe("filter state helpers", () => {
  it("parses, serializes, and builds list + counts queries (counts omits state)", () => {
    const parsed = parseGradingFilters("?state=IN_PROGRESS&exam=BCA&kind=BCA&from=2026-11-01&to=2026-11-20&page=2&size=50");
    expect(parsed).toEqual({
      state: "IN_PROGRESS",
      exam: "BCA",
      kind: "BCA",
      from: "2026-11-01",
      to: "2026-11-20",
      page: 2,
      size: 50,
    });

    expect(toListGradingQuery(parsed)).toEqual({
      state: "IN_PROGRESS",
      exam: "BCA",
      kind: "BCA",
      from: "2026-11-01",
      to: "2026-11-20",
      page: 1,
      size: 50,
    });
    expect(toGradingCountsQuery(parsed)).toEqual({
      exam: "BCA",
      kind: "BCA",
      from: "2026-11-01",
      to: "2026-11-20",
    });

    const patched = applyGradingFiltersPatch(parsed, { kind: "SPT" });
    expect(patched.page).toBe(1);
    expect(serializeGradingFilters(DEFAULT_GRADING_FILTERS)).toBe("");
  });
});
