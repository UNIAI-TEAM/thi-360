import { describe, expect, it } from "vitest";
import { toApiParts } from "../api-mapping";
import { saveBlockers } from "../allocation";
import { removeBlock } from "../components/exam-structure-draft";
import {
  convertLegacySection,
  describeRule,
  emptyRuleForm,
  groupCountScope,
  groupSize,
  recomputeMapidRules,
  ruleCount,
  ruleFormIssues,
  ruleFromForm,
  ruleUnits,
} from "../mapid-rules";
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
  blocks: [block({ id: "b-single", name: "Trắc nghiệm", questionType: "SINGLE_CHOICE", questionCount: 32 }), block()],
  ...over,
});

const quota = (over: Partial<TopicQuota> = {}): TopicQuota => ({ id: "tq1", name: "Hàm số", nodeIds: ["n-ham-so"], count: 3, enforcement: "REQUIRED", basis: "QUESTION", ...over });

describe("R1-F1: chỉ tiêu theo tỷ lệ không có mẫu số không chuyển được", () => {
  const pool = (over: Partial<ExamSection> = {}) => section({ subjectCode: "LY", questionCount: null, blocks: [], ...over });

  it("môn chọn môn (questionCount rỗng) có chỉ tiêu theo tỷ lệ: giữ kiểu cũ, trả đúng tham chiếu", () => {
    const legacy = pool({ topicQuotas: [quota({ percent: 20, count: 4 })] });
    expect(convertLegacySection(legacy)).toBe(legacy);
  });

  it("nhóm lấy phần còn lại của môn chưa có số câu, chỉ tiêu nhóm theo tỷ lệ: giữ kiểu cũ", () => {
    const legacy = section({
      questionCount: null,
      blocks: [block({ id: "b-rest", questionCount: null, topicQuotas: [quota({ percent: 50, count: 2 })] })],
    });
    expect(convertLegacySection(legacy)).toBe(legacy);
  });

  it("chỉ tiêu theo số câu vẫn chuyển ở môn chưa có số câu; theo tỷ lệ chuyển khi biết mẫu số", () => {
    const byCount = convertLegacySection(pool({ topicQuotas: [quota({ count: 4 })] }));
    expect(byCount.mapidRules).toEqual([{ id: "legacy-tq1", type: "COUNT", groupId: null, nodeIds: ["n-ham-so"], comparator: "AT_LEAST", count: 4 }]);
    const byPercent = convertLegacySection(section({ topicQuotas: [quota({ percent: 20, count: 10 })] }));
    expect(byPercent.mapidRules).toMatchObject([{ id: "legacy-tq1", percent: 20, count: 10 }]);
  });
});

describe("R1-F2: nhóm đã bỏ không bị trừ khi tính phần còn lại, như máy chủ", () => {
  const withOmitted = () =>
    section({
      questionCount: 10,
      blocks: [block({ id: "b-fixed", name: "Cố định", questionCount: 4, omitted: true }), block({ id: "b-rest", name: "Phần còn lại", questionCount: null })],
    });

  it("môn 10 câu, nhóm cố định 4 câu đã bỏ, nhóm còn lại 50% là 5/10", () => {
    const view = withOmitted();
    expect(groupSize(view, view.blocks[1]!).units).toBe(10);
    const rule: MapidRule = { id: "r1", type: "COUNT", groupId: "b-rest", nodeIds: ["n1"], comparator: "AT_LEAST", percent: 50 };
    expect(ruleUnits(view, rule)).toBe(10);
    expect(ruleCount(view, rule)).toBe(5);
    expect(describeRule(view, rule).amount).toBe("5/10 (50%)");
    expect(recomputeMapidRules({ ...view, mapidMode: "MATRIX", mapidRules: [{ ...rule, count: 3 }] }).mapidRules![0]!.count).toBe(5);
  });

  it("nhóm cố định không bỏ vẫn bị trừ", () => {
    const view = withOmitted();
    view.blocks[0]!.omitted = false;
    expect(groupSize(view, view.blocks[1]!).units).toBe(6);
  });
});

describe("R1-F5: số câu lớn hơn số câu của môn bị chặn như máy chủ", () => {
  const view = section({ questionCount: 20 });
  const form = (over: object) => ({ ...emptyRuleForm("r1"), type: "COUNT" as const, nodeIds: ["n1"], ...over });

  it("COUNT theo số câu vượt số câu của môn: COUNT_ABOVE_TOTAL; bằng thì được", () => {
    expect(ruleFormIssues(form({ count: 21 }), view)).toContain("COUNT_ABOVE_TOTAL");
    expect(ruleFormIssues(form({ count: 20 }), view)).not.toContain("COUNT_ABOVE_TOTAL");
  });

  it("áp cho cả nhóm: vẫn so với số câu của môn, không so với nhóm (máy chủ để phần này cho bước kiểm tra)", () => {
    expect(ruleFormIssues(form({ groupId: "b1", count: 18 }), view)).not.toContain("COUNT_ABOVE_TOTAL");
    expect(ruleFormIssues(form({ groupId: "b1", count: 21 }), view)).toContain("COUNT_ABOVE_TOTAL");
  });

  it("môn chưa có số câu, nhập theo tỷ lệ, hoặc loại không có số: không báo", () => {
    expect(ruleFormIssues(form({ count: 999 }), section({ questionCount: null }))).not.toContain("COUNT_ABOVE_TOTAL");
    expect(ruleFormIssues(form({ entry: "PERCENT", percent: 50, count: 999 }), view)).not.toContain("COUNT_ABOVE_TOTAL");
    expect(ruleFormIssues(form({ type: "PRESENT", count: 999 }), view)).not.toContain("COUNT_ABOVE_TOTAL");
  });

  it("ONLY_IN_GROUP kèm số lượng cũng bị chặn", () => {
    expect(ruleFormIssues(form({ type: "ONLY_IN_GROUP", groupId: "b1", withNumber: true, count: 25 }), view)).toContain("COUNT_ABOVE_TOTAL");
    expect(ruleFormIssues(form({ type: "ONLY_IN_GROUP", groupId: "b1", withNumber: false, count: 25 }), view)).not.toContain("COUNT_ABOVE_TOTAL");
  });
});

describe("R1-F6: lọc năng lực của nhóm có quy tắc SOURCE riêng kế thừa của môn", () => {
  const matrix = (over: Partial<ExamSection> = {}): ExamSection =>
    section({
      mapidMode: "MATRIX",
      knowledgeScope: { nodeIds: [], competencyCodes: ["C1"] },
      mapidRules: [{ id: "r1", type: "SOURCE", groupId: "b1", nodeIds: ["n1"] }],
      ...over,
    });

  it("nhóm không có năng lực riêng dùng năng lực của môn", () => {
    const view = matrix();
    expect(groupCountScope(view.blocks[1]!, view)).toMatchObject({ nodeGroups: [["n1"]], competencyCodes: ["C1"] });
  });

  it("nhóm có năng lực riêng thì dùng của nhóm", () => {
    const view = matrix();
    const own = { ...view.blocks[1]!, knowledgeScope: { nodeIds: [], competencyCodes: ["C9"] } };
    expect(groupCountScope(own, view)).toMatchObject({ competencyCodes: ["C9"] });
  });
});

describe("R1-F4: tên nhóm đã xóa còn trên quy tắc cho tới khi lưu", () => {
  const rules: MapidRule[] = [
    { id: "r-b1", type: "COUNT", groupId: "b1", nodeIds: ["n1"], comparator: "EXACT", count: 3 },
    { id: "r-subject", type: "PRESENT", groupId: null, nodeIds: ["n2"] },
    { id: "r-single", type: "PRESENT", groupId: "b-single", nodeIds: ["n3"] },
  ];
  const part = (): ExamPart => ({ id: "p1", sections: [section({ mapidMode: "MATRIX", mapidRules: rules })], options: [] }) as unknown as ExamPart;

  it("xóa nhóm ghi tên nhóm lên quy tắc của nhóm đó, quy tắc khác không đổi", () => {
    const [next] = removeBlock([part()], "b1");
    const saved = next!.sections[0]!.mapidRules!;
    expect(saved.find((rule) => rule.id === "r-b1")).toMatchObject({ groupId: "b1", deletedGroupName: "Trả lời ngắn" });
    expect(saved.find((rule) => rule.id === "r-subject")).not.toHaveProperty("deletedGroupName");
    expect(saved.find((rule) => rule.id === "r-single")).not.toHaveProperty("deletedGroupName");
  });

  it("câu mô tả mang tên nhóm đã xóa, chặn lưu nêu đúng quy tắc, và tên không bao giờ gửi lên máy chủ", () => {
    const [next] = removeBlock([part()], "b1");
    const view = next!.sections[0]!;
    const rule = view.mapidRules![0]!;
    expect(describeRule(view, rule)).toMatchObject({ scope: "DELETED", deletedGroupName: "Trả lời ngắn" });
    expect(saveBlockers([next!], { grades: [], difficulties: [] })).toContainEqual({ kind: "MAPID_GROUP", sectionId: "s1", blockId: null, ruleId: "r-b1" });
    const wire = toApiParts([next!])[0]!.sections![0]! as unknown as { mapidRules: Array<Record<string, unknown>> };
    expect(wire.mapidRules.some((item) => "deletedGroupName" in item)).toBe(false);
  });

  it("chọn lại nhóm cho quy tắc (lưu từ biểu mẫu) bỏ tên đã xóa", () => {
    const [next] = removeBlock([part()], "b1");
    const view = next!.sections[0]!;
    const saved = ruleFromForm({ ...emptyRuleForm("r-b1", "b-single"), type: "PRESENT", nodeIds: ["n1"] }, view);
    expect(saved).not.toHaveProperty("deletedGroupName");
  });
});
