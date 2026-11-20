import { describe, expect, it } from "vitest";
import { subjectFormFor } from "../subject-forms/registry";
import type { RowContext } from "../subject-forms/row-list-model";
import { evenSplit, replaceWithSplit, splitBlock, splitChoices, splitIssues, splitState } from "../subject-forms/row-split";
import { block, section, setBlock } from "./subject-form-builders";

const context = (over: Partial<RowContext> = {}): RowContext => ({ scope: "SUBJECT", reviewMode: false, template: null, ...over });
const hscaEnglish = subjectFormFor("HSCA", "ANH")!;
const ids = () => {
  let next = 0;
  return () => `new-${++next}`;
};

describe("row-split: chia số, lỗi (R-7, AC 51)", () => {
  it("chia đều theo đơn vị nguyên, phần dư cho dòng đứng trước; n chỉ từ 2 tới số đơn vị", () => {
    expect(evenSplit(4, 3)).toEqual([2, 1, 1]);
    expect(evenSplit(10, 4)).toEqual([3, 3, 2, 2]);
    expect(evenSplit(4, 4)).toEqual([1, 1, 1, 1]);
    expect(splitChoices(4)).toEqual([2, 3, 4]);
    // 4 chùm thành 5 dòng không chọn được.
    expect(splitChoices(4)).not.toContain(5);
    expect(splitChoices(1)).toEqual([]);
  });

  it("positiveChildren: mỗi dòng con là số nguyên dương, tổng bằng dòng cha; 0 + 2 + 1 + 1 báo lỗi tại dòng con 0", () => {
    expect(splitIssues(4, [1, 1, 1, 1])).toEqual([]);
    expect(splitIssues(4, [0, 2, 1, 1])).toEqual([{ code: "NOT_POSITIVE", index: 0 }]);
    // Tách 4 → 4 rồi sửa một dòng con về 0: dòng con lỗi và tổng thiếu.
    expect(splitIssues(4, [0, 1, 1, 1])).toEqual([
      { code: "NOT_POSITIVE", index: 0 },
      { code: "TOTAL", total: 3, units: 4 },
    ]);
    expect(splitIssues(4, [2, 1, 2])).toEqual([{ code: "TOTAL", total: 5, units: 4 }]);
    expect(splitIssues(4, [2.5, 1.5]).map((issue) => issue.code)).toEqual(["NOT_POSITIVE", "NOT_POSITIVE"]);
    expect(splitIssues(4, [Number.NaN, 4]).map((issue) => issue.code)).toContain("NOT_POSITIVE");
    expect(splitIssues(4, [-1, 5]).map((issue) => issue.code)).toContain("NOT_POSITIVE");
  });
});

describe("row-split: dòng con (R-7 bảng Trường của dòng con)", () => {
  const logic = setBlock("logic", 4, 3, { name: "Logic", templateGroupCode: "VACT_LOGIC", shuffleEditable: true, shuffleUnits: true, requiredPointsPerQuestion: 0.5, positionRange: { from: 1, to: 12 } });

  it("firstChildKeepsId: dòng con đầu giữ id và templateGroupCode của dòng cha; các dòng sau có id mới và không có trường của mẫu", () => {
    const children = splitBlock(logic, [1, 1, 1, 1], ids());
    expect(children.map((child) => child.id)).toEqual(["logic", "new-1", "new-2", "new-3"]);
    expect(children[0]).toMatchObject({ templateGroupCode: "VACT_LOGIC", shuffleEditable: true, name: "Logic 1" });
    for (const child of children.slice(1)) {
      expect(child.templateGroupCode).toBeNull();
      expect(child.answerTypeLocked).toBe(false);
      expect(child).not.toHaveProperty("shuffleEditable");
    }
    expect(children.map((child) => child.name)).toEqual(["Logic 1", "Logic 2", "Logic 3", "Logic 4"]);
    // Tách Logic 12 (4 chùm × 3) thành 4 dòng 1 × 3: tổng câu 12, cỡ chùm giữ 3.
    expect(children.map((child) => [child.setCount, child.setSize, child.questionCount])).toEqual([[1, 3, 3], [1, 3, 3], [1, 3, 3], [1, 3, 3]]);
    expect(children.reduce((total, child) => total + (child.questionCount ?? 0), 0)).toBe(12);
    // Điểm mỗi câu, dạng, cỡ chùm chép từ dòng cha; `positionRange` do máy chủ tính, không chép.
    for (const child of children) {
      expect(child.requiredPointsPerQuestion).toBe(0.5);
      expect(child.questionType).toBe("QUESTION_SET");
      expect(child).not.toHaveProperty("positionRange");
    }
  });

  it("chia không đều: dòng chùm 4 → 3 dòng 2 + 1 + 1 chùm, câu = số chùm × cỡ chùm; dòng câu đơn chia theo câu", () => {
    const sets = splitBlock(logic, [2, 1, 1], ids());
    expect(sets.map((child) => [child.setCount, child.questionCount])).toEqual([[2, 6], [1, 3], [1, 3]]);
    const single = splitBlock(block("single", { name: "Doc", questionCount: 10 }), [3, 3, 2, 2], ids());
    expect(single.map((child) => child.questionCount)).toEqual([3, 3, 2, 2]);
    expect(single.every((child) => child.setCount === null)).toBe(true);
  });

  it("keepsShuffleUnits: shuffleUnits giữ đúng giá trị ở mọi dòng con, kể cả false; trường thiếu vẫn thiếu", () => {
    const off = splitBlock(setBlock("s", 4, 10, { shuffleUnits: false }), [1, 1, 1, 1], ids());
    expect(off.map((child) => child.shuffleUnits)).toEqual([false, false, false, false]);
    const missing = splitBlock(setBlock("s", 4, 10), [2, 2], ids());
    expect(missing.every((child) => !("shuffleUnits" in child) || child.shuffleUnits === undefined)).toBe(true);
  });

  it("chép mọi trường lọc: nhãn, phạm vi, hình dạng câu, định dạng đáp án, cách chấm dự kiến", () => {
    const scope = { nodeIds: ["n1"] };
    const source = block("r", { questionCount: 8, tagCodes: ["T"], knowledgeScope: scope, itemShape: { statements: 4 }, answerFormats: ["INTEGER"], expectedScoring: { multipleChoiceRule: "PARTIAL" }, childAnswerType: null });
    for (const child of splitBlock(source, [4, 4], ids())) {
      expect(child).toMatchObject({ tagCodes: ["T"], knowledgeScope: scope, itemShape: { statements: 4 }, answerFormats: ["INTEGER"], expectedScoring: { multipleChoiceRule: "PARTIAL" } });
    }
  });

  it("replaceWithSplit: dòng cha bị thay đúng tại vị trí, dòng khác và môn khác không đổi; tổng mục giữ nguyên", () => {
    const first = block("a", { questionCount: 5 });
    const last = block("z", { questionCount: 7 });
    const original = section("s", "ANH", [first, setBlock("m", 4, 10), last]);
    const next = replaceWithSplit(original, "m", splitBlock(original.blocks[1]!, [1, 3], ids()));
    expect(next.blocks.map((item) => item.id)).toEqual(["a", "m", "new-1", "z"]);
    expect(next.blocks[0]).toBe(first);
    expect(next.blocks[3]).toBe(last);
    expect(next.blocks.reduce((total, item) => total + (item.questionCount ?? 0), 0)).toBe(5 + 40 + 7);
    expect(replaceWithSplit(original, "zzz", [])).toBe(original);
  });
});

describe("row-split: khi nào được tách (R-7, AC 22, AC 51 e, h)", () => {
  const reading = setBlock("read", 4, 10, { name: "Doc" });
  const state = (target = reading, over: Partial<RowContext> = {}, subject = section("s", "ANH", [target])) => splitState(target, subject, hscaEnglish, context(over));

  it("H-SCA Anh tách được dòng Đọc 4 × 10; cặp không đăng ký (HSA Văn, V-SAT Anh) không có nút", () => {
    expect(state()).toEqual({ status: "OPEN", units: 4 });
    expect(splitState(reading, section("s", "VAN", [reading]), subjectFormFor("HSA", "VAN")!, context())).toEqual({ status: "HIDDEN" });
    expect(splitState(reading, section("s", "ANH", [reading]), subjectFormFor("VSAT", "ANH")!, context())).toEqual({ status: "HIDDEN" });
    expect(splitState(reading, section("s", "SCIENTIFIC_THINKING", [reading]), subjectFormFor("VACT", "SCIENTIFIC_THINKING")!, context())).toEqual({ status: "OPEN", units: 4 });
    // Dòng chỉ có một đơn vị không còn gì để chia.
    expect(state(setBlock("one", 1, 10))).toEqual({ status: "HIDDEN" });
  });

  it("splitBlockedWithConstraints: slots, childConditions, matrix, topicQuotas của dòng, hoặc chỉ tiêu mục tham chiếu qua groupIds khóa nút tách kèm lý do", () => {
    const locked = { status: "LOCKED", reason: "rowHasConditions" };
    expect(state(setBlock("s", 4, 10, { slots: [{ index: 1, tagCodes: ["T"] }] }))).toEqual(locked);
    expect(state(setBlock("c", 4, 10, { childConditions: [{ from: 1, to: 1, nodeIds: ["n"] }] }))).toEqual(locked);
    expect(state(setBlock("m", 4, 10, { matrix: { mode: "SEPARATE", unit: "COUNT", cells: [], byGrade: [], byDifficulty: [{ code: "NB", value: 40 }] } }))).toEqual(locked);
    const quota = { id: "q", name: "Q", nodeIds: ["n"], count: 2, enforcement: "REQUIRED" as const };
    expect(state(setBlock("t", 4, 10, { topicQuotas: [quota] }))).toEqual(locked);
    const referenced = setBlock("g", 4, 10);
    expect(state(referenced, {}, section("s", "ANH", [referenced], { topicQuotas: [{ ...quota, groupIds: ["g"] }] }))).toEqual(locked);
    // Chỉ tiêu mục không tham chiếu dòng này thì không khóa.
    expect(state(referenced, {}, section("s", "ANH", [referenced], { topicQuotas: [{ ...quota, groupIds: ["other"] }] }))).toEqual({ status: "OPEN", units: 4 });
  });

  it("flexibleAndOmittedLocked: chùm cỡ linh hoạt và dòng đang tạm bỏ qua khóa nút tách kèm lý do riêng", () => {
    const flexible = setBlock("f", 4, 10, { setCount: null, setSize: null, questionCount: 12 });
    expect(state(flexible)).toEqual({ status: "LOCKED", reason: "rowNoSetSize" });
    const omitted = block("o", { questionType: "ESSAY", questionCount: 3, omitted: true });
    expect(state(omitted)).toEqual({ status: "LOCKED", reason: "rowOmitted" });
  });

  it("khóa dòng (H42) và khóa dạng khóa nút tách; bài PART từ cùng mẫu tách được", () => {
    const template = { fixedBlockCounts: true, omittedLocked: null, parts: [] };
    expect(state(reading, { template })).toEqual({ status: "LOCKED", reason: "rowCounts" });
    expect(state(reading, { template, scope: "PART" })).toEqual({ status: "OPEN", units: 4 });
    expect(state(setBlock("t", 4, 10, { answerTypeLocked: true }))).toEqual({ status: "LOCKED", reason: "answerType" });
  });
});
