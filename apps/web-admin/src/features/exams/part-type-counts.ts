import type { ExamPart, ExamSection, QuestionBlock, TypeCount } from "./types";

function isRemainderBlock(block: QuestionBlock): boolean {
  return block.questionType !== "QUESTION_SET" && block.questionType !== "CLOZE" && block.questionCount === null;
}

function fixedBlockCount(block: QuestionBlock): number {
  if (block.questionType === "QUESTION_SET" || block.questionType === "CLOZE") {
    const setCount = block.setCount ?? 0;
    const setSize = block.setSize ?? 0;
    if (setCount > 0 && setSize > 0) return setCount * setSize;
  }
  return block.questionCount ?? 0;
}

function addSectionBlockCounts(sections: readonly ExamSection[], counts: Map<TypeCount["questionType"], number>): void {
  for (const section of sections) {
    let fixedInSection = 0;
    let remainderBlocks = 0;
    for (const block of section.blocks) {
      if (block.omitted) continue;
      if (isRemainderBlock(block)) remainderBlocks += 1;
      else fixedInSection += fixedBlockCount(block);
    }
    const remainderCount =
      remainderBlocks === 1 && section.questionCount !== null ? Math.max(0, section.questionCount - fixedInSection) : 0;
    for (const block of section.blocks) {
      if (block.omitted) continue;
      const type = block.questionType ?? "ANY";
      const count = isRemainderBlock(block) ? remainderCount : fixedBlockCount(block);
      if (count > 0) counts.set(type, (counts.get(type) ?? 0) + count);
    }
  }
}

function addDirectBlockCounts(blocks: readonly QuestionBlock[], counts: Map<TypeCount["questionType"], number>): void {
  for (const block of blocks) {
    if (block.omitted) continue;
    const type = block.questionType ?? "ANY";
    const count = fixedBlockCount(block);
    if (count > 0) counts.set(type, (counts.get(type) ?? 0) + count);
  }
}

/**
 * Số câu theo dạng câu của một phần ở cấu trúc đang có, cùng quy tắc với `ExamTemplateService.partSummaries` của máy
 * chủ (phía mẫu): nhóm không lược, theo thứ tự xuất hiện; phần tự chọn tính theo lựa chọn đầu tiên (trừ `SUBJECT_CHOICE`
 * và `subjectPool` trả rỗng); `QUESTION_SET`/`CLOZE` ưu tiên `setCount * setSize`; nhóm lấy phần còn lại tính từ
 * `section.questionCount - fixed`; bỏ qua số `<= 0`. Hai phía cùng một cách đếm nên so "trước → sau" khi đổi phiên bản
 * không lệch vì cách tính.
 */
export function partTypeCounts(part: ExamPart): TypeCount[] {
  const counts = new Map<TypeCount["questionType"], number>();
  const choice = part.participation?.mode === "CHOICE" || part.mode === "CHOICE" || (part.sections.length === 0 && part.options.length > 0);
  if (choice) {
    const first = part.options[0];
    if (first && first.kind !== "SUBJECT_CHOICE") {
      addSectionBlockCounts(first.sections, counts);
      addDirectBlockCounts(first.blocks ?? [], counts);
    }
  } else {
    addSectionBlockCounts(part.sections, counts);
  }
  return [...counts].map(([questionType, questionCount]) => ({ questionType, questionCount }));
}
