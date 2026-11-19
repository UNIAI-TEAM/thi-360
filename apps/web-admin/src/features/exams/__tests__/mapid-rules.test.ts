import { describe, expect, it } from "vitest";
import { mapSection, toApiParts } from "../api-mapping";
import {
  convertLegacySection,
  deletedGroupRules,
  describeRule,
  groupEffectiveRules,
  mapidViewMode,
  normalizeMapidSection,
  groupSize,
  recomputeMapidRules,
  ruleFromForm,
  ruleUnits,
  formFromRule,
  ruleFormIssues,
  ruleNeedsNumber,
  usesMapidMatrix,
} from "../mapid-rules";
import { normalizeHsaParts } from "../hsa-enforcement";
import type { ExamPart, ExamSection, MapidRule, QuestionBlock, TopicQuota } from "../types";

const block = (over: Partial<QuestionBlock> = {}): QuestionBlock => ({
  id: "b1",
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

const section = (over: Partial<ExamSection> = {}): ExamSection => ({
  id: "s1",
  subjectCode: "TOAN",
  questionCount: 50,
  knowledgeScope: null,
  answerQuotas: null,
  matrix: null,
  choiceGroup: null,
  blocks: [block({ id: "b-single", name: "Trắc nghiệm", questionType: "SINGLE_CHOICE", questionCount: 32 }), block(), block({ id: "b-set", name: "Chùm cuối", questionType: "QUESTION_SET", questionCount: 3, setCount: 1, setSize: 3 })],
  ...over,
});

const quota = (over: Partial<TopicQuota> = {}): TopicQuota => ({ id: "tq1", name: "Hàm số", nodeIds: ["n-ham-so"], count: 3, enforcement: "REQUIRED", basis: "QUESTION", ...over });

describe("Môn dùng ma trận MAPID", () => {
  it("Toán, Lý, Hóa, Sinh, Sử, Địa có chế độ MAPID; Văn, Anh và mã khác không", () => {
    for (const code of ["TOAN", "LY", "HOA", "SINH", "SU", "DIA"]) expect(usesMapidMatrix(code)).toBe(true);
    for (const code of ["VAN", "ANH", "INFORMATICS", "toString", ""]) expect(usesMapidMatrix(code)).toBe(false);
  });

  it("Văn giữ kiểu cũ: dữ liệu cũ không chuyển, không có chế độ", () => {
    const van = section({ subjectCode: "VAN", knowledgeScope: { nodeIds: ["n1"] }, topicQuotas: [quota()] });
    expect(convertLegacySection(van)).toBe(van);
    expect(mapidViewMode(van)).toBeNull();
  });
});

describe("Chuyển dữ liệu cũ sang quy tắc (AC-10)", () => {
  it("chỉ tiêu môn, phạm vi nhóm, chỉ tiêu nhóm thành quy tắc, xóa trường cũ, chế độ MATRIX; lọc năng lực của môn giữ", () => {
    const legacy = section({
      knowledgeScope: { nodeIds: [], competencyCodes: ["C1"] },
      topicQuotas: [quota(), quota({ id: "tq2", name: "Xác suất", nodeIds: ["n-xs"], count: 0, percent: 10 })],
      blocks: [
        block({ id: "b-short", knowledgeScope: { nodeIds: ["n-c"] }, topicQuotas: [quota({ id: "bq1", nodeIds: ["n-d"], count: 2 })] }),
        block({ id: "b-set", questionType: "SINGLE_CHOICE", questionCount: 3 }),
      ],
    });
    const next = convertLegacySection(legacy);
    expect(next.mapidMode).toBe("MATRIX");
    expect(next.mapidRules).toEqual([
      { id: "legacy-tq1", type: "COUNT", groupId: null, nodeIds: ["n-ham-so"], comparator: "AT_LEAST", count: 3 },
      { id: "legacy-tq2", type: "COUNT", groupId: null, nodeIds: ["n-xs"], comparator: "AT_LEAST", count: 0, percent: 10 },
      { id: "legacy-source-b-short", type: "SOURCE", groupId: "b-short", nodeIds: ["n-c"] },
      { id: "legacy-bq1", type: "COUNT", groupId: "b-short", nodeIds: ["n-d"], comparator: "AT_LEAST", count: 2 },
    ]);
    expect(next.topicQuotas).toBeUndefined();
    expect(next.knowledgeScope).toEqual({ nodeIds: [], competencyCodes: ["C1"] });
    expect(next.blocks[0]!.topicQuotas).toBeUndefined();
    expect(next.blocks[0]!.knowledgeScope).toBeNull();
    expect(next.blocks.map((item) => item.id)).toEqual(["b-short", "b-set"]);
  });

  it("phạm vi MAPID của môn (nhóm không có phạm vi riêng) thành quy tắc SOURCE cả môn", () => {
    const legacy = section({
      knowledgeScope: { nodeIds: ["n-a", "n-b"], everyChildInScope: true, competencyCodes: ["C1"] },
      topicQuotas: [quota()],
      blocks: [block({ id: "b-single", questionType: "SINGLE_CHOICE", questionCount: 50 })],
    });
    const next = convertLegacySection(legacy);
    expect(next.mapidRules).toEqual([
      { id: "legacy-source", type: "SOURCE", groupId: null, nodeIds: ["n-a", "n-b"] },
      { id: "legacy-tq1", type: "COUNT", groupId: null, nodeIds: ["n-ham-so"], comparator: "AT_LEAST", count: 3 },
    ]);
    expect(next.knowledgeScope).toEqual({ nodeIds: [], competencyCodes: ["C1"] });
  });

  it("MAPID ở cả môn và phạm vi riêng của một nhóm: bản cũ chỉ theo phạm vi nhóm, SOURCE mới đòi cả hai (AND) nên giữ kiểu cũ (R1-2)", () => {
    const legacy = section({
      knowledgeScope: { nodeIds: ["n-a"] },
      blocks: [block({ id: "b-short", knowledgeScope: { nodeIds: ["n-c"] } })],
    });
    expect(convertLegacySection(legacy)).toBe(legacy);
    expect(mapidViewMode(legacy)).toBeNull();
    const competencyOnly = section({ knowledgeScope: { nodeIds: ["n-a"] }, blocks: [block({ id: "b-short", questionType: "SINGLE_CHOICE", knowledgeScope: { nodeIds: [], competencyCodes: ["C1"] } })] });
    expect(convertLegacySection(competencyOnly).mapidRules).toEqual([{ id: "legacy-source", type: "SOURCE", groupId: null, nodeIds: ["n-a"] }]);
  });

  it("không mất gì: mỗi MAPID, số câu, tỷ lệ của dòng cũ có mặt trong quy tắc mới", () => {
    const legacy = section({ topicQuotas: [quota({ percent: 6 }), quota({ id: "tq2", nodeIds: ["n1", "n2"], count: 5 })] });
    const rules = convertLegacySection(legacy).mapidRules!;
    expect(rules.map((rule) => [rule.nodeIds, rule.count, rule.percent])).toEqual([
      [["n-ham-so"], 3, 6],
      [["n1", "n2"], 5, undefined],
    ]);
  });

  it("chuyển rồi chuyển lại không đổi gì (môn đã có chế độ), và trả đúng tham chiếu khi không có dữ liệu cũ", () => {
    const converted = convertLegacySection(section({ topicQuotas: [quota()] }));
    expect(convertLegacySection(converted)).toBe(converted);
    const plain = section();
    expect(convertLegacySection(plain)).toBe(plain);
    expect(mapidViewMode(plain)).toBe("ALL");
  });

  it("chỉ tiêu cũ chuyển không trọn vẹn thì giữ nguyên môn kiểu cũ: lọc khối lớp, độ khó, chưa gắn MAPID, Tham khảo, lệnh hỏi, giới hạn nhóm", () => {
    const keep = [
      quota({ gradeCodes: ["G11"] }),
      quota({ difficultyCodes: ["VD"] }),
      quota({ nodeIds: [] }),
      quota({ enforcement: "REFERENCE" }),
      quota({ basis: "PROMPT" }),
      quota({ groupIds: ["b1"] }),
    ];
    for (const item of keep) {
      const legacy = section({ topicQuotas: [quota({ id: "ok" }), item] });
      expect(convertLegacySection(legacy)).toBe(legacy);
      expect(mapidViewMode(legacy)).toBeNull();
    }
  });

  it("quá 20 quy tắc thì giữ nguyên kiểu cũ", () => {
    const many = Array.from({ length: 8 }, (_, index) => quota({ id: `m${index}` }));
    const legacy = section({ knowledgeScope: { nodeIds: ["n"] }, topicQuotas: many, blocks: [block({ id: "b1", topicQuotas: many }), block({ id: "b2", topicQuotas: many })] });
    expect(convertLegacySection(legacy)).toBe(legacy);
  });

  it("quy tắc đọc từ máy chủ và ghi lại giữ đủ trường (hai chiều)", () => {
    const rules: MapidRule[] = [
      { id: "r1", type: "COUNT", groupId: "b1", nodeIds: ["n1"], comparator: "EXACT", count: 3, percent: 20 },
      { id: "r2", type: "SOURCE", groupId: null, nodeIds: ["n2", "n3"] },
    ];
    const read = mapSection({ id: "s1", subjectCode: "TOAN", blocks: [], mapidMode: "MATRIX", mapidRules: rules } as never);
    expect(read.mapidMode).toBe("MATRIX");
    expect(read.mapidRules).toEqual(rules);
    const [part] = toApiParts([{ id: "p", sections: [read], options: [], mode: "FIXED" } as unknown as ExamPart]);
    expect((part as unknown as { sections: ExamSection[] }).sections[0]).toMatchObject({ mapidMode: "MATRIX", mapidRules: [{ id: "r1", groupId: "b1" }, { id: "r2", nodeIds: ["n2", "n3"] }] });
    expect(JSON.stringify(part)).not.toContain("null");
  });

  it("bài HSA chuyển khi nạp (normalizeHsaParts), bài khác loại không đụng tới vì chỉ HSA gọi hàm này", () => {
    const part = { id: "p", sections: [section({ topicQuotas: [quota()] })], options: [], mode: "FIXED" } as unknown as ExamPart;
    const [next] = normalizeHsaParts([part]);
    expect(next!.sections[0]!.mapidMode).toBe("MATRIX");
    expect(next!.sections[0]!.topicQuotas).toBeUndefined();
    expect(normalizeHsaParts([next!])[0]).toBe(next);
  });
});

describe("Chuyển dữ liệu cũ không đổi nguồn của chùm (R1-3)", () => {
  const setBlock = (over: Partial<QuestionBlock> = {}) => block({ id: "b-set", name: "Chùm cuối", questionType: "QUESTION_SET", questionCount: 3, setCount: 1, setSize: 3, ...over });
  const withSet = (over: Partial<ExamSection>, setOver: Partial<QuestionBlock> = {}) => section({ blocks: [block({ id: "b-single", questionType: "SINGLE_CHOICE", questionCount: 47 }), setBlock(setOver)], ...over });

  it("phạm vi riêng của chùm có hai MAPID {A,B}: máy chủ đòi một nhánh chung nên giữ kiểu cũ (chùm trộn A và B vẫn hợp lệ ở bản cũ)", () => {
    const legacy = withSet({}, { knowledgeScope: { nodeIds: ["n-a", "n-b"], everyChildInScope: true } });
    expect(convertLegacySection(legacy)).toBe(legacy);
    expect(mapidViewMode(legacy)).toBeNull();
  });

  it("phạm vi của chùm không đòi mọi câu con (everyChildInScope false hoặc thiếu), dù một MAPID: giữ kiểu cũ", () => {
    for (const everyChildInScope of [false, undefined]) {
      const legacy = withSet({}, { knowledgeScope: { nodeIds: ["n-a"], ...(everyChildInScope === undefined ? {} : { everyChildInScope }) } });
      expect(convertLegacySection(legacy)).toBe(legacy);
    }
  });

  it("chùm thừa kế phạm vi của môn: cùng điều kiện, hai MAPID hoặc không đòi mọi câu con thì giữ kiểu cũ", () => {
    const many = withSet({ knowledgeScope: { nodeIds: ["n-a", "n-b"], everyChildInScope: true } });
    expect(convertLegacySection(many)).toBe(many);
    const loose = withSet({ knowledgeScope: { nodeIds: ["n-a"], everyChildInScope: false } });
    expect(convertLegacySection(loose)).toBe(loose);
  });

  it("một MAPID và mọi câu con đúng phạm vi (không đổi điều kiện): chuyển thành SOURCE của chùm", () => {
    const legacy = withSet({}, { knowledgeScope: { nodeIds: ["n-a"], everyChildInScope: true } });
    const next = convertLegacySection(legacy);
    expect(next.mapidRules).toEqual([{ id: "legacy-source-b-set", type: "SOURCE", groupId: "b-set", nodeIds: ["n-a"] }]);
  });

  it("nhóm câu đơn nhiều MAPID, không đòi mọi câu con: vẫn chuyển (hợp của các MAPID, không đổi điều kiện)", () => {
    const legacy = section({ blocks: [block({ id: "b-single", questionType: "SINGLE_CHOICE", questionCount: 50, knowledgeScope: { nodeIds: ["n-a", "n-b"] } })] });
    expect(convertLegacySection(legacy).mapidRules).toEqual([{ id: "legacy-source-b-single", type: "SOURCE", groupId: "b-single", nodeIds: ["n-a", "n-b"] }]);
  });

  it("chỉ tiêu chủ đề cũ vẫn chuyển khi chùm không có phạm vi MAPID", () => {
    const legacy = withSet({ topicQuotas: [quota()] });
    expect(convertLegacySection(legacy).mapidRules).toEqual([expect.objectContaining({ id: "legacy-tq1", type: "COUNT" })]);
  });
});

describe("Mẫu số của nhóm lấy phần còn lại (R1-5)", () => {
  const remainderBlock = block({ id: "b-rem", name: "Phần còn lại", questionType: "SINGLE_CHOICE", questionCount: null });
  const ten = (over: Partial<ExamSection> = {}) =>
    section({ questionCount: 10, blocks: [block({ id: "b-fixed", name: "Cố định", questionType: "SINGLE_CHOICE", questionCount: 4 }), remainderBlock], ...over });

  it("mẫu số = số câu của môn trừ số câu của các nhóm khác (cùng cách máy chủ tính): 10 - 4 = 6", () => {
    expect(ruleUnits(ten(), { groupId: "b-rem" })).toBe(6);
    expect(groupSize(ten(), remainderBlock)).toMatchObject({ units: 6, total: 10, remainder: true });
  });

  it("chùm của nhóm khác tính theo số chùm × số câu con", () => {
    const s = ten({ blocks: [block({ id: "b-set", questionType: "QUESTION_SET", questionCount: 3, setCount: 1, setSize: 3 }), block({ id: "b-fixed", questionCount: 1 }), remainderBlock] });
    expect(ruleUnits(s, { groupId: "b-rem" })).toBe(6);
  });

  it("một nhóm lấy phần còn lại khác không bị trừ đi (máy chủ không trừ nhóm cùng loại)", () => {
    const s = ten({ blocks: [block({ id: "b-fixed", questionType: "SINGLE_CHOICE", questionCount: 4 }), remainderBlock, { ...remainderBlock, id: "b-rem2" }] });
    expect(ruleUnits(s, { groupId: "b-rem" })).toBe(6);
    expect(ruleUnits(s, { groupId: "b-rem2" })).toBe(6);
  });

  it("chưa biết thì null: môn chưa có số câu, hoặc nhóm khác chưa biết số câu (chùm chưa khai cỡ), hoặc các nhóm khác vượt số câu của môn", () => {
    expect(ruleUnits(ten({ questionCount: null }), { groupId: "b-rem" })).toBeNull();
    const unsized = ten({ blocks: [block({ id: "b-set", questionType: "QUESTION_SET", questionCount: null, setCount: null, setSize: null }), remainderBlock] });
    expect(ruleUnits(unsized, { groupId: "b-rem" })).toBeNull();
    expect(ruleUnits(ten({ questionCount: 3 }), { groupId: "b-rem" })).toBeNull();
  });

  it("nhóm khai số câu, cả môn: mẫu số không đổi", () => {
    expect(ruleUnits(ten(), { groupId: "b-fixed" })).toBe(4);
    expect(ruleUnits(ten(), { groupId: null })).toBe(10);
    expect(groupSize(ten(), ten().blocks[0]!)).toMatchObject({ units: 4, remainder: false });
  });

  it("Đúng 50% của nhóm còn lại: ghi 3 câu và percent 50; câu mô tả 3/6 (50%)", () => {
    const s = ten();
    const form = { ...formFromRule({ id: "r", type: "COUNT", groupId: "b-rem", nodeIds: ["n"], comparator: "EXACT", count: 1 }), entry: "PERCENT" as const, percent: 50 };
    expect(ruleFromForm(form, s)).toEqual({ id: "r", type: "COUNT", groupId: "b-rem", nodeIds: ["n"], comparator: "EXACT", count: 3, percent: 50 });
    expect(describeRule(s, ruleFromForm(form, s)).amount).toBe("3/6 (50%)");
  });

  it("Ít nhất theo tỷ lệ cũng quy đổi theo phần còn lại, và đổi số câu của nhóm khác thì tính lại", () => {
    const rule: MapidRule = { id: "r", type: "COUNT", groupId: "b-rem", nodeIds: ["n"], comparator: "AT_LEAST", count: 3, percent: 50 };
    const s = ten({ mapidMode: "MATRIX", mapidRules: [rule] });
    expect(recomputeMapidRules(s)).toBe(s);
    const moved = { ...s, blocks: s.blocks.map((item) => (item.id === "b-fixed" ? { ...item, questionCount: 6 } : item)) };
    expect(recomputeMapidRules(moved).mapidRules![0]!.count).toBe(2);
  });

  it("tỷ lệ mà chưa biết mẫu số thì chặn lưu có lý do, không ghi số 0 âm thầm; nhập theo số câu hoặc đã biết mẫu số thì không chặn", () => {
    const form = { ...formFromRule({ id: "r", type: "COUNT", groupId: "b-rem", nodeIds: ["n"], comparator: "EXACT", count: 1 }), entry: "PERCENT" as const, percent: 50 };
    const unknown = ten({ questionCount: null });
    expect(ruleFormIssues(form, unknown)).toEqual(["PERCENT_DENOMINATOR_UNKNOWN"]);
    expect(ruleFormIssues({ ...form, entry: "COUNT" }, unknown)).toEqual([]);
    expect(ruleFormIssues(form, ten())).toEqual([]);
    expect(ruleFormIssues({ ...form, groupId: null }, unknown)).toEqual(["PERCENT_DENOMINATOR_UNKNOWN"]);
    expect(ruleFormIssues({ ...form, type: "PRESENT" }, unknown)).toEqual([]);
  });
});

describe("Câu mô tả quy tắc", () => {
  const s = section();
  it("COUNT nhóm: kiểu, phạm vi, 3/15", () => {
    const sentence = describeRule(s, { id: "r", type: "COUNT", groupId: "b1", nodeIds: ["n"], comparator: "EXACT", count: 3 });
    expect(sentence).toMatchObject({ kind: "COUNT_EXACT", scope: "GROUP", amount: "3/15" });
    expect(sentence.group?.id).toBe("b1");
  });
  it("COUNT cả môn theo tỷ lệ: số câu quy đổi theo 50 câu", () => {
    expect(describeRule(s, { id: "r", type: "COUNT", groupId: null, nodeIds: ["n"], comparator: "AT_LEAST", count: 0, percent: 20 })).toMatchObject({ kind: "COUNT_AT_LEAST", scope: "SUBJECT", amount: "10/50 (20%)" });
  });
  it("chùm cuối tính theo câu con: 1 chùm × 3 câu con = 3", () => {
    expect(describeRule(s, { id: "r", type: "COUNT", groupId: "b-set", nodeIds: ["n"], comparator: "AT_LEAST", count: 3 }).amount).toBe("3/3");
  });
  it("PRESENT, SOURCE không có số; ONLY_IN_GROUP có hoặc không kèm số", () => {
    expect(describeRule(s, { id: "r", type: "PRESENT", groupId: null, nodeIds: ["n"] })).toMatchObject({ kind: "PRESENT", amount: null });
    expect(describeRule(s, { id: "r", type: "SOURCE", groupId: "b-set", nodeIds: ["n"] })).toMatchObject({ kind: "SOURCE", amount: null });
    expect(describeRule(s, { id: "r", type: "ONLY_IN_GROUP", groupId: "b1", nodeIds: ["n"] }).kind).toBe("ONLY_IN_GROUP");
    expect(describeRule(s, { id: "r", type: "ONLY_IN_GROUP", groupId: "b1", nodeIds: ["n"], comparator: "EXACT", count: 3 })).toMatchObject({ kind: "ONLY_IN_GROUP_EXACT", amount: "3/15" });
  });
  it("nhóm đã xóa: phạm vi DELETED, không có nhóm", () => {
    expect(describeRule(s, { id: "r", type: "PRESENT", groupId: "gone", nodeIds: ["n"] })).toMatchObject({ scope: "DELETED", group: null });
  });
});

describe("Đổi số câu tính lại tỷ lệ", () => {
  it("quy tắc nhập theo tỷ lệ theo số câu mới của nhóm; quy tắc nhập số câu giữ nguyên", () => {
    const rules: MapidRule[] = [
      { id: "p", type: "COUNT", groupId: "b1", nodeIds: ["n"], comparator: "EXACT", count: 3, percent: 20 },
      { id: "c", type: "COUNT", groupId: "b1", nodeIds: ["n"], comparator: "EXACT", count: 3 },
    ];
    const s = section({ mapidMode: "MATRIX", mapidRules: rules });
    expect(recomputeMapidRules(s)).toBe(s);
    const changed = { ...s, blocks: s.blocks.map((item) => (item.id === "b1" ? { ...item, questionCount: 20 } : item)) };
    expect(recomputeMapidRules(changed).mapidRules).toEqual([{ ...rules[0], count: 4 }, rules[1]]);
    const total = section({ mapidMode: "MATRIX", mapidRules: [{ id: "t", type: "COUNT", groupId: null, nodeIds: ["n"], comparator: "AT_LEAST", count: 10, percent: 20 }], questionCount: 40 });
    expect(normalizeMapidSection(total).mapidRules![0]!.count).toBe(8);
  });
});

describe("Nhóm đã xóa", () => {
  it("quy tắc trỏ tới nhóm không còn bị đánh dấu", () => {
    const s = section({
      mapidMode: "MATRIX",
      mapidRules: [
        { id: "ok", type: "PRESENT", groupId: "b1", nodeIds: ["n"] },
        { id: "bad", type: "PRESENT", groupId: "gone", nodeIds: ["n"] },
        { id: "all", type: "PRESENT", groupId: null, nodeIds: ["n"] },
      ],
    });
    expect(deletedGroupRules(s).map((rule) => rule.id)).toEqual(["bad"]);
  });
});

describe("Toàn bài giữ quy tắc trong bản nháp", () => {
  it("chuyển sang ALL không xóa quy tắc, đọc lại vẫn là ALL với các quy tắc", () => {
    const rules: MapidRule[] = [{ id: "r1", type: "PRESENT", groupId: null, nodeIds: ["n"] }];
    const all = section({ mapidMode: "ALL", mapidRules: rules });
    expect(mapidViewMode(all)).toBe("ALL");
    expect(normalizeMapidSection(all)).toBe(all);
    expect(all.mapidRules).toBe(rules);
  });
});

describe("Hiệu lực của quy tắc đối với nhóm", () => {
  it("nhóm thấy quy tắc của chính nó, SOURCE cả môn và loại trừ do ONLY_IN_GROUP của nhóm khác", () => {
    const s = section({
      mapidMode: "MATRIX",
      mapidRules: [
        { id: "own", type: "COUNT", groupId: "b1", nodeIds: ["n"], comparator: "EXACT", count: 3 },
        { id: "src", type: "SOURCE", groupId: null, nodeIds: ["n"] },
        { id: "only", type: "ONLY_IN_GROUP", groupId: "b-set", nodeIds: ["n"] },
        { id: "present", type: "PRESENT", groupId: null, nodeIds: ["n"] },
      ],
    });
    expect(groupEffectiveRules(s, "b1").map((item) => [item.rule.id, item.role])).toEqual([["own", "OWN"], ["src", "SUBJECT"], ["only", "ELSEWHERE"]]);
    expect(groupEffectiveRules(s, "b-set").map((item) => [item.rule.id, item.role])).toEqual([["src", "SUBJECT"], ["only", "OWN"]]);
  });
});

describe("Biểu mẫu quy tắc", () => {
  const s = section();
  it("ô số chỉ cho COUNT và ONLY_IN_GROUP kèm số", () => {
    expect(ruleNeedsNumber("COUNT", false)).toBe(true);
    expect(ruleNeedsNumber("ONLY_IN_GROUP", false)).toBe(false);
    expect(ruleNeedsNumber("ONLY_IN_GROUP", true)).toBe(true);
    expect(ruleNeedsNumber("PRESENT", true)).toBe(false);
    expect(ruleNeedsNumber("SOURCE", true)).toBe(false);
  });
  it("ghi PRESENT không kèm số; COUNT theo tỷ lệ ghi cả percent và count quy đổi", () => {
    const base = { ...formFromRule({ id: "r", type: "PRESENT", groupId: null, nodeIds: ["n"] }), count: 9, percent: 9, comparator: "EXACT" as const };
    expect(ruleFromForm(base, s)).toEqual({ id: "r", type: "PRESENT", groupId: null, nodeIds: ["n"] });
    expect(ruleFromForm({ ...base, type: "COUNT", entry: "PERCENT", percent: 20, groupId: "b1" }, s)).toEqual({ id: "r", type: "COUNT", groupId: "b1", nodeIds: ["n"], comparator: "EXACT", count: 3, percent: 20 });
    expect(ruleFromForm({ ...base, type: "COUNT", count: 4 }, s)).toEqual({ id: "r", type: "COUNT", groupId: null, nodeIds: ["n"], comparator: "EXACT", count: 4 });
  });
  it("lỗi: chưa chọn MAPID, ONLY_IN_GROUP cả môn, nhóm không còn, tỷ lệ ngoài 0–100", () => {
    const form = formFromRule({ id: "r", type: "ONLY_IN_GROUP", groupId: null, nodeIds: [] });
    expect(ruleFormIssues(form, s)).toEqual(["NO_NODES", "ONLY_IN_GROUP_NEEDS_GROUP"]);
    expect(ruleFormIssues({ ...form, nodeIds: ["n"], groupId: "gone" }, s)).toContain("GROUP_MISSING");
    expect(ruleFormIssues({ ...form, nodeIds: ["n"], groupId: "b1", withNumber: true, entry: "PERCENT", percent: 120 }, s)).toEqual(["PERCENT_RANGE"]);
    expect(ruleFormIssues({ ...form, nodeIds: ["n"], groupId: "b1" }, s)).toEqual([]);
  });
});
