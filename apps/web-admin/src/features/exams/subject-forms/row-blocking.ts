import type { BlockingIssue, FormBlockingRule } from "./blocking";
import { COUNT_FIELD } from "./row-fields";

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
