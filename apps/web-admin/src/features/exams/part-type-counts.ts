import type { ExamPart, QuestionBlock, TypeCount } from "./types";

/**
 * Số câu theo dạng câu của một phần ở cấu trúc đang có, cùng quy tắc với `ExamTemplatePartSummary.questionTypes` của máy
 * chủ (phía mẫu): nhóm không lược, theo thứ tự xuất hiện, phần tự chọn tính theo lựa chọn đầu tiên, nhóm không ghi dạng
 * câu là `ANY`. Hai phía cùng một cách đếm nên so "trước → sau" khi đổi phiên bản không lệch vì cách tính.
 */
export function partTypeCounts(part: ExamPart): TypeCount[] {
  const first = part.mode === "CHOICE" ? part.options[0] : undefined;
  const blocks: QuestionBlock[] = first
    ? [...first.sections.flatMap((section) => section.blocks), ...(first.blocks ?? [])]
    : part.sections.flatMap((section) => section.blocks);
  const counts = new Map<TypeCount["questionType"], number>();
  for (const block of blocks) {
    if (block.omitted) continue;
    const type = block.questionType ?? "ANY";
    counts.set(type, (counts.get(type) ?? 0) + (block.questionCount ?? 0));
  }
  return [...counts].map(([questionType, questionCount]) => ({ questionType, questionCount }));
}
