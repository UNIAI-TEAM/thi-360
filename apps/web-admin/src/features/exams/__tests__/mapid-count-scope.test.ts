import { beforeEach, describe, expect, it } from "vitest";
import { countAvailableQuestions, countSubjectAvailableQuestions } from "../api";
import { blockAvailableInput } from "../components/block-row-metrics";
import { groupCountScope } from "../mapid-rules";
import type { ExamSection, MapidRule, QuestionBlock } from "../types";
import { ExamsBackend } from "./fake-exams-backend";

const block = (over: Partial<QuestionBlock>): QuestionBlock => ({
  id: "b1",
  name: "Nhóm",
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

const single = block({ id: "single", name: "Trắc nghiệm", questionType: "SINGLE_CHOICE", questionCount: 32 });
const short = block({ id: "short" });
const set = block({ id: "set", name: "Chùm cuối", questionType: "QUESTION_SET", questionCount: 3, setCount: 1, setSize: 3 });

function section(mapidRules: MapidRule[], over: Partial<ExamSection> = {}): ExamSection {
  return {
    id: "s1",
    subjectCode: "TOAN",
    questionCount: 50,
    knowledgeScope: null,
    answerQuotas: null,
    matrix: null,
    choiceGroup: null,
    blocks: [single, short, set],
    mapidMode: "MATRIX",
    mapidRules,
    ...over,
  };
}

const only = (id: string, groupId: string, nodeIds: string[]): MapidRule => ({ id, type: "ONLY_IN_GROUP", groupId, nodeIds });
const source = (id: string, groupId: string | null, nodeIds: string[]): MapidRule => ({ id, type: "SOURCE", groupId, nodeIds });

describe("phạm vi đếm nguồn của nhóm ở môn MATRIX", () => {
  it("loại các MAPID mà quy tắc 'Chỉ xuất hiện ở nhóm này' của nhóm khác giữ riêng", () => {
    const s = section([only("r1", "short", ["n-ham-so"])]);

    expect(groupCountScope(single, s)).toEqual({ nodeIds: [], excludedNodeIds: ["n-ham-so"] });
    expect(groupCountScope(set, s)).toEqual({ nodeIds: [], excludedNodeIds: ["n-ham-so"] });
  });

  it("nhóm giữ riêng MAPID không tự loại mình", () => {
    const s = section([only("r1", "short", ["n-ham-so"])]);

    expect(groupCountScope(short, s)).toBeNull();
  });

  it("gộp loại trừ của nhiều quy tắc, mỗi MAPID một lần", () => {
    const s = section([only("r1", "short", ["a", "b"]), only("r2", "set", ["b", "c"])]);

    expect(groupCountScope(single, s)?.excludedNodeIds).toEqual(["a", "b", "c"]);
    expect(groupCountScope(short, s)?.excludedNodeIds).toEqual(["b", "c"]);
  });

  it("quy tắc của nhóm đã xóa không loại gì (máy chủ chặn lưu)", () => {
    expect(groupCountScope(single, section([only("r1", "gone", ["a"])]))).toBeNull();
  });

  it("phạm vi là quy tắc 'Chỉ lấy từ': của nhóm trước, không có thì của cả môn; chùm đòi cả chùm cùng phạm vi", () => {
    const s = section([source("s-all", null, ["m1", "m2"]), source("s-set", "set", ["m3"]), source("s-set2", "set", ["m4"])]);

    expect(groupCountScope(single, s)).toEqual({ nodeIds: ["m1", "m2"], competencyCodes: [], everyChildInScope: true });
    expect(groupCountScope(set, s)).toEqual({ nodeIds: ["m3", "m4"], competencyCodes: [], everyChildInScope: true });
  });

  it("MAPID cũ của môn và nhóm bị bỏ, lọc năng lực giữ", () => {
    const legacy = block({ id: "single", questionType: "SINGLE_CHOICE", knowledgeScope: { nodeIds: ["old"], competencyCodes: ["NL1"] } });
    const s = section([], { knowledgeScope: { nodeIds: ["old-section"], competencyCodes: ["NL2"] }, blocks: [legacy, short] });

    expect(groupCountScope(legacy, s)).toEqual({ nodeIds: [], competencyCodes: ["NL1"] });
    expect(groupCountScope(short, s)).toEqual({ nodeIds: [], competencyCodes: ["NL2"] });
  });

  it("chế độ ALL không giới hạn MAPID dù quy tắc còn trong bản nháp", () => {
    const s = section([source("s-all", null, ["m1"]), only("r1", "short", ["n"])], { mapidMode: "ALL" });

    expect(groupCountScope(single, s)).toBeNull();
  });

  it("môn chưa có chế độ MAPID giữ phạm vi kiểu cũ của nhóm hoặc của môn", () => {
    const own = block({ id: "single", knowledgeScope: { nodeIds: ["x"] } });
    const legacy = section([], { mapidMode: undefined, knowledgeScope: { nodeIds: ["y"] }, blocks: [own, short] });
    delete legacy.mapidRules;

    expect(groupCountScope(own, legacy)).toEqual({ nodeIds: ["x"] });
    expect(groupCountScope(short, legacy)).toEqual({ nodeIds: ["y"] });
  });

  it("blockAvailableInput (vùng sửa nhóm và bảng nhóm dùng chung) mang phạm vi này", () => {
    const s = section([only("r1", "short", ["n-ham-so"])]);

    expect(blockAvailableInput(single, s, "TOAN").knowledgeScope).toEqual({ nodeIds: [], excludedNodeIds: ["n-ham-so"] });
  });
});

describe("thân yêu cầu đếm nguồn", () => {
  let backend: ExamsBackend;
  beforeEach(() => {
    backend = new ExamsBackend();
    backend.install();
  });

  it("count và count-subject gửi excludedNodeIds và everyChildInScope; không có thì bỏ khỏi thân", async () => {
    backend.seedCounts("count", { status: 200, body: { total: 1, cells: [], byAnswerType: [] } });
    backend.seedCounts("count-subject", { status: 200, body: { total: 1, cells: [], byAnswerType: [] } });
    const s = section([source("s-all", null, ["m1"]), only("r1", "short", ["n-ham-so"])]);

    await countAvailableQuestions(blockAvailableInput(single, s, "TOAN"));
    await countSubjectAvailableQuestions({
      subjectCode: "TOAN",
      groups: [blockAvailableInput(short, s, "TOAN"), blockAvailableInput(set, s, "TOAN")],
    });

    const [call] = backend.calls("POST", "/api/v1/admin/exams/available-questions/count");
    expect(call?.body).toMatchObject({
      knowledgeScope: { nodeIds: ["m1"], everyChildInScope: true, excludedNodeIds: ["n-ham-so"] },
    });
    const [subject] = backend.calls("POST", "/api/v1/admin/exams/available-questions/count-subject");
    const groups = (subject?.body as { groups: Array<{ knowledgeScope?: Record<string, unknown> }> }).groups;
    expect(groups[0]?.knowledgeScope).toEqual({ nodeIds: ["m1"], competencyCodes: [], everyChildInScope: true });
    expect(groups[0]?.knowledgeScope).not.toHaveProperty("excludedNodeIds");
    expect(groups[1]?.knowledgeScope).toEqual({
      nodeIds: ["m1"],
      competencyCodes: [],
      everyChildInScope: true,
      excludedNodeIds: ["n-ham-so"],
    });
  });
});
