import type { ExamPathRef } from "./review-types";
import type { ExamPart } from "./types";

export const SUBJECT_CHOICE_FULL_COUNT = 17;
export const SUBJECT_CHOICE_TRIMMED_COUNT = 16;

export interface PreviewSectionOption {
  sectionId: string;
  subjectCode: string;
  partId: string;
  partName: string;
  /** Môn của `SUBJECT_CHOICE`: số câu chọn được (17 rồi 16); `null` khi môn có số câu cố định. */
  counts: number[] | null;
  /** Số câu cố định của môn; `null` với môn của `SUBJECT_CHOICE`. */
  fixedCount: number | null;
}

function sortedByOrder(parts: readonly ExamPart[]): ExamPart[] {
  return [...parts].sort((a, b) => a.order - b.order);
}

export function listPreviewSections(parts: readonly ExamPart[]): PreviewSectionOption[] {
  const found: PreviewSectionOption[] = [];
  const seen = new Set<string>();
  for (const part of sortedByOrder(parts)) {
    const add = (
      section: { id: string; subjectCode: string; questionCount: number | null },
      counts: number[] | null,
    ) => {
      const key = `${part.id}:${section.subjectCode}`;
      if (seen.has(key)) return;
      seen.add(key);
      found.push({
        sectionId: section.id,
        subjectCode: section.subjectCode,
        partId: part.id,
        partName: part.name,
        counts,
        fixedCount: counts ? null : section.questionCount,
      });
    };
    part.sections.forEach((section) => add(section, null));
    (part.subjectPool ?? []).forEach((section) => add(section, null));
    for (const option of part.options) {
      const max = option.countsByPick && option.countsByPick.length > 0 ? Math.max(...option.countsByPick) : null;
      const counts =
        option.kind === "SUBJECT_CHOICE" && max !== null
          ? max === SUBJECT_CHOICE_FULL_COUNT
            ? [SUBJECT_CHOICE_FULL_COUNT, SUBJECT_CHOICE_TRIMMED_COUNT]
            : [max]
          : null;
      option.sections.forEach((section) => add(section, counts));
    }
  }
  return found;
}

export function examNeedsPathChoice(parts: readonly ExamPart[]): boolean {
  return parts.some((part) => part.mode === "CHOICE" || Boolean(part.codeSource));
}

export function changedPartIds(from: ExamPathRef, to: ExamPathRef): string[] {
  const optionOf = (path: ExamPathRef, partId: string) => path.choices.find((choice) => choice.partId === partId)?.optionId;
  const partIds = new Set([...from.choices, ...to.choices].map((choice) => choice.partId));
  return [...partIds].filter((partId) => optionOf(from, partId) !== optionOf(to, partId));
}
