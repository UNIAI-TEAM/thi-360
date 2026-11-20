import type { BlockingIssue, FormBlockingRule } from "./blocking";
import { COUNT_FIELD } from "./row-fields";

/**
 * Luật chặn lưu của Danh sách dòng (FTM R-15, plan CR-3b): số câu của dòng phải là số nguyên dương, ở mọi dòng của môn dùng DS (kể cả
 * dòng con sau khi tách, R-7: "mỗi dòng con là số nguyên dương"). Nhóm đang tạm bỏ qua không tính câu nên không xét. Lỗi nhập thô của
 * dòng (tỷ lệ quy ra không chia hết cỡ chùm, khối tách sai) đi qua kho nhập thô, không qua luật này.
 */
export const rowFormBlockingRule: FormBlockingRule = (section, context) => {
  if (context.form !== "ROWS") return [];
  const issues: BlockingIssue[] = [];
  for (const block of section.blocks) {
    if (block.omitted === true || block.questionCount === null) continue;
    if (!Number.isInteger(block.questionCount) || block.questionCount <= 0) {
      issues.push({ code: "ROW_COUNT_NOT_POSITIVE", sectionId: section.id, blockId: block.id, field: COUNT_FIELD });
    }
  }
  return issues;
};
