import { describe, expect, it } from "vitest";
import { indexKnowledge } from "@/features/content/knowledge-tree";
import type { KnowledgeNode } from "@/features/content/types";
import { checkMapidRuleConflicts, nodeCovers, nodesCover, nodesEquivalent, nodesOverlap } from "../mapid-rule-form-conflicts";
import { emptyRuleForm, ruleFormIssues } from "../mapid-rules";
import type { ExamSection, MapidRule, QuestionBlock } from "../types";

const kn = (id: string, level: KnowledgeNode["level"], children: KnowledgeNode[] = []): KnowledgeNode => ({
  id,
  code: id.toUpperCase(),
  name: id,
  level,
  subjectCode: "TOAN",
  gradeCode: "GRADE_12",
  active: true,
  children,
});

const TREE: KnowledgeNode[] = [
  kn("sub-toan", "SUBJECT", [
    kn("chap-1", "CHAPTER", [
      kn("les-hamso", "LESSON", [kn("kn-hs-1", "KNOWLEDGE"), kn("kn-hs-2", "KNOWLEDGE")]),
      kn("les-daoham", "LESSON", [kn("kn-dh-1", "KNOWLEDGE")]),
    ]),
    kn("chap-2", "CHAPTER", [kn("les-xacsuat", "LESSON", [kn("kn-xs-1", "KNOWLEDGE")])]),
  ]),
];

const knowledgeIndex = indexKnowledge(TREE);

const block = (over: Partial<QuestionBlock> = {}): QuestionBlock => ({
  id: "b-short",
  name: "Trả lời ngắn",
  questionType: "SHORT_ANSWER",
  questionCount: 15,
  setCount: null,
  setSize: null,
  requiredPointsPerQuestion: null,
  knowledgeScope: null,
  tagCodes: [],
  matrix: null,
  childAnswerType: null,
  templateGroupCode: null,
  answerTypeLocked: false,
  ...over,
});

const makeSection = (rules: MapidRule[] = [], over: Partial<ExamSection> = {}): ExamSection => ({
  id: "s1",
  subjectCode: "TOAN",
  questionCount: 50,
  knowledgeScope: null,
  answerQuotas: null,
  matrix: null,
  choiceGroup: null,
  blocks: [
    block({ id: "b-single", name: "Trắc nghiệm", questionType: "SINGLE_CHOICE", questionCount: 32 }),
    block({ id: "b-short", name: "Trả lời ngắn", questionType: "SHORT_ANSWER", questionCount: 15 }),
    block({ id: "b-set", name: "Chùm cuối", questionType: "QUESTION_SET", questionCount: 3, setCount: 1, setSize: 3 }),
  ],
  mapidMode: "MATRIX",
  mapidRules: rules,
  ...over,
});

describe("knowledge covering helpers", () => {
  it("nodeCovers, nodesCover, nodesEquivalent, nodesOverlap handle ancestor/descendant relationships", () => {
    expect(nodeCovers("les-hamso", "les-hamso", knowledgeIndex)).toBe(true);
    expect(nodeCovers("les-hamso", "kn-hs-1", knowledgeIndex)).toBe(true);
    expect(nodeCovers("chap-1", "kn-hs-2", knowledgeIndex)).toBe(true);
    expect(nodeCovers("kn-hs-1", "les-hamso", knowledgeIndex)).toBe(false);
    expect(nodeCovers("les-hamso", "kn-dh-1", knowledgeIndex)).toBe(false);

    expect(nodesCover(["les-hamso"], ["kn-hs-1", "kn-hs-2"], knowledgeIndex)).toBe(true);
    expect(nodesCover(["les-hamso"], ["kn-hs-1", "kn-dh-1"], knowledgeIndex)).toBe(false);
    expect(nodesEquivalent(["les-hamso", "kn-hs-1"], ["les-hamso"], knowledgeIndex)).toBe(true);
    expect(nodesEquivalent(["les-hamso"], ["kn-hs-1"], knowledgeIndex)).toBe(false);
    expect(nodesOverlap(["les-hamso"], ["kn-hs-2"], knowledgeIndex)).toBe(true);
    expect(nodesOverlap(["kn-hs-1"], ["les-hamso"], knowledgeIndex)).toBe(true);
    expect(nodesOverlap(["kn-hs-1"], ["kn-dh-1"], knowledgeIndex)).toBe(false);
  });
});

describe("mapid-rules form issues updates (AC-1707-4 & AC-1707-6)", () => {
  it("reports TYPE_REQUIRED when form.type is null", () => {
    const form = { ...emptyRuleForm("r1"), type: null, nodeIds: ["kn-hs-1"] };
    expect(ruleFormIssues(form, makeSection())).toContain("TYPE_REQUIRED");
  });

  it("reports COUNT_ABOVE_GROUP when count exceeds target group units (including set group 1x3=3)", () => {
    const section = makeSection();
    expect(
      ruleFormIssues({ ...emptyRuleForm("r1", "b-short"), type: "COUNT", nodeIds: ["kn-hs-1"], count: 16 }, section),
    ).toContain("COUNT_ABOVE_GROUP");
    expect(
      ruleFormIssues({ ...emptyRuleForm("r1", "b-short"), type: "COUNT", nodeIds: ["kn-hs-1"], count: 15 }, section),
    ).not.toContain("COUNT_ABOVE_GROUP");
    expect(
      ruleFormIssues({ ...emptyRuleForm("r1", "b-set"), type: "COUNT", nodeIds: ["kn-hs-1"], count: 4 }, section),
    ).toContain("COUNT_ABOVE_GROUP");
    expect(
      ruleFormIssues(
        { ...emptyRuleForm("r1", "b-set"), type: "ONLY_IN_GROUP", withNumber: true, nodeIds: ["kn-hs-1"], count: 4 },
        section,
      ),
    ).toContain("COUNT_ABOVE_GROUP");
  });
});

describe("checkMapidRuleConflicts — 3-way cross-row check (AC-1707-7)", () => {
  it("CONFLICT: capacity exceeded (group or subject units)", () => {
    const section = makeSection();
    expect(
      checkMapidRuleConflicts({
        candidate: { id: "c1", type: "COUNT", groupId: "b-set", nodeIds: ["kn-hs-1"], comparator: "EXACT", count: 4 },
        section,
        knowledgeIndex,
      }),
    ).toMatchObject({ kind: "CONFLICT", code: "CAPACITY_EXCEEDED", required: 4, available: 3 });

    const zeroGroup = makeSection([], {
      blocks: [block({ id: "b-zero", name: "Rỗng", questionType: "SINGLE_CHOICE", questionCount: 0 })],
    });
    expect(
      checkMapidRuleConflicts({
        candidate: { id: "c2", type: "PRESENT", groupId: "b-zero", nodeIds: ["kn-hs-1"] },
        section: zeroGroup,
        knowledgeIndex,
      }),
    ).toMatchObject({ kind: "CONFLICT", code: "CAPACITY_EXCEEDED", required: 1, available: 0 });
  });

  it("CONFLICT: same scope EXACT vs EXACT / AT_LEAST / PRESENT in BOTH directions and ancestor-aware", () => {
    const cases: Array<[MapidRule, MapidRule, string]> = [
      [
        { id: "r1", type: "COUNT", groupId: null, nodeIds: ["kn-hs-1"], comparator: "EXACT", count: 3 },
        { id: "r2", type: "COUNT", groupId: null, nodeIds: ["kn-hs-1"], comparator: "EXACT", count: 5 },
        "EXACT_COUNTS_DIFFER",
      ],
      [
        { id: "r1", type: "COUNT", groupId: "b-short", nodeIds: ["kn-hs-1"], comparator: "EXACT", count: 2 },
        { id: "r2", type: "COUNT", groupId: "b-short", nodeIds: ["kn-hs-1"], comparator: "AT_LEAST", count: 3 },
        "EXACT_BELOW_MINIMUM",
      ],
      [
        { id: "r1", type: "COUNT", groupId: null, nodeIds: ["kn-hs-1"], comparator: "EXACT", count: 0 },
        { id: "r2", type: "PRESENT", groupId: null, nodeIds: ["kn-hs-1"] },
        "EXACT_BELOW_MINIMUM",
      ],
      [
        { id: "r1", type: "COUNT", groupId: null, nodeIds: ["les-hamso"], comparator: "EXACT", count: 2 },
        { id: "r2", type: "COUNT", groupId: null, nodeIds: ["kn-hs-1"], comparator: "EXACT", count: 3 },
        "EXACT_COUNTS_DIFFER",
      ],
    ];
    for (const [first, second, code] of cases) {
      const forward = checkMapidRuleConflicts({ candidate: second, section: makeSection([first]), knowledgeIndex });
      expect(forward).toMatchObject({ kind: "CONFLICT", code });
      expect(forward.kind === "CONFLICT" && forward.relatedRules.map((r) => r.id)).toContain(first.id);

      const reverse = checkMapidRuleConflicts({ candidate: first, section: makeSection([second]), knowledgeIndex });
      expect(reverse).toMatchObject({ kind: "CONFLICT", code });
      expect(reverse.kind === "CONFLICT" && reverse.relatedRules.map((r) => r.id)).toContain(second.id);
    }

    // Narrower EXACT 2 child + wider EXACT 3 parent is allowed
    expect(
      checkMapidRuleConflicts({
        candidate: { id: "r2", type: "COUNT", groupId: null, nodeIds: ["les-hamso"], comparator: "EXACT", count: 3 },
        section: makeSection([{ id: "r1", type: "COUNT", groupId: null, nodeIds: ["kn-hs-1"], comparator: "EXACT", count: 2 }]),
        knowledgeIndex,
      }),
    ).toEqual({ kind: "OK" });
  });

  it("Subject EXACT 3 + single Group EXACT 2 is ALLOWED; Subject EXACT 2 + single Group AT_LEAST 3 is CONFLICT in both orders", () => {
    const subj3: MapidRule = { id: "s3", type: "COUNT", groupId: null, nodeIds: ["kn-hs-1"], comparator: "EXACT", count: 3 };
    const grp2: MapidRule = { id: "g2", type: "COUNT", groupId: "b-short", nodeIds: ["kn-hs-1"], comparator: "EXACT", count: 2 };
    expect(checkMapidRuleConflicts({ candidate: grp2, section: makeSection([subj3]), knowledgeIndex })).toEqual({ kind: "OK" });
    expect(checkMapidRuleConflicts({ candidate: subj3, section: makeSection([grp2]), knowledgeIndex })).toEqual({ kind: "OK" });

    const subj2: MapidRule = { id: "s2", type: "COUNT", groupId: null, nodeIds: ["kn-hs-1"], comparator: "EXACT", count: 2 };
    const grp3: MapidRule = { id: "g3", type: "COUNT", groupId: "b-short", nodeIds: ["kn-hs-1"], comparator: "AT_LEAST", count: 3 };
    expect(checkMapidRuleConflicts({ candidate: grp3, section: makeSection([subj2]), knowledgeIndex })).toMatchObject({
      kind: "CONFLICT",
      code: "SUBJECT_EXACT_BELOW_GROUP_MINIMUM",
    });
    expect(checkMapidRuleConflicts({ candidate: subj2, section: makeSection([grp3]), knowledgeIndex })).toMatchObject({
      kind: "CONFLICT",
      code: "SUBJECT_EXACT_BELOW_GROUP_MINIMUM",
    });
  });

  it("CONFLICT: Subject EXACT 3 vs sum of 2+ distinct groups >= 4 (order-independent; max per distinct group)", () => {
    const subj3: MapidRule = { id: "s3", type: "COUNT", groupId: null, nodeIds: ["les-hamso"], comparator: "EXACT", count: 3 };
    const grpShort2: MapidRule = { id: "g-short", type: "COUNT", groupId: "b-short", nodeIds: ["kn-hs-1"], comparator: "AT_LEAST", count: 2 };
    const grpSingle2: MapidRule = { id: "g-single", type: "COUNT", groupId: "b-single", nodeIds: ["kn-hs-2"], comparator: "EXACT", count: 2 };

    // Candidate is 2nd group rule
    const res1 = checkMapidRuleConflicts({ candidate: grpSingle2, section: makeSection([subj3, grpShort2]), knowledgeIndex });
    expect(res1).toMatchObject({ kind: "CONFLICT", code: "GROUP_MINIMUMS_ABOVE_SUBJECT_EXACT", required: 4, available: 3 });

    // Candidate is Subject rule added last
    const res2 = checkMapidRuleConflicts({ candidate: subj3, section: makeSection([grpShort2, grpSingle2]), knowledgeIndex });
    expect(res2).toMatchObject({ kind: "CONFLICT", code: "GROUP_MINIMUMS_ABOVE_SUBJECT_EXACT", required: 4, available: 3 });

    // Two rules in the SAME group take max(g.min), not sum: max(2, 1) = 2 <= 3 -> OK
    const sameGroupLower: MapidRule = { id: "g-short-lower", type: "PRESENT", groupId: "b-short", nodeIds: ["kn-hs-2"] };
    expect(
      checkMapidRuleConflicts({ candidate: sameGroupLower, section: makeSection([subj3, grpShort2]), knowledgeIndex }),
    ).toEqual({ kind: "OK" });
  });

  it("CONFLICT: ONLY_IN_GROUP in group A vs PRESENT/COUNT/ONLY_IN_GROUP/SOURCE in group B (order-independent)", () => {
    const onlyShort: MapidRule = { id: "only-s", type: "ONLY_IN_GROUP", groupId: "b-short", nodeIds: ["les-hamso"] };
    const presentSingle: MapidRule = { id: "pres-1", type: "PRESENT", groupId: "b-single", nodeIds: ["kn-hs-1"] };
    const onlySet: MapidRule = { id: "only-set", type: "ONLY_IN_GROUP", groupId: "b-set", nodeIds: ["kn-hs-2"] };
    const sourceSet: MapidRule = { id: "src-set", type: "SOURCE", groupId: "b-set", nodeIds: ["kn-hs-1"] };

    expect(checkMapidRuleConflicts({ candidate: presentSingle, section: makeSection([onlyShort]), knowledgeIndex })).toMatchObject({
      kind: "CONFLICT",
      code: "ONLY_IN_GROUP_EXCLUDES_OTHER_GROUP",
    });
    expect(checkMapidRuleConflicts({ candidate: onlyShort, section: makeSection([presentSingle]), knowledgeIndex })).toMatchObject({
      kind: "CONFLICT",
      code: "ONLY_IN_GROUP_EXCLUDES_OTHER_GROUP",
    });

    // Unnumbered ONLY_IN_GROUP vs unnumbered ONLY_IN_GROUP on overlapping nodes
    expect(checkMapidRuleConflicts({ candidate: onlySet, section: makeSection([onlyShort]), knowledgeIndex })).toMatchObject({
      kind: "CONFLICT",
      code: "ONLY_IN_GROUP_OVERLAPS_OTHER_GROUP",
    });
    expect(checkMapidRuleConflicts({ candidate: onlyShort, section: makeSection([onlySet]), knowledgeIndex })).toMatchObject({
      kind: "CONFLICT",
      code: "ONLY_IN_GROUP_OVERLAPS_OTHER_GROUP",
    });

    // ONLY_IN_GROUP vs SOURCE in non-empty group B
    expect(checkMapidRuleConflicts({ candidate: sourceSet, section: makeSection([onlyShort]), knowledgeIndex })).toMatchObject({
      kind: "CONFLICT",
      code: "ONLY_IN_GROUP_EXCLUDES_SOURCE",
    });
    expect(checkMapidRuleConflicts({ candidate: onlyShort, section: makeSection([sourceSet]), knowledgeIndex })).toMatchObject({
      kind: "CONFLICT",
      code: "ONLY_IN_GROUP_EXCLUDES_SOURCE",
    });

    // EXACT 0 in group B vs ONLY_IN_GROUP in group A is allowed
    expect(
      checkMapidRuleConflicts({
        candidate: { id: "z", type: "COUNT", groupId: "b-single", nodeIds: ["kn-hs-1"], comparator: "EXACT", count: 0 },
        section: makeSection([onlyShort]),
        knowledgeIndex,
      }),
    ).toEqual({ kind: "OK" });
  });

  it("CONFLICT: EXACT 0 vs SOURCE in same non-empty scope or Subject EXACT 0 vs non-empty Group SOURCE", () => {
    const exact0Subj: MapidRule = { id: "z-s", type: "COUNT", groupId: null, nodeIds: ["les-hamso"], comparator: "EXACT", count: 0 };
    const srcShort: MapidRule = { id: "src-s", type: "SOURCE", groupId: "b-short", nodeIds: ["kn-hs-1"] };
    expect(checkMapidRuleConflicts({ candidate: srcShort, section: makeSection([exact0Subj]), knowledgeIndex })).toMatchObject({
      kind: "CONFLICT",
      code: "EXACT_ZERO_CONFLICTS_SOURCE",
    });
    expect(checkMapidRuleConflicts({ candidate: exact0Subj, section: makeSection([srcShort]), knowledgeIndex })).toMatchObject({
      kind: "CONFLICT",
      code: "EXACT_ZERO_CONFLICTS_SOURCE",
    });
  });

  it("EQUIVALENT: PRESENT <-> AT_LEAST 1, EXACT 3 <-> EXACT 20% of 15, SOURCE <-> SOURCE, ONLY_IN_GROUP <-> ONLY_IN_GROUP", () => {
    const pres: MapidRule = { id: "r1", type: "PRESENT", groupId: "b-short", nodeIds: ["kn-hs-1"] };
    const atLeast1: MapidRule = { id: "r2", type: "COUNT", groupId: "b-short", nodeIds: ["kn-hs-1"], comparator: "AT_LEAST", count: 1 };
    expect(checkMapidRuleConflicts({ candidate: atLeast1, section: makeSection([pres]), knowledgeIndex })).toMatchObject({
      kind: "EQUIVALENT",
    });
    expect(checkMapidRuleConflicts({ candidate: pres, section: makeSection([atLeast1]), knowledgeIndex })).toMatchObject({
      kind: "EQUIVALENT",
    });

    const exact3: MapidRule = { id: "e3", type: "COUNT", groupId: "b-short", nodeIds: ["kn-hs-1"], comparator: "EXACT", count: 3 };
    const exact20Pct: MapidRule = { id: "e20", type: "COUNT", groupId: "b-short", nodeIds: ["kn-hs-1"], comparator: "EXACT", percent: 20 };
    expect(checkMapidRuleConflicts({ candidate: exact20Pct, section: makeSection([exact3]), knowledgeIndex })).toMatchObject({
      kind: "EQUIVALENT",
    });

    // Editing the exact same rule id excludes itself -> OK
    expect(checkMapidRuleConflicts({ candidate: exact3, section: makeSection([exact3]), knowledgeIndex })).toEqual({ kind: "OK" });
  });

  it("SUBSUMED_BY and SUBSUMES: existing PRESENT + new EXACT 3 -> SUBSUMES; existing EXACT 3 + new PRESENT -> SUBSUMED_BY", () => {
    const pres: MapidRule = { id: "r-pres", type: "PRESENT", groupId: null, nodeIds: ["kn-hs-1"] };
    const exact3: MapidRule = { id: "r-ex3", type: "COUNT", groupId: null, nodeIds: ["kn-hs-1"], comparator: "EXACT", count: 3 };
    const atLeast2: MapidRule = { id: "r-al2", type: "COUNT", groupId: null, nodeIds: ["kn-hs-1"], comparator: "AT_LEAST", count: 2 };

    const subsumesRes = checkMapidRuleConflicts({
      candidate: exact3,
      section: makeSection([pres, atLeast2]),
      knowledgeIndex,
    });
    expect(subsumesRes).toMatchObject({ kind: "SUBSUMES", reason: "EXACT_COVERS_PRESENT", count: 3 });
    expect(subsumesRes.kind === "SUBSUMES" && subsumesRes.relatedRules.map((r) => r.id)).toEqual(["r-pres", "r-al2"]);

    const subsumedByRes = checkMapidRuleConflicts({
      candidate: pres,
      section: makeSection([exact3]),
      knowledgeIndex,
    });
    expect(subsumedByRes).toMatchObject({ kind: "SUBSUMED_BY" });

    // Ancestor-aware: existing EXACT 3 Child subsumes new PRESENT Parent
    const presParent: MapidRule = { id: "r-pp", type: "PRESENT", groupId: null, nodeIds: ["les-hamso"] };
    expect(checkMapidRuleConflicts({ candidate: presParent, section: makeSection([exact3]), knowledgeIndex })).toMatchObject({
      kind: "SUBSUMED_BY",
    });
  });

  it("priority: CONFLICT > EQUIVALENT > SUBSUMED_BY > SUBSUMES > OK, and skips deleted/omitted group rules", () => {
    const section = makeSection([
      { id: "r-pres", type: "PRESENT", groupId: null, nodeIds: ["kn-hs-1"] },
      { id: "r-ex2", type: "COUNT", groupId: null, nodeIds: ["kn-hs-1"], comparator: "EXACT", count: 2 },
      { id: "r-gone", type: "COUNT", groupId: "gone", nodeIds: ["kn-hs-1"], comparator: "EXACT", count: 1 },
    ]);
    // Candidate EXACT 4 subsumes r-pres, conflicts with r-ex2 -> CONFLICT wins
    expect(
      checkMapidRuleConflicts({
        candidate: { id: "c1", type: "COUNT", groupId: null, nodeIds: ["kn-hs-1"], comparator: "EXACT", count: 4 },
        section,
        knowledgeIndex,
      }),
    ).toMatchObject({ kind: "CONFLICT", code: "EXACT_COUNTS_DIFFER" });

    // Deleted/omitted group rules are ignored
    expect(
      checkMapidRuleConflicts({
        candidate: { id: "c2", type: "COUNT", groupId: null, nodeIds: ["kn-hs-1"], comparator: "EXACT", count: 5 },
        section: makeSection([{ id: "r-gone", type: "COUNT", groupId: "gone", nodeIds: ["kn-hs-1"], comparator: "EXACT", count: 1 }]),
        knowledgeIndex,
      }),
    ).toEqual({ kind: "OK" });
  });
});
