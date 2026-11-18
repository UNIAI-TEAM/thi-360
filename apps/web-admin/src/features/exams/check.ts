import type { ExamPathRef, PathChoice } from "./review-types";
import type { ExamDetail, ExamPart, ExamSection, QuestionBlock } from "./types";

/**
 * Tiện ích thuần còn dùng trên màn thật: liệt kê đường lựa chọn (xem trước đề) và cờ "năm cấu trúc khác năm
 * ma trận cần chuyên gia xác nhận". Bộ kiểm tra cấu hình phía client đã bị gỡ cùng lớp mock — server kiểm tra
 * thật qua `review-api.ts`.
 */

/** Giới hạn dài hơn khi người dùng chọn "Kiểm tra lại với thời gian dài hơn" (mẫu gửi server). */
export const EXTENDED_CHECK_TIME_LIMIT_SECONDS = 120;

export type ChoiceNameResolver = (kind: PathChoice["kind"], code: string) => string;

const codeAsName: ChoiceNameResolver = (_kind, code) => code;

const DEFAULT_PATH_ID = "default";

export function sortedParts(parts: readonly ExamPart[]): ExamPart[] {
  return [...parts].sort((a, b) => a.order - b.order);
}

/** Liệt kê mọi đường lựa chọn: tích Descartes các lựa chọn của mọi phần tự chọn (theo thứ tự phần). */
export function enumeratePaths(parts: readonly ExamPart[], nameOf: ChoiceNameResolver = codeAsName): ExamPathRef[] {
  let combos: PathChoice[][] = [[]];
  for (const part of sortedParts(parts)) {
    if (part.mode !== "CHOICE" || part.options.length === 0) continue;
    combos = combos.flatMap((prefix) =>
      part.options.map((option) => [
        ...prefix,
        {
          partId: part.id,
          optionId: option.id,
          kind: option.kind,
          code: option.code,
          // Nhánh và tổ hợp sinh từ "chọn k trong n" (chưa có mã danh mục) mang tên riêng; còn lại lấy theo danh mục.
          name: option.name ?? (option.kind === "BRANCH" ? option.code : nameOf(option.kind, option.code)),
        },
      ]),
    );
  }
  return combos.map((choices) => ({
    pathId: choices.length ? choices.map((choice) => choice.optionId).join("+") : DEFAULT_PATH_ID,
    choices,
  }));
}

/** Năm cấu trúc khác năm ma trận (SP2E 2026 dùng ma trận 2025) mà chuyên gia chưa xác nhận chỉ tiêu. */
export function yearsNeedConfirmation(exam: Pick<ExamDetail, "structureYear" | "matrixYear" | "yearsConfirmed">): boolean {
  return (
    exam.structureYear !== null && exam.matrixYear !== null && exam.structureYear !== exam.matrixYear && !exam.yearsConfirmed
  );
}

/** Khối, môn và phần chứa một khối theo id, tìm trong phần chung, `subjectPool`, môn của lựa chọn và khối của lựa chọn. */
export interface BlockContext {
  block: QuestionBlock;
  section: ExamSection | null;
  part: ExamPart;
  optionId: string | null;
}

export function findBlockContext(exam: Pick<ExamDetail, "parts">, blockId: string | null | undefined): BlockContext | null {
  if (!blockId) return null;
  for (const part of exam.parts) {
    const groups: Array<{ sections: ExamSection[]; optionId: string | null; blocks?: QuestionBlock[] }> = [
      { sections: part.sections, optionId: null },
      { sections: part.subjectPool ?? [], optionId: null },
      ...part.options.map((option) => ({ sections: option.sections, optionId: option.id, blocks: option.blocks })),
    ];
    for (const group of groups) {
      for (const section of group.sections) {
        const block = section.blocks.find((item) => item.id === blockId);
        if (block) return { block, section, part, optionId: group.optionId };
      }
      const block = group.blocks?.find((item) => item.id === blockId);
      if (block) return { block, section: null, part, optionId: group.optionId };
    }
  }
  return null;
}

/**
 * Điều kiện lọc của khối dùng dựng câu "ngân hàng chỉ có N câu …" (2B 7.3): hình dạng câu, định dạng đáp án, vị trí
 * câu con. Máy chủ chỉ trả mã, `required`, `available` và vị trí khối; phần còn lại đọc từ cấu hình khối. Trường chưa
 * có trên khối thì để rỗng và câu bỏ cụm tương ứng.
 */
export interface BlockFilterConfig {
  itemShape: { statements: number | null; leftItems: number | null; rightItems: number | null } | null;
  answerFormats: string[];
  childPositions: Array<{ from: number; to: number }>;
}

interface BlockFilterFields {
  itemShape?: { statements?: number | null; leftItems?: number | null; rightItems?: number | null } | null;
  answerFormats?: string[] | null;
  childConditions?: Array<{ from?: number | null; to?: number | null }> | null;
}

export function blockFilterConfig(block: QuestionBlock | null | undefined): BlockFilterConfig {
  const fields: BlockFilterFields = (block as (QuestionBlock & BlockFilterFields) | null | undefined) ?? {};
  const shape = fields.itemShape;
  const positions: Array<{ from: number; to: number }> = [];
  for (const condition of fields.childConditions ?? []) {
    if (condition.from == null) continue;
    positions.push({ from: condition.from, to: condition.to ?? condition.from });
  }
  return {
    itemShape: shape
      ? { statements: shape.statements ?? null, leftItems: shape.leftItems ?? null, rightItems: shape.rightItems ?? null }
      : null,
    answerFormats: fields.answerFormats ?? [],
    childPositions: positions,
  };
}
