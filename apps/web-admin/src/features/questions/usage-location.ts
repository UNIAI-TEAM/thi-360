import type { QuestionUsageBlock } from "./editor-types";

export interface UsageLocationNames {
  /** Tên môn theo ngôn ngữ giao diện (danh mục). */
  subject: (code: string) => string;
  /** Tên tổ hợp theo ngôn ngữ giao diện; `undefined` khi danh mục không có mã đó. */
  combination: (code: string) => string | undefined;
}

/**
 * Một dòng vị trí lấy câu: phần › lựa chọn › môn › khối (F-10). Môn và tổ hợp lấy tên danh mục theo ngôn ngữ giao diện;
 * tên phần và khối là dữ liệu của bài thi (do người soạn đặt), giữ nguyên.
 */
export function usageLocation(block: QuestionUsageBlock, subjectCode: string, names: UsageLocationNames): string {
  const option = block.optionCode ? (names.combination(block.optionCode) ?? (block.optionName || block.optionCode)) : block.optionName;
  return [block.partName, option, names.subject(subjectCode), block.blockName].filter(Boolean).join(" › ");
}
