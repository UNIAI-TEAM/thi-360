import type { ExamTemplate, ExamTemplateBlock, QuestionBlock } from "./types";

/**
 * Phần mà mọi nhóm của mọi môn đều tạm bỏ qua (SP2E Ngữ văn Phần II · Viết, K7, K15): số câu của phần và của môn là 0 theo
 * thiết kế, nên màn tạo bài không có ô nào để nhập (F5) và màn cấu trúc không kiểm số câu, không so tổng nhóm. Phần có cả
 * nhóm thường lẫn nhóm tạm bỏ qua không thuộc loại này. Một quy tắc cho hai màn; mỗi màn chỉ đếm nhóm theo dữ liệu của mình
 * (mẫu: `omittedBlocks`; cấu trúc: `omitted`).
 */
export function isAllOmittedPart(sections: ReadonlyArray<{ blockCount: number; omittedCount: number }>): boolean {
  return sections.length > 0 && sections.every((section) => section.omittedCount > 0 && section.omittedCount === section.blockCount);
}

/**
 * Nhóm của bài là bản sao của một nhóm đã lược trong mẫu (H43), cùng điều kiện với `ExamCheckRules.reenabledBlocks`: khớp
 * theo id của nhóm mẫu đã lược, nếu bài không mang id đó thì khớp theo mã nhóm mẫu khi mã ấy chỉ có ở một nhóm của mẫu.
 * Nhóm đã lược của mẫu được đọc ở `omittedBlocks` của các môn trong phần; mã nhóm duy nhất tính trên mọi nhóm của mẫu
 * (môn của phần, lựa chọn, môn của lựa chọn tự chọn môn, khối gợi ý). Nhóm thêm tay hoặc nhóm cùng loại nhưng mẫu không lược
 * thì không khớp. Máy chủ vẫn là lớp chặn cuối (`OMITTED_BLOCK_LOCKED`) cho các ca giao diện không có dữ liệu để khớp.
 */
export function isTemplateOmittedGroup(template: ExamTemplate | null | undefined, block: Pick<QuestionBlock, "id" | "templateGroupCode">): boolean {
  if (!template) return false;
  const omitted = template.parts.flatMap((part) => (part.sections ?? []).flatMap((section) => section.omittedBlocks ?? []));
  if (block.id && omitted.some((candidate) => candidate.id === block.id)) return true;
  const code = block.templateGroupCode;
  if (!code || !omitted.some((candidate) => candidate.templateGroupCode === code)) return false;
  return templateGroupCodeCount(template, code) === 1;
}

function templateGroupCodeCount(template: ExamTemplate, code: string): number {
  let count = 0;
  const countIn = (blocks: readonly ExamTemplateBlock[] | undefined) => {
    for (const block of blocks ?? []) if (block.templateGroupCode === code) count += 1;
  };
  for (const part of template.parts) {
    countIn(part.blocks);
    for (const section of part.sections ?? []) countIn(section.blocks);
    for (const option of part.options ?? []) {
      countIn(option.blocks);
      for (const section of option.subjectChoice?.sections ?? []) countIn(section.blocks);
    }
  }
  return count;
}
