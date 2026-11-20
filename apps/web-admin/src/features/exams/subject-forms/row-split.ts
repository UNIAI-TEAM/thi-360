import { draftId } from "../components/exam-structure-draft";
import { isFlexibleSet, isSetBlock } from "../matrix";
import type { ExamSection, QuestionBlock } from "../types";
import type { LockReasonCode } from "./lock-reason";
import type { SubjectFormEntry } from "./registry";
import { rowLocks, type RowContext } from "./row-list-model";

/**
 * Luật tách dòng của Danh sách dòng (FTM R-7, plan CR-3b): chỉ ở cặp đăng ký (H-SCA Anh, V-ACT Tư duy khoa học) và chỉ ở dòng không
 * chịu khóa dòng, khóa dạng; dòng có điều kiện riêng, chùm cỡ linh hoạt hay đang tạm bỏ qua có nút tách khóa kèm lý do. Dòng cha bị
 * **thay** bằng n dòng con đứng ngay tại vị trí của nó; tổng đơn vị (câu, hoặc chùm với dòng chùm) giữ nguyên; mỗi dòng con là số
 * nguyên dương. Hàm thuần: màn (`row-split-panel.tsx`) chỉ nhập số và hiện lỗi.
 */

export type SplitState =
  /** Cặp không đăng ký tách dòng, hoặc dòng chỉ có một đơn vị: không có nút. */
  | { status: "HIDDEN" }
  | { status: "LOCKED"; reason: LockReasonCode }
  /** Tách được; `units` là số đơn vị của dòng cha (câu của dòng câu đơn, chùm của dòng chùm). */
  | { status: "OPEN"; units: number };

/** Dòng có điều kiện riêng mà FTM chưa có quy tắc chia: `slots`, `childConditions`, `matrix`, `topicQuotas` của dòng, hoặc được chỉ tiêu mục tham chiếu qua `groupIds`. */
export function hasOwnConditions(block: QuestionBlock, section: Pick<ExamSection, "topicQuotas">): boolean {
  if ((block.slots?.length ?? 0) > 0 || (block.childConditions?.length ?? 0) > 0) return true;
  if (block.matrix !== null || (block.topicQuotas?.length ?? 0) > 0) return true;
  return (section.topicQuotas ?? []).some((quota) => quota.groupIds?.includes(block.id) === true);
}

/** Số đơn vị chia được của dòng: số chùm của dòng chùm cỡ cố định, số câu của dòng câu đơn. */
export function splitUnits(block: QuestionBlock): number {
  return isSetBlock(block) ? (block.setCount ?? 0) : (block.questionCount ?? 0);
}

/** Trạng thái nút tách của một dòng. Thứ tự lý do: khóa dòng, khóa dạng, rồi điều kiện riêng, cỡ linh hoạt, tạm bỏ qua. */
export function splitState(block: QuestionBlock, section: Pick<ExamSection, "topicQuotas">, entry: Pick<SubjectFormEntry, "rowsEditable">, context: RowContext): SplitState {
  if (entry.rowsEditable !== "ADD_AND_SPLIT") return { status: "HIDDEN" };
  const locks = rowLocks(block, context);
  if (locks.counts) return { status: "LOCKED", reason: "rowCounts" };
  if (locks.mode) return { status: "LOCKED", reason: "answerType" };
  if (block.omitted === true) return { status: "LOCKED", reason: "rowOmitted" };
  if (isFlexibleSet(block)) return { status: "LOCKED", reason: "rowNoSetSize" };
  if (hasOwnConditions(block, section)) return { status: "LOCKED", reason: "rowHasConditions" };
  const units = splitUnits(block);
  return units >= 2 ? { status: "OPEN", units } : { status: "HIDDEN" };
}

/** Các n được chọn: từ 2 tới số đơn vị (4 chùm không chia thành 5 dòng). */
export function splitChoices(units: number): number[] {
  return units < 2 ? [] : Array.from({ length: units - 1 }, (_, index) => index + 2);
}

/** Chia đều theo đơn vị nguyên, phần dư cho dòng đứng trước (4 → 3: 2, 1, 1). */
export function evenSplit(units: number, parts: number): number[] {
  if (parts <= 0) return [];
  const base = Math.floor(units / parts);
  const rest = units - base * parts;
  return Array.from({ length: parts }, (_, index) => base + (index < rest ? 1 : 0));
}

export type SplitIssue =
  /** Dòng con thứ `index` (từ 0) không phải số nguyên dương. */
  | { code: "NOT_POSITIVE"; index: number }
  /** Tổng các dòng con khác số đơn vị của dòng cha. */
  | { code: "TOTAL"; total: number; units: number };

/** Lỗi của một cách chia: lỗi tại dòng trước, lỗi tổng sau. */
export function splitIssues(units: number, parts: readonly number[]): SplitIssue[] {
  const issues: SplitIssue[] = [];
  parts.forEach((value, index) => {
    if (!Number.isInteger(value) || value < 1) issues.push({ code: "NOT_POSITIVE", index });
  });
  const total = parts.reduce((sum, value) => sum + value, 0);
  if (total !== units) issues.push({ code: "TOTAL", total, units });
  return issues;
}

/**
 * Các dòng con của một cách chia hợp lệ. Dòng con đầu giữ `id`, `templateGroupCode`, `answerTypeLocked`, `shuffleEditable` của dòng cha;
 * các dòng sau có `id` mới và để máy chủ gán lại các trường theo mẫu (`ExamStructureOps.restoreBlock`), web không tự gán. Mọi trường
 * khác (dạng, cỡ chùm, phạm vi, nhãn, `shuffleUnits` đúng giá trị kể cả `false`, điểm mỗi câu, cách chấm, hình dạng câu, định dạng
 * đáp án) chép từ dòng cha; `positionRange` do máy chủ tính, không chép. Tên mặc định "<tên cha> 1…n".
 */
export function splitBlock(block: QuestionBlock, parts: readonly number[], newId: () => string = () => draftId("blk")): QuestionBlock[] {
  const set = isSetBlock(block);
  return parts.map((value, index) => ({
    id: index === 0 ? block.id : newId(),
    name: `${block.name} ${index + 1}`,
    questionType: block.questionType,
    questionCount: set ? value * (block.setSize ?? 0) : value,
    setCount: set ? value : null,
    setSize: set ? block.setSize : null,
    requiredPointsPerQuestion: block.requiredPointsPerQuestion,
    knowledgeScope: block.knowledgeScope,
    tagCodes: block.tagCodes,
    matrix: null,
    childAnswerType: block.childAnswerType,
    templateGroupCode: index === 0 ? block.templateGroupCode : null,
    answerTypeLocked: index === 0 && block.answerTypeLocked,
    ...(index === 0 && block.shuffleEditable !== undefined ? { shuffleEditable: block.shuffleEditable } : {}),
    ...(block.shuffleUnits !== undefined ? { shuffleUnits: block.shuffleUnits } : {}),
    ...(block.expectedScoring !== undefined ? { expectedScoring: block.expectedScoring } : {}),
    ...(block.itemShape !== undefined ? { itemShape: block.itemShape } : {}),
    ...(block.answerFormats !== undefined ? { answerFormats: block.answerFormats } : {}),
  }));
}

/** Thay dòng cha bằng các dòng con tại đúng vị trí của nó; dòng khác và môn khác giữ nguyên. */
export function replaceWithSplit(section: ExamSection, blockId: string, children: readonly QuestionBlock[]): ExamSection {
  const index = section.blocks.findIndex((block) => block.id === blockId);
  if (index < 0) return section;
  return { ...section, blocks: [...section.blocks.slice(0, index), ...children, ...section.blocks.slice(index + 1)] };
}
