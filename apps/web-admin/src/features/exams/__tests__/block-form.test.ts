import { describe, expect, it } from "vitest";
import {
  applyBlockPatch,
  blockCountsLocked,
  childConditionIssue,
  conditionState,
  droppedSlotIndexes,
  normalizeSlots,
  reconcileBlock,
  resizeSets,
  slotAt,
  withSlot,
} from "../components/block-form";
import { mapPart, toApiParts } from "../api-mapping";
import type { components } from "@thi360/api-client";
import type { QuestionBlock } from "../types";

const NONE = new Set<never>();

function block(patch: Partial<QuestionBlock> = {}): QuestionBlock {
  return {
    id: "b1",
    name: "Nhom",
    questionType: "SINGLE_CHOICE",
    questionCount: 4,
    setCount: null,
    setSize: null,
    requiredPointsPerQuestion: null,
    knowledgeScope: null,
    tagCodes: [],
    matrix: null,
    childAnswerType: null,
    templateGroupCode: null,
    answerTypeLocked: false,
    ...patch,
  };
}

function setBlock(patch: Partial<QuestionBlock> = {}): QuestionBlock {
  return block({ questionType: "QUESTION_SET", setCount: 5, setSize: 5, questionCount: 25, ...patch });
}

describe("điều kiện riêng từng chùm (slots)", () => {
  it("không chùm nào có điều kiện thì không gửi slots", () => {
    expect(normalizeSlots([{ index: 1 }, { index: 2, tagCodes: [] }], 5)).toBeUndefined();
    expect(normalizeSlots(undefined, 5)).toBeUndefined();
  });

  it("có điều kiện thì đúng một phần tử cho mỗi chùm, theo thứ tự 1..số chùm", () => {
    const slots = normalizeSlots([{ index: 4, tagCodes: ["THO"] }], 5)!;
    expect(slots.map((slot) => slot.index)).toEqual([1, 2, 3, 4, 5]);
    expect(slots[3]).toEqual({ index: 4, tagCodes: ["THO"] });
    expect(slots[0]).toEqual({ index: 1 });
  });

  it("đặt điều kiện cho một chùm của nhóm chưa có slots vẫn ra đủ số chùm", () => {
    const slots = withSlot(undefined, 2, { tagCodes: ["TIN"] }, 5)!;
    expect(slots).toHaveLength(5);
    expect(slotAt(slots, 2).tagCodes).toEqual(["TIN"]);
  });

  it("xóa điều kiện của chùm cuối cùng thì bỏ hẳn slots", () => {
    const slots = withSlot(undefined, 2, { tagCodes: ["TIN"] }, 3);
    expect(withSlot(slots, 2, { tagCodes: undefined }, 3)).toBeUndefined();
  });

  it("đổi số chùm: slots luôn bằng số chùm mới; chùm vượt số chùm bị bỏ và được báo", () => {
    const original = setBlock({ slots: normalizeSlots([{ index: 2, tagCodes: ["A"] }, { index: 5, tagCodes: ["B"] }], 5) });
    const smaller = resizeSets(original, 4, 5);
    expect(smaller.patch.slots).toHaveLength(4);
    expect(smaller.patch.questionCount).toBe(20);
    expect(smaller.dropped).toEqual([5]);
    const larger = resizeSets(original, 7, 5);
    expect(larger.patch.slots).toHaveLength(7);
    expect(larger.dropped).toEqual([]);
    expect(droppedSlotIndexes(original.slots, 5)).toEqual([]);
  });
});

describe("điều kiện theo vị trí câu con", () => {
  it("báo lỗi dải vị trí, vượt số câu con, thiếu MAPID", () => {
    expect(childConditionIssue({ from: 0, to: 1, nodeIds: ["n"] }, 5)).toBe("FROM_MIN");
    expect(childConditionIssue({ from: 3, to: 2, nodeIds: ["n"] }, 5)).toBe("RANGE_ORDER");
    expect(childConditionIssue({ from: 6, to: 8, nodeIds: ["n"] }, 5)).toBe("OUT_OF_SET");
    expect(childConditionIssue({ from: 2, to: 3, nodeIds: [] }, 5)).toBe("NO_MAPID");
    expect(childConditionIssue({ from: 2, to: 3, nodeIds: ["n"] }, 5)).toBeNull();
  });
});

describe("điều kiện áp theo loại câu", () => {
  it("hình dạng câu chỉ cho Đúng/Sai và Ghép đôi; định dạng đáp án chỉ cho Trả lời ngắn hoặc chưa giới hạn", () => {
    expect(conditionState(block(), "ITEM_SHAPE", NONE)).toBe("unavailable");
    expect(conditionState(block({ questionType: "TRUE_FALSE" }), "ITEM_SHAPE", NONE)).toBe("available");
    expect(conditionState(block({ questionType: "MATCHING" }), "ITEM_SHAPE", NONE)).toBe("available");
    expect(conditionState(block(), "ANSWER_FORMATS", NONE)).toBe("unavailable");
    expect(conditionState(block({ questionType: "SHORT_ANSWER" }), "ANSWER_FORMATS", NONE)).toBe("available");
    // Nhóm chùm xét theo loại của câu con.
    expect(conditionState(setBlock({ childAnswerType: "TRUE_FALSE" }), "ITEM_SHAPE", NONE)).toBe("available");
    expect(conditionState(setBlock({ childAnswerType: "SINGLE_CHOICE" }), "ITEM_SHAPE", NONE)).toBe("unavailable");
  });

  it("đổi loại câu thì bỏ điều kiện không còn áp dụng", () => {
    const original = block({ questionType: "TRUE_FALSE", itemShape: { statements: 4 }, answerFormats: ["INTEGER"], omitted: true });
    const next = applyBlockPatch(original, { questionType: "SINGLE_CHOICE" });
    expect(next).not.toHaveProperty("itemShape");
    expect(next).not.toHaveProperty("answerFormats");
    expect(next).not.toHaveProperty("omitted");
  });

  it("rời nhóm chùm thì bỏ slots và childConditions", () => {
    const original = setBlock({ slots: [{ index: 1, tagCodes: ["A"] }], childConditions: [{ from: 1, to: 1, nodeIds: ["n"] }] });
    const next = applyBlockPatch(original, { questionType: "SINGLE_CHOICE", setCount: null, setSize: null });
    expect(next).not.toHaveProperty("slots");
    expect(next).not.toHaveProperty("childConditions");
  });

  it("khóa có giá trị undefined trong bản vá bị bỏ khỏi nhóm", () => {
    expect(applyBlockPatch(block({ itemShape: { statements: 4 }, questionType: "TRUE_FALSE" }), { itemShape: undefined })).not.toHaveProperty("itemShape");
  });
});

describe("giá trị đã lưu đi qua dạng API của cấu trúc", () => {
  const wireBlock: components["schemas"]["QuestionBlock"] = {
    id: "b1",
    name: "Doc hieu",
    questionType: "QUESTION_SET",
    questionCount: 25,
    setCount: 5,
    setSize: 5,
    requiredPointsPerQuestion: 0.75,
    knowledgeScope: { nodeIds: ["n1"] },
    tagCodes: ["TRAP"],
    childAnswerType: "SINGLE_CHOICE",
    answerTypeLocked: false,
    shuffleUnits: false,
    shuffleEditable: true,
    omitted: false,
    slots: [
      { index: 1, tagCodes: ["THO"] },
      { index: 2, knowledgeScope: { nodeIds: ["n2"] } },
      { index: 3 },
      { index: 4 },
      { index: 5 },
    ],
    childConditions: [{ from: 2, to: 3, nodeIds: ["n3"] }],
    itemShape: { leftItems: 4, rightItems: 6 },
    answerFormats: ["INTEGER", "FRACTION"],
  };

  it("đọc rồi gửi lại không đổi giá trị; positionRange chỉ có ở phản hồi nên không gửi lại", () => {
    const part = mapPart(
      {
        name: "P",
        participation: { mode: "ALL" },
        sections: [{ subjectCode: "LIT", blocks: [{ ...wireBlock, positionRange: { from: 26, to: 50 } }] }],
      },
      0,
    );
    expect(part.sections[0]!.blocks[0]!.positionRange).toEqual({ from: 26, to: 50 });
    const [sent] = toApiParts([part]) as unknown as Array<{ sections: Array<{ blocks: Array<Record<string, unknown>> }> }>;
    expect(sent!.sections[0]!.blocks[0]).toEqual(wireBlock);
  });

  it("nhóm không có trường mới thì không gửi trường mới, không gửi taskTypeCode", () => {
    const part = mapPart({ name: "P", participation: { mode: "ALL" }, sections: [{ subjectCode: "MATH", blocks: [{ name: "A" }] }] }, 0);
    const [sent] = toApiParts([part]) as unknown as Array<{ sections: Array<{ blocks: Array<Record<string, unknown>> }> }>;
    const keys = Object.keys(sent!.sections[0]!.blocks[0]!);
    for (const key of ["slots", "childConditions", "itemShape", "answerFormats", "shuffleUnits", "omitted", "positionRange", "taskTypeCode"]) {
      expect(keys).not.toContain(key);
    }
  });
});

describe("FE-7 dòng 12: hình dạng câu giữ số phương án", () => {
  it("giữ itemShape.options của câu chọn một khi sửa nhóm, bỏ trường của loại khác", () => {
    const patched = applyBlockPatch(block({ itemShape: { options: 4 } }), { name: "Chọn một (đã đổi tên)" });
    expect(patched.itemShape).toEqual({ options: 4 });
    expect(reconcileBlock(block({ itemShape: { options: 4, statements: 4 } })).itemShape).toEqual({ options: 4 });
    expect(reconcileBlock(block({ questionType: "TRUE_FALSE", itemShape: { statements: 4, options: 4 } })).itemShape).toEqual({ statements: 4 });
    expect(reconcileBlock(block({ questionType: "SHORT_ANSWER", itemShape: { options: 4 } })).itemShape).toBeUndefined();
  });
});

describe("FE-7 dòng 5: khóa dòng theo mẫu fixedBlockCounts", () => {
  const template = { fixedBlockCounts: true };
  it("chỉ khóa bài SUBJECT, không khóa bài PART, bài ôn hay mẫu không khai", () => {
    expect(blockCountsLocked(template, "SUBJECT", false)).toBe(true);
    expect(blockCountsLocked(template, "PART", false)).toBe(false);
    expect(blockCountsLocked(template, "SUBJECT", true)).toBe(false);
    expect(blockCountsLocked({ fixedBlockCounts: false }, "SUBJECT", false)).toBe(false);
    expect(blockCountsLocked(null, "SUBJECT", false)).toBe(false);
  });
});
