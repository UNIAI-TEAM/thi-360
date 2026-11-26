import { describe, expect, it } from "vitest";
import { indexKnowledge, restrictKnowledgeTree } from "@/features/content/knowledge-tree";
import type { KnowledgeNode } from "@/features/content/types";
import {
  groupBudget,
  groupRulesLeftOutside,
  groupSharesUnder,
  mapidRuleWarnings,
  onlyElsewhereNodes,
  subjectBudget,
  subjectSourceNodes,
  withRuleCount,
} from "../mapid-rule-budget";
import { checkMapidRuleConflicts } from "../mapid-rule-form-conflicts";
import type { ExamSection, MapidRule, QuestionBlock } from "../types";

// UNI-2824: quy tắc MAPID "ngân sách lồng nhau" (quyết định 2026-11-26).

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

const block = (id: string, questionCount: number, over: Partial<QuestionBlock> = {}): QuestionBlock => ({
  id,
  name: id,
  questionType: "SINGLE_CHOICE",
  questionCount,
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

const section = (rules: MapidRule[], over: Partial<ExamSection> = {}): ExamSection => ({
  id: "s1",
  subjectCode: "TOAN",
  questionCount: 30,
  knowledgeScope: null,
  answerQuotas: null,
  matrix: null,
  choiceGroup: null,
  blocks: [block("short", 10), block("set", 4), block("single", 16)],
  mapidMode: "MATRIX",
  mapidRules: rules,
  ...over,
});

const count = (id: string, groupId: string | null, nodeIds: string[], comparator: "EXACT" | "AT_LEAST", n: number): MapidRule => ({
  id,
  type: "COUNT",
  groupId,
  nodeIds,
  comparator,
  count: n,
});
const source = (id: string, groupId: string | null, nodeIds: string[]): MapidRule => ({ id, type: "SOURCE", groupId, nodeIds });
const only = (id: string, groupId: string, nodeIds: string[]): MapidRule => ({ id, type: "ONLY_IN_GROUP", groupId, nodeIds });
const present = (id: string, groupId: string | null, nodeIds: string[]): MapidRule => ({ id, type: "PRESENT", groupId, nodeIds });

describe("groupSharesUnder — phần đã chia cho các nhóm", () => {
  it("cộng mức tối thiểu của từng nhóm có quy tắc trên MAPID hoặc MAPID con; trong một nhóm lấy mức lớn nhất", () => {
    const rules = [
      count("short-hs", "short", ["les-hamso"], "AT_LEAST", 2),
      count("short-hs1", "short", ["kn-hs-1"], "AT_LEAST", 1),
      count("set-hs1", "set", ["kn-hs-1"], "AT_LEAST", 2),
      count("single-xs", "single", ["kn-xs-1"], "AT_LEAST", 5),
      count("subject-hs", null, ["les-hamso"], "EXACT", 3),
    ];
    expect(groupSharesUnder(section(rules), rules, ["les-hamso"], knowledgeIndex)).toEqual([
      { groupId: "short", need: 2 },
      { groupId: "set", need: 2 },
    ]);
    expect(groupSharesUnder(section(rules), rules, ["les-hamso"], knowledgeIndex, "set")).toEqual([{ groupId: "short", need: 2 }]);
  });

  it("bỏ nhóm đã bỏ qua (omitted) và quy tắc không có MAPID", () => {
    const rules = [count("set-hs", "set", ["les-hamso"], "AT_LEAST", 2), count("short-empty", "short", [], "AT_LEAST", 4)];
    const sec = section(rules, { blocks: [block("short", 10), block("set", 4, { omitted: true }), block("single", 16)] });
    expect(groupSharesUnder(sec, rules, ["les-hamso"], knowledgeIndex)).toEqual([]);
  });
});

describe("subjectBudget — ô số của quy tắc toàn môn", () => {
  it("bảng ví dụ của quyết định: đúng 3 với hai nhóm ít nhất 2 cần ít nhất 4", () => {
    const rules = [count("short", "short", ["les-hamso"], "AT_LEAST", 2), count("set", "set", ["les-hamso"], "AT_LEAST", 2)];
    const candidate = count("subject", null, ["les-hamso"], "EXACT", 3);
    expect(subjectBudget(section(rules), candidate, knowledgeIndex)).toMatchObject({
      allocated: 4,
      shares: [
        { groupId: "short", need: 2 },
        { groupId: "set", need: 2 },
      ],
      onlyGroup: null,
    });
  });

  it("không tính chính quy tắc đang sửa (kể cả khi trước đó nó nằm ở một nhóm)", () => {
    const rules = [count("r", "short", ["les-hamso"], "AT_LEAST", 2)];
    expect(subjectBudget(section(rules), count("r", null, ["les-hamso"], "EXACT", 1), knowledgeIndex)?.allocated).toBe(0);
  });

  it("MAPID chỉ xuất hiện ở nhóm A: toàn môn không vượt số câu của nhóm A", () => {
    const rules = [only("only-set", "set", ["les-hamso"])];
    expect(subjectBudget(section(rules), count("subject", null, ["kn-hs-1"], "AT_LEAST", 5), knowledgeIndex)?.onlyGroup).toMatchObject({
      groupId: "set",
      units: 4,
    });
    // Quy tắc toàn môn rộng hơn MAPID chỉ-ở-nhóm thì không bị giới hạn.
    expect(subjectBudget(section(rules), count("subject", null, ["chap-1"], "AT_LEAST", 5), knowledgeIndex)?.onlyGroup).toBeNull();
  });

  it("quy tắc nhóm không trả về ngân sách toàn môn", () => {
    expect(subjectBudget(section([]), count("g", "short", ["les-hamso"], "AT_LEAST", 1), knowledgeIndex)).toBeNull();
  });
});

describe("groupBudget — ô số của quy tắc nhóm", () => {
  it("tối đa = toàn môn đúng N trừ phần các nhóm khác; lấy quy tắc toàn môn chặt nhất", () => {
    const rules = [
      count("subject-chap", null, ["chap-1"], "EXACT", 6),
      count("subject-hs", null, ["les-hamso"], "EXACT", 3),
      count("short", "short", ["les-hamso"], "AT_LEAST", 2),
    ];
    const budget = groupBudget(section(rules), count("set", "set", ["kn-hs-1"], "AT_LEAST", 1), knowledgeIndex);
    expect(budget).toMatchObject({ exact: 3, remaining: 1, others: [{ groupId: "short", need: 2 }] });
    expect(budget?.subjectRule.id).toBe("subject-hs");
  });

  it("toàn môn ít nhất N không giới hạn nhóm", () => {
    const rules = [count("subject", null, ["les-hamso"], "AT_LEAST", 3), count("short", "short", ["les-hamso"], "AT_LEAST", 2)];
    expect(groupBudget(section(rules), count("set", "set", ["les-hamso"], "AT_LEAST", 4), knowledgeIndex)).toBeNull();
  });

  it("sửa quy tắc nhóm thì phần của chính nhóm đó không bị trừ hai lần", () => {
    const rules = [count("subject", null, ["les-hamso"], "EXACT", 3), count("short", "short", ["les-hamso"], "AT_LEAST", 2)];
    expect(groupBudget(section(rules), count("short", "short", ["les-hamso"], "AT_LEAST", 3), knowledgeIndex)?.remaining).toBe(3);
  });
});

describe("nguồn toàn môn là biên ngoài; MAPID chỉ-ở-nhóm bị loại khỏi nhóm khác", () => {
  it("subjectSourceNodes gộp mọi quy tắc nguồn toàn môn, trừ quy tắc đang sửa", () => {
    const rules = [source("a", null, ["les-hamso"]), source("b", null, ["les-daoham"]), source("g", "short", ["kn-hs-1"])];
    expect(subjectSourceNodes(rules)).toEqual(["les-hamso", "les-daoham"]);
    expect(subjectSourceNodes(rules, "a")).toEqual(["les-daoham"]);
    expect(subjectSourceNodes([source("g", "short", ["kn-hs-1"])])).toBeNull();
  });

  it("onlyElsewhereNodes trả MAPID chỉ được ở nhóm khác", () => {
    const rules = [only("o-set", "set", ["les-hamso"]), only("o-short", "short", ["kn-xs-1"])];
    expect(onlyElsewhereNodes(section(rules), "short")).toEqual(["les-hamso"]);
    expect(onlyElsewhereNodes(section(rules), "single")).toEqual(["les-hamso", "kn-xs-1"]);
  });

  it("groupRulesLeftOutside liệt kê quy tắc nhóm rơi ra ngoài khi thu hẹp nguồn toàn môn, không tính dòng đã ở ngoài từ trước", () => {
    const rules = [
      source("src", null, ["chap-1"]),
      present("in-hs", "short", ["kn-hs-1"]),
      present("in-dh", "set", ["les-daoham"]),
      present("already-out", "single", ["kn-xs-1"]),
    ];
    const narrowed = source("src", null, ["les-hamso"]);
    expect(groupRulesLeftOutside(section(rules), narrowed, knowledgeIndex).map((rule) => rule.id)).toEqual(["in-dh"]);
    // Thêm nguồn toàn môn lần đầu cũng là thu hẹp (trước đó nhóm chọn được mọi MAPID).
    const first = section([present("in-hs", "short", ["kn-hs-1"]), present("dh", "set", ["les-daoham"])]);
    expect(groupRulesLeftOutside(first, source("new", null, ["les-hamso"]), knowledgeIndex).map((rule) => rule.id)).toEqual(["dh"]);
    // Nới rộng không hỏi gì.
    expect(groupRulesLeftOutside(section(rules), source("src", null, ["sub-toan"]), knowledgeIndex)).toEqual([]);
  });

  it("checkMapidRuleConflicts chặn quy tắc nhóm ngoài nguồn toàn môn; nút con của nguồn vẫn được", () => {
    const sec = section([source("src", null, ["les-hamso"])]);
    expect(checkMapidRuleConflicts({ candidate: present("g", "short", ["kn-dh-1"]), section: sec, knowledgeIndex })).toMatchObject({
      kind: "CONFLICT",
      code: "OUTSIDE_SUBJECT_SOURCE",
      scopeGroupId: "short",
      relatedRules: [{ id: "src" }],
    });
    // Nút cha của nguồn cũng nằm ngoài (rộng hơn biên).
    expect(checkMapidRuleConflicts({ candidate: present("g", "short", ["chap-1"]), section: sec, knowledgeIndex })).toMatchObject({
      code: "OUTSIDE_SUBJECT_SOURCE",
    });
    expect(checkMapidRuleConflicts({ candidate: present("g", "short", ["kn-hs-2"]), section: sec, knowledgeIndex })).toEqual({ kind: "OK" });
    expect(checkMapidRuleConflicts({ candidate: present("s", null, ["kn-dh-1"]), section: sec, knowledgeIndex })).toEqual({ kind: "OK" });
  });

  it("checkMapidRuleConflicts: toàn môn cần nhiều hơn số câu của nhóm chỉ-ở-nhóm, theo cả hai thứ tự", () => {
    const onlySet = only("o", "set", ["les-hamso"]);
    const subject = count("s", null, ["kn-hs-1"], "AT_LEAST", 5);
    const expected = { kind: "CONFLICT", code: "SUBJECT_MINIMUM_ABOVE_ONLY_GROUP", onlyGroupId: "set", required: 5, available: 4 };
    expect(checkMapidRuleConflicts({ candidate: subject, section: section([onlySet]), knowledgeIndex })).toMatchObject(expected);
    expect(checkMapidRuleConflicts({ candidate: onlySet, section: section([subject]), knowledgeIndex })).toMatchObject(expected);
    expect(checkMapidRuleConflicts({ candidate: count("s", null, ["kn-hs-1"], "AT_LEAST", 4), section: section([onlySet]), knowledgeIndex })).not.toMatchObject({
      code: "SUBJECT_MINIMUM_ABOVE_ONLY_GROUP",
    });
  });
});

describe("mapidRuleWarnings — cảnh báo trên dòng và nút sửa nhanh", () => {
  it("toàn môn đúng 3 với hai nhóm ít nhất 2: nâng toàn môn lên 4 hoặc giảm từng nhóm còn 1", () => {
    const rules = [
      count("subject", null, ["les-hamso"], "EXACT", 3),
      count("short", "short", ["les-hamso"], "AT_LEAST", 2),
      count("set", "set", ["kn-hs-1"], "AT_LEAST", 2),
    ];
    const warnings = mapidRuleWarnings(section(rules), knowledgeIndex);
    expect(warnings.get("subject")).toEqual([
      { code: "SUBJECT_BELOW_GROUPS", allocated: 4, shares: [{ groupId: "short", need: 2 }, { groupId: "set", need: 2 }], fix: 4 },
    ]);
    expect(warnings.get("short")).toMatchObject([{ code: "GROUP_ABOVE_SUBJECT", remaining: 1, fix: 1 }]);
    expect(warnings.get("set")).toMatchObject([{ code: "GROUP_ABOVE_SUBJECT", remaining: 1, fix: 1 }]);
  });

  it("kết quả không phụ thuộc thứ tự dòng (điểm 7)", () => {
    const rules = [
      count("subject", null, ["les-hamso"], "EXACT", 3),
      count("short", "short", ["les-hamso"], "AT_LEAST", 2),
      count("set", "set", ["kn-hs-1"], "AT_LEAST", 2),
      source("src", null, ["chap-1"]),
      present("out", "single", ["kn-xs-1"]),
    ];
    const forward = mapidRuleWarnings(section(rules), knowledgeIndex);
    const backward = mapidRuleWarnings(section([...rules].reverse()), knowledgeIndex);
    expect(new Map([...backward].sort())).toEqual(new Map([...forward].sort()));
  });

  it("sửa cấu trúc bớt câu: quy tắc vượt số câu của nhóm hoặc của môn có nút giảm về đúng số câu", () => {
    const rules = [count("short", "short", ["les-hamso"], "AT_LEAST", 8), count("subject", null, ["chap-1"], "AT_LEAST", 25)];
    const sec = section(rules, { questionCount: 20, blocks: [block("short", 5), block("set", 4), block("single", 11)] });
    const warnings = mapidRuleWarnings(sec, knowledgeIndex);
    expect(warnings.get("short")).toEqual([{ code: "ABOVE_UNITS", units: 5, fix: 5 }]);
    expect(warnings.get("subject")).toEqual([{ code: "ABOVE_UNITS", units: 20, fix: 20 }]);
  });

  it("nâng toàn môn không vượt số câu của môn: khi đó không có nút sửa nhanh", () => {
    const rules = [
      count("subject", null, ["les-hamso"], "EXACT", 3),
      count("short", "short", ["les-hamso"], "AT_LEAST", 3),
      count("set", "set", ["les-hamso"], "AT_LEAST", 3),
    ];
    const sec = section(rules, { questionCount: 5, blocks: [block("short", 3), block("set", 3)] });
    expect(mapidRuleWarnings(sec, knowledgeIndex).get("subject")).toMatchObject([{ code: "SUBJECT_BELOW_GROUPS", allocated: 6, fix: null }]);
  });

  it("quy tắc nhóm ngoài nguồn toàn môn và quy tắc toàn môn vượt nhóm chỉ-ở-nhóm", () => {
    const rules = [
      source("src", null, ["les-hamso"]),
      present("out", "short", ["kn-dh-1"]),
      only("o", "set", ["les-hamso"]),
      count("subject", null, ["kn-hs-1"], "AT_LEAST", 6),
    ];
    const warnings = mapidRuleWarnings(section(rules), knowledgeIndex);
    expect(warnings.get("out")).toMatchObject([{ code: "OUTSIDE_SUBJECT_SOURCE", sources: [{ id: "src" }] }]);
    expect(warnings.get("subject")).toEqual([{ code: "ABOVE_ONLY_GROUP", groupId: "set", units: 4, fix: 4 }]);
    expect(warnings.has("src")).toBe(false);
    expect(warnings.has("o")).toBe(false);
  });

  it("nhóm có quy tắc 'phải xuất hiện' khi toàn môn đã chia hết: cảnh báo, không có nút sửa", () => {
    const rules = [count("subject", null, ["les-hamso"], "EXACT", 2), count("short", "short", ["les-hamso"], "AT_LEAST", 2), present("set", "set", ["kn-hs-1"])];
    expect(mapidRuleWarnings(section(rules), knowledgeIndex).get("set")).toMatchObject([{ code: "GROUP_ABOVE_SUBJECT", remaining: 0, fix: null }]);
  });

  it("số khớp thì không cảnh báo", () => {
    const rules = [count("subject", null, ["les-hamso"], "EXACT", 3), count("short", "short", ["les-hamso"], "AT_LEAST", 2)];
    expect(mapidRuleWarnings(section(rules), knowledgeIndex).size).toBe(0);
  });

  it("withRuleCount đổi số câu và bỏ tỷ lệ", () => {
    const rule: MapidRule = { id: "r", type: "COUNT", groupId: null, nodeIds: ["les-hamso"], comparator: "EXACT", count: 3, percent: 10 };
    const fixed = withRuleCount(rule, 4);
    expect(fixed).toEqual({ id: "r", type: "COUNT", groupId: null, nodeIds: ["les-hamso"], comparator: "EXACT", count: 4 });
    expect("percent" in fixed).toBe(false);
  });
});

describe("restrictKnowledgeTree — hộp chọn MAPID của nhóm", () => {
  const ids = (nodes: readonly KnowledgeNode[]): string[] => nodes.flatMap((node) => [node.id, ...ids(node.children)]);

  it("chỉ giữ nhánh trong nguồn; tổ tiên chỉ để dẫn đường", () => {
    const { tree, pathOnly } = restrictKnowledgeTree(TREE, { within: ["les-hamso"] });
    expect(ids(tree)).toEqual(["sub-toan", "chap-1", "les-hamso", "kn-hs-1", "kn-hs-2"]);
    expect([...pathOnly].sort()).toEqual(["chap-1", "sub-toan"]);
  });

  it("bỏ MAPID chỉ-ở-nhóm-khác cùng nhánh con, giữ phần còn lại", () => {
    const { tree, pathOnly } = restrictKnowledgeTree(TREE, { within: ["chap-1"], exclude: ["les-hamso"] });
    expect(ids(tree)).toEqual(["sub-toan", "chap-1", "les-daoham", "kn-dh-1"]);
    expect([...pathOnly]).toEqual(["sub-toan"]);
  });

  it("không giới hạn thì giữ nguyên cây", () => {
    expect(restrictKnowledgeTree(TREE, {}).tree).toEqual(TREE);
    expect(restrictKnowledgeTree(TREE, { within: null, exclude: [] }).pathOnly.size).toBe(0);
  });
});
