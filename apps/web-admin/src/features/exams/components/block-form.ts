import { isSetBlock } from "../matrix";
import type { QuestionType } from "@/features/content/types";
import type { BlockSlot, ChildCondition, ExamScope, ExamTemplate, ItemShape, QuestionBlock } from "../types";

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

/** Loại câu mà hình dạng câu còn có nghĩa khi lưu: Đúng/Sai (số ý), Ghép đôi (ý trái, lựa chọn phải), câu chọn (số phương án, R-131). */
function shapeKept(type: QuestionType | null): boolean {
  return shapeApplies(type) || type === "SINGLE_CHOICE" || type === "MULTIPLE_CHOICE";
}

/** Giữ trường hình dạng hợp với loại câu: số ý Đúng/Sai, số ý trái/phải Ghép đôi, số phương án câu chọn; bỏ trường của loại kia. */
function shapeFor(type: QuestionType | null, shape: ItemShape): ItemShape | undefined {
  const next: ItemShape =
    type === "TRUE_FALSE"
      ? { ...(shape.statements !== undefined ? { statements: shape.statements } : {}) }
      : type === "MATCHING"
        ? { ...(shape.leftItems !== undefined ? { leftItems: shape.leftItems } : {}), ...(shape.rightItems !== undefined ? { rightItems: shape.rightItems } : {}) }
        : { ...(shape.options !== undefined ? { options: shape.options } : {}) };
  return Object.keys(next).length > 0 ? next : undefined;
}

/**
 * Mẫu khóa dòng (H42, R-135): chỉ bài phạm vi `SUBJECT` từ mẫu `fixedBlockCounts` mà không phải bài ôn. Bài `PART` và bài ôn
 * cùng mẫu vẫn sửa được số lượng.
 */
export function blockCountsLocked(template: Pick<ExamTemplate, "fixedBlockCounts"> | null | undefined, scope: ExamScope, reviewMode: boolean): boolean {
  return template?.fixedBlockCounts === true && scope === "SUBJECT" && !reviewMode;
}

/**
 * Bỏ giá trị của điều kiện không còn áp dụng sau khi đổi loại câu hoặc cách lấy: hình dạng câu chỉ cho Đúng/Sai, Ghép
 * đôi; định dạng đáp án chỉ cho Trả lời ngắn; `omitted` chỉ cho Tự luận; `slots`, `childConditions` chỉ cho nhóm chùm.
 */
export function reconcileBlock(block: QuestionBlock): QuestionBlock {
  const type = sourceType(block);
  const next: QuestionBlock = { ...block };
  if (next.itemShape) {
    const shape = shapeKept(type) ? shapeFor(type, next.itemShape) : undefined;
    if (shape) next.itemShape = shape;
    else delete next.itemShape;
  }
  if (next.answerFormats && !formatApplies(type)) delete next.answerFormats;
  if (next.omitted && next.questionType !== "ESSAY") delete next.omitted;
  if (!isSetBlock(next)) {
    delete next.slots;
    delete next.childConditions;
    if (next.knowledgeScope?.everyChildInScope !== undefined) {
      const scope = { ...next.knowledgeScope };
      delete scope.everyChildInScope;
      // Không còn MAPID hay năng lực nào thì phạm vi trống: về `null` như nhóm chưa đặt phạm vi.
      next.knowledgeScope = scope.nodeIds.length > 0 || scope.competencyCodes?.length ? scope : null;
    }
  }
  return next;
}

/**
 * Bài ôn V-ACT bật sẵn "Mọi câu con thuộc chủ đề" (R-120): nhóm chùm có MAPID riêng mà chưa có cờ thì ghi `true` vào dữ
 * liệu, để lưu và đếm nguồn dùng đúng giá trị màn đang hiện. Đã có cờ (kể cả `false` do người dùng bỏ chọn): giữ nguyên.
 */
export function withEveryChildDefault(block: QuestionBlock): QuestionBlock {
  const scope = block.knowledgeScope;
  if (!isSetBlock(block) || !scope || scope.nodeIds.length === 0 || scope.everyChildInScope !== undefined) return block;
  return { ...block, knowledgeScope: { ...scope, everyChildInScope: true } };
}

// ---------------------------------------------------------------- điều kiện riêng từng chùm

export function slotHasCondition(slot: BlockSlot | undefined): boolean {
  return (slot?.tagCodes?.length ?? 0) > 0 || (slot?.knowledgeScope?.nodeIds.length ?? 0) > 0;
}

/**
 * Đổi cách khai chùm (H28): sang "chỉ nhập tổng" thì bỏ số chùm, cỡ chùm, điều kiện riêng từng chùm và điều kiện theo vị
 * trí câu con (cỡ chùm không cố định nên vị trí không có nghĩa), giữ tổng câu con; sang "khai số chùm × cỡ" thì dựng 1
 * chùm cỡ bằng tổng hiện có để người dùng sửa tiếp. Nhóm không bao giờ mang cả hai.
 */
export function switchSetMode(block: QuestionBlock, mode: "total" | "sets"): Partial<QuestionBlock> {
  if (mode === "total") return { setCount: null, setSize: null, slots: undefined, childConditions: undefined };
  const size = Math.max(block.questionCount ?? 0, 1);
  return { setCount: 1, setSize: size, questionCount: size };
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
  // H39: điều kiện theo dạng câu con (`answerType`) không cần MAPID.
  if (condition.nodeIds.length === 0 && !condition.answerType) return "NO_MAPID";
  return null;
}

export function newChildCondition(setSize: number): ChildCondition {
  const from = Math.max(1, setSize);
  return { from, to: from, nodeIds: [] };
}

export interface PositionRow {
  from: number;
  to: number;
  answerType: QuestionType;
  itemShape?: ItemShape;
  /** Số câu con, số lệnh hỏi (câu Đúng/Sai tính theo số ý, Ghép đôi theo số ý trái) và điểm của dải vị trí trong một chùm. */
  questions: number;
  commands: number;
  points: number;
}

/** Các dải vị trí có khai dạng câu con của chùm (chùm trộn dạng, R-132), theo thứ tự vị trí. Điểm theo `pointsEach` của khối. */
export function positionRows(block: Pick<QuestionBlock, "childConditions">, pointsEach: number): PositionRow[] {
  return (block.childConditions ?? [])
    .filter((condition): condition is ChildCondition & { answerType: QuestionType } => condition.answerType !== undefined && condition.to >= condition.from)
    .sort((a, b) => a.from - b.from)
    .map((condition) => {
      const questions = condition.to - condition.from + 1;
      const perQuestion =
        condition.answerType === "TRUE_FALSE"
          ? (condition.itemShape?.statements ?? 1)
          : condition.answerType === "MATCHING"
            ? (condition.itemShape?.leftItems ?? 1)
            : 1;
      return {
        from: condition.from,
        to: condition.to,
        answerType: condition.answerType,
        ...(condition.itemShape ? { itemShape: condition.itemShape } : {}),
        questions,
        commands: questions * perQuestion,
        points: Math.round(questions * pointsEach * 10_000) / 10_000,
      };
    });
}

// ---------------------------------------------------------------- nhập theo tỷ lệ (R-142)

export interface RateSetMismatch {
  /** Số câu mà tỷ lệ ra. */
  count: number;
  /** Hai số câu gần nhất chia hết cho cỡ chùm; `low` là 0 khi tỷ lệ ra ít hơn một chùm. */
  low: number;
  high: number;
}

/**
 * Tỷ lệ của dòng chùm phải ra số câu chia hết cho cỡ chùm: hệ thống chỉ lấy nguyên chùm. Trả về hai số câu gần nhất khi
 * không chia hết, `null` khi hợp lệ.
 */
export function rateSetMismatch(count: number, setSize: number): RateSetMismatch | null {
  if (setSize < 1 || (count > 0 && count % setSize === 0)) return null;
  const low = Math.floor(count / setSize) * setSize;
  return { count, low, high: low + setSize };
}
