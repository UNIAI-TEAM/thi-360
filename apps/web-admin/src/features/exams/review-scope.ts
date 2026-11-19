import type { ExamPathRef } from "./review-types";
import type { ExamPart } from "./types";

/**
 * Phạm vi sinh thử (2B mục 8 và 15.4): một môn, một đường (mã, nhánh, bộ ba môn HSA), hoặc cả bài. Chỉ các hàm thuần
 * dựng danh sách lựa chọn từ cấu trúc; thân yêu cầu gửi lên là `PreviewTarget` của `review-types.ts`.
 */

/** Số câu của môn `SUBJECT_CHOICE` có thể sinh thử: đủ 17 câu, hoặc 16 câu (bỏ 1 câu, B10). */
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

/**
 * Các môn có thể sinh thử riêng: môn của phần cố định, của danh sách môn phần theo mã, và của các lựa chọn. Một môn
 * xuất hiện ở nhiều lựa chọn của cùng phần (tổ hợp cũ) chỉ liệt kê một lần.
 */
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

/** Bài có phần chọn (chọn mã, nhánh, môn, phương án): "Cả bài" không có nghĩa, phải chọn một đường (bản vẽ `SinhThu2B`). */
export function examNeedsPathChoice(parts: readonly ExamPart[]): boolean {
  return parts.some((part) => part.mode === "CHOICE" || Boolean(part.codeSource));
}

/** Các phần có lựa chọn khác nhau giữa hai đường: chỉ các phần này phải sinh lại khi đổi mã (R-15), phần còn lại giữ nguyên. */
export function changedPartIds(from: ExamPathRef, to: ExamPathRef): string[] {
  const optionOf = (path: ExamPathRef, partId: string) => path.choices.find((choice) => choice.partId === partId)?.optionId;
  const partIds = new Set([...from.choices, ...to.choices].map((choice) => choice.partId));
  return [...partIds].filter((partId) => optionOf(from, partId) !== optionOf(to, partId));
}
