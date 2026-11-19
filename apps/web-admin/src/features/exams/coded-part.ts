import type { QuestionType } from "@/features/content/types";
import { draftId } from "./components/exam-structure-draft";
import { isSetBlock } from "./matrix";
import type { ExamPart, ExamSection, ExamSectionState, StructureCombination } from "./types";

/**
 * Phần theo mã (QDA, BCA; 2B 3.1, 15.3, D12): mỗi môn một ma trận trong `subjectPool`, dùng chung cho mọi mã có môn đó.
 * Mã và quan hệ mã–môn đọc từ Global List, chỉ xem ở bước Cấu trúc; không có thao tác chọn mã hay gán môn cho mã.
 */

export type SectionStateName = ExamSectionState["state"];

/** Một mục trong cột "Cách lấy" của môn: chùm (`sets × size`) hoặc câu đơn theo dạng trả lời. */
export type HowPart = { kind: "set"; sets: number; size: number } | { kind: "single"; type: QuestionType | null; count: number };

/** Cách lấy của môn: các chùm cộng dồn, câu đơn gộp theo dạng trả lời; nhóm tạm bỏ qua không tính. */
export function sectionHow(section: ExamSection): HowPart[] {
  const parts: HowPart[] = [];
  for (const block of section.blocks) {
    if (block.omitted) continue;
    if (isSetBlock(block) && block.setCount && block.setSize) {
      const existing = parts.find((part) => part.kind === "set" && part.size === block.setSize);
      if (existing && existing.kind === "set") existing.sets += block.setCount;
      else parts.push({ kind: "set", sets: block.setCount, size: block.setSize });
      continue;
    }
    if (block.questionCount === null) continue;
    const existing = parts.find((part) => part.kind === "single" && part.type === block.questionType);
    if (existing && existing.kind === "single") existing.count += block.questionCount;
    else parts.push({ kind: "single", type: block.questionType, count: block.questionCount });
  }
  return parts;
}

/** Trạng thái của một môn: có dòng từ máy chủ thì theo đó; chưa có nhóm nào thì "Chưa hoàn tất"; chưa biết thì `null`. */
export function sectionStateOf(
  section: ExamSection,
  states: readonly ExamSectionState[] | null | undefined,
): SectionStateName | null {
  if (!states) return null;
  return states.find((item) => item.sectionId === section.id)?.state ?? (section.blocks.length === 0 ? "INCOMPLETE" : null);
}

const STATE_RANK: Record<SectionStateName, number> = { INCOMPLETE: 2, NEEDS_CHECK: 1, CHECKED: 0 };

/** Trạng thái gộp của nhiều môn: lấy mức xấu nhất ("Chưa hoàn tất" hơn "Cần kiểm tra lại" hơn "Đã kiểm tra"). */
export function worstState(states: ReadonlyArray<SectionStateName | null>): SectionStateName | null {
  let worst: SectionStateName | null = null;
  for (const state of states) {
    if (state !== null && (worst === null || STATE_RANK[state] > STATE_RANK[worst])) worst = state;
  }
  return worst;
}

export interface CodePosition {
  subjectCode: string;
  from: number;
  to: number;
}

/** Vị trí câu của từng môn trong mã theo thứ tự môn của mã: QĐ02 có Hóa học ở câu 1–25, Sinh học ở câu 26–50 (R-27). */
export function codePositions(combination: StructureCombination, pool: readonly ExamSection[]): CodePosition[] {
  let next = 1;
  const positions: CodePosition[] = [];
  for (const subjectCode of combination.subjectCodes) {
    const count = pool.find((section) => section.subjectCode === subjectCode)?.questionCount ?? 0;
    if (count > 0) positions.push({ subjectCode, from: next, to: next + count - 1 });
    next += count;
  }
  return positions;
}

/** Các mã dùng một môn của phần, theo tên mã đầy đủ (`QD01`) hoặc mã nguồn. */
export function codesUsing(subjectCode: string, combinations: readonly StructureCombination[]): string[] {
  return combinations.filter((item) => item.subjectCodes.includes(subjectCode)).map((item) => item.examCode ?? item.code);
}

/**
 * Thêm môn còn thiếu của một mã vào `subjectPool` (D12: danh mục vừa thêm mã mới). Môn mới chưa có nhóm nên "Chưa hoàn
 * tất"; số câu theo môn đầu tiên của phần để người dùng sửa tiếp.
 */
export function addPoolSection(parts: readonly ExamPart[], partId: string, subjectCode: string): ExamPart[] {
  return parts.map((part) => {
    if (part.id !== partId) return part;
    const pool = part.subjectPool ?? [];
    if (pool.some((section) => section.subjectCode === subjectCode)) return part;
    const section: ExamSection = {
      id: draftId("sec"),
      subjectCode,
      questionCount: pool[0]?.questionCount ?? part.questionCount,
      knowledgeScope: null,
      answerQuotas: null,
      matrix: null,
      choiceGroup: null,
      blocks: [],
    };
    return { ...part, subjectPool: [...pool, section] };
  });
}
