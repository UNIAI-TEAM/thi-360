import type { ExamTemplate, ExamTemplateBlock, QuestionBlock } from "./types";

export function isAllOmittedPart(sections: ReadonlyArray<{ blockCount: number; omittedCount: number }>): boolean {
  return sections.length > 0 && sections.every((section) => section.omittedCount > 0 && section.omittedCount === section.blockCount);
}

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
