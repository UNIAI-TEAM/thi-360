import { isSetBlock } from "../matrix";
import type { QuestionType } from "@/features/content/types";
import type { BlockSlot, ChildCondition, ItemShape, QuestionBlock } from "../types";

/**
 * Logic thuần của biểu mẫu nhóm (2B 3.3, 15.3): điều kiện của nhóm, điều kiện riêng từng chùm (`slots`), điều kiện theo
 * vị trí câu con (`childConditions`). Không có dạng bài (K11): loại câu được phân biệt bằng MAPID hoặc nhãn.
 */

/** Các điều kiện thêm được ở menu "Thêm điều kiện". MAPID là phạm vi kiến thức của nhóm (`knowledgeScope.nodeIds`). */
export const CONDITION_KINDS = ["MAPID", "TAGS", "ITEM_SHAPE", "ANSWER_FORMATS", "POINTS"] as const;
export type ConditionKind = (typeof CONDITION_KINDS)[number];

/** Định dạng đáp án của câu Trả lời ngắn (H11). */
export const ANSWER_FORMATS = ["INTEGER", "DECIMAL", "FRACTION", "TEXT", "CHEM_FORMULA", "CHEM_EQUATION"] as const;

/** Loại câu mà bộ lọc nguồn áp lên: câu con của nhóm chùm, hoặc chính nhóm câu đơn. `null`: chưa giới hạn. */
export function sourceType(block: Pick<QuestionBlock, "questionType" | "childAnswerType">): QuestionType | null {
  return isSetBlock(block) ? block.childAnswerType : block.questionType;
}

const SHAPED_TYPES: readonly (QuestionType | null)[] = ["TRUE_FALSE", "MATCHING"];

function shapeApplies(type: QuestionType | null): boolean {
  return SHAPED_TYPES.includes(type);
}

/** Câu Trả lời ngắn có thể nằm trong nhóm khi loại câu là Trả lời ngắn hoặc chưa giới hạn (`null`). */
function formatApplies(type: QuestionType | null): boolean {
  return type === "SHORT_ANSWER" || type === null;
}

export type ConditionState = "available" | "added" | "unavailable";

/** Trạng thái của một điều kiện trong menu: đã thêm (có giá trị hoặc vừa thêm, chưa điền), thêm được, hoặc không áp. */
export function conditionState(block: QuestionBlock, kind: ConditionKind, pending: ReadonlySet<ConditionKind>): ConditionState {
  const type = sourceType(block);
  switch (kind) {
    case "MAPID":
      return (block.knowledgeScope?.nodeIds.length ?? 0) > 0 ? "added" : "available";
    case "TAGS":
      return block.tagCodes.length > 0 || pending.has(kind) ? "added" : "available";
    case "ITEM_SHAPE":
      if (block.itemShape || pending.has(kind)) return shapeApplies(type) ? "added" : "unavailable";
      return shapeApplies(type) ? "available" : "unavailable";
    case "ANSWER_FORMATS":
      if ((block.answerFormats?.length ?? 0) > 0 || pending.has(kind)) return formatApplies(type) ? "added" : "unavailable";
      return formatApplies(type) ? "available" : "unavailable";
    case "POINTS":
      return block.requiredPointsPerQuestion !== null || pending.has(kind) ? "added" : "available";
  }
}

/** Số ý trái/phải hoặc số ý Đúng/Sai phù hợp với loại câu: bỏ trường của loại kia. */
function shapeFor(type: QuestionType | null, shape: ItemShape): ItemShape | undefined {
  const next: ItemShape =
    type === "TRUE_FALSE"
      ? { ...(shape.statements !== undefined ? { statements: shape.statements } : {}) }
      : { ...(shape.leftItems !== undefined ? { leftItems: shape.leftItems } : {}), ...(shape.rightItems !== undefined ? { rightItems: shape.rightItems } : {}) };
  return Object.keys(next).length > 0 ? next : undefined;
}

/**
 * Bỏ giá trị của điều kiện không còn áp dụng sau khi đổi loại câu hoặc cách lấy: hình dạng câu chỉ cho Đúng/Sai, Ghép
 * đôi; định dạng đáp án chỉ cho Trả lời ngắn; `omitted` chỉ cho Tự luận; `slots`, `childConditions` chỉ cho nhóm chùm.
 */
export function reconcileBlock(block: QuestionBlock): QuestionBlock {
  const type = sourceType(block);
  const next: QuestionBlock = { ...block };
  if (next.itemShape) {
    const shape = shapeApplies(type) ? shapeFor(type, next.itemShape) : undefined;
    if (shape) next.itemShape = shape;
    else delete next.itemShape;
  }
  if (next.answerFormats && !formatApplies(type)) delete next.answerFormats;
  if (next.omitted && next.questionType !== "ESSAY") delete next.omitted;
  if (!isSetBlock(next)) {
    delete next.slots;
    delete next.childConditions;
  }
  return next;
}

// ---------------------------------------------------------------- điều kiện riêng từng chùm

export function slotHasCondition(slot: BlockSlot | undefined): boolean {
  return (slot?.tagCodes?.length ?? 0) > 0 || (slot?.knowledgeScope?.nodeIds.length ?? 0) > 0;
}

/** Điều kiện của chùm `index` (từ 1); chùm chưa có phần tử trả về phần tử rỗng. */
export function slotAt(slots: readonly BlockSlot[] | undefined, index: number): BlockSlot {
  return slots?.find((slot) => slot.index === index) ?? { index };
}

/**
 * `slots` gửi lên: không chùm nào có điều kiện thì bỏ hẳn (mọi chùm dùng điều kiện của nhóm); ngược lại đúng một phần
 * tử cho mỗi chùm từ 1 đến `setCount` (chùm không đặt gì là phần tử rỗng), bỏ phần tử của chùm vượt số chùm.
 */
export function normalizeSlots(slots: readonly BlockSlot[] | undefined, setCount: number): BlockSlot[] | undefined {
  const kept = (slots ?? []).filter((slot) => slot.index >= 1 && slot.index <= setCount);
  if (!kept.some(slotHasCondition)) return undefined;
  return Array.from({ length: setCount }, (_, offset) => {
    const slot = slotAt(kept, offset + 1);
    return {
      index: offset + 1,
      ...((slot.knowledgeScope?.nodeIds.length ?? 0) > 0 ? { knowledgeScope: slot.knowledgeScope } : {}),
      ...((slot.tagCodes?.length ?? 0) > 0 ? { tagCodes: slot.tagCodes } : {}),
    };
  });
}

/** Áp một phần thay đổi: khóa có giá trị `undefined` bị bỏ khỏi nhóm, rồi dọn điều kiện không còn áp (`reconcileBlock`). */
export function applyBlockPatch(block: QuestionBlock, patch: Partial<QuestionBlock>): QuestionBlock {
  const merged: Record<string, unknown> = { ...block, ...patch };
  for (const key of Object.keys(patch)) {
    if (merged[key] === undefined) delete merged[key];
  }
  return reconcileBlock(merged as unknown as QuestionBlock);
}

/** Chùm vượt số chùm mới mà đang có điều kiện riêng: bị bỏ khi đổi số chùm. */
export function droppedSlotIndexes(slots: readonly BlockSlot[] | undefined, setCount: number): number[] {
  return (slots ?? []).filter((slot) => slot.index > setCount && slotHasCondition(slot)).map((slot) => slot.index);
}

export function withSlot(
  slots: readonly BlockSlot[] | undefined,
  index: number,
  patch: Partial<Omit<BlockSlot, "index">>,
  setCount: number,
): BlockSlot[] | undefined {
  const base = slots?.some((slot) => slot.index === index) ? [...slots] : [...(slots ?? []), { index }];
  return normalizeSlots(
    base.map((slot) => (slot.index === index ? { ...slot, ...patch } : slot)),
    setCount,
  );
}

/**
 * Đổi số chùm hoặc số câu con: số câu của nhóm là tích, `slots` theo số chùm mới. Số chùm 0 là ô đang được xóa để gõ
 * số mới, chưa phải ý định bỏ chùm: giữ nguyên `slots` để gõ lại không mất điều kiện đã đặt.
 */
export function resizeSets(
  block: QuestionBlock,
  setCount: number,
  setSize: number,
): { patch: Partial<QuestionBlock>; dropped: number[] } {
  if (setCount < 1) return { patch: { setCount, setSize, questionCount: setCount * setSize }, dropped: [] };
  return {
    patch: { setCount, setSize, questionCount: setCount * setSize, slots: normalizeSlots(block.slots, setCount) },
    dropped: droppedSlotIndexes(block.slots, setCount),
  };
}

// ---------------------------------------------------------------- điều kiện theo vị trí câu con

export type ChildConditionIssue = "FROM_MIN" | "RANGE_ORDER" | "OUT_OF_SET" | "NO_MAPID";

/** Lỗi của một dòng điều kiện theo vị trí; `setSize` là số câu con mỗi chùm (0: chưa biết, không so). */
export function childConditionIssue(condition: ChildCondition, setSize: number): ChildConditionIssue | null {
  if (condition.from < 1) return "FROM_MIN";
  if (condition.to < condition.from) return "RANGE_ORDER";
  if (setSize > 0 && condition.to > setSize) return "OUT_OF_SET";
  if (condition.nodeIds.length === 0) return "NO_MAPID";
  return null;
}

export function newChildCondition(setSize: number): ChildCondition {
  const from = Math.max(1, setSize);
  return { from, to: from, nodeIds: [] };
}
